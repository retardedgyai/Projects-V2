package dev.projects.webui

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ForgeRasterCalibrationTest {
    private val output = Path.of("../assets/ui/polish05-design-review/shared-preview")

    @Test fun `pack glyphs and native scene use the same opaque tiles without resampling`() {
        val manifest = JsonParser.parseString(Files.readString(output.resolve("manifest.json"))).asJsonObject
        val sceneBuilder = ForgeRasterCalibration(output.resolve("manifest.json"))
        assertEquals(24, sceneBuilder.frameIds.size)
        val providers = JsonParser.parseString(Files.readString(output.resolve(
            "pack/assets/projects_forge_calibration/font/plates.json"))).asJsonObject.getAsJsonArray("providers")
            .associate { it.asJsonObject.getAsJsonArray("chars")[0].asString to it.asJsonObject }
        for (frame in manifest.getAsJsonArray("frames")) {
            val f = frame.asJsonObject
            val scene = sceneBuilder.scene(f.get("id").asString)
            val sprites = scene.nodes.filter { it.sprite != null }
            val scale = 800.0 / f.get("width").asDouble
            assertEquals(f.getAsJsonArray("tiles").size(), sprites.size)
            assertTrue(sprites.size <= 40, "Retained glyph count must stay bounded")
            for (node in sprites) {
                val glyph = requireNotNull(node.sprite)
                val provider = providers.getValue(glyph.char)
                assertEquals(glyph.height, provider.get("height").asInt)
                assertEquals(glyph.height, provider.get("ascent").asInt)
                assertEquals(glyph.width * scale, node.box.w, 1e-8)
                assertEquals(glyph.height * scale, node.box.h, 1e-8)
                assertTrue(node.box.x >= 0 && node.box.y >= 0)
                assertTrue(node.box.x + node.box.w <= 800.000001 && node.box.y + node.box.h <= 480.000001)
                val file = provider.get("file").asString.substringAfter(':')
                assertTrue(Files.isRegularFile(output.resolve("pack/assets/projects_forge_calibration/textures/$file")))
            }
        }
    }

    @Test fun `hit locations share the tile coordinate transform and unknown frames fail`() {
        val builder = ForgeRasterCalibration(output.resolve("manifest.json"))
        val scene = builder.scene("weapon-focused")
        val hit = scene.nodes.single { it.action == "calibration:enhance" }
        assertEquals(hit.id, scene.hit(hit.box.x + hit.box.w/2, hit.box.y + hit.box.h/2)?.id)
        assertFailsWith<IllegalArgumentException> { builder.scene("live-account") }
        val short = builder.scene("shortage")
        assertTrue(short.nodes.none { it.action == "calibration:enhance" })
    }

    @Test fun `isolated flow changes display fixtures without accepting gameplay actions`() {
        val flow = ForgeRasterCalibrationFlow(ForgeRasterCalibration(output.resolve("manifest.json")))
        assertTrue(flow.action("calibration:select:weapon"))
        assertTrue(flow.action("calibration:catalyst"))
        assertEquals("weapon-focused", flow.frameId)
        assertTrue(flow.action("calibration:enhance"))
        assertEquals("confirm-weapon-focused", flow.frameId)
        assertTrue(flow.action("calibration:cancel"))
        assertEquals("weapon-focused", flow.frameId)
        assertTrue(!flow.action("confirm"))
        assertTrue(!flow.action("calibration:select:live-account"))
        assertTrue(flow.muted && !flow.operationActive)
        assertEquals("calibration", flow.view)
    }
}
