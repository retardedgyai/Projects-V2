package dev.projects.server.coreloop.ui

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Always-visible labels and state for a vanilla six-row chest. This owns presentation only:
 * callers still install the actual item stacks, click actions and server-side validation.
 *
 * All coordinates are relative to the vanilla chest's upper-left corner. The canvas extends
 * 104 px to each side, but never moves a vanilla slot. Use only after the pack reports loaded.
 * Labels draw before item stacks, so every text-only button slot must use the blank item model.
 */
class CoreMenuCanvas(private val title: String) {
    enum class Tone { NEUTRAL, SELECTED, PRIMARY, DISABLED, DANGER }
    enum class TextStyle { BODY, EMPHASIS }
    data class Line(val text: String, val color: TextColor = BODY_COLOR, val art: CoreMenuArt? = null, val style: TextStyle = TextStyle.BODY)
    internal data class Panel(val title: String, val lines: List<Line>, val hero: CoreMenuArt?)
    internal data class Button(val firstSlot: Int, val span: Int, val label: String, val tone: Tone, val icon: Boolean)
    internal data class Card(val firstSlot: Int, val columns: Int, val rows: Int, val label: String, val art: CoreMenuArt, val tone: Tone)
    internal data class Art(val x: Int, val y: Int, val art: CoreMenuArt, val size: Int)
    internal data class Text(val x: Int, val y: Int, val value: String, val color: TextColor, val maxWidth: Int, val style: TextStyle)
    internal data class Focus(val art: CoreMenuArt, val caption: String)
    /** Plain data for offline visual QA of an actual constructed menu, not a second UI model. */
    internal data class Snapshot(
        val title: String,
        val titleColor: Int,
        val leftPanel: PanelSnapshot?,
        val rightPanel: PanelSnapshot?,
        val buttons: List<ButtonSnapshot>,
        val texts: List<TextSnapshot>,
        val cards: List<CardSnapshot>,
        val arts: List<ArtSnapshot>,
        val focus: FocusSnapshot?,
    )
    internal data class PanelSnapshot(val title: String, val titleColor: Int, val lines: List<LineSnapshot>, val hero: ArtSnapshot?)
    internal data class LineSnapshot(val text: String, val color: Int, val x: Int, val y: Int, val maxWidth: Int, val art: ArtSnapshot?, val style: String)
    internal data class ButtonSnapshot(val firstSlot: Int, val span: Int, val label: String, val tone: String, val icon: Boolean, val textColor: Int)
    internal data class TextSnapshot(val x: Int, val y: Int, val value: String, val color: Int, val maxWidth: Int, val style: String)
    internal data class ArtSnapshot(val x: Int, val y: Int, val art: String, val size: Int)
    internal data class FocusSnapshot(
        val x: Int, val y: Int, val width: Int, val height: Int, val artPlacement: ArtSnapshot,
        val caption: String, val captionX: Int, val captionY: Int, val captionMaxWidth: Int,
        val textColor: Int, val style: String, val reservedSlots: List<Int>,
    )
    internal data class CardSnapshot(
        val firstSlot: Int, val columns: Int, val rows: Int, val label: String, val art: String,
        val tone: String, val textColor: Int, val x: Int, val y: Int, val width: Int, val height: Int,
        val labelX: Int, val labelY: Int, val labelMaxWidth: Int, val artPlacement: ArtSnapshot, val occupiedSlots: List<Int>,
    )

    private var leftPanel: Panel? = null
    private var rightPanel: Panel? = null
    private val buttons = linkedMapOf<Int, Button>()
    private val cards = linkedMapOf<Int, Card>()
    private val arts = mutableListOf<Art>()
    private val texts = mutableListOf<Text>()
    private var focus: Focus? = null

    /** Replaces the panel. Callers can use [wrap] for prose; no required state is silently dropped. */
    fun left(title: String, lines: List<Line>, hero: CoreMenuArt? = null) { leftPanel = panel(title, lines, hero) }
    fun right(title: String, lines: List<Line>, hero: CoreMenuArt? = null) { rightPanel = panel(title, lines, hero) }

    private fun panel(title: String, lines: List<Line>, hero: CoreMenuArt?): Panel {
        val availableLines = if (hero == null) PANEL_LINES else HERO_PANEL_LINES
        require(lines.size <= availableLines) { "Menu panel supports $availableLines lines; paginate or shorten the content" }
        require(lines.zipWithNext().none { (first, second) -> first.art != null && second.art != null }) {
            "Menu panel icons need a plain value or spacer line between them"
        }
        return Panel(title, lines.toList(), hero)
    }

    /** All covered slots must invoke the same action. A selected state has a warm gold accent. */
    fun button(firstSlot: Int, span: Int, label: String, tone: Tone = Tone.NEUTRAL, icon: Boolean = false) {
        require(firstSlot in 0..53 && span in 1..9 && firstSlot % 9 + span <= 9) { "Button escaped its vanilla slot row" }
        val occupied = firstSlot until firstSlot + span
        require(focus == null || occupied.none(FOCUS_SLOTS::contains)) { "Menu button overlaps the equipment focus" }
        require(buttons.values.none { it.firstSlot != firstSlot && (it.firstSlot until it.firstSlot + it.span).any(occupied::contains) }) {
            "Menu buttons overlap: slot $firstSlot, span $span"
        }
        require(cards.values.none { occupiedSlots(it.firstSlot, it.columns, it.rows).any(occupied::contains) }) {
            "Menu button overlaps a card: slot $firstSlot, span $span"
        }
        buttons[firstSlot] = Button(firstSlot, span, label, tone, icon)
    }

    /**
     * A single illustrated action covering an entire vanilla-slot rectangle. Callers must
     * put the same action and blank item model in every covered slot, including the interior.
     * One-row cards use an inline icon; two/three-row cards put 16/32 px art above the label.
     */
    fun card(firstSlot: Int, columns: Int, rows: Int, label: String, art: CoreMenuArt, tone: Tone = Tone.NEUTRAL) {
        require(firstSlot in 0..53 && columns in 1..9 && rows in 1..3 &&
            firstSlot % 9 + columns <= 9 && firstSlot / 9 + rows <= 6) { "Card escaped the vanilla slot grid" }
        require(rows != 1 || columns >= 2 || label.isEmpty()) { "An inline card label needs at least two slots" }
        require(rows != 3 || columns >= 2) { "A 32 px card illustration needs at least two columns" }
        val occupied = occupiedSlots(firstSlot, columns, rows).toSet()
        require(focus == null || occupied.none(FOCUS_SLOTS::contains)) { "Menu card overlaps the equipment focus" }
        require(buttons.values.none { (it.firstSlot until it.firstSlot + it.span).any(occupied::contains) } &&
            cards.values.none { it.firstSlot != firstSlot && occupiedSlots(it.firstSlot, it.columns, it.rows).any(occupied::contains) }) {
            "Menu card overlaps another action: slot $firstSlot, columns $columns, rows $rows"
        }
        cards[firstSlot] = Card(firstSlot, columns, rows, label, art, tone)
    }

    /**
     * A non-clicking equipment subject on its own pedestal. The caller may install the same
     * blank-model detail item in [FOCUS_SLOTS], but no action card/button belongs in that area.
     * Replacing the focus replaces both its art and its original, untruncated caption.
     */
    fun focus(art: CoreMenuArt, caption: String) {
        require(buttons.values.none { (it.firstSlot until it.firstSlot + it.span).any(FOCUS_SLOTS::contains) } &&
            cards.values.none { occupiedSlots(it.firstSlot, it.columns, it.rows).any(FOCUS_SLOTS::contains) }) {
            "Menu equipment focus overlaps an action"
        }
        focus = Focus(art, caption)
    }

    /** Decorative art is not an extra click target. Only explicitly shipped sizes/rows work. */
    fun art(x: Int, y: Int, art: CoreMenuArt, size: Int = 16) {
        require(size in ART_SIZES && y in ART_YS) { "Menu art requested an unshipped size or vertical position" }
        require(x >= -98 && x + size <= 272 && y + size <= 218) { "Art escaped the readable canvas" }
        arts += Art(x, y, art, size)
    }

    /**
     * Render one line; overflow gets a visible ellipsis. For important values, preflight with
     * [width] or wrap over multiple lines. y snaps to an available atlas row to avoid hundreds
     * of duplicate font uploads on the client; [snapY] exposes the exact resulting coordinate.
     */
    fun text(x: Int, y: Int, value: String, color: TextColor = BODY_COLOR, maxWidth: Int = PANEL_WIDTH, style: TextStyle = TextStyle.BODY) {
        require(x >= -98 && x + maxWidth <= 272 && maxWidth > 0) { "Text escaped the readable canvas" }
        require(y in 0..210) { "Text escaped the readable canvas vertically" }
        texts += Text(x, snapY(y), value, color, maxWidth, style)
    }

    internal fun snapshot(): Snapshot {
        fun Panel.snapshot(x: Int): PanelSnapshot {
            val startY = if (hero == null) 30 else 72
            return PanelSnapshot(title, HEADING.value(), lines.mapIndexed { index, line ->
                val y = startY + index * LINE_HEIGHT
                val inset = if (line.art == null) 0 else 18
                LineSnapshot(line.text, line.color.value(), x + inset, y, PANEL_WIDTH - inset,
                    line.art?.let { ArtSnapshot(x, y - 2, it.name, 16) }, line.style.name)
            }, hero?.let { ArtSnapshot(x + (PANEL_WIDTH - 32) / 2, 30, it.name, 32) })
        }
        return Snapshot(title, HEADING.value(), leftPanel?.snapshot(-98), rightPanel?.snapshot(184),
            buttons.values.map { ButtonSnapshot(it.firstSlot, it.span, it.label, it.tone.name, it.icon, toneColor(it.tone).value()) },
            texts.map { TextSnapshot(it.x, it.y, it.value, it.color.value(), it.maxWidth, it.style.name) },
            cards.values.map { card ->
                val x = 8 + card.firstSlot % 9 * 18
                val y = 18 + card.firstSlot / 9 * 18
                val extent = card.columns * 18 - 2
                val size = if (card.rows == 3) 32 else 16
                val inset = if (card.rows == 1) 18 else 0
                val room = (extent - inset).coerceAtLeast(0)
                val visible = trim(card.label, room, TextStyle.EMPHASIS)
                CardSnapshot(card.firstSlot, card.columns, card.rows, card.label, card.art.name, card.tone.name,
                    toneColor(card.tone).value(), x, y, extent, card.rows * 18 - 2,
                    x + inset + (room - width(visible, TextStyle.EMPHASIS)) / 2, 20 + (card.firstSlot / 9 + card.rows - 1) * 18, room,
                    ArtSnapshot(if (card.rows == 1) x else x + (extent - size) / 2, y, card.art.name, size),
                    occupiedSlots(card.firstSlot, card.columns, card.rows))
            }, arts.map { ArtSnapshot(it.x, it.y, it.art.name, it.size) }, focus?.let {
                val visible = trim(it.caption, 106, TextStyle.EMPHASIS)
                FocusSnapshot(8, 44, 106, 64, ArtSnapshot(37, 54, it.art.name, 48), it.caption,
                    8 + (106 - width(visible, TextStyle.EMPHASIS)) / 2, 100, 106,
                    HEADING.value(), TextStyle.EMPHASIS.name, FOCUS_SLOTS)
            })
    }

    /** Original Unicode information for a no-pack detail item; never the encoded PUA or ellipsis. */
    fun fallbackLines(): List<String> = buildList {
        add(title)
        for (panel in listOfNotNull(leftPanel, rightPanel)) {
            add(""); add(panel.title); addAll(panel.lines.map(Line::text))
        }
        if (texts.isNotEmpty()) { add(""); addAll(texts.map(Text::value)) }
        focus?.let { add(""); add(it.caption) }
    }

    fun render(): Component {
        var result: Component = Component.empty()
        fun draw(x: Int, value: Component, advance: Int) {
            // Each primitive returns to the vanilla title origin (x=8). A long preceding
            // label, half-width digit, or transparent glyph cannot move another primitive.
            result = result.append(CoreUiComponents.space(x - 8)).append(value)
                .append(CoreUiComponents.space(8 - x - advance))
        }
        fun label(x: Int, y: Int, value: String, color: TextColor, limit: Int, style: TextStyle) {
            val visible = trim(value, limit, style)
            if (visible.isEmpty()) return
            warnMissing(visible)
            val encoded = buildString { visible.codePoints().forEach { append(metric(it, style).glyph) } }
            val font = if (style == TextStyle.EMPHASIS) "core_menu_emphasis_y${snapY(y)}" else "core_menu_y${snapY(y)}"
            val rendered = Component.text(encoded, color).font(Key.key("projects", font))
                .shadowColor(net.kyori.adventure.text.format.ShadowColor.none())
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
            draw(x, rendered, width(visible, style))
        }
        fun illustration(value: ArtSnapshot) {
            val art = CoreMenuArt.valueOf(value.art)
            draw(value.x, CoreUiComponents.glyph(art.glyph, Key.key("projects", "core_menu_art_${value.size}_${value.y}")), art.advance(value.size))
        }

        val snapshot = snapshot()
        draw(-104, CoreUiComponents.glyph('\uE600', CANVAS_FONT), 193)
        draw(88, CoreUiComponents.glyph('\uE601', CANVAS_FONT), 193)
        label(8, 6, title, HEADING, 160, TextStyle.EMPHASIS)
        // Vanilla draws its own player-inventory label at (8,128) after this title.
        // The frame gives that dark text a light strip; adding a label here would overlap it.
        for ((x, panel) in listOf(-98 to snapshot.leftPanel, 184 to snapshot.rightPanel)) {
            if (panel == null) continue
            label(x, 8, panel.title, HEADING, PANEL_WIDTH, TextStyle.EMPHASIS)
            panel.hero?.let(::illustration)
            panel.lines.forEach { line ->
                line.art?.let(::illustration)
                label(line.x, line.y, line.text, TextColor.color(line.color), line.maxWidth, TextStyle.valueOf(line.style))
            }
        }
        for (card in snapshot.cards) {
            val tone = Tone.valueOf(card.tone)
            val glyph = (0xE650 + tone.ordinal * 9 + card.columns - 1).toChar()
            draw(card.x, CoreUiComponents.glyph(glyph, Key.key("projects", "core_menu_cards_${card.rows}_${card.firstSlot / 9}")), card.width + 1)
            illustration(card.artPlacement)
            label(card.labelX, card.labelY, card.label, TextColor.color(card.textColor), card.labelMaxWidth, TextStyle.EMPHASIS)
        }
        for (button in buttons.values) {
            val x = 8 + button.firstSlot % 9 * 18
            val row = button.firstSlot / 9
            val extent = button.span * 18 - 2
            val glyph = (0xE610 + button.tone.ordinal * 9 + button.span - 1).toChar()
            draw(x, CoreUiComponents.glyph(glyph, Key.key("projects", "core_menu_buttons_$row")), extent + 1)
            val inset = if (button.icon) 18 else 0
            // Japanese 3 x 11 px labels fit the real 34 px two-slot width. Do not
            // subtract another invented padding budget and turn distinct labels into ellipses.
            val room = (extent - inset).coerceAtLeast(0)
            val visible = trim(button.label, room, TextStyle.EMPHASIS)
            label(x + inset + (extent - inset - width(visible, TextStyle.EMPHASIS)) / 2, 20 + row * 18, visible, toneColor(button.tone), room, TextStyle.EMPHASIS)
        }
        snapshot.focus?.let {
            draw(it.x, CoreUiComponents.glyph('\uE6F0', FOCUS_FONT), it.width + 1)
            illustration(it.artPlacement)
            label(it.captionX, it.captionY, it.caption, TextColor.color(it.textColor), it.captionMaxWidth, TextStyle.EMPHASIS)
        }
        for (art in snapshot.arts) illustration(art)
        for (text in texts) label(text.x, text.y, text.value, text.color, text.maxWidth, text.style)
        return result
    }

    companion object {
        const val PANEL_WIDTH = 88
        const val PANEL_LINES = 13
        const val HERO_PANEL_LINES = 10
        const val LINE_HEIGHT = 14
        val HEADING: TextColor = TextColor.color(0xEAD9BA)
        val BODY_COLOR: TextColor = TextColor.color(0xD6CBB7)
        val ART_SIZES: Set<Int> = setOf(16, 32, 48)
        val ART_YS: Set<Int> = setOf(18, 28, 30, 36, 42, 48, 54, 56, 70, 72, 84, 90, 98, 108, 112, 126, 140, 154, 168, 182, 196)
        val FOCUS_SLOTS: List<Int> = occupiedSlots(18, 6, 3)
        private val CANVAS_FONT = Key.key("projects", "core_menu_canvas")
        private val FOCUS_FONT = Key.key("projects", "core_menu_focus")
        internal val TEXT_YS = (listOf(6, 8, 128) + (0..5).map { 20 + 18 * it } + (0..12).map { 30 + 14 * it }).distinct().sorted()
        private data class Metric(val glyph: Char, val advance: Int)
        private fun loadMetrics(name: String): Map<Int, Metric> {
            val resource = "core-ui-pack/assets/projects/menu/$name.tsv"
            val parsed = requireNotNull(CoreMenuCanvas::class.java.classLoader.getResourceAsStream(resource)) { "Missing menu font metrics: $name" }
                .bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
                        val fields = line.split('\t')
                        require(fields.size == 3) { "Malformed menu font metrics" }
                        val codepoint = fields[0].toInt(16)
                        val glyph = fields[1].toInt(16)
                        val advance = fields[2].toInt()
                        require(Character.isValidCodePoint(codepoint) && glyph in 0xE000..0xF8FF && advance in 1..15) {
                            "Invalid menu font metric in $name"
                        }
                        codepoint to Metric(glyph.toChar(), advance)
                    }.toList()
                }
            require(parsed.map { it.first }.toSet().size == parsed.size && parsed.map { it.second.glyph }.toSet().size == parsed.size) {
                "Menu font metrics must contain each character and glyph once: $name"
            }
            return parsed.toMap().also { require('□'.code in it) { "Menu font metrics have no fallback glyph: $name" } }
        }
        private val metrics: Map<TextStyle, Map<Int, Metric>> by lazy {
            val body = loadMetrics("glyphs")
            val emphasis = loadMetrics("glyphs-emphasis")
            require(body.keys == emphasis.keys && body.all { (codepoint, metric) -> emphasis.getValue(codepoint).glyph == metric.glyph }) {
                "Menu text styles must share characters and glyph ordinals"
            }
            mapOf(TextStyle.BODY to body, TextStyle.EMPHASIS to emphasis)
        }
        private val warnedMissing = ConcurrentHashMap.newKeySet<Int>()
        private fun metric(codepoint: Int, style: TextStyle): Metric = metrics.getValue(style).let { it[codepoint] ?: it.getValue('□'.code) }
        private fun occupiedSlots(firstSlot: Int, columns: Int, rows: Int): List<Int> =
            (0 until rows).flatMap { row -> (0 until columns).map { column -> firstSlot + row * 9 + column } }
        fun missingCharacters(value: String): Set<Int> = value.codePoints().toArray().filterNot(metrics.getValue(TextStyle.BODY)::containsKey).toSet()
        private fun warnMissing(value: String) {
            for (codepoint in missingCharacters(value)) if (warnedMissing.add(codepoint)) {
                System.err.println("CORE_MENU_MISSING_GLYPH U+${codepoint.toString(16).uppercase()}; regenerate scripts/build_core_ui_assets.py")
            }
        }

        fun snapY(y: Int): Int = TEXT_YS.minBy { abs(it - y) }
        fun width(value: String, style: TextStyle = TextStyle.BODY): Int = value.codePoints().toArray().sumOf { metric(it, style).advance }

        fun trim(value: String, maxWidth: Int, style: TextStyle = TextStyle.BODY): String {
            if (width(value, style) <= maxWidth) return value
            val available = maxWidth - width("…", style)
            if (available < 0) return ""
            return buildString {
                var used = 0
                for (codepoint in value.codePoints().toArray()) {
                    val advance = metric(codepoint, style).advance
                    if (used + advance > available) break
                    appendCodePoint(codepoint); used += advance
                }
                append('…')
            }
        }

        /**
         * Keep Latin words and numeric expressions intact when they fit a panel. Japanese
         * prose still wraps by codepoint; callers should use short, meaningful UI sentences.
         * A single oversized ASCII expression falls back to codepoints, never lost content.
         */
        fun wrap(value: String, maxWidth: Int = PANEL_WIDTH, style: TextStyle = TextStyle.BODY): List<String> {
            require(maxWidth >= width("□", style)) { "Text width must fit at least one menu glyph" }
            return value.split('\n').flatMap { paragraph ->
                val result = mutableListOf<String>()
                var line = StringBuilder()
                var used = 0
                fun appendUnit(unit: String) {
                    val advance = width(unit, style)
                    if (used + advance > maxWidth && line.isNotEmpty()) {
                        result += line.toString(); line = StringBuilder(); used = 0
                    }
                    line.append(unit); used += advance
                }
                for (unit in wrapUnits(paragraph)) {
                    if (width(unit, style) <= maxWidth) appendUnit(unit)
                    else unit.codePoints().forEach { appendUnit(String(Character.toChars(it))) }
                }
                result += line.toString()
                result
            }
        }

        private fun wrapUnits(paragraph: String): List<String> = buildList {
            val ascii = StringBuilder()
            fun flushAscii() { if (ascii.isNotEmpty()) { add(ascii.toString()); ascii.setLength(0) } }
            for (codepoint in paragraph.codePoints().toArray()) {
                val isAsciiWord = codepoint in 'A'.code..'Z'.code || codepoint in 'a'.code..'z'.code ||
                    codepoint in '0'.code..'9'.code || (codepoint < 128 && codepoint.toChar() in "_+-.%/")
                if (isAsciiWord) ascii.appendCodePoint(codepoint)
                else { flushAscii(); add(String(Character.toChars(codepoint))) }
            }
            flushAscii()
        }

        private fun toneColor(tone: Tone): TextColor = TextColor.color(when (tone) {
            Tone.NEUTRAL -> 0xD6CBB7
            Tone.SELECTED -> 0xF4D59A
            Tone.PRIMARY -> 0xFFF0CE
            Tone.DISABLED -> 0x837C70
            Tone.DANGER -> 0xFFE3E0
        })
    }
}
