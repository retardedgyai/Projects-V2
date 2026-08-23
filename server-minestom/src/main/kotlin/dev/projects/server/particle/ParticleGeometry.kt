package dev.projects.server.particle

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.particle.Particle
import kotlin.math.cos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sin

object ParticleGeometry {
    fun drawCleaveArc(
        origin: Point,
        facing: Vec,
        radius: Double,
        tiltAngle: Double,
        startDegrees: Double,
        endDegrees: Double,
        rings: Int,
        extraYaw: Double = 0.0,
        extraPitch: Double = 0.0,
        ringSpacing: Double = 0.22,
        degreesPerTick: Double = 45.0,
        degreeStep: Double = 8.0,
        lateralOffset: Double = 0.0,
        sample: (position: Point, ringIndex: Int, angleProgress: Double) -> ParticleStyle =
            { _, ring, progress -> ParticleStyle(if (ring == 0) dust(0xffdd33, 0.45f) else dust(0xff5522, 0.3f + progress.toFloat() * 0.15f)) },
    ): ParticleEffect {
        val length = facing.length().coerceAtLeast(1.0e-9)
        return drawCleaveArc(
            originFacing = origin,
            radius = radius,
            tiltAngle = tiltAngle,
            startDegrees = startDegrees,
            endDegrees = endDegrees,
            rings = rings,
            extraYaw = Math.toDegrees(atan2(facing.x(), facing.z())) + extraYaw,
            extraPitch = Math.toDegrees(asin((facing.y() / length).coerceIn(-1.0, 1.0))) + extraPitch,
            ringSpacing = ringSpacing,
            degreesPerTick = degreesPerTick,
            degreeStep = degreeStep,
            lateralOffset = lateralOffset,
            sample = sample,
        )
    }

    fun drawParticleLineSlash(
        origin: Point,
        direction: Vec,
        angleDegrees: Double,
        length: Double,
        spacing: Double,
        durationTicks: Int,
        sample: (position: Point, middleProgress: Double, endProgress: Double, middle: Boolean) -> ParticleStyle =
            { _, middle, _, _ -> ParticleStyle(Particle.END_ROD, if (middle > 0.6) 2 else 1) },
    ): ParticleEffect {
        val (_, right, up) = basis(direction)
        val angle = Math.toRadians(angleDegrees)
        val line = right.mul(cos(angle)).add(up.mul(sin(angle)))
        val safeSpacing = spacing.coerceAtLeast(0.01)
        val count = (length / safeSpacing).toInt().coerceAtLeast(1)
        return ParticleParametric(
            positionAt = { t ->
                val distance = (t - 0.5) * length
                origin.add(line.x() * distance, line.y() * distance, line.z() * distance)
            },
            sampleCount = count,
            durationTicks = durationTicks,
            styleAt = { t ->
                val middleProgress = (1.0 - kotlin.math.abs(t - 0.5) * 2.0).coerceIn(0.0, 1.0)
                val endProgress = t.coerceIn(0.0, 1.0)
                sample(
                    origin.add(line.x() * ((t - 0.5) * length), line.y() * ((t - 0.5) * length), line.z() * ((t - 0.5) * length)),
                    middleProgress,
                    endProgress,
                    kotlin.math.abs(t - 0.5) < 0.5 / count,
                )
            },
        )
    }

    fun drawCleaveArc(
        originFacing: Point,
        radius: Double,
        tiltAngle: Double,
        startDegrees: Double,
        endDegrees: Double,
        rings: Int,
        extraYaw: Double = 0.0,
        extraPitch: Double = 0.0,
        ringSpacing: Double = 0.22,
        degreesPerTick: Double = 45.0,
        degreeStep: Double = 8.0,
        lateralOffset: Double = 0.0,
        sample: (position: Point, ringIndex: Int, angleProgress: Double) -> ParticleStyle =
            { _, ring, progress -> ParticleStyle(if (ring == 0) dust(0xffdd33, 0.45f) else dust(0xff5522, 0.3f + progress.toFloat() * 0.15f)) },
    ): ParticleEffect {
        val origin = originFacing
        val facing = Vec(0.0, 0.0, 1.0)
        val yaw = Math.toRadians(extraYaw)
        val pitch = Math.toRadians(extraPitch + tiltAngle)
        val forward = Vec(sin(yaw) * cos(pitch), sin(pitch), cos(yaw) * cos(pitch))
        val (_, right, up) = basis(forward)
        val ringCount = rings.coerceAtLeast(1)
        val span = endDegrees - startDegrees
        val steps = (kotlin.math.abs(span) / degreeStep.coerceAtLeast(0.1)).toInt().coerceAtLeast(1)
        val totalTicks = (kotlin.math.abs(span) / degreesPerTick.coerceAtLeast(0.1)).toInt().coerceAtLeast(1)
        val effectiveRadius = radius.coerceAtLeast(0.0)
        return object : ParticleEffect {
            override val durationTicks: Int = totalTicks

            override fun emit(tick: Int, sink: ParticleSink) {
                val progressEnd = ((tick + 1).toDouble() / durationTicks).coerceAtMost(1.0)
                val startProgress = tick.toDouble() / durationTicks
                for (ring in 0 until ringCount) {
                    val ringRadius = effectiveRadius + ring * ringSpacing
                    for (step in 0..steps) {
                        val progress = step.toDouble() / steps
                        if (progress > progressEnd || progress < startProgress) continue
                        val angle = Math.toRadians(startDegrees + span * progress)
                        val radial = right.mul(cos(angle) * ringRadius).add(up.mul(sin(angle) * ringRadius))
                        val point = origin.add(
                            radial.x() + right.x() * lateralOffset,
                            radial.y() + right.y() * lateralOffset,
                            radial.z() + right.z() * lateralOffset,
                        )
                        emitStyle(point, sample(point, ring, progress), step + ring * (steps + 1), sink)
                    }
                }
            }
        }
    }

    fun drawHalfArc(
        originFacing: Point,
        radius: Double,
        durationTicks: Int,
        sample: (Point, Double) -> ParticleStyle = { _, _ -> ParticleStyle(Particle.END_ROD) },
    ): ParticleEffect = drawCleaveArc(
        originFacing = originFacing,
        radius = radius,
        tiltAngle = 0.0,
        startDegrees = -90.0,
        endDegrees = 90.0,
        rings = 1,
        degreesPerTick = 180.0 / durationTicks.coerceAtLeast(1),
        sample = { point, _, progress -> sample(point, progress) },
    )

    fun drawParticleCircleExplosion(
        center: Point,
        radius: Double,
        count: Int,
        particle: Particle = Particle.ELECTRIC_SPARK,
        durationTicks: Int = 1,
    ): ParticleEffect = ParticleExplosion(center, radius, false, particle, count, 0.35f, 0.08f, radius, 0L, durationTicks)
}
