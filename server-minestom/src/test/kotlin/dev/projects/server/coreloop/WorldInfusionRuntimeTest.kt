package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.component.DataComponents
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockFace
import net.minestom.server.item.ItemStack
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import net.minestom.server.tag.Tag
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.*

class WorldInfusionRuntimeTest {
    @Test fun `real Minestom blocks hand interactions inventory displays particles and recovery perform one ritual`() {
        MinecraftServer.init(Auth.Offline())
        val map=MinecraftServer.getInstanceManager().createInstanceContainer()
        map.viewDistance(2);map.setGenerator { it.modifier().fillHeight(0,41,Block.STONE_BRICKS) }
        for(x in -1..1)for(z in -1..1)map.loadChunk(x,z).get(10,TimeUnit.SECONDS)
        val packets=mutableListOf<SendablePacket>()
        val connection=object:PlayerConnection() {
            override fun sendPacket(packet:SendablePacket) { packets+=packet }
            override fun getRemoteAddress():SocketAddress=InetSocketAddress("127.0.0.1",0)
        }
        connection.setClientState(ConnectionState.PLAY);connection.setServerState(ConnectionState.PLAY)
        val player=Player(connection,GameProfile(UUID.randomUUID(),"InfusionTest"));connection.player=player
        fun await(f:java.util.concurrent.CompletableFuture<*>) {
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
            while(!f.isDone && System.nanoTime()<deadline) { MinecraftServer.getSchedulerManager().processTick();Thread.sleep(2) }
            f.get(1,TimeUnit.SECONDS)
        }
        player.gameMode=GameMode.ADVENTURE;await(player.setInstance(map,Pos(.5,41.0,-2.5)))
        val repo=WorldInfusionRepository(Files.createTempDirectory("world-infusion-runtime-"))
        val initial=repo.save(0,WorldInfusionState.fresh(player.uuid))
        val game=WorldInfusionServer.WorldInfusionGame(player,map,repo,initial)
        val tag=Tag.String("projects_world_infusion_item")
        fun hand(prefix:String?) {
            player.setItemInMainHand(if(prefix==null)ItemStack.AIR else (0 until 36).map { player.inventory.getItemStack(it) }
                .first { it.getTag(tag)?.startsWith(prefix)==true })
        }
        fun click(c:InfusionCell,y:Int,id:String?) {
            hand(id);await(player.teleport(Pos(c.x+.5,41.0,c.z-2.5)))
            game.interact(Pos(c.x.toDouble(),y.toDouble(),c.z.toDouble()),BlockFace.TOP)
            repeat(4) { game.tick() }
        }
        try {
            game.inventory();val center=InfusionCell(0,41,0)
            click(center,40,"matrix")
            assertEquals(center,game.state.matrix,packets.takeLast(2).toString())
            assertEquals(Block.LODESTONE,map.getBlock(0,44,0))
            assertEquals(Block.CUT_COPPER,map.getBlock(-1,43,-1))
            val cells=listOf(InfusionCell(3,41,0),InfusionCell(0,41,3),InfusionCell(-3,41,0),InfusionCell(0,41,-3))
            val resources=listOf(CoreResource.INGOT,CoreResource.CLOTH,CoreResource.STONE_BLOCK,CoreResource.INGOT)
            for((i,c) in cells.withIndex()) { click(c,40,"pedestal");click(c,41,"material:${resources[i].name}") }
            for((i,j) in game.state.jars.toList().withIndex())click(InfusionCell(-4+i*4,41,4),40,"jar:${j.id}")
            click(center,41,"gear:")
            assertEquals(InfusionGearPlace.CENTER,game.state.gearPlace)
            assertEquals(0,(0 until 36).count { player.inventory.getItemStack(it).getTag(tag)?.startsWith("gear:")==true })
            val before=game.state.gear
            click(center,44,"cast")
            assertEquals(InfusionPhase.ESSENTIA,game.state.phase)
            assertEquals(4,game.state.pedestals.count { it.item!=null })
            game.packed=true;game.rebuild()
            repeat(20) { game.tick() }
            assertTrue(map.entities.any { (it.entityMeta as? ItemDisplayMeta)?.itemStack?.get(DataComponents.CUSTOM_MODEL_DATA)?.colors()?.isNotEmpty()==true },"Packed smoke must use real tinted billboard entities")
            repeat(220) { game.tick() }
            assertFalse(map.entities.any { (it.entityMeta as? ItemDisplayMeta)?.itemStack?.get(DataComponents.ITEM_MODEL)=="projects:infusion/smoke" },"Expired smoke must leave no display entities")
            assertEquals(InfusionPhase.COMPLETE,game.state.phase)
            assertEquals(5,game.state.jars.sumOf { it.amount })
            assertTrue(packets.any { it is ParticlePacket })
            assertEquals(before.identity,game.state.gear.identity)
            assertEquals(before.affixes[1],game.state.gear.affixes[1])
            assertEquals(WorldInfusionRules.MOD,game.state.gear.affixes[0].stone.modId)
            click(center,41,null)
            assertEquals(InfusionGearPlace.INVENTORY,game.state.gearPlace)
            assertEquals(1,(0 until 36).count { player.inventory.getItemStack(it).getTag(tag)?.startsWith("gear:")==true })
            assertEquals(WorldInfusionCodec.encode(game.state),WorldInfusionCodec.encode(repo.load(player.uuid)!!))
            assertTrue(map.entities.size>=6,"Physical world display entities must exist")
        } finally { game.close();player.remove();MinecraftServer.getInstanceManager().unregisterInstance(map) }
    }
}
