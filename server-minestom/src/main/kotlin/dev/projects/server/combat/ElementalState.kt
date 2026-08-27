package dev.projects.server.combat

import dev.projects.server.combat.damage.DamageType
import java.util.UUID
import kotlin.math.floor

enum class TargetClassification {
    NORMAL,
    ELITE,
    MINIBOSS,
    BOSS,
}

enum class ColdLevel {
    NONE,
    COLD_I,
    COLD_II,
    FROZEN,
}

typealias IceLevel = ColdLevel

data class ElementAttribution(
    val contributorId: String,
    val lineage: DamageType,
) {
    init {
        require(contributorId.isNotBlank()) { "element contributor is required" }
        require(lineage == DamageType.PHYSICAL || lineage == DamageType.MAGICAL) {
            "element lineage must be physical or magical"
        }
    }
}

data class FireDetonation(
    val effectiveFireContribution: Double,
    val attribution: ElementAttribution,
    val radius: Double = FireState.DETONATION_RADIUS,
    val primaryMultiplier: Double = FireState.PRIMARY_TARGET_MULTIPLIER,
    val nearbyMultiplier: Double = FireState.NEARBY_TARGET_MULTIPLIER,
    val spreadsBurn: Boolean = false,
) {
    val primaryDamage: Double
        get() = effectiveFireContribution * primaryMultiplier

    val nearbyDamage: Double
        get() = effectiveFireContribution * nearbyMultiplier

    init {
        require(effectiveFireContribution.isFinite() && effectiveFireContribution >= 0.0) {
            "detonation contribution must be finite and non-negative"
        }
        require(radius.isFinite() && primaryMultiplier.isFinite() && nearbyMultiplier.isFinite() &&
            radius >= 0.0 && primaryMultiplier >= 0.0 && nearbyMultiplier >= 0.0
        ) {
            "detonation parameters must be non-negative"
        }
        require(!spreadsBurn) { "Fire detonation must not spread Burn" }
    }
}

data class FireApplicationResult(
    val applicationPower: Double,
    val stacksBefore: Int,
    val stacksAfter: Int,
    val remainderBefore: Double,
    val remainderAfter: Double,
    val attribution: ElementAttribution?,
    val detonations: List<FireDetonation>,
    val duplicateHit: Boolean = false,
) {
    val detonated: Boolean
        get() = detonations.isNotEmpty()

    val effectiveDetonationDamage: Double
        get() = detonations.sumOf(FireDetonation::primaryDamage)
}

data class FireStateSnapshot(
    val stacks: Int,
    val remainder: Double,
    val quietTicks: Int,
    val decayTicks: Int,
    val attribution: ElementAttribution?,
)

/** Explicit target-shared Burn state for the Fire slice. */
class FireState {
    var stacks: Int = 0
        private set

    var remainder: Double = 0.0
        private set

    var attribution: ElementAttribution? = null
        private set

    private var quietTicks = 0
    private var decayTicks = 0
    private val processedHitIds = mutableSetOf<Long>()

    fun apply(
        applicationPower: Double,
        contributorId: String = DEFAULT_CONTRIBUTOR,
        lineage: DamageType = DamageType.PHYSICAL,
        hitExecutionId: Long? = null,
    ): FireApplicationResult {
        require(applicationPower.isFinite() && applicationPower >= 0.0) {
            "Fire application power must be finite and non-negative"
        }
        val beforeStacks = stacks
        val beforeRemainder = remainder
        if (hitExecutionId != null && !processedHitIds.add(hitExecutionId)) {
            return FireApplicationResult(
                applicationPower = 0.0,
                stacksBefore = beforeStacks,
                stacksAfter = stacks,
                remainderBefore = beforeRemainder,
                remainderAfter = remainder,
                attribution = attribution,
                detonations = emptyList(),
                duplicateHit = true,
            )
        }
        if (applicationPower == 0.0) {
            return FireApplicationResult(
                applicationPower = applicationPower,
                stacksBefore = beforeStacks,
                stacksAfter = stacks,
                remainderBefore = beforeRemainder,
                remainderAfter = remainder,
                attribution = attribution,
                detonations = emptyList(),
            )
        }

        val nextAttribution = ElementAttribution(contributorId, lineage)
        attribution = nextAttribution
        quietTicks = 0
        decayTicks = 0

        var wholeStacks = floor(remainder + applicationPower).toLong()
        remainder = (remainder + applicationPower) - wholeStacks.toDouble()
        val detonations = mutableListOf<FireDetonation>()
        while (wholeStacks > 0L) {
            val stacksUntilDetonation = MAX_STACKS - stacks
            if (wholeStacks < stacksUntilDetonation.toLong()) {
                stacks += wholeStacks.toInt()
                wholeStacks = 0L
            } else {
                wholeStacks -= stacksUntilDetonation.toLong()
                stacks = RETAINED_STACKS
                detonations += FireDetonation(
                    effectiveFireContribution = applicationPower * DETONATION_MULTIPLIER,
                    attribution = nextAttribution,
                )
            }
        }

        return FireApplicationResult(
            applicationPower = applicationPower,
            stacksBefore = beforeStacks,
            stacksAfter = stacks,
            remainderBefore = beforeRemainder,
            remainderAfter = remainder,
            attribution = nextAttribution,
            detonations = detonations,
        )
    }

    /** Advances the five-second hold and then decays remainder before stacks. */
    fun tick(ticks: Int = 1): FireStateSnapshot {
        require(ticks >= 0) { "Fire state ticks must not be negative" }
        repeat(ticks) {
            if (stacks == 0 && remainder == 0.0) return@repeat
            quietTicks++
            if (quietTicks <= HOLD_TICKS) return@repeat
            decayTicks++
            if (decayTicks < DECAY_INTERVAL_TICKS) return@repeat
            decayTicks = 0
            if (remainder > 0.0) {
                remainder = 0.0
            } else {
                stacks = (stacks - 1).coerceAtLeast(0)
            }
        }
        return snapshot()
    }

    fun snapshot(): FireStateSnapshot = FireStateSnapshot(stacks, remainder, quietTicks, decayTicks, attribution)

    fun reset() {
        stacks = 0
        remainder = 0.0
        attribution = null
        quietTicks = 0
        decayTicks = 0
        processedHitIds.clear()
    }

    companion object {
        const val MAX_STACKS = 10
        const val RETAINED_STACKS = 3
        const val DETONATION_MULTIPLIER = 2.5
        const val DETONATION_RADIUS = 4.0
        const val PRIMARY_TARGET_MULTIPLIER = 1.0
        const val NEARBY_TARGET_MULTIPLIER = 0.6
        const val HOLD_TICKS = 5 * 20
        const val DECAY_INTERVAL_TICKS = 20
        const val DEFAULT_CONTRIBUTOR = "projects:unknown"
    }
}

typealias BurnState = FireState

data class IceShatterResult(
    val preCriticalDirectDamage: Double,
    val impactDamage: Double,
    val frozenCore: Double,
    val coreBonus: Double,
    val attribution: ElementAttribution,
    val retainedCold: Double,
    val refreezeImmunityTicks: Int,
    val critical: Boolean = false,
    val targetCount: Int = 1,
) {
    val totalBonusDamage: Double
        get() = impactDamage + coreBonus

    init {
        require(listOf(preCriticalDirectDamage, impactDamage, frozenCore, coreBonus, retainedCold).all(Double::isFinite)) {
            "Ice shatter values must be finite"
        }
        require(listOf(preCriticalDirectDamage, impactDamage, frozenCore, coreBonus, retainedCold).all { it >= 0.0 }) {
            "Ice shatter values must be non-negative"
        }
        require(!critical) { "Ice shatter bonus must not be critical" }
        require(targetCount == 1) { "Ice shatter bonus must be single-target" }
        require(refreezeImmunityTicks >= 0) { "refreeze immunity must not be negative" }
    }
}

data class IceApplicationResult(
    val applicationPower: Double,
    val levelBefore: ColdLevel,
    val levelAfter: ColdLevel,
    val gaugeBefore: Double,
    val gaugeAfter: Double,
    val wasFrozenBeforeHit: Boolean,
    val createdFrozen: Boolean,
    val directDamageMultiplier: Double,
    val shatter: IceShatterResult?,
    val immunityTicksRemaining: Int,
    val duplicateHit: Boolean = false,
)

data class IceStateSnapshot(
    val level: ColdLevel,
    val gauge: Double,
    val frozenCore: Double,
    val immunityTicksRemaining: Int,
    val shatterAvailable: Boolean,
)

/** Explicit target-shared Cold -> Frozen -> Shatter state for the Ice slice. */
class IceState(
    val targetClassification: TargetClassification = TargetClassification.BOSS,
) {
    var gauge: Double = 0.0
        private set

    var level: ColdLevel = ColdLevel.NONE
        private set

    var frozenCore: Double = 0.0
        private set

    var immunityTicksRemaining: Int = 0
        private set

    private var shatterAvailable = false
    private val processedHitIds = mutableSetOf<Long>()

    fun apply(
        applicationPower: Double,
        preCriticalDirectDamage: Double = 0.0,
        contributorId: String = FireState.DEFAULT_CONTRIBUTOR,
        lineage: DamageType = DamageType.PHYSICAL,
        hitExecutionId: Long? = null,
    ): IceApplicationResult {
        require(applicationPower.isFinite() && applicationPower >= 0.0) {
            "Ice application power must be finite and non-negative"
        }
        require(preCriticalDirectDamage.isFinite() && preCriticalDirectDamage >= 0.0) {
            "pre-critical direct damage must be finite and non-negative"
        }
        val levelBefore = level
        val gaugeBefore = gauge
        if (hitExecutionId != null && !processedHitIds.add(hitExecutionId)) {
            return IceApplicationResult(
                applicationPower = 0.0,
                levelBefore = levelBefore,
                levelAfter = level,
                gaugeBefore = gaugeBefore,
                gaugeAfter = gauge,
                wasFrozenBeforeHit = levelBefore == ColdLevel.FROZEN,
                createdFrozen = false,
                directDamageMultiplier = if (levelBefore == ColdLevel.FROZEN) FROZEN_DIRECT_DAMAGE_MULTIPLIER else 1.0,
                shatter = null,
                immunityTicksRemaining = immunityTicksRemaining,
                duplicateHit = true,
            )
        }

        val wasFrozen = level == ColdLevel.FROZEN
        val attribution = ElementAttribution(contributorId, lineage)
        if (applicationPower > 0.0) {
            gauge = (gauge + applicationPower).coerceIn(0.0, MAX_GAUGE)
        }
        var createdFrozen = false
        var shatter: IceShatterResult? = null
        val directDamageMultiplier = if (wasFrozen) FROZEN_DIRECT_DAMAGE_MULTIPLIER else 1.0

        if (wasFrozen) {
            if (shatterAvailable) {
                val retainedCold = (frozenCore * RETAINED_COLD_RATIO).coerceIn(0.0, MAX_GAUGE)
                shatter = IceShatterResult(
                    preCriticalDirectDamage = preCriticalDirectDamage,
                    impactDamage = preCriticalDirectDamage * SHATTER_IMPACT_MULTIPLIER,
                    frozenCore = frozenCore,
                    coreBonus = frozenCore * ICE_CORE_BONUS_RATIO,
                    attribution = attribution,
                    retainedCold = retainedCold,
                    refreezeImmunityTicks = refreezeImmunityTicks(targetClassification),
                )
                gauge = retainedCold
                level = coldLevel(gauge)
                frozenCore = 0.0
                shatterAvailable = false
                immunityTicksRemaining = shatter.refreezeImmunityTicks
            }
        } else if (applicationPower > 0.0 && immunityTicksRemaining == 0 && gauge >= FREEZE_GAUGE) {
            level = ColdLevel.FROZEN
            frozenCore = gauge
            shatterAvailable = true
            createdFrozen = true
        } else {
            level = coldLevel(gauge)
        }

        return IceApplicationResult(
            applicationPower = applicationPower,
            levelBefore = levelBefore,
            levelAfter = level,
            gaugeBefore = gaugeBefore,
            gaugeAfter = gauge,
            wasFrozenBeforeHit = wasFrozen,
            createdFrozen = createdFrozen,
            directDamageMultiplier = directDamageMultiplier,
            shatter = shatter,
            immunityTicksRemaining = immunityTicksRemaining,
        )
    }

    fun tick(ticks: Int = 1): IceStateSnapshot {
        require(ticks >= 0) { "Ice state ticks must not be negative" }
        immunityTicksRemaining = (immunityTicksRemaining - ticks).coerceAtLeast(0)
        return snapshot()
    }

    fun snapshot(): IceStateSnapshot = IceStateSnapshot(
        level = level,
        gauge = gauge,
        frozenCore = frozenCore,
        immunityTicksRemaining = immunityTicksRemaining,
        shatterAvailable = shatterAvailable,
    )

    fun reset() {
        gauge = 0.0
        level = ColdLevel.NONE
        frozenCore = 0.0
        immunityTicksRemaining = 0
        shatterAvailable = false
        processedHitIds.clear()
    }

    companion object {
        const val COLD_I_GAUGE = 1.0
        const val COLD_II_GAUGE = 2.0
        const val FREEZE_GAUGE = 3.0
        const val MAX_GAUGE = FREEZE_GAUGE
        const val FROZEN_DIRECT_DAMAGE_MULTIPLIER = 1.08
        const val SHATTER_IMPACT_MULTIPLIER = 1.25
        const val ICE_CORE_BONUS_RATIO = 0.5
        const val RETAINED_COLD_RATIO = 0.4

        fun refreezeImmunityTicks(classification: TargetClassification): Int = when (classification) {
            TargetClassification.NORMAL -> 3 * 20
            TargetClassification.ELITE -> 4 * 20
            TargetClassification.MINIBOSS -> 5 * 20
            TargetClassification.BOSS -> 8 * 20
        }

        fun refreezeImmunitySeconds(classification: TargetClassification): Int =
            refreezeImmunityTicks(classification) / 20

        private fun coldLevel(gauge: Double): ColdLevel = when {
            gauge >= FREEZE_GAUGE -> ColdLevel.COLD_II
            gauge >= COLD_II_GAUGE -> ColdLevel.COLD_II
            gauge >= COLD_I_GAUGE -> ColdLevel.COLD_I
            else -> ColdLevel.NONE
        }
    }
}

typealias ColdState = IceState
typealias TargetClass = TargetClassification

class ElementalTargetState(
    val classification: TargetClassification = TargetClassification.BOSS,
) {
    val fire = FireState()
    val ice = IceState(classification)

    fun tick(ticks: Int = 1) {
        fire.tick(ticks)
        ice.tick(ticks)
    }

    fun reset() {
        fire.reset()
        ice.reset()
    }
}

/** Target-keyed storage; Fire and Ice are intentionally explicit, not a generic status engine. */
class ElementalCombatState {
    private val targets = mutableMapOf<UUID, ElementalTargetState>()

    fun target(targetId: UUID, classification: TargetClassification = TargetClassification.BOSS): ElementalTargetState =
        targets.getOrPut(targetId) { ElementalTargetState(classification) }

    fun tick(ticks: Int = 1) {
        targets.values.forEach { it.tick(ticks) }
    }

    fun reset() {
        targets.values.forEach(ElementalTargetState::reset)
        targets.clear()
    }

    fun clearTarget(targetId: UUID) {
        targets.remove(targetId)?.reset()
    }

    companion object {
        const val DEFAULT_CONTRIBUTOR = FireState.DEFAULT_CONTRIBUTOR
    }
}
