package dev.projects.server

import net.minestom.server.coordinate.Point
import java.util.UUID
import kotlin.math.hypot

enum class Skill2Phase {
    IDLE,
    DIVE,
}

data class Skill2Tick(
    val phase: Skill2Phase,
    val diveActive: Boolean,
    val velocityY: Double,
    val landed: Boolean = false,
    val pulseIndex: Int? = null,
)

/** Server-owned state machine for the falling Blade Storm multi-hit prototype. */
class Skill2State(
    private val castIdSource: () -> Long = Skill2ExecutionIds::next,
) {
    var phase: Skill2Phase = Skill2Phase.IDLE
        private set

    var cooldownTicksRemaining: Int = 0
        private set

    var castId: Long = 0L
        private set

    private var landingWindowOpen = false
    private var elapsedTicks = 0
    private var nextPulse = 0
    private var activePulse: Int? = null
    private val pulseHitTargets = mutableMapOf<Int, MutableSet<UUID>>()
    private val landingHitTargets = mutableSetOf<UUID>()

    val isReady: Boolean
        get() = phase == Skill2Phase.IDLE && cooldownTicksRemaining == 0

    fun tryCast(isGrounded: Boolean): Long? {
        if (isGrounded || !isReady) return null
        castId = castIdSource()
        phase = Skill2Phase.DIVE
        landingWindowOpen = false
        elapsedTicks = 0
        nextPulse = 0
        activePulse = null
        pulseHitTargets.clear()
        landingHitTargets.clear()
        return castId
    }

    fun tick(isGrounded: Boolean): Skill2Tick {
        if (phase == Skill2Phase.DIVE) {
            if (isGrounded) {
                phase = Skill2Phase.IDLE
                cooldownTicksRemaining = COOLDOWN_TICKS
                landingWindowOpen = true
                activePulse = null
                return Skill2Tick(Skill2Phase.DIVE, false, 0.0, landed = true)
            }
            val pulse = if (elapsedTicks % PULSE_INTERVAL_TICKS == 0 && nextPulse < PULSE_COUNT) {
                nextPulse++
                nextPulse
            } else {
                null
            }
            activePulse = pulse
            val velocity = if (elapsedTicks >= FINAL_DIVE_TICK) -FINAL_DIVE_SPEED else -DESCENT_SPEED
            elapsedTicks++
            return Skill2Tick(Skill2Phase.DIVE, true, velocity, pulseIndex = pulse)
        }

        activePulse = null
        landingWindowOpen = false
        if (cooldownTicksRemaining > 0) cooldownTicksRemaining--
        return Skill2Tick(Skill2Phase.IDLE, false, 0.0)
    }

    /** Consumes the current pulse and returns each target at most once for that pulse. */
    fun hitTargetsAtPulse(
        pulseIndex: Int,
        center: Point,
        targets: Collection<CombatTarget>,
        radius: Double = PULSE_RADIUS,
    ): List<UUID> {
        require(pulseIndex in 1..PULSE_COUNT) { "Skill2 pulse index is out of range" }
        require(radius >= 0.0 && radius.isFinite()) { "Skill2 pulse radius must be finite and non-negative" }
        if (activePulse != pulseIndex) return emptyList()
        activePulse = null
        val hitTargets = pulseHitTargets.getOrPut(pulseIndex) { mutableSetOf() }
        val result = targets.filter {
            it.id !in hitTargets && isWithinLandingRadius(center, it, radius)
        }.map { it.id }
        hitTargets += result
        return result
    }

    /** Returns each target at most once for the landing of this cast. */
    fun hitTargetsAtLanding(
        center: Point,
        targets: Collection<CombatTarget>,
        radius: Double = LANDING_RADIUS,
    ): List<UUID> {
        require(radius >= 0.0 && radius.isFinite()) { "Skill2 landing radius must be finite and non-negative" }
        if (!landingWindowOpen) return emptyList()
        val result = targets.filter {
            it.id !in landingHitTargets && isWithinLandingRadius(center, it, radius)
        }.map { it.id }
        landingHitTargets += result
        landingWindowOpen = false
        return result
    }

    fun reset() {
        phase = Skill2Phase.IDLE
        cooldownTicksRemaining = 0
        castId = 0L
        landingWindowOpen = false
        elapsedTicks = 0
        nextPulse = 0
        activePulse = null
        pulseHitTargets.clear()
        landingHitTargets.clear()
    }

    companion object {
        const val COOLDOWN_TICKS = 100
        const val PULSE_COUNT = 4
        const val PULSE_INTERVAL_TICKS = 2
        const val PULSE_RADIUS = 2.75
        const val DESCENT_SPEED = 10.0
        const val FINAL_DIVE_SPEED = 18.0
        const val FINAL_DIVE_TICK = 6
        const val DOWNWARD_SPEED = FINAL_DIVE_SPEED
        const val LANDING_RADIUS = 4.0
    }
}

internal fun isWithinLandingRadius(center: Point, target: CombatTarget, radius: Double): Boolean {
    require(radius >= 0.0 && radius.isFinite()) { "Skill2 landing radius must be finite and non-negative" }
    require(
        target.halfExtent.x().isFinite() && target.halfExtent.y().isFinite() && target.halfExtent.z().isFinite() &&
            target.halfExtent.x() >= 0.0 && target.halfExtent.y() >= 0.0 && target.halfExtent.z() >= 0.0,
    ) { "Skill2 target half extents must be finite and non-negative" }
    val minX = target.position.x() - target.halfExtent.x()
    val maxX = target.position.x() + target.halfExtent.x()
    val minZ = target.position.z() - target.halfExtent.z()
    val maxZ = target.position.z() + target.halfExtent.z()
    val distanceX = when {
        center.x() < minX -> minX - center.x()
        center.x() > maxX -> center.x() - maxX
        else -> 0.0
    }
    val distanceZ = when {
        center.z() < minZ -> minZ - center.z()
        center.z() > maxZ -> center.z() - maxZ
        else -> 0.0
    }
    return hypot(distanceX, distanceZ) <= radius
}

private object Skill2ExecutionIds {
    private var nextId = 0L

    @Synchronized
    fun next(): Long = ++nextId
}
