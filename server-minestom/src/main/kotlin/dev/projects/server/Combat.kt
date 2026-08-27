package dev.projects.server

import dev.projects.protocol.AttackInputState
import dev.projects.protocol.DodgeInput
import dev.projects.server.combat.CombatBuildSnapshot
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
                range = 3.5,
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

data class CombatTarget(
    val id: UUID,
    val position: Point,
    val halfExtent: Vec = Vec.ZERO,
    val sphereRadius: Double = 0.0,
)

sealed interface CombatEvent {
    data class Started(val attackExecutionId: Long) : CombatEvent
    data class Active(
        val attackExecutionId: Long,
        val position: Point,
        val direction: Vec,
        val profile: WeaponProfile,
    ) : CombatEvent
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
    private val buildSource: () -> CombatBuildSnapshot? = { null },
) {
    private var held = false
    private var phase: AttackPhase? = null
    private var phaseTicks = 0
    private var executionId = 0L
    private var deferAttackRestart = false
    private val hitTargets = mutableSetOf<UUID>()
    var activeProfile: WeaponProfile? = null
        private set

    var activeBuildSnapshot: CombatBuildSnapshot? = null
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

    fun reset() {
        held = false
        phase = null
        phaseTicks = 0
        executionId = 0L
        deferAttackRestart = false
        hitTargets.clear()
        activeProfile = null
        activeBuildSnapshot = null
    }

    fun tick(position: Point, direction: Vec, targets: Collection<CombatTarget>): List<CombatEvent> {
        if (phase == null) {
            if (held) return startAttack()
            return emptyList()
        }

        val events = mutableListOf<CombatEvent>()
        phaseTicks--
        if (phase == AttackPhase.ACTIVE) {
            val profile = requireNotNull(activeProfile)
            events += CombatEvent.Active(executionId, position, direction, profile)
            for (target in targets) {
                if (target.id !in hitTargets && isInAttackRange(profile, position, direction, target)) {
                    hitTargets += target.id
                    events += CombatEvent.HitConfirmed(executionId, target.id, profile.weapon)
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
                activeBuildSnapshot = null
                hitTargets.clear()
                if (held && !deferAttackRestart) events += startAttack()
                deferAttackRestart = false
            }
        }
        return events
    }

    private fun startAttack(): List<CombatEvent> {
        executionId = executionIdSource()
        activeBuildSnapshot = buildSource()
        activeProfile = weaponSource().profile(
            attackSpeedSource() * (activeBuildSnapshot?.stats?.attackSpeedMultiplier ?: 1.0),
        )
        phase = AttackPhase.WINDUP
        phaseTicks = activeProfile!!.startupTicks
        hitTargets.clear()
        return listOf(CombatEvent.Started(executionId))
    }

    companion object {
        private const val RANGE_EPSILON = 1.0e-9

        fun isInAttackRange(profile: WeaponProfile, position: Point, direction: Vec, target: CombatTarget): Boolean {
            if (profile.weapon == WeaponType.TWIN_RODS) {
                return if (target.sphereRadius > 0.0) {
                    isInTwinRodsSphereRange(profile, position, direction, target.position, target.sphereRadius)
                } else {
                    isInTwinRodsBoxRange(profile, position, direction, target.position, target.halfExtent)
                }
            }
            return isInAttackRange(profile, position, direction, target.position)
        }

        fun isInAttackRange(profile: WeaponProfile, position: Point, direction: Vec, target: Point): Boolean {
            val offset = Vec(target.x() - position.x(), target.y() - position.y(), target.z() - position.z())
            if (profile.weapon == WeaponType.TWIN_RODS) {
                return isInTwinRodsPointRange(profile, position, direction, target)
            }
            val horizontalDistance = kotlin.math.sqrt(offset.x() * offset.x() + offset.z() * offset.z())
            if (horizontalDistance > profile.range || kotlin.math.abs(offset.y()) > profile.verticalRange) return false
            if (horizontalDistance == 0.0) return true
            val forward = Vec(direction.x(), 0.0, direction.z()).normalize()
            val toTarget = Vec(offset.x(), 0.0, offset.z()).normalize()
            return forward.x() * toTarget.x() + forward.z() * toTarget.z() >= profile.minForwardDot
        }

        private fun isInTwinRodsPointRange(
            profile: WeaponProfile,
            position: Point,
            direction: Vec,
            target: Point,
        ): Boolean {
            val normalizedDirection = normalizeDirection(direction) ?: return false
            val offsetX = target.x() - position.x()
            val offsetY = target.y() - position.y()
            val offsetZ = target.z() - position.z()
            return isInTwinRodsPointRange(profile, normalizedDirection, offsetX, offsetY, offsetZ)
        }

        private fun isInTwinRodsBoxRange(
            profile: WeaponProfile,
            position: Point,
            direction: Vec,
            target: Point,
            halfExtent: Vec,
        ): Boolean {
            val normalizedDirection = normalizeDirection(direction) ?: return false
            val minX = target.x() - halfExtent.x()
            val minY = target.y() - halfExtent.y()
            val minZ = target.z() - halfExtent.z()
            val maxX = target.x() + halfExtent.x()
            val maxY = target.y() + halfExtent.y()
            val maxZ = target.z() + halfExtent.z()
            fun containsAttackPoint(x: Double, y: Double, z: Double): Boolean =
                isInTwinRodsPointRange(
                    profile,
                    normalizedDirection,
                    x - position.x(),
                    y - position.y(),
                    z - position.z(),
                )

            val closestX = position.x().coerceIn(minX, maxX)
            val closestY = position.y().coerceIn(minY, maxY)
            val closestZ = position.z().coerceIn(minZ, maxZ)
            if (containsAttackPoint(closestX, closestY, closestZ)) return true
            if (containsAttackPoint(target.x(), target.y(), target.z())) return true

            for (x in doubleArrayOf(minX, maxX)) {
                for (y in doubleArrayOf(minY, maxY)) {
                    for (z in doubleArrayOf(minZ, maxZ)) {
                        if (containsAttackPoint(x, y, z)) return true
                    }
                }
            }

            val centerOffsetX = target.x() - position.x()
            val centerOffsetY = target.y() - position.y()
            val centerOffsetZ = target.z() - position.z()
            val axisProjection = maxOf(
                0.0,
                centerOffsetX * normalizedDirection.x() +
                    centerOffsetY * normalizedDirection.y() +
                    centerOffsetZ * normalizedDirection.z(),
            )
            val axisX = position.x() + normalizedDirection.x() * axisProjection
            val axisY = position.y() + normalizedDirection.y() * axisProjection
            val axisZ = position.z() + normalizedDirection.z() * axisProjection
            return containsAttackPoint(
                axisX.coerceIn(minX, maxX),
                axisY.coerceIn(minY, maxY),
                axisZ.coerceIn(minZ, maxZ),
            )
        }

        private fun isInTwinRodsSphereRange(
            profile: WeaponProfile,
            position: Point,
            direction: Vec,
            target: Point,
            radius: Double,
        ): Boolean {
            val normalizedDirection = normalizeDirection(direction) ?: return false
            val offsetX = target.x() - position.x()
            val offsetY = target.y() - position.y()
            val offsetZ = target.z() - position.z()
            val distance = sqrt(offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ)
            if (distance <= radius + RANGE_EPSILON) return true
            if (distance - radius > profile.range + RANGE_EPSILON) return false

            val centerDot = (
                normalizedDirection.x() * offsetX +
                    normalizedDirection.y() * offsetY +
                    normalizedDirection.z() * offsetZ
                ) / distance
            val centerAngle = kotlin.math.acos(centerDot.coerceIn(-1.0, 1.0))
            val coneAngle = kotlin.math.acos(profile.minForwardDot.coerceIn(-1.0, 1.0))
            val closestConeAngle = maxOf(0.0, centerAngle - coneAngle)
            val closestConeProjection = distance * kotlin.math.cos(closestConeAngle)
            if (closestConeProjection <= 0.0) return false
            val discriminant = radius * radius - distance * distance + closestConeProjection * closestConeProjection
            if (discriminant < -RANGE_EPSILON) return false
            val nearDistance = closestConeProjection - sqrt(maxOf(0.0, discriminant))
            return nearDistance <= profile.range + RANGE_EPSILON
        }

        private fun isInTwinRodsPointRange(
            profile: WeaponProfile,
            direction: Vec,
            offsetX: Double,
            offsetY: Double,
            offsetZ: Double,
        ): Boolean {
            val distance = sqrt(offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ)
            if (distance > profile.range + RANGE_EPSILON) return false
            if (distance <= RANGE_EPSILON) return true
            val forwardDot = (
                direction.x() * offsetX +
                    direction.y() * offsetY +
                    direction.z() * offsetZ
                ) / distance
            return forwardDot >= profile.minForwardDot
        }

        private fun normalizeDirection(direction: Vec): Vec? {
            val length = sqrt(
                direction.x() * direction.x() +
                    direction.y() * direction.y() +
                    direction.z() * direction.z(),
            )
            return if (length > RANGE_EPSILON) {
                Vec(direction.x() / length, direction.y() / length, direction.z() / length)
            } else {
                null
            }
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

    fun reset() {
        active = false
        elapsedTicks = 0
        direction = Vec.ZERO
        pendingInput = null
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
