package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** The ten ranger skills: instant shot beats, moving wakes, falling volleys and a physical snare. */
internal object CoreRangerChoreography {
    fun parts(e: CoreSkillEffect, raw: List<CoreCombatMeshPart>, life: Int): List<CoreCombatMeshPart> {
        val s=CoreSkillScenes.get(e.sceneId)
        if(e.phase==CoreSkillVisualPhase.CONTACT) return contact(e,life)
        if(s.kind==CoreSceneKind.RAIN) return rain(e,life,e.phase==CoreSkillVisualPhase.PREPARE)
        if(e.sceneId=="hunt_trap") return snare(e,life,e.phase==CoreSkillVisualPhase.PREPARE)
        if(e.phase==CoreSkillVisualPhase.PREPARE) {
            if(s.kind==CoreSceneKind.RAY) return draw(e,life)
            return raw.map { it.copy(durationTicks=life,motion=CoreMeshMotion.GATHER,erode=false) }
        }
        if(s.kind==CoreSceneKind.FAN) return fan(e,life)
        // Server ray damage is immediate. Do not delay the hit cue with a slow
        // cosmetic projectile. The arrow flashes at the endpoint; the wake unravels.
        return listOf(raw.first().copy(durationTicks=6,startSize=1.0,endSize=1.0,erode=true))+
            raw.drop(1).map { it.copy(shape="shot_wake",scale=Vec(if(e.skill.ultimate) 1.4 else 1.0,
                if(e.skill.ultimate) 1.4 else 1.0,it.scale.z()),durationTicks=life,
                startSize=1.0,endSize=1.0,secondary=false) }
    }

    private fun draw(e: CoreSkillEffect,life: Int): List<CoreCombatMeshPart> {
        val s=CoreSkillScenes.get(e.sceneId)
        val yaw=atan2(e.direction.x(),e.direction.z())
        val pitch=-atan2(e.direction.y(),hypot(e.direction.x(),e.direction.z()))
        val forward=Vec(sin(yaw),0.0,cos(yaw))
        val side=Vec(cos(yaw),0.0,-sin(yaw))
        val center=Vec(0.0,1.15,0.0).add(side.mul(.35)).add(forward.mul(.65))
        val arrow=CoreCombatMeshPart(s.body,s.palette,center,Vec(.6,.6,if(e.skill.ultimate) 1.3 else .9),
            yaw=yaw,pitch=pitch,travel=forward.mul(-.25),startSize=.75,endSize=1.0,
            durationTicks=life,motion=CoreMeshMotion.GATHER,followOwner=true)
        val tension=listOf(-1,1).map { sign ->
            CoreCombatMeshPart("draw_tension","hunter",center.add(side.mul(sign*.42)),Vec(.7,.7,.85),
                yaw=yaw,pitch=pitch,roll=sign*.3,travel=side.mul(-sign*.3),
                startSize=.3,endSize=1.0,durationTicks=life,motion=CoreMeshMotion.GATHER,
                followOwner=true,secondary=true)
        }
        return listOf(arrow)+tension
    }

    private fun fan(e: CoreSkillEffect,life: Int): List<CoreCombatMeshPart> {
        val yaw=atan2(e.direction.x(),e.direction.z())
        val reach=e.radius.coerceAtMost(7.0)
        return (-2..2).flatMap { i ->
            val a=yaw+i*.4
            val direction=Vec(sin(a),0.0,cos(a))
            val length=min(1.2,reach)
            listOf(CoreCombatMeshPart("frost_arrow","ice",Vec(0.0,1.05,0.0).add(direction.mul(reach-length*.5)),
                Vec(.7,.7,length),yaw=a,startSize=1.0,endSize=1.0,durationTicks=6,erode=true,secondary=i%2!=0),
                CoreCombatMeshPart("shot_wake","ice",Vec(0.0,1.05,0.0).add(direction.mul(reach*.5)),
                    Vec(.8,.8,reach),yaw=a,startSize=1.0,endSize=1.0,durationTicks=life,
                    secondary=false))
        }
    }

    private fun rain(e: CoreSkillEffect,life: Int,prepare: Boolean): List<CoreCombatMeshPart> {
        val count=if(e.skill.ultimate) 8 else 6
        val radius=e.radius.coerceAtMost(7.0)
        val body=if(e.skill.ultimate) "barbed_rain_cluster" else "rain_cluster"
        return (0 until count).flatMap { i ->
            val a=i*PI*2/count+e.pulse*2.39996
            val distance=radius*(if(i%2==0) .28 else .72)
            val target=Vec(sin(a)*distance,.12,cos(a)*distance)
            val direction=Vec(sin(a)*.22,-1.0,cos(a)*.22).normalize()
            val length=if(e.skill.ultimate) 1.6 else 1.3
            val center=target.sub(direction.mul(length*.5))
            val arrow=CoreCombatMeshPart(body,"hunter",if(prepare) center.sub(direction.mul(4.0)) else center,
                Vec(.75,.75,length),yaw=a,pitch=-atan2(direction.y(),hypot(direction.x(),direction.z())),
                travel=if(prepare) direction.mul(4.0) else Vec.ZERO,startSize=1.0,endSize=1.0,
                durationTicks=if(prepare) life else 6,motion=if(prepare) CoreMeshMotion.FALL else CoreMeshMotion.LINEAR,
                erode=!prepare)
            if(prepare) listOf(arrow) else listOf(arrow,
                CoreCombatMeshPart("fletching","hunter",target.add(0.0,.25,0.0),Vec(.55,.55,.9),
                    yaw=a,pitch=-PI/2,roll=.4,rollTravel=-1.3,spin=.8,
                    travel=Vec(cos(a)*.7,.6,-sin(a)*.7),bend=Vec(0.0,.4,0.0),
                    startSize=1.0,endSize=.2,delayTicks=1,durationTicks=life-1,
                    motion=CoreMeshMotion.FLOAT,erode=true,secondary=true))
        }
    }

    private fun snare(e: CoreSkillEffect,life: Int,prepare: Boolean): List<CoreCombatMeshPart> {
        // Subsequent server PREPARE beats must not add a second trap over the
        // already opening jaws. Each actual pulse owns one short close/release.
        if(prepare && e.pulse>0) return emptyList()
        val yaw=atan2(e.direction.x(),e.direction.z())
        val jaws=listOf(-1,1).map { side ->
            CoreCombatMeshPart(if(side==1) "snare_jaw" else "snare_jaw_reverse","hunter",
                Vec(0.0,.14,0.0),Vec(2.2,2.2,2.2),yaw=yaw,
                pitch=if(prepare) -side*.65 else -side*1.2,
                pitchTravel=if(prepare) side*.65 else side*1.2,
                durationTicks=if(prepare) life else 8,startSize=1.0,endSize=1.0,
                motion=if(prepare) CoreMeshMotion.GATHER else CoreMeshMotion.LINEAR,ground=true)
        }
        if(prepare) return jaws
        return jaws+(0 until 5).map { i ->
            val a=i*PI*2/5+e.pulse*.45
            CoreCombatMeshPart("venom_bead","venom",Vec(sin(a)*.35,.45,cos(a)*.35),Vec(.6,.6,.8),
                yaw=a,pitch=-PI/2,travel=Vec(sin(a)*.35,.15,cos(a)*.35),bend=Vec(0.0,.65,0.0),
                startSize=1.0,endSize=.2,durationTicks=life,motion=CoreMeshMotion.FLOAT,erode=true,secondary=true)
        }
    }

    private fun contact(e: CoreSkillEffect,life: Int): List<CoreCombatMeshPart> {
        val yaw=atan2(e.direction.x(),e.direction.z())
        if(e.sceneId=="hunt_mark") return (0 until 4).map { i ->
            val a=i*PI/2+PI/4
            val side=Vec(cos(yaw),0.0,-sin(yaw))
            val radial=side.mul(cos(a)*.7).add(0.0,sin(a)*.7,0.0)
            CoreCombatMeshPart("mark_hook","hunter",Vec(0.0,1.0,0.0).add(radial),Vec(.65,1.0,.65),
                yaw=yaw,pitch=-PI/2,roll=a-PI/4,travel=radial.mul(-.4),
                startSize=1.0,endSize=1.0,durationTicks=24,motion=CoreMeshMotion.GATHER,erode=true)
        }
        val poison=e.sceneId=="hunt_trap"
        return (0 until if(e.skill.ultimate) 6 else 4).map { i ->
            val a=yaw+i*PI*2/(if(e.skill.ultimate) 6 else 4)
            CoreCombatMeshPart(if(poison) "venom_bead" else "fletching",if(poison) "venom" else "hunter",
                Vec(0.0,1.0,0.0),Vec(.45,.45,.7),yaw=a,pitch=-PI/2,roll=i*.3,rollTravel=.8,
                travel=Vec(sin(a)*.7,if(poison) -.65 else .2,cos(a)*.7),bend=Vec(0.0,.45,0.0),
                startSize=1.0,endSize=.25,durationTicks=life,motion=CoreMeshMotion.FLOAT,erode=true,secondary=i>=4)
        }
    }
}
