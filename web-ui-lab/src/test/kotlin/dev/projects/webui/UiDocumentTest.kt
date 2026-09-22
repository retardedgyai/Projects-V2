package dev.projects.webui

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class UiDocumentTest {
    private fun source()=Files.readString(Path.of("ui/forge.html"))
    @Test fun allStatesFitAndHitTheSameAuthoredButtons() {
        val doc=UiDocument.parse(source());val demo=ForgeDemo()
        for(tab in listOf("forge","catalog")) for(page in 0..1) {
            demo.action("tab:$tab");if(page!=demo.page)demo.action("page:next")
            val scene=doc.layout(demo.values(),demo.flags())
            assertEquals(800.0,scene.width);assertEquals(480.0,scene.height)
            assertTrue(scene.nodes.size<80)
            scene.nodes.forEach { assertTrue(it.box.x>=0 && it.box.y>=0 && it.box.x+it.box.w<=800 && it.box.y+it.box.h<=480) }
            scene.nodes.filter { it.action!=null }.forEach {
                assertEquals(it.id,scene.hit(it.box.x+it.box.w/2,it.box.y+it.box.h/2)?.id)
            }
        }
    }
    @Test fun forgeDebitsExactlyOnceThenDisablesInsufficientFunds() {
        val demo=ForgeDemo()
        assertTrue(demo.action("forge"));assertEquals(4,demo.level);assertEquals(6,demo.ore);assertEquals(1000,demo.coins)
        repeat(20){assertFalse(demo.action("forge"))}
        assertEquals(4,demo.level)
        val scene=UiDocument.parse(source()).layout(demo.values(),demo.flags())
        assertFalse(scene.nodes.single { it.id=="forge-button" }.enabled)
        assertTrue(demo.action("reset"));assertTrue(demo.affordable)
        assertFalse(demo.action("op @a"))
    }
    @Test fun rejectsUnsupportedCssScriptsAndExternalEntities() {
        assertFails { UiDocument.parse(source().replace("padding:20px","position:absolute")) }
        assertFails { UiDocument.parse(source().replace("<body>","<body><script>alert(1)</script>")) }
        assertFails { UiDocument.parse("<!DOCTYPE html [<!ENTITY x SYSTEM 'file:///secrets'>]>"+source()) }
        assertFails { UiDocument.parse(source().replace("class=\"screen\"","class=\"missing\"")).layout(emptyMap(),emptySet()) }
        assertFails { UiDocument.parse(source().replace("width:800px","width:NaNpx")) }
    }
    @Test fun rejectsOverflowAndDuplicateIdentifiers() {
        assertFails { UiDocument.parse(source().replace("height:300px","height:700px")).layout(ForgeDemo().values(),ForgeDemo().flags()) }
        assertFails { UiDocument.parse(source().replace("id=\"status\"","id=\"screen\"")).layout(ForgeDemo().values(),ForgeDemo().flags()) }
        assertFails { UiDocument.parse(source().replace("minecraft:iron_sword","minecraft:not_real")).layout(ForgeDemo().values(),ForgeDemo().flags()) }
    }
    @Test fun pointerWrapsYawClampsAndRejectsNonFiniteInput() {
        val p=UiPointer()
        p.move(179f,0f,800.0,480.0);p.move(-179f,1f,800.0,480.0)
        assertEquals(416.0,p.x);assertEquals(248.0,p.y)
        p.move(Float.NaN,0f,800.0,480.0);assertEquals(416.0,p.x)
        p.move(-120f,60f,800.0,480.0);assertTrue(p.x<800 && p.y<480)
        p.reset();p.move(0f,0f,800.0,480.0);assertTrue(p.x>0)
    }
    @Test fun nativePanelCornersProjectOntoTheAuthoredHitBoxAtEveryDepthAndZoom() {
        // Independent 26.2 native background vertices for the default-font single space.
        for(zoom in listOf(1.0,0.8,0.65)) for(depth in listOf(0.0,0.01,0.05,0.4,0.42)) {
            val geometry=UiGeometry(zoom)
            for(box in listOf(Box(0.0,0.0,800.0,480.0),Box(430.0,220.0,210.0,48.0),Box(317.0,150.0,2.0,12.0))) {
                val t=geometry.panel(box,depth)
                val left=(t.translation.x()-.05*t.scale.x())/geometry.unit(depth)+400
                val right=(t.translation.x()+.075*t.scale.x())/geometry.unit(depth)+400
                val top=240-(t.translation.y()+.25*t.scale.y())/geometry.unit(depth)
                val bottom=240-t.translation.y()/geometry.unit(depth)
                assertEquals(box.x,left,1e-8);assertEquals(box.x+box.w,right,1e-8)
                assertEquals(box.y,top,1e-8);assertEquals(box.y+box.h,bottom,1e-8)
            }
        }
    }
}
