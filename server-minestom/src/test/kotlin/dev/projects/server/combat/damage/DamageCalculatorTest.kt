package dev.projects.server.combat.damage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DamageCalculatorTest {
    @Test
    fun `physical damage is reduced by defense`() {
        assertEquals(50.0, calculate(DamageType.PHYSICAL, defense = 300.0).finalRoundedDamage)
        assertEquals(100.0, calculate(DamageType.MAGICAL).finalRoundedDamage)
    }

    @Test
    fun `final damage uses legacy half-up rounding`() {
        val result = DamageCalculator.calculate(DamageCalculator.Input(fixedDamage = 0.0005))

        assertEquals(0.001, result.finalRoundedDamage)
    }

    @Test
    fun `true damage bypasses defense`() {
        val result = calculate(DamageType.TRUE, defense = 1_000_000.0)
        assertEquals(100.0, result.finalRoundedDamage)
        assertEquals(0.0, result.effectiveDefense)
    }

    @Test
    fun `reduction shield and health are applied`() {
        val result = DamageCalculator.calculate(
            DamageCalculator.Input(
                damageType = DamageType.PHYSICAL,
                fixedDamage = 100.0,
                damageReductions = doubleArrayOf(0.5),
                shield = 30.0,
                health = 50.0,
                lifeStealPercent = 0.2,
                lifeStealEfficiency = 1.0,
            ),
        )
        assertEquals(50.0, result.finalRoundedDamage)
        assertEquals(30.0, result.shieldDamage)
        assertEquals(20.0, result.healthDamage)
        assertEquals(4.0, result.lifeStealHealing)
    }

    @Test
    fun `damage kinds that cannot lifesteal always return zero`() {
        for (kind in listOf(DamageKind.DAMAGE_OVER_TIME, DamageKind.REFLECTED, DamageKind.PERCENT_HEALTH)) {
            val result = DamageCalculator.calculate(
                DamageCalculator.Input(
                    damageKind = kind,
                    fixedDamage = 100.0,
                    health = 100.0,
                    lifeStealPercent = 1.0,
                    lifeStealEfficiency = 1.0,
                ),
            )
            assertEquals(0.0, result.lifeStealHealing)
        }
        val trueDamage = DamageCalculator.calculate(
            DamageCalculator.Input(
                damageType = DamageType.TRUE,
                fixedDamage = 100.0,
                health = 100.0,
                lifeStealPercent = 1.0,
                lifeStealEfficiency = 1.0,
            ),
        )
        assertEquals(0.0, trueDamage.lifeStealHealing)
    }

    @Test
    fun `input is safe for mutation and non finite values`() {
        val reductions = doubleArrayOf(0.5)
        val input = DamageCalculator.Input(fixedDamage = 100.0, damageReductions = reductions)
        reductions[0] = 0.0
        input.damageReductions()[0] = 0.0
        assertEquals(50.0, DamageCalculator.calculate(input).finalRoundedDamage)

        val result = DamageCalculator.calculate(
            DamageCalculator.Input(
                attackPower = Double.NaN,
                fixedDamage = Double.POSITIVE_INFINITY,
                coefficient = Double.NaN,
                defense = Double.NEGATIVE_INFINITY,
                damageReductions = doubleArrayOf(Double.NaN),
            ),
        )
        assertTrue(result.finalRoundedDamage.isFinite())
        assertTrue(result.finalRoundedDamage >= 0.0)
    }

    @Test
    fun `attack power and calculation are deterministic`() {
        assertEquals(150.0, StatCalculator.attackPower(100.0, 0.0, 0.5))
        val input = calculateInput(DamageType.PHYSICAL, 300.0)
        assertEquals(DamageCalculator.calculate(input), DamageCalculator.calculate(input))
    }

    private fun calculate(type: DamageType, defense: Double = 0.0): DamageResult =
        DamageCalculator.calculate(calculateInput(type, defense))

    private fun calculateInput(type: DamageType, defense: Double): DamageCalculator.Input =
        DamageCalculator.Input(damageType = type, fixedDamage = 100.0, defense = defense, health = 1_000.0)
}
