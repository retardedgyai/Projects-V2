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
    }
}

private object FixedAttackExecutionIds {
    private var nextId = 0L

    @Synchronized
    fun next(): Long = ++nextId
}
