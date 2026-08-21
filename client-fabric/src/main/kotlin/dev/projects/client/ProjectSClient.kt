package dev.projects.client

import dev.projects.protocol.PROJECTS_CHANNEL
import dev.projects.protocol.AttackHitConfirmed
import dev.projects.protocol.AttackInput
import dev.projects.protocol.AttackInputState
import dev.projects.protocol.AttackStarted
import dev.projects.protocol.AirJumpInput
import dev.projects.protocol.ClassResourceSnapshot
import dev.projects.protocol.ClassSkillInput
import dev.projects.protocol.ClassSkillSlot
import dev.projects.protocol.DodgeInput
import dev.projects.protocol.ProtocolCodec
import dev.projects.protocol.ProtocolHello
import dev.projects.protocol.ProtocolHelloAck
import dev.projects.protocol.ProtocolVersion
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.KeyMapping
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor
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
    private var jumpHeld = false
    private var inputSequence = 0L
    private var suppressNextAttackStarted = false
    private var twinRodSide = false
    private var mana = 0
    private var maxMana = 100
    private var skill1CooldownTicks = 0
    private var skill1CooldownMaxTicks = 80
    private var skill2CooldownTicks = 0
    private var skill2CooldownMaxTicks = 100
    private var skill3CooldownTicks = 0
    private var skill3CooldownMaxTicks = 60
    private val skillCategory = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath("projects", "skills"),
    )
    private val dodgeKey = KeyMapping(
        "key.projects.dodge",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_R,
        KeyMapping.Category.GAMEPLAY,
    )
    private val skill1Key = skillKey("key.projects.skill_1", InputConstants.KEY_Z)
    private val skill2Key = skillKey("key.projects.skill_2", InputConstants.KEY_X)
    private val skill3Key = skillKey("key.projects.skill_3", InputConstants.KEY_C)
    private val ultimateKey = skillKey("key.projects.ultimate", InputConstants.KEY_V)

    override fun onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(dodgeKey)
        KeyMappingHelper.registerKeyMapping(skill1Key)
        KeyMappingHelper.registerKeyMapping(skill2Key)
        KeyMappingHelper.registerKeyMapping(skill3Key)
        KeyMappingHelper.registerKeyMapping(ultimateKey)
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
                    is ClassResourceSnapshot -> context.client().execute {
                        mana = message.mana
                        maxMana = message.maxMana
                        skill1CooldownTicks = message.skill1CooldownTicks
                        skill1CooldownMaxTicks = message.skill1CooldownMaxTicks
                        skill2CooldownTicks = message.skill2CooldownTicks
                        skill2CooldownMaxTicks = message.skill2CooldownMaxTicks
                        skill3CooldownTicks = message.skill3CooldownTicks
                        skill3CooldownMaxTicks = message.skill3CooldownMaxTicks
                    }
                    else -> require(false) { "Unexpected ProjectS clientbound message" }
                }
            } catch (error: IllegalArgumentException) {
                context.player().connection.connection.disconnect(
                    Component.literal(error.message ?: "Invalid ProjectS protocol handshake"),
                )
            }
        }

        HudElementRegistry.attachElementAfter(
            VanillaHudElements.HOTBAR,
            Identifier.fromNamespaceAndPath("projects", "class_resources"),
            ::renderResourceHud,
        )
        ClientTickEvents.END_CLIENT_TICK.register(::handleAttackInput)
    }

    private fun handleAttackInput(client: Minecraft) {
        val player = client.player ?: run {
            attackHeld = false
            dodgeHeld = false
            jumpHeld = false
            suppressNextAttackStarted = false
            twinRodSide = false
            mana = 0
            skill1CooldownTicks = 0
            skill2CooldownTicks = 0
            skill3CooldownTicks = 0
            return
        }
        val jumpPressed = client.options.keyJump.isDown()
        if (jumpPressed != jumpHeld) {
            jumpHeld = jumpPressed
            if (jumpPressed && !player.onGround() && client.getConnection() != null &&
                ClientPlayNetworking.canSend(ProjectSPayload.TYPE)
            ) {
                ClientPlayNetworking.send(
                    ProjectSPayload(ProtocolCodec.encode(AirJumpInput(movementX(client), movementZ(client)))),
                )
            }
        }
        sendSkillInputs(client)
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

    private fun sendSkillInputs(client: Minecraft) {
        val keys = listOf(
            skill1Key to ClassSkillSlot.SKILL_1,
            skill2Key to ClassSkillSlot.SKILL_2,
            skill3Key to ClassSkillSlot.SKILL_3,
            ultimateKey to ClassSkillSlot.ULTIMATE,
        )
        if (client.getConnection() == null || !ClientPlayNetworking.canSend(ProjectSPayload.TYPE)) return
        for ((key, slot) in keys) {
            if (key.consumeClick()) {
                ClientPlayNetworking.send(
                    ProjectSPayload(ProtocolCodec.encode(ClassSkillInput(slot, movementX(client), movementZ(client)))),
                )
            }
        }
    }

    private fun movementX(client: Minecraft): Double =
        (if (client.options.keyRight.isDown()) 1.0 else 0.0) -
            (if (client.options.keyLeft.isDown()) 1.0 else 0.0)

    private fun movementZ(client: Minecraft): Double =
        (if (client.options.keyUp.isDown()) 1.0 else 0.0) -
            (if (client.options.keyDown.isDown()) 1.0 else 0.0)

    private fun renderResourceHud(context: GuiGraphicsExtractor, tickCounter: DeltaTracker) {
        val barWidth = 130
        val barHeight = 5
        val x = (context.guiWidth() - barWidth) / 2
        val y = context.guiHeight() - 52
        drawResourceBar(context, "MANA $mana / $maxMana", mana, maxMana, x, y, barWidth, barHeight, 0xFF4C9BFF.toInt())
        renderSkillHud(context)
    }

    private fun renderSkillHud(context: GuiGraphicsExtractor) {
        val slotWidth = 38
        val slotHeight = 28
        val gap = 4
        val totalWidth = slotWidth * 4 + gap * 3
        val startX = (context.guiWidth() - totalWidth) / 2
        val y = context.guiHeight() - 84
        val slots = listOf(
            SkillHudSlot("S1", skill1Key, skill1CooldownTicks, skill1CooldownMaxTicks, true),
            SkillHudSlot("S2", skill2Key, skill2CooldownTicks, skill2CooldownMaxTicks, true),
            SkillHudSlot("S3", skill3Key, skill3CooldownTicks, skill3CooldownMaxTicks, true),
            SkillHudSlot("ULT", ultimateKey, 0, 1, false),
        )
        for ((index, slot) in slots.withIndex()) {
            val slotX = startX + index * (slotWidth + gap)
            drawSkillSlot(context, slot, slotX, y, slotWidth, slotHeight)
        }
    }

    private fun drawSkillSlot(
        context: GuiGraphicsExtractor,
        slot: SkillHudSlot,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        val ready = slot.implemented && slot.remainingTicks == 0
        val background = if (ready) 0xDD1E6B78.toInt() else 0xDD18202A.toInt()
        context.fill(x, y, x + width, y + height, background)
        if (!ready) {
            val fillHeight = (height * cooldownFillRatio(slot.remainingTicks, slot.maxTicks)).toInt()
            if (fillHeight > 0) {
                context.fill(x, y, x + width, y + fillHeight, 0xAA080B10.toInt())
            }
        }
        val keyLabel = slot.key.getTranslatedKeyMessage().getString()
        val keyColor = if (slot.implemented) 0xFFFFFFFF.toInt() else 0xFF707780.toInt()
        context.text(Minecraft.getInstance().font, slot.name, x + 3, y + 3, keyColor, true)
        context.text(Minecraft.getInstance().font, keyLabel, x + 3, y + height - 10, keyColor, true)
        val centerText = when {
            !slot.implemented -> "--"
            ready -> "READY"
            else -> cooldownSecondsText(slot.remainingTicks)
        }
        val textWidth = Minecraft.getInstance().font.width(centerText)
        context.text(
            Minecraft.getInstance().font,
            centerText,
            x + (width - textWidth) / 2,
            y + 9,
            keyColor,
            true,
        )
    }

    private fun drawResourceBar(
        context: GuiGraphicsExtractor,
        label: String,
        value: Int,
        maximum: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int,
    ) {
        context.fill(x, y, x + width, y + height, 0xAA10151C.toInt())
        val filled = if (maximum > 0) width * value.coerceIn(0, maximum) / maximum else 0
        if (filled > 0) context.fill(x, y, x + filled, y + height, color)
        context.text(Minecraft.getInstance().font, label, x, y + 6, 0xFFFFFFFF.toInt(), true)
    }

    private fun skillKey(name: String, defaultKey: Int): KeyMapping = KeyMapping(
        name,
        InputConstants.Type.KEYSYM,
        defaultKey,
        skillCategory,
    )

    private data class SkillHudSlot(
        val name: String,
        val key: KeyMapping,
        val remainingTicks: Int,
        val maxTicks: Int,
        val implemented: Boolean,
    )

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
