package dev.projects.server

import dev.projects.protocol.DodgeInput
import net.minestom.server.coordinate.Vec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TwinRodsAirStateTest {
    @Test
    fun `twin rods hit refreshes sustain but heavy blade does not`() {
        val air = TwinRodsAirState()

        air.tick(isGrounded = false)
        air.onAttackHit(WeaponType.HEAVY_BLADE, isGrounded = false, attackExecutionId = 1L)
        assertFalse(air.isSustainActive)

        air.onAttackHit(WeaponType.TWIN_RODS, isGrounded = false, attackExecutionId = 2L)
        assertTrue(air.isSustainActive)
    }

    @Test
    fun `grounding clears sustain and resets charges`() {
        val air = TwinRodsAirState()
        assertTrue(air.consumeAirDodge())
        air.onAttackHit(WeaponType.TWIN_RODS, isGrounded = false, attackExecutionId = 1L)
        assertEquals(2, air.airDodgeCharges)

        air.tick(isGrounded = true)

        assertFalse(air.isSustainActive)
        assertEquals(2, air.airDodgeCharges)
    }

    @Test
    fun `sustain clamps only falling velocity`() {
        val rising = applyAerialSustain(Vec(1.0, 0.4, -2.0), sustainActive = true)
        val falling = applyAerialSustain(Vec(1.0, -0.4, -2.0), sustainActive = true)

        assertEquals(0.4, rising.y())
        assertEquals(0.0, falling.y())
        assertEquals(1.0, falling.x())
        assertEquals(-2.0, falling.z())
    }

    @Test
    fun `sustain expires without velocity correction`() {
        val air = TwinRodsAirState()
        air.onAttackHit(WeaponType.TWIN_RODS, isGrounded = false, attackExecutionId = 1L)
        repeat(TwinRodsAirState.SUSTAIN_WINDOW_TICKS) { air.tick(isGrounded = false) }

        assertFalse(air.isSustainActive)
        assertEquals(
            Vec(0.0, -0.4, 0.0),
            applyAerialSustain(Vec(0.0, -0.4, 0.0), air.isSustainActive),
        )
    }

    @Test
    fun `switching away clears sustain and re-equipping does not restore it`() {
        val air = TwinRodsAirState()
        air.onAttackHit(WeaponType.TWIN_RODS, isGrounded = false, attackExecutionId = 1L)
        assertTrue(air.isSustainActive)

        air.clearSustain()

        assertFalse(air.isSustainActive)
        assertEquals(
            Vec(0.0, -0.4, 0.0),
            applyAerialSustain(Vec(0.0, -0.4, 0.0), air.isSustainActive),
        )
    }

    @Test
    fun `air dodge starts with two charges and recovers once per execution`() {
        val air = TwinRodsAirState()
        assertEquals(2, air.airDodgeCharges)
        assertTrue(air.consumeAirDodge())
        assertTrue(air.consumeAirDodge())
        assertFalse(air.consumeAirDodge())

        air.onAttackHit(WeaponType.TWIN_RODS, isGrounded = false, attackExecutionId = 10L)
        air.onAttackHit(WeaponType.TWIN_RODS, isGrounded = false, attackExecutionId = 10L)
        assertEquals(1, air.airDodgeCharges)

        air.onAttackHit(WeaponType.TWIN_RODS, isGrounded = false, attackExecutionId = 11L)
        assertEquals(2, air.airDodgeCharges)
    }

    @Test
    fun `air dodge queue consumes only when it starts`() {
        val air = TwinRodsAirState()
        val dodge = DodgeState()
        val input = DodgeInput(1.0, 0.0)

        assertTrue(
            dodge.request(
                input,
                canStart = false,
                facing = Vec(0.0, 0.0, 1.0),
                startAllowed = air::canStartAirDodge,
                onStart = { check(air.consumeAirDodge()) },
            ),
        )
        assertEquals(2, air.airDodgeCharges)
        assertFalse(dodge.request(input, canStart = false, facing = Vec(0.0, 0.0, 1.0)))

        assertNotNull(
            dodge.tick(
                canStart = true,
                facing = Vec(-1.0, 0.0, 0.0),
                startAllowed = air::canStartAirDodge,
                onStart = { check(air.consumeAirDodge()) },
            ),
        )
        assertEquals(1, air.airDodgeCharges)
    }
}
