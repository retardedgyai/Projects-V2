package dev.projects.server

import dev.projects.protocol.ProtocolMessage
import dev.projects.protocol.VfxEditor2Composition
import dev.projects.protocol.VfxEditor2Direction
import dev.projects.protocol.VfxEditor2Open
import dev.projects.protocol.VfxEditor2PreviewStart
import dev.projects.protocol.VfxEditor2PreviewStop
import dev.projects.protocol.VfxEditor2Shape
import dev.projects.protocol.VfxEditor2Status
import dev.projects.protocol.VfxEditor2StatusKind
import dev.projects.protocol.VFX_EDITOR_2_MAX_SAMPLES_PER_EFFECT
import dev.projects.protocol.defaultVfxEditor2Composition
import dev.projects.protocol.estimateVfxEditor2Samples
import dev.projects.server.particle.EmitterRate
import dev.projects.server.particle.ParticleAnchor
import dev.projects.server.particle.ParticleAnimationScheduler
import dev.projects.server.particle.ParticleBatch
import dev.projects.server.particle.ParticleBezier
import dev.projects.server.particle.ParticleCircle
import dev.projects.server.particle.ParticleDelay
import dev.projects.server.particle.ParticleEffect
import dev.projects.server.particle.ParticleEffectHandle
import dev.projects.server.particle.ParticleEffectState
import dev.projects.server.particle.ParticleEmitter
import dev.projects.server.particle.ParticleExplosion
import dev.projects.server.particle.ParticleLine
import dev.projects.server.particle.ParticleLightning
import dev.projects.server.particle.ParticleManager
import dev.projects.server.particle.ParticleMulti
import dev.projects.server.particle.ParticleParametric
import dev.projects.server.particle.ParticlePillar
import dev.projects.server.particle.ParticleRectPrism
import dev.projects.server.particle.ParticleRibbon
import dev.projects.server.particle.ParticleSink
import dev.projects.server.particle.ParticleSpawn
import dev.projects.server.particle.ParticleSpiral
import dev.projects.server.particle.ParticleStyle
import dev.projects.server.particle.ParticleStyleCurve
import dev.projects.server.particle.ParticleTransform
import dev.projects.server.particle.ParticleViewer
import dev.projects.server.particle.PlayerParticleSink
import dev.projects.server.particle.ParticleUtils
import dev.projects.server.particle.RectPrismMode
import dev.projects.server.particle.SpawnShape
import dev.projects.server.particle.constantCurve
import dev.projects.server.particle.dust
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.particle.Particle
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

private const val VFX_EDITOR_2_PREVIEW_DURATION_TICKS = 12
private const val VFX_EDITOR_2_PREVIEW_DISTANCE = 3.0
private const val VFX_EDITOR_2_PREVIEW_HEIGHT = 1.1

/** Compiles the small, typed workbench model into the existing particle primitives. */
object VfxWorkbenchCompiler {
    fun compile(composition: VfxEditor2Composition, origin: Point, direction: Vec): ParticleEffect {
        val visibleEffects = composition.visibleEffects()
        if (visibleEffects.isEmpty()) return ParticleDelay(VFX_EDITOR_2_PREVIEW_DURATION_TICKS)
        return ParticleBatch.of(*visibleEffects.map { effect -> compileEffect(effect, origin, direction) }.toTypedArray())
    }

    private fun compileEffect(
        effect: dev.projects.protocol.VfxEditor2Effect,
        origin: Point,
        direction: Vec,
    ): ParticleEffect {
        val transform = effectFrame(origin, direction, effect.transform)
        val appearance = effect.appearance
        val particle = dust(appearance.color, appearance.particleSize.toFloat())
        return when (val shape = effect.shape) {
            is VfxEditor2Shape.ArcSlash -> compileArcSlash(shape, transform, appearance, particle)
            is VfxEditor2Shape.StraightSlash -> compileStraightSlash(shape, transform, appearance, particle)
            is VfxEditor2Shape.Ring -> compileRing(shape, transform, appearance, particle)
            is VfxEditor2Shape.Burst -> compileBurst(shape, transform, particle)
            is VfxEditor2Shape.Bezier -> compileBezier(shape, transform, appearance, particle)
            is VfxEditor2Shape.Wave -> compileWave(shape, transform, appearance, particle)
            is VfxEditor2Shape.Lightning -> compileLightning(shape, transform, appearance, particle)
            is VfxEditor2Shape.Spiral -> compileSpiral(shape, transform, appearance, particle)
            is VfxEditor2Shape.Helix -> compileHelix(shape, transform, appearance, particle)
            is VfxEditor2Shape.Disk -> compileDisk(shape, transform, appearance, particle)
            is VfxEditor2Shape.Sector -> compileSector(shape, transform, appearance, particle)
            is VfxEditor2Shape.Grid -> compileGrid(shape, transform, appearance, particle)
            is VfxEditor2Shape.Sphere -> compileSphere(shape, transform, appearance, particle)
            is VfxEditor2Shape.Orb -> compileOrb(shape, transform, appearance, particle)
            is VfxEditor2Shape.Dome -> compileDome(shape, transform, appearance, particle)
            is VfxEditor2Shape.Cylinder -> compileCylinder(shape, transform, appearance, particle)
            is VfxEditor2Shape.Cone -> compileCone(shape, transform, appearance, particle)
            is VfxEditor2Shape.Box -> compileBox(shape, transform, appearance, particle)
            is VfxEditor2Shape.Torus -> compileTorus(shape, transform, appearance, particle)
            is VfxEditor2Shape.Star -> compileStar(shape, transform, appearance, particle)
            is VfxEditor2Shape.Cross -> compileCross(shape, transform, appearance, particle)
            is VfxEditor2Shape.Shockwave -> compileShockwave(shape, transform, appearance, particle)
            is VfxEditor2Shape.Vortex -> compileVortex(shape, transform, appearance, particle)
            is VfxEditor2Shape.Tornado -> compileTornado(shape, transform, appearance, particle)
            is VfxEditor2Shape.Fountain -> compileFountain(shape, transform, appearance, particle)
            is VfxEditor2Shape.SphereBurst -> compileSphereBurst(shape, transform, particle)
            is VfxEditor2Shape.ConeBurst -> compileConeBurst(shape, transform, appearance, particle)
        }
    }

    private fun compileArcSlash(
        shape: VfxEditor2Shape.ArcSlash,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect {
        val arcBulge = shape.length * sin(Math.toRadians(shape.arcDegrees / 2.0)).coerceAtLeast(0.05) * 0.22
        val curvatureBulge = shape.curvature * 0.42
        val path: (Double) -> Point = { progress ->
            val lateral = (progress - 0.5) * shape.length
            val depth = sin(progress * PI) * (arcBulge + curvatureBulge)
            localPoint(transform, lateral, 0.0, depth)
        }
        val lanes = if (shape.thickness <= 0.04) 1 else 3
        val sampleCount = ribbonSampleCount(shape, appearance, lanes)
        return ParticleRibbon(
            path = path,
            particle = particle,
            sampleCount = sampleCount,
            lanes = lanes,
            width = constantCurve(shape.thickness),
            durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
            styleAt = { _, _ -> ParticleStyle(particle) },
        )
    }

    private fun compileStraightSlash(
        shape: VfxEditor2Shape.StraightSlash,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect {
        val offsets = if (shape.thickness <= 0.04) listOf(0.0) else listOf(-0.5, 0.0, 0.5)
        val countPerMeter = boundedDensity(shape.length, appearance.density * 7.0, VFX_EDITOR_2_MAX_SAMPLES_PER_EFFECT / offsets.size - 1)
        return ParticleBatch.of(*offsets.map { offset ->
            val verticalOffset = offset * shape.thickness
            ParticleLine(
                start = localPoint(transform, -shape.length / 2.0, verticalOffset, 0.0),
                end = localPoint(transform, shape.length / 2.0, verticalOffset, 0.0),
                countPerMeter = countPerMeter,
                durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
                style = ParticleStyle(particle),
            )
        }.toTypedArray())
    }

    private fun compileRing(
        shape: VfxEditor2Shape.Ring,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect {
        val innerRadiusFactor = if (shape.radius <= 1.0e-6) 0.0 else {
            ((shape.radius - shape.thickness) / shape.radius).coerceIn(0.0, 1.0)
        }
        return ParticleCircle(
            center = transform.origin,
            radius = shape.radius,
            axis1 = transform.right,
            axis2 = transform.up,
            filled = shape.thickness > 0.04,
            innerRadiusFactor = innerRadiusFactor,
            startDegrees = -shape.arcDegrees / 2.0,
            endDegrees = shape.arcDegrees / 2.0,
            countPerMeter = circleDensity(
                shape.radius,
                shape.arcDegrees,
                appearance.density * 8.0,
                shape.thickness > 0.04,
                innerRadiusFactor,
                VFX_EDITOR_2_MAX_SAMPLES_PER_EFFECT,
            ),
            includeEnd = true,
            durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
            style = ParticleStyle(particle),
        )
    }

    private fun compileBurst(
        shape: VfxEditor2Shape.Burst,
        transform: ParticleTransform,
        particle: Particle,
    ): ParticleEffect {
        return directionalBurst(
            transform = transform,
            count = shape.count,
            angleDegrees = shape.spread,
            length = shape.radius,
            speed = shape.speed,
            particle = particle,
            seed = shape.seed,
        )
    }

    private fun compileBezier(
        shape: VfxEditor2Shape.Bezier,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect {
        val start = localPoint(transform, 0.0, 0.0, 0.0)
        val control = localPoint(transform, shape.controlSide, shape.controlHeight, shape.controlForward)
        val end = localPoint(transform, shape.endSide, shape.endHeight, shape.length)
        val pointAt: (Double) -> Point = { t -> quadraticBezier(start, control, end, t) }
        val center = ParticleBezier(
            start = start,
            end = end,
            controlPoints = listOf(control),
            particle = particle,
            sampleCount = (sampleBudget(shape, appearance) - 1).coerceAtLeast(1),
            durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
            styleAt = { ParticleStyle(particle) },
        )
        if (shape.thickness <= 0.04) return center
        return ParticleBatch.of(
            center,
            ParticleRibbon(
                path = pointAt,
                particle = particle,
                 sampleCount = ribbonSampleCount(shape, appearance, 3),
                lanes = 3,
                width = constantCurve(shape.thickness),
                durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
                styleAt = { _, _ -> ParticleStyle(particle) },
            ),
        )
    }

    private fun compileWave(
        shape: VfxEditor2Shape.Wave,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect {
        val phase = Math.toRadians(shape.phaseDegrees)
        val pointAt: (Double) -> Point = { progress ->
            val lateral = shape.amplitude * sin(progress * shape.waves * 2.0 * PI + phase)
            localPoint(transform, lateral, 0.0, (progress - 0.5) * shape.length)
        }
        val center = ParticleParametric(
            positionAt = pointAt,
            particle = particle,
             sampleCount = (sampleBudget(shape, appearance) - 1).coerceAtLeast(1),
            durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
            styleAt = { ParticleStyle(particle) },
        )
        if (shape.thickness <= 0.04) return center
        return ParticleBatch.of(
            center,
            ParticleRibbon(
                path = pointAt,
                particle = particle,
                 sampleCount = ribbonSampleCount(shape, appearance, 3),
                lanes = 3,
                width = constantCurve(shape.thickness),
                durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
                styleAt = { _, _ -> ParticleStyle(particle) },
            ),
        )
    }

    private fun compileLightning(
        shape: VfxEditor2Shape.Lightning,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect = ParticleLightning.fromDirection(
        origin = transform.origin,
        direction = transform.forward,
        length = shape.length,
        particle = particle,
        hops = shape.hops,
        hopVariance = shape.jitter,
        density = (appearance.density * 4.0).roundToInt().coerceIn(1, 4),
        seed = shape.seed,
        durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
        style = ParticleStyle(particle),
    )

    private fun compileSpiral(
        shape: VfxEditor2Shape.Spiral,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect = ParticleSpiral(
        origin = transform.origin,
        axis = transform.forward,
        radius = shape.radius,
        curveAngle = 2.0 * PI,
        curves = shape.turns,
        axialLength = shape.length,
        angleOffset = Math.toRadians(shape.angleOffsetDegrees),
        durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
        reversed = shape.reverse,
        sampleCount = (sampleBudget(shape, appearance) - 1).coerceAtLeast(1),
        style = ParticleStyle(particle),
    )

    private fun compileHelix(
        shape: VfxEditor2Shape.Helix,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect {
        val phase = Math.toRadians(shape.phaseDegrees)
        return ParticleParametric(
            positionAt = { progress ->
                val angle = phase + progress * shape.turns * 2.0 * PI
                localPoint(
                    transform,
                    cos(angle) * shape.radius,
                    sin(angle) * shape.radius,
                    (progress - 0.5) * shape.length,
                )
            },
            particle = particle,
             sampleCount = (sampleBudget(shape, appearance) - 1).coerceAtLeast(1),
            durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
            styleAt = { ParticleStyle(particle) },
        )
    }

    private fun compileDisk(
        shape: VfxEditor2Shape.Disk,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect = filledCircle(
        transform,
        radius = shape.radius,
        innerRadius = shape.innerRadius,
        startDegrees = 0.0,
        endDegrees = 360.0,
        appearance = appearance,
        particle = particle,
    )

    private fun compileSector(
        shape: VfxEditor2Shape.Sector,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect = filledCircle(
        transform,
        radius = shape.radius,
        innerRadius = shape.innerRadius,
        startDegrees = -shape.angleDegrees / 2.0,
        endDegrees = shape.angleDegrees / 2.0,
        appearance = appearance,
        particle = particle,
    )

    private fun filledCircle(
        transform: ParticleTransform,
        radius: Double,
        innerRadius: Double,
        startDegrees: Double,
        endDegrees: Double,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect {
        val innerFactor = (innerRadius / radius.coerceAtLeast(1.0e-6)).coerceIn(0.0, 1.0)
        return ParticleCircle(
            center = transform.origin,
            radius = radius,
            axis1 = transform.right,
            axis2 = transform.up,
            filled = true,
            innerRadiusFactor = innerFactor,
            startDegrees = startDegrees,
            endDegrees = endDegrees,
            countPerMeter = circleDensity(
                radius,
                endDegrees - startDegrees,
                appearance.density * 8.0,
                filled = true,
                innerRadiusFactor = innerFactor,
                budget = VFX_EDITOR_2_MAX_SAMPLES_PER_EFFECT,
            ),
            includeEnd = endDegrees - startDegrees < 359.99,
            durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
            style = ParticleStyle(particle),
        )
    }

    private fun compileGrid(
        shape: VfxEditor2Shape.Grid,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect {
        val budget = sampleBudget(shape, appearance)
        val side = if (budget <= 1) 1 else minOf(shape.rows + 1, sqrt(budget.toDouble()).toInt().coerceAtLeast(2))
        val points = buildList {
            for (row in 0 until side) for (column in 0 until side) {
                val x = -shape.width / 2.0 + shape.width * column / (side - 1).coerceAtLeast(1)
                val y = -shape.height / 2.0 + shape.height * row / (side - 1).coerceAtLeast(1)
                add(localPoint(transform, x, y, 0.0))
            }
        }
        return ParticleMulti(points, style = ParticleStyle(particle), durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS)
    }

    private fun compileSphere(
        shape: VfxEditor2Shape.Sphere,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect = ParticleMulti(
        locations = fibonacciSphere(shape.radius, sampleBudget(shape, appearance)).map { transform.localPoint(it) },
        style = ParticleStyle(particle),
        durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
    )

    private fun compileOrb(
        shape: VfxEditor2Shape.Orb,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect = ParticleMulti(
        locations = solidSphere(shape.radius, minOf(shape.count, sampleBudget(shape, appearance)), shape.seed).map { transform.localPoint(it) },
        style = ParticleStyle(particle),
        durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
    )

    private fun compileDome(
        shape: VfxEditor2Shape.Dome,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect = ParticleMulti(
        locations = hemisphere(shape.radius, sampleBudget(shape, appearance), shape.direction).map { transform.localPoint(it) },
        style = ParticleStyle(particle),
        durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
    )

    private fun compileCylinder(
        shape: VfxEditor2Shape.Cylinder,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect {
        val count = minOf(shape.count, sampleBudget(shape, appearance))
        if (!shape.shell) {
            return ParticlePillar(
                base = transform.origin,
                height = shape.height,
                radius = shape.radius,
                count = count,
                axis = transform.up,
                seed = 42L,
                style = ParticleStyle(particle),
                durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
            )
        }
        return ParticleMulti(
            locations = cylinderShell(shape.radius, shape.height, count).map { transform.localPoint(it) },
            style = ParticleStyle(particle),
            durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
        )
    }

    private fun compileCone(
        shape: VfxEditor2Shape.Cone,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect = ParticleMulti(
        locations = coneSurface(shape.length, shape.radius, shape.angleDegrees, sampleBudget(shape, appearance)).map { transform.localPoint(it) },
        style = ParticleStyle(particle),
        durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
    )

    private fun compileBox(
        shape: VfxEditor2Shape.Box,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect {
        val mode = if (shape.mode == dev.projects.protocol.VfxEditor2BoxMode.EDGES) RectPrismMode.EDGES else RectPrismMode.FACES
        val base = ParticleRectPrism(
            cornerA = Pos(-shape.width / 2.0, -shape.height / 2.0, -shape.depth / 2.0),
            cornerB = Pos(shape.width / 2.0, shape.height / 2.0, shape.depth / 2.0),
            mode = mode,
            countPerMeter = 2.0,
            countPerMeterSquared = 1.5,
            durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
            style = ParticleStyle(particle),
        )
        return ParticleMulti(
            locations = base.points().take(sampleBudget(shape, appearance)).map { transform.localPoint(it) },
            style = ParticleStyle(particle),
            durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
        )
    }

    private fun compileTorus(
        shape: VfxEditor2Shape.Torus,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect = ParticleMulti(
        locations = torusPoints(shape.majorRadius, shape.tubeRadius, sampleBudget(shape, appearance)).map { transform.localPoint(it) },
        style = ParticleStyle(particle),
        durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
    )

    private fun compileStar(
        shape: VfxEditor2Shape.Star,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect = dev.projects.server.particle.ParticleFlower(
        center = transform.origin,
        petals = shape.points,
        radius = shape.radius,
        sharp = shape.sharpness >= 0.75,
        planeNormal = transform.forward,
        count = sampleBudget(shape, appearance),
        innerRadius = shape.innerRadius,
        style = ParticleStyle(particle),
        durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
    )

    private fun compileCross(
        shape: VfxEditor2Shape.Cross,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect {
        val angle = Math.toRadians(shape.angleDegrees)
        val first = Vec(cos(angle), sin(angle), 0.0)
        val second = Vec(-sin(angle), cos(angle), 0.0)
         val density = boundedDensity(shape.size, appearance.density * 10.0, VFX_EDITOR_2_MAX_SAMPLES_PER_EFFECT / 2 - 1)
        fun line(axis: Vec): ParticleLine = ParticleLine(
            start = localPoint(transform, -axis.x() * shape.size / 2.0, -axis.y() * shape.size / 2.0, 0.0),
            end = localPoint(transform, axis.x() * shape.size / 2.0, axis.y() * shape.size / 2.0, 0.0),
            particle = particle,
            countPerMeter = density,
            durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
            style = ParticleStyle(particle),
        )
        return ParticleBatch.of(line(first), line(second))
    }

    private fun compileShockwave(
        shape: VfxEditor2Shape.Shockwave,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect = object : ParticleEffect {
        override val durationTicks: Int = shape.durationTicks

        override fun emit(tick: Int, sink: ParticleSink) {
            if (tick !in 0 until durationTicks) return
            val progress = if (durationTicks == 1) 1.0 else tick.toDouble() / (durationTicks - 1)
            val radius = shape.startRadius + (shape.endRadius - shape.startRadius) * progress
            ParticleCircle(
                center = transform.origin,
                radius = radius,
                axis1 = transform.right,
                axis2 = transform.up,
                countPerMeter = circleDensity(radius, 360.0, appearance.density * 8.0, false, 0.0, VFX_EDITOR_2_MAX_SAMPLES_PER_EFFECT),
                includeEnd = false,
                durationTicks = 1,
                style = ParticleStyle(particle),
            ).emit(0, sink)
        }
    }

    private fun compileVortex(
        shape: VfxEditor2Shape.Vortex,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect = animatedHelix(
        radius = shape.radius,
        height = shape.height,
        turns = shape.turns,
        durationTicks = shape.durationTicks,
        direction = shape.direction,
        transform = transform,
        count = sampleBudget(shape, appearance),
        particle = particle,
    )

    private fun compileTornado(
        shape: VfxEditor2Shape.Tornado,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect = object : ParticleEffect {
        override val durationTicks: Int = shape.durationTicks

        override fun emit(tick: Int, sink: ParticleSink) {
            if (tick !in 0 until durationTicks) return
            val phase = tick.toDouble() / durationTicks * 2.0 * PI
            val count = sampleBudget(shape, appearance)
            val points = List(count) { index ->
                val progress = if (count == 1) 0.0 else index.toDouble() / (count - 1)
                val angle = phase + progress * shape.turns * 2.0 * PI
                val radius = shape.bottomRadius + (shape.topRadius - shape.bottomRadius) * progress
                localPoint(
                    transform,
                    cos(angle) * radius,
                    (progress - 0.5) * shape.height,
                    sin(angle) * radius,
                )
            }
            ParticleMulti(points, style = ParticleStyle(particle)).emit(0, sink)
        }
    }

    private fun animatedHelix(
        radius: Double,
        height: Double,
        turns: Double,
        durationTicks: Int,
        direction: VfxEditor2Direction,
        transform: ParticleTransform,
        count: Int,
        particle: Particle,
    ): ParticleEffect = object : ParticleEffect {
        override val durationTicks: Int = durationTicks

        override fun emit(tick: Int, sink: ParticleSink) {
            if (tick !in 0 until this.durationTicks) return
            val phase = tick.toDouble() / this.durationTicks * 2.0 * PI
            val sign = if (direction == VfxEditor2Direction.UP) 1.0 else -1.0
            val points = List(count) { index ->
                val progress = if (count == 1) 0.0 else index.toDouble() / (count - 1)
                val angle = phase + progress * turns * 2.0 * PI
                localPoint(transform, cos(angle) * radius, sign * (progress - 0.5) * height, sin(angle) * radius)
            }
            ParticleMulti(points, style = ParticleStyle(particle)).emit(0, sink)
        }
    }

    private fun compileFountain(
        shape: VfxEditor2Shape.Fountain,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect = object : ParticleEffect {
        override val durationTicks: Int = shape.durationTicks

        override fun emit(tick: Int, sink: ParticleSink) {
            if (tick !in 0 until durationTicks) return
            val count = minOf(shape.count, sampleBudget(shape, appearance))
            val points = List(count) { index ->
                val random = Random(index.toLong() * 31L + 17L)
                val phase = ((tick + index * 3) % (durationTicks * 2)).toDouble() / (durationTicks * 2 - 1).coerceAtLeast(1)
                val angle = random.nextDouble(0.0, 2.0 * PI)
                val spread = tan(Math.toRadians(shape.spreadDegrees)) * shape.height * 0.15 * sin(PI * phase)
                val radial = shape.radius * phase + spread
                localPoint(
                    transform,
                    cos(angle) * radial,
                    shape.height * sin(PI * phase),
                    sin(angle) * radial,
                )
            }
            ParticleMulti(points, style = ParticleStyle(particle)).emit(0, sink)
        }
    }

    private fun compileSphereBurst(
        shape: VfxEditor2Shape.SphereBurst,
        transform: ParticleTransform,
        particle: Particle,
    ): ParticleEffect = ParticleExplosion(
        center = transform.origin,
        radius = shape.spawnRadius,
        sphere = true,
        particle = particle,
        count = shape.count.coerceAtMost(VFX_EDITOR_2_MAX_SAMPLES_PER_EFFECT),
        speed = shape.speed.toFloat(),
        speedVariance = shape.variance.toFloat(),
        spawnOffset = shape.spawnRadius,
        seed = shape.seed,
        durationTicks = 1,
    )

    private fun compileConeBurst(
        shape: VfxEditor2Shape.ConeBurst,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect {
        val count = minOf(shape.count, sampleBudget(shape, appearance))
        val points = coneBurstPoints(shape.length, shape.radius, shape.angleDegrees, count, shape.seed)
        return object : ParticleEffect {
            override val durationTicks: Int = 1

            override fun emit(tick: Int, sink: ParticleSink) {
                if (tick != 0) return
                points.forEach { local ->
                    val position = transform.localPoint(local)
                    sink.spawn(
                        ParticleSpawn(
                            particle = particle,
                            position = position,
                            count = 1,
                            offset = transform.forward,
                            speed = shape.speed.toFloat(),
                            directional = true,
                        ),
                    )
                }
            }
        }
    }

    private fun directionalBurst(
        transform: ParticleTransform,
        count: Int,
        angleDegrees: Double,
        length: Double,
        speed: Double,
        particle: Particle,
        seed: Long,
    ): ParticleEffect {
        val directions = List(count.coerceAtMost(VFX_EDITOR_2_MAX_SAMPLES_PER_EFFECT)) { index ->
            val random = Random(seed + index)
            val angle = random.nextDouble(0.0, 2.0 * PI)
            val spread = tan(Math.toRadians(angleDegrees)) * sqrt(random.nextDouble())
            normalize(Vec(cos(angle) * spread, sin(angle) * spread, 1.0))
        }
        return object : ParticleEffect {
            override val durationTicks: Int = 1

            override fun emit(tick: Int, sink: ParticleSink) {
                if (tick != 0) return
                directions.forEach { localDirection ->
                    val direction = normalize(transform.localDirection(localDirection))
                    val position = localPoint(
                        transform,
                        direction.dot(transform.right) * length,
                        direction.dot(transform.up) * length,
                        direction.dot(transform.forward) * length,
                    )
                    sink.spawn(
                        ParticleSpawn(
                            particle = particle,
                            position = position,
                            count = 1,
                            offset = direction,
                            speed = speed.toFloat(),
                            directional = true,
                        ),
                    )
                }
            }
        }
    }

    private fun sampleBudget(shape: VfxEditor2Shape, appearance: dev.projects.protocol.VfxEditor2Appearance): Int =
        estimateVfxEditor2Samples(shape, appearance.density).coerceIn(1, VFX_EDITOR_2_MAX_SAMPLES_PER_EFFECT)

    private fun ribbonSampleCount(shape: VfxEditor2Shape, appearance: dev.projects.protocol.VfxEditor2Appearance, lanes: Int): Int =
        ((sampleBudget(shape, appearance) / lanes.coerceAtLeast(1)) - 1).coerceAtLeast(1)

    private fun boundedDensity(length: Double, requested: Double, budget: Int): Double =
         minOf(requested.coerceAtLeast(0.25), (budget - 1).coerceAtLeast(1).toDouble() / length.coerceAtLeast(0.25)).coerceAtLeast(0.25)

    private fun circleDensity(
        radius: Double,
        spanDegrees: Double,
        requested: Double,
        filled: Boolean,
        innerRadiusFactor: Double,
        budget: Int,
    ): Double {
        var density = requested.coerceAtLeast(0.25)
        repeat(10) {
            val circumference = radius * Math.toRadians(abs(spanDegrees)).coerceAtLeast(0.01)
            val segments = max(1, ceil(circumference * density).toInt())
            val radialSpan = if (filled) {
                (1.0 - innerRadiusFactor.coerceIn(0.0, 1.0)) * max(1.0, radius * density / 2.0)
            } else 1.0
            val rings = max(1, ceil(radialSpan).toInt())
             val total = (segments + 1L) * rings.toLong()
            if (total <= budget) return density
            density *= sqrt(budget.toDouble() / total.toDouble()).coerceIn(0.1, 0.9)
        }
        return density.coerceAtLeast(0.25)
    }

    private fun quadraticBezier(start: Point, control: Point, end: Point, t: Double): Point {
        val inverse = 1.0 - t
        return Pos(
            inverse * inverse * start.x() + 2.0 * inverse * t * control.x() + t * t * end.x(),
            inverse * inverse * start.y() + 2.0 * inverse * t * control.y() + t * t * end.y(),
            inverse * inverse * start.z() + 2.0 * inverse * t * control.z() + t * t * end.z(),
        )
    }

    private fun fibonacciSphere(radius: Double, count: Int): List<Point> = List(count.coerceAtLeast(1)) { index ->
        val progress = (index + 0.5) / count.coerceAtLeast(1)
        val y = 1.0 - 2.0 * progress
        val radial = sqrt((1.0 - y * y).coerceAtLeast(0.0))
        val angle = index * PI * (3.0 - sqrt(5.0))
        Pos(cos(angle) * radial * radius, y * radius, sin(angle) * radial * radius)
    }

    private fun solidSphere(radius: Double, count: Int, seed: Long): List<Point> = List(count.coerceAtLeast(1)) { index ->
        val random = Random(seed + index)
        val z = random.nextDouble(-1.0, 1.0)
        val angle = random.nextDouble(0.0, 2.0 * PI)
        val radial = sqrt((1.0 - z * z).coerceAtLeast(0.0)) * radius * random.nextDouble().pow(1.0 / 3.0)
        Pos(cos(angle) * radial, z * radius * random.nextDouble().pow(1.0 / 3.0), sin(angle) * radial)
    }

    private fun hemisphere(radius: Double, count: Int, direction: VfxEditor2Direction): List<Point> = List(count.coerceAtLeast(1)) { index ->
        val progress = (index + 0.5) / count.coerceAtLeast(1)
        val y = kotlin.math.abs(1.0 - 2.0 * progress) * if (direction == VfxEditor2Direction.UP) 1.0 else -1.0
        val radial = sqrt((1.0 - y * y).coerceAtLeast(0.0))
        val angle = index * PI * (3.0 - sqrt(5.0))
        Pos(cos(angle) * radial * radius, y * radius, sin(angle) * radial * radius)
    }

    private fun cylinderShell(radius: Double, height: Double, budget: Int): List<Point> {
        val around = max(8, sqrt(budget.toDouble()).toInt().coerceAtMost(32))
        val levels = max(2, (budget / around).coerceAtLeast(2))
        return buildList {
            for (level in 0 until levels) {
                val y = -height / 2.0 + height * level / (levels - 1).coerceAtLeast(1)
                for (index in 0 until around) {
                    val angle = 2.0 * PI * index / around
                    add(Pos(cos(angle) * radius, y, sin(angle) * radius))
                }
            }
        }.take(budget)
    }

    private fun coneSurface(length: Double, radius: Double, angleDegrees: Double, budget: Int): List<Point> {
        val effectiveRadius = minOf(radius, tan(Math.toRadians(angleDegrees)) * length)
        val around = max(8, sqrt(budget.toDouble()).toInt().coerceAtMost(32))
        val along = max(2, (budget / around).coerceAtLeast(2))
        return buildList {
            for (step in 0..along) {
                val progress = step.toDouble() / along
                val currentRadius = effectiveRadius * progress
                for (index in 0 until around) {
                    val angle = 2.0 * PI * index / around
                    add(Pos(cos(angle) * currentRadius, sin(angle) * currentRadius, progress * length))
                }
            }
        }.take(budget)
    }

    private fun torusPoints(majorRadius: Double, tubeRadius: Double, budget: Int): List<Point> {
        val majorSegments = max(12, sqrt(budget.toDouble() * 2.0).toInt().coerceAtMost(48))
        val tubeSegments = max(6, (budget / majorSegments).coerceAtLeast(6).coerceAtMost(24))
        return buildList {
            for (major in 0 until majorSegments) {
                val majorAngle = 2.0 * PI * major / majorSegments
                for (tube in 0 until tubeSegments) {
                    val tubeAngle = 2.0 * PI * tube / tubeSegments
                    val distance = majorRadius + tubeRadius * cos(tubeAngle)
                    add(Pos(cos(majorAngle) * distance, tubeRadius * sin(tubeAngle), sin(majorAngle) * distance))
                }
            }
        }.take(budget)
    }

    private fun coneBurstPoints(length: Double, radius: Double, angleDegrees: Double, count: Int, seed: Long): List<Point> =
        List(count.coerceAtLeast(1)) { index ->
            val random = Random(seed + index)
            val distance = random.nextDouble() * length
            val maxRadial = minOf(radius, tan(Math.toRadians(angleDegrees)) * distance)
            val radial = maxRadial * sqrt(random.nextDouble())
            val angle = random.nextDouble(0.0, 2.0 * PI)
            Pos(cos(angle) * radial, sin(angle) * radial, distance)
        }

    private fun effectFrame(
        origin: Point,
        direction: Vec,
        transform: dev.projects.protocol.VfxEditor2Transform,
    ): ParticleTransform {
        val base = ParticleTransform.fromDirection(origin, normalize(direction))
        val translatedOrigin = localPoint(
            base,
            transform.side,
            transform.height,
            transform.forward,
        )
        return base.copy(origin = translatedOrigin).rotate(
            yaw = transform.yaw,
            pitch = transform.pitch,
            roll = transform.roll,
        )
    }

    private fun localPoint(transform: ParticleTransform, right: Double, up: Double, forward: Double): Point =
        transform.origin.add(
            transform.right.x() * right + transform.up.x() * up + transform.forward.x() * forward,
            transform.right.y() * right + transform.up.y() * up + transform.forward.y() * forward,
            transform.right.z() * right + transform.up.z() * up + transform.forward.z() * forward,
        )

    private fun normalize(value: Vec): Vec = if (value.length() <= 1.0e-9) Vec(0.0, 0.0, 1.0) else value.mul(1.0 / value.length())
}

/** Checkpoint A's public entry point, now backed by the editable default composition. */
object VfxWorkbenchPreview {
    fun create(origin: Point, direction: Vec): ParticleEffect =
        create(origin, direction, defaultVfxEditor2Composition())

    fun create(origin: Point, direction: Vec, composition: VfxEditor2Composition): ParticleEffect =
        VfxWorkbenchCompiler.compile(composition, origin, direction)
}

/** Per-player handle bookkeeping for replacement and lifecycle cleanup. */
internal class VfxEditor2PreviewHandles(
    private val cancelHandle: (ParticleEffectHandle) -> Unit,
) {
    private val handles = mutableMapOf<UUID, ParticleEffectHandle>()

    val size: Int get() = handles.size

    fun replace(owner: UUID, handle: ParticleEffectHandle) {
        handles.remove(owner)?.let(cancelHandle)
        handles[owner] = handle
    }

    fun cancel(owner: UUID) {
        handles.remove(owner)?.let(cancelHandle)
    }

    fun remove(owner: UUID): ParticleEffectHandle? = handles.remove(owner)

    fun entries(): List<Pair<UUID, ParticleEffectHandle>> = handles.toList()
}

class VfxEditor2Runtime(
    private val scheduler: ParticleAnimationScheduler,
    private val particleManager: ParticleManager,
    private val send: (Player, ProtocolMessage) -> Unit,
) {
    private val previews = VfxEditor2PreviewHandles { handle -> scheduler.cancel(handle) }
    private val lastRequestIds = mutableMapOf<UUID, Long>()

    fun open(player: Player) {
        lastRequestIds[player.uuid] = -1L
        send(player, VfxEditor2Open("Ronin Q", defaultVfxEditor2Composition()))
        sendStatus(player, VfxEditor2StatusKind.READY, "Ready")
    }

    fun preview(player: Player, request: VfxEditor2PreviewStart) {
        val lastRequestId = lastRequestIds[player.uuid]
        if (lastRequestId != null && request.requestId <= lastRequestId) return
        lastRequestIds[player.uuid] = request.requestId
        previews.cancel(player.uuid)
        sendStatus(player, VfxEditor2StatusKind.PREVIEW_REQUESTED, "Preview requested")
        try {
            // Capture the server-known position/direction at the moment the packet arrives.
            val direction = normalize(player.position.direction())
            val previewOrigin = player.position.add(
                direction.x() * VFX_EDITOR_2_PREVIEW_DISTANCE,
                direction.y() * VFX_EDITOR_2_PREVIEW_DISTANCE + VFX_EDITOR_2_PREVIEW_HEIGHT,
                direction.z() * VFX_EDITOR_2_PREVIEW_DISTANCE,
            )
            val effectId = "editor2:preview:${player.uuid}"
            val sink = particleManager.sink(
                ParticleViewer(player.position, player),
                PlayerParticleSink(player),
                effectId,
            )
            val handle = scheduler.start(
                effect = VfxWorkbenchCompiler.compile(request.composition, previewOrigin, direction),
                sink = sink,
                id = effectId,
                onComplete = { sendStatus(player, VfxEditor2StatusKind.STOPPED, "Stopped") },
            )
            previews.replace(player.uuid, handle)
            sendStatus(player, VfxEditor2StatusKind.PLAYING, "Playing")
        } catch (error: RuntimeException) {
            previews.cancel(player.uuid)
            sendStatus(player, VfxEditor2StatusKind.ERROR, error.message ?: "Preview start failed")
        }
    }

    fun stop(player: Player) {
        previews.cancel(player.uuid)
        sendStatus(player, VfxEditor2StatusKind.STOPPED, "Stopped")
    }

    fun tick() {
        previews.entries().forEach { (playerId, handle) ->
            if (handle.state == ParticleEffectState.COMPLETED || handle.state == ParticleEffectState.CANCELLED) {
                previews.remove(playerId)
            }
        }
    }

    fun disconnect(player: Player) {
        previews.cancel(player.uuid)
        lastRequestIds.remove(player.uuid)
    }

    internal fun activePreviewCount(): Int = previews.size

    private fun sendStatus(player: Player, kind: VfxEditor2StatusKind, message: String) {
        send(player, VfxEditor2Status(kind, message.take(160)))
    }

    private fun normalize(value: Vec): Vec = if (value.length() <= 1.0e-9) Vec(0.0, 0.0, 1.0) else value.mul(1.0 / value.length())
}
