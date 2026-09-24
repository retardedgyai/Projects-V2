package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CoreInventoryItemsTest {
    @Test fun `harvested and combat resources appear as tagged inventory items after account update`() {
        MinecraftServer.init(Auth.Offline())
        val connection = MemoryConnection()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "Gatherer"))
        connection.player = player
        val wood = CoreMaterial(CoreResource.WOOD, 1)
        val dust = CoreMaterial(CoreResource.AFFIX_DUST)
        val maps = listOf(CoreOwnedMap(UUID.randomUUID(), 11L, 1), CoreOwnedMap(UUID.randomUUID(), 22L, 1))
        val account = CoreAccount(player.uuid, balances = mapOf(wood to 7L, dust to 90L), maps = maps,
            fragments = mapOf(CoreActivityKind.RIFT to 2L))
        CoreLoopItems.refresh(player, account, initial = true)
        val woodItem = (0 until 36).map(player.inventory::getItemStack).firstOrNull { CoreLoopItems.resourceId(it) == wood }
        val dustItem = (0 until 36).map(player.inventory::getItemStack).firstOrNull { CoreLoopItems.resourceId(it) == dust }
        assertEquals(7, assertNotNull(woodItem).amount())
        assertEquals(7L, woodItem.getTag(CoreLoopItems.resourceQuantityTag))
        assertEquals(90L, assertNotNull(dustItem).getTag(CoreLoopItems.resourceQuantityTag))
        assertEquals(maps.map { it.id }.toSet(), (0 until 36).map(player.inventory::getItemStack)
            .mapNotNull(CoreLoopItems::mapId).toSet())
        assertEquals(2L, (0 until 36).map(player.inventory::getItemStack)
            .first { CoreLoopItems.fragmentId(it) == CoreActivityKind.RIFT }.getTag(CoreLoopItems.resourceQuantityTag))
        CoreLoopItems.refresh(player, account.copy(balances = mapOf(wood to 6L, dust to 90L)))
        assertEquals(6, (0 until 36).map(player.inventory::getItemStack)
            .first { CoreLoopItems.resourceId(it) == wood }.amount())
    }

    private class MemoryConnection : PlayerConnection() {
        override fun isOnline() = true
        override fun sendPacket(packet: SendablePacket) = Unit
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }
}
