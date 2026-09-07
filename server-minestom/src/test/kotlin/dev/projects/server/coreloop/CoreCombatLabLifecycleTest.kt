package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Native server actors only. Never connects, moves or clicks the user's Minecraft client. */
class CoreCombatLabLifecycleTest {
    @Test fun `real random map allows skills and menu then releases map and temporary actor on return`() = player { p, hub ->
        val executor = Executors.newSingleThreadExecutor()
        var restores = 0
        val lab = CoreCombatLab(executor, { true }, { false }, {},
            { it.setInstance(hub, Pos(8.5, 40.0, 8.5)).thenApply { true } }, { restores++ }, { 1_788_168_623_401L })
        try {
            lab.enter(p, CoreClass.ASSASSIN)
            pumpUntil { lab.ready(p) }
            val map = p.instance
            assertNotSame(hub, map)
            assertEquals(CoreClass.ASSASSIN, lab.actor(p)?.classId)
            assertNull(lab.account(p)?.activeRun)
            val actor = assertNotNull(lab.actor(p))
            val combat = assertNotNull(lab.combat(p))
            val target = combat.combatTargets().first()
            val at = assertNotNull(combat.positionOf(target.id))
            p.teleport(at.sub(0.0, 0.0, 2.0).withView(0f, 0f)).get(10, TimeUnit.SECONDS)
            assertTrue(greatswordInRange(p.position, p.position.direction(), target, 3.0))
            actor.reset(); actor.attack()
            repeat(15) { actor.tick() }
            assertTrue(actor.resource > 0, "Stationary target rejected damage: player=${p.position}, target=$at, hp=${combat.mobInfo(target.id)}, inventory=${p.openInventory}, job=${actor.classId}")
            actor.reset(); lab.beforeTick(p)
            actor.skill(3); actor.tick()
            assertTrue(actor.activeVisualEffects > 0)
            lab.menu(p)
            assertNotNull(p.openInventory)
            assertEquals(70, CoreSkillCatalog.artNames.size)
            assertTrue(lab.leave(p))
            pumpUntil { !lab.contains(p) }
            assertSame(hub, p.instance)
            assertNull(lab.actor(p))
            assertNull(lab.account(p))
            assertEquals(1, restores)
            assertFalse(MinecraftServer.getInstanceManager().instances.contains(map))
        } finally {
            if (lab.contains(p)) { lab.leave(p); pumpUntil { !lab.contains(p) } }
            executor.shutdownNow(); lab.close()
        }
    }

    @Test fun `leaving while queued cancels generation and duplicate entry cannot queue another map`() = player { p, _ ->
        val jobs = mutableListOf<Runnable>()
        var resets = 0
        val lab = CoreCombatLab(java.util.concurrent.Executor { jobs += it }, { true }, { false }, { resets++ },
            { CompletableFuture.completedFuture(true) }, {}, { error("Cancelled entry must never generate a map") })
        lab.enter(p, CoreClass.WARRIOR); lab.enter(p, CoreClass.MAGE)
        assertEquals(1, jobs.size)
        assertEquals(1, resets)
        assertTrue(lab.leave(p))
        pumpUntil { !lab.contains(p) }
        jobs.single().run()
        repeat(3) { MinecraftServer.getSchedulerManager().processTick() }
        assertFalse(lab.contains(p))
        assertNull(lab.actor(p))
        lab.close()
    }

    private fun pumpUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
        while (!condition() && System.nanoTime() < deadline) {
            MinecraftServer.getSchedulerManager().processTick()
            Thread.sleep(5)
        }
        assertTrue(condition(), "Laboratory lifecycle did not complete")
    }

    private fun player(action: (Player, net.minestom.server.instance.InstanceContainer) -> Unit) {
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
        val p = Player(connection, GameProfile(UUID.randomUUID(), "SkillLabTest"))
        connection.player = p; p.gameMode = GameMode.ADVENTURE
        p.setInstance(hub, Pos(8.5, 40.0, 8.5)).get(10, TimeUnit.SECONDS)
        try { action(p, hub) } finally {
            p.remove(); MinecraftServer.getInstanceManager().unregisterInstance(hub)
        }
    }
}
