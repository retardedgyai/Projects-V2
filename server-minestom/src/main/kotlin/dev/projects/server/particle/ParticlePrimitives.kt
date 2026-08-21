package dev.projects.server.particle

import net.kyori.adventure.text.format.TextColor
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class ParticleCategory {
    OWN_ACTIVE,
    OTHER_ACTIVE,
    ENEMY,
    BOSS,
    FULL,
}

data class ParticleStyle(
    val particle: Particle,
    val count: Int = 1,
    val offset: Vec = Vec.ZERO,
    val speed: Float = 0f,
    val densityMultiplier: Double = 1.0,
    val category: ParticleCategory = ParticleCategory.FULL,
) {
    init {
        require(count >= 0) { "count must be non-negative" }
        require(densityMultiplier >= 0.0) { "densityMultiplier must be non-negative" }
    }
}

data class ParticleSpawn(
    val particle: Particle,
    val position: Point,
    val count: Int = 1,
    val offset: Vec = Vec.ZERO,
    val speed: Float = 0f,
    val category: ParticleCategory = ParticleCategory.FULL,
) {
    init {
        require(count >= 0) { "count must be non-negative" }
    }

    companion object {
        fun from(position: Point, style: ParticleStyle): ParticleSpawn = ParticleSpawn(
            particle = style.particle,
            position = position,
            count = style.count,
            offset = style.offset,
            speed = style.speed,
            category = style.category,
        )
    }
}

fun interface ParticleSink {
    fun spawn(spawn: ParticleSpawn)
}

class PlayerParticleSink(private val player: Player) : ParticleSink {
    fun belongsTo(candidate: Player): Boolean = player === candidate

    override fun spawn(spawn: ParticleSpawn) {
        if (!player.isOnline) return
        player.sendPacket(
            ParticlePacket(
                spawn.particle,
                spawn.position.x(),
                spawn.position.y(),
                spawn.position.z(),
                spawn.offset.x().toFloat(),
                spawn.offset.y().toFloat(),
                spawn.offset.z().toFloat(),
                spawn.speed,
                spawn.count,
            ),
        )
    }
}

class RecordingParticleSink : ParticleSink {
    val spawns = mutableListOf<ParticleSpawn>()

    override fun spawn(spawn: ParticleSpawn) {
        spawns += spawn
    }

    fun clear() = spawns.clear()
}

interface ParticleEffect {
    val durationTicks: Int

    fun emit(tick: Int, sink: ParticleSink)
}

private fun frameRange(total: Int, duration: Int, tick: Int): IntRange {
    if (total <= 0 || tick !in 0 until duration) return IntRange.EMPTY
    val start = total * tick / duration
    val end = total * (tick + 1) / duration - 1
    return if (end >= start) start..end else IntRange.EMPTY
}

private fun scaledCount(count: Int, multiplier: Double, index: Int): Int {
    if (count <= 0 || multiplier <= 0.0) return 0
    val exact = count * multiplier
    val base = floor(exact).toInt()
    val remainder = exact - base
    return base + if (remainder > 0.0 && ((index * 1103515245L + 12345L) and 0x7fffffff) / 2147483648.0 < remainder) 1 else 0
}

internal fun emitStyle(point: Point, style: ParticleStyle, index: Int, sink: ParticleSink) {
    val count = scaledCount(style.count, style.densityMultiplier, index)
    if (count > 0) sink.spawn(ParticleSpawn.from(point, style.copy(count = count)))
}

class PartialParticle(
    val particle: Particle,
    val origin: Point,
    val count: Int = 1,
    val offset: Vec = Vec.ZERO,
    val speed: Float = 0f,
    val minimumCount: Int = 0,
    val densityMultiplier: Double = 1.0,
    override val durationTicks: Int = 1,
    val category: ParticleCategory = ParticleCategory.FULL,
) : ParticleEffect {
    init {
        require(count >= 0 && minimumCount >= 0) { "particle counts must be non-negative" }
        require(durationTicks >= 1) { "durationTicks must be at least one" }
    }

    override fun emit(tick: Int, sink: ParticleSink) {
        if (tick != 0) return
        val scaled = max(minimumCount, scaledCount(count, densityMultiplier, 0))
        if (scaled > 0) sink.spawn(ParticleSpawn(particle, origin, scaled, offset, speed, category))
    }
}

class ParticleLine(
    val start: Point,
    val end: Point,
    val particle: Particle = Particle.END_ROD,
    val countPerMeter: Double = 8.0,
    val minimumCountPerMeter: Double = 0.0,
    val includeStart: Boolean = true,
    val includeEnd: Boolean = true,
    val sampleOffset: Double = 0.0,
    override val durationTicks: Int = 1,
    val style: ParticleStyle = ParticleStyle(particle),
) : ParticleEffect {
    init {
        require(countPerMeter >= 0.0 && minimumCountPerMeter >= 0.0)
        require(durationTicks >= 1)
    }

    val length: Double get() = start.distance(end)

    fun points(): List<Point> {
        val length = length
        val count = max(1, ceil(max(countPerMeter, minimumCountPerMeter) * length).toInt())
        val result = mutableListOf<Point>()
        for (index in 0..count) {
            val progress = index.toDouble() / count
            if ((index == 0 && !includeStart) || (index == count && !includeEnd)) continue
            val sampledProgress = if (index == 0 || index == count) progress else {
                (progress + sampleOffset / count).coerceIn(0.0, 1.0)
            }
            result += start.add(
                (end.x() - start.x()) * sampledProgress,
                (end.y() - start.y()) * sampledProgress,
                (end.z() - start.z()) * sampledProgress,
            )
        }
        if (result.isEmpty() && length == 0.0 && includeStart) result += start
        return result
    }

    override fun emit(tick: Int, sink: ParticleSink) {
        val points = points()
        for (index in frameRange(points.size, durationTicks, tick)) {
            emitStyle(points[index], style, index, sink)
        }
    }

    companion object {
        fun fromDirection(
            start: Point,
            direction: Vec,
            length: Double,
            particle: Particle = Particle.END_ROD,
            countPerMeter: Double = 8.0,
            durationTicks: Int = 1,
            style: ParticleStyle = ParticleStyle(particle),
        ): ParticleLine {
            val forward = normalize(direction)
            return ParticleLine(
                start = start,
                end = start.add(forward.x() * length, forward.y() * length, forward.z() * length),
                particle = particle,
                countPerMeter = countPerMeter,
                durationTicks = durationTicks,
                style = style,
            )
        }
    }
}

class ParticleCircle(
    val center: Point,
    val radius: Double,
    val axis1: Vec = Vec(1.0, 0.0, 0.0),
    val axis2: Vec = Vec(0.0, 1.0, 0.0),
    val filled: Boolean = false,
    val innerRadiusFactor: Double = 0.0,
    val startDegrees: Double = 0.0,
    val endDegrees: Double = 360.0,
    val countPerMeter: Double = 8.0,
    val includeStart: Boolean = true,
    val includeEnd: Boolean = false,
    val sampleOffset: Double = 0.0,
    override val durationTicks: Int = 1,
    val style: ParticleStyle = ParticleStyle(Particle.END_ROD),
) : ParticleEffect {
    init {
        require(radius >= 0.0 && countPerMeter >= 0.0)
        require(durationTicks >= 1)
    }

    private fun basis(): Pair<Vec, Vec> {
        val first = normalize(axis1)
        val projected = axis2.sub(first.mul(axis2.dot(first)))
        val second = if (projected.length() > 1.0e-9) normalize(projected) else perpendicular(first)
        return first to second
    }

    fun points(): List<Point> {
        val span = endDegrees - startDegrees
        val circumference = radius * Math.toRadians(abs(span))
        val segments = max(1, ceil(circumference * countPerMeter).toInt())
        val rings = if (filled) max(1, ceil((1.0 - innerRadiusFactor.coerceIn(0.0, 1.0)) * max(1.0, radius * countPerMeter / 2.0)).toInt()) else 1
        val (first, second) = basis()
        return buildList {
            for (ringIndex in 0 until rings) {
                val ringFactor = if (rings == 1) 1.0 else innerRadiusFactor.coerceIn(0.0, 1.0) +
                    (1.0 - innerRadiusFactor.coerceIn(0.0, 1.0)) * ringIndex / (rings - 1)
                for (index in 0..segments) {
                    if (!includeStart && index == 0) continue
                    if (!includeEnd && index == segments) continue
                    val progress = index.toDouble() / segments
                    val angle = Math.toRadians(startDegrees + span * progress) + sampleOffset
                    val radial = first.mul(cleanTrig(cos(angle)) * radius * ringFactor).add(second.mul(cleanTrig(sin(angle)) * radius * ringFactor))
                    add(center.add(radial.x(), radial.y(), radial.z()))
                }
            }
        }
    }

    override fun emit(tick: Int, sink: ParticleSink) {
        val points = points()
        for (index in frameRange(points.size, durationTicks, tick)) emitStyle(points[index], style, index, sink)
    }
}

class ParticleParametric(
    val positionAt: (Double) -> Point,
    val particle: Particle = Particle.END_ROD,
    val sampleCount: Int = 16,
    val includeStart: Boolean = true,
    val includeEnd: Boolean = true,
    override val durationTicks: Int = 1,
    val styleAt: (Double) -> ParticleStyle = { ParticleStyle(particle) },
) : ParticleEffect {
    init {
        require(sampleCount >= 1 && durationTicks >= 1)
    }

    fun points(): List<Pair<Point, Double>> = buildList {
        for (index in 0..sampleCount) {
            if (index == 0 && !includeStart) continue
            if (index == sampleCount && !includeEnd) continue
            val progress = index.toDouble() / sampleCount
            add(positionAt(progress) to progress)
        }
    }

    override fun emit(tick: Int, sink: ParticleSink) {
        val points = points()
        for (index in frameRange(points.size, durationTicks, tick)) {
            val (point, progress) = points[index]
            emitStyle(point, styleAt(progress), index, sink)
        }
    }
}

class ParticleBezier(
    val start: Point,
    val end: Point,
    val controlPoints: List<Point> = emptyList(),
    val particle: Particle = Particle.END_ROD,
    val sampleCount: Int = 16,
    val includeStart: Boolean = true,
    val includeEnd: Boolean = true,
    override val durationTicks: Int = 1,
    val styleAt: (Double) -> ParticleStyle = { ParticleStyle(particle) },
) : ParticleEffect {
    init {
        require(sampleCount >= 1 && durationTicks >= 1)
    }

    fun pointAt(t: Double): Point {
        val values = buildList {
            add(start)
            addAll(controlPoints)
            add(end)
        }.toMutableList()
        repeat(values.size - 1) {
            for (index in 0 until values.lastIndex) {
                val a = values[index]
                val b = values[index + 1]
                values[index] = a.add((b.x() - a.x()) * t, (b.y() - a.y()) * t, (b.z() - a.z()) * t)
            }
            values.removeAt(values.lastIndex)
        }
        return values.single()
    }

    override fun emit(tick: Int, sink: ParticleSink) {
        val effect = ParticleParametric(::pointAt, particle, sampleCount, includeStart, includeEnd, durationTicks, styleAt)
        effect.emit(tick, sink)
    }
}

class ParticleSpiral(
    val origin: Point,
    val axis: Vec,
    val radius: Double,
    val curveAngle: Double,
    val curves: Double,
    val angleOffset: Double = 0.0,
    override val durationTicks: Int = 1,
    val reversed: Boolean = false,
    val sampleCount: Int = max(16, ceil(curves * 24.0).toInt()),
    val style: ParticleStyle = ParticleStyle(Particle.END_ROD),
) : ParticleEffect {
    init {
        require(radius >= 0.0 && curves >= 0.0 && durationTicks >= 1)
    }

    fun points(): List<Point> {
        val (forward, right, up) = basis(axis)
        return (0..sampleCount).map { index ->
            val t = index.toDouble() / sampleCount
            val progress = if (reversed) 1.0 - t else t
            val angle = angleOffset + curveAngle * curves * progress
            val radial = right.mul(cos(angle) * radius * progress).add(up.mul(sin(angle) * radius * progress))
            origin.add(forward.x() * progress + radial.x(), forward.y() * progress + radial.y(), forward.z() * progress + radial.z())
        }
    }

    override fun emit(tick: Int, sink: ParticleSink) {
        val points = points()
        for (index in frameRange(points.size, durationTicks, tick)) emitStyle(points[index], style, index, sink)
    }
}

class ParticleExplosion(
    val center: Point,
    val radius: Double = 1.0,
    val sphere: Boolean = false,
    val particle: Particle = Particle.FLAME,
    val count: Int = 24,
    val speed: Float = 0.35f,
    val speedVariance: Float = 0f,
    val spawnOffset: Double = 0.0,
    val seed: Long = 0L,
    override val durationTicks: Int = 1,
) : ParticleEffect {
    private val directions = List(max(0, count)) { index ->
        val random = Random(seed + index)
        val angle = random.nextDouble(0.0, 2.0 * PI)
        val vector = if (sphere) {
            val z = random.nextDouble(-1.0, 1.0)
            val radial = sqrt(max(0.0, 1.0 - z * z))
            Vec(cos(angle) * radial, z, sin(angle) * radial)
        } else {
            Vec(cos(angle), 0.0, sin(angle))
        }
        normalize(vector)
    }

    init {
        require(radius >= 0.0 && count >= 0 && speed >= 0f && speedVariance >= 0f && durationTicks >= 1)
    }

    override fun emit(tick: Int, sink: ParticleSink) {
        if (tick != 0) return
        directions.forEachIndexed { index, direction ->
            val variance = if (speedVariance == 0f) 0f else {
                Random(seed + index + 91).nextDouble(-speedVariance.toDouble(), speedVariance.toDouble()).toFloat()
            }
            val actualSpeed = speed + variance
            val offsetDistance = if (spawnOffset == 0.0) radius else spawnOffset
            sink.spawn(ParticleSpawn(particle, center.add(direction.x() * offsetDistance, direction.y() * offsetDistance, direction.z() * offsetDistance), 1, Vec.ZERO, actualSpeed))
        }
    }
}

class ParticleLightning(
    val start: Point,
    val end: Point,
    val particle: Particle = Particle.ELECTRIC_SPARK,
    val hops: Int = 8,
    val hopVariance: Double = 0.35,
    val density: Int = 1,
    val seed: Long = 0L,
    override val durationTicks: Int = 1,
    val style: ParticleStyle = ParticleStyle(particle),
) : ParticleEffect {
    private val points: List<Point> = buildList {
        val forward = normalize(Vec(end.x() - start.x(), end.y() - start.y(), end.z() - start.z()))
        val (_, right, up) = basis(forward)
        add(start)
        for (index in 1 until hops.coerceAtLeast(1)) {
            val progress = index.toDouble() / hops.coerceAtLeast(1)
            val random = Random(seed + index)
            val jitter = Vec(
                right.x() * random.nextDouble(-hopVariance, hopVariance) + up.x() * random.nextDouble(-hopVariance, hopVariance),
                right.y() * random.nextDouble(-hopVariance, hopVariance) + up.y() * random.nextDouble(-hopVariance, hopVariance),
                right.z() * random.nextDouble(-hopVariance, hopVariance) + up.z() * random.nextDouble(-hopVariance, hopVariance),
            )
            add(start.add((end.x() - start.x()) * progress + jitter.x(), (end.y() - start.y()) * progress + jitter.y(), (end.z() - start.z()) * progress + jitter.z()))
        }
        add(end)
    }

    init {
        require(hops >= 1 && density >= 1 && hopVariance >= 0.0 && durationTicks >= 1)
    }

    fun points(): List<Point> = points

    override fun emit(tick: Int, sink: ParticleSink) {
        val frame = frameRange((points.size - 1) * density + 1, durationTicks, tick)
        for (sample in frame) {
            if (sample == (points.size - 1) * density) {
                emitStyle(points.last(), style, sample, sink)
                continue
            }
            val segment = min(points.lastIndex - 1, sample / density)
            val progress = (sample % density).toDouble() / density
            val a = points[segment]
            val b = points[segment + 1]
            val point = a.add((b.x() - a.x()) * progress, (b.y() - a.y()) * progress, (b.z() - a.z()) * progress)
            emitStyle(point, style, sample, sink)
        }
    }

    companion object {
        fun fromDirection(
            origin: Point,
            direction: Vec,
            length: Double,
            particle: Particle = Particle.ELECTRIC_SPARK,
            hops: Int = 8,
            hopVariance: Double = 0.35,
            density: Int = 1,
            seed: Long = 0L,
            durationTicks: Int = 1,
            style: ParticleStyle = ParticleStyle(particle),
        ): ParticleLightning {
            val forward = normalize(direction)
            return ParticleLightning(
                origin,
                origin.add(forward.x() * length, forward.y() * length, forward.z() * length),
                particle,
                hops,
                hopVariance,
                density,
                seed,
                durationTicks,
                style,
            )
        }
    }
}

class ParticleBatch(private val effects: List<ParticleEffect>) : ParticleEffect {
    override val durationTicks: Int = effects.maxOfOrNull { it.durationTicks } ?: 1

    override fun emit(tick: Int, sink: ParticleSink) {
        effects.forEach { if (tick < it.durationTicks) it.emit(tick, sink) }
    }

    companion object {
        fun of(vararg effects: ParticleEffect) = ParticleBatch(effects.toList())
    }
}

class ParticlePeriodic(
    private val effect: ParticleEffect,
    private val period: Int,
    private val phase: Int = 0,
) : ParticleEffect {
    init {
        require(period >= 1)
    }

    override val durationTicks: Int = effect.durationTicks

    override fun emit(tick: Int, sink: ParticleSink) {
        if ((tick + phase) % period == 0) effect.emit(tick, sink)
    }
}

fun dust(red: Int, green: Int, blue: Int, scale: Float = 1f): Particle =
    Particle.DUST.withProperties(TextColor.color(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255)), scale)

fun dust(color: Int, scale: Float = 1f): Particle = dust(color shr 16 and 0xff, color shr 8 and 0xff, color and 0xff, scale)

fun dustTransition(start: Int, end: Int, scale: Float = 1f): Particle =
    Particle.DUST_COLOR_TRANSITION.withProperties(
        TextColor.color(start shr 16 and 0xff, start shr 8 and 0xff, start and 0xff),
        TextColor.color(end shr 16 and 0xff, end shr 8 and 0xff, end and 0xff),
        scale,
    )

fun lerpColor(a: Int, b: Int, t: Double): Int {
    val progress = t.coerceIn(0.0, 1.0)
    fun channel(shift: Int): Int = (a shr shift and 0xff) + (((b shr shift and 0xff) - (a shr shift and 0xff)) * progress).toInt()
    return channel(16) shl 16 or (channel(8) shl 8) or channel(0)
}

internal fun normalize(vector: Vec): Vec {
    val length = vector.length()
    return if (length <= 1.0e-9) Vec(0.0, 1.0, 0.0) else vector.mul(1.0 / length)
}

internal fun perpendicular(forward: Vec): Vec {
    val reference = if (kotlin.math.abs(forward.y()) < 0.9) Vec(0.0, 1.0, 0.0) else Vec(1.0, 0.0, 0.0)
    return normalize(Vec(
        forward.y() * reference.z() - forward.z() * reference.y(),
        forward.z() * reference.x() - forward.x() * reference.z(),
        forward.x() * reference.y() - forward.y() * reference.x(),
    ))
}

internal fun basis(forwardInput: Vec): Triple<Vec, Vec, Vec> {
    val forward = normalize(forwardInput)
    val right = perpendicular(forward)
    val up = normalize(Vec(
        forward.y() * right.z() - forward.z() * right.y(),
        forward.z() * right.x() - forward.x() * right.z(),
        forward.x() * right.y() - forward.y() * right.x(),
    ))
    return Triple(forward, right, up)
}

private fun cleanTrig(value: Double): Double = if (abs(value) < 1.0e-12) 0.0 else value
