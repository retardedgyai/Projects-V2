package dev.projects.server.combat.damage

enum class DamageType {
    PHYSICAL,
    MAGICAL,
    TRUE,
}

enum class DamageKind(
    val criticalAllowed: Boolean,
    val defaultLifeStealEfficiency: Double,
) {
    NORMAL_ATTACK(true, 1.0),
    DIRECT_SKILL(true, 1.0),
    DAMAGE_OVER_TIME(false, 0.0),
    REFLECTED(false, 0.0),
    PERCENT_HEALTH(false, 0.0),
}

enum class DamageMode(
    val baseCriticalMultiplier: Double,
    val reductionCap: Double,
) {
    PVE(1.75, 0.80),
    PVP(1.50, 0.75),
}

data class DamageOffenseSnapshot(
    val damage: Double,
    val critical: Boolean,
    val criticalMultiplier: Double,
) {
    init {
        require(damage.isFinite() && damage >= 0.0)
        require(criticalMultiplier.isFinite() && criticalMultiplier >= 1.0)
    }
}

data class DamageResult(
    val resolvedAttackPower: Double,
    val baseDamage: Double,
    val damageIncreaseMultiplier: Double,
    val offenseResolvedDamage: Double,
    val critical: Boolean,
    val criticalMultiplier: Double,
    val defenseBeforePenetration: Double,
    val effectiveDefense: Double,
    val defenseMultiplier: Double,
    val reductionMultiplier: Double,
    val modeMultiplier: Double,
    val damageBeforeShield: Double,
    val shieldDamage: Double,
    val healthDamage: Double,
    val lifeStealHealing: Double,
    val finalRoundedDamage: Double,
)
