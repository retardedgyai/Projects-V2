package dev.projects.server.coreloop

import dev.projects.server.coreloop.ui.*
import net.kyori.adventure.text.Component
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.component.DataComponents

/** Same rarity skin and centered headline as equipment, without irrelevant item-level fields. */
internal object CoreSkillTooltip {
    fun item(skill: CoreSkillDefinition, sheet: CoreCombatSheet, j: CoreJourney, packed: Boolean, locked: Boolean = false,
        footer: String = if(locked) "成長するとこの枠を使えます" else "港の成長と職業 → 技選びで変更"): ItemStack {
        val rarity = if(skill.ultimate) CoreUiRarity.EPIC else CoreUiRarity.RARE
        val warrior = j.job == CoreClass.WARRIOR
        val lines = if(warrior) CoreWarriorSkillPresentation.tooltip(skill,sheet,j) else skill.tooltip(sheet,j)
        val name = (if(locked) "【未解放】" else "")+skill.name
        fun wrap(line: String): List<String> = buildList {
            var part="";var width=0
            for(c in line) {
                val advance=if(c.code in 0x20..0x7e) 6 else 9
                if(width+advance>270) { add(part);part="";width=0 }
                part+=c;width+=advance
            }
            if(part.isNotEmpty()) add(part)
        }
        val lore=buildList {
            add(CoreUiComponents.text("${j.job.displayName}  ·  ${if(skill.ultimate) "奥義" else "選択技能"}",rarity.color,true))
            add(Component.empty())
            lines.forEachIndexed { index,line ->
                val heading = if(warrior) index in setOf(0,3,4) else index==2
                val color=if(warrior) when(index) { 0,3,4 -> CoreUiComponents.GOLD;1,5 -> CoreUiComponents.IVORY;else -> CoreUiComponents.MUTED }
                    else when(index) { 2 -> CoreUiComponents.GOLD;3 -> CoreUiComponents.IVORY;5,6 -> CoreUiComponents.BLUE;else -> CoreUiComponents.MUTED }
                if(line.isEmpty()) add(Component.empty())
                else wrap(line).forEach { add(CoreUiComponents.text(it,color,heading)) }
            }
            add(Component.empty())
            add(CoreUiComponents.text(footer,CoreUiComponents.GOLD))
        }
        val width=maxOf(192,CoreUiComponents.width(footer),lines.flatMap(::wrap).maxOf { CoreUiComponents.width(it) },CoreUiComponents.width(name,true))
        val pad=((width-CoreUiComponents.width(name,true))/2).coerceAtLeast(0)
        val title=(if(packed) CoreUiComponents.space(pad) else CoreUiComponents.text(" ".repeat(pad/4)))
            .append(CoreUiComponents.text(name,rarity.color,true))
        var item=ItemStack.of(Material.PAPER).withCustomName(title).withLore(lore).withoutExtraTooltip()
        if(packed) item=item.withItemModel("projects:core_ui/${skill.icon}").with(DataComponents.TOOLTIP_STYLE,rarity.style)
        return item
    }
}
