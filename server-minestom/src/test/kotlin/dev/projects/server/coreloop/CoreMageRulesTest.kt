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

    @Test fun `ice builds up and freezes when full, then fire shatters it once`() {
        val f = CoreMageField(); val id = UUID.randomUUID()
        assertTrue(f.land(MageElement.ICE, id, boss = false, now = 0, amount = 60.0).isEmpty())
        assertFalse(f.isFrozen(id, 1)); assertEquals(.6, f.chill(id), 1e-9)
        assertEquals(setOf(MageReaction.FREEZE), f.land(MageElement.ICE, id, false, 5, 50.0))
        assertTrue(f.isFrozen(id, 10)); assertEquals(0.0, f.chill(id), 1e-9, "a freeze empties the ice")
        assertEquals(setOf(MageReaction.SHATTER), f.land(MageElement.FIRE, id, false, 10))
        assertFalse(f.isFrozen(id, 11))
        assertTrue(f.land(MageElement.FIRE, id, false, 12).isEmpty())
        assertTrue(f.isBurning(id, 13))
        assertFalse(f.isBurning(id, 12L + CoreMageField.BURN_TICKS), "burning lasts 4 s")
    }

    @Test fun `the grand chant's ice fills at once, and a boss needs more each time`() {
        val f = CoreMageField(); val boss = UUID.randomUUID()
        assertEquals(setOf(MageReaction.FREEZE), f.land(MageElement.ICE, boss, true, 0, CoreMageField.FILL))
        assertTrue(f.isFrozen(boss, CoreMageField.BOSS_FREEZE_TICKS - 1L)); assertFalse(f.isFrozen(boss, CoreMageField.BOSS_FREEZE_TICKS + 1L))
        val later = CoreMageField.BOSS_FREEZE_TICKS + 2L
        assertTrue(f.land(MageElement.ICE, boss, true, later, CoreMageField.FULL).isEmpty(), "a boss's second freeze needs half as much again")
        assertEquals(setOf(MageReaction.FREEZE), f.land(MageElement.ICE, boss, true, later + 1, CoreMageField.FULL * .5))
        assertTrue(f.shatterMultiplier(true) < f.shatterMultiplier(false))
    }

    @Test fun `built-up ice fades once no ice has come for a while`() {
        val f = CoreMageField(); val id = UUID.randomUUID()
        f.land(MageElement.ICE, id, false, 0, 50.0)
        f.tick(CoreMageField.FADE_AFTER); assertEquals(.5, f.chill(id), 1e-9)
        repeat(100) { f.tick(CoreMageField.FADE_AFTER + 1L + it) }
        assertEquals(0.0, f.chill(id), 1e-9)
    }

    @Test fun `lightning builds a charge that bursts when full`() {
        val f = CoreMageField(); val id = UUID.randomUUID()
        assertTrue(f.land(MageElement.LIGHTNING, id, false, 0, 40.0).isEmpty())
        assertTrue(f.land(MageElement.LIGHTNING, id, false, 1, 40.0).isEmpty())
        assertEquals(setOf(MageReaction.BURST), f.land(MageElement.LIGHTNING, id, false, 2, 40.0))
        assertEquals(0.0, f.charged(id), 1e-9)
    }

    @Test fun `lightning conducts on a frozen enemy and overloads on a burning one`() {
        val f = CoreMageField(); val a = UUID.randomUUID(); val b = UUID.randomUUID(); val c = UUID.randomUUID()
        f.land(MageElement.ICE, a, false, 0, CoreMageField.FULL)
        f.land(MageElement.FIRE, b, false, 0)
        assertEquals(setOf(MageReaction.CONDUCT), f.land(MageElement.LIGHTNING, a, false, 5))
        assertEquals(setOf(MageReaction.OVERLOAD), f.land(MageElement.LIGHTNING, b, false, 5))
        assertTrue(f.land(MageElement.LIGHTNING, c, false, 5).isEmpty(), "nothing is left on the ground to mix with")
        f.tick(CoreMageField.BURN_TICKS + 1L); assertTrue(f.burningIds(CoreMageField.BURN_TICKS + 1L).isEmpty())
    }

    @Test fun `every grand chant is aimed, and each element is its own spell`() {
        val names = MageElement.entries.map { e -> CoreMageRules.grand(List(3) { e }, e) }
        assertTrue(names.all { it.motion == CoreSkillMotion.FIELD })
        assertEquals(4, names.map { it.name }.distinct().size)
        assertTrue(CoreMageRules.grand(listOf(MageElement.FIRE), MageElement.FIRE).formula.ap < names[0].formula.ap, "more marks, stronger")
    }

    @Test fun `wind picks up the state of the enemies it blows over`() {
        val f = CoreMageField(); val burning = UUID.randomUUID(); val frozen1 = UUID.randomUUID(); val frozen2 = UUID.randomUUID(); val plain = UUID.randomUUID()
        f.land(MageElement.FIRE, burning, false, 0); f.land(MageElement.ICE, frozen1, false, 0, CoreMageField.FULL); f.land(MageElement.ICE, frozen2, false, 0, CoreMageField.FULL)
        assertNull(f.carry(listOf(plain), 5))
        assertEquals(MageElement.FIRE, f.carry(listOf(burning, plain), 5))
        assertEquals(MageElement.ICE, f.carry(listOf(burning, frozen1, frozen2), 5), "the state on the most of them")
        assertEquals(MageElement.FIRE, f.carry(listOf(burning, frozen1), 5), "a tie goes to fire")
        assertTrue(f.isBurning(burning, 6) && f.isFrozen(frozen1, 6), "the states stay")
    }
}
