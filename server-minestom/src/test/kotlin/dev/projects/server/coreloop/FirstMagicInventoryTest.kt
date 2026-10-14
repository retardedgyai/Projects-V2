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
import kotlin.test.assertTrue

class FirstMagicInventoryTest {
    @Test fun `samples are ledger stacks with the first-magic icon and survive an ordinary refresh`() {
        MinecraftServer.init(Auth.Offline())
        val connection = MemoryConnection()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "Researcher"))
        connection.player = player
        val account = CoreAccount(player.uuid, balances = mapOf(CoreMaterial(CoreResource.MOONBELL) to 3L, CoreMaterial(CoreResource.ARCANE_CATALYST) to 1L))
        CoreLoopItems.refresh(player, account, initial = true, packed = true)
        CoreLoopItems.refresh(player, account, packed = true)
        val stacks = (0 until 36).map(player.inventory::getItemStack)
        val sample = stacks.single { CoreLoopItems.resourceId(it) == CoreMaterial(CoreResource.MOONBELL) }
        assertEquals(3, sample.amount())
        assertEquals("projects:first_magic/icon_moonbell", sample.get(net.minestom.server.component.DataComponents.ITEM_MODEL))
        assertTrue(stacks.any { CoreLoopItems.resourceId(it) == CoreMaterial(CoreResource.ARCANE_CATALYST) })
    }

    @Test fun `colony furniture items are issued once`() {
        MinecraftServer.init(Auth.Offline())
        val connection = MemoryConnection()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "Researcher"))
        connection.player = player
        FirstMagicColonyItems.issueMissing(player, listOf(ColonyPlaceable.DESK), packed = true)
        assertEquals(1, (0 until 36).count {
            FirstMagicColonyItems.kind(player.inventory.getItemStack(it)) == ColonyPlaceable.DESK
        })
        FirstMagicColonyItems.issueMissing(player, listOf(ColonyPlaceable.DESK), packed = true)
        assertEquals(1, (0 until 36).count {
            FirstMagicColonyItems.kind(player.inventory.getItemStack(it)) == ColonyPlaceable.DESK
        }, "Reconnect and re-enter must not duplicate the physical desk item")
        FirstMagicColonyItems.reskin(player, packed = false)
        FirstMagicColonyItems.reskin(player, packed = true)
        assertEquals(1, (0 until 36).count {
            FirstMagicColonyItems.kind(player.inventory.getItemStack(it)) == ColonyPlaceable.DESK
        })
    }

    private class MemoryConnection : PlayerConnection() {
        override fun isOnline() = true
        override fun sendPacket(packet: SendablePacket) = Unit
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }
}
