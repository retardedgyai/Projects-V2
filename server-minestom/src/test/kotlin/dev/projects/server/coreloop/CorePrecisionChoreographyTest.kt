package dev.projects.server.coreloop

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.*
import kotlin.test.*

class CorePrecisionChoreographyTest {
    private fun effect(id: String,phase: CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE,length: Double=6.0,
        direction: Vec=Vec(0.0,0.0,1.0),endpoint: CoreSkillEndpoint=CoreSkillEndpoint.NONE,prepare: Int=4): CoreSkillEffect {
        val job=if(id=="ass_needle") CoreClass.ASSASSIN else CoreClass.MAGE
        val skill=CoreSkillCatalog.skills(job).first { it.icon==id }
        return CoreSkillEffect(job,skill,Vec.ZERO,direction,phase,rayLength=length,clippedRay=skill.motion==CoreSkillMotion.RAY,
            endpoint=endpoint,prepareTicks=prepare)
    }
    private val models=mutableMapOf<String,JsonObject>()
    private fun model(name: String)=models.getOrPut(name) {
        javaClass.getResourceAsStream("/core-ui-pack/assets/projects/models/$name.json")!!
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
    }
    @Test fun `both marked shots keep every animation vertex inside their clipped segment at every pitch`() {
        for(id in listOf("mage_mark","ass_needle")) for(length in listOf(.06,.5,3.0,24.0))
            for(dir in listOf(Vec(0.0,0.0,1.0),Vec(0.0,1.0,0.0),Vec(.4,-.7,-.6).normalize())) {
                val parts=CoreSkillChoreography.parts(effect(id,length=length,direction=dir))
                assertEquals(if(id=="ass_needle") 2 else 1,parts.size)
                assertTrue(parts.none { it.shape in setOf("arrow_trail","electric_thread") })
                for(p in parts) for(tick in 0 until p.durationTicks) {
                    val pose=CoreSkillChoreography.pose(p,tick.toDouble());val center=pose.offset.dot(dir)
                    assertEquals(atan2(dir.x(),dir.z()),pose.yaw,.00001)
                    assertEquals(-atan2(dir.y(),hypot(dir.x(),dir.z())),pose.pitch,.00001)
                    for(element in model(pose.model).getAsJsonArray("elements")) for(edge in listOf("from","to")) {
                        val z=element.asJsonObject.getAsJsonArray(edge)[2].asDouble
                        assertTrue(center+(z/16-.5)*pose.scale.z() in -.00001..length+.00001)
                    }
                }
                assertTrue(CoreSkillChoreography.parts(effect(id,length=.01)).isEmpty())
            }
    }
    @Test fun `lightning detail follows distance and both bolt families lose geometry through their aftermath`() {
        for(count in listOf(1,4,16)) {
            val prefix="combat_vfx/precision/bolt_${count}_"
            val sizes=(0..7).map { model("$prefix$it").getAsJsonArray("elements").size() }
            assertTrue(sizes[0]>=count*16)
            assertTrue((3 until 7).all { sizes[it]>sizes[it+1] },sizes.toString())
            assertTrue(sizes[7]<sizes[0]/3)
        }
        val sizes=(0..7).map { model("combat_vfx/storm_branch_lightning_$it").getAsJsonArray("elements").size() }
        assertTrue((3 until 7).all { sizes[it]>sizes[it+1] },sizes.toString())
        val p=CoreSkillChoreography.parts(effect("mage_mark")).single()
        assertEquals(8,(0 until p.durationTicks).map { CoreSkillChoreography.pose(p,it.toDouble()).model }.toSet().size)
    }
    @Test fun `lightning bends remain thin stepped conductors instead of filled rectangular plates`() {
        for(frame in 0..7) for(name in listOf("combat_vfx/precision/bolt_16_$frame","combat_vfx/storm_branch_lightning_$frame")) {
            val elements=model(name).getAsJsonArray("elements")
            assertTrue(elements.size()<3000,"Unbounded native model complexity: $name")
            for(element in elements) {
                val lo=element.asJsonObject.getAsJsonArray("from")
                val hi=element.asJsonObject.getAsJsonArray("to")
                assertTrue(hi[0].asDouble-lo[0].asDouble<=1.200001,"Wide plate in $name")
                assertTrue(hi[1].asDouble-lo[1].asDouble<=1.200001,"Tall plate in $name")
            }
        }
    }
    @Test fun `needle rift uses sixteen distinct opaque coarse pixel frames with empty margins`() {
        val hashes=mutableSetOf<Int>();val areas=mutableListOf<Int>()
        for(frame in 0..15) {
            val img=ImageIO.read(javaClass.getResourceAsStream("/core-ui-pack/assets/projects/textures/combat_vfx/precision/needle_$frame.png"))
            assertEquals(96,img.width);assertEquals(64,img.height)
            var area=0
            for(y in 0 until 64) for(x in 0 until 96) {
                val pixel=img.getRGB(x,y);val alpha=pixel ushr 24
                assertTrue(alpha==0 || alpha==255)
                assertEquals(img.getRGB(x/2*2,y/2*2),pixel)
                if(alpha>0) { area++;assertTrue((pixel and 255) in setOf(85,170,255));assertTrue(x in 8..87 && y in 8..55) }
            }
            assertTrue(area>0,"Frame $frame disappeared during import")
            areas+=area;hashes+=img.getRGB(0,0,96,64,null,0,96).contentHashCode()
        }
        assertEquals(16,hashes.size);assertTrue(areas.take(5).max()>areas.last()*3)
        val p=CoreSkillChoreography.parts(effect("ass_needle")).first()
        assertEquals(3,CorePrecisionChoreography.needleFrame(p,3.0))
        assertEquals(15,CorePrecisionChoreography.needleFrame(p,21.0))
        for(tick in 0 until p.durationTicks) {
            val m=model(CoreSkillChoreography.pose(p,tick.toDouble()).model)
            assertEquals(6,m.getAsJsonArray("elements").size()) // 6m / 2m, two planes per section
            assertTrue(m.getAsJsonObject("textures").entrySet().all { it.value.asString.contains("precision/needle_") })
        }
    }
    @Test fun `marks appear only in contact and lightning contact differs from the shadow puncture`() {
        for(id in listOf("mage_mark","ass_needle")) {
            val mark=CoreSkillScenes.get(id).impact
            val shot=CoreSkillChoreography.parts(effect(id))
            assertTrue(shot.none { it.shape==mark || it.stellarBurst })
            val hit=CoreSkillChoreography.parts(effect(id,CoreSkillVisualPhase.CONTACT))
            assertEquals(1,hit.count { it.shape==mark })
            assertTrue(hit.all { it.durationTicks>=18 && !it.followOwner })
            assertEquals(id=="mage_mark",hit.any { it.shape=="storm_branch" })
            assertEquals(id=="ass_needle",hit.any { it.shape=="needle_rift" })
        }
    }
    @Test fun `needle contact split rotates its origin and drift together with the shot heading`() {
        val baseline=CoreSkillChoreography.parts(effect("ass_needle",CoreSkillVisualPhase.CONTACT))
            .filter { it.shape=="needle_rift" }
        for(yaw in listOf(-PI,-PI/2,.7,PI/2,PI)) {
            val direction=Vec(sin(yaw),0.0,cos(yaw))
            val rotated=CoreSkillChoreography.parts(effect("ass_needle",CoreSkillVisualPhase.CONTACT,direction=direction))
                .filter { it.shape=="needle_rift" }
            for((base,part) in baseline.zip(rotated)) for(tick in 0 until part.durationTicks) {
                val a=CoreSkillChoreography.pose(base,tick.toDouble()).offset
                val b=CoreSkillChoreography.pose(part,tick.toDouble()).offset
                assertEquals(cos(yaw)*a.x()+sin(yaw)*a.z(),b.x(),.000001)
                assertEquals(a.y(),b.y(),.000001)
                assertEquals(-sin(yaw)*a.x()+cos(yaw)*a.z(),b.z(),.000001)
            }
        }
    }
    @Test fun `lightning step closes into departure and cracks outward at arrival without a filled gate`() {
        for(endpoint in listOf(CoreSkillEndpoint.DEPARTURE,CoreSkillEndpoint.ARRIVAL)) {
            val e=effect("mage_blink",endpoint=endpoint);val parts=CoreSkillChoreography.parts(e)
            assertEquals(6,parts.size);assertEquals(4,parts.count { !it.secondary })
            assertTrue(parts.none { it.sprite || it.shape=="lightning_gate" || it.followOwner })
            for(p in parts.take(4)) {
                val start=CoreSkillChoreography.pose(p,0.0);val end=CoreSkillChoreography.pose(p,23.0)
                assertEquals(endpoint==CoreSkillEndpoint.DEPARTURE,abs(end.offset.x())<abs(start.offset.x()))
                assertEquals(endpoint==CoreSkillEndpoint.DEPARTURE,end.scale.x()<start.scale.x())
                assertEquals(24,p.durationTicks)
                assertEquals(8,(0..23).map { CoreSkillChoreography.pose(p,it.toDouble()).model }.toSet().size)
            }
        }
    }
    @Test fun `fast preparation stays positive and dedicated phrases fit existing entity budgets`() {
        for(id in CorePrecisionChoreography.sceneIds) {
            assertTrue(CoreSkillChoreography.parts(effect(id)).size<=6)
            for(life in 1..4) for(p in CoreSkillChoreography.parts(effect(id,CoreSkillVisualPhase.PREPARE,prepare=life))) {
                assertEquals(life,p.durationTicks);assertTrue(p.followOwner)
                assertTrue(CoreSkillChoreography.pose(p,0.0).visible)
                assertFalse(CoreSkillChoreography.pose(p,life.toDouble()).visible)
            }
        }
    }
    @Test fun `export precision casts both transfer endpoints and accepted contacts`() {
        fun xyz(v: Vec)=listOf(v.x(),v.y(),v.z())
        val scenes=(CorePrecisionChoreography.sceneIds.toList()+listOf("mage_blink_arrival","mage_mark_contact","ass_needle_contact")).map { output ->
            val id=output.removeSuffix("_arrival").removeSuffix("_contact");val skill=effect(id).skill
            val contact=output.endsWith("_contact")
            val events=mutableListOf<Pair<Int,List<CoreCombatMeshPart>>>()
            if(!contact && skill.startup>1) events+=0 to CoreSkillChoreography.parts(effect(id,CoreSkillVisualPhase.PREPARE,prepare=skill.startup-1))
            val endpoint=if(id!="mage_blink") CoreSkillEndpoint.NONE else
                if(output.endsWith("_arrival")) CoreSkillEndpoint.ARRIVAL else CoreSkillEndpoint.DEPARTURE
            val origin=if(contact) Vec(0.0,0.0,3.0) else if(skill.motion==CoreSkillMotion.RAY) Vec(0.0,1.0,0.0) else Vec.ZERO
            events+=(if(contact) 0 else skill.startup) to CoreSkillChoreography.parts(effect(id,
                phase=if(contact) CoreSkillVisualPhase.CONTACT else CoreSkillVisualPhase.PULSE,endpoint=endpoint))
                .map { it.copy(offset=it.offset.add(origin)) }
            val end=events.maxOf { (at,parts) -> at+parts.maxOf { it.delayTicks+it.durationTicks } }
            mapOf("id" to output,"name" to skill.name+if(contact) " 命中" else if(output.endsWith("_arrival")) " 到着" else "",
                "frames" to (0..end).map { tick -> events.flatMap { (at,parts) ->
                    if(tick<at) emptyList() else parts.mapNotNull { p ->
                        val pose=CoreSkillChoreography.pose(p,(tick-at).toDouble())
                        if(!pose.visible) null else mapOf("model" to pose.model,"offset" to xyz(pose.offset),"scale" to xyz(pose.scale),
                            "yaw" to pose.yaw,"pitch" to pose.pitch,"roll" to pose.roll)
                    }
                } })
        }
        val path=Path.of("../.tools/precision-cast-frames.json")
        Files.createDirectories(path.parent);Files.writeString(path,Gson().toJson(scenes))
    }
}
