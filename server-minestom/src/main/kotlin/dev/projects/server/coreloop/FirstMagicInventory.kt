package dev.projects.server.coreloop

import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag

/** The six anomalous materials are real inventory stacks while a player is online. */
internal object FirstMagicInventory {
    private val materialTag = Tag.String("projects_first_magic_material")

    fun kind(item: ItemStack): AnomalousMaterial? = item.getTag(materialTag)?.let { name ->
        AnomalousMaterial.entries.firstOrNull { it.name == name }
    }

    fun item(material: AnomalousMaterial, packed: Boolean, amount: Int = 1): ItemStack {
        val fallback = when (material) {
            AnomalousMaterial.MOONBELL -> Material.BLUE_ORCHID
            AnomalousMaterial.EMBER_MOSS -> Material.RED_MUSHROOM
            AnomalousMaterial.HOLLOW_CRYSTAL -> Material.AMETHYST_SHARD
            AnomalousMaterial.WARM_ORE -> Material.RAW_COPPER
            AnomalousMaterial.TIDEWING_FEATHER -> Material.FEATHER
            AnomalousMaterial.WITHERED_CORE -> Material.ECHO_SHARD
        }
        val art = when (material) {
            AnomalousMaterial.MOONBELL -> "moonbell"
            AnomalousMaterial.EMBER_MOSS -> "ember_moss"
            AnomalousMaterial.HOLLOW_CRYSTAL -> "hollow_crystal"
            AnomalousMaterial.WARM_ORE -> "warm_ore"
            AnomalousMaterial.TIDEWING_FEATHER -> "tidewing_feather"
            AnomalousMaterial.WITHERED_CORE -> "withered_core"
        }
        val stack = CoreLoopItems.icon(fallback, material.label,
            "コロニーの研究机で分析できる", "分析後、蒸留器に投入できる", color = NamedTextColor.AQUA)
            .withTag(materialTag, material.name).withAmount(amount)
        return if (packed) stack.withItemModel("projects:first_magic/icon_$art") else stack
    }

    fun count(player: Player, material: AnomalousMaterial): Int = (0 until 36).sumOf { slot ->
        player.inventory.getItemStack(slot).takeIf { kind(it) == material }?.amount() ?: 0
    } + (player.itemInOffHand.takeIf { kind(it) == material }?.amount() ?: 0) +
        (player.inventory.cursorItem.takeIf { kind(it) == material }?.amount() ?: 0)

    fun canAdd(player: Player, material: AnomalousMaterial): Boolean = (0 until 36).any { slot ->
        val stack = player.inventory.getItemStack(slot)
        stack.isAir || (kind(stack) == material && stack.amount() < 64)
    }

    fun add(player: Player, material: AnomalousMaterial, packed: Boolean): Boolean {
        for (slot in 0 until 36) {
            val existing = player.inventory.getItemStack(slot)
            if (kind(existing) == material && existing.amount() < 64) {
                player.inventory.setItemStack(slot, existing.withAmount(existing.amount() + 1))
                return true
            }
        }
        val slot = (16 until 36).firstOrNull { player.inventory.getItemStack(it).isAir }
            ?: (0 until 16).firstOrNull { player.inventory.getItemStack(it).isAir }
            ?: return false
        player.inventory.setItemStack(slot, item(material, packed))
        return true
    }

    fun remove(player: Player, material: AnomalousMaterial): Boolean {
        for (slot in 0 until 36) {
            val existing = player.inventory.getItemStack(slot)
            if (kind(existing) == material) {
                player.inventory.setItemStack(slot, if (existing.amount() == 1) ItemStack.AIR else existing.withAmount(existing.amount() - 1))
                return true
            }
        }
        val offhand = player.itemInOffHand
        if (kind(offhand) == material) {
            player.setItemInOffHand(if (offhand.amount() == 1) ItemStack.AIR else offhand.withAmount(offhand.amount() - 1))
            return true
        }
        val cursor = player.inventory.cursorItem
        if (kind(cursor) == material) {
            player.inventory.cursorItem = if (cursor.amount() == 1) ItemStack.AIR else cursor.withAmount(cursor.amount() - 1)
            return true
        }
        return false
    }

    /** V1 saves store the quantities; restore only at login, never during ordinary inventory refreshes. */
    fun restore(player: Player, state: FirstMagicState, packed: Boolean) {
        AnomalousMaterial.entries.forEach { material ->
            repeat(state.count(material)) {
                check(add(player, material, packed)) { "Not enough inventory space to restore ${material.name}" }
            }
        }
    }

    fun reskin(player: Player, packed: Boolean) {
        for (slot in 0 until 36) {
            val previous = player.inventory.getItemStack(slot)
            kind(previous)?.let { player.inventory.setItemStack(slot, item(it, packed, previous.amount())) }
        }
        val offhand = player.itemInOffHand
        kind(offhand)?.let { player.setItemInOffHand(item(it, packed, offhand.amount())) }
        val cursor = player.inventory.cursorItem
        kind(cursor)?.let { player.inventory.cursorItem = item(it, packed, cursor.amount()) }
    }
}
