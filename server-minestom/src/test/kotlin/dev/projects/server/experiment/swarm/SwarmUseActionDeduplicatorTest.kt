package dev.projects.server.experiment.swarm

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SwarmUseActionDeduplicatorTest {
    @Test
    fun `block and air callbacks for one physical use share one action id`() {
        val playerId = UUID.randomUUID()
        val deduplicator = SwarmUseActionDeduplicator()
        var sequence = 0L

        repeat(20) { tick ->
            val blockCallback = deduplicator.actionId(playerId, tick.toLong()) { ++sequence }
            val useItemCallback = deduplicator.actionId(playerId, tick.toLong()) { ++sequence }
            assertEquals(blockCallback, useItemCallback)
        }
        assertEquals(20L, sequence)
    }

    @Test
    fun `new tick and disconnect produce fresh action identities`() {
        val playerId = UUID.randomUUID()
        val deduplicator = SwarmUseActionDeduplicator()
        var sequence = 0L
        val first = deduplicator.actionId(playerId, 10) { ++sequence }
        val nextTick = deduplicator.actionId(playerId, 11) { ++sequence }
        deduplicator.clear(playerId)
        val reconnected = deduplicator.actionId(playerId, 11) { ++sequence }

        assertNotEquals(first, nextTick)
        assertNotEquals(nextTick, reconnected)
    }
}
