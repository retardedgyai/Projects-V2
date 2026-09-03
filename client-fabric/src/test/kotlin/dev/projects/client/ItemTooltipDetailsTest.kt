package dev.projects.client

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemTooltipDetailsTest {
    @Test
    fun `advanced lines are only visible while shift is held`() {
        val advanced = "${ItemTooltipDetails.ADVANCED_ONLY_MARKER}ロール範囲"
        assertFalse(ItemTooltipDetails.shouldDisplay(advanced, shiftDown = false))
        assertTrue(ItemTooltipDetails.shouldDisplay(advanced, shiftDown = true))
    }

    @Test
    fun `compact hint disappears while shift is held`() {
        val compact = "${ItemTooltipDetails.COMPACT_ONLY_MARKER}SHIFT 詳細表示"
        assertTrue(ItemTooltipDetails.shouldDisplay(compact, shiftDown = false))
        assertFalse(ItemTooltipDetails.shouldDisplay(compact, shiftDown = true))
        assertTrue(ItemTooltipDetails.shouldDisplay("通常表示", shiftDown = false))
        assertTrue(ItemTooltipDetails.shouldDisplay("通常表示", shiftDown = true))
    }
}
