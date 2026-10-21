package dev.projects.server.coreloop

import dev.projects.server.mob.CrowdControl
import net.minestom.server.coordinate.Pos
import kotlin.test.*

/** The three player CC rules: half from players, diminishing returns, dodge escape. */
class CorePlayerCrowdControlTest {
    @Test fun `players get half the duration from another player`() {
        assertEquals(20, CorePlayerCrowdControl().apply(CrowdControl.STUN, 1000, 0, pvp = false, from = null))
        assertEquals(10, CorePlayerCrowdControl().apply(CrowdControl.STUN, 1000, 0, pvp = true, from = null))
        assertEquals(60, CorePlayerCrowdControl().apply(CrowdControl.SILENCE, 9000, 0, pvp = false, from = null), "capped at 3 s")
    }

    @Test fun `CC resistance shortens every kind but never makes a player immune`() {
        assertEquals(10, CorePlayerCrowdControl().apply(CrowdControl.STUN, 1000, 0, false, null, resist = 50.0))
        assertEquals(10, CorePlayerCrowdControl().apply(CrowdControl.SILENCE, 1000, 0, false, null, resist = 50.0))
        assertEquals(8, CorePlayerCrowdControl().apply(CrowdControl.STUN, 1000, 0, false, null, resist = 500.0), "capped at 60%")
        assertEquals(4, CorePlayerCrowdControl().apply(CrowdControl.STUN, 1000, 0, true, null, resist = 60.0), "stacks with the PvP share")
    }

    @Test fun `movement locks diminish within the window and reset after it`() {
        val cc = CorePlayerCrowdControl()
        assertEquals(20, cc.apply(CrowdControl.STUN, 1000, 0, false, null))
        assertEquals(10, cc.apply(CrowdControl.ROOT, 1000, 40, false, null), "root shares the category")
        assertEquals(5, cc.apply(CrowdControl.AIRBORNE, 1000, 80, false, null))
        assertEquals(0, cc.apply(CrowdControl.STUN, 1000, 120, false, null), "immune on the fourth")
        assertFalse(cc.active(CrowdControl.STUN, 121))
        val later = 120 + CorePlayerCrowdControl.DR_WINDOW_TICKS + 1
        assertEquals(20, cc.apply(CrowdControl.STUN, 1000, later, false, null), "window passed")
        assertEquals(20, cc.apply(CrowdControl.SILENCE, 1000, later, false, null), "silence is not diminished")
    }

    @Test fun `what each CC blocks`() {
        val cc = CorePlayerCrowdControl()
        cc.apply(CrowdControl.ROOT, 1000, 0, false, null)
        assertFalse(cc.canMove(1)); assertTrue(cc.canAttack(1)); assertTrue(cc.canCast(1))
        cc.clear(); cc.apply(CrowdControl.SILENCE, 1000, 0, false, null)
        assertTrue(cc.canMove(1)); assertTrue(cc.canAttack(1)); assertFalse(cc.canCast(1))
        cc.clear(); cc.apply(CrowdControl.STUN, 1000, 0, false, null)
        assertFalse(cc.canMove(1)); assertFalse(cc.canAttack(1)); assertFalse(cc.canCast(1))
        assertEquals("スタン 0.9秒", cc.cue(2))
        cc.clear(); cc.apply(CrowdControl.FEAR, 1000, 0, false, Pos(1.0, 0.0, 0.0))
        assertTrue(cc.forced(1)); assertFalse(cc.canAttack(1)); assertEquals(Pos(1.0, 0.0, 0.0), cc.source)
        assertEquals(0, CorePlayerCrowdControl().apply(CrowdControl.FLINCH, 1000, 0, false, null), "flinch only cancels the windup")
    }

    @Test fun `the dodge breaks movement locks but not silence`() {
        val cc = CorePlayerCrowdControl()
        cc.apply(CrowdControl.STUN, 1000, 0, false, null); cc.apply(CrowdControl.SILENCE, 1000, 0, false, null)
        assertTrue(cc.escape(5))
        assertTrue(cc.canMove(6)); assertTrue(cc.canAttack(6)); assertFalse(cc.canCast(6))
        assertFalse(cc.escape(6), "nothing left to break")
    }
}
