package dev.projects.server.questmap

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
                assertNotNull(runtime.gatheringLabelFor(node), "Gathering node ${node.id} has no readable label")
                if (gathering.visualKind == QuestMapStructureAssets.GatheringVisualKind.ANIMAL_CORPSE) {
                    val interaction = assertNotNull(runtime.gatheringInteractionFor(node))
                    assertEquals(node, runtime.gatheringNodeForEntity(interaction))
                    assertTrue(gathering.blocks.isEmpty(), "Animal corpse ${node.id} unexpectedly contains blocks")
                } else {
                    assertEquals(null, runtime.gatheringInteractionFor(node))
                    assertTrue(gathering.blocks.size > 1, "Gathering object ${node.id} is not a complete asset")
                    gathering.blocks.forEach { (position, block) ->
                        assertEquals(block, runtime.instance.getBlock(position), "Gathering asset ${node.id} lost $position")
                    }
                    assertTrue(
                        gathering.interactionBlocks.any { position -> runtime.gatheringNodeAt(position) == node },
                        "Gathering asset ${node.id} exposes no right-clickable block",
                    )
                }
            }

            val schematicNode = runtime.gatheringNodes.first {
                runtime.gatheringObjects.getValue(it.id).visualKind == QuestMapStructureAssets.GatheringVisualKind.SCHEMATIC
            }
            val schematic = runtime.gatheringObjects.getValue(schematicNode.id)
            assertTrue(runtime.tryDepleteGatheringNode(schematicNode, 1_000L))
            assertEquals(null, runtime.gatheringLabelFor(schematicNode))
            schematic.blocks.keys.forEach { position -> assertEquals(Block.AIR, runtime.instance.getBlock(position)) }
            runtime.respawnGatheringNodes(100_000L)
            assertNotNull(runtime.gatheringLabelFor(schematicNode))
            schematic.blocks.forEach { (position, block) -> assertEquals(block, runtime.instance.getBlock(position)) }

            val customizedRuntime = VerdantRoadQuestRuntime.prepare(
                BLOCKED_ROAD_REGRESSION_SEED + 1,
                candidateCount = 1,
                customization = QuestMapCustomization(
                    listOf(
                        QuestMapGatheringModifier(null, QuestMapGatheringStat.AMOUNT, 100),
                        QuestMapGatheringModifier(
                            QuestGatheringDiscipline.MINING,
                            QuestMapGatheringStat.DENSE_REGIONS,
                            200,
                        ),
                    ),
                ),
            ).get(30, TimeUnit.SECONDS)
            try {
                assertTrue(customizedRuntime.gatheringNodes.size > questGatheringNodes(customizedRuntime.plan).size)
                assertTrue(customizedRuntime.gatheringNodes.any { it.denseRegionId != null })
                customizedRuntime.gatheringNodes.forEach { node ->
                    assertNotNull(customizedRuntime.gatheringLabelFor(node))
                    val gathering = customizedRuntime.gatheringObjects.getValue(node.id)
                    if (gathering.visualKind == QuestMapStructureAssets.GatheringVisualKind.SCHEMATIC) {
                        assertTrue(gathering.blocks.isNotEmpty(), "Customized gathering node ${node.id} was not placed")
                    }
                }
            } finally {
                customizedRuntime.close()
            }
        } finally {
            runtime.close()
        }
    }

    private companion object {
        const val BLOCKED_ROAD_REGRESSION_SEED = 1_788_168_623_401L
    }
}
