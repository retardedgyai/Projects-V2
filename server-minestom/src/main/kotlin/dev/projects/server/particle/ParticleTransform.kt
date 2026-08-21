package dev.projects.server.particle

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import kotlin.math.abs

/** A stable orthonormal frame used by particle authors instead of hand-written yaw math. */
data class ParticleTransform(
    val origin: Point,
    val forward: Vec,
    val right: Vec,
    val up: Vec,
    val uniformScale: Double = 1.0,
) {
    init {
        require(listOf(forward, right, up).all { isFinite(it) })
        require(abs(forward.length() - 1.0) < 1.0e-6)
        require(abs(right.length() - 1.0) < 1.0e-6)
        require(abs(up.length() - 1.0) < 1.0e-6)
        require(uniformScale.isFinite() && uniformScale >= 0.0)
    }

    fun localPoint(point: Point): Point = origin.add(
        right.x() * point.x() * uniformScale + up.x() * point.y() * uniformScale + forward.x() * point.z() * uniformScale,
        right.y() * point.x() * uniformScale + up.y() * point.y() * uniformScale + forward.y() * point.z() * uniformScale,
        right.z() * point.x() * uniformScale + up.z() * point.y() * uniformScale + forward.z() * point.z() * uniformScale,
    )

    fun localToWorldPoint(point: Point): Point = localPoint(point)

    fun localDirection(direction: Vec): Vec = Vec(
        right.x() * direction.x() + up.x() * direction.y() + forward.x() * direction.z(),
        right.y() * direction.x() + up.y() * direction.y() + forward.y() * direction.z(),
        right.z() * direction.x() + up.z() * direction.y() + forward.z() * direction.z(),
    )

    fun localToWorldDirection(direction: Vec): Vec = localDirection(direction)

    fun worldPoint(point: Point): Point = Vec(point.x(), point.y(), point.z()).sub(origin).let { delta ->
        Vec(
            delta.dot(right) / uniformScale.coerceAtLeast(1.0e-9),
            delta.dot(up) / uniformScale.coerceAtLeast(1.0e-9),
            delta.dot(forward) / uniformScale.coerceAtLeast(1.0e-9),
        )
    }

    fun worldToLocalPoint(point: Point): Point = worldPoint(point)

    fun worldDirection(direction: Vec): Vec = Vec(
        direction.dot(right),
        direction.dot(up),
        direction.dot(forward),
    )

    fun worldToLocalDirection(direction: Vec): Vec = worldDirection(direction)

    fun translate(offset: Vec): ParticleTransform = copy(origin = origin.add(offset.x(), offset.y(), offset.z()))

    /** Rotations are author-facing degrees, applied in local yaw, pitch, roll order. */
    fun rotate(yaw: Double = 0.0, pitch: Double = 0.0, roll: Double = 0.0): ParticleTransform {
        fun rotateLocal(vector: Vec): Vec {
            var result = vector.rotateAroundY(Math.toRadians(yaw))
            result = result.rotateAroundX(Math.toRadians(pitch))
            return result.rotateAroundZ(Math.toRadians(roll))
        }
        return fromDirection(origin, rotateLocal(forward), rotateLocal(up)).copy(uniformScale = uniformScale)
    }

    fun scale(factor: Double): ParticleTransform {
        require(factor.isFinite() && factor >= 0.0)
        return copy(uniformScale = factor)
    }

    companion object {
        fun fromDirection(origin: Point, direction: Vec, upHint: Vec = Vec(0.0, 1.0, 0.0)): ParticleTransform {
            val forward = safeNormalize(direction, Vec(0.0, 0.0, 1.0))
            val projectedUp = upHint.sub(forward.mul(upHint.dot(forward)))
            val up = safeNormalize(projectedUp, transformPerpendicular(forward))
            val right = safeNormalize(forward.cross(up), transformPerpendicular(forward))
            val correctedUp = safeNormalize(right.cross(forward), up)
            return ParticleTransform(origin, forward, right, correctedUp)
        }

        fun lookAt(origin: Point, target: Point, upHint: Vec = Vec(0.0, 1.0, 0.0)): ParticleTransform =
            fromDirection(origin, Vec(target.x() - origin.x(), target.y() - origin.y(), target.z() - origin.z()), upHint)
    }
}

private fun safeNormalize(vector: Vec, fallback: Vec): Vec {
    val length = vector.length()
    return if (length.isFinite() && length > 1.0e-9) vector.mul(1.0 / length) else fallback
}

private fun transformPerpendicular(forward: Vec): Vec {
    val reference = if (abs(forward.y()) < 0.9) Vec(0.0, 1.0, 0.0) else Vec(1.0, 0.0, 0.0)
    return safeNormalize(forward.cross(reference), Vec(1.0, 0.0, 0.0))
}

private fun isFinite(vector: Vec): Boolean = vector.x().isFinite() && vector.y().isFinite() && vector.z().isFinite()
