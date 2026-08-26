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
        assertEquals(162, layout.inventoryGrid.width)
        assertEquals(76, layout.inventoryGrid.height)
        assertTrue(layout.detail.x > layout.inventory.x + layout.inventory.width)
        assertEquals(4, layout.equipmentSlots.size)
        assertTrue(EquipmentSlot.CHEST in layout.equipmentSlots)
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
