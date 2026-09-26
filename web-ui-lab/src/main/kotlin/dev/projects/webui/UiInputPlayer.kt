package dev.projects.webui

import net.minestom.server.entity.Player
import net.minestom.server.network.packet.client.ClientPacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection

/** Dispatch UI probe replies immediately; leave ordinary gameplay input in Minestom's queue. */
class UiInputPlayer(
    connection: PlayerConnection, profile: GameProfile,
    private val uiInput: (Player, ClientPacket) -> Boolean,
) : Player(connection,profile) {
    override fun addPacketToQueue(packet: ClientPacket) {
        if(!uiInput(this,packet)) super.addPacketToQueue(packet)
    }
}
