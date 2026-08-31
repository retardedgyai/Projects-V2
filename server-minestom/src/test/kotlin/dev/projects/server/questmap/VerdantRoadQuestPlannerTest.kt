package dev.projects.server.questmap

import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VerdantRoadQuestPlannerTest {
    @Test
    fun `generation is deterministic and seed-sensitive`() {
        val first = VerdantRoadQuestPlanner.generate(7_341L)
        val repeated = VerdantRoadQuestPlanner.generate(7_341L)
        val different = VerdantRoadQuestPlanner.generate(7_342L)

        assertEquals(first.fingerprint(), repeated.fingerprint())
        assertNotEquals(first.fingerprint(), different.fingerprint())
    }

    @Test
    fun `representative seeds always pass the complete quality gate`() {
        val styles = mutableSetOf<QuestTerrainStyle>()
        val layouts = mutableSetOf<QuestRouteLayout>()
        val terrainProfiles = mutableSetOf<QuestTerrainProfile>()
        repeat(120) { ordinal ->
            val plan = VerdantRoadQuestPlanner.generate(ordinal * 104_729L + 17L)
            val report = QuestMapQualityGate.evaluate(plan)
            assertTrue(report.accepted, "seed=${plan.seed}: ${report.violations}")
            styles += plan.style
            layouts += plan.routeLayout
            terrainProfiles += plan.terrainProfile
        }
        assertEquals(QuestTerrainStyle.entries.toSet(), styles)
        assertEquals(QuestRouteLayout.entries.toSet(), layouts)
        assertEquals(QuestTerrainProfile.entries.toSet(), terrainProfiles)
    }

    @Test
    fun `one map contains the authored ProjectS quest rhythm`() {
        val plan = VerdantRoadQuestPlanner.generate(919_191L)

        assertEquals(224, plan.size)
        assertEquals(1, plan.contents.count { it.kind == QuestMapContentKind.START })
        assertEquals(3, plan.contents.count { it.kind == QuestMapContentKind.COMBAT })
        assertEquals(4, plan.contents.count { it.kind == QuestMapContentKind.GATHERING })
        assertEquals(3, plan.contents.count { it.kind == QuestMapContentKind.DISCOVERY })
        assertEquals(1, plan.contents.count { it.kind == QuestMapContentKind.BOSS })
        assertTrue(plan.mainRoute.size >= 180)
        assertTrue(plan.elevationRange() in 16..48)
        assertTrue(plan.terrainOcclusionSamples() >= 3)
        assertTrue(plan.routeDetourRatio() in 1.06..1.58)
        assertTrue(plan.maximumRouteRise() <= 5)
        assertTrue(plan.maximumRoadShoulderRelief() <= 4)
        assertTrue(plan.explorableCorridorCoverage() >= 0.82)
        assertTrue(plan.groundCoverDiversity() >= 4)
    }

    @Test
    fun `failed highland smoke seed now produces a moderate explorable pass`() {
        val plan = VerdantRoadQuestPlanner.generate(1_788_109_725_769L)

        assertEquals(QuestTerrainStyle.HIGHLANDS, plan.style)
        assertEquals(QuestRouteLayout.MEANDER, plan.routeLayout)
        assertEquals(QuestTerrainProfile.BROKEN_HILLS, plan.terrainProfile)
        assertTrue(plan.routeDetourRatio() <= 1.58)
        assertTrue(plan.maximumRouteRise() <= 5)
        assertTrue(plan.maximumRoadShoulderRelief() <= 4)
        assertTrue(plan.explorableCorridorCoverage() >= 0.82)
        assertTrue(plan.elevationRange() <= 48)
    }

    @Test
    fun `four compact smoke seeds expose every route topology`() {
        val plans = (0L..3L).map(VerdantRoadQuestPlanner::generate)

        assertEquals(QuestRouteLayout.entries.toSet(), plans.map { it.routeLayout }.toSet())
        assertEquals(4, plans.map { it.fingerprint() }.toSet().size)
    }

    @Test
    fun `six compact smoke seeds expose every terrain concept`() {
        val plans = (0L..5L).map(VerdantRoadQuestPlanner::generate)

        assertEquals(QuestTerrainStyle.entries.toSet(), plans.map { it.style }.toSet())
    }

    @Test
    fun `prewarm range covers the complete client render horizon`() {
        val range = questMapRenderChunkRange(VerdantRoadQuestPlanner.MAP_SIZE, 8)

        assertEquals(-8, range.first)
        assertEquals(21, range.last)
        assertEquals(900, range.count() * range.count())
    }

    @Test
    fun `failed manual smoke saltmarsh seed no longer floods or builds a perimeter wall`() {
        val plan = VerdantRoadQuestPlanner.generate(1_788_101_320_652L)

        assertEquals(QuestTerrainStyle.SALTMARSH, plan.style)
        assertTrue(plan.surfaceCoverageAtOrBelow(QUEST_WATER_LEVEL) in 0.04..0.38)
        assertTrue(plan.maximumBoundaryRise() <= 16)
        assertTrue(plan.mainRoute.all { plan.heightAt(it) > QUEST_WATER_LEVEL })
        assertTrue(plan.maximumWaterBankStep() <= 4)
    }

    @Test
    fun `generation stays inside the prewarm budget`() {
        val elapsed = measureTimeMillis {
            repeat(20) { VerdantRoadQuestPlanner.generate(50_000L + it) }
        }

        assertTrue(elapsed < 4_000, "20 maps took ${elapsed}ms")
    }
}
