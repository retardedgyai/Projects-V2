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
    data class Line(val text: String, val color: TextColor = CoreUiComponents.IVORY)
    internal data class Panel(val title: String, val lines: List<Line>)
    internal data class Button(val firstSlot: Int, val span: Int, val label: String, val tone: Tone, val icon: Boolean)
    internal data class Text(val x: Int, val y: Int, val value: String, val color: TextColor, val maxWidth: Int)

    private var leftPanel: Panel? = null
    private var rightPanel: Panel? = null
    private val buttons = linkedMapOf<Int, Button>()
    private val texts = mutableListOf<Text>()

    /** Replaces the panel. Callers can use [wrap] for prose; no required state is silently dropped. */
    fun left(title: String, lines: List<Line>) { leftPanel = panel(title, lines) }
    fun right(title: String, lines: List<Line>) { rightPanel = panel(title, lines) }

    private fun panel(title: String, lines: List<Line>): Panel {
        require(lines.size <= PANEL_LINES) { "Menu panels support $PANEL_LINES lines; paginate or shorten the content" }
        return Panel(title, lines.toList())
    }

    /** All covered slots must invoke the same action. A selected state includes a gold underline. */
    fun button(firstSlot: Int, span: Int, label: String, tone: Tone = Tone.NEUTRAL, icon: Boolean = false) {
        require(firstSlot in 0..53 && span in 1..9 && firstSlot % 9 + span <= 9) { "Button escaped its vanilla slot row" }
        val occupied = firstSlot until firstSlot + span
        require(buttons.values.none { it.firstSlot != firstSlot && (it.firstSlot until it.firstSlot + it.span).any(occupied::contains) }) {
            "Menu buttons overlap: slot $firstSlot, span $span"
        }
        buttons[firstSlot] = Button(firstSlot, span, label, tone, icon)
    }

    /**
     * Render one line; overflow gets a visible ellipsis. For important values, preflight with
     * [width] or wrap over multiple lines. y snaps to an available atlas row to avoid hundreds
     * of duplicate font uploads on the client; [snapY] exposes the exact resulting coordinate.
     */
    fun text(x: Int, y: Int, value: String, color: TextColor = CoreUiComponents.IVORY, maxWidth: Int = PANEL_WIDTH) {
        require(x >= -98 && x + maxWidth <= 272 && maxWidth > 0) { "Text escaped the readable canvas" }
        require(y in 0..210) { "Text escaped the readable canvas vertically" }
        texts += Text(x, snapY(y), value, color, maxWidth)
    }

    fun render(): Component {
        var result: Component = Component.empty()
        fun draw(x: Int, value: Component, advance: Int) {
            // Each primitive returns to the vanilla title origin (x=8). A long preceding
            // label, half-width digit, or transparent glyph cannot move another primitive.
            result = result.append(CoreUiComponents.space(x - 8)).append(value)
                .append(CoreUiComponents.space(8 - x - advance))
        }
        fun label(x: Int, y: Int, value: String, color: TextColor, limit: Int) {
            val visible = trim(value, limit)
            if (visible.isEmpty()) return
            warnMissing(visible)
            val encoded = buildString { visible.codePoints().forEach { append(metric(it).glyph) } }
            val rendered = Component.text(encoded, color).font(Key.key("projects", "core_menu_y${snapY(y)}"))
                .shadowColor(net.kyori.adventure.text.format.ShadowColor.none())
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
            draw(x, rendered, width(visible))
        }

        draw(-104, CoreUiComponents.glyph('\uE600', CANVAS_FONT), 193)
        draw(88, CoreUiComponents.glyph('\uE601', CANVAS_FONT), 193)
        label(8, 6, title, CoreUiComponents.GOLD, 160)
        // Vanilla draws its own player-inventory label at (8,128) after this title.
        // The frame gives that dark text a light strip; adding a label here would overlap it.
        for ((x, panel) in listOf(-98 to leftPanel, 184 to rightPanel)) {
            if (panel == null) continue
            label(x, 8, panel.title, CoreUiComponents.GOLD, PANEL_WIDTH)
            panel.lines.forEachIndexed { index, line ->
                label(x, 30 + index * LINE_HEIGHT, line.text, line.color, PANEL_WIDTH)
            }
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
            val visible = trim(button.label, room)
            label(x + inset + (extent - inset - width(visible)) / 2, 20 + row * 18, visible, toneColor(button.tone), room)
        }
        for (text in texts) label(text.x, text.y, text.value, text.color, text.maxWidth)
        return result
    }

    companion object {
        const val PANEL_WIDTH = 88
        const val PANEL_LINES = 13
        const val LINE_HEIGHT = 14
        private val CANVAS_FONT = Key.key("projects", "core_menu_canvas")
        internal val TEXT_YS = (listOf(6, 8, 128) + (0..5).map { 20 + 18 * it } + (0..12).map { 30 + 14 * it }).distinct().sorted()
        private data class Metric(val glyph: Char, val advance: Int)
        private val metrics: Map<Int, Metric> by lazy {
            val resource = "core-ui-pack/assets/projects/menu/glyphs.tsv"
            requireNotNull(CoreMenuCanvas::class.java.classLoader.getResourceAsStream(resource)) { "Missing menu font metrics" }
                .bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.filter { it.isNotBlank() && !it.startsWith('#') }.associate { line ->
                        val fields = line.split('\t')
                        require(fields.size == 3) { "Malformed menu font metrics" }
                        fields[0].toInt(16) to Metric(fields[1].toInt(16).toChar(), fields[2].toInt())
                    }
                }
        }
        private val warnedMissing = ConcurrentHashMap.newKeySet<Int>()
        private fun metric(codepoint: Int): Metric = metrics[codepoint] ?: metrics.getValue('□'.code)
        fun missingCharacters(value: String): Set<Int> = value.codePoints().toArray().filterNot(metrics::containsKey).toSet()
        private fun warnMissing(value: String) {
            for (codepoint in missingCharacters(value)) if (warnedMissing.add(codepoint)) {
                System.err.println("CORE_MENU_MISSING_GLYPH U+${codepoint.toString(16).uppercase()}; regenerate scripts/build_core_ui_assets.py")
            }
        }

        fun snapY(y: Int): Int = TEXT_YS.minBy { abs(it - y) }
        fun width(value: String): Int = value.codePoints().toArray().sumOf { metric(it).advance }

        fun trim(value: String, maxWidth: Int): String {
            if (width(value) <= maxWidth) return value
            val available = maxWidth - width("…")
            if (available < 0) return ""
            return buildString {
                var used = 0
                for (codepoint in value.codePoints().toArray()) {
                    val advance = metric(codepoint).advance
                    if (used + advance > available) break
                    appendCodePoint(codepoint); used += advance
                }
                append('…')
            }
        }

        /** Greedy codepoint-safe wrapping for the concrete menu panels; explicit newlines survive. */
        fun wrap(value: String, maxWidth: Int = PANEL_WIDTH): List<String> {
            require(maxWidth >= width("□")) { "Text width must fit at least one menu glyph" }
            return value.split('\n').flatMap { paragraph ->
                val result = mutableListOf<String>()
                var line = StringBuilder()
                var used = 0
                for (codepoint in paragraph.codePoints().toArray()) {
                    val advance = metric(codepoint).advance
                    if (used + advance > maxWidth && line.isNotEmpty()) {
                        result += line.toString(); line = StringBuilder(); used = 0
                    }
                    line.appendCodePoint(codepoint); used += advance
                }
                result += line.toString()
                result
            }
        }

        private fun toneColor(tone: Tone): TextColor = TextColor.color(when (tone) {
            Tone.NEUTRAL -> 0xECF1F2
            Tone.SELECTED -> 0xFFF0BF
            Tone.PRIMARY -> 0xF1FFF0
            Tone.DISABLED -> 0xABB0B4
            Tone.DANGER -> 0xFFE3E0
        })
    }
}
