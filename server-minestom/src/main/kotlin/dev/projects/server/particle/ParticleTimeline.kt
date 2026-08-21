package dev.projects.server.particle

enum class ParticleEffectState { RUNNING, PAUSED, COMPLETED, CANCELLED }

/** Shared authored time context; all children of a composition can use the same playhead. */
data class ParticleFrame(val tick: Int, val durationTicks: Int) {
    val progress: Double get() = if (durationTicks <= 1) 1.0 else (tick.toDouble() / (durationTicks - 1)).coerceIn(0.0, 1.0)
}

class ParticleEffectHandle(
    val id: String,
    private val effect: ParticleEffect,
    private val anchors: List<ParticleAnchor> = emptyList(),
    private val onComplete: (() -> Unit)? = null,
) {
    var state: ParticleEffectState = ParticleEffectState.RUNNING
        private set
    var elapsedTicks: Int = 0
        private set
    val durationTicks: Int get() = effect.durationTicks

    fun pause() {
        if (state == ParticleEffectState.RUNNING) state = ParticleEffectState.PAUSED
    }

    fun resume() {
        if (state == ParticleEffectState.PAUSED) state = ParticleEffectState.RUNNING
    }

    fun cancel() {
        if (state == ParticleEffectState.RUNNING || state == ParticleEffectState.PAUSED) state = ParticleEffectState.CANCELLED
    }

    internal fun tick(sink: ParticleSink): Boolean {
        if (state != ParticleEffectState.RUNNING) return state == ParticleEffectState.COMPLETED || state == ParticleEffectState.CANCELLED
        if (anchors.any { it.cancelWhenInvalid && !it.sample().valid }) {
            cancel()
            return true
        }
        effect.emit(elapsedTicks, sink)
        elapsedTicks++
        if (elapsedTicks >= durationTicks) {
            state = ParticleEffectState.COMPLETED
            onComplete?.invoke()
        }
        return state == ParticleEffectState.COMPLETED || state == ParticleEffectState.CANCELLED
    }
}

class ParticleDelay(override val durationTicks: Int) : ParticleEffect {
    init { require(durationTicks >= 0) }
    override fun emit(tick: Int, sink: ParticleSink) = Unit
}

class ParticleSequence(private val effects: List<ParticleEffect>) : ParticleEffect {
    override val durationTicks: Int = effects.sumOf { it.durationTicks }.coerceAtLeast(1)

    override fun emit(tick: Int, sink: ParticleSink) {
        var offset = 0
        for (effect in effects) {
            if (tick in offset until offset + effect.durationTicks) effect.emit(tick - offset, sink)
            offset += effect.durationTicks
        }
    }

    companion object {
        fun of(vararg effects: ParticleEffect): ParticleSequence = ParticleSequence(effects.toList())
    }
}

class ParticleParallel(private val effects: List<ParticleEffect>) : ParticleEffect {
    override val durationTicks: Int = effects.maxOfOrNull { it.durationTicks } ?: 1
    override fun emit(tick: Int, sink: ParticleSink) {
        effects.forEach { if (tick < it.durationTicks) it.emit(tick, sink) }
    }

    companion object {
        fun of(vararg effects: ParticleEffect): ParticleParallel = ParticleParallel(effects.toList())
    }
}

class ParticleMarker(private val callback: () -> Unit) : ParticleEffect {
    override val durationTicks: Int = 1
    override fun emit(tick: Int, sink: ParticleSink) {
        if (tick == 0) callback()
    }
}

class ParticleSequenceBuilder {
    private val effects = mutableListOf<ParticleEffect>()
    fun play(effect: ParticleEffect, delayTicks: Int = 0) {
        require(delayTicks >= 0)
        if (delayTicks > 0) effects += ParticleDelay(delayTicks)
        effects += effect
    }
    fun waitTicks(ticks: Int) { if (ticks > 0) effects += ParticleDelay(ticks) }
    fun marker(callback: () -> Unit) { effects += ParticleMarker(callback) }
    fun parallel(block: ParticleParallelBuilder.() -> Unit) {
        val builder = ParticleParallelBuilder().apply(block)
        effects += builder.build()
    }
    fun build(): ParticleSequence = ParticleSequence.of(*effects.toTypedArray())
}

class ParticleParallelBuilder {
    private val effects = mutableListOf<ParticleEffect>()
    fun play(effect: ParticleEffect, delayTicks: Int = 0) {
        effects += if (delayTicks > 0) ParticleSequence.of(ParticleDelay(delayTicks), effect) else effect
    }
    fun waitTicks(ticks: Int) { effects += ParticleDelay(ticks.coerceAtLeast(0)) }
    fun build(): ParticleParallel = ParticleParallel.of(*effects.toTypedArray())
}

fun sequence(block: ParticleSequenceBuilder.() -> Unit): ParticleEffect = ParticleSequenceBuilder().apply(block).build()

data class ParticleVfx(val id: String, val effect: ParticleEffect, val anchors: List<ParticleAnchor>) {
    fun start(scheduler: ParticleAnimationScheduler, sink: ParticleSink, onComplete: (() -> Unit)? = null): ParticleEffectHandle =
        scheduler.start(effect, sink, id = id, anchors = anchors, onComplete = onComplete)
}

class ParticleVfxBuilder(private val id: String) {
    private val anchors = mutableListOf<ParticleAnchor>()
    private val content = ParticleSequenceBuilder()

    fun anchor(anchor: ParticleAnchor) { anchors += anchor }
    fun play(effect: ParticleEffect, delayTicks: Int = 0) { content.play(effect, delayTicks) }
    fun waitTicks(ticks: Int) { content.waitTicks(ticks) }
    fun parallel(block: ParticleParallelBuilder.() -> Unit) { content.parallel(block) }
    fun build(): ParticleVfx = ParticleVfx(id, content.build(), anchors.toList())
}

fun vfx(id: String, block: ParticleVfxBuilder.() -> Unit): ParticleVfx = ParticleVfxBuilder(id).apply(block).build()
