package dev.projects.server.questmap

import kotlin.math.abs
import kotlin.math.sqrt

internal fun interface QuestMapPlanProvider {
    fun generate(seed: Long): QuestMapPlan
}

internal object NativeQuestMapPlanProvider : QuestMapPlanProvider {
    override fun generate(seed: Long): QuestMapPlan = VerdantRoadQuestPlanner.generate(seed)
}

internal data class QuestMapCandidateScore(
    val total: Double,
    val heightBandCount: Int,
    val coverTransitionRatio: Double,
    val reliefVariation: Double,
    val landmarkSamples: Int,
)

internal data class QuestMapSelection(
    val requestedSeed: Long,
    val plan: QuestMapPlan,
    val score: QuestMapCandidateScore,
    val attemptedCandidates: Int,
)

/**
 * Generate-and-discard boundary for quest terrain. The runtime consumes only the winning plan, so
 * an Anvil/Terra-backed provider can replace the native provider without changing quest logic.
 */
internal object QuestMapCandidateSelector {
    const val DEFAULT_CANDIDATE_COUNT = 4
    private const val CANDIDATE_SEED_STRIDE = 104_730L // divisible by all six style slots

    fun select(
        requestedSeed: Long,
        candidateCount: Int = DEFAULT_CANDIDATE_COUNT,
        provider: QuestMapPlanProvider = NativeQuestMapPlanProvider,
    ): QuestMapSelection {
        require(candidateCount > 0)
        val candidates = (0 until candidateCount).mapNotNull { ordinal ->
            val candidateSeed = requestedSeed + ordinal * CANDIDATE_SEED_STRIDE
            runCatching { provider.generate(candidateSeed) }
                .getOrNull()
                ?.let { plan -> plan to score(plan) }
        }
        check(candidates.isNotEmpty()) { "No quest-map terrain candidate passed structural generation" }
        val requestedStyle = QuestTerrainStyle.entries[
            Math.floorMod(requestedSeed, QuestTerrainStyle.entries.size.toLong()).toInt()
        ]
        check(candidates.all { (plan, _) -> plan.style == requestedStyle }) {
            "Candidate search crossed the requested terrain concept"
        }
        val winner = candidates.maxWithOrNull(
            compareBy<Pair<QuestMapPlan, QuestMapCandidateScore>> { it.second.total }
                .thenByDescending { it.first.seed },
        )!!
        return QuestMapSelection(
            requestedSeed = requestedSeed,
            plan = winner.first,
            score = winner.second,
            attemptedCandidates = candidateCount,
        )
    }

    fun score(plan: QuestMapPlan): QuestMapCandidateScore {
        val stride = 7
        val heightBands = linkedSetOf<Int>()
        var transitions = 0
        var transitionSamples = 0
        var landmarkSamples = 0
        var severeSamples = 0
        val localReliefs = mutableListOf<Double>()
        for (z in plan.playableBorder until plan.size - plan.playableBorder step stride) {
            for (x in plan.playableBorder until plan.size - plan.playableBorder step stride) {
                val height = plan.heightAt(x, z)
                heightBands += Math.floorDiv(height, 3)
                if (plan.slopeAt(x, z) >= 4) severeSamples++
                if (x + stride < plan.size - plan.playableBorder) {
                    transitionSamples++
                    if (plan.groundCoverAt(x, z) != plan.groundCoverAt(x + stride, z)) transitions++
                }
                if (z + stride < plan.size - plan.playableBorder) {
                    transitionSamples++
                    if (plan.groundCoverAt(x, z) != plan.groundCoverAt(x, z + stride)) transitions++
                }
                val radius = 14
                val neighbors = listOf(
                    plan.heightAt((x - radius).coerceAtLeast(0), z),
                    plan.heightAt((x + radius).coerceAtMost(plan.size - 1), z),
                    plan.heightAt(x, (z - radius).coerceAtLeast(0)),
                    plan.heightAt(x, (z + radius).coerceAtMost(plan.size - 1)),
                )
                val relief = neighbors.map { abs(height - it) }.average()
                localReliefs += relief
                if (relief >= 4.0 && plan.roadDistanceSquaredAt(x, z) > 10 * 10) landmarkSamples++
            }
        }
        val transitionRatio = if (transitionSamples == 0) 0.0 else transitions.toDouble() / transitionSamples
        val reliefAverage = localReliefs.average()
        val reliefVariation = sqrt(localReliefs.sumOf { (it - reliefAverage) * (it - reliefAverage) } / localReliefs.size)
        val severeRatio = severeSamples.toDouble() / localReliefs.size
        val elevationScore = 24.0 - abs(plan.elevationRange() - 31.0) * 0.8
        val detourScore = 18.0 - abs(plan.routeDetourRatio() - 1.25) * 45.0
        val revealScore = plan.terrainOcclusionSamples().coerceAtMost(18) * 0.8
        val heightBandScore = heightBands.size.coerceAtMost(18) * 1.1
        val transitionScore = (transitionRatio * 48.0).coerceAtMost(12.0)
        val reliefScore = (reliefVariation * 4.0).coerceAtMost(13.0)
        val landmarkScore = (landmarkSamples / 9.0).coerceAtMost(12.0)
        val severePenalty = severeRatio * 70.0
        return QuestMapCandidateScore(
            total = elevationScore + detourScore + revealScore + heightBandScore + transitionScore +
                reliefScore + landmarkScore - severePenalty,
            heightBandCount = heightBands.size,
            coverTransitionRatio = transitionRatio,
            reliefVariation = reliefVariation,
            landmarkSamples = landmarkSamples,
        )
    }
}
