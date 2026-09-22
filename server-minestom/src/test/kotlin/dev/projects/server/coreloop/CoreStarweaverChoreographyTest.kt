package dev.projects.server.coreloop

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.*
import kotlin.test.*

class CoreStarweaverChoreographyTest {
    private fun effect(id: String,phase: CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE,prepare: Int=4,
        length: Double=6.0,direction: Vec=Vec(0.0,0.0,1.0),endpoint: CoreSkillEndpoint=CoreSkillEndpoint.NONE): CoreSkillEffect {
        val skill=CoreSkillCatalog.skills(CoreClass.STARWEAVER).first { it.icon==id }
        return CoreSkillEffect(CoreClass.STARWEAVER,skill,Vec.ZERO,direction,phase,prepareTicks=prepare,
            rayLength=length,clippedRay=skill.motion==CoreSkillMotion.RAY,endpoint=endpoint)
    }
    private val models=mutableMapOf<String,JsonObject>()
    private fun model(name: String)=models.getOrPut(name) {
        javaClass.getResourceAsStream("/core-ui-pack/assets/projects/models/$name.json")!!
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
    }
    @Test fun `star rays preserve clipped endpoints at every aim angle without cosmetic overshoot`() {
        for(id in listOf("star_thread","star_needle","star_break")) for(length in listOf(.06,.4,3.0,24.0))
            for(dir in listOf(Vec(0.0,0.0,1.0),Vec(0.0,1.0,0.0),Vec(.4,-.7,-.6).normalize())) {
                val parts=CoreSkillChoreography.parts(effect(id,length=length,direction=dir))
                assertEquals(if(id=="star_thread") 1 else 2,parts.size)
                assertEquals("weave_ray",parts.first().shape)
                for(p in parts) for(tick in 0 until p.durationTicks) {
                    val pose=CoreSkillChoreography.pose(p,tick.toDouble())
                    val center=pose.offset.dot(dir)
                    assertEquals(atan2(dir.x(),dir.z()),pose.yaw,.00001)
                    assertEquals(-atan2(dir.y(),hypot(dir.x(),dir.z())),pose.pitch,.00001)
                    for(element in model(pose.model).getAsJsonArray("elements")) for(edge in listOf("from","to")) {
                        val along=center+(element.asJsonObject.getAsJsonArray(edge)[2].asDouble/16-.5)*pose.scale.z()
                        assertTrue(along in -.00001..length+.00001,"$id $length tick=$tick along=$along")
                    }
                }
                assertTrue(CoreSkillChoreography.parts(effect(id,length=.01)).isEmpty())
            }
    }
    @Test fun `woven rays tile the actual pixel nebula rather than stretch one picture along the entire ray`() {
        for(length in listOf(.06,1.5,6.0,24.0)) {
            val p=CoreSkillChoreography.parts(effect("star_thread",length=length)).single()
            val count=ceil(length/1.5).toInt().coerceIn(1,16)
            val states=(0 until p.durationTicks).map { CoreSkillChoreography.pose(p,it.toDouble()) }
            assertEquals(16,states.map { it.model }.toSet().size)
            assertTrue(states[3].model.endsWith("_3"));assertTrue(states.last().model.endsWith("_15"))
            for(state in states) {
                val value=model(state.model)
                assertEquals(count*2,value.getAsJsonArray("elements").size())
                assertTrue(value.getAsJsonObject("textures").entrySet().all { it.value.asString.startsWith("projects:combat_vfx/nebula/stream_") })
                for(element in value.getAsJsonArray("elements")) {
                    val e=element.asJsonObject
                    assertTrue(e.getAsJsonObject("rotation")["angle"].asInt in setOf(-45,45))
                    for(face in e.getAsJsonObject("faces").entrySet()) {
                        assertTrue(face.value.asJsonObject.getAsJsonArray("uv").all { it.asDouble in 0.0..16.0 })
                        assertTrue(face.value.asJsonObject["rotation"].asInt in setOf(90,270))
                    }
                }
            }
        }
    }
    @Test fun `star needle and breaker retain separate nuclei and accepted hit only bursts`() {
        val thread=CoreSkillChoreography.parts(effect("star_thread"))
        val needle=CoreSkillChoreography.parts(effect("star_needle"))
        val breaker=CoreSkillChoreography.parts(effect("star_break"))
        assertTrue(needle.first().scale.x()<thread.first().scale.x())
        assertTrue(thread.first().scale.x()<breaker.first().scale.x())
        for(parts in listOf(needle,breaker)) {
            assertEquals("weave_spindle",parts.last().shape)
            assertEquals(8,parts.last().durationTicks)
        }
        for(id in listOf("star_thread","star_needle","star_break")) {
            assertTrue(CoreSkillChoreography.parts(effect(id)).none { it.stellarBurst })
            val hit=CoreSkillChoreography.parts(effect(id,CoreSkillVisualPhase.CONTACT))
            assertTrue(hit.any { it.stellarBurst })
            assertEquals(id=="star_needle",hit.any { it.shape=="star_mark" })
        }
    }
    @Test fun `six nova crests reach the gameplay radius promptly and leave a hollow centre`() {
        val e=effect("star_ring");val parts=CoreSkillChoreography.parts(e)
        assertEquals(6,parts.size);assertTrue(parts.none { it.secondary || it.ground })
        for(p in parts) {
            assertEquals(CoreMeshAtlas.NEBULA_STREAM,p.atlas)
            val early=CoreSkillChoreography.pose(p,5.0)
            assertEquals(e.radius-.9,hypot(early.offset.x(),early.offset.z()),.00001)
            assertEquals(.7,early.offset.y(),.00001)
            assertEquals(-.65,early.pitch,.00001)
            assertEquals(1.8,early.scale.z(),.00001)
            assertTrue(CoreSkillChoreography.pose(p,3.0).model.endsWith("_3"))
            val planes=model(early.model).getAsJsonArray("elements")
            assertEquals(2,planes.size())
            assertEquals(setOf(-45,45),planes.map { it.asJsonObject.getAsJsonObject("rotation")["angle"].asInt }.toSet())
            assertTrue(planes.all { it.asJsonObject.getAsJsonObject("rotation")["axis"].asString=="x" })
            assertTrue(CoreSkillChoreography.pose(p,24.0).visible)
        }
    }
    @Test fun `both endpoints of every constellation edge remain joined while the mantle opens and turns`() {
        for(id in listOf("star_shield","star_constellation")) for(heading in 0..7) {
            val a=heading*PI/4;val e=effect(id,direction=Vec(sin(a),0.0,cos(a)))
            val all=CoreSkillChoreography.parts(e)
            val parts=all.filter { it.shape=="constellation_edge" }
            val mantle=all.filter { it.shape=="star_mantle_stream" }
            assertEquals(if(id=="star_shield") 2 else 4,mantle.size)
            assertTrue(mantle.all { it.followOwner && it.atlas==CoreMeshAtlas.NEBULA_STREAM && it.scale.x()>=2.0 })
            assertEquals(if(id=="star_shield") 4 else 6,parts.size)
            assertTrue(parts.all { it.followOwner && !it.secondary && it.chainAnchor!=null })
            for(tick in 0 until e.durationTicks) {
                val edges=parts.map { p ->
                    val pose=CoreSkillChoreography.pose(p,tick.toDouble())
                    val dir=Vec(sin(pose.yaw)*cos(pose.pitch),-sin(pose.pitch),cos(pose.yaw)*cos(pose.pitch))
                    val from=pose.offset.sub(dir.mul(pose.scale.z()*.5));val to=pose.offset.add(dir.mul(pose.scale.z()*.5))
                    assertEquals(p.chainAnchor!!.y(),from.y(),.00001);assertEquals(p.offset.y(),to.y(),.00001)
                    assertTrue(model(pose.model).getAsJsonArray("elements").size()>0)
                    from to to
                }
                val half=parts.size/2
                for(side in 0..1) for(i in 0 until half-1)
                    assertTrue(edges[side*half+i].second.distance(edges[side*half+i+1].first)<.00001)
            }
            // Even on initial activation, neither open drape lies across the forward camera axis.
            for(p in parts) for(v in listOf(p.offset,p.chainAnchor!!)) {
                val side=cos(a)*v.x()-sin(a)*v.z()
                assertTrue(abs(side)>.6)
            }
        }
    }
    @Test fun `stellar step closes at departure and unfolds at arrival without an opaque gate`() {
        for(endpoint in listOf(CoreSkillEndpoint.DEPARTURE,CoreSkillEndpoint.ARRIVAL)) {
            val e=effect("star_step",endpoint=endpoint);val parts=CoreSkillChoreography.parts(e)
            assertEquals(4,parts.size)
            assertTrue(parts.all { it.atlas==CoreMeshAtlas.NEBULA_STREAM && !it.followOwner && !it.secondary })
            for(p in parts) {
                val start=CoreSkillChoreography.pose(p,p.delayTicks.toDouble())
                val end=CoreSkillChoreography.pose(p,(p.delayTicks+p.durationTicks-1).toDouble())
                assertEquals(endpoint==CoreSkillEndpoint.DEPARTURE,abs(end.offset.x())<abs(start.offset.x()))
                assertEquals(endpoint==CoreSkillEndpoint.DEPARTURE,end.scale.x()<start.scale.x())
                assertEquals(e.durationTicks,p.delayTicks+p.durationTicks)
            }
        }
    }
    @Test fun `all short preparations are positive and new phrases fit primary display budgets`() {
        for(id in CoreStarweaverChoreography.sceneIds) {
            val pulse=CoreSkillChoreography.parts(effect(id))
            assertTrue(pulse.size<=10 && pulse.count { !it.secondary }<=8)
            for(life in 1..4) {
                val parts=CoreSkillChoreography.parts(effect(id,CoreSkillVisualPhase.PREPARE,prepare=life))
                assertTrue(parts.all { it.durationTicks==life && it.delayTicks==0 && it.followOwner })
                assertTrue(parts.all { CoreSkillChoreography.pose(it,0.0).visible })
                assertTrue(parts.none { CoreSkillChoreography.pose(it,life.toDouble()).visible })
            }
        }
    }
    @Test fun `export composed stellar startup and pulse timelines for projection review`() {
        fun xyz(v: Vec)=listOf(v.x(),v.y(),v.z())
        val scenes=(CoreStarweaverChoreography.sceneIds.toList()+"star_step_arrival").map { output ->
            val id=output.removeSuffix("_arrival");val skill=effect(id).skill
            val endpoint=if(id!="star_step") CoreSkillEndpoint.NONE else
                if(output.endsWith("_arrival")) CoreSkillEndpoint.ARRIVAL else CoreSkillEndpoint.DEPARTURE
            val events=mutableListOf<Pair<Int,List<CoreCombatMeshPart>>>()
            if(skill.startup>1) events+=0 to CoreSkillChoreography.parts(effect(id,CoreSkillVisualPhase.PREPARE,prepare=skill.startup-1))
            val origin=if(skill.motion==CoreSkillMotion.RAY) Vec(0.0,1.0,0.0) else Vec.ZERO
            events+=skill.startup to CoreSkillChoreography.parts(effect(id,endpoint=endpoint)).map { it.copy(offset=it.offset.add(origin)) }
            val end=events.maxOf { (at,parts) -> at+parts.maxOf { it.delayTicks+it.durationTicks } }
            mapOf("id" to output,"name" to skill.name+if(output.endsWith("_arrival")) " 到着" else "",
                "frames" to (0..end).map { tick -> events.flatMap { (at,parts) ->
                    if(tick<at) emptyList() else parts.mapNotNull { p ->
                        val pose=CoreSkillChoreography.pose(p,(tick-at).toDouble())
                        if(!pose.visible) null else mapOf("model" to pose.model,"offset" to xyz(pose.offset),"scale" to xyz(pose.scale),
                            "yaw" to pose.yaw,"pitch" to pose.pitch,"roll" to pose.roll)
                    }
                } })
        }
        val path=Path.of("../.tools/star-weaving-frames.json")
        Files.createDirectories(path.parent);Files.writeString(path,Gson().toJson(scenes))
    }
}
