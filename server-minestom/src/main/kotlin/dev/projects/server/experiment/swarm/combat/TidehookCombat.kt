package dev.projects.server.experiment.swarm.combat

import java.util.ArrayDeque
import java.util.UUID

enum class TidehookMod {
    NONE,
    BARBED_POINT,
    GUARD_RING,
}

enum class TidehookHand {
    MAIN,
    OFF,
}

enum class TidehookActionState {
    READY,
    THRUST_RECOVERY,
    BRACE_WINDUP,
    BRACE_ACTIVE,
    BRACE_RECOVERY,
}

enum class TidehookRejectReason {
    DUPLICATE_ACTION,
    WRONG_ITEM,
    WRONG_ZONE,
    INVALID_TARGET,
    NOT_ALIVE,
    ACTION_RECOVERY,
    OUT_OF_RANGE,
    NOT_FACING,
    LINE_OF_SIGHT_BLOCKED,
    OFFHAND_IGNORED,
}

data class TidehookConfig(
    val thrustRange: Double = 3.5,
    val minimumFacingDot: Double = 0.35,
    val thrustRecoveryTicks: Long = 8,
    val braceWindupTicks: Long = 3,
    val baseBraceActiveTicks: Long = 12,
    val barbedBraceActiveTicks: Long = 10,
    val guardBraceActiveTicks: Long = 15,
    val braceRecoveryTicks: Long = 8,
    val braceDamageMultiplier: Double = 0.35,
    val braceKnockbackMultiplier: Double = 0.25,
    val guardDamageMultiplier: Double = 0.25,
    val guardKnockbackMultiplier: Double = 0.10,
    val rememberedActionCount: Int = 64,
) {
    init {
        require(thrustRange > 0.0)
        require(minimumFacingDot in -1.0..1.0)
        require(thrustRecoveryTicks >= 0 && braceWindupTicks >= 0 && braceRecoveryTicks >= 0)
        require(baseBraceActiveTicks > 0 && barbedBraceActiveTicks > 0 && guardBraceActiveTicks > 0)
        require(braceDamageMultiplier in 0.0..1.0 && braceKnockbackMultiplier in 0.0..1.0)
        require(guardDamageMultiplier in 0.0..1.0 && guardKnockbackMultiplier in 0.0..1.0)
        require(rememberedActionCount > 0)
    }
}

data class TidehookThrustIntent(
    val playerId: UUID,
    val targetId: UUID,
    val actionId: Long,
    val tick: Long,
    val eye: CombatPoint,
    val lookDirection: CombatPoint,
    val targetBounds: CombatBounds,
    val blockers: Collection<CombatBounds> = emptyList(),
    val correctItem: Boolean,
    val correctZone: Boolean,
    val registeredTarget: Boolean,
    val alive: Boolean,
    val targetExposed: Boolean,
    val mod: TidehookMod = TidehookMod.NONE,
)

data class TidehookBraceIntent(
    val playerId: UUID,
    val actionId: Long,
    val tick: Long,
    val hand: TidehookHand,
    val correctItem: Boolean,
    val correctZone: Boolean,
    val alive: Boolean,
    val mod: TidehookMod = TidehookMod.NONE,
)

data class TidehookThrustResult(
    val accepted: Boolean,
    val reason: TidehookRejectReason? = null,
    val targetId: UUID? = null,
    val actionId: Long? = null,
    val damageMultiplier: Double = 0.0,
)

data class TidehookBraceResult(
    val accepted: Boolean,
    val reason: TidehookRejectReason? = null,
    val activeFromTick: Long? = null,
    val activeUntilTickExclusive: Long? = null,
)

data class ChargeMitigation(
    val braced: Boolean,
    val damageMultiplier: Double,
    val knockbackMultiplier: Double,
)

class TidehookCombat(
    private val config: TidehookConfig = TidehookConfig(),
) {
    private val players = mutableMapOf<UUID, PlayerAction>()
    private val rememberedActions = mutableMapOf<UUID, RememberedActions>()

    fun actionState(playerId: UUID, tick: Long): TidehookActionState {
        val action = players[playerId] ?: return TidehookActionState.READY
        val state = action.stateAt(tick)
        if (state == TidehookActionState.READY) players.remove(playerId)
        return state
    }

    fun requestThrust(intent: TidehookThrustIntent): TidehookThrustResult {
        if (!remember(intent.playerId, intent.actionId)) return rejectedThrust(TidehookRejectReason.DUPLICATE_ACTION)
        if (!intent.correctItem) return rejectedThrust(TidehookRejectReason.WRONG_ITEM)
        if (!intent.correctZone) return rejectedThrust(TidehookRejectReason.WRONG_ZONE)
        if (!intent.registeredTarget) return rejectedThrust(TidehookRejectReason.INVALID_TARGET)
        if (!intent.alive) return rejectedThrust(TidehookRejectReason.NOT_ALIVE)
        if (actionState(intent.playerId, intent.tick) != TidehookActionState.READY) {
            return rejectedThrust(TidehookRejectReason.ACTION_RECOVERY)
        }
        if (intent.targetBounds.distanceTo(intent.eye) > config.thrustRange) {
            return rejectedThrust(TidehookRejectReason.OUT_OF_RANGE)
        }
        if (!CombatGeometry.isWithinFacing(intent.eye, intent.lookDirection, intent.targetBounds, config.minimumFacingDot)) {
            return rejectedThrust(TidehookRejectReason.NOT_FACING)
        }
        if (!CombatGeometry.hasSampledLineOfSight(intent.eye, intent.targetBounds, intent.blockers)) {
            return rejectedThrust(TidehookRejectReason.LINE_OF_SIGHT_BLOCKED)
        }

        players[intent.playerId] = PlayerAction.ThrustRecovery(intent.tick + config.thrustRecoveryTicks)
        val modMultiplier = when (intent.mod) {
            TidehookMod.GUARD_RING -> 0.90
            TidehookMod.BARBED_POINT -> if (intent.targetExposed) 1.20 else 1.0
            TidehookMod.NONE -> 1.0
        }
        return TidehookThrustResult(
            accepted = true,
            targetId = intent.targetId,
            actionId = intent.actionId,
            damageMultiplier = modMultiplier,
        )
    }

    fun requestBrace(intent: TidehookBraceIntent): TidehookBraceResult {
        if (intent.hand != TidehookHand.MAIN) {
            return TidehookBraceResult(false, TidehookRejectReason.OFFHAND_IGNORED)
        }
        if (!remember(intent.playerId, intent.actionId)) {
            return TidehookBraceResult(false, TidehookRejectReason.DUPLICATE_ACTION)
        }
        if (!intent.correctItem) return TidehookBraceResult(false, TidehookRejectReason.WRONG_ITEM)
        if (!intent.correctZone) return TidehookBraceResult(false, TidehookRejectReason.WRONG_ZONE)
        if (!intent.alive) return TidehookBraceResult(false, TidehookRejectReason.NOT_ALIVE)
        if (actionState(intent.playerId, intent.tick) != TidehookActionState.READY) {
            return TidehookBraceResult(false, TidehookRejectReason.ACTION_RECOVERY)
        }

        val activeTicks = when (intent.mod) {
            TidehookMod.BARBED_POINT -> config.barbedBraceActiveTicks
            TidehookMod.GUARD_RING -> config.guardBraceActiveTicks
            TidehookMod.NONE -> config.baseBraceActiveTicks
        }
        val activeFrom = intent.tick + config.braceWindupTicks
        val activeUntil = activeFrom + activeTicks
        players[intent.playerId] = PlayerAction.Brace(
            activeFromTick = activeFrom,
            activeUntilTickExclusive = activeUntil,
            recoveryUntilTickExclusive = activeUntil + config.braceRecoveryTicks,
            mod = intent.mod,
        )
        return TidehookBraceResult(
            accepted = true,
            activeFromTick = activeFrom,
            activeUntilTickExclusive = activeUntil,
        )
    }

    fun resolveIncomingCharge(playerId: UUID, tick: Long): ChargeMitigation {
        val action = players[playerId]
        if (action !is PlayerAction.Brace || action.stateAt(tick) != TidehookActionState.BRACE_ACTIVE) {
            return ChargeMitigation(false, 1.0, 1.0)
        }

        val result = when (action.mod) {
            TidehookMod.GUARD_RING -> ChargeMitigation(true, config.guardDamageMultiplier, config.guardKnockbackMultiplier)
            TidehookMod.NONE,
            TidehookMod.BARBED_POINT,
            -> ChargeMitigation(true, config.braceDamageMultiplier, config.braceKnockbackMultiplier)
        }
        players[playerId] = PlayerAction.BraceRecovery(tick + config.braceRecoveryTicks)
        return result
    }

    fun clearPlayer(playerId: UUID) {
        players.remove(playerId)
        rememberedActions.remove(playerId)
    }

    fun reset() {
        players.clear()
        rememberedActions.clear()
    }

    private fun remember(playerId: UUID, actionId: Long): Boolean =
        rememberedActions.getOrPut(playerId) { RememberedActions(config.rememberedActionCount) }.add(actionId)

    private fun rejectedThrust(reason: TidehookRejectReason) = TidehookThrustResult(false, reason)
}

private sealed interface PlayerAction {
    fun stateAt(tick: Long): TidehookActionState

    data class ThrustRecovery(val untilTickExclusive: Long) : PlayerAction {
        override fun stateAt(tick: Long): TidehookActionState =
            if (tick < untilTickExclusive) TidehookActionState.THRUST_RECOVERY else TidehookActionState.READY
    }

    data class Brace(
        val activeFromTick: Long,
        val activeUntilTickExclusive: Long,
        val recoveryUntilTickExclusive: Long,
        val mod: TidehookMod,
    ) : PlayerAction {
        override fun stateAt(tick: Long): TidehookActionState = when {
            tick < activeFromTick -> TidehookActionState.BRACE_WINDUP
            tick < activeUntilTickExclusive -> TidehookActionState.BRACE_ACTIVE
            tick < recoveryUntilTickExclusive -> TidehookActionState.BRACE_RECOVERY
            else -> TidehookActionState.READY
        }
    }

    data class BraceRecovery(val untilTickExclusive: Long) : PlayerAction {
        override fun stateAt(tick: Long): TidehookActionState =
            if (tick < untilTickExclusive) TidehookActionState.BRACE_RECOVERY else TidehookActionState.READY
    }
}

private class RememberedActions(
    private val capacity: Int,
) {
    private val order = ArrayDeque<Long>()
    private val values = mutableSetOf<Long>()

    fun add(actionId: Long): Boolean {
        if (!values.add(actionId)) return false
        order.addLast(actionId)
        while (order.size > capacity) {
            values.remove(order.removeFirst())
        }
        return true
    }
}
