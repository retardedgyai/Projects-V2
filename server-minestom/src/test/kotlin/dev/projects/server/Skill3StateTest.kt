package dev.projects.server

import dev.projects.protocol.AirJumpInput
import net.minestom.server.collision.BoundingBox
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Skill3StateTest {
    private val facing = Vec(0.0, 0.0, 1.0)

    @Test
    fun `skill3 starts ready and fixes direction for four dash ticks`() {
        val skill3 = Skill3State(sequence())

        assertTrue(skill3.isReady)
        assertNotNull(skill3.tryCast(facing, ClassSkillDirection(0.0, 1.0)))
        assertEquals(Skill3Phase.DASH, skill3.phase)
        repeat(4) { tick ->
            val result = skill3.tick(false, -2.0)
            assertTrue(result.dashActive)
            assertEquals(0.0, result.velocityY)
            assertEquals(Vec(0.0, 0.0, 1.0), result.dashDirection)
            assertEquals(if (tick == 3) 0 else 4 - tick - 1, skill3.dashTicksRemaining)
        }
        assertEquals(Skill3Phase.HOVER, skill3.phase)
        assertEquals(80, skill3.cooldownTicksRemaining)
    }

    @Test
    fun `dash preserves positive vertical velocity`() {
        val skill3 = Skill3State(sequence())
        skill3.tryCast(facing, ClassSkillDirection(0.0, 1.0))

        assertEquals(2.5, skill3.tick(false, 2.5).velocityY)
    }

    @Test
    fun `direction uses facing relative diagonal and falls back to forward`() {
        val diagonal = Skill3State(sequence())
        diagonal.tryCast(Vec(1.0, 0.0, 0.0), ClassSkillDirection(1.0, 1.0))
        val direction = diagonal.dashDirection!!
        assertEquals(0.7071067811865475, direction.x(), 0.000001)
        assertEquals(0.7071067811865475, direction.z(), 0.000001)

        val noInput = Skill3State(sequence())
        noInput.tryCast(Vec(0.0, 0.0, 0.0), ClassSkillDirection(0.0, 0.0))
        assertEquals(Vec(0.0, 0.0, 1.0), noInput.dashDirection)
    }

    @Test
    fun `normal attacks reduce cooldown once per execution and skill3 hit does not`() {
        val skill3 = Skill3State(sequence())
        skill3.tryCast(facing, ClassSkillDirection(0.0, 1.0))
        repeat(4) { skill3.tick(false, 0.0) }
        assertTrue(skill3.reduceCooldownForNormalAttack(10L))
        assertFalse(skill3.reduceCooldownForNormalAttack(10L))
        assertEquals(60, skill3.cooldownTicksRemaining)
        assertTrue(skill3.reduceCooldownForNormalAttack(11L))
        assertTrue(skill3.reduceCooldownForNormalAttack(12L))
        assertEquals(20, skill3.cooldownTicksRemaining)
        repeat(20) { skill3.tick(false, -1.0) }
        assertEquals(0, skill3.cooldownTicksRemaining)
        assertTrue(skill3.isReady)
        assertFalse(skill3.reduceCooldownForNormalAttack(13L))
    }

    @Test
    fun `dash segment hits each target once and can hit multiple targets`() {
        val skill3 = Skill3State(sequence())
        skill3.tryCast(facing, ClassSkillDirection(0.0, 1.0))
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val targets = listOf(
            CombatTarget(first, Pos(0.0, 0.0, 0.9)),
            CombatTarget(second, Pos(0.8, 0.0, 1.8)),
            CombatTarget(UUID.randomUUID(), Pos(4.0, 0.0, 0.0)),
        )

        assertEquals(listOf(first, second), skill3.hitTargetsOnSegment(Pos(0.0, 0.0, 0.0), Pos(0.0, 0.0, 2.0), targets))
        assertTrue(skill3.hitTargetsOnSegment(Pos(0.0, 0.0, 2.0), Pos(0.0, 0.0, 4.0), targets).isEmpty())
    }

    @Test
    fun `hover clamps falling velocity, preserves ascent, and ends on ground`() {
        val skill3 = Skill3State(sequence())
        skill3.tryCast(facing, ClassSkillDirection(0.0, 1.0))
        repeat(4) { skill3.tick(false, 0.0) }

        assertEquals(-0.4, skill3.tick(false, -2.0).velocityY)
        assertEquals(2.0, skill3.tick(false, 2.0).velocityY)
        assertEquals(Skill3Phase.IDLE, run {
            skill3.tick(true, -1.0)
            skill3.phase
        })
        skill3.reset()
        assertTrue(skill3.isReady)
    }

    @Test
    fun `hover preserves Twin Rods air jump and air dodge horizontal velocity`() {
        val airJump = airJumpVelocity(
            Vec(0.3, -0.2, -0.4),
            facing,
            AirJumpInput(0.0, 0.0),
        )
        val airDodge = dodgeVelocity(Vec(0.15, 0.0, -0.2), 0.0)
        val airJumpAfterHover = skill3HoverVelocity(airJump, -0.4)
        val airDodgeAfterHover = skill3HoverVelocity(airDodge, -0.4)

        assertEquals(0.3, airJumpAfterHover.x())
        assertEquals(-0.4, airJumpAfterHover.z())
        assertEquals(3.0, airDodgeAfterHover.x())
        assertEquals(-4.0, airDodgeAfterHover.z())
    }

    @Test
    fun `body bounding box catches an aerial segment above the boss origin`() {
        val skill3 = Skill3State(sequence())
        skill3.tryCast(facing, ClassSkillDirection(0.0, 1.0))
        val targetId = UUID.randomUUID()
        val target = combatTargetFromBoundingBox(
            targetId,
            Pos(0.0, 0.0, 2.0),
            BoundingBox(Vec(-0.7, 0.0, -0.7), Vec(0.7, 2.0, 0.7)),
        )
        assertEquals(1.0, target.position.y())
        assertEquals(1.0, target.halfExtent.y())

        assertEquals(
            listOf(targetId),
            skill3.hitTargetsOnSegment(Pos(0.0, 1.75, 0.0), Pos(0.0, 1.75, 4.0), listOf(target)),
        )
        assertTrue(
            skill3.hitTargetsOnSegment(Pos(0.0, 1.75, 4.0), Pos(0.0, 1.75, 6.0), listOf(target)).isEmpty(),
        )
    }

    @Test
    fun `natural cooldown synchronization waits for four ticks`() {
        assertFalse(shouldSyncSkill3Cooldown(1, 79, 80))
        assertFalse(shouldSyncSkill3Cooldown(3, 77, 80))
        assertTrue(shouldSyncSkill3Cooldown(4, 76, 80))
        assertFalse(shouldSyncSkill3Cooldown(4, 80, 80))
    }

    private fun sequence(): () -> Long {
        var id = 0L
        return { ++id }
    }
}
