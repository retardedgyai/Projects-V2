package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.test.*

class CoreMageFireboltChoreographyTest {
    private fun effect(length:Double=6.0,direction:Vec=Vec(0.0,0.0,1.0),
                       phase:CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE,prepare:Int=4)=
        CoreSkillEffect(CoreClass.MAGE,CoreSkillCatalog.skills(CoreClass.MAGE).first { it.icon=="firebolt" },
            Vec.ZERO,direction,phase,rayLength=length,clippedRay=true,prepareTicks=prepare)

    @Test fun `ray has separate compact front rear shell and narrow wakes immediately`() {
        val parts=CoreSkillChoreography.parts(effect())
        assertEquals(listOf("core","shell","wake","thread"),parts.map { it.shape.substringAfter("solar_bolt_") })
        assertTrue(parts.all { CoreMageFireboltChoreography.owns(it) && !it.followOwner && !it.ground })
        assertTrue(parts.all { CoreSkillChoreography.pose(it,0.0).scale.lengthSquared()>0 })
        assertEquals(.85,parts.first().scale.z())
        assertEquals(1.15,parts.first().scale.x()) // actual mesh width is 10.2 / 16 of this
        assertTrue(parts.none { it.shape.contains("hit") || it.shape.contains("chip") })
    }

    @Test fun `entire longitudinal envelope stays within clipped ray at arbitrary pitch and heading`() {
        for(length in listOf(.051,.2,1.0,6.0,24.0)) for(direction in listOf(
            Vec(0.0,0.0,1.0),Vec(1.0,0.0,0.0),Vec(.3,.8,-.2).normalize(),Vec(0.0,-1.0,0.0))) {
            for(p in CoreSkillChoreography.parts(effect(length,direction))) for(i in 0..90) {
                val pose=CoreSkillChoreography.pose(p,i/10.0)
                val axial=pose.offset.dot(direction)
                assertTrue(axial-pose.scale.z()/2>=-1e-8,"${p.shape}: starts behind caster")
                assertTrue(axial+pose.scale.z()/2<=length+1e-8,"${p.shape}: crosses clipped endpoint")
            }
        }
        assertTrue(CoreSkillChoreography.parts(effect(.01)).isEmpty())
    }

    @Test fun `native interpolated displays keep immutable models and reach zero before removal`() {
        for(phase in CoreSkillVisualPhase.entries) for(p in CoreSkillChoreography.parts(effect(phase=phase))) {
            assertEquals(1,CoreCombatMeshes.interpolationTicks(p))
            val poses=(0..p.durationTicks*10).map { CoreSkillChoreography.pose(p,it/10.0) }
            assertEquals(1,poses.map { it.model }.distinct().size)
            assertTrue(poses.all { it.roll==p.roll && it.yaw==p.yaw && it.pitch==p.pitch })
            assertEquals(Vec.ZERO,CoreSkillChoreography.pose(p,p.durationTicks-1.0).scale)
            assertFalse(poses.last().visible)
        }
    }

    @Test fun `wake withdraws forward while shell outlasts the brighter core`() {
        val parts=CoreSkillChoreography.parts(effect())
        assertTrue(parts[0].durationTicks<parts[1].durationTicks)
        for(p in parts.takeLast(2)) {
            val poses=(0..p.durationTicks*10).map { CoreSkillChoreography.pose(p,it/10.0) }
            assertTrue(poses.zipWithNext().all { (a,b)->b.offset.z()>=a.offset.z() && b.scale.z()<=a.scale.z() })
        }
    }

    @Test fun `confirmed hit creates small diverging chips instead of another projectile or explosion`() {
        val parts=CoreSkillChoreography.parts(effect(phase=CoreSkillVisualPhase.CONTACT))
        assertEquals(3,parts.size)
        assertTrue(parts.all { it.shape.endsWith("solar_bolt_chip") && it.scale.x()<=.22 && !it.followOwner })
        assertEquals(3,parts.map { CoreSkillChoreography.pose(it,3.0).offset }.distinct().size)
    }

    @Test fun `short and long prepare clocks stay finite and close cleanly`() {
        for(ticks in listOf(1,2,4,20,60)) {
            val p=CoreSkillChoreography.parts(effect(phase=CoreSkillVisualPhase.PREPARE,prepare=ticks)).single()
            assertTrue(p.followOwner)
            assertEquals(Vec.ZERO,CoreSkillChoreography.pose(p,p.durationTicks-1.0).scale)
        }
    }
}
