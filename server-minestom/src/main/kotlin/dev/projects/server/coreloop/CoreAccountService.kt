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
        is CoreAction.BossReward -> {
            val run = requireRun(account, action.runId)
            require(!run.bossDefeated) { "この討伐報酬は受取済みです" }
            require(account.maps.size < CoreLoopCatalog.MAX_MAPS) { "地図の保管庫が満杯です" }
            val nextTier = (run.map.tier + 1).coerceAtMost(4)
            val rewardMap = CoreOwnedMap(derived(requestId, "boss-map"), run.map.seed xor requestId.leastSignificantBits, nextTier)
            val rewarded = recipe(addSource(account, source("boss", run.id, "defeat")), CoreRecipe("討伐完了！ T$nextTier の地図と討伐証を獲得しました", emptyMap(),
                mapOf(CoreMaterial(CoreResource.BOSS_SIGIL, run.map.tier) to 2L,
                    CoreMaterial(CoreResource.COMBAT_TOKEN, run.map.tier) to 12L,
                    CoreMaterial(CoreResource.GATHERING_TABLET) to 1L,
                    CoreMaterial(CoreResource.POTION) to 2L)))
            rewarded.first.copy(unlockedMapTier = maxOf(account.unlockedMapTier, nextTier),
                maps = account.maps + rewardMap, activeRun = run.copy(bossDefeated = true)) to rewarded.second
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
        is CoreAction.AbortRun -> {
            val run = requireRun(account, action.runId)
            require(!run.bossDefeated && account.claimedSources.none { it.startsWith("gather/${run.id}/") || it.startsWith("combat/${run.id}/") }) {
                "開始済みの遠征は中断返却できません"
            }
            require(account.maps.size < CoreLoopCatalog.MAX_MAPS) { "地図の保管庫が満杯です" }
            account.copy(maps = account.maps + run.map, activeRun = null) to "遠征の準備に失敗したため地図を返却しました"
        }
        is CoreAction.FinishRun -> { requireRun(account, action.runId); account.copy(activeRun = null) to "拠点へ帰還しました。獲得した素材は保管済みです" }
        is CoreAction.Consume -> {
            require(action.resource in setOf(CoreResource.POTION, CoreResource.WHETSTONE)) { "使用できないアイテムです" }
            require(account.activeRun != null) { "遠征中のみ使用できます" }
            recipe(account, CoreRecipe("${action.resource.displayName}を使用しました", mapOf(CoreMaterial(action.resource) to 1L), emptyMap()))
        }
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
