package dev.projects.client

import dev.projects.protocol.ProtocolMessage
import dev.projects.protocol.VfxEditor2PreviewStart
import dev.projects.protocol.VfxEditor2PreviewStop
import dev.projects.protocol.VfxEditor2Status
import dev.projects.protocol.VfxEditor2StatusKind
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** Small, world-preserving Checkpoint A workbench for the fixed preview pipeline. */
class VfxEditor2Screen(
    private val targetLabel: String,
    private val sendMessage: (ProtocolMessage) -> Unit,
) : Screen(Component.literal("VFX Workbench")) {
    private companion object {
        const val PANEL_WIDTH = 250
        const val PANEL_HEIGHT = 136
    }

    private var statusText = "Ready"
    private var previewActive = false
    private var requestId = 0L
    private var closeRequested = false

    private val panelX: Int
        get() = (width - PANEL_WIDTH - 18).coerceAtLeast(8)

    private val panelY: Int
        get() = ((height - PANEL_HEIGHT) / 2).coerceAtLeast(8)

    override fun isPauseScreen(): Boolean = false

    // Keep the live Minecraft world visible behind the small workbench.
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) = Unit

    override fun init() {
        val x = panelX
        val y = panelY
        addRenderableWidget(
            Button.builder(Component.literal("Play")) { play() }
                .bounds(x + 18, y + 58, 96, 20)
                .build(),
        )
        addRenderableWidget(
            Button.builder(Component.literal("Stop")) { stop() }
                .bounds(x + 124, y + 58, 96, 20)
                .build(),
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        val x = panelX
        val y = panelY
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xE6101824.toInt())
        graphics.fill(x, y, x + 2, y + PANEL_HEIGHT, 0xFFCF3348.toInt())
        graphics.text(font, "VFX Workbench", x + 14, y + 12, 0xFFE8F3FF.toInt(), true)
        graphics.text(font, "Target: $targetLabel", x + 14, y + 31, 0xFFD5E2F0.toInt(), false)
        graphics.text(font, "Preview: In Front of Player", x + 14, y + 88, 0xFF9BB4CE.toInt(), false)
        graphics.text(font, "Status: ${statusText.take(28)}", x + 14, y + 108, statusColor(), false)
    }

    fun setStatus(status: VfxEditor2Status) {
        statusText = when (status.kind) {
            VfxEditor2StatusKind.ERROR -> "Error: ${status.message}"
            else -> status.message
        }.ifBlank { status.kind.name }
        previewActive = status.kind == VfxEditor2StatusKind.PREVIEW_REQUESTED ||
            status.kind == VfxEditor2StatusKind.PLAYING
    }

    override fun onClose() {
        if (!closeRequested) {
            closeRequested = true
            previewActive = false
            sendMessage(VfxEditor2PreviewStop)
        }
        super.onClose()
    }

    private fun play() {
        requestId++
        previewActive = true
        statusText = "Preview requested"
        sendMessage(VfxEditor2PreviewStart(requestId))
    }

    private fun stop() {
        previewActive = false
        statusText = "Stopped"
        sendMessage(VfxEditor2PreviewStop)
    }

    private fun statusColor(): Int = when {
        statusText.startsWith("Error:") -> 0xFFFF6B6B.toInt()
        previewActive -> 0xFF8BE28B.toInt()
        else -> 0xFFFFD166.toInt()
    }
}
