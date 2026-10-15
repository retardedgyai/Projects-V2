package dev.projects.server.coreloop

import dev.projects.webui.Polish05FontMetrics
import java.util.zip.ZipInputStream
import kotlin.test.*

class GrimoireFlowTest {
    private val art = GrimoireFlow.Art(assertNotNull(javaClass.getResourceAsStream("/polish05/grimoire.json")).use { it.readBytes() })
    private var closed = false
    private fun flow() = GrimoireFlow(art) { closed = true }

    @Test fun `every sprite the screen can draw ships in the grimoire font`() {
        val files = mutableSetOf<String>()
        ZipInputStream(assertNotNull(javaClass.getResourceAsStream("/polish05/grimoire.zip"))).use { zip ->
            while (true) { val e = zip.nextEntry ?: break; files += e.name }
        }
        assertTrue("assets/projects_ui_polish05/font/grimoire.json" in files)
        assertTrue(files.all { it.startsWith("assets/projects_ui_polish05/") }, "private namespace only")
        for (name in listOf("frame", "page", "div") + listOf("trunk", "koku", "chu", "sho", "ren", "chi", "mad").map { "neb_$it" })
            assertNotNull(art.layouts[name], name)
        val drawn = GrimoireFlow.ENTRIES.flatMap { listOfNotNull(it.icon, it.hero) } + GrimoireFlow.BRANCHES.map { it.second } +
            listOf("node_done", "node_open", "node_glow", "node_locked", "node_side", "node_goal", "node_hidden",
                "tab", "tab_on", "seal", "i16_coin", "i16_ink", "i16_box", "i16_check", "i16_open", "i16_see", "i16_place", "i16_craft", "i16_get")
        for (name in drawn) assertNotNull(art.sprites[name], name)
        for (s in art.sprites.values) assertTrue(s.width <= 256 && s.height <= 256, "glyphs above 256px do not render")
    }

    @Test fun `every text uses a font page that has its glyphs`() {
        val f = flow()
        for (action in listOf("node:n1", "node:n4", "node:n11", "node:n12", "tab:1", "tab:6", "tab:0", "node:n5", "achieve", "node:n8")) {
            f.action(action)
            for (node in f.scene().nodes.filter { it.text.isNotEmpty() }) {
                val page = assertNotNull(node.style["font-family"]).substringAfter(':')
                assertTrue(Polish05FontMetrics.has(page), "missing page $page")
                for (c in node.text) if (c.code > 32) assertTrue(Polish05FontMetrics.advance(c.toString(), page) > 0, "'$c' in ${node.id}")
            }
        }
    }

    @Test fun `quest flow - select, achieve in the world, claim, pin`() {
        val f = flow()
        assertTrue(f.scene().nodes.any { it.action == "node:n5" })
        // n4 is done and still owes its reward.
        assertTrue(f.action("node:n4"))
        assertTrue(f.scene().nodes.any { it.action == "claim" })
        assertTrue(f.action("claim"))
        assertFalse(f.action("claim"), "a reward is claimed once")
        assertTrue(f.scene().nodes.none { it.action == "claim" })
        // Achieving the distiller and the jar opens 相を溜める.
        assertTrue(f.action("node:n7"))
        assertFalse(f.action("achieve"), "locked entries cannot be achieved")
        f.action("node:n5"); assertTrue(f.action("achieve"))
        f.action("node:n6"); assertTrue(f.action("achieve"))
        f.action("node:n7")
        assertTrue(f.scene().nodes.any { it.action == "pin" })
        assertTrue(f.action("pin"))
        assertTrue(f.scene().nodes.any { it.id == "n-n7-p" }, "pinned marker on the map")
        // Branch tabs show the sealed page; close leaves.
        assertTrue(f.action("tab:2"))
        assertTrue(f.scene().nodes.any { it.id == "seal" })
        assertTrue(f.action("close")); assertTrue(closed)
    }

    @Test fun `the scene stays inside the 800x480 display`() {
        for (n in flow().scene().nodes) {
            assertTrue(n.box.x >= -0.5 && n.box.y >= -0.5 && n.box.x + n.box.w <= 800.5 && n.box.y + n.box.h <= 480.5, "${n.id} ${n.box}")
        }
    }
}
