package dev.projects.server.coreloop.ui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreHudLayoutTest {
    private data class Placed(val font: String, val code: Int, val x: Int, val advance: Int, val color: Int)

    /** Walk the component exactly as the client lays it out: every glyph's x and the net advance. */
    private fun layout(component: Component): Pair<List<Placed>, Int> {
        val glyphs = mutableListOf<Placed>()
        var x = 0
        fun visit(c: Component, inherited: net.kyori.adventure.key.Key?, color: Int) {
            val font = c.font() ?: inherited
            val tint = c.color()?.value() ?: color
            for (char in (c as? TextComponent)?.content().orEmpty()) {
                val key = requireNotNull(font) { "Packed HUD must not depend on a global text font" }
                val width = if (key == CoreUiComponents.SPACE_FONT) when (char.code) {
                    in 0xE100..0xE10B -> 1 shl (char.code - 0xE100)
                    in 0xE180..0xE18B -> -(1 shl (char.code - 0xE180))
                    else -> error("Unexpected space glyph")
                } else requireNotNull(CoreHudGlyphs.advanceOf(key, char)) { "Unknown glyph ${key.asString()} U+${char.code.toString(16)}" }
                if (key != CoreUiComponents.SPACE_FONT) glyphs += Placed(key.asString(), char.code, x, width, tint)
                x += width
            }
            c.children().forEach { visit(it, font, tint) }
        }
        visit(component, null, 0xFFFFFF)
        return glyphs to x
    }

    private fun skills(remaining: Double = 0.0) = listOf(
        CoreHudSkill(CoreUiIcon.DASH, "2", remaining, 4.0, 15),
        CoreHudSkill(CoreUiIcon.SLAM, "3", remaining, 7.0, 25),
        CoreHudSkill(CoreUiIcon.WHIRL, "4", remaining, 11.0, 35))

    @Test fun `export HUD surfaces from the actual components for the pixel preview`() {
        val root = generateSequence(java.nio.file.Path.of("").toAbsolutePath()) { it.parent }
            .first { java.nio.file.Files.isRegularFile(it.resolve("settings.gradle.kts")) }
        val output = java.nio.file.Files.createDirectories(root.resolve("build/hud-preview/snapshots"))
        val loadout = listOf(0, 1, 2, 3, 8).mapIndexed { index, art -> CoreHudSkill(CoreUiIcon.DASH, (index + 2).toString(), 0.0, 12.0, 15, artIndex = art) }
        val surfaces = listOf(
            Triple("actionbar", "ready", CoreUiComponents.hud(CoreHudState(240.0, 240.0, 100.0, skills = loadout, resource = 60.0,
                level = 12, experience = .42, buffs = listOf(CoreHudBuff(CoreHudBuffKind.WHETSTONE, 175.0, 180.0, false))), true)),
            Triple("actionbar", "combat", CoreUiComponents.hud(CoreHudState(132.0, 240.0, 46.0, skills = loadout.mapIndexed { i, s ->
                s.copy(remainingSeconds = listOf(0.0, 7.2, 0.0, 2.5, 11.0)[i]) }, resource = 35.0, shield = 48.0, healthTrail = 171.0,
                level = 12, experience = .42, buffs = listOf(CoreHudBuff(CoreHudBuffKind.WHETSTONE, 96.0, 180.0, false),
                    CoreHudBuff(CoreHudBuffKind.POTION, 6.3, 10.0, true), CoreHudBuff(CoreHudBuffKind.DODGE, .8, 1.2, true))), true)),
            Triple("actionbar", "counter", CoreUiComponents.hud(CoreHudState(180.0, 240.0, 80.0, skills = loadout.mapIndexed { i, s ->
                s.copy(remainingSeconds = if (i == 3) 3.0 else 0.0) }, resource = 100.0, level = 7, experience = .9, combatCue = "反撃の好機 3秒"), true)),
            Triple("actionbar", "unavailable", CoreUiComponents.hud(CoreHudState(18.0, 240.0, 4.0, skills = loadout.mapIndexed { i, s ->
                s.copy(remainingSeconds = if (i == 0) 8.0 else 0.0, resourceAvailable = i != 2, unlocked = i != 4) }, resource = 0.0,
                level = 3, experience = .05, notice = "マナが足りません（必要 15）"), true)),
            Triple("actionbar", "starweaver", CoreUiComponents.hud(CoreHudState(300.0, 300.0, 120.0, 140.0,
                skills = listOf(30, 31, 32, 33, 38).mapIndexed { i, art -> CoreHudSkill(CoreUiIcon.DASH, (i + 2).toString(), 0.0, 9.0, 10, artIndex = art) },
                resource = 2.0, resourceMaximum = 3.0, charges = 2, level = 40, experience = 1.0), true)),
            Triple("boss", "boss", CoreBossBarHud.render("裂け目の執行官", 12480.0, 20000.0, 14900.0)),
            Triple("nametag", "normal", CoreMobNameplate.render(14, "苔むした兵士", 120.0, 200.0, CoreMobNameplate.Rank.NORMAL, "fire", null)),
            Triple("nametag", "elite", CoreMobNameplate.render(15, "裂け目の術師", 410.0, 420.0, CoreMobNameplate.Rank.ELITE, "ice", "氷結の槍")),
            Triple("damage", "normal", CoreDamageNumbers.render(482.0, false, false)),
            Triple("damage", "critical", CoreDamageNumbers.render(1260.0, true, false)),
            Triple("damage", "weak", CoreDamageNumbers.render(733.0, false, true)),
        )
        for ((surface, name, component) in surfaces) {
            val (glyphs, net) = layout(component)
            assertEquals(0, net, "$surface/$name must return to its anchor")
            assertTrue(glyphs.isNotEmpty())
            java.nio.file.Files.writeString(output.resolve("$surface-$name.json"), com.google.gson.Gson().toJson(mapOf(
                "surface" to surface, "state" to name, "netAdvance" to net,
                "glyphs" to glyphs.map { mapOf("font" to it.font, "code" to it.code, "x" to it.x, "advance" to it.advance, "color" to it.color) })))
        }
    }

    @Test fun `ready cooldown and no mana are separate states with cooldown taking priority`() {
        val skill = skills().first()
        assertEquals(CoreHudLayout.SkillVisual(CoreHudLayout.READY, ""), CoreHudLayout.skillVisual(skill, 15.0))
        assertEquals(CoreHudLayout.SkillVisual(CoreHudLayout.NO_MANA, "MP"), CoreHudLayout.skillVisual(skill, 14.0))
        assertEquals(CoreHudLayout.SkillVisual(CoreHudLayout.CD_STEPS, "4"), CoreHudLayout.skillVisual(skill.copy(remainingSeconds = 4.0), 0.0))
        assertEquals(CoreHudLayout.SkillVisual(1, "1"), CoreHudLayout.skillVisual(skill.copy(remainingSeconds = .01), 100.0))
    }

    @Test fun `gauges are whole pixels and bounded for invalid external state`() {
        val skill = skills().first()
        assertEquals(CoreHudLayout.CD_STEPS, CoreHudLayout.skillVisual(skill.copy(remainingSeconds = 4.0, totalSeconds = 0.0), 100.0).frame)
        assertEquals("99", CoreHudLayout.skillVisual(skill.copy(remainingSeconds = 120.0), 100.0).centre)
        assertEquals(0, CoreHudLayout.skillVisual(skill.copy(remainingSeconds = Double.NaN), 100.0).frame)
        assertEquals(CoreHudLayout.NO_MANA, CoreHudLayout.skillVisual(skill, Double.NaN).frame)
        assertEquals(0, CoreHudLayout.fillWidth(20.0, 0.0, 76))
        assertEquals(0, CoreHudLayout.fillWidth(Double.NaN, 100.0, 76))
        assertEquals(76, CoreHudLayout.fillWidth(200.0, 100.0, 76))
        assertEquals(1, CoreHudLayout.fillWidth(.01, 100.0, 76), "Any health left stays visible")
        assertEquals(38, CoreHudLayout.fillWidth(50.0, 100.0, 76))
        assertEquals(1, CoreHudLayout.buffFrame(CoreHudBuff(CoreHudBuffKind.POTION, .1, 10.0, true)))
        assertEquals(CoreHudLayout.BUFF_STEPS, CoreHudLayout.buffFrame(CoreHudBuff(CoreHudBuffKind.POTION, 10.0, 10.0, true)))
        assertEquals(0, CoreHudLayout.buffFrame(CoreHudBuff(CoreHudBuffKind.WHETSTONE, 100.0, 180.0, false)))
    }

    @Test fun `every pixel step of every gauge is drawable and returns to the screen centre`() {
        for (health in 0..240) {
            val state = CoreHudState(health.toDouble(), 240.0, (240 - health) / 2.4, skills = skills(health % 9.0),
                resource = health / 2.4, shield = (health % 50).toDouble(), healthTrail = health + 30.0, level = health % 41,
                experience = health / 240.0, combatCue = if (health % 2 == 0) "防御 1秒" else "", buffs = CoreHudBuffKind.entries.map {
                    CoreHudBuff(it, health % 7.0, 7.0, it != CoreHudBuffKind.WHETSTONE) })
            assertEquals(0, layout(CoreUiComponents.hud(state, true)).second)
        }
        for (health in 0..20000 step 97) assertEquals(0, layout(CoreBossBarHud.render("執行官", health.toDouble(), 20000.0, health + 500.0)).second)
        for (health in 0..200 step 3) assertEquals(0, layout(CoreMobNameplate.render(9, "兵士", health.toDouble(), 200.0,
            CoreMobNameplate.Rank.ELITE, "thunder", null)).second)
        for (amount in listOf(0.0, 7.0, 99.0, 12345.0, Double.NaN)) for (crit in listOf(true, false))
            assertEquals(0, layout(CoreDamageNumbers.render(amount, crit, !crit)).second)
    }

    @Test fun `all seventy skill arts and every state have a glyph and keep exact centring`() {
        for (index in 0..69) for (remaining in listOf(0.0, 3.0)) {
            val skill = CoreHudSkill(CoreUiIcon.DASH, "6", remaining, 30.0, 10, artIndex = index)
            val state = CoreHudState(80.0, 140.0, 100.0, skills = List(5) { skill.copy(key = (it + 2).toString(), unlocked = it != 4, resourceAvailable = it != 3) })
            assertEquals(0, layout(CoreHudLayout.render(state)).second)
        }
        for (frame in 0..CoreHudLayout.LOCKED) CoreHudGlyphs["skill_69_$frame"]
        assertEquals(listOf(-94, -56, -18, 20, 58), CoreHudLayout.skillLeft)
    }

    @Test fun `skill cards plate and caption keep clear of vanilla rows they share the screen with`() {
        // Bitmap top = height - 65 - ascent. Skill row ends above the selected-item name (height-59).
        val plateTop = 47; val skillTop = 96; val captionTop = 113
        assertTrue(skillTop - CoreHudLayout.SKILL_SIZE > 59, "Selected item names stay readable")
        assertTrue(plateTop < 50 && plateTop - 24 >= 23, "Plate sits between the item name and the hotbar")
        assertTrue(captionTop - 16 >= skillTop, "Caption pill never covers a skill card")
        assertEquals(-91, CoreHudLayout.PLATE_X)
        assertEquals(CoreHudLayout.HP_X + CoreHudLayout.BAR_FILL + 22, CoreHudLayout.MP_X)
    }

    @Test fun `notices replace the combat cue in the caption slot and long text is trimmed`() {
        val cue = CoreHudState(100.0, 100.0, 100.0, combatCue = "反撃の好機 3秒")
        assertEquals("反撃の好機 3秒", CoreHudLayout.caption(cue))
        assertEquals("マナが足りません", CoreHudLayout.caption(cue.copy(notice = "マナが足りません")))
        val long = cue.copy(notice = "氷の庭は一つまで。今の庭が解けるまで待ってください".repeat(3))
        val glyphs = layout(CoreHudLayout.render(long)).first.filter { it.font == "projects:hud_text_slot" }
        assertTrue(glyphs.maxOf { it.x + it.advance } - glyphs.minOf { it.x } <= CoreHudLayout.MAX_CAPTION_WIDTH + 2)
    }

    @Test fun `override allowlist hides only the HUD parts the pixel HUD replaces`() {
        assertEquals(50 + 3 + 2 + 2, CoreUiPackPolicy.vanillaOverrides.size)
        assertEquals(setOf("assets/minecraft/atlases/items.json"), CoreUiPackPolicy.vanillaAdditions)
        assertTrue(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/armor_full.png"))
        assertTrue(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/experience_bar_progress.png"))
        assertTrue(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/boss_bar/pink_progress.png"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/boss_bar/red_progress.png"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/atlases/blocks.json"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/font/default.json"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/heart/vehicle_full.png"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/air.png"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/hotbar.png"))
        assertFalse(CoreUiPackPolicy.allowedPath("../invalid"))
        assertTrue(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/widget/button.png"))
    }

    @Test fun `resource starvation keeps artwork unobscured even when mana is also missing`() {
        val skill = skills().first().copy(resourceAvailable = false)
        for (mana in listOf(0.0, 100.0, Double.NaN)) {
            assertEquals(CoreHudLayout.SkillVisual(CoreHudLayout.NO_MANA, ""), CoreHudLayout.skillVisual(skill, mana))
            val glyphs = layout(CoreHudLayout.render(CoreHudState(100.0, 100.0, mana, skills = listOf(skill)))).first
            assertFalse(glyphs.any { g -> CoreHudGlyphs.advanceOf(net.kyori.adventure.key.Key.key(g.font), g.code.toChar()) != null &&
                (0..9).any { CoreHudGlyphs["big_cd_$it"].char.code == g.code && g.font == "projects:hud" } }, "No centre label may hide a resource-starved skill")
        }
        assertEquals(CoreHudLayout.SkillVisual(CoreHudLayout.CD_STEPS, "4"), CoreHudLayout.skillVisual(skill.copy(remainingSeconds = 4.0), 0.0))
        assertEquals(CoreHudLayout.SkillVisual(CoreHudLayout.LOCKED, ""), CoreHudLayout.skillVisual(skill.copy(unlocked = false), 100.0))
    }

    @Test fun `health trail holds after a hit then drains to the current value`() {
        val trail = CoreHudTrail(holdMillis = 400, drainPerSecond = .5)
        assertEquals(100.0, trail.update(100.0, 100.0, 0))
        assertEquals(100.0, trail.update(60.0, 100.0, 100), "Holds right after the hit")
        assertEquals(100.0, trail.update(60.0, 100.0, 400))
        assertEquals(75.0, trail.update(60.0, 100.0, 900), 1e-9)
        assertEquals(60.0, trail.update(60.0, 100.0, 1900))
        assertEquals(80.0, trail.update(80.0, 100.0, 2000), "Healing never shows a trail")
    }
}
