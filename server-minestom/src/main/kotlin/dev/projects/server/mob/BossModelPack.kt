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
     * WSEE texture states (`<file>.states` beside the model): every extra embedded texture `<model>_<state>.png` (same
     * atlas layout) becomes a state `<state>` that puts it on every bone, switched with `model.setState`. The 黒翳竜's
     * `shade_wyrm_frenzy.png` is its second form (BossModelView.awaken). Null when the model has one texture.
     */
    fun textureStates(bbmodel: String): String? {
        val root = JsonParser.parseString(bbmodel).asJsonObject
        val textures = root.getAsJsonArray("textures").map { it.asJsonObject["name"].asString }
        if (textures.size < 2) return null
        val bones = mutableListOf<String>()
        fun walk(node: JsonElement) {
            if (!node.isJsonObject) return
            bones += node.asJsonObject["name"].asString
            node.asJsonObject.getAsJsonArray("children")?.forEach(::walk)
        }
        root.getAsJsonArray("outliner").forEach(::walk)
        val base = textures[0].removeSuffix(".png")
        val states = com.google.gson.JsonObject()
        for (texture in textures.drop(1)) {
            val mapping = com.google.gson.JsonObject()
            bones.forEach { mapping.addProperty(it, texture) }
            states.add(texture.removeSuffix(".png").removePrefix("${base}_"), mapping)
        }
        return states.toString()
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
            for (model in models) {
                val text = model.readText()
                sources.resolve(model.fileName.toString()).writeText(forWseeRuntime(text))
                textureStates(text)?.let { sources.resolve("${model.fileName}.states").writeText(it) }
            }
            val modelData = staging.resolve("data")
            val config = PackBuilder.generate(sources, staging.resolve("pack"), modelData)
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
