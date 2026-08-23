package dev.projects.server

import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class StarweaverStateTest {
    @Test
    fun `initial rotation has exactly two of every celestial`() {
        val snapshot = StarweaverRotationState(Random(1)).snapshot()
        val allMarks = snapshot.queue + snapshot.stored

        assertEquals(5, snapshot.queue.size)
        StarweaverCelestial.entries.forEach { celestial ->
            assertEquals(2, allMarks.count { it == celestial })
        }
        assertNotEquals(snapshot.stored, snapshot.current)
    }

    @Test
    fun `seeded cycle generation keeps current different from stored`() {
        for (seed in 0..128) {
            val state = StarweaverRotationState(Random(seed))
            val initial = state.snapshot()
            assertNotEquals(initial.stored, initial.current, "initial cycle seed=$seed")

            state.setRotationForTest(
                listOf(
                    StarweaverCelestial.SUN,
                    StarweaverCelestial.MOON,
                    StarweaverCelestial.STAR,
                    StarweaverCelestial.SUN,
                    StarweaverCelestial.MOON,
                ),
                StarweaverCelestial.STAR,
            )
            state.tryCast(StarweaverSlot.Q)
            state.tryCast(StarweaverSlot.W)
            state.tryCast(StarweaverSlot.E)
            repeat(StarweaverBalance.BASE_COOLDOWN_TICKS) { state.tick() }
            state.tryCast(StarweaverSlot.Q)
            state.tryCast(StarweaverSlot.W)
            repeat(StarweaverBalance.RELOAD_TICKS) { state.tick() }

            val reloaded = state.snapshot()
            assertNotEquals(reloaded.stored, reloaded.current, "reload cycle seed=$seed")
            assertEquals(5, reloaded.queue.size)
            StarweaverCelestial.entries.forEach { celestial ->
                assertEquals(2, (reloaded.queue + reloaded.stored).count { it == celestial })
            }
        }
    }

    @Test
    fun `normal cast consumes one mark and cooldowns stay independent`() {
        val state = StarweaverRotationState(Random(2))
        state.setRotationForTest(
            listOf(
                StarweaverCelestial.SUN,
                StarweaverCelestial.MOON,
                StarweaverCelestial.STAR,
                StarweaverCelestial.SUN,
                StarweaverCelestial.MOON,
            ),
            StarweaverCelestial.STAR,
        )

        assertEquals(StarweaverCastKind.BASE, state.tryCast(StarweaverSlot.Q)?.kind)
        assertEquals(4, state.snapshot().queue.size)
        assertEquals(StarweaverCelestial.MOON, state.current)
        assertEquals(StarweaverBalance.BASE_COOLDOWN_TICKS, state.cooldownRemaining(StarweaverSlot.Q))
        assertNotNull(state.tryCast(StarweaverSlot.W))
        assertEquals(StarweaverBalance.BASE_COOLDOWN_TICKS, state.cooldownRemaining(StarweaverSlot.W))
        assertEquals(StarweaverBalance.BASE_COOLDOWN_TICKS, state.cooldownRemaining(StarweaverSlot.Q))
    }

    @Test
    fun `R swaps current and stored without consuming a mark`() {
        val state = StarweaverRotationState(Random(3))
        state.setRotationForTest(
            listOf(
                StarweaverCelestial.SUN,
                StarweaverCelestial.SUN,
                StarweaverCelestial.MOON,
                StarweaverCelestial.STAR,
                StarweaverCelestial.MOON,
            ),
            StarweaverCelestial.STAR,
        )

        assertTrue(state.trySwap())
        assertEquals(5, state.snapshot().queue.size)
        assertEquals(StarweaverCelestial.STAR, state.current)
        assertEquals(StarweaverCelestial.SUN, state.snapshot().stored)
        assertEquals(2, (state.snapshot().queue + state.snapshot().stored).count { it == StarweaverCelestial.SUN })
    }

    @Test
    fun `conjunction ignores base cooldown and does not change it`() {
        val state = StarweaverRotationState(Random(4))
        state.setRotationForTest(
            listOf(
                StarweaverCelestial.SUN,
                StarweaverCelestial.SUN,
                StarweaverCelestial.MOON,
                StarweaverCelestial.STAR,
                StarweaverCelestial.MOON,
            ),
            StarweaverCelestial.STAR,
        )
        state.setCooldownForTest(StarweaverSlot.Q, 37)

        val cast = state.tryCast(StarweaverSlot.Q)
        assertEquals(StarweaverCastKind.CONJUNCTION, cast?.kind)
        assertEquals(37, state.cooldownRemaining(StarweaverSlot.Q))
        assertEquals(3, state.snapshot().queue.size)
    }

    @Test
    fun `R or another normal slot can break a conjunction`() {
        val state = StarweaverRotationState(Random(5))
        state.setRotationForTest(
            listOf(
                StarweaverCelestial.SUN,
                StarweaverCelestial.SUN,
                StarweaverCelestial.MOON,
                StarweaverCelestial.STAR,
                StarweaverCelestial.MOON,
            ),
            StarweaverCelestial.STAR,
        )
        assertTrue(state.trySwap())
        assertEquals(StarweaverCelestial.STAR, state.current)
        assertFalse(state.conjunctionUsed)

        state.setRotationForTest(
            listOf(
                StarweaverCelestial.SUN,
                StarweaverCelestial.SUN,
                StarweaverCelestial.MOON,
                StarweaverCelestial.STAR,
                StarweaverCelestial.MOON,
            ),
            StarweaverCelestial.STAR,
        )
        val normal = state.tryCast(StarweaverSlot.W)
        assertEquals(StarweaverCastKind.BASE, normal?.kind)
        assertEquals(4, state.snapshot().queue.size)
    }

    @Test
    fun `only one conjunction is available in a rotation`() {
        val state = StarweaverRotationState(Random(6))
        state.setRotationForTest(
            listOf(
                StarweaverCelestial.SUN,
                StarweaverCelestial.SUN,
                StarweaverCelestial.MOON,
                StarweaverCelestial.MOON,
                StarweaverCelestial.STAR,
            ),
            StarweaverCelestial.STAR,
        )

        assertEquals(StarweaverCastKind.CONJUNCTION, state.tryCast(StarweaverSlot.Q)?.kind)
        assertEquals(StarweaverCastKind.BASE, state.tryCast(StarweaverSlot.W)?.kind)
        assertTrue(state.conjunctionUsed)
    }

    @Test
    fun `reload lasts sixty ticks and preserves stored mark`() {
        val state = StarweaverRotationState(Random(7))
        state.setRotationForTest(
            listOf(
                StarweaverCelestial.SUN,
                StarweaverCelestial.MOON,
                StarweaverCelestial.STAR,
                StarweaverCelestial.SUN,
                StarweaverCelestial.MOON,
            ),
            StarweaverCelestial.STAR,
        )
        state.tryCast(StarweaverSlot.Q)
        state.tryCast(StarweaverSlot.W)
        state.tryCast(StarweaverSlot.E)
        repeat(StarweaverBalance.BASE_COOLDOWN_TICKS) { state.tick() }
        state.tryCast(StarweaverSlot.Q)
        state.tryCast(StarweaverSlot.W)

        val storedBeforeReload = state.snapshot().stored
        assertTrue(state.isReloading)
        assertEquals(StarweaverBalance.RELOAD_TICKS, state.reloadTicksRemaining)
        assertEquals(StarweaverBalance.RELOAD_MOVEMENT_SPEED_BONUS, state.movementSpeedBonus, 1.0e-9)
        repeat(StarweaverBalance.RELOAD_TICKS - 1) { state.tick() }
        assertEquals(storedBeforeReload, state.snapshot().stored)
        assertTrue(state.isReloading)
        state.tick()
        assertFalse(state.isReloading)
        assertEquals(5, state.snapshot().queue.size)
        assertEquals(storedBeforeReload, state.snapshot().stored)
        assertNotEquals(storedBeforeReload, state.snapshot().current)
        StarweaverCelestial.entries.forEach { celestial ->
            assertEquals(2, (state.snapshot().queue + state.snapshot().stored).count { it == celestial })
        }
    }

    @Test
    fun `R is unavailable during reload`() {
        val state = StarweaverRotationState(Random(8))
        state.setRotationForTest(
            listOf(
                StarweaverCelestial.SUN,
                StarweaverCelestial.MOON,
                StarweaverCelestial.STAR,
                StarweaverCelestial.SUN,
                StarweaverCelestial.MOON,
            ),
            StarweaverCelestial.STAR,
        )
        state.tryCast(StarweaverSlot.Q)
        state.tryCast(StarweaverSlot.W)
        state.tryCast(StarweaverSlot.E)
        repeat(StarweaverBalance.BASE_COOLDOWN_TICKS) { state.tick() }
        state.tryCast(StarweaverSlot.Q)
        state.tryCast(StarweaverSlot.W)
        assertTrue(state.isReloading)
        assertFalse(state.trySwap())
    }
}
