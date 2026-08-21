package dev.projects.server.particle

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.particle.Particle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Small, author-facing geometry helpers. They return effects and never perform gameplay checks. */
object ParticleUtils {
    fun line(start: Point, end: Point, density: Double = 8.0, style: ParticleStyle = ParticleStyle(Particle.END_ROD), durationTicks: Int = 1): ParticleEffect =
        ParticleLine(start, end, countPerMeter = density, durationTicks = durationTicks, style = style)

    fun ring(center: Point, radius: Double, normal: Vec = Vec(0.0, 1.0, 0.0), density: Double = 8.0, style: ParticleStyle = ParticleStyle(Particle.END_ROD)): ParticleEffect {
        val (_, right, up) = basis(normal)
        return ParticleCircle(center, radius, right, up, countPerMeter = density, style = style)
    }

    fun sphere(center: Point, radius: Double, count: Int = max(16, ceil(radius * radius * 24.0).toInt()), style: ParticleStyle = ParticleStyle(Particle.END_ROD), seed: Long = 0L): ParticleEffect =
        ParticleMulti(spherePoints(center, radius, count, seed), style = style)

    fun dome(center: Point, radius: Double, count: Int = max(16, ceil(radius * radius * 12.0).toInt()), normal: Vec = Vec(0.0, 1.0, 0.0), style: ParticleStyle = ParticleStyle(Particle.END_ROD), seed: Long = 0L): ParticleEffect {
        val (forward, right, up) = basis(normal)
        val points = List(count.coerceAtLeast(0)) { index ->
            val random = Random(seed + index)
            val azimuth = random.nextDouble(0.0, PI * 2.0)
            val elevation = random.nextDouble(0.0, PI / 2.0)
            val radial = sin(elevation) * radius
            val along = cos(elevation) * radius
            val offset = right.mul(cos(azimuth) * radial).add(up.mul(sin(azimuth) * radial)).add(forward.mul(along))
            center.add(offset.x(), offset.y(), offset.z())
        }
        return ParticleMulti(points, style = style)
    }

    fun boundingBoxEdges(min: Point, max: Point, density: Double = 4.0, style: ParticleStyle = ParticleStyle(Particle.END_ROD)): ParticleEffect =
        ParticleRectPrism(min, max, RectPrismMode.EDGE, countPerMeter = density, style = style)

    fun faceBox(min: Point, max: Point, density: Double = 4.0, style: ParticleStyle = ParticleStyle(Particle.END_ROD)): ParticleEffect =
        ParticleRectPrism(min, max, RectPrismMode.FACE, countPerMeterSquared = density, style = style)

    fun explodingRing(center: Point, radius: Double, count: Int = 24, durationTicks: Int = 6, style: ParticleStyle = ParticleStyle(Particle.END_ROD)): ParticleEffect = object : ParticleEffect {
        override val durationTicks: Int = durationTicks.coerceAtLeast(1)
        override fun emit(tick: Int, sink: ParticleSink) {
            val progress = (tick + 1).toDouble() / this.durationTicks
            ParticleCircle(center, radius * progress, countPerMeter = count / (2.0 * PI * radius.coerceAtLeast(1.0e-6)), style = style).emit(0, sink)
        }
    }

    fun explodingCone(origin: Point, direction: Vec, angleDegrees: Double, length: Double, count: Int = 32, durationTicks: Int = 6, style: ParticleStyle = ParticleStyle(Particle.END_ROD), seed: Long = 0L): ParticleEffect {
        val (forward, right, up) = basis(direction)
        val points = List(count.coerceAtLeast(0)) { index ->
            val random = Random(seed + index)
            val distance = random.nextDouble() * length.coerceAtLeast(0.0)
            val spread = kotlin.math.tan(Math.toRadians(angleDegrees.coerceAtLeast(0.0))) * distance * kotlin.math.sqrt(random.nextDouble())
            val angle = random.nextDouble(0.0, PI * 2.0)
            val offset = forward.mul(distance).add(right.mul(cos(angle) * spread)).add(up.mul(sin(angle) * spread))
            origin.add(offset.x(), offset.y(), offset.z())
        }
        return ParticleMulti(points, style = style, durationTicks = durationTicks)
    }

    fun circleDirectionalExplosion(center: Point, radius: Double, count: Int = 24, style: ParticleStyle = ParticleStyle(Particle.ELECTRIC_SPARK), durationTicks: Int = 1, seed: Long = 0L): ParticleEffect =
        ParticleExplosion(center, radius, sphere = false, particle = style.particle, count = count, speed = style.speed, spawnOffset = radius, seed = seed, durationTicks = durationTicks)

    fun animatedLineSlash(origin: Point, direction: Vec, length: Double, durationTicks: Int, style: ParticleStyle = ParticleStyle(Particle.END_ROD)): ParticleEffect =
        ParticleGeometry.drawParticleLineSlash(origin, direction, 0.0, length, 0.15, durationTicks) { _, _, _, _ -> style }

    fun cleaveArc(origin: Point, facing: Vec, radius: Double, startDegrees: Double, endDegrees: Double, rings: Int = 1, durationTicks: Int = 4, style: ParticleStyle = ParticleStyle(Particle.END_ROD)): ParticleEffect =
        ParticleGeometry.drawCleaveArc(origin, facing, radius, 0.0, startDegrees, endDegrees, rings, degreesPerTick = abs(endDegrees - startDegrees) / durationTicks.coerceAtLeast(1)) { _, _, _ -> style }

    fun rectangleTelegraph(center: Point, width: Double, depth: Double, durationTicks: Int = 1, style: ParticleStyle = ParticleStyle(Particle.END_ROD)): ParticleEffect =
        ParticleRectPrism(center.add(-width / 2.0, 0.0, -depth / 2.0), center.add(width / 2.0, 0.0, depth / 2.0), RectPrismMode.EDGE, countPerMeter = 5.0, style = style, durationTicks = durationTicks)

    fun shrinkingRectangleTelegraph(center: Point, width: Double, depth: Double, durationTicks: Int, style: ParticleStyle = ParticleStyle(Particle.END_ROD)): ParticleEffect = object : ParticleEffect {
        override val durationTicks: Int = durationTicks.coerceAtLeast(1)
        override fun emit(tick: Int, sink: ParticleSink) {
            val scale = 1.0 - (tick + 1).toDouble() / this.durationTicks
            rectangleTelegraph(center, width * scale, depth * scale, style = style).emit(0, sink)
        }
    }

    fun circleTelegraph(center: Point, radius: Double, durationTicks: Int = 1, style: ParticleStyle = ParticleStyle(Particle.END_ROD)): ParticleEffect =
        ParticleCircle(center, radius, countPerMeter = 8.0, style = style, durationTicks = durationTicks)

    fun flowerPattern(center: Point, petals: Int, radius: Double, normal: Vec = Vec(0.0, 1.0, 0.0), style: ParticleStyle = ParticleStyle(Particle.END_ROD)): ParticleEffect =
        ParticleFlower(center, petals, radius, planeNormal = normal, style = style)

    fun weirdCircle(center: Point, radius: Double, waves: Int = 5, count: Int = 64, normal: Vec = Vec(0.0, 1.0, 0.0), style: ParticleStyle = ParticleStyle(Particle.END_ROD)): ParticleEffect {
        val (_, right, up) = basis(normal)
        return ParticleParametric(
            positionAt = { t ->
                val angle = t * PI * 2.0
                val distance = radius * (0.75 + 0.25 * cos(waves * angle))
                val point = right.mul(cos(angle) * distance).add(up.mul(sin(angle) * distance))
                center.add(point.x(), point.y(), point.z())
            },
            sampleCount = count.coerceAtLeast(1),
            styleAt = { style },
        )
    }

    fun preciseCurve(positionAt: (Double) -> Point, samples: Int = 64, styleAt: (Double) -> ParticleStyle = { ParticleStyle(Particle.END_ROD) }): ParticleEffect =
        ParticleParametric(positionAt, sampleCount = samples.coerceAtLeast(1), styleAt = styleAt)

    fun integerCurve(points: List<Point>, style: ParticleStyle = ParticleStyle(Particle.END_ROD)): ParticleEffect =
        ParticleMulti(points, style = style)

    fun homingOrbTrajectory(start: Point, end: Point, control: Point? = null, durationTicks: Int = 8, style: ParticleStyle = ParticleStyle(Particle.END_ROD)): ParticleEffect =
        if (control == null) ParticleLine(start, end, countPerMeter = 8.0, durationTicks = durationTicks, style = style)
        else ParticleBezier(start, end, listOf(control), durationTicks = durationTicks, styleAt = { style })

    fun tendril(origin: Point, axis: Vec, length: Double, amplitude: Double, turns: Double, samples: Int = 48, style: ParticleStyle = ParticleStyle(Particle.END_ROD)): ParticleEffect {
        val (forward, right, up) = basis(axis)
        return ParticleParametric(
            positionAt = { t ->
                val angle = t * turns * PI * 2.0
                val offset = right.mul(cos(angle) * amplitude * t).add(up.mul(sin(angle) * amplitude * t)).add(forward.mul(length * t))
                origin.add(offset.x(), offset.y(), offset.z())
            },
            sampleCount = samples.coerceAtLeast(1),
            styleAt = { style },
        )
    }

    fun randomColor(random: Random = Random.Default): Int =
        (random.nextInt(256) shl 16) or (random.nextInt(256) shl 8) or random.nextInt(256)

    fun nearbyHueColor(color: Int, amount: Int = 24, random: Random = Random.Default): Int = {
        val shift = random.nextInt(-amount, amount + 1)
        fun channel(offset: Int) = ((color shr offset and 0xff) + shift).coerceIn(0, 255)
        (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }()

    private fun spherePoints(center: Point, radius: Double, count: Int, seed: Long): List<Point> = List(count.coerceAtLeast(0)) { index ->
        val random = Random(seed + index)
        val z = random.nextDouble(-1.0, 1.0)
        val angle = random.nextDouble(0.0, PI * 2.0)
        val radial = kotlin.math.sqrt(1.0 - z * z) * radius
        center.add(cos(angle) * radial, z * radius, sin(angle) * radial)
    }
}
