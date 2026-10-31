package dev.projects.server.coreloop

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
        val f = CoreMageField(); val id = UUID.randomUUID()
        assertTrue(f.land(MageElement.ICE, id, boss = false, now = 0).isEmpty())
        assertTrue(f.isFrozen(id, 10))
        assertEquals(setOf(MageReaction.SHATTER), f.land(MageElement.FIRE, id, false, 10))
        assertFalse(f.isFrozen(id, 11))
        assertTrue(f.land(MageElement.FIRE, id, false, 12).isEmpty())
        assertTrue(f.isBurning(id, 13))
        assertFalse(f.isBurning(id, 12L + CoreMageField.BURN_TICKS), "burning lasts 4 s")
    }

    @Test fun `a boss is only chilled but still shatters, for less`() {
        val f = CoreMageField(); val id = UUID.randomUUID()
        f.land(MageElement.ICE, id, boss = true, now = 0)
        assertTrue(f.isFrozen(id, CoreMageField.FREEZE_TICKS + 1L), "a chill outlasts a freeze")
        assertEquals(setOf(MageReaction.SHATTER), f.land(MageElement.FIRE, id, true, 5))
        assertTrue(f.shatterMultiplier(true) < f.shatterMultiplier(false))
    }

    @Test fun `lightning conducts on a frozen enemy and overloads on a burning one`() {
        val f = CoreMageField(); val a = UUID.randomUUID(); val b = UUID.randomUUID(); val c = UUID.randomUUID()
        f.land(MageElement.ICE, a, false, 0)
        f.land(MageElement.FIRE, b, false, 0)
        assertEquals(setOf(MageReaction.CONDUCT), f.land(MageElement.LIGHTNING, a, false, 5))
        assertEquals(setOf(MageReaction.OVERLOAD), f.land(MageElement.LIGHTNING, b, false, 5))
        assertTrue(f.land(MageElement.LIGHTNING, c, false, 5).isEmpty(), "nothing is left on the ground to mix with")
        f.tick(CoreMageField.BURN_TICKS + 1L); assertTrue(f.burningIds(CoreMageField.BURN_TICKS + 1L).isEmpty())
    }

    @Test fun `wind picks up the state of the enemies it blows over`() {
        val f = CoreMageField(); val burning = UUID.randomUUID(); val frozen1 = UUID.randomUUID(); val frozen2 = UUID.randomUUID(); val plain = UUID.randomUUID()
        f.land(MageElement.FIRE, burning, false, 0); f.land(MageElement.ICE, frozen1, false, 0); f.land(MageElement.ICE, frozen2, false, 0)
        assertNull(f.carry(listOf(plain), 5))
        assertEquals(MageElement.FIRE, f.carry(listOf(burning, plain), 5))
        assertEquals(MageElement.ICE, f.carry(listOf(burning, frozen1, frozen2), 5), "the state on the most of them")
        assertEquals(MageElement.FIRE, f.carry(listOf(burning, frozen1), 5), "a tie goes to fire")
        assertTrue(f.isBurning(burning, 6) && f.isFrozen(frozen1, 6), "the states stay")
    }
}
