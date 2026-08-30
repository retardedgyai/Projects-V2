package dev.projects.server.questmap

import kotlin.math.abs
import kotlin.math.max

internal data class QuestMapPoint(
    val x: Int,
    val z: Int,
) {
    fun distanceSquared(other: QuestMapPoint): Int {
        val dx = x - other.x
        val dz = z - other.z
        return dx * dx + dz * dz
    }
}

internal enum class QuestTerrainStyle {
    VERDANT,
    HIGHLANDS,
    SALTMARSH,
}

internal enum class QuestMapContentKind {
    START,
    COMBAT,
    GATHERING,
    DISCOVERY,
    BOSS,
}

internal data class QuestMapContent(
    val kind: QuestMapContentKind,
    val position: QuestMapPoint,
    val mainRouteIndex: Int,
    val optional: Boolean,
)

/** Immutable output of one concrete ProjectS quest-map generation pass. */
internal class QuestMapPlan(
    val seed: Long,
    val size: Int,
    val playableBorder: Int,
    val style: QuestTerrainStyle,
    val mainRoute: List<QuestMapPoint>,
    val trails: Set<QuestMapPoint>,
    val contents: List<QuestMapContent>,
    private val heights: IntArray,
    private val roadDistanceSquared: IntArray,
) {
    init {
        require(size > 0)
        require(playableBorder in 1 until size / 2)
        require(heights.size == size * size)
        require(roadDistanceSquared.size == size * size)
        require(mainRoute.isNotEmpty())
    }

    val start: QuestMapPoint = contents.single { it.kind == QuestMapContentKind.START }.position
    val boss: QuestMapPoint = contents.single { it.kind == QuestMapContentKind.BOSS }.position

    fun heightAt(x: Int, z: Int): Int = heights[index(x, z)]

    fun heightAt(point: QuestMapPoint): Int = heightAt(point.x, point.z)

    fun roadDistanceSquaredAt(x: Int, z: Int): Int = roadDistanceSquared[index(x, z)]

    fun isInsidePlayable(point: QuestMapPoint): Boolean =
        point.x in playableBorder until size - playableBorder &&
            point.z in playableBorder until size - playableBorder

    fun elevationRange(): Int {
        var lowest = Int.MAX_VALUE
        var highest = Int.MIN_VALUE
        heights.forEach { height ->
            lowest = minOf(lowest, height)
            highest = maxOf(highest, height)
        }
        return highest - lowest
    }

    fun fingerprint(): Long {
        var hash = seed xor -7046029254386353131L
        fun mix(value: Long) {
            hash = (hash xor value) * -4658895280553007687L
            hash = hash xor (hash ushr 27)
        }
        mix(style.ordinal.toLong())
        mainRoute.forEach { point -> mix((point.x.toLong() shl 32) xor point.z.toLong()) }
        contents.forEach { content ->
            mix(content.kind.ordinal.toLong())
            mix((content.position.x.toLong() shl 32) xor content.position.z.toLong())
        }
        heights.forEach { mix(it.toLong()) }
        return hash
    }

    private fun index(x: Int, z: Int): Int {
        require(x in 0 until size && z in 0 until size) { "Point outside quest map: $x,$z" }
        return z * size + x
    }
}

internal data class QuestMapQualityReport(
    val violations: List<String>,
) {
    val accepted: Boolean get() = violations.isEmpty()
}

internal object QuestMapQualityGate {
    private const val MINIMUM_MAIN_ROUTE_POINTS = 210
    private const val MAXIMUM_CONTENT_GAP = 75

    fun evaluate(plan: QuestMapPlan): QuestMapQualityReport {
        val violations = mutableListOf<String>()
        val expectedCounts = mapOf(
            QuestMapContentKind.START to 1,
            QuestMapContentKind.COMBAT to 3,
            QuestMapContentKind.GATHERING to 4,
            QuestMapContentKind.DISCOVERY to 3,
            QuestMapContentKind.BOSS to 1,
        )

        if (plan.size != VerdantRoadQuestPlanner.MAP_SIZE) violations += "Unexpected map size"
        if (plan.mainRoute.size < MINIMUM_MAIN_ROUTE_POINTS) violations += "Main route is too short"
        if (plan.mainRoute.first() != plan.start) violations += "Main route does not start at the quest start"
        if (plan.mainRoute.last() != plan.boss) violations += "Main route does not terminate at the boss"
        if (!plan.contents.all { plan.isInsidePlayable(it.position) }) violations += "Content outside playable area"
        expectedCounts.forEach { (kind, expected) ->
            if (plan.contents.count { it.kind == kind } != expected) violations += "$kind count is not $expected"
        }

        plan.mainRoute.zipWithNext().forEachIndexed { index, (from, to) ->
            if (max(abs(from.x - to.x), abs(from.z - to.z)) != 1) {
                violations += "Broken route adjacency at $index"
                return@forEachIndexed
            }
            if (abs(plan.heightAt(from) - plan.heightAt(to)) > 1) {
                violations += "Unwalkable route step at $index"
            }
        }

        if (!roadReachable(plan)) violations += "Boss is not reachable on generated road"
        if (plan.start.distanceSquared(plan.boss) < 120 * 120) violations += "Boss is too close to start"
        if (plan.boss.x !in 14 until plan.size - 14 || plan.boss.z !in 14 until plan.size - 14) {
            violations += "Boss arena does not fit inside generated extent"
        }
        if (plan.elevationRange() < 8) violations += "Terrain is visually too flat"

        val requiredRouteIndices = plan.contents
            .map { it.mainRouteIndex }
            .sorted()
        requiredRouteIndices.zipWithNext().forEach { (from, to) ->
            if (to - from > MAXIMUM_CONTENT_GAP) violations += "Main route contains an empty content gap"
        }

        plan.contents.forEachIndexed { index, left ->
            plan.contents.drop(index + 1).forEach { right ->
                if (left.kind != QuestMapContentKind.START && right.kind != QuestMapContentKind.BOSS &&
                    left.position.distanceSquared(right.position) < 6 * 6
                ) {
                    violations += "Content points overlap"
                }
            }
        }

        return QuestMapQualityReport(violations.distinct())
    }

    fun requireAccepted(plan: QuestMapPlan): QuestMapPlan {
        val report = evaluate(plan)
        require(report.accepted) { "Quest map seed ${plan.seed} rejected: ${report.violations.joinToString()}" }
        return plan
    }

    private fun roadReachable(plan: QuestMapPlan): Boolean {
        val visited = BooleanArray(plan.size * plan.size)
        val queue = ArrayDeque<QuestMapPoint>()
        queue += plan.start
        visited[index(plan, plan.start)] = true
        val directions = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, -1 to 1, 1 to -1, 1 to 1)

        while (queue.isNotEmpty()) {
            val point = queue.removeFirst()
            if (point == plan.boss) return true
            directions.forEach { (dx, dz) ->
                val next = QuestMapPoint(point.x + dx, point.z + dz)
                if (next.x !in 0 until plan.size || next.z !in 0 until plan.size) return@forEach
                val nextIndex = index(plan, next)
                if (visited[nextIndex] || plan.roadDistanceSquaredAt(next.x, next.z) > 3 * 3) return@forEach
                if (abs(plan.heightAt(point) - plan.heightAt(next)) > 1) return@forEach
                visited[nextIndex] = true
                queue += next
            }
        }
        return false
    }

    private fun index(plan: QuestMapPlan, point: QuestMapPoint): Int = point.z * plan.size + point.x
}
