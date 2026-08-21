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
    fun `weakpoint target position still hits only once per execution`() {
        val combat = CombatState(sequence())
        val selection = FixedAttackTester.selectWeakpoint(
            playerPosition = Pos(0.0, 2.0, 3.0),
            playerDirection = Vec(0.0, 0.0, -1.0),
            testerOrigin = origin,
            testerFacing = forward,
            weaponRange = 4.5,
        )
        val target = CombatTarget(targetId, selection!!.center)

        combat.input(AttackInputState.PRESS)
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

    @Test
    fun `heavy blade is slower and reaches farther than twin rods at default speed`() {
        val heavy = WeaponType.HEAVY_BLADE.profile(1.0)
        val rods = WeaponType.TWIN_RODS.profile(1.0)

        assertTrue(heavy.totalTicks > rods.totalTicks)
        assertEquals(4.5, heavy.range)
        assertEquals(0.40, heavy.minForwardDot)
        assertEquals(3, rods.startupTicks)
        assertEquals(1, rods.activeTicks)
        assertEquals(4, rods.recoveryTicks)
        assertEquals(3.5, rods.range)
        assertEquals(0.65, rods.minForwardDot)
        assertEquals(1.75, rods.verticalRange)
        assertTrue(CombatState.isInAttackRange(heavy, origin, forward, Pos(0.0, 0.0, 4.25)))
        assertTrue(CombatState.isInAttackRange(heavy, origin, forward, Pos(0.0, 0.0, 3.5)))
        assertTrue(CombatState.isInAttackRange(rods, origin, forward, Pos(0.0, 0.0, 3.5)))
        assertTrue(!CombatState.isInAttackRange(rods, origin, forward, Pos(0.0, 0.0, 3.51)))
        val wideTarget = Pos(4.0, 0.0, 1.8)
        assertTrue(CombatState.isInAttackRange(heavy, origin, forward, wideTarget))
        assertTrue(!CombatState.isInAttackRange(rods, origin, forward, wideTarget))
    }

    @Test
    fun `twin rods follows full 3d look direction within reach and cone`() {
        val rods = WeaponType.TWIN_RODS.profile(1.0)
        val upward = Vec(1.0, 1.0, 0.0)
        val downward = Vec(1.0, -1.0, 0.0)

        assertTrue(CombatState.isInAttackRange(rods, origin, upward, Pos(2.0, 2.0, 0.0)))
        assertTrue(CombatState.isInAttackRange(rods, origin, downward, Pos(2.0, -2.0, 0.0)))
        assertTrue(!CombatState.isInAttackRange(rods, origin, upward, Pos(2.5, 2.5, 0.0)))
        assertTrue(!CombatState.isInAttackRange(rods, origin, upward, Pos(3.0, -1.0, 0.0)))
    }

    @Test
    fun `attack speed shortens both weapon timings from 1 to 2`() {
        for (weapon in WeaponType.entries) {
            val atOne = attackLength(weapon, 1.0)
            val atOneAndHalf = attackLength(weapon, 1.5)
            val atTwo = attackLength(weapon, 2.0)
            assertTrue(atOne > atOneAndHalf)
            assertTrue(atOneAndHalf > atTwo)
        }
    }

    @Test
    fun `heavy blade keeps more startup while attack speed reduces recovery`() {
        val atOne = WeaponType.HEAVY_BLADE.profile(1.0)
        val atTwo = WeaponType.HEAVY_BLADE.profile(2.0)

        assertTrue(atOne.startupTicks - atTwo.startupTicks < atOne.recoveryTicks - atTwo.recoveryTicks)
    }

    @Test
    fun `weapon slot changes do not alter the active attack profile`() {
        var weapon = WeaponType.HEAVY_BLADE
        val combat = CombatState(
            executionIdSource = sequence(),
            weaponSource = { weapon },
            attackSpeedSource = { 1.0 },
        )
        combat.input(AttackInputState.PRESS)
        val startedProfile = combat.activeProfile
        assertEquals(WeaponType.HEAVY_BLADE, startedProfile?.weapon)

        weapon = WeaponType.TWIN_RODS
        repeat(startedProfile!!.totalTicks - 1) {
            combat.tick(origin, forward, emptyList())
            assertEquals(startedProfile, combat.activeProfile)
        }
        combat.input(AttackInputState.RELEASE)
        combat.tick(origin, forward, emptyList())
        assertEquals(null, combat.activeProfile)
    }

    private fun finishAttack(combat: CombatState, targets: Collection<CombatTarget>): List<UUID> =
        buildList {
            repeat(combat.activeProfile!!.totalTicks) {
                addAll(combat.tick(origin, forward, targets).filterIsInstance<CombatEvent.HitConfirmed>().map { it.targetId })
            }
        }

    private fun attackLength(weapon: WeaponType, attackSpeed: Double): Int {
        val combat = CombatState(
            executionIdSource = sequence(),
            weaponSource = { weapon },
            attackSpeedSource = { attackSpeed },
        )
        combat.input(AttackInputState.PRESS)
        var ticks = 0
        while (true) {
            ticks++
            if (combat.tick(origin, forward, emptyList()).any { it is CombatEvent.Started && it.attackExecutionId == 2L }) {
                return ticks
            }
        }
    }

    private fun sequence(): () -> Long {
        var id = 0L
        return { ++id }
    }
}
