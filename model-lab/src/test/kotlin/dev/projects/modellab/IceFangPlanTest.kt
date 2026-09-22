package dev.projects.modellab

import kotlin.test.*

class IceFangPlanTest {
    private val origin = IceFangPlan.Point(0.0, 1.0, 0.0)
    @Test fun forwardFollowsAllFourHeadings() {
        for ((yaw, x, z) in listOf(Triple(0f, 0.0, 7.0), Triple(90f, -7.0, 0.0),
            Triple(180f, 0.0, -7.0), Triple(-90f, 7.0, 0.0))) {
            val path = IceFangPlan.path(origin, yaw) { true }
            assertEquals(x, path.last().point.x, 1e-8)
            assertEquals(z, path.last().point.z, 1e-8)
            assertEquals(listOf(0, 6, 12), path.map { it.start })
        }
    }
    @Test fun wallBetweenEndpointsStopsTheWholeRemainingChain() {
        val teeth = IceFangPlan.path(origin, 0f) { it.z !in 3.0..3.5 }
        assertEquals(1, teeth.size)
        assertEquals(2.0, teeth.single().point.z)
        assertTrue(IceFangPlan.path(origin, 0f) { false }.isEmpty())
    }
    @Test fun hitVolumeRejectsOutsideAndSupportsTallTargets() {
        val tooth = IceFangPlan.path(origin, 0f) { true }.last()
        assertTrue(IceFangPlan.hits(tooth, IceFangPlan.Point(0.0, 2.7, 7.0)))
        assertFalse(IceFangPlan.hits(tooth, IceFangPlan.Point(2.0, 1.0, 7.0)))
        assertFalse(IceFangPlan.hits(tooth, IceFangPlan.Point(0.0, 8.0, 7.0)))
        assertEquals(42, tooth.start + IceFangPlan.LIFETIME)
    }
}
