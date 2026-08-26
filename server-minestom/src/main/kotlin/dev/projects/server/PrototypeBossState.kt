package dev.projects.server

import java.util.UUID
import kotlin.math.roundToInt

enum class PrototypeEncounterState {
    ACTIVE,
    FINAL_STRUGGLE,
    VICTORY,
    DEFEAT,
}

enum class PrototypeBossPhase {
    DUEL,
    RIFT_PRESSURE,
    EXECUTION,
}

/** Small encounter state for the first playable boss loop. */
class PrototypeBossState(
    val maxHealth: Int = DEFAULT_MAX_HEALTH,
    val playerMaxHealth: Int = DEFAULT_PLAYER_MAX_HEALTH,
) {
    init {
        require(maxHealth > 0) { "Boss max health must be positive" }
        require(playerMaxHealth > 0) { "Player max health must be positive" }
    }

    var currentHealth: Int = maxHealth
        private set

    var encounterState: PrototypeEncounterState = PrototypeEncounterState.ACTIVE
        private set

    var phase: PrototypeBossPhase = PrototypeBossPhase.DUEL
        private set

    var breakActive: Boolean = false
        private set

    private val playerHealth = mutableMapOf<UUID, Int>()
    private val playerMaxHealthValues = mutableMapOf<UUID, Int>()
    private val playerDamageExecutions = mutableMapOf<UUID, MutableSet<Long>>()
    private val bossDamageExecutions = mutableSetOf<Long>()
    private val skill3PulseDamageExecutions = mutableSetOf<Triple<Long, Int, UUID>>()
    private val skill3FinisherDamageExecutions = mutableSetOf<Pair<Long, UUID>>()
    private val skill1DamageExecutions = mutableSetOf<Pair<Long, UUID>>()
    private val skill2PulseDamageExecutions = mutableSetOf<Triple<Long, Int, UUID>>()
    private val skill2LandingDamageExecutions = mutableSetOf<Pair<Long, UUID>>()
    private var victoryRewardClaimed = false

    val isActive: Boolean
        get() = encounterState == PrototypeEncounterState.ACTIVE

    val isEncounterRunning: Boolean
        get() = encounterState == PrototypeEncounterState.ACTIVE ||
            encounterState == PrototypeEncounterState.FINAL_STRUGGLE

    val isVictory: Boolean
        get() = encounterState == PrototypeEncounterState.VICTORY

    val isFinalStruggle: Boolean
        get() = encounterState == PrototypeEncounterState.FINAL_STRUGGLE

    val isDefeat: Boolean
        get() = encounterState == PrototypeEncounterState.DEFEAT

    val isDefeated: Boolean
        get() = currentHealth == 0

    val healthProgress: Float
        get() = currentHealth.toFloat() / maxHealth

    fun registerPlayer(playerId: UUID, maxHealth: Int = playerMaxHealth) {
        require(maxHealth > 0) { "Player max health must be positive" }
        playerMaxHealthValues.putIfAbsent(playerId, maxHealth)
        playerHealth.putIfAbsent(playerId, maxHealth)
    }

    fun playerHealth(playerId: UUID): Int = playerHealth[playerId] ?: playerMaxHealth

    fun playerMaxHealth(playerId: UUID): Int = playerMaxHealthValues[playerId] ?: playerMaxHealth

    fun setPlayerMaxHealth(playerId: UUID, maxHealth: Int) {
        require(maxHealth > 0) { "Player max health must be positive" }
        registerPlayer(playerId)
        playerMaxHealthValues[playerId] = maxHealth
        playerHealth[playerId] = playerHealth(playerId).coerceAtMost(maxHealth)
    }

    fun playerEntityHealth(playerId: UUID): Float = playerHealth(playerId).coerceAtLeast(1).toFloat()

    /** Applies one explicit boss attack to one player, once per execution. */
    fun applyBossAttack(
        playerId: UUID,
        executionId: Long,
        attack: FixedAttackType,
        incomingDamageMultiplier: Double = 1.0,
    ): Int {
        return applyBossDamage(playerId, executionId, attack.damage, incomingDamageMultiplier)
    }

    fun applyBossDamage(
        playerId: UUID,
        executionId: Long,
        damage: Int,
        incomingDamageMultiplier: Double = 1.0,
    ): Int {
        require(damage >= 0) { "Boss damage must not be negative" }
        require(incomingDamageMultiplier >= 0.0 && incomingDamageMultiplier.isFinite()) {
            "Incoming damage multiplier must be finite and non-negative"
        }
        if (!isEncounterRunning) return 0
        registerPlayer(playerId)
        val executions = playerDamageExecutions.getOrPut(playerId) { mutableSetOf() }
        if (!executions.add(executionId)) return 0

        val effectiveDamage = (damage * incomingDamageMultiplier).roundToInt().coerceAtLeast(0)
        val remainingHealth = (playerHealth(playerId) - effectiveDamage).coerceAtLeast(0)
        playerHealth[playerId] = remainingHealth
        if (remainingHealth == 0) encounterState = PrototypeEncounterState.DEFEAT
        return effectiveDamage
    }

    /** Applies the server-confirmed player hit to the single prototype boss. */
    fun applyPlayerAttack(
        attackExecutionId: Long,
        weapon: WeaponType,
        weakpoint: FixedWeakpoint? = null,
        outgoingDamageMultiplier: Double = 1.0,
    ): Int {
        if (!isActive || !bossDamageExecutions.add(attackExecutionId)) return 0

        val bodyDamage = when (weapon) {
            WeaponType.HEAVY_BLADE -> HEAVY_BLADE_BODY_DAMAGE
            WeaponType.TWIN_RODS -> TWIN_RODS_BODY_DAMAGE
        }
        return applyPlayerDamage(bodyDamage, weakpoint != null, outgoingDamageMultiplier)
    }

    /** Applies one server-confirmed Skill3 pulse per cast, pulse, and target. */
    fun applySkill3Pulse(
        castId: Long,
        pulseIndex: Int,
        targetId: UUID,
        outgoingDamageMultiplier: Double = 1.0,
    ): Int {
        require(pulseIndex in 1..Skill3State.PULSE_COUNT) { "Skill3 pulse index is out of range" }
        if (!isActive || !skill3PulseDamageExecutions.add(Triple(castId, pulseIndex, targetId))) return 0
        return applyPlayerDamage(SKILL_3_PULSE_DAMAGE, outgoingDamageMultiplier = outgoingDamageMultiplier)
    }

    /** Applies the Skill3 finisher once per cast and target. */
    fun applySkill3Finisher(
        castId: Long,
        targetId: UUID,
        outgoingDamageMultiplier: Double = 1.0,
    ): Int {
        if (!isActive || !skill3FinisherDamageExecutions.add(castId to targetId)) return 0
        return applyPlayerDamage(SKILL_3_FINISHER_DAMAGE, outgoingDamageMultiplier = outgoingDamageMultiplier)
    }

    /** Applies one server-confirmed Skill1 hit per cast and target. */
    fun applySkill1Attack(castId: Long, targetId: UUID, outgoingDamageMultiplier: Double = 1.0): Int {
        if (!isActive || !skill1DamageExecutions.add(castId to targetId)) return 0
        return applyPlayerDamage(SKILL_1_DAMAGE, outgoingDamageMultiplier = outgoingDamageMultiplier)
    }

    /** Applies one server-confirmed Skill2 pulse hit per cast, pulse, and target. */
    fun applySkill2Pulse(
        castId: Long,
        pulseIndex: Int,
        targetId: UUID,
        outgoingDamageMultiplier: Double = 1.0,
    ): Int {
        require(pulseIndex in 1..Skill2State.PULSE_COUNT) { "Skill2 pulse index is out of range" }
        if (!isActive || !skill2PulseDamageExecutions.add(Triple(castId, pulseIndex, targetId))) return 0
        return applyPlayerDamage(SKILL_2_PULSE_DAMAGE, outgoingDamageMultiplier = outgoingDamageMultiplier)
    }

    /** Applies the server-confirmed Skill2 landing finisher once per cast and target. */
    fun applySkill2Landing(castId: Long, targetId: UUID, outgoingDamageMultiplier: Double = 1.0): Int {
        if (!isActive || !skill2LandingDamageExecutions.add(castId to targetId)) return 0
        return applyPlayerDamage(SKILL_2_LANDING_DAMAGE, outgoingDamageMultiplier = outgoingDamageMultiplier)
    }

    fun setBreakActive(active: Boolean) {
        breakActive = active
    }

    fun completeFinalStruggle() {
        if (encounterState == PrototypeEncounterState.FINAL_STRUGGLE) {
            encounterState = PrototypeEncounterState.VICTORY
        }
    }

    /** Claims the encounter reward gate once after a successful victory. */
    fun claimVictoryReward(): Boolean {
        if (!isVictory || victoryRewardClaimed) return false
        victoryRewardClaimed = true
        return true
    }

    /** Development-only phase jump used to inspect one mechanic without a full HP burn. */
    fun forcePhase(targetPhase: PrototypeBossPhase) {
        breakActive = false
        playerDamageExecutions.clear()
        bossDamageExecutions.clear()
        skill3PulseDamageExecutions.clear()
        skill3FinisherDamageExecutions.clear()
        skill1DamageExecutions.clear()
        skill2PulseDamageExecutions.clear()
        skill2LandingDamageExecutions.clear()
        when (targetPhase) {
            PrototypeBossPhase.DUEL -> {
                currentHealth = (maxHealth * 0.85).roundToInt().coerceAtLeast(1)
                encounterState = PrototypeEncounterState.ACTIVE
                phase = PrototypeBossPhase.DUEL
            }
            PrototypeBossPhase.RIFT_PRESSURE -> {
                currentHealth = (maxHealth * 0.60).roundToInt().coerceAtLeast(1)
                encounterState = PrototypeEncounterState.ACTIVE
                phase = PrototypeBossPhase.RIFT_PRESSURE
            }
            PrototypeBossPhase.EXECUTION -> {
                currentHealth = (maxHealth * 0.20).roundToInt().coerceAtLeast(1)
                encounterState = PrototypeEncounterState.ACTIVE
                phase = PrototypeBossPhase.EXECUTION
            }
        }
    }

    fun forceFinalStruggle() {
        currentHealth = 0
        phase = PrototypeBossPhase.EXECUTION
        encounterState = PrototypeEncounterState.FINAL_STRUGGLE
        breakActive = false
        playerDamageExecutions.clear()
        bossDamageExecutions.clear()
        skill3PulseDamageExecutions.clear()
        skill3FinisherDamageExecutions.clear()
        skill1DamageExecutions.clear()
        skill2PulseDamageExecutions.clear()
        skill2LandingDamageExecutions.clear()
    }

    /** Restores the prototype encounter and every registered player to its test-start state. */
    fun reset() {
        currentHealth = maxHealth
        encounterState = PrototypeEncounterState.ACTIVE
        phase = PrototypeBossPhase.DUEL
        breakActive = false
        bossDamageExecutions.clear()
        skill3PulseDamageExecutions.clear()
        skill3FinisherDamageExecutions.clear()
        skill1DamageExecutions.clear()
        skill2PulseDamageExecutions.clear()
        skill2LandingDamageExecutions.clear()
        playerDamageExecutions.clear()
        victoryRewardClaimed = false
        playerHealth.keys.toList().forEach { playerHealth[it] = playerMaxHealth(it) }
    }

    private fun applyPlayerDamage(
        bodyDamage: Int,
        weakpoint: Boolean = false,
        outgoingDamageMultiplier: Double = 1.0,
    ): Int {
        require(outgoingDamageMultiplier >= 0.0 && outgoingDamageMultiplier.isFinite()) {
            "Outgoing damage multiplier must be finite and non-negative"
        }
        if (!isActive) return 0
        val breakMultiplier = if (breakActive) BREAK_MULTIPLIER else 1.0
        val weakpointMultiplier = if (weakpoint) WEAKPOINT_MULTIPLIER else 1.0
        val damage = (bodyDamage * breakMultiplier * weakpointMultiplier * outgoingDamageMultiplier).roundToInt()
        currentHealth = (currentHealth - damage).coerceAtLeast(0)
        updatePhase()
        if (currentHealth == 0) encounterState = PrototypeEncounterState.FINAL_STRUGGLE
        return damage
    }

    private fun updatePhase() {
        val nextPhase = when {
            currentHealth * 100 > maxHealth * 70 -> PrototypeBossPhase.DUEL
            currentHealth * 100 > maxHealth * 30 -> PrototypeBossPhase.RIFT_PRESSURE
            else -> PrototypeBossPhase.EXECUTION
        }
        if (nextPhase.ordinal > phase.ordinal) phase = nextPhase
    }

    companion object {
        const val DEFAULT_MAX_HEALTH = 3000
        const val DEFAULT_PLAYER_MAX_HEALTH = 20
        const val HEAVY_BLADE_BODY_DAMAGE = 20
        const val TWIN_RODS_BODY_DAMAGE = 10
        const val SKILL_3_PULSE_DAMAGE = 6
        const val SKILL_3_FINISHER_DAMAGE = 12
        const val SKILL_1_DAMAGE = 20
        const val SKILL_2_PULSE_DAMAGE = 4
        const val SKILL_2_LANDING_DAMAGE = 12
        const val WEAKPOINT_MULTIPLIER = 1.5
        const val BREAK_MULTIPLIER = 1.5
    }
}
