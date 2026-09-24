package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import net.minestom.server.entity.Player
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirstMagicInventoryTest {
    @Test fun `collected material is a stack that survives an ordinary inventory refresh and is consumed`() {
        MinecraftServer.init(Auth.Offline())
        val connection = MemoryConnection()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "Researcher"))
        connection.player = player
        val material = AnomalousMaterial.MOONBELL
        assertTrue(FirstMagicInventory.add(player, material, packed = true))
        assertEquals(1, FirstMagicInventory.count(player, material))
        assertTrue((0 until 36).any { FirstMagicInventory.kind(player.inventory.getItemStack(it)) == material })
        CoreLoopItems.refresh(player, CoreAccount(player.uuid), packed = true)
        assertEquals(1, FirstMagicInventory.count(player, material))
        val origin = (0 until 36).first { FirstMagicInventory.kind(player.inventory.getItemStack(it)) == material }
        player.inventory.setItemStack(5, player.inventory.getItemStack(origin))
        player.inventory.setItemStack(origin, net.minestom.server.item.ItemStack.AIR)
        CoreLoopItems.refresh(player, CoreAccount(player.uuid), packed = true)
        assertEquals(1, FirstMagicInventory.count(player, material), "Combat HUD refresh must preserve a moved real item")
        assertTrue(FirstMagicInventory.remove(player, material))
        assertEquals(0, FirstMagicInventory.count(player, material))
        assertFalse(FirstMagicInventory.remove(player, material))
    }

    @Test fun `saved materials restore as real inventory stacks`() {
        MinecraftServer.init(Auth.Offline())
        val connection = MemoryConnection()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "Researcher"))
        connection.player = player
        val state = FirstMagicState(materialCounts = mapOf(AnomalousMaterial.EMBER_MOSS to 3))
        FirstMagicInventory.restore(player, state, packed = true)
        assertEquals(3, FirstMagicInventory.count(player, AnomalousMaterial.EMBER_MOSS))
        assertEquals(0, FirstMagicInventory.count(player, AnomalousMaterial.MOONBELL))
    }

    private class MemoryConnection : PlayerConnection() {
        override fun isOnline() = true
        override fun sendPacket(packet: SendablePacket) = Unit
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }
}
