package dev.projects.server.mob

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** The same local-space shape supplies both damage tests and the ground warning outline. */
sealed interface MobAttackShape {
    val verticalReach: Double get() = 2.2

    fun contains(origin: Point, facing: Vec, target: Point): Boolean
    fun outline(origin: Point, facing: Vec): List<Pos>

    data class Sweep(val radius: Double = 3.4, val halfAngleDegrees: Double = 65.0) : MobAttackShape {
        init { require(radius > 0.0 && halfAngleDegrees in 1.0..180.0) }

        override fun contains(origin: Point, facing: Vec, target: Point): Boolean {
            if (abs(target.y() - origin.y()) > verticalReach) return false
            val offset = horizontal(target.sub(origin))
            val distance = horizontalLength(offset)
            if (distance > radius + EPSILON) return false
            if (distance <= EPSILON) return true
            return normalizeHorizontal(facing).dot(offset.div(distance)) + EPSILON >= cos(Math.toRadians(halfAngleDegrees))
        }

        override fun outline(origin: Point, facing: Vec): List<Pos> {
            val baseAngle = atan2(facing.z(), facing.x())
            val halfAngle = Math.toRadians(halfAngleDegrees)
            val arc = (0..24).map { sample ->
                val angle = baseAngle - halfAngle + 2.0 * halfAngle * sample / 24.0
                groundPoint(origin, cos(angle) * radius, sin(angle) * radius)
            }
            val sides = listOf(-halfAngle, halfAngle).flatMap { side ->
                (0..7).map { sample ->
                    val distance = radius * sample / 7.0
                    groundPoint(origin, cos(baseAngle + side) * distance, sin(baseAngle + side) * distance)
                }
            }
            return arc + sides
        }
    }

    data class Slam(val length: Double = 5.8, val halfWidth: Double = 1.25) : MobAttackShape {
        init { require(length > 0.0 && halfWidth > 0.0) }

        override fun contains(origin: Point, facing: Vec, target: Point): Boolean {
            if (abs(target.y() - origin.y()) > verticalReach) return false
            val forward = normalizeHorizontal(facing)
            val offset = horizontal(target.sub(origin))
            val along = offset.dot(forward)
            val across = offset.x() * -forward.z() + offset.z() * forward.x()
            return along >= -EPSILON && along <= length + EPSILON && abs(across) <= halfWidth + EPSILON
        }

        override fun outline(origin: Point, facing: Vec): List<Pos> {
            val forward = normalizeHorizontal(facing)
            val side = Vec(-forward.z(), 0.0, forward.x())
            fun point(along: Double, across: Double): Pos {
                val offset = forward.mul(along).add(side.mul(across))
                return groundPoint(origin, offset.x(), offset.z())
            }
            return (-1..1 step 2).flatMap { sign ->
                (0..16).map { point(length * it / 16.0, sign * halfWidth) }
            } + listOf(0.0, length).flatMap { along ->
                (0..8).map { point(along, -halfWidth + 2.0 * halfWidth * it / 8.0) }
            }
        }
    }

    /** Ring leaves an explicit safe center; innerRadius=0 is a filled circular blast. */
    data class Ring(val radius: Double, val innerRadius: Double = 0.0) : MobAttackShape {
        init { require(radius > 0.0 && innerRadius >= 0.0 && innerRadius < radius) }

        override fun contains(origin: Point, facing: Vec, target: Point): Boolean {
            if (abs(target.y() - origin.y()) > verticalReach) return false
            val distance = horizontalLength(target.sub(origin))
            return distance <= radius + EPSILON && distance + EPSILON >= innerRadius
        }

        override fun outline(origin: Point, facing: Vec): List<Pos> =
            listOf(radius, innerRadius).filter { it > 0.0 }.flatMap { edge ->
                (0..48).map { sample ->
                    val angle = sample * Math.PI * 2.0 / 48.0
                    groundPoint(origin, cos(angle) * edge, sin(angle) * edge)
                }
            }
    }

    /** Two perpendicular strips. The same union supplies the furnace floor warning and damage. */
    data class Cross(val armLength: Double, val halfWidth: Double) : MobAttackShape {
        init { require(armLength > halfWidth && halfWidth > 0.0) }
        override fun contains(origin: Point, facing: Vec, target: Point): Boolean {
            if (abs(target.y() - origin.y()) > verticalReach) return false
            val forward = normalizeHorizontal(facing)
            val delta = horizontal(target.sub(origin))
            val along = abs(delta.dot(forward))
            val across = abs(delta.x() * -forward.z() + delta.z() * forward.x())
            return (along <= armLength && across <= halfWidth) || (across <= armLength && along <= halfWidth)
        }
        override fun outline(origin: Point, facing: Vec): List<Pos> {
            val forward = normalizeHorizontal(facing)
            val side = Vec(-forward.z(), 0.0, forward.x())
            val corners = listOf(-halfWidth to -armLength, halfWidth to -armLength, halfWidth to -halfWidth,
                armLength to -halfWidth, armLength to halfWidth, halfWidth to halfWidth, halfWidth to armLength,
                -halfWidth to armLength, -halfWidth to halfWidth, -armLength to halfWidth, -armLength to -halfWidth,
                -halfWidth to -halfWidth)
            return corners.indices.flatMap { index ->
                val a = corners[index]
                val b = corners[(index + 1) % corners.size]
                (0..8).map { sample ->
                    val t = sample / 8.0
                    val offset = forward.mul(a.first + (b.first - a.first) * t).add(side.mul(a.second + (b.second - a.second) * t))
                    Pos(origin.x() + offset.x(), origin.y() + 0.12, origin.z() + offset.z())
                }
            }
        }
    }

    companion object {
        private const val EPSILON = 1.0e-8
        private fun groundPoint(origin: Point, x: Double, z: Double) =
            Pos(origin.x() + x, origin.y() + 0.12, origin.z() + z)
    }
}

data class MobAbility(
    val id: String,
    val displayName: String,
    val shape: MobAttackShape,
    val maximumStartRange: Double,
    val damage: Double,
    val telegraphMillis: Long,
    val trackingMillis: Long,
    val recoveryMillis: Long,
    val cooldownMillis: Long,
    val weight: Int = 1,
    val minimumStartRange: Double = 0.0,
    val anchor: MobAbilityAnchor = MobAbilityAnchor.CASTER,
    val maximumHealthRatio: Double = 1.0,
) {
    init {
        require(id.isNotBlank() && maximumStartRange > 0.0 && damage > 0.0)
        require(telegraphMillis > 0L && trackingMillis in 0 until telegraphMillis)
        require(recoveryMillis >= 0L && cooldownMillis >= 0L && weight > 0)
        require(minimumStartRange in 0.0..maximumStartRange && maximumHealthRatio in 0.0..1.0)
    }
}

enum class MobAbilityPhase { TRACKING, LOCKED, RECOVERY }
enum class MobAbilityAnchor { CASTER, TARGET }

data class MobAbilityFrame(
    val ability: MobAbility,
    val origin: Pos,
    val facing: Vec,
    val phase: MobAbilityPhase,
    val startedAt: Long,
    val casterOrigin: Pos = origin,
)

sealed interface MobAbilityEvent {
    data class Started(val frame: MobAbilityFrame) : MobAbilityEvent
    data class Locked(val frame: MobAbilityFrame) : MobAbilityEvent
    data class Hit(val frame: MobAbilityFrame) : MobAbilityEvent
    data class Finished(val abilityId: String) : MobAbilityEvent
}

/** A small, clock-driven scheduler: no delayed callbacks survive cancellation or a dead mob. */
class MobAbilityManager(
    private val abilities: List<MobAbility>,
    private val random: Random = Random.Default,
    private val globalCooldownMillis: Long = 350L,
) {
    init {
        require(abilities.isNotEmpty() && abilities.map { it.id }.distinct().size == abilities.size)
        require(globalCooldownMillis >= 0L)
    }

    var current: MobAbilityFrame? = null
        private set
    private var nextSelectionAt = Long.MIN_VALUE
    private var previousAbilityId: String? = null
    private val readyAt = mutableMapOf<String, Long>()
    val isActive: Boolean get() = current != null

    fun tryStart(nowMillis: Long, origin: Pos, target: Point, healthRatio: Double = 1.0): MobAbilityEvent.Started? {
        if (current != null || nowMillis < nextSelectionAt) return null
        if (abs(origin.y() - target.y()) > 2.2) return null
        val distance = horizontalLength(target.sub(origin))
        val available = abilities.filter {
            distance in it.minimumStartRange..it.maximumStartRange && healthRatio <= it.maximumHealthRatio &&
                nowMillis >= readyAt.getOrDefault(it.id, Long.MIN_VALUE)
        }
        if (available.isEmpty()) return null
        val choices = available.filter { it.id != previousAbilityId }.ifEmpty { available }
        var roll = random.nextInt(choices.sumOf { it.weight })
        val selected = choices.first { roll -= it.weight; roll < 0 }
        val frame = MobAbilityFrame(
            selected, if (selected.anchor == MobAbilityAnchor.TARGET) Pos(target) else origin,
            normalizeHorizontal(target.sub(origin)), MobAbilityPhase.TRACKING, nowMillis, origin,
        )
        current = frame
        previousAbilityId = selected.id
        return MobAbilityEvent.Started(frame)
    }

    fun tick(nowMillis: Long, target: Point?): List<MobAbilityEvent> {
        var frame = current ?: return emptyList()
        val events = mutableListOf<MobAbilityEvent>()
        val elapsed = nowMillis - frame.startedAt
        if (frame.phase == MobAbilityPhase.TRACKING) {
            // A delayed tick locks the previously shown direction, never snaps to the new target at impact.
            if (elapsed >= frame.ability.trackingMillis) {
                frame = frame.copy(phase = MobAbilityPhase.LOCKED)
                events += MobAbilityEvent.Locked(frame)
            } else if (target != null) {
                frame = frame.copy(
                    origin = if (frame.ability.anchor == MobAbilityAnchor.TARGET) Pos(target) else frame.origin,
                    facing = normalizeHorizontal(target.sub(frame.casterOrigin)),
                )
            }
        }
        if (frame.phase == MobAbilityPhase.LOCKED && elapsed >= frame.ability.telegraphMillis) {
            events += MobAbilityEvent.Hit(frame)
            frame = frame.copy(phase = MobAbilityPhase.RECOVERY)
            readyAt[frame.ability.id] = nowMillis + frame.ability.cooldownMillis
        }
        if (frame.phase == MobAbilityPhase.RECOVERY &&
            elapsed >= frame.ability.telegraphMillis + frame.ability.recoveryMillis
        ) {
            current = null
            nextSelectionAt = nowMillis + globalCooldownMillis
            events += MobAbilityEvent.Finished(frame.ability.id)
        } else {
            current = frame
        }
        return events
    }

    fun cancel() { current = null }

    fun reset() {
        cancel()
        readyAt.clear()
        previousAbilityId = null
        nextSelectionAt = Long.MIN_VALUE
    }
}

internal fun horizontal(point: Point): Vec = Vec(point.x(), 0.0, point.z())
internal fun horizontalLength(point: Point): Double = sqrt(point.x() * point.x() + point.z() * point.z())
internal fun normalizeHorizontal(point: Point): Vec {
    val length = horizontalLength(point)
    return if (length > 1.0e-8) Vec(point.x() / length, 0.0, point.z() / length) else Vec(0.0, 0.0, 1.0)
}
