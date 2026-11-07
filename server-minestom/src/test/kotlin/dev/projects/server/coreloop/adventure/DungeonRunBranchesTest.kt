package dev.projects.server.coreloop.adventure

import dev.projects.server.coreloop.CoreDungeons
import dev.projects.server.mob.QuestEncounterCombat
import dev.projects.server.mob.QuestMobArchetype
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * DungeonRun's branches the walk-through in InstanceDungeonTest does not take (docs/development/dungeon-test-coverage.md):
 * nobody standing (dead or gone), a second player left behind or fallen, the glade before the rooms are done, a 穢れ場's
 * last enemy, the higher 段階, and what dispose leaves. Each test builds one dungeon; arenas are not built in a loop.
 */
class DungeonRunBranchesTest {
    private class MemoryConnection : PlayerConnection() {
        override fun sendPacket(packet: SendablePacket) = Unit
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }

    private fun player(name: String): Player {
        val connection = MemoryConnection().apply { setClientState(ConnectionState.PLAY); setServerState(ConnectionState.PLAY) }
        return Player(connection, GameProfile(UUID.randomUUID(), name)).also { connection.player = it; it.gameMode = GameMode.ADVENTURE }
    }

    private class Scene(val dungeon: InstanceDungeon, val combat: QuestEncounterCombat) {
        val layout get() = dungeon.layout
        val instance get() = dungeon.arena.instance
        fun mobs() = combat.entities().filter { !combat.isBoss(it.uuid) }
        fun killAll(by: Player) = mobs().count { combat.applyEffectDamage(it.uuid, by, 9999.0) }
    }

    private fun scene(block: Scene.() -> Unit) {
        MinecraftServer.init(Auth.Offline())
        val dungeon = InstanceDungeonFactory.create(1)
        val combat = QuestEncounterCombat(dungeon.arena.instance, 1, emptyList(), dungeon.layout.bossSpawn,
            { _, _ -> }, { _, _ -> }, explicitBossArchetype = QuestMobArchetype.SAKURA_FANG)
        try { Scene(dungeon, combat).block() } finally {
            combat.dispose()
            dungeon.arena.instance.players.toList().forEach { it.remove() }
            dungeon.arena.dispose()
        }
    }

    @Test fun `with nobody standing the run waits, and the entrance pack rises only once someone is back`() = scene {
        val hero = player("Alone").also { it.setInstance(instance, layout.spawn).join() }
        var standing = emptyList<Player>()     // CoreLoopServer leaves out a defeated or returning player
        val run = DungeonRun(instance, combat, layout) { standing }
        hero.teleport(layout.rooms[0].entry).join()
        repeat(40) { run.tick() }
        assertEquals(DungeonRun.Stage.WALKING, run.stage, "a room started with nobody standing")
        assertEquals(0, mobs().size, "enemies rose for nobody")
        assertTrue(layout.gates[0].isOpen(instance))
        standing = listOf(hero)
        run.tick()
        assertEquals(4, mobs().size - layout.rooms[0].waves[0].size, "the pack (3 + 1 player) and the room's first wave")
        assertEquals(DungeonRun.Stage.FIGHTING, run.stage)
        // Fallen mid-room: the wave stays, the gate stays shut, nothing more rises until someone stands again.
        val risen = mobs().size
        standing = emptyList()
        repeat(40) { run.tick() }
        assertEquals(risen, mobs().size)
        assertEquals(DungeonRun.Stage.FIGHTING, run.stage)
        assertFalse(layout.gates[0].isOpen(instance))
        assertFalse(layout.gates[1].isOpen(instance))
    }

    @Test fun `a party member left behind is brought into the room, and one who falls leaves the room to the rest`() = scene {
        val first = player("Ahead").also { it.setInstance(instance, layout.spawn).join() }
        val second = player("Behind").also { it.setInstance(instance, layout.spawn).join() }
        var standing = listOf(first, second)
        val run = DungeonRun(instance, combat, layout) { standing }
        run.tick(); killAll(first); run.tick()
        val room = layout.rooms[0]
        assertEquals(RoomKind.WAVES, room.kind)
        first.teleport(room.entry).join()
        assertFalse(room.holds(second.position))
        run.tick()
        assertEquals(DungeonRun.Stage.FIGHTING, run.stage)
        assertTrue(room.holds(second.position), "the one left behind was not brought through the gate")
        assertFalse(layout.gates[0].isOpen(instance), "the gate stays open behind the party")
        // One falls; the other clears the waves one after another: the next wave only once the last is down.
        standing = listOf(first)
        assertEquals(0, run.wave)
        val firstWave = mobs().size
        mobs().drop(1).forEach { combat.applyEffectDamage(it.uuid, first, 9999.0) }
        run.tick()
        assertEquals(0, run.wave, "the next wave came while one of this wave still stood")
        assertEquals(1, mobs().size)
        killAll(first); run.tick()
        assertEquals(1, run.wave)
        assertEquals(room.waves[1].size, mobs().size)
        assertTrue(firstWave >= room.waves[0].size)
        killAll(first); run.tick()
        assertEquals(DungeonRun.Stage.WALKING, run.stage)
        assertEquals(1, run.room)
        assertTrue(layout.gates[1].isOpen(instance))
    }

    @Test fun `the glade does not wake 桜牙獣 before the rooms are cleared`() = scene {
        val hero = player("Skipper").also { it.setInstance(instance, layout.spawn).join() }
        val run = DungeonRun(instance, combat, layout) { listOf(hero) }
        run.tick(); killAll(hero)
        hero.teleport(layout.arenaEntry).join()
        repeat(20) { run.tick() }
        assertFalse(run.bossEngaged)
        assertEquals(DungeonRun.Stage.WALKING, run.stage)
        assertEquals(0, run.room)
        // 桜牙獣 stands in the glade from the start (QuestEncounterCombat); only the run's stage says it is awake.
        assertFalse(layout.arenaGate.isOpen(instance), "the glade's gate opened before the rooms were cleared")
    }

    @Test fun `a 穢れ場 skipped to its end raises ELITE_BRUTE last, and dispose clears every enemy`() = scene {
        val hero = player("Purifier").also { it.setInstance(instance, layout.spawn).join() }
        val run = DungeonRun(instance, combat, layout, step = 2) { listOf(hero) }
        run.tick(); killAll(hero)
        // Walk the rooms before the first 穢れ場 (as InstanceDungeonTest does).
        val spawner = layout.rooms.indexOfFirst { it.kind == RoomKind.SPAWNER }
        for (i in 0 until spawner) {
            hero.teleport(layout.rooms[i].entry).join(); run.tick()
            var guard = 0
            while (run.stage == DungeonRun.Stage.FIGHTING && guard++ < 5_000) { killAll(hero); run.tick() }
        }
        val room = layout.rooms[spawner]
        hero.teleport(room.entry).join(); run.tick()
        assertEquals(DungeonRun.Stage.FIGHTING, run.stage)
        assertEquals(room.spawner!!.target(1) + 3, run.spawnerTarget(room, 1), "段階2 asks three more")
        repeat(room.spawner.cap(1)) { run.tick() }
        assertTrue(mobs().isNotEmpty())
        run.skipToLast()
        killAll(hero); run.tick()            // the fallen are counted, the room is empty: the last one rises
        run.tick()
        assertEquals(listOf(QuestMobArchetype.ELITE_BRUTE), mobs().map { combat.mobInfo(it.uuid)?.archetype })
        assertTrue(layout.gates[spawner].let { !it.isOpen(instance) } && !layout.gates[spawner + 1].isOpen(instance))
        killAll(hero); run.tick()
        assertEquals(DungeonRun.Stage.WALKING, run.stage)
        assertEquals(spawner + 1, run.room)
        assertTrue(layout.gates[spawner + 1].isOpen(instance))
        // Into the next room, then home mid-fight: dispose takes every enemy the run raised.
        hero.teleport(layout.rooms[spawner + 1].entry).join(); repeat(3) { run.tick() }
        assertTrue(mobs().isNotEmpty())
        run.dispose()
        assertEquals(0, mobs().size, "enemies the run raised outlived it")
    }

    @Test fun `skipToLast does nothing outside a 穢れ場's fight`() = scene {
        val hero = player("Idle").also { it.setInstance(instance, layout.spawn).join() }
        val run = DungeonRun(instance, combat, layout) { listOf(hero) }
        run.skipToLast()
        run.tick(); killAll(hero)
        hero.teleport(layout.rooms[0].entry).join(); run.tick()
        val wave = mobs().size
        run.skipToLast()
        run.tick()
        assertEquals(wave, mobs().size)
        assertEquals(0, run.wave)
    }

    @Test fun `the higher 段階 stop growing where they should`() = scene {
        for (step in 1..CoreDungeons.MAX_STAGE) {
            val run = DungeonRun(instance, combat, layout, step) { emptyList() }
            for (room in layout.rooms) when (room.kind) {
                RoomKind.WAVES -> room.waves.indices.forEach { w ->
                    val wave = run.waveAt(room, w)
                    assertEquals(if (step == 1) room.waves[w].size else minOf(4, room.waves[w].size + 1), wave.size, "${room.name} 段階$step")
                    assertTrue(wave.size <= 4)
                    assertEquals(room.waves[w], wave.take(room.waves[w].size))
                }
                RoomKind.MINIBOSS -> assertEquals(listOf(room.waves[0][0]) + List(minOf(2, step - 1)) { QuestMobArchetype.RIFT_CASTER }, run.waveAt(room, 0), "${room.name} 段階$step")
                RoomKind.SPAWNER -> for (party in 1..4) assertEquals(room.spawner!!.target(party) + 3 * (step - 1), run.spawnerTarget(room, party))
            }
        }
    }

    @Test fun `the dungeon is keyed by its clears' id and only tiers 1 to 4 are built`() = scene {
        // CoreLoopServer reads the 段階 from account.dungeonClears[arena.bossId].
        assertEquals(CoreDungeons.SAKURA, dungeon.arena.bossId)
        assertTrue(dungeon.arena.bossId in CoreDungeons.ids)
        assertEquals(QuestMobArchetype.SAKURA_FANG, dungeon.arena.archetype)
        assertFailsWith<IllegalArgumentException> { InstanceDungeonFactory.create(0) }
        assertFailsWith<IllegalArgumentException> { InstanceDungeonFactory.create(5) }
    }

    /** CoreLoopServer sends a player home once they drop below y -20 in a dungeon (voidY); no floor may be that low. */
    @Test fun `every place the run puts a player or an enemy stands well above the dungeon's void line`() = scene {
        val voidY = -20.0
        val places = listOf(layout.spawn, layout.bossSpawn, layout.arenaEntry, layout.arenaCenter) +
            layout.rooms.flatMap { listOf(it.center, it.entry) + it.spawns } + layout.packs.map { it.first }
        places.forEach { assertTrue(it.y() > voidY + 4, "$it is near the void line") }
        assertTrue(layout.rooms.all { it.floor > voidY + 4 })
    }
}
