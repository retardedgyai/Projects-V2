package dev.projects.server.mob

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
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
import kotlin.io.path.readText
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

    /**
     * WSEE 13.0.4 mirrors the cubes on X but rotates each bone about its un-mirrored pivot with Y/Z rest
     * rotations un-mirrored too, so a bone off the centre line (a wing) swings about the wrong side. Mirroring
     * every bone pivot's X and flipping its Y/Z rest rotation here makes the game match Blockbench (as a mirror
     * image, like before). Checked by ShadeWyrmBossTest's rest-pose test.
     */
    fun forWseeRuntime(bbmodel: String): String {
        val root = JsonParser.parseString(bbmodel).asJsonObject
        fun fix(node: JsonElement) {
            if (!node.isJsonObject) return
            val group = node.asJsonObject
            group.getAsJsonArray("origin")?.let { it.set(0, JsonPrimitive(-it[0].asDouble)) }
            group.getAsJsonArray("rotation")?.let {
                it.set(1, JsonPrimitive(-it[1].asDouble)); it.set(2, JsonPrimitive(-it[2].asDouble))
            }
            group.getAsJsonArray("children")?.forEach(::fix)
        }
        root.getAsJsonArray("outliner").forEach(::fix)
        return root.toString()
    }

    /**
     * Bosses whose textures carry their own (smooth) lighting. Minecraft shades every item-model face flat by its
     * direction, which shows each facet plate of a faceted model as a seam; `shade: false` leaves only the baked light.
     */
    val BAKED_LIGHT = setOf("shade_wyrm")

    @OptIn(ExperimentalPathApi::class)
    private fun unshade(models: Path) {
        if (!models.exists()) return
        for (file in models.walk().filter { it.isRegularFile() && it.toString().endsWith(".json") }) {
            val json = JsonParser.parseString(file.readText()).asJsonObject
            json.getAsJsonArray("elements")?.forEach { it.asJsonObject.addProperty("shade", false) }
            file.writeText(json.toString())
        }
    }

    /** Replaces pack/assets/worldseed and the data dir completely; returns the number of models. */
    @OptIn(ExperimentalPathApi::class)
    fun generate(source: Path, pack: Path, data: Path): Int {
        val models = Files.list(source).use { files -> files.filter { it.fileName.toString().endsWith(".bbmodel") }.toList() }
        require(models.isNotEmpty()) { "No .bbmodel files in $source" }
        val staging = Files.createTempDirectory("projects-boss-pack")
        try {
            ModelEngine.setModelMaterial(CARRIER)
            val sources = staging.resolve("bbmodels").createDirectories()
            for (model in models) sources.resolve(model.fileName.toString()).writeText(forWseeRuntime(model.readText()))
            val modelData = staging.resolve("data")
            val config = PackBuilder.generate(sources, staging.resolve("pack"), modelData)
            val target = pack.resolve("assets/worldseed")
            if (target.exists()) target.deleteRecursively()
            if (data.exists()) data.deleteRecursively()
            target.parent.createDirectories()
            // Copy, not move: the staging directory is usually on another drive than the repository.
            staging.resolve("pack/assets/worldseed").copyToRecursively(target, followLinks = false)
            for (id in BAKED_LIGHT) unshade(target.resolve("models/mobs/$id.bbmodel"))
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
