package dev.projects.server

import dev.projects.protocol.SlashEditorParameters
import dev.projects.server.particle.RecordingParticleSink
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.particle.Particle
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlashEditorTest {
    @Test
    fun `drafts survive a new store and reject unsafe names`() {
        val directory = Files.createTempDirectory("slash-editor-test")
        val file = directory.resolve("drafts.json")
        val parameters = SlashEditorParameters(color = 0x123456)

        assertTrue(SlashDraftStore(file).save("deep blue", parameters))
        val reloaded = SlashDraftStore(file)
        assertEquals(parameters, reloaded.load("deep blue"))
        assertFalse(reloaded.save("../escape", parameters))
        assertFalse(reloaded.save("", parameters))
    }

    @Test
    fun `draft store enforces the maximum number of drafts`() {
        val file = Files.createTempFile("slash-editor-test", ".json")
        val store = SlashDraftStore(file)
        repeat(16) { index -> assertTrue(store.save("draft-$index", SlashEditorParameters())) }
        assertFalse(store.save("draft-16", SlashEditorParameters()))
        assertTrue(store.save("draft-0", SlashEditorParameters(color = 0xabcdef)))
    }

    @Test
    fun `preview emits finite positions with the requested color`() {
        val color = 0x123456
        val effect = SlashEditorPreview.create(Pos.ZERO, Vec(0.0, 0.0, 1.0), SlashEditorParameters(color = color))
        val sink = RecordingParticleSink()
        repeat(effect.durationTicks) { effect.emit(it, sink) }

        assertTrue(sink.spawns.isNotEmpty())
        assertTrue(sink.spawns.all { spawn ->
            spawn.position.x().isFinite() && spawn.position.y().isFinite() && spawn.position.z().isFinite()
        })
        val dustColors = sink.spawns.mapNotNull { spawn ->
            (spawn.particle as? Particle.Dust)?.color()?.let { textColor ->
                textColor.red() shl 16 or (textColor.green() shl 8) or textColor.blue()
            }
        }.toSet()
        assertEquals(setOf(color), dustColors)
    }
}
