package dev.projects.webui

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerPacketEvent
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.entity.PlayerHand
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.client.play.ClientInputPacket
import net.minestom.server.network.packet.client.play.ClientTeleportConfirmPacket
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket
import net.minestom.server.network.packet.client.play.ClientUseItemPacket
import net.minestom.server.network.packet.client.play.ClientHeldItemChangePacket
import net.minestom.server.network.packet.client.play.ClientSpectatorActionPacket
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket
import net.minestom.server.network.packet.server.play.HeldItemChangePacket
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.packet.server.play.CameraPacket
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    MinecraftServer.init(Auth.Offline())
    val instance=MinecraftServer.getInstanceManager().createInstanceContainer()
    instance.viewDistance(2)
    for(x in -3..3) for(z in -3..3) instance.loadChunk(x,z).get(10,TimeUnit.SECONDS)
    val packets=CopyOnWriteArrayList<SendablePacket>()
    fun player(name: String): Player {
        val connection=object: PlayerConnection() {
            override fun sendPacket(packet: SendablePacket) { packets+=SendablePacket.extractServerPacket(ConnectionState.PLAY,packet) }
            override fun getRemoteAddress(): SocketAddress=InetSocketAddress("127.0.0.1",0)
        }
        connection.setClientState(ConnectionState.PLAY);connection.setServerState(ConnectionState.PLAY)
        return Player(connection,GameProfile(UUID.randomUUID(),name)).also {
            connection.player=it;it.setInstance(instance,Pos(0.0,1.0,0.0)).get(10,TimeUnit.SECONDS)
        }
    }
    val first=player("UiFirst");val second=player("UiSecond")
    val events=MinecraftServer.getGlobalEventHandler()
    val sessions=UiSessions(events,Path.of(args.single()))
    try {
        repeat(5) {
            sessions.open(first);sessions.open(first)
            check(sessions.sessionCount==1)
            check(packets.filterIsInstance<CameraPacket>().last().cameraId()!=first.entityId)
            check(packets.filterIsInstance<ChangeGameStatePacket>().last().value()==3f)
            check(first.gameMode!=net.minestom.server.entity.GameMode.SPECTATOR)
            val sync=packets.filterIsInstance<PlayerPositionAndLookPacket>().last()
            check(sync.teleportId()<0)
            val ack=PlayerPacketEvent(first,ClientTeleportConfirmPacket(sync.teleportId()))
            events.call(ack);check(ack.isCancelled)
            fun rotate(yaw: Float,pitch: Float) {
                val e=PlayerPacketEvent(first,ClientPlayerPositionAndRotationPacket(Pos(0.0,1.0,0.0,yaw,pitch),false,false))
                events.call(e);check(e.isCancelled)
            }
            rotate(0f,0f)
            val idleMetadata=packets.filterIsInstance<EntityMetaDataPacket>().size
            repeat(20) { events.call(InstanceTickEvent(instance,0,50)) }
            check(packets.filterIsInstance<EntityMetaDataPacket>().size==idleMetadata) { "Idle UI sends metadata" }
            val demo=ForgeDemo()
            val button=UiDocument.parse(java.nio.file.Files.readString(Path.of(args.single())))
                .layout(demo.values(),demo.flags()).nodes.single { it.id=="forge-button" }.box
            rotate(((button.x+button.w/2-400)/8).toFloat(),((button.y+button.h/2-240)/8).toFloat())
            // No tick between movement and click: zero-delay input must update immediately.
            val moveUpdates=packets.filterIsInstance<EntityMetaDataPacket>().size-idleMetadata
            check(moveUpdates in 3..5) { "Pointer movement metadata count=$moveUpdates (expected 3..5)" }
            events.call(PlayerPacketEvent(first,ClientSpectatorActionPacket(null)))
            check(instance.entities.mapNotNull { (it.entityMeta as? TextDisplayMeta)?.text as? net.kyori.adventure.text.TextComponent }
                .any { it.content()=="旅人の大剣 +4" }) { "Packet-driven forge did not execute" }
            val scroll=PlayerPacketEvent(first,ClientHeldItemChangePacket(1))
            events.call(scroll);check(scroll.isCancelled);check(first.heldSlot.toInt()==0)
            check(sessions.entityCount in 20..180)
            check(instance.entities.filter { it!==first && it!==second }.all { second !in it.viewers })
            val exit=PlayerPacketEvent(first,ClientInputPacket(false,false,false,false,false,true,false))
            val outstanding=packets.filterIsInstance<PlayerPositionAndLookPacket>().last()
            events.call(exit)
            check(exit.isCancelled);check(sessions.sessionCount==0);check(sessions.entityCount==0)
            check(packets.filterIsInstance<CameraPacket>().last().cameraId()==first.entityId)
            check(packets.filterIsInstance<HeldItemChangePacket>().last().slot().toInt()==0)
            check(packets.filterIsInstance<ChangeGameStatePacket>().last().value()==first.gameMode.ordinal.toFloat())
            val lateAck=PlayerPacketEvent(first,ClientTeleportConfirmPacket(outstanding.teleportId()))
            events.call(lateAck);check(lateAck.isCancelled)
            val lateRotation=PlayerPacketEvent(first,ClientPlayerPositionAndRotationPacket(Pos(20.0,30.0,40.0,75f,60f),false,false))
            events.call(lateRotation);check(!lateRotation.isCancelled) { "Closed UI swallowed ordinary movement" }
            // Minestom itself gates movement until the authoritative restore teleport is acknowledged.
            check(first.lastSentTeleportId!=first.lastReceivedTeleportId)
        }
        check(instance.entities.all { it===first || it===second })
        println("UI_SMOKE_PASS 5 cycles; immediate rotation/click; idle metadata=0; move metadata<=5; private entities; client mode/camera/slot restored; late ACK consumed, ordinary movement untouched; zero leaks")
    } finally {
        sessions.close();first.remove();second.remove();MinecraftServer.stopCleanly()
    }
}
