package dev.projects.server.questmap

import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.instance.generator.Generator
import net.minestom.server.world.biome.Biome

internal class VerdantRoadQuestGenerator(
    private val plan: QuestMapPlan,
) : Generator {
    override fun generate(unit: GenerationUnit) {
        val start = unit.absoluteStart()
        unit.modifier().fillBiome(biomeAt(start.blockX() + 8, start.blockZ() + 8))
        unit.modifier().setAllRelative { relativeX, relativeY, relativeZ ->
            blockAt(
                start.blockX() + relativeX,
                start.blockY() + relativeY,
                start.blockZ() + relativeZ,
            )
        }
    }

    private fun biomeAt(x: Int, z: Int) = when (plan.style) {
        QuestTerrainStyle.VERDANT -> Biome.FOREST
        QuestTerrainStyle.HIGHLANDS -> Biome.WINDSWEPT_HILLS
        QuestTerrainStyle.SALTMARSH -> Biome.MANGROVE_SWAMP
        QuestTerrainStyle.CLIFFLANDS -> Biome.STONY_PEAKS
        QuestTerrainStyle.SAKURA_GROVE -> Biome.CHERRY_GROVE
        QuestTerrainStyle.INFERNAL -> {
            if (x in 0 until plan.size && z in 0 until plan.size && plan.groundCoverAt(x, z) == QuestGroundCover.ROCKY) {
                Biome.BASALT_DELTAS
            } else {
                Biome.CRIMSON_FOREST
            }
        }
    }

    private fun blockAt(x: Int, y: Int, z: Int): Block {
        if (x !in 0 until plan.size || z !in 0 until plan.size) return outerSeaBlock(x, y, z)
        val ground = plan.heightAt(x, z)
        val neighborHeights = listOf(
            plan.heightAt((x - 1).coerceAtLeast(0), z),
            plan.heightAt((x + 1).coerceAtMost(plan.size - 1), z),
            plan.heightAt(x, (z - 1).coerceAtLeast(0)),
            plan.heightAt(x, (z + 1).coerceAtMost(plan.size - 1)),
        )
        val exposedCliff = neighborHeights.min() <= ground - 3
        val mainRoad = plan.mainRoadDistanceSquaredAt(x, z) <= 3 * 3
        val sideTrail = !mainRoad && plan.roadDistanceSquaredAt(x, z) <= 1 * 1
        val road = mainRoad || sideTrail
        val boundary = x == 0 || z == 0 || x == plan.size - 1 || z == plan.size - 1
        if (boundary && y in ground + 1..ground + 4) return Block.BARRIER
        if (y > ground) {
            return when {
                plan.style == QuestTerrainStyle.SALTMARSH && !road && y <= QUEST_WATER_LEVEL -> Block.WATER
                plan.style == QuestTerrainStyle.INFERNAL && !road && y <= QUEST_WATER_LEVEL -> Block.LAVA
                else -> Block.AIR
            }
        }
        if (y < ground - 4) return when (plan.style) {
            QuestTerrainStyle.HIGHLANDS, QuestTerrainStyle.CLIFFLANDS -> Block.TUFF
            QuestTerrainStyle.INFERNAL -> Block.BLACKSTONE
            else -> Block.STONE
        }
        if (y < ground) {
            if (exposedCliff && neighborHeights.min() < y) {
                return when (plan.style) {
                    QuestTerrainStyle.VERDANT -> if ((x + y + z) and 3 == 0) Block.ANDESITE else Block.STONE
                    QuestTerrainStyle.HIGHLANDS -> if ((x + y + z) and 3 == 0) Block.COBBLESTONE else Block.TUFF
                    QuestTerrainStyle.SALTMARSH -> if ((x + y + z) and 3 == 0) Block.MUD_BRICKS else Block.STONE
                    QuestTerrainStyle.CLIFFLANDS -> {
                        val regionalOffset = Math.floorMod(
                            plan.seed xor ((x / 24) * 341_873L) xor ((z / 24) * 712_619L),
                            7L,
                        ).toInt()
                        val stratum = Math.floorMod(y + regionalOffset, 15)
                        val calciteVein = Math.floorMod(
                            plan.seed xor ((x / 10) * 1_299_721L) xor ((z / 10) * 741_457L),
                            29L,
                        ) == 0L
                        when {
                            calciteVein && stratum in 0..2 -> Block.CALCITE
                            stratum in 7..8 -> Block.ANDESITE
                            else -> Block.STONE
                        }
                    }
                    QuestTerrainStyle.SAKURA_GROVE -> if ((x + y + z) and 3 == 0) Block.ANDESITE else Block.STONE
                    QuestTerrainStyle.INFERNAL -> if ((x + y + z) and 3 == 0) Block.BASALT else Block.BLACKSTONE
                }
            }
            return subsurfaceBlock(x, z, ground)
        }
        if (mainRoad) {
            val variation = Math.floorMod(plan.seed xor (x * 734_287L) xor (z * 912_271L), 13L).toInt()
            val core = plan.mainRoadDistanceSquaredAt(x, z) <= 1
            return when (plan.style) {
                QuestTerrainStyle.SALTMARSH -> when {
                    core && variation < 3 -> Block.MUD_BRICKS
                    core -> Block.PACKED_MUD
                    variation < 4 -> Block.MUD
                    else -> Block.PACKED_MUD
                }
                QuestTerrainStyle.HIGHLANDS -> when {
                    core && variation < 3 -> Block.COBBLESTONE
                    core -> Block.GRAVEL
                    variation < 5 -> Block.ANDESITE
                    else -> Block.GRAVEL
                }
                QuestTerrainStyle.VERDANT -> when {
                    core && variation < 2 -> Block.GRAVEL
                    core -> Block.DIRT_PATH
                    // Worn shoulders in coherent patches, not a salt-and-pepper fringe.
                    questClump(plan.seed, x, z, 3.0, 31) > 0.25 -> Block.COARSE_DIRT
                    else -> Block.DIRT_PATH
                }
                QuestTerrainStyle.CLIFFLANDS -> when {
                    core && variation < 3 -> Block.COBBLESTONE
                    core -> Block.GRAVEL
                    variation < 5 -> Block.COARSE_DIRT
                    else -> Block.ANDESITE
                }
                QuestTerrainStyle.SAKURA_GROVE -> when {
                    core && variation < 2 -> Block.GRAVEL
                    core -> Block.DIRT_PATH
                    variation < 5 -> Block.ROOTED_DIRT
                    else -> Block.DIRT_PATH
                }
                QuestTerrainStyle.INFERNAL -> when {
                    core && variation < 3 -> Block.POLISHED_BLACKSTONE
                    core -> Block.BLACKSTONE
                    variation < 5 -> Block.SOUL_SOIL
                    else -> Block.BASALT
                }
            }
        }
        if (sideTrail) {
            return when (plan.style) {
                QuestTerrainStyle.SALTMARSH -> Block.MUD
                QuestTerrainStyle.HIGHLANDS -> Block.PODZOL
                QuestTerrainStyle.VERDANT -> Block.ROOTED_DIRT
                QuestTerrainStyle.CLIFFLANDS -> Block.COARSE_DIRT
                QuestTerrainStyle.SAKURA_GROVE -> Block.ROOTED_DIRT
                QuestTerrainStyle.INFERNAL -> Block.SOUL_SOIL
            }
        }
        return surfaceBlock(x, z, ground, exposedCliff)
    }

    private fun subsurfaceBlock(x: Int, z: Int, ground: Int): Block {
        if (plan.style == QuestTerrainStyle.INFERNAL) {
            return if (((x + z + ground) and 4) == 0) Block.BASALT else Block.NETHERRACK
        }
        if (plan.style == QuestTerrainStyle.CLIFFLANDS) {
            return if (plan.groundCoverAt(x, z) == QuestGroundCover.ROCKY) Block.STONE else Block.COARSE_DIRT
        }
        return when (plan.groundCoverAt(x, z)) {
            QuestGroundCover.ROCKY -> if (plan.style == QuestTerrainStyle.HIGHLANDS) Block.TUFF else Block.STONE
            QuestGroundCover.SHORE -> if (ground <= QUEST_WATER_LEVEL - 2) Block.CLAY else Block.MUD
            QuestGroundCover.PEAT -> if ((x + z) and 3 == 0) Block.PACKED_MUD else Block.MUD
            QuestGroundCover.HEATH -> if (plan.style == QuestTerrainStyle.HIGHLANDS && ground >= 66) Block.STONE else Block.DIRT
            QuestGroundCover.MEADOW,
            QuestGroundCover.FOREST_FLOOR -> Block.DIRT
        }
    }

    private fun surfaceBlock(x: Int, z: Int, ground: Int, exposedCliff: Boolean): Block {
        if (exposedCliff) {
            return when (plan.style) {
                QuestTerrainStyle.VERDANT -> if (plan.surfacePatchAt(x, z) <= 1) Block.MOSSY_COBBLESTONE else Block.ANDESITE
                QuestTerrainStyle.HIGHLANDS -> if (plan.surfacePatchAt(x, z) <= 1) Block.TUFF else Block.STONE
                QuestTerrainStyle.SALTMARSH -> if (ground <= QUEST_WATER_LEVEL + 1) Block.MUD_BRICKS else Block.STONE
                QuestTerrainStyle.CLIFFLANDS -> when (plan.surfacePatchAt(x, z)) {
                    0 -> Block.CALCITE
                    1, 2 -> Block.ANDESITE
                    else -> Block.STONE
                }
                QuestTerrainStyle.SAKURA_GROVE -> if (plan.surfacePatchAt(x, z) <= 1) Block.MOSSY_COBBLESTONE else Block.ANDESITE
                QuestTerrainStyle.INFERNAL -> if (plan.surfacePatchAt(x, z) <= 1) Block.BASALT else Block.BLACKSTONE
            }
        }
        val patch = plan.surfacePatchAt(x, z)
        val variation = Math.floorMod(plan.seed xor (x * 1_299_721L) xor (z * 741_457L), 19L).toInt()
        if (plan.style == QuestTerrainStyle.CLIFFLANDS) {
            return when (plan.groundCoverAt(x, z)) {
                QuestGroundCover.ROCKY -> when {
                    patch <= 1 -> Block.STONE
                    patch == 2 -> Block.ANDESITE
                    patch == 3 && variation < 3 -> Block.CALCITE
                    patch == 4 && variation == 0 -> Block.TERRACOTTA
                    patch >= 4 -> Block.ANDESITE
                    else -> Block.STONE
                }
                QuestGroundCover.HEATH -> if (variation < 7) Block.COARSE_DIRT else Block.GRASS_BLOCK
                QuestGroundCover.SHORE -> if (ground <= QUEST_WATER_LEVEL) Block.GRAVEL else Block.COARSE_DIRT
                else -> when {
                    patch <= 1 -> Block.COARSE_DIRT
                    variation < 4 -> Block.ROOTED_DIRT
                    else -> Block.GRASS_BLOCK
                }
            }
        }
        if (plan.style == QuestTerrainStyle.SAKURA_GROVE) {
            return when (plan.groundCoverAt(x, z)) {
                QuestGroundCover.ROCKY -> if (patch <= 1) Block.CALCITE else Block.ANDESITE
                QuestGroundCover.FOREST_FLOOR -> when {
                    patch <= 1 -> Block.PODZOL
                    patch == 2 -> Block.MOSS_BLOCK
                    variation < 4 -> Block.ROOTED_DIRT
                    else -> Block.GRASS_BLOCK
                }
                QuestGroundCover.SHORE -> if (ground <= QUEST_WATER_LEVEL) Block.GRAVEL else Block.MOSS_BLOCK
                else -> if (patch == 0 && variation < 8) Block.MOSS_BLOCK else Block.GRASS_BLOCK
            }
        }
        if (plan.style == QuestTerrainStyle.INFERNAL) {
            return when (plan.groundCoverAt(x, z)) {
                QuestGroundCover.ROCKY -> when {
                    patch <= 1 -> Block.BASALT
                    patch == 2 -> Block.BLACKSTONE
                    variation == 0 -> Block.MAGMA_BLOCK
                    else -> Block.NETHERRACK
                }
                QuestGroundCover.SHORE -> if (ground <= QUEST_WATER_LEVEL) Block.MAGMA_BLOCK else Block.BLACKSTONE
                QuestGroundCover.PEAT -> if (patch <= 2) Block.CRIMSON_NYLIUM else Block.NETHERRACK
                QuestGroundCover.HEATH -> Block.SOUL_SOIL
                else -> if (patch == 0) Block.WARPED_NYLIUM else Block.CRIMSON_NYLIUM
            }
        }
        if (plan.style == QuestTerrainStyle.VERDANT) verdantSurface(x, z)?.let { return it }
        return when (plan.groundCoverAt(x, z)) {
            QuestGroundCover.MEADOW -> when {
                patch == 0 && variation < 13 -> Block.MOSS_BLOCK
                patch == 5 && variation < 6 -> Block.COARSE_DIRT
                variation == 0 -> Block.ROOTED_DIRT
                else -> Block.GRASS_BLOCK
            }
            QuestGroundCover.FOREST_FLOOR -> when {
                patch <= 1 && variation < 14 -> Block.PODZOL
                patch == 2 && variation < 12 -> Block.ROOTED_DIRT
                patch >= 4 && variation < 11 -> Block.MOSS_BLOCK
                variation <= 2 -> Block.COARSE_DIRT
                else -> Block.GRASS_BLOCK
            }
            QuestGroundCover.SHORE -> when {
                ground <= QUEST_WATER_LEVEL - 2 && patch <= 2 -> Block.CLAY
                ground <= QUEST_WATER_LEVEL - 1 && patch >= 4 -> Block.GRAVEL
                ground <= QUEST_WATER_LEVEL -> Block.MUD
                patch == 0 -> Block.SAND
                patch == 5 -> Block.GRAVEL
                else -> Block.MUD
            }
            QuestGroundCover.ROCKY -> when {
                plan.style == QuestTerrainStyle.HIGHLANDS && patch <= 2 -> Block.TUFF
                patch == 0 -> Block.COBBLESTONE
                patch == 1 -> Block.MOSSY_COBBLESTONE
                patch >= 4 -> Block.ANDESITE
                else -> Block.STONE
            }
            QuestGroundCover.HEATH -> when {
                patch <= 1 -> Block.PODZOL
                patch == 2 -> Block.COARSE_DIRT
                patch == 3 && variation < 12 -> Block.GRASS_BLOCK
                patch >= 4 && variation < 10 -> Block.MOSS_BLOCK
                else -> Block.ROOTED_DIRT
            }
            QuestGroundCover.PEAT -> when {
                ground <= QUEST_WATER_LEVEL -> Block.MUD
                patch <= 1 -> Block.PACKED_MUD
                patch <= 3 -> Block.MOSS_BLOCK
                patch == 4 -> Block.MUD
                else -> Block.ROOTED_DIRT
            }
        }
    }

    /** Verdant ground in coherent clumps: moss pools, podzol under canopies, worn heath. Null = default rules. */
    private fun verdantSurface(x: Int, z: Int): Block? {
        val broad = questClump(plan.seed, x, z, 9.0, 11)
        val fine = questClump(plan.seed, x, z, 4.5, 12)
        return when (plan.groundCoverAt(x, z)) {
            QuestGroundCover.MEADOW -> when {
                fine > 0.58 && broad > 0.0 -> Block.MOSS_BLOCK
                else -> Block.GRASS_BLOCK
            }
            QuestGroundCover.FOREST_FLOOR -> when {
                broad > 0.5 && fine > -0.2 -> Block.PODZOL
                fine > 0.4 -> Block.MOSS_BLOCK
                broad < -0.62 && fine < -0.2 -> Block.ROOTED_DIRT
                else -> Block.GRASS_BLOCK
            }
            QuestGroundCover.HEATH -> when {
                broad > 0.38 -> Block.COARSE_DIRT
                fine > 0.45 -> Block.MOSS_BLOCK
                else -> Block.GRASS_BLOCK
            }
            else -> null
        }
    }

    private fun outerSeaBlock(x: Int, y: Int, z: Int): Block {
        val floorVariation = Math.floorMod(plan.seed xor (x * 341_873L) xor (z * 712_619L), 3L).toInt() - 1
        val floor = QUEST_WATER_LEVEL - 3 + floorVariation
        if (plan.style == QuestTerrainStyle.INFERNAL) {
            return when {
                y < floor - 3 -> Block.BLACKSTONE
                y < floor -> Block.BASALT
                y == floor -> if ((x + z) and 3 == 0) Block.MAGMA_BLOCK else Block.BLACKSTONE
                y <= QUEST_WATER_LEVEL -> Block.LAVA
                else -> Block.AIR
            }
        }
        return when {
            y < floor - 3 -> Block.STONE
            y < floor -> Block.DIRT
            y == floor -> if ((x + z) and 1 == 0) Block.SAND else Block.GRAVEL
            y <= QUEST_WATER_LEVEL -> Block.WATER
            else -> Block.AIR
        }
    }
}
