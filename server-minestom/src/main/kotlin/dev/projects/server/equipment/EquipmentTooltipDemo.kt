package dev.projects.server.equipment

import dev.projects.server.mod.AttackTag
import dev.projects.server.mod.ModDefinition
import dev.projects.server.mod.ModEntry
import dev.projects.server.mod.ModRank
import dev.projects.server.mod.ModStackingLayer
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

private val TOOLTIP_DEMO_DEFINITIONS = mapOf(
    "projects:gale" to ModDefinition(
        modId = "projects:gale",
        rank = ModRank.RANK_2,
        allowedSlots = setOf(EquipmentSlot.WEAPON),
        requiredTags = setOf(AttackTag.MELEE),
        excludedTags = emptySet(),
        statId = "projects:attack-speed",
        minimumValue = 10.0,
        maximumValue = 15.0,
        stackingLayer = ModStackingLayer.INCREASED,
        definitionRevision = 1,
    ),
)

internal fun tooltipDemoItemStacks(): List<ItemStack> = EquipmentRarity.entries.map { rarity ->
    val item = EquipmentItem(
        itemId = "projects:tooltip-demo-${rarity.name.lowercase()}",
        category = EquipmentCategory.WEAPON,
        slot = EquipmentSlot.WEAPON,
        tier = EquipmentTier.T2,
        itemLevel = 24,
        rarity = rarity,
        baseStatRolls = listOf(
            BaseStatRoll("projects:physical-attack", 42.8),
            BaseStatRoll("projects:attack-speed", 1.45),
            BaseStatRoll("projects:critical-chance", 6.0),
        ),
        modSlots = List(rarity.modCapacity) { index ->
            if (index == 0) {
                EquipmentModSlot(
                    index,
                    ModEntry("projects:gale", ModRank.RANK_2, 12.4, index, definitionRevision = 1),
                )
            } else {
                EquipmentModSlot.empty(index)
            }
        },
    )
    item.toPresentationItemStack(
        material = Material.IRON_SWORD,
        displayName = "双刃",
        definitions = TOOLTIP_DEMO_DEFINITIONS,
    )
}
