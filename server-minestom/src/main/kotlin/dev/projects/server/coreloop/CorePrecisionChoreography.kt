package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** The remaining mark shots and lightning step. Hit/movement authority remains in CorePlayerCombat. */
internal object CorePrecisionChoreography {
    val sceneIds=setOf("mage_mark","mage_blink","ass_needle")
    fun parts(e: CoreSkillEffect,raw: List<CoreCombatMeshPart>,life: Int): List<CoreCombatMeshPart> {
        val yaw=atan2(e.direction.x(),e.direction.z())
        fun local(x: Double,y: Double,z: Double)=Vec(cos(yaw)*x+sin(yaw)*z,y,-sin(yaw)*x+cos(yaw)*z)
        val needle=e.sceneId=="ass_needle"
        val prepare=e.phase==CoreSkillVisualPhase.PREPARE
        if(e.phase==CoreSkillVisualPhase.CONTACT) {
            val mark=raw.first().copy(scale=Vec(.8,.8,1.1),durationTicks=18,
                startSize=.65,endSize=1.0,motion=CoreMeshMotion.SNAP,erode=true)
            if(needle) return listOf(mark)+listOf(-1,1).map { side ->
                CoreCombatMeshPart("needle_rift","shadow",local(side*.22,1.0,0.0),Vec(.65,.65,1.0),
                    yaw=yaw,pitch=-PI/2,roll=side*.45,travel=local(side*.25,.15,0.0),
                    durationTicks=18,startSize=1.0,endSize=1.0,motion=CoreMeshMotion.FLOAT,secondary=side<0)
            }
            return listOf(mark)+(0 until 3).map { i ->
                val a=yaw+i*PI*2/3
                CoreCombatMeshPart("storm_branch","lightning",Vec(sin(a)*.25,1.0,cos(a)*.25),Vec(.75,.75,1.1),
                    yaw=a,pitch=-.3,travel=Vec(sin(a)*.5,.3,cos(a)*.5),
                    durationTicks=18,startSize=.7,endSize=1.0,motion=CoreMeshMotion.THRUST,secondary=i>0)
            }
        }
        if(e.sceneId=="mage_blink") {
            val depart=e.endpoint==CoreSkillEndpoint.DEPARTURE || prepare
            // Four separate upright fault lines close into the body or spring away.
            // There is no filled gate image, slash or cosmetic explosion at either endpoint.
            val arcs=(0 until 4).map { i ->
                val side=if(i%2==0) -1 else 1
                CoreCombatMeshPart("storm_branch","lightning",local(side*(if(depart) .6 else .2),.65+i/2*.7,.15),
                    Vec(.8,.8,1.2),yaw=yaw+side*.35,pitch=-PI/2,roll=side*.15,
                    travel=local(side*(if(depart) -.45 else .9),if(depart) -.15 else .3,0.0),
                    startSize=if(prepare) .3 else if(depart) 1.0 else .55,endSize=if(depart) .2 else 1.0,
                    durationTicks=life,motion=if(depart) CoreMeshMotion.GATHER else CoreMeshMotion.THRUST,followOwner=prepare)
            }
            return arcs+if(prepare) emptyList() else listOf(-1,1).map { side ->
                CoreCombatMeshPart("electric_shard","lightning",local(side*.3,1.0,.25),Vec(.45,.45,.75),
                    yaw=yaw,pitch=-PI/2,roll=side*.5,rollTravel=side*.8,
                    travel=local(side*(if(depart) -.25 else 1.0),if(depart) -.5 else .6,.2),
                    startSize=1.0,endSize=.2,durationTicks=life,motion=CoreMeshMotion.FLOAT,erode=true,secondary=true)
            }
        }
        if(prepare) {
            if(needle) return listOf(CoreCombatMeshPart("shadow_pin","shadow",local(.4,1.15,.55),Vec(.5,.5,.85),
                yaw=yaw,pitch=-atan2(e.direction.y(),hypot(e.direction.x(),e.direction.z())),
                travel=local(0.0,0.0,-.2),durationTicks=life,startSize=1.0,endSize=1.0,motion=CoreMeshMotion.GATHER,followOwner=true))
            return listOf(-1,1).map { side ->
                CoreCombatMeshPart("storm_branch","lightning",local(side*.4,1.15,.6),Vec(.6,.6,.8),yaw=yaw,pitch=-PI/2,
                    travel=local(-side*.2,0.0,0.0),durationTicks=life,startSize=.4,endSize=1.0,
                    motion=CoreMeshMotion.GATHER,followOwner=true)
            }
        }
        val trail=raw.last()
        val stream=trail.copy(shape=if(needle) "needle_rift" else "inscribed_bolt",
            scale=Vec(if(needle) 1.05 else 1.15,if(needle) 1.05 else 1.15,trail.scale.z()),
            durationTicks=life,startSize=1.0,endSize=1.0,secondary=false,erode=false)
        return if(!needle) listOf(stream) else listOf(stream,
            raw.first().copy(shape="shadow_pin",durationTicks=7,startSize=1.0,endSize=1.0,erode=true,secondary=false))
    }

    fun lightningFrame(p: CoreCombatMeshPart,age: Double): Int {
        val local=(age-p.delayTicks).coerceAtLeast(0.0)
        val t=(local/(p.durationTicks-1).coerceAtLeast(1)).coerceIn(0.0,1.0)
        return if(t<.42) local.toInt()/2%4 else 4+floor(((t-.42)/.58).coerceIn(0.0,1.0)*3).toInt()
    }
    fun needleFrame(p: CoreCombatMeshPart,age: Double): Int {
        val local=(age-p.delayTicks).coerceAtLeast(0.0)
        return if(local<4) local.toInt() else 4+floor(((local-4)/(p.durationTicks-5).coerceAtLeast(1)).coerceIn(0.0,1.0)*11).toInt()
    }
}
