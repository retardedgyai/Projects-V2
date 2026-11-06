package dev.projects.server.coreloop

import net.kyori.adventure.key.Key
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReturnDialogTest {
    @Test fun `帰還する goes home, 手帳 opens the book, other keys are not its own`() {
        MinecraftServer.init(Auth.Offline())
        val connection = object : PlayerConnection() {
            override fun sendPacket(packet: SendablePacket) = Unit
            override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
        }
        val player = Player(connection, GameProfile(UUID.randomUUID(), "ReturnTest")).also { connection.player = it }
        val calls = mutableListOf<String>()
        val dialog = ReturnDialog({ calls += "home" }, { calls += "book" })
        dialog.show(player, ReturnDialog.Place("散り桜の社", "桜牙獣", defeated = true))
        assertTrue(dialog.click(player, Key.key("projects_return", "home"), null))
        assertTrue(dialog.click(player, Key.key("projects_return", "book"), null))
        assertTrue(dialog.click(player, Key.key("projects_return", "close"), null))
        assertFalse(dialog.click(player, Key.key("projects_menu", "home"), null))
        assertEquals(listOf("home", "book"), calls)
    }
}
