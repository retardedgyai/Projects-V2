package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Absolute Zero: one continuous cast, not five restarts of an eight-tick flipbook. */
internal object CoreFrostChoreography {
    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart> {
        // NOVA damage is centred on the caster at each accepted pulse. The persistent
        // cosmetic field follows that same caster; cancellation still removes it normally.
        if(e.pulse!=0) return emptyList()
        val r=min(e.radius,CoreSkillScenes.get(e.sceneId).reach)
        val life=(e.skill.pulses-1)*8+16
        val phase=atan2(e.direction.x(),e.direction.z())
        return buildList {
            add(CoreCombatMeshPart("frost_domain_floor","ice",Vec(0.0,.12,0.0),Vec(r*2,1.0,r*2),
                yaw=phase,spin=PI*2.2,durationTicks=life,startSize=.3,endSize=1.0,ground=true,followOwner=true))
            // Four separated rising ribbons leave large sightlines through the ring.
            // Their actual textured planes, not collections of concrete cubes, carry the shape.
            repeat(4) { i ->
                val a=phase+i*PI/2
                add(CoreCombatMeshPart("frost_domain_band","ice",
                    Vec(sin(a)*r*.65,.65,cos(a)*r*.65),Vec(r*.75,1.0,.65),
                    yaw=a,pitch=-PI/2,roll=if(i%2==0) .12 else -.12,
                    spin=PI*2.2,travel=Vec(0.0,.65,0.0),bend=Vec(0.0,if(i%2==0) .16 else -.12,0.0),
                    durationTicks=life,startSize=.3,endSize=1.0,motion=CoreMeshMotion.ORBIT,followOwner=true))
            }
            repeat(4) { i ->
                val a=phase+.5+i*PI/2
                add(CoreCombatMeshPart("frost_domain_flake","ice",
                    Vec(sin(a)*r*.44,.45+(i%2)*.3,cos(a)*r*.44),Vec(.6,.6,.6),
                    yaw=a,pitch=-PI/2,roll=i*.7,rollTravel=.5,
                    spin=-PI*2.6,travel=Vec(0.0,1.25,0.0),durationTicks=life,
                    startSize=.25,endSize=1.0,motion=CoreMeshMotion.ORBIT,secondary=true,followOwner=true))
            }
        }
    }

    fun pose(p: CoreCombatMeshPart,age: Double): CoreMeshPose? {
        if(p.shape !in setOf("frost_domain_floor","frost_domain_band","frost_domain_flake")) return null
        val local=(age-p.delayTicks).coerceAtLeast(0.0)
        val t=(local/(p.durationTicks-1).coerceAtLeast(1)).coerceIn(0.0,1.0)
        // Expand once, sustain through all five damage beats, then shed pixel clusters.
        val open=1-(1-(local/8).coerceIn(0.0,1.0)).pow(3)
        val grow=p.startSize+(p.endSize-p.startSize)*open
        val angle=p.spin*(.45*t+.55*t*t)
        val offset=if(p.motion==CoreMeshMotion.ORBIT)
            Vec(cos(angle)*p.offset.x()+sin(angle)*p.offset.z(),p.offset.y(),
                -sin(angle)*p.offset.x()+cos(angle)*p.offset.z()) else p.offset
        // Pixel clusters dissolve from the tail; geometry never collapses to a thin line.
        val fade=floor(((local-(p.durationTicks-12))/11).coerceIn(0.0,1.0)*7).toInt()
        return CoreMeshPose(offset.add(p.travel.mul(t)).add(p.bend.mul(sin(PI*t))),p.scale.mul(grow),
            p.yaw+angle,p.pitch,p.roll+p.rollTravel*t,"combat_vfx/frost/${p.shape.removePrefix("frost_domain_")}_$fade",
            age>=p.delayTicks && local<p.durationTicks)
    }
}
