package dev.projects.server.coreloop.adventure

import dev.projects.server.coreloop.CoreDungeons
import dev.projects.server.mob.QuestCombatEncounter
import dev.projects.server.mob.QuestEncounterCombat
import dev.projects.server.mob.QuestMobArchetype
import dev.projects.server.mob.SakuraPetalFx
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.NetworkBuffer
import net.minestom.server.network.packet.PacketWriting
import net.minestom.server.network.packet.client.play.ClientChunkBatchReceivedPacket
import net.minestom.server.network.packet.server.BufferedPacket
import net.minestom.server.network.packet.server.play.ChunkBatchFinishedPacket
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test

/**
 * Night MSPT bench (QUEUE.md「重さの計測」): four players in 散り桜の社 at its highest 段階, and how long one server tick
 * takes in its heaviest moments — 桜牙獣's 千本桜 (most petals), a 穢れ場 at its cap, the mini-bosses' skills together,
 * all of them at once in the glade, and four solo runs each in 千本桜 at once (the game's dungeons are solo today).
 *
 * One measured tick = what the game's session tick does for the fight (QuestEncounterCombat.tick + DungeonRun.tick) and
 * the whole Minestom tick (scheduler, instances, entities, viewers) — the same `ticker().tick` the server runs. Packets
 * the players receive are counted in the tick; their bytes are encoded after the tick's clock stops (the real server
 * encodes them on its network threads). Ticks run back to back (game time steps 50 ms a tick), after a warm-up. The
 * players stand still and take no damage (the fight's damage callbacks do nothing), so a scene lasts as long as it needs.
 *
 * Not part of `test`: `./gradlew :server-minestom:mspt` (writes build/mspt/mspt-report.txt).
 */
class MsptBenchTest {
    /**
     * Counts what the player is sent and answers chunk batches as a client does (25 chunks a tick asked for), so the
     * chunks around the player are sent as in play; the answer is read in the player's own tick, like a real one.
     */
    private class Counting : PlayerConnection() {
        val sent = ArrayList<SendablePacket>()
        override fun sendPacket(packet: SendablePacket) {
            synchronized(sent) { sent += packet }
            // (a real connection only queues the packet: chunk packets are built on its network thread, so not here)
            if (packet is ChunkBatchFinishedPacket) player?.addPacketToQueue(ClientChunkBatchReceivedPacket(25f))
        }
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }

    private class Rig(val instance: InstanceContainer, val combat: QuestEncounterCombat, val run: DungeonRun?)

    private class Result(val name: String, val ticks: List<Long>, val packets: List<Int>, val bytes: List<Long>,
                         val peakPetals: Int, val peakEntities: Int, val peakMobs: Int) {
        fun line(): String {
            val ms = ticks.map { it / 1e6 }.sorted()
            fun pct(p: Double) = ms[((ms.size - 1) * p).toInt()]
            return "SCENE %-14s ticks=%d avg=%.3f p50=%.3f p99=%.3f max=%.3f packetsPerTick=%.1f maxPackets=%d bytesPerTick=%.0f maxBytes=%d peakPetals=%d peakEntities=%d peakMobs=%d"
                .format(name, ms.size, ms.average(), pct(.5), pct(.99), ms.last(), packets.average(), packets.max(), bytes.average(), bytes.max(),
                    peakPetals, peakEntities, peakMobs)
        }
    }

    private companion object {
        /**
         * Unrecorded ticks after the players are moved into a scene (its chunks stream in, its enemies close in): the
         * scene is the fight, not the arrival; the arrival is the *_start line and f_enter.
         */
        const val SETTLE = 60
        val SAMPLE = System.getProperty("projects.mspt.sample") == "true"
        val DEPTH = System.getProperty("projects.mspt.depth", "1").toInt()
    }

    private val players = mutableListOf<Player>()
    private val connections = mutableListOf<Counting>()
    private var now = 1_000_000L
    private val stage = CoreDungeons.MAX_STAGE

    private fun combat(instance: InstanceContainer, layout: DungeonLayout, canTarget: (Player) -> Boolean = { it.gameMode == GameMode.ADVENTURE }) =
        QuestEncounterCombat(instance, 1, emptyList(), layout.bossSpawn, { _, _ -> }, { _, _ -> }, canTarget = canTarget,
            contentSeed = 7L, explicitBossArchetype = QuestMobArchetype.SAKURA_FANG,
            healthMultiplier = CoreDungeons.health(stage), damageMultiplier = CoreDungeons.damage(stage), rollGrades = true,
        ).also { it.pixelNameplates = true }

    /** Puts the run at [room] (still walking): the players standing in it start it on the next tick. */
    private fun at(run: DungeonRun, room: Int) {
        DungeonRun::class.java.getDeclaredField("room").apply { isAccessible = true }.setInt(run, room)
    }

    private fun ring(center: Pos, radius: Double, n: Int) = (0 until n).map { i ->
        val a = 2 * Math.PI * i / n
        Pos(center.x() + cos(a) * radius, center.y(), center.z() + sin(a) * radius).withDirection(center.sub(center.x() + cos(a) * radius, center.y(), center.z() + sin(a) * radius))
    }

    private fun place(instance: InstanceContainer, center: Pos, radius: Double, who: List<Player> = players) {
        ring(center, radius, who.size).zip(who).forEach { (p, player) ->
            val at = p.withY(InstanceDungeonFactory.floorAt(instance, p.blockX(), p.blockZ(), center.blockY()).toDouble())
            if (player.instance === instance) player.teleport(at).join() else player.setInstance(instance, at).join()
        }
    }

    private fun petals(combat: QuestEncounterCombat): Int =
        (QuestEncounterCombat::class.java.getDeclaredField("sakuraFx").apply { isAccessible = true }.get(combat) as SakuraPetalFx?)?.liveCount ?: 0

    /** Runs [ticks] ticks of [rigs] (or until [stop]), timing each. */
    private fun measure(name: String, rigs: List<Rig>, ticks: Int, record: Boolean = true, stop: (Int) -> Boolean = { false }): Result? {
        val ticker = MinecraftServer.process().ticker()
        val times = ArrayList<Long>(); val packets = ArrayList<Int>(); val bytes = ArrayList<Long>()
        var peakPetals = 0; var peakEntities = 0; var peakMobs = 0
        for (i in 0 until ticks) {
            if (stop(i)) break
            now += 50
            // what was sent between ticks (a move into a new instance sends its first chunks at once) is encoded too
            connections.forEach { synchronized(it.sent) { it.sent.forEach(::bytesOf); it.sent.clear() } }
            val sampled = HashMap<String, Int>()
            val sampler = if (SAMPLE) sample(Thread.currentThread(), sampled) else null
            val gc0 = gcMillis()
            val t0 = System.nanoTime()
            for (rig in rigs) { rig.combat.tick(now); rig.run?.tick() }
            val t1 = System.nanoTime()
            ticker.tick(System.nanoTime())
            val t2 = System.nanoTime()
            val dt = t2 - t0
            sampler?.let { it.interrupt(); it.join() }
            if (dt > 12_000_000L) sampled.entries.sortedByDescending { it.value }.take(10).forEach { println("SAMPLE $name tick=$i ${it.value} ${it.key}") }
            val gc = gcMillis() - gc0
            if (record && gc > 0) { gcTicks++; gcPauseMillis += gc }
            if (dt > 25_000_000L) {
                val kinds = connections.flatMap { c -> synchronized(c.sent) { c.sent.map { (SendablePacket.extractServerPacket(ConnectionState.PLAY, it) ?: it).javaClass.simpleName } } }
                    .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6)
                println("SLOW $name tick=$i ms=%.1f fight=%.1f minestom=%.1f gcPause=${gc}ms packets=${kinds.sumOf { it.value }} kinds=$kinds".format(dt / 1e6, (t1 - t0) / 1e6, (t2 - t1) / 1e6))
            }
            // Every tick's packets are encoded (outside the clock) as the server's network threads would: building a
            // chunk's packet is also what works out its heightmap, which a later block change would otherwise redo.
            var count = 0; var size = 0L
            for (c in connections) synchronized(c.sent) {
                count += c.sent.size
                for (p in c.sent) size += bytesOf(p)
            }
            if (!record) continue
            times += dt
            packets += count; bytes += size
            if (SAMPLE && size > 200_000) {
                val heavy = connections.flatMap { c -> synchronized(c.sent) { c.sent.map { (SendablePacket.extractServerPacket(ConnectionState.PLAY, it) ?: it).javaClass.simpleName to bytesOf(it) } } }
                    .groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.size to v.sum() }.entries.sortedByDescending { it.value.second }.take(4)
                println("BIG $name tick=$i bytes=$size by=$heavy")
            }
            peakPetals = maxOf(peakPetals, rigs.sumOf { petals(it.combat) })
            peakEntities = maxOf(peakEntities, rigs.sumOf { it.instance.entities.size })
            peakMobs = maxOf(peakMobs, rigs.sumOf { it.combat.entities().size })
        }
        return if (record) Result(name, times, packets, bytes, peakPetals, peakEntities, peakMobs).also { println(it.line()) } else null
    }

    /** Debug (-PmsptSample=true): where the tick thread is, every 0.5 ms, by project frame; printed for ticks over 12 ms. */
    private val tickThreads by lazy { Thread.getAllStackTraces().keys.filter { it.name.startsWith("Ms-Tick") } }

    private fun sample(target: Thread, counts: HashMap<String, Int>): Thread = Thread {
        try {
            while (true) for (thread in listOf(target) + tickThreads) {
                val frames = thread.stackTrace
                if (thread !== target && frames.firstOrNull()?.methodName == "park") continue
                val mine = frames.filter { it.className.startsWith("dev.projects") && "MsptBench" !in it.className }.take(4)
                    .joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
                val top = frames.take(DEPTH).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}" }
                counts.merge("${if (thread === target) "" else "[${thread.name}] "}$mine || $top", 1, Int::plus)
                if (thread === target) Thread.sleep(0, 500_000)
            }
        } catch (_: InterruptedException) {}
    }.also { it.isDaemon = true; it.start() }

    private var gcTicks = 0
    private var gcPauseMillis = 0L
    /** Stop-the-world collection time so far (G1's young/mixed/full collections; ZGC's pauses). */
    private fun gcMillis(): Long = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()
        .filter { "Pauses" in it.name || "Young" in it.name || "Old" in it.name || "Mixed" in it.name }.sumOf { it.collectionTime }

    /** One reused buffer, as the network writer reuses its own (the bench should not make garbage the server does not). */
    private val scratch = NetworkBuffer.resizableBuffer(1L shl 20, MinecraftServer.process())

    private fun bytesOf(packet: SendablePacket): Long = when (packet) {
        is BufferedPacket -> packet.length()
        else -> SendablePacket.extractServerPacket(ConnectionState.PLAY, packet)?.let {
            scratch.clear()
            runCatching { PacketWriting.writeFramedPacket(scratch, ConnectionState.PLAY, it, 0); scratch.writeIndex() }.getOrDefault(0L)
        } ?: 0L
    }

    /** 千本桜: a blow that drops 桜牙獣 to its quarter (it enrages, roars, then 千本桜), until its blossoms have fallen. */
    private fun finale(name: String, rigs: List<Rig>, record: Boolean): Result? {
        val bosses = rigs.map { rig -> rig.combat.entities().single { rig.combat.isBoss(it.uuid) } }
        rigs.zip(bosses).forEach { (rig, boss) ->
            val hitter = players.first { it.instance === rig.instance }
            rig.combat.applyEffectDamage(boss.uuid, hitter, 1e9)
        }
        var after = -1
        return measure(name, rigs, 1200, record) {
            val done = rigs.zip(bosses).all { (rig, boss) -> rig.combat.finaleState(boss.uuid)?.let { it.first && !it.second } == true }
            if (done && after < 0) after = it
            after >= 0 && it - after >= 80                                 // the blow's burst and the falling petals
        }
    }

    @Test
    fun `tick time with four players in 散り桜の社's heaviest moments`() {
        assumeTrue(System.getProperty("projects.mspt") == "true", "./gradlew :server-minestom:mspt runs this")
        MinecraftServer.init(Auth.Offline())
        BossArenaFactory.registerDimensions()
        MinecraftServer.process().dispatcher().start()
        val results = mutableListOf<Result>()
        val dungeon = InstanceDungeonFactory.create(1)
        val instance = dungeon.arena.instance
        val layout = dungeon.layout
        repeat(4) { i ->
            val connection = Counting().apply { setClientState(ConnectionState.PLAY); setServerState(ConnectionState.PLAY) }
            val player = Player(connection, GameProfile(UUID.randomUUID(), "Mspt$i"))
            connection.player = player
            player.gameMode = GameMode.ADVENTURE
            player.setInstance(instance, layout.spawn).join()
            players += player; connections += connection
        }
        val spawnerRoom = layout.rooms.indexOfFirst { it.kind == RoomKind.SPAWNER }
        val minibossRoom = layout.rooms.indexOfFirst { it.kind == RoomKind.MINIBOSS }
        val extra = mutableListOf<InstanceDungeon>()
        try {
            fun glade(): Rig { place(instance, layout.bossSpawn, 6.0); return Rig(instance, combat(instance, layout), null) }
            fun spawner(): Rig {
                val c = combat(instance, layout); val run = DungeonRun(instance, c, layout, stage) { players }
                at(run, spawnerRoom); place(instance, layout.rooms[spawnerRoom].center, 3.0)
                return Rig(instance, c, run)
            }
            fun minibosses(): Rig {
                val c = combat(instance, layout); val run = DungeonRun(instance, c, layout, stage) { players }
                at(run, minibossRoom); val room = layout.rooms[minibossRoom]; place(instance, room.center, 3.0)
                // the room's own (ミニボス1 + the 段階's escorts) rises on the first tick; ミニボス2 beside it so their skills overlap
                c.spawnEncounter(QuestCombatEncounter(listOf(room.spawns.first().add(4.0, 0.0, 0.0).let {
                    it.withY(InstanceDungeonFactory.floorAt(instance, it.blockX(), it.blockZ(), it.blockY()).toDouble()) }),
                    listOf(QuestMobArchetype.SAKURA_MINIBOSS_2)))
                return Rig(instance, c, run)
            }
            fun dispose(rig: Rig) { rig.run?.dispose(); rig.combat.dispose() }

            // Warm-up (JIT): each scene once, unrecorded.
            glade().also { finale("warmup", listOf(it), false); dispose(it) }
            spawner().also { measure("warmup", listOf(it), 400, false); dispose(it) }
            minibosses().also { measure("warmup", listOf(it), 400, false); dispose(it) }

            // (a) 千本桜: the most petals.
            glade().also { results += measure("a_start", listOf(it), 1)!!; measure("settle", listOf(it), SETTLE, false); results += finale("a_senbonzakura", listOf(it), true)!!; dispose(it) }
            // (b) 穢れ場 at its cap for four (nobody falls, so it stays full); 30 s.
            spawner().also { results += measure("b_start", listOf(it), 1)!!; measure("fill", listOf(it), 200, false); results += measure("b_kegareba", listOf(it), 600)!!; dispose(it) }
            // (c) both mini-bosses (and ミニボス1's escorts) at the four; 30 s.
            minibosses().also { results += measure("c_start", listOf(it), 1)!!; measure("settle", listOf(it), SETTLE, false); results += measure("c_minibosses", listOf(it), 600)!!; dispose(it) }
            // (d) all at once in the glade: 千本桜 + a full 穢れ場's enemies + both mini-bosses and escorts.
            glade().also { rig ->
                val c = rig.combat; val centre = layout.bossSpawn
                val ring = ring(centre, 9.0, 12).map { it.withY(InstanceDungeonFactory.floorAt(instance, it.blockX(), it.blockZ(), centre.blockY()).toDouble()) }
                val spawned = listOf(QuestMobArchetype.SOLDIER, QuestMobArchetype.RIFT_CASTER, QuestMobArchetype.SOLDIER, QuestMobArchetype.SHIELD_GUARD,
                    QuestMobArchetype.SOLDIER, QuestMobArchetype.RIFT_CASTER, QuestMobArchetype.SOLDIER, QuestMobArchetype.ELITE_BRUTE,
                    QuestMobArchetype.SAKURA_MINIBOSS_1, QuestMobArchetype.SAKURA_MINIBOSS_2, QuestMobArchetype.RIFT_CASTER, QuestMobArchetype.RIFT_CASTER)
                spawned.chunked(4).forEachIndexed { k, types -> c.spawnEncounter(QuestCombatEncounter(ring.subList(k * 4, k * 4 + types.size), types)) }
                measure("settle", listOf(rig), SETTLE, false)
                results += finale("d_all_at_once", listOf(rig), true)!!; dispose(rig)
            }
            // (e) four solo runs (one player each, as the game is today), all four in 千本桜 at once.
            repeat(3) { extra += InstanceDungeonFactory.create(1) }
            val instances = listOf(instance) + extra.map { it.arena.instance }
            val rigs = instances.mapIndexed { i, inst ->
                val lay = if (i == 0) layout else extra[i - 1].layout
                val me = players[i]
                place(inst, lay.bossSpawn, 6.0, listOf(me))
                Rig(inst, combat(inst, lay) { it === me }, null)
            }
            results += measure("e_start", rigs, 1)!!
            measure("settle", rigs, SETTLE, false)
            results += finale("e_four_solo", rigs, true)!!
            rigs.forEach(::dispose)
            // (f) one player enters a fresh 散り桜の社 (a new run): its first 2 s (the others stay where (e) left them).
            val fresh = InstanceDungeonFactory.create(1).also { extra += it }
            players[0].setInstance(fresh.arena.instance, fresh.layout.spawn).join()
            val entering = Rig(fresh.arena.instance, combat(fresh.arena.instance, fresh.layout) { it === players[0] },
                null).let { Rig(it.instance, it.combat, DungeonRun(it.instance, it.combat, fresh.layout, stage) { listOf(players[0]) }) }
            results += measure("f_enter", listOf(entering), 40)!!
            dispose(entering)

            val cpu = runCatching { File("/proc/cpuinfo").readLines().first { it.startsWith("model name") }.substringAfter(":").trim() }.getOrDefault("?")
            val report = buildString {
                appendLine("MSPT cpu=$cpu cores=${Runtime.getRuntime().availableProcessors()} java=${System.getProperty("java.version")} " +
                    "heap=${Runtime.getRuntime().maxMemory() / 1_048_576}MB gc=${java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().joinToString("+") { it.name }} " +
                    "stage=$stage players=4 measuredTicksWithGcPause=$gcTicks gcPauseInMeasuredTicks=${gcPauseMillis}ms")
                results.forEach { appendLine(it.line()) }
            }
            println(report)
            File("mspt-report.txt").appendText(report)
        } finally {
            players.forEach { it.remove() }
            runCatching { MinecraftServer.stopCleanly() }
        }
    }
}
