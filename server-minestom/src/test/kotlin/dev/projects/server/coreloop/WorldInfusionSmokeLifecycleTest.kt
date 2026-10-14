package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockFace
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.packet.server.play.*
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.*

class WorldInfusionSmokeLifecycleTest {
    private class Fixture : AutoCloseable {
        init { MinecraftServer.init(Auth.Offline()) }
        val map=MinecraftServer.getInstanceManager().createInstanceContainer().apply {
            viewDistance(2);setGenerator { it.modifier().fillHeight(40,41,Block.STONE_BRICKS) }
            for(x in -1..0)for(z in -1..0)loadChunk(x,z).get(10,TimeUnit.SECONDS)
        }
        val packets=mutableListOf<SendablePacket>()
        val connection=object:PlayerConnection() {
            override fun sendPacket(packet:SendablePacket) { packets+=packet }
            override fun getRemoteAddress():SocketAddress=InetSocketAddress("127.0.0.1",0)
        }.apply { setClientState(ConnectionState.PLAY);setServerState(ConnectionState.PLAY) }
        val player=Player(connection,GameProfile(UUID.randomUUID(),"SmokeTest")).also {
            connection.player=it;await(it.setInstance(map,Pos(.5,41.0,-2.5)))
        }
        val repo=WorldInfusionRepository(Files.createTempDirectory("infusion-smoke-test-"))
        val game:WorldInfusionServer.WorldInfusionGame
        init {
            var s=WorldInfusionState.fresh(player.uuid)
            s=WorldInfusionRules.placeMatrix(s,InfusionCell(0,41,0))
            val cells=listOf(InfusionCell(3,41,0),InfusionCell(0,41,3),InfusionCell(-3,41,0),InfusionCell(0,41,-3))
            for((i,r) in listOf(CoreResource.INGOT,CoreResource.CLOTH,CoreResource.INGOT,CoreResource.STONE_BLOCK).withIndex()) {
                s=WorldInfusionRules.placePedestal(s,cells[i]);s=WorldInfusionRules.material(s,cells[i],r)
            }
            val jarCells=listOf(InfusionCell(-4,41,-3),InfusionCell(4,41,-3),InfusionCell(0,41,5))
            for((i,j) in s.jars.withIndex())s=WorldInfusionRules.placeJar(s,j.id,jarCells[i])
            s=WorldInfusionRules.start(WorldInfusionRules.placeGear(s,s.gear.identity.id))
            game=WorldInfusionServer.WorldInfusionGame(player,map,repo,repo.save(0,s))
            game.packed=true;game.rebuild();WorldInfusionServer.track(game)
        }
        fun await(f:CompletableFuture<*>) {
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
            while(!f.isDone && System.nanoTime()<until) { MinecraftServer.getSchedulerManager().processTick();Thread.sleep(2) }
            f.get(1,TimeUnit.SECONDS)
        }
        override fun close() {
            game.close();player.remove()
            if(MinecraftServer.getInstanceManager().getInstance(map.uuid)===map)MinecraftServer.getInstanceManager().unregisterInstance(map)
            assertEquals(0,WorldInfusionSmokeBudget.inUse)
        }
    }
    @Test fun `global cosmetic budget is bounded and lease release is idempotent`() {
        assertEquals(0,WorldInfusionSmokeBudget.inUse)
        val leases=List(WorldInfusionSmokeBudget.MAX_TOTAL) { assertNotNull(WorldInfusionSmokeBudget.acquire()) }
        assertNull(WorldInfusionSmokeBudget.acquire())
        assertEquals(192,WorldInfusionSmokeBudget.inUse)
        leases.forEach { it.release();it.release() }
        assertEquals(0,WorldInfusionSmokeBudget.inUse)
    }
    @Test fun `real billboard load is bounded and finite with no leftover entities`() {
        Fixture().use { f->
            val ids=mutableSetOf<Int>();var peakScene=0
            repeat(260) {
                f.game.tick();ids+=f.game.smokeEntityIds();peakScene=maxOf(peakScene,f.map.entities.size)
                if(it==39)assertTrue(f.map.entities.filter { e->e.entityId in f.game.smokeEntityIds() }.any { e->e.position.y()>43 },
                    "Real cloud entities must move above the Jar toward the focus, not just change metadata")
            }
            val m=f.game.smokeStats
            assertEquals(126L,m.born);assertEquals(m.born,m.removed)
            assertTrue(m.peak<=WorldInfusionSmoke.MAX_SAMPLES)
            assertTrue(f.game.smokeEntityIds().isEmpty());assertEquals(0,WorldInfusionSmokeBudget.inUse)
            assertEquals(InfusionPhase.COMPLETE,f.game.state.phase)
            // Resolve cached packets as well. Fake connection captures API deliveries, not TCP bytes/FPS.
            val delivered=f.packets.map { SendablePacket.extractServerPacket(ConnectionState.PLAY,it) }
            val spawns=delivered.filterIsInstance<SpawnEntityPacket>().count { it.entityId() in ids }
            val metadata=delivered.filterIsInstance<EntityMetaDataPacket>().count { it.entityId() in ids }
            val moves=delivered.filterIsInstance<EntityPositionSyncPacket>().count { it.entityId() in ids } +
                delivered.filterIsInstance<EntityTeleportPacket>().count { it.entityId() in ids }
            val removes=delivered.filterIsInstance<DestroyEntitiesPacket>().sumOf { it.entityIds().count(ids::contains) }
            val dir=Path.of(".tools/world-infusion-evidence");Files.createDirectories(dir)
            Files.writeString(dir.resolve("smoke-load-verification.json"),"""
                {"clientMeasured":false,"ritualJarCount":3,"ritualUnits":7,"peakSmokeEntities":${m.peak},"peakSceneEntities":$peakScene,
                "born":${m.born},"removed":${m.removed},"positionCalls":${m.moves},"metadataEdits":${m.metadata},
                "fakeConnectionPackets":{"spawn":$spawns,"metadata":$metadata,"position":$moves,"destroyEntityIds":$removes},
                "positionPacketNote":"Minestom queues viewer position packets separately; fake connection is not a network flush measurement",
                "globalCosmeticCap":${WorldInfusionSmokeBudget.MAX_TOTAL},"tuftTravelTicks":42,"transferRetentionTicks":54}
            """.trimIndent())
        }
    }
    @Test fun `cancel and shortage stop births and drain existing billboards without duplication`() {
        for(reason in listOf("cancel","shortage"))Fixture().use { f->
            repeat(39) { f.game.tick() };assertTrue(f.game.smokeEntityIds().isNotEmpty())
            f.player.isSneaking=true
            if(reason=="cancel") {
                f.player.setItemInMainHand(ItemStack.of(Material.BLAZE_ROD).withTag(Tag.String("projects_world_infusion_item"),"cast"))
                f.await(f.player.teleport(Pos(.5,43.0,-2.5)))
                f.game.interact(Pos(0.0,44.0,0.0),BlockFace.TOP)
                assertEquals(InfusionPhase.READY,f.game.state.phase)
            } else {
                f.player.setItemInMainHand(ItemStack.AIR)
                f.await(f.player.teleport(Pos(-3.5,41.0,-4.5)))
                f.game.interact(Pos(-4.0,41.0,-3.0),BlockFace.TOP)
                repeat(15) { f.game.tick() };assertTrue(f.game.state.paused)
            }
            val born=f.game.smokeStats.born;val ledger=WorldInfusionCodec.encode(f.game.state)
            repeat(60) { f.game.tick() }
            assertEquals(born,f.game.smokeStats.born,reason)
            assertTrue(f.game.smokeEntityIds().isEmpty(),reason);assertEquals(0,WorldInfusionSmokeBudget.inUse)
            assertEquals(ledger,WorldInfusionCodec.encode(f.game.state),reason)
            assertEquals(WorldInfusionCodec.encode(f.repo.load(f.player.uuid)!!),ledger)
        }
    }
    @Test fun `external matrix destruction closes once without further resource drain`() {
        Fixture().use { f->
            repeat(40) { f.game.tick() };assertTrue(f.game.smokeEntityIds().isNotEmpty())
            val ledger=WorldInfusionCodec.encode(f.game.state)
            f.map.setBlock(0,44,0,Block.AIR);f.game.tick();f.game.close();repeat(60) { f.game.tick() }
            assertTrue(f.game.isClosed);assertTrue(f.game.smokeEntityIds().isEmpty())
            assertEquals(ledger,WorldInfusionCodec.encode(f.game.state));assertEquals(1,f.map.entities.size)
        }
    }
    @Test fun `disconnect world switch and instance unload clean registered game and budget`() {
        for(reason in listOf("disconnect","switch","unload"))Fixture().use { f->
            val node=WorldInfusionServer.installCleanupEvents()
            try {
                repeat(40) { f.game.tick() };assertTrue(f.game.smokeEntityIds().isNotEmpty())
                when(reason) {
                    "disconnect"->{ f.player.remove();MinecraftServer.getGlobalEventHandler().call(PlayerDisconnectEvent(f.player)) }
                    else->{
                        val other=MinecraftServer.getInstanceManager().createInstanceContainer()
                        other.loadChunk(0,0).get(10,TimeUnit.SECONDS)
                        // Unload branch tests the unregister callback directly, without spawn callback retiring it first.
                        if(reason=="unload")MinecraftServer.getGlobalEventHandler().removeChild(node)
                        f.await(f.player.setInstance(other,Pos(.5,41.0,.5)))
                        if(reason=="unload") {
                            MinecraftServer.getGlobalEventHandler().addChild(node)
                            MinecraftServer.getInstanceManager().unregisterInstance(f.map)
                        }
                        f.player.remove();MinecraftServer.getInstanceManager().unregisterInstance(other)
                    }
                }
                assertTrue(f.game.isClosed,reason);assertTrue(f.game.smokeEntityIds().isEmpty(),reason)
                assertEquals(0,WorldInfusionSmokeBudget.inUse,reason);assertTrue(f.map.entities.isEmpty(),reason)
            } finally { MinecraftServer.getGlobalEventHandler().removeChild(node) }
        }
    }
}
