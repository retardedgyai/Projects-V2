package dev.projects.modellab

import com.sun.net.httpserver.HttpServer
import com.yuuki14202028.WseeAssets
import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.text.Component
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.event.player.PlayerResourcePackStatusEvent
import net.kyori.adventure.resource.ResourcePackStatus
import net.minestom.server.event.server.ServerTickMonitorEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.LightingChunk
import net.minestom.server.command.builder.arguments.ArgumentType
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

/** Explicit opt-in laboratory, not the production server. Does not load saves or move existing players. */
fun main(args: Array<String>) {
    val server = MinecraftServer.init(Auth.Offline())
    val bundle = ModelBundle(Path.of(args.single()).resolve("bundle"))
    bundle.load()
    val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
    instance.setChunkSupplier(::LightingChunk)
    instance.setGenerator { it.modifier().fillHeight(0, 1, Block.SMOOTH_STONE) }
    instance.time = 6000
    val actors = java.util.concurrent.ConcurrentHashMap<UUID, BossModelActor>()
    val metrics = TickMetrics()
    val packBytes = Files.readAllBytes(bundle.directory.resolve("pack.zip"))
    val packHash = MessageDigest.getInstance("SHA-1").digest(packBytes).joinToString("") { "%02x".format(it) }
    val port = System.getProperty("projects.models.port", "25566").toInt()
    val httpPort = System.getProperty("projects.models.packPort", "18086").toInt()
    val http = HttpServer.create(InetSocketAddress("127.0.0.1", httpPort), 0)
    http.createContext("/pack.zip") { exchange ->
        exchange.use {
            if (exchange.requestURI.path != "/pack.zip" || exchange.requestMethod != "GET") {
                exchange.sendResponseHeaders(404, -1)
            } else {
                exchange.responseHeaders.add("Content-Type", "application/zip")
                exchange.sendResponseHeaders(200, packBytes.size.toLong())
                exchange.responseBody.use { it.write(packBytes) }
            }
        }
    }
    val info = ResourcePackInfo.resourcePackInfo(UUID.nameUUIDFromBytes(packHash.toByteArray()),
        URI("http://127.0.0.1:$httpPort/pack.zip"), packHash)
    val events = MinecraftServer.getGlobalEventHandler()
    val menu = LabMenu(events)
    val iceFang = IceFangTraining(bundle, instance)
    val packReady = java.util.concurrent.ConcurrentHashMap.newKeySet<UUID>()
    val testUi = LabTestUi(events,menu,bundle,instance,iceFang,actors,{ it.uuid in packReady },metrics)
    events.addListener(PlayerResourcePackStatusEvent::class.java) {
        if (it.packUuid == info.id()) {
            if (it.status == ResourcePackStatus.SUCCESSFULLY_LOADED) packReady.add(it.player.uuid)
            else packReady.remove(it.player.uuid)
        }
    }
    events.addListener(AsyncPlayerConfigurationEvent::class.java) {
        it.spawningInstance = instance
        it.player.respawnPoint = Pos(0.5, 1.0, -8.5)
    }
    events.addListener(PlayerSpawnEvent::class.java) {
        if (it.isFirstSpawn) {
            it.player.gameMode = GameMode.CREATIVE
            it.player.sendResourcePacks(ResourcePackRequest.resourcePackRequest().packs(info).required(true).build())
            testUi.giveOpener(it.player)
            it.player.sendMessage(Component.text("テスト工房：ホットバー9番のコンパスを右クリック。Shift＋Fでもメニューを開けます。"))
        }
    }
    events.addListener(PlayerDisconnectEvent::class.java) { actors.remove(it.player.uuid)?.close() }
    events.addListener(PlayerDisconnectEvent::class.java) { iceFang.remove(it.player); packReady.remove(it.player.uuid) }
    events.addListener(PlayerUseItemEvent::class.java) {
        if (iceFang.uses(it.player, it.hand)) { it.isCancelled = true; iceFang.requestCast(it.player) }
    }
    events.addListener(PlayerBlockInteractEvent::class.java) {
        if (iceFang.uses(it.player, it.hand)) { it.isCancelled = true; iceFang.requestCast(it.player) }
    }
    events.addListener(PlayerEntityInteractEvent::class.java) {
        if (iceFang.uses(it.player, it.hand)) iceFang.requestCast(it.player)
    }
    events.addListener(InstanceTickEvent::class.java) { if (it.instance === instance) iceFang.tick() }
    events.addListener(InstanceTickEvent::class.java) { if (it.instance === instance) actors.values.forEach(BossModelActor::syncViewers) }
    events.addListener(ServerTickMonitorEvent::class.java) { metrics.record(it.tickMonitor.tickTime) }

    fun command(name: String, execute: (Player, List<String>) -> Unit) {
        val command = Command(name)
        val words = ArgumentType.StringArray("arguments")
        command.setDefaultExecutor { sender, _ ->
            if (sender is Player) runCatching { execute(sender, emptyList()) }.onFailure {
                sender.sendMessage(Component.text("実行できません：${it.message}"))
            }
        }
        command.addSyntax({ sender, context ->
            if (sender is Player) runCatching { execute(sender, context.get(words).toList()) }.onFailure {
                sender.sendMessage(Component.text("実行できません：${it.message}"))
            }
        }, words)
        MinecraftServer.getCommandManager().register(command)
    }
    // These references are generated from the actual bbmodel files, not handwritten string IDs.
    val bosses = mapOf("osirion" to WseeAssets.Osirion.Model, "radix" to WseeAssets.Radix.Model,
        "vesper" to WseeAssets.Vesper.Model, "piglin_lord" to WseeAssets.PiglinLord.Model)
    command("models") { player, _ -> player.sendMessage(Component.text("モデル：${bundle.definitions.keys.joinToString()}")) }
    command("test") { player, _ -> testUi.home(player) }
    command("menu") { player, _ -> testUi.home(player) }
    command("mage") { player, _ ->
        if (player.uuid in packReady) iceFang.equip(player)
        else player.sendMessage(Component.text("リソースパックの適用完了後に /mage を実行してください。"))
    }
    command("mageclear") { player, _ -> iceFang.remove(player) }
    command("modelmenu") { player, _ ->
        testUi.bosses(player)
    }
    command("model") { player, words ->
        val name = words.firstOrNull() ?: "vesper"
        val id = bosses[name] ?: "$name.bbmodel"
        val definition = bundle.definition(id)
        actors.remove(player.uuid)?.close()
        actors[player.uuid] = BossModelActor(definition, instance, player.position.add(0.0, 0.0, 6.0))
        player.sendMessage(Component.text("再生可能：${definition.animations.keys.joinToString()}"))
    }
    command("anim") { player, words ->
        val actor = requireNotNull(actors[player.uuid]) { "先に /model を実行してください" }
        actor.play(words.firstOrNull() ?: "idle")
    }
    command("loop") { player, words ->
        val actor = requireNotNull(actors[player.uuid]) { "先に /model を実行してください" }
        actor.repeat(words.firstOrNull() ?: "idle")
    }
    command("bone") { player, words ->
        require(words.size == 2) { "/bone 部位名 true|false" }
        requireNotNull(actors[player.uuid]) { "先に /model" }.setBoneVisible(words[0], words[1].toBooleanStrict())
    }
    command("modelclear") { player, _ -> actors.remove(player.uuid)?.close() }
    command("modelstats") { player, _ -> player.sendMessage(Component.text(metrics.summary(instance.entities.size))) }
    Runtime.getRuntime().addShutdownHook(Thread {
        iceFang.close()
        actors.values.forEach(BossModelActor::close)
        http.stop(0)
    })
    http.start()
    server.start("127.0.0.1", port)
    println("MODEL_LAB_READY 127.0.0.1:$port pack=$packHash (isolated, no ProjectS saves)")
}
