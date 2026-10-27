package dev.projects.server.coreloop

import java.util.Collections
import java.util.UUID
import kotlin.math.pow

/** `research` goods are tier-1 market items of the magic path: anomalous samples and research products. */
enum class CoreResource(val displayName: String, val raw: Boolean = false, val research: Boolean = false) {
    WOOD("木材", true), ORE("鉱石", true), STONE("石材", true), HIDE("獣皮", true), FIBER("植物繊維", true),
    BOARD("板材"), INGOT("インゴット"), STONE_BLOCK("加工石材"), LEATHER("なめし革"), CLOTH("布"),
    BOSS_SIGIL("討伐証"), COMBAT_TOKEN("戦利品券"), POTION("回復薬"), GATHERING_TABLET("採取の石板"), WHETSTONE("砥石"),
    AFFIX_DUST("刻印粉"),
    // Names match AnomalousMaterial so the first-magic sidecar migrates by name.
    MOONBELL("月鈴草", research = true), EMBER_MOSS("熾火苔", research = true), HOLLOW_CRYSTAL("空洞水晶", research = true),
    WARM_ORE("微熱鉱片", research = true), TIDEWING_FEATHER("潮羽", research = true), WITHERED_CORE("枯れた核", research = true),
    ARCANE_CATALYST("星鉄の触媒", research = true), MENDING_SEAL("修復の封蝋", research = true),
}

data class CoreMaterial(val resource: CoreResource, val tier: Int = 1) {
    init { require(tier in 1..4) }
    val displayName: String get() = "T$tier ${resource.displayName}"
}

data class CoreMapModifier(val discipline: String?, val stat: String, val percent: Int) {
    init {
        require(discipline == null || discipline in setOf("skinning", "woodcutting", "quarrying", "mining", "herbalism"))
        require(stat in setOf("amount", "quality", "dense_regions"))
        require(percent in 1..200)
    }
    val key: String get() = "${discipline ?: "all"}:$stat"
}

/** [zone] is the atlas zone the map was drawn for (null for maps from rewards, surveys and trials). */
class CoreOwnedMap(val id: UUID, val seed: Long, val tier: Int, modifiers: List<CoreMapModifier> = emptyList(), val level: Int = CoreJourneyRules.floor(tier),
    val zone: Int? = null) {
    val modifiers: List<CoreMapModifier> = Collections.unmodifiableList(modifiers.toList())
    init {
        require(tier in 1..4)
        require(level in CoreJourneyRules.floor(tier)..CoreJourneyRules.ceiling(tier))
        require(modifiers.size <= 3 && modifiers.map { it.key }.distinct().size == modifiers.size)
        require(zone == null || zone in 0..CoreZoneRecord.MAX_ZONE_ID)
    }
    fun withModifier(modifier: CoreMapModifier) = CoreOwnedMap(id, seed, tier, modifiers + modifier, level, zone)
    override fun toString() = "CoreOwnedMap($id,$seed,$tier,$modifiers,$level,$zone)"
}

/** A player's own record of one atlas zone: boss clears there and the highest enemy level cleared. */
data class CoreZoneRecord(val clears: Int, val bestLevel: Int) {
    init { require(clears in 1..MAX_CLEARS && bestLevel in 1..CoreJourneyRules.MAX_LEVEL) }
    fun cleared(level: Int) = CoreZoneRecord((clears + 1).coerceAtMost(MAX_CLEARS), maxOf(bestLevel, level))
    companion object {
        const val MAX_ZONE_ID = 255
        const val MAX_CLEARS = 1_000_000
    }
}

data class CoreActiveRun(val id: UUID, val map: CoreOwnedMap, val bossDefeated: Boolean = false, val trialId: String? = null) {
    init { require(trialId == null || CoreActivityKind.entries.any { it.bossId == trialId }) }
}
data class CoreReceipt(val fingerprint: String, val revision: Long, val message: String)
private val RECEIPT_FINGERPRINT = Regex("[0-9a-f]{64}")

data class CoreArmorPiece(val identity: CoreGearIdentity, val tier: Int = 1,
    val rarity: CoreGearRarity = CoreGearRarity.NORMAL,
    val enhancement: CoreEnhancementState = CoreEnhancementState(), val broken: Boolean = false) {
    init { require(tier in 1..4) }
}

/** One immutable, independently persisted aggregate; inventories are projections of this ledger. */
class CoreAccount(
    val playerId: UUID,
    val revision: Long = 0,
    balances: Map<CoreMaterial, Long> = mapOf(CoreMaterial(CoreResource.POTION) to 3L),
    val weaponTier: Int = 1,
    val armorTier: Int = 1,
    val unlockedMapTier: Int = 1,
    maps: List<CoreOwnedMap> = emptyList(),
    val activeRun: CoreActiveRun? = null,
    receipts: Map<UUID, CoreReceipt> = emptyMap(),
    claimedSources: Set<String> = emptySet(),
    affixStones: List<CoreAffixStone> = emptyList(),
    equippedAffixes: List<CoreEquippedAffix> = emptyList(),
    val weaponRarity: CoreGearRarity = CoreCraftingCatalog.inferRarity(equippedAffixes, CoreGearSlot.WEAPON),
    val armorRarity: CoreGearRarity = CoreCraftingCatalog.inferRarity(equippedAffixes, CoreGearSlot.ARMOR),
    currencies: Map<CoreCraftingCurrency, Long> = emptyMap(),
    fragments: Map<CoreActivityKind, Long> = emptyMap(),
    legacyLayouts: Set<CoreGearSlot> = CoreCraftingCatalog.legacyLayouts(equippedAffixes, weaponRarity, armorRarity),
    val craftingSeed: Long = UUID.randomUUID().leastSignificantBits,
    val weaponEnhancement: CoreEnhancementState = CoreEnhancementState(),
    val armorEnhancement: CoreEnhancementState = CoreEnhancementState(),
    val smithingXp: Long = 0,
    val silver: Long = 0,
    val weaponIdentity: CoreGearIdentity = CoreGearIdentity.legacy(playerId, CoreGearSlot.WEAPON),
    val armorIdentity: CoreGearIdentity = CoreGearIdentity.legacy(playerId, CoreGearSlot.ARMOR),
    storedGear: List<CoreStoredGear> = emptyList(),
    offers: List<CoreMarketOffer> = emptyList(),
    val deliveryDay: Long = 0,
    val deliveries: Int = 0,
    val weaponBroken: Boolean = false,
    val armorBroken: Boolean = false,
    professions: Map<CoreProfession, CoreProfessionProgress> = emptyMap(),
    val surveyPoints: Long = 0,
    buyOrders: List<CoreBuyOrder> = emptyList(),
    val journey: CoreJourney = CoreJourney.fresh(),
    armorParts: Map<CoreGearSlot, CoreArmorPiece> = CoreGearSlot.armorSlots.filterNot { it == CoreGearSlot.CHEST }
        .associateWith { slot -> CoreArmorPiece(CoreGearIdentity.legacy(playerId, slot).copy(quality = armorIdentity.quality),
            armorTier, CoreCraftingCatalog.inferRarity(equippedAffixes, slot), armorEnhancement, armorBroken) },
    zoneRecords: Map<Int, CoreZoneRecord> = emptyMap(),
) {
    /** Atlas clears by zone id. Unknown ids are kept so a removed zone never corrupts a save. */
    val zoneRecords: Map<Int, CoreZoneRecord> = Collections.unmodifiableMap(LinkedHashMap(zoneRecords.toSortedMap()))
    /** CHEST owns the v10 armor fields so older callers and fixtures remain readable. */
    val armorParts = Collections.unmodifiableMap(armorParts.toMap())
    fun armor(slot: CoreGearSlot): CoreArmorPiece = when (CoreGearSlot.equipped(slot)) {
        CoreGearSlot.CHEST -> CoreArmorPiece(armorIdentity, armorTier, armorRarity, armorEnhancement, armorBroken)
        else -> requireNotNull(armorParts[slot]) { "防具部位ではありません: $slot" }
    }
    fun withArmor(slot: CoreGearSlot, piece: CoreArmorPiece): CoreAccount = when (CoreGearSlot.equipped(slot)) {
        CoreGearSlot.CHEST -> copy(armorIdentity = piece.identity, armorTier = piece.tier,
            armorRarity = piece.rarity, armorEnhancement = piece.enhancement, armorBroken = piece.broken)
        CoreGearSlot.HEAD, CoreGearSlot.LEGS, CoreGearSlot.FEET -> copy(armorParts = armorParts + (slot to piece))
        else -> error("防具部位ではありません: $slot")
    }
    val professions = Collections.unmodifiableMap(professions.toMap())
    val buyOrders = Collections.unmodifiableList(buyOrders.toList())
    val storedGear = Collections.unmodifiableList(storedGear.toList())
    val offers = Collections.unmodifiableList(offers.toList())
    val balances: Map<CoreMaterial, Long> = Collections.unmodifiableMap(LinkedHashMap(balances))
    val maps: List<CoreOwnedMap> = Collections.unmodifiableList(maps.toList())
    val receipts: Map<UUID, CoreReceipt> = Collections.unmodifiableMap(LinkedHashMap(receipts))
    val claimedSources: Set<String> = Collections.unmodifiableSet(LinkedHashSet(claimedSources))
    val affixStones: List<CoreAffixStone> = Collections.unmodifiableList(affixStones.toList())
    val equippedAffixes: List<CoreEquippedAffix> = Collections.unmodifiableList(equippedAffixes.toList())
    val currencies: Map<CoreCraftingCurrency, Long> = Collections.unmodifiableMap(LinkedHashMap(currencies))
    val fragments: Map<CoreActivityKind, Long> = Collections.unmodifiableMap(LinkedHashMap(fragments))
    val legacyLayouts: Set<CoreGearSlot> = Collections.unmodifiableSet(legacyLayouts.toSet())
    init {
        require(surveyPoints in 0..1_000_000_000L)
        require(zoneRecords.keys.all { it in 0..CoreZoneRecord.MAX_ZONE_ID })
        require(buyOrders.size <= CoreEconomy.MAX_OFFERS && buyOrders.map { it.id }.distinct().size == buyOrders.size)
        require(revision >= 0 && weaponTier in 1..4 && armorTier in 1..4 && unlockedMapTier in 1..4)
        require(smithingXp in 0..CoreEnhancementCatalog.MAX_SMITHING_XP)
        require(silver in 0..CoreEconomy.MAX_SILVER && deliveries in 0..CoreEconomy.DAILY_DELIVERIES && deliveryDay >= 0)
        require(storedGear.size <= CoreEconomy.MAX_GEAR && offers.size <= CoreEconomy.MAX_OFFERS)
        require(armorParts.keys == setOf(CoreGearSlot.HEAD, CoreGearSlot.LEGS, CoreGearSlot.FEET))
        val identities = storedGear.map { it.identity.id } + weaponIdentity.id + armorIdentity.id + armorParts.values.map { it.identity.id }
        require(identities.distinct().size == identities.size)
        (storedGear.map { it.identity to it.tier } + listOf(weaponIdentity to weaponTier, armorIdentity to armorTier) +
            armorParts.values.map { it.identity to it.tier }).forEach { (id, tier) ->
            require(id.itemLevel == 0 || id.itemLevel in CoreJourneyRules.floor(tier)..CoreJourneyRules.ceiling(tier))
        }
        require(offers.map { it.id }.distinct().size == offers.size)
        require(offers.mapNotNull { it.gearId }.distinct().size == offers.count { it.gearId != null })
        require(offers.all { offer -> offer.gearId == null || storedGear.any { it.identity.id == offer.gearId && !it.identity.bound } })
        storedGear.forEach { item ->
            require(item.affixes.map { it.index }.distinct().size == item.affixes.size)
            require(item.affixes.map { it.stone.modId }.distinct().size == item.affixes.size)
            require(item.affixes.all { it.index < item.rarity.capacity && it.stone.tier <= item.tier &&
                CoreAffixCatalog.definition(it.stone)?.allowedGear?.contains(item.slot) != false })
            require(item.legacy || CoreCraftingCatalog.validLayout(item.affixes, item.rarity))
        }
        require(balances.size <= CoreLoopCatalog.MAX_BALANCES && balances.values.all { it in 0..CoreLoopCatalog.MAX_BALANCE })
        require(maps.size <= CoreLoopCatalog.MAX_MAPS && maps.map { it.id }.distinct().size == maps.size)
        require(activeRun == null || maps.none { it.id == activeRun.map.id })
        require(receipts.size <= CoreLoopCatalog.MAX_RECEIPTS && claimedSources.size <= CoreLoopCatalog.MAX_SOURCES)
        require(receipts.values.all { it.revision in 1..revision && it.fingerprint.matches(RECEIPT_FINGERPRINT) && it.message.length <= 256 })
        require(claimedSources.all { it.length in 1..192 && '\n' !in it && '\t' !in it && '\r' !in it })
        require(affixStones.size <= CoreAffixCatalog.MAX_STONES && equippedAffixes.size <= 30)
        require(currencies.values.all { it in 0..CoreLoopCatalog.MAX_BALANCE } && fragments.values.all { it in 0..CoreLoopCatalog.MAX_BALANCE })
        val allStones = affixStones + equippedAffixes.map { it.stone } + storedGear.flatMap { it.affixes.map { affix -> affix.stone } }
        require(allStones.map { it.id }.distinct().size == allStones.size) { "MODの識別番号が重複しています" }
        require(equippedAffixes.map { it.gear to it.index }.distinct().size == equippedAffixes.size)
        require(equippedAffixes.map { it.gear to it.stone.modId }.distinct().size == equippedAffixes.size)
        require(equippedAffixes.all { it.index < CoreAffixCatalog.capacity(this, it.gear) && it.stone.tier <= CoreAffixCatalog.gearTier(this, it.gear) })
        // Known malformed rolls are corrupt data; unknown definitions are retained with effects disabled.
        require(allStones.all { CoreAffixCatalog.definition(it) == null || CoreAffixCatalog.valid(it) })
        require(equippedAffixes.all { CoreAffixCatalog.definition(it.stone)?.allowedGear?.contains(it.gear) != false })
        CoreGearSlot.equipSlots.forEach { gear ->
            require(gear in legacyLayouts || CoreCraftingCatalog.validLayout(equippedAffixes.filter { it.gear == gear }, CoreAffixCatalog.rarity(this, gear)))
        }
    }
    fun amount(resource: CoreResource, tier: Int = 1): Long = balances[CoreMaterial(resource, tier)] ?: 0
    fun amount(material: CoreMaterial): Long = balances[material] ?: 0
    fun amount(currency: CoreCraftingCurrency): Long = currencies[currency] ?: 0
    fun amount(kind: CoreActivityKind): Long = fragments[kind] ?: 0

    fun copy(
        revision: Long = this.revision, balances: Map<CoreMaterial, Long> = this.balances,
        weaponTier: Int = this.weaponTier, armorTier: Int = this.armorTier,
        unlockedMapTier: Int = this.unlockedMapTier, maps: List<CoreOwnedMap> = this.maps,
        activeRun: CoreActiveRun? = this.activeRun, receipts: Map<UUID, CoreReceipt> = this.receipts,
        claimedSources: Set<String> = this.claimedSources,
        affixStones: List<CoreAffixStone> = this.affixStones,
        equippedAffixes: List<CoreEquippedAffix> = this.equippedAffixes,
        weaponRarity: CoreGearRarity = this.weaponRarity, armorRarity: CoreGearRarity = this.armorRarity,
        currencies: Map<CoreCraftingCurrency, Long> = this.currencies,
        fragments: Map<CoreActivityKind, Long> = this.fragments,
        legacyLayouts: Set<CoreGearSlot> = this.legacyLayouts,
        craftingSeed: Long = this.craftingSeed,
        weaponEnhancement: CoreEnhancementState = this.weaponEnhancement,
        armorEnhancement: CoreEnhancementState = this.armorEnhancement,
        smithingXp: Long = this.smithingXp,
        silver: Long = this.silver,
        weaponIdentity: CoreGearIdentity = this.weaponIdentity,
        armorIdentity: CoreGearIdentity = this.armorIdentity,
        storedGear: List<CoreStoredGear> = this.storedGear,
        offers: List<CoreMarketOffer> = this.offers,
        deliveryDay: Long = this.deliveryDay,
        deliveries: Int = this.deliveries,
        weaponBroken: Boolean = this.weaponBroken,
        armorBroken: Boolean = this.armorBroken,
        professions: Map<CoreProfession, CoreProfessionProgress> = this.professions,
        surveyPoints: Long = this.surveyPoints,
        buyOrders: List<CoreBuyOrder> = this.buyOrders,
        journey: CoreJourney = this.journey,
        armorParts: Map<CoreGearSlot, CoreArmorPiece> = this.armorParts,
        zoneRecords: Map<Int, CoreZoneRecord> = this.zoneRecords,
    ) = CoreAccount(playerId, revision, balances, weaponTier, armorTier, unlockedMapTier, maps, activeRun, receipts, claimedSources, affixStones, equippedAffixes,
        weaponRarity, armorRarity, currencies, fragments, legacyLayouts, craftingSeed, weaponEnhancement, armorEnhancement, smithingXp,
        silver, weaponIdentity, armorIdentity, storedGear, offers, deliveryDay, deliveries, weaponBroken, armorBroken, professions, surveyPoints, buyOrders, journey, armorParts, zoneRecords)
}

data class CoreOperation(val requestId: UUID, val expectedRevision: Long, val action: CoreAction)

sealed interface CoreAction {
    data class ChooseClass(val job: CoreClass) : CoreAction
    data class SelectSkill(val slot: Int, val choice: Int) : CoreAction
    data class TakePassive(val node: Int) : CoreAction
    /** Refunds cost silver (a gold sink); taking a node is free. */
    data class RefundPassive(val node: Int) : CoreAction
    data object ResetPassives : CoreAction
    /** Emitted only from authoritative server gameplay, never a menu's arbitrary completion claim. */
    data class LearnCombat(val lesson: Int) : CoreAction
    data class TemperEquipment(val slot: CoreGearSlot) : CoreAction
    data class SurveyMap(val tier: Int, val raw: CoreResource, val seed: Long) : CoreAction
    data class PlaceBuyOrder(val resource: CoreResource?, val slot: CoreGearSlot?, val tier: Int, val quantity: Int, val unitPrice: Long) : CoreAction
    data class CancelBuyOrder(val id: UUID) : CoreAction
    data class FillBuyOrder(val buyer: UUID, val id: UUID, val unitPrice: Long, val quantity: Int = 1, val gearId: UUID? = null) : CoreAction
    data class Manufacture(val slot: CoreGearSlot, val tier: Int, val count: Int = 1, val base: CoreWeaponBase = CoreWeaponBase.STANDARD) : CoreAction
    data class Equip(val id: UUID) : CoreAction
    data class Repair(val slot: CoreGearSlot, val input: UUID) : CoreAction
    data class Deliver(val id: UUID) : CoreAction
    data class RedeemTokens(val tier: Int, val quantity: Int) : CoreAction
    data class ListGear(val id: UUID, val price: Long) : CoreAction
    data class ListMaterial(val material: CoreMaterial, val quantity: Long, val price: Long) : CoreAction
    data class CancelOffer(val id: UUID) : CoreAction
    data class BuyOffer(val seller: UUID, val id: UUID, val price: Long) : CoreAction
    data class Gather(val runId: UUID, val nodeId: String, val resource: CoreResource, val quantity: Int) : CoreAction
    data class CombatReward(val runId: UUID, val encounterId: String, val quantity: Int = 2) : CoreAction
    data class BossReward(val runId: UUID) : CoreAction
    data class AffixLoot(val runId: UUID, val sourceId: String, val kind: CoreLootKind) : CoreAction
    data class CraftEquipment(val gear: CoreGearSlot, val currency: CoreCraftingCurrency) : CoreAction
    data class EnhanceEquipment(val gear: CoreGearSlot, val mode: CoreEnhancementMode = CoreEnhancementMode.STANDARD) : CoreAction
    data class ConvertLegacyStone(val stoneId: UUID) : CoreAction
    data class ActivityReward(val runId: UUID, val sourceId: String, val kind: CoreActivityKind) : CoreAction
    data class StartTrial(val bossId: String, val tier: Int, val runId: UUID) : CoreAction
    /** Replacing an occupied slot requires its exact currently displayed identity. */
    data class ApplyAffix(val gear: CoreGearSlot, val index: Int, val stoneId: UUID, val expectedReplacedStoneId: UUID? = null) : CoreAction
    data class ExtractAffix(val gear: CoreGearSlot, val index: Int, val expectedStoneId: UUID) : CoreAction
    data class RerollAffix(val stoneId: UUID) : CoreAction
    data class SalvageAffix(val stoneId: UUID) : CoreAction
    data class Refine(val resource: CoreResource, val tier: Int, val batches: Int = 1) : CoreAction
    data object UpgradeWeapon : CoreAction
    data object UpgradeArmor : CoreAction
    data class UpgradeArmorPart(val slot: CoreGearSlot) : CoreAction
    data class Exchange(val resource: CoreResource, val tier: Int, val batches: Int = 1) : CoreAction
    data class Craft(val resource: CoreResource, val batches: Int = 1, val tier: Int = 1) : CoreAction
    /** [zone]: the atlas zone the map is drawn for; its tier must match. */
    data class ClaimMap(val tier: Int, val seed: Long, val level: Int? = null, val zone: Int? = null) : CoreAction
    data class ApplyTablet(val mapId: UUID, val modifier: CoreMapModifier) : CoreAction
    data class StartRun(val mapId: UUID, val runId: UUID) : CoreAction
    /** Only failed generation/transfer refunds a map. Normal withdrawal/death/disconnect does not. */
    data class AbortRun(val runId: UUID) : CoreAction
    data class FinishRun(val runId: UUID) : CoreAction
    data class Consume(val resource: CoreResource) : CoreAction
    /** Magic path. Each spends ledger goods and destroys a silver fee; the first-magic sidecar is updated after commit. */
    data class ResearchDistill(val material: CoreResource) : CoreAction
    data class ResearchInk(val material: CoreResource) : CoreAction
    /** The caller (server) has verified the personal research unlock and Essentia in the sidecar. */
    data class ResearchCraft(val researchId: String) : CoreAction
    data class SealRepair(val slot: CoreGearSlot) : CoreAction
    /** Server-only: legacy sidecar materials at login, and the dev-only /researchtest top-up. */
    data class GrantResearchMaterials(val materials: Map<CoreResource, Long>, val silverFloor: Long = 0) : CoreAction
}

enum class CoreTransactionStatus { COMMITTED, REPLAYED, REJECTED, STALE, CONFLICT, UNAVAILABLE, SAVE_FAILED }
data class CoreTransactionResult(val status: CoreTransactionStatus, val account: CoreAccount?, val message: String) {
    val successful: Boolean get() = status == CoreTransactionStatus.COMMITTED || status == CoreTransactionStatus.REPLAYED
}

sealed interface CoreAccountLoadResult {
    data class Ready(val account: CoreAccount, val newlyCreated: Boolean) : CoreAccountLoadResult
    data class Invalid(val reason: String) : CoreAccountLoadResult
}

data class CoreRecipe(val displayName: String, val costs: Map<CoreMaterial, Long>, val outputs: Map<CoreMaterial, Long>) {
    fun canAfford(account: CoreAccount): Boolean = costs.all { (item, amount) -> account.amount(item) >= amount }
}

/** Explicit provisional benchmark balance, shared by transaction validation and the hub UI. */
object CoreLoopCatalog {
    const val MAX_BALANCE = 1_000_000L
    const val MAX_BALANCES = 128 // Raised from 64 when research goods joined the ledger; old saves are below either cap.
    const val MAX_MAPS = 32
    const val MAX_RECEIPTS = 16_384
    const val MAX_SOURCES = 16_384
    /** Finished run ids kept to reject reuse; older markers are pruned on the next start/finish. */
    const val RUN_HISTORY = 1_024
    val refined = mapOf(CoreResource.WOOD to CoreResource.BOARD, CoreResource.ORE to CoreResource.INGOT,
        CoreResource.STONE to CoreResource.STONE_BLOCK, CoreResource.HIDE to CoreResource.LEATHER, CoreResource.FIBER to CoreResource.CLOTH)
    fun weaponDamage(tier: Int): Double { require(tier in 1..4); return 1.65.pow(tier - 1) }
    fun armorHealth(tier: Int): Double { require(tier in 1..4); return 100.0 + 30.0 * (tier - 1) }
    fun weaponUpgrade(tier: Int): CoreRecipe {
        require(tier in 1..3)
        return CoreRecipe("T${tier + 1} 武器へ鍛造", mapOf(CoreMaterial(CoreResource.INGOT, tier) to 4L,
            CoreMaterial(CoreResource.BOARD, tier) to 2L, CoreMaterial(CoreResource.BOSS_SIGIL, tier) to 1L), emptyMap())
    }
    fun armorUpgrade(tier: Int): CoreRecipe {
        require(tier in 1..3)
        return CoreRecipe("T${tier + 1} 防具へ仕立て", mapOf(CoreMaterial(CoreResource.LEATHER, tier) to 4L,
            CoreMaterial(CoreResource.CLOTH, tier) to 2L, CoreMaterial(CoreResource.BOSS_SIGIL, tier) to 1L), emptyMap())
    }
    fun refine(resource: CoreResource, tier: Int, batches: Int = 1): CoreRecipe {
        require(batches in 1..64)
        val output = requireNotNull(refined[resource])
        return CoreRecipe("${resource.displayName}を精製", mapOf(CoreMaterial(resource, tier) to 2L * batches),
            mapOf(CoreMaterial(output, tier) to batches.toLong()))
    }
    fun exchange(resource: CoreResource, tier: Int, batches: Int = 1): CoreRecipe {
        require(resource.raw && batches in 1..64)
        return CoreRecipe("戦利品券で${resource.displayName}を交換", mapOf(CoreMaterial(CoreResource.COMBAT_TOKEN, tier) to batches.toLong()),
            mapOf(CoreMaterial(resource, tier) to 4L * batches))
    }
    fun craft(resource: CoreResource, batches: Int = 1, tier: Int = 1): CoreRecipe {
        require(batches in 1..64)
        require(tier in 1..4)
        val unit = when (resource) {
            CoreResource.POTION -> CoreRecipe("回復薬を調合", mapOf(CoreMaterial(CoreResource.CLOTH, tier) to 1L), mapOf(CoreMaterial(resource) to 2L))
            CoreResource.GATHERING_TABLET -> CoreRecipe("採取の石板を彫刻", mapOf(CoreMaterial(CoreResource.STONE_BLOCK, tier) to 1L,
                CoreMaterial(CoreResource.BOARD, tier) to 1L, CoreMaterial(CoreResource.LEATHER, tier) to 1L), mapOf(CoreMaterial(resource) to 1L))
            CoreResource.WHETSTONE -> CoreRecipe("砥石を加工", mapOf(CoreMaterial(CoreResource.STONE_BLOCK, tier) to 1L,
                CoreMaterial(CoreResource.INGOT, tier) to 1L), mapOf(CoreMaterial(resource) to 1L))
            else -> error("この素材は製作できません")
        }
        return CoreRecipe(unit.displayName, unit.costs.mapValues { it.value * batches }, unit.outputs.mapValues { it.value * batches })
    }
}
