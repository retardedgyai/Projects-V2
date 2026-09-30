package dev.projects.webui

import com.google.gson.Gson
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.Executors
import javax.imageio.ImageIO

/** Read-only transport of captured Minecraft PNGs. No image resampling or game input. */
class NativeEnginePreview(
    private val captureDirectory: Path,
    private val screenshotsDirectory: Path,
    private val page: Path,
    port: Int,
) : AutoCloseable {
    private data class Frame(val source: String, val revision: String, val width: Int, val height: Int,
        val capturedAt: String, val bytes: ByteArray)
    private data class Fingerprint(val path: Path, val size: Long, val modified: Long)
    private val frames = mutableMapOf<String, Frame>()
    private val fingerprints = mutableMapOf<String, Fingerprint>()
    private val retained = linkedMapOf<String, Frame>()
    private val gson = Gson()
    private val executor = Executors.newScheduledThreadPool(2) { task ->
        Thread(task, "native-engine-preview").apply { isDaemon = true }
    }
    private val http = HttpServer.create(InetSocketAddress("127.0.0.1", port), 8)
    val address: String get() = "http://127.0.0.1:${http.address.port}/"

    init {
        require(port == 0 || port in 1024..65535 && port !in setOf(25565, 25566))
        require(Files.isRegularFile(page))
        http.executor = executor
        http.createContext("/") { exchange -> exchange.use { e ->
            if (e.requestMethod != "GET") { e.sendResponseHeaders(405, -1); return@use }
            val path = e.requestURI.path
            val bytes: ByteArray
            val contentType: String
            when {
                path == "/" -> { bytes = Files.readAllBytes(page); contentType = "text/html; charset=utf-8" }
                path == "/status" -> {
                    val source = e.requestURI.rawQuery.orEmpty().removePrefix("source=").ifEmpty { "window" }
                    if (source !in setOf("window", "f2")) { e.sendResponseHeaders(400, -1); return@use }
                    val frame = synchronized(this) { frames[source] }
                    val metadata = if (frame == null) mapOf("state" to "waiting", "source" to source)
                        else mapOf("state" to "ready", "source" to source, "revision" to frame.revision,
                            "width" to frame.width, "height" to frame.height, "capturedAt" to frame.capturedAt,
                            "image" to "/frame/${frame.revision}.png", "nativeEngineCapture" to true,
                            "captureMethod" to if (source == "f2") "minecraft-f2" else "windows-graphics-capture")
                    bytes = gson.toJson(metadata).toByteArray(Charsets.UTF_8); contentType = "application/json; charset=utf-8"
                }
                Regex("/frame/[a-f0-9]{64}\\.png").matches(path) -> {
                    val revision = path.removePrefix("/frame/").removeSuffix(".png")
                    val frame = synchronized(this) { retained[revision] }
                    if (frame == null) { e.sendResponseHeaders(410, -1); return@use }
                    bytes = frame.bytes; contentType = "image/png"
                    e.responseHeaders.set("ETag", "\"$revision\"")
                }
                else -> { e.sendResponseHeaders(404, -1); return@use }
            }
            e.responseHeaders.set("Content-Type", contentType)
            e.responseHeaders.set("Cache-Control", "no-store")
            e.responseHeaders.set("X-Content-Type-Options", "nosniff")
            if (path == "/") e.responseHeaders.set("Content-Security-Policy",
                "default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'")
            e.sendResponseHeaders(200, bytes.size.toLong()); e.responseBody.write(bytes)
        } }
        refresh()
        executor.scheduleWithFixedDelay({ refresh() }, 50, 50, java.util.concurrent.TimeUnit.MILLISECONDS)
        http.start()
    }

    @Synchronized fun refresh() {
        for (source in listOf("window", "f2")) {
            try {
                val path = if (source == "window") captureDirectory.resolve("latest.png")
                    else if (Files.isDirectory(screenshotsDirectory)) Files.list(screenshotsDirectory).use { paths ->
                        paths.filter { it.fileName.toString().endsWith(".png", true) && Files.isRegularFile(it) && !Files.isSymbolicLink(it) }
                            .max(compareBy<Path> { Files.getLastModifiedTime(it).toMillis() }).orElse(null)
                    } else null
                if (path == null || !Files.isRegularFile(path) || Files.isSymbolicLink(path)) continue
                val fingerprint = Fingerprint(path, Files.size(path), Files.getLastModifiedTime(path).toMillis())
                if (fingerprints[source] == fingerprint || fingerprint.size !in 1..32_000_000) continue
                val bytes = Files.readAllBytes(path)
                // Decode only to validate dimensions/completeness; deliver the original bytes.
                val image = ImageIO.read(bytes.inputStream()) ?: continue
                if (image.width !in 1..8192 || image.height !in 1..8192) continue
                val revision = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                val frame = Frame(source, revision, image.width, image.height,
                    Instant.ofEpochMilli(fingerprint.modified).toString(), bytes)
                retained[revision] = frame; frames[source] = frame; fingerprints[source] = fingerprint
                // Keep recent responses immutable while a browser loads the next revision.
                while (retained.size > 6) {
                    val old = retained.keys.firstOrNull { id -> frames.values.none { it.revision == id } } ?: break
                    retained.remove(old)
                }
            } catch (_: Exception) { /* A partial F2 write is retried; the last complete frame remains valid. */ }
        }
    }

    override fun close() { http.stop(0); executor.shutdownNow() }
}

fun main(args: Array<String>) {
    require(args.size in 3..4) { "NativeEnginePreview <capture directory> <F2 directory> <HTML page> [port]" }
    val preview = NativeEnginePreview(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), args.getOrNull(3)?.toInt() ?: 18100)
    Runtime.getRuntime().addShutdownHook(Thread { preview.close() })
    println("NATIVE_ENGINE_PREVIEW_READY ${preview.address}")
    java.util.concurrent.CountDownLatch(1).await()
}
