package dev.projects.server.coreloop.ui

import net.kyori.adventure.text.Component
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.inventory.click.Click
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.packet.server.play.OpenWindowPacket
import net.minestom.server.network.packet.server.play.SetSlotPacket
import net.minestom.server.network.packet.server.play.WindowItemsPacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import kotlin.test.*

class CoreMenuInventoryTest {
    private class RecordingConnection : PlayerConnection() {
        val packets = mutableListOf<SendablePacket>()
        override fun sendPacket(packet: SendablePacket) {
            // Grouped viewer sends wrap OpenWindow in CachedPacket; record its actual payload.
            packets += SendablePacket.extractServerPacket(ConnectionState.PLAY, packet) ?: packet
        }
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }

    private fun viewer(id: UUID = UUID.randomUUID()): Pair<Player, RecordingConnection> {
        MinecraftServer.init(Auth.Offline())
        val connection = RecordingConnection()
        val player = Player(connection, GameProfile(id, "MenuViewer"))
        connection.player = player
        return player to connection
    }

    private fun screen(title: String = "開拓工房", items: Array<ItemStack> = Array(54) { ItemStack.AIR },
        actions: Map<Int, () -> Unit> = emptyMap(), redraw: () -> Unit = {}) =
        CoreMenuInventory.Screen(Component.text(title), items, actions, redraw)

    @Test fun `same-title render keeps window and sends one full content packet without reopen`() {
        val (player, connection) = viewer()
        val menus = CoreMenuInventory()
        val first = screen()
        menus.show(player, first)
        val inventory = assertNotNull(player.openInventory)
        connection.packets.clear()
        val nextItems = Array(54) { ItemStack.of(Material.PAPER) }
        val next = screen(items = nextItems)

        menus.show(player, next)

        assertSame(inventory, player.openInventory)
        assertFalse(menus.isCurrent(player, first))
        assertTrue(menus.isCurrent(player, next))
        assertEquals(0, connection.packets.filterIsInstance<OpenWindowPacket>().size)
        assertEquals(0, connection.packets.filterIsInstance<SetSlotPacket>().size)
        val update = connection.packets.filterIsInstance<WindowItemsPacket>().single()
        assertEquals(inventory.windowId.toInt(), update.windowId())
        assertEquals(nextItems.toList(), update.items())
    }

    @Test fun `title change uses one standard reopen and one content update on the same window`() {
        val (player, connection) = viewer()
        val menus = CoreMenuInventory()
        menus.show(player, screen())
        val inventory = assertNotNull(player.openInventory)
        connection.packets.clear()

        menus.show(player, screen("素材倉庫", Array(54) { ItemStack.of(Material.STONE) }))

        assertSame(inventory, player.openInventory)
        val opened = connection.packets.filterIsInstance<OpenWindowPacket>().single()
        assertEquals(inventory.windowId.toInt(), opened.windowId())
        assertEquals(Component.text("素材倉庫"), opened.title())
        assertEquals(1, connection.packets.filterIsInstance<WindowItemsPacket>().size)
        assertEquals(0, connection.packets.filterIsInstance<SetSlotPacket>().size)
    }

    @Test fun `only new generation actions run after in-place replacement`() {
        val (player) = viewer()
        val menus = CoreMenuInventory()
        var oldCalls = 0
        var newCalls = 0
        val old = screen(actions = mapOf(10 to { oldCalls++ }))
        val next = screen(actions = mapOf(10 to { newCalls++ }))
        menus.show(player, old)
        val inventory = assertNotNull(player.openInventory)
        menus.show(player, next)
        val click = InventoryPreClickEvent(inventory, player, Click.Left(10))

        assertTrue(menus.click(click))
        assertTrue(click.isCancelled)
        assertEquals(0, oldCalls)
        assertEquals(1, newCalls)
        assertFalse(menus.isCurrent(player, old))
        assertTrue(menus.isCurrent(player, next))
    }

    @Test fun `screen defensively snapshots caller items and actions`() {
        val (player) = viewer()
        val menus = CoreMenuInventory()
        var calls = 0
        val items = Array(54) { ItemStack.AIR }
        items[10] = ItemStack.of(Material.PAPER)
        val actions = mutableMapOf<Int, () -> Unit>(10 to { calls++ })
        val display = screen(items = items, actions = actions)
        items[10] = ItemStack.of(Material.DIAMOND)
        actions.clear()
        menus.show(player, display)
        val inventory = assertNotNull(player.openInventory)

        menus.click(InventoryPreClickEvent(inventory, player, Click.Left(10)))

        assertEquals(ItemStack.of(Material.PAPER), inventory.getItemStack(10))
        assertEquals(1, calls)
    }

    @Test fun `left and right work across all label hitboxes but decorative slots do nothing`() {
        val (player) = viewer()
        val menus = CoreMenuInventory()
        var calls = 0
        val shared: () -> Unit = { calls++ }
        menus.show(player, screen(actions = listOf(10, 11, 12).associateWith { shared }))
        val inventory = assertNotNull(player.openInventory)
        for (slot in 10..12) for (click in listOf(Click.Left(slot), Click.Right(slot))) {
            val event = InventoryPreClickEvent(inventory, player, click)
            assertTrue(menus.click(event))
            assertTrue(event.isCancelled)
        }
        val decorative = InventoryPreClickEvent(inventory, player, Click.Left(13))
        assertTrue(menus.click(decorative))
        assertTrue(decorative.isCancelled)
        assertEquals(6, calls)
    }

    @Test fun `transfer clicks and bottom inventory are cancelled without dispatch or item movement`() {
        val (player) = viewer()
        val menus = CoreMenuInventory()
        var calls = 0
        menus.show(player, screen(actions = mapOf(10 to { calls++ })))
        val inventory = assertNotNull(player.openInventory)
        val cursor = ItemStack.of(Material.DIAMOND)
        player.inventory.cursorItem = cursor
        val unsupported = listOf<Click>(Click.LeftShift(10), Click.RightShift(10), Click.HotbarSwap(0, 10),
            Click.OffhandSwap(10), Click.Middle(10), Click.Double(10), Click.DropSlot(10, true),
            Click.LeftDrag(listOf(10, 11)), Click.RightDrag(listOf(10, 11)), Click.LeftDropCursor(),
            Click.Left(-999), Click.Left(54))
        for (click in unsupported) {
            val event = InventoryPreClickEvent(inventory, player, click)
            assertTrue(menus.click(event), "Unhandled $click")
            assertTrue(event.isCancelled, "Uncancelled $click")
        }
        val bottom = InventoryPreClickEvent(player.inventory, player, Click.Left(10))
        assertTrue(menus.click(bottom))
        assertTrue(bottom.isCancelled)
        assertEquals(0, calls)
        assertEquals(cursor, player.inventory.cursorItem)
    }

    @Test fun `closing invalidates callbacks and theme refresh never reopens a closed menu`() {
        val (player, connection) = viewer()
        val menus = CoreMenuInventory()
        var redraws = 0
        val display = screen(redraw = { redraws++ })
        menus.show(player, display)
        val oldInventory = assertNotNull(player.openInventory)
        player.closeInventory()
        connection.packets.clear()

        menus.refresh(player)
        val click = InventoryPreClickEvent(oldInventory, player, Click.Left(10))

        assertFalse(menus.isCurrent(player, display))
        assertFalse(menus.click(click))
        assertFalse(click.isCancelled)
        assertNull(player.openInventory)
        assertEquals(0, redraws)
        assertTrue(connection.packets.isEmpty())
    }

    @Test fun `theme refresh uses current screen redraw without returning to another route`() {
        val (player) = viewer()
        val menus = CoreMenuInventory()
        var oldRedraws = 0
        var storageRedraws = 0
        menus.show(player, screen(redraw = { oldRedraws++ }))
        menus.show(player, screen("倉庫 T3 2頁", redraw = { storageRedraws++ }))

        menus.refresh(player)

        assertEquals(0, oldRedraws)
        assertEquals(1, storageRedraws)
    }

    @Test fun `another inventory makes the old callback stale and the next show starts a new window`() {
        val (player) = viewer()
        val menus = CoreMenuInventory()
        val display = screen()
        menus.show(player, display)
        val first = assertNotNull(player.openInventory)
        val other = Inventory(InventoryType.CHEST_1_ROW, "別の画面")
        player.openInventory(other)
        assertFalse(menus.isCurrent(player, display))

        menus.show(player, screen())

        assertNotSame(first, player.openInventory)
        assertNotSame(other, player.openInventory)
    }

    @Test fun `reconnected player identity and forget invalidate former callbacks`() {
        val (oldPlayer) = viewer()
        val menus = CoreMenuInventory()
        val old = screen()
        menus.show(oldPlayer, old)
        val (newPlayer) = viewer(oldPlayer.uuid)
        val next = screen()
        menus.show(newPlayer, next)
        assertFalse(menus.isCurrent(oldPlayer, old))
        assertTrue(menus.isCurrent(newPlayer, next))

        menus.forget(newPlayer.uuid)

        assertFalse(menus.isCurrent(newPlayer, next))
    }

    @Test fun `screen rejects malformed array size and actions outside vanilla top hitboxes`() {
        assertFailsWith<IllegalArgumentException> { screen(items = Array(53) { ItemStack.AIR }) }
        assertFailsWith<IllegalArgumentException> { screen(items = Array(55) { ItemStack.AIR }) }
        assertFailsWith<IllegalArgumentException> { screen(actions = mapOf(-1 to {})) }
        assertFailsWith<IllegalArgumentException> { screen(actions = mapOf(54 to {})) }
    }
}
