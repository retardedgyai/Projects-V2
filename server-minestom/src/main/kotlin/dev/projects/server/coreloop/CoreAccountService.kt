package dev.projects.server.coreloop

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.UUID

/** Call from the hub's dedicated serial executor; no Minecraft objects or tick-thread I/O. */
class CoreAccountService(private val repository: CoreAccountRepository,
    private val epochDay: () -> Long = { java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay() }) {
    private val accounts = mutableMapOf<UUID, CoreAccount>()
    @Volatile private var market = emptyList<CoreMarketEntry>()
    @Volatile private var buyOrders = emptyList<CoreBuyOrderEntry>()
    fun buyOrderSnapshot(): List<CoreBuyOrderEntry> = buyOrders
    fun marketSnapshot(): List<CoreMarketEntry> = market
    private fun refreshMarket() {
        val saved = repository.marketAccounts()
        buyOrders = saved.flatMap { a -> a.buyOrders.map { CoreBuyOrderEntry(a.playerId, it) } }
        market = saved.flatMap { a -> a.offers.map { CoreMarketEntry(a.playerId, it,
            it.gearId?.let { id -> a.storedGear.single { gear -> gear.identity.id == id } }) } }
    }

    @Synchronized
    fun open(playerId: UUID): CoreAccountLoadResult {
        runCatching { refreshMarket() }.getOrElse { return CoreAccountLoadResult.Invalid("市場の取引を復旧できません") }
        accounts[playerId]?.let { return CoreAccountLoadResult.Ready(it, false) }
        return when (val result = repository.load(playerId)) {
            CoreRepositoryLoad.Missing -> CoreAccount(playerId, journey = CoreJourney.fresh()).let {
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
        if (current.revision == Long.MAX_VALUE) {
            return CoreTransactionResult(CoreTransactionStatus.REJECTED, current, "保存履歴の上限に達しました。管理者に連絡してください")
        }
        if (operation.action is CoreAction.BuyOffer) return buy(current, operation, fingerprint)
        if (operation.action is CoreAction.FillBuyOrder) return fillOrder(current, operation, fingerprint)
        val proposal = runCatching { apply(current, operation.action, operation.requestId) }.getOrElse {
            return CoreTransactionResult(CoreTransactionStatus.REJECTED, current, it.message?.take(256) ?: "操作できません")
        }
        val revision = current.revision + 1
        val next = proposal.first.copy(revision = revision,
            receipts = retainedReceipts(current) + (operation.requestId to CoreReceipt(fingerprint, revision, proposal.second)))
        return when (val saved = repository.commit(current.revision, next)) {
            CoreRepositorySave.Saved -> {
                accounts[playerId] = next
                if (next.offers != current.offers || next.buyOrders != current.buyOrders) runCatching { refreshMarket() }
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
        is CoreAction.ChooseClass -> {
            requireHub(account)
            if (action.job == CoreClass.STARWEAVER) {
                require(account.journey.job == CoreClass.MAGE || account.journey.job == CoreClass.STARWEAVER) { "メイジから転職してください" }
                require(account.journey.level >= 20 && account.amount(CoreResource.BOSS_SIGIL, 2) >= 2) { "Lv20とT2討伐証2枚が必要です（証は消費しません）" }
            }
            var selected = account.copy(journey = account.journey.copy(job = action.job, chosen = true))
            if (!selected.weaponIdentity.base.usable(action.job)) {
                val old = CoreEconomy.capture(selected, CoreGearSlot.WEAPON)
                val base = when (action.job) { CoreClass.WARRIOR -> CoreWeaponBase.FLOW; CoreClass.RANGER -> CoreWeaponBase.LONGBOW; else -> CoreWeaponBase.STAFF }
                val existing = selected.storedGear.firstOrNull { it.slot == CoreGearSlot.WEAPON && it.identity.bound && it.identity.base == base }
                val starter = existing ?: CoreStoredGear(CoreGearIdentity(derived(selected.playerId, "starter:${base.family}"), selected.playerId, true, base = base), CoreGearSlot.WEAPON, 1, CoreGearRarity.NORMAL, CoreEnhancementState())
                require(existing != null || selected.storedGear.size < CoreEconomy.MAX_GEAR) { "装備庫に空きが必要です" }
                selected = starter.project(selected).let { it.copy(storedGear = it.storedGear + old) }
            }
            selected to "${action.job.displayName}を選びました。手帳の「成長と職業」で次の目標を確認できます"
        }
        is CoreAction.LearnCombat -> {
            require(action.lesson in 0..1 && account.activeRun != null) { "遠征中の実際の戦闘で習得します" }
            account.copy(journey = account.journey.learn(action.lesson)) to "戦闘の基本を習得しました"
        }
        is CoreAction.TemperEquipment -> {
            requireHub(account); require(!CoreEconomy.broken(account, action.slot)) { "先に修理してください" }
            val id = CoreEconomy.identity(account, action.slot)
            val tier = CoreAffixCatalog.gearTier(account, action.slot)
            val nextLevel = CoreJourneyRules.itemLevel(id, tier) + 1
            require(account.journey.legacy || nextLevel <= account.journey.level + 2) { "冒険レベルを上げるとさらに鍛錬できます" }
            val paid = recipe(account, CoreJourneyRules.temper(account, action.slot)).first
            val next = id.copy(itemLevel = nextLevel)
            paid.copy(weaponIdentity = if (action.slot == CoreGearSlot.WEAPON) next else paid.weaponIdentity,
                armorIdentity = if (action.slot == CoreGearSlot.ARMOR) next else paid.armorIdentity) to "装備Lv$nextLevel。MOD・品質・強化は維持しています"
        }
        is CoreAction.BuyOffer, is CoreAction.FillBuyOrder -> error("市場の取引処理を使用してください")
        is CoreAction.PlaceBuyOrder -> {
            requireHub(account)
            require(account.buyOrders.size < CoreEconomy.MAX_OFFERS) { "注文の上限です" }
            val order = CoreBuyOrder(derived(requestId, "buy-order"), action.unitPrice, action.quantity, action.tier, action.resource, action.slot,
                if (action.slot == CoreGearSlot.WEAPON) account.weaponIdentity.base.family else null)
            require(account.silver >= order.escrow) { "預ける銀貨が足りません" }
            account.copy(silver = account.silver - order.escrow, buyOrders = account.buyOrders + order) to "購入注文を掲示しました。代金は預託済みです"
        }
        is CoreAction.CancelBuyOrder -> {
            requireHub(account)
            val order = account.buyOrders.singleOrNull { it.id == action.id } ?: error("注文は完了済みです")
            account.copy(silver = Math.addExact(account.silver, order.escrow), buyOrders = account.buyOrders.filterNot { it.id == order.id }) to "未成立分の銀貨を返却しました"
        }
        is CoreAction.SurveyMap -> {
            requireHub(account)
            require(action.tier <= CoreProfessions.surveyTier(account.surveyPoints)) { "採取実績が足りません" }
            require(account.maps.size < CoreLoopCatalog.MAX_MAPS) { "地図の保管庫が満杯です" }
            val paid = recipe(account, CoreProfessions.surveyMap(action.tier, action.raw)).first
            paid.copy(unlockedMapTier = maxOf(account.unlockedMapTier, action.tier),
                maps = account.maps + CoreOwnedMap(derived(requestId, "survey-map"), action.seed, action.tier)) to "採取実績でT${action.tier}の地図を用意しました。討伐は不要です"
        }
        is CoreAction.StartDungeon -> {
            requireHub(account)
            val b = CoreMmoTuning.balance
            require(action.tier in 1..account.unlockedMapTier) { "そのTierは未解放です" }
            require(action.ascension in 0..minOf(b.dungeonMaxAscension, (account.dungeonRecords[action.tier] ?: -1) + 1)) { "先に一つ前の深度を踏破してください" }
            require(account.claimedSources.none { it.startsWith("run/${action.runId}/") }) { "その遠征番号は使用済みです" }
            val map = CoreOwnedMap(derived(requestId, "dungeon"), action.seed, action.tier)
            addSource(account, source("run", action.runId, "started")).copy(activeRun = CoreActiveRun(action.runId, map,
                dungeon = CoreDungeonEntry(action.ascension, b.dungeonStages, b.dungeonRoomsPerFloor))) to "星環の深殿を準備しています"
        }
        is CoreAction.DungeonReward -> {
            val run = requireRun(account, action.runId)
            val d = requireNotNull(run.dungeon) { "深殿の遠征ではありません" }
            require(action.stage == d.rewardedStage + 1 && action.stage <= d.stages) { "この部屋の報酬は受取済みか、順序が不正です" }
            require(action.boss == (action.stage % d.roomsPerFloor == 0)) { "部屋の報酬区分が不正です" }
            val b = CoreMmoTuning.balance
            val count = b.dungeonRoomTokens.toLong() + d.ascension / 4
            var updated = recipe(addSource(account, source("dungeon", run.id, action.stage.toString())),
                CoreRecipe("深殿の戦利品を保管しました", emptyMap(), mapOf(CoreMaterial(CoreResource.COMBAT_TOKEN, run.map.tier) to count))).first
            val orbs = if (action.boss) mapOf(CoreCraftingCurrency.CHAOS to b.dungeonBossOrbs.toLong(),
                CoreCraftingCurrency.EXALTED to 1L) else mapOf((if (action.treasury) CoreCraftingCurrency.CHAOS else CoreCraftingCurrency.ALTERATION) to 1L)
            updated = grantCurrencies(updated, orbs)
            val complete = action.stage == d.stages
            if (complete) updated = grantCurrencies(updated, mapOf(CoreCraftingCurrency.DIVINE to (1L + d.ascension / 5), CoreCraftingCurrency.ASTRAL to (1L + d.ascension / 10)))
            updated.copy(journey = updated.journey.gain(CoreJourneyRules.reward(run.map.tier, action.boss)).learn(2), activeRun = run.copy(dungeon = d.copy(rewardedStage = action.stage), bossDefeated = complete),
                dungeonRecords = if (complete) account.dungeonRecords + (run.map.tier to maxOf(account.dungeonRecords[run.map.tier] ?: -1, d.ascension)) else account.dungeonRecords) to
                if (complete) "深殿踏破！次の深度を解放。神聖のオーブを獲得しました" else "第${action.stage}の間を突破。報酬は保管済みです"
        }
        is CoreAction.Manufacture -> {
            requireHub(account)
            require(action.count in 1..16 && account.storedGear.size + action.count <= CoreEconomy.MAX_GEAR) { "装備庫の空きが足りません" }
            require(action.slot == CoreGearSlot.WEAPON || action.base == CoreWeaponBase.STANDARD)
            val paid = recipe(account, CoreProfessions.manufacture(action.slot, action.tier, action.count, action.base)).first
            val random = kotlin.random.Random(account.craftingSeed xor requestId.leastSignificantBits xor account.revision)
            val items = (0 until action.count).map { index ->
                CoreStoredGear(CoreGearIdentity(derived(requestId, "equipment:$index"), account.playerId, quality = CoreProfessions.quality(account, action.slot, random), base = action.base),
                    action.slot, action.tier, CoreGearRarity.NORMAL, CoreEnhancementState())
            }
            val profession = CoreProfession.crafting(action.slot)
            val p = CoreProfessions.progress(account, profession)
            val next = paid.copy(storedGear = paid.storedGear + items, journey = paid.journey.learn(4), professions = paid.professions +
                (profession to p.copy(xp = (p.xp + action.count.toLong() * action.tier * CoreMmoTuning.balance.craftXp).coerceAtMost(1_000_000_000))))
            CoreEnhancementCatalog.gainMastery(next, 5L * action.count) to
                "T${action.tier} ${action.slot.displayName}を${action.count}個制作。製造品質は装備庫で確認できます"
        }
        is CoreAction.Equip -> {
            requireHub(account)
            val item = stored(account, action.id)
            require(item.slot != CoreGearSlot.WEAPON || item.identity.base.usable(account.journey.job)) { "この武器に合う職業を先に選んでください" }
            val old = CoreEconomy.capture(account, item.slot)
            item.project(account).let { it.copy(storedGear = it.storedGear + old) } to "${item.displayName}を装備しました。以前の装備は装備庫へ保管しました"
        }
        is CoreAction.Deliver -> {
            requireHub(account)
            val item = stored(account, action.id)
            require(!item.identity.bound && item.identity.crafter == account.playerId && item.enhancement.level == 0 && item.affixes.isEmpty() && item.rarity == CoreGearRarity.NORMAL && !item.broken) {
                "自分で制作した未加工の装備だけ納品できます"
            }
            val today = epochDay()
            require(today >= account.deliveryDay) { "日付を確認できません" }
            val count = if (account.deliveryDay == today) account.deliveries else 0
            require(count < CoreEconomy.DAILY_DELIVERIES) { "本日の納品は完了しました。市場への出品は引き続き可能です" }
            account.copy(storedGear = account.storedGear.filterNot { it.identity.id == action.id },
                silver = Math.addExact(account.silver, CoreEconomy.deliveryPrice(item.tier)), deliveryDay = today, deliveries = count + 1) to
                "港へ納品しました。銀貨${CoreEconomy.deliveryPrice(item.tier)}枚を獲得（本日${count + 1}/3）"
        }
        is CoreAction.RedeemTokens -> {
            requireHub(account); require(action.quantity in 1..999)
            val paid = recipe(account, CoreRecipe("戦利品券を換金", mapOf(CoreMaterial(CoreResource.COMBAT_TOKEN, action.tier) to action.quantity.toLong()), emptyMap())).first
            val gain = 10L * action.tier * action.quantity
            paid.copy(silver = Math.addExact(paid.silver, gain)) to "銀貨${gain}枚を獲得しました。市場で素材や装備を購入できます"
        }
        is CoreAction.Repair -> {
            requireHub(account)
            require(CoreEconomy.broken(account, action.slot)) { "この装備は破損していません" }
            val input = stored(account, action.input)
            require(CoreEconomy.repairInput(account, action.slot, input)) { "同Tier・同系統・+0・未破損の装備が1個必要です（初期装備・出品中は不可）" }
            account.copy(storedGear = account.storedGear.filterNot { it.identity.id == input.identity.id },
                weaponBroken = if (action.slot == CoreGearSlot.WEAPON) false else account.weaponBroken,
                armorBroken = if (action.slot == CoreGearSlot.ARMOR) false else account.armorBroken) to
                "修理材料の装備1個を消費して修理しました。対象のMOD・強化値・天井・識別番号は維持しています"
        }
        is CoreAction.ListGear -> {
            requireHub(account)
            val item = stored(account, action.id)
            require(!item.identity.bound) { "初期・引継ぎ装備は売却できません" }
            list(account, CoreMarketOffer(derived(requestId, "listing"), action.price, gearId = action.id))
        }
        is CoreAction.ListMaterial -> {
            requireHub(account)
            val offer = CoreMarketOffer(derived(requestId, "listing"), action.price, action.material, action.quantity)
            val paid = recipe(account, CoreRecipe("素材を出品", mapOf(action.material to action.quantity), emptyMap())).first
            list(paid, offer)
        }
        is CoreAction.CancelOffer -> {
            requireHub(account)
            val offer = account.offers.singleOrNull { it.id == action.id } ?: error("出品は売却済みか取り下げ済みです")
            val canceled = account.copy(offers = account.offers.filterNot { it.id == offer.id })
            if (offer.material != null) recipe(canceled, CoreRecipe("出品を取り下げ素材を返却しました", emptyMap(), mapOf(offer.material to offer.quantity)))
            else canceled to "出品を取り下げました。装備庫から使用できます"
        }
        is CoreAction.Gather -> {
            val run = requireRun(account, action.runId)
            require(action.resource.raw && action.quantity in 1..1024) { "採取内容が不正です" }
            val source = source("gather", run.id, action.nodeId)
            val updated = addSource(account, source).copy(surveyPoints = (account.surveyPoints + run.map.tier).coerceAtMost(1_000_000_000),
                journey = account.journey.gain(20L * run.map.tier))
            recipe(updated, CoreRecipe("${action.resource.displayName}を${action.quantity}個保管しました", emptyMap(),
                mapOf(CoreMaterial(action.resource, run.map.tier) to action.quantity.toLong())))
        }
        is CoreAction.CombatReward -> {
            val run = requireRun(account, action.runId)
            require(action.quantity in 1..64) { "戦利品数が不正です" }
            recipe(addSource(account, source("combat", run.id, action.encounterId)).copy(journey = account.journey.gain(CoreJourneyRules.reward(run.map.tier)).learn(2)),
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
            val rolledCurrencies = CoreCraftingCatalog.rollLoot(run, action.sourceId, action.kind)
            // One deterministic introduction reward: a normal starter must be able to try MOD crafting.
            val currencies = if (!account.journey.legacy && !account.journey.knows(2))
                rolledCurrencies + (CoreCraftingCurrency.TRANSMUTATION to ((rolledCurrencies[CoreCraftingCurrency.TRANSMUTATION] ?: 0L) + 1L)) else rolledCurrencies
            val dust = CoreAffixCatalog.lootDust(action.kind)
            val outputs = mutableMapOf(CoreMaterial(CoreResource.AFFIX_DUST) to dust)
            val tokens = if (tokensAlreadyPaid) 0L else CoreAffixCatalog.lootTokens(action.kind)
            if (tokens > 0) outputs[CoreMaterial(CoreResource.COMBAT_TOKEN, run.map.tier)] = tokens
            val message = "戦利品を回収：オーブ${currencies.values.sum()}個・刻印粉${dust}個"
            val rewarded = recipe(updated, CoreRecipe(message, emptyMap(), outputs))
            grantCurrencies(rewarded.first, currencies).copy(journey = account.journey.gain(if (tokensAlreadyPaid || action.kind == CoreLootKind.BOSS) 0 else CoreJourneyRules.reward(run.map.tier)).learn(2)) to rewarded.second
        }
        is CoreAction.ApplyAffix, is CoreAction.ExtractAffix, is CoreAction.RerollAffix, is CoreAction.SalvageAffix ->
            error("旧刻印石の直接付与・抽出・再抽選は終了しました。保管庫でオーブへ交換できます")
        is CoreAction.CraftEquipment -> {
            requireHub(account)
            CoreCraftingCatalog.craft(account, action.gear, action.currency, requestId).copy(journey = account.journey.learn(5)) to
                "${action.gear.displayName}に${action.currency.displayName}を使用しました。結果を確認してください"
        }
        is CoreAction.EnhanceEquipment -> {
            requireHub(account)
            val quote = CoreEnhancementCatalog.quote(account, action.gear, action.mode)
            require(quote.blockedReason == null) { quote.blockedReason ?: "強化できません" }
            val paid = recipe(account, quote.recipe).first
            CoreEnhancementCatalog.resolve(paid, action.gear, quote, requestId, action.mode)
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
            require(run.dungeon == null) { "深殿の段階報酬を使用してください" }
            require(!run.bossDefeated) { "この討伐報酬は受取済みです" }
            if (run.trialId == null) require(account.maps.size < CoreLoopCatalog.MAX_MAPS) { "地図の保管庫が満杯です" }
            val nextTier = if (account.journey.legacy || run.map.level >= CoreJourneyRules.ceiling(run.map.tier)) (run.map.tier + 1).coerceAtMost(4) else run.map.tier
            val message = if (run.trialId == null) "討伐完了！ T$nextTier の地図・討伐証・試練の欠片を獲得しました"
                else "専用ボス討伐！ 専用オーブ2個・高揚1個・討伐証を獲得しました"
            val outputs = if (run.trialId == null)
                mapOf(CoreMaterial(CoreResource.BOSS_SIGIL, run.map.tier) to 2L,
                    CoreMaterial(CoreResource.COMBAT_TOKEN, run.map.tier) to 12L,
                    CoreMaterial(CoreResource.GATHERING_TABLET) to 1L,
                    CoreMaterial(CoreResource.POTION) to 2L)
                else mapOf(CoreMaterial(CoreResource.BOSS_SIGIL, run.map.tier) to 2L, CoreMaterial(CoreResource.COMBAT_TOKEN, run.map.tier) to 12L)
            val rewarded = recipe(addSource(account, source("boss", run.id, "defeat")), CoreRecipe(message, emptyMap(), outputs)).first
                .copy(journey = account.journey.gain(CoreJourneyRules.reward(run.map.tier, true)))
            if (run.trialId == null) {
                val rewardMap = CoreOwnedMap(derived(requestId, "boss-map"), run.map.seed xor requestId.leastSignificantBits, nextTier,
                    level = if (nextTier != run.map.tier) CoreJourneyRules.floor(nextTier) else (run.map.level + 2).coerceAtMost(CoreJourneyRules.ceiling(nextTier)))
                grantFragments(rewarded, CoreActivityKind.TRIAL, 1L).copy(unlockedMapTier = maxOf(account.unlockedMapTier, nextTier),
                    maps = account.maps + rewardMap, activeRun = run.copy(bossDefeated = true)) to message
            } else {
                val kind = CoreActivityKind.entries.single { it.bossId == run.trialId }
                grantCurrencies(rewarded, mapOf(kind.currency to 2L, CoreCraftingCurrency.EXALTED to 1L))
                    .copy(activeRun = run.copy(bossDefeated = true)) to message
            }
        }
        is CoreAction.Refine -> {
            requireHub(account)
            val (quote, progress) = CoreProfessions.refineQuote(account, action.resource, action.tier, action.batches)
            val refined = recipe(account, quote)
            CoreEnhancementCatalog.gainMastery(refined.first.copy(professions = account.professions + (CoreProfession.refining(action.resource) to progress)), action.batches.toLong()) to refined.second
        }
        CoreAction.UpgradeWeapon -> {
            requireHub(account)
            require(!account.weaponBroken) { "先に武器を修理してください" }
            require(account.weaponTier < 4) { "武器は最高Tierです" }
            val upgraded = recipe(account, CoreLoopCatalog.weaponUpgrade(account.weaponTier))
            CoreEnhancementCatalog.gainMastery(upgraded.first.copy(weaponTier = account.weaponTier + 1, weaponIdentity = account.weaponIdentity.copy(itemLevel = 0)), 5) to upgraded.second
        }
        CoreAction.UpgradeArmor -> {
            requireHub(account)
            require(!account.armorBroken) { "先に防具を修理してください" }
            require(account.armorTier < 4) { "防具は最高Tierです" }
            val upgraded = recipe(account, CoreLoopCatalog.armorUpgrade(account.armorTier))
            CoreEnhancementCatalog.gainMastery(upgraded.first.copy(armorTier = account.armorTier + 1, armorIdentity = account.armorIdentity.copy(itemLevel = 0)), 5) to upgraded.second
        }
        is CoreAction.Exchange -> error("戦利品券と採取素材の交換は終了しました。採取または市場で入手してください")
        is CoreAction.Craft -> {
            requireHub(account)
            val crafted = recipe(account, CoreLoopCatalog.craft(action.resource, action.batches, action.tier))
            CoreEnhancementCatalog.gainMastery(crafted.first, action.batches.toLong()) to crafted.second
        }
        is CoreAction.ClaimMap -> {
            requireHub(account)
            require(action.tier in 1..account.unlockedMapTier) { "そのTierはまだ解放されていません" }
            require(account.maps.size < CoreLoopCatalog.MAX_MAPS) { "地図の保管庫が満杯です" }
            val cost = if (action.tier == 1) emptyMap() else mapOf(CoreMaterial(CoreResource.COMBAT_TOKEN, action.tier - 1) to 1L)
            val claimed = recipe(account, CoreRecipe("T${action.tier} の遠征地図を受け取りました", cost, emptyMap()))
            val level = action.level ?: account.journey.level.coerceIn(CoreJourneyRules.floor(action.tier), CoreJourneyRules.ceiling(action.tier))
            require(account.journey.legacy || level <= maxOf(CoreJourneyRules.floor(action.tier), account.journey.level + 2)) { "その地図レベルはまだ高すぎます" }
            claimed.first.copy(maps = account.maps + CoreOwnedMap(derived(requestId, "map"), action.seed, action.tier, level = level)) to claimed.second
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
            require(!run.bossDefeated && account.claimedSources.none { it.startsWith("gather/${run.id}/") || it.startsWith("combat/${run.id}/") || it.startsWith("affix/${run.id}/") || it.startsWith("activity/${run.id}/") || it.startsWith("dungeon/${run.id}/") }) {
                "開始済みの遠征は中断返却できません"
            }
            if (run.dungeon != null) account.copy(activeRun = null) to "深殿の準備を中止しました（入場料なし）"
            else if (run.trialId == null) {
                require(account.maps.size < CoreLoopCatalog.MAX_MAPS) { "地図の保管庫が満杯です" }
                account.copy(maps = account.maps + run.map, activeRun = null) to "遠征の準備に失敗したため地図を返却しました"
            } else {
                val kind = CoreActivityKind.entries.single { it.bossId == run.trialId }
                grantFragments(account, kind, CoreCraftingCatalog.TRIAL_ENTRY_FRAGMENTS).copy(activeRun = null) to "準備に失敗したため入場の欠片3個を返却しました"
            }
        }
        is CoreAction.FinishRun -> {
            requireRun(account, action.runId)
            account.copy(activeRun = null, journey = if (account.journey.knows(2)) account.journey.learn(3) else account.journey,
                claimedSources = account.claimedSources.filterTo(linkedSetOf()) { it.startsWith("run/") }) to "拠点へ帰還しました。獲得品は保管済みです"
        }
        is CoreAction.Consume -> {
            require(action.resource in setOf(CoreResource.POTION, CoreResource.WHETSTONE)) { "使用できないアイテムです" }
            require(account.activeRun != null) { "遠征中のみ使用できます" }
            recipe(account, CoreRecipe("${action.resource.displayName}を使用しました", mapOf(CoreMaterial(action.resource) to 1L), emptyMap()))
        }
    }

    private fun stored(a: CoreAccount, id: UUID): CoreStoredGear {
        require(a.offers.none { it.gearId == id }) { "出品中です。先に取り下げてください" }
        return a.storedGear.singleOrNull { it.identity.id == id } ?: error("装備が見つかりません")
    }
    private fun list(a: CoreAccount, offer: CoreMarketOffer): Pair<CoreAccount, String> {
        require(a.offers.size < CoreEconomy.MAX_OFFERS) { "出品上限です。先に取り下げてください" }
        return a.copy(offers = a.offers + offer) to "銀貨${offer.price}枚で出品しました。成約時に手数料${CoreEconomy.fee(offer.price)}枚が差し引かれます"
    }

    private fun buy(buyer: CoreAccount, operation: CoreOperation, fingerprint: String): CoreTransactionResult {
        val action = operation.action as CoreAction.BuyOffer
        val proposal = runCatching {
            requireHub(buyer)
            require(action.seller != buyer.playerId) { "自分の出品は購入できません" }
            val seller = (repository.load(action.seller) as? CoreRepositoryLoad.Loaded)?.account ?: error("出品者のデータを読み込めません")
            val offer = seller.offers.singleOrNull { it.id == action.id } ?: error("売り切れか取り下げ済みです")
            require(offer.price == action.price) { "価格が変わりました。選び直してください" }
            require(buyer.silver >= offer.price) { "銀貨が${offer.price - buyer.silver}枚不足しています" }
            var paid = buyer.copy(silver = buyer.silver - offer.price)
            val removed = seller.copy(offers = seller.offers.filterNot { it.id == offer.id })
            val credited = removed.copy(silver = Math.addExact(seller.silver, offer.price - CoreEconomy.fee(offer.price)))
            val sold = if (offer.gearId != null) {
                require(paid.storedGear.size < CoreEconomy.MAX_GEAR) { "装備庫が満杯です" }
                val item = seller.storedGear.single { it.identity.id == offer.gearId }
                paid = paid.copy(storedGear = paid.storedGear + item, journey = paid.journey.learn(4))
                credited.copy(storedGear = credited.storedGear.filterNot { it.identity.id == offer.gearId })
            } else {
                paid = recipe(paid, CoreRecipe("購入", emptyMap(), mapOf(requireNotNull(offer.material) to offer.quantity))).first
                credited
            }
            val message = "銀貨${offer.price}枚で購入しました。${if (offer.gearId != null) "装備庫" else "素材倉庫"}へ保管しました"
            val revision = buyer.revision + 1
            Triple(paid.copy(revision = revision, receipts = retainedReceipts(buyer) + (operation.requestId to CoreReceipt(fingerprint, revision, message))),
                sold.copy(revision = seller.revision + 1), message)
        }.getOrElse { return CoreTransactionResult(CoreTransactionStatus.REJECTED, buyer, it.message?.take(256) ?: "購入できません") }
        return when (repository.commitTrade(proposal.first, proposal.second)) {
            CoreRepositorySave.Saved -> {
                accounts[buyer.playerId] = proposal.first
                if (action.seller in accounts) accounts[action.seller] = proposal.second
                runCatching { refreshMarket() }
                CoreTransactionResult(CoreTransactionStatus.COMMITTED, proposal.first, proposal.third)
            }
            else -> {
                // A durable intent may already exist: never claim rollback or continue with stale balances.
                accounts.clear(); market = emptyList(); buyOrders = emptyList()
                CoreTransactionResult(CoreTransactionStatus.UNAVAILABLE, null, "取引結果を確認できません。再接続すると保存済みの取引を復旧します")
            }
        }
    }

    // Old requests remain STALE by expectedRevision after this bounded replay window.
    private fun retainedReceipts(a: CoreAccount) = a.receipts.entries.sortedBy { it.value.revision }.takeLast(4095).associate { it.toPair() }

    private fun fillOrder(seller: CoreAccount, operation: CoreOperation, fingerprint: String): CoreTransactionResult {
        val action = operation.action as CoreAction.FillBuyOrder
        val proposal = runCatching {
            requireHub(seller)
            require(action.buyer != seller.playerId) { "自分の注文には納品できません" }
            val buyer = (repository.load(action.buyer) as? CoreRepositoryLoad.Loaded)?.account ?: error("注文者のデータを読み込めません")
            val order = buyer.buyOrders.singleOrNull { it.id == action.id } ?: error("注文は成立済みか取り下げ済みです")
            require(order.unitPrice == action.unitPrice && action.quantity in 1..order.remaining) { "注文の価格か残数が変わりました" }
            val amount = Math.multiplyExact(order.unitPrice, action.quantity.toLong())
            var sold = seller.copy(silver = Math.addExact(seller.silver, amount - CoreEconomy.fee(amount)))
            var received = buyer
            if (order.slot != null) {
                require(action.quantity == 1 && action.gearId != null) { "装備を1個選択してください" }
                val item = stored(seller, action.gearId)
                require(order.accepts(item)) { "同Tier・同系統の未破損+0装備が必要です" }
                require(buyer.storedGear.size < CoreEconomy.MAX_GEAR) { "注文者の装備庫が満杯です" }
                received = buyer.copy(storedGear = buyer.storedGear + item)
                sold = sold.copy(storedGear = sold.storedGear.filterNot { it.identity.id == item.identity.id })
            } else {
                require(action.gearId == null)
                val material = CoreMaterial(requireNotNull(order.resource), order.tier)
                sold = recipe(sold, CoreRecipe("注文へ納品", mapOf(material to action.quantity.toLong()), emptyMap())).first
                received = recipe(received, CoreRecipe("注文品を保管", emptyMap(), mapOf(material to action.quantity.toLong()))).first
            }
            val remaining = order.remaining - action.quantity
            received = received.copy(buyOrders = buyer.buyOrders.mapNotNull { if (it.id != order.id) it else if (remaining == 0) null else order.copy(remaining = remaining) })
            val message = "注文へ納品しました。手数料を引き銀貨${amount - CoreEconomy.fee(amount)}枚を受け取りました"
            Triple(sold.copy(revision = seller.revision + 1, receipts = retainedReceipts(seller) +
                (operation.requestId to CoreReceipt(fingerprint, seller.revision + 1, message))), received.copy(revision = buyer.revision + 1), message)
        }.getOrElse { return CoreTransactionResult(CoreTransactionStatus.REJECTED, seller, it.message?.take(256) ?: "納品できません") }
        return when (repository.commitTrade(proposal.first, proposal.second)) {
            CoreRepositorySave.Saved -> {
                accounts[seller.playerId] = proposal.first
                if (action.buyer in accounts) accounts[action.buyer] = proposal.second
                runCatching { refreshMarket() }
                CoreTransactionResult(CoreTransactionStatus.COMMITTED, proposal.first, proposal.third)
            }
            else -> {
                accounts.clear(); market = emptyList(); buyOrders = emptyList()
                CoreTransactionResult(CoreTransactionStatus.UNAVAILABLE, null, "取引結果を確認できません。再接続で復旧します")
            }
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
