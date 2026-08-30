package dev.projects.server.experiment.swarm.combat

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

data class CombatPoint(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    operator fun plus(other: CombatPoint) = CombatPoint(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: CombatPoint) = CombatPoint(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Double) = CombatPoint(x * scale, y * scale, z * scale)

    fun dot(other: CombatPoint): Double = x * other.x + y * other.y + z * other.z
    fun lengthSquared(): Double = dot(this)
    fun length(): Double = sqrt(lengthSquared())

    fun normalizedOrNull(): CombatPoint? {
        val length = length()
        return if (length <= EPSILON) null else this * (1.0 / length)
    }
}

data class CombatBounds(
    val min: CombatPoint,
    val max: CombatPoint,
) {
    init {
        require(min.x <= max.x && min.y <= max.y && min.z <= max.z) { "Invalid combat bounds" }
    }

    val center: CombatPoint
        get() = CombatPoint(
            (min.x + max.x) / 2.0,
            (min.y + max.y) / 2.0,
            (min.z + max.z) / 2.0,
        )

    fun closestPoint(point: CombatPoint): CombatPoint = CombatPoint(
        point.x.coerceIn(min.x, max.x),
        point.y.coerceIn(min.y, max.y),
        point.z.coerceIn(min.z, max.z),
    )

    fun distanceTo(point: CombatPoint): Double = (closestPoint(point) - point).length()
}

object CombatGeometry {
    fun isWithinFacing(
        eye: CombatPoint,
        lookDirection: CombatPoint,
        target: CombatBounds,
        minimumDot: Double,
    ): Boolean {
        require(minimumDot in -1.0..1.0)
        val look = lookDirection.normalizedOrNull() ?: return false
        val towardTarget = (target.closestPoint(eye) - eye).normalizedOrNull() ?: return true
        return look.dot(towardTarget) >= minimumDot
    }

    fun hasSampledLineOfSight(
        eye: CombatPoint,
        target: CombatBounds,
        blockers: Collection<CombatBounds>,
    ): Boolean {
        val center = target.center
        val samples = listOf(
            target.closestPoint(eye),
            center,
            CombatPoint(center.x, target.max.y, center.z),
        ).distinct()

        return samples.any { sample ->
            blockers.none { blocker -> segmentIntersectsBounds(eye, sample, blocker) }
        }
    }

    fun segmentIntersectsBounds(
        start: CombatPoint,
        end: CombatPoint,
        bounds: CombatBounds,
    ): Boolean {
        val direction = end - start
        var minimumTime = 0.0
        var maximumTime = 1.0

        fun clip(origin: Double, delta: Double, minimum: Double, maximum: Double): Boolean {
            if (abs(delta) <= EPSILON) {
                return origin in minimum..maximum
            }
            val first = (minimum - origin) / delta
            val second = (maximum - origin) / delta
            val entry = minOf(first, second)
            val exit = maxOf(first, second)
            minimumTime = maxOf(minimumTime, entry)
            maximumTime = minOf(maximumTime, exit)
            return minimumTime <= maximumTime
        }

        return clip(start.x, direction.x, bounds.min.x, bounds.max.x) &&
            clip(start.y, direction.y, bounds.min.y, bounds.max.y) &&
            clip(start.z, direction.z, bounds.min.z, bounds.max.z)
    }

    fun intersectsSweepArc(
        origin: CombatPoint,
        forward: CombatPoint,
        radius: Double,
        halfAngleDegrees: Double,
        target: CombatBounds,
    ): Boolean {
        require(radius > 0.0)
        require(halfAngleDegrees in 0.0..180.0)
        val horizontalForward = CombatPoint(forward.x, 0.0, forward.z).normalizedOrNull() ?: return false
        val points = listOf(
            target.center,
            CombatPoint(target.min.x, target.center.y, target.min.z),
            CombatPoint(target.min.x, target.center.y, target.max.z),
            CombatPoint(target.max.x, target.center.y, target.min.z),
            CombatPoint(target.max.x, target.center.y, target.max.z),
        )
        val minimumDot = cos(Math.toRadians(halfAngleDegrees))

        return points.any { point ->
            val offset = CombatPoint(point.x - origin.x, 0.0, point.z - origin.z)
            val distance = offset.length()
            distance <= radius && (distance <= EPSILON || horizontalForward.dot(offset * (1.0 / distance)) >= minimumDot)
        }
    }

    fun intersectsChargeCorridor(
        origin: CombatPoint,
        forward: CombatPoint,
        length: Double,
        halfWidth: Double,
        height: Double,
        target: CombatBounds,
    ): Boolean {
        require(length > 0.0 && halfWidth >= 0.0 && height > 0.0)
        if (target.max.y < origin.y || target.min.y > origin.y + height) return false

        val direction = CombatPoint(forward.x, 0.0, forward.z).normalizedOrNull() ?: return false
        val right = CombatPoint(-direction.z, 0.0, direction.x)
        val centerOffset = CombatPoint(target.center.x - origin.x, 0.0, target.center.z - origin.z)
        val halfX = (target.max.x - target.min.x) / 2.0
        val halfZ = (target.max.z - target.min.z) / 2.0
        val longitudinalExtent = abs(direction.x) * halfX + abs(direction.z) * halfZ
        val lateralExtent = abs(right.x) * halfX + abs(right.z) * halfZ
        val longitudinal = centerOffset.dot(direction)
        val lateral = abs(centerOffset.dot(right))

        return longitudinal + longitudinalExtent >= 0.0 &&
            longitudinal - longitudinalExtent <= length &&
            lateral <= halfWidth + lateralExtent
    }
}

private const val EPSILON = 1.0e-9
