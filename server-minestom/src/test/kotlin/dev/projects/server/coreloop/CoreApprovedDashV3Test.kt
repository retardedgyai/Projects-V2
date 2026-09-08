package dev.projects.server.coreloop

import com.google.gson.Gson
import com.google.gson.JsonParser
import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.math.*

class CoreApprovedDashV3Test {
    private val skill=CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon=="dash" }
    private fun effect(phase: CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE,direction: Vec=Vec(0.0,0.0,1.0))=
        CoreSkillEffect(CoreClass.WARRIOR,skill,Vec.ZERO,direction,phase,prepareTicks=skill.startup)

    @Test fun `blade and aftermath are separate geometry sequences with no extra damage flashes`() {
        val parts=CoreSkillChoreography.parts(effect())
        assertEquals(listOf("approved_dash_blade","approved_dash_wake"),parts.map { it.shape })
        assertFalse(parts.first().secondary)
        assertTrue(parts.last().secondary)
        for(p in parts) {
            val poses=(0 until p.durationTicks).map { CoreSkillChoreography.pose(p,it.toDouble()) }
            assertEquals(p.durationTicks,poses.map { it.model }.distinct().size)
            assertTrue(poses.all { it.offset==p.offset && it.yaw==p.yaw && it.scale==p.scale })
            assertFalse(CoreSkillChoreography.pose(p,-1.0).visible)
            assertFalse(CoreSkillChoreography.pose(p,p.durationTicks.toDouble()).visible)
            for(pose in poses) assertNotNull(javaClass.getResource("/core-ui-pack/assets/projects/items/${pose.model}.json"))
        }
        assertTrue(parts.last().durationTicks>parts.first().durationTicks)
        val hit=CoreSkillChoreography.parts(effect(CoreSkillVisualPhase.CONTACT))
        assertEquals(listOf("approved_dash_impact"),hit.map { it.shape })
        assertEquals(Vec(0.0,1.0,0.0),hit.single().offset)
        val prepare=CoreSkillChoreography.parts(effect(CoreSkillVisualPhase.PREPARE)).single()
        assertEquals(skill.startup,prepare.durationTicks)
        assertTrue(CoreSkillChoreography.pose(prepare,0.0).model.endsWith("blade_0"))
        assertTrue(CoreSkillChoreography.pose(prepare,skill.startup-1.0).model.endsWith("blade_2"))
        assertTrue(CoreSkillChoreography.pose(parts.first(),0.0).model.endsWith("blade_3"))
        val other=skill.copy(icon="war_wound")
        assertNull(CoreApprovedDashV3.parts(CoreSkillEffect(CoreClass.WARRIOR,other,Vec.ZERO,Vec(0.0,0.0,1.0))))
    }

    @Test fun `real vertices stay in front and inside authored reach at all eight headings`() {
        repeat(8) { heading ->
            val a=heading*PI/4
            val d=Vec(sin(a),0.0,cos(a))
            val parts=CoreSkillChoreography.parts(effect(CoreSkillVisualPhase.PREPARE,d))+
                CoreSkillChoreography.parts(effect(direction=d))
            for(p in parts) repeat(p.durationTicks) { tick ->
                val pose=CoreSkillChoreography.pose(p,tick.toDouble())
                val vertices=mutableListOf<Vec>()
                val model=javaClass.getResourceAsStream("/core-ui-pack/assets/projects/models/${pose.model}.json")!!
                    .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
                for(element in model.getAsJsonArray("elements")) {
                    val e=element.asJsonObject
                    val lo=e.getAsJsonArray("from").map { it.asDouble }
                    val hi=e.getAsJsonArray("to").map { it.asDouble }
                    // Test every real corner AFTER native element rotation. Opposite
                    // raw bounds alone no longer describe this curved ribbon.
                    repeat(8) { corner ->
                        val v=DoubleArray(3) { axis -> if(corner and (1 shl axis)==0) lo[axis] else hi[axis] }
                        e.getAsJsonObject("rotation")?.let { r ->
                            assertEquals("x",r.get("axis").asString)
                            val o=r.getAsJsonArray("origin").map { it.asDouble }
                            val angle=Math.toRadians(r.get("angle").asDouble)
                            val dy=v[1]-o[1]; val dz=v[2]-o[2]
                            v[1]=o[1]+dy*cos(angle)-dz*sin(angle)
                            v[2]=o[2]+dy*sin(angle)+dz*cos(angle)
                        }
                        val x0=(v[0]-8)/16*pose.scale.x()
                        val y0=(v[1]-8)/16*pose.scale.y()
                        val z0=(v[2]-8)/16*pose.scale.z()
                        val y=y0*cos(pose.pitch)-z0*sin(pose.pitch)
                        val z=y0*sin(pose.pitch)+z0*cos(pose.pitch)
                        val x=x0*cos(pose.roll)-y*sin(pose.roll)
                        val at=Vec(x*cos(a)+z*sin(a),x0*sin(pose.roll)+y*cos(pose.roll),-x*sin(a)+z*cos(a)).add(pose.offset)
                        vertices+=at
                        assertTrue(at.x()*d.x()+at.z()*d.z()>=-.05,"behind $heading $tick")
                        assertTrue(hypot(at.x(),at.z())<=CoreSkillScenes.get("dash").reach+.25,"reach $heading $tick $at")
                    }
                }
                if(p.shape=="approved_dash_blade" && tick==1) {
                    // The apex must not regress to disconnected horizontal slivers.
                    assertTrue(vertices.maxOf { it.y() }-vertices.minOf { it.y() }>.25,"flat apex")
                }
            }
        }
    }

    @Test fun `export complete prepare swing and accepted hit plus a miss comparison`() {
        fun xyz(v: Vec)=listOf(v.x(),v.y(),v.z())
        val scenes=listOf(false,true).map { hit ->
            val prepare=CoreSkillChoreography.parts(effect(CoreSkillVisualPhase.PREPARE))
            val swing=CoreSkillChoreography.parts(effect())
            val contact=if(hit) CoreSkillChoreography.parts(effect(CoreSkillVisualPhase.CONTACT)) else emptyList()
            val beats=listOf(Triple(0,prepare,Vec.ZERO),Triple(skill.startup,swing,Vec.ZERO),
                Triple(skill.startup,contact,Vec(0.0,0.0,1.5)))
            val frames=(0..(skill.startup+17)).map { tick -> beats.flatMap { (start,parts,at) -> parts.mapNotNull { p ->
                val pose=CoreSkillChoreography.pose(p,(tick-start).toDouble())
                if(!pose.visible) null else mapOf("model" to pose.model,"offset" to xyz(pose.offset.add(at)),
                    "scale" to xyz(pose.scale),"yaw" to pose.yaw,"pitch" to pose.pitch,"roll" to pose.roll)
            } } }
            assertTrue(frames.last().isEmpty())
            mapOf("id" to if(hit) "dash_hit" else "dash_miss","name" to if(hit) "踏み込み斬り・命中例" else "踏み込み斬り・空振り","frames" to frames)
        }
        val cwd=Path.of(System.getProperty("user.dir"))
        val root=if(cwd.fileName.toString()=="server-minestom") cwd.parent else cwd
        Files.createDirectories(root.resolve(".tools"))
        Files.writeString(root.resolve(".tools/approved-dash-v3-timeline.json"),Gson().toJson(scenes))
    }
}
