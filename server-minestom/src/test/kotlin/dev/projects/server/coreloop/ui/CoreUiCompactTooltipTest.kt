package dev.projects.server.coreloop.ui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreUiCompactTooltipTest {
    private fun plain(component: Component): String = (component as? TextComponent)?.content().orEmpty() +
        component.children().joinToString("") { plain(it) }

    private val affixes = listOf(
        CoreTooltipAffix("接頭 · 剛力", "攻撃力 +22%", "17〜22%", 100, "R4"),
        CoreTooltipAffix("接頭 · 技力", "スキルダメージ +29%", "23〜29%", 100, "R4"),
        CoreTooltipAffix("接頭 · 火炎", "火属性値 +8", "8〜10", 0, "R4"),
        CoreTooltipAffix("接尾 · 会心", "クリティカル率増加 +50%", "40〜50%", 100, "R4"),
        CoreTooltipAffix("接尾 · 痛撃", "クリティカル倍率 +30%", "23〜30%", 100, "R4"),
        CoreTooltipAffix("接尾 · 集中", "クールダウン短縮 +10%", "9〜11%", 50, "R4"),
    )
    private val model = CoreTooltipModel("T4 開拓者の大剣", CoreUiRarity.RARE, 4, 46, "レア / 両手剣",
        stats = listOf(
            CoreTooltipStat("攻撃力", "92", CoreUiIcon.ATTACK),
            CoreTooltipStat("攻撃速度", "+18%", CoreUiIcon.SPEED),
            CoreTooltipStat("会心率 / 倍率", "7.5% / 180%", CoreUiIcon.CRITICAL),
            CoreTooltipStat("炎 / 氷 / 雷", "8 / 0 / 0", CoreUiIcon.MAGIC)),
        affixes = affixes, modCapacity = 6,
        footer = listOf("能力欄は装備全体のMODを反映", "マジック：接頭1＋接尾1 / レア：接頭3＋接尾3",
            "左：斬撃 / 右：踏み込み / F：回避", "港でオーブを重ねるか、刻印工房へ"))

    @Test fun `six affix weapon fits twenty four lines without hiding any rolled information`() {
        for (packed in listOf(false, true)) {
            val rendered = CoreUiTooltip.render(model, packed)
            val lines = rendered.lore.map(::plain)
            assertTrue(rendered.lore.size + 1 <= 24, "Name plus all lore must fit the small-GUI budget")
            assertTrue(rendered.contentWidth <= 310, "Compact rows must not become extra-wide paragraphs")
            for (affix in affixes) {
                val line = lines.single { affix.effect in it }
                assertTrue("範囲 ${affix.range}" in line)
                assertTrue("品質 ${affix.qualityPercent}%" in line)
                assertTrue(affix.rank in line)
            }
            assertTrue(lines.any { "アイテムレベル 46" in it && "内部Tier T4" in it })
            assertTrue(lines.containsAll(model.footer))
            assertFalse(lines.any { "Shift" in it })
            if (!packed) assertFalse(lines.any { line -> line.any { it.code in 0xE000..0xF8FF } })
        }
    }

    @Test fun `three to six affixes use one line per effect instead of one section per affix`() {
        for (count in 3..6) {
            val subset = model.copy(affixes = affixes.take(count))
            val lines = CoreUiTooltip.render(subset, true).lore.map(::plain)
            assertEquals(count, lines.count { "範囲 " in it })
            assertFalse(lines.any { line -> subset.affixes.any { it.name in line } })
            assertTrue(lines.size + 1 <= 24)
        }
    }

    @Test fun `small affix lists retain their named presentation and quality is still bounded`() {
        val single = model.copy(affixes = listOf(affixes.first().copy(qualityPercent = 150)), modCapacity = 2)
        val lines = CoreUiTooltip.render(single, true).lore.map(::plain)
        assertTrue(lines.any { "接頭 · 剛力" in it })
        assertTrue(lines.any { "品質 100%" in it })
        assertFalse(lines.any { "品質 150%" in it })
        val compact = model.copy(affixes = affixes.map { it.copy(qualityPercent = -10) })
        assertEquals(6, CoreUiTooltip.render(compact, false).lore.map(::plain).count { "品質 0%" in it })
    }
}
