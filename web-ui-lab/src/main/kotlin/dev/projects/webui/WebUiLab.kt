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
    val source=Path.of(args.firstOrNull()?:"ui/forge.html").toAbsolutePath()
    UiDocument.parse(Files.readString(source)).layout(ForgeDemo().values(),ForgeDemo().flags())
    val server=MinecraftServer.init(Auth.Offline())
    val instance=MinecraftServer.getInstanceManager().createInstanceContainer().apply {
        setChunkSupplier(::LightingChunk)
        setGenerator { it.modifier().fillHeight(0,1,Block.STONE_BRICKS) }
        time=6000
    }
    val events=MinecraftServer.getGlobalEventHandler()
    val sessions=UiSessions(events,source)
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
            player.sendMessage(Component.text("UI試作：コンパスを右クリック /ui。マウスで選択、クリックで決定、Shiftで終了。追加遅延・表示サイズは画面下で変更できます。"))
            println("UI_LAB_PLAYER_CONNECTED ${player.username}")
        }
    }
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
    Runtime.getRuntime().addShutdownHook(Thread { sessions.close();preview.close() })
    server.start("127.0.0.1",port)
    println("UI_LAB_READY minecraft=127.0.0.1:$port preview=http://127.0.0.1:$previewPort source=$source")
}
