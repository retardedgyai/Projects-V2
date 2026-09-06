package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.network.packet.server.play.CloseWindowPacket
import net.minestom.server.timer.TaskSchedule

/** Explicit, separate executable for real Vanilla architectural photographs. No account/save/game runtime.
 * Fixed spawn views are not manual gameplay automation and never move an existing Creator session.
 * Launched explicitly by scripts/start-harbor-preview.ps1, never by the gameplay entry point.
 */
object HarborPreviewServer {
    @JvmStatic
    fun main(args: Array<String>) {
        val server = MinecraftServer.init(Auth.Offline())
        val harbor = MinecraftServer.getInstanceManager().createInstanceContainer()
        HarborScene.build(harbor)
        val views = mapOf(
            "HarborArrival" to Pos(0.5, 41.0, 37.5, 180f, -14f),
            "HarborMarket" to Pos(0.5, 41.0, 12.5, 180f, -22f),
            "HarborGallery" to Pos(11.5, 53.0, -27.5, 120f, -18f),
            "HarborQuay" to Pos(-24.5, 41.0, 23.5, -70f, -14f),
            "HarborShipyard" to Pos(26.5, 41.0, 20.5, -145f, -14f),
            "HarborFoundry" to Pos(-7.5, 41.0, 1.5, 123f, -15f),
            "HarborAcademy" to Pos(8.5, 41.0, 0.5, -125f, -22f),
            "HarborOverview" to Pos(43.5, 72.0, 50.5, 147f, 24f),
        )
        val lockCamera = System.getProperty("projects.harbor.preview.lockCamera", "true").toBoolean()
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            event.spawningInstance = harbor
            event.player.respawnPoint = views[event.player.username] ?: views.getValue("HarborArrival")
            event.player.gameMode = GameMode.SPECTATOR
            event.player.setNoGravity(true)
        }
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent::class.java) { event ->
            println("HARBOR_PREVIEW_CONNECTED player=${event.player.username} camera=${event.player.position}")
            // A photographic viewport, not a manual-smoke session. Startup focus/input must not alter comparisons.
            if (event.isFirstSpawn && lockCamera) event.player.scheduler().submitTask {
                if (!event.player.isOnline) TaskSchedule.stop()
                else {
                    event.player.sendPacket(CloseWindowPacket(0))
                    TaskSchedule.seconds(2)
                }
            }
        }
        if (lockCamera) MinecraftServer.getGlobalEventHandler().addListener(PlayerMoveEvent::class.java) { event ->
            event.isCancelled = true
        }
        val port = System.getProperty("projects.harbor.preview.port", "25575").toInt()
        require(port != 25565) { "Architectural preview must not use the gameplay port" }
        server.start("127.0.0.1", port)
        println("HARBOR_PREVIEW_READY address=127.0.0.1:$port persistence=none")
    }
}
