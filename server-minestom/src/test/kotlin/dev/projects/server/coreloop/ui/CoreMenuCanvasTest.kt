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
    private fun fontMetrics(name: String): Map<Int, Pair<Char, Int>> = requireNotNull(javaClass.classLoader.getResourceAsStream(
        "core-ui-pack/assets/projects/menu/$name.tsv")).bufferedReader(Charsets.UTF_8).useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith('#') }.associate { line ->
            val fields = line.split('\t')
            fields[0].toInt(16) to (fields[1].toInt(16).toChar() to fields[2].toInt())
        }
    }

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
        assertTrue(CoreMenuCanvas.width("金属材", CoreMenuCanvas.TextStyle.EMPHASIS) <= 34, "A three-kanji category should fit a two-slot button")
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
        assertEquals(CoreMenuCanvas.HEADING.value(), snapshot.titleColor)
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

    @Test fun `panel wrapping keeps Latin names and complete numeric expressions together`() {
        for (unit in listOf("Tier", "MOD", "1000000/256", "+15.8%", "-12.5%", "+/-10%", "HP/MP")) {
            val value = "所持素材 $unit を確認"
            val wrapped = CoreMenuCanvas.wrap(value)
            assertTrue(wrapped.any { unit in it }, "The unit $unit was split across lines: $wrapped")
            assertEquals(value, wrapped.joinToString(""))
            assertTrue(wrapped.all { CoreMenuCanvas.width(it) <= CoreMenuCanvas.PANEL_WIDTH })
        }
        assertEquals(CoreMenuCanvas.wrap("MODの種類とTierを保持"), CoreMenuCanvas.wrap("MODの種類とTierを保持", style = CoreMenuCanvas.TextStyle.EMPHASIS))
    }

    @Test fun `oversized ASCII runs fall back safely without erasing explicit empty lines`() {
        val longWord = "SuperLongUnbrokenInventoryIdentifier1234567890/+100.25%"
        val text = "\n$longWord\n\n木材"
        val wrapped = CoreMenuCanvas.wrap(text, 44)
        assertEquals("", wrapped.first())
        assertEquals("木材", wrapped.last())
        assertTrue(wrapped.all { CoreMenuCanvas.width(it) <= 44 })
        assertEquals(longWord + "木材", wrapped.joinToString(""))
        assertTrue(wrapped.count { it.isEmpty() } >= 2)
        val unicode = "\uD83D\uDDE1Tier木材\uD83D\uDDE1"
        assertEquals(unicode, CoreMenuCanvas.wrap(unicode, 44).joinToString(""))
    }

    @Test fun `cards reserve their entire rectangle including interior slots`() {
        val canvas = CoreMenuCanvas("遠征")
        canvas.card(9, 3, 3, "遠征", CoreMenuArt.EXPEDITION)
        canvas.card(9, 3, 3, "遠征", CoreMenuArt.EXPEDITION, CoreMenuCanvas.Tone.SELECTED)
        val expected = listOf(9, 10, 11, 18, 19, 20, 27, 28, 29)
        assertEquals(expected, canvas.snapshot().cards.single().occupiedSlots)
        for (slot in expected) assertFailsWith<IllegalArgumentException>("Interior slot $slot must not acquire another action") {
            canvas.button(slot, 1, "不正")
        }
        assertFailsWith<IllegalArgumentException> { canvas.card(20, 3, 2, "工房", CoreMenuArt.FORGE) }
        assertFailsWith<IllegalArgumentException> { canvas.card(0, 3, 2, "工房", CoreMenuArt.FORGE) }
        canvas.card(12, 3, 3, "工房", CoreMenuArt.FORGE)
        canvas.button(36, 3, "戻る")
        assertFailsWith<IllegalArgumentException> { canvas.card(36, 3, 1, "戻る", CoreMenuArt.RETURN) }
        assertEquals(2, canvas.snapshot().cards.size)
    }

    @Test fun `card bounds and replacement cannot overwrite neighboring actions`() {
        val canvas = CoreMenuCanvas("工房")
        for ((slot, columns, rows) in listOf(Triple(-1, 3, 2), Triple(54, 3, 2), Triple(8, 2, 2),
            Triple(45, 3, 2), Triple(0, 0, 2), Triple(0, 3, 0), Triple(0, 3, 4), Triple(0, 1, 3))) {
            assertFailsWith<IllegalArgumentException> { canvas.card(slot, columns, rows, "不正", CoreMenuArt.HELP) }
        }
        canvas.card(0, 3, 2, "遠征", CoreMenuArt.EXPEDITION)
        canvas.button(18, 3, "戻る")
        assertFailsWith<IllegalArgumentException> { canvas.card(0, 3, 3, "遠征", CoreMenuArt.EXPEDITION) }
        assertEquals(2, canvas.snapshot().cards.single().rows)
        assertFailsWith<IllegalArgumentException> { canvas.card(45, 1, 1, "戻る", CoreMenuArt.RETURN) }
        canvas.card(45, 3, 1, "戻る", CoreMenuArt.RETURN)
    }

    @Test fun `card snapshot exports exact slot geometry and label positions`() {
        val canvas = CoreMenuCanvas("保管庫")
        canvas.card(9, 3, 3, "遠征", CoreMenuArt.EXPEDITION, CoreMenuCanvas.Tone.SELECTED)
        canvas.card(12, 4, 2, "木材 100万", CoreMenuArt.WOOD)
        canvas.card(36, 3, 1, "工房", CoreMenuArt.FORGE)
        val cards = canvas.snapshot().cards
        with(cards[0]) {
            assertEquals(8, x); assertEquals(36, y)
            assertEquals(52, width); assertEquals(52, height)
            assertEquals(74, labelY); assertEquals(52, labelMaxWidth)
            assertEquals(CoreMenuCanvas.ArtSnapshot(18, 36, "EXPEDITION", 32), artPlacement)
            assertEquals(0xF4D59A, textColor)
        }
        with(cards[1]) {
            assertEquals(70, width); assertEquals(34, height)
            assertEquals(56, labelY); assertEquals(70, labelMaxWidth)
            assertEquals(16, artPlacement.size)
            assertTrue(CoreMenuCanvas.width(label, CoreMenuCanvas.TextStyle.EMPHASIS) <= labelMaxWidth)
        }
        with(cards[2]) {
            assertEquals(34, labelMaxWidth); assertEquals(92, labelY)
            assertEquals(x, artPlacement.x); assertEquals(y, artPlacement.y)
            assertTrue(labelX >= x + 18)
        }
        for (card in cards) {
            assertTrue(card.artPlacement.x >= card.x && card.artPlacement.x + card.artPlacement.size <= card.x + card.width)
            assertTrue(card.artPlacement.y >= card.y && card.artPlacement.y + card.artPlacement.size <= card.y + card.height)
            assertTrue(card.labelY in CoreMenuCanvas.TEXT_YS)
            assertTrue(card.artPlacement.y in CoreMenuCanvas.ART_YS)
        }
    }

    @Test fun `panel hero reserves space and material icons leave full width value rows`() {
        val canvas = CoreMenuCanvas("工房")
        canvas.left("装備", List(10) { CoreMenuCanvas.Line("攻撃 42") }, CoreMenuArt.WEAPON)
        canvas.right("必要素材", listOf(CoreMenuCanvas.Line("木材 T1", art = CoreMenuArt.WOOD),
            CoreMenuCanvas.Line("1000000 / 256"), CoreMenuCanvas.Line("金属材", art = CoreMenuArt.INGOT)))
        val snapshot = canvas.snapshot()
        with(snapshot.leftPanel!!) {
            assertEquals(CoreMenuCanvas.ArtSnapshot(-70, 30, "WEAPON", 32), hero)
            assertEquals(72, lines.first().y); assertEquals(198, lines.last().y)
        }
        with(snapshot.rightPanel!!) {
            assertEquals(null, hero)
            assertEquals(202, lines[0].x); assertEquals(70, lines[0].maxWidth)
            assertEquals(CoreMenuCanvas.ArtSnapshot(184, 28, "WOOD", 16), lines[0].art)
            assertEquals(184, lines[1].x); assertEquals(88, lines[1].maxWidth)
            assertEquals(null, lines[1].art)
            assertTrue(lines.mapNotNull { it.art }.all { it.y in CoreMenuCanvas.ART_YS })
        }
        assertFailsWith<IllegalArgumentException> {
            canvas.left("装備", List(11) { CoreMenuCanvas.Line("攻撃 42") }, CoreMenuArt.WEAPON)
        }
        assertFailsWith<IllegalArgumentException> {
            canvas.right("素材", listOf(CoreMenuCanvas.Line("木材", art = CoreMenuArt.WOOD), CoreMenuCanvas.Line("原石", art = CoreMenuArt.ORE)))
        }
        canvas.left("装備", List(13) { CoreMenuCanvas.Line("攻撃 42") })
        assertEquals(null, canvas.snapshot().leftPanel!!.hero)
        assertEquals(30, canvas.snapshot().leftPanel!!.lines.first().y)
    }

    @Test fun `decorative art is bounded and only uses shipped rows and sizes`() {
        val canvas = CoreMenuCanvas("工房")
        canvas.art(-70, 30, CoreMenuArt.FORGE, 32)
        canvas.art(184, 196, CoreMenuArt.WOOD)
        val previous = canvas.snapshot()
        canvas.art(220, 30, CoreMenuArt.ARMOR, 32)
        assertEquals(2, previous.arts.size)
        assertEquals(3, canvas.snapshot().arts.size)
        assertEquals(CoreMenuCanvas.ArtSnapshot(184, 196, "WOOD", 16), previous.arts.last())
        for ((x, y, size) in listOf(Triple(-99, 30, 32), Triple(250, 30, 32), Triple(0, 196, 32),
            Triple(0, 182, 48), Triple(0, 30, 64), Triple(0, 31, 16), Triple(0, -1, 16))) {
            assertFailsWith<IllegalArgumentException> { canvas.art(x, y, CoreMenuArt.HELP, size) }
        }
    }

    @Test fun `art glyph metrics preserve every subject without changing later label anchors`() {
        assertEquals(29, CoreMenuArt.entries.size)
        for ((index, art) in CoreMenuArt.entries.withIndex()) {
            assertEquals(0xE700 + index, art.glyph.code)
            assertTrue(art.advance(16) in 1..17)
            assertTrue(art.advance(32) in 1..33)
            assertTrue(art.advance(48) in 1..49)
        }
        val canvas = CoreMenuCanvas("工房").apply {
            card(9, 3, 3, "強化", CoreMenuArt.ENHANCE, CoreMenuCanvas.Tone.SELECTED)
            left("装備", listOf(CoreMenuCanvas.Line("攻撃 42")), CoreMenuArt.WEAPON)
            right("必要素材", listOf(CoreMenuCanvas.Line("木材", art = CoreMenuArt.WOOD), CoreMenuCanvas.Line("12 / 80")))
            art(212, 140, CoreMenuArt.ORE, 32)
            text(8, 20, "結果を確認", maxWidth = 160)
        }
        val render = canvas.render()
        val glyphs = components(render).filterIsInstance<TextComponent>().filter { it.content().isNotEmpty() }
        assertTrue(glyphs.all { it.content().all { char -> char.code in 0xE000..0xF8FF } && it.style().font()?.namespace() == "projects" })
        assertTrue(glyphs.any { it.style().font()?.value() == "core_menu_cards_3_1" && it.content() == "\uE65B" })
        assertTrue(glyphs.any { it.style().font()?.value() == "core_menu_art_32_36" && it.content() == CoreMenuArt.ENHANCE.glyph.toString() })
        assertTrue(glyphs.any { it.style().font()?.value() == "core_menu_art_16_28" && it.content() == CoreMenuArt.WOOD.glyph.toString() })
        assertEquals(listOf("工房", "", "装備", "攻撃 42", "", "必要素材", "木材", "12 / 80", "", "結果を確認"), canvas.fallbackLines())
    }

    @Test fun `body and emphasis share 16px metrics and matching glyph ordinals`() {
        val body = fontMetrics("glyphs")
        val emphasis = fontMetrics("glyphs-emphasis")
        assertEquals(body.keys, emphasis.keys)
        for ((codepoint, entry) in body) assertEquals(entry.first, emphasis.getValue(codepoint).first)
        val sample = "木材 123 / 強化 +30"
        assertEquals(sample.codePoints().toArray().sumOf { body.getValue(it).second }, CoreMenuCanvas.width(sample))
        assertEquals(sample.codePoints().toArray().sumOf { emphasis.getValue(it).second },
            CoreMenuCanvas.width(sample, CoreMenuCanvas.TextStyle.EMPHASIS))
        assertEquals(body, emphasis)
        assertEquals(CoreMenuCanvas.width(sample), CoreMenuCanvas.width(sample, CoreMenuCanvas.TextStyle.EMPHASIS))
        for (style in CoreMenuCanvas.TextStyle.entries) {
            val text = "必要な素材は保管庫から使用します"
            val trimmed = CoreMenuCanvas.trim(text, 44, style)
            assertTrue(trimmed.endsWith('…'))
            assertTrue(CoreMenuCanvas.width(trimmed, style) <= 44)
            val wrapped = CoreMenuCanvas.wrap(text, 44, style)
            assertEquals(text, wrapped.joinToString(""))
            assertTrue(wrapped.all { CoreMenuCanvas.width(it, style) <= 44 })
        }
    }

    @Test fun `hierarchy keeps body calm while headings and actions use emphasis`() {
        val canvas = CoreMenuCanvas("工房").apply {
            left("装備", listOf(CoreMenuCanvas.Line("攻撃 42"),
                CoreMenuCanvas.Line("攻撃 46", style = CoreMenuCanvas.TextStyle.EMPHASIS)))
            button(0, 2, "強化", CoreMenuCanvas.Tone.PRIMARY)
            card(9, 3, 1, "木材", CoreMenuArt.WOOD)
            text(8, 56, "素材", maxWidth = 106)
            text(8, 72, "結果", maxWidth = 106, style = CoreMenuCanvas.TextStyle.EMPHASIS)
        }
        val snapshot = canvas.snapshot()
        assertEquals(listOf("BODY", "EMPHASIS"), snapshot.leftPanel!!.lines.map { it.style })
        assertEquals(listOf("BODY", "EMPHASIS"), snapshot.texts.map { it.style })
        assertEquals(0xEAD9BA, snapshot.titleColor)
        assertEquals(0xFFF0CE, snapshot.buttons.single().textColor)
        assertEquals(0xD6CBB7, snapshot.cards.single().textColor)
        val fonts = components(canvas.render()).filterIsInstance<TextComponent>()
            .filter { it.content().isNotEmpty() }.mapNotNull { it.style().font()?.value() }
        for (font in listOf("core_menu_emphasis_y6", "core_menu_emphasis_y8", "core_menu_y30",
            "core_menu_emphasis_y44", "core_menu_emphasis_y20", "core_menu_emphasis_y38", "core_menu_y56", "core_menu_emphasis_y72")) {
            assertTrue(font in fonts, "Missing hierarchy font $font")
        }
    }

    @Test fun `equipment focus has exact pedestal art caption geometry and original fallback`() {
        val caption = "選択中の武器の強化結果を確認します"
        val canvas = CoreMenuCanvas("工房").apply { focus(CoreMenuArt.WEAPON, caption) }
        val snapshot = canvas.snapshot().focus!!
        assertEquals(8, snapshot.x); assertEquals(44, snapshot.y)
        assertEquals(106, snapshot.width); assertEquals(64, snapshot.height)
        assertEquals(CoreMenuCanvas.ArtSnapshot(37, 54, "WEAPON", 48), snapshot.artPlacement)
        assertEquals(caption, snapshot.caption)
        assertEquals("EMPHASIS", snapshot.style)
        assertEquals(100, snapshot.captionY)
        assertEquals(106, snapshot.captionMaxWidth)
        assertTrue(snapshot.artPlacement.y >= 54, "Hero must not paint over gear selectors")
        assertTrue(snapshot.artPlacement.y + snapshot.artPlacement.size <= 108, "Hero cell must finish before footer controls")
        val visible = CoreMenuCanvas.trim(caption, 106, CoreMenuCanvas.TextStyle.EMPHASIS)
        assertEquals(8 + (106 - CoreMenuCanvas.width(visible, CoreMenuCanvas.TextStyle.EMPHASIS)) / 2, snapshot.captionX)
        assertEquals(listOf(18, 19, 20, 21, 22, 23, 27, 28, 29, 30, 31, 32, 36, 37, 38, 39, 40, 41), snapshot.reservedSlots)
        assertEquals(listOf("工房", "", caption), canvas.fallbackLines())
        val rendered = components(canvas.render()).filterIsInstance<TextComponent>().filter { it.content().isNotEmpty() }
        assertTrue(rendered.any { it.content() == "\uE6F0" && it.style().font()?.value() == "core_menu_focus" })
        assertTrue(rendered.any { it.content() == CoreMenuArt.WEAPON.glyph.toString() && it.style().font()?.value() == "core_menu_art_48_54" })
        assertTrue(rendered.any { it.style().font()?.value() == "core_menu_emphasis_y100" })
        canvas.focus(CoreMenuArt.ARMOR, "防具")
        assertEquals("WEAPON", snapshot.artPlacement.art)
        assertEquals("ARMOR", canvas.snapshot().focus!!.artPlacement.art)
        assertEquals(listOf("工房", "", "防具"), canvas.fallbackLines())
    }

    @Test fun `equipment focus and actions cannot collide regardless of insertion order`() {
        val canvas = CoreMenuCanvas("工房").apply { focus(CoreMenuArt.WEAPON, "武器") }
        for (slot in CoreMenuCanvas.FOCUS_SLOTS) {
            assertFailsWith<IllegalArgumentException> { canvas.button(slot, 1, "不正") }
            assertFailsWith<IllegalArgumentException> { canvas.card(slot, 1, 1, "", CoreMenuArt.HELP) }
        }
        canvas.button(0, 3, "強化")
        canvas.card(15, 3, 1, "詳細", CoreMenuArt.HELP)
        canvas.card(24, 3, 1, "通常", CoreMenuArt.ENHANCE)
        canvas.card(33, 3, 1, "触媒", CoreMenuArt.ORB)
        canvas.card(42, 3, 1, "素材", CoreMenuArt.GATHER)
        canvas.button(45, 3, "戻る")
        assertEquals(6, canvas.snapshot().buttons.size + canvas.snapshot().cards.size)
        for (card in listOf(false, true)) {
            val occupied = CoreMenuCanvas("工房")
            if (card) occupied.card(18, 3, 2, "強化", CoreMenuArt.ENHANCE)
            else occupied.button(27, 3, "強化")
            assertFailsWith<IllegalArgumentException> { occupied.focus(CoreMenuArt.WEAPON, "武器") }
            assertEquals(null, occupied.snapshot().focus)
        }
    }

    @Test fun `mixed font sizes and focus restore origin before later primitives`() {
        val canvas = CoreMenuCanvas("工房").apply {
            focus(CoreMenuArt.WEAPON, "+6 → +7")
            button(0, 2, "強化", CoreMenuCanvas.Tone.SELECTED)
            card(24, 3, 1, "通常", CoreMenuArt.ENHANCE)
            art(184, 196, CoreMenuArt.ORE)
            text(-90, 56, "木材 12 / 80", maxWidth = 80)
            text(190, 72, "+30", maxWidth = 60, style = CoreMenuCanvas.TextStyle.EMPHASIS)
        }
        val body = fontMetrics("glyphs").values.toMap()
        val emphasis = fontMetrics("glyphs-emphasis").values.toMap()
        var cursor = 0
        val anchors = mutableMapOf<String, MutableList<Int>>()
        for (part in components(canvas.render()).filterIsInstance<TextComponent>().filter { it.content().isNotEmpty() }) {
            val font = part.style().font()!!.value()
            if (font != "core_spacing") anchors.getOrPut(font) { mutableListOf() }.add(cursor + 8)
            for (glyph in part.content()) cursor += when {
                font == "core_spacing" -> if (glyph.code >= 0xE180) -1.shl(glyph.code - 0xE180) else 1.shl(glyph.code - 0xE100)
                font == "core_menu_canvas" -> 193
                font == "core_menu_focus" -> 107
                font.startsWith("core_menu_emphasis_y") -> emphasis.getValue(glyph)
                font.startsWith("core_menu_y") -> body.getValue(glyph)
                font.startsWith("core_menu_buttons_") -> ((glyph.code - 0xE610) % 9 + 1) * 18 - 1
                font.startsWith("core_menu_cards_") -> ((glyph.code - 0xE650) % 9 + 1) * 18 - 1
                font.startsWith("core_menu_art_") -> CoreMenuArt.entries[glyph.code - 0xE700].advance(font.split('_')[3].toInt())
                else -> error("Unexpected menu font $font")
            }
        }
        assertEquals(0, cursor, "Every primitive must return the title cursor to its origin")
        assertEquals(listOf(8), anchors.getValue("core_menu_focus").toList())
        assertEquals(listOf(37), anchors.getValue("core_menu_art_48_54").toList())
        assertEquals(listOf(canvas.snapshot().focus!!.captionX), anchors.getValue("core_menu_emphasis_y100").toList())
        assertEquals(listOf(184), anchors.getValue("core_menu_art_16_196").toList())
        assertEquals(listOf(-90), anchors.getValue("core_menu_y56").toList())
        assertEquals(listOf(190), anchors.getValue("core_menu_emphasis_y72").toList())
    }
}
