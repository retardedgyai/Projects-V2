package dev.projects.server

import dev.projects.protocol.AttackInputState
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import java.util.UUID

data class CombatTarget(val id: UUID, val position: Point)

sealed interface CombatEvent {
    data class Started(val attackExecutionId: Long) : CombatEvent
    data class HitConfirmed(val attackExecutionId: Long, val targetId: UUID) : CombatEvent
}

private enum class AttackPhase(val durationTicks: Int) {
    WINDUP(4),
    ACTIVE(2),
    RECOVERY(6),
}

/** Minimal server-owned state machine for the normal attack. */
class CombatState(private val executionIdSource: () -> Long = ExecutionIds::next) {
    private var held = false
    private var phase: AttackPhase? = null
    private var phaseTicks = 0
    private var executionId = 0L
    private val hitTargets = mutableSetOf<UUID>()

    fun input(state: AttackInputState): List<CombatEvent> {
        held = state == AttackInputState.PRESS
        return if (state == AttackInputState.PRESS && phase == null) startAttack() else emptyList()
    }

    fun tick(position: Point, direction: Vec, targets: Collection<CombatTarget>): List<CombatEvent> {
        if (phase == null) {
            if (held) return startAttack()
            return emptyList()
        }

        val events = mutableListOf<CombatEvent>()
        phaseTicks--
        if (phase == AttackPhase.ACTIVE) {
            for (target in targets) {
                if (target.id !in hitTargets && isInAttackRange(position, direction, target.position)) {
                    hitTargets += target.id
                    events += CombatEvent.HitConfirmed(executionId, target.id)
                }
            }
        }
        if (phaseTicks <= 0) {
            phase = when (phase) {
                AttackPhase.WINDUP -> AttackPhase.ACTIVE
                AttackPhase.ACTIVE -> AttackPhase.RECOVERY
                AttackPhase.RECOVERY -> null
                null -> null
            }
            phaseTicks = phase?.durationTicks ?: 0
            if (phase == null) {
                hitTargets.clear()
                if (held) events += startAttack()
            }
        }
        return events
    }

    private fun startAttack(): List<CombatEvent> {
        executionId = executionIdSource()
        phase = AttackPhase.WINDUP
        phaseTicks = AttackPhase.WINDUP.durationTicks
        hitTargets.clear()
        return listOf(CombatEvent.Started(executionId))
    }

    companion object {
        private const val ATTACK_RANGE = 3.5
        private const val MIN_FORWARD_DOT = 0.72
        private const val VERTICAL_RANGE = 1.75

        fun isInAttackRange(position: Point, direction: Vec, target: Point): Boolean {
            val offset = Vec(target.x() - position.x(), target.y() - position.y(), target.z() - position.z())
            val horizontalDistance = kotlin.math.sqrt(offset.x() * offset.x() + offset.z() * offset.z())
            if (horizontalDistance > ATTACK_RANGE || kotlin.math.abs(offset.y()) > VERTICAL_RANGE) return false
            if (horizontalDistance == 0.0) return true
            val forward = Vec(direction.x(), 0.0, direction.z()).normalize()
            val toTarget = Vec(offset.x(), 0.0, offset.z()).normalize()
            return forward.x() * toTarget.x() + forward.z() * toTarget.z() >= MIN_FORWARD_DOT
        }
    }
}

private object ExecutionIds {
    private var nextId = 0L

    @Synchronized
    fun next(): Long = ++nextId
}
