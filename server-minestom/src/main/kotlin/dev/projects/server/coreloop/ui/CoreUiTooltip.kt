package dev.projects.server.coreloop.ui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.component.DataComponents
import net.minestom.server.item.ItemStack

/** Server-authored lore with a centered title. No client mixin and no Shift-only information. */
object CoreUiTooltip {
    fun apply(item: ItemStack, model: CoreTooltipModel, packed: Boolean): ItemStack {
        val rendered = render(model, packed)
        var result = item.withCustomName(rendered.title).withLore(rendered.lore).withoutExtraTooltip()
        result = if (packed) result.with(DataComponents.TOOLTIP_STYLE, model.rarity.style)
            else result.without(DataComponents.TOOLTIP_STYLE)
        return result
    }

    data class Rendered(val title: Component, val lore: List<Component>, val contentWidth: Int)

    fun render(model: CoreTooltipModel, packed: Boolean): Rendered {
        val allLines = buildList {
            add("${model.rarity.name}  ・  ${model.typeLabel}")
            model.stats.forEach { add("${it.label}   ${it.value}") }
            model.affixes.forEach { add("${it.name} ${it.rank}"); add(it.effect); add("範囲 ${it.range}  品質 ${it.qualityPercent.coerceIn(0, 100)}%") }
            add("アイテムレベル ${model.itemLevel}  /  内部Tier T${model.tier}")
            addAll(model.footer)
        }
        val width = maxOf(154, CoreUiComponents.width(model.name, true), allLines.maxOfOrNull { CoreUiComponents.width(it) + 16 } ?: 154)
        val nameWidth = CoreUiComponents.width(model.name, true)
        val pad = ((width - nameWidth) / 2).coerceAtLeast(0)
        val title = if (packed) Component.empty().append(CoreUiComponents.space(pad))
            .append(CoreUiComponents.text(model.name, model.rarity.color, true))
        else CoreUiComponents.text(" ".repeat(pad / 4) + model.name, model.rarity.color, true)
        val lore = buildList {
            val rarity = "${model.rarity.name}  ・  ${model.typeLabel}"
            add(CoreUiComponents.text(rarity, model.rarity.color, true))
            add(divider(width, packed))
            if (model.stats.isNotEmpty()) {
                add(CoreUiComponents.text("基本性能", CoreUiComponents.GOLD, true))
                model.stats.forEach { stat ->
                    add(Component.empty().append(CoreUiComponents.icon(stat.icon, packed))
                        .append(CoreUiComponents.text(" ${stat.label}  ", CoreUiComponents.MUTED))
                        .append(CoreUiComponents.text(stat.value)))
                }
                add(Component.empty())
            }
            if (model.modCapacity > 0 || model.affixes.isNotEmpty()) {
                add(CoreUiComponents.text("MOD  ", CoreUiComponents.GOLD, true)
                    .append(CoreUiComponents.text("◆".repeat(model.affixes.size) + "◇".repeat((model.modCapacity - model.affixes.size).coerceAtLeast(0)), model.rarity.color)))
                if (model.affixes.isEmpty()) add(CoreUiComponents.text("  未装着 — 工房で刻印できます", CoreUiComponents.MUTED))
                model.affixes.forEach { affix ->
                    val quality = affix.qualityPercent.coerceIn(0, 100)
                    add(Component.empty().append(CoreUiComponents.icon(CoreUiIcon.MOD, packed))
                        .append(CoreUiComponents.text(" ${affix.name} ${affix.rank}", CoreUiComponents.IVORY, true)))
                    add(CoreUiComponents.text("  └ ${affix.effect}", qualityColor(quality)))
                    add(CoreUiComponents.text("    範囲 ${affix.range}  品質 $quality%", CoreUiComponents.MUTED))
                }
                add(Component.empty())
            }
            add(CoreUiComponents.text("詳細情報", CoreUiComponents.GOLD, true))
            add(CoreUiComponents.text("アイテムレベル ${model.itemLevel}  /  内部Tier T${model.tier}", CoreUiComponents.MUTED))
            model.footer.forEach { add(CoreUiComponents.text(it, CoreUiComponents.MUTED)) }
        }
        return Rendered(title, lore, width)
    }

    private fun divider(width: Int, packed: Boolean): Component =
        if (packed) CoreUiComponents.text(" ").append(CoreUiComponents.space(width - 4))
        else CoreUiComponents.text("─".repeat((width / 9).coerceAtLeast(1)), TextColor.color(0x5E5542))

    private fun qualityColor(quality: Int): TextColor = when {
        quality >= 90 -> CoreUiComponents.GOLD
        quality >= 70 -> CoreUiComponents.BLUE
        quality >= 35 -> TextColor.color(0x99C5A6)
        else -> CoreUiComponents.IVORY
    }
}
