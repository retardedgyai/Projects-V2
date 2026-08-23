package dev.projects.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CombatHudLayoutTest {
    @Test
    fun `class core stays on the gui center`() {
        val layout = calculateCombatHudLayout(640, 360)

        assertEquals(320, layout.core.centerX)
        assertEquals(layout.core.x, layout.health.right + 8)
        assertEquals(layout.core.right, layout.resource.x - 8)
    }

    @Test
    fun `combat groups fit and do not overlap at minimum size`() {
        val layout = calculateCombatHudLayout(640, 360)
        val rects = listOf(layout.health, layout.core, layout.resource, layout.skills, layout.hotbar, layout.offhand)

        assertTrue(rects.all { it.isWithin(640, 360) })
        assertFalse(layout.skills.intersects(layout.hotbar))
        assertFalse(layout.hotbar.intersects(layout.offhand))
        assertFalse(layout.health.intersects(layout.core))
        assertFalse(layout.core.intersects(layout.resource))
    }
}
