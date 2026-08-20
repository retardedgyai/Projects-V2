package dev.projects.server

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.util.UUID

class FixedAttackTesterTest {
    private val origin = Pos(0.0, 0.0, 0.0)
    private val forward = Vec(0.0, 0.0, 1.0)

    @Test
    fun `side sweep hits front and diagonal but misses outside and behind`() {
        val profile = FixedAttackType.SIDE_SWEEP
        assertTrue(inRegion(profile, Pos(0.0, 0.0, 3.0)))
        assertTrue(inRegion(profile, Pos(2.5, 0.0, 2.5)))
        assertFalse(inRegion(profile, Pos(4.5, 0.0, 0.0)))
        assertFalse(inRegion(profile, Pos(0.0, 0.0, -2.0)))
    }

    @Test
    fun `forward slam hits front but misses lateral dodge and range`() {
        val profile = FixedAttackType.FORWARD_SLAM
        assertTrue(inRegion(profile, Pos(0.0, 0.0, 4.0)))
        assertFalse(inRegion(profile, Pos(1.25, 0.0, 3.0)))
        assertFalse(inRegion(profile, Pos(0.0, 0.0, 5.1)))
    }

    @Test
    fun `attack direction is fixed at start and does not follow a moved target`() {
        val tester = testerStartingImmediately()
        val targetId = UUID.randomUUID()
        val started = tester.tick(origin, forward, emptyList())
            .filterIsInstance<FixedAttackEvent.Started>()
            .single()
        assertEquals(forward, started.direction)

        repeat(FixedAttackType.SIDE_SWEEP.telegraphTicks - 1) {
            tester.tick(origin, Vec(-1.0, 0.0, 0.0), emptyList())
        }
        val movedTarget = FixedAttackTarget(targetId, Pos(-3.0, 0.0, 0.0))
        val hitEvents = buildList {
            repeat(FixedAttackType.SIDE_SWEEP.activeTicks) {
                addAll(tester.tick(origin, Vec(-1.0, 0.0, 0.0), listOf(movedTarget)))
            }
        }
        assertTrue(hitEvents.none { it is FixedAttackEvent.HitConfirmed })
    }

    @Test
    fun `one execution hits once and next execution can hit again`() {
        val tester = testerStartingImmediately()
        val targetId = UUID.randomUUID()
        val target = FixedAttackTarget(targetId, Pos(0.0, 0.0, 3.0))
        val first = finishCurrentAttack(tester, target, FixedAttackType.SIDE_SWEEP)
        assertEquals(listOf(targetId), first)

        val second = finishCurrentAttack(tester, target, FixedAttackType.FORWARD_SLAM)
        assertEquals(listOf(targetId), second)
    }

    @Test
    fun `cycle starts with side sweep then forward slam`() {
        val tester = testerStartingImmediately()
        val first = tester.tick(origin, forward, emptyList())
        assertEquals(FixedAttackType.SIDE_SWEEP, first.filterIsInstance<FixedAttackEvent.Started>().single().attack)

        repeat(FixedAttackType.SIDE_SWEEP.telegraphTicks + FixedAttackType.SIDE_SWEEP.activeTicks + FixedAttackType.SIDE_SWEEP.recoveryTicks) {
            tester.tick(origin, forward, emptyList())
        }
        val next = tester.tick(origin, forward, emptyList())
        assertEquals(FixedAttackType.FORWARD_SLAM, next.filterIsInstance<FixedAttackEvent.Started>().single().attack)
    }

    @Test
    fun `ray through head selects head`() {
        val selection = FixedAttackTester.selectWeakpoint(
            playerPosition = Pos(0.0, 2.0, 3.0),
            playerDirection = Vec(0.0, 0.0, -1.0),
            testerOrigin = origin,
            testerFacing = forward,
            weaponRange = 4.5,
        )

        assertEquals(FixedWeakpoint.HEAD, selection?.weakpoint)
    }

    @Test
    fun `aiming outside head radius falls back to body`() {
        val selection = FixedAttackTester.selectWeakpoint(
            playerPosition = Pos(1.0, 2.0, 3.0),
            playerDirection = Vec(0.0, 0.0, -1.0),
            testerOrigin = origin,
            testerFacing = forward,
            weaponRange = 4.5,
        )

        assertEquals(null, selection)
    }

    @Test
    fun `ray from behind selects back`() {
        val selection = FixedAttackTester.selectWeakpoint(
            playerPosition = Pos(0.0, 1.45, -3.0),
            playerDirection = forward,
            testerOrigin = origin,
            testerFacing = forward,
            weaponRange = 4.5,
        )

        assertEquals(FixedWeakpoint.BACK, selection?.weakpoint)
    }

    @Test
    fun `weakpoint outside range falls back to body`() {
        val selection = FixedAttackTester.selectWeakpoint(
            playerPosition = Pos(0.0, 2.0, 6.0),
            playerDirection = Vec(0.0, 0.0, -1.0),
            testerOrigin = origin,
            testerFacing = forward,
            weaponRange = 2.0,
        )

        assertEquals(null, selection)
    }

    @Test
    fun `overlapping weakpoints choose only the ray closest candidate`() {
        val selection = FixedAttackTester.selectWeakpoint(
            playerPosition = Pos(0.0, 1.7, -3.0),
            playerDirection = forward,
            testerOrigin = origin,
            testerFacing = forward,
            weaponRange = 5.0,
            weakpointRadius = 1.0,
        )

        assertEquals(FixedWeakpoint.BACK, selection?.weakpoint)
    }

    @Test
    fun `weakpoint center rotates with tester facing`() {
        val head = FixedAttackTester.weakpointCenter(origin, Vec(1.0, 0.0, 0.0), FixedWeakpoint.HEAD)
        val back = FixedAttackTester.weakpointCenter(origin, Vec(1.0, 0.0, 0.0), FixedWeakpoint.BACK)

        assertEquals(0.55, head.x())
        assertEquals(-0.55, back.x())
        assertEquals(0.0, head.z())
        assertEquals(0.0, back.z())
    }

    private fun inRegion(attack: FixedAttackType, target: Pos): Boolean =
        FixedAttackTester.isInAttackRegion(attack, origin, forward, target)

    private fun testerStartingImmediately(): FixedAttackTester =
        FixedAttackTester(executionIdSource = sequence(), initialPauseTicks = 0, pauseTicks = 0)

    private fun finishCurrentAttack(
        tester: FixedAttackTester,
        target: FixedAttackTarget,
        attack: FixedAttackType,
    ): List<UUID> = buildList {
        repeat(1 + attack.telegraphTicks + attack.activeTicks + attack.recoveryTicks) {
            addAll(tester.tick(origin, forward, listOf(target)).filterIsInstance<FixedAttackEvent.HitConfirmed>().map { it.targetId })
        }
    }

    private fun sequence(): () -> Long {
        var id = 0L
        return { ++id }
    }
}
