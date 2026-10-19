package dev.projects.server.mob

import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import kotlin.test.assertTrue

/** 桜牙獣 (scripts/build_boss_sakura_fang.py). Needs `generateBossModels` (runs before processResources). */
class SakuraFangBossTest {
    private val definition = QuestMobContent.definition(1, QuestMobArchetype.SAKURA_FANG)

    private fun animations(): JsonObject {
        val text = requireNotNull(javaClass.classLoader.getResourceAsStream("boss-models/models/sakura_fang.bbmodel/model.animation.json")) {
            "boss-models missing: run :server-minestom:generateBossModels"
        }.use { it.readBytes().decodeToString() }
        return JsonParser.parseString(text).asJsonObject.getAsJsonObject("animations")
    }

    @Test
    fun `every ability has a once clip timed to its telegraph and recovery`() {
        val clips = animations()
        assertEquals("sakura_fang", definition.model)
        assertEquals(4, definition.abilities.size)
        for (ability in definition.abilities) {
            val clip = assertNotNull(clips.getAsJsonObject(ability.id), "missing clip ${ability.id}")
            assertFalse(clip["loop"].asBoolean, ability.id)
            assertEquals((ability.telegraphMillis + ability.recoveryMillis) / 1000.0, clip["animation_length"].asDouble, 0.051, ability.id)
            if (ability.maximumHealthRatio > definition.enrageHealthRatio) {
                val twin = assertNotNull(clips.getAsJsonObject("${ability.id}_enraged"), "missing clip ${ability.id}_enraged")
                assertEquals(clip["animation_length"].asDouble, twin["animation_length"].asDouble, 0.001, ability.id)
            }
        }
        for (loop in listOf("idle", "walk", "idle_enraged", "walk_enraged")) assertTrue(clips.getAsJsonObject(loop)["loop"].asBoolean, loop)
        for (once in listOf("enrage", "death", "death_enraged")) assertFalse(clips.getAsJsonObject(once)["loop"].asBoolean, once)
        // The enrage clip is the held roar (QuestEncounterCombat holds attacks for 5 s).
        assertEquals(5.0, clips.getAsJsonObject("enrage")["animation_length"].asDouble, 0.051)
        assertEquals(setOf(1.0, definition.enrageHealthRatio), definition.abilities.map { it.maximumHealthRatio }.toSet())
        assertTrue(BossModelView.available("sakura_fang"))
    }

    @Test
    fun `arena fight spawns the model, enrages once at half health and dies`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("sakura", 1)
        assertEquals("散り桜の庭", arena.displayName)
        val connection = MemoryConnection()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "SakuraTest"))
        connection.player = player
        player.gameMode = GameMode.ADVENTURE
        player.setInstance(arena.instance, arena.bossSpawn.sub(0.0, 0.0, 6.0)).join()
        var victories = 0
        val combat = QuestEncounterCombat(arena.instance, 1, emptyList(), arena.bossSpawn,
            { _, boss -> if (boss) victories++ }, { _, _ -> }, explicitBossArchetype = arena.archetype)
        try {
            val boss = combat.entities().single()
            assertTrue(boss is ModelCarrier && boss.isInvisible)
            assertTrue(combat.modelDisplayCount >= 20, "bones ${combat.modelDisplayCount}")
            combat.tick(1)
            assertFalse(combat.isEnraged(boss.uuid))
            assertTrue(combat.applyEffectDamage(boss.uuid, player, combat.bossMaxHealth() * .55))
            combat.tick(100)
            assertTrue(combat.isEnraged(boss.uuid))
            combat.tick(1500)
            combat.tick(6000)
            assertTrue(combat.applyEffectDamage(boss.uuid, player, 99_999.0))
            assertEquals(1, victories)
        } finally {
            combat.dispose(); player.remove(); arena.dispose()
        }
    }

    private class MemoryConnection : PlayerConnection() {
        override fun sendPacket(packet: SendablePacket) = Unit
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }
}
