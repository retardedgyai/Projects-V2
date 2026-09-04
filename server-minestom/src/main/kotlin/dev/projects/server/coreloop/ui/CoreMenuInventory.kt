package dev.projects.server.coreloop.ui

import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.inventory.click.Click
import net.minestom.server.item.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Owned six-row menu lifecycle. Invoke rendering and clicks on the viewer's player thread. */
internal class CoreMenuInventory {
    /** Identity is the render generation, including when the underlying window is reused. */
    class Screen(
        val title: Component,
        items: Array<ItemStack>,
        actions: Map<Int, () -> Unit>,
        val redraw: () -> Unit,
    ) {
        internal val items = items.toList()
        internal val actions = actions.toMap()

        init {
            require(items.size == SLOT_COUNT) { "A core menu needs exactly $SLOT_COUNT items" }
            require(actions.keys.all { it in 0 until SLOT_COUNT }) { "Menu actions must use top-inventory slots" }
        }
    }

    private class OpenScreen(val player: Player, val inventory: Inventory, val screen: Screen)
    private val screens = ConcurrentHashMap<UUID, OpenScreen>()

    fun show(player: Player, screen: Screen) {
        val previous = screens[player.uuid]
        val reusable = previous?.takeIf { it.player === player && player.openInventory === it.inventory }
        val inventory = reusable?.inventory ?: Inventory(InventoryType.CHEST_6_ROW, screen.title)
        // Install the new authority before any item-change event or packet can observe the render.
        screens[player.uuid] = OpenScreen(player, inventory, screen)
        screen.items.forEachIndexed { slot, item -> inventory.setItemStack(slot, item, false) }
        when {
            reusable == null -> player.openInventory(inventory)
            inventory.title != screen.title -> inventory.title = screen.title
            else -> inventory.update(player)
        }
        // setTitle itself sends OpenWindow + WindowItems. Do not send a second content update.
    }

    fun isCurrent(player: Player, screen: Screen): Boolean {
        val current = screens[player.uuid] ?: return false
        return current.player === player && current.screen === screen && player.openInventory === current.inventory
    }

    /** Bottom inventory and all transfer-capable click forms are cancelled, never dispatched. */
    fun click(event: InventoryPreClickEvent): Boolean {
        val current = screens[event.player.uuid] ?: return false
        if (current.player !== event.player || event.player.openInventory !== current.inventory) return false
        event.isCancelled = true
        if (event.inventory !== current.inventory || event.slot !in 0 until SLOT_COUNT) return true
        if (event.click is Click.Left || event.click is Click.Right) current.screen.actions[event.slot]?.invoke()
        return true
    }

    fun refresh(player: Player) {
        val current = screens[player.uuid] ?: return
        if (isCurrent(player, current.screen)) current.screen.redraw()
    }

    fun forget(playerId: UUID) { screens.remove(playerId) }

    companion object { private const val SLOT_COUNT = 54 }
}
