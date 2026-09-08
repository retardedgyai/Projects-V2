package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** One hit -> one directional stroke. Whole-picture orbiting is never a cutting animation. */
internal object CoreSlashChoreography {
    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart> {
        val s=CoreSkillScenes.get(e.sceneId)
        val yaw=atan2(e.direction.x(),e.direction.z())
        val r=min(s.reach,e.radius.coerceAtLeast(.5))
        val reverse=((if(s.body=="slash_reverse") 1 else 0)+e.pulse)%2!=0
        val assassin=e.job==CoreClass.ASSASSIN
        val cleave=s.kind==CoreSceneKind.CLEAVE
        val cross=s.body=="cross_cut" || e.sceneId=="ass_ult"
        val circle=s.kind==CoreSceneKind.SPIN && !cross
        fun stroke(angle: Double=yaw,roll: Double=0.0,delay: Int=0,mirror: Boolean=reverse,
                   radial: Boolean=false)=CoreCombatMeshPart("directional_cut",s.palette,
            offset=Vec(sin(angle)*r*.5,if(cleave) 1.4 else 1.1,cos(angle)*r*.5),
            scale=Vec(if(cleave) 2.8 else if(radial) r*1.4 else r*2,1.0,if(radial) r*.65 else r),
            yaw=angle+(if(cleave) .4 else 0.0),pitch=if(cleave) 0.0 else -.4,roll=roll,
            startSize=1.0,endSize=1.0,durationTicks=if(radial) 6 else if(assassin) 10 else 14,
            delayTicks=delay,spriteMirror=mirror)
        return when {
            circle -> (0..3).map { i ->
                // Four fixed sectors form ONE traversing ring. No quad turns in place.
                stroke(yaw+(if(reverse) -1 else 1)*i*PI/2,delay=i*2,radial=true)
            }
            cross -> listOf(stroke(roll=if(reverse) -.7 else .7),
                stroke(roll=if(reverse) .7 else -.7,delay=1,mirror=!reverse))
            cleave -> listOf(stroke(roll=-PI/2,mirror=false))
            else -> listOf(stroke(roll=if(e.sceneId=="war_wound") -.3 else .08))
        }
    }

    fun pose(p: CoreCombatMeshPart,age: Double): CoreMeshPose? {
        if(p.shape!="directional_cut") return null
        val local=(age-p.delayTicks).coerceAtLeast(0.0)
        val peak=if(p.durationTicks<=6) 2.0 else if(p.durationTicks<=10) 3.0 else 4.0
        val frame=if(local<=peak) floor(local/peak*4).toInt() else
            5+floor(((local-peak-1)/(p.durationTicks-peak-2).coerceAtLeast(1.0)).coerceIn(0.0,1.0)*6).toInt()
        return CoreMeshPose(p.offset,p.scale,p.yaw,p.pitch,p.roll,
            "combat_vfx/stroke/cut_${if(p.spriteMirror) "reverse_" else ""}${p.palette}_${frame.coerceIn(0,11)}",
            age>=p.delayTicks && age<p.delayTicks+p.durationTicks)
    }
}
