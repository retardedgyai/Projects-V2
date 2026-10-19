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
 * Boss bar drawn by the name component of a PINK boss bar (whose vanilla sprites the pack blanks).
 * Graded bosses: the level diamond (wings and a crown grow with the grade) over a charcoal plate with the
 * grade + name row, a thick HP gauge with the number inside and the break gauge as cells. A dedicated boss
 * swaps all of it for a frame drawn from the boss (shade wyrm: its fan of wing blades and horned head).
 * Every row sits at its own font's height (scripts/boss_bar_art.py), so text is not tied to the title line.
 */
internal object CoreBossBarHud {
    const val PLATE_X = -118
    const val PLATE_W = 237
    const val EMBLEM_X = -40
    const val HP_X = -112
    const val HP_W = 225
    const val CELL_X = -109
    const val CELL_PITCH = 11
    const val CELLS = 20
    const val WYRM_FRAME_X = -180
    const val WYRM_HP_X = -110
    const val WYRM_HP_W = 221
    const val ICON_PITCH = 10
    const val MAX_ICONS = 6
    private val WHITE: TextColor = TextColor.color(0xFFFFFF)
    private val LEVEL: TextColor = TextColor.color(0xFFD25A)
    private val BROKEN: TextColor = TextColor.color(0xFFF2A0)
    private val NOTICE: TextColor = TextColor.color(0xFF8A70)
    private val WYRM_NAME: TextColor = TextColor.color(0xE0D4FF)

    fun percent(health: Double, maximum: Double): String =
        if (!health.isFinite() || !maximum.isFinite() || maximum <= 0.0) "0%" else "${(health / maximum * 100).coerceIn(0.0, 100.0).toInt()}%"

    private fun grouped(value: Double): String = "%,d".format(java.util.Locale.ROOT, kotlin.math.ceil(value.coerceAtLeast(0.0)).toLong())

    /**
     * Left x that centres text of advance [width] on the art's centre column 0: the ink is width - 1 px
     * (the last advance pixel is spacing), and symmetric art spans -r..r round column 0.
     */
    fun centred(width: Int): Int = -(width - 1) / 2

    fun render(hud: MobHud, trail: Double): Component =
        if (hud.dedicated == "shade_wyrm") wyrm(hud, trail) else graded(hud, trail)

    private fun CoreHudLine.gauge(prefix: String, x: Int, width: Int, hud: MobHud, trail: Double) {
        val filled = CoreHudLayout.fillWidth(hud.health, hud.maximumHealth, width)
        val trailing = CoreHudLayout.fillWidth(maxOf(hud.health, trail.takeIf { it.isFinite() } ?: hud.health), hud.maximumHealth, width)
        fill("${prefix}_fill", x, filled, 128)
        if (trailing > filled) fill("${prefix}_trail", x + filled, trailing - filled, 128)
    }

    private fun CoreHudLine.cells(prefix: String, hud: MobHud) {
        val x0 = CELL_X
        val lit = when {
            hud.broken != null -> (CELLS * hud.broken).roundToInt().coerceIn(0, CELLS)
            else -> (CELLS * (hud.breakGauge ?: 0.0)).roundToInt().coerceIn(0, CELLS)
        }
        val glyph = if (hud.broken != null) "${prefix}_cellw" else "${prefix}_cell"
        for (i in 0 until lit) put(x0 + i * CELL_PITCH, glyph)
    }

    private fun title(hud: MobHud): Pair<String, TextColor>? = when {
        hud.broken != null -> "崩れた！ 被ダメ+30%" to BROKEN
        hud.notice != null -> hud.notice to NOTICE
        else -> null
    }

    private fun graded(hud: MobHud, trail: Double): Component {
        val line = CoreHudLine()
        val grade = (if (hud.grade == MobGrade.COMMON) MobGrade.ELITE else hud.grade).name.lowercase()
        line.put(PLATE_X, "boss_plate_$grade")
        line.put(EMBLEM_X, "boss_emblem_$grade")
        line.gauge("boss", HP_X, HP_W, hud, trail)
        for (q in 1..3) line.put(HP_X + HP_W * q / 4, "boss_tick")
        line.cells("boss", hud)
        hud.effects.take(MAX_ICONS).forEachIndexed { i, effect -> line.put(PLATE_X + 2 + i * ICON_PITCH, "boss_icon_${effect.icon}") }
        val lv = hud.level.coerceIn(0, 999).toString()
        line.uni(centred(CoreHudGlyphs.uniWidth(lv)), lv, CoreHudGlyphs.BOSS_LV, LEVEL)
        val nameX = PLATE_X + 6
        val override = title(hud)
        if (override != null) line.uni(nameX, override.first, CoreHudGlyphs.BOSS_NAME, override.second)
        else {
            val label = (if (hud.grade == MobGrade.COMMON) MobGrade.ELITE else hud.grade).label
            line.uni(nameX, label, CoreHudGlyphs.BOSS_NAME, CoreMobNameplate.color(if (hud.grade == MobGrade.COMMON) MobGrade.ELITE else hud.grade))
            line.uni(nameX + CoreHudGlyphs.uniWidth(label) + 4, hud.name, CoreHudGlyphs.BOSS_NAME, WHITE)
        }
        val pct = percent(hud.health, hud.maximumHealth)
        line.uni(PLATE_X + PLATE_W - 6 - CoreHudGlyphs.uniWidth(pct) + 1, pct, CoreHudGlyphs.BOSS_NAME, WHITE)
        val number = "${grouped(hud.health)} / ${grouped(hud.maximumHealth)}"
        line.uni(centred(CoreHudGlyphs.uniWidth(number)), number, CoreHudGlyphs.BOSS_HP, WHITE)
        return line.build()
    }

    private fun wyrm(hud: MobHud, trail: Double): Component {
        val line = CoreHudLine()
        line.put(WYRM_FRAME_X, "wyrm_frame_l")
        line.put(0, "wyrm_frame_r")
        line.gauge("wyrm", WYRM_HP_X, WYRM_HP_W, hud, trail)
        line.cells("wyrm", hud)
        val icons = hud.effects.take(MAX_ICONS)
        icons.forEachIndexed { i, effect ->
            line.put(WYRM_HP_X + WYRM_HP_W - icons.size * ICON_PITCH + 1 + i * ICON_PITCH, "wyrm_icon_${effect.icon}")
        }
        val lv = "Lv${hud.level.coerceIn(0, 999)}"
        val (title, color) = title(hud) ?: (hud.name to WYRM_NAME)
        val left = centred(CoreHudGlyphs.uniWidth(lv) + 4 + CoreHudGlyphs.uniWidth(title))
        line.uni(left, lv, CoreHudGlyphs.WYRM_NAME, CoreMobNameplate.LEVEL)
        line.uni(left + CoreHudGlyphs.uniWidth(lv) + 4, title, CoreHudGlyphs.WYRM_NAME, color)
        val pct = percent(hud.health, hud.maximumHealth)
        line.uni(WYRM_HP_X + WYRM_HP_W + 2 - CoreHudGlyphs.uniWidth(pct), pct, CoreHudGlyphs.WYRM_PCT, WHITE)
        return line.build()
    }
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
    const val FILL = 61
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
            val left = CoreBossBarHud.centred(effects.size * ICON_PITCH)
            effects.forEachIndexed { index, effect -> put(left + index * ICON_PITCH, "plate_icon_${effect.icon}") }
        }.build())
        add(CoreHudLine().apply {
            val level = "Lv${hud.level.coerceIn(0, 999)}"
            val title = hud.notice ?: hud.name
            val levelWidth = CoreUiComponents.width(level)
            val left = CoreBossBarHud.centred(levelWidth + 4 + CoreUiComponents.width(title))
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
            vanilla(CoreBossBarHud.centred(CoreUiComponents.width(text)), text, MODS)
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
