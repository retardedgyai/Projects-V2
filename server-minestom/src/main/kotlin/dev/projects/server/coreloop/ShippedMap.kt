package dev.projects.server.coreloop

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * A map drawn in scripts/buildkit and shipped in the jar as region files beside a rooms.json (resources/<name>).
 * AnvilLoader reads plain files, so each map is copied out once to a temporary folder and read from there.
 */
internal object ShippedMap {
    private val folders = ConcurrentHashMap<String, Path>()

    fun folder(name: String): Path = folders.computeIfAbsent(name) {
        val dir = Files.createTempDirectory("projects-" + name.replace('/', '-'))
        val files = listOf("rooms.json") + (-2..1).flatMap { x -> (-2..1).map { z -> "region/r.$x.$z.mca" } }
        for (file in files) {
            val stream = ShippedMap::class.java.getResourceAsStream("/$name/$file") ?: continue
            val target = dir.resolve(file).also { Files.createDirectories(it.parent) }
            stream.use { Files.copy(it, target) }
        }
        require(Files.exists(dir.resolve("rooms.json"))) { "Missing $name/rooms.json" }
        dir
    }
}
