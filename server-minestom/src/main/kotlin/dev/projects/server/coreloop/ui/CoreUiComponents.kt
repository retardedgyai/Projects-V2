package dev.projects.server.coreloop.ui

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.format.ShadowColor
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

    fun inventoryTitle(title: String, packed: Boolean, forge: Boolean = false, emptyForge: Boolean = false): Component {
        if (!packed) return text(trimWidth(title, 158), NamedTextColor.DARK_GRAY)
        // Verified on Vanilla 26.2: extractLabels runs before extractSlots, so this backs the items.
        val frame = if (emptyForge) '\uE202' else if (forge) '\uE201' else '\uE200'
        return Component.empty().append(space(-8)).append(glyph(frame, MENU_FONT))
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
                val visual = CoreHudLayout.skillVisual(it, state.mana)
                val status = when (visual.frame) {
                    CoreHudLayout.READY -> "可"
                    CoreHudLayout.NO_MANA -> "マナ不足"
                    CoreHudLayout.LOCKED -> "未解放"
                    else -> "${visual.centre}秒"
                }
                "${it.key}:$status"
            }
            return text("HP ${number(state.health)}/${number(state.maxHealth)}  マナ ${number(state.mana)}/${number(state.maxMana)}", GOLD)
                .append(text("  $cooldowns" + (state.charges?.let { "  蓄積 ${it.coerceIn(0,3)}/3" } ?: ""), IVORY))
                .append(if (state.hint.isBlank()) Component.empty() else text("  ${state.hint}", MUTED))
        }
        return CoreHudLayout.render(state)
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
