package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.test.*
import kotlin.math.*

/** 体当たり / 跳び叩き in the world (2026-10-27): only what the body and blade leave, never a band for the caster. */
class CoreApprovedDashV3Test {
    private val dash=CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon=="dash" }
    private val leap=CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon==CoreWarriorLeap.ICON }
    private fun effect(skill: CoreSkillDefinition,phase: CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE,pulse: Int=0,direction: Vec=Vec(0.0,0.0,1.0))=
        CoreSkillEffect(CoreClass.WARRIOR,skill,Vec.ZERO,direction,phase,pulse=pulse,prepareTicks=skill.startup)
    private fun all(e: CoreSkillEffect)=CoreSkillChoreography.parts(e)+CoreWarriorCompanions.parts(e)
    private val R=CoreWarriorRefChoreography
    private val dashLooks=(0..6).map { R.TACKLE_STEP_PULSE+it }+listOf(R.TACKLE_STOP_PULSE,R.TACKLE_HIT_PULSE,R.TACKLE_CAUGHT_PULSE,R.TACKLE_CATCH_PULSE)
    private val leapLooks=listOf(R.LEAP_TAKEOFF_PULSE,R.LEAP_LAND_PULSE,R.LEAP_FAR_LAND_PULSE)

    @Test fun `the old dash blade and wake and the leap's slash are gone, the skills' own pulses draw nothing`() {
        for(skill in listOf(dash,leap)) {
            assertTrue(all(effect(skill)).isEmpty(),"${skill.icon} pulse 0")
            assertTrue(all(effect(skill,pulse=2)).isEmpty(),"${skill.icon} pulse 2")
            for(phase in CoreSkillVisualPhase.entries) for(p in all(effect(skill,phase)))
                assertTrue(p.shape.startsWith("war_ref:"),"${skill.icon} $phase ${p.shape}")
        }
        // The normal hits still land with the approved impact.
        assertEquals(listOf("approved_dash_impact"),CoreApprovedDashV3.parts(effect(dash,CoreSkillVisualPhase.CONTACT))!!.map { it.shape })
        assertNull(CoreApprovedDashV3.parts(effect(dash)))
    }

    @Test fun `every look has its pieces and every pose names a shipped model`() {
        for((skill,pulses) in listOf(dash to dashLooks,leap to leapLooks)) for(n in pulses) {
            val parts=all(effect(skill,pulse=n))
            assertTrue(parts.isNotEmpty(),"${skill.icon} $n")
            for(p in parts) for(t in -1..p.durationTicks) {
                val pose=CoreSkillChoreography.pose(p,(p.delayTicks+t).toDouble())
                // The heaved turf is the real ground block under it (CoreCombatMeshes.GROUND), not a pack model.
                if(t in 0 until p.durationTicks) { if(pose.model!=CoreCombatMeshes.GROUND)
                    assertNotNull(javaClass.getResource("/core-ui-pack/assets/projects/items/${pose.model}.json"),pose.model) }
                else assertFalse(pose.visible,"${skill.icon} $n ${p.shape} t=$t")
            }
        }
        // A far leap lands bigger: a second, wider ring of heaved turf.
        val near=all(effect(leap,pulse=R.LEAP_LAND_PULSE)); val far=all(effect(leap,pulse=R.LEAP_FAR_LAND_PULSE))
        assertTrue(far.count { it.shape.startsWith("war_ref:turf") }>near.count { it.shape.startsWith("war_ref:turf") }+3)
        // Caught: harder than a plain hit.
        assertTrue(all(effect(dash,pulse=R.TACKLE_CAUGHT_PULSE)).size>all(effect(dash,pulse=R.TACKLE_HIT_PULSE)).size)
    }

    @Test fun `in first person nothing in front comes within two blocks of the eye, at all eight headings`() {
        val eye=Vec(0.0,1.62,0.0)
        repeat(8) { heading ->
            val a=heading*PI/4
            val d=Vec(sin(a),0.0,cos(a))
            for((skill,pulses) in listOf(dash to dashLooks,leap to leapLooks)) for(n in pulses) {
                for(p in all(effect(skill,pulse=n,direction=d))) repeat(p.durationTicks) { t ->
                    val at=CoreSkillChoreography.pose(p,(p.delayTicks+t).toDouble()).offset
                    // Behind the eye (the heel's grit, the push-off) is never on screen.
                    if(at.x()*d.x()+at.z()*d.z()<=.3) return@repeat
                    assertTrue(at.distance(eye)>=2.0,"${skill.icon} $n ${p.shape} tick $t at $at")
                }
            }
        }
    }
}
