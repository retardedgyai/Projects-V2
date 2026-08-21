package dev.projects.server

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Skill2StateTest {
    @Test
    fun `skill2 rejects grounded cast and dives vertically when airborne`() {
        val skill2 = Skill2State(sequence())

        assertEquals(null, skill2.tryCast(true))
        assertTrue(skill2.isReady)
        assertEquals(0, ClassResourceState().snapshot(0, skill2.cooldownTicksRemaining, 0).skill2CooldownTicks)
        assertNotNull(skill2.tryCast(false))
        repeat(3) {
            val tick = skill2.tick(false)
            assertTrue(tick.diveActive)
            assertEquals(-18.0, tick.velocityY)
        }
        assertEquals(Skill2Phase.DIVE, skill2.phase)
        assertEquals(null, skill2.tryCast(false))
    }

    @Test
    fun `skill2 only lands once and hits targets whose bounding box is within radius`() {
        val skill2 = Skill2State(sequence())
        val inside = UUID.randomUUID()
        val outside = UUID.randomUUID()
        skill2.tryCast(false)

        assertFalse(skill2.tick(false).landed)
        assertTrue(skill2.hitTargetsAtLanding(Pos.ZERO, listOf(CombatTarget(inside, Pos(1.0, 0.0, 1.0)))).isEmpty())

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
