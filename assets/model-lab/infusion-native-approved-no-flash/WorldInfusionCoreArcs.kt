package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** ProjectS proposal derived from the relayed TC6 temporal observation, not a TC mechanic. */
internal object WorldInfusionCoreArcs {
    const val LIFE_TICKS=6L
    const val IDLE_PERIOD=18L
    const val ACTIVE_PERIOD=11L
    private val angles=doubleArrayOf(-145.0,-65.0,25.0,115.0,-105.0,165.0)
    private val alphas=doubleArrayOf(.72,.98,.96,.65,.30,.06)
    data class Arc(val event:Long,val age:Int,val alpha:Double,val points:List<Vec>)
    fun sample(tick:Long,yaw:Double,pivot:Vec,active:Boolean):Arc? {
        if(tick<6)return null
        val period=if(active)ACTIVE_PERIOD else IDLE_PERIOD
        val event=(tick-6)/period;val age=((tick-6)%period).toInt()
        if(age>=LIFE_TICKS)return null
        return shape(event,age,yaw,pivot)
    }
    /** Keep an existing pulse alive over phase changes; frequency changes after it ends. */
    class Track {
        private var born:Long?=null
        private var nextBirth=6L
        private var event=-1L
        fun sample(tick:Long,yaw:Double,pivot:Vec,active:Boolean,allowNew:Boolean):Arc? {
            val start=born
            if(start!=null && tick-start<LIFE_TICKS)return shape(event,(tick-start).toInt(),yaw,pivot)
            born=null
            if(!allowNew) { nextBirth=tick+6;return null }
            if(tick<nextBirth)return null
            born=tick;event++
            nextBirth=tick+if(active)ACTIVE_PERIOD else IDLE_PERIOD
            return shape(event,0,yaw,pivot)
        }
    }
    private fun shape(event:Long,age:Int,yaw:Double,pivot:Vec):Arc {
        val angle=Math.toRadians(angles[(event%angles.size).toInt()])
        val radial=Vec(cos(angle),0.0,sin(angle));val tangent=Vec(-sin(angle),0.0,cos(angle))
        val y=-.27+.10*((event*3)%5)/4
        val bulge=.06+.16*sin(PI*(age+.5)/LIFE_TICKS)
        val side=.035*sin(age*PI/3)
        val nodes=listOf(Triple(.91,y,0.0),Triple(1.05+bulge,y+.08,-.14+side),
            Triple(1.00+bulge*.35,y+.22,.12+side),Triple(.93,y+.32,.20))
        val a=Math.toRadians(22.5);val b=Math.toRadians(yaw)
        val points=nodes.map { (radius,height,offset)->
            val p=radial.mul(radius).add(tangent.mul(offset)).add(0.0,height,0.0)
            val x=p.x()*cos(a)-p.y()*sin(a);val y2=p.x()*sin(a)+p.y()*cos(a)
            pivot.add(x*cos(b)+p.z()*sin(b),y2,-x*sin(b)+p.z()*cos(b))
        }
        return Arc(event,age,alphas[age],points)
    }
    /** Quaternion rotates the centered native segment's Z axis onto the endpoint vector. */
    fun alignZ(delta:Vec):FloatArray {
        val n=delta.normalize();val w=1+n.z()
        if(w<1e-8)return floatArrayOf(0f,1f,0f,0f)
        val x=-n.y();val y=n.x();val length=sqrt(x*x+y*y+w*w)
        return floatArrayOf((x/length).toFloat(),(y/length).toFloat(),0f,(w/length).toFloat())
    }
}
