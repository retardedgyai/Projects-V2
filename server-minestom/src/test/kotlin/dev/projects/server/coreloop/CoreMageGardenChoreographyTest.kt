package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.test.*

class CoreMageGardenChoreographyTest {
    private fun parts()=CoreSkillChoreography.parts(CoreSkillEffect(CoreClass.MAGE,
        CoreSkillCatalog.skills(CoreClass.MAGE).first { it.icon=="mage_garden" },Vec.ZERO,Vec(0.0,0.0,1.0)))

    @Test fun `root silhouette precedes one dominant shaft and unequal companion`() {
        val parts=parts()
        val roots=parts.filter { it.shape.endsWith(":cryo_root") }
        val pillars=parts.filter { it.shape.endsWith(":cryo_pillar") }
        assertEquals(4,roots.size)
        assertEquals(2,pillars.size)
        assertTrue(roots.maxOf { it.delayTicks }<pillars.minOf { it.delayTicks })
        assertTrue(pillars.maxOf { it.scale.y() }/pillars.minOf { it.scale.y() }>1.4)
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
            assertEquals(p.offset,formed.offset)
            assertTrue(pose(0.0).scale.y()<p.scale.y()*.03)
            val all=(0 until p.durationTicks*10).map { pose(it/10.0) }
            assertEquals(1,all.map { it.model }.distinct().size)
            assertTrue(all.zipWithNext().all { (a,b)->a.offset.distance(b.offset)<.09 })
            val last=pose(p.durationTicks-1.0)
            val height=if(p.shape.endsWith(":cryo_root")) .75 else 1.5
            assertTrue(last.offset.y()+last.scale.y()*height<p.offset.y()-.1)
            assertEquals(p.scale,last.scale) // sink; do not squash the painted shaft
            assertFalse(pose(p.durationTicks.toDouble()).visible)
        }
    }
}
