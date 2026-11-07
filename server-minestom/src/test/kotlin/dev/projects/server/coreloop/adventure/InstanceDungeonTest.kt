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
            assertEquals(listOf(RoomKind.WAVES, RoomKind.MINIBOSS, RoomKind.WAVES, RoomKind.SPAWNER, RoomKind.SPAWNER, RoomKind.SPAWNER, RoomKind.MINIBOSS),
                layout.rooms.map { it.kind })
            assertTrue(QuestCombatPlacement.clear(instance, layout.spawn))
            assertTrue(QuestCombatPlacement.clear(instance, layout.bossSpawn))
            assertEquals(listOf(true) + List(layout.rooms.size) { false }, layout.gates.map { it.isOpen(instance) })
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
    fun `each kind of room holds its gate shut until it is cleared and walking into the glade wakes 桜牙獣`() {
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
        fun mobs() = combat.entities().filter { !combat.isBoss(it.uuid) }
        try {
            run.tick()
            assertEquals(DungeonRun.Stage.WALKING, run.stage)
            assertEquals(4, mobs().size, "The entrance pack (3 + one player) did not rise")
            mobs().forEach { combat.applyEffectDamage(it.uuid, player, 9999.0) }
            for ((index, room) in layout.rooms.withIndex()) {
                player.teleport(room.entry).join()
                run.tick()
                assertEquals(DungeonRun.Stage.FIGHTING, run.stage, room.name)
                assertFalse(layout.gates[index].isOpen(instance), "The door stays open behind ${room.name}")
                when (room.kind) {
                    RoomKind.MINIBOSS -> assertEquals(listOf(room.waves[0][0]), mobs().map { combat.mobInfo(it.uuid)?.archetype }, room.name)
                    RoomKind.SPAWNER -> { repeat(room.spawner!!.cap(1)) { run.tick() }; assertEquals(room.spawner.cap(1), mobs().size, "${room.name} does not fill to its cap") }
                    RoomKind.WAVES -> assertEquals(room.waves[0].size, mobs().size)
                }
                assertTrue(mobs().all { room.holds(it.position) }, "${room.name}: an enemy rose outside the room")
                // Fight it out: everything that stands falls, a tick at a time, until the room is done.
                var kills = 0
                var guard = 0
                while (run.stage == DungeonRun.Stage.FIGHTING && guard++ < 20_000) {
                    assertFalse(layout.gates[index + 1].isOpen(instance), "${room.name} let the party past before it was cleared")
                    mobs().forEach { if (combat.applyEffectDamage(it.uuid, player, 9999.0)) kills++ }
                    run.tick()
                }
                assertEquals(DungeonRun.Stage.WALKING, run.stage, "${room.name} never cleared")
                assertTrue(layout.gates[index + 1].isOpen(instance), "${room.name} did not open the next door")
                when (room.kind) {
                    RoomKind.SPAWNER -> assertEquals(room.spawner!!.target(1) + 1, kills, "${room.name}: the fallen and the last")
                    else -> assertEquals(room.waves.sumOf { it.size }, kills, room.name)
                }
            }
            // The shrine's and the cave's seals, and the two before the gatehouse, are lit.
            assertTrue(layout.rooms.sumOf { it.seals.size } >= 2)
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

    @Test
    fun `a 穢れ場's counts grow with the party`() {
        assertEquals(8 to 4, InstanceDungeonFactory.perPlayers("8 + 4 * 人数"))
        assertEquals(3 to 1, InstanceDungeonFactory.perPlayers("3 + 人数"))
        assertEquals(6 to 0, InstanceDungeonFactory.perPlayers("6"))
    }

    private class MemoryConnection : PlayerConnection() {
        override fun sendPacket(packet: SendablePacket) = Unit
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }
}
