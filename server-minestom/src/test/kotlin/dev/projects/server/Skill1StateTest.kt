package dev.projects.server

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Skill1StateTest {
    @Test
    fun `skill1 starts ready fixes horizontal direction and rejects cooldown cast`() {
        val skill1 = Skill1State(sequence())

        assertTrue(skill1.isReady)
        assertNotNull(skill1.tryCast(Vec(1.0, 0.8, 1.0)))
        assertEquals(Vec(0.7071067811865475, 0.0, 0.7071067811865475), skill1.dashDirection)
        assertFalse(skill1.isReady)
        assertEquals(null, skill1.tryCast(Vec(0.0, 0.0, 1.0)))

        repeat(4) { tick ->
            val result = skill1.tick()
            assertTrue(result.dashActive)
            assertEquals(if (tick == 3) 0 else 4 - tick - 1, skill1.dashTicksRemaining)
        }
        assertEquals(Skill1Phase.IDLE, skill1.phase)
        skill1.reset()
        assertTrue(skill1.isReady)
    }

    @Test
    fun `skill1 miss has no hit target and hit consumes only the first target`() {
        val skill1 = Skill1State(sequence())
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        skill1.tryCast(Vec(0.0, 0.0, 1.0))

        assertTrue(
            skill1.hitTargetsOnSegment(
                Pos(0.0, 0.0, 0.0),
                Pos(0.0, 0.0, 0.5),
                listOf(CombatTarget(UUID.randomUUID(), Pos(5.0, 0.0, 0.0))),
            ).isEmpty(),
        )
        assertEquals(
            listOf(first),
            skill1.hitTargetsOnSegment(
                Pos(0.0, 0.0, 0.0),
                Pos(0.0, 0.0, 2.0),
                listOf(
                    CombatTarget(first, Pos(0.0, 0.0, 1.0)),
                    CombatTarget(second, Pos(0.0, 0.0, 1.2)),
                ),
            ),
        )
        assertTrue(
            skill1.hitTargetsOnSegment(
                Pos(0.0, 0.0, 0.0),
                Pos(0.0, 0.0, 2.0),
                listOf(CombatTarget(second, Pos(0.0, 0.0, 1.2))),
            ).isEmpty(),
        )
    }

    @Test
    fun `skill1 hit launch uses the updated vertical speed`() {
        assertEquals(20.0, Skill1State.LAUNCH_SPEED_Y)
        assertEquals(2.5, Skill1State.LAUNCH_HORIZONTAL_SPEED)
    }

    @Test
    fun `skill1 cooldown recovery modifier consumes fractional ticks`() {
        val skill1 = Skill1State(sequence())
        skill1.tryCast(Vec(0.0, 0.0, 1.0))

        repeat(5) { skill1.tick(1.20) }

        assertEquals(74, skill1.cooldownTicksRemaining)
    }

    @Test
    fun `skill2 cast cancels skill1 movement but preserves its cooldown`() {
        val skill1 = Skill1State(sequence())
        val skill2 = Skill2State(sequence())
        skill1.tryCast(Vec(0.0, 0.0, 1.0))
        assertEquals(Skill1Phase.DASH, skill1.phase)
        assertEquals(80, skill1.cooldownTicksRemaining)

        assertNotNull(skill2.tryCast(false))
        skill1.cancelActiveMovement()

        assertEquals(Skill1Phase.IDLE, skill1.phase)
        assertEquals(0, skill1.dashTicksRemaining)
        assertEquals(80, skill1.cooldownTicksRemaining)
        assertTrue(skill1.hitTargetsOnSegment(
            Pos.ZERO,
            Pos(0.0, 0.0, 2.0),
            listOf(CombatTarget(UUID.randomUUID(), Pos(0.0, 0.0, 1.0))),
        ).isEmpty())
    }

    private fun sequence(): () -> Long {
        var id = 0L
        return { ++id }
    }
}
