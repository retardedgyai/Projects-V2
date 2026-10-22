package dev.projects.server.coreloop.ui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Combat HUD drawn in the action bar (direction approved 2026-10-16, "modern MMO x Minecraft").
 * Vanilla 26.2 GUI pixels, x relative to the screen centre; vertical positions live in the font
 * providers (scripts/build_combat_hud.py, art in scripts/combat_hud_art.py, numbers mirrored in
 * assets/core-ui/hud-layout.json). Bottom up:
 *
 *   H-22..H-0    vanilla hotbar, reskinned by the pack; skill art over slots 2-6 (keys 2-6)
 *   H-27..H-22   class-resource cells (20)
 *   H-40..H-28   HP plate | level diamond | MP plate (3x5 values inside the gauges)
 *   H-55..H-43   buff tiles
 *   action-bar text row: notice / combat cue in the vanilla font
 */
internal object CoreHudLayout {
    const val CONSOLE_X = -92
    const val HP_X = -78
    const val HP_W = 54
    const val MP_X = 25
    const val MP_W = 54
    const val HP_TICKS = 6
    const val CELL_X = -89
    const val CELL_PITCH = 9
    const val CELL_COUNT = 20
    const val EMBLEM_X = -29
    const val XP_STEPS = 24
    const val SLOT_ICON_X = -88
    const val SLOT_PITCH = 20
    const val BUFF_LEFT = -92
    const val BUFF_STEPS = 12
    const val MAX_CAPTION_WIDTH = 300
    val classes = listOf("warrior", "ranger", "mage", "assassin", "templar", "healer")

    const val READY = "ready"
    const val COOLDOWN = "cooldown"
    const val NO_MANA = "mana"
    const val LOCKED = "locked"

    private val NUMBER: TextColor = TextColor.color(0xFFFFFF)
    private val LEVEL: TextColor = TextColor.color(0xFFD77A)
    private val CUE: TextColor = TextColor.color(0xF2D58A)
    private val NOTICE: TextColor = TextColor.color(0xA8D0FF)

    data class SkillVisual(val state: String, val centre: String)

    fun skillVisual(skill: CoreHudSkill, mana: Double): SkillVisual {
        if (!skill.unlocked) return SkillVisual(LOCKED, "")
        val remaining = skill.remainingSeconds.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        if (remaining > 0.0) return SkillVisual(COOLDOWN, ceil(remaining).toInt().coerceIn(1, 99).toString())
        // Starved of mana or of the class resource: the art turns blue, nothing covers it.
        return if (!skill.resourceAvailable || !mana.isFinite() || mana < skill.manaCost.coerceAtLeast(0)) SkillVisual(NO_MANA, "")
        else SkillVisual(READY, "")
    }

    /** Whole pixels of a [width] px gauge; any positive value keeps at least one pixel lit. */
    fun fillWidth(value: Double, maximum: Double, width: Int): Int {
        if (!value.isFinite() || !maximum.isFinite() || maximum <= 0.0 || value <= 0.0) return 0
        return ((value / maximum).coerceIn(0.0, 1.0) * width).roundToInt().coerceIn(1, width)
    }

    fun buffFrame(buff: CoreHudBuff): Int {
        if (!buff.cooldown) return 0
        val total = buff.totalSeconds.takeIf { it.isFinite() && it > 0.0 } ?: return BUFF_STEPS
        val remaining = buff.remainingSeconds.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        return ceil((remaining / total).coerceIn(0.0, 1.0) * BUFF_STEPS).toInt().coerceIn(1, BUFF_STEPS)
    }

    /** Lit class-resource cells out of [CELL_COUNT]; charge-based classes light thirds. */
    fun litCells(state: CoreHudState): Int {
        state.resource?.let { return fillCells(it, state.resourceMaximum) }
        state.charges?.let { return fillCells(it.toDouble(), 3.0) }
        return 0
    }

    private fun fillCells(value: Double, maximum: Double): Int =
        if (!value.isFinite() || !maximum.isFinite() || maximum <= 0.0 || value <= 0.0) 0
        else floor((value / maximum).coerceIn(0.0, 1.0) * CELL_COUNT).toInt().coerceIn(1, CELL_COUNT)

    fun xpStep(experience: Double): Int =
        if (!experience.isFinite() || experience <= 0.0) 0 else floor(experience.coerceIn(0.0, 1.0) * XP_STEPS).toInt().coerceIn(1, XP_STEPS)

    /** Decoration set for the level diamond; unknown classes fall back to the warrior's. */
    fun job(state: CoreHudState): String = when (val key = state.classKey.lowercase()) {
        in classes -> key
        "starweaver" -> "mage"
        else -> "warrior"
    }

    fun caption(state: CoreHudState): String = state.notice.ifBlank { state.combatCue }

    private fun number(value: Double) = if (value.isFinite()) value.coerceIn(0.0, 99_999.0).roundToInt().toString() else "0"

    fun render(state: CoreHudState): Component {
        val line = CoreHudLine()
        line.put(CONSOLE_X, "console")
        // HP: health, then the absorption barrier in vanilla gold, then the pale trail of recent damage.
        val filled = fillWidth(state.health, state.maxHealth, HP_W)
        val barrier = fillWidth(state.shield, state.maxHealth, HP_W).coerceAtMost(HP_W - filled)
        val trailing = fillWidth(maxOf(state.health, state.healthTrail.takeIf { it.isFinite() } ?: state.health), state.maxHealth, HP_W)
        line.fill("hp_fill", HP_X, filled)
        line.fill("hp_barrier", HP_X + filled, barrier)
        val trailEnd = (trailing + barrier).coerceAtMost(HP_W)
        if (trailEnd > filled + barrier) line.fill("hp_trail", HP_X + filled + barrier, trailEnd - filled - barrier)
        for (k in 1 until HP_TICKS) line.put(HP_X + (HP_W * k + HP_TICKS / 2) / HP_TICKS, "hp_tick")
        gaugeValue(line, HP_X, HP_W, "${number(state.health)}/${number(state.maxHealth)}")
        line.fill("mp_fill", MP_X, fillWidth(state.mana, state.maxMana, MP_W))
        gaugeValue(line, MP_X, MP_W, "${number(state.mana)}/${number(state.maxMana)}")
        for (i in 0 until litCells(state)) line.put(CELL_X + i * CELL_PITCH, "rage_cell")
        // Level diamond: class wings and crown, XP filling the class-colour band clockwise.
        val job = job(state)
        line.put(EMBLEM_X, "emblem_$job")
        val step = xpStep(state.experience)
        if (step > 0) line.put(EMBLEM_X, "xp_${job}_$step")
        if (state.level > 0) line.numberCentred(0.0, state.level.coerceIn(1, 999).toString(), CoreHudGlyphs.NUM_GAUGE, LEVEL)
        // Skills sit over hotbar slots 2-6 (the keys that cast them).
        state.skills.take(5).forEachIndexed { index, skill ->
            val art = skill.artIndex?.coerceIn(0, 69) ?: when (skill.icon) {
                CoreUiIcon.DASH -> 0
                CoreUiIcon.SLAM -> 1
                CoreUiIcon.WHIRL -> 2
                else -> index
            }
            val visual = skillVisual(skill, state.mana)
            val x = SLOT_ICON_X + SLOT_PITCH * (index + 1)
            line.put(x, "skill_${art}_${visual.state}")
            if (visual.centre.isNotEmpty()) line.numberCentred(x + 7.5, visual.centre, CoreHudGlyphs.NUM_CD, NUMBER)
        }
        // Buff tiles left to right above the HP plate: icon, then the timer.
        var bx = BUFF_LEFT
        state.buffs.take(4).forEach { buff ->
            val seconds = buff.remainingSeconds.takeIf { it.isFinite() && it > 0.0 }?.let { "${ceil(it).toInt().coerceIn(1, 999)}s" } ?: ""
            val ink = gaugeDigitsWidth(seconds)
            val tile = ink + 18
            line.put(bx, "buff_left")
            line.fill("buff_mid", bx + 14, tile - 17, 16)
            line.put(bx + tile - 3, "buff_right")
            line.put(bx + 2, "buff_${buff.kind.glyph}_${buffFrame(buff)}")
            if (seconds.isNotEmpty()) line.digits("bd", kotlin.math.round(bx + 13 + (tile - 15) / 2.0 - (ink - 1) / 2.0).toInt(), seconds, NUMBER, DIGIT_PITCH)
            bx += tile + 2
        }
        // Notice (or the combat cue) on the action bar's own text row, in the vanilla font.
        val caption = caption(state)
        if (caption.isNotBlank()) {
            val text = trimCaption(caption)
            line.vanilla(-CoreUiComponents.width(text) / 2, text, if (state.notice.isNotBlank()) NOTICE else CUE)
        }
        return line.build()
    }

    const val DIGIT_PITCH = 4

    /** Ink width of [value] in the 3x5 gauge digits (shadow excluded). */
    fun gaugeDigitsWidth(value: String): Int = if (value.isEmpty()) 0 else (value.length - 1) * DIGIT_PITCH + 3

    /** Small 3x5 digits centred in a gauge (the vanilla font filled the whole 54 px bar in-game). */
    private fun gaugeValue(line: CoreHudLine, x: Int, width: Int, value: String) {
        val left = kotlin.math.round(x + (width - 1) / 2.0 - (gaugeDigitsWidth(value) - 1) / 2.0).toInt()
        line.digits("gd", left, value, NUMBER, DIGIT_PITCH)
    }

    private fun trimCaption(value: String): String {
        if (CoreUiComponents.width(value) <= MAX_CAPTION_WIDTH) return value
        val codepoints = value.codePoints().toArray()
        var end = codepoints.size
        while (end > 0 && CoreUiComponents.width(String(codepoints, 0, end) + "…") > MAX_CAPTION_WIDTH) end--
        return String(codepoints, 0, end) + "…"
    }
}
