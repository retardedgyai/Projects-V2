package dev.projects.server.experiment.swarm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwarmSliceBalanceTest {
    @Test
    fun `closed shell cannot be brute forced on encounter timescale`() {
        val closedDamage = SwarmSliceBalance.cairnbackThrustDamage(exposed = false, modMultiplier = 1.0)
        val hitsRequired = (SwarmSliceBalance.CAIRNBACK_BASE_HEALTH + closedDamage - 1) / closedDamage
        val minimumTicks = (hitsRequired - 1) * SwarmSliceBalance.THRUST_RECOVERY_TICKS

        assertTrue(hitsRequired >= 900)
        assertTrue(minimumTicks >= 10_000)
    }

    @Test
    fun `pillar exposure is the dominant damage window and mods apply once`() {
        val closed = SwarmSliceBalance.cairnbackThrustDamage(exposed = false, modMultiplier = 1.0)
        val exposed = SwarmSliceBalance.cairnbackThrustDamage(exposed = true, modMultiplier = 1.0)
        val barbed = SwarmSliceBalance.cairnbackThrustDamage(exposed = true, modMultiplier = 1.20)
        val guard = SwarmSliceBalance.cairnbackThrustDamage(exposed = true, modMultiplier = 0.90)

        assertTrue(exposed >= closed * 12)
        assertEquals(90, barbed)
        assertEquals(68, guard)
    }
}
