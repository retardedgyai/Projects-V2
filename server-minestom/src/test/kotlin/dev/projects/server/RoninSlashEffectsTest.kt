package dev.projects.server

import dev.projects.server.particle.ParticleCategory
import dev.projects.server.particle.RecordingParticleSink
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoninSlashEffectsTest {
    private val origin = Pos(0.0, 1.0, 0.0)
    private val direction = Vec(0.0, 0.0, 1.0)

    @Test
    fun `Q emits layered player facing slash particles`() {
        val effect = RoninSlashEffects.create(RoninSlashEffect.Q, origin, direction, seed = 11L)
        val sink = RecordingParticleSink()

        repeat(effect.durationTicks) { tick -> effect.emit(tick, sink) }

        assertEquals(6, effect.durationTicks)
        assertTrue(sink.spawns.isNotEmpty())
        assertTrue(sink.spawns.any { spawn -> spawn.category == ParticleCategory.OWN_ACTIVE })
        assertTrue(sink.spawns.all { spawn ->
            listOf(spawn.position.x(), spawn.position.y(), spawn.position.z()).all(Double::isFinite)
        })
    }

    @Test
    fun `Tempest stays staggered and bounded to a finite particle cost`() {
        val effect = RoninSlashEffects.create(RoninSlashEffect.TEMPEST_SEQUENCE, origin, direction, seed = 19L)
        val perTick = (0 until effect.durationTicks).map { tick ->
            RecordingParticleSink().also { sink -> effect.emit(tick, sink) }.spawns.size
        }

        assertEquals(16, effect.durationTicks)
        assertTrue(perTick.all { it > 0 })
        assertTrue(perTick.maxOrNull()!! <= 160)
        assertTrue(perTick.sum() <= 1500)
    }
}
