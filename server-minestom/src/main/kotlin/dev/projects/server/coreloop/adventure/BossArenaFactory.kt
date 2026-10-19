package dev.projects.server.coreloop.adventure

import dev.projects.server.mob.QuestMobArchetype
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.Weather
import net.minestom.server.instance.block.Block
import net.minestom.server.registry.RegistryKey
import net.minestom.server.world.DimensionType
import net.minestom.server.world.attribute.EnvironmentAttribute
import net.minestom.server.world.clock.WorldClock
import net.minestom.server.world.timeline.Timeline
import net.minestom.server.registry.RegistryTag
import net.minestom.server.utils.EaseFunction
import net.kyori.adventure.util.RGBLike
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.format.TextColor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

data class BossArena(
    val instance: InstanceContainer,
    val playerSpawn: Pos,
    val bossSpawn: Pos,
    val archetype: QuestMobArchetype,
    val bossId: String,
    val displayName: String,
) {
    private val disposed = AtomicBoolean()
    /** Root moves players out and disposes QuestEncounterCombat before calling this. */
    fun dispose() {
        if (disposed.get() || !instance.isRegistered) return
        check(instance.players.isEmpty()) { "Move trial players out before unloading their arena" }
        if (!disposed.compareAndSet(false, true)) return
        instance.entities.toList().forEach { it.remove() }
        MinecraftServer.getInstanceManager().unregisterInstance(instance)
    }
}

/** Three concrete server-built arenas; no imported world or landscape mutation. */
object BossArenaFactory {
    /**
     * 翳の巣 lives in its own dimension type whose light turns violet when the wyrm enrages (26.3 environment
     * attributes): its timeline [SHADE_RAGE] runs on a clock of its own that stays at 0 (the ordinary night) until
     * the wyrm's enrage roar sends its wave out (QuestEncounterCombat), then blends block light, ambient light, fog
     * and sky light to violet over [RAGE_FADE_TICKS], the time the wave takes to reach the wall.
     */
    val SHADE_NEST: Key = Key.key("projects", "shade_nest")
    val SHADE_RAGE: Key = Key.key("projects", "shade_rage")
    const val RAGE_FADE_TICKS = 24

    /**
     * Registers the arena dimensions. Dimension types reach a client only when it joins, and an instance gets only
     * the clocks registered before it is made, so the server calls this once at start (CoreLoopServer); create()
     * calls it too for tests and tools that build an arena directly.
     */
    @Synchronized
    fun registerDimensions(): RegistryKey<DimensionType> {
        val registry = MinecraftServer.getDimensionTypeRegistry()
        registry.getKey(SHADE_NEST)?.let { return it }
        val clock = MinecraftServer.getWorldClockRegistry().register(SHADE_RAGE, WorldClock.create())
        fun <A> track(modifier: EnvironmentAttribute.Modifier<RGBLike, A>, calm: A, rage: A) =
            Timeline.Track(modifier, listOf(Timeline.Keyframe(0, calm), Timeline.Keyframe(RAGE_FADE_TICKS, rage)), EaseFunction.LINEAR)
        val override = EnvironmentAttribute.Modifier.Override(EnvironmentAttribute.BLOCK_LIGHT_TINT.valueCodec())
        @Suppress("UNCHECKED_CAST")                                       // composes with the day timeline's night
        val multiply = EnvironmentAttribute.Modifier.RGB_OPERATORS.getValue(EnvironmentAttribute.Modifier.Operator.MULTIPLY) as EnvironmentAttribute.Modifier<RGBLike, RGBLike>
        val white: RGBLike = TextColor.color(0xffffff)
        val rage = MinecraftServer.getTimelineRegistry().register(SHADE_RAGE, Timeline.builder().clock(clock)
            .track(EnvironmentAttribute.BLOCK_LIGHT_TINT, track(override, EnvironmentAttribute.BLOCK_LIGHT_TINT.defaultValue(), TextColor.color(SHADE_LIGHT)))
            .track(EnvironmentAttribute.AMBIENT_LIGHT_COLOR, track(override, TextColor.color(0x0a0a0a), TextColor.color(0x1c1128)))
            .track(EnvironmentAttribute.FOG_COLOR, track(multiply, white, TextColor.color(0xb080ff)))
            .track(EnvironmentAttribute.SKY_LIGHT_COLOR, track(multiply, white, TextColor.color(0xa070ff)))
            .build())
        val base = requireNotNull(registry.get(DimensionType.OVERWORLD))
        return registry.register(SHADE_NEST, DimensionType.builder(base)
            .timelines(RegistryTag.direct(Timeline.DAY, Timeline.MOON, Timeline.EARLY_GAME, rage))
            .build())
    }

    /** 翳の巣's rage clock (registered by [registerDimensions]). */
    fun rageClock(): RegistryKey<WorldClock> {
        registerDimensions()
        return requireNotNull(MinecraftServer.getWorldClockRegistry().getKey(SHADE_RAGE))
    }

    /** The violet every block light in 翳の巣 takes (vanilla mixes it toward white near light level 0 and 15). */
    const val SHADE_LIGHT = 0xb34cff

    fun create(bossId: String, tier: Int): BossArena {
        require(tier in 1..4)
        val archetype = when (bossId) {
            "rift" -> QuestMobArchetype.CINDER_REGENT
            "ritual" -> QuestMobArchetype.GLACIAL_COLOSSUS
            "trial" -> QuestMobArchetype.TEMPEST_HIEROPHANT
            // Dev-only so far (/bosstest); no fragment/currency entry exists for it yet.
            "shade" -> QuestMobArchetype.SHADE_WYRM
            "sakura" -> QuestMobArchetype.SAKURA_FANG
            else -> error("Unknown trial boss: $bossId")
        }
        val instance = if (bossId == "shade") MinecraftServer.getInstanceManager().createInstanceContainer(registerDimensions())
            else MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setChunkSupplier(::LightingChunk)
        instance.time = if (bossId == "trial" || bossId == "shade") 18_000L else 6_000L
        instance.defaultClock()?.pause()
        if (bossId == "shade") instance.clock(rageClock()).run { time(0); pause() }
        instance.setWeather(Weather.CLEAR)
        instance.setGenerator { unit ->
            unit.modifier().fillHeight(0, 39, Block.DEEPSLATE)
            val start = unit.absoluteStart()
            val end = unit.absoluteEnd()
            for (x in start.blockX() until end.blockX()) for (z in start.blockZ() until end.blockZ()) {
                val dx = x - 32.0
                val dz = z - 32.0
                val distance = sqrt(dx * dx + dz * dz)
                val floor = when (bossId) {
                    "rift" -> if (abs(dx) < 2 || abs(dz) < 2) Block.POLISHED_BLACKSTONE else Block.RED_NETHER_BRICKS
                    "ritual" -> if (distance in 4.5..5.5 || distance in 15.5..16.5) Block.BLUE_ICE else Block.SMOOTH_QUARTZ
                    // The wyrm's nest: pale tuff (it takes the violet light), sculk drifts, amethyst stains and a sparse grid
                    // of crying obsidian (light 10) so the violet light of its effects is what lights the floor.
                    "shade" -> when {
                        Math.floorMod(x, 8) == 0 && Math.floorMod(z, 8) == 0 -> Block.CRYING_OBSIDIAN
                        distance < 3.5 || Math.floorMod(x * 31 + z * 17, 11) == 0 -> Block.SCULK
                        Math.floorMod(x * 7 + z * 13, 9) == 0 -> Block.AMETHYST_BLOCK
                        else -> Block.POLISHED_TUFF
                    }
                    // 桜牙獣's glade: moss and grass strewn with fallen petals, a ring of cherry planks.
                    "sakura" -> when {
                        distance in 9.5..10.5 -> Block.CHERRY_PLANKS
                        Math.floorMod(x * 7 + z * 13, 5) == 0 -> Block.PINK_WOOL
                        Math.floorMod(x * 11 + z * 3, 4) == 0 -> Block.MOSS_BLOCK
                        else -> Block.GRASS_BLOCK
                    }
                    else -> if ((x + z) % 7 == 0) Block.SEA_LANTERN else Block.PRISMARINE_BRICKS
                }
                unit.modifier().setBlock(x, 39, z, floor)
                if (distance in 22.0..23.5) for (y in 40..43) unit.modifier().setBlock(x, y, z,
                    when (bossId) { "ritual" -> Block.BLUE_ICE; "sakura" -> Block.CHERRY_LEAVES; else -> Block.POLISHED_DEEPSLATE })
                // 散り桜の庭's pillars are wider: they are the cover from 桜牙獣's 千本桜
                val pillar = if (bossId == "sakura") 1.6 else 1.0
                if (distance in 19.0..20.5 && (abs(dx) < pillar || abs(dz) < pillar)) {
                    for (y in 40..44) unit.modifier().setBlock(x, y, z, Block.POLISHED_DEEPSLATE)
                    unit.modifier().setBlock(x, 45, z, when (bossId) {
                        "rift" -> Block.SHROOMLIGHT; "shade" -> Block.PEARLESCENT_FROGLIGHT; "sakura" -> Block.PEARLESCENT_FROGLIGHT; else -> Block.SEA_LANTERN })
                }
            }
        }
        try {
            val chunks = (-1..5).flatMap { x -> (-1..5).map { z -> instance.loadChunk(x, z) } }
            CompletableFuture.allOf(*chunks.toTypedArray()).join()
            return BossArena(instance, Pos(32.5, 40.0, 16.5), Pos(32.5, 40.0, 32.5), archetype, bossId,
                when (bossId) { "rift" -> "灰燼の溶炉"; "ritual" -> "氷獄の円庭"; "shade" -> "翳の巣"; "sakura" -> "散り桜の庭"; else -> "天雷の祭壇" })
        } catch (failure: Throwable) {
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
            throw failure
        }
    }
}
