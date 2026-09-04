package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
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
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Real chest/text/interaction entities with read-only Minecraft world geometry and fake connections. */
class CoreMapCachesTest {
    @Test fun `cache owns visible chest label and large interaction target and opens only once`() = arena { h ->
        assertEquals(3, h.cacheEntities().size)
        assertEquals(setOf(EntityType.ITEM_DISPLAY, EntityType.TEXT_DISPLAY, EntityType.INTERACTION), h.cacheEntities().map { it.entityType }.toSet())
        val targets = h.cacheEntities().toList()
        val target = targets.single { it.entityType == EntityType.INTERACTION }
        assertTrue(h.caches.interact(h.owner, target))
        assertEquals(listOf(0), h.opened.map { it.first })
        assertEquals(h.cachePosition, h.opened.single().second)
        assertTrue(h.cacheEntities().isEmpty())
        targets.forEach { assertFalse(h.caches.interact(h.owner, it)) }
        assertEquals(1, h.opened.size)
    }

    @Test fun `nearby living enemy guard blocks opening until defeated`() = arena { h ->
        val target = h.target()
        h.guarded = true
        assertTrue(h.caches.interact(h.owner, target))
        assertEquals(1, h.guardChecks)
        assertTrue(h.opened.isEmpty())
        assertEquals(3, h.cacheEntities().size)
        h.guarded = false
        assertTrue(h.caches.interact(h.owner, target))
        assertEquals(2, h.guardChecks)
        assertEquals(1, h.opened.size)
        assertTrue(h.cacheEntities().isEmpty())
    }

    @Test fun `foreign player distant owner and owner in wrong instance cannot open or trigger guard callback`() = arena { h ->
        val target = h.target()
        val visitor = h.visitor(h.owner.position)
        assertTrue(h.caches.interact(visitor, target))
        h.owner.teleport(Pos(8.5, 40.0, 5.5)).get(10, TimeUnit.SECONDS)
        assertTrue(h.caches.interact(h.owner, target))
        val other = h.otherInstance()
        h.owner.setInstance(other, h.cachePosition).get(10, TimeUnit.SECONDS)
        assertTrue(h.caches.interact(h.owner, target))
        assertEquals(0, h.guardChecks)
        assertTrue(h.opened.isEmpty())
        assertEquals(3, h.cacheEntities().size)
        h.owner.setInstance(h.instance, Pos(8.5, 40.0, 8.5)).get(10, TimeUnit.SECONDS)
        assertTrue(h.caches.interact(h.owner, target))
        assertEquals(1, h.opened.size)
    }

    @Test fun `server line of sight blocks cache through a wall then permits clear view`() = arena { h ->
        val target = h.target()
        for (y in 40..43) h.instance.setBlock(8, y, 10, Block.STONE_BRICKS)
        assertTrue(h.caches.interact(h.owner, target))
        assertTrue(h.opened.isEmpty())
        assertEquals(0, h.guardChecks)
        for (y in 40..43) h.instance.setBlock(8, y, 10, Block.AIR)
        assertTrue(h.caches.interact(h.owner, target))
        assertEquals(1, h.opened.size)
    }

    @Test fun `clicking unrelated entity is not claimed and dispose removes every unopened cache entity`() = arena(2) { h ->
        val unrelated = Entity(EntityType.INTERACTION)
        try { assertFalse(h.caches.interact(h.owner, unrelated)) } finally { unrelated.remove() }
        assertEquals(6, h.cacheEntities().size)
        val oldTargets = h.cacheEntities().toList()
        h.caches.dispose()
        h.caches.dispose()
        assertTrue(h.cacheEntities().isEmpty())
        oldTargets.forEach { assertFalse(h.caches.interact(h.owner, it)) }
        assertTrue(h.opened.isEmpty())
    }

    @Test fun `multiple caches preserve original indexes and each pays once`() = arena(2) { h ->
        val targets = h.cacheEntities().filter { it.entityType == EntityType.INTERACTION }.sortedBy { it.position.x() }
        assertEquals(2, targets.size)
        targets.forEach { target ->
            h.owner.teleport(target.position.sub(0.0, 0.0, 2.0)).get(10, TimeUnit.SECONDS)
            assertTrue(h.caches.interact(h.owner, target))
            assertFalse(h.caches.interact(h.owner, target))
        }
        assertEquals(listOf(0, 1), h.opened.map { it.first })
        assertTrue(h.cacheEntities().isEmpty())
    }

    private fun arena(count: Int = 1, test: (Harness) -> Unit) {
        MinecraftServer.init(Auth.Offline())
        Harness(count).use(test)
    }

    private class Harness(count: Int) : AutoCloseable {
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        val owner: Player
        val cachePosition = Pos(8.5, 40.0, 12.5)
        val opened = mutableListOf<Pair<Int, Pos>>()
        var guarded = false
        var guardChecks = 0
        val caches: CoreMapCaches
        private val visitors = mutableListOf<Player>()
        private val otherInstances = mutableListOf<InstanceContainer>()

        init {
            prepare(instance)
            owner = player(instance, Pos(8.5, 40.0, 8.5), "CacheOwner")
            caches = CoreMapCaches(owner, instance, List(count) { cachePosition.add(it * 8.0, 0.0, 0.0) },
                { guardChecks++; guarded }, { index, position -> opened += index to position })
        }
        private fun prepare(target: InstanceContainer) {
            target.viewDistance(2)
            target.setGenerator { it.modifier().fillHeight(0, 40, Block.STONE) }
            for (x in -3..3) for (z in -3..3) target.loadChunk(x, z).get(10, TimeUnit.SECONDS)
        }
        private fun player(target: InstanceContainer, pos: Pos, name: String): Player {
            val connection = MemoryConnection()
            connection.setClientState(ConnectionState.PLAY); connection.setServerState(ConnectionState.PLAY)
            return Player(connection, GameProfile(UUID.randomUUID(), name)).apply {
                connection.player = this
                gameMode = GameMode.ADVENTURE
                setInstance(target, pos).get(10, TimeUnit.SECONDS)
            }
        }
        fun visitor(pos: Pos): Player = player(instance, pos, "CacheVisitor").also { visitors += it }
        fun otherInstance(): InstanceContainer = MinecraftServer.getInstanceManager().createInstanceContainer().also {
            prepare(it); otherInstances += it
        }
        fun cacheEntities() = instance.entities.filter { it !is Player }
        fun target(): Entity = cacheEntities().single { it.entityType == EntityType.INTERACTION }
        override fun close() {
            caches.dispose()
            visitors.forEach { it.remove() }; owner.remove()
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
            otherInstances.forEach { MinecraftServer.getInstanceManager().unregisterInstance(it) }
        }
    }
    private class MemoryConnection : PlayerConnection() {
        override fun sendPacket(packet: SendablePacket) = Unit
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }
}
