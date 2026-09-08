package dev.projects.server.coreloop

import com.google.gson.Gson
import com.google.gson.JsonParser
import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.math.*

class CoreSkillChoreographyTest {
    @Test fun `pull chains keep both endpoints attached as hooks converge at every heading`() {
        for(id in listOf("temp_pull","temp_ult")) repeat(8) { heading ->
            val a=heading*PI/4
            val skill=CoreSkillCatalog.skills(CoreClass.TEMPLAR).first { it.icon==id }
            val effect=CoreSkillEffect(CoreClass.TEMPLAR,skill,Vec.ZERO,Vec(sin(a),0.0,cos(a)))
            val parts=CoreSkillChoreography.parts(effect)
            assertEquals(if(skill.ultimate) 12 else 8,parts.size)
            assertEquals(8,parts.count { !it.secondary },"Other viewers must see four complete hook-and-chain pairs")
            for(pair in parts.chunked(2)) {
                val hook=pair[0];val chain=pair[1]
                var previous=Double.POSITIVE_INFINITY
                for(tick in 0 until hook.durationTicks) {
                    val tip=CoreSkillChoreography.pose(hook,tick.toDouble())
                    val line=CoreSkillChoreography.pose(chain,tick.toDouble())
                    val direction=Vec(sin(line.yaw)*cos(line.pitch),-sin(line.pitch),cos(line.yaw)*cos(line.pitch))
                    val from=line.offset.sub(direction.mul(line.scale.z()/2))
                    val to=line.offset.add(direction.mul(line.scale.z()/2))
                    assertTrue(from.distance(chain.chainAnchor!!)<.00001,"$id anchor detached at $tick")
                    assertTrue(to.distance(tip.offset)<.00001,"$id hook detached at $tick")
                    assertTrue(line.scale.z()<=previous+.00001,"$id chain must tighten, not radiate")
                    previous=line.scale.z()
                }
            }
        }
    }
    @Test fun `chain assets are hollow alternating volume not painted lines`() {
        for(count in 1..12) {
            val model=javaClass.getResourceAsStream("/core-ui-pack/assets/projects/models/combat_vfx/chain_${count}_gold.json")!!
                .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
            val parts=model.getAsJsonArray("elements").map { it.asJsonObject }
            assertEquals(count*4,parts.size)
            assertEquals(0.0,parts.minOf { it.getAsJsonArray("from")[2].asDouble })
            assertEquals(16.0,parts.maxOf { it.getAsJsonArray("to")[2].asDouble },.00001)
            assertTrue(parts.all { e -> (0..2).all { e.getAsJsonArray("to")[it].asDouble>e.getAsJsonArray("from")[it].asDouble } })
        }
    }
    @Test fun `arcane ward unfolds around torso and leaves a forward opening`() {
        val parts=CoreSkillChoreography.parts(effect(CoreClass.MAGE,"mage_ward"))
        assertEquals(4,parts.size)
        assertTrue(parts.all { it.followOwner && it.shape=="arcane_shield" })
        for(p in parts) {
            val start=CoreSkillChoreography.pose(p,p.delayTicks.toDouble())
            val open=CoreSkillChoreography.pose(p,p.delayTicks+7.0)
            assertTrue(open.offset.distance(Vec(0.0,1.0,0.0))>start.offset.distance(Vec(0.0,1.0,0.0)))
            // At full opening even the conservative half-diagonal leaves the crosshair gap.
            assertTrue(abs(open.offset.x())>open.scale.x()/2+.1)
            assertTrue(open.offset.y() in .8..1.2)
        }
    }
    @Test fun `slash pack contains crisp pixel frames without opaque tile borders`() {
        assertPixelFrames("ribbon/slash")
    }
    @Test fun `stellar burst pack contains crisp sequential frames without tile borders`() {
        assertPixelFrames("stellar/burst")
    }
    @Test fun `nebula stream pack has pixel contours and changing cloud mass`() {
        assertPixelFrames("nebula/stream")
    }
    @Test fun `nebula is staggered low moving currents not an explosion or orbit diagram`() {
        val e=effect(CoreClass.STARWEAVER,"star_cloud")
        val parts=CoreSkillChoreography.parts(e)
        val streams=parts.filter { it.atlas==CoreMeshAtlas.NEBULA_STREAM }
        assertEquals(6,streams.size)
        assertTrue(streams.map { it.offset.y() }.distinct().size>=3)
        assertEquals(setOf(0,3,6),streams.map { it.delayTicks }.toSet())
        assertTrue(parts.none { it.stellarBurst || it.sprite || it.shape=="constellation" })
        for(p in streams) {
            assertTrue(p.delayTicks+p.durationTicks==e.durationTicks)
            assertFalse(p.followOwner)
            val poses=(0 until p.durationTicks).map { CoreSkillChoreography.pose(p,(p.delayTicks+it).toDouble()) }
            assertEquals(16,poses.map { it.model }.distinct().size)
            assertTrue(poses.first().offset.distance(poses.last().offset)>.4)
            for(pose in poses) {
                assertTrue(pose.offset.y() in .3..1.2)
                assertTrue(hypot(pose.offset.x(),pose.offset.z())<e.radius)
            }
        }
    }
    @Test fun `ice garden grows anchored volume in waves without circling pillars`() {
        val parts=CoreSkillChoreography.parts(effect(CoreClass.MAGE,"mage_garden"))
        assertEquals(8,parts.size)
        assertEquals(setOf(0,2,4,6),parts.map { it.delayTicks }.toSet())
        for(p in parts) {
            assertEquals(CoreMeshMotion.EMERGE,p.motion)
            assertTrue(p.ground && !p.followOwner && !p.erode)
            val first=CoreSkillChoreography.pose(p,p.delayTicks.toDouble())
            val grown=CoreSkillChoreography.pose(p,p.delayTicks+8.0)
            val last=CoreSkillChoreography.pose(p,(p.delayTicks+p.durationTicks-1).toDouble())
            assertEquals(first.offset,grown.offset)
            assertTrue(grown.scale.z()>first.scale.z()*10)
            assertTrue(last.scale.z()<grown.scale.z()*.1)
        }
        val model=javaClass.getResourceAsStream("/core-ui-pack/assets/projects/models/combat_vfx/ice_growth_ice.json")!!
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val elements=model.getAsJsonArray("elements").map { it.asJsonObject }
        assertTrue(elements.size>=16)
        assertTrue(elements.all { e -> (0..2).all { e.getAsJsonArray("to")[it].asDouble>e.getAsJsonArray("from")[it].asDouble } })
        assertEquals(8.0,elements.minOf { it.getAsJsonArray("from")[2].asDouble })
    }
    private fun assertPixelFrames(prefix: String) {
        val frameHashes=mutableSetOf<Int>()
        var maximumInk=0
        repeat(16) { frame ->
            val path="/core-ui-pack/assets/projects/textures/combat_vfx/${prefix}_$frame.png"
            val image=javaClass.getResourceAsStream(path)!!.use { ImageIO.read(it) }
            assertEquals(64,image.width,path);assertEquals(64,image.height,path)
            val pixels=image.getRGB(0,0,64,64,null,0,64)
            val colors=pixels.toSet()
            assertTrue(colors.size<=5,"$path colors=${colors.size}")
            assertTrue(colors.all { (it ushr 24) in setOf(0,255) },"$path has soft alpha")
            for(y in 0 until 64) for(x in 0 until 64) {
                if(x<4 || x>=60 || y<4 || y>=60) assertEquals(0,pixels[y*64+x] ushr 24,"$path gutter ($x,$y)")
            }
            maximumInk=maxOf(maximumInk,pixels.count { it ushr 24>0 })
            frameHashes+=pixels.contentHashCode()
        }
        assertTrue(maximumInk in 100..1600,"Blade must be a connected readable mass, not blank or an opaque card: $maximumInk")
        assertTrue(frameHashes.size>=12,"Animation must change its artwork, not just transform a static PNG")
    }
    @Test fun `stellar bursts belong to landings and accepted ray hits not fields or defense`() {
        for(id in listOf("starfall","star_ult")) {
            val pulse=CoreSkillChoreography.parts(effect(CoreClass.STARWEAVER,id))
            val burst=pulse.filter { it.stellarBurst && !it.ground }
            assertEquals(2,burst.size,id)
            assertTrue(burst.map { it.yaw }.distinct().size==2)
            assertTrue(burst.any { it.delayTicks==2 && it.secondary })
            assertTrue(burst.all { it.durationTicks>=28 && !it.followOwner && !it.ground })
            assertTrue(pulse.any { it.ground && it.stellarBurst })
            assertFalse(pulse.any { it.shape=="astral_crack" },"Static forked ground diagram must not compete with the breakup")
            assertFalse(CoreSkillChoreography.parts(effect(CoreClass.STARWEAVER,id,CoreSkillVisualPhase.PREPARE)).any { it.stellarBurst })
        }
        for(id in listOf("star_thread","star_needle","star_break")) {
            assertFalse(CoreSkillChoreography.parts(effect(CoreClass.STARWEAVER,id)).any { it.stellarBurst },"Empty ray must not explode: $id")
            val hit=CoreSkillChoreography.parts(effect(CoreClass.STARWEAVER,id,CoreSkillVisualPhase.CONTACT))
            assertEquals(2,hit.count { it.stellarBurst },id)
            if(id=="star_needle") assertTrue(hit.any { it.shape=="star_mark" && !it.stellarBurst })
        }
        for(id in listOf("star_cloud","star_shield","star_constellation","star_step","star_ring")) {
            assertFalse(CoreSkillChoreography.parts(effect(CoreClass.STARWEAVER,id)).any { it.stellarBurst },id)
        }
        val burst=CoreSkillChoreography.parts(effect(CoreClass.STARWEAVER,"starfall")).first()
        assertTrue(CoreSkillChoreography.pose(burst,2.0).model.endsWith("_3"))
        assertTrue(CoreSkillChoreography.pose(burst,6.0).model.endsWith("_5"))
        assertTrue(CoreSkillChoreography.pose(burst,(burst.durationTicks-1).toDouble()).model.endsWith("_15"))
    }
    private fun effect(job: CoreClass,id: String,phase: CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE): CoreSkillEffect {
        val skill=CoreSkillCatalog.skills(job).first { it.icon==id }
        val ray=CoreSkillScenes.get(id).kind==CoreSceneKind.RAY
        return CoreSkillEffect(job,skill,Vec(0.0,if(ray) 1.1 else 0.0,0.0),Vec(0.0,0.0,1.0),phase,rayLength=if(ray) 5.0 else 0.0,clippedRay=ray)
    }
    @Test fun `all skills have bounded readable phrases and every animated asset resolves`() {
        assertEquals(CoreSkillScenes.all.keys,CoreSkillChoreography.sceneIds)
        val index=javaClass.getResourceAsStream("/core-ui-pack/index.txt")!!.bufferedReader().use { it.readLines().toSet() }
        val checked=mutableSetOf<String>()
        for(job in CoreClass.entries) for(s in CoreSkillCatalog.skills(job)) for(phase in CoreSkillVisualPhase.entries) {
            val e=effect(job,s.icon,phase)
            if(phase==CoreSkillVisualPhase.PULSE) assertTrue(e.durationTicks in 18..40,s.icon)
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
    @Test fun `warrior blade planes are not edge on from owner eye height`() {
        for(id in listOf("dash","war_wound","war_counter","slam","war_ult")) {
            val part=CoreSkillChoreography.parts(effect(CoreClass.WARRIOR,id)).first()
            for(tick in 1..6) {
                val p=CoreSkillChoreography.pose(part,tick.toDouble())
                // The quad normal is +Y before pitch, roll and yaw (same order as runtime).
                val x=-sin(p.roll)*cos(p.pitch);val y=cos(p.roll)*cos(p.pitch);val z=sin(p.pitch)
                val normal=Vec(x*cos(p.yaw)+z*sin(p.yaw),y,-x*sin(p.yaw)+z*cos(p.yaw))
                val sight=Vec(0.0,1.62,-.7).sub(p.offset).normalize()
                val projected=abs(normal.x()*sight.x()+normal.y()*sight.y()+normal.z()*sight.z())
                assertTrue(projected>.18,"$id tick=$tick visible-plane fraction=$projected")
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
            if(s.icon in setOf("star_thread","star_needle","star_break")) {
                val contact=CoreSkillChoreography.parts(effect(job,s.icon,CoreSkillVisualPhase.CONTACT))
                scenes+=mapOf("id" to "${s.icon}_contact","name" to "${s.name} 命中","job" to job.name,
                    "frames" to (0..contact.maxOf { it.delayTicks+it.durationTicks }).map { tick ->
                        contact.mapNotNull { p -> val pose=CoreSkillChoreography.pose(p,tick.toDouble());if(!pose.visible) null else
                            mapOf("model" to pose.model,"offset" to xyz(pose.offset.add(0.0,0.0,4.0)),"scale" to xyz(pose.scale),
                                "yaw" to pose.yaw,"pitch" to pose.pitch,"roll" to pose.roll) }
                    })
            }
        }
        val cwd=Path.of(System.getProperty("user.dir"))
        val root=if(cwd.fileName.toString()=="server-minestom") cwd.parent else cwd
        Files.createDirectories(root.resolve(".tools"))
        Files.writeString(root.resolve(".tools/skill-choreography-frames.json"),Gson().toJson(scenes))
    }
}
