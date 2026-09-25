package dev.projects.webui

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.event.player.PlayerPacketEvent
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.network.packet.client.play.ClientTeleportConfirmPacket
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket
import net.minestom.server.network.packet.client.play.ClientSpectatorActionPacket
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.packet.server.play.CameraPacket
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** Packet-level native draw check. Creator still performs the final Minecraft visual/manual smoke. */
fun main(args:Array<String>) {
    require(args.size==3)
    System.setProperty("minestom.tps","60")
    MinecraftServer.init(Auth.Offline())
    val instance=MinecraftServer.getInstanceManager().createInstanceContainer()
    instance.viewDistance(2)
    for(x in -3..3)for(z in -3..3)instance.loadChunk(x,z).get(10,TimeUnit.SECONDS)
    val packets=CopyOnWriteArrayList<SendablePacket>()
    fun player(name:String): Player {
        val connection=object:PlayerConnection() {
            override fun sendPacket(packet:SendablePacket) { packets+=SendablePacket.extractServerPacket(ConnectionState.PLAY,packet) }
            override fun getRemoteAddress():SocketAddress=InetSocketAddress("127.0.0.1",0)
        }
        connection.setClientState(ConnectionState.PLAY);connection.setServerState(ConnectionState.PLAY)
        return Player(connection,GameProfile(UUID.randomUUID(),name)).also {
            connection.player=it;it.setInstance(instance,Pos(0.0,1.0,0.0)).get(10,TimeUnit.SECONDS)
        }
    }
    val owner=player("PolishOwner")
    val other=player("PolishOther")
    val scene=Polish05Scene(Path.of(args[1]),Path.of(args[2]))
    val events=MinecraftServer.getGlobalEventHandler()
    val sessions=UiSessions(events,Path.of(args[0]),{true},scene)
    try {
        sessions.open(owner)
        check(sessions.sessionCount==1)
        check(packets.filterIsInstance<CameraPacket>().last().cameraId()!=owner.entityId)
        check(packets.filterIsInstance<ChangeGameStatePacket>().last().value()==3f)
        val display=instance.entities.mapNotNull { it.entityMeta as? TextDisplayMeta }
        check(display.any { (it.text as? net.kyori.adventure.text.TextComponent)?.font()?.asString()=="projects_ui_polish05:plates" }) {
            "Approved chrome/environment bitmap font was not sent"
        }
        check(display.any { (it.text as? net.kyori.adventure.text.TextComponent)?.font()?.asString()=="projects_ui_polish05:sprites" }) {
            "Original weapon/icon bitmap font was not sent"
        }
        check(sessions.entityCount in 80..400)
        check(instance.entities.filter { it!==owner && it!==other }.all { other !in it.viewers })
        val sync=packets.filterIsInstance<PlayerPositionAndLookPacket>().last()
        events.call(PlayerPacketEvent(owner,ClientTeleportConfirmPacket(sync.teleportId())))
        fun rotate(x:Double,y:Double) {
            val p=Pos(0.0,1.0,0.0,((x-400)/8).toFloat(),((y-240)/8).toFloat())
            events.call(PlayerPacketEvent(owner,ClientPlayerPositionAndRotationPacket(p,false,false)))
        }
        rotate(400.0,240.0)
        val action=scene.forge(Polish05Flow(scene).model).nodes.single { it.action=="enhance" }.box
        rotate(action.x+action.w/2,action.y+action.h/2)
        events.call(PlayerPacketEvent(owner,ClientSpectatorActionPacket(null)))
        check(instance.entities.mapNotNull { (it.entityMeta as? TextDisplayMeta)?.text as? net.kyori.adventure.text.TextComponent }
            .any { it.content().contains("この装備を強化しますか") }) { "Click did not open native confirmation" }
        Thread.sleep(80)
        val confirm=scene.forge(Polish05Flow(scene).model,modal=true).nodes.single { it.action=="confirm" }.box
        rotate(confirm.x+confirm.w/2,confirm.y+confirm.h/2)
        events.call(PlayerPacketEvent(owner,ClientSpectatorActionPacket(null)))
        check(packets.any { it.javaClass.simpleName.contains("SoundEffectPacket") }) { "Owner did not receive a Polish05 sound" }
        Thread.sleep(750)
        events.call(InstanceTickEvent(instance,0,50))
        check(instance.entities.mapNotNull { (it.entityMeta as? TextDisplayMeta)?.text as? net.kyori.adventure.text.TextComponent }
            .any { it.content().contains("熾火の大剣  +7") }) { "Committed enhancement did not update native text" }
        sessions.close(owner)
        check(sessions.entityCount==0 && sessions.sessionCount==0)
        check(packets.filterIsInstance<CameraPacket>().last().cameraId()==owner.entityId)
        println("POLISH05_NATIVE_SMOKE_PASS glyphs=${display.size}; packet click/confirm/commit/sound; private entities; camera/mode restored; zero leaked")
    } finally {
        sessions.close();owner.remove();other.remove();MinecraftServer.stopCleanly()
    }
}
