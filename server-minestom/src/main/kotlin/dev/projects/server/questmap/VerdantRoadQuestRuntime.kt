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
                QuestMapContentKind.COMBAT -> decorateCombat(instance, plan, content.position)
                QuestMapContentKind.GATHERING -> decorateGathering(instance, plan, content.position, ordinal)
                QuestMapContentKind.DISCOVERY -> decorateDiscovery(instance, plan, content.position)
                QuestMapContentKind.BOSS -> decorateBossArena(instance, plan, content.position)
            }
        }
    }

    private fun decorateTrees(instance: Instance, plan: QuestMapPlan) {
        val random = Random(plan.seed xor 0x47524F5645L)
        val occupied = mutableSetOf<QuestMapPoint>()
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
            if (occupied.any { it.distanceSquared(point) < 7 * 7 }) return@repeat
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
            if (terrainRange(plan, point, 2) > 1 || plan.slopeAt(point) > 1) return@repeat
            occupied += point
            QuestMapStructureAssets.placeTree(
                instance,
                plan,
                point,
                random.nextInt(),
                random.nextInt(4),
            )
            decorateTreeBase(instance, plan, point, random)
        }
    }

    private fun decorateTerrainDetail(instance: Instance, plan: QuestMapPlan) {
        val random = Random(plan.seed xor 0x5445525241494EL)
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
            when (cover) {
                QuestGroundCover.ROCKY -> when {
                    roll < 34 && terrainRange(plan, point, 4) <= 5 -> QuestMapStructureAssets.placeBoulder(
                        instance,
                        plan,
                        point,
                        random.nextInt(),
                        assetRotation,
                    )
                    roll < 62 -> instance.setBlock(point.x, ground, point.z, if (plan.style == QuestTerrainStyle.HIGHLANDS) Block.TUFF else Block.ANDESITE)
                    else -> instance.setBlock(point.x, ground + 1, point.z, Block.DEAD_BUSH)
                }
                QuestGroundCover.FOREST_FLOOR -> when {
                    roll < 8 && lineTerrainRange(plan, point, 3, assetRotation) <= 1 -> QuestMapStructureAssets.placeFallenLog(
                        instance,
                        plan,
                        point,
                        3 + random.nextInt(4),
                        assetRotation,
                        random.nextInt(),
                    )
                    roll < 30 -> instance.setBlock(point.x, ground + 1, point.z, Block.FERN)
                    roll < 42 -> instance.setBlock(point.x, ground + 1, point.z, if (roll and 1 == 0) Block.BROWN_MUSHROOM else Block.RED_MUSHROOM)
                    roll < 54 -> instance.setBlock(point.x, ground + 1, point.z, Block.MOSS_CARPET)
                    else -> instance.setBlock(point.x, ground + 1, point.z, Block.SHORT_GRASS)
                }
                QuestGroundCover.MEADOW -> when {
                    roll < 4 && terrainRange(plan, point, 3) <= 2 -> QuestMapStructureAssets.placeBoulder(
                        instance,
                        plan,
                        point,
                        random.nextInt(),
                        assetRotation,
                    )
                    roll < 9 -> instance.setBlock(point.x, ground + 1, point.z, Block.DANDELION)
                    roll < 16 -> instance.setBlock(point.x, ground + 1, point.z, Block.POPPY)
                    roll < 22 -> instance.setBlock(point.x, ground + 1, point.z, Block.AZURE_BLUET)
                    roll < 29 -> instance.setBlock(point.x, ground + 1, point.z, Block.TALL_GRASS)
                    else -> instance.setBlock(point.x, ground + 1, point.z, Block.SHORT_GRASS)
                }
                QuestGroundCover.HEATH -> when {
                    roll < 7 && terrainRange(plan, point, 3) <= 3 -> QuestMapStructureAssets.placeBoulder(
                        instance,
                        plan,
                        point,
                        random.nextInt(),
                        assetRotation,
                    )
                    roll < 14 -> instance.setBlock(point.x, ground + 1, point.z, Block.SWEET_BERRY_BUSH)
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

    private fun decorateRoadGuidance(instance: Instance, plan: QuestMapPlan) {
        val interval = maxOf(35, plan.mainRoute.size / 6)
        for (routeIndex in interval until plan.mainRoute.lastIndex - interval step interval) {
            val point = plan.mainRoute[routeIndex]
            val before = plan.mainRoute[(routeIndex - 4).coerceAtLeast(0)]
            val after = plan.mainRoute[(routeIndex + 4).coerceAtMost(plan.mainRoute.lastIndex)]
            val sideX = (after.z - before.z).coerceIn(-1, 1) * 4
            val sideZ = (before.x - after.x).coerceIn(-1, 1) * 4
            val marker = QuestMapPoint(
                (point.x + sideX).coerceIn(3, plan.size - 4),
                (point.z + sideZ).coerceIn(3, plan.size - 4),
            )
            placeLanternPost(instance, marker.x, plan.heightAt(marker) + 1, marker.z)
        }
    }

    private fun decorateStart(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint) {
        val ground = plan.heightAt(center)
        for (z in center.z - 4..center.z + 4) {
            for (x in center.x - 4..center.x + 4) {
                if ((x + z) % 3 == 0) instance.setBlock(x, ground, z, Block.COARSE_DIRT)
            }
        }
        instance.setBlock(center.x, ground + 1, center.z, Block.CAMPFIRE)
        instance.setBlock(center.x + 2, ground + 1, center.z, Block.BARREL)
        instance.setBlock(center.x - 2, ground + 1, center.z, Block.CRAFTING_TABLE)
        for (x in center.x - 3..center.x + 3) instance.setBlock(x, ground + 4, center.z - 4, Block.GREEN_WOOL)
        listOf(center.x - 3, center.x + 3).forEach { x ->
            repeat(3) { height -> instance.setBlock(x, ground + 1 + height, center.z - 4, Block.OAK_FENCE) }
        }
        placeLanternPost(instance, center.x, ground + 1, center.z + 4)
    }

    private fun decorateCombat(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint) {
        val y = plan.heightAt(center)
        for (step in 0 until 28) {
            val angle = Math.PI * 2.0 * step / 28.0
            val x = center.x + (kotlin.math.cos(angle) * 6.0).roundToInt()
            val z = center.z + (kotlin.math.sin(angle) * 6.0).roundToInt()
            instance.setBlock(x, plan.heightAt(x, z), z, Block.MOSSY_COBBLESTONE)
        }
        listOf(-4 to -4, -4 to 4, 4 to -4, 4 to 4).forEach { (dx, dz) ->
            repeat(2) { height -> instance.setBlock(center.x + dx, y + 1 + height, center.z + dz, Block.MOSSY_STONE_BRICKS) }
            instance.setBlock(center.x + dx, y + 3, center.z + dz, Block.LANTERN)
        }
    }

    private fun decorateGathering(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint, ordinal: Int) {
        val y = plan.heightAt(center) + 1
        val ore = when (ordinal % 4) {
            0 -> Block.RAW_COPPER_BLOCK
            1 -> Block.IRON_ORE
            2 -> Block.COAL_ORE
            else -> Block.AMETHYST_BLOCK
        }
        listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1).forEachIndexed { index, (dx, dz) ->
            instance.setBlock(center.x + dx, y + if (index == 0) 1 else 0, center.z + dz, ore)
        }
        listOf(-3 to -3, 3 to -3).forEach { (dx, dz) ->
            repeat(3) { height -> instance.setBlock(center.x + dx, y + height, center.z + dz, Block.STRIPPED_OAK_LOG) }
        }
        for (x in center.x - 3..center.x + 3) instance.setBlock(x, y + 3, center.z - 3, Block.BROWN_WOOL)
    }

    private fun decorateDiscovery(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint) {
        val y = plan.heightAt(center) + 1
        for (height in 0..4) {
            instance.setBlock(center.x - 2, y + height, center.z, Block.STONE_BRICKS)
            instance.setBlock(center.x + 2, y + height, center.z, Block.STONE_BRICKS)
        }
        for (x in center.x - 2..center.x + 2) instance.setBlock(x, y + 5, center.z, Block.CHISELED_STONE_BRICKS)
        instance.setBlock(center.x, y, center.z, Block.AMETHYST_BLOCK)
        instance.setBlock(center.x, y + 1, center.z, Block.CANDLE)
    }

    private fun decorateBossArena(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint) {
        val ground = plan.heightAt(center)
        for (z in center.z - 14..center.z + 14) {
            for (x in center.x - 14..center.x + 14) {
                val distance = QuestMapPoint(x, z).distanceSquared(center)
                if (distance <= 13 * 13) {
                    val block = when {
                        distance >= 11 * 11 -> Block.POLISHED_DEEPSLATE
                        distance % 11 == 0 -> Block.CRACKED_DEEPSLATE_BRICKS
                        else -> Block.POLISHED_ANDESITE
                    }
                    instance.setBlock(x, ground, z, block)
                }
            }
        }
        instance.setBlock(center.x, ground + 1, center.z, Block.LODESTONE)
        listOf(-10 to 0, 10 to 0, 0 to -10, 0 to 10).forEach { (dx, dz) ->
            repeat(5) { height -> instance.setBlock(center.x + dx, ground + 1 + height, center.z + dz, Block.DEEPSLATE_BRICKS) }
            instance.setBlock(center.x + dx, ground + 6, center.z + dz, Block.SOUL_LANTERN)
        }
    }

    private fun placeLanternPost(instance: Instance, x: Int, y: Int, z: Int) {
        repeat(3) { offset -> instance.setBlock(x, y + offset, z, Block.OAK_FENCE) }
        instance.setBlock(x, y + 3, z, Block.LANTERN)
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
