package dev.projects.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressionTest {
    @Test
    fun `xp boundary grants one point and carries remainder`() {
        val state = ProgressionState()
        assertEquals(1, state.addXp(100))
        assertEquals(2, state.level)
        assertEquals(0, state.xp)
        assertEquals(1, state.unspentSkillPoints)
        assertEquals(1, state.addXp(199))
        assertEquals(3, state.level)
        assertEquals(49, state.xp)
    }

    @Test
    fun `skill tree validates prerequisites duplicate and points`() {
        val state = ProgressionState()
        assertEquals(SpendResult.MISSING_PREREQUISITE, state.spend("swift-step"))
        state.addXp(100)
        assertEquals(SpendResult.ACCEPTED, state.spend("keen-edge"))
        assertEquals(SpendResult.ALREADY_ACQUIRED, state.spend("keen-edge"))
        assertEquals(SpendResult.INSUFFICIENT_POINTS, state.spend("swift-step"))
        assertFalse(state.has("swift-step"))
    }

    @Test
    fun `acquiring nodes changes twin blades effects`() {
        val state = ProgressionState()
        state.addXp(450)
        state.spend("keen-edge")
        state.spend("swift-step")
        state.spend("wide-cut")
        assertEquals(1.10, state.twinBladesEffects().normalDamageMultiplier)
        assertEquals(Skill1State.DASH_TICKS + 1, state.twinBladesEffects().skill1DashTicks)
        assertTrue(state.twinBladesEffects().skill1HitRadius > Skill1State.HIT_RADIUS)
    }
}
