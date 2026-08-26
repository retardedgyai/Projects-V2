package dev.projects.client

import dev.projects.protocol.ProtocolMessage
import dev.projects.protocol.VfxEditor2Appearance
import dev.projects.protocol.VfxEditor2BoxMode
import dev.projects.protocol.VfxEditor2Composition
import dev.projects.protocol.VfxEditor2Direction
import dev.projects.protocol.VfxEditor2Effect
import dev.projects.protocol.VfxEditor2EffectType
import dev.projects.protocol.VfxEditor2PreviewStart
import dev.projects.protocol.VfxEditor2PreviewStop
import dev.projects.protocol.VfxEditor2Shape
import dev.projects.protocol.VfxEditor2Status
import dev.projects.protocol.VfxEditor2StatusKind
import dev.projects.protocol.VFX_EDITOR_2_MAX_EFFECTS
import dev.projects.protocol.defaultVfxEditor2Composition
import dev.projects.protocol.defaultVfxEditor2Effect
import dev.projects.protocol.vfxEditor2DisplayName
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** World-preserving Checkpoint B.1 workbench for authoring a small VFX composition. */
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
        const val MAX_EFFECTS = VFX_EDITOR_2_MAX_EFFECTS
        const val MAX_NAME_LENGTH = 32
        const val PREVIEW_DEBOUNCE_TICKS = 4
        const val ADD_ITEMS_PER_PAGE = 4
        const val INSPECTOR_CONTROLS_PER_PAGE = 7

        fun isIntegerParameter(key: ParameterKey): Boolean = key in setOf(
            ParameterKey.COUNT,
            ParameterKey.WAVES,
            ParameterKey.HOPS,
            ParameterKey.ROWS,
            ParameterKey.POINTS,
            ParameterKey.DURATION,
        )
    }

    private enum class ParameterKey {
        LENGTH,
        ARC,
        CURVATURE,
        THICKNESS,
        RADIUS,
        INNER_RADIUS,
        CONTROL_FORWARD,
        CONTROL_SIDE,
        CONTROL_HEIGHT,
        END_SIDE,
        END_HEIGHT,
        AMPLITUDE,
        WAVES,
        PHASE,
        JITTER,
        HOPS,
        SEED,
        TURNS,
        ANGLE_OFFSET,
        ANGLE,
        WIDTH,
        DEPTH,
        ROWS,
        SHAPE_HEIGHT,
        MAJOR_RADIUS,
        TUBE_RADIUS,
        POINTS,
        SHARPNESS,
        SIZE,
        START_RADIUS,
        END_RADIUS,
        DURATION,
        BOTTOM_RADIUS,
        TOP_RADIUS,
        VARIANCE,
        SPAWN_RADIUS,
        CONE_ANGLE,
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
        val unit: String,
        val tooltip: String,
        val read: (VfxEditor2Effect) -> Double,
        val write: (VfxEditor2Effect, Double) -> VfxEditor2Effect,
    )

    private data class PlacedControl(val spec: ControlSpec, val y: Int)

    private enum class ChoiceKey { REVERSE, DIRECTION, SHELL, MODE }

    private data class ChoiceSpec(
        val key: ChoiceKey,
        val section: String,
        val label: String,
        val values: List<String>,
        val read: (VfxEditor2Effect) -> Int,
        val write: (VfxEditor2Effect, Int) -> VfxEditor2Effect,
        val tooltip: String,
    )

    private data class PlacedChoice(val spec: ChoiceSpec, val y: Int)

    private enum class EffectCategory(val label: String, val types: List<VfxEditor2EffectType>) {
        SLASH_PATH(
            "SLASH / PATH",
            listOf(
                VfxEditor2EffectType.ARC_SLASH,
                VfxEditor2EffectType.STRAIGHT_SLASH,
                VfxEditor2EffectType.BEZIER,
                VfxEditor2EffectType.WAVE,
                VfxEditor2EffectType.LIGHTNING,
                VfxEditor2EffectType.SPIRAL,
                VfxEditor2EffectType.HELIX,
            ),
        ),
        AREA_2D(
            "AREA / 2D",
            listOf(
                VfxEditor2EffectType.RING,
                VfxEditor2EffectType.DISK,
                VfxEditor2EffectType.SECTOR,
                VfxEditor2EffectType.GRID,
            ),
        ),
        VOLUME_3D(
            "VOLUME / 3D",
            listOf(
                VfxEditor2EffectType.SPHERE,
                VfxEditor2EffectType.ORB,
                VfxEditor2EffectType.DOME,
                VfxEditor2EffectType.CYLINDER,
                VfxEditor2EffectType.CONE,
                VfxEditor2EffectType.BOX,
                VfxEditor2EffectType.TORUS,
            ),
        ),
        MOTION(
            "MOTION",
            listOf(
                VfxEditor2EffectType.SHOCKWAVE,
                VfxEditor2EffectType.VORTEX,
                VfxEditor2EffectType.TORNADO,
                VfxEditor2EffectType.FOUNTAIN,
            ),
        ),
        IMPACT_SPECIAL(
            "IMPACT / SPECIAL",
            listOf(
                VfxEditor2EffectType.BURST,
                VfxEditor2EffectType.SPHERE_BURST,
                VfxEditor2EffectType.CONE_BURST,
                VfxEditor2EffectType.STAR_FLOWER,
                VfxEditor2EffectType.CROSS,
            ),
        ),
    }

    private var composition = initialComposition
    private var selectedEffectId: Long? = initialComposition.effects.firstOrNull()?.id
    private var nextEffectId = (initialComposition.effects.maxOfOrNull { it.id } ?: 0L) + 1L
    private var statusText = "Ready"
    private var previewActive = false
    private var previewDebounceTicks = 0
    private var requestId = 0L
    private var addMenuOpen = false
    private var addCategory: EffectCategory? = null
    private var addPage = 0
    private var inspectorPage = 0
    private var syncingColorField = false
    private var placedControls: List<PlacedControl> = emptyList()
    private var placedChoices: List<PlacedChoice> = emptyList()
    private var sectionLabels: List<Pair<String, Int>> = emptyList()
    private var colorFieldY = 0
    private var presetY = 0
    private var nameField: EditBox? = null
    private var colorField: EditBox? = null

    private val rightPanelX: Int
        get() = (width - RIGHT_PANEL_WIDTH).coerceAtLeast(LEFT_PANEL_WIDTH + 40)

    private val bottomBarY: Int
        get() = (height - BOTTOM_BAR_HEIGHT).coerceAtLeast(0)

    override fun isPauseScreen(): Boolean = false

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) = Unit

    override fun init() {
        rebuildEditorWidgets()
    }

    override fun rebuildWidgets() {
        rebuildEditorWidgets()
    }

    private fun rebuildEditorWidgets() {
        clearWidgets()
        nameField = null
        colorField = null
        placedControls = emptyList()
        placedChoices = emptyList()
        sectionLabels = emptyList()

        val addY = (38 + composition.effects.size * EFFECT_ROW_HEIGHT).coerceAtMost(bottomBarY - 24)
        addRenderableWidget(
            Button.builder(Component.literal(if (addMenuOpen) "- Close Add Effect" else "+ Add Effect")) {
                addMenuOpen = !addMenuOpen
                if (!addMenuOpen) {
                    addCategory = null
                    addPage = 0
                }
                rebuildEditorWidgets()
            }.bounds(8, addY, LEFT_PANEL_WIDTH - 16, 20).build(),
        )

        var actionY = addY + 24
        if (addMenuOpen) {
            val category = addCategory
            if (category == null) {
                EffectCategory.entries.forEach { entry ->
                    addRenderableWidget(
                        Button.builder(Component.literal(entry.label)) {
                            addCategory = entry
                            addPage = 0
                            rebuildEditorWidgets()
                        }.bounds(8, actionY, LEFT_PANEL_WIDTH - 16, 18).build(),
                    )
                    actionY += 20
                }
            } else {
                addRenderableWidget(
                    Button.builder(Component.literal("<- Categories")) {
                        addCategory = null
                        addPage = 0
                        rebuildEditorWidgets()
                    }.bounds(8, actionY, LEFT_PANEL_WIDTH - 16, 18).build(),
                )
                actionY += 20
                val pageCount = (category.types.size + ADD_ITEMS_PER_PAGE - 1) / ADD_ITEMS_PER_PAGE
                val currentPage = addPage.coerceIn(0, pageCount - 1)
                addPage = currentPage
                val pageStart = currentPage * ADD_ITEMS_PER_PAGE
                val pageTypes = category.types.drop(pageStart).take(ADD_ITEMS_PER_PAGE)
                val pageButtonWidth = (LEFT_PANEL_WIDTH - 19) / 2
                addRenderableWidget(
                    Button.builder(Component.literal("< Page")) {
                        addPage = (currentPage - 1).coerceAtLeast(0)
                        rebuildEditorWidgets()
                    }.bounds(8, actionY, pageButtonWidth, 18).build(),
                )
                addRenderableWidget(
                    Button.builder(Component.literal("Page ${currentPage + 1}/$pageCount >")) {
                        addPage = (currentPage + 1).coerceAtMost(pageCount - 1)
                        rebuildEditorWidgets()
                    }.bounds(11 + pageButtonWidth, actionY, pageButtonWidth, 18).build(),
                )
                actionY += 20
                pageTypes.forEach { type ->
                    addRenderableWidget(
                        Button.builder(Component.literal(vfxEditor2DisplayName(type))) { addEffect(type) }
                            .bounds(8, actionY, LEFT_PANEL_WIDTH - 16, 18)
                            .build(),
                    )
                    actionY += 20
                }
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
        addRenderableWidget(Button.builder(Component.literal("Play")) { play() }.bounds(centerButtonX, bottomBarY + 8, 78, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Stop")) { stop() }.bounds(centerButtonX + 84, bottomBarY + 8, 78, 20).build())

        selectedEffect()?.let { effect ->
            val panelX = rightPanelX
            val field = EditBox(font, panelX + 102, 31, RIGHT_PANEL_WIDTH - 112, 18, Component.literal("Effect Name"))
            field.setMaxLength(MAX_NAME_LENGTH)
            field.setValue(effect.name)
            field.setResponder(::renameSelected)
            nameField = addRenderableWidget(field)

            val allControls = controlsFor(effect)
            val pageCount = (allControls.size + INSPECTOR_CONTROLS_PER_PAGE - 1) / INSPECTOR_CONTROLS_PER_PAGE
            val currentPage = inspectorPage.coerceIn(0, pageCount - 1)
            inspectorPage = currentPage
            if (pageCount > 1) {
                val pageButtonWidth = (RIGHT_PANEL_WIDTH - 20 - 4) / 2
                addRenderableWidget(
                    Button.builder(Component.literal("< Page")) {
                        inspectorPage = (currentPage - 1).coerceAtLeast(0)
                        rebuildEditorWidgets()
                    }.bounds(panelX + 10, 66, pageButtonWidth, 18).build(),
                )
                addRenderableWidget(
                    Button.builder(Component.literal("Page ${currentPage + 1}/$pageCount >")) {
                        inspectorPage = (currentPage + 1).coerceAtMost(pageCount - 1)
                        rebuildEditorWidgets()
                    }.bounds(panelX + 14 + pageButtonWidth, 66, pageButtonWidth, 18).build(),
                )
            }
            var nextY = if (pageCount > 1) 90 else 66
            var previousSection = ""
            val labels = mutableListOf<Pair<String, Int>>()
            val placed = mutableListOf<PlacedControl>()
            allControls.drop(currentPage * INSPECTOR_CONTROLS_PER_PAGE).take(INSPECTOR_CONTROLS_PER_PAGE).forEach { control ->
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
                ).also { it.setTooltip(Tooltip.create(Component.literal(control.tooltip))) }
                addRenderableWidget(slider)
                placed += PlacedControl(control, nextY)
                nextY += 20
            }
            placedControls = placed

            val placedChoiceValues = mutableListOf<PlacedChoice>()
            if (currentPage == pageCount - 1) choicesFor(effect).forEach { choice ->
                if (choice.section != previousSection) {
                    if (previousSection.isNotEmpty()) nextY += 8
                    labels += choice.section to nextY
                    nextY += 12
                    previousSection = choice.section
                }
                val currentIndex = choice.read(effect).coerceIn(0, choice.values.lastIndex)
                val button = Button.builder(Component.literal(choice.values[currentIndex])) { updateChoice(choice) }
                    .bounds(panelX + 112, nextY, RIGHT_PANEL_WIDTH - 122, 18)
                    .build()
                    .also { it.setTooltip(Tooltip.create(Component.literal(choice.tooltip))) }
                addRenderableWidget(button)
                placedChoiceValues += PlacedChoice(choice, nextY)
                nextY += 20
            }
            placedChoices = placedChoiceValues
            sectionLabels = labels
            colorFieldY = if (currentPage == pageCount - 1) nextY + 3 else 0
            if (currentPage == pageCount - 1) addColorControls(panelX, effect)
        }
    }

    private fun addColorControls(panelX: Int, effect: VfxEditor2Effect) {
        val field = EditBox(font, panelX + 102, colorFieldY, 92, 18, Component.literal("Color"))
        field.setMaxLength(7)
        field.setValue(formatColor(effect.appearance.color))
        field.setResponder { value -> if (!syncingColorField) updateColorFromInput(value) }
        field.setTooltip(Tooltip.create(Component.literal("Dust particle color")))
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
            graphics.text(font, vfxEditor2DisplayName(effect.type), rightPanelX + 12, 52, 0xFF9BB4CE.toInt(), false)
            sectionLabels.forEach { (label, y) -> graphics.text(font, label, rightPanelX + 10, y, 0xFFCF7990.toInt(), true) }
            placedControls.forEach { placed -> graphics.text(font, placed.spec.label, rightPanelX + 10, placed.y + 5, 0xFFD5E2F0.toInt(), false) }
            placedChoices.forEach { placed -> graphics.text(font, placed.spec.label, rightPanelX + 10, placed.y + 5, 0xFFD5E2F0.toInt(), false) }
            if (colorFieldY > 0) {
                graphics.text(font, "Color", rightPanelX + 10, colorFieldY + 5, 0xFFD5E2F0.toInt(), false)
                val color = effect.appearance.color or 0xff000000.toInt()
                graphics.fill(rightPanelX + 80, colorFieldY + 2, rightPanelX + 98, colorFieldY + 20, color)
                graphics.fill(rightPanelX + 80, colorFieldY + 2, rightPanelX + 98, colorFieldY + 3, 0xffffffff.toInt())
            }
            graphics.text(font, "Estimated: ${composition.estimatedSampleCount()} particles", rightPanelX + 10, presetY + 22, 0xFF8EA9C5.toInt(), false)
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
            VfxEditor2StatusKind.PREVIEW_REQUESTED, VfxEditor2StatusKind.PLAYING -> "Previewing"
            VfxEditor2StatusKind.STOPPED -> "Stopped"
            VfxEditor2StatusKind.ERROR -> "Invalid value"
        }
        previewActive = status.kind == VfxEditor2StatusKind.PREVIEW_REQUESTED || status.kind == VfxEditor2StatusKind.PLAYING
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
        addCategory = null
        addPage = 0
        inspectorPage = 0
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
            addCategory = null
            addPage = 0
            rebuildEditorWidgets()
            return
        }
        val effect = defaultVfxEditor2Effect(type, allocateEffectId())
        composition = composition.add(effect) ?: return
        selectedEffectId = effect.id
        addMenuOpen = false
        addCategory = null
        addPage = 0
        inspectorPage = 0
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
        inspectorPage = 0
        rebuildEditorWidgets()
        schedulePreview()
    }

    private fun deleteSelected() {
        val selected = selectedEffect() ?: return
        val oldIndex = composition.effects.indexOfFirst { it.id == selected.id }
        val remaining = composition.effects.filterNot { it.id == selected.id }
        composition = composition.remove(selected.id)
        selectedEffectId = remaining.getOrNull(oldIndex.coerceIn(0, remaining.lastIndex.coerceAtLeast(0)))?.id ?: remaining.lastOrNull()?.id
        inspectorPage = 0
        rebuildEditorWidgets()
        schedulePreview()
    }

    private fun moveSelected(delta: Int) {
        val selected = selectedEffect() ?: return
        val from = composition.effects.indexOfFirst { it.id == selected.id }
        val to = (from + delta).coerceIn(0, composition.effects.lastIndex)
        if (from == to) return
        val effects = composition.effects.toMutableList()
        effects.add(to, effects.removeAt(from))
        composition = composition.copy(effects = effects)
        rebuildEditorWidgets()
        schedulePreview()
    }

    private fun updateParameter(control: ControlSpec, value: Double) {
        val normalized = value.coerceIn(control.min, control.max)
        val adjusted = if (isIntegerParameter(control.key)) normalized.roundToInt().toDouble() else normalized
        updateSelectedEffect(transform = { control.write(it, adjusted) })
        schedulePreview()
    }

    private fun updateChoice(choice: ChoiceSpec) {
        val effect = selectedEffect() ?: return
        val current = choice.read(effect).coerceIn(0, choice.values.lastIndex)
        updateSelectedEffect(transform = { choice.write(it, (current + 1) % choice.values.size) })
        rebuildEditorWidgets()
        schedulePreview()
    }

    private fun renameSelected(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            statusText = "Invalid value"
            return
        }
        updateSelectedEffect({ it.copy(name = trimmed.take(MAX_NAME_LENGTH)) }, preview = false)
    }

    private fun updateColorFromInput(value: String) {
        val raw = value.removePrefix("#")
        val parsed = raw.takeIf { it.length == 6 && it.all { character -> character.isDigit() || character.lowercaseChar() in 'a'..'f' } }?.toIntOrNull(16)
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
        val field = colorField ?: return
        val effect = selectedEffect() ?: return
        syncingColorField = true
        field.setValue(formatColor(effect.appearance.color))
        syncingColorField = false
    }

    private fun selectedEffect(): VfxEditor2Effect? = selectedEffectId?.let { id -> composition.effects.firstOrNull { it.id == id } }

    private fun allocateEffectId(): Long {
        while (composition.effects.any { it.id == nextEffectId }) nextEffectId++
        return nextEffectId++
    }

    private fun controlsFor(effect: VfxEditor2Effect): List<ControlSpec> {
        fun shapeControl(
            key: ParameterKey,
            label: String,
            min: Double,
            max: Double,
            decimals: Int,
            unit: String,
            tooltip: String,
            read: (VfxEditor2Shape) -> Double,
            write: (VfxEditor2Shape, Double) -> VfxEditor2Shape,
        ) = ControlSpec(key, "Shape", label, min, max, decimals, unit, tooltip, { current -> read(current.shape) }) { current, value ->
            current.copy(shape = write(current.shape, value))
        }

        fun transformControl(
            key: ParameterKey,
            label: String,
            min: Double,
            max: Double,
            decimals: Int,
            unit: String,
            tooltip: String,
            read: (dev.projects.protocol.VfxEditor2Transform) -> Double,
            write: (dev.projects.protocol.VfxEditor2Transform, Double) -> dev.projects.protocol.VfxEditor2Transform,
        ) = ControlSpec(key, "Position", label, min, max, decimals, unit, tooltip, { current -> read(current.transform) }) { current, value ->
            current.copy(transform = write(current.transform, value))
        }

        fun appearanceControl(
            key: ParameterKey,
            label: String,
            min: Double,
            max: Double,
            decimals: Int,
            unit: String,
            tooltip: String,
            read: (VfxEditor2Appearance) -> Double,
            write: (VfxEditor2Appearance, Double) -> VfxEditor2Appearance,
        ) = ControlSpec(key, "Appearance", label, min, max, decimals, unit, tooltip, { current -> read(current.appearance) }) { current, value ->
            current.copy(appearance = write(current.appearance, value))
        }

        fun shape(
            key: ParameterKey,
            label: String,
            min: Double,
            max: Double,
            decimals: Int,
            unit: String,
            tooltip: String,
            read: (VfxEditor2Shape) -> Double,
            write: (VfxEditor2Shape, Double) -> VfxEditor2Shape,
        ) = shapeControl(key, label, min, max, decimals, unit, tooltip, read, write)

        val position = listOf(
            transformControl(ParameterKey.FORWARD, "Forward", -1.0, 8.0, 1, "blocks", "Playerの向いている方向への位置", { transform -> transform.forward }) { transform, value -> transform.copy(forward = value) },
            transformControl(ParameterKey.SIDE, "Side", -5.0, 5.0, 1, "blocks", "Player基準の左右位置", { transform -> transform.side }) { transform, value -> transform.copy(side = value) },
            transformControl(ParameterKey.HEIGHT, "Height", -2.0, 5.0, 1, "blocks", "Player基準の上下位置", { transform -> transform.height }) { transform, value -> transform.copy(height = value) },
        )
        val rotation = listOf(
            transformControl(ParameterKey.YAW, "Yaw", -180.0, 180.0, 0, "°", "左右方向の回転", { transform -> transform.yaw }) { transform, value -> transform.copy(yaw = value) }.copy(section = "Rotation"),
            transformControl(ParameterKey.PITCH, "Pitch", -180.0, 180.0, 0, "°", "上下方向の回転", { transform -> transform.pitch }) { transform, value -> transform.copy(pitch = value) }.copy(section = "Rotation"),
            transformControl(ParameterKey.ROLL, "Roll", -180.0, 180.0, 0, "°", "Effect平面自体の傾き", { transform -> transform.roll }) { transform, value -> transform.copy(roll = value) }.copy(section = "Rotation"),
        )
        val particleSize = appearanceControl(ParameterKey.PARTICLE_SIZE, "Particle Size", 0.05, 1.5, 2, "x", "表示するParticleの大きさ", { appearance -> appearance.particleSize }) { appearance, value -> appearance.copy(particleSize = value) }
        val density = appearanceControl(ParameterKey.DENSITY, "Density", 0.25, 4.0, 2, "x", "軌道上に配置するParticle密度", { appearance -> appearance.density }) { appearance, value -> appearance.copy(density = value) }

        return when (effect.type) {
            VfxEditor2EffectType.ARC_SLASH -> listOf(
                shape(ParameterKey.LENGTH, "Length", 0.5, 10.0, 1, "blocks", "Effect全体の長さ", { shape -> (shape as VfxEditor2Shape.ArcSlash).length }) { shape, value -> (shape as VfxEditor2Shape.ArcSlash).copy(length = value) },
                shape(ParameterKey.ARC, "Arc", 10.0, 300.0, 0, "°", "弧の開き角度", { shape -> (shape as VfxEditor2Shape.ArcSlash).arcDegrees }) { shape, value -> (shape as VfxEditor2Shape.ArcSlash).copy(arcDegrees = value) },
                shape(ParameterKey.CURVATURE, "Curvature", 0.0, 2.0, 2, "x", "弧の中央をどれだけ膨らませるか", { shape -> (shape as VfxEditor2Shape.ArcSlash).curvature }) { shape, value -> (shape as VfxEditor2Shape.ArcSlash).copy(curvature = value) },
                shape(ParameterKey.THICKNESS, "Thickness", 0.0, 1.5, 2, "blocks", "線・Ribbonの横方向の太さ", { shape -> (shape as VfxEditor2Shape.ArcSlash).thickness }) { shape, value -> (shape as VfxEditor2Shape.ArcSlash).copy(thickness = value) },
            ) + position + rotation + listOf(particleSize, density)
            VfxEditor2EffectType.STRAIGHT_SLASH -> listOf(
                shape(ParameterKey.LENGTH, "Length", 0.5, 10.0, 1, "blocks", "Effect全体の長さ", { shape -> (shape as VfxEditor2Shape.StraightSlash).length }) { shape, value -> (shape as VfxEditor2Shape.StraightSlash).copy(length = value) },
                shape(ParameterKey.THICKNESS, "Thickness", 0.0, 1.5, 2, "blocks", "線・Ribbonの横方向の太さ", { shape -> (shape as VfxEditor2Shape.StraightSlash).thickness }) { shape, value -> (shape as VfxEditor2Shape.StraightSlash).copy(thickness = value) },
            ) + position + rotation + listOf(particleSize, density)
            VfxEditor2EffectType.BEZIER -> listOf(
                shape(ParameterKey.LENGTH, "Length", 0.5, 10.0, 1, "blocks", "Effect全体の長さ", { shape -> (shape as VfxEditor2Shape.Bezier).length }) { shape, value -> (shape as VfxEditor2Shape.Bezier).copy(length = value) },
                shape(ParameterKey.CONTROL_FORWARD, "Control Forward", -1.0, 10.0, 1, "blocks", "中央の制御点の前後位置", { shape -> (shape as VfxEditor2Shape.Bezier).controlForward }) { shape, value -> (shape as VfxEditor2Shape.Bezier).copy(controlForward = value) },
                shape(ParameterKey.CONTROL_SIDE, "Control Side", -5.0, 5.0, 1, "blocks", "中央の制御点の左右位置", { shape -> (shape as VfxEditor2Shape.Bezier).controlSide }) { shape, value -> (shape as VfxEditor2Shape.Bezier).copy(controlSide = value) },
                shape(ParameterKey.CONTROL_HEIGHT, "Control Height", -2.0, 5.0, 1, "blocks", "中央の制御点の上下位置", { shape -> (shape as VfxEditor2Shape.Bezier).controlHeight }) { shape, value -> (shape as VfxEditor2Shape.Bezier).copy(controlHeight = value) },
                shape(ParameterKey.END_SIDE, "End Side", -5.0, 5.0, 1, "blocks", "終点の左右位置", { shape -> (shape as VfxEditor2Shape.Bezier).endSide }) { shape, value -> (shape as VfxEditor2Shape.Bezier).copy(endSide = value) },
                shape(ParameterKey.END_HEIGHT, "End Height", -2.0, 5.0, 1, "blocks", "終点の上下位置", { shape -> (shape as VfxEditor2Shape.Bezier).endHeight }) { shape, value -> (shape as VfxEditor2Shape.Bezier).copy(endHeight = value) },
                shape(ParameterKey.THICKNESS, "Thickness", 0.0, 1.5, 2, "blocks", "曲線Ribbonの横方向の太さ", { shape -> (shape as VfxEditor2Shape.Bezier).thickness }) { shape, value -> (shape as VfxEditor2Shape.Bezier).copy(thickness = value) },
            ) + position + rotation + listOf(particleSize, density)
            VfxEditor2EffectType.WAVE -> listOf(
                shape(ParameterKey.LENGTH, "Length", 0.5, 10.0, 1, "blocks", "Wave全体の長さ", { shape -> (shape as VfxEditor2Shape.Wave).length }) { shape, value -> (shape as VfxEditor2Shape.Wave).copy(length = value) },
                shape(ParameterKey.AMPLITUDE, "Amplitude", 0.0, 8.0, 1, "blocks", "波の左右の振れ幅", { shape -> (shape as VfxEditor2Shape.Wave).amplitude }) { shape, value -> (shape as VfxEditor2Shape.Wave).copy(amplitude = value) },
                shape(ParameterKey.WAVES, "Waves / Frequency", 1.0, 8.0, 0, "waves", "長さの中に何周期入れるか", { shape -> (shape as VfxEditor2Shape.Wave).waves.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.Wave).copy(waves = value.roundToInt()) },
                shape(ParameterKey.PHASE, "Phase", -360.0, 360.0, 0, "°", "Waveの開始位置", { shape -> (shape as VfxEditor2Shape.Wave).phaseDegrees }) { shape, value -> (shape as VfxEditor2Shape.Wave).copy(phaseDegrees = value) },
                shape(ParameterKey.THICKNESS, "Thickness", 0.0, 1.5, 2, "blocks", "Wave Ribbonの横方向の太さ", { shape -> (shape as VfxEditor2Shape.Wave).thickness }) { shape, value -> (shape as VfxEditor2Shape.Wave).copy(thickness = value) },
            ) + position + rotation + listOf(density)
            VfxEditor2EffectType.LIGHTNING -> listOf(
                shape(ParameterKey.LENGTH, "Length", 0.5, 10.0, 1, "blocks", "雷の長さ", { shape -> (shape as VfxEditor2Shape.Lightning).length }) { shape, value -> (shape as VfxEditor2Shape.Lightning).copy(length = value) },
                shape(ParameterKey.JITTER, "Jitter / Variance", 0.0, 2.0, 2, "blocks", "雷の折れ曲がり幅", { shape -> (shape as VfxEditor2Shape.Lightning).jitter }) { shape, value -> (shape as VfxEditor2Shape.Lightning).copy(jitter = value) },
                shape(ParameterKey.HOPS, "Hops", 1.0, 32.0, 0, "hops", "雷を分割する区間数", { shape -> (shape as VfxEditor2Shape.Lightning).hops.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.Lightning).copy(hops = value.roundToInt()) },
                shape(ParameterKey.SEED, "Seed", 0.0, 100.0, 0, "id", "同じ値なら同じ雷の形", { shape -> (shape as VfxEditor2Shape.Lightning).seed.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.Lightning).copy(seed = value.roundToLong()) },
            ) + position + rotation + listOf(density)
            VfxEditor2EffectType.SPIRAL -> listOf(
                shape(ParameterKey.RADIUS, "Radius", 0.0, 8.0, 1, "blocks", "螺旋の最大半径", { shape -> (shape as VfxEditor2Shape.Spiral).radius }) { shape, value -> (shape as VfxEditor2Shape.Spiral).copy(radius = value) },
                shape(ParameterKey.LENGTH, "Length", 0.5, 10.0, 1, "blocks", "螺旋の軸方向の長さ", { shape -> (shape as VfxEditor2Shape.Spiral).length }) { shape, value -> (shape as VfxEditor2Shape.Spiral).copy(length = value) },
                shape(ParameterKey.TURNS, "Turns", 0.25, 8.0, 2, "turns", "螺旋が回る回数", { shape -> (shape as VfxEditor2Shape.Spiral).turns }) { shape, value -> (shape as VfxEditor2Shape.Spiral).copy(turns = value) },
                shape(ParameterKey.ANGLE_OFFSET, "Angle Offset", -360.0, 360.0, 0, "°", "螺旋の開始角度", { shape -> (shape as VfxEditor2Shape.Spiral).angleOffsetDegrees }) { shape, value -> (shape as VfxEditor2Shape.Spiral).copy(angleOffsetDegrees = value) },
            ) + position + rotation + listOf(density)
            VfxEditor2EffectType.HELIX -> listOf(
                shape(ParameterKey.RADIUS, "Radius", 0.0, 8.0, 1, "blocks", "螺旋の一定半径", { shape -> (shape as VfxEditor2Shape.Helix).radius }) { shape, value -> (shape as VfxEditor2Shape.Helix).copy(radius = value) },
                shape(ParameterKey.LENGTH, "Length", 0.5, 10.0, 1, "blocks", "螺旋の軸方向の長さ", { shape -> (shape as VfxEditor2Shape.Helix).length }) { shape, value -> (shape as VfxEditor2Shape.Helix).copy(length = value) },
                shape(ParameterKey.TURNS, "Turns", 0.25, 8.0, 2, "turns", "螺旋が回る回数", { shape -> (shape as VfxEditor2Shape.Helix).turns }) { shape, value -> (shape as VfxEditor2Shape.Helix).copy(turns = value) },
                shape(ParameterKey.PHASE, "Phase", -360.0, 360.0, 0, "°", "螺旋の開始角度", { shape -> (shape as VfxEditor2Shape.Helix).phaseDegrees }) { shape, value -> (shape as VfxEditor2Shape.Helix).copy(phaseDegrees = value) },
            ) + position + rotation + listOf(density)
            VfxEditor2EffectType.RING -> listOf(
                shape(ParameterKey.RADIUS, "Radius", 0.05, 8.0, 1, "blocks", "円の外周までの距離", { shape -> (shape as VfxEditor2Shape.Ring).radius }) { shape, value -> (shape as VfxEditor2Shape.Ring).copy(radius = value) },
                shape(ParameterKey.ARC, "Arc", 10.0, 360.0, 0, "°", "円弧の開き角度", { shape -> (shape as VfxEditor2Shape.Ring).arcDegrees }) { shape, value -> (shape as VfxEditor2Shape.Ring).copy(arcDegrees = value) },
                shape(ParameterKey.THICKNESS, "Thickness", 0.0, 1.5, 2, "blocks", "リングの横方向の太さ", { shape -> (shape as VfxEditor2Shape.Ring).thickness }) { shape, value -> (shape as VfxEditor2Shape.Ring).copy(thickness = value) },
            ) + position + rotation + listOf(density)
            VfxEditor2EffectType.DISK -> listOf(
                shape(ParameterKey.RADIUS, "Radius", 0.05, 8.0, 1, "blocks", "円盤の外周までの距離", { shape -> (shape as VfxEditor2Shape.Disk).radius }) { shape, value -> val disk = shape as VfxEditor2Shape.Disk; disk.copy(radius = value, innerRadius = disk.innerRadius.coerceAtMost(value)) },
                shape(ParameterKey.INNER_RADIUS, "Inner Radius", 0.0, 8.0, 1, "blocks", "中央を空ける半径。0なら円盤", { shape -> (shape as VfxEditor2Shape.Disk).innerRadius }) { shape, value -> val disk = shape as VfxEditor2Shape.Disk; disk.copy(innerRadius = value.coerceAtMost(disk.radius)) },
            ) + position + rotation + listOf(density)
            VfxEditor2EffectType.SECTOR -> listOf(
                shape(ParameterKey.RADIUS, "Radius", 0.05, 8.0, 1, "blocks", "扇形の外周までの距離", { shape -> (shape as VfxEditor2Shape.Sector).radius }) { shape, value -> val sector = shape as VfxEditor2Shape.Sector; sector.copy(radius = value, innerRadius = sector.innerRadius.coerceAtMost(value)) },
                shape(ParameterKey.ANGLE, "Angle", 1.0, 360.0, 0, "°", "扇形の開き角度", { shape -> (shape as VfxEditor2Shape.Sector).angleDegrees }) { shape, value -> (shape as VfxEditor2Shape.Sector).copy(angleDegrees = value) },
                shape(ParameterKey.INNER_RADIUS, "Inner Radius", 0.0, 8.0, 1, "blocks", "中央を空ける半径", { shape -> (shape as VfxEditor2Shape.Sector).innerRadius }) { shape, value -> val sector = shape as VfxEditor2Shape.Sector; sector.copy(innerRadius = value.coerceAtMost(sector.radius)) },
            ) + position + rotation + listOf(density)
            VfxEditor2EffectType.GRID -> listOf(
                shape(ParameterKey.WIDTH, "Width", 0.25, 10.0, 1, "blocks", "Gridの横幅", { shape -> (shape as VfxEditor2Shape.Grid).width }) { shape, value -> (shape as VfxEditor2Shape.Grid).copy(width = value) },
                shape(ParameterKey.SHAPE_HEIGHT, "Height", 0.25, 10.0, 1, "blocks", "Gridの高さ", { shape -> (shape as VfxEditor2Shape.Grid).height }) { shape, value -> (shape as VfxEditor2Shape.Grid).copy(height = value) },
                shape(ParameterKey.ROWS, "Rows", 1.0, 32.0, 0, "rows", "縦横に置くGridの区切り数", { shape -> (shape as VfxEditor2Shape.Grid).rows.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.Grid).copy(rows = value.roundToInt()) },
            ) + position + rotation + listOf(density)
            VfxEditor2EffectType.SPHERE -> listOf(
                shape(ParameterKey.RADIUS, "Radius", 0.05, 8.0, 1, "blocks", "球の半径", { shape -> (shape as VfxEditor2Shape.Sphere).radius }) { shape, value -> (shape as VfxEditor2Shape.Sphere).copy(radius = value) },
            ) + position + rotation + listOf(density)
            VfxEditor2EffectType.ORB -> listOf(
                shape(ParameterKey.RADIUS, "Radius", 0.05, 8.0, 1, "blocks", "Orbの半径", { shape -> (shape as VfxEditor2Shape.Orb).radius }) { shape, value -> (shape as VfxEditor2Shape.Orb).copy(radius = value) },
                shape(ParameterKey.COUNT, "Count", 1.0, 256.0, 0, "particles", "Orbの中へ配置するParticle数", { shape -> (shape as VfxEditor2Shape.Orb).count.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.Orb).copy(count = value.roundToInt()) },
                shape(ParameterKey.SEED, "Seed", 0.0, 100.0, 0, "id", "同じ値なら同じ配置", { shape -> (shape as VfxEditor2Shape.Orb).seed.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.Orb).copy(seed = value.roundToLong()) },
            ) + position + rotation + listOf(density)
            VfxEditor2EffectType.DOME -> listOf(shape(ParameterKey.RADIUS, "Radius", 0.05, 8.0, 1, "blocks", "Domeの半径", { shape -> (shape as VfxEditor2Shape.Dome).radius }) { shape, value -> (shape as VfxEditor2Shape.Dome).copy(radius = value) }) + position + rotation + listOf(density)
            VfxEditor2EffectType.CYLINDER -> listOf(
                shape(ParameterKey.RADIUS, "Radius", 0.05, 8.0, 1, "blocks", "柱の半径", { shape -> (shape as VfxEditor2Shape.Cylinder).radius }) { shape, value -> (shape as VfxEditor2Shape.Cylinder).copy(radius = value) },
                shape(ParameterKey.SHAPE_HEIGHT, "Height", 0.25, 10.0, 1, "blocks", "柱の高さ", { shape -> (shape as VfxEditor2Shape.Cylinder).height }) { shape, value -> (shape as VfxEditor2Shape.Cylinder).copy(height = value) },
                shape(ParameterKey.COUNT, "Count", 1.0, 256.0, 0, "particles", "柱へ配置するParticle数", { shape -> (shape as VfxEditor2Shape.Cylinder).count.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.Cylinder).copy(count = value.roundToInt()) },
            ) + position + rotation + listOf(density)
            VfxEditor2EffectType.CONE -> listOf(
                shape(ParameterKey.LENGTH, "Length", 0.5, 10.0, 1, "blocks", "Coneの奥行き", { shape -> (shape as VfxEditor2Shape.Cone).length }) { shape, value -> (shape as VfxEditor2Shape.Cone).copy(length = value) },
                shape(ParameterKey.RADIUS, "Radius", 0.05, 8.0, 1, "blocks", "Cone先端の半径", { shape -> (shape as VfxEditor2Shape.Cone).radius }) { shape, value -> (shape as VfxEditor2Shape.Cone).copy(radius = value) },
                shape(ParameterKey.CONE_ANGLE, "Angle", 1.0, 89.0, 0, "°", "Coneの広がり角度", { shape -> (shape as VfxEditor2Shape.Cone).angleDegrees }) { shape, value -> (shape as VfxEditor2Shape.Cone).copy(angleDegrees = value) },
            ) + position + rotation + listOf(density)
            VfxEditor2EffectType.BOX -> listOf(
                shape(ParameterKey.WIDTH, "Width", 0.25, 10.0, 1, "blocks", "Boxの横幅", { shape -> (shape as VfxEditor2Shape.Box).width }) { shape, value -> (shape as VfxEditor2Shape.Box).copy(width = value) },
                shape(ParameterKey.SHAPE_HEIGHT, "Height", 0.25, 10.0, 1, "blocks", "Boxの高さ", { shape -> (shape as VfxEditor2Shape.Box).height }) { shape, value -> (shape as VfxEditor2Shape.Box).copy(height = value) },
                shape(ParameterKey.DEPTH, "Depth", 0.25, 10.0, 1, "blocks", "Boxの奥行き", { shape -> (shape as VfxEditor2Shape.Box).depth }) { shape, value -> (shape as VfxEditor2Shape.Box).copy(depth = value) },
            ) + position + rotation + listOf(density)
            VfxEditor2EffectType.TORUS -> listOf(
                shape(ParameterKey.MAJOR_RADIUS, "Major Radius", 0.05, 8.0, 1, "blocks", "Donut中心からチューブ中心までの距離", { shape -> (shape as VfxEditor2Shape.Torus).majorRadius }) { shape, value -> val torus = shape as VfxEditor2Shape.Torus; torus.copy(majorRadius = value, tubeRadius = torus.tubeRadius.coerceAtMost(value)) },
                shape(ParameterKey.TUBE_RADIUS, "Tube Radius", 0.05, 8.0, 1, "blocks", "Donutチューブの太さ", { shape -> (shape as VfxEditor2Shape.Torus).tubeRadius }) { shape, value -> val torus = shape as VfxEditor2Shape.Torus; torus.copy(tubeRadius = value.coerceAtMost(torus.majorRadius)) },
            ) + position + rotation + listOf(density)
            VfxEditor2EffectType.STAR_FLOWER -> listOf(
                shape(ParameterKey.POINTS, "Points / Petals", 2.0, 12.0, 0, "points", "星の頂点または花弁の数", { shape -> (shape as VfxEditor2Shape.Star).points.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.Star).copy(points = value.roundToInt()) },
                shape(ParameterKey.RADIUS, "Radius", 0.05, 8.0, 1, "blocks", "星の外側の半径", { shape -> (shape as VfxEditor2Shape.Star).radius }) { shape, value -> val star = shape as VfxEditor2Shape.Star; star.copy(radius = value, innerRadius = star.innerRadius.coerceAtMost(value)) },
                shape(ParameterKey.INNER_RADIUS, "Inner Radius", 0.0, 8.0, 1, "blocks", "星の内側の半径", { shape -> (shape as VfxEditor2Shape.Star).innerRadius }) { shape, value -> val star = shape as VfxEditor2Shape.Star; star.copy(innerRadius = value.coerceAtMost(star.radius)) },
                shape(ParameterKey.SHARPNESS, "Sharpness", 0.0, 2.0, 2, "x", "頂点をどれだけ尖らせるか", { shape -> (shape as VfxEditor2Shape.Star).sharpness }) { shape, value -> (shape as VfxEditor2Shape.Star).copy(sharpness = value) },
            ) + position + rotation + listOf(density)
            VfxEditor2EffectType.CROSS -> listOf(
                shape(ParameterKey.SIZE, "Size", 0.25, 10.0, 1, "blocks", "X全体の大きさ", { shape -> (shape as VfxEditor2Shape.Cross).size }) { shape, value -> (shape as VfxEditor2Shape.Cross).copy(size = value) },
                shape(ParameterKey.ANGLE, "Angle", -360.0, 360.0, 0, "°", "Xを平面内で回す角度", { shape -> (shape as VfxEditor2Shape.Cross).angleDegrees }) { shape, value -> (shape as VfxEditor2Shape.Cross).copy(angleDegrees = value) },
                shape(ParameterKey.THICKNESS, "Thickness", 0.0, 1.5, 2, "blocks", "線の横方向の太さ", { shape -> (shape as VfxEditor2Shape.Cross).thickness }) { shape, value -> (shape as VfxEditor2Shape.Cross).copy(thickness = value) },
            ) + position + rotation + listOf(particleSize, density)
            VfxEditor2EffectType.SHOCKWAVE -> listOf(
                shape(ParameterKey.START_RADIUS, "Start Radius", 0.0, 8.0, 1, "blocks", "Shockwaveの開始半径", { shape -> (shape as VfxEditor2Shape.Shockwave).startRadius }) { shape, value -> (shape as VfxEditor2Shape.Shockwave).copy(startRadius = value) },
                shape(ParameterKey.END_RADIUS, "End Radius", 0.05, 8.0, 1, "blocks", "Shockwaveの終了半径", { shape -> (shape as VfxEditor2Shape.Shockwave).endRadius }) { shape, value -> (shape as VfxEditor2Shape.Shockwave).copy(endRadius = value) },
                shape(ParameterKey.DURATION, "Duration", 1.0, 40.0, 0, "ticks", "拡大にかかる時間", { shape -> (shape as VfxEditor2Shape.Shockwave).durationTicks.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.Shockwave).copy(durationTicks = value.roundToInt()) },
            ) + position + rotation + listOf(density)
            VfxEditor2EffectType.VORTEX -> listOf(
                shape(ParameterKey.RADIUS, "Radius", 0.05, 8.0, 1, "blocks", "Vortexの軸からの距離", { shape -> (shape as VfxEditor2Shape.Vortex).radius }) { shape, value -> (shape as VfxEditor2Shape.Vortex).copy(radius = value) },
                shape(ParameterKey.SHAPE_HEIGHT, "Height", 0.25, 10.0, 1, "blocks", "Vortexの高さ", { shape -> (shape as VfxEditor2Shape.Vortex).height }) { shape, value -> (shape as VfxEditor2Shape.Vortex).copy(height = value) },
                shape(ParameterKey.TURNS, "Turns", 0.25, 8.0, 2, "turns", "上昇中に回る回数", { shape -> (shape as VfxEditor2Shape.Vortex).turns }) { shape, value -> (shape as VfxEditor2Shape.Vortex).copy(turns = value) },
                shape(ParameterKey.DURATION, "Duration", 1.0, 40.0, 0, "ticks", "回転アニメーションの時間", { shape -> (shape as VfxEditor2Shape.Vortex).durationTicks.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.Vortex).copy(durationTicks = value.roundToInt()) },
            ) + position + rotation + listOf(density)
            VfxEditor2EffectType.TORNADO -> listOf(
                shape(ParameterKey.BOTTOM_RADIUS, "Bottom Radius", 0.05, 8.0, 1, "blocks", "竜巻の下側の半径", { shape -> (shape as VfxEditor2Shape.Tornado).bottomRadius }) { shape, value -> (shape as VfxEditor2Shape.Tornado).copy(bottomRadius = value) },
                shape(ParameterKey.TOP_RADIUS, "Top Radius", 0.05, 8.0, 1, "blocks", "竜巻の上側の半径", { shape -> (shape as VfxEditor2Shape.Tornado).topRadius }) { shape, value -> (shape as VfxEditor2Shape.Tornado).copy(topRadius = value) },
                shape(ParameterKey.SHAPE_HEIGHT, "Height", 0.25, 10.0, 1, "blocks", "竜巻の高さ", { shape -> (shape as VfxEditor2Shape.Tornado).height }) { shape, value -> (shape as VfxEditor2Shape.Tornado).copy(height = value) },
                shape(ParameterKey.TURNS, "Turns", 0.25, 8.0, 2, "turns", "竜巻が回る回数", { shape -> (shape as VfxEditor2Shape.Tornado).turns }) { shape, value -> (shape as VfxEditor2Shape.Tornado).copy(turns = value) },
                shape(ParameterKey.DURATION, "Duration", 1.0, 40.0, 0, "ticks", "回転アニメーションの時間", { shape -> (shape as VfxEditor2Shape.Tornado).durationTicks.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.Tornado).copy(durationTicks = value.roundToInt()) },
            ) + position + rotation + listOf(density)
            VfxEditor2EffectType.FOUNTAIN -> listOf(
                shape(ParameterKey.RADIUS, "Radius", 0.05, 8.0, 1, "blocks", "噴水の横方向の広がり", { shape -> (shape as VfxEditor2Shape.Fountain).radius }) { shape, value -> (shape as VfxEditor2Shape.Fountain).copy(radius = value) },
                shape(ParameterKey.SHAPE_HEIGHT, "Height / Speed", 0.25, 10.0, 1, "blocks", "噴き上がる高さ", { shape -> (shape as VfxEditor2Shape.Fountain).height }) { shape, value -> (shape as VfxEditor2Shape.Fountain).copy(height = value) },
                shape(ParameterKey.SPREAD, "Spread", 0.0, 89.0, 0, "°", "噴き上がる方向の広がり", { shape -> (shape as VfxEditor2Shape.Fountain).spreadDegrees }) { shape, value -> (shape as VfxEditor2Shape.Fountain).copy(spreadDegrees = value) },
                shape(ParameterKey.COUNT, "Count", 1.0, 256.0, 0, "particles", "噴水へ配置するParticle数", { shape -> (shape as VfxEditor2Shape.Fountain).count.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.Fountain).copy(count = value.roundToInt()) },
                shape(ParameterKey.DURATION, "Duration", 1.0, 40.0, 0, "ticks", "噴水アニメーションの時間", { shape -> (shape as VfxEditor2Shape.Fountain).durationTicks.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.Fountain).copy(durationTicks = value.roundToInt()) },
            ) + position + rotation + listOf(particleSize, density)
            VfxEditor2EffectType.BURST -> listOf(
                shape(ParameterKey.RADIUS, "Radius", 0.0, 8.0, 1, "blocks", "Burstの開始位置までの距離", { shape -> (shape as VfxEditor2Shape.Burst).radius }) { shape, value -> (shape as VfxEditor2Shape.Burst).copy(radius = value) },
                shape(ParameterKey.COUNT, "Count", 1.0, 64.0, 0, "particles", "同時に飛ばすParticle数", { shape -> (shape as VfxEditor2Shape.Burst).count.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.Burst).copy(count = value.roundToInt()) },
                shape(ParameterKey.SPREAD, "Spread", 0.0, 89.0, 0, "°", "前方へ広がる角度", { shape -> (shape as VfxEditor2Shape.Burst).spread }) { shape, value -> (shape as VfxEditor2Shape.Burst).copy(spread = value) },
                shape(ParameterKey.SPEED, "Speed", 0.0, 3.0, 2, "blocks/tick", "Particleが飛び出す速さ", { shape -> (shape as VfxEditor2Shape.Burst).speed }) { shape, value -> (shape as VfxEditor2Shape.Burst).copy(speed = value) },
                shape(ParameterKey.SEED, "Seed", 0.0, 100.0, 0, "id", "同じ値なら同じランダム形状", { shape -> (shape as VfxEditor2Shape.Burst).seed.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.Burst).copy(seed = value.roundToLong()) },
            ) + position + rotation + listOf(particleSize)
            VfxEditor2EffectType.SPHERE_BURST -> listOf(
                shape(ParameterKey.SPAWN_RADIUS, "Spawn Radius", 0.0, 8.0, 1, "blocks", "Burstが始まる球の半径", { shape -> (shape as VfxEditor2Shape.SphereBurst).spawnRadius }) { shape, value -> (shape as VfxEditor2Shape.SphereBurst).copy(spawnRadius = value) },
                shape(ParameterKey.COUNT, "Count", 1.0, 256.0, 0, "particles", "飛ばすParticle数", { shape -> (shape as VfxEditor2Shape.SphereBurst).count.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.SphereBurst).copy(count = value.roundToInt()) },
                shape(ParameterKey.SPEED, "Speed", 0.0, 3.0, 2, "blocks/tick", "Particleが飛び出す速さ", { shape -> (shape as VfxEditor2Shape.SphereBurst).speed }) { shape, value -> (shape as VfxEditor2Shape.SphereBurst).copy(speed = value) },
                shape(ParameterKey.VARIANCE, "Variance", 0.0, 3.0, 2, "blocks/tick", "速度のランダムな揺らぎ", { shape -> (shape as VfxEditor2Shape.SphereBurst).variance }) { shape, value -> (shape as VfxEditor2Shape.SphereBurst).copy(variance = value) },
                shape(ParameterKey.SEED, "Seed", 0.0, 100.0, 0, "id", "同じ値なら同じ方向配置", { shape -> (shape as VfxEditor2Shape.SphereBurst).seed.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.SphereBurst).copy(seed = value.roundToLong()) },
            ) + position + rotation + listOf(particleSize)
            VfxEditor2EffectType.CONE_BURST -> listOf(
                shape(ParameterKey.LENGTH, "Length", 0.5, 10.0, 1, "blocks", "Cone Burstの奥行き", { shape -> (shape as VfxEditor2Shape.ConeBurst).length }) { shape, value -> (shape as VfxEditor2Shape.ConeBurst).copy(length = value) },
                shape(ParameterKey.RADIUS, "Radius", 0.05, 8.0, 1, "blocks", "Cone Burstの最大半径", { shape -> (shape as VfxEditor2Shape.ConeBurst).radius }) { shape, value -> (shape as VfxEditor2Shape.ConeBurst).copy(radius = value) },
                shape(ParameterKey.CONE_ANGLE, "Cone Angle", 1.0, 89.0, 0, "°", "前方へ広がる角度", { shape -> (shape as VfxEditor2Shape.ConeBurst).angleDegrees }) { shape, value -> (shape as VfxEditor2Shape.ConeBurst).copy(angleDegrees = value) },
                shape(ParameterKey.COUNT, "Count", 1.0, 256.0, 0, "particles", "飛ばすParticle数", { shape -> (shape as VfxEditor2Shape.ConeBurst).count.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.ConeBurst).copy(count = value.roundToInt()) },
                shape(ParameterKey.SPEED, "Speed", 0.0, 3.0, 2, "blocks/tick", "Particleが飛び出す速さ", { shape -> (shape as VfxEditor2Shape.ConeBurst).speed }) { shape, value -> (shape as VfxEditor2Shape.ConeBurst).copy(speed = value) },
                shape(ParameterKey.SEED, "Seed", 0.0, 100.0, 0, "id", "同じ値なら同じ配置", { shape -> (shape as VfxEditor2Shape.ConeBurst).seed.toDouble() }) { shape, value -> (shape as VfxEditor2Shape.ConeBurst).copy(seed = value.roundToLong()) },
            ) + position + rotation + listOf(particleSize)
        }
    }

    private fun choicesFor(effect: VfxEditor2Effect): List<ChoiceSpec> = when (effect.type) {
        VfxEditor2EffectType.SPIRAL -> listOf(
            ChoiceSpec(ChoiceKey.REVERSE, "Options", "Direction", listOf("Forward", "Reverse"), { current -> if ((current.shape as VfxEditor2Shape.Spiral).reverse) 1 else 0 }, { current, value -> current.copy(shape = (current.shape as VfxEditor2Shape.Spiral).copy(reverse = value == 1)) }, "螺旋を前から進めるか逆向きにするか"),
        )
        VfxEditor2EffectType.DOME -> listOf(
            ChoiceSpec(ChoiceKey.DIRECTION, "Options", "Cut / Direction", listOf("Upper", "Lower"), { current -> (current.shape as VfxEditor2Shape.Dome).direction.ordinal }, { current, value -> current.copy(shape = (current.shape as VfxEditor2Shape.Dome).copy(direction = VfxEditor2Direction.values()[value])) }, "上半分または下半分を表示"),
        )
        VfxEditor2EffectType.CYLINDER -> listOf(
            ChoiceSpec(ChoiceKey.SHELL, "Options", "Fill", listOf("Filled", "Shell"), { current -> if ((current.shape as VfxEditor2Shape.Cylinder).shell) 1 else 0 }, { current, value -> current.copy(shape = (current.shape as VfxEditor2Shape.Cylinder).copy(shell = value == 1)) }, "柱の内部にもParticleを置くか、外周だけにするか"),
        )
        VfxEditor2EffectType.BOX -> listOf(
            ChoiceSpec(ChoiceKey.MODE, "Options", "Mode", listOf("Edges", "Faces"), { current -> if ((current.shape as VfxEditor2Shape.Box).mode == VfxEditor2BoxMode.FACES) 1 else 0 }, { current, value -> current.copy(shape = (current.shape as VfxEditor2Shape.Box).copy(mode = if (value == 1) VfxEditor2BoxMode.FACES else VfxEditor2BoxMode.EDGES)) }, "Boxの輪郭だけ、または面全体へ表示"),
        )
        VfxEditor2EffectType.VORTEX -> listOf(
            ChoiceSpec(ChoiceKey.DIRECTION, "Options", "Direction", listOf("Up", "Down"), { current -> (current.shape as VfxEditor2Shape.Vortex).direction.ordinal }, { current, value -> current.copy(shape = (current.shape as VfxEditor2Shape.Vortex).copy(direction = VfxEditor2Direction.values()[value])) }, "Vortexが上昇するか下降するか"),
        )
        else -> emptyList()
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
        Component.literal(format(value, control.decimals, control.unit)),
        normalized(value, control.min, control.max),
    ) {
        private var current = value

        override fun updateMessage() {
            message = Component.literal(format(current, control.decimals, control.unit))
        }

        override fun applyValue() {
            current = control.min + value * (control.max - control.min)
            if (isIntegerParameter(control.key)) current = current.roundToInt().toDouble()
            current = current.coerceIn(control.min, control.max)
            changed(current)
            updateMessage()
        }

        companion object {
            private fun normalized(value: Double, min: Double, max: Double): Double = ((value - min) / (max - min)).coerceIn(0.0, 1.0)

            private fun format(value: Double, decimals: Int, unit: String): String {
                val number = "%.${decimals}f".format(value)
                return if (unit.isBlank()) number else "$number $unit"
            }
        }
    }
}
