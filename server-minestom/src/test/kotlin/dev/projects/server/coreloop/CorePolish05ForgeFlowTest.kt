package dev.projects.server.coreloop

import dev.projects.webui.Polish05Scene
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CorePolish05ForgeFlowTest {
    private fun scene(): Polish05Scene {
        val loader=javaClass.classLoader
        return Polish05Scene(requireNotNull(loader.getResourceAsStream("polish05/forge_initial.json")).readBytes(),
            requireNotNull(loader.getResourceAsStream("polish05/font-map.json")).readBytes())
    }

    @Test fun liveAccountValuesReplacePreviewAndConfirmationUsesRevision() {
        val id=UUID.randomUUID()
        val empty=CoreAccount(id,silver=123)
        val recipe=CoreEnhancementCatalog.quote(empty,CoreGearSlot.WEAPON).recipe
        var current=empty.copy(balances=recipe.costs.mapValues { it.value+7 })
        var request: Triple<CoreGearSlot,CoreEnhancementMode,Long>?=null
        val flow=CorePolish05ForgeFlow(scene(),{current},{true}) { gear, mode, revision, done ->
            request=Triple(gear,mode,revision)
            done(true)
        }
        val display=flow.scene()
        fun text(id:String)=display.nodes.single { it.id==id }.text
        assertEquals("123",text("wallet"))
        assertEquals(recipe.costs.keys.first().displayName,text("cost-name-0"))
        assertEquals("100.0%",text("chance-value"))
        assertFalse(display.nodes.any { it.text.contains("12,480") || it.text.contains("陽鉱の塊") })
        assertTrue(flow.action("enhance"))
        assertTrue(flow.scene().nodes.any { it.id=="modal-confirm" })
        assertTrue(flow.action("confirm"))
        assertEquals(Triple(CoreGearSlot.WEAPON,CoreEnhancementMode.STANDARD,current.revision),request)
        assertNotNull(flow.tick())
    }

    @Test fun shortageCannotOpenConfirmation() {
        val flow=CorePolish05ForgeFlow(scene(),{CoreAccount(UUID.randomUUID())},{true}) { _,_,_,_ -> error("must not transact") }
        assertFalse(flow.scene().nodes.any { it.action=="enhance" })
        assertTrue(flow.action("enhance"))
        assertFalse(flow.scene().nodes.any { it.id=="modal-confirm" })
    }

    @Test fun fourArmorRowsSelectAndConfirmTheirOwnSlot() {
        val id = UUID.randomUUID()
        val empty = CoreAccount(id)
        val costs = CoreEnhancementCatalog.quote(empty, CoreGearSlot.HEAD).recipe.costs
        val current = empty.copy(balances = costs.mapValues { it.value + 5 })
        var dispatched: CoreGearSlot? = null
        val flow = CorePolish05ForgeFlow(scene(), { current }, { true }) { slot, _, _, done ->
            dispatched = slot
            done(true)
        }
        val initial = flow.scene()
        assertEquals(5, initial.nodes.count { it.id.startsWith("live-gear-hit-") })
        val armorIcons = (1..4).map { initial.nodes.single { node -> node.id == "live-gear-icon-$it" }.item }
        assertEquals(4, armorIcons.distinct().size)
        for (part in listOf("helmet", "chestplate", "leggings", "boots")) {
            assertNotNull(javaClass.classLoader.getResource("core-ui-pack/assets/projects/items/armor/warrior_t1_$part.json"))
        }
        assertTrue(flow.action("select:head"))
        assertTrue(flow.scene().nodes.single { it.id == "hero-name" }.text.contains("頭"))
        assertEquals(armorIcons.first(), flow.scene().nodes.single { it.id == "hero-weapon" }.item)
        assertTrue(flow.action("enhance"))
        assertTrue(flow.scene().nodes.any { it.id == "modal-confirm" })
        assertEquals(armorIcons.first(), flow.scene().nodes.single { it.id == "modal-icon" }.item)
        assertTrue(flow.action("confirm"))
        assertEquals(CoreGearSlot.HEAD, dispatched)
    }
}
