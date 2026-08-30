package dev.projects.server.questmap

/**
 * Reviewed third-party nature assets. The source repository is MIT licensed; attribution lives
 * beside the resource files. Selection is deterministic so a seed can always be reviewed again.
 */
internal object QuestMapSchematicCatalog {
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

    fun selectTree(style: QuestTerrainStyle, variation: Int): Selection {
        val candidates = when (style) {
            QuestTerrainStyle.VERDANT -> lushOak + oldLivingForest + deadTrees.take(1)
            QuestTerrainStyle.HIGHLANDS -> spruce + deadTrees.take(2)
            QuestTerrainStyle.SALTMARSH -> swamp + oldLivingForest + deadTrees.takeLast(1)
        }
        val asset = candidates[Math.floorMod(variation, candidates.size)]
        return Selection(asset) { state, _ -> treePalette(state, style) }
    }

    fun selectBoulder(style: QuestTerrainStyle, variation: Int): Selection {
        val asset = rocks[Math.floorMod(variation, rocks.size)]
        return Selection(asset) { state, voxel -> rockPalette(state, style, variation, voxel) }
    }

    internal fun allAssets(): List<SpongeSchematicAsset> =
        lushOak + spruce + swamp + oldLivingForest + deadTrees + rocks

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

    private fun treePalette(state: String, style: QuestTerrainStyle): String = when (style) {
        QuestTerrainStyle.VERDANT -> state
        QuestTerrainStyle.HIGHLANDS -> state
            .replace("minecraft:oak_", "minecraft:spruce_")
            .replace("minecraft:dark_oak_", "minecraft:spruce_")
        QuestTerrainStyle.SALTMARSH -> state
            .replace("minecraft:dark_oak_", "minecraft:mangrove_")
            .replace("minecraft:oak_", "minecraft:mangrove_")
            .replace("minecraft:spruce_", "minecraft:mangrove_")
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
        }
    }
}
