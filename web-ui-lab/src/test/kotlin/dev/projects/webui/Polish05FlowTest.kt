package dev.projects.webui

import dev.projects.webui.polish05.Polish05PreviewModel.Material
import dev.projects.webui.polish05.Polish05ScreenSpace
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
        assertTrue(flow.action("confirm"))
        val op=assertNotNull(flow.operation)
        assertEquals(36,flow.model.snapshot().materials.getValue(Material.ORE))
        assertFalse(flow.action("confirm"))
        assertEquals(null,flow.tick(op.finishAtMs-1))
        assertTrue(assertNotNull(flow.tick(op.finishAtMs)).success)
        assertEquals(7,flow.model.snapshot().gears.single { it.id=="ember" }.level)
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
