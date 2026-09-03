package dev.projects.server.equipment

import dev.projects.server.mod.ModDefinition
import dev.projects.server.mod.ModEntry
import dev.projects.server.mod.ModStackingLayer
import dev.projects.server.mod.ModValidation
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

internal data class EquipmentTooltipMod(
    val name: String,
    val value: String,
)

internal data class EquipmentTooltipModel(
    val tierRarity: String,
    val baseStats: List<String>,
    val mods: List<EquipmentTooltipMod>,
    val marketValue: Long,
    val tooltipStyle: String,
    val rarityColor: TextColor,
) {
    fun lore(): List<Component> = buildList {
        add(tooltipLine(tierRarity, rarityColor, bold = true))
        add(Component.empty())

        add(tooltipLine("基本性能", HEADER_COLOR, bold = true))
        if (baseStats.isEmpty()) {
            add(tooltipLine("  なし", MUTED_COLOR))
        } else {
            baseStats.forEach { add(tooltipLine("  $it", BODY_COLOR)) }
        }

        add(Component.empty())
        add(tooltipLine("MOD", HEADER_COLOR, bold = true))
        if (mods.isEmpty()) {
            add(tooltipLine("  なし", MUTED_COLOR))
        } else {
            mods.forEach { mod ->
                add(tooltipLine("  ${mod.name}", VALUE_COLOR, bold = true))
                add(tooltipLine("    ${mod.value}", BODY_COLOR))
            }
        }

        add(Component.empty())
        add(tooltipLine("市場価値", HEADER_COLOR, bold = true))
        add(tooltipLine("  推定 ${formatCurrency(marketValue)} G", MARKET_COLOR, bold = true))
    }
}

internal fun EquipmentItem.toTooltipModel(
    definitions: Map<String, ModDefinition>,
): EquipmentTooltipModel {
    val validMods = modSlots.mapNotNull { slot ->
        val entry = slot.entry as? ModEntry ?: return@mapNotNull null
        val definition = definitions[entry.modId] ?: return@mapNotNull null
        if (!ModValidation.validate(entry, definition, this.slot).valid) return@mapNotNull null
        EquipmentTooltipMod(
            name = "${modDisplayName(entry.modId)} ${rankRoman(entry.rank.value)}",
            value = formatModValue(entry.rolledValue, definition),
        )
    }

    return EquipmentTooltipModel(
        tierRarity = "${tierDisplayName(tier)} • ${rarity.name}",
        baseStats = baseStatRolls
            .sortedBy { it.statId }
            .map { "${formatDecimal(it.value)} ${statDisplayName(it.statId)}" },
        mods = validMods,
        marketValue = ProvisionalEquipmentMarketValue.estimate(this, definitions),
        tooltipStyle = "projects:${rarity.name.lowercase(Locale.ROOT)}",
        rarityColor = rarityColor(rarity),
    )
}

internal fun equipmentTooltipName(
    displayName: String,
    rarity: EquipmentRarity,
): Component = tooltipLine(displayName, rarityColor(rarity), bold = true)

private object ProvisionalEquipmentMarketValue {
    fun estimate(
        item: EquipmentItem,
        definitions: Map<String, ModDefinition>,
    ): Long {
        val tierBase = when (item.tier) {
            EquipmentTier.T1 -> 200.0
            EquipmentTier.T2 -> 1_000.0
            EquipmentTier.T3 -> 4_000.0
        }
        val itemLevelValue = item.itemLevel * when (item.tier) {
            EquipmentTier.T1 -> 20.0
            EquipmentTier.T2 -> 30.0
            EquipmentTier.T3 -> 45.0
        }
        val baseRollValue = item.baseStatRolls.sumOf { abs(it.value) * 20.0 }
        val installedModValue = item.modSlots.sumOf { slot ->
            val entry = slot.entry as? ModEntry ?: return@sumOf 0.0
            val definition = definitions[entry.modId] ?: return@sumOf 0.0
            if (!ModValidation.validate(entry, definition, item.slot).valid) return@sumOf 0.0
            120.0 * entry.rank.value + abs(entry.rolledValue) * 40.0
        }
        val rarityMultiplier = when (item.rarity) {
            EquipmentRarity.COMMON -> 1.0
            EquipmentRarity.UNCOMMON -> 1.35
            EquipmentRarity.RARE -> 1.8
            EquipmentRarity.EPIC -> 2.5
        }

        val raw = (tierBase + itemLevelValue + baseRollValue + installedModValue) * rarityMultiplier
        return ((raw / 10.0).roundToLong() * 10L).coerceAtLeast(10L)
    }
}

private fun formatModValue(
    value: Double,
    definition: ModDefinition,
): String {
    val percent = definition.stackingLayer == ModStackingLayer.BASE_PERCENT ||
        definition.stackingLayer == ModStackingLayer.INCREASED
    val suffix = if (percent) "%" else ""
    val prefix = if (value >= 0.0) "+" else ""
    return "$prefix${formatDecimal(value)}$suffix ${statDisplayName(definition.statId)}"
}

private fun statDisplayName(statId: String): String = when (statId) {
    "projects:physical-attack" -> "攻撃力"
    "projects:magic-attack" -> "魔力"
    "projects:attack-speed" -> "攻撃速度"
    "projects:critical-strike" -> "クリティカル率"
    "projects:max-health" -> "最大HP"
    "projects:armor" -> "防御力"
    "projects:magic-resist" -> "魔法防御"
    else -> humanizeCanonicalId(statId)
}

private fun modDisplayName(modId: String): String = when (modId) {
    "projects:keen-edge" -> "鋭刃"
    else -> humanizeCanonicalId(modId)
}

private fun humanizeCanonicalId(id: String): String =
    id.substringAfter(':')
        .replace('_', ' ')
        .replace('-', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.substring(0, 1).uppercase(Locale.ROOT) + token.substring(1)
        }

private fun tierDisplayName(tier: EquipmentTier): String = when (tier) {
    EquipmentTier.T1 -> "TIER I"
    EquipmentTier.T2 -> "TIER II"
    EquipmentTier.T3 -> "TIER III"
}

private fun rankRoman(rank: Int): String = when (rank) {
    1 -> "I"
    2 -> "II"
    3 -> "III"
    else -> rank.toString()
}

private fun formatDecimal(value: Double): String {
    val rounded = value.roundToLong()
    if (abs(value - rounded.toDouble()) < 0.000_001) return rounded.toString()
    return String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
}

private fun formatCurrency(value: Long): String =
    String.format(Locale.ROOT, "%,d", value)

private fun rarityColor(rarity: EquipmentRarity): TextColor = when (rarity) {
    EquipmentRarity.COMMON -> TextColor.color(0xA7ADB4)
    EquipmentRarity.UNCOMMON -> TextColor.color(0x6FAF95)
    EquipmentRarity.RARE -> TextColor.color(0x6EA7D6)
    EquipmentRarity.EPIC -> TextColor.color(0xB58AC6)
}

private fun tooltipLine(
    text: String,
    color: TextColor,
    bold: Boolean = false,
): Component {
    var component = Component.text(text, color)
        .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE)
    if (bold) component = component.decorate(TextDecoration.BOLD)
    return component
}

private val HEADER_COLOR = TextColor.color(0xD8D3C5)
private val BODY_COLOR = TextColor.color(0xC9CDD1)
private val VALUE_COLOR = TextColor.color(0xE4E7E9)
private val MUTED_COLOR = TextColor.color(0x858B93)
private val MARKET_COLOR = TextColor.color(0xD0B36A)
