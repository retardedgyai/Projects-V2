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
            val sprites = scene.nodes.filter { it.id.startsWith("calibration-tile-") }
            val scale = 800.0 / f.get("width").asDouble / f.get("rasterScale").asDouble
            assertEquals(f.getAsJsonArray("tiles").size(), sprites.size)
            assertEquals(112, sprites.size, "2x raster uses bounded 256px glyphs without reducing source pixels")
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

    @Test fun `motion preserves fixed pixels and hits while drifting hero and motes inside the stage`() {
        val builder = ForgeRasterCalibration(output.resolve("manifest.json"))
        val first = builder.scene("weapon", 0L)
        val later = builder.scene("weapon", 1400L)
        assertEquals(first.nodes.filter { it.id.startsWith("calibration-tile-") || it.action != null },
            later.nodes.filter { it.id.startsWith("calibration-tile-") || it.action != null })
        val hero = first.nodes.first { it.id.startsWith("calibration-hero-") }
        val moved = later.nodes.single { it.id == hero.id }
        assertEquals(hero.box.y + 3 * 800.0 / 1700, moved.box.y, 1e-8)
        assertEquals(hero.sprite, moved.sprite)
        assertEquals(5, first.nodes.count { it.id.startsWith("calibration-mote-") })
        assertTrue(first.nodes.filter { it.id.startsWith("calibration-glow-") }.map { it.sprite } !=
            later.nodes.filter { it.id.startsWith("calibration-glow-") }.map { it.sprite })
        assertEquals(first.nodes.filter { it.id.startsWith("calibration-glow-") }.map { it.box },
            later.nodes.filter { it.id.startsWith("calibration-glow-") }.map { it.box }, "Light must breathe without growing")
        // All periods wrap exactly. No accumulating random motion or advancing hits.
        assertEquals(first, builder.scene("weapon", 672000L))
        assertTrue(builder.scene("confirm-weapon", 1400L).nodes.none {
            it.id.startsWith("calibration-hero-") || it.id.startsWith("calibration-mote-") || it.id.startsWith("calibration-glow-")
        })
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
