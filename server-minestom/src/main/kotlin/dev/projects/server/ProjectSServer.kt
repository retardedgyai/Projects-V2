package dev.projects.server

import dev.projects.protocol.PROJECTS_CHANNEL
import dev.projects.protocol.ProtocolCodec
import dev.projects.protocol.ProtocolHello
import dev.projects.protocol.ProtocolHelloAck
import dev.projects.protocol.ProtocolVersion
import net.kyori.adventure.text.Component
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerPluginMessageEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.common.PluginMessagePacket
import java.nio.charset.StandardCharsets

private const val SERVER_ADDRESS = "127.0.0.1"
private const val SERVER_PORT = 25565

fun main() {
    val server = MinecraftServer.init(Auth.Offline())
    val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
    instance.setGenerator { unit -> unit.modifier().fillHeight(0, 40, Block.GRASS_BLOCK) }

    val events = MinecraftServer.getGlobalEventHandler()
    events.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
        event.spawningInstance = instance
        event.player.respawnPoint = Pos(0.0, 41.0, 0.0)
    }
    events.addListener(PlayerSpawnEvent::class.java) { event ->
        if (event.isFirstSpawn) {
            event.player.sendPacket(
                PluginMessagePacket("minecraft:register", PROJECTS_CHANNEL.toByteArray(StandardCharsets.UTF_8)),
            )
            event.player.sendPacket(
                PluginMessagePacket(
                    PROJECTS_CHANNEL,
                    ProtocolCodec.encode(ProtocolHello(ProtocolVersion.CURRENT)),
                ),
            )
        }
    }
    events.addListener(PlayerPluginMessageEvent::class.java) { event ->
        if (event.identifier != PROJECTS_CHANNEL) return@addListener

        try {
            val message = ProtocolCodec.decode(event.message)
            require(message is ProtocolHelloAck) { "Expected ProjectS protocol hello acknowledgement" }
            ProtocolVersion.requireCompatible(message.version)
            event.player.sendMessage(Component.text("ProjectS protocol ${message.version} handshake complete"))
            println("ProjectS handshake complete for ${event.player.username}")
        } catch (error: IllegalArgumentException) {
            event.player.kick(Component.text(error.message ?: "Invalid ProjectS protocol handshake"))
        }
    }

    server.start(SERVER_ADDRESS, SERVER_PORT)
    println("ProjectS Minestom server listening on $SERVER_ADDRESS:$SERVER_PORT")
}
