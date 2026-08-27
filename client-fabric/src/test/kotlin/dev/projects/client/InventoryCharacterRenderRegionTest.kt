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
    fun `texture blit region keeps integer target and source pixels`() {
        val region = inventoryCharacterTextureRegion(8, 8, 18, 18, 0, 0, 24, 24)

        assertEquals(8, region.x)
        assertEquals(8, region.y)
        assertEquals(18, region.width)
        assertEquals(18, region.height)
        assertEquals(0, region.sourceX)
        assertEquals(0, region.sourceY)
        assertEquals(24, region.sourceWidth)
        assertEquals(24, region.sourceHeight)
        assertTrue(region.width > 0)
        assertTrue(region.height > 0)
        assertTrue(region.sourceWidth > 0)
        assertTrue(region.sourceHeight > 0)
    }
}
