package dev.projects.client

import dev.projects.protocol.PROJECTS_CHANNEL
import dev.projects.protocol.AttackDebugShape
import dev.projects.protocol.AttackDebugShapeKind
import dev.projects.protocol.AttackHitConfirmed
import dev.projects.protocol.AttackInput
import dev.projects.protocol.AttackInputState
import dev.projects.protocol.AttackStarted
import dev.projects.protocol.AirJumpInput
import dev.projects.protocol.ClassResourceSnapshot
import dev.projects.protocol.ClassSkillInput
import dev.projects.protocol.ClassSkillSlot
import dev.projects.protocol.DodgeInput
import dev.projects.protocol.GroundTelegraphRemove
import dev.projects.protocol.GroundTelegraphStart
import dev.projects.protocol.ProtocolCodec
import dev.projects.protocol.ProtocolHello
import dev.projects.protocol.ProtocolHelloAck
import dev.projects.protocol.ProtocolVersion
import dev.projects.protocol.RoninHudSnapshot
import dev.projects.protocol.SlashEditorParameters
import dev.projects.protocol.StarweaverHudSnapshot
import dev.projects.protocol.VfxEditorNotice
import dev.projects.protocol.VfxEditorOpen
import dev.projects.protocol.VfxEditor2Draft
import dev.projects.protocol.VfxEditor2DraftList
import dev.projects.protocol.VfxEditor2Notice
import dev.projects.protocol.VfxEditor2Open
import dev.projects.protocol.VfxSlashDraft
import dev.projects.protocol.VfxSlashDraftList
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
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.Items
import net.minecraft.world.InteractionHand
import org.slf4j.LoggerFactory
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object ProjectSClient : ClientModInitializer {
    private const val ATTACK_DEBUG_TICKS = 5
    private val attackDebugDust = DustParticleOptions(0xFF0000, 0.65f)
    private val hitCoreDust = DustParticleOptions(0xFFFFFF, 0.9f)
    private val logger = LoggerFactory.getLogger("projects")
    private var attackHeld = false
    private var dodgeHeld = false
    private var jumpHeld = false
    private var inputSequence = 0L
    private var suppressNextAttackStarted = false
    private var twinRodSwingOffhand = false
    private var hitMarkerTicksRemaining = 0
    private var mana = 0
    private var maxMana = 100
    private var skill1CooldownTicks = 0
    private var skill1CooldownMaxTicks = 80
    private var skill2CooldownTicks = 0
    private var skill2CooldownMaxTicks = 100
    private var skill3CooldownTicks = 0
    private var skill3CooldownMaxTicks = 60
    private var starweaverHudSnapshot: StarweaverHudSnapshot? = null
    private var roninHudSnapshot: RoninHudSnapshot? = null
    private var attackDebugShape: AttackDebugShape? = null
    private var attackDebugTicksRemaining = 0
    private var attackDebugEnabled = true
    private var slashDraftNames: List<String> = emptyList()
    private var vfxEditor2DraftNames: List<String> = emptyList()
    private val skillCategory = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath("projects", "skills"),
    )
    private val dodgeKey = KeyMapping(
        "key.projects.dodge",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_R,
        KeyMapping.Category.GAMEPLAY,
    )
    private val attackDebugKey = KeyMapping(
        "key.projects.attack_debug",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_F6,
        KeyMapping.Category.GAMEPLAY,
    )
    private val skill1Key = skillKey("key.projects.skill_1", InputConstants.KEY_Z)
    private val skill2Key = skillKey("key.projects.skill_2", InputConstants.KEY_X)
    private val skill3Key = skillKey("key.projects.skill_3", InputConstants.KEY_C)
    private val ultimateKey = skillKey("key.projects.ultimate", InputConstants.KEY_V)

    override fun onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(dodgeKey)
        KeyMappingHelper.registerKeyMapping(attackDebugKey)
        KeyMappingHelper.registerKeyMapping(skill1Key)
        KeyMappingHelper.registerKeyMapping(skill2Key)
        KeyMappingHelper.registerKeyMapping(skill3Key)
        KeyMappingHelper.registerKeyMapping(ultimateKey)
        PayloadTypeRegistry.clientboundPlay().register(ProjectSPayload.TYPE, ProjectSPayload.CODEC)
        PayloadTypeRegistry.serverboundPlay().register(ProjectSPayload.TYPE, ProjectSPayload.CODEC)
        GroundTelegraphRenderer.register()

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
                    is AttackDebugShape -> context.client().execute {
                        attackDebugShape = message
                        attackDebugTicksRemaining = ATTACK_DEBUG_TICKS
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
                    is StarweaverHudSnapshot -> context.client().execute {
                        starweaverHudSnapshot = message
                    }
                    is RoninHudSnapshot -> context.client().execute {
                        roninHudSnapshot = message
                    }
                    is GroundTelegraphStart -> context.client().execute {
                        GroundTelegraphRenderer.start(message)
                    }
                    is GroundTelegraphRemove -> context.client().execute {
                        GroundTelegraphRenderer.remove(message.telegraphId)
                    }
                    is VfxEditorOpen -> context.client().execute {
                        val client = context.client()
                        if (client.gui.screen() is SlashEditorScreen) return@execute
                        client.gui.setScreen(
                            SlashEditorScreen(message.parameters) { outgoing ->
                                if (outgoing is dev.projects.protocol.ProtocolMessage &&
                                    ClientPlayNetworking.canSend(ProjectSPayload.TYPE)
                                ) {
                                    ClientPlayNetworking.send(ProjectSPayload(ProtocolCodec.encode(outgoing)))
                                }
                            }.also { it.setDraftNames(slashDraftNames) },
                        )
                    }
                    is VfxSlashDraftList -> context.client().execute {
                        slashDraftNames = message.names
                        (context.client().gui.screen() as? SlashEditorScreen)?.setDraftNames(message.names)
                    }
                    is VfxSlashDraft -> context.client().execute {
                        (context.client().gui.screen() as? SlashEditorScreen)?.applyDraft(message.parameters)
                    }
                    is VfxEditorNotice -> context.client().execute {
                        context.client().player?.sendSystemMessage(Component.literal(message.text))
                    }
                    is VfxEditor2Open -> context.client().execute {
                        val client = context.client()
                        if (client.gui.screen() is VfxEditor2Screen) return@execute
                        client.gui.setScreen(
                            VfxEditor2Screen(message.composition) { outgoing ->
                                if (outgoing is dev.projects.protocol.ProtocolMessage &&
                                    ClientPlayNetworking.canSend(ProjectSPayload.TYPE)
                                ) {
                                    ClientPlayNetworking.send(ProjectSPayload(ProtocolCodec.encode(outgoing)))
                                }
                            }.also { it.setDraftNames(vfxEditor2DraftNames) },
                        )
                    }
                    is VfxEditor2DraftList -> context.client().execute {
                        vfxEditor2DraftNames = message.names
                        (context.client().gui.screen() as? VfxEditor2Screen)?.setDraftNames(message.names)
                    }
                    is VfxEditor2Draft -> context.client().execute {
                        (context.client().gui.screen() as? VfxEditor2Screen)?.applyDraft(message.composition)
                    }
                    is VfxEditor2Notice -> context.client().execute {
                        val screen = context.client().gui.screen() as? VfxEditor2Screen
                        if (screen != null) screen.setNotice(message.text)
                        else context.client().player?.sendSystemMessage(Component.literal(message.text))
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
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.HOTBAR,
            Identifier.fromNamespaceAndPath("projects", "starweaver_celestial"),
            ::renderStarweaverHud,
        )
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.HOTBAR,
            Identifier.fromNamespaceAndPath("projects", "ronin_iaido"),
            ::renderRoninHud,
        )
        ClientTickEvents.END_CLIENT_TICK.register(::handleAttackInput)
    }

    private fun handleAttackInput(client: Minecraft) {
        val player = client.player ?: run {
            attackHeld = false
            dodgeHeld = false
            jumpHeld = false
            suppressNextAttackStarted = false
            twinRodSwingOffhand = false
            hitMarkerTicksRemaining = 0
            mana = 0
            skill1CooldownTicks = 0
            skill2CooldownTicks = 0
            skill3CooldownTicks = 0
            starweaverHudSnapshot = null
            roninHudSnapshot = null
            attackDebugShape = null
            attackDebugTicksRemaining = 0
            slashDraftNames = emptyList()
            if (client.gui.screen() is SlashEditorScreen) client.gui.setScreen(null)
            return
        }
        renderAttackDebugShape(client)
        if (hitMarkerTicksRemaining > 0) hitMarkerTicksRemaining--
        if (attackDebugKey.consumeClick()) {
            attackDebugEnabled = !attackDebugEnabled
            player.sendSystemMessage(
                Component.literal("Attack Debug: ${if (attackDebugEnabled) "ON" else "OFF"}"),
            )
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
        renderHitMarker(context)
    }

    private fun renderStarweaverHud(context: GuiGraphicsExtractor, tickCounter: DeltaTracker) {
        StarweaverCelestialHud.render(context, starweaverHudSnapshot)
    }

    private fun renderRoninHud(context: GuiGraphicsExtractor, tickCounter: DeltaTracker) {
        RoninIaidoHud.render(context, roninHudSnapshot)
    }

    private fun renderHitMarker(context: GuiGraphicsExtractor) {
        if (hitMarkerTicksRemaining <= 0) return
        val centerX = context.guiWidth() / 2
        val centerY = context.guiHeight() / 2
        val spread = 3 + (3 - hitMarkerTicksRemaining)
        val arm = 3
        val color = 0xDDF5FFFF.toInt()
        context.fill(centerX - spread - arm, centerY - 1, centerX - spread, centerY + 2, color)
        context.fill(centerX + spread, centerY - 1, centerX + spread + arm, centerY + 2, color)
        context.fill(centerX - 1, centerY - spread - arm, centerX + 2, centerY - spread, color)
        context.fill(centerX - 1, centerY + spread, centerX + 2, centerY + spread + arm, color)
    }

    private fun renderSkillHud(context: GuiGraphicsExtractor) {
        val slotWidth = 38
        val slotHeight = 28
        val gap = 4
        val totalWidth = slotWidth * 4 + gap * 3
        val startX = (context.guiWidth() - totalWidth) / 2
        val y = context.guiHeight() - 84
        val ronin = roninHudSnapshot?.takeIf { it.selected }
        val slots = if (ronin != null) {
            listOf(
                SkillHudSlot("Q", skill1Key, ronin.qCooldownTicks, 160, true),
                SkillHudSlot(
                    when (ronin.wVariant) {
                        1 -> "Wound"
                        2 -> "Crosscut"
                        3 -> "Tempest"
                        else -> "W"
                    },
                    skill2Key,
                    0,
                    1,
                    ronin.wVariant > 0,
                ),
                SkillHudSlot("E", skill3Key, ronin.eCooldownTicks, 300, true),
                SkillHudSlot("R", ultimateKey, ronin.rCooldownTicks, 600, true),
            )
        } else {
            listOf(
                SkillHudSlot("S1", skill1Key, skill1CooldownTicks, skill1CooldownMaxTicks, true),
                SkillHudSlot("S2", skill2Key, skill2CooldownTicks, skill2CooldownMaxTicks, true),
                SkillHudSlot("S3", skill3Key, skill3CooldownTicks, skill3CooldownMaxTicks, true),
                SkillHudSlot("ULT", ultimateKey, 0, 1, false),
            )
        }
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

    private fun renderAttackDebugShape(client: Minecraft) {
        if (!attackDebugEnabled) {
            attackDebugShape = null
            attackDebugTicksRemaining = 0
            return
        }
        val shape = attackDebugShape ?: return
        if (client.level == null) return
        when (shape.kind) {
            AttackDebugShapeKind.TWIN_RODS -> renderTwinRodsDebugShape(client, shape)
            AttackDebugShapeKind.HEAVY_BLADE -> renderHeavyBladeDebugShape(client, shape)
        }
        attackDebugTicksRemaining--
        if (attackDebugTicksRemaining <= 0) attackDebugShape = null
    }

    private fun renderTwinRodsDebugShape(client: Minecraft, shape: AttackDebugShape) {
        val directionLength = sqrt(
            shape.directionX * shape.directionX +
                shape.directionY * shape.directionY +
                shape.directionZ * shape.directionZ,
        )
        if (directionLength <= 1.0e-9) return
        val dx = shape.directionX / directionLength
        val dy = shape.directionY / directionLength
        val dz = shape.directionZ / directionLength
        val referenceX: Double
        val referenceY: Double
        val referenceZ: Double
        if (abs(dy) < 0.9) {
            referenceX = 0.0
            referenceY = 1.0
            referenceZ = 0.0
        } else {
            referenceX = 1.0
            referenceY = 0.0
            referenceZ = 0.0
        }
        var ux = dy * referenceZ - dz * referenceY
        var uy = dz * referenceX - dx * referenceZ
        var uz = dx * referenceY - dy * referenceX
        val basisLength = sqrt(ux * ux + uy * uy + uz * uz)
        ux /= basisLength
        uy /= basisLength
        uz /= basisLength
        val vx = dy * uz - dz * uy
        val vy = dz * ux - dx * uz
        val vz = dx * uy - dy * ux
        val halfAngle = acos(shape.minForwardDot.coerceIn(-1.0, 1.0))
        val axialScale = cos(halfAngle)
        val radialScale = sin(halfAngle)
        val segments = 16
        val ring = Array(segments) { index ->
            val angle = 2.0 * Math.PI * index / segments
            val radialX = cos(angle) * ux + sin(angle) * vx
            val radialY = cos(angle) * uy + sin(angle) * vy
            val radialZ = cos(angle) * uz + sin(angle) * vz
            doubleArrayOf(
                shape.originX + shape.range * (axialScale * dx + radialScale * radialX),
                shape.originY + shape.range * (axialScale * dy + radialScale * radialY),
                shape.originZ + shape.range * (axialScale * dz + radialScale * radialZ),
            )
        }
        addDebugLine(
            client,
            shape.originX,
            shape.originY,
            shape.originZ,
            shape.originX + shape.range * dx,
            shape.originY + shape.range * dy,
            shape.originZ + shape.range * dz,
        )
        for (index in ring.indices) {
            val point = ring[index]
            val next = ring[(index + 1) % ring.size]
            addDebugLine(client, shape.originX, shape.originY, shape.originZ, point[0], point[1], point[2])
            addDebugLine(client, point[0], point[1], point[2], next[0], next[1], next[2], 3)
        }
    }

    private fun renderHeavyBladeDebugShape(client: Minecraft, shape: AttackDebugShape) {
        val horizontalLength = sqrt(shape.directionX * shape.directionX + shape.directionZ * shape.directionZ)
        if (horizontalLength <= 1.0e-9) return
        val forwardX = shape.directionX / horizontalLength
        val forwardZ = shape.directionZ / horizontalLength
        val rightX = -forwardZ
        val rightZ = forwardX
        val halfAngle = acos(shape.minForwardDot.coerceIn(-1.0, 1.0))
        val segments = 12
        val bottom = Array(segments + 1) { index ->
            horizontalSectorPoint(shape, forwardX, forwardZ, rightX, rightZ, halfAngle, index, segments, -shape.verticalRange)
        }
        val top = Array(segments + 1) { index ->
            horizontalSectorPoint(shape, forwardX, forwardZ, rightX, rightZ, halfAngle, index, segments, shape.verticalRange)
        }
        for (index in 0 until segments) {
            addDebugLine(client, bottom[index], bottom[index + 1], 3)
            addDebugLine(client, top[index], top[index + 1], 3)
        }
        for (index in bottom.indices) {
            addDebugLine(client, bottom[index], top[index], 3)
            if (index % 3 == 0) {
                addDebugLine(
                    client,
                    shape.originX,
                    shape.originY - shape.verticalRange,
                    shape.originZ,
                    bottom[index][0],
                    bottom[index][1],
                    bottom[index][2],
                    5,
                )
                addDebugLine(
                    client,
                    shape.originX,
                    shape.originY + shape.verticalRange,
                    shape.originZ,
                    top[index][0],
                    top[index][1],
                    top[index][2],
                    5,
                )
            }
        }
        addDebugLine(
            client,
            shape.originX,
            shape.originY - shape.verticalRange,
            shape.originZ,
            shape.originX,
            shape.originY + shape.verticalRange,
            shape.originZ,
        )
    }

    private fun horizontalSectorPoint(
        shape: AttackDebugShape,
        forwardX: Double,
        forwardZ: Double,
        rightX: Double,
        rightZ: Double,
        halfAngle: Double,
        index: Int,
        segments: Int,
        yOffset: Double,
    ): DoubleArray {
        val angle = -halfAngle + 2.0 * halfAngle * index / segments
        val x = cos(angle) * forwardX + sin(angle) * rightX
        val z = cos(angle) * forwardZ + sin(angle) * rightZ
        return doubleArrayOf(
            shape.originX + shape.range * x,
            shape.originY + yOffset,
            shape.originZ + shape.range * z,
        )
    }

    private fun addDebugLine(
        client: Minecraft,
        startX: Double,
        startY: Double,
        startZ: Double,
        endX: Double,
        endY: Double,
        endZ: Double,
        steps: Int = 6,
    ) {
        val level = client.level ?: return
        for (step in 0..steps) {
            val progress = step.toDouble() / steps
            level.addParticle(
                attackDebugDust,
                startX + (endX - startX) * progress,
                startY + (endY - startY) * progress,
                startZ + (endZ - startZ) * progress,
                0.0,
                0.0,
                0.0,
            )
        }
    }

    private fun addDebugLine(client: Minecraft, start: DoubleArray, end: DoubleArray, steps: Int) {
        addDebugLine(client, start[0], start[1], start[2], end[0], end[1], end[2], steps)
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
            Items.IRON_SWORD -> {
                val hand = if (twinRodSwingOffhand) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
                player.swing(hand, true)
                twinRodSwingOffhand = !twinRodSwingOffhand
            }
            else -> {
                player.swing(InteractionHand.MAIN_HAND, true)
                twinRodSwingOffhand = false
            }
        }
    }

    private fun showHitEffect(client: Minecraft, message: AttackHitConfirmed) {
        val player = client.player
        if (player?.mainHandItem?.item == Items.IRON_SWORD) {
            hitMarkerTicksRemaining = 3
            return
        }
        showHeavyBladeHitEffect(client, message)
    }

    private fun showHeavyBladeHitEffect(client: Minecraft, message: AttackHitConfirmed) {
        val level = client.level ?: return
        val target = level.getEntity(message.targetId) ?: return
        val player = client.player
        val hitY = target.y + target.bbHeight * 0.66
        val attackerX = player?.x ?: target.x
        val attackerY = player?.let { it.y + it.eyeHeight } ?: hitY
        val attackerZ = player?.z ?: target.z
        val towardAttackerX = attackerX - target.x
        val towardAttackerY = attackerY - hitY
        val towardAttackerZ = attackerZ - target.z
        val distance = sqrt(
            towardAttackerX * towardAttackerX +
                towardAttackerY * towardAttackerY +
                towardAttackerZ * towardAttackerZ,
        ).coerceAtLeast(1.0e-6)
        val normalX = towardAttackerX / distance
        val normalY = towardAttackerY / distance
        val normalZ = towardAttackerZ / distance
        val referenceX: Double
        val referenceY: Double
        val referenceZ: Double
        if (abs(normalY) < 0.9) {
            referenceX = 0.0
            referenceY = 1.0
            referenceZ = 0.0
        } else {
            referenceX = 1.0
            referenceY = 0.0
            referenceZ = 0.0
        }
        val rightX = referenceY * normalZ - referenceZ * normalY
        val rightY = referenceZ * normalX - referenceX * normalZ
        val rightZ = referenceX * normalY - referenceY * normalX
        val rightLength = sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ)
        val normalizedRightX = rightX / rightLength
        val normalizedRightY = rightY / rightLength
        val normalizedRightZ = rightZ / rightLength
        val upX = normalY * normalizedRightZ - normalZ * normalizedRightY
        val upY = normalZ * normalizedRightX - normalX * normalizedRightZ
        val upZ = normalX * normalizedRightY - normalY * normalizedRightX
        val surfaceDistance = 0.42
        val centerX = target.x + normalX * surfaceDistance
        val centerY = hitY + normalY * surfaceDistance
        val centerZ = target.z + normalZ * surfaceDistance

        level.addParticle(ParticleTypes.END_ROD, centerX, centerY, centerZ, 0.0, 0.0, 0.0)
        for (arm in -1..1) {
            val offset = arm * 0.22
            level.addParticle(
                hitCoreDust,
                centerX + normalizedRightX * offset,
                centerY + normalizedRightY * offset,
                centerZ + normalizedRightZ * offset,
                0.0,
                0.0,
                0.0,
            )
            level.addParticle(
                hitCoreDust,
                centerX + upX * offset,
                centerY + upY * offset,
                centerZ + upZ * offset,
                0.0,
                0.0,
                0.0,
            )
        }
        for (spark in -1..1) {
            val side = spark * 0.17
            level.addParticle(
                ParticleTypes.ELECTRIC_SPARK,
                centerX + normalizedRightX * side + normalX * 0.05,
                centerY + normalizedRightY * side + normalY * 0.05,
                centerZ + normalizedRightZ * side + normalZ * 0.05,
                normalizedRightX * side * 1.8,
                0.06 + abs(side) * 0.2,
                normalizedRightZ * side * 1.8,
            )
        }
        player?.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 0.82f, 1.02f)
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
