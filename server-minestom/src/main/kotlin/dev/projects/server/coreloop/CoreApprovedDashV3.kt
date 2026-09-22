package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Frozen c34c624 choreography from the creator's exact approved GIF; dash only. */
internal object CoreApprovedDashV3 {
    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart>? {
        if(e.job!=CoreClass.WARRIOR || e.sceneId!="dash" || !e.valid) return null
        val yaw=atan2(e.direction.x(),e.direction.z())
        if(e.phase==CoreSkillVisualPhase.CONTACT) {
            // e.origin is the authoritative accepted-hit position, not an invented cast endpoint.
            return listOf(CoreCombatMeshPart("approved_dash_impact","gold",Vec(0.0,1.0,0.0),
                Vec(2.8,1.0,2.8),yaw=yaw,pitch=-PI/2,durationTicks=9))
        }
        val reach=min(CoreSkillScenes.get(e.sceneId).reach,e.radius.coerceAtLeast(.5))
        val forward=reach*.40
        val anchor=Vec(sin(yaw)*forward,1.0,cos(yaw)*forward)
        // The native contour occupies less than a unit square. Keep cast heading fixed;
        // only the blade tip moves. Separate wake samples outlive their own cutting edge.
        val blade=CoreCombatMeshPart("approved_dash_blade","steel",anchor,
            Vec(reach*1.8,reach*1.5,reach*1.65),yaw=yaw,pitch=-.50,roll=-.28,
            durationTicks=7,startSize=1.0,endSize=1.0)
        // Spend the slow first three frames in the ACTUAL startup, not after damage.
        if(e.phase==CoreSkillVisualPhase.PREPARE)
            return listOf(blade.copy(shape="approved_dash_prepare",durationTicks=e.prepareDuration))
        val wake=blade.copy(shape="approved_dash_wake",palette="steel",durationTicks=13,secondary=true)
        return listOf(blade,wake)
    }

    fun pose(p: CoreCombatMeshPart,age: Double): CoreMeshPose? {
        val layer=when(p.shape) {
            "approved_dash_blade","approved_dash_prepare" -> "blade"
            "approved_dash_wake" -> "wake"
            "approved_dash_impact" -> "impact"
            else -> return null
        }
        val local=(age-p.delayTicks).coerceIn(0.0,(p.durationTicks-1).toDouble())
        val frame=when(p.shape) {
            "approved_dash_prepare" -> floor(local/(p.durationTicks-1).coerceAtLeast(1)*2).toInt()
            "approved_dash_blade","approved_dash_wake" -> floor(local).toInt()+3
            else -> floor(local).toInt()
        }
        return CoreMeshPose(p.offset,p.scale,p.yaw,p.pitch,p.roll,
            "combat_vfx/approved_dash_v3/${layer}_$frame",age>=p.delayTicks && age<p.delayTicks+p.durationTicks)
    }
}
