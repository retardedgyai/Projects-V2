package dev.projects.server.coreloop.ui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreMenuCanvasTest {
    private fun components(value: Component): List<Component> = listOf(value) + value.children().flatMap(::components)
    private fun contents(value: Component): String = components(value).filterIsInstance<TextComponent>().joinToString("") { it.content() }

    @Test fun `all menu fonts are private and do not replace default Japanese`() {
        val canvas = CoreMenuCanvas("開拓工房 / 強化")
        canvas.left("装備の変化", listOf(CoreMenuCanvas.Line("攻撃 42 → 46")))
        canvas.right("必要な素材", listOf(CoreMenuCanvas.Line("木材 T1"), CoreMenuCanvas.Line("12 / 80")))
        canvas.button(0, 2, "強化", CoreMenuCanvas.Tone.SELECTED)
        val render = canvas.render()
        assertTrue(contents(render).all { it.code in 0xE000..0xF8FF })
        assertTrue(components(render).filterIsInstance<TextComponent>().filter { it.content().isNotEmpty() }
            .all { it.style().font()?.namespace() == "projects" })
        assertTrue(CoreMenuCanvas.missingCharacters("開拓工房 / 強化 +30 攻撃 42 → 46 装備の変化 必要な素材 □").isEmpty())
    }

    @Test fun `panel replacement does not append stale material or result data`() {
        val replacement = CoreMenuCanvas("工房").apply {
            left("古い表示", listOf(CoreMenuCanvas.Line("不足")))
            left("現在の表示", listOf(CoreMenuCanvas.Line("完了")))
        }
        val fresh = CoreMenuCanvas("工房").apply { left("現在の表示", listOf(CoreMenuCanvas.Line("完了"))) }
        assertEquals(fresh.render(), replacement.render())
    }

    @Test fun `buttons remain within one vanilla row and cannot overlap`() {
        val canvas = CoreMenuCanvas("工房")
        canvas.button(0, 2, "強化")
        canvas.button(0, 2, "強化", CoreMenuCanvas.Tone.SELECTED)
        assertFailsWith<IllegalArgumentException> { canvas.button(1, 2, "精製") }
        assertFailsWith<IllegalArgumentException> { canvas.button(8, 2, "不正") }
        assertFailsWith<IllegalArgumentException> { canvas.button(54, 1, "不正") }
        assertFailsWith<IllegalArgumentException> { canvas.button(9, 0, "不正") }
        canvas.button(9, 9, "素材を補充")
    }

    @Test fun `critical panel overflow is explicit and labels truncate visibly`() {
        assertFailsWith<IllegalArgumentException> {
            CoreMenuCanvas("工房").right("必要素材", List(14) { CoreMenuCanvas.Line("木材") })
        }
        val label = "必要な素材を保管庫から使用します"
        assertTrue(CoreMenuCanvas.trim(label, 88).endsWith("…"))
        assertTrue(CoreMenuCanvas.width(CoreMenuCanvas.trim(label, 88)) <= 88)
        assertEquals("", CoreMenuCanvas.trim(label, 1))
        assertEquals("木材", CoreMenuCanvas.trim("木材", 88))
        assertTrue(CoreMenuCanvas.width("金属材") <= 34, "A three-kanji category should fit a two-slot button")
    }

    @Test fun `wrapping preserves content and Unicode codepoints`() {
        val sentence = "素材が足りないときは精製して戻る"
        val lines = CoreMenuCanvas.wrap(sentence, 55)
        assertEquals(sentence, lines.joinToString(""))
        assertTrue(lines.all { CoreMenuCanvas.width(it) <= 55 })
        assertEquals(listOf("", "木材", ""), CoreMenuCanvas.wrap("\n木材\n"))
        val supplementary = "\uD83D\uDDE1木材\uD83D\uDDE1"
        assertEquals(supplementary, CoreMenuCanvas.wrap(supplementary, 22).joinToString(""))
        assertEquals(setOf(0x1F5E1), CoreMenuCanvas.missingCharacters(supplementary))
        assertFalse(CoreMenuCanvas.trim(supplementary, 22).any { it == '\uFFFD' })
    }

    @Test fun `only the useful rows are uploaded and arbitrary text positions snap predictably`() {
        for (row in 0..5) assertEquals(20 + row * 18, CoreMenuCanvas.snapY(20 + row * 18))
        for (row in 0..12) assertEquals(30 + row * 14, CoreMenuCanvas.snapY(30 + row * 14))
        assertEquals(128, CoreMenuCanvas.snapY(128))
        assertTrue(CoreMenuCanvas.TEXT_YS.size <= 23)
        assertFailsWith<IllegalArgumentException> { CoreMenuCanvas("工房").text(250, 30, "不正", maxWidth = 88) }
        assertFailsWith<IllegalArgumentException> { CoreMenuCanvas("工房").text(0, 222, "不正") }
    }

    @Test fun `snapshot contains actual Unicode and RGB values without mutable references`() {
        val canvas = CoreMenuCanvas("工房")
        canvas.left("材料", listOf(CoreMenuCanvas.Line("木材 1 / 2", CoreUiComponents.RED)))
        canvas.button(9, 3, "木材", CoreMenuCanvas.Tone.DANGER, icon = true)
        canvas.text(8, 19, "素材を補充", maxWidth = 160)
        val snapshot = canvas.snapshot()
        assertEquals("工房", snapshot.title)
        assertEquals(CoreUiComponents.GOLD.value(), snapshot.titleColor)
        assertEquals(CoreUiComponents.RED.value(), snapshot.leftPanel!!.lines.single().color)
        assertEquals("木材 1 / 2", snapshot.leftPanel.lines.single().text)
        assertEquals("DANGER", snapshot.buttons.single().tone)
        assertTrue(snapshot.buttons.single().icon)
        assertEquals(20, snapshot.texts.single().y)
        canvas.left("材料", listOf(CoreMenuCanvas.Line("木材 2 / 2")))
        assertEquals("木材 1 / 2", snapshot.leftPanel.lines.single().text)
        assertEquals("木材 2 / 2", canvas.snapshot().leftPanel!!.lines.single().text)
    }

    @Test fun `pack declined information retains every untruncated source line but excludes action labels`() {
        val original = "必要な素材を保管庫から使って強化します"
        val canvas = CoreMenuCanvas("工房").apply {
            left("選択内容", listOf(CoreMenuCanvas.Line(original)))
            right("必要素材", listOf(CoreMenuCanvas.Line("木材 1000000 / 256")))
            text(8, 38, "追加の説明", maxWidth = 160)
            button(51, 3, "強化する", CoreMenuCanvas.Tone.PRIMARY)
        }
        val fallback = canvas.fallbackLines()
        assertEquals(listOf("工房", "", "選択内容", original, "", "必要素材", "木材 1000000 / 256", "", "追加の説明"), fallback)
        assertFalse(fallback.any { it.contains('…') || it.any { char -> char.code in 0xE000..0xF8FF } })
        assertFalse("強化する" in fallback)
    }
}
