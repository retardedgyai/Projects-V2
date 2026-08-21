package dev.projects.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TwinRodFirstPersonAnimationStateTest {
    @Test
    fun `animation starts with a small windup and returns to neutral`() {
        val start = TwinRodFirstPersonAnimationState.poseAt(0f, 0)
        val strike = TwinRodFirstPersonAnimationState.poseAt(0.5f, 0)
        val end = TwinRodFirstPersonAnimationState.poseAt(1f, 0)

        assertNotEquals(start, strike)
        assertNotEquals(TwinRodPose(0f, 0f, 0f, 0f, 0f, 0f), start)
        assertEquals(TwinRodPose(0f, 0f, 0f, 0f, 0f, 0f), end)
    }

    @Test
    fun `beats use different directions`() {
        assertNotEquals(
            TwinRodFirstPersonAnimationState.poseAt(0.5f, 0),
            TwinRodFirstPersonAnimationState.poseAt(0.5f, 1),
        )
    }

    @Test
    fun `hit recoil is short and only affects the active pose`() {
        val withoutRecoil = TwinRodFirstPersonAnimationState.poseAt(0.5f, 0)
        val withRecoil = TwinRodFirstPersonAnimationState.poseAt(0.5f, 0, 1f)
        val recovered = TwinRodFirstPersonAnimationState.poseAt(0.5f, 0, 0f)

        assertNotEquals(withoutRecoil, withRecoil)
        assertEquals(withoutRecoil, recovered)
    }

    @Test
    fun `new attack replaces the previous beat and progress`() {
        TwinRodFirstPersonAnimationState.start(0)
        TwinRodFirstPersonAnimationState.tick()
        TwinRodFirstPersonAnimationState.start(1)

        assertEquals(
            TwinRodFirstPersonAnimationState.poseAt(0f, 1),
            TwinRodFirstPersonAnimationState.pose(0f),
        )
        TwinRodFirstPersonAnimationState.reset()
    }
}
