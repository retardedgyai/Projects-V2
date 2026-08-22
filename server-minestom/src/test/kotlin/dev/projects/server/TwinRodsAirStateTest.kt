package dev.projects.server

import dev.projects.protocol.AirJumpInput
import net.minestom.server.coordinate.Vec
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwinRodsAirStateTest {
    @Test
    fun `ground reset leaves no air jump charge`() {
        val air = TwinRodsAirState()
        air.onAttackHit(WeaponType.TWIN_RODS, isGrounded = false, attackExecutionId = 1L)
        assertEquals(1, air.airJumpCharges)

        air.tick(isGrounded = true)

        assertEquals(0, air.airJumpCharges)
    }

    @Test
    fun `only an airborne Twin Rods hit grants air jump`() {
        val air = TwinRodsAirState()

        air.onAttackHit(WeaponType.HEAVY_BLADE, isGrounded = false, attackExecutionId = 1L)
        air.onAttackHit(WeaponType.TWIN_RODS, isGrounded = true, attackExecutionId = 2L)
        assertEquals(0, air.airJumpCharges)

        air.onAttackHit(WeaponType.TWIN_RODS, isGrounded = false, attackExecutionId = 3L)
        assertEquals(1, air.airJumpCharges)
    }

    @Test
    fun `one attack execution grants air rewards only once`() {
        val air = TwinRodsAirState()

        air.onAttackHit(WeaponType.TWIN_RODS, isGrounded = false, attackExecutionId = 10L)
        assertEquals(1, air.airJumpCharges)

        check(air.consumeAirJump())
        air.onAttackHit(WeaponType.TWIN_RODS, isGrounded = false, attackExecutionId = 10L)
        assertEquals(0, air.airJumpCharges)

        air.onAttackHit(WeaponType.TWIN_RODS, isGrounded = false, attackExecutionId = 11L)
        assertEquals(1, air.airJumpCharges)
    }

    @Test
    fun `air jump consumes only an available charge`() {
        val air = TwinRodsAirState()
        assertFalse(air.consumeAirJump())

        air.onAttackHit(WeaponType.TWIN_RODS, isGrounded = false, attackExecutionId = 1L)
        assertTrue(air.consumeAirJump())
        assertFalse(air.consumeAirJump())
        assertEquals(0, air.airJumpCharges)
    }

    @Test
    fun `switching away clears air jump and re-equipping does not restore it`() {
        val air = TwinRodsAirState()
        air.onAttackHit(WeaponType.TWIN_RODS, isGrounded = false, attackExecutionId = 1L)
        assertEquals(1, air.airJumpCharges)

        air.clearAirJump()

        assertEquals(0, air.airJumpCharges)
    }

    @Test
    fun `air jump raises falling velocity without weakening stronger ascent`() {
        val falling = airJumpVelocity(
            Vec(1.0, -0.4, -2.0),
            Vec(0.0, 0.0, 1.0),
            AirJumpInput(0.0, 0.0),
        )
        assertEquals(1.0, falling.x())
        assertEquals(8.4, falling.y(), 1.0e-9)
        assertEquals(-2.0, falling.z())

        val rising = airJumpVelocity(
            Vec(1.0, 9.0, -2.0),
            Vec(0.0, 0.0, 1.0),
            AirJumpInput(0.0, 0.0),
        )
        assertEquals(9.0, rising.y())
    }

    @Test
    fun `air jump keeps horizontal velocity without input`() {
        val velocity = airJumpVelocity(
            Vec(0.3, -0.2, -0.4),
            Vec(0.0, 0.0, 1.0),
            AirJumpInput(0.0, 0.0),
        )

        assertEquals(0.3, velocity.x())
        assertEquals(-0.4, velocity.z())
    }

    @Test
    fun `directional air jump uses facing basis and normalizes diagonals`() {
        val right = airJumpVelocity(
            Vec.ZERO,
            Vec(0.0, 0.0, 1.0),
            AirJumpInput(1.0, 0.0),
        )
        assertEquals(-5.0, right.x(), 1.0e-9)
        assertEquals(0.0, right.z(), 1.0e-9)

        val diagonal = airJumpVelocity(
            Vec.ZERO,
            Vec(0.0, 0.0, 1.0),
            AirJumpInput(1.0, 1.0),
        )
        assertEquals(5.0 / sqrt(2.0), kotlin.math.abs(diagonal.x()), 1.0e-9)
        assertEquals(5.0 / sqrt(2.0), diagonal.z(), 1.0e-9)
    }

}
