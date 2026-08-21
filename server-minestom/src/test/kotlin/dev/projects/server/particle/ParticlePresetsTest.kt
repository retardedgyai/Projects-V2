package dev.projects.server.particle

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParticlePresetsTest {
    private val context = ParticlePresetParameters.at(Pos.ZERO, Vec(0.3, 0.4, 0.8))

    @Test
    fun `catalogue has unique ids and the complete 35 preset arsenal`() {
        assertTrue(ParticlePresetRegistry.all.size >= 35)
        assertEquals(ParticlePresetRegistry.all.size, ParticlePresetRegistry.all.map { it.id }.toSet().size)
        assertTrue(ParticlePresetRegistry.all.all { it.id.startsWith("projects:") })
        assertTrue(ParticlePresetRegistry.list("combat").size >= 15)
    }

    @Test
    fun `every preset instantiates and emits finite geometry`() {
        ParticlePresetRegistry.all.forEach { preset ->
            val effect = preset.create(context)
            val sink = RecordingParticleSink()
            repeat(effect.durationTicks) { tick -> effect.emit(tick, sink) }
            assertTrue(effect.durationTicks >= 1, preset.id)
            assertTrue(sink.spawns.all { spawn ->
                spawn.position.x().isFinite() && spawn.position.y().isFinite() && spawn.position.z().isFinite() &&
                    spawn.offset.x().isFinite() && spawn.offset.y().isFinite() && spawn.offset.z().isFinite() &&
                    spawn.speed.isFinite()
            }, preset.id)
        }
    }

    @Test
    fun `numeric values clamp and invalid values fall back safely`() {
        val preset = requireNotNull(ParticlePresetRegistry["projects:combat/slash_light"])
        val effect = preset.create(context.with("length", Double.NaN).with("duration", 9999.0).with("colorPrimary", 0x1ffffff))
        assertTrue(effect.durationTicks <= 40)
        val parsed = parseParticlePresetOverrides(preset, arrayOf("length=999", "colorPrimary=#00ff00"))
        assertEquals(null, parsed.error)
        val sink = RecordingParticleSink()
        effect.emit(0, sink)
        assertTrue(sink.spawns.isNotEmpty())
    }

    @Test
    fun `same seed produces the same preset output`() {
        val preset = requireNotNull(ParticlePresetRegistry["projects:combat/lightning_strike"])
        fun emit(): List<ParticleSpawn> {
            val sink = RecordingParticleSink()
            preset.create(context.with("seed", 77.0)).emit(0, sink)
            return sink.spawns
        }
        assertEquals(emit(), emit())
    }

    @Test
    fun `override parser rejects unknown and malformed parameters`() {
        val preset = requireNotNull(ParticlePresetRegistry["projects:combat/slash_light"])
        assertNotNull(parseParticlePresetOverrides(preset, arrayOf("length=2")))
        assertTrue(parseParticlePresetOverrides(preset, arrayOf("unknown=2")).error != null)
        assertTrue(parseParticlePresetOverrides(preset, arrayOf("length=NaN")).error != null)
    }
}
