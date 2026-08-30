package dev.projects.server.experiment.swarm.loop

import dev.projects.server.experiment.swarm.world.TidebreakTargetKind
import dev.projects.server.experiment.swarm.world.TidebreakWorldSpec
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class TargetInteractionAttempt(
    val playerId: UUID,
    val targetId: String,
    val playerX: Double,
    val playerY: Double,
    val playerZ: Double,
    val hasLineOfSight: Boolean,
    val serverTick: Long,
)

/** Spatial validator for the fixed Ore, record, and Coupler targets; it registers no events. */
class SwarmWorldInteractions(
    private val loop: SwarmLoopService,
    private val oreCooldownTicks: Long = 40,
) {
    private val nextOreTick = ConcurrentHashMap<HarvestKey, Long>()

    fun harvestOre(attempt: TargetInteractionAttempt): LoopOperationResult {
        val validation = validate(attempt, TidebreakTargetKind.ORE_NODE)
        if (validation != null) return validation
        val key = HarvestKey(attempt.playerId, attempt.targetId)
        val nextAllowed = nextOreTick[key] ?: Long.MIN_VALUE
        if (attempt.serverTick < nextAllowed) {
            return LoopOperationResult(LoopStatus.COOLDOWN, loop.snapshot(attempt.playerId))
        }
        val result = loop.grantHarvestedOre(attempt.playerId)
        if (result.status == LoopStatus.APPLIED) nextOreTick[key] = attempt.serverTick + oreCooldownTicks
        return result
    }

    fun inspectSupplierRecord(attempt: TargetInteractionAttempt): LoopOperationResult {
        val validation = validate(attempt, TidebreakTargetKind.SUPPLIER_RECORD)
        if (validation != null) return validation
        val record = when (attempt.targetId) {
            TidebreakWorldSpec.supplierRecords[0].targetId -> SupplierRecord.TIDAL_FLAT
            TidebreakWorldSpec.supplierRecords[1].targetId -> SupplierRecord.QUARRY
            else -> return LoopOperationResult(LoopStatus.UNKNOWN_TARGET, loop.snapshot(attempt.playerId))
        }
        return loop.inspectSupplierRecord(attempt.playerId, record)
    }

    fun craftAndInstallCoupler(attempt: TargetInteractionAttempt): LoopOperationResult {
        val validation = validate(attempt, TidebreakTargetKind.COUPLER_RACK)
        if (validation != null) return validation
        return loop.craftAndInstallCoupler(attempt.playerId)
    }

    private fun validate(
        attempt: TargetInteractionAttempt,
        expectedKind: TidebreakTargetKind,
    ): LoopOperationResult? {
        val target = TidebreakWorldSpec.targetsById[attempt.targetId]
            ?: return LoopOperationResult(LoopStatus.UNKNOWN_TARGET, loop.snapshot(attempt.playerId))
        if (target.kind != expectedKind) {
            return LoopOperationResult(LoopStatus.UNKNOWN_TARGET, loop.snapshot(attempt.playerId))
        }
        if (!attempt.hasLineOfSight) {
            return LoopOperationResult(LoopStatus.NO_LINE_OF_SIGHT, loop.snapshot(attempt.playerId))
        }
        val dx = attempt.playerX - target.position.x()
        val dy = attempt.playerY - target.position.y()
        val dz = attempt.playerZ - target.position.z()
        if (dx * dx + dy * dy + dz * dz > target.maxInteractionDistance * target.maxInteractionDistance) {
            return LoopOperationResult(LoopStatus.TOO_FAR, loop.snapshot(attempt.playerId))
        }
        return null
    }

    private data class HarvestKey(val playerId: UUID, val targetId: String)
}
