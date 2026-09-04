package dev.projects.server.coreloop

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Collections
import java.util.Random
import java.util.UUID

enum class CoreEnhancementMode(val displayName: String) { STANDARD("通常強化"), FOCUSED("精錬触媒を使う") }

/** Consecutive failures belong to the next level of this equipment, not a menu or connection. */
data class CoreEnhancementState(val level: Int = 0, val failures: Int = 0) {
    init {
        require(level in 0..CoreEnhancementCatalog.MAX_LEVEL)
        require(failures in 0..if (level == CoreEnhancementCatalog.MAX_LEVEL) 0 else CoreEnhancementCatalog.pityThreshold(level + 1))
    }
}

data class CoreEnhancementQuote(
    val currentLevel: Int, val targetLevel: Int,
    val baseChancePercent: Double, val masteryBonusPercent: Double, val catalystBonusPercent: Double,
    val successChancePercent: Double, val failures: Int, val pityThreshold: Int, val guaranteed: Boolean,
    val recipe: CoreRecipe, val blockedReason: String?,
)

/** Experiment policy v1. Old probability curve, new permanent protection, pity and crafting mastery. */
object CoreEnhancementCatalog {
    const val POLICY_REVISION = 1
    const val MAX_LEVEL = 30
    const val MAX_SMITHING_XP = 200L
    const val XP_PER_RANK = 20L
    const val CATALYST_BONUS_PERCENT = 15.0

    fun state(account: CoreAccount, gear: CoreGearSlot): CoreEnhancementState =
        if (gear == CoreGearSlot.WEAPON) account.weaponEnhancement else account.armorEnhancement

    fun masteryRank(xp: Long): Int { require(xp in 0..MAX_SMITHING_XP); return (xp / XP_PER_RANK).toInt() }
    fun masteryProgress(xp: Long): Int { require(xp in 0..MAX_SMITHING_XP); return if (xp == MAX_SMITHING_XP) XP_PER_RANK.toInt() else (xp % XP_PER_RANK).toInt() }

    fun baseChancePercent(targetLevel: Int): Double {
        require(targetLevel in 1..MAX_LEVEL)
        return when (targetLevel) {
            in 1..5 -> 100.0
            in 6..10 -> 95.0 - (targetLevel - 6) * 7.5
            in 11..15 -> 55.0 - (targetLevel - 11) * 5.0
            in 16..20 -> 30.0 - (targetLevel - 16) * 4.0
            in 21..25 -> 10.0 - (targetLevel - 21) * 1.5
            else -> 3.0 - (targetLevel - 26) * 0.5
        }
    }

    /** This many failed paid attempts guarantee the next attempt. Zero means the base rate is 100%. */
    fun pityThreshold(targetLevel: Int): Int {
        require(targetLevel in 1..MAX_LEVEL)
        return when (targetLevel) { in 1..5 -> 0; in 6..10 -> 4; in 11..20 -> 6; else -> 9 }
    }

    fun weaponDamageMultiplier(level: Int): Double { require(level in 0..MAX_LEVEL); return 1.0 + level * 0.04 }
    fun weaponAttackSpeedPercent(level: Int): Double { require(level in 0..MAX_LEVEL); return level * 0.8 }
    fun armorHealthMultiplier(level: Int): Double { require(level in 0..MAX_LEVEL); return 1.0 + level * 0.02 }

    /** Pure visible inputs only. The next random result is never exposed by this method. */
    fun quote(account: CoreAccount, gear: CoreGearSlot, mode: CoreEnhancementMode = CoreEnhancementMode.STANDARD): CoreEnhancementQuote {
        val state = state(account, gear)
        val maximum = state.level == MAX_LEVEL
        val target = (state.level + 1).coerceAtMost(MAX_LEVEL)
        val threshold = if (maximum) 0 else pityThreshold(target)
        val base = if (maximum) 0.0 else baseChancePercent(target)
        val mastery = masteryRank(account.smithingXp).toDouble()
        val catalyst = if (mode == CoreEnhancementMode.FOCUSED) CATALYST_BONUS_PERCENT else 0.0
        val pityGuaranteed = !maximum && state.failures >= threshold
        val chance = if (maximum) 0.0 else if (pityGuaranteed) 100.0 else (base + mastery + catalyst).coerceAtMost(100.0)
        val guaranteed = !maximum && chance == 100.0
        val costs = linkedMapOf<CoreMaterial, Long>()
        if (!maximum) {
            val tier = CoreAffixCatalog.gearTier(account, gear)
            val unit = (target + 4L) / 5L
            val primary = if (gear == CoreGearSlot.WEAPON) CoreResource.INGOT else CoreResource.LEATHER
            val secondary = if (gear == CoreGearSlot.WEAPON) CoreResource.BOARD else CoreResource.CLOTH
            costs[CoreMaterial(primary, tier)] = unit
            costs[CoreMaterial(secondary, tier)] = (unit + 1) / 2
            costs[CoreMaterial(CoreResource.STONE_BLOCK, tier)] = unit
            if (mode == CoreEnhancementMode.FOCUSED) {
                costs.merge(CoreMaterial(CoreResource.STONE_BLOCK, tier), 1, Long::plus)
                costs.merge(CoreMaterial(CoreResource.CLOTH, tier), 1, Long::plus)
                costs[CoreMaterial(CoreResource.AFFIX_DUST)] = 2
            }
        }
        val recipe = CoreRecipe("${gear.displayName}を+$target へ強化", Collections.unmodifiableMap(costs), emptyMap())
        val blocked = when {
            maximum -> "すでに最大強化 +$MAX_LEVEL です"
            account.activeRun != null -> "拠点で操作してください"
            !recipe.canAfford(account) -> "強化素材が足りません"
            else -> null
        }
        return CoreEnhancementQuote(state.level, target, base, mastery, catalyst, chance,
            state.failures, threshold, guaranteed, recipe, blocked)
    }

    internal fun gainMastery(account: CoreAccount, amount: Long): CoreAccount {
        require(amount in 1..64)
        return account.copy(smithingXp = (account.smithingXp + amount).coerceAtMost(MAX_SMITHING_XP))
    }

    /** Costs were validated and deducted on an immutable proposal. The caller persists both or neither. */
    internal fun resolve(account: CoreAccount, gear: CoreGearSlot, quote: CoreEnhancementQuote, requestId: UUID, mode: CoreEnhancementMode): Pair<CoreAccount, String> {
        require(quote.blockedReason == null)
        require(state(account, gear).level == quote.currentLevel && quote.currentLevel < MAX_LEVEL)
        val id = UUID.nameUUIDFromBytes("enhancement-v$POLICY_REVISION/${account.craftingSeed}/${account.playerId}/${account.revision}/$requestId/$gear/$mode".toByteArray(UTF_8))
        val random = Random(id.leastSignificantBits xor id.mostSignificantBits)
        val success = random.nextDouble() * 100.0 < quote.successChancePercent
        val next = if (success) CoreEnhancementState(quote.targetLevel) else CoreEnhancementState(quote.currentLevel, quote.failures + 1)
        val updated = gainMastery(account.copy(
            weaponEnhancement = if (gear == CoreGearSlot.WEAPON) next else account.weaponEnhancement,
            armorEnhancement = if (gear == CoreGearSlot.ARMOR) next else account.armorEnhancement,
        ), 1)
        val message = if (success) "強化成功！ ${gear.displayName}が +${next.level} になりました（鍛冶経験 +1）"
            else "強化失敗。+${next.level} とMODは保護されました。天井 ${next.failures}/${quote.pityThreshold}（鍛冶経験 +1）"
        return updated to message
    }
}
