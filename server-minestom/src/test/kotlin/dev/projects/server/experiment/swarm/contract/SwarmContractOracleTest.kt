package dev.projects.server.experiment.swarm.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwarmContractOracleTest {
    @Test
    fun `frozen constants keep the playable slice bounded`() {
        assertEquals(96, SwarmContractOracle.WORLD_EDGE_BLOCKS)
        assertEquals(45, SwarmContractOracle.MARKET_TRAVEL_LIMIT_SECONDS)
        assertEquals(15..30, SwarmContractOracle.BOSS_RETRY_MIN_SECONDS..SwarmContractOracle.BOSS_RETRY_MAX_SECONDS)
        assertEquals(300, SwarmContractOracle.BOSS_TIMEOUT_SECONDS)
        assertEquals(5, SwarmContractOracle.WIPE_RESET_DELAY_SECONDS)
        assertEquals(3.5, SwarmContractOracle.THRUST_RANGE_BLOCKS)
        assertEquals(10..12, SwarmContractOracle.BRACE_ACTIVE_MIN_TICKS..SwarmContractOracle.BRACE_ACTIVE_MAX_TICKS)
        assertEquals(3, SwarmContractOracle.REQUIRED_ELIGIBLE_HITS)
        assertEquals(4, SwarmContractOracle.MAX_ROSTER_SIZE)
    }

    @Test
    fun `broker round trip is always negative`() {
        val roundTripDelta = SwarmContractOracle.RESOURCE_SELL_SCRIP - SwarmContractOracle.RESOURCE_BUY_SCRIP

        assertEquals(-4, roundTripDelta)
    }

    @Test
    fun `all three fresh solo routes can pay for exactly one coupler`() {
        assertEquals(RouteArithmetic(0, 0, 0), expectedRouteArithmetic("HUNTER"))
        assertEquals(RouteArithmetic(0, 0, 0), expectedRouteArithmetic("GATHERER"))
        assertEquals(RouteArithmetic(0, 0, 0), expectedRouteArithmetic("SUPPLIER"))
    }

    @Test
    fun `persistent stages and fitting states move monotonically`() {
        assertEquals(
            listOf("ROUTE_SELECTED", "COUPLER_INSTALLED", "BOSS_CLEARED", "MOD_INSTALLED", "COMPLETE"),
            SwarmContractOracle.persistentStages,
        )
        assertEquals(
            listOf(
                "NONE" to "PREPARED",
                "PREPARED" to "CATALYST_READY",
                "CATALYST_READY" to "MOD_BARBED|MOD_GUARD",
            ),
            SwarmContractOracle.fittingTransitions,
        )
    }

    @Test
    fun `encounter reset invalidates stale work and preserves preparation`() {
        val reset = expectedEncounterReset(generation = 41)

        assertEquals(42, reset.nextGeneration)
        assertEquals(SwarmContractOracle.resetFields, reset.clearedFields)
        assertTrue(reset.preparationPreserved)
        assertFalse(reset.previousGenerationCanAct)
    }

    @Test
    fun `journey contains every required named proof point once and in order`() {
        val expected = listOf(
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

        assertEquals(expected, SwarmContractOracle.completeJourney)
        assertEquals(expected.size, expected.toSet().size)
    }
}
