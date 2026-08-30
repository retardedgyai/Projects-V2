package dev.projects.server.questmap

import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** One concrete 224x224 quest map: authored rhythm over deterministic procedural terrain. */
internal object VerdantRoadQuestPlanner {
    const val MAP_SIZE = 224
    const val PLAYABLE_BORDER = 10
    private const val ROAD_BLEND_RADIUS = 8
    private const val BASE_GROUND_Y = 52

    fun generate(seed: Long): QuestMapPlan {
        val random = Random(seed)
        val style = QuestTerrainStyle.entries[Math.floorMod(seed, QuestTerrainStyle.entries.size.toLong()).toInt()]
        val routePlan = routeControls(seed, random)
        val mainRoute = curvedRoute(routePlan.controls)
        val rawHeights = rawTerrain(seed, style, mainRoute)
        val routeHeights = smoothRouteHeights(mainRoute.map { rawHeights[index(it)] }).also { heights ->
            if (style == QuestTerrainStyle.SALTMARSH) {
                heights.indices.forEach { routeIndex ->
                    heights[routeIndex] = maxOf(heights[routeIndex], QUEST_WATER_LEVEL + 2)
                }
            }
        }
        val trails = linkedSetOf<QuestMapPoint>()
        trails += mainRoute
        val contents = contentPlan(mainRoute, random, trails)
        val nearestRoad = nearestRoad(mainRoute, trails)
        val nearestMainRoad = nearestRoad(mainRoute, mainRoute.toSet())
        val finalHeights = shapeTerrain(seed, style, rawHeights, mainRoute, routeHeights, contents, nearestRoad)

        return QuestMapQualityGate.requireAccepted(
            QuestMapPlan(
                seed = seed,
                size = MAP_SIZE,
                playableBorder = PLAYABLE_BORDER,
                style = style,
                routeLayout = routePlan.layout,
                mainRoute = mainRoute,
                trails = trails,
                contents = contents,
                heights = finalHeights,
                roadDistanceSquared = nearestRoad.distanceSquared,
                mainRoadDistanceSquared = nearestMainRoad.distanceSquared,
            ),
        )
    }

    private data class RouteControls(
        val layout: QuestRouteLayout,
        val controls: List<QuestMapPoint>,
    )

    private fun routeControls(seed: Long, random: Random): RouteControls {
        val layout = QuestRouteLayout.entries[Math.floorMod(seed xor 0x524F555445L, QuestRouteLayout.entries.size.toLong()).toInt()]
        fun jitter(value: Int, radius: Int = 8): Int = value + random.nextInt(radius * 2 + 1) - radius
        val base = when (layout) {
            QuestRouteLayout.MEANDER -> {
                val startsHigh = random.nextBoolean()
                fun band(high: Boolean): Int = if (high) jitter(170, 14) else jitter(54, 14)
                listOf(
                    QuestMapPoint(22, jitter(112, 22)),
                    QuestMapPoint(54, band(startsHigh)),
                    QuestMapPoint(91, band(!startsHigh)),
                    QuestMapPoint(130, band(startsHigh)),
                    QuestMapPoint(169, band(!startsHigh)),
                    QuestMapPoint(202, jitter(112, 22)),
                )
            }
            QuestRouteLayout.RIDGE_PASS -> listOf(
                QuestMapPoint(22, jitter(38)),
                QuestMapPoint(jitter(54), jitter(66)),
                QuestMapPoint(jitter(86), jitter(126)),
                QuestMapPoint(jitter(122), jitter(178)),
                QuestMapPoint(jitter(166), jitter(190)),
                QuestMapPoint(202, jitter(154, 12)),
            )
            QuestRouteLayout.HORSESHOE -> listOf(
                QuestMapPoint(jitter(26, 4), jitter(42, 8)),
                QuestMapPoint(jitter(78), jitter(30, 6)),
                QuestMapPoint(jitter(158), jitter(48)),
                QuestMapPoint(jitter(196, 5), jitter(108)),
                QuestMapPoint(jitter(174), jitter(176)),
                QuestMapPoint(jitter(110), jitter(198, 5)),
                QuestMapPoint(jitter(38, 5), jitter(182, 8)),
            )
            QuestRouteLayout.DIAGONAL -> listOf(
                QuestMapPoint(jitter(24, 5), jitter(24, 5)),
                QuestMapPoint(jitter(48), jitter(88)),
                QuestMapPoint(jitter(94), jitter(65)),
                QuestMapPoint(jitter(122), jitter(135)),
                QuestMapPoint(jitter(176), jitter(150)),
                QuestMapPoint(jitter(200, 5), jitter(200, 5)),
            )
        }
        val symmetry = Math.floorMod(seed ushr 11, 8L).toInt()
        return RouteControls(layout, base.map { transform(it, symmetry) })
    }

    private fun transform(point: QuestMapPoint, symmetry: Int): QuestMapPoint {
        val maximum = MAP_SIZE - 1
        return when (symmetry) {
            0 -> point
            1 -> QuestMapPoint(maximum - point.z, point.x)
            2 -> QuestMapPoint(maximum - point.x, maximum - point.z)
            3 -> QuestMapPoint(point.z, maximum - point.x)
            4 -> QuestMapPoint(maximum - point.x, point.z)
            5 -> QuestMapPoint(point.x, maximum - point.z)
            6 -> QuestMapPoint(point.z, point.x)
            else -> QuestMapPoint(maximum - point.z, maximum - point.x)
        }
    }

    private fun curvedRoute(controls: List<QuestMapPoint>): List<QuestMapPoint> {
        val samples = mutableListOf(controls.first())
        for (index in 0 until controls.lastIndex) {
            val p0 = controls[(index - 1).coerceAtLeast(0)]
            val p1 = controls[index]
            val p2 = controls[index + 1]
            val p3 = controls[(index + 2).coerceAtMost(controls.lastIndex)]
            for (step in 1..16) {
                val t = step / 16.0
                fun interpolate(a: Int, b: Int, c: Int, d: Int): Int {
                    val t2 = t * t
                    val t3 = t2 * t
                    return (0.5 * (
                        2.0 * b +
                            (-a + c) * t +
                            (2.0 * a - 5.0 * b + 4.0 * c - d) * t2 +
                            (-a + 3.0 * b - 3.0 * c + d) * t3
                        )).roundToInt()
                }
                val sample = QuestMapPoint(
                    interpolate(p0.x, p1.x, p2.x, p3.x).coerceIn(PLAYABLE_BORDER + 2, MAP_SIZE - PLAYABLE_BORDER - 3),
                    interpolate(p0.z, p1.z, p2.z, p3.z).coerceIn(PLAYABLE_BORDER + 2, MAP_SIZE - PLAYABLE_BORDER - 3),
                )
                samples += bresenham(samples.last(), sample).drop(1)
            }
        }
        return samples.distinctConsecutive()
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

    private data class Landform(
        val center: QuestMapPoint,
        val radiusX: Double,
        val radiusZ: Double,
        val height: Double,
        val angle: Double,
    )

    private fun rawTerrain(seed: Long, style: QuestTerrainStyle, route: List<QuestMapPoint>): IntArray {
        val landforms = authoredLandforms(seed, style, route)
        return IntArray(MAP_SIZE * MAP_SIZE) { offset ->
            val x = offset % MAP_SIZE
            val z = offset / MAP_SIZE
            val broad = valueNoise(seed xor 0x51A4C3L, x / 92.0, z / 92.0)
            val medium = valueNoise(seed xor 0x137F29L, x / 34.0, z / 34.0)
            val detail = valueNoise(seed xor 0x6C8E9FL, x / 13.0, z / 13.0)
            val ridge = 1.0 - abs(valueNoise(seed xor 0x5249444745L, x / 48.0, z / 48.0))
            val styleOffset = when (style) {
                QuestTerrainStyle.VERDANT -> 0.0
                QuestTerrainStyle.HIGHLANDS -> 6.0
                QuestTerrainStyle.SALTMARSH -> -3.0
            }
            val broadAmplitude = when (style) {
                QuestTerrainStyle.VERDANT -> 12.0
                QuestTerrainStyle.HIGHLANDS -> 18.0
                QuestTerrainStyle.SALTMARSH -> 8.0
            }
            val mediumAmplitude = when (style) {
                QuestTerrainStyle.VERDANT -> 7.0
                QuestTerrainStyle.HIGHLANDS -> 10.0
                QuestTerrainStyle.SALTMARSH -> 4.0
            }
            val ridgeAmplitude = when (style) {
                QuestTerrainStyle.VERDANT -> 7.0
                QuestTerrainStyle.HIGHLANDS -> 13.0
                QuestTerrainStyle.SALTMARSH -> 4.0
            }
            val detailAmplitude = if (style == QuestTerrainStyle.HIGHLANDS) 2.5 else 1.8
            val authoredHeight = landforms.sumOf { landform -> landform.heightAt(x, z) }
            (
                BASE_GROUND_Y + styleOffset + broad * broadAmplitude + medium * mediumAmplitude +
                    (ridge - 0.45) * ridgeAmplitude + detail * detailAmplitude + authoredHeight
                ).roundToInt()
        }
    }

    private fun authoredLandforms(
        seed: Long,
        style: QuestTerrainStyle,
        route: List<QuestMapPoint>,
    ): List<Landform> {
        val random = Random(seed xor 0x4C414E44464F524DL)
        val start = route.first()
        val boss = route.last()
        val sightBlocker = QuestMapPoint(
            (start.x * 0.56 + boss.x * 0.44).roundToInt(),
            (start.z * 0.56 + boss.z * 0.44).roundToInt(),
        )
        val blockerHeight = when (style) {
            QuestTerrainStyle.VERDANT -> 24.0
            QuestTerrainStyle.HIGHLANDS -> 32.0
            QuestTerrainStyle.SALTMARSH -> 20.0
        }
        val result = mutableListOf(
            Landform(
                center = sightBlocker,
                radiusX = 30.0 + random.nextInt(10),
                radiusZ = 24.0 + random.nextInt(10),
                height = blockerHeight,
                angle = random.nextDouble() * Math.PI,
            ),
        )
        listOf(0.22, 0.52, 0.76).forEachIndexed { ordinal, fraction ->
            val routeIndex = (route.lastIndex * fraction).roundToInt()
            val point = route[routeIndex]
            val before = route[(routeIndex - 8).coerceAtLeast(0)]
            val after = route[(routeIndex + 8).coerceAtMost(route.lastIndex)]
            val dx = after.x - before.x
            val dz = after.z - before.z
            val length = maxOf(1, abs(dx) + abs(dz))
            val side = if (ordinal % 2 == 0) 1 else -1
            val offset = 24 + random.nextInt(18)
            val center = QuestMapPoint(
                (point.x + (-dz * side * offset) / length).coerceIn(PLAYABLE_BORDER, MAP_SIZE - PLAYABLE_BORDER - 1),
                (point.z + (dx * side * offset) / length).coerceIn(PLAYABLE_BORDER, MAP_SIZE - PLAYABLE_BORDER - 1),
            )
            result += Landform(
                center = center,
                radiusX = 22.0 + random.nextInt(15),
                radiusZ = 18.0 + random.nextInt(12),
                height = (if (style == QuestTerrainStyle.HIGHLANDS) 18 else 12).toDouble() + random.nextInt(7),
                angle = random.nextDouble() * Math.PI,
            )
        }
        listOf(0.36, 0.68).forEachIndexed { ordinal, fraction ->
            val routeIndex = (route.lastIndex * fraction).roundToInt()
            val point = route[routeIndex]
            val before = route[(routeIndex - 8).coerceAtLeast(0)]
            val after = route[(routeIndex + 8).coerceAtMost(route.lastIndex)]
            val dx = after.x - before.x
            val dz = after.z - before.z
            val length = maxOf(1, abs(dx) + abs(dz))
            val side = if (ordinal % 2 == 0) -1 else 1
            val offset = 40 + random.nextInt(16)
            result += Landform(
                center = QuestMapPoint(
                    (point.x + (-dz * side * offset) / length).coerceIn(36, MAP_SIZE - 37),
                    (point.z + (dx * side * offset) / length).coerceIn(36, MAP_SIZE - 37),
                ),
                radiusX = 22.0 + random.nextInt(16),
                radiusZ = 14.0 + random.nextInt(12),
                height = if (style == QuestTerrainStyle.SALTMARSH) -6.0 else -11.0 - random.nextInt(5),
                angle = random.nextDouble() * Math.PI,
            )
        }
        return result
    }

    private fun Landform.heightAt(x: Int, z: Int): Double {
        val dx = x - center.x
        val dz = z - center.z
        val rotatedX = dx * cos(angle) - dz * sin(angle)
        val rotatedZ = dx * sin(angle) + dz * cos(angle)
        val distance = (rotatedX * rotatedX) / (radiusX * radiusX) + (rotatedZ * rotatedZ) / (radiusZ * radiusZ)
        if (distance >= 1.0) return 0.0
        val influence = 1.0 - distance
        return height * influence * influence
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
        seed: Long,
        style: QuestTerrainStyle,
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

        if (style == QuestTerrainStyle.SALTMARSH) {
            carveSaltmarshWetlands(seed, shaped, contents, nearest)
        }
        shapeNaturalCoastline(seed, shaped)

        flattenCircle(shaped, contents.single { it.kind == QuestMapContentKind.START }.position, 8)
        flattenCircle(shaped, contents.single { it.kind == QuestMapContentKind.BOSS }.position, 14)
        contents.filter { it.kind == QuestMapContentKind.COMBAT }.forEach { flattenCircle(shaped, it.position, 7) }
        contents.filter { it.kind == QuestMapContentKind.GATHERING }.forEach { flattenCircle(shaped, it.position, 4) }
        contents.filter { it.kind == QuestMapContentKind.DISCOVERY }.forEach { flattenCircle(shaped, it.position, 4) }
        route.forEachIndexed { routeIndex, point -> shaped[index(point)] = routeHeights[routeIndex] }
        return shaped
    }

    private fun shapeNaturalCoastline(seed: Long, heights: IntArray) {
        for (z in 0 until MAP_SIZE) {
            for (x in 0 until MAP_SIZE) {
                val edgeDistance = minOf(x, z, MAP_SIZE - 1 - x, MAP_SIZE - 1 - z)
                if (edgeDistance >= PLAYABLE_BORDER) continue
                val offset = index(QuestMapPoint(x, z))
                val coastalHeight = QUEST_WATER_LEVEL + 1 +
                    (hashUnit(seed xor 0x434F415354L, x / 3, z / 3) * 1.5).roundToInt()
                val influence = edgeDistance.toDouble() / PLAYABLE_BORDER
                heights[offset] = (coastalHeight * (1.0 - influence) + heights[offset] * influence).roundToInt()
            }
        }
    }

    private fun carveSaltmarshWetlands(
        seed: Long,
        heights: IntArray,
        contents: List<QuestMapContent>,
        nearest: NearestRoad,
    ) {
        val random = Random(seed xor 0x5745544C414E44L)
        repeat(8) {
            val center = QuestMapPoint(15 + random.nextInt(MAP_SIZE - 30), 15 + random.nextInt(MAP_SIZE - 30))
            val radiusX = 11 + random.nextInt(8)
            val radiusZ = 8 + random.nextInt(6)
            for (z in (center.z - radiusZ).coerceAtLeast(1)..(center.z + radiusZ).coerceAtMost(MAP_SIZE - 2)) {
                for (x in (center.x - radiusX).coerceAtLeast(1)..(center.x + radiusX).coerceAtMost(MAP_SIZE - 2)) {
                    val dx = (x - center.x).toDouble() / radiusX
                    val dz = (z - center.z).toDouble() / radiusZ
                    if (dx * dx + dz * dz > 1.0) continue
                    val point = QuestMapPoint(x, z)
                    val offset = index(point)
                    if (nearest.distanceSquared[offset] <= 5 * 5) continue
                    if (contents.any { it.position.distanceSquared(point) < 8 * 8 }) continue
                    heights[offset] = minOf(heights[offset], QUEST_WATER_LEVEL - 1)
                }
            }
        }
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
