package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** An effect's readable phrase is longer than its damage beat. Never feeds back into hit timing. */
internal enum class CoreMeshMotion { LINEAR, SNAP, SWEEP, REVOLVE, THRUST, FALL, RADIATE, ORBIT, GATHER, FLOAT }
internal data class CoreMeshPose(val offset: Vec, val scale: Vec, val yaw: Double, val pitch: Double,
    val roll: Double, val model: String, val visible: Boolean)

internal object CoreSkillChoreography {
    // Explicit durations in server ticks. Assassin is not allowed to disappear in three frames.
    private val duration = mapOf(
        "normal_sweep" to 16,"normal_reverse" to 16,"normal_finish" to 24,
        "dash" to 20,"slam" to 28,"whirl" to 20,"war_guard" to 24,"war_wound" to 18,
        "war_counter" to 22,"war_cry" to 28,"war_breach" to 22,"war_ult" to 30,"war_banner" to 40,
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
        val localAge=(age-p.delayTicks).coerceAtLeast(0.0)
        val t=((age-p.delayTicks)/(p.durationTicks-1).coerceAtLeast(1)).coerceIn(0.0,1.0)
        val u=when(p.motion) {
            CoreMeshMotion.LINEAR,CoreMeshMotion.ORBIT,CoreMeshMotion.FLOAT -> t
            CoreMeshMotion.FALL -> t*t
            CoreMeshMotion.GATHER -> (localAge/min(p.durationTicks*.5,10.0)).coerceIn(0.0,1.0).pow(2)
            CoreMeshMotion.THRUST,CoreMeshMotion.SWEEP -> ease(localAge/5.0)
            CoreMeshMotion.REVOLVE -> (localAge/6.0).coerceIn(0.0,1.0)
            CoreMeshMotion.SNAP,CoreMeshMotion.RADIATE -> ease(t/.65)
        }
        val grow=p.startSize+(p.endSize-p.startSize)*u
        val arc=sin(PI*t)
        val travel=p.travel.mul(u).add(p.bend.mul(arc))
        val angle=if(p.motion==CoreMeshMotion.ORBIT) p.spin*t else p.spin*u
        val at=if(p.motion==CoreMeshMotion.ORBIT) {
            Vec(cos(angle)*p.offset.x()+sin(angle)*p.offset.z(),p.offset.y(),-sin(angle)*p.offset.x()+cos(angle)*p.offset.z()).add(travel)
        } else p.offset.add(travel)
        // Stroke peaks in 0.20s regardless of the longer aftermath. Extending readability
        // must not postpone the visually strongest beat until half a second after damage.
        val stage=if(p.sprite) {
            if(localAge<=4.0) floor(localAge/4.0*7).toInt() else
                8+floor(((localAge-5)/(p.durationTicks-6).coerceAtLeast(1)).coerceIn(0.0,1.0)*7).toInt()
        } else floor(((t-.42)/.58).coerceIn(0.0,1.0)*7).toInt()
        val model=if(p.sprite) "combat_vfx/ribbon/slash_${if(p.spriteMirror) "reverse_" else ""}${p.palette}_$stage" else
            "combat_vfx/${p.shape}_${p.palette}" + if(p.erode && stage>0) "_fade$stage" else ""
        return CoreMeshPose(at,p.scale.mul(grow),p.yaw+angle,p.pitch+p.pitchTravel*u,p.roll+p.rollTravel*u,
            model,age>=p.delayTicks && age<p.delayTicks+p.durationTicks)
    }

    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart> {
        val raw=CoreCombatMeshArt.parts(e)
        if(raw.isEmpty()) return raw
        val s=CoreSkillScenes.get(e.sceneId)
        val life=duration(e)
        if(e.phase==CoreSkillVisualPhase.PREPARE) return raw.map { it.copy(
            motion=if(s.kind==CoreSceneKind.RAIN) CoreMeshMotion.FALL else CoreMeshMotion.GATHER,
            durationTicks=life,erode=false) }
        if(e.phase==CoreSkillVisualPhase.CONTACT) {
            val p=raw.first()
            return listOf(p.copy(durationTicks=14,motion=CoreMeshMotion.SNAP,erode=true,startSize=.35,endSize=1.35),
                p.copy(offset=p.offset.add(.25,.15,.02),scale=p.scale.mul(.5),delayTicks=3,durationTicks=16,
                    motion=CoreMeshMotion.RADIATE,travel=Vec(.5,.55,.1),spin=.7,secondary=true,erode=true))
        }
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
            val ribbon=p.copy(sprite=true,spriteMirror=sign<0,erode=false,
                offset=Vec(0.0,if(vertical) 1.25 else 1.1,0.0).add(forward.mul(if(spin) 0.0 else r*.55)),
                scale=Vec(if(vertical) 2.25 else r*2,1.0,if(spin) r*2 else r),pitch=0.0,roll=if(vertical) PI/2 else p.roll,
                yaw=if(spin) yaw else yaw-sign*.15,spin=if(spin) sign*PI*2 else sign*.3,startSize=.9,endSize=1.0,
                durationTicks=life,travel=if(vertical) Vec(0.0,-.15,0.0) else forward.mul(.12),
                rollTravel=if(vertical) -.12 else sign*.12,motion=if(spin) CoreMeshMotion.REVOLVE else CoreMeshMotion.SWEEP)
            base[0]=ribbon
            // A delayed separate curved wake has its own plane and shorter lifetime, not an identical stamped copy.
            base+=ribbon.copy(offset=ribbon.offset.add(0.0,.13,0.0),scale=ribbon.scale.mul(.82),
                delayTicks=3,durationTicks=(life*.8).toInt(),secondary=true,roll=ribbon.roll+.2,spin=ribbon.spin*.7)
            if(s.body=="cross_cut") {
                // Two upright opposing cuts, not the warrior's horizontal crescent recoloured.
                base[0]=ribbon.copy(pitch=-PI/2,roll=.72,scale=Vec(r,1.0,r),spin=0.0,rollTravel=-.2)
                base[base.lastIndex]=ribbon.copy(pitch=-PI/2,roll=-.72,scale=Vec(r,1.0,r),spin=0.0,
                    rollTravel=.2,spriteMirror=!ribbon.spriteMirror,delayTicks=3,secondary=true)
            }
            if(s.body=="poison_fang") {
                // Keep the identifying paired fangs at the striking point, with a smaller toxic wake.
                base[0]=p.copy(durationTicks=12,startSize=.75,endSize=1.0,motion=CoreMeshMotion.SWEEP,erode=true)
                base[base.lastIndex]=ribbon.copy(scale=ribbon.scale.mul(.75),delayTicks=1,secondary=true)
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
        if(s.kind==CoreSceneKind.RAIN && s.palette=="astral") {
            repeat(2) { i -> base+=CoreCombatMeshPart("star_orbit",s.palette,Vec(0.0,.85,0.0),Vec(2.5,1.0,2.5),
                pitch=if(i==0) .55 else -.55,spin=if(i==0) 1.5 else -1.5,secondary=true,
                startSize=.3,endSize=1.5,durationTicks=life,delayTicks=i*3,motion=CoreMeshMotion.RADIATE,erode=true) }
        }
        return base
    }
}
