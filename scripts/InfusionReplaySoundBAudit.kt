package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.NetworkBuffer
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.packet.server.play.SoundEffectPacket
import net.minestom.server.network.packet.server.play.StopSoundPacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import net.kyori.adventure.sound.Sound
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Real game tick + real Player/Adventure sound packet audit, isolated fake connection and repository. */
fun main(args:Array<String>) {
    for(key in listOf("testReplay","testReplaySoundB","energyBudgeted","chargedCore","boldCore","coreArcLook","roleSpriteLook","idleCoreRotation"))System.setProperty("projects.infusion.$key","true")
    System.setProperty("projects.infusion.powerPerSecond","18")
    MinecraftServer.init(Auth.Offline());val manager=MinecraftServer.getInstanceManager();val map=manager.createInstanceContainer()
    var auditTick=0;val sounds=mutableListOf<Pair<Int,SoundEffectPacket>>();val stops=mutableListOf<StopSoundPacket>()
    val connection=object:PlayerConnection() {
        override fun sendPacket(packet:SendablePacket) {
            when(packet) { is SoundEffectPacket->sounds+=auditTick to packet;is StopSoundPacket->stops+=packet;else->{} }
        }
        override fun getRemoteAddress():SocketAddress=InetSocketAddress("127.0.0.1",0)
    }
    connection.setClientState(ConnectionState.PLAY);connection.setServerState(ConnectionState.PLAY)
    val player=Player(connection,GameProfile(UUID.fromString("00000000-0000-0000-0000-000000005516"),"SoundBAudit"));connection.player=player
    val fresh=WorldInfusionState.fresh(player.uuid)
    val initial=fresh.copy(account=fresh.account.copy(balances=emptyMap()),matrix=InfusionCell(0,41,0),
        pedestals=listOf(InfusionCell(3,41,0),InfusionCell(0,41,3),InfusionCell(-3,41,0),InfusionCell(0,41,-3)).map { InfusionPedestal(it) },
        jars=fresh.jars.mapIndexed { i,j->j.copy(amount=0,cell=listOf(InfusionCell(-4,41,2),InfusionCell(4,41,2),InfusionCell(0,41,5))[i]) })
    val repo=WorldInfusionRepository(Path.of(args.single()));val saved=repo.save(0,initial);val encoded=WorldInfusionCodec.encode(saved)
    val file=Path.of(args.single()).resolve("${player.uuid}.infusion");val bytes=Files.readAllBytes(file)
    val game=WorldInfusionServer.WorldInfusionGame(player,map,repo,saved);WorldInfusionServer.track(game)
    fun unchanged() { check(Files.readAllBytes(file).contentEquals(bytes));check(WorldInfusionCodec.encode(repo.load(player.uuid)!!)==encoded) }
    fun key(p:SoundEffectPacket)=p.soundEvent().name().substringAfter(':')
    fun checkStops(expected:Set<String>) {
        check(stops.map { it.sound()?.substringAfter(':') }.toSet()==expected)
        check(stops.size==expected.size && stops.all { it.flags().toInt()==3 && it.source()==Sound.Source.BLOCK })
    }
    try {
        val spawn=player.setInstance(map,Pos(6.5,41.0,2.5));val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        while(!spawn.isDone && System.nanoTime()<deadline) { MinecraftServer.getSchedulerManager().processTick();Thread.sleep(2) }
        spawn.get(1,TimeUnit.SECONDS);game.packed=true;game.rebuild()
        check(game.beginReplay());check(!game.beginReplay());val visualAccount=CoreAccountCodec.encode(game.state.account)
        val phases=mutableSetOf<InfusionPhase>();var count=0;var completeAt:Int?=null
        while(game.isReplaying && count<1200) {
            if(count<20)repeat(30) { game.requestReplay(false) }
            auditTick=++count;game.tick();phases+=game.state.phase
            if(game.state.phase==InfusionPhase.COMPLETE && completeAt==null)completeAt=count
            if(game.isReplaying)check(CoreAccountCodec.encode(game.state.account)==visualAccount)
            unchanged()
        }
        check(!game.isReplaying && count<1200 && game.state===saved)
        check(phases.containsAll(listOf(InfusionPhase.ESSENTIA,InfusionPhase.INGREDIENTS,InfusionPhase.COMPLETE)))
        check(sounds.size==3) { "Wrong cue count: ${sounds.map { key(it.second) }}" }
        val start=sounds.single { key(it.second)=="block.amethyst_block.chime" }
        val arrival=sounds.single { key(it.second)=="block.enchantment_table.use" }
        val finish=sounds.single { key(it.second)=="block.amethyst_block.resonate" }
        check(start.second.volume()==.6f && start.second.pitch()==.8f)
        check(arrival.second.volume()==.05f && arrival.second.pitch()==1f)
        check(finish.second.volume()==.25f && finish.second.pitch()==1f)
        check(finish.first==completeAt && finish.first-arrival.first==20)
        for((_,packet) in listOf(arrival,finish)) {
            check(packet.source()==Sound.Source.BLOCK)
            check(abs(packet.origin().x()-.5)<1e-5 && abs(packet.origin().y()-42.29)<1e-5 && abs(packet.origin().z()-.5)<1e-5)
            check(packet.x()==0 && packet.y()==42 && packet.z()==0) // Compatibility accessors use block coordinates.
            val wire=NetworkBuffer.makeArray(SoundEffectPacket.SERIALIZER,packet)
            val decoded=NetworkBuffer.wrap(wire,0,wire.size).read(SoundEffectPacket.SERIALIZER)
            check(decoded.soundEvent().name()==packet.soundEvent().name() && decoded.volume()==packet.volume() && decoded.pitch()==packet.pitch())
            check(decoded.origin()==net.minestom.server.coordinate.Vec(.5,42.25,.5)) // Wire precision is 1/8 block.
        }
        checkStops(setOf("block.amethyst_block.chime","block.enchantment_table.use","block.amethyst_block.resonate"))
        println("REPLAY_B_PACKET_TIMING_PASS arrivalTick=${arrival.first} completedTick=${finish.first} delta20 startUnchanged .05_1.0 .25_1.0 BLOCK receiverPosition noDuplicateCompletion")
        for(after in listOf(1,35,120,145,168)) {
            sounds.clear();stops.clear();auditTick=0;check(game.beginReplay())
            repeat(after) { auditTick++;game.tick() }
            val played=sounds.map { key(it.second) }.toSet();check(played.size==sounds.size)
            game.requestReplay(true);auditTick++;game.tick()
            check(!game.isReplaying && game.state===saved && game.smokeEntityIds().isEmpty() && game.coreArcEntityIds().isEmpty())
            check(WorldInfusionSmokeBudget.inUse==0);checkStops(played);unchanged()
            val emitted=sounds.size;repeat(6) { auditTick++;game.tick() };check(sounds.size==emitted)
        }
        sounds.clear();stops.clear();check(game.beginReplay());repeat(145) { auditTick++;game.tick() }
        val played=sounds.map { key(it.second) }.toSet();check("block.enchantment_table.use" in played)
        player.remove();auditTick++;game.tick()
        check(game.isClosed && !game.isReplaying && game.state===saved && WorldInfusionSmokeBudget.inUse==0);checkStops(played);unchanged()
        println("REPLAY_B_AUDIT_PASS actualSoundAndStopPackets standardEvents countsOnce spam600 noOutputRewardSkill emptyMaterials emptyJars cancel1_35_120_145_168 lateCueSuppressed disconnectStopsOwnedNamedBlockCues visualCleanup repositoryBytesUnchanged")
    } finally {
        game.close();if(!player.isRemoved)player.remove();if(manager.getInstance(map.uuid)===map)manager.unregisterInstance(map)
    }
}
