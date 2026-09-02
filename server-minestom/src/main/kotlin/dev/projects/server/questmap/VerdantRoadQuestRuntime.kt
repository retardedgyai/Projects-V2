package dev.projects.server.questmap

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.ServerFlag
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityPose
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.Weather
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.instance.generator.Generator
import net.minestom.server.item.ItemStack
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.network.packet.server.play.EntityAnimationPacket
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import net.minestom.server.world.biome.Biome
import java.nio.file.Path
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
        decorateLandscapeScenes(instance, plan)
        decorateTrees(instance, plan, plan.landscapeScenes)
        decorateTerrainDetail(instance, plan, plan.landscapeScenes)
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
        enforceRouteClearance(instance, plan)
        val remainingObstructions = routeClearanceObstructions(instance, plan)
        check(remainingObstructions.isEmpty()) {
            "Decorated quest route is obstructed: ${remainingObstructions.take(8)}"
        }
    }

    /**
     * Decoration is intentionally allowed to compose across scene boundaries, but walking space is
     * authoritative. This final pass runs after every structure, prop, and encounter scene so a new
     * asset call site cannot silently reintroduce the blocked-road regression.
     */
    internal fun enforceRouteClearance(instance: Instance, plan: QuestMapPlan) {
        for (z in 0 until plan.size) {
            for (x in 0 until plan.size) {
                if (!isProtectedRouteCell(plan, x, z)) continue
                val ground = plan.heightAt(x, z)
                for (y in ground + 1..ground + ROUTE_VISUAL_CLEARANCE_HEIGHT) {
                    if (instance.getBlock(x, y, z).blocksMotion()) {
                        instance.setBlock(x, y, z, Block.AIR)
                    }
                }
            }
        }
    }

    internal fun routeClearanceObstructions(
        instance: Instance,
        plan: QuestMapPlan,
        limit: Int = 64,
    ): List<String> {
        val obstructions = mutableListOf<String>()
        for (z in 0 until plan.size) {
            for (x in 0 until plan.size) {
                if (!isProtectedRouteCell(plan, x, z)) continue
                val ground = plan.heightAt(x, z)
                for (y in ground + 1..ground + ROUTE_VISUAL_CLEARANCE_HEIGHT) {
                    val block = instance.getBlock(x, y, z)
                    if (block.blocksMotion()) {
                        obstructions += "$x,$y,$z=${block.name()}"
                        if (obstructions.size >= limit) return obstructions
                    }
                }
            }
        }
        return obstructions
    }

    private fun isProtectedRouteCell(plan: QuestMapPlan, x: Int, z: Int): Boolean =
        plan.mainRoadDistanceSquaredAt(x, z) <= MAIN_ROAD_CLEARANCE_RADIUS * MAIN_ROAD_CLEARANCE_RADIUS ||
            plan.roadDistanceSquaredAt(x, z) <= SIDE_TRAIL_CLEARANCE_RADIUS * SIDE_TRAIL_CLEARANCE_RADIUS

    private const val MAIN_ROAD_CLEARANCE_RADIUS = 5
    private const val SIDE_TRAIL_CLEARANCE_RADIUS = 2
    private const val ROUTE_VISUAL_CLEARANCE_HEIGHT = 8

    private data class LandscapeSurface(
        val core: Block,
        val secondary: Block,
        val edge: Block,
    )

    /**
     * Paint broad, readable compositions before the ambient scatter pass. Each scene owns its
     * ground field, approach, negative space and landmark group, so props read as one place rather
     * than independent random samples.
     */
    private fun decorateLandscapeScenes(instance: Instance, plan: QuestMapPlan) {
        plan.landscapeScenes.forEach { scene ->
            paintLandscapeField(instance, plan, scene)
            paintLandscapeApproach(instance, plan, scene)
            decorateLandscapeLandmarks(instance, plan, scene)
        }
    }

    private fun paintLandscapeField(instance: Instance, plan: QuestMapPlan, scene: QuestLandscapeScene) {
        val surface = landscapeSurface(plan.style, scene.role)
        for (dz in -scene.radius..scene.radius) {
            for (dx in -scene.radius..scene.radius) {
                val point = QuestMapPoint(scene.center.x + dx, scene.center.z + dz)
                if (point.x !in 2 until plan.size - 2 || point.z !in 2 until plan.size - 2) continue
                if (plan.roadDistanceSquaredAt(point.x, point.z) <= 6 * 6) continue
                val cellX = Math.floorDiv(point.x, 5)
                val cellZ = Math.floorDiv(point.z, 5)
                val cellHash = Math.floorMod(
                    scene.id * 73_856_093 + cellX * 19_349_663 + cellZ * 83_492_791 + plan.seed.toInt(),
                    100,
                )
                val warp = (cellHash - 50) / 160.0
                val normalized = kotlin.math.sqrt((dx * dx + dz * dz).toDouble()) / scene.radius
                if (normalized > 0.92 + warp * 0.16) continue
                val block = when {
                    normalized > 0.70 + warp * 0.08 -> surface.edge
                    cellHash < 31 -> surface.secondary
                    else -> surface.core
                }
                paintSurface(instance, plan, point, block)
            }
        }
    }

    private fun paintLandscapeApproach(instance: Instance, plan: QuestMapPlan, scene: QuestLandscapeScene) {
        val palette = scenePalette(plan.style)
        scene.accessPath.forEachIndexed { index, center ->
            for (dz in -1..1) {
                for (dx in -1..1) {
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dz) > 1) continue
                    val point = QuestMapPoint(center.x + dx, center.z + dz)
                    if (point.x !in 2 until plan.size - 2 || point.z !in 2 until plan.size - 2) continue
                    val block = if (Math.floorMod(index + dx * 3 + dz * 5 + scene.id, 7) == 0) {
                        palette.pathAccent
                    } else {
                        palette.path
                    }
                    paintSurface(instance, plan, point, block)
                }
            }
        }
    }

    private fun decorateLandscapeLandmarks(instance: Instance, plan: QuestMapPlan, scene: QuestLandscapeScene) {
        fun point(forward: Int, side: Int): QuestMapPoint = landscapePoint(scene, forward, side)
        fun tree(forward: Int, side: Int, salt: Int) {
            val origin = point(forward, side)
            val variation = scene.id * 1_009 + salt
            val footprint = QuestMapStructureAssets.treeFootprint(plan.style, variation)
            if (clearForStructure(plan, origin, footprint) && terrainRange(plan, origin, 5) <= 3) {
                QuestMapStructureAssets.placeTree(instance, plan, origin, variation, scene.rotation + salt)
            }
        }
        fun boulder(forward: Int, side: Int, salt: Int) {
            val origin = point(forward, side)
            val footprint = QuestMapStructureAssets.boulderFootprint(plan.style, scene.id * 97 + salt)
            if (clearForStructure(plan, origin, footprint) && terrainRange(plan, origin, 4) <= 4) {
                QuestMapStructureAssets.placeBoulder(instance, plan, origin, scene.id * 97 + salt, scene.rotation + salt)
            }
        }
        fun shrub(forward: Int, side: Int, salt: Int) {
            val origin = point(forward, side)
            if (clearForStructure(plan, origin, 4) && terrainRange(plan, origin, 3) <= 3) {
                QuestMapStructureAssets.placeShrubCluster(instance, plan, origin, scene.id * 131 + salt, scene.rotation + salt)
            }
        }
        fun outcrop(forward: Int, side: Int, salt: Int) {
            val origin = point(forward, side)
            if (clearForStructure(plan, origin, 11) && terrainRange(plan, origin, 7) <= 7) {
                QuestMapStructureAssets.placeRockOutcrop(instance, plan, origin, scene.id * 173 + salt, scene.rotation + salt)
            }
        }

        when (scene.role) {
            QuestLandscapeRole.SHELTERED_GROVE -> {
                tree(-11, -14, 1)
                tree(12, -13, 2)
                tree(15, 12, 3)
                tree(-13, 14, 4)
                val log = point(7, 5)
                if (clearForStructure(plan, log, 7) && lineTerrainRange(plan, log, 7, scene.rotation + 1) <= 2) {
                    QuestMapStructureAssets.placeFallenLog(instance, plan, log, 7, scene.rotation + 1, scene.id * 211)
                }
                shrub(-4, 9, 5)
                shrub(4, -8, 6)
            }
            QuestLandscapeRole.RIDGE_GATE -> {
                outcrop(2, -11, 1)
                outcrop(5, 12, 2)
                tree(-12, 16, 3)
                boulder(15, -16, 4)
            }
            QuestLandscapeRole.HEATH_VISTA -> {
                tree(8, 16, 1)
                boulder(-8, -12, 2)
                shrub(3, -15, 3)
                shrub(15, 5, 4)
            }
            QuestLandscapeRole.HEADWATER -> {
                tree(-10, -15, 1)
                tree(13, 14, 2)
                boulder(8, -10, 3)
                shrub(-5, 8, 4)
                shrub(8, 7, 5)
            }
            QuestLandscapeRole.RUINED_TERRACE -> {
                placeRuinedTerrace(instance, plan, scene)
                tree(-12, -15, 1)
                boulder(13, 13, 2)
                shrub(8, -10, 3)
            }
            QuestLandscapeRole.ORE_CUT -> {
                outcrop(3, 4, 1)
                boulder(-12, -13, 2)
                boulder(15, 13, 3)
                tree(-14, 15, 4)
            }
            QuestLandscapeRole.RIFT_GARDEN -> {
                outcrop(6, -8, 1)
                tree(-13, 14, 2)
                shrub(-5, -12, 3)
                shrub(13, 10, 4)
            }
        }
    }

    private fun placeRuinedTerrace(instance: Instance, plan: QuestMapPlan, scene: QuestLandscapeScene) {
        val palette = scenePalette(plan.style)
        for (forward in -14..14) {
            if (forward in -2..2 || Math.floorMod(forward + scene.id, 11) == 0) continue
            for (depth in 0..1) {
                val point = landscapePoint(scene, forward, 8 + depth)
                if (point.x !in 2 until plan.size - 2 || point.z !in 2 until plan.size - 2) continue
                if (plan.roadDistanceSquaredAt(point.x, point.z) <= 7 * 7) continue
                val block = if (Math.floorMod(forward * 17 + depth * 5 + scene.id, 5) == 0) {
                    palette.stoneCracked
                } else {
                    palette.stone
                }
                setGrounded(instance, plan, point, 0, block)
                if (depth == 0 && Math.floorMod(forward + scene.id, 4) != 0) {
                    setGrounded(instance, plan, point, 1, block)
                }
            }
        }
    }

    private fun landscapePoint(scene: QuestLandscapeScene, forward: Int, side: Int): QuestMapPoint {
        val mirroredSide = if (scene.mirrored) -side else side
        val (dx, dz) = when (Math.floorMod(scene.rotation, 4)) {
            0 -> forward to mirroredSide
            1 -> -mirroredSide to forward
            2 -> -forward to -mirroredSide
            else -> mirroredSide to -forward
        }
        return QuestMapPoint(scene.center.x + dx, scene.center.z + dz)
    }

    private fun landscapeSurface(style: QuestTerrainStyle, role: QuestLandscapeRole): LandscapeSurface {
        if (style == QuestTerrainStyle.INFERNAL) {
            return when (role) {
                QuestLandscapeRole.ORE_CUT, QuestLandscapeRole.RIDGE_GATE -> LandscapeSurface(Block.BLACKSTONE, Block.BASALT, Block.NETHERRACK)
                QuestLandscapeRole.RIFT_GARDEN -> LandscapeSurface(Block.CRIMSON_NYLIUM, Block.WARPED_NYLIUM, Block.SOUL_SOIL)
                QuestLandscapeRole.RUINED_TERRACE -> LandscapeSurface(Block.SOUL_SOIL, Block.POLISHED_BLACKSTONE, Block.NETHERRACK)
                else -> LandscapeSurface(Block.NETHERRACK, Block.SOUL_SOIL, Block.BLACKSTONE)
            }
        }
        return when (role) {
            QuestLandscapeRole.RIDGE_GATE, QuestLandscapeRole.ORE_CUT -> when (style) {
                QuestTerrainStyle.CLIFFLANDS -> LandscapeSurface(Block.ANDESITE, Block.CALCITE, Block.GRAVEL)
                QuestTerrainStyle.HIGHLANDS -> LandscapeSurface(Block.TUFF, Block.ANDESITE, Block.COARSE_DIRT)
                else -> LandscapeSurface(Block.ANDESITE, Block.STONE, Block.COARSE_DIRT)
            }
            QuestLandscapeRole.SHELTERED_GROVE -> when (style) {
                QuestTerrainStyle.SAKURA_GROVE -> LandscapeSurface(Block.MOSS_BLOCK, Block.ROOTED_DIRT, Block.PODZOL)
                QuestTerrainStyle.SALTMARSH -> LandscapeSurface(Block.MOSS_BLOCK, Block.MUD, Block.PACKED_MUD)
                else -> LandscapeSurface(Block.PODZOL, Block.MOSS_BLOCK, Block.COARSE_DIRT)
            }
            QuestLandscapeRole.HEADWATER -> LandscapeSurface(Block.MOSS_BLOCK, Block.MUD, Block.CLAY)
            QuestLandscapeRole.RUINED_TERRACE -> LandscapeSurface(Block.GRASS_BLOCK, Block.MOSSY_COBBLESTONE, Block.COARSE_DIRT)
            QuestLandscapeRole.HEATH_VISTA -> when (style) {
                QuestTerrainStyle.SAKURA_GROVE -> LandscapeSurface(Block.GRASS_BLOCK, Block.MOSS_BLOCK, Block.ROOTED_DIRT)
                QuestTerrainStyle.SALTMARSH -> LandscapeSurface(Block.PACKED_MUD, Block.MUD, Block.MOSS_BLOCK)
                else -> LandscapeSurface(Block.GRASS_BLOCK, Block.COARSE_DIRT, Block.PODZOL)
            }
            QuestLandscapeRole.RIFT_GARDEN -> LandscapeSurface(Block.PODZOL, Block.MOSS_BLOCK, Block.COARSE_DIRT)
        }
    }

    private fun rasterLine(from: QuestMapPoint, to: QuestMapPoint): List<QuestMapPoint> {
        val points = mutableListOf<QuestMapPoint>()
        var x = from.x
        var z = from.z
        val dx = kotlin.math.abs(to.x - from.x)
        val dz = kotlin.math.abs(to.z - from.z)
        val sx = if (from.x < to.x) 1 else -1
        val sz = if (from.z < to.z) 1 else -1
        var error = dx - dz
        while (true) {
            points += QuestMapPoint(x, z)
            if (x == to.x && z == to.z) break
            val doubled = error * 2
            if (doubled > -dz) {
                error -= dz
                x += sx
            }
            if (doubled < dx) {
                error += dx
                z += sz
            }
        }
        return points
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

    private fun decorateTrees(instance: Instance, plan: QuestMapPlan, scenes: List<QuestLandscapeScene>) {
        val random = Random(plan.seed xor 0x47524F5645L)
        val occupied = mutableListOf<Pair<QuestMapPoint, Int>>()
        val ambientGroveCount = when (plan.style) {
            QuestTerrainStyle.VERDANT -> 22
            QuestTerrainStyle.HIGHLANDS -> 17
            QuestTerrainStyle.SALTMARSH -> 16
            QuestTerrainStyle.CLIFFLANDS -> 15
            QuestTerrainStyle.SAKURA_GROVE -> 25
            QuestTerrainStyle.INFERNAL -> 18
        }
        val groveCenters = buildList {
            addAll(scenes.filter { it.role == QuestLandscapeRole.SHELTERED_GROVE }.map { it.center })
            addAll(List(
                ambientGroveCount,
            ) {
                QuestMapPoint(
                    18 + random.nextInt(plan.size - 36),
                    18 + random.nextInt(plan.size - 36),
                )
            })
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
            val containingScene = scenes.firstOrNull { it.center.distanceSquared(point) < it.radius * it.radius }
            if (containingScene != null &&
                (containingScene.role != QuestLandscapeRole.SHELTERED_GROVE || containingScene.center.distanceSquared(point) < 9 * 9)
            ) return@repeat
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

    private fun decorateTerrainDetail(instance: Instance, plan: QuestMapPlan, scenes: List<QuestLandscapeScene>) {
        val random = Random(plan.seed xor 0x5445525241494EL)
        val occupiedScenes = mutableListOf<QuestMapPoint>()
        repeat(10_500) {
            val point = QuestMapPoint(8 + random.nextInt(plan.size - 16), 8 + random.nextInt(plan.size - 16))
            if (plan.roadDistanceSquaredAt(point.x, point.z) <= 4 * 4) return@repeat
            if (plan.contents.any { it.position.distanceSquared(point) < 6 * 6 }) return@repeat
            if (scenes.any { it.center.distanceSquared(point) < it.radius * it.radius }) return@repeat
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
                // Two separated retaining-wall remnants frame the encounter without crossing the road.
                ((-12..-8) + (8..12)).forEach { side ->
                    if (Math.floorMod(side + ordinal, 5) == 0) return@forEach
                    val wall = framedPoint(center, frame, 3, side)
                    setGrounded(instance, plan, wall, 0, if (side and 1 == 0) palette.stone else palette.stoneCracked)
                    if (kotlin.math.abs(side) >= 10) setGrounded(instance, plan, wall, 1, palette.stone)
                }
                QuestMapStructureAssets.placeBoulder(instance, plan, framedPoint(center, frame, 4, -13), ordinal * 31 + 1, 1)
                QuestMapStructureAssets.placeBoulder(instance, plan, framedPoint(center, frame, 4, 13), ordinal * 31 + 2, 3)
            }
            1 -> {
                // A timber ambush camp: barricades frame combat but never block the route.
                listOf(-1 to -10, 1 to -10, -1 to 10, 1 to 10).forEach { (forward, side) ->
                    val log = framedPoint(center, frame, forward, side)
                    setGrounded(instance, plan, log, 0, palette.timber)
                    setGrounded(instance, plan, framedPoint(log, frame, 1, 0), 0, palette.timber)
                }
                setGrounded(instance, plan, framedPoint(center, frame, 2, 9), 0, Block.BARREL)
                setGrounded(instance, plan, framedPoint(center, frame, 1, 8), 0, Block.CAMPFIRE)
                for (forward in -2..2) setGrounded(instance, plan, framedPoint(center, frame, forward, -10), 0, palette.roof)
            }
            else -> {
                // Natural choke: rock masses sit on the flanks and the clear middle remains playable.
                listOf(-2 to -13, 3 to -12, 4 to 12, -3 to 13).forEachIndexed { index, (forward, side) ->
                    QuestMapStructureAssets.placeBoulder(
                        instance,
                        plan,
                        framedPoint(center, frame, forward, side),
                        ordinal * 31 + index,
                        index,
                    )
                }
                QuestMapStructureAssets.placeFallenLog(instance, plan, framedPoint(center, frame, 5, -11), 5, 1, ordinal)
            }
        }
    }

    private fun decorateGathering(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint, ordinal: Int) {
        val frame = routeFrame(plan, center)
        val palette = scenePalette(plan.style)
        val node = questGatheringNodes(plan).single { it.contentPosition == center }

        // A worked clearing and a short final approach make the authored resource object readable.
        for (forward in -3..3) {
            for (side in -3..3) {
                if (forward * forward + side * side > 12) continue
                val point = framedPoint(center, frame, forward, side)
                paintSurface(instance, plan, point, if ((forward + side) and 2 == 0) palette.path else palette.stonePolished)
            }
        }
        val objectPoint = QuestMapPoint(node.blockPosition.blockX(), node.blockPosition.blockZ())
        for (step in 0..8) {
            val progress = step / 8.0
            val approach = QuestMapPoint(
                (center.x + (objectPoint.x - center.x) * progress).roundToInt(),
                (center.z + (objectPoint.z - center.z) * progress).roundToInt(),
            )
            paintSurface(instance, plan, approach, palette.path)
            if (step in 1..6) {
                val sidePoint = QuestMapPoint(approach.x + frame.sideX, approach.z + frame.sideZ)
                paintSurface(instance, plan, sidePoint, palette.pathAccent)
            }
        }
        setGrounded(instance, plan, framedPoint(center, frame, -2, -3), 0, Block.BARREL)
        setGrounded(instance, plan, framedPoint(center, frame, -1, -3), 0, Block.CRAFTING_TABLE)
        for (forward in -2..2) setGrounded(instance, plan, framedPoint(center, frame, forward, -4), 0, palette.roof)
        QuestMapStructureAssets.placeGatheringObject(instance, plan, node)
    }

    private fun decorateDiscovery(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint, ordinal: Int) {
        val frame = routeFrame(plan, center)
        val palette = scenePalette(plan.style)
        val site = framedPoint(center, frame, 0, if (ordinal and 1 == 0) 9 else -9)
        when (ordinal % 3) {
            0 -> {
                // A spring framed by a low ruin; the pool is the focal point.
                for (forward in -2..2) {
                    for (side in -2..2) {
                        val point = framedPoint(site, frame, forward, side)
                        if (forward * forward + side * side <= 3) {
                            instance.setBlock(point.x, plan.heightAt(point), point.z, if (plan.style == QuestTerrainStyle.INFERNAL) Block.LAVA else Block.WATER)
                        }
                    }
                }
                for (side in -4..4) {
                    if (side == 0 || Math.floorMod(side, 3) == 0) continue
                    setGrounded(instance, plan, framedPoint(site, frame, 3, side), 0, palette.stone)
                }
            }
            1 -> {
                // A collapsed wayside shrine has a broad base and a deliberate recessed focal niche.
                for (side in -4..4) {
                    val point = framedPoint(site, frame, 2, side)
                    setGrounded(instance, plan, point, 0, if (side and 1 == 0) palette.stone else palette.stoneCracked)
                    if (kotlin.math.abs(side) in 2..3) setGrounded(instance, plan, point, 1, palette.stonePolished)
                }
                setGrounded(instance, plan, framedPoint(site, frame, 2, 0), 1, palette.stonePolished)
                setGrounded(instance, plan, framedPoint(site, frame, 1, 0), 0, Block.CANDLE)
            }
            else -> {
                // A rooted stone seat: landscape and discovery object read as one silhouette.
                QuestMapStructureAssets.placeBoulder(instance, plan, framedPoint(site, frame, 2, 1), ordinal * 71, 2)
                for (side in -2..2) {
                    setGrounded(instance, plan, framedPoint(site, frame, 0, side), 0, palette.roof)
                }
                setGrounded(instance, plan, framedPoint(site, frame, 1, 0), 0, Block.AMETHYST_CLUSTER)
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
    val requestedSeed: Long,
    val plan: QuestMapPlan,
    val candidateScore: QuestMapCandidateScore,
    val candidateCount: Int,
    val instance: InstanceContainer,
    val spawn: Pos,
    val preparationMillis: Long,
    val loadedChunkCount: Int,
) {
    val gatheringNodes: List<QuestGatheringNode> = questGatheringNodes(plan)
    val gatheringObjects: Map<Int, QuestMapStructureAssets.GatheringObject> = gatheringNodes.associate { node ->
        val resolved = QuestMapStructureAssets.resolveGatheringObject(plan, node)
        val survivingBlocks = resolved.blocks.filter { (position, block) -> instance.getBlock(position) == block }
        node.id to resolved.copy(blocks = survivingBlocks)
    }
    private val gatheringNodesByPosition = buildMap {
        gatheringNodes.forEach { node ->
            val gathering = gatheringObjects.getValue(node.id)
            gathering.interactionBlocks.forEach { position ->
                if (instance.getBlock(position) != Block.AIR && position !in this) put(position, node)
            }
            if (node.discipline == QuestGatheringDiscipline.WOODCUTTING) {
                gathering.interactionBlocks.forEach { position ->
                    for (dy in -1..1) {
                        for (dz in -1..1) {
                            for (dx in -1..1) {
                                val nearby = BlockVec(
                                    position.blockX() + dx,
                                    position.blockY() + dy,
                                    position.blockZ() + dz,
                                )
                                if (nearby !in this && isWoodGatheringBlock(instance.getBlock(nearby))) put(nearby, node)
                            }
                        }
                    }
                }
            }
        }
    }
    private val gatheringInteractionByNode = mutableMapOf<Int, Entity>()
    private val gatheringNodeByEntityId = mutableMapOf<Int, QuestGatheringNode>()
    private val gatheringLabelByNode = mutableMapOf<Int, Entity>()
    private val gatheringRespawnAtMillis = mutableMapOf<Int, Long>()

    init {
        gatheringNodes.forEach(::spawnGatheringLabel)
        gatheringNodes.filter {
            gatheringObjects.getValue(it.id).visualKind == QuestMapStructureAssets.GatheringVisualKind.ANIMAL_CORPSE
        }.forEach(::spawnGatheringInteraction)
    }

    fun gatheringNodeAt(position: BlockVec): QuestGatheringNode? = gatheringNodesByPosition[position]

    fun gatheringNodeForEntity(entity: Entity): QuestGatheringNode? = gatheringNodeByEntityId[entity.entityId]

    fun gatheringInteractionFor(node: QuestGatheringNode): Entity? = gatheringInteractionByNode[node.id]

    fun gatheringLabelFor(node: QuestGatheringNode): Entity? = gatheringLabelByNode[node.id]

    fun gatheringBlockAt(position: BlockVec): Block? =
        gatheringNodeAt(position)?.let { node -> gatheringObjects.getValue(node.id).blocks[position] }

    @Synchronized
    fun isGatheringNodeAvailable(node: QuestGatheringNode, nowMillis: Long): Boolean =
        (gatheringRespawnAtMillis[node.id] ?: Long.MIN_VALUE) <= nowMillis

    @Synchronized
    fun tryDepleteGatheringNode(node: QuestGatheringNode, nowMillis: Long): Boolean {
        if (!isGatheringNodeAvailable(node, nowMillis)) return false
        gatheringRespawnAtMillis[node.id] = nowMillis + GATHERING_RESPAWN_MILLIS
        gatheringObjects.getValue(node.id).blocks.keys.forEach { position -> instance.setBlock(position, Block.AIR) }
        removeGatheringInteraction(node.id)
        removeGatheringLabel(node.id)
        return true
    }

    @Synchronized
    fun respawnGatheringNodes(nowMillis: Long) {
        val ready = gatheringRespawnAtMillis.filterValues { it <= nowMillis }.keys.toList()
        ready.forEach { nodeId ->
            val node = gatheringNodes.single { it.id == nodeId }
            val gathering = gatheringObjects.getValue(node.id)
            gathering.blocks.forEach { (position, block) -> instance.setBlock(position, block) }
            gatheringRespawnAtMillis.remove(nodeId)
            if (gathering.visualKind == QuestMapStructureAssets.GatheringVisualKind.ANIMAL_CORPSE) {
                spawnGatheringInteraction(node)
            }
            spawnGatheringLabel(node)
        }
    }

    private fun spawnGatheringLabel(node: QuestGatheringNode) {
        removeGatheringLabel(node.id)
        val gathering = gatheringObjects.getValue(node.id)
        val qualityColor = when (node.quality) {
            QuestGatheringQuality.COMMON -> NamedTextColor.WHITE
            QuestGatheringQuality.BOUNTIFUL -> NamedTextColor.GREEN
            QuestGatheringQuality.RARE -> NamedTextColor.LIGHT_PURPLE
        }
        val label = Entity(EntityType.TEXT_DISPLAY).apply {
            setHasPhysics(false)
            setNoGravity(true)
            editEntityMeta(TextDisplayMeta::class.java) { meta ->
                meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
                meta.setText(
                    Component.text("T${node.tier} ", NamedTextColor.GRAY)
                        .append(Component.text(node.discipline.commonResourceName, NamedTextColor.GOLD))
                        .append(Component.text("  ${node.quality.displayName}", qualityColor)),
                )
                meta.setScale(Vec(0.82, 0.82, 0.82))
                meta.setViewRange(20f)
                meta.setShadow(true)
                meta.setSeeThrough(true)
                meta.setBackgroundColor(0xA0000000.toInt())
                meta.setLineWidth(220)
                meta.setBrightness(15, 15)
            }
        }
        gatheringLabelByNode[node.id] = label
        val lift = when (node.discipline) {
            QuestGatheringDiscipline.SKINNING -> 1.75
            QuestGatheringDiscipline.WOODCUTTING -> 3.1
            QuestGatheringDiscipline.QUARRYING, QuestGatheringDiscipline.MINING ->
                (gathering.interactionHeight + 0.4f).coerceIn(2.8f, 4.8f).toDouble()
            QuestGatheringDiscipline.HERBALISM ->
                (gathering.interactionHeight + 0.35f).coerceIn(3.0f, 5.2f).toDouble()
        }
        val labelPosition = Pos(
            gathering.interactionPosition.x(),
            gathering.interactionPosition.y() + lift,
            gathering.interactionPosition.z(),
        )
        label.setInstance(instance, labelPosition).whenComplete { _, failure ->
            if (failure != null || gatheringRespawnAtMillis.containsKey(node.id)) {
                gatheringLabelByNode.remove(node.id, label)
                label.remove()
            }
        }
    }

    private fun spawnGatheringInteraction(node: QuestGatheringNode) {
        removeGatheringInteraction(node.id)
        val gathering = gatheringObjects.getValue(node.id)
        check(gathering.visualKind == QuestMapStructureAssets.GatheringVisualKind.ANIMAL_CORPSE)
        val interaction = Entity(EntityType.COW).apply {
            setHasPhysics(false)
            setNoGravity(true)
            setPose(EntityPose.DYING)
        }
        gatheringInteractionByNode[node.id] = interaction
        gatheringNodeByEntityId[interaction.entityId] = node
        interaction.setInstance(instance, gathering.interactionPosition).whenComplete { _, failure ->
            if (failure != null || gatheringRespawnAtMillis.containsKey(node.id)) {
                gatheringNodeByEntityId.remove(interaction.entityId)
                gatheringInteractionByNode.remove(node.id, interaction)
                interaction.remove()
            }
        }
    }

    private fun removeGatheringInteraction(nodeId: Int) {
        gatheringInteractionByNode.remove(nodeId)?.let { interaction ->
            gatheringNodeByEntityId.remove(interaction.entityId)
            interaction.remove()
        }
    }

    private fun removeGatheringLabel(nodeId: Int) {
        gatheringLabelByNode.remove(nodeId)?.remove()
    }

    fun close() {
        check(instance.players.isEmpty()) { "Cannot close a quest map while players are inside" }
        gatheringInteractionByNode.keys.toList().forEach(::removeGatheringInteraction)
        gatheringLabelByNode.keys.toList().forEach(::removeGatheringLabel)
        MinecraftServer.getInstanceManager().unregisterInstance(instance)
    }

    companion object {
        private const val GATHERING_RESPAWN_MILLIS = 90_000L

        private fun isWoodGatheringBlock(block: Block): Boolean {
            val name = block.name().toString()
            return name.endsWith("_log") || name.endsWith("_wood") || name.endsWith("_stem") ||
                name.endsWith("_hyphae") || name.endsWith("_leaves") || name.endsWith("_wart_block") ||
                name.endsWith("mangrove_roots")
        }

        fun prepare(
            seed: Long,
            candidateCount: Int = QuestMapCandidateSelector.DEFAULT_CANDIDATE_COUNT,
        ): CompletableFuture<VerdantRoadQuestRuntime> {
            val startedAt = System.nanoTime()
            val selection = QuestMapCandidateSelector.select(seed, candidateCount)
            val plan = selection.plan
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
                    requestedSeed = selection.requestedSeed,
                    plan = plan,
                    candidateScore = selection.score,
                    candidateCount = selection.attemptedCandidates,
                    instance = instance,
                    spawn = spawn,
                    preparationMillis = (System.nanoTime() - startedAt) / 1_000_000,
                    loadedChunkCount = chunkCoordinates.size,
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
    private val masteryRepository: QuestGatheringMasteryRepository = QuestGatheringMasteryRepository(
        Path.of("config", "projects", "gathering-mastery"),
    ),
    seedBase: Long = System.currentTimeMillis(),
) {
    private val ready = ConcurrentLinkedQueue<VerdantRoadQuestRuntime>()
    private val activeByPlayer = ConcurrentHashMap<UUID, VerdantRoadQuestRuntime>()
    private val preparing = AtomicInteger()
    private val nextSeed = AtomicLong(seedBase)
    private val gatheringMasteries = ConcurrentHashMap<UUID, QuestGatheringMastery>()
    private val activeGathering = ConcurrentHashMap<UUID, ActiveGathering>()
    private val gatheringSmokeIndices = ConcurrentHashMap<UUID, Int>()

    private data class ActiveGathering(
        val runtime: VerdantRoadQuestRuntime,
        val node: QuestGatheringNode,
        val targetPosition: BlockVec,
        val targetEntity: Entity?,
        val requiresContinuousInput: Boolean,
        val requiredTicks: Int,
        val progressDisplay: Entity,
        var elapsedTicks: Int = 0,
        var lastInputTick: Long,
    )

    fun prewarmInitial(): CompletableFuture<Void> = CompletableFuture.allOf(
        *Array(PREWARM_TARGET) { prepareOne() },
    )

    fun enter(player: Player): CompletableFuture<Boolean> {
        if (activeByPlayer.containsKey(player.uuid)) {
            player.sendMessage(Component.text("すでに生成済みクエストマップ内にいます。", NamedTextColor.YELLOW))
            return CompletableFuture.completedFuture(false)
        }
        val runtime = ready.poll()
        if (runtime == null) {
            replenish()
            player.sendMessage(Component.text("クエストマップを準備中です。少し待ってからもう一度お試しください。", NamedTextColor.RED))
            return CompletableFuture.completedFuture(false)
        }
        return enterRuntime(player, runtime, returnToReadyOnFailure = true).whenComplete { _, _ -> replenish() }
    }

    fun enterSeed(player: Player, seed: Long): CompletableFuture<Boolean> {
        if (activeByPlayer.containsKey(player.uuid)) {
            player.sendMessage(Component.text("すでに生成済みクエストマップ内にいます。", NamedTextColor.YELLOW))
            return CompletableFuture.completedFuture(false)
        }
        player.sendMessage(Component.text("手動確認用マップを準備しています（seed=$seed）…", NamedTextColor.GRAY))
        return VerdantRoadQuestRuntime.prepare(seed, candidateCount = 1).thenCompose { runtime ->
            enterRuntime(player, runtime, returnToReadyOnFailure = false)
        }
    }

    fun startGathering(player: Player, blockPosition: BlockVec): Boolean {
        val runtime = activeByPlayer[player.uuid] ?: return false
        if (player.instance !== runtime.instance) return false
        val node = runtime.gatheringNodeAt(blockPosition) ?: return false
        return startGathering(player, runtime, node, blockPosition, null, requiresContinuousInput = true)
    }

    fun startGathering(player: Player, target: Entity): Boolean {
        val runtime = activeByPlayer[player.uuid] ?: return false
        if (player.instance !== runtime.instance || target.instance !== runtime.instance) return false
        val node = runtime.gatheringNodeForEntity(target) ?: return false
        val targetPosition = runtime.gatheringObjects.getValue(node.id).interactionPosition.asBlockVec()
        return startGathering(player, runtime, node, targetPosition, target, requiresContinuousInput = false)
    }

    private fun startGathering(
        player: Player,
        runtime: VerdantRoadQuestRuntime,
        node: QuestGatheringNode,
        targetPosition: BlockVec,
        targetEntity: Entity?,
        requiresContinuousInput: Boolean,
    ): Boolean {
        val now = System.currentTimeMillis()
        if (!runtime.isGatheringNodeAvailable(node, now)) {
            player.sendActionBar(Component.text("この採取物は再生中です。", NamedTextColor.DARK_GRAY))
            return true
        }
        if (!node.discipline.accepts(player.itemInMainHand)) {
            removeActiveGathering(player.uuid)
            player.sendActionBar(
                Component.text("${node.discipline.toolName}が必要です。", NamedTextColor.RED),
            )
            return true
        }
        val current = activeGathering[player.uuid]
        if (current != null && current.runtime === runtime && current.node.id == node.id) {
            current.lastInputTick = player.aliveTicks
            return true
        }
        removeActiveGathering(player.uuid)
        val mastery = gatheringMastery(player.uuid)
        val active = ActiveGathering(
            runtime = runtime,
            node = node,
            targetPosition = targetPosition,
            targetEntity = targetEntity,
            requiresContinuousInput = requiresContinuousInput,
            requiredTicks = mastery.harvestTicks(node.discipline),
            progressDisplay = createGatheringProgressDisplay(),
            lastInputTick = player.aliveTicks,
        )
        activeGathering[player.uuid] = active
        updateGatheringProgressDisplay(active)
        spawnGatheringProgressDisplay(player, active)
        return true
    }

    fun cancelGathering(player: Player) {
        removeActiveGathering(player.uuid)
    }

    fun protectGatheringNode(player: Player, blockPosition: BlockVec): Block? {
        val runtime = activeByPlayer[player.uuid] ?: return null
        runtime.gatheringNodeAt(blockPosition) ?: return null
        return runtime.gatheringBlockAt(blockPosition)
    }

    fun tick(player: Player) {
        val runtime = activeByPlayer[player.uuid] ?: return
        val now = System.currentTimeMillis()
        runtime.respawnGatheringNodes(now)
        if (player.aliveTicks % RARE_PARTICLE_INTERVAL_TICKS == 0L) {
            emitRareGatheringParticles(player, runtime, now)
        }
        val active = activeGathering[player.uuid] ?: return
        val interaction = active.runtime.gatheringObjects.getValue(active.node.id)
        val allowedDistance = maxOf(
            MIN_GATHERING_DISTANCE,
            interaction.interactionWidth / 2.0 + GATHERING_INTERACTION_REACH,
        )
        if (active.requiresContinuousInput && player.aliveTicks - active.lastInputTick > GATHERING_INPUT_GRACE_TICKS) {
            cancelGathering(player)
            return
        }
        if (active.runtime !== runtime || player.instance !== runtime.instance ||
            !active.node.discipline.accepts(player.itemInMainHand) ||
            player.position.distanceSquared(active.targetPosition) > allowedDistance * allowedDistance ||
            (active.targetEntity != null && active.targetEntity.instance !== runtime.instance)
        ) {
            cancelGathering(player)
            return
        }
        if (!runtime.isGatheringNodeAvailable(active.node, now)) {
            cancelGathering(player)
            return
        }
        active.elapsedTicks++
        if (active.elapsedTicks == 1 || active.elapsedTicks % GATHERING_SWING_INTERVAL_TICKS == 0) {
            player.sendPacketToViewersAndSelf(
                EntityAnimationPacket(player.entityId, EntityAnimationPacket.Animation.SWING_MAIN_ARM),
            )
            playGatheringSound(player, active.node.discipline, active.targetPosition, completion = false)
        }
        updateGatheringProgressDisplay(active)
        if (active.elapsedTicks < active.requiredTicks) return
        removeActiveGathering(player.uuid)
        if (!runtime.tryDepleteGatheringNode(active.node, now)) return
        playGatheringSound(player, active.node.discipline, active.targetPosition, completion = true)
        completeGathering(player, active.node)
    }

    fun gatheringMasterySummary(player: Player) {
        val mastery = gatheringMastery(player.uuid)
        player.sendMessage(Component.text("採取マスタリー", NamedTextColor.GOLD))
        QuestGatheringDiscipline.entries.forEach { discipline ->
            player.sendMessage(
                Component.text(
                    "${discipline.displayName}: Lv ${mastery.level(discipline)}（経験値 ${mastery.experience(discipline)}）",
                    NamedTextColor.GRAY,
                ),
            )
            player.sendMessage(
                Component.text(
                    "  ツリーポイント: 残り ${mastery.availableTreePoints(discipline)} / " +
                        "獲得 ${mastery.earnedTreePoints(discipline)}、" +
                        "取得済み=${mastery.unlockedNodes(discipline).joinToString { it.id }.ifEmpty { "なし" }}",
                    NamedTextColor.DARK_GRAY,
                ),
            )
        }
        player.sendMessage(Component.text("確認: /gathering tree <系統>　取得: /gathering unlock <系統> <ノード>", NamedTextColor.GRAY))
    }

    fun gatheringMasteryTree(player: Player, disciplineId: String?) {
        val mastery = gatheringMastery(player.uuid)
        val disciplines = if (disciplineId == null) {
            QuestGatheringDiscipline.entries
        } else {
            listOfNotNull(QuestGatheringDiscipline.entries.firstOrNull { it.id == disciplineId.lowercase() }).also {
                if (it.isEmpty()) player.sendMessage(Component.text("不明な採取系統です: $disciplineId", NamedTextColor.RED))
            }
        }
        disciplines.forEach { discipline ->
            val unlocked = mastery.unlockedNodes(discipline)
            player.sendMessage(
                Component.text(
                    "${discipline.displayName}ツリー — 使用可能ポイント ${mastery.availableTreePoints(discipline)}",
                    NamedTextColor.GOLD,
                ),
            )
            QuestGatheringMasteryNode.entries.forEach { node ->
                val state = when {
                    node in unlocked -> "取得済み"
                    node.prerequisite != null && node.prerequisite !in unlocked -> "前提: ${node.prerequisite.id}"
                    else -> "必要ポイント: ${node.cost}"
                }
                player.sendMessage(
                    Component.text(
                        "  ${node.id} [$state] — ${node.description}",
                        if (node in unlocked) NamedTextColor.GREEN else NamedTextColor.GRAY,
                    ),
                )
            }
        }
    }

    fun unlockGatheringMasteryNode(player: Player, disciplineId: String, nodeId: String) {
        val discipline = QuestGatheringDiscipline.entries.firstOrNull { it.id == disciplineId.lowercase() }
        val node = QuestGatheringMasteryNode.byId(nodeId.lowercase())
        if (discipline == null || node == null) {
            player.sendMessage(Component.text("採取系統またはマスタリーノードが見つかりません。", NamedTextColor.RED))
            return
        }
        val current = gatheringMastery(player.uuid)
        when (val result = current.unlock(discipline, node)) {
            is QuestGatheringMasteryUnlockResult.Unlocked -> {
                gatheringMasteries[player.uuid] = result.mastery
                masteryRepository.save(player.uuid, result.mastery)
                player.sendMessage(Component.text("${discipline.displayName}: ${node.displayName}を取得しました。", NamedTextColor.GREEN))
            }
            QuestGatheringMasteryUnlockResult.AlreadyUnlocked -> player.sendMessage(Component.text("そのノードは取得済みです。", NamedTextColor.YELLOW))
            QuestGatheringMasteryUnlockResult.MissingPrerequisite -> player.sendMessage(Component.text("先に${node.prerequisite?.id}を取得してください。", NamedTextColor.RED))
            QuestGatheringMasteryUnlockResult.KeystoneConflict -> player.sendMessage(Component.text("各採取系統で選べるキーストーンは1つだけです。", NamedTextColor.RED))
            QuestGatheringMasteryUnlockResult.NotEnoughPoints -> player.sendMessage(Component.text("マスタリーツリーポイントが足りません。", NamedTextColor.RED))
        }
    }

    fun prepareGatheringSmokeTest(player: Player) {
        val runtime = activeByPlayer[player.uuid]
        if (runtime == null) {
            player.sendMessage(Component.text("採取テストを使う前にクエストマップへ入ってください。", NamedTextColor.RED))
            return
        }
        val index = gatheringSmokeIndices.getOrDefault(player.uuid, 0).mod(runtime.gatheringNodes.size)
        gatheringSmokeIndices[player.uuid] = (index + 1).mod(runtime.gatheringNodes.size)
        val node = runtime.gatheringNodes[index]
        val gathering = runtime.gatheringObjects.getValue(node.id)
        val standDistance = gathering.interactionWidth / 2.0 + 2.5
        val standX = gathering.interactionPosition.blockX().coerceIn(1, runtime.plan.size - 2)
        val standZ = (gathering.interactionPosition.z() + standDistance).toInt().coerceIn(1, runtime.plan.size - 2)
        player.itemInMainHand = node.discipline.toolItem()
        player.teleport(
            Pos(
                standX + 0.5,
                runtime.plan.heightAt(standX, standZ) + 1.0,
                standZ + 0.5,
                180f,
                0f,
            ),
        )
        player.sendMessage(
            Component.text(
                "採取テスト ${index + 1}/${runtime.gatheringNodes.size}: 正面の${node.discipline.displayName}対象を右クリック長押ししてください。もう一度 /gathering test で次へ進みます。",
                NamedTextColor.GOLD,
            ),
        )
    }

    private fun completeGathering(player: Player, node: QuestGatheringNode) {
        val previous = gatheringMastery(player.uuid)
        val previousLevel = previous.level(node.discipline)
        val amount = previous.yieldAmount(node.discipline, node.quality)
        val nodeCenter = Vec(
            node.blockPosition.blockX() + 0.5,
            node.blockPosition.blockY() + 0.5,
            node.blockPosition.blockZ() + 0.5,
        )
        player.sendPacket(
            ParticlePacket(
                Particle.BLOCK.withBlock(node.discipline.nodeBlock),
                nodeCenter,
                Vec(0.42, 0.42, 0.42),
                0.12f,
                34,
            ),
        )
        giveGatheringReward(
            player,
            ItemStack.of(node.discipline.commonMaterial, amount)
                .withCustomName(Component.text(node.discipline.commonResourceName, NamedTextColor.WHITE)),
        )
        val rareDiscovered = node.quality == QuestGatheringQuality.RARE ||
            rareDiscoveryRoll(player.uuid, node, previous) < previous.rareDiscoveryChancePercent(node.discipline)
        if (rareDiscovered) {
            giveGatheringReward(
                player,
                ItemStack.of(node.discipline.rareMaterial)
                    .withCustomName(Component.text(node.discipline.rareResourceName, NamedTextColor.LIGHT_PURPLE))
                    .withGlowing(true),
            )
        }
        val updated = previous.addExperience(node.discipline, node.quality.masteryExperience)
        gatheringMasteries[player.uuid] = updated
        masteryRepository.save(player.uuid, updated)
        player.sendMessage(
            Component.text(
                "+$amount ${node.discipline.commonResourceName}  •  +${node.quality.masteryExperience} ${node.discipline.displayName}マスタリー経験値",
                if (rareDiscovered) NamedTextColor.LIGHT_PURPLE else NamedTextColor.GREEN,
            ),
        )
        val newLevel = updated.level(node.discipline)
        if (newLevel > previousLevel) {
            player.sendMessage(
                Component.text("${node.discipline.displayName}マスタリーがLv ${newLevel}になりました。", NamedTextColor.GOLD),
            )
        }
    }

    private fun giveGatheringReward(player: Player, item: ItemStack) {
        if (!player.inventory.addItemStack(item)) player.dropItem(item)
    }

    private fun playGatheringSound(
        player: Player,
        discipline: QuestGatheringDiscipline,
        position: BlockVec,
        completion: Boolean,
    ) {
        val key = when (discipline) {
            QuestGatheringDiscipline.SKINNING -> if (completion) "minecraft:entity.sheep.shear" else "minecraft:item.axe.scrape"
            QuestGatheringDiscipline.WOODCUTTING -> if (completion) "minecraft:block.wood.break" else "minecraft:block.wood.hit"
            QuestGatheringDiscipline.QUARRYING -> if (completion) "minecraft:block.stone.break" else "minecraft:block.stone.hit"
            QuestGatheringDiscipline.MINING -> if (completion) "minecraft:block.deepslate.break" else "minecraft:block.deepslate.hit"
            QuestGatheringDiscipline.HERBALISM -> if (completion) "minecraft:block.grass.break" else "minecraft:block.grass.hit"
        }
        val event = SoundEvent.fromKey(key) ?: return
        val pitch = if (completion) 0.9f else 0.82f + (player.aliveTicks % 4L) * 0.04f
        player.playSound(
            Sound.sound(event, Sound.Source.BLOCK, if (completion) 1.15f else 0.72f, pitch),
            position,
        )
    }

    private fun createGatheringProgressDisplay(): Entity = Entity(EntityType.TEXT_DISPLAY).apply {
        setAutoViewable(false)
        setNoGravity(true)
        editEntityMeta(TextDisplayMeta::class.java) { meta ->
            meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
            meta.setScale(Vec(0.72, 0.72, 0.72))
            meta.setViewRange(16f)
            meta.setShadow(true)
            meta.setSeeThrough(true)
            meta.setBackgroundColor(0xB0000000.toInt())
            meta.setLineWidth(180)
            meta.setBrightness(15, 15)
        }
    }

    private fun spawnGatheringProgressDisplay(player: Player, active: ActiveGathering) {
        val position = gatheringProgressDisplayPosition(player.position, player.eyeHeight, active.targetPosition)
        active.progressDisplay.setInstance(active.runtime.instance, position).thenRun {
            if (activeGathering[player.uuid] === active && player.instance === active.runtime.instance) {
                active.progressDisplay.addViewer(player)
            } else {
                active.progressDisplay.remove()
            }
        }
    }

    private fun updateGatheringProgressDisplay(active: ActiveGathering) {
        val filled = (active.elapsedTicks * GATHERING_BAR_SEGMENTS / active.requiredTicks)
            .coerceIn(0, GATHERING_BAR_SEGMENTS)
        val titleColor = if (active.node.quality == QuestGatheringQuality.RARE) {
            NamedTextColor.LIGHT_PURPLE
        } else {
            NamedTextColor.GOLD
        }
        val text = Component.text(
            "${active.node.quality.displayName} ${active.node.discipline.displayName}",
            titleColor,
        ).append(Component.newline())
            .append(Component.text("█".repeat(filled), NamedTextColor.GREEN))
            .append(Component.text("░".repeat(GATHERING_BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY))
        active.progressDisplay.editEntityMeta(TextDisplayMeta::class.java) { it.setText(text) }
    }

    private fun removeActiveGathering(playerId: UUID): ActiveGathering? =
        activeGathering.remove(playerId)?.also { it.progressDisplay.remove() }

    private fun rareDiscoveryRoll(
        playerId: UUID,
        node: QuestGatheringNode,
        mastery: QuestGatheringMastery,
    ): Int = Math.floorMod(
        playerId.mostSignificantBits xor playerId.leastSignificantBits xor
            (node.id * 2_654_435_761L) xor mastery.experience(node.discipline).toLong(),
        100L,
    ).toInt()

    private fun emitRareGatheringParticles(
        player: Player,
        runtime: VerdantRoadQuestRuntime,
        nowMillis: Long,
    ) {
        runtime.gatheringNodes.asSequence()
            .filter { it.quality == QuestGatheringQuality.RARE && runtime.isGatheringNodeAvailable(it, nowMillis) }
            .filter { player.position.distanceSquared(it.blockPosition) <= RARE_PARTICLE_DISTANCE_SQUARED }
            .forEach { node ->
                player.sendPacket(
                    ParticlePacket(
                        Particle.END_ROD,
                        Vec(
                            node.blockPosition.blockX() + 0.5,
                            node.blockPosition.blockY() + 1.0,
                            node.blockPosition.blockZ() + 0.5,
                        ),
                        Vec(0.35, 0.55, 0.35),
                        0.01f,
                        3,
                    ),
                )
            }
    }

    private fun gatheringMastery(playerId: UUID): QuestGatheringMastery = gatheringMasteries.computeIfAbsent(playerId) {
        when (val loaded = masteryRepository.load(playerId)) {
            QuestGatheringMasteryLoadResult.Missing -> QuestGatheringMastery()
            is QuestGatheringMasteryLoadResult.Loaded -> loaded.mastery
            is QuestGatheringMasteryLoadResult.Invalid -> {
                System.err.println("Gathering mastery load blocked for $playerId: ${loaded.reason}")
                QuestGatheringMastery()
            }
        }
    }

    private fun ensureGatheringTools(player: Player) {
        val ownedDisciplines = player.inventory.itemStacks.mapNotNull { it.getTag(QUEST_GATHERING_TOOL_TAG) }.toSet()
        QuestGatheringDiscipline.entries.filterNot { it.id in ownedDisciplines }.forEach { discipline ->
            giveGatheringReward(player, discipline.toolItem())
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
                player.sendMessage(Component.text("クエストマップへの転送に失敗しました: ${failure.message}", NamedTextColor.RED))
                println("Quest map transfer failed for ${player.username}: ${failure.message}")
                false
            } else {
                val transferMillis = (System.nanoTime() - transferStartedAt) / 1_000_000
                gatheringMastery(player.uuid)
                ensureGatheringTools(player)
                println(
                    "Quest map transfer complete: player=${player.username} seed=${runtime.plan.seed} " +
                        "style=${runtime.plan.style} layout=${runtime.plan.routeLayout} " +
                        "terrain=${runtime.plan.terrainProfile} chunks=${runtime.loadedChunkCount} " +
                        "ready=${runtime.preparationMillis}ms transfer=${transferMillis}ms",
                )
                player.sendMessage(
                    Component.text(
                        "クエストマップ seed=${runtime.plan.seed} 景観=${runtime.plan.style} 経路=${runtime.plan.routeLayout} " +
                            "地形=${runtime.plan.terrainProfile} チャンク=${runtime.loadedChunkCount} " +
                            "候補数=${runtime.candidateCount} 評価=${"%.2f".format(runtime.candidateScore.total)} " +
                            "生成=${runtime.preparationMillis}ms 転送=${transferMillis}ms",
                        NamedTextColor.GREEN,
                    ),
                )
                player.sendMessage(Component.text("道を進むとボス地点へ到達できます。脇道には採取物や発見があります。", NamedTextColor.GRAY))
                player.sendMessage(Component.text("採取: 対応する道具を持ち、対象を右クリック長押ししてください。", NamedTextColor.GOLD))
                true
            }
        }
    }

    fun returnToHub(player: Player): CompletableFuture<Boolean> {
        val runtime = activeByPlayer.remove(player.uuid) ?: return CompletableFuture.completedFuture(false)
        removeActiveGathering(player.uuid)
        player.setVelocity(Vec.ZERO)
        return player.setInstance(hubInstance, hubSpawn).handle { _, failure ->
            if (failure == null) {
                runtime.close()
                player.sendMessage(Component.text("ProjectSの拠点へ戻りました。", NamedTextColor.GREEN))
                true
            } else {
                activeByPlayer[player.uuid] = runtime
                player.sendMessage(Component.text("拠点への転送に失敗しました: ${failure.message}", NamedTextColor.RED))
                false
            }
        }
    }

    fun disconnect(playerId: UUID) {
        removeActiveGathering(playerId)
        gatheringMasteries.remove(playerId)?.let { masteryRepository.save(playerId, it) }
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
                    "size=${runtime.plan.size} chunks=${runtime.loadedChunkCount} " +
                    "candidates=${runtime.candidateCount} score=${"%.2f".format(runtime.candidateScore.total)} " +
                    "preparation=${runtime.preparationMillis}ms",
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
        const val GATHERING_BAR_SEGMENTS = 12
        const val GATHERING_SWING_INTERVAL_TICKS = 6
        const val GATHERING_INPUT_GRACE_TICKS = 8L
        const val MIN_GATHERING_DISTANCE = 7.0
        const val GATHERING_INTERACTION_REACH = 4.0
        const val RARE_PARTICLE_INTERVAL_TICKS = 18L
        const val RARE_PARTICLE_DISTANCE_SQUARED = 28.0 * 28.0
    }
}
