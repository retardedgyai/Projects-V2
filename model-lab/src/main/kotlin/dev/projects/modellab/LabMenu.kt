package dev.projects.modellab

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
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
    data class Button(val label: String, val icon: Material = Material.AMETHYST_SHARD,
                      val description: List<String> = emptyList(), val enabled: Boolean = true,
                      val action: () -> Unit)
    private data class Session(val inventory: Inventory, val buttons: Map<Int, Button>)
    private val sessions = ConcurrentHashMap<UUID, Session>()
    init {
        events.addListener(InventoryPreClickEvent::class.java) { event ->
            val session = sessions[event.player.uuid]
            if (session != null && event.player.openInventory === session.inventory) {
                // Cancel all transfer forms, including clicks in the player's bottom inventory.
                event.isCancelled = true
                if (event.inventory === session.inventory && (event.click is Click.Left || event.click is Click.Right)) {
                    runCatching { session.buttons[event.slot]?.takeIf { it.enabled }?.action?.invoke() }.onFailure {
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
    fun show(player: Player, title: String, entries: List<Button>, page: Int = 0,
             back: (() -> Unit)? = null, extra: Button? = null) {
        val capacity = if (entries.size <= 18) 18 else 45
        val lastPage = ((entries.size - 1).coerceAtLeast(0) / capacity)
        val currentPage = page.coerceIn(0, lastPage)
        val buttons = entries.drop(currentPage * capacity).take(capacity).mapIndexed { index, button -> index to button }.toMap().toMutableMap()
        if (currentPage > 0) buttons[capacity] = Button("前のページ", Material.ARROW) { show(player, title, entries, currentPage - 1, back, extra) }
        if (back != null) buttons[capacity+2] = Button("戻る", Material.OAK_DOOR, action=back)
        buttons[capacity+4] = Button("閉じる", Material.BARRIER) { player.closeInventory() }
        if (extra != null) buttons[capacity+6] = extra
        if (currentPage < lastPage) buttons[capacity+8] = Button("次のページ", Material.ARROW) { show(player, title, entries, currentPage + 1, back, extra) }
        val suffix = if (lastPage > 0) " ${currentPage + 1}/${lastPage + 1}" else ""
        open(player, title+suffix, buttons, if (capacity == 18) InventoryType.CHEST_3_ROW else InventoryType.CHEST_6_ROW)
    }
    fun panel(player: Player, title: String, buttons: Map<Int, Button>) {
        require(buttons.keys.all { it in 0..26 })
        open(player, title, buttons, InventoryType.CHEST_3_ROW)
    }
    private fun open(player: Player, title: String, buttons: Map<Int, Button>, type: InventoryType) {
        val inventory = Inventory(type, Component.text(title))
        val filler = ItemStack.builder(Material.GRAY_STAINED_GLASS_PANE).customName(Component.empty()).build()
        for (slot in 0 until inventory.size) inventory.setItemStack(slot, filler)
        buttons.forEach { (slot, button) -> inventory.setItemStack(slot,
            ItemStack.builder(if (button.enabled) button.icon else Material.GRAY_DYE)
                .customName(Component.text(button.label, if (button.enabled) NamedTextColor.GOLD else NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC,false))
                .lore(button.description.map { Component.text(it, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false) }).build()) }
        // Publish the new session before openInventory triggers events for an old window.
        sessions[player.uuid] = Session(inventory, buttons.toMap())
        player.openInventory(inventory)
    }
}
