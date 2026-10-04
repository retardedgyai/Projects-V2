package dev.projects.server.coreloop

import net.minestom.server.coordinate.Pos
import kotlin.test.*

class CoreHuntingTrapTest {
    @Test fun `late entry starts four spaced waves only once`() {
        val trap = CoreHuntingTrap(Pos.ZERO, 3.5, 6, 120)
        for (tick in 6L..45) assertFalse(trap.tick(tick, false))
        val releases = (46L..100).filter { trap.tick(it, true) }
        assertEquals(listOf(46L, 54L, 62L, 70L), releases)
        assertTrue(trap.finished)
    }
    @Test fun `empty trap expires without damage and entry at deadline is too late`() {
        val trap = CoreHuntingTrap(Pos.ZERO, 3.5, 6, 120)
        assertFalse(trap.tick(125, false)); assertFalse(trap.expired(125))
        assertFalse(trap.tick(126, true)); assertTrue(trap.expired(126)); assertEquals(0, trap.pulses)
    }
    @Test fun `triggered trap completes near deadline without waiting another six seconds`() {
        val trap = CoreHuntingTrap(Pos.ZERO, 3.5, 6, 120)
        assertTrue(trap.tick(125, true))
        assertEquals(listOf(133L,141L,149L), (126L..170).filter { trap.tick(it, false) })
        assertTrue(trap.finished)
    }
    @Test fun `leaving and reentering never resets the release clock`() {
        val trap = CoreHuntingTrap(Pos.ZERO, 3.5, 0, 120)
        assertTrue(trap.tick(0, true)); assertFalse(trap.tick(0, true))
        assertTrue(trap.tick(8, false)); assertTrue(trap.tick(16, true)); assertTrue(trap.tick(24, true))
        assertFalse(trap.tick(32, true)); assertEquals(4, trap.pulses)
    }
    @Test fun `footprint has finite horizontal and vertical bounds`() {
        val trap = CoreHuntingTrap(Pos(10.0,40.0,10.0), 3.5, 0, 120)
        assertTrue(trap.contains(Pos(13.5,40.0,10.0)))
        assertFalse(trap.contains(Pos(13.501,40.0,10.0)))
        assertFalse(trap.contains(Pos(12.5,40.0,12.5)))
        assertFalse(trap.contains(Pos(10.0,41.501,10.0)))
    }
    @Test fun `delayed update and repeated tick cannot compress waves into one burst`() {
        val trap = CoreHuntingTrap(Pos.ZERO, 3.5, 0, 120)
        assertTrue(trap.tick(0, true)); assertTrue(trap.tick(40, true))
        repeat(10) { assertFalse(trap.tick(40, true)) }
        assertFalse(trap.tick(47, true)); assertTrue(trap.tick(48, true))
        assertTrue(trap.tick(56, true)); assertEquals(4,trap.pulses)
    }
}
