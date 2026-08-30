package dev.projects.server.questmap

import java.util.Random
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** One concrete 160x160 quest map: authored rhythm over deterministic procedural terrain. */
internal object VerdantRoadQuestPlanner {
    const val MAP_SIZE = 160
    const val PLAYABLE_BORDER = 8
    private const val ROAD_BLEND_RADIUS = 6
    private const val BASE_GROUND_Y = 46

    fun generate(seed: Long): QuestMapPlan {
        val random = Random(seed)
        val style = QuestTerrainStyle.entries[Math.floorMod(seed, QuestTerrainStyle.entries.size.toLong()).toInt()]
        val controls = routeControls(random)
        val mainRoute = controls.zipWithNext()
            .flatMapIndexed { index, pair -> bresenham(pair.first, pair.second).drop(if (index == 0) 0 else 1) }
            .distinctConsecutive()
        val rawHeights = rawTerrain(seed, style)
        val routeHeights = smoothRouteHeights(mainRoute.map { rawHeights[index(it)] })
        val trails = linkedSetOf<QuestMapPoint>()
        trails += mainRoute
        val contents = contentPlan(mainRoute, random, trails)
        val nearestRoad = nearestRoad(mainRoute, trails)
        val finalHeights = shapeTerrain(rawHeights, mainRoute, routeHeights, contents, nearestRoad)

        return QuestMapQualityGate.requireAccepted(
            QuestMapPlan(
                seed = seed,
                size = MAP_SIZE,
                playableBorder = PLAYABLE_BORDER,
                style = style,
                mainRoute = mainRoute,
                trails = trails,
                contents = contents,
                heights = finalHeights,
                roadDistanceSquared = nearestRoad.distanceSquared,
            ),
        )
    }

    private fun routeControls(random: Random): List<QuestMapPoint> {
        val lowBand = 24..53
        val highBand = 106..135
        val startHigh = random.nextBoolean()
        fun band(high: Boolean): IntRange = if (high) highBand else lowBand
        fun sample(range: IntRange): Int = range.first + random.nextInt(range.last - range.first + 1)

        return listOf(
            QuestMapPoint(18, sample(52..108)),
            QuestMapPoint(42, sample(band(startHigh))),
            QuestMapPoint(69, sample(band(!startHigh))),
            QuestMapPoint(97, sample(band(startHigh))),
            QuestMapPoint(124, sample(band(!startHigh))),
            QuestMapPoint(141, sample(52..108)),
        )
    }

    private fun contentPlan(
        route: List<QuestMapPoint>,
        random: Random,
        trails: MutableSet<QuestMapPoint>,
    ): List<QuestMapContent> {
        val result = mutableListOf<QuestMapContent>()
        result += QuestMapContent(QuestMapContentKind.START, route.first(), 0, optional = false)

        listOf(0.20, 0.46, 0.72).forEach { fraction ->
            val routeIndex = (route.lastIndex * fraction).roundToInt()
            result += QuestMapContent(QuestMapContentKind.COMBAT, route[routeIndex], routeIndex, optional = false)
        }

        listOf(0.12, 0.34, 0.58, 0.82).forEachIndexed { ordinal, fraction ->
            val routeIndex = (route.lastIndex * fraction).roundToInt()
            val anchor = route[routeIndex]
            val branch = uniqueBranch(
                route,
                routeIndex,
                anchor,
                preferredSide = if (ordinal % 2 == 0) 1 else -1,
                minimumDistance = 10 + random.nextInt(6),
                occupied = result.map { it.position },
            )
            trails += branch
            result += QuestMapContent(QuestMapContentKind.GATHERING, branch.last(), routeIndex, optional = true)
        }

        listOf(0.29, 0.66).forEachIndexed { ordinal, fraction ->
            val routeIndex = (route.lastIndex * fraction).roundToInt()
            val anchor = route[routeIndex]
            val branch = uniqueBranch(
                route,
                routeIndex,
                anchor,
                preferredSide = if (ordinal % 2 == 0) -1 else 1,
                minimumDistance = 15 + random.nextInt(5),
                occupied = result.map { it.position },
            )
            trails += branch
            result += QuestMapContent(QuestMapContentKind.DISCOVERY, branch.last(), routeIndex, optional = true)
        }

        val desiredForeshadowIndex = (route.lastIndex * 0.90).roundToInt()
        val bossForeshadowIndex = (0..24).asSequence()
            .flatMap { offset -> if (offset == 0) sequenceOf(0) else sequenceOf(-offset, offset) }
            .map { offset -> (desiredForeshadowIndex + offset).coerceIn(0, route.lastIndex) }
            .first { candidate -> result.all { it.position.distanceSquared(route[candidate]) >= 8 * 8 } }
        result += QuestMapContent(
            QuestMapContentKind.DISCOVERY,
            route[bossForeshadowIndex],
            bossForeshadowIndex,
            optional = false,
        )

        result += QuestMapContent(QuestMapContentKind.BOSS, route.last(), route.lastIndex, optional = false)
        return result
    }

    private fun uniqueBranch(
        route: List<QuestMapPoint>,
        routeIndex: Int,
        anchor: QuestMapPoint,
        preferredSide: Int,
        minimumDistance: Int,
        occupied: List<QuestMapPoint>,
    ): List<QuestMapPoint> {
        for (extraDistance in 0..12 step 2) {
            for (side in listOf(preferredSide, -preferredSide)) {
                val branch = branchFrom(route, routeIndex, anchor, side, minimumDistance + extraDistance)
                if (branch.size >= 8 && occupied.all { it.distanceSquared(branch.last()) >= 8 * 8 }) return branch
            }
        }
        error("Unable to place a distinct quest branch at route index $routeIndex")
    }

    private fun branchFrom(
        route: List<QuestMapPoint>,
        routeIndex: Int,
        anchor: QuestMapPoint,
        side: Int,
        distance: Int,
    ): List<QuestMapPoint> {
        val before = route[(routeIndex - 4).coerceAtLeast(0)]
        val after = route[(routeIndex + 4).coerceAtMost(route.lastIndex)]
        val dx = after.x - before.x
        val dz = after.z - before.z
        val length = maxOf(1, abs(dx) + abs(dz))
        val target = QuestMapPoint(
            x = (anchor.x + (-dz * side * distance) / length).coerceIn(PLAYABLE_BORDER + 2, MAP_SIZE - PLAYABLE_BORDER - 3),
            z = (anchor.z + (dx * side * distance) / length).coerceIn(PLAYABLE_BORDER + 2, MAP_SIZE - PLAYABLE_BORDER - 3),
        )
        return bresenham(anchor, target)
    }

    private fun rawTerrain(seed: Long, style: QuestTerrainStyle): IntArray = IntArray(MAP_SIZE * MAP_SIZE) { offset ->
        val x = offset % MAP_SIZE
        val z = offset / MAP_SIZE
        val broad = valueNoise(seed xor 0x51A4C3L, x / 74.0, z / 74.0)
        val medium = valueNoise(seed xor 0x137F29L, x / 29.0, z / 29.0)
        val detail = valueNoise(seed xor 0x6C8E9FL, x / 13.0, z / 13.0)
        val styleOffset = when (style) {
            QuestTerrainStyle.VERDANT -> 0.0
            QuestTerrainStyle.HIGHLANDS -> 4.0
            QuestTerrainStyle.SALTMARSH -> -3.0
        }
        val amplitude = when (style) {
            QuestTerrainStyle.VERDANT -> 9.0
            QuestTerrainStyle.HIGHLANDS -> 15.0
            QuestTerrainStyle.SALTMARSH -> 7.0
        }
        (BASE_GROUND_Y + styleOffset + broad * amplitude + medium * 5.0 + detail * 1.8).roundToInt()
    }

    private fun smoothRouteHeights(raw: List<Int>): IntArray {
        val averaged = IntArray(raw.size) { index ->
            val from = (index - 8).coerceAtLeast(0)
            val to = (index + 8).coerceAtMost(raw.lastIndex)
            (from..to).sumOf { raw[it] }.toDouble().div(to - from + 1).roundToInt()
        }
        for (index in 1..averaged.lastIndex) {
            averaged[index] = averaged[index].coerceIn(averaged[index - 1] - 1, averaged[index - 1] + 1)
        }
        for (index in averaged.lastIndex - 1 downTo 0) {
            averaged[index] = averaged[index].coerceIn(averaged[index + 1] - 1, averaged[index + 1] + 1)
        }
        return averaged
    }

    private data class NearestRoad(
        val routeIndex: IntArray,
        val distanceSquared: IntArray,
    )

    private fun nearestRoad(mainRoute: List<QuestMapPoint>, trails: Set<QuestMapPoint>): NearestRoad {
        val routeIndex = IntArray(MAP_SIZE * MAP_SIZE)
        val distanceSquared = IntArray(MAP_SIZE * MAP_SIZE) { Int.MAX_VALUE }
        val mainIndexByPoint = mainRoute.withIndex().associate { it.value to it.index }
        trails.forEach { road ->
            val nearestMainIndex = mainIndexByPoint[road] ?: mainRoute.indices.minBy { road.distanceSquared(mainRoute[it]) }
            for (z in (road.z - ROAD_BLEND_RADIUS).coerceAtLeast(0)..(road.z + ROAD_BLEND_RADIUS).coerceAtMost(MAP_SIZE - 1)) {
                for (x in (road.x - ROAD_BLEND_RADIUS).coerceAtLeast(0)..(road.x + ROAD_BLEND_RADIUS).coerceAtMost(MAP_SIZE - 1)) {
                    val point = QuestMapPoint(x, z)
                    val offset = index(point)
                    val candidateDistance = point.distanceSquared(road)
                    if (candidateDistance < distanceSquared[offset]) {
                        distanceSquared[offset] = candidateDistance
                        routeIndex[offset] = nearestMainIndex
                    }
                }
            }
        }
        return NearestRoad(routeIndex, distanceSquared)
    }

    private fun shapeTerrain(
        raw: IntArray,
        route: List<QuestMapPoint>,
        routeHeights: IntArray,
        contents: List<QuestMapContent>,
        nearest: NearestRoad,
    ): IntArray {
        val shaped = raw.copyOf()
        for (z in 0 until MAP_SIZE) {
            for (x in 0 until MAP_SIZE) {
                val offset = index(QuestMapPoint(x, z))
                val distance = kotlin.math.sqrt(nearest.distanceSquared[offset].toDouble())
                if (distance <= ROAD_BLEND_RADIUS) {
                    val roadHeight = routeHeights[nearest.routeIndex[offset]]
                    val influence = ((ROAD_BLEND_RADIUS - distance) / ROAD_BLEND_RADIUS).coerceIn(0.0, 1.0)
                    shaped[offset] = (raw[offset] * (1.0 - influence) + roadHeight * influence).roundToInt()
                }
            }
        }

        for (z in 0 until MAP_SIZE) {
            for (x in 0 until MAP_SIZE) {
                val edgeDistance = minOf(x, z, MAP_SIZE - 1 - x, MAP_SIZE - 1 - z)
                if (edgeDistance < PLAYABLE_BORDER) {
                    val rimHeight = BASE_GROUND_Y + 13 + (PLAYABLE_BORDER - edgeDistance) * 2
                    val offset = index(QuestMapPoint(x, z))
                    shaped[offset] = maxOf(shaped[offset], rimHeight)
                }
            }
        }

        flattenCircle(shaped, contents.single { it.kind == QuestMapContentKind.START }.position, 8)
        flattenCircle(shaped, contents.single { it.kind == QuestMapContentKind.BOSS }.position, 14)
        route.forEachIndexed { routeIndex, point -> shaped[index(point)] = routeHeights[routeIndex] }
        return shaped
    }

    private fun flattenCircle(heights: IntArray, center: QuestMapPoint, radius: Int) {
        val centerHeight = heights[index(center)]
        for (z in (center.z - radius).coerceAtLeast(0)..(center.z + radius).coerceAtMost(MAP_SIZE - 1)) {
            for (x in (center.x - radius).coerceAtLeast(0)..(center.x + radius).coerceAtMost(MAP_SIZE - 1)) {
                val point = QuestMapPoint(x, z)
                val distanceSquared = center.distanceSquared(point)
                if (distanceSquared <= radius * radius) heights[index(point)] = centerHeight
            }
        }
    }

    private fun valueNoise(seed: Long, x: Double, z: Double): Double {
        val x0 = floor(x).toInt()
        val z0 = floor(z).toInt()
        val tx = smooth(x - x0)
        val tz = smooth(z - z0)
        val a = hashUnit(seed, x0, z0)
        val b = hashUnit(seed, x0 + 1, z0)
        val c = hashUnit(seed, x0, z0 + 1)
        val d = hashUnit(seed, x0 + 1, z0 + 1)
        return lerp(lerp(a, b, tx), lerp(c, d, tx), tz)
    }

    private fun hashUnit(seed: Long, x: Int, z: Int): Double {
        var value = seed xor (x.toLong() * -7046029254386353131L) xor (z.toLong() * -4417276706812531889L)
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        value = value xor (value ushr 31)
        return ((value ushr 11).toDouble() / (1L shl 53).toDouble()) * 2.0 - 1.0
    }

    private fun smooth(value: Double): Double = value * value * (3.0 - 2.0 * value)

    private fun lerp(from: Double, to: Double, amount: Double): Double = from + (to - from) * amount

    private fun bresenham(from: QuestMapPoint, to: QuestMapPoint): List<QuestMapPoint> {
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
            val doubled = 2 * error
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

    private fun List<QuestMapPoint>.distinctConsecutive(): List<QuestMapPoint> =
        filterIndexed { index, point -> index == 0 || point != this[index - 1] }

    private fun index(point: QuestMapPoint): Int = point.z * MAP_SIZE + point.x
}
