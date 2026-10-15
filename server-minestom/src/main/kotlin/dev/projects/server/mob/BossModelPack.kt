package dev.projects.server.mob

import net.minestom.server.item.Material
import net.worldseed.multipart.ModelEngine
import net.worldseed.resourcepack.PackBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText

/**
 * Build step (Gradle `:server-minestom:generateBossModels`): Blockbench `.bbmodel` -> pack assets + server data.
 *
 * WSEE writes its bone models under the private `worldseed` namespace, but binds them through the vanilla
 * item definition of its carrier material. The pack policy forbids vanilla overrides, so that definition is
 * moved to `assets/worldseed/items/boss_bones.json` and the server stamps every bone item with that item model
 * (see [BossModelLibrary]). The vanilla item stays untouched for players.
 */
object BossModelPack {
    val CARRIER: Material = Material.MAGMA_CREAM
    const val ITEM_MODEL = "worldseed:boss_bones"
    const val DATA_ROOT = "boss-models"

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3) { "usage: BossModelPack <bbmodel dir> <pack root> <server data dir>" }
        val count = generate(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]))
        println("[generateBossModels] $count bbmodel(s) -> ${args[1]}/assets/worldseed, ${args[2]}")
    }

    /** Replaces pack/assets/worldseed and the data dir completely; returns the number of models. */
    @OptIn(ExperimentalPathApi::class)
    fun generate(source: Path, pack: Path, data: Path): Int {
        val models = Files.list(source).use { files -> files.filter { it.fileName.toString().endsWith(".bbmodel") }.toList() }
        require(models.isNotEmpty()) { "No .bbmodel files in $source" }
        val staging = Files.createTempDirectory("projects-boss-pack")
        try {
            ModelEngine.setModelMaterial(CARRIER)
            val modelData = staging.resolve("data")
            val config = PackBuilder.generate(source, staging.resolve("pack"), modelData)
            val target = pack.resolve("assets/worldseed")
            if (target.exists()) target.deleteRecursively()
            if (data.exists()) data.deleteRecursively()
            target.parent.createDirectories()
            // Copy, not move: the staging directory is usually on another drive than the repository.
            staging.resolve("pack/assets/worldseed").copyToRecursively(target, followLinks = false)
            target.resolve("items").createDirectories()
            val binding = staging.resolve("pack/assets/minecraft/items/${CARRIER.key().value()}.json")
            Files.copy(binding, target.resolve("items/boss_bones.json"), StandardCopyOption.REPLACE_EXISTING)
            // The rest of staging/assets/minecraft (WSEE's ink_sac thumbnail) is intentionally dropped.
            data.createDirectories()
            modelData.copyToRecursively(data.resolve("models"), followLinks = false)
            data.resolve("mappings.json").writeText(config.modelMappings())
            // Classpath resources cannot be listed at runtime; this index names every file to extract.
            val files = data.walk().filter { it.isRegularFile() }.map { it.relativeTo(data).toString().replace('\\', '/') }.sorted().toList()
            data.resolve("index.txt").writeText(files.joinToString("\n", postfix = "\n"))
            return models.size
        } finally {
            staging.deleteRecursively()
        }
    }
}
