package dev.projects.server

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

enum class FixedAttackType(
    val telegraphTicks: Int,
    val activeTicks: Int,
    val recoveryTicks: Int,
) {
    SIDE_SWEEP(14, 2, 18),
    FORWARD_SLAM(18, 2, 22),
    ;
}

enum class FixedWeakpoint {
    HEAD,
    BACK,
    ;
}

data class FixedWeakpointSelection(
    val weakpoint: FixedWeakpoint,
    val center: Point,
)

data class FixedAttackTarget(val id: UUID, val position: Point)

sealed interface FixedAttackEvent {
    data class Started(
        val executionId: Long,
        val attack: FixedAttackType,
        val direction: Vec,
    ) : FixedAttackEvent

    data class Telegraph(
        val executionId: Long,
        val attack: FixedAttackType,
        val direction: Vec,
        val progress: Double,
    ) : FixedAttackEvent

    data class Active(
        val executionId: Long,
        val attack: FixedAttackType,
        val direction: Vec,
    ) : FixedAttackEvent

    data class HitConfirmed(val executionId: Long, val targetId: UUID) : FixedAttackEvent
}

private enum class TesterPhase {
    PAUSE,
    TELEGRAPH,
    ACTIVE,
    RECOVERY,
}

/** Deterministic Day 1 attack loop. The server owns direction, timing, and hit confirmation. */
class FixedAttackTester(
    private val executionIdSource: () -> Long = FixedAttackExecutionIds::next,
    private val initialPauseTicks: Int = 30,
    private val pauseTicks: Int = 20,
) {
    private var phase = TesterPhase.PAUSE
    private var phaseTicks = initialPauseTicks
    private var nextAttack = FixedAttackType.SIDE_SWEEP
    private var executionId = 0L
    private var attackDirection = Vec(0.0, 0.0, 1.0)
    private val hitTargets = mutableSetOf<UUID>()

    fun tick(
        origin: Point,
        facing: Vec,
        targets: Collection<FixedAttackTarget>,
    ): List<FixedAttackEvent> {
        val events = mutableListOf<FixedAttackEvent>()
        when (phase) {
            TesterPhase.PAUSE -> {
                phaseTicks--
                if (phaseTicks <= 0) {
                    startAttack(facing, events)
                }
            }

            TesterPhase.TELEGRAPH -> {
                val attack = nextAttack
                events += FixedAttackEvent.Telegraph(
                    executionId = executionId,
                    attack = attack,
                    direction = attackDirection,
                    progress = 1.0 - phaseTicks.toDouble() / attack.telegraphTicks,
                )
                phaseTicks--
                if (phaseTicks <= 0) {
                    phase = TesterPhase.ACTIVE
                    phaseTicks = attack.activeTicks
                    events += FixedAttackEvent.Active(executionId, attack, attackDirection)
                }
            }

            TesterPhase.ACTIVE -> {
                val attack = nextAttack
                for (target in targets) {
                    if (target.id !in hitTargets && isInAttackRegion(attack, origin, attackDirection, target.position)) {
                        hitTargets += target.id
                        events += FixedAttackEvent.HitConfirmed(executionId, target.id)
                    }
                }
                phaseTicks--
                if (phaseTicks <= 0) {
                    phase = TesterPhase.RECOVERY
                    phaseTicks = attack.recoveryTicks
                }
            }

            TesterPhase.RECOVERY -> {
                phaseTicks--
                if (phaseTicks <= 0) {
                    phase = TesterPhase.PAUSE
                    phaseTicks = pauseTicks
                    hitTargets.clear()
                    nextAttack = when (nextAttack) {
                        FixedAttackType.SIDE_SWEEP -> FixedAttackType.FORWARD_SLAM
                        FixedAttackType.FORWARD_SLAM -> FixedAttackType.SIDE_SWEEP
                    }
                }
            }
        }
        return events
    }

    private fun startAttack(facing: Vec, events: MutableList<FixedAttackEvent>) {
        executionId = executionIdSource()
        attackDirection = normalizeHorizontal(facing)
        phase = TesterPhase.TELEGRAPH
        phaseTicks = nextAttack.telegraphTicks
        hitTargets.clear()
        events += FixedAttackEvent.Started(executionId, nextAttack, attackDirection)
    }

    companion object {
        const val SIDE_SWEEP_RANGE = 4.5
        const val FORWARD_SLAM_RANGE = 5.0
        const val SIDE_SWEEP_MIN_FORWARD = 0.5
        const val FORWARD_SLAM_MIN_FORWARD = 0.5
        const val FORWARD_SLAM_HALF_WIDTH = 1.0
        const val VERTICAL_RANGE = 2.0
        const val WEAKPOINT_RADIUS = 0.45
        private const val HEAD_FORWARD_OFFSET = 0.55
        private const val BACK_FORWARD_OFFSET = -0.55
        private const val HEAD_HEIGHT = 2.0
        private const val BACK_HEIGHT = 1.45

        fun isInAttackRegion(attack: FixedAttackType, origin: Point, direction: Vec, target: Point): Boolean {
            val offsetX = target.x() - origin.x()
            val offsetY = target.y() - origin.y()
            val offsetZ = target.z() - origin.z()
            if (abs(offsetY) > VERTICAL_RANGE) return false

            val forward = normalizeHorizontal(direction)
            val rightX = -forward.z()
            val rightZ = forward.x()
            val forwardDistance = offsetX * forward.x() + offsetZ * forward.z()
            val lateralDistance = abs(offsetX * rightX + offsetZ * rightZ)
            val horizontalDistance = sqrt(offsetX * offsetX + offsetZ * offsetZ)

            return when (attack) {
                FixedAttackType.SIDE_SWEEP ->
                    horizontalDistance <= SIDE_SWEEP_RANGE &&
                        forwardDistance >= SIDE_SWEEP_MIN_FORWARD &&
                        forwardDistance / horizontalDistance >= 0.15

                FixedAttackType.FORWARD_SLAM ->
                    forwardDistance >= FORWARD_SLAM_MIN_FORWARD &&
                        forwardDistance <= FORWARD_SLAM_RANGE &&
                        lateralDistance <= FORWARD_SLAM_HALF_WIDTH
            }
        }

        fun normalizeHorizontal(direction: Vec): Vec {
            val length = sqrt(direction.x() * direction.x() + direction.z() * direction.z())
            return if (length > 1.0e-9) {
                Vec(direction.x() / length, 0.0, direction.z() / length)
            } else {
                Vec(0.0, 0.0, 1.0)
            }
        }

        fun weakpointCenter(origin: Point, facing: Vec, weakpoint: FixedWeakpoint): Point {
            val forward = normalizeHorizontal(facing)
            val forwardOffset = when (weakpoint) {
                FixedWeakpoint.HEAD -> HEAD_FORWARD_OFFSET
                FixedWeakpoint.BACK -> BACK_FORWARD_OFFSET
            }
            val height = when (weakpoint) {
                FixedWeakpoint.HEAD -> HEAD_HEIGHT
                FixedWeakpoint.BACK -> BACK_HEIGHT
            }
            return origin.add(forward.x() * forwardOffset, height, forward.z() * forwardOffset)
        }

        /** Selects one server-owned weakpoint from the player's current forward ray. */
        fun selectWeakpoint(
            playerPosition: Point,
            playerDirection: Vec,
            testerOrigin: Point,
            testerFacing: Vec,
            weaponRange: Double,
            weakpointRadius: Double = WEAKPOINT_RADIUS,
        ): FixedWeakpointSelection? {
            require(weaponRange >= 0.0) { "Weapon range must not be negative" }
            require(weakpointRadius >= 0.0) { "Weakpoint radius must not be negative" }

            val ray = normalize(playerDirection)
            val radiusSquared = weakpointRadius * weakpointRadius
            val rangeSquared = weaponRange * weaponRange
            data class Candidate(
                val selection: FixedWeakpointSelection,
                val rayDistanceSquared: Double,
                val playerDistanceSquared: Double,
            )

            val candidates = FixedWeakpoint.entries.mapNotNull { weakpoint ->
                val center = weakpointCenter(testerOrigin, testerFacing, weakpoint)
                val offsetX = center.x() - playerPosition.x()
                val offsetY = center.y() - playerPosition.y()
                val offsetZ = center.z() - playerPosition.z()
                val projection = offsetX * ray.x() + offsetY * ray.y() + offsetZ * ray.z()
                if (projection < 0.0) return@mapNotNull null

                val closestX = offsetX - ray.x() * projection
                val closestY = offsetY - ray.y() * projection
                val closestZ = offsetZ - ray.z() * projection
                val rayDistanceSquared =
                    closestX * closestX + closestY * closestY + closestZ * closestZ
                val playerDistanceSquared = offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ
                if (playerDistanceSquared > rangeSquared || rayDistanceSquared > radiusSquared) {
                    return@mapNotNull null
                }
                Candidate(
                    FixedWeakpointSelection(weakpoint, center),
                    rayDistanceSquared,
                    playerDistanceSquared,
                )
            }

            return candidates.minWithOrNull(
                compareBy<Candidate> { it.rayDistanceSquared }.thenBy { it.playerDistanceSquared },
            )?.selection
        }

        private fun normalize(direction: Vec): Vec {
            val length = sqrt(
                direction.x() * direction.x() +
                    direction.y() * direction.y() +
                    direction.z() * direction.z(),
            )
            return if (length > 1.0e-9) {
                Vec(direction.x() / length, direction.y() / length, direction.z() / length)
            } else {
                Vec(0.0, 0.0, 1.0)
            }
        }
    }
}

private object FixedAttackExecutionIds {
    private var nextId = 0L

    @Synchronized
    fun next(): Long = ++nextId
}
