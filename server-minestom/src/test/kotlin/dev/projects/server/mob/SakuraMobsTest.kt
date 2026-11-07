package dev.projects.server.mob

import dev.projects.server.coreloop.adventure.BossArenaFactory
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 散り桜の社's common enemies (scripts/build_sakura_mobs.py): 桜木の亡者, 枝弓の骸, つぼみ. */
class SakuraMobsTest {
    private val mobs = mapOf(
        QuestMobArchetype.SAKURA_HUSK to "sakura_husk",
        QuestMobArchetype.SAKURA_ARCHER to "branch_archer",
        QuestMobArchetype.SAKURA_BUD to "bud_creeper",
    )

    @Test
    fun `each is a common enemy on its own model with a clip for every move`() {
        for ((archetype, model) in mobs) {
            val definition = QuestMobContent.definition(1, archetype)
            assertEquals(QuestMobRarity.NORMAL, archetype.rarity)
            assertEquals(model, definition.model)
            assertTrue(BossModelView.available(model), "boss-models missing: run :server-minestom:generateBossModels")
            val text = requireNotNull(javaClass.classLoader.getResourceAsStream("boss-models/models/$model.bbmodel/model.animation.json")).use { it.readBytes().decodeToString() }
            val clips = com.google.gson.JsonParser.parseString(text).asJsonObject.getAsJsonObject("animations")
            for (ability in definition.abilities) {
                val clip = assertNotNull(clips.getAsJsonObject(ability.id), "$model: missing clip ${ability.id}")
                assertFalse(clip["loop"].asBoolean, ability.id)
                assertEquals((ability.telegraphMillis + ability.recoveryMillis) / 1000.0, clip["animation_length"].asDouble, 0.051, ability.id)
            }
            for (loop in listOf("idle", "walk")) assertTrue(clips.getAsJsonObject(loop)["loop"].asBoolean, "$model $loop")
            for (once in listOf("hurt", "death")) assertFalse(clips.getAsJsonObject(once)["loop"].asBoolean, "$model $once")
        }
        assertNotNull(QuestMobContent.definition(1, QuestMobArchetype.SAKURA_BUD).abilities.singleOrNull { it.id == QuestEncounterCombat.SELF_BURST })
    }

    @Test
    fun `a bud that reaches a player bursts into bloom, hurts them, and is spent without a killer`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("miniboss1", 1)
        val connection = MemoryConnection()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "BudTest"))
        connection.player = player
        player.gameMode = GameMode.ADVENTURE
        val at = arena.bossSpawn
        player.setInstance(arena.instance, at.add(0.0, 0.0, 1.5)).join()
        val killers = mutableListOf<Player?>()
        var hurt = 0.0
        val combat = QuestEncounterCombat(arena.instance, 1, listOf(QuestCombatEncounter(listOf(at), listOf(QuestMobArchetype.SAKURA_BUD))),
            at, { killer, _ -> killers += killer }, { _, amount -> hurt += amount }, spawnBoss = false)
        try {
            val bud = combat.entities().single()
            var now = 1_000L
            repeat(120) { if (combat.isAlive(bud.uuid)) { now += 50; combat.tick(now) } }
            assertFalse(combat.isAlive(bud.uuid), "it never burst")
            assertTrue(hurt > 0.0, "the bloom hurt nobody")
            assertEquals(listOf<Player?>(null), killers, "counted for the room, with no one's kill")
            assertNull(combat.latestDefeat?.killerId)
        } finally {
            combat.dispose(); player.remove(); arena.dispose()
        }
    }

    @Test
    fun `the archer's arrow flies to the player, with no ground warning`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("miniboss1", 1)
        val connection = MemoryConnection()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "ArrowTest"))
        connection.player = player
        player.gameMode = GameMode.ADVENTURE
        val at = arena.bossSpawn
        player.setInstance(arena.instance, at.add(0.0, 0.0, 8.0)).join()
        var hurt = 0.0
        var hurtAt = 0L
        val combat = QuestEncounterCombat(arena.instance, 1, listOf(QuestCombatEncounter(listOf(at), listOf(QuestMobArchetype.SAKURA_ARCHER))),
            at, { _, _ -> }, { _, amount -> if (hurt == 0.0) hurtAt = -1; hurt += amount }, spawnBoss = false)
        try {
            var now = 1_000L
            var warned = false
            repeat(200) {
                if (hurt == 0.0) {
                    now += 50; combat.tick(now)
                    if (combat.groundDisplayCount > 0) warned = true
                    // hold the player still where it stood, so the arrow has a mark
                    player.teleport(at.add(0.0, 0.0, 8.0))
                }
            }
            assertTrue(hurt > 0.0, "the arrow never struck")
            assertFalse(warned, "a small enemy drew a ground warning")
        } finally {
            combat.dispose(); player.remove(); arena.dispose()
        }
    }

    @Test
    fun `a blow and a shot strike one target and draw no area`() {
        val strike = MobAttackShape.Strike(2.5)
        val origin = net.minestom.server.coordinate.Pos(0.0, 0.0, 0.0)
        val facing = net.minestom.server.coordinate.Vec(0.0, 0.0, 1.0)
        assertTrue(strike.contains(origin, facing, net.minestom.server.coordinate.Pos(0.0, 0.0, 2.0)))
        assertFalse(strike.contains(origin, facing, net.minestom.server.coordinate.Pos(0.0, 0.0, -2.0)))
        assertTrue(strike.outline(origin, facing).isEmpty())
        assertTrue(MobAttackShape.Shot(18.0, 22.0).outline(origin, facing).isEmpty())
    }

    private class MemoryConnection : PlayerConnection() {
        override fun sendPacket(packet: SendablePacket) = Unit
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }
}
