package dev.projects.server.coreloop

import dev.projects.server.mob.CombatTarget
import dev.projects.server.mob.HitSphere
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreMageBoltTest {
    private val east = Vec(1.0, 0.0, 0.0)
    private fun bolt() = CoreMageBolt(Pos(0.0, 1.6, 0.0), east, Vec(0.0, -.35, .45), 1.0)
    private fun zombie(x: Double, y: Double = 1.6) = CombatTarget(UUID.randomUUID(), Pos(x, y, 0.0), Vec(.35, .9, .35))

    @Test fun `flies a block and a half a tick and hits the first body on its path when it gets there`() {
        val near = zombie(5.0); val far = zombie(6.0)
        val b = bolt()
        repeat(3) { assertNull(b.step(listOf(far, near)) { false }.hit) }
        val step = b.step(listOf(far, near)) { false }
        assertEquals(near.id, step.hit)
        assertTrue(step.ended)
        assertTrue(step.at.x() in 4.5..5.0, "stops at the body, not past it: ${step.at}")
    }

    @Test fun `a body is an upright line, so a shot at the head or the knees still lands`() {
        assertTrue(bolt().step(listOf(zombie(1.0, 2.3))) { false }.hit != null)
        assertTrue(bolt().step(listOf(zombie(1.0, .9))) { false }.hit != null)
        assertNull(bolt().step(listOf(zombie(1.0, 4.0))) { false }.hit)
    }

    @Test fun `a wall stops the bolt before a body behind it`() {
        val b = bolt()
        val step = b.step(listOf(zombie(1.4))) { it.x() >= 1.0 }
        assertNull(step.hit)
        assertTrue(step.ended)
        assertTrue(step.at.x() < 1.0)
    }

    @Test fun `bodies made of spheres are hit by their spheres`() {
        val wyrm = CombatTarget(UUID.randomUUID(), Pos(20.0, 0.0, 0.0), parts = listOf(HitSphere(Pos(3.0, 1.6, 1.0), .5)))
        val b = bolt()
        assertNull(b.step(listOf(wyrm)) { false }.hit)
        assertEquals(wyrm.id, b.step(listOf(wyrm)) { false }.hit)
    }

    @Test fun `ends at eighteen blocks and the picture joins the aim line in four ticks`() {
        val b = bolt()
        assertTrue(b.shown.y() < 1.6)
        var ticks = 0
        while (!b.step(emptyList()) { false }.ended) ticks++
        assertEquals(11, ticks)
        assertEquals(18.0, b.position.x(), 1e-9)
        assertEquals(b.position, b.shown)
    }
}
