package dev.projects.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemTooltipLayoutTest {
    @Test
    fun `only ProjectS item tooltip styles center their title`() {
        assertTrue(ItemTooltipLayout.shouldCenterTitle("projects", "item_common"))
        assertTrue(ItemTooltipLayout.shouldCenterTitle("projects", "item_epic"))
        assertFalse(ItemTooltipLayout.shouldCenterTitle("minecraft", "item_epic"))
        assertFalse(ItemTooltipLayout.shouldCenterTitle("projects", "dialog"))
        assertFalse(ItemTooltipLayout.shouldCenterTitle(null, null))
    }

    @Test
    fun `title is centered inside widest tooltip line`() {
        assertEquals(130, ItemTooltipLayout.centeredTitleX(100, 80, 20))
        assertEquals(100, ItemTooltipLayout.centeredTitleX(100, 20, 80))
    }
}
