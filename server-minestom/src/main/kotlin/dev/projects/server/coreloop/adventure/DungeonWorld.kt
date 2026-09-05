package dev.projects.server.coreloop.adventure

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** Original modular Minecraft architecture. Broad, level arena cores remain free of decoration. */
class DungeonWorld(val plan: DungeonPlan, val instance: InstanceContainer) {
    private val disposed = AtomicBoolean()
    fun dispose() {
        if (disposed.get()) return
        check(instance.players.isEmpty()) { "先に全員を港へ移してください" }
        if (!disposed.compareAndSet(false, true)) return
        instance.entities.toList().forEach { it.remove() }
        if (instance.isRegistered) MinecraftServer.getInstanceManager().unregisterInstance(instance)
    }
    companion object {
        fun create(plan: DungeonPlan): DungeonWorld {
            plan.validate()
            val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
            instance.viewDistance(2)
            instance.time = 18000
            instance.setGenerator { unit ->
                val start = unit.absoluteStart(); val end = unit.absoluteEnd()
                val near = plan.rooms.filter { it.center.x() + 26 >= start.x() && it.center.x() - 26 < end.x() && it.center.z() + 26 >= start.z() && it.center.z() - 26 < end.z() }
                for (x in start.blockX() until end.blockX()) for (z in start.blockZ() until end.blockZ()) {
                    val room = near.firstOrNull { abs(x - it.center.blockX()) <= 24 && abs(z - it.center.blockZ()) <= 24 } ?: continue
                    val dx = abs(x - room.center.blockX()); val dz = abs(z - room.center.blockZ())
                    val wall = if (room.layout == DungeonLayout.OCTAGON) dx + dz >= 39 || maxOf(dx, dz) >= 23 else maxOf(dx, dz) >= 23
                    val floor = floorBlock(room, x, z)
                    unit.modifier().setBlock(x, 38, z, Block.DEEPSLATE)
                    unit.modifier().setBlock(x, 39, z, floor)
                    if (wall) for (y in 40..53) unit.modifier().setBlock(x, y, z, if (y % 5 == 0) Block.CHISELED_STONE_BRICKS else wallBlock(room))
                    // A vaulted ceiling hides future bosses; pillars stay outside the 18-block combat core.
                    if (maxOf(dx, dz) <= 23) unit.modifier().setBlock(x, 54 + (23 - maxOf(dx, dz)) / 5, z, wallBlock(room))
                    if (dx in 18..19 && dz in 13..14) {
                        for (y in 40..49) unit.modifier().setBlock(x, y, z, wallBlock(room))
                        unit.modifier().setBlock(x, 50, z, lightBlock(room))
                    }
                    if (dx == 21 && dz % 7 == 0) unit.modifier().setBlock(x, 44, z, lightBlock(room))
                    // Inlaid runes, bookcases, moss and furnace niches give the six compositions distinct edges.
                    if (!wall && maxOf(dx, dz) == 21 && (x + z) % 4 == 0) for (y in 40..42) {
                        val decor = when (room.layout) {
                            DungeonLayout.CLOISTER -> Block.MOSSY_STONE_BRICKS
                            DungeonLayout.VAULT -> Block.CHISELED_DEEPSLATE
                            DungeonLayout.GALLERY -> Block.BOOKSHELF
                            DungeonLayout.GARDEN -> if (y == 40) Block.MOSS_BLOCK else Block.AZALEA_LEAVES
                            DungeonLayout.CRUCIBLE -> Block.COPPER_BLOCK
                            else -> Block.AMETHYST_BLOCK
                        }
                        unit.modifier().setBlock(x, y, z, decor)
                    }
                }
            }
            try {
                // Minestom adds one border chunk to the configured view distance. Cover the
                // entire walkable room plus that 3-chunk radius, including off-center spawns.
                val chunks = plan.rooms.flatMap { room ->
                    ((room.center.blockX() - 68 shr 4)..(room.center.blockX() + 68 shr 4)).flatMap { x ->
                        ((room.center.blockZ() - 68 shr 4)..(room.center.blockZ() + 68 shr 4)).map { z -> x to z }
                    }
                }.distinct().map { (x, z) -> instance.loadChunk(x, z) }
                CompletableFuture.allOf(*chunks.toTypedArray()).join()
                return DungeonWorld(plan, instance)
            } catch (failure: Throwable) {
                MinecraftServer.getInstanceManager().unregisterInstance(instance); throw failure
            }
        }
        internal fun wallBlock(r: DungeonRoom) = when (r.theme) {
            DungeonTheme.EMBER -> Block.POLISHED_BLACKSTONE_BRICKS; DungeonTheme.TIDE -> Block.PRISMARINE_BRICKS; DungeonTheme.ASTRAL -> Block.DEEPSLATE_TILES
        }
        internal fun lightBlock(r: DungeonRoom) = when (r.theme) {
            DungeonTheme.EMBER -> Block.SHROOMLIGHT; DungeonTheme.TIDE -> Block.SEA_LANTERN; DungeonTheme.ASTRAL -> Block.PEARLESCENT_FROGLIGHT
        }
        internal fun floorBlock(r: DungeonRoom, x: Int, z: Int): Block {
            val dx = abs(x - r.center.blockX()); val dz = abs(z - r.center.blockZ())
            if (maxOf(dx, dz) in 16..17 || (dx == 0 || dz == 0) && maxOf(dx, dz) > 13) return when (r.theme) {
                DungeonTheme.EMBER -> Block.CUT_COPPER; DungeonTheme.TIDE -> Block.SMOOTH_QUARTZ; DungeonTheme.ASTRAL -> Block.POLISHED_ANDESITE
            }
            if (dx <= 1 && dz <= 1) return lightBlock(r)
            return when (r.theme) {
                DungeonTheme.EMBER -> if ((x + z) % 7 == 0) Block.CRACKED_POLISHED_BLACKSTONE_BRICKS else Block.POLISHED_BLACKSTONE
                DungeonTheme.TIDE -> if ((x + z) % 6 == 0) Block.DARK_PRISMARINE else Block.PRISMARINE_BRICKS
                DungeonTheme.ASTRAL -> if ((x + z) % 7 == 0) Block.CALCITE else Block.POLISHED_DEEPSLATE
            }
        }
        fun safe(room: DungeonRoom, position: Pos) = abs(position.x() - room.center.x()) <= 20 && abs(position.z() - room.center.z()) <= 20 && position.y() in 39.5..43.5
    }
}
