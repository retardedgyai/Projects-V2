package dev.projects.server.coreloop

import dev.projects.webui.Polish05FontMetrics
import java.util.zip.ZipInputStream
import kotlin.test.*

class GrimoireFlowTest {
    private fun bytes(path: String) = assertNotNull(javaClass.getResourceAsStream(path), path).use { it.readBytes() }
    private val art = GrimoireFlow.Art(bytes("/polish05/grimoire.json"), bytes("/polish05/font-map.json"))
    private var closed = false
    private fun flow(progress: GrimoireProgress = GrimoireSample.midway()) = GrimoireFlow(art, progress) { closed = true }

    @Test fun `every sprite the screen can draw is shipped`() {
        val files = mutableSetOf<String>()
        ZipInputStream(bytes("/polish05/grimoire.zip").inputStream()).use { zip ->
            while (true) { val e = zip.nextEntry ?: break; files += e.name }
        }
        assertTrue("assets/projects_ui_polish05/font/grimoire.json" in files)
        assertTrue(files.all { it.startsWith("assets/projects_ui_polish05/") }, "private namespace only")
        val drawn = GrimoireBook.ENTRIES.flatMap { listOfNotNull(it.icon, it.hero) } + GrimoireBook.BRANCHES.map { it.second } +
            listOf("i16_coin", "i16_ink", "i16_box", "i16_check", "i16_open", "i16_see", "i16_place", "i16_craft", "i16_get",
                "v3_glow", "v3_ring", "v3_round_tl", "v3_ring10_tl", "v3_ring14_br")
        for (name in drawn) assertNotNull(art.sprites[name], name)
        for (s in art.sprites.values) assertTrue(s.width <= 256 && s.height <= 256, "glyphs above 256px do not render")
    }

    @Test fun `every text uses a font page that has its glyphs`() {
        val f = flow()
        for (action in listOf("node:n1", "node:n4", "claim", "node:n11", "node:n12", "hover:n-n9-hit", "hover:n-n11-hit", "hover:n-n5-hit",
                "tab:1", "tab:6", "tab:0", "node:n5", "achieve", "node:n8", "node:n9")) {
            if (action.startsWith("hover:")) f.hover(action.removePrefix("hover:")) else f.action(action)
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
        assertFalse(f.action("pin"), "locked entries cannot be pinned")
        f.action("node:n5"); assertTrue(f.action("achieve"))
        f.action("node:n6"); assertTrue(f.action("achieve"))
        f.action("node:n7")
        assertTrue(f.scene().nodes.any { it.action == "pin" })
        assertTrue(f.action("pin"))
        assertTrue(f.scene().nodes.any { it.id == "n-n7-p-tl" }, "pinned marker on the map")
        // Branch tabs show the sealed branch; close leaves.
        assertTrue(f.action("tab:2"))
        assertTrue(f.scene().nodes.any { it.id == "seal-ring" })
        assertTrue(f.action("close")); assertTrue(closed)
    }

    @Test fun `progress outlives the book being closed`() {
        val progress = GrimoireSample.midway()
        flow(progress).apply { action("node:n4"); action("claim"); action("node:n5"); action("achieve"); action("node:n6"); action("pin") }
        val again = flow(progress)
        again.action("node:n4")
        assertTrue(again.scene().nodes.none { it.action == "claim" }, "the claim is remembered")
        assertTrue(again.scene().nodes.any { it.id == "n-n6-p-tl" }, "the pin is remembered")
        assertTrue(again.scene().nodes.any { it.id == "n-n5-ring" } && "n5" in progress.done)
        // 最初から: only the first entry can be worked on.
        val start = flow(GrimoireSample.start())
        start.action("node:n1")
        assertTrue(start.scene().nodes.any { it.action == "achieve" })
        start.action("node:n2"); assertFalse(start.action("achieve"))
    }

    @Test fun `a claimed reward lands in the wallet`() {
        var now = 0L
        val progress = GrimoireSample.midway()
        val f = GrimoireFlow(art, progress, { now }) {}
        fun value(id: String) = f.scene().nodes.first { it.id == id }.text
        f.action("node:n4"); assertTrue(f.action("claim"))
        assertEquals(12_500, progress.silver, "the server-side wallet changes at once")
        assertEquals("12,480", value("p-silver-v"), "the pill waits for the icon to land")
        assertTrue(f.scene().nodes.any { it.id == "fx-slot-t" })
        now = 600; assertTrue(f.scene().nodes.any { it.id == "fx-c0" })
        now = 1200; val mid = value("p-silver-v"); assertTrue(mid != "12,480" && mid != "12,500", mid)
        now = 1600; assertEquals("12,500", value("p-silver-v"))
        now = 5000; assertTrue(f.scene().nodes.none { it.id.startsWith("fx-") })
        // Ink goes to its aspect; the pill shows the total.
        f.action("node:n5"); f.action("achieve"); assertTrue(f.action("claim"))
        assertEquals(18, progress.ink["aqua"]); now = 9000; assertEquals("40", value("p-ink-v"))
    }

    @Test fun `hover tells what an entry is and why it is locked`() {
        val f = flow()
        f.hover("n-n9-hit")
        val tip = f.scene().nodes.filter { it.id.startsWith("tip-") }.map { it.text }
        assertTrue("杖" in tip && "未解放" in tip && "「相を溜める」を終えると開く" in tip, tip.toString())
        f.hover("n-n11-hit")
        assertTrue(f.scene().nodes.any { it.id == "tip-t" && it.text == "まだ見えない" })
        assertTrue(f.scene().nodes.none { it.id == "n-n11-l" }, "? entries stay unnamed on the map")
        assertTrue(f.scene().nodes.any { it.id == "n-n9-l" }, "locked entries are named, as in the panel")
        f.action("node:n9")
        assertEquals("「相を溜める」を終えると開く", f.scene().nodes.first { it.id == "e-lock-t" }.text)
    }

    @Test fun `counted goals show a number and a bar, the count leaves out side entries`() {
        val f = flow()
        f.action("node:n8")
        val nodes = f.scene().nodes
        assertEquals("7 / 12", nodes.first { it.id == "e-g0-n" }.text)
        assertEquals("相を12種類観察する", nodes.first { it.id == "e-g0" }.text)
        assertTrue(nodes.any { it.id == "e-g0-fill-c" })
        assertEquals("4 / 11", nodes.first { it.id == "m-count" }.text)
        assertEquals(listOf("序章", "1章", "2章", "3章", "終章"), GrimoireBook.CHAPTERS.map { it.first })
        val icons = GrimoireBook.ENTRIES.groupBy { it.icon }.filterValues { it.size > 1 }
        assertTrue(icons.isEmpty(), "each entry has its own icon: $icons")
    }

    @Test fun `fills sit behind the glyphs they belong to`() {
        // UiRenderer draws glyphs 5 depth steps in front of fills; a fill meant to cover a sprite needs a higher depth.
        val nodes = flow().scene().nodes
        val disc = nodes.first { it.id == "n-n5-disc-tl" }
        val ring = nodes.first { it.id == "n-n5-ring" }
        val icon = nodes.first { it.id == "n-n5-i" }
        assertTrue(disc.depth * 0.005 < ring.depth * 0.005 + 0.025)
        assertTrue(ring.depth <= icon.depth)
    }

    @Test fun `the scene stays inside the 800x480 display`() {
        for (n in flow().scene().nodes.filter { !it.id.startsWith("backdrop") && !it.id.startsWith("cloud") }) {
            assertTrue(n.box.x >= -0.5 && n.box.y >= -0.5 && n.box.x + n.box.w <= 800.5 && n.box.y + n.box.h <= 480.5, "${n.id} ${n.box}")
        }
    }
}
