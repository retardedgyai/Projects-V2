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
}
