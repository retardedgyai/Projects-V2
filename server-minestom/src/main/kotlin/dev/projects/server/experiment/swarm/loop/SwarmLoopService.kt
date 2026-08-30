package dev.projects.server.experiment.swarm.loop

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class LoopStatus {
    APPLIED,
    ALREADY_APPLIED,
    NOT_LOADED,
    INVALID_PROFILE,
    INVALID_STATE,
    WRONG_ROUTE,
    OBJECTIVE_INCOMPLETE,
    INSUFFICIENT_RESOURCES,
    CAPACITY_EXCEEDED,
    PERSISTENCE_FAILED,
    UNKNOWN_TARGET,
    TOO_FAR,
    NO_LINE_OF_SIGHT,
    COOLDOWN,
}

data class LoopOperationResult(
    val status: LoopStatus,
    val snapshot: SwarmPlayerSnapshot? = null,
) {
    val accepted: Boolean get() = status == LoopStatus.APPLIED || status == LoopStatus.ALREADY_APPLIED
}

sealed interface PlayerLoadResult {
    data class Ready(val snapshot: SwarmPlayerSnapshot, val created: Boolean) : PlayerLoadResult
    data class Invalid(val reason: String) : PlayerLoadResult
    data object PersistenceFailed : PlayerLoadResult
}

/** Atomic per-player transaction boundary used by NPC, world, and encounter wiring. */
class SwarmLoopService(
    private val store: SwarmSnapshotStore,
) {
    private val snapshots = ConcurrentHashMap<UUID, SwarmPlayerSnapshot>()
    private val locks = ConcurrentHashMap<UUID, Any>()

    fun loadPlayer(playerId: UUID): PlayerLoadResult = synchronized(lockFor(playerId)) {
        when (val loaded = store.load(playerId)) {
            SnapshotLoadResult.Missing -> {
                val fresh = SwarmPlayerSnapshot.fresh()
                if (!store.save(playerId, fresh)) {
                    snapshots.remove(playerId)
                    return@synchronized PlayerLoadResult.PersistenceFailed
                }
                snapshots[playerId] = fresh
                PlayerLoadResult.Ready(fresh, created = true)
            }
            is SnapshotLoadResult.Loaded -> {
                snapshots[playerId] = loaded.snapshot
                PlayerLoadResult.Ready(loaded.snapshot, created = false)
            }
            is SnapshotLoadResult.Invalid -> {
                snapshots.remove(playerId)
                PlayerLoadResult.Invalid(loaded.reason)
            }
        }
    }

    fun unloadPlayer(playerId: UUID) {
        snapshots.remove(playerId)
        locks.remove(playerId)
    }

    fun snapshot(playerId: UUID): SwarmPlayerSnapshot? = snapshots[playerId]

    fun preparedForEncounter(playerId: UUID): Boolean = snapshots[playerId]?.preparedForEncounter == true

    fun fittingEffects(playerId: UUID): TidehookFittingEffects =
        snapshots[playerId]?.fittingEffects ?: TidehookFittingEffects()

    fun selectRoute(playerId: UUID, route: ProcurementRoute): LoopOperationResult = transact(playerId) { current ->
        when (current.selectedRoute) {
            route -> Decision.already(current)
            null -> Decision.apply(current.copy(selectedRoute = route, questStage = QuestStage.ROUTE_SELECTED))
            else -> Decision.reject(LoopStatus.INVALID_STATE, current)
        }
    }

    /** Called only after Lane B confirms Brineclaw defeat participation. */
    fun grantBrineclawCord(playerId: UUID): LoopOperationResult = transact(playerId) { current ->
        if (current.selectedRoute == null) return@transact Decision.reject(LoopStatus.INVALID_STATE, current)
        if (current.cord >= SwarmPlayerSnapshot.MAX_RESOURCE) {
            return@transact Decision.reject(LoopStatus.CAPACITY_EXCEEDED, current)
        }
        Decision.apply(
            current.copy(
                cord = current.cord + 1,
                brineclawTrainingDefeats = (current.brineclawTrainingDefeats + 1)
                    .coerceAtMost(SwarmPlayerSnapshot.BRINECLAW_TRAINING_OBJECTIVE),
                hunterCordEarned = if (current.selectedRoute == ProcurementRoute.HUNTER) {
                    (current.hunterCordEarned + 1).coerceAtMost(SwarmPlayerSnapshot.HUNTER_OBJECTIVE)
                } else {
                    current.hunterCordEarned
                },
            ),
        )
    }

    /** Called only after a registered Ore target passes spatial validation. */
    fun grantHarvestedOre(playerId: UUID, objectiveNodeIndex: Int): LoopOperationResult = transact(playerId) { current ->
        if (current.selectedRoute == null) return@transact Decision.reject(LoopStatus.INVALID_STATE, current)
        if (objectiveNodeIndex !in 0 until SwarmPlayerSnapshot.ORE_NODE_COUNT) {
            return@transact Decision.reject(LoopStatus.UNKNOWN_TARGET, current)
        }
        if (current.ore >= SwarmPlayerSnapshot.MAX_RESOURCE) {
            return@transact Decision.reject(LoopStatus.CAPACITY_EXCEEDED, current)
        }
        val nodeBit = 1 shl objectiveNodeIndex
        val isNewGathererNode = current.selectedRoute == ProcurementRoute.GATHERER &&
            (current.gathererOreNodeMask and nodeBit) == 0
        Decision.apply(
            current.copy(
                ore = current.ore + 1,
                gathererOreEarned = if (isNewGathererNode) {
                    (current.gathererOreEarned + 1).coerceAtMost(SwarmPlayerSnapshot.GATHERER_OBJECTIVE)
                } else {
                    current.gathererOreEarned
                },
                gathererOreNodeMask = if (isNewGathererNode) current.gathererOreNodeMask or nodeBit else current.gathererOreNodeMask,
            ),
        )
    }

    fun inspectSupplierRecord(playerId: UUID, record: SupplierRecord): LoopOperationResult = transact(playerId) { current ->
        if (current.selectedRoute != ProcurementRoute.SUPPLIER) {
            return@transact Decision.reject(LoopStatus.WRONG_ROUTE, current)
        }
        if ((current.supplierRecordMask and record.bit) != 0) return@transact Decision.already(current)
        Decision.apply(current.copy(supplierRecordMask = current.supplierRecordMask or record.bit))
    }

    fun claimSupplierCommission(playerId: UUID): LoopOperationResult = transact(playerId) { current ->
        if (current.selectedRoute != ProcurementRoute.SUPPLIER) {
            return@transact Decision.reject(LoopStatus.WRONG_ROUTE, current)
        }
        if (current.supplierCommissionClaimed) return@transact Decision.already(current)
        if (current.supplierRecordMask != SwarmPlayerSnapshot.ALL_SUPPLIER_RECORDS) {
            return@transact Decision.reject(LoopStatus.OBJECTIVE_INCOMPLETE, current)
        }
        val newScrip = current.scrip + SUPPLIER_COMMISSION
        if (newScrip > SwarmPlayerSnapshot.MAX_SCRIP) {
            return@transact Decision.reject(LoopStatus.CAPACITY_EXCEEDED, current)
        }
        Decision.apply(current.copy(scrip = newScrip, supplierCommissionClaimed = true))
    }

    fun buy(playerId: UUID, resource: SwarmResource, quantity: Int = 1): LoopOperationResult =
        exchange(playerId, resource, quantity, buying = true)

    fun sell(playerId: UUID, resource: SwarmResource, quantity: Int = 1): LoopOperationResult =
        exchange(playerId, resource, quantity, buying = false)

    fun craftAndInstallCoupler(playerId: UUID): LoopOperationResult = transact(playerId) { current ->
        if (current.fittingState != FittingState.NONE) return@transact Decision.already(current)
        if (!current.routeObjectiveComplete) {
            return@transact Decision.reject(LoopStatus.OBJECTIVE_INCOMPLETE, current)
        }
        if (current.ore < COUPLER_ORE || current.cord < COUPLER_CORD || current.scrip < COUPLER_SCRIP) {
            return@transact Decision.reject(LoopStatus.INSUFFICIENT_RESOURCES, current)
        }
        Decision.apply(
            current.copy(
                ore = current.ore - COUPLER_ORE,
                cord = current.cord - COUPLER_CORD,
                scrip = current.scrip - COUPLER_SCRIP,
                fittingState = FittingState.PREPARED,
                questStage = QuestStage.COUPLER_INSTALLED,
            ),
        )
    }

    /** Narrow reward boundary for Lane B; encounter eligibility remains Lane B authority. */
    fun claimBossVictory(playerId: UUID): LoopOperationResult = transact(playerId) { current ->
        if (current.firstClearClaimed) return@transact Decision.already(current)
        if (!current.brineclawTrainingComplete) {
            return@transact Decision.reject(LoopStatus.OBJECTIVE_INCOMPLETE, current)
        }
        if (current.fittingState != FittingState.PREPARED || current.questStage != QuestStage.COUPLER_INSTALLED) {
            return@transact Decision.reject(LoopStatus.INVALID_STATE, current)
        }
        Decision.apply(
            current.copy(
                fittingState = FittingState.CATALYST_READY,
                questStage = QuestStage.BOSS_CLEARED,
                firstClearClaimed = true,
            ),
        )
    }

    fun installMod(playerId: UUID, choice: TidehookModChoice): LoopOperationResult = transact(playerId) { current ->
        val requested = when (choice) {
            TidehookModChoice.BARBED_POINT -> FittingState.MOD_BARBED
            TidehookModChoice.GUARD_RING -> FittingState.MOD_GUARD
        }
        if (current.fittingState == requested) return@transact Decision.already(current)
        if (current.fittingState == FittingState.MOD_BARBED || current.fittingState == FittingState.MOD_GUARD) {
            return@transact Decision.reject(LoopStatus.INVALID_STATE, current)
        }
        if (current.fittingState != FittingState.CATALYST_READY || current.questStage != QuestStage.BOSS_CLEARED) {
            return@transact Decision.reject(LoopStatus.INVALID_STATE, current)
        }
        Decision.apply(current.copy(fittingState = requested, questStage = QuestStage.MOD_INSTALLED))
    }

    fun reportCompletion(playerId: UUID): LoopOperationResult = transact(playerId) { current ->
        if (current.questStage == QuestStage.COMPLETE) return@transact Decision.already(current)
        if (current.questStage != QuestStage.MOD_INSTALLED) {
            return@transact Decision.reject(LoopStatus.INVALID_STATE, current)
        }
        Decision.apply(current.copy(questStage = QuestStage.COMPLETE))
    }

    private fun exchange(
        playerId: UUID,
        resource: SwarmResource,
        quantity: Int,
        buying: Boolean,
    ): LoopOperationResult {
        if (quantity <= 0 || quantity > SwarmPlayerSnapshot.MAX_RESOURCE) {
            return LoopOperationResult(LoopStatus.INVALID_STATE, snapshots[playerId])
        }
        return transact(playerId) { current ->
            if (current.selectedRoute == null) return@transact Decision.reject(LoopStatus.INVALID_STATE, current)
            val currentAmount = if (resource == SwarmResource.ORE) current.ore else current.cord
            val unitPrice = if (buying) BUY_PRICE else SELL_PRICE
            val total = unitPrice * quantity
            if (buying) {
                if (current.scrip < total) return@transact Decision.reject(LoopStatus.INSUFFICIENT_RESOURCES, current)
                if (currentAmount + quantity > SwarmPlayerSnapshot.MAX_RESOURCE) {
                    return@transact Decision.reject(LoopStatus.CAPACITY_EXCEEDED, current)
                }
                Decision.apply(
                    current.withResource(resource, currentAmount + quantity).copy(scrip = current.scrip - total),
                )
            } else {
                if (currentAmount < quantity) return@transact Decision.reject(LoopStatus.INSUFFICIENT_RESOURCES, current)
                if (current.scrip + total > SwarmPlayerSnapshot.MAX_SCRIP) {
                    return@transact Decision.reject(LoopStatus.CAPACITY_EXCEEDED, current)
                }
                Decision.apply(
                    current.withResource(resource, currentAmount - quantity).copy(scrip = current.scrip + total),
                )
            }
        }
    }

    private fun transact(
        playerId: UUID,
        operation: (SwarmPlayerSnapshot) -> Decision,
    ): LoopOperationResult = synchronized(lockFor(playerId)) {
        val current = snapshots[playerId] ?: return@synchronized LoopOperationResult(LoopStatus.NOT_LOADED)
        val decision = operation(current)
        val candidate = decision.next ?: return@synchronized LoopOperationResult(decision.status, current)
        val next = candidate.copy(revision = current.revision + 1)
        if (!store.save(playerId, next)) {
            return@synchronized LoopOperationResult(LoopStatus.PERSISTENCE_FAILED, current)
        }
        snapshots[playerId] = next
        LoopOperationResult(LoopStatus.APPLIED, next)
    }

    private fun lockFor(playerId: UUID): Any = locks.computeIfAbsent(playerId) { Any() }

    private fun SwarmPlayerSnapshot.withResource(resource: SwarmResource, amount: Int): SwarmPlayerSnapshot =
        when (resource) {
            SwarmResource.ORE -> copy(ore = amount)
            SwarmResource.CORD -> copy(cord = amount)
        }

    private data class Decision(
        val status: LoopStatus,
        val next: SwarmPlayerSnapshot? = null,
    ) {
        companion object {
            fun apply(next: SwarmPlayerSnapshot) = Decision(LoopStatus.APPLIED, next)
            fun already(current: SwarmPlayerSnapshot) = Decision(LoopStatus.ALREADY_APPLIED, null)
            fun reject(status: LoopStatus, current: SwarmPlayerSnapshot) = Decision(status, null)
        }
    }

    companion object {
        const val BUY_PRICE = 6
        const val SELL_PRICE = 2
        const val SUPPLIER_COMMISSION = 16
        const val COUPLER_ORE = 2
        const val COUPLER_CORD = 2
        const val COUPLER_SCRIP = 4
    }
}
