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
        assertTrue(definition.abilities.size in 4..18)
        for (ability in definition.abilities) {
            val clip = assertNotNull(clips.getAsJsonObject(ability.id), "missing clip ${ability.id}")
            assertFalse(clip["loop"].asBoolean, ability.id)
            assertEquals((ability.telegraphMillis + ability.recoveryMillis) / 1000.0, clip["animation_length"].asDouble, 0.051, ability.id)
            // Phase-one abilities also run after the enrage: their twin in the enraged stance keeps the same timing.
            if (ability.maximumHealthRatio > definition.enrageHealthRatio) {
                val twin = assertNotNull(clips.getAsJsonObject("${ability.id}_enraged"), "missing clip ${ability.id}_enraged")
                assertEquals(clip["animation_length"].asDouble, twin["animation_length"].asDouble, 0.001, ability.id)
            }
        }
        for (loop in listOf("idle", "walk", "idle_enraged", "walk_enraged")) assertTrue(clips.getAsJsonObject(loop)["loop"].asBoolean, loop)
        for (once in listOf("awaken", "relocate", "relocate_enraged")) assertFalse(clips.getAsJsonObject(once)["loop"].asBoolean, once)
        for (once in listOf("enrage", "death", "death_enraged")) assertFalse(clips.getAsJsonObject(once)["loop"].asBoolean, once)
        // Three phases: abilities unlock at the second form (70 %) and at the enrage (35 %), nowhere else.
        assertEquals(setOf(1.0, definition.awakenHealthRatio, definition.enrageHealthRatio), definition.abilities.map { it.maximumHealthRatio }.toSet())
        assertTrue(definition.awakenHealthRatio > definition.enrageHealthRatio)
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
            // No clip: idle carries the neck arched like the walk, so only the bare model shows the rest pose.
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
            val restMembrane = at("wing_l_membrane", Vec(19.0, 20.0, -34.0))
            // Yaw 0 is south (+Z) in Minecraft: the snout must lead. WSEE mirrors Blockbench X, so the
            // Blockbench +X membrane hangs on world -X here; only "away from the centre line" matters below.
            assertTrue(restSnout.z() > origin.z() + 3.0, "snout $restSnout vs $origin")
            assertTrue(kotlin.math.abs(restMembrane.x() - origin.x()) > 0.8, "membrane $restMembrane")

            handler.playOnce("enrage") {}; ticks(1 + 52) // 2.6 s: reared on the hind legs (body pitched -38)
            val reared = at("skull", snout)
            assertTrue(reared.y() > restSnout.y() + 0.5, "rear-up should lift the snout: $reared vs $restSnout")

            ticks(40) // 4.6 s: the enraged crouch, the wing-arm's carpal planted in front on the ground
            val carpal = at("cloak_l", Vec(32.96, 21.5, 47.71))
            assertTrue(carpal.y() - origin.y() < 0.9 && carpal.z() > origin.z() + 1.5 &&
                kotlin.math.abs(carpal.x() - origin.x()) > 1.2, "planted carpal $carpal vs $origin")
        } finally {
            handler.destroy(); model.destroy(); arena.dispose()
        }
    }

    @Test
    fun `the model walks while its carrier moves and stands when it stops`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("shade", 1)
        val view = BossModelView("shade_wyrm", arena.instance, arena.bossSpawn, definition.enragedBones, definition.foldedBones)
        try {
            var at = arena.bossSpawn
            repeat(10) { at = at.add(0.0, 0.0, .13); view.tick(at, it * 50L) }
            assertEquals("walk", view.currentLoop)
            repeat(5) { view.tick(at, 600L + it * 50L) }
            assertEquals("idle", view.currentLoop)
        } finally {
            view.dispose(); arena.dispose()
        }
    }

    @Test
    fun `moving sideways while facing ahead plays the strafe, to the carrier's right as strafe_l`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("shade", 1)
        val view = BossModelView("shade_wyrm", arena.instance, arena.bossSpawn.withView(0f, 0f), definition.enragedBones, definition.foldedBones)
        try {
            assertTrue(view.canManeuver)
            // Yaw 0 faces +Z; the carrier's right is -X.
            var at = arena.bossSpawn.withView(0f, 0f)
            repeat(6) { at = at.add(-.08, 0.0, 0.0); view.tick(at, it * 50L) }
            assertEquals("strafe_l", view.currentLoop)
            repeat(6) { at = at.add(.08, 0.0, 0.0); view.tick(at, 400L + it * 50L) }
            assertEquals("strafe_r", view.currentLoop)
        } finally {
            view.dispose(); arena.dispose()
        }
    }

    @Test
    fun `the wyrm moves round its target between attacks`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("shade", 1)
        val connection = MemoryConnection()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "ShadeMove"))
        connection.player = player
        player.gameMode = GameMode.ADVENTURE
        player.setInstance(arena.instance, arena.bossSpawn.add(0.0, 0.0, 6.5)).join()
        val combat = QuestEncounterCombat(arena.instance, 1, emptyList(), arena.bossSpawn,
            { _, _ -> }, { _, _ -> }, explicitBossArchetype = arena.archetype)
        try {
            val boss = combat.entities().single()
            var now = 1_000L
            // Openings after each move (2026-10-19) leave fewer moves, so fewer chances to move after one: watch 40 s.
            repeat(800) { now += 50; combat.tick(now); MinecraftServer.getSchedulerManager().processTick() }
            assertTrue(combat.maneuverCount(boss.uuid) > 0, "no maneuver in 40 s")
        } finally {
            combat.dispose(); player.remove(); arena.dispose()
        }
    }

    @Test
    fun `scripted abilities never come up at random and start by name`() {
        val manager = MobAbilityManager(definition.abilities, kotlin.random.Random(3))
        val turn = definition.abilities.single { it.id == "shade-turnsweep" }
        assertTrue(turn.scripted)
        var now = 0L
        repeat(200) {
            now += 10_000
            val started = manager.tryStart(now, Pos(0.0, 64.0, 0.0), Pos(0.0, 64.0, 5.0))
            assertTrue(started?.frame?.ability?.id != "shade-turnsweep")
            manager.cancel()
        }
        val forced = assertNotNull(manager.tryStart(now + 10_000, Pos(0.0, 64.0, 0.0), Pos(0.0, 64.0, -5.0), only = "shade-turnsweep"))
        assertEquals("shade-turnsweep", forced.frame.ability.id)
    }

    @Test
    fun `the dive carries the wyrm onto a far player`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("shade", 1)
        val connection = MemoryConnection()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "ShadeDive"))
        connection.player = player
        player.gameMode = GameMode.ADVENTURE
        val spot = arena.bossSpawn.add(0.0, 0.0, 17.0)
        player.setInstance(arena.instance, spot).join()
        val combat = QuestEncounterCombat(arena.instance, 1, emptyList(), arena.bossSpawn,
            { _, _ -> }, { _, _ -> }, explicitBossArchetype = arena.archetype)
        try {
            val boss = combat.entities().single()
            var now = 1_000L
            var closest = Double.MAX_VALUE
            repeat(120) {
                now += 50; combat.tick(now); MinecraftServer.getSchedulerManager().processTick()
                closest = minOf(closest, boss.position.distance(spot))
            }
            assertTrue(closest < 2.0, "the dive should land on the player: closest ${"%.1f".format(closest)} blocks")
        } finally {
            combat.dispose(); player.remove(); arena.dispose()
        }
    }

    @Test
    fun `arena fight goes through the glowing second form, then the enrage, and plays out its death`() {
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
            assertTrue(combat.applyEffectDamage(boss.uuid, player, combat.bossMaxHealth() * .32))
            // A leap in progress lands first, then it roars (it never roars in mid-air).
            var t = 100L
            while (!combat.isAwakened(boss.uuid) && t < 4000) { combat.tick(t); t += 50 }
            assertTrue(combat.isAwakened(boss.uuid))
            assertFalse(combat.isEnraged(boss.uuid))
            assertEquals(0, combat.groundDisplayCount, "the second-form roar holds every attack")
            t += 1400; combat.tick(t)
            assertTrue(combat.isGlowing(boss.uuid), "the glowing texture comes in at the roar")
            assertTrue(combat.applyEffectDamage(boss.uuid, player, combat.bossMaxHealth() * .35))
            while (!combat.isEnraged(boss.uuid) && t < 12_000) { t += 50; combat.tick(t) }
            assertTrue(combat.isEnraged(boss.uuid))
            assertEquals(0, combat.groundDisplayCount, "the roar holds every attack")
            val mountsBefore = connection.passengerPackets
            t += 1400; combat.tick(t)
            assertTrue(connection.passengerPackets > mountsBefore, "the rage bones shown mid-clip must be mounted on the model root")
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

    private fun testPlayer(arena: dev.projects.server.coreloop.adventure.BossArena, at: Pos, name: String): Player {
        val connection = MemoryConnection()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), name))
        connection.player = player
        player.gameMode = GameMode.ADVENTURE
        player.setInstance(arena.instance, at).join()
        return player
    }

    @Test
    fun `dust erosion builds up, erupts at full and attacking relieves it`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("shade", 1)
        val player = testPlayer(arena, arena.bossSpawn.add(0.0, 0.0, 6.0), "Erode")
        val gimmicks = ShadeWyrmGimmicks(1, ShadeWyrmShaderFx(arena.instance), kotlin.random.Random(1))
        var hurt = 0.0
        try {
            repeat(4) { gimmicks.playerHit(player, "shade-dust", 1000L) }
            assertEquals(80.0, gimmicks.erosionOf(player.uuid))
            gimmicks.playerStruck(player)
            assertEquals(78.0, gimmicks.erosionOf(player.uuid))
            gimmicks.playerHit(player, "shade-sweep", 1000L)                  // not a dust attack
            assertEquals(78.0, gimmicks.erosionOf(player.uuid))
            repeat(2) { gimmicks.playerHit(player, "shade-breath", 1100L) }
            gimmicks.tick(1150L, listOf(player)) { _, amount, _ -> hurt += amount }
            assertEquals(ShadeWyrmGimmicks.ERUPT_DAMAGE, hurt)
            assertEquals(0.0, gimmicks.erosionOf(player.uuid))
        } finally {
            gimmicks.dispose(); player.remove(); arena.dispose()
        }
    }

    @Test
    fun `the dust burst leaves pools and the antennae pulse bursts them`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("shade", 1)
        val gimmicks = ShadeWyrmGimmicks(1, ShadeWyrmShaderFx(arena.instance), kotlin.random.Random(2))
        val dust = definition.abilities.single { it.id == "shade-dust" }
        val pulse = definition.abilities.single { it.id == "shade-pulse" }
        val c = arena.bossSpawn
        try {
            gimmicks.abilityHit(MobAbilityFrame(dust, c, Vec(0.0, 0.0, 1.0), MobAbilityPhase.LOCKED, 0L), 1000L)
            assertEquals(4, gimmicks.poolCount)
            gimmicks.abilityHit(MobAbilityFrame(pulse, c, Vec(0.0, 0.0, 1.0), MobAbilityPhase.LOCKED, 0L), 2000L)
            gimmicks.tick(2000L + ShadeWyrmGimmicks.BURST_DELAY_MILLIS - 50, emptyList()) { _, _, _ -> }
            assertEquals(4, gimmicks.poolCount)
            gimmicks.tick(2000L + ShadeWyrmGimmicks.BURST_DELAY_MILLIS, emptyList()) { _, _, _ -> }
            assertEquals(0, gimmicks.poolCount)
        } finally {
            gimmicks.dispose(); arena.dispose()
        }
    }

    @Test
    fun `enraged horns break from the front and each fan tears from its side`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("shade", 1)
        val player = testPlayer(arena, arena.bossSpawn.add(0.0, 0.0, -20.0), "Parts")
        val combat = QuestEncounterCombat(arena.instance, 1, emptyList(), arena.bossSpawn,
            { _, _ -> }, { _, _ -> }, explicitBossArchetype = arena.archetype)
        try {
            val boss = combat.entities().single()
            var now = 1_000L
            combat.tick(now)
            assertTrue(combat.applyEffectDamage(boss.uuid, player, combat.bossMaxHealth() * .66))
            assertTrue(arena.instance.clock(BossArenaFactory.rageClock()).paused())
            repeat(180) { now += 50; combat.tick(now) }                        // the second-form roar, then the enrage roar
            assertTrue(combat.isEnraged(boss.uuid))
            assertFalse(arena.instance.clock(BossArenaFactory.rageClock()).paused())   // the arena turns violet
            fun hitFrom(offset: Vec, share: Double) {
                val yaw = Math.toRadians(boss.position.yaw().toDouble())
                val ahead = Vec(-kotlin.math.sin(yaw), 0.0, kotlin.math.cos(yaw))
                val right = Vec(-kotlin.math.cos(yaw), 0.0, -kotlin.math.sin(yaw))
                player.teleport(boss.position.add(ahead.mul(offset.z())).add(right.mul(offset.x()))).join()
                assertTrue(combat.applyEffectDamage(boss.uuid, player, combat.bossMaxHealth() * share))
            }
            hitFrom(Vec(0.0, 0.0, 6.0), .121)
            assertEquals(listOf(true, false, false), combat.partsBroken(boss.uuid))
            hitFrom(Vec(5.0, 0.0, 0.0), .101)
            assertEquals(listOf(true, true, false), combat.partsBroken(boss.uuid))
            hitFrom(Vec(-5.0, 0.0, 0.0), .101)
            assertEquals(listOf(true, true, true), combat.partsBroken(boss.uuid))
        } finally {
            combat.dispose(); player.remove(); arena.dispose()
        }
    }

    @Test
    fun `the shade nest holds its violet timeline at the calm end and pools light the floor until they burst`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("shade", 1)
        // The violet comes from the rage timeline on its own clock, held at 0 (the ordinary night) until the enrage.
        val rage = requireNotNull(MinecraftServer.getTimelineRegistry().get(BossArenaFactory.SHADE_RAGE))
        val tint = requireNotNull(rage.tracks()[net.minestom.server.world.attribute.EnvironmentAttribute.BLOCK_LIGHT_TINT])
        assertEquals(net.kyori.adventure.text.format.TextColor.color(BossArenaFactory.SHADE_LIGHT), tint.keyframes().last().value())
        assertEquals(BossArenaFactory.RAGE_FADE_TICKS, tint.keyframes().last().ticks())
        assertTrue(arena.instance.clock(BossArenaFactory.rageClock()).paused())
        assertEquals(0L, arena.instance.clock(BossArenaFactory.rageClock()).time())
        val lights = ShadeWyrmLights(arena.instance)
        val gimmicks = ShadeWyrmGimmicks(1, ShadeWyrmShaderFx(arena.instance), kotlin.random.Random(2), lights)
        val dust = definition.abilities.single { it.id == "shade-dust" }
        val pulse = definition.abilities.single { it.id == "shade-pulse" }
        val c = arena.bossSpawn
        fun lit() = lights.snapshot().keys.count { arena.instance.getBlock(it).compare(net.minestom.server.instance.block.Block.LIGHT) }
        try {
            gimmicks.abilityHit(MobAbilityFrame(dust, c, Vec(0.0, 0.0, 1.0), MobAbilityPhase.LOCKED, 0L), 1000L)
            assertTrue(lit() in 1..4)                                         // two pools may share a block
            gimmicks.abilityHit(MobAbilityFrame(pulse, c, Vec(0.0, 0.0, 1.0), MobAbilityPhase.LOCKED, 0L), 2000L)
            val burst = 2000L + ShadeWyrmGimmicks.BURST_DELAY_MILLIS
            lights.tick(burst); gimmicks.tick(burst, emptyList()) { _, _, _ -> }
            assertTrue(lights.snapshot().values.all { it == 14 })             // the burst flashes brighter
            lights.tick(burst + 600)
            assertEquals(0, lit())
            assertTrue(lights.snapshot().isEmpty())
        } finally {
            gimmicks.dispose(); lights.dispose(); arena.dispose()
        }
    }

    @Test
    fun `players in the arena get the glow post effect while the wyrm lives`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("shade", 1)
        val player = testPlayer(arena, arena.bossSpawn.add(0.0, 0.0, -20.0), "Glow")
        val connection = player.playerConnection as MemoryConnection
        val combat = QuestEncounterCombat(arena.instance, 1, emptyList(), arena.bossSpawn,
            { _, _ -> }, { _, _ -> }, explicitBossArchetype = arena.archetype)
        try {
            combat.tick(1_000L); combat.tick(1_050L)
            assertEquals(listOf(listOf(QuestEncounterCombat.GLOW)), connection.postEffects)
            combat.dispose()
            assertEquals(listOf(listOf(QuestEncounterCombat.GLOW), emptyList()), connection.postEffects)
        } finally {
            combat.dispose(); player.remove(); arena.dispose()
        }
    }

    @Test
    fun `hits land on the head, wings and tail through spheres that follow the bones`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("shade", 1)
        val combat = QuestEncounterCombat(arena.instance, 1, emptyList(), arena.bossSpawn,
            { _, _ -> }, { _, _ -> }, explicitBossArchetype = arena.archetype)
        try {
            combat.tick(1_000L); combat.tick(1_050L)
            val target = combat.combatTargets().single()
            assertEquals(ShadeWyrmHitbox.BODY.size + ShadeWyrmHitbox.WINGS.size, target.parts.size)
            val head = target.parts[ShadeWyrmHitbox.BODY.indexOfFirst { it.bone == "skull" }]
            val boss = combat.entities().single().position
            assertTrue(head.center.distance(boss) > 3.0, "the head is well ahead of the carrier: ${head.center} vs $boss")
            // Aimed from 10 blocks off at the head, a wing tip and the tail end: each ray finds the body.
            for (part in listOf(head, target.parts.last(), target.parts[ShadeWyrmHitbox.BODY.indexOfFirst { it.bone == "tail_end" }])) {
                val eye = part.center.add(Vec(7.0, 2.0, 7.0))
                val dir = Vec.fromPoint(part.center.sub(eye)).normalize()
                assertNotNull(target.parts.rayHit(eye, dir, 24.0, .65), "ray at ${part.center}")
                // Turned away from it, it misses.
                assertEquals(null, target.parts.rayHit(eye, dir.mul(-1.0), 24.0, .65))
            }
            // A sword swing beside the tail reaches it although the carrier is ~7 blocks away.
            val tail = target.parts[ShadeWyrmHitbox.BODY.indexOfFirst { it.bone == "tail_end" }].center
            val feet = Vec(tail.x() + 3.0, boss.y(), tail.z())
            assertTrue(target.parts.meleeHit(feet, Vec(-1.0, 0.0, 0.0), 4.5, .4))
            assertFalse(target.parts.meleeHit(feet, Vec(1.0, 0.0, 0.0), 4.5, .4))
        } finally {
            combat.dispose(); arena.dispose()
        }
    }

    @Test
    fun `the triple bite strikes three times and only the last one ends it`() {
        val bite = definition.abilities.single { it.id == "shade-bite" }
        val manager = MobAbilityManager(listOf(bite))
        val origin = Pos(0.0, 40.0, 0.0)
        assertNotNull(manager.tryStart(0L, origin, origin.add(0.0, 0.0, 3.0), only = "shade-bite"))
        val hits = mutableListOf<Long>()
        var now = 0L
        while (manager.isActive) {
            now += 50
            manager.tick(now, origin.add(0.0, 0.0, 3.0)).forEach { if (it is MobAbilityEvent.Hit) hits += now }
        }
        assertEquals(listOf(700L, 1150L, 1600L), hits)
        assertEquals(bite.telegraphMillis + bite.recoveryMillis, now)
    }

    @Test
    fun `after a move it stands open, longer when it whiffed or only met a dodge`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("shade", 1)
        val player = testPlayer(arena, arena.bossSpawn.add(0.0, 0.0, -4.0), "Open")
        var dodging = false
        val combat = QuestEncounterCombat(arena.instance, 1, emptyList(), arena.bossSpawn,
            { _, _ -> }, { _, _ -> }, explicitBossArchetype = arena.archetype, evading = { dodging })
        try {
            val boss = combat.entities().single()
            var now = 1_000L
            combat.tick(now)
            fun moveFrom(id: String, distance: Double): Long {
                now += 5_000; combat.tick(now)
                // Set the scene after the free AI's tick (it may have flown off), then start the bite from it.
                boss.teleport(arena.bossSpawn).join()
                player.teleport(arena.bossSpawn.add(0.0, 0.0, -distance)).join()
                assertTrue(combat.startAbilityForTest(boss.uuid, id, now))
                var guard = 0
                while (combat.busy(boss.uuid) && guard++ < 200) { now += 50; combat.tick(now) }
                return combat.openFor(boss.uuid)
            }
            val open = QuestEncounterCombat.OPENING.getValue("shade-bite")
            assertEquals(open, moveFrom("shade-bite", 3.0), "a bite that lands leaves its plain opening")
            // The bite lunges ~10 blocks; the sweep (7 blocks, no lunge) shows a whiff on someone close but out of reach.
            assertEquals(QuestEncounterCombat.OPENING.getValue("shade-sweep") + QuestEncounterCombat.WHIFF_MILLIS, moveFrom("shade-sweep", 9.5),
                "a move that reaches nobody near it whiffs")
            dodging = true
            assertEquals(open + QuestEncounterCombat.WHIFF_MILLIS, moveFrom("shade-bite", 3.0), "a bite that only met a dodge whiffs too")
            dodging = false
            val before = boss.position
            now += 300; combat.tick(now)
            assertEquals(before, boss.position, "it stands still while open")
        } finally {
            combat.dispose(); player.remove(); arena.dispose()
        }
    }

    @Test
    fun `a break skill on the white flash of a heavy wind-up knocks the wyrm down`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("shade", 1)
        val player = testPlayer(arena, arena.bossSpawn.add(0.0, 0.0, -8.0), "Counter")
        val combat = QuestEncounterCombat(arena.instance, 1, emptyList(), arena.bossSpawn,
            { _, _ -> }, { _, _ -> }, explicitBossArchetype = arena.archetype)
        try {
            val boss = combat.entities().single()
            var now = 1_000L
            combat.tick(now)
            assertTrue(combat.startAbilityForTest(boss.uuid, "shade-pounce", now))
            val start = now
            now += 100; combat.tick(now)
            assertTrue(combat.applyCrowdControl(boss.uuid, CrowdControl.FLINCH, 300, player))
            assertFalse(combat.isDown(boss.uuid), "before the flash a break skill only wears the gauge")
            while (now < start + 700) { now += 50; combat.tick(now) }
            assertTrue(combat.applyCrowdControl(boss.uuid, CrowdControl.FLINCH, 300, player))
            assertTrue(combat.isDown(boss.uuid), "on the flash it is a counter")
            assertFalse(combat.busy(boss.uuid))
        } finally {
            combat.dispose(); player.remove(); arena.dispose()
        }
    }

    @Test
    fun `the head takes more and the tail less, and break skills wear the cast down`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("shade", 1)
        val player = testPlayer(arena, arena.bossSpawn.add(0.0, 0.0, -8.0), "Weak")
        val combat = QuestEncounterCombat(arena.instance, 1, emptyList(), arena.bossSpawn,
            { _, _ -> }, { _, _ -> }, explicitBossArchetype = arena.archetype)
        try {
            val boss = combat.entities().single()
            val max = combat.bossMaxHealth()
            var now = 1_000L
            combat.tick(now)
            fun hitFrom(offset: Vec): Double {
                val yaw = Math.toRadians(boss.position.yaw().toDouble())
                val ahead = Vec(-kotlin.math.sin(yaw), 0.0, kotlin.math.cos(yaw))
                val right = Vec(-kotlin.math.cos(yaw), 0.0, -kotlin.math.sin(yaw))
                player.teleport(boss.position.add(ahead.mul(offset.z())).add(right.mul(offset.x()))).join()
                val before = combat.healthOf(boss.uuid)!!
                assertNotNull(combat.applyDamageAmount(boss.uuid, player, 10.0))
                return before - combat.healthOf(boss.uuid)!!
            }
            assertEquals(10.0 * QuestEncounterCombat.HEAD_MULTIPLIER, hitFrom(Vec(0.0, 0.0, 6.0)), 1e-6)
            assertTrue(combat.takeWeakSpot(player), "a head hit is flagged for the damage number")
            assertFalse(combat.takeWeakSpot(player), "the flag is read once")
            assertEquals(10.0, hitFrom(Vec(5.0, 0.0, 0.0)), 1e-6)
            assertEquals(10.0 * QuestEncounterCombat.TAIL_MULTIPLIER, hitFrom(Vec(0.0, 0.0, -6.0)), 1e-6)
            assertFalse(combat.takeWeakSpot(player))

            player.teleport(arena.bossSpawn.add(0.0, 0.0, -8.0)).join()
            assertTrue(combat.applyEffectDamage(boss.uuid, player, max * .32))           // the second form
            var guard = 0
            while (combat.gimmickBars()?.first != QuestEncounterCombat.CHANNEL && guard++ < 600) { now += 50; combat.tick(now) }
            assertEquals(QuestEncounterCombat.CHANNEL, combat.gimmickBars()?.first)
            val hits = kotlin.math.ceil(QuestEncounterCombat.CAST_SHARE / QuestEncounterCombat.CC_CAST_HARD).toInt()
            repeat(hits - 1) { assertTrue(combat.applyCrowdControl(boss.uuid, CrowdControl.STUN, 1000, player)) }
            assertFalse(combat.isDown(boss.uuid))
            assertEquals(QuestEncounterCombat.CHANNEL, combat.gimmickBars()?.first, "crowd control never cancels the cast by itself")
            combat.applyCrowdControl(boss.uuid, CrowdControl.STUN, 1000, player)
            assertTrue(combat.isDown(boss.uuid), "enough break skills during the cast interrupt it")
        } finally {
            combat.dispose(); player.remove(); arena.dispose()
        }
    }

    @Test
    fun `hitting the cast hard enough knocks the wyrm down, and the shell soaks damage until it breaks`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("shade", 1)
        val player = testPlayer(arena, arena.bossSpawn.add(0.0, 0.0, -8.0), "Gimmick")
        val combat = QuestEncounterCombat(arena.instance, 1, emptyList(), arena.bossSpawn,
            { _, _ -> }, { _, _ -> }, explicitBossArchetype = arena.archetype)
        try {
            val boss = combat.entities().single()
            val max = combat.bossMaxHealth()
            var now = 1_000L
            combat.tick(now)
            assertTrue(combat.applyEffectDamage(boss.uuid, player, max * .32))           // the second form
            var guard = 0
            while (combat.gimmickBars()?.first != QuestEncounterCombat.CHANNEL && guard++ < 600) { now += 50; combat.tick(now) }
            assertEquals(QuestEncounterCombat.CHANNEL, combat.gimmickBars()?.first, "the cast comes ${QuestEncounterCombat.CHANNEL_FIRST_MILLIS} ms after waking")
            assertFalse(combat.isDown(boss.uuid))
            assertTrue(combat.applyEffectDamage(boss.uuid, player, max * QuestEncounterCombat.CAST_SHARE * 1.05))
            assertTrue(combat.isDown(boss.uuid), "enough damage during the cast interrupts it")
            assertEquals(null, combat.gimmickBars())
            now += QuestEncounterCombat.DOWN_MILLIS + 50; combat.tick(now)
            assertFalse(combat.isDown(boss.uuid))

            assertTrue(combat.applyEffectDamage(boss.uuid, player, max * .35))           // the enrage
            guard = 0
            while (combat.gimmickBars()?.first != QuestEncounterCombat.SHELL && guard++ < 900) { now += 50; combat.tick(now) }
            assertEquals(QuestEncounterCombat.SHELL, combat.gimmickBars()?.first)
            // It may have flown or leapt during the enrage: strike from its side, in reach (no head multiplier).
            run {
                val yaw = Math.toRadians(boss.position.yaw().toDouble())
                player.teleport(boss.position.add(Vec(-kotlin.math.cos(yaw), 0.0, -kotlin.math.sin(yaw)).mul(4.0))).join()
            }
            val before = combat.healthOf(boss.uuid)
            assertNotNull(combat.applyDamageAmount(boss.uuid, player, max * .02))
            assertEquals(before, combat.healthOf(boss.uuid), "the shell takes the hit")
            assertNotNull(combat.applyDamageAmount(boss.uuid, player, max * .06))
            assertTrue(combat.isDown(boss.uuid), "the shell broke")
        } finally {
            combat.dispose(); player.remove(); arena.dispose()
        }
    }

    @Test
    fun `the pounce carries the wyrm onto the player and the small attacks show no floor`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("shade", 1)
        val c = arena.bossSpawn
        val player = testPlayer(arena, c.add(0.0, 0.0, -9.0), "Pounce")
        val combat = QuestEncounterCombat(arena.instance, 1, emptyList(), c,
            { _, _ -> }, { _, _ -> }, explicitBossArchetype = arena.archetype)
        try {
            val boss = combat.entities().single()
            var now = 1_000L
            combat.tick(now)
            val start = boss.position
            assertTrue(combat.startAbilityForTest(boss.uuid, "shade-pounce", now), "the pounce starts")
            var floor = 0
            repeat(30) { now += 50; combat.tick(now); floor = maxOf(floor, combat.groundDisplayCount) }
            val moved = horizontalLength(boss.position.sub(start))
            assertTrue(moved > 4.0, "the pounce leaps at the player (moved $moved)")
            assertTrue(boss.position.distance(player.position) < start.distance(player.position) - 3.0, "closer: ${boss.position} vs ${start} player ${player.position}")
            assertEquals(0, floor, "the pounce is read from the wind-up, not the floor")
        } finally {
            combat.dispose(); player.remove(); arena.dispose()
        }
    }

    /**
     * `SHADE_FIGHT_DUMP=<file> ./gradlew :server-minestom:test --tests '*ShadeWyrmBossTest' --rerun` runs a scripted
     * 75 s fight through the real encounter code and writes, per tick, where the wyrm is, where it looks, the clip on
     * show and the shader displays, for scripts/boss_preview/fightclip.py. The player: in front, then running round it,
     * far away, inside its reach, enrages it, inside its reach again, mid range.
     */
    @Test
    fun `dump a scripted fight for the preview`() {
        val path = System.getenv("SHADE_FIGHT_DUMP") ?: return
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("shade", 1)
        val connection = MemoryConnection()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "ShadeFight"))
        connection.player = player
        player.gameMode = GameMode.ADVENTURE
        val c = arena.bossSpawn
        player.setInstance(arena.instance, c.add(0.0, 0.0, -6.0)).join()
        val combat = QuestEncounterCombat(arena.instance, 1, emptyList(), arena.bossSpawn,
            { _, _ -> }, { _, _ -> }, explicitBossArchetype = arena.archetype)
        val lines = mutableListOf<String>()
        try {
            val boss = combat.entities().single()
            var now = 1_000L
            for (tick in 0 until 1500) {
                val t = tick * .05
                val b = boss.position
                val facing = Math.toRadians((combat.modelFacing(boss.uuid) ?: 0f).toDouble())
                val ahead = Vec(-kotlin.math.sin(facing), 0.0, kotlin.math.cos(facing))
                val want = when {
                    t < 6 -> c.add(0.0, 0.0, -6.0)
                    t < 11 -> b.add(kotlin.math.cos(t * 2.1) * 4.5, 0.0, kotlin.math.sin(t * 2.1) * 4.5)   // run round it
                    t < 18 -> c.add(0.0, 0.0, -17.0)                      // far away
                    t < 22 -> b.add(ahead.mul(2.5))                       // inside its reach
                    t < 27 -> c.add(0.0, 0.0, -8.0)
                    t < 33 -> b.add(ahead.mul(2.5))
                    t < 36.5 -> b.add(Vec(-kotlin.math.cos(facing), 0.0, -kotlin.math.sin(facing)).mul(4.5))   // its right side
                    t < 40 -> b.add(Vec(kotlin.math.cos(facing), 0.0, kotlin.math.sin(facing)).mul(4.5))       // its left side
                    t < 48 -> b.add(ahead.mul(-4.5))                      // at its back
                    else -> c.add(kotlin.math.cos(t * .4) * 9.0, 0.0, kotlin.math.sin(t * .4) * 9.0)   // circling at mid range
                }
                player.teleport(Pos(want.x(), c.y(), want.z())).join()
                player.health = 20f
                if (tick == 200) combat.applyEffectDamage(boss.uuid, player, combat.bossMaxHealth() * .32)   // the second form
                if (tick == 560) combat.applyEffectDamage(boss.uuid, player, combat.bossMaxHealth() * .35)   // the enrage
                if (t >= 27.5 && tick % 10 == 0) combat.applyEffectDamage(boss.uuid, player, combat.bossMaxHealth() * .006)   // it fights back
                now += 50; combat.tick(now); boss.tick(now); MinecraftServer.getSchedulerManager().processTick()
                arena.instance.clock(BossArenaFactory.rageClock()).run { if (!paused()) time(time() + 1) }   // the instance tick's job
                val (clip, since) = combat.shownClip(boss.uuid) ?: ("idle" to now)
                val shader = combat.shaderViews().joinToString(",") { v ->
                    "{\"sprite\":\"${v.sprite}\",\"kind\":${v.kind},\"progress\":${v.progress},\"at\":[${v.at.x() - c.x()},${v.at.y() - c.y()},${v.at.z() - c.z()}]," +
                        "\"scale\":[${v.scale.x()},${v.scale.y()},${v.scale.z()}],\"translation\":[${v.translation.x()},${v.translation.y()},${v.translation.z()}]," +
                        "\"rotation\":[${v.rotation.joinToString(",")}],\"billboard\":${v.billboard}}"
                }
                val p = player.position
                lines += "{\"t\":${"%.2f".format(t + .05)},\"boss\":[${boss.position.x() - c.x()},${boss.position.y() - c.y()},${boss.position.z() - c.z()}]," +
                    "\"yaw\":${combat.modelFacing(boss.uuid) ?: 0f},\"clip\":\"$clip\",\"clipT\":${(now - since) / 1000.0}," +
                    "\"player\":[${p.x() - c.x()},${p.z() - c.z()}],\"enraged\":${combat.isEnraged(boss.uuid)}," +
                    "\"rage\":${combat.rageShown()},\"glowing\":${combat.isGlowing(boss.uuid)},\"down\":${combat.isDown(boss.uuid)},\"bars\":${combat.gimmickBars()?.let { (id, left, gauge) -> "[\"$id\",$left,$gauge]" } ?: "null"},\"broken\":[${combat.partsBroken(boss.uuid).joinToString(",")}],\"lights\":[${combat.lightsOn().entries.joinToString(",") { (b, l) -> "[${b.x() - c.blockX()},${b.y() - c.blockY()},${b.z() - c.blockZ()},$l]" }}],\"erosion\":${combat.erosionOf(player.uuid)},\"shader\":[$shader]}"
            }
        } finally {
            combat.dispose(); player.remove(); arena.dispose()
        }
        java.io.File(path).writeText(lines.joinToString("\n"))
    }

    private class MemoryConnection : PlayerConnection() {
        var passengerPackets = 0
        val postEffects = mutableListOf<List<net.kyori.adventure.key.Key>>()
        override fun sendPacket(packet: SendablePacket) {
            if (packet is net.minestom.server.network.packet.server.common.PostEffectsPacket) postEffects += packet.postEffects()
            if (packet is net.minestom.server.network.packet.server.play.SetPassengersPacket) passengerPackets++
        }
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }
}
