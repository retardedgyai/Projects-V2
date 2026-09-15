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
            "dash" -> pair("step_dust",local(0.0,.14,.5),Vec(2.2,1.0,2.3),0.0,13)
            "war_breach" -> pair("pierce_shell",local(0.0,1.0,reach*.5),Vec(2.3,1.0,reach*1.1),-.12,18)
            "war_wound" -> pair("cut_thread",local(0.0,1.05,reach*.42),Vec(reach,1.0,1.7),-.45,14,-.3)
            "war_counter" -> pair("reversal",local(0.0,1.15,reach*.45),Vec(reach*1.3,1.0,2.3),-.45,18,.4)
            "slam" -> pair("stone_break",local(0.0,.16,reach*.5),Vec(reach*1.3,1.0,reach),0.0,20)+
                pair("impact_dust",local(0.0,.22,reach*.5),Vec(reach*1.4,1.0,reach*1.05),0.0,20)+
                pair("stone_spall",local(0.0,1.1,reach*.5),Vec(reach*.95,1.0,2.1),-PI/2,20)
            "whirl" -> if(e.skill.motion == CoreSkillMotion.CONE)
                pair("sweep_pressure",local(0.0,1.0,reach*.4),Vec(reach*1.5,1.0,2.6),-.25,16)+
                    pair("sweep_dust",local(0.0,.16,reach*.4),Vec(reach*1.5,1.0,2.6),0.0,18)
                else pair(listOf("spin_a","spin_b","spin_c")[e.pulse%3],Vec(0.0,.45+e.pulse%3*.23,0.0),
                    Vec(reach*1.85,1.0,reach*1.85),.1,20)
            "war_ult" -> when(e.pulse%3) {
                0 -> pair("ultimate_rise",local(0.0,1.5,reach*.4),Vec(reach*.95,1.0,3.0),-PI/2,18)
                1 -> pair("ultimate_cross",local(0.0,1.35,reach*.43),Vec(reach*1.4,1.0,2.4),-.45,18,.25)
                else -> pair("ultimate_rift",local(0.0,.18,reach*.5),Vec(reach*1.45,1.0,reach),0.0,20)+
                    pair("ultimate_fall",local(0.0,1.45,reach*.5),Vec(reach*.85,1.0,2.8),-PI/2,20)
            }
            "war_cry" -> pair("voice_compression",local(0.0,1.3,.8),Vec(2.8,.7,2.1),-PI/2,18)
            "war_guard" -> pair("guard_edge",local(.5,1.2,.75),Vec(.8,.5,1.2),-PI/2,12)
            "war_banner" -> pair("standard_foot",local(-.9,.14,.55),Vec(1.6,1.0,1.6),0.0,14)
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
