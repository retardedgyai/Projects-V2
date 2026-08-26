package dev.projects.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InventoryCharacterRenderRegionTest {
    @Test
    fun `entity preview uses positive endpoint bounds`() {
        val bounds = inventoryCharacterPreviewBounds(HudRect(180, 94, 174, 382))

        assertEquals(180, bounds.x0)
        assertEquals(354, bounds.x1)
        assertEquals(94, bounds.y0)
        assertEquals(476, bounds.y1)
        assertTrue(bounds.x1 > bounds.x0)
        assertTrue(bounds.y1 > bounds.y0)
    }

    @Test
    fun `texture blit region uses positive endpoint bounds`() {
        val region = inventoryCharacterTextureRegion(8, 8, 889, 494, 0, 0, 24, 24)

        assertEquals(8, region.x0)
        assertEquals(897, region.x1)
        assertEquals(8, region.y0)
        assertEquals(502, region.y1)
        assertTrue(region.x1 > region.x0)
        assertTrue(region.y1 > region.y0)
        assertTrue(region.u1 > region.u0)
        assertTrue(region.v1 > region.v0)
    }
}
