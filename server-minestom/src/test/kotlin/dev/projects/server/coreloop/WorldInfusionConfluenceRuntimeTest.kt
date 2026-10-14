package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
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

class WorldInfusionConfluenceRuntimeTest {
    @Test fun `actual display presenter supports low high atomic intake weapon channel cancel and no leaked entities`() {
        MinecraftServer.init(Auth.Offline())
        for(mode in listOf("low","high","cancel-channel")) {
            val map=MinecraftServer.getInstanceManager().createInstanceContainer()
            map.viewDistance(2);map.setGenerator { it.modifier().fillHeight(0,41,Block.STONE_BRICKS) }
            for(x in -1..1)for(z in -1..1)map.loadChunk(x,z).get(10,TimeUnit.SECONDS)
            val connection=object:PlayerConnection() {
                override fun sendPacket(packet:SendablePacket) {}
                override fun getRemoteAddress():SocketAddress=InetSocketAddress("127.0.0.1",0)
            }
            connection.setClientState(ConnectionState.PLAY);connection.setServerState(ConnectionState.PLAY)
            val player=Player(connection,GameProfile(UUID.randomUUID(),"ConfluenceTest"));connection.player=player
            val spawn=player.setInstance(map,Pos(.5,41.0,-2.5))
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
            while(!spawn.isDone && System.nanoTime()<deadline) { MinecraftServer.getSchedulerManager().processTick();Thread.sleep(2) }
            spawn.get(1,TimeUnit.SECONDS)
            val repo=WorldInfusionRepository(Files.createTempDirectory("confluence-runtime-"))
            var s=WorldInfusionRules.placeMatrix(WorldInfusionState.fresh(player.uuid),InfusionCell(0,41,0))
            for((i,r) in listOf(CoreResource.INGOT,CoreResource.CLOTH,CoreResource.INGOT,CoreResource.STONE_BLOCK).withIndex()) {
                val c=listOf(InfusionCell(3,41,0),InfusionCell(0,41,3),InfusionCell(-3,41,0),InfusionCell(0,41,-3))[i]
                s=WorldInfusionRules.placePedestal(s,c);s=WorldInfusionRules.material(s,c,r)
            }
            for((i,j) in s.jars.withIndex())s=WorldInfusionRules.placeJar(s,j.id,listOf(InfusionCell(-4,41,2),InfusionCell(4,41,2),InfusionCell(0,41,5))[i])
            s=WorldInfusionRules.start(WorldInfusionRules.placeGear(s,s.gear.identity.id))
            val initial=s.gear
            val game=WorldInfusionServer.WorldInfusionGame(player,map,repo,repo.save(0,s),true,if(mode=="low")0.0 else 1.0)
            try {
                game.packed=true;game.rebuild();game.inventory()
                var first=false;var channel=false;var cancelled=false;var doneTransitions=0;var last=game.state.phase
                repeat(490) {
                    game.tick()
                    if(!first && game.state.jars.sumOf { it.amount }<12) {
                        first=true;assertEquals(listOf(3,3,3),game.state.jars.map { it.amount })
                    }
                    if(game.state.phase==InfusionPhase.INGREDIENTS && game.state.consumed==WorldInfusionRules.ingredients) {
                        channel=true;assertEquals(initial,game.state.gear,"Weapon changes only at channel completion")
                        if(mode=="cancel-channel" && !cancelled) {
                            player.setItemInMainHand(ItemStack.of(net.minestom.server.item.Material.BLAZE_ROD).withTag(Tag.String("projects_world_infusion_item"),"cast"))
                            player.isSneaking=true;game.interact(Pos(0.0,44.0,0.0),BlockFace.TOP);cancelled=true
                        }
                    }
                    if(last!=InfusionPhase.COMPLETE && game.state.phase==InfusionPhase.COMPLETE)doneTransitions++
                    last=game.state.phase
                }
                assertTrue(first);assertTrue(channel)
                assertEquals(0.0,game.animationPose.speed)
                assertTrue(game.smokeStats.peak<=64);assertEquals(game.smokeStats.born,game.smokeStats.removed)
                assertFalse(map.entities.any { (it.entityMeta as? ItemDisplayMeta)?.itemStack?.get(DataComponents.ITEM_MODEL)=="projects:infusion/smoke" })
                if(mode=="cancel-channel") {
                    assertTrue(cancelled);assertEquals(0,doneTransitions);assertEquals(initial,game.state.gear)
                    assertEquals(12,game.state.jars.sumOf { it.amount }+game.state.reservoir.values.sum())
                } else {
                    assertEquals(1,doneTransitions);assertEquals(5,game.state.jars.sumOf { it.amount })
                    assertEquals(initial.identity,game.state.gear.identity);assertEquals(initial.affixes[1],game.state.gear.affixes[1])
                }
                assertEquals(WorldInfusionCodec.encode(game.state),WorldInfusionCodec.encode(repo.load(player.uuid)!!))
            } finally { game.close();player.remove();MinecraftServer.getInstanceManager().unregisterInstance(map) }
            assertEquals(0,WorldInfusionSmokeBudget.inUse)
        }
    }
}
