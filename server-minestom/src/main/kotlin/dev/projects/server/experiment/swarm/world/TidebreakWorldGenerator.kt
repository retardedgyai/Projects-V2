package dev.projects.server.experiment.swarm.world

import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.instance.generator.Generator
import kotlin.math.max
import kotlin.math.min

/** Deterministic fixed block plan; repeated generation produces the same bounded port. */
class TidebreakBlockPlan {
    fun blockAt(x: Int, y: Int, z: Int): Block {
        if (!TidebreakWorldSpec.worldBounds.contains(x, y, z)) return Block.AIR

        targetBlock(x, y, z)?.let { return it }
        structureBlock(x, y, z)?.let { return it }

        if (y in 36..38) return Block.DEEPSLATE
        if (y == 39) return Block.STONE
        if (y != TidebreakWorldSpec.GROUND_Y) return Block.AIR

        return when {
            inRect(x, z, -44, -14, -18, 18) -> Block.STONE_BRICKS
            inRect(x, z, -13, 21, 12, 44) -> tidalSurface(x, z)
            inRect(x, z, -8, 24, -44, -12) -> quarrySurface(x, z)
            inRect(x, z, 17, 47, -20, 20) -> breakwaterSurface(x, z)
            inRect(x, z, -14, 18, -3, 3) -> Block.OAK_PLANKS
            inRect(x, z, -4, 4, 3, 16) -> Block.OAK_PLANKS
            inRect(x, z, -4, 4, -16, -3) -> Block.OAK_PLANKS
            else -> Block.COARSE_DIRT
        }
    }

    private fun targetBlock(x: Int, y: Int, z: Int): Block? {
        TidebreakWorldSpec.oreNodes.firstOrNull { it.position.sameBlock(x, y, z) }?.let {
            return Block.RAW_IRON_BLOCK
        }
        TidebreakWorldSpec.supplierRecords.firstOrNull { it.position.sameBlock(x, y, z) }?.let {
            return Block.LECTERN
        }
        if (TidebreakWorldSpec.couplerRack.position.sameBlock(x, y, z)) return Block.COPPER_BLOCK
        if (TidebreakWorldSpec.practicePost.position.sameBlock(x, y, z)) return Block.CUT_COPPER
        return null
    }

    private fun structureBlock(x: Int, y: Int, z: Int): Block? {
        if (isCrashPillar(x, y, z)) return if (y == 47) Block.SEA_LANTERN else Block.REINFORCED_DEEPSLATE

        if (isPracticePost(x, y, z)) return if (y == 44) Block.LIGHTNING_ROD else Block.CUT_COPPER

        if (inRect(x, z, 29, 33, -4, 4) && y == 41) return Block.SMOOTH_STONE
        if (inRect(x, z, 42, 46, -4, 4) && y == 41) return Block.SMOOTH_STONE

        if (isMarketStall(x, y, z)) return Block.STRIPPED_OAK_LOG
        if (isCaveTunnel(x, y, z)) return Block.TUFF
        if (isQuarryWall(x, y, z)) return Block.TUFF

        if (isPerimeterRail(x, y, z)) return Block.OAK_FENCE
        return null
    }

    private fun tidalSurface(x: Int, z: Int): Block = when {
        z >= 40 || x >= 19 -> Block.WATER
        (x + z) % 3 == 0 -> Block.GRAVEL
        else -> Block.SAND
    }

    private fun quarrySurface(x: Int, z: Int): Block = when {
        inRect(x, z, 8, 15, -37, -26) -> Block.STONE
        (x - z) % 4 == 0 -> Block.GRAVEL
        else -> Block.TUFF
    }

    private fun breakwaterSurface(x: Int, z: Int): Block = when {
        x >= 45 && z !in -10..10 -> Block.WATER
        z !in -16..16 -> Block.WATER
        else -> Block.STONE_BRICKS
    }

    private fun isCrashPillar(x: Int, y: Int, z: Int): Boolean =
        y in 41..47 && TidebreakWorldSpec.crashPillars.any { pillar ->
            val px = pillar.position.blockX()
            val pz = pillar.position.blockZ()
            x in (px - 1)..(px + 1) && z in (pz - 1)..(pz + 1)
        }

    private fun isPracticePost(x: Int, y: Int, z: Int): Boolean {
        val post = TidebreakWorldSpec.practicePost.position
        return y in 41..44 && x in (post.blockX() - 1)..(post.blockX() + 1) && z in (post.blockZ() - 1)..(post.blockZ() + 1)
    }

    private fun isMarketStall(x: Int, y: Int, z: Int): Boolean {
        val corners = setOf(-31 to 7, -24 to 7, -31 to 12, -24 to 12)
        return y in 41..44 && (x to z) in corners
    }

    private fun isQuarryWall(x: Int, y: Int, z: Int): Boolean =
        y in 41..45 && (
            (z == -44 && x in -8..24) ||
                (x == 24 && z in -44..-24) ||
                (x == -8 && z in -44..-24)
            )

    private fun isCaveTunnel(x: Int, y: Int, z: Int): Boolean {
        if (z !in -37..-26) return false
        val sideWall = y in 41..44 && (x == 8 || x == 15)
        val roof = y == 45 && x in 8..15
        return sideWall || roof
    }

    private fun isPerimeterRail(x: Int, y: Int, z: Int): Boolean {
        if (y != 41) return false
        val bounds = TidebreakWorldSpec.worldBounds
        val onEdge = x == bounds.minX || x == bounds.maxX || z == bounds.minZ || z == bounds.maxZ
        return onEdge && blockAtWithoutStructures(x, TidebreakWorldSpec.GROUND_Y, z) != Block.WATER
    }

    private fun blockAtWithoutStructures(x: Int, y: Int, z: Int): Block {
        if (y != TidebreakWorldSpec.GROUND_Y) return Block.AIR
        return when {
            inRect(x, z, -44, -14, -18, 18) -> Block.STONE_BRICKS
            inRect(x, z, -13, 21, 12, 44) -> tidalSurface(x, z)
            inRect(x, z, -8, 24, -44, -12) -> quarrySurface(x, z)
            inRect(x, z, 17, 47, -20, 20) -> breakwaterSurface(x, z)
            else -> Block.COARSE_DIRT
        }
    }

    private fun inRect(x: Int, z: Int, minX: Int, maxX: Int, minZ: Int, maxZ: Int): Boolean =
        x in minX..maxX && z in minZ..maxZ

    private fun net.minestom.server.coordinate.Pos.sameBlock(x: Int, y: Int, z: Int): Boolean =
        blockX() == x && blockY() == y && blockZ() == z
}

class TidebreakWorldGenerator(
    private val plan: TidebreakBlockPlan = TidebreakBlockPlan(),
) : Generator {
    override fun generate(unit: GenerationUnit) {
        val bounds = TidebreakWorldSpec.worldBounds
        val start = unit.absoluteStart()
        val end = unit.absoluteEnd()
        val minX = max(bounds.minX, start.blockX())
        val maxX = min(bounds.maxX, end.blockX() - 1)
        val minZ = max(bounds.minZ, start.blockZ())
        val maxZ = min(bounds.maxZ, end.blockZ() - 1)
        if (minX > maxX || minZ > maxZ) return

        val modifier = unit.modifier()
        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                for (y in bounds.minY..bounds.maxY) {
                    val block = plan.blockAt(x, y, z)
                    if (block != Block.AIR) modifier.setBlock(x, y, z, block)
                }
            }
        }
    }
}
