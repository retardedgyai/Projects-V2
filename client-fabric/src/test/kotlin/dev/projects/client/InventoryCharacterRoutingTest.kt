package dev.projects.client

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InventoryCharacterRoutingTest {
    @Test
    fun `non ProjectS survival keeps vanilla inventory routing`() {
        assertFalse(
            shouldOpenInventoryCharacterScreen(
                projectSProtocolSessionActive = false,
                screenOpen = false,
                infiniteMaterials = false,
                serverControlledInventory = false,
            ),
        )
    }

    @Test
    fun `ProjectS survival routes the inventory key`() {
        assertTrue(
            shouldOpenInventoryCharacterScreen(
                projectSProtocolSessionActive = true,
                screenOpen = false,
                infiniteMaterials = false,
                serverControlledInventory = false,
            ),
        )
    }

    @Test
    fun `vanilla screen guards remain active for ProjectS`() {
        assertFalse(shouldOpenInventoryCharacterScreen(true, screenOpen = true, infiniteMaterials = false, serverControlledInventory = false))
        assertFalse(shouldOpenInventoryCharacterScreen(true, screenOpen = false, infiniteMaterials = true, serverControlledInventory = false))
        assertFalse(shouldOpenInventoryCharacterScreen(true, screenOpen = false, infiniteMaterials = false, serverControlledInventory = true))
    }
}
