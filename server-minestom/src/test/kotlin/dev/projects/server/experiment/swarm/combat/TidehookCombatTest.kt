package dev.projects.server.experiment.swarm.combat

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TidehookCombatTest {
    private val playerId = UUID.randomUUID()
    private val targetId = UUID.randomUUID()
    private val eye = CombatPoint(0.0, 1.6, 0.0)
    private val target = CombatBounds(CombatPoint(2.5, 0.0, -0.5), CombatPoint(3.5, 2.0, 0.5))

    @Test
    fun `valid thrust resolves once then respects recovery`() {
        val combat = TidehookCombat()

        assertTrue(combat.requestThrust(thrust(actionId = 1, tick = 0)).accepted)
        assertEquals(TidehookRejectReason.DUPLICATE_ACTION, combat.requestThrust(thrust(actionId = 1, tick = 0)).reason)
        assertEquals(TidehookRejectReason.ACTION_RECOVERY, combat.requestThrust(thrust(actionId = 2, tick = 1)).reason)
        assertTrue(combat.requestThrust(thrust(actionId = 3, tick = 8)).accepted)
    }

    @Test
    fun `thrust validates range facing and sampled line of sight`() {
        val rangeCombat = TidehookCombat()
        assertEquals(
            TidehookRejectReason.OUT_OF_RANGE,
            rangeCombat.requestThrust(thrust(actionId = 1, eyeOverride = CombatPoint(-2.0, 1.6, 0.0))).reason,
        )

        val facingCombat = TidehookCombat()
        assertEquals(
            TidehookRejectReason.NOT_FACING,
            facingCombat.requestThrust(thrust(actionId = 1, look = CombatPoint(-1.0, 0.0, 0.0))).reason,
        )

        val blockedCombat = TidehookCombat()
        val wall = CombatBounds(CombatPoint(1.0, 0.0, -2.0), CombatPoint(2.0, 3.0, 2.0))
        assertEquals(
            TidehookRejectReason.LINE_OF_SIGHT_BLOCKED,
            blockedCombat.requestThrust(thrust(actionId = 1, blockers = listOf(wall))).reason,
        )
    }

    @Test
    fun `brace has windup active window and recovery while offhand is ignored`() {
        val combat = TidehookCombat()
        val offhand = combat.requestBrace(brace(actionId = 1, tick = 0, hand = TidehookHand.OFF))
        assertFalse(offhand.accepted)
        assertEquals(TidehookRejectReason.OFFHAND_IGNORED, offhand.reason)

        val result = combat.requestBrace(brace(actionId = 1, tick = 0))
        assertTrue(result.accepted)
        assertEquals(3, result.activeFromTick)
        assertEquals(15, result.activeUntilTickExclusive)
        assertEquals(TidehookActionState.BRACE_WINDUP, combat.actionState(playerId, 2))
        assertEquals(TidehookActionState.BRACE_ACTIVE, combat.actionState(playerId, 3))

        val mitigation = combat.resolveIncomingCharge(playerId, 4)
        assertTrue(mitigation.braced)
        assertEquals(0.35, mitigation.damageMultiplier)
        assertEquals(0.25, mitigation.knockbackMultiplier)
        assertEquals(TidehookActionState.BRACE_RECOVERY, combat.actionState(playerId, 4))
        assertFalse(combat.resolveIncomingCharge(playerId, 5).braced)
    }

    @Test
    fun `mods change exposed thrust and brace tradeoffs only`() {
        val barbed = TidehookCombat()
        assertEquals(
            1.20,
            barbed.requestThrust(thrust(actionId = 1, mod = TidehookMod.BARBED_POINT, exposed = true)).damageMultiplier,
        )
        val barbedBrace = TidehookCombat().requestBrace(brace(1, 0, mod = TidehookMod.BARBED_POINT))
        val guardBrace = TidehookCombat().requestBrace(brace(1, 0, mod = TidehookMod.GUARD_RING))
        assertEquals(10, barbedBrace.activeUntilTickExclusive!! - barbedBrace.activeFromTick!!)
        assertEquals(15, guardBrace.activeUntilTickExclusive!! - guardBrace.activeFromTick!!)

        val guardThrust = TidehookCombat().requestThrust(thrust(actionId = 1, mod = TidehookMod.GUARD_RING))
        assertEquals(0.90, guardThrust.damageMultiplier)
    }

    private fun thrust(
        actionId: Long,
        tick: Long = 0,
        eyeOverride: CombatPoint = eye,
        look: CombatPoint = CombatPoint(1.0, 0.0, 0.0),
        blockers: Collection<CombatBounds> = emptyList(),
        mod: TidehookMod = TidehookMod.NONE,
        exposed: Boolean = false,
    ) = TidehookThrustIntent(
        playerId = playerId,
        targetId = targetId,
        actionId = actionId,
        tick = tick,
        eye = eyeOverride,
        lookDirection = look,
        targetBounds = target,
        blockers = blockers,
        correctItem = true,
        correctZone = true,
        registeredTarget = true,
        alive = true,
        targetExposed = exposed,
        mod = mod,
    )

    private fun brace(
        actionId: Long,
        tick: Long,
        hand: TidehookHand = TidehookHand.MAIN,
        mod: TidehookMod = TidehookMod.NONE,
    ) = TidehookBraceIntent(
        playerId = playerId,
        actionId = actionId,
        tick = tick,
        hand = hand,
        correctItem = true,
        correctZone = true,
        alive = true,
        mod = mod,
    )
}
