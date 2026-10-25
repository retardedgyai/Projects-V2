package dev.projects.server.coreloop

import dev.projects.server.particle.RecordingParticleSink
import net.minestom.server.coordinate.Vec
import kotlin.math.*
import kotlin.test.*

class CoreApprovedNormalV3Test {
    private val skill = CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon == "dash" }
    private fun effect(id: String, phase: CoreSkillVisualPhase = CoreSkillVisualPhase.PULSE, heading: Double = 0.0) =
        CoreSkillEffect(CoreClass.WARRIOR, skill.copy(radius = 3.9), Vec.ZERO, Vec(sin(heading), 0.0, cos(heading)), phase, sceneId = id)

    @Test fun `each cut leaves the skills' band of wind, smaller and shorter than the skills', in its own plane`() {
        val frenzy = CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon == CoreWarriorRules.FRENZY }
        val skillBand = CoreSkillChoreography.parts(CoreSkillEffect(CoreClass.WARRIOR, frenzy, Vec.ZERO, Vec(0.0, 0.0, 1.0),
            sceneId = CoreWarriorRules.FRENZY)).first { it.shape == "war_ref:wind" }
        val tilts = mutableListOf<Double>()
        for (id in listOf("normal_sweep", "normal_reverse", "normal_finish")) {
            val parts = CoreSkillChoreography.parts(effect(id)).filterNot(CoreWarriorCompanions::owns)
            val band = parts.single()
            assertEquals("war_ref:wind", band.shape)
            assertTrue(band.notOwner, "the owner sees the slash drawn with the sword in the hand")
            assertTrue(band.scale.x() < skillBand.scale.x())
            assertTrue(band.durationTicks < skillBand.durationTicks)
            for (age in 0 until band.durationTicks) {
                val pose = CoreSkillChoreography.pose(band, age.toDouble())
                assertTrue(pose.visible)
                assertNotNull(javaClass.getResource("/core-ui-pack/assets/projects/items/${pose.model}.json"))
            }
            assertFalse(CoreSkillChoreography.pose(band, band.durationTicks.toDouble()).visible)
            tilts += band.pitch
            val contact = CoreSkillChoreography.parts(effect(id, CoreSkillVisualPhase.CONTACT)).filterNot(CoreWarriorCompanions::owns).single()
            assertEquals("approved_dash_impact", contact.shape)
            val e = effect(id).also { it.solidCompanion = true }
            val sink = RecordingParticleSink()
            repeat(e.durationTicks) { sink.clear(); e.emit(it, sink); assertTrue(sink.spawns.isEmpty(), "No vanilla particles around the slash") }
        }
        assertEquals(3, tilts.size)
    }

    /** Where the board's own axis lands in the world (CoreCombatMeshArt.rotation, then the item's half turn undone). */
    private fun axis(p: CoreCombatMeshPart, v: Vec): Vec {
        val q = CoreCombatMeshArt.rotation(p.yaw, p.pitch, p.roll).map { it.toDouble() }
        val (x, y, z, w) = q
        val u = Vec(x, y, z)
        val t = u.cross(v).mul(2.0)
        return v.add(t.mul(w)).add(u.cross(t))
    }

    @Test fun `the crescent stands ahead facing the eye along the look, whatever the facing and pitch`() {
        for (heading in listOf(0.0, 1.0, -2.5)) for (pitch in listOf(0.0, .4, -.3)) {
            val look = Vec(sin(heading) * cos(pitch), -sin(pitch), cos(heading) * cos(pitch))
            val right = Vec(-cos(heading), 0.0, sin(heading))
            fun band(id: String) = CoreSkillChoreography.parts(effect(id, heading = heading).also { it.lookPitch = pitch })
                .filterNot(CoreWarriorCompanions::owns).single()
            for (id in CoreApprovedNormalV3.sceneIds) {
                val b = band(id)
                assertEquals(1.0, abs(axis(b, Vec(0.0, 0.0, 1.0)).dot(look)), 1e-6, "$id faces the eye")
                val pivot = b.offset.add(axis(b, Vec(-.25 * b.scale.x(), -.03125 * b.scale.x(), 0.0)))
                assertEquals(2.2, pivot.sub(0.0, 1.62, 0.0).dot(look), 1e-6, "$id stands ahead")
            }
            // the two sweeps mirror each other across the view's middle: the board's axes swap sides
            val a = axis(band("normal_sweep"), Vec(1.0, 0.0, 0.0)).dot(right)
            val b = axis(band("normal_reverse"), Vec(1.0, 0.0, 0.0)).dot(right)
            assertEquals(-a, b, 1e-6)
        }
    }
}
