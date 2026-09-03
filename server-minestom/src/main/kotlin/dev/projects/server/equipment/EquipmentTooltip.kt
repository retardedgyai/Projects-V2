package dev.projects.server.equipment

import dev.projects.server.mod.ModDefinition
import dev.projects.server.mod.ModEntry
import dev.projects.server.mod.ModStackingLayer
import dev.projects.server.mod.ModValidation
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

data class EquipmentTooltipStatRow(
    val statId: String,
    val label: String,
    val valueText: String,
)

data class EquipmentTooltipModRow(
    val modId: String,
    val displayName: String,
    val rankLabel: String,
    val effectText: String,
)

data class EquipmentTooltipModel(
    val tierLabel: String,
    val rarityLabel: String,
    val tooltipStyleId: String,
    val baseStats: List<EquipmentTooltipStatRow>,
    val mods: List<EquipmentTooltipModRow>,
    val estimatedMarketValue: Long,
)

object EquipmentMarketValuePolicy {
    private const val MAX_ESTIMATE = 9_000_000_000_000_000.0

    fun estimate(item: EquipmentItem, definitions: Map<String, ModDefinition>): Long {
        val tierBase = when (item.tier) {
            EquipmentTier.T1 -> 750.0
            EquipmentTier.T2 -> 3_500.0
            EquipmentTier.T3 -> 9_000.0
        }
        val levelPremium = item.itemLevel * when (item.tier) {
            EquipmentTier.T1 -> 50.0
            EquipmentTier.T2 -> 90.0
            EquipmentTier.T3 -> 160.0
        }
        val baseRollPremium = item.baseStatRolls.sumOf { roll ->
            abs(roll.value).coerceAtMost(10_000.0) * 35.0
        }
        val modPremium = item.validatedMods(definitions).sumOf { (entry, definition) ->
            val range = definition.maximumValue - definition.minimumValue
            val rollQuality = if (range == 0.0) {
                1.0
            } else {
                ((entry.rolledValue - definition.minimumValue) / range).coerceIn(0.0, 1.0)
            }
            500.0 + rollQuality * 500.0
        }
        val rarityMultiplier = when (item.rarity) {
            EquipmentRarity.COMMON -> 1.0
            EquipmentRarity.UNCOMMON -> 1.20
            EquipmentRarity.RARE -> 1.55
            EquipmentRarity.EPIC -> 2.05
        }
        return ((tierBase + levelPremium + baseRollPremium + modPremium) * rarityMultiplier)
            .coerceIn(0.0, MAX_ESTIMATE)
            .roundToLong()
    }
}

fun EquipmentRarity.tooltipStyleId(): String = when (this) {
    EquipmentRarity.COMMON -> "projects:item_common"
    EquipmentRarity.UNCOMMON -> "projects:item_uncommon"
    EquipmentRarity.RARE -> "projects:item_rare"
    EquipmentRarity.EPIC -> "projects:item_epic"
}

fun EquipmentItem.toTooltipModel(definitions: Map<String, ModDefinition>): EquipmentTooltipModel {
    val baseStats = baseStatRolls
        .sortedWith(compareBy<BaseStatRoll> { statPresentation(it.statId).order }.thenBy { it.statId })
        .map { roll ->
            val presentation = statPresentation(roll.statId)
            EquipmentTooltipStatRow(
                statId = roll.statId,
                label = presentation.label,
                valueText = presentation.formatBaseValue(roll.value),
            )
        }
    val mods = validatedMods(definitions).map { (entry, definition) ->
        val presentation = statPresentation(definition.statId)
        EquipmentTooltipModRow(
            modId = entry.modId,
            displayName = MOD_NAMES[entry.modId] ?: readableId(entry.modId),
            rankLabel = roman(entry.rank.value),
            effectText = formatModEffect(entry.rolledValue, presentation.label, definition.stackingLayer),
        )
    }
    return EquipmentTooltipModel(
        tierLabel = itemTierLabel(tier),
        rarityLabel = rarity.name,
        tooltipStyleId = rarity.tooltipStyleId(),
        baseStats = baseStats,
        mods = mods,
        estimatedMarketValue = EquipmentMarketValuePolicy.estimate(this, definitions),
    )
}

internal fun EquipmentTooltipModel.toLore(): List<Component> = buildList {
    add(heading("$tierLabel • $rarityLabel", rarityColor(rarityLabel)))
    add(Component.empty())
    add(section("基本性能"))
    baseStats.forEach { stat -> add(statLine(stat)) }
    add(Component.empty())
    add(section("MOD"))
    if (mods.isEmpty()) {
        add(line("  なし", MUTED_COLOR))
    } else {
        mods.forEach { mod ->
            add(line("  ${mod.displayName} ${mod.rankLabel}", MOD_NAME_COLOR))
            add(line("    ${mod.effectText}", MOD_VALUE_COLOR))
        }
    }
    add(Component.empty())
    add(section("市場価値"))
    add(line("  推定 ${String.format(Locale.US, "%,d", estimatedMarketValue)} G", MARKET_COLOR))
}

internal fun EquipmentRarity.nameColor(): TextColor = when (this) {
    EquipmentRarity.COMMON -> NamedTextColor.WHITE
    EquipmentRarity.UNCOMMON -> TextColor.color(0x66B28B)
    EquipmentRarity.RARE -> TextColor.color(0x5BA9D3)
    EquipmentRarity.EPIC -> TextColor.color(0xB884E0)
}

private data class ValidatedMod(val entry: ModEntry, val definition: ModDefinition)

private fun EquipmentItem.validatedMods(definitions: Map<String, ModDefinition>): List<ValidatedMod> =
    modSlots.mapNotNull { slot ->
        val entry = slot.entry as? ModEntry ?: return@mapNotNull null
        val definition = definitions[entry.modId] ?: return@mapNotNull null
        if (!ModValidation.validate(entry, definition, this.slot).valid) return@mapNotNull null
        ValidatedMod(entry, definition)
    }.sortedBy { it.entry.slotIndex }

private enum class BaseValueUnit { DECIMAL, PERCENT }

private data class StatPresentation(
    val label: String,
    val order: Int,
    val baseValueUnit: BaseValueUnit = BaseValueUnit.DECIMAL,
) {
    fun formatBaseValue(value: Double): String = when (baseValueUnit) {
        BaseValueUnit.DECIMAL -> formatNumber(value)
        BaseValueUnit.PERCENT -> "${formatNumber(value)}%"
    }
}

private val STAT_PRESENTATIONS = mapOf(
    "projects:physical-attack" to StatPresentation("攻撃力", 0),
    "projects:attack-speed" to StatPresentation("攻撃速度", 1),
    "projects:critical-chance" to StatPresentation("クリティカル率", 2, BaseValueUnit.PERCENT),
    "projects:defense" to StatPresentation("防御力", 3),
    "projects:health" to StatPresentation("体力", 4),
    "projects:magic-power" to StatPresentation("魔力", 5),
    "projects:mana" to StatPresentation("マナ", 6),
)

private val MOD_NAMES = mapOf(
    "projects:keen-edge" to "鋭刃",
    "projects:gale" to "疾風",
)

private val SECTION_COLOR = TextColor.color(0x979EA2)
private val VALUE_COLOR = TextColor.color(0xEBEBE5)
private val MUTED_COLOR = TextColor.color(0x777D80)
private val MOD_NAME_COLOR = TextColor.color(0xD2D9DB)
private val MOD_VALUE_COLOR = TextColor.color(0x7EC2AE)
private val MARKET_COLOR = TextColor.color(0xD5B974)
private val TOOLTIP_ICON_FONT = Key.key("projects", "tooltip_icons")
private val STAT_ICONS = mapOf(
    "projects:physical-attack" to '\uE001',
    "projects:attack-speed" to '\uE002',
    "projects:critical-chance" to '\uE003',
    "projects:defense" to '\uE004',
    "projects:health" to '\uE005',
    "projects:magic-power" to '\uE006',
    "projects:mana" to '\uE007',
)

private fun statPresentation(statId: String): StatPresentation =
    STAT_PRESENTATIONS[statId] ?: StatPresentation(readableId(statId), Int.MAX_VALUE)

private fun readableId(canonicalId: String): String = canonicalId.substringAfter(':')
    .split('-')
    .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.US) } }

private fun itemTierLabel(tier: EquipmentTier): String = when (tier) {
    EquipmentTier.T1 -> "TIER I"
    EquipmentTier.T2 -> "TIER II"
    EquipmentTier.T3 -> "TIER III"
}

private fun roman(value: Int): String = when (value) {
    1 -> "I"
    2 -> "II"
    3 -> "III"
    else -> value.toString()
}

private fun formatModEffect(value: Double, statLabel: String, layer: ModStackingLayer): String {
    val signedValue = if (value >= 0.0) "+${formatNumber(value)}" else formatNumber(value)
    return when (layer) {
        ModStackingLayer.BASE_FLAT -> "$signedValue $statLabel"
        ModStackingLayer.BASE_PERCENT,
        ModStackingLayer.INCREASED,
        ModStackingLayer.FINAL,
        -> "$signedValue% $statLabel"
        ModStackingLayer.CONDITIONAL -> "$signedValue $statLabel（条件付き）"
    }
}

private fun formatNumber(value: Double): String {
    val absolute = abs(value)
    return when {
        abs(value - value.roundToLong()) < 0.000_001 -> String.format(Locale.US, "%.0f", value)
        absolute < 10.0 -> String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        else -> String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')
    }
}

private fun section(text: String): Component = heading(text, SECTION_COLOR)

private fun statLine(stat: EquipmentTooltipStatRow): Component {
    val icon = STAT_ICONS[stat.statId] ?: return line("  ${stat.valueText} ${stat.label}", VALUE_COLOR)
    return Component.empty()
        .decoration(TextDecoration.ITALIC, false)
        .decoration(TextDecoration.BOLD, false)
        .append(
            Component.text(" $icon ", NamedTextColor.WHITE)
                .font(TOOLTIP_ICON_FONT)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, false),
        )
        .append(line("${stat.valueText} ${stat.label}", VALUE_COLOR))
}

private fun heading(text: String, color: TextColor): Component = line(text, color)
    .decoration(TextDecoration.BOLD, true)

private fun line(text: String, color: TextColor): Component = Component.text(text, color)
    .decoration(TextDecoration.ITALIC, false)
    .decoration(TextDecoration.BOLD, false)

private fun rarityColor(rarityLabel: String): TextColor =
    EquipmentRarity.valueOf(rarityLabel).nameColor()
