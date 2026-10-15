package dev.projects.server.coreloop.ui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Pixel combat HUD drawn in the action bar. Vanilla 26.2 GUI pixels, x relative to the screen
 * centre; vertical positions live in the font providers (see scripts/build_combat_hud.py).
 *
 *   H-113..H-98  caption slot (combat cue / notice) on a dark pill
 *   H-96 ..H-61  five 36 px skill cards (32 px art at 1:1); buffs to their left
 *   H-59 ..H-50  vanilla selected-item name (left free)
 *   H-47 ..H-24  status plate: HP | level crest | MP, shield, class resource, charges, XP line
 */
internal object CoreHudLayout {
    const val PLATE_X = -91
    const val HP_X = -87
    const val MP_X = 11
    const val BAR_FILL = 76
    const val RESOURCE_X = -44
    const val RESOURCE_FILL = 88
    const val XP_X = -88
    const val XP_FILL = 176
    const val SKILL_SIZE = 36
    val skillLeft = listOf(-94, -56, -18, 20, 58)
    const val BUFF_RIGHT = -98
    const val BUFF_SIZE = 12
    const val BUFF_PITCH = 14
    const val BUFF_STEPS = 12
    const val CD_STEPS = 24
    const val READY = 0
    const val NO_MANA = CD_STEPS + 1
    const val LOCKED = CD_STEPS + 2
    const val MAX_CAPTION_WIDTH = 300

    private val NUMBER: TextColor = TextColor.color(0xFFF5E0)
    private val CREST: TextColor = TextColor.color(0xF2D58A)
    private val SHIELD: TextColor = TextColor.color(0xCFF5FF)
    private val CUE: TextColor = TextColor.color(0xF2D58A)
    private val NOTICE: TextColor = TextColor.color(0xFFD9A0)

    data class SkillVisual(val frame: Int, val centre: String)

    fun skillVisual(skill: CoreHudSkill, mana: Double): SkillVisual {
        if (!skill.unlocked) return SkillVisual(LOCKED, "")
        val remaining = skill.remainingSeconds.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        if (remaining > 0.0) {
            val total = skill.totalSeconds.takeIf { it.isFinite() && it > 0.0 } ?: remaining
            return SkillVisual(ceil((remaining / total).coerceIn(0.0, 1.0) * CD_STEPS).toInt().coerceIn(1, CD_STEPS),
                ceil(remaining).toInt().coerceIn(1, 99).toString())
        }
        // Resource starvation dims the artwork without obscuring the skill with a label.
        return if (!skill.resourceAvailable) SkillVisual(NO_MANA, "")
        else if (!mana.isFinite() || mana < skill.manaCost.coerceAtLeast(0)) SkillVisual(NO_MANA, "MP")
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

    fun caption(state: CoreHudState): String = state.notice.ifBlank { state.combatCue }

    private fun number(value: Double) = if (value.isFinite()) value.coerceIn(0.0, 99_999.0).roundToInt().toString() else "0"

    fun render(state: CoreHudState): Component {
        val line = CoreHudLine()
        line.put(PLATE_X, "plate")
        gauge(line, "hp", HP_X, state.health, state.maxHealth, state.healthTrail, fillWidth(state.shield, state.maxHealth, BAR_FILL))
        gauge(line, "mp", MP_X, state.mana, state.maxMana, state.mana)
        if (state.shield.isFinite() && state.shield > 0) {
            line.put(HP_X, "shield_icon")
            line.digits("tiny_res", HP_X + 10, number(state.shield), SHIELD)
        }
        state.resource?.let { resource ->
            line.fill("res_fill", RESOURCE_X, fillWidth(resource, state.resourceMaximum, RESOURCE_FILL))
            val text = "${number(resource)}/${number(state.resourceMaximum)}"
            line.digits("tiny_res", RESOURCE_X + (RESOURCE_FILL - CoreHudLine.digitsWidth(text.length)) / 2, text, NUMBER)
        }
        state.charges?.let { charges ->
            for (pip in 0 until 3) line.put(PLATE_X + 138 + pip * 7, if (pip < charges.coerceIn(0, 3)) "pip_on" else "pip_off")
        }
        line.fill("xp_fill", XP_X, if (state.experience.isFinite()) (state.experience.coerceIn(0.0, 1.0) * XP_FILL).toInt() else 0, 128)
        if (state.level > 0) {
            val level = state.level.coerceIn(1, 999).toString()
            line.digits("tiny_crest", -(CoreHudLine.digitsWidth(level.length) + 1) / 2, level, CREST)
        }
        state.skills.take(skillLeft.size).forEachIndexed { index, skill ->
            val art = skill.artIndex?.coerceIn(0, 69) ?: when (skill.icon) {
                CoreUiIcon.DASH -> 0
                CoreUiIcon.SLAM -> 1
                CoreUiIcon.WHIRL -> 2
                else -> index
            }
            val visual = skillVisual(skill, state.mana)
            val x = skillLeft[index]
            line.put(x, "skill_${art}_${visual.frame}")
            val seconds = visual.centre.filter { it in '0'..'9' }
            if (seconds.isNotEmpty()) {
                val width = CoreHudLine.digitsWidth(seconds.length, pitch = 6, glyph = 7)
                line.digits("big_cd", x + (SKILL_SIZE - width) / 2, seconds, NUMBER, pitch = 6)
            }
            val key = skill.key.filter { it in '0'..'9' }.take(2)
            if (key.isNotEmpty()) line.digits("tiny_key", x + 33 - CoreHudLine.digitsWidth(key.length), key, NUMBER)
        }
        state.buffs.take(4).forEachIndexed { index, buff ->
            val x = BUFF_RIGHT - BUFF_SIZE - index * BUFF_PITCH
            line.put(x, "buff_${buff.kind.glyph}_${buffFrame(buff)}")
            val seconds = buff.remainingSeconds.takeIf { it.isFinite() && it > 0.0 }?.let { ceil(it).toInt().coerceIn(1, 999).toString() }
            if (seconds != null) line.digits("tiny_buff", x + (BUFF_SIZE - CoreHudLine.digitsWidth(seconds.length)) / 2, seconds, NUMBER)
        }
        val caption = caption(state)
        if (caption.isNotBlank()) {
            val text = trimCaption(caption)
            val width = CoreHudGlyphs.textWidth(text)
            val left = -width / 2
            line.put(left - 6, "pill_left")
            line.fill("pill", left - 2, width + 4, 128)
            line.put(left + width + 2, "pill_right")
            line.text(left, text, CoreHudGlyphs.TEXT_SLOT, if (state.notice.isNotBlank()) NOTICE else CUE)
        }
        return line.build()
    }

    private fun gauge(line: CoreHudLine, bar: String, x: Int, value: Double, maximum: Double, trail: Double, shield: Int = 0) {
        val filled = fillWidth(value, maximum, BAR_FILL)
        val trailing = fillWidth(maxOf(value, trail.takeIf { it.isFinite() } ?: value), maximum, BAR_FILL)
        line.fill("${bar}_fill", x, filled)
        if (trailing > filled) line.fill("hp_trail", x + filled, trailing - filled)
        if (filled in 1 until BAR_FILL) line.put(x + filled - 1, "${bar}_edge")
        // The barrier is a bright layer over the top rows of the health it protects.
        line.fill("hp_shield", x, shield)
        val text = "${number(value)}/${number(maximum)}"
        line.digits("tiny_bar", x + (BAR_FILL - CoreHudLine.digitsWidth(text.length)) / 2, text, NUMBER)
    }

    private fun trimCaption(value: String): String {
        if (CoreHudGlyphs.textWidth(value) <= MAX_CAPTION_WIDTH) return value
        val codepoints = value.codePoints().toArray()
        var end = codepoints.size
        while (end > 0 && CoreHudGlyphs.textWidth(String(codepoints, 0, end) + "…") > MAX_CAPTION_WIDTH) end--
        return String(codepoints, 0, end) + "…"
    }
}
