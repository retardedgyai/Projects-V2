package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** The offensive prayers and light-step; existing restorative petals/wings remain separate. */
internal object CoreHealerChoreography {
    val sceneIds=setOf("heal_light","heal_mark","heal_pillar","heal_lamp","heal_judgment","heal_step")
    fun parts(e: CoreSkillEffect,raw: List<CoreCombatMeshPart>,life: Int): List<CoreCombatMeshPart> {
        val yaw=atan2(e.direction.x(),e.direction.z())
        fun local(x: Double,y: Double,z: Double)=Vec(cos(yaw)*x+sin(yaw)*z,y,-sin(yaw)*x+cos(yaw)*z)
        val prepare=e.phase==CoreSkillVisualPhase.PREPARE
        if(e.phase==CoreSkillVisualPhase.CONTACT) return (0 until 4).map { i ->
            val a=i*PI/2+PI/4
            val radial=local(cos(a)*.65,sin(a)*.65,0.0)
            val mark=e.sceneId=="heal_mark"
            CoreCombatMeshPart("guidance_petal","holy",Vec(0.0,1.0,0.0).add(if(mark) radial else radial.mul(.15)),
                Vec(.45,.6,.65),yaw=yaw,pitch=-PI/2,roll=a-PI/2,
                travel=if(mark) radial.mul(-.3) else radial.mul(.85).add(0.0,.35,0.0),
                startSize=if(mark) 1.0 else .6,endSize=if(mark) .8 else .25,
                durationTicks=if(mark) 24 else 18,motion=if(mark) CoreMeshMotion.GATHER else CoreMeshMotion.FLOAT,
                erode=true)
        }
        if(e.sceneId in setOf("heal_light","heal_mark")) {
            if(prepare) return listOf(-1,1).map { side ->
                CoreCombatMeshPart("guidance_petal","holy",local(side*.5,1.2,.6),Vec(.55,.6,.75),
                    yaw=yaw,pitch=-PI/2,roll=side*.6,rollTravel=-side*.4,
                    travel=local(-side*.25,0.0,0.0),startSize=.4,endSize=1.0,
                    durationTicks=life,motion=CoreMeshMotion.GATHER,followOwner=true)
            }
            val trail=raw.last()
            return listOf(trail.copy(shape="prayer_ray",scale=Vec(.65,.65,trail.scale.z()),
                durationTicks=life,startSize=1.0,endSize=1.0,secondary=false,erode=false))
        }
        if(e.sceneId=="heal_step") {
            val depart=e.endpoint==CoreSkillEndpoint.DEPARTURE
            return listOf(-1,1).flatMap { side -> (0 until 3).map { index ->
                CoreCombatMeshPart("feather_plume","life",local(side*(.4+index*.12),.65+index*.15,.3),
                    Vec(.65,.7,1.15-index*.1),yaw=yaw,pitch=-PI/2,roll=-side*(.25+index*.22),
                    rollTravel=side*(if(prepare || depart) .35 else -.65),
                    travel=local(side*(if(depart) -.25 else .3),if(depart) -.35 else .8,if(depart) -.3 else .2),
                    startSize=if(prepare) .3 else 1.0,endSize=if(depart) .1 else .65,
                    durationTicks=life-min(index,life-1),delayTicks=min(index,life-1),motion=if(depart) CoreMeshMotion.GATHER else CoreMeshMotion.FLOAT,
                    erode=!prepare,followOwner=prepare,secondary=index==2)
            } }
        }
        if(e.sceneId=="heal_lamp") {
            if(prepare && e.pulse>0) return emptyList()
            return buildList {
                if(prepare || e.pulse==0) {
                    val lampLife=if(prepare) life else (e.skill.pulses-1)*8+20
                    add(CoreCombatMeshPart("prayer_lantern","holy",Vec(0.0,if(prepare) 1.35 else 1.55,0.0),Vec(.9,.9,.9),
                        yaw=yaw,spin=.5,travel=Vec(0.0,if(prepare) .2 else .15,0.0),
                        startSize=if(prepare) .4 else 1.0,endSize=1.0,durationTicks=lampLife,
                        motion=CoreMeshMotion.FLOAT,erode=!prepare))
                    if(!prepare) add(column(Vec(0.0,1.8,0.0),yaw,.45,.9,lampLife).copy(shape="prayer_flame",
                        travel=Vec(0.0,.15,0.0),motion=CoreMeshMotion.FLOAT))
                }
                if(!prepare) add(column(Vec(0.0,1.7,0.0),yaw,.9,1.5,16).copy(pitch=PI/2))
            }
        }
        if(e.sceneId=="heal_judgment") {
            val angle=yaw+e.pulse*2.39996
            val radius=if(e.pulse==0) 0.0 else min(e.radius*.3,1.8)
            val root=Vec(sin(angle)*radius,.12,cos(angle)*radius)
            val sword=CoreCombatMeshPart("prayer_sword","holy",root.add(0.0,1.6+if(prepare && life>1) 4.0 else 0.0,0.0),
                Vec(1.15,1.0,3.2),yaw=angle,pitch=PI/2,
                travel=if(prepare && life>1) Vec(0.0,-4.0,0.0) else Vec.ZERO,
                startSize=1.0,endSize=1.0,durationTicks=if(prepare) life else 6,
                motion=if(prepare) CoreMeshMotion.FALL else CoreMeshMotion.LINEAR,erode=!prepare)
            return if(prepare) listOf(sword) else listOf(sword)+columns(root,angle,life,large=true)
        }
        // Pillar rises; the judgment sword descends. No generic meteor/arrow asset.
        if(prepare) return listOf(-1,1).map { side ->
            CoreCombatMeshPart("guidance_petal","holy",local(side*.55,.2,0.0),Vec(.5,.6,.8),
                yaw=yaw,pitch=-.3,pitchTravel=-.8,travel=Vec(0.0,.35,0.0),
                startSize=.3,endSize=.7,durationTicks=life,motion=CoreMeshMotion.GATHER,ground=true)
        }
        return columns(Vec(0.0,.12,0.0),yaw+e.pulse*.8,life,large=false)
    }

    private fun column(root: Vec,yaw: Double,width: Double,height: Double,life: Int)=
        CoreCombatMeshPart("purifying_column","holy",root,Vec(width,width,height),yaw=yaw,pitch=-PI/2,
            durationTicks=life,startSize=1.0,endSize=1.0)
    private fun columns(root: Vec,yaw: Double,life: Int,large: Boolean): List<CoreCombatMeshPart> =
        (0 until 3).map { i ->
            val a=yaw+i*PI*2/3
            val distance=if(i==0) 0.0 else if(large) 1.0 else .8
            column(root.add(sin(a)*distance,0.0,cos(a)*distance),a,
                if(i==0) 1.15 else .75,if(i==0) 3.0 else 2.0,life-i*2)
                .copy(delayTicks=i*2,ground=true)
        }

    fun frame(p: CoreCombatMeshPart,age: Double): Int {
        val local=(age-p.delayTicks).coerceAtLeast(0.0)
        if(p.shape=="prayer_flame") {
            val release=p.durationTicks-12
            return if(local<release) 2+local.toInt()%8/2 else
                8+floor(((local-release)/11).coerceIn(0.0,1.0)*7).toInt()
        }
        return if(local<4) local.toInt() else
            4+floor(((local-4)/(p.durationTicks-5).coerceAtLeast(1)).coerceIn(0.0,1.0)*11).toInt()
    }
}
