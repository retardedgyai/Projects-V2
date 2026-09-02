package dev.projects.server.questmap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.minestom.server.instance.block.Block

class QuestMapSchematicCatalogTest {
    @Test
    fun `runtime schematic assets remain decodable`() {
        val assets = QuestMapSchematicCatalog.allAssets()

        assertEquals(244, assets.size)
        assertEquals(244, assets.map { it.id }.distinct().size)
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
    fun `runtime silhouettes expose multiple assets per ecology with safe footprints`() {
        QuestTerrainStyle.entries.forEach { style ->
            val treeFootprints = (0 until 32).map { QuestMapStructureAssets.treeFootprint(style, it) }.distinct()
            val treeFamilies = (0 until 64).map { QuestMapStructureAssets.treeFamilyId(style, it) }.distinct()
            val rockAssets = (0 until 32).map { QuestMapSchematicCatalog.selectBoulder(style, it).asset.id }.distinct()

            assertTrue(treeFootprints.size >= 2, "$style lacks tree scale variation")
            assertEquals(5, treeFamilies.size, "$style lacks concept-compatible tree families")
            assertTrue(treeFootprints.min() >= 9, "$style tree silhouette is too small")
            assertTrue(treeFootprints.max() <= 15, "$style tree silhouette is too large for placement")
            assertTrue(rockAssets.size >= 4, "$style lacks rock silhouette variation")
            (0 until 32).forEach { variation ->
                val tree = QuestMapSchematicCatalog.selectTree(style, variation).asset
                val rock = QuestMapSchematicCatalog.selectBoulder(style, variation).asset
                assertTrue(QuestMapStructureAssets.treeFootprint(style, variation) >= tree.footprintRadius + 3)
                assertTrue(QuestMapStructureAssets.boulderFootprint(style, variation) >= rock.footprintRadius + 2)
            }
        }
    }

    @Test
    fun `runtime source palettes provide production variety`() {
        QuestTerrainStyle.entries.forEach { style ->
            val trees = (0 until 64).map { QuestMapSchematicCatalog.selectTree(style, it).asset.id }.distinct()
            val boulders = (0 until 64).map { QuestMapSchematicCatalog.selectBoulder(style, it).asset.id }.distinct()

            assertTrue(trees.size >= 8, "$style tree family has only ${trees.size} production assets")
            assertEquals(QuestMapSchematicCatalog.productionBoulders().size, boulders.size)
            QuestMapSchematicCatalog.productionTrees(style).forEach { asset ->
                assertTrue(asset.height >= 11, "${asset.id} is too short for production")
                assertTrue(asset.footprintRadius >= 3, "${asset.id} has a weak silhouette")
                assertTrue(asset.footprintRadius <= 12, "${asset.id} cannot be kept clear of paths")
                assertTrue(asset.voxels.size >= 48, "${asset.id} is too sparse for production")
            }
        }
    }

    @Test
    fun `production scenery draws from distinct creators and object categories`() {
        QuestTerrainStyle.entries.forEach { style ->
            val treeSources = QuestMapSchematicCatalog.productionTrees(style)
                .map { it.id.substringBefore('/') }
                .toSet()
            assertTrue("worldpainter" in treeSources, "$style lost the established source")
            assertTrue("daniye" in treeSources, "$style does not use Daniye silhouettes")
            assertTrue("meowbeard" in treeSources, "$style does not use Meowbeard silhouettes")
        }
        assertTrue(QuestMapSchematicCatalog.productionGroundDetails().size >= 12)
        assertTrue(QuestMapSchematicCatalog.productionBoulders().any { it.id.startsWith("meowbeard/") })
    }

    @Test
    fun `all selected palettes and rotations resolve to current Minecraft blocks`() {
        QuestTerrainStyle.entries.forEach { style ->
            (0 until 32).forEach { variation ->
                listOf(
                    QuestMapSchematicCatalog.selectTree(style, variation),
                    QuestMapSchematicCatalog.selectBoulder(style, variation),
                    QuestMapSchematicCatalog.selectGroundDetail(style, variation),
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
