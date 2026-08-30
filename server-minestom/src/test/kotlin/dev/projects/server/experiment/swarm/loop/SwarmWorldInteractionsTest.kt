package dev.projects.server.experiment.swarm.loop

import dev.projects.server.experiment.swarm.world.TidebreakWorldSpec
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class SwarmWorldInteractionsTest {
    @Test
    fun `ore target validates id sight distance and cooldown before progress`() {
        val store = MemoryInteractionStore()
        val loop = SwarmLoopService(store)
        val playerId = UUID.randomUUID()
        loop.loadPlayer(playerId)
        loop.selectRoute(playerId, ProcurementRoute.GATHERER)
        val interactions = SwarmWorldInteractions(loop, oreCooldownTicks = 40)
        val node = TidebreakWorldSpec.oreNodes.first()
        val valid = attempt(playerId, node.targetId, node.position.x(), node.position.y(), node.position.z(), true, 10)

        assertEquals(LoopStatus.UNKNOWN_TARGET, interactions.harvestOre(valid.copy(targetId = "unknown")).status)
        assertEquals(LoopStatus.NO_LINE_OF_SIGHT, interactions.harvestOre(valid.copy(hasLineOfSight = false)).status)
        assertEquals(LoopStatus.TOO_FAR, interactions.harvestOre(valid.copy(playerX = node.position.x() + 10)).status)
        assertEquals(LoopStatus.APPLIED, interactions.harvestOre(valid).status)
        assertEquals(LoopStatus.COOLDOWN, interactions.harvestOre(valid.copy(serverTick = 49)).status)
        assertEquals(LoopStatus.APPLIED, interactions.harvestOre(valid.copy(serverTick = 50)).status)
        assertEquals(2, loop.snapshot(playerId)?.ore)
        assertEquals(2, loop.snapshot(playerId)?.gathererOreEarned)
    }

    @Test
    fun `supplier records validate fixed targets and become idempotent`() {
        val loop = SwarmLoopService(MemoryInteractionStore())
        val playerId = UUID.randomUUID()
        loop.loadPlayer(playerId)
        loop.selectRoute(playerId, ProcurementRoute.SUPPLIER)
        val interactions = SwarmWorldInteractions(loop)
        val record = TidebreakWorldSpec.supplierRecords.first()
        val attempt = attempt(playerId, record.targetId, record.position.x(), record.position.y(), record.position.z(), true, 0)

        assertEquals(LoopStatus.APPLIED, interactions.inspectSupplierRecord(attempt).status)
        assertEquals(LoopStatus.ALREADY_APPLIED, interactions.inspectSupplierRecord(attempt).status)
    }

    private fun attempt(
        playerId: UUID,
        targetId: String,
        x: Double,
        y: Double,
        z: Double,
        sight: Boolean,
        tick: Long,
    ) = TargetInteractionAttempt(playerId, targetId, x, y, z, sight, tick)
}

private class MemoryInteractionStore : SwarmSnapshotStore {
    private val data = mutableMapOf<UUID, SwarmPlayerSnapshot>()
    override fun load(playerId: UUID): SnapshotLoadResult =
        data[playerId]?.let(SnapshotLoadResult::Loaded) ?: SnapshotLoadResult.Missing

    override fun save(playerId: UUID, snapshot: SwarmPlayerSnapshot): Boolean {
        data[playerId] = snapshot
        return true
    }
}
