package dev.projects.server

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrototypeBossStateTest {
    private val playerId = UUID.randomUUID()

    @Test
    fun `default boss health is ten thousand`() {
        val boss = PrototypeBossState()

        assertEquals(10000, boss.maxHealth)
        assertEquals(10000, boss.currentHealth)
    }

    @Test
    fun `body damage uses weapon values`() {
        val boss = PrototypeBossState()

        assertEquals(20, boss.applyPlayerAttack(1L, WeaponType.HEAVY_BLADE))
        assertEquals(10, boss.applyPlayerAttack(2L, WeaponType.TWIN_RODS))
        assertEquals(9970, boss.currentHealth)
    }

    @Test
    fun `head and back weakpoints both multiply body damage`() {
        val boss = PrototypeBossState()

        assertEquals(30, boss.applyPlayerAttack(1L, WeaponType.HEAVY_BLADE, FixedWeakpoint.HEAD))
        assertEquals(15, boss.applyPlayerAttack(2L, WeaponType.TWIN_RODS, FixedWeakpoint.BACK))
        assertEquals(9955, boss.currentHealth)
    }

    @Test
    fun `same player attack execution damages boss once and clamps victory`() {
        val boss = PrototypeBossState(maxHealth = 440)

        assertEquals(20, boss.applyPlayerAttack(1L, WeaponType.HEAVY_BLADE))
        assertEquals(0, boss.applyPlayerAttack(1L, WeaponType.HEAVY_BLADE))
        repeat(14) { executionId ->
            boss.applyPlayerAttack(executionId + 2L, WeaponType.HEAVY_BLADE, FixedWeakpoint.HEAD)
        }

        assertEquals(0, boss.currentHealth)
        assertTrue(boss.isVictory)
        assertTrue(boss.isDefeated)
        assertEquals(0, boss.applyPlayerAttack(99L, WeaponType.TWIN_RODS))
    }

    @Test
    fun `skill3 deals thirty damage once per cast and target`() {
        val boss = PrototypeBossState()
        val targetId = UUID.randomUUID()

        assertEquals(30, boss.applySkill3Attack(1L, targetId))
        assertEquals(0, boss.applySkill3Attack(1L, targetId))
        assertEquals(9970, boss.currentHealth)
    }

    @Test
    fun `skill1 and skill2 damage are server-owned and reset`() {
        val boss = PrototypeBossState()
        val targetId = UUID.randomUUID()

        assertEquals(20, boss.applySkill1Attack(1L, targetId))
        assertEquals(0, boss.applySkill1Attack(1L, targetId))
        assertEquals(25, boss.applySkill2Attack(2L, targetId))
        assertEquals(9955, boss.currentHealth)

        boss.reset()

        assertEquals(20, boss.applySkill1Attack(1L, targetId))
        assertEquals(9980, boss.currentHealth)
    }

    @Test
    fun `boss attack damages player once per execution with attack values`() {
        val boss = PrototypeBossState()

        assertEquals(6, boss.applyBossAttack(playerId, 1L, FixedAttackType.SIDE_SWEEP))
        assertEquals(0, boss.applyBossAttack(playerId, 1L, FixedAttackType.SIDE_SWEEP))
        assertEquals(8, boss.applyBossAttack(playerId, 2L, FixedAttackType.FORWARD_SLAM))
        assertEquals(6, boss.playerHealth(playerId))
    }

    @Test
    fun `defeat keeps entity health above zero while logical health reaches zero`() {
        val boss = PrototypeBossState()

        boss.applyBossAttack(playerId, 1L, FixedAttackType.SIDE_SWEEP)
        boss.applyBossAttack(playerId, 2L, FixedAttackType.FORWARD_SLAM)
        boss.applyBossAttack(playerId, 3L, FixedAttackType.SIDE_SWEEP)

        assertEquals(0, boss.playerHealth(playerId))
        assertEquals(1.0f, boss.playerEntityHealth(playerId))
        assertTrue(boss.isDefeat)
    }

    @Test
    fun `player defeat stops attacks and reset restores encounter`() {
        val boss = PrototypeBossState()

        boss.applyBossAttack(playerId, 1L, FixedAttackType.SIDE_SWEEP)
        boss.applyBossAttack(playerId, 2L, FixedAttackType.FORWARD_SLAM)
        boss.applyBossAttack(playerId, 3L, FixedAttackType.SIDE_SWEEP)

        assertEquals(0, boss.playerHealth(playerId))
        assertTrue(boss.isDefeat)
        assertFalse(boss.isActive)
        assertEquals(0, boss.applyBossAttack(playerId, 4L, FixedAttackType.FORWARD_SLAM))

        boss.reset()

        assertTrue(boss.isActive)
        assertEquals(boss.maxHealth, boss.currentHealth)
        assertEquals(boss.playerMaxHealth, boss.playerHealth(playerId))
    }
}
