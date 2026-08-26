package dev.projects.server

import dev.projects.protocol.VfxEditor2Composition
import dev.projects.protocol.VfxEditor2EffectType
import dev.projects.protocol.defaultVfxEditor2Effect
import dev.projects.protocol.isVfxEditor2Instant
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VfxEditor2CompositionStoreTest {
    @Test
    fun `all editor 2 shapes survive save and reload without solo state`() {
        val directory = Files.createTempDirectory("vfx-editor2-store-test")
        val file = directory.resolve("compositions.json")
        try {
            val store = VfxEditor2CompositionStore(file)
            VfxEditor2EffectType.entries.forEachIndexed { index, type ->
                val effect = defaultVfxEditor2Effect(type, index.toLong() + 1L).copy(
                    name = "Effect $index",
                    startTick = index,
                    durationTicks = if (isVfxEditor2Instant(type)) 1 else 2,
                    solo = index == 0,
                )
                val composition = VfxEditor2Composition(
                    effects = listOf(effect),
                    name = "shape_$index",
                    timelineLengthTicks = 40,
                )
                assertTrue(store.save(composition).success, type.name)
                assertEquals(composition.withoutSolo(), store.load(composition.name))
            }
            assertEquals(VfxEditor2EffectType.entries.size, store.list().size)
            assertTrue(Files.readString(file).contains("\"schemaVersion\":1"))

            val reloaded = VfxEditor2CompositionStore(file)
            assertEquals(store.list(), reloaded.list())
            assertEquals(
                store.load("shape_22"),
                reloaded.load("shape_22"),
            )

            Files.writeString(file, "{\"schemaVersion\":999,\"compositions\":[]}")
            assertTrue(VfxEditor2CompositionStore(file).list().isEmpty())
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `store overwrites atomically and reports overwrite`() {
        val directory = Files.createTempDirectory("vfx-editor2-store-overwrite")
        val file = directory.resolve("compositions.json")
        try {
            val store = VfxEditor2CompositionStore(file)
            val first = VfxEditor2Composition(
                listOf(defaultVfxEditor2Effect(VfxEditor2EffectType.ARC_SLASH, 1L)),
                name = "shared",
            )
            val second = first.copy(
                effects = listOf(first.effects.single().copy(startTick = 4, durationTicks = 8)),
            )
            assertTrue(store.save(first).success)
            val result = store.save(second)
            assertTrue(result.success)
            assertTrue(result.overwritten)
            assertEquals(second.withoutSolo(), store.load("shared"))
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }
}
