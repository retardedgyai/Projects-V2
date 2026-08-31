package dev.projects.server.questmap

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.ServerFlag
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.Weather
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.instance.generator.Generator
import net.minestom.server.world.biome.Biome
import java.util.Random
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

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
                    variation < 5 -> Block.COARSE_DIRT
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

internal object VerdantRoadQuestDecorator {
    fun decorate(instance: InstanceContainer, plan: QuestMapPlan) {
        val scenicAnchors = scenicAnchors(plan)
        decorateScenicCompositions(instance, plan, scenicAnchors)
        decorateTrees(instance, plan, scenicAnchors)
        decorateTerrainDetail(instance, plan, scenicAnchors)
        decorateWaterEdges(instance, plan)
        decorateRoadGuidance(instance, plan)
        plan.contents.forEachIndexed { ordinal, content ->
            when (content.kind) {
                QuestMapContentKind.START -> decorateStart(instance, plan, content.position)
                QuestMapContentKind.COMBAT -> decorateCombat(instance, plan, content.position, ordinal)
                QuestMapContentKind.GATHERING -> decorateGathering(instance, plan, content.position, ordinal)
                QuestMapContentKind.DISCOVERY -> decorateDiscovery(instance, plan, content.position, ordinal)
                QuestMapContentKind.BOSS -> decorateBossArena(instance, plan, content.position)
            }
        }
    }

    private fun scenicAnchors(plan: QuestMapPlan): List<QuestMapPoint> {
        val random = Random(plan.seed xor 0x5343454E49434CL)
        val result = mutableListOf<QuestMapPoint>()
        repeat(4_000) {
            if (result.size >= 34) return@repeat
            val point = QuestMapPoint(
                22 + random.nextInt(plan.size - 44),
                22 + random.nextInt(plan.size - 44),
            )
            val roadDistance = plan.roadDistanceSquaredAt(point.x, point.z)
            if (roadDistance !in 22 * 22..62 * 62) return@repeat
            if (plan.contents.any { it.position.distanceSquared(point) < 20 * 20 }) return@repeat
            if (result.any { it.distanceSquared(point) < 34 * 34 }) return@repeat
            if (terrainRange(plan, point, 8) > 5 || plan.slopeAt(point) > 2) return@repeat
            result += point
        }
        return result
    }

    /** Keeps the entire authored silhouette, roots included, outside the walkable road shoulder. */
    internal fun clearForStructure(
        plan: QuestMapPlan,
        point: QuestMapPoint,
        footprint: Int,
        shoulder: Int = 7,
    ): Boolean {
        val border = footprint + 2
        if (point.x !in border until plan.size - border || point.z !in border until plan.size - border) return false
        val required = footprint + shoulder
        return plan.roadDistanceSquaredAt(point.x, point.z) > required * required
    }

    private fun decorateScenicCompositions(
        instance: Instance,
        plan: QuestMapPlan,
        anchors: List<QuestMapPoint>,
    ) {
        val random = Random(plan.seed xor 0x564953544153L)
        anchors.forEachIndexed { ordinal, center ->
            val cover = plan.groundCoverAt(center)
            val palette = scenePalette(plan.style)
            val groundPatch = when (cover) {
                QuestGroundCover.ROCKY -> palette.stonePolished
                QuestGroundCover.FOREST_FLOOR -> if (plan.style == QuestTerrainStyle.INFERNAL) Block.CRIMSON_NYLIUM else Block.PODZOL
                QuestGroundCover.PEAT -> if (plan.style == QuestTerrainStyle.INFERNAL) Block.NETHERRACK else Block.MOSS_BLOCK
                QuestGroundCover.SHORE -> if (plan.style == QuestTerrainStyle.INFERNAL) Block.BLACKSTONE else Block.MUD
                QuestGroundCover.HEATH -> palette.pathAccent
                QuestGroundCover.MEADOW -> if (plan.style == QuestTerrainStyle.SAKURA_GROVE) Block.MOSS_BLOCK else Block.GRASS_BLOCK
            }
            for (dz in -9..9) {
                for (dx in -9..9) {
                    val distance = dx * dx + dz * dz
                    if (distance > 9 * 9 || Math.floorMod(dx * 11 + dz * 7 + ordinal, 9) > 3) continue
                    val point = QuestMapPoint(center.x + dx, center.z + dz)
                    if (point.x !in 2 until plan.size - 2 || point.z !in 2 until plan.size - 2) continue
                    if (plan.roadDistanceSquaredAt(point.x, point.z) <= 6 * 6) continue
                    paintSurface(instance, plan, point, groundPatch)
                }
            }

            val turns = random.nextInt(4)
            val mirrored = random.nextBoolean()
            fun compositionPoint(dx: Int, dz: Int): QuestMapPoint {
                val mirrorX = if (mirrored) -dx else dx
                val (rotatedX, rotatedZ) = when (turns) {
                    0 -> mirrorX to dz
                    1 -> -dz to mirrorX
                    2 -> -mirrorX to -dz
                    else -> dz to -mirrorX
                }
                return QuestMapPoint(center.x + rotatedX, center.z + rotatedZ)
            }
            val placements = listOf(
                -7 to -3,
                6 to -5,
                -4 to 7,
                7 to 5,
                0 to 0,
            ).map { (dx, dz) -> compositionPoint(dx, dz) }
            when (cover) {
                QuestGroundCover.ROCKY -> {
                    if (clearForStructure(plan, center, 11)) {
                        QuestMapStructureAssets.placeRockOutcrop(instance, plan, center, ordinal * 101, turns)
                    }
                    val shrubPoint = compositionPoint(-10, 2)
                    if (plan.style != QuestTerrainStyle.CLIFFLANDS && clearForStructure(plan, shrubPoint, 4)) {
                        QuestMapStructureAssets.placeShrubCluster(instance, plan, shrubPoint, ordinal, ordinal)
                    }
                }
                QuestGroundCover.FOREST_FLOOR -> {
                    placements.take(3).forEachIndexed { index, point ->
                        val variation = ordinal * 37 + index
                        val footprint = QuestMapStructureAssets.treeFootprint(plan.style, variation)
                        if (clearForStructure(plan, point, footprint) && terrainRange(plan, point, 5) <= 3) {
                            QuestMapStructureAssets.placeTree(instance, plan, point, variation, random.nextInt(4))
                        }
                    }
                    val logLength = 6 + ordinal % 3
                    if (clearForStructure(plan, placements[3], logLength)) {
                        QuestMapStructureAssets.placeFallenLog(instance, plan, placements[3], logLength, ordinal, ordinal)
                    }
                    if (clearForStructure(plan, placements[4], 4)) {
                        QuestMapStructureAssets.placeShrubCluster(instance, plan, placements[4], ordinal * 13, ordinal)
                    }
                }
                QuestGroundCover.SHORE, QuestGroundCover.PEAT -> {
                    placements.take(2).forEachIndexed { index, point ->
                        val variation = ordinal * 41 + index
                        val footprint = QuestMapStructureAssets.treeFootprint(plan.style, variation)
                        if (clearForStructure(plan, point, footprint) && plan.heightAt(point) > QUEST_WATER_LEVEL && terrainRange(plan, point, 4) <= 3) {
                            QuestMapStructureAssets.placeTree(instance, plan, point, variation, random.nextInt(4))
                        }
                    }
                    placements.drop(2).forEachIndexed { index, point ->
                        if (clearForStructure(plan, point, 4)) {
                            QuestMapStructureAssets.placeShrubCluster(instance, plan, point, ordinal * 17 + index, index)
                        }
                    }
                }
                QuestGroundCover.MEADOW, QuestGroundCover.HEATH -> {
                    val treeVariation = ordinal * 43
                    val treeFootprint = QuestMapStructureAssets.treeFootprint(plan.style, treeVariation)
                    if (clearForStructure(plan, placements[0], treeFootprint) && terrainRange(plan, placements[0], 5) <= 3) {
                        QuestMapStructureAssets.placeTree(instance, plan, placements[0], treeVariation, random.nextInt(4))
                    }
                    if (clearForStructure(plan, placements[1], 6)) {
                        QuestMapStructureAssets.placeBoulder(instance, plan, placements[1], ordinal * 47, ordinal)
                    }
                    if (plan.style == QuestTerrainStyle.CLIFFLANDS) {
                        val secondVariation = ordinal * 53 + 1
                        val secondFootprint = QuestMapStructureAssets.treeFootprint(plan.style, secondVariation)
                        if (clearForStructure(plan, placements[2], secondFootprint) && terrainRange(plan, placements[2], 5) <= 3) {
                            QuestMapStructureAssets.placeTree(instance, plan, placements[2], secondVariation, random.nextInt(4))
                        }
                        if (clearForStructure(plan, placements[3], 11)) {
                            QuestMapStructureAssets.placeRockOutcrop(instance, plan, placements[3], ordinal * 59, ordinal + 2)
                        }
                        if (clearForStructure(plan, placements[4], 6)) {
                            QuestMapStructureAssets.placeFallenLog(instance, plan, placements[4], 6, ordinal, ordinal)
                        }
                    } else {
                        placements.drop(2).forEachIndexed { index, point ->
                            if (clearForStructure(plan, point, 4)) {
                                QuestMapStructureAssets.placeShrubCluster(instance, plan, point, ordinal * 19 + index, index)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun decorateTrees(instance: Instance, plan: QuestMapPlan, scenicAnchors: List<QuestMapPoint>) {
        val random = Random(plan.seed xor 0x47524F5645L)
        val occupied = mutableListOf<Pair<QuestMapPoint, Int>>()
        val groveCenters = List(
            when (plan.style) {
                QuestTerrainStyle.VERDANT -> 38
                QuestTerrainStyle.HIGHLANDS -> 30
                QuestTerrainStyle.SALTMARSH -> 28
                QuestTerrainStyle.CLIFFLANDS -> 26
                QuestTerrainStyle.SAKURA_GROVE -> 44
                QuestTerrainStyle.INFERNAL -> 31
            },
        ) {
            QuestMapPoint(
                18 + random.nextInt(plan.size - 36),
                18 + random.nextInt(plan.size - 36),
            )
        }
        val attempts = when (plan.style) {
            QuestTerrainStyle.VERDANT -> 2_900
            QuestTerrainStyle.HIGHLANDS -> 2_100
            QuestTerrainStyle.SALTMARSH -> 1_850
            QuestTerrainStyle.CLIFFLANDS -> 1_700
            QuestTerrainStyle.SAKURA_GROVE -> 3_450
            QuestTerrainStyle.INFERNAL -> 2_000
        }
        repeat(attempts) {
            val point = if (random.nextInt(100) < 78) {
                val grove = groveCenters[random.nextInt(groveCenters.size)]
                val angle = random.nextDouble() * Math.PI * 2.0
                val radius = 5.0 + random.nextDouble() * 38.0
                QuestMapPoint(
                    (grove.x + Math.cos(angle) * radius).roundToInt().coerceIn(10, plan.size - 11),
                    (grove.z + Math.sin(angle) * radius).roundToInt().coerceIn(10, plan.size - 11),
                )
            } else {
                QuestMapPoint(10 + random.nextInt(plan.size - 20), 10 + random.nextInt(plan.size - 20))
            }
            if (plan.contents.any { it.position.distanceSquared(point) < 11 * 11 }) return@repeat
            if (scenicAnchors.any { it.distanceSquared(point) < 13 * 13 }) return@repeat
            val variation = random.nextInt()
            val footprint = QuestMapStructureAssets.treeFootprint(plan.style, variation)
            if (!clearForStructure(plan, point, footprint)) return@repeat
            val clearance = maxOf(7, footprint + 2)
            if (occupied.any { (other, otherClearance) -> other.distanceSquared(point) < maxOf(clearance, otherClearance).let { it * it } }) return@repeat
            if (plan.style in setOf(QuestTerrainStyle.SALTMARSH, QuestTerrainStyle.INFERNAL) && plan.heightAt(point) <= QUEST_WATER_LEVEL) return@repeat
            val density = when (plan.groundCoverAt(point)) {
                QuestGroundCover.FOREST_FLOOR -> 88
                QuestGroundCover.PEAT -> when (plan.style) {
                    QuestTerrainStyle.SALTMARSH, QuestTerrainStyle.INFERNAL -> 58
                    else -> 20
                }
                QuestGroundCover.HEATH -> if (plan.style == QuestTerrainStyle.CLIFFLANDS) 18 else 28
                QuestGroundCover.MEADOW -> if (plan.style == QuestTerrainStyle.SAKURA_GROVE) 30 else 18
                QuestGroundCover.SHORE -> if (plan.style == QuestTerrainStyle.SALTMARSH) 32 else 9
                QuestGroundCover.ROCKY -> 6
            }
            if (random.nextInt(100) >= density) return@repeat
            if (terrainRange(plan, point, footprint.coerceIn(2, 6)) > 2 || plan.slopeAt(point) > 1) return@repeat
            occupied += point to clearance
            QuestMapStructureAssets.placeTree(
                instance,
                plan,
                point,
                variation,
                random.nextInt(4),
            )
            decorateTreeBase(instance, plan, point, random)
        }
    }

    private fun decorateTerrainDetail(instance: Instance, plan: QuestMapPlan, scenicAnchors: List<QuestMapPoint>) {
        val random = Random(plan.seed xor 0x5445525241494EL)
        val occupiedScenes = mutableListOf<QuestMapPoint>()
        repeat(10_500) {
            val point = QuestMapPoint(8 + random.nextInt(plan.size - 16), 8 + random.nextInt(plan.size - 16))
            if (plan.roadDistanceSquaredAt(point.x, point.z) <= 4 * 4) return@repeat
            if (plan.contents.any { it.position.distanceSquared(point) < 6 * 6 }) return@repeat
            if (scenicAnchors.any { it.distanceSquared(point) < 8 * 8 }) return@repeat
            val ground = plan.heightAt(point)
            if (plan.style in setOf(QuestTerrainStyle.SALTMARSH, QuestTerrainStyle.INFERNAL) && ground <= QUEST_WATER_LEVEL) {
                if (plan.style == QuestTerrainStyle.SALTMARSH && random.nextInt(8) == 0) {
                    instance.setBlock(point.x, QUEST_WATER_LEVEL + 1, point.z, Block.LILY_PAD)
                } else if (plan.style == QuestTerrainStyle.INFERNAL && random.nextInt(10) == 0) {
                    instance.setBlock(point.x, ground, point.z, Block.MAGMA_BLOCK)
                }
                return@repeat
            }
            val cover = plan.groundCoverAt(point)
            val roll = random.nextInt(100)
            val assetRotation = random.nextInt(4)
            fun sceneClear(radius: Int): Boolean = occupiedScenes.none { it.distanceSquared(point) < radius * radius }
            fun rememberScene() {
                occupiedScenes += point
            }
            if (plan.style == QuestTerrainStyle.INFERNAL) {
                when {
                    roll < 8 && clearForStructure(plan, point, 6) && sceneClear(8) && terrainRange(plan, point, 4) <= 3 -> {
                        QuestMapStructureAssets.placeBoulder(instance, plan, point, random.nextInt(), assetRotation)
                        rememberScene()
                    }
                    roll < 18 && clearForStructure(plan, point, 4) && sceneClear(6) && terrainRange(plan, point, 3) <= 2 -> {
                        QuestMapStructureAssets.placeShrubCluster(instance, plan, point, random.nextInt(), assetRotation)
                        rememberScene()
                    }
                    roll < 30 -> instance.setBlock(point.x, ground + 1, point.z, Block.CRIMSON_ROOTS)
                    roll < 40 -> instance.setBlock(point.x, ground + 1, point.z, Block.WARPED_ROOTS)
                    roll < 48 -> instance.setBlock(point.x, ground + 1, point.z, Block.CRIMSON_FUNGUS)
                    roll < 54 -> instance.setBlock(point.x, ground + 1, point.z, Block.WARPED_FUNGUS)
                    roll < 58 -> instance.setBlock(point.x, ground + 1, point.z, Block.FIRE)
                }
                return@repeat
            }
            when (cover) {
                QuestGroundCover.ROCKY -> when {
                    roll < 12 && clearForStructure(plan, point, 6) && sceneClear(7) && terrainRange(plan, point, 4) <= 3 -> {
                        QuestMapStructureAssets.placeBoulder(instance, plan, point, random.nextInt(), assetRotation)
                        rememberScene()
                    }
                    plan.style != QuestTerrainStyle.CLIFFLANDS && roll < 25 && clearForStructure(plan, point, 4) && sceneClear(6) && terrainRange(plan, point, 3) <= 2 -> {
                        QuestMapStructureAssets.placeShrubCluster(instance, plan, point, random.nextInt(), assetRotation)
                        rememberScene()
                    }
                    roll < 58 -> instance.setBlock(
                        point.x,
                        ground,
                        point.z,
                        when (plan.style) {
                            QuestTerrainStyle.HIGHLANDS -> Block.TUFF
                            QuestTerrainStyle.CLIFFLANDS -> if (roll and 1 == 0) Block.CALCITE else Block.ANDESITE
                            QuestTerrainStyle.SAKURA_GROVE -> Block.MOSSY_COBBLESTONE
                            else -> Block.ANDESITE
                        },
                    )
                    else -> instance.setBlock(point.x, ground + 1, point.z, Block.DEAD_BUSH)
                }
                QuestGroundCover.FOREST_FLOOR -> when {
                    roll < 5 && clearForStructure(plan, point, 7) && sceneClear(8) && lineTerrainRange(plan, point, 3, assetRotation) <= 1 -> {
                        QuestMapStructureAssets.placeFallenLog(instance, plan, point, 3 + random.nextInt(4), assetRotation, random.nextInt())
                        rememberScene()
                    }
                    roll < 13 && clearForStructure(plan, point, 4) && sceneClear(6) && terrainRange(plan, point, 3) <= 2 -> {
                        QuestMapStructureAssets.placeShrubCluster(instance, plan, point, random.nextInt(), assetRotation)
                        rememberScene()
                    }
                    plan.style == QuestTerrainStyle.SAKURA_GROVE && roll < 26 -> instance.setBlock(point.x, ground + 1, point.z, Block.PINK_PETALS)
                    roll < 30 -> instance.setBlock(point.x, ground + 1, point.z, Block.FERN)
                    roll < 42 -> instance.setBlock(point.x, ground + 1, point.z, if (roll and 1 == 0) Block.BROWN_MUSHROOM else Block.RED_MUSHROOM)
                    roll < 54 -> instance.setBlock(point.x, ground + 1, point.z, Block.MOSS_CARPET)
                    else -> instance.setBlock(point.x, ground + 1, point.z, Block.SHORT_GRASS)
                }
                QuestGroundCover.MEADOW -> when {
                    roll < 2 && clearForStructure(plan, point, 6) && sceneClear(8) && terrainRange(plan, point, 3) <= 2 -> {
                        QuestMapStructureAssets.placeBoulder(instance, plan, point, random.nextInt(), assetRotation)
                        rememberScene()
                    }
                    roll < 7 && clearForStructure(plan, point, 4) && sceneClear(7) && terrainRange(plan, point, 3) <= 2 -> {
                        QuestMapStructureAssets.placeShrubCluster(instance, plan, point, random.nextInt(), assetRotation)
                        rememberScene()
                    }
                    roll < 9 -> instance.setBlock(point.x, ground + 1, point.z, Block.DANDELION)
                    roll < 16 -> instance.setBlock(point.x, ground + 1, point.z, Block.POPPY)
                    roll < 22 -> instance.setBlock(point.x, ground + 1, point.z, Block.AZURE_BLUET)
                    roll < 29 -> instance.setBlock(point.x, ground + 1, point.z, Block.TALL_GRASS)
                    else -> instance.setBlock(point.x, ground + 1, point.z, Block.SHORT_GRASS)
                }
                QuestGroundCover.HEATH -> when {
                    roll < 5 && clearForStructure(plan, point, 6) && sceneClear(8) && terrainRange(plan, point, 3) <= 3 -> {
                        QuestMapStructureAssets.placeBoulder(instance, plan, point, random.nextInt(), assetRotation)
                        rememberScene()
                    }
                    plan.style != QuestTerrainStyle.CLIFFLANDS && roll < 17 && clearForStructure(plan, point, 4) && sceneClear(6) && terrainRange(plan, point, 3) <= 2 -> {
                        QuestMapStructureAssets.placeShrubCluster(instance, plan, point, random.nextInt(), assetRotation)
                        rememberScene()
                    }
                    roll < 25 -> instance.setBlock(point.x, ground + 1, point.z, Block.SWEET_BERRY_BUSH)
                    roll < 30 -> instance.setBlock(point.x, ground + 1, point.z, Block.FERN)
                    roll < 42 -> instance.setBlock(point.x, ground + 1, point.z, Block.DEAD_BUSH)
                    roll < 52 -> instance.setBlock(point.x, ground + 1, point.z, Block.MOSS_CARPET)
                    else -> instance.setBlock(point.x, ground + 1, point.z, Block.SHORT_GRASS)
                }
                QuestGroundCover.PEAT -> when {
                    roll < 18 -> instance.setBlock(point.x, ground + 1, point.z, Block.MANGROVE_ROOTS)
                    roll < 34 -> instance.setBlock(point.x, ground + 1, point.z, Block.FERN)
                    roll < 48 -> instance.setBlock(point.x, ground + 1, point.z, Block.BROWN_MUSHROOM)
                    else -> instance.setBlock(point.x, ground + 1, point.z, Block.MOSS_CARPET)
                }
                QuestGroundCover.SHORE -> if (ground > QUEST_WATER_LEVEL) {
                    when {
                        roll < 20 -> instance.setBlock(point.x, ground + 1, point.z, Block.FERN)
                        roll < 36 -> instance.setBlock(point.x, ground + 1, point.z, Block.MANGROVE_ROOTS)
                        roll < 48 -> instance.setBlock(point.x, ground + 1, point.z, Block.MOSS_CARPET)
                        else -> instance.setBlock(point.x, ground + 1, point.z, Block.SHORT_GRASS)
                    }
                }
            }
        }
    }

    private fun decorateWaterEdges(instance: Instance, plan: QuestMapPlan) {
        val random = Random(plan.seed xor 0x5741544552454447L)
        repeat(4_000) {
            val point = QuestMapPoint(8 + random.nextInt(plan.size - 16), 8 + random.nextInt(plan.size - 16))
            if (plan.roadDistanceSquaredAt(point.x, point.z) <= 4 * 4) return@repeat
            if (plan.contents.any { it.position.distanceSquared(point) < 7 * 7 }) return@repeat
            val waterDistance = plan.waterDistanceAt(point)
            if (waterDistance > 4) return@repeat
            val ground = plan.heightAt(point)
            if (plan.style == QuestTerrainStyle.INFERNAL) {
                when {
                    waterDistance == 0 && ground <= QUEST_WATER_LEVEL && random.nextInt(4) == 0 ->
                        instance.setBlock(point.x, ground, point.z, if (random.nextBoolean()) Block.MAGMA_BLOCK else Block.BLACKSTONE)
                    waterDistance in 1..2 && ground > QUEST_WATER_LEVEL -> when (random.nextInt(6)) {
                        0 -> instance.setBlock(point.x, ground + 1, point.z, Block.CRIMSON_ROOTS)
                        1 -> instance.setBlock(point.x, ground + 1, point.z, Block.WARPED_ROOTS)
                        2 -> instance.setBlock(point.x, ground + 1, point.z, Block.CRIMSON_FUNGUS)
                        3 -> instance.setBlock(point.x, ground, point.z, Block.MAGMA_BLOCK)
                        4 -> instance.setBlock(point.x, ground, point.z, Block.BASALT)
                        else -> instance.setBlock(point.x, ground + 1, point.z, Block.FIRE)
                    }
                    waterDistance in 3..4 && ground > QUEST_WATER_LEVEL && random.nextInt(3) == 0 ->
                        instance.setBlock(point.x, ground + 1, point.z, if (random.nextBoolean()) Block.CRIMSON_ROOTS else Block.WARPED_ROOTS)
                }
                return@repeat
            }
            when {
                waterDistance == 0 && ground <= QUEST_WATER_LEVEL && random.nextInt(5) == 0 ->
                    instance.setBlock(point.x, QUEST_WATER_LEVEL + 1, point.z, Block.LILY_PAD)
                waterDistance in 1..2 && ground > QUEST_WATER_LEVEL -> when (random.nextInt(5)) {
                    0 -> instance.setBlock(point.x, ground + 1, point.z, Block.FERN)
                    1 -> instance.setBlock(point.x, ground + 1, point.z, Block.MANGROVE_ROOTS)
                    2 -> instance.setBlock(point.x, ground + 1, point.z, Block.MOSS_CARPET)
                    3 -> instance.setBlock(point.x, ground, point.z, Block.CLAY)
                    else -> instance.setBlock(point.x, ground + 1, point.z, Block.SHORT_GRASS)
                }
                waterDistance in 3..4 && ground > QUEST_WATER_LEVEL && random.nextBoolean() ->
                    instance.setBlock(point.x, ground + 1, point.z, if (random.nextBoolean()) Block.TALL_GRASS else Block.FERN)
            }
        }
    }

    private fun decorateTreeBase(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint, random: Random) {
        repeat(5 + random.nextInt(7)) {
            val dx = random.nextInt(7) - 3
            val dz = random.nextInt(7) - 3
            if (dx * dx + dz * dz <= 1) return@repeat
            val x = center.x + dx
            val z = center.z + dz
            if (x !in 1 until plan.size - 1 || z !in 1 until plan.size - 1) return@repeat
            val ground = plan.heightAt(x, z)
            val block = when (plan.style) {
                QuestTerrainStyle.SAKURA_GROVE -> when (random.nextInt(7)) {
                    0, 1 -> Block.PINK_PETALS
                    2 -> Block.FLOWERING_AZALEA
                    3 -> Block.MOSS_CARPET
                    4 -> Block.FERN
                    else -> Block.SHORT_GRASS
                }
                QuestTerrainStyle.CLIFFLANDS -> when (random.nextInt(6)) {
                    0 -> Block.DEAD_BUSH
                    1 -> Block.FERN
                    2 -> Block.MOSS_CARPET
                    else -> Block.SHORT_GRASS
                }
                QuestTerrainStyle.INFERNAL -> when (random.nextInt(7)) {
                    0, 1 -> Block.CRIMSON_ROOTS
                    2 -> Block.WARPED_ROOTS
                    3 -> Block.CRIMSON_FUNGUS
                    4 -> Block.WARPED_FUNGUS
                    else -> Block.NETHER_SPROUTS
                }
                else -> when (random.nextInt(6)) {
                    0 -> Block.BROWN_MUSHROOM
                    1, 2 -> Block.FERN
                    3 -> Block.MOSS_CARPET
                    else -> Block.SHORT_GRASS
                }
            }
            instance.setBlock(x, ground + 1, z, block)
        }
    }

    private data class ScenePalette(
        val path: Block,
        val pathAccent: Block,
        val stone: Block,
        val stoneCracked: Block,
        val stonePolished: Block,
        val timber: Block,
        val roof: Block,
        val light: Block,
        val carpet: Block,
    )

    private fun scenePalette(style: QuestTerrainStyle): ScenePalette = when (style) {
        QuestTerrainStyle.VERDANT -> ScenePalette(Block.DIRT_PATH, Block.COARSE_DIRT, Block.MOSSY_STONE_BRICKS, Block.CRACKED_STONE_BRICKS, Block.STONE_BRICKS, Block.STRIPPED_OAK_LOG, Block.DARK_OAK_SLAB, Block.LANTERN, Block.GREEN_CARPET)
        QuestTerrainStyle.HIGHLANDS -> ScenePalette(Block.GRAVEL, Block.PODZOL, Block.TUFF_BRICKS, Block.CRACKED_STONE_BRICKS, Block.POLISHED_TUFF, Block.STRIPPED_SPRUCE_LOG, Block.SPRUCE_SLAB, Block.SOUL_LANTERN, Block.BROWN_CARPET)
        QuestTerrainStyle.SALTMARSH -> ScenePalette(Block.PACKED_MUD, Block.MUD, Block.MUD_BRICKS, Block.MOSSY_STONE_BRICKS, Block.PACKED_MUD, Block.STRIPPED_MANGROVE_LOG, Block.MANGROVE_SLAB, Block.LANTERN, Block.GREEN_CARPET)
        QuestTerrainStyle.CLIFFLANDS -> ScenePalette(Block.GRAVEL, Block.COARSE_DIRT, Block.STONE_BRICKS, Block.CRACKED_STONE_BRICKS, Block.POLISHED_ANDESITE, Block.STRIPPED_SPRUCE_LOG, Block.SPRUCE_SLAB, Block.SOUL_LANTERN, Block.GRAY_CARPET)
        QuestTerrainStyle.SAKURA_GROVE -> ScenePalette(Block.DIRT_PATH, Block.ROOTED_DIRT, Block.MOSSY_STONE_BRICKS, Block.CRACKED_STONE_BRICKS, Block.POLISHED_ANDESITE, Block.STRIPPED_CHERRY_LOG, Block.CHERRY_SLAB, Block.LANTERN, Block.PINK_CARPET)
        QuestTerrainStyle.INFERNAL -> ScenePalette(Block.POLISHED_BLACKSTONE, Block.SOUL_SOIL, Block.POLISHED_BLACKSTONE_BRICKS, Block.CRACKED_POLISHED_BLACKSTONE_BRICKS, Block.POLISHED_BASALT, Block.STRIPPED_CRIMSON_STEM, Block.CRIMSON_SLAB, Block.SOUL_LANTERN, Block.RED_CARPET)
    }

    private fun terrainRange(plan: QuestMapPlan, center: QuestMapPoint, radius: Int): Int {
        var minimum = Int.MAX_VALUE
        var maximum = Int.MIN_VALUE
        for (z in center.z - radius..center.z + radius) {
            for (x in center.x - radius..center.x + radius) {
                if (x !in 0 until plan.size || z !in 0 until plan.size) continue
                val height = plan.heightAt(x, z)
                minimum = minOf(minimum, height)
                maximum = maxOf(maximum, height)
            }
        }
        return maximum - minimum
    }

    private fun lineTerrainRange(plan: QuestMapPlan, start: QuestMapPoint, length: Int, rotation: Int): Int {
        val points = (0 until length).map { offset ->
            when (Math.floorMod(rotation, 4)) {
                0 -> QuestMapPoint(start.x + offset, start.z)
                1 -> QuestMapPoint(start.x, start.z + offset)
                2 -> QuestMapPoint(start.x - offset, start.z)
                else -> QuestMapPoint(start.x, start.z - offset)
            }
        }.filter { it.x in 0 until plan.size && it.z in 0 until plan.size }
        return points.maxOf(plan::heightAt) - points.minOf(plan::heightAt)
    }

    private data class RouteFrame(
        val forwardX: Int,
        val forwardZ: Int,
        val sideX: Int,
        val sideZ: Int,
    )

    private fun routeFrame(plan: QuestMapPlan, center: QuestMapPoint): RouteFrame {
        val nearest = plan.mainRoute.indices.minBy { plan.mainRoute[it].distanceSquared(center) }
        val before = plan.mainRoute[(nearest - 6).coerceAtLeast(0)]
        val after = plan.mainRoute[(nearest + 6).coerceAtMost(plan.mainRoute.lastIndex)]
        val deltaX = after.x - before.x
        val deltaZ = after.z - before.z
        val forwardX: Int
        val forwardZ: Int
        if (kotlin.math.abs(deltaX) >= kotlin.math.abs(deltaZ)) {
            forwardX = if (deltaX >= 0) 1 else -1
            forwardZ = 0
        } else {
            forwardX = 0
            forwardZ = if (deltaZ >= 0) 1 else -1
        }
        return RouteFrame(forwardX, forwardZ, -forwardZ, forwardX)
    }

    private fun framedPoint(center: QuestMapPoint, frame: RouteFrame, forward: Int, side: Int): QuestMapPoint =
        QuestMapPoint(
            center.x + frame.forwardX * forward + frame.sideX * side,
            center.z + frame.forwardZ * forward + frame.sideZ * side,
        )

    private fun setGrounded(
        instance: Instance,
        plan: QuestMapPlan,
        point: QuestMapPoint,
        dy: Int,
        block: Block,
    ) {
        if (point.x !in 1 until plan.size - 1 || point.z !in 1 until plan.size - 1) return
        instance.setBlock(point.x, plan.heightAt(point) + 1 + dy, point.z, block)
    }

    private fun paintSurface(
        instance: Instance,
        plan: QuestMapPlan,
        point: QuestMapPoint,
        block: Block,
    ) {
        if (point.x !in 1 until plan.size - 1 || point.z !in 1 until plan.size - 1) return
        instance.setBlock(point.x, plan.heightAt(point), point.z, block)
    }

    private fun decorateRoadGuidance(instance: Instance, plan: QuestMapPlan) {
        val interval = maxOf(40, plan.mainRoute.size / 9)
        var markerOrdinal = 0
        for (routeIndex in interval until plan.mainRoute.lastIndex - interval step interval) {
            val point = plan.mainRoute[routeIndex]
            val before = plan.mainRoute[(routeIndex - 4).coerceAtLeast(0)]
            val after = plan.mainRoute[(routeIndex + 4).coerceAtMost(plan.mainRoute.lastIndex)]
            val side = if (markerOrdinal and 1 == 0) 1 else -1
            val sideX = (after.z - before.z).coerceIn(-1, 1) * 5 * side
            val sideZ = (before.x - after.x).coerceIn(-1, 1) * 5 * side
            val preferred = QuestMapPoint(
                (point.x + sideX).coerceIn(3, plan.size - 4),
                (point.z + sideZ).coerceIn(3, plan.size - 4),
            )
            val opposite = QuestMapPoint(
                (point.x - sideX).coerceIn(3, plan.size - 4),
                (point.z - sideZ).coerceIn(3, plan.size - 4),
            )
            val marker = listOf(preferred, opposite).minBy { terrainRange(plan, it, 2) }
            if (terrainRange(plan, marker, 2) <= 2) {
                QuestMapStructureAssets.placeRoadsideMarker(
                    instance,
                    plan,
                    marker,
                    (plan.seed xor routeIndex.toLong() xor markerOrdinal.toLong()).toInt(),
                    markerOrdinal,
                )
            }
            markerOrdinal++
        }
    }

    private fun decorateStart(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint) {
        val frame = routeFrame(plan, center)
        val palette = scenePalette(plan.style)
        val camp = framedPoint(center, frame, 0, 6)
        for (forward in -4..4) {
            for (side in -4..4) {
                if (forward * forward + side * side > 20) continue
                val point = framedPoint(camp, frame, forward, side)
                val block = when (Math.floorMod(forward * 7 + side * 11, 9)) {
                    0, 1 -> palette.pathAccent
                    2 -> palette.stonePolished
                    else -> palette.path
                }
                paintSurface(instance, plan, point, block)
            }
        }

        val fire = framedPoint(camp, frame, 1, -1)
        setGrounded(instance, plan, fire, 0, Block.CAMPFIRE)
        listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).forEach { (forward, side) ->
            setGrounded(instance, plan, framedPoint(fire, frame, forward, side), 0, palette.stone)
        }

        // A compact field-work canopy with an obvious crafting/storage purpose.
        val shelter = framedPoint(camp, frame, -1, 2)
        listOf(-2 to -2, -2 to 2, 2 to -2, 2 to 2).forEach { (forward, side) ->
            val post = framedPoint(shelter, frame, forward, side)
            repeat(3) { height -> setGrounded(instance, plan, post, height, palette.timber) }
        }
        for (forward in -2..2) {
            for (side in -2..2) {
                val roof = framedPoint(shelter, frame, forward, side)
                setGrounded(instance, plan, roof, 3, palette.roof)
            }
        }
        setGrounded(instance, plan, framedPoint(shelter, frame, 0, 1), 0, Block.CRAFTING_TABLE)
        setGrounded(instance, plan, framedPoint(shelter, frame, -1, 1), 0, Block.BARREL)
        setGrounded(instance, plan, framedPoint(shelter, frame, 1, 1), 0, Block.BARREL)
        for (forward in -1..1) {
            setGrounded(instance, plan, framedPoint(shelter, frame, forward, -1), 0, palette.carpet)
        }
    }

    private fun decorateCombat(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint, ordinal: Int) {
        val frame = routeFrame(plan, center)
        val palette = scenePalette(plan.style)
        for (forward in -7..7) {
            for (side in -7..7) {
                if (forward * forward + side * side > 48) continue
                val point = framedPoint(center, frame, forward, side)
                if (Math.floorMod(forward * 13 + side * 5 + ordinal, 7) <= 1) {
                    paintSurface(instance, plan, point, if ((forward + side) and 1 == 0) palette.path else palette.pathAccent)
                }
            }
        }
        when (ordinal % 3) {
            0 -> {
                // The encounter sits inside one broken retaining wall with a readable entry/exit.
                for (side in -7..7) {
                    if (side in -1..1 || Math.floorMod(side + ordinal, 5) == 0) continue
                    val wall = framedPoint(center, frame, 4, side)
                    setGrounded(instance, plan, wall, 0, if (side and 1 == 0) palette.stone else palette.stoneCracked)
                    if (kotlin.math.abs(side) >= 4) setGrounded(instance, plan, wall, 1, palette.stone)
                }
                QuestMapStructureAssets.placeBoulder(instance, plan, framedPoint(center, frame, 5, -6), ordinal * 31 + 1, 1)
                QuestMapStructureAssets.placeBoulder(instance, plan, framedPoint(center, frame, 5, 6), ordinal * 31 + 2, 3)
            }
            1 -> {
                // A timber ambush camp: barricades frame combat but never block the route.
                listOf(-1 to -6, 1 to -6, -1 to 6, 1 to 6).forEach { (forward, side) ->
                    val log = framedPoint(center, frame, forward, side)
                    setGrounded(instance, plan, log, 0, palette.timber)
                    setGrounded(instance, plan, framedPoint(log, frame, 1, 0), 0, palette.timber)
                }
                setGrounded(instance, plan, framedPoint(center, frame, 2, 4), 0, Block.BARREL)
                setGrounded(instance, plan, framedPoint(center, frame, 1, 3), 0, Block.CAMPFIRE)
                for (side in -2..2) setGrounded(instance, plan, framedPoint(center, frame, -5, side), 0, palette.roof)
            }
            else -> {
                // Natural choke: rock masses sit on the flanks and the clear middle remains playable.
                listOf(-2 to -7, 3 to -6, 4 to 6, -3 to 7).forEachIndexed { index, (forward, side) ->
                    QuestMapStructureAssets.placeBoulder(
                        instance,
                        plan,
                        framedPoint(center, frame, forward, side),
                        ordinal * 31 + index,
                        index,
                    )
                }
                QuestMapStructureAssets.placeFallenLog(instance, plan, framedPoint(center, frame, 5, -4), 5, 1, ordinal)
            }
        }
    }

    private fun decorateGathering(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint, ordinal: Int) {
        val frame = routeFrame(plan, center)
        val palette = scenePalette(plan.style)
        val ore = when (ordinal % 4) {
            0 -> Block.COPPER_ORE
            1 -> Block.IRON_ORE
            2 -> Block.COAL_ORE
            else -> Block.AMETHYST_CLUSTER
        }
        val face = framedPoint(center, frame, 1, 3)
        QuestMapStructureAssets.placeBoulder(instance, plan, face, ordinal * 97, ordinal)

        // Resource blocks are exposed inside a cut, never stacked loose on grass.
        for (forward in -3..3) {
            for (side in -3..3) {
                if (forward * forward + side * side > 12) continue
                val point = framedPoint(center, frame, forward, side)
                paintSurface(instance, plan, point, if ((forward + side) and 2 == 0) palette.path else palette.stonePolished)
            }
        }
        listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, -1 to 1).forEachIndexed { index, (forward, side) ->
            val point = framedPoint(face, frame, forward, side)
            val ground = plan.heightAt(point)
            instance.setBlock(point.x, ground + if (index == 0) 1 else 0, point.z, ore)
        }
        setGrounded(instance, plan, framedPoint(center, frame, -2, -3), 0, Block.BARREL)
        setGrounded(instance, plan, framedPoint(center, frame, -1, -3), 0, Block.CRAFTING_TABLE)
        for (forward in -2..2) setGrounded(instance, plan, framedPoint(center, frame, forward, -4), 0, palette.roof)
    }

    private fun decorateDiscovery(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint, ordinal: Int) {
        val frame = routeFrame(plan, center)
        val palette = scenePalette(plan.style)
        when (ordinal % 3) {
            0 -> {
                // A spring framed by a low ruin; the pool is the focal point.
                for (forward in -2..2) {
                    for (side in -2..2) {
                        val point = framedPoint(center, frame, forward, side)
                        if (forward * forward + side * side <= 3) {
                            instance.setBlock(point.x, plan.heightAt(point), point.z, if (plan.style == QuestTerrainStyle.INFERNAL) Block.LAVA else Block.WATER)
                        }
                    }
                }
                for (side in -4..4) {
                    if (side == 0 || Math.floorMod(side, 3) == 0) continue
                    setGrounded(instance, plan, framedPoint(center, frame, 3, side), 0, palette.stone)
                }
            }
            1 -> {
                // A collapsed wayside shrine has a broad base and a deliberate recessed focal niche.
                for (side in -4..4) {
                    val point = framedPoint(center, frame, 2, side)
                    setGrounded(instance, plan, point, 0, if (side and 1 == 0) palette.stone else palette.stoneCracked)
                    if (kotlin.math.abs(side) in 2..3) setGrounded(instance, plan, point, 1, palette.stonePolished)
                }
                setGrounded(instance, plan, framedPoint(center, frame, 2, 0), 1, palette.stonePolished)
                setGrounded(instance, plan, framedPoint(center, frame, 1, 0), 0, Block.CANDLE)
            }
            else -> {
                // A rooted stone seat: landscape and discovery object read as one silhouette.
                QuestMapStructureAssets.placeBoulder(instance, plan, framedPoint(center, frame, 2, 1), ordinal * 71, 2)
                for (side in -2..2) {
                    setGrounded(instance, plan, framedPoint(center, frame, 0, side), 0, palette.roof)
                }
                setGrounded(instance, plan, framedPoint(center, frame, 1, 0), 0, Block.AMETHYST_CLUSTER)
            }
        }
    }

    private fun decorateBossArena(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint) {
        val frame = routeFrame(plan, center)
        val palette = scenePalette(plan.style)
        for (forward in -13..13) {
            for (side in -13..13) {
                val distance = forward * forward + side * side
                if (distance > 13 * 13) continue
                val point = framedPoint(center, frame, forward, side)
                val block = when {
                    distance >= 11 * 11 -> if (Math.floorMod(forward + side, 4) == 0) palette.stoneCracked else palette.stone
                    Math.floorMod(forward * 5 + side * 7, 17) <= 1 -> palette.pathAccent
                    else -> palette.stonePolished
                }
                paintSurface(instance, plan, point, block)
            }
        }

        // Two low entry buttresses make the arrival legible without an unexplained floating arch.
        listOf(-6, 6).forEach { side ->
            val base = framedPoint(center, frame, -10, side)
            for (forwardOffset in -1..1) {
                for (sideOffset in -1..1) {
                    val point = framedPoint(base, frame, forwardOffset, sideOffset)
                    setGrounded(instance, plan, point, 0, palette.stone)
                }
            }
            repeat(3) { height -> setGrounded(instance, plan, base, height, if (height == 1) palette.stoneCracked else palette.stone) }
            setGrounded(instance, plan, framedPoint(base, frame, 0, if (side > 0) -1 else 1), 0, palette.light)
        }

        // A broad far-wall shrine creates the destination silhouette seen after entering the arena.
        for (side in -7..7) {
            val height = when {
                kotlin.math.abs(side) <= 1 -> 5
                kotlin.math.abs(side) <= 4 -> 3
                else -> 1
            }
            val point = framedPoint(center, frame, 11, side)
            repeat(height) { layer ->
                val block = when {
                    layer == height - 1 && side % 3 == 0 -> palette.stonePolished
                    (side + layer) and 3 == 0 -> palette.stoneCracked
                    else -> palette.stone
                }
                setGrounded(instance, plan, point, layer, block)
            }
        }
        setGrounded(instance, plan, framedPoint(center, frame, 10, 0), 1, Block.LODESTONE)
        setGrounded(instance, plan, framedPoint(center, frame, 10, 0), 2, palette.light)
        setGrounded(instance, plan, center, 0, Block.LODESTONE)
        QuestMapStructureAssets.placeBoulder(instance, plan, framedPoint(center, frame, 10, -9), plan.seed.toInt(), 1)
        QuestMapStructureAssets.placeBoulder(instance, plan, framedPoint(center, frame, 10, 9), (plan.seed ushr 32).toInt(), 3)
    }

}

internal class VerdantRoadQuestRuntime private constructor(
    val plan: QuestMapPlan,
    val instance: InstanceContainer,
    val spawn: Pos,
    val preparationMillis: Long,
    val loadedChunkCount: Int,
) {
    fun close() {
        check(instance.players.isEmpty()) { "Cannot close a quest map while players are inside" }
        MinecraftServer.getInstanceManager().unregisterInstance(instance)
    }

    companion object {
        fun prepare(seed: Long): CompletableFuture<VerdantRoadQuestRuntime> {
            val startedAt = System.nanoTime()
            val plan = VerdantRoadQuestPlanner.generate(seed)
            val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
            instance.setTime(6000)
            instance.defaultClock()?.pause()
            instance.setWeather(Weather.CLEAR)
            instance.setChunkSupplier(::LightingChunk)
            instance.setGenerator(VerdantRoadQuestGenerator(plan))
            val chunkRange = questMapRenderChunkRange(plan.size, ServerFlag.CHUNK_VIEW_DISTANCE)
            val chunkCoordinates = buildList {
                for (chunkX in chunkRange) {
                    for (chunkZ in chunkRange) add(chunkX to chunkZ)
                }
            }
            val chunks = chunkCoordinates.map { (chunkX, chunkZ) -> instance.loadChunk(chunkX, chunkZ) }
            return CompletableFuture.allOf(*chunks.toTypedArray()).thenApply {
                val missing = chunkCoordinates.filter { (chunkX, chunkZ) -> instance.getChunk(chunkX, chunkZ) == null }
                check(missing.isEmpty()) { "Quest map render coverage incomplete: ${missing.take(8)} (${missing.size} missing)" }
                VerdantRoadQuestDecorator.decorate(instance, plan)
                val spawn = Pos(plan.start.x + 0.5, plan.heightAt(plan.start) + 1.0, plan.start.z + 0.5)
                VerdantRoadQuestRuntime(
                    plan,
                    instance,
                    spawn,
                    (System.nanoTime() - startedAt) / 1_000_000,
                    chunkCoordinates.size,
                )
            }.whenComplete { _, failure ->
                if (failure != null) MinecraftServer.getInstanceManager().unregisterInstance(instance)
            }
        }
    }
}

internal fun questMapRenderChunkRange(mapSize: Int, viewDistance: Int): IntRange {
    require(mapSize > 0)
    val lastMapChunk = Math.floorDiv(mapSize - 1, 16)
    val renderBuffer = viewDistance.coerceAtLeast(2)
    return -renderBuffer..lastMapChunk + renderBuffer
}

internal data class VerdantRoadQuestServiceStatus(
    val readyMaps: Int,
    val preparingMaps: Int,
    val activeMaps: Int,
)

/** Concrete prewarmed service for the first generated ProjectS quest, not a generic quest framework. */
internal class VerdantRoadQuestService(
    private val hubInstance: Instance,
    private val hubSpawn: Pos,
    seedBase: Long = System.currentTimeMillis(),
) {
    private val ready = ConcurrentLinkedQueue<VerdantRoadQuestRuntime>()
    private val activeByPlayer = ConcurrentHashMap<UUID, VerdantRoadQuestRuntime>()
    private val preparing = AtomicInteger()
    private val nextSeed = AtomicLong(seedBase)

    fun prewarmInitial(): CompletableFuture<Void> = CompletableFuture.allOf(
        *Array(PREWARM_TARGET) { prepareOne() },
    )

    fun enter(player: Player): CompletableFuture<Boolean> {
        if (activeByPlayer.containsKey(player.uuid)) {
            player.sendMessage(Component.text("You are already inside a generated quest map.", NamedTextColor.YELLOW))
            return CompletableFuture.completedFuture(false)
        }
        val runtime = ready.poll()
        if (runtime == null) {
            replenish()
            player.sendMessage(Component.text("Quest map pool is warming; try again shortly.", NamedTextColor.RED))
            return CompletableFuture.completedFuture(false)
        }
        return enterRuntime(player, runtime, returnToReadyOnFailure = true).whenComplete { _, _ -> replenish() }
    }

    fun enterSeed(player: Player, seed: Long): CompletableFuture<Boolean> {
        if (activeByPlayer.containsKey(player.uuid)) {
            player.sendMessage(Component.text("You are already inside a generated quest map.", NamedTextColor.YELLOW))
            return CompletableFuture.completedFuture(false)
        }
        player.sendMessage(Component.text("Preparing manual-smoke seed $seed...", NamedTextColor.GRAY))
        return VerdantRoadQuestRuntime.prepare(seed).thenCompose { runtime ->
            enterRuntime(player, runtime, returnToReadyOnFailure = false)
        }
    }

    private fun enterRuntime(
        player: Player,
        runtime: VerdantRoadQuestRuntime,
        returnToReadyOnFailure: Boolean,
    ): CompletableFuture<Boolean> {
        activeByPlayer[player.uuid] = runtime
        player.setVelocity(Vec.ZERO)
        val transferStartedAt = System.nanoTime()
        return player.setInstance(runtime.instance, runtime.spawn).handle { _, failure ->
            if (failure != null) {
                activeByPlayer.remove(player.uuid, runtime)
                if (returnToReadyOnFailure) {
                    ready += runtime
                } else if (runtime.instance.players.isEmpty()) {
                    runtime.close()
                }
                player.sendMessage(Component.text("Quest transfer failed: ${failure.message}", NamedTextColor.RED))
                false
            } else {
                val transferMillis = (System.nanoTime() - transferStartedAt) / 1_000_000
                player.sendMessage(
                    Component.text(
                        "Verdant Road seed=${runtime.plan.seed} style=${runtime.plan.style} layout=${runtime.plan.routeLayout} " +
                            "terrain=${runtime.plan.terrainProfile} chunks=${runtime.loadedChunkCount} " +
                            "ready=${runtime.preparationMillis}ms transfer=${transferMillis}ms",
                        NamedTextColor.GREEN,
                    ),
                )
                player.sendMessage(Component.text("Follow the road to the Lodestone boss arena. Side trails contain gathering and discoveries.", NamedTextColor.GRAY))
                true
            }
        }
    }

    fun returnToHub(player: Player): CompletableFuture<Boolean> {
        val runtime = activeByPlayer.remove(player.uuid) ?: return CompletableFuture.completedFuture(false)
        player.setVelocity(Vec.ZERO)
        return player.setInstance(hubInstance, hubSpawn).handle { _, failure ->
            if (failure == null) {
                runtime.close()
                player.sendMessage(Component.text("Returned to the ProjectS hub.", NamedTextColor.GREEN))
                true
            } else {
                activeByPlayer[player.uuid] = runtime
                player.sendMessage(Component.text("Hub transfer failed: ${failure.message}", NamedTextColor.RED))
                false
            }
        }
    }

    fun disconnect(playerId: UUID) {
        activeByPlayer.remove(playerId)?.let { closeAfterDisconnect(it, attemptsRemaining = 20) }
    }

    fun status(): VerdantRoadQuestServiceStatus = VerdantRoadQuestServiceStatus(
        readyMaps = ready.size,
        preparingMaps = preparing.get(),
        activeMaps = activeByPlayer.size,
    )

    private fun replenish() {
        while (ready.size + preparing.get() < PREWARM_TARGET) {
            prepareOne().exceptionally { failure ->
                System.err.println("Quest map background prewarm failed: ${failure.message}")
                null
            }
        }
    }

    private fun prepareOne(): CompletableFuture<Void> {
        preparing.incrementAndGet()
        return VerdantRoadQuestRuntime.prepare(nextSeed.getAndIncrement()).whenComplete { _, _ ->
            preparing.decrementAndGet()
        }.thenAccept { runtime ->
            ready += runtime
            println(
                "QUEST_MAP_READY seed=${runtime.plan.seed} style=${runtime.plan.style} " +
                    "layout=${runtime.plan.routeLayout} terrain=${runtime.plan.terrainProfile} " +
                    "size=${runtime.plan.size} chunks=${runtime.loadedChunkCount} preparation=${runtime.preparationMillis}ms",
            )
        }
    }

    private fun closeAfterDisconnect(runtime: VerdantRoadQuestRuntime, attemptsRemaining: Int) {
        MinecraftServer.getSchedulerManager().scheduleNextTick {
            if (runtime.instance.players.isEmpty()) {
                runtime.close()
            } else if (attemptsRemaining > 1) {
                closeAfterDisconnect(runtime, attemptsRemaining - 1)
            } else {
                System.err.println("Quest map ${runtime.plan.seed} still owns players after disconnect cleanup window")
            }
        }
    }

    private companion object {
        const val PREWARM_TARGET = 2
    }
}
