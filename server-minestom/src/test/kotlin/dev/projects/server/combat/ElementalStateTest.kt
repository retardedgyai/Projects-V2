package dev.projects.server.combat

import dev.projects.server.combat.damage.DamageType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ElementalStateTest {
    @Test
    fun `fire keeps fractional remainder and detonates at ten stacks`() {
        val state = FireState()

        state.apply(1.5, contributorId = "projects:ember", lineage = DamageType.PHYSICAL, hitExecutionId = 1L)
        assertEquals(1, state.stacks)
        assertEquals(0.5, state.remainder)

        state.apply(1.5, contributorId = "projects:ember", lineage = DamageType.PHYSICAL, hitExecutionId = 2L)
        assertEquals(3, state.stacks)
        assertEquals(0.0, state.remainder)

        state.reset()
        state.apply(9.0, contributorId = "projects:ember", lineage = DamageType.PHYSICAL, hitExecutionId = 3L)
        val detonation = state.apply(
            1.0,
            contributorId = "projects:ember",
            lineage = DamageType.PHYSICAL,
            hitExecutionId = 4L,
        )

        assertTrue(detonation.detonated)
        assertEquals(3, state.stacks)
        assertEquals(2.5, detonation.detonations.single().effectiveFireContribution)
        assertEquals(2.5, detonation.detonations.single().primaryDamage)
        assertEquals(1.5, detonation.detonations.single().nearbyDamage)
        assertFalse(detonation.detonations.single().spreadsBurn)
        assertEquals("projects:ember", detonation.attribution?.contributorId)
        assertEquals(DamageType.PHYSICAL, detonation.attribution?.lineage)
    }

    @Test
    fun `fire ignores duplicate hit and decays only after five second hold`() {
        val state = FireState()

        state.apply(1.5, contributorId = "projects:ember", hitExecutionId = 10L)
        val duplicate = state.apply(1.5, contributorId = "projects:ember", hitExecutionId = 10L)
        assertTrue(duplicate.duplicateHit)
        assertEquals(1, state.stacks)
        assertEquals(0.5, state.remainder)

        state.tick(FireState.HOLD_TICKS)
        assertEquals(1, state.stacks)
        assertEquals(0.5, state.remainder)

        state.tick(FireState.DECAY_INTERVAL_TICKS)
        assertEquals(1, state.stacks)
        assertEquals(0.0, state.remainder)
        state.tick(FireState.DECAY_INTERVAL_TICKS)
        assertEquals(0, state.stacks)
    }

    @Test
    fun `ice progresses to frozen and only a later hit shatters`() {
        val state = IceState(TargetClassification.BOSS)

        assertEquals(ColdLevel.COLD_I, state.apply(1.0, hitExecutionId = 1L).levelAfter)
        assertEquals(ColdLevel.COLD_II, state.apply(1.0, hitExecutionId = 2L).levelAfter)
        val freeze = state.apply(1.0, hitExecutionId = 3L)
        assertTrue(freeze.createdFrozen)
        assertFalse(freeze.wasFrozenBeforeHit)
        assertEquals(ColdLevel.FROZEN, freeze.levelAfter)
        assertEquals(null, freeze.shatter)

        val shatter = state.apply(
            applicationPower = 0.25,
            preCriticalDirectDamage = 100.0,
            contributorId = "projects:frost",
            lineage = DamageType.PHYSICAL,
            hitExecutionId = 4L,
        )
        val impact = assertNotNull(shatter.shatter)
        assertTrue(shatter.wasFrozenBeforeHit)
        assertEquals(1.08, shatter.directDamageMultiplier)
        assertEquals(125.0, impact.impactDamage)
        assertEquals(1.5, impact.coreBonus)
        assertEquals(126.5, impact.totalBonusDamage)
        assertFalse(impact.critical)
        assertEquals(1, impact.targetCount)
        assertEquals(1.2, impact.retainedCold, 0.0001)
        assertEquals(ColdLevel.COLD_I, state.level)
        assertEquals(8 * 20, state.immunityTicksRemaining)
    }

    @Test
    fun `ice caps during immunity and needs a later valid hit to refreeze`() {
        val state = IceState(TargetClassification.NORMAL)
        state.apply(3.0, hitExecutionId = 1L)
        state.apply(0.1, hitExecutionId = 2L)
        assertEquals(3 * 20, state.immunityTicksRemaining)

        val capped = state.apply(10.0, hitExecutionId = 3L)
        assertFalse(capped.createdFrozen)
        assertEquals(3.0, capped.gaugeAfter)
        assertEquals(ColdLevel.COLD_II, state.level)

        state.tick(3 * 20)
        val refreeze = state.apply(0.1, hitExecutionId = 4L)
        assertTrue(refreeze.createdFrozen)
        assertEquals(ColdLevel.FROZEN, state.level)
    }

    @Test
    fun `ice immunity policy matches target classification`() {
        assertEquals(3 * 20, IceState.refreezeImmunityTicks(TargetClassification.NORMAL))
        assertEquals(4 * 20, IceState.refreezeImmunityTicks(TargetClassification.ELITE))
        assertEquals(5 * 20, IceState.refreezeImmunityTicks(TargetClassification.MINIBOSS))
        assertEquals(8 * 20, IceState.refreezeImmunityTicks(TargetClassification.BOSS))
    }

    @Test
    fun `elemental state is shared by target rather than contributor`() {
        val store = ElementalCombatState()
        val targetId = UUID.randomUUID()
        val first = store.target(targetId)
        first.fire.apply(1.0, contributorId = "projects:ember", hitExecutionId = 1L)

        val sameTarget = store.target(targetId)
        sameTarget.fire.apply(1.0, contributorId = "projects:other-ember", hitExecutionId = 2L)

        assertEquals(2, sameTarget.fire.stacks)
        assertEquals("projects:other-ember", sameTarget.fire.attribution?.contributorId)
    }
}
