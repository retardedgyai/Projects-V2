package dev.projects.server.mob

import dev.projects.server.coreloop.adventure.BossArenaFactory
import dev.projects.server.coreloop.ui.CoreBossBarHud
import dev.projects.server.coreloop.ui.CoreHudGlyphs
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
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

/** 散り桜の社's two mini-boss slots (docs/development/sakura-minibosses.md): what any pair of the proposals keeps. */
class SakuraMiniBossTest {
    private val slots = listOf(QuestMobArchetype.SAKURA_MINIBOSS_1, QuestMobArchetype.SAKURA_MINIBOSS_2)

    @Test
    fun `both slots are room guardians at about four tenths of the beast, fighting in a wave`() {
        val fang = QuestMobContent.definition(1, QuestMobArchetype.SAKURA_FANG).maximumHealth
        for (slot in slots) {
            val definition = QuestMobContent.definition(1, slot)
            assertEquals(QuestMobRarity.MINIBOSS, slot.rarity)
            assertFalse(definition.boss, "$slot is not the run's boss")
            assertTrue(definition.abilities.size in 3..4, "$slot skills ${definition.abilities.size}")
            // every skill says how to avoid it, like 桜牙獣's (the ground warning shows where)
            assertTrue(definition.abilities.all { "・" in it.displayName }, slot.name)
            assertEquals(QuestMobDropKind.ELITE, definition.dropKind)
            val health = definition.maximumHealth * MobGrades.MINIBOSS.health
            assertTrue(health in fang * .3..fang * .5, "$slot health $health of $fang")
            QuestCombatEncounter(listOf(net.minestom.server.coordinate.Pos.ZERO), listOf(slot))
        }
        // a mini-boss is always 希少 without MODs, whatever the seed
        assertEquals(setOf(MobGrades.MINIBOSS to emptyList<MobMod>()), (0L..40L).map { MobGrades.roll(it, slots[0]) }.toSet())
    }

    @Test
    fun `each skill has its own once clip timed to its telegraph and recovery`() {
        for ((slot, model) in slots.zip(listOf("hanamori_armor", "stone_lantern"))) {
            val definition = QuestMobContent.definition(1, slot)
            assertEquals(model, definition.model)
            assertTrue(BossModelView.available(model), "boss-models missing: run :server-minestom:generateBossModels")
            val text = requireNotNull(javaClass.classLoader.getResourceAsStream("boss-models/models/$model.bbmodel/model.animation.json")).use { it.readBytes().decodeToString() }
            val clips = com.google.gson.JsonParser.parseString(text).asJsonObject.getAsJsonObject("animations")
            for (ability in definition.abilities) {
                val clip = assertNotNull(clips.getAsJsonObject(ability.id), "missing clip ${ability.id}")
                assertFalse(clip["loop"].asBoolean, ability.id)
                assertEquals((ability.telegraphMillis + ability.recoveryMillis) / 1000.0, clip["animation_length"].asDouble, 0.051, ability.id)
            }
            for (loop in listOf("idle", "walk")) assertTrue(clips.getAsJsonObject(loop)["loop"].asBoolean, loop)
            assertFalse(clips.getAsJsonObject("death")["loop"].asBoolean)
        }
        // the armour's counter clip lasts its stagger
        val text = requireNotNull(javaClass.classLoader.getResourceAsStream("boss-models/models/hanamori_armor.bbmodel/model.animation.json")).use { it.readBytes().decodeToString() }
        val countered = com.google.gson.JsonParser.parseString(text).asJsonObject.getAsJsonObject("animations").getAsJsonObject("countered")
        assertEquals(QuestEncounterCombat.MINIBOSS_COUNTER_STAGGER_MILLIS / 1000.0, countered["animation_length"].asDouble, 0.051)
    }

    @Test
    fun `a mini-boss in a wave has its own bar with the tag and falls like a wave mob`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("miniboss1", 1)
        assertEquals(QuestMobArchetype.SAKURA_MINIBOSS_1, arena.archetype)
        val connection = MemoryConnection()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "MiniBossTest"))
        connection.player = player
        player.gameMode = GameMode.ADVENTURE
        player.setInstance(arena.instance, arena.bossSpawn.sub(0.0, 0.0, 6.0)).join()
        val defeats = mutableListOf<Boolean>()
        val combat = QuestEncounterCombat(arena.instance, 1, listOf(QuestCombatEncounter(listOf(arena.bossSpawn), listOf(arena.archetype))),
            arena.bossSpawn, { _, boss -> defeats += boss }, { _, _ -> }, spawnBoss = false, rollGrades = true)
        try {
            val mob = combat.entities().single()
            assertFalse(combat.isBoss(mob.uuid))
            assertNull(combat.bossHud())
            val hud = assertNotNull(combat.miniBossHud())
            assertTrue(hud.miniBoss)
            assertEquals("花守の鎧武者", hud.name)
            assertEquals(MobGrades.MINIBOSS, hud.grade)
            assertTrue(hud.mods.isEmpty())
            assertNotNull(hud.breakGauge, "a mini-boss can be broken like an elite")
            assertEquals(QuestMobContent.definition(1, arena.archetype).maximumHealth * MobGrades.MINIBOSS.health, hud.maximumHealth, .01)
            // it fights: within ten seconds it winds up a skill at the player in front of it
            var now = 1_000L
            var warned = false
            repeat(200) { now += 50; combat.tick(now); if (combat.groundDisplayCount > 0) warned = true }
            assertTrue(warned, "never warned of a skill")
            assertTrue(combat.applyEffectDamage(mob.uuid, player, 99_999.0))
            assertEquals(listOf(false), defeats, "the room's wave callback, not the boss's")
            assertEquals(QuestMobRarity.MINIBOSS, combat.latestDefeat?.rarity)
            assertNull(combat.miniBossHud())
        } finally {
            combat.dispose(); player.remove(); arena.dispose()
        }
    }

    @Test
    fun `the boss bar reads ミニボス on a tag wide enough for it`() {
        val hud = MobHud(5, "花守の鎧武者", MobGrades.MINIBOSS, emptyList(), 180.0, 225.0, .6, null, emptyList(), miniBoss = true)
        val text = flatten(CoreBossBarHud.render(hud, 200.0))
        assertTrue(CoreHudGlyphs.has("boss_plate_rare_mini"))
        assertTrue(CoreHudGlyphs["boss_plate_rare_mini"].char in text, "the wide-tag plate")
        assertFalse(CoreHudGlyphs["boss_plate_rare"].char in text, "not the graded plate")
        // the label fits the tag with the graded tag's 4 px margins
        assertTrue(CoreHudGlyphs.uniWidth(CoreBossBarHud.MINI_TAG) + 8 <= CoreBossBarHud.MINI_TAG_W,
            "ミニボス is ${CoreHudGlyphs.uniWidth(CoreBossBarHud.MINI_TAG)} px")
        val graded = flatten(CoreBossBarHud.render(hud.copy(miniBoss = false), 200.0))
        assertTrue(CoreHudGlyphs["boss_plate_rare"].char in graded)
    }

    private fun flatten(component: Component): String = buildString {
        fun visit(c: Component) {
            if (c is TextComponent) append(c.content())
            c.children().forEach(::visit)
        }
        visit(component)
    }

    private class MemoryConnection : PlayerConnection() {
        override fun sendPacket(packet: SendablePacket) = Unit
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }
}
