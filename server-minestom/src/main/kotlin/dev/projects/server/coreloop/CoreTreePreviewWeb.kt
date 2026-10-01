package dev.projects.server.coreloop

import com.google.gson.GsonBuilder
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.projects.webui.ForgeLightPhase
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Local inspection only: runs the same native flow without starting Minestom or opening player saves. */
internal object CoreTreePreviewWeb {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    fun fixture() = CoreAccount(UUID.fromString("00000000-0000-0000-0000-000000000125"), weaponTier = 2, armorTier = 2,
        journey = CoreJourney.fresh().copy(chosen = true, xp = CoreJourneyRules.threshold(12), build = CoreClassBuild(nodes = 1)))

    @JvmStatic fun main(args: Array<String>) {
        require(args.size in 2..3) { "Usage: repo output [port]" }
        val repo = Path.of(args[0]).toAbsolutePath().normalize()
        val output = Path.of(args[1]).toAbsolutePath().normalize()
        Files.createDirectories(output)
        val spriteJson = Files.readString(repo.resolve("web-ui-lab/ui/polish05-font-map.json"))
        fun flow() = CoreTreePreviewFlow(CoreTreePreview(fixture()), spriteJson)
        val model = flow()
        model.action("node:warrior:9")
        fun state() = gson.toJson(mapOf("scene" to model.scene(ForgeLightPhase.IDLE), "snapshot" to model.snapshot(),
            "profile" to "比較キャラクター / Lv12・T2装備", "source" to "CoreClassTrees / CoreSkillCatalog.modify"))
        Files.writeString(output.resolve("selected.json"), state())
        model.action("trial:toggle")
        Files.writeString(output.resolve("trial.json"), state())
        model.action("trial:reset")
        val html = Files.readString(repo.resolve("assets/core-ui/tree-preview/preview.html"))
        Files.writeString(output.resolve("preview.html"), html)
        if (args.size == 2) return
        val port = args[2].toInt()
        require(port in 1024..65535 && port !in setOf(25565, 25566))
        val icons = CoreSkillCatalog.artNames.toSet()
        val spriteNames = com.google.gson.JsonParser.parseString(spriteJson).asJsonObject.keySet()
        fun bytes(exchange: HttpExchange, data: ByteArray, type: String) {
            exchange.responseHeaders.add("Content-Type", type)
            exchange.responseHeaders.add("Cache-Control", "no-store")
            exchange.sendResponseHeaders(200, data.size.toLong())
            exchange.responseBody.use { it.write(data) }
        }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        server.createContext("/") { e ->
            try {
                val path = e.requestURI.path
                when {
                    path == "/" && e.requestMethod == "GET" -> bytes(e, html.toByteArray(Charsets.UTF_8), "text/html; charset=utf-8")
                    path == "/state" && e.requestMethod == "GET" -> synchronized(model) { bytes(e, state().toByteArray(Charsets.UTF_8), "application/json; charset=utf-8") }
                    path == "/action" && e.requestMethod == "POST" -> synchronized(model) {
                        val action = e.requestBody.use { it.readNBytes(100).toString(Charsets.UTF_8) }
                        if (action == "close") bytes(e, "{\"closed\":true}".toByteArray(), "application/json")
                        else if (model.action(action)) bytes(e, state().toByteArray(Charsets.UTF_8), "application/json; charset=utf-8")
                        else { e.sendResponseHeaders(400, -1); e.close() }
                    }
                    path.startsWith("/asset/icon/") && path.substringAfterLast('/') in icons -> {
                        val icon = path.substringAfterLast('/')
                        bytes(e, Files.readAllBytes(repo.resolve("server-minestom/src/main/resources/core-ui-pack/assets/projects/textures/item/core_ui/$icon.png")), "image/png")
                    }
                    path.startsWith("/asset/sprite/") && path.removePrefix("/asset/sprite/") in spriteNames -> {
                        val name = path.removePrefix("/asset/sprite/")
                        require(name.startsWith("window_chrome/"))
                        bytes(e, Files.readAllBytes(repo.resolve("assets/ui/polish05-import/resourcepack/assets/projects_ui_polish05/textures/plates/${name.replace('/', '_')}.png")), "image/png")
                    }
                    path in setOf("/asset/sans.ttf", "/asset/serif.ttf") -> bytes(e,
                        Files.readAllBytes(repo.resolve("web-ui-lab/ui/polish05-fonts/${path.substringAfterLast('/') }")), "font/ttf")
                    path == "/sprites" -> bytes(e, spriteJson.toByteArray(Charsets.UTF_8), "application/json; charset=utf-8")
                    else -> { e.sendResponseHeaders(404, -1); e.close() }
                }
            } catch (_: Exception) { runCatching { e.sendResponseHeaders(500, -1) }; e.close() }
        }
        Runtime.getRuntime().addShutdownHook(Thread { server.stop(0) })
        server.start()
        println("TREE_PREVIEW_READY http://127.0.0.1:$port/ output=$output save=none")
    }
}
