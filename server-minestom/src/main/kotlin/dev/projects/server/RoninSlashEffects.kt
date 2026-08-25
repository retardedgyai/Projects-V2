package dev.projects.server

import dev.projects.server.particle.CurveKeyframe
import dev.projects.server.particle.Easing
import dev.projects.server.particle.EmitterRate
import dev.projects.server.particle.KeyframeCurve
import dev.projects.server.particle.ParticleAnchor
import dev.projects.server.particle.ParticleCircle
import dev.projects.server.particle.ParticleCategory
import dev.projects.server.particle.ParticleEffect
import dev.projects.server.particle.ParticleEmitter
import dev.projects.server.particle.ParticleImportance
import dev.projects.server.particle.ParticleParallel
import dev.projects.server.particle.ParticleRibbon
import dev.projects.server.particle.ParticleSequence
import dev.projects.server.particle.ParticleSpiral
import dev.projects.server.particle.ParticleStyle
import dev.projects.server.particle.ParticleStyleCurve
import dev.projects.server.particle.ParticleTrail
import dev.projects.server.particle.ParticleTransform
import dev.projects.server.particle.SpawnShape
import dev.projects.server.particle.dust
import dev.projects.server.particle.lerpColor
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.particle.Particle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Ronin-only names for particle compositions; gameplay state stays in RoninState. */
internal enum class RoninSlashEffect {
    AA,
    Q,
    WOUND_MARK,
    WOUND_CONSUME,
    CROSSCUT,
    CROSSCUT_FLASH,
    TEMPEST_SEQUENCE,
    TEMPEST_FINAL,
    BLINK_TRAIL,
    BLINK_HIT,
    R_SHEATH,
    R_DRAW,
    R_SWEET_DRAW,
}

/**
 * Ronin-local particle compositions. These deliberately use the existing
 * primitives instead of adding another rendering or VFX framework.
 */
internal object RoninSlashEffects {
    private const val CRIMSON_EDGE = 0x3a0310
    private const val CRIMSON_BODY = 0x8f1026
    private const val CRIMSON_BRIGHT = 0xc51c38
    private const val WHITE_CORE = 0xfff8f2
    private const val PALE_CORE = 0xffc8c2

    fun create(
        effect: RoninSlashEffect,
        origin: Point,
        direction: Vec,
        variant: Int = 0,
        seed: Long = 0L,
    ): ParticleEffect = when (effect) {
        RoninSlashEffect.AA -> slash(
            origin = origin,
            direction = direction,
            length = 2.6,
            arcSpanDegrees = 82.0,
            tiltDegrees = 20.0,
            durationTicks = 4,
            bodyColor = 0x781021,
            bodySamples = 12,
            coreSamples = 16,
            edgeSamples = 6,
            fragmentCount = 1,
            seed = seed,
        )
        RoninSlashEffect.Q -> slash(
            origin = origin,
            direction = direction,
            length = 5.35,
            arcSpanDegrees = 110.0,
            tiltDegrees = 0.0,
            durationTicks = 6,
            bodyColor = CRIMSON_BODY,
            bodySamples = 20,
            coreSamples = 26,
            edgeSamples = 10,
            fragmentCount = 5,
            seed = seed,
        )
        RoninSlashEffect.WOUND_MARK -> slash(
            origin = origin,
            direction = direction,
            length = 1.55,
            arcSpanDegrees = 74.0,
            tiltDegrees = 38.0,
            durationTicks = 5,
            bodyColor = 0xa5162d,
            bodySamples = 8,
            coreSamples = 12,
            edgeSamples = 5,
            fragmentCount = 2,
            seed = seed,
        )
        RoninSlashEffect.WOUND_CONSUME -> woundConsume(origin, direction, seed)
        RoninSlashEffect.CROSSCUT -> slash(
            origin = origin,
            direction = direction,
            length = 3.25,
            arcSpanDegrees = 78.0,
            tiltDegrees = if (variant >= 0) 36.0 else -36.0,
            durationTicks = 5,
            bodyColor = CRIMSON_BODY,
            bodySamples = 12,
            coreSamples = 17,
            edgeSamples = 6,
            fragmentCount = 2,
            seed = seed,
        )
        RoninSlashEffect.CROSSCUT_FLASH -> crosscutFlash(origin, direction, seed)
        RoninSlashEffect.TEMPEST_SEQUENCE -> tempestSequence(origin, direction, seed)
        RoninSlashEffect.TEMPEST_FINAL -> tempestFinal(origin, direction, seed)
        RoninSlashEffect.BLINK_TRAIL -> ParticleSequence.of(
            dev.projects.server.particle.ParticleDelay(1),
            slash(
                origin = origin,
                direction = direction,
                length = 2.55,
                arcSpanDegrees = 78.0,
                tiltDegrees = 16.0,
                durationTicks = 4,
                bodyColor = 0x851022,
                bodySamples = 10,
                coreSamples = 14,
                edgeSamples = 5,
                fragmentCount = 2,
                seed = seed,
            ),
        )
        RoninSlashEffect.BLINK_HIT -> ParticleSequence.of(
            dev.projects.server.particle.ParticleDelay(2),
            slash(
                origin = origin,
                direction = direction,
                length = 2.15,
                arcSpanDegrees = 72.0,
                tiltDegrees = 25.0,
                durationTicks = 3,
                bodyColor = 0xa5162d,
                bodySamples = 9,
                coreSamples = 13,
                edgeSamples = 5,
                fragmentCount = 2,
                seed = seed,
            ),
        )
        RoninSlashEffect.R_SHEATH -> rSheath(origin, direction)
        RoninSlashEffect.R_DRAW -> rDraw(origin, direction, sweetSpot = false, seed = seed)
        RoninSlashEffect.R_SWEET_DRAW -> rDraw(origin, direction, sweetSpot = true, seed = seed)
    }

    /** A short moving trail used during E; the fixed landing slash is made by [create]. */
    fun blinkTrail(anchor: ParticleAnchor): ParticleEffect {
        val trail = ParticleTrail(
            anchor = anchor,
            particle = Particle.DUST,
            maxAgeTicks = 5,
            maxLength = 8.0,
            density = 4.0,
            teleportDistance = 12.0,
            durationTicks = 6,
            styleAt = { progress ->
                ParticleStyle(
                    particle = dust(lerpColor(CRIMSON_EDGE, CRIMSON_BRIGHT, 1.0 - progress), 0.18f),
                    densityMultiplier = 0.8,
                    importance = ParticleImportance.COMBAT_FEEDBACK,
                )
            },
        )
        val sparks = ParticleSequence.of(
            dev.projects.server.particle.ParticleDelay(2),
            ParticleEmitter(
                anchor = anchor,
                particle = Particle.ELECTRIC_SPARK,
                rate = EmitterRate.BURST,
                shape = SpawnShape.SPHERE,
                durationTicks = 1,
                burstCount = 4,
                radius = 0.24,
                speedRange = 0.04f..0.14f,
                seed = 71L,
                styleCurve = ParticleStyleCurve(
                    base = particleStyle(Particle.ELECTRIC_SPARK, directional = true, speed = 0.08f),
                ),
            ),
        )
        return ParticleParallel.of(trail, sparks)
    }

    private fun slash(
        origin: Point,
        direction: Vec,
        length: Double,
        arcSpanDegrees: Double,
        tiltDegrees: Double,
        durationTicks: Int,
        bodyColor: Int,
        bodySamples: Int,
        coreSamples: Int,
        edgeSamples: Int,
        fragmentCount: Int,
        seed: Long,
    ): ParticleEffect {
        val path = arcPath(origin, direction, length, arcSpanDegrees, tiltDegrees)
        val edgePath = arcPath(
            origin = origin,
            direction = direction,
            length = length * 0.98,
            arcSpanDegrees = arcSpanDegrees,
            tiltDegrees = tiltDegrees,
            lateralOffset = 0.08,
            forwardOffset = -0.06,
        )
        return layeredSlash(
            path = path,
            edgePath = edgePath,
            durationTicks = durationTicks,
            bodyColor = bodyColor,
            bodySamples = bodySamples,
            coreSamples = coreSamples,
            edgeSamples = edgeSamples,
            fragmentCount = fragmentCount,
            fragmentCenter = path(0.94),
            seed = seed,
        )
    }

    private fun layeredSlash(
        path: (Double) -> Point,
        edgePath: (Double) -> Point,
        durationTicks: Int,
        bodyColor: Int,
        bodySamples: Int,
        coreSamples: Int,
        edgeSamples: Int,
        fragmentCount: Int,
        fragmentCenter: Point,
        seed: Long,
    ): ParticleEffect {
        val body = ParticleRibbon(
            path = path,
            particle = Particle.DUST,
            sampleCount = bodySamples,
            lanes = 3,
            width = slashWidth(0.34),
            durationTicks = durationTicks,
            styleAt = { progress, laneProgress ->
                val middle = middleWeight(progress)
                val laneColor = if (laneProgress == 0.5) {
                    lerpColor(bodyColor, CRIMSON_BRIGHT, 0.24 + middle * 0.16)
                } else {
                    lerpColor(CRIMSON_EDGE, bodyColor, 0.42 + middle * 0.35)
                }
                particleStyle(
                    particle = dust(laneColor, (0.24 + middle * 0.16).toFloat()),
                    count = if (laneProgress == 0.5 && middle > 0.38) 2 else 1,
                    densityMultiplier = 0.92,
                )
            },
        )
        val core = ParticleRibbon(
            path = path,
            particle = Particle.DUST,
            sampleCount = coreSamples,
            lanes = 1,
            width = slashWidth(0.08),
            durationTicks = durationTicks,
            styleAt = { progress, _ ->
                val middle = middleWeight(progress)
                particleStyle(
                    particle = dust(lerpColor(PALE_CORE, WHITE_CORE, middle), (0.13 + middle * 0.11).toFloat()),
                    count = if (middle > 0.55) 2 else 1,
                    densityMultiplier = 1.0,
                )
            },
        )
        val edge = ParticleRibbon(
            path = edgePath,
            particle = Particle.DUST,
            sampleCount = edgeSamples,
            lanes = 2,
            width = slashWidth(0.18),
            durationTicks = durationTicks,
            styleAt = { progress, _ ->
                particleStyle(
                    particle = dust(lerpColor(CRIMSON_EDGE, 0x6f071b, middleWeight(progress)), 0.16f),
                    densityMultiplier = 0.55,
                )
            },
        )
        val fragments = if (fragmentCount <= 0) {
            null
        } else {
            ParticleSequence.of(
                dev.projects.server.particle.ParticleDelay((durationTicks - 1).coerceAtLeast(0)),
                fragmentBurst(fragmentCenter, fragmentCount, 0.16, seed),
            )
        }
        return ParticleParallel.of(
            body,
            core,
            edge,
            *(listOfNotNull(fragments).toTypedArray()),
        )
    }

    private fun woundConsume(origin: Point, direction: Vec, seed: Long): ParticleEffect {
        val frame = ParticleTransform.fromDirection(origin, horizontal(direction))
        return ParticleParallel.of(
            ParticleCircle(
                center = origin,
                radius = 0.42,
                axis1 = frame.right,
                axis2 = frame.up,
                startDegrees = -80.0,
                endDegrees = 220.0,
                countPerMeter = 10.0,
                durationTicks = 3,
                style = particleStyle(dust(WHITE_CORE, 0.16f), importance = ParticleImportance.COMBAT_FEEDBACK),
            ),
            flash(origin, count = 5, radius = 0.20, seed = seed),
            fragmentBurst(origin, count = 5, radius = 0.34, seed = seed + 1L, particle = Particle.DUST, color = CRIMSON_BRIGHT),
        )
    }

    private fun crosscutFlash(origin: Point, direction: Vec, seed: Long): ParticleEffect = ParticleParallel.of(
        slash(origin, direction, 2.5, 68.0, 35.0, 2, CRIMSON_BODY, 8, 11, 4, 0, seed),
        slash(origin, direction, 2.5, 68.0, -35.0, 2, CRIMSON_BODY, 8, 11, 4, 0, seed + 11L),
        flash(origin, count = 8, radius = 0.26, seed = seed + 23L),
        fragmentBurst(origin, count = 7, radius = 0.42, seed = seed + 31L),
    )

    private fun tempestSequence(origin: Point, direction: Vec, seed: Long): ParticleEffect {
        val frame = ParticleTransform.fromDirection(origin, horizontal(direction))
        data class Spec(
            val delay: Int,
            val yaw: Double,
            val tilt: Double,
            val length: Double,
            val arc: Double,
            val radius: Double,
            val height: Double,
        )

        val specs = listOf(
            Spec(0, 28.0, 28.0, 4.65, 104.0, 0.62, 0.10),
            Spec(1, -42.0, -24.0, 4.35, 96.0, 0.76, 0.08),
            Spec(3, 68.0, 18.0, 3.65, 88.0, 0.60, 0.12),
            Spec(4, -78.0, -20.0, 3.85, 94.0, 0.78, 0.10),
            Spec(6, 18.0, 64.0, 3.35, 82.0, 0.52, 0.25),
            Spec(7, 116.0, -58.0, 3.20, 82.0, 0.68, 0.20),
            Spec(9, 0.0, 0.0, 4.85, 112.0, 0.40, 0.08),
            Spec(11, 156.0, 24.0, 3.75, 92.0, 0.78, 0.12),
            Spec(12, -132.0, -30.0, 3.45, 86.0, 0.56, 0.10),
            Spec(14, 204.0, 18.0, 4.10, 100.0, 0.70, 0.12),
        )
        val effects = specs.mapIndexed { index, spec ->
            val facing = yaw(horizontal(direction), spec.yaw)
            val localOrigin = frame.localPoint(
                Vec(
                    cos(Math.toRadians(spec.yaw)) * spec.radius,
                    0.45 + spec.height,
                    sin(Math.toRadians(spec.yaw)) * spec.radius,
                ),
            )
            val slash = slash(
                origin = localOrigin,
                direction = facing,
                length = spec.length,
                arcSpanDegrees = spec.arc,
                tiltDegrees = spec.tilt,
                durationTicks = 2,
                bodyColor = if (index % 3 == 0) CRIMSON_BRIGHT else CRIMSON_BODY,
                bodySamples = 12,
                coreSamples = 14,
                edgeSamples = 6,
                fragmentCount = 2,
                seed = seed + index * 37L,
            )
            if (spec.delay == 0) slash else ParticleSequence.of(
                dev.projects.server.particle.ParticleDelay(spec.delay),
                slash,
            )
        }
        return ParticleParallel.of(*effects.toTypedArray())
    }

    private fun tempestFinal(origin: Point, direction: Vec, seed: Long): ParticleEffect {
        val frame = ParticleTransform.fromDirection(origin, horizontal(direction))
        val path = sweepPath(origin, direction, 4.45, -154.0, 154.0, 0.10)
        val edgePath = sweepPath(origin, direction, 4.34, -154.0, 154.0, 0.02)
        return ParticleParallel.of(
            layeredSlash(
                path = path,
                edgePath = edgePath,
                durationTicks = 7,
                bodyColor = CRIMSON_BODY,
                bodySamples = 28,
                coreSamples = 34,
                edgeSamples = 12,
                fragmentCount = 0,
                fragmentCenter = path(0.5),
                seed = seed,
            ),
            flash(origin, count = 8, radius = 0.34, seed = seed + 71L),
            ParticleCircle(
                center = origin,
                radius = 1.05,
                axis1 = frame.right,
                axis2 = frame.up,
                startDegrees = -65.0,
                endDegrees = 120.0,
                countPerMeter = 8.0,
                durationTicks = 4,
                style = particleStyle(dust(PALE_CORE, 0.15f), importance = ParticleImportance.COMBAT_FEEDBACK),
            ),
            fragmentBurst(path(0.86), count = 10, radius = 0.45, seed = seed + 83L),
        )
    }

    private fun rSheath(origin: Point, direction: Vec): ParticleEffect {
        val facing = horizontal(direction)
        val frame = ParticleTransform.fromDirection(origin, facing)
        val gather = ParticleParallel.of(
            ParticleSpiral(
                origin = origin,
                axis = facing,
                radius = 0.34,
                curveAngle = PI * 2.0,
                curves = 1.0,
                axialLength = 0.45,
                durationTicks = 4,
                style = particleStyle(dust(CRIMSON_BODY, 0.17f), densityMultiplier = 0.75),
            ),
            ParticleCircle(
                center = origin,
                radius = 0.46,
                axis1 = frame.right,
                axis2 = frame.up,
                startDegrees = -70.0,
                endDegrees = 190.0,
                countPerMeter = 8.0,
                durationTicks = 4,
                style = particleStyle(dust(PALE_CORE, 0.13f), densityMultiplier = 0.8),
            ),
        )
        val stillRelease = ParticleSequence.of(
            dev.projects.server.particle.ParticleDelay(4),
            flash(origin, count = 5, radius = 0.18, seed = 501L),
        )
        return ParticleParallel.of(gather, stillRelease)
    }

    private fun rDraw(origin: Point, direction: Vec, sweetSpot: Boolean, seed: Long): ParticleEffect {
        val slash = slash(
            origin = origin,
            direction = direction,
            length = if (sweetSpot) 6.6 else 6.35,
            arcSpanDegrees = 118.0,
            tiltDegrees = 0.0,
            durationTicks = if (sweetSpot) 7 else 6,
            bodyColor = CRIMSON_BODY,
            bodySamples = 18,
            coreSamples = if (sweetSpot) 28 else 24,
            edgeSamples = 8,
            fragmentCount = if (sweetSpot) 4 else 3,
            seed = seed,
        )
        if (!sweetSpot) return slash
        val frame = ParticleTransform.fromDirection(origin, horizontal(direction))
        return ParticleParallel.of(
            slash,
            flash(origin, count = 7, radius = 0.30, seed = seed + 17L),
            ParticleCircle(
                center = origin,
                radius = 0.72,
                axis1 = frame.right,
                axis2 = frame.up,
                startDegrees = -48.0,
                endDegrees = 132.0,
                countPerMeter = 9.0,
                durationTicks = 3,
                style = particleStyle(dust(WHITE_CORE, 0.15f), importance = ParticleImportance.GAMEPLAY_TELEGRAPH),
            ),
        )
    }

    private fun slashWidth(maxWidth: Double) = KeyframeCurve.double(
        CurveKeyframe(0.0, maxWidth * 0.34),
        CurveKeyframe(0.42, maxWidth),
        CurveKeyframe(0.58, maxWidth),
        CurveKeyframe(1.0, maxWidth * 0.34),
        easing = Easing.SINE,
    )

    private fun middleWeight(progress: Double): Double = sin(progress.coerceIn(0.0, 1.0) * PI).pow(0.72)

    private fun particleStyle(
        particle: Particle,
        count: Int = 1,
        densityMultiplier: Double = 1.0,
        speed: Float = 0f,
        directional: Boolean = false,
        category: ParticleCategory = ParticleCategory.OWN_ACTIVE,
        importance: ParticleImportance = ParticleImportance.COMBAT_FEEDBACK,
    ) = ParticleStyle(
        particle = particle,
        count = count,
        densityMultiplier = densityMultiplier,
        speed = speed,
        directional = directional,
        category = category,
        importance = importance,
    )

    private fun fragmentBurst(
        center: Point,
        count: Int,
        radius: Double,
        seed: Long,
        particle: Particle = Particle.ELECTRIC_SPARK,
        color: Int? = null,
    ): ParticleEffect {
        val baseStyle = if (color == null) {
            particleStyle(particle, directional = true, speed = 0.10f)
        } else {
            particleStyle(dust(color, 0.15f), directional = true, speed = 0.06f)
        }
        return ParticleEmitter(
            anchor = ParticleAnchor.fixed(center),
            particle = particle,
            rate = EmitterRate.BURST,
            shape = SpawnShape.SPHERE,
            durationTicks = 1,
            burstCount = count,
            radius = radius,
            speedRange = if (color == null) 0.05f..0.16f else 0.02f..0.10f,
            seed = seed,
            styleCurve = ParticleStyleCurve(base = baseStyle),
        )
    }

    private fun flash(center: Point, count: Int, radius: Double, seed: Long): ParticleEffect = ParticleEmitter(
        anchor = ParticleAnchor.fixed(center),
        particle = Particle.END_ROD,
        rate = EmitterRate.BURST,
        shape = SpawnShape.SPHERE,
        durationTicks = 1,
        burstCount = count,
        radius = radius,
        speedRange = 0.01f..0.07f,
        seed = seed,
        styleCurve = ParticleStyleCurve(
            base = particleStyle(Particle.END_ROD, directional = true, speed = 0.04f),
        ),
    )

    private fun arcPath(
        origin: Point,
        direction: Vec,
        length: Double,
        arcSpanDegrees: Double,
        tiltDegrees: Double,
        lateralOffset: Double = 0.0,
        forwardOffset: Double = 0.0,
        verticalLift: Double = 0.12,
    ): (Double) -> Point {
        val transform = ParticleTransform.fromDirection(origin, horizontal(direction))
        val span = Math.toRadians(arcSpanDegrees)
        val halfSpan = (abs(span) / 2.0).coerceAtLeast(Math.toRadians(5.0))
        val radius = length.coerceAtLeast(0.05) / (2.0 * sin(halfSpan))
        val tilt = Math.toRadians(tiltDegrees)
        return { progress ->
            val t = progress.coerceIn(0.0, 1.0)
            val theta = -span / 2.0 + span * t
            val lateral = sin(theta) * radius + lateralOffset
            val depth = (cos(theta) - cos(halfSpan)) * radius + forwardOffset
            val vertical = sin(t * PI) * verticalLift
            val rotatedLateral = lateral * cos(tilt) - vertical * sin(tilt)
            val rotatedVertical = lateral * sin(tilt) + vertical * cos(tilt)
            transform.localPoint(Vec(rotatedLateral, rotatedVertical, depth))
        }
    }

    private fun sweepPath(
        origin: Point,
        direction: Vec,
        radius: Double,
        startDegrees: Double,
        endDegrees: Double,
        verticalLift: Double,
    ): (Double) -> Point {
        val transform = ParticleTransform.fromDirection(origin, horizontal(direction))
        return { progress ->
            val angle = Math.toRadians(startDegrees + (endDegrees - startDegrees) * progress.coerceIn(0.0, 1.0))
            transform.localPoint(
                Vec(
                    cos(angle) * radius,
                    sin(progress.coerceIn(0.0, 1.0) * PI) * verticalLift,
                    sin(angle) * radius,
                ),
            )
        }
    }

    private fun horizontal(direction: Vec): Vec {
        val length = kotlin.math.sqrt(direction.x() * direction.x() + direction.z() * direction.z())
        return if (length > 1.0e-9) Vec(direction.x() / length, 0.0, direction.z() / length) else Vec(0.0, 0.0, 1.0)
    }

    private fun yaw(direction: Vec, degrees: Double): Vec {
        val angle = Math.toRadians(degrees)
        val cosine = cos(angle)
        val sine = sin(angle)
        return horizontal(Vec(
            direction.x() * cosine + direction.z() * sine,
            0.0,
            -direction.x() * sine + direction.z() * cosine,
        ))
    }
}
