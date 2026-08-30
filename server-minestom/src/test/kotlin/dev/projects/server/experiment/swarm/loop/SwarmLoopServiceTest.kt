package dev.projects.server.experiment.swarm.loop

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SwarmLoopServiceTest {
    @Test
    fun `fresh initial grant persists exactly once`() {
        val store = MemorySnapshotStore()
        val playerId = UUID.randomUUID()
        val first = SwarmLoopService(store)

        val created = assertIs<PlayerLoadResult.Ready>(first.loadPlayer(playerId))
        assertTrue(created.created)
        assertEquals(12, created.snapshot.scrip)

        val reloaded = assertIs<PlayerLoadResult.Ready>(SwarmLoopService(store).loadPlayer(playerId))
        assertFalse(reloaded.created)
        assertEquals(12, reloaded.snapshot.scrip)
        assertEquals(created.snapshot, reloaded.snapshot)
    }

    @Test
    fun `hunter route completes with fixed negative-arbitrage economy`() {
        val fixture = Fixture(ProcurementRoute.HUNTER)
        repeat(4) { assertEquals(LoopStatus.APPLIED, fixture.service.grantBrineclawCord(fixture.playerId).status) }
        assertTrue(fixture.snapshot().routeObjectiveComplete)

        assertEquals(LoopStatus.APPLIED, fixture.service.sell(fixture.playerId, SwarmResource.CORD, 2).status)
        assertEquals(LoopStatus.APPLIED, fixture.service.buy(fixture.playerId, SwarmResource.ORE, 2).status)
        val installed = fixture.service.craftAndInstallCoupler(fixture.playerId)

        assertEquals(LoopStatus.APPLIED, installed.status)
        assertEquals(0, installed.snapshot?.scrip)
        assertEquals(0, installed.snapshot?.ore)
        assertEquals(0, installed.snapshot?.cord)
        assertEquals(FittingState.PREPARED, installed.snapshot?.fittingState)
    }

    @Test
    fun `gatherer and supplier each have a solo completion path`() {
        val gatherer = Fixture(ProcurementRoute.GATHERER)
        repeat(4) { gatherer.service.grantHarvestedOre(gatherer.playerId) }
        gatherer.service.sell(gatherer.playerId, SwarmResource.ORE, 2)
        gatherer.service.buy(gatherer.playerId, SwarmResource.CORD, 2)
        assertEquals(LoopStatus.APPLIED, gatherer.service.craftAndInstallCoupler(gatherer.playerId).status)

        val supplier = Fixture(ProcurementRoute.SUPPLIER)
        supplier.service.inspectSupplierRecord(supplier.playerId, SupplierRecord.TIDAL_FLAT)
        supplier.service.inspectSupplierRecord(supplier.playerId, SupplierRecord.QUARRY)
        assertEquals(LoopStatus.APPLIED, supplier.service.claimSupplierCommission(supplier.playerId).status)
        val commissioned = supplier.snapshot()
        assertEquals(LoopStatus.ALREADY_APPLIED, supplier.service.claimSupplierCommission(supplier.playerId).status)
        assertEquals(commissioned, supplier.snapshot())
        supplier.service.buy(supplier.playerId, SwarmResource.ORE, 2)
        supplier.service.buy(supplier.playerId, SwarmResource.CORD, 2)
        val installed = supplier.service.craftAndInstallCoupler(supplier.playerId)
        assertEquals(LoopStatus.APPLIED, installed.status)
        assertEquals(0, installed.snapshot?.scrip)
    }

    @Test
    fun `buy sell round trip always loses four scrip`() {
        val fixture = Fixture(ProcurementRoute.HUNTER)
        val before = fixture.snapshot().scrip

        fixture.service.buy(fixture.playerId, SwarmResource.ORE)
        fixture.service.sell(fixture.playerId, SwarmResource.ORE)

        assertEquals(before - 4, fixture.snapshot().scrip)
        assertEquals(0, fixture.snapshot().ore)
    }

    @Test
    fun `coupler reward mod and completion are idempotent and no respec is allowed`() {
        val fixture = preparedHunter()
        val afterCoupler = fixture.snapshot()
        assertEquals(LoopStatus.ALREADY_APPLIED, fixture.service.craftAndInstallCoupler(fixture.playerId).status)
        assertEquals(afterCoupler, fixture.snapshot())

        assertEquals(LoopStatus.APPLIED, fixture.service.claimBossVictory(fixture.playerId).status)
        val afterReward = fixture.snapshot()
        assertEquals(LoopStatus.ALREADY_APPLIED, fixture.service.claimBossVictory(fixture.playerId).status)
        assertEquals(afterReward, fixture.snapshot())

        assertEquals(LoopStatus.APPLIED, fixture.service.installMod(fixture.playerId, TidehookModChoice.BARBED_POINT).status)
        assertEquals(1.20, fixture.service.fittingEffects(fixture.playerId).exposedDamageMultiplier)
        assertEquals(LoopStatus.INVALID_STATE, fixture.service.installMod(fixture.playerId, TidehookModChoice.GUARD_RING).status)
        assertEquals(LoopStatus.APPLIED, fixture.service.reportCompletion(fixture.playerId).status)
        val complete = fixture.snapshot()
        assertEquals(LoopStatus.ALREADY_APPLIED, fixture.service.reportCompletion(fixture.playerId).status)
        assertEquals(complete, fixture.snapshot())
    }

    @Test
    fun `persistence failure rolls back the complete in-memory delta`() {
        val fixture = Fixture(ProcurementRoute.HUNTER)
        val before = fixture.snapshot()
        fixture.store.failNextSave = true

        val result = fixture.service.grantBrineclawCord(fixture.playerId)

        assertEquals(LoopStatus.PERSISTENCE_FAILED, result.status)
        assertEquals(before, result.snapshot)
        assertEquals(before, fixture.snapshot())
        assertEquals(before, fixture.store.snapshots.getValue(fixture.playerId))
    }

    @Test
    fun `two players remain isolated`() {
        val store = MemorySnapshotStore()
        val service = SwarmLoopService(store)
        val hunter = UUID.randomUUID()
        val gatherer = UUID.randomUUID()
        service.loadPlayer(hunter)
        service.loadPlayer(gatherer)
        service.selectRoute(hunter, ProcurementRoute.HUNTER)
        service.selectRoute(gatherer, ProcurementRoute.GATHERER)

        service.grantBrineclawCord(hunter)
        service.grantHarvestedOre(gatherer)

        assertEquals(1, service.snapshot(hunter)?.cord)
        assertEquals(0, service.snapshot(hunter)?.ore)
        assertEquals(0, service.snapshot(gatherer)?.cord)
        assertEquals(1, service.snapshot(gatherer)?.ore)
        assertNotEquals(service.snapshot(hunter), service.snapshot(gatherer))
    }

    private fun preparedHunter(): Fixture = Fixture(ProcurementRoute.HUNTER).also { fixture ->
        repeat(4) { fixture.service.grantBrineclawCord(fixture.playerId) }
        fixture.service.sell(fixture.playerId, SwarmResource.CORD, 2)
        fixture.service.buy(fixture.playerId, SwarmResource.ORE, 2)
        fixture.service.craftAndInstallCoupler(fixture.playerId)
    }

    private class Fixture(route: ProcurementRoute) {
        val store = MemorySnapshotStore()
        val service = SwarmLoopService(store)
        val playerId = UUID.randomUUID()

        init {
            service.loadPlayer(playerId)
            service.selectRoute(playerId, route)
        }

        fun snapshot(): SwarmPlayerSnapshot = requireNotNull(service.snapshot(playerId))
    }
}

private class MemorySnapshotStore : SwarmSnapshotStore {
    val snapshots = mutableMapOf<UUID, SwarmPlayerSnapshot>()
    var failNextSave: Boolean = false

    override fun load(playerId: UUID): SnapshotLoadResult =
        snapshots[playerId]?.let(SnapshotLoadResult::Loaded) ?: SnapshotLoadResult.Missing

    override fun save(playerId: UUID, snapshot: SwarmPlayerSnapshot): Boolean {
        if (failNextSave) {
            failNextSave = false
            return false
        }
        snapshots[playerId] = snapshot
        return true
    }
}
