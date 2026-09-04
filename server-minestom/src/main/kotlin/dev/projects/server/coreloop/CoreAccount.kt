package dev.projects.server.coreloop

import java.util.Collections
import java.util.UUID
import kotlin.math.pow

enum class CoreResource(val displayName: String, val raw: Boolean = false) {
    WOOD("木材", true), ORE("鉱石", true), STONE("石材", true), HIDE("獣皮", true), FIBER("植物繊維", true),
    BOARD("板材"), INGOT("インゴット"), STONE_BLOCK("加工石材"), LEATHER("なめし革"), CLOTH("布"),
    BOSS_SIGIL("討伐証"), COMBAT_TOKEN("戦利品券"), POTION("回復薬"), GATHERING_TABLET("採取の石板"), WHETSTONE("砥石"),
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

class CoreOwnedMap(val id: UUID, val seed: Long, val tier: Int, modifiers: List<CoreMapModifier> = emptyList()) {
    val modifiers: List<CoreMapModifier> = Collections.unmodifiableList(modifiers.toList())
    init {
        require(tier in 1..4)
        require(modifiers.size <= 3 && modifiers.map { it.key }.distinct().size == modifiers.size)
    }
    fun withModifier(modifier: CoreMapModifier) = CoreOwnedMap(id, seed, tier, modifiers + modifier)
    override fun toString() = "CoreOwnedMap($id,$seed,$tier,$modifiers)"
}

data class CoreActiveRun(val id: UUID, val map: CoreOwnedMap, val bossDefeated: Boolean = false)
data class CoreReceipt(val fingerprint: String, val revision: Long, val message: String)

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
) {
    val balances: Map<CoreMaterial, Long> = Collections.unmodifiableMap(LinkedHashMap(balances))
    val maps: List<CoreOwnedMap> = Collections.unmodifiableList(maps.toList())
    val receipts: Map<UUID, CoreReceipt> = Collections.unmodifiableMap(LinkedHashMap(receipts))
    val claimedSources: Set<String> = Collections.unmodifiableSet(LinkedHashSet(claimedSources))
    init {
        require(revision >= 0 && weaponTier in 1..4 && armorTier in 1..4 && unlockedMapTier in 1..4)
        require(balances.size <= CoreLoopCatalog.MAX_BALANCES && balances.values.all { it in 0..CoreLoopCatalog.MAX_BALANCE })
        require(maps.size <= CoreLoopCatalog.MAX_MAPS && maps.map { it.id }.distinct().size == maps.size)
        require(activeRun == null || maps.none { it.id == activeRun.map.id })
        require(receipts.size <= CoreLoopCatalog.MAX_RECEIPTS && claimedSources.size <= CoreLoopCatalog.MAX_SOURCES)
        require(receipts.values.all { it.revision in 1..revision && it.fingerprint.matches(Regex("[0-9a-f]{64}")) && it.message.length <= 256 })
        require(claimedSources.all { it.length in 1..192 && '\n' !in it && '\t' !in it && '\r' !in it })
    }
    fun amount(resource: CoreResource, tier: Int = 1): Long = balances[CoreMaterial(resource, tier)] ?: 0
    fun amount(material: CoreMaterial): Long = balances[material] ?: 0

    fun copy(
        revision: Long = this.revision, balances: Map<CoreMaterial, Long> = this.balances,
        weaponTier: Int = this.weaponTier, armorTier: Int = this.armorTier,
        unlockedMapTier: Int = this.unlockedMapTier, maps: List<CoreOwnedMap> = this.maps,
        activeRun: CoreActiveRun? = this.activeRun, receipts: Map<UUID, CoreReceipt> = this.receipts,
        claimedSources: Set<String> = this.claimedSources,
    ) = CoreAccount(playerId, revision, balances, weaponTier, armorTier, unlockedMapTier, maps, activeRun, receipts, claimedSources)
}

data class CoreOperation(val requestId: UUID, val expectedRevision: Long, val action: CoreAction)

sealed interface CoreAction {
    data class Gather(val runId: UUID, val nodeId: String, val resource: CoreResource, val quantity: Int) : CoreAction
    data class CombatReward(val runId: UUID, val encounterId: String, val quantity: Int = 2) : CoreAction
    data class BossReward(val runId: UUID) : CoreAction
    data class Refine(val resource: CoreResource, val tier: Int, val batches: Int = 1) : CoreAction
    data object UpgradeWeapon : CoreAction
    data object UpgradeArmor : CoreAction
    data class Exchange(val resource: CoreResource, val tier: Int, val batches: Int = 1) : CoreAction
    data class Craft(val resource: CoreResource, val batches: Int = 1, val tier: Int = 1) : CoreAction
    data class ClaimMap(val tier: Int, val seed: Long) : CoreAction
    data class ApplyTablet(val mapId: UUID, val modifier: CoreMapModifier) : CoreAction
    data class StartRun(val mapId: UUID, val runId: UUID) : CoreAction
    /** Only failed generation/transfer refunds a map. Normal withdrawal/death/disconnect does not. */
    data class AbortRun(val runId: UUID) : CoreAction
    data class FinishRun(val runId: UUID) : CoreAction
    data class Consume(val resource: CoreResource) : CoreAction
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
    const val MAX_BALANCES = 60
    const val MAX_MAPS = 32
    const val MAX_RECEIPTS = 16_384
    const val MAX_SOURCES = 16_384
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
