package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*
import kotlin.test.*

class CoreApprovedNormalV3Test {
    private val skill = CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon == "dash" }
    private fun effect(id: String, phase: CoreSkillVisualPhase = CoreSkillVisualPhase.PULSE) =
        CoreSkillEffect(CoreClass.WARRIOR, skill.copy(radius = 3.9), Vec.ZERO, Vec(0.0, 0.0, 1.0), phase, sceneId = id)

    @Test fun `the normal slash is off for now, the hit still shows`() {
        assertFalse(CoreApprovedNormalV3.SHOW_SLASH)
        for (id in CoreApprovedNormalV3.sceneIds) {
            assertTrue(CoreSkillChoreography.parts(effect(id)).filterNot(CoreWarriorCompanions::owns).isEmpty())
            val contact = CoreSkillChoreography.parts(effect(id, CoreSkillVisualPhase.CONTACT)).filterNot(CoreWarriorCompanions::owns).single()
            assertEquals("approved_dash_impact", contact.shape)
        }
    }
}
