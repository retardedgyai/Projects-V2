package dev.projects.server

import net.minestom.server.coordinate.Vec
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DodgeTest {
    @Test
    fun `all eight directions keep the same normalized distance`() {
        val directions = listOf(
            Vec(0.0, 0.0, 1.0),
            Vec(1.0, 0.0, 1.0),
            Vec(1.0, 0.0, 0.0),
            Vec(1.0, 0.0, -1.0),
            Vec(0.0, 0.0, -1.0),
            Vec(-1.0, 0.0, -1.0),
            Vec(-1.0, 0.0, 0.0),
            Vec(-1.0, 0.0, 1.0),
        )

        for (direction in directions) {
            val dodge = DodgeState()
            assertTrue(dodge.request(direction, canStart = true))
            val movement = (1..DodgeState.DURATION_TICKS).map { assertNotNull(dodge.tick(canStart = true)) }
            val traveled = movement.sumOf { sqrt(it.x() * it.x() + it.z() * it.z()) }
            assertEquals(DodgeState.DISTANCE, traveled, 1.0e-9)
        }
    }

    @Test
    fun `dodge rejects re-dodge while active and allows it after completion`() {
        val dodge = DodgeState()
        assertTrue(dodge.request(Vec(0.0, 0.0, 1.0), canStart = true))
        assertFalse(dodge.request(Vec(1.0, 0.0, 0.0), canStart = true))
        repeat(DodgeState.DURATION_TICKS) { assertNotNull(dodge.tick(canStart = true)) }
        assertNull(dodge.tick(canStart = true))
        assertTrue(dodge.request(Vec(1.0, 0.0, 0.0), canStart = true))
    }

    @Test
    fun `dodge queues once until attack fully ends`() {
        val combat = CombatState(executionIdSource = sequence())
        val dodge = DodgeState()
        combat.input(dev.projects.protocol.AttackInputState.PRESS)

        assertTrue(dodge.request(Vec(0.0, 0.0, 1.0), canStart = combat.isAttacking.not()))
        assertFalse(dodge.request(Vec(1.0, 0.0, 0.0), canStart = combat.isAttacking.not()))
        assertNull(dodge.tick(canStart = false))

        repeat(combat.activeProfile!!.totalTicks - 1) {
            if (dodge.hasPending) combat.deferAttackRestart()
            combat.tick(Vec.ZERO, Vec(0.0, 0.0, 1.0), emptyList())
            assertNull(dodge.tick(canStart = !combat.isAttacking))
        }

        combat.deferAttackRestart()
        val finalEvents = combat.tick(Vec.ZERO, Vec(0.0, 0.0, 1.0), emptyList())
        assertFalse(finalEvents.any { it is CombatEvent.Started })
        assertNotNull(dodge.tick(canStart = !combat.isAttacking))
        assertFalse(dodge.hasPending)
    }

    private fun sequence(): () -> Long {
        var id = 0L
        return { ++id }
    }
}
