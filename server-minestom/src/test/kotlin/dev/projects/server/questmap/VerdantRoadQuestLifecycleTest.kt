package dev.projects.server.questmap

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Native server actors only: the quest map must always release its instance, whatever the player does. */
class VerdantRoadQuestLifecycleTest {
    @Test
    fun `close is idempotent and unregisters an empty map immediately`() {
        MinecraftServer.init(Auth.Offline())
        val runtime = prepare()
        runtime.close()
        assertTrue(runtime.isClosed)
        assertFalse(runtime.instance.isRegistered)
        runtime.close()
        assertTrue(runtime.isClosed)
    }

    @Test
    fun `close evicts a player who never leaves instead of leaking the map`() = player { p, _, _ ->
        val runtime = prepare()
        p.setInstance(runtime.instance, runtime.spawn).get(10, TimeUnit.SECONDS)
        runtime.close()
        assertFalse(runtime.isClosed, "An occupied map must wait for outgoing transfers first")
        repeat(VerdantRoadQuestRuntime.CLOSE_EVICT_AFTER_TICKS + 2) { MinecraftServer.getSchedulerManager().processTick() }
        assertTrue(runtime.isClosed)
        assertFalse(runtime.instance.isRegistered)
        assertTrue(runtime.instance.players.isEmpty())
    }

    @Test
    fun `disconnect while generating refuses entry and releases the prepared map`() = player { p, connection, hub ->
        val service = service(hub)
        val runtime = prepare()
        connection.disconnect()
        assertEquals(false, service.enterPrepared(p, runtime).get(10, TimeUnit.SECONDS))
        assertNull(service.currentRuntime(p.uuid))
        assertFalse(runtime.instance.isRegistered)
    }

    @Test
    fun `disconnect during the transfer into the map still releases it`() = player { p, connection, hub ->
        val service = service(hub)
        val runtime = prepare()
        // Disconnect from inside the transfer, after the player is placed but before its future settles.
        val listener = EventNode.all("quest-map-lifecycle-test").addListener(PlayerSpawnEvent::class.java) { event ->
            if (event.player === p && event.spawnInstance === runtime.instance) {
                connection.disconnect()
                service.disconnect(p.uuid)
            }
        }
        MinecraftServer.getGlobalEventHandler().addChild(listener)
        val entered = try {
            service.enterPrepared(p, runtime)
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(listener)
        }
        assertFalse(p.isOnline, "The spawn listener did not run inside the transfer")
        pumpUntil { entered.isDone && !runtime.instance.isRegistered }
        assertEquals(false, entered.get())
        assertNull(service.currentRuntime(p.uuid))
        assertTrue(runtime.isClosed)
        assertTrue(runtime.instance.players.isEmpty())
    }

    @Test
    fun `disconnect inside the map releases it after the player entity is gone`() = player { p, connection, hub ->
        val service = service(hub)
        val runtime = prepare()
        assertEquals(true, service.enterPrepared(p, runtime).get(10, TimeUnit.SECONDS))
        assertEquals(runtime, service.currentRuntime(p.uuid))
        connection.disconnect()
        service.disconnect(p.uuid)
        pumpUntil { !runtime.instance.isRegistered }
        assertNull(service.currentRuntime(p.uuid))
    }

    @Test
    fun `return to hub releases the map`() = player { p, _, hub ->
        val service = service(hub)
        val runtime = prepare()
        assertEquals(true, service.enterPrepared(p, runtime).get(10, TimeUnit.SECONDS))
        assertEquals(true, service.returnToHub(p).get(10, TimeUnit.SECONDS))
        assertEquals(hub, p.instance)
        assertFalse(runtime.instance.isRegistered)
        assertNull(service.currentRuntime(p.uuid))
    }

    @Test
    fun `resource respawn scans are throttled per runtime`() {
        MinecraftServer.init(Auth.Offline())
        val runtime = prepare()
        try {
            val node = runtime.gatheringNodes.first {
                runtime.gatheringObjects.getValue(it.id).visualKind == QuestMapStructureAssets.GatheringVisualKind.SCHEMATIC
            }
            assertTrue(runtime.tryDepleteGatheringNode(node, 0L))
            runtime.respawnGatheringNodes(89_500L)
            runtime.respawnGatheringNodes(90_000L) // Ready, but inside the one-second throttle window.
            assertFalse(runtime.isGatheringNodeAvailable(node, 0L))
            runtime.respawnGatheringNodes(90_500L)
            assertTrue(runtime.isGatheringNodeAvailable(node, 0L))
            assertNotNull(runtime.gatheringLabelFor(node))
        } finally {
            runtime.close()
        }
    }

    private fun prepare(): VerdantRoadQuestRuntime =
        VerdantRoadQuestRuntime.prepare(SEED, candidateCount = 1).get(45, TimeUnit.SECONDS)

    private fun service(hub: InstanceContainer) = VerdantRoadQuestService(
        hub,
        HUB_SPAWN,
        QuestGatheringMasteryRepository(Files.createTempDirectory("gathering-mastery")),
        respawnResources = false,
        technicalMessages = false,
    )

    private fun pumpUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!condition() && System.nanoTime() < deadline) {
            MinecraftServer.getSchedulerManager().processTick()
            Thread.sleep(5)
        }
        assertTrue(condition(), "Quest map lifecycle did not complete")
    }

    private fun player(action: (Player, PlayerConnection, InstanceContainer) -> Unit) {
        MinecraftServer.init(Auth.Offline())
        val hub = MinecraftServer.getInstanceManager().createInstanceContainer()
        hub.viewDistance(2)
        hub.setGenerator { it.modifier().fillHeight(0, 40, Block.STONE) }
        for (x in -3..3) for (z in -3..3) hub.loadChunk(x, z).get(10, TimeUnit.SECONDS)
        val connection = object : PlayerConnection() {
            override fun sendPacket(packet: SendablePacket) = Unit
            override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
        }
        connection.setClientState(ConnectionState.PLAY); connection.setServerState(ConnectionState.PLAY)
        val p = Player(connection, GameProfile(UUID.randomUUID(), "QuestMapLife"))
        connection.player = p; p.gameMode = GameMode.ADVENTURE
        p.setInstance(hub, HUB_SPAWN).get(10, TimeUnit.SECONDS)
        try { action(p, connection, hub) } finally {
            p.remove(); MinecraftServer.getInstanceManager().unregisterInstance(hub)
        }
    }

    private companion object {
        const val SEED = 1_788_168_623_401L
        val HUB_SPAWN = Pos(8.5, 40.0, 8.5)
    }
}
