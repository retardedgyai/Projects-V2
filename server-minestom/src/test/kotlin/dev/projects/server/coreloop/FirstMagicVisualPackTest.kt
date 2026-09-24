package dev.projects.server.coreloop

import dev.projects.server.coreloop.ui.CoreMenuCanvas
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FirstMagicVisualPackTest {
    @Test fun allWorkshopModelsAndIconsArePackedAndNamespaced() {
        val loader = javaClass.classLoader
        val paths = assertNotNull(loader.getResourceAsStream("core-ui-pack/index.txt"))
            .bufferedReader().use { it.readLines().toSet() }
        val art = listOf("ember", "tide", "gale", "stone", "moonbell", "ember_moss", "hollow_crystal",
            "warm_ore", "tidewing_feather", "withered_core", "desk", "distiller", "jar", "journal", "return", "sealed")
        for (name in art) {
            val model = "assets/projects/models/first_magic/icon_$name.json"
            val definition = "assets/projects/items/first_magic/icon_$name.json"
            val png = "assets/projects/textures/item/first_magic/$name.png"
            assertTrue(model in paths && definition in paths && png in paths, "Missing $name asset")
            assertTrue(assertNotNull(loader.getResourceAsStream("core-ui-pack/$model"))
                .bufferedReader().use { it.readText() }.contains("projects:item/first_magic/$name"))
        }
        val models = listOf("research_desk", "research_desk_dormant", "crude_distiller", "jar_shelf", "star_chart") +
            listOf("ember", "tide", "gale", "stone").flatMap { aspect ->
                listOf("empty", "low", "high").map { fill -> "jar_${aspect}_$fill" }
            }
        for (machine in models) {
            assertTrue("assets/projects/items/first_magic/$machine.json" in paths)
            val path = "assets/projects/models/first_magic/$machine.json"
            val model = assertNotNull(loader.getResourceAsStream("core-ui-pack/$path"))
                .bufferedReader().use { it.readText() }
            for (texture in Regex("projects:item/first_magic/model/([a-z_]+)").findAll(model).map { it.groupValues[1] })
                assertTrue("assets/projects/textures/item/first_magic/model/$texture.png" in paths,
                    "$machine references a missing $texture texture")
        }
        for (aspect in listOf("ember", "tide", "gale", "stone"))
            for (fill in listOf("empty", "low", "high"))
                assertTrue("assets/projects/items/first_magic/jar_${aspect}_$fill.json" in paths)
        for (page in listOf("desk", "distiller", "jars", "journal")) {
            assertTrue("assets/projects/font/first_magic_canvas_$page.json" in paths)
            assertTrue("assets/projects/textures/gui/first_magic/${page}_0.png" in paths)
            assertTrue("assets/projects/textures/gui/first_magic/${page}_1.png" in paths)
        }
    }

    @Test fun workshopCanvasUsesItsOwnArtworkAndHasEveryJapaneseGlyph() {
        val labels = "観測手順素材修復分析記録帳抽出工程蒸留炉加熱冷却保存瓶熾火潮風石沈殿休眠稼働共鳴余白第一頁"
        assertTrue(CoreMenuCanvas.missingCharacters(labels).isEmpty())
        val title = CoreMenuCanvas("観測工房・研究机", CoreMenuCanvas.Background.FIRST_MAGIC_DESK).render()
        fun all(component: Component): List<Component> = listOf(component) + component.children().flatMap(::all)
        val font = all(title).filterIsInstance<TextComponent>().firstOrNull { it.content() == "\uE600" }?.style()?.font()
        assertEquals("projects:first_magic_canvas_desk", font.toString())
    }
}
