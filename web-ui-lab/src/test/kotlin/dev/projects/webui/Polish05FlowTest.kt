package dev.projects.webui

import dev.projects.webui.polish05.Polish05PreviewModel.Material
import dev.projects.webui.polish05.Polish05PreviewModel
import dev.projects.webui.polish05.Polish05ScreenSpace
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class Polish05FlowTest {
    private val repo=Path.of("..").toAbsolutePath().normalize()
    private val scene=Polish05Scene(repo.resolve("assets/ui/polish05-import"),Path.of("ui/polish05-font-map.json"))

    @Test fun approvedFrameAndWeaponShareTheHitTransform() {
        val flow=Polish05Flow(scene)
        val nodes=flow.scene().nodes
        val screen=Polish05ScreenSpace(800.0,480.0)
        val sword=nodes.single { it.id=="hero-weapon" }
        assertEquals(276*screen.scale,sword.box.h,0.0001)
        assertEquals(screen.forward(652.5,365.0).x,sword.box.x,0.0001)
        val button=nodes.single { it.action=="enhance" }
        assertEquals(screen.forward(1019.0,710.0).x,button.box.x,0.0001)
        assertEquals("enhance",flow.scene().hit(button.box.x+button.box.w/2,button.box.y+button.box.h/2)?.action)
        assertTrue(nodes.any { it.sprite?.font=="projects_ui_polish05:plates" })
        assertTrue(nodes.any { it.sprite?.font=="projects_ui_polish05:sprites" })
        // These approved bitmaps have baked-in labels. Live text must not be drawn on top.
        assertFalse(nodes.any { it.id.startsWith("replenish-bg-") && it.sprite!=null })
        assertTrue(nodes.any { it.id.startsWith("enhance_button/") })
        val buttonTiles=nodes.filter { it.id.startsWith("enhance_button/") }
        assertEquals(326*screen.scale,buttonTiles.sumOf { it.box.w },0.0001)
        assertFalse(nodes.any { it.id=="enhance-label" })
        assertTrue(nodes.single { it.id=="tab-label-forge" }.sprite!=null)
        // The CSS-rendered Georgia glyph has transparent padding for its blur.
        val nextLevel=nodes.single { it.id=="after-level" }
        val currentLevel=nodes.single { it.id=="before-level" }
        assertTrue(currentLevel.sprite!=null && currentLevel.text.isEmpty())
        assertTrue(nextLevel.sprite!=null && nextLevel.text.isEmpty())
        assertEquals(screen.forward(1230.0,273.0).x,nextLevel.box.x,0.0001)
        assertTrue(Files.exists(repo.resolve("web-ui-lab/ui/polish05-effects/next_level_7.png")))
        assertTrue(Files.exists(repo.resolve("web-ui-lab/ui/polish05-effects/current_level_6.png")))
    }

    @Test fun approvedLightPhasesAndEmbersRemainSeparateFromClickableScene() {
        val flow=Polish05Flow(scene)
        assertTrue(flow.scene(ForgeLightPhase.STRIKING).nodes.any {
            it.id.startsWith("tile-forge_environment_striking/") })
        assertTrue(flow.scene(ForgeLightPhase.RESULT_WARM).nodes.any {
            it.id.startsWith("tile-forge_environment_result_warm/") })
        val effects=Polish05Effects()
        effects.beginStrike(1_000)
        assertEquals(ForgeLightPhase.STRIKING,effects.phase(1_100))
        val base=flow.scene()
        val frame=effects.frame(base,1_000)
        val embers=frame.nodes.filter { it.id.startsWith("ember-") }
        assertTrue(embers.size>=30)
        assertTrue(embers.all { it.action==null && it.background?.length==9 && it.depth<20 })
        assertEquals(base.hit(0.0,0.0),frame.hit(0.0,0.0))
        val movingSword=effects.frame(base,1_270).nodes.single { it.id=="hero-weapon" }
        assertEquals(base.nodes.single { it.id=="hero-weapon" }.box.y+2*Polish05ScreenSpace(800.0,480.0).scale,
            movingSword.box.y,0.0001)
        effects.finish(true,1_720)
        assertEquals(ForgeLightPhase.RESULT_WARM,effects.phase(1_800))
        assertEquals(ForgeLightPhase.IDLE,effects.phase(2_821))
        effects.clear()
        assertFalse(effects.frame(base,3_000).nodes.any { it.id.startsWith("ember-") && it.id in embers.map(UiNode::id) })
    }

    @Test fun approvedWarmPlatesFollowSelectionAndCatalystState() {
        val flow=Polish05Flow(scene)
        val initial=flow.scene().nodes
        val selected=initial.single { it.id=="gear-plate-0" }.sprite
        val unselected=initial.single { it.id=="gear-plate-1" }.sprite
        assertNotNull(selected)
        assertNotNull(unselected)
        assertNotEquals(selected.char,unselected.char)
        assertTrue(initial.any { it.id=="tab-forge" && it.sprite!=null })
        val catalystOff=initial.single { it.id=="catalyst-halo-0" }.sprite
        assertTrue(flow.action("select:ash"))
        val changed=flow.scene().nodes
        assertEquals(unselected.char,changed.single { it.id=="gear-plate-0" }.sprite?.char)
        assertEquals(selected.char,changed.single { it.id=="gear-plate-1" }.sprite?.char)
        assertTrue(flow.action("catalyst"))
        assertNotEquals(catalystOff?.char,flow.scene().nodes.single { it.id=="catalyst-halo-0" }.sprite?.char)
        assertEquals("✓",flow.scene().nodes.single { it.id=="catalyst-check" }.text)
        assertTrue(flow.action("view:bag"))
        assertTrue(flow.scene().nodes.any { it.id=="inventory-tab" && it.sprite!=null })
        assertTrue(flow.action("view:refine"))
        assertTrue(flow.scene().nodes.any { it.id=="refine-tab" && it.sprite!=null })
    }

    @Test fun forgeOnlyScreenKeepsLiveMaterialStatusAndDistinctShortageLight() {
        val model=Polish05PreviewModel()
        val initial=scene.forge(model).nodes
        assertTrue(initial.any { it.id=="result-level-halo-0" && it.sprite!=null })
        assertTrue(initial.any { it.id=="catalyst-halo-0" && it.sprite!=null })
        val chromeDepth=initial.first { it.id.startsWith("tile-window_chrome/") }.depth
        assertTrue(initial.single { it.id=="result-level-halo-0" }.depth>chromeDepth)
        assertTrue(initial.single { it.id=="before-level" }.depth>initial.single { it.id=="result-level-halo-0" }.depth)
        assertTrue(initial.single { it.id=="cost-name-0" }.depth>initial.single { it.id=="cost-halo-0-0" }.depth)
        assertTrue(initial.mapNotNull { it.sprite }.all { it.width<=256 && it.height<=256 })
        assertTrue(initial.any { it.id=="replenish-count-0" && it.text.contains("所持 60個") })
        assertFalse(initial.any { it.action=="compare" || it.action?.startsWith("view:")==true ||
            it.action?.startsWith("refine:")==true })
        assertFalse(initial.any { it.id.startsWith("tab-label-bag") || it.id.startsWith("tab-label-refine") })
        val ready=initial.single { it.id=="cost-halo-0-0" }.sprite?.char
        assertNotNull(ready)
        repeat(2) {
            val quote=assertNotNull(model.quoteEnhancement())
            val operation=assertNotNull(model.begin(quote.id,0L,reducedMotion=true))
            assertNotNull(model.finish(operation.id,operation.finishAtMs))
        }
        val depleted=scene.forge(model).nodes
        assertNotEquals(ready,depleted.single { it.id=="cost-halo-0-0" }.sprite?.char)
        assertEquals(ready,depleted.single { it.id=="cost-halo-1-0" }.sprite?.char)
        assertTrue(depleted.single { it.id=="cost-after-0" }.text.contains("不足"))
        assertFalse(depleted.any { it.action=="enhance" })
    }

    @Test fun selectCompareConfirmConsumeRefineAndReturnToSameGear() {
        val flow=Polish05Flow(scene)
        assertTrue(flow.action("select:ash"))
        assertEquals("ash",flow.model.snapshot().selected)
        assertEquals(240*Polish05ScreenSpace(800.0,480.0).scale,flow.scene().nodes.single { it.id=="hero-weapon" }.box.h,0.0001)
        assertTrue(flow.action("compare"))
        assertEquals("bag",flow.view)
        assertTrue(flow.compare)
        assertFalse(flow.scene().nodes.any { it.action=="bag:equip" })
        assertTrue(flow.action("cancel"))
        assertTrue(flow.action("bag:ember"))
        assertTrue(flow.action("bag:forge"))
        assertEquals("ember",flow.model.snapshot().selected)
        assertTrue(flow.action("enhance"))
        assertTrue(flow.modal)
        assertFalse(flow.scene().nodes.any { it.action=="select:ash" })
        assertFalse(flow.scene().nodes.any { it.id=="hero-weapon" })
        assertTrue(flow.scene().nodes.single { it.id=="modal-panel" }.depth >
            scene.forge(flow.model).nodes.single { it.id=="hero-weapon" }.depth+5)
        assertTrue(flow.action("confirm"))
        val op=assertNotNull(flow.operation)
        assertEquals(36,flow.model.snapshot().materials.getValue(Material.ORE))
        assertFalse(flow.action("confirm"))
        assertEquals(null,flow.tick(op.finishAtMs-1))
        assertTrue(assertNotNull(flow.tick(op.finishAtMs)).success)
        assertEquals(7,flow.model.snapshot().gears.single { it.id=="ember" }.level)
        assertTrue(flow.scene().nodes.any { it.id=="enhance-label" && it.text=="続けて強化する" })
        assertFalse(flow.scene().nodes.any { it.id.startsWith("enhance_button/") })
        assertTrue(flow.action("refine:ore"))
        assertEquals("refine",flow.view)
        assertEquals("ember",flow.model.snapshot().selected)
        assertTrue(flow.action("batch:max"))
        assertEquals(11,flow.batch)
        assertTrue(flow.action("refine:quote"))
        assertTrue(flow.modal)
        assertFalse(flow.scene().nodes.any { it.action=="view:forge" })
        assertTrue(flow.action("confirm"))
        val refine=assertNotNull(flow.operation)
        assertNotNull(flow.tick(refine.finishAtMs))
        assertEquals(47,flow.model.snapshot().materials.getValue(Material.ORE))
        assertEquals(0,flow.model.snapshot().materials.getValue(Material.RAW_ORE))
        assertTrue(flow.action("view:forge"))
        assertEquals("ember",flow.model.snapshot().selected)
        assertEquals("forge",flow.view)
    }
}
