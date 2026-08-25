package dev.projects.server.combat.damage

object DamageCalculator {
    fun calculate(input: Input): DamageResult {
        val attackPower = StatCalculator.nonNegative(input.attackPower)
        val baseDamage = StatCalculator.baseDamage(input.fixedDamage, attackPower, input.coefficient)
        val outgoing = StatCalculator.nonNegative(StatCalculator.saturatedAdd(1.0, input.damageIncreasePercent))
        val taken = StatCalculator.nonNegative(StatCalculator.saturatedAdd(1.0, input.damageTakenIncreasePercent))
        val criticalMultiplier = if (input.critical) maxOf(1.0, StatCalculator.finiteOrZero(input.criticalMultiplier)) else 1.0
        var offenseDamage = StatCalculator.saturatedMultiply(baseDamage, outgoing)
        offenseDamage = StatCalculator.saturatedMultiply(offenseDamage, criticalMultiplier)
        offenseDamage = StatCalculator.saturatedMultiply(offenseDamage, StatCalculator.nonNegative(input.modeMultiplier))
        val increase = StatCalculator.saturatedMultiply(outgoing, taken)
        return finish(input, attackPower, baseDamage, increase, DamageOffenseSnapshot(offenseDamage, input.critical, criticalMultiplier), taken)
    }

    private fun finish(input: Input, attackPower: Double, baseDamage: Double, increase: Double, offense: DamageOffenseSnapshot, taken: Double): DamageResult {
        val trueDamage = input.damageType == DamageType.TRUE
        val defense = if (trueDamage) 0.0 else StatCalculator.nonNegative(input.defense)
        val effectiveDefense = if (trueDamage) 0.0 else StatCalculator.effectiveDefense(defense, input.defenseReductionPercent, input.penetrationPercent, input.flatPenetration)
        val defenseMultiplier = if (trueDamage) 1.0 else StatCalculator.defenseMultiplier(effectiveDefense, input.defenseConstant)
        var reduction = 1.0
        input.damageReductions().forEach { reduction = StatCalculator.saturatedMultiply(reduction, 1.0 - StatCalculator.clamp01(it)) }
        val mode = input.mode ?: DamageMode.PVE
        val reductionMultiplier = maxOf(1.0 - mode.reductionCap, reduction)
        var beforeShield = StatCalculator.saturatedMultiply(offense.damage, taken)
        beforeShield = StatCalculator.saturatedMultiply(beforeShield, defenseMultiplier)
        beforeShield = StatCalculator.saturatedMultiply(beforeShield, reductionMultiplier)
        val rounded = kotlin.math.round(StatCalculator.nonNegative(beforeShield) * 1_000.0) / 1_000.0
        val shieldDamage = StatCalculator.shieldDamage(rounded, input.shield)
        val healthDamage = StatCalculator.actualHealthDamage(rounded, input.shield, input.health)
        val kind = input.damageKind ?: DamageKind.DIRECT_SKILL
        val lifeStealEfficiency = if (trueDamage || kind !in setOf(DamageKind.NORMAL_ATTACK, DamageKind.DIRECT_SKILL)) 0.0 else input.lifeStealEfficiency
        val lifeSteal = StatCalculator.lifeSteal(healthDamage, input.lifeStealPercent, lifeStealEfficiency, input.healingReductionPercent)
        return DamageResult(attackPower, baseDamage, increase, offense.damage, offense.critical, offense.criticalMultiplier, defense, effectiveDefense, defenseMultiplier, reductionMultiplier, StatCalculator.nonNegative(input.modeMultiplier), beforeShield, shieldDamage, healthDamage, lifeSteal, rounded)
    }

    class Input(
        val damageType: DamageType? = DamageType.PHYSICAL,
        val mode: DamageMode? = DamageMode.PVE,
        val damageKind: DamageKind? = DamageKind.DIRECT_SKILL,
        val attackPower: Double = 0.0,
        val fixedDamage: Double = 0.0,
        val coefficient: Double = 0.0,
        val damageIncreasePercent: Double = 0.0,
        val damageTakenIncreasePercent: Double = 0.0,
        val critical: Boolean = false,
        val criticalMultiplier: Double = 1.5,
        val defense: Double = 0.0,
        val defenseReductionPercent: Double = 0.0,
        val penetrationPercent: Double = 0.0,
        val flatPenetration: Double = 0.0,
        val defenseConstant: Double = StatCalculator.DEFAULT_DEFENSE_CONSTANT,
        damageReductions: DoubleArray = doubleArrayOf(),
        val modeMultiplier: Double = 1.0,
        val shield: Double = 0.0,
        val health: Double = 0.0,
        val lifeStealPercent: Double = 0.0,
        val lifeStealEfficiency: Double = 0.0,
        val healingReductionPercent: Double = 0.0,
    ) {
        private val rawDamageReductions: DoubleArray

        init {
            rawDamageReductions = damageReductions.clone()
        }

        fun damageReductions(): DoubleArray = rawDamageReductions.clone()
    }
}
