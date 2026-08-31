package dev.projects.server.questmap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.minestom.server.instance.block.Block

class QuestMapSchematicCatalogTest {
    @Test
    fun `archived licensed assets remain decodable but are not the runtime catalog`() {
        val assets = QuestMapSchematicCatalog.allAssets()

        assertEquals(36, assets.size)
        assertEquals(36, assets.map { it.id }.distinct().size)
        assets.forEach { asset ->
            assertTrue(asset.width in 1..48, "${asset.id} width=${asset.width}")
            assertTrue(asset.length in 1..48, "${asset.id} length=${asset.length}")
            assertTrue(asset.height in 1..64, "${asset.id} height=${asset.height}")
            val minimumBlocks = if (asset.anchorMode == SchematicAnchorMode.TREE_TRUNK) 8 else 3
            assertTrue(asset.voxels.size >= minimumBlocks, "${asset.id} is too sparse")
            assertTrue(asset.footprintRadius >= 1, "${asset.id} has no footprint")
        }
    }

    @Test
    fun `ProjectS authored runtime silhouettes expose multiple scales per ecology`() {
        QuestTerrainStyle.entries.forEach { style ->
            val treeFootprints = (0 until 32).map { QuestMapStructureAssets.treeFootprint(style, it) }.distinct()
            val rockFootprints = (0 until 32).map { QuestMapStructureAssets.boulderFootprint(style, it) }.distinct()

            assertTrue(treeFootprints.size >= 3, "$style lacks authored tree scale variation")
            assertTrue(treeFootprints.min() >= 9, "$style tree silhouette is too small")
            assertTrue(treeFootprints.max() <= 15, "$style tree silhouette is too large for placement")
            assertEquals(3, rockFootprints.size, "$style lacks authored rock scale variation")
        }
    }

    @Test
    fun `archived source palettes still decode for migration compatibility`() {
        QuestTerrainStyle.entries.forEach { style ->
            val trees = (0 until 64).map { QuestMapSchematicCatalog.selectTree(style, it).asset.id }.distinct()
            val boulders = (0 until 64).map { QuestMapSchematicCatalog.selectBoulder(style, it).asset.id }.distinct()

            assertTrue(trees.size >= 4, "$style tree family has only ${trees.size} production assets")
            assertEquals(QuestMapSchematicCatalog.productionBoulders().size, boulders.size)
            QuestMapSchematicCatalog.productionTrees(style).forEach { asset ->
                assertTrue(asset.height >= 11, "${asset.id} is too short for production")
                assertTrue(asset.footprintRadius >= 3, "${asset.id} has a weak silhouette")
                assertTrue(asset.voxels.size >= 48, "${asset.id} is too sparse for production")
            }
        }
    }

    @Test
    fun `all selected palettes and rotations resolve to current Minecraft blocks`() {
        QuestTerrainStyle.entries.forEach { style ->
            (0 until 32).forEach { variation ->
                listOf(
                    QuestMapSchematicCatalog.selectTree(style, variation),
                    QuestMapSchematicCatalog.selectBoulder(style, variation),
                ).forEach { selection ->
                    repeat(4) { rotation ->
                        selection.asset.resolvedStates(rotation, selection.palette).forEach { state ->
                            assertNotNull(Block.fromState(state), "Unknown state $state in ${selection.asset.id}")
                        }
                    }
                }
            }
        }
    }
}
