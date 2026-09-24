package dev.projects.server.coreloop

import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.component.DataComponents
import net.minestom.server.instance.block.predicate.BlockPredicate
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.BlockPredicates
import net.minestom.server.tag.Tag

/** One-of-each build items. Placement consumes a stack and recovery puts it back in inventory. */
internal object FirstMagicColonyItems {
    val tag: Tag<String> = Tag.String("projects_first_magic_placeable")

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
            .with(DataComponents.CAN_PLACE_ON, BlockPredicates(BlockPredicate.ALL))
        return if (packed) base.withItemModel("projects:first_magic/${kind.model}") else base
    }

    fun issue(player: Player, colony: FirstMagicColony, packed: Boolean) {
        issueMissing(player, colony.missingItems(), packed)
    }

    fun issueMissing(player: Player, missingItems: List<ColonyPlaceable>, packed: Boolean) {
        missingItems.forEach { missing ->
            if ((0 until 36).any { kind(player.inventory.getItemStack(it)) == missing } ||
                kind(player.itemInOffHand) == missing || kind(player.inventory.cursorItem) == missing) return@forEach
            if (!give(player, missing, packed))
                player.sendMessage(CoreLoopItems.text("インベントリに空きがないため${missing.label}を受け取れません", NamedTextColor.YELLOW))
        }
    }

    fun canReceive(player: Player): Boolean = (0 until 36).any { player.inventory.getItemStack(it).isAir }

    fun give(player: Player, placeable: ColonyPlaceable, packed: Boolean): Boolean {
        val slot = (16 until 36).firstOrNull { player.inventory.getItemStack(it).isAir }
            ?: (0 until 16).firstOrNull { player.inventory.getItemStack(it).isAir }
            ?: return false
        player.inventory.setItemStack(slot, item(placeable, packed))
        return true
    }

    fun reskin(player: Player, packed: Boolean) {
        for (slot in 0 until 36) {
            val previous = player.inventory.getItemStack(slot)
            kind(previous)?.let { player.inventory.setItemStack(slot, item(it, packed).withAmount(previous.amount())) }
        }
        val offhand = player.itemInOffHand
        kind(offhand)?.let { player.setItemInOffHand(item(it, packed).withAmount(offhand.amount())) }
        val cursor = player.inventory.cursorItem
        kind(cursor)?.let { player.inventory.cursorItem = item(it, packed).withAmount(cursor.amount()) }
    }

    fun consumeMainHand(player: Player, placeable: ColonyPlaceable): Boolean {
        val held = player.itemInMainHand
        if (kind(held) != placeable) return false
        player.itemInMainHand = if (held.amount() == 1) ItemStack.AIR else held.withAmount(held.amount() - 1)
        return true
    }
}
