package dev.projects.client

import dev.projects.protocol.SlashEditorParameters
import dev.projects.protocol.VfxSlashDraftLoadRequest
import dev.projects.protocol.VfxSlashApplySkill3
import dev.projects.protocol.VfxSlashPreviewCancel
import dev.projects.protocol.VfxSlashPreviewRequest
import dev.projects.protocol.VfxSlashSaveRequest
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

class SlashEditorScreen(
    initialParameters: SlashEditorParameters,
    private val sendMessage: (Any) -> Unit,
) : Screen(Component.literal("斬撃エディター")) {
    private companion object {
        const val MIN_PARTICLE_SIZE = 0.05
        const val MAX_PARTICLE_SIZE = 2.0
        const val MIN_SPACING = 0.05
        const val MAX_SPACING = 2.0
        const val MIN_LANE_SPACING = 0.05
        const val MAX_LANE_SPACING = 2.0
        const val BASIC_ROW_SPACING = 16
        const val BASIC_START_Y = 30
    }

    private var parameters = initialParameters
    private var autoPreview = true
    private var previewDebounce = 0
    private var requestId = 0L
    private var draftNames: List<String> = emptyList()
    private var showDetails = false
    private val fields = linkedMapOf<String, EditBox>()
    private val sliders = linkedMapOf<String, ValueSlider>()
    private lateinit var draftName: EditBox
    private lateinit var autoButton: Button
    private lateinit var detailsButton: Button

    private data class BasicControl(
        val key: String,
        val label: String,
        val description: String,
        val min: Double,
        val max: Double,
        val decimals: Int = 1,
    )

    private val basicControls = listOf(
        BasicControl("length", "長さ", "斬撃が届く距離", 0.5, 12.0),
        BasicControl("curvature", "曲がり", "直線から強いカーブまで", 0.0, 4.0),
        BasicControl("arcSpan", "弧の広さ", "斬撃が描く円弧の大きさ", 10.0, 350.0, 0),
        BasicControl("tilt", "傾き", "斬撃面の角度", -90.0, 90.0, 0),
        BasicControl("yaw", "向き", "斬撃全体を左右へ回転", -180.0, 180.0, 0),
        BasicControl("originY", "開始高さ", "プレイヤー基準の発生高さ", -4.0, 8.0),
        BasicControl("width", "太さ", "斬撃1本の視覚的な太さ", 0.0, 1.5),
        BasicControl("laneCount", "線の本数", "平行に描く斬撃ラインの本数", 1.0, 3.0, 0),
        BasicControl("laneSpacing", "線の間隔", "斬撃ライン同士の距離", MIN_LANE_SPACING, MAX_LANE_SPACING, 2),
        BasicControl("particleSize", "粒の大きさ", "1粒の見た目の大きさ", MIN_PARTICLE_SIZE, MAX_PARTICLE_SIZE, 2),
        BasicControl("density", "密度", "斬撃ライン上の粒の詰まり具合", MIN_SPACING, MAX_SPACING, 2),
        BasicControl("forwardOffset", "前後位置", "斬撃全体をプレイヤーから前後に移動", 0.0, 8.0, 2),
    )

    private val detailNames = listOf(
        "forwardOffset" to "前方オフセット",
        "particleSize" to "粒子サイズ",
        "spacing" to "粒子間隔",
        "durationTicks" to "再生時間(tick)",
        "color" to "色 (#RRGGBB)",
    )

    override fun isPauseScreen(): Boolean = false

    // The default Screen background inserts a blur marker before the panel.
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) = Unit

    override fun init() {
        val panelX = width - 340
        basicControls.forEachIndexed { index, control ->
            val y = BASIC_START_Y + index * BASIC_ROW_SPACING
            val slider = ValueSlider(
                panelX + 112, y, 140, control, valueFor(control.key),
            ) { value -> updateBasicValue(control.key, value) }
            slider.visible = !showDetails
            sliders[control.key] = addRenderableWidget(slider)
        }
        rebuildDetails(panelX)

        val controlsY = height - 92
        addRenderableWidget(Button.builder(Component.literal("再生")) { replay() }.bounds(panelX + 8, controlsY, 72, 20).build())
        addRenderableWidget(Button.builder(Component.literal("リセット")) { reset() }.bounds(panelX + 84, controlsY, 72, 20).build())
        autoButton = addRenderableWidget(Button.builder(Component.literal(autoLabel())) { autoPreview = !autoPreview; autoButton.message = Component.literal(autoLabel()) }
            .bounds(panelX + 160, controlsY, 112, 20).build())
        detailsButton = addRenderableWidget(Button.builder(Component.literal(detailsLabel())) { showDetails = !showDetails; rebuildWidgets() }
            .bounds(panelX + 8, controlsY - 25, 120, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Skill3へ適用")) { applySkill3() }
            .bounds(panelX + 132, controlsY - 25, 140, 20).build())

        val draftY = height - 43
        draftName = addRenderableWidget(EditBox(font, panelX + 136, draftY, 108, 18, Component.literal("Draft名")))
        draftName.setMaxLength(32)
        addRenderableWidget(Button.builder(Component.literal("保存")) { saveDraft() }.bounds(panelX + 248, draftY, 40, 20).build())
        addRenderableWidget(Button.builder(Component.literal("読込")) { loadDraft() }.bounds(panelX + 292, draftY, 40, 20).build())
    }

    private fun rebuildDetails(panelX: Int) {
        if (!showDetails) return
        detailNames.forEachIndexed { index, (key, _) ->
            val field = EditBox(font, panelX + 136, 38 + index * 20, 180, 18, Component.literal(key))
            field.setValue(formatValue(key, parameters))
            field.setMaxLength(16)
            field.setResponder { updateFromFields() }
            fields[key] = addRenderableWidget(field)
        }
    }

    override fun rebuildWidgets() {
        clearWidgets()
        fields.clear()
        sliders.clear()
        init()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        val panelX = width - 340
        graphics.fill(panelX, 0, width, height, 0xD9101824.toInt())
        graphics.fill(panelX, 0, panelX + 2, height, 0xFF2871C7.toInt())
        graphics.text(font, "斬撃エディター", panelX + 10, 8, 0xFFE8F3FF.toInt(), true)
        graphics.text(font, "基本" + if (showDetails) " / 詳細" else "", panelX + 10, 22, 0xFF9BB4CE.toInt(), false)
        if (!showDetails) {
            basicControls.forEachIndexed { index, control ->
                val y = BASIC_START_Y + index * BASIC_ROW_SPACING + 5
                graphics.text(font, control.label, panelX + 10, y, 0xFFD5E2F0.toInt(), false)
            }
            val hoveredIndex = basicControls.indices.firstOrNull { index ->
                val y = BASIC_START_Y + index * BASIC_ROW_SPACING
                mouseX in (panelX + 112)..(panelX + 252) && mouseY in y..(y + 20)
            }
            if (hoveredIndex != null) {
                graphics.text(
                    font,
                    basicControls[hoveredIndex].description,
                    panelX + 10,
                    BASIC_START_Y + basicControls.size * BASIC_ROW_SPACING + 2,
                    0xFF8EA9C5.toInt(),
                    false,
                )
            }
        } else {
            detailNames.forEachIndexed { index, (_, label) ->
                graphics.text(font, label, panelX + 10, 43 + index * 20, 0xFFD5E2F0.toInt(), false)
            }
        }
        graphics.text(font, "Draft: ${draftNames.joinToString().ifBlank { "なし" }}", panelX + 10, height - 14, 0xFF8EA9C5.toInt(), false)
    }

    override fun tick() {
        super.tick()
        if (previewDebounce > 0) {
            previewDebounce--
            if (previewDebounce == 0 && autoPreview) sendPreview()
        }
    }

    fun setDraftNames(names: List<String>) { draftNames = names }

    fun applyDraft(loaded: SlashEditorParameters) {
        parameters = loaded
        fields.forEach { (key, field) -> field.setValue(formatValue(key, parameters)) }
        sliders.forEach { (key, slider) -> slider.setParameterValue(valueFor(key)) }
        replay()
    }

    override fun onClose() {
        sendMessage(VfxSlashPreviewCancel)
        super.onClose()
    }

    private fun updateBasicValue(key: String, value: Double) {
        parameters = parameters.withValue(key, value)
        fields[key]?.setValue(formatValue(key, parameters))
        if (key == "density") fields["spacing"]?.setValue(formatValue("spacing", parameters))
        previewDebounce = 5
    }

    private fun updateFromFields() {
        parseParameters()?.let { parameters = it; previewDebounce = 5 }
    }

    private fun parseParameters(): SlashEditorParameters? = runCatching {
        fun number(key: String): Double = fields[key]?.value?.toDouble() ?: valueFor(key)
        val color = fields["color"]?.value?.removePrefix("#")?.toInt(16) ?: parameters.color
        SlashEditorParameters.clamped(
            originY = valueFor("originY"), forwardOffset = number("forwardOffset"), length = valueFor("length"),
            arcSpan = valueFor("arcSpan"), curvature = valueFor("curvature"), tilt = valueFor("tilt"), yaw = number("yaw"),
            width = valueFor("width"), laneCount = valueFor("laneCount").roundToInt(), laneSpacing = valueFor("laneSpacing"),
            particleSize = number("particleSize"), spacing = number("spacing"),
            durationTicks = number("durationTicks").toInt(), color = color, targetDistance = number("targetDistance"),
        )
    }.getOrNull()

    private fun replay() { parseParameters()?.let { parameters = it; sendPreview() } }
    private fun sendPreview() { sendMessage(VfxSlashPreviewRequest(++requestId, parameters)) }

    private fun applySkill3() {
        parseParameters()?.let {
            parameters = it
            sendMessage(VfxSlashApplySkill3(it))
        }
    }

    private fun reset() {
        parameters = SlashEditorParameters()
        fields.forEach { (key, field) -> field.setValue(formatValue(key, parameters)) }
        sliders.forEach { (key, slider) -> slider.setParameterValue(valueFor(key)) }
        replay()
    }

    private fun saveDraft() { parseParameters()?.let { sendMessage(VfxSlashSaveRequest(draftName.value.trim(), it)) } }
    private fun loadDraft() { draftName.value.trim().takeIf { it.isNotEmpty() }?.let { sendMessage(VfxSlashDraftLoadRequest(it)) } }

    private fun valueFor(key: String): Double = when (key) {
        "originY" -> parameters.originY; "forwardOffset" -> parameters.forwardOffset; "length" -> parameters.length
        "arcSpan" -> parameters.arcSpan; "curvature" -> parameters.curvature; "tilt" -> parameters.tilt
        "yaw" -> parameters.yaw; "width" -> parameters.width; "laneCount" -> parameters.laneCount.toDouble(); "laneSpacing" -> parameters.laneSpacing
        "particleSize" -> parameters.particleSize
        "spacing" -> parameters.spacing; "density" -> densityForSpacing(parameters.spacing)
        "durationTicks" -> parameters.durationTicks.toDouble(); "targetDistance" -> parameters.targetDistance
        else -> 0.0
    }

    private fun formatValue(key: String, values: SlashEditorParameters = parameters): String = when (key) {
        "forwardOffset" -> values.forwardOffset.toString(); "yaw" -> values.yaw.toString(); "particleSize" -> values.particleSize.toString()
        "spacing" -> values.spacing.toString(); "durationTicks" -> values.durationTicks.toString(); "color" -> "#%06x".format(values.color)
        "targetDistance" -> values.targetDistance.toString(); else -> ""
    }

    private fun autoLabel() = if (autoPreview) "自動プレビュー: ON" else "自動プレビュー: OFF"
    private fun detailsLabel() = if (showDetails) "詳細を閉じる" else "詳細を表示"

    private fun SlashEditorParameters.withValue(key: String, value: Double) = SlashEditorParameters.clamped(
        originY = if (key == "originY") value else originY,
        forwardOffset = if (key == "forwardOffset") value else forwardOffset,
        length = if (key == "length") value else length, arcSpan = if (key == "arcSpan") value else arcSpan,
        curvature = if (key == "curvature") value else curvature, tilt = if (key == "tilt") value else tilt,
        yaw = if (key == "yaw") value else yaw, width = if (key == "width") value else width,
        laneCount = if (key == "laneCount") value.roundToInt() else laneCount,
        laneSpacing = if (key == "laneSpacing") value else laneSpacing,
        particleSize = if (key == "particleSize") value else particleSize,
        spacing = if (key == "density") spacingForDensity(value) else spacing,
        durationTicks = durationTicks, color = color, targetDistance = targetDistance,
    )

    private fun densityForSpacing(spacing: Double): Double =
        MAX_SPACING + MIN_SPACING - spacing

    private fun spacingForDensity(density: Double): Double =
        MAX_SPACING + MIN_SPACING - density

    private class ValueSlider(
        x: Int, y: Int, width: Int, private val control: BasicControl, value: Double,
        private val changed: (Double) -> Unit,
    ) : AbstractSliderButton(
        x, y, width, BASIC_ROW_SPACING, Component.literal(control.label),
        ((value - control.min) / (control.max - control.min)).coerceIn(0.0, 1.0),
    ) {
        private var current = value
        override fun updateMessage() { message = Component.literal("${control.label}: ${format(current, control.decimals)}") }
        override fun applyValue() {
            val raw = control.min + value * (control.max - control.min)
            current = if (control.key == "laneCount") raw.roundToInt().toDouble() else raw
            changed(current)
            updateMessage()
        }
        fun setParameterValue(value: Double) {
            current = if (control.key == "laneCount") value.roundToInt().toDouble() else value
            setValue(normalized(current))
            updateMessage()
        }
        companion object {
            fun normalized(value: Double, min: Double = 0.0, max: Double = 1.0) = ((value - min) / (max - min)).coerceIn(0.0, 1.0)
            private fun format(value: Double, decimals: Int) = "% .${decimals}f".format(value).trim()
        }
        private fun normalized(value: Double) = ((value - control.min) / (control.max - control.min)).coerceIn(0.0, 1.0)
    }
}
