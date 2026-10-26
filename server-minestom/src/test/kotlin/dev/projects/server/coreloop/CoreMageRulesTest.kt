package dev.projects.server.coreloop

import net.minestom.server.coordinate.Pos
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreMageRulesTest {
    @Test fun `the circle keeps the last three marks and names the most common one`() {
        val s = CoreMageSigils()
        assertNull(s.dominant())
        s.carve(MageElement.FIRE); assertEquals(MageElement.FIRE, s.dominant())
        s.carve(MageElement.ICE); assertEquals(MageElement.ICE, s.dominant(), "a tie goes to the latest")
        s.carve(MageElement.ICE); assertEquals(MageElement.ICE, s.dominant())
        s.carve(MageElement.LIGHTNING)
        assertEquals(listOf(MageElement.ICE, MageElement.ICE, MageElement.LIGHTNING), s.list)
        s.carve(MageElement.WIND)
        assertTrue(s.allDifferent); assertNull(s.dominant())
        assertEquals(3, s.spend().size); assertEquals(0, s.size)
    }

    @Test fun `fire shatters what ice froze, once`() {
        val f = CoreMageField(); val id = UUID.randomUUID(); val at = Pos(0.0, 0.0, 0.0)
        assertTrue(f.land(MageElement.ICE, id, at, boss = false, now = 0).isEmpty())
        assertTrue(f.isFrozen(id, 10))
        assertEquals(setOf(MageReaction.SHATTER), f.land(MageElement.FIRE, id, at, false, 10))
        assertFalse(f.isFrozen(id, 11))
        assertTrue(f.land(MageElement.FIRE, id, at, false, 12).isEmpty())
        assertTrue(f.isBurning(id, 13))
    }

    @Test fun `a boss is only chilled but still shatters, for less`() {
        val f = CoreMageField(); val id = UUID.randomUUID(); val at = Pos(0.0, 0.0, 0.0)
        f.land(MageElement.ICE, id, at, boss = true, now = 0)
        assertTrue(f.isFrozen(id, CoreMageField.FREEZE_TICKS + 1L), "a chill outlasts a freeze")
        assertEquals(setOf(MageReaction.SHATTER), f.land(MageElement.FIRE, id, at, true, 5))
        assertTrue(f.shatterMultiplier(true) < f.shatterMultiplier(false))
    }

    @Test fun `lightning conducts on ice and overloads on fire`() {
        val f = CoreMageField(); val a = UUID.randomUUID(); val b = UUID.randomUUID()
        f.lay(MageElement.ICE, Pos(0.0, 0.0, 0.0), 3.0, now = 0, ticks = 100)
        f.lay(MageElement.FIRE, Pos(10.0, 0.0, 0.0), 3.0, now = 0, ticks = 100)
        assertEquals(setOf(MageReaction.CONDUCT), f.land(MageElement.LIGHTNING, a, Pos(1.0, 0.0, 1.0), false, 5))
        assertEquals(setOf(MageReaction.OVERLOAD), f.land(MageElement.LIGHTNING, b, Pos(9.0, 0.0, 0.0), false, 5))
        f.land(MageElement.FIRE, a, Pos(30.0, 0.0, 0.0), false, 6)
        assertEquals(setOf(MageReaction.OVERLOAD), f.land(MageElement.LIGHTNING, a, Pos(30.0, 0.0, 0.0), false, 7))
        f.tick(101); assertNull(f.floorUnder(Pos(0.0, 0.0, 0.0), MageElement.ICE))
    }

    @Test fun `wind carries off the floor it crosses`() {
        val f = CoreMageField()
        f.lay(MageElement.FIRE, Pos(4.0, 0.0, 0.0), 2.0, now = 0, ticks = 100)
        f.lay(MageElement.ICE, Pos(4.0, 0.0, 1.0), 1.0, now = 0, ticks = 100)
        assertNull(f.carry(Pos(-10.0, 0.0, 0.0), 2.0))
        assertEquals(MageElement.FIRE, f.carry(Pos(2.0, 0.0, 0.0), 1.0))
        assertEquals(listOf(MageElement.ICE), f.floorList.map { it.element }, "the ice it did not reach stays")
    }
}
