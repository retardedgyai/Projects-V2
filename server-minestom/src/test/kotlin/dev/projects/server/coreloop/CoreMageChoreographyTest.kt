package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.test.*

class CoreMageChoreographyTest {
    private fun effect(id:String,pulse:Int=0,phase:CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE,direction:Vec=Vec(0.0,0.0,1.0))=
        CoreSkillEffect(CoreClass.MAGE,CoreSkillCatalog.skills(CoreClass.MAGE).first { it.icon==id },
            Vec.ZERO,direction,phase,pulse=pulse,rayLength=6.0,clippedRay=id in setOf("firebolt","mage_mark"))

    @Test fun `all ten mage skills use dedicated primary silhouettes not slash recolors`() {
        val identities=mutableSetOf<Set<String>>()
        for(id in CoreMageChoreography.sceneIds) {
            val parts=CoreSkillChoreography.parts(effect(id))
            assertTrue(parts.isNotEmpty(),id)
            assertTrue(parts.all { CoreMageChoreography.owns(it) && !it.sprite && it.spin==0.0 },id)
            assertTrue(parts.count { !it.secondary }<=8,id)
            identities+=parts.filter { !it.secondary }.map { it.shape }.toSet()
            assertTrue(parts.all { CoreCombatMeshes.interpolationTicks(it)==if(CoreMageChoreography.interpolated(it))1 else 0 },id)
        }
        assertEquals(10,identities.size)
    }
    @Test fun `meteor flame masses keep shape identity through opening and collapse before removal`() {
        for(pulse in 0..2) {
            val parts=CoreSkillChoreography.parts(effect("meteor",pulse))
            val flow=parts.filter(CoreMageChoreography::interpolated)
            assertEquals(7,flow.size)
            assertEquals(8,parts.count { !it.secondary })
            for(p in flow) {
                assertTrue(p.ground && !p.followOwner && p.delayTicks==0)
                assertEquals(1,CoreCombatMeshes.interpolationTicks(p))
                val poses=(0..35).map { CoreSkillChoreography.pose(p,it/10.0) }
                assertEquals(1,poses.map { it.model }.distinct().size)
                for((a,b) in poses.zipWithNext()) {
                    assertTrue(a.offset.distance(b.offset)<.13)
                    assertTrue(a.scale.distance(b.scale)<.36)
                }
                assertTrue(poses.first().scale.x()>=p.scale.x()*.5)
                assertTrue(poses.first().scale.y()>=p.scale.y()*.38)
                val end=CoreSkillChoreography.pose(p,p.durationTicks-1.0)
                assertEquals(Vec.ZERO,end.scale)
                assertFalse(CoreSkillChoreography.pose(p,p.durationTicks.toDouble()).visible)
            }
            val a=CoreSkillChoreography.parts(effect("meteor",pulse,direction=Vec(1.0,0.0,0.0)))
                .filter(CoreMageChoreography::interpolated)
            for((p,turned) in flow.zip(a)) {
                assertEquals(p.travel.x(),-turned.travel.z(),1e-8)
                assertEquals(p.travel.z(),turned.travel.x(),1e-8)
                assertEquals(p.offset,turned.offset) // pulse's authoritative landing stays fixed
            }
        }
    }
    @Test fun `meteor pressure unfolds in three dimensions with distinct lobe roles`() {
        val parts=CoreSkillChoreography.parts(effect("meteor")).filter { it.shape.startsWith("mage_material:meteor_flow_") }
        assertEquals(4,parts.size)
        assertTrue(parts.map { it.travel.z() }.max()-parts.map { it.travel.z() }.min()>.8)
        assertEquals(listOf(6,12,6,12),parts.map { it.durationTicks })
        for(i in listOf(0,2)) {
            // Low pressure ends before the ash phase; no two persistent grey
            // wings remain under the later rising flame/debris.
            assertEquals(Vec.ZERO,CoreSkillChoreography.pose(parts[i],5.0).scale)
            assertFalse(CoreSkillChoreography.pose(parts[i],6.0).visible)
        }
        val poses=parts.map { CoreSkillChoreography.pose(it,3.0) }
        // The main arch stays rooted instead of lifting a central flame ball.
        val centre=CoreMageChoreography.meteorFragmentCenters.first().div(16.0).mul(poses[1].scale)
        assertTrue(poses[1].offset.sub(centre).y()<.3)
        assertTrue(poses[1].scale.x()>CoreSkillChoreography.pose(parts[1],0.0).scale.x()*1.7)
        assertTrue(poses[3].scale.y()<poses[1].scale.y()*.75)
        assertTrue(poses[0].offset.x()>0 && poses[2].offset.x()<0)
        for(p in parts) {
            val rotation=(0..110).map { CoreSkillChoreography.pose(p,it/10.0).roll }
            assertTrue(rotation.all { it.isFinite() && kotlin.math.abs(it)<.5 })
            assertTrue(rotation.zipWithNext().all { (a,b)->kotlin.math.abs(a-b)<.05 })
        }
    }
    @Test fun `meteor pieces retain one connected frame before peeling around their own centres`() {
        val parts=CoreSkillChoreography.parts(effect("meteor")).filter {
            it.shape=="mage_material:meteor_flow_1" || it.shape.startsWith("mage_material:meteor_break_") }
        assertEquals(3,parts.size)
        for(age in listOf(0.0,1.5,3.0)) {
            val poses=parts.map { CoreSkillChoreography.pose(it,age) }
            assertEquals(1,poses.map { it.scale }.distinct().size)
            assertEquals(1,poses.map { it.roll }.distinct().size)
            val recovered=poses.mapIndexed { i,p ->
                val v=CoreMageChoreography.meteorFragmentCenters[i].div(16.0).mul(p.scale)
                val c=Vec(v.x()*kotlin.math.cos(p.roll)-v.y()*kotlin.math.sin(p.roll),
                    v.x()*kotlin.math.sin(p.roll)+v.y()*kotlin.math.cos(p.roll),v.z())
                p.offset.sub(c)
            }
            assertTrue(recovered.all { it.distance(recovered.first())<1e-8 })
        }
        val later=parts.map { CoreSkillChoreography.pose(it,8.0) }
        assertTrue(later.map { it.roll }.distinct().size==3)
        assertTrue(later[0].offset.distance(later[1].offset)>1.0)
        assertTrue(later.all { it.scale.x()<CoreSkillChoreography.pose(parts.first(),4.0).scale.x()*.4 })
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
            assertEquals(1,parts.size) // no unrelated rotating fire-charge beneath the rock
            assertEquals("mage_material:meteor",rock.shape)
            assertTrue(CoreSkillChoreography.pose(rock,rock.durationTicks-1.0).offset.y()<rock.offset.y()-3.0)
            val last=CoreSkillChoreography.pose(rock,rock.durationTicks-1.0)
            assertTrue(last.model.endsWith("meteor_22"))
            val land=CoreSkillChoreography.parts(effect("meteor",pulse)).first()
            assertEquals(rock.offset.x(),land.offset.x());assertEquals(rock.offset.z(),land.offset.z())
            assertEquals("mage_material:eruption",land.shape)
            val wake=CoreSkillChoreography.parts(effect("meteor",pulse))[1]
            assertEquals("mage_material:meteor_ring",wake.shape)
            assertTrue(wake.secondary && wake.ground)
            assertEquals(land.offset.x(),wake.offset.x());assertEquals(land.offset.z(),wake.offset.z())
            assertTrue(wake.scale.x()>land.scale.x())
        }
    }
    @Test fun `garden has staggered upright roots while ward leaves the aim corridor open`() {
        val garden=CoreSkillChoreography.parts(effect("mage_garden"))
        assertEquals(5,garden.size);assertEquals(setOf(0,2,4),garden.map { it.delayTicks }.toSet())
        assertTrue(garden.all { it.ground && it.offset.y()==.12 })
        assertTrue(garden.none { it.followOwner || it.spin!=0.0 })
        assertTrue(garden.all { it.shape.startsWith("mage_material:garden_") })
        assertEquals(4,garden.count { !it.secondary })
        assertTrue(CoreSkillChoreography.parts(effect("mage_garden",phase=CoreSkillVisualPhase.PREPARE))
            .all { it.shape=="mage_material:garden_charge" })
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
