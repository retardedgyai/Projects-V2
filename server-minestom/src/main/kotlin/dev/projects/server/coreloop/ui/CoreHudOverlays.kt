package dev.projects.server.coreloop.ui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import kotlin.math.roundToInt

/** Floating damage numbers: pixel digits in a text display, centred on the hit. */
internal object CoreDamageNumbers {
    private val NORMAL: TextColor = TextColor.color(0xFFF3DC)
    private val CRITICAL: TextColor = TextColor.color(0xFFC94A)
    private val WEAK: TextColor = TextColor.color(0x8FE8FF)
    private const val PITCH = 6

    fun render(amount: Double, critical: Boolean, weak: Boolean): Component {
        val digits = (if (amount.isFinite()) amount.coerceIn(0.0, 9_999_999.0).roundToInt() else 0).toString()
        val color = if (critical) CRITICAL else if (weak) WEAK else NORMAL
        val star = if (critical) CoreHudGlyphs["dmg_crit"].advance else 0
        val mark = if (weak) CoreHudGlyphs["dmg_weak"].advance else 0
        val width = star + CoreHudLine.digitsWidth(digits.length, PITCH, 7) + mark
        val line = CoreHudLine()
        var x = -width / 2
        if (critical) x = line.put(x, "dmg_crit", color) + 1
        line.digits("dmg", x, digits, color, PITCH)
        if (weak) line.put(x + CoreHudLine.digitsWidth(digits.length, PITCH, 7), "dmg_weak", color)
        return line.build()
    }
}

/**
 * Boss bar drawn by the name component of a PINK boss bar (whose vanilla sprites the pack blanks):
 * a charcoal plate with the rank gem, the name in the vanilla font on the title row, the remaining
 * percentage on the right, and a 220 px gauge with quarter marks and a trail of recent damage.
 */
internal object CoreBossBarHud {
    const val PLATE_X = -115
    const val FILL_X = -110
    const val FILL = 220
    const val NAME_X = -99
    private val NAME: TextColor = TextColor.color(0xFFFFFF)
    private val NUMBER: TextColor = TextColor.color(0xFFFFFF)

    fun percent(health: Double, maximum: Double): String =
        if (!health.isFinite() || !maximum.isFinite() || maximum <= 0.0) "0%" else "${(health / maximum * 100).coerceIn(0.0, 100.0).toInt()}%"

    fun render(name: String, health: Double, maximum: Double, trail: Double): Component {
        val line = CoreHudLine()
        line.put(PLATE_X, "boss_plate")
        val filled = CoreHudLayout.fillWidth(health, maximum, FILL)
        val trailing = CoreHudLayout.fillWidth(maxOf(health, trail.takeIf { it.isFinite() } ?: health), maximum, FILL)
        line.fill("boss_fill", FILL_X, filled, 128)
        if (trailing > filled) line.fill("boss_trail", FILL_X + filled, trailing - filled, 128)
        for (q in 1..3) line.put(FILL_X + FILL * q / 4, "boss_tick")
        val pct = percent(health, maximum)
        line.number(FILL_X + FILL - (CoreHudGlyphs.numberWidth(pct) - 1), pct, CoreHudGlyphs.NUM_BOSS, NUMBER)
        line.vanilla(NAME_X, name, NAME)
        return line.build()
    }
}

/** Mob name tag: 12 px name (or the attack being prepared) over level, 40 px HP gauge and weakness. */
internal object CoreMobNameplate {
    enum class Rank(val glyph: String, val color: TextColor) {
        NORMAL("normal", TextColor.color(0xEDE3CF)),
        ELITE("elite", TextColor.color(0xD9B3FF)),
        BOSS("boss", TextColor.color(0xFFC77A)),
    }
    private val ATTACK: TextColor = TextColor.color(0xFF8A70)
    private val LEVEL: TextColor = TextColor.color(0xC9CED6)
    const val FILL = 40

    /** [weakness]: "fire" / "ice" / anything else is thunder (matches QuestMobArchetype.weakness). */
    fun render(level: Int, name: String, health: Double, maximum: Double, rank: Rank, weakness: String, attack: String?): Component {
        val line = CoreHudLine()
        val title = attack ?: name
        line.text(-CoreHudGlyphs.textWidth(title) / 2, title, CoreHudGlyphs.TEXT_WORLD, if (attack != null) ATTACK else rank.color)
        val lv = "Lv${level.coerceIn(0, 999)}"
        val lvWidth = CoreHudLine.digitsWidth(lv.length)
        val left = -(lvWidth + 2 + FILL + 2 + 2 + 7) / 2
        line.digits("tag_tiny", left, lv, LEVEL)
        val frame = left + lvWidth + 2
        line.put(frame, "tag_frame")
        line.fill("tag_${rank.glyph}", frame + 1, CoreHudLayout.fillWidth(health, maximum, FILL), 32)
        line.put(frame + FILL + 4, "weak_" + when (weakness) { "fire" -> "fire"; "ice" -> "ice"; else -> "thunder" })
        return line.build()
    }
}

/** Pale "recent damage" trail: holds briefly after a hit, then drains toward the current value. */
internal class CoreHudTrail(private val holdMillis: Long = 450, private val drainPerSecond: Double = 0.5) {
    private var shown = Double.NaN
    private var previous = Double.NaN
    private var holdUntil = 0L
    private var updatedAt = 0L

    fun update(current: Double, maximum: Double, now: Long): Double {
        if (!current.isFinite() || !maximum.isFinite() || maximum <= 0.0) return current
        if (!shown.isFinite() || current >= shown) shown = current
        else {
            if (previous.isFinite() && current < previous) holdUntil = now + holdMillis
            if (now >= holdUntil) shown = maxOf(current, shown - maximum * drainPerSecond * (now - updatedAt).coerceIn(0, 1000) / 1000.0)
        }
        previous = current
        updatedAt = now
        return shown
    }
}
