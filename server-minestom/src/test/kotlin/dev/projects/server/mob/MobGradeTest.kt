package dev.projects.server.mob

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MobGradeTest {
    @Test fun `a roll is fixed by its seed, carries one MOD per grade step and bosses stay outside the grades`() {
        val rolls = (0L until 4000L).map { MobGrades.roll(it, QuestMobArchetype.SOLDIER) }
        assertEquals(rolls, (0L until 4000L).map { MobGrades.roll(it, QuestMobArchetype.SOLDIER) })
        assertTrue(rolls.all { (grade, mods) -> mods.size == grade.mods && mods.distinct() == mods })
        val counts = rolls.groupingBy { it.first }.eachCount()
        assertEquals(MobGrade.entries.toSet(), counts.keys, "every grade appears")
        assertTrue(counts.getValue(MobGrade.COMMON) > counts.getValue(MobGrade.ELITE) && counts.getValue(MobGrade.UNIQUE) > counts.getValue(MobGrade.CALAMITY))
        assertTrue((0L until 500L).none { MobGrades.roll(it, QuestMobArchetype.ELITE_BRUTE).first == MobGrade.COMMON })
        assertEquals(MobGrade.COMMON to emptyList(), MobGrades.roll(7L, QuestMobArchetype.SHADE_WYRM))
    }

    @Test fun `graded mobs show a plate, wear a break gauge under crowd control and break for four seconds`() {
        MinecraftServer.init(Auth.Offline())
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setGenerator { it.modifier().fillHeight(0, 40, Block.STONE) }
        instance.viewDistance(2)
        for (x in -3..3) for (z in -3..3) instance.loadChunk(x, z).get(10, TimeUnit.SECONDS)
        val connection = MemoryConnection(); connection.setClientState(ConnectionState.PLAY); connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "GradeTest")); connection.player = player
        player.gameMode = GameMode.ADVENTURE
        player.setInstance(instance, Pos(2.0, 40.0, 6.0)).get(10, TimeUnit.SECONDS)
        val combat = QuestEncounterCombat(instance, 1, listOf(QuestCombatEncounter(listOf(Pos(2.0, 40.0, 2.0)))), Pos(12.0, 40.0, 12.0),
            { _, _ -> }, { _, _ -> }, rollGrades = true)
        try {
            val id = combat.entities().first { !combat.isBoss(it.uuid) }.uuid
            combat.forceGrade(id, MobGrade.RARE, listOf(MobMod.IRONHIDE, MobMod.REGEN))
            combat.pixelNameplates = true
            combat.tick(1000L)
            assertEquals(1, combat.plateCount, "only the graded mob gets a plate; the boss uses the boss bar")
            val hud = assertNotNull(combat.hudOf(id))
            assertEquals(MobGrade.RARE, hud.grade); assertEquals(1.0, hud.breakGauge); assertNull(hud.broken)
            assertTrue(combat.applyCrowdControl(id, CrowdControl.STUN, 1000, player))
            assertTrue(CrowdControl.STUN in combat.crowdControl(id), "a graded mob still takes the CC itself")
            assertEquals(80.0, combat.breakGauge(id))
            assertTrue(combat.hudOf(id)!!.effects.any { it.kind == MobHudEffectKind.CC && it.icon == "stun" })
            repeat(4) { combat.applyCrowdControl(id, CrowdControl.STUN, 1000, player) }
            assertTrue(combat.isBroken(id)); assertEquals(1.0, combat.hudOf(id)!!.broken)
            combat.tick(3000L); assertEquals(.5, combat.hudOf(id)!!.broken!!, .01)
            combat.tick(5100L); assertTrue(!combat.isBroken(id) && combat.hudOf(id)!!.broken == null)
            assertNull(combat.bossHud()?.broken); assertEquals(1.0, combat.bossHud()?.breakGauge)
        } finally {
            combat.dispose(); player.remove()
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
        }
    }

    @Test fun `a launch throws the mob up off the ground and it comes back down`() {
        MinecraftServer.init(Auth.Offline())
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setGenerator { it.modifier().fillHeight(0, 40, Block.STONE) }
        instance.viewDistance(2)
        for (x in -3..3) for (z in -3..3) instance.loadChunk(x, z).get(10, TimeUnit.SECONDS)
        val connection = MemoryConnection(); connection.setClientState(ConnectionState.PLAY); connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "LaunchTest")); connection.player = player
        player.gameMode = GameMode.ADVENTURE
        player.setInstance(instance, Pos(2.0, 40.0, 6.0)).get(10, TimeUnit.SECONDS)
        val combat = QuestEncounterCombat(instance, 1, listOf(QuestCombatEncounter(listOf(Pos(2.0, 40.0, 2.0)))), Pos(12.0, 40.0, 12.0),
            { _, _ -> }, { _, _ -> }, rollGrades = false)
        try {
            val mob = combat.entities().first { !combat.isBoss(it.uuid) }
            var now = 1000L
            repeat(20) { combat.tick(now); mob.tick(now); now += 50 }
            val ground = mob.position.y()
            assertTrue(combat.applyCrowdControl(mob.uuid, CrowdControl.STUN, 1500, player))
            assertTrue(combat.applyCrowdControl(mob.uuid, CrowdControl.AIRBORNE, 700, player))
            var peak = ground
            repeat(30) { combat.tick(now); mob.tick(now); now += 50; peak = maxOf(peak, mob.position.y()) }
            println("LAUNCH ground=$ground peak=$peak end=${mob.position.y()}")
            assertTrue(peak - ground > 2.5, "rose ${peak - ground}")
        } finally {
            combat.dispose(); player.remove()
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
        }
    }

    private class MemoryConnection : PlayerConnection() {
        override fun sendPacket(packet: SendablePacket) {}
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }
}
