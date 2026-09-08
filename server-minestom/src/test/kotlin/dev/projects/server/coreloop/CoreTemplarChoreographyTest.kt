package dev.projects.server.coreloop

import com.google.gson.Gson
import com.google.gson.JsonParser
import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.*
import kotlin.test.*

class CoreTemplarChoreographyTest {
    private fun effect(id: String,phase: CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE,pulse: Int=0,
        yaw: Double=0.0,prepare: Int=6)=CoreSkillEffect(CoreClass.TEMPLAR,
        CoreSkillCatalog.skills(CoreClass.TEMPLAR).first { it.icon==id },Vec.ZERO,Vec(sin(yaw),0.0,cos(yaw)),
        phase,pulse,prepareTicks=prepare)
    private fun model(name: String)=javaClass.getResourceAsStream("/core-ui-pack/assets/projects/models/$name.json")!!
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }

    @Test fun `hammer handle stays at the grip and windup arrives at the pulse contact pose`() {
        for(id in listOf("temp_mace","temp_break")) repeat(8) { heading ->
            val yaw=heading*PI/4
            val wind=CoreSkillChoreography.parts(effect(id,CoreSkillVisualPhase.PREPARE,yaw=yaw)).single()
            val hit=CoreSkillChoreography.parts(effect(id,yaw=yaw)).first()
            val arrival=CoreSkillChoreography.pose(wind,5.0);val contact=CoreSkillChoreography.pose(hit,0.0)
            assertTrue(arrival.offset.distance(contact.offset)<.00001)
            assertEquals(arrival.pitch,contact.pitch,.00001)
            for(p in listOf(wind,hit)) for(tick in 0 until p.durationTicks) {
                val pose=CoreSkillChoreography.pose(p,tick.toDouble())
                val axis=Vec(sin(yaw)*cos(pose.pitch),-sin(pose.pitch),cos(yaw)*cos(pose.pitch))
                assertTrue(pose.offset.sub(axis.mul(p.scale.z()*.5)).distance(p.offset)<.00001)
            }
            val contactAxis=Vec(sin(yaw)*cos(.7),-sin(.7),cos(yaw)*cos(.7))
            val tip=contact.offset.add(contactAxis.mul(hit.scale.z()*.5))
            assertTrue(tip.y() in .0.. .2)
            assertTrue(CoreSkillChoreography.pose(hit,7.0).offset.y()>contact.offset.y()+.2)
            assertFalse(CoreSkillChoreography.pose(hit,12.0).visible)
        }
    }
    @Test fun `guard uses two opening halves for the actual guard duration with a sight gap`() {
        val e=effect("temp_guard");val parts=CoreSkillChoreography.parts(e)
        assertEquals(2,parts.size)
        assertTrue(parts.all { it.followOwner && it.durationTicks==e.skill.duration })
        for(p in parts) {
            val hold=CoreSkillChoreography.pose(p,10.0)
            assertEquals(p.offset,hold.offset);assertEquals(p.yaw,hold.yaw)
            val sign=if(p.shape.endsWith("left")) -1 else 1
            for(element in model(hold.model).getAsJsonArray("elements")) for(edge in listOf("from","to")) {
                val x=element.asJsonObject.getAsJsonArray(edge)[0].asDouble
                assertTrue(sign*(p.offset.x()+(x/16-.5)*p.scale.x())>=.28)
            }
            val leaving=CoreSkillChoreography.pose(p,(e.skill.duration-1).toDouble())
            assertTrue(abs(leaving.offset.x())>abs(hold.offset.x())+.25)
            assertTrue(leaving.model.endsWith("fade7"))
            assertFalse(CoreSkillChoreography.pose(p,e.skill.duration.toDouble()).visible)
        }
    }
    @Test fun `ward and sanctuary are distinct open activation structures not persistent area buffs`() {
        val ward=CoreSkillChoreography.parts(effect("temp_ward"))
        assertEquals(3,ward.count { it.shape=="oath_shield" })
        assertTrue(ward.filter { it.shape=="oath_shield" }.all { it.followOwner && it.durationTicks==32 })
        val e=effect("temp_sanctuary");val sanctuary=CoreSkillChoreography.parts(e)
        val arches=sanctuary.filter { it.shape=="oath_arch" }
        assertEquals(6,arches.size)
        assertTrue(arches.all { it.ground && !it.followOwner && it.durationTicks==40 })
        assertTrue(arches.filter { it.offset.z()>0 }.all { abs(it.offset.x())>1.0 })
        assertTrue(arches.all { it.durationTicks<e.skill.duration })
        assertTrue((ward+sanctuary).none { it.shape.startsWith("oath_wave") })
    }
    @Test fun `field creates its posts once and each actual wave owns a short boundary pulse`() {
        val first=CoreSkillChoreography.parts(effect("temp_field"))
        val posts=first.filter { it.shape=="oath_boundary" }
        assertEquals(4,posts.size);assertTrue(posts.all { it.durationTicks==32 && it.ground })
        for(pulse in 1..3) {
            assertTrue(CoreSkillChoreography.parts(effect("temp_field",CoreSkillVisualPhase.PREPARE,pulse)).isEmpty())
            val wave=CoreSkillChoreography.parts(effect("temp_field",pulse=pulse))
            assertEquals(4,wave.size)
            assertTrue(wave.all { it.shape.startsWith("oath_wave_") && it.durationTicks==8 && !it.secondary })
        }
    }
    @Test fun `rebuke expands four quarter fronts within the actual radius and throws separate rubble`() {
        val e=effect("temp_rebuke");val parts=CoreSkillChoreography.parts(e)
        assertEquals(4,parts.count { it.shape.startsWith("oath_wave_") })
        assertEquals(6,parts.count { it.shape=="oath_stone_chip" })
        for(p in parts.filter { it.shape.startsWith("oath_wave_") }) {
            assertEquals(e.radius,p.scale.x())
            val full=CoreSkillChoreography.pose(p,5.0)
            assertEquals(e.radius,full.scale.x(),.00001)
            for(element in model(full.model).getAsJsonArray("elements")) {
                val hi=element.asJsonObject.getAsJsonArray("to")
                assertTrue(hypot((hi[0].asDouble-8)/16,(hi[2].asDouble-8)/16)<=1.00001)
            }
        }
        assertTrue(parts.filter { it.shape=="oath_stone_chip" }.all { it.bend.y()>=.9 })
    }
    @Test fun `armor rupture only appears on accepted breaker contacts and no shield becomes a spear`() {
        assertTrue(CoreSkillChoreography.parts(effect("temp_break")).none { it.shape=="oath_armor_chip" })
        val contact=CoreSkillChoreography.parts(effect("temp_break",CoreSkillVisualPhase.CONTACT))
        assertEquals(6,contact.size);assertTrue(contact.all { it.shape=="oath_armor_chip" })
        val dash=CoreSkillChoreography.parts(effect("temp_dash"))
        assertEquals("oath_shield",dash.first().shape)
        assertTrue(dash.first().travel.z()>0)
    }
    @Test fun `large wave radii keep metre scale crest thickness and hammers use branching fractures`() {
        for(radius in 1..11) for(element in model("combat_vfx/oath_wave_${radius}_gold").getAsJsonArray("elements")) {
            val lo=element.asJsonObject.getAsJsonArray("from");val hi=element.asJsonObject.getAsJsonArray("to")
            val inner=hypot((lo[0].asDouble-8)/16,(lo[2].asDouble-8)/16)*radius
            val outer=hypot((hi[0].asDouble-8)/16,(hi[2].asDouble-8)/16)*radius
            assertTrue(outer<=radius+.00001 && inner>=radius-.35)
        }
        for(id in listOf("temp_mace","temp_break")) {
            val parts=CoreSkillChoreography.parts(effect(id))
            assertEquals(5,parts.count { it.shape=="oath_fracture" })
            assertTrue(parts.none { it.shape.startsWith("oath_wave") })
        }
    }
    @Test fun `templar phases stay within primary and total display budgets`() {
        for(skill in CoreSkillCatalog.skills(CoreClass.TEMPLAR)) for(phase in CoreSkillVisualPhase.entries) {
            val parts=CoreSkillChoreography.parts(effect(skill.icon,phase))
            assertTrue(parts.size in 1..16,"${skill.icon} $phase ${parts.size}")
            assertTrue(parts.count { !it.secondary }<=8,"${skill.icon} $phase")
        }
    }
    @Test fun `export templar windup and actual pulse sequences for projection review`() {
        val ids=listOf("temp_mace","temp_break","temp_guard","temp_dash","temp_rebuke","temp_field","temp_ward","temp_sanctuary")
        fun xyz(v: Vec)=listOf(v.x(),v.y(),v.z())
        val scenes=ids.map { id ->
            val skill=effect(id).skill
            val events=mutableListOf<Pair<Int,List<CoreCombatMeshPart>>>()
            if(skill.startup>1) events+=0 to CoreSkillChoreography.parts(effect(id,CoreSkillVisualPhase.PREPARE,prepare=skill.startup-1))
            repeat(skill.pulses) { pulse ->
                val at=skill.startup+pulse*8
                if(pulse>0 && skill.motion==CoreSkillMotion.FIELD)
                    events+=at-4 to CoreSkillChoreography.parts(effect(id,CoreSkillVisualPhase.PREPARE,pulse,prepare=4))
                events+=at to CoreSkillChoreography.parts(effect(id,pulse=pulse))
            }
            val end=events.maxOf { (at,parts) -> at+(parts.maxOfOrNull { it.delayTicks+it.durationTicks }?:0) }
            mapOf("id" to id,"name" to "${skill.name} 連続","frames" to (0..end).map { tick ->
                events.flatMap { (at,parts) -> if(tick<at) emptyList() else parts.mapNotNull { p ->
                    val pose=CoreSkillChoreography.pose(p,(tick-at).toDouble())
                    if(!pose.visible) null else mapOf("model" to pose.model,"offset" to xyz(pose.offset),"scale" to xyz(pose.scale),
                        "yaw" to pose.yaw,"pitch" to pose.pitch,"roll" to pose.roll)
                } }
            })
        }
        val path=Path.of("../.tools/templar-choreography-frames.json")
        Files.createDirectories(path.parent);Files.writeString(path,Gson().toJson(scenes))
    }
}
