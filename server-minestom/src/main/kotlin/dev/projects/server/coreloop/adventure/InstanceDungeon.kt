package dev.projects.server.coreloop.adventure

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

/** A doorway's blocks: closed they are a wall the wave cannot be left through, open they are what the map had there. */
internal class DungeonGate(val name: String, val blocks: List<Vec>, private val closed: List<Block>, private val original: List<Block>) {
    init { require(blocks.isNotEmpty() && closed.size == blocks.size && original.size == blocks.size) }
    val center: Pos get() = blocks.fold(Vec.ZERO) { sum, it -> sum.add(it) }.div(blocks.size.toDouble()).let { Pos(it.x() + .5, it.y(), it.z() + .5) }
    fun isOpen(instance: InstanceContainer) = instance.getBlock(blocks.first()) != closed.first()
    fun set(instance: InstanceContainer, open: Boolean) = blocks.forEachIndexed { i, it -> instance.setBlock(it, if (open) original[i] else closed[i]) }
}

/** A round area of the map (centre, radius, floor at the centre). Its waves come one after another. */
internal data class DungeonRoom(val name: String, val cx: Int, val cz: Int, val radius: Int, val floor: Int,
    val waves: List<List<QuestMobArchetype>>, val entry: Pos, val seals: List<Vec> = emptyList()) {
    init { require(radius > 4 && waves.all { it.size in 1..4 }) }
    val center: Pos get() = Pos(cx + .5, floor.toDouble(), cz + .5)
    /** Inside the round, [margin] blocks in from its edge. */
    fun holds(p: Pos, margin: Double = 0.0): Boolean {
        val r = radius + 2.0 - margin
        return (p.x() - center.x()).let { it * it } + (p.z() - center.z()).let { it * it } < r * r && p.y() in floor - 6.0..floor + 10.0
    }
}

/** gates[i] leads into rooms[i]; the last gate leads into the arena. */
internal class DungeonLayout(val name: String, val spawn: Pos, val rooms: List<DungeonRoom>, val gates: List<DungeonGate>,
    val arenaCenter: Pos, val arenaRadius: Double, val bossSpawn: Pos) {
    init { require(gates.size == rooms.size + 1 && rooms.all { it.waves.isNotEmpty() }) }
    val arenaGate: DungeonGate get() = gates.last()
    /** Past the gatehouse and the flower beds, on the glade's grass. */
    fun inArena(p: Pos) = p.distanceSquared(arenaCenter.withY(p.y())) < (arenaRadius - 3) * (arenaRadius - 3) && p.y() > arenaCenter.y() - 4
    /** Just inside the glade, on the side the gate is. */
    val arenaEntry: Pos get() = arenaGate.center.sub(arenaCenter).withY(0.0).asVec().normalize().mul(arenaRadius - 6).add(arenaCenter.asVec()).asPosition()
}

internal class InstanceDungeon(val arena: BossArena, val layout: DungeonLayout)

/**
 * 散り桜の社: the map drawn in scripts/buildkit/towns/sakura_dungeon.py, exported to region files beside rooms.json
 * (resources/dungeons/sakura_dungeon). Each run reads its own copy of the chunks; nothing is saved back. The path
 * climbs round the valley through 苔の窪, 散華の間, 古い祠の森 and 滝裏の洞, then crosses the bridge to 散り桜の庭 and 桜牙獣.
 */
internal object InstanceDungeonFactory {
    const val DISPLAY_NAME = "散り桜の社"
    private const val MAP = "sakura_dungeon"

    private val SOLDIERS = listOf(QuestMobArchetype.SOLDIER, QuestMobArchetype.SOLDIER, QuestMobArchetype.RIFT_CASTER)
    /** rooms.json key, the gate leading in, waves, and the seals its clearing lights. */
    private class Plan(val key: String, val name: String, val waves: List<List<QuestMobArchetype>>, val seals: List<String> = emptyList())
    private val PLANS = listOf(
        Plan("R1", "苔の窪", listOf(SOLDIERS,
            listOf(QuestMobArchetype.SHIELD_GUARD, QuestMobArchetype.SOLDIER, QuestMobArchetype.RIFT_CASTER))),
        Plan("H", "散華の間", listOf(
            listOf(QuestMobArchetype.SHIELD_GUARD, QuestMobArchetype.RIFT_CASTER, QuestMobArchetype.SOLDIER, QuestMobArchetype.SOLDIER),
            listOf(QuestMobArchetype.ELITE_BRUTE, QuestMobArchetype.RIFT_CASTER, QuestMobArchetype.SHIELD_GUARD))),
        Plan("N", "古い祠の森", listOf(SOLDIERS,
            listOf(QuestMobArchetype.SHIELD_GUARD, QuestMobArchetype.SHIELD_GUARD, QuestMobArchetype.RIFT_CASTER, QuestMobArchetype.SOLDIER)),
            listOf("北", "枡形の北")),
        Plan("W", "滝裏の洞", listOf(
            listOf(QuestMobArchetype.SOLDIER, QuestMobArchetype.RIFT_CASTER, QuestMobArchetype.RIFT_CASTER),
            listOf(QuestMobArchetype.ELITE_BRUTE, QuestMobArchetype.SHIELD_GUARD, QuestMobArchetype.SOLDIER)),
            listOf("西", "枡形の南")),
    )
    val ROOM_NAMES get() = PLANS.map { it.name }

    private val folder: Path get() = ShippedMap.folder("dungeons/$MAP")

    fun create(tier: Int): InstanceDungeon {
        require(tier in 1..4)
        val rooms = JsonParser.parseString(Files.readString(folder.resolve("rooms.json"))).asJsonObject
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
            fun xyz(key: String, at: com.google.gson.JsonElement) = at.asJsonArray.map { it.asInt }.also { require(it.size == 3) { key } }
            val gateAt = rooms.getAsJsonObject("gates"); val doors = rooms.getAsJsonObject("doors")
            val seals = rooms.getAsJsonObject("seals")
            fun gate(name: String): DungeonGate {
                val (x, y, z) = xyz(name, gateAt[name]); val door = doors.getAsJsonObject(name)
                return doorway(instance, name, x, y, z, door["axis"].asString == "x", door["half"].asInt)
            }
            val gates = PLANS.map { gate(it.name) } + gate("庭")
            val list = PLANS.mapIndexed { i, plan ->
                val (cx, cz, radius) = rooms.getAsJsonObject("rooms")[plan.key].asJsonArray.map { it.asInt }
                val floor = floorAt(instance, cx, cz, gates[i].center.blockY())
                // On the gate's side, well inside the round: where whoever lagged behind is brought in.
                val door = gates[i].center
                val inward = Vec(cx + .5 - door.x(), 0.0, cz + .5 - door.z()).normalize()
                val ex = cx + .5 - inward.x() * (radius - 4); val ez = cz + .5 - inward.z() * (radius - 4)
                val entry = Pos(ex, floorAt(instance, ex.toInt(), ez.toInt(), floor).toDouble(), ez).withDirection(inward)
                DungeonRoom(plan.name, cx, cz, radius, floor, plan.waves, entry, plan.seals.map { xyz(it, seals[it]).let { (a, b, c) -> Vec(a.toDouble(), b.toDouble(), c.toDouble()) } })
            }
            gates.forEachIndexed { index, it -> it.set(instance, open = index == 0) }
            val spawn = xyz("spawn", rooms["spawn"]).let { (x, y, z) -> Pos(x + .5, y.toDouble(), z + .5) }
            val (ax, ay, az) = xyz("arena_center", rooms["arena_center"])
            val center = Pos(ax + .5, ay.toDouble(), az + .5)
            // Facing up the valley toward the first gate.
            val first = gates.first().center
            val layout = DungeonLayout(DISPLAY_NAME, spawn.withDirection(first.sub(spawn).withY(0.0)), list, gates,
                center, rooms["arena_r"].asDouble, QuestCombatPlacement.resolve(instance, center))
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
 * One dungeon run's progress: each room's waves start when a player walks in (the door shuts behind them), and its
 * last wave opens the next door. Past the last room the glade is open; walking in shuts it and wakes the boss.
 * Combat, rewards and the boss itself stay with QuestEncounterCombat and the root's run.
 */
internal class DungeonRun(
    private val instance: InstanceContainer,
    private val combat: QuestEncounterCombat,
    val layout: DungeonLayout,
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
    val bossEngaged: Boolean get() = stage == Stage.BOSS
    fun aliveEnemies() = enemies.count(combat::isAlive)

    fun tick() {
        val players = players()
        if (players.isEmpty()) return
        when (stage) {
            Stage.WALKING -> if (room < layout.rooms.size) {
                val r = layout.rooms[room]
                if (players.any { r.holds(it.position, margin = 2.5) }) {
                    gather(players, layout.gates[room], r.entry) { r.holds(it.position) }
                    stage = Stage.FIGHTING; wave = 0
                    announce(players, r.name, "${r.waves.size}つの波を退けよ", NamedTextColor.LIGHT_PURPLE)
                    spawnWave(r)
                }
            } else if (players.any { layout.inArena(it.position) }) {
                gather(players, layout.arenaGate, layout.arenaEntry.withDirection(layout.arenaCenter.sub(layout.arenaEntry))) { layout.inArena(it.position) }
                stage = Stage.BOSS
                announce(players, InstanceDungeonFactory.DISPLAY_NAME, "${combat.bossName()}が目を覚ました", NamedTextColor.RED)
            }
            Stage.FIGHTING -> if (enemies.none(combat::isAlive)) {
                val r = layout.rooms[room]
                combat.removeEncounter(enemies); enemies = emptySet()
                if (wave + 1 < r.waves.size) { wave++; spawnWave(r) }
                else {
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
            }
            Stage.BOSS -> Unit
        }
    }

    fun dispose() { combat.removeEncounter(spawned) }

    private fun spawnWave(r: DungeonRoom) {
        val types = r.waves[wave]
        // Across the middle and the far side of the room; the players come in at the gate side.
        val ahead = r.entry.direction().withY(0.0).normalize(); val side = Vec(-ahead.z(), 0.0, ahead.x())
        val offsets = listOf(-4.0 to 1.0, 4.0 to 1.0, 0.0 to 4.0, 0.0 to -2.0)
        val positions = offsets.take(types.size).map { (u, v) ->
            val at = r.center.add(side.mul(u)).add(ahead.mul(v))
            QuestCombatPlacement.resolve(instance, at.withY(InstanceDungeonFactory.floorAt(instance, at.blockX(), at.blockZ(), r.floor).toDouble()))
        }
        enemies = combat.spawnEncounter(QuestCombatEncounter(positions, types))
        spawned += enemies
        players().forEach { it.sendActionBar(Component.text("${r.name}・第${wave + 1}/${r.waves.size}波", NamedTextColor.GOLD)) }
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
