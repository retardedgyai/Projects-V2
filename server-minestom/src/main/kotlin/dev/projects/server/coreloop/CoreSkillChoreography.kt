package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** An effect's readable phrase is longer than its damage beat. Never feeds back into hit timing. */
internal enum class CoreMeshMotion { LINEAR, SNAP, SWEEP, REVOLVE, THRUST, FALL, RADIATE, ORBIT, GATHER, FLOAT, EMERGE }
internal data class CoreMeshPose(val offset: Vec, val scale: Vec, val yaw: Double, val pitch: Double,
    val roll: Double, val model: String, val visible: Boolean)

internal object CoreSkillChoreography {
    // Explicit durations in server ticks. Assassin is not allowed to disappear in three frames.
    private val duration = mapOf(
        "normal_sweep" to 16,"normal_reverse" to 16,"normal_finish" to 24,
        "dash" to 20,"slam" to 28,"whirl" to 20,"war_guard" to 30,"war_wound" to 18,
        "war_counter" to 22,"war_cry" to 28,"war_breach" to 22,"war_ult" to 30,"war_banner" to 60,
        "firebolt" to 22,"frost_nova" to 30,"meteor" to 30,"mage_blink" to 24,"mage_mark" to 22,
        "mage_garden" to 32,"mage_burst" to 28,"mage_ward" to 30,"mage_ult" to 34,"mage_zero" to 32,
        "pierce" to 18,"frost_fan" to 24,"arrow_rain" to 24,"hunt_retreat" to 20,"hunt_pierce" to 22,
        "hunt_trap" to 32,"hunt_mark" to 22,"hunt_volley" to 18,"hunt_ult" to 28,"hunt_storm" to 26,
        "ass_stab" to 22,"ass_execute" to 24,"ass_fan" to 24,"ass_escape" to 24,"ass_poison" to 26,
        "ass_chase" to 22,"ass_needle" to 22,"ass_guard" to 32,"ass_ult" to 24,"ass_contract" to 30,
        "temp_mace" to 26,"temp_pull" to 30,"temp_ward" to 32,"temp_guard" to 30,"temp_rebuke" to 28,
        "temp_field" to 32,"temp_break" to 28,"temp_dash" to 24,"temp_ult" to 34,"temp_sanctuary" to 40,
        "heal_light" to 22,"heal_ring" to 30,"heal_pillar" to 28,"heal_step" to 24,"heal_lamp" to 36,
        "heal_shield" to 32,"heal_mark" to 24,"heal_wind" to 28,"heal_ult" to 40,"heal_judgment" to 32,
        "star_thread" to 22,"star_ring" to 32,"starfall" to 32,"star_step" to 26,"star_needle" to 24,
        "star_cloud" to 36,"star_shield" to 32,"star_break" to 28,"star_ult" to 38,"star_constellation" to 40)
    val sceneIds get() = duration.keys
    fun duration(e: CoreSkillEffect) = when(e.phase) {
        CoreSkillVisualPhase.PREPARE -> e.prepareDuration
        CoreSkillVisualPhase.CONTACT -> 14
        CoreSkillVisualPhase.PULSE -> duration.getValue(e.sceneId)
    }
    private fun ease(t: Double)=1-(1-t.coerceIn(0.0,1.0)).pow(3)
    fun pose(p: CoreCombatMeshPart, age: Double): CoreMeshPose {
        CoreFrostChoreography.pose(p,age)?.let { return it }
        CoreWarriorSupportChoreography.pose(p,age)?.let { return it }
        CoreStarweaverChoreography.pose(p,age)?.let { return it }
        CoreTemplarChoreography.pose(p,age)?.let { return it }
        val localAge=(age-p.delayTicks).coerceAtLeast(0.0)
        val t=((age-p.delayTicks)/(p.durationTicks-1).coerceAtLeast(1)).coerceIn(0.0,1.0)
        val u=when(p.motion) {
            CoreMeshMotion.LINEAR,CoreMeshMotion.ORBIT,CoreMeshMotion.FLOAT -> t
            CoreMeshMotion.FALL -> t*t
            CoreMeshMotion.GATHER -> (localAge/min(p.durationTicks*.5,10.0)).coerceIn(0.0,1.0).pow(2)
            CoreMeshMotion.THRUST,CoreMeshMotion.SWEEP -> ease(localAge/5.0)
            CoreMeshMotion.REVOLVE -> (localAge/6.0).coerceIn(0.0,1.0)
            CoreMeshMotion.SNAP,CoreMeshMotion.RADIATE -> ease(t/.65)
            CoreMeshMotion.EMERGE -> ease(localAge/5.0)*(1-ease((t-.72)/.28))
        }
        val grow=p.startSize+(p.endSize-p.startSize)*u
        val arc=sin(PI*t)
        val travel=p.travel.mul(u).add(p.bend.mul(arc))
        val angle=if(p.motion==CoreMeshMotion.ORBIT) p.spin*t else p.spin*u
        val at=if(p.shape=="piercing_wake") {
            // Keep the rear at the cast origin and the front attached to the dagger.
            p.offset.add(Vec(sin(p.yaw),0.0,cos(p.yaw)).mul(p.scale.z()*grow*.5))
        } else if(p.motion==CoreMeshMotion.ORBIT) {
            Vec(cos(angle)*p.offset.x()+sin(angle)*p.offset.z(),p.offset.y(),-sin(angle)*p.offset.x()+cos(angle)*p.offset.z()).add(travel)
        } else p.offset.add(travel)
        p.chainAnchor?.let { anchor ->
            // The moving end is exactly the hook's trajectory. Recompute the midpoint,
            // direction and length each tick; scaling a static line would detach an end.
            val delta=at.sub(anchor)
            val length=delta.length().coerceAtLeast(.01)
            val links=ceil(length/.45).toInt().coerceIn(1,12)
            val width=(1-ease((t-.5)/.5)).coerceAtLeast(.025)
            return CoreMeshPose(anchor.add(delta.mul(.5)),Vec(p.scale.x()*width,p.scale.y()*width,length),
                atan2(delta.x(),delta.z()),-atan2(delta.y(),hypot(delta.x(),delta.z())),0.0,
                "combat_vfx/chain_${links}_${p.palette}",age>=p.delayTicks && age<p.delayTicks+p.durationTicks)
        }
        // Stroke peaks in 0.20s regardless of the longer aftermath. Extending readability
        // must not postpone the visually strongest beat until half a second after damage.
        val stage=if(p.shape in setOf("star_crest","star_fold","star_mantle_stream")) {
            CoreStarweaverChoreography.frame(p,age)
        } else if(p.atlas in setOf(CoreMeshAtlas.NEBULA_STREAM,CoreMeshAtlas.SHADOW_SMOKE)) {
            floor(t*15).toInt()
        } else if(p.stellarBurst) {
            // Ignition peaks at frame 3 in 0.10s; the hollow broken wake then unravels.
            if(localAge<=2.0) floor(localAge/2*3).toInt() else
                4+floor(((localAge-3)/(p.durationTicks-4).coerceAtLeast(1)).coerceIn(0.0,1.0)*11).toInt()
        } else if(p.sprite) {
            if(localAge<=4.0) floor(localAge/4.0*7).toInt() else
                8+floor(((localAge-5)/(p.durationTicks-6).coerceAtLeast(1)).coerceIn(0.0,1.0)*7).toInt()
        } else floor(((t-.42)/.58).coerceIn(0.0,1.0)*7).toInt()
        val model=if(p.shape=="inscribed_bolt") {
            "combat_vfx/precision/bolt_${ceil(p.scale.z()/1.5).toInt().coerceIn(1,16)}_${CorePrecisionChoreography.lightningFrame(p,age)}"
        } else if(p.shape=="needle_rift") {
            "combat_vfx/precision/needle_${ceil(p.scale.z()/2).toInt().coerceIn(1,12)}_${CorePrecisionChoreography.needleFrame(p,age)}"
        } else if(p.shape=="star_crest") {
            "combat_vfx/weave/crest_${p.palette}_$stage"
        } else if(p.shape=="weave_ray") {
            val segments=ceil(p.scale.z()/1.5).toInt().coerceIn(1,16)
            "combat_vfx/weave/ray_${segments}_${CoreStarweaverChoreography.frame(p,age)}"
        } else if(p.shape=="purifying_column" || p.shape=="prayer_flame") {
            "combat_vfx/prayer/column_${p.palette}_${CoreHealerChoreography.frame(p,age)}"
        } else if(p.shape=="prayer_ray") {
            val segments=ceil(p.scale.z()/(2*p.scale.x())).toInt().coerceIn(1,24)
            "combat_vfx/prayer/ray_${segments}_${CoreHealerChoreography.frame(p,age)}"
        } else if(p.shape=="shot_wake") {
            val segments=ceil(p.scale.z()/(2*p.scale.x().coerceAtLeast(.1))).toInt().coerceIn(1,12)
            "combat_vfx/shot/wake_${p.palette}_${segments}_${floor(t*15).toInt()}"
        } else
            if(p.shape=="shadow_echo") "combat_vfx/shadow_echo_shadow_${floor(t*7).toInt()}" else
            if(p.atlas==CoreMeshAtlas.SHADOW_SMOKE) "combat_vfx/shadow/smoke_shadow_$stage" else
            if(p.shape=="flame_plume" || p.shape=="flame_tail") {
            val frame=if(localAge<=3.0) floor(localAge/3*2).toInt() else
                3+floor(((localAge-4)/(p.durationTicks-5).coerceAtLeast(1)).coerceIn(0.0,1.0)*8).toInt()
            "combat_vfx/${if(p.shape=="flame_tail") "fire_wake" else "flame_plume"}_fire_$frame"
        } else if(p.shape=="storm_branch") {
            val frame=if(t<.42) (localAge.toInt()/2)%4 else
                4+floor(((t-.42)/.58).coerceIn(0.0,1.0)*3).toInt()
            "combat_vfx/storm_branch_lightning_$frame"
        } else if(p.atlas==CoreMeshAtlas.NEBULA_STREAM) "combat_vfx/nebula/stream_${p.palette}_$stage" else
            if(p.stellarBurst) "combat_vfx/stellar/burst_${p.palette}_$stage" else
            if(p.sprite) "combat_vfx/ribbon/slash_${if(p.spriteMirror) "reverse_" else ""}${p.palette}_$stage" else
            "combat_vfx/${p.shape}_${p.palette}" + if(p.erode && stage>0) "_fade$stage" else ""
        val scale=if(p.shape=="piercing_wake") Vec(p.scale.x(),p.scale.y(),p.scale.z()*grow) else p.scale.mul(grow)
        return CoreMeshPose(at,scale,p.yaw+angle,p.pitch+p.pitchTravel*u,p.roll+p.rollTravel*u,
            model,age>=p.delayTicks && age<p.delayTicks+p.durationTicks)
    }

    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart> {
        val raw=CoreCombatMeshArt.parts(e)
        if(raw.isEmpty()) return raw
        val s=CoreSkillScenes.get(e.sceneId)
        val life=duration(e)
        if(e.sceneId in CorePrecisionChoreography.sceneIds) return CorePrecisionChoreography.parts(e,raw,life)
        if(e.job==CoreClass.RANGER) return CoreRangerChoreography.parts(e,raw,life)
        if(e.job==CoreClass.TEMPLAR && s.kind!=CoreSceneKind.PULL) return CoreTemplarChoreography.parts(e,life)
        if(e.sceneId in CoreHealerChoreography.sceneIds) return CoreHealerChoreography.parts(e,raw,life)
        if(e.sceneId in CoreWarriorSupportChoreography.sceneIds && e.phase!=CoreSkillVisualPhase.CONTACT)
            return CoreWarriorSupportChoreography.parts(e,life)
        // CONTACT keeps the accepted-hit-only stellar bursts below.
        if(e.sceneId in CoreStarweaverChoreography.sceneIds && e.phase!=CoreSkillVisualPhase.CONTACT)
            return CoreStarweaverChoreography.parts(e,raw,life)
        if(e.phase==CoreSkillVisualPhase.PREPARE) return raw.map { it.copy(
            motion=if(s.kind==CoreSceneKind.RAIN) CoreMeshMotion.FALL else CoreMeshMotion.GATHER,
            durationTicks=life,erode=false) }
        if(e.phase==CoreSkillVisualPhase.CONTACT) {
            val p=raw.first()
            if(e.sceneId=="ass_poison") return venomDrops(p.offset,atan2(e.direction.x(),e.direction.z()),18)
            // Only accepted hits reach CONTACT. Never attach this explosion to an empty ray.
            if(e.sceneId in setOf("star_thread","star_needle","star_break")) {
                val burst=stellarBurst(p.offset,atan2(e.direction.x(),e.direction.z()),
                    if(e.sceneId=="star_break") 3.6 else 1.7,if(e.sceneId=="star_break") 24 else 16)
                // Star Needle's gameplay mark retains its own identity and location.
                return if(e.sceneId=="star_needle") burst+ p.copy(durationTicks=14,secondary=true) else burst
            }
            return listOf(p.copy(durationTicks=14,motion=CoreMeshMotion.SNAP,erode=true,startSize=.35,endSize=1.35),
                p.copy(offset=p.offset.add(.25,.15,.02),scale=p.scale.mul(.5),delayTicks=3,durationTicks=16,
                    motion=CoreMeshMotion.RADIATE,travel=Vec(.5,.55,.1),spin=.7,secondary=true,erode=true))
        }
        if(e.sceneId=="star_cloud") return nebulaField(e,life)
        if(e.sceneId=="mage_garden") return iceGarden(e,life)
        if(s.kind==CoreSceneKind.PULL) return chainPull(e,life)
        if(e.sceneId=="mage_ward") return arcaneWard(e,life)
        if(e.sceneId in setOf("heal_ring","heal_wind","heal_ult","heal_shield")) return healingPhrase(e,life)
        if(e.sceneId in setOf("meteor","mage_ult")) return fireLanding(e,raw.first().offset,life)
        if(e.sceneId=="mage_zero") return CoreFrostChoreography.parts(e)
        if(e.sceneId=="frost_nova") return frostWave(e,life)
        if(e.sceneId=="mage_burst") return stormDischarge(e,life)
        if(e.sceneId in setOf("ass_stab","ass_chase","ass_contract")) return assassinThrust(e,life)
        if(e.sceneId in setOf("ass_escape","ass_guard")) return shadowDeparture(e,life)
        if(e.sceneId=="ass_poison") return venomBite(e,life)
        val ray=s.kind==CoreSceneKind.RAY
        val base=raw.mapIndexed { i,p ->
            val motion=when(s.kind) {
                CoreSceneKind.CUT,CoreSceneKind.CLEAVE,CoreSceneKind.SPIN -> if(p.secondary) CoreMeshMotion.RADIATE else CoreMeshMotion.SWEEP
                CoreSceneKind.THRUST,CoreSceneKind.FAN -> CoreMeshMotion.THRUST
                CoreSceneKind.PULL -> CoreMeshMotion.GATHER
                CoreSceneKind.NOVA -> CoreMeshMotion.RADIATE
                CoreSceneKind.FIELD -> if(p.secondary) CoreMeshMotion.ORBIT else CoreMeshMotion.FLOAT
                CoreSceneKind.HEAL -> CoreMeshMotion.FLOAT
                CoreSceneKind.HAMMER,CoreSceneKind.RAIN,CoreSceneKind.PILLAR -> CoreMeshMotion.SNAP
                CoreSceneKind.TELEPORT -> if(e.endpoint==CoreSkillEndpoint.DEPARTURE) CoreMeshMotion.GATHER else CoreMeshMotion.SNAP
                else -> CoreMeshMotion.SNAP
            }
            p.copy(durationTicks=if(s.kind==CoreSceneKind.GUARD && !p.secondary) max(life,p.durationTicks) else life,
                delayTicks=if(p.secondary) 2+i%3 else 0,motion=motion,
                erode=!(s.kind==CoreSceneKind.GUARD && !p.secondary),
                // Preserve clipped ray geometry: no expansion or cosmetic travel through a wall.
                startSize=if(ray) 1.0 else p.startSize,
                endSize=if(ray) 1.0 else p.endSize,
                spin=if(s.kind==CoreSceneKind.FIELD && p.secondary) (if(i%2==0) 1 else -1)*1.4 else p.spin,
                bend=if(p.secondary && !p.ground && !ray) Vec(0.0,.3,0.0) else Vec.ZERO)
        }.toMutableList()
        val blade=s.kind in setOf(CoreSceneKind.CUT,CoreSceneKind.CLEAVE,CoreSceneKind.SPIN)
        if(blade) {
            val p=base.first()
            val yaw=atan2(e.direction.x(),e.direction.z())
            val r=min(s.reach,e.radius.coerceAtLeast(.5))
            val forward=Vec(sin(yaw),0.0,cos(yaw))
            val vertical=s.kind==CoreSceneKind.CLEAVE
            val spin=s.kind==CoreSceneKind.SPIN
            val sign=if(((if(s.body=="slash_reverse") 1 else 0)+e.pulse)%2==0) 1.0 else -1.0
            // The atlas is an evolving trail, not a full silhouette scaled away at the end.
            val ribbon=p.copy(atlas=CoreMeshAtlas.SLASH,spriteMirror=sign<0,erode=false,
                offset=Vec(0.0,if(vertical) 1.25 else 1.1,0.0).add(forward.mul(if(spin) 0.0 else r*.55)),
                // A perfectly horizontal sweep or forward YZ cleave is edge-on from the
                // owner's eyes. Cant the authored stroke planes, without camera billboarding.
                scale=Vec(if(vertical) 2.25 else r*2,1.0,if(spin) r*2 else r),
                pitch=if(!vertical && !spin) -.45 else 0.0,roll=if(vertical) PI/2 else p.roll,
                yaw=if(vertical) yaw+sign*.38 else if(spin) yaw else yaw-sign*.15,
                spin=if(spin) sign*PI*2 else if(vertical) -sign*.12 else sign*.3,startSize=.9,endSize=1.0,
                durationTicks=life,travel=if(vertical) Vec(0.0,-.15,0.0) else forward.mul(.12),
                rollTravel=if(vertical) -.12 else sign*.12,motion=if(spin) CoreMeshMotion.REVOLVE else CoreMeshMotion.SWEEP)
            base[0]=ribbon
            // A delayed separate curved wake has its own plane and shorter lifetime, not an identical stamped copy.
            base+=ribbon.copy(offset=ribbon.offset.add(0.0,.13,0.0),scale=ribbon.scale.mul(.82),
                delayTicks=3,durationTicks=(life*.8).toInt(),secondary=true,
                yaw=if(vertical) yaw-sign*.38 else ribbon.yaw,
                pitch=if(!vertical && !spin) -.25 else ribbon.pitch,
                roll=ribbon.roll+.2,spin=if(vertical) sign*.12 else ribbon.spin*.7)
            if(s.body=="cross_cut") {
                // Two upright opposing cuts, not the warrior's horizontal crescent recoloured.
                base[0]=ribbon.copy(pitch=-PI/2,roll=.72,scale=Vec(r,1.0,r),spin=0.0,rollTravel=-.2)
                base[base.lastIndex]=ribbon.copy(pitch=-PI/2,roll=-.72,scale=Vec(r,1.0,r),spin=0.0,
                    rollTravel=.2,spriteMirror=!ribbon.spriteMirror,delayTicks=3,secondary=true)
            }
        }
        // Each family keeps its own visual verb, but gains a spatial foreground/midground/aftermath.
        if(!ray && s.kind in setOf(CoreSceneKind.RAIN,CoreSceneKind.NOVA,CoreSceneKind.PULL,CoreSceneKind.HAMMER)) {
            val count=if(e.skill.ultimate) 6 else 4
            val yaw=atan2(e.direction.x(),e.direction.z())
            repeat(count) { i ->
                val a=yaw+i*PI*2/count+.27
                val reach=min(e.radius,4.5)
                val inward=s.kind==CoreSceneKind.PULL
                val from=if(inward) reach else .25
                val to=if(inward) -reach*.8 else reach*.65
                base+=CoreCombatMeshPart(s.impact,s.palette,Vec(sin(a)*from,.7,cos(a)*from),
                    Vec(.5,.5,.85),yaw=a,pitch=-PI/2,spin=(if(i%2==0) 1 else -1)*.8,
                    travel=Vec(sin(a)*to,.2,cos(a)*to),bend=Vec(0.0,.7,0.0),secondary=true,
                    durationTicks=life-2,delayTicks=3+i%3,startSize=.6,endSize=1.0,
                    motion=if(inward) CoreMeshMotion.GATHER else CoreMeshMotion.RADIATE,erode=true)
            }
        }
        if(e.sceneId in setOf("starfall","star_ult")) {
            // The core was already seen descending in PREPARE. PULSE is its breakup,
            // not a second stationary core and two unrelated orbit diagrams.
            val landed=base.removeAt(0)
            val floorIndex=base.indexOfFirst { it.ground }
            if(floorIndex>=0) {
                val floor=base[floorIndex]
                val size=min(e.radius,3.6)*2
                base[floorIndex]=floor.copy(shape="star_spark",atlas=CoreMeshAtlas.STELLAR_BURST,erode=false,
                    scale=Vec(size,1.0,size),startSize=.6,endSize=1.0,spin=.35,
                    delayTicks=2,durationTicks=life-2,motion=CoreMeshMotion.RADIATE)
            }
            base.addAll(0,stellarBurst(landed.offset,atan2(e.direction.x(),e.direction.z()),
                if(e.skill.ultimate) 6.0 else 4.3,life))
        }
        return base
    }

    private fun stellarBurst(at: Vec,yaw: Double,size: Double,life: Int): List<CoreCombatMeshPart> {
        val main=CoreCombatMeshPart("star_spark","astral",at,Vec(size,1.0,size),
            yaw=yaw-PI/4,pitch=-PI/2,roll=.18,startSize=.8,endSize=1.0,
            durationTicks=life,motion=CoreMeshMotion.SNAP,atlas=CoreMeshAtlas.STELLAR_BURST,rollTravel=.45)
        // Crossing planes provide depth and side visibility, rather than viewer-facing cards.
        // The cool secondary wake has a different clock, angle and upward drift.
        return listOf(main,main.copy(palette="ice",yaw=yaw+PI/4,roll=-.3,
            scale=Vec(size*.82,1.0,size*.82),delayTicks=2,durationTicks=life-2,
            travel=Vec(0.0,.2,0.0),secondary=true,rollTravel=-.5))
    }

    private fun nebulaField(e: CoreSkillEffect,life: Int): List<CoreCombatMeshPart> {
        val r=min(e.radius,CoreSkillScenes.get(e.sceneId).reach)
        // Six low cloud currents, three heights and two crossing directions. No falling
        // core, giant ground diagram or camera-facing card. Each damage wave refreshes a
        // bounded visual phrase; it does not create a second gameplay field or timer.
        val clouds=(0 until 6).map { i ->
            val a=i*PI/3+e.pulse*.45
            val tangent=Vec(cos(a),0.0,-sin(a))
            val delay=(i%3)*3
            CoreCombatMeshPart("nebula_wisp",if(i%3==1) "ice" else "astral",
                Vec(sin(a)*r*.22,.45+(i%3)*.24,cos(a)*r*.22).sub(tangent.mul(r*.13)),
                Vec(r*1.35,1.0,r*.85),yaw=a,pitch=if(i%2==0) -.6 else .45,roll=if(i%2==0) .08 else -.08,
                travel=tangent.mul(r*.26),bend=Vec(0.0,.16,0.0),spin=if(i%2==0) .25 else -.2,
                startSize=.85,endSize=1.0,durationTicks=life-delay,delayTicks=delay,
                motion=CoreMeshMotion.FLOAT,atlas=CoreMeshAtlas.NEBULA_STREAM,secondary=i>=3)
        }
        // Small stellar knots move with the cloud volume rather than outlining another logo.
        return clouds+(0 until 3).map { i ->
            val a=i*PI*2/3+e.pulse*.45
            CoreCombatMeshPart("star_seed","astral",Vec(sin(a)*r*.5,.65,cos(a)*r*.5),
                Vec(.25,.25,.25),pitch=-PI/2,spin=.7,travel=Vec(0.0,.4,0.0),
                delayTicks=4+i*2,durationTicks=life-4-i*2,startSize=.5,endSize=.2,
                motion=CoreMeshMotion.ORBIT,secondary=true,erode=true)
        }
    }

    private fun iceGarden(e: CoreSkillEffect,life: Int): List<CoreCombatMeshPart> {
        val r=min(e.radius,CoreSkillScenes.get(e.sceneId).reach)
        return (0 until 8).map { i ->
            val a=i*PI/4+e.pulse*.2
            val radius=r*if(i%2==0) .58 else .3
            val height=if(i%2==0) 1.35 else .8
            val delay=(i%4)*2
            CoreCombatMeshPart("ice_growth","ice",Vec(sin(a)*radius,.12,cos(a)*radius),
                Vec(.8,.8,height),yaw=a,pitch=-PI/2,ground=true,
                startSize=.02,endSize=1.0,durationTicks=life-delay,delayTicks=delay,
                motion=CoreMeshMotion.EMERGE,secondary=i%2!=0)
        }
    }

    private fun chainPull(e: CoreSkillEffect,life: Int): List<CoreCombatMeshPart> {
        val r=min(e.radius,CoreSkillScenes.get(e.sceneId).reach)
        val count=if(e.skill.ultimate) 6 else 4
        val yaw=atan2(e.direction.x(),e.direction.z())+e.pulse*.18
        return (0 until count).flatMap { i ->
            val a=yaw+i*PI*2/count
            val end=Vec(sin(a)*r,.65+(i%2)*.2,cos(a)*r)
            val travel=Vec(-sin(a)*(r-.45),.1,-cos(a)*(r-.45))
            val hook=CoreCombatMeshPart("chain_hook","gold",end,Vec(.75,.8,.9),yaw=a,
                pitch=-.3,travel=travel,startSize=1.0,endSize=.45,
                durationTicks=life,motion=CoreMeshMotion.GATHER,erode=true,secondary=i>=4)
            listOf(hook,hook.copy(shape="chain",scale=Vec(1.0,1.0,1.0),pitch=0.0,
                erode=false,chainAnchor=Vec(0.0,.9,0.0)))
        }
    }

    private fun arcaneWard(e: CoreSkillEffect,life: Int): List<CoreCombatMeshPart> {
        val yaw=atan2(e.direction.x(),e.direction.z())
        // Four opening facets around the torso. The forward sight line stays empty;
        // this is an activation envelope, not a new collision wall or buff timer.
        return listOf(-2,-1,1,2).mapIndexed { i,side ->
            val a=yaw+side*PI/3
            CoreCombatMeshPart("arcane_shield","lightning",Vec(sin(a)*.3,1.0,cos(a)*.3),
                Vec(.7,1.0,1.45),yaw=a,pitch=-PI/2,roll=side*.06,
                travel=Vec(sin(a)*.55,0.0,cos(a)*.55),startSize=.2,endSize=1.0,
                delayTicks=i%2,durationTicks=life-i%2,motion=CoreMeshMotion.EMERGE,
                followOwner=true,erode=true)
        }
    }

    private fun healingPhrase(e: CoreSkillEffect,life: Int): List<CoreCombatMeshPart> {
        val yaw=atan2(e.direction.x(),e.direction.z())
        val r=min(e.radius,CoreSkillScenes.get(e.sceneId).reach)
        fun local(x: Double,y: Double,z: Double)=Vec(cos(yaw)*x+sin(yaw)*z,y,-sin(yaw)*x+cos(yaw)*z)
        val result=mutableListOf<CoreCombatMeshPart>()
        val wings=e.sceneId in setOf("heal_shield","heal_ult")
        if(wings) for(side in listOf(-1,1)) repeat(4) { feather ->
            val delay=feather
            val large=e.skill.ultimate
            result+=CoreCombatMeshPart("feather_plume","life",
                local(side*(.5+feather*.04),1.1+feather*.03,-.15),
                Vec(if(large) 1.15 else 1.0,1.0,(if(large) 2.0 else 1.5)-feather*.13),
                yaw=yaw+side*.2,pitch=-PI/2,roll=-side*(.1+feather*.02),
                rollTravel=-side*(.2+feather*.26),travel=local(side*.18,.12,0.0),
                startSize=.45,endSize=1.0,delayTicks=delay,durationTicks=life-delay,
                motion=CoreMeshMotion.EMERGE,followOwner=e.sceneId=="heal_shield",erode=true,
                secondary=feather>=2)
        }
        if(e.sceneId=="heal_ring") repeat(6) { i ->
            val a=i*PI/3+e.pulse*.2
            val delay=(i%3)*2
            result+=CoreCombatMeshPart("healing_petal","life",Vec(sin(a)*r*.22,.28,cos(a)*r*.22),
                Vec(.7,.7,1.15),yaw=a,pitch=-.25,pitchTravel=-.9,
                travel=Vec(sin(a)*r*.55,.85,cos(a)*r*.55),bend=Vec(0.0,.3,0.0),
                startSize=.3,endSize=1.0,delayTicks=delay,durationTicks=life-delay,
                motion=CoreMeshMotion.FLOAT,erode=true)
        }
        if(e.sceneId in setOf("heal_wind","heal_ult")) repeat(if(wings) 6 else 8) { i ->
            val count=if(wings) 6 else 8
            val a=i*PI*2/count+e.pulse*.3
            val delay=(i%4)*2
            result+=CoreCombatMeshPart("feather_plume","life",Vec(sin(a)*r*.5,.3,cos(a)*r*.5),
                Vec(.75,.8,if(wings) 1.4 else 1.2),yaw=a,pitch=-PI/2+.25,roll=.2,
                spin=1.2,rollTravel=-.6,travel=Vec(0.0,if(wings) 1.9 else 1.5,0.0),
                startSize=.75,endSize=.55,delayTicks=delay,durationTicks=life-delay,
                motion=CoreMeshMotion.ORBIT,erode=true,secondary=if(wings) i>=4 else i>=6)
        }
        return result
    }

    private fun fireLanding(e: CoreSkillEffect,landed: Vec,life: Int): List<CoreCombatMeshPart> {
        // PREPARE owns the falling rock. On the authoritative landing it breaks into
        // a forked flame column and separate outward debris, never a second intact rock.
        val ultimate=e.skill.ultimate
        val yaw=atan2(e.direction.x(),e.direction.z())+e.pulse*.31
        val root=Vec(landed.x(),.12,landed.z())
        val count=if(ultimate) 5 else 3
        val flames=(0 until count).map { i ->
            val a=yaw+i*PI*2/count
            val center=i==0
            val delay=if(center) 0 else 2+i%2
            CoreCombatMeshPart("flame_plume","fire",root.add(sin(a)*if(center) 0.0 else .55,0.0,cos(a)*if(center) 0.0 else .55),
                Vec(if(center) 2.4 else 1.3,1.0,if(center) (if(ultimate) 2.8 else 1.9) else 1.5),
                yaw=a,pitch=-PI/2,roll=if(center) 0.0 else .25,
                travel=Vec(sin(a)*if(center) 0.0 else .45,.4,cos(a)*if(center) 0.0 else .45),
                startSize=.65,endSize=1.0,delayTicks=delay,durationTicks=life-delay,
                motion=CoreMeshMotion.SNAP,secondary=i>=3)
        }
        return flames+(0 until if(ultimate) 6 else 4).map { i ->
            val a=yaw+i*PI*2/(if(ultimate) 6 else 4)+.4
            val reach=min(e.radius,3.5)*.7
            CoreCombatMeshPart("meteor_rock","fire",root.add(0.0,.4,0.0),Vec(.38,.32,.42),
                yaw=a,pitch=.3,spin=1.8,pitchTravel=2.0,rollTravel=1.3,
                travel=Vec(sin(a)*reach,0.0,cos(a)*reach),bend=Vec(0.0,if(ultimate) 1.6 else 1.1,0.0),
                startSize=1.0,endSize=.3,delayTicks=1,durationTicks=life-1,
                motion=CoreMeshMotion.FLOAT,erode=true,secondary=i>=3)
        }
    }

    private fun frostWave(e: CoreSkillEffect,life: Int): List<CoreCombatMeshPart> {
        val r=min(e.radius,CoreSkillScenes.get(e.sceneId).reach)
        val count=if(e.skill.ultimate) 12 else 8
        // Low, outward-moving crests. Unlike the garden, these do not sit and grow
        // into tall pillars. Every gameplay pulse owns exactly one expanding wave.
        return (0 until count).map { i ->
            val a=i*PI*2/count+e.pulse*.23
            val from=.3
            val end=(r-.55).coerceAtLeast(from)
            val delay=i%2
            CoreCombatMeshPart("frost_crest","ice",Vec(sin(a)*from,.12,cos(a)*from),
                Vec(r*(if(e.skill.ultimate) .6 else .85),1.5,.8),yaw=a,pitch=-PI/2,
                travel=Vec(sin(a)*(end-from),0.0,cos(a)*(end-from)),
                startSize=.35,endSize=1.0,delayTicks=delay,durationTicks=life-delay,
                motion=CoreMeshMotion.RADIATE,erode=true,secondary=i%3==2)
        }
    }

    private fun stormDischarge(e: CoreSkillEffect,life: Int): List<CoreCombatMeshPart> {
        val r=min(e.radius,CoreSkillScenes.get(e.sceneId).reach)
        return (0 until 8).map { i ->
            val a=i*PI/4+e.pulse*.23+if(i%2==0) -.09 else .12
            val length=r*(.72+(i%3)*.08)
            val delay=if(i%2==0) 0 else 2
            CoreCombatMeshPart("storm_branch","lightning",Vec(sin(a)*length*.5,.55+(i%3)*.13,cos(a)*length*.5),
                Vec(2.0,.8,length),yaw=a,pitch=if(i%2==0) -.08 else .08,
                startSize=1.0,endSize=1.0,delayTicks=delay,durationTicks=life-delay,
                motion=CoreMeshMotion.LINEAR,secondary=i%2!=0)
        }
    }

    private fun assassinThrust(e: CoreSkillEffect,life: Int): List<CoreCombatMeshPart> {
        val s=CoreSkillScenes.get(e.sceneId)
        val r=min(e.radius,s.reach)
        val yaw=atan2(e.direction.x(),e.direction.z())
        val forward=Vec(sin(yaw),0.0,cos(yaw))
        val root=Vec(0.0,1.08,0.0).add(forward.mul(.18))
        val bladeLength=if(e.skill.ultimate) 1.25 else .8
        val distance=(r-.18-bladeLength).coerceAtLeast(.05)
        val blade=CoreCombatMeshPart(s.body,"shadow",root.add(forward.mul(bladeLength*.5)),
            Vec(if(e.skill.ultimate) .85 else .65,.65,bladeLength),yaw=yaw,roll=.35,
            travel=forward.mul(distance),startSize=1.0,endSize=1.0,
            durationTicks=life,motion=CoreMeshMotion.THRUST,erode=true)
        val wake=CoreCombatMeshPart("piercing_wake","shadow",root,Vec(1.15,1.15,r-.18),yaw=yaw,
            startSize=bladeLength/(r-.18),endSize=1.0,durationTicks=life,
            motion=CoreMeshMotion.THRUST,erode=true)
        // Chase leans into a crossing pair of speed wakes, contract adds four.
        // They are forward acceleration traces, never extra slashes or hit pulses.
        val traces=if(e.sceneId=="ass_stab") emptyList() else (0 until if(e.skill.ultimate) 4 else 2).map { i ->
            val a=yaw+i*PI*2/(if(e.skill.ultimate) 4 else 2)
            wake.copy(offset=root.add(cos(a)*.23,if(i%2==0) .18 else -.18,-sin(a)*.23),
                scale=Vec(.42,.42,r-.18),delayTicks=1,durationTicks=life-1,
                secondary=i>=2)
        }
        return listOf(blade,wake)+traces
    }

    private fun shadowDeparture(e: CoreSkillEffect,life: Int): List<CoreCombatMeshPart> {
        val yaw=atan2(e.direction.x(),e.direction.z())
        fun local(x: Double,y: Double,z: Double)=Vec(cos(yaw)*x+sin(yaw)*z,y,-sin(yaw)*x+cos(yaw)*z)
        val guard=e.sceneId=="ass_guard"
        val departure=e.endpoint!=CoreSkillEndpoint.ARRIVAL
        val result=mutableListOf<CoreCombatMeshPart>()
        if(guard) for(side in listOf(-1,1)) {
            result+=CoreCombatMeshPart("shadow_echo","shadow",local(side*.2,.9,-.15),Vec(.95,.95,.95),
                yaw=yaw,pitch=-PI/2,roll=side*.05,travel=local(side*.8,.05,-.65),
                rollTravel=side*.13,startSize=1.0,endSize=.9,delayTicks=if(side==1) 0 else 3,
                durationTicks=life-if(side==1) 0 else 3,motion=CoreMeshMotion.FLOAT)
        } else {
            result+=CoreCombatMeshPart("shadow_gate","shadow",Vec(0.0,1.0,0.0),Vec(1.3,1.0,2.0),
                yaw=yaw,pitch=-PI/2,startSize=if(departure) 1.0 else .2,endSize=if(departure) .05 else 1.0,
                durationTicks=12,motion=if(departure) CoreMeshMotion.GATHER else CoreMeshMotion.SNAP,erode=true)
        }
        repeat(if(guard) 4 else 3) { i ->
            val side=if(i%2==0) -1 else 1
            val delay=if(guard) 4+i*2 else i*2
            // The two primary wisps stay by the hands, in front of the owner.
            // Rear-only smoke would hide the activation from a first-person player.
            val front=guard && i<2
            val at=local(side*(if(front) .8 else if(guard) .55 else .35),.65+(i%2)*.55,
                if(front) .4 else if(guard) -.45 else 0.0)
            result+=CoreCombatMeshPart("shadow_wisp","shadow",at,Vec(2.1,1.0,2.7),
                yaw=yaw+side*.65,pitch=-PI/2,roll=side*.2,
                travel=local(side*(if(departure || guard) .7 else .25),.6,if(front) .35 else if(guard) -.4 else .15),
                startSize=if(departure || guard) .7 else .4,endSize=1.0,
                delayTicks=delay,durationTicks=life-delay,motion=CoreMeshMotion.FLOAT,
                rollTravel=-side*.45,atlas=CoreMeshAtlas.SHADOW_SMOKE,secondary=i>=2)
        }
        return result
    }

    private fun venomBite(e: CoreSkillEffect,life: Int): List<CoreCombatMeshPart> {
        val yaw=atan2(e.direction.x(),e.direction.z())
        val r=min(e.radius,CoreSkillScenes.get(e.sceneId).reach)
        fun local(x: Double,y: Double,z: Double)=Vec(cos(yaw)*x+sin(yaw)*z,y,-sin(yaw)*x+cos(yaw)*z)
        val fangs=listOf(-1,1).map { side ->
            CoreCombatMeshPart(if(side<0) "venom_fang" else "venom_fang_reverse","venom",
                local(side*.65,1.7,r*.38),Vec(1.0,.65,1.4),yaw=yaw,pitch=PI/2-.4,
                roll=side*.55,rollTravel=-side*.45,travel=local(-side*.42,-.35,r*.22),
                startSize=1.0,endSize=1.0,durationTicks=life,motion=CoreMeshMotion.THRUST,erode=true)
        }
        return fangs+venomDrops(local(0.0,1.0,r*.6),yaw,life-3).map { it.copy(delayTicks=3,secondary=true) }
    }

    private fun venomDrops(at: Vec,yaw: Double,life: Int): List<CoreCombatMeshPart> = (0 until 5).map { i ->
        val a=yaw+(i-2)*.45
        CoreCombatMeshPart("venom_bead","venom",at.add(sin(a)*.15,0.0,cos(a)*.15),
            Vec(.65,.65,.8),yaw=a,pitch=-PI/2,roll=(i-2)*.15,
            travel=Vec(sin(a)*.75,-.75,cos(a)*.75),bend=Vec(0.0,.35+(i%2)*.15,0.0),
            startSize=1.0,endSize=.2,durationTicks=life,motion=CoreMeshMotion.FLOAT,erode=true)
    }
}
