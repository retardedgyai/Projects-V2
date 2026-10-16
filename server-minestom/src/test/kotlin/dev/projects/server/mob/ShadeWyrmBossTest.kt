package dev.projects.server.mob

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.projects.server.coreloop.adventure.BossArenaFactory
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import net.worldseed.multipart.GenericModelImpl
import net.worldseed.multipart.animations.AnimationHandlerImpl
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Needs the build step: `generateBossModels` runs before processResources, so the data is on the test classpath. */
class ShadeWyrmBossTest {
    private val definition = QuestMobContent.definition(1, QuestMobArchetype.SHADE_WYRM)

    private fun animations(): JsonObject {
        val text = requireNotNull(javaClass.classLoader.getResourceAsStream("boss-models/models/shade_wyrm.bbmodel/model.animation.json")) {
            "boss-models missing: run :server-minestom:generateBossModels"
        }.use { it.readBytes().decodeToString() }
        return JsonParser.parseString(text).asJsonObject.getAsJsonObject("animations")
    }

    @Test
    fun `every ability has a once clip timed to its telegraph and recovery`() {
        val clips = animations()
        assertEquals("shade_wyrm", definition.model)
        assertTrue(definition.abilities.size in 4..6)
        for (ability in definition.abilities) {
            val clip = assertNotNull(clips.getAsJsonObject(ability.id), "missing clip ${ability.id}")
            assertFalse(clip["loop"].asBoolean, ability.id)
            assertEquals((ability.telegraphMillis + ability.recoveryMillis) / 1000.0, clip["animation_length"].asDouble, 0.051, ability.id)
        }
        for (loop in listOf("idle", "walk", "idle_enraged", "walk_enraged")) assertTrue(clips.getAsJsonObject(loop)["loop"].asBoolean, loop)
        for (once in listOf("enrage", "death")) assertFalse(clips.getAsJsonObject(once)["loop"].asBoolean, once)
        // Phase two: the abilities gated at the enrage ratio exist and nothing is gated elsewhere.
        assertEquals(setOf(1.0, definition.enrageHealthRatio), definition.abilities.map { it.maximumHealthRatio }.toSet())
        assertTrue(BossModelView.available("shade_wyrm"))
    }

    private class Probe : GenericModelImpl() {
        override fun getId() = "shade_wyrm.bbmodel"
    }

    @Test
    fun `rest pose bone rotations land where the build script aims them`() {
        MinecraftServer.init(Auth.Offline())
        assertTrue(BossModelView.available("shade_wyrm"))
        val arena = BossArenaFactory.create("shade", 1)
        val origin = arena.bossSpawn.withView(0f, 0f)
        val model = Probe()
        model.init(arena.instance, origin)
        val handler = AnimationHandlerImpl(model)
        try {
            model.setGlobalRotation(BossModelView.modelYaw(origin.yaw()))
            handler.playRepeat("idle")
            repeat(2) { MinecraftServer.getSchedulerManager().processTick() }
            // (bone, Blockbench point on that bone, where build_boss_shade_wyrm.py's rest pose puts it) in px.
            // X is compared by distance from the centre line because WSEE mirrors Blockbench X.
            val checks = listOf(
                Triple("body", Vec(10.0, 56.0, 20.0), Vec(10.0, 56.0, 20.0)), // body
                Triple("neck", Vec(0.0, 70.0, 40.0), Vec(0.0, 70.0, 40.0)), // neck
                Triple("skull", Vec(0.0, 70.0, 60.0), Vec(0.0, 70.0, 60.0)), // face
                Triple("wing_l_hand", Vec(42.0, 48.0, -20.0), Vec(42.0, 48.0, -20.0)), // carpal
                Triple("wing_r_hinge15", Vec(-42.0, 30.0, -79.0), Vec(-42.0, 30.0, -79.0)), // right cloak tip
                Triple("leg_fl_paw", Vec(30.0, 6.0, 44.0), Vec(30.0, 6.0, 44.0)), // front paw
                Triple("leg_hr_foot", Vec(-26.0, 6.0, -60.0), Vec(-26.0, 6.0, -60.0)), // hind foot
                Triple("tail_end", Vec(-16.0, 20.0, -120.0), Vec(-16.0, 20.0, -120.0)), // tail tip
            )
            val misses = checks.mapNotNull { (bone, local, expected) ->
                val actual = requireNotNull(model.getBonePoint(bone, local.mul(0.25)))
                val got = Vec(kotlin.math.abs(actual.x() - origin.x()), actual.y() - origin.y(), actual.z() - origin.z()).mul(16.0)
                if (got.distance(Vec(kotlin.math.abs(expected.x()), expected.y(), expected.z())) < 3.0) null else "$bone: $got px vs $expected px"
            }
            assertTrue(misses.isEmpty(), misses.joinToString("; "))
        } finally {
            handler.destroy(); model.destroy(); arena.dispose()
        }
    }

    @Test
    fun `wsee runtime faces the carrier and the authored rotation signs mean what the script says`() {
        MinecraftServer.init(Auth.Offline())
        assertTrue(BossModelView.available("shade_wyrm"))
        val arena = BossArenaFactory.create("shade", 1)
        val origin = arena.bossSpawn.withView(0f, 0f)
        val model = Probe()
        model.init(arena.instance, origin)
        val handler = AnimationHandlerImpl(model)
        // getBonePoint takes generated geometry units: Blockbench px * 0.25.
        fun at(bone: String, point: Point) = requireNotNull(model.getBonePoint(bone, point.mul(0.25)))
        fun ticks(count: Int) = repeat(count) { MinecraftServer.getSchedulerManager().processTick() }
        try {
            model.setGlobalRotation(BossModelView.modelYaw(origin.yaw()))
            handler.playRepeat("idle"); ticks(2)
            val snout = Vec(0.0, 44.0, 63.0)
            val restSnout = at("skull", snout)
            val restChest = at("body", Vec(0.0, 40.0, 22.0))
            val restMembrane = at("wing_l_membrane", Vec(19.0, 20.0, -34.0))
            // Yaw 0 is south (+Z) in Minecraft: the snout must lead. WSEE mirrors Blockbench X, so the
            // Blockbench +X membrane hangs on world -X here; only "away from the centre line" matters below.
            assertTrue(restSnout.z() > origin.z() + 3.0, "snout $restSnout vs $origin")
            assertTrue(kotlin.math.abs(restMembrane.x() - origin.x()) > 0.8, "membrane $restMembrane")

            handler.playOnce("shade-breath") {}; ticks(1 + 26) // 1.3 s: neck pitched -30 (rear up)
            val reared = at("skull", snout)
            assertTrue(reared.y() > restSnout.y() + 0.3, "rear-up should lift the snout: $reared vs $restSnout")

            handler.playOnce("shade-pounce") {}; ticks(1 + 28) // 1.4 s: lunge +18 px forward
            assertTrue(at("body", Vec(0.0, 40.0, 22.0)).z() > restChest.z() + 0.6, "lunge should move forward")

            handler.playOnce("enrage") {}; ticks(1 + 18) // 0.9 s: cloak spread roll 110
            val spread = at("wing_l_membrane", Vec(19.0, 20.0, -34.0))
            assertTrue(kotlin.math.abs(spread.x() - origin.x()) > kotlin.math.abs(restMembrane.x() - origin.x()) + 1.0 &&
                spread.y() > restMembrane.y() + 1.0, "spread $spread vs $restMembrane")
        } finally {
            handler.destroy(); model.destroy(); arena.dispose()
        }
    }

    @Test
    fun `arena fight shows the model enrages once at half health and plays out its death`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("shade", 1)
        val connection = MemoryConnection()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "ShadeTest"))
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
            val target = combat.combatTargets().single()
            assertTrue(target.halfExtent.x() >= 2.0)
            combat.tick(1)
            assertFalse(combat.isEnraged(boss.uuid))
            assertTrue(combat.applyEffectDamage(boss.uuid, player, combat.bossMaxHealth() * .55))
            combat.tick(100)
            assertTrue(combat.isEnraged(boss.uuid))
            assertEquals(0, combat.groundDisplayCount, "the roar holds every attack")
            combat.tick(1500)
            assertEquals(0, combat.groundDisplayCount)
            assertTrue(combat.applyEffectDamage(boss.uuid, player, 99_999.0))
            assertEquals(1, victories)
            assertTrue(combat.modelDisplayCount > 0, "death clip keeps the bones until it ends")
        } finally {
            combat.dispose()
            assertEquals(0, combat.modelDisplayCount)
            player.remove()
            arena.dispose()
            assertFalse(arena.instance.isRegistered)
        }
    }

    private class MemoryConnection : PlayerConnection() {
        override fun sendPacket(packet: SendablePacket) = Unit
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }
}
