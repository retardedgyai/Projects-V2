package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Absolute Zero only. Each server-confirmed pulse owns one short, open frost vortex. */
internal object CoreFrostChoreography {
    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart> {
        val r=min(e.radius,CoreSkillScenes.get(e.sceneId).reach)
        val last=e.pulse==e.skill.pulses-1
        // The authoritative pulses are eight ticks apart. Do not stack five opaque
        // 32-tick floor decals, or leave a false damage field at an old cast centre.
        val life=if(last) 16 else 8
        val phase=atan2(e.direction.x(),e.direction.z())+e.pulse*.65
        return buildList {
            add(CoreCombatMeshPart("frost_domain_floor","ice",Vec(0.0,.12,0.0),Vec(r*2,1.0,r*2),
                yaw=phase,spin=.65,durationTicks=life,startSize=.85,endSize=1.0,ground=true))
            // Four separated rising ribbons leave large sightlines through the ring.
            // Their actual textured planes, not collections of concrete cubes, carry the shape.
            repeat(4) { i ->
                val a=phase+i*PI/2
                add(CoreCombatMeshPart("frost_domain_band","ice",
                    Vec(sin(a)*r*.65,.65,cos(a)*r*.65),Vec(r*.75,1.0,.65),
                    yaw=a,pitch=-PI/2,roll=if(i%2==0) .12 else -.12,
                    spin=.65,travel=Vec(0.0,.18,0.0),bend=Vec(0.0,if(i%2==0) .16 else -.12,0.0),
                    durationTicks=life,startSize=.8,endSize=1.0,motion=CoreMeshMotion.ORBIT))
            }
            repeat(4) { i ->
                val a=phase+.5+i*PI/2
                add(CoreCombatMeshPart("frost_domain_flake","ice",
                    Vec(sin(a)*r*.44,.45+(i%2)*.3,cos(a)*r*.44),Vec(.6,.6,.6),
                    yaw=a,pitch=-PI/2,roll=i*.7,rollTravel=.5,
                    spin=-.35,travel=Vec(0.0,.5,0.0),durationTicks=life,
                    startSize=.5,endSize=1.0,motion=CoreMeshMotion.ORBIT,secondary=true))
            }
        }
    }

    fun pose(p: CoreCombatMeshPart,age: Double): CoreMeshPose? {
        if(p.shape !in setOf("frost_domain_floor","frost_domain_band","frost_domain_flake")) return null
        val local=(age-p.delayTicks).coerceAtLeast(0.0)
        val t=(local/(p.durationTicks-1).coerceAtLeast(1)).coerceIn(0.0,1.0)
        val open=1-(1-(local/2).coerceIn(0.0,1.0)).pow(3)
        val grow=p.startSize+(p.endSize-p.startSize)*open
        val angle=p.spin*t
        val offset=if(p.motion==CoreMeshMotion.ORBIT)
            Vec(cos(angle)*p.offset.x()+sin(angle)*p.offset.z(),p.offset.y(),
                -sin(angle)*p.offset.x()+cos(angle)*p.offset.z()) else p.offset
        // Pixel clusters dissolve from the tail; geometry never collapses to a thin line.
        val fade=floor(((t-.45)/.55).coerceIn(0.0,1.0)*7).toInt()
        return CoreMeshPose(offset.add(p.travel.mul(t)).add(p.bend.mul(sin(PI*t))),p.scale.mul(grow),
            p.yaw+angle,p.pitch,p.roll+p.rollTravel*t,"combat_vfx/frost/${p.shape.removePrefix("frost_domain_")}_$fade",
            age>=p.delayTicks && local<p.durationTicks)
    }
}
