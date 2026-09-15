package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.test.*

class CoreMageChoreographyTest {
    private fun effect(id:String,pulse:Int=0,phase:CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE)=
        CoreSkillEffect(CoreClass.MAGE,CoreSkillCatalog.skills(CoreClass.MAGE).first { it.icon==id },
            Vec.ZERO,Vec(0.0,0.0,1.0),phase,pulse=pulse,rayLength=6.0,clippedRay=id in setOf("firebolt","mage_mark"))

    @Test fun `all ten mage skills use dedicated primary silhouettes not slash recolors`() {
        val identities=mutableSetOf<Set<String>>()
        for(id in CoreMageChoreography.sceneIds) {
            val parts=CoreSkillChoreography.parts(effect(id))
            assertTrue(parts.isNotEmpty(),id)
            assertTrue(parts.all { CoreMageChoreography.owns(it) && !it.sprite && it.spin==0.0 },id)
            assertTrue(parts.count { !it.secondary }<=8,id)
            identities+=parts.filter { !it.secondary }.map { it.shape }.toSet()
            assertTrue(parts.all { CoreCombatMeshes.interpolationTicks(it)==0 },id)
        }
        assertEquals(10,identities.size)
    }
    @Test fun `persistent field preparations do not spawn more complete fields between damage beats`() {
        for(id in listOf("mage_garden","mage_ult","mage_zero")) {
            val e=effect(id)
            val parts=CoreSkillChoreography.parts(e)
            assertEquals(48,parts.maxOf { it.delayTicks+it.durationTicks })
            for(pulse in 1 until e.skill.pulses) {
                val beat=CoreSkillChoreography.parts(effect(id,pulse))
                assertEquals(1,beat.size,id)
                assertTrue(beat.all { it.secondary && it.durationTicks<=12 },id)
                if(id!="mage_zero") assertTrue(CoreSkillChoreography.parts(effect(id,pulse,CoreSkillVisualPhase.PREPARE)).isEmpty(),id)
            }
            assertEquals(id=="mage_zero",parts.all { it.followOwner },id)
        }
    }
    @Test fun `meteor has a falling anticipation and independent landing at each authoritative pulse`() {
        for(pulse in 0..2) {
            val parts=CoreSkillChoreography.parts(effect("meteor",pulse,CoreSkillVisualPhase.PREPARE))
            val rock=parts.first()
            assertEquals("mage_material:meteor",rock.shape)
            assertTrue(CoreSkillChoreography.pose(rock,rock.durationTicks-1.0).offset.y()<rock.offset.y()-3.0)
            val land=CoreSkillChoreography.parts(effect("meteor",pulse)).first()
            assertEquals(rock.offset.x(),land.offset.x());assertEquals(rock.offset.z(),land.offset.z())
            assertEquals("mage_material:eruption",land.shape)
        }
    }
    @Test fun `garden has staggered upright roots while ward leaves the aim corridor open`() {
        val garden=CoreSkillChoreography.parts(effect("mage_garden"))
        assertEquals(4,garden.size);assertEquals(setOf(0,2,4),garden.map { it.delayTicks }.toSet())
        assertTrue(garden.all { it.ground && it.offset.y()==.12 })
        assertTrue(garden.none { it.followOwner || it.spin!=0.0 })
        val ward=CoreSkillChoreography.parts(effect("mage_ward"))
        assertEquals(4,ward.size)
        assertTrue(ward.all { it.followOwner && !it.ground })
        assertTrue(ward.all { kotlin.math.abs(it.offset.x())>it.scale.x()*.5+.2 })
    }
    @Test fun `elemental accepted contact stays small and never starts another cast silhouette`() {
        val clips=mutableSetOf<String>()
        for(id in CoreMageChoreography.sceneIds) {
            val p=CoreSkillChoreography.parts(effect(id,phase=CoreSkillVisualPhase.CONTACT)).single()
            assertTrue(p.shape.endsWith("_hit"));assertTrue(p.scale.x()<1.5)
            assertFalse(p.followOwner);clips+=p.shape
        }
        assertEquals(3,clips.size)
    }
    @Test fun `other classes never route through mage materials`() {
        for(job in CoreClass.entries.filter { it!=CoreClass.MAGE }) for(skill in CoreSkillCatalog.skills(job)) {
            assertNull(CoreMageChoreography.parts(CoreSkillEffect(job,skill,Vec.ZERO,Vec(0.0,0.0,1.0))))
        }
    }
}
