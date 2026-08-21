package dev.projects.server.particle

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.particle.Particle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParticlePrimitivesTest {
    private val origin = Pos(0.0, 0.0, 0.0)
    private val end = Pos(0.0, 0.0, 2.0)

    @Test
    fun `line includes exact endpoints and distributes frames once`() {
        val line = ParticleLine(origin, end, countPerMeter = 2.0, durationTicks = 3)
        assertEquals(origin, line.points().first())
        assertEquals(end, line.points().last())

        val sink = RecordingParticleSink()
        repeat(line.durationTicks) { line.emit(it, sink) }
        assertEquals(line.points().size, sink.spawns.size)
        assertEquals(sink.spawns.map { it.position }.toSet().size, sink.spawns.size)
    }

    @Test
    fun `line include rules and minimum density are respected`() {
        val line = ParticleLine(origin, end, countPerMeter = 0.0, minimumCountPerMeter = 2.0, includeStart = false, includeEnd = false)
        assertTrue(line.points().isNotEmpty())
        assertFalse(line.points().contains(origin))
        assertFalse(line.points().contains(end))
    }

    @Test
    fun `circle supports arbitrary axes and arc endpoints`() {
        val circle = ParticleCircle(
            center = origin,
            radius = 2.0,
            axis1 = Vec(0.0, 0.0, 1.0),
            axis2 = Vec(0.0, 1.0, 0.0),
            startDegrees = 0.0,
            endDegrees = 90.0,
            countPerMeter = 3.0,
            includeStart = true,
            includeEnd = true,
        )
        val points = circle.points()
        assertEquals(Pos(0.0, 0.0, 2.0), points.first())
        assertEquals(0.0, points.last().x(), absoluteTolerance = 1.0e-9)
        assertEquals(2.0, points.last().y(), absoluteTolerance = 1.0e-9)
        assertEquals(0.0, points.last().z(), absoluteTolerance = 1.0e-9)
        assertTrue(points.all { it.x().isFinite() && it.y().isFinite() && it.z().isFinite() })
    }

    @Test
    fun `parametric and bezier preserve endpoints`() {
        val parametric = ParticleParametric({ t -> Pos(t, t * t, 0.0) }, sampleCount = 4)
        assertEquals(origin, parametric.points().first().first)
        assertEquals(Pos(1.0, 1.0, 0.0), parametric.points().last().first)

        val bezier = ParticleBezier(origin, end, listOf(Pos(1.0, 1.0, 0.0)))
        assertEquals(origin, bezier.pointAt(0.0))
        assertEquals(end, bezier.pointAt(1.0))
        assertEquals(Pos(0.5, 0.5, 0.5), bezier.pointAt(0.5))
    }

    @Test
    fun `spiral reverses radius progression and explosion directions are normalized`() {
        val normal = ParticleSpiral(origin, Vec(0.0, 1.0, 0.0), 2.0, 6.28, 1.0, sampleCount = 4)
        val reversed = ParticleSpiral(origin, Vec(0.0, 1.0, 0.0), 2.0, 6.28, 1.0, reversed = true, sampleCount = 4)
        assertEquals(0.0, normal.points().first().distance(origin))
        assertEquals(2.0, reversed.points().first().distance(origin.add(0.0, 1.0, 0.0)), absoluteTolerance = 1.0e-6)

        val sink = RecordingParticleSink()
        ParticleExplosion(origin, count = 8, speed = 1.0f, seed = 9L).emit(0, sink)
        assertTrue(sink.spawns.all { it.speed >= 0f && it.position.x().isFinite() })
        assertTrue(sink.spawns.all { it.directional && it.count == 0 && kotlin.math.abs(it.offset.length() - 1.0) < 1.0e-9 })
    }

    @Test
    fun `explosion defaults to center and accepts explicit spawn offset`() {
        val sink = RecordingParticleSink()
        ParticleExplosion(origin, radius = 2.0, count = 1, seed = 1L).emit(0, sink)
        assertEquals(origin, sink.spawns.single().position)
        ParticleExplosion(origin, radius = 2.0, count = 1, spawnOffset = 2.0, seed = 1L).emit(0, sink)
        assertEquals(2.0, sink.spawns.last().position.distance(origin), absoluteTolerance = 1.0e-9)
    }

    @Test
    fun `lightning shape is fixed per instance and reaches endpoints`() {
        val first = ParticleLightning(origin, end, hops = 5, density = 2, seed = 11L)
        val second = ParticleLightning(origin, end, hops = 5, density = 2, seed = 11L)
        assertEquals(first.points(), second.points())
        assertEquals(origin, first.points().first())
        assertEquals(end, first.points().last())
    }

    @Test
    fun `colors clamp and interpolate`() {
        assertEquals(0xff0000, lerpColor(0xff0000, 0x0000ff, 0.0))
        assertEquals(0x0000ff, lerpColor(0xff0000, 0x0000ff, 1.0))
        assertEquals(0x80007f, lerpColor(0xff0000, 0x0000ff, 0.5))
        assertTrue(dust(255, 30, 10) is Particle.Dust)
    }

    @Test
    fun `slash center has highest middle progress and scheduler ticks once`() {
        val progress = mutableListOf<Double>()
        val slash = ParticleGeometry.drawParticleLineSlash(origin, Vec(0.0, 0.0, 1.0), 0.0, 2.0, 0.5, 2) { _, middle, _, _ ->
            progress += middle
            ParticleStyle(Particle.END_ROD)
        }
        val sink = RecordingParticleSink()
        val scheduler = ParticleAnimationScheduler()
        scheduler.start(slash, sink)
        scheduler.tick()
        val afterFirstTick = sink.spawns.size
        scheduler.tick()
        assertTrue(afterFirstTick > 0)
        assertTrue(progress.max() > progress.min())
        assertEquals(0, scheduler.activeAnimationCount)

        val path = mutableListOf<Double>()
        ParticleGeometry.drawParticleLineSlash(origin, Vec(0.0, 0.0, 1.0), 0.0, 2.0, 0.5, 1) { _, _, end, _ ->
            path += end
            ParticleStyle(Particle.END_ROD)
        }.emit(0, RecordingParticleSink())
        assertEquals(listOf(0.0, 0.25, 0.5, 0.75, 1.0), path)
        assertEquals(path.sorted(), path)
    }

    @Test
    fun `periodic thins counts without skipping the wrapped timeline`() {
        val sink = RecordingParticleSink()
        val effect = object : ParticleEffect {
            override val durationTicks: Int = 4
            override fun emit(tick: Int, sink: ParticleSink) {
                sink.spawn(ParticleSpawn(Particle.END_ROD, origin))
            }
        }
        val periodic = ParticlePeriodic(effect, 0.25)
        repeat(4) { periodic.emit(it, sink) }
        assertEquals(1, sink.spawns.size)
        assertEquals(1, sink.spawns.single().count)
    }
}
