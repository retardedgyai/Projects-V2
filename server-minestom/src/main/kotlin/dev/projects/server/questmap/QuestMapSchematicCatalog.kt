package dev.projects.server.questmap

/**
 * Third-party nature assets used by the runtime. Attribution lives beside the resource files.
 * Selection is deterministic so a seed can always be reviewed again.
 */
internal object QuestMapSchematicCatalog {
    internal const val SOURCE_URL = "https://github.com/sijmenvb/worldpainter-trees"
    internal const val SOURCE_LICENSE = "MIT"
    internal const val DANIYE_SOURCE_URL = "https://www.curseforge.com/minecraft/customization/daniyes-tree-bundle"
    internal const val MEOWBEARD_SOURCE_URL =
        "https://www.curseforge.com/minecraft/worlds/custom-object-repository-trees-rocks-mushrooms"

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
    private val roofedForest = loadFamily(
        "roofed_forest",
        listOf(
            "dark_oak_01.schem", "dark_oak_02.schem",
            "dark_oak_large_01.schem", "dark_oak_large_02.schem",
        ),
        SchematicAnchorMode.TREE_TRUNK,
    )
    private val jungle = loadFamily(
        "jungle",
        listOf(
            "jungle_1.schem", "jungle_2.schem", "jungle_3.schem",
            "jungle_big_1.schem", "jungle_big_2.schem",
            "jungle_small_1.schem", "jungle_small_2.schem",
        ),
        SchematicAnchorMode.TREE_TRUNK,
    )
    private val savanna = loadFamily(
        "savanna",
        (1..5).map { index -> "acacia$index.schem" },
        SchematicAnchorMode.TREE_TRUNK,
    )
    private val badlands = loadFamily(
        "badlands",
        (1..3).map { index -> "badlands_tree_${index.toString().padStart(2, '0')}.schem" },
        SchematicAnchorMode.TREE_TRUNK,
    )
    private val rocks = loadFamily(
        "rocks",
        (1..11).map { index -> "stone${index.toString().padStart(2, '0')}.schem" },
        SchematicAnchorMode.BURIED_MASS,
    )
    private val daniyeAcacia = loadProviderFamily(
        "daniye", "acacia", numberedNames("acacia_tree_", 21), SchematicAnchorMode.TREE_TRUNK,
    )
    private val daniyeBirch = loadProviderFamily(
        "daniye", "birch", numberedNames("birch_tree_", 18), SchematicAnchorMode.TREE_TRUNK,
    )
    private val daniyeCherry = loadProviderFamily(
        "daniye", "cherry", numberedNames("cherry_tree_", 15), SchematicAnchorMode.TREE_TRUNK,
    )
    private val daniyeDarkOak = loadProviderFamily(
        "daniye", "dark_oak", numberedNames("dark_oak_tree_", 9), SchematicAnchorMode.TREE_TRUNK,
    )
    private val daniyeJungle = loadProviderFamily(
        "daniye", "jungle", numberedNames("jungle_tree_", 15), SchematicAnchorMode.TREE_TRUNK,
    )
    private val daniyeMangrove = loadProviderFamily(
        "daniye", "mangrove", numberedNames("mangrove_tree_", 6), SchematicAnchorMode.TREE_TRUNK,
    )
    private val daniyeOak = loadProviderFamily(
        "daniye", "oak", numberedNames("oak_tree_", 18), SchematicAnchorMode.TREE_TRUNK,
    )
    private val daniyePaleOak = loadProviderFamily(
        "daniye", "pale_oak", numberedNames("pale_oak_tree_", 9), SchematicAnchorMode.TREE_TRUNK,
    )
    private val daniyeSpruce = loadProviderFamily(
        "daniye", "spruce", numberedNames("spruce_tree_", 12), SchematicAnchorMode.TREE_TRUNK,
    )
    private val meowbeardTrees = loadProviderFamily(
        "meowbeard",
        "trees",
        listOf(
            "MB_BirchGreenS_01.schem", "MB_BirchGreenS_02.schem", "MB_BirchGreenS_03.schem", "MB_BirchGreenS_04.schem",
            "MB_BirchMedium01.schem", "MB_BirchMedium02.schem", "MB_BirchMedium03.schem", "MB_BirchMedium04.schem",
            "MB_DeadTree02.schem", "MB_DeadTree03.schem", "MB_DeadTree04.schem", "MB_DeadTree05.schem",
            "MB_Oak_01.schem", "MB_Oak_03.schem", "MB_Oak_07.schem", "MB_Oak_08.schem", "MB_Oak_09.schem",
            "MB_Palm01.schem", "MB_Palm03.schem",
            "MB_RedWood1.schem", "MB_RedWood2.schem", "MB_RedWood3.schem", "MB_RedWood4.schem",
            "MB_Redwood_L01.schem", "MB_Redwood_L02.schem", "MB_Redwood_L03.schem", "MB_Redwood_L04.schem",
            "MB_Redwood_L05.schem", "MB_Redwood_L06.schem", "MB_Redwood_L07.schem",
            "MB_SwampLarge_01.schem", "MB_SwampLarge_02.schem", "MB_SwampLarge_05.schem",
            "MB_SwampLarge_06.schem", "MB_SwampLarge_07.schem", "MB_SwampStump02.schem",
        ),
        SchematicAnchorMode.TREE_TRUNK,
    )
    private val meowbeardGround = loadProviderFamily(
        "meowbeard",
        "ground",
        listOf(
            "MB_BirchBush01.schem", "MB_BirchBush02.schem", "MB_BirchBush03.schem", "MB_BirchBush04.schem",
            "MB_BirchBushGreen_01.schem", "MB_BirchBushGreen_02.schem",
            "MB_BirchBushGreen_03.schem", "MB_BirchBushGreen_04.schem",
            "MB_GreenBush01.schem", "MB_GreenBush02.schem", "MB_GreenBush03.schem", "MB_RedBush04.schem",
            "MB_SwampBush_01.schem", "MB_SwampSmall_01.schem", "MB_SwampSmall_03.schem", "MB_SwampSmall_05.schem",
            "MB_GreenShroom1.schem", "MB_RedShroom2.schem",
        ),
        SchematicAnchorMode.SURFACE_MASS,
    )
    private val meowbeardRocks = loadProviderFamily(
        "meowbeard",
        "rocks",
        listOf("GrayRock01.schem", "GrayRock03.schem", "GrayRock04.schem", "GrayRock10.schem") +
            listOf("RedRock01.schem", "RedRock03.schem", "RedRock04.schem", "RedRock10.schem") +
            listOf("WhiteRock01.schem", "WhiteRock03.schem", "WhiteRock04.schem", "WhiteRock10.schem"),
        SchematicAnchorMode.BURIED_MASS,
    )

    private fun reviewedTrees(candidates: List<SpongeSchematicAsset>): List<SpongeSchematicAsset> {
        val reviewed = candidates.filter { asset ->
            asset.height >= 11 && asset.footprintRadius in 3..12 && asset.voxels.size >= 48
        }
        require(reviewed.size >= 4) { "A production ecology needs at least four substantial tree silhouettes" }
        return reviewed
    }

    private val productionRocks by lazy {
        (rocks + meowbeardRocks)
            .filter { asset -> asset.height >= 3 && asset.footprintRadius in 2..6 && asset.voxels.size >= 10 }
            .also { require(it.size >= 4) { "Production rock catalog lacks substantial silhouettes" } }
    }

    private val productionGround by lazy {
        meowbeardGround.filter { asset ->
            asset.height in 3..12 && asset.footprintRadius in 2..4 && asset.voxels.size >= 10
        }.also { require(it.size >= 12) { "Production understory catalog lacks silhouette variety" } }
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

    fun selectGroundDetail(style: QuestTerrainStyle, variation: Int): Selection {
        val asset = productionGround[Math.floorMod(variation + style.ordinal * 7, productionGround.size)]
        return Selection(asset) { state, voxel -> groundPalette(state, style, variation, voxel) }
    }

    fun selectGatheringTree(style: QuestTerrainStyle, variation: Int): Selection {
        val compact = productionTreePools.getValue(style).filter { asset ->
            asset.footprintRadius <= 6 && asset.height <= 24
        }
        val candidates = compact.ifEmpty { productionTreePools.getValue(style) }
        val asset = candidates[Math.floorMod(variation, candidates.size)]
        return Selection(asset) { state, voxel -> treePalette(state, style, variation, voxel) }
    }

    fun selectGatheringBoulder(style: QuestTerrainStyle, variation: Int): Selection {
        val compact = productionRocks.filter { it.footprintRadius <= 4 }
        val candidates = compact.ifEmpty { productionRocks }
        val asset = candidates[Math.floorMod(variation, candidates.size)]
        return Selection(asset) { state, voxel -> rockPalette(state, style, variation, voxel) }
    }

    fun selectGatheringPlant(style: QuestTerrainStyle, variation: Int): Selection {
        val candidates = productionGround.filter { asset ->
            asset.height >= 5 && !asset.id.contains("shroom", ignoreCase = true)
        }.ifEmpty { productionGround }
        val asset = candidates[Math.floorMod(variation + style.ordinal * 11, candidates.size)]
        return Selection(asset) { state, voxel -> groundPalette(state, style, variation, voxel) }
    }

    internal fun allAssets(): List<SpongeSchematicAsset> =
        lushOak + spruce + swamp + oldLivingForest + deadTrees + roofedForest + jungle + savanna + badlands + rocks +
            daniyeAcacia + daniyeBirch + daniyeCherry + daniyeDarkOak + daniyeJungle + daniyeMangrove +
            daniyeOak + daniyePaleOak + daniyeSpruce + meowbeardTrees + meowbeardGround + meowbeardRocks

    internal fun productionTrees(style: QuestTerrainStyle): List<SpongeSchematicAsset> = productionTreePools.getValue(style)

    internal fun productionBoulders(): List<SpongeSchematicAsset> = productionRocks

    internal fun productionGroundDetails(): List<SpongeSchematicAsset> = productionGround

    private fun treeCandidates(style: QuestTerrainStyle): List<SpongeSchematicAsset> = when (style) {
        QuestTerrainStyle.VERDANT ->
            lushOak + oldLivingForest + roofedForest + daniyeOak + daniyeBirch + daniyeDarkOak + meowbeardTrees
        QuestTerrainStyle.HIGHLANDS ->
            spruce + deadTrees + daniyeSpruce + daniyeBirch + meowbeardTrees + badlands
        QuestTerrainStyle.SALTMARSH ->
            swamp + oldLivingForest + daniyeMangrove + daniyeJungle + meowbeardTrees + jungle
        QuestTerrainStyle.CLIFFLANDS ->
            spruce + deadTrees + daniyeSpruce + daniyeAcacia + meowbeardTrees + badlands + savanna
        QuestTerrainStyle.SAKURA_GROVE ->
            daniyeCherry + daniyePaleOak + daniyeBirch + meowbeardTrees + lushOak + oldLivingForest + roofedForest
        QuestTerrainStyle.INFERNAL ->
            deadTrees + daniyePaleOak + daniyeDarkOak + oldLivingForest + meowbeardTrees + badlands
    }

    private fun loadFamily(
        family: String,
        names: List<String>,
        anchorMode: SchematicAnchorMode,
    ): List<SpongeSchematicAsset> = loadProviderFamily("worldpainter", family, names, anchorMode)

    private fun loadProviderFamily(
        provider: String,
        family: String,
        names: List<String>,
        anchorMode: SchematicAnchorMode,
    ): List<SpongeSchematicAsset> = names.map { name ->
        val resource = "/questmap/assets/$provider/$family/$name"
        val input = QuestMapSchematicCatalog::class.java.getResourceAsStream(resource)
            ?: error("Missing quest-map asset $resource")
        input.use { SpongeSchematicAsset.read("$provider/$family/$name", it, anchorMode) }
    }

    private fun numberedNames(prefix: String, count: Int): List<String> =
        (1..count).map { index -> "$prefix${index.toString().padStart(2, '0')}.schem" }

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
            .replace("minecraft:birch_", "minecraft:mangrove_")
            .replace("minecraft:spruce_", "minecraft:mangrove_")
        QuestTerrainStyle.CLIFFLANDS -> state
            .replace("minecraft:dark_oak_", "minecraft:spruce_")
            .replace("minecraft:oak_", "minecraft:spruce_")
        QuestTerrainStyle.SAKURA_GROVE -> state
            .replace("minecraft:dark_oak_", "minecraft:cherry_")
            .replace("minecraft:oak_", "minecraft:cherry_")
            .replace("minecraft:spruce_", "minecraft:cherry_")
            .replace("minecraft:birch_", "minecraft:cherry_")
            .replace("minecraft:jungle_", "minecraft:cherry_")
            .replace("minecraft:acacia_", "minecraft:cherry_")
        QuestTerrainStyle.INFERNAL -> infernalTreeState(state, variation, voxel)
    }

    private fun groundPalette(
        state: String,
        style: QuestTerrainStyle,
        variation: Int,
        voxel: SchematicVoxel,
    ): String {
        val modern = if (state == "minecraft:grass") "minecraft:short_grass" else state
        if (style != QuestTerrainStyle.INFERNAL) return treePalette(modern, style, variation, voxel)
        val name = modern.substringBefore('[')
        val properties = modern.substringAfter('[', "").let { if (it.isEmpty()) "" else "[$it" }
        return when {
            name.endsWith("_fence") -> "minecraft:crimson_fence$properties"
            name == "minecraft:short_grass" || name == "minecraft:large_fern" -> "minecraft:crimson_roots"
            name == "minecraft:moss_block" || name == "minecraft:melon" -> "minecraft:nether_wart_block"
            else -> infernalTreeState(modern, variation, voxel)
        }
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
