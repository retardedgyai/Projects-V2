package dev.projects.server

import dev.projects.protocol.AttackInputState
import dev.projects.protocol.DodgeInput
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.math.round
import kotlin.math.sqrt

enum class WeaponType {
    HEAVY_BLADE,
    TWIN_RODS,
    ;

    fun profile(attackSpeed: Double): WeaponProfile {
        require(attackSpeed > 0.0) { "Attack Speed must be positive" }
        return when (this) {
            HEAVY_BLADE -> WeaponProfile(
                weapon = this,
                startupTicks = scaledTicks(7.0 / (1.0 + 0.25 * (attackSpeed - 1.0))),
                activeTicks = 2,
                recoveryTicks = scaledTicks(11.0 / attackSpeed),
                range = 4.5,
                minForwardDot = 0.40,
                verticalRange = 2.0,
            )
            TWIN_RODS -> WeaponProfile(
                weapon = this,
                startupTicks = scaledTicks(3.0 / attackSpeed),
                activeTicks = 1,
                recoveryTicks = scaledTicks(4.0 / attackSpeed),
                range = 2.8,
                minForwardDot = 0.65,
                verticalRange = 1.75,
            )
        }
    }

    private fun scaledTicks(value: Double): Int = round(value).toInt().coerceAtLeast(1)
}

data class WeaponProfile(
    val weapon: WeaponType,
    val startupTicks: Int,
    val activeTicks: Int,
    val recoveryTicks: Int,
    val range: Double,
    val minForwardDot: Double,
    val verticalRange: Double,
) {
    val totalTicks: Int
        get() = startupTicks + activeTicks + recoveryTicks
}

data class CombatTarget(val id: UUID, val position: Point)

sealed interface CombatEvent {
    data class Started(val attackExecutionId: Long) : CombatEvent
    data class HitConfirmed(
        val attackExecutionId: Long,
        val targetId: UUID,
        val weapon: WeaponType,
    ) : CombatEvent
}

private enum class AttackPhase {
    WINDUP,
    ACTIVE,
    RECOVERY,
}

/** Minimal server-owned state machine for the normal attack. */
class CombatState(
    private val executionIdSource: () -> Long = ExecutionIds::next,
    private val weaponSource: () -> WeaponType = { WeaponType.HEAVY_BLADE },
    private val attackSpeedSource: () -> Double = { 1.0 },
) {
    private var held = false
    private var phase: AttackPhase? = null
    private var phaseTicks = 0
    private var executionId = 0L
    private var deferAttackRestart = false
    private val hitTargets = mutableSetOf<UUID>()
    var activeProfile: WeaponProfile? = null
        private set

    val isAttacking: Boolean
        get() = phase != null

    fun input(state: AttackInputState): List<CombatEvent> {
        held = state == AttackInputState.PRESS
        if (state == AttackInputState.RELEASE) deferAttackRestart = false
        return if (state == AttackInputState.PRESS && phase == null) startAttack() else emptyList()
    }

    fun deferAttackRestart() {
        if (phase != null) deferAttackRestart = true
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
                if (target.id !in hitTargets && isInAttackRange(activeProfile!!, position, direction, target.position)) {
                    hitTargets += target.id
                    events += CombatEvent.HitConfirmed(executionId, target.id, activeProfile!!.weapon)
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
            phaseTicks = when (phase) {
                AttackPhase.WINDUP -> activeProfile!!.startupTicks
                AttackPhase.ACTIVE -> activeProfile!!.activeTicks
                AttackPhase.RECOVERY -> activeProfile!!.recoveryTicks
                null -> 0
            }
            if (phase == null) {
                activeProfile = null
                hitTargets.clear()
                if (held && !deferAttackRestart) events += startAttack()
                deferAttackRestart = false
            }
        }
        return events
    }

    private fun startAttack(): List<CombatEvent> {
        executionId = executionIdSource()
        activeProfile = weaponSource().profile(attackSpeedSource())
        phase = AttackPhase.WINDUP
        phaseTicks = activeProfile!!.startupTicks
        hitTargets.clear()
        return listOf(CombatEvent.Started(executionId))
    }

    companion object {
        fun isInAttackRange(profile: WeaponProfile, position: Point, direction: Vec, target: Point): Boolean {
            val offset = Vec(target.x() - position.x(), target.y() - position.y(), target.z() - position.z())
            val horizontalDistance = kotlin.math.sqrt(offset.x() * offset.x() + offset.z() * offset.z())
            if (horizontalDistance > profile.range || kotlin.math.abs(offset.y()) > profile.verticalRange) return false
            if (horizontalDistance == 0.0) return true
            val forward = Vec(direction.x(), 0.0, direction.z()).normalize()
            val toTarget = Vec(offset.x(), 0.0, offset.z()).normalize()
            return forward.x() * toTarget.x() + forward.z() * toTarget.z() >= profile.minForwardDot
        }
    }
}

internal fun dodgeDirection(facing: Vec, input: DodgeInput): Vec {
    val horizontalLength = sqrt(facing.x() * facing.x() + facing.z() * facing.z())
    val forward = if (horizontalLength > 1.0e-9) {
        Vec(facing.x() / horizontalLength, 0.0, facing.z() / horizontalLength)
    } else {
        Vec(0.0, 0.0, 1.0)
    }
    if (input.directionX == 0.0 && input.directionZ == 0.0) return forward

    val right = Vec(-forward.z(), 0.0, forward.x())
    return DodgeState.normalizeDirection(
        Vec(
            right.x() * input.directionX + forward.x() * input.directionZ,
            0.0,
            right.z() * input.directionX + forward.z() * input.directionZ,
        ),
    )
}

class DodgeState {
    private var active = false
    private var elapsedTicks = 0
    private var direction = Vec.ZERO
    private var pendingInput: DodgeInput? = null

    val isActive: Boolean
        get() = active

    val hasPending: Boolean
        get() = pendingInput != null

    fun request(
        input: DodgeInput,
        canStart: Boolean,
        facing: Vec,
        startAllowed: () -> Boolean = { true },
        onStart: () -> Unit = {},
    ): Boolean {
        if (isActive || pendingInput != null) return false

        if (canStart) {
            if (!startAllowed()) return false
            start(dodgeDirection(facing, input))
        } else {
            if (!startAllowed()) return false
            pendingInput = input
        }
        if (canStart) onStart()
        return true
    }

    fun tick(
        canStart: Boolean,
        facing: Vec,
        startAllowed: () -> Boolean = { true },
        onStart: () -> Unit = {},
    ): Vec? {
        if (!isActive && canStart) {
            pendingInput?.let {
                pendingInput = null
                if (startAllowed()) {
                    start(dodgeDirection(facing, it))
                    onStart()
                }
            }
        }
        if (!isActive) return null

        val previousProgress = progress(elapsedTicks.toDouble() / DURATION_TICKS)
        elapsedTicks++
        val currentProgress = progress(elapsedTicks.toDouble() / DURATION_TICKS)
        if (elapsedTicks == DURATION_TICKS) active = false
        return direction.mul(DISTANCE * (currentProgress - previousProgress))
    }

    private fun start(direction: Vec) {
        this.direction = direction
        elapsedTicks = 0
        active = true
    }

    internal fun stop() {
        active = false
        elapsedTicks = DURATION_TICKS
    }

    companion object {
        const val DISTANCE = 3.0
        const val DURATION_TICKS = 8

        private fun progress(time: Double): Double {
            val t = time.coerceIn(0.0, 1.0)
            return 1.0 - (1.0 - t) * (1.0 - t)
        }

        fun normalizeDirection(direction: Vec): Vec {
            val length = kotlin.math.sqrt(direction.x() * direction.x() + direction.z() * direction.z())
            require(length > 1.0e-9) { "Dodge direction must not be zero" }
            return Vec(direction.x() / length, 0.0, direction.z() / length)
        }
    }
}

private object ExecutionIds {
    private var nextId = 0L

    @Synchronized
    fun next(): Long = ++nextId
}
