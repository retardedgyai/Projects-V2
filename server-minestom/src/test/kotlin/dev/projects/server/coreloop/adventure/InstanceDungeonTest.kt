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
import net.minestom.server.instance.block.Block
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
    /**
     * Where a player can walk from [from] (the walk check, in the game's own blocks): up one block, down three, two
     * free blocks to stand in. Barriers, walls and closed gates stop it.
     */
    private class Walk(val stand: Map<Pair<Int, Int>, Int>, val from: Map<Pair<Int, Int>, Pair<Int, Int>>) {
        fun near(p: Pos) = (-2..2).any { dx -> (-2..2).any { dz -> (p.blockX() + dx to p.blockZ() + dz) in stand } }
        /** The way there, every 12th step, to say where a wall is missing. */
        fun way(p: Pos): String {
            var at = (-2..2).flatMap { dx -> (-2..2).map { dz -> p.blockX() + dx to p.blockZ() + dz } }.firstOrNull { it in stand } ?: return "-"
            val steps = mutableListOf(at)
            while (true) { at = from[at] ?: break; steps += at }
            return steps.reversed().filterIndexed { i, _ -> i % 12 == 0 }.joinToString(" ") { "(${it.first},${stand[it]},${it.second})" }
        }
    }

    private fun reachable(instance: InstanceContainer, start: Pos): Walk {
        fun open(x: Int, y: Int, z: Int) = !instance.getBlock(x, y, z).solid() && !instance.getBlock(x, y, z).liquid()
        // Fences and walls (1.5 high) are not stood on: a player cannot step up onto them.
        fun stands(x: Int, y: Int, z: Int) = instance.getBlock(x, y - 1, z).let { it.solid() && it.registry().collisionShape().relativeEnd().y() <= 1.0 } &&
            open(x, y, z) && open(x, y + 1, z)
        val stand = hashMapOf((start.blockX() to start.blockZ()) to start.blockY())
        val from = hashMapOf<Pair<Int, Int>, Pair<Int, Int>>()
        val queue = ArrayDeque(listOf(Triple(start.blockX(), start.blockY(), start.blockZ())))
        while (queue.isNotEmpty()) {
            val (x, y, z) = queue.removeFirst()
            for ((dx, dz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val nx = x + dx; val nz = z + dz
                if (nx !in -112..111 || nz !in -112..111 || (nx to nz) in stand) continue
                val ny = (1 downTo -3).map { y + it }.firstOrNull { stands(nx, it, nz) && (it <= y || open(x, y + 2, z)) } ?: continue
                stand[nx to nz] = ny; from[nx to nz] = x to z; queue += Triple(nx, ny, nz)
            }
        }
        return Walk(stand, from)
    }

    @Test
    fun `the map leads from the entrance through each room into the glade only as each gate opens`() {
        MinecraftServer.init(Auth.Offline())
        val dungeon = InstanceDungeonFactory.create(1)
        val instance = dungeon.arena.instance
        val layout = dungeon.layout
        try {
            assertEquals(4, layout.rooms.size)
            assertTrue(QuestCombatPlacement.clear(instance, layout.spawn))
            assertTrue(QuestCombatPlacement.clear(instance, layout.bossSpawn))
            assertEquals(listOf(true, false, false, false, false), layout.gates.map { it.isOpen(instance) })
            for (i in layout.rooms.indices) {
                val open = reachable(instance, layout.spawn)
                assertTrue(open.near(layout.rooms[i].center), "${layout.rooms[i].name} cannot be reached with its gate open")
                layout.rooms.drop(i + 1).forEach { assertFalse(open.near(it.center), "${it.name} leaks past the gate before ${layout.rooms[i].name}: ${open.way(it.center)}") }
                assertFalse(open.near(layout.bossSpawn), "The glade leaks into ${layout.rooms[i].name}: ${open.way(layout.bossSpawn)}")
                layout.gates[i + 1].set(instance, open = true)
            }
            assertTrue(reachable(instance, layout.spawn).near(layout.bossSpawn))
            // Every wave can be placed inside its room; whoever lagged behind is brought in on floor.
            for (room in layout.rooms) {
                assertTrue(room.holds(room.entry, margin = 2.5), "${room.name}: the entry does not start the waves")
                assertTrue(QuestCombatPlacement.clear(instance, room.entry), "${room.name}: the entry is in a wall")
            }
            assertTrue(layout.inArena(layout.arenaEntry) && QuestCombatPlacement.clear(instance, layout.arenaEntry))
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
                player.teleport(room.entry).join()
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
            // 古い祠の森 and 滝裏の洞 lit their seals and the two before the gatehouse.
            assertEquals(4, layout.rooms.sumOf { it.seals.size })
            layout.rooms.flatMap { it.seals }.forEach { assertEquals(Block.LANTERN.name(), instance.getBlock(it).name()) }
            assertFalse(run.bossEngaged)
            player.teleport(layout.arenaEntry).join()
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
