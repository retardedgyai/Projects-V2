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
        assertEquals(15, definition.abilities.size)
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
        // movement between attacks: a leap back, a dash to either side and a sideways prowl (BossModelView.canManeuver)
        for (once in listOf("hop_back", "dash_l", "dash_r", "hop_back_enraged", "dash_l_enraged", "dash_r_enraged"))
            assertFalse(clips.getAsJsonObject(once)["loop"].asBoolean, once)
        // and its own moves between attack chains: a growl, a shake, a paw at the ground, a low creep (once clips)
        for ((kind, millis) in QuestEncounterCombat.SAKURA_STAND_MILLIS + (ManeuverKind.STALK to 1600L)) for (tag in listOf("", "_enraged")) {
            val clip = clips.getAsJsonObject(kind.name.lowercase() + tag)
            assertFalse(clip["loop"].asBoolean, kind.name)
            assertEquals(millis / 1000.0, clip["animation_length"].asDouble, 0.051, kind.name)
        }
        for (loop in listOf("strafe_l", "strafe_r", "strafe_l_enraged", "strafe_r_enraged")) assertTrue(clips.getAsJsonObject(loop)["loop"].asBoolean, loop)
        // 三連桜爪 follow-ups only start as a chain
        assertTrue(definition.abilities.filter { it.id.startsWith("sakura-claw") && it.id != "sakura-claw" }.all { it.scripted })
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
            // that blow also broke a part (horns from in front, tail shells from behind): the stagger holds it, then it enrages
            assertTrue(combat.partsTorn(boss.uuid)!!.let { it.first || it.second })
            combat.tick(3300)
            assertTrue(combat.isEnraged(boss.uuid))
            combat.tick(4800)
            combat.tick(9000)
            // it cannot die before its 千本桜 (held at a quarter); let that play out, then finish it
            assertTrue(combat.applyEffectDamage(boss.uuid, player, 99_999.0))
            var now = 9050L
            while (combat.finaleState(boss.uuid)?.let { !it.first || it.second } == true && now < 40_000L) { combat.tick(now); now += 50 }
            assertTrue(combat.applyEffectDamage(boss.uuid, player, 99_999.0))
            assertEquals(1, victories)
        } finally {
            combat.dispose(); player.remove(); arena.dispose()
        }
    }

    @Test
    fun `between attack chains it rests, moving round its prey and throwing small blows on the move`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("sakura", 1)
        val connection = MemoryConnection()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "SakuraRest"))
        connection.player = player
        player.gameMode = GameMode.ADVENTURE
        player.setInstance(arena.instance, arena.bossSpawn.sub(0.0, 0.0, 6.0)).join()
        val combat = QuestEncounterCombat(arena.instance, 1, emptyList(), arena.bossSpawn,
            { _, _ -> }, { _, _ -> }, explicitBossArchetype = arena.archetype)
        try {
            val boss = combat.entities().single()
            var now = 1_000L
            var attackTicks = 0
            val moves = mutableSetOf<String>()
            val light = mutableSetOf<String>()
            repeat(1200) {                                              // 60 s
                now += 50; combat.tick(now); MinecraftServer.getSchedulerManager().processTick()
                val clip = combat.shownClip(boss.uuid)?.first ?: return@repeat
                if (clip.startsWith("sakura-")) attackTicks++
                if (clip.removeSuffix("_enraged") in QuestEncounterCombat.SAKURA_LIGHT) light += clip
                else if (clip in setOf("growl", "shake", "scrape", "stalk", "hop_back", "dash_l", "dash_r")) moves += clip
            }
            assertTrue(attackTicks < 1200 * .6, "attacking ${attackTicks * 50} ms of 60 s")
            assertTrue(attackTicks > 0, "never attacked")
            assertTrue(moves.size >= 3, "moves between attacks: $moves")
            assertTrue(light.size >= 2, "small blows on the move: $light")
        } finally {
            combat.dispose(); player.remove(); arena.dispose()
        }
    }

    @Test
    fun `petals fly the way the mirrored model in game moves`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("sakura", 1)
        assertTrue(BossModelView.available("sakura_fang"))
        val at = arena.bossSpawn.withView(0f, 0f)                    // facing +Z: its right is -X
        val view = BossModelView("sakura_fang", arena.instance, at, emptySet(), emptySet())
        try {
            view.tick(at, 0)
            // WSEE mirrors Blockbench X: the model's +X paw (hand_l) stands on the beast's right
            assertTrue(view.point("hand_l", net.minestom.server.coordinate.Vec(15.0, 1.0, 31.0))!!.x() < at.x())
            fun sense(p: net.minestom.server.coordinate.Point, v: net.minestom.server.coordinate.Vec) = (p.z() - at.z()) * v.x() - (p.x() - at.x()) * v.z()
            // 花尾の旋回: the thrown petals turn the same way round as the tail sweeping through the hit
            val fx = SakuraPetalFx(arena.instance)
            view.ability("sakura-spin"); fx.start("sakura-spin", 1.0)
            var tail = view.point("tail_5", net.minestom.server.coordinate.Vec(0.0, 78.0, -43.0))!!
            var tailSense = 0.0
            for (tick in 1..24) {
                view.tick(at, tick * 50L); MinecraftServer.getSchedulerManager().processTick()
                fx.tick(at, false) { b, px -> view.point(b, px) }
                val next = view.point("tail_5", net.minestom.server.coordinate.Vec(0.0, 78.0, -43.0))!!
                if (tick in 16..22) tailSense += sense(tail, net.minestom.server.coordinate.Vec(next.x() - tail.x(), 0.0, next.z() - tail.z()))
                tail = next
            }
            val petalSense = fx.snapshot().sumOf { (p, v) -> sense(p, v) }
            assertTrue(tailSense * petalSense > 0, "tail $tailSense petals $petalSense")
            fx.dispose()
            // 踏み込み爪: the paw rakes across to its right, and so does the fan of petals
            val rake = SakuraPetalFx(arena.instance)
            rake.start("sakura-lunge", .55)
            repeat(14) { rake.tick(at, false) { _, _ -> null } }
            assertTrue(rake.snapshot().sumOf { (_, v) -> v.x() } < 0, "the rake's petals fly to its right (-X)")
            rake.dispose()
        } finally {
            view.dispose(); arena.dispose()
        }
    }

    @Test
    fun `a hit from in front in the counter window throws it out of its strike, and parts break`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("sakura", 1)
        val connection = MemoryConnection()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "SakuraCounter"))
        connection.player = player
        player.gameMode = GameMode.ADVENTURE
        player.setInstance(arena.instance, arena.bossSpawn.add(0.0, 0.0, 3.5)).join()
        val combat = QuestEncounterCombat(arena.instance, 1, emptyList(), arena.bossSpawn, { _, _ -> }, { _, _ -> }, explicitBossArchetype = arena.archetype)
        try {
            val boss = combat.entities().single()
            var now = 1000L
            var window: LongRange? = null
            while (now < 60_000L && window == null) {
                player.teleport(boss.position.add(boss.position.direction().mul(3.0)).withY(boss.position.y()))
                combat.tick(now)
                window = combat.counterWindow(boss.uuid)?.takeIf { now in it }   // damage below lands at this tick's time
                now += 50
            }
            assertNotNull(window, "a counterable strike opened its window")
            assertTrue(combat.applyEffectDamage(boss.uuid, player, 1.0))
            assertTrue(combat.isBroken(boss.uuid), "countered: it is down and takes more damage")
            // the horns break after enough damage from in front
            repeat(20) { combat.applyEffectDamage(boss.uuid, player, combat.bossMaxHealth() * .01) }
            assertEquals(true, combat.partsTorn(boss.uuid)?.first)
        } finally {
            combat.dispose(); player.remove(); arena.dispose()
        }
    }

    @Test
    fun `桜紋の陣 backfires when every crest is held and blasts the party when one is empty`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("sakura", 1)
        val connection = MemoryConnection()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "SakuraSigil"))
        connection.player = player
        player.gameMode = GameMode.ADVENTURE
        player.setInstance(arena.instance, arena.bossSpawn.add(0.0, 0.0, 6.0)).join()
        var hurt = 0.0
        val combat = QuestEncounterCombat(arena.instance, 1, emptyList(), arena.bossSpawn, { _, _ -> }, { _, amount -> hurt += amount },
            explicitBossArchetype = arena.archetype)
        try {
            val boss = combat.entities().single()
            var now = 1000L
            combat.tick(now)
            // enrage from behind (no part break from in front), then wait for the sigil
            player.teleport(boss.position.sub(boss.position.direction().mul(5.0)))
            assertTrue(combat.applyEffectDamage(boss.uuid, player, combat.bossMaxHealth() * .52))
            var crests = emptyList<net.minestom.server.coordinate.Pos>()
            while (now < 80_000L && crests.isEmpty()) { now += 50; combat.tick(now); crests = combat.sigilCrests(boss.uuid) }
            assertEquals(1, crests.size, "one crest per player")
            // first one: stand on it -> it backfires
            player.teleport(crests[0].add(0.0, .1, 0.0))
            val before = hurt
            while (combat.sigilCrests(boss.uuid).isNotEmpty()) { now += 50; player.teleport(crests[0].add(0.0, .1, 0.0)); combat.tick(now) }
            assertTrue(combat.isBroken(boss.uuid), "held crest: it collapses")
            assertEquals(before, hurt, 1e-9)
            // next one: stand off it -> blasted
            var next = emptyList<net.minestom.server.coordinate.Pos>()
            while (now < 200_000L && next.isEmpty()) { now += 50; combat.tick(now); next = combat.sigilCrests(boss.uuid) }
            assertEquals(1, next.size)
            val hurtBefore = hurt
            while (combat.sigilCrests(boss.uuid).isNotEmpty()) { now += 50; player.teleport(next[0].add(6.0, .1, 6.0)); combat.tick(now) }
            assertTrue(hurt > hurtBefore, "empty crest: the party is blasted")
        } finally {
            combat.dispose(); player.remove(); arena.dispose()
        }
    }

    @Test
    fun `千本桜 at a quarter health cannot be skipped and spares whoever hides behind a pillar`() {
        MinecraftServer.init(Auth.Offline())
        val arena = BossArenaFactory.create("sakura", 1)
        val connection = MemoryConnection()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "SakuraFinale"))
        connection.player = player
        player.gameMode = GameMode.ADVENTURE
        player.setInstance(arena.instance, arena.bossSpawn.add(0.0, 0.0, 6.0)).join()
        var hurt = 0.0
        val combat = QuestEncounterCombat(arena.instance, 1, emptyList(), arena.bossSpawn, { _, _ -> }, { _, amount -> hurt += amount },
            explicitBossArchetype = arena.archetype)
        try {
            val boss = combat.entities().single()
            var now = 1000L
            combat.tick(now)
            player.teleport(boss.position.sub(boss.position.direction().mul(5.0)))
            combat.applyEffectDamage(boss.uuid, player, combat.bossMaxHealth() * .52)       // enrage
            repeat(200) { now += 50; combat.tick(now) }
            combat.applyEffectDamage(boss.uuid, player, combat.bossMaxHealth() * .9)         // far more than is left
            assertTrue(combat.isAlive(boss.uuid), "held at a quarter until 千本桜 starts")
            // hide behind the +X pillar (the arena's pillars stand 19..20.5 out from the middle)
            val hide = arena.bossSpawn.add(21.6, 0.0, 0.0)
            var started = false
            while (now < 120_000L && !started) { now += 50; player.teleport(hide); combat.tick(now); started = combat.finaleState(boss.uuid)?.first == true }
            assertTrue(started)
            val before = hurt
            while (combat.finaleState(boss.uuid)?.second == true) { now += 50; player.teleport(hide); combat.tick(now) }
            assertEquals(before, hurt, 1e-9, "behind the pillar: spared")
            assertTrue(combat.isBroken(boss.uuid), "spent, it collapses")
            assertTrue(combat.applyEffectDamage(boss.uuid, player, 99_999.0))
        } finally {
            combat.dispose(); player.remove(); arena.dispose()
        }
    }

    @Test
    fun `the petal storm follows each ability, stays under its cap and dies away after the death`() {
        MinecraftServer.init(Auth.Offline())
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        java.util.concurrent.CompletableFuture.allOf(*(-2..1).flatMap { x -> (-2..1).map { z -> instance.loadChunk(x, z) } }.toTypedArray()).join()
        try {
            val at = net.minestom.server.coordinate.Pos(0.0, 40.0, 0.0, 0f, 0f)       // yaw 0: facing +Z
            val fx = SakuraPetalFx(instance)
            val none: (String, net.minestom.server.coordinate.Vec) -> net.minestom.server.coordinate.Point? = { _, _ -> null }
            repeat(40) { fx.tick(at, false, none) }
            val idle = fx.liveCount
            assertTrue(idle in 3..40, "idle shedding $idle")
            fx.start("sakura-scatter", 1.7)
            repeat(36) { fx.tick(at, false, none) }                                     // 1.8 s: just after the fling
            assertTrue(fx.liveCount > idle + 40, "fan ${fx.liveCount}")
            assertTrue(fx.liveCount <= SakuraPetalFx.CAP)
            val forward = fx.snapshot().filter { it.second.length() > 10.0 }
            assertTrue(forward.isNotEmpty() && forward.all { it.second.z() > 0 }, "the fan flies toward the facing (+Z)")
            fx.start("sakura-bloom", 2.0)
            repeat(50) { fx.tick(at, true, none) }
            assertTrue(fx.liveCount <= SakuraPetalFx.CAP)
            fx.death()
            repeat(120) { fx.tick(at, true, none) }                                     // 6 s
            assertEquals(0, fx.liveCount)
            fx.dispose()
        } finally {
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
        }
    }

    /** `SAKURA_VFX_DUMP=<file> ./gradlew :server-minestom:test --tests '*SakuraFangBossTest' --rerun` writes the strike
     * effects (SakuraStrikeFx, facing +Z from the origin, blocks) for scripts/boss_preview/sakura_shaderclip.py. */
    @Test
    fun `dump the strike effects for the preview`() {
        val path = System.getenv("SAKURA_VFX_DUMP") ?: return
        MinecraftServer.init(Auth.Offline())
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        for (x in -2..1) for (z in -2..1) instance.loadChunk(x, z).join()
        val lines = mutableListOf<String>()
        for (ability in definition.abilities + listOf<MobAbility?>(null)) {
            val fx = SakuraStrikeFx(instance)
            val id = ability?.id ?: "enrage"
            val hitTick = if (ability == null) 50 else (ability.telegraphMillis / 50).toInt()
            // a target-anchored ability lands where the player stands, 7 blocks ahead here
            val origin = if (ability?.anchor == MobAbilityAnchor.TARGET) net.minestom.server.coordinate.Pos(0.0, 0.0, 7.0) else net.minestom.server.coordinate.Pos.ZERO
            val frame = ability?.let { MobAbilityFrame(it, origin, net.minestom.server.coordinate.Vec(0.0, 0.0, 1.0),
                MobAbilityPhase.LOCKED, 0L, net.minestom.server.coordinate.Pos.ZERO) }
            if (frame != null) fx.windup(id, frame) { _, _ -> null }
            repeat(hitTick + 40) { tick ->
                if (tick == hitTick) { if (frame != null) fx.strike(id, frame) { _, _ -> null } else fx.roar(net.minestom.server.coordinate.Pos.ZERO, 22.0) }
                fx.tick()
                for (v in fx.views()) {
                    fun vec(p: net.minestom.server.coordinate.Point) = "[${p.x()},${p.y()},${p.z()}]"
                    lines += "{\"ability\":\"$id\",\"phase\":\"shader\",\"tick\":$tick,\"sprite\":\"${v.sprite}\",\"kind\":${v.kind}," +
                        "\"progress\":${v.progress},\"at\":${vec(v.at)},\"scale\":${vec(v.scale)},\"translation\":${vec(v.translation)}," +
                        "\"rotation\":[${v.rotation.joinToString(",")}],\"billboard\":${v.billboard}}"
                }
            }
            fx.dispose()
        }
        run {                                                // 千本桜: the floor's crest for 8 s, then the blow
            val fx = SakuraStrikeFx(instance)
            fx.finale(net.minestom.server.coordinate.Pos.ZERO, 160)
            repeat(200) { tick ->
                if (tick == 160) fx.finaleBlow(net.minestom.server.coordinate.Pos.ZERO, 22.0)
                fx.tick()
                for (v in fx.views()) {
                    fun vec(p: net.minestom.server.coordinate.Point) = "[${p.x()},${p.y()},${p.z()}]"
                    lines += "{\"ability\":\"finale\",\"phase\":\"shader\",\"tick\":$tick,\"sprite\":\"${v.sprite}\",\"kind\":${v.kind}," +
                        "\"progress\":${v.progress},\"at\":${vec(v.at)},\"scale\":${vec(v.scale)},\"translation\":${vec(v.translation)}," +
                        "\"rotation\":[${v.rotation.joinToString(",")}],\"billboard\":${v.billboard}}"
                }
            }
            fx.dispose()
        }
        run {                                                // 桜紋の陣 with three crests: the middle one left empty
            val fx = SakuraStrikeFx(instance)
            val crests = (0 until 3).map { k -> val a = Math.PI / 2 + (k - 1) * 2 * Math.PI / 3
                net.minestom.server.coordinate.Pos(kotlin.math.cos(a) * 7.0, 0.0, kotlin.math.sin(a) * 7.0) }
            fx.sigil(crests, 1.8, 120)
            repeat(160) { tick ->
                if (tick == 120) fx.sigilResolve(crests, listOf(true, false, true))
                fx.tick()
                for (v in fx.views()) {
                    fun vec(p: net.minestom.server.coordinate.Point) = "[${p.x()},${p.y()},${p.z()}]"
                    lines += "{\"ability\":\"sigil\",\"phase\":\"shader\",\"tick\":$tick,\"sprite\":\"${v.sprite}\",\"kind\":${v.kind}," +
                        "\"progress\":${v.progress},\"at\":${vec(v.at)},\"scale\":${vec(v.scale)},\"translation\":${vec(v.translation)}," +
                        "\"rotation\":[${v.rotation.joinToString(",")}],\"billboard\":${v.billboard}}"
                }
            }
            fx.dispose()
        }
        MinecraftServer.getInstanceManager().unregisterInstance(instance)
        java.io.File(path).writeText(lines.joinToString("\n"))
    }

    private class MemoryConnection : PlayerConnection() {
        override fun sendPacket(packet: SendablePacket) = Unit
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }
}
