package dev.projects.server.coreloop

import dev.projects.server.CombatTarget
import dev.projects.server.particle.*
import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.test.*

class GreatswordTest {
    @Test fun `three combo stages have one impact and the final swing is heavier and slower`() {
        val combo = GreatswordCombo()
        val impacts = mutableListOf<GreatswordCombo.Swing>()
        repeat(3) { stage ->
            val swing = combo.press(1.0)!!
            assertEquals(stage + 1, swing.step)
            repeat(swing.totalTicks) { combo.tick()?.let(impacts::add) }
        }
        assertEquals(listOf(1.0, 1.15, 1.85), impacts.map { it.multiplier })
        assertEquals(listOf(8, 8, 11), impacts.map { it.impactTick })
        assertEquals(listOf(20, 22, 30), impacts.map { it.totalTicks })
        assertEquals(1, combo.press(1.0)!!.step)
    }

    @Test fun `duplicate startup presses never reset windup and recovery buffers only one attack`() {
        val combo = GreatswordCombo()
        combo.press(1.0)
        repeat(13) { assertNull(combo.press(1.0)); combo.tick() }
        assertFalse(combo.takeBuffered())
        repeat(7) { combo.tick(); if (combo.isAttacking) repeat(10) { combo.press(1.0) } }
        assertTrue(combo.takeBuffered())
        assertFalse(combo.takeBuffered())
        assertEquals(2, combo.press(1.0)!!.step)
    }

    @Test fun `idle gap and return reset both restart from first stage`() {
        val combo = GreatswordCombo()
        combo.press(1.0); repeat(20) { combo.tick() }
        repeat(18) { combo.tick() }
        assertEquals(1, combo.press(1.0)!!.step)
        repeat(16) { combo.tick() }; combo.press(1.0)
        combo.reset()
        assertFalse(combo.takeBuffered())
        assertNull(combo.tick())
        assertEquals(1, combo.press(1.0)!!.step)
    }

    @Test fun `haste is finite bounded and preserves startup and recovery`() {
        for (speed in listOf(1.0, 1.84, 200.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val swing = GreatswordCombo().press(speed)!!
            assertTrue(swing.impactTick >= 5)
            assertTrue(swing.totalTicks - swing.impactTick >= 7)
        }
    }

    @Test fun `slope reach is symmetric feet relative but does not extend beyond three blocks`() {
        fun target(feet: Double) = CombatTarget(UUID.randomUUID(), Vec(0.0, feet + 1.17, 4.0), Vec(.4, 1.17, .4))
        for (feet in listOf(-3.0, -2.5, 0.0, 2.5, 3.0)) assertTrue(greatswordInRange(Vec.ZERO, Vec(0.0, 0.0, 1.0), target(feet)))
        for (feet in listOf(-3.01, 3.01)) assertFalse(greatswordInRange(Vec.ZERO, Vec(0.0, 0.0, 1.0), target(feet)))
        assertFalse(greatswordInRange(Vec.ZERO, Vec(0.0, 0.0, -1.0), target(0.0)))
        assertTrue(greatswordInRange(Vec.ZERO, Vec.ZERO, target(0.0)))
    }

    @Test fun `all eight effects have finite bounded frames and expire without more emission`() {
        for (kind in GreatswordVisual.entries) for (facing in listOf(Vec.ZERO, Vec(0.0, 1.0, 0.0), Vec(0.0, -1.0, 0.0), Vec(1.0, .5, 1.0))) {
            val effect = GreatswordEffect(kind, Vec(8.0, 40.0, 8.0), facing)
            assertTrue(effect.durationTicks in 1..10)
            val sink = RecordingParticleSink()
            repeat(effect.durationTicks) { tick ->
                sink.clear(); effect.emit(tick, sink)
                assertTrue(sink.spawns.size <= 90, "$kind $tick ${sink.spawns.size}")
                assertTrue(sink.spawns.all { it.position.x().isFinite() && it.position.y().isFinite() && it.position.z().isFinite() })
            }
            sink.clear(); effect.emit(effect.durationTicks, sink)
            assertTrue(sink.spawns.isEmpty())
        }
    }

    @Test fun `vertical finisher visibly spans height while horizontal sweep stays level`() {
        fun points(kind: GreatswordVisual): List<Double> {
            val sink = RecordingParticleSink()
            GreatswordEffect(kind, Vec.ZERO, Vec(0.0, 0.0, 1.0)).emit(3, sink)
            return sink.spawns.map { it.position.y() }
        }
        val finisher = points(GreatswordVisual.FINISHER)
        val sweep = points(GreatswordVisual.SWEEP)
        assertTrue(finisher.max() - finisher.min() > 3.0)
        assertTrue(sweep.max() - sweep.min() < .2)
    }

    @Test fun `framework viewer budget and distance LOD bound dispatch`() {
        val manager = ParticleManager(ParticleQuality(distanceFalloffStart = 12.0, distanceFalloffEnd = 32.0), ParticleBudget(180))
        val frame = RecordingParticleSink()
        repeat(10) { GreatswordEffect(GreatswordVisual.FINISHER, Vec.ZERO, Vec(0.0, 0.0, 1.0)).emit(3, frame) }
        val near = RecordingParticleSink()
        manager.beginTick(); manager.dispatchAll(ParticleViewer(Vec.ZERO), frame.spawns, near)
        assertEquals(180, near.spawns.sumOf { it.count })
        val far = RecordingParticleSink()
        manager.beginTick(); manager.dispatchAll(ParticleViewer(Vec(100.0, 0.0, 0.0)), frame.spawns, far)
        assertTrue(far.spawns.isEmpty())
    }

    @Test fun `party swords share per viewer and per instance budgets each world tick`() {
        val budget = GreatswordSceneBudget()
        val viewer = UUID.randomUUID()
        assertEquals(180, budget.accept(1, viewer, 180))
        assertEquals(60, budget.accept(1, viewer, 180))
        assertEquals(0, budget.accept(1, viewer, 1))
        repeat(9) { assertEquals(240, budget.accept(1, UUID.randomUUID(), 240)) }
        assertEquals(0, budget.accept(1, UUID.randomUUID(), 1))
        assertEquals(180, budget.accept(2, viewer, 180))
    }
}
