package dev.projects.modellab

import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.inventory.InventoryCloseEvent
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.inventory.click.Click
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Small port of Scorpius's declarative, session-owned GUI. Does not replace ProjectS menus. */
class LabMenu(events: GlobalEventHandler) {
    data class Button(val label: String, val action: () -> Unit)
    private data class Session(val inventory: Inventory, val buttons: Map<Int, Button>)
    private val sessions = ConcurrentHashMap<UUID, Session>()
    init {
        events.addListener(InventoryPreClickEvent::class.java) { event ->
            val session = sessions[event.player.uuid]
            if (session != null && event.player.openInventory === session.inventory) {
                // Cancel all transfer forms, including clicks in the player's bottom inventory.
                event.isCancelled = true
                if (event.inventory === session.inventory && (event.click is Click.Left || event.click is Click.Right)) {
                    runCatching { session.buttons[event.slot]?.action?.invoke() }.onFailure {
                        event.player.sendMessage(Component.text("実行できません：${it.message}"))
                    }
                }
            }
        }
        events.addListener(InventoryCloseEvent::class.java) { event ->
            val session = sessions[event.player.uuid]
            if (session?.inventory === event.inventory) sessions.remove(event.player.uuid, session)
        }
        events.addListener(PlayerDisconnectEvent::class.java) { sessions.remove(it.player.uuid) }
    }
    fun show(player: Player, title: String, entries: List<Button>, page: Int = 0) {
        val lastPage = ((entries.size - 1).coerceAtLeast(0) / 45)
        val currentPage = page.coerceIn(0, lastPage)
        val inventory = Inventory(InventoryType.CHEST_6_ROW, Component.text("$title ${currentPage + 1}/${lastPage + 1}"))
        val buttons = entries.drop(currentPage * 45).take(45).mapIndexed { index, button -> index to button }.toMap().toMutableMap()
        if (currentPage > 0) buttons[45] = Button("前のページ") { show(player, title, entries, currentPage - 1) }
        buttons[49] = Button("閉じる") { player.closeInventory() }
        if (currentPage < lastPage) buttons[53] = Button("次のページ") { show(player, title, entries, currentPage + 1) }
        buttons.forEach { (slot, button) -> inventory.setItemStack(slot,
            ItemStack.builder(if (slot < 45) Material.AMETHYST_SHARD else Material.PAPER)
                .customName(Component.text(button.label)).build()) }
        // Publish the new session before openInventory triggers events for an old window.
        sessions[player.uuid] = Session(inventory, buttons.toMap())
        player.openInventory(inventory)
    }
}
