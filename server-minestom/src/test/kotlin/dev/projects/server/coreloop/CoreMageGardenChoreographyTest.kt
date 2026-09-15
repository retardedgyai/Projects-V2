package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.test.*

class CoreMageGardenChoreographyTest {
    private fun parts()=CoreSkillChoreography.parts(CoreSkillEffect(CoreClass.MAGE,
        CoreSkillCatalog.skills(CoreClass.MAGE).first { it.icon=="mage_garden" },Vec.ZERO,Vec(0.0,0.0,1.0)))

    @Test fun `root silhouette precedes one dominant shaft and unequal companion`() {
        val parts=parts()
        val roots=parts.filter { it.shape.endsWith(":cryo_root") || it.shape.endsWith(":cryo_buttress") }
        val pillars=parts.filter { it.shape.endsWith(":cryo_pillar") || it.shape.endsWith(":cryo_crown") }
        assertEquals(4,roots.size)
        assertEquals(2,pillars.size)
        assertTrue(roots.maxOf { it.delayTicks }<pillars.minOf { it.delayTicks })
        val heights=pillars.map { it.scale.y()*CoreMageChoreography.rootedIceHeight(it.shape) }
        assertTrue(heights.max()/heights.min()>1.4)
        assertEquals(2,pillars.map { it.shape }.distinct().size)
        assertEquals(2,roots.count { it.shape.endsWith(":cryo_buttress") })
        assertTrue(parts.all { CoreCombatMeshes.interpolationTicks(it)==1 && it.ground })
        assertTrue(parts.none { it.secondary || it.followOwner || it.spin!=0.0 })
        assertEquals(48,parts.maxOf { it.durationTicks+it.delayTicks })
    }

    @Test fun `each shaft retains its model and holds still before withdrawing below ground`() {
        for(p in parts()) {
            fun pose(local:Double)=CoreMageChoreography.pose(p,local+p.delayTicks)!!
            val formed=pose(7.0)
            assertEquals(formed,pose(20.0))
            assertEquals(p.scale,formed.scale)
            assertEquals(p.offset.add(0.0,-.14,0.0),formed.offset)
            assertTrue(pose(0.0).scale.y()<p.scale.y()*.03)
            val all=(0 until p.durationTicks*10).map { pose(it/10.0) }
            assertEquals(1,all.map { it.model }.distinct().size)
            assertTrue(all.zipWithNext().all { (a,b)->a.offset.distance(b.offset)<.09 })
            val last=pose(p.durationTicks-1.0)
            val height=CoreMageChoreography.rootedIceHeight(p.shape)
            assertTrue(last.offset.y()+last.scale.y()*height<p.offset.y()-.1)
            assertEquals(p.scale,last.scale) // sink; do not squash the painted shaft
            assertFalse(pose(p.durationTicks.toDouble()).visible)
        }
    }

    @Test fun `anticipation uses low blue roots and later pulses do not repeat a large animation`() {
        val skill=CoreSkillCatalog.skills(CoreClass.MAGE).first { it.icon=="mage_garden" }
        val prepare=CoreSkillChoreography.parts(CoreSkillEffect(CoreClass.MAGE,skill,Vec.ZERO,
            Vec(0.0,0.0,1.0),CoreSkillVisualPhase.PREPARE,prepareTicks=7))
        assertEquals(2,prepare.size)
        for(p in prepare) {
            assertEquals("mage_material:cryo_seed",p.shape)
            assertTrue(p.scale.y()*.3125<.15)
            assertEquals(Vec.ZERO,CoreSkillChoreography.pose(p,p.durationTicks-1.0).scale)
        }
        for(pulse in 1 until skill.pulses) {
            assertTrue(CoreSkillChoreography.parts(CoreSkillEffect(CoreClass.MAGE,skill,Vec.ZERO,
                Vec(0.0,0.0,1.0),CoreSkillVisualPhase.PULSE,pulse=pulse)).isEmpty())
        }
    }
}
