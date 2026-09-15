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
        assertEquals(6,parts.drop(1).map { it.yaw }.distinct().size)
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

    @Test fun `inner and outer bundles resolve terrain beneath their own anchored roots`() {
        for(p in CoreSkillChoreography.parts(effect()).drop(1)) {
            val units=if(p.shape.contains("inner_"))3.4 else 5.225
            val distance=3.6*units/16
            assertEquals(kotlin.math.sin(p.yaw)*distance,p.offset.x(),1e-9)
            assertEquals(kotlin.math.cos(p.yaw)*distance,p.offset.z(),1e-9)
            assertEquals(.12,p.offset.y())
            assertTrue(p.ground && !p.followOwner)
        }
    }

    @Test fun `large crowns disappear before short roots without spawning another impact`() {
        val parts=CoreSkillChoreography.parts(effect())
        val inner=parts.filter { it.shape.contains("inner_") }
        val outer=parts.filter { it.shape.contains("outer_") }
        fun end(p:CoreCombatMeshPart)=p.delayTicks+p.durationTicks
        assertTrue(outer.maxOf(::end)<inner.minOf(::end))
        assertTrue(inner.maxOf(::end)<end(parts.first()))
        val age=outer.maxOf(::end).toDouble()
        assertTrue(outer.all { !CoreSkillChoreography.pose(it,age).visible })
        assertTrue(CoreSkillChoreography.pose(parts.first(),age).visible)
        assertEquals(7,parts.size)
    }
}
