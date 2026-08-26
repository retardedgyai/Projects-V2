package dev.projects.server

import dev.projects.protocol.defaultVfxEditor2Composition
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec

class VfxEditor2BindingTest {
    @Test
    fun `catalog exposes unique ronin and starweaver targets`() {
        val ids = VfxEditor2TargetCatalog.targets.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            setOf(
                "ronin.aa.main",
                "ronin.q.main",
                "ronin.w1.main",
                "ronin.w2.main",
                "ronin.w3.main",
                "ronin.e.main",
                "ronin.r.main",
            ),
            ids.filter { it.startsWith("ronin.") }.toSet(),
        )
        assertEquals(12, ids.count { it.startsWith("starweaver.") })
        assertTrue(VfxEditor2TargetCatalog.targets.all { it.classLabel.isNotBlank() && it.skillLabel.isNotBlank() })
    }

    @Test
    fun `binding store applies overwrites clears and persists names only`() {
        val directory = Files.createTempDirectory("vfx-editor2-bindings")
        val file = directory.resolve("bindings.json")
        try {
            val store = VfxEditor2BindingStore(file)
            assertTrue(store.apply("ronin.q.main", "first") { it == "first" }.success)
            assertEquals(mapOf("ronin.q.main" to "first"), store.snapshot())
            assertTrue(Files.readString(file).contains("\"ronin.q.main\":\"first\""))
            assertFalse(Files.readString(file).contains("effects"))

            assertTrue(store.apply("ronin.q.main", "second") { it == "second" }.success)
            assertEquals("second", store.bindingFor("ronin.q.main"))

            val reloaded = VfxEditor2BindingStore(file)
            assertEquals(mapOf("ronin.q.main" to "second"), reloaded.snapshot())
            assertTrue(reloaded.clear("ronin.q.main").success)
            assertEquals(emptyMap(), VfxEditor2BindingStore(file).snapshot())
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `binding store rejects unknown target and missing composition`() {
        val directory = Files.createTempDirectory("vfx-editor2-binding-validation")
        val file = directory.resolve("bindings.json")
        try {
            val store = VfxEditor2BindingStore(file)
            assertFalse(store.apply("ronin.unknown.main", "valid") { true }.success)
            assertFalse(store.apply("ronin.q.main", "missing") { false }.success)
            assertTrue(store.snapshot().isEmpty())
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `malformed or unknown binding schema safely falls back to empty`() {
        val directory = Files.createTempDirectory("vfx-editor2-binding-malformed")
        val file = directory.resolve("bindings.json")
        val warnings = mutableListOf<String>()
        try {
            Files.writeString(file, "not json")
            assertTrue(VfxEditor2BindingStore(file, warn = warnings::add).snapshot().isEmpty())
            assertTrue(warnings.any { it.contains("malformed") })

            Files.writeString(file, "{\"schemaVersion\":999,\"bindings\":{\"ronin.q.main\":\"saved\"}}")
            assertTrue(VfxEditor2BindingStore(file, warn = warnings::add).snapshot().isEmpty())
            assertTrue(warnings.any { it.contains("malformed") || it.contains("Unknown") })
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `resolver falls back for dangling binding and resolves the current saved composition`() {
        val directory = Files.createTempDirectory("vfx-editor2-resolver")
        val bindingFile = directory.resolve("bindings.json")
        val compositionFile = directory.resolve("compositions.json")
        val warnings = mutableListOf<String>()
        try {
            val compositions = VfxEditor2CompositionStore(compositionFile)
            val bindings = VfxEditor2BindingStore(bindingFile)
            val resolver = VfxEditor2SkillVfxResolver(bindings, compositions, warnings::add)
            assertNull(resolver.resolve("ronin.q.main", Pos.ZERO, Vec(0.0, 0.0, 1.0)))

            val first = defaultVfxEditor2Composition().copy(name = "runtime_test", timelineLengthTicks = 12)
            assertTrue(compositions.save(first).success)
            assertTrue(bindings.apply("ronin.q.main", "runtime_test") { compositions.load(it) != null }.success)
            assertEquals(12, resolver.resolve("ronin.q.main", Pos.ZERO, Vec(0.0, 0.0, 1.0))?.durationTicks)

            val updated = first.copy(timelineLengthTicks = 30)
            assertTrue(compositions.save(updated).success)
            assertEquals(30, resolver.resolve("ronin.q.main", Pos.ZERO, Vec(0.0, 0.0, 1.0))?.durationTicks)

            Files.writeString(bindingFile, "{\"schemaVersion\":1,\"bindings\":{\"ronin.q.main\":\"gone\"}}")
            val danglingBindings = VfxEditor2BindingStore(bindingFile)
            val danglingResolver = VfxEditor2SkillVfxResolver(danglingBindings, compositions, warnings::add)
            assertNull(danglingResolver.resolve("ronin.q.main", Pos.ZERO, Vec(0.0, 0.0, 1.0)))
            assertTrue(warnings.any { it.contains("dangling") })
        } finally {
            Files.deleteIfExists(bindingFile)
            Files.deleteIfExists(compositionFile)
            Files.deleteIfExists(directory)
        }
    }
}
