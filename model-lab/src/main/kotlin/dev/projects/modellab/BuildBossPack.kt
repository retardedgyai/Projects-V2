package dev.projects.modellab

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.worldseed.resourcepack.PackBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Adapted from Scorpius BuildBossPack; output is deliberately separate from the live UI pack. */
fun main(args: Array<String>) {
    require(args.size == 2) { "BuildBossPack <bbmodel-directory> <module/build/boss-pack>" }
    val source = Path.of(args[0]).toAbsolutePath().normalize()
    val output = Path.of(args[1]).toAbsolutePath().normalize()
    require(output.fileName.toString() == "boss-pack" && output.parent.fileName.toString() == "build")
    require(Files.isDirectory(source) && !source.startsWith(output))
    val inputs = Files.list(source).use { s -> s.filter { it.toString().endsWith(".bbmodel") }.sorted().toList() }
    require(inputs.isNotEmpty()) { "No bbmodel files: $source" }
    val catalog = JsonObject()
    inputs.forEach { file ->
        val model = Files.newBufferedReader(file).use { JsonParser.parseReader(it).asJsonObject }
        val entry = JsonObject()
        val animations = JsonObject()
        model.getAsJsonArray("animations")?.forEach { a ->
            val animation = a.asJsonObject
            val name = animation["name"].asString
            require(!animations.has(name)) { "Duplicate animation: $file / $name" }
            animations.addProperty(name, animation["length"].asDouble)
        }
        entry.add("animations", animations)
        entry.addProperty("sourceSha256", sha256(Files.readAllBytes(file)))
        catalog.add(file.fileName.toString(), entry)
    }
    Files.createDirectories(output)
    val staging = Files.createTempDirectory(output, "generation-")
    try {
        val pack = staging.resolve("resourcepack")
        val geo = staging.resolve("models")
        val config = PackBuilder.generate(source, pack, geo)
        Files.writeString(staging.resolve("mappings.json"), config.modelMappings())
        Files.writeString(staging.resolve("catalog.json"), catalog.toString())
        Files.writeString(pack.resolve("pack.mcmeta"), """{"pack":{"description":"ProjectS / Scorpius model laboratory","min_format":[88,0],"max_format":[88,0]}}""")
        verifyPack(pack)
        ZipOutputStream(Files.newOutputStream(staging.resolve("pack.zip"))).use { zip ->
            Files.walk(pack).use { paths -> paths.filter(Files::isRegularFile).sorted().forEach { file ->
                val name = pack.relativize(file).toString().replace('\\', '/')
                zip.putNextEntry(ZipEntry(name).apply { time = 0 })
                Files.copy(file, zip)
                zip.closeEntry()
            } }
        }
        val manifest = JsonObject().apply {
            addProperty("packSha256", sha256(Files.readAllBytes(staging.resolve("pack.zip"))))
            addProperty("modelCount", inputs.size)
            add("models", JsonArray().apply { inputs.forEach { add(it.fileName.toString()) } })
            add("files", JsonObject().apply {
                Files.walk(staging).use { paths -> paths.filter(Files::isRegularFile).sorted().forEach { file ->
                    addProperty(staging.relativize(file).toString().replace('\\', '/'), sha256(Files.readAllBytes(file)))
                } }
            })
        }
        Files.writeString(staging.resolve("manifest.json"), manifest.toString())
        val bundle = output.resolve("bundle")
        // Only replace this tool's dedicated generated child, never source assets or the live pack.
        deleteGeneratedTree(bundle, output)
        Files.move(staging, bundle)
        println("Built ${inputs.size} models -> $bundle")
    } finally {
        if (Files.exists(staging)) deleteGeneratedTree(staging, output)
    }
}

/** Catch missing model/texture references before a purple-black placeholder reaches the client. */
internal fun verifyPack(pack: Path) {
    fun asset(reference: String, folder: String, extension: String) {
        if (!reference.startsWith("worldseed:")) return // Vanilla fallbacks are provided by the client.
        val relative = reference.substringAfter(':')
        val path = pack.resolve("assets/worldseed/$folder/$relative$extension").normalize()
        require(path.startsWith(pack.normalize()) && Files.isRegularFile(path)) { "Missing pack asset: $reference ($folder)" }
    }
    fun walk(node: com.google.gson.JsonElement) {
        if (node.isJsonArray) node.asJsonArray.forEach(::walk)
        if (node.isJsonObject) node.asJsonObject.entrySet().forEach { (key, value) ->
            if ((key == "model" || key == "parent") && value.isJsonPrimitive && value.asJsonPrimitive.isString)
                asset(value.asString, "models", ".json")
            if (key == "textures" && value.isJsonObject) value.asJsonObject.entrySet().forEach { (_, texture) ->
                if (texture.isJsonPrimitive && texture.asJsonPrimitive.isString) asset(texture.asString, "textures", ".png")
            }
            walk(value)
        }
    }
    Files.walk(pack).use { paths -> paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".json") }
        .forEach { file -> Files.newBufferedReader(file).use { walk(JsonParser.parseReader(it)) } } }
}

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }

private fun deleteGeneratedTree(child: Path, root: Path) {
    require(child.parent == root && root.fileName.toString() == "boss-pack")
    if (Files.exists(child)) Files.walk(child).use { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
    }
}
