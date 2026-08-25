package dev.projects.server.combat.damage

import kotlin.math.max

object StatCalculator {
    const val DEFAULT_DEFENSE_CONSTANT: Double = 300.0
    private const val MAX_SAFE_VALUE: Double = Long.MAX_VALUE / 1_000.0

    fun attackPower(weaponAttack: Double, flatAttack: Double, percentAttack: Double): Double =
        saturatedMultiply(nonNegative(saturatedAdd(nonNegative(weaponAttack), flatAttack)), nonNegative(saturatedAdd(1.0, percentAttack)))

    fun baseDamage(fixedDamage: Double, attackPower: Double, coefficient: Double): Double =
        saturatedAdd(nonNegative(fixedDamage), saturatedMultiply(nonNegative(attackPower), nonNegative(coefficient)))

    fun defense(equipmentDefense: Double, flatDefense: Double, percentDefense: Double): Double =
        saturatedMultiply(nonNegative(saturatedAdd(nonNegative(equipmentDefense), flatDefense)), nonNegative(saturatedAdd(1.0, percentDefense)))

    fun effectiveDefense(defense: Double, reductionPercent: Double, penetrationPercent: Double, flatPenetration: Double): Double {
        val afterReduction = saturatedMultiply(nonNegative(defense), 1.0 - clamp01(reductionPercent))
        val afterPenetration = saturatedMultiply(afterReduction, 1.0 - clamp01(penetrationPercent))
        return nonNegative(saturatedAdd(afterPenetration, -nonNegative(flatPenetration)))
    }

    fun defenseMultiplier(effectiveDefense: Double, constant: Double): Double {
        val safeConstant = if (constant.isFinite() && constant > 0.0) constant else DEFAULT_DEFENSE_CONSTANT
        return safeConstant / (safeConstant + nonNegative(effectiveDefense))
    }

    fun lifeSteal(actualHealthDamage: Double, percent: Double, efficiency: Double, healingReduction: Double): Double {
        var result = saturatedMultiply(nonNegative(actualHealthDamage), nonNegative(percent))
        result = saturatedMultiply(result, clamp01(efficiency))
        return saturatedMultiply(result, 1.0 - clamp01(healingReduction))
    }

    fun shieldDamage(finalDamage: Double, shield: Double): Double = minOf(nonNegative(finalDamage), nonNegative(shield))

    fun actualHealthDamage(finalDamage: Double, shield: Double, health: Double): Double =
        minOf(nonNegative(health), nonNegative(finalDamage) - shieldDamage(finalDamage, shield))

    fun clamp01(value: Double): Double = finiteOrZero(value).coerceIn(0.0, 1.0)

    fun finiteOrZero(value: Double): Double = when {
        value.isNaN() -> 0.0
        value == Double.POSITIVE_INFINITY -> MAX_SAFE_VALUE
        value == Double.NEGATIVE_INFINITY -> -MAX_SAFE_VALUE
        else -> value.coerceIn(-MAX_SAFE_VALUE, MAX_SAFE_VALUE)
    }

    fun nonNegative(value: Double): Double = max(0.0, finiteOrZero(value))

    fun saturatedAdd(left: Double, right: Double): Double {
        val safeLeft = finiteOrZero(left)
        val safeRight = finiteOrZero(right)
        return finiteOrZero(safeLeft + safeRight)
    }

    fun saturatedMultiply(left: Double, right: Double): Double {
        val safeLeft = nonNegative(left)
        val safeRight = nonNegative(right)
        if (safeLeft == 0.0 || safeRight == 0.0) return 0.0
        if (safeLeft > MAX_SAFE_VALUE / safeRight) return MAX_SAFE_VALUE
        return nonNegative(safeLeft * safeRight)
    }
}
