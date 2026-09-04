package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** In-memory connections plus real Minestom display entities; no port/client or game input. */
class CoreWorldLootTest {
    @Test fun `one source creates one visible drop before and after its successful pickup`() = arena { h ->
        h.loot.spawn("enemy-1", CoreLootKind.ELITE, h.lootPosition)
        h.loot.spawn("enemy-1", CoreLootKind.ELITE, h.lootPosition)
        assertEquals(1, h.loot.remainingCount())
        assertEquals(2, h.displayCount())
        h.move(h.lootPosition)
        h.loot.tick()
        assertEquals(1, h.requests.size)
        h.finish("enemy-1")
        assertEquals(0, h.loot.remainingCount())
        assertEquals(0, h.displayCount())
        h.loot.spawn("enemy-1", CoreLootKind.ELITE, h.lootPosition)
        h.loot.tick()
        assertEquals(0, h.loot.remainingCount(), "A paid source must not visually respawn")
        assertEquals(0, h.displayCount())
        assertEquals(1, h.requests.size)
        assertEquals(1, h.inventoryUpdates)
    }

    @Test fun `proximity checks the owner and range then submits once while persistence is pending`() = arena { h ->
        h.loot.spawn("enemy-2", CoreLootKind.NORMAL, h.lootPosition)
        val visitor = h.visitor(h.lootPosition)
        h.loot.tick()
        assertTrue(h.requests.isEmpty(), "Nearby non-owner cannot collect the owner's drop")
        h.move(h.lootPosition.add(0.0, 0.0, 3.0))
        h.loot.tick()
        assertTrue(h.requests.isEmpty())
        h.move(h.lootPosition.add(0.0, 0.0, 2.4))
        repeat(20) { h.loot.tick() }
        assertEquals(listOf(CoreAction.AffixLoot(h.run.id, "enemy-2", CoreLootKind.NORMAL)), h.requests)
        assertEquals(2, h.displayCount(), "Pending writes retain a visible drop")
        h.finish("enemy-2")
        assertEquals(0, h.displayCount())
        assertTrue(visitor.isOnline)
    }

    @Test fun `offline owner or owner in another instance does not proximity collect`() = arena { h ->
        h.loot.spawn("offline", CoreLootKind.NORMAL, h.lootPosition)
        h.move(h.lootPosition)
        h.connection.connected = false
        h.loot.tick()
        assertTrue(h.requests.isEmpty())
        h.connection.connected = true
        val other = h.otherInstance()
        h.owner.setInstance(other, h.lootPosition).get(10, TimeUnit.SECONDS)
        h.loot.tick()
        assertTrue(h.requests.isEmpty())
        h.owner.setInstance(h.instance, h.lootPosition).get(10, TimeUnit.SECONDS)
        h.loot.tick()
        assertEquals(1, h.requests.size)
        h.finish("offline")
    }

    @Test fun `return collectAll recovers distant drops and waits for every durable reward`() = arena { h ->
        h.loot.spawn("far-one", CoreLootKind.NORMAL, h.lootPosition)
        h.loot.spawn("far-two", CoreLootKind.ELITE, h.lootPosition.add(7.0, 0.0, 0.0))
        h.connection.connected = false
        val first = h.loot.collectAll()
        val second = h.loot.collectAll()
        assertEquals(2, h.requests.size)
        assertFalse(first.isDone)
        assertFalse(second.isDone)
        h.finish("far-one")
        assertFalse(first.isDone)
        h.finish("far-two")
        first.get(3, TimeUnit.SECONDS); second.get(3, TimeUnit.SECONDS)
        assertEquals(0, h.loot.remainingCount())
        assertEquals(0, h.displayCount())
        assertEquals(0, h.inventoryUpdates, "Disconnected owner is not sent gameplay updates")
    }

    @Test fun `failed persistence retains display and allows a single later retry`() = arena { h ->
        h.loot.spawn("retry", CoreLootKind.ELITE, h.lootPosition)
        h.move(h.lootPosition)
        h.loot.tick()
        h.pending.getValue("retry").complete(CoreTransactionResult(CoreTransactionStatus.SAVE_FAILED, null, "保存失敗"))
        h.flushScheduler()
        assertEquals(1, h.loot.remainingCount())
        assertEquals(2, h.displayCount())
        assertEquals(0, h.inventoryUpdates)
        repeat(5) { h.loot.tick() }
        assertEquals(2, h.requests.size)
        h.finish("retry")
        assertEquals(0, h.displayCount())
        assertEquals(1, h.inventoryUpdates)
    }

    @Test fun `dispose removes all displays and late save completion cannot revive entities or update inventory`() = arena { h ->
        h.loot.spawn("pending", CoreLootKind.NORMAL, h.lootPosition)
        h.loot.spawn("untouched", CoreLootKind.ELITE, h.lootPosition.add(12.0, 0.0, 0.0))
        h.move(h.lootPosition)
        h.loot.tick()
        assertEquals(4, h.displayCount())
        h.loot.dispose()
        h.loot.dispose()
        assertEquals(0, h.displayCount())
        assertEquals(0, h.loot.remainingCount())
        h.finish("pending")
        h.loot.spawn("after-dispose", CoreLootKind.BOSS, h.lootPosition)
        repeat(20) { h.loot.tick() }
        assertEquals(0, h.displayCount())
        assertEquals(0, h.inventoryUpdates)
        assertEquals(1, h.requests.size)
    }

    private fun arena(test: (Harness) -> Unit) {
        MinecraftServer.init(Auth.Offline())
        Harness().use(test)
    }

    private class Harness : AutoCloseable {
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        val connection = MemoryConnection()
        val owner: Player
        val lootPosition = Pos(8.5, 40.0, 8.5)
        val run = CoreActiveRun(UUID.randomUUID(), CoreOwnedMap(UUID.randomUUID(), 887766, 1))
        val requests = mutableListOf<CoreAction.AffixLoot>()
        val pending = mutableMapOf<String, CompletableFuture<CoreTransactionResult>>()
        var inventoryUpdates = 0
        val loot: CoreWorldLoot
        private val visitors = mutableListOf<Player>()
        private val otherInstances = mutableListOf<InstanceContainer>()

        init {
            prepare(instance)
            owner = player(connection, instance, Pos(8.5, 40.0, -5.5), "LootOwner")
            loot = CoreWorldLoot(owner, instance, run, { action ->
                requests += action
                CompletableFuture<CoreTransactionResult>().also { pending[action.sourceId] = it }
            }, { inventoryUpdates++ })
        }
        private fun prepare(target: InstanceContainer) {
            target.viewDistance(2)
            target.setGenerator { it.modifier().fillHeight(0, 40, Block.STONE) }
            for (x in -3..3) for (z in -3..3) target.loadChunk(x, z).get(10, TimeUnit.SECONDS)
        }
        private fun player(connection: MemoryConnection, target: InstanceContainer, pos: Pos, name: String): Player {
            connection.setClientState(ConnectionState.PLAY); connection.setServerState(ConnectionState.PLAY)
            return Player(connection, GameProfile(UUID.randomUUID(), name)).apply {
                connection.player = this
                gameMode = GameMode.ADVENTURE
                setInstance(target, pos).get(10, TimeUnit.SECONDS)
            }
        }
        fun visitor(pos: Pos): Player = player(MemoryConnection(), instance, pos, "LootVisitor").also { visitors += it }
        fun otherInstance(): InstanceContainer = MinecraftServer.getInstanceManager().createInstanceContainer().also {
            prepare(it); otherInstances += it
        }
        fun move(pos: Pos) { owner.teleport(pos).get(10, TimeUnit.SECONDS) }
        fun displayCount() = instance.entities.count { it.entityType == EntityType.ITEM_DISPLAY || it.entityType == EntityType.TEXT_DISPLAY }
        fun finish(source: String) {
            assertTrue(pending.getValue(source).complete(CoreTransactionResult(CoreTransactionStatus.COMMITTED, null, "回収済み")))
            flushScheduler()
        }
        fun flushScheduler() { repeat(3) { MinecraftServer.getSchedulerManager().processTick() } }
        override fun close() {
            loot.dispose(); connection.connected = true
            visitors.forEach { it.remove() }; owner.remove()
            flushScheduler()
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
            otherInstances.forEach { MinecraftServer.getInstanceManager().unregisterInstance(it) }
        }
    }

    private class MemoryConnection : PlayerConnection() {
        var connected = true
        override fun isOnline() = connected
        override fun sendPacket(packet: SendablePacket) = Unit
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }
}
