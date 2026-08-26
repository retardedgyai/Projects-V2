package dev.projects.client

import dev.projects.protocol.EquipmentPresentationCodec
import dev.projects.protocol.EquipmentPresentationMod
import dev.projects.protocol.EquipmentPresentationSnapshot
import dev.projects.protocol.EquipmentPresentationStat
import net.minecraft.core.component.DataComponents
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.util.Base64
import java.util.Locale

private const val PRESENTATION_TAG = "projects_equipment_presentation"

data class EquipmentTooltipModel(
    val snapshot: EquipmentPresentationSnapshot,
) {
    fun lines(): List<Component> {
        val primary = snapshot.baseStats.firstOrNull { it.statId.contains("attack", ignoreCase = true) }
        val lines = buildList {
            add(Component.literal(snapshot.displayName).withStyle(rarityColor(snapshot.rarity), ChatFormatting.BOLD))
            add(
                Component.literal(
                    "${snapshot.rarity.uppercase(Locale.ROOT)}  •  TIER ${snapshot.tier.uppercase(Locale.ROOT)}",
                ).withStyle(rarityColor(snapshot.rarity)),
            )
            add(Component.literal("Item Level ${snapshot.itemLevel}").withStyle(ChatFormatting.GRAY))
            if (primary != null) {
                add(
                    Component.literal("${formatValue(primary.value)} ${statLabel(primary.statId).uppercase(Locale.ROOT)}")
                        .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
                )
            }
            if (snapshot.baseStats.isNotEmpty()) add(Component.literal("Base Stats").withStyle(ChatFormatting.GRAY))
            snapshot.baseStats.forEach { stat ->
                if (stat != primary) add(statLine(stat, ChatFormatting.GRAY))
            }
            snapshot.installedMods.forEach { mod ->
                add(modLine(mod))
            }
        }
        return lines
    }

    private fun statLine(stat: EquipmentPresentationStat, color: ChatFormatting): Component =
        Component.literal("+${formatValue(stat.value)} ${statLabel(stat.statId)}").withStyle(color)

    private fun modLine(mod: EquipmentPresentationMod): Component =
        Component.literal("${modLabel(mod.modId)} ${roman(mod.rank)}  +${formatValue(mod.rolledValue)} ${statLabel(mod.statId)}")
            .withStyle(ChatFormatting.AQUA)

    companion object {
        fun from(stack: ItemStack): EquipmentTooltipModel? = runCatching {
            val customData = stack.get(DataComponents.CUSTOM_DATA) ?: return null
            val encoded = customData.copyTag().getString(PRESENTATION_TAG).orElse(null) ?: return null
            val bytes = Base64.getDecoder().decode(encoded)
            EquipmentPresentationCodec.decodeOrNull(bytes)?.let(::EquipmentTooltipModel)
        }.getOrNull()

        fun formatValue(value: Double): String = if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.ROOT, "%.1f", value)
        }

        fun statLabel(id: String): String = when (id.substringAfterLast(':').lowercase(Locale.ROOT)) {
            "physical-attack" -> "Physical Attack"
            "magical-attack" -> "Magical Attack"
            "critical-chance" -> "Critical Chance"
            "critical-damage" -> "Critical Damage"
            "attack-speed" -> "Attack Speed"
            else -> id.substringAfterLast(':').replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }
        }

        fun modLabel(id: String): String = id.substringAfterLast(':').replace('-', ' ').replace('_', ' ')
            .split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

        fun rarityColor(rarity: String): ChatFormatting = when (rarity.lowercase(Locale.ROOT)) {
            "uncommon" -> ChatFormatting.GREEN
            "rare" -> ChatFormatting.BLUE
            "epic" -> ChatFormatting.LIGHT_PURPLE
            else -> ChatFormatting.WHITE
        }

        private fun roman(rank: Int): String = when (rank) {
            1 -> "I"
            2 -> "II"
            3 -> "III"
            4 -> "IV"
            5 -> "V"
            else -> rank.toString()
        }
    }
}
