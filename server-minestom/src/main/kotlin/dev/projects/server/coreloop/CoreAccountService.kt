package dev.projects.server.coreloop

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.UUID

/** Call from the hub's dedicated serial executor; no Minecraft objects or tick-thread I/O. */
class CoreAccountService(private val repository: CoreAccountRepository) {
    private val accounts = mutableMapOf<UUID, CoreAccount>()

    @Synchronized
    fun open(playerId: UUID): CoreAccountLoadResult {
        accounts[playerId]?.let { return CoreAccountLoadResult.Ready(it, false) }
        return when (val result = repository.load(playerId)) {
            CoreRepositoryLoad.Missing -> CoreAccount(playerId).let {
                accounts[playerId] = it
                CoreAccountLoadResult.Ready(it, true)
            }
            is CoreRepositoryLoad.Loaded -> {
                accounts[playerId] = result.account
                CoreAccountLoadResult.Ready(result.account, false)
            }
            is CoreRepositoryLoad.Invalid -> CoreAccountLoadResult.Invalid(result.reason)
        }
    }

    @Synchronized
    fun snapshot(playerId: UUID): CoreAccount? = accounts[playerId]

    /** Every accepted mutation is already durable; there is no unsafe logout-only save. */
    @Synchronized
    fun forget(playerId: UUID) { accounts.remove(playerId) }

    @Synchronized
    fun transact(playerId: UUID, operation: CoreOperation): CoreTransactionResult {
        val current = accounts[playerId]
            ?: return CoreTransactionResult(CoreTransactionStatus.UNAVAILABLE, null, "データの読み込みが完了していません")
        val fingerprint = digest("${operation.expectedRevision}|${operation.action}")
        current.receipts[operation.requestId]?.let { receipt ->
            return if (receipt.fingerprint == fingerprint) {
                CoreTransactionResult(CoreTransactionStatus.REPLAYED, current, receipt.message)
            } else CoreTransactionResult(CoreTransactionStatus.CONFLICT, current, "同じ操作番号に異なる内容が送られました")
        }
        if (operation.expectedRevision != current.revision) {
            return CoreTransactionResult(CoreTransactionStatus.STALE, current, "内容が更新されました。もう一度選択してください")
        }
        if (current.receipts.size >= CoreLoopCatalog.MAX_RECEIPTS || current.revision == Long.MAX_VALUE) {
            return CoreTransactionResult(CoreTransactionStatus.REJECTED, current, "保存履歴の上限に達しました。管理者に連絡してください")
        }
        val proposal = runCatching { apply(current, operation.action, operation.requestId) }.getOrElse {
            return CoreTransactionResult(CoreTransactionStatus.REJECTED, current, it.message?.take(256) ?: "操作できません")
        }
        val revision = current.revision + 1
        val next = proposal.first.copy(revision = revision,
            receipts = current.receipts + (operation.requestId to CoreReceipt(fingerprint, revision, proposal.second)))
        return when (val saved = repository.commit(current.revision, next)) {
            CoreRepositorySave.Saved -> {
                accounts[playerId] = next
                CoreTransactionResult(CoreTransactionStatus.COMMITTED, next, proposal.second)
            }
            CoreRepositorySave.Conflict -> {
                accounts.remove(playerId)
                CoreTransactionResult(CoreTransactionStatus.CONFLICT, null, "保存内容が別の処理で更新されました。再接続してください")
            }
            is CoreRepositorySave.Failed -> CoreTransactionResult(CoreTransactionStatus.SAVE_FAILED, current,
                "保存に失敗しました。素材は消費されていません")
        }
    }

    private fun apply(account: CoreAccount, action: CoreAction, requestId: UUID): Pair<CoreAccount, String> = when (action) {
        is CoreAction.Gather -> {
            val run = requireRun(account, action.runId)
            require(action.resource.raw && action.quantity in 1..1024) { "採取内容が不正です" }
            val source = source("gather", run.id, action.nodeId)
            val updated = addSource(account, source)
            recipe(updated, CoreRecipe("${action.resource.displayName}を${action.quantity}個保管しました", emptyMap(),
                mapOf(CoreMaterial(action.resource, run.map.tier) to action.quantity.toLong())))
        }
        is CoreAction.CombatReward -> {
            val run = requireRun(account, action.runId)
            require(action.quantity in 1..64) { "戦利品数が不正です" }
            recipe(addSource(account, source("combat", run.id, action.encounterId)),
                CoreRecipe("戦利品券を${action.quantity}枚獲得しました", emptyMap(),
                    mapOf(CoreMaterial(CoreResource.COMBAT_TOKEN, run.map.tier) to action.quantity.toLong())))
        }
        is CoreAction.AffixLoot -> {
            val run = requireRun(account, action.runId)
            var updated = addSource(account, source("affix", run.id, action.sourceId))
            val combatSource = source("combat", run.id, action.sourceId)
            val tokensAlreadyPaid = combatSource in account.claimedSources
            if (action.kind == CoreLootKind.BOSS) {
                require(run.bossDefeated) { "ボスの討伐報酬がまだ確定していません" }
                updated = addSource(updated, source("boss-affix", run.id, "defeat"))
            } else {
                // Old token-only and new visible loot callbacks cannot pay for the same enemy twice.
                if (!tokensAlreadyPaid) updated = addSource(updated, combatSource)
            }
            val currencies = CoreCraftingCatalog.rollLoot(run, action.sourceId, action.kind)
            val dust = CoreAffixCatalog.lootDust(action.kind)
            val outputs = mutableMapOf(CoreMaterial(CoreResource.AFFIX_DUST) to dust)
            val tokens = if (tokensAlreadyPaid) 0L else CoreAffixCatalog.lootTokens(action.kind)
            if (tokens > 0) outputs[CoreMaterial(CoreResource.COMBAT_TOKEN, run.map.tier)] = tokens
            val message = "戦利品を回収：オーブ${currencies.values.sum()}個・刻印粉${dust}個"
            val rewarded = recipe(updated, CoreRecipe(message, emptyMap(), outputs))
            grantCurrencies(rewarded.first, currencies) to rewarded.second
        }
        is CoreAction.ApplyAffix, is CoreAction.ExtractAffix, is CoreAction.RerollAffix, is CoreAction.SalvageAffix ->
            error("旧刻印石の直接付与・抽出・再抽選は終了しました。保管庫でオーブへ交換できます")
        is CoreAction.CraftEquipment -> {
            requireHub(account)
            CoreCraftingCatalog.craft(account, action.gear, action.currency, requestId) to
                "${action.gear.displayName}に${action.currency.displayName}を使用しました。結果を確認してください"
        }
        is CoreAction.ConvertLegacyStone -> {
            requireHub(account)
            val stone = account.affixStones.singleOrNull { it.id == action.stoneId } ?: error("刻印石が見つかりません")
            require(CoreAffixCatalog.definition(stone) != null) { "未対応のMODは交換せず保管してください" }
            grantCurrencies(account, mapOf(CoreCraftingCurrency.ALTERATION to stone.tier.toLong(), CoreCraftingCurrency.ALCHEMY to 1L))
                .copy(affixStones = account.affixStones.filterNot { it.id == stone.id }) to "旧石を改変${stone.tier}個・錬金1個へ交換しました"
        }
        is CoreAction.ActivityReward -> {
            val run = requireRun(account, action.runId)
            require(run.trialId == null) { "通常マップの探索イベントのみ報酬を受け取れます" }
            val rewarded = recipe(addSource(account, source("activity", run.id, action.sourceId)), CoreRecipe(
                "${action.kind.displayName}を制覇！ 専用オーブと入場の欠片を獲得しました", emptyMap(),
                mapOf(CoreMaterial(CoreResource.COMBAT_TOKEN, run.map.tier) to 3L)))
            grantFragments(grantCurrencies(rewarded.first, mapOf(action.kind.currency to 1L)), action.kind, 1L) to rewarded.second
        }
        is CoreAction.BossReward -> {
            val run = requireRun(account, action.runId)
            require(!run.bossDefeated) { "この討伐報酬は受取済みです" }
            if (run.trialId == null) require(account.maps.size < CoreLoopCatalog.MAX_MAPS) { "地図の保管庫が満杯です" }
            val nextTier = (run.map.tier + 1).coerceAtMost(4)
            val message = if (run.trialId == null) "討伐完了！ T$nextTier の地図・討伐証・試練の欠片を獲得しました"
                else "専用ボス討伐！ 専用オーブ2個・高揚1個・討伐証を獲得しました"
            val outputs = if (run.trialId == null)
                mapOf(CoreMaterial(CoreResource.BOSS_SIGIL, run.map.tier) to 2L,
                    CoreMaterial(CoreResource.COMBAT_TOKEN, run.map.tier) to 12L,
                    CoreMaterial(CoreResource.GATHERING_TABLET) to 1L,
                    CoreMaterial(CoreResource.POTION) to 2L)
                else mapOf(CoreMaterial(CoreResource.BOSS_SIGIL, run.map.tier) to 2L, CoreMaterial(CoreResource.COMBAT_TOKEN, run.map.tier) to 12L)
            val rewarded = recipe(addSource(account, source("boss", run.id, "defeat")), CoreRecipe(message, emptyMap(), outputs)).first
            if (run.trialId == null) {
                val rewardMap = CoreOwnedMap(derived(requestId, "boss-map"), run.map.seed xor requestId.leastSignificantBits, nextTier)
                grantFragments(rewarded, CoreActivityKind.TRIAL, 1L).copy(unlockedMapTier = maxOf(account.unlockedMapTier, nextTier),
                    maps = account.maps + rewardMap, activeRun = run.copy(bossDefeated = true)) to message
            } else {
                val kind = CoreActivityKind.entries.single { it.bossId == run.trialId }
                grantCurrencies(rewarded, mapOf(kind.currency to 2L, CoreCraftingCurrency.EXALTED to 1L))
                    .copy(activeRun = run.copy(bossDefeated = true)) to message
            }
        }
        is CoreAction.Refine -> { requireHub(account); recipe(account, CoreLoopCatalog.refine(action.resource, action.tier, action.batches)) }
        CoreAction.UpgradeWeapon -> {
            requireHub(account)
            require(account.weaponTier < 4) { "武器は最高Tierです" }
            val upgraded = recipe(account, CoreLoopCatalog.weaponUpgrade(account.weaponTier))
            upgraded.first.copy(weaponTier = account.weaponTier + 1) to upgraded.second
        }
        CoreAction.UpgradeArmor -> {
            requireHub(account)
            require(account.armorTier < 4) { "防具は最高Tierです" }
            val upgraded = recipe(account, CoreLoopCatalog.armorUpgrade(account.armorTier))
            upgraded.first.copy(armorTier = account.armorTier + 1) to upgraded.second
        }
        is CoreAction.Exchange -> { requireHub(account); recipe(account, CoreLoopCatalog.exchange(action.resource, action.tier, action.batches)) }
        is CoreAction.Craft -> { requireHub(account); recipe(account, CoreLoopCatalog.craft(action.resource, action.batches, action.tier)) }
        is CoreAction.ClaimMap -> {
            requireHub(account)
            require(action.tier in 1..account.unlockedMapTier) { "そのTierはまだ解放されていません" }
            require(account.maps.size < CoreLoopCatalog.MAX_MAPS) { "地図の保管庫が満杯です" }
            val cost = if (action.tier == 1) emptyMap() else mapOf(CoreMaterial(CoreResource.COMBAT_TOKEN, action.tier - 1) to 1L)
            val claimed = recipe(account, CoreRecipe("T${action.tier} の遠征地図を受け取りました", cost, emptyMap()))
            claimed.first.copy(maps = account.maps + CoreOwnedMap(derived(requestId, "map"), action.seed, action.tier)) to claimed.second
        }
        is CoreAction.ApplyTablet -> {
            requireHub(account)
            val map = account.maps.singleOrNull { it.id == action.mapId } ?: error("地図が見つかりません")
            require(map.modifiers.size < 3) { "この地図にはこれ以上MODを付けられません" }
            require(map.modifiers.none { it.key == action.modifier.key }) { "同じMODは付与できません" }
            val applied = recipe(account, CoreRecipe("採取MODを地図に刻みました", mapOf(CoreMaterial(CoreResource.GATHERING_TABLET) to 1L), emptyMap()))
            applied.first.copy(maps = account.maps.map { if (it.id == map.id) map.withModifier(action.modifier) else it }) to applied.second
        }
        is CoreAction.StartRun -> {
            requireHub(account)
            require(account.claimedSources.none { it.startsWith("run/${action.runId}/") }) { "その遠征番号は使用済みです" }
            val map = account.maps.singleOrNull { it.id == action.mapId } ?: error("地図が見つかりません")
            require(map.tier <= account.unlockedMapTier) { "そのTierはまだ解放されていません" }
            addSource(account, source("run", action.runId, "started")).copy(
                maps = account.maps.filterNot { it.id == map.id }, activeRun = CoreActiveRun(action.runId, map)) to "遠征を準備しています"
        }
        is CoreAction.StartTrial -> {
            requireHub(account)
            val kind = CoreActivityKind.entries.singleOrNull { it.bossId == action.bossId } ?: error("未対応の専用ボスです")
            require(action.tier in 1..account.unlockedMapTier) { "そのTierはまだ解放されていません" }
            require(account.claimedSources.none { it.startsWith("run/${action.runId}/") }) { "その遠征番号は使用済みです" }
            require(account.amount(kind) >= CoreCraftingCatalog.TRIAL_ENTRY_FRAGMENTS) { "${kind.displayName}の欠片が3個必要です" }
            val map = CoreOwnedMap(derived(requestId, "trial-map"), account.craftingSeed xor requestId.leastSignificantBits, action.tier)
            addSource(account, source("run", action.runId, "started")).copy(
                fragments = account.fragments + (kind to account.amount(kind) - CoreCraftingCatalog.TRIAL_ENTRY_FRAGMENTS),
                activeRun = CoreActiveRun(action.runId, map, trialId = kind.bossId)) to "${kind.displayName}の専用ボス戦を準備しています"
        }
        is CoreAction.AbortRun -> {
            val run = requireRun(account, action.runId)
            require(!run.bossDefeated && account.claimedSources.none { it.startsWith("gather/${run.id}/") || it.startsWith("combat/${run.id}/") || it.startsWith("affix/${run.id}/") || it.startsWith("activity/${run.id}/") }) {
                "開始済みの遠征は中断返却できません"
            }
            if (run.trialId == null) {
                require(account.maps.size < CoreLoopCatalog.MAX_MAPS) { "地図の保管庫が満杯です" }
                account.copy(maps = account.maps + run.map, activeRun = null) to "遠征の準備に失敗したため地図を返却しました"
            } else {
                val kind = CoreActivityKind.entries.single { it.bossId == run.trialId }
                grantFragments(account, kind, CoreCraftingCatalog.TRIAL_ENTRY_FRAGMENTS).copy(activeRun = null) to "準備に失敗したため入場の欠片3個を返却しました"
            }
        }
        is CoreAction.FinishRun -> { requireRun(account, action.runId); account.copy(activeRun = null) to "拠点へ帰還しました。獲得した素材は保管済みです" }
        is CoreAction.Consume -> {
            require(action.resource in setOf(CoreResource.POTION, CoreResource.WHETSTONE)) { "使用できないアイテムです" }
            require(account.activeRun != null) { "遠征中のみ使用できます" }
            recipe(account, CoreRecipe("${action.resource.displayName}を使用しました", mapOf(CoreMaterial(action.resource) to 1L), emptyMap()))
        }
    }

    private fun grantCurrencies(account: CoreAccount, outputs: Map<CoreCraftingCurrency, Long>): CoreAccount {
        val balances = account.currencies.toMutableMap()
        outputs.forEach { (currency, amount) ->
            val next = Math.addExact(balances[currency] ?: 0, amount)
            require(next in 0..CoreLoopCatalog.MAX_BALANCE) { "オーブの保管上限です" }
            balances[currency] = next
        }
        return account.copy(currencies = balances)
    }
    private fun grantFragments(account: CoreAccount, kind: CoreActivityKind, amount: Long): CoreAccount {
        val next = Math.addExact(account.amount(kind), amount)
        require(next in 0..CoreLoopCatalog.MAX_BALANCE) { "入場の欠片の保管上限です" }
        return account.copy(fragments = account.fragments + (kind to next))
    }

    private fun recipe(account: CoreAccount, recipe: CoreRecipe): Pair<CoreAccount, String> {
        require(recipe.canAfford(account)) { "素材が足りません" }
        val balances = account.balances.toMutableMap()
        recipe.costs.forEach { (key, amount) -> balances[key] = (balances[key] ?: 0) - amount }
        recipe.outputs.forEach { (key, amount) ->
            val next = Math.addExact(balances[key] ?: 0, amount)
            require(next <= CoreLoopCatalog.MAX_BALANCE) { "素材の保管上限です" }
            balances[key] = next
        }
        return account.copy(balances = balances.filterValues { it > 0 }) to recipe.displayName
    }

    private fun requireRun(account: CoreAccount, runId: UUID): CoreActiveRun {
        val run = account.activeRun ?: error("進行中の遠征がありません")
        require(run.id == runId) { "遠征が切り替わっています" }
        return run
    }
    private fun requireHub(account: CoreAccount) { require(account.activeRun == null) { "拠点で操作してください" } }
    private fun addSource(account: CoreAccount, source: String): CoreAccount {
        require(source !in account.claimedSources) { "この報酬は受取済みです" }
        require(account.claimedSources.size < CoreLoopCatalog.MAX_SOURCES) { "報酬履歴の上限に達しました" }
        return account.copy(claimedSources = account.claimedSources + source)
    }
    private fun source(type: String, runId: UUID, sourceId: String): String {
        require(sourceId.length in 1..128 && sourceId.all { it.isLetterOrDigit() || it in "_-.:/" }) { "報酬元が不正です" }
        return "$type/$runId/$sourceId"
    }
    private fun derived(requestId: UUID, kind: String): UUID = UUID.nameUUIDFromBytes("$requestId/$kind".toByteArray(UTF_8))
    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(UTF_8)).joinToString("") { "%02x".format(it) }
}
