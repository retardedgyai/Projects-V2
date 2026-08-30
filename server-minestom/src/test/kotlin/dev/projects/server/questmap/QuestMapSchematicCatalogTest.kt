package dev.projects.server.questmap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.minestom.server.instance.block.Block

class QuestMapSchematicCatalogTest {
    @Test
    fun `all licensed assets decode and have usable bounds`() {
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
    fun `each ecology exposes meaningful deterministic variation`() {
        QuestTerrainStyle.entries.forEach { style ->
            val trees = (0 until 32).map { QuestMapSchematicCatalog.selectTree(style, it).asset.id }.distinct()
            val boulders = (0 until 32).map { QuestMapSchematicCatalog.selectBoulder(style, it).asset.id }.distinct()

            assertTrue(trees.size >= 6, "$style tree family has only ${trees.size} assets")
            assertEquals(11, boulders.size, "$style does not expose all reviewed rock assets")
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
