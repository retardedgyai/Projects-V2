package dev.projects.client

import dev.projects.protocol.PROJECTS_CHANNEL
import dev.projects.protocol.AttackHitConfirmed
import dev.projects.protocol.AttackInput
import dev.projects.protocol.AttackInputState
import dev.projects.protocol.AttackStarted
import dev.projects.protocol.DodgeInput
import dev.projects.protocol.ProtocolCodec
import dev.projects.protocol.ProtocolHello
import dev.projects.protocol.ProtocolHelloAck
import dev.projects.protocol.ProtocolVersion
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.KeyMapping
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.network.chat.Component
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.Items
import org.slf4j.LoggerFactory

object ProjectSClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("projects")
    private var attackHeld = false
    private var dodgeHeld = false
    private var inputSequence = 0L
    private var suppressNextAttackStarted = false
    private var twinRodSide = false
    private val dodgeKey = KeyMapping(
        "key.projects.dodge",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_R,
        KeyMapping.Category.GAMEPLAY,
    )

    override fun onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(dodgeKey)
        PayloadTypeRegistry.clientboundPlay().register(ProjectSPayload.TYPE, ProjectSPayload.CODEC)
        PayloadTypeRegistry.serverboundPlay().register(ProjectSPayload.TYPE, ProjectSPayload.CODEC)

        ClientPlayNetworking.registerGlobalReceiver(ProjectSPayload.TYPE) { payload, context ->
            try {
                when (val message = ProtocolCodec.decode(payload.data)) {
                    is ProtocolHello -> {
                        ProtocolVersion.requireCompatible(message.version)
                        context.responseSender().sendPacket(
                            ProjectSPayload(ProtocolCodec.encode(ProtocolHelloAck(ProtocolVersion.CURRENT))),
                        )
                        logger.info("ProjectS protocol {} handshake complete", message.version)
                    }
                    is AttackStarted -> context.client().execute {
                        if (suppressNextAttackStarted) {
                            suppressNextAttackStarted = false
                        } else {
                            context.client().player?.let { showSwingEffect(context.client(), it) }
                        }
                    }
                    is AttackHitConfirmed -> context.client().execute {
                        showHitEffect(context.client(), message)
                    }
                    else -> require(false) { "Unexpected ProjectS clientbound message" }
                }
            } catch (error: IllegalArgumentException) {
                context.player().connection.connection.disconnect(
                    Component.literal(error.message ?: "Invalid ProjectS protocol handshake"),
                )
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register(::handleAttackInput)
    }

    private fun handleAttackInput(client: Minecraft) {
        val player = client.player ?: run {
            attackHeld = false
            dodgeHeld = false
            suppressNextAttackStarted = false
            twinRodSide = false
            return
        }
        val dodgePressed = dodgeKey.isDown()
        if (dodgePressed != dodgeHeld) {
            dodgeHeld = dodgePressed
            if (dodgePressed && client.getConnection() != null && ClientPlayNetworking.canSend(ProjectSPayload.TYPE)) {
                val directionX = (if (client.options.keyRight.isDown()) 1.0 else 0.0) -
                    (if (client.options.keyLeft.isDown()) 1.0 else 0.0)
                val directionZ = (if (client.options.keyUp.isDown()) 1.0 else 0.0) -
                    (if (client.options.keyDown.isDown()) 1.0 else 0.0)
                ClientPlayNetworking.send(
                    ProjectSPayload(ProtocolCodec.encode(DodgeInput(directionX, directionZ))),
                )
            }
        }
        val pressed = client.options.keyAttack.isDown()
        if (pressed == attackHeld) return
        attackHeld = pressed

        val state = if (pressed) AttackInputState.PRESS else AttackInputState.RELEASE
        if (pressed) suppressNextAttackStarted = true
        if (client.getConnection() != null && ClientPlayNetworking.canSend(ProjectSPayload.TYPE)) {
            ClientPlayNetworking.send(
                ProjectSPayload(ProtocolCodec.encode(AttackInput(state, inputSequence++))),
            )
        }
        if (pressed) showSwingEffect(client, player)
    }

    private fun showSwingEffect(client: Minecraft, player: net.minecraft.client.player.LocalPlayer) {
        val level = client.level ?: return
        val position = player.position().add(player.lookAngle.scale(1.0)).add(0.0, 1.0, 0.0)
        when (player.mainHandItem.item) {
            Items.NETHERITE_SWORD -> {
                level.addParticle(ParticleTypes.SWEEP_ATTACK, position.x, position.y, position.z, 0.0, 0.0, 0.0)
                level.addParticle(ParticleTypes.SWEEP_ATTACK, position.x, position.y + 0.18, position.z, 0.0, 0.0, 0.0)
                level.addParticle(ParticleTypes.SWEEP_ATTACK, position.x, position.y - 0.18, position.z, 0.0, 0.0, 0.0)
                player.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 0.7f)
            }
            Items.BLAZE_ROD -> {
                twinRodSide = !twinRodSide
                val side = if (twinRodSide) 0.28 else -0.28
                val look = player.lookAngle
                val horizontalLength = kotlin.math.sqrt(look.x * look.x + look.z * look.z)
                val rightX = if (horizontalLength > 1.0e-6) look.z / horizontalLength else 1.0
                val rightZ = if (horizontalLength > 1.0e-6) -look.x / horizontalLength else 0.0
                level.addParticle(
                    ParticleTypes.SWEEP_ATTACK,
                    position.x + rightX * side,
                    position.y,
                    position.z + rightZ * side,
                    0.0,
                    0.0,
                    0.0,
                )
                player.playSound(SoundEvents.PLAYER_ATTACK_WEAK, 0.45f, 1.35f)
            }
            else -> {
                level.addParticle(ParticleTypes.SWEEP_ATTACK, position.x, position.y, position.z, 0.0, 0.0, 0.0)
                player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.45f, 1.0f)
            }
        }
    }

    private fun showHitEffect(client: Minecraft, message: AttackHitConfirmed) {
        val level = client.level ?: return
        val target = level.getEntity(message.targetId) ?: return
        val hitY = target.y + target.bbHeight * 0.65
        val offsets = arrayOf(
            doubleArrayOf(-0.18, -0.12),
            doubleArrayOf(0.0, -0.16),
            doubleArrayOf(0.18, -0.12),
            doubleArrayOf(-0.2, 0.08),
            doubleArrayOf(0.2, 0.08),
            doubleArrayOf(-0.12, 0.2),
            doubleArrayOf(0.12, 0.2),
            doubleArrayOf(0.0, 0.0),
        )
        for ((offsetX, offsetZ) in offsets) {
            level.addParticle(
                ParticleTypes.CRIT,
                target.x + offsetX,
                hitY,
                target.z + offsetZ,
                offsetX * 0.35,
                0.08,
                offsetZ * 0.35,
            )
        }
        client.player?.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 0.6f, 1.1f)
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
