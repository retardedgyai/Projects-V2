package dev.projects.server

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal enum class RoninSkill {
    Q,
    W,
    E,
    R,
}

internal enum class RoninWVariant {
    NONE,
    WOUND,
    CROSSCUT,
    TEMPEST,
}

internal enum class RoninCastEventKind {
    Q_IMPACT,
    W_INITIAL,
    W_DELAYED,
    E_BLINK,
    R_IMPACT,
    W3_PULSE,
    W3_FINAL,
}

internal data class RoninCast(
    val castId: Long,
    val skill: RoninSkill,
    val variant: RoninWVariant,
    val origin: Point,
    val direction: Vec,
)

internal data class RoninCastEvent(
    val kind: RoninCastEventKind,
    val castId: Long,
    val targetId: UUID? = null,
    val pulseIndex: Int = 0,
)

internal data class RoninTickResult(
    val events: List<RoninCastEvent>,
    val completedCast: RoninCast?,
)

/**
 * Ronin-only server state. This is intentionally not a shared class or effect
 * framework: it owns just the mechanics needed by the Ronin prototype.
 */
internal class RoninState(
    private val castIdSource: () -> Long = RoninExecutionIds::next,
) {
    private data class ActiveCast(
        val cast: RoninCast,
        val lockTicks: Int,
        var ageTicks: Int = 0,
        var hitEnemy: Boolean = false,
        var delayedTargetId: UUID? = null,
        var healingAccumulated: Double = 0.0,
    )

    private var activeCast: ActiveCast? = null
    private var iaidoValue = 0
    private var qCooldown = 0
    private var eCooldown = 0
    private var rCooldown = 0
    private val wounds = mutableMapOf<UUID, Int>()
    private val severed = mutableMapOf<UUID, Int>()
    private val woundExecutionTargets = mutableMapOf<Long, MutableSet<UUID>>()

    val iaido: Int
        get() = iaidoValue

    val isMovementLocked: Boolean
        get() = activeCast != null

    val movementLockTicksRemaining: Int
        get() = activeCast?.let { (it.lockTicks - it.ageTicks).coerceAtLeast(0) } ?: 0

    val isW3Untargetable: Boolean
        get() {
            val active = activeCast ?: return false
            return active.cast.variant == RoninWVariant.TEMPEST &&
                active.ageTicks >= RoninBalance.W3_INVULNERABLE_START_TICK &&
                active.ageTicks < RoninBalance.W3_INVULNERABLE_END_TICK
        }

    val currentCast: RoninCast?
        get() = activeCast?.cast

    val qCooldownTicksRemaining: Int
        get() = qCooldown

    val eCooldownTicksRemaining: Int
        get() = eCooldown

    val rCooldownTicksRemaining: Int
        get() = rCooldown

    fun cooldownRemaining(skill: RoninSkill): Int = when (skill) {
        RoninSkill.Q -> qCooldown
        RoninSkill.W -> 0
        RoninSkill.E -> eCooldown
        RoninSkill.R -> rCooldown
    }

    fun reset() {
        activeCast = null
        iaidoValue = 0
        qCooldown = 0
        eCooldown = 0
        rCooldown = 0
        wounds.clear()
        severed.clear()
        woundExecutionTargets.clear()
    }

    fun tryCast(skill: RoninSkill, origin: Point, direction: Vec): RoninCast? {
        if (activeCast != null || cooldownRemaining(skill) > 0) return null
        val variant = when (skill) {
            RoninSkill.Q -> RoninWVariant.NONE
            RoninSkill.W -> when (iaidoValue) {
                1 -> RoninWVariant.WOUND
                2 -> RoninWVariant.CROSSCUT
                3 -> RoninWVariant.TEMPEST
                else -> return null
            }
            RoninSkill.E -> RoninWVariant.NONE
            RoninSkill.R -> RoninWVariant.NONE
        }
        val cast = RoninCast(
            castId = castIdSource(),
            skill = skill,
            variant = variant,
            origin = origin,
            direction = normalizeDirection(direction),
        )
        activeCast = ActiveCast(cast, lockTicksFor(skill, variant))
        when (skill) {
            RoninSkill.Q -> qCooldown = RoninBalance.Q_COOLDOWN_TICKS
            RoninSkill.W -> iaidoValue = 0
            RoninSkill.E -> eCooldown = RoninBalance.E_COOLDOWN_TICKS
            RoninSkill.R -> rCooldown = RoninBalance.R_COOLDOWN_TICKS
        }
        return cast
    }

    /** Advances one server tick and emits only the scheduled Ronin cast phases. */
    fun tick(): RoninTickResult {
        qCooldown = (qCooldown - 1).coerceAtLeast(0)
        eCooldown = (eCooldown - 1).coerceAtLeast(0)
        rCooldown = (rCooldown - 1).coerceAtLeast(0)
        tickTimedEffects(wounds)
        tickTimedEffects(severed)

        val active = activeCast ?: return RoninTickResult(emptyList(), null)
        active.ageTicks++
        val events = when (active.cast.skill to active.cast.variant) {
            RoninSkill.Q to RoninWVariant.NONE -> scheduledEvent(
                active,
                RoninBalance.Q_IMPACT_TICK,
                RoninCastEventKind.Q_IMPACT,
            )
            RoninSkill.W to RoninWVariant.WOUND -> scheduledEvent(
                active,
                RoninBalance.W1_IMPACT_TICK,
                RoninCastEventKind.W_INITIAL,
            )
            RoninSkill.W to RoninWVariant.CROSSCUT -> buildList {
                if (active.ageTicks == RoninBalance.W2_INITIAL_IMPACT_TICK) {
                    add(RoninCastEvent(RoninCastEventKind.W_INITIAL, active.cast.castId))
                }
                if (active.ageTicks == RoninBalance.W2_DELAYED_IMPACT_TICK) {
                    add(
                        RoninCastEvent(
                            kind = RoninCastEventKind.W_DELAYED,
                            castId = active.cast.castId,
                            targetId = active.delayedTargetId,
                        ),
                    )
                }
            }
            RoninSkill.W to RoninWVariant.TEMPEST -> buildList {
                when (active.ageTicks) {
                    RoninBalance.W3_PULSE_1_TICK -> add(
                        RoninCastEvent(RoninCastEventKind.W3_PULSE, active.cast.castId, pulseIndex = 1),
                    )
                    RoninBalance.W3_PULSE_2_TICK -> add(
                        RoninCastEvent(RoninCastEventKind.W3_PULSE, active.cast.castId, pulseIndex = 2),
                    )
                    RoninBalance.W3_PULSE_3_TICK -> add(
                        RoninCastEvent(RoninCastEventKind.W3_PULSE, active.cast.castId, pulseIndex = 3),
                    )
                    RoninBalance.W3_FINAL_TICK -> add(
                        RoninCastEvent(RoninCastEventKind.W3_FINAL, active.cast.castId),
                    )
                }
            }
            RoninSkill.E to RoninWVariant.NONE -> scheduledEvent(
                active,
                RoninBalance.E_BLINK_TICK,
                RoninCastEventKind.E_BLINK,
            )
            RoninSkill.R to RoninWVariant.NONE -> scheduledEvent(
                active,
                RoninBalance.R_IMPACT_TICK,
                RoninCastEventKind.R_IMPACT,
            )
            else -> emptyList()
        }

        val completed = if (active.ageTicks >= active.lockTicks) {
            activeCast = null
            if (active.hitEnemy && active.cast.skill in setOf(RoninSkill.Q, RoninSkill.E, RoninSkill.R)) {
                iaidoValue = (iaidoValue + 1).coerceAtMost(RoninBalance.MAX_IAIDO)
            }
            active.cast
        } else {
            null
        }
        return RoninTickResult(events, completed)
    }

    /** Marks that this cast hit at least one enemy; repeated targets still add only once. */
    fun recordEnemyHit(): Boolean {
        val active = activeCast ?: return false
        if (active.hitEnemy) return false
        active.hitEnemy = true
        return true
    }

    fun lockDelayedTarget(targetId: UUID): Boolean {
        val active = activeCast ?: return false
        if (active.cast.variant != RoninWVariant.CROSSCUT || active.delayedTargetId != null) return false
        active.delayedTargetId = targetId
        return true
    }

    fun delayedTargetId(): UUID? = activeCast?.delayedTargetId

    fun recordW3Healing(actualDamage: Int, maxHealth: Int): Double {
        val active = activeCast ?: return 0.0
        if (active.cast.variant != RoninWVariant.TEMPEST || actualDamage <= 0 || maxHealth <= 0) return 0.0
        val cap = maxHealth * RoninBalance.W3_HEAL_CAP_RATIO
        val amount = (actualDamage * RoninBalance.W3_HEAL_RATIO).coerceAtMost(cap - active.healingAccumulated)
            .coerceAtLeast(0.0)
        active.healingAccumulated += amount
        return amount
    }

    fun applyWound(targetId: UUID) {
        wounds[targetId] = RoninBalance.WOUND_DURATION_TICKS
    }

    fun woundRemaining(targetId: UUID): Int = wounds[targetId] ?: 0

    /**
     * Consumes at most three wounds during one direct-damage execution. DoT and
     * secondary damage never call this method, so they cannot consume Wound.
     */
    fun consumeWound(executionId: Long, targetId: UUID): Boolean {
        if (targetId !in wounds) return false
        val targets = woundExecutionTargets.getOrPut(executionId) { mutableSetOf() }
        if (targetId in targets || targets.size >= RoninBalance.WOUND_HEAL_TARGET_CAP) return false
        targets += targetId
        wounds.remove(targetId)
        if (woundExecutionTargets.size > 512) {
            woundExecutionTargets.keys.minOrNull()?.let(woundExecutionTargets::remove)
        }
        return true
    }

    fun applySevered(targetId: UUID) {
        severed[targetId] = RoninBalance.SEVERED_DURATION_TICKS
    }

    fun severedRemaining(targetId: UUID): Int = severed[targetId] ?: 0

    fun roninDamageMultiplier(targetId: UUID): Double =
        if (targetId in severed) RoninBalance.SEVERED_DAMAGE_MULTIPLIER else 1.0

    private fun scheduledEvent(
        active: ActiveCast,
        scheduledTick: Int,
        kind: RoninCastEventKind,
    ): List<RoninCastEvent> = if (active.ageTicks == scheduledTick) {
        listOf(RoninCastEvent(kind, active.cast.castId))
    } else {
        emptyList()
    }

    private fun lockTicksFor(skill: RoninSkill, variant: RoninWVariant): Int = when (skill to variant) {
        RoninSkill.Q to RoninWVariant.NONE -> RoninBalance.Q_LOCK_TICKS
        RoninSkill.W to RoninWVariant.WOUND -> RoninBalance.W1_LOCK_TICKS
        RoninSkill.W to RoninWVariant.CROSSCUT -> RoninBalance.W2_LOCK_TICKS
        RoninSkill.W to RoninWVariant.TEMPEST -> RoninBalance.W3_LOCK_TICKS
        RoninSkill.E to RoninWVariant.NONE -> RoninBalance.E_LOCK_TICKS
        RoninSkill.R to RoninWVariant.NONE -> RoninBalance.R_LOCK_TICKS
        else -> error("Invalid Ronin skill variant: $skill/$variant")
    }

    private fun tickTimedEffects(effects: MutableMap<UUID, Int>) {
        effects.entries.removeIf { (_, remaining) -> remaining - 1 <= 0 }
        effects.keys.toList().forEach { targetId ->
            effects[targetId] = effects.getValue(targetId) - 1
        }
    }

    companion object {
        private fun normalizeDirection(direction: Vec): Vec {
            val length = sqrt(direction.x() * direction.x() + direction.y() * direction.y() + direction.z() * direction.z())
            return if (length > 1.0e-9) direction.mul(1.0 / length) else Vec(0.0, 0.0, 1.0)
        }
    }
}

internal object RoninBalance {
    const val MAX_IAIDO = 3
    const val Q_COOLDOWN_TICKS = 160
    const val E_COOLDOWN_TICKS = 300
    const val R_COOLDOWN_TICKS = 600

    const val AA_DAMAGE = 18
    const val Q_DAMAGE = 30
    const val W1_DAMAGE = 22
    const val W2_INITIAL_DAMAGE = 25
    const val W2_DELAYED_DAMAGE = 45
    const val W3_PULSE_DAMAGE = 16
    const val W3_FINAL_DAMAGE = 42
    const val E_DAMAGE = 28
    const val R_DAMAGE = 70

    const val Q_RANGE = 5.5
    const val Q_WIDTH = 7.0
    const val Q_VERTICAL_TOLERANCE = 2.5
    const val W1_RANGE = 5.0
    const val W1_WIDTH = 6.0
    const val W1_VERTICAL_TOLERANCE = 2.5
    const val W2_RANGE = 6.0
    const val W3_RADIUS = 5.0
    const val E_RANGE = 8.0
    const val R_RANGE = 7.0
    const val R_HALF_ANGLE_DEGREES = 90.0
    const val R_SWEET_HALF_ANGLE_DEGREES = 45.0

    const val Q_LOCK_TICKS = 9
    const val W1_LOCK_TICKS = 9
    const val W2_LOCK_TICKS = 16
    const val W3_LOCK_TICKS = 24
    const val E_LOCK_TICKS = 10
    const val R_LOCK_TICKS = 22

    const val Q_IMPACT_TICK = 2
    const val W1_IMPACT_TICK = 2
    const val W2_INITIAL_IMPACT_TICK = 2
    const val W2_DELAYED_IMPACT_TICK = 8
    const val W3_PULSE_1_TICK = 4
    const val W3_PULSE_2_TICK = 8
    const val W3_PULSE_3_TICK = 12
    const val W3_FINAL_TICK = 20
    const val E_BLINK_TICK = 3
    const val R_IMPACT_TICK = 6

    const val W3_INVULNERABLE_START_TICK = 4
    const val W3_INVULNERABLE_END_TICK = 21
    const val WOUND_DURATION_TICKS = 120
    const val SEVERED_DURATION_TICKS = 100
    const val SEVERED_DAMAGE_MULTIPLIER = 1.15
    const val WOUND_HEAL_TARGET_CAP = 3
    const val WOUND_HEAL_RATIO = 0.06
    const val W3_HEAL_RATIO = 0.20
    const val W3_HEAL_CAP_RATIO = 0.35
}

private object RoninExecutionIds {
    private var nextId = 0L

    @Synchronized
    fun next(): Long = ++nextId
}

internal fun isRoninFrontVolumeHit(
    origin: Point,
    direction: Vec,
    target: CombatTarget,
    range: Double,
    width: Double,
    verticalTolerance: Double,
): Boolean {
    val horizontalLength = sqrt(direction.x() * direction.x() + direction.z() * direction.z())
    if (horizontalLength <= 1.0e-9) return false
    val forwardX = direction.x() / horizontalLength
    val forwardZ = direction.z() / horizontalLength
    val rightX = -forwardZ
    val rightZ = forwardX
    val offsetX = target.position.x() - origin.x()
    val offsetY = target.position.y() - origin.y()
    val offsetZ = target.position.z() - origin.z()
    val projection = offsetX * forwardX + offsetZ * forwardZ
    val lateral = offsetX * rightX + offsetZ * rightZ
    val projectionExtent = abs(forwardX) * target.halfExtent.x() + abs(forwardZ) * target.halfExtent.z()
    val lateralExtent = abs(rightX) * target.halfExtent.x() + abs(rightZ) * target.halfExtent.z()
    return projection + projectionExtent >= 0.0 &&
        projection - projectionExtent <= range &&
        abs(lateral) <= width / 2.0 + lateralExtent &&
        abs(offsetY) <= verticalTolerance + target.halfExtent.y()
}

internal fun isRoninRadialHit(center: Point, radius: Double, target: CombatTarget): Boolean {
    val closestX = center.x().coerceIn(target.position.x() - target.halfExtent.x(), target.position.x() + target.halfExtent.x())
    val closestY = center.y().coerceIn(target.position.y() - target.halfExtent.y(), target.position.y() + target.halfExtent.y())
    val closestZ = center.z().coerceIn(target.position.z() - target.halfExtent.z(), target.position.z() + target.halfExtent.z())
    val dx = closestX - center.x()
    val dy = closestY - center.y()
    val dz = closestZ - center.z()
    return dx * dx + dy * dy + dz * dz <= radius * radius + 1.0e-9
}

internal fun isRoninSectorHit(
    origin: Point,
    direction: Vec,
    target: CombatTarget,
    range: Double,
    halfAngleDegrees: Double,
): Boolean {
    val horizontalLength = sqrt(direction.x() * direction.x() + direction.z() * direction.z())
    if (horizontalLength <= 1.0e-9) return false
    val forwardX = direction.x() / horizontalLength
    val forwardZ = direction.z() / horizontalLength
    val offsetX = target.position.x() - origin.x()
    val offsetZ = target.position.z() - origin.z()
    val distance = sqrt(offsetX * offsetX + offsetZ * offsetZ)
    val targetRadius = sqrt(target.halfExtent.x() * target.halfExtent.x() + target.halfExtent.z() * target.halfExtent.z())
    if (distance <= targetRadius) return true
    if (distance - targetRadius > range + 1.0e-9) return false
    val dot = ((offsetX * forwardX + offsetZ * forwardZ) / distance).coerceIn(-1.0, 1.0)
    val centerAngle = acos(dot)
    val angularRadius = asin((targetRadius / distance).coerceIn(0.0, 1.0))
    return centerAngle <= Math.toRadians(halfAngleDegrees) + angularRadius + 1.0e-9
}

internal fun roninForwardProjection(origin: Point, direction: Vec, target: CombatTarget): Double {
    val horizontalLength = sqrt(direction.x() * direction.x() + direction.z() * direction.z())
    if (horizontalLength <= 1.0e-9) return Double.POSITIVE_INFINITY
    return ((target.position.x() - origin.x()) * direction.x() +
        (target.position.z() - origin.z()) * direction.z()) / horizontalLength
}

internal fun roninSegmentIntersectsAabb(start: Point, end: Point, target: CombatTarget): Boolean {
    val minX = target.position.x() - target.halfExtent.x()
    val maxX = target.position.x() + target.halfExtent.x()
    val minY = target.position.y() - target.halfExtent.y()
    val maxY = target.position.y() + target.halfExtent.y()
    val minZ = target.position.z() - target.halfExtent.z()
    val maxZ = target.position.z() + target.halfExtent.z()
    var near = 0.0
    var far = 1.0

    fun update(startValue: Double, delta: Double, minValue: Double, maxValue: Double): Boolean {
        if (abs(delta) <= 1.0e-9) return startValue in minValue..maxValue
        var axisNear = (minValue - startValue) / delta
        var axisFar = (maxValue - startValue) / delta
        if (axisNear > axisFar) {
            val swap = axisNear
            axisNear = axisFar
            axisFar = swap
        }
        near = max(near, axisNear)
        far = min(far, axisFar)
        return near <= far
    }

    return update(start.x(), end.x() - start.x(), minX, maxX) &&
        update(start.y(), end.y() - start.y(), minY, maxY) &&
        update(start.z(), end.z() - start.z(), minZ, maxZ) &&
        far >= 0.0 && near <= 1.0
}
