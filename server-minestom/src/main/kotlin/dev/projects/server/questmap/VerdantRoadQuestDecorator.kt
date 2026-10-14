package dev.projects.server.questmap

import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import java.util.Random
import kotlin.math.roundToInt

internal object VerdantRoadQuestDecorator {
    fun decorate(
        instance: InstanceContainer,
        plan: QuestMapPlan,
        gatheringNodes: List<QuestGatheringNode> = questGatheringNodes(plan),
    ) {
        decorateLandscapeScenes(instance, plan)
        decorateTrees(instance, plan, plan.landscapeScenes)
        decorateTerrainDetail(instance, plan, plan.landscapeScenes)
        QuestMapFlora.decorate(instance, plan, plan.landscapeScenes)
        decorateWaterEdges(instance, plan)
        decorateRoadGuidance(instance, plan)
        plan.contents.forEachIndexed { ordinal, content ->
            when (content.kind) {
                QuestMapContentKind.START -> decorateStart(instance, plan, content.position)
                QuestMapContentKind.COMBAT -> decorateCombat(instance, plan, content.position, ordinal)
                QuestMapContentKind.GATHERING ->
                    decorateGathering(instance, plan, content.position, gatheringNodes)
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

    private fun decorateGathering(
        instance: Instance,
        plan: QuestMapPlan,
        center: QuestMapPoint,
        gatheringNodes: List<QuestGatheringNode>,
    ) {
        val frame = routeFrame(plan, center)
        val palette = scenePalette(plan.style)
        val nodes = gatheringNodes.filter { it.contentPosition == center }
        val node = nodes.firstOrNull() ?: error("Gathering scene has no resource node at $center")

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
        nodes.forEach { gatheringNode ->
            QuestMapStructureAssets.placeGatheringObject(instance, plan, gatheringNode)
        }
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
