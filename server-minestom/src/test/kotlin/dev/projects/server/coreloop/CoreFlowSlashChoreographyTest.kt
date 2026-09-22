package dev.projects.server.coreloop

import com.google.gson.Gson
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.MetadataHolder
import net.minestom.server.entity.MetadataDef
import java.util.function.Consumer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.*
import kotlin.test.*

class CoreFlowSlashChoreographyTest {
    @Test fun `installed Minestom resends equal interpolation starts instead of suppressing them`() {
        var updates=0
        val holder=MetadataHolder(Consumer { updates++ })
        holder.set(MetadataDef.Display.INTERPOLATION_DELAY,0)
        holder.set(MetadataDef.Display.INTERPOLATION_DELAY,0)
        assertEquals(2,updates,"Every tick must restart the vanilla transform interpolation")
    }
    private val ids=listOf("dash","war_wound","war_counter","slam","whirl","ass_execute","ass_fan")
    private fun legacyParts(e: CoreSkillEffect)=CoreExpandedSlashChoreography.parts(e) ?: CoreGreatswordSweepChoreography.parts(e)!!
    private fun effect(id: String,phase: CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE,pulse: Int=0): CoreSkillEffect {
        val job=if(id.startsWith("ass_")) CoreClass.ASSASSIN else CoreClass.WARRIOR
        val skill=CoreSkillCatalog.skills(job).first { it.icon==id }
        return CoreSkillEffect(job,skill,Vec.ZERO,Vec(0.0,0.0,1.0),phase,pulse,prepareTicks=skill.startup)
    }
    private fun quaternion(p: CoreMeshPose)=CoreCombatMeshArt.rotation(p.yaw,p.pitch,p.roll).map { it.toDouble() }
    @Test fun `export actual flow transform targets including hidden spawn and drain ticks`() {
        fun xyz(v: Vec)=listOf(v.x(),v.y(),v.z())
        val rows=ids.flatMap { id -> CoreSkillChoreography.parts(effect(id)).map { part ->
            val targets=(-2 until CoreCombatMeshes.removalAge(part)).map { age ->
                if(age>=part.delayTicks+part.durationTicks) null else {
                    val pose=CoreSkillChoreography.pose(part,age.toDouble())
                    mapOf("model" to pose.model,"translation" to xyz(pose.offset),
                        "scale" to xyz(if(pose.visible) pose.scale else Vec.ZERO),"rotation" to quaternion(pose))
                }
            }
            mapOf("skill" to id,"interpolation" to CoreCombatMeshes.interpolationTicks(part),"targets" to targets)
        } }
        val cwd=Path.of(System.getProperty("user.dir"));val root=if(cwd.fileName.toString()=="server-minestom") cwd.parent else cwd
        Files.createDirectories(root.resolve(".tools"))
        Files.writeString(root.resolve(".tools/flow-display-contract.json"),Gson().toJson(rows))
    }
    private fun dot(a: List<Double>,b: List<Double>)=a.indices.sumOf { a[it]*b[it] }
    private fun slerp(a: List<Double>,raw: List<Double>,t: Double): List<Double> {
        val b=if(dot(a,raw)<0) raw.map { -it } else raw
        val cosine=dot(a,b).coerceIn(-1.0,1.0)
        val angle=acos(cosine)
        val left=if(cosine>.9995) 1-t else sin((1-t)*angle)/sin(angle)
        val right=if(cosine>.9995) t else sin(t*angle)/sin(angle)
        val q=a.indices.map { a[it]*left+b[it]*right };val norm=sqrt(dot(q,q))
        return q.map { it/norm }
    }

    @Test fun `short segments keep their model while moving continuously between server ticks`() {
        for(id in ids) for(p in legacyParts(effect(id))) {
            val poses=(0..p.durationTicks*10).map { CoreSkillChoreography.pose(p,it/10.0) }
            assertEquals(1,poses.map { it.model }.distinct().size,id)
            assertTrue(poses.map { it.offset }.distinct().size>5,id)
            for((a,b) in poses.zipWithNext()) {
                assertTrue(a.offset.distance(b.offset)<.4,"$id teleport ${a.offset.distance(b.offset)}")
                assertTrue(abs(a.scale.x()-b.scale.x())<.25,"$id width snap")
                if(min(a.scale.z(),b.scale.z())>.04) {
                    val qa=quaternion(a);val qb=quaternion(b)
                    assertTrue(abs(dot(qa,qb))>.95,"$id orientation discontinuity")
                }
            }
            val end=poses.last()
            assertTrue(end.scale.x()*end.scale.z()<1e-8,"$id shrinks before despawn")
        }
    }

    @Test fun `primary joints fit observer budget and previous pulses expire before next hit`() {
        for(id in ids) {
            val e=effect(id);val p=legacyParts(e)
            assertTrue(p.count { !it.secondary }<=8)
            assertTrue(p.all { it.delayTicks==0 && !it.followOwner && !it.sprite })
            assertTrue(p.filter { !it.secondary }.all { it.durationTicks<=8 })
            for(t in 0..50) {
                val live=(0 until e.skill.pulses).sumOf { pulse -> p.count { CoreSkillChoreography.pose(it,t-pulse*8.0).visible } }
                assertTrue(live<=16,"$id live displays=$live")
            }
        }
    }

    // Idealised authoring-only export, NOT a Vanilla display or delivery simulation.
    // Real client interpolation is checked by CheckNativeDisplayInterpolation.java.
    @Test fun `export ideal authoring interpolation not proof of client smoothness`() {
        fun xyz(v: Vec)=listOf(v.x(),v.y(),v.z())
        val rows=ids.map { id ->
            val s=effect(id).skill
            val beats=listOf(0 to CoreSkillChoreography.parts(effect(id,CoreSkillVisualPhase.PREPARE)))+
                (0 until s.pulses).map { s.startup+it*8 to CoreSkillChoreography.parts(effect(id,pulse=it)) }
            val frames=(0..(s.startup+(s.pulses-1)*8+16)*3).map { frame ->
                val time=frame/3.0;val tick=floor(time);val fraction=time-tick
                beats.flatMap { (start,parts) -> parts.mapNotNull { p ->
                    val a=CoreSkillChoreography.pose(p,tick-start-1);val b=CoreSkillChoreography.pose(p,tick-start)
                    if(!a.visible && !b.visible) null else {
                        val scaleA=if(a.visible) a.scale else Vec.ZERO;val scaleB=if(b.visible) b.scale else Vec.ZERO
                        val q=slerp(quaternion(a),quaternion(b),fraction)
                        mapOf("model" to b.model,"offset" to xyz(a.offset.mul(1-fraction).add(b.offset.mul(fraction))),
                            "scale" to xyz(scaleA.mul(1-fraction).add(scaleB.mul(fraction))),
                            "quaternion" to q,"yaw" to b.yaw,"pitch" to b.pitch,"roll" to b.roll)
                    }
                } }
            }
            mapOf("id" to id,"name" to s.name,"frames" to frames)
        }
        val cwd=Path.of(System.getProperty("user.dir"));val root=if(cwd.fileName.toString()=="server-minestom") cwd.parent else cwd
        Files.createDirectories(root.resolve(".tools"))
        Files.writeString(root.resolve(".tools/flow-slash-ideal-authoring-60fps.json"),Gson().toJson(rows))
    }
}
