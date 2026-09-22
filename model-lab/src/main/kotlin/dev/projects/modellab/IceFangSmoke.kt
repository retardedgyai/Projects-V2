package dev.projects.modellab

import net.minestom.server.MinecraftServer
import net.minestom.server.Auth
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.Player
import net.minestom.server.entity.EntityType
import net.minestom.server.instance.block.Block
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Real Minestom + WSEE + training runtime, but no listener socket or Minecraft launch. */
fun main(args: Array<String>) {
    MinecraftServer.init(Auth.Offline())
    val process = MinecraftServer.process()
    try {
        val bundle = ModelBundle(Path.of(args.single()).resolve("bundle"))
        bundle.load()
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.viewDistance(2)
        instance.setGenerator { it.modifier().fillHeight(0, 1, Block.STONE) }
        for (x in -3..3) for (z in -3..3) instance.loadChunk(x, z).get(10, TimeUnit.SECONDS)
        val connection = object : PlayerConnection() {
            override fun sendPacket(packet: SendablePacket) {}
            override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
        }
        connection.setClientState(ConnectionState.PLAY); connection.setServerState(ConnectionState.PLAY)
        val player = Player(connection, GameProfile(UUID.randomUUID(), "IceFangSmoke"))
        connection.player = player
        player.setInstance(instance, Pos(.5, 1.0, .5)).get(10, TimeUnit.SECONDS)
        process.dispatcher().start()
        var ticks = 0L
        IceFangTraining(bundle, instance).use { training ->
            fun step(count: Int) { repeat(count) {
                training.tick(); process.ticker().tick(++ticks * 50_000_000L)
            } }
            fun targets() = instance.entities.filterIsInstance<EntityCreature>().filter { it.entityType == EntityType.HUSK }
            training.equip(player); step(3)
            check(targets().size == 3) { "Missing training targets" }
            val baseline = instance.entities.size
            repeat(10) { training.requestCast(player) }
            step(45)
            check(targets().all { it.health == 440f }) { "Expected one 88 hit per target: ${targets().map { it.health }}" }
            check(instance.entities.size == baseline) { "Completed cast leaked model entities" }
            training.requestCast(player); step(5)
            check(targets().all { it.health == 440f }) { "Cooldown bypass" }
            // Reset reproduces normal /mage, closes previous targets and replenishes training state.
            training.equip(player); step(3)
            check(targets().size == 3)
            instance.setBlock(0, 1, 4, Block.STONE)
            training.requestCast(player); step(45)
            check(targets().count { it.health == 440f } == 1) { "Wall did not stop chain" }
            check(targets().count { it.health == 528f } == 2)
            instance.setBlock(0, 1, 4, Block.AIR)
            training.equip(player); step(3)
            training.requestCast(player); step(2)
            training.remove(player); step(3)
            check(instance.entities.size == 1) { "Cancel leaked: ${instance.entities.size}" }
            training.equip(player); step(3)
            training.requestCast(player); step(2)
            player.remove(); step(4)
            check(instance.entities.isEmpty()) { "Disconnect leaked ${instance.entities.size} entities" }
        }
        println("ICE FANG SMOKE PASS: 3 targets, 88 damage once each, spam/cooldown/wall/reset/cancel/disconnect, zero entity leaks; no game launched")
    } finally {
        process.dispatcher().shutdown()
        MinecraftServer.stopCleanly()
    }
}
