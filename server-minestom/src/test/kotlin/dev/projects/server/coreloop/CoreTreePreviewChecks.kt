package dev.projects.server.coreloop

import dev.projects.webui.*
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerPacketEvent
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.client.play.*
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.packet.server.play.*
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Focused executable checks: genuine catalog, account immutability and native packet/session cleanup. */
object CoreTreePreviewChecks {
    @JvmStatic fun main(args: Array<String>) {
        val repo = Path.of(args.single())
        val spriteJson = Files.readString(repo.resolve("web-ui-lab/ui/polish05-font-map.json"))
        val account = CoreTreePreviewWeb.fixture()
        val encoded = CoreAccountCodec.encode(account)
        val model = CoreTreePreview(account)
        check(model.select("warrior:9"))
        val selected = model.snapshot()
        check(selected.spent == 1 && !selected.changed && selected.canToggle)
        check(selected.nodes.size == CoreClassTrees.nodes(CoreClass.WARRIOR).size)
        val radius = selected.skills.single { it.icon == "whirl" }.metrics.single { it.label == "範囲" }
        check(abs(radius.current - 4.2) < .0001 && abs(radius.after - 5.46) < .0001)
        check(CoreAccountCodec.encode(account) == encoded)
        check(!model.select("unknown:500"))
        check(model.toggle() && model.snapshot().changed && model.snapshot().spent == 2)
        check(model.select("warrior:0") && model.toggle())
        check(model.trial.nodes == 0) { "A trial parent refund did not remove its orphaned descendant" }
        check(CoreAccountCodec.encode(account) == encoded) { "Preview changed the source account" }
        model.reset()
        check(model.trial == account.journey.build)
        check(model.select("warrior:2") && !model.toggle()) { "Prerequisite was bypassed" }
        for (i in listOf(9, 11, 6, 7)) { check(model.select("warrior:$i")); check(model.toggle()) }
        check(model.snapshot().spent == model.snapshot().budget)
        check(model.select("warrior:1") && !model.toggle()) { "Point budget was bypassed" }
        val legacy = CoreTreePreview(account.copy(journey = CoreJourney(build = CoreClassBuild(nodes = 1))))
        for (i in listOf(1, 10, 2, 3, 4, 13)) { check(legacy.select("warrior:$i")); check(legacy.toggle()) }
        check(legacy.select("warrior:5") && !legacy.toggle()) { "Exclusive keystones were bypassed" }
        val merge = CoreTreePreview(account.copy(journey = CoreJourney(build = CoreClassBuild(nodes = (1 shl 0) or (1 shl 1) or (1 shl 9) or (1 shl 10) or (1 shl 11) or (1 shl 2)))))
        check(merge.select("warrior:1") && merge.toggle())
        check(merge.trial.has(2) && merge.trial.has(11)) { "Refund removed a still-connected merge" }
        val flow = CoreTreePreviewFlow(CoreTreePreview(account), spriteJson)
        flow.action("node:warrior:9")
        val scene = flow.scene(ForgeLightPhase.IDLE)
        check(scene.nodes.size < 180)
        for (n in scene.nodes) check(n.box.x >= 0 && n.box.y >= 0 && n.box.x + n.box.w <= scene.width + .01 && n.box.y + n.box.h <= scene.height + .01)
        for (n in scene.nodes.filter { it.action?.startsWith("node:") == true }) {
            check(scene.hit(n.box.x + n.box.w / 2, n.box.y + n.box.h / 2)?.action == n.action)
        }
        check(!flow.action("save") && !flow.action("confirm"))
        nativeSession(account, spriteJson)
        check(CoreAccountCodec.encode(account) == encoded)
        println("TREE_PREVIEW_CHECKS_PASS catalog comparison; immutable account; points; prerequisites; exclusive keystones; orphan refund; alternate-path merge; native selection/trial/close; restored camera/mode; zero UI entities")
    }

    private fun nativeSession(account: CoreAccount, spriteJson: String) {
        MinecraftServer.init(Auth.Offline())
        try {
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.viewDistance(2)
        for (x in -3..3) for (z in -3..3) instance.loadChunk(x, z).get(10, TimeUnit.SECONDS)
        val packets = CopyOnWriteArrayList<SendablePacket>()
        val connection = object : PlayerConnection() {
            override fun sendPacket(packet: SendablePacket) { packets += SendablePacket.extractServerPacket(ConnectionState.PLAY, packet) }
            override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
        }
        connection.setClientState(ConnectionState.PLAY); connection.setServerState(ConnectionState.PLAY)
        lateinit var sessions: UiSessions
        val player = UiInputPlayer(connection, GameProfile(UUID.randomUUID(), "TreePreview")) { p, packet -> sessions.consumeImmediateUiPacket(p, packet) }
        connection.player = player
        player.setInstance(instance, Pos(0.0, 1.0, 0.0)).get(10, TimeUnit.SECONDS)
        val events = MinecraftServer.getGlobalEventHandler()
        sessions = UiSessions(events, null, { true })
        val flow = CoreTreePreviewFlow(CoreTreePreview(account), spriteJson)
        try {
            sessions.open(player, flow)
            check(sessions.sessionCount == 1 && sessions.entityCount > 0)
            val probe = packets.filterIsInstance<PlayerPositionAndLookPacket>().last()
            check(sessions.consumeImmediateUiPacket(player, ClientTeleportConfirmPacket(probe.teleportId())))
            fun rotation(x: Double, y: Double) {
                player.addPacketToQueue(ClientPlayerPositionAndRotationPacket(Pos(0.0, 1.0, 0.0, ((x - 400) / 8).toFloat(), ((y - 240) / 8).toFloat()), false, false))
            }
            rotation(400.0, 240.0)
            fun click(action: String) {
                val b = flow.scene(ForgeLightPhase.IDLE).nodes.single { it.action == action }.box
                rotation(b.x + b.w / 2, b.y + b.h / 2)
                val click = ClientSpectatorActionPacket(null)
                sessions.consumeImmediateUiPacket(player, click)
                events.call(PlayerPacketEvent(player, click))
                Thread.sleep(85)
            }
            click("node:warrior:9")
            check(flow.snapshot().selected == "warrior:9")
            click("trial:toggle")
            check(flow.snapshot().spent == 2)
            click("close")
            check(sessions.sessionCount == 0 && sessions.entityCount == 0)
            check(packets.filterIsInstance<CameraPacket>().last().cameraId() == player.entityId)
            check(packets.filterIsInstance<ChangeGameStatePacket>().last().value() == player.gameMode.ordinal.toFloat())
            check(instance.entities.all { it === player })
        } finally { sessions.close(); player.remove() }
        } finally { MinecraftServer.stopCleanly() }
    }
}
