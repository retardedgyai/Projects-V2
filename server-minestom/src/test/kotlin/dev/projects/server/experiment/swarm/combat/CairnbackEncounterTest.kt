package dev.projects.server.experiment.swarm.combat

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CairnbackEncounterTest {
    @Test
    fun `roster freezes one to four unique prepared entrants`() {
        val encounter = CairnbackEncounter(CairnbackConfig(baseMaxHealth = 100))
        assertFalse(encounter.start(emptyList(), 0).accepted)
        assertFalse(encounter.start(List(5) { UUID.randomUUID() }, 0).accepted)
        val duplicate = UUID.randomUUID()
        assertFalse(encounter.start(listOf(duplicate, duplicate), 0).accepted)

        val players = listOf(UUID.randomUUID(), UUID.randomUUID())
        val result = encounter.start(players, 0)
        assertTrue(result.accepted)
        assertEquals(players.toSet(), encounter.roster())
        assertEquals(165, encounter.maxHealth)
        assertFalse(encounter.start(listOf(UUID.randomUUID()), 1).accepted)
    }

    @Test
    fun `phase two begins at half health and uses fixed faster recombination`() {
        val player = UUID.randomUUID()
        val encounter = CairnbackEncounter(CairnbackConfig(baseMaxHealth = 100))
        encounter.start(listOf(player), 0)

        assertEquals(CairnbackPhase.PHASE_ONE, encounter.phase)
        encounter.applyTidehookHit(player, 1, 30)
        val threshold = encounter.applyTidehookHit(player, 2, 20)
        assertEquals(CairnbackPhase.PHASE_TWO, encounter.phase)
        assertTrue(threshold.events.single() is CairnbackEvent.PhaseChanged)

        val first = assertNotNull(encounter.beginNextAttack(10, player))
        assertEquals(CairnbackAttack.SWEEP, first.telegraph.attack)
        runPlan(encounter, first)
        val second = assertNotNull(encounter.beginNextAttack(100, player))
        assertEquals(CairnbackAttack.CHARGE, second.telegraph.attack)
        runPlan(encounter, second)
        val third = assertNotNull(encounter.beginNextAttack(200, player))
        assertEquals(CairnbackAttack.CHARGE, third.telegraph.attack)
        assertTrue(first.scheduled.first().dueTick - 10 < 26)
        assertTrue(second.scheduled.first().dueTick - 100 < 32)
    }

    @Test
    fun `powered pillar collision is the only charge exposure`() {
        val player = UUID.randomUUID()
        val encounter = CairnbackEncounter()
        encounter.start(listOf(player), 0)
        runPlan(encounter, assertNotNull(encounter.beginNextAttack(0, player)))
        val charge = assertNotNull(encounter.beginNextAttack(100, player))
        encounter.executeScheduled(charge.scheduled.first(), charge.scheduled.first().dueTick)

        assertTrue(encounter.collideChargeWithPillar(powered = false, tick = 140).events.isEmpty())
        assertFalse(encounter.exposed)
        val exposed = encounter.collideChargeWithPillar(powered = true, tick = 140)
        assertTrue(encounter.exposed)
        assertTrue(exposed.events.single() is CairnbackEvent.ExposureStarted)
        val end = exposed.scheduled.single()
        val ended = encounter.executeScheduled(end, end.dueTick)
        assertFalse(encounter.exposed)
        assertEquals(CairnbackState.RECOVERY, encounter.state)
        encounter.executeScheduled(charge.scheduled.last(), end.dueTick + 1)
        assertEquals(CairnbackState.RECOVERY, encounter.state)
        val ready = ended.scheduled.single()
        encounter.executeScheduled(ready, ready.dueTick)
        assertEquals(CairnbackState.READY, encounter.state)
    }

    @Test
    fun `victory callback includes only present roster members with three hits`() {
        val eligible = UUID.randomUUID()
        val short = UUID.randomUUID()
        val victories = mutableListOf<CairnbackEvent.Victory>()
        val encounter = CairnbackEncounter(
            CairnbackConfig(baseMaxHealth = 20, additionalPlayerHealthFactor = 0.0),
            CairnbackRewardSink { victories += it },
        )
        encounter.start(listOf(eligible, short), 0)
        repeat(3) { encounter.applyTidehookHit(eligible, it + 1L, 1) }
        repeat(2) { encounter.applyTidehookHit(short, it + 1L, 1) }

        val victory = encounter.applyTidehookHit(eligible, 99, 20)

        assertEquals(CairnbackLifecycle.VICTORY, encounter.lifecycle)
        assertEquals(setOf(eligible), victories.single().eligiblePlayers)
        assertTrue(victory.events.single() is CairnbackEvent.Victory)
        assertFalse(encounter.applyTidehookHit(eligible, 100, 1).accepted)
        assertEquals(1, victories.size)
    }

    @Test
    fun `disconnect permanently loses current encounter eligibility`() {
        val connected = UUID.randomUUID()
        val disconnected = UUID.randomUUID()
        val victories = mutableListOf<CairnbackEvent.Victory>()
        val encounter = CairnbackEncounter(
            CairnbackConfig(baseMaxHealth = 20, additionalPlayerHealthFactor = 0.0),
            CairnbackRewardSink { victories += it },
        )
        encounter.start(listOf(connected, disconnected), 0)
        repeat(3) { encounter.applyTidehookHit(connected, it + 1L, 1) }
        repeat(3) { encounter.applyTidehookHit(disconnected, it + 1L, 1) }
        encounter.markDisconnected(disconnected)

        encounter.applyTidehookHit(connected, 99, 20)

        assertEquals(setOf(connected), victories.single().eligiblePlayers)
        assertTrue(encounter.member(disconnected)!!.eligibilityLost)
    }

    @Test
    fun `wipe and hard timeout reset and invalidate scheduled generation`() {
        val player = UUID.randomUUID()
        val encounter = CairnbackEncounter()
        encounter.start(listOf(player), 0)
        val oldPlan = assertNotNull(encounter.beginNextAttack(0, player))
        encounter.markDisconnected(player)
        assertTrue(encounter.tick(10).events.isEmpty())
        val wipe = encounter.tick(110).events.single() as CairnbackEvent.Reset
        assertEquals(CairnbackResetReason.WIPE, wipe.reason)
        assertEquals(CairnbackLifecycle.READY, encounter.lifecycle)
        assertTrue(encounter.executeScheduled(oldPlan.scheduled.first(), oldPlan.scheduled.first().dueTick).events.isEmpty())

        encounter.start(listOf(player), 1_000)
        val timeout = encounter.tick(7_000).events.single() as CairnbackEvent.Reset
        assertEquals(CairnbackResetReason.HARD_TIMEOUT, timeout.reason)
        assertTrue(timeout.generation > timeout.previousGeneration)
    }

    @Test
    fun `attack cannot start for exited target and old callbacks are harmless`() {
        val player = UUID.randomUUID()
        val encounter = CairnbackEncounter()
        encounter.start(listOf(player), 0)
        val plan = assertNotNull(encounter.beginNextAttack(0, player))
        encounter.reset()

        assertTrue(encounter.executeScheduled(plan.scheduled.first(), plan.scheduled.first().dueTick).events.isEmpty())
        assertNull(encounter.beginNextAttack(100, player))
    }

    private fun runPlan(encounter: CairnbackEncounter, plan: CairnbackAttackPlan) {
        plan.scheduled.forEach { encounter.executeScheduled(it, it.dueTick) }
        assertEquals(CairnbackState.READY, encounter.state)
    }
}
