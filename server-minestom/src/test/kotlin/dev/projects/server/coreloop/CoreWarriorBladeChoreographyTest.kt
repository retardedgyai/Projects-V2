package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.*
import kotlin.test.*

class CoreWarriorBladeChoreographyTest {
    private val skills = CoreSkillCatalog.skills(CoreClass.WARRIOR).filter { it.icon in CoreWarriorBladeChoreography.sceneIds && it.icon!="dash" }
    private fun effect(s: CoreSkillDefinition, pulse: Int=0, yaw: Double=0.0, phase: CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE,
        prepareTicks: Int=s.startup) =
        CoreSkillEffect(CoreClass.WARRIOR,s,Vec.ZERO,Vec(sin(yaw),0.0,cos(yaw)),phase,pulse,prepareTicks=prepareTicks)

    @Test fun `all attacks use native contours with stable transform and valid model sequence`() {
        for(s in skills) repeat(s.pulses) { beat ->
            for(phase in CoreSkillVisualPhase.entries) for(p in CoreSkillChoreography.parts(effect(s,beat,phase=phase))) {
                assertTrue(CoreWarriorBladeChoreography.owns(p))
                assertEquals(0,CoreCombatMeshes.interpolationTicks(p))
                val poses=(0 until p.durationTicks).map { CoreSkillChoreography.pose(p,it.toDouble()) }
                assertEquals(1,poses.map { it.offset to it.scale }.distinct().size)
                assertEquals(1,poses.map { Triple(it.yaw,it.pitch,it.roll) }.distinct().size)
                assertTrue(poses.all { it.visible })
                assertFalse(CoreSkillChoreography.pose(p,-1.0).visible)
                assertFalse(CoreSkillChoreography.pose(p,p.durationTicks.toDouble()).visible)
                poses.forEach { assertNotNull(javaClass.getResource("/core-ui-pack/assets/projects/items/${it.model}.json"),it.model) }
            }
        }
        for(job in CoreClass.entries.filter { it!=CoreClass.WARRIOR }) for(s in CoreSkillCatalog.skills(job))
            assertNull(CoreWarriorBladeChoreography.parts(CoreSkillEffect(job,s,Vec.ZERO,Vec(0.0,0.0,1.0))))
    }

    @Test fun `multi hit attacks author distinct paths not rotated identical frames`() {
        for(id in listOf("war_ult")) {
            val s=skills.first { it.icon==id }
            val parts=(0..2).map { CoreSkillChoreography.parts(effect(s,it)).first() }
            assertEquals(3,parts.map { CoreSkillChoreography.pose(it,1.0).model }.distinct().size)
            assertTrue(parts.all { it.spin==0.0 && !it.followOwner })
        }
    }

    @Test fun `core sweep has one frontal horizontal contour rather than a rotating combo`() {
        val s=skills.first { it.icon=="whirl" }
        assertEquals(1,s.pulses)
        val blade=CoreSkillChoreography.parts(effect(s)).first()
        assertEquals("warrior_skill:wound:blade",blade.shape)
        assertTrue(blade.offset.z()>0)
        assertEquals(-.35,blade.pitch);assertEquals(-.08,blade.roll);assertEquals(0.0,blade.spin)
    }

    @Test fun `preparation stays in startup frames and contact is only target located`() {
        for(s in skills) {
            val prepare=CoreSkillChoreography.parts(effect(s,phase=CoreSkillVisualPhase.PREPARE)).single()
            assertTrue(CoreSkillChoreography.pose(prepare,0.0).model.endsWith("blade_0"))
            assertTrue(CoreSkillChoreography.pose(prepare,(prepare.durationTicks-1).toDouble()).model.endsWith("blade_2"))
            val pulse=CoreSkillChoreography.parts(effect(s))
            assertTrue(CoreSkillChoreography.pose(pulse.first(),0.0).model.endsWith("blade_3"))
            assertTrue(pulse.none { it.shape.contains("contact") })
            val contact=CoreSkillChoreography.parts(effect(s,phase=CoreSkillVisualPhase.CONTACT)).single()
            assertEquals(Vec(0.0,1.0,0.0),contact.offset)
        }
    }

    @Test fun `actual folded model corners stay above floor and inside cast envelope in eight headings`() {
        // Inspect the shipped legal model element rotations, not imaginary unit-square bounds.
        val cache=mutableMapOf<String,List<DoubleArray>>()
        fun corners(model: String)=cache.getOrPut(model) {
            javaClass.getResourceAsStream("/core-ui-pack/assets/projects/models/$model.json")!!.bufferedReader().use { reader ->
                JsonParser.parseReader(reader).asJsonObject.getAsJsonArray("elements").flatMap { element ->
                    val e=element.asJsonObject; val lo=e.getAsJsonArray("from"); val hi=e.getAsJsonArray("to")
                    (0..7).map { c ->
                        DoubleArray(3) { axis -> (if(c and (1 shl axis)==0) lo[axis] else hi[axis]).asDouble }.also { v ->
                            e.getAsJsonObject("rotation")?.let { r ->
                                val o=r.getAsJsonArray("origin"); val a=Math.toRadians(r.get("angle").asDouble)
                                val y=v[1]-o[1].asDouble; val z=v[2]-o[2].asDouble
                                v[1]=o[1].asDouble+y*cos(a)-z*sin(a);v[2]=o[2].asDouble+y*sin(a)+z*cos(a)
                            }
                        }
                    }
                }
            }
        }
        for(s in skills) repeat(s.pulses) { beat -> repeat(8) { heading ->
            val yaw=heading*PI/4
            for(phase in listOf(CoreSkillVisualPhase.PREPARE,CoreSkillVisualPhase.PULSE))
            for(p in CoreSkillChoreography.parts(effect(s,beat,yaw,phase))) repeat(p.durationTicks) { age ->
                val pose=CoreSkillChoreography.pose(p,age.toDouble())
                for(v in corners(pose.model)) {
                    val x=(v[0]-8)/16*pose.scale.x();val y=(v[1]-8)/16*pose.scale.y();val z=(v[2]-8)/16*pose.scale.z()
                    val y1=y*cos(pose.pitch)-z*sin(pose.pitch);val z1=y*sin(pose.pitch)+z*cos(pose.pitch)
                    val x2=x*cos(pose.roll)-y1*sin(pose.roll);val y2=x*sin(pose.roll)+y1*cos(pose.roll)
                    val point=Vec(x2*cos(yaw)+z1*sin(yaw),y2,-x2*sin(yaw)+z1*cos(yaw)).add(pose.offset)
                    assertTrue(point.y()>=.02,"${s.icon} buried at $age: $point")
                    assertTrue(hypot(point.x(),point.z())<=s.radius+.1,"${s.icon} beyond damage reach: $point")
                    if(s.icon!="whirl") assertTrue(point.x()*sin(yaw)+point.z()*cos(yaw)>=-.1,"${s.icon} behind: $point")
                }
            }
        } }
    }

    @Test fun `export actual skill phases for eye height and side review`() {
        fun xyz(v: Vec)=listOf(v.x(),v.y(),v.z())
        fun scene(s: CoreSkillDefinition, beats: List<Int>, id: String, name: String): Map<String,Any> {
            // AS 1.0 review schedule from CorePlayerCombat: one prepare, then
            // pulse every 8 ticks. This is not a client packet/FPS recording.
            val events=listOf(0 to effect(s,beats.first(),phase=CoreSkillVisualPhase.PREPARE,prepareTicks=s.startup-1))+
                beats.mapIndexed { index,beat -> (s.startup+index*8) to effect(s,beat) }
            val frames=(0 until s.startup+(beats.size-1)*8+22).map { tick ->
                events.flatMap { (start,event) ->
                    CoreSkillChoreography.parts(event).mapNotNull { p ->
                        val age=tick-start
                        val pose=CoreSkillChoreography.pose(p,age.toDouble())
                        if(!pose.visible) null else mapOf("model" to pose.model,"offset" to xyz(pose.offset),"scale" to xyz(pose.scale),
                            "yaw" to pose.yaw,"pitch" to pose.pitch,"roll" to pose.roll)
                    }
                }
            }
            return mapOf("id" to id,"name" to name,"frames" to frames,
                "pulseTicks" to events.drop(1).map { it.first },"attackSpeed" to 1.0,
                "timingSource" to "CorePlayerCombat declared schedule; not a packet capture")
        }
        val scenes=skills.flatMap { s ->
            (0 until s.pulses).map { beat -> scene(s,listOf(beat),"${s.icon}_$beat","${s.name} ${beat+1}") }+
                if(s.pulses>1) listOf(scene(s,(0 until s.pulses).toList(),"${s.icon}_full","${s.name} / 全段連続")) else emptyList()
        }
        val cwd=Path.of(System.getProperty("user.dir"));val root=if(cwd.fileName.toString()=="server-minestom")cwd.parent else cwd
        Files.createDirectories(root.resolve(".tools"))
        Files.writeString(root.resolve(".tools/warrior-rework-timeline.json"),Gson().toJson(scenes))
    }
}
