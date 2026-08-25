package dev.projects.client

import dev.projects.protocol.ProtocolMessage
import dev.projects.protocol.VfxEditor2ApplyRequest
import dev.projects.protocol.VfxEditor2Composition
import dev.projects.protocol.VfxEditor2Layer
import dev.projects.protocol.VfxEditor2LoadRequest
import dev.projects.protocol.VfxEditor2Particle
import dev.projects.protocol.VfxEditor2PreviewCancel
import dev.projects.protocol.VfxEditor2PreviewRequest
import dev.projects.protocol.VfxEditor2SaveRequest
import dev.projects.protocol.VfxEditor2Shape
import dev.projects.protocol.VfxEditor2ShapeParameters
import dev.projects.protocol.VfxEditor2WidthCurve
import dev.projects.protocol.VfxEditor2Offset
import dev.projects.protocol.VfxEditor2Rotation
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.Locale
import kotlin.math.roundToInt

/** World-preserving right-side authoring panel for the Editor 2 composition model. */
class VfxEditor2Screen(
    initialComposition: VfxEditor2Composition,
    private val sendMessage: (ProtocolMessage) -> Unit,
) : Screen(Component.literal("VFX Editor 2")) {
    private enum class Category { TRANSFORM, APPEARANCE, GEOMETRY, TIMING }

    private data class FieldSpec(
        val key: String,
        val label: String,
        val decimals: Int = 2,
        val integer: Boolean = false,
    )

    private var composition = initialComposition
    private var selectedLayerIndex = 0
    private var layerPage = 0
    private var category = Category.TRANSFORM
    private var autoPreview = true
    private var loop = false
    private var previewDebounce = 0
    private var requestId = 0L
    private var draftNames: List<String> = emptyList()
    private var notice = ""
    private var updatingFields = false
    private val fields = linkedMapOf<String, EditBox>()
    private lateinit var draftName: EditBox

    private val panelWidth: Int
        get() = 360.coerceAtMost((width * 0.42).toInt().coerceAtLeast(320)).coerceAtMost(width)
    private val panelX: Int get() = width - panelWidth

    override fun isPauseScreen(): Boolean = false

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) = Unit

    override fun init() {
        val x = panelX
        addRenderableWidget(Button.builder(Component.literal("再生")) { replay() }.bounds(x + 6, 40, 52, 20).build())
        addRenderableWidget(Button.builder(Component.literal(loopLabel())) { loop = !loop; rebuildWidgets(); replay() }.bounds(x + 62, 40, 62, 20).build())
        addRenderableWidget(Button.builder(Component.literal(autoLabel())) { autoPreview = !autoPreview; rebuildWidgets() }.bounds(x + 130, 40, 72, 20).build())
        addRenderableWidget(Button.builder(Component.literal("保存")) { save() }.bounds(x + 208, 40, 44, 20).build())
        addRenderableWidget(Button.builder(Component.literal("読込")) { load() }.bounds(x + 258, 40, 44, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Reset")) { reset() }.bounds(x + 308, 40, 46, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Apply Ronin Q")) { applyRoninQ() }.bounds(x + 6, 64, 130, 20).build())

        draftName = addRenderableWidget(EditBox(font, x + 70, 18, 176, 18, Component.literal("Draft Name")))
        draftName.setMaxLength(32)
        draftName.setValue(composition.name)

        val duration = addRenderableWidget(EditBox(font, x + 290, 18, 64, 18, Component.literal("Duration")))
        duration.setMaxLength(4)
        duration.setValue(composition.durationTicks.toString())
        duration.setResponder { value ->
            value.toIntOrNull()?.let { ticks ->
                composition = composition.copy(durationTicks = ticks.coerceIn(1, 200))
                previewDebounce = 5
            }
        }

        val visibleRows = visibleLayerRows()
        val first = layerPage * visibleRows
        repeat(visibleRows) { row ->
            val index = first + row
            val layer = composition.layers.getOrNull(index) ?: return@repeat
            val y = 92 + row * 20
            addRenderableWidget(Button.builder(Component.literal(layerLabel(layer, index))) { selectedLayerIndex = index; category = Category.TRANSFORM; rebuildWidgets() }
                .bounds(x + 6, y, 170, 18).build())
            addRenderableWidget(Button.builder(Component.literal(if (layer.enabled) "E" else "-") ) { toggleEnabled(index) }
                .bounds(x + 180, y, 26, 18).build())
            addRenderableWidget(Button.builder(Component.literal(if (layer.solo) "S" else "·")) { toggleSolo(index) }
                .bounds(x + 210, y, 26, 18).build())
        }
        val layerActionsY = 92 + visibleRows * 20 + 2
        addRenderableWidget(Button.builder(Component.literal("+ Add")) { addLayer() }.bounds(x + 6, layerActionsY, 52, 18).build())
        addRenderableWidget(Button.builder(Component.literal("Duplicate")) { duplicateLayer() }.bounds(x + 62, layerActionsY, 66, 18).build())
        addRenderableWidget(Button.builder(Component.literal("Delete")) { deleteLayer() }.bounds(x + 134, layerActionsY, 52, 18).build())
        addRenderableWidget(Button.builder(Component.literal("▲")) { moveLayer(-1) }.bounds(x + 192, layerActionsY, 28, 18).build())
        addRenderableWidget(Button.builder(Component.literal("▼")) { moveLayer(1) }.bounds(x + 224, layerActionsY, 28, 18).build())
        if (composition.layers.size > visibleRows) {
            addRenderableWidget(Button.builder(Component.literal("◀")) { layerPage = (layerPage - 1).coerceAtLeast(0); rebuildWidgets() }
                .bounds(x + 258, layerActionsY, 28, 18).build())
            addRenderableWidget(Button.builder(Component.literal("▶")) { layerPage = (layerPage + 1).coerceAtMost(maxLayerPage()); rebuildWidgets() }
                .bounds(x + 290, layerActionsY, 28, 18).build())
        }

        val typeY = layerActionsY + 22
        val layer = selectedLayerOrNull()
        addRenderableWidget(Button.builder(Component.literal("Shape: ${layer?.shapeType ?: "-"}")) { cycleShape() }
            .bounds(x + 6, typeY, 166, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Particle: ${layer?.particleType ?: "-"}")) { cycleParticle() }
            .bounds(x + 178, typeY, 176, 20).build())

        val categoryY = if (layer?.shapeType == VfxEditor2Shape.RIBBON) {
            addRenderableWidget(Button.builder(Component.literal("Width: ${layer.shapeParameters.widthCurve}")) { cycleWidthCurve() }
                .bounds(x + 6, typeY + 23, 166, 19).build())
            addRenderableWidget(Button.builder(Component.literal(if (layer.shapeParameters.reverse) "Direction: Reverse" else "Direction: Forward")) { toggleReverse() }
                .bounds(x + 178, typeY + 23, 176, 19).build())
            typeY + 46
        } else {
            typeY + 24
        }
        Category.entries.forEachIndexed { index, value ->
            addRenderableWidget(Button.builder(Component.literal(value.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }))
                { category = value; rebuildWidgets() }.bounds(x + 6 + index * 88, categoryY, 84, 19).build())
        }
        buildParameterFields(categoryY + 24)
    }

    private fun buildParameterFields(startY: Int) {
        fields.clear()
        val specs = fieldSpecs()
        specs.forEachIndexed { index, spec ->
            val column = index % 2
            val row = index / 2
            val field = EditBox(font, panelX + 82 + column * 174, startY + row * 22, 88, 18, Component.literal(spec.label))
            field.setMaxLength(16)
            field.setValue(formatValue(spec))
            field.setResponder { value -> if (!updatingFields) updateField(spec, value) }
            fields[spec.key] = addRenderableWidget(field)
        }
    }

    override fun rebuildWidgets() {
        clearWidgets()
        fields.clear()
        init()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        val x = panelX
        graphics.fill(x, 0, width, height, 0xD9101824.toInt())
        graphics.fill(x, 0, x + 2, height, 0xFF4DB6AC.toInt())
        graphics.text(font, "VFX Editor 2", x + 8, 5, 0xFFE8F3FF.toInt(), true)
        graphics.text(font, "VFX ID / Draft", x + 8, 22, 0xFF9BB4CE.toInt(), false)
        graphics.text(font, "ticks", x + 256, 22, 0xFF9BB4CE.toInt(), false)
        graphics.text(font, "Layers", x + 8, 82, 0xFFE8F3FF.toInt(), true)
        val layerActionsY = 92 + visibleLayerRows() * 20 + 2
        graphics.text(font, "Selected: ${selectedLayerOrNull()?.name ?: "none"}", x + 8, layerActionsY + 44, 0xFF9BB4CE.toInt(), false)
        graphics.text(font, "Estimated particles: ${estimateParticles()}", x + 8, layerActionsY + 62, 0xFF8EA9C5.toInt(), false)
        drawTimeline(graphics)
        if (notice.isNotBlank()) graphics.text(font, notice.take(48), x + 8, height - 24, 0xFFFFD166.toInt(), false)
        if (draftNames.isNotEmpty()) graphics.text(font, "Drafts: ${draftNames.take(3).joinToString()}", x + 8, height - 40, 0xFF8EA9C5.toInt(), false)
    }

    override fun tick() {
        super.tick()
        if (previewDebounce > 0) {
            previewDebounce--
            if (previewDebounce == 0 && autoPreview) sendPreview()
        }
    }

    override fun onClose() {
        sendMessage(VfxEditor2PreviewCancel)
        super.onClose()
    }

    fun setDraftNames(names: List<String>) { draftNames = names }

    fun applyDraft(value: VfxEditor2Composition) {
        composition = value
        selectedLayerIndex = 0
        layerPage = 0
        notice = "Loaded ${value.name}"
        rebuildWidgets()
        replay()
    }

    fun setNotice(value: String) { notice = value }

    private fun selectedLayerOrNull(): VfxEditor2Layer? = composition.layers.getOrNull(selectedLayerIndex)

    private fun visibleLayerRows(): Int = 5.coerceAtMost(composition.layers.size.coerceAtLeast(1))

    private fun maxLayerPage(): Int = ((composition.layers.size - 1).coerceAtLeast(0) / visibleLayerRows())

    private fun layerLabel(layer: VfxEditor2Layer, index: Int): String = "${index + 1}. ${layer.name.take(20)}"

    private fun fieldSpecs(): List<FieldSpec> {
        val layer = selectedLayerOrNull() ?: return emptyList()
        val specs = when (category) {
            Category.TRANSFORM -> listOf(
                FieldSpec("offsetForward", "Forward"), FieldSpec("offsetRight", "Right"),
                FieldSpec("offsetUp", "Up"), FieldSpec("yaw", "Yaw", 0),
                FieldSpec("pitch", "Pitch", 0), FieldSpec("roll", "Roll", 0),
            )
            Category.APPEARANCE -> listOf(
                FieldSpec("layerName", "Name"), FieldSpec("color", "#RRGGBB"), FieldSpec("size", "Size"),
                FieldSpec("density", "Density"),
            )
            Category.GEOMETRY -> when (layer.shapeType) {
                VfxEditor2Shape.RIBBON -> listOf(
                    FieldSpec("length", "Length"), FieldSpec("arcSpan", "Arc", 0),
                    FieldSpec("curvature", "Curve"), FieldSpec("width", "Width"),
                    FieldSpec("sampleDensity", "Samples", 0), FieldSpec("laneCount", "Lanes", 0, true),
                    FieldSpec("laneSpacing", "Lane gap"),
                )
                VfxEditor2Shape.LINE -> listOf(FieldSpec("lineLength", "Length"), FieldSpec("lineSpacing", "Spacing"))
                VfxEditor2Shape.CIRCLE -> listOf(
                    FieldSpec("circleRadius", "Radius"), FieldSpec("circleArcDegrees", "Arc", 0), FieldSpec("circleSpacing", "Spacing"),
                )
                VfxEditor2Shape.BURST -> listOf(
                    FieldSpec("burstRadius", "Radius"), FieldSpec("burstCount", "Count", 0, true),
                    FieldSpec("burstSpread", "Spread"), FieldSpec("burstSpeed", "Speed"),
                )
            }
            Category.TIMING -> listOf(FieldSpec("startTick", "Start", 0, true), FieldSpec("layerDuration", "Duration", 0, true))
        }
        return specs
    }

    private fun formatValue(spec: FieldSpec): String {
        val layer = selectedLayerOrNull() ?: return ""
        if (spec.key == "layerName") return layer.name
        val value = when (spec.key) {
            "offsetForward" -> layer.offset.forward
            "offsetRight" -> layer.offset.right
            "offsetUp" -> layer.offset.up
            "yaw" -> layer.rotation.yaw
            "pitch" -> layer.rotation.pitch
            "roll" -> layer.rotation.roll
            "color" -> return "#%06x".format(Locale.ROOT, layer.color)
            "size" -> layer.size
            "density" -> layer.density
            "length" -> layer.shapeParameters.length
            "arcSpan" -> layer.shapeParameters.arcSpan
            "curvature" -> layer.shapeParameters.curvature
            "width" -> layer.shapeParameters.width
            "sampleDensity" -> layer.shapeParameters.sampleDensity
            "laneCount" -> layer.shapeParameters.laneCount.toDouble()
            "laneSpacing" -> layer.shapeParameters.laneSpacing
            "lineLength" -> layer.shapeParameters.lineLength
            "lineSpacing" -> layer.shapeParameters.lineSpacing
            "circleRadius" -> layer.shapeParameters.circleRadius
            "circleArcDegrees" -> layer.shapeParameters.circleArcDegrees
            "circleSpacing" -> layer.shapeParameters.circleSpacing
            "burstRadius" -> layer.shapeParameters.burstRadius
            "burstCount" -> layer.shapeParameters.burstCount.toDouble()
            "burstSpread" -> layer.shapeParameters.burstSpread
            "burstSpeed" -> layer.shapeParameters.burstSpeed
            "startTick" -> layer.startTick.toDouble()
            "layerDuration" -> layer.durationTicks.toDouble()
            else -> 0.0
        }
        return if (spec.integer) value.roundToInt().toString() else "%1$.${spec.decimals}f".format(Locale.ROOT, value)
    }

    private fun updateField(spec: FieldSpec, raw: String) {
        val layer = selectedLayerOrNull() ?: return
        if (spec.key == "layerName") {
            if (raw.matches(Regex("[A-Za-z0-9][A-Za-z0-9 _-]{0,31}"))) {
                composition = composition.copy(layers = composition.layers.toMutableList().also { it[selectedLayerIndex] = layer.copy(name = raw) })
                previewDebounce = 5
            }
            return
        }
        val parsed = if (spec.key == "color") raw.removePrefix("#").toIntOrNull(16)?.toDouble() else raw.toDoubleOrNull()
        if (parsed == null || !parsed.isFinite()) return
        val next = when (spec.key) {
            "offsetForward" -> layer.copy(offset = VfxEditor2Offset.clamped(parsed, layer.offset.right, layer.offset.up))
            "offsetRight" -> layer.copy(offset = VfxEditor2Offset.clamped(layer.offset.forward, parsed, layer.offset.up))
            "offsetUp" -> layer.copy(offset = VfxEditor2Offset.clamped(layer.offset.forward, layer.offset.right, parsed))
            "yaw" -> layer.copy(rotation = VfxEditor2Rotation.clamped(parsed, layer.rotation.pitch, layer.rotation.roll))
            "pitch" -> layer.copy(rotation = VfxEditor2Rotation.clamped(layer.rotation.yaw, parsed, layer.rotation.roll))
            "roll" -> layer.copy(rotation = VfxEditor2Rotation.clamped(layer.rotation.yaw, layer.rotation.pitch, parsed))
            "color" -> layer.copy(color = parsed.toInt().coerceIn(0, 0xffffff))
            "size" -> layer.copy(size = parsed.coerceIn(0.01, 2.0))
            "density" -> layer.copy(density = parsed.coerceIn(0.05, 32.0))
            else -> layer.copy(shapeParameters = updateShape(layer.shapeParameters, spec.key, parsed), startTick = if (spec.key == "startTick") parsed.roundToInt().coerceIn(0, 200) else layer.startTick, durationTicks = if (spec.key == "layerDuration") parsed.roundToInt().coerceIn(1, 200) else layer.durationTicks)
        }
        updateSelectedLayer(next)
        previewDebounce = 5
    }

    private fun updateShape(shape: VfxEditor2ShapeParameters, key: String, value: Double): VfxEditor2ShapeParameters = when (key) {
        "length" -> shape.copy(length = value.coerceIn(0.1, 16.0))
        "arcSpan" -> shape.copy(arcSpan = value.coerceIn(1.0, 360.0))
        "curvature" -> shape.copy(curvature = value.coerceIn(0.0, 4.0))
        "width" -> shape.copy(width = value.coerceIn(0.0, 2.0))
        "sampleDensity" -> shape.copy(sampleDensity = value.coerceIn(1.0, 32.0))
        "laneCount" -> shape.copy(laneCount = value.roundToInt().coerceIn(1, 4))
        "laneSpacing" -> shape.copy(laneSpacing = value.coerceIn(0.0, 3.0))
        "lineLength" -> shape.copy(lineLength = value.coerceIn(0.1, 16.0))
        "lineSpacing" -> shape.copy(lineSpacing = value.coerceIn(0.05, 3.0))
        "circleRadius" -> shape.copy(circleRadius = value.coerceIn(0.0, 12.0))
        "circleArcDegrees" -> shape.copy(circleArcDegrees = value.coerceIn(1.0, 360.0))
        "circleSpacing" -> shape.copy(circleSpacing = value.coerceIn(0.05, 3.0))
        "burstRadius" -> shape.copy(burstRadius = value.coerceIn(0.0, 8.0))
        "burstCount" -> shape.copy(burstCount = value.roundToInt().coerceIn(0, 64))
        "burstSpread" -> shape.copy(burstSpread = value.coerceIn(0.0, 8.0))
        "burstSpeed" -> shape.copy(burstSpeed = value.coerceIn(0.0, 2.0))
        else -> shape
    }

    private fun updateSelectedLayer(value: VfxEditor2Layer) {
        if (selectedLayerIndex !in composition.layers.indices) return
        composition = composition.copy(layers = composition.layers.toMutableList().also { it[selectedLayerIndex] = value })
        syncingFields { fields.forEach { (key, field) -> field.setValue(formatValue(fieldSpecs().first { it.key == key })) } }
    }

    private fun syncingFields(block: () -> Unit) {
        updatingFields = true
        block()
        updatingFields = false
    }

    private fun toggleEnabled(index: Int) {
        composition = composition.copy(layers = composition.layers.mapIndexed { i, layer -> if (i == index) layer.copy(enabled = !layer.enabled) else layer })
        previewDebounce = 5
    }

    private fun toggleSolo(index: Int) {
        composition = composition.copy(layers = composition.layers.mapIndexed { i, layer -> if (i == index) layer.copy(solo = !layer.solo) else layer })
        previewDebounce = 5
    }

    private fun addLayer() {
        if (composition.layers.size >= 16) {
            notice = "Layer limit reached (16)"
            return
        }
        val id = (composition.layers.maxOfOrNull { it.id } ?: 0) + 1
        val layer = VfxEditor2Layer(id = id, name = "Layer $id")
        val index = (selectedLayerIndex + 1).coerceAtMost(composition.layers.size)
        composition = composition.copy(layers = composition.layers.toMutableList().also { it.add(index, layer) })
        selectedLayerIndex = index
        layerPage = selectedLayerIndex / visibleLayerRows()
        rebuildWidgets()
        replay()
    }

    private fun duplicateLayer() {
        if (composition.layers.size >= 16) {
            notice = "Layer limit reached (16)"
            return
        }
        val source = selectedLayerOrNull() ?: return
        val id = (composition.layers.maxOfOrNull { it.id } ?: 0) + 1
        val copy = source.copy(id = id, name = "${source.name.take(26)} Copy")
        val index = (selectedLayerIndex + 1).coerceAtMost(composition.layers.size)
        composition = composition.copy(layers = composition.layers.toMutableList().also { it.add(index, copy) })
        selectedLayerIndex = index
        layerPage = selectedLayerIndex / visibleLayerRows()
        rebuildWidgets()
        replay()
    }

    private fun deleteLayer() {
        if (composition.layers.isEmpty()) return
        composition = composition.copy(layers = composition.layers.toMutableList().also { it.removeAt(selectedLayerIndex) })
        selectedLayerIndex = selectedLayerIndex.coerceIn(0, (composition.layers.lastIndex).coerceAtLeast(0))
        layerPage = layerPage.coerceAtMost(maxLayerPage())
        rebuildWidgets()
        replay()
    }

    private fun moveLayer(delta: Int) {
        val target = selectedLayerIndex + delta
        if (target !in composition.layers.indices) return
        val layers = composition.layers.toMutableList()
        val moved = layers.removeAt(selectedLayerIndex)
        layers.add(target, moved)
        composition = composition.copy(layers = layers)
        selectedLayerIndex = target
        rebuildWidgets()
        replay()
    }

    private fun cycleShape() {
        val layer = selectedLayerOrNull() ?: return
        val next = VfxEditor2Shape.entries[(layer.shapeType.ordinal + 1) % VfxEditor2Shape.entries.size]
        composition = composition.copy(layers = composition.layers.toMutableList().also { it[selectedLayerIndex] = layer.copy(shapeType = next) })
        rebuildWidgets()
        replay()
    }

    private fun cycleParticle() {
        val layer = selectedLayerOrNull() ?: return
        val next = VfxEditor2Particle.entries[(layer.particleType.ordinal + 1) % VfxEditor2Particle.entries.size]
        updateSelectedLayer(layer.copy(particleType = next))
        rebuildWidgets()
        replay()
    }

    private fun cycleWidthCurve() {
        val layer = selectedLayerOrNull() ?: return
        val next = when (layer.shapeParameters.widthCurve) {
            VfxEditor2WidthCurve.CONSTANT -> VfxEditor2WidthCurve.THIN_THICK_THIN
            VfxEditor2WidthCurve.THIN_THICK_THIN -> VfxEditor2WidthCurve.CONSTANT
        }
        composition = composition.copy(layers = composition.layers.toMutableList().also {
            it[selectedLayerIndex] = layer.copy(shapeParameters = layer.shapeParameters.copy(widthCurve = next))
        })
        rebuildWidgets()
        replay()
    }

    private fun toggleReverse() {
        val layer = selectedLayerOrNull() ?: return
        composition = composition.copy(layers = composition.layers.toMutableList().also {
            it[selectedLayerIndex] = layer.copy(shapeParameters = layer.shapeParameters.copy(reverse = !layer.shapeParameters.reverse))
        })
        rebuildWidgets()
        replay()
    }

    private fun replay() {
        previewDebounce = 0
        sendPreview()
    }

    private fun sendPreview() {
        sendMessage(VfxEditor2PreviewRequest(++requestId, composition, loop))
    }

    private fun save() {
        val name = draftName.value.trim()
        if (!name.matches(Regex("[A-Za-z0-9][A-Za-z0-9 _-]{0,31}"))) {
            notice = "Draft name must be ASCII"
            return
        }
        composition = composition.copy(name = name)
        sendMessage(VfxEditor2SaveRequest(composition))
    }

    private fun load() {
        val name = draftName.value.trim()
        if (name.isNotEmpty()) sendMessage(VfxEditor2LoadRequest(name))
    }

    private fun applyRoninQ() {
        sendMessage(VfxEditor2ApplyRequest("ronin.q", composition))
    }

    private fun reset() {
        composition = VfxEditor2Composition()
        selectedLayerIndex = 0
        layerPage = 0
        notice = "Reset"
        rebuildWidgets()
        replay()
    }

    private fun loopLabel() = if (loop) "Loop: ON" else "Loop: OFF"
    private fun autoLabel() = if (autoPreview) "Auto: ON" else "Auto: OFF"

    private fun estimateParticles(): Int = composition.layers.filter { it.enabled }.let { active ->
        val solo = active.filter { it.solo }
        (if (solo.isEmpty()) active else solo).sumOf { layer ->
            val shape = layer.shapeParameters
            when (layer.shapeType) {
                VfxEditor2Shape.RIBBON -> (shape.sampleDensity * shape.laneCount * layer.density).toInt()
                VfxEditor2Shape.LINE -> (shape.lineLength / shape.lineSpacing * layer.density).toInt()
                VfxEditor2Shape.CIRCLE -> (shape.circleRadius * Math.toRadians(shape.circleArcDegrees) / shape.circleSpacing * layer.density).toInt()
                VfxEditor2Shape.BURST -> (shape.burstCount * layer.density).toInt()
            }
        }.coerceAtMost(4096)
    }

    private fun drawTimeline(graphics: GuiGraphicsExtractor) {
        val x = panelX + 8
        val y = height - 112
        graphics.text(font, "Timeline  0        ${composition.durationTicks}", x, y, 0xFF9BB4CE.toInt(), false)
        val width = panelWidth - 18
        composition.layers.take(4).forEachIndexed { index, layer ->
            val rowY = y + 14 + index * 12
            graphics.text(font, layer.name.take(8), x, rowY, 0xFFD5E2F0.toInt(), false)
            val barX = x + 66
            val barWidth = (width - 66).coerceAtLeast(1)
            val start = (barWidth * layer.startTick / composition.durationTicks.coerceAtLeast(1)).coerceIn(0, barWidth)
            val length = (barWidth * layer.durationTicks / composition.durationTicks.coerceAtLeast(1)).coerceAtLeast(2).coerceAtMost(barWidth - start)
            graphics.fill(barX + start, rowY - 1, barX + start + length, rowY + 8, if (layer.enabled) 0xFF4DB6AC.toInt() else 0xFF4A5568.toInt())
        }
    }
}
