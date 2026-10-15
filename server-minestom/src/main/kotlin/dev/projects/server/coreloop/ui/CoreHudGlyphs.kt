package dev.projects.server.coreloop.ui

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor

/**
 * Pixel HUD glyph table written by scripts/build_combat_hud.py. Every glyph is drawn at one texel
 * per GUI pixel; its vertical position is baked into its font provider, so callers only place x.
 */
internal object CoreHudGlyphs {
    data class Glyph(val font: Key, val char: Char, val advance: Int)

    private const val GLYPHS = "core-ui-pack/assets/projects/menu/hud-glyphs.tsv"
    private const val TEXT = "core-ui-pack/assets/projects/menu/hud-text.tsv"
    val TEXT_SLOT: Key = Key.key("projects", "hud_text_slot")
    val TEXT_WORLD: Key = Key.key("projects", "hud_text")
    val TEXT_BOSS: Key = Key.key("projects", "hud_text_boss")

    private fun lines(resource: String): List<List<String>> =
        requireNotNull(CoreHudGlyphs::class.java.classLoader.getResourceAsStream(resource)) { "Missing generated HUD table: $resource" }
            .bufferedReader().use { reader -> reader.readLines().filter { it.isNotBlank() && !it.startsWith('#') }.map { it.split('\t') } }

    private val glyphs: Map<String, Glyph> by lazy {
        lines(GLYPHS).associate { (name, font, code, advance) -> name to Glyph(Key.key(font), code.toInt(16).toChar(), advance.toInt()) }
    }
    /** Codepoint → (private glyph, advance). Shared by every projects:hud_text* font. */
    private val text: Map<Int, Pair<Char, Int>> by lazy {
        lines(TEXT).associate { (codepoint, glyph, advance) -> codepoint.toInt(16) to (glyph.toInt(16).toChar() to advance.toInt()) }
    }

    operator fun get(name: String): Glyph = glyphs[name] ?: error("Missing HUD glyph $name")

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
            listOf(TEXT_SLOT, TEXT_WORLD, TEXT_BOSS).flatMap { font -> text.values.map { (font.asString() to it.first) to it.second } }
    }

    /** Client advance of a generated HUD glyph (null for anything this table does not own). */
    fun advanceOf(font: Key, char: Char): Int? = byGlyph[font.asString() to char] ?: if (font.namespace() == "projects" &&
        font.value().startsWith("hud")) when (char.code) {
            in CoreHudLine.SPACE_POSITIVE..CoreHudLine.SPACE_POSITIVE + 11 -> 1 shl (char.code - CoreHudLine.SPACE_POSITIVE)
            in CoreHudLine.SPACE_NEGATIVE..CoreHudLine.SPACE_NEGATIVE + 11 -> -(1 shl (char.code - CoreHudLine.SPACE_NEGATIVE))
            else -> null
        } else null
}

/**
 * One line of HUD glyphs addressed by absolute x (GUI px from the line's anchor).
 * Signed spaces move a cursor between placements; [build] returns the cursor to 0 so the
 * component's net advance is zero and the client centres it exactly on its anchor.
 * Every projects:hud* font carries the same signed spaces, so consecutive glyphs of one font
 * and colour share a single text component (an action-bar update stays small).
 */
internal class CoreHudLine {
    private val runs = mutableListOf<Component>()
    private var runFont: Key? = null
    private var runColor: TextColor? = null
    private val run = StringBuilder()
    private var cursor = 0
    private var target = 0

    private fun flush() {
        val font = runFont ?: return
        if (run.isNotEmpty()) runs += Component.text(run.toString(), runColor).font(font)
            .shadowColor(net.kyori.adventure.text.format.ShadowColor.none())
            .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
        run.setLength(0)
    }

    private fun emit(font: Key, color: TextColor, char: Char, advance: Int) {
        if (font != runFont || color != runColor) { flush(); runFont = font; runColor = color }
        appendSpace(target - cursor)
        run.append(char)
        cursor = target + advance
        target = cursor
    }

    private fun appendSpace(pixels: Int) {
        var left = kotlin.math.abs(pixels).coerceAtMost(4095)
        val base = if (pixels < 0) SPACE_NEGATIVE else SPACE_POSITIVE
        for (bit in 11 downTo 0) if (left >= 1 shl bit) { run.append((base + bit).toChar()); left -= 1 shl bit }
    }

    fun put(x: Int, glyph: CoreHudGlyphs.Glyph, color: TextColor = NamedTextColor.WHITE): Int {
        target = x
        emit(glyph.font, color, glyph.char, glyph.advance)
        return x + glyph.advance - 1
    }

    fun put(x: Int, name: String, color: TextColor = NamedTextColor.WHITE): Int = put(x, CoreHudGlyphs[name], color)

    /** Fill [width] px with `prefix_1..prefix_128` segments, largest first (keeps 4 px texture rhythm). */
    fun fill(prefix: String, x: Int, width: Int, largest: Int = 64): Int {
        var at = x
        var left = width.coerceAtLeast(0)
        var size = largest
        while (left > 0 && size > 0) {
            if (left >= size) { put(at, "${prefix}_$size"); at += size; left -= size } else size /= 2
        }
        return at
    }

    /** Small digits on a readout strip; [pitch] lets neighbouring cells share their dark edge. */
    fun digits(prefix: String, x: Int, value: String, color: TextColor = NamedTextColor.WHITE, pitch: Int = 4) {
        value.forEachIndexed { index, c -> put(x + index * pitch, "${prefix}_$c", color) }
    }

    fun text(x: Int, value: String, font: Key, color: TextColor) {
        var at = x
        for (codepoint in value.codePoints().toArray()) {
            val glyph = CoreHudGlyphs.textGlyph(codepoint)
            if (glyph != null) {
                target = at
                emit(font, color, glyph, CoreHudGlyphs.textAdvance(codepoint) - CoreHudGlyphs.TEXT_KERN)
            }
            at += CoreHudGlyphs.textAdvance(codepoint)
        }
    }

    fun build(): Component {
        if (runFont != null) appendSpace(-cursor)
        flush()
        return Component.empty().children(runs)
    }

    companion object {
        /** +2^n at F800+n and -2^n at F810+n in every projects:hud* font (build_combat_hud.SPACE_BASE). */
        const val SPACE_POSITIVE = 0xF800
        const val SPACE_NEGATIVE = 0xF810
        fun digitsWidth(count: Int, pitch: Int = 4, glyph: Int = 5): Int = if (count <= 0) 0 else (count - 1) * pitch + glyph
    }
}
