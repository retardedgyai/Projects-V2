package dev.projects.server.questmap

/**
 * Reviewed third-party nature assets. The source repository is MIT licensed; attribution lives
 * beside the resource files. Selection is deterministic so a seed can always be reviewed again.
 */
internal object QuestMapSchematicCatalog {
    internal const val SOURCE_URL = "https://github.com/sijmenvb/worldpainter-trees"
    internal const val SOURCE_LICENSE = "MIT"

    internal data class Selection(
        val asset: SpongeSchematicAsset,
        val palette: (String, SchematicVoxel) -> String,
    )

    private val lushOak = loadFamily(
        "lush_oak",
        listOf(
            "lush_oak_01.schem", "lush_oak_02.schem", "lush_oak_03.schem",
            "lush_oak_04.schem", "lush_oak_05.schem", "lush_oak_big_1.schem",
            "lush_oak_big_2.schem", "lush_oak_small_1.schem", "lush_oak_small_2.schem",
        ),
        SchematicAnchorMode.TREE_TRUNK,
    )
    private val spruce = loadFamily(
        "spruce",
        listOf(
            "big_spruce_3.schem", "large_spruce_tree_1.schem", "large_spruce_tree_2.schem",
            "spruce_tree_big_01.schem", "spruce_tree_big_02.schem", "spruce_tree_mid_wide_01.schem",
        ),
        SchematicAnchorMode.TREE_TRUNK,
    )
    private val swamp = loadFamily(
        "swamp",
        listOf("swamp_tree1.schem", "swamp_tree2.schem", "swamp_tree3.schem"),
        SchematicAnchorMode.TREE_TRUNK,
    )
    private val oldLivingForest = loadFamily(
        "old_living_forest",
        listOf("old_tree1.schem", "old_tree2.schem", "old_tree_big1.schem"),
        SchematicAnchorMode.TREE_TRUNK,
    )
    private val deadTrees = loadFamily(
        "dead_trees",
        listOf(
            "dead_tree_big_01.schem", "dead_tree_big_02.schem",
            "dead_tree_big_03.schem", "dead_tree_small_01.schem",
        ),
        SchematicAnchorMode.TREE_TRUNK,
    )
    private val rocks = loadFamily(
        "rocks",
        (1..11).map { index -> "stone${index.toString().padStart(2, '0')}.schem" },
        SchematicAnchorMode.BURIED_MASS,
    )

    private fun reviewedTrees(candidates: List<SpongeSchematicAsset>): List<SpongeSchematicAsset> {
        val reviewed = candidates.filter { asset ->
            asset.height >= 11 && asset.footprintRadius >= 3 && asset.voxels.size >= 48
        }
        require(reviewed.size >= 4) { "A production ecology needs at least four substantial tree silhouettes" }
        return reviewed
    }

    private val productionRocks by lazy {
        rocks.filter { asset -> asset.height >= 3 && asset.footprintRadius >= 2 && asset.voxels.size >= 10 }
            .also { require(it.size >= 4) { "Production rock catalog lacks substantial silhouettes" } }
    }

    private val productionTreePools by lazy {
        QuestTerrainStyle.entries.associateWith { style -> reviewedTrees(treeCandidates(style)) }
    }

    fun selectTree(style: QuestTerrainStyle, variation: Int): Selection {
        val candidates = productionTreePools.getValue(style)
        val asset = candidates[Math.floorMod(variation, candidates.size)]
        return Selection(asset) { state, voxel -> treePalette(state, style, variation, voxel) }
    }

    fun selectBoulder(style: QuestTerrainStyle, variation: Int): Selection {
        val asset = productionRocks[Math.floorMod(variation, productionRocks.size)]
        return Selection(asset) { state, voxel -> rockPalette(state, style, variation, voxel) }
    }

    internal fun allAssets(): List<SpongeSchematicAsset> =
        lushOak + spruce + swamp + oldLivingForest + deadTrees + rocks

    internal fun productionTrees(style: QuestTerrainStyle): List<SpongeSchematicAsset> = productionTreePools.getValue(style)

    internal fun productionBoulders(): List<SpongeSchematicAsset> = productionRocks

    private fun treeCandidates(style: QuestTerrainStyle): List<SpongeSchematicAsset> = when (style) {
        QuestTerrainStyle.VERDANT -> lushOak + oldLivingForest + deadTrees.take(1)
        QuestTerrainStyle.HIGHLANDS -> spruce + deadTrees.take(2)
        QuestTerrainStyle.SALTMARSH -> swamp + oldLivingForest + deadTrees.takeLast(1)
        QuestTerrainStyle.CLIFFLANDS -> spruce + oldLivingForest + deadTrees
        QuestTerrainStyle.SAKURA_GROVE -> lushOak + oldLivingForest + deadTrees.take(1)
        QuestTerrainStyle.INFERNAL -> deadTrees + oldLivingForest + swamp
    }

    private fun loadFamily(
        family: String,
        names: List<String>,
        anchorMode: SchematicAnchorMode,
    ): List<SpongeSchematicAsset> = names.map { name ->
        val resource = "/questmap/assets/worldpainter/$family/$name"
        val input = QuestMapSchematicCatalog::class.java.getResourceAsStream(resource)
            ?: error("Missing quest-map asset $resource")
        input.use { SpongeSchematicAsset.read("$family/$name", it, anchorMode) }
    }

    private fun treePalette(
        state: String,
        style: QuestTerrainStyle,
        variation: Int,
        voxel: SchematicVoxel,
    ): String = when (style) {
        QuestTerrainStyle.VERDANT -> state
        QuestTerrainStyle.HIGHLANDS -> state
            .replace("minecraft:oak_", "minecraft:spruce_")
            .replace("minecraft:dark_oak_", "minecraft:spruce_")
        QuestTerrainStyle.SALTMARSH -> state
            .replace("minecraft:dark_oak_", "minecraft:mangrove_")
            .replace("minecraft:oak_", "minecraft:mangrove_")
            .replace("minecraft:spruce_", "minecraft:mangrove_")
        QuestTerrainStyle.CLIFFLANDS -> state
            .replace("minecraft:dark_oak_", "minecraft:spruce_")
            .replace("minecraft:oak_", "minecraft:spruce_")
        QuestTerrainStyle.SAKURA_GROVE -> state
            .replace("minecraft:dark_oak_", "minecraft:cherry_")
            .replace("minecraft:oak_", "minecraft:cherry_")
            .replace("minecraft:spruce_", "minecraft:cherry_")
        QuestTerrainStyle.INFERNAL -> infernalTreeState(state, variation, voxel)
    }

    private fun infernalTreeState(state: String, variation: Int, voxel: SchematicVoxel): String {
        val name = state.substringBefore('[')
        val axis = Regex("axis=(x|y|z)").find(state)?.groupValues?.get(1) ?: "y"
        val warped = Math.floorMod(variation + voxel.x * 3 + voxel.z * 5, 7) == 0
        return when {
            name.endsWith("_leaves") || name.endsWith("_wart_block") ->
                if (warped) "minecraft:warped_wart_block" else "minecraft:nether_wart_block"
            name.endsWith("_log") || name.endsWith("_stem") ->
                if (warped) "minecraft:warped_stem[axis=$axis]" else "minecraft:crimson_stem[axis=$axis]"
            name.endsWith("_wood") || name.endsWith("_hyphae") ->
                if (warped) "minecraft:warped_hyphae[axis=$axis]" else "minecraft:crimson_hyphae[axis=$axis]"
            name.endsWith("_roots") -> if (warped) "minecraft:warped_roots" else "minecraft:crimson_roots"
            else -> state
        }
    }

    private fun rockPalette(
        state: String,
        style: QuestTerrainStyle,
        variation: Int,
        voxel: SchematicVoxel,
    ): String {
        val name = state.substringBefore('[')
        val isRock = name in setOf(
            "minecraft:stone", "minecraft:cobblestone", "minecraft:andesite",
            "minecraft:mossy_cobblestone", "minecraft:stone_bricks", "minecraft:mossy_stone_bricks",
        )
        if (!isRock) return state
        val color = Math.floorMod(
            variation * 31 + voxel.x * 17 + voxel.y * 11 + voxel.z * 43,
            13,
        )
        return when (style) {
            QuestTerrainStyle.VERDANT -> when {
                color < 2 -> "minecraft:mossy_cobblestone"
                color < 5 -> "minecraft:andesite"
                else -> "minecraft:stone"
            }
            QuestTerrainStyle.HIGHLANDS -> when {
                color < 3 -> "minecraft:cobblestone"
                color < 7 -> "minecraft:tuff"
                else -> "minecraft:stone"
            }
            QuestTerrainStyle.SALTMARSH -> when {
                color < 3 -> "minecraft:mossy_cobblestone"
                color < 6 -> "minecraft:mud_bricks"
                else -> "minecraft:stone"
            }
            QuestTerrainStyle.CLIFFLANDS -> when {
                color < 2 -> "minecraft:calcite"
                color < 6 -> "minecraft:andesite"
                color == 12 -> "minecraft:terracotta"
                else -> "minecraft:stone"
            }
            QuestTerrainStyle.SAKURA_GROVE -> when {
                color < 3 -> "minecraft:mossy_cobblestone"
                color < 5 -> "minecraft:calcite"
                else -> "minecraft:stone"
            }
            QuestTerrainStyle.INFERNAL -> when {
                color < 3 -> "minecraft:basalt"
                color < 7 -> "minecraft:blackstone"
                color == 12 -> "minecraft:magma_block"
                else -> "minecraft:netherrack"
            }
        }
    }
}
