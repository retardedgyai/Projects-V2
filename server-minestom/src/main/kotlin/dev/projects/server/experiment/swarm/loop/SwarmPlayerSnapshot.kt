package dev.projects.server.experiment.swarm.loop

enum class ProcurementRoute {
    HUNTER,
    GATHERER,
    SUPPLIER,
}

enum class QuestStage {
    ROUTE_SELECTED,
    COUPLER_INSTALLED,
    BOSS_CLEARED,
    MOD_INSTALLED,
    COMPLETE,
}

enum class FittingState {
    NONE,
    PREPARED,
    CATALYST_READY,
    MOD_BARBED,
    MOD_GUARD,
}

enum class SwarmResource {
    ORE,
    CORD,
}

enum class TidehookModChoice {
    BARBED_POINT,
    GUARD_RING,
}

enum class SupplierRecord(val bit: Int) {
    TIDAL_FLAT(1),
    QUARRY(2),
}

data class TidehookFittingEffects(
    val thrustDamageMultiplier: Double = 1.0,
    val exposedDamageMultiplier: Double = 1.0,
    val braceActiveTicksDelta: Int = 0,
    val chargeKnockbackMultiplier: Double = 1.0,
)

/** Complete bounded, immutable player authority for the experiment. */
data class SwarmPlayerSnapshot(
    val revision: Long,
    val initialGrantClaimed: Boolean,
    val scrip: Int,
    val ore: Int,
    val cord: Int,
    val selectedRoute: ProcurementRoute?,
    val hunterCordEarned: Int,
    val gathererOreEarned: Int,
    val supplierRecordMask: Int,
    val supplierCommissionClaimed: Boolean,
    val fittingState: FittingState,
    val questStage: QuestStage?,
    val firstClearClaimed: Boolean,
) {
    init {
        require(revision >= 0) { "Revision must not be negative" }
        require(initialGrantClaimed) { "Persisted snapshots must contain the exactly-once initial grant" }
        require(scrip in 0..MAX_SCRIP) { "Scrip out of range" }
        require(ore in 0..MAX_RESOURCE) { "Ore out of range" }
        require(cord in 0..MAX_RESOURCE) { "Cord out of range" }
        require(hunterCordEarned in 0..HUNTER_OBJECTIVE) { "Hunter progress out of range" }
        require(gathererOreEarned in 0..GATHERER_OBJECTIVE) { "Gatherer progress out of range" }
        require(supplierRecordMask in 0..ALL_SUPPLIER_RECORDS) { "Supplier record mask out of range" }

        if (selectedRoute == null) {
            require(questStage == null) { "Unselected player cannot have a quest stage" }
            require(hunterCordEarned == 0 && gathererOreEarned == 0 && supplierRecordMask == 0) {
                "Unselected player cannot have route progress"
            }
            require(!supplierCommissionClaimed && fittingState == FittingState.NONE && !firstClearClaimed) {
                "Unselected player cannot have completion state"
            }
        } else {
            require(questStage != null) { "Selected route requires a quest stage" }
            require(selectedRoute == ProcurementRoute.HUNTER || hunterCordEarned == 0) {
                "Hunter progress belongs only to Hunter"
            }
            require(selectedRoute == ProcurementRoute.GATHERER || gathererOreEarned == 0) {
                "Gatherer progress belongs only to Gatherer"
            }
            require(selectedRoute == ProcurementRoute.SUPPLIER || supplierRecordMask == 0) {
                "Supplier records belong only to Supplier"
            }
            require(selectedRoute == ProcurementRoute.SUPPLIER || !supplierCommissionClaimed) {
                "Supplier commission belongs only to Supplier"
            }
        }

        when (questStage) {
            null, QuestStage.ROUTE_SELECTED -> {
                require(fittingState == FittingState.NONE && !firstClearClaimed) {
                    "Route-selected state cannot own fitting or clear rewards"
                }
            }
            QuestStage.COUPLER_INSTALLED -> {
                require(fittingState == FittingState.PREPARED && !firstClearClaimed) {
                    "Installed Coupler must be prepared without a clear"
                }
            }
            QuestStage.BOSS_CLEARED -> {
                require(fittingState == FittingState.CATALYST_READY && firstClearClaimed) {
                    "Boss-cleared state must own the catalyst"
                }
            }
            QuestStage.MOD_INSTALLED, QuestStage.COMPLETE -> {
                require(fittingState == FittingState.MOD_BARBED || fittingState == FittingState.MOD_GUARD) {
                    "MOD stage requires an installed MOD"
                }
                require(firstClearClaimed) { "MOD stage requires a first clear" }
            }
        }
    }

    val routeObjectiveComplete: Boolean
        get() = when (selectedRoute) {
            ProcurementRoute.HUNTER -> hunterCordEarned >= HUNTER_OBJECTIVE
            ProcurementRoute.GATHERER -> gathererOreEarned >= GATHERER_OBJECTIVE
            ProcurementRoute.SUPPLIER -> supplierRecordMask == ALL_SUPPLIER_RECORDS && supplierCommissionClaimed
            null -> false
        }

    val preparedForEncounter: Boolean
        get() = fittingState != FittingState.NONE

    val fittingEffects: TidehookFittingEffects
        get() = when (fittingState) {
            FittingState.MOD_BARBED -> TidehookFittingEffects(
                exposedDamageMultiplier = 1.20,
                braceActiveTicksDelta = -2,
            )
            FittingState.MOD_GUARD -> TidehookFittingEffects(
                thrustDamageMultiplier = 0.90,
                braceActiveTicksDelta = 4,
                chargeKnockbackMultiplier = 0.50,
            )
            else -> TidehookFittingEffects()
        }

    companion object {
        const val MAX_SCRIP = 999
        const val MAX_RESOURCE = 99
        const val INITIAL_SCRIP = 12
        const val HUNTER_OBJECTIVE = 4
        const val GATHERER_OBJECTIVE = 4
        const val ALL_SUPPLIER_RECORDS = 3

        fun fresh(): SwarmPlayerSnapshot = SwarmPlayerSnapshot(
            revision = 0,
            initialGrantClaimed = true,
            scrip = INITIAL_SCRIP,
            ore = 0,
            cord = 0,
            selectedRoute = null,
            hunterCordEarned = 0,
            gathererOreEarned = 0,
            supplierRecordMask = 0,
            supplierCommissionClaimed = false,
            fittingState = FittingState.NONE,
            questStage = null,
            firstClearClaimed = false,
        )
    }
}
