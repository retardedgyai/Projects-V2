package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Cosmetic timing shared by the world presenter and exported preview; never resource authority. */
internal object WorldInfusionAnimation {
    const val MAX_SPEED = 4.5 // degrees/tick; ProjectS preview tuning, not a Thaumcraft rule.
    const val ACCELERATION = .18
    const val DECELERATION = .09
    const val IDLE_GLOW = .22
    data class Pose(val yaw:Double,val speed:Double,val glow:Double) {
        val neutralTint get() = (glow*255).roundToInt().coerceIn(0,255).let { (it shl 16) or (it shl 8) or it }
        fun rotation():FloatArray {
            val a=Math.toRadians(yaw%360)/2
            return floatArrayOf(0f,sin(a).toFloat(),0f,cos(a).toFloat())
        }
        fun inlet(pivot:Vec):Vec {
            val a=Math.toRadians(yaw);val x=-.1875*sin(Math.PI/8);val z=-.125
            return pivot.add(x*cos(a)+z*sin(a),.1875*cos(Math.PI/8),-x*sin(a)+z*cos(a))
        }
    }
    class Track {
        var pose=Pose(0.0,0.0,IDLE_GLOW);private set
        private var completedAt:Long?=null
        fun step(s:WorldInfusionState,tick:Long,completed:Boolean=false):Pose {
            if(completed)completedAt=tick
            val active=!s.paused && s.phase in setOf(InfusionPhase.ESSENTIA,InfusionPhase.INGREDIENTS)
            if(active)completedAt=null
            val speed=if(active)(pose.speed+ACCELERATION).coerceAtMost(MAX_SPEED) else (pose.speed-DECELERATION).coerceAtLeast(0.0)
            val age=completedAt?.let { tick-it }
            val charge=s.supplied.values.sum().toDouble()/WorldInfusionRules.cost.values.sum()
            val glow=when {
                age!=null && age in 0..14 -> .75+.25*sin(Math.PI*age/14)
                age!=null && age in 15..70 -> IDLE_GLOW+(.75-IDLE_GLOW)*(70-age)/56
                active -> (.30+.40*charge+.035*sin(tick*Math.PI/24)).coerceIn(IDLE_GLOW,.80)
                else -> (pose.glow-.025).coerceAtLeast(IDLE_GLOW)
            }
            pose=Pose(pose.yaw+speed,speed,glow)
            return pose
        }
    }
    fun mayAdvance(s:WorldInfusionState,transfers:List<WorldInfusionSmoke.Transfer>,tick:Long)=
        s.phase!=InfusionPhase.INGREDIENTS || transfers.all { WorldInfusionSmoke.expired(it,tick) }
}
