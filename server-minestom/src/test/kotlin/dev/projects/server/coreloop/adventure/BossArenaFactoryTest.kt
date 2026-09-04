package dev.projects.server.coreloop.adventure

import dev.projects.server.coreloop.QuestCombatPlacement
import dev.projects.server.mob.MobAbilityManager
import dev.projects.server.mob.MobAttackShape
import dev.projects.server.mob.QuestEncounterCombat
import dev.projects.server.mob.QuestMobArchetype
import dev.projects.server.mob.QuestMobContent
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BossArenaFactoryTest {
    @Test
    fun `three dedicated arenas have distinct floors and clear entry boss and guardian spawns`() {
        MinecraftServer.init(Auth.Offline())
        val floors = mutableSetOf<String>()
        val bosses = mutableSetOf<QuestMobArchetype>()
        for (id in listOf("rift", "ritual", "trial")) {
            val arena = BossArenaFactory.create(id, 1)
            try {
                assertTrue(QuestCombatPlacement.clear(arena.instance, arena.playerSpawn))
                assertTrue(QuestCombatPlacement.clear(arena.instance, arena.bossSpawn))
                assertTrue(QuestCombatPlacement.clear(arena.instance, arena.bossSpawn.add(5.0, 0.0, 0.0)))
                assertTrue(QuestCombatPlacement.clear(arena.instance, arena.bossSpawn.add(-5.0, 0.0, 0.0)))
                floors += arena.instance.getBlock(38, 39, 37).name()
                bosses += arena.archetype
            } finally { arena.dispose() }
        }
        assertEquals(3, floors.size)
        assertEquals(3, bosses.size)
    }

    @Test
    fun `furnace cross has safe diagonals and exact axis strips`() {
        val shape = MobAttackShape.Cross(13.0, 1.6)
        assertTrue(shape.contains(Pos.ZERO, Vec(0.0, 0.0, 1.0), Pos(12.0, 0.0, 1.0)))
        assertTrue(shape.contains(Pos.ZERO, Vec(0.0, 0.0, 1.0), Pos(1.0, 0.0, 12.0)))
        assertFalse(shape.contains(Pos.ZERO, Vec(0.0, 0.0, 1.0), Pos(8.0, 0.0, 8.0)))
        assertFalse(shape.contains(Pos.ZERO, Vec(0.0, 0.0, 1.0), Pos(14.0, 0.0, 0.0)))
    }

    @Test
    fun `frost giant alternates safe outside and safe inside attacks`() {
        MinecraftServer.init(Auth.Offline())
        val definition = QuestMobContent.definition(1, QuestMobArchetype.GLACIAL_COLOSSUS)
        val manager = MobAbilityManager(definition.abilities, Random(5))
        val first = assertNotNull(manager.tryStart(0, Pos.ZERO, Pos(0.0, 0.0, 12.0)))
        manager.tick(5000, Pos(0.0, 0.0, 12.0))
        val second = assertNotNull(manager.tryStart(5500, Pos.ZERO, Pos(0.0, 0.0, 12.0)))
        assertNotEquals(first.frame.ability.id, second.frame.ability.id)
        val shapes = definition.abilities.map { it.shape as MobAttackShape.Ring }
        assertEquals(listOf(5.0, 0.0), shapes.map { it.innerRadius })
    }

    @Test
    fun `tempest barrier spawns at both thresholds blocks damage until guardians die and cleans up`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("trial", 1)
        val connection = MemoryConnection()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "TrialTest"))
        connection.player = player
        player.gameMode = GameMode.ADVENTURE
        player.setInstance(arena.instance, arena.bossSpawn.sub(0.0, 0.0, 4.0)).join()
        var victories = 0
        val combat = QuestEncounterCombat(arena.instance, 1, emptyList(), arena.bossSpawn,
            { _, boss -> if (boss) victories++ }, { _, _ -> }, explicitBossArchetype = arena.archetype)
        try {
            val boss = combat.entities().single()
            assertTrue(combat.applyDamage(boss.uuid, player, 160.0))
            combat.tick(0)
            assertEquals(2, combat.entities().count { !combat.isBoss(it.uuid) })
            assertFalse(combat.applyEffectDamage(boss.uuid, player, 9999.0))
            fun removeGuards() {
                combat.entities().filter { !combat.isBoss(it.uuid) }.forEach { assertTrue(combat.applyEffectDamage(it.uuid, player, 9999.0)) }
            }
            removeGuards()
            combat.tick(100)
            assertTrue(combat.applyDamage(boss.uuid, player, 150.0))
            combat.tick(200)
            assertEquals(2, combat.entities().count { !combat.isBoss(it.uuid) })
            assertFalse(combat.applyDamage(boss.uuid, player, 9999.0))
            removeGuards()
            combat.tick(300)
            assertTrue(combat.applyDamage(boss.uuid, player, 9999.0))
            assertEquals(1, victories)
            combat.tick(10_000)
            assertEquals(1, victories)
        } finally {
            combat.dispose()
            player.remove()
            arena.dispose()
        }
    }

    private class MemoryConnection : PlayerConnection() {
        override fun sendPacket(packet: SendablePacket) = Unit
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }
}
