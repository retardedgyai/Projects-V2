package dev.projects.server.coreloop.ui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.TextDecoration
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoreUiTest {
    private fun plain(component: Component): String = (component as? TextComponent)?.content().orEmpty() + component.children().joinToString("") { plain(it) }
    private fun children(component: Component): List<Component> = listOf(component) + component.children().flatMap(::children)
    @Test fun `pack declined shows locked class skill and weaving charges in Japanese`() {
        val state=CoreHudState(100.0,100.0,100.0,skills=listOf(CoreHudSkill(CoreUiIcon.DASH,"2",0.0,4.0,artIndex=9,unlocked=false)),charges=3)
        val text=plain(CoreUiComponents.hud(state,false))
        assertTrue(text.contains("2:未解放")); assertTrue(text.contains("蓄積 3/3"))
        assertFalse(text.any { it.code in 0xE000..0xF8FF })
        assertEquals(CoreHudLayout.LOCKED,CoreHudLayout.skillVisual(state.skills.single(),100.0).frame)
    }
    private val model = CoreTooltipModel("開拓者の大剣", CoreUiRarity.EPIC, 4, 28, "大剣", listOf(
        CoreTooltipStat("攻撃力", "+42", CoreUiIcon.ATTACK)), listOf(CoreTooltipAffix("鋭刃", "攻撃力 +12%", "8〜15%", 57, "IV")), 4)

    @Test fun `plain tooltip preserves Japanese details without any private glyphs`() {
        val output = CoreUiTooltip.render(model, false)
        val text = plain(output.title) + output.lore.joinToString("\n") { plain(it) }
        assertTrue(text.contains("開拓者の大剣")); assertTrue(text.contains("EPIC"))
        assertTrue(text.contains("品質 57%")); assertTrue(text.contains("範囲 8〜15%"))
        assertTrue(text.contains("アイテムレベル 28")); assertTrue(text.contains("内部Tier T4"))
        assertFalse(text.any { it.code in 0xE000..0xF8FF })
        assertFalse(text.contains("Shift"))
    }

    @Test fun `packed title centers while Japanese text stays in default font`() {
        val output = CoreUiTooltip.render(model, true)
        assertTrue(children(output.title).any { it.style().font() == CoreUiComponents.SPACE_FONT })
        val heading = output.lore.first()
        assertEquals(TextDecoration.State.TRUE, heading.decoration(TextDecoration.BOLD))
        for (component in listOf(output.title, *output.lore.toTypedArray()).flatMap(::children)) {
            val content = (component as? TextComponent)?.content().orEmpty()
            if (content.any { it.code in 0x3000..0x9FFF || it in 'A'..'z' }) {
                assertEquals(CoreUiComponents.DEFAULT_FONT, component.style().font())
            }
        }
    }

    @Test fun `HUD has three skills and bars without corrupting the plain fallback`() {
        val state = CoreHudState(70.0, 100.0, 50.0, skills = listOf(
            CoreHudSkill(CoreUiIcon.DASH, "2", 0.0, 4.0, 15),
            CoreHudSkill(CoreUiIcon.SLAM, "3", 3.1, 7.0, 25),
            CoreHudSkill(CoreUiIcon.WHIRL, "4", 8.0, 11.0, 35)))
        val fallback = plain(CoreUiComponents.hud(state, false))
        assertTrue(fallback.contains("HP 70/100")); assertTrue(fallback.contains("3:4秒"))
        assertFalse(fallback.any { it.code in 0xE000..0xF8FF })
        val packed = plain(CoreUiComponents.hud(state, true))
        assertTrue(packed.any { it.code in 0xE400..0xE415 }); assertTrue(packed.any { it.code in 0xE420..0xE435 })
        assertTrue(packed.any { it.code in 0xE440..0xE455 })
    }

    @Test fun `inventory titles fit their frame using bold Japanese pixel width`() {
        val title = "刻印工房 — 装備と刻印石の詳細を確認する"
        val packed = CoreUiComponents.inventoryTitle(title, true)
        val heading = children(packed).filterIsInstance<TextComponent>()
            .single { it.style().font() == CoreUiComponents.DEFAULT_FONT }.content()
        assertTrue(heading.endsWith("…"))
        assertTrue(CoreUiComponents.width(heading, bold = true) <= 158)
        assertEquals("刻印工房", CoreUiComponents.trimWidth("刻印工房", 158, bold = true))
        val fallback = plain(CoreUiComponents.inventoryTitle(title, false))
        assertTrue(CoreUiComponents.width(fallback) <= 158)
        assertFalse(fallback.any { it.code in 0xE000..0xF8FF })
    }

    @Test fun `width trimming accounts for ellipsis and never splits a Unicode codepoint`() {
        val title = "\uD840\uDC0B\uD840\uDC0B\uD840\uDC0B"
        val trimmed = CoreUiComponents.trimWidth(title, 20, bold = true)
        assertEquals("\uD840\uDC0B…", trimmed)
        assertEquals(2, trimmed.codePointCount(0, trimmed.length))
        assertTrue(CoreUiComponents.width(trimmed, bold = true) <= 20)
        assertEquals("", CoreUiComponents.trimWidth("長い", 3, bold = true))
    }

    @Test fun `pack bundling is deterministic and overrides only player heart and food sprites`() {
        val first = CoreUiPackServer.bundle()
        assertContentEquals(first, CoreUiPackServer.bundle())
        val paths = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(first)).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; paths += entry.name; zip.closeEntry() }
        }
        assertTrue("pack.mcmeta" in paths)
        assertTrue("assets/projects/font/core_icons.json" in paths)
        assertEquals(CoreUiPackPolicy.vanillaOverrides, paths.filter { it.startsWith("assets/minecraft/") }.toSet())
        assertFalse(paths.any { it.startsWith("assets/minecraft/font/") })
        assertEquals(paths.size, paths.distinct().size)
    }

    @Test fun `generated item models reference sprites covered by the stock item atlas`() {
        val loader = CoreUiPackServer::class.java.classLoader
        val paths = assertNotNull(loader.getResourceAsStream("core-ui-pack/index.txt"))
            .bufferedReader().use { it.readLines() }
        val models = paths.filter { it.startsWith("assets/projects/models/core_ui/") }
        assertEquals(CoreUiIcon.entries.size + 1 + 9, models.size, "Base icons, nine additional class skills and the invisible filler must be covered")
        for (path in models) {
            val json = assertNotNull(loader.getResourceAsStream("core-ui-pack/$path"))
                .bufferedReader().use { it.readText() }
            val texture = assertNotNull(Regex("\"layer0\"\\s*:\\s*\"([^\"]+)\"").find(json)).groupValues[1]
            assertTrue(texture.startsWith("projects:item/core_ui/"), "GUI PNGs are not in Vanilla's item atlas: $path")
            val texturePath = "assets/projects/textures/${texture.substringAfter(':')}.png"
            assertTrue(texturePath in paths)
            assertNotNull(loader.getResourceAsStream("core-ui-pack/$texturePath")).use { stream ->
                assertContentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), stream.readNBytes(8))
            }
        }
    }

    @Test fun `local optional pack server serves only the generated bundle`() {
        val oldPort = System.getProperty("projects.ui.port")
        System.setProperty("projects.ui.port", "0")
        try {
            val server = assertNotNull(CoreUiPackServer.start())
            server.use {
                val connection = server.uri.toURL().openConnection() as HttpURLConnection
                connection.connectTimeout = 2_000; connection.readTimeout = 2_000
                assertEquals(200, connection.responseCode)
                assertContentEquals(CoreUiPackServer.bundle(), connection.inputStream.use { it.readBytes() })
                val missing = server.uri.resolve("/not-a-file").toURL().openConnection() as HttpURLConnection
                missing.connectTimeout = 2_000; missing.readTimeout = 2_000
                assertEquals(404, missing.responseCode)
                connection.disconnect(); missing.disconnect()
            }
        } finally {
            if (oldPort == null) System.clearProperty("projects.ui.port") else System.setProperty("projects.ui.port", oldPort)
        }
    }
}
