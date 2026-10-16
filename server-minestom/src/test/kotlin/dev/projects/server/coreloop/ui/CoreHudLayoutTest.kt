package dev.projects.server.coreloop.ui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreHudLayoutTest {
    /** font "" = the vanilla default font (notices, boss name). */
    private data class Placed(val font: String, val code: Int, val x: Int, val advance: Int, val color: Int, val shadow: Boolean)

    /** Walk the component exactly as the client lays it out: every glyph's x and the net advance. */
    private fun layout(component: Component): Pair<List<Placed>, Int> {
        val glyphs = mutableListOf<Placed>()
        var x = 0
        fun visit(c: Component, inherited: net.kyori.adventure.key.Key?, color: Int, shadow: Boolean) {
            val font = c.font() ?: inherited
            val tint = c.color()?.value() ?: color
            val shadowed = if (c.shadowColor() != null) c.shadowColor()!!.alpha() > 0 else shadow
            val content = (c as? TextComponent)?.content().orEmpty()
            for (codepoint in content.codePoints().toArray()) {
                val char = codepoint.toChar()
                val width = if (font == null) CoreUiComponents.width(String(Character.toChars(codepoint)))
                else if (font == CoreUiComponents.SPACE_FONT) when (char.code) {
                    in 0xE100..0xE10B -> 1 shl (char.code - 0xE100)
                    in 0xE180..0xE18B -> -(1 shl (char.code - 0xE180))
                    else -> requireNotNull(CoreHudGlyphs.advanceOf(font, char)) { "Unknown glyph ${font.asString()} U+${char.code.toString(16)}" }
                } else requireNotNull(CoreHudGlyphs.advanceOf(font, char)) { "Unknown glyph ${font.asString()} U+${char.code.toString(16)}" }
                val space = font != null && char.code in CoreHudLine.SPACE_POSITIVE..CoreHudLine.SPACE_NEGATIVE + 11
                if (!space) glyphs += Placed(font?.asString() ?: "", codepoint, x, width, tint, shadowed)
                x += width
            }
            c.children().forEach { visit(it, font, tint, shadowed) }
        }
        visit(component, null, 0xFFFFFF, true)
        return glyphs to x
    }

    private fun glyphName(placed: Placed): String? = placed.font.takeIf { it.isNotEmpty() }?.let { font ->
        listOf("console", "emblem_warrior", "emblem_mage", "boss_plate").firstOrNull { name ->
            CoreHudGlyphs[name].let { it.font.asString() == font && it.char.code == placed.code } }
    }

    private fun skills(remaining: Double = 0.0) = listOf(
        CoreHudSkill(CoreUiIcon.DASH, "2", remaining, 4.0, 15),
        CoreHudSkill(CoreUiIcon.SLAM, "3", remaining, 7.0, 25),
        CoreHudSkill(CoreUiIcon.WHIRL, "4", remaining, 11.0, 35))

    private val loadout = listOf(0, 1, 2, 3, 8).mapIndexed { index, art ->
        CoreHudSkill(CoreUiIcon.DASH, (index + 2).toString(), 0.0, 12.0, 15, artIndex = art) }

    private fun combat(job: String = "warrior") = CoreHudState(132.0, 240.0, 46.0, skills = loadout.mapIndexed { i, s ->
        s.copy(remainingSeconds = listOf(0.0, 7.2, 0.0, 2.5, 0.0)[i], resourceAvailable = i != 4) }, resource = 35.0, shield = 48.0,
        healthTrail = 171.0, level = 12, experience = .62, notice = "マナが足りません", classKey = job,
        buffs = listOf(CoreHudBuff(CoreHudBuffKind.WHETSTONE, 96.0, 180.0, false), CoreHudBuff(CoreHudBuffKind.POTION, 6.3, 10.0, true)))

    @Test fun `export HUD surfaces from the actual components for the preview`() {
        val root = generateSequence(java.nio.file.Path.of("").toAbsolutePath()) { it.parent }
            .first { java.nio.file.Files.isRegularFile(it.resolve("settings.gradle.kts")) }
        val output = java.nio.file.Files.createDirectories(root.resolve("build/hud-preview/snapshots"))
        output.toFile().listFiles()?.forEach { it.delete() }
        val surfaces = listOf(
            Triple("actionbar", "ready", CoreUiComponents.hud(CoreHudState(240.0, 240.0, 100.0, skills = loadout, resource = 60.0,
                level = 12, experience = .42, buffs = listOf(CoreHudBuff(CoreHudBuffKind.WHETSTONE, 175.0, 180.0, false))), true)),
            Triple("actionbar", "combat", CoreUiComponents.hud(combat(), true)),
            Triple("actionbar", "counter", CoreUiComponents.hud(CoreHudState(180.0, 240.0, 80.0, skills = loadout.mapIndexed { i, s ->
                s.copy(remainingSeconds = if (i == 3) 3.0 else 0.0) }, resource = 100.0, level = 7, experience = .9,
                combatCue = "反撃の好機 3秒", classKey = "templar"), true)),
            Triple("actionbar", "unavailable", CoreUiComponents.hud(CoreHudState(18.0, 240.0, 4.0, skills = loadout.mapIndexed { i, s ->
                s.copy(remainingSeconds = if (i == 0) 8.0 else 0.0, resourceAvailable = i != 2, unlocked = i != 4) }, resource = 0.0,
                level = 3, experience = .05, notice = "マナが足りません（必要 15）", classKey = "assassin"), true)),
        ) + CoreHudLayout.classes.map { Triple("actionbar", "class-$it", CoreUiComponents.hud(combat(it), true)) } + listOf(
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
            assertTrue(GsonComponentSerializer.gson().serialize(component).length < 9000, "$surface/$name update stays small")
            assertTrue(glyphs.isNotEmpty())
            java.nio.file.Files.writeString(output.resolve("$surface-$name.json"), com.google.gson.Gson().toJson(mapOf(
                "surface" to surface, "state" to name, "netAdvance" to net,
                "jsonBytes" to GsonComponentSerializer.gson().serialize(component).length,
                "glyphs" to glyphs.map { mapOf("font" to it.font, "code" to it.code, "x" to it.x, "advance" to it.advance,
                    "color" to it.color, "shadow" to it.shadow) })))
        }
    }

    @Test fun `ready cooldown no mana and locked are separate states with cooldown taking priority`() {
        val skill = skills().first()
        assertEquals(CoreHudLayout.SkillVisual(CoreHudLayout.READY, ""), CoreHudLayout.skillVisual(skill, 15.0))
        assertEquals(CoreHudLayout.SkillVisual(CoreHudLayout.NO_MANA, ""), CoreHudLayout.skillVisual(skill, 14.0))
        assertEquals(CoreHudLayout.SkillVisual(CoreHudLayout.COOLDOWN, "4"), CoreHudLayout.skillVisual(skill.copy(remainingSeconds = 4.0), 0.0))
        assertEquals(CoreHudLayout.SkillVisual(CoreHudLayout.COOLDOWN, "1"), CoreHudLayout.skillVisual(skill.copy(remainingSeconds = .01), 100.0))
        assertEquals(CoreHudLayout.SkillVisual(CoreHudLayout.LOCKED, ""), CoreHudLayout.skillVisual(skill.copy(unlocked = false), 100.0))
        assertEquals(CoreHudLayout.SkillVisual(CoreHudLayout.NO_MANA, ""), CoreHudLayout.skillVisual(skill.copy(resourceAvailable = false), 100.0))
    }

    @Test fun `gauges cells and XP steps are whole and bounded for invalid external state`() {
        val skill = skills().first()
        assertEquals("99", CoreHudLayout.skillVisual(skill.copy(remainingSeconds = 120.0), 100.0).centre)
        assertEquals(CoreHudLayout.READY, CoreHudLayout.skillVisual(skill.copy(remainingSeconds = Double.NaN), 100.0).state)
        assertEquals(CoreHudLayout.NO_MANA, CoreHudLayout.skillVisual(skill, Double.NaN).state)
        assertEquals(0, CoreHudLayout.fillWidth(20.0, 0.0, 54))
        assertEquals(0, CoreHudLayout.fillWidth(Double.NaN, 100.0, 54))
        assertEquals(54, CoreHudLayout.fillWidth(200.0, 100.0, 54))
        assertEquals(1, CoreHudLayout.fillWidth(.01, 100.0, 54), "Any health left stays visible")
        assertEquals(0, CoreHudLayout.xpStep(0.0)); assertEquals(1, CoreHudLayout.xpStep(.001)); assertEquals(24, CoreHudLayout.xpStep(1.0))
        assertEquals(0, CoreHudLayout.xpStep(Double.NaN))
        assertEquals(7, CoreHudLayout.litCells(CoreHudState(1.0, 1.0, 1.0, resource = 35.0)))
        assertEquals(13, CoreHudLayout.litCells(CoreHudState(1.0, 1.0, 1.0, charges = 2)))
        assertEquals(0, CoreHudLayout.litCells(CoreHudState(1.0, 1.0, 1.0)))
        assertEquals(1, CoreHudLayout.buffFrame(CoreHudBuff(CoreHudBuffKind.POTION, .1, 10.0, true)))
        assertEquals(CoreHudLayout.BUFF_STEPS, CoreHudLayout.buffFrame(CoreHudBuff(CoreHudBuffKind.POTION, 10.0, 10.0, true)))
        assertEquals(0, CoreHudLayout.buffFrame(CoreHudBuff(CoreHudBuffKind.WHETSTONE, 100.0, 180.0, false)))
    }

    @Test fun `every pixel step of every gauge is drawable and returns to the screen centre`() {
        for (health in 0..240) {
            val state = CoreHudState(health.toDouble(), 240.0, (240 - health) / 2.4, skills = skills(health % 9.0),
                resource = health / 2.4, shield = (health % 50).toDouble(), healthTrail = health + 30.0, level = health % 41,
                experience = health / 240.0, combatCue = if (health % 2 == 0) "防御 1秒" else "",
                classKey = CoreHudLayout.classes[health % 6], buffs = CoreHudBuffKind.entries.map {
                    CoreHudBuff(it, health % 7.0, 7.0, it != CoreHudBuffKind.WHETSTONE) })
            assertEquals(0, layout(CoreUiComponents.hud(state, true)).second)
        }
        for (health in 0..20000 step 97) assertEquals(0, layout(CoreBossBarHud.render("執行官", health.toDouble(), 20000.0, health + 500.0)).second)
        for (health in 0..200 step 3) assertEquals(0, layout(CoreMobNameplate.render(9, "兵士", health.toDouble(), 200.0,
            CoreMobNameplate.Rank.ELITE, "thunder", null)).second)
        for (amount in listOf(0.0, 7.0, 99.0, 12345.0, Double.NaN)) for (crit in listOf(true, false))
            assertEquals(0, layout(CoreDamageNumbers.render(amount, crit, !crit)).second)
    }

    @Test fun `all seventy skill arts have every state and sit exactly on hotbar slots two to six`() {
        for (index in 0..69) for (state in listOf(CoreHudLayout.READY, CoreHudLayout.COOLDOWN, CoreHudLayout.NO_MANA, CoreHudLayout.LOCKED))
            assertEquals(17, CoreHudGlyphs["skill_${index}_$state"].advance, "32 texels drawn 16 px wide")
        // Vanilla draws hotbar item i at x = centre - 91 + 3 + 20 i; keys 2-6 are slots 1-5.
        val glyphs = layout(CoreHudLayout.render(CoreHudState(80.0, 140.0, 100.0, skills = loadout))).first
        val icons = glyphs.filter { it.font == "projects:hud_skills" }.map { it.x }
        assertEquals((1..5).map { -91 + 3 + 20 * it }, icons)
    }

    @Test fun `every class has its decorated diamond and all XP steps and unknown classes fall back`() {
        for (job in CoreHudLayout.classes) {
            CoreHudGlyphs["emblem_$job"]
            for (step in 1..CoreHudLayout.XP_STEPS) CoreHudGlyphs["xp_${job}_$step"]
        }
        assertEquals("warrior", CoreHudLayout.job(CoreHudState(1.0, 1.0, 1.0, classKey = "UNKNOWN")))
        assertEquals("mage", CoreHudLayout.job(CoreHudState(1.0, 1.0, 1.0, classKey = "STARWEAVER")))
        assertEquals("healer", CoreHudLayout.job(CoreHudState(1.0, 1.0, 1.0, classKey = "HEALER")))
    }

    @Test fun `numbers use the vanilla font with its shadow and sit centred in their gauges`() {
        val glyphs = layout(CoreHudLayout.render(combat())).first
        fun inkCentre(font: String, from: Int, until: Int): Double {
            val run = glyphs.filter { it.font == font && it.x in from until until && it.code != CoreHudGlyphs.ASCII_BASE + ' '.code }
            assertTrue(run.isNotEmpty() && run.all { it.shadow }, "vanilla numbers keep the drop shadow")
            val left = run.minOf { it.x }
            val right = run.maxOf { it.x + it.advance - 2 }
            return (left + right) / 2.0
        }
        assertEquals(CoreHudLayout.HP_X + (CoreHudLayout.HP_W - 1) / 2.0, inkCentre("projects:hud_num_gauge", -78, -20), 1.0)
        assertEquals(CoreHudLayout.MP_X + (CoreHudLayout.MP_W - 1) / 2.0, inkCentre("projects:hud_num_gauge", 20, 80), 1.0)
        assertEquals(0.0, inkCentre("projects:hud_num_gauge", -10, 10), 1.0)
        assertTrue(glyphs.filter { it.font.startsWith("projects:hud") && !it.font.startsWith("projects:hud_num") }.none { it.shadow },
            "drawn HUD art never gets a text shadow")
    }

    @Test fun `notices replace the combat cue on the vanilla text row and long text is trimmed`() {
        val cue = CoreHudState(100.0, 100.0, 100.0, combatCue = "反撃の好機 3秒")
        assertEquals("反撃の好機 3秒", CoreHudLayout.caption(cue))
        assertEquals("マナが足りません", CoreHudLayout.caption(cue.copy(notice = "マナが足りません")))
        val glyphs = layout(CoreHudLayout.render(cue.copy(notice = "マナが足りません"))).first.filter { it.font == "" }
        assertEquals(0.0, (glyphs.minOf { it.x } + glyphs.maxOf { it.x + it.advance }) / 2.0, 1.0, "notice is centred")
        val long = layout(CoreHudLayout.render(cue.copy(notice = "氷の庭は一つまで。今の庭が解けるまで待ってください".repeat(3)))).first.filter { it.font == "" }
        assertTrue(long.maxOf { it.x + it.advance } - long.minOf { it.x } <= CoreHudLayout.MAX_CAPTION_WIDTH)
    }

    @Test fun `boss bar keeps its plate gauge and percentage inside the plate`() {
        val glyphs = layout(CoreBossBarHud.render("裂け目の執行官", 12480.0, 20000.0, 14900.0)).first
        assertEquals("62%", CoreBossBarHud.percent(12480.0, 20000.0))
        assertEquals(CoreBossBarHud.PLATE_X, glyphs.first { glyphName(it) == "boss_plate" }.x)
        val pct = glyphs.filter { it.font == "projects:hud_num_boss" }
        assertEquals(CoreBossBarHud.FILL_X + CoreBossBarHud.FILL, pct.maxOf { it.x + it.advance - 1 })
        assertTrue(glyphs.filter { it.font == "" }.minOf { it.x } == CoreBossBarHud.NAME_X)
    }

    @Test fun `override allowlist hides only the HUD parts the HUD replaces and reskins the hotbar`() {
        assertEquals(50 + 3 + 2 + 2 + 2, CoreUiPackPolicy.vanillaOverrides.size)
        assertEquals(setOf("assets/minecraft/atlases/items.json"), CoreUiPackPolicy.vanillaAdditions)
        assertTrue(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/armor_full.png"))
        assertTrue(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/experience_bar_progress.png"))
        assertTrue(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/boss_bar/pink_progress.png"))
        assertTrue(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/hotbar.png"))
        assertTrue(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/boss_bar/red_progress.png"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/atlases/blocks.json"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/font/default.json"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/heart/vehicle_full.png"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/air.png"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/hotbar_offhand_left.png"))
        assertFalse(CoreUiPackPolicy.allowedPath("../invalid"))
        assertTrue(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/widget/button.png"))
    }

    @Test fun `starved skills show no number over their artwork`() {
        val skill = skills().first().copy(resourceAvailable = false)
        for (mana in listOf(0.0, 100.0, Double.NaN)) {
            assertEquals(CoreHudLayout.SkillVisual(CoreHudLayout.NO_MANA, ""), CoreHudLayout.skillVisual(skill, mana))
            val glyphs = layout(CoreHudLayout.render(CoreHudState(100.0, 100.0, mana, skills = listOf(skill)))).first
            assertFalse(glyphs.any { it.font == "projects:hud_num_cd" }, "No number may cover a resource-starved skill")
        }
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
