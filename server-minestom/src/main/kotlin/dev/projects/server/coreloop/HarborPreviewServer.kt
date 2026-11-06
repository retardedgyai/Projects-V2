package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
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
        val scene = HarborScene.build(harbor)
        // The arrival, then each facility from six blocks south of it, looking north at it.
        val views = mapOf("HarborArrival" to scene.spawn.withPitch(-14f)) + scene.facilities.associate { facility ->
            "Harbor" + facility.kind.name.lowercase().replaceFirstChar { it.uppercase() }.take(9) to
                facility.position.add(0.0, 2.0, 6.0).withView(180f, 18f)
        }
        require(views.keys.all { it.matches(Regex("[A-Za-z0-9_]{1,16}")) }) { "Camera names must be valid login usernames" }
        val lockCamera = System.getProperty("projects.harbor.preview.lockCamera", "true").toBoolean()
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            event.spawningInstance = harbor
            event.player.respawnPoint = if (lockCamera) {
                views[event.player.username] ?: views.getValue("HarborArrival")
            } else scene.spawn
            event.player.gameMode = if (lockCamera) GameMode.SPECTATOR else GameMode.ADVENTURE
            event.player.setNoGravity(lockCamera)
        }
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent::class.java) { event ->
            println("HARBOR_PREVIEW_CONNECTED player=${event.player.username} camera=${event.player.position} mode=${event.player.gameMode} cameraLocked=$lockCamera")
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
        println("HARBOR_PREVIEW_READY address=127.0.0.1:$port persistence=none cameraLocked=$lockCamera")
    }
}
