package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import kotlin.test.*

class CoreItemProjectionTest {
    @Test fun `currency refresh clears moved copies and spending last balance removes all projections`() {
        MinecraftServer.init(Auth.Offline())
        val connection = object : PlayerConnection() {
            override fun sendPacket(packet: SendablePacket) = Unit
            override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
        }
        val id = UUID.randomUUID()
        val player = Player(connection, GameProfile(id, "Projection"))
        connection.player = player
        val account = CoreAccount(id, currencies = mapOf(CoreCraftingCurrency.CHAOS to 2L))
        val display = CoreLoopItems.currency(CoreCraftingCurrency.CHAOS, 2, false)
        player.inventory.setItemStack(6, display)
        player.inventory.cursorItem = display
        player.setItemInOffHand(display)
        CoreLoopItems.refresh(player, account)
        assertTrue(player.itemInOffHand.isAir)
        assertTrue(player.inventory.cursorItem.isAir)
        assertEquals(1, (0 until 36).count { CoreLoopItems.currencyId(player.inventory.getItemStack(it)) != null })
        assertEquals(2, player.inventory.getItemStack(16).amount())
        CoreLoopItems.refresh(player, account.copy(currencies = emptyMap()))
        assertEquals(0, (0 until 36).count { CoreLoopItems.currencyId(player.inventory.getItemStack(it)) != null })
    }
}
