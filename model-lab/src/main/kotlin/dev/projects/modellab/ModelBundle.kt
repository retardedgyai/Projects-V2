package dev.projects.modellab

import com.google.gson.JsonParser
import net.worldseed.multipart.ModelEngine
import net.worldseed.multipart.ModelLoader
import java.nio.file.Files
import java.nio.file.Path

data class ModelDefinition(val id: String, val animations: Map<String, Double>) {
    // Match the original Scorpius visual constructors; not every boss shares the same raw scale.
    val previewScale: Float get() = when (id) {
        "osirion.bbmodel" -> 3.2f
        "radix.bbmodel", "vesper_bell.bbmodel" -> 4f
        else -> 3f
    }
    fun requireAnimation(name: String): String {
        require(name in animations) { "$id: unknown animation '$name'; available: ${animations.keys}" }
        return name
    }
}

/** One immutable resource-pack + mappings generation per server process. No unsafe live remapping. */
class ModelBundle(val directory: Path) {
    val definitions: Map<String, ModelDefinition>
    init {
        val catalog = Files.newBufferedReader(directory.resolve("catalog.json")).use {
            JsonParser.parseReader(it).asJsonObject
        }
        definitions = catalog.entrySet().associate { (id, raw) ->
            id to ModelDefinition(id, raw.asJsonObject.getAsJsonObject("animations").entrySet()
                .associate { (name, length) -> name to length.asDouble })
        }
        require(definitions.isNotEmpty())
        val manifest = Files.newBufferedReader(directory.resolve("manifest.json")).use {
            JsonParser.parseReader(it).asJsonObject
        }
        require(sha256(Files.readAllBytes(directory.resolve("pack.zip"))) == manifest["packSha256"].asString) {
            "Pack and mappings generation mismatch; rebuild boss pack"
        }
        manifest.getAsJsonObject("files").entrySet().forEach { (relative, hash) ->
            val target = directory.resolve(relative).normalize()
            require(target.startsWith(directory.normalize()) && Files.isRegularFile(target)) { "Missing bundle file: $relative" }
            require(sha256(Files.readAllBytes(target)) == hash.asString) { "Mismatched bundle file: $relative" }
        }
    }
    fun load() {
        Files.newBufferedReader(directory.resolve("mappings.json")).use {
            ModelEngine.loadMappings(it, directory.resolve("models"))
        }
        definitions.values.forEach { definition ->
            requireNotNull(ModelLoader.loadModel(definition.id)) { "Missing geometry: ${definition.id}" }
            if (definition.animations.isNotEmpty()) {
                val animations = requireNotNull(ModelLoader.loadAnimations(definition.id)).getAsJsonObject("animations")
                definition.animations.keys.forEach { require(animations.has(it)) { "Missing ${definition.id}/$it" } }
            }
        }
    }
    fun definition(id: String) = requireNotNull(definitions[id]) { "Unknown model: $id" }
}
