package dev.projects.server.coreloop.ui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import dev.projects.server.mob.MobGrade
import dev.projects.server.mob.MobHud
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
 * a charcoal plate with the rank gem, Lv and name in the vanilla font on the title row, buff / debuff
 * icons and the remaining percentage on the right, a 220 px gauge with quarter marks and a trail of
 * recent damage, and the break gauge under it (white and draining while the boss is broken).
 */
internal object CoreBossBarHud {
    const val PLATE_X = -115
    const val FILL_X = -110
    const val FILL = 220
    const val NAME_X = -99
    private val NAME: TextColor = TextColor.color(0xFFFFFF)
    private val NUMBER: TextColor = TextColor.color(0xFFFFFF)
    private val BROKEN: TextColor = TextColor.color(0xFFF2A0)
    private val NOTICE: TextColor = TextColor.color(0xFF8A70)

    fun percent(health: Double, maximum: Double): String =
        if (!health.isFinite() || !maximum.isFinite() || maximum <= 0.0) "0%" else "${(health / maximum * 100).coerceIn(0.0, 100.0).toInt()}%"

    fun render(hud: MobHud, trail: Double): Component {
        val line = CoreHudLine()
        line.put(PLATE_X, "boss_plate")
        val filled = CoreHudLayout.fillWidth(hud.health, hud.maximumHealth, FILL)
        val trailing = CoreHudLayout.fillWidth(maxOf(hud.health, trail.takeIf { it.isFinite() } ?: hud.health), hud.maximumHealth, FILL)
        line.fill("boss_fill", FILL_X, filled, 128)
        if (trailing > filled) line.fill("boss_trail", FILL_X + filled, trailing - filled, 128)
        for (q in 1..3) line.put(FILL_X + FILL * q / 4, "boss_tick")
        when {
            hud.broken != null -> line.fill("boss_brkw", FILL_X, CoreHudLayout.fillWidth(hud.broken, 1.0, FILL), 128)
            hud.breakGauge != null -> line.fill("boss_brk", FILL_X, CoreHudLayout.fillWidth(hud.breakGauge, 1.0, FILL), 128)
        }
        val pct = percent(hud.health, hud.maximumHealth)
        val pctX = FILL_X + FILL - (CoreHudGlyphs.numberWidth(pct) - 1)
        line.number(pctX, pct, CoreHudGlyphs.NUM_BOSS, NUMBER)
        hud.effects.take(MAX_ICONS).forEachIndexed { index, effect -> line.put(pctX - 3 - (index + 1) * ICON_PITCH, "boss_icon_${effect.icon}") }
        val level = "Lv${hud.level.coerceIn(0, 999)}"
        line.vanilla(NAME_X, level, CoreMobNameplate.LEVEL)
        val title = if (hud.broken != null) "崩れた！ 被ダメ+30%" else hud.notice ?: hud.name
        line.vanilla(NAME_X + CoreUiComponents.width(level) + 4, title,
            if (hud.broken != null) BROKEN else if (hud.notice != null) NOTICE else NAME)
        return line.build()
    }

    const val ICON_PITCH = 10
    const val MAX_ICONS = 6
}

/**
 * Overhead mob HUD, one text-display line per row (top to bottom): buff / debuff / crowd-control icons,
 * Lv + name in the vanilla font, the HP gauge (with the break gauge under it from 精鋭 up) in a frame that
 * shows the grade, and the mob's MODs. Every line returns to its anchor, so the display centres each row.
 */
internal object CoreMobNameplate {
    val LEVEL: TextColor = TextColor.color(0x80FF20)
    private val ATTACK: TextColor = TextColor.color(0xFF8A70)
    private val MODS: TextColor = TextColor.color(0xD8D2C4)
    const val FILL = 60
    const val FILL_X = -FILL / 2
    const val FRAME_X = FILL_X - 10
    const val ICON_PITCH = 10
    const val MAX_ICONS = 6

    fun color(grade: MobGrade): TextColor = TextColor.color(when (grade) {
        MobGrade.COMMON -> 0xECE6DE
        MobGrade.ELITE -> 0x6EA8FF
        MobGrade.RARE -> 0xC48CFF
        MobGrade.UNIQUE -> 0xFFD25A
        MobGrade.CALAMITY -> 0xFF7A3A
    })

    fun lines(hud: MobHud): List<Component> = buildList {
        val effects = hud.effects.take(MAX_ICONS)
        if (effects.isNotEmpty()) add(CoreHudLine().apply {
            val left = -(effects.size * ICON_PITCH - 1) / 2
            effects.forEachIndexed { index, effect -> put(left + index * ICON_PITCH, "plate_icon_${effect.icon}") }
        }.build())
        add(CoreHudLine().apply {
            val level = "Lv${hud.level.coerceIn(0, 999)}"
            val title = hud.notice ?: hud.name
            val levelWidth = CoreUiComponents.width(level)
            val left = -(levelWidth + 4 + CoreUiComponents.width(title)) / 2
            vanilla(left, level, LEVEL)
            vanilla(left + levelWidth + 4, title, if (hud.notice != null) ATTACK else color(hud.grade))
        }.build())
        add(CoreHudLine().apply {
            put(FRAME_X, "plate_frame_${hud.grade.name.lowercase()}")
            fill("plate_hp", FILL_X, CoreHudLayout.fillWidth(hud.health, hud.maximumHealth, FILL), 32)
            when {
                hud.broken != null -> fill("plate_brkw", FILL_X, CoreHudLayout.fillWidth(hud.broken, 1.0, FILL), 32)
                hud.breakGauge != null -> fill("plate_brk", FILL_X, CoreHudLayout.fillWidth(hud.breakGauge, 1.0, FILL), 32)
            }
        }.build())
        if (hud.mods.isNotEmpty()) add(CoreHudLine().apply {
            val text = hud.mods.joinToString("・") { it.label }
            vanilla(-CoreUiComponents.width(text) / 2, text, MODS)
        }.build())
    }

    fun render(hud: MobHud): Component = Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines(hud))
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
