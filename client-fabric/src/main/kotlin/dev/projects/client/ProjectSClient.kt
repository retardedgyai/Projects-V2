package dev.projects.client

import dev.projects.protocol.PROJECTS_CHANNEL
import dev.projects.protocol.ProtocolCodec
import dev.projects.protocol.ProtocolHello
import dev.projects.protocol.ProtocolHelloAck
import dev.projects.protocol.ProtocolVersion
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory

object ProjectSClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("projects")

    override fun onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().register(ProjectSPayload.TYPE, ProjectSPayload.CODEC)
        PayloadTypeRegistry.serverboundPlay().register(ProjectSPayload.TYPE, ProjectSPayload.CODEC)

        ClientPlayNetworking.registerGlobalReceiver(ProjectSPayload.TYPE) { payload, context ->
            try {
                val hello = ProtocolCodec.decode(payload.data)
                require(hello is ProtocolHello) { "Expected ProjectS protocol hello" }
                ProtocolVersion.requireCompatible(hello.version)
                context.responseSender().sendPacket(
                    ProjectSPayload(ProtocolCodec.encode(ProtocolHelloAck(ProtocolVersion.CURRENT))),
                )
                logger.info("ProjectS protocol {} handshake complete", hello.version)
            } catch (error: IllegalArgumentException) {
                context.player().connection.connection.disconnect(
                    Component.literal(error.message ?: "Invalid ProjectS protocol handshake"),
                )
            }
        }
    }
}

data class ProjectSPayload(val data: ByteArray) : CustomPacketPayload {
    constructor(buffer: RegistryFriendlyByteBuf) : this(ByteArray(buffer.readableBytes()).also(buffer::readBytes))

    fun write(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBytes(data)
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ProjectSPayload>(Identifier.fromNamespaceAndPath("projects", "protocol"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, ProjectSPayload> =
            CustomPacketPayload.codec(ProjectSPayload::write, ::ProjectSPayload)
    }
}
