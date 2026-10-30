package dev.projects.server.coreloop

import java.util.UUID
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

    /** Points the cursor at a tree node through the same scene transform the screen uses. */
    private fun pointAt(node: Int) {
        val scene = flow.scene()
        val face = scene.nodes.firstOrNull { it.id == "f${CorePassiveTree.start(CoreClass.MAGE)}" }
        assertNotNull(face, "the class start is drawn gold")
        // Start face centre in scene units, then offset by the node's position relative to the start.
        val sx = face.box.x + face.box.w / 2; val sy = face.box.y + face.box.h / 2
        val s = art.spots[CorePassiveTree.start(CoreClass.MAGE)]; val n = art.spots[node]
        val zoom = face.box.w / scale / art.sprites.getValue("face_START").width
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
}
