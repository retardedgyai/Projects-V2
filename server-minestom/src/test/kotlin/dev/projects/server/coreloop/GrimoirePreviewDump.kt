package dev.projects.server.coreloop

import com.google.gson.Gson
import java.io.File
import kotlin.test.*

// Temporary: map structure candidates for mock images. Not committed.
class GrimoirePreviewDump {
    private fun bytes(path: String) = assertNotNull(javaClass.getResourceAsStream(path), path).use { it.readBytes() }
    private val art = GrimoireFlow.Art(bytes("/polish05/grimoire.json"), bytes("/polish05/font-map.json"))

    @Test fun dump() {
        val dir = File("build/grimoire-preview").apply { deleteRecursively(); mkdirs() }
        for (style in "ABC") {
            var now = 1_000_000L
            val f = GrimoireFlow(art, GrimoireSample.midway(), { now }) {}
            f.mapStyle = style
            now += 1000; File(dir, "${style}_map.json").writeText(Gson().toJson(f.scene()))
            f.action("node:n7"); now += 1000; File(dir, "${style}_n7.json").writeText(Gson().toJson(f.scene()))
        }
        var now = 1_000_000L
        val f = GrimoireFlow(art, GrimoireSample.midway(), { now }) {}
        f.action("tab:2"); now += 1000; File(dir, "sealed.json").writeText(Gson().toJson(f.scene()))
    }
}
