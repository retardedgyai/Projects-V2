package dev.projects.server.questmap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestMapCandidateSelectionTest {
    @Test
    fun `candidate selector is deterministic and preserves the requested concept`() {
        val first = QuestMapCandidateSelector.select(91_337L, candidateCount = 4)
        val repeated = QuestMapCandidateSelector.select(91_337L, candidateCount = 4)

        assertEquals(first.plan.seed, repeated.plan.seed)
        assertEquals(first.score, repeated.score)
        assertEquals(4, first.attemptedCandidates)
        assertEquals(
            Math.floorMod(91_337L, QuestTerrainStyle.entries.size.toLong()).toInt(),
            first.plan.style.ordinal,
        )
    }

    @Test
    fun `selector returns the highest-scoring generated candidate`() {
        val requestedSeed = 7_701L
        val selection = QuestMapCandidateSelector.select(requestedSeed, candidateCount = 4)
        val scores = (0 until 4).map { ordinal ->
            val plan = VerdantRoadQuestPlanner.generate(requestedSeed + ordinal * 104_730L)
            QuestMapCandidateSelector.score(plan).total
        }

        assertEquals(scores.max(), selection.score.total)
        assertTrue(selection.score.heightBandCount >= 6)
        assertTrue(selection.score.landmarkSamples > 0)
    }
}
