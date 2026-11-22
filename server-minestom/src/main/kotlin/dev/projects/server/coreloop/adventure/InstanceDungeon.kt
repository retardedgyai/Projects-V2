package dev.projects.server.coreloop.adventure

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.projects.server.coreloop.CoreDungeons
import dev.projects.server.coreloop.QuestCombatPlacement
import dev.projects.server.coreloop.ShippedMap
import dev.projects.server.mob.QuestCombatEncounter
import dev.projects.server.mob.QuestEncounterCombat
import dev.projects.server.mob.QuestMobArchetype
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.Weather
import net.minestom.server.instance.anvil.AnvilLoader
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.math.floor

/** A doorway's blocks: closed they are a wall the wave cannot be left through, open they are what the map had there. */
internal class DungeonGate(val name: String, val blocks: List<Vec>, private val closed: List<Block>, private val original: List<Block>) {
    init { require(blocks.isNotEmpty() && closed.size == blocks.size && original.size == blocks.size) }
    val center: Pos get() = blocks.fold(Vec.ZERO) { sum, it -> sum.add(it) }.div(blocks.size.toDouble()).let { Pos(it.x() + .5, it.y(), it.z() + .5) }
    fun isOpen(instance: InstanceContainer) = instance.getBlock(blocks.first()) != closed.first()
    fun set(instance: InstanceContainer, open: Boolean) = blocks.forEachIndexed { i, it -> instance.setBlock(it, if (open) original[i] else closed[i]) }
}

/** What a room asks of the players before its far gate opens. */
internal enum class RoomKind { WAVES, MINIBOSS, SPAWNER }

/**
 * 穢れ場: enemies keep rising from [spawns] until [target] of them have fallen (at most [cap] at once, one every
 * [intervalTicks]), then the [last] one comes; it opens the gate. Both counts grow with the party: base + per × players.
 */
internal data class SpawnerRule(val target: Pair<Int, Int>, val cap: Pair<Int, Int>, val intervalTicks: Int,
    val mobs: List<QuestMobArchetype>, val last: QuestMobArchetype) {
    fun target(players: Int) = target.first + target.second * players
    fun cap(players: Int) = cap.first + cap.second * players
}

/** A round area of the map (centre, radius, floor at the centre). Its waves come one after another. */
internal data class DungeonRoom(val name: String, val cx: Int, val cz: Int, val radius: Int, val floor: Int,
    val waves: List<List<QuestMobArchetype>>, val entry: Pos, val seals: List<Vec> = emptyList(),
    val kind: RoomKind = RoomKind.WAVES, val spawns: List<Pos> = emptyList(), val spawner: SpawnerRule? = null) {
    init { require(radius > 4 && waves.all { it.size in 1..4 } && (kind == RoomKind.SPAWNER) == (spawner != null)) }
    val center: Pos get() = Pos(cx + .5, floor.toDouble(), cz + .5)
    /** Inside the round, [margin] blocks in from its edge. */
    fun holds(p: Pos, margin: Double = 0.0): Boolean {
        val r = radius + 2.0 - margin
        return (p.x() - center.x()).let { it * it } + (p.z() - center.z()).let { it * it } < r * r && p.y() in floor - 6.0..floor + 10.0
    }
}

/** gates[i] leads into rooms[i]; the last gate leads into the arena. */
internal class DungeonLayout(val name: String, val spawn: Pos, val rooms: List<DungeonRoom>, val gates: List<DungeonGate>,
    val arenaCenter: Pos, val arenaRadius: Double, val bossSpawn: Pos, val packs: List<Pair<Pos, Pair<Int, Int>>> = emptyList()) {
    init { require(gates.size == rooms.size + 1 && rooms.all { it.waves.isNotEmpty() || it.kind == RoomKind.SPAWNER }) }
    val arenaGate: DungeonGate get() = gates.last()
    /** Past the gatehouse and the flower beds, on the glade's grass. */
    fun inArena(p: Pos) = p.distanceSquared(arenaCenter.withY(p.y())) < (arenaRadius - 3) * (arenaRadius - 3) && p.y() > arenaCenter.y() - 4
    /** Just inside the glade, on the side the gate is. */
    val arenaEntry: Pos get() = arenaGate.center.sub(arenaCenter).withY(0.0).asVec().normalize().mul(arenaRadius - 6).add(arenaCenter.asVec()).asPosition()
}

internal class InstanceDungeon(val arena: BossArena, val layout: DungeonLayout)

/**
 * 散り桜の社: the map drawn in scripts/buildkit/towns/sakura_dungeon.py, exported to region files beside rooms.json
 * (resources/dungeons/sakura_dungeon). Each run reads its own copy of the chunks; nothing is saved back. rooms.json's
 * flow lists the rooms in order, each with its kind (waves, miniboss, spawner = 穢れ場, boss), its gate and where its
 * enemies rise; the last is 散り桜の庭 and 桜牙獣.
 */
internal object InstanceDungeonFactory {
    const val DISPLAY_NAME = "散り桜の社"
    private const val MAP = "sakura_dungeon"

    // The shrine's own common enemies (桜木の亡者, 枝弓の骸, つぼみ); the exiles' shield and their elite stay until sakura ones exist.
    private val HUSK = QuestMobArchetype.SAKURA_HUSK
    private val ARCHER = QuestMobArchetype.SAKURA_ARCHER
    private val BUD = QuestMobArchetype.SAKURA_BUD
    private val SOLDIERS = listOf(HUSK, HUSK, ARCHER)
    /** The waves of each waves room, by its rooms.json key; any other waves room gets two of soldiers. */
    private val WAVES = mapOf(
        "R1" to listOf(SOLDIERS, listOf(HUSK, BUD, ARCHER)),
        "H" to listOf(
            listOf(QuestMobArchetype.SHIELD_GUARD, ARCHER, HUSK, BUD),
            listOf(QuestMobArchetype.ELITE_BRUTE, ARCHER, BUD)),
    )
    /** The miniboss rooms in order: the first holds ミニボス1, the second ミニボス2. */
    private val MINIBOSSES = listOf(QuestMobArchetype.SAKURA_MINIBOSS_1, QuestMobArchetype.SAKURA_MINIBOSS_2)
    private val SPAWNED = listOf(HUSK, ARCHER, BUD, HUSK)

    private val folder: Path get() = ShippedMap.folder("dungeons/$MAP")
    private val rooms: JsonObject by lazy { JsonParser.parseString(Files.readString(folder.resolve("rooms.json"))).asJsonObject }
    /** The rooms before the glade, in the order they are walked. */
    val ROOM_NAMES: List<String> get() = rooms.getAsJsonArray("flow").map { it.asJsonObject }.filter { it["kind"].asString != "boss" }.map { it["name"].asString }

    /** "8 + 4 * 人数" → 8 to 4, "3 + 人数" → 3 to 1, "6" → 6 to 0. */
    internal fun perPlayers(text: String): Pair<Int, Int> {
        val parts = text.split('+').map { it.trim() }
        val base = parts.filter { "人数" !in it }.sumOf { it.toInt() }
        val per = parts.filter { "人数" in it }.sumOf { p -> p.substringBefore('*').trim().takeIf { it != p && it.isNotEmpty() }?.toInt() ?: 1 }
        return base to per
    }

    fun create(tier: Int): InstanceDungeon {
        require(tier in 1..4)
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setChunkSupplier(::LightingChunk)
        instance.chunkLoader = AnvilLoader(folder)
        instance.time = 6_000L
        instance.defaultClock()?.pause()
        instance.setWeather(Weather.CLEAR)
        try {
            // The map spans x and z -112..111; its chunks carry no light until relit here.
            val chunks = (-8..7).flatMap { x -> (-8..7).map { z -> instance.loadChunk(x, z) } }
            CompletableFuture.allOf(*chunks.toTypedArray()).join()
            LightingChunk.relight(instance, instance.chunks)
            fun ints(at: JsonElement) = at.asJsonArray.map { it.asInt }
            fun pos(at: JsonElement) = ints(at).let { (x, y, z) -> Pos(x + .5, y.toDouble(), z + .5) }
            val seals = rooms.getAsJsonObject("seals")
            val flow = rooms.getAsJsonArray("flow").map { it.asJsonObject }
            val gates = flow.map { room ->
                val (x, y, z) = ints(room["gate_at"]); val door = room.getAsJsonObject("door")
                doorway(instance, room["name"].asString, x, y, z, door["axis"].asString == "x", door["half"].asInt)
            }
            var miniboss = 0
            val list = flow.dropLast(1).mapIndexed { i, room ->
                require(room["kind"].asString != "boss") { "Only the last room of the flow is the boss's" }
                val (cx, _, cz) = ints(room["center"]); val radius = room["radius"].asInt
                val floor = floorAt(instance, cx, cz, ints(room["center"])[1])
                // On the gate's side, well inside the round: where whoever lagged behind is brought in.
                val door = gates[i].center
                val inward = Vec(cx + .5 - door.x(), 0.0, cz + .5 - door.z()).normalize()
                // From radius - 4 toward the middle, the first spot a player can stand in (rocks and trees stand in some rooms).
                val entry = (radius - 4 downTo 0).firstNotNullOfOrNull { d ->
                    val ex = cx + .5 - inward.x() * d; val ez = cz + .5 - inward.z() * d
                    Pos(ex, floorAt(instance, floor(ex).toInt(), floor(ez).toInt(), floor).toDouble(), ez).takeIf { QuestCombatPlacement.clear(instance, it) }
                }?.withDirection(inward) ?: QuestCombatPlacement.resolve(instance, Pos(cx + .5, floor.toDouble(), cz + .5)).withDirection(inward)
                val lit = room.getAsJsonArray("seals").map { name -> ints(seals[name.asString]).let { (a, b, c) -> Vec(a.toDouble(), b.toDouble(), c.toDouble()) } }
                val spawns = room.getAsJsonArray("spawns")?.map(::pos).orEmpty()
                val name = room["name"].asString
                when (room["kind"].asString) {
                    "waves" -> DungeonRoom(name, cx, cz, radius, floor, WAVES[room["key"].asString] ?: listOf(SOLDIERS, SOLDIERS), entry, lit,
                        RoomKind.WAVES, spawns)
                    "miniboss" -> DungeonRoom(name, cx, cz, radius, floor, listOf(listOf(MINIBOSSES[miniboss++])), entry, lit,
                        RoomKind.MINIBOSS, listOf(pos(room["miniboss_spawn"])))
                    "spawner" -> {
                        val rule = room.getAsJsonObject("spawner")
                        DungeonRoom(name, cx, cz, radius, floor, emptyList(), entry, lit, RoomKind.SPAWNER, spawns,
                            SpawnerRule(perPlayers(rule["kill_target"].asString), perPlayers(rule["alive_cap"].asString),
                                (rule["interval_s"].asDouble * 20).toInt(), SPAWNED, QuestMobArchetype.ELITE_BRUTE))
                    }
                    else -> error("Unknown room kind ${room["kind"]}")
                }
            }
            gates.forEachIndexed { index, it -> it.set(instance, open = index == 0) }
            val spawn = pos(rooms["spawn"])
            val boss = flow.last()
            val center = pos(boss["center"])
            val packs = rooms.getAsJsonArray("packs")?.map { it.asJsonObject }.orEmpty().map { pos(it["at"]) to perPlayers(it["count"].asString) }
            // Facing up the valley toward the first gate.
            val first = gates.first().center
            val layout = DungeonLayout(DISPLAY_NAME, spawn.withDirection(first.sub(spawn).withY(0.0)), list, gates,
                center, boss["radius"].asDouble, QuestCombatPlacement.resolve(instance, center), packs)
            return InstanceDungeon(BossArena(instance, layout.spawn, layout.bossSpawn, QuestMobArchetype.SAKURA_FANG,
                CoreDungeons.SAKURA, DISPLAY_NAME), layout)
        } catch (failure: Throwable) {
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
            throw failure
        }
    }

    /** The standing height (block above the ground) of column x, z nearest [near]. */
    internal fun floorAt(instance: InstanceContainer, x: Int, z: Int, near: Int): Int =
        (0..12).flatMap { listOf(near + it, near - it) }.firstOrNull { y ->
            instance.getBlock(x, y - 1, z).solid() && !instance.getBlock(x, y, z).solid() && !instance.getBlock(x, y + 1, z).solid()
        } ?: near

    /**
     * The doorway through the gate at x, y, z: the open cells of the plane across the path ([alongX] = the path runs
     * east–west, so the plane spans z). Under the arch (±[half]) bars up to the arch, joined along the plane so they are
     * a wall, not posts. Beside it, where the ground goes on round the pillars, an invisible wall like the map's own, out
     * to the first barrier.
     */
    private fun doorway(instance: InstanceContainer, name: String, x: Int, y: Int, z: Int, alongX: Boolean, half: Int): DungeonGate {
        val bars = Block.IRON_BARS.withProperties(if (alongX) mapOf("north" to "true", "south" to "true") else mapOf("east" to "true", "west" to "true"))
        val blocks = mutableListOf<Vec>(); val closed = mutableListOf<Block>()
        fun column(u: Int, block: Block, height: Int) {
            val (bx, bz) = if (alongX) x to z + u else x + u to z
            var by = floorAt(instance, bx, bz, y)
            if (by !in y - 4..y + 4) return
            val top = by + height
            while (by < top && !instance.getBlock(bx, by, bz).solid()) { blocks += Vec(bx.toDouble(), by.toDouble(), bz.toDouble()); closed += block; by++ }
        }
        for (u in -half..half) column(u, bars, 8)
        for (sign in listOf(-1, 1)) for (u in half + 1..SIDE_REACH) {
            val (bx, bz) = if (alongX) x to z + sign * u else x + sign * u to z
            if ((y - 1..y + 3).any { instance.getBlock(bx, it, bz).compare(Block.BARRIER) }) break
            column(sign * u, Block.BARRIER, 6)
        }
        require(blocks.isNotEmpty()) { "Gate $name has no opening" }
        return DungeonGate(name, blocks, closed, blocks.map { instance.getBlock(it) })
    }
    private const val SIDE_REACH = 16
}

/**
 * One dungeon run's progress. Walking into a room shuts the gate behind the party and starts it: a waves room sends
 * its waves one after another, a miniboss room its miniboss, a 穢れ場 keeps raising enemies until enough have fallen
 * and its last one is down. Clearing a room opens the next gate (it stays shut until then, so a room cannot be run
 * past). Past the last room the glade is open; walking in shuts it and wakes the boss. The entrance's packs rise on
 * the first tick and follow whoever they see. Combat, rewards and the boss itself stay with QuestEncounterCombat.
 */
internal class DungeonRun(
    private val instance: InstanceContainer,
    private val combat: QuestEncounterCombat,
    val layout: DungeonLayout,
    /** 段階 (CoreDungeons.stage): from 2 every waves room sends one more, a miniboss brings escorts, a 穢れ場 asks for more. */
    val step: Int = 1,
    private val players: () -> List<Player>,
) {
    enum class Stage { WALKING, FIGHTING, BOSS }
    var stage = Stage.WALKING
        private set
    /** The room the players are in or walking toward; rooms.size once the glade is next. */
    var room = 0
        private set
    var wave = 0
        private set
    private var enemies = emptySet<UUID>()
    private val spawned = mutableSetOf<UUID>()
    private var ticks = 0L
    private var packsRisen = false
    // 穢れ場: how many have fallen, risen, and whether the last one is out; when the next one may rise.
    private var fallen = 0
    private var risen = 0
    private var lastOut = false
    private var nextRise = 0L
    val bossEngaged: Boolean get() = stage == Stage.BOSS
    fun aliveEnemies() = enemies.count(combat::isAlive)

    fun tick() {
        ticks++
        val players = players()
        if (players.isEmpty()) return
        if (!packsRisen) {
            packsRisen = true; risePacks(players.size)
            if (step > 1) announce(players, "${layout.name}・段階$step", "敵は強く、部屋は手強くなった", NamedTextColor.GOLD)
        }
        when (stage) {
            Stage.WALKING -> if (room < layout.rooms.size) {
                val r = layout.rooms[room]
                if (players.any { r.holds(it.position, margin = 2.5) }) {
                    gather(players, layout.gates[room], r.entry) { r.holds(it.position) }
                    stage = Stage.FIGHTING; wave = 0
                    when (r.kind) {
                        RoomKind.WAVES -> { announce(players, r.name, "${r.waves.size}つの波を退けよ", NamedTextColor.LIGHT_PURPLE); spawnWave(r) }
                        RoomKind.MINIBOSS -> { announce(players, r.name, "${r.waves[0][0].displayName}を討て", NamedTextColor.GOLD); spawnWave(r) }
                        RoomKind.SPAWNER -> {
                            announce(players, r.name, "湧き続ける穢れを祓え", NamedTextColor.DARK_PURPLE)
                            fallen = 0; risen = 0; lastOut = false; nextRise = ticks
                        }
                    }
                }
            } else if (players.any { layout.inArena(it.position) }) {
                gather(players, layout.arenaGate, layout.arenaEntry.withDirection(layout.arenaCenter.sub(layout.arenaEntry))) { layout.inArena(it.position) }
                stage = Stage.BOSS
                announce(players, InstanceDungeonFactory.DISPLAY_NAME, "${combat.bossName()}が目を覚ました", NamedTextColor.RED)
            }
            Stage.FIGHTING -> {
                val r = layout.rooms[room]
                if (r.kind == RoomKind.SPAWNER) tickSpawner(r, players.size)
                else if (enemies.none(combat::isAlive)) {
                    combat.removeEncounter(enemies); enemies = emptySet()
                    if (wave + 1 < r.waves.size) { wave++; spawnWave(r) } else cleared(r, players)
                }
            }
            Stage.BOSS -> Unit
        }
    }

    /** Development: a 穢れ場 skips to its last enemy (the rest of the room's enemies fall as /dungeontest clear does). */
    fun skipToLast() {
        val r = layout.rooms.getOrNull(room) ?: return
        if (stage == Stage.FIGHTING && r.kind == RoomKind.SPAWNER) { val target = spawnerTarget(r, players().size); fallen = target; risen = target }
    }

    fun dispose() { combat.removeEncounter(spawned) }

    private fun cleared(r: DungeonRoom, players: List<Player>) {
        combat.removeEncounter(enemies); enemies = emptySet()
        room++; stage = Stage.WALKING
        val gate = layout.gates[room]
        gate.set(instance, open = true)
        sound(players, SoundEvent.BLOCK_IRON_DOOR_OPEN, .8f)
        // The room's seals light (the shrine's, and one of the two before the gatehouse).
        r.seals.forEach { at -> instance.setBlock(at, Block.LANTERN.withProperties(instance.getBlock(at).properties())); particle(Pos(at.x() + .5, at.y() + .5, at.z() + .5), Particle.FLAME) }
        if (r.seals.isNotEmpty()) sound(players, SoundEvent.BLOCK_BEACON_ACTIVATE, .6f)
        gate.blocks.forEach { particle(Pos(it.x() + .5, it.y() + .5, it.z() + .5), Particle.CHERRY_LEAVES) }
        players.forEach { it.sendActionBar(Component.text(if (room < layout.rooms.size) "${r.name}を制圧・次の扉が開いた" else "${r.name}を制圧・庭への道が開いた", NamedTextColor.GREEN)) }
    }

    /** 穢れ場: count the fallen, raise the next while the room holds fewer than its cap, then the last; it ends the room. */
    private fun tickSpawner(r: DungeonRoom, party: Int) {
        val rule = r.spawner!!
        val down = enemies.filterNot(combat::isAlive).toSet()
        if (down.isNotEmpty()) {
            if (!lastOut) fallen += down.size
            combat.removeEncounter(down); enemies = enemies - down
            players().forEach { it.sendActionBar(Component.text(if (lastOut) "${r.name}・最後の穢れ" else "${r.name}・あと${(spawnerTarget(r, party) - fallen).coerceAtLeast(0)}体", NamedTextColor.GOLD)) }
        }
        val target = spawnerTarget(r, party)
        when {
            lastOut -> if (enemies.isEmpty()) cleared(r, players())
            fallen >= target && enemies.isEmpty() -> { lastOut = true; rise(r, rule.last) }
            risen < target && enemies.size < rule.cap(party) && ticks >= nextRise -> {
                rise(r, rule.mobs[risen % rule.mobs.size]); risen++
                // The room fills at once, then one at a time.
                nextRise = if (risen < rule.cap(party)) ticks else ticks + rule.intervalTicks
            }
        }
    }

    /** A 穢れ場 asks three more a 段階 past the first. */
    internal fun spawnerTarget(r: DungeonRoom, party: Int) = r.spawner!!.target(party) + 3 * (step - 1)

    /** The room's wave at this 段階: one more shield in a waves room (up to four), escorts beside a miniboss. */
    internal fun waveAt(r: DungeonRoom, wave: Int): List<QuestMobArchetype> {
        val types = r.waves[wave]
        if (step < 2) return types
        return when (r.kind) {
            RoomKind.WAVES -> if (types.size < 4) types + QuestMobArchetype.SHIELD_GUARD else types
            RoomKind.MINIBOSS -> types + List((step - 1).coerceAtMost(2)) { QuestMobArchetype.SAKURA_ARCHER }
            RoomKind.SPAWNER -> types
        }
    }

    private fun rise(r: DungeonRoom, type: QuestMobArchetype) {
        val at = place(r.spawns.getOrElse(risen % r.spawns.size.coerceAtLeast(1)) { r.center }, r.floor)
        val ids = combat.spawnEncounter(QuestCombatEncounter(listOf(at), listOf(type)))
        enemies = enemies + ids; spawned += ids
        for (n in 0..6) particle(at.add(0.0, n * .35, 0.0), Particle.SQUID_INK)
    }

    private fun risePacks(party: Int) {
        for ((at, count) in layout.packs) {
            val n = (count.first + count.second * party).coerceIn(1, 4)
            val positions = (0 until n).map { i -> place(at.add((i % 2) * 3.0 - 1.5, 0.0, (i / 2) * 3.0 - 1.5), at.blockY()) }
            spawned += combat.spawnEncounter(QuestCombatEncounter(positions, (0 until n).map { if (it % 3 == 2) QuestMobArchetype.SAKURA_ARCHER else QuestMobArchetype.SAKURA_HUSK }))
        }
    }

    private fun place(at: Pos, near: Int) =
        QuestCombatPlacement.resolve(instance, at.withY(InstanceDungeonFactory.floorAt(instance, at.blockX(), at.blockZ(), near).toDouble()))

    private fun spawnWave(r: DungeonRoom) {
        val types = waveAt(r, wave)
        // Where the map says, else across the middle and the far side of the room (the players come in at the gate side).
        val ahead = r.entry.direction().withY(0.0).normalize(); val side = Vec(-ahead.z(), 0.0, ahead.x())
        val offsets = listOf(-4.0 to 1.0, 4.0 to 1.0, 0.0 to 4.0, 0.0 to -2.0)
        val positions = types.indices.map { i ->
            // A miniboss's escorts stand four blocks to either side of it.
            if (r.kind == RoomKind.MINIBOSS && i > 0) place(r.spawns[0].add(side.mul(if (i % 2 == 1) 4.0 else -4.0)), r.floor)
            else place(r.spawns.getOrNull((i + wave * 2) % r.spawns.size.coerceAtLeast(1)) ?: offsets[i].let { (u, v) -> r.center.add(side.mul(u)).add(ahead.mul(v)) }, r.floor)
        }
        enemies = combat.spawnEncounter(QuestCombatEncounter(positions, types))
        spawned += enemies
        if (r.kind == RoomKind.WAVES) players().forEach { it.sendActionBar(Component.text("${r.name}・第${wave + 1}/${r.waves.size}波", NamedTextColor.GOLD)) }
        positions.forEach { at -> for (n in 0..6) particle(at.add(0.0, n * .35, 0.0), Particle.CHERRY_LEAVES) }
    }

    /** Shut [gate] behind the party; whoever was left behind is brought through it (RuneForge-style). */
    private fun gather(players: List<Player>, gate: DungeonGate, inside: Pos, holds: (Player) -> Boolean) {
        players.filterNot(holds).forEach { it.teleport(inside) }
        gate.set(instance, open = false)
        sound(players, SoundEvent.BLOCK_IRON_DOOR_CLOSE, .7f)
    }

    private fun announce(players: List<Player>, title: String, subtitle: String, color: NamedTextColor) = players.forEach {
        it.showTitle(Title.title(Component.text(title, color), Component.text(subtitle, NamedTextColor.WHITE),
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800), Duration.ofMillis(500))))
    }

    private fun sound(players: List<Player>, event: SoundEvent, volume: Float) =
        players.forEach { it.playSound(Sound.sound(event, Sound.Source.BLOCK, volume, 1f)) }

    private fun particle(position: Pos, type: Particle) {
        val packet = ParticlePacket(type, position.x(), position.y(), position.z(), .3f, .3f, .3f, 0f, 3)
        instance.players.forEach { it.sendPacket(packet) }
    }
}
