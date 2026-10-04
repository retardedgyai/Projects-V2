package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import net.minestom.server.sound.SoundEvent

/** Selected standard-sound B. Temporary test replay only; no ledger/repository or custom assets. */
internal object WorldInfusionReplaySoundB {
    enum class Cue(val event:SoundEvent,val volume:Float,val pitch:Float) {
        START(SoundEvent.BLOCK_AMETHYST_BLOCK_CHIME,.6f,.8f),
        ARRIVAL(SoundEvent.BLOCK_ENCHANTMENT_TABLE_USE,.05f,1f),
        COMPLETE(SoundEvent.BLOCK_AMETHYST_BLOCK_RESONATE,.25f,1f)
    }
    class Track {
        private var running=false
        private val played=linkedSetOf<Cue>()
        fun begin():Cue {
            check(!running);running=true;played.clear();played+=Cue.START;return Cue.START
        }
        fun arrival(state:WorldInfusionState,samples:List<WorldInfusionSmoke.Sample>,receiver:Vec):Cue? {
            if(!running || Cue.ARRIVAL in played || state.paused || state.phase!=InfusionPhase.INGREDIENTS)return null
            if(samples.none { it.key.startsWith("radiance:flow:") && it.position.distance(receiver)<=.30 })return null
            played+=Cue.ARRIVAL;return Cue.ARRIVAL
        }
        fun complete():Cue? {
            if(!running || Cue.COMPLETE in played)return null
            played+=Cue.COMPLETE;return Cue.COMPLETE
        }
        fun stop():Set<Cue> {
            val result=played.toSet();played.clear();running=false;return result
        }
    }
}
