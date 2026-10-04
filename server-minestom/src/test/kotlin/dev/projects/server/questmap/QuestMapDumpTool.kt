package dev.projects.server.questmap

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.block.Block

/**
 * Dev tool, not a check: dumps one generated quest map per terrain style so the maps can be rendered
 * offline (scratch isometric renderer). Runs only when QUESTMAP_DUMP names an output directory.
 */
class QuestMapDumpTool {
    @Test
    fun `dump one decorated map per terrain style`() {
        val out = System.getenv("QUESTMAP_DUMP") ?: return
        MinecraftServer.init(Auth.Offline())
        Files.createDirectories(Path.of(out))
        for (style in QuestTerrainStyle.entries) {
            val seed = 9_000L * 6 + style.ordinal
            val runtime = VerdantRoadQuestRuntime.prepare(seed, candidateCount = 1).get(60, TimeUnit.SECONDS)
            try {
                val plan = runtime.plan
                val lines = ArrayList<String>(plan.size * plan.size)
                for (x in 0 until plan.size) for (z in 0 until plan.size) {
                    val h = plan.heightAt(x, z)
                    val column = StringBuilder()
                    for (y in h - 4..h + 28) {
                        val block = runtime.instance.getBlock(x, y, z)
                        if (block.isAir) continue
                        column.append(y).append(':').append(block.name().removePrefix("minecraft:"))
                        if (block.name().endsWith("_slab")) column.append('#').append(block.getProperty("type"))
                        column.append(' ')
                    }
                    if (column.isNotEmpty()) lines += "$x $z ${column.toString().trim()}"
                }
                val route = plan.mainRoute.joinToString(" ") { "${it.x},${it.z}" }
                Files.write(Path.of(out, "${style.name.lowercase()}.txt"), listOf("size ${plan.size} seed $seed", "route $route") + lines)
                println("QUESTMAP_DUMP ${style.name} columns=${lines.size}")
            } finally {
                runtime.close()
            }
        }
    }
}
