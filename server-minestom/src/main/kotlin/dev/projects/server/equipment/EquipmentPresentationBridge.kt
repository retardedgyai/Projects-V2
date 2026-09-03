package dev.projects.server.equipment

import dev.projects.protocol.EquipmentPresentationCodec
import dev.projects.protocol.EquipmentPresentationMod
import dev.projects.protocol.EquipmentPresentationSnapshot
import dev.projects.protocol.EquipmentPresentationStat
import dev.projects.server.mod.ModDefinition
import dev.projects.server.mod.ModEntry
import dev.projects.server.mod.ModValidation
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.component.DataComponents
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import java.util.Base64

private val PRESENTATION_TAG = Tag.String("projects_equipment_presentation")

fun EquipmentItem.toPresentationSnapshot(
    displayName: String,
    definitions: Map<String, ModDefinition>,
): EquipmentPresentationSnapshot = EquipmentPresentationSnapshot(
    itemId = itemId,
    displayName = displayName,
    category = category.name.lowercase(),
    slot = slot.id,
    tier = tier.name,
    itemLevel = itemLevel,
    rarity = rarity.name.lowercase(),
    baseStats = baseStatRolls.map { EquipmentPresentationStat(it.statId, it.value) }.sortedBy { it.statId },
    installedMods = modSlots.mapNotNull { slot ->
        val entry = slot.entry as? ModEntry ?: return@mapNotNull null
        val definition = definitions[entry.modId] ?: return@mapNotNull null
        if (!ModValidation.validate(entry, definition, this.slot).valid) return@mapNotNull null
        EquipmentPresentationMod(
            slotIndex = slot.index,
            modId = entry.modId,
            rank = entry.rank.value,
            rolledValue = entry.rolledValue,
            statId = definition.statId,
            stackingLayer = definition.stackingLayer.name.lowercase(),
        )
    }.sortedBy { it.slotIndex },
)

fun EquipmentItem.toPresentationItemStack(
    material: Material,
    displayName: String,
    definitions: Map<String, ModDefinition>,
): ItemStack {
    val tooltip = toTooltipModel(definitions)
    val encoded = Base64.getEncoder().encodeToString(
        EquipmentPresentationCodec.encode(toPresentationSnapshot(displayName, definitions)),
    )
    return ItemStack.builder(material)
        .customName(
            Component.text(displayName, rarity.nameColor())
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true),
        )
        .lore(tooltip.toLore())
        .set(DataComponents.TOOLTIP_STYLE, tooltip.tooltipStyleId)
        .hideExtraTooltip()
        .set(PRESENTATION_TAG, encoded)
        .build()
}

fun ItemStack.readEquipmentPresentation(): EquipmentPresentationSnapshot? =
    getTag(PRESENTATION_TAG)?.let { encoded ->
        runCatching { EquipmentPresentationCodec.decodeOrNull(Base64.getDecoder().decode(encoded)) }.getOrNull()
    }
