package dev.projects.server.puppet

import com.google.gson.JsonParser
import net.minestom.server.entity.Player
import java.awt.image.BufferedImage
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * The player's skin as per-face colours for the puppet parts (scripts/build_player_puppet.py writes the part
 * models and player-motions/puppet-layout.json, the skin pixel behind every tint index).
 *
 * The skin PNG comes from the texture URL in the player's signed profile (Mojang's texture server), fetched once
 * per URL and cached. Until it arrives, or with no skin (offline profiles), the puppet wears a plain mannequin.
 * Base layer only; slim (3 px) arms are read as wide ones for now.
 */
internal object PuppetSkin {
    class Part(val model: String, val anchor: DoubleArray, val pixels: IntArray)

    val parts: Map<String, Part> by lazy {
        val text = PuppetSkin::class.java.classLoader.getResourceAsStream("player-motions/puppet-layout.json")!!
            .use { String(it.readAllBytes(), Charsets.UTF_8) }
        JsonParser.parseString(text).asJsonObject.getAsJsonObject("parts").entrySet().associate { (name, value) ->
            val o = value.asJsonObject
            name to Part(o.get("model").asString, o.getAsJsonArray("anchor").map { it.asDouble }.toDoubleArray(),
                o.getAsJsonArray("pixels").flatMap { p -> p.asJsonArray.map { it.asInt } }.toIntArray())
        }
    }

    /** Colours (0xRRGGBB) for [part], in tint order, read from [skin] (64x64 or legacy 64x32). */
    fun colors(skin: BufferedImage, part: String): List<Int> {
        val p = parts.getValue(part)
        return List(p.pixels.size / 2) { i ->
            var x = p.pixels[2 * i]; var y = p.pixels[2 * i + 1]
            if (skin.height < 64 && y >= 32) { if (x >= 32) { x += 8; y -= 32 } else { x -= 16; y -= 32 } }  // legacy: left = right
            if (x !in 0 until skin.width || y !in 0 until skin.height) 0x9a9a9a else skin.getRGB(x, y) and 0xFFFFFF
        }
    }

    /** A plain mannequin, the same layout as the Blockbench model's (scripts/player_rig.py). */
    val mannequin: BufferedImage by lazy {
        val img = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        fun fill(x0: Int, y0: Int, w: Int, h: Int, c: Int) { for (y in y0 until y0 + h) for (x in x0 until x0 + w) img.setRGB(x, y, c or (0xFF shl 24)) }
        fun box(u: Int, v: Int, w: Int, h: Int, d: Int, side: Int, top: Int, front: Int = side) {
            fill(u + d, v, w, d, top); fill(u + d + w, v, w, d, top)
            fill(u, v + d, d, h, side); fill(u + d, v + d, w, h, front); fill(u + d + w, v + d, d, h, side); fill(u + 2 * d + w, v + d, w, h, side)
        }
        box(0, 0, 8, 8, 8, 0xB0A89C, 0xC4BCB0); fill(8, 11, 8, 2, 0x3C3432)
        box(16, 16, 8, 12, 4, 0x8C7868, 0xA08C7C, 0x964834)
        box(40, 16, 4, 12, 4, 0xA49688, 0xB4A696); box(32, 48, 4, 12, 4, 0xA49688, 0xB4A696)
        box(0, 16, 4, 12, 4, 0x5C5660, 0x6E6872); box(16, 48, 4, 12, 4, 0x5C5660, 0x6E6872)
        img
    }

    private val http by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build() }
    private val cache = ConcurrentHashMap<String, CompletableFuture<BufferedImage?>>()

    /** The skin texture URL in the player's profile, if any. */
    fun url(player: Player): String? = runCatching {
        val json = String(Base64.getDecoder().decode(player.skin?.textures() ?: return null), Charsets.UTF_8)
        JsonParser.parseString(json).asJsonObject.getAsJsonObject("textures")?.getAsJsonObject("SKIN")?.get("url")?.asString
    }.getOrNull()?.takeIf { it.startsWith("http://textures.minecraft.net/") || it.startsWith("https://textures.minecraft.net/") }

    /** The player's skin, fetched once per URL; completes with null when there is none or it cannot be read. */
    fun skin(player: Player): CompletableFuture<BufferedImage?> {
        val url = url(player) ?: return CompletableFuture.completedFuture(null)
        return cache.computeIfAbsent(url) {
            http.sendAsync(HttpRequest.newBuilder(URI.create(url.replaceFirst("http://", "https://"))).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofByteArray())
                .thenApply { r -> if (r.statusCode() == 200) runCatching { ImageIO.read(r.body().inputStream()) }.getOrNull() else null }
                .exceptionally { null }
        }
    }
}
