package dev.projects.server.questmap

import java.util.Random
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** One concrete 224x224 quest map: authored rhythm over deterministic procedural terrain. */
internal object VerdantRoadQuestPlanner {
    const val MAP_SIZE = 224
    const val PLAYABLE_BORDER = 10
    private const val ROAD_BLEND_RADIUS = 24
    private const val BASE_GROUND_Y = 52

    fun generate(seed: Long): QuestMapPlan {
        val random = Random(seed)
        val style = QuestTerrainStyle.entries[Math.floorMod(seed, QuestTerrainStyle.entries.size.toLong()).toInt()]
        val terrainProfile = QuestTerrainProfile.entries[
            Math.floorMod(seed xor 0x50524F46494C45L, QuestTerrainProfile.entries.size.toLong()).toInt()
        ]
        val routePlan = routeControls(seed, random)
        val mainRoute = curvedRoute(routePlan.controls)
        val rawHeights = rawTerrain(seed, style, terrainProfile, mainRoute)
        val routeHeights = smoothRouteHeights(mainRoute, mainRoute.map { rawHeights[index(it)] }).also { heights ->
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
        val finalHeights = shapeTerrain(seed, style, rawHeights, mainRoute, routeHeights, contents, nearestRoad, nearestMainRoad)
        val ecology = ecology(seed, style, finalHeights)

        return QuestMapQualityGate.requireAccepted(
            QuestMapPlan(
                seed = seed,
                size = MAP_SIZE,
                playableBorder = PLAYABLE_BORDER,
                style = style,
                routeLayout = routePlan.layout,
                terrainProfile = terrainProfile,
                mainRoute = mainRoute,
                trails = trails,
                contents = contents,
                heights = finalHeights,
                roadDistanceSquared = nearestRoad.distanceSquared,
                mainRoadDistanceSquared = nearestMainRoad.distanceSquared,
                groundCovers = ecology.groundCovers,
                surfacePatches = ecology.surfacePatches,
                waterDistances = ecology.waterDistances,
                slopes = ecology.slopes,
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
                val center = jitter(112, 16)
                val broadTurn = 17 + random.nextInt(8)
                fun band(high: Boolean, scale: Double = 1.0): Int =
                    (center + (if (high) broadTurn else -broadTurn) * scale).roundToInt()
                listOf(
                    QuestMapPoint(22, jitter(center, 10)),
                    QuestMapPoint(58, jitter(band(startsHigh), 7)),
                    QuestMapPoint(96, jitter(band(!startsHigh, 0.55), 7)),
                    QuestMapPoint(136, jitter(band(startsHigh, 0.45), 7)),
                    QuestMapPoint(174, jitter(band(!startsHigh), 7)),
                    QuestMapPoint(202, jitter(center, 10)),
                )
            }
            QuestRouteLayout.RIDGE_PASS -> listOf(
                QuestMapPoint(22, jitter(46, 10)),
                QuestMapPoint(jitter(55, 6), jitter(94, 8)),
                QuestMapPoint(jitter(91, 7), jitter(91, 8)),
                QuestMapPoint(jitter(130, 7), jitter(154, 8)),
                QuestMapPoint(jitter(168, 6), jitter(143, 8)),
                QuestMapPoint(202, jitter(181, 10)),
            )
            QuestRouteLayout.HORSESHOE -> listOf(
                QuestMapPoint(jitter(24, 4), jitter(58, 8)),
                QuestMapPoint(jitter(52, 6), jitter(39, 6)),
                QuestMapPoint(jitter(92, 7), jitter(45, 7)),
                QuestMapPoint(jitter(133, 7), jitter(67, 8)),
                QuestMapPoint(jitter(168, 6), jitter(105, 8)),
                QuestMapPoint(jitter(190, 5), jitter(148, 8)),
                QuestMapPoint(jitter(200, 4), jitter(185, 7)),
            )
            QuestRouteLayout.DIAGONAL -> listOf(
                QuestMapPoint(jitter(24, 5), jitter(24, 5)),
                QuestMapPoint(jitter(54, 7), jitter(82, 8)),
                QuestMapPoint(jitter(91, 8), jitter(78, 8)),
                QuestMapPoint(jitter(127, 8), jitter(150, 8)),
                QuestMapPoint(jitter(166, 7), jitter(146, 8)),
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

    private fun rawTerrain(
        seed: Long,
        style: QuestTerrainStyle,
        profile: QuestTerrainProfile,
        route: List<QuestMapPoint>,
    ): IntArray {
        val landforms = authoredLandforms(seed, style, profile, route)
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
                QuestTerrainStyle.VERDANT -> 9.0
                QuestTerrainStyle.HIGHLANDS -> 10.0
                QuestTerrainStyle.SALTMARSH -> 6.0
            }
            val mediumAmplitude = when (style) {
                QuestTerrainStyle.VERDANT -> 5.0
                QuestTerrainStyle.HIGHLANDS -> 6.0
                QuestTerrainStyle.SALTMARSH -> 3.0
            }
            val ridgeAmplitude = when (style) {
                QuestTerrainStyle.VERDANT -> 4.0
                QuestTerrainStyle.HIGHLANDS -> 6.0
                QuestTerrainStyle.SALTMARSH -> 2.5
            }
            val detailAmplitude = if (style == QuestTerrainStyle.HIGHLANDS) 1.8 else 1.3
            val authoredHeight = landforms.sumOf { landform -> landform.heightAt(x, z) }
            val profileHeight = when (profile) {
                QuestTerrainProfile.ROLLING -> 0.0
                QuestTerrainProfile.RIDGED -> (ridge - 0.48) * 8.0
                QuestTerrainProfile.TERRACED -> {
                    val terraceSource = broad * broadAmplitude + medium * mediumAmplitude
                    (terraceSource / 4.0).roundToInt() * 4.0 - terraceSource
                }
                QuestTerrainProfile.BASIN -> {
                    val dx = (x - MAP_SIZE / 2.0) / (MAP_SIZE * 0.44)
                    val dz = (z - MAP_SIZE / 2.0) / (MAP_SIZE * 0.44)
                    val radial = (dx * dx + dz * dz).coerceIn(0.0, 1.0)
                    -10.0 * (1.0 - radial) + 5.0 * radial
                }
                QuestTerrainProfile.BROKEN_HILLS -> {
                    val cells = valueNoise(seed xor 0x42524F4B454EL, x / 23.0, z / 23.0)
                    maxOf(0.0, cells) * 8.0 - 1.0
                }
            }
            (
                BASE_GROUND_Y + styleOffset + broad * broadAmplitude + medium * mediumAmplitude +
                    (ridge - 0.45) * ridgeAmplitude + detail * detailAmplitude + authoredHeight + profileHeight
                ).roundToInt()
        }
    }

    private fun authoredLandforms(
        seed: Long,
        style: QuestTerrainStyle,
        profile: QuestTerrainProfile,
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
            QuestTerrainStyle.VERDANT -> 13.0
            QuestTerrainStyle.HIGHLANDS -> 16.0
            QuestTerrainStyle.SALTMARSH -> 10.0
        }
        val result = mutableListOf(
            Landform(
                center = sightBlocker,
                radiusX = 42.0 + random.nextInt(12),
                radiusZ = 32.0 + random.nextInt(12),
                height = blockerHeight,
                angle = random.nextDouble() * Math.PI,
            ),
        )
        val hillFractions = when (profile) {
            QuestTerrainProfile.ROLLING -> listOf(0.22, 0.56)
            QuestTerrainProfile.RIDGED -> listOf(0.18, 0.38, 0.62, 0.82)
            QuestTerrainProfile.TERRACED -> listOf(0.28, 0.72)
            QuestTerrainProfile.BASIN -> listOf(0.20, 0.50, 0.80)
            QuestTerrainProfile.BROKEN_HILLS -> listOf(0.18, 0.36, 0.58, 0.78)
        }
        hillFractions.forEachIndexed { ordinal, fraction ->
            val routeIndex = (route.lastIndex * fraction).roundToInt()
            val point = route[routeIndex]
            val before = route[(routeIndex - 8).coerceAtLeast(0)]
            val after = route[(routeIndex + 8).coerceAtMost(route.lastIndex)]
            val dx = after.x - before.x
            val dz = after.z - before.z
            val length = maxOf(1, abs(dx) + abs(dz))
            val side = if (ordinal % 2 == 0) 1 else -1
            val offset = 32 + random.nextInt(18)
            val center = QuestMapPoint(
                (point.x + (-dz * side * offset) / length).coerceIn(PLAYABLE_BORDER, MAP_SIZE - PLAYABLE_BORDER - 1),
                (point.z + (dx * side * offset) / length).coerceIn(PLAYABLE_BORDER, MAP_SIZE - PLAYABLE_BORDER - 1),
            )
            result += Landform(
                center = center,
                radiusX = 30.0 + random.nextInt(16),
                radiusZ = 24.0 + random.nextInt(13),
                height = (if (style == QuestTerrainStyle.HIGHLANDS) 8 else 7).toDouble() + random.nextInt(6),
                angle = random.nextDouble() * Math.PI,
            )
        }
        val valleyFractions = when (profile) {
            QuestTerrainProfile.BASIN -> listOf(0.42, 0.66)
            QuestTerrainProfile.ROLLING -> listOf(0.52)
            else -> emptyList()
        }
        valleyFractions.forEachIndexed { ordinal, fraction ->
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
                radiusX = 34.0 + random.nextInt(18),
                radiusZ = 26.0 + random.nextInt(14),
                height = if (style == QuestTerrainStyle.SALTMARSH) -4.0 else -7.0 - random.nextInt(4),
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

    private fun smoothRouteHeights(route: List<QuestMapPoint>, raw: List<Int>): IntArray {
        val averaged = IntArray(raw.size) { index ->
            val from = (index - 18).coerceAtLeast(0)
            val to = (index + 18).coerceAtMost(raw.lastIndex)
            (from..to).sumOf { raw[it] }.toDouble().div(to - from + 1).roundToInt()
        }
        fun gentle(source: IntArray): IntArray {
            val result = source.copyOf()
            var current = result.first()
            var lastStep = -3
            result[0] = current
            for (index in 1..result.lastIndex) {
                if (index - lastStep >= 3) {
                    when {
                        source[index] > current -> current++
                        source[index] < current -> current--
                    }
                    if (current != result[index - 1]) lastStep = index
                }
                result[index] = current
            }
            return result
        }
        val forward = gentle(averaged)
        val backward = gentle(averaged.reversedArray()).reversedArray()
        val centered = IntArray(averaged.size) { index -> ((forward[index] + backward[index]) / 2.0).roundToInt() }
        val result = gentle(centered)
        // Reconcile nearby hairpins as well as consecutive steps. Without this spatial pass,
        // two sections of the same road can sit five or six blocks apart vertically even
        // though a player sees and approaches both at once.
        repeat(10) {
            for (left in route.indices) {
                for (right in left + 1..route.lastIndex) {
                    if (route[left].distanceSquared(route[right]) > 6 * 6) continue
                    val difference = result[left] - result[right]
                    if (difference > 3) {
                        result[left]--
                        result[right]++
                    } else if (difference < -3) {
                        result[left]++
                        result[right]--
                    }
                }
            }
            for (index in 1..result.lastIndex) {
                result[index] = result[index].coerceIn(result[index - 1] - 1, result[index - 1] + 1)
            }
            for (index in result.lastIndex - 1 downTo 0) {
                result[index] = result[index].coerceIn(result[index + 1] - 1, result[index + 1] + 1)
            }
        }
        return result
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
        nearestMain: NearestRoad,
    ): IntArray {
        val shaped = raw.copyOf()
        for (z in 0 until MAP_SIZE) {
            for (x in 0 until MAP_SIZE) {
                val offset = index(QuestMapPoint(x, z))
                val distance = kotlin.math.sqrt(nearest.distanceSquared[offset].toDouble())
                if (distance <= ROAD_BLEND_RADIUS) {
                    val roadHeight = routeHeights[nearest.routeIndex[offset]]
                    val allowedRelief = when {
                        distance <= 8.0 -> 0
                        distance <= 14.0 -> 2
                        else -> 2 + ((distance - 14.0) / 2.5).roundToInt()
                    }
                    val constrained = raw[offset].coerceIn(roadHeight - allowedRelief, roadHeight + allowedRelief)
                    val influence = smooth(((ROAD_BLEND_RADIUS - distance) / ROAD_BLEND_RADIUS).coerceIn(0.0, 1.0))
                    shaped[offset] = (constrained * (1.0 - influence * 0.45) + roadHeight * influence * 0.45).roundToInt()
                }
            }
        }

        if (style == QuestTerrainStyle.SALTMARSH) {
            carveSaltmarshWetlands(seed, shaped, contents, nearest)
        }
        shapeNaturalCoastline(seed, shaped)

        flattenPlateau(shaped, contents.single { it.kind == QuestMapContentKind.START }.position, 7, 12)
        flattenPlateau(shaped, contents.single { it.kind == QuestMapContentKind.BOSS }.position, 13, 19)
        contents.filter { it.kind == QuestMapContentKind.COMBAT }.forEach { flattenPlateau(shaped, it.position, 6, 10) }
        contents.filter { it.kind == QuestMapContentKind.GATHERING }.forEach { flattenPlateau(shaped, it.position, 3, 7) }
        contents.filter { it.kind == QuestMapContentKind.DISCOVERY }.forEach { flattenPlateau(shaped, it.position, 3, 7) }
        route.forEachIndexed { routeIndex, point -> shaped[index(point)] = routeHeights[routeIndex] }
        relaxQuestCorridor(shaped, nearest)
        if (style == QuestTerrainStyle.SALTMARSH) {
            relaxSaltmarshBanks(shaped, passes = 12)
            relaxQuestCorridor(shaped, nearest)
            relaxSaltmarshBanks(shaped, passes = 6)
        }
        route.forEachIndexed { routeIndex, point -> shaped[index(point)] = routeHeights[routeIndex] }
        // Occlusion is the final terrain pass. Earlier corridor relaxation used to erase this
        // landform and expose the boss, while applying it here preserves both the gentle road
        // envelope and a broad, off-road sight blocker.
        ensureBossOcclusion(shaped, route.first(), route.last(), nearest, contents)
        if (style == QuestTerrainStyle.SALTMARSH) relaxSaltmarshBanks(shaped, passes = 12)
        capExplorationRelief(shaped, maximumRange = 48)
        protectRoadShoulders(shaped, route, routeHeights, nearestMain)
        ensureBossOcclusion(shaped, route.first(), route.last(), nearest, contents)
        return shaped
    }

    private fun capExplorationRelief(heights: IntArray, maximumRange: Int) {
        val ceiling = heights.min() + maximumRange
        heights.indices.forEach { offset -> heights[offset] = minOf(heights[offset], ceiling) }
    }

    private fun protectRoadShoulders(
        heights: IntArray,
        route: List<QuestMapPoint>,
        routeHeights: IntArray,
        nearestMain: NearestRoad,
    ) {
        heights.indices.forEach { offset ->
            if (nearestMain.distanceSquared[offset] > 6 * 6) return@forEach
            val roadHeight = routeHeights[nearestMain.routeIndex[offset]]
            heights[offset] = heights[offset].coerceIn(roadHeight - 1, roadHeight + 1)
        }
        route.forEachIndexed { routeIndex, point -> heights[index(point)] = routeHeights[routeIndex] }
    }

    /** Keeps the invited exploration space walkable while preserving stronger landforms outside it. */
    private fun relaxQuestCorridor(heights: IntArray, nearest: NearestRoad) {
        val corridor = heights.indices
            .filter { nearest.distanceSquared[it] <= ROAD_BLEND_RADIUS * ROAD_BLEND_RADIUS }
            .sortedBy { nearest.distanceSquared[it] }
        repeat(2) {
            corridor.forEach { offset ->
                if (nearest.distanceSquared[offset] <= 4 * 4) return@forEach
                val x = offset % MAP_SIZE
                val z = offset / MAP_SIZE
                val parent = buildList {
                    for (dz in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dz == 0) continue
                            val nextX = x + dx
                            val nextZ = z + dz
                            if (nextX !in 0 until MAP_SIZE || nextZ !in 0 until MAP_SIZE) continue
                            val nextOffset = index(QuestMapPoint(nextX, nextZ))
                            if (nearest.distanceSquared[nextOffset] < nearest.distanceSquared[offset]) add(nextOffset)
                        }
                    }
                }.minByOrNull { candidate -> abs(heights[candidate] - heights[offset]) } ?: return@forEach
                heights[offset] = heights[offset].coerceIn(heights[parent] - 1, heights[parent] + 1)
            }
        }
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
        data class WetlandBasin(
            val center: QuestMapPoint,
            val radiusX: Int,
            val radiusZ: Int,
            val angle: Double,
            val noiseSeed: Long,
        )
        val basins = List(5 + random.nextInt(4)) {
            WetlandBasin(
                center = QuestMapPoint(24 + random.nextInt(MAP_SIZE - 48), 24 + random.nextInt(MAP_SIZE - 48)),
                radiusX = 16 + random.nextInt(19),
                radiusZ = 11 + random.nextInt(16),
                angle = random.nextDouble() * Math.PI,
                noiseSeed = random.nextLong(),
            )
        }
        basins.forEach { basin ->
            val extent = maxOf(basin.radiusX, basin.radiusZ) + 8
            for (z in (basin.center.z - extent).coerceAtLeast(1)..(basin.center.z + extent).coerceAtMost(MAP_SIZE - 2)) {
                for (x in (basin.center.x - extent).coerceAtLeast(1)..(basin.center.x + extent).coerceAtMost(MAP_SIZE - 2)) {
                    val rawX = x - basin.center.x
                    val rawZ = z - basin.center.z
                    val rotatedX = rawX * cos(basin.angle) - rawZ * sin(basin.angle)
                    val rotatedZ = rawX * sin(basin.angle) + rawZ * cos(basin.angle)
                    val ellipseDistance = kotlin.math.sqrt(
                        (rotatedX * rotatedX) / (basin.radiusX * basin.radiusX) +
                            (rotatedZ * rotatedZ) / (basin.radiusZ * basin.radiusZ),
                    )
                    val shorelineWarp = valueNoise(basin.noiseSeed, x / 9.0, z / 9.0) * 0.22 +
                        valueNoise(basin.noiseSeed xor 0x53484F5245L, x / 21.0, z / 21.0) * 0.13
                    val interior = 1.0 - ellipseDistance + shorelineWarp
                    if (interior <= 0.0) continue
                    val point = QuestMapPoint(x, z)
                    val offset = index(point)
                    if (nearest.distanceSquared[offset] <= 12 * 12) continue
                    if (contents.any { it.position.distanceSquared(point) < 20 * 20 }) continue
                    val depthNoise = valueNoise(basin.noiseSeed xor 0x4445505448L, x / 7.0, z / 7.0)
                    val target = when {
                        interior > 0.70 -> QUEST_WATER_LEVEL - 3 - if (depthNoise > 0.35) 1 else 0
                        interior > 0.46 -> QUEST_WATER_LEVEL - 2
                        interior > 0.24 -> QUEST_WATER_LEVEL - 1
                        else -> QUEST_WATER_LEVEL + 1
                    }
                    val influence = smooth((interior * 1.55).coerceIn(0.0, 1.0))
                    heights[offset] = minOf(
                        heights[offset],
                        (heights[offset] * (1.0 - influence) + target * influence).roundToInt(),
                    )
                }
            }
        }
        basins.take(3).forEachIndexed { ordinal, basin ->
            val angle = basin.angle + (if (ordinal % 2 == 0) 1 else -1) * (0.55 + random.nextDouble())
            val endpoint = QuestMapPoint(
                (basin.center.x + cos(angle) * (basin.radiusX + 18)).roundToInt().coerceIn(2, MAP_SIZE - 3),
                (basin.center.z + sin(angle) * (basin.radiusZ + 18)).roundToInt().coerceIn(2, MAP_SIZE - 3),
            )
            bresenham(basin.center, endpoint).forEachIndexed { channelIndex, channelPoint ->
                val radius = 2 + if (channelIndex % 11 == 0) 1 else 0
                for (dz in -radius..radius) {
                    for (dx in -radius..radius) {
                        if (dx * dx + dz * dz > radius * radius) continue
                        val point = QuestMapPoint(channelPoint.x + dx, channelPoint.z + dz)
                        if (point.x !in 1 until MAP_SIZE - 1 || point.z !in 1 until MAP_SIZE - 1) continue
                        val offset = index(point)
                        if (nearest.distanceSquared[offset] <= 12 * 12) continue
                        if (contents.any { it.position.distanceSquared(point) < 20 * 20 }) continue
                        val target = if (abs(dx) + abs(dz) <= 1) QUEST_WATER_LEVEL - 1 else QUEST_WATER_LEVEL + 1
                        heights[offset] = minOf(heights[offset], target)
                    }
                }
            }
        }
        repeat(3) {
            val before = heights.copyOf()
            for (z in 2 until MAP_SIZE - 2) {
                for (x in 2 until MAP_SIZE - 2) {
                    val offset = index(QuestMapPoint(x, z))
                    if (nearest.distanceSquared[offset] <= 6 * 6) continue
                    if (before[offset] > QUEST_WATER_LEVEL + 10) continue
                    val lowestNeighbor = minOf(
                        before[index(QuestMapPoint(x - 1, z))],
                        before[index(QuestMapPoint(x + 1, z))],
                        before[index(QuestMapPoint(x, z - 1))],
                        before[index(QuestMapPoint(x, z + 1))],
                    )
                    if (before[offset] > lowestNeighbor + 2) heights[offset] = lowestNeighbor + 2
                }
            }
        }
        ensureSaltmarshWaterCoverage(seed, heights, contents, nearest)
        relaxSaltmarshBanks(heights, passes = 8)
    }

    private fun relaxSaltmarshBanks(heights: IntArray, passes: Int) {
        repeat(passes) {
            val before = heights.copyOf()
            for (z in 2 until MAP_SIZE - 2) {
                for (x in 2 until MAP_SIZE - 2) {
                    val point = QuestMapPoint(x, z)
                    val offset = index(point)
                    val neighbors = listOf(
                        before[index(QuestMapPoint(x - 1, z))],
                        before[index(QuestMapPoint(x + 1, z))],
                        before[index(QuestMapPoint(x, z - 1))],
                        before[index(QuestMapPoint(x, z + 1))],
                    )
                    val lowestNeighbor = neighbors.min()
                    if (before[offset] > lowestNeighbor + 2 && lowestNeighbor <= QUEST_WATER_LEVEL + 5) {
                        heights[offset] = lowestNeighbor + 2
                    }
                }
            }
        }
    }

    private fun ensureSaltmarshWaterCoverage(
        seed: Long,
        heights: IntArray,
        contents: List<QuestMapContent>,
        nearest: NearestRoad,
    ) {
        val target = (MAP_SIZE * MAP_SIZE * 0.055).roundToInt()
        var current = heights.count { it <= QUEST_WATER_LEVEL }
        if (current >= target) return
        data class Candidate(val point: QuestMapPoint, val score: Double)
        val queued = BooleanArray(MAP_SIZE * MAP_SIZE)
        val frontier = PriorityQueue<Candidate>(compareBy(Candidate::score))
        fun offer(point: QuestMapPoint) {
            if (point.x !in 2 until MAP_SIZE - 2 || point.z !in 2 until MAP_SIZE - 2) return
            val offset = index(point)
            if (queued[offset] || heights[offset] <= QUEST_WATER_LEVEL) return
            if (nearest.distanceSquared[offset] <= 12 * 12) return
            if (contents.any { it.position.distanceSquared(point) < 20 * 20 }) return
            queued[offset] = true
            val organicBias = hashUnit(seed xor 0x5741544552454447L, point.x, point.z) * 1.8
            frontier += Candidate(point, heights[offset] + organicBias)
        }
        for (z in 2 until MAP_SIZE - 2) {
            for (x in 2 until MAP_SIZE - 2) {
                val point = QuestMapPoint(x, z)
                if (heights[index(point)] > QUEST_WATER_LEVEL) continue
                offer(QuestMapPoint(x - 1, z))
                offer(QuestMapPoint(x + 1, z))
                offer(QuestMapPoint(x, z - 1))
                offer(QuestMapPoint(x, z + 1))
            }
        }
        while (current < target && frontier.isNotEmpty()) {
            val point = frontier.remove().point
            val offset = index(point)
            heights[offset] = QUEST_WATER_LEVEL - if (hashUnit(seed xor 0x44454550L, point.x, point.z) > 0.55) 1 else 0
            current++
            offer(QuestMapPoint(point.x - 1, point.z))
            offer(QuestMapPoint(point.x + 1, point.z))
            offer(QuestMapPoint(point.x, point.z - 1))
            offer(QuestMapPoint(point.x, point.z + 1))
        }
    }

    private fun ensureBossOcclusion(
        heights: IntArray,
        start: QuestMapPoint,
        boss: QuestMapPoint,
        nearest: NearestRoad,
        contents: List<QuestMapContent>,
    ) {
        val direct = bresenham(start, boss)
        val startEye = heights[index(start)] + 2.0
        val bossTop = heights[index(boss)] + 7.0
        fun samples(): Int = direct.drop(2).dropLast(2).countIndexed { sampleIndex, point ->
            val progress = (sampleIndex + 2).toDouble() / direct.lastIndex
            heights[index(point)] >= startEye + (bossTop - startEye) * progress
        }
        if (samples() >= 3) return
        val broadCandidates = direct.withIndex()
            .filter { (lineIndex, point) ->
                lineIndex in (direct.size * 0.20).roundToInt()..(direct.size * 0.78).roundToInt() &&
                    contents.none { it.position.distanceSquared(point) < 7 * 7 }
            }
        val offRoadCandidates = broadCandidates.filter { (_, point) ->
            nearest.distanceSquared[index(point)] > 20 * 20
        }
        val candidate = offRoadCandidates
            .filter { (_, point) -> hasWaterClearance(heights, point, 15) }
            .maxByOrNull { (_, point) -> nearest.distanceSquared[index(point)] }
            ?: offRoadCandidates.maxByOrNull { (_, point) -> nearest.distanceSquared[index(point)] }
            // Some moderate routes necessarily stay close to the direct sightline. In that
            // case make a broad, walkable pass instead of forcing a narrow mountain wall.
            ?: broadCandidates
                .filter { (_, point) -> hasWaterClearance(heights, point, 22) }
                .maxByOrNull { (_, point) -> nearest.distanceSquared[index(point)] }
            ?: broadCandidates.maxByOrNull { (_, point) -> nearest.distanceSquared[index(point)] }
            ?: return
        val progress = candidate.index.toDouble() / direct.lastIndex
        val requiredCenter = (startEye + (bossTop - startEye) * progress + 4.0).roundToInt()
        val center = candidate.value
        val crossesRoad = nearest.distanceSquared[index(center)] <= 20 * 20
        val radius = if (crossesRoad) 36 else 23
        for (z in (center.z - radius).coerceAtLeast(1)..(center.z + radius).coerceAtMost(MAP_SIZE - 2)) {
            for (x in (center.x - radius).coerceAtLeast(1)..(center.x + radius).coerceAtMost(MAP_SIZE - 2)) {
                val point = QuestMapPoint(x, z)
                val offset = index(point)
                val roadClearance = if (crossesRoad) 8 else 15
                if (nearest.distanceSquared[offset] <= roadClearance * roadClearance) continue
                val distance = kotlin.math.sqrt(center.distanceSquared(point).toDouble())
                if (distance > radius) continue
                val flatCrest = if (crossesRoad) 10.0 else 0.0
                val influence = when {
                    distance <= flatCrest -> 1.0
                    else -> smooth(1.0 - (distance - flatCrest) / (radius - flatCrest))
                }
                val target = (heights[offset] * (1.0 - influence) + requiredCenter * influence).roundToInt()
                heights[offset] = maxOf(heights[offset], target)
            }
        }
    }

    private fun hasWaterClearance(heights: IntArray, center: QuestMapPoint, radius: Int): Boolean {
        for (z in (center.z - radius).coerceAtLeast(0)..(center.z + radius).coerceAtMost(MAP_SIZE - 1)) {
            for (x in (center.x - radius).coerceAtLeast(0)..(center.x + radius).coerceAtMost(MAP_SIZE - 1)) {
                if (QuestMapPoint(x, z).distanceSquared(center) > radius * radius) continue
                if (heights[index(QuestMapPoint(x, z))] <= QUEST_WATER_LEVEL) return false
            }
        }
        return true
    }

    private inline fun <T> Iterable<T>.countIndexed(predicate: (Int, T) -> Boolean): Int {
        var count = 0
        forEachIndexed { index, value -> if (predicate(index, value)) count++ }
        return count
    }

    private data class EcologyMap(
        val groundCovers: IntArray,
        val surfacePatches: IntArray,
        val waterDistances: IntArray,
        val slopes: IntArray,
    )

    private fun ecology(seed: Long, style: QuestTerrainStyle, heights: IntArray): EcologyMap {
        val waterDistances = waterDistances(heights)
        val slopes = IntArray(MAP_SIZE * MAP_SIZE)
        val surfacePatches = IntArray(MAP_SIZE * MAP_SIZE)
        val groundCovers = IntArray(MAP_SIZE * MAP_SIZE)
        for (z in 0 until MAP_SIZE) {
            for (x in 0 until MAP_SIZE) {
                val offset = index(QuestMapPoint(x, z))
                val center = heights[offset]
                val slope = maxOf(
                    abs(center - heights[index(QuestMapPoint((x - 1).coerceAtLeast(0), z))]),
                    abs(center - heights[index(QuestMapPoint((x + 1).coerceAtMost(MAP_SIZE - 1), z))]),
                    abs(center - heights[index(QuestMapPoint(x, (z - 1).coerceAtLeast(0)))]),
                    abs(center - heights[index(QuestMapPoint(x, (z + 1).coerceAtMost(MAP_SIZE - 1)))]),
                )
                slopes[offset] = slope
                val patchNoise = valueNoise(seed xor 0x5041544348L, x / 16.0, z / 16.0)
                surfacePatches[offset] = (((patchNoise + 1.0) * 3.0).toInt()).coerceIn(0, 5)
                val moisture = valueNoise(seed xor 0x4D4F495354L, x / 43.0, z / 43.0) +
                    (10 - waterDistances[offset]).coerceAtLeast(0) * 0.07
                val canopy = valueNoise(seed xor 0x43414E4F5059L, x / 51.0, z / 51.0)
                val cover = when {
                    waterDistances[offset] <= 3 -> QuestGroundCover.SHORE
                    slope >= 3 -> QuestGroundCover.ROCKY
                    style == QuestTerrainStyle.SALTMARSH && (waterDistances[offset] <= 9 || center <= QUEST_WATER_LEVEL + 4) -> QuestGroundCover.PEAT
                    style == QuestTerrainStyle.HIGHLANDS && (center >= 70 || moisture < -0.30) -> QuestGroundCover.HEATH
                    canopy + moisture * 0.35 > 0.12 -> QuestGroundCover.FOREST_FLOOR
                    moisture < -0.32 || (style == QuestTerrainStyle.HIGHLANDS && center >= 62) -> QuestGroundCover.HEATH
                    else -> QuestGroundCover.MEADOW
                }
                groundCovers[offset] = cover.ordinal
            }
        }
        return EcologyMap(groundCovers, surfacePatches, waterDistances, slopes)
    }

    private fun waterDistances(heights: IntArray): IntArray {
        val distances = IntArray(MAP_SIZE * MAP_SIZE) { 32 }
        val queue = ArrayDeque<QuestMapPoint>()
        for (z in 0 until MAP_SIZE) {
            for (x in 0 until MAP_SIZE) {
                val point = QuestMapPoint(x, z)
                val offset = index(point)
                if (heights[offset] <= QUEST_WATER_LEVEL) {
                    distances[offset] = 0
                    queue += point
                }
            }
        }
        val directions = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        while (queue.isNotEmpty()) {
            val point = queue.removeFirst()
            val nextDistance = distances[index(point)] + 1
            if (nextDistance >= 32) continue
            directions.forEach { (dx, dz) ->
                val next = QuestMapPoint(point.x + dx, point.z + dz)
                if (next.x !in 0 until MAP_SIZE || next.z !in 0 until MAP_SIZE) return@forEach
                val nextIndex = index(next)
                if (nextDistance >= distances[nextIndex]) return@forEach
                distances[nextIndex] = nextDistance
                queue += next
            }
        }
        return distances
    }

    private fun flattenPlateau(heights: IntArray, center: QuestMapPoint, flatRadius: Int, featherRadius: Int) {
        val centerHeight = heights[index(center)]
        for (z in (center.z - featherRadius).coerceAtLeast(0)..(center.z + featherRadius).coerceAtMost(MAP_SIZE - 1)) {
            for (x in (center.x - featherRadius).coerceAtLeast(0)..(center.x + featherRadius).coerceAtMost(MAP_SIZE - 1)) {
                val point = QuestMapPoint(x, z)
                val distanceSquared = center.distanceSquared(point)
                if (distanceSquared > featherRadius * featherRadius) continue
                val distance = kotlin.math.sqrt(distanceSquared.toDouble())
                val influence = when {
                    distance <= flatRadius -> 1.0
                    else -> smooth(1.0 - (distance - flatRadius) / (featherRadius - flatRadius))
                }
                val offset = index(point)
                heights[offset] = (heights[offset] * (1.0 - influence) + centerHeight * influence).roundToInt()
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
