package dev.projects.server

import dev.projects.protocol.VfxEditor2Composition
import dev.projects.protocol.VfxEditor2Effect
import dev.projects.protocol.VfxEditor2EffectType
import dev.projects.protocol.VfxEditor2Shape
import dev.projects.server.particle.ParticleAnimationScheduler
import dev.projects.server.particle.ParticleDelay
import dev.projects.server.particle.ParticleEffectState
import dev.projects.server.particle.RecordingParticleSink
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.particle.Particle
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VfxEditor2Test {
    @Test
    fun `fixed preview emits visible red and white particles`() {
        val effect = VfxWorkbenchPreview.create(Pos.ZERO, Vec(0.0, 0.0, 1.0))
        val sink = RecordingParticleSink()
        repeat(effect.durationTicks) { effect.emit(it, sink) }

        assertTrue(sink.spawns.isNotEmpty())
        assertTrue(sink.spawns.all { spawn ->
            spawn.position.x().isFinite() && spawn.position.y().isFinite() && spawn.position.z().isFinite()
        })
        assertTrue(sink.spawns.any { it.particle.isDustColor(0xc51f3a) })
        assertTrue(sink.spawns.any { it.particle.isDustColor(0xffffff) })
    }

    @Test
    fun `compiler emits particles for every checkpoint B effect type`() {
        val composition = VfxEditor2Composition(
            listOf(
                VfxEditor2Effect(1L, "Arc", VfxEditor2EffectType.ARC_SLASH, VfxEditor2Shape.ArcSlash()),
                VfxEditor2Effect(2L, "Line", VfxEditor2EffectType.STRAIGHT_SLASH, VfxEditor2Shape.StraightSlash()),
                VfxEditor2Effect(3L, "Ring", VfxEditor2EffectType.RING, VfxEditor2Shape.Ring()),
                VfxEditor2Effect(4L, "Burst", VfxEditor2EffectType.BURST, VfxEditor2Shape.Burst()),
            ),
        )
        val effect = VfxWorkbenchCompiler.compile(composition, Pos.ZERO, Vec(0.0, 0.0, 1.0))
        val sink = RecordingParticleSink()
        repeat(effect.durationTicks) { tick -> effect.emit(tick, sink) }

        assertTrue(sink.spawns.size > 50)
        assertTrue(sink.spawns.any { it.particle.isDustColor(0xffffff) })
        assertTrue(sink.spawns.all { spawn ->
            spawn.position.x().isFinite() && spawn.position.y().isFinite() && spawn.position.z().isFinite()
        })
    }

    @Test
    fun `compiler honors hidden and solo effect visibility`() {
        val arc = VfxEditor2Effect(1L, "Arc", VfxEditor2EffectType.ARC_SLASH, VfxEditor2Shape.ArcSlash())
        val ring = VfxEditor2Effect(2L, "Ring", VfxEditor2EffectType.RING, VfxEditor2Shape.Ring())
        val hidden = VfxEditor2Composition(listOf(arc, ring.copy(enabled = false)))
        val solo = VfxEditor2Composition(listOf(arc.copy(solo = true), ring))

        assertEquals(1, hidden.visibleEffects().size)
        assertEquals(1, solo.visibleEffects().size)
        assertTrue(hidden.visibleEffects().single().type == VfxEditor2EffectType.ARC_SLASH)
        assertTrue(solo.visibleEffects().single().type == VfxEditor2EffectType.ARC_SLASH)
    }

    @Test
    fun `play replacement and stop cleanup cancel only the current handle`() {
        val scheduler = ParticleAnimationScheduler()
        val sessions = VfxEditor2PreviewHandles { handle -> scheduler.cancel(handle) }
        val owner = UUID.randomUUID()
        val sink = RecordingParticleSink()
        val first = scheduler.start(ParticleDelay(4), sink, id = "first")
        val second = scheduler.start(ParticleDelay(4), sink, id = "second")

        sessions.replace(owner, first)
        sessions.replace(owner, second)
        assertEquals(ParticleEffectState.CANCELLED, first.state)
        assertEquals(1, sessions.size)

        sessions.cancel(owner)
        assertEquals(ParticleEffectState.CANCELLED, second.state)
        assertEquals(0, sessions.size)
    }

    @Test
    fun `completed handles can be removed without accumulating`() {
        val scheduler = ParticleAnimationScheduler()
        val sessions = VfxEditor2PreviewHandles { handle -> scheduler.cancel(handle) }
        val owner = UUID.randomUUID()
        val handle = scheduler.start(ParticleDelay(1), RecordingParticleSink(), id = "complete")
        sessions.replace(owner, handle)

        scheduler.tick()
        assertEquals(ParticleEffectState.COMPLETED, handle.state)
        sessions.remove(owner)
        assertEquals(0, sessions.size)
    }

    private fun Particle.isDustColor(expected: Int): Boolean {
        val dust = this as? Particle.Dust ?: return false
        val color = dust.color()
        return color.red() == (expected shr 16 and 0xff) &&
            color.green() == (expected shr 8 and 0xff) &&
            color.blue() == (expected and 0xff)
    }
}
