package dev.projects.server.questmap

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer

class QuestMapRuntimeClearanceTest {
    @Test
    fun `fully decorated regression map keeps road and side trails unobstructed`() {
        MinecraftServer.init(Auth.Offline())
        val runtime = VerdantRoadQuestRuntime.prepare(BLOCKED_ROAD_REGRESSION_SEED, candidateCount = 1)
            .get(30, TimeUnit.SECONDS)
        try {
            val obstructions = VerdantRoadQuestDecorator.routeClearanceObstructions(runtime.instance, runtime.plan)
            assertTrue(obstructions.isEmpty(), "Decorated route contains solid blocks: $obstructions")
            runtime.gatheringNodes.forEach { node ->
                assertEquals(
                    node.discipline.nodeBlock,
                    runtime.instance.getBlock(node.blockPosition),
                    "Gathering node ${node.id} was cleared from ${node.blockPosition}",
                )
            }
        } finally {
            runtime.close()
        }
    }

    private companion object {
        const val BLOCKED_ROAD_REGRESSION_SEED = 1_788_168_623_401L
    }
}
