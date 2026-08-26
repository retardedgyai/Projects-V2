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
    fun `skills resize keeps four runtime slots usable`() {
        val layout = HudLayoutConfig.defaults().elements[HudElementId.SKILLS]!!
        val resized = layout.resizedFor(HudElementId.SKILLS, 4, 22)

        assertEquals(HudElementLayout.SKILLS_MIN_WIDTH, resized.width)
        assertTrue(HudElementLayout.skillsSlotWidth(resized.width) > 0)
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

    @Test
    fun `snap aligns to screen center and other element edges`() {
        val selected = HudElementLayout(HudAnchorX.LEFT, HudAnchorY.TOP, 316, 97, 8, 8)
        val other = HudElementLayout(HudAnchorX.LEFT, HudAnchorY.TOP, 100, 100, 40, 20)
        val result = HudLayoutSnap.snap(
            HudElementId.SKILLS,
            selected,
            mapOf(HudElementId.SKILLS to selected, HudElementId.HP to other),
            640,
            360,
        )
        assertEquals(316, result.layout.resolve(640, 360).x)
        assertEquals(96, result.layout.resolve(640, 360).y)
        assertEquals(320, result.guideX)
        assertEquals(100, result.guideY)
    }

    @Test
    fun `snap mirrors another element around screen center`() {
        val selected = HudElementLayout(HudAnchorX.LEFT, HudAnchorY.TOP, 408, 40, 20, 10)
        val other = HudElementLayout(HudAnchorX.LEFT, HudAnchorY.TOP, 210, 40, 20, 10)
        val result = HudLayoutSnap.snap(
            HudElementId.RESOURCE,
            selected,
            mapOf(HudElementId.RESOURCE to selected, HudElementId.HP to other),
            640,
            360,
        )

        assertEquals(410, result.layout.resolve(640, 360).x)
        assertEquals(320, result.guideX)
    }

    @Test
    fun `snap only applies within two pixels`() {
        val selected = HudElementLayout(HudAnchorX.LEFT, HudAnchorY.TOP, 318, 40, 8, 8)
        val result = HudLayoutSnap.snap(
            HudElementId.SKILLS,
            selected,
            mapOf(HudElementId.SKILLS to selected),
            640,
            360,
        )

        assertEquals(316, result.layout.resolve(640, 360).x)

        val outside = selected.copy(offsetX = 319)
        val outsideResult = HudLayoutSnap.snap(
            HudElementId.SKILLS,
            outside,
            mapOf(HudElementId.SKILLS to outside),
            640,
            360,
        )
        assertEquals(319, outsideResult.layout.resolve(640, 360).x)
    }

    @Test
    fun `disabled snap keeps raw position within threshold`() {
        val selected = HudElementLayout(HudAnchorX.LEFT, HudAnchorY.TOP, 318, 40, 8, 8)
        val result = HudLayoutSnap.snap(
            HudElementId.SKILLS,
            selected,
            mapOf(HudElementId.SKILLS to selected),
            640,
            360,
            enabled = false,
        )

        assertEquals(318, result.layout.resolve(640, 360).x)
        assertEquals(null, result.guideX)
        assertEquals(null, result.guideY)
    }
}
