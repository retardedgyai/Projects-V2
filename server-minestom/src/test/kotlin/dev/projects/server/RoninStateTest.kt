package dev.projects.server

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoninStateTest {
    private val origin = Pos(0.0, 0.0, 0.0)
    private val facing = Vec(0.0, 0.0, 1.0)
    private val targetA = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val targetB = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val targetC = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val targetD = UUID.fromString("00000000-0000-0000-0000-000000000004")

    @Test
    fun `starts at zero and W is unavailable until Iaido exists`() {
        val state = RoninState()

        assertEquals(0, state.iaido)
        assertNull(state.tryCast(RoninSkill.W, origin, facing))
    }

    @Test
    fun `Q E and R each add one Iaido only after a real enemy hit`() {
        val state = RoninState()

        val q = requireNotNull(state.tryCast(RoninSkill.Q, origin, facing))
        repeat(RoninBalance.Q_IMPACT_TICK) { state.tick() }
        state.recordEnemyHit()
        complete(state)
        assertEquals(1, state.iaido)

        val e = requireNotNull(state.tryCast(RoninSkill.E, origin, facing))
        repeat(RoninBalance.E_BLINK_TICK) { state.tick() }
        state.recordEnemyHit()
        complete(state)
        assertEquals(2, state.iaido)

        val r = requireNotNull(state.tryCast(RoninSkill.R, origin, facing))
        repeat(RoninBalance.R_IMPACT_TICK) { state.tick() }
        state.recordEnemyHit()
        complete(state)
        assertEquals(3, state.iaido)

        assertEquals(q.castId + 1, e.castId)
        assertEquals(e.castId + 1, r.castId)
        assertEquals(RoninWVariant.TEMPEST, requireNotNull(state.tryCast(RoninSkill.W, origin, facing)).variant)
    }

    @Test
    fun `misses do not add Iaido and multi-target casts still add one`() {
        val state = RoninState()

        state.tryCast(RoninSkill.Q, origin, facing)
        complete(state)
        assertEquals(0, state.iaido)

        state.tryCast(RoninSkill.E, origin, facing)
        state.recordEnemyHit()
        state.recordEnemyHit()
        complete(state)
        assertEquals(1, state.iaido)
    }

    @Test
    fun `Iaido is capped and W consumes it immediately even on a miss`() {
        val state = RoninState()
        repeat(4) {
            requireNotNull(state.tryCast(RoninSkill.Q, origin, facing))
            state.recordEnemyHit()
            complete(state)
            repeat(RoninBalance.Q_COOLDOWN_TICKS) { state.tick() }
        }
        assertEquals(RoninBalance.MAX_IAIDO, state.iaido)

        val w = requireNotNull(state.tryCast(RoninSkill.W, origin, facing))
        assertEquals(RoninWVariant.TEMPEST, w.variant)
        assertEquals(0, state.iaido)
    }

    @Test
    fun `cooldowns are exact and W has no cooldown`() {
        val state = RoninState()

        state.tryCast(RoninSkill.Q, origin, facing)
        state.recordEnemyHit()
        assertEquals(160, state.qCooldownTicksRemaining)
        assertNull(state.tryCast(RoninSkill.Q, origin, facing))
        complete(state)
        repeat(159) { state.tick() }
        assertEquals(0, state.qCooldownTicksRemaining)

        state.tryCast(RoninSkill.E, origin, facing)
        assertEquals(300, state.eCooldownTicksRemaining)
        complete(state)
        state.tryCast(RoninSkill.W, origin, facing)
        assertEquals(0, state.cooldownRemaining(RoninSkill.W))
    }

    @Test
    fun `Wound refreshes, is not consumed by its own application, and caps healing triggers at three`() {
        val state = RoninState()

        state.applyWound(targetA)
        state.applyWound(targetB)
        state.applyWound(targetC)
        state.applyWound(targetD)
        assertEquals(RoninBalance.WOUND_DURATION_TICKS, state.woundRemaining(targetA))
        assertTrue(state.consumeWound(10L, targetA))
        assertTrue(state.consumeWound(10L, targetB))
        assertTrue(state.consumeWound(10L, targetC))
        assertFalse(state.consumeWound(10L, targetD))
        assertFalse(state.consumeWound(10L, targetA))

        state.applyWound(targetA)
        assertTrue(state.woundRemaining(targetA) > 0)
        assertTrue(state.consumeWound(11L, targetA))
    }

    @Test
    fun `W2 delayed target is fixed and W3 has startup invulnerability and capped healing`() {
        val state = RoninState()
        requireNotNull(state.tryCast(RoninSkill.E, origin, facing))
        state.recordEnemyHit()
        complete(state)
        requireNotNull(state.tryCast(RoninSkill.R, origin, facing))
        state.recordEnemyHit()
        complete(state)
        val w2 = requireNotNull(state.tryCast(RoninSkill.W, origin, facing))
        assertEquals(RoninWVariant.CROSSCUT, w2.variant)
        repeat(RoninBalance.W2_INITIAL_IMPACT_TICK) { state.tick() }
        assertTrue(state.lockDelayedTarget(targetA))
        assertFalse(state.lockDelayedTarget(targetB))
        repeat(RoninBalance.W2_DELAYED_IMPACT_TICK - RoninBalance.W2_INITIAL_IMPACT_TICK - 1) { state.tick() }
        val delayed = state.tick().events.single { it.kind == RoninCastEventKind.W_DELAYED }
        assertEquals(targetA, delayed.targetId)
        complete(state)

        val fresh = RoninState()
        repeat(3) {
            requireNotNull(fresh.tryCast(RoninSkill.Q, origin, facing))
            fresh.recordEnemyHit()
            complete(fresh)
            repeat(RoninBalance.Q_COOLDOWN_TICKS) { fresh.tick() }
        }
        val w3 = requireNotNull(fresh.tryCast(RoninSkill.W, origin, facing))
        assertEquals(RoninWVariant.TEMPEST, w3.variant)
        repeat(RoninBalance.W3_INVULNERABLE_START_TICK - 1) { fresh.tick() }
        assertFalse(fresh.isW3Untargetable)
        assertTrue(fresh.tick().events.any { it.kind == RoninCastEventKind.W3_PULSE })
        assertTrue(fresh.isW3Untargetable)
        assertEquals(7.0, fresh.recordW3Healing(35, 20))
        assertEquals(0.0, fresh.recordW3Healing(35, 20))
    }

    @Test
    fun `reset clears lock cooldown Iaido and timed effects`() {
        val state = RoninState()
        state.applyWound(targetA)
        state.applySevered(targetB)
        state.tryCast(RoninSkill.Q, origin, facing)
        state.recordEnemyHit()

        state.reset()

        assertFalse(state.isMovementLocked)
        assertFalse(state.isW3Untargetable)
        assertEquals(0, state.iaido)
        assertEquals(0, state.qCooldownTicksRemaining)
        assertEquals(0, state.woundRemaining(targetA))
        assertEquals(0, state.severedRemaining(targetB))
    }

    @Test
    fun `Ronin AABB helpers cover front volume radial sector and blink sweep`() {
        val target = CombatTarget(
            id = targetA,
            position = Pos(0.0, 1.0, 4.0),
            halfExtent = Vec(0.7, 1.0, 0.7),
        )
        assertTrue(isRoninFrontVolumeHit(origin, facing, target, 5.5, 7.0, 2.5))
        assertTrue(isRoninRadialHit(Pos(0.0, 0.0, 0.0), 5.0, target))
        assertTrue(isRoninSectorHit(origin, facing, target, 7.0, 90.0))
        assertTrue(roninSegmentIntersectsAabb(origin, Pos(0.0, 1.0, 8.0), target))
    }

    private fun complete(state: RoninState) {
        var completed = false
        repeat(40) {
            if (state.tick().completedCast != null) completed = true
            if (completed) return
        }
        assertTrue(completed, "Ronin cast did not complete")
    }
}
