package dev.projects.client

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class HudDesignerScreen(
    private val store: HudLayoutStore,
    initialConfig: HudLayoutConfig,
    private val onSaved: (HudLayoutConfig) -> Unit = {},
    private val onChanged: (HudLayoutConfig) -> Unit = {},
) : Screen(Component.literal("HUD Designer")) {
    private var config = initialConfig.copyLayouts()
    private var selected = HudElementId.SKILLS
    private var dragging = false
    private var dragX = 0
    private var dragY = 0
    private var guideX: Int? = null
    private var guideY: Int? = null
    private var syncingFields = false
    private lateinit var widthField: EditBox
    private lateinit var heightField: EditBox

    override fun isPauseScreen(): Boolean = false
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) = Unit

    override fun init() {
        widthField = addRenderableWidget(EditBox(font, width - 145, 48, 55, 18, Component.literal("Width")))
        heightField = addRenderableWidget(EditBox(font, width - 75, 48, 55, 18, Component.literal("Height")))
        widthField.setMaxLength(4)
        heightField.setMaxLength(4)
        widthField.setResponder { if (!syncingFields) applyDimensions() }
        heightField.setResponder { if (!syncingFields) applyDimensions() }
        addRenderableWidget(Button.builder(Component.literal("Center X")) { centerX() }
            .bounds(width - 145, 74, 110, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Center Y")) { centerY() }
            .bounds(width - 145, 98, 110, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Reset")) { config.elements[selected] = HudLayoutConfig.defaults().elements[selected]!!; changed() }
            .bounds(width - 145, 122, 110, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Save")) { saveConfig() }
            .bounds(width - 145, 146, 110, 20).build())
        syncFields()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        coverVanillaHotbar(graphics)
        graphics.fill(width - CONTROL_PANEL_WIDTH, 0, width, height, 0xDD10151C.toInt())
        graphics.text(font, "HUD Designer", width - 148, 12, 0xFFFFFFFF.toInt(), true)
        graphics.text(font, "Width / Height", width - 145, 37, 0xFFB8C4CF.toInt(), false)
        HudElementId.entries.forEach { id ->
            val layout = config.elements[id]!!
            val rect = layout.resolve(width, height)
            val selectedColor = if (id == selected) 0xFFFFFFFF.toInt() else 0xFF78838D.toInt()
            graphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, 0x553F5968)
            outline(graphics, rect, selectedColor)
            graphics.text(font, id.label, rect.x + 3, rect.y + 3, selectedColor, false)
        }
        drawHotbarPreview(graphics, config.elements[HudElementId.HOTBAR]!!.resolve(width, height))
        guideX?.let { graphics.fill(it, 0, it + 1, height, 0x8899D9FF.toInt()) }
        guideY?.let { graphics.fill(0, it, width - CONTROL_PANEL_WIDTH, it + 1, 0x8899D9FF.toInt()) }
        val layout = config.elements[selected]!!
        graphics.text(font, "X ${layout.offsetX} / Y ${layout.offsetY}", width - 148, 178, 0xFFB8C4CF.toInt(), false)
        graphics.text(font, "W ${layout.width} / H ${layout.height}", width - 148, 190, 0xFFB8C4CF.toInt(), false)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        applyDimensions()
        val mouseX = event.x()
        val mouseY = event.y()
        if (mouseX >= width - CONTROL_PANEL_WIDTH) {
            return super.mouseClicked(event, doubleClick)
        }
        val hit = HudElementId.entries.asReversed().firstOrNull { config.elements[it]!!.resolve(width, height).contains(mouseX, mouseY) }
        if (hit != null) {
            selected = hit
            dragging = true
            dragX = mouseX.toInt()
            dragY = mouseY.toInt()
            syncFields()
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        dragging = false
        guideX = null
        guideY = null
        return super.mouseReleased(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        if (!dragging) return super.mouseDragged(event, deltaX, deltaY)
        val layout = config.elements[selected]!!
        val rect = layout.resolve(width, height)
        config.elements[selected] = layout.movedTo(rect.x + (mouseX.toInt() - this.dragX), rect.y + (mouseY.toInt() - this.dragY), width, height)
        val snapped = HudLayoutSnap.snap(
            selected,
            config.elements[selected]!!,
            config.elements,
            width,
            height,
            enabled = event.modifiers() and GLFW.GLFW_MOD_ALT == 0,
        )
        config.elements[selected] = snapped.layout
        guideX = snapped.guideX
        guideY = snapped.guideY
        this.dragX = mouseX.toInt()
        this.dragY = mouseY.toInt()
        syncFields()
        onChanged(config.copyLayouts())
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val step = if (event.modifiers() and GLFW.GLFW_MOD_SHIFT != 0) 5 else 1
        val delta = when (event.key()) {
            GLFW.GLFW_KEY_LEFT -> -step to 0
            GLFW.GLFW_KEY_RIGHT -> step to 0
            GLFW.GLFW_KEY_UP -> 0 to -step
            GLFW.GLFW_KEY_DOWN -> 0 to step
            else -> return super.keyPressed(event)
        }
        val layout = config.elements[selected]!!
        val rect = layout.resolve(width, height)
        config.elements[selected] = layout.movedTo(rect.x + delta.first, rect.y + delta.second, width, height)
        guideX = null
        guideY = null
        changed()
        return true
    }

    override fun onClose() {
        saveConfig()
        super.onClose()
    }

    private fun syncFields() {
        if (!::widthField.isInitialized) return
        val layout = config.elements[selected]!!
        syncingFields = true
        try {
            widthField.setValue(layout.width.toString())
            heightField.setValue(layout.height.toString())
        } finally {
            syncingFields = false
        }
    }

    private fun applyDimensions() {
        val layout = config.elements[selected]!!
        val newWidth = widthField.value.toIntOrNull() ?: layout.width
        val newHeight = heightField.value.toIntOrNull() ?: layout.height
        config.elements[selected] = layout.resizedFor(selected, newWidth, newHeight)
        onChanged(config.copyLayouts())
    }

    private fun updateSelected(update: HudElementLayout.() -> HudElementLayout) {
        applyDimensions()
        config.elements[selected] = config.elements[selected]!!.update()
        changed()
    }

    private fun centerX() {
        applyDimensions()
        val layout = config.elements[selected]!!
        val rect = layout.resolve(width, height)
        config.elements[selected] = layout.movedTo((width - layout.width) / 2, rect.y, width, height)
        changed()
    }

    private fun centerY() {
        applyDimensions()
        val layout = config.elements[selected]!!
        val rect = layout.resolve(width, height)
        config.elements[selected] = layout.movedTo(rect.x, (height - layout.height) / 2, width, height)
        changed()
    }

    private fun saveConfig() {
        applyDimensions()
        store.save(config)
        onSaved(config.copyLayouts())
        onChanged(config.copyLayouts())
        syncFields()
    }

    private fun changed() {
        guideX = null
        guideY = null
        syncFields()
        onChanged(config.copyLayouts())
    }

    private fun drawHotbarPreview(graphics: GuiGraphicsExtractor, rect: HudRect) {
        val slotWidth = (rect.width / 9).coerceAtLeast(1)
        graphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, 0xCC10151C.toInt())
        repeat(9) { index ->
            val x = rect.x + index * slotWidth
            outline(graphics, HudRect(x, rect.y, slotWidth, rect.height), 0xFFB8C4CF.toInt())
            Minecraft.getInstance().player?.inventory?.getItem(index)?.takeUnless { it.isEmpty }?.let { stack ->
                val itemX = x + (slotWidth - 16) / 2
                val itemY = rect.y + (rect.height - 16) / 2
                graphics.item(stack, itemX, itemY)
                graphics.itemDecorations(font, stack, itemX, itemY)
            }
        }
        Minecraft.getInstance().player?.inventory?.getSelectedSlot()?.let { index ->
            if (index in 0..8) outline(graphics, HudRect(rect.x + index * slotWidth, rect.y, slotWidth, rect.height), 0xFFFFFF55.toInt())
        }
    }

    private fun coverVanillaHotbar(graphics: GuiGraphicsExtractor) {
        val vanilla = HudRect((width - 182) / 2, height - 22, 182, 22)
        graphics.fill(vanilla.x, vanilla.y, vanilla.x + vanilla.width, vanilla.y + vanilla.height, 0xFF10151C.toInt())
    }

    private fun outline(graphics: GuiGraphicsExtractor, rect: HudRect, color: Int) {
        graphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + 1, color)
        graphics.fill(rect.x, rect.y + rect.height - 1, rect.x + rect.width, rect.y + rect.height, color)
        graphics.fill(rect.x, rect.y, rect.x + 1, rect.y + rect.height, color)
        graphics.fill(rect.x + rect.width - 1, rect.y, rect.x + rect.width, rect.y + rect.height, color)
    }

    private companion object {
        const val CONTROL_PANEL_WIDTH = 160
    }
}
