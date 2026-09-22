package dev.projects.modellab

import net.minestom.server.MinecraftServer
import net.minestom.server.Auth
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.inventory.InventoryCloseEvent
import net.minestom.server.inventory.click.Click
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.*

class LabMenuTest {
    @Test fun transferClicksAndStaleScreensCannotTriggerActions() {
        MinecraftServer.init(Auth.Offline())
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.viewDistance(2)
        for (x in -3..3) for (z in -3..3) instance.loadChunk(x, z).get(10, TimeUnit.SECONDS)
        val connection = object : PlayerConnection() {
            override fun sendPacket(packet: SendablePacket) {}
            override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
        }
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "ModelMenuTest"))
        connection.player = player
        player.setInstance(instance, Pos(8.0, 1.0, 8.0)).get(10, TimeUnit.SECONDS)
        try {
            val events = MinecraftServer.getGlobalEventHandler()
            val menu = LabMenu(events)
            var calls = 0
            menu.show(player, "test", listOf(LabMenu.Button("play") { calls++ }))
            val first = player.openInventory!!
            val normal = InventoryPreClickEvent(first, player, Click.Left(0))
            events.call(normal)
            assertTrue(normal.isCancelled)
            assertEquals(1, calls)
            val shift = InventoryPreClickEvent(first, player, Click.LeftShift(0))
            events.call(shift)
            assertTrue(shift.isCancelled)
            val bottom = InventoryPreClickEvent(player.inventory, player, Click.Left(0))
            events.call(bottom)
            assertTrue(bottom.isCancelled)
            assertEquals(1, calls)
            menu.show(player, "replacement", listOf(LabMenu.Button("new") { calls += 10 }))
            events.call(InventoryCloseEvent(first, player, true))
            events.call(InventoryPreClickEvent(first, player, Click.Left(0)))
            assertEquals(1, calls)
            events.call(InventoryPreClickEvent(player.openInventory!!, player, Click.Left(0)))
            assertEquals(11, calls)
            menu.panel(player,"panel",mapOf(10 to LabMenu.Button("disabled",enabled=false) { calls++ }))
            assertEquals(27,player.openInventory!!.size)
            events.call(InventoryPreClickEvent(player.openInventory!!,player,Click.Left(10)))
            events.call(InventoryPreClickEvent(player.openInventory!!,player,Click.Left(0)))
            assertEquals(11,calls)
            var back=0
            menu.show(player,"pages",List(46) { i -> LabMenu.Button("$i") { calls=i } },back={back++})
            events.call(InventoryPreClickEvent(player.openInventory!!,player,Click.Left(53)))
            events.call(InventoryPreClickEvent(player.openInventory!!,player,Click.Left(0)))
            assertEquals(45,calls)
            events.call(InventoryPreClickEvent(player.openInventory!!,player,Click.Left(47)))
            assertEquals(1,back)
        } finally {
            player.remove()
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
            MinecraftServer.stopCleanly()
        }
    }
    @Test fun compoundAnimationNamesAreReadableJapanese() {
        assertEquals("振り払い・予備動作・解放形態",LabTestUi.animationLabel("sweep_windup_unbound"))
        assertEquals("開花待機",LabTestUi.animationLabel("idle_bloom"))
        assertEquals("斬り上げ・溜め",LabTestUi.animationLabel("uppercut_hold"))
    }
}
