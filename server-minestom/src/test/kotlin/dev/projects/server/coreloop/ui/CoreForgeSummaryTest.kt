package dev.projects.server.coreloop.ui

import dev.projects.server.coreloop.*
import java.util.UUID
import kotlin.test.*

class CoreForgeSummaryTest {
    private fun account(vararg balances: Pair<CoreMaterial, Long>) = CoreAccount(UUID.randomUUID(), balances = mapOf(*balances))

    @Test fun `costs expose required owned and shortage at the same time in recipe order`() {
        val ingot = CoreMaterial(CoreResource.INGOT, 2)
        val board = CoreMaterial(CoreResource.BOARD, 2)
        val recipe = CoreRecipe("試験", linkedMapOf(ingot to 12L, board to 6L), emptyMap())
        val rows = CoreForgeSummary.materials(account(ingot to 9L, board to 64L), recipe)
        assertEquals(listOf(ingot, board), rows.map { it.material })
        assertEquals(12, rows[0].required)
        assertEquals(9, rows[0].owned)
        assertEquals(3, rows[0].missing)
        assertFalse(rows[0].satisfied)
        assertEquals(0, rows[1].missing)
        assertTrue(rows[1].satisfied)
    }

    @Test fun `empty balances are actual zero rather than fake visible owned inventory`() {
        val summary = CoreForgeSummary.recipe(account(), CoreLoopCatalog.refine(CoreResource.WOOD, 4, 5))
        assertEquals(0, summary.materials.single().owned)
        assertEquals(10, summary.materials.single().missing)
        assertEquals("T4 木材が10個不足", summary.blockedReason)
    }

    @Test fun `craft output is its real yield and full storage blocks the current batch`() {
        val cloth = CoreMaterial(CoreResource.CLOTH, 3)
        val potion = CoreMaterial(CoreResource.POTION)
        val a = account(cloth to 20L, potion to CoreLoopCatalog.MAX_BALANCE - 3)
        val one = CoreForgeSummary.recipe(a, CoreLoopCatalog.craft(CoreResource.POTION, 1, 3))
        assertNull(one.blockedReason)
        assertEquals(2, one.outputs.single().produced)
        assertEquals(3, one.outputs.single().remainingCapacity)
        assertTrue(one.outputs.single().fits)
        val five = CoreForgeSummary.recipe(a, CoreLoopCatalog.craft(CoreResource.POTION, 5, 3))
        assertFalse(five.outputs.single().fits)
        assertEquals("T1 回復薬の保管上限を超えます", five.blockedReason)
    }

    @Test fun `maximum balance remains exact without abbreviated or overflowing counts`() {
        val wood = CoreMaterial(CoreResource.WOOD)
        val board = CoreMaterial(CoreResource.BOARD)
        val summary = CoreForgeSummary.recipe(account(wood to CoreLoopCatalog.MAX_BALANCE, board to CoreLoopCatalog.MAX_BALANCE),
            CoreRecipe("試験", mapOf(wood to Long.MAX_VALUE), mapOf(board to Long.MAX_VALUE)))
        assertEquals(Long.MAX_VALUE - CoreLoopCatalog.MAX_BALANCE, summary.materials.single().missing)
        assertEquals(0, summary.outputs.single().remainingCapacity)
        assertFalse(summary.outputs.single().fits)
    }

    @Test fun `required batch advice rounds up without silently claiming affordability`() {
        assertEquals(0, CoreForgeSummary.requiredBatches(0, 4))
        assertEquals(1, CoreForgeSummary.requiredBatches(1, 4))
        assertEquals(2, CoreForgeSummary.requiredBatches(5, 4))
        assertEquals(64, CoreForgeSummary.requiredBatches(1_000_000, 1))
        assertEquals(64, CoreForgeSummary.requiredBatches(Long.MAX_VALUE, 2))
        assertEquals(1, CoreForgeSummary.requiredBatches(Long.MAX_VALUE, Long.MAX_VALUE))
        assertFailsWith<IllegalArgumentException> { CoreForgeSummary.requiredBatches(-1, 1) }
        assertFailsWith<IllegalArgumentException> { CoreForgeSummary.requiredBatches(1, 0) }
    }

    @Test fun `summary uses chosen armor mode and canonical quote rather than an invented preview`() {
        val a = account().copy(weaponEnhancement = CoreEnhancementState(2), armorEnhancement = CoreEnhancementState(12, 2))
        val selected = CoreForgeLayout.Selection(gear = CoreGearSlot.ARMOR, focused = true)
        val summary = CoreForgeSummary.enhancement(a, selected)
        val actual = CoreEnhancementCatalog.quote(a, CoreGearSlot.ARMOR, CoreEnhancementMode.FOCUSED)
        assertEquals(actual, summary.quote)
        assertEquals("+12 → +13", summary.levelLabel)
        assertEquals("成功率 60%", summary.successLabel)
        assertEquals("失敗しても保護・天井 2/6", summary.protectionLabel)
        assertEquals(actual.recipe.costs, summary.materials.associate { it.material to it.required })
    }

    @Test fun `guaranteed enhancement hides irrelevant catalyst costs even with focused selected`() {
        val summary = CoreForgeSummary.enhancement(account(), CoreForgeLayout.Selection(focused = true))
        assertEquals("+0 → +1", summary.levelLabel)
        assertEquals("成功率 100%・確定", summary.successLabel)
        assertEquals(0.0, summary.quote.catalystBonusPercent)
        assertTrue(summary.materials.none { it.material.resource == CoreResource.AFFIX_DUST })
    }

    @Test fun `max enhancement does not show a false zero percent attempt or fake next level`() {
        val summary = CoreForgeSummary.enhancement(account().copy(weaponEnhancement = CoreEnhancementState(30)),
            CoreForgeLayout.Selection(focused = true))
        assertEquals("+30（最大）", summary.levelLabel)
        assertEquals("最大まで強化済み", summary.successLabel)
        assertTrue(summary.materials.isEmpty())
        assertNotNull(summary.quote.blockedReason)
    }

    @Test fun `summary cannot enable the forge away from the hub even if materials are present`() {
        val a = account(CoreMaterial(CoreResource.WOOD) to 50L).copy(activeRun = CoreActiveRun(UUID.randomUUID(),
            CoreOwnedMap(UUID.randomUUID(), 1L, 1)))
        assertEquals("拠点で操作してください", CoreForgeSummary.recipe(a, CoreLoopCatalog.refine(CoreResource.WOOD, 1)).blockedReason)
    }
}
