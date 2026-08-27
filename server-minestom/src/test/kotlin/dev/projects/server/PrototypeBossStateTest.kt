package dev.projects.server

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrototypeBossStateTest {
    private val playerId = UUID.randomUUID()

    @Test
    fun `default boss health is three thousand`() {
        val boss = PrototypeBossState()

        assertEquals(3000, boss.maxHealth)
        assertEquals(3000, boss.currentHealth)
    }

    @Test
    fun `body damage uses weapon values`() {
        val boss = PrototypeBossState()

        assertEquals(20, boss.applyPlayerAttack(1L, WeaponType.HEAVY_BLADE))
        assertEquals(10, boss.applyPlayerAttack(2L, WeaponType.TWIN_RODS))
        assertEquals(2970, boss.currentHealth)
    }

    @Test
    fun `progression modifiers affect outgoing and incoming pve damage`() {
        val boss = PrototypeBossState()

        assertEquals(23, boss.applyPlayerAttack(1L, WeaponType.HEAVY_BLADE, outgoingDamageMultiplier = 1.15))
        assertEquals(5, boss.applyBossAttack(playerId, 2L, FixedAttackType.SIDE_SWEEP, incomingDamageMultiplier = 0.90))
        assertEquals(15, boss.playerHealth(playerId))
    }

    @Test
    fun `resolved direct and elemental damage each apply once per attack execution`() {
        val boss = PrototypeBossState(maxHealth = 100)

        assertEquals(10, boss.applyResolvedPlayerAttack(1L, 10.0))
        assertEquals(0, boss.applyResolvedPlayerAttack(1L, 10.0))
        assertEquals(3, boss.applyElementalDamage(1L, "fire-detonation-0", 2.5))
        assertEquals(0, boss.applyElementalDamage(1L, "fire-detonation-0", 2.5))
        assertEquals(0, boss.applyElementalDamage(1L, "fire-detonation-1", 0.0))
        assertEquals(87, boss.currentHealth)
    }

    @Test
    fun `player max health modifier survives reset`() {
        val boss = PrototypeBossState()

        boss.registerPlayer(playerId, 24)
        boss.applyBossDamage(playerId, 1L, 8)
        boss.reset()

        assertEquals(24, boss.playerMaxHealth(playerId))
        assertEquals(24, boss.playerHealth(playerId))
    }

    @Test
    fun `head and back weakpoints both multiply body damage`() {
        val boss = PrototypeBossState()

        assertEquals(30, boss.applyPlayerAttack(1L, WeaponType.HEAVY_BLADE, FixedWeakpoint.HEAD))
        assertEquals(15, boss.applyPlayerAttack(2L, WeaponType.TWIN_RODS, FixedWeakpoint.BACK))
        assertEquals(2955, boss.currentHealth)
    }

    @Test
    fun `same player attack execution damages boss once and enters final struggle`() {
        val boss = PrototypeBossState(maxHealth = 440)

        assertEquals(20, boss.applyPlayerAttack(1L, WeaponType.HEAVY_BLADE))
        assertEquals(0, boss.applyPlayerAttack(1L, WeaponType.HEAVY_BLADE))
        repeat(14) { executionId ->
            boss.applyPlayerAttack(executionId + 2L, WeaponType.HEAVY_BLADE, FixedWeakpoint.HEAD)
        }

        assertEquals(0, boss.currentHealth)
        assertTrue(boss.isFinalStruggle)
        assertTrue(boss.isDefeated)
        assertEquals(0, boss.applyPlayerAttack(99L, WeaponType.TWIN_RODS))

        boss.completeFinalStruggle()
        assertTrue(boss.isVictory)
    }

    @Test
    fun `victory reward gate grants once and resets for the next encounter`() {
        val boss = PrototypeBossState(maxHealth = 20)
        boss.applyPlayerAttack(1L, WeaponType.HEAVY_BLADE)
        boss.completeFinalStruggle()

        assertTrue(boss.claimVictoryReward())
        assertFalse(boss.claimVictoryReward())
        boss.reset()
        assertFalse(boss.claimVictoryReward())
    }

    @Test
    fun `skill3 deals four pulses and one finisher once per execution`() {
        val boss = PrototypeBossState()
        val targetId = UUID.randomUUID()

        (1..4).forEach { pulse ->
            assertEquals(6, boss.applySkill3Pulse(1L, pulse, targetId))
            assertEquals(0, boss.applySkill3Pulse(1L, pulse, targetId))
        }
        assertEquals(12, boss.applySkill3Finisher(1L, targetId))
        assertEquals(0, boss.applySkill3Finisher(1L, targetId))
        assertEquals(2964, boss.currentHealth)
    }

    @Test
    fun `skill1 and blade storm damage are server-owned and reset`() {
        val boss = PrototypeBossState()
        val targetId = UUID.randomUUID()

        assertEquals(20, boss.applySkill1Attack(1L, targetId))
        assertEquals(0, boss.applySkill1Attack(1L, targetId))
        (1..4).forEach { pulse ->
            assertEquals(4, boss.applySkill2Pulse(2L, pulse, targetId))
            assertEquals(0, boss.applySkill2Pulse(2L, pulse, targetId))
        }
        assertEquals(12, boss.applySkill2Landing(2L, targetId))
        assertEquals(0, boss.applySkill2Landing(2L, targetId))
        assertEquals(2952, boss.currentHealth)

        boss.reset()

        assertEquals(20, boss.applySkill1Attack(1L, targetId))
        assertEquals(2980, boss.currentHealth)
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

    @Test
    fun `health thresholds advance phases once and hp zero starts final struggle`() {
        val boss = PrototypeBossState(maxHealth = 100)

        boss.applyPlayerAttack(1L, WeaponType.HEAVY_BLADE)
        assertEquals(PrototypeBossPhase.DUEL, boss.phase)
        boss.applyPlayerAttack(2L, WeaponType.HEAVY_BLADE)
        assertEquals(PrototypeBossPhase.RIFT_PRESSURE, boss.phase)
        boss.applyPlayerAttack(3L, WeaponType.HEAVY_BLADE)
        boss.applyPlayerAttack(4L, WeaponType.HEAVY_BLADE)
        assertEquals(PrototypeBossPhase.EXECUTION, boss.phase)
        boss.applyPlayerAttack(5L, WeaponType.HEAVY_BLADE)

        assertEquals(PrototypeEncounterState.FINAL_STRUGGLE, boss.encounterState)
        assertEquals(0, boss.applySkill1Attack(6L, playerId))
    }

    @Test
    fun `break multiplier applies to normal and skill damage and clears`() {
        val boss = PrototypeBossState()
        val targetId = UUID.randomUUID()

        boss.setBreakActive(true)
        assertEquals(30, boss.applyPlayerAttack(1L, WeaponType.HEAVY_BLADE))
        assertEquals(30, boss.applySkill1Attack(2L, targetId))
        boss.setBreakActive(false)
        assertEquals(12, boss.applySkill2Landing(3L, targetId))
    }

    @Test
    fun `final struggle player death defeats and reset clears lifecycle`() {
        val boss = PrototypeBossState()
        boss.forceFinalStruggle()

        assertEquals(20, boss.applyBossDamage(playerId, 1L, 20))
        assertTrue(boss.isDefeat)
        boss.reset()
        assertEquals(PrototypeEncounterState.ACTIVE, boss.encounterState)
        assertEquals(PrototypeBossPhase.DUEL, boss.phase)
        assertFalse(boss.breakActive)
    }
}
