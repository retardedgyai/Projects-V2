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
            listOf("body","accent").map { layer ->
                CoreCombatMeshPart("war_flourish:$clip:$layer","steel",at,scale,yaw=yaw,pitch=pitch,roll=roll,
                    durationTicks=life,startSize=1.0,endSize=1.0,secondary=true,followOwner=follow)
            }
        val support=e.sceneId in CoreWarriorSupportChoreography.sceneIds
        if(e.phase==CoreSkillVisualPhase.CONTACT) {
            if(support && e.sceneId!="war_guard") return emptyList()
            return pair("burst",Vec(0.0,1.0,0.0),Vec(2.0,1.0,1.8),-PI/2,8)
        }
        if(e.phase==CoreSkillVisualPhase.PREPARE) {
            if(e.sceneId !in setOf("slam","war_breach","war_ult","war_cry")) return emptyList()
            return pair("gather",local(.25,1.05,.75),Vec(1.35,.7,1.6),-PI/2,e.prepareDuration,follow=true)
        }
        return when(e.sceneId) {
            "dash","war_breach" -> pair("jet",local(0.0,.95,reach*.45),Vec(2.15,1.0,reach*.95),-.12,
                if(e.sceneId=="dash")13 else 20)
            "war_wound" -> pair("fan",local(0.0,1.05,reach*.42),Vec(reach*1.15,1.0,2.0),-.45,16,-.3)
            "war_counter" -> pair("fan",local(0.0,1.15,reach*.45),Vec(reach*1.4,1.0,2.3),-.45,20,.4)
            "slam" -> pair("eruption",local(0.0,1.2,reach*.5),Vec(reach*.95,.8,2.6),-PI/2)+
                pair("fan",local(0.0,.3,reach*.45),Vec(reach*1.3,.6,2.2),.1,18)
            "whirl" -> pair(listOf("spin_a","spin_b","spin_c")[e.pulse%3],Vec(0.0,.45+e.pulse%3*.23,0.0),
                Vec(reach*1.85,1.0,reach*1.85),.1,20)
            "war_ult" -> when(e.pulse%3) {
                0 -> pair("lift",local(0.0,1.4,reach*.4),Vec(reach*.8,1.0,3.1),-PI/2)
                1 -> pair("fan",local(0.0,1.3,reach*.43),Vec(reach*1.5,1.0,2.5),-.35,20,.3)
                else -> pair("eruption",local(0.0,1.45,reach*.5),Vec(reach*1.2,1.0,3.2),-PI/2)+
                    pair("fan",local(0.0,.35,reach*.45),Vec(reach*1.65,.7,2.5),.05)
            }
            "war_cry" -> pair("rally",local(0.0,1.25,.4),Vec(3.6,.7,1.8),-PI/2,20)
            "war_guard" -> pair("gather",local(.5,1.2,.75),Vec(.8,.5,1.1),-PI/2,10)
            // The approved flag already supplies its large form and four flowing ribbons.
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
