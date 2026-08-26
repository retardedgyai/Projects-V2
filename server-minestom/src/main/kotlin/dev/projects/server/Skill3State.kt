package dev.projects.server

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.math.sqrt

enum class Skill3Phase {
    IDLE,
    DASH,
    MULTIHIT,
    HOVER,
}

data class Skill3Tick(
    val phase: Skill3Phase,
    val dashDirection: Vec?,
    val dashActive: Boolean,
    val velocityY: Double,
    val stopHorizontalVelocity: Boolean = false,
    val pulseIndex: Int? = null,
    val finisherActive: Boolean = false,
)

/** Server-owned state machine for the Skill3 dash, execution pulses, and recoil. */
class Skill3State(
    private val castIdSource: () -> Long = Skill3ExecutionIds::next,
) {
    var phase: Skill3Phase = Skill3Phase.IDLE
        private set

    var dashDirection: Vec? = null
        private set

    var dashTicksRemaining: Int = 0
        private set

    var hoverTicksRemaining: Int = 0
        private set

    var cooldownTicksRemaining: Int = 0
        private set

    var castId: Long = 0L
        private set

    var primaryTargetId: UUID? = null
        private set

    private val hitTargets = mutableSetOf<UUID>()
    private val reducedNormalAttackExecutions = mutableSetOf<Long>()
    private var nextPulseIndex = 1
    private var ticksUntilNextPulse = 0
    private var finisherPending = false
    private var configuredPulseIntervalTicks = PULSE_INTERVAL_TICKS

    val isReady: Boolean
        get() = phase == Skill3Phase.IDLE && cooldownTicksRemaining == 0

    fun tryCast(
        facing: Vec,
        input: ClassSkillDirection,
        dashTicks: Int = DASH_TICKS,
        pulseIntervalTicks: Int = PULSE_INTERVAL_TICKS,
    ): Long? {
        if (!isReady) return null
        require(dashTicks > 0 && pulseIntervalTicks > 0) { "Skill3 timing must be positive" }
        dashDirection = skill3Direction(facing, input)
        castId = castIdSource()
        phase = Skill3Phase.DASH
        dashTicksRemaining = dashTicks
        hoverTicksRemaining = 0
        primaryTargetId = null
        hitTargets.clear()
        nextPulseIndex = 1
        ticksUntilNextPulse = 0
        finisherPending = false
        configuredPulseIntervalTicks = pulseIntervalTicks
        return castId
    }

    fun tick(isGrounded: Boolean, velocityY: Double): Skill3Tick {
        require(velocityY.isFinite()) { "Skill3 velocity must be finite" }
        val wasDashing = phase == Skill3Phase.DASH
        val tick = when (phase) {
            Skill3Phase.DASH -> {
                val result = Skill3Tick(
                    phase = Skill3Phase.DASH,
                    dashDirection = dashDirection,
                    dashActive = true,
                    velocityY = maxOf(velocityY, 0.0),
                )
                dashTicksRemaining--
                if (dashTicksRemaining == 0) {
                    phase = Skill3Phase.HOVER
                    hoverTicksRemaining = HOVER_TICKS
                    cooldownTicksRemaining = COOLDOWN_TICKS
                }
                result
            }
            Skill3Phase.MULTIHIT -> {
                when {
                    finisherPending -> {
                        finisherPending = false
                        phase = Skill3Phase.HOVER
                        hoverTicksRemaining = HOVER_TICKS
                        cooldownTicksRemaining = COOLDOWN_TICKS
                        Skill3Tick(
                            phase = Skill3Phase.MULTIHIT,
                            dashDirection = dashDirection,
                            dashActive = false,
                            velocityY = 0.0,
                            stopHorizontalVelocity = true,
                            finisherActive = true,
                        )
                    }
                    ticksUntilNextPulse > 0 -> {
                        ticksUntilNextPulse--
                        Skill3Tick(
                            phase = Skill3Phase.MULTIHIT,
                            dashDirection = dashDirection,
                            dashActive = false,
                            velocityY = 0.0,
                            stopHorizontalVelocity = true,
                        )
                    }
                    nextPulseIndex <= PULSE_COUNT -> {
                        val pulse = nextPulseIndex++
                        ticksUntilNextPulse = configuredPulseIntervalTicks - 1
                        if (pulse == PULSE_COUNT) finisherPending = true
                        Skill3Tick(
                            phase = Skill3Phase.MULTIHIT,
                            dashDirection = dashDirection,
                            dashActive = false,
                            velocityY = 0.0,
                            stopHorizontalVelocity = true,
                            pulseIndex = pulse,
                        )
                    }
                    else -> error("Skill3 multihit reached an invalid pulse state")
                }
            }
            Skill3Phase.HOVER -> {
                if (isGrounded) {
                    phase = Skill3Phase.IDLE
                    hoverTicksRemaining = 0
                    Skill3Tick(Skill3Phase.HOVER, dashDirection, false, velocityY)
                } else {
                    val isFirstHoverTick = hoverTicksRemaining == HOVER_TICKS
                    val result = Skill3Tick(
                        phase = Skill3Phase.HOVER,
                        dashDirection = dashDirection,
                        dashActive = false,
                        velocityY = maxOf(velocityY, -HOVER_FALL_SPEED),
                        stopHorizontalVelocity = isFirstHoverTick && primaryTargetId == null,
                    )
                    hoverTicksRemaining--
                    if (hoverTicksRemaining == 0) phase = Skill3Phase.IDLE
                    result
                }
            }
            Skill3Phase.IDLE -> Skill3Tick(Skill3Phase.IDLE, null, false, velocityY)
        }

        if (!wasDashing && cooldownTicksRemaining > 0 && !tick.finisherActive) {
            cooldownTicksRemaining--
        }
        return tick
    }

    /** Ends the dash at its first confirmed hit and starts the four-pulse execution. */
    fun finishDashOnHit(targetId: UUID? = hitTargets.firstOrNull()): Boolean {
        if (phase != Skill3Phase.DASH) return false
        if (targetId == null || targetId !in hitTargets) return false
        phase = Skill3Phase.MULTIHIT
        dashTicksRemaining = 0
        primaryTargetId = targetId
        nextPulseIndex = 1
        ticksUntilNextPulse = 0
        finisherPending = false
        return true
    }

    /** Returns true only for the first confirmed target hit by this normal execution. */
    fun reduceCooldownForNormalAttack(attackExecutionId: Long): Boolean {
        if (!reducedNormalAttackExecutions.add(attackExecutionId)) return false
        if (cooldownTicksRemaining == 0) return false
        val reducedCooldown = (cooldownTicksRemaining - NORMAL_ATTACK_REDUCTION_TICKS).coerceAtLeast(0)
        if (reducedCooldown == cooldownTicksRemaining) return false
        cooldownTicksRemaining = reducedCooldown
        return true
    }

    /** Returns only the nearest unconsumed target intersected by this dash segment. */
    fun hitTargetsOnSegment(
        start: Point,
        end: Point,
        targets: Collection<CombatTarget>,
        radius: Double = DASH_HIT_RADIUS,
    ): List<UUID> {
        require(radius >= 0.0 && radius.isFinite()) { "Skill3 hit radius must be finite and non-negative" }
        if (phase != Skill3Phase.DASH || hitTargets.isNotEmpty()) return emptyList()
        val nearest = targets.asSequence()
            .mapNotNull { target ->
                segmentIntersectionProgress(start, end, target.position, target.halfExtent, radius)
                    ?.let { progress -> target to progress }
            }
            .minWithOrNull(compareBy { it.second })
            ?.first
            ?: return emptyList()
        hitTargets += nearest.id
        return listOf(nearest.id)
    }

    fun cancelActiveMovement() {
        phase = Skill3Phase.IDLE
        dashDirection = null
        dashTicksRemaining = 0
        hoverTicksRemaining = 0
        castId = 0L
        primaryTargetId = null
        hitTargets.clear()
        nextPulseIndex = 1
        ticksUntilNextPulse = 0
        finisherPending = false
        configuredPulseIntervalTicks = PULSE_INTERVAL_TICKS
    }

    fun reset() {
        cancelActiveMovement()
        cooldownTicksRemaining = 0
        reducedNormalAttackExecutions.clear()
    }

    companion object {
        const val DASH_TICKS = 4
        const val HOVER_TICKS = 20
        const val COOLDOWN_TICKS = 60
        const val DASH_SPEED = 15.0
        const val DASH_HIT_RADIUS = 1.0
        const val HOVER_FALL_SPEED = 0.4
        const val NORMAL_ATTACK_REDUCTION_TICKS = 20
        const val HIT_BOUNCE_SPEED_Y = 6.0
        const val HIT_BOUNCE_HORIZONTAL_SPEED = 1.5
        const val PULSE_COUNT = 4
        const val PULSE_INTERVAL_TICKS = 2

        private fun segmentIntersectionProgress(
            start: Point,
            end: Point,
            center: Point,
            halfExtent: Vec,
            radius: Double,
        ): Double? {
            require(
                halfExtent.x().isFinite() && halfExtent.y().isFinite() && halfExtent.z().isFinite() &&
                    halfExtent.x() >= 0.0 && halfExtent.y() >= 0.0 && halfExtent.z() >= 0.0,
            ) { "Skill3 target half extents must be finite and non-negative" }

            var minimum = 0.0
            var maximum = 1.0

            fun clip(startValue: Double, delta: Double, lower: Double, upper: Double): Boolean {
                if (kotlin.math.abs(delta) < 1.0e-9) return startValue in lower..upper
                var enter = (lower - startValue) / delta
                var exit = (upper - startValue) / delta
                if (enter > exit) {
                    val swap = enter
                    enter = exit
                    exit = swap
                }
                minimum = maxOf(minimum, enter)
                maximum = minOf(maximum, exit)
                return minimum <= maximum
            }

            val deltaX = end.x() - start.x()
            val deltaY = end.y() - start.y()
            val deltaZ = end.z() - start.z()
            if (!clip(
                start.x(),
                deltaX,
                center.x() - halfExtent.x() - radius,
                center.x() + halfExtent.x() + radius,
            )) return null
            if (!clip(
                start.y(),
                deltaY,
                center.y() - halfExtent.y() - radius,
                center.y() + halfExtent.y() + radius,
            )) return null
            if (!clip(
                start.z(),
                deltaZ,
                center.z() - halfExtent.z() - radius,
                center.z() + halfExtent.z() + radius,
            )) return null
            return minimum
        }
    }
}

internal fun skill3HitBounceVelocity(direction: Vec): Vec {
    val horizontalLength = sqrt(direction.x() * direction.x() + direction.z() * direction.z())
    if (horizontalLength <= 1.0e-9) return Vec(0.0, Skill3State.HIT_BOUNCE_SPEED_Y, 0.0)
    return Vec(
        -direction.x() / horizontalLength * Skill3State.HIT_BOUNCE_HORIZONTAL_SPEED,
        Skill3State.HIT_BOUNCE_SPEED_Y,
        -direction.z() / horizontalLength * Skill3State.HIT_BOUNCE_HORIZONTAL_SPEED,
    )
}

data class ClassSkillDirection(val x: Double, val z: Double) {
    init {
        require(x.isFinite() && z.isFinite()) { "Skill direction must be finite" }
        require(kotlin.math.abs(x) <= 1.0 && kotlin.math.abs(z) <= 1.0) { "Skill direction is out of range" }
    }
}

internal fun skill3Direction(facing: Vec, @Suppress("UNUSED_PARAMETER") input: ClassSkillDirection): Vec {
    val length = sqrt(facing.x() * facing.x() + facing.y() * facing.y() + facing.z() * facing.z())
    return if (length > 1.0e-9) {
        Vec(facing.x() / length, facing.y() / length, facing.z() / length)
    } else {
        Vec(0.0, 0.0, 1.0)
    }
}

private object Skill3ExecutionIds {
    private var nextId = 0L

    @Synchronized
    fun next(): Long = ++nextId
}
