package dev.projects.server.particle

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.particle.Particle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParticleEngineV2Test {
    @Test
    fun `transform round trips vertical direction with orthonormal basis`() {
        val transform = ParticleTransform.fromDirection(Pos.ZERO, Vec(0.0, 1.0, 0.0))
        assertEquals(1.0, transform.forward.length(), 1.0e-9)
        assertEquals(0.0, transform.forward.dot(transform.right), 1.0e-9)
        val local = Vec(1.2, -0.4, 2.0)
        val roundTrip = transform.worldPoint(transform.localPoint(local))
        assertEquals(local.x(), roundTrip.x(), 1.0e-9)
        assertEquals(local.y(), roundTrip.y(), 1.0e-9)
        assertEquals(local.z(), roundTrip.z(), 1.0e-9)
    }

    @Test
    fun `follow anchor samples current position and invalidates safely`() {
        var point: Point? = Pos.ZERO
        val anchor = ParticleAnchor.follow({ point })
        assertEquals(Pos.ZERO, anchor.position())
        point = Pos(2.0, 3.0, 4.0)
        assertEquals(point, anchor.position())
        point = null
        assertFalse(anchor.sample().valid)
    }

    @Test
    fun `curves expose easing endpoints and monotonic samples`() {
        val curve = KeyframeCurve.double(CurveKeyframe(0.0, 0.0), CurveKeyframe(1.0, 10.0), easing = Easing.EASE_IN_OUT_QUAD)
        assertEquals(0.0, curve.sample(0.0))
        assertEquals(10.0, curve.sample(1.0))
        assertTrue((0..20).map { curve.sample(it / 20.0) }.zipWithNext().all { it.first <= it.second })
    }

    @Test
    fun `sequence timings and handle lifecycle are exact`() {
        val origin = Pos.ZERO
        val sink = RecordingParticleSink()
        val effect = sequence {
            play(PartialParticle(Particle.END_ROD, origin))
            waitTicks(1)
            play(PartialParticle(Particle.END_ROD, origin.add(1.0, 0.0, 0.0)))
        }
        val scheduler = ParticleAnimationScheduler()
        val handle = scheduler.start(effect, sink, id = "timing")
        handle.pause()
        scheduler.tick()
        assertEquals(0, handle.elapsedTicks)
        handle.resume()
        repeat(effect.durationTicks) { scheduler.tick() }
        assertEquals(ParticleEffectState.COMPLETED, handle.state)
        assertEquals(0, scheduler.activeAnimationCount)
        assertEquals(2, sink.spawns.size)
    }

    @Test
    fun `emitter seed and ribbon vertical path stay deterministic and finite`() {
        val anchor = ParticleAnchor.fixed(Pos.ZERO, Vec(0.0, 1.0, 0.0))
        fun emit(): List<ParticleSpawn> {
            val sink = RecordingParticleSink()
            ParticleEmitter(anchor, particle = Particle.END_ROD, shape = SpawnShape.SPHERE, particlesPerTick = 8, seed = 7L).emit(0, sink)
            return sink.spawns
        }
        assertEquals(emit(), emit())
        val ribbon = ParticleRibbon({ Pos(0.0, it * 3.0, 0.0) }, sampleCount = 8, lanes = 3, width = constantCurve(1.0))
        assertTrue(ribbon.points().all { it.x().isFinite() && it.y().isFinite() && it.z().isFinite() })
    }

    @Test
    fun `trail resets history after teleport`() {
        var point = Pos.ZERO
        val anchor = ParticleAnchor.follow({ point })
        val trail = ParticleTrail(anchor, teleportDistance = 2.0, durationTicks = 3)
        val sink = RecordingParticleSink()
        trail.emit(0, sink)
        point = Pos(1.0, 0.0, 0.0)
        trail.emit(1, sink)
        assertTrue(trail.history.size >= 2)
        point = Pos(10.0, 0.0, 0.0)
        trail.emit(2, sink)
        assertEquals(1, trail.history.size)
    }
}
