package dev.projects.client

import dev.projects.protocol.ProtocolMessage
import dev.projects.protocol.VfxEditor2Appearance
import dev.projects.protocol.VfxEditor2Composition
import dev.projects.protocol.VfxEditor2Effect
import dev.projects.protocol.VfxEditor2EffectType
import dev.projects.protocol.VfxEditor2PreviewStart
import dev.projects.protocol.VfxEditor2PreviewStop
import dev.projects.protocol.VfxEditor2Shape
import dev.projects.protocol.VfxEditor2Status
import dev.projects.protocol.VfxEditor2StatusKind
import dev.projects.protocol.defaultVfxEditor2Composition
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

/** World-preserving Checkpoint B workbench for authoring a small VFX composition. */
class VfxEditor2Screen(
    private val targetLabel: String,
    initialComposition: VfxEditor2Composition,
    private val sendMessage: (ProtocolMessage) -> Unit,
) : Screen(Component.literal("VFX Workbench")) {
    constructor(
        targetLabel: String,
        sendMessage: (ProtocolMessage) -> Unit,
    ) : this(targetLabel, defaultVfxEditor2Composition(), sendMessage)

    private companion object {
        const val LEFT_PANEL_WIDTH = 190
        const val RIGHT_PANEL_WIDTH = 330
        const val BOTTOM_BAR_HEIGHT = 36
        const val EFFECT_ROW_HEIGHT = 22
        const val MAX_EFFECTS = 8
        const val MAX_NAME_LENGTH = 32
        const val PREVIEW_DEBOUNCE_TICKS = 4
    }

    private enum class ParameterKey {
        LENGTH,
        ARC,
        CURVATURE,
        THICKNESS,
        RADIUS,
        COUNT,
        SPREAD,
        SPEED,
        FORWARD,
        SIDE,
        HEIGHT,
        YAW,
        PITCH,
        ROLL,
        PARTICLE_SIZE,
        DENSITY,
    }

    private data class ControlSpec(
        val key: ParameterKey,
        val section: String,
        val label: String,
        val min: Double,
        val max: Double,
        val decimals: Int,
        val read: (VfxEditor2Effect) -> Double,
        val write: (VfxEditor2Effect, Double) -> VfxEditor2Effect,
    )

    private data class PlacedControl(val spec: ControlSpec, val y: Int)

    private val initialState = initialComposition
    private var composition = initialState
    private var selectedEffectId: Long? = initialState.effects.firstOrNull()?.id
    private var nextEffectId = (initialState.effects.maxOfOrNull { it.id } ?: 0L) + 1L
    private var statusText = "Ready"
    private var previewActive = false
    private var previewDebounceTicks = 0
    private var requestId = 0L
    private var addMenuOpen = false
    private var syncingColorField = false
    private var placedControls: List<PlacedControl> = emptyList()
    private var sectionLabels: List<Pair<String, Int>> = emptyList()
    private var colorFieldY = 0
    private var presetY = 0
    private val sliders = linkedMapOf<ParameterKey, WorkbenchSlider>()
    private var nameField: EditBox? = null
    private var colorField: EditBox? = null

    private val rightPanelX: Int
        get() = (width - RIGHT_PANEL_WIDTH).coerceAtLeast(LEFT_PANEL_WIDTH + 40)

    private val bottomBarY: Int
        get() = (height - BOTTOM_BAR_HEIGHT).coerceAtLeast(0)

    override fun isPauseScreen(): Boolean = false

    // Keep the live Minecraft world visible behind the workbench.
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) = Unit

    override fun init() {
        rebuildEditorWidgets()
    }

    override fun rebuildWidgets() {
        rebuildEditorWidgets()
    }

    private fun rebuildEditorWidgets() {
        clearWidgets()
        sliders.clear()
        nameField = null
        colorField = null
        placedControls = emptyList()
        sectionLabels = emptyList()

        val addY = (38 + composition.effects.size * EFFECT_ROW_HEIGHT).coerceAtMost(bottomBarY - 24)
        addRenderableWidget(
            Button.builder(Component.literal("+ Add Effect")) { addMenuOpen = !addMenuOpen; rebuildEditorWidgets() }
                .bounds(8, addY, LEFT_PANEL_WIDTH - 16, 20)
                .build(),
        )

        var actionY = addY + 24
        if (addMenuOpen) {
            listOf(
                VfxEditor2EffectType.ARC_SLASH to "Arc Slash",
                VfxEditor2EffectType.STRAIGHT_SLASH to "Straight Slash",
                VfxEditor2EffectType.RING to "Ring",
                VfxEditor2EffectType.BURST to "Burst",
            ).forEach { (type, label) ->
                addRenderableWidget(
                    Button.builder(Component.literal(label)) { addEffect(type) }
                        .bounds(8, actionY, LEFT_PANEL_WIDTH - 16, 18)
                        .build(),
                )
                actionY += 20
            }
            actionY += 2
        }
        if (selectedEffectId != null) {
            val labels = listOf("Duplicate", "Delete", "Up", "Down")
            val gap = 3
            val buttonWidth = (LEFT_PANEL_WIDTH - 16 - gap * (labels.size - 1)) / labels.size
            labels.forEachIndexed { index, label ->
                addRenderableWidget(
                    Button.builder(Component.literal(label)) {
                        when (index) {
                            0 -> duplicateSelected()
                            1 -> deleteSelected()
                            2 -> moveSelected(-1)
                            else -> moveSelected(1)
                        }
                    }.bounds(8 + index * (buttonWidth + gap), actionY, buttonWidth, 20).build(),
                )
            }
        }

        val centerButtonX = ((width - 164) / 2).coerceAtLeast(LEFT_PANEL_WIDTH + 4)
        addRenderableWidget(
            Button.builder(Component.literal("Play")) { play() }
                .bounds(centerButtonX, bottomBarY + 8, 78, 20)
                .build(),
        )
        addRenderableWidget(
            Button.builder(Component.literal("Stop")) { stop() }
                .bounds(centerButtonX + 84, bottomBarY + 8, 78, 20)
                .build(),
        )

        selectedEffect()?.let { effect ->
            val panelX = rightPanelX
            val field = EditBox(font, panelX + 102, 31, RIGHT_PANEL_WIDTH - 112, 18, Component.literal("Effect Name"))
            field.setMaxLength(MAX_NAME_LENGTH)
            field.setValue(effect.name)
            field.setResponder(::renameSelected)
            nameField = addRenderableWidget(field)

            var nextY = 66
            var previousSection = ""
            val controls = controlsFor(effect)
            val placed = mutableListOf<PlacedControl>()
            val labels = mutableListOf<Pair<String, Int>>()
            controls.forEach { control ->
                if (control.section != previousSection) {
                    if (previousSection.isNotEmpty()) nextY += 8
                    labels += control.section to nextY
                    nextY += 12
                    previousSection = control.section
                }
                val slider = WorkbenchSlider(
                    x = panelX + 112,
                    y = nextY,
                    width = RIGHT_PANEL_WIDTH - 122,
                    control = control,
                    value = control.read(effect),
                    changed = { value -> updateParameter(control, value) },
                )
                sliders[control.key] = addRenderableWidget(slider)
                placed += PlacedControl(control, nextY)
                nextY += 20
            }
            placedControls = placed
            sectionLabels = labels
            colorFieldY = nextY + 3
            graphicsSafeColorControls(panelX, effect)
        }
    }

    /** Adds the color controls after the numeric inspector rows have a stable y position. */
    private fun graphicsSafeColorControls(panelX: Int, effect: VfxEditor2Effect) {
        val field = EditBox(font, panelX + 102, colorFieldY, 92, 18, Component.literal("Color"))
        field.setMaxLength(7)
        field.setValue(formatColor(effect.appearance.color))
        field.setResponder { value -> if (!syncingColorField) updateColorFromInput(value) }
        colorField = addRenderableWidget(field)
        presetY = colorFieldY + 21
        val presets = listOf(
            "White" to 0xffffff,
            "Red" to 0xff3344,
            "Dark Red" to 0x8f1728,
            "Blue" to 0x367bff,
            "Cyan" to 0x30d8d0,
            "Purple" to 0xb14dff,
            "Gold" to 0xffc247,
        )
        val gap = 2
        val buttonWidth = ((RIGHT_PANEL_WIDTH - 20 - gap * (presets.size - 1)) / presets.size).coerceAtLeast(28)
        presets.forEachIndexed { index, (label, color) ->
            addRenderableWidget(
                Button.builder(Component.literal(label)) { setColor(color) }
                    .bounds(panelX + 10 + index * (buttonWidth + gap), presetY, buttonWidth, 18)
                    .build(),
            )
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        graphics.fill(0, 0, LEFT_PANEL_WIDTH, bottomBarY, 0xC8101824.toInt())
        graphics.fill(rightPanelX, 0, width, bottomBarY, 0xC8101824.toInt())
        graphics.fill(0, bottomBarY, width, height, 0xDD0B1018.toInt())
        graphics.fill(LEFT_PANEL_WIDTH - 2, 0, LEFT_PANEL_WIDTH, bottomBarY, 0xFF31485F.toInt())
        graphics.fill(rightPanelX, 0, rightPanelX + 2, bottomBarY, 0xFFCF3348.toInt())

        graphics.text(font, "Effects", 12, 10, 0xFFE8F3FF.toInt(), true)
        graphics.text(font, "${composition.effects.size}/$MAX_EFFECTS", LEFT_PANEL_WIDTH - 32, 11, 0xFF8EA9C5.toInt(), false)
        composition.effects.forEachIndexed { index, effect ->
            val rowY = 32 + index * EFFECT_ROW_HEIGHT
            if (effect.id == selectedEffectId) {
                graphics.fill(5, rowY - 2, LEFT_PANEL_WIDTH - 5, rowY + EFFECT_ROW_HEIGHT - 2, 0xFF29445D.toInt())
            }
            graphics.text(font, if (effect.enabled) "[O]" else "[ ]", 9, rowY + 4, 0xFFD5E2F0.toInt(), false)
            graphics.text(font, if (effect.solo) "[S]" else "[ ]", 39, rowY + 4, if (effect.solo) 0xFFFFD166.toInt() else 0xFF8EA9C5.toInt(), false)
            graphics.text(font, effect.name.take(18), 69, rowY + 4, 0xFFE8F3FF.toInt(), effect.id == selectedEffectId)
        }

        graphics.text(font, "Inspector", rightPanelX + 12, 8, 0xFFE8F3FF.toInt(), true)
        selectedEffect()?.let { effect ->
            graphics.text(font, "Editing: ${effect.name.take(24)}", rightPanelX + 12, 20, 0xFFD5E2F0.toInt(), false)
            graphics.text(font, displayName(effect.type), rightPanelX + 12, 52, 0xFF9BB4CE.toInt(), false)
            sectionLabels.forEach { (label, y) ->
                graphics.text(font, label, rightPanelX + 10, y, 0xFFCF7990.toInt(), true)
            }
            graphics.text(font, "Color", rightPanelX + 10, colorFieldY + 5, 0xFFD5E2F0.toInt(), false)
            val color = effect.appearance.color or 0xff000000.toInt()
            graphics.fill(rightPanelX + 80, colorFieldY + 2, rightPanelX + 98, colorFieldY + 20, color)
            graphics.fill(rightPanelX + 80, colorFieldY + 2, rightPanelX + 98, colorFieldY + 3, 0xffffffff.toInt())
        } ?: run {
            graphics.text(font, "Select an Effect", rightPanelX + 12, 36, 0xFF9BB4CE.toInt(), false)
            graphics.text(font, "Use + Add Effect to begin", rightPanelX + 12, 54, 0xFF8EA9C5.toInt(), false)
        }
        graphics.text(font, "Status: $statusText", rightPanelX + 12, bottomBarY + 13, statusColor(), false)
        graphics.text(font, targetLabel.take(24), LEFT_PANEL_WIDTH + 8, bottomBarY + 13, 0xFF8EA9C5.toInt(), false)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        if (event.button() == 0 && mouseX in 0.0..LEFT_PANEL_WIDTH.toDouble()) {
            composition.effects.forEachIndexed { index, effect ->
                val rowY = 32 + index * EFFECT_ROW_HEIGHT
                if (mouseY in rowY.toDouble()..(rowY + EFFECT_ROW_HEIGHT).toDouble()) {
                    when {
                        mouseX < 36.0 -> toggleEnabled(effect.id)
                        mouseX < 66.0 -> toggleSolo(effect.id)
                        else -> selectEffect(effect.id)
                    }
                    return true
                }
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun tick() {
        super.tick()
        if (previewDebounceTicks > 0) {
            previewDebounceTicks--
            if (previewDebounceTicks == 0) sendPreview()
        }
    }

    fun setStatus(status: VfxEditor2Status) {
        statusText = when (status.kind) {
            VfxEditor2StatusKind.READY -> "Ready"
            VfxEditor2StatusKind.PREVIEW_REQUESTED,
            VfxEditor2StatusKind.PLAYING,
            -> "Previewing"
            VfxEditor2StatusKind.STOPPED -> "Stopped"
            VfxEditor2StatusKind.ERROR -> "Invalid value"
        }
        previewActive = status.kind == VfxEditor2StatusKind.PREVIEW_REQUESTED ||
            status.kind == VfxEditor2StatusKind.PLAYING
    }

    override fun onClose() {
        previewDebounceTicks = 0
        previewActive = false
        sendMessage(VfxEditor2PreviewStop)
        super.onClose()
    }

    private fun selectEffect(id: Long) {
        if (selectedEffectId == id) return
        selectedEffectId = id
        addMenuOpen = false
        rebuildEditorWidgets()
    }

    private fun toggleEnabled(id: Long) {
        updateEffect(id) { it.copy(enabled = !it.enabled) }
        schedulePreview()
    }

    private fun toggleSolo(id: Long) {
        updateEffect(id) { it.copy(solo = !it.solo) }
        schedulePreview()
    }

    private fun addEffect(type: VfxEditor2EffectType) {
        if (composition.effects.size >= MAX_EFFECTS) {
            statusText = "Invalid value"
            addMenuOpen = false
            rebuildEditorWidgets()
            return
        }
        val effect = defaultEffect(type, allocateEffectId())
        composition = composition.add(effect) ?: return
        selectedEffectId = effect.id
        addMenuOpen = false
        rebuildEditorWidgets()
        schedulePreview()
    }

    private fun duplicateSelected() {
        val selected = selectedEffect() ?: return
        if (composition.effects.size >= MAX_EFFECTS) {
            statusText = "Invalid value"
            return
        }
        val duplicateId = allocateEffectId()
        val duplicateName = "${selected.name} Copy".take(MAX_NAME_LENGTH)
        composition = composition.duplicate(selected.id, duplicateId, duplicateName) ?: return
        selectedEffectId = duplicateId
        rebuildEditorWidgets()
        schedulePreview()
    }

    private fun deleteSelected() {
        val selected = selectedEffect() ?: return
        val oldIndex = composition.effects.indexOfFirst { it.id == selected.id }
        val effects = composition.effects.filterNot { it.id == selected.id }
        composition = composition.remove(selected.id)
        selectedEffectId = effects.getOrNull(oldIndex.coerceIn(0, effects.lastIndex.coerceAtLeast(0)))?.id
            ?: effects.lastOrNull()?.id
        rebuildEditorWidgets()
        schedulePreview()
    }

    private fun moveSelected(delta: Int) {
        val selected = selectedEffect() ?: return
        val from = composition.effects.indexOfFirst { it.id == selected.id }
        val to = (from + delta).coerceIn(0, composition.effects.lastIndex)
        if (from == to) return
        val effects = composition.effects.toMutableList()
        val moved = effects.removeAt(from)
        effects.add(to, moved)
        composition = composition.copy(effects = effects)
        rebuildEditorWidgets()
        schedulePreview()
    }

    private fun updateParameter(control: ControlSpec, value: Double) {
        val clamped = value.coerceIn(control.min, control.max).let { if (control.key == ParameterKey.COUNT) it.roundToInt().toDouble() else it }
        updateSelectedEffect(transform = { control.write(it, clamped) })
        statusText = "Previewing"
        schedulePreview()
    }

    private fun renameSelected(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            statusText = "Invalid value"
            return
        }
        updateSelectedEffect(transform = { it.copy(name = trimmed.take(MAX_NAME_LENGTH)) }, preview = false)
    }

    private fun updateColorFromInput(value: String) {
        val raw = value.removePrefix("#")
        val parsed = raw.takeIf { it.length == 6 && it.all { char -> char.isDigit() || char.lowercaseChar() in 'a'..'f' } }
            ?.toIntOrNull(16)
        if (parsed == null) {
            statusText = "Invalid value"
            return
        }
        setColor(parsed)
    }

    private fun setColor(color: Int) {
        updateSelectedEffect(transform = { it.copy(appearance = it.appearance.copy(color = color.coerceIn(0, 0xffffff))) })
        syncColorField()
        schedulePreview()
    }

    private fun play() {
        previewDebounceTicks = 0
        sendPreview()
    }

    private fun stop() {
        previewDebounceTicks = 0
        previewActive = false
        statusText = "Stopped"
        sendMessage(VfxEditor2PreviewStop)
    }

    private fun schedulePreview() {
        previewDebounceTicks = PREVIEW_DEBOUNCE_TICKS
        statusText = "Previewing"
    }

    private fun sendPreview() {
        requestId++
        previewActive = true
        statusText = "Previewing"
        sendMessage(VfxEditor2PreviewStart(requestId, composition))
    }

    private fun updateEffect(id: Long, transform: (VfxEditor2Effect) -> VfxEditor2Effect) {
        composition = composition.update(id, transform)
        syncColorField()
    }

    private fun updateSelectedEffect(
        transform: (VfxEditor2Effect) -> VfxEditor2Effect,
        preview: Boolean = true,
    ) {
        selectedEffectId?.let { updateEffect(it, transform) }
        if (preview) syncColorField()
    }

    private fun syncColorField() {
        val effect = selectedEffect() ?: return
        val field = colorField ?: return
        syncingColorField = true
        field.setValue(formatColor(effect.appearance.color))
        syncingColorField = false
    }

    private fun selectedEffect(): VfxEditor2Effect? = selectedEffectId?.let { id -> composition.effects.firstOrNull { it.id == id } }

    private fun allocateEffectId(): Long {
        while (composition.effects.any { it.id == nextEffectId }) nextEffectId++
        return nextEffectId++
    }

    private fun defaultEffect(type: VfxEditor2EffectType, id: Long): VfxEditor2Effect = when (type) {
        VfxEditor2EffectType.ARC_SLASH -> VfxEditor2Effect(id, "Arc Slash", type, VfxEditor2Shape.ArcSlash())
        VfxEditor2EffectType.STRAIGHT_SLASH -> VfxEditor2Effect(id, "Straight Slash", type, VfxEditor2Shape.StraightSlash())
        VfxEditor2EffectType.RING -> VfxEditor2Effect(id, "Ring", type, VfxEditor2Shape.Ring())
        VfxEditor2EffectType.BURST -> VfxEditor2Effect(id, "Burst", type, VfxEditor2Shape.Burst())
    }

    private fun controlsFor(effect: VfxEditor2Effect): List<ControlSpec> {
        fun shapeControl(
            key: ParameterKey,
            section: String,
            label: String,
            min: Double,
            max: Double,
            decimals: Int,
            read: (VfxEditor2Shape) -> Double,
            write: (VfxEditor2Shape, Double) -> VfxEditor2Shape,
        ) = ControlSpec(key, section, label, min, max, decimals, { read(it.shape) }) { current, value ->
            current.copy(shape = write(current.shape, value))
        }

        fun transformControl(
            key: ParameterKey,
            section: String,
            label: String,
            min: Double,
            max: Double,
            decimals: Int,
            read: (dev.projects.protocol.VfxEditor2Transform) -> Double,
            write: (dev.projects.protocol.VfxEditor2Transform, Double) -> dev.projects.protocol.VfxEditor2Transform,
        ) = ControlSpec(key, section, label, min, max, decimals, { read(it.transform) }) { current, value ->
            current.copy(transform = write(current.transform, value))
        }

        fun appearanceControl(
            key: ParameterKey,
            label: String,
            min: Double,
            max: Double,
            decimals: Int,
            read: (VfxEditor2Appearance) -> Double,
            write: (VfxEditor2Appearance, Double) -> VfxEditor2Appearance,
        ) = ControlSpec(key, "Appearance", label, min, max, decimals, { read(it.appearance) }) { current, value ->
            current.copy(appearance = write(current.appearance, value))
        }

        val position = listOf(
            transformControl(ParameterKey.FORWARD, "Position", "Forward", -1.0, 8.0, 1, { it.forward }) { current, value -> current.copy(forward = value) },
            transformControl(ParameterKey.SIDE, "Position", "Side", -5.0, 5.0, 1, { it.side }) { current, value -> current.copy(side = value) },
            transformControl(ParameterKey.HEIGHT, "Position", "Height", -2.0, 5.0, 1, { it.height }) { current, value -> current.copy(height = value) },
        )
        val rotation = listOf(
            transformControl(ParameterKey.YAW, "Rotation", "Yaw", -180.0, 180.0, 0, { it.yaw }) { current, value -> current.copy(yaw = value) },
            transformControl(ParameterKey.PITCH, "Rotation", "Pitch", -180.0, 180.0, 0, { it.pitch }) { current, value -> current.copy(pitch = value) },
            transformControl(ParameterKey.ROLL, "Rotation", "Roll", -180.0, 180.0, 0, { it.roll }) { current, value -> current.copy(roll = value) },
        )
        val density = appearanceControl(ParameterKey.DENSITY, "Density", 0.25, 4.0, 2, { it.density }) { current, value -> current.copy(density = value) }
        val particleSize = appearanceControl(ParameterKey.PARTICLE_SIZE, "Particle Size", 0.05, 1.5, 2, { it.particleSize }) { current, value -> current.copy(particleSize = value) }

        return when (effect.type) {
            VfxEditor2EffectType.ARC_SLASH -> listOf(
                shapeControl(ParameterKey.LENGTH, "Shape", "Length", 0.5, 10.0, 1, { (it as VfxEditor2Shape.ArcSlash).length }) { shape, value -> (shape as VfxEditor2Shape.ArcSlash).copy(length = value) },
                shapeControl(ParameterKey.ARC, "Shape", "Arc", 10.0, 300.0, 0, { (it as VfxEditor2Shape.ArcSlash).arcDegrees }) { shape, value -> (shape as VfxEditor2Shape.ArcSlash).copy(arcDegrees = value) },
                shapeControl(ParameterKey.CURVATURE, "Shape", "Curvature", 0.0, 2.0, 2, { (it as VfxEditor2Shape.ArcSlash).curvature }) { shape, value -> (shape as VfxEditor2Shape.ArcSlash).copy(curvature = value) },
                shapeControl(ParameterKey.THICKNESS, "Shape", "Thickness", 0.0, 1.5, 2, { (it as VfxEditor2Shape.ArcSlash).thickness }) { shape, value -> (shape as VfxEditor2Shape.ArcSlash).copy(thickness = value) },
            ) + position + rotation + listOf(particleSize, density)
            VfxEditor2EffectType.STRAIGHT_SLASH -> listOf(
                shapeControl(ParameterKey.LENGTH, "Shape", "Length", 0.5, 10.0, 1, { (it as VfxEditor2Shape.StraightSlash).length }) { shape, value -> (shape as VfxEditor2Shape.StraightSlash).copy(length = value) },
                shapeControl(ParameterKey.THICKNESS, "Shape", "Thickness", 0.0, 1.5, 2, { (it as VfxEditor2Shape.StraightSlash).thickness }) { shape, value -> (shape as VfxEditor2Shape.StraightSlash).copy(thickness = value) },
            ) + position + rotation + listOf(particleSize, density)
            VfxEditor2EffectType.RING -> listOf(
                shapeControl(ParameterKey.RADIUS, "Shape", "Radius", 0.0, 8.0, 1, { (it as VfxEditor2Shape.Ring).radius }) { shape, value -> (shape as VfxEditor2Shape.Ring).copy(radius = value) },
                shapeControl(ParameterKey.ARC, "Shape", "Arc", 10.0, 360.0, 0, { (it as VfxEditor2Shape.Ring).arcDegrees }) { shape, value -> (shape as VfxEditor2Shape.Ring).copy(arcDegrees = value) },
                shapeControl(ParameterKey.THICKNESS, "Shape", "Thickness", 0.0, 1.5, 2, { (it as VfxEditor2Shape.Ring).thickness }) { shape, value -> (shape as VfxEditor2Shape.Ring).copy(thickness = value) },
            ) + position + rotation + listOf(density)
            VfxEditor2EffectType.BURST -> listOf(
                shapeControl(ParameterKey.RADIUS, "Shape", "Radius", 0.0, 8.0, 1, { (it as VfxEditor2Shape.Burst).radius }) { shape, value -> (shape as VfxEditor2Shape.Burst).copy(radius = value) },
                shapeControl(ParameterKey.COUNT, "Shape", "Count", 1.0, 64.0, 0, { (it as VfxEditor2Shape.Burst).count.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.Burst).copy(count = value.roundToInt()) },
                shapeControl(ParameterKey.SPREAD, "Shape", "Spread", 0.0, 89.0, 0, { (it as VfxEditor2Shape.Burst).spread }) { shape, value -> (shape as VfxEditor2Shape.Burst).copy(spread = value) },
                shapeControl(ParameterKey.SPEED, "Shape", "Speed", 0.0, 3.0, 2, { (it as VfxEditor2Shape.Burst).speed }) { shape, value -> (shape as VfxEditor2Shape.Burst).copy(speed = value) },
            ) + position + listOf(particleSize)
        }
    }

    private fun displayName(type: VfxEditor2EffectType): String = when (type) {
        VfxEditor2EffectType.ARC_SLASH -> "Arc Slash"
        VfxEditor2EffectType.STRAIGHT_SLASH -> "Straight Slash"
        VfxEditor2EffectType.RING -> "Ring"
        VfxEditor2EffectType.BURST -> "Burst"
    }

    private fun formatColor(color: Int): String = "#%06x".format(color and 0xffffff)

    private fun statusColor(): Int = when {
        statusText == "Invalid value" -> 0xFFFF6B6B.toInt()
        previewActive || statusText == "Previewing" -> 0xFF8BE28B.toInt()
        else -> 0xFFFFD166.toInt()
    }

    private class WorkbenchSlider(
        x: Int,
        y: Int,
        width: Int,
        private val control: ControlSpec,
        value: Double,
        private val changed: (Double) -> Unit,
    ) : AbstractSliderButton(
        x,
        y,
        width,
        18,
        Component.literal(format(value, control.decimals)),
        normalized(value, control.min, control.max),
    ) {
        private var current = value

        override fun updateMessage() {
            message = Component.literal(format(current, control.decimals))
        }

        override fun applyValue() {
            current = control.min + value * (control.max - control.min)
            if (control.key == ParameterKey.COUNT) current = current.roundToInt().toDouble()
            current = current.coerceIn(control.min, control.max)
            changed(current)
            updateMessage()
        }

        fun setParameterValue(newValue: Double) {
            current = newValue.coerceIn(control.min, control.max)
            if (control.key == ParameterKey.COUNT) current = current.roundToInt().toDouble()
            setValue(normalized(current, control.min, control.max))
            updateMessage()
        }

        companion object {
            private fun normalized(value: Double, min: Double, max: Double): Double =
                ((value - min) / (max - min)).coerceIn(0.0, 1.0)

            private fun format(value: Double, decimals: Int): String = "%.${decimals}f".format(value)
        }
    }
}
