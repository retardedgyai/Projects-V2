package dev.projects.webui

import kotlin.test.*

class ForgePreviewTest {
    private fun scene(item: String="minecraft:iron_sword") = UiScene(800.0,480.0,listOf(
        UiNode("hero-weapon",Box(300.0,150.0,200.0,200.0),"",emptyMap(),null,item,true,4),
        UiNode("preview-hit",Box(300.0,150.0,200.0,200.0),"",emptyMap(),"preview:toggle",null,true,10)))

    @Test fun stopFreezesAtClickAndSelectionOrModalResets() {
        val p=ForgePreview()
        p.sync(scene());p.toggle(400.0,240.0);p.move(430.0,250.0)
        assertTrue(p.rotating)
        assertEquals(36.0,p.yaw);assertEquals(10.0,p.pitch)
        p.toggle(440.0,260.0)
        assertFalse(p.rotating)
        p.move(700.0,400.0)
        assertEquals(48.0,p.yaw);assertEquals(20.0,p.pitch)
        p.sync(scene());assertEquals(48.0,p.yaw)
        p.toggle(700.0,400.0);p.move(700.0,0.0)
        assertEquals(-65.0,p.pitch)
        p.sync(scene("minecraft:leather_helmet"))
        assertFalse(p.rotating);assertEquals(0.0,p.yaw)
        p.toggle(400.0,240.0);p.move(450.0,250.0)
        p.sync(UiScene(800.0,480.0,emptyList()))
        assertFalse(p.rotating);assertEquals(0.0,p.pitch)
    }

    @Test fun quaternionIsUnitLengthAndReturnsToSameOrientationAfterFullTurn() {
        for(y in listOf(-359.0,-90.0,0.0,90.0,359.0)) {
            val q=previewQuaternion(y,45.0)
            assertEquals(1.0,q.sumOf { (it*it).toDouble() },.00001)
        }
        assertContentEquals(floatArrayOf(0f,0f,0f,1f),previewQuaternion(0.0,0.0))
    }
}
