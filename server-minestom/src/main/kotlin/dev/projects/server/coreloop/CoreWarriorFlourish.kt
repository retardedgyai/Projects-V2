package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Middle-scale force surfaces between the accepted blade and the fine escaping particles. */
internal object CoreWarriorFlourish {
    fun owns(p:CoreCombatMeshPart)=p.shape.startsWith("war_flourish:")
    fun parts(e:CoreSkillEffect):List<CoreCombatMeshPart> {
        if(e.job!=CoreClass.WARRIOR || !e.valid || e.sceneId in CoreApprovedNormalV3.sceneIds) return emptyList()
        val yaw=atan2(e.direction.x(),e.direction.z())
        val reach=min(e.radius,CoreSkillScenes.get(e.sceneId).reach)
        fun local(x:Double,y:Double,z:Double)=Vec(cos(yaw)*x+sin(yaw)*z,y,-sin(yaw)*x+cos(yaw)*z)
        fun pair(clip:String,at:Vec,scale:Vec,pitch:Double,life:Int=20,roll:Double=0.0,follow:Boolean=false):List<CoreCombatMeshPart> =
            (if(clip in setOf("step_dust","impact_dust","sweep_dust")) listOf("body") else listOf("body","accent")).map { layer ->
                CoreCombatMeshPart("war_flourish:$clip:$layer","steel",at,scale,yaw=yaw,pitch=pitch,roll=roll,
                    durationTicks=life,startSize=1.0,endSize=1.0,secondary=layer=="accent",followOwner=follow,
                    ground=clip in setOf("step_dust","impact_dust","sweep_dust","stone_break","ultimate_rift","standard_foot"))
            }
        // Contact already has an authoritative, target-local primary. Do not
        // bury every hit in the same large burst on top of that primary.
        if(e.phase==CoreSkillVisualPhase.CONTACT) {
            return emptyList()
        }
        if(e.phase==CoreSkillVisualPhase.PREPARE) {
            val clip=when(e.sceneId) {
                "slam" -> "weight_load"
                "war_breach" -> "point_load"
                "war_ult" -> "ultimate_load"
                else -> return emptyList()
            }
            return pair(clip,local(.25,1.2,.85),Vec(1.35,.7,1.6),-PI/2,e.prepareDuration,follow=true)
        }
        return when(e.sceneId) {
            "dash" -> pair("step_dust",local(0.0,.14,.5),Vec(2.9,1.0,2.7),0.0,13)
            "war_breach" -> pair("pierce_shell",local(0.0,1.15,reach*.5),Vec(3.0,1.0,reach*1.2),-.12,18).let { shell ->
                // Two intersecting ribs give the forward puncture depth; never
                // face the same slash towards the camera or rotate it over time.
                shell + shell.first().copy(scale=Vec(2.2,1.0,reach*1.2),pitch=0.0,roll=PI/2,secondary=true)
            }
            "war_wound" -> pair("cut_thread",local(0.0,1.05,reach*.42),Vec(reach*1.1,1.0,2.0),-.55,14,-.45)
            "war_counter" -> pair("reversal",local(0.0,1.25,reach*.45),Vec(reach*1.4,1.0,2.7),-.55,18,.4)
            "slam" -> pair("stone_break",local(0.0,.16,reach*.5),Vec(reach*1.3,1.0,reach),0.0,20)+
                pair("impact_dust",local(0.0,.22,reach*.5),Vec(reach*1.4,1.0,reach*1.05),0.0,20)+
                pair("stone_spall",local(0.0,1.55,reach*.5),Vec(reach*1.15,1.0,3.0),-PI/2,20)
            "whirl" -> if(e.skill.motion == CoreSkillMotion.CONE)
                pair("sweep_pressure",local(0.0,1.1,reach*.4),Vec(reach*1.55,1.0,3.0),-.3,16)+
                    pair("sweep_dust",local(0.0,.16,reach*.4),Vec(reach*1.5,1.0,2.6),0.0,18)
                else pair(listOf("spin_a","spin_b","spin_c")[e.pulse%3],Vec(0.0,.45+e.pulse%3*.23,0.0),
                    Vec(reach*1.85,1.0,reach*1.85),.1,20)
            "war_ult" -> when(e.pulse%3) {
                0 -> pair("ultimate_rise",local(0.0,1.9,reach*.4),Vec(reach*1.05,1.0,3.7),-PI/2,18)
                1 -> pair("ultimate_cross",local(0.0,1.45,reach*.43),Vec(reach*1.5,1.0,2.7),-.55,18,.25)
                else -> pair("ultimate_rift",local(0.0,.18,reach*.5),Vec(reach*1.45,1.0,reach),0.0,20)+
                    pair("ultimate_fall",local(0.0,1.9,reach*.5),Vec(reach*1.1,1.0,3.7),-PI/2,20)
            }
            "war_cry" -> pair("voice_compression",local(0.0,1.5,.8),Vec(4.6,.7,2.9),-PI/2,18)
            "war_guard" -> pair("guard_edge",local(.5,1.2,.75),Vec(1.05,.5,1.5),-PI/2,12)
            "war_banner" -> pair("standard_foot",local(-.9,.14,.55),Vec(2.3,1.0,2.3),0.0,14)
            else -> emptyList()
        }
    }
    fun pose(p:CoreCombatMeshPart,age:Double):CoreMeshPose? {
        if(!owns(p)) return null
        val tokens=p.shape.split(':')
        val t=((age-p.delayTicks)/(p.durationTicks-1).coerceAtLeast(1)).coerceIn(0.0,1.0)
        val frame=floor(t*19).toInt()
        return CoreMeshPose(p.offset,p.scale,p.yaw,p.pitch,p.roll,
            "combat_vfx/warrior_flourish/${tokens[1]}_${tokens[2]}_$frame",
            age>=p.delayTicks && age<p.delayTicks+p.durationTicks)
    }
}
