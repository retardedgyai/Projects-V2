package dev.projects.server

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Skill2StateTest {
    @Test
    fun `skill2 rejects grounded cast and emits four timed pulses while accelerating`() {
        val skill2 = Skill2State(sequence())

        assertEquals(null, skill2.tryCast(true))
        assertTrue(skill2.isReady)
        assertEquals(0, ClassResourceState().snapshot(0, skill2.cooldownTicksRemaining, 0).skill2CooldownTicks)
        assertNotNull(skill2.tryCast(false))
        val ticks = (0 until 8).map { skill2.tick(false) }
        assertTrue(ticks.all { it.diveActive })
        assertEquals(listOf(1, 2, 3, 4), ticks.mapNotNull { it.pulseIndex })
        assertEquals(-10.0, ticks.first().velocityY)
        assertEquals(-18.0, ticks[6].velocityY)
        assertEquals(Skill2Phase.DIVE, skill2.phase)
        assertEquals(null, skill2.tryCast(false))
    }

    @Test
    fun `skill2 hits one target per pulse and only lands once`() {
        val skill2 = Skill2State(sequence())
        val inside = UUID.randomUUID()
        val outside = UUID.randomUUID()
        skill2.tryCast(false)

        assertEquals(1, skill2.tick(false).pulseIndex)
        val pulseTargets = listOf(
            CombatTarget(inside, Pos(2.5, 0.0, 0.0)),
            CombatTarget(outside, Pos(3.0, 0.0, 0.0)),
        )
        assertEquals(listOf(inside), skill2.hitTargetsAtPulse(1, Pos.ZERO, pulseTargets))
        assertTrue(skill2.hitTargetsAtPulse(1, Pos.ZERO, pulseTargets).isEmpty())

        skill2.tick(false)
        assertEquals(2, skill2.tick(false).pulseIndex)
        assertEquals(listOf(inside), skill2.hitTargetsAtPulse(2, Pos.ZERO, pulseTargets))

        assertTrue(skill2.tick(true).landed)
        val targets = listOf(
            CombatTarget(inside, Pos(4.5, 0.0, 0.0), Vec(1.0, 0.0, 0.0)),
            CombatTarget(outside, Pos(4.1, 0.0, 4.1), Vec.ZERO),
        )
        assertEquals(listOf(inside), skill2.hitTargetsAtLanding(Pos.ZERO, targets))
        assertTrue(skill2.hitTargetsAtLanding(Pos.ZERO, targets).isEmpty())
        assertEquals(100, skill2.cooldownTicksRemaining)
    }

    @Test
    fun `skill2 pulse uses three dimensional distance`() {
        val skill2 = Skill2State(sequence())
        val farAbove = UUID.randomUUID()
        val withinRadius = UUID.randomUUID()
        skill2.tryCast(false)

        assertEquals(1, skill2.tick(false).pulseIndex)
        val targets = listOf(
            CombatTarget(farAbove, Pos(0.0, 3.0, 0.0)),
            CombatTarget(withinRadius, Pos(0.0, 2.5, 0.0)),
        )
        assertEquals(listOf(withinRadius), skill2.hitTargetsAtPulse(1, Pos.ZERO, targets))
    }

    @Test
    fun `early landing closes the storm without residual pulses`() {
        val skill2 = Skill2State(sequence())
        skill2.tryCast(false)

        assertTrue(skill2.tick(true).landed)
        assertTrue((0 until 8).all { skill2.tick(false).pulseIndex == null })
    }

    @Test
    fun `skill2 reset clears cooldown and dive state`() {
        val skill2 = Skill2State(sequence())
        skill2.tryCast(false)
        skill2.tick(true)
        skill2.reset()

        assertTrue(skill2.isReady)
        assertEquals(Skill2Phase.IDLE, skill2.phase)
        assertEquals(0, skill2.cooldownTicksRemaining)
    }

    private fun sequence(): () -> Long {
        var id = 0L
        return { ++id }
    }
}
