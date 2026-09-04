package dev.projects.server.coreloop

import dev.projects.server.questmap.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import java.util.UUID
import kotlin.math.roundToInt

internal object CoreLoopItems {
    val actionTag: Tag<String> = Tag.String("projects_core_action")
    val ownedMapTag: Tag<String> = Tag.String("projects_core_owned_map")
    val colors = listOf(NamedTextColor.WHITE, NamedTextColor.GREEN, NamedTextColor.AQUA, NamedTextColor.LIGHT_PURPLE)

    fun text(value: String, color: NamedTextColor = NamedTextColor.GRAY): Component =
        Component.text(value, color).decoration(TextDecoration.ITALIC, false)

    fun icon(material: Material, name: String, vararg lore: String, color: NamedTextColor = NamedTextColor.GOLD): ItemStack =
        ItemStack.builder(material).customName(text(name, color)).lore(lore.map { text(it) }).build()

    fun resourceMaterial(resource: CoreResource): Material = when (resource) {
        CoreResource.WOOD -> Material.OAK_LOG
        CoreResource.ORE -> Material.RAW_IRON
        CoreResource.STONE -> Material.COBBLESTONE
        CoreResource.HIDE -> Material.RABBIT_HIDE
        CoreResource.FIBER -> Material.WHEAT
        CoreResource.BOARD -> Material.OAK_PLANKS
        CoreResource.INGOT -> Material.IRON_INGOT
        CoreResource.STONE_BLOCK -> Material.STONE_BRICKS
        CoreResource.LEATHER -> Material.LEATHER
        CoreResource.CLOTH -> Material.WHITE_WOOL
        CoreResource.BOSS_SIGIL -> Material.ECHO_SHARD
        CoreResource.COMBAT_TOKEN -> Material.GOLD_NUGGET
        CoreResource.POTION -> Material.HONEY_BOTTLE
        CoreResource.GATHERING_TABLET -> Material.AMETHYST_SHARD
        CoreResource.WHETSTONE -> Material.FLINT
    }

    fun resource(material: CoreMaterial, count: Long): ItemStack = icon(resourceMaterial(material.resource), material.displayName,
        "倉庫：$count 個", "素材は採取・討伐時に自動で保存されます", color = colors[material.tier - 1])
        .withAmount(count.coerceIn(1, 64).toInt())

    fun weapon(tier: Int): ItemStack = icon(listOf(Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD)[tier - 1],
        "T$tier 開拓者の大剣", "攻撃力 ${(12 * CoreLoopCatalog.weaponDamage(tier)).roundToInt()}",
        "左クリック：前方を斬る", "右クリック：踏み込み斬り", "F：移動方向へ回避", color = colors[tier - 1])
        .withTag(actionTag, "weapon")

    fun map(data: CoreOwnedMap): ItemStack = icon(Material.FILLED_MAP, "T${data.tier} 未踏の地図",
        *listOf("右クリック：この地図で出発", "石板をつかんで重ねるとMODを付与", "付与MOD ${data.modifiers.size}/3")
            .plus(data.modifiers.map { modifierName(it) }).toTypedArray(), color = colors[data.tier - 1])
        .withTag(ownedMapTag, data.id.toString())

    fun modifierName(mod: CoreMapModifier): String = QuestMapGatheringModifier(
        mod.discipline?.let { id -> QuestGatheringDiscipline.entries.first { it.id == id } },
        QuestMapGatheringStat.entries.first { it.id == mod.stat }, mod.percent).displayName()

    fun customization(data: CoreOwnedMap): QuestMapCustomization = QuestMapCustomization(data.modifiers.map { mod ->
        QuestMapGatheringModifier(mod.discipline?.let { id -> QuestGatheringDiscipline.entries.first { it.id == id } },
            QuestMapGatheringStat.entries.first { it.id == mod.stat }, mod.percent)
    })

    fun nextModifier(data: CoreOwnedMap, roll: Long): CoreMapModifier? {
        val updated = QuestMapItems.applyTablet(QuestMapItemData(data.seed, customization(data)), roll) ?: return null
        val mod = updated.customization.modifiers.last()
        return CoreMapModifier(mod.discipline?.id, mod.stat.id, mod.percent)
    }

    fun mapId(item: ItemStack): UUID? = item.getTag(ownedMapTag)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    fun refresh(player: Player, account: CoreAccount, initial: Boolean = false) {
        if (initial) player.inventory.clear()
        player.inventory.setItemStack(0, weapon(account.weaponTier))
        val skillIcons = listOf(Material.FEATHER, Material.IRON_SWORD, Material.BLAZE_POWDER)
        val descriptions = listOf("前方へ踏み込み斬る / マナ15 / 4秒", "前方の広範囲を叩く / マナ25 / 7秒", "周囲へ3連撃 / マナ35 / 11秒")
        for (id in 0..2) player.inventory.setItemStack(id + 1, icon(skillIcons[id], CorePlayerCombat.SKILL_NAMES[id], descriptions[id],
            "右クリックで使用し、大剣に持ち替える").withTag(actionTag, "skill:$id"))
        player.inventory.setItemStack(4, icon(Material.HONEY_BOTTLE, "回復薬（倉庫 ${account.amount(CoreResource.POTION)}）",
            "右クリック：最大HPの45%を回復", "工房で布から調合 / 再使用10秒").withTag(actionTag, "potion"))
        player.inventory.setItemStack(5, icon(Material.COMPASS, "帰還の羅針盤", "右クリック：探索状況・帰還", "獲得素材は帰還前から保存されています").withTag(actionTag, "journal"))
        player.inventory.setItemStack(8, icon(Material.BOOK, "冒険の手帳", "右クリック：地図・工房・倉庫への案内", "港の施設からも同じ操作ができます").withTag(actionTag, "journal"))
        if (initial) {
            player.inventory.setItemStack(6, QuestGatheringDiscipline.WOODCUTTING.toolItem())
            QuestGatheringDiscipline.entries.forEachIndexed { i, discipline -> player.inventory.setItemStack(9 + i, discipline.toolItem()) }
        }
        player.inventory.setItemStack(14, icon(Material.AMETHYST_SHARD, "採取の石板（${account.amount(CoreResource.GATHERING_TABLET)}）",
            "つかんで地図に重ねるとMODを付与", "地図台でも同じ操作ができます", color = NamedTextColor.LIGHT_PURPLE)
            .withTag(actionTag, "tablet"))
        player.inventory.setItemStack(15, icon(Material.FLINT, "砥石（${account.amount(CoreResource.WHETSTONE)}）",
            "右クリック：攻撃力+20% / 3分", "加工石材とインゴットから作れます").withTag(actionTag, "whetstone"))
        val existingMapId = mapId(player.inventory.getItemStack(7))
        player.inventory.setItemStack(7, account.maps.firstOrNull { it.id == existingMapId }?.let(::map) ?: ItemStack.AIR)
        val tier = account.armorTier
        val armor = listOf(
            listOf(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS),
            listOf(Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS),
            listOf(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS),
            listOf(Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS),
        )[tier - 1]
        listOf(EquipmentSlot.HELMET, EquipmentSlot.CHESTPLATE, EquipmentSlot.LEGGINGS, EquipmentSlot.BOOTS).forEachIndexed { index, slot ->
            player.setEquipment(slot, icon(armor[index], "T$tier 開拓者の防具", "最大HP ${CoreLoopCatalog.armorHealth(tier).toInt()}",
                "被ダメージ軽減 ${(tier - 1) * 10}%", "工房で仕立てると自動で装備", color = colors[tier - 1]).withTag(actionTag, "armor"))
        }
    }
}
