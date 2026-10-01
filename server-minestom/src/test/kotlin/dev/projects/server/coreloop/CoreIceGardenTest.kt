package dev.projects.server.coreloop

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.test.*
import kotlin.math.*

class CoreIceGardenTest {
    @Test fun `visible plate footprint agrees with height and stepped corner exclusions`() {
        val cells=CoreIceGarden.offsets.map { (x,z)->Pos(x*1.25,40.0,z*1.25) }
        val field=CoreIceGarden(cells,6,120)
        assertEquals(21,cells.size)
        assertTrue(field.contains(Pos(0.0,40.0,0.0)))
        assertTrue(field.contains(Pos(3.12,40.0,1.87)))
        assertFalse(field.contains(Pos(2.5,40.0,2.5)))
        assertFalse(field.contains(Pos(0.0,41.0,0.0)))
        assertFalse(field.contains(Pos(3.2,40.0,0.0)))
    }
    @Test fun `late arrivals and repeated reentry cannot reset four hit budget or lifetime`() {
        val field=CoreIceGarden(listOf(Pos.ZERO),6,120); val id=UUID.randomUUID()
        assertFalse(field.claimHit(id,5))
        assertTrue(field.claimHit(id,40)); assertTrue(field.firstHit(id))
        assertFalse(field.claimHit(id,41))
        for(t in listOf(60L,80L,100L)) assertTrue(field.claimHit(id,t))
        assertFalse(field.claimHit(id,120)); assertFalse(field.claimHit(UUID.randomUUID(),126))
        assertEquals(126,field.endsAt)
    }
    @Test fun `target accounting is bounded even for a large wave of unique enemies`() {
        val field=CoreIceGarden(listOf(Pos.ZERO),0,120)
        assertEquals(128,(0..511).count { field.claimHit(UUID.randomUUID(),0) })
    }
    @Test fun `opening is immediate and held field has no restart at former eight tick waves`() {
        val skill=CoreSkillCatalog.skills(CoreClass.MAGE).first { it.icon=="mage_garden" }
        val effect=CoreSkillEffect(CoreClass.MAGE,skill,Pos.ZERO,Vec(0.0,0.0,1.0))
        val parts=CoreSkillChoreography.parts(effect)
        assertEquals(21,parts.size)
        for(p in parts) {
            val held=CoreSkillChoreography.pose(p,8.0)
            assertTrue(held.visible)
            for(t in listOf(16.0,24.0,40.0,80.0)) assertEquals(held,CoreSkillChoreography.pose(p,t))
            assertTrue(CoreSkillChoreography.pose(p,100.0).model.endsWith("_3"))
            assertTrue(CoreSkillChoreography.pose(p,120.0).model.endsWith("_4"))
            assertFalse(CoreSkillChoreography.pose(p,130.0).visible)
        }
        assertEquals(130,effect.durationTicks)
    }
    @Test fun `radius choices widen both the native plates and server footprint with the same factor`() {
        val skill=CoreSkillCatalog.skills(CoreClass.MAGE).first { it.icon=="mage_garden" }.copy(radius=5.15)
        val effect=CoreSkillEffect(CoreClass.MAGE,skill,Pos.ZERO,Vec(0.0,0.0,1.0))
        val parts=CoreSkillChoreography.parts(effect)
        val cellSize=CoreIceGarden.cellSize(skill.radius)
        assertTrue(cellSize>1.7)
        val field=CoreIceGarden(parts.map { Pos(it.offset.x(),0.0,it.offset.z()) },0,120,cellSize)
        assertTrue(field.contains(Pos(4.0,0.0,0.0)))
        assertTrue(parts.all { it.scale.x()==cellSize && it.scale.z()==cellSize })
    }
    @Test fun `export native old main and new garden poses for a bounded comparison preview`() {
        val skill=CoreSkillCatalog.skills(CoreClass.MAGE).first { it.icon=="mage_garden" }
        val effect=CoreSkillEffect(CoreClass.MAGE,skill,Pos.ZERO,Vec(0.0,0.0,1.0))
        val newParts=CoreSkillChoreography.parts(effect)
        // Exact former iceGarden body from main 7341fe25 (reach=3, life=32).
        fun oldParts(pulse: Int)=(0 until 8).map { i ->
            val a=i*PI/4+pulse*.2;val radius=3.0*if(i%2==0).58 else .3
            CoreCombatMeshPart("ice_growth","ice",Vec(sin(a)*radius,.12,cos(a)*radius),
                Vec(.8,.8,if(i%2==0)1.35 else .8),yaw=a,pitch=-PI/2,ground=true,
                startSize=.02,endSize=1.0,durationTicks=32-(i%4)*2,delayTicks=(i%4)*2,
                motion=CoreMeshMotion.EMERGE,secondary=i%2!=0)
        }
        fun record(p: CoreCombatMeshPart,age: Double): Map<String,Any>? {
            val pose=CoreSkillChoreography.pose(p,age);if(!pose.visible) return null
            return mapOf("model" to pose.model,"offset" to listOf(pose.offset.x(),pose.offset.y(),pose.offset.z()),
                "scale" to listOf(pose.scale.x(),pose.scale.y(),pose.scale.z()),"yaw" to pose.yaw,"pitch" to pose.pitch,"roll" to pose.roll)
        }
        val frames=(0..140).map { tick ->
            val old=(0..3).flatMap { pulse -> val age=tick-6-pulse*8
                if(age<0) emptyList() else oldParts(pulse).mapNotNull { record(it,age.toDouble()) } }
            val fresh=if(tick<6) CoreSkillChoreography.parts(CoreSkillEffect(CoreClass.MAGE,skill,Pos.ZERO,Vec(0.0,0.0,1.0),
                CoreSkillVisualPhase.PREPARE,prepareTicks=6)).mapNotNull { record(it,tick.toDouble()) }
                else newParts.mapNotNull { record(it,(tick-6).toDouble()) }
            mapOf("tick" to tick,"old" to old,"new" to fresh)
        }
        val cwd=java.nio.file.Path.of(System.getProperty("user.dir"));val root=if(cwd.fileName.toString()=="server-minestom")cwd.parent else cwd
        val path=root.resolve(".tools/ice-garden-poses.json");java.nio.file.Files.createDirectories(path.parent)
        val contactParts=CoreSkillChoreography.parts(CoreSkillEffect(CoreClass.MAGE,skill,Pos.ZERO,Vec(0.0,0.0,1.0),CoreSkillVisualPhase.CONTACT))
        val contacts=(0..9).map { age -> contactParts.mapNotNull { record(it,age.toDouble()) } }
        java.nio.file.Files.writeString(path,com.google.gson.Gson().toJson(mapOf("base" to "7341fe25","fps" to 20,"frames" to frames,"contacts" to contacts)))
    }
}
