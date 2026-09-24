package dev.projects.webui

import net.kyori.adventure.text.Component
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.timer.TaskSchedule
import java.nio.file.Files
import java.nio.file.Path

/** Separate loopback-only sandbox; no gameplay saves, economy, client mod, JS engine or RP override. */
fun main(args: Array<String>) {
    // This process contains only the UI laboratory, not gameplay. Shorten BOTH polling and inbound
    // packet-queue intervals. Do not apply this property to the production game server.
    val uiTps=System.getProperty("projects.ui.tps","60").toInt()
    require(uiTps in setOf(20,60)) { "UI lab supports 20 or 60 TPS" }
    System.setProperty("minestom.tps",uiTps.toString())
    val source=Path.of(args.firstOrNull()?:"ui/forge.html").toAbsolutePath()
    UiDocument.parse(Files.readString(source)).layout(ForgeDemo().values(),ForgeDemo().flags())
    val server=MinecraftServer.init(Auth.Offline())
    val instance=MinecraftServer.getInstanceManager().createInstanceContainer().apply {
        setChunkSupplier(::LightingChunk)
        setGenerator { it.modifier().fillHeight(0,1,Block.STONE_BRICKS) }
        time=6000
    }
    val events=MinecraftServer.getGlobalEventHandler()
    val pack=System.getProperty("projects.ui.pack")?.let { Polish05Pack.start(Path.of(it),System.getProperty("projects.ui.packPort","18091").toInt()) }
    val sessions=UiSessions(events,source) { player -> pack?.ready(player) ?: true }
    val port=System.getProperty("projects.ui.port","25570").toInt()
    val previewPort=System.getProperty("projects.ui.previewPort","18090").toInt()
    require(port in 1024..65535 && port !in setOf(25565,25566) && previewPort!=port)
    val preview=UiPreview(source,previewPort)
    events.addListener(AsyncPlayerConfigurationEvent::class.java) {
        it.spawningInstance=instance;it.player.respawnPoint=Pos(0.0,1.0,0.0)
    }
    events.addListener(PlayerSpawnEvent::class.java) { e ->
        if(e.isFirstSpawn) {
            val player=e.player
            player.gameMode=GameMode.ADVENTURE
            player.inventory.setItemStack(0,ItemStack.of(Material.COMPASS).withCustomName(Component.text("UI試作を開く")))
            player.sendMessage(Component.text("UI試作：コンパスを右クリック /ui。マウスで選択、左クリックで決定、Shiftで終了。ページは画面内の前へ・次へ。"))
            println("UI_LAB_PLAYER_CONNECTED ${player.username}")
            pack?.offer(player)
            if(java.lang.Boolean.getBoolean("projects.ui.openOnJoin")) {
                player.scheduler().buildTask {
                    if(player.instance===instance && !player.isRemoved) {
                        try { sessions.open(player);println("UI_LAB_AUTO_OPEN ${player.username}") }
                        catch(ex: Exception) { ex.printStackTrace() }
                    }
                }.delay(TaskSchedule.duration(java.time.Duration.ofMillis(1500))).schedule()
            }
        }
    }
    events.addListener(PlayerDisconnectEvent::class.java) { pack?.forget(it.player) }
    events.addListener(PlayerUseItemEvent::class.java) {
        if(it.player.itemInMainHand.material()==Material.COMPASS) { it.isCancelled=true;sessions.open(it.player) }
    }
    val command=Command("ui")
    command.setDefaultExecutor { sender,_ -> if(sender is Player) {
        try { sessions.open(sender) } catch(ex: Exception) { sender.sendMessage(Component.text("UIを開けません：${ex.message?.take(160)}")) }
    } }
    MinecraftServer.getCommandManager().register(command)
    val closeCommand=Command("uiclose")
    closeCommand.setDefaultExecutor { sender,_ -> if(sender is Player) sessions.close(sender) }
    MinecraftServer.getCommandManager().register(closeCommand)
    Runtime.getRuntime().addShutdownHook(Thread { sessions.close();pack?.close();preview.close() })
    server.start("127.0.0.1",port)
    println("UI_LAB_READY minecraft=127.0.0.1:$port preview=http://127.0.0.1:$previewPort inputTps=$uiTps cursor=immediate-position source=$source")
}
