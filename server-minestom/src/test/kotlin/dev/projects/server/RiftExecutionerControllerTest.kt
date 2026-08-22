package dev.projects.server

import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RiftExecutionerControllerTest {
    private val playerId = UUID.randomUUID()
    private val origin = Vec(0.0, 0.0, 0.0)
    private val target = RiftExecutionerTarget(playerId, Vec(3.0, 0.0, 0.0))

    @Test
    fun `sector geometry matches radius angle and vertical tolerance`() {
        val facing = Vec(1.0, 0.0, 0.0)

        assertTrue(RiftExecutionerController.isInsideSector(origin, facing, 4.5, 100.0, Vec(4.5, 0.0, 0.0)))
        assertTrue(RiftExecutionerController.isInsideSector(origin, facing, 4.5, 100.0, Vec(3.0, 0.0, 3.0)))
        assertFalse(RiftExecutionerController.isInsideSector(origin, facing, 4.5, 100.0, Vec(3.0, 0.0, 4.0)))
        assertFalse(RiftExecutionerController.isInsideSector(origin, facing, 4.5, 100.0, Vec(3.0, 2.1, 0.0)))
    }

    @Test
    fun `sector execution emits one hit per target across active ticks`() {
        val controller = RiftExecutionerController(initialPauseTicks = 0)
        val events = mutableListOf<RiftExecutionerEvent>()

        repeat(17) {
            events += controller.tick(origin, Vec(1.0, 0.0, 0.0), listOf(target), PrototypeBossPhase.DUEL)
        }

        val sectorHits = events.filterIsInstance<RiftExecutionerEvent.AttackHit>()
            .filter { it.damage == RiftExecutionerController.SECTOR_DAMAGE }
        assertEquals(1, sectorHits.size)
    }

    @Test
    fun `dash overshoot is clamped and segment distance stays finite`() {
        val destination = RiftExecutionerController.overshootDestination(target.position, origin)
        assertEquals(5.5, destination.x())
        assertEquals(0.0, destination.z())
        assertEquals(0.0, RiftExecutionerController.distanceToSegment(Vec(2.0, 0.0, 0.0), origin, destination))
        assertTrue(RiftExecutionerController.distanceToSegment(Vec(2.0, 1.0, 0.0), origin, destination).isFinite())
    }

    @Test
    fun `phase two sector impact creates a rift and periodic damage has cadence`() {
        val controller = RiftExecutionerController(initialPauseTicks = 0)
        val events = mutableListOf<RiftExecutionerEvent>()
        repeat(40) {
            events += controller.tick(origin, Vec(1.0, 0.0, 0.0), listOf(target), PrototypeBossPhase.RIFT_PRESSURE)
        }

        assertTrue(events.any { it is RiftExecutionerEvent.RiftCreated })
        val damageHits = events.filterIsInstance<RiftExecutionerEvent.AttackHit>().count { it.damage == RiftExecutionerController.RIFT_DAMAGE }
        assertEquals(1, damageHits)
        repeat(19) {
            events += controller.tick(origin, Vec(1.0, 0.0, 0.0), listOf(target), PrototypeBossPhase.RIFT_PRESSURE)
        }
        val laterDamageHits = events.filterIsInstance<RiftExecutionerEvent.AttackHit>().count { it.damage == RiftExecutionerController.RIFT_DAMAGE }
        assertEquals(2, laterDamageHits)
    }

    @Test
    fun `rift pulses pass boss damage dedupe with unique executions`() {
        val controller = RiftExecutionerController(initialPauseTicks = 0)
        val boss = PrototypeBossState(playerMaxHealth = 100)
        val events = mutableListOf<RiftExecutionerEvent>()

        repeat(60) {
            events += controller.tick(origin, Vec(1.0, 0.0, 0.0), listOf(target), PrototypeBossPhase.RIFT_PRESSURE)
        }

        events.filterIsInstance<RiftExecutionerEvent.AttackHit>()
            .filter { it.damage == RiftExecutionerController.RIFT_DAMAGE }
            .forEach { boss.applyBossDamage(it.targetId, it.executionId, it.damage) }

        assertEquals(96, boss.playerHealth(playerId))
        assertEquals(2, events.filterIsInstance<RiftExecutionerEvent.AttackHit>()
            .filter { it.damage == RiftExecutionerController.RIFT_DAMAGE }
            .map { it.executionId }
            .distinct()
            .size)
    }

    @Test
    fun `forward slam uses its narrow rectangle instead of sector`() {
        val controller = RiftExecutionerController(initialPauseTicks = 0)
        val outsideRectangle = RiftExecutionerTarget(playerId, Vec(3.0, 0.0, 3.0))
        val insideRectangle = RiftExecutionerTarget(playerId, Vec(3.0, 0.0, 0.5))
        var slamStarted = false

        while (!slamStarted) {
            val events = controller.tick(origin, Vec(1.0, 0.0, 0.0), listOf(target), PrototypeBossPhase.DUEL)
            slamStarted = events.any {
                it is RiftExecutionerEvent.SectorTelegraph && it.attack == RiftExecutionerAttack.FORWARD_SLAM
            }
        }
        repeat(RiftExecutionerController.SLAM_TELEGRAPH_TICKS) {
            controller.tick(origin, Vec(1.0, 0.0, 0.0), listOf(outsideRectangle), PrototypeBossPhase.DUEL)
        }
        val outsideHits = controller.tick(
            origin,
            Vec(1.0, 0.0, 0.0),
            listOf(outsideRectangle),
            PrototypeBossPhase.DUEL,
        )
        assertFalse(outsideHits.any { it is RiftExecutionerEvent.AttackHit && it.damage == RiftExecutionerController.SLAM_DAMAGE })

        val insideController = RiftExecutionerController(initialPauseTicks = 0)
        while (true) {
            val events = insideController.tick(origin, Vec(1.0, 0.0, 0.0), listOf(target), PrototypeBossPhase.DUEL)
            if (events.any {
                    it is RiftExecutionerEvent.SectorTelegraph && it.attack == RiftExecutionerAttack.FORWARD_SLAM
                }) break
        }
        repeat(RiftExecutionerController.SLAM_TELEGRAPH_TICKS) {
            insideController.tick(origin, Vec(1.0, 0.0, 0.0), listOf(insideRectangle), PrototypeBossPhase.DUEL)
        }
        val insideHits = insideController.tick(
            origin,
            Vec(1.0, 0.0, 0.0),
            listOf(insideRectangle),
            PrototypeBossPhase.DUEL,
        )
        assertTrue(insideHits.any { it is RiftExecutionerEvent.AttackHit && it.damage == RiftExecutionerController.SLAM_DAMAGE })
    }

    @Test
    fun `dash hit continues chain while miss starts forty tick break`() {
        val hitController = RiftExecutionerController(initialPauseTicks = 0)
        reachDashTelegraph(hitController, target)
        val hitEvents = (1..RiftExecutionerController.DASH_TICKS).flatMap {
            hitController.tick(origin, Vec(1.0, 0.0, 0.0), listOf(target), PrototypeBossPhase.DUEL)
        }
        assertEquals(
            true,
            hitEvents.any { it is RiftExecutionerEvent.AttackHit && it.damage == RiftExecutionerController.DASH_DAMAGE },
            hitEvents.joinToString(),
        )
        assertFalse(hitEvents.any { it is RiftExecutionerEvent.BreakStarted })

        val missController = RiftExecutionerController(initialPauseTicks = 0)
        reachDashTelegraph(missController, RiftExecutionerTarget(playerId, Vec(8.0, 0.0, 0.0)))
        val missEvents = (1..RiftExecutionerController.DASH_TICKS).flatMap {
            missController.tick(
                origin,
                Vec(1.0, 0.0, 0.0),
                listOf(RiftExecutionerTarget(playerId, Vec(8.0, 0.0, 8.0))),
                PrototypeBossPhase.DUEL,
            )
        }
        assertTrue(missEvents.any { it is RiftExecutionerEvent.BreakStarted })
        repeat(RiftExecutionerController.BREAK_TICKS - 1) {
            missController.tick(origin, Vec(1.0, 0.0, 0.0), emptyList(), PrototypeBossPhase.DUEL)
        }
        assertEquals("BREAK", missController.controllerPhase)
        val ended = missController.tick(origin, Vec(1.0, 0.0, 0.0), emptyList(), PrototypeBossPhase.DUEL)
        assertTrue(ended.contains(RiftExecutionerEvent.BreakEnded))
    }

    @Test
    fun `final struggle completes after two hundred ticks`() {
        val controller = RiftExecutionerController(initialPauseTicks = 0)
        controller.startFinalStruggle()

        val events = (1..200).flatMap {
            controller.tick(origin, Vec(1.0, 0.0, 0.0), listOf(target), PrototypeBossPhase.EXECUTION, PrototypeEncounterState.FINAL_STRUGGLE)
        }

        assertIs<RiftExecutionerEvent.FinalStruggleComplete>(events.last())
        assertEquals("COMPLETE", controller.controllerPhase)
        assertTrue(events.any { it is RiftExecutionerEvent.DashTelegraph })
        assertTrue(events.any { it is RiftExecutionerEvent.DashPosition })
    }

    private fun reachDashTelegraph(controller: RiftExecutionerController, target: RiftExecutionerTarget) {
        while (true) {
            val events = controller.tick(origin, Vec(1.0, 0.0, 0.0), listOf(target), PrototypeBossPhase.DUEL)
            if (events.any { it is RiftExecutionerEvent.DashTelegraph }) {
                repeat(RiftExecutionerController.DASH_TELEGRAPH_TICKS) {
                    controller.tick(origin, Vec(1.0, 0.0, 0.0), listOf(target), PrototypeBossPhase.DUEL)
                }
                return
            }
        }
    }
}
