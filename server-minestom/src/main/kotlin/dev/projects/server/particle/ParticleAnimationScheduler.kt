package dev.projects.server.particle

import net.minestom.server.entity.Player

class ParticleAnimationScheduler {
    private data class Active(val effect: ParticleEffect, val sink: ParticleSink, var tick: Int = 0)

    private val active = mutableListOf<Active>()

    val activeAnimationCount: Int get() = active.size

    fun start(effect: ParticleEffect, sink: ParticleSink) {
        require(effect.durationTicks >= 1) { "durationTicks must be at least one" }
        active += Active(effect, sink)
    }

    fun tick() {
        val iterator = active.iterator()
        while (iterator.hasNext()) {
            val animation = iterator.next()
            animation.effect.emit(animation.tick, animation.sink)
            animation.tick++
            if (animation.tick >= animation.effect.durationTicks) iterator.remove()
        }
    }

    fun cancelFor(player: Player) {
        active.removeIf { (it.sink as? PlayerParticleSink)?.belongsTo(player) == true }
    }

    fun cancelAll() = active.clear()
}
