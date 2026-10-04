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
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import net.minestom.server.tag.Tag
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.*

class WorldFoundryRuntimeTest {
    @Test fun `actual Minestom placement interaction displays fallback and restart complete a casting`() {
        MinecraftServer.init(Auth.Offline())
        val map=MinecraftServer.getInstanceManager().createInstanceContainer().apply {
            viewDistance(2);setGenerator { it.modifier().fillHeight(0,41,Block.STONE_BRICKS) }
            for(x in -1..0)for(z in -1..0)loadChunk(x,z).get(10,TimeUnit.SECONDS)
        }
        val packets=mutableListOf<SendablePacket>()
        val connection=object:PlayerConnection() {
            override fun sendPacket(packet:SendablePacket) { packets+=packet }
            override fun getRemoteAddress():SocketAddress=InetSocketAddress("127.0.0.1",0)
        }.apply { setClientState(ConnectionState.PLAY);setServerState(ConnectionState.PLAY) }
        val player=Player(connection,GameProfile(UUID.randomUUID(),"FoundryTest")).also { connection.player=it }
        fun await(f:java.util.concurrent.CompletableFuture<*>) {
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
            while(!f.isDone && System.nanoTime()<deadline) { MinecraftServer.getSchedulerManager().processTick();Thread.sleep(2) }
            f.get(1,TimeUnit.SECONDS)
        }
        player.gameMode=GameMode.ADVENTURE;await(player.setInstance(map,Pos(.5,41.0,-2.5)))
        val dir=Files.createTempDirectory("foundry-runtime-")
        val repo=WorldFoundryRepository(dir)
        var game=WorldFoundryServer.Game(player,map,repo,repo.save(0,WorldFoundryState.fresh(player.uuid)))
        val tag=Tag.String("projects_foundry")
        fun click(x:Int,y:Int,z:Int,id:String?,face:BlockFace=BlockFace.TOP,sneak:Boolean=false) {
            player.setItemInMainHand(if(id==null)ItemStack.AIR else (0 until 36).map { player.inventory.getItemStack(it) }.first { it.getTag(tag)==id })
            player.isSneaking=sneak
            game.interact(Pos(x.toDouble(),y.toDouble(),z.toDouble()),face)
            repeat(4) { game.tick() };player.isSneaking=false
        }
        fun models()=map.entities.mapNotNull { (it.entityMeta as? ItemDisplayMeta)?.itemStack?.get(DataComponents.ITEM_MODEL) }.toSet()
        try {
            game.refresh()
            click(0,40,0,"kit:HEATER")
            click(0,41,0,"kit:MELTER")
            click(0,42,0,"kit:FAUCET",BlockFace.EAST)
            click(1,40,0,"kit:TABLE")
            assertTrue(WorldFoundryRules.linked(game.state))
            assertEquals(Block.CAULDRON,map.getBlock(0,42,0));assertTrue(models().isEmpty(),"No display is needed for native interaction")
            click(0,41,0,"coal");click(0,42,0,"metal")
            repeat(150) { game.tick() }
            assertEquals(4,game.state.liquid);assertEquals(Block.LAVA_CAULDRON,map.getBlock(0,42,0))
            click(1,41,0,"sand");click(1,41,0,"pattern:ROD")
            assertEquals(FoundryPhase.PATTERN,game.state.phase)
            click(1,42,0,null) // Prototype still in place: fail without spending metal.
            assertEquals(4,game.state.liquid)
            click(1,41,0,null)
            game.packed=true;game.refresh()
            assertTrue("projects:foundry/mold_rod" in models())
            val faucet=map.entities.single { (it.entityMeta as? ItemDisplayMeta)?.itemStack?.get(DataComponents.ITEM_MODEL)=="projects:foundry/faucet" }
            assertEquals(-90f,faucet.position.yaw())
            click(1,42,0,null)
            assertEquals(FoundryPhase.POURING,game.state.phase);assertEquals(2,game.state.liquid)
            assertTrue("projects:foundry/stream" in models())
            click(1,42,0,null)
            assertEquals(2,game.state.castUnits)
            click(1,41,0,null,sneak=true)
            assertTrue(FoundryEquipment.TABLE in game.state.equipment)
            val persisted=repo.load(player.uuid)!!
            game.close();game.close()
            assertTrue(models().isEmpty(),"Close removes all owned model entities")
            game=WorldFoundryServer.Game(player,map,WorldFoundryRepository(dir),persisted).also { it.packed=true;it.refresh() }
            assertEquals(2,game.state.castUnits)
            // External destruction does not spend metal or allow a floating worksite to advance that second.
            map.setBlock(0,42,0,Block.AIR)
            repeat(20) { game.tick() }
            assertEquals(Block.BARRIER,map.getBlock(0,42,0))
            repeat(160) { game.tick() }
            assertEquals(FoundryPhase.OUTPUT,game.state.phase)
            assertTrue("projects:foundry/part_rod" in models());assertFalse("projects:foundry/stream" in models())
            click(1,41,0,null);click(1,41,0,null)
            assertEquals(1,game.state.parts[FoundryShape.ROD]);assertEquals(32,WorldFoundryRules.metalUnits(game.state))
            assertEquals(WorldFoundryCodec.encode(game.state),WorldFoundryCodec.encode(repo.load(player.uuid)!!))
            game.packed=false;game.refresh();assertTrue(models().isEmpty())
            assertEquals(Block.SMITHING_TABLE,map.getBlock(1,41,0));assertTrue(packets.isNotEmpty())
        } finally { game.close();player.remove();MinecraftServer.getInstanceManager().unregisterInstance(map) }
    }
}
