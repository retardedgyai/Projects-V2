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
    @Test fun `accepted parry uses one short native impact and grants have no fake contact stars`() {
        val contact=CoreSkillChoreography.parts(effect("war_guard",CoreSkillVisualPhase.CONTACT)).single()
        assertEquals("warrior_skill:contact",contact.shape)
        assertEquals(9,contact.durationTicks)
        assertEquals(0,contact.delayTicks)
        assertEquals(0,CoreCombatMeshes.interpolationTicks(contact))
        assertFalse(CoreSkillChoreography.pose(contact,9.0).visible)
        for(id in listOf("war_banner","war_cry"))
            assertTrue(CoreSkillChoreography.parts(effect(id,CoreSkillVisualPhase.CONTACT)).isEmpty())
    }
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
    @Test fun `parry raises one greatsword around its grip and keeps it for the actual guard interval`() {
        repeat(8) { heading ->
            val e=effect("war_guard",yaw=heading*PI/4);val parts=CoreSkillChoreography.parts(e)
            assertEquals(2,parts.size);val blade=parts.first();val glint=parts.last()
            assertEquals("war_parry_blade",blade.shape);assertEquals(e.skill.duration,blade.durationTicks)
            assertEquals(30,blade.durationTicks);assertEquals(12,glint.durationTicks)
            assertTrue(parts.all { it.followOwner && !it.sprite && !it.secondary })
            for(tick in 0 until blade.durationTicks) {
                val p=CoreSkillChoreography.pose(blade,tick.toDouble())
                assertTrue(p.offset.sub(axis(p).mul(p.scale.z()*.5)).distance(blade.offset)<.00001)
                if(tick>=3) assertEquals(.7,p.roll,.00001)
                assertTrue(p.visible)
                if(tick<12) {
                    val g=CoreSkillChoreography.pose(glint,tick.toDouble())
                    val delta=g.offset.sub(blade.offset)
                    assertTrue(delta.dot(axis(p)) in 0.0..p.scale.z())
                    assertTrue(delta.cross(axis(p)).length()<.00001)
                }
            }
            assertFalse(CoreSkillChoreography.pose(blade,30.0).visible)
            assertFalse(CoreSkillChoreography.pose(glint,12.0).visible)
        }
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
        for(p in CoreSkillChoreography.parts(effect("war_cry"))) {
            val hashes=(0 until p.durationTicks).map {
                model(CoreSkillChoreography.pose(p,(it+p.delayTicks).toDouble()).model).toString().hashCode()
            }.toSet()
            assertTrue(hashes.size>=14,"Voice should deform and tear, not scale one static hoop")
            assertEquals(0,model(CoreSkillChoreography.pose(p,27.0).model).getAsJsonArray("elements").size())
        }
    }
    @Test fun `roar has three independently moving open voice fronts in each direction not slashes`() {
        val e=effect("war_cry");val parts=CoreSkillChoreography.parts(e)
        assertEquals(6,parts.size);assertTrue(parts.all { it.shape=="war_voice_band" && !it.sprite && !it.followOwner })
        assertEquals(mapOf(0 to 2,4 to 2,8 to 2),parts.groupingBy { it.delayTicks }.eachCount())
        for(p in parts) {
            assertEquals(28,p.delayTicks+p.durationTicks)
            val start=CoreSkillChoreography.pose(p,p.delayTicks.toDouble())
            val peak=CoreSkillChoreography.pose(p,p.delayTicks+5.0)
            assertEquals(.8,hypot(peak.offset.x()-start.offset.x(),peak.offset.z()-start.offset.z()),.00001)
            assertTrue(peak.scale.x()>start.scale.x()*3)
            val later=CoreSkillChoreography.pose(p,p.delayTicks+15.0)
            assertTrue(later.offset.distance(peak.offset)>1.5,"Voice wave must not freeze after its first five ticks")
            val elements=model(start.model).getAsJsonArray("elements")
            // No cube occupies the centre of the voice aperture.
            assertTrue(elements.none {
                val e=it.asJsonObject;val lo=e.getAsJsonArray("from");val hi=e.getAsJsonArray("to")
                lo[0].asDouble<=8 && hi[0].asDouble>=8 && lo[2].asDouble<=8 && hi[2].asDouble>=8
            })
        }
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
