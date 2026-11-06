package dev.projects.server.coreloop.adventure

import dev.projects.server.coreloop.QuestCombatPlacement
import dev.projects.server.mob.QuestEncounterCombat
import dev.projects.server.mob.QuestMobArchetype
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.ArrayDeque
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstanceDungeonTest {
    /** Cells a player can stand in at y 40, flooded from [from] (gates block as they are set). */
    private fun reachable(instance: InstanceContainer, from: Pos): Set<Pair<Int, Int>> {
        val seen = hashSetOf(from.blockX() to from.blockZ())
        val queue = ArrayDeque(seen)
        while (queue.isNotEmpty()) {
            val (x, z) = queue.removeFirst()
            for ((dx, dz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val next = x + dx to z + dz
                if (next.first !in -16..95 || next.second !in -48..95 || next in seen) continue
                if (!instance.getBlock(next.first, 39, next.second).solid() || instance.getBlock(next.first, 40, next.second).solid() ||
                    instance.getBlock(next.first, 41, next.second).solid()) continue
                seen += next; queue += next
            }
        }
        return seen
    }

    @Test
    fun `the building leads from the hall through both rooms into the glade only as each gate opens`() {
        MinecraftServer.init(Auth.Offline())
        val dungeon = InstanceDungeonFactory.create(1)
        val instance = dungeon.arena.instance
        val layout = dungeon.layout
        try {
            assertTrue(QuestCombatPlacement.clear(instance, layout.spawn))
            assertTrue(QuestCombatPlacement.clear(instance, layout.bossSpawn))
            assertEquals(listOf(true, false, false), layout.gates.map { it.isOpen(instance) })
            fun cell(p: Pos) = p.blockX() to p.blockZ()
            var open = reachable(instance, layout.spawn)
            assertTrue(cell(layout.rooms[0].center) in open)
            assertFalse(cell(layout.rooms[1].center) in open)
            layout.gates[1].set(instance, open = true)
            open = reachable(instance, layout.spawn)
            assertTrue(cell(layout.rooms[1].center) in open)
            assertFalse(cell(layout.bossSpawn) in open, "The glade leaks into the rooms")
            layout.arenaGate.set(instance, open = true)
            assertTrue(cell(layout.bossSpawn) in reachable(instance, layout.spawn))
            // Every wave can be placed where the room says.
            for (room in layout.rooms) for (dx in listOf(-4.0, 0.0, 4.0)) for (dz in listOf(3.0, 5.0, 7.0))
                assertTrue(room.holds(QuestCombatPlacement.resolve(instance, room.center.add(dx, 0.0, dz))), "${room.name} spawn outside")
        } finally { dungeon.arena.dispose(); assertFalse(instance.isRegistered) }
    }

    @Test
    fun `waves open the doors one room at a time and walking into the glade shuts it and wakes 桜牙獣`() {
        MinecraftServer.init(Auth.Offline())
        val dungeon = InstanceDungeonFactory.create(1)
        val instance = dungeon.arena.instance
        val layout = dungeon.layout
        val connection = MemoryConnection().apply { setClientState(ConnectionState.PLAY); setServerState(ConnectionState.PLAY) }
        val player = Player(connection, GameProfile(UUID.randomUUID(), "DungeonTest"))
        connection.player = player
        player.gameMode = GameMode.ADVENTURE
        player.setInstance(instance, layout.spawn).join()
        val combat = QuestEncounterCombat(instance, 1, emptyList(), layout.bossSpawn,
            { _, _ -> }, { _, _ -> }, explicitBossArchetype = QuestMobArchetype.SAKURA_FANG)
        val run = DungeonRun(instance, combat, layout) { listOf(player) }
        try {
            run.tick()
            assertEquals(DungeonRun.Stage.WALKING, run.stage)
            for ((index, room) in layout.rooms.withIndex()) {
                player.teleport(room.center).join()
                run.tick()
                assertEquals(DungeonRun.Stage.FIGHTING, run.stage)
                assertFalse(layout.gates[index].isOpen(instance), "The door stays open behind ${room.name}")
                for (wave in room.waves.indices) {
                    assertEquals(wave, run.wave)
                    val mobs = combat.entities().filter { !combat.isBoss(it.uuid) }
                    assertEquals(room.waves[wave].size, mobs.size)
                    assertTrue(mobs.all { room.holds(it.position) })
                    assertFalse(layout.gates[index + 1].isOpen(instance))
                    mobs.forEach { assertTrue(combat.applyEffectDamage(it.uuid, player, 9999.0)) }
                    run.tick()
                }
                assertEquals(DungeonRun.Stage.WALKING, run.stage)
                assertTrue(layout.gates[index + 1].isOpen(instance), "${room.name} did not open the next door")
            }
            assertFalse(run.bossEngaged)
            player.teleport(layout.arenaCenter.add(0.0, 0.0, -10.0)).join()
            run.tick()
            assertTrue(run.bossEngaged)
            assertFalse(layout.arenaGate.isOpen(instance))
            assertEquals(1, combat.entities().count { combat.isBoss(it.uuid) })
            run.dispose()
        } finally {
            combat.dispose()
            player.remove()
            dungeon.arena.dispose()
        }
    }

    private class MemoryConnection : PlayerConnection() {
        override fun sendPacket(packet: SendablePacket) = Unit
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }
}
