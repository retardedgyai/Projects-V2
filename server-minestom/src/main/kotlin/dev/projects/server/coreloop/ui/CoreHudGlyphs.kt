package dev.projects.server.coreloop.ui

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration

/**
 * HUD glyph table written by scripts/build_combat_hud.py. Every glyph's vertical position is
 * baked into its font provider, so callers only place x.
 */
internal object CoreHudGlyphs {
    data class Glyph(val font: Key, val char: Char, val advance: Int)

    private const val GLYPHS = "core-ui-pack/assets/projects/menu/hud-glyphs.tsv"
    private const val TEXT = "core-ui-pack/assets/projects/menu/hud-text.tsv"
    val TEXT_WORLD: Key = Key.key("projects", "hud_text")

    /** Fonts that draw the game's own ascii sheet through U+E600+n (build_combat_hud.NUMBER_FONTS). */
    val NUM_GAUGE: Key = Key.key("projects", "hud_num_gauge")
    val NUM_CD: Key = Key.key("projects", "hud_num_cd")
    val NUM_BOSS: Key = Key.key("projects", "hud_num_boss")
    private val numberFonts = setOf(NUM_GAUGE, NUM_CD, NUM_BOSS)
    const val ASCII_BASE = 0xE600

    /** Vanilla ascii advances for the characters the HUD prints (ink width + 1, read from 26.2's ascii.png). */
    fun numberAdvance(c: Char): Int = when (c) {
        ' ' -> 4
        '.', ':', '!', ',' -> 2
        else -> 6
    }

    fun numberWidth(value: String): Int = value.sumOf(::numberAdvance)

    private fun lines(resource: String): List<List<String>> =
        requireNotNull(CoreHudGlyphs::class.java.classLoader.getResourceAsStream(resource)) { "Missing generated HUD table: $resource" }
            .bufferedReader().use { reader -> reader.readLines().filter { it.isNotBlank() && !it.startsWith('#') }.map { it.split('\t') } }

    private val glyphs: Map<String, Glyph> by lazy {
        lines(GLYPHS).associate { (name, font, code, advance) -> name to Glyph(Key.key(font), code.toInt(16).toChar(), advance.toInt()) }
    }
    /** Codepoint → (private glyph, advance) for the world name-tag font. */
    private val text: Map<Int, Pair<Char, Int>> by lazy {
        lines(TEXT).associate { (codepoint, glyph, advance) -> codepoint.toInt(16) to (glyph.toInt(16).toChar() to advance.toInt()) }
    }

    operator fun get(name: String): Glyph = glyphs[name] ?: error("Missing HUD glyph $name")
    fun has(name: String): Boolean = name in glyphs

    /** Hard-pixel 12 px text is outlined; consecutive glyphs share one outline column. */
    const val TEXT_KERN = -1
    const val SPACE_ADVANCE = 4
    const val WIDE_SPACE_ADVANCE = 12

    fun textAdvance(codepoint: Int): Int = when (codepoint) {
        ' '.code -> SPACE_ADVANCE
        '　'.code -> WIDE_SPACE_ADVANCE
        else -> text[codepoint]?.let { it.second + TEXT_KERN } ?: 0
    }

    fun textWidth(value: String): Int = value.codePoints().toArray().sumOf(::textAdvance).let { if (it > 0) it - TEXT_KERN else 0 }

    fun textGlyph(codepoint: Int): Char? = text[codepoint]?.first

    private val byGlyph: Map<Pair<String, Char>, Int> by lazy {
        glyphs.values.associate { (it.font.asString() to it.char) to it.advance } +
            text.values.map { (TEXT_WORLD.asString() to it.first) to it.second }
    }

    /** Client advance of a generated HUD glyph (null for anything this table does not own). */
    fun advanceOf(font: Key, char: Char): Int? {
        byGlyph[font.asString() to char]?.let { return it }
        if (font.namespace() != "projects" || !font.value().startsWith("hud")) return null
        return when (char.code) {
            in CoreHudLine.SPACE_POSITIVE..CoreHudLine.SPACE_POSITIVE + 11 -> 1 shl (char.code - CoreHudLine.SPACE_POSITIVE)
            in CoreHudLine.SPACE_NEGATIVE..CoreHudLine.SPACE_NEGATIVE + 11 -> -(1 shl (char.code - CoreHudLine.SPACE_NEGATIVE))
            in ASCII_BASE..ASCII_BASE + 255 -> if (font in numberFonts) numberAdvance((char.code - ASCII_BASE).toChar()) else null
            else -> null
        }
    }
}

/**
 * One line of HUD glyphs addressed by absolute x (GUI px from the line's anchor).
 * Signed spaces move a cursor between placements; [build] returns the cursor to 0 so the
 * component's net advance is zero and the client centres it exactly on its anchor.
 * Every projects:hud* font carries the same signed spaces, so consecutive glyphs of one font,
 * colour and shadow share a single text component (an action-bar update stays small).
 * Vanilla-font text (notices, boss name) is measured with [CoreUiComponents.width].
 */
internal class CoreHudLine {
    /** [font] null = the vanilla default font. Numbers and vanilla text keep the vanilla drop shadow. */
    private data class Style(val font: Key?, val color: TextColor, val shadow: Boolean)

    private val runs = mutableListOf<Component>()
    private var style: Style? = null
    private val run = StringBuilder()
    private var cursor = 0

    private fun flush() {
        val current = style ?: return
        if (run.isNotEmpty()) {
            var component = Component.text(run.toString(), current.color)
                .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false)
            if (current.font != null) component = component.font(current.font)
            if (!current.shadow) component = component.shadowColor(ShadowColor.none())
            runs += component
        }
        run.setLength(0)
    }

    private fun use(next: Style) {
        if (next != style) { flush(); style = next }
    }

    /** Move the cursor to [x]; spaces only exist in projects:hud* fonts, so a vanilla run hands over. */
    private fun moveTo(x: Int) {
        val pixels = x - cursor
        if (pixels == 0) return
        if (style?.font == null) use(Style(SPACE_FONT, style?.color ?: NamedTextColor.WHITE, false))
        var left = kotlin.math.abs(pixels).coerceAtMost(4095)
        val base = if (pixels < 0) SPACE_NEGATIVE else SPACE_POSITIVE
        for (bit in 11 downTo 0) if (left >= 1 shl bit) { run.append((base + bit).toChar()); left -= 1 shl bit }
        cursor = x
    }

    private fun emit(x: Int, next: Style, char: Char, advance: Int) {
        moveTo(x)
        use(next)
        run.append(char)
        cursor += advance
    }

    fun put(x: Int, glyph: CoreHudGlyphs.Glyph, color: TextColor = NamedTextColor.WHITE): Int {
        emit(x, Style(glyph.font, color, false), glyph.char, glyph.advance)
        return x + glyph.advance - 1
    }

    fun put(x: Int, name: String, color: TextColor = NamedTextColor.WHITE): Int = put(x, CoreHudGlyphs[name], color)

    /** Fill [width] px with `prefix_1..prefix_largest` segments, largest first. */
    fun fill(prefix: String, x: Int, width: Int, largest: Int = 32): Int {
        var at = x
        var left = width.coerceAtLeast(0)
        var size = largest
        while (left > 0 && size > 0) {
            if (left >= size) { put(at, "${prefix}_$size"); at += size; left -= size } else size /= 2
        }
        return at
    }

    /** Small pixel digits (name tags, damage numbers); [pitch] lets neighbouring cells share an edge. */
    fun digits(prefix: String, x: Int, value: String, color: TextColor = NamedTextColor.WHITE, pitch: Int = 4) {
        value.forEachIndexed { index, c -> put(x + index * pitch, "${prefix}_$c", color) }
    }

    /** The game's own font, drawn through a hud_num* font at that font's row; returns the ink width. */
    fun number(x: Int, value: String, font: Key, color: TextColor = NamedTextColor.WHITE): Int {
        var at = x
        for (c in value) {
            val advance = CoreHudGlyphs.numberAdvance(c)
            emit(at, Style(font, color, true), (CoreHudGlyphs.ASCII_BASE + c.code).toChar(), advance)
            at += advance
        }
        return at - x - 1
    }

    /** [number] with its ink centred on [centre] (a pixel column, or a half column between two). */
    fun numberCentred(centre: Double, value: String, font: Key, color: TextColor = NamedTextColor.WHITE) {
        val ink = CoreHudGlyphs.numberWidth(value) - 1
        number(kotlin.math.round(centre - (ink - 1) / 2.0).toInt(), value, font, color)
    }

    /** Vanilla default-font text at its natural row (action bar line, boss bar title). */
    fun vanilla(x: Int, value: String, color: TextColor) {
        var at = x
        for (codepoint in value.codePoints().toArray()) {
            val text = String(Character.toChars(codepoint))
            val advance = CoreUiComponents.width(text)
            moveTo(at)
            use(Style(null, color, true))
            run.append(text)
            cursor += advance
            at += advance
        }
    }

    /** World name tags: hard-pixel MaruMinya through projects:hud_text. */
    fun text(x: Int, value: String, font: Key, color: TextColor) {
        var at = x
        for (codepoint in value.codePoints().toArray()) {
            val glyph = CoreHudGlyphs.textGlyph(codepoint)
            if (glyph != null) emit(at, Style(font, color, false), glyph, CoreHudGlyphs.textAdvance(codepoint) - CoreHudGlyphs.TEXT_KERN)
            at += CoreHudGlyphs.textAdvance(codepoint)
        }
    }

    fun build(): Component {
        if (style != null) moveTo(0)
        flush()
        return Component.empty().children(runs)
    }

    companion object {
        /** +2^n at F800+n and -2^n at F810+n in every projects:hud* font (build_combat_hud.SPACE_BASE). */
        const val SPACE_POSITIVE = 0xF800
        const val SPACE_NEGATIVE = 0xF810
        val SPACE_FONT: Key = Key.key("projects", "hud")
        fun digitsWidth(count: Int, pitch: Int = 4, glyph: Int = 5): Int = if (count <= 0) 0 else (count - 1) * pitch + glyph
    }
}
