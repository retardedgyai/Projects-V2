package dev.projects.client

import dev.projects.protocol.SlashEditorParameters
import dev.projects.protocol.VfxSlashDraftLoadRequest
import dev.projects.protocol.VfxSlashPreviewCancel
import dev.projects.protocol.VfxSlashPreviewRequest
import dev.projects.protocol.VfxSlashSaveRequest
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class SlashEditorScreen(
    initialParameters: SlashEditorParameters,
    private val sendMessage: (Any) -> Unit,
) : Screen(Component.literal("Slash Editor")) {
    private var parameters = initialParameters
    private var autoPreview = true
    private var previewDebounce = 0
    private var requestId = 0L
    private var draftNames: List<String> = emptyList()
    private val fields = linkedMapOf<String, EditBox>()
    private lateinit var draftName: EditBox

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        val panelX = width - 326
        val fieldX = panelX + 145
        val fieldWidth = 155
        val names = listOf(
            "originY" to "Origin Y",
            "forwardOffset" to "Forward Offset",
            "length" to "Length / Reach",
            "arcSpan" to "Arc Span",
            "curvature" to "Curvature / Radius",
            "tilt" to "Tilt",
            "yaw" to "Yaw",
            "width" to "Width / lane offset",
            "particleSize" to "Particle Size",
            "spacing" to "Spacing / density",
            "durationTicks" to "Duration ticks",
            "color" to "Color (#RRGGBB)",
            "targetDistance" to "Target Distance",
        )
        names.forEachIndexed { index, (key, _) ->
            val field = EditBox(font, fieldX, 31 + index * 20, fieldWidth, 18, Component.literal(key))
            field.setValue(formatValue(key, parameters))
            field.setMaxLength(16)
            field.setResponder { updateFromFields() }
            fields[key] = addRenderableWidget(field)
        }

        val buttonY = 31 + names.size * 20 + 5
        addRenderableWidget(Button.builder(Component.literal("Replay")) { replay() }.bounds(panelX + 8, buttonY, 76, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Reset")) { reset() }.bounds(panelX + 88, buttonY, 76, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Auto: ON")) { autoPreview = !autoPreview }.bounds(panelX + 168, buttonY, 100, 20).build())

        val draftY = buttonY + 27
        draftName = addRenderableWidget(EditBox(font, panelX + 8, draftY, 180, 18, Component.literal("Draft name")))
        draftName.setMaxLength(32)
        addRenderableWidget(Button.builder(Component.literal("Save Draft")) { saveDraft() }.bounds(panelX + 192, draftY, 76, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Load Draft")) { loadDraft() }.bounds(panelX + 192, draftY + 23, 76, 20).build())
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        val panelX = width - 326
        graphics.fill(panelX, 0, width, height, 0xD9101824.toInt())
        graphics.fill(panelX, 0, panelX + 2, height, 0xFF2871C7.toInt())
        graphics.text(font, "SLASH TUNER MVP", panelX + 8, 8, 0xFFE8F3FF.toInt(), true)
        graphics.text(font, "Edit values while watching the world", panelX + 8, 19, 0xFF9BB4CE.toInt(), false)
        val labels = listOf(
            "Origin Y", "Forward Offset", "Length / Reach", "Arc Span", "Curvature / Radius", "Tilt", "Yaw",
            "Width / lane offset", "Particle Size", "Spacing / density", "Duration ticks", "Color (#RRGGBB)", "Target Distance",
        )
        labels.forEachIndexed { index, label ->
            graphics.text(font, label, panelX + 8, 36 + index * 20, 0xFFD5E2F0.toInt(), false)
        }
        graphics.text(font, "Drafts: ${draftNames.joinToString().ifBlank { "none" }}", panelX + 8, height - 14, 0xFF8EA9C5.toInt(), false)
    }

    override fun tick() {
        super.tick()
        if (previewDebounce > 0) {
            previewDebounce--
            if (previewDebounce == 0 && autoPreview) sendPreview()
        }
    }

    fun setDraftNames(names: List<String>) {
        draftNames = names
    }

    fun applyDraft(loaded: SlashEditorParameters) {
        parameters = loaded
        fields.forEach { (key, field) -> field.setValue(formatValue(key, parameters)) }
        replay()
    }

    override fun onClose() {
        sendMessage(VfxSlashPreviewCancel)
        super.onClose()
    }

    private fun updateFromFields() {
        val next = parseParameters() ?: return
        parameters = next
        previewDebounce = 5
    }

    private fun parseParameters(): SlashEditorParameters? = runCatching {
        fun number(key: String): Double = fields.getValue(key).value.toDouble()
        val color = fields.getValue("color").value.removePrefix("#").toInt(16)
        SlashEditorParameters.clamped(
            originY = number("originY"),
            forwardOffset = number("forwardOffset"),
            length = number("length"),
            arcSpan = number("arcSpan"),
            curvature = number("curvature"),
            tilt = number("tilt"),
            yaw = number("yaw"),
            width = number("width"),
            particleSize = number("particleSize"),
            spacing = number("spacing"),
            durationTicks = number("durationTicks").toInt(),
            color = color,
            targetDistance = number("targetDistance"),
        )
    }.getOrNull()

    private fun replay() {
        parseParameters()?.let {
            parameters = it
            sendPreview()
        }
    }

    private fun sendPreview() {
        sendMessage(VfxSlashPreviewRequest(++requestId, parameters))
    }

    private fun reset() {
        parameters = SlashEditorParameters()
        fields.forEach { (key, field) -> field.setValue(formatValue(key, parameters)) }
        replay()
    }

    private fun saveDraft() {
        parseParameters()?.let { sendMessage(VfxSlashSaveRequest(draftName.value.trim(), it)) }
    }

    private fun loadDraft() {
        val name = draftName.value.trim()
        if (name.isNotEmpty()) sendMessage(VfxSlashDraftLoadRequest(name))
    }

    private fun formatValue(key: String, values: SlashEditorParameters): String = when (key) {
        "originY" -> values.originY.toString()
        "forwardOffset" -> values.forwardOffset.toString()
        "length" -> values.length.toString()
        "arcSpan" -> values.arcSpan.toString()
        "curvature" -> values.curvature.toString()
        "tilt" -> values.tilt.toString()
        "yaw" -> values.yaw.toString()
        "width" -> values.width.toString()
        "particleSize" -> values.particleSize.toString()
        "spacing" -> values.spacing.toString()
        "durationTicks" -> values.durationTicks.toString()
        "color" -> "#%06x".format(values.color)
        "targetDistance" -> values.targetDistance.toString()
        else -> ""
    }
}
