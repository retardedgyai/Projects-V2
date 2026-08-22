package dev.projects.server.particle

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.particle.Particle
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.pow
import kotlin.random.Random

enum class EmitterRate { INSTANT, STEADY, BURST }
enum class SpawnShape { POINT, BOX, SPHERE, DISC, CONE }
typealias ParticleEmitterRate = EmitterRate
typealias ParticleSpawnShape = SpawnShape

/** A deliberately thin runtime emitter. It controls spawn packets, not client particle lifetime. */
class ParticleEmitter(
    val anchor: ParticleAnchor,
    val particle: Particle = Particle.END_ROD,
    val rate: EmitterRate = EmitterRate.STEADY,
    val shape: SpawnShape = SpawnShape.POINT,
    override val durationTicks: Int = 1,
    val particlesPerTick: Int = 1,
    val burstCount: Int = particlesPerTick,
    val dimensions: Vec = Vec(1.0, 1.0, 1.0),
    val radius: Double = 1.0,
    val coneAngleDegrees: Double = 25.0,
    val initialDirection: Vec? = null,
    val speedRange: ClosedFloatingPointRange<Float> = 0f..0f,
    val seed: Long = 0L,
    val styleCurve: ParticleStyleCurve = ParticleStyleCurve(ParticleStyle(particle)),
) : ParticleEffect {
    init {
        require(durationTicks >= 1 && particlesPerTick >= 0 && burstCount >= 0)
        require(dimensions.x() >= 0.0 && dimensions.y() >= 0.0 && dimensions.z() >= 0.0)
        require(radius >= 0.0 && coneAngleDegrees in 0.0..89.9)
        require(speedRange.start >= 0f && speedRange.start.isFinite() && speedRange.endInclusive.isFinite() && speedRange.start <= speedRange.endInclusive)
    }

    override fun emit(tick: Int, sink: ParticleSink) {
        if (tick !in 0 until durationTicks || !anchor.sample().valid) return
        val count = when (rate) {
            EmitterRate.INSTANT -> if (tick == 0) particlesPerTick else 0
            EmitterRate.STEADY -> particlesPerTick
            EmitterRate.BURST -> if (tick == 0) burstCount else 0
        }
        if (count == 0) return
        val sample = anchor.sample()
        val transform = ParticleTransform.fromDirection(sample.position, initialDirection ?: sample.direction)
        val progress = ParticleFrame(tick, durationTicks).progress
        repeat(count) { index ->
            val random = Random(seed xor (tick.toLong() * 1_000_003L) xor index.toLong())
            val local = sampleLocal(random)
            val position = transform.localPoint(local)
            val direction = emissionDirection(local, transform)
            val rangeSpeed = if (speedRange.start == speedRange.endInclusive) speedRange.start
            else random.nextDouble(speedRange.start.toDouble(), speedRange.endInclusive.toDouble()).toFloat()
            val style = styleCurve.sample(progress)
            // An explicit speed curve is authoritative; otherwise use the configured range,
            // falling back to the style's base speed when no range was supplied.
            val speed = when {
                styleCurve.speed != null -> style.speed
                speedRange.start != 0f || speedRange.endInclusive != 0f -> rangeSpeed
                else -> style.speed
            }
            emitStyle(position, style.copy(offset = direction, speed = speed, directional = true), index + tick * max(1, count), sink)
        }
    }

    private fun sampleLocal(random: Random): Vec = when (shape) {
        SpawnShape.POINT -> Vec.ZERO
        SpawnShape.BOX -> Vec(
            random.nextDouble(-dimensions.x() / 2.0, dimensions.x() / 2.0),
            random.nextDouble(-dimensions.y() / 2.0, dimensions.y() / 2.0),
            random.nextDouble(-dimensions.z() / 2.0, dimensions.z() / 2.0),
        )
        SpawnShape.SPHERE -> {
            val z = random.nextDouble(-1.0, 1.0)
            val angle = random.nextDouble(0.0, Math.PI * 2.0)
            val radial = sqrt(max(0.0, 1.0 - z * z)) * radius * random.nextDouble().pow(1.0 / 3.0)
            Vec(cos(angle) * radial, z * radius, sin(angle) * radial)
        }
        SpawnShape.DISC -> {
            val angle = random.nextDouble(0.0, Math.PI * 2.0)
            val distance = radius * sqrt(random.nextDouble())
            Vec(cos(angle) * distance, sin(angle) * distance, 0.0)
        }
        SpawnShape.CONE -> {
            val distance = random.nextDouble(0.0, radius)
            val spread = tan(Math.toRadians(coneAngleDegrees)) * distance * sqrt(random.nextDouble())
            val angle = random.nextDouble(0.0, Math.PI * 2.0)
            Vec(cos(angle) * spread, sin(angle) * spread, distance)
        }
    }

    private fun emissionDirection(local: Vec, transform: ParticleTransform): Vec {
        val base = initialDirection?.let { emitterNormalize(it) } ?: when (shape) {
            SpawnShape.CONE -> emitterNormalize(local)
            SpawnShape.DISC -> transform.forward
            else -> transform.forward
        }
        return when {
            initialDirection != null -> emitterNormalize(initialDirection)
            shape == SpawnShape.CONE -> emitterNormalize(transform.localDirection(base))
            else -> emitterNormalize(base)
        }
    }
}

private fun emitterNormalize(vector: Vec): Vec = if (vector.length() <= 1.0e-9) Vec(0.0, 0.0, 1.0) else vector.mul(1.0 / vector.length())
