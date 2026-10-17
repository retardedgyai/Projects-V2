package dev.projects.server.coreloop

import com.google.gson.Gson
import dev.projects.webui.UiScene
import java.io.File
import java.security.MessageDigest
import kotlin.test.*

/**
 * Guards refactors that must not change the picture: a fixed click script, a fixed clock, one hash per scene.
 * After a deliberate visual change, copy build/grimoire-golden/hashes.txt into GOLDEN.
 */
class GrimoireSceneGoldenTest {
    private fun bytes(path: String) = assertNotNull(javaClass.getResourceAsStream(path), path).use { it.readBytes() }
    private val art = GrimoireFlow.Art(bytes("/polish05/grimoire.json"), bytes("/polish05/font-map.json"))

    @Test fun `the scene is unchanged for the click script`() {
        var now = 1_000_000L
        val f = GrimoireFlow(art, GrimoireSample.midway(), { now }) {}
        val dumps = mutableListOf<String>()
        val scenes = mutableListOf<UiScene>()
        fun record() { val s = f.scene(); scenes += s; dumps += dump(s) }
        record()
        for (step in SCRIPT) {
            now += 137
            if (step.startsWith("hover:")) f.hover(step.removePrefix("hover:")) else f.action(step)
            record()
        }
        val hashes = dumps.map(::sha)
        File("build/grimoire-golden").apply { mkdirs() }.let { dir ->
            dumps.forEachIndexed { i, d -> File(dir, "$i.txt").writeText(d) }
            // For scripts/render_grimoire_preview.py: the scenes as JSON, to look at without a client.
            scenes.forEachIndexed { i, s -> File(dir, "$i.json").writeText(Gson().toJson(s)) }
            File(dir, "hashes.txt").writeText(hashes.joinToString("\n"))
        }
        assertEquals(GOLDEN, hashes)
    }

    private fun dump(s: UiScene) = s.nodes.joinToString("\n") { n ->
        listOf(n.id, n.box.x, n.box.y, n.box.w, n.box.h, n.text, n.style.toSortedMap(), n.action, n.depth, n.sprite?.char).joinToString("|")
    }
    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    private companion object {
        val SCRIPT = listOf("node:n4", "claim", "node:n5", "achieve", "node:n6", "achieve", "node:n7", "pin", "hover:n-n9-hit",
            "node:n8", "node:n11", "node:n12", "node:n1", "tab:2", "tab:0")
        val GOLDEN = listOf(
            "183fa336f5089cdc", "0736ab179ec6a018", "b514e55fb00d58c6", "1f0baae7deb49704",
            "7e4b44de7b4f5d72", "9147e9390aa457b5", "39b3d72596d84b20", "054d4d14e1e52883",
            "cc98de610771cf59", "aa68410bd3fd7194", "4325cbabafd5f3f3", "29c6a2c09d3bbeb2",
            "9dca1dfc7aea205b", "c5db4c0b363c941b", "1c9710ed4b646fbb", "eb5ce6635ba9f67e")
    }
}
