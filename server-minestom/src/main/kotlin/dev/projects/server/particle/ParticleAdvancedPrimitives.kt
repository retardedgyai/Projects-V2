package dev.projects.server.particle

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.particle.Particle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Places one style at a deterministic, evenly distributed subset of locations. */
class ParticleMulti(
    val locations: List<Point>,
    val count: Int = locations.size,
    val style: ParticleStyle = ParticleStyle(Particle.END_ROD),
    override val durationTicks: Int = 1,
) : ParticleEffect {
    init {
        require(count >= 0) { "count must be non-negative" }
        require(durationTicks >= 1) { "durationTicks must be at least one" }
    }

    fun points(): List<Point> {
        if (locations.isEmpty() || count == 0) return emptyList()
        if (count >= locations.size) return locations
        if (count == 1) return listOf(locations[locations.lastIndex / 2])
        return (0 until count).map { index ->
            locations[(index.toLong() * (locations.lastIndex) / (count - 1)).toInt()]
        }
    }

    override fun emit(tick: Int, sink: ParticleSink) {
        if (tick != 0) return
        points().forEachIndexed { index, point -> emitStyle(point, style, index, sink) }
    }
}

/** Samples a filled cylinder around an arbitrary axis. */
class ParticlePillar(
    val base: Point,
    val height: Double,
    val radius: Double = 0.0,
    val count: Int = max(1, ceil(max(abs(height), radius) * 8.0).toInt()),
    val axis: Vec = Vec(0.0, 1.0, 0.0),
    val seed: Long = 0L,
    val style: ParticleStyle = ParticleStyle(Particle.END_ROD),
    override val durationTicks: Int = 1,
) : ParticleEffect {
    init {
        require(radius >= 0.0 && count >= 0 && durationTicks >= 1)
    }

    fun points(): List<Point> {
        if (count == 0) return emptyList()
        val (forward, right, up) = basis(axis)
        return List(count) { index ->
            val random = Random(seed + index)
            val along = random.nextDouble() * height
            val radial = sqrt(random.nextDouble()) * radius
            val angle = random.nextDouble(0.0, 2.0 * PI)
            val offset = right.mul(cos(angle) * radial).add(up.mul(sin(angle) * radial))
            base.add(
                forward.x() * along + offset.x(),
                forward.y() * along + offset.y(),
                forward.z() * along + offset.z(),
            )
        }
    }

    override fun emit(tick: Int, sink: ParticleSink) {
        if (tick != 0) return
        points().forEachIndexed { index, point -> emitStyle(point, style, index, sink) }
    }
}

enum class RectPrismMode { EDGE, FACE, EDGES, FACES }

data class RectPrismSample(val point: Point, val progress: Double)

/** Emits a rectangular prism without assuming positive dimensions. */
class ParticleRectPrism(
    val cornerA: Point,
    val cornerB: Point,
    val mode: RectPrismMode = RectPrismMode.EDGE,
    val countPerMeter: Double = 4.0,
    val countPerMeterSquared: Double = 4.0,
    val style: ParticleStyle = ParticleStyle(Particle.END_ROD),
    val startColor: Int? = null,
    val endColor: Int? = startColor,
    override val durationTicks: Int = 1,
) : ParticleEffect {
    init {
        require(countPerMeter >= 0.0 && countPerMeterSquared >= 0.0 && durationTicks >= 1)
    }

    constructor(
        origin: Point,
        size: Vec,
        mode: RectPrismMode = RectPrismMode.EDGE,
        countPerMeter: Double = 4.0,
        countPerMeterSquared: Double = 4.0,
        style: ParticleStyle = ParticleStyle(Particle.END_ROD),
        startColor: Int? = null,
        endColor: Int? = startColor,
        durationTicks: Int = 1,
    ) : this(
        origin,
        origin.add(size.x(), size.y(), size.z()),
        mode,
        countPerMeter,
        countPerMeterSquared,
        style,
        startColor,
        endColor,
        durationTicks,
    )

    private val minX get() = min(cornerA.x(), cornerB.x())
    private val minY get() = min(cornerA.y(), cornerB.y())
    private val minZ get() = min(cornerA.z(), cornerB.z())
    private val maxX get() = max(cornerA.x(), cornerB.x())
    private val maxY get() = max(cornerA.y(), cornerB.y())
    private val maxZ get() = max(cornerA.z(), cornerB.z())

    fun samples(): List<RectPrismSample> {
        val result = linkedMapOf<String, RectPrismSample>()
        fun add(point: Point, progress: Double) {
            if (point.x().isFinite() && point.y().isFinite() && point.z().isFinite()) {
                val key = "${point.x()}:${point.y()}:${point.z()}"
                result.putIfAbsent(key, RectPrismSample(point, progress.coerceIn(0.0, 1.0)))
            }
        }
        val edges = mode == RectPrismMode.EDGE || mode == RectPrismMode.EDGES
        val faces = mode == RectPrismMode.FACE || mode == RectPrismMode.FACES
        if (edges) {
            val corners = listOf(
                Pos(minX, minY, minZ), Pos(maxX, minY, minZ), Pos(maxX, minY, maxZ), Pos(minX, minY, maxZ),
                Pos(minX, maxY, minZ), Pos(maxX, maxY, minZ), Pos(maxX, maxY, maxZ), Pos(minX, maxY, maxZ),
            )
            val edgePairs = listOf(
                0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4,
                0 to 4, 1 to 5, 2 to 6, 3 to 7,
            )
            edgePairs.forEach { (a, b) ->
                val start = corners[a]
                val end = corners[b]
                val length = distance(start, end)
                val segments = max(1, ceil(length * countPerMeter).toInt())
                for (index in 0..segments) {
                    val t = index.toDouble() / segments
                    add(interpolate(start, end, t), t)
                }
            }
        }
        if (faces) {
            val xSegments = max(1, ceil((maxX - minX) * countPerMeterSquared).toInt())
            val ySegments = max(1, ceil((maxY - minY) * countPerMeterSquared).toInt())
            val zSegments = max(1, ceil((maxZ - minZ) * countPerMeterSquared).toInt())
            fun grid(axis: Int, fixed: Double, first: Int, second: Int) {
                for (a in 0..first) for (b in 0..second) {
                    val u = a.toDouble() / first
                    val v = b.toDouble() / second
                    val point = when (axis) {
                        0 -> Pos(fixed, minY + (maxY - minY) * u, minZ + (maxZ - minZ) * v)
                        1 -> Pos(minX + (maxX - minX) * u, fixed, minZ + (maxZ - minZ) * v)
                        else -> Pos(minX + (maxX - minX) * u, minY + (maxY - minY) * v, fixed)
                    }
                    add(point, (u + v) / 2.0)
                }
            }
            grid(0, minX, ySegments, zSegments); grid(0, maxX, ySegments, zSegments)
            grid(1, minY, xSegments, zSegments); grid(1, maxY, xSegments, zSegments)
            grid(2, minZ, xSegments, ySegments); grid(2, maxZ, xSegments, ySegments)
        }
        return result.values.toList()
    }

    fun points(): List<Point> = samples().map { it.point }

    override fun emit(tick: Int, sink: ParticleSink) {
        val values = samples()
        for (index in frameRange(values.size, durationTicks, tick)) {
            val sample = values[index]
            val particleStyle = if (startColor != null) {
                style.copy(particle = dust(lerpColor(startColor, endColor ?: startColor, sample.progress)))
            } else style
            emitStyle(sample.point, particleStyle, index, sink)
        }
    }
}

class ParticleFlower(
    val center: Point,
    val petals: Int,
    val radius: Double,
    val sharp: Boolean = false,
    val planeNormal: Vec = Vec(0.0, 1.0, 0.0),
    val count: Int = max(16, petals.coerceAtLeast(1) * 16),
    val innerRadius: Double = 0.0,
    val startColor: Int? = null,
    val endColor: Int? = null,
    val style: ParticleStyle = ParticleStyle(Particle.END_ROD),
    override val durationTicks: Int = 1,
) : ParticleEffect {
    init {
        require(petals >= 1 && radius >= 0.0 && count >= 1 && durationTicks >= 1)
    }

    fun points(): List<Point> {
        val (_, right, up) = basis(planeNormal)
        return (0 until count).map { index ->
            val progress = if (count == 1) 0.0 else index.toDouble() / (count - 1)
            val angle = progress * 2.0 * PI
            val petal = cos(petals * angle)
            val shape = if (sharp) abs(petal) else 0.5 + 0.5 * petal
            val distance = innerRadius.coerceIn(0.0, radius) + (radius - innerRadius.coerceIn(0.0, radius)) * shape
            val radial = right.mul(cos(angle) * distance).add(up.mul(sin(angle) * distance))
            center.add(radial.x(), radial.y(), radial.z())
        }
    }

    override fun emit(tick: Int, sink: ParticleSink) {
        if (tick != 0) return
        points().forEachIndexed { index, point ->
            val radialDistance = kotlin.math.sqrt(
                (point.x() - center.x()) * (point.x() - center.x()) +
                    (point.y() - center.y()) * (point.y() - center.y()) +
                    (point.z() - center.z()) * (point.z() - center.z()),
            )
            val progress = (radialDistance / radius.coerceAtLeast(1.0e-9)).coerceIn(0.0, 1.0)
            val particleStyle = if (startColor != null) {
                style.copy(particle = dust(lerpColor(startColor, endColor ?: startColor, progress)))
            } else style
            emitStyle(point, particleStyle, index, sink)
        }
    }
}

private fun interpolate(a: Point, b: Point, t: Double): Point = a.add(
    (b.x() - a.x()) * t,
    (b.y() - a.y()) * t,
    (b.z() - a.z()) * t,
)

private fun distance(a: Point, b: Point): Double = kotlin.math.sqrt(
    (a.x() - b.x()) * (a.x() - b.x()) +
        (a.y() - b.y()) * (a.y() - b.y()) +
        (a.z() - b.z()) * (a.z() - b.z()),
)
