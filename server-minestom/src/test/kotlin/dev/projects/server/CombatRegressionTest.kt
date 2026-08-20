package dev.projects.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CombatRegressionTest {
    private val combat = CombatFixture()

    @Test
    fun `forward target inside range is hit`() {
        assertTrue(combat.canHit(Target(0.25, 0.0, 2.0)))
    }

    @Test
    fun `target outside range misses`() {
        assertFalse(combat.canHit(Target(0.0, 0.0, 3.1)))
    }

    @Test
    fun `target behind attacker misses`() {
        assertFalse(combat.canHit(Target(0.0, 0.0, -1.0)))
    }

    @Test
    fun `one execution can hit a target only once`() {
        val target = Target(0.0, 0.0, 2.0)

        assertTrue(combat.confirmHit(100, target))
        assertFalse(combat.confirmHit(100, target))
        assertEquals(1, combat.damageCount(target))
    }

    @Test
    fun `another execution can hit the same target again`() {
        val target = Target(0.0, 0.0, 2.0)

        assertTrue(combat.confirmHit(100, target))
        assertTrue(combat.confirmHit(101, target))
        assertEquals(2, combat.damageCount(target))
    }

    private data class Target(val x: Double, val y: Double, val z: Double)

    /** Minimal deterministic fixture for the server-authoritative hit invariants. */
    private class CombatFixture {
        private val hitRange = 3.0
        private val hitHalfWidth = 0.5
        private val hitTargets = mutableSetOf<Pair<Long, Target>>()
        private val damageByTarget = mutableMapOf<Target, Int>()

        fun canHit(target: Target): Boolean =
            target.z in 0.0..hitRange && kotlin.math.abs(target.x) <= hitHalfWidth

        fun confirmHit(attackExecutionId: Long, target: Target): Boolean {
            if (!canHit(target) || !hitTargets.add(attackExecutionId to target)) {
                return false
            }
            damageByTarget[target] = (damageByTarget[target] ?: 0) + 1
            return true
        }

        fun damageCount(target: Target): Int = damageByTarget[target] ?: 0
    }
}
