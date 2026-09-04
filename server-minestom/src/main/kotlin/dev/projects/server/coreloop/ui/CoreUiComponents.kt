package dev.projects.server.coreloop.ui

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.format.ShadowColor
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Presentation only. No input, damage, account or progression state is owned by this class. */
object CoreUiComponents {
    val GOLD: TextColor = TextColor.color(0xD5BB7D)
    val IVORY: TextColor = TextColor.color(0xE8E3D8)
    val MUTED: TextColor = TextColor.color(0x929899)
    val RED: TextColor = TextColor.color(0xE49792)
    val BLUE: TextColor = TextColor.color(0x83BBD8)
    val DEFAULT_FONT: Key = Key.key("minecraft", "default")
    val ICON_FONT: Key = Key.key("projects", "core_icons")
    val SPACE_FONT: Key = Key.key("projects", "core_spacing")
    val HUD_FONT: Key = Key.key("projects", "core_hud")
    val MENU_FONT: Key = Key.key("projects", "core_menu")

    fun text(value: String, color: TextColor = IVORY, bold: Boolean = false): Component =
        Component.text(value, color).font(DEFAULT_FONT)
            .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, bold)

    fun icon(icon: CoreUiIcon, packed: Boolean): Component =
        if (packed) glyph(icon.glyph, ICON_FONT) else text(icon.fallback, GOLD)

    internal fun glyph(character: Char, font: Key, color: TextColor = NamedTextColor.WHITE): Component =
        Component.text(character, color).font(font)
            .shadowColor(ShadowColor.none())
            .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false)

    /** Signed pixel advances are isolated from both Japanese text and the user's default font. */
    fun space(pixels: Int): Component {
        var left = pixels.coerceIn(-4095, 4095).let { kotlin.math.abs(it) }
        val base = if (pixels < 0) 0xE180 else 0xE100
        val chars = buildString {
            for (bit in 11 downTo 0) if (left >= 1.shl(bit)) {
                append((base + bit).toChar()); left -= 1.shl(bit)
            }
        }
        return Component.text(chars).font(SPACE_FONT)
            .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false)
    }

    fun inventoryTitle(title: String, packed: Boolean): Component {
        if (!packed) return text(trimWidth(title, 158), NamedTextColor.DARK_GRAY)
        // Verified on Vanilla 26.2: extractLabels runs before extractSlots, so this backs the items.
        return Component.empty().append(space(-8)).append(glyph('\uE200', MENU_FONT))
            .append(space(-169)).append(text(trimWidth(title, 158, bold = true), GOLD, true))
    }

    /** Avoid splitting surrogate pairs, and reserve the ellipsis in the same pixel budget. */
    internal fun trimWidth(value: String, maximum: Int, bold: Boolean = false): String {
        if (width(value, bold) <= maximum) return value
        val suffix = "…"
        val remaining = maximum - width(suffix, bold)
        if (remaining < 0) return ""
        return buildString {
            var used = 0
            for (code in value.codePoints().toArray()) {
                val character = String(Character.toChars(code))
                val advance = width(character, bold)
                if (used + advance > remaining) break
                append(character)
                used += advance
            }
            append(suffix)
        }
    }

    fun hud(state: CoreHudState, packed: Boolean): Component {
        if (!packed) {
            val cooldowns = state.skills.take(3).joinToString(" / ") {
                "${it.key}:${if (it.remainingSeconds <= 0) "可" else "${ceil(it.remainingSeconds).toInt()}秒"}"
            }
            return text("HP ${number(state.health)}/${number(state.maxHealth)}  マナ ${number(state.mana)}/${number(state.maxMana)}", GOLD)
                .append(text("  $cooldowns", IVORY))
                .append(if (state.hint.isBlank()) Component.empty() else text("  ${state.hint}", MUTED))
        }
        var result = Component.empty()
            .append(icon(CoreUiIcon.HEALTH, true)).append(space(3))
            .append(gauge(state.health, state.maxHealth, 0xE300))
            .append(space(4)).append(text("${number(state.health)}/${number(state.maxHealth)}", RED))
            .append(space(10)).append(icon(CoreUiIcon.MANA, true)).append(space(3))
            .append(gauge(state.mana, state.maxMana, 0xE320))
            .append(space(4)).append(text("${number(state.mana)}", BLUE)).append(space(12))
        state.skills.take(3).forEach { skill ->
            val ready = skill.remainingSeconds <= 0.0
            val usable = ready && state.mana >= skill.manaCost
            val progress = if (ready) 10 else ((1.0 - skill.remainingSeconds / skill.totalSeconds.coerceAtLeast(0.1)) * 10).toInt().coerceIn(0, 10)
            result = result.append(icon(skill.icon, true)).append(space(2))
                .append(text(skill.key, MUTED)).append(space(2))
                .append(text(if (ready) if (usable) "可" else "MP" else ceil(skill.remainingSeconds).toInt().toString(), if (usable) GOLD else MUTED))
                .append(space(2)).append(glyph((0xE340 + progress).toChar(), HUD_FONT)).append(space(8))
        }
        return result
    }

    private fun gauge(value: Double, maximum: Double, base: Int): Component {
        val fraction = if (!value.isFinite() || !maximum.isFinite() || maximum <= 0.0) 0.0 else (value / maximum).coerceIn(0.0, 1.0)
        return glyph((base + (fraction * 20).roundToInt()).toChar(), HUD_FONT)
    }

    private fun number(value: Double): String = if (value.isFinite()) value.coerceAtLeast(0.0).roundToInt().toString() else "0"

    /** Vanilla default glyph advances; CJK uses the game's unifont width, never a replacement font. */
    fun width(value: String, bold: Boolean = false): Int = value.codePoints().toArray().sumOf { code ->
        val advance = when (code.toChar()) {
            ' ' -> 4
            'i', '!', '.', ',', ':', ';', '|', '\'' -> 2
            'l' -> 3
            'I', '[', ']', 't' -> 4
            'f', 'k', '<', '>', '(', ')', '{', '}' -> 5
            '@', '~' -> 7
            else -> if (code in 0x21..0x7E) 6 else 9
        }
        advance + if (bold && code != 32) 1 else 0
    }
}
