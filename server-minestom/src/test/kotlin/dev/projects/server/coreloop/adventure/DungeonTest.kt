package dev.projects.server.coreloop.adventure

import dev.projects.server.coreloop.*
import dev.projects.server.mob.*
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.*
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

class DungeonTest {
    @Test fun `2000 seeds have connected finite choices all required bosses and reproducible theme layout`() {
        val layouts = mutableSetOf<DungeonLayout>(); val kinds = mutableSetOf<DungeonRoomKind>()
        repeat(2000) { seed ->
            val p = DungeonPlan.generate(seed.toLong(), seed % 4 + 1, seed % 21)
            assertEquals(p, DungeonPlan.generate(seed.toLong(), seed % 4 + 1, seed % 21)); p.validate()
            assertEquals(3, p.rooms.count { it.kind == DungeonRoomKind.BOSS })
            assertEquals(21, p.rooms.size)
            layouts += p.rooms.map { it.layout }; kinds += p.rooms.map { it.kind }
            var at = p.choices(1)
            repeat(p.stages - 1) { at = at.flatMap(p::next).distinct() }
            assertEquals(p.choices(p.stages), at)
        }
        assertEquals(DungeonLayout.entries.toSet(), layouts); assertEquals(DungeonRoomKind.entries.toSet(), kinds)
        val malformed = DungeonPlan.generate(3, 1, 0)
        assertFailsWith<IllegalArgumentException> { malformed.copy(edges = malformed.edges + (0 to emptyList())).validate() }
    }

    @Test fun `all configurable floor lengths keep required guardians and bounded scaling`() {
        for (floors in 1..3) for (length in 3..6) {
            val balance = CoreMmoBalance(dungeonFloors = floors, dungeonRoomsPerFloor = length)
            val plan = DungeonPlan.generate(999, 4, 20, balance)
            plan.validate()
            assertEquals(floors, plan.rooms.count { it.kind == DungeonRoomKind.BOSS })
            assertEquals(floors * length, plan.stages)
        }
    }

    @Test fun `party readiness resets on roster change immutable launch and no duplicate membership`() {
        val parties = DungeonParties(); val ids = List(5) { UUID.randomUUID() }
        val p = parties.create(ids[0], 1, 0); parties.ready(ids[0])
        parties.join(ids[1], p.id); assertTrue(parties.of(ids[0])!!.ready.isEmpty())
        assertFailsWith<IllegalStateException> { parties.launch(ids[0]) }
        parties.join(ids[2], p.id); parties.join(ids[3], p.id)
        assertFailsWith<IllegalStateException> { parties.join(ids[4], p.id) }
        assertFailsWith<IllegalStateException> { parties.create(ids[2], 1, 0) }
        ids.take(4).forEach(parties::ready)
        val launched = parties.launch(ids[0]); assertTrue(launched.starting)
        assertFailsWith<IllegalStateException> { parties.leave(ids[1]) }
        parties.unlock(p.id); parties.leave(ids[0]); assertEquals(ids[1], parties.of(ids[1])!!.leader)
        assertTrue(parties.of(ids[1])!!.ready.isEmpty())
    }

    @Test fun `boons are personal deterministic bounded to one choice per cleared room and leave gear untouched`() {
        val b = DungeonBlessings(10); val player = UUID.randomUUID()
        assertEquals(b.offers(player, 1), b.offers(player, 1))
        val boon = b.offers(player, 1).first()
        assertTrue(b.choose(player, 1, boon, true)); assertFalse(b.choose(player, 1, boon, true))
        assertFalse(b.choose(player, 2, DungeonBoon.entries.first { it !in b.offers(player, 2) }, false))
        assertEquals(2, b.bonuses(player)[boon]); assertTrue(b.bonuses(UUID.randomUUID()).isEmpty())
        assertEquals(boon.apply(CoreAffixStats(), 2), b.stats(player, CoreAffixStats()))
    }

    @Test fun `real generated chambers preserve full level combat core and spawn headroom`() {
        MinecraftServer.init(Auth.Offline())
        val world = DungeonWorld.create(DungeonPlan.generate(10, 1, 0))
        try {
            for (room in world.plan.rooms) {
                for (dx in -17..17) for (dz in -17..17) {
                    val at = room.center.add(dx.toDouble(), 0.0, dz.toDouble())
                    assertTrue(world.instance.getBlock(at.sub(0.0, 1.0, 0.0)).isSolid)
                    assertTrue(world.instance.getBlock(at).isAir); assertTrue(world.instance.getBlock(at.add(0.0, 1.0, 0.0)).isAir)
                }
                assertTrue(DungeonWorld.safe(room, room.spawn)); assertTrue(world.instance.getBlock(room.spawn).isAir)
            }
        } finally { world.dispose() }
        assertFalse(world.instance.isRegistered)
    }

    @Test fun `all three bosses enforce three health gates allow solo mechanics and cleanup all displays`() {
        MinecraftServer.init(Auth.Offline())
        val world = DungeonWorld.create(DungeonPlan.generate(13, 1, 12))
        val p = player(world.plan.choices(1).first().spawn, world)
        try {
            world.plan.rooms.filter { it.kind == DungeonRoomKind.BOSS }.forEach { room ->
                p.teleport(room.spawn).join()
                var deaths = 0
                val c = QuestEncounterCombat(world.instance, 1, emptyList(), room.center, { _, boss -> if (boss) deaths++ }, { _, _ -> }, { true }, explicitBossArchetype = when (room.theme) {
                    DungeonTheme.EMBER -> QuestMobArchetype.FORGE_SENTINEL; DungeonTheme.TIDE -> QuestMobArchetype.TIDE_ARCHIVIST; DungeonTheme.ASTRAL -> QuestMobArchetype.ECLIPSE_REGENT
                })
                val m = DungeonBossMechanics(world.instance, room, 1, 12, c, { listOf(p) }, { _, _ -> })
                try {
                    var now = 0L
                    repeat(3) { gate ->
                        val boss = c.entities().first { c.isBoss(it.uuid) }
                        p.teleport(boss.position.sub(0.0, 0.0, 1.5)).join()
                        assertTrue(c.applyEffectDamage(boss.uuid, p, 1_000_000.0))
                        assertEquals(c.bossMaxHealth() * DungeonBossMechanics.GATES[gate], c.bossHealth(), .0001)
                        assertFalse(c.bossDefeated)
                        now += 100; m.tick(now); assertTrue(m.active)
                        assertFalse(c.applyEffectDamage(boss.uuid, p, 1_000_000.0))
                        // Every solo-permitted ordering is tried; only the displayed correct sequence advances.
                        repeat(90) {
                            if (m.active) {
                                val markerList = m.markerEntities()
                                val order = if (room.theme == DungeonTheme.TIDE) Regex("碑文 ([1-3→]+)").find(m.title)?.groupValues?.get(1)?.split('→')?.map { it.toInt() - 1 } ?: markerList.indices.toList() else markerList.indices.toList()
                                order.forEach { index -> val marker = markerList[index]; p.teleport(marker.position).join(); m.interact(p, marker, now) }
                                c.entities().filter { !c.isBoss(it.uuid) }.forEach { e -> p.teleport(e.position.sub(0.0, 0.0, 1.5)).join(); c.applyEffectDamage(e.uuid, p, 999999.0) }
                                now += 250; m.tick(now)
                            }
                        }
                        assertFalse(m.active, "${room.theme} gate=$gate stuck: ${m.title}")
                        assertEquals(gate + 1, m.gate)
                    }
                    val boss = c.entities().first { c.isBoss(it.uuid) }; p.teleport(boss.position.sub(0.0, 0.0, 1.5)).join()
                    assertTrue(c.applyEffectDamage(boss.uuid, p, 999999.0)); assertEquals(1, deaths)
                    assertTrue(c.bossDefeated)
                } finally { m.dispose(); c.dispose() }
                assertEquals(0, m.groundCount)
                assertTrue(m.markerEntities().isEmpty())
            }
        } finally { p.remove(); world.dispose() }
    }

    @Test fun `shared dungeon rewards each participant once leader cannot skip boons and exit leaves partner playing`() {
        harness(2) { h ->
            h.clearRoom()
            assertEquals(DungeonRunPhase.CHOOSING, h.run.phase)
            assertEquals(2, h.rewards.size)
            val first = h.players.first(); val second = h.players.last()
            val choices = h.run.view(first)!!.choices
            assertFalse(h.run.chooseRoom(first, choices.first().id))
            h.players.forEach { p -> assertTrue(h.run.chooseBoon(p, h.run.view(p)!!.boonOffers.first())) }
            assertFalse(h.run.chooseRoom(second, choices.first().id))
            assertTrue(h.run.chooseRoom(first, choices.first().id)); h.flush()
            assertEquals(DungeonRunPhase.FIGHTING, h.run.phase)
            h.run.remove(first); assertEquals(second.uuid, h.run.leader)
            assertNotNull(h.run.combat); assertEquals(1, h.run.playerIds.size)
            first.remove(); h.now += 200; h.run.tick(h.now)
            assertFalse(h.run.canFight(first)); assertTrue(h.run.canFight(second))
        }
    }

    @Test fun `room save barrier rejects skipping and late callback cannot resurrect closed instance`() {
        harness(1, holdReward = true) { h ->
            h.clearRoom()
            assertEquals(DungeonRunPhase.SAVING, h.run.phase); assertEquals(1, h.rewards.size)
            assertFalse(h.run.chooseBoon(h.players.first(), DungeonBoon.FORCE))
            h.run.close(); h.pendingReward.complete(CoreTransactionResult(CoreTransactionStatus.COMMITTED, null, "saved")); h.flush()
            assertEquals(DungeonRunPhase.CLOSED, h.run.phase); assertNull(h.run.combat)
            assertTrue(h.run.interactionMarkers().isEmpty())
        }
    }

    @Test fun `rescue requires nearby living teammate and spends one shared revive`() {
        harness(2) { h ->
            val fallen = h.players.first(); val rescuer = h.players.last()
            h.run.defeated(fallen); assertFalse(h.run.canFight(fallen)); assertEquals(GameMode.SPECTATOR, fallen.gameMode)
            val corpse = h.run.interactionMarkers().single()
            rescuer.teleport(corpse.position).join(); h.run.interact(rescuer, corpse, h.now)
            h.now += 3100; h.run.tick(h.now)
            assertTrue(h.run.canFight(fallen)); assertEquals(GameMode.ADVENTURE, fallen.gameMode)
            assertEquals(CoreMmoTuning.balance.dungeonRevives - 1, h.run.revives)
            assertTrue(corpse.isRemoved)
        }
    }

    @Test fun `solo wipe retries same room without rewards or extra boons`() {
        harness(1) { h ->
            val room = h.run.room
            h.run.defeated(h.players.first()); h.now += 100; h.run.tick(h.now)
            h.now += 6100; h.run.tick(h.now); h.flush()
            assertEquals(room, h.run.room); assertEquals(DungeonRunPhase.FIGHTING, h.run.phase)
            assertTrue(h.run.canFight(h.players.first())); assertTrue(h.rewards.isEmpty())
        }
    }

    private fun harness(count: Int, holdReward: Boolean = false, test: (Harness) -> Unit) {
        MinecraftServer.init(Auth.Offline()); Harness(count, holdReward).use(test)
    }
    private class Harness(count: Int, holdReward: Boolean) : AutoCloseable {
        var now = 1000L
        val world = DungeonWorld.create(DungeonPlan.generate(42, 1, 0, CoreMmoBalance(dungeonFloors = 1)))
        val players = List(count) { player(world.plan.choices(1).first().spawn, world) }
        val jobs = ArrayDeque<() -> Unit>()
        val rewards = mutableListOf<Pair<UUID, CoreAction.DungeonReward>>()
        val pendingReward = CompletableFuture<CoreTransactionResult>()
        val run = DungeonRun(UUID.randomUUID(), world, players, object : DungeonRunHost {
            override fun nowMillis() = now
            override fun connected(player: Player) = !player.isRemoved
            override fun hurt(player: Player, damage: Double) {}
            override fun resetActions(player: Player) {}
            override fun revive(player: Player, fraction: Double) {}
            override fun showRunMenu(player: Player) {}
            override fun schedule(action: () -> Unit) { jobs += action }
            override fun reward(player: Player, action: CoreAction.DungeonReward): CompletableFuture<CoreTransactionResult> {
                rewards += player.uuid to action
                return if (holdReward) pendingReward else CompletableFuture.completedFuture(CoreTransactionResult(CoreTransactionStatus.COMMITTED, null, "ok"))
            }
        })
        init { run.start(now) }
        fun flush() { while (jobs.isNotEmpty()) jobs.removeFirst()() }
        fun clearRoom() {
            repeat(20) {
                if (run.phase == DungeonRunPhase.FIGHTING) {
                    now += 1500; run.tick(now)
                    run.combat?.let { c -> c.entities().filter { !c.isBoss(it.uuid) }.forEach { enemy ->
                        val p = players.last(); p.teleport(enemy.position.sub(0.0, 0.0, 1.5)).join(); c.applyEffectDamage(enemy.uuid, p, 999999.0)
                    } }
                }
                flush()
            }
        }
        override fun close() { run.close(); players.forEach { if (!it.isRemoved) it.remove() }; world.dispose() }
    }
    companion object {
        private fun player(position: Pos, world: DungeonWorld): Player {
            val connection = object : PlayerConnection() {
                override fun sendPacket(packet: SendablePacket) {}
                override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
            }
            connection.setClientState(ConnectionState.PLAY); connection.setServerState(ConnectionState.PLAY)
            return Player(connection, GameProfile(UUID.randomUUID(), "DungeonTester")).also {
                connection.player = it; it.gameMode = GameMode.ADVENTURE
                it.setInstance(world.instance, position).get(10, TimeUnit.SECONDS)
            }
        }
    }
}
