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
import dev.projects.protocol.SlashEditorParameters
import dev.projects.protocol.VfxEditorNotice
import dev.projects.protocol.VfxEditorOpen
import dev.projects.protocol.VfxSlashDraft
import dev.projects.protocol.VfxSlashDraftList
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
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
    private var attackDebugShape: AttackDebugShape? = null
    private var attackDebugTicksRemaining = 0
    private var attackDebugEnabled = true
    private var slashDraftNames: List<String> = emptyList()
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
    private val activeCombatHudTheme = CombatHudTheme.TWIN_BLADES

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
                    else -> require(false) { "Unexpected ProjectS clientbound message" }
                }
            } catch (error: IllegalArgumentException) {
                context.player().connection.connection.disconnect(
                    Component.literal(error.message ?: "Invalid ProjectS protocol handshake"),
                )
            }
        }

        HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR) {
            original -> HudElement { context, tickCounter ->
                if (Minecraft.getInstance().player?.isSpectator() == true) {
                    original.extractRenderState(context, tickCounter)
                } else {
                    renderCombatHud(context, tickCounter)
                }
            }
        }
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

    private fun renderCombatHud(context: GuiGraphicsExtractor, tickCounter: DeltaTracker) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        if (player.isSpectator()) return

        val layout = calculateCombatHudLayout(context.guiWidth(), context.guiHeight())
        val theme = activeCombatHudTheme
        drawStatusBar(
            context,
            "HP ${player.getHealth().toInt()}/${player.getMaxHealth().toInt()}",
            player.getHealth().toInt(),
            player.getMaxHealth().toInt(),
            layout.health,
            theme.hpBackground,
            theme.hpFrame,
            theme.hpFill,
            theme.labelColor,
        )
        drawStatusBar(
            context,
            "${theme.resourceLabel} $mana/$maxMana",
            mana,
            maxMana,
            layout.resource,
            theme.resourceBackground,
            theme.resourceFrame,
            theme.resourceFill,
            theme.labelColor,
        )
        drawTexture(context, theme.core, layout.core)
        renderSkillHud(context, layout.skills, theme)
        renderHotbar(context, layout.hotbar, player, theme)
        renderOffhand(context, layout.offhand, player, theme)
        theme.storedPanelFrame?.let { renderStoredPanel(context, layout.stored, it) }
        renderHitMarker(context)
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

    private fun renderSkillHud(context: GuiGraphicsExtractor, bounds: HudRect, theme: CombatHudTheme) {
        val slotSize = 38
        val gap = 3
        val slots = listOf(
            SkillHudSlot(0, skill1Key, skill1CooldownTicks, skill1CooldownMaxTicks),
            SkillHudSlot(1, skill2Key, skill2CooldownTicks, skill2CooldownMaxTicks),
            SkillHudSlot(2, skill3Key, skill3CooldownTicks, skill3CooldownMaxTicks),
            SkillHudSlot(3, ultimateKey, 0, 0),
        )
        for ((index, slot) in slots.withIndex()) {
            val slotX = bounds.x + index * (slotSize + gap)
            drawSkillSlot(context, slot, HudRect(slotX, bounds.y, slotSize, slotSize), theme)
        }
    }

    private fun drawSkillSlot(
        context: GuiGraphicsExtractor,
        slot: SkillHudSlot,
        bounds: HudRect,
        theme: CombatHudTheme,
    ) {
        val ready = slot.remainingTicks <= 0
        drawTexture(context, theme.skillSlotFrame, bounds)
        val iconBounds = HudRect(bounds.x + 6, bounds.y + 4, 26, 26)
        drawTexture(context, theme.skillIcons[slot.index], iconBounds)
        val keyLabel = slot.key.getTranslatedKeyMessage().getString()
        context.text(
            Minecraft.getInstance().font,
            keyLabel,
            bounds.x + 4,
            bounds.bottom - 11,
            theme.labelColor,
            true,
        )
        if (!ready) {
            context.fill(iconBounds.x, iconBounds.y, iconBounds.right, iconBounds.bottom, 0xAA080B10.toInt())
            context.centeredText(
                Minecraft.getInstance().font,
                cooldownSecondsText(slot.remainingTicks),
                bounds.centerX,
                bounds.y + 13,
                theme.cooldownColor,
            )
        }
    }

    private fun drawStatusBar(
        context: GuiGraphicsExtractor,
        label: String,
        value: Int,
        maximum: Int,
        bounds: HudRect,
        background: HudTexture,
        frame: HudTexture,
        fill: HudTexture,
        labelColor: Int,
    ) {
        val ratio = if (maximum > 0) value.coerceIn(0, maximum).toFloat() / maximum else 0f
        drawTexture(context, background, bounds)
        if (ratio > 0f) {
            drawTexture(
                context,
                fill,
                HudRect(bounds.x, bounds.y, (bounds.width * ratio).toInt().coerceAtLeast(1), bounds.height),
                ratio,
            )
        }
        drawTexture(context, frame, bounds)
        context.text(Minecraft.getInstance().font, label, bounds.x + 7, bounds.y + 7, labelColor, true)
    }

    private fun renderHotbar(
        context: GuiGraphicsExtractor,
        bounds: HudRect,
        player: net.minecraft.client.player.LocalPlayer,
        theme: CombatHudTheme,
    ) {
        drawTexture(context, theme.hotbarFrame, bounds)
        val slotSize = 22
        val gap = 2
        val selectedSlot = player.inventory.getSelectedSlot()
        for (slot in 0 until 9) {
            val x = bounds.x + 5 + slot * (slotSize + gap)
            val y = bounds.y + 6
            val selected = slot == selectedSlot
            if (selected) drawTexture(context, theme.selectedSlot, HudRect(x, y, slotSize, slotSize))
            val stack = player.inventory.getItem(slot)
            if (!stack.isEmpty) {
                context.item(stack, x + 3, y + 3)
                context.itemDecorations(Minecraft.getInstance().font, stack, x + 3, y + 3)
            }
        }
    }

    private fun renderOffhand(
        context: GuiGraphicsExtractor,
        bounds: HudRect,
        player: net.minecraft.client.player.LocalPlayer,
        theme: CombatHudTheme,
    ) {
        drawTexture(context, theme.offhandFrame, bounds)
        val stack = player.getOffhandItem()
        if (!stack.isEmpty) {
            context.item(stack, bounds.x + 9, bounds.y + 9)
            context.itemDecorations(Minecraft.getInstance().font, stack, bounds.x + 9, bounds.y + 9)
        }
    }

    private fun renderStoredPanel(context: GuiGraphicsExtractor, bounds: HudRect, frame: HudTexture) {
        drawTexture(context, frame, bounds)
        context.text(Minecraft.getInstance().font, "STORED", bounds.x + 7, bounds.y + 5, 0xFFE8F5FF.toInt(), true)
        context.text(Minecraft.getInstance().font, "0", bounds.right - 13, bounds.y + 5, 0xFFFFFFFF.toInt(), true)
    }

    private fun drawTexture(
        context: GuiGraphicsExtractor,
        texture: HudTexture,
        bounds: HudRect,
        uRight: Float = 1f,
    ) {
        context.blit(texture.id, bounds.x, bounds.y, bounds.right, bounds.bottom, 0f, uRight, 0f, 1f)
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
        val index: Int,
        val key: KeyMapping,
        val remainingTicks: Int,
        val maxTicks: Int,
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
