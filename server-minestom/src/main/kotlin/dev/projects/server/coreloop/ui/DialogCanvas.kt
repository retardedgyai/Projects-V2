package dev.projects.server.coreloop.ui

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextColor
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Places words and sprites at any (x, y) inside a vanilla dialog's text. The fonts, sprites and their advances come
 * from scripts/build_menu_dialog_assets.py (polish05/menu-metrics.json); see its header for how the client lays text out.
 *
 * A canvas is [lineCount] lines of exactly [width] px. Its y = 0 is the top of the first line, which sits
 * [originTop] px below where the caller measures from (0 for the dialog body, the title's top for the title).
 */
internal class DialogCanvas(private val metrics: Metrics, private val width: Double, private val lineCount: Int, private val originTop: Int = 0) {
    enum class Align { LEFT, CENTER, RIGHT }

    private class Piece(val line: Int, val x: Double, val advance: Double, val component: Component)

    private val pieces = mutableListOf<Piece>()

    /** [baseline] is where the letters stand. Lines are 9 px apart and a line's baseline is 7 px below its top. */
    fun text(style: String, x: Double, baseline: Int, text: String, color: TextColor, align: Align = Align.LEFT,
             click: ClickEvent<*>? = null, hover: Component? = null): Double {
        val font = metrics.styles.getValue(style)
        val advance = text.codePoints().toArray().sumOf { font.advances[it] ?: missing(style, it) }
        val y = baseline - originTop - 7
        val line = maxOf(0, floor(y / 9.0).toInt())
        val shift = y - line * 9
        require(shift in 0..8) { "Text at baseline $baseline is above the canvas" }
        val left = when (align) { Align.LEFT -> x; Align.CENTER -> x - advance / 2; Align.RIGHT -> x - advance }
        var component = Component.text(text, color).font(Key.key(font.font + shift)).shadowColor(ShadowColor.none())
        if (click != null) component = component.clickEvent(click)
        if (hover != null) component = component.hoverEvent(HoverEvent.showText(hover))
        add(line, left, advance, component)
        return advance
    }

    /** A baked sprite whose top-left corner is at ([x], [top]). Only the positions the build script baked exist. */
    fun sprite(name: String, x: Double, top: Int, click: ClickEvent<*>? = null, hover: Component? = null) {
        val sprite = metrics.sprites[name] ?: error("Unknown menu sprite $name")
        val y = top - originTop
        val line = maxOf(0, floor(y / 9.0).toInt())
        // Short sprites hang from the line above (the client rejects ascent > height); the script baked the same choice.
        val choice = listOf(line to 7 - (y - line * 9), line - 1 to 7 - (y - (line - 1) * 9))
            .firstOrNull { (l, ascent) -> l >= 0 && sprite.chars.containsKey(ascent) }
            ?: error("Sprite $name was not baked for y=$top")
        var component = Component.text(String(Character.toChars(sprite.chars.getValue(choice.second)))).font(ART)
            .color(WHITE).shadowColor(ShadowColor.none())
        if (click != null) component = component.clickEvent(click)
        if (hover != null) component = component.hoverEvent(HoverEvent.showText(hover))
        add(choice.first, x, sprite.advance.toDouble(), component)
    }

    /** Any other component of known advance (a player's face is 8 px wide), on the line holding [baseline]. */
    fun inline(x: Double, baseline: Int, advance: Double, component: Component) {
        val y = baseline - originTop - 7
        add(maxOf(0, floor(y / 9.0).toInt()), x, advance, component)
    }

    fun width(style: String, text: String): Double {
        val font = metrics.styles.getValue(style)
        return text.codePoints().toArray().sumOf { font.advances[it] ?: missing(style, it) }
    }

    private fun add(line: Int, x: Double, advance: Double, component: Component) {
        require(line < lineCount) { "Line $line is past the canvas" }
        pieces += Piece(line, x, advance, component)
    }

    /** Every line, padded to the full width so the client's centring leaves each one at the same left edge. */
    fun build(): Component {
        val out = Component.text()
        for (line in 0 until lineCount) {
            if (line > 0) out.append(Component.newline())
            out.append(row(pieces.filter { it.line == line }.sortedBy { it.x }))
        }
        return out.build()
    }

    /** One line only, ending at [width] (the title). */
    fun buildLine(): Component = row(pieces.sortedBy { it.x })

    private fun row(items: List<Piece>): Component {
        val out = Component.text()
        var cursor = 0.0
        for (piece in items) {
            space(out, piece.x - cursor)
            out.append(piece.component)
            cursor = piece.x + piece.advance
        }
        space(out, width - cursor)
        return out.build()
    }

    private fun space(out: net.kyori.adventure.text.TextComponent.Builder, distance: Double) {
        var left = (distance * 4).roundToInt() / 4.0
        if (left == 0.0) return
        val text = StringBuilder()
        val sign = if (left < 0) -1 else 1
        left = abs(left)
        for (step in SPACE_STEPS) while (left >= step - 1e-9) { text.appendCodePoint(metrics.space.getValue(sign * step)); left -= step }
        out.append(Component.text(text.toString()).font(SPACE))
    }

    private fun missing(style: String, codePoint: Int): Double {
        System.err.println("MENU_FONT_MISSING: U+%04X in %s (rerun scripts/build_menu_dialog_assets.py)".format(codePoint, style))
        return 6.0
    }

    class Font(val font: String, val advances: Map<Int, Double>)
    class Sprite(val advance: Int, val chars: Map<Int, Int>)

    class Metrics(val styles: Map<String, Font>, val sprites: Map<String, Sprite>, val space: Map<Double, Int>, val layout: JsonObject,
                  val canvasWidth: Int, val lines: Int, val titleWidth: Int, val titleX: Int, val titleTop: Int) {
        companion object {
            fun parse(bytes: ByteArray): Metrics {
                val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
                val styles = root.getAsJsonObject("styles").entrySet().associate { (name, value) ->
                    val o = value.asJsonObject
                    name to Font(o["font"].asString, o.getAsJsonObject("advances").entrySet().associate { (cp, a) -> cp.toInt() to a.asDouble })
                }
                val sprites = root.getAsJsonObject("sprites").entrySet().associate { (name, value) ->
                    val o = value.asJsonObject
                    name to Sprite(o["advance"].asInt, o.getAsJsonObject("chars").entrySet().associate { (a, cp) -> a.toInt() to cp.asInt })
                }
                val space = root.getAsJsonObject("space").entrySet().associate { (advance, cp) -> advance.toDouble() to cp.asInt }
                return Metrics(styles, sprites, space, root.getAsJsonObject("layout"), root["canvasWidth"].asInt, root["lines"].asInt,
                    root["titleWidth"].asInt, root["titleX"].asInt, root["titleTop"].asInt)
            }
        }
    }

    companion object {
        private val ART = Key.key("projects_ui_polish05", "menu_art")
        private val SPACE = Key.key("projects_ui_polish05", "menu_space")
        private val WHITE = TextColor.color(0xFFFFFF)
        private val SPACE_STEPS = listOf(512.0, 256.0, 128.0, 64.0, 32.0, 16.0, 8.0, 4.0, 2.0, 1.0, 0.5, 0.25)
    }
}
