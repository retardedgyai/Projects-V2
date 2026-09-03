package dev.projects.client

import com.google.gson.JsonParser
import java.awt.image.BufferedImage
import java.io.InputStream
import java.io.InputStreamReader
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ItemTooltipAssetResourceTest {
    @Test
    fun `required rarity tooltip sprites and metadata are valid`() {
        val frameMasks = mutableSetOf<List<Boolean>>()
        val backgroundPixels = mutableSetOf<List<Int>>()
        val frameBorders = mapOf("common" to 10, "uncommon" to 10, "rare" to 12, "epic" to 12)

        frameBorders.forEach { (rarity, expectedBorder) ->
            val background = image("item_${rarity}_background.png")
            val frame = image("item_${rarity}_frame.png")
            assertEquals(100, background.width)
            assertEquals(100, background.height)
            assertEquals(100, frame.width)
            assertEquals(100, frame.height)
            assertTrue(alpha(background.getRGB(50, 50)) > 0)
            assertEquals(0, alpha(frame.getRGB(50, 50)))

            backgroundPixels += pixels(background)
            frameMasks += pixels(frame).map { alpha(it) > 0 }
            assertMetadata(rarity, "background", expectedBorder = 9, stretchInner = false)
            assertMetadata(rarity, "frame", expectedBorder = expectedBorder, stretchInner = true)
        }

        assertEquals(4, backgroundPixels.size, "Rarity backgrounds must be distinct assets")
        assertEquals(4, frameMasks.size, "Rarity frames must have distinct silhouettes, not only different colors")
    }

    private fun image(fileName: String): BufferedImage = resource(fileName).use { stream ->
        assertNotNull(ImageIO.read(stream), "$fileName must be a readable PNG")
    }

    private fun assertMetadata(rarity: String, part: String, expectedBorder: Int, stretchInner: Boolean) {
        resource("item_${rarity}_${part}.png.mcmeta").use { stream ->
            val scaling = JsonParser.parseReader(InputStreamReader(stream)).asJsonObject
                .getAsJsonObject("gui")
                .getAsJsonObject("scaling")
            assertEquals("nine_slice", scaling.get("type").asString)
            assertEquals(100, scaling.get("width").asInt)
            assertEquals(100, scaling.get("height").asInt)
            assertEquals(expectedBorder, scaling.get("border").asInt)
            assertEquals(stretchInner, scaling.has("stretch_inner") && scaling.get("stretch_inner").asBoolean)
        }
    }

    private fun resource(fileName: String): InputStream {
        val path = "assets/projects/textures/gui/sprites/tooltip/$fileName"
        return assertNotNull(javaClass.classLoader.getResourceAsStream(path), "Missing $path")
    }

    private fun pixels(image: BufferedImage): List<Int> = buildList(image.width * image.height) {
        for (y in 0 until image.height) for (x in 0 until image.width) add(image.getRGB(x, y))
    }

    private fun alpha(argb: Int): Int = argb ushr 24 and 0xFF
}
