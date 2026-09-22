package dev.projects.server.coreloop

import com.google.gson.Gson
import com.google.gson.JsonParser
import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.*
import kotlin.test.*

class CoreExpandedSlashChoreographyTest {
    // Retain coverage of the old art still used by assassin; warrior routing has its own tests.
    private fun legacyParts(e: CoreSkillEffect)=CoreExpandedSlashChoreography.parts(e) ?: CoreGreatswordSweepChoreography.parts(e)!!
    private fun job(id: String)=if(id.startsWith("ass_")) CoreClass.ASSASSIN else CoreClass.WARRIOR
    private fun skill(id: String)=CoreSkillCatalog.skills(job(id)).first { it.icon==id }
    private fun effect(id: String,phase: CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE,pulse: Int=0,d: Vec=Vec(0.0,0.0,1.0))=
        CoreSkillEffect(job(id),skill(id),Vec.ZERO,d,phase,pulse,prepareTicks=skill(id).startup)
    private fun vertices(pose: CoreMeshPose): List<Vec> {
        val json=javaClass.getResourceAsStream("/core-ui-pack/assets/projects/models/${pose.model}.json")!!
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        return json.getAsJsonArray("elements").flatMap { raw ->
            val e=raw.asJsonObject
            val lo=e.getAsJsonArray("from").map { it.asDouble }; val hi=e.getAsJsonArray("to").map { it.asDouble }
            (0..7).map { c ->
                val v=DoubleArray(3) { if(c and (1 shl it)==0) lo[it] else hi[it] }
                e.getAsJsonObject("rotation")?.let { r ->
                    assertEquals("x",r.get("axis").asString)
                    val o=r.getAsJsonArray("origin").map { it.asDouble }; val a=Math.toRadians(r.get("angle").asDouble)
                    val y=v[1]-o[1]; val z=v[2]-o[2]
                    v[1]=o[1]+y*cos(a)-z*sin(a);v[2]=o[2]+y*sin(a)+z*cos(a)
                }
                val x0=(v[0]-8)/16*pose.scale.x(); val y0=(v[1]-8)/16*pose.scale.y(); val z0=(v[2]-8)/16*pose.scale.z()
                val y=y0*cos(pose.pitch)-z0*sin(pose.pitch); val z=y0*sin(pose.pitch)+z0*cos(pose.pitch)
                val x=x0*cos(pose.roll)-y*sin(pose.roll)
                Vec(x*cos(pose.yaw)+z*sin(pose.yaw),x0*sin(pose.roll)+y*cos(pose.roll),-x*sin(pose.yaw)+z*cos(pose.yaw)).add(pose.offset)
            }
        }
    }

    @Test fun `each accepted pulse owns its stroke with stable headings and separate aftermath`() {
        for(id in CoreExpandedSlashChoreography.sceneIds) for(pulse in 0 until skill(id).pulses) {
            val parts=legacyParts(effect(id,pulse=pulse))
            assertEquals(if(id in setOf("ass_execute","whirl","ass_fan")) 8 else 4,parts.count { !it.secondary },id)
            assertEquals(if(id=="ass_execute") 8 else 4,parts.count { it.secondary },id)
            assertTrue(parts.none { it.shape.endsWith(":impact") || it.delayTicks!=0 },id)
            for(p in parts) {
                assertTrue(p.durationTicks<=13)
                if(!p.secondary) assertTrue(p.durationTicks<=8,"No cutting core overlaps next server hit")
                for(t in 0 until p.durationTicks) {
                    val pose=CoreSkillChoreography.pose(p,t.toDouble())
                    assertEquals(CoreSkillChoreography.pose(p,0.0).model,pose.model,"Model identity is stable for interpolation")
                    assertTrue(listOf(pose.yaw,pose.pitch,pose.roll,pose.scale.x(),pose.scale.z()).all { it.isFinite() })
                    assertNotNull(javaClass.getResource("/core-ui-pack/assets/projects/items/${pose.model}.json"))
                }
                assertFalse(CoreSkillChoreography.pose(p,-1.0).visible)
                assertFalse(CoreSkillChoreography.pose(p,p.durationTicks.toDouble()).visible)
            }
            val contact=legacyParts(effect(id,CoreSkillVisualPhase.CONTACT,pulse)).single()
            assertTrue(contact.shape.endsWith(":impact"))
            assertEquals(Vec(0.0,1.0,0.0),contact.offset)
        }
        assertNotEquals(legacyParts(effect("ass_fan",pulse=0)).first().shape,
            legacyParts(effect("ass_fan",pulse=1)).first().shape)
    }

    @Test fun `real bent vertices respect front reach and ground for every heading`() {
        for(id in CoreExpandedSlashChoreography.sceneIds) repeat(8) { h ->
            val a=h*PI/4; val d=Vec(sin(a),0.0,cos(a))
            val radial=id in setOf("whirl","ass_fan")
            for(phase in listOf(CoreSkillVisualPhase.PREPARE,CoreSkillVisualPhase.PULSE))
                for(p in legacyParts(effect(id,phase,d=d))) repeat(p.durationTicks) { t ->
                    for(v in vertices(CoreSkillChoreography.pose(p,t.toDouble()))) {
                        assertTrue(v.y()>=.02,"$id enters ground: $v")
                        assertTrue(hypot(v.x(),v.z())<=CoreSkillScenes.get(id).reach+.25,"$id exceeds reach: $v")
                        if(!radial) assertTrue(v.x()*d.x()+v.z()*d.z()>=-.05,"$id reaches behind: $v")
                    }
                }
        }
    }

    @Test fun `forward cutting surfaces are not edge on to the owner eye`() {
        for(id in listOf("war_wound","war_counter","slam","ass_execute")) {
            for(p in legacyParts(effect(id)).filter { !it.secondary }) {
                for (age in listOf(1.0, 2.0)) {
                    val points=vertices(CoreSkillChoreography.pose(p,age))
                    var area=0.0;var projected=0.0
                    for(v in points.chunked(8)) {
                        val a=v[3].sub(v[2]);val b=v[6].sub(v[2])
                        val n=Vec(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x())
                        val sight=Vec(0.0,1.62,-.7).sub(v[2].add(v[7]).mul(.5)).normalize()
                        area+=n.length()
                        projected+=abs(n.x()*sight.x()+n.y()*sight.y()+n.z()*sight.z())
                    }
                    if(area>1e-8) assertTrue(projected/area>.18,"$id at $age edge-on fraction ${projected/area}")
                }
            }
        }
    }

    @Test fun `prepare spends startup before the first pulse and never has contact art`() {
        for(id in CoreExpandedSlashChoreography.sceneIds) {
            val prepare=legacyParts(effect(id,CoreSkillVisualPhase.PREPARE))
            for((i,p) in prepare.withIndex()) {
                assertEquals(skill(id).startup,p.durationTicks)
                val release=legacyParts(effect(id)).filter { !it.secondary }[i]
                assertEquals(CoreSkillChoreography.pose(p,p.durationTicks-1.0),CoreSkillChoreography.pose(release,0.0))
            }
            assertTrue(legacyParts(effect(id)).all { CoreSkillChoreography.pose(it,0.0).model.startsWith("combat_vfx/flow/") })
        }
        assertNull(CoreExpandedSlashChoreography.parts(effect("war_breach")))
        assertNull(CoreExpandedSlashChoreography.parts(effect("ass_ult")))
    }

    @Test fun `export complete startup and server spaced multi pulse review timelines`() {
        fun xyz(v: Vec)=listOf(v.x(),v.y(),v.z())
        val rows=(listOf("dash")+CoreExpandedSlashChoreography.sceneIds).map { id ->
            val s=skill(id)
            val prepare=legacyParts(effect(id,CoreSkillVisualPhase.PREPARE))
            val beats=listOf(0 to prepare)+(0 until s.pulses).map { (s.startup+it*8) to legacyParts(effect(id,pulse=it)) }
            val end=s.startup+(s.pulses-1)*8+18
            val frames=(0..end).map { tick -> beats.flatMap { (start,parts) -> parts.mapNotNull { p ->
                val pose=CoreSkillChoreography.pose(p,tick-start.toDouble())
                if(!pose.visible) null else mapOf("model" to pose.model,"offset" to xyz(pose.offset),"scale" to xyz(pose.scale),
                    "yaw" to pose.yaw,"pitch" to pose.pitch,"roll" to pose.roll)
            } } }
            assertTrue(frames.last().isEmpty())
            assertTrue(frames.maxOf { it.size }<=16)
            mapOf("id" to id,"name" to s.name,"frames" to frames)
        }
        val cwd=Path.of(System.getProperty("user.dir"));val root=if(cwd.fileName.toString()=="server-minestom") cwd.parent else cwd
        Files.createDirectories(root.resolve(".tools"))
        Files.writeString(root.resolve(".tools/expanded-slash-timelines.json"),Gson().toJson(rows))
    }
}
