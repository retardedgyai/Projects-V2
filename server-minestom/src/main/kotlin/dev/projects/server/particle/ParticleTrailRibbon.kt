package dev.projects.server.particle

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.particle.Particle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Moving-anchor history with bounded age/length and teleport reset. */
class ParticleTrail(
    val anchor: ParticleAnchor,
    val particle: Particle = Particle.END_ROD,
    val maxAgeTicks: Int = 12,
    val maxLength: Double = 8.0,
    val density: Double = 8.0,
    val teleportDistance: Double = 4.0,
    override val durationTicks: Int = 1,
    val styleAt: (Double) -> ParticleStyle = { ParticleStyle(particle) },
) : ParticleEffect {
    private data class Sample(val point: Point, var age: Int)
    private val samples = mutableListOf<Sample>()

    init {
        require(maxAgeTicks >= 1 && maxLength >= 0.0 && density >= 0.0 && teleportDistance >= 0.0 && durationTicks >= 1)
    }

    val history: List<Point> get() = samples.map { it.point }

    fun reset() = samples.clear()

    override fun emit(tick: Int, sink: ParticleSink) {
        if (tick !in 0 until durationTicks) return
        val current = anchor.position() ?: return
        if (samples.firstOrNull()?.point?.distance(current)?.let { it > teleportDistance } == true) samples.clear()
        samples.forEach { it.age++ }
        samples.add(0, Sample(current, 0))
        samples.removeIf { it.age > maxAgeTicks }
        trimLength()
        if (samples.size < 2) return
        var outputIndex = 0
        for (segment in 0 until samples.lastIndex) {
            val a = samples[segment].point
            val b = samples[segment + 1].point
            val length = a.distance(b)
            val count = max(1, kotlin.math.ceil(length * density).toInt())
            for (index in 0 until count) {
                val t = index.toDouble() / count
                val point = a.lerp(b, t).asVec()
                val progress = ((outputIndex + t) / max(1.0, estimatedSampleCount())).coerceIn(0.0, 1.0)
                emitStyle(point, styleAt(progress), outputIndex++, sink)
            }
        }
        emitStyle(samples.last().point, styleAt(1.0), outputIndex, sink)
    }

    private fun estimatedSampleCount(): Double = max(1.0, samples.zipWithNext().sumOf { (a, b) -> a.point.distance(b.point) * density } + 1.0)

    private fun trimLength() {
        var length = 0.0
        while (samples.size > 1) {
            length += samples[samples.lastIndex - 1].point.distance(samples.last().point)
            if (length <= maxLength) break
            samples.removeAt(samples.lastIndex)
        }
    }
}

class ParticleRibbon(
    val path: (Double) -> Point,
    val particle: Particle = Particle.END_ROD,
    val sampleCount: Int = 16,
    val lanes: Int = 1,
    val width: Curve<Double> = constantCurve(0.0),
    val twist: Curve<Double> = constantCurve(0.0),
    override val durationTicks: Int = 1,
    val styleAt: (progress: Double, laneProgress: Double) -> ParticleStyle = { _, _ -> ParticleStyle(particle) },
) : ParticleEffect {
    init { require(sampleCount >= 1 && lanes >= 1 && durationTicks >= 1) }

    fun points(): List<Point> = buildList {
        for (index in 0..sampleCount) {
            val t = index.toDouble() / sampleCount
            val center = path(t)
            val before = path((t - 1.0 / sampleCount).coerceIn(0.0, 1.0))
            val after = path((t + 1.0 / sampleCount).coerceIn(0.0, 1.0))
            val tangent = ribbonNormalize(Vec(after.x() - before.x(), after.y() - before.y(), after.z() - before.z()))
            val frame = ParticleTransform.fromDirection(center, tangent)
            val angle = twist.sample(t) * Math.PI / 180.0
            val right = frame.right.rotateAroundAxis(tangent, angle)
            for (lane in 0 until lanes) {
                val laneProgress = if (lanes == 1) 0.5 else lane.toDouble() / (lanes - 1)
                val offset = right.mul((laneProgress - 0.5) * width.sample(t))
                add(center.add(offset.x(), offset.y(), offset.z()))
            }
        }
    }

    override fun emit(tick: Int, sink: ParticleSink) {
        if (tick !in 0 until durationTicks) return
        val values = points()
        values.forEachIndexed { index, point ->
            val pathIndex = index / lanes
            val progress = pathIndex.toDouble() / sampleCount
            val laneProgress = if (lanes == 1) 0.5 else (index % lanes).toDouble() / (lanes - 1)
            emitStyle(point, styleAt(progress, laneProgress), index, sink)
        }
    }
}

private fun ribbonNormalize(vector: Vec): Vec = if (vector.length() <= 1.0e-9) Vec(0.0, 0.0, 1.0) else vector.mul(1.0 / vector.length())
