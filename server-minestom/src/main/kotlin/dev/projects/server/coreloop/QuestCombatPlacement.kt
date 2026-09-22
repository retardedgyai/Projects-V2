package dev.projects.server.coreloop

import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance

/** Resolve against decorated blocks, not the terrain heightmap: arenas contain real structures. */
internal object QuestCombatPlacement {
    fun resolve(instance: Instance, desired: Pos): Pos {
        val offsets = (-7..7).flatMap { x -> (-7..7).map { z -> x to z } }
            .sortedBy { (x, z) -> x * x + z * z }
        // Prefer the terrain-level floor over a roof or a landmark's top.
        for (dy in listOf(0, 1, -1, 2, -2)) for ((dx, dz) in offsets) {
            val point = Pos(desired.blockX() + dx + 0.5, desired.blockY() + dy.toDouble(), desired.blockZ() + dz + 0.5)
            if (clear(instance, point)) return point
        }
        error("No walkable enemy spawn near $desired")
    }

    fun clear(instance: Instance, point: Pos): Boolean =
        instance.getBlock(point.sub(0.0, 0.1, 0.0)).isSolid &&
            listOf(-0.35, 0.35).all { dx -> listOf(-0.35, 0.35).all { dz ->
                listOf(0.05, 0.95, 1.9, 2.7).all { dy ->
                    val block = instance.getBlock(point.add(dx, dy, dz))
                    !block.isSolid && !block.isLiquid
                }
            } }
}
