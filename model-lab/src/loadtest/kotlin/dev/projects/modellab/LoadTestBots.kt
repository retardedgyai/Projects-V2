package dev.projects.modellab

import org.geysermc.mcprotocollib.auth.GameProfile
import org.geysermc.mcprotocollib.network.Session
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter
import org.geysermc.mcprotocollib.network.packet.Packet
import org.geysermc.mcprotocollib.network.session.ClientNetworkSession
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol
import org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundResourcePackPushPacket
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundResourcePackPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Ported from Scorpius. Network/replication load only, NOT a resource-pack rendering test. */
fun main(args: Array<String>) {
    val count = args.getOrNull(0)?.toIntOrNull() ?: 0
    val duration = args.getOrNull(1)?.toIntOrNull() ?: 60
    require(count in 1..32) { "Explicitly set -PmodelLabBots=1..32; disabled by default" }
    require(duration in 5..300)
    val scheduler = Executors.newScheduledThreadPool(4)
    val connections = CopyOnWriteArrayList<ClientNetworkSession>()
    val joined = AtomicInteger()
    val disconnected = AtomicInteger()
    val errors = AtomicInteger()
    try {
        repeat(count) { index ->
            scheduler.schedule({
                try {
                    val name = "ModelBot_%03d".format(index)
                    val profile = GameProfile(UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray()), name)
                    val session = ClientNetworkSession(InetSocketAddress("127.0.0.1", 25566),
                        MinecraftProtocol(profile, null), scheduler, null, null)
                    connections.add(session)
                    session.addListener(object : SessionAdapter() {
                        override fun packetReceived(session: Session, packet: Packet) {
                            when (packet) {
                                is ClientboundLoginPacket -> {
                                    joined.incrementAndGet()
                                    scheduler.schedule({ session.send(ServerboundChatCommandPacket("model vesper")) }, 3, TimeUnit.SECONDS)
                                }
                                is ClientboundPlayerPositionPacket -> {
                                    session.send(ServerboundAcceptTeleportationPacket(packet.id))
                                    val pos = packet.position
                                    session.send(ServerboundMovePlayerPosPacket(true, false, pos.x, pos.y, pos.z))
                                }
                                is ClientboundResourcePackPushPacket -> {
                                    // Synthetic protocol acknowledgement: no download and no texture validation.
                                    session.send(ServerboundResourcePackPacket(packet.id, ResourcePackStatus.ACCEPTED))
                                    session.send(ServerboundResourcePackPacket(packet.id, ResourcePackStatus.SUCCESSFULLY_LOADED))
                                }
                            }
                        }
                        override fun disconnected(event: DisconnectedEvent) { disconnected.incrementAndGet() }
                    })
                    session.connect()
                } catch (error: Exception) {
                    errors.incrementAndGet()
                    System.err.println("Model bot connection failed: ${error.message}")
                }
            }, index * 500L, TimeUnit.MILLISECONDS)
        }
        println("MODEL LOAD: localhost:25566 only; $count bots, ${duration}s; NO visual validation")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(duration.toLong())
        while (System.nanoTime() < deadline) {
            Thread.sleep(1000)
            println("joined=${joined.get()}/$count disconnected=${disconnected.get()} errors=${errors.get()}")
        }
    } finally {
        connections.forEach { runCatching { it.disconnect("model load test complete") } }
        scheduler.shutdownNow()
    }
    check(joined.get() == count && errors.get() == 0) { "Some bots did not connect" }
}
