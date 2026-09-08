package dev.projects.server.coreloop

import com.google.gson.JsonParser
import com.google.gson.Gson
import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.*
import kotlin.test.*

class CoreRangerChoreographyTest {
    private fun effect(id: String,phase: CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE,pulse: Int=0,
        direction: Vec=Vec(0.0,0.0,1.0),length: Double=5.0): CoreSkillEffect {
        val skill=CoreSkillCatalog.skills(CoreClass.RANGER).first { it.icon==id }
        return CoreSkillEffect(CoreClass.RANGER,skill,Vec.ZERO,direction,phase,pulse,
            rayLength=length,clippedRay=CoreSkillScenes.get(id).kind==CoreSceneKind.RAY)
    }
    @Test fun `six instant shots keep endpoint arrows brief while changing wakes remain readable`() {
        val shots=CoreSkillCatalog.skills(CoreClass.RANGER).filter { CoreSkillScenes.get(it.icon).kind==CoreSceneKind.RAY }
        assertEquals(6,shots.size)
        for(skill in shots) for(length in listOf(.06,.25,1.0,5.0,23.0)) {
            val e=effect(skill.icon,length=length)
            val parts=CoreSkillChoreography.parts(e)
            assertEquals(2,parts.size)
            assertEquals(6,parts.first().durationTicks)
            val wake=parts.last()
            assertEquals("shot_wake",wake.shape)
            assertEquals(e.durationTicks,wake.durationTicks)
            assertEquals(12,(0 until wake.durationTicks).map { CoreSkillChoreography.pose(wake,it.toDouble()).model }.toSet().size)
            assertFalse(CoreSkillChoreography.pose(parts.first(),6.0).visible)
            assertTrue(CoreSkillChoreography.pose(wake,8.0).visible)
        }
    }
    @Test fun `ranger shot vertices never leave any clipped segment including vertical shots`() {
        fun model(name: String)=javaClass.getResourceAsStream("/core-ui-pack/assets/projects/models/$name.json")!!
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        for(id in listOf("pierce","hunt_pierce","hunt_ult")) for(length in listOf(.06,.25,5.0,23.0))
            for(direction in listOf(Vec(0.0,0.0,1.0),Vec(0.0,1.0,0.0),Vec(.4,-.7,-.6).normalize())) {
                val e=effect(id,direction=direction,length=length)
                for(p in CoreSkillChoreography.parts(e)) for(tick in 0 until p.durationTicks) {
                    val pose=CoreSkillChoreography.pose(p,tick.toDouble())
                    val center=pose.offset.x()*direction.x()+pose.offset.y()*direction.y()+pose.offset.z()*direction.z()
                    for(element in model(pose.model).getAsJsonArray("elements")) {
                        // Model +Z is aligned to the ray by the yaw/pitch transform;
                        // any X/Y cross-section is perpendicular to that direction.
                        for(edge in listOf("from","to")) {
                            val along=center+(element.asJsonObject.getAsJsonArray(edge)[2].asDouble/16-.5)*pose.scale.z()
                            assertTrue(along in -.00001..length+.00001,"$id $length ${pose.model}: $along")
                        }
                    }
                }
            }
    }
    @Test fun `rain prepare tips arrive exactly at the corresponding authoritative pulse points`() {
        for(id in listOf("arrow_rain","hunt_storm")) for(pulse in 0..5) {
            val prepared=CoreSkillChoreography.parts(effect(id,CoreSkillVisualPhase.PREPARE,pulse))
            val landed=CoreSkillChoreography.parts(effect(id,pulse=pulse))
            val arrows=landed.filter { !it.secondary }
            assertEquals(if(id=="hunt_storm") 8 else 6,arrows.size)
            assertEquals(arrows.size,prepared.size)
            for((from,to) in prepared.zip(arrows)) {
                val end=CoreSkillChoreography.pose(from,(from.durationTicks-1).toDouble())
                val hit=CoreSkillChoreography.pose(to,0.0)
                assertTrue(end.offset.distance(hit.offset)<.00001)
                assertTrue(from.offset.y()-end.offset.y()>3.8)
                val direction=Vec(sin(hit.yaw)*cos(hit.pitch),-sin(hit.pitch),cos(hit.yaw)*cos(hit.pitch))
                val tip=hit.offset.add(direction.mul(hit.scale.z()*.5))
                assertEquals(.12,tip.y(),.00001)
                assertTrue(direction.y()<-.9)
            }
            assertTrue(landed.filter { it.secondary }.all { it.shape=="fletching" && it.travel.y()>0 })
        }
    }
    @Test fun `successive rain pulses change landing positions rather than stacking a stamped symbol`() {
        for(id in listOf("arrow_rain","hunt_storm")) {
            val first=CoreSkillChoreography.parts(effect(id,pulse=0)).filter { !it.secondary }.map { it.offset }
            val next=CoreSkillChoreography.parts(effect(id,pulse=1)).filter { !it.secondary }.map { it.offset }
            assertTrue(first.zip(next).all { (a,b) -> a.distance(b)>.5 })
            assertTrue(CoreSkillChoreography.parts(effect(id)).none { it.shape=="target_reticle" })
        }
    }
    @Test fun `frost fan has five forward rays across the actual cone rather than an all round burst`() {
        val e=effect("frost_fan")
        val parts=CoreSkillChoreography.parts(e)
        assertEquals(5,parts.count { it.shape=="frost_arrow" })
        assertEquals(5,parts.count { it.shape=="shot_wake" })
        assertEquals(8,parts.count { !it.secondary })
        for(p in parts) {
            assertTrue(p.offset.z()>0)
            assertTrue(abs(p.yaw)<=.8)
            if(p.shape=="shot_wake") assertEquals(e.radius,p.scale.z())
        }
    }
    @Test fun `snare only snaps on actual pulses and no second prepare duplicates its jaws`() {
        for(pulse in 0..3) {
            val prepare=CoreSkillChoreography.parts(effect("hunt_trap",CoreSkillVisualPhase.PREPARE,pulse))
            assertEquals(if(pulse==0) 2 else 0,prepare.size)
            val parts=CoreSkillChoreography.parts(effect("hunt_trap",pulse=pulse))
            assertEquals(7,parts.size)
            for(p in parts.take(2)) {
                assertEquals(8,p.durationTicks)
                val start=CoreSkillChoreography.pose(p,0.0);val released=CoreSkillChoreography.pose(p,7.0)
                assertTrue(abs(start.pitch)>1.0)
                assertEquals(0.0,released.pitch,.00001)
                assertFalse(CoreSkillChoreography.pose(p,8.0).visible)
            }
        }
    }
    @Test fun `hunter mark occurs only on contact and draws four inward closing corner marks`() {
        val shot=CoreSkillChoreography.parts(effect("hunt_mark"))
        assertTrue(shot.none { it.shape=="mark_hook" || it.shape=="hunter_mark" })
        val contact=CoreSkillChoreography.parts(effect("hunt_mark",CoreSkillVisualPhase.CONTACT))
        assertEquals(4,contact.size)
        for(p in contact) {
            assertEquals("mark_hook",p.shape)
            assertEquals(24,p.durationTicks)
            assertTrue(CoreSkillChoreography.pose(p,9.0).offset.distance(Vec(0.0,1.0,0.0))<p.offset.distance(Vec(0.0,1.0,0.0)))
        }
    }
    @Test fun `ranger prepare and real pulse sequences export for temporal art review`() {
        fun xyz(v: Vec)=listOf(v.x(),v.y(),v.z())
        val scenes=mutableListOf<Map<String,Any>>()
        for(id in listOf("arrow_rain","hunt_storm","hunt_trap","hunt_volley","hunt_ult","hunt_mark")) {
            val skill=CoreSkillCatalog.skills(CoreClass.RANGER).first { it.icon==id }
            val events=mutableListOf<Pair<Int,List<CoreCombatMeshPart>>>()
            // Catalog timing at baseline attack speed; equipment can shorten it
            // in-game, so this is not a recording of a particular player build.
            val startup=skill.startup
            fun add(at: Int,phase: CoreSkillVisualPhase,pulse: Int=0,prepare: Int=4) {
                val origin=if(phase==CoreSkillVisualPhase.PULSE && CoreSkillScenes.get(id).kind==CoreSceneKind.RAY) Vec(0.0,1.0,0.0) else Vec.ZERO
                val e=CoreSkillEffect(CoreClass.RANGER,skill,origin,Vec(0.0,0.0,1.0),phase,pulse,prepare,
                    rayLength=5.0,clippedRay=CoreSkillScenes.get(id).kind==CoreSceneKind.RAY)
                events+=at to CoreSkillChoreography.parts(e).map { it.copy(offset=it.offset.add(origin)) }
            }
            if(startup>1) add(0,CoreSkillVisualPhase.PREPARE,prepare=startup-1)
            repeat(skill.pulses) { pulse ->
                add(startup+pulse*8,CoreSkillVisualPhase.PULSE,pulse)
                if(pulse>0 && skill.motion==CoreSkillMotion.FIELD) add(startup+pulse*8-4,CoreSkillVisualPhase.PREPARE,pulse)
            }
            if(id=="hunt_mark") {
                events+=startup to CoreSkillChoreography.parts(effect(id,CoreSkillVisualPhase.CONTACT)).map { it.copy(offset=it.offset.add(0.0,0.0,4.0)) }
            }
            val end=events.maxOf { (start,parts) -> start+(parts.maxOfOrNull { it.delayTicks+it.durationTicks } ?: 0) }
            scenes+=mapOf("id" to id,"name" to "${skill.name} 連続","job" to "RANGER",
                "frames" to (0..end).map { tick ->
                    events.flatMap { (start,parts) -> if(tick<start) emptyList() else parts.mapNotNull { p ->
                        val pose=CoreSkillChoreography.pose(p,(tick-start).toDouble())
                        if(!pose.visible) null else mapOf("model" to pose.model,"offset" to xyz(pose.offset),"scale" to xyz(pose.scale),
                            "yaw" to pose.yaw,"pitch" to pose.pitch,"roll" to pose.roll)
                    } }
                })
        }
        val cwd=Path.of(System.getProperty("user.dir"))
        val root=if(cwd.fileName.toString()=="server-minestom") cwd.parent else cwd
        Files.createDirectories(root.resolve(".tools"))
        Files.writeString(root.resolve(".tools/ranger-choreography-frames.json"),Gson().toJson(scenes))
    }
}
