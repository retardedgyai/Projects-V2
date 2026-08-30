package dev.projects.server.experiment.swarm.combat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CombatGeometryTest {
    private val eye = CombatPoint(0.0, 1.6, 0.0)
    private val target = CombatBounds(
        CombatPoint(2.5, 0.0, -0.5),
        CombatPoint(3.5, 2.0, 0.5),
    )

    @Test
    fun `range uses eye to closest target bounds point`() {
        assertTrue(target.distanceTo(eye) < 3.0)
        assertFalse(target.distanceTo(CombatPoint(-1.0, 1.6, 0.0)) < 3.5)
    }

    @Test
    fun `facing rejects a target behind the player`() {
        assertTrue(CombatGeometry.isWithinFacing(eye, CombatPoint(1.0, 0.0, 0.0), target, 0.35))
        assertFalse(CombatGeometry.isWithinFacing(eye, CombatPoint(-1.0, 0.0, 0.0), target, 0.35))
    }

    @Test
    fun `sampled line of sight accepts a clear sample and rejects a full wall`() {
        val sideBlock = CombatBounds(CombatPoint(1.0, 0.0, 1.0), CombatPoint(2.0, 3.0, 2.0))
        val wall = CombatBounds(CombatPoint(1.0, 0.0, -2.0), CombatPoint(2.0, 3.0, 2.0))

        assertTrue(CombatGeometry.hasSampledLineOfSight(eye, target, listOf(sideBlock)))
        assertFalse(CombatGeometry.hasSampledLineOfSight(eye, target, listOf(wall)))
    }

    @Test
    fun `sweep arc and charge corridor include intersecting bounds only`() {
        val forward = CombatPoint(1.0, 0.0, 0.0)
        val front = CombatBounds(CombatPoint(3.0, 0.0, -0.4), CombatPoint(3.8, 1.8, 0.4))
        val behind = CombatBounds(CombatPoint(-3.8, 0.0, -0.4), CombatPoint(-3.0, 1.8, 0.4))
        val chargeSide = CombatBounds(CombatPoint(3.0, 0.0, 2.5), CombatPoint(3.8, 1.8, 3.1))

        assertTrue(CombatGeometry.intersectsSweepArc(eye, forward, 5.0, 70.0, front))
        assertFalse(CombatGeometry.intersectsSweepArc(eye, forward, 5.0, 70.0, behind))
        assertTrue(CombatGeometry.intersectsChargeCorridor(CombatPoint(0.0, 0.0, 0.0), forward, 8.0, 1.0, 3.0, front))
        assertFalse(CombatGeometry.intersectsChargeCorridor(CombatPoint(0.0, 0.0, 0.0), forward, 8.0, 1.0, 3.0, chargeSide))
    }
}
