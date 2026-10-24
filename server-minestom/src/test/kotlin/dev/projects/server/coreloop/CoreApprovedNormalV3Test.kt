package dev.projects.server.coreloop

import com.google.gson.JsonParser
import dev.projects.server.particle.RecordingParticleSink
import net.minestom.server.coordinate.Vec
import kotlin.math.*
import kotlin.test.*

class CoreApprovedNormalV3Test {
    private val skill = CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon == "dash" }
    private fun effect(id: String, phase: CoreSkillVisualPhase = CoreSkillVisualPhase.PULSE,
        heading: Double = 0.0, pitchDegrees: Double = 12.0) = CoreSkillEffect(CoreClass.WARRIOR,
        skill.copy(radius = 3.9), Vec.ZERO, Vec(sin(heading), 0.0, cos(heading)), phase,
        sceneId = id).also { it.lookPitch = Math.toRadians(pitchDegrees) }

    @Test fun `each beat has its own slash, a tick-by-tick blade and a longer wake, all in the pack`() {
        for ((beat, id) in listOf("normal_sweep", "normal_reverse", "normal_finish").withIndex()) {
            val parts = CoreSkillChoreography.parts(effect(id)).filterNot(CoreWarriorCompanions::owns)
            assertEquals(listOf("normal_slash_blade_${beat + 1}", "normal_slash_wake_${beat + 1}"), parts.map { it.shape })
            assertEquals(listOf(CoreApprovedNormalV3.BLADE_TICKS, CoreApprovedNormalV3.WAKE_TICKS), parts.map { it.durationTicks })
            assertEquals(listOf(false, true), parts.map { it.secondary })
            for (p in parts) {
                assertEquals(0, CoreCombatMeshes.interpolationTicks(p))
                for (age in 0 until p.durationTicks) {
                    val pose = CoreSkillChoreography.pose(p, age.toDouble())
                    assertTrue(pose.model.endsWith("b${beat + 1}_${if (p.secondary) "wake" else "blade"}_$age"))
                    assertTrue(pose.visible)
                    assertNotNull(javaClass.getResource("/core-ui-pack/assets/projects/items/${pose.model}.json"))
                }
                assertFalse(CoreSkillChoreography.pose(p, -1.0).visible)
                assertFalse(CoreSkillChoreography.pose(p, p.durationTicks.toDouble()).visible)
            }
            val contact = CoreSkillChoreography.parts(effect(id, CoreSkillVisualPhase.CONTACT)).filterNot(CoreWarriorCompanions::owns).single()
            assertEquals("approved_dash_impact", contact.shape)
            val e = effect(id).also { it.solidCompanion = true }
            val sink = RecordingParticleSink()
            repeat(e.durationTicks) {
                sink.clear(); e.emit(it, sink)
                assertTrue(sink.spawns.isEmpty(), "No vanilla particles around the slash")
            }
        }
    }

    @Test fun `the slash turns with the facing and round the eye with the look pitch`() {
        val eye = Vec(0.0, 1.62, 0.0)
        for (heading in listOf(0.0, 1.0, -2.5)) for (pitch in listOf(-40.0, 12.0, 30.0, 70.0)) {
            val p = CoreSkillChoreography.parts(effect("normal_sweep", heading = heading, pitchDegrees = pitch)).first()
            assertEquals(heading, p.yaw, 1e-9)
            assertEquals(Math.toRadians(pitch - 12.0).coerceIn(-PI / 3, PI / 3), p.pitch, 1e-9)
            // The eye is the pivot: where the display's turn takes the eye, the offset brings it back.
            val turned = Vec(0.0, eye.y() * cos(p.pitch), eye.y() * sin(p.pitch))
            val world = Vec(sin(p.yaw) * turned.z(), turned.y(), cos(p.yaw) * turned.z()).add(p.offset)
            assertEquals(0.0, world.distance(eye), 1e-9)
        }
        assertEquals(0.0, CoreSkillChoreography.parts(effect("normal_sweep")).first().offset.length(), 1e-12)
    }

    @Test fun `every slash piece stays near the player and above the ground as drawn`() {
        for (id in CoreApprovedNormalV3.sceneIds) for (p in CoreSkillChoreography.parts(effect(id))) repeat(p.durationTicks) { age ->
            val pose = CoreSkillChoreography.pose(p, age.toDouble())
            val model = javaClass.getResourceAsStream("/core-ui-pack/assets/projects/models/${pose.model}.json")!!
                .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
            for (element in model.getAsJsonArray("elements")) {
                val o = element.asJsonObject.getAsJsonObject("rotation").getAsJsonArray("origin").map { it.asDouble }
                // Model pixels are a quarter block about the model's middle, with the display's half turn undone.
                val x = -(o[0] - 8) / 4; val y = (o[1] - 8) / 4; val z = -(o[2] - 8) / 4
                assertTrue(y > .1, "$id buried at age=$age y=$y")
                assertTrue(hypot(x, z) < 3.2, "$id too far at age=$age")
            }
        }
    }
}
