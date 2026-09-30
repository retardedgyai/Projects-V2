package dev.projects.server.coreloop

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Cosmetic cap across private playground worlds. It never gates a craft or spends resources. */
internal object WorldInfusionSmokeBudget {
    const val MAX_TOTAL=192
    private val active=AtomicInteger()
    val inUse get()=active.get()
    class Lease internal constructor() {
        private val released=AtomicBoolean(false)
        fun release() { if(released.compareAndSet(false,true))active.decrementAndGet() }
    }
    fun acquire():Lease? {
        while(true) {
            val old=active.get();if(old>=MAX_TOTAL)return null
            if(active.compareAndSet(old,old+1))return Lease()
        }
    }
}
