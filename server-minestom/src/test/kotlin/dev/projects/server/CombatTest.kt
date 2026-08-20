package dev.projects.server

import dev.projects.protocol.AttackInputState
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.UUID

class CombatTest {
    private val origin = Pos(0.0, 0.0, 0.0)
    private val forward = Vec(0.0, 0.0, 1.0)
    private val targetId = UUID.randomUUID()

    @Test
    fun `front target is hit once and can be hit by next execution`() {
        val combat = CombatState(sequence())
        val target = CombatTarget(targetId, Pos(0.0, 0.0, 2.0))
        assertEquals(1, combat.input(AttackInputState.PRESS).size)
        val firstHits = finishAttack(combat, listOf(target))
        assertEquals(listOf(targetId), firstHits)
        assertEquals(listOf(targetId), finishAttack(combat, listOf(target)))
    }

    @Test
    fun `out of range and behind targets miss`() {
        val combat = CombatState(sequence())
        val targets = listOf(
            CombatTarget(UUID.randomUUID(), Pos(0.0, 0.0, 5.0)),
            CombatTarget(UUID.randomUUID(), Pos(0.0, 0.0, -2.0)),
        )
        combat.input(AttackInputState.PRESS)
        assertTrue(finishAttack(combat, targets).isEmpty())
    }

    @Test
    fun `release stops after current attack and hold starts another`() {
        val target = CombatTarget(targetId, Pos(0.0, 0.0, 2.0))
        val released = CombatState(sequence())
        released.input(AttackInputState.PRESS)
        released.input(AttackInputState.RELEASE)
        repeat(20) { released.tick(origin, forward, listOf(target)) }
        assertTrue(released.tick(origin, forward, listOf(target)).isEmpty())

        val held = CombatState(sequence())
        held.input(AttackInputState.PRESS)
        val events = buildList {
            repeat(20) { addAll(held.tick(origin, forward, listOf(target))) }
        }
        assertTrue(events.any { it is CombatEvent.Started && it.attackExecutionId == 2L })
    }

    private fun finishAttack(combat: CombatState, targets: Collection<CombatTarget>): List<UUID> =
        buildList {
            repeat(12) {
                addAll(combat.tick(origin, forward, targets).filterIsInstance<CombatEvent.HitConfirmed>().map { it.targetId })
            }
        }

    private fun sequence(): () -> Long {
        var id = 0L
        return { ++id }
    }
}
