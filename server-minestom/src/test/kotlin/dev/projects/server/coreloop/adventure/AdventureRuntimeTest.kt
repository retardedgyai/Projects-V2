package dev.projects.server.coreloop.adventure

import dev.projects.server.mob.QuestEncounterCombat
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdventureRuntimeTest {
    @Test
    fun `rift requires three separate starts and only final clear settles once`() {
        val progress = AdventureProgress(AdventureKind.RIFT)
        repeat(2) {
            assertTrue(progress.start())
            assertFalse(progress.start())
            assertNull(progress.clearWave())
            assertEquals(AdventurePhase.TRAVEL, progress.phase)
        }
        assertTrue(progress.start())
        assertEquals(3, progress.clearWave())
        assertNull(progress.clearWave())
        assertFalse(progress.start())
        assertNull(progress.claim())
    }

    @Test
    fun `ritual bank scales one three six but failure forfeits unbanked reward`() {
        for (completed in 1..3) {
            val progress = AdventureProgress(AdventureKind.RITUAL)
            repeat(completed) { progress.start(); progress.clearWave() }
            assertEquals(listOf(1, 3, 6)[completed - 1], progress.availableReward)
            if (completed < 3) assertEquals(progress.availableReward, progress.claim())
            assertEquals(AdventurePhase.COMPLETED, progress.phase)
            assertNull(progress.claim())
        }
        val failed = AdventureProgress(AdventureKind.RITUAL)
        failed.start(); failed.clearWave(); failed.start(); failed.fail()
        assertEquals(AdventurePhase.FAILED, failed.phase)
        assertNull(failed.claim())
        assertNull(failed.clearWave())
        assertFalse(failed.start())
    }

    @Test
    fun `unrelated entity or distant click cannot start an activity`() = world(AdventureKind.RITUAL) { h ->
        assertFalse(h.runtime.interact(h.player, Entity(EntityType.INTERACTION), 0))
        h.player.teleport(Pos(30.5, 40.0, 8.5)).join()
        assertTrue(h.runtime.interact(h.player, h.marker(), 0))
        assertEquals(AdventurePhase.READY, h.runtime.snapshots().single().phase)
        assertTrue(h.combat.entities().none { !h.combat.isBoss(it.uuid) })
    }

    @Test
    fun `rift travels across three plazas spawns real waves and rewards only once`() = world(AdventureKind.RIFT) { h ->
        repeat(3) { wave ->
            h.player.teleport(h.runtime.snapshots().single().position).join()
            h.runtime.interact(h.player, h.marker(), wave * 1000L)
            assertEquals(if (wave == 2) 4 else 3, h.runtime.snapshots().single().aliveEnemies)
            h.clearEnemies()
            h.runtime.tick(wave * 1000L + 100)
        }
        assertEquals(listOf(AdventureReward(AdventureKind.RIFT, "test-event", 3)), h.rewards)
        h.runtime.interact(h.player, h.marker(), 4000)
        h.runtime.tick(10_000)
        assertEquals(1, h.rewards.size)
        assertEquals(AdventurePhase.COMPLETED, h.runtime.snapshots().single().phase)
    }

    @Test
    fun `ritual requires explicit targeted sneak click to bank after wave`() = world(AdventureKind.RITUAL) { h ->
        h.runtime.interact(h.player, h.marker(), 0)
        h.clearEnemies()
        h.runtime.tick(100)
        assertEquals(AdventurePhase.DECISION, h.runtime.snapshots().single().phase)
        assertTrue(h.rewards.isEmpty())
        h.player.isSneaking = true
        h.runtime.interact(h.player, h.marker(), 200)
        assertEquals(listOf(AdventureReward(AdventureKind.RITUAL, "test-event", 1)), h.rewards)
        h.runtime.interact(h.player, h.marker(), 201)
        assertEquals(1, h.rewards.size)
    }

    @Test
    fun `leaving ritual for five seconds fails without reward and removes wave enemies`() = world(AdventureKind.RITUAL) { h ->
        h.runtime.interact(h.player, h.marker(), 0)
        h.player.teleport(Pos(20.0, 40.0, 8.5)).join()
        h.runtime.tick(100)
        h.runtime.tick(5100)
        assertEquals(AdventurePhase.FAILED, h.runtime.snapshots().single().phase)
        assertTrue(h.rewards.isEmpty())
        assertTrue(h.combat.entities().none { !h.combat.isBoss(it.uuid) })
    }

    @Test
    fun `wave deadline and disposed map cannot spawn or reward later`() = world(AdventureKind.RITUAL) { h ->
        h.runtime.interact(h.player, h.marker(), 0)
        h.runtime.tick(90_000)
        assertEquals(AdventurePhase.FAILED, h.runtime.snapshots().single().phase)
        val markers = h.runtime.markerEntities()
        h.runtime.dispose()
        h.runtime.dispose()
        h.runtime.tick(100_000)
        assertTrue(markers.all { it.isRemoved })
        assertTrue(h.runtime.markerEntities().isEmpty())
        assertTrue(h.rewards.isEmpty())
    }

    private fun world(kind: AdventureKind, test: (Harness) -> Unit) {
        MinecraftServer.init(Auth.Offline())
        Harness(kind).use(test)
    }
    private class Harness(kind: AdventureKind) : AutoCloseable {
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        val player: Player
        val combat: QuestEncounterCombat
        val runtime: AdventureRuntime
        val rewards = mutableListOf<AdventureReward>()
        init {
            instance.viewDistance(2)
            instance.setGenerator { it.modifier().fillHeight(0, 40, Block.STONE) }
            for (x in -3..5) for (z in -3..5) instance.loadChunk(x, z).get(10, TimeUnit.SECONDS)
            val connection = MemoryConnection()
            connection.setClientState(ConnectionState.PLAY)
            connection.setServerState(ConnectionState.PLAY)
            player = Player(connection, GameProfile(UUID.randomUUID(), "AdventureTest"))
            connection.player = player
            player.gameMode = GameMode.ADVENTURE
            player.setInstance(instance, Pos(8.5, 40.0, 8.5)).get(10, TimeUnit.SECONDS)
            combat = QuestEncounterCombat(instance, 1, emptyList(), Pos(50.5, 40.0, 50.5), { _, _ -> }, { _, _ -> })
            val centers = if (kind == AdventureKind.RIFT) listOf(Pos(8.5, 40.0, 8.5), Pos(16.5, 40.0, 8.5), Pos(24.5, 40.0, 8.5))
                else listOf(Pos(8.5, 40.0, 8.5))
            runtime = AdventureRuntime(instance, combat, listOf(AdventureSite(kind, "test-event", centers)), { true }, rewards::add)
        }
        fun marker(): Entity = runtime.markerEntities().single()
        fun clearEnemies() {
            combat.entities().filter { !combat.isBoss(it.uuid) }.forEach { enemy ->
                player.teleport(enemy.position.sub(0.0, 0.0, 1.5)).join()
                assertTrue(combat.applyEffectDamage(enemy.uuid, player, 9999.0))
            }
            player.teleport(runtime.snapshots().single().position).join()
        }
        override fun close() {
            runtime.dispose(); combat.dispose(); player.remove()
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
        }
    }
    private class MemoryConnection : PlayerConnection() {
        override fun sendPacket(packet: SendablePacket) = Unit
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }
}
