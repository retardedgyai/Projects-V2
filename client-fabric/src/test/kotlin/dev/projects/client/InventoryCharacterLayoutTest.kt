package dev.projects.client

import net.minecraft.world.entity.EquipmentSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InventoryCharacterLayoutTest {
    @Test
    fun `wide layout keeps the detail column beside the inventory`() {
        val layout = inventoryCharacterLayout(1280, 720)

        assertFalse(layout.compact)
        assertFalse(layout.tiny)
        assertEquals(210, layout.inventoryGrid.width)
        assertEquals(90, layout.inventoryGrid.height)
        assertTrue(layout.detail.x > layout.inventory.x + layout.inventory.width)
        assertEquals(4, layout.equipmentSlots.size)
        assertTrue(EquipmentSlot.CHEST in layout.equipmentSlots)
    }

    @Test
    fun `canonical layout matches the approved mock canvas`() {
        val layout = inventoryCharacterLayout(1672, 941)

        assertEquals(HudRect(116, 166, 1438, 588), layout.panel)
        assertEquals(HudRect(142, 186, 192, 532), layout.rail)
        assertEquals(HudRect(346, 186, 356, 532), layout.character)
        assertEquals(HudRect(714, 186, 494, 532), layout.inventory)
        assertEquals(HudRect(1220, 186, 310, 532), layout.detail)
        assertEquals(90, layout.inventoryGrid.height)
        assertEquals(24, layout.slotStep)
        assertEquals(INVENTORY_CHARACTER_NAV_ROW_HEIGHT, layout.navRowHeight)
        val navLastBottom = layout.rail.y + layout.navTopPadding +
            6 * (layout.navRowHeight + layout.navRowGap) + layout.navRowHeight
        assertTrue(navLastBottom <= layout.rail.y + layout.rail.height)
    }

    @Test
    fun `canonical layout scales uniformly for a creator viewport`() {
        val layout = inventoryCharacterLayout(1280, 720)

        assertEquals(1100, layout.panel.width)
        assertEquals(450, layout.panel.height)
        assertEquals(20, layout.rail.x - layout.panel.x)
        assertEquals(15, layout.rail.y - layout.panel.y)
        assertTrue(layout.character.width.toFloat() / layout.panel.width in 0.24f..0.27f)
        assertTrue(layout.inventory.width.toFloat() / layout.panel.width in 0.33f..0.36f)
        assertTrue(layout.detail.width.toFloat() / layout.panel.width in 0.20f..0.24f)
    }

    @Test
    fun `gui scaled mock viewport still uses the canonical composition`() {
        val layout = inventoryCharacterLayout(836, 470)

        assertFalse(layout.tiny)
        assertEquals(24, layout.slotStep)
        assertTrue(layout.navRowHeight in 29..31)
        assertTrue(layout.panel.width in 700..720)
        assertTrue(layout.inventoryGrid.x >= layout.inventory.x)
        assertTrue(layout.inventoryGrid.x + layout.inventoryGrid.width <= layout.inventory.x + layout.inventory.width)
    }

    @Test
    fun `compact layout keeps the real nine-column grid inside the inventory panel`() {
        val layout = inventoryCharacterLayout(640, 360)

        assertTrue(layout.compact)
        assertFalse(layout.tiny)
        assertTrue(layout.inventoryGrid.x >= layout.inventory.x)
        assertTrue(layout.inventoryGrid.x + layout.inventoryGrid.width <= layout.inventory.x + layout.inventory.width)
        assertTrue(layout.inventoryGrid.y >= layout.inventory.y)
        assertTrue(layout.inventoryGrid.y + layout.inventoryGrid.height <= layout.inventory.y + layout.inventory.height)
    }

    @Test
    fun `tiny layout switches the rail to a horizontal header without losing the grid`() {
        val layout = inventoryCharacterLayout(320, 240)

        assertTrue(layout.tiny)
        assertEquals(layout.panel.x + 8, layout.rail.x)
        assertEquals(layout.panel.width - 16, layout.rail.width)
        assertTrue(layout.inventoryGrid.x >= layout.panel.x)
        assertTrue(layout.inventoryGrid.x + layout.inventoryGrid.width <= layout.panel.x + layout.panel.width)
    }
}
