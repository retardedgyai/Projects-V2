package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*
import kotlin.test.*

/** Lifecycle regressions retained across the replacement of the old spinning frost bands. */
class CoreFrostChoreographyTest {
    private val skill=CoreSkillCatalog.skills(CoreClass.MAGE).first { it.icon=="mage_zero" }
    private fun effect(pulse:Int=0,radius:Double=skill.radius)=
        CoreSkillEffect(CoreClass.MAGE,skill.copy(radius=radius),Vec.ZERO,Vec(0.0,0.0,1.0),pulse=pulse)

    @Test fun `zero starts once follows the actual nova origin and clears after the fifth hit`() {
        val first=CoreSkillChoreography.parts(effect())
        assertEquals(4,first.size);assertTrue(first.all { it.ground })
        assertEquals(4,first.map { it.shape }.toSet().size)
        assertTrue(first.all { it.followOwner && !it.sprite && it.durationTicks==48 })
        for(pulse in 1..4) {
            val beat=CoreSkillChoreography.parts(effect(pulse)).single()
            assertTrue(beat.secondary && beat.followOwner && beat.durationTicks==8)
            assertEquals("mage_material:ice_pulse",beat.shape)
        }
        for(p in first) {
            assertFalse(CoreSkillChoreography.pose(p,-1.0).visible)
            assertTrue(CoreSkillChoreography.pose(p,0.0).visible)
            assertTrue(CoreSkillChoreography.pose(p,47.0).visible)
            assertFalse(CoreSkillChoreography.pose(p,48.0).visible)
        }
    }

    @Test fun `long frost uses forty eight monotonic material frames never restarts or spins a card`() {
        for(p in CoreSkillChoreography.parts(effect())) {
            val poses=(0..47).map { CoreSkillChoreography.pose(p,it.toDouble()) }
            assertEquals(48,poses.map { it.model }.toSet().size)
            assertEquals(1,poses.map { it.yaw to it.pitch }.toSet().size)
            assertEquals(1,poses.map { it.scale to it.offset }.toSet().size)
            assertEquals((0..47).toList(),poses.map { it.model.substringAfterLast('_').toInt() })
        }
    }

    @Test fun `three unequal ice faces stay within reach and point their painted faces toward the owner`() {
        for(radius in listOf(.5,1.0,4.0,8.0,10.5)) {
            val reach=min(radius,CoreSkillScenes.get("mage_zero").reach)
            for(p in CoreSkillChoreography.parts(effect(radius=radius))) {
                assertEquals(.12,p.offset.y())
                if(p.shape=="mage_material:zero_floor") {
                    assertEquals(reach*1.36,p.scale.x(),.00001)
                } else {
                    val distance=hypot(p.offset.x(),p.offset.z())
                    assertTrue(distance in reach*.45..reach*.60)
                    assertTrue(hypot(distance,p.scale.x()*.5)+p.scale.z()*2.6/32<reach)
                    // A previous arbitrary yaw exposed only strip-shaped side
                    // caps to the caster and hid the painted primary faces.
                    assertEquals(atan2(p.offset.x(),p.offset.z()),atan2(sin(p.yaw),cos(p.yaw)),.00001)
                }
            }
        }
    }

    @Test fun `invalid frost stays rejected and contact never creates another domain`() {
        assertTrue(CoreSkillChoreography.parts(effect(radius=Double.NaN)).isEmpty())
        for(phase in listOf(CoreSkillVisualPhase.PREPARE,CoreSkillVisualPhase.CONTACT)) {
            val e=CoreSkillEffect(CoreClass.MAGE,skill,Vec.ZERO,Vec(0.0,0.0,1.0),phase)
            assertTrue(CoreSkillChoreography.parts(e).none { it.shape.contains("zero_") })
        }
    }
}
