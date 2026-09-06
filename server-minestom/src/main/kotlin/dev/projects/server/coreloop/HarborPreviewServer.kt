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
            "HarborHall" to Pos(0.5, 53.0, -33.5, 180f, -18f),
            "HarborOutlook" to Pos(0.5, 53.0, -36.5, 0f, -8f),
            "HarborRear" to Pos(10.5, 53.0, -49.5, 180f, -8f),
            "HarborAlley" to Pos(-27.5, 41.0, 12.5, 180f, -14f),
            "HarborArcade" to Pos(-5.5, 41.0, 15.5, 180f, -12f),
            "HarborCanopy" to Pos(1.5, 41.0, 17.5, -145f, -22f),
            "HarborBerth" to Pos(-13.5, 41.0, 23.5, 50f, -10f),
            "HarborBoarding" to Pos(-10.5, 41.0, 34.5, 90f, -8f),
            "HarborCutter" to Pos(20.5, 41.0, 34.5, -85f, -18f),
            "HarborCoast" to Pos(48.5, 39.0, -21.5, 90f, -20f),
            "HarborCoastSouth" to Pos(46.5, 41.0, -11.5, 135f, -15f),
            "HarborLodging" to Pos(34.5, 45.0, -17.5, 180f, -6f),
            "HarborRooms" to Pos(33.5, 51.0, -25.5, 0f, -5f),
            "HarborBunk" to Pos(36.5, 51.0, -26.5, -110f, 10f),
            "HarborOverview" to Pos(43.5, 72.0, 50.5, 147f, 24f),
        )
        require(views.keys.all { it.matches(Regex("[A-Za-z0-9_]{1,16}")) }) { "Camera names must be valid login usernames" }
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
