package dev.projects.client

import kotlin.math.sin

data class TwinRodPose(
    val translateX: Float,
    val translateY: Float,
    val translateZ: Float,
    val rotateX: Float,
    val rotateY: Float,
    val rotateZ: Float,
)

object TwinRodFirstPersonAnimationState {
    private const val ATTACK_TICKS = 7f
    private const val RECOIL_TICKS = 2f

    private var previousProgress = 0f
    private var progress = 1f
    private var beat = 0
    private var previousRecoil = 0f
    private var recoil = 0f

    fun start(nextBeat: Int) {
        previousProgress = 0f
        progress = 0f
        beat = nextBeat.mod(4)
        previousRecoil = 0f
        recoil = 0f
    }

    fun confirmHit() {
        previousRecoil = 1f
        recoil = 1f
    }

    fun tick() {
        previousProgress = progress
        progress = (progress + 1f / ATTACK_TICKS).coerceAtMost(1f)
        previousRecoil = recoil
        recoil = (recoil - 1f / RECOIL_TICKS).coerceAtLeast(0f)
    }

    fun reset() {
        previousProgress = 0f
        progress = 1f
        previousRecoil = 0f
        recoil = 0f
    }

    fun pose(renderDelta: Float): TwinRodPose {
        val interpolatedProgress = previousProgress + (progress - previousProgress) * renderDelta.coerceIn(0f, 1f)
        val interpolatedRecoil = previousRecoil + (recoil - previousRecoil) * renderDelta.coerceIn(0f, 1f)
        return poseAt(interpolatedProgress, beat, interpolatedRecoil)
    }

    fun poseAt(progress: Float, beat: Int, hitRecoil: Float = 0f): TwinRodPose {
        val direction = BEAT_DIRECTIONS[beat.mod(4)]
        val clampedProgress = progress.coerceIn(0f, 1f)
        val motion = attackCurve(clampedProgress)
        val arc = strikeArc(clampedProgress)
        val recoil = hitRecoil.coerceIn(0f, 1f)
        return TwinRodPose(
            translateX = direction.x * motion + direction.arcX * arc,
            translateY = direction.y * motion + direction.arcY * arc,
            translateZ = direction.z * motion + recoil * 0.07f,
            rotateX = direction.rotateX * motion + recoil * 3.5f,
            rotateY = direction.rotateY * motion,
            rotateZ = direction.rotateZ * motion + recoil * 2.5f,
        )
    }

    private fun attackCurve(progress: Float): Float = when {
        progress <= 0.2f -> -0.32f + 0.07f * smoothStep(progress / 0.2f)
        progress <= 0.5f -> -0.25f + 1.25f * smoothStep((progress - 0.2f) / 0.3f)
        progress <= 0.75f -> 1f - 0.45f * smoothStep((progress - 0.5f) / 0.25f)
        else -> 0.55f * (1f - smoothStep((progress - 0.75f) / 0.25f))
    }

    private fun strikeArc(progress: Float): Float {
        if (progress <= 0.2f || progress >= 0.75f) return 0f
        val strikeProgress = ((progress - 0.2f) / 0.55f).coerceIn(0f, 1f)
        return sin(strikeProgress * Math.PI).toFloat()
    }

    private fun smoothStep(value: Float): Float {
        val clamped = value.coerceIn(0f, 1f)
        return clamped * clamped * (3f - 2f * clamped)
    }

    private data class BeatDirection(
        val x: Float,
        val y: Float,
        val z: Float,
        val arcX: Float,
        val arcY: Float,
        val rotateX: Float,
        val rotateY: Float,
        val rotateZ: Float,
    )

    private val BEAT_DIRECTIONS = listOf(
        // Jab: pull from the lower-right, then drive toward the crosshair.
        BeatDirection(-0.25f, 0.16f, -0.18f, 0f, 0f, -7f, 6f, -10f),
        // Cross: return from the opposite side with stronger lateral travel.
        BeatDirection(0.34f, 0.06f, -0.16f, 0f, 0.02f, -5f, -8f, 8f),
        // Hook: sweep horizontally through the center on a shallow arc.
        BeatDirection(0.38f, 0.03f, -0.12f, 0f, 0.1f, -4f, 5f, -8f),
        // Uppercut: start low and drive upward toward the center.
        BeatDirection(-0.08f, 0.3f, -0.15f, 0.03f, 0f, -13f, -3f, 5f),
    )
}
