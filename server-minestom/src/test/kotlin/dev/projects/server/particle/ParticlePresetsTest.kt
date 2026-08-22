package dev.projects.server.particle

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import kotlin.test.Test
import kotlin.math.cos
import kotlin.math.sin
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
            assertTrue(sink.spawns.isNotEmpty(), preset.id)
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
    fun `twin blades swing emits a finite ribbon with visible width`() {
        val preset = requireNotNull(ParticlePresetRegistry["projects:class/twin_blades/aa_swing"])
        val sink = RecordingParticleSink()
        preset.create(context.with("angle", 35.0).with("length", 2.4).with("duration", 3.0)).emit(0, sink)

        val (_, right, up) = basis(context.direction)
        val angle = Math.toRadians(35.0)
        val slashDirection = right.mul(cos(angle)).add(up.mul(sin(angle)))
        val distances = sink.spawns.map { spawn ->
            val relative = Vec(spawn.position.x(), spawn.position.y(), spawn.position.z())
            relative.sub(slashDirection.mul(relative.dot(slashDirection))).length()
        }
        assertTrue(distances.maxOrNull()!! >= 0.12)
    }

    @Test
    fun `twin blades swing advances along a crescent instead of replaying one line`() {
        val preset = requireNotNull(ParticlePresetRegistry["projects:class/twin_blades/aa_swing"])
        val effect = preset.create(context.with("angle", 35.0).with("duration", 3.0))
        val first = RecordingParticleSink().also { effect.emit(0, it) }.spawns.map { it.position }
        val last = RecordingParticleSink().also { effect.emit(2, it) }.spawns.map { it.position }
        fun centroid(points: List<net.minestom.server.coordinate.Point>) = points.reduce { a, b ->
            a.add(b.x(), b.y(), b.z())
        }.let { sum -> sum.div(points.size.toDouble()) }
        assertTrue(centroid(first).distance(centroid(last)) > 0.1)
    }

    @Test
    fun `twin blades swing mirrors its hand-side origin`() {
        val positive = twinBladesArcPoint(Pos.ZERO, context.direction, 35.0, 2.1, 0.0)
        val negative = twinBladesArcPoint(Pos.ZERO, context.direction, -35.0, 2.1, 0.0)
        assertTrue(positive.distance(negative) > 0.3)
    }

    @Test
    fun `override parser rejects unknown and malformed parameters`() {
        val preset = requireNotNull(ParticlePresetRegistry["projects:combat/slash_light"])
        assertNotNull(parseParticlePresetOverrides(preset, arrayOf("length=2")))
        assertTrue(parseParticlePresetOverrides(preset, arrayOf("unknown=2")).error != null)
        assertTrue(parseParticlePresetOverrides(preset, arrayOf("length=NaN")).error != null)
    }
}
