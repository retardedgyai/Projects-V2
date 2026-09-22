package dev.projects.server.coreloop.ui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreHudLayoutTest {
    @Test fun `export complete warrior HUD glyph positions from the actual component`() {
        val root=generateSequence(java.nio.file.Path.of("").toAbsolutePath()) { it.parent }
            .first { java.nio.file.Files.isRegularFile(it.resolve("settings.gradle.kts")) }
        val output=java.nio.file.Files.createDirectories(root.resolve("build/readable-ui-preview"))
        val loadout=listOf(0,1,2,3,8).mapIndexed { index,art ->
            CoreHudSkill(CoreUiIcon.DASH,(index+2).toString(),0.0,12.0,15,artIndex=art)
        }
        val states=listOf(
            "ready" to CoreHudState(180.0,240.0,80.0,skills=loadout,resource=60.0,resourceMaximum=100.0),
            "counter" to CoreHudState(180.0,240.0,80.0,skills=loadout.mapIndexed { i,s ->
                s.copy(remainingSeconds=if(i==3)3.0 else 0.0) },resource=60.0,resourceMaximum=100.0,combatCue="反撃の好機 3秒"),
            "guard" to CoreHudState(180.0,240.0,80.0,skills=loadout,resource=60.0,resourceMaximum=100.0,shield=120.0,combatCue="防御 8秒"),
            "unavailable" to CoreHudState(180.0,240.0,0.0,skills=loadout.mapIndexed { i,s ->
                s.copy(remainingSeconds=if(i==0)8.0 else 0.0,resourceAvailable=i!=2,unlocked=i!=4) },resource=0.0,resourceMaximum=100.0)
        )
        for((name,state) in states) {
            val glyphs=mutableListOf<Map<String,Any>>()
            var x=0
            fun visit(c:Component,inherited:net.kyori.adventure.key.Key?=null,color:Int=0xffffff) {
                val font=c.font()?:inherited
                val tint=c.color()?.value()?:color
                for(char in (c as? TextComponent)?.content().orEmpty()) {
                    val key=requireNotNull(font)
                    val width=advance(Component.text(char.toString()).font(key))
                    glyphs+=mapOf("font" to key.asString(),"code" to char.code,"x" to x,"advance" to width,"color" to tint)
                    x+=width
                }
                c.children().forEach { visit(it,font,tint) }
            }
            visit(CoreHudLayout.render(state))
            assertEquals(0,x)
            assertTrue(glyphs.isNotEmpty())
            java.nio.file.Files.writeString(output.resolve("warrior-hud-$name.json"),com.google.gson.Gson().toJson(
                mapOf("state" to name,"glyphs" to glyphs,"netAdvance" to x)))
        }
    }

    private val acceptedWidths by lazy { javaClass.getResourceAsStream("/core-ui-pack/assets/projects/menu/glyphs-emphasis.tsv")!!
        .bufferedReader().readLines().filter { it.isNotBlank() && !it.startsWith('#') }.associate {
            val parts=it.split('\t');parts[1].toInt(16).toChar() to parts[2].toInt()
        } }
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
                    in 0xE300..0xE354 -> 82
                    0xE380 -> 10
                    in 0xE400..0xE457 -> 33
                    in 0xE500..0xE50E -> 9
                    in 0xE520..0xE56E -> 4
                    in 0xE600..0xEE57 -> 33
                    else -> error("Unexpected HUD glyph ${c.code}")
                }
                component.style().font()?.asString()=="projects:warrior_hud_status" ->
                    acceptedWidths.getValue(c)
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
        assertEquals(setOf("assets/minecraft/atlases/items.json"), CoreUiPackPolicy.vanillaAdditions)
        assertTrue(CoreUiPackPolicy.allowedPath("assets/minecraft/atlases/items.json"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/atlases/blocks.json"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/font/default.json"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/armor_full.png"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/heart/vehicle_full.png"))
        assertFalse(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/air.png"))
        assertFalse(CoreUiPackPolicy.allowedPath("../invalid"))
        assertTrue(CoreUiPackPolicy.allowedPath("assets/minecraft/textures/gui/sprites/hud/heart/full.png"))
    }

    @Test fun `combat opportunity caption keeps exact centering without covering the skill row`() {
        val font=javaClass.getResourceAsStream("/core-ui-pack/assets/projects/font/warrior_hud_status.json")!!
            .bufferedReader().use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
        val providers=font.getAsJsonArray("providers").map { it.asJsonObject }
        val characters=providers.filter { it.get("type").asString=="bitmap" }
            .flatMap { it.getAsJsonArray("chars").map { row -> row.asString }.joinToString("").toList() }.toSet()
        for(text in listOf("反撃の好機 3秒","防御 1秒")) {
            val encoded=(CoreMenuCanvas.combatCaption(text) as TextComponent).content()
            assertTrue(encoded.filter { it.code!=0xE800 }.all { it in characters })
            val state=CoreHudState(100.0,100.0,100.0,skills=skills(),combatCue=text)
            assertEquals(0,advance(CoreHudLayout.render(state)))
        }
        providers.filter { it.get("type").asString=="bitmap" }.forEach {
            assertEquals(45,it.get("ascent").asInt)
            assertTrue(it.get("ascent").asInt<=it.get("height").asInt)
            assertEquals(48,it.get("height").asInt)
            assertTrue(65+it.get("ascent").asInt-14>=94+2,"Actual ink, not transparent padding, stays above icons")
        }
    }

    @Test fun `five skill slots resource shield and all seventy art mappings retain exact centering`() {
        for(index in 0..69) {
            val skill=CoreHudSkill(CoreUiIcon.DASH,"6",0.0,30.0,10,artIndex=index,resourceAvailable=false)
            assertEquals(CoreHudLayout.SkillVisual(21,""),CoreHudLayout.skillVisual(skill,100.0))
            val state=CoreHudState(80.0,140.0,100.0,skills=List(5){skill.copy(key=(it+2).toString())},resource=60.0,shield=42.0)
            assertEquals(0,advance(CoreHudLayout.render(state)))
        }
        assertEquals(listOf(-88,-52,-16,20,56),CoreHudLayout.skillLeft)
    }

    @Test fun `resource starvation keeps artwork unobscured even when mana is also missing`() {
        val skill = skills().first().copy(resourceAvailable = false)
        for (mana in listOf(0.0, 100.0, Double.NaN)) {
            assertEquals(CoreHudLayout.SkillVisual(CoreHudLayout.NO_MANA, ""), CoreHudLayout.skillVisual(skill, mana))
            val rendered = CoreHudLayout.render(CoreHudState(100.0, 100.0, mana, skills = listOf(skill)))
            fun hasCentreGlyph(component: Component): Boolean =
                (component.style().font() == CoreUiComponents.HUD_FONT &&
                    (component as? TextComponent)?.content().orEmpty().any { it.code in 0xE500..0xE50D }) ||
                    component.children().any(::hasCentreGlyph)
            assertFalse(hasCentreGlyph(rendered), "No RP/MP label may hide a resource-starved skill")
            assertEquals(0, advance(rendered))
        }
        assertEquals(CoreHudLayout.SkillVisual(20, "4"),
            CoreHudLayout.skillVisual(skill.copy(remainingSeconds = 4.0), 0.0))
        assertEquals(CoreHudLayout.SkillVisual(CoreHudLayout.LOCKED, ""),
            CoreHudLayout.skillVisual(skill.copy(unlocked = false), 100.0))
    }
}
