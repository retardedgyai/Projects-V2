package dev.projects.server.experiment.swarm.contract

/**
 * Frozen values from CURRENT_CONTRACT.md.
 *
 * This test-only oracle intentionally has no dependency on production classes. The integration
 * lane can bind public production values to it without Lane C inventing a production API.
 */
object SwarmContractOracle {
    const val WORLD_EDGE_BLOCKS = 96
    const val MARKET_TRAVEL_LIMIT_SECONDS = 45
    const val BOSS_RETRY_MIN_SECONDS = 15
    const val BOSS_RETRY_MAX_SECONDS = 30
    const val BOSS_TIMEOUT_SECONDS = 5 * 60
    const val WIPE_RESET_DELAY_SECONDS = 5

    const val INITIAL_SCRIP = 12
    const val SUPPLIER_COMMISSION_SCRIP = 16
    const val RESOURCE_BUY_SCRIP = 6
    const val RESOURCE_SELL_SCRIP = 2
    const val COUPLER_ORE = 2
    const val COUPLER_CORD = 2
    const val COUPLER_SCRIP = 4

    const val THRUST_RANGE_BLOCKS = 3.5
    const val BRACE_ACTIVE_MIN_TICKS = 10
    const val BRACE_ACTIVE_MAX_TICKS = 12
    const val REQUIRED_ELIGIBLE_HITS = 3
    const val MAX_ROSTER_SIZE = 4

    val persistentStages = listOf(
        "ROUTE_SELECTED",
        "COUPLER_INSTALLED",
        "BOSS_CLEARED",
        "MOD_INSTALLED",
        "COMPLETE",
    )

    val fittingTransitions = listOf(
        "NONE" to "PREPARED",
        "PREPARED" to "CATALYST_READY",
        "CATALYST_READY" to "MOD_BARBED|MOD_GUARD",
    )

    val completeJourney = listOf(
        "ARRIVE",
        "SPEAK_WARDEN",
        "SELECT_ROUTE",
        "COMPLETE_ROUTE_OBJECTIVE",
        "CREATE_OR_BUY_MISSING_ORE_AND_CORD",
        "CRAFT_AND_INSTALL_COUPLER",
        "LEARN_SWEEP_AND_CHARGE",
        "ENTER_ENCOUNTER",
        "CHARGE_POWERED_CRASH_PILLAR",
        "DEFEAT_CAIRNBACK",
        "CLAIM_PRESSURE_PEARL_STATE",
        "INSTALL_ONE_MOD",
        "REPORT_COMPLETION",
    )

    val resetFields = setOf(
        "health",
        "phase",
        "vulnerability",
        "attack",
        "recovery",
        "entities",
        "hazards",
        "roster",
    )
}

data class RouteArithmetic(
    val endingScrip: Int,
    val endingOre: Int,
    val endingCord: Int,
)

fun expectedRouteArithmetic(route: String): RouteArithmetic = when (route) {
    "HUNTER" -> {
        val afterSale = SwarmContractOracle.INITIAL_SCRIP + 2 * SwarmContractOracle.RESOURCE_SELL_SCRIP
        val afterPurchase = afterSale - 2 * SwarmContractOracle.RESOURCE_BUY_SCRIP
        RouteArithmetic(
            endingScrip = afterPurchase - SwarmContractOracle.COUPLER_SCRIP,
            endingOre = 2 - SwarmContractOracle.COUPLER_ORE,
            endingCord = 2 - SwarmContractOracle.COUPLER_CORD,
        )
    }

    "GATHERER" -> {
        val afterSale = SwarmContractOracle.INITIAL_SCRIP + 2 * SwarmContractOracle.RESOURCE_SELL_SCRIP
        val afterPurchase = afterSale - 2 * SwarmContractOracle.RESOURCE_BUY_SCRIP
        RouteArithmetic(
            endingScrip = afterPurchase - SwarmContractOracle.COUPLER_SCRIP,
            endingOre = 2 - SwarmContractOracle.COUPLER_ORE,
            endingCord = 2 - SwarmContractOracle.COUPLER_CORD,
        )
    }

    "SUPPLIER" -> {
        val commissioned = SwarmContractOracle.INITIAL_SCRIP + SwarmContractOracle.SUPPLIER_COMMISSION_SCRIP
        val afterPurchases = commissioned - 4 * SwarmContractOracle.RESOURCE_BUY_SCRIP
        RouteArithmetic(
            endingScrip = afterPurchases - SwarmContractOracle.COUPLER_SCRIP,
            endingOre = 2 - SwarmContractOracle.COUPLER_ORE,
            endingCord = 2 - SwarmContractOracle.COUPLER_CORD,
        )
    }

    else -> error("Unknown frozen route: $route")
}

data class EncounterResetExpectation(
    val nextGeneration: Long,
    val clearedFields: Set<String>,
    val preparationPreserved: Boolean,
    val previousGenerationCanAct: Boolean,
)

fun expectedEncounterReset(generation: Long): EncounterResetExpectation = EncounterResetExpectation(
    nextGeneration = generation + 1,
    clearedFields = SwarmContractOracle.resetFields,
    preparationPreserved = true,
    previousGenerationCanAct = false,
)
