package dev.projects.server.coreloop

import com.google.gson.JsonParser
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertTrue

class ClassSelectSceneTest {
    /** The camp writes in the menu's MaruMinya, which only carries the letters of its text sources: a word with a letter
     *  outside them shows as a blank box in the world (build_menu_dialog_assets.py TEXT_SOURCES). */
    @Test fun `every word in the camp is in the menu font`() {
        val metrics = requireNotNull(javaClass.classLoader.getResourceAsStream("polish05/menu-metrics.json")) { "menu-metrics.json missing" }
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val letters = metrics.getAsJsonObject("sizes").getAsJsonObject("4").getAsJsonObject("styles")
            .getAsJsonObject("m6").getAsJsonObject("advances").keySet().map { it.toInt() }.toSet()
        val scene = ClassSelectScene()
        val plain = PlainTextComponentSerializer.plainText()
        for (job in CoreClass.entries) {
            val words = plain.serialize(scene.card(job)) + plain.serialize(scene.hint()) + "開発中"
            val missing = words.codePoints().toArray().filter { it >= 0x80 && it !in letters }.map { String(Character.toChars(it)) }.distinct()
            assertTrue(missing.isEmpty(), "${job.displayName}: not in the menu font: $missing")
        }
    }
}
