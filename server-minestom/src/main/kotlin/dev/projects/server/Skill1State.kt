package dev.projects.server

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

enum class Skill1Phase {
    IDLE,
    DASH,
}

data class Skill1Tick(
    val phase: Skill1Phase,
    val dashDirection: Vec?,
    val dashActive: Boolean,
    val stopHorizontalVelocity: Boolean = false,
)

/** Small server-owned state machine for the Flying Kick prototype. */
class Skill1State(
    private val castIdSource: () -> Long = Skill1ExecutionIds::next,
) {
    var phase: Skill1Phase = Skill1Phase.IDLE
        private set

    var dashDirection: Vec? = null
        private set

    var dashTicksRemaining: Int = 0
        private set

    var cooldownTicksRemaining: Int = 0
        private set

    var castId: Long = 0L
        private set

    private var hitWindowOpen = false
    private val hitTargets = mutableSetOf<UUID>()

    val isReady: Boolean
        get() = phase == Skill1Phase.IDLE && cooldownTicksRemaining == 0

    fun tryCast(facing: Vec): Long? {
        if (!isReady) return null
        dashDirection = horizontalFacing(facing)
        castId = castIdSource()
        phase = Skill1Phase.DASH
        dashTicksRemaining = DASH_TICKS
        cooldownTicksRemaining = COOLDOWN_TICKS
        hitWindowOpen = true
        hitTargets.clear()
        return castId
    }

    fun tick(): Skill1Tick {
        val wasDashing = phase == Skill1Phase.DASH
        hitWindowOpen = wasDashing
        val result = when (phase) {
            Skill1Phase.DASH -> {
                val lastDashTick = dashTicksRemaining == 1
                val tick = Skill1Tick(
                    phase = Skill1Phase.DASH,
                    dashDirection = dashDirection,
                    dashActive = true,
                    stopHorizontalVelocity = lastDashTick && hitTargets.isEmpty(),
                )
                dashTicksRemaining--
                if (dashTicksRemaining == 0) phase = Skill1Phase.IDLE
                tick
            }
            Skill1Phase.IDLE -> {
                hitWindowOpen = false
                Skill1Tick(Skill1Phase.IDLE, null, false)
            }
        }
        if (cooldownTicksRemaining > 0) cooldownTicksRemaining--
        if (!wasDashing) hitWindowOpen = false
        return result
    }

    /** Returns only the first target intersected by this kick. */
    fun hitTargetsOnSegment(
        start: Point,
        end: Point,
        targets: Collection<CombatTarget>,
        radius: Double = HIT_RADIUS,
    ): List<UUID> {
        require(radius >= 0.0 && radius.isFinite()) { "Skill1 hit radius must be finite and non-negative" }
        if (!hitWindowOpen || hitTargets.isNotEmpty()) return emptyList()
        val target = targets.firstOrNull {
            segmentIntersectsTarget(start, end, it.position, it.halfExtent, radius)
        } ?: return emptyList()
        hitTargets += target.id
        phase = Skill1Phase.IDLE
        hitWindowOpen = false
        return listOf(target.id)
    }

    fun cancelActiveMovement() {
        phase = Skill1Phase.IDLE
        dashDirection = null
        dashTicksRemaining = 0
        castId = 0L
        hitWindowOpen = false
        hitTargets.clear()
    }

    fun reset() {
        cancelActiveMovement()
        cooldownTicksRemaining = 0
    }

    companion object {
        const val DASH_TICKS = 4
        const val COOLDOWN_TICKS = 80
        const val DASH_SPEED = 12.0
        const val LAUNCH_SPEED_Y = 20.0
        const val LAUNCH_HORIZONTAL_SPEED = 2.5
        const val HIT_RADIUS = 1.0
    }
}

private fun horizontalFacing(facing: Vec): Vec {
    val length = sqrt(facing.x() * facing.x() + facing.z() * facing.z())
    return if (length > 1.0e-9) {
        Vec(facing.x() / length, 0.0, facing.z() / length)
    } else {
        Vec(0.0, 0.0, 1.0)
    }
}

private fun segmentIntersectsTarget(
    start: Point,
    end: Point,
    center: Point,
    halfExtent: Vec,
    radius: Double,
): Boolean {
    require(
        halfExtent.x().isFinite() && halfExtent.y().isFinite() && halfExtent.z().isFinite() &&
            halfExtent.x() >= 0.0 && halfExtent.y() >= 0.0 && halfExtent.z() >= 0.0,
    ) { "Skill1 target half extents must be finite and non-negative" }

    var minimum = 0.0
    var maximum = 1.0

    fun clip(startValue: Double, delta: Double, lower: Double, upper: Double): Boolean {
        if (abs(delta) < 1.0e-9) return startValue in lower..upper
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

    return clip(start.x(), end.x() - start.x(), center.x() - halfExtent.x() - radius, center.x() + halfExtent.x() + radius) &&
        clip(start.y(), end.y() - start.y(), center.y() - halfExtent.y() - radius, center.y() + halfExtent.y() + radius) &&
        clip(start.z(), end.z() - start.z(), center.z() - halfExtent.z() - radius, center.z() + halfExtent.z() + radius)
}

private object Skill1ExecutionIds {
    private var nextId = 0L

    @Synchronized
    fun next(): Long = ++nextId
}
