package dev.projects.client

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
        beat = nextBeat.mod(2)
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
        val clampedProgress = progress.coerceIn(0f, 1f)
        val guard = guardCurve(clampedProgress)
        val strike = strikeCurve(clampedProgress)
        val recoil = hitRecoil.coerceIn(0f, 1f)
        return TwinRodPose(
            // The renderer mirrors this local X offset for the left arm.
            translateX = 0.42f * guard,
            translateY = -0.18f * guard + 0.02f * strike,
            translateZ = -0.42f * strike + recoil * 0.06f,
            rotateX = -4f * strike + recoil * 3.5f,
            rotateY = 3f * strike,
            rotateZ = -6f * strike + recoil * 2.5f,
        )
    }

    fun activeArmIsRight(beat: Int): Boolean = beat.mod(2) == 0

    fun activeArmIsRight(): Boolean = activeArmIsRight(beat)

    private fun guardCurve(progress: Float): Float = when {
        progress <= 0.2f -> 1f - smoothStep(progress / 0.2f)
        else -> 0f
    }

    private fun strikeCurve(progress: Float): Float = when {
        progress <= 0.2f -> 0f
        progress <= 0.55f -> smoothStep((progress - 0.2f) / 0.35f)
        else -> 1f - smoothStep((progress - 0.55f) / 0.45f)
    }

    private fun smoothStep(value: Float): Float {
        val clamped = value.coerceIn(0f, 1f)
        return clamped * clamped * (3f - 2f * clamped)
    }

}
