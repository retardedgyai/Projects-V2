package dev.projects.server.equipment

import dev.projects.server.mod.ModDefinition
import dev.projects.server.mod.ModEntry
import dev.projects.server.mod.ModRank
import java.util.Random
import java.util.UUID

enum class LootSource { NORMAL_ENEMY, RIFT_EXECUTIONER }

data class LootProfile(
    val source: LootSource,
    val tier: EquipmentTier,
    val itemLevel: Int,
    val rarityWeights: Map<EquipmentRarity, Int>,
    val minimumBaseAttack: Double,
    val maximumBaseAttack: Double,
) {
    init {
        require(itemLevel in tier.minimumItemLevel..tier.maximumItemLevel)
        require(rarityWeights.values.all { it >= 0 } && rarityWeights.values.sum() > 0)
        require(minimumBaseAttack >= 0.0 && maximumBaseAttack >= minimumBaseAttack)
    }

    fun qualityScore(): Pair<Int, Int> = tier.ordinal to rarityWeights[EquipmentRarity.EPIC]!!
}

data class LootDrop(
    val item: EquipmentItem,
    val displayName: String,
    val source: LootSource,
)

class LootGenerator(
    private val definitions: List<ModDefinition>,
) {
    init {
        require(definitions.isNotEmpty()) { "loot MOD pool must not be empty" }
        require(definitions.map { it.modId }.toSet().size == definitions.size) { "loot MOD ids must be unique" }
    }

    fun generate(seed: Long, profile: LootProfile): LootDrop {
        val random = Random(seed)
        val rarity = weightedRarity(random, profile.rarityWeights)
        val rank = ModRank.entries.single { it.tier == profile.tier }
        val candidates = definitions.filter { it.rank == rank && EquipmentSlot.WEAPON in it.allowedSlots }
        require(candidates.size >= rarity.modCapacity) { "loot MOD pool is smaller than rarity capacity" }
        val selected = candidates.shuffled(random).take(rarity.modCapacity)
        val mods = selected.mapIndexed { index, definition ->
            EquipmentModSlot(index, ModEntry(
                modId = definition.modId,
                rank = definition.rank,
                rolledValue = definition.minimumValue + random.nextDouble() * (definition.maximumValue - definition.minimumValue),
                slotIndex = index,
                definitionRevision = definition.definitionRevision,
            ))
        }
        val attack = profile.minimumBaseAttack + random.nextDouble() * (profile.maximumBaseAttack - profile.minimumBaseAttack)
        val suffix = selected.firstOrNull()?.modId?.substringAfter(':')?.replace('-', ' ') ?: "fortune"
        val name = if (profile.source == LootSource.RIFT_EXECUTIONER) "Riftbound ${suffix.titleCase()}" else "Worn ${suffix.titleCase()}"
        return LootDrop(
            EquipmentItem(
                itemId = "projects:loot-${profile.source.name.lowercase()}-${random.nextInt(1_000_000)}",
                category = EquipmentCategory.WEAPON,
                slot = EquipmentSlot.WEAPON,
                tier = profile.tier,
                itemLevel = profile.itemLevel,
                rarity = rarity,
                baseStatRolls = listOf(BaseStatRoll("projects:physical-attack", attack)),
                modSlots = mods,
            ),
            name,
            profile.source,
        )
    }

    private fun weightedRarity(random: Random, weights: Map<EquipmentRarity, Int>): EquipmentRarity {
        var roll = random.nextInt(weights.values.sum())
        for (rarity in EquipmentRarity.entries) {
            roll -= weights[rarity] ?: 0
            if (roll < 0) return rarity
        }
        error("rarity weights did not produce a result")
    }

    private fun String.titleCase(): String = split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
}

fun lootRewardSeed(playerId: UUID, rewardSequence: Long): Long =
    playerId.mostSignificantBits xor playerId.leastSignificantBits xor rewardSequence

val V0_LOOT_PROFILES = mapOf(
    LootSource.NORMAL_ENEMY to LootProfile(
        LootSource.NORMAL_ENEMY, EquipmentTier.T1, 5,
        mapOf(EquipmentRarity.COMMON to 60, EquipmentRarity.UNCOMMON to 30, EquipmentRarity.RARE to 9, EquipmentRarity.EPIC to 1),
        8.0, 16.0,
    ),
    LootSource.RIFT_EXECUTIONER to LootProfile(
        LootSource.RIFT_EXECUTIONER, EquipmentTier.T2, 20,
        mapOf(EquipmentRarity.COMMON to 15, EquipmentRarity.UNCOMMON to 35, EquipmentRarity.RARE to 35, EquipmentRarity.EPIC to 15),
        14.0, 28.0,
    ),
)

private fun lootDefinition(
    id: String,
    rank: ModRank,
    statId: String,
    layer: dev.projects.server.mod.ModStackingLayer,
    minimum: Double,
    maximum: Double,
) = ModDefinition(
    modId = "projects:$id",
    rank = rank,
    allowedSlots = setOf(EquipmentSlot.WEAPON),
    requiredTags = emptySet(),
    excludedTags = emptySet(),
    statId = "projects:$statId",
    minimumValue = minimum,
    maximumValue = maximum,
    stackingLayer = layer,
    definitionRevision = 1,
)

val V0_LOOT_MOD_POOL = listOf(
    lootDefinition("keen-edge", ModRank.RANK_1, "physical-attack", dev.projects.server.mod.ModStackingLayer.BASE_FLAT, 1.0, 3.0),
    lootDefinition("critical-focus", ModRank.RANK_1, "critical-chance", dev.projects.server.mod.ModStackingLayer.INCREASED, 1.0, 4.0),
    lootDefinition("skill-drive", ModRank.RANK_1, "skill-power", dev.projects.server.mod.ModStackingLayer.INCREASED, 2.0, 6.0),
    lootDefinition("execution-mark", ModRank.RANK_1, "boss-damage", dev.projects.server.mod.ModStackingLayer.CONDITIONAL, 2.0, 8.0),
    lootDefinition("steady-hand", ModRank.RANK_1, "attack-speed", dev.projects.server.mod.ModStackingLayer.INCREASED, 1.0, 3.0),
    lootDefinition("shatter-point", ModRank.RANK_1, "shatter-damage", dev.projects.server.mod.ModStackingLayer.CONDITIONAL, 2.0, 7.0),
    lootDefinition("frostbite", ModRank.RANK_1, "ice-damage", dev.projects.server.mod.ModStackingLayer.BASE_FLAT, 2.0, 6.0),
    lootDefinition("last-stand", ModRank.RANK_1, "conditional-damage", dev.projects.server.mod.ModStackingLayer.CONDITIONAL, 3.0, 9.0),
    lootDefinition("keen-edge-2", ModRank.RANK_2, "physical-attack", dev.projects.server.mod.ModStackingLayer.BASE_FLAT, 2.0, 5.0),
    lootDefinition("critical-focus-2", ModRank.RANK_2, "critical-chance", dev.projects.server.mod.ModStackingLayer.INCREASED, 2.0, 5.0),
    lootDefinition("skill-drive-2", ModRank.RANK_2, "skill-power", dev.projects.server.mod.ModStackingLayer.INCREASED, 2.0, 7.0),
    lootDefinition("execution-mark-2", ModRank.RANK_2, "boss-damage", dev.projects.server.mod.ModStackingLayer.CONDITIONAL, 3.0, 10.0),
    lootDefinition("last-stand-2", ModRank.RANK_2, "conditional-damage", dev.projects.server.mod.ModStackingLayer.CONDITIONAL, 3.0, 11.0),
)
