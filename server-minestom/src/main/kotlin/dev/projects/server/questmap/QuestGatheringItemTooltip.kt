package dev.projects.server.questmap

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.component.DataComponents
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

internal enum class QuestGatheringResourceGrade(
    val rarityLabel: String,
    val resourceTypeLabel: String,
    val tooltipStyleId: String,
    val nameColor: TextColor,
) {
    COMMON(
        rarityLabel = "COMMON",
        resourceTypeLabel = "通常素材",
        tooltipStyleId = "projects:item_common",
        nameColor = NamedTextColor.WHITE,
    ),
    RARE(
        rarityLabel = "RARE",
        resourceTypeLabel = "希少素材",
        tooltipStyleId = "projects:item_rare",
        nameColor = TextColor.color(0x5BA9D3),
    ),
}

internal fun QuestGatheringDiscipline.resourceItem(
    tier: Int,
    grade: QuestGatheringResourceGrade,
    amount: Int = 1,
): ItemStack {
    require(tier >= 1) { "Gathering resource tier must be positive" }
    require(amount >= 1) { "Gathering resource amount must be positive" }

    val material: Material
    val resourceName: String
    when (grade) {
        QuestGatheringResourceGrade.COMMON -> {
            material = commonMaterial
            resourceName = commonResourceName
        }
        QuestGatheringResourceGrade.RARE -> {
            material = rareMaterial
            resourceName = rareResourceName
        }
    }

    return ItemStack.builder(material)
        .amount(amount)
        .customName(
            Component.text(resourceName, grade.nameColor)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true),
        )
        .lore(gatheringResourceLore(tier, grade))
        .set(DataComponents.TOOLTIP_STYLE, grade.tooltipStyleId)
        .hideExtraTooltip()
        .glowing(grade == QuestGatheringResourceGrade.RARE)
        .build()
}

private fun QuestGatheringDiscipline.gatheringResourceLore(
    tier: Int,
    grade: QuestGatheringResourceGrade,
): List<Component> = buildList {
    add(fixedWidthHeading("TIER ${roman(tier)} • ${grade.rarityLabel}", grade.nameColor))
    add(Component.empty())
    add(heading("素材情報", SECTION_COLOR))
    add(detailLine("採取系統", displayName))
    add(detailLine("素材区分", grade.resourceTypeLabel))
    add(Component.empty())
    add(heading("詳細情報", SECTION_COLOR))
    add(detailLine("内部Tier", "T$tier"))
    add(Component.empty())
    add(line("  採取素材・$displayName", FOOTER_COLOR))
}

private val SECTION_COLOR = TextColor.color(0x979EA2)
private val DETAIL_LABEL_COLOR = TextColor.color(0x80888B)
private val VALUE_COLOR = TextColor.color(0xEBEBE5)
private val FOOTER_COLOR = TextColor.color(0x777F82)
private val TOOLTIP_ICON_FONT = Key.key("projects", "tooltip_icons")
private const val TOOLTIP_MIN_CONTENT_WIDTH = 124
private const val DETAIL_TEXT_COLUMN_WIDTH = 96
private val SPACER_GLYPHS = listOf(
    32 to '\uE115',
    16 to '\uE114',
    8 to '\uE113',
    4 to '\uE112',
    2 to '\uE111',
    1 to '\uE110',
)

private fun detailLine(label: String, value: String): Component {
    val spacerWidth = (
        DETAIL_TEXT_COLUMN_WIDTH - approximateTextWidth(label) - approximateTextWidth(value)
    ).coerceAtLeast(8)
    return bodyLine()
        .append(line("  $label", DETAIL_LABEL_COLOR))
        .append(spacer(spacerWidth))
        .append(line(value, VALUE_COLOR))
}

private fun fixedWidthHeading(text: String, color: TextColor): Component = heading(text, color)
    .append(spacer((TOOLTIP_MIN_CONTENT_WIDTH - approximateTextWidth(text)).coerceAtLeast(0)))

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
    if (character.code <= 0x7F) 6 else 9
}

private fun roman(value: Int): String = when (value) {
    1 -> "I"
    2 -> "II"
    3 -> "III"
    else -> value.toString()
}

private fun bodyLine(): Component = Component.empty()
    .decoration(TextDecoration.ITALIC, false)
    .decoration(TextDecoration.BOLD, false)

private fun heading(text: String, color: TextColor): Component = line(text, color)
    .decoration(TextDecoration.BOLD, true)

private fun line(text: String, color: TextColor): Component = Component.text(text, color)
    .decoration(TextDecoration.ITALIC, false)
    .decoration(TextDecoration.BOLD, false)
