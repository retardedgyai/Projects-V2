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
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class EquipmentTooltipStatRow(
    val statId: String,
    val label: String,
    val valueText: String,
)

data class EquipmentTooltipModRow(
    val slotIndex: Int,
    val modId: String,
    val displayName: String,
    val rankLabel: String,
    val effectValueText: String,
    val effectLabel: String,
    val effectText: String,
    val rangeText: String,
    val rollQuality: Double,
)

data class EquipmentTooltipModel(
    val tierId: String,
    val tierLabel: String,
    val itemLevel: Int,
    val rarityLabel: String,
    val tooltipStyleId: String,
    val equipmentTypeLabel: String,
    val modCapacity: Int,
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

fun EquipmentItem.toTooltipModel(
    definitions: Map<String, ModDefinition>,
    equipmentTypeLabel: String = defaultEquipmentTypeLabel(),
): EquipmentTooltipModel {
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
        val effectValueText = formatModValue(entry.rolledValue, definition.stackingLayer)
        val effectLabel = formatModLabel(presentation.label, definition.stackingLayer)
        EquipmentTooltipModRow(
            slotIndex = entry.slotIndex,
            modId = entry.modId,
            displayName = MOD_NAMES[entry.modId] ?: readableId(entry.modId),
            rankLabel = roman(entry.rank.value),
            effectValueText = effectValueText,
            effectLabel = effectLabel,
            effectText = "$effectValueText $effectLabel",
            rangeText = formatModRange(definition),
            rollQuality = rollQuality(entry.rolledValue, definition.minimumValue, definition.maximumValue),
        )
    }
    return EquipmentTooltipModel(
        tierId = tier.name,
        tierLabel = itemTierLabel(tier),
        itemLevel = itemLevel,
        rarityLabel = rarity.name,
        tooltipStyleId = rarity.tooltipStyleId(),
        equipmentTypeLabel = equipmentTypeLabel,
        modCapacity = rarity.modCapacity,
        baseStats = baseStats,
        mods = mods,
        estimatedMarketValue = EquipmentMarketValuePolicy.estimate(this, definitions),
    )
}

internal fun EquipmentTooltipModel.toLore(): List<Component> = buildList {
    add(fixedWidthHeading("$tierLabel • $rarityLabel", rarityColor(rarityLabel)))
    add(Component.empty())
    add(section("基本性能"))
    baseStats.forEach { stat -> add(statLine(stat)) }
    add(Component.empty())
    add(modSection(mods, modCapacity))
    if (mods.isEmpty()) {
        add(line("  なし", MUTED_COLOR))
    } else {
        mods.forEach { mod ->
            add(modNameLine(mod))
            add(modEffectLine(mod))
        }
    }
    add(Component.empty())
    add(section("市場価値"))
    add(marketValueLine(estimatedMarketValue))
    add(Component.empty())
    add(section("詳細情報"))
    add(detailLine("アイテムレベル", itemLevel.toString()))
    add(detailLine("内部Tier", tierId))
    mods.forEach { mod -> add(modRangeLine(mod)) }
    add(Component.empty())
    add(line("  $equipmentTypeLabel", FOOTER_COLOR))
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
private val STAT_LABEL_COLOR = TextColor.color(0xB4B9BA)
private val VALUE_COLOR = TextColor.color(0xEBEBE5)
private val MUTED_COLOR = TextColor.color(0x777D80)
private val MOD_NAME_COLOR = TextColor.color(0xD2D9DB)
private val MOD_BRANCH_COLOR = TextColor.color(0x586164)
private val MOD_VALUE_LABEL_COLOR = TextColor.color(0x9EA8A7)
private val MARKET_COLOR = TextColor.color(0xD5B974)
private val FOOTER_COLOR = TextColor.color(0x777F82)
private val DETAIL_LABEL_COLOR = TextColor.color(0x80888B)
private val MOD_SLOT_FILLED_COLOR = TextColor.color(0x78BBA8)
private val MOD_SLOT_EMPTY_COLOR = TextColor.color(0x4E5659)
private val LOW_ROLL_COLOR = TextColor.color(0x9CA3A5)
private val MID_ROLL_COLOR = TextColor.color(0x78BBA8)
private val HIGH_ROLL_COLOR = TextColor.color(0x69BDD0)
private val PERFECT_ROLL_COLOR = TextColor.color(0xD8B962)
private val TOOLTIP_ICON_FONT = Key.key("projects", "tooltip_icons")
private const val TOOLTIP_MIN_CONTENT_WIDTH = 124
private const val STAT_TEXT_COLUMN_WIDTH = 96
private const val MARKET_ICON = '\uE008'
private const val MOD_ICON = '\uE009'
private val STAT_ICONS = mapOf(
    "projects:physical-attack" to '\uE001',
    "projects:attack-speed" to '\uE002',
    "projects:critical-chance" to '\uE003',
    "projects:defense" to '\uE004',
    "projects:health" to '\uE005',
    "projects:magic-power" to '\uE006',
    "projects:mana" to '\uE007',
)
private val STAT_VALUE_COLORS = mapOf(
    "projects:physical-attack" to TextColor.color(0xE39A91),
    "projects:attack-speed" to TextColor.color(0xDEC77C),
    "projects:critical-chance" to TextColor.color(0x79C6D8),
    "projects:defense" to TextColor.color(0x9DB2CF),
    "projects:health" to TextColor.color(0xE49AAA),
    "projects:magic-power" to TextColor.color(0xA7B9E2),
    "projects:mana" to TextColor.color(0x73B9DD),
)
private val SPACER_GLYPHS = listOf(
    32 to '\uE115',
    16 to '\uE114',
    8 to '\uE113',
    4 to '\uE112',
    2 to '\uE111',
    1 to '\uE110',
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

private fun EquipmentItem.defaultEquipmentTypeLabel(): String = when (category) {
    EquipmentCategory.WEAPON -> "武器・${slotLabel(slot)}"
    EquipmentCategory.ARMOR -> "防具・${slotLabel(slot)}"
    EquipmentCategory.ACCESSORY -> "アクセサリー・${slotLabel(slot)}"
}

private fun slotLabel(slot: EquipmentSlot): String = when (slot) {
    EquipmentSlot.WEAPON -> "武器枠"
    EquipmentSlot.HEAD -> "頭装備"
    EquipmentSlot.CHEST -> "胴装備"
    EquipmentSlot.LEGS -> "脚装備"
    EquipmentSlot.BOOTS -> "足装備"
    EquipmentSlot.NECKLACE -> "首飾り"
    EquipmentSlot.RING_1,
    EquipmentSlot.RING_2,
    -> "指輪"
}

private fun roman(value: Int): String = when (value) {
    1 -> "I"
    2 -> "II"
    3 -> "III"
    else -> value.toString()
}

private fun formatModValue(value: Double, layer: ModStackingLayer): String {
    val signedValue = if (value >= 0.0) "+${formatNumber(value)}" else formatNumber(value)
    return when (layer) {
        ModStackingLayer.BASE_FLAT,
        ModStackingLayer.CONDITIONAL,
        -> signedValue
        ModStackingLayer.BASE_PERCENT,
        ModStackingLayer.INCREASED,
        ModStackingLayer.FINAL,
        -> "$signedValue%"
    }
}

private fun formatModLabel(statLabel: String, layer: ModStackingLayer): String =
    if (layer == ModStackingLayer.CONDITIONAL) "$statLabel（条件付き）" else statLabel

private fun formatModRange(definition: ModDefinition): String {
    val minimum = formatModValue(definition.minimumValue, definition.stackingLayer)
    val maximum = formatModValue(definition.maximumValue, definition.stackingLayer).removePrefix("+")
    return "$minimum〜$maximum"
}

private fun rollQuality(value: Double, minimum: Double, maximum: Double): Double =
    if (maximum == minimum) 1.0 else ((value - minimum) / (maximum - minimum)).coerceIn(0.0, 1.0)

private fun formatNumber(value: Double): String {
    val absolute = abs(value)
    return when {
        abs(value - value.roundToLong()) < 0.000_001 -> String.format(Locale.US, "%.0f", value)
        absolute < 10.0 -> String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        else -> String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')
    }
}

private fun section(text: String): Component = heading(text, SECTION_COLOR)

private fun fixedWidthHeading(text: String, color: TextColor): Component = heading(text, color)
    .append(spacer((TOOLTIP_MIN_CONTENT_WIDTH - approximateTextWidth(text)).coerceAtLeast(0)))

private fun statLine(stat: EquipmentTooltipStatRow): Component {
    val iconGlyph = STAT_ICONS[stat.statId] ?: return line("  ${stat.valueText} ${stat.label}", VALUE_COLOR)
    val spacerWidth = (
        STAT_TEXT_COLUMN_WIDTH - approximateTextWidth(stat.label) - approximateTextWidth(stat.valueText)
    ).coerceAtLeast(8)
    return bodyLine()
        .append(line(" ", VALUE_COLOR))
        .append(icon(iconGlyph))
        .append(line(" ${stat.label}", STAT_LABEL_COLOR))
        .append(spacer(spacerWidth))
        .append(line(stat.valueText, STAT_VALUE_COLORS[stat.statId] ?: VALUE_COLOR))
}

private fun modSection(mods: List<EquipmentTooltipModRow>, capacity: Int): Component {
    val filledSlots = mods.mapTo(mutableSetOf()) { mod -> mod.slotIndex }
    var result = bodyLine()
        .append(heading("MOD", SECTION_COLOR))
        .append(line("  ", SECTION_COLOR))
    repeat(capacity) { slotIndex ->
        result = result.append(
            line(
                if (slotIndex in filledSlots) "◆ " else "◇ ",
                if (slotIndex in filledSlots) MOD_SLOT_FILLED_COLOR else MOD_SLOT_EMPTY_COLOR,
            ),
        )
    }
    return result
}

private fun modNameLine(mod: EquipmentTooltipModRow): Component = bodyLine()
    .append(line(" ", MOD_NAME_COLOR))
    .append(icon(MOD_ICON))
    .append(line(" ${mod.displayName} ${mod.rankLabel}", MOD_NAME_COLOR))

private fun modEffectLine(mod: EquipmentTooltipModRow): Component = bodyLine()
    .append(line("    └ ", MOD_BRANCH_COLOR))
    .append(line(mod.effectValueText, modRollColor(mod.rollQuality)))
    .append(line(" ${mod.effectLabel}", MOD_VALUE_LABEL_COLOR))

private fun marketValueLine(estimatedMarketValue: Long): Component {
    val label = "推定"
    val value = "${String.format(Locale.US, "%,d", estimatedMarketValue)} G"
    val spacerWidth = (
        STAT_TEXT_COLUMN_WIDTH - approximateTextWidth(label) - approximateTextWidth(value)
    ).coerceAtLeast(8)
    return bodyLine()
        .append(line(" ", MARKET_COLOR))
        .append(icon(MARKET_ICON))
        .append(line(" $label", DETAIL_LABEL_COLOR))
        .append(spacer(spacerWidth))
        .append(line(value, MARKET_COLOR))
}

private fun detailLine(label: String, value: String): Component {
    val spacerWidth = (
        STAT_TEXT_COLUMN_WIDTH - approximateTextWidth(label) - approximateTextWidth(value)
    ).coerceAtLeast(8)
    return bodyLine()
        .append(line("  $label", DETAIL_LABEL_COLOR))
        .append(spacer(spacerWidth))
        .append(line(value, VALUE_COLOR))
}

private fun modRangeLine(mod: EquipmentTooltipModRow): Component = bodyLine()
    .append(line("  ${mod.displayName} ${mod.rankLabel}  ", DETAIL_LABEL_COLOR))
    .append(line("範囲 ${mod.rangeText}", MUTED_COLOR))
    .append(line("  品質 ", DETAIL_LABEL_COLOR))
    .append(line("${(mod.rollQuality * 100.0).roundToInt()}%", modRollColor(mod.rollQuality)))

private fun modRollColor(quality: Double): TextColor = when {
    quality >= 0.90 -> PERFECT_ROLL_COLOR
    quality >= 0.70 -> HIGH_ROLL_COLOR
    quality >= 0.35 -> MID_ROLL_COLOR
    else -> LOW_ROLL_COLOR
}

private fun bodyLine(): Component = Component.empty()
    .decoration(TextDecoration.ITALIC, false)
    .decoration(TextDecoration.BOLD, false)

private fun icon(glyph: Char): Component = Component.text(glyph, NamedTextColor.WHITE)
    .font(TOOLTIP_ICON_FONT)
    .decoration(TextDecoration.ITALIC, false)
    .decoration(TextDecoration.BOLD, false)

private fun spacer(width: Int): Component {
    var remaining = width
    val glyphs = buildString {
        SPACER_GLYPHS.forEach { (advance, glyph) ->
            while (remaining >= advance) {
                append(glyph)
                remaining -= advance
            }
        }
    }
    return Component.text(glyphs, NamedTextColor.WHITE)
        .font(TOOLTIP_ICON_FONT)
        .decoration(TextDecoration.ITALIC, false)
        .decoration(TextDecoration.BOLD, false)
}

private fun approximateTextWidth(text: String): Int = text.sumOf { character ->
    when {
        character == '.' || character == ',' -> 2
        character.code <= 0x7F -> 6
        else -> 9
    }
}

private fun heading(text: String, color: TextColor): Component = line(text, color)
    .decoration(TextDecoration.BOLD, true)

private fun line(text: String, color: TextColor): Component = Component.text(text, color)
    .decoration(TextDecoration.ITALIC, false)
    .decoration(TextDecoration.BOLD, false)

private fun rarityColor(rarityLabel: String): TextColor =
    EquipmentRarity.valueOf(rarityLabel).nameColor()
