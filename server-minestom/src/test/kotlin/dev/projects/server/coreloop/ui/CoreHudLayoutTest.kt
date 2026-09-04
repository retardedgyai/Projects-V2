package dev.projects.server.coreloop.ui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreHudLayoutTest {
    private fun skills(remaining: Double = 0.0) = listOf(
        CoreHudSkill(CoreUiIcon.DASH, "2", remaining, 4.0, 15),
        CoreHudSkill(CoreUiIcon.SLAM, "3", remaining, 7.0, 25),
        CoreHudSkill(CoreUiIcon.WHIRL, "4", remaining, 11.0, 35))
    private fun advance(component: Component): Int {
        val own = (component as? TextComponent)?.content().orEmpty().sumOf { c ->
            when {
                component.style().font() == CoreUiComponents.SPACE_FONT -> when (c.code) {
                    in 0xE100..0xE10B -> 1 shl (c.code - 0xE100)
                    in 0xE180..0xE18B -> -(1 shl (c.code - 0xE180))
                    else -> error("Unexpected space glyph")
                }
                component.style().font() == CoreUiComponents.HUD_FONT -> when (c.code) {
                    in 0xE300..0xE334 -> 82
                    in 0xE400..0xE457 -> 33
                    in 0xE500..0xE50D -> 9
                    in 0xE520..0xE54D -> 4
                    else -> error("Unexpected HUD glyph ${c.code}")
                }
                else -> error("Packed HUD must not depend on a global text font")
            }
        }
        return own + component.children().sumOf(::advance)
    }

    @Test fun `ready cooldown and no mana are separate states with cooldown taking priority`() {
        val skill = skills().first()
        assertEquals(CoreHudLayout.SkillVisual(0, ""), CoreHudLayout.skillVisual(skill, 15.0))
        assertEquals(CoreHudLayout.SkillVisual(21, "MP"), CoreHudLayout.skillVisual(skill, 14.0))
        assertEquals(CoreHudLayout.SkillVisual(20, "4"), CoreHudLayout.skillVisual(skill.copy(remainingSeconds = 4.0), 0.0))
        assertEquals(CoreHudLayout.SkillVisual(1, "1"), CoreHudLayout.skillVisual(skill.copy(remainingSeconds = .01), 100.0))
    }

    @Test fun `countdown and radial progress are bounded for invalid external state`() {
        val skill = skills().first()
        assertEquals(20, CoreHudLayout.skillVisual(skill.copy(remainingSeconds = 4.0, totalSeconds = 0.0), 100.0).frame)
        assertEquals("99", CoreHudLayout.skillVisual(skill.copy(remainingSeconds = 120.0), 100.0).centre)
        assertEquals(0, CoreHudLayout.skillVisual(skill.copy(remainingSeconds = Double.NaN), 100.0).frame)
        assertEquals(21, CoreHudLayout.skillVisual(skill, Double.NaN).frame)
        assertEquals(0, CoreHudLayout.barFrame(20.0, 0.0))
        assertEquals(0, CoreHudLayout.barFrame(Double.NaN, 100.0))
        assertEquals(20, CoreHudLayout.barFrame(200.0, 100.0))
    }

    @Test fun `all layers return to exact screen centre regardless of health digit count or skill state`() {
        for (health in listOf(0.0, 9.0, 100.0, 12345.0)) {
            for (mana in listOf(0.0, 100.0)) for (remaining in listOf(0.0, .1, 4.0, 11.0)) {
                val state = CoreHudState(health, 20000.0, mana, skills = skills(remaining))
                assertEquals(0, advance(CoreUiComponents.hud(state, true)))
            }
        }
        assertEquals(0, advance(CoreUiComponents.hud(CoreHudState(100.0, 100.0, 100.0), true)))
    }

    @Test fun `heart food and skill geometry remains tied to vanilla GUI coordinates`() {
        for (height in listOf(240, 360, 480, 720)) {
            assertEquals(height - 39, height - 65 - CoreHudLayout.BAR_ASCENT)
            val skillTop = height - 65 - CoreHudLayout.SKILL_ASCENT
            assertEquals(height - 94, skillTop)
            assertTrue(skillTop + CoreHudLayout.SKILL_SIZE < height - 59, "Leave selected item names unobstructed")
        }
        assertEquals(-91, CoreHudLayout.HEALTH_X)
        assertEquals(91, CoreHudLayout.MANA_X + CoreHudLayout.BAR_WIDTH)
    }

    @Test fun `override allowlist cannot hide other HUD information or alter any default font`() {
        assertEquals(50, CoreUiPackPolicy.vanillaOverrides.size)
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/font/default.json"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/armor_full.png"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/heart/vehicle_full.png"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/air.png"))
        assertFalse(CoreUiPackPolicy.allowedPath("../invalid"))
        assertTrue(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/heart/full.png"))
    }
}
