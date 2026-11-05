package dev.projects.server.coreloop

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.anvil.AnvilLoader
import net.minestom.server.instance.block.Block
import java.nio.file.Files
import java.nio.file.Path

/**
 * Development only: walk the 1000×1000 開拓大陸 written by scripts/buildkit/towns/frontier_export.py.
 * The world is read from region files (AnvilLoader) and shared by everyone who enters; nothing is saved back.
 * Folder: -Dprojects.frontierWorld, default ~/ProjectS/worlds/frontier1000.
 */
internal object FrontierWorld {
    private var instance: InstanceContainer? = null
    private var spawn: Pos = Pos(500.5, 80.0, 850.5)

    private fun folder(): Path = Path.of(System.getProperty("projects.frontierWorld")
        ?: (System.getProperty("user.home") + "/ProjectS/worlds/frontier1000"))

    /** Null when the world has not been exported yet. */
    private fun world(): InstanceContainer? {
        instance?.let { return it }
        val dir = folder()
        if (!Files.isDirectory(dir.resolve("region"))) return null
        val created = MinecraftServer.getInstanceManager().createInstanceContainer()
        created.setChunkSupplier(::LightingChunk)
        created.chunkLoader = AnvilLoader(dir)
        created.setGenerator { unit -> unit.modifier().fillHeight(-64, 0, Block.DEEPSLATE) }   // outside the exported area
        created.time = 6_000L
        created.defaultClock()?.pause()
        readSpawn(dir)?.let { spawn = it }
        instance = created
        return created
    }

    private fun readSpawn(dir: Path): Pos? = runCatching {
        val text = Files.readString(dir.resolve("projects-map.json"))
        val n = Regex("\"spawn\"\\s*:\\s*\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)").find(text) ?: return null
        val (x, y, z) = n.destructured
        Pos(x.toDouble() + 0.5, y.toDouble(), z.toDouble() + 0.5)
    }.getOrNull()

    fun enter(player: Player): Boolean {
        val world = world() ?: return false
        player.setInstance(world, spawn)
        return true
    }
}
