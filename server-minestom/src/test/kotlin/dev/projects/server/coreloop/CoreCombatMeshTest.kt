package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.*

class CoreCombatMeshTest {
    @Test fun `export complete warrior render path with force layers on the actual entity clock`() = player { owner ->
        val meshes=CoreCombatMeshes(owner)
        CoreCombatPresentation.pack(owner,true)
        val scenes=mutableListOf<Map<String,Any>>()
        fun xyz(v:net.minestom.server.coordinate.Point)=listOf(v.x(),v.y(),v.z())
        try {
            for(skill in CoreSkillCatalog.skills(CoreClass.WARRIOR)) {
                meshes.cancel()
                val frames=(0 until skill.startup+(skill.pulses-1)*8+65).map { tick ->
                    if(tick==0) meshes.play(CoreSkillEffect(CoreClass.WARRIOR,skill,owner.position,Vec(0.0,0.0,1.0),
                        CoreSkillVisualPhase.PREPARE,prepareTicks=(skill.startup-1).coerceAtLeast(1)))
                    if(tick>=skill.startup && (tick-skill.startup)%8==0 && (tick-skill.startup)/8<skill.pulses)
                        meshes.play(CoreSkillEffect(CoreClass.WARRIOR,skill,owner.position,Vec(0.0,0.0,1.0),
                            pulse=(tick-skill.startup)/8))
                    meshes.tick()
                    assertTrue(meshes.size<=CoreCombatMeshes.OWNER_LIMIT)
                    owner.instance.entities.filter { it.entityType==net.minestom.server.entity.EntityType.ITEM_DISPLAY && owner in it.viewers }
                        .mapNotNull { entity ->
                            val meta=entity.entityMeta as net.minestom.server.entity.metadata.display.ItemDisplayMeta
                            if(meta.scale.lengthSquared()<.00001) null else mapOf<String,Any>(
                                "model" to meta.itemStack.get(net.minestom.server.component.DataComponents.ITEM_MODEL)!!.removePrefix("projects:"),
                                "offset" to xyz(entity.position.sub(owner.position).add(meta.translation)),"scale" to xyz(meta.scale),
                                "quaternion" to meta.leftRotation.toList(),"pitch" to 0.0,"yaw" to 0.0,"roll" to 0.0)
                        }
                }
                assertEquals(0,meshes.size,skill.icon)
                scenes+=mapOf("id" to skill.icon,"name" to skill.name,"frames" to frames)
                scenes+=mapOf("id" to skill.icon+"_before","name" to skill.name+" / 中間層なし",
                    "frames" to frames.map { parts -> parts.filterNot { (it["model"] as String).contains("warrior_flourish/") } })
            }
            val cwd=java.nio.file.Path.of(System.getProperty("user.dir"))
            val root=if(cwd.fileName.toString()=="server-minestom")cwd.parent else cwd
            java.nio.file.Files.createDirectories(root.resolve(".tools"))
            java.nio.file.Files.writeString(root.resolve(".tools/warrior-force-timeline.json"),com.google.gson.Gson().toJson(scenes))
        } finally { meshes.cancel();CoreCombatPresentation.forget(owner) }
    }

    @Test fun `banner companion reaches owner and observer packets at every detail and stops on cancel`() {
        val packets=mutableListOf<SendablePacket>()
        player(packets) { owner ->
            val observerPackets=mutableListOf<SendablePacket>()
            val observer=connect(owner.instance,owner.position.add(1.0,0.0,0.0),"ParticleObserver",observerPackets)
            val vfx=GreatswordVfx(owner)
            fun count(items:List<SendablePacket>)=items.count { it.javaClass.simpleName=="ParticlePacket" }
            try {
                CoreCombatPresentation.pack(owner,true)
                CoreCombatPresentation.pack(observer,true)
                repeat(3) {
                    val skill=CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon=="war_banner" }
                    vfx.playSkill(CoreSkillEffect(CoreClass.WARRIOR,skill,owner.position,Vec(0.0,0.0,1.0)))
                    packets.clear();observerPackets.clear()
                    vfx.tick()
                    assertEquals(0,count(packets));assertEquals(0,count(observerPackets))
                    val boundary=owner.instance.entities.single { entity ->
                        (entity.entityMeta as? net.minestom.server.entity.metadata.display.ItemDisplayMeta)?.itemStack
                            ?.get(net.minestom.server.component.DataComponents.ITEM_MODEL)=="projects:combat_vfx/war_mote_boundary_steel"
                    }
                    assertTrue(owner in boundary.viewers);assertTrue(observer in boundary.viewers)
                    vfx.cancel();packets.clear();observerPackets.clear()
                    repeat(20) { vfx.tick() }
                    assertEquals(0,count(packets));assertEquals(0,count(observerPackets))
                    assertEquals(0,vfx.activeEffects);assertFalse(vfx.retainsInstance)
                    CoreCombatPresentation.cycle(owner);CoreCombatPresentation.cycle(observer)
                }
            } finally {
                vfx.cancel();CoreCombatPresentation.forget(owner);CoreCombatPresentation.forget(observer);observer.remove()
            }
        }
    }

    @Test fun `warrior marks follow actual target height stay private and clear when targets or marks disappear`() = player { owner ->
        val display=CoreWarriorMarkDisplay(owner)
        val state=CoreClassState()
        val observer=connect(owner.instance,owner.position.add(1.0,0.0,0.0),"MarkObserver")
        val id=UUID.randomUUID()
        val target=dev.projects.server.CombatTarget(id,owner.position.add(0.0,1.0,2.0),Vec(.4,1.0,.4))
        try {
            CoreCombatPresentation.pack(owner,true)
            state.mark(id,10);display.update(listOf(target),state,10)
            assertEquals(1,display.size)
            val label=owner.instance.entities.single { it.entityType==net.minestom.server.entity.EntityType.TEXT_DISPLAY }
            assertEquals(setOf(owner),label.viewers)
            val meta=label.entityMeta as net.minestom.server.entity.metadata.display.TextDisplayMeta
            assertEquals("\uE001",(meta.text as net.kyori.adventure.text.TextComponent).content())
            assertEquals(net.kyori.adventure.key.Key.key("projects","warrior_mark"),meta.text.font())
            assertEquals(" 6秒",(meta.text.children().single() as net.kyori.adventure.text.TextComponent).content())
            val moved=target.copy(position=owner.position.add(2.0,2.0,3.0))
            display.update(listOf(moved),state,50)
            assertEquals(" 4秒",(meta.text.children().single() as net.kyori.adventure.text.TextComponent).content())
            assertEquals(CoreWarriorMarkDisplay.position(moved,owner.position.add(0.0,owner.eyeHeight,0.0)),label.position)
            state.consumeMark(id,51);display.update(listOf(moved),state,51)
            assertTrue(label.isRemoved);assertEquals(0,display.size)
            state.mark(id,60);display.update(listOf(target),state,60)
            display.update(emptyList(),state,61);assertEquals(0,display.size)
            state.mark(id,70);display.update(listOf(target),state,70)
            display.update(listOf(target),state,191);assertEquals(0,display.size)
            val crowd=(0..15).map { n -> target.copy(id=UUID.randomUUID(),position=owner.position.add(n*.1,1.0,2.0)) }
            crowd.forEach { state.mark(it.id,200) };display.update(crowd,state,200)
            assertEquals(12,display.size);display.clear();assertEquals(0,display.size)
        } finally { display.clear();CoreCombatPresentation.forget(owner);observer.remove() }
    }

    @Test fun `warrior attack phases keep every authored frame through consecutive pulses for owner and observer`() = player { owner ->
        val meshes=CoreCombatMeshes(owner)
        val observer=connect(owner.instance,owner.position.add(1.0,0.0,0.0),"WarriorObserver")
        data class Track(val part:CoreCombatMeshPart,val entity:net.minestom.server.entity.Entity,val started:Int)
        val tracks=mutableListOf<Track>()
        val attacks=CoreSkillCatalog.skills(CoreClass.WARRIOR).filter {
            it.icon in CoreWarriorBladeChoreography.sceneIds && it.icon!="dash"
        }
        try {
            listOf(owner,observer).forEach { CoreCombatPresentation.pack(it,true) }
            for(skill in attacks) {
                tracks.clear()
                fun cast(phase:CoreSkillVisualPhase,pulse:Int,tick:Int) {
                    val e=CoreSkillEffect(CoreClass.WARRIOR,skill,owner.position,Vec(0.0,0.0,1.0),
                        phase,pulse,prepareTicks=skill.startup-1)
                    val before=owner.instance.entities.toSet()
                    meshes.play(e)
                    val fresh=owner.instance.entities.filter { it !in before }
                    val parts=CoreSkillChoreography.parts(e)
                    assertEquals(parts.size+CoreWarriorCompanions.parts(e).size,fresh.size,"${skill.icon} $phase/$pulse")
                    parts.filterNot(CoreWarriorCompanions::owns).forEach { p ->
                        val model="projects:"+CoreSkillChoreography.pose(p,0.0).model
                        val entity=fresh.single { display ->
                            (display.entityMeta as net.minestom.server.entity.metadata.display.ItemDisplayMeta).itemStack
                                .get(net.minestom.server.component.DataComponents.ITEM_MODEL)==model
                        }
                        assertTrue(owner in entity.viewers,"The first phase must not wait for a hidden spawn lead-in")
                        tracks+=Track(p,entity,tick)
                    }
                }
                val finalPulse=skill.startup+(skill.pulses-1)*8
                for(tick in 0..finalPulse+20) {
                    if(tick==0) cast(CoreSkillVisualPhase.PREPARE,0,tick)
                    if(tick>=skill.startup && (tick-skill.startup)%8==0 && tick<=finalPulse)
                        cast(CoreSkillVisualPhase.PULSE,(tick-skill.startup)/8,tick)
                    meshes.tick()
                    for((p,entity,start) in tracks) {
                        val age=tick-start
                        if(age>=CoreCombatMeshes.removalAge(p)) { assertTrue(entity.isRemoved);continue }
                        val pose=CoreSkillChoreography.pose(p,age.toDouble())
                        val meta=entity.entityMeta as net.minestom.server.entity.metadata.display.ItemDisplayMeta
                        assertEquals("projects:"+pose.model,meta.itemStack.get(net.minestom.server.component.DataComponents.ITEM_MODEL),
                            "${skill.icon} ${p.shape} tick=$tick age=$age")
                        assertEquals(pose.scale,meta.scale)
                        assertContentEquals(CoreCombatMeshArt.rotation(pose.yaw,pose.pitch,pose.roll),meta.leftRotation)
                        assertEquals(0,meta.transformationInterpolationDuration)
                        assertTrue(owner in entity.viewers)
                        assertEquals(!p.secondary,observer in entity.viewers,"Observer must keep each main cut, not the secondary trail")
                    }
                }
                assertEquals(0,meshes.size,skill.icon)
                assertTrue(tracks.all { it.entity.isRemoved })
            }
        } finally {
            meshes.cancel()
            listOf(owner,observer).forEach { CoreCombatPresentation.forget(it) }
            observer.remove()
        }
    }

    @Test fun `warrior support reveals its actual first pose immediately but never reveals future echoes`() = player { owner ->
        val meshes=CoreCombatMeshes(owner)
        CoreCombatPresentation.pack(owner,true)
        try {
            for(id in CoreWarriorSupportChoreography.sceneIds) {
                val e=effect(CoreClass.WARRIOR,id)
                val parts=CoreSkillChoreography.parts(e)+CoreWarriorCompanions.parts(e)
                meshes.play(e)
                val displays=owner.instance.entities.filter { it.entityType==net.minestom.server.entity.EntityType.ITEM_DISPLAY }
                assertEquals(parts.size,displays.size)
                // Ground companion snap is verified separately; retain the exact approved support poses here.
                val expected=parts.filterNot(CoreWarriorCompanions::owns).map { part ->
                    val pose=CoreSkillChoreography.pose(part,0.0)
                    listOf("projects:"+pose.model,pose.offset,if(pose.visible) pose.scale else Vec.ZERO,
                        CoreCombatMeshArt.rotation(pose.yaw,pose.pitch,pose.roll).toList())
                }
                val actual=displays.filterNot { display ->
                    (display.entityMeta as net.minestom.server.entity.metadata.display.ItemDisplayMeta).itemStack
                        .get(net.minestom.server.component.DataComponents.ITEM_MODEL)?.let { it.contains("war_mote_") || it.contains("warrior_flourish/") }==true
                }.map { display ->
                    val meta=display.entityMeta as net.minestom.server.entity.metadata.display.ItemDisplayMeta
                    assertTrue(owner in display.viewers)
                    listOf(meta.itemStack.get(net.minestom.server.component.DataComponents.ITEM_MODEL),
                        meta.translation,meta.scale,meta.leftRotation.toList())
                }
                // Four flag fragments share a model and origin but not a rotation.
                // Compare the complete multiset, not an arbitrary first entity match.
                assertEquals(expected.groupingBy { it }.eachCount(),actual.groupingBy { it }.eachCount(),id)
                repeat(parts.maxOf { it.durationTicks+it.delayTicks }+1) { meshes.tick() }
                assertEquals(0,meshes.size)
                assertTrue(displays.all { it.isRemoved })
            }
        } finally { meshes.cancel();CoreCombatPresentation.forget(owner) }
    }
    @Test fun `approved dash and AA send original frames at full fixed transform without tween distortion`() = player { owner ->
        val meshes=CoreCombatMeshes(owner)
        CoreCombatPresentation.pack(owner,true)
        try {
            for(id in listOf("dash") + CoreApprovedNormalV3.sceneIds) for(phase in CoreSkillVisualPhase.entries) {
                val base=effect(CoreClass.WARRIOR,"dash",phase)
                val e=CoreSkillEffect(base.job,base.skill,base.origin,base.direction,phase,sceneId=id)
                val parts=CoreSkillChoreography.parts(e)
                meshes.play(e)
                val pairs=parts.map { p ->
                    val name="projects:"+CoreSkillChoreography.pose(p,0.0).model
                    val entity=owner.instance.entities.single { it.entityType==net.minestom.server.entity.EntityType.ITEM_DISPLAY &&
                        (it.entityMeta as net.minestom.server.entity.metadata.display.ItemDisplayMeta).itemStack
                            .get(net.minestom.server.component.DataComponents.ITEM_MODEL)==name }
                    p to entity
                }
                if(id=="dash") repeat(2) { meshes.tick() } // Skills retain their existing lead-in; AA does not.
                for(age in 0..parts.maxOf { it.durationTicks }) {
                    meshes.tick()
                    for((p,entity) in pairs) {
                        if(age>=p.durationTicks) { assertTrue(entity.isRemoved);continue }
                        val pose=CoreSkillChoreography.pose(p,age.toDouble())
                        val meta=entity.entityMeta as net.minestom.server.entity.metadata.display.ItemDisplayMeta
                        assertEquals(0,meta.transformationInterpolationDuration)
                        assertEquals(pose.scale,meta.scale)
                        assertEquals(pose.offset,meta.translation)
                        assertContentEquals(CoreCombatMeshArt.rotation(pose.yaw,pose.pitch,pose.roll),meta.leftRotation)
                        assertContentEquals(CoreCombatMeshArt.vanillaItemCorrection,meta.rightRotation)
                        assertEquals("projects:"+pose.model,meta.itemStack.get(net.minestom.server.component.DataComponents.ITEM_MODEL))
                    }
                }
                assertEquals(0,meshes.size)
            }
        } finally { meshes.cancel();CoreCombatPresentation.forget(owner) }
    }
    @Test fun `normal release and contact are visible to owner before any tick and clean up`() = player { owner ->
        CoreCombatPresentation.pack(owner,true)
        val vfx=GreatswordVfx(owner)
        fun models()=owner.instance.entities.filter { it.entityType==net.minestom.server.entity.EntityType.ITEM_DISPLAY }.map {
            (it.entityMeta as net.minestom.server.entity.metadata.display.ItemDisplayMeta).itemStack
                .get(net.minestom.server.component.DataComponents.ITEM_MODEL)
        }.filterNot { it?.contains("war_mote_")==true }
        try {
            for((index,visual) in listOf(GreatswordVisual.SWEEP,GreatswordVisual.REVERSE,GreatswordVisual.FINISHER).withIndex()) {
                val prefix=if(index==1) "approved_aa_reverse_v3" else "approved_dash_v3"
                vfx.play(visual,owner.position,Vec(0.0,0.0,1.0))
                assertEquals(setOf("projects:combat_vfx/$prefix/blade_3","projects:combat_vfx/$prefix/wake_3"),models().toSet())
                for(entity in owner.instance.entities.filter { it.entityType==net.minestom.server.entity.EntityType.ITEM_DISPLAY }) {
                    assertTrue(owner in entity.viewers, "No hidden spawn wait for AA")
                    val meta=entity.entityMeta as net.minestom.server.entity.metadata.display.ItemDisplayMeta
                    if(meta.itemStack.get(net.minestom.server.component.DataComponents.ITEM_MODEL)?.contains("war_mote_")!=true)
                        assertTrue(meta.scale.x()>0)
                }
                repeat(18) { vfx.tick() }
                assertTrue(models().isEmpty())
            }
            vfx.normalContact(owner.position.add(0.0,0.0,1.5),Vec(0.0,0.0,1.0))
            assertEquals(listOf("projects:combat_vfx/approved_dash_v3/impact_0"),models())
            assertTrue(owner.instance.entities.filter { it.entityType==net.minestom.server.entity.EntityType.ITEM_DISPLAY }.all { owner in it.viewers })
            vfx.cancel()
            assertTrue(models().isEmpty())
        } finally { vfx.cancel();CoreCombatPresentation.forget(owner) }
    }
    @Test fun `immediate normal reveal respects full subdued minimal and unloaded owner settings`() = player { owner ->
        val meshes=CoreCombatMeshes(owner)
        fun displayed()=owner.instance.entities.count { it.entityType==net.minestom.server.entity.EntityType.ITEM_DISPLAY && owner in it.viewers }
        val base=effect(CoreClass.WARRIOR,"dash")
        val normal=CoreSkillEffect(base.job,base.skill,base.origin,base.direction,sceneId="normal_sweep")
        try {
            meshes.play(normal)
            assertEquals(0,displayed())
            CoreCombatPresentation.pack(owner,true)
            for(expected in listOf(6,1,0)) {
                meshes.play(normal)
                assertEquals(expected,displayed())
                meshes.cancel()
                CoreCombatPresentation.cycle(owner)
            }
        } finally { meshes.cancel();CoreCombatPresentation.forget(owner) }
    }

    @Test fun `normal warrior swings actually dispatch their sound packets`() {
        val packets=mutableListOf<SendablePacket>()
        player(packets) { owner ->
            val vfx=GreatswordVfx(owner)
            try {
                for((visual,expected) in listOf(GreatswordVisual.SWEEP to 2,GreatswordVisual.REVERSE to 2,GreatswordVisual.FINISHER to 3)) {
                    packets.clear()
                    vfx.play(visual,owner.position,Vec(0.0,0.0,1.0))
                    assertEquals(expected,packets.count { it.javaClass.simpleName.contains("SoundEffectPacket") },visual.name)
                }
            } finally { vfx.cancel() }
        }
    }
    @Test fun `support audio sends once per actual phase not once per echo mesh`() {
        val packets=mutableListOf<SendablePacket>()
        player(packets) { owner ->
            for(id in CoreWarriorSupportChoreography.sceneIds) for(phase in CoreSkillVisualPhase.entries) {
                packets.clear()
                val e=effect(CoreClass.WARRIOR,id,phase)
                CoreSkillAudio.play(owner,e)
                assertEquals(CoreSkillAudio.warriorCues(e).size,
                    packets.count { it.javaClass.simpleName.contains("SoundEffectPacket") },"$id $phase")
            }
        }
    }
    private fun effect(job: CoreClass, id: String, phase: CoreSkillVisualPhase = CoreSkillVisualPhase.PULSE, pulse: Int = 0, length: Double = 0.0) =
        CoreSkillEffect(job, CoreSkillCatalog.skills(job).first { it.icon == id }, Vec(8.0, 41.0, 8.0), Vec(0.0, 0.0, 1.0), phase, pulse, rayLength = length,clippedRay=length>0)

    @Test fun `all seventy skills have bounded pack-backed solid silhouettes`() {
        val index = javaClass.getResourceAsStream("/core-ui-pack/index.txt")!!.bufferedReader().use { it.readLines().toSet() }
        for (job in CoreClass.entries) for (skill in CoreSkillCatalog.skills(job)) for (phase in CoreSkillVisualPhase.entries) {
            val parts = CoreCombatMeshArt.parts(effect(job, skill.icon, phase, length = if (CoreSkillScenes.get(skill.icon).kind == CoreSceneKind.RAY) 18.0 else 0.0))
            assertTrue(parts.size in 1..7, "${skill.icon} $phase")
            for (p in parts) {
                val name = "${p.shape}_${p.palette}"
                for (kind in listOf("models", "items")) {
                    val path = "assets/projects/$kind/combat_vfx/$name.json"
                    assertTrue(path in index, path)
                    assertNotNull(javaClass.getResource("/core-ui-pack/$path"))
                }
                assertTrue(listOf(p.scale.x(), p.scale.y(), p.scale.z()).all { it.isFinite() && it > 0 && it <= 24 })
                val q = CoreCombatMeshArt.rotation(p.yaw, p.pitch, p.roll)
                assertTrue(q.all(Float::isFinite))
                assertTrue(abs(q.sumOf { (it * it).toDouble() } - 1.0) < 1e-5)
            }
        }
    }

    @Test fun `starfall falls while cloud ring shield and teleport have distinct bodies`() {
        val prepare = CoreCombatMeshArt.parts(effect(CoreClass.STARWEAVER, "starfall", CoreSkillVisualPhase.PREPARE))
        assertTrue(prepare.any { it.shape == "star_core" && it.offset.y() >= 4 && it.travel.y() < -3 })
        val impact = CoreCombatMeshArt.parts(effect(CoreClass.STARWEAVER, "starfall"))
        assertTrue(impact.any { it.shape == "star_core" })
        assertTrue(impact.any { it.shape == "astral_crack" })
        assertTrue(impact.all { it.palette == "astral" })
        assertEquals("nebula_wisp",CoreCombatMeshArt.parts(effect(CoreClass.STARWEAVER,"star_cloud")).first().shape)
        assertEquals("star_orbit",CoreCombatMeshArt.parts(effect(CoreClass.STARWEAVER,"star_ring")).first().shape)
        assertEquals("star_mantle",CoreCombatMeshArt.parts(effect(CoreClass.STARWEAVER,"star_shield")).first().shape)
        assertEquals("star_gate",CoreCombatMeshArt.parts(effect(CoreClass.STARWEAVER,"star_step")).first().shape)
    }

    @Test fun `blade pulses alternate and clipped ray never becomes a twenty four block beam`() {
        val a = CoreCombatMeshArt.parts(effect(CoreClass.ASSASSIN, "ass_ult", pulse = 0)).first()
        val b = CoreCombatMeshArt.parts(effect(CoreClass.ASSASSIN, "ass_ult", pulse = 1)).first()
        assertTrue(a.spin * b.spin < 0)
        val beam = CoreCombatMeshArt.parts(effect(CoreClass.STARWEAVER, "star_needle", length = 3.25)).last()
        assertEquals(3.25, beam.scale.z())
        assertEquals(1.625, beam.offset.z())
        assertTrue(CoreCombatMeshArt.parts(effect(CoreClass.STARWEAVER, "star_needle", length = 0.0)).isEmpty())
        val invalid = CoreSkillEffect(CoreClass.WARRIOR, CoreSkillCatalog.skills(CoreClass.WARRIOR).first(), Vec.ZERO, Vec(Double.NaN, 0.0, 0.0))
        assertTrue(CoreCombatMeshArt.parts(invalid).isEmpty())
    }

    @Test fun `native displays require pack obey viewer setting expire and cancel without leaks`() = player { p ->
        val meshes = CoreCombatMeshes(p)
        val scene = p.instance
        val original = scene.entities.size
        val effect = effect(CoreClass.STARWEAVER, "starfall")
        try {
            meshes.play(effect)
            assertEquals(0, meshes.size, "No PAPER items may appear for a missing pack")
            CoreCombatPresentation.pack(p, true)
            meshes.play(effect); meshes.tick()
            val expected=CoreSkillChoreography.parts(effect).size
            assertEquals(expected, meshes.size)
            scene.entities.filter { it !== p }.forEach { entity ->
                val meta=entity.entityMeta as net.minestom.server.entity.metadata.display.ItemDisplayMeta
                assertContentEquals(CoreCombatMeshArt.vanillaItemCorrection,meta.rightRotation)
            }
            assertEquals(expected, scene.entities.count { it !== p && p in it.viewers })
            assertEquals(CoreCombatPresentation.Detail.SUBDUED, CoreCombatPresentation.cycle(p))
            meshes.tick()
            assertEquals(1, scene.entities.count { it !== p && p in it.viewers })
            assertEquals(CoreCombatPresentation.Detail.MINIMAL, CoreCombatPresentation.cycle(p))
            meshes.tick()
            assertEquals(0, scene.entities.count { it !== p && p in it.viewers })
            repeat(80) { meshes.tick() }
            assertEquals(0, meshes.size)
            assertEquals(original, scene.entities.size)
            assertEquals(CoreCombatPresentation.Detail.FULL, CoreCombatPresentation.cycle(p))
            repeat(20) { meshes.play(effect) }
            assertEquals(CoreCombatMeshes.OWNER_LIMIT, meshes.size)
            meshes.cancel(); meshes.cancel()
            assertEquals(original, scene.entities.size)
            val party = List(9) { CoreCombatMeshes(p) }
            try {
                party.forEach { renderer -> repeat(20) { renderer.play(effect) } }
                assertEquals(CoreCombatMeshes.SCENE_LIMIT, party.sumOf { it.size }, "The shared scene cap must apply across actors")
                assertEquals(0, party.last().size)
            } finally { party.forEach { it.cancel() } }
            assertEquals(original, scene.entities.size)
            CoreCombatPresentation.forget(p)
            assertEquals(CoreCombatPresentation.Detail.FULL, CoreCombatPresentation.detail(p))
            assertFalse(CoreCombatPresentation.packed(p))
        } finally { meshes.cancel(); CoreCombatPresentation.forget(p) }
    }

    @Test fun `flow cut targets use two tick interpolation and drain zero scale before removal`() = player { owner ->
        val meshes=CoreCombatMeshes(owner)
        val scene=owner.instance
        CoreCombatPresentation.pack(owner,true)
        try {
            val e=effect(CoreClass.ASSASSIN,"ass_fan")
            val parts=CoreSkillChoreography.parts(e)
            meshes.play(e)
            val displays=scene.entities.filter { it!==owner }.toList()
            assertEquals(parts.size,displays.size)
            displays.forEach {
                assertEquals(2,(it.entityMeta as net.minestom.server.entity.metadata.display.ItemDisplayMeta).transformationInterpolationDuration)
            }
            val cuts=displays.filter {
                !(it.entityMeta as net.minestom.server.entity.metadata.display.ItemDisplayMeta).itemStack
                    .get(net.minestom.server.component.DataComponents.ITEM_MODEL)!!.endsWith("_wake")
            }
            // Live starts at -2: after ten ticks, cut age 7 (zero width) was sent.
            repeat(10) { meshes.tick() }
            assertTrue(cuts.all { !it.isRemoved })
            cuts.forEach {
                assertEquals(0.0,(it.entityMeta as net.minestom.server.entity.metadata.display.ItemDisplayMeta).scale.x(),1e-8)
            }
            repeat(2) { meshes.tick() }
            assertTrue(cuts.all { !it.isRemoved },"Do not delete before the client drains its last transform")
            meshes.tick()
            assertTrue(cuts.all { it.isRemoved })
            repeat(8) { meshes.tick() }
            assertEquals(0,meshes.size)
            val prepare=CoreSkillChoreography.parts(effect(CoreClass.ASSASSIN,"ass_fan",CoreSkillVisualPhase.PREPARE)).first()
            assertEquals(1,CoreCombatMeshes.interpolationTicks(prepare),"Do not extend the prepare/pulse handoff")
            assertEquals(prepare.delayTicks+prepare.durationTicks,CoreCombatMeshes.removalAge(prepare))
        } finally { meshes.cancel(); CoreCombatPresentation.forget(owner) }
    }

    @Test fun `observers see fresh nearby beats instead of older or distant primary tails`() = player { owner ->
        val map=owner.instance
        val near=connect(map,Pos(8.0,40.0,9.0),"NearVfx")
        val far=connect(map,Pos(40.0,40.0,9.0),"FarVfx")
        val meshes=CoreCombatMeshes(owner)
        fun cast(id: String,job: CoreClass,x: Double)=CoreSkillEffect(job,
            CoreSkillCatalog.skills(job).first { it.icon==id },Vec(x,40.0,8.0),Vec(0.0,0.0,1.0))
        try {
            listOf(owner,near,far).forEach { CoreCombatPresentation.pack(it,true) }
            // More than eight old primaries, with the far scene inserted first.
            meshes.play(cast("war_banner",CoreClass.WARRIOR,40.0))
            repeat(2) { meshes.play(cast("war_banner",CoreClass.WARRIOR,8.0)) }
            repeat(5) { meshes.tick() }
            val old=map.entities.toSet()
            val incoming=cast("mage_blink",CoreClass.MAGE,8.0)
            meshes.play(incoming)
            val fresh=map.entities.filter { it !in old }.toSet()
            assertEquals(6,fresh.size)
            meshes.tick() // New displays still have a hidden spawn frame.
            assertTrue(fresh.none { near in it.viewers },"Hidden displays must not reserve observer slots")
            assertTrue(old.any { it.entityType==net.minestom.server.entity.EntityType.ITEM_DISPLAY && near in it.viewers })
            repeat(3) { meshes.tick() }
            assertEquals(4,fresh.count { near in it.viewers },"All four current primary arcs must survive older tails")
            assertTrue(fresh.none { far in it.viewers })
            assertTrue(map.entities.any { it.entityType==net.minestom.server.entity.EntityType.ITEM_DISPLAY && far in it.viewers },
                "A nearby observer's choices must not consume the distant observer's slots")
            assertTrue(map.entities.count { near in it.viewers && it.entityType==net.minestom.server.entity.EntityType.ITEM_DISPLAY }<=8)
            CoreCombatPresentation.cycle(near);CoreCombatPresentation.cycle(near)
            meshes.tick()
            assertTrue(fresh.none { near in it.viewers },"Minimal still opts out")
        } finally {
            meshes.cancel()
            listOf(owner,near,far).forEach { CoreCombatPresentation.forget(it) }
            near.remove();far.remove()
        }
    }

    private fun connect(map: net.minestom.server.instance.Instance,at: Pos,name: String,packets:MutableList<SendablePacket>?=null): Player {
        val connection=object : PlayerConnection() {
            override fun sendPacket(packet: SendablePacket) { packets?.add(packet) }
            override fun getRemoteAddress(): SocketAddress=InetSocketAddress("127.0.0.1",0)
        }
        connection.setClientState(ConnectionState.PLAY);connection.setServerState(ConnectionState.PLAY)
        val p=Player(connection,GameProfile(UUID.randomUUID(),name));connection.player=p
        p.setInstance(map,at).get(10,TimeUnit.SECONDS)
        return p
    }
    private fun player(packets: MutableList<SendablePacket>?=null,action: (Player) -> Unit) {
        MinecraftServer.init(Auth.Offline())
        val map = MinecraftServer.getInstanceManager().createInstanceContainer()
        map.viewDistance(2)
        map.setGenerator { it.modifier().fillHeight(0, 40, Block.STONE) }
        for (x in -3..5) for (z in -3..3) map.loadChunk(x, z).get(10, TimeUnit.SECONDS)
        val connection = object : PlayerConnection() {
            override fun sendPacket(packet: SendablePacket) { packets?.add(packet) }
            override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
        }
        connection.setClientState(ConnectionState.PLAY); connection.setServerState(ConnectionState.PLAY)
        val p = Player(connection, GameProfile(UUID.randomUUID(), "CombatMeshTest"))
        connection.player = p
        p.setInstance(map, Pos(8.0, 40.0, 8.0)).get(10, TimeUnit.SECONDS)
        try { action(p) } finally {
            map.players.toList().forEach { it.remove() }
            MinecraftServer.getInstanceManager().unregisterInstance(map)
        }
    }
}
