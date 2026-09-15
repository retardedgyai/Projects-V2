package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.test.*

class CoreMageFrostChoreographyTest {
    private fun effect(phase:CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE,prepare:Int=10)=
        CoreSkillEffect(CoreClass.MAGE,CoreSkillCatalog.skills(CoreClass.MAGE).first { it.icon=="frost_nova" },
            Vec.ZERO,Vec(0.0,0.0,1.0),phase,prepareTicks=prepare)

    @Test fun `six unequal rooted clusters surround an open central footing`() {
        val parts=CoreSkillChoreography.parts(effect())
        assertEquals(7,parts.size)
        assertEquals(7,parts.map { it.shape }.distinct().size)
        assertTrue(parts.all { it.ground && !it.followOwner && it.travel==Vec.ZERO && !it.secondary })
        assertEquals(3,parts.drop(1).map { it.yaw }.distinct().size)
        assertTrue(parts.all { it.scale.x()==3.6 && it.scale.z()==3.6 })
        val inner=parts.filter { it.shape.contains("inner_") }
        val outer=parts.filter { it.shape.contains("outer_") }
        assertTrue(inner.maxOf { it.delayTicks }<outer.minOf { it.delayTicks })
        assertTrue(inner.maxOf { it.scale.y() }<outer.minOf { it.scale.y() })
    }

    @Test fun `planted contours interpolate without changing models rotating or moving roots`() {
        for(phase in listOf(CoreSkillVisualPhase.PREPARE,CoreSkillVisualPhase.PULSE))
            for(p in CoreSkillChoreography.parts(effect(phase))) {
                assertEquals(1,CoreCombatMeshes.interpolationTicks(p))
                val poses=(0..(p.durationTicks+p.delayTicks)*10).map { CoreSkillChoreography.pose(p,it/10.0) }
                assertEquals(1,poses.map { it.model }.distinct().size)
                assertEquals(1,poses.map { it.offset }.distinct().size)
                assertTrue(poses.all { it.yaw==p.yaw && it.pitch==0.0 && it.roll==0.0 })
                assertTrue(poses.all { it.scale.x().isFinite() && it.scale.y()>=0 && it.scale.z().isFinite() })
                assertEquals(Vec.ZERO,CoreSkillChoreography.pose(p,p.delayTicks+p.durationTicks-1.0).scale)
                assertFalse(poses.last().visible)
            }
    }

    @Test fun `preparation is at the feet and contact cannot create a second full crown`() {
        for(ticks in listOf(1,2,10,40)) {
            val p=CoreSkillChoreography.parts(effect(CoreSkillVisualPhase.PREPARE,ticks)).single()
            assertEquals("mage_material:rime_footing",p.shape)
            assertTrue(p.ground && p.followOwner)
            assertEquals(Vec.ZERO,CoreSkillChoreography.pose(p,ticks-1.0).scale)
        }
        val hit=CoreSkillChoreography.parts(effect(CoreSkillVisualPhase.CONTACT)).single()
        assertEquals("mage_material:ice_hit",hit.shape)
        assertTrue(hit.scale.x()<1.5)
    }
}
