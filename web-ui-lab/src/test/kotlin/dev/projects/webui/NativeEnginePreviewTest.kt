package dev.projects.webui

import com.google.gson.JsonParser
import java.awt.image.BufferedImage
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeEnginePreviewTest {
    @Test fun `PNG transport preserves source bytes and size across updates without mixing F2`() {
        val root = Files.createDirectories(Path.of("../.tools/native-preview-tests"))
        val run = Files.createTempDirectory(root, "transport-")
        val live = Files.createDirectories(run.resolve("live"))
        val shots = Files.createDirectories(run.resolve("screenshots"))
        val page = run.resolve("index.html"); Files.writeString(page, "<h1>fixture</h1>")
        fun png(path: Path, width: Int, color: Int): ByteArray {
            val image = BufferedImage(width, 32, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until 32) for (x in 0 until width) image.setRGB(x, y, color + x + y)
            ImageIO.write(image, "png", path.toFile()); return Files.readAllBytes(path)
        }
        val original = png(live.resolve("latest.png"), 48, 0xff104050.toInt())
        val f2 = png(shots.resolve("f2.png"), 64, 0xff507010.toInt())
        NativeEnginePreview(live, shots, page, 0).use { preview ->
            val client = HttpClient.newHttpClient()
            fun get(path: String) = client.send(HttpRequest.newBuilder(URI(preview.address + path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray())
            fun status(source: String) = JsonParser.parseString(String(get("status?source=$source").body())).asJsonObject
            val before = status("window")
            assertEquals(48, before.get("width").asInt)
            assertEquals(32, before.get("height").asInt)
            val revision = before.get("revision").asString
            assertEquals(MessageDigest.getInstance("SHA-256").digest(original).joinToString("") { "%02x".format(it) }, revision)
            val served = get(before.get("image").asString.removePrefix("/"))
            assertContentEquals(original, served.body())
            assertEquals("no-store", served.headers().firstValue("Cache-Control").get())
            assertContentEquals(f2, get(status("f2").get("image").asString.removePrefix("/")).body())
            val replacement = png(live.resolve("latest.png"), 80, 0xff305010.toInt())
            preview.refresh()
            val after = status("window")
            assertEquals(80, after.get("width").asInt)
            assertTrue(after.get("revision").asString != revision)
            assertContentEquals(replacement, get(after.get("image").asString.removePrefix("/")).body())
            assertContentEquals(original, get(before.get("image").asString.removePrefix("/")).body(), "In-flight prior image must stay immutable")
            // An incomplete file cannot replace the last valid image or its revision.
            Files.write(live.resolve("latest.png"), byteArrayOf(1, 2, 3)); preview.refresh()
            assertEquals(after.get("revision"), status("window").get("revision"))
            assertEquals(400, get("status?source=arbitrary-directory").statusCode())
            assertEquals(404, get("not-a-frame").statusCode())
            val post = client.send(HttpRequest.newBuilder(URI(preview.address)).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray())
            assertEquals(405, post.statusCode())
        }
    }
}
