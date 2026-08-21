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
        val motion = attackCurve(progress.coerceIn(0f, 1f))
        val recoil = hitRecoil.coerceIn(0f, 1f)
        return TwinRodPose(
            translateX = direction.x * motion * 0.14f,
            translateY = direction.y * motion * 0.1f,
            translateZ = direction.z * motion * 0.07f + recoil * 0.06f,
            rotateX = direction.rotateX * motion + recoil * 3.5f,
            rotateY = direction.rotateY * motion,
            rotateZ = direction.rotateZ * motion + recoil * 2.5f,
        )
    }

    private fun attackCurve(progress: Float): Float = when {
        progress <= 0.2f -> -0.3f * smoothStep(progress / 0.2f)
        progress <= 0.5f -> -0.3f + 1.3f * smoothStep((progress - 0.2f) / 0.3f)
        progress <= 0.75f -> 1f - 0.45f * smoothStep((progress - 0.5f) / 0.25f)
        else -> 0.55f * (1f - smoothStep((progress - 0.75f) / 0.25f))
    }

    private fun smoothStep(value: Float): Float {
        val clamped = value.coerceIn(0f, 1f)
        return clamped * clamped * (3f - 2f * clamped)
    }

    private data class BeatDirection(
        val x: Float,
        val y: Float,
        val z: Float,
        val rotateX: Float,
        val rotateY: Float,
        val rotateZ: Float,
    )

    private val BEAT_DIRECTIONS = listOf(
        BeatDirection(1f, 0.25f, -0.4f, -12f, 18f, -28f),
        BeatDirection(-1f, 0.1f, -0.25f, -8f, -20f, 26f),
        BeatDirection(0.2f, 1f, -0.3f, -28f, 8f, -10f),
        BeatDirection(-0.3f, -0.8f, -0.2f, 22f, -12f, 14f),
    )
}
