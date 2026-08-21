package dev.projects.client

import kotlin.test.Test
import kotlin.test.assertEquals

class CooldownHudTest {
    @Test
    fun `cooldown fill is clamped to remaining range`() {
        assertEquals(0f, cooldownFillRatio(0, 80))
        assertEquals(0.5f, cooldownFillRatio(40, 80))
        assertEquals(1f, cooldownFillRatio(100, 80))
        assertEquals(0f, cooldownFillRatio(20, 0))
    }

    @Test
    fun `cooldown seconds use one decimal place`() {
        assertEquals("2.4", cooldownSecondsText(48))
        assertEquals("0.0", cooldownSecondsText(0))
    }
}
