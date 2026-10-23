package dev.projects.server.coreloop

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.*
import kotlin.test.*

class CoreWarriorSupportChoreographyTest {
    private fun effect(id: String,phase: CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE,prepare: Int=5,yaw: Double=0.0)=
        CoreSkillEffect(CoreClass.WARRIOR,CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon==id },
            Vec.ZERO,Vec(sin(yaw),0.0,cos(yaw)),phase,prepareTicks=prepare)
    private val models=mutableMapOf<String,JsonObject>()
    private fun model(name: String)=models.getOrPut(name) {
        javaClass.getResourceAsStream("/core-ui-pack/assets/projects/models/$name.json")!!
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
    }
    private fun axis(p: CoreMeshPose): Vec {
        val y=-sin(p.pitch);val z=cos(p.pitch);val x=-y*sin(p.roll)
        return Vec(cos(p.yaw)*x+sin(p.yaw)*z,y*cos(p.roll),-sin(p.yaw)*x+cos(p.yaw)*z)
    }
    @Test fun `guard uses approved painted sword texture and voice fronts disappear completely`() {
        val guard=model("combat_vfx/war_parry_blade_steel")
        assertEquals("projects:combat_vfx/warrior_support/guard",guard.getAsJsonObject("textures")["sword"].asString)
        assertTrue(guard.getAsJsonArray("elements").all {
            val e=it.asJsonObject
            e.getAsJsonArray("to")[1].asDouble-e.getAsJsonArray("from")[1].asDouble<=.5
        })
        assertEquals(0,model("combat_vfx/war_parry_blade_steel_fade7").getAsJsonArray("elements").size())
    }
    @Test fun `all support windups remain bounded at high attack speed and every pose resolves`() {
        for(id in CoreWarriorSupportChoreography.sceneIds) for(life in 1..4) {
            val parts=CoreSkillChoreography.parts(effect(id,CoreSkillVisualPhase.PREPARE,life))
            for(p in parts) {
                assertEquals(life,p.durationTicks)
                assertTrue(CoreSkillChoreography.pose(p,0.0).visible)
                assertFalse(CoreSkillChoreography.pose(p,life.toDouble()).visible)
                model(CoreSkillChoreography.pose(p,(life-1).toDouble()).model)
            }
        }
    }
    @Test fun `export support windup and active intervals for model projection`() {
        fun xyz(v: Vec)=listOf(v.x(),v.y(),v.z())
        val scenes=CoreWarriorSupportChoreography.sceneIds.map { id ->
            val skill=effect(id).skill
            val events=mutableListOf<Pair<Int,List<CoreCombatMeshPart>>>()
            if(skill.startup>1) events+=0 to CoreSkillChoreography.parts(effect(id,CoreSkillVisualPhase.PREPARE,skill.startup-1))
            events+=skill.startup to CoreSkillChoreography.parts(effect(id))
            val end=events.maxOf { (at,parts) -> at+parts.maxOf { it.delayTicks+it.durationTicks } }
            mapOf("id" to id,"name" to skill.name,"frames" to (0..end).map { tick ->
                events.flatMap { (at,parts) -> if(tick<at) emptyList() else parts.mapNotNull { p ->
                    val pose=CoreSkillChoreography.pose(p,(tick-at).toDouble())
                    if(!pose.visible) null else mapOf("model" to pose.model,"offset" to xyz(pose.offset),"scale" to xyz(pose.scale),
                        "yaw" to pose.yaw,"pitch" to pose.pitch,"roll" to pose.roll)
                } }
            })
        }
        val path=Path.of("../.tools/warrior-support-frames.json")
        Files.createDirectories(path.parent);Files.writeString(path,Gson().toJson(scenes))
    }
}
