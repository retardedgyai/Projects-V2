package dev.projects.server.coreloop

import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag

/** Physical one-of-each build items. Their ownership follows the saved private-island layout. */
internal object FirstMagicColonyItems {
    val tag: Tag<String> = Tag.String("projects_first_magic_placeable")
    private val kitSlots = listOf(5) + (16..35).toList()

    fun kind(item: ItemStack): ColonyPlaceable? = item.getTag(tag)?.let { name ->
        ColonyPlaceable.entries.firstOrNull { it.name == name }
    }

    fun item(kind: ColonyPlaceable, packed: Boolean): ItemStack {
        val fallback = when (kind) {
            ColonyPlaceable.DESK -> Material.LECTERN
            ColonyPlaceable.DISTILLER -> Material.BREWING_STAND
            ColonyPlaceable.SHELF -> Material.BOOKSHELF
            ColonyPlaceable.STAR_CHART -> Material.PAINTING
            else -> Material.GLASS_BOTTLE
        }
        val base = CoreLoopItems.icon(fallback, kind.label,
            "手に持ってブロックへ右クリック：設置",
            "しゃがみながら右クリック：回収",
            if (kind == ColonyPlaceable.STAR_CHART) "壁面に設置" else "コロニー内の好きな場所に設置",
            color = NamedTextColor.AQUA).withTag(tag, kind.name)
        return if (packed) base.withItemModel("projects:first_magic/${kind.model}") else base
    }

    fun issue(player: Player, colony: FirstMagicColony, packed: Boolean) {
        for (slot in 0 until 36) if (kind(player.inventory.getItemStack(slot)) != null)
            player.inventory.setItemStack(slot, ItemStack.AIR)
        if (kind(player.itemInOffHand) != null) player.setItemInOffHand(ItemStack.AIR)
        if (kind(player.inventory.cursorItem) != null) player.inventory.cursorItem = ItemStack.AIR
        colony.missingItems().zip(kitSlots).forEach { (kind, slot) ->
            player.inventory.setItemStack(slot, item(kind, packed))
        }
    }

    fun clear(player: Player) {
        for (slot in 0 until 36) if (kind(player.inventory.getItemStack(slot)) != null)
            player.inventory.setItemStack(slot, ItemStack.AIR)
        if (kind(player.itemInOffHand) != null) player.setItemInOffHand(ItemStack.AIR)
        if (kind(player.inventory.cursorItem) != null) player.inventory.cursorItem = ItemStack.AIR
    }
}
