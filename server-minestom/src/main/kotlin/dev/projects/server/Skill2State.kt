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
)

/** Small server-owned state machine for the Dive AoE prototype. */
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
    private val hitTargets = mutableSetOf<UUID>()

    val isReady: Boolean
        get() = phase == Skill2Phase.IDLE && cooldownTicksRemaining == 0

    fun tryCast(isGrounded: Boolean): Long? {
        if (isGrounded || !isReady) return null
        castId = castIdSource()
        phase = Skill2Phase.DIVE
        landingWindowOpen = false
        hitTargets.clear()
        return castId
    }

    fun tick(isGrounded: Boolean): Skill2Tick {
        if (phase == Skill2Phase.DIVE) {
            if (isGrounded) {
                phase = Skill2Phase.IDLE
                cooldownTicksRemaining = COOLDOWN_TICKS
                landingWindowOpen = true
                return Skill2Tick(Skill2Phase.DIVE, false, 0.0, landed = true)
            }
            return Skill2Tick(Skill2Phase.DIVE, true, -DOWNWARD_SPEED)
        }

        landingWindowOpen = false
        if (cooldownTicksRemaining > 0) cooldownTicksRemaining--
        return Skill2Tick(Skill2Phase.IDLE, false, 0.0)
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
            it.id !in hitTargets && isWithinLandingRadius(center, it, radius)
        }.map { it.id }
        hitTargets += result
        landingWindowOpen = false
        return result
    }

    fun reset() {
        phase = Skill2Phase.IDLE
        cooldownTicksRemaining = 0
        castId = 0L
        landingWindowOpen = false
        hitTargets.clear()
    }

    companion object {
        const val COOLDOWN_TICKS = 100
        const val DOWNWARD_SPEED = 18.0
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
