package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Guard / shout / standard have no cosmetic sword hits or extra damage pulses. */
internal object CoreWarriorSupportChoreography {
    val sceneIds=setOf("war_guard","war_cry","war_banner")
    fun parts(e: CoreSkillEffect,life: Int): List<CoreCombatMeshPart> {
        val yaw=atan2(e.direction.x(),e.direction.z())
        fun local(x: Double,y: Double,z: Double)=Vec(cos(yaw)*x+sin(yaw)*z,y,-sin(yaw)*x+cos(yaw)*z)
        val prepare=e.phase==CoreSkillVisualPhase.PREPARE
        if(e.sceneId=="war_guard") {
            val blade=CoreCombatMeshPart("war_parry_blade","steel",local(.6,.82,.72),Vec(.85,.85,1.85),
                yaw=yaw,durationTicks=if(prepare) life else e.skill.duration,
                startSize=1.0,endSize=1.0,followOwner=true,erode=!prepare)
            return listOf(blade)+if(prepare) emptyList() else listOf(blade.copy(shape="war_parry_glint",durationTicks=12))
        }
        if(e.sceneId=="war_banner") {
            val root=local(-.9,if(prepare) .85 else .12,.55)
            val standard=CoreCombatMeshPart("war_standard","gold",root,Vec(1.4,1.4,1.4),yaw=yaw,
                travel=if(prepare) Vec(0.0,-.73,0.0) else Vec.ZERO,
                durationTicks=life,startSize=1.0,endSize=1.0,followOwner=prepare,erode=!prepare)
            if(prepare) return listOf(standard)
            val crest=(0 until 4).map { i ->
                val a=yaw+PI/4+i*PI/2
                CoreCombatMeshPart("war_rally_streamer","gold",root.add(sin(a)*.35,1.0,cos(a)*.35),Vec(.9,.8,1.8),
                    yaw=a,pitch=-.2,travel=Vec(sin(a)*2.2,.3,cos(a)*2.2),bend=Vec(0.0,.6,0.0),
                    durationTicks=28,startSize=1.0,endSize=.4,motion=CoreMeshMotion.FLOAT,erode=true)
            }
            return listOf(standard)+crest+(0 until 4).map { i ->
                val a=yaw+i*PI/2
                CoreCombatMeshPart("oath_stone_chip","steel",root,Vec(.3,.3,.4),yaw=a,pitch=i*.4,
                    travel=Vec(sin(a)*.75,0.0,cos(a)*.75),bend=Vec(0.0,.65,0.0),
                    spin=1.5,durationTicks=16,startSize=1.0,endSize=.2,motion=CoreMeshMotion.FLOAT,erode=true,secondary=true)
            }
        }
        if(prepare) return listOf(-1,1).map { side ->
            CoreCombatMeshPart("war_voice_band","steel",local(side*.4,1.3,.4),Vec(.5,.6,.5),yaw=yaw,pitch=-PI/2,
                travel=local(-side*.28,0.0,0.0),durationTicks=life,startSize=1.0,endSize=.4,
                motion=CoreMeshMotion.GATHER,followOwner=true)
        }
        // Three successive voice fronts, each with its own outward translation and
        // breaking rim. They are sound echoes of one shield grant, not three hits.
        return listOf(0.0,PI).flatMap { side -> (0 until 3).map { echo ->
            val a=yaw+side;val delay=echo*4
            CoreCombatMeshPart("war_voice_band",if(echo==1) "gold" else "steel",
                Vec(sin(a)*.35,1.25,cos(a)*.35),Vec(1.8+echo*.2,1.0,1.6+echo*.2),
                yaw=a,pitch=-PI/2,travel=Vec(sin(a)*.16*(life-delay-1),.05,cos(a)*.16*(life-delay-1)),
                startSize=.3,endSize=1.0,delayTicks=delay,durationTicks=life-delay,
                motion=CoreMeshMotion.SWEEP,erode=true)
        } }
    }

    fun pose(p: CoreCombatMeshPart,age: Double): CoreMeshPose? {
        val local=(age-p.delayTicks).coerceAtLeast(0.0)
        val visible=age>=p.delayTicks && local<p.durationTicks
        val t=(local/(p.durationTicks-1).coerceAtLeast(1)).coerceIn(0.0,1.0)
        if(p.shape=="war_voice_band" && !p.followOwner) {
            val grow=p.startSize+(p.endSize-p.startSize)*(1-(1-(local/6).coerceIn(0.0,1.0)).pow(3))
            val fade=floor(((t-.3)/.7).coerceIn(0.0,1.0)*7).toInt()
            return CoreMeshPose(p.offset.add(p.travel.mul(t)),p.scale.mul(grow),p.yaw,p.pitch,p.roll,
                "combat_vfx/war_voice_band_${p.palette}"+(if(fade>0) "_fade$fade" else ""),visible)
        }
        if(p.shape=="war_standard") {
            val wind=if(!p.erode && p.durationTicks==1) 1.0 else t
            val root=p.offset.add(p.travel.mul(wind*wind))
            val frame=if(!p.erode) floor(wind*3).toInt() else if(local<2) 3 else 4+((local-2).toInt()/2)%8
            val fade=if(p.erode) floor(((local-(p.durationTicks-10))/9).coerceIn(0.0,1.0)*7).toInt() else 0
            return CoreMeshPose(root.add(0.0,p.scale.y()*.5,0.0),p.scale,p.yaw,0.0,0.0,
                "combat_vfx/warrior/standard_${frame}_$fade",visible)
        }
        if(p.shape !in setOf("war_parry_blade","war_parry_glint")) return null
        val ready=if(!p.erode) 0.0 else 1-(1-(local/3).coerceIn(0.0,1.0)).pow(3)
        val pitch=-1.05+(-PI/2+1.05)*ready
        val roll=.2+.5*ready
        // R_yaw * R_roll * R_pitch, matching ItemDisplay: rotate about the grip.
        val y=-sin(pitch);val z=cos(pitch);val x=-y*sin(roll)
        val axis=Vec(cos(p.yaw)*x+sin(p.yaw)*z,y*cos(roll),-sin(p.yaw)*x+cos(p.yaw)*z)
        val glint=p.shape=="war_parry_glint"
        val along=if(glint) .4+.4*(local/8).coerceIn(0.0,1.0) else .5
        val fade=if(p.erode) floor(((local-(p.durationTicks-6))/5).coerceIn(0.0,1.0)*7).toInt() else 0
        return CoreMeshPose(p.offset.add(axis.mul(p.scale.z()*along)),if(glint) Vec(.45,.45,.5) else p.scale,
            p.yaw,pitch,roll,"combat_vfx/${p.shape}_steel"+(if(fade>0) "_fade$fade" else ""),visible)
    }
}
