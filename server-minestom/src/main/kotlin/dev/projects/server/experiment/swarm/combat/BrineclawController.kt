package dev.projects.server.experiment.swarm.combat

import java.util.UUID

enum class BrineclawAttack {
    SWEEP,
    CHARGE,
}

enum class BrineclawState {
    READY,
    SWEEP_TELEGRAPH,
    SWEEP_ACTIVE,
    CHARGE_TELEGRAPH,
    CHARGE_ACTIVE,
    EXPOSED,
    RECOVERY,
    DEFEATED,
}

data class BrineclawConfig(
    val maxHealth: Int = 80,
    val sweepTelegraphTicks: Long = 18,
    val sweepActiveTicks: Long = 4,
    val sweepRecoveryTicks: Long = 14,
    val chargeTelegraphTicks: Long = 24,
    val chargeActiveTicks: Long = 10,
    val chargeRecoveryTicks: Long = 16,
    val exposedTicks: Long = 50,
    val sweepRadius: Double = 4.0,
    val sweepHalfAngleDegrees: Double = 65.0,
    val chargeLength: Double = 9.0,
    val chargeHalfWidth: Double = 1.0,
    val chargeHeight: Double = 2.5,
) {
    init {
        require(maxHealth > 0)
        require(sweepTelegraphTicks > 0 && sweepActiveTicks > 0 && sweepRecoveryTicks >= 0)
        require(chargeTelegraphTicks > 0 && chargeActiveTicks > 0 && chargeRecoveryTicks >= 0)
        require(exposedTicks > 0)
    }
}

enum class BrineclawScheduledStep {
    ACTIVATE,
    END_ACTIVE,
    END_EXPOSURE,
    READY,
}

data class BrineclawScheduledAction(
    val generation: Long,
    val attackId: Long,
    val dueTick: Long,
    val step: BrineclawScheduledStep,
)

sealed interface BrineclawEvent {
    val generation: Long

    data class Telegraph(
        override val generation: Long,
        val attackId: Long,
        val attack: BrineclawAttack,
        val textCue: String,
        val soundCue: String,
    ) : BrineclawEvent

    data class Active(
        override val generation: Long,
        val attackId: Long,
        val attack: BrineclawAttack,
    ) : BrineclawEvent

    data class ExposureStarted(
        override val generation: Long,
        val attackId: Long,
        val untilTickExclusive: Long,
    ) : BrineclawEvent

    data class ExposureEnded(
        override val generation: Long,
        val attackId: Long,
    ) : BrineclawEvent

    data class Ready(
        override val generation: Long,
    ) : BrineclawEvent

    data class Defeated(
        override val generation: Long,
        val participants: Set<UUID>,
    ) : BrineclawEvent

    data class Reset(
        override val generation: Long,
    ) : BrineclawEvent
}

data class BrineclawPlan(
    val telegraph: BrineclawEvent.Telegraph,
    val scheduled: List<BrineclawScheduledAction>,
)

data class BrineclawTransition(
    val events: List<BrineclawEvent> = emptyList(),
    val scheduled: List<BrineclawScheduledAction> = emptyList(),
)

data class BrineclawHitResult(
    val accepted: Boolean,
    val damageApplied: Int = 0,
    val defeated: Boolean = false,
)

fun interface BrineclawDefeatSink {
    fun onDefeat(event: BrineclawEvent.Defeated)
}

class BrineclawController(
    private val config: BrineclawConfig = BrineclawConfig(),
    private val defeatSink: BrineclawDefeatSink = BrineclawDefeatSink {},
) {
    var generation: Long = 1
        private set
    var state: BrineclawState = BrineclawState.READY
        private set
    var health: Int = config.maxHealth
        private set

    private var nextAttackId = 1L
    private var currentAttackId: Long? = null
    private var currentAttack: BrineclawAttack? = null
    private var expectedReadyTick: Long? = null
    private val hitActions = mutableSetOf<Pair<UUID, Long>>()
    private val participants = mutableSetOf<UUID>()

    fun beginSweep(tick: Long): BrineclawPlan? = beginAttack(BrineclawAttack.SWEEP, tick)

    fun beginCharge(tick: Long): BrineclawPlan? = beginAttack(BrineclawAttack.CHARGE, tick)

    fun executeScheduled(action: BrineclawScheduledAction, tick: Long): BrineclawTransition {
        if (
            action.generation != generation ||
            state == BrineclawState.DEFEATED ||
            tick < action.dueTick ||
            action.attackId != currentAttackId
        ) {
            return BrineclawTransition()
        }

        return when (action.step) {
            BrineclawScheduledStep.ACTIVATE -> activate(action.attackId)
            BrineclawScheduledStep.END_ACTIVE -> endActive(action.attackId)
            BrineclawScheduledStep.END_EXPOSURE -> endExposure(action.attackId, tick)
            BrineclawScheduledStep.READY -> becomeReady(action)
        }
    }

    fun collideChargeWithPracticePost(tick: Long): BrineclawTransition {
        if (state != BrineclawState.CHARGE_ACTIVE) return BrineclawTransition()
        val attackId = currentAttackId ?: return BrineclawTransition()
        state = BrineclawState.EXPOSED
        expectedReadyTick = null
        val endTick = tick + config.exposedTicks
        return BrineclawTransition(
            events = listOf(BrineclawEvent.ExposureStarted(generation, attackId, endTick)),
            scheduled = listOf(
                BrineclawScheduledAction(generation, attackId, endTick, BrineclawScheduledStep.END_EXPOSURE),
            ),
        )
    }

    fun applyTidehookHit(playerId: UUID, actionId: Long, damage: Int): BrineclawHitResult {
        if (damage <= 0 || state == BrineclawState.DEFEATED) return BrineclawHitResult(false)
        if (!hitActions.add(playerId to actionId)) return BrineclawHitResult(false)
        participants += playerId
        val applied = minOf(damage, health)
        health -= applied
        if (health > 0) return BrineclawHitResult(true, applied)

        state = BrineclawState.DEFEATED
        val event = BrineclawEvent.Defeated(generation, participants.toSet())
        defeatSink.onDefeat(event)
        return BrineclawHitResult(true, applied, defeated = true)
    }

    fun intersectsSweep(origin: CombatPoint, forward: CombatPoint, target: CombatBounds): Boolean =
        state == BrineclawState.SWEEP_ACTIVE && CombatGeometry.intersectsSweepArc(
            origin,
            forward,
            config.sweepRadius,
            config.sweepHalfAngleDegrees,
            target,
        )

    fun intersectsCharge(origin: CombatPoint, forward: CombatPoint, target: CombatBounds): Boolean =
        state == BrineclawState.CHARGE_ACTIVE && CombatGeometry.intersectsChargeCorridor(
            origin,
            forward,
            config.chargeLength,
            config.chargeHalfWidth,
            config.chargeHeight,
            target,
        )

    fun reset(): BrineclawEvent.Reset {
        generation += 1
        state = BrineclawState.READY
        health = config.maxHealth
        currentAttackId = null
        currentAttack = null
        expectedReadyTick = null
        hitActions.clear()
        participants.clear()
        return BrineclawEvent.Reset(generation)
    }

    private fun beginAttack(attack: BrineclawAttack, tick: Long): BrineclawPlan? {
        if (state != BrineclawState.READY) return null
        val attackId = nextAttackId++
        currentAttackId = attackId
        currentAttack = attack
        val telegraphTicks: Long
        val activeTicks: Long
        val recoveryTicks: Long
        val textCue: String
        val soundCue: String
        when (attack) {
            BrineclawAttack.SWEEP -> {
                state = BrineclawState.SWEEP_TELEGRAPH
                telegraphTicks = config.sweepTelegraphTicks
                activeTicks = config.sweepActiveTicks
                recoveryTicks = config.sweepRecoveryTicks
                textCue = "Brineclaw sweeps — leave the arc"
                soundCue = "brineclaw.sweep"
            }

            BrineclawAttack.CHARGE -> {
                state = BrineclawState.CHARGE_TELEGRAPH
                telegraphTicks = config.chargeTelegraphTicks
                activeTicks = config.chargeActiveTicks
                recoveryTicks = config.chargeRecoveryTicks
                textCue = "Brineclaw charges — sidestep or Brace"
                soundCue = "brineclaw.charge"
            }
        }
        val activeTick = tick + telegraphTicks
        val recoveryTick = activeTick + activeTicks
        val readyTick = recoveryTick + recoveryTicks
        expectedReadyTick = readyTick
        return BrineclawPlan(
            telegraph = BrineclawEvent.Telegraph(generation, attackId, attack, textCue, soundCue),
            scheduled = listOf(
                BrineclawScheduledAction(generation, attackId, activeTick, BrineclawScheduledStep.ACTIVATE),
                BrineclawScheduledAction(generation, attackId, recoveryTick, BrineclawScheduledStep.END_ACTIVE),
                BrineclawScheduledAction(generation, attackId, readyTick, BrineclawScheduledStep.READY),
            ),
        )
    }

    private fun activate(attackId: Long): BrineclawTransition {
        state = when {
            currentAttack == BrineclawAttack.SWEEP && state == BrineclawState.SWEEP_TELEGRAPH ->
                BrineclawState.SWEEP_ACTIVE
            currentAttack == BrineclawAttack.CHARGE && state == BrineclawState.CHARGE_TELEGRAPH ->
                BrineclawState.CHARGE_ACTIVE
            else -> return BrineclawTransition()
        }
        return BrineclawTransition(listOf(BrineclawEvent.Active(generation, attackId, currentAttack!!)))
    }

    private fun endActive(attackId: Long): BrineclawTransition {
        if (state != BrineclawState.SWEEP_ACTIVE && state != BrineclawState.CHARGE_ACTIVE) {
            return BrineclawTransition()
        }
        state = BrineclawState.RECOVERY
        return BrineclawTransition()
    }

    private fun endExposure(attackId: Long, tick: Long): BrineclawTransition {
        if (state != BrineclawState.EXPOSED) return BrineclawTransition()
        state = BrineclawState.RECOVERY
        val readyTick = tick + config.chargeRecoveryTicks
        expectedReadyTick = readyTick
        return BrineclawTransition(
            events = listOf(BrineclawEvent.ExposureEnded(generation, attackId)),
            scheduled = listOf(
                BrineclawScheduledAction(
                    generation,
                    attackId,
                    readyTick,
                    BrineclawScheduledStep.READY,
                ),
            ),
        )
    }

    private fun becomeReady(action: BrineclawScheduledAction): BrineclawTransition {
        if (state != BrineclawState.RECOVERY || action.dueTick != expectedReadyTick) {
            return BrineclawTransition()
        }
        state = BrineclawState.READY
        currentAttackId = null
        currentAttack = null
        expectedReadyTick = null
        return BrineclawTransition(listOf(BrineclawEvent.Ready(generation)))
    }
}
