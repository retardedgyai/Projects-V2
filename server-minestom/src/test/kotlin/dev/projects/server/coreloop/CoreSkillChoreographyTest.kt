package dev.projects.server.coreloop

import com.google.gson.Gson
import com.google.gson.JsonParser
import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.math.*

class CoreSkillChoreographyTest {
    private fun effect(job: CoreClass,id: String,phase: CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE): CoreSkillEffect {
        val skill=CoreSkillCatalog.skills(job).first { it.icon==id }
        val ray=CoreSkillScenes.get(id).kind==CoreSceneKind.RAY
        return CoreSkillEffect(job,skill,Vec(0.0,if(ray) 1.1 else 0.0,0.0),Vec(0.0,0.0,1.0),phase,rayLength=if(ray) 5.0 else 0.0,clippedRay=ray)
    }
    @Test fun `all skills have bounded readable phrases and every animated asset resolves`() {
        assertEquals(CoreSkillScenes.all.keys,CoreSkillChoreography.sceneIds)
        val index=javaClass.getResourceAsStream("/core-ui-pack/index.txt")!!.bufferedReader().use { it.readLines().toSet() }
        val checked=mutableSetOf<String>()
        for(job in CoreClass.entries) for(s in CoreSkillCatalog.skills(job)) {
            val e=effect(job,s.icon)
            assertTrue(e.durationTicks in 18..40,s.icon)
            val parts=CoreSkillChoreography.parts(e)
            assertTrue(parts.size in 1..16,"${s.icon}: ${parts.size}")
            for(p in parts) for(tick in 0..p.delayTicks+p.durationTicks) {
                val pose=CoreSkillChoreography.pose(p,tick.toDouble())
                assertTrue(pose.scale.x()>0 && pose.scale.y()>0 && pose.scale.z()>0)
                assertTrue(listOf(pose.offset.x(),pose.offset.y(),pose.offset.z(),pose.yaw,pose.pitch,pose.roll).all(Double::isFinite))
                if(checked.add(pose.model)) {
                    val path="assets/projects/items/${pose.model}.json"
                    assertTrue(path in index,path)
                    val item=javaClass.getResourceAsStream("/core-ui-pack/$path")!!.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
                    val model=item.getAsJsonObject("model").get("model").asString.removePrefix("projects:")
                    assertTrue("assets/projects/models/$model.json" in index,model)
                }
            }
        }
    }
    @Test fun `slash changes artwork over time and keeps a separate delayed wake`() {
        for(id in listOf("ass_execute","ass_fan","ass_ult")) {
            val parts=CoreSkillChoreography.parts(effect(CoreClass.ASSASSIN,id))
            val blade=parts.first()
            assertTrue(blade.sprite,id)
            assertTrue(blade.durationTicks>=22,id)
            val states=(0 until blade.durationTicks).map { CoreSkillChoreography.pose(blade,it.toDouble()) }
            assertTrue(states.map { it.model }.distinct().size>=12,id)
            assertTrue(parts.any { it.secondary && it.delayTicks>=3 && it.sprite },id)
            assertFalse(CoreSkillChoreography.pose(parts.last(),-1.0).visible)
        }
        val poison=CoreSkillChoreography.parts(effect(CoreClass.ASSASSIN,"ass_poison"))
        assertEquals("poison_fang",poison.first().shape)
        assertFalse(poison.first().sprite)
        assertTrue(poison.any { it.sprite && it.palette=="venom" })
    }
    @Test fun `stroke peaks early while its dissolving wake remains readable`() {
        for(job in CoreClass.entries) for(s in CoreSkillCatalog.skills(job)) {
            for(p in CoreSkillChoreography.parts(effect(job,s.icon)).filter { it.sprite }) {
                assertTrue(CoreSkillChoreography.pose(p,p.delayTicks+4.0).model.endsWith("_7"),s.icon)
                assertTrue(CoreSkillChoreography.pose(p,p.delayTicks+p.durationTicks-1.0).model.endsWith("_15"),s.icon)
            }
        }
    }
    @Test fun `return cuts mirror their UVs and orbital cuts stay centered on their owner`() {
        val first=CoreSkillChoreography.parts(effect(CoreClass.WARRIOR,"dash")).first()
        val reverse=CoreSkillChoreography.parts(effect(CoreClass.WARRIOR,"war_counter")).first()
        assertNotEquals(first.spriteMirror,reverse.spriteMirror)
        assertTrue(first.spin*reverse.spin<0)
        for(id in listOf("ass_fan","ass_ult")) {
            val p=CoreSkillChoreography.parts(effect(CoreClass.ASSASSIN,id)).first()
            assertEquals(0.0,p.offset.x());assertEquals(0.0,p.offset.z())
            assertEquals(CoreMeshMotion.REVOLVE,p.motion)
            assertEquals(2*PI,abs(CoreSkillChoreography.pose(p,6.0).yaw-p.yaw),.00001)
        }
        for(reverseUv in listOf(false,true)) {
            val name="slash_${if(reverseUv) "reverse_" else ""}0"
            val model=javaClass.getResourceAsStream("/core-ui-pack/assets/projects/models/combat_vfx/ribbon/$name.json")!!
                .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
            val faces=model.getAsJsonArray("elements")[0].asJsonObject.getAsJsonObject("faces")
            val up=faces.getAsJsonObject("up").getAsJsonArray("uv").map { it.asInt }
            val down=faces.getAsJsonObject("down").getAsJsonArray("uv").map { it.asInt }
            // FaceInfo.UP index zero is MIN_Z; DOWN index zero is MAX_Z.
            assertEquals(listOf(16,0),listOf(up[1],up[3]),name)
            assertEquals(listOf(0,16),listOf(down[1],down[3]),name)
            assertEquals(if(reverseUv) listOf(16,0) else listOf(0,16),listOf(up[0],up[2]),name)
        }
    }
    @Test fun `forward blade quad stays in front at every camera heading`() {
        for(job in listOf(CoreClass.WARRIOR,CoreClass.ASSASSIN)) for(skill in CoreSkillCatalog.skills(job)) {
            if(CoreSkillScenes.get(skill.icon).kind !in setOf(CoreSceneKind.CUT,CoreSceneKind.CLEAVE)) continue
            repeat(8) { heading ->
                val a=heading*PI/4
                val direction=Vec(sin(a),0.0,cos(a))
                val e=CoreSkillEffect(job,skill,Vec.ZERO,direction,CoreSkillVisualPhase.PULSE)
                for(p in CoreSkillChoreography.parts(e).filter { it.sprite }) repeat(p.durationTicks) { tick ->
                    val pose=CoreSkillChoreography.pose(p,(p.delayTicks+tick).toDouble())
                    for(x0 in listOf(-.5,.5)) for(z0 in listOf(-.5,.5)) {
                        var x=x0*pose.scale.x();var y=0.0;var z=z0*pose.scale.z()
                        y=-z*sin(pose.pitch);z*=cos(pose.pitch)
                        val rx=x*cos(pose.roll)-y*sin(pose.roll)
                        x=rx*cos(pose.yaw)+z*sin(pose.yaw)
                        z=-rx*sin(pose.yaw)+z*cos(pose.yaw)
                        val forward=(x+pose.offset.x())*direction.x()+(z+pose.offset.z())*direction.z()
                        assertTrue(forward>=-.3,"${skill.icon} heading=$heading tick=$tick forward=$forward")
                    }
                }
            }
        }
    }
    @Test fun `ray choreography never moves its center beyond the clipped hit segment`() {
        for(job in CoreClass.entries) for(s in CoreSkillCatalog.skills(job)) {
            if(CoreSkillScenes.get(s.icon).kind!=CoreSceneKind.RAY) continue
            val e=effect(job,s.icon)
            for(p in CoreSkillChoreography.parts(e)) repeat(e.durationTicks) { tick ->
                val pose=CoreSkillChoreography.pose(p,tick.toDouble())
                assertTrue(pose.offset.z()-pose.scale.z()/2>=-.001,s.icon)
                assertTrue(pose.offset.z()+pose.scale.z()/2<=5.001,s.icon)
            }
        }
    }
    @Test fun `all actual timeline frames export for animated art review`() {
        fun xyz(v: Vec)=listOf(v.x(),v.y(),v.z())
        val scenes=mutableListOf<Map<String,Any>>()
        for(job in CoreClass.entries) for(s in CoreSkillCatalog.skills(job)) {
            val e=effect(job,s.icon)
            val parts=CoreSkillChoreography.parts(e)
            val end=parts.maxOf { it.delayTicks+it.durationTicks }
            scenes+=mapOf("id" to s.icon,"name" to s.name,"job" to job.name,"frames" to (0..end).map { tick ->
                parts.mapNotNull { p -> val pose=CoreSkillChoreography.pose(p,tick.toDouble());if(!pose.visible) null else
                    mapOf("model" to pose.model,"offset" to xyz(pose.offset.add(e.origin)),"scale" to xyz(pose.scale),
                        "yaw" to pose.yaw,"pitch" to pose.pitch,"roll" to pose.roll) }
            })
        }
        val cwd=Path.of(System.getProperty("user.dir"))
        val root=if(cwd.fileName.toString()=="server-minestom") cwd.parent else cwd
        Files.createDirectories(root.resolve(".tools"))
        Files.writeString(root.resolve(".tools/skill-choreography-frames.json"),Gson().toJson(scenes))
    }
}
