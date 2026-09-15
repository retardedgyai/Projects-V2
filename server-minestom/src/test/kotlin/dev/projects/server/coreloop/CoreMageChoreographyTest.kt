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
    @Test fun `meteor pressure opens on both sides of the landing with a lower rear face`() {
        val parts=CoreSkillChoreography.parts(effect("meteor")).filter {
            CoreMageChoreography.interpolated(it) && it.shape!="mage_material:meteor_front" }
        assertEquals(6,parts.size)
        assertTrue(parts.all { it.durationTicks==12 && it.ground })
        for(age in listOf(0.0,1.5,3.0)) {
            val poses=parts.map { CoreSkillChoreography.pose(it,age) }
            assertTrue(poses.take(3).all { it.offset.z()>0 })
            assertTrue(poses.drop(3).all { it.offset.z()<0 })
            assertTrue(poses.drop(3).all { it.scale.y()<poses.first().scale.y() })
        }
        for(p in parts) {
            assertEquals(Vec.ZERO,CoreSkillChoreography.pose(p,11.0).scale)
            assertFalse(CoreSkillChoreography.pose(p,12.0).visible)
            val rotation=(0..110).map { CoreSkillChoreography.pose(p,it/10.0).roll }
            assertTrue(rotation.all { it.isFinite() && kotlin.math.abs(it)<.5 })
            assertTrue(rotation.zipWithNext().all { (a,b)->kotlin.math.abs(a-b)<.05 })
        }
        // Heading changes rotate the depth composition, not only its textures.
        val turned=CoreSkillChoreography.parts(effect("meteor",direction=Vec(1.0,0.0,0.0)))
            .filter { CoreMageChoreography.interpolated(it) && it.shape!="mage_material:meteor_front" }
        for((p,q) in parts.zip(turned)) {
            val left=CoreSkillChoreography.pose(p,5.0).offset
            val right=CoreSkillChoreography.pose(q,5.0).offset
            assertEquals(left.z(),right.x(),1e-8)
            assertEquals(-left.x(),right.z(),1e-8)
            assertEquals(left.y(),right.y(),1e-8)
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
        val peak=CoreSkillChoreography.pose(parts.first(),4.0).scale.x()
        assertTrue(later.all { it.scale.x() in peak*.45..peak*.7 })
    }
    @Test fun `meteor cools through tearing drawings and remnants leave the arch footprint`() {
        val parts=CoreSkillChoreography.parts(effect("meteor")).filter {
            CoreMageChoreography.interpolated(it) && it.shape!="mage_material:meteor_front" }
        for(p in parts) {
            val ages=listOf(3.0,4.0,5.0,6.0,7.0,9.0)
            assertEquals((0..5).toList(),ages.map {
                CoreSkillChoreography.pose(p,it+CoreMageChoreography.meteorCoolingDelay(p.shape))
                    .model.substringAfterLast('_').toInt() })
            val a=CoreSkillChoreography.pose(p,7.0)
            val b=CoreSkillChoreography.pose(p,9.0)
            assertTrue(b.offset.y()>a.offset.y(),p.shape)
            assertTrue(b.offset.distance(a.offset)>.2,p.shape)
            val dense=(0..109).map { CoreSkillChoreography.pose(p,it/10.0) }
            assertTrue(dense.zipWithNext().all { (x,y)->x.offset.distance(y.offset)<.16 })
        }
        val cooling=parts.map { CoreSkillChoreography.pose(it,5.0).model.substringAfterLast('_').toInt() }
        assertTrue(2 in cooling && cooling.any { it<2 })
    }
    @Test fun `persistent field preparations do not spawn more complete fields between damage beats`() {
        for(id in listOf("mage_garden","mage_ult","mage_zero")) {
            val e=effect(id)
            val parts=CoreSkillChoreography.parts(e)
            assertEquals(48,parts.maxOf { it.delayTicks+it.durationTicks })
            for(pulse in 1 until e.skill.pulses) {
                val beat=CoreSkillChoreography.parts(effect(id,pulse))
                assertEquals(if(id=="mage_garden")0 else 1,beat.size,id)
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
        assertEquals(6,garden.size);assertEquals(setOf(0,1,3,5),garden.map { it.delayTicks }.toSet())
        assertTrue(garden.all { it.ground && it.offset.y()==.12 })
        assertTrue(garden.none { it.followOwner || it.spin!=0.0 })
        assertTrue(garden.all { it.shape.startsWith("mage_material:cryo_") })
        assertEquals(6,garden.count { !it.secondary })
        assertTrue(CoreSkillChoreography.parts(effect("mage_garden",phase=CoreSkillVisualPhase.PREPARE))
            .all { it.shape=="mage_material:cryo_seed" })
        val ward=CoreSkillChoreography.parts(effect("mage_ward"))
        assertEquals(4,ward.size)
        assertTrue(ward.all { it.followOwner && !it.ground })
        assertTrue(ward.all { kotlin.math.abs(it.offset.x())>it.scale.x()*.5+.2 })
    }
    @Test fun `elemental accepted contact stays small and never starts another cast silhouette`() {
        val clips=mutableSetOf<String>()
        for(id in CoreMageChoreography.sceneIds) {
            if(id=="firebolt") {
                val chips=CoreSkillChoreography.parts(effect(id,phase=CoreSkillVisualPhase.CONTACT))
                assertEquals(3,chips.size)
                assertTrue(chips.all { it.shape.endsWith("solar_bolt_chip") && it.scale.x()<=.22 && !it.followOwner })
                continue
            }
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
