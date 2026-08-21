package dev.projects.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.minestom.server.coordinate.Vec

class ClassResourceStateTest {
    @Test
    fun `mana starts full and reset restores it`() {
        val resources = ClassResourceState()

        assertEquals(100, resources.mana)
        assertTrue(resources.trySpend(25))
        assertEquals(75, resources.mana)
        resources.reset()
        assertEquals(100, resources.mana)
    }

    @Test
    fun `mana cannot be overspent`() {
        val resources = ClassResourceState()

        assertFalse(resources.trySpend(101))
        assertEquals(100, resources.mana)
        val snapshot = resources.snapshot(0, 0, 0)
        assertEquals(100, snapshot.mana)
        assertEquals(80, snapshot.skill1CooldownMaxTicks)
        assertEquals(100, snapshot.skill2CooldownMaxTicks)
        assertEquals(60, snapshot.skill3CooldownMaxTicks)
    }

    @Test
    fun `snapshot exposes server cooldowns and reset returns all skills to ready`() {
        val resources = ClassResourceState()
        val skill1 = Skill1State()
        val skill2 = Skill2State()
        val skill3 = Skill3State()

        skill1.tryCast(Vec(0.0, 0.0, 1.0))
        skill2.tryCast(false)
        skill2.tick(true)
        skill3.tryCast(Vec(0.0, 0.0, 1.0), ClassSkillDirection(0.0, 0.0))
        repeat(4) { skill3.tick(false, 0.0) }
        skill3.reduceCooldownForNormalAttack(1L)

        val active = resources.snapshot(
            skill1.cooldownTicksRemaining,
            skill2.cooldownTicksRemaining,
            skill3.cooldownTicksRemaining,
        )
        assertEquals(80, active.skill1CooldownTicks)
        assertEquals(100, active.skill2CooldownTicks)
        assertEquals(40, active.skill3CooldownTicks)

        resources.reset()
        skill1.reset()
        skill2.reset()
        skill3.reset()
        val reset = resources.snapshot(
            skill1.cooldownTicksRemaining,
            skill2.cooldownTicksRemaining,
            skill3.cooldownTicksRemaining,
        )
        assertEquals(0, reset.skill1CooldownTicks)
        assertEquals(0, reset.skill2CooldownTicks)
        assertEquals(0, reset.skill3CooldownTicks)
    }
}
