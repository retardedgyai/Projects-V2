package dev.projects.server.coreloop

import com.google.gson.Gson
import com.google.gson.JsonParser
import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.*
import kotlin.test.*

class CoreHealerChoreographyTest {
    private fun effect(id: String,phase: CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE,pulse: Int=0,
        prepare: Int=4,length: Double=5.0,direction: Vec=Vec(0.0,0.0,1.0),
        endpoint: CoreSkillEndpoint=CoreSkillEndpoint.NONE): CoreSkillEffect {
        val skill=CoreSkillCatalog.skills(CoreClass.HEALER).first { it.icon==id }
        return CoreSkillEffect(CoreClass.HEALER,skill,Vec.ZERO,direction,phase,pulse,prepare,
            rayLength=length,clippedRay=skill.motion==CoreSkillMotion.RAY,endpoint=endpoint)
    }
    private fun model(name: String)=javaClass.getResourceAsStream("/core-ui-pack/assets/projects/models/$name.json")!!
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
    @Test fun `purification is a coarse opaque pixel flipbook with an early peak and a broken ending`() {
        val hashes=mutableSetOf<Int>()
        val areas=(0..15).map { frame ->
            val image=ImageIO.read(javaClass.getResourceAsStream("/core-ui-pack/assets/projects/textures/combat_vfx/prayer/light_$frame.png"))
            assertEquals(32,image.width);assertEquals(64,image.height)
            var area=0
            for(y in 0..63) for(x in 0..31) {
                val pixel=image.getRGB(x,y);val alpha=pixel ushr 24
                assertTrue(alpha==0 || alpha==255)
                assertEquals(image.getRGB(x/2*2,y/2*2),pixel,"not a 2x2 cluster")
                if(alpha>0) { area++;assertTrue((pixel and 255) in setOf(85,170,255));assertTrue(x in 4..27 && y in 4..59) }
            }
            hashes+=image.getRGB(0,0,32,64,null,0,32).contentHashCode();area
        }
        assertEquals(16,hashes.size)
        assertTrue(areas.take(6).max()>areas.last()*3)
        val p=CoreSkillChoreography.parts(effect("heal_pillar")).first()
        assertEquals(3,CoreHealerChoreography.frame(p,3.0))
        assertEquals(15,CoreHealerChoreography.frame(p,(p.durationTicks-1).toDouble()))
    }
    @Test fun `holy rays stay on the clipped segment for horizontal vertical and downward aim`() {
        for(id in listOf("heal_light","heal_mark")) for(length in listOf(.06,.5,5.0,24.0))
            for(direction in listOf(Vec(0.0,0.0,1.0),Vec(0.0,1.0,0.0),Vec(.4,-.7,-.6).normalize())) {
                val p=CoreSkillChoreography.parts(effect(id,length=length,direction=direction)).single()
                assertEquals("prayer_ray",p.shape);assertFalse(p.secondary)
                for(tick in 0 until p.durationTicks) {
                    val pose=CoreSkillChoreography.pose(p,tick.toDouble())
                    val center=pose.offset.x()*direction.x()+pose.offset.y()*direction.y()+pose.offset.z()*direction.z()
                    val elements=model(pose.model).getAsJsonArray("elements")
                    assertEquals(ceil(length/1.3).toInt().coerceIn(1,24)*2,elements.size())
                    for(element in elements) for(edge in listOf("from","to")) {
                        val along=center+(element.asJsonObject.getAsJsonArray(edge)[2].asDouble/16-.5)*pose.scale.z()
                        assertTrue(along in -.00001..length+.00001)
                    }
                }
            }
    }
    @Test fun `judgment descent ends at the pulse sword tip on every actual wave`() {
        val roots=mutableSetOf<Vec>()
        for(pulse in 0..3) for(prepare in listOf(1,4,17)) {
            val wind=CoreSkillChoreography.parts(effect("heal_judgment",CoreSkillVisualPhase.PREPARE,pulse,prepare)).single()
            val hit=CoreSkillChoreography.parts(effect("heal_judgment",pulse=pulse)).first()
            val end=CoreSkillChoreography.pose(wind,(prepare-1).toDouble())
            val start=CoreSkillChoreography.pose(hit,0.0)
            assertTrue(end.offset.distance(start.offset)<.00001)
            assertEquals(.12,start.offset.y()-start.scale.z()*.5,.00001)
            assertFalse(CoreSkillChoreography.pose(hit,6.0).visible)
            roots+=start.offset
        }
        assertEquals(4,roots.size)
    }
    @Test fun `rising pillars differ from the downward illumination of the floating lamp`() {
        val pillars=CoreSkillChoreography.parts(effect("heal_pillar"))
        assertEquals(3,pillars.size)
        assertTrue(pillars.all { it.shape=="purifying_column" && it.pitch==-PI/2 && it.ground && it.scale.z()>=2.0 })
        assertEquals(setOf(0,2,4),pillars.map { it.delayTicks }.toSet())
        val lamp=CoreSkillChoreography.parts(effect("heal_lamp",pulse=1)).single()
        assertEquals(PI/2,lamp.pitch)
        assertEquals(.2,lamp.offset.y()-lamp.scale.z(),.00001)
    }
    @Test fun `one lamp and flame persist across three pulses without duplicate lanterns`() {
        val parts=CoreSkillChoreography.parts(effect("heal_lamp"))
        assertEquals(3,parts.size)
        val lamp=parts.single { it.shape=="prayer_lantern" };val flame=parts.single { it.shape=="prayer_flame" }
        assertEquals(36,lamp.durationTicks);assertEquals(lamp.durationTicks,flame.durationTicks)
        for(tick in 0 until 36) {
            val a=CoreSkillChoreography.pose(lamp,tick.toDouble());val b=CoreSkillChoreography.pose(flame,tick.toDouble())
            assertEquals(.25,b.offset.y()-a.offset.y(),.00001)
        }
        assertEquals(CoreHealerChoreography.frame(flame,2.0),CoreHealerChoreography.frame(flame,10.0))
        assertEquals(15,CoreHealerChoreography.frame(flame,35.0))
        for(pulse in 1..2) {
            assertTrue(CoreSkillChoreography.parts(effect("heal_lamp",CoreSkillVisualPhase.PREPARE,pulse)).isEmpty())
            assertEquals(1,CoreSkillChoreography.parts(effect("heal_lamp",pulse=pulse)).size)
        }
    }
    @Test fun `guidance closes around an accepted target while ordinary light contact disperses outward`() {
        assertTrue(CoreSkillChoreography.parts(effect("heal_mark")).none { it.shape=="guidance_petal" })
        for(id in listOf("heal_mark","heal_light")) {
            val parts=CoreSkillChoreography.parts(effect(id,CoreSkillVisualPhase.CONTACT))
            assertEquals(4,parts.size)
            for(p in parts) {
                val a=CoreSkillChoreography.pose(p,0.0).offset.sub(Vec(0.0,1.0,0.0)).length()
                val b=CoreSkillChoreography.pose(p,(p.durationTicks-1).toDouble()).offset.sub(Vec(0.0,1.0,0.0)).length()
                assertEquals(id=="heal_mark",b<a)
            }
        }
    }
    @Test fun `light step folds down at departure and fans upward on arrival including short preparation`() {
        for(endpoint in listOf(CoreSkillEndpoint.DEPARTURE,CoreSkillEndpoint.ARRIVAL)) {
            val e=effect("heal_step",endpoint=endpoint);val parts=CoreSkillChoreography.parts(e)
            assertEquals(6,parts.size);assertEquals(4,parts.count { !it.secondary })
            assertTrue(parts.all { !it.followOwner && it.delayTicks+it.durationTicks==e.durationTicks })
            assertTrue(parts.all { if(endpoint==CoreSkillEndpoint.DEPARTURE) it.travel.y()<0 else it.travel.y()>0 })
        }
        for(id in CoreHealerChoreography.sceneIds) for(prepare in 1..4) {
            val parts=CoreSkillChoreography.parts(effect(id,CoreSkillVisualPhase.PREPARE,prepare=prepare))
            assertTrue(parts.all { it.durationTicks>0 && it.delayTicks+it.durationTicks==prepare })
        }
    }
    @Test fun `export actual healer windup and repeated pulse sequences for projection review`() {
        val ids=CoreHealerChoreography.sceneIds.toList()+"heal_step_arrival"
        fun xyz(v: Vec)=listOf(v.x(),v.y(),v.z())
        val scenes=ids.map { outputId ->
            val id=outputId.removeSuffix("_arrival");val skill=effect(id).skill
            val events=mutableListOf<Pair<Int,List<CoreCombatMeshPart>>>()
            if(skill.startup>1) events+=0 to CoreSkillChoreography.parts(effect(id,CoreSkillVisualPhase.PREPARE,prepare=skill.startup-1))
            repeat(skill.pulses) { pulse ->
                val at=skill.startup+pulse*8
                if(pulse>0 && skill.motion==CoreSkillMotion.FIELD)
                    events+=at-4 to CoreSkillChoreography.parts(effect(id,CoreSkillVisualPhase.PREPARE,pulse,prepare=4))
                val endpoint=if(id!="heal_step") CoreSkillEndpoint.NONE else
                    if(outputId.endsWith("_arrival")) CoreSkillEndpoint.ARRIVAL else CoreSkillEndpoint.DEPARTURE
                val origin=if(skill.motion==CoreSkillMotion.RAY) Vec(0.0,1.0,0.0) else Vec.ZERO
                events+=at to CoreSkillChoreography.parts(effect(id,pulse=pulse,endpoint=endpoint)).map { it.copy(offset=it.offset.add(origin)) }
            }
            val end=events.maxOf { (at,parts) -> at+(parts.maxOfOrNull { it.delayTicks+it.durationTicks }?:0) }
            mapOf("id" to outputId,"name" to skill.name+if(outputId.endsWith("_arrival")) " 到着" else " 連続",
                "frames" to (0..end).map { tick -> events.flatMap { (at,parts) ->
                    if(tick<at) emptyList() else parts.mapNotNull { p ->
                        val pose=CoreSkillChoreography.pose(p,(tick-at).toDouble())
                        if(!pose.visible) null else mapOf("model" to pose.model,"offset" to xyz(pose.offset),"scale" to xyz(pose.scale),
                            "yaw" to pose.yaw,"pitch" to pose.pitch,"roll" to pose.roll)
                    }
                } })
        }
        val path=Path.of("../.tools/healer-prayer-frames.json")
        Files.createDirectories(path.parent);Files.writeString(path,Gson().toJson(scenes))
    }
}
