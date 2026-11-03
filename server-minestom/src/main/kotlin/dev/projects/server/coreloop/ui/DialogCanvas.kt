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
 * Callers work in design px (the layout for GUI scale 4); [unit] GUI px per design px picks the size baked for the
 * player's GUI scale. The canvas is [lineCount] lines of exactly [width] GUI px. Its y = 0 is the top of the first
 * line, which sits [originTop] GUI px below where the caller measures from (0 for the body, the title's top for the title).
 */
internal class DialogCanvas(private val size: Size, private val space: Map<Double, Int>, private val width: Double, private val lineCount: Int,
                            private val originTop: Int = 0, private val unit: Double = size.unit, private val styles: Map<String, Font> = size.styles) {
    enum class Align { LEFT, CENTER, RIGHT }

    private class Piece(val line: Int, val x: Double, val advance: Double, val component: Component, val layer: Int)

    private val pieces = mutableListOf<Piece>()

    /** [baseline] is where the letters stand. Lines are 9 GUI px apart and a line's baseline is 7 px below its top. */
    fun text(style: String, x: Double, baseline: Double, text: String, color: TextColor, align: Align = Align.LEFT,
             click: ClickEvent<*>? = null, hover: Component? = null): Double {
        val font = styles.getValue(style)
        val advance = text.codePoints().toArray().sumOf { font.advances[it] ?: missing(style, it) }
        val y = (baseline * unit).roundToInt() - originTop - 7
        val line = maxOf(0, floor(y / 9.0).toInt())
        val shift = y - line * 9
        require(shift in 0..8) { "Text at baseline $baseline is above the canvas" }
        val gx = x * unit
        val left = when (align) { Align.LEFT -> gx; Align.CENTER -> gx - advance / 2; Align.RIGHT -> gx - advance }
        var component = Component.text(text, color).font(Key.key(font.font + shift)).shadowColor(ShadowColor.none())
        if (click != null) component = component.clickEvent(click)
        if (hover != null) component = component.hoverEvent(HoverEvent.showText(hover))
        add(line, left, advance, component)
        return advance / unit
    }

    /** A baked sprite whose top-left corner is at ([x], [top]). Only the positions the build script baked exist. */
    fun sprite(name: String, x: Double, top: Double, click: ClickEvent<*>? = null, hover: Component? = null) = sprite(name, x, top, click, hover, 1)

    /**
     * A sprite drawn under everything else. The client draws a line's glyphs left to right and the lines top to
     * bottom, so a translucent panel placed after a word covers it (2026-11-02, in game); backgrounds go first.
     */
    fun background(name: String, x: Double, top: Double) = sprite(name, x, top, null, null, 0)

    /** A sprite drawn over everything else on its line (a dimming veil over a used skill). */
    fun overlay(name: String, x: Double, top: Double) = sprite(name, x, top, null, null, 2)

    private fun sprite(name: String, x: Double, top: Double, click: ClickEvent<*>?, hover: Component?, layer: Int) {
        val sprite = size.sprites[name] ?: error("Unknown menu sprite $name")
        if (sprite.tiles.isNotEmpty()) {
            // Too big for the client's 256 px font texture page: baked as tiles, each placed on its own.
            sprite.tiles.forEach { tile -> sprite(tile.sprite, x + tile.dx, top + tile.dy, click, hover, layer) }
            return
        }
        val y = (top * unit).roundToInt() - originTop
        val line = maxOf(0, floor(y / 9.0).toInt())
        // Short sprites hang from the line above (the client rejects ascent > height); the script baked the same choice.
        // Backgrounds hang from the first line, so they are drawn before every line below them (layer 0 first on it).
        val choice = (if (layer == 0) listOf(0 to 7 - y) else listOf(line to 7 - (y - line * 9), line - 1 to 7 - (y - (line - 1) * 9)))
            .firstOrNull { (l, ascent) -> l >= 0 && sprite.chars.containsKey(ascent) }
            ?: error("Sprite $name was not baked for y=$top")
        var component = Component.text(String(Character.toChars(sprite.chars.getValue(choice.second)))).font(size.art)
            .color(WHITE).shadowColor(ShadowColor.none())
        if (click != null) component = component.clickEvent(click)
        if (hover != null) component = component.hoverEvent(HoverEvent.showText(hover))
        add(choice.first, x * unit, sprite.advance.toDouble(), component, layer)
    }

    /** Any other component [advance] GUI px wide (a player's face is 8), on the line holding [baseline]. */
    fun inline(x: Double, baseline: Double, advance: Double, component: Component) {
        val y = (baseline * unit).roundToInt() - originTop - 7
        add(maxOf(0, floor(y / 9.0).toInt()), x * unit, advance, component)
    }

    /** In design px. */
    fun width(style: String, text: String): Double {
        val font = styles.getValue(style)
        return text.codePoints().toArray().sumOf { font.advances[it] ?: missing(style, it) } / unit
    }

    /** GUI px in design px. */
    fun design(gui: Double) = gui / unit

    private fun add(line: Int, x: Double, advance: Double, component: Component, layer: Int = 1) {
        require(line < lineCount) { "Line $line is past the canvas" }
        pieces += Piece(line, x, advance, component, layer)
    }

    /** Every line, padded to the full width so the client's centring leaves each one at the same left edge. */
    fun build(): Component {
        val out = Component.text()
        for (line in 0 until lineCount) {
            if (line > 0) out.append(Component.newline())
            out.append(row(pieces.filter { it.line == line }.sortedWith(compareBy({ it.layer }, { it.x }))))
        }
        return out.build()
    }

    /** One line only, ending at [width] (the title). */
    fun buildLine(): Component = row(pieces.sortedWith(compareBy({ it.layer }, { it.x })))

    private fun row(items: List<Piece>): Component {
        val out = Component.text()
        var cursor = 0.0
        for (piece in items) {
            // Follow where the rounded spaces really put us, or the quarter-pixel error piles up along a row of
            // narrow sprites (20 bars at GUI scale 3 drifted 1.75 px).
            cursor += space(out, piece.x - cursor)
            out.append(piece.component)
            cursor += piece.advance
        }
        space(out, width - cursor)
        return out.build()
    }

    /** Emits a space of [distance] rounded to a quarter pixel and returns the rounded distance. */
    private fun space(out: net.kyori.adventure.text.TextComponent.Builder, distance: Double): Double {
        val rounded = (distance * 4).roundToInt() / 4.0
        var left = rounded
        if (left == 0.0) return 0.0
        val text = StringBuilder()
        val sign = if (left < 0) -1 else 1
        left = abs(left)
        for (step in SPACE_STEPS) while (left >= step - 1e-9) { text.appendCodePoint(space.getValue(sign * step)); left -= step }
        out.append(Component.text(text.toString()).font(SPACE))
        return rounded
    }

    private fun missing(style: String, codePoint: Int): Double {
        System.err.println("MENU_FONT_MISSING: U+%04X in %s (rerun scripts/build_menu_dialog_assets.py)".format(codePoint, style))
        return 6.0
    }

    class Font(val font: String, val advances: Map<Int, Double>)
    class Tile(val sprite: String, val dx: Int, val dy: Int)
    class Sprite(val advance: Int, val chars: Map<Int, Int>, val tiles: List<Tile> = emptyList())

    /** One baked size (GUI scale 4, 3 or 2). */
    class Size(val unit: Double, val styles: Map<String, Font>, val sprites: Map<String, Sprite>, val art: Key, val layout: JsonObject,
               val canvasWidth: Int, val lines: Int, val titleWidth: Int, val titleX: Int, val titleTop: Int)

    class Metrics(val sizes: Map<Int, Size>, val space: Map<Double, Int>) {
        /** The size baked for [guiScale]; scales without their own size use the nearest one. */
        fun forScale(guiScale: Int): Size = sizes[guiScale.coerceIn(sizes.keys.min(), sizes.keys.max())]!!

        companion object {
            fun parse(bytes: ByteArray): Metrics {
                val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
                val sizes = root.getAsJsonObject("sizes").entrySet().associate { (tag, value) -> tag.toInt() to size(value.asJsonObject) }
                val space = root.getAsJsonObject("space").entrySet().associate { (advance, cp) -> advance.toDouble() to cp.asInt }
                return Metrics(sizes, space)
            }

            private fun size(o: JsonObject): Size {
                val styles = o.getAsJsonObject("styles").entrySet().associate { (name, value) ->
                    val s = value.asJsonObject
                    name to Font(s["font"].asString, s.getAsJsonObject("advances").entrySet().associate { (cp, a) -> cp.toInt() to a.asDouble })
                }
                val sprites = o.getAsJsonObject("sprites").entrySet().associate { (name, value) ->
                    val s = value.asJsonObject
                    if (s.has("tiles")) name to Sprite(0, emptyMap(), s.getAsJsonArray("tiles").map {
                        val t = it.asJsonObject; Tile(t["sprite"].asString, t["dx"].asInt, t["dy"].asInt) })
                    else name to Sprite(s["advance"].asInt, s.getAsJsonObject("chars").entrySet().associate { (a, cp) -> a.toInt() to cp.asInt })
                }
                return Size(o["unit"].asDouble, styles, sprites, Key.key(o["art"].asString), o.getAsJsonObject("layout"),
                    o["canvasWidth"].asInt, o["lines"].asInt, o["titleWidth"].asInt, o["titleX"].asInt, o["titleTop"].asInt)
            }
        }
    }

    companion object {
        private val SPACE = Key.key("projects_ui_polish05", "menu_space")
        private val WHITE = TextColor.color(0xFFFFFF)
        private val SPACE_STEPS = listOf(1024.0, 512.0, 256.0, 128.0, 64.0, 32.0, 16.0, 8.0, 4.0, 2.0, 1.0, 0.5, 0.25)
    }
}
