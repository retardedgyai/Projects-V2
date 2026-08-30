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
        repeat(120) { ordinal ->
            val plan = VerdantRoadQuestPlanner.generate(ordinal * 104_729L + 17L)
            val report = QuestMapQualityGate.evaluate(plan)
            assertTrue(report.accepted, "seed=${plan.seed}: ${report.violations}")
            styles += plan.style
        }
        assertEquals(QuestTerrainStyle.entries.toSet(), styles)
    }

    @Test
    fun `one map contains the authored ProjectS quest rhythm`() {
        val plan = VerdantRoadQuestPlanner.generate(919_191L)

        assertEquals(160, plan.size)
        assertEquals(1, plan.contents.count { it.kind == QuestMapContentKind.START })
        assertEquals(3, plan.contents.count { it.kind == QuestMapContentKind.COMBAT })
        assertEquals(4, plan.contents.count { it.kind == QuestMapContentKind.GATHERING })
        assertEquals(3, plan.contents.count { it.kind == QuestMapContentKind.DISCOVERY })
        assertEquals(1, plan.contents.count { it.kind == QuestMapContentKind.BOSS })
        assertTrue(plan.mainRoute.size >= 210)
        assertTrue(plan.elevationRange() >= 8)
    }

    @Test
    fun `generation stays inside the prewarm budget`() {
        val elapsed = measureTimeMillis {
            repeat(20) { VerdantRoadQuestPlanner.generate(50_000L + it) }
        }

        assertTrue(elapsed < 4_000, "20 maps took ${elapsed}ms")
    }
}
