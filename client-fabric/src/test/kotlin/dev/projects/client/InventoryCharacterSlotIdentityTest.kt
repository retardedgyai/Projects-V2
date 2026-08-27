package dev.projects.client

import kotlin.test.Test
import kotlin.test.assertEquals

class InventoryCharacterSlotIdentityTest {
    @Test
    fun `visible identity covers armor main hotbar and offhand without crafting slots`() {
        val mapping = inventoryCharacterVisibleSlotMapping()

        assertEquals((5..45).toList(), mapping.map { it.menuIndex })
        assertEquals((39 downTo 36).toList(), mapping.filter { it.group == InventoryCharacterSlotGroup.ARMOR }.map { it.containerIndex })
        assertEquals((9..35).toList(), mapping.filter { it.group == InventoryCharacterSlotGroup.MAIN }.map { it.containerIndex })
        assertEquals((0..8).toList(), mapping.filter { it.group == InventoryCharacterSlotGroup.HOTBAR }.map { it.containerIndex })
        assertEquals(listOf(40), mapping.filter { it.group == InventoryCharacterSlotGroup.OFFHAND }.map { it.containerIndex })
    }
}
