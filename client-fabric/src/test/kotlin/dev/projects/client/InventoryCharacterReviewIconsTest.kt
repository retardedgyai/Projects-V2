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
    fun `temporary review keeps 32px masters and adds nearest neighbor 16px stats`() {
        (inventoryCharacterReviewMenuIcons + inventoryCharacterReviewStatIcons).forEach { icon ->
            readReviewAsset("icons32", icon.assetName, INVENTORY_CHARACTER_REVIEW_MASTER_SIZE)
        }
        inventoryCharacterReviewStatIcons.forEach { icon ->
            val master = readReviewAsset("icons32", icon.assetName, INVENTORY_CHARACTER_REVIEW_MASTER_SIZE)
            val stat = readReviewAsset("icons16", icon.assetName, INVENTORY_CHARACTER_REVIEW_STAT_ICON_SIZE)
            val masterColors = master.visibleColors()

            assertTrue(
                stat.visibleColors().all { it in masterColors },
                "16px review asset must not introduce interpolated colors: ${icon.assetName}",
            )
        }
    }

    @Test
    fun `standard gui viewport displays every stat icon at native 16px`() {
        val detail = inventoryCharacterLayout(836, 470).detail
        val placements = inventoryCharacterReviewIconPlacements(detail, inventoryCharacterReviewStatIcons.size)

        assertEquals(22, placements.size)
        placements.forEach { icon ->
            assertEquals(INVENTORY_CHARACTER_REVIEW_STAT_ICON_SIZE, icon.width)
            assertEquals(INVENTORY_CHARACTER_REVIEW_STAT_ICON_SIZE, icon.height)
            assertTrue(icon.x >= detail.x)
            assertTrue(icon.y >= detail.y)
            assertTrue(icon.x + icon.width <= detail.x + detail.width)
            assertTrue(icon.y + icon.height <= detail.y + detail.height)
        }
    }

    private fun readReviewAsset(directory: String, assetName: String, expectedSize: Int): BufferedImage {
        val path = "/assets/projects/textures/gui/review/$directory/$assetName.png"
        val stream = assertNotNull(javaClass.getResourceAsStream(path), "Missing review asset: $path")
        val image: BufferedImage = stream.use { assertNotNull(ImageIO.read(it), "Invalid PNG: $path") }

        assertEquals(expectedSize, image.width, path)
        assertEquals(expectedSize, image.height, path)
        assertTrue(image.colorModel.hasAlpha(), "Review asset must retain transparency: $path")
        assertTrue(image.hasVisiblePixel(), "Review asset must not be fully transparent: $path")
        return image
    }

    private fun BufferedImage.hasVisiblePixel(): Boolean = (0 until height).any { y ->
        (0 until width).any { x -> getRGB(x, y) ushr 24 != 0 }
    }

    private fun BufferedImage.visibleColors(): Set<Int> = buildSet {
        repeat(height) { y ->
            repeat(width) { x ->
                getRGB(x, y).takeIf { it ushr 24 != 0 }?.let(::add)
            }
        }
    }
}
