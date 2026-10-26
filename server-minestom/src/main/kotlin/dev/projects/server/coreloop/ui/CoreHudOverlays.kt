package dev.projects.server.coreloop.ui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import dev.projects.server.coreloop.CoreDamageType
import dev.projects.server.mob.MobGrade
import dev.projects.server.mob.MobHud
import kotlin.math.roundToInt

/**
 * Floating damage numbers (2026-10-20, learned from Spellforged: big digits, a minus, the damage type as an
 * icon after the number): `[★] -1234 ⚔ [element / weak spot]`. The number takes the damage type's colour
 * (physical gold, magical blue-violet, true white); a critical adds a star in front; a hit on the mob's
 * weakness adds that element's mark, a weak spot (boss part) adds a target mark. Centred on the hit.
 */
internal object CoreDamageNumbers {
    private val PHYSICAL: TextColor = TextColor.color(0xFFD23A)
    private val MAGICAL: TextColor = TextColor.color(0x8A96FF)
    private val TRUE: TextColor = TextColor.color(0xF4F0E8)
    private val STAR: TextColor = TextColor.color(0xFFF2A0)
    private val SPOT: TextColor = TextColor.color(0xFF6A6A)
    private val ELEMENTS = mapOf("fire" to TextColor.color(0xFF8C3C), "ice" to TextColor.color(0x8FE8FF),
        "lightning" to TextColor.color(0xFFE65A))
    private const val PITCH = 6

    fun color(type: CoreDamageType): TextColor = when (type) {
        CoreDamageType.PHYSICAL -> PHYSICAL
        CoreDamageType.MAGICAL -> MAGICAL
        CoreDamageType.TRUE -> TRUE
    }

    /** [element]: the weakness this hit struck ("fire" / "ice" / "lightning"), or null; [spot]: hit a weak part. */
    fun render(amount: Double, critical: Boolean, type: CoreDamageType, element: String? = null, spot: Boolean = false): Component {
        val digits = (if (amount.isFinite()) amount.coerceIn(0.0, 9_999_999.0).roundToInt() else 0).toString()
        val color = color(type)
        val icon = "dmg_${type.name.lowercase()}"
        val mark = when {
            element != null && element in ELEMENTS -> "dmg_el_$element" to ELEMENTS.getValue(element)
            spot -> "dmg_spot" to SPOT
            else -> null
        }
        fun adv(name: String) = CoreHudGlyphs[name].advance
        val width = (if (critical) adv("dmg_crit") else 0) + adv("dmg_minus") - 1 +
            CoreHudLine.digitsWidth(digits.length, PITCH, 7) + 2 + adv(icon) + (mark?.let { 1 + adv(it.first) } ?: 0)
        val line = CoreHudLine()
        var x = -(width - 1) / 2
        if (critical) x = line.put(x, "dmg_crit", STAR) + 1
        x = line.put(x, "dmg_minus", color)  // outlines touch: the minus sits right against the digits
        line.digits("dmg", x, digits, color, PITCH)
        x += CoreHudLine.digitsWidth(digits.length, PITCH, 7) + 2
        x = line.put(x, icon, color) + 2
        if (mark != null) line.put(x, mark.first, mark.second)
        return line.build()
    }
}

/**
 * Boss bar drawn by the name component of a PINK boss bar (whose vanilla sprites the pack blanks).
 * Graded bosses: the level diamond (larger for higher grades) over a charcoal plate with the name row, a
 * thick HP gauge with the number inside and the break gauge as cells; the grade is a labelled tag on the
 * plate's corner and the plate's frame (purple ring, gold ring and brackets, black iron with thorns). A dedicated boss
 * keeps that bar and adds its own ornament (shade wyrm: wing fan and pink horns; sakura beast: mane, horns, tails).
 * Every row sits at its own font's height (scripts/boss_bar_art.py), so text is not tied to the title line.
 */
internal object CoreBossBarHud {
    const val PLATE_X = -118
    /** The plate glyph carries a margin for its corner pieces and grade tag (boss_bar_art.PLATE_PAD). */
    const val PLATE_PAD = 9
    const val TAG_X = PLATE_X + 12
    const val PLATE_W = 237
    const val EMBLEM_X = -40
    /** Ornament glyphs (wings, mane, tails) come in two 180 px halves: x -180..-1 and 0..179. */
    const val ORNAMENT_X = -180
    const val HP_X = -112
    const val HP_W = 225
    const val CELL_X = -109
    const val CELL_PITCH = 11
    const val CELLS = 20
    const val ICON_PITCH = 10
    const val MAX_ICONS = 6
    private val WHITE: TextColor = TextColor.color(0xFFFFFF)
    private val LEVEL: TextColor = TextColor.color(0xFFD25A)
    private val NOTICE: TextColor = TextColor.color(0xFF8A70)
    private val WYRM_NAME: TextColor = TextColor.color(0xE0D4FF)
    private val SAKURA_NAME: TextColor = TextColor.color(0xFFD8E4)
    private val EROSION: TextColor = TextColor.color(0xD0A8FF)

    fun percent(health: Double, maximum: Double): String =
        if (!health.isFinite() || !maximum.isFinite() || maximum <= 0.0) "0%" else "${(health / maximum * 100).coerceIn(0.0, 100.0).toInt()}%"

    private fun grouped(value: Double): String = "%,d".format(java.util.Locale.ROOT, kotlin.math.ceil(value.coerceAtLeast(0.0)).toLong())

    /**
     * Left x that centres text of advance [width] on the art's centre column 0: the ink is width - 1 px
     * (the last advance pixel is spacing), and symmetric art spans -r..r round column 0.
     */
    fun centred(width: Int): Int = -(width - 1) / 2

    /** Decorated bosses by [MobHud.dedicated]: the key's glyph family (back, front, fill, trail). */
    private val DEDICATED = mapOf("shade_wyrm" to "wyrm", "sakura_fang" to "sakura")
    const val EROSION_X = -45
    const val EROSION_CELLS = 10
    const val EROSION_PITCH = 9

    /** [erosion]: this viewer's 黒翳竜 erosion 0..1 (drawn under the name while above zero). */
    fun render(hud: MobHud, trail: Double, erosion: Double = 0.0): Component = graded(hud, trail, DEDICATED[hud.dedicated], erosion)

    /** [fill]: the fill glyph family (boss_fill, boss_fillc, wyrm_fill); the trail is that family's *_trail. */
    private fun CoreHudLine.gauge(fill: String, x: Int, width: Int, hud: MobHud, trail: Double) {
        val filled = CoreHudLayout.fillWidth(hud.health, hud.maximumHealth, width)
        val trailing = CoreHudLayout.fillWidth(maxOf(hud.health, trail.takeIf { it.isFinite() } ?: hud.health), hud.maximumHealth, width)
        fill(fill, x, filled, 128)
        if (trailing > filled) fill(fill.substringBefore('_') + "_trail", x + filled, trailing - filled, 128)
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

    /** The notice (attack being prepared, 帰還中) replaces the name; a break shows on the gauge and as an icon. */
    private fun title(hud: MobHud): Pair<String, TextColor>? = hud.notice?.let { it to NOTICE }

    /**
     * [ornament]: a dedicated boss's glyph family ("wyrm", "sakura"): the same bar, decorated — its ornament
     * behind the plate (wings, mane, tails), horns over the diamond, its own HP colour, the plate's corner
     * pieces left off so they do not fight the ornament.
     */
    private fun graded(hud: MobHud, trail: Double, ornament: String?, erosion: Double): Component {
        val line = CoreHudLine()
        val grade = if (hud.grade == MobGrade.COMMON) MobGrade.ELITE else hud.grade
        val key = grade.name.lowercase()
        if (ornament != null) {
            line.put(ORNAMENT_X, "${ornament}_back_l")
            line.put(0, "${ornament}_back_r")
        }
        line.put(PLATE_X - PLATE_PAD, if (ornament != null && grade == MobGrade.CALAMITY) "boss_plate_calamity_bare" else "boss_plate_$key")
        line.put(EMBLEM_X, "boss_emblem_$key")
        if (ornament != null) line.put(EMBLEM_X, "${ornament}_front")
        val fill = when {
            ornament != null -> "${ornament}_fill"
            grade == MobGrade.CALAMITY -> "boss_fillc"
            else -> "boss_fill"
        }
        line.gauge(fill, HP_X, HP_W, hud, trail)
        for (q in 1..3) line.put(HP_X + HP_W * q / 4, "boss_tick")
        line.cells("boss", hud)
        hud.effects.take(MAX_ICONS).forEachIndexed { i, effect -> line.put(PLATE_X + 2 + i * ICON_PITCH, "boss_icon_${effect.icon}") }
        line.uni(TAG_X + 4, grade.label, CoreHudGlyphs.BOSS_TAG, CoreMobNameplate.color(grade))
        val lv = hud.level.coerceIn(0, 999).toString()
        line.uni(centred(CoreHudGlyphs.uniWidth(lv)), lv, CoreHudGlyphs.BOSS_LV, LEVEL)
        val nameColor = when (ornament) { "wyrm" -> WYRM_NAME; "sakura" -> SAKURA_NAME; else -> WHITE }
        val (title, color) = title(hud) ?: (hud.name to nameColor)
        line.uni(PLATE_X + 6, title, CoreHudGlyphs.BOSS_NAME, color)
        val pct = percent(hud.health, hud.maximumHealth)
        line.uni(PLATE_X + PLATE_W - 6 - CoreHudGlyphs.uniWidth(pct) + 1, pct, CoreHudGlyphs.BOSS_NAME, WHITE)
        val number = "${grouped(hud.health)} / ${grouped(hud.maximumHealth)}"
        line.uni(centred(CoreHudGlyphs.uniWidth(number)), number, CoreHudGlyphs.BOSS_HP, WHITE)
        if (erosion > 0.0 && erosion.isFinite()) {
            line.put(EROSION_X - 2, "erosion_well")
            val lit = kotlin.math.ceil(erosion.coerceIn(0.0, 1.0) * EROSION_CELLS).toInt()
            for (i in 0 until lit) line.put(EROSION_X + i * EROSION_PITCH, "erosion_cell")
            line.uni(EROSION_X - 4 - CoreHudGlyphs.uniWidth("侵食"), "侵食", CoreHudGlyphs.DED_EROSION, EROSION)
        }
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
