package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Local ritual effect only: packed translucent billboards, vanilla Dust fallback; no client extension or resource mutations. */
internal object WorldInfusionSmoke {
    const val EMISSION_TICKS = 12
    const val TRAVEL_TICKS = 42
    const val MAX_SAMPLES = 64
    data class Transfer(val from: Vec, val to: Vec, val rgb: Int, val started: Long, val releaseUntil:Long=Long.MAX_VALUE,
        val travelTicks:Int=TRAVEL_TICKS,val tuftStep:Int=2) {
        init { require(travelTicks>0 && tuftStep>0) }
    }
    data class Sample(val key:String,val position: Vec, val rgb: Int, val scale: Float, val progress: Double)
    fun expired(t: Transfer, tick: Long) = tick >= t.started + 6*t.tuftStep + t.travelTicks

    /** Each confirmed unit releases six tufts; stopping consumption creates no further Transfers. */
    fun sample(t: Transfer, tick: Long, movingFocus:Vec?=null): List<Sample> = buildList {
        val delta=(movingFocus ?: t.to).sub(t.from)
        val horizontal=hypot(delta.x(),delta.z()).coerceAtLeast(.001)
        val side=Vec(-delta.z()/horizontal,0.0,delta.x()/horizontal)
        for(tuft in 0 until 6) {
            if(t.started+tuft*t.tuftStep>=t.releaseUntil)continue
            val age=tick-t.started-tuft*t.tuftStep
            if(age < 0 || age >= t.travelTicks)continue
            val u=age.toDouble()/t.travelTicks
            val seed=t.started*.37+tuft*1.79+t.from.x()*.41
            val envelope=sin(PI*u).coerceAtLeast(0.0)
            val bend=sin(PI*u)*.95
            val wave=sin(u*PI*3+seed)*.17*envelope
            val center=t.from.add(delta.mul(u)).add(side.mul(wave)).add(0.0,bend,0.0)
            val width=(.055+.14*envelope)*(.8+.2*sin(u*7+seed))
            for(lobe in 0 until 3) {
                val angle=seed+lobe*2.094+u*5
                val p=center.add(side.mul(cos(angle)*width)).add(0.0,sin(angle)*width*.8,0.0)
                val tone=.78+.2*sin(seed+lobe*.9+u*4)
                fun channel(shift:Int)=(((t.rgb shr shift) and 255)*tone).toInt().coerceIn(0,255)
                val rgb=(channel(16) shl 16) or (channel(8) shl 8) or channel(0)
                val end=(1.0-u).coerceAtMost(.22)/.22
                val size=((.7+.65*envelope+.18*sin(seed+u*9))*end).coerceAtLeast(.12).toFloat()
                add(Sample("${t.started}:${t.from.x()}:${t.from.z()}:${t.rgb}:$tuft:$lobe",p,rgb,size,u))
            }
        }
    }
    fun frame(transfers: List<Transfer>,tick:Long,movingFocus:Vec?=null):List<Sample> = transfers.asSequence()
        .filterNot { expired(it,tick) }.flatMap { sample(it,tick,movingFocus).asSequence() }.take(MAX_SAMPLES).toList()
}
