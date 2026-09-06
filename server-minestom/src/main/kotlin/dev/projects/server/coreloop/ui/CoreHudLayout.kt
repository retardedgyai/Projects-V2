package dev.projects.server.coreloop.ui

import net.kyori.adventure.text.Component
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Vanilla 26.2 GUI pixels, relative to screen centre and the action-bar baseline (height - 72). */
internal object CoreHudLayout {
    const val HEALTH_X = -91
    const val MANA_X = 10
    const val BAR_WIDTH = 81
    const val BAR_HEIGHT = 9
    const val BAR_ASCENT = -26 // height - 65 - ascent = height - 39, exactly the heart/food row.
    const val SKILL_SIZE = 32
    const val SKILL_ASCENT = 29 // top = height - 94; bottom leaves room for item-name and armour rows.
    val skillLeft = listOf(-52, -16, 20)
    const val READY = 0
    const val NO_MANA = 21
    const val LOCKED = 22
    private const val DIGITS = "0123456789/HMP"

    data class SkillVisual(val frame: Int, val centre: String)

    fun skillVisual(skill: CoreHudSkill, mana: Double): SkillVisual {
        if (!skill.unlocked) return SkillVisual(LOCKED, "")
        val remaining = skill.remainingSeconds.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        if (remaining > 0.0) {
            val total = skill.totalSeconds.takeIf { it.isFinite() && it > 0.0 } ?: remaining
            return SkillVisual(ceil((remaining / total).coerceIn(0.0, 1.0) * 20).toInt().coerceIn(1, 20),
                ceil(remaining).toInt().coerceIn(1, 99).toString())
        }
        return if (!mana.isFinite() || mana < skill.manaCost.coerceAtLeast(0)) SkillVisual(NO_MANA, "MP")
        else SkillVisual(READY, "")
    }

    fun barFrame(value: Double, maximum: Double): Int =
        if (!value.isFinite() || !maximum.isFinite() || maximum <= 0.0) 0
        else ((value / maximum).coerceIn(0.0, 1.0) * 20).roundToInt()

    fun render(state: CoreHudState): Component {
        // Every layer returns to x=0. Net advance is zero, so the client centres on its exact centre
        // regardless of digit count. Its optional text backdrop stays under the middle skill only.
        var result = Component.empty()
        fun layer(x: Int, component: Component, advance: Int) {
            result = result.append(CoreUiComponents.space(x)).append(component)
                .append(CoreUiComponents.space(-x - advance))
        }
        fun glyph(code: Int) = CoreUiComponents.glyph(code.toChar(), CoreUiComponents.HUD_FONT)
        fun number(value: Double) = if (value.isFinite()) value.coerceIn(0.0, 99_999.0).roundToInt().toString() else "0"
        fun digits(value: String, base: Int): Component = value.fold(Component.empty() as Component) { component, c ->
            val index = DIGITS.indexOf(c)
            if (index < 0) component else component.append(glyph(base + index))
        }
        fun bar(x: Int, value: Double, maximum: Double, base: Int, label: String) {
            layer(x, glyph(base + barFrame(value, maximum)), BAR_WIDTH + 1)
            layer(x + 3, digits(label, 0xE540), label.length * 4)
            val display = "${number(value)}/${number(maximum)}"
            val textWidth = display.length * 4
            layer(x + 19 + (BAR_WIDTH - 22 - textWidth) / 2, digits(display, 0xE540), textWidth)
        }
        bar(HEALTH_X, state.health, state.maxHealth, 0xE300, "HP")
        bar(MANA_X, state.mana, state.maxMana, 0xE320, "MP")
        state.charges?.let { charge -> layer(-6, digits("${charge.coerceIn(0,3)}/3", 0xE540), 12) }
        state.skills.take(3).forEachIndexed { index, skill ->
            val kind = skill.artIndex?.coerceIn(0, 11) ?: when (skill.icon) {
                CoreUiIcon.DASH -> 0
                CoreUiIcon.SLAM -> 1
                CoreUiIcon.WHIRL -> 2
                else -> index
            }
            val visual = skillVisual(skill, state.mana)
            val x = skillLeft[index]
            layer(x, glyph((if (kind < 3) 0xE400 + kind * 32 else 0xE600 + (kind - 3) * 32) + visual.frame), SKILL_SIZE + 1)
            if (visual.centre.isNotEmpty()) {
                val advance = visual.centre.length * 9
                layer(x + (SKILL_SIZE - advance + 1) / 2, digits(visual.centre, 0xE500), advance)
            }
            val key = skill.key.filter { it in '0'..'9' }.take(2)
            if (key.isNotEmpty()) layer(x + 29 - key.length * 4, digits(key, 0xE520), key.length * 4)
        }
        return result
    }
}
