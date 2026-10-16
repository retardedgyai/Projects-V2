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
            "3e7641b14a52236e", "249aca7bed73b2ef", "5872c33cb0f5d747", "57c4ab1d3f84ca5f",
            "0a3677b87677733e", "45e994d866718238", "3706ffb0854ce03a", "d730b3101093e4f8",
            "115b474e46fa9d69", "5d68ff5c031a8ed3", "ee7b59bad298302b", "9a2ed3809421f67d",
            "f24f604bdb67a255", "9df3af06b8b27a16", "292632d2de421e18", "02f16fca19152e83")
    }
}
