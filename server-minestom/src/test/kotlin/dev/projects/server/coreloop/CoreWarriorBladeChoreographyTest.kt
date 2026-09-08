package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import com.google.gson.Gson
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.*
import kotlin.test.*

class CoreWarriorBladeChoreographyTest {
    private fun effect(id: String,pulse: Int=0,heading: Double=0.0,phase: CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE): CoreSkillEffect {
        val skill=CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon==if(id.startsWith("normal_")) "dash" else id }
        return CoreSkillEffect(CoreClass.WARRIOR,skill.copy(radius=if(id.startsWith("normal_")) 3.9 else skill.radius),
            Vec.ZERO,Vec(sin(heading),0.0,cos(heading)),phase,pulse,sceneId=id,prepareTicks=skill.startup)
    }
    private fun parts(id: String,pulse: Int=0)=CoreSkillChoreography.parts(effect(id,pulse))
    @Test fun `warrior has one class route while other classes retain their existing routes`() {
        for(id in (CoreWarriorBladeChoreography.sceneIds - "dash")) {
            assertTrue(parts(id).all { it.shape.startsWith("warrior_trace:") },id)
            assertEquals(8,parts(id).size,id)
            assertTrue(parts(id).all { it.palette in setOf("warsteel","warred") && !it.sprite && !it.followOwner })
        }
        for(job in CoreClass.entries.filter { it!=CoreClass.WARRIOR }) for(skill in CoreSkillCatalog.skills(job)) {
            val e=CoreSkillEffect(job,skill,Vec.ZERO,Vec(0.0,0.0,1.0))
            assertNull(CoreWarriorBladeChoreography.parts(e))
            assertTrue(CoreSkillChoreography.parts(e).none { it.shape.startsWith("warrior_") })
        }
    }
    @Test fun `wake stays on the cut path with stable model and orientation and fades before removal`() {
        for(id in (CoreWarriorBladeChoreography.sceneIds - "dash")) for(p in parts(id)) {
            val poses=(0..32).map { CoreSkillChoreography.pose(p,p.delayTicks+it/4.0) }
            assertEquals(1,poses.map { it.model }.distinct().size,id)
            assertEquals(1,poses.map { it.offset }.distinct().size,id)
            assertEquals(1,poses.map { Triple(it.yaw,it.pitch,it.roll) }.distinct().size,id)
            assertEquals(0.0,poses.first().scale.x(),id)
            assertEquals(0.0,poses.last().scale.x(),id)
            assertTrue(poses.maxOf { it.scale.x() }>.1,id)
            assertFalse(CoreSkillChoreography.pose(p,p.delayTicks-1.0).visible)
            assertEquals(p.delayTicks+p.durationTicks+2,CoreCombatMeshes.removalAge(p))
            assertNotNull(javaClass.getResource("/core-ui-pack/assets/projects/items/${poses.first().model}.json"))
        }
    }
    @Test fun `normal combo reverses traversal then becomes a downstroke instead of replaying one stroke`() {
        val a=parts("normal_sweep");val b=parts("normal_reverse");val c=parts("normal_finish")
        assertTrue(a.first().offset.x()<a.last().offset.x())
        assertTrue(b.first().offset.x()>b.last().offset.x())
        assertTrue(c.first().offset.y()-c.last().offset.y()>1.0)
        assertTrue(c.first().scale.x()>a.first().scale.x())
    }
    @Test fun `spin is centered on the fighter and three beats have distinct silhouettes`() {
        val beats=(0..2).map { parts("whirl",it) }
        for(p in beats) {
            assertEquals(0.0,p.sumOf { it.offset.x() }/p.size,1e-7)
            assertEquals(0.0,p.sumOf { it.offset.z() }/p.size,1e-7)
            assertEquals(listOf(0,0,1,2,3,3,4,5),p.map { it.delayTicks })
        }
        assertEquals(3,beats.map { p -> p.map { it.offset to it.scale } }.distinct().size)
        assertTrue(beats[2].all { it.palette=="warred" })
        val ult=(0..2).map { parts("war_ult",it) }
        assertEquals(3,ult.map { p -> p.map { it.offset } }.distinct().size)
    }
    @Test fun `all enclosing model corners remain above ground and within reach at eight headings`() {
        for(id in (CoreWarriorBladeChoreography.sceneIds - "dash")) repeat(8) { heading ->
            val a=heading*PI/4
            for(pulse in 0 until effect(id).skill.pulses)
            for(p in CoreSkillChoreography.parts(effect(id,pulse,heading=a))) {
                val pose=CoreSkillChoreography.pose(p,p.delayTicks+1.0)
                for(x0 in listOf(-.5,.5)) for(z0 in listOf(-.5,.5)) {
                    val x=x0*pose.scale.x();val z=z0*pose.scale.z()
                    val y1=-z*sin(pose.pitch);val z1=z*cos(pose.pitch)
                    val x2=x*cos(pose.roll)-y1*sin(pose.roll);val y2=x*sin(pose.roll)+y1*cos(pose.roll)
                    val v=Vec(x2*cos(pose.yaw)+z1*sin(pose.yaw),y2,-x2*sin(pose.yaw)+z1*cos(pose.yaw)).add(pose.offset)
                    assertTrue(v.y()>.02,"$id buried $v")
                    assertTrue(hypot(v.x(),v.z())<=CoreSkillScenes.get(id).reach+.1,"$id reach $v")
                    if(id!="whirl") assertTrue(v.x()*sin(a)+v.z()*cos(a)>-.05,"$id behind $v")
                }
            }
        }
    }
    @Test fun `anticipation does not draw a complete second stroke and contact remains hit located`() {
        for(id in (CoreWarriorBladeChoreography.sceneIds - "dash")) {
            val prep=CoreSkillChoreography.parts(effect(id,phase=CoreSkillVisualPhase.PREPARE))
            assertEquals(listOf("warrior_charge"),prep.map { it.shape })
            assertTrue(prep.single().scale.x()<.2)
            val hit=CoreSkillChoreography.parts(effect(id,phase=CoreSkillVisualPhase.CONTACT)).single()
            assertEquals("warrior_impact",hit.shape)
            assertEquals(Vec(0.0,1.0,0.0),hit.offset)
        }
    }
    @Test fun `apex is a connected broad surface rather than a solitary white needle`() {
        for(id in (CoreWarriorBladeChoreography.sceneIds - "dash")) {
            val p=parts(id)
            val apex=(1..8).maxOf { t -> p.count {
                val pose=CoreSkillChoreography.pose(it,t.toDouble())
                pose.visible && pose.scale.x()>=it.scale.x()*.8
            } }
            assertTrue(apex>=6,"$id only $apex sections coexist")
            assertTrue(p.all { it.scale.x()>=.7 },"$id reverted to sub-block needles")
            // Every main face uses coarse class art, never the smooth shared flow strip.
            assertTrue(p.all { CoreSkillChoreography.pose(it,3.0).model.startsWith("combat_vfx/warrior_blade/") })
        }
    }
    @Test fun `export every warrior attack and pulse as server targets for the native display check`() {
        fun xyz(v: Vec)=listOf(v.x(),v.y(),v.z())
        val rows=(CoreWarriorBladeChoreography.sceneIds - "dash").flatMap { id ->
            val pulses=if(id.startsWith("normal_")) 1 else effect(id).skill.pulses
            (0 until pulses).flatMap { pulse -> parts(id,pulse).map { part ->
                val targets=(-2 until CoreCombatMeshes.removalAge(part)).map { age ->
                    if(age>=part.delayTicks+part.durationTicks) null else {
                        val p=CoreSkillChoreography.pose(part,age.toDouble())
                        mapOf("model" to p.model,"translation" to xyz(p.offset),
                            "scale" to xyz(if(p.visible) p.scale else Vec.ZERO),
                            "rotation" to CoreCombatMeshArt.rotation(p.yaw,p.pitch,p.roll).toList())
                    }
                }
                mapOf("skill" to id,"pulse" to pulse,"interpolation" to CoreCombatMeshes.interpolationTicks(part),"targets" to targets)
            } }
        }
        val cwd=Path.of(System.getProperty("user.dir"));val root=if(cwd.fileName.toString()=="server-minestom") cwd.parent else cwd
        Files.createDirectories(root.resolve(".tools"))
        Files.writeString(root.resolve(".tools/warrior-display-contract.json"),Gson().toJson(rows))
    }
}
