package dev.projects.server

import dev.projects.protocol.VfxEditor2Composition
import dev.projects.protocol.VfxEditor2Layer
import dev.projects.protocol.VfxEditor2Particle
import dev.projects.protocol.VfxEditor2Shape
import dev.projects.server.particle.RecordingParticleSink
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.particle.Particle
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VfxEditor2Test {
    @Test
    fun `compiler supports all v1 shapes with finite particle output`() {
        VfxEditor2Shape.entries.forEachIndexed { index, shape ->
            val composition = VfxEditor2Composition(
                durationTicks = 8,
                layers = listOf(
                    VfxEditor2Layer(
                        id = index + 1,
                        name = "Shape $index",
                        shapeType = shape,
                        particleType = if (shape == VfxEditor2Shape.BURST) VfxEditor2Particle.ELECTRIC_SPARK else VfxEditor2Particle.DUST,
                    ),
                ),
            )
            val effect = VfxEditor2Compiler.compile(composition, Pos.ZERO, Vec(0.0, 0.0, 1.0))
            val sink = RecordingParticleSink()
            repeat(effect.durationTicks) { effect.emit(it, sink) }
            assertTrue(sink.spawns.isNotEmpty(), "shape $shape should emit particles")
            assertTrue(sink.spawns.all { it.position.x().isFinite() && it.position.y().isFinite() && it.position.z().isFinite() })
        }
    }

    @Test
    fun `disabled and solo layers are excluded by the compiler`() {
        val composition = VfxEditor2Composition(
            durationTicks = 8,
            layers = listOf(
                VfxEditor2Layer(id = 1, name = "Disabled", enabled = false, color = 0xff0000),
                VfxEditor2Layer(id = 2, name = "Solo", solo = true, color = 0x00ff00),
                VfxEditor2Layer(id = 3, name = "Other", color = 0x0000ff),
            ),
        )
        val sink = RecordingParticleSink()
        val effect = VfxEditor2Compiler.compile(composition, Pos.ZERO, Vec(0.0, 0.0, 1.0))
        repeat(effect.durationTicks) { effect.emit(it, sink) }
        val colors = sink.spawns.mapNotNull { (it.particle as? Particle.Dust)?.color() }
        assertFalse(colors.any { it.red() == 255 && it.green() == 0 })
        assertFalse(colors.any { it.red() == 0 && it.green() == 0 && it.blue() == 255 })
        assertTrue(colors.any { it.red() == 0 && it.green() == 255 })
    }

    @Test
    fun `editor 2 drafts round trip without touching slash drafts`() {
        val directory = Files.createTempDirectory("vfx-editor2-test")
        val file = directory.resolve("drafts.json")
        val composition = VfxEditor2Composition(name = "ronin_q")
        assertTrue(VfxEditor2DraftStore(file).save(composition))
        val reloaded = VfxEditor2DraftStore(file)
        assertEquals(composition, reloaded.load("ronin_q"))
        assertEquals(listOf("ronin_q"), reloaded.list())
        assertTrue(Files.readString(file).contains("schemaVersion"))
    }
}
