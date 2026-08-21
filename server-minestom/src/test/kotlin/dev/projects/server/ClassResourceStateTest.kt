package dev.projects.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClassResourceStateTest {
    @Test
    fun `mana starts full and reset restores it`() {
        val resources = ClassResourceState()

        assertEquals(100, resources.mana)
        assertTrue(resources.trySpend(25))
        assertEquals(75, resources.mana)
        resources.reset()
        assertEquals(100, resources.mana)
    }

    @Test
    fun `mana cannot be overspent`() {
        val resources = ClassResourceState()

        assertFalse(resources.trySpend(101))
        assertEquals(100, resources.mana)
        assertEquals(100, resources.snapshot(0).mana)
        assertEquals(60, resources.snapshot(0).skill3CooldownMaxTicks)
    }
}
