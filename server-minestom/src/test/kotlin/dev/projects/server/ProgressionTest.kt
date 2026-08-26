package dev.projects.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressionTest {
    @Test
    fun `xp threshold carries remainder and grants one point per level`() {
        val state = ProgressionState()

        val first = state.addXp(100)
        assertEquals(1, first.levelUpCount)
        assertEquals(2, state.level)
        assertEquals(0, state.experience)
        assertEquals(1, state.grantedPassivePoints)

        state.addXp(449)
        assertEquals(4, state.level)
        assertEquals(99, state.experience)
        assertEquals(3, state.grantedPassivePoints)
    }

    @Test
    fun `level cap discards overflow without creating invalid state`() {
        val state = ProgressionState(level = 44, grantedPassivePoints = 43)

        state.addXp(10_000)

        assertEquals(ProgressionState.MAX_LEVEL, state.level)
        assertEquals(0, state.experience)
        assertEquals(44, state.grantedPassivePoints)
        assertEquals(0, state.xpRequiredForNextLevel)
        state.addXp(100)
        assertEquals(45, state.level)
        assertEquals(0, state.experience)
    }

    @Test
    fun `global tree validates stale revision prerequisites duplicates and points`() {
        val state = ProgressionState(grantedPassivePoints = 2)

        assertEquals(PassiveSpendResult.MISSING_PREREQUISITE, state.spend(GlobalPassiveTree.OVERPOWER))
        assertEquals(PassiveSpendResult.STALE_REVISION, state.spend(GlobalPassiveTree.FORCE, expectedRevision = 1L))
        assertEquals(PassiveSpendResult.ACCEPTED, state.spend(GlobalPassiveTree.FORCE, expectedRevision = 0L))
        assertEquals(PassiveSpendResult.ALREADY_ACQUIRED, state.spend(GlobalPassiveTree.FORCE, expectedRevision = 1L))
        assertEquals(PassiveSpendResult.ACCEPTED, state.spend(GlobalPassiveTree.OVERPOWER, expectedRevision = 1L))
        assertEquals(PassiveSpendResult.INSUFFICIENT_POINTS, state.spend(GlobalPassiveTree.TEMPO))
        assertTrue(state.has(GlobalPassiveTree.OVERPOWER))
        assertEquals(0, state.availablePassivePoints)
    }

    @Test
    fun `all global nodes resolve to combat modifiers`() {
        val state = ProgressionState(
            grantedPassivePoints = GlobalPassiveTree.nodes.size,
        )
        GlobalPassiveTree.nodes.forEach { node ->
            assertEquals(PassiveSpendResult.ACCEPTED, state.spend(node.id))
        }

        val effects = state.effects()
        assertEquals(1.30, effects.directDamageMultiplier)
        assertEquals(1.15, effects.normalAttackSpeedMultiplier)
        assertEquals(1.20, effects.cooldownRecoveryMultiplier)
        assertEquals(4, effects.maxHealthBonus)
        assertEquals(0.90, effects.incomingPveDamageMultiplier)
    }

    @Test
    fun `default effects preserve existing combat values`() {
        val effects = ProgressionState().effects()

        assertEquals(ProgressionEffects.DEFAULT, effects)
        assertFalse(ProgressionState().has(GlobalPassiveTree.FORCE))
    }
}
