package dev.projects.webui

import com.sun.net.httpserver.HttpServer
import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.resource.ResourcePackStatus
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerResourcePackStatusEvent
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/** Serves the approved private-namespace art as an optional Vanilla resource pack. */
class Polish05Pack private constructor(
    private val http: HttpServer,
    private val executor: java.util.concurrent.ExecutorService,
    private val info: ResourcePackInfo,
) : AutoCloseable {
    private data class Offer(val player: Player, val id: UUID, var loaded: Boolean = false)
    private val offers = ConcurrentHashMap<UUID, Offer>()
    private val listener = EventListener.of(PlayerResourcePackStatusEvent::class.java) { event ->
        val offer = offers[event.player.uuid] ?: return@of
        if (offer.player !== event.player || offer.id != event.packUuid) return@of
        when (event.status) {
            ResourcePackStatus.SUCCESSFULLY_LOADED -> {
                offer.loaded = true
                event.player.sendMessage(net.kyori.adventure.text.Component.text("Polish05の工房素材を読み込みました。"))
                println("POLISH05_PACK_LOADED ${event.player.username}")
            }
            ResourcePackStatus.DECLINED, ResourcePackStatus.FAILED_DOWNLOAD,
            ResourcePackStatus.FAILED_RELOAD, ResourcePackStatus.DISCARDED,
            ResourcePackStatus.INVALID_URL -> {
                offer.loaded = false
                println("POLISH05_PACK_UNAVAILABLE ${event.player.username} ${event.status}")
            }
            else -> Unit
        }
    }

    init { MinecraftServer.getGlobalEventHandler().addListener(listener) }

    fun ready(player: Player): Boolean = offers[player.uuid]?.let { it.player === player && it.loaded } == true

    fun offer(player: Player) {
        val id = UUID.randomUUID()
        offers[player.uuid] = Offer(player, id)
        val request = ResourcePackRequest.resourcePackRequest()
            .packs(ResourcePackInfo.resourcePackInfo(id, info.uri(), info.hash()))
            .required(false).replace(false)
            .prompt(net.kyori.adventure.text.Component.text("Polish05の工房UI素材を適用します。"))
            .build()
        player.sendResourcePacks(request)
    }

    fun forget(player: Player) { offers.remove(player.uuid)?.takeIf { it.player === player } }

    override fun close() {
        MinecraftServer.getGlobalEventHandler().removeListener(listener)
        offers.clear()
        http.stop(0)
        executor.shutdownNow()
    }

    companion object {
        fun start(path: Path, port: Int): Polish05Pack = start(Files.readAllBytes(path), port)

        fun start(bytes: ByteArray, port: Int): Polish05Pack {
            require(port in 1024..65535 && port !in setOf(25565, 25566, 25570, 18090))
            require(bytes.isNotEmpty() && bytes.size <= 32_000_000) { "Invalid Polish05 resource pack size" }
            val entries = mutableSetOf<String>()
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.name.startsWith('/') && !entry.name.contains("..") && entries.add(entry.name)) {
                        "Unsafe or duplicate pack entry: ${entry.name}"
                    }
                }
            }
            require("pack.mcmeta" in entries && entries.any { it.startsWith("assets/projects_ui_polish05/") }) {
                "Polish05 pack needs pack.mcmeta and its private asset namespace"
            }
            val hash = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
            val urlPath = "/polish05-$hash.zip"
            val http = HttpServer.create(InetSocketAddress("127.0.0.1", port), 8)
            val executor = Executors.newFixedThreadPool(2) { task -> Thread(task, "polish05-pack-http").apply { isDaemon = true } }
            http.executor = executor
            http.createContext(urlPath) { exchange ->
                try {
                    if (exchange.requestURI.path != urlPath || exchange.requestMethod !in setOf("GET", "HEAD")) {
                        exchange.sendResponseHeaders(404, -1)
                    } else {
                        exchange.responseHeaders.set("Content-Type", "application/zip")
                        exchange.responseHeaders.set("ETag", hash)
                        if (exchange.requestMethod == "HEAD") {
                            exchange.responseHeaders.set("Content-Length", bytes.size.toString())
                            exchange.sendResponseHeaders(200, -1)
                        } else {
                            exchange.sendResponseHeaders(200, bytes.size.toLong())
                            exchange.responseBody.use { it.write(bytes) }
                        }
                    }
                } finally { exchange.close() }
            }
            try {
                http.start()
                val uri = URI.create("http://127.0.0.1:$port$urlPath")
                println("POLISH05_PACK_READY url=$uri sha1=$hash bytes=${bytes.size}")
                return Polish05Pack(http, executor, ResourcePackInfo.resourcePackInfo(UUID.randomUUID(), uri, hash))
            } catch (failure: Exception) {
                http.stop(0); executor.shutdownNow(); throw failure
            }
        }
    }
}
