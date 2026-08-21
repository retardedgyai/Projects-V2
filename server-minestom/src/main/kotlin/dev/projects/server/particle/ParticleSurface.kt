package dev.projects.server.particle

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.Instance
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

data class ParticleRaycastHit(val position: Point, val normal: Vec, val distance: Double)

fun interface ParticleRaycast {
    fun trace(origin: Point, direction: Vec, maxDistance: Double): ParticleRaycastHit?
}

/** Bounded, allocation-light voxel ray query for VFX placement. It does not affect gameplay. */
class MinestomParticleRaycast(private val instance: Instance) : ParticleRaycast {
    override fun trace(origin: Point, direction: Vec, maxDistance: Double): ParticleRaycastHit? {
        val length = direction.length()
        val limit = maxDistance.coerceIn(0.0, 128.0)
        if (!length.isFinite() || length <= 1.0e-9 || !limit.isFinite() || limit <= 0.0) return null
        val ray = direction.mul(1.0 / length)
        val step = 0.05
        var distance = 0.0
        var previous = origin
        while (distance <= limit) {
            val point = origin.add(ray.x() * distance, ray.y() * distance, ray.z() * distance)
            val block = instance.getBlock(floor(point.x()).toInt(), floor(point.y()).toInt(), floor(point.z()).toInt())
            if (!block.isAir && block.collisionShape() != null) {
                val normal = faceNormal(ray)
                return ParticleRaycastHit(previous, normal, distance.coerceAtMost(limit))
            }
            previous = point
            distance += step
        }
        return null
    }
}

data class SurfaceProjection(val position: Point, val normal: Vec)

class SurfaceProjector(private val raycast: ParticleRaycast) {
    fun project(origin: Point, direction: Vec = Vec(0.0, -1.0, 0.0), maxDistance: Double = 16.0): SurfaceProjection? {
        val hit = raycast.trace(origin, direction, maxDistance) ?: return null
        if (!finite(hit.position) || !finite(hit.normal) || hit.normal.length() <= 1.0e-9) return null
        return SurfaceProjection(hit.position, hit.normal)
    }

    fun project(points: Iterable<Point>, direction: Vec = Vec(0.0, -1.0, 0.0), maxDistance: Double = 16.0): List<SurfaceProjection> =
        points.mapNotNull { project(it, direction, maxDistance) }
}

enum class ParticleCollisionMode { NONE, STOP_AT_WALL }

/** A beam helper with optional wall stopping; no-hit keeps the requested endpoint. */
class ParticleBeam(
    val start: ParticleAnchor,
    val direction: Vec,
    val length: Double,
    val raycast: ParticleRaycast? = null,
    val collisionMode: ParticleCollisionMode = ParticleCollisionMode.NONE,
    val style: ParticleStyle = ParticleStyle(net.minestom.server.particle.Particle.END_ROD),
    override val durationTicks: Int = 1,
) : ParticleEffect {
    init { require(length >= 0.0 && durationTicks >= 1) }

    override fun emit(tick: Int, sink: ParticleSink) {
        val sample = start.sample()
        if (tick !in 0 until durationTicks || !sample.valid) return
        val actualLength = if (collisionMode == ParticleCollisionMode.STOP_AT_WALL && raycast != null) {
            raycast.trace(sample.position, direction, length)?.distance ?: length
        } else length
        ParticleLine.fromDirection(sample.position, direction, max(0.0, actualLength), durationTicks = 1, style = style).emit(0, sink)
    }
}

private fun faceNormal(direction: Vec): Vec {
    val ax = abs(direction.x())
    val ay = abs(direction.y())
    val az = abs(direction.z())
    return when {
        ay >= ax && ay >= az -> Vec(0.0, -kotlin.math.sign(direction.y()), 0.0)
        ax >= az -> Vec(-kotlin.math.sign(direction.x()), 0.0, 0.0)
        else -> Vec(0.0, 0.0, -kotlin.math.sign(direction.z()))
    }
}

private fun finite(point: Point): Boolean = point.x().isFinite() && point.y().isFinite() && point.z().isFinite()
