package dev.projects.server.coreloop.adventure

import dev.projects.server.coreloop.*
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.*
import net.minestom.server.instance.block.Block
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Real instances, Player transfers, run coordinator and durable account ledger; no Minecraft socket or client. */
class CoreDungeonExpeditionsTest {
    @Test fun `four players clear twelve rooms three bosses and individually return with durable exclusive loot`() = harness(4) { h ->
        h.launch(); h.flush()
        val run = assertNotNull(h.expeditions.run(h.players.first()))
        assertEquals(4, run.playerIds.size)
        h.players.forEach { assertSame(run, h.expeditions.run(it)); assertSame(run.instance, it.instance) }
        repeat(run.world.plan.stages) { stage ->
            h.clearRoom(run)
            assertEquals(stage + 1, h.account(h.players.first())!!.activeRun!!.dungeon!!.rewardedStage)
            if (stage + 1 < run.world.plan.stages) {
                assertEquals(DungeonRunPhase.CHOOSING, run.phase)
                h.players.forEach { assertTrue(run.chooseBoon(it, run.view(it)!!.boonOffers.first())) }
                assertTrue(run.chooseRoom(h.players.first(), run.view(h.players.first())!!.choices.first().id)); h.flush()
            }
        }
        assertEquals(DungeonRunPhase.COMPLETE, run.phase)
        assertEquals(48, h.rewardCalls)
        h.players.forEach { p ->
            assertEquals(1, h.account(p)!!.amount(CoreCraftingCurrency.ASTRAL))
            assertEquals(0, h.account(p)!!.dungeonRecords[1])
            assertTrue(h.account(p)!!.balances.keys.none { it.resource.raw })
            assertTrue(h.expeditions.returnToHarbor(p)); h.flush()
            assertSame(h.hub, p.instance); assertNull(h.account(p)!!.activeRun)
            h.service.forget(p.uuid); h.service.open(p.uuid)
            assertEquals(1, h.account(p)!!.amount(CoreCraftingCurrency.ASTRAL))
        }
        h.expeditions.tick(run.instance, ++h.now)
        assertFalse(run.instance.isRegistered)
    }

    @Test fun `one participant leaves without disposing shared world and final departure disposes it`() = harness(2) { h ->
        h.launch(); h.flush()
        val run = h.expeditions.run(h.players.first())!!
        assertTrue(h.expeditions.returnToHarbor(h.players.first())); h.flush()
        assertTrue(run.instance.isRegistered); assertTrue(run.canFight(h.players.last()))
        assertEquals(h.players.last().uuid, run.leader)
        assertTrue(h.expeditions.returnToHarbor(h.players.last())); h.flush()
        h.expeditions.tick(run.instance, ++h.now); assertFalse(run.instance.isRegistered)
    }

    @Test fun `partial entry reservation failure aborts every ledger and never builds a world`() = harness(2) { h ->
        h.rejectStart = h.players.last().uuid
        h.launch(); h.flush()
        assertTrue(h.builds.isEmpty())
        h.players.forEach { assertNull(h.account(it)!!.activeRun); assertFalse(h.expeditions.isDeparting(it)); assertSame(h.hub, it.instance) }
        assertTrue(h.expeditions.parties.list().isEmpty())
    }

    @Test fun `disconnect during background generation never transfers replacement login and releases abandoned world`() = harness(2) { h ->
        h.launch(); h.flushJobs()
        assertEquals(1, h.builds.size)
        val old = h.players.last(); h.online.remove(old.uuid); h.expeditions.disconnect(old)
        h.transactNow(old, CoreAction.FinishRun(h.account(old)!!.activeRun!!.id))
        old.remove()
        val replacement = h.addPlayer(old.uuid)
        h.flush()
        assertSame(h.hub, replacement.instance); assertNull(h.account(replacement)!!.activeRun)
        assertNull(h.account(h.players.first())!!.activeRun)
        MinecraftServer.getInstanceManager().instances.toList().filter { it !== h.hub }.forEach { h.expeditions.tick(it, ++h.now) }
        assertEquals(listOf(h.hub), MinecraftServer.getInstanceManager().instances.toList())
    }

    @Test fun `failed return save keeps ledger and retry terminates only once`() = harness(1) { h ->
        h.launch(); h.flush(); val p = h.players.first(); val run = h.expeditions.run(p)!!
        h.clearRoom(run); val before = h.account(p)!!.currencies
        h.failFinish = true; assertTrue(h.expeditions.returnToHarbor(p)); h.flush()
        assertNotNull(h.account(p)!!.activeRun); assertSame(run.instance, p.instance)
        h.failFinish = false; assertTrue(h.expeditions.returnToHarbor(p)); h.flush()
        assertSame(h.hub, p.instance); assertNull(h.account(p)!!.activeRun)
        assertEquals(before, h.account(p)!!.currencies)
    }

    private fun harness(count: Int, body: (Harness) -> Unit) {
        MinecraftServer.init(Auth.Offline()); Harness(count).use(body)
    }
    private class Harness(count: Int) : CoreDungeonHost, AutoCloseable {
        var now = 1000L
        val service = CoreAccountService(CoreAccountRepository(Files.createTempDirectory("core-dungeon-coordinator-")))
        val hub = MinecraftServer.getInstanceManager().createInstanceContainer().also { instance ->
            instance.viewDistance(2); instance.setGenerator { it.modifier().fillHeight(38, 40, Block.STONE) }
            for (x in -4..4) for (z in -4..4) instance.loadChunk(x, z).join()
        }
        val online = linkedMapOf<UUID, Player>()
        val allPlayers = mutableListOf<Player>()
        val players = List(count) { addPlayer(UUID.randomUUID()) }
        val jobs = ArrayDeque<() -> Unit>()
        val builds = ArrayDeque<Runnable>()
        val expeditions = CoreDungeonExpeditions(this, Executor { builds += it })
        var rejectStart: UUID? = null
        var failFinish = false
        var rewardCalls = 0
        fun addPlayer(id: UUID): Player {
            service.open(id)
            val connection = object : PlayerConnection() {
                override fun sendPacket(packet: SendablePacket) {}
                override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
            }
            connection.setClientState(ConnectionState.PLAY); connection.setServerState(ConnectionState.PLAY)
            val p = Player(connection, GameProfile(id, "Member" + allPlayers.size)); connection.player = p
            p.gameMode = GameMode.ADVENTURE; p.setInstance(hub, Pos(8.5, 40.0, 8.5)).get(10, TimeUnit.SECONDS)
            online[id] = p; allPlayers += p; return p
        }
        override fun nowMillis() = now
        override fun connected(player: Player) = online[player.uuid] === player && !player.isRemoved
        override fun player(id: UUID) = online[id]
        override fun account(player: Player) = service.snapshot(player.uuid)
        override fun eligible(player: Player) = connected(player) && player.instance === hub && account(player)?.activeRun == null
        override fun schedule(action: () -> Unit) { jobs += action }
        override fun hurt(player: Player, damage: Double) {}
        override fun resetActions(player: Player) {}
        override fun revive(player: Player, fraction: Double) {}
        override fun showRunMenu(player: Player) {}
        override fun refreshed(player: Player) {}
        override fun drain(player: Player): CompletableFuture<Void> = CompletableFuture.completedFuture(null)
        override fun harbor(player: Player) = player.setInstance(hub, Pos(8.5, 40.0, 8.5)).thenApply { true }
        fun transactNow(player: Player, action: CoreAction, revision: Long? = null): CoreTransactionResult =
            service.transact(player.uuid, CoreOperation(UUID.randomUUID(), revision ?: account(player)!!.revision, action))
        override fun transact(player: Player, action: CoreAction, revision: Long?): CompletableFuture<CoreTransactionResult> {
            if (action is CoreAction.StartDungeon && player.uuid == rejectStart || action is CoreAction.FinishRun && failFinish)
                return CompletableFuture.completedFuture(CoreTransactionResult(CoreTransactionStatus.SAVE_FAILED, account(player), "injected disk failure"))
            return CompletableFuture.completedFuture(transactNow(player, action, revision))
        }
        override fun reward(player: Player, action: CoreAction.DungeonReward): CompletableFuture<CoreTransactionResult> {
            rewardCalls++; return transact(player, action)
        }
        fun launch() {
            expeditions.lobby(players.first(), DungeonLobbyAction.Create(1, 0))
            val id = expeditions.parties.list().single().id
            players.drop(1).forEach { expeditions.lobby(it, DungeonLobbyAction.Join(id)) }
            players.forEach { expeditions.lobby(it, DungeonLobbyAction.Ready) }
            expeditions.lobby(players.first(), DungeonLobbyAction.Start)
        }
        fun flushJobs() { while (jobs.isNotEmpty()) jobs.removeFirst()() }
        fun flush() { do { flushJobs(); while (builds.isNotEmpty()) builds.removeFirst().run() } while (jobs.isNotEmpty() || builds.isNotEmpty()) }
        fun clearRoom(run: DungeonRun) {
            repeat(500) {
                if (run.phase != DungeonRunPhase.FIGHTING) { flush(); return }
                val c = run.combat!!; val p = players.first()
                c.entities().toList().forEach { e ->
                    p.teleport(e.position.sub(0.0, 0.0, 1.5)).join(); c.applyEffectDamage(e.uuid, p, 999999.0)
                }
                run.bossMechanics()?.let { mechanic ->
                    val markers = mechanic.markerEntities()
                    val order = Regex("碑文 ([1-3→]+)").find(mechanic.title)?.groupValues?.get(1)?.split('→')?.map { it.toInt() - 1 } ?: markers.indices.toList()
                    order.forEach { index -> p.teleport(markers[index].position).join(); run.interact(p, markers[index], now) }
                }
                now += 250; expeditions.tick(run.instance, now); flush()
            }
            fail("Room did not terminate: ${run.objective()}")
        }
        override fun close() {
            expeditions.close(); allPlayers.forEach { if (!it.isRemoved) it.remove() }
            MinecraftServer.getInstanceManager().instances.toList().forEach { instance ->
                instance.entities.toList().forEach { it.remove() }
                if (instance.isRegistered) MinecraftServer.getInstanceManager().unregisterInstance(instance)
            }
        }
    }
}
