package dev.projects.server.coreloop

import kotlin.math.sqrt
import kotlin.random.Random

enum class CoreProfession(val displayName: String) {
    SAWMILL("木工精製"), SMELTING("鉱石精製"), MASONRY("石材精製"), TANNING("皮革精製"), WEAVING("繊維精製"),
    WEAPONSMITH("武器製造"), ARMORSMITH("防具製造");
    companion object {
        fun refining(raw: CoreResource) = when (raw) {
            CoreResource.WOOD -> SAWMILL; CoreResource.ORE -> SMELTING; CoreResource.STONE -> MASONRY
            CoreResource.HIDE -> TANNING; CoreResource.FIBER -> WEAVING; else -> error("精製できない素材です")
        }
        fun crafting(slot: CoreGearSlot) = if (slot == CoreGearSlot.WEAPON) WEAPONSMITH else ARMORSMITH
    }
}

data class CoreProfessionProgress(val xp: Long = 0, val returnCredit: Int = 0) {
    init { require(xp in 0..1_000_000_000L && returnCredit in 0..99) }
    fun level(balance: CoreMmoBalance = CoreMmoTuning.balance) = sqrt(xp.toDouble() / balance.professionXpScale).toInt().coerceAtMost(100)
}

object CoreProfessions {
    fun progress(a: CoreAccount, profession: CoreProfession) = a.professions[profession] ?: CoreProfessionProgress()
    fun surveyTier(points: Long, b: CoreMmoBalance = CoreMmoTuning.balance): Int = when {
        points >= b.surveyTier4 -> 4; points >= b.surveyTier3 -> 3; points >= b.surveyTier2 -> 2; else -> 1
    }
    fun surveyMap(tier: Int, raw: CoreResource): CoreRecipe {
        require(raw.raw && tier in 1..4)
        return CoreRecipe("T$tier 採取遠征の地図", if (tier == 1) emptyMap() else mapOf(CoreMaterial(raw, tier - 1) to CoreMmoTuning.balance.surveyMapCost.toLong()), emptyMap())
    }
    /** Refund fractions persist across clicks, so splitting batches cannot create or erase resources. */
    fun refineQuote(a: CoreAccount, raw: CoreResource, tier: Int, batches: Int): Pair<CoreRecipe, CoreProfessionProgress> {
        val base = CoreLoopCatalog.refine(raw, tier, batches)
        val p = progress(a, CoreProfession.refining(raw))
        val rate = p.level() * CoreMmoTuning.balance.refineReturnMaxPercent / 100
        val credits = p.returnCredit + 2 * batches * rate
        val refunds = credits / 100
        val recipe = base.copy(outputs = base.outputs + if (refunds > 0) mapOf(CoreMaterial(raw, tier) to refunds.toLong()) else emptyMap())
        return recipe to CoreProfessionProgress((p.xp + batches.toLong() * tier * CoreMmoTuning.balance.refineXp).coerceAtMost(1_000_000_000), credits % 100)
    }
    fun manufacture(slot: CoreGearSlot, tier: Int, count: Int): CoreRecipe {
        require(count in 1..16)
        val base = CoreEconomy.manufacture(slot, tier)
        return base.copy(displayName = base.displayName + " ×$count", costs = base.costs.mapValues { it.value * count })
    }
    /** Quality improves the base, not MOD rarity. Low quality equipment still repairs the same tier. */
    fun quality(a: CoreAccount, slot: CoreGearSlot, random: Random): Int {
        val level = progress(a, CoreProfession.crafting(slot)).level()
        val max = CoreMmoTuning.balance.craftQualityMax
        val floor = level * max / 200
        return (0..level / 25).maxOf { random.nextInt(floor, max + 1) }
    }
}
