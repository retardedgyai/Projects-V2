package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Bold opt-in native sprites, not a shader/Bloom. Fewer larger shapes replace the prior tiny motes. */
internal object WorldInfusionRadiance {
    fun glow(s:WorldInfusionState,tick:Long,pulsing:Boolean):Double {
        val f=WorldInfusionCharge.fill(s).fraction
        if(f==0.0)return .22
        val base=if(f<.5).76 else if(f<1).88 else .94
        val amplitude=if(pulsing).08*f else 0.0
        return (base+amplitude*sin(tick*PI/14)).coerceIn(.22,1.0)
    }
    class Track {
        private var completedAt:Long?=null
        fun accepted(s:WorldInfusionState,tick:Long,completed:Boolean) {
            if(completed)completedAt=tick
            else if(s.phase!=InfusionPhase.COMPLETE)completedAt=null
        }
        fun samples(s:WorldInfusionState,tick:Long,pose:WorldInfusionAnimation.Pose,pivot:Vec,weapon:Vec,
            channel:Double?,flowAllowed:Boolean):List<WorldInfusionSmoke.Sample> = buildList {
            val fill=WorldInfusionCharge.fill(s).fraction
            val working=s.phase in setOf(InfusionPhase.ESSENTIA,InfusionPhase.INGREDIENTS)
            val flowing=working && flowAllowed && !s.paused
            if(working && fill>0) {
                val pulse=if(flowing)sin(tick*PI/14) else 0.0
                val full=fill>=.75
                val size=if(full)4.5 else 3.2
                val tone=if(full).98+.10*pulse else .83+.12*pulse
                fun colour(rgb:Int,f:Double)=(((((rgb shr 16) and 255)*f).toInt().coerceIn(0,255)) shl 16) or
                    (((((rgb shr 8) and 255)*f).toInt().coerceIn(0,255)) shl 8) or ((rgb and 255)*f).toInt().coerceIn(0,255)
                add(WorldInfusionSmoke.Sample("radiance:halo",pivot,colour(0xc979ff,tone),1f,fill,
                    "infusion-radiance/halo",(size+.2*pulse).toFloat(),(size+.2*pulse).toFloat(),brightness=15))
                if(full)add(WorldInfusionSmoke.Sample("radiance:outer",pivot,colour(0xa74de3,.63+.07*pulse),1f,fill,
                    "infusion-radiance/halo",5.25f,5.25f,brightness=15))
                if(flowing)for(i in 0 until if(full)6 else 3) {
                    val a=i*PI/3+Math.toRadians(pose.yaw);val r=if(full)1.0 else .82
                    add(WorldInfusionSmoke.Sample("radiance:orbit:$i",pivot.add(cos(a)*r,.08*sin(a*2+tick*.13),sin(a)*r),
                        0xb872f1,1f,fill,width=if(full).48f else .32f,height=if(full).48f else .32f,brightness=15))
                }
                if(flowing && channel!=null && channel>0 && channel<1) {
                    val from=pivot.add(0.0,-.49,0.0);val to=weapon.add(0.0,.14,0.0)
                    val span=from.y()-to.y();val center=from.add(to).mul(.5)
                    add(WorldInfusionSmoke.Sample("radiance:column",center,0xc38af2,1f,channel,
                        "infusion-radiance/column",1.1f,span.toFloat(),WorldInfusionSmoke.Facing.VERTICAL,15))
                    add(WorldInfusionSmoke.Sample("radiance:receiver",weapon.add(0.0,-.37,0.0),0xb878f0,1f,channel,
                        "infusion-radiance/wave",2.2f,1f,WorldInfusionSmoke.Facing.HORIZONTAL,15))
                    for(i in 0..5) {
                        val travel=channel*2+i/6.0;val u=travel%1
                        val a=i*PI/3+channel*PI*6
                        val p=from.add(to.sub(from).mul(u)).add(cos(a)*.08,0.0,sin(a)*.08)
                        // Retire/rebirth at the inlet on wrap; never interpolate a finished knot back upwards.
                        add(WorldInfusionSmoke.Sample("radiance:flow:$i:${floor(travel).toInt()}",p,0xe0b4ff,1f,u,width=.38f,height=.38f,brightness=15))
                    }
                }
            }
            val age=completedAt?.let { tick-it }
            if(age!=null && s.phase==InfusionPhase.COMPLETE && s.gearPlace==InfusionGearPlace.OUTPUT) {
                for(i in 0..1) {
                    val t=age-i*6
                    if(t !in 0..24)continue
                    val u=t/24.0;val size=1.9+4.2*u;val tone=(.98-.3*u)
                    val rgb=((210*tone).toInt() shl 16) or ((131*tone).toInt() shl 8) or (255*tone).toInt()
                    add(WorldInfusionSmoke.Sample("radiance:wave:$i",weapon.add(0.0,-.37,0.0),rgb,1f,u,
                        "infusion-radiance/wave",size.toFloat(),1f,WorldInfusionSmoke.Facing.HORIZONTAL,15))
                }
                if(age in 0..8)add(WorldInfusionSmoke.Sample("radiance:flash",weapon,0xe2b5ff,1f,age/8.0,
                    "infusion-radiance/burst",1.5f,1.5f,brightness=15))
            }
        }
    }
}
