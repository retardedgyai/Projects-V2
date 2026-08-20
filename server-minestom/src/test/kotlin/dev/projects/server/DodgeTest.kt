package dev.projects.server

import net.minestom.server.coordinate.Vec
import dev.projects.protocol.DodgeInput
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DodgeTest {
    @Test
    fun `dodge input follows the player's local directions`() {
        assertDirection(dodgeDirection(Vec(0.0, 0.0, 1.0), DodgeInput(0.0, 1.0)), 0.0, 1.0)
        assertDirection(dodgeDirection(Vec(0.0, 0.0, 1.0), DodgeInput(0.0, -1.0)), 0.0, -1.0)
        assertDirection(dodgeDirection(Vec(0.0, 0.0, 1.0), DodgeInput(1.0, 0.0)), -1.0, 0.0)
        assertDirection(dodgeDirection(Vec(0.0, 0.0, 1.0), DodgeInput(-1.0, 0.0)), 1.0, 0.0)

        assertDirection(dodgeDirection(Vec(0.0, 0.0, -1.0), DodgeInput(1.0, 0.0)), 1.0, 0.0)
        assertDirection(dodgeDirection(Vec(0.0, 0.0, -1.0), DodgeInput(-1.0, 0.0)), -1.0, 0.0)
    }

    @Test
    fun `diagonal dodge input is normalized and zero input falls back to forward`() {
        val diagonal = dodgeDirection(Vec(0.0, 0.0, 1.0), DodgeInput(1.0, 1.0))
        assertEquals(-1.0 / sqrt(2.0), diagonal.x(), 1.0e-9)
        assertEquals(1.0 / sqrt(2.0), diagonal.z(), 1.0e-9)

        assertDirection(dodgeDirection(Vec(1.0, 0.0, 0.0), DodgeInput(0.0, 0.0)), 1.0, 0.0)
    }

    @Test
    fun `all eight directions keep the same normalized distance`() {
        val directions = listOf(
            DodgeInput(0.0, 1.0),
            DodgeInput(1.0, 1.0),
            DodgeInput(1.0, 0.0),
            DodgeInput(1.0, -1.0),
            DodgeInput(0.0, -1.0),
            DodgeInput(-1.0, -1.0),
            DodgeInput(-1.0, 0.0),
            DodgeInput(-1.0, 1.0),
        )

        for (input in directions) {
            val dodge = DodgeState()
            assertTrue(dodge.request(input, canStart = true, facing = Vec(0.0, 0.0, 1.0)))
            val movement = (1..DodgeState.DURATION_TICKS)
                .map { assertNotNull(dodge.tick(canStart = true, facing = Vec(0.0, 0.0, 1.0))) }
            val traveled = movement.sumOf { sqrt(it.x() * it.x() + it.z() * it.z()) }
            assertEquals(DodgeState.DISTANCE, traveled, 1.0e-9)
        }
    }

    @Test
    fun `dodge starts quickly and decelerates monotonically`() {
        val dodge = DodgeState()
        assertTrue(dodge.request(DodgeInput(0.0, 1.0), canStart = true, facing = Vec(0.0, 0.0, 1.0)))

        val movement = (1..DodgeState.DURATION_TICKS)
            .map { assertNotNull(dodge.tick(canStart = true, facing = Vec(0.0, 0.0, 1.0))) }
            .map { sqrt(it.x() * it.x() + it.z() * it.z()) }

        assertTrue(movement.first() > movement[DodgeState.DURATION_TICKS / 2])
        assertTrue(movement.zipWithNext().all { (current, next) -> current > next })
        assertTrue(movement.last() < movement.first() * 0.1)
        assertEquals(DodgeState.DISTANCE, movement.sum(), 1.0e-9)
        assertNull(dodge.tick(canStart = true, facing = Vec(0.0, 0.0, 1.0)))
    }

    @Test
    fun `dodge velocity converts one tick movement and stops only horizontally`() {
        val velocity = dodgeVelocity(Vec(0.25, 0.0, -0.5), verticalVelocity = 0.2)
        assertEquals(5.0, velocity.x(), 1.0e-9)
        assertEquals(0.2, velocity.y(), 1.0e-9)
        assertEquals(-10.0, velocity.z(), 1.0e-9)

        val stopped = stopDodgeVelocity(Vec(5.0, -0.3, -10.0))
        assertEquals(0.0, stopped.x(), 1.0e-9)
        assertEquals(-0.3, stopped.y(), 1.0e-9)
        assertEquals(0.0, stopped.z(), 1.0e-9)
    }

    @Test
    fun `dodge rejects re-dodge while active and allows it after completion`() {
        val dodge = DodgeState()
        assertTrue(dodge.request(DodgeInput(0.0, 1.0), canStart = true, facing = Vec(0.0, 0.0, 1.0)))
        assertFalse(dodge.request(DodgeInput(1.0, 0.0), canStart = true, facing = Vec(0.0, 0.0, 1.0)))
        repeat(DodgeState.DURATION_TICKS) {
            assertNotNull(dodge.tick(canStart = true, facing = Vec(0.0, 0.0, 1.0)))
        }
        assertNull(dodge.tick(canStart = true, facing = Vec(0.0, 0.0, 1.0)))
        assertTrue(dodge.request(DodgeInput(1.0, 0.0), canStart = true, facing = Vec(0.0, 0.0, 1.0)))
    }

    @Test
    fun `dodge queues once until attack fully ends`() {
        val combat = CombatState(executionIdSource = sequence())
        val dodge = DodgeState()
        combat.input(dev.projects.protocol.AttackInputState.PRESS)

        assertTrue(
            dodge.request(
                DodgeInput(0.0, 1.0),
                canStart = combat.isAttacking.not(),
                facing = Vec(0.0, 0.0, 1.0),
            ),
        )
        assertFalse(
            dodge.request(
                DodgeInput(1.0, 0.0),
                canStart = combat.isAttacking.not(),
                facing = Vec(0.0, 0.0, 1.0),
            ),
        )
        assertNull(dodge.tick(canStart = false, facing = Vec(0.0, 0.0, 1.0)))

        repeat(combat.activeProfile!!.totalTicks - 1) {
            if (dodge.hasPending) combat.deferAttackRestart()
            combat.tick(Vec.ZERO, Vec(0.0, 0.0, 1.0), emptyList())
            assertNull(dodge.tick(canStart = !combat.isAttacking, facing = Vec(0.0, 0.0, 1.0)))
        }

        combat.deferAttackRestart()
        val finalEvents = combat.tick(Vec.ZERO, Vec(0.0, 0.0, 1.0), emptyList())
        assertFalse(finalEvents.any { it is CombatEvent.Started })
        assertNotNull(dodge.tick(canStart = !combat.isAttacking, facing = Vec(0.0, 0.0, 1.0)))
        assertFalse(dodge.hasPending)
    }

    @Test
    fun `pending local input resolves using activation facing`() {
        val dodge = DodgeState()
        assertTrue(
            dodge.request(
                DodgeInput(1.0, 0.0),
                canStart = false,
                facing = Vec(0.0, 0.0, 1.0),
            ),
        )

        val movement = assertNotNull(dodge.tick(canStart = true, facing = Vec(-1.0, 0.0, 0.0)))
        assertEquals(0.0, movement.x(), 1.0e-9)
        assertTrue(movement.z() < 0.0)
    }

    @Test
    fun `active dodge direction does not change when facing changes`() {
        val dodge = DodgeState()
        assertTrue(dodge.request(DodgeInput(0.0, 1.0), canStart = true, facing = Vec(0.0, 0.0, 1.0)))

        val movement = assertNotNull(dodge.tick(canStart = true, facing = Vec(-1.0, 0.0, 0.0)))
        assertTrue(movement.z() > 0.0)
        assertEquals(0.0, movement.x(), 1.0e-9)
    }

    @Test
    fun `pending zero input uses activation forward`() {
        val dodge = DodgeState()
        assertTrue(
            dodge.request(
                DodgeInput(0.0, 0.0),
                canStart = false,
                facing = Vec(0.0, 0.0, 1.0),
            ),
        )

        val movement = assertNotNull(dodge.tick(canStart = true, facing = Vec(-1.0, 0.0, 0.0)))
        assertTrue(movement.x() < 0.0)
        assertEquals(0.0, movement.z(), 1.0e-9)
    }

    private fun sequence(): () -> Long {
        var id = 0L
        return { ++id }
    }

    private fun assertDirection(direction: Vec, expectedX: Double, expectedZ: Double) {
        assertEquals(expectedX, direction.x(), 1.0e-9)
        assertEquals(expectedZ, direction.z(), 1.0e-9)
    }
}
