package dev.projects.server.coreloop

import java.util.UUID
import com.google.gson.Gson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PassiveTreeFlowTest {
    private val art = PassiveTreeFlow.Art(requireNotNull(javaClass.classLoader.getResourceAsStream("polish05/passive-tree.json")).use { it.readBytes() })
    private var account = CoreAccount(UUID.randomUUID(), silver = 10_000, journey = CoreJourney.legacy(CoreClass.MAGE))
    private val sent = mutableListOf<CoreAction>()
    private val flow = PassiveTreeFlow(art, { account }, act = { action, _, done -> sent += action; done(true) }, leave = {})
    private val scale = 800.0 / 1920.0
    private val top = (480.0 - 1080.0 * scale) / 2

    /** Points the cursor at a tree node through the same scene transform the screen uses, measured from a lit badge. */
    private fun pointAt(node: Int, from: Int = CorePassiveTree.start(CoreClass.MAGE)) {
        val scene = flow.scene()
        val face = scene.nodes.firstOrNull { it.id == "f$from" }
        assertNotNull(face, "the reference node is drawn lit")
        // Badge centre in scene units, then offset by the node's position relative to it.
        val sx = face.box.x + face.box.w / 2; val sy = face.box.y + face.box.h / 2
        val s = art.spots[from]; val n = art.spots[node]
        val zoom = face.box.w / scale / face.sprite!!.width
        flow.pointer(sx + (n.x - s.x) * zoom * scale, sy + (n.y - s.y) * zoom * scale)
    }

    @Test fun `the art matches the server tree and every sprite fits a glyph`() {
        assertEquals(CorePassiveTree.nodes.size, art.spots.size)
        assertTrue(art.sprites.values.all { it.width <= 256 && it.height <= 256 })
        assertEquals(CorePassiveTree.nodes.sumOf { n -> n.links.count { it > n.id } }, art.edges.size)
        assertTrue(CorePassiveTree.nodes.all { it.id in art.infos })
    }

    @Test fun `the screen opens on the class start and stays inside the scene`() {
        val scene = flow.scene()
        assertTrue(scene.nodes.any { it.id.startsWith("t-") })
        assertTrue(scene.nodes.any { it.action == "tree" } && scene.nodes.any { it.action == "close" })
        assertTrue(scene.nodes.size < 400, "only what is on screen is drawn: ${scene.nodes.size}")
    }

    @Test fun `first click selects, second click takes the path`() {
        val target = CorePassiveTree.nodes.first { it.kind == CorePassiveKind.NOTABLE &&
            (CorePassiveTree.pathTo(CoreClass.MAGE, emptySet(), it.id)?.size ?: 99) in 2..4 }
        pointAt(target.id)
        assertTrue(flow.action("tree"))
        assertTrue(sent.isEmpty())
        assertTrue(flow.scene().nodes.any { it.id == "sel" })
        pointAt(target.id)
        assertTrue(flow.action("tree"))
        assertEquals(CoreAction.TakePassive(target.id), sent.single())
    }

    @Test fun `a taken node offers a refund and zoom keeps working`() {
        val first = CorePassiveTree.nodes[CorePassiveTree.start(CoreClass.MAGE)].links.first { CorePassiveTree.nodes[it].ready }
        account = account.copy(journey = account.journey.copy(build = account.journey.build.copy(passives = setOf(first))))
        pointAt(first)
        flow.action("tree")
        assertTrue(flow.action("confirm"))
        assertIs<CoreAction.RefundPassive>(sent.single())
        assertTrue(flow.action("zoom:out")); assertTrue(flow.action("zoom:out"))
        assertTrue(flow.scene().nodes.isNotEmpty())
        assertTrue(flow.action("reset"))
        assertEquals(CoreAction.ResetPassives, sent.last())
        assertFalse(flow.action("nothing"))
    }

    @Test fun `a mastery shows its options as buttons and takes the one clicked`() {
        val job = CoreClass.MAGE
        val masteries = CorePassiveTree.nodes.filter { it.kind == CorePassiveKind.MASTERY }
        fun notable(m: CorePassiveNode) = CorePassiveTree.nodes.filter { it.kind == CorePassiveKind.NOTABLE && it.group == m.group }
            .minBy { CorePassiveTree.pathTo(job, emptySet(), it.id)?.size ?: 999 }
        val mastery = masteries.minBy { CorePassiveTree.pathTo(job, emptySet(), notable(it).id)?.size ?: 999 }
        assertEquals(mastery.choices.size, art.choiceLabels[mastery.id].size)
        assertTrue(art.choiceLabels[mastery.id].all { it in art.labels })
        val taken = CorePassiveTree.take(job, emptySet(), notable(mastery).id, 999)
        account = account.copy(journey = account.journey.copy(build = account.journey.build.copy(passives = taken)))
        flow.action("zoom:out")
        pointAt(mastery.id)
        assertTrue(flow.action("tree"))
        val buttons = flow.scene().nodes.filter { it.action?.startsWith("choice:") == true }
        assertEquals(3, buttons.size)
        assertTrue(flow.action("choice:1"))
        assertEquals(CoreAction.TakeMastery(mastery.id, 1), sent.single())
    }

    /**
     * The still that scripts/build_passive_tree_assets.py preview renders: a warrior who walked 14 points outward,
     * one reachable notable selected so the right panel has content. Written to build/passive-tree-preview/scene.json.
     */
    @Test fun `a warrior's screen for the preview`() {
        val job = CoreClass.WARRIOR
        val start = CorePassiveTree.nodes[CorePassiveTree.start(job)]
        var taken = setOf<Int>()
        repeat(14) {
            val best = (listOf(start.id) + taken).flatMap { CorePassiveTree.nodes[it].links }
                .filter { it !in taken && CorePassiveTree.nodes[it].kind in setOf(CorePassiveKind.SMALL, CorePassiveKind.NOTABLE) }
                .minBy { val n = CorePassiveTree.nodes[it]; kotlin.math.hypot(n.x - start.x * 1.6, n.y - start.y * 1.6) }
            taken = taken + best
        }
        account = CoreAccount(UUID.randomUUID(), silver = 48_300, journey = CoreJourney.legacy(job).let { it.copy(build = it.build.copy(passives = taken)) })
        val preview = PassiveTreeFlow(art, { account }, act = { _, _, _ -> }, leave = {})
        val cx = taken.map { CorePassiveTree.nodes[it].x }.average(); val cy = taken.map { CorePassiveTree.nodes[it].y }.average()
        val target = CorePassiveTree.nodes.filter { it.kind == CorePassiveKind.NOTABLE && it.id !in taken && (CorePassiveTree.pathTo(job, taken, it.id)?.size ?: 99) <= 3 }
            .minBy { kotlin.math.hypot(it.x - cx, it.y - cy) }
        // Glide up until the walked branch sits in the middle of the view.
        val ref = taken.minBy { CorePassiveTree.nodes[it].y }
        repeat(400) {
            val face = preview.scene().nodes.firstOrNull { it.id == "f$ref" }
            val y = face?.let { (it.box.y + it.box.h / 2 - top) / scale } ?: -1.0
            if (y >= 330) return@repeat
            preview.pointer(922 * scale, top + 105 * scale); preview.tick()
        }
        preview.pointer(922 * scale, top + 600 * scale); repeat(60) { preview.tick() }
        val face = assertNotNull(preview.scene().nodes.firstOrNull { it.id == "f$ref" })
        val sx = face.box.x + face.box.w / 2; val sy = face.box.y + face.box.h / 2
        val s = art.spots[ref]; val n = art.spots[target.id]
        preview.pointer(sx + (n.x - s.x) * scale, sy + (n.y - s.y) * scale)
        assertTrue(preview.action("tree"))
        preview.pointer(1600 * scale, top + 600 * scale)  // rest on the right panel: no hover, no scrolling
        val scene = preview.scene()
        assertTrue(scene.nodes.any { it.id == "sel" })
        val dir = File("build/passive-tree-preview").apply { mkdirs() }
        dir.resolve("scene.json").writeText(Gson().toJson(scene))
        // A second still with an open mastery chosen, for its option buttons.
        CorePassiveTree.nodes.firstOrNull { CorePassiveTree.isMastery(it.id) && CorePassiveTree.wheelOpen(taken, it.id) }?.let { m ->
            val n2 = art.spots[m.id]
            preview.pointer(sx + (n2.x - s.x) * scale, sy + (n2.y - s.y) * scale)
            if (preview.action("tree")) {
                preview.pointer(1600 * scale, top + 600 * scale)
                dir.resolve("scene-mastery.json").writeText(Gson().toJson(preview.scene()))
            }
        }
    }
}
