package dev.projects.server.coreloop

import com.google.gson.Gson
import com.google.gson.JsonParser
import dev.projects.server.particle.RecordingParticleSink
import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.*
import kotlin.test.*

class CoreSkillSceneGeometryTest {
    private fun effect(job: CoreClass,id: String,dir: Vec=Vec(0.0,0.0,1.0),phase: CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE,
        endpoint: CoreSkillEndpoint=CoreSkillEndpoint.NONE): CoreSkillEffect {
        val s=CoreSkillCatalog.skills(job).first { it.icon==id }
        val ray=CoreSkillScenes.get(id).kind==CoreSceneKind.RAY
        return CoreSkillEffect(job,s,Vec(0.0,if(ray) 1.0 else 0.0,0.0),dir,phase,rayLength=if(ray) 4.0 else 0.0,clippedRay=ray,endpoint=endpoint)
    }
    private fun vertices(p: CoreCombatMeshPart): List<Vec> {
        val path="/core-ui-pack/assets/projects/models/combat_vfx/${p.shape}_${p.palette}.json"
        val root=javaClass.getResourceAsStream(path)!!.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        return root.getAsJsonArray("elements").flatMap { element ->
            val lo=element.asJsonObject.getAsJsonArray("from");val hi=element.asJsonObject.getAsJsonArray("to")
            (0..7).map { bits -> Vec((if(bits and 1!=0) hi[0] else lo[0]).asDouble/16-.5,
                (if(bits and 2!=0) hi[1] else lo[1]).asDouble/16-.5,(if(bits and 4!=0) hi[2] else lo[2]).asDouble/16-.5) }
        }
    }
    private fun rotate(v: Vec,q: FloatArray): Vec {
        fun cross(a: Vec,b: Vec)=Vec(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x())
        val u=Vec(q[0].toDouble(),q[1].toDouble(),q[2].toDouble())
        val uv=cross(u,v)
        return v.add(uv.mul(2*q[3].toDouble())).add(cross(u,uv).mul(2.0))
    }
    private fun world(v: Vec,p: CoreCombatMeshPart,t: Double): Vec {
        val vanilla=rotate(v,floatArrayOf(0f,1f,0f,0f))
        val corrected=rotate(vanilla,CoreCombatMeshArt.vanillaItemCorrection)
        val size=p.sizeAt(t)
        val scaled=Vec(corrected.x()*p.scale.x()*size,corrected.y()*p.scale.y()*size,corrected.z()*p.scale.z()*size)
        return rotate(scaled,CoreCombatMeshArt.rotation(p.yaw+p.spin*t,p.pitch,p.roll)).add(p.offset).add(p.travel.mul(t))
    }

    @Test fun `all skills and three normal steps have explicit individual contracts`() {
        assertEquals((CoreSkillCatalog.artNames+listOf("normal_sweep","normal_reverse","normal_finish")).toSet(),CoreSkillScenes.all.keys)
        assertTrue(CoreSkillScenes.all.values.flatMap { listOf(it.body,it.accent,it.windup,it.impact) }.distinct().size>=85)
        for(job in CoreClass.entries) for(s in CoreSkillCatalog.skills(job)) {
            val scene=CoreSkillScenes.get(s.icon)
            assertEquals(s.name,scene.name)
            assertTrue(scene.intent.length>15)
            assertTrue(scene.reach in .4..5.0)
            val used=CoreSkillVisualPhase.entries.flatMap { CoreCombatMeshArt.parts(effect(job,s.icon,phase=it)) }.map { it.shape }.toSet()
            assertTrue(used.containsAll(listOf(scene.body,scene.accent,scene.windup,scene.impact)),"Unused declared layer: ${scene.id}")
        }
    }

    @Test fun `actual model vertices remain ahead for every cardinal and diagonal melee cast`() {
        for(job in CoreClass.entries) for(s in CoreSkillCatalog.skills(job)) {
            if(CoreSkillScenes.get(s.icon).kind !in setOf(CoreSceneKind.CUT,CoreSceneKind.CLEAVE,CoreSceneKind.THRUST,CoreSceneKind.HAMMER)) continue
            for(i in 0..7) {
                val a=i*PI/4;val dir=Vec(sin(a),0.0,cos(a))
                val part=CoreCombatMeshArt.parts(effect(job,s.icon,dir)).first()
                for(t in listOf(0.0,.5,1.0)) for(v in vertices(part)) {
                    val p=world(v,part,t)
                    assertTrue(p.x()*dir.x()+p.z()*dir.z()>=-.03,"${s.icon} behind player: $p direction $dir t=$t")
                    assertTrue(hypot(p.x(),p.z())<=3.6,"Oversized weapon body ${s.icon}: $p")
                    assertTrue(p.y()>=-.15,"${s.icon} blade below floor: $p")
                }
            }
        }
    }

    @Test fun `rays stay on their actual three dimensional clipped segment including looking up`() {
        val directions=listOf(Vec(0.0,0.0,1.0),Vec(1.0,0.0,0.0),Vec(0.0,1.0,0.0),Vec(.4,-.7,-.6).normalize())
        for(job in CoreClass.entries) for(s in CoreSkillCatalog.skills(job)) {
            if(CoreSkillScenes.get(s.icon).kind!=CoreSceneKind.RAY) continue
            for(dir in directions) for(part in CoreCombatMeshArt.parts(effect(job,s.icon,dir))) for(t in listOf(0.0,.5,1.0)) {
                for(v in vertices(part)) {
                    val p=world(v,part,t);val along=p.x()*dir.x()+p.y()*dir.y()+p.z()*dir.z()
                    assertTrue(along in -.001..4.001,"${s.icon} outside visible ray: $along")
                }
            }
        }
    }

    @Test fun `teleport endpoints have opposite opening and closing motion and no blades`() {
        for((job,id) in listOf(CoreClass.MAGE to "mage_blink",CoreClass.ASSASSIN to "ass_escape",CoreClass.HEALER to "heal_step",CoreClass.STARWEAVER to "star_step")) {
            val from=CoreCombatMeshArt.parts(effect(job,id,endpoint=CoreSkillEndpoint.DEPARTURE)).first()
            val to=CoreCombatMeshArt.parts(effect(job,id,endpoint=CoreSkillEndpoint.ARRIVAL)).first()
            assertTrue(from.shape.endsWith("gate"));assertEquals(from.shape,to.shape)
            assertTrue(from.startSize>from.endSize);assertTrue(to.startSize<to.endSize)
            assertEquals(-PI/2,from.pitch)
        }
    }

    @Test fun `pull pieces move inward and rain reaches the same point as its actual pulse`() {
        val pull=CoreCombatMeshArt.parts(effect(CoreClass.TEMPLAR,"temp_ult"))
        pull.filterNot { it.secondary }.forEach { assertTrue(it.offset.add(it.travel).length()<it.offset.length()) }
        for((job,id) in listOf(CoreClass.MAGE to "meteor",CoreClass.RANGER to "arrow_rain",CoreClass.STARWEAVER to "starfall")) {
            val prep=CoreCombatMeshArt.parts(effect(job,id,phase=CoreSkillVisualPhase.PREPARE)).first()
            val pulse=CoreCombatMeshArt.parts(effect(job,id)).first()
            assertTrue(prep.offset.add(prep.travel).distance(pulse.offset)<1e-6)
        }
    }

    @Test fun `lantern light stays upright star wards stay on body and judgment points down`() {
        val lantern=CoreCombatMeshArt.parts(effect(CoreClass.HEALER,"heal_lamp"))
        assertTrue(lantern.first().offset.y()>2.0)
        assertEquals(-PI/2,lantern.last().pitch)
        assertFalse(lantern.last().ground)
        val star=CoreCombatMeshArt.parts(effect(CoreClass.STARWEAVER,"star_constellation"))
        assertTrue(star.all { it.followOwner && !it.ground })
        assertTrue(star.first().offset.y()>1.8)
        assertEquals(PI/2,CoreCombatMeshArt.parts(effect(CoreClass.HEALER,"heal_judgment")).first().pitch)
        assertEquals("shield_wave",CoreCombatMeshArt.parts(effect(CoreClass.TEMPLAR,"temp_rebuke")).first().shape)
    }

    @Test fun `packed particles stay secondary and runtime scene poses export for visual QA`() {
        val poses=mutableListOf<Map<String,Any>>()
        for(job in CoreClass.entries) for(s in CoreSkillCatalog.skills(job)) {
            val e=effect(job,s.icon);e.solidCompanion=true
            repeat(e.durationTicks) { tick -> val sink=RecordingParticleSink();e.emit(tick,sink);assertTrue(sink.spawns.size<=60) }
            poses+=mapOf("id" to s.icon,"name" to s.name,"parts" to CoreCombatMeshArt.parts(e).map { p ->
                mapOf("shape" to p.shape,"palette" to p.palette,"scale" to listOf(p.scale.x(),p.scale.y(),p.scale.z()),
                    "offset" to listOf(p.offset.x()+e.origin.x(),p.offset.y()+e.origin.y(),p.offset.z()+e.origin.z()),"yaw" to p.yaw,"pitch" to p.pitch,"roll" to p.roll,
                    "spin" to p.spin,"travel" to listOf(p.travel.x(),p.travel.y(),p.travel.z()),"start" to p.startSize,"end" to p.endSize)
            })
        }
        val root=Path.of(System.getProperty("user.dir")).let { if(it.fileName.toString()=="server-minestom") it.parent else it }
        val target=root.resolve(".tools/skill-scene-poses.json")
        Files.createDirectories(target.parent);Files.writeString(target,Gson().toJson(poses))
    }
}
