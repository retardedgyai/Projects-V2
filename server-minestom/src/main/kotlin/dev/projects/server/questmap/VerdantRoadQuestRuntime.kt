package dev.projects.server.questmap

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
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
        unit.modifier().setAllRelative { relativeX, relativeY, relativeZ ->
            blockAt(
                start.blockX() + relativeX,
                start.blockY() + relativeY,
                start.blockZ() + relativeZ,
            )
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
            return if (plan.style == QuestTerrainStyle.SALTMARSH && !road && y <= QUEST_WATER_LEVEL) Block.WATER else Block.AIR
        }
        if (y < ground - 4) return if (plan.style == QuestTerrainStyle.HIGHLANDS) Block.TUFF else Block.STONE
        if (y < ground) {
            if (exposedCliff && neighborHeights.min() < y) {
                return when (plan.style) {
                    QuestTerrainStyle.VERDANT -> if ((x + y + z) and 3 == 0) Block.ANDESITE else Block.STONE
                    QuestTerrainStyle.HIGHLANDS -> if ((x + y + z) and 3 == 0) Block.COBBLESTONE else Block.TUFF
                    QuestTerrainStyle.SALTMARSH -> if ((x + y + z) and 3 == 0) Block.MUD_BRICKS else Block.STONE
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
            }
        }
        if (sideTrail) {
            return when (plan.style) {
                QuestTerrainStyle.SALTMARSH -> Block.MUD
                QuestTerrainStyle.HIGHLANDS -> Block.PODZOL
                QuestTerrainStyle.VERDANT -> Block.ROOTED_DIRT
            }
        }
        return surfaceBlock(x, z, ground, exposedCliff)
    }

    private fun subsurfaceBlock(x: Int, z: Int, ground: Int): Block = when (plan.groundCoverAt(x, z)) {
        QuestGroundCover.ROCKY -> if (plan.style == QuestTerrainStyle.HIGHLANDS) Block.TUFF else Block.STONE
        QuestGroundCover.SHORE -> if (ground <= QUEST_WATER_LEVEL - 2) Block.CLAY else Block.MUD
        QuestGroundCover.PEAT -> if ((x + z) and 3 == 0) Block.PACKED_MUD else Block.MUD
        QuestGroundCover.HEATH -> if (plan.style == QuestTerrainStyle.HIGHLANDS && ground >= 66) Block.STONE else Block.DIRT
        QuestGroundCover.MEADOW,
        QuestGroundCover.FOREST_FLOOR -> Block.DIRT
    }

    private fun surfaceBlock(x: Int, z: Int, ground: Int, exposedCliff: Boolean): Block {
        if (exposedCliff) {
            return when (plan.style) {
                QuestTerrainStyle.VERDANT -> if (plan.surfacePatchAt(x, z) <= 1) Block.MOSSY_COBBLESTONE else Block.ANDESITE
                QuestTerrainStyle.HIGHLANDS -> if (plan.surfacePatchAt(x, z) <= 1) Block.TUFF else Block.STONE
                QuestTerrainStyle.SALTMARSH -> if (ground <= QUEST_WATER_LEVEL + 1) Block.MUD_BRICKS else Block.STONE
            }
        }
        val patch = plan.surfacePatchAt(x, z)
        val variation = Math.floorMod(plan.seed xor (x * 1_299_721L) xor (z * 741_457L), 19L).toInt()
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
        decorateTrees(instance, plan)
        decorateTerrainDetail(instance, plan)
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

    private fun decorateTrees(instance: Instance, plan: QuestMapPlan) {
        val random = Random(plan.seed xor 0x47524F5645L)
        val occupied = mutableListOf<Pair<QuestMapPoint, Int>>()
        val groveCenters = List(
            when (plan.style) {
                QuestTerrainStyle.VERDANT -> 10
                QuestTerrainStyle.HIGHLANDS -> 8
                QuestTerrainStyle.SALTMARSH -> 7
            },
        ) {
            QuestMapPoint(
                18 + random.nextInt(plan.size - 36),
                18 + random.nextInt(plan.size - 36),
            )
        }
        val attempts = when (plan.style) {
            QuestTerrainStyle.VERDANT -> 760
            QuestTerrainStyle.HIGHLANDS -> 560
            QuestTerrainStyle.SALTMARSH -> 480
        }
        repeat(attempts) {
            val point = if (random.nextInt(100) < 78) {
                val grove = groveCenters[random.nextInt(groveCenters.size)]
                val angle = random.nextDouble() * Math.PI * 2.0
                val radius = 5.0 + random.nextDouble() * 29.0
                QuestMapPoint(
                    (grove.x + Math.cos(angle) * radius).roundToInt().coerceIn(10, plan.size - 11),
                    (grove.z + Math.sin(angle) * radius).roundToInt().coerceIn(10, plan.size - 11),
                )
            } else {
                QuestMapPoint(10 + random.nextInt(plan.size - 20), 10 + random.nextInt(plan.size - 20))
            }
            if (plan.roadDistanceSquaredAt(point.x, point.z) <= 7 * 7) return@repeat
            if (plan.contents.any { it.position.distanceSquared(point) < 11 * 11 }) return@repeat
            val variation = random.nextInt()
            val footprint = QuestMapStructureAssets.treeFootprint(plan.style, variation)
            val clearance = maxOf(7, footprint + 2)
            if (occupied.any { (other, otherClearance) -> other.distanceSquared(point) < maxOf(clearance, otherClearance).let { it * it } }) return@repeat
            if (plan.style == QuestTerrainStyle.SALTMARSH && plan.heightAt(point) <= QUEST_WATER_LEVEL) return@repeat
            val density = when (plan.groundCoverAt(point)) {
                QuestGroundCover.FOREST_FLOOR -> 88
                QuestGroundCover.PEAT -> if (plan.style == QuestTerrainStyle.SALTMARSH) 58 else 20
                QuestGroundCover.HEATH -> 28
                QuestGroundCover.MEADOW -> 18
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

    private fun decorateTerrainDetail(instance: Instance, plan: QuestMapPlan) {
        val random = Random(plan.seed xor 0x5445525241494EL)
        val occupiedScenes = mutableListOf<QuestMapPoint>()
        repeat(2_800) {
            val point = QuestMapPoint(8 + random.nextInt(plan.size - 16), 8 + random.nextInt(plan.size - 16))
            if (plan.roadDistanceSquaredAt(point.x, point.z) <= 4 * 4) return@repeat
            if (plan.contents.any { it.position.distanceSquared(point) < 6 * 6 }) return@repeat
            val ground = plan.heightAt(point)
            if (plan.style == QuestTerrainStyle.SALTMARSH && ground <= QUEST_WATER_LEVEL) {
                if (random.nextInt(8) == 0) {
                    instance.setBlock(point.x, QUEST_WATER_LEVEL + 1, point.z, Block.LILY_PAD)
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
            when (cover) {
                QuestGroundCover.ROCKY -> when {
                    roll < 12 && sceneClear(7) && terrainRange(plan, point, 4) <= 3 -> {
                        QuestMapStructureAssets.placeBoulder(instance, plan, point, random.nextInt(), assetRotation)
                        rememberScene()
                    }
                    roll < 25 && sceneClear(6) && terrainRange(plan, point, 3) <= 2 -> {
                        QuestMapStructureAssets.placeShrubCluster(instance, plan, point, random.nextInt(), assetRotation)
                        rememberScene()
                    }
                    roll < 58 -> instance.setBlock(point.x, ground, point.z, if (plan.style == QuestTerrainStyle.HIGHLANDS) Block.TUFF else Block.ANDESITE)
                    else -> instance.setBlock(point.x, ground + 1, point.z, Block.DEAD_BUSH)
                }
                QuestGroundCover.FOREST_FLOOR -> when {
                    roll < 5 && sceneClear(8) && lineTerrainRange(plan, point, 3, assetRotation) <= 1 -> {
                        QuestMapStructureAssets.placeFallenLog(instance, plan, point, 3 + random.nextInt(4), assetRotation, random.nextInt())
                        rememberScene()
                    }
                    roll < 13 && sceneClear(6) && terrainRange(plan, point, 3) <= 2 -> {
                        QuestMapStructureAssets.placeShrubCluster(instance, plan, point, random.nextInt(), assetRotation)
                        rememberScene()
                    }
                    roll < 30 -> instance.setBlock(point.x, ground + 1, point.z, Block.FERN)
                    roll < 42 -> instance.setBlock(point.x, ground + 1, point.z, if (roll and 1 == 0) Block.BROWN_MUSHROOM else Block.RED_MUSHROOM)
                    roll < 54 -> instance.setBlock(point.x, ground + 1, point.z, Block.MOSS_CARPET)
                    else -> instance.setBlock(point.x, ground + 1, point.z, Block.SHORT_GRASS)
                }
                QuestGroundCover.MEADOW -> when {
                    roll < 2 && sceneClear(8) && terrainRange(plan, point, 3) <= 2 -> {
                        QuestMapStructureAssets.placeBoulder(instance, plan, point, random.nextInt(), assetRotation)
                        rememberScene()
                    }
                    roll < 7 && sceneClear(7) && terrainRange(plan, point, 3) <= 2 -> {
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
                    roll < 5 && sceneClear(8) && terrainRange(plan, point, 3) <= 3 -> {
                        QuestMapStructureAssets.placeBoulder(instance, plan, point, random.nextInt(), assetRotation)
                        rememberScene()
                    }
                    roll < 17 && sceneClear(6) && terrainRange(plan, point, 3) <= 2 -> {
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
        repeat(1_100) {
            val point = QuestMapPoint(8 + random.nextInt(plan.size - 16), 8 + random.nextInt(plan.size - 16))
            if (plan.roadDistanceSquaredAt(point.x, point.z) <= 4 * 4) return@repeat
            if (plan.contents.any { it.position.distanceSquared(point) < 7 * 7 }) return@repeat
            val waterDistance = plan.waterDistanceAt(point)
            if (waterDistance > 4) return@repeat
            val ground = plan.heightAt(point)
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
            val block = when (random.nextInt(6)) {
                0 -> Block.BROWN_MUSHROOM
                1, 2 -> Block.FERN
                3 -> Block.MOSS_CARPET
                else -> Block.SHORT_GRASS
            }
            instance.setBlock(x, ground + 1, z, block)
        }
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
        val interval = maxOf(44, plan.mainRoute.size / 6)
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
        val camp = framedPoint(center, frame, 0, 6)
        for (forward in -4..4) {
            for (side in -4..4) {
                if (forward * forward + side * side > 20) continue
                val point = framedPoint(camp, frame, forward, side)
                val block = when (Math.floorMod(forward * 7 + side * 11, 9)) {
                    0, 1 -> Block.COARSE_DIRT
                    2 -> Block.ROOTED_DIRT
                    else -> if (plan.style == QuestTerrainStyle.HIGHLANDS) Block.PODZOL else Block.DIRT_PATH
                }
                paintSurface(instance, plan, point, block)
            }
        }

        val fire = framedPoint(camp, frame, 1, -1)
        setGrounded(instance, plan, fire, 0, Block.CAMPFIRE)
        listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).forEach { (forward, side) ->
            setGrounded(instance, plan, framedPoint(fire, frame, forward, side), 0, Block.COBBLESTONE)
        }

        // A compact field-work canopy with an obvious crafting/storage purpose.
        val shelter = framedPoint(camp, frame, -1, 2)
        listOf(-2 to -2, -2 to 2, 2 to -2, 2 to 2).forEach { (forward, side) ->
            val post = framedPoint(shelter, frame, forward, side)
            repeat(3) { height -> setGrounded(instance, plan, post, height, Block.STRIPPED_OAK_LOG) }
        }
        for (forward in -2..2) {
            for (side in -2..2) {
                val roof = framedPoint(shelter, frame, forward, side)
                setGrounded(instance, plan, roof, 3, if ((forward + side) and 1 == 0) Block.DARK_OAK_SLAB else Block.SPRUCE_SLAB)
            }
        }
        setGrounded(instance, plan, framedPoint(shelter, frame, 0, 1), 0, Block.CRAFTING_TABLE)
        setGrounded(instance, plan, framedPoint(shelter, frame, -1, 1), 0, Block.BARREL)
        setGrounded(instance, plan, framedPoint(shelter, frame, 1, 1), 0, Block.BARREL)
        for (forward in -1..1) {
            setGrounded(instance, plan, framedPoint(shelter, frame, forward, -1), 0, Block.GREEN_CARPET)
        }
    }

    private fun decorateCombat(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint, ordinal: Int) {
        val frame = routeFrame(plan, center)
        for (forward in -7..7) {
            for (side in -7..7) {
                if (forward * forward + side * side > 48) continue
                val point = framedPoint(center, frame, forward, side)
                if (Math.floorMod(forward * 13 + side * 5 + ordinal, 7) <= 1) {
                    paintSurface(instance, plan, point, if (plan.style == QuestTerrainStyle.HIGHLANDS) Block.GRAVEL else Block.COARSE_DIRT)
                }
            }
        }
        when (ordinal % 3) {
            0 -> {
                // The encounter sits inside one broken retaining wall with a readable entry/exit.
                for (side in -7..7) {
                    if (side in -1..1 || Math.floorMod(side + ordinal, 5) == 0) continue
                    val wall = framedPoint(center, frame, 4, side)
                    setGrounded(instance, plan, wall, 0, if (side and 1 == 0) Block.MOSSY_STONE_BRICKS else Block.CRACKED_STONE_BRICKS)
                    if (kotlin.math.abs(side) >= 4) setGrounded(instance, plan, wall, 1, Block.MOSSY_STONE_BRICKS)
                }
                QuestMapStructureAssets.placeBoulder(instance, plan, framedPoint(center, frame, 5, -6), ordinal * 31 + 1, 1)
                QuestMapStructureAssets.placeBoulder(instance, plan, framedPoint(center, frame, 5, 6), ordinal * 31 + 2, 3)
            }
            1 -> {
                // A timber ambush camp: barricades frame combat but never block the route.
                listOf(-1 to -6, 1 to -6, -1 to 6, 1 to 6).forEach { (forward, side) ->
                    val log = framedPoint(center, frame, forward, side)
                    setGrounded(instance, plan, log, 0, Block.STRIPPED_SPRUCE_LOG)
                    setGrounded(instance, plan, framedPoint(log, frame, 1, 0), 0, Block.STRIPPED_SPRUCE_LOG)
                }
                setGrounded(instance, plan, framedPoint(center, frame, 2, 4), 0, Block.BARREL)
                setGrounded(instance, plan, framedPoint(center, frame, 1, 3), 0, Block.CAMPFIRE)
                for (side in -2..2) setGrounded(instance, plan, framedPoint(center, frame, -5, side), 0, Block.SPRUCE_SLAB)
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
                paintSurface(instance, plan, point, if ((forward + side) and 2 == 0) Block.GRAVEL else Block.ANDESITE)
            }
        }
        listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, -1 to 1).forEachIndexed { index, (forward, side) ->
            val point = framedPoint(face, frame, forward, side)
            val ground = plan.heightAt(point)
            instance.setBlock(point.x, ground + if (index == 0) 1 else 0, point.z, ore)
        }
        setGrounded(instance, plan, framedPoint(center, frame, -2, -3), 0, Block.BARREL)
        setGrounded(instance, plan, framedPoint(center, frame, -1, -3), 0, Block.CRAFTING_TABLE)
        for (forward in -2..2) setGrounded(instance, plan, framedPoint(center, frame, forward, -4), 0, Block.SPRUCE_SLAB)
    }

    private fun decorateDiscovery(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint, ordinal: Int) {
        val frame = routeFrame(plan, center)
        when (ordinal % 3) {
            0 -> {
                // A spring framed by a low ruin; the pool is the focal point.
                for (forward in -2..2) {
                    for (side in -2..2) {
                        val point = framedPoint(center, frame, forward, side)
                        if (forward * forward + side * side <= 3) {
                            instance.setBlock(point.x, plan.heightAt(point), point.z, Block.WATER)
                        }
                    }
                }
                for (side in -4..4) {
                    if (side == 0 || Math.floorMod(side, 3) == 0) continue
                    setGrounded(instance, plan, framedPoint(center, frame, 3, side), 0, Block.MOSSY_STONE_BRICKS)
                }
            }
            1 -> {
                // A collapsed wayside shrine has a broad base and a deliberate recessed focal niche.
                for (side in -4..4) {
                    val point = framedPoint(center, frame, 2, side)
                    setGrounded(instance, plan, point, 0, if (side and 1 == 0) Block.MOSSY_STONE_BRICKS else Block.CRACKED_STONE_BRICKS)
                    if (kotlin.math.abs(side) in 2..3) setGrounded(instance, plan, point, 1, Block.STONE_BRICKS)
                }
                setGrounded(instance, plan, framedPoint(center, frame, 2, 0), 1, Block.CHISELED_STONE_BRICKS)
                setGrounded(instance, plan, framedPoint(center, frame, 1, 0), 0, Block.CANDLE)
            }
            else -> {
                // A rooted stone seat: landscape and discovery object read as one silhouette.
                QuestMapStructureAssets.placeBoulder(instance, plan, framedPoint(center, frame, 2, 1), ordinal * 71, 2)
                for (side in -2..2) {
                    setGrounded(instance, plan, framedPoint(center, frame, 0, side), 0, Block.MOSSY_COBBLESTONE_SLAB)
                }
                setGrounded(instance, plan, framedPoint(center, frame, 1, 0), 0, Block.AMETHYST_CLUSTER)
            }
        }
    }

    private fun decorateBossArena(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint) {
        val frame = routeFrame(plan, center)
        for (forward in -13..13) {
            for (side in -13..13) {
                val distance = forward * forward + side * side
                if (distance > 13 * 13) continue
                val point = framedPoint(center, frame, forward, side)
                val block = when {
                    distance >= 11 * 11 -> if (Math.floorMod(forward + side, 4) == 0) Block.CRACKED_DEEPSLATE_BRICKS else Block.POLISHED_DEEPSLATE
                    Math.floorMod(forward * 5 + side * 7, 17) <= 1 -> Block.CRACKED_DEEPSLATE_TILES
                    else -> Block.POLISHED_ANDESITE
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
                    setGrounded(instance, plan, point, 0, Block.DEEPSLATE_BRICKS)
                }
            }
            repeat(3) { height -> setGrounded(instance, plan, base, height, if (height == 1) Block.CRACKED_DEEPSLATE_BRICKS else Block.DEEPSLATE_BRICKS) }
            setGrounded(instance, plan, framedPoint(base, frame, 0, if (side > 0) -1 else 1), 0, Block.SOUL_LANTERN)
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
                    layer == height - 1 && side % 3 == 0 -> Block.CHISELED_DEEPSLATE
                    (side + layer) and 3 == 0 -> Block.CRACKED_DEEPSLATE_BRICKS
                    else -> Block.DEEPSLATE_BRICKS
                }
                setGrounded(instance, plan, point, layer, block)
            }
        }
        setGrounded(instance, plan, framedPoint(center, frame, 10, 0), 1, Block.LODESTONE)
        setGrounded(instance, plan, framedPoint(center, frame, 10, 0), 2, Block.SOUL_LANTERN)
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
            val chunkCount = plan.size / 16
            val chunks = buildList {
                for (chunkX in -1..chunkCount) {
                    for (chunkZ in -1..chunkCount) add(instance.loadChunk(chunkX, chunkZ))
                }
            }
            return CompletableFuture.allOf(*chunks.toTypedArray()).thenApply {
                VerdantRoadQuestDecorator.decorate(instance, plan)
                val spawn = Pos(plan.start.x + 0.5, plan.heightAt(plan.start) + 1.0, plan.start.z + 0.5)
                VerdantRoadQuestRuntime(
                    plan,
                    instance,
                    spawn,
                    (System.nanoTime() - startedAt) / 1_000_000,
                )
            }.whenComplete { _, failure ->
                if (failure != null) MinecraftServer.getInstanceManager().unregisterInstance(instance)
            }
        }
    }
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
                            "terrain=${runtime.plan.terrainProfile} ready=${runtime.preparationMillis}ms transfer=${transferMillis}ms",
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
                    "size=${runtime.plan.size} preparation=${runtime.preparationMillis}ms",
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
