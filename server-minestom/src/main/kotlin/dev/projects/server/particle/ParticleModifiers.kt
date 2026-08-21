package dev.projects.server.particle

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import kotlin.math.ceil
import kotlin.math.floor

/** Effect wrappers. Chaining is evaluated inside-out: the last wrapper runs first. */
class ModifiedParticleEffect(
    private val source: ParticleEffect,
    private val transform: (Point) -> Point = { it },
    private val spawnTransform: (ParticleSpawn) -> ParticleSpawn = { it },
    private val tickTransform: (Int) -> Int = { it },
    override val durationTicks: Int = source.durationTicks,
) : ParticleEffect {
    override fun emit(tick: Int, sink: ParticleSink) {
        if (tick !in 0 until durationTicks) return
        source.emit(tickTransform(tick)) { spawn ->
            sink.spawn(spawnTransform(spawn.copy(position = transform(spawn.position))))
        }
    }
}

fun ParticleEffect.translated(offset: Vec): ParticleEffect = ModifiedParticleEffect(this, transform = {
    it.add(offset.x(), offset.y(), offset.z())
})

fun ParticleEffect.rotated(transform: ParticleTransform): ParticleEffect = ModifiedParticleEffect(this, transform = {
    val pivot = transform.origin
    val delta = Vec(it.x() - pivot.x(), it.y() - pivot.y(), it.z() - pivot.z())
    val rotated = transform.localDirection(delta)
    pivot.add(rotated.x(), rotated.y(), rotated.z())
})

fun ParticleEffect.transformed(transform: ParticleTransform): ParticleEffect = rotated(transform)

fun ParticleEffect.localCoordinates(transform: ParticleTransform): ParticleEffect = rotated(transform)

fun ParticleEffect.scaled(factor: Double, pivot: Point = Vec.ZERO): ParticleEffect {
    require(factor.isFinite() && factor >= 0.0)
    return ModifiedParticleEffect(this, transform = { point ->
        pivot.add(
            (point.x() - pivot.x()) * factor,
            (point.y() - pivot.y()) * factor,
            (point.z() - pivot.z()) * factor,
        )
    })
}

fun ParticleEffect.timeScaled(factor: Double): ParticleEffect {
    require(factor.isFinite() && factor > 0.0)
    val duration = ceil(durationTicks / factor).toInt().coerceAtLeast(1)
    return ModifiedParticleEffect(this, tickTransform = { floor(it * factor).toInt().coerceIn(0, durationTicks - 1) }, durationTicks = duration)
}

fun ParticleEffect.reversedTime(): ParticleEffect = ModifiedParticleEffect(
    this,
    tickTransform = { durationTicks - 1 - it.coerceIn(0, durationTicks - 1) },
)

fun ParticleEffect.density(multiplier: Double): ParticleEffect {
    require(multiplier >= 0.0 && multiplier.isFinite())
    return ModifiedParticleEffect(this, spawnTransform = { spawn ->
        val count = (spawn.count * multiplier).toInt()
        spawn.copy(count = count)
    })
}

fun ParticleEffect.colorMap(map: (Int) -> Int): ParticleEffect = ModifiedParticleEffect(this, spawnTransform = { spawn ->
    // Vanilla particles expose different payloads. Keep non-dust payloads untouched.
    spawn.copy(particle = when (val particle = spawn.particle) {
        is net.minestom.server.particle.Particle.Dust -> dust(
            map((particle.color().red() shl 16) or (particle.color().green() shl 8) or particle.color().blue()),
            particle.scale(),
        )
        else -> particle
    })
})

fun ParticleEffect.tint(color: Int): ParticleEffect = colorMap { color }

/** Per-frame geometry curves for authored offsets, rotation and radius/length scaling. */
fun ParticleEffect.animatedTransform(
    positionOffset: Curve<Vec> = constantCurve(Vec.ZERO),
    rotationDegrees: Curve<Double> = constantCurve(0.0),
    scale: Curve<Double> = constantCurve(1.0),
    pivot: Point = Vec.ZERO,
): ParticleEffect = object : ParticleEffect {
    override val durationTicks: Int = this@animatedTransform.durationTicks

    override fun emit(tick: Int, sink: ParticleSink) {
        if (tick !in 0 until durationTicks) return
        val progress = ParticleFrame(tick, durationTicks).progress
        val angle = Math.toRadians(rotationDegrees.sample(progress))
        val factor = scale.sample(progress)
        require(factor.isFinite() && factor >= 0.0)
        this@animatedTransform.emit(tick) { spawn ->
            val dx = spawn.position.x() - pivot.x()
            val dz = spawn.position.z() - pivot.z()
            val rotated = Vec(
                dx * kotlin.math.cos(angle) - dz * kotlin.math.sin(angle),
                spawn.position.y() - pivot.y(),
                dx * kotlin.math.sin(angle) + dz * kotlin.math.cos(angle),
            )
            val offset = positionOffset.sample(progress)
            sink.spawn(spawn.copy(position = pivot.add(
                rotated.x() * factor + offset.x(),
                rotated.y() * factor + offset.y(),
                rotated.z() * factor + offset.z(),
            )))
        }
    }
}
