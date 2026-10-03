package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

fun main(args:Array<String>) {
    System.setProperty("projects.infusion.testReplay","true")
    System.setProperty("projects.infusion.energyBudgeted","true")
    System.setProperty("projects.infusion.chargedCore","true")
    System.setProperty("projects.infusion.boldCore","true")
    System.setProperty("projects.infusion.coreArcLook","true")
    System.setProperty("projects.infusion.roleSpriteLook","true")
    System.setProperty("projects.infusion.idleCoreRotation","true")
    System.setProperty("projects.infusion.powerPerSecond","18")
    MinecraftServer.init(Auth.Offline())
    val manager=MinecraftServer.getInstanceManager();val map=manager.createInstanceContainer()
    val connection=object:PlayerConnection() {
        override fun sendPacket(packet:SendablePacket) {}
        override fun getRemoteAddress():SocketAddress=InetSocketAddress("127.0.0.1",0)
    }
    connection.setClientState(ConnectionState.PLAY);connection.setServerState(ConnectionState.PLAY)
    val player=Player(connection,GameProfile(UUID.fromString("00000000-0000-0000-0000-000000005515"),"ReplayAudit"));connection.player=player
    val fresh=WorldInfusionState.fresh(player.uuid)
    val initial=fresh.copy(account=fresh.account.copy(balances=emptyMap()),matrix=InfusionCell(0,41,0),
        pedestals=listOf(InfusionCell(3,41,0),InfusionCell(0,41,3),InfusionCell(-3,41,0),InfusionCell(0,41,-3)).map { InfusionPedestal(it) },
        jars=fresh.jars.mapIndexed { i,j->j.copy(amount=0,cell=listOf(InfusionCell(-4,41,2),InfusionCell(4,41,2),InfusionCell(0,41,5))[i]) })
    val repo=WorldInfusionRepository(Path.of(args.single()));val saved=repo.save(0,initial)
    val encoded=WorldInfusionCodec.encode(saved)
    val file=Path.of(args.single()).resolve("${player.uuid}.infusion");val bytes=Files.readAllBytes(file)
    val game=WorldInfusionServer.WorldInfusionGame(player,map,repo,saved);WorldInfusionServer.track(game)
    fun unchanged() { check(Files.readAllBytes(file).contentEquals(bytes));check(WorldInfusionCodec.encode(repo.load(player.uuid)!!)==encoded) }
    try {
        val spawn=player.setInstance(map,Pos(6.5,41.0,2.5));val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        while(!spawn.isDone && System.nanoTime()<deadline) { MinecraftServer.getSchedulerManager().processTick();Thread.sleep(2) }
        spawn.get(1,TimeUnit.SECONDS);game.packed=true;game.rebuild()
        check(game.beginReplay());check(!game.beginReplay())
        val visualAccount=CoreAccountCodec.encode(game.state.account)
        var phases=mutableSetOf<InfusionPhase>();var count=0
        while(game.isReplaying && count<1200) {
            if(count<20)repeat(30) { game.requestReplay(false) }
            game.tick();phases+=game.state.phase
            if(game.isReplaying)check(CoreAccountCodec.encode(game.state.account)==visualAccount) { "Replay granted item/account progress" }
            unchanged();count++
        }
        check(!game.isReplaying && count<1200 && phases.containsAll(listOf(InfusionPhase.ESSENTIA,InfusionPhase.INGREDIENTS,InfusionPhase.COMPLETE)))
        check(game.state===saved && WorldInfusionCodec.encode(game.state)==encoded);unchanged()
        for(after in listOf(1,35,120)) {
            check(game.beginReplay());repeat(after) { game.tick() }
            game.requestReplay(true);game.tick()
            check(!game.isReplaying && game.state===saved)
            check(game.smokeEntityIds().isEmpty() && game.coreArcEntityIds().isEmpty())
            check(WorldInfusionSmokeBudget.inUse==0);unchanged()
        }
        check(game.beginReplay());repeat(30) { game.tick() };game.close()
        check(!game.isReplaying && game.state===saved && WorldInfusionSmokeBudget.inUse==0);unchanged()
        println("TEST_REPLAY_AUDIT_PASS emptyMaterials emptyJars noOutputNoRewardsNoSkill repositoryBytesUnchanged allPhases spam600NoOverlap cancelAt1_35_120 closeCleanup normalRulesUnchanged durationTicks=$count")
    } finally {
        game.close();if(!player.isRemoved)player.remove()
        if(manager.getInstance(map.uuid)===map)manager.unregisterInstance(map)
    }
}
