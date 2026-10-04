package dev.projects.server.coreloop

import dev.projects.webui.ForgeV3Scene
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CorePolish05ForgeFlowTest {
    private fun scene() = ForgeV3Scene(requireNotNull(javaClass.classLoader.getResourceAsStream("polish05/font-map.json")).readBytes())
    private var now = 10_000L

    private fun stocked(account: CoreAccount, slot: CoreGearSlot, mode: CoreEnhancementMode = CoreEnhancementMode.STANDARD) =
        account.copy(balances = account.balances + CoreEnhancementCatalog.quote(account, slot, mode).recipe.costs.mapValues { it.value + 7 })

    @Test fun liveValuesAndChargedRevealUseTheLedgerRevision() {
        val empty = CoreAccount(UUID.randomUUID(), silver = 123)
        var current = stocked(empty, CoreGearSlot.WEAPON)
        val recipe = CoreEnhancementCatalog.quote(current, CoreGearSlot.WEAPON).recipe
        var request: Triple<CoreGearSlot, CoreEnhancementMode, Long>? = null
        val flow = CorePolish05ForgeFlow(scene(), { current }, { true }, { now }) { gear, mode, revision, done ->
            request = Triple(gear, mode, revision)
            current = current.copy(weaponEnhancement = CoreEnhancementState(1))
            done(true)
        }
        val display = flow.scene()
        fun text(id: String) = display.nodes.single { it.id == id }.text
        assertEquals("123", text("wallet"))
        assertEquals(recipe.costs.keys.first().resource.displayName, text("cost-name-0"))
        assertEquals("100.0", text("chance-value"))
        assertTrue(display.nodes.any { it.sprite?.font == "projects_ui_polish05:v3plates" && it.id.startsWith("chrome-") })
        assertTrue(display.nodes.filter { it.text.isNotEmpty() }.all { it.style["font-family"]?.startsWith("projects_ui_polish05:v3") == true })
        assertFalse(display.nodes.any { it.text.contains("12,480") || it.text.contains("熾火") })

        val revision = current.revision
        assertTrue(flow.action("enhance"))
        assertEquals(Triple(CoreGearSlot.WEAPON, CoreEnhancementMode.STANDARD, revision), request)
        assertTrue(flow.operationActive)
        // The ledger already moved to +1, but the item is still charging and shows +0.
        assertEquals("+0", flow.scene().nodes.single { it.id == "level-now" }.text)
        assertNull(flow.tick())
        now += 800
        assertNotNull(flow.tick()).also { assertTrue(it.success) }
        assertFalse(flow.operationActive)
        val after = flow.scene()
        assertEquals("+1", after.nodes.single { it.id == "level-now" }.text)
        assertTrue(after.nodes.any { it.id == "toast-title" && it.text.contains("+1") })
        assertTrue(after.nodes.any { it.id.startsWith("spark-") })
    }

    @Test fun shortageNeverStartsAnAttempt() {
        val flow = CorePolish05ForgeFlow(scene(), { CoreAccount(UUID.randomUUID()) }, { true }, { now }) { _, _, _, _ -> error("must not transact") }
        assertFalse(flow.scene().nodes.any { it.action == "enhance" })
        assertTrue(flow.action("enhance"))
        assertFalse(flow.operationActive)
        assertFalse(flow.scene().nodes.any { it.id == "modal-confirm" })
    }

    @Test fun fourArmorRowsSelectAndEnhanceTheirOwnSlot() {
        val current = stocked(CoreAccount(UUID.randomUUID()), CoreGearSlot.HEAD)
        var dispatched: CoreGearSlot? = null
        val flow = CorePolish05ForgeFlow(scene(), { current }, { true }, { now }) { slot, _, _, done ->
            dispatched = slot
            done(true)
        }
        val initial = flow.scene()
        assertEquals(5, initial.nodes.count { it.id.startsWith("row-hit-") && it.action != null })
        val armorIcons = (1..4).map { initial.nodes.single { node -> node.id == "gear-icon-$it" }.item }
        assertEquals(4, armorIcons.distinct().size)
        for (part in listOf("helmet", "chestplate", "leggings", "boots")) {
            assertNotNull(javaClass.classLoader.getResource("core-ui-pack/assets/projects/items/armor/warrior_t1_$part.json"))
        }
        assertTrue(flow.action("select:head"))
        assertEquals(armorIcons.first(), flow.scene().nodes.single { it.id == "hero-item" }.item)
        assertTrue(flow.action("enhance"))
        assertEquals(CoreGearSlot.HEAD, dispatched)
    }

    @Test fun breakRiskAsksBeforeTheAttempt() {
        val base = CoreAccount(UUID.randomUUID()).copy(weaponEnhancement = CoreEnhancementState(16))
        val current = stocked(base, CoreGearSlot.WEAPON)
        var calls = 0
        val flow = CorePolish05ForgeFlow(scene(), { current }, { true }, { now }) { _, _, _, done -> calls++; done(true) }
        val display = flow.scene()
        assertEquals("8.0%", display.nodes.single { it.id == "break-value" }.text)
        assertTrue(display.nodes.any { it.id == "cell-17-risk" || it.id == "cell-18-risk" })
        assertTrue(flow.action("enhance"))
        assertEquals(0, calls)
        assertTrue(flow.scene().nodes.any { it.id == "modal-confirm" })
        assertTrue(flow.action("confirm"))
        assertEquals(1, calls)
    }

    @Test fun guaranteedAttemptsCannotSpendTheCatalyst() {
        val current = stocked(CoreAccount(UUID.randomUUID()), CoreGearSlot.WEAPON)
        val flow = CorePolish05ForgeFlow(scene(), { current }, { true }, { now }) { _, _, _, _ -> }
        assertFalse(flow.action("catalyst"))
        assertFalse(flow.scene().nodes.any { it.action == "catalyst" })
    }
}
