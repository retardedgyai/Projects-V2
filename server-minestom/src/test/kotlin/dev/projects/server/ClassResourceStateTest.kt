package dev.projects.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClassResourceStateTest {
    @Test
    fun `resources start at class defaults and aerial gauge clamps`() {
        val resources = ClassResourceState()

        assertEquals(100, resources.mana)
        assertEquals(0.0, resources.aerialGauge)
        resources.addAerialGauge(100)
        resources.addAerialGauge(30)
        assertEquals(100.0, resources.aerialGauge)
    }

    @Test
    fun `normal body and weakpoint hits reward once per execution`() {
        val resources = ClassResourceState()
        val rewards = AerialGaugeRewardState()

        assertTrue(rewards.onNormalHit(resources, 1L, null))
        assertFalse(rewards.onNormalHit(resources, 1L, FixedWeakpoint.HEAD))
        assertEquals(20.0, resources.aerialGauge)
        assertTrue(rewards.onNormalHit(resources, 2L, FixedWeakpoint.BACK))
        assertEquals(50.0, resources.aerialGauge)
    }

    @Test
    fun `reset restores mana and aerial gauge`() {
        val resources = ClassResourceState()
        resources.trySpend(25)
        resources.addAerialGauge(80)

        resources.reset()

        assertEquals(100, resources.mana)
        assertEquals(0.0, resources.aerialGauge)
    }

    @Test
    fun `grounded and empty hold do not drain`() {
        val resources = ClassResourceState()
        val hover = AerialHoverState()
        hover.request(active = true, isGrounded = true)
        assertEquals(0.0, hover.tick(true, -1.0, resources).drained)
        hover.request(active = true, isGrounded = false)
        assertEquals(0.0, hover.tick(false, -1.0, resources).drained)
        assertFalse(hover.isHolding)
    }

    @Test
    fun `airborne falling hold drains and clamps without affecting ascent`() {
        val resources = ClassResourceState()
        resources.addAerialGauge(100)
        val hover = AerialHoverState()
        hover.request(active = true, isGrounded = false)

        val rising = hover.tick(false, 2.0, resources)
        assertEquals(0.0, rising.drained)
        assertEquals(2.0, rising.velocityY)
        val falling = hover.tick(false, -2.0, resources)
        assertEquals(1.25, falling.drained)
        assertEquals(-0.4, falling.velocityY)
    }

    @Test
    fun `drain to zero ends hover`() {
        val resources = ClassResourceState()
        resources.addAerialGauge(1)
        val hover = AerialHoverState()
        hover.request(active = true, isGrounded = false)

        hover.tick(false, -1.0, resources)

        assertEquals(0.0, resources.aerialGauge)
        assertFalse(hover.isHolding)
    }
}
