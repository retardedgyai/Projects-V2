package dev.projects.server.coreloop

import dev.projects.server.mob.QuestEncounterCombat
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.instance.block.Block
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real Minestom entities, but no sockets, external client, native input, or live server port. */
class CorePlayerCombatTest {
    @Test
    fun `duplicate vanilla swing signals start one normal attack and hit once during active frames`() = arena { h ->
        h.actor.attack()
        h.actor.attack()
        h.ticks(7)
        assertEquals(300.0, h.combat.bossHealth())
        h.ticks(1)
        assertEquals(288.0, h.combat.bossHealth())
        h.ticks(20)
        assertEquals(288.0, h.combat.bossHealth())
    }

    @Test
    fun `normal attack cannot hit a target behind the server facing`() = arena { h ->
        h.player.setView(180f, 0f)
        h.actor.attack()
        h.ticks(20)
        assertEquals(300.0, h.combat.bossHealth())
        h.player.setView(0f, 0f)
        h.actor.attack()
        h.ticks(20)
        assertEquals(288.0, h.combat.bossHealth())
    }

    @Test
    fun `wall blocks a spatially eligible normal hit`() = arena { h ->
        for (y in 40..43) h.instance.setBlock(8, y, 10, Block.STONE_BRICKS)
        h.actor.attack()
        h.ticks(20)
        assertEquals(300.0, h.combat.bossHealth())
    }

    @Test
    fun `lunge spends mana once hits after startup and respects its cooldown`() = arena { h ->
        h.actor.skill(0)
        h.actor.skill(0)
        assertEquals(85, h.actor.mana)
        assertEquals(4L, h.actor.cooldownSeconds(0))
        h.ticks(4)
        assertEquals(300.0, h.combat.bossHealth())
        h.ticks(1)
        assertEquals(279.6, h.combat.bossHealth(), 0.00001)
        assertTrue(h.player.position.z() > 10.6)
        h.ticks(11)
        val manaDuringCooldown = h.actor.mana
        h.actor.skill(0)
        assertEquals(manaDuringCooldown, h.actor.mana)
        h.ticks(64)
        assertEquals(0L, h.actor.cooldownSeconds(0))
        val readyMana = h.actor.mana
        h.actor.skill(0)
        assertEquals(readyMana - 15, h.actor.mana)
    }

    @Test
    fun `lunge stops before a close enemy and still connects instead of passing through`() = arena(bossDistance = 1.5) { h ->
        h.actor.skill(0)
        h.ticks(5)
        assertEquals(9.0, h.player.position.z(), 0.00001)
        assertEquals(279.6, h.combat.bossHealth(), 0.00001)
        h.ticks(15)
        assertEquals(279.6, h.combat.bossHealth(), 0.00001)
    }

    @Test
    fun `ground slam resolves once at its startup boundary and has seven second cooldown`() = arena { h ->
        h.actor.skill(1)
        assertEquals(75, h.actor.mana)
        assertEquals(7L, h.actor.cooldownSeconds(1))
        h.ticks(11)
        assertEquals(300.0, h.combat.bossHealth())
        h.ticks(1)
        assertEquals(268.8, h.combat.bossHealth(), 0.00001)
        h.ticks(20)
        assertEquals(268.8, h.combat.bossHealth(), 0.00001)
        val mana = h.actor.mana
        h.actor.skill(1)
        assertEquals(mana, h.actor.mana)
    }

    @Test
    fun `whirlwind damages surrounding target in exactly three timed pulses`() = arena(bossDistance = 2.0) { h ->
        h.player.setView(180f, 0f)
        h.actor.skill(2)
        assertEquals(65, h.actor.mana)
        assertEquals(11L, h.actor.cooldownSeconds(2))
        h.ticks(5)
        assertEquals(300.0, h.combat.bossHealth())
        h.ticks(1)
        assertEquals(286.2, h.combat.bossHealth(), 0.00001)
        h.ticks(8)
        assertEquals(272.4, h.combat.bossHealth(), 0.00001)
        h.ticks(8)
        assertEquals(258.6, h.combat.bossHealth(), 0.00001)
        h.ticks(15)
        assertEquals(258.6, h.combat.bossHealth(), 0.00001)
    }

    @Test
    fun `skill requested during normal swing waits for normal recovery`() = arena { h ->
        h.actor.attack()
        h.actor.skill(1)
        assertEquals(100, h.actor.mana)
        h.ticks(19)
        assertEquals(100, h.actor.mana)
        h.ticks(1)
        assertEquals(75, h.actor.mana)
        assertEquals(288.0, h.combat.bossHealth())
        h.ticks(12)
        assertEquals(256.8, h.combat.bossHealth(), 0.00001)
    }

    @Test
    fun `F dodge follows Minecraft strafe direction and cooldown then forward movement`() = arena { h ->
        h.player.refreshInput(false, false, false, true, false, false, false)
        h.actor.dodge()
        assertEquals(5.9, h.player.position.x(), 0.00001)
        assertEquals(8.5, h.player.position.z(), 0.00001)
        val afterFirst = h.player.position
        h.actor.dodge()
        assertEquals(afterFirst, h.player.position)
        h.ticks(24)
        h.player.refreshInput(true, false, false, false, false, false, false)
        h.actor.dodge()
        assertEquals(5.9, h.player.position.x(), 0.00001)
        assertEquals(11.1, h.player.position.z(), 0.00001)
    }

    @Test
    fun `diagonal dodge is normalized and cannot teleport through solid wall`() {
        arena { h ->
            h.player.refreshInput(true, false, false, true, false, false, false)
            h.actor.dodge()
            assertEquals(8.5 - 2.6 / sqrt(2.0), h.player.position.x(), 0.00001)
            assertEquals(8.5 + 2.6 / sqrt(2.0), h.player.position.z(), 0.00001)
        }
        arena { h ->
            for (y in 40..42) h.instance.setBlock(8, y, 10, Block.STONE)
            h.actor.dodge()
            assertTrue(h.player.position.z() in 8.5..9.7)
            assertTrue(h.player.position.z() + 0.3 < 10.000001)
        }
    }

    @Test
    fun `death cancels pending and queued actions and callback fires once until reset`() = arena { h ->
        h.actor.skill(1)
        h.actor.dodge()
        val deathPosition = h.player.position
        h.actor.hurt(1000.0)
        h.actor.hurt(1000.0)
        h.ticks(40)
        h.actor.attack()
        h.actor.skill(0)
        h.actor.dodge()
        assertTrue(h.actor.defeated)
        assertEquals(1, h.deaths)
        assertEquals(deathPosition, h.player.position)
        assertEquals(300.0, h.combat.bossHealth())
        h.actor.reset()
        assertFalse(h.actor.defeated)
        assertEquals(100.0, h.actor.health)
        assertEquals(20f, h.player.health)
        assertEquals(100, h.actor.mana)
        assertTrue((0..2).all { h.actor.cooldownSeconds(it) == 0L })
        h.ticks(20)
        assertEquals(300.0, h.combat.bossHealth())
        h.actor.attack()
        h.ticks(20)
        assertEquals(288.0, h.combat.bossHealth())
    }

    @Test
    fun `dying during enemy warning prevents delayed enemy damage`() = arena { h ->
        // Four blocks away permits only the boss's forward slam, so timing is deterministic.
        h.combat.tick(0L)
        h.combat.tick(550L)
        h.actor.hurt(1000.0)
        h.combat.tick(1450L)
        h.combat.tick(3000L)
        assertEquals(1, h.deaths)
        assertTrue(h.incomingHits.isEmpty())
    }

    @Test
    fun `armor damage reduction and potion healing use logical max health`() = arena(armorTier = 2) { h ->
        assertEquals(130, h.actor.maxHealth)
        h.actor.hurt(20.0)
        assertEquals(112.0, h.actor.health)
        assertEquals(20f * 112f / 130f, h.player.health, 0.0001f)
        h.actor.healPotion()
        assertEquals(130.0, h.actor.health)
        assertEquals(20f, h.player.health)
    }

    @Test
    fun `resetActions on map exit prevents a delayed skill from leaking into the next map`() = arena { h ->
        h.actor.skill(1)
        h.ticks(5)
        h.actor.resetActions()
        h.activeEncounter = null
        val exitPosition = h.player.position
        h.actor.skill(0)
        h.actor.attack()
        h.actor.dodge()
        h.ticks(30)
        assertEquals(exitPosition, h.player.position)
        h.activeEncounter = h.combat
        h.ticks(30)
        assertEquals(300.0, h.combat.bossHealth())
    }

    private fun arena(bossDistance: Double = 4.0, armorTier: Int = 1, stats: CoreAffixStats = CoreAffixStats(), roll: Double = 1.0, test: (Harness) -> Unit) {
        MinecraftServer.init(Auth.Offline())
        Harness(bossDistance, armorTier, stats, roll).use(test)
    }

    @Test
    fun `damage and normal modifiers multiply actual hit while haste shortens windup`() = arena(
        stats = CoreAffixStats(damagePercent = 20.0, normalDamagePercent = 10.0, attackSpeedPercent = 50.0)) { h ->
        assertEquals(1.5, h.actor.attackSpeed)
        h.actor.attack()
        h.ticks(7)
        assertEquals(300.0 - 12.0 * 1.2 * 1.1, h.combat.bossHealth(), 0.00001)
    }

    @Test
    fun `critical chance increase and multiplier modify a real server hit`() {
        arena(roll = 0.075) { h ->
            h.actor.attack(); h.ticks(8)
            assertEquals(288.0, h.combat.bossHealth())
        }
        arena(stats = CoreAffixStats(critChanceIncreasedPercent = 100.0, critMultiplierBonusPercent = 30.0), roll = 0.075) { h ->
            h.actor.attack(); h.ticks(8)
            assertEquals(300.0 - 12.0 * 1.8, h.combat.bossHealth(), 0.00001)
        }
    }

    @Test
    fun `skill damage cast reduction and cooldown reduction affect the actual skill`() = arena(
        stats = CoreAffixStats(skillDamagePercent = 50.0, castReductionPercent = 40.0, cooldownReductionPercent = 25.0)) { h ->
        h.actor.skill(1)
        assertEquals(105, h.actor.cooldownTicks(1))
        h.ticks(7)
        assertEquals(300.0, h.combat.bossHealth())
        h.ticks(1)
        assertEquals(300.0 - 12.0 * 2.6 * 1.5, h.combat.bossHealth(), 0.00001)
        assertEquals(97, h.actor.cooldownRemaining(1))
    }

    @Test
    fun `health mitigation and mobility mods apply once to the armor set`() = arena(armorTier = 2,
        stats = CoreAffixStats(healthFlat = 20.0, mitigationPercent = 20.0, moveSpeedPercent = 20.0)) { h ->
        assertEquals(150, h.actor.maxHealth)
        h.actor.hurt(20.0)
        assertEquals(135.6, h.actor.health, 0.00001)
        assertEquals(20f, h.player.getAttribute(Attribute.MAX_HEALTH).baseValue.toFloat())
        h.ticks(20)
        assertEquals(0.12, h.player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue, 0.00001)
        h.actor.healPotion()
        assertEquals(150.0, h.actor.health)
    }

    @Test
    fun `maximum mana and fractional regeneration are used by the skill pool`() = arena(
        stats = CoreAffixStats(maxManaFlat = 50.0, manaRegenPercent = 40.0)) { h ->
        assertEquals(150, h.actor.maxMana)
        h.actor.skill(2)
        assertEquals(115, h.actor.mana)
        h.ticks(20)
        assertEquals(122, h.actor.mana)
    }

    @Test
    fun `fire mod adds direct damage and three nonrecursive burn ticks`() = arena(stats = CoreAffixStats(fireFlat = 10.0)) { h ->
        h.actor.attack(); h.ticks(8)
        assertEquals(281.5, h.combat.bossHealth(), 0.00001)
        h.ticks(59)
        assertEquals(275.5, h.combat.bossHealth(), 0.00001)
        h.ticks(1)
        assertEquals(272.5, h.combat.bossHealth(), 0.00001)
        h.ticks(60)
        assertEquals(272.5, h.combat.bossHealth(), 0.00001)
    }

    @Test
    fun `ice mod exploits enemy elemental weakness with actual damage`() = arena(stats = CoreAffixStats(iceFlat = 8.0)) { h ->
        assertEquals("ice", h.combat.weaknessOf(h.combat.combatTargets().single().id))
        h.actor.attack(); h.ticks(8)
        assertEquals(300.0 - (12.0 + 8.0 * 0.65) * 1.25, h.combat.bossHealth(), 0.00001)
    }

    @Test
    fun `lightning mod chains once to a nearby second enemy`() = arena { h ->
        h.combat.dispose()
        val encounter = QuestEncounterCombat(h.instance, 1,
            listOf(dev.projects.server.mob.QuestCombatEncounter(listOf(Pos(8.5, 40.0, 12.5), Pos(11.5, 40.0, 12.5)),
                listOf(dev.projects.server.mob.QuestMobArchetype.SOLDIER, dev.projects.server.mob.QuestMobArchetype.SOLDIER))),
            Pos(25.0, 40.0, 25.0), onMobDefeated = { _, _ -> }, damagePlayer = { _, _ -> })
        try {
            val actor = CorePlayerCombat(h.player, { 1 }, { 1 }, { encounter },
                statSource = { CoreAffixStats(lightningFlat = 10.0) }, criticalRoll = { 1.0 }) {}
            actor.reset(); actor.attack(); repeat(8) { actor.tick() }
            val soldiers = encounter.combatTargets().mapNotNull { encounter.mobInfo(it.id) }.filter { it.archetype == dev.projects.server.mob.QuestMobArchetype.SOLDIER }
            assertEquals(listOf(25.5, 36.0), soldiers.map { it.health }.sorted())
            actor.resetActions()
        } finally { encounter.dispose() }
    }

    private class Harness(bossDistance: Double, armorTier: Int, stats: CoreAffixStats, roll: Double) : AutoCloseable {
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        val player: Player
        val combat: QuestEncounterCombat
        var activeEncounter: QuestEncounterCombat? = null
        val actor: CorePlayerCombat
        var deaths = 0
        val incomingHits = mutableListOf<Double>()

        init {
            instance.viewDistance(2)
            instance.setGenerator { unit -> unit.modifier().fillHeight(0, 40, Block.STONE) }
            // Player's effective range includes one chunk beyond the configured view distance.
            for (x in -3..3) for (z in -3..3) instance.loadChunk(x, z).get(10, TimeUnit.SECONDS)
            val connection = MemoryConnection()
            connection.setClientState(ConnectionState.PLAY)
            connection.setServerState(ConnectionState.PLAY)
            player = Player(connection, GameProfile(UUID.randomUUID(), "CombatTest"))
            connection.player = player
            player.gameMode = GameMode.ADVENTURE
            player.getAttribute(Attribute.MAX_HEALTH).baseValue = 100.0 + (armorTier - 1) * 30.0
            player.setInstance(instance, Pos(8.5, 40.0, 8.5)).get(10, TimeUnit.SECONDS)
            actor = CorePlayerCombat(player, { 1 }, { armorTier }, { activeEncounter }, statSource = { stats }, criticalRoll = { roll }) { deaths++ }
            combat = QuestEncounterCombat(instance, 1, emptyList(), Pos(8.5, 40.0, 8.5 + bossDistance),
                onMobDefeated = { _, _ -> },
                damagePlayer = { _, amount -> incomingHits += amount; actor.hurt(amount) },
                canTarget = { it === player && !actor.defeated })
            activeEncounter = combat
            actor.reset()
        }

        fun ticks(count: Int) = repeat(count) { actor.tick() }

        override fun close() {
            combat.dispose()
            player.remove()
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
        }
    }

    private class MemoryConnection : PlayerConnection() {
        override fun sendPacket(packet: SendablePacket) = Unit
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }
}
