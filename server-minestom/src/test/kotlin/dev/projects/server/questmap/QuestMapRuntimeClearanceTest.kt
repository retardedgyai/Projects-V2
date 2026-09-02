package dev.projects.server.questmap

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.block.Block

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
                val gathering = runtime.gatheringObjects.getValue(node.id)
                if (gathering.visualKind == QuestMapStructureAssets.GatheringVisualKind.ANIMAL_CORPSE) {
                    assertTrue(gathering.blocks.isEmpty(), "Animal corpse ${node.id} unexpectedly contains blocks")
                } else {
                    assertTrue(gathering.blocks.size > 1, "Gathering object ${node.id} is not a complete asset")
                    gathering.blocks.forEach { (position, block) ->
                        assertEquals(block, runtime.instance.getBlock(position), "Gathering asset ${node.id} lost $position")
                    }
                }
            }

            val schematicNode = runtime.gatheringNodes.first {
                runtime.gatheringObjects.getValue(it.id).visualKind == QuestMapStructureAssets.GatheringVisualKind.SCHEMATIC
            }
            val schematic = runtime.gatheringObjects.getValue(schematicNode.id)
            assertTrue(runtime.tryDepleteGatheringNode(schematicNode, 1_000L))
            schematic.blocks.keys.forEach { position -> assertEquals(Block.AIR, runtime.instance.getBlock(position)) }
            runtime.respawnGatheringNodes(100_000L)
            schematic.blocks.forEach { (position, block) -> assertEquals(block, runtime.instance.getBlock(position)) }
        } finally {
            runtime.close()
        }
    }

    private companion object {
        const val BLOCKED_ROAD_REGRESSION_SEED = 1_788_168_623_401L
    }
}
