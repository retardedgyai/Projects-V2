package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.ServerFlag
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.component.DataComponents
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

fun main(args:Array<String>) {
    MinecraftServer.init(Auth.Offline())
    val manager=MinecraftServer.getInstanceManager()
    val map=manager.createInstanceContainer()
    val connection=object:PlayerConnection() {
        override fun sendPacket(packet:SendablePacket) {}
        override fun getRemoteAddress():SocketAddress=InetSocketAddress("127.0.0.1",0)
    }
    connection.setClientState(ConnectionState.PLAY)
    connection.setServerState(ConnectionState.PLAY)
    val player=Player(connection,GameProfile(UUID.randomUUID(),"SpawnRegression"))
    connection.player=player
    val repo=WorldInfusionRepository(Path.of(args[0]))
    val initial=repo.save(0,WorldInfusionState.fresh(player.uuid))
    val game=WorldInfusionServer.WorldInfusionGame(player,map,repo,initial)
    WorldInfusionServer.track(game)
    try {
        // Configuration precedes player.setInstance. World ticks must not retire it.
        repeat(100) { game.tick() }
        check(manager.getInstance(map.uuid)===map) { "Pending login world was unregistered before spawn" }
        check(WorldInfusionCodec.encode(initial)==WorldInfusionCodec.encode(repo.load(player.uuid)!!))
        val spawn=player.setInstance(map,Pos(.5,41.0,-7.5))
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        while(!spawn.isDone && System.nanoTime()<deadline) {
            MinecraftServer.getSchedulerManager().processTick()
            Thread.sleep(2)
        }
        spawn.get(1,TimeUnit.SECONDS)
        // Include native 26.2's -entityYaw and final ItemDisplay Y=180 rotation.
        val center=InfusionCell(0,41,0)
        game.state=WorldInfusionRules.placeMatrix(game.state,center)
        game.packed=true
        game.rebuild()
        repeat(10) { MinecraftServer.getSchedulerManager().processTick(); Thread.sleep(2) }
        val supports=map.entities.filter {
            (it.entityMeta as? ItemDisplayMeta)?.itemStack?.get(DataComponents.ITEM_MODEL)=="projects:infusion-v4/support"
        }
        check(supports.size==4)
        for(support in supports) {
            val meta=support.entityMeta as ItemDisplayMeta
            check(meta.rightRotation.contentEquals(floatArrayOf(0f,1f,0f,0f)))
            val compensation=2*kotlin.math.atan2(meta.rightRotation[1].toDouble(),meta.rightRotation[3].toDouble())
            val theta=Math.toRadians(-support.position.yaw().toDouble())+compensation+Math.PI
            val leanX=-kotlin.math.cos(theta)
            val leanZ=kotlin.math.sin(theta)
            val towardX=center.x+.5-support.position.x()
            val towardZ=center.z+.5-support.position.z()
            check(leanX*towardX+leanZ*towardZ>1.4) { "Support leans away from central pedestal" }
        }
        val structural=map.entities.filter {
            val id=(it.entityMeta as? ItemDisplayMeta)?.itemStack?.get(DataComponents.ITEM_MODEL)
            id=="projects:infusion-v6/center" || id=="projects:infusion-v7/core_ritual"
        }
        check(structural.size==2)
        check(structural.all { (it.entityMeta as ItemDisplayMeta).rightRotation.contentEquals(floatArrayOf(0f,1f,0f,0f)) })
        repeat(1200) { game.tick() }
        check(player.instance===map && manager.getInstance(map.uuid)===map)
        check(WorldInfusionCodec.encode(initial)==WorldInfusionCodec.encode(repo.load(player.uuid)!!))
        player.remove()
        game.tick()
        check(manager.getInstance(map.uuid)==null) { "Disconnected world was not retired" }
        check(WorldInfusionSmokeBudget.inUse==0)
        check(ServerFlag.PLAYER_PACKET_QUEUE_SIZE==1000 && ServerFlag.PLAYER_PACKET_PER_TICK==50)
        println("SPAWN_REGRESSION_PASS pending100 online1200 supportsInward4 nativeItemY180Compensated centerCoreAuthoredAxes disconnectCleanup saveUnchanged packetQueue1000 packetPerTick50")
    } finally {
        game.close()
        if(!player.isRemoved)player.remove()
        if(manager.getInstance(map.uuid)===map)manager.unregisterInstance(map)
    }
}
