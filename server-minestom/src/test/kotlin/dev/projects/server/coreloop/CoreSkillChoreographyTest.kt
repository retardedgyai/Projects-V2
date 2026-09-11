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
    @Test fun `shadow smoke frames retain hard pixel clusters and empty gutters`() {
        assertPixelFrames("shadow/smoke")
    }
    @Test fun `assassin thrust wake stays attached to the dagger tip at every heading`() {
        for(id in listOf("ass_stab","ass_chase","ass_contract")) repeat(8) { heading ->
            val skill=CoreSkillCatalog.skills(CoreClass.ASSASSIN).first { it.icon==id }
            val a=heading*PI/4
            val forward=Vec(sin(a),0.0,cos(a))
            val e=CoreSkillEffect(CoreClass.ASSASSIN,skill,Vec.ZERO,forward)
            val parts=CoreSkillChoreography.parts(e)
            assertEquals(when(id) { "ass_stab" -> 2; "ass_chase" -> 4; else -> 6 },parts.size)
            assertTrue(parts.none { it.sprite || it.atlas==CoreMeshAtlas.SHADOW_SMOKE })
            val blade=parts[0];val wake=parts[1]
            val r=CoreSkillScenes.get(id).reach
            for(tick in 0 until e.durationTicks) {
                val p=CoreSkillChoreography.pose(blade,tick.toDouble())
                val w=CoreSkillChoreography.pose(wake,tick.toDouble())
                val bladeTip=p.offset.add(forward.mul(p.scale.z()*.5))
                val wakeTip=w.offset.add(forward.mul(w.scale.z()*.5))
                assertTrue(bladeTip.distance(wakeTip)<.00001,"$id heading=$heading tick=$tick")
                assertTrue(w.offset.sub(forward.mul(w.scale.z()*.5)).distance(wake.offset)<.00001)
                assertTrue(hypot(bladeTip.x(),bladeTip.z())<=r+.00001)
            }
            assertTrue(CoreSkillChoreography.pose(blade,5.0).offset.distance(blade.offset)>.5)
        }
    }
    @Test fun `shadow departure closes while arrival opens and smoke has a separate lifetime`() {
        val skill=CoreSkillCatalog.skills(CoreClass.ASSASSIN).first { it.icon=="ass_escape" }
        for(endpoint in listOf(CoreSkillEndpoint.DEPARTURE,CoreSkillEndpoint.ARRIVAL)) {
            val e=CoreSkillEffect(CoreClass.ASSASSIN,skill,Vec.ZERO,Vec(0.0,0.0,1.0),endpoint=endpoint)
            val parts=CoreSkillChoreography.parts(e)
            assertEquals(4,parts.size)
            val gate=parts.first()
            val start=CoreSkillChoreography.pose(gate,0.0);val end=CoreSkillChoreography.pose(gate,7.0)
            assertEquals(endpoint==CoreSkillEndpoint.DEPARTURE,end.scale.x()<start.scale.x())
            assertEquals(12,gate.durationTicks)
            for(p in parts.drop(1)) {
                assertEquals(CoreMeshAtlas.SHADOW_SMOKE,p.atlas)
                assertFalse(p.followOwner)
                assertEquals(e.durationTicks,p.durationTicks+p.delayTicks)
                assertEquals(16,(0 until p.durationTicks).map { CoreSkillChoreography.pose(p,(p.delayTicks+it).toDouble()).model }.toSet().size)
                assertTrue(CoreSkillChoreography.pose(p,(p.delayTicks+p.durationTicks-1).toDouble()).offset.y()>p.offset.y())
            }
        }
    }
    @Test fun `afterimages move away from the body and break into smoke instead of following as shields`() {
        val e=effect(CoreClass.ASSASSIN,"ass_guard")
        val parts=CoreSkillChoreography.parts(e)
        assertEquals(6,parts.size)
        val echoes=parts.filter { it.shape=="shadow_echo" }
        assertEquals(2,echoes.size)
        assertEquals(4,parts.count { it.atlas==CoreMeshAtlas.SHADOW_SMOKE })
        val foreground=parts.filter { it.atlas==CoreMeshAtlas.SHADOW_SMOKE && !it.secondary }
        assertEquals(2,foreground.size)
        assertTrue(foreground.all { it.offset.z()>.3 && it.travel.z()>0 && abs(it.offset.x())>=.8 },
            "The owner needs visible hand-side smoke, not only phantoms behind the camera")
        assertTrue(parts.none { it.followOwner || it.shape=="afterimage" || it.shape=="shadow_gate" })
        for(p in echoes) {
            val states=(0 until p.durationTicks).map { CoreSkillChoreography.pose(p,(p.delayTicks+it).toDouble()) }
            assertEquals(8,states.map { it.model }.toSet().size)
            assertTrue(abs(states.last().offset.x())>abs(states.first().offset.x())+.7)
            assertTrue(states.last().offset.z()<states.first().offset.z()-.6)
            assertEquals(e.durationTicks,p.durationTicks+p.delayTicks)
        }
    }
    @Test fun `venom is a closing paired bite with falling liquid not a green sword ribbon`() {
        val e=effect(CoreClass.ASSASSIN,"ass_poison")
        val parts=CoreSkillChoreography.parts(e)
        assertEquals(7,parts.size)
        assertTrue(parts.none { it.sprite || it.atlas==CoreMeshAtlas.SHADOW_SMOKE })
        for(p in parts.take(2)) {
            val peak=CoreSkillChoreography.pose(p,5.0)
            assertTrue(abs(peak.offset.x())<abs(p.offset.x())*.5)
            assertTrue(peak.offset.z()>p.offset.z())
        }
        for(p in parts.drop(2)) {
            val end=CoreSkillChoreography.pose(p,(p.delayTicks+p.durationTicks-1).toDouble())
            assertTrue(end.offset.y()<p.offset.y()-.7)
            assertEquals(e.durationTicks,p.delayTicks+p.durationTicks)
        }
        val contact=CoreSkillChoreography.parts(effect(CoreClass.ASSASSIN,"ass_poison",CoreSkillVisualPhase.CONTACT))
        assertEquals(5,contact.size)
        assertTrue(contact.all { it.shape=="venom_bead" && it.durationTicks==18 })
    }
    @Test fun `phantom poses and venom models are bounded solid geometry`() {
        val names=(0..7).map { "shadow_echo_shadow_$it" }+
            listOf("venom_fang_venom","venom_fang_reverse_venom","venom_bead_venom","piercing_wake_shadow")
        for(name in names) {
            val model=javaClass.getResourceAsStream("/core-ui-pack/assets/projects/models/combat_vfx/$name.json")!!
                .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
            val elements=model.getAsJsonArray("elements")
            assertTrue(elements.size()>=if(name.startsWith("shadow_echo")) 6 else 10)
            for(element in elements) {
                val lo=element.asJsonObject.getAsJsonArray("from");val hi=element.asJsonObject.getAsJsonArray("to")
                assertTrue((0..2).all { hi[it].asDouble>lo[it].asDouble && lo[it].asDouble>=-16 && hi[it].asDouble<=32 },name)
            }
        }
    }
    @Test fun `fire landing breaks the prepared rock into flames and arcing debris`() {
        for(id in listOf("meteor","mage_ult")) {
            val e=effect(CoreClass.MAGE,id)
            val prepared=CoreSkillChoreography.parts(effect(CoreClass.MAGE,id,CoreSkillVisualPhase.PREPARE))
            assertTrue(prepared.any { it.shape in setOf("meteor_rock","meteor_crown") && it.motion==CoreMeshMotion.FALL })
            val parts=CoreSkillChoreography.parts(e)
            val flames=parts.filter { it.shape=="flame_plume" }
            val debris=parts.filter { it.shape=="meteor_rock" }
            assertEquals(if(e.skill.ultimate) 5 else 3,flames.size)
            assertEquals(if(e.skill.ultimate) 6 else 4,debris.size)
            assertFalse(parts.any { it.shape=="fire_crater" || it.shape=="fire_burst" })
            for(p in parts) assertEquals(e.durationTicks,p.delayTicks+p.durationTicks)
            for(p in flames) {
                assertEquals(-PI/2,p.pitch)
                val states=(0 until p.durationTicks).map { CoreSkillChoreography.pose(p,(p.delayTicks+it).toDouble()) }
                assertEquals(12,states.map { it.model }.distinct().size)
                assertTrue(states[3].model.endsWith("_2"),"The ignition peaks at the damage beat, not at the end")
            }
            for(p in debris) {
                val start=CoreSkillChoreography.pose(p,p.delayTicks.toDouble())
                val middle=CoreSkillChoreography.pose(p,p.delayTicks+p.durationTicks*.5)
                val end=CoreSkillChoreography.pose(p,(p.delayTicks+p.durationTicks-1).toDouble())
                assertTrue(middle.offset.y()>start.offset.y()+1.0)
                assertEquals(start.offset.y(),end.offset.y(),.00001)
                assertTrue(end.offset.distance(start.offset)>1.0)
            }
        }
    }
    @Test fun `firebolt changes its wake inside the original clipped ray at every pitch`() {
        val skill=CoreSkillCatalog.skills(CoreClass.MAGE).first { it.icon=="firebolt" }
        for(length in listOf(.06,.2,.6,2.0,5.0)) for(pitch in listOf(-1.4,-.5,0.0,.5,1.4)) {
            val dir=Vec(0.0,-sin(pitch),cos(pitch))
            val e=CoreSkillEffect(CoreClass.MAGE,skill,Vec.ZERO,dir,rayLength=length,clippedRay=true)
            val parts=CoreSkillChoreography.parts(e)
            val wake=parts.first { it.shape=="flame_tail" }
            assertEquals(12,(0 until wake.durationTicks).map {
                CoreSkillChoreography.pose(wake,(it+wake.delayTicks).toDouble()).model }.distinct().size)
            for(p in parts) for(tick in 0 until e.durationTicks) {
                val pose=CoreSkillChoreography.pose(p,tick.toDouble())
                val along=pose.offset.x()*dir.x()+pose.offset.y()*dir.y()+pose.offset.z()*dir.z()
                assertTrue(along-pose.scale.z()/2>=-.00001)
                assertTrue(along+pose.scale.z()/2<=length+.00001)
            }
        }
    }
    @Test fun `frost crests expand in all directions below waist instead of stationary garden pillars`() {
        for(id in listOf("frost_nova")) {
            val e=effect(CoreClass.MAGE,id)
            val parts=CoreSkillChoreography.parts(e)
            assertEquals(if(e.skill.ultimate) 12 else 8,parts.size)
            assertTrue(parts.all { it.shape=="frost_crest" && it.motion==CoreMeshMotion.RADIATE && it.erode })
            assertEquals(4,parts.map { Pair(it.offset.x()>=0,it.offset.z()>=0) }.toSet().size)
            for(p in parts) {
                var prior=0.0
                for(tick in 0 until p.durationTicks) {
                    val pose=CoreSkillChoreography.pose(p,(p.delayTicks+tick).toDouble())
                    val r=hypot(pose.offset.x(),pose.offset.z())
                    assertTrue(r>=prior-.00001 && r<e.radius)
                    assertTrue(pose.offset.y()+pose.scale.z()<1.0)
                    prior=r
                }
                assertTrue(prior>1.0)
            }
        }
    }
    @Test fun `lightning discharge changes fork paths and keeps four radial arms for other players`() {
        val parts=CoreSkillChoreography.parts(effect(CoreClass.MAGE,"mage_burst"))
        assertEquals(8,parts.size);assertEquals(4,parts.count { !it.secondary })
        for(p in parts) {
            assertEquals("storm_branch",p.shape)
            assertFalse(p.ground || p.followOwner)
            val models=(0 until p.durationTicks).map { CoreSkillChoreography.pose(p,(it+p.delayTicks).toDouble()).model }
            assertEquals(8,models.toSet().size)
            assertTrue(models.last().endsWith("_7"))
        }
    }
    @Test fun `elemental animation models have real volumes and projectile bounds stay normalized`() {
        fun model(name: String)=javaClass.getResourceAsStream("/core-ui-pack/assets/projects/models/combat_vfx/$name.json")!!
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        for(prefix in listOf("flame_plume_fire","fire_wake_fire","storm_branch_lightning")) {
            val frames=(0 until if(prefix.startsWith("storm")) 8 else 12).map { model("${prefix}_$it") }
            assertEquals(frames.size,frames.map { it.toString() }.toSet().size,prefix)
            for(frame in frames) for(element in frame.getAsJsonArray("elements")) {
                val lo=element.asJsonObject.getAsJsonArray("from");val hi=element.asJsonObject.getAsJsonArray("to")
                assertTrue((0..2).all { hi[it].asDouble>lo[it].asDouble })
                if(prefix=="fire_wake_fire") assertTrue(lo[2].asDouble>=0 && hi[2].asDouble<=16)
            }
        }
        for(name in listOf("fire_orb_fire","meteor_rock_fire","meteor_crown_fire")) {
            val elements=model(name).getAsJsonArray("elements")
            assertTrue(elements.size()>40)
            for(element in elements) {
                val lo=element.asJsonObject.getAsJsonArray("from");val hi=element.asJsonObject.getAsJsonArray("to")
                assertTrue((0..2).all { hi[it].asDouble>lo[it].asDouble && lo[it].asDouble>=0 && hi[it].asDouble<=16 })
            }
        }
    }
    @Test fun `healing phrases distinguish opening petals rising feathers and articulated wings`() {
        val ring=CoreSkillChoreography.parts(effect(CoreClass.HEALER,"heal_ring"))
        val wind=CoreSkillChoreography.parts(effect(CoreClass.HEALER,"heal_wind"))
        val shield=CoreSkillChoreography.parts(effect(CoreClass.HEALER,"heal_shield"))
        val ultimate=CoreSkillChoreography.parts(effect(CoreClass.HEALER,"heal_ult"))
        assertEquals(6,ring.size);assertEquals(8,wind.size);assertEquals(8,shield.size);assertEquals(14,ultimate.size)
        assertTrue(ring.all { it.shape=="healing_petal" && it.pitchTravel<0 && it.travel.y()>0 })
        assertTrue(wind.all { it.shape=="feather_plume" && it.motion==CoreMeshMotion.ORBIT })
        assertTrue(shield.all { it.followOwner && it.motion==CoreMeshMotion.EMERGE })
        assertTrue(ultimate.none { it.followOwner })
        assertEquals(8,ultimate.count { !it.secondary })
        for(parts in listOf(ring,wind,shield,ultimate)) {
            assertTrue(parts.all { it.atlas==CoreMeshAtlas.NONE && it.chainAnchor==null })
            assertFalse(parts.any { it.shape=="healing_wave" || it.shape=="prayer_wing" },"Do not leave the old static full symbol under the new phrase")
        }
    }
    @Test fun `healing wind rises within its orbit and each feather finishes inside phrase lifetime`() {
        for(id in listOf("heal_wind","heal_ult")) {
            val e=effect(CoreClass.HEALER,id)
            val parts=CoreSkillChoreography.parts(e)
            for(p in parts) assertEquals(e.durationTicks,p.delayTicks+p.durationTicks,id)
            for(p in parts.filter { it.motion==CoreMeshMotion.ORBIT }) {
                val start=CoreSkillChoreography.pose(p,p.delayTicks.toDouble())
                val end=CoreSkillChoreography.pose(p,(p.delayTicks+p.durationTicks-1).toDouble())
                assertTrue(end.offset.y()-start.offset.y()>=1.5,id)
                assertEquals(hypot(start.offset.x(),start.offset.z()),hypot(end.offset.x(),end.offset.z()),.00001,id)
                assertTrue(abs(end.yaw-start.yaw)>1.0,id)
            }
        }
    }
    @Test fun `prayer feathers fan out on both sides while keeping the body center empty`() {
        val parts=CoreSkillChoreography.parts(effect(CoreClass.HEALER,"heal_shield"))
        assertEquals(4,parts.count { it.offset.x()>0 });assertEquals(4,parts.count { it.offset.x()<0 })
        for(p in parts) {
            val initial=CoreSkillChoreography.pose(p,p.delayTicks.toDouble())
            val opened=CoreSkillChoreography.pose(p,p.delayTicks+8.0)
            assertTrue(abs(opened.roll)>abs(initial.roll))
            assertTrue(abs(opened.offset.x())>.6)
            assertTrue(opened.roll*opened.offset.x()<0,"Feathers should fan away from the center")
        }
    }
    @Test fun `healing feather has a volumetric quill and separate stepped barbs`() {
        val model=javaClass.getResourceAsStream("/core-ui-pack/assets/projects/models/combat_vfx/feather_plume_life.json")!!
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val elements=model.getAsJsonArray("elements").map { it.asJsonObject }
        assertTrue(elements.size>80)
        assertTrue(elements.all { e -> (0..2).all { e.getAsJsonArray("to")[it].asDouble>e.getAsJsonArray("from")[it].asDouble } })
        assertTrue(elements.any { it.getAsJsonArray("to")[2].asDouble-it.getAsJsonArray("from")[2].asDouble>=15.0 })
    }
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
            if(phase==CoreSkillVisualPhase.PULSE) {
                if(s.icon=="war_banner") assertEquals(60,e.durationTicks)
                else assertTrue(e.durationTicks in 18..40,s.icon)
            }
            val parts=CoreSkillChoreography.parts(e)
            if(job==CoreClass.WARRIOR && phase==CoreSkillVisualPhase.CONTACT && s.icon in setOf("war_cry","war_banner"))
                assertTrue(parts.isEmpty(),"Support grants must not imply an enemy hit")
            else assertTrue(parts.size in 1..16,"${s.icon}: ${parts.size}")
            for(p in parts) for(tick in 0..p.delayTicks+p.durationTicks) {
                val pose=CoreSkillChoreography.pose(p,tick.toDouble())
                if(p.shape.startsWith("flow:") || p.shape.startsWith("warrior_trace:")) assertTrue(pose.scale.x()>=0 && pose.scale.y()>0 && pose.scale.z()>=0)
                else assertTrue(pose.scale.x()>0 && pose.scale.y()>0 && pose.scale.z()>0)
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
    @Test fun `slash draws through a fixed plane without a duplicate delayed crescent`() {
        for(id in listOf("ass_execute","ass_fan","ass_ult")) {
            val parts=CoreSkillChoreography.parts(effect(CoreClass.ASSASSIN,id))
            val blade=parts.first()
            val expanded=id in CoreExpandedSlashChoreography.sceneIds
            assertTrue(if(expanded) blade.shape.startsWith("flow:") else blade.shape=="directional_cut",id)
            assertTrue(blade.durationTicks in 5..14,id)
            val states=(0 until blade.durationTicks).map { CoreSkillChoreography.pose(blade,it.toDouble()) }
            if(expanded) {
                assertEquals(1,states.map { it.model }.distinct().size,id)
                assertTrue(states.map { it.offset }.distinct().size>2,id)
            } else {
                assertTrue(states.map { it.model }.distinct().size>=5,id)
                assertEquals(1,states.map { Triple(it.yaw,it.pitch,it.roll) }.distinct().size,id)
                assertEquals(1,states.map { it.offset to it.scale }.distinct().size,id)
            }
            assertTrue(parts.none { it.sprite },id)
            assertTrue(parts.filter { it.secondary }.all { it.shape.contains(":wake:") },id)
            assertFalse(CoreSkillChoreography.pose(parts.last(),-1.0).visible)
        }
        val poison=CoreSkillChoreography.parts(effect(CoreClass.ASSASSIN,"ass_poison"))
        assertEquals(2,poison.count { it.shape.startsWith("venom_fang") })
        assertTrue(poison.none { it.sprite },"The bite must not inherit the generic sword ribbon")
        assertEquals(5,poison.count { it.shape=="venom_bead" })
    }
    @Test fun `stroke peaks early while its dissolving wake remains readable`() {
        for(job in CoreClass.entries) for(s in CoreSkillCatalog.skills(job)) {
            for(p in CoreSkillChoreography.parts(effect(job,s.icon)).filter { it.shape=="directional_cut" }) {
                val peak=if(p.durationTicks<=6) 2.0 else if(p.durationTicks<=10) 3.0 else 4.0
                assertTrue(CoreSkillChoreography.pose(p,p.delayTicks+peak).model.endsWith("_4"),s.icon)
                assertTrue(CoreSkillChoreography.pose(p,p.delayTicks+p.durationTicks-1.0).model.endsWith("_11"),s.icon)
            }
        }
    }
    @Test fun `warrior blade planes are not edge on from owner eye height`() {
        // Curved native strips have multiple normals; their real vertices are tested separately.
        for(id in listOf("dash","war_ult")) {
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
    @Test fun `return cuts have dedicated contours and full circles no longer duplicate fixed sectors`() {
        val first=CoreSkillChoreography.parts(effect(CoreClass.WARRIOR,"dash")).first()
        val reverse=CoreSkillChoreography.parts(effect(CoreClass.WARRIOR,"war_counter")).first()
        assertTrue(CoreWarriorBladeChoreography.owns(reverse))
        assertNotEquals(CoreSkillChoreography.pose(first,0.0).model,CoreSkillChoreography.pose(reverse,0.0).model)
        assertEquals(0.0,first.spin);assertEquals(0.0,reverse.spin)
        for((job,id) in listOf(CoreClass.ASSASSIN to "ass_fan",CoreClass.WARRIOR to "whirl")) {
            val sectors=CoreSkillChoreography.parts(effect(job,id))
            assertEquals(if(job==CoreClass.WARRIOR) 1 else 8,sectors.count { !it.secondary })
            assertEquals(if(job==CoreClass.WARRIOR) 2 else 4,sectors.count { it.secondary })
            assertTrue(sectors.all { it.delayTicks==0 })
            for(p in sectors) {
                if(job==CoreClass.WARRIOR) assertNotEquals(CoreSkillChoreography.pose(p,0.0).model,CoreSkillChoreography.pose(p,3.0).model)
                else assertEquals(CoreSkillChoreography.pose(p,0.0).model,CoreSkillChoreography.pose(p,3.0).model)
            }
        }
        assertEquals(2,CoreSkillChoreography.parts(effect(CoreClass.ASSASSIN,"ass_ult")).size)
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
                for(p in CoreSkillChoreography.parts(e).filter { it.shape=="directional_cut" }) repeat(p.durationTicks) { tick ->
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
            if(s.icon in setOf("star_thread","star_needle","star_break","ass_poison")) {
                val contact=CoreSkillChoreography.parts(effect(job,s.icon,CoreSkillVisualPhase.CONTACT))
                scenes+=mapOf("id" to "${s.icon}_contact","name" to "${s.name} 命中","job" to job.name,
                    "frames" to (0..contact.maxOf { it.delayTicks+it.durationTicks }).map { tick ->
                        contact.mapNotNull { p -> val pose=CoreSkillChoreography.pose(p,tick.toDouble());if(!pose.visible) null else
                            mapOf("model" to pose.model,"offset" to xyz(pose.offset.add(0.0,0.0,4.0)),"scale" to xyz(pose.scale),
                                "yaw" to pose.yaw,"pitch" to pose.pitch,"roll" to pose.roll) }
                    })
            }
            if(s.icon=="ass_escape") {
                val arrival=CoreSkillChoreography.parts(CoreSkillEffect(job,s,Vec.ZERO,Vec(0.0,0.0,1.0),endpoint=CoreSkillEndpoint.ARRIVAL))
                scenes+=mapOf("id" to "ass_escape_arrival","name" to "影抜け 到着","job" to job.name,
                    "frames" to (0..arrival.maxOf { it.delayTicks+it.durationTicks }).map { tick ->
                        arrival.mapNotNull { p -> val pose=CoreSkillChoreography.pose(p,tick.toDouble());if(!pose.visible) null else
                            mapOf("model" to pose.model,"offset" to xyz(pose.offset),"scale" to xyz(pose.scale),
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
