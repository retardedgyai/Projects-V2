package dev.projects.server.mob

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.metadata.monster.raider.SpellcasterIllagerMeta
import net.minestom.server.instance.block.Block
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QuestEncounterContentTest {
    @Test
    fun `shield reduces front hits but rear strikes and attack windups are vulnerable`() = arena(QuestMobArchetype.SHIELD_GUARD) { h ->
        h.player.teleport(Pos(8.5, 40.0, 14.5)).join()
        assertTrue(h.combat.applyDamage(h.enemyId, h.player, 20.0))
        assertEquals(57.0, h.info().health)
        h.player.teleport(Pos(8.5, 40.0, 10.5)).join()
        assertTrue(h.combat.applyDamage(h.enemyId, h.player, 20.0))
        assertEquals(37.0, h.info().health)
        h.player.teleport(Pos(8.5, 40.0, 14.5)).join()
        h.combat.tick(0)
        assertTrue(h.combat.groundDisplayCount > 0)
        assertTrue(h.combat.applyDamage(h.enemyId, h.player, 20.0))
        assertEquals(17.0, h.info().health)
    }

    @Test
    fun `slow clamps duration and intensity changes Navigator speed then expires`() = arena(QuestMobArchetype.SOLDIER) { h ->
        h.combat.tick(0)
        val enemy = h.combat.entities().first { it.uuid == h.enemyId }
        assertTrue(h.combat.applySlow(h.enemyId, 0.5, 1000))
        assertEquals(0.07, enemy.getAttribute(Attribute.MOVEMENT_SPEED).baseValue, 0.00001)
        h.combat.tick(999)
        assertEquals(0.07, enemy.getAttribute(Attribute.MOVEMENT_SPEED).baseValue, 0.00001)
        h.combat.tick(1000)
        assertEquals(0.14, enemy.getAttribute(Attribute.MOVEMENT_SPEED).baseValue, 0.00001)
        assertTrue(h.combat.applySlow(h.enemyId, 99.0, 1000))
        assertEquals(0.14 * 0.35, enemy.getAttribute(Attribute.MOVEMENT_SPEED).baseValue, 0.00001)
        val boss = h.combat.entities().single { h.combat.isBoss(it.uuid) }
        assertTrue(h.combat.applySlow(boss.uuid, 0.5, 1000))
        assertEquals(0.12 * 0.75, boss.getAttribute(Attribute.MOVEMENT_SPEED).baseValue, 0.00001)
        assertFalse(h.combat.applySlow(h.enemyId, Double.NaN, 1000))
        assertFalse(h.combat.applySlow(h.enemyId, 0.5, -1))
    }

    @Test
    fun `defeat context exists before callback and every source rewards once with no lingering warning`() = arena(QuestMobArchetype.ELITE_BRUTE) { h ->
        h.combat.tick(0)
        assertTrue(h.combat.groundDisplayCount > 0)
        assertTrue(h.combat.applyDamage(h.enemyId, h.player, 9999.0))
        assertEquals(0, h.displaysAtReward.single())
        val defeat = h.defeats.single()
        assertEquals(h.enemyId, defeat.entityId)
        assertEquals("mob:${h.enemyId}", defeat.sourceId)
        assertEquals(QuestMobRarity.ELITE, defeat.rarity)
        assertEquals(QuestMobDropKind.ELITE, defeat.dropKind)
        assertEquals(QuestMobArchetype.ELITE_BRUTE, defeat.archetype)
        assertEquals(h.player.uuid, defeat.killerId)
        assertEquals(Pos(8.5, 40.0, 12.5), defeat.position)
        assertFalse(h.combat.applyDamage(h.enemyId, h.player, 9999.0))
        assertFalse(h.combat.applyEffectDamage(h.enemyId, h.player, 9999.0))
        assertFalse(h.combat.applySlow(h.enemyId, 0.5, 1000))
        h.combat.tick(10_000)
        assertEquals(1, h.defeats.size)
        assertEquals(1, h.combat.defeatedMobCount)
        assertEquals(1, h.combat.clearedEncounterCount)
    }

    @Test
    fun `validated burn can finish at twenty meters but raw hits and effects beyond twentyfour cannot`() = arena(QuestMobArchetype.SOLDIER) { h ->
        h.player.teleport(Pos(8.5, 40.0, -7.5)).join()
        assertFalse(h.combat.applyDamage(h.enemyId, h.player, 12.0))
        assertTrue(h.combat.applyEffectDamage(h.enemyId, h.player, 12.0))
        assertEquals(32.0, h.info().health)
        h.player.teleport(Pos(8.5, 40.0, -12.0)).join()
        assertFalse(h.combat.applyEffectDamage(h.enemyId, h.player, 12.0))
        h.player.teleport(Pos(8.5, 40.0, -7.5)).join()
        h.targetable = false
        assertFalse(h.combat.applyEffectDamage(h.enemyId, h.player, 12.0))
    }

    @Test
    fun `caster fires a long thin locked ray once instead of chasing into melee`() = arena(QuestMobArchetype.RIFT_CASTER) { h ->
        h.player.teleport(Pos(8.5, 40.0, 2.5)).join()
        h.combat.tick(0)
        assertTrue(h.combat.groundDisplayCount > 0)
        val meta = h.combat.entities().first { it.uuid == h.enemyId }.entityMeta as SpellcasterIllagerMeta
        assertEquals(SpellcasterIllagerMeta.Spell.ATTACK, meta.spell)
        h.combat.tick(450)
        h.combat.tick(1249)
        assertTrue(h.hits.isEmpty())
        h.combat.tick(1250)
        assertEquals(listOf(12.0), h.hits)
        assertEquals(SpellcasterIllagerMeta.Spell.NONE, meta.spell)
        h.combat.tick(1300)
        assertEquals(1, h.hits.size)
        h.combat.tick(1430)
        assertEquals(0, h.combat.groundDisplayCount)
    }

    @Test
    fun `moving outside caster locked ray avoids damage`() = arena(QuestMobArchetype.RIFT_CASTER) { h ->
        h.player.teleport(Pos(8.5, 40.0, 2.5)).join()
        h.combat.tick(0)
        h.combat.tick(450)
        h.player.teleport(Pos(11.0, 40.0, 2.5)).join()
        h.combat.tick(1250)
        assertTrue(h.hits.isEmpty())
    }

    @Test
    fun `delayed server tick resolves the already shown ray once even after recovery elapsed`() = arena(QuestMobArchetype.RIFT_CASTER) { h ->
        h.player.teleport(Pos(8.5, 40.0, 2.5)).join()
        h.combat.tick(0)
        assertTrue(h.combat.groundDisplayCount > 0)
        h.combat.tick(3000)
        assertEquals(listOf(12.0), h.hits)
        assertEquals(0, h.combat.groundDisplayCount)
        h.combat.tick(3100)
        assertEquals(listOf(12.0), h.hits)
    }

    @Test
    fun `return cancels warning immediately and failed save resumes with a fresh warning not an overdue hit`() = arena(QuestMobArchetype.RIFT_CASTER) { h ->
        h.player.teleport(Pos(8.5, 40.0, 2.5)).join()
        h.combat.tick(0)
        h.combat.tick(450)
        assertTrue(h.combat.groundDisplayCount > 0)
        h.targetable = false
        h.combat.stopActionsForReturn(450)
        assertEquals(0, h.combat.groundDisplayCount)
        h.combat.tick(3000)
        assertTrue(h.hits.isEmpty())
        h.targetable = true
        h.combat.tick(3100)
        assertTrue(h.hits.isEmpty())
        assertTrue(h.combat.groundDisplayCount > 0)
        h.combat.tick(4349)
        assertTrue(h.hits.isEmpty())
        h.combat.tick(4350)
        assertEquals(listOf(12.0), h.hits)
    }

    @Test
    fun `return inside damage callback cancels all remaining attacks in the same server tick`() = arena(QuestMobArchetype.RIFT_CASTER) { h ->
        h.player.teleport(Pos(8.5, 40.0, 2.5)).join()
        h.combat.spawnEncounter(QuestCombatEncounter(listOf(Pos(10.5, 40.0, 12.5)), listOf(QuestMobArchetype.RIFT_CASTER)))
        h.onIncomingHit = {
            h.targetable = false
            h.combat.stopActionsForReturn(1250)
        }
        h.combat.tick(0)
        h.combat.tick(450)
        h.combat.tick(1250)
        assertEquals(listOf(12.0), h.hits)
        assertEquals(0, h.combat.groundDisplayCount)
        h.combat.tick(10_000)
        assertEquals(listOf(12.0), h.hits)
        assertEquals(0, h.combat.groundDisplayCount)
    }

    @Test
    fun `losing a live player cancels warning slow and delayed hit and return heals`() = arena(QuestMobArchetype.SOLDIER) { h ->
        h.combat.applyDamage(h.enemyId, h.player, 12.0)
        h.combat.tick(0)
        h.combat.applySlow(h.enemyId, 0.5, 10_000)
        assertTrue(h.combat.groundDisplayCount > 0)
        h.targetable = false
        h.combat.tick(500)
        assertEquals(0, h.combat.groundDisplayCount)
        assertFalse(h.combat.applySlow(h.enemyId, 0.5, 10_000))
        h.combat.tick(2000)
        assertEquals(44.0, h.info().health)
        assertTrue(h.hits.isEmpty())
        assertEquals(0.14, h.combat.entities().first { it.uuid == h.enemyId }.getAttribute(Attribute.MOVEMENT_SPEED).baseValue)
    }

    @Test
    fun `boss seed variant names and elemental weakness are exposed through public API`() {
        for (seed in 0L..2L) arena(QuestMobArchetype.SOLDIER, seed) { h ->
            val boss = h.combat.entities().single { h.combat.isBoss(it.uuid) }
            val expected = QuestMobContent.boss(seed)
            assertEquals(expected.displayName, h.combat.bossName())
            assertEquals(expected.weakness, h.combat.weaknessOf(boss.uuid))
            assertEquals(expected, h.combat.mobInfo(boss.uuid)?.archetype)
            assertEquals(QuestMobRarity.BOSS, h.combat.mobInfo(boss.uuid)?.rarity)
            assertNotNull(boss.customName)
        }
    }

    private fun arena(archetype: QuestMobArchetype, seed: Long = 0L, test: (Harness) -> Unit) {
        MinecraftServer.init(Auth.Offline())
        Harness(archetype, seed).use(test)
    }

    private class Harness(archetype: QuestMobArchetype, seed: Long) : AutoCloseable {
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        val player: Player
        lateinit var combat: QuestEncounterCombat
        val enemyId: UUID
        var targetable = true
        val hits = mutableListOf<Double>()
        var onIncomingHit: (() -> Unit)? = null
        val defeats = mutableListOf<QuestMobDefeat>()
        val displaysAtReward = mutableListOf<Int>()
        init {
            instance.viewDistance(2)
            instance.setGenerator { it.modifier().fillHeight(0, 40, Block.STONE) }
            for (x in -3..3) for (z in -3..3) instance.loadChunk(x, z).get(10, TimeUnit.SECONDS)
            val connection = MemoryConnection()
            connection.setClientState(ConnectionState.PLAY)
            connection.setServerState(ConnectionState.PLAY)
            player = Player(connection, GameProfile(UUID.randomUUID(), "ContentTest"))
            connection.player = player
            player.gameMode = GameMode.ADVENTURE
            player.setInstance(instance, Pos(8.5, 40.0, 8.5)).get(10, TimeUnit.SECONDS)
            combat = QuestEncounterCombat(instance, 1,
                listOf(QuestCombatEncounter(listOf(Pos(8.5, 40.0, 12.5)), listOf(archetype))),
                Pos(40.5, 40.0, 40.5),
                onMobDefeated = { _, _ -> defeats += checkNotNull(combat.latestDefeat); displaysAtReward += combat.groundDisplayCount },
                damagePlayer = { _, damage -> hits += damage; onIncomingHit?.invoke() }, canTarget = { it === player && targetable }, contentSeed = seed)
            enemyId = combat.entities().first { !combat.isBoss(it.uuid) }.uuid
        }

        fun info(): QuestMobInfo = checkNotNull(combat.mobInfo(enemyId))

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
