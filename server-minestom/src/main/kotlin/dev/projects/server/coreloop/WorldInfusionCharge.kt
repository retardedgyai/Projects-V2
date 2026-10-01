package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Optional presentation only. Accepted, persisted Essentia receipts own the fill, never elapsed time. */
internal object WorldInfusionCharge {
    data class Fill(val fraction:Double) {
        init { require(fraction in 0.0..1.0) }
        val baseline get()=.22+.68*fraction
        val pulseAmplitude get()=.05*fraction*fraction
        val motes get()=(16*fraction).toInt()
        fun glow(tick:Long,pulsing:Boolean)=(baseline+if(pulsing)pulseAmplitude*sin(tick*PI/(30-12*fraction)) else 0.0).coerceIn(.22,.95)
    }
    fun fill(s:WorldInfusionState)=Fill(if(s.phase==InfusionPhase.READY)0.0 else
        WorldInfusionRules.cost.entries.sumOf { (a,n)->s.supplied.getOrDefault(a,0).coerceIn(0,n) }.toDouble()/WorldInfusionRules.cost.values.sum())
    /** Reuse the existing sprite/lease pool: 16 charge motes + 24 stream knots, under the 64 local cap. */
    fun samples(s:WorldInfusionState,tick:Long,pose:WorldInfusionAnimation.Pose,pivot:Vec,weapon:Vec,
        channel:Double?,flowAllowed:Boolean):List<WorldInfusionSmoke.Sample> = buildList {
        if(!flowAllowed || s.paused || s.phase !in setOf(InfusionPhase.ESSENTIA,InfusionPhase.INGREDIENTS))return@buildList
        val fill=fill(s);val a=Math.toRadians(pose.yaw)
        for(i in 0 until fill.motes) {
            val corner=i%4;val band=i/4;val theta=corner*PI/2+PI/4+a+.045*sin(tick*.08+band)
            val radius=.64+.035*sin(tick*.09+i);val h=(band-1.5)*.22+.04*sin(tick*.11+i)
            val p=pivot.add(cos(theta)*radius,h,sin(theta)*radius)
            val brightness=.69+.26*fill.fraction+.05*sin(tick*.08+i)
            fun c(v:Int)=(v*brightness).toInt().coerceIn(0,255)
            add(WorldInfusionSmoke.Sample("core-charge:$i",p,(c(185) shl 16) or (c(86) shl 8) or c(238),(.42+.46*fill.fraction).toFloat(),fill.fraction))
        }
        if(channel!=null && channel>0 && channel<1) {
            // Energy receipt drives the descending front. P=0 cannot advance it or emit new knots.
            val from=pivot.add(0.0,-.49,0.0);val to=weapon.add(0.0,.07,0.0)
            val front=(channel*1.2).coerceAtMost(1.0)
            for(i in 0 until 12) {
                val u=front*(i+1)/12
                val taper=sin(PI*u).coerceAtLeast(.2);val phase=channel*PI*9+i*.42
                for(lobe in 0..1) {
                    val angle=phase+lobe*PI;val radius=(.055+.04*taper)*(1-.3*u)
                    val p=from.add(to.sub(from).mul(u)).add(cos(angle)*radius,0.0,sin(angle)*radius)
                    add(WorldInfusionSmoke.Sample("core-injection:$i:$lobe",p,if(lobe==0)0xb574e3 else 0x8351b7,
                        (.7+.4*taper).toFloat(),u))
                }
            }
        }
    }
    /** Reserve room for the central visual before Jar smoke; all share the unchanged per-ritual limit. */
    fun frame(charge:List<WorldInfusionSmoke.Sample>,finish:List<WorldInfusionSmoke.Sample>,jar:List<WorldInfusionSmoke.Sample>)=
        (charge+finish+jar).take(WorldInfusionSmoke.MAX_SAMPLES)
}
