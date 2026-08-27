package dev.projects.client

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InventoryCharacterReviewIconsTest {
    @Test
    fun `temporary review set contains all menu and stat icons`() {
        val icons = inventoryCharacterReviewMenuIcons + inventoryCharacterReviewStatIcons

        assertEquals(7, inventoryCharacterReviewMenuIcons.size)
        assertEquals(22, inventoryCharacterReviewStatIcons.size)
        assertEquals(29, icons.size)
        assertEquals(29, icons.map { it.assetName }.distinct().size)
    }

    @Test
    fun `temporary review assets are transparent native 32px pngs`() {
        (inventoryCharacterReviewMenuIcons + inventoryCharacterReviewStatIcons).forEach { icon ->
            val path = "/assets/projects/textures/gui/review/icons32/${icon.assetName}.png"
            val stream = assertNotNull(javaClass.getResourceAsStream(path), "Missing review asset: $path")
            val image: BufferedImage = stream.use { assertNotNull(ImageIO.read(it), "Invalid PNG: $path") }

            assertEquals(INVENTORY_CHARACTER_REVIEW_ICON_SIZE, image.width, path)
            assertEquals(INVENTORY_CHARACTER_REVIEW_ICON_SIZE, image.height, path)
            assertTrue(image.colorModel.hasAlpha(), "Review asset must retain transparency: $path")
            assertTrue(image.hasVisiblePixel(), "Review asset must not be fully transparent: $path")
        }
    }

    @Test
    fun `standard gui viewport displays every stat icon at native size`() {
        val detail = inventoryCharacterLayout(836, 470).detail
        val placements = inventoryCharacterReviewIconPlacements(detail, inventoryCharacterReviewStatIcons.size)

        assertEquals(22, placements.size)
        placements.forEach { icon ->
            assertEquals(INVENTORY_CHARACTER_REVIEW_ICON_SIZE, icon.width)
            assertEquals(INVENTORY_CHARACTER_REVIEW_ICON_SIZE, icon.height)
            assertTrue(icon.x >= detail.x)
            assertTrue(icon.y >= detail.y)
            assertTrue(icon.x + icon.width <= detail.x + detail.width)
            assertTrue(icon.y + icon.height <= detail.y + detail.height)
        }
    }

    private fun BufferedImage.hasVisiblePixel(): Boolean = (0 until height).any { y ->
        (0 until width).any { x -> getRGB(x, y) ushr 24 != 0 }
    }
}
