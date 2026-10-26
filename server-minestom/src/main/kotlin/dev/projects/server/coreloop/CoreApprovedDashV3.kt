package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/**
 * The approved impact plate (c34c624) that the warrior's normal hits land with (CoreApprovedNormalV3). 体当たり itself
 * no longer draws its blade and wake in the world (2026-10-27: the own swing is in the hand only); it is drawn by
 * CoreWarriorRefChoreography.
 */
internal object CoreApprovedDashV3 {
    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart>? {
        if(e.job!=CoreClass.WARRIOR || e.sceneId!="dash" || !e.valid || e.phase!=CoreSkillVisualPhase.CONTACT) return null
        val yaw=atan2(e.direction.x(),e.direction.z())
        // e.origin is the authoritative accepted-hit position, not an invented cast endpoint.
        return listOf(CoreCombatMeshPart("approved_dash_impact","gold",Vec(0.0,1.0,0.0),
            Vec(2.8,1.0,2.8),yaw=yaw,pitch=-PI/2,durationTicks=9))
    }

    fun pose(p: CoreCombatMeshPart,age: Double): CoreMeshPose? {
        if(p.shape!="approved_dash_impact") return null
        val local=(age-p.delayTicks).coerceIn(0.0,(p.durationTicks-1).toDouble())
        return CoreMeshPose(p.offset,p.scale,p.yaw,p.pitch,p.roll,
            "combat_vfx/approved_dash_v3/impact_${floor(local).toInt()}",age>=p.delayTicks && age<p.delayTicks+p.durationTicks)
    }
}
