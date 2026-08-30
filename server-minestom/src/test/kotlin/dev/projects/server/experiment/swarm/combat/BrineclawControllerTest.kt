package dev.projects.server.experiment.swarm.combat

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrineclawControllerTest {
    @Test
    fun `sweep moves through telegraph active recovery and ready once`() {
        val controller = BrineclawController()
        val plan = assertNotNull(controller.beginSweep(10))

        assertEquals(BrineclawState.SWEEP_TELEGRAPH, controller.state)
        assertTrue(controller.executeScheduled(plan.scheduled[0], plan.scheduled[0].dueTick - 1).events.isEmpty())
        assertEquals(1, controller.executeScheduled(plan.scheduled[0], plan.scheduled[0].dueTick).events.size)
        assertEquals(BrineclawState.SWEEP_ACTIVE, controller.state)
        assertTrue(controller.executeScheduled(plan.scheduled[0], plan.scheduled[0].dueTick).events.isEmpty())

        controller.executeScheduled(plan.scheduled[1], plan.scheduled[1].dueTick)
        assertEquals(BrineclawState.RECOVERY, controller.state)
        controller.executeScheduled(plan.scheduled[2], plan.scheduled[2].dueTick)
        assertEquals(BrineclawState.READY, controller.state)
    }

    @Test
    fun `charge exposes only after active practice post collision and resets exposure`() {
        val controller = BrineclawController()
        assertTrue(controller.collideChargeWithPracticePost(0).events.isEmpty())
        val plan = assertNotNull(controller.beginCharge(0))
        controller.executeScheduled(plan.scheduled[0], plan.scheduled[0].dueTick)

        val collision = controller.collideChargeWithPracticePost(plan.scheduled[0].dueTick + 1)
        assertEquals(BrineclawState.EXPOSED, controller.state)
        assertEquals(1, collision.events.size)
        val endExposure = collision.scheduled.single()
        val ended = controller.executeScheduled(endExposure, endExposure.dueTick)
        assertEquals(BrineclawState.RECOVERY, controller.state)
        assertTrue(ended.events.single() is BrineclawEvent.ExposureEnded)

        controller.executeScheduled(plan.scheduled.last(), endExposure.dueTick + 1)
        assertEquals(BrineclawState.RECOVERY, controller.state)

        val ready = ended.scheduled.single()
        controller.executeScheduled(ready, ready.dueTick)
        assertEquals(BrineclawState.READY, controller.state)
    }

    @Test
    fun `old scheduled actions are no ops after reset`() {
        val controller = BrineclawController()
        val plan = assertNotNull(controller.beginCharge(0))
        val oldGeneration = controller.generation

        controller.reset()

        assertTrue(controller.generation > oldGeneration)
        assertTrue(controller.executeScheduled(plan.scheduled.first(), plan.scheduled.first().dueTick).events.isEmpty())
        assertEquals(BrineclawState.READY, controller.state)
    }

    @Test
    fun `defeat callback lists participants once and duplicate action cannot damage`() {
        val events = mutableListOf<BrineclawEvent.Defeated>()
        val controller = BrineclawController(
            config = BrineclawConfig(maxHealth = 10),
            defeatSink = BrineclawDefeatSink { events += it },
        )
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()

        assertTrue(controller.applyTidehookHit(first, 1, 4).accepted)
        assertFalse(controller.applyTidehookHit(first, 1, 4).accepted)
        assertTrue(controller.applyTidehookHit(second, 1, 6).defeated)
        assertEquals(setOf(first, second), events.single().participants)
        assertFalse(controller.applyTidehookHit(first, 2, 1).accepted)
        assertEquals(1, events.size)
        assertNull(controller.beginSweep(0))
    }
}
