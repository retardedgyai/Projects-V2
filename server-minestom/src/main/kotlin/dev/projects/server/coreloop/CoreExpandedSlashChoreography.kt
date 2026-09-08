package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Six authored skills. One PULSE starts one stroke, never an autonomous combo. */
internal object CoreExpandedSlashChoreography {
    val sceneIds=setOf("war_wound","war_counter","slam","whirl","ass_execute","ass_fan")
    private val profiles=setOf("wound","counter","fall","orbit","execute","execute_return","fan","fan_return")

    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart>? {
        if(!e.valid || e.sceneId !in sceneIds) return null
        val yaw=atan2(e.direction.x(),e.direction.z())
        val r=min(CoreSkillScenes.get(e.sceneId).reach,e.radius.coerceAtLeast(.5))
        val radial=e.sceneId in setOf("whirl","ass_fan")
        val assassin=e.job==CoreClass.ASSASSIN
        val profile=when(e.sceneId) {
            "war_wound" -> "wound"
            "war_counter" -> "counter"
            "slam" -> "fall"
            "whirl" -> "orbit"
            "ass_execute" -> "execute"
            else -> if(e.pulse%2==0) "fan" else "fan_return"
        }
        if(e.phase==CoreSkillVisualPhase.CONTACT) return listOf(CoreCombatMeshPart(
            "sweep:$profile:impact",if(assassin) "shadow" else "gold",Vec(0.0,1.0,0.0),
            Vec(if(assassin) 1.8 else 2.8,1.0,if(assassin) 1.8 else 2.8),
            yaw=yaw,pitch=-PI/2,durationTicks=if(assassin) 6 else 9))
        val down=e.sceneId=="slam"
        val anchor=if(radial) Vec(0.0,1.0,0.0) else Vec(sin(yaw)*r*.4,if(down) 1.4 else 1.0,cos(yaw)*r*.4)
        val scale=when {
            down -> Vec(3.2,1.2,r*1.6)
            radial -> Vec(r*2.1,.8,r*2.1)
            else -> Vec(r*1.8,r*1.4,r*1.55)
        }
        val roll=when(e.sceneId) { "slam" -> -PI/2; "war_counter" -> .40; "war_wound" -> -.55; "ass_execute" -> -.7; else -> -.10 }
        val blade=CoreCombatMeshPart("sweep:$profile:blade",if(assassin) "shadow" else "steel",anchor,scale,
            // The downstroke plane is slightly oblique, not edge-on to the owner's eye.
            yaw=yaw+(if(down) .4 else if(radial) e.pulse*PI*.35 else 0.0),pitch=if(down) 0.0 else if(radial) -.08 else -.50,
            roll=roll,durationTicks=if(assassin || e.sceneId=="war_wound") 5 else 7)
        val blades=if(e.sceneId=="ass_execute") listOf(blade,blade.copy(shape="sweep:execute_return:blade",roll=.7)) else listOf(blade)
        if(e.phase==CoreSkillVisualPhase.PREPARE) return blades.map {
            it.copy(shape=it.shape.replace(":blade",":prepare"),durationTicks=e.prepareDuration)
        }
        return blades+blades.map { it.copy(shape=it.shape.replace(":blade",":wake"),
            durationTicks=if(assassin || radial) 9 else 13,secondary=true) }
    }

    fun pose(p: CoreCombatMeshPart,age: Double): CoreMeshPose? {
        if(!p.shape.startsWith("sweep:")) return null
        val key=p.shape.split(':')
        require(key.size==3 && key[1] in profiles)
        val layer=key[2]
        val t=((age-p.delayTicks)/(p.durationTicks-1).coerceAtLeast(1)).coerceIn(0.0,1.0)
        val frame=when(layer) {
            "prepare" -> floor(t*2).toInt()
            "blade" -> 3+floor(t*6).toInt()
            "wake" -> 3+floor(t*12).toInt()
            "impact" -> floor(t*8).toInt()
            else -> error("Unassigned sweep layer $layer")
        }
        return CoreMeshPose(p.offset,p.scale,p.yaw,p.pitch,p.roll,
            "combat_vfx/sweeps/${key[1]}/${if(layer=="prepare") "blade" else layer}_$frame",
            age>=p.delayTicks && age<p.delayTicks+p.durationTicks)
    }
}
