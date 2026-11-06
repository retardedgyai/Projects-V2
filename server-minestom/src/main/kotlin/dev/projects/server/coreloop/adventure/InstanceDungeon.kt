package dev.projects.server.coreloop.adventure

import dev.projects.server.coreloop.CoreDungeons
import dev.projects.server.coreloop.QuestCombatPlacement
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
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** A doorway's blocks: closed it is a wall the wave cannot be left through, open it is air. */
internal class DungeonGate(val blocks: List<Vec>, private val closed: Block) {
    val center: Pos get() = blocks.fold(Vec.ZERO) { sum, it -> sum.add(it) }.div(blocks.size.toDouble()).let { Pos(it.x() + .5, it.y(), it.z() + .5) }
    fun isOpen(instance: InstanceContainer) = instance.getBlock(blocks.first()).air()
    fun set(instance: InstanceContainer, open: Boolean) = blocks.forEach { instance.setBlock(it, if (open) Block.AIR else closed) }
}

/** A hand-built room (interior x1..x2, z1..z2, floor at y 39). Its waves come one after another. */
internal data class DungeonRoom(val name: String, val x1: Int, val x2: Int, val z1: Int, val z2: Int,
    val waves: List<List<QuestMobArchetype>>) {
    init { require(x1 < x2 && z1 < z2 && waves.all { it.size in 1..4 }) }
    val center: Pos get() = Pos((x1 + x2 + 1) / 2.0, 40.0, (z1 + z2 + 1) / 2.0)
    /** Inside and past the doorway by [margin] blocks (the entry is the low-z side). */
    fun holds(p: Pos, margin: Double = 0.0) = p.x() >= x1 && p.x() < x2 + 1 && p.z() >= z1 + margin && p.z() < z2 + 1 && p.y() in 39.0..47.0
}

/** gates[i] leads into rooms[i]; the last gate leads into the arena. */
internal class DungeonLayout(val name: String, val spawn: Pos, val rooms: List<DungeonRoom>, val gates: List<DungeonGate>,
    val arenaCenter: Pos, val bossSpawn: Pos) {
    init { require(gates.size == rooms.size + 1 && rooms.all { it.waves.isNotEmpty() }) }
    val arenaGate: DungeonGate get() = gates.last()
    fun inArena(p: Pos) = p.distanceSquared(arenaCenter.withY(p.y())) < ARENA_ENGAGE * ARENA_ENGAGE && p.z() > arenaCenter.z() - 21.0
    companion object { const val ARENA_ENGAGE = 19.0 }
}

internal class InstanceDungeon(val arena: BossArena, val layout: DungeonLayout)

/**
 * 散り桜の社: an entry hall, two rooms of waves, then 散り桜の庭 where 桜牙獣 waits. Built in code, like the boss
 * arenas: nothing imported, nothing persisted. One instance per run, unloaded when its last player leaves.
 */
internal object InstanceDungeonFactory {
    const val DISPLAY_NAME = "散り桜の社"

    // The building runs north (low z) to south: hall → room 1 → room 2 → the arena around (32, 32).
    private val HALL = DungeonRoom("入口の間", 28, 36, -37, -31, emptyList())
    val ROOMS = listOf(
        DungeonRoom("花守の回廊", 25, 39, -29, -13, listOf(
            listOf(QuestMobArchetype.SOLDIER, QuestMobArchetype.SOLDIER, QuestMobArchetype.RIFT_CASTER),
            listOf(QuestMobArchetype.SHIELD_GUARD, QuestMobArchetype.SOLDIER, QuestMobArchetype.RIFT_CASTER))),
        DungeonRoom("散華の間", 24, 40, -11, 7, listOf(
            listOf(QuestMobArchetype.SHIELD_GUARD, QuestMobArchetype.RIFT_CASTER, QuestMobArchetype.SOLDIER, QuestMobArchetype.SOLDIER),
            listOf(QuestMobArchetype.ELITE_BRUTE, QuestMobArchetype.RIFT_CASTER, QuestMobArchetype.SHIELD_GUARD))),
    )

    fun create(tier: Int): InstanceDungeon {
        require(tier in 1..4)
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setChunkSupplier(::LightingChunk)
        instance.time = 6_000L
        instance.defaultClock()?.pause()
        instance.setWeather(Weather.CLEAR)
        instance.setGenerator { unit ->
            unit.modifier().fillHeight(0, 39, Block.DEEPSLATE)
            val start = unit.absoluteStart()
            val end = unit.absoluteEnd()
            for (x in start.blockX() until end.blockX()) for (z in start.blockZ() until end.blockZ())
                if ((x - 32.0) * (x - 32.0) + (z - 32.0) * (z - 32.0) < 24.0 * 24.0)
                    BossArenaFactory.paintColumn("sakura", x, z) { bx, by, bz, block -> unit.modifier().setBlock(bx, by, bz, block) }
        }
        try {
            val chunks = (-1..5).flatMap { x -> (-3..5).map { z -> instance.loadChunk(x, z) } }
            CompletableFuture.allOf(*chunks.toTypedArray()).join()
            val gates = build(instance)
            val layout = DungeonLayout(DISPLAY_NAME, Pos(32.5, 40.0, -35.5, 0f, 0f), ROOMS, gates,
                Pos(32.5, 40.0, 32.5), Pos(32.5, 40.0, 32.5))
            return InstanceDungeon(BossArena(instance, layout.spawn, layout.bossSpawn, QuestMobArchetype.SAKURA_FANG,
                CoreDungeons.SAKURA, DISPLAY_NAME), layout)
        } catch (failure: Throwable) {
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
            throw failure
        }
    }

    private fun build(instance: InstanceContainer): List<DungeonGate> {
        fun put(x: Int, y: Int, z: Int, block: Block) = instance.setBlock(x, y, z, block)
        fun room(r: DungeonRoom) {
            for (x in r.x1 - 1..r.x2 + 1) for (z in r.z1 - 1..r.z2 + 1) {
                val wall = x == r.x1 - 1 || x == r.x2 + 1 || z == r.z1 - 1 || z == r.z2 + 1
                val corner = (x == r.x1 - 1 || x == r.x2 + 1) && (z == r.z1 - 1 || z == r.z2 + 1)
                // Posts every 4 blocks along the walls hold the beams; between them deepslate with a cherry band.
                val post = wall && (corner || (if (x == r.x1 - 1 || x == r.x2 + 1) Math.floorMod(z - r.z1, 4) == 3 else Math.floorMod(x - r.x1, 4) == 3))
                put(x, 39, z, when {
                    wall -> Block.POLISHED_DEEPSLATE
                    x == 32 -> Block.CHERRY_PLANKS
                    Math.floorMod(x * 7 + z * 13, 9) == 0 -> Block.MOSSY_STONE_BRICKS
                    else -> Block.STONE_BRICKS
                })
                for (y in 40..46) put(x, y, z, when {
                    !wall -> Block.AIR
                    post -> Block.STRIPPED_CHERRY_LOG
                    y == 40 -> Block.POLISHED_DEEPSLATE
                    y == 44 -> Block.CHERRY_PLANKS
                    else -> Block.DEEPSLATE_BRICKS
                })
                put(x, 47, z, if (!wall && Math.floorMod(x - r.x1, 4) == 2 && Math.floorMod(z - r.z1, 4) == 2) Block.PEARLESCENT_FROGLIGHT
                    else if (!wall && (x - r.x1) % 4 == 2 || !wall && (z - r.z1) % 4 == 2) Block.STRIPPED_DARK_OAK_LOG else Block.DARK_OAK_PLANKS)
            }
        }
        listOf(HALL).plus(ROOMS).forEach(::room)
        // Petals fallen along the cherry path, and cherry blossoms stood in the corners of each room.
        for (r in ROOMS) {
            for (z in r.z1..r.z2) if (Math.floorMod(z * 5, 3) == 0) put(31, 40, z, Block.PINK_PETALS.withProperty("flower_amount", "${1 + Math.floorMod(z, 4)}"))
            for ((x, z) in listOf(r.x1 to r.z1, r.x2 to r.z1, r.x1 to r.z2, r.x2 to r.z2)) {
                put(x, 40, z, Block.MOSS_BLOCK)
                put(x, 41, z, Block.CHERRY_LEAVES.withProperty("persistent", "true"))
            }
        }
        // Doorways 3 wide and 3 high (the arena's 4, through the leaf wall), framed by cherry posts.
        fun doorway(z1: Int, z2: Int, top: Int, closed: Block): DungeonGate {
            val blocks = mutableListOf<Vec>()
            for (z in z1..z2) for (x in 31..33) for (y in 40..top) blocks += Vec(x.toDouble(), y.toDouble(), z.toDouble())
            for (z in z1..z2) {
                for (y in 40..top + 1) { put(30, y, z, Block.STRIPPED_CHERRY_LOG); put(34, y, z, Block.STRIPPED_CHERRY_LOG) }
                for (x in 30..34) put(x, top + 1, z, Block.CHERRY_PLANKS)
                for (x in 31..33) put(x, 39, z, Block.CHERRY_PLANKS)
            }
            return DungeonGate(blocks, closed)
        }
        val gates = listOf(
            doorway(-30, -30, 42, Block.IRON_BARS),
            doorway(-12, -12, 42, Block.IRON_BARS),
            // The leaves close the glade like its wall; the boss cannot see the rooms through them.
            doorway(8, 10, 43, Block.CHERRY_LEAVES.withProperty("persistent", "true")),
        )
        gates.forEachIndexed { index, gate -> gate.set(instance, open = index == 0) }
        return gates
    }
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
                    gather(players, layout.gates[room], r.center.withZ(r.z1 + 1.5)) { r.holds(it.position) }
                    stage = Stage.FIGHTING; wave = 0
                    announce(players, r.name, "${r.waves.size}つの波を退けよ", NamedTextColor.LIGHT_PURPLE)
                    spawnWave(r)
                }
            } else if (players.any { layout.inArena(it.position) }) {
                gather(players, layout.arenaGate, layout.arenaCenter.add(0.0, 0.0, -16.0)) { layout.inArena(it.position) }
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
        // From the far side of the room, spread across it; the players come in at the near side.
        val offsets = listOf(-4.0 to 5.0, 4.0 to 5.0, 0.0 to 7.0, 0.0 to 3.0)
        val positions = offsets.take(types.size).map { (x, z) -> QuestCombatPlacement.resolve(instance, r.center.add(x, 0.0, z)) }
        enemies = combat.spawnEncounter(QuestCombatEncounter(positions, types))
        spawned += enemies
        players().forEach { it.sendActionBar(Component.text("${r.name}・第${wave + 1}/${r.waves.size}波", NamedTextColor.GOLD)) }
        positions.forEach { at -> for (n in 0..6) particle(at.add(0.0, n * .35, 0.0), Particle.CHERRY_LEAVES) }
    }

    /** Shut [gate] behind the party; whoever was left behind is brought through it (RuneForge-style). */
    private fun gather(players: List<Player>, gate: DungeonGate, inside: Pos, holds: (Player) -> Boolean) {
        players.filterNot(holds).forEach { it.teleport(inside.withView(0f, 0f)) }
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
