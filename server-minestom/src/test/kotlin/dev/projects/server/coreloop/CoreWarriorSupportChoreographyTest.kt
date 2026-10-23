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
    @Test fun `standard planting ends exactly at the fixed pulse root including one tick windups`() {
        for(life in listOf(1,2,5,17)) repeat(4) { heading ->
            val yaw=heading*PI/2
            val prepared=CoreSkillChoreography.parts(effect("war_banner",CoreSkillVisualPhase.PREPARE,life,yaw)).single()
            val planted=CoreSkillChoreography.parts(effect("war_banner",yaw=yaw)).first()
            val end=CoreSkillChoreography.pose(prepared,(life-1).toDouble());val start=CoreSkillChoreography.pose(planted,0.0)
            assertTrue(end.offset.distance(start.offset)<.00001)
            assertEquals(end.model,start.model)
            assertEquals(.12,start.offset.y()-start.scale.y()*.5,.00001)
            assertEquals(60,planted.durationTicks);assertFalse(planted.followOwner)
            assertTrue(prepared.followOwner)
            assertEquals(start.offset,CoreSkillChoreography.pose(planted,40.0).offset)
        }
    }
    @Test fun `cloth unfurls then has sixteen textured wind poses without moving its sewn top or pole`() {
        val hashes=mutableSetOf<Int>()
        val pole=mutableListOf<String>()
        for(frame in 0..19) {
            val m=model("combat_vfx/warrior/standard_${frame}_0")
            val elements=m.getAsJsonArray("elements")
            hashes+=elements.toString().hashCode()
            // The first pole element remains identical; cloth cells extend from its crossbar.
            pole+=elements.first().toString()
            for(element in elements) {
                val e=element.asJsonObject;val lo=e.getAsJsonArray("from");val hi=e.getAsJsonArray("to")
                assertTrue((0..2).all { lo[it].asDouble<=hi[it].asDouble && lo[it].asDouble>=-16 && hi[it].asDouble<=32 })
                assertTrue((0..2).count { lo[it].asDouble<hi[it].asDouble }>=2)
            }
            assertTrue(m.getAsJsonObject("textures").entrySet().all { it.value.asString.startsWith("projects:combat_vfx/warrior_support/") })
            if(frame>0) {
                val top=elements[4].asJsonObject
                assertEquals(27.0,top.getAsJsonArray("to")[1].asDouble,.00001)
                assertEquals(8.0,top.getAsJsonArray("to")[2].asDouble,.00001)
                assertEquals("#cloth",top.getAsJsonObject("faces").getAsJsonObject("south")["texture"].asString)
            }
        }
        // frame 3 (fully unfurled) intentionally matches wind pose 4.
        assertEquals(19,hashes.size);assertEquals(1,pole.toSet().size)
        val p=CoreSkillChoreography.parts(effect("war_banner")).first()
        assertEquals(CoreSkillChoreography.pose(p,2.0).model,CoreSkillChoreography.pose(p,18.0).model)
        assertTrue(CoreSkillChoreography.pose(p,59.0).model.endsWith("_7"))
        assertFalse(CoreSkillChoreography.pose(p,60.0).visible)
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
    @Test fun `rally has one planted standard and a single four direction shield grant phrase`() {
        val parts=CoreSkillChoreography.parts(effect("war_banner"))
        assertEquals(9,parts.size);assertEquals(5,parts.count { !it.secondary })
        assertEquals(1,parts.count { it.shape=="war_standard" })
        val crests=parts.filter { it.shape=="war_rally_streamer" }
        assertEquals(4,crests.size);assertTrue(crests.all { it.delayTicks==0 && it.durationTicks==28 })
        assertTrue(parts.none { it.followOwner || it.sprite || it.shape=="battle_banner" || it.shape=="shield_arch" })
        for(p in crests) assertTrue(CoreSkillChoreography.pose(p,20.0).offset.distance(p.offset)>1.5)
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
