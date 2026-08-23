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
    fun `legacy drafts load with safe lane defaults`() {
        val file = Files.createTempFile("slash-editor-legacy", ".json")
        Files.writeString(file, """
            {"schemaVersion":1,"drafts":[{"name":"legacy","parameters":{"originY":1.2,"forwardOffset":1.7,"length":5.0,"arcSpan":160.0,"curvature":0.35,"tilt":12.0,"yaw":0.0,"width":0.8,"particleSize":0.28,"spacing":0.18,"durationTicks":8,"color":1190875,"targetDistance":5.0}}]}
        """.trimIndent())
        val loaded = SlashDraftStore(file).load("legacy")
        assertEquals(1, loaded?.laneCount)
        assertEquals(0.18, loaded?.laneSpacing)
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

    @Test
    fun `lane count is independent from thickness`() {
        val oneLane = SlashEditorPreview.create(Pos.ZERO, Vec(0.0, 0.0, 1.0), SlashEditorParameters(laneCount = 1, width = 1.5))
        val threeLanes = SlashEditorPreview.create(Pos.ZERO, Vec(0.0, 0.0, 1.0), SlashEditorParameters(laneCount = 3, laneSpacing = 0.5, width = 0.05))
        val oneSink = RecordingParticleSink()
        val threeSink = RecordingParticleSink()
        oneLane.emit(0, oneSink)
        threeLanes.emit(0, threeSink)
        assertTrue(threeSink.spawns.size > oneSink.spawns.size)
    }
}
