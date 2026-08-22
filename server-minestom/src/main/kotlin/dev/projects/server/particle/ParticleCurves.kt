package dev.projects.server.particle

import net.minestom.server.coordinate.Vec
import net.minestom.server.particle.Particle
import kotlin.math.cos
import kotlin.math.pow

enum class Easing {
    LINEAR,
    SMOOTHSTEP,
    EASE_IN_QUAD,
    EASE_OUT_QUAD,
    EASE_IN_OUT_QUAD,
    CUBIC,
    SINE,
    EXPO,
}

fun Easing.apply(progress: Double): Double {
    val t = progress.coerceIn(0.0, 1.0)
    return when (this) {
        Easing.LINEAR -> t
        Easing.SMOOTHSTEP -> t * t * (3.0 - 2.0 * t)
        Easing.EASE_IN_QUAD -> t * t
        Easing.EASE_OUT_QUAD -> 1.0 - (1.0 - t) * (1.0 - t)
        Easing.EASE_IN_OUT_QUAD -> if (t < 0.5) 2.0 * t * t else 1.0 - (-2.0 * t + 2.0).pow(2.0) / 2.0
        Easing.CUBIC -> t * t * t
        Easing.SINE -> 1.0 - cos(t * Math.PI / 2.0)
        Easing.EXPO -> when {
            t == 0.0 || t == 1.0 -> t
            else -> 2.0.pow(10.0 * (t - 1.0))
        }
    }
}

data class CurveKeyframe<T>(val progress: Double, val value: T)

interface Curve<T> {
    fun sample(progress: Double): T
}

class KeyframeCurve<T>(
    keyframes: List<CurveKeyframe<T>>,
    private val interpolate: (T, T, Double) -> T,
    private val easing: Easing = Easing.LINEAR,
) : Curve<T> {
    private val keyframes = keyframes.sortedBy { it.progress }.also { require(it.isNotEmpty()) }

    override fun sample(progress: Double): T {
        val t = progress.coerceIn(0.0, 1.0)
        if (t <= keyframes.first().progress) return keyframes.first().value
        if (t >= keyframes.last().progress) return keyframes.last().value
        val right = keyframes.indexOfFirst { it.progress >= t }.coerceAtLeast(1)
        val leftFrame = keyframes[right - 1]
        val rightFrame = keyframes[right]
        val span = (rightFrame.progress - leftFrame.progress).coerceAtLeast(1.0e-9)
        return interpolate(leftFrame.value, rightFrame.value, easing.apply((t - leftFrame.progress) / span))
    }

    companion object {
        fun double(vararg keyframes: CurveKeyframe<Double>, easing: Easing = Easing.LINEAR): Curve<Double> =
            KeyframeCurve(keyframes.toList(), { a, b, t -> a + (b - a) * t }, easing)

        fun float(vararg keyframes: CurveKeyframe<Float>, easing: Easing = Easing.LINEAR): Curve<Float> =
            KeyframeCurve(keyframes.toList(), { a, b, t -> a + (b - a) * t.toFloat() }, easing)

        fun vec(vararg keyframes: CurveKeyframe<Vec>, easing: Easing = Easing.LINEAR): Curve<Vec> =
            KeyframeCurve(keyframes.toList(), { a, b, t -> Vec(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t) }, easing)

        fun color(vararg keyframes: CurveKeyframe<Int>, easing: Easing = Easing.LINEAR): Curve<Int> =
            KeyframeCurve(keyframes.toList(), ::interpolateColor, easing)
    }
}

fun <T> constantCurve(value: T): Curve<T> = object : Curve<T> {
    override fun sample(progress: Double): T = value
}

/** The small set of style properties needed by animated particle primitives. */
data class ParticleStyleCurve(
    val base: ParticleStyle = ParticleStyle(Particle.END_ROD),
    val color: Curve<Int>? = null,
    val size: Curve<Float>? = null,
    val speed: Curve<Float>? = null,
    val density: Curve<Double> = constantCurve(1.0),
) {
    fun sample(progress: Double): ParticleStyle {
        val colorValue = color?.sample(progress)
        val sizeValue = size?.sample(progress)
        val particle = when {
            colorValue != null -> dust(colorValue, sizeValue ?: 1.0f)
            sizeValue != null && base.particle is Particle.Dust -> {
                val current = base.particle
                dust((current.color().red() shl 16) or (current.color().green() shl 8) or current.color().blue(), sizeValue)
            }
            else -> base.particle
        }
        return base.copy(
            particle = particle,
            speed = speed?.sample(progress) ?: base.speed,
            densityMultiplier = base.densityMultiplier * density.sample(progress).coerceAtLeast(0.0),
        )
    }
}

private fun interpolateColor(a: Int, b: Int, t: Double): Int = lerpColor(a, b, t.coerceIn(0.0, 1.0))
