package dev.projects.server.particle

import kotlin.math.sin

/** Small deterministic context for authored scatter without sharing mutable RNG state. */
class ParticleRandom(val seed: Long) {
    fun value(index: Long): Double {
        val mixed = seed xor (index * -7046029254386353131L)
        return (sin(mixed.toDouble()) * 0.5 + 0.5).coerceIn(0.0, 1.0)
    }

    fun range(index: Long, min: Double, max: Double): Double = min + (max - min) * value(index)
}
