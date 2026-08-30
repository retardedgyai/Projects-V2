package dev.projects.server.experiment.swarm.combat

import java.util.UUID
import kotlin.math.roundToInt

enum class CairnbackPhase {
    PHASE_ONE,
    PHASE_TWO,
}

enum class CairnbackAttack {
    SWEEP,
    CHARGE,
}

enum class CairnbackState {
    READY,
    SWEEP_TELEGRAPH,
    SWEEP_ACTIVE,
    CHARGE_TELEGRAPH,
    CHARGE_ACTIVE,
    EXPOSED,
    RECOVERY,
    VICTORY,
}

enum class CairnbackLifecycle {
    READY,
    ACTIVE,
    VICTORY,
}

enum class CairnbackResetReason {
    WIPE,
    HARD_TIMEOUT,
    INTEGRATION_REQUEST,
}

data class CairnbackAttackTiming(
    val telegraphTicks: Long,
    val activeTicks: Long,
    val recoveryTicks: Long,
) {
    init {
        require(telegraphTicks > 0 && activeTicks > 0 && recoveryTicks >= 0)
    }
}

data class CairnbackConfig(
    val baseMaxHealth: Int = 1_000,
    val additionalPlayerHealthFactor: Double = 0.65,
    val phaseTwoHealthRatio: Double = 0.50,
    val phaseOneSweep: CairnbackAttackTiming = CairnbackAttackTiming(26, 5, 20),
    val phaseOneCharge: CairnbackAttackTiming = CairnbackAttackTiming(32, 12, 22),
    val phaseTwoSweep: CairnbackAttackTiming = CairnbackAttackTiming(19, 5, 15),
    val phaseTwoCharge: CairnbackAttackTiming = CairnbackAttackTiming(23, 12, 16),
    val exposedTicks: Long = 60,
    val wipeGraceTicks: Long = 100,
    val hardTimeoutTicks: Long = 6_000,
    val sweepRadius: Double = 6.0,
    val sweepHalfAngleDegrees: Double = 70.0,
    val chargeLength: Double = 16.0,
    val chargeHalfWidth: Double = 1.6,
    val chargeHeight: Double = 4.0,
) {
    init {
        require(baseMaxHealth > 0)
        require(additionalPlayerHealthFactor >= 0.0)
        require(phaseTwoHealthRatio in 0.01..0.99)
        require(exposedTicks > 0 && wipeGraceTicks > 0 && hardTimeoutTicks > wipeGraceTicks)
    }
}

enum class CairnbackScheduledStep {
    ACTIVATE,
    END_ACTIVE,
    END_EXPOSURE,
    READY,
}

data class CairnbackScheduledAction(
    val generation: Long,
    val attackId: Long,
    val dueTick: Long,
    val step: CairnbackScheduledStep,
)

sealed interface CairnbackEvent {
    val generation: Long

    data class Started(
        override val generation: Long,
        val roster: Set<UUID>,
        val maxHealth: Int,
    ) : CairnbackEvent

    data class Telegraph(
        override val generation: Long,
        val attackId: Long,
        val attack: CairnbackAttack,
        val phase: CairnbackPhase,
        val targetId: UUID,
        val textCue: String,
        val soundCue: String,
    ) : CairnbackEvent

    data class Active(
        override val generation: Long,
        val attackId: Long,
        val attack: CairnbackAttack,
    ) : CairnbackEvent

    data class PhaseChanged(
        override val generation: Long,
        val phase: CairnbackPhase,
    ) : CairnbackEvent

    data class ExposureStarted(
        override val generation: Long,
        val attackId: Long,
        val untilTickExclusive: Long,
    ) : CairnbackEvent

    data class ExposureEnded(
        override val generation: Long,
        val attackId: Long,
    ) : CairnbackEvent

    data class Victory(
        override val generation: Long,
        val eligiblePlayers: Set<UUID>,
    ) : CairnbackEvent

    data class Reset(
        override val generation: Long,
        val previousGeneration: Long,
        val reason: CairnbackResetReason,
    ) : CairnbackEvent
}

data class CairnbackAttackPlan(
    val telegraph: CairnbackEvent.Telegraph,
    val scheduled: List<CairnbackScheduledAction>,
)

data class CairnbackTransition(
    val events: List<CairnbackEvent> = emptyList(),
    val scheduled: List<CairnbackScheduledAction> = emptyList(),
)

data class CairnbackStartResult(
    val accepted: Boolean,
    val event: CairnbackEvent.Started? = null,
    val reason: String? = null,
)

data class CairnbackHitResult(
    val accepted: Boolean,
    val damageApplied: Int = 0,
    val exposed: Boolean = false,
    val events: List<CairnbackEvent> = emptyList(),
)

data class CairnbackMemberSnapshot(
    val playerId: UUID,
    val online: Boolean,
    val present: Boolean,
    val alive: Boolean,
    val eligibilityLost: Boolean,
    val confirmedHits: Int,
)

fun interface CairnbackRewardSink {
    fun onEligibleVictory(event: CairnbackEvent.Victory)
}

class CairnbackEncounter(
    private val config: CairnbackConfig = CairnbackConfig(),
    private val rewardSink: CairnbackRewardSink = CairnbackRewardSink {},
) {
    var generation: Long = 0
        private set
    var lifecycle: CairnbackLifecycle = CairnbackLifecycle.READY
        private set
    var state: CairnbackState = CairnbackState.READY
        private set
    var phase: CairnbackPhase = CairnbackPhase.PHASE_ONE
        private set
    var maxHealth: Int = config.baseMaxHealth
        private set
    var health: Int = config.baseMaxHealth
        private set

    val exposed: Boolean
        get() = state == CairnbackState.EXPOSED

    private val members = linkedMapOf<UUID, MutableMember>()
    private val hitActions = mutableSetOf<Pair<UUID, Long>>()
    private var startTick = 0L
    private var inactiveSinceTick: Long? = null
    private var nextAttackId = 1L
    private var currentAttackId: Long? = null
    private var currentAttack: CairnbackAttack? = null
    private var expectedReadyTick: Long? = null
    private var phaseSequenceIndex = 0

    fun start(roster: Collection<UUID>, tick: Long): CairnbackStartResult {
        if (lifecycle == CairnbackLifecycle.ACTIVE) return CairnbackStartResult(false, reason = "encounter-active")
        val uniqueRoster = roster.toCollection(linkedSetOf())
        if (uniqueRoster.size !in 1..4 || uniqueRoster.size != roster.size) {
            return CairnbackStartResult(false, reason = "roster-must-have-1-to-4-unique-players")
        }

        generation += 1
        lifecycle = CairnbackLifecycle.ACTIVE
        state = CairnbackState.READY
        phase = CairnbackPhase.PHASE_ONE
        maxHealth = (config.baseMaxHealth * (1.0 + config.additionalPlayerHealthFactor * (uniqueRoster.size - 1))).roundToInt()
        health = maxHealth
        members.clear()
        uniqueRoster.forEach { members[it] = MutableMember(it) }
        hitActions.clear()
        startTick = tick
        inactiveSinceTick = null
        currentAttackId = null
        currentAttack = null
        expectedReadyTick = null
        phaseSequenceIndex = 0
        val event = CairnbackEvent.Started(generation, uniqueRoster, maxHealth)
        return CairnbackStartResult(true, event)
    }

    fun roster(): Set<UUID> = members.keys.toSet()

    fun member(playerId: UUID): CairnbackMemberSnapshot? = members[playerId]?.snapshot()

    fun beginNextAttack(tick: Long, targetId: UUID): CairnbackAttackPlan? {
        if (lifecycle != CairnbackLifecycle.ACTIVE || state != CairnbackState.READY) return null
        val target = members[targetId] ?: return null
        if (!target.canAct()) return null

        val sequence = when (phase) {
            CairnbackPhase.PHASE_ONE -> PHASE_ONE_SEQUENCE
            CairnbackPhase.PHASE_TWO -> PHASE_TWO_SEQUENCE
        }
        val attack = sequence[phaseSequenceIndex % sequence.size]
        phaseSequenceIndex += 1
        val timing = timingFor(attack)
        val attackId = nextAttackId++
        currentAttackId = attackId
        currentAttack = attack
        state = when (attack) {
            CairnbackAttack.SWEEP -> CairnbackState.SWEEP_TELEGRAPH
            CairnbackAttack.CHARGE -> CairnbackState.CHARGE_TELEGRAPH
        }
        val activeTick = tick + timing.telegraphTicks
        val recoveryTick = activeTick + timing.activeTicks
        val readyTick = recoveryTick + timing.recoveryTicks
        expectedReadyTick = readyTick
        val telegraph = CairnbackEvent.Telegraph(
            generation = generation,
            attackId = attackId,
            attack = attack,
            phase = phase,
            targetId = targetId,
            textCue = when (attack) {
                CairnbackAttack.SWEEP -> "Cairnback sweeps — leave the arc"
                CairnbackAttack.CHARGE -> "Cairnback charges — line it up with a powered pillar"
            },
            soundCue = when (attack) {
                CairnbackAttack.SWEEP -> "cairnback.sweep"
                CairnbackAttack.CHARGE -> "cairnback.charge"
            },
        )
        return CairnbackAttackPlan(
            telegraph = telegraph,
            scheduled = listOf(
                CairnbackScheduledAction(generation, attackId, activeTick, CairnbackScheduledStep.ACTIVATE),
                CairnbackScheduledAction(generation, attackId, recoveryTick, CairnbackScheduledStep.END_ACTIVE),
                CairnbackScheduledAction(generation, attackId, readyTick, CairnbackScheduledStep.READY),
            ),
        )
    }

    fun executeScheduled(action: CairnbackScheduledAction, tick: Long): CairnbackTransition {
        if (action.generation != generation || lifecycle != CairnbackLifecycle.ACTIVE || tick < action.dueTick) {
            return CairnbackTransition()
        }
        if (action.attackId != currentAttackId) return CairnbackTransition()

        return when (action.step) {
            CairnbackScheduledStep.ACTIVATE -> activate(action.attackId)
            CairnbackScheduledStep.END_ACTIVE -> endActive()
            CairnbackScheduledStep.END_EXPOSURE -> endExposure(action.attackId, tick)
            CairnbackScheduledStep.READY -> becomeReady(action)
        }
    }

    fun collideChargeWithPillar(powered: Boolean, tick: Long): CairnbackTransition {
        if (!powered || lifecycle != CairnbackLifecycle.ACTIVE || state != CairnbackState.CHARGE_ACTIVE) {
            return CairnbackTransition()
        }
        val attackId = currentAttackId ?: return CairnbackTransition()
        state = CairnbackState.EXPOSED
        expectedReadyTick = null
        val endTick = tick + config.exposedTicks
        return CairnbackTransition(
            events = listOf(CairnbackEvent.ExposureStarted(generation, attackId, endTick)),
            scheduled = listOf(
                CairnbackScheduledAction(generation, attackId, endTick, CairnbackScheduledStep.END_EXPOSURE),
            ),
        )
    }

    fun applyTidehookHit(playerId: UUID, actionId: Long, damage: Int): CairnbackHitResult {
        if (lifecycle != CairnbackLifecycle.ACTIVE || damage <= 0) return CairnbackHitResult(false)
        val member = members[playerId] ?: return CairnbackHitResult(false)
        if (!member.canAct() || !hitActions.add(playerId to actionId)) return CairnbackHitResult(false)

        member.confirmedHits += 1
        val wasExposed = exposed
        val applied = minOf(damage, health)
        health -= applied
        val events = mutableListOf<CairnbackEvent>()
        if (phase == CairnbackPhase.PHASE_ONE && health > 0 && health <= maxHealth * config.phaseTwoHealthRatio) {
            phase = CairnbackPhase.PHASE_TWO
            phaseSequenceIndex = 0
            events += CairnbackEvent.PhaseChanged(generation, phase)
        }
        if (health == 0) {
            lifecycle = CairnbackLifecycle.VICTORY
            state = CairnbackState.VICTORY
            val victory = CairnbackEvent.Victory(
                generation,
                members.values.filter { it.eligibleForReward() }.mapTo(linkedSetOf()) { it.playerId },
            )
            events += victory
            rewardSink.onEligibleVictory(victory)
        }
        return CairnbackHitResult(true, applied, wasExposed, events)
    }

    fun setAlive(playerId: UUID, alive: Boolean) {
        members[playerId]?.alive = alive
    }

    fun markDisconnected(playerId: UUID) {
        members[playerId]?.apply {
            online = false
            present = false
            eligibilityLost = true
        }
    }

    fun markExited(playerId: UUID) {
        members[playerId]?.apply {
            present = false
            eligibilityLost = true
        }
    }

    fun tick(tick: Long): CairnbackTransition {
        if (lifecycle != CairnbackLifecycle.ACTIVE) return CairnbackTransition()
        if (tick - startTick >= config.hardTimeoutTicks) {
            return CairnbackTransition(events = listOf(reset(CairnbackResetReason.HARD_TIMEOUT)))
        }

        if (members.values.none { it.canAct() }) {
            val inactiveSince = inactiveSinceTick
            if (inactiveSince == null) {
                inactiveSinceTick = tick
            } else if (tick - inactiveSince >= config.wipeGraceTicks) {
                return CairnbackTransition(events = listOf(reset(CairnbackResetReason.WIPE)))
            }
        } else {
            inactiveSinceTick = null
        }
        return CairnbackTransition()
    }

    fun intersectsSweep(origin: CombatPoint, forward: CombatPoint, target: CombatBounds): Boolean =
        state == CairnbackState.SWEEP_ACTIVE && CombatGeometry.intersectsSweepArc(
            origin,
            forward,
            config.sweepRadius,
            config.sweepHalfAngleDegrees,
            target,
        )

    fun intersectsCharge(origin: CombatPoint, forward: CombatPoint, target: CombatBounds): Boolean =
        state == CairnbackState.CHARGE_ACTIVE && CombatGeometry.intersectsChargeCorridor(
            origin,
            forward,
            config.chargeLength,
            config.chargeHalfWidth,
            config.chargeHeight,
            target,
        )

    fun reset(reason: CairnbackResetReason = CairnbackResetReason.INTEGRATION_REQUEST): CairnbackEvent.Reset {
        val previousGeneration = generation
        generation += 1
        lifecycle = CairnbackLifecycle.READY
        state = CairnbackState.READY
        phase = CairnbackPhase.PHASE_ONE
        maxHealth = config.baseMaxHealth
        health = config.baseMaxHealth
        members.clear()
        hitActions.clear()
        inactiveSinceTick = null
        currentAttackId = null
        currentAttack = null
        expectedReadyTick = null
        phaseSequenceIndex = 0
        return CairnbackEvent.Reset(generation, previousGeneration, reason)
    }

    private fun timingFor(attack: CairnbackAttack): CairnbackAttackTiming = when (phase) {
        CairnbackPhase.PHASE_ONE -> when (attack) {
            CairnbackAttack.SWEEP -> config.phaseOneSweep
            CairnbackAttack.CHARGE -> config.phaseOneCharge
        }

        CairnbackPhase.PHASE_TWO -> when (attack) {
            CairnbackAttack.SWEEP -> config.phaseTwoSweep
            CairnbackAttack.CHARGE -> config.phaseTwoCharge
        }
    }

    private fun activate(attackId: Long): CairnbackTransition {
        state = when {
            currentAttack == CairnbackAttack.SWEEP && state == CairnbackState.SWEEP_TELEGRAPH -> CairnbackState.SWEEP_ACTIVE
            currentAttack == CairnbackAttack.CHARGE && state == CairnbackState.CHARGE_TELEGRAPH -> CairnbackState.CHARGE_ACTIVE
            else -> return CairnbackTransition()
        }
        return CairnbackTransition(listOf(CairnbackEvent.Active(generation, attackId, currentAttack!!)))
    }

    private fun endActive(): CairnbackTransition {
        if (state != CairnbackState.SWEEP_ACTIVE && state != CairnbackState.CHARGE_ACTIVE) {
            return CairnbackTransition()
        }
        state = CairnbackState.RECOVERY
        return CairnbackTransition()
    }

    private fun endExposure(attackId: Long, tick: Long): CairnbackTransition {
        if (state != CairnbackState.EXPOSED) return CairnbackTransition()
        state = CairnbackState.RECOVERY
        val readyTick = tick + timingFor(CairnbackAttack.CHARGE).recoveryTicks
        expectedReadyTick = readyTick
        return CairnbackTransition(
            events = listOf(CairnbackEvent.ExposureEnded(generation, attackId)),
            scheduled = listOf(
                CairnbackScheduledAction(
                    generation,
                    attackId,
                    readyTick,
                    CairnbackScheduledStep.READY,
                ),
            ),
        )
    }

    private fun becomeReady(action: CairnbackScheduledAction): CairnbackTransition {
        if (state != CairnbackState.RECOVERY || action.dueTick != expectedReadyTick) {
            return CairnbackTransition()
        }
        state = CairnbackState.READY
        currentAttackId = null
        currentAttack = null
        expectedReadyTick = null
        return CairnbackTransition()
    }

    private data class MutableMember(
        val playerId: UUID,
        var online: Boolean = true,
        var present: Boolean = true,
        var alive: Boolean = true,
        var eligibilityLost: Boolean = false,
        var confirmedHits: Int = 0,
    ) {
        fun canAct(): Boolean = online && present && alive && !eligibilityLost

        fun eligibleForReward(): Boolean = online && present && !eligibilityLost && confirmedHits >= 3

        fun snapshot() = CairnbackMemberSnapshot(
            playerId,
            online,
            present,
            alive,
            eligibilityLost,
            confirmedHits,
        )
    }

    private companion object {
        val PHASE_ONE_SEQUENCE = listOf(CairnbackAttack.SWEEP, CairnbackAttack.CHARGE)
        val PHASE_TWO_SEQUENCE = listOf(CairnbackAttack.SWEEP, CairnbackAttack.CHARGE, CairnbackAttack.CHARGE)
    }
}
