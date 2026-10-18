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
    /** 翳の巣 lives in its own dimension type (26.3 environment attributes): block light tinted violet, a violet night. */
    val SHADE_NEST: Key = Key.key("projects", "shade_nest")

    /**
     * Registers the arena dimensions. Dimension types reach a client only when it joins, so the server calls this
     * once at start (CoreLoopServer); create() calls it too for tests and tools that build an arena directly.
     */
    @Synchronized
    fun registerDimensions(): RegistryKey<DimensionType> {
        val registry = MinecraftServer.getDimensionTypeRegistry()
        registry.getKey(SHADE_NEST)?.let { return it }
        val base = requireNotNull(registry.get(DimensionType.OVERWORLD))
        return registry.register(SHADE_NEST, DimensionType.builder(base)
            .setAttribute(EnvironmentAttribute.BLOCK_LIGHT_TINT, TextColor.color(SHADE_LIGHT))
            .setAttribute(EnvironmentAttribute.AMBIENT_LIGHT_COLOR, TextColor.color(0x1c1128))
            .setAttribute(EnvironmentAttribute.FOG_COLOR, TextColor.color(0x140b20))
            .setAttribute(EnvironmentAttribute.SKY_COLOR, TextColor.color(0x0b0612))
            .setAttribute(EnvironmentAttribute.SKY_LIGHT_COLOR, TextColor.color(0x3a2660))
            .build())
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
            else -> error("Unknown trial boss: $bossId")
        }
        val instance = if (bossId == "shade") MinecraftServer.getInstanceManager().createInstanceContainer(registerDimensions())
            else MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setChunkSupplier(::LightingChunk)
        instance.time = if (bossId == "trial" || bossId == "shade") 18_000L else 6_000L
        instance.defaultClock()?.pause()
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
                    else -> if ((x + z) % 7 == 0) Block.SEA_LANTERN else Block.PRISMARINE_BRICKS
                }
                unit.modifier().setBlock(x, 39, z, floor)
                if (distance in 22.0..23.5) for (y in 40..43) unit.modifier().setBlock(x, y, z,
                    if (bossId == "ritual") Block.BLUE_ICE else Block.POLISHED_DEEPSLATE)
                if (distance in 19.0..20.5 && (abs(dx) < 1 || abs(dz) < 1)) {
                    for (y in 40..44) unit.modifier().setBlock(x, y, z, Block.POLISHED_DEEPSLATE)
                    unit.modifier().setBlock(x, 45, z, when (bossId) {
                        "rift" -> Block.SHROOMLIGHT; "shade" -> Block.PEARLESCENT_FROGLIGHT; else -> Block.SEA_LANTERN })
                }
            }
        }
        try {
            val chunks = (-1..5).flatMap { x -> (-1..5).map { z -> instance.loadChunk(x, z) } }
            CompletableFuture.allOf(*chunks.toTypedArray()).join()
            return BossArena(instance, Pos(32.5, 40.0, 16.5), Pos(32.5, 40.0, 32.5), archetype, bossId,
                when (bossId) { "rift" -> "灰燼の溶炉"; "ritual" -> "氷獄の円庭"; "shade" -> "翳の巣"; else -> "天雷の祭壇" })
        } catch (failure: Throwable) {
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
            throw failure
        }
    }
}
