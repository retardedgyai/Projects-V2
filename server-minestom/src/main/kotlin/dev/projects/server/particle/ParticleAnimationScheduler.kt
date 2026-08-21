package dev.projects.server.particle

import net.minestom.server.entity.Player

class ParticleAnimationScheduler {
    private data class Active(val handle: ParticleEffectHandle, val sink: ParticleSink)

    private val active = mutableListOf<Active>()

    val activeAnimationCount: Int get() = active.size

    fun start(
        effect: ParticleEffect,
        sink: ParticleSink,
        id: String = "particle-effect-${nextId++}",
        anchors: List<ParticleAnchor> = emptyList(),
        onComplete: (() -> Unit)? = null,
    ): ParticleEffectHandle {
        require(effect.durationTicks >= 1) { "durationTicks must be at least one" }
        val attached = if (anchors.isEmpty()) defaultAnchors(effect) else anchors
        return ParticleEffectHandle(id, effect, attached, onComplete).also { active += Active(it, sink) }
    }

    fun tick() {
        val iterator = active.iterator()
        while (iterator.hasNext()) {
            val animation = iterator.next()
            if (animation.handle.tick(animation.sink)) iterator.remove()
        }
    }

    fun cancelFor(player: Player) {
        active.removeIf {
            val matches = (it.sink as? PlayerParticleSink)?.belongsTo(player) == true
            if (matches) it.handle.cancel()
            matches
        }
    }

    fun pauseFor(player: Player) {
        active.filter { (it.sink as? PlayerParticleSink)?.belongsTo(player) == true }
            .forEach { it.handle.pause() }
    }

    fun resumeFor(player: Player) {
        active.filter { (it.sink as? PlayerParticleSink)?.belongsTo(player) == true }
            .forEach { it.handle.resume() }
    }

    fun cancelAll() {
        active.forEach { it.handle.cancel() }
        active.clear()
    }

    companion object {
        private var nextId = 0L
    }

    private fun defaultAnchors(effect: ParticleEffect): List<ParticleAnchor> = when (effect) {
        is ParticleEmitter -> listOf(effect.anchor)
        is ParticleTrail -> listOf(effect.anchor)
        is ParticleBeam -> listOf(effect.start)
        else -> emptyList()
    }
}
