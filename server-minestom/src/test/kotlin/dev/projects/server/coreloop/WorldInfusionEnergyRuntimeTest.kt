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

class WorldInfusionEnergyRuntimeTest {
    @Test fun `energy presenter freezes at zero restores ledger and emits success-only pedestal seal without leaks`() {
        MinecraftServer.init(Auth.Offline())
        for(mode in listOf("zero","low","high","cancel-channel","interrupt")) {
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
            s=WorldInfusionEnergy.start(WorldInfusionRules.placeGear(s,s.gear.identity.id))
            val initial=s.gear
            var game=WorldInfusionServer.WorldInfusionGame(player,map,repo,repo.save(0,s),energyEnabled=true,demoPowerPerSecond=when(mode){ "zero"->0;"low"->6;else->18 })
            try {
                game.packed=true;game.rebuild();game.inventory()
                var first=false;var channel=false;var cancelled=false;var doneTransitions=0;var last=game.state.phase;var sawSeal=false;var frozen:String?=null
                repeat(690) { i->
                    if(mode=="interrupt" && i==56) { game.demoPowerPerSecond=0;frozen=WorldInfusionCodec.encode(game.state) }
                    if(mode=="interrupt" && i==100) {
                        game.close();assertEquals(0,WorldInfusionSmokeBudget.inUse)
                        game=WorldInfusionServer.WorldInfusionGame(player,map,repo,repo.load(player.uuid)!!,energyEnabled=true,demoPowerPerSecond=0)
                        game.packed=true;game.rebuild();game.inventory()
                    }
                    if(mode=="interrupt" && i==160)game.demoPowerPerSecond=18
                    game.tick()
                    if(mode=="interrupt" && i in 56..159)assertEquals(frozen,WorldInfusionCodec.encode(game.state))
                    if(map.entities.any { (it.entityMeta as? ItemDisplayMeta)?.itemStack?.get(DataComponents.ITEM_MODEL)=="projects:infusion-energy/pedestal_seal" })sawSeal=true
                    if(!first && game.state.jars.sumOf { it.amount }<12) {
                        first=true;assertEquals(listOf(3,3,3),game.state.jars.map { it.amount })
                    }
                    if(game.state.phase==InfusionPhase.INGREDIENTS && game.state.consumed==WorldInfusionRules.ingredients) {
                        channel=true;assertEquals(initial.identity,game.state.gear.identity)
                        assertEquals(initial.affixes,game.state.gear.affixes,"Weapon changes only at channel completion")
                        assertEquals(initial.enhancement,game.state.gear.enhancement);assertEquals(initial.tier,game.state.gear.tier)
                        if(mode=="cancel-channel" && !cancelled) {
                            player.setItemInMainHand(ItemStack.of(net.minestom.server.item.Material.BLAZE_ROD).withTag(Tag.String("projects_world_infusion_item"),"cast"))
                            player.isSneaking=true;game.interact(Pos(0.0,44.0,0.0),BlockFace.TOP);cancelled=true
                        }
                    }
                    if(last!=InfusionPhase.COMPLETE && game.state.phase==InfusionPhase.COMPLETE)doneTransitions++
                    last=game.state.phase
                }
                if(mode=="zero") { assertFalse(first);assertFalse(channel) } else { assertTrue(first);assertTrue(channel) }
                assertEquals(0.0,game.animationPose.speed)
                assertTrue(game.smokeStats.peak<=65);assertEquals(game.smokeStats.born,game.smokeStats.removed)
                assertFalse(map.entities.any { (it.entityMeta as? ItemDisplayMeta)?.itemStack?.get(DataComponents.ITEM_MODEL)=="projects:infusion/smoke" })
                if(mode=="cancel-channel" || mode=="zero") {
                    if(mode=="cancel-channel")assertTrue(cancelled);assertEquals(0,doneTransitions);assertEquals(initial.identity,game.state.gear.identity);assertEquals(initial.affixes,game.state.gear.affixes);assertFalse(sawSeal)
                    assertEquals(12,game.state.jars.sumOf { it.amount }+game.state.reservoir.values.sum())
                } else {
                    assertEquals(1,doneTransitions);assertEquals(5,game.state.jars.sumOf { it.amount });assertTrue(sawSeal);assertEquals(120_000L,game.state.energy!!.receivedMilli)
                    assertEquals(initial.identity,game.state.gear.identity);assertEquals(initial.affixes[1],game.state.gear.affixes[1])
                }
                assertEquals(WorldInfusionCodec.encode(game.state),WorldInfusionCodec.encode(repo.load(player.uuid)!!))
            } finally { game.close();player.remove();MinecraftServer.getInstanceManager().unregisterInstance(map) }
            assertEquals(0,WorldInfusionSmokeBudget.inUse)
        }
    }
}
