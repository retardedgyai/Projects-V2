package dev.projects.server.coreloop

import com.google.gson.Gson
import com.google.gson.JsonParser
import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.math.*

class CoreGreatswordSweepChoreographyTest {
    private val skill=CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon=="dash" }
    private fun effect(phase: CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE,direction: Vec=Vec(0.0,0.0,1.0))=
        CoreSkillEffect(CoreClass.WARRIOR,skill,Vec.ZERO,direction,phase,prepareTicks=skill.startup)

    @Test fun `blade and aftermath are separate geometry sequences with no extra damage flashes`() {
        val parts=CoreSkillChoreography.parts(effect())
        assertEquals(listOf("greatsword_blade","greatsword_wake"),parts.map { it.shape })
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
        assertEquals(listOf("greatsword_impact"),hit.map { it.shape })
        assertEquals(Vec(0.0,1.0,0.0),hit.single().offset)
        val prepare=CoreSkillChoreography.parts(effect(CoreSkillVisualPhase.PREPARE)).single()
        assertEquals(skill.startup,prepare.durationTicks)
        assertTrue(CoreSkillChoreography.pose(prepare,0.0).model.endsWith("blade_0"))
        assertTrue(CoreSkillChoreography.pose(prepare,skill.startup-1.0).model.endsWith("blade_2"))
        assertTrue(CoreSkillChoreography.pose(parts.first(),0.0).model.endsWith("blade_3"))
        val other=skill.copy(icon="war_wound")
        assertNull(CoreGreatswordSweepChoreography.parts(CoreSkillEffect(CoreClass.WARRIOR,other,Vec.ZERO,Vec(0.0,0.0,1.0))))
    }

    @Test fun `real vertices stay in front and inside authored reach at all eight headings`() {
        repeat(8) { heading ->
            val a=heading*PI/4
            val d=Vec(sin(a),0.0,cos(a))
            for(p in CoreSkillChoreography.parts(effect(direction=d))) repeat(p.durationTicks) { tick ->
                val pose=CoreSkillChoreography.pose(p,tick.toDouble())
                val model=javaClass.getResourceAsStream("/core-ui-pack/assets/projects/models/${pose.model}.json")!!
                    .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
                for(element in model.getAsJsonArray("elements")) {
                    val e=element.asJsonObject
                    for(key in listOf("from","to")) {
                        val v=e.getAsJsonArray(key).map { it.asDouble }
                        val x0=(v[0]-8)/16*pose.scale.x()
                        val y0=(v[1]-8)/16*pose.scale.y()
                        val z0=(v[2]-8)/16*pose.scale.z()
                        val y=y0*cos(pose.pitch)-z0*sin(pose.pitch)
                        val z=y0*sin(pose.pitch)+z0*cos(pose.pitch)
                        val x=x0*cos(pose.roll)-y*sin(pose.roll)
                        val at=Vec(x*cos(a)+z*sin(a),x0*sin(pose.roll)+y*cos(pose.roll),-x*sin(a)+z*cos(a)).add(pose.offset)
                        assertTrue(at.x()*d.x()+at.z()*d.z()>=-.05,"behind $heading $tick")
                        assertTrue(hypot(at.x(),at.z())<=CoreSkillScenes.get("dash").reach+.25,"reach $heading $tick $at")
                    }
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
        Files.writeString(root.resolve(".tools/greatsword-sweep-timeline.json"),Gson().toJson(scenes))
    }
}
