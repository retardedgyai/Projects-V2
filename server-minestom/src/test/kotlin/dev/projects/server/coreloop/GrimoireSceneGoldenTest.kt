package dev.projects.server.coreloop

import java.io.File
import java.security.MessageDigest
import kotlin.test.*

/** Guards refactors that must not change the picture: a fixed click script, a fixed clock, one hash per scene. */
class GrimoireSceneGoldenTest {
    private fun bytes(path: String) = assertNotNull(javaClass.getResourceAsStream(path), path).use { it.readBytes() }
    private val art = GrimoireFlow.Art(bytes("/polish05/grimoire.json"), bytes("/polish05/font-map.json"))

    @Test fun `the scene is unchanged for the click script`() {
        var now = 1_000_000L
        val f = GrimoireFlow(art, GrimoireSample.midway(), { now }) {}
        val dumps = mutableListOf<String>()
        dumps += dump(f)
        for (step in SCRIPT) {
            now += 137
            if (step.startsWith("hover:")) f.hover(step.removePrefix("hover:")) else f.action(step)
            dumps += dump(f)
        }
        val hashes = dumps.map(::sha)
        File("build/grimoire-golden").apply { mkdirs() }.let { dir ->
            dumps.forEachIndexed { i, d -> File(dir, "$i.txt").writeText(d) }
            File(dir, "hashes.txt").writeText(hashes.joinToString("\n"))
        }
        assertEquals(GOLDEN, hashes)
    }

    private fun dump(f: GrimoireFlow) = f.scene().nodes.joinToString("\n") { n ->
        listOf(n.id, n.box.x, n.box.y, n.box.w, n.box.h, n.text, n.style.toSortedMap(), n.action, n.depth, n.sprite?.char).joinToString("|")
    }
    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    private companion object {
        val SCRIPT = listOf("node:n4", "claim", "node:n5", "achieve", "node:n6", "achieve", "node:n7", "pin", "hover:n-n9-hit",
            "node:n8", "node:n11", "node:n12", "node:n1", "tab:2", "tab:0")
        val GOLDEN = listOf(
            "769c042ac630ea6d", "503da7682d965fa0", "1e7113c939ad993e", "c0e8cc7cc8de2782",
            "b46bccf3f7423292", "ba44478810790562", "08cdb418f20ae96b", "f9e27c191b626be3",
            "d0b5189e83bc7c71", "85b363f5044efdc6", "74b5c9b7777d69c9", "a4d1e7ca473ee3bd",
            "bea06178426f1527", "44fb03072c6f7428", "f625602648494f87", "ef079dea84876f77")
    }
}
