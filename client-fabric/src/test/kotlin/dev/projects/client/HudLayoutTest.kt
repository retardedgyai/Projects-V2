package dev.projects.client

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HudLayoutTest {
    @Test
    fun `center bottom resolves and remains inside 640x360`() {
        val layout = HudElementLayout(HudAnchorX.CENTER, HudAnchorY.BOTTOM, 0, 22, 182, 22)
        val rect = layout.resolve(640, 360)
        assertEquals(HudRect(229, 316, 182, 22), rect)
        assertTrue(rect.x >= 0 && rect.y >= 0)
    }

    @Test
    fun `moving outside clamps and recalculates anchored offsets`() {
        val layout = HudElementLayout(HudAnchorX.CENTER, HudAnchorY.BOTTOM, 0, 10, 80, 12)
        val moved = layout.movedTo(-100, 500, 640, 360)
        assertEquals(HudRect(0, 348, 80, 12), moved.resolve(640, 360))
    }

    @Test
    fun `resize clamps invalid dimensions`() {
        val resized = HudElementLayout(HudAnchorX.LEFT, HudAnchorY.TOP, 0, 0, 20, 20).resized(0, -3)
        assertEquals(HudElementLayout.MIN_SIZE, resized.width)
        assertEquals(HudElementLayout.MIN_SIZE, resized.height)
    }

    @Test
    fun `config round trips through local store`() {
        val directory = Files.createTempDirectory("projects-hud-test")
        val store = HudLayoutStore(directory.resolve("config/projects/hud-layout.json"))
        val config = HudLayoutConfig.defaults()
        config.elements[HudElementId.HP] = HudElementLayout(HudAnchorX.RIGHT, HudAnchorY.TOP, 7, 9, 55, 11)
        store.save(config)
        assertEquals(config, store.load())
    }
}
