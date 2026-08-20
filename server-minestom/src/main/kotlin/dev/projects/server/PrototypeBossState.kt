package dev.projects.server

import java.util.UUID
import kotlin.math.roundToInt

enum class PrototypeEncounterState {
    ACTIVE,
    VICTORY,
    DEFEAT,
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

    private val playerHealth = mutableMapOf<UUID, Int>()
    private val playerDamageExecutions = mutableMapOf<UUID, MutableSet<Long>>()
    private val bossDamageExecutions = mutableSetOf<Long>()

    val isActive: Boolean
        get() = encounterState == PrototypeEncounterState.ACTIVE

    val isVictory: Boolean
        get() = encounterState == PrototypeEncounterState.VICTORY

    val isDefeat: Boolean
        get() = encounterState == PrototypeEncounterState.DEFEAT

    val isDefeated: Boolean
        get() = currentHealth == 0

    val healthProgress: Float
        get() = currentHealth.toFloat() / maxHealth

    fun registerPlayer(playerId: UUID) {
        playerHealth.putIfAbsent(playerId, playerMaxHealth)
    }

    fun playerHealth(playerId: UUID): Int = playerHealth[playerId] ?: playerMaxHealth

    fun playerEntityHealth(playerId: UUID): Float = playerHealth(playerId).coerceAtLeast(1).toFloat()

    /** Applies one explicit boss attack to one player, once per execution. */
    fun applyBossAttack(playerId: UUID, executionId: Long, attack: FixedAttackType): Int {
        if (!isActive) return 0
        registerPlayer(playerId)
        val executions = playerDamageExecutions.getOrPut(playerId) { mutableSetOf() }
        if (!executions.add(executionId)) return 0

        val remainingHealth = (playerHealth(playerId) - attack.damage).coerceAtLeast(0)
        playerHealth[playerId] = remainingHealth
        if (remainingHealth == 0) encounterState = PrototypeEncounterState.DEFEAT
        return attack.damage
    }

    /** Applies the server-confirmed player hit to the single prototype boss. */
    fun applyPlayerAttack(
        attackExecutionId: Long,
        weapon: WeaponType,
        weakpoint: FixedWeakpoint? = null,
    ): Int {
        if (!isActive || !bossDamageExecutions.add(attackExecutionId)) return 0

        val bodyDamage = when (weapon) {
            WeaponType.HEAVY_BLADE -> HEAVY_BLADE_BODY_DAMAGE
            WeaponType.TWIN_RODS -> TWIN_RODS_BODY_DAMAGE
        }
        val damage = if (weakpoint == null) bodyDamage else (bodyDamage * WEAKPOINT_MULTIPLIER).roundToInt()
        currentHealth = (currentHealth - damage).coerceAtLeast(0)
        if (currentHealth == 0) encounterState = PrototypeEncounterState.VICTORY
        return damage
    }

    /** Restores the prototype encounter and every registered player to its test-start state. */
    fun reset() {
        currentHealth = maxHealth
        encounterState = PrototypeEncounterState.ACTIVE
        bossDamageExecutions.clear()
        playerDamageExecutions.clear()
        playerHealth.keys.toList().forEach { playerHealth[it] = playerMaxHealth }
    }

    companion object {
        const val DEFAULT_MAX_HEALTH = 300
        const val DEFAULT_PLAYER_MAX_HEALTH = 20
        const val HEAVY_BLADE_BODY_DAMAGE = 20
        const val TWIN_RODS_BODY_DAMAGE = 10
        const val WEAKPOINT_MULTIPLIER = 1.5
    }
}
