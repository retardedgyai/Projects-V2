package dev.projects.server.coreloop.ui

import com.sun.net.httpserver.HttpServer
import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.resource.ResourcePackStatus
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerResourcePackStatusEvent
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Optional local HTTP distribution; a pack rejection must never block login or game actions. */
class CoreUiPackServer private constructor(
    private val server: HttpServer,
    private val executor: java.util.concurrent.ExecutorService,
    private val info: ResourcePackInfo,
) : AutoCloseable {
    private class Offer(val player: Player, val callback: (Player, Boolean) -> Unit) {
        val state = CoreUiPackOfferState()
    }
    private val offers = ConcurrentHashMap<UUID, Offer>()
    private val closed = AtomicBoolean()
    private var listenerRegistered = false
    private val statusListener = EventListener.of(PlayerResourcePackStatusEvent::class.java) { event ->
        handleStatus(event.player, event.packUuid, event.status)
    }
    val uri: URI get() = info.uri()

    fun enabled(player: Player): Boolean = !closed.get() && offers[player.uuid]?.let { it.player === player && it.state.loaded } == true

    @Synchronized
    fun offer(player: Player, onChanged: (Player, Boolean) -> Unit = { _, _ -> }) {
        if (closed.get() || !player.isOnline) return
        // Minestom removes request callbacks at the first terminal status. Its event stream also
        // contains later DISCARDED/FAILED_RELOAD, so own this listener for the service's lifetime.
        if (!listenerRegistered) {
            MinecraftServer.getGlobalEventHandler().addListener(statusListener)
            listenerRegistered = true
        }
        val offer = Offer(player, onChanged)
        val previous = offers.put(player.uuid, offer)
        val wasLoaded = previous?.state?.loaded == true
        previous?.state?.invalidate()
        if (previous != null && previous.player === player) {
            runCatching { player.removeResourcePacks(previous.state.packId) }
        }
        if (wasLoaded) publish(offer, offer.state.current(), announce = false)
        // The asset hash/URL stays cacheable, but every offer needs its own protocol ID: a delayed
        // status for a removed pack must not enable the replacement offer on the same connection.
        val offeredInfo = ResourcePackInfo.resourcePackInfo(offer.state.packId, info.uri(), info.hash())
        val request = ResourcePackRequest.resourcePackRequest().packs(offeredInfo).required(false).replace(false)
            .prompt(CoreUiComponents.text("ProjectSのUI・アイコンを適用します。拒否しても通常表示で遊べます。"))
            .callback { id, status, _ ->
                handleStatus(player, id, status)
            }.build()
        runCatching { player.sendResourcePacks(request) }.onFailure { failure ->
            handleStatus(player, offer.state.packId, ResourcePackStatus.FAILED_DOWNLOAD)
            System.err.println("CORE_UI_PACK_OFFER_FAILED player=${player.username}: ${failure.message}")
        }
    }

    @Synchronized
    private fun handleStatus(player: Player, packId: UUID, status: ResourcePackStatus) {
        if (closed.get()) return
        val offer = offers[player.uuid]?.takeIf { it.player === player } ?: return
        val change = offer.state.accept(packId, status) ?: return
        publish(offer, change)
        println("CORE_UI_PACK player=${player.username} status=$status customGlyphs=${change.loaded}")
    }

    private fun publish(offer: Offer, change: CoreUiPackOfferState.Change, announce: Boolean = true) {
        MinecraftServer.getSchedulerManager().scheduleNextTick {
            synchronized(this) {
                val player = offer.player
                if (!closed.get() && player.isOnline && offers[player.uuid] === offer && offer.state.isCurrent(change)) {
                    if (announce) player.sendMessage(CoreUiComponents.text(if (change.loaded) "ProjectSのUIを適用しました。" else "通常表示で続けます。ゲーム機能はそのまま利用できます。"))
                    offer.callback(player, change.loaded)
                }
            }
        }
    }

    @Synchronized
    fun forget(player: Player) {
        val offer = offers[player.uuid]?.takeIf { it.player === player } ?: return
        offer.state.invalidate()
        offers.remove(player.uuid, offer)
    }

    @Synchronized
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        if (listenerRegistered) {
            runCatching { MinecraftServer.getGlobalEventHandler().removeListener(statusListener) }
                .onFailure { System.err.println("CORE_UI_PACK_LISTENER_CLOSE_FAILED: ${it.message}") }
            listenerRegistered = false
        }
        offers.values.forEach { it.state.invalidate() }
        offers.clear(); server.stop(0); executor.shutdownNow()
    }

    companion object {
        /** Default address is intentionally loopback, matching this local solo test server. */
        fun start(): CoreUiPackServer? = runCatching {
            val bytes = bundle()
            val hash = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
            val id = UUID.nameUUIDFromBytes("projects:core-ui:$hash".toByteArray(Charsets.UTF_8))
            val host = System.getProperty("projects.ui.host", "127.0.0.1")
            val port = System.getProperty("projects.ui.port", "25566").toInt()
            val path = "/projects-core-ui-$hash.zip"
            val http = HttpServer.create(InetSocketAddress(host, port), 8)
            val pool = Executors.newFixedThreadPool(2) { task -> Thread(task, "projects-ui-pack-http").apply { isDaemon = true } }
            http.executor = pool
            http.createContext(path) { exchange ->
                try {
                    if (exchange.requestURI.path != path || exchange.requestMethod !in setOf("GET", "HEAD")) {
                        exchange.sendResponseHeaders(404, -1)
                    } else {
                        exchange.responseHeaders.set("Content-Type", "application/zip")
                        exchange.responseHeaders.set("Cache-Control", "public, max-age=31536000, immutable")
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
                val advertised = System.getProperty("projects.ui.publicUrl", "http://127.0.0.1:${http.address.port}$path")
                val info = ResourcePackInfo.resourcePackInfo(id, URI.create(advertised), hash)
                println("CORE_UI_PACK_READY url=$advertised sha1=$hash bytes=${bytes.size}")
                CoreUiPackServer(http, pool, info)
            } catch (failure: Exception) { http.stop(0); pool.shutdownNow(); throw failure }
        }.getOrElse { failure ->
            System.err.println("CORE_UI_PACK_DISABLED: ${failure.message}. Plain Japanese UI remains available.")
            null
        }

        internal fun bundle(): ByteArray {
            val loader = CoreUiPackServer::class.java.classLoader
            val paths = requireNotNull(loader.getResourceAsStream("core-ui-pack/index.txt")) { "Missing UI asset index" }
                .bufferedReader(Charsets.UTF_8).use { reader -> reader.readLines().filter { it.isNotBlank() && !it.startsWith('#') } }
            require(paths.isNotEmpty() && paths.distinct().size == paths.size)
            require(paths.all(CoreUiPackPolicy::allowedPath)) {
                "Only the explicitly scoped player heart/food sprite overrides are allowed; never global fonts"
            }
            val output = ByteArrayOutputStream()
            ZipOutputStream(output).use { zip ->
                paths.sorted().forEach { path ->
                    val data = requireNotNull(loader.getResourceAsStream("core-ui-pack/$path")) { "Missing UI asset $path" }.use { it.readBytes() }
                    zip.putNextEntry(ZipEntry(path).apply { time = 0L }); zip.write(data); zip.closeEntry()
                }
            }
            return output.toByteArray()
        }
    }
}
