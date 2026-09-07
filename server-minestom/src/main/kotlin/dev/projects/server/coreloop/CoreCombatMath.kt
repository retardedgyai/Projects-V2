package dev.projects.server.coreloop

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.pow

/** Damage type selects a defence, never the coefficient's source. Elements do not change it. */
enum class CoreDamageType(val label: String) { PHYSICAL("物理 / ARで軽減"), MAGICAL("魔法 / MRで軽減"), TRUE("確定 / 防御無視") }
enum class CoreAttackTag { NORMAL, SKILL, MELEE, PROJECTILE, AREA }

data class CoreDamageFormula(val base: Double = 0.0, val ad: Double = 0.0, val ap: Double = 0.0) {
    init { require(listOf(base, ad, ap).all { it.isFinite() && it >= 0.0 }) }
    fun evaluate(sheet: CoreCombatSheet): Double = CoreCombatMath.safe(base + sheet.ad * ad + sheet.ap * ap)
    fun label(): String = buildList {
        add(CoreCombatMath.number(base))
        if (ad > 0) add("AD ${CoreCombatMath.number(ad * 100)}%")
        if (ap > 0) add("AP ${CoreCombatMath.number(ap * 100)}%")
    }.joinToString(" + ")
}

data class CoreCombatGear(
    val weaponTier: Int = 1, val armorTier: Int = 1,
    val base: CoreWeaponBase = CoreWeaponBase.STANDARD,
    val weaponEnhancement: Int = 0, val armorEnhancement: Int = 0,
    val weaponQuality: Int = 0, val armorQuality: Int = 0,
    val weaponLevelPower: Double = 1.0, val armorLevelPower: Double = 1.0,
    val weaponBroken: Boolean = false, val armorBroken: Boolean = false,
) {
    companion object {
        fun from(a: CoreAccount) = CoreCombatGear(a.weaponTier, a.armorTier, a.weaponIdentity.base,
            a.weaponEnhancement.level, a.armorEnhancement.level, a.weaponIdentity.quality, a.armorIdentity.quality,
            CoreJourneyRules.power(a.weaponIdentity, a.weaponTier), CoreJourneyRules.power(a.armorIdentity, a.armorTier),
            a.weaponBroken, a.armorBroken)
    }
}

data class CoreCombatSheet(val ad: Double, val ap: Double, val ar: Double, val mr: Double,
    val health: Double, val mana: Double, val attackSpeed: Double, val healingPower: Double,
    val mods: CoreAffixStats = CoreAffixStats()) {
    companion object {
        fun from(a: CoreAccount) = from(CoreCombatGear.from(a), CoreAffixCatalog.stats(a))
        fun from(g: CoreCombatGear, s: CoreAffixStats): CoreCombatSheet {
            fun b(stat: CoreAffixStat) = s.bonus(stat)
            val weapon = if (g.weaponBroken) 0.0 else 12.0 * 1.65.pow(g.weaponTier.coerceIn(1, 4) - 1) * g.base.power *
                g.weaponLevelPower * (1 + g.weaponQuality.coerceIn(0, 30) / 100.0) * (1 + .04 * g.weaponEnhancement.coerceIn(0, 30))
            // A staff grants its own AP. It does not turn AD, or AD affixes, into AP.
            val spellWeapon = if (g.base == CoreWeaponBase.STAFF) weapon else 0.0
            val armorHp = if (g.armorBroken) 100.0 else (100 + (g.armorTier - 1) * 30) * g.armorLevelPower *
                (1 + g.armorQuality.coerceIn(0, 30) / 100.0) * (1 + .02 * g.armorEnhancement.coerceIn(0, 30))
            // Preserve the old unmodified tier protection, expressed as real AR/MR instead of a second reduction layer.
            val defense = if (g.armorBroken) 0.0 else 300.0 * (1 / (1 - .1 * (g.armorTier.coerceIn(1, 4) - 1)) - 1)
            return CoreCombatSheet(
                if (g.weaponBroken) 0.0 else CoreCombatMath.power(weapon, b(CoreAffixStat.AD_FLAT), b(CoreAffixStat.AD_PERCENT)),
                if (g.weaponBroken) 0.0 else CoreCombatMath.power(spellWeapon, b(CoreAffixStat.AP_FLAT), b(CoreAffixStat.AP_PERCENT)),
                CoreCombatMath.power(defense, b(CoreAffixStat.AR_FLAT), b(CoreAffixStat.AR_PERCENT)),
                CoreCombatMath.power(defense, b(CoreAffixStat.MR_FLAT), b(CoreAffixStat.MR_PERCENT)),
                CoreCombatMath.power(armorHp.toInt().toDouble(), s.healthFlat, b(CoreAffixStat.HEALTH_PERCENT)),
                CoreCombatMath.power(100.0, s.maxManaFlat, b(CoreAffixStat.MANA_PERCENT)),
                if (g.weaponBroken) 1.0 else g.base.speed * (1 + (CoreCombatMath.safe(s.attackSpeedPercent) + .8 * g.weaponEnhancement.coerceIn(0, 30)) / 100),
                CoreCombatMath.power(0.0, b(CoreAffixStat.HEALING_FLAT), b(CoreAffixStat.HEALING_PERCENT)), s)
        }
    }
}

/** Recovered from ProjectS StatCalculator/DamageCalculator; percent inputs here use percentage points. */
object CoreCombatMath {
    const val DEFENSE_CONSTANT = 300.0
    const val BASE_CRITICAL_MULTIPLIER = 1.5
    const val MAX_REDUCTION_PERCENT = 80.0
    private const val LIMIT = 1.0e12
    fun safe(v: Double): Double = if (v.isNaN()) 0.0 else v.coerceIn(0.0, LIMIT)
    fun percent(v: Double) = safe(v).coerceAtMost(100.0) / 100
    fun power(base: Double, flat: Double, increase: Double) = safe((safe(base) + safe(flat)) * (1 + safe(increase) / 100))
    fun effectiveDefense(defense: Double, reduction: Double = 0.0, penetration: Double = 0.0, flat: Double = 0.0) =
        safe(safe(defense) * (1 - percent(reduction)) * (1 - percent(penetration)) - safe(flat))
    fun defenseMultiplier(defense: Double) = DEFENSE_CONSTANT / (DEFENSE_CONSTANT + safe(defense))
    fun cooldownTicks(base: Int, recovery: Double) = ceil(maxOf(base * .25, minOf(base.toDouble(), 10.0), base / (1 + safe(recovery) / 100))).toInt()
    fun castTicks(base: Int, speed: Double): Int {
        val increase = if (speed.isNaN()) 0.0 else speed.coerceIn(-90.0, LIMIT)
        return ceil(maxOf(base * .35, base / (1 + increase / 100))).toInt()
    }
    fun manaRegeneration(s: CoreAffixStats, outOfCombat: Boolean) =
        power(5.0, s.bonus(CoreAffixStat.MANA_REGEN_FLAT), s.manaRegenPercent) * if (outOfCombat) 3 else 1
    fun outgoing(base: Double, type: CoreDamageType, tags: Set<CoreAttackTag>, s: CoreAffixStats, critical: Boolean = false): Double {
        val increase = s.damagePercent + (if (CoreAttackTag.SKILL in tags) s.skillDamagePercent else s.normalDamagePercent) +
            (when (type) { CoreDamageType.PHYSICAL -> s.bonus(CoreAffixStat.PHYSICAL_DAMAGE); CoreDamageType.MAGICAL -> s.bonus(CoreAffixStat.MAGICAL_DAMAGE); else -> 0.0 }) +
            (if (CoreAttackTag.MELEE in tags) s.bonus(CoreAffixStat.MELEE_DAMAGE) else 0.0) +
            (if (CoreAttackTag.PROJECTILE in tags) s.bonus(CoreAffixStat.PROJECTILE_DAMAGE) else 0.0)
        return safe(safe(base) * (1 + safe(increase) / 100) * if (critical) s.criticalMultiplier else 1.0)
    }
    fun mitigate(amount: Double, type: CoreDamageType, ar: Double, mr: Double, s: CoreAffixStats = CoreAffixStats(),
        defenseReduction: Double = 0.0, damageReduction: Double = 0.0): Double {
        val physical = type == CoreDamageType.PHYSICAL
        val defense = if (type == CoreDamageType.TRUE) 0.0 else effectiveDefense(if (physical) ar else mr, defenseReduction,
            s.bonus(if (physical) CoreAffixStat.PHYSICAL_PEN_PERCENT else CoreAffixStat.MAGICAL_PEN_PERCENT),
            s.bonus(if (physical) CoreAffixStat.PHYSICAL_PEN_FLAT else CoreAffixStat.MAGICAL_PEN_FLAT))
        return safe(safe(amount) * defenseMultiplier(defense) * (1 - safe(damageReduction).coerceAtMost(MAX_REDUCTION_PERCENT) / 100))
    }
    fun healing(base: Double, coefficient: Double, source: CoreCombatSheet, target: CoreAffixStats = source.mods): Double =
        safe((safe(base) + source.healingPower * safe(coefficient)) * (1 + source.mods.bonus(CoreAffixStat.OUTGOING_HEALING) / 100) *
            (1 + target.bonus(CoreAffixStat.INCOMING_HEALING) / 100))
    fun lifeSteal(actualHealthDamage: Double, stats: CoreAffixStats, area: Boolean) =
        safe(actualHealthDamage) * percent(stats.bonus(CoreAffixStat.LIFESTEAL)) * (if (area) .33 else 1.0)
    fun number(v: Double): String = String.format(Locale.ROOT, "%.1f", safe(v)).removeSuffix(".0")
}
