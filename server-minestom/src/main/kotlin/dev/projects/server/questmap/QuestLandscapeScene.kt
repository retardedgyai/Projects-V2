package dev.projects.server.questmap

import java.util.Random
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal enum class QuestLandscapeRole {
    RIDGE_GATE,
    SHELTERED_GROVE,
    HEATH_VISTA,
    HEADWATER,
    RUINED_TERRACE,
    ORE_CUT,
    RIFT_GARDEN,
}

/** A broad, authored composition that binds terrain, vegetation and landmarks together. */
internal data class QuestLandscapeScene(
    val id: Int,
    val role: QuestLandscapeRole,
    val center: QuestMapPoint,
    val approach: QuestMapPoint,
    val accessPath: List<QuestMapPoint>,
    val radius: Int,
    val rotation: Int,
    val mirrored: Boolean,
)

internal object QuestLandscapePlanner {
    const val TARGET_SCENE_COUNT = 9
    const val MINIMUM_SCENE_CENTER_SEPARATION = 40
    private const val MINIMUM_ROAD_OFFSET = 22

    private data class SceneCandidate(
        val center: QuestMapPoint,
        val approach: QuestMapPoint,
        val rotation: Int,
        val mirrored: Boolean,
    )

    fun plan(
        seed: Long,
        style: QuestTerrainStyle,
        mainRoute: List<QuestMapPoint>,
        contents: List<QuestMapContent>,
        size: Int,
        playableBorder: Int,
        heightAt: (QuestMapPoint) -> Int,
    ): List<QuestLandscapeScene> {
        require(mainRoute.size >= TARGET_SCENE_COUNT * 12)
        val random = Random(seed xor 0x4C414E4453434150L)
        val roles = rolesFor(style)
        val scenes = mutableListOf<QuestLandscapeScene>()

        repeat(TARGET_SCENE_COUNT) { ordinal ->
            val role = roles[ordinal % roles.size]
            val baseIndex = ((ordinal + 1).toDouble() / (TARGET_SCENE_COUNT + 1) * mainRoute.lastIndex).roundToInt()
            val segment = (mainRoute.size / (TARGET_SCENE_COUNT + 1)).coerceAtLeast(24)
            val radius = 29 + random.nextInt(8)
            val margin = playableBorder + radius + 2
            val routeIndices = listOf(
                baseIndex + random.nextInt(segment / 3 + 1) - segment / 6,
                baseIndex - segment / 3,
                baseIndex + segment / 3,
                baseIndex - segment / 2,
                baseIndex + segment / 2,
            ).map { it.coerceIn(12, mainRoute.lastIndex - 12) }.distinct()
            val candidateOptions = mutableListOf<SceneCandidate>()

            routeIndices.forEach { routeIndex ->
                val approach = mainRoute[routeIndex]
                val before = mainRoute[routeIndex - 10]
                val after = mainRoute[routeIndex + 10]
                val tangentX = after.x - before.x
                val tangentZ = after.z - before.z
                val length = sqrt((tangentX * tangentX + tangentZ * tangentZ).toDouble()).coerceAtLeast(1.0)
                val forwardX = tangentX / length
                val forwardZ = tangentZ / length
                val sideX = -forwardZ
                val sideZ = forwardX
                val rotation = cardinalRotation(tangentX, tangentZ)
                repeat(10) { attempt ->
                    val positiveSide = (ordinal + routeIndex + attempt) % 2 == 0
                    val sideSign = if (positiveSide) 1.0 else -1.0
                    val roadOffset = 24 + random.nextInt(13)
                    val forwardOffset = random.nextInt(23) - 11
                    val point = QuestMapPoint(
                        (approach.x + sideX * roadOffset * sideSign + forwardX * forwardOffset)
                            .roundToInt().coerceIn(margin, size - margin - 1),
                        (approach.z + sideZ * roadOffset * sideSign + forwardZ * forwardOffset)
                            .roundToInt().coerceIn(margin, size - margin - 1),
                    )
                    if (point.distanceSquared(approach) >= MINIMUM_ROAD_OFFSET * MINIMUM_ROAD_OFFSET) {
                        candidateOptions += SceneCandidate(point, approach, rotation, !positiveSide)
                    }
                }
                for (roadOffset in 22..30 step 2) {
                    for (positiveSide in listOf(true, false)) {
                        val sideSign = if (positiveSide) 1.0 else -1.0
                        val forwardOffset = 0
                        val point = QuestMapPoint(
                            (approach.x + sideX * roadOffset * sideSign + forwardX * forwardOffset)
                                .roundToInt().coerceIn(margin, size - margin - 1),
                            (approach.z + sideZ * roadOffset * sideSign + forwardZ * forwardOffset)
                                .roundToInt().coerceIn(margin, size - margin - 1),
                        )
                        if (point.distanceSquared(approach) >= MINIMUM_ROAD_OFFSET * MINIMUM_ROAD_OFFSET) {
                            candidateOptions += SceneCandidate(point, approach, rotation, !positiveSide)
                        }
                    }
                }
            }

            val distinctOptions = candidateOptions.distinct().filter { candidate ->
                scenes.all {
                    it.center.distanceSquared(candidate.center) >=
                        MINIMUM_SCENE_CENTER_SEPARATION * MINIMUM_SCENE_CENTER_SEPARATION
                }
            }
            val walkableOptions = distinctOptions.filter { candidate ->
                maximumApproachStep(candidate.approach, candidate.center, heightAt) <= 2
            }
            val directSelection = walkableOptions.maxByOrNull { candidate ->
                candidateScore(role, candidate.center, candidate.approach, scenes, contents, heightAt)
            }
            val selectedWithPath = if (directSelection != null) {
                directSelection to rasterLine(directSelection.approach, directSelection.center)
            } else {
                distinctOptions
                    .sortedByDescending { candidate ->
                        candidateScore(role, candidate.center, candidate.approach, scenes, contents, heightAt)
                    }
                    .asSequence()
                    .mapNotNull { candidate ->
                        findAccessPath(candidate.approach, candidate.center, size, heightAt)?.let { path -> candidate to path }
                    }
                    .firstOrNull()
                    ?: error("Unable to place landscape scene $ordinal for seed $seed")
            }
            val selected = selectedWithPath.first
            scenes += QuestLandscapeScene(
                id = ordinal,
                role = role,
                center = selected.center,
                approach = selected.approach,
                accessPath = selectedWithPath.second,
                radius = radius,
                rotation = selected.rotation,
                mirrored = selected.mirrored,
            )
        }
        return scenes
    }

    private fun rolesFor(style: QuestTerrainStyle): List<QuestLandscapeRole> = when (style) {
        QuestTerrainStyle.VERDANT -> listOf(
            QuestLandscapeRole.SHELTERED_GROVE,
            QuestLandscapeRole.HEADWATER,
            QuestLandscapeRole.RUINED_TERRACE,
            QuestLandscapeRole.HEATH_VISTA,
            QuestLandscapeRole.RIDGE_GATE,
        )
        QuestTerrainStyle.HIGHLANDS, QuestTerrainStyle.CLIFFLANDS -> listOf(
            QuestLandscapeRole.RIDGE_GATE,
            QuestLandscapeRole.HEATH_VISTA,
            QuestLandscapeRole.SHELTERED_GROVE,
            QuestLandscapeRole.ORE_CUT,
            QuestLandscapeRole.RUINED_TERRACE,
        )
        QuestTerrainStyle.SALTMARSH -> listOf(
            QuestLandscapeRole.HEADWATER,
            QuestLandscapeRole.SHELTERED_GROVE,
            QuestLandscapeRole.RUINED_TERRACE,
            QuestLandscapeRole.HEATH_VISTA,
            QuestLandscapeRole.RIDGE_GATE,
        )
        QuestTerrainStyle.SAKURA_GROVE -> listOf(
            QuestLandscapeRole.SHELTERED_GROVE,
            QuestLandscapeRole.HEADWATER,
            QuestLandscapeRole.RUINED_TERRACE,
            QuestLandscapeRole.HEATH_VISTA,
            QuestLandscapeRole.RIDGE_GATE,
        )
        QuestTerrainStyle.INFERNAL -> listOf(
            QuestLandscapeRole.RIFT_GARDEN,
            QuestLandscapeRole.ORE_CUT,
            QuestLandscapeRole.RIDGE_GATE,
            QuestLandscapeRole.RUINED_TERRACE,
            QuestLandscapeRole.HEATH_VISTA,
        )
    }

    private fun candidateScore(
        role: QuestLandscapeRole,
        point: QuestMapPoint,
        approach: QuestMapPoint,
        scenes: List<QuestLandscapeScene>,
        contents: List<QuestMapContent>,
        heightAt: (QuestMapPoint) -> Int,
    ): Double {
        val sampleRadius = 11
        val sampledHeights = listOf(
            point,
            QuestMapPoint(point.x - sampleRadius, point.z),
            QuestMapPoint(point.x + sampleRadius, point.z),
            QuestMapPoint(point.x, point.z - sampleRadius),
            QuestMapPoint(point.x, point.z + sampleRadius),
        ).map(heightAt)
        val relief = sampledHeights.max() - sampledHeights.min()
        val terrainSuitability = when (role) {
            QuestLandscapeRole.RIDGE_GATE, QuestLandscapeRole.ORE_CUT -> 24.0 - abs(relief - 7) * 2.2
            QuestLandscapeRole.HEADWATER -> 22.0 - abs(sampledHeights.first() - (QUEST_WATER_LEVEL + 5)) * 0.7 - relief * 1.3
            else -> 24.0 - relief * 2.6
        }
        val nearestScene = scenes.minOfOrNull { sqrt(it.center.distanceSquared(point).toDouble()) } ?: 80.0
        val spacing = nearestScene.coerceAtMost(80.0) * 0.8
        val nearestContent = contents
            .filter { it.kind !in setOf(QuestMapContentKind.START, QuestMapContentKind.BOSS) }
            .minOfOrNull { sqrt(it.position.distanceSquared(point).toDouble()) }
            ?: 80.0
        val contentClearance = nearestContent.coerceAtMost(45.0) * 0.35
        val approachHeights = rasterLine(approach, point).map(heightAt)
        val maximumApproachStep = approachHeights.zipWithNext().maxOfOrNull { (from, to) -> abs(from - to) } ?: 0
        val roughApproachSteps = approachHeights.zipWithNext().count { (from, to) -> abs(from - to) > 1 }
        val approachSuitability = 35.0 - maximumApproachStep * 15.0 - roughApproachSteps * 2.5
        return terrainSuitability + spacing + contentClearance + approachSuitability
    }

    private fun maximumApproachStep(
        approach: QuestMapPoint,
        point: QuestMapPoint,
        heightAt: (QuestMapPoint) -> Int,
    ): Int = rasterLine(approach, point)
        .map(heightAt)
        .zipWithNext()
        .maxOfOrNull { (from, to) -> abs(from - to) }
        ?: 0

    private data class PathNode(
        val point: QuestMapPoint,
        val cost: Int,
        val estimatedTotal: Int,
    )

    private fun findAccessPath(
        from: QuestMapPoint,
        to: QuestMapPoint,
        size: Int,
        heightAt: (QuestMapPoint) -> Int,
    ): List<QuestMapPoint>? {
        val padding = 18
        val minX = (minOf(from.x, to.x) - padding).coerceAtLeast(1)
        val maxX = (maxOf(from.x, to.x) + padding).coerceAtMost(size - 2)
        val minZ = (minOf(from.z, to.z) - padding).coerceAtLeast(1)
        val maxZ = (maxOf(from.z, to.z) + padding).coerceAtMost(size - 2)
        val frontier = PriorityQueue<PathNode>(compareBy(PathNode::estimatedTotal).thenBy(PathNode::cost))
        val costs = mutableMapOf(from to 0)
        val previous = mutableMapOf<QuestMapPoint, QuestMapPoint>()
        frontier += PathNode(from, 0, heuristic(from, to))
        val directions = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, -1 to 1, 1 to -1, 1 to 1)
        var visited = 0
        while (frontier.isNotEmpty() && visited++ < 12_000) {
            val node = frontier.remove()
            if (node.cost != costs[node.point]) continue
            if (node.point == to) {
                val path = mutableListOf(to)
                var cursor = to
                while (cursor != from) {
                    cursor = previous[cursor] ?: return null
                    path += cursor
                }
                return path.asReversed()
            }
            directions.forEach { (dx, dz) ->
                val next = QuestMapPoint(node.point.x + dx, node.point.z + dz)
                if (next.x !in minX..maxX || next.z !in minZ..maxZ) return@forEach
                val heightStep = abs(heightAt(node.point) - heightAt(next))
                if (heightStep > 2) return@forEach
                val movement = if (dx == 0 || dz == 0) 10 else 14
                val nextCost = node.cost + movement + heightStep * 8
                if (nextCost >= (costs[next] ?: Int.MAX_VALUE)) return@forEach
                costs[next] = nextCost
                previous[next] = node.point
                frontier += PathNode(next, nextCost, nextCost + heuristic(next, to))
            }
        }
        return null
    }

    private fun heuristic(from: QuestMapPoint, to: QuestMapPoint): Int =
        (abs(from.x - to.x) + abs(from.z - to.z)) * 8

    private fun cardinalRotation(dx: Int, dz: Int): Int = when {
        abs(dx) >= abs(dz) && dx >= 0 -> 0
        abs(dx) < abs(dz) && dz >= 0 -> 1
        abs(dx) >= abs(dz) -> 2
        else -> 3
    }

    private fun rasterLine(from: QuestMapPoint, to: QuestMapPoint): List<QuestMapPoint> {
        val points = mutableListOf<QuestMapPoint>()
        var x = from.x
        var z = from.z
        val dx = abs(to.x - from.x)
        val dz = abs(to.z - from.z)
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
}
