package dev.projects.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MagicEssenceStateTest {
    @Test
    fun `grant is capped and never creates negative state`() {
        val state = MagicEssenceState(capacity = 3)

        assertEquals(3, state.grant(PrimalEssence.EMBER, 9))
        assertEquals(0, state.grant(PrimalEssence.EMBER, 1))
        assertEquals(3, state.amount(PrimalEssence.EMBER))
        assertFalse(state.tryConsume(DerivedEssence.SPARK))
    }

    @Test
    fun `recipe consumes both inputs and creates deterministic result`() {
        val state = MagicEssenceState()
        state.grant(PrimalEssence.EMBER, 1)
        state.grant(PrimalEssence.GALE, 1)

        assertEquals(DerivedEssence.SPARK, state.tryCombine(PrimalEssence.GALE, PrimalEssence.EMBER))
        assertEquals(0, state.amount(PrimalEssence.EMBER))
        assertEquals(0, state.amount(PrimalEssence.GALE))
        assertEquals(1, state.amount(DerivedEssence.SPARK))
    }

    @Test
    fun `combat actions can acquire every primal essence`() {
        val state = MagicEssenceState()

        MagicCombatAction.entries.forEach(state::grantForCombatAction)

        PrimalEssence.entries.forEach { assertEquals(1, state.amount(it)) }
    }

    @Test
    fun `derived capacity rejects combine without consuming inputs`() {
        val state = MagicEssenceState(capacity = 1)
        state.grant(PrimalEssence.EMBER, 1)
        state.grant(PrimalEssence.TIDE, 1)
        assertEquals(DerivedEssence.BLOOM, state.tryCombine(PrimalEssence.EMBER, PrimalEssence.TIDE))
        state.grant(PrimalEssence.EMBER, 1)
        state.grant(PrimalEssence.TIDE, 1)

        assertNull(state.tryCombine(PrimalEssence.EMBER, PrimalEssence.TIDE))
        assertEquals(1, state.amount(PrimalEssence.EMBER))
        assertEquals(1, state.amount(PrimalEssence.TIDE))
        assertEquals(1, state.amount(DerivedEssence.BLOOM))
    }

    @Test
    fun `insufficient or invalid recipe does not mutate state`() {
        val state = MagicEssenceState()
        state.grant(PrimalEssence.STONE, 1)

        assertNull(state.tryCombine(PrimalEssence.STONE, PrimalEssence.TIDE))
        assertEquals(1, state.amount(PrimalEssence.STONE))
        assertNull(state.tryCombine(PrimalEssence.STONE, PrimalEssence.STONE))
    }

    @Test
    fun `derived result is consumed exactly once`() {
        val state = MagicEssenceState()
        state.grant(PrimalEssence.EMBER, 1)
        state.grant(PrimalEssence.TIDE, 1)
        state.tryCombine(PrimalEssence.EMBER, PrimalEssence.TIDE)

        assertTrue(state.tryConsume(DerivedEssence.BLOOM))
        assertFalse(state.tryConsume(DerivedEssence.BLOOM))
    }
}
