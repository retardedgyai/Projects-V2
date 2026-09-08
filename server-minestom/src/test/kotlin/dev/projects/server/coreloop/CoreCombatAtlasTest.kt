package dev.projects.server.coreloop

import com.google.gson.JsonParser
import kotlin.test.*

/** PNG existence and valid model JSON do not imply that the client stitches the sprite. */
class CoreCombatAtlasTest {
    private val root = "/core-ui-pack/"
    private fun json(path: String) = assertNotNull(javaClass.getResourceAsStream(root + path), path)
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }

    @Test fun `every custom combat sprite is indexed and explicitly included in the native item atlas`() {
        val index = assertNotNull(javaClass.getResourceAsStream(root + "index.txt"))
            .bufferedReader().use { it.readLines().toSet() }
        val atlas = "assets/minecraft/atlases/items.json"
        assertTrue(atlas in index, "A PNG outside textures/item is invisible without an atlas source")
        val sources = json(atlas).getAsJsonArray("sources")
        assertEquals(1, sources.size(), "Only add the combat directory; do not filter or replace Vanilla sprites")
        val source = sources.single().asJsonObject
        assertEquals("minecraft:directory", source["type"].asString)
        assertEquals("combat_vfx", source["source"].asString)
        assertEquals("combat_vfx/", source["prefix"].asString)
        val models = index.filter { it.startsWith("assets/projects/models/combat_vfx/") }
        assertTrue(models.size > 3000)
        val checked = mutableSetOf<String>()
        for (model in models) for ((_, value) in json(model).getAsJsonObject("textures").entrySet()) {
            val sprite = if (value.isJsonPrimitive) value.asString else value.asJsonObject["sprite"].asString
            if (!sprite.startsWith("projects:")) continue
            assertTrue(sprite.startsWith("projects:combat_vfx/"), "$model: uncovered sprite $sprite")
            val path = "assets/projects/textures/${sprite.substringAfter(':')}.png"
            assertTrue(path in index, "$model: unbundled $path")
            assertNotNull(javaClass.getResourceAsStream(root + path), path).use {
                assertContentEquals(byteArrayOf(-119,80,78,71,13,10,26,10), it.readNBytes(8))
            }
            checked += sprite
        }
        assertTrue(checked.size > 100, "Check animated families, not just the base mesh")
    }
}
