package dev.projects.client

import dev.projects.protocol.EquipmentPresentationCodec
import dev.projects.protocol.EquipmentPresentationSnapshot
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ArmorSlot
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.ResultContainer
import net.minecraft.world.inventory.ResultSlot
import net.minecraft.world.inventory.Slot
import net.minecraft.world.inventory.TransientCraftingContainer
import net.minecraft.world.item.ItemStack
import java.util.Base64
import java.util.Locale

private const val PRESENTATION_TAG = "projects_equipment_presentation"
private const val SLOT_SIZE = INVENTORY_CHARACTER_SLOT_SIZE
private const val PREVIEW_ENTITY_SCALE = 52

private data class InventoryCharacterUiTexture(
    val identifier: Identifier,
    val width: Int,
    val height: Int,
)

private fun inventoryCharacterUiTexture(name: String, width: Int, height: Int): InventoryCharacterUiTexture =
    InventoryCharacterUiTexture(
        Identifier.fromNamespaceAndPath("projects", "textures/gui/ui/$name.png"),
        width,
        height,
    )

private val GLASS_MAIN_TEXTURE = inventoryCharacterUiTexture("glass_main", 96, 56)
private val GLASS_SECONDARY_TEXTURE = inventoryCharacterUiTexture("glass_secondary", 88, 52)
private val GLASS_DETAIL_TEXTURE = inventoryCharacterUiTexture("glass_detail", 96, 48)
private val NAV_ROW_IDLE_TEXTURE = inventoryCharacterUiTexture("nav_row_idle", 84, 20)
private val NAV_ROW_HOVER_TEXTURE = inventoryCharacterUiTexture("nav_row_hover", 84, 20)
private val NAV_ROW_DISABLED_TEXTURE = inventoryCharacterUiTexture("nav_row_disabled", 84, 20)
private val ITEM_SLOT_IDLE_TEXTURE = inventoryCharacterUiTexture("item_slot_idle", 24, 24)
private val ITEM_SLOT_HOVER_TEXTURE = inventoryCharacterUiTexture("item_slot_hover", 24, 24)
private val ITEM_SLOT_SELECTED_TEXTURE = inventoryCharacterUiTexture("item_slot_selected", 24, 24)
private val EQUIPMENT_SLOT_IDLE_TEXTURE = inventoryCharacterUiTexture("equipment_slot_idle", 28, 28)
private val EQUIPMENT_SLOT_SELECTED_TEXTURE = inventoryCharacterUiTexture("equipment_slot_selected", 28, 28)
private val DIVIDER_TEXTURE = inventoryCharacterUiTexture("divider", 96, 4)
private val THIN_HIGHLIGHT_TEXTURE = inventoryCharacterUiTexture("thin_highlight", 96, 2)

internal data class InventoryCharacterTextureRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val sourceX: Int,
    val sourceY: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

internal fun inventoryCharacterTextureRegion(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    sourceX: Int,
    sourceY: Int,
    sourceWidth: Int,
    sourceHeight: Int,
): InventoryCharacterTextureRegion {
    require(width > 0 && height > 0) { "Inventory Character texture target must be positive" }
    require(sourceWidth > 0 && sourceHeight > 0) { "Inventory Character texture source must be positive" }
    return InventoryCharacterTextureRegion(
        x = x,
        y = y,
        width = width,
        height = height,
        sourceX = sourceX,
        sourceY = sourceY,
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
    )
}

internal data class InventoryCharacterPreviewBounds(
    val x0: Int,
    val y0: Int,
    val x1: Int,
    val y1: Int,
)

internal fun inventoryCharacterPreviewBounds(preview: HudRect): InventoryCharacterPreviewBounds = InventoryCharacterPreviewBounds(
    x0 = preview.x,
    y0 = preview.y,
    x1 = preview.x + preview.width,
    y1 = preview.y + preview.height,
)

internal class InventoryCharacterMenu(
    private val player: Player,
    layout: InventoryCharacterLayout,
) : AbstractContainerMenu(MenuType.GENERIC_9x6, InventoryMenu.CONTAINER_ID) {
    private val craftingSlots = TransientCraftingContainer(this, 2, 2)
    private val resultSlots = ResultContainer()

    init {
        addSlot(ResultSlot(player, craftingSlots, resultSlots, 0, -100, -100))
        repeat(4) { index ->
            addSlot(Slot(craftingSlots, index, -100, -100))
        }

        val armorIcons = mapOf(
            EquipmentSlot.HEAD to InventoryMenu.EMPTY_ARMOR_SLOT_HELMET,
            EquipmentSlot.CHEST to InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            EquipmentSlot.LEGS to InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
            EquipmentSlot.FEET to InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS,
        )
        val armorSlots = listOf(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
        )
        layout.equipmentSlots.forEach { (equipmentSlot, position) ->
            addSlot(
                ArmorSlot(
                    player.inventory,
                    player,
                    equipmentSlot,
                    39 - armorSlots.indexOf(equipmentSlot),
                    position.x - layout.panel.x,
                    position.y - layout.panel.y,
                    armorIcons.getValue(equipmentSlot),
                ),
            )
        }

        val inventoryGridX = layout.inventoryGrid.x - layout.panel.x
        val inventoryGridY = layout.inventoryGrid.y - layout.panel.y
        addInventorySlots(player.inventory, inventoryGridX, inventoryGridY, layout.slotStep)
        val offhand = layout.offhandSlot
        addSlot(
            object : Slot(
                player.inventory,
                Inventory.SLOT_OFFHAND,
                offhand.x - layout.panel.x,
                offhand.y - layout.panel.y,
            ) {
                override fun getNoItemIcon(): Identifier = InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD
            },
        )
        validateVisibleSlotOrder()
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack = player.inventoryMenu.quickMoveStack(player, index)

    override fun stillValid(player: Player): Boolean = true

    private fun addInventorySlots(inventory: Inventory, x: Int, y: Int, step: Int) {
        repeat(3) { row ->
            repeat(9) { column ->
                addSlot(Slot(inventory, 9 + row * 9 + column, x + column * step, y + row * step))
            }
        }
        repeat(9) { column ->
            addSlot(Slot(inventory, column, x + column * step, y + 3 * step))
        }
    }

    private fun validateVisibleSlotOrder() {
        val expected = inventoryCharacterVisibleSlotMapping()
        val actual = slots.mapIndexed { menuIndex, slot -> menuIndex to slot }
            .filter { (menuIndex, _) -> expected.any { it.menuIndex == menuIndex } }
        check(actual.map { it.first } == expected.map { it.menuIndex }) { "Inventory Character menu slot order changed" }
        check(actual.map { it.second.getContainerSlot() } == expected.map { it.containerIndex }) {
            "Inventory Character container slot mapping changed"
        }
    }
}

internal class InventoryCharacterScreen private constructor(
    private val setup: InventoryCharacterScreenSetup,
) : AbstractContainerScreen<InventoryCharacterMenu>(
    setup.menu,
    setup.player.inventory,
    Component.translatable("screen.projects.inventory_character"),
    setup.layout.panel.width,
    setup.layout.panel.height,
) {
    constructor(player: Player) : this(InventoryCharacterScreenSetup(player))

    private val layout = setup.layout
    private var selectedSlotIndex: Int? = null
    private val visibleMenuIndices = inventoryCharacterVisibleSlotMapping().map { it.menuIndex }.toSet()

    override fun isPauseScreen(): Boolean = false

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        extractBlurredBackground(graphics)
        drawPanel(graphics, layout.panel, GLASS_MAIN_TEXTURE, 10)
        drawPanel(graphics, layout.rail, GLASS_SECONDARY_TEXTURE, 10)
        drawPanel(graphics, layout.character, GLASS_SECONDARY_TEXTURE, 10)
        drawPanel(graphics, layout.inventory, GLASS_SECONDARY_TEXTURE, 10)
        drawPanel(graphics, layout.detail, GLASS_DETAIL_TEXTURE, 9)
        drawActiveTab(graphics)
    }

    override fun init() {
        super.init()
        leftPos = layout.panel.x.coerceIn(0, (width - imageWidth).coerceAtLeast(0))
        topPos = layout.panel.y.coerceIn(0, (height - imageHeight).coerceAtLeast(0))
        if (setup.player.containerMenu !== setup.player.inventoryMenu) setup.player.containerMenu = setup.player.inventoryMenu
        if (selectedSlotIndex == null) {
            selectedSlotIndex = visibleSlotEntries().firstOrNull { (_, slot) -> slot.hasItem() }?.first
                ?: visibleMenuIndices.first()
        }
    }

    override fun extractLabels(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) = Unit

    override fun extractSlots(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        syncClientMenuState()
        visibleSlotEntries().forEach { (_, slot) ->
            drawSlotFrame(graphics, slot, mouseX, mouseY)
            extractSlot(graphics, slot, mouseX, mouseY)
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        syncClientMenuState()
        drawRail(graphics, mouseX, mouseY)
        drawCharacter(graphics)
        drawInventory(graphics)
        drawDetail(graphics)
        val preview = inventoryCharacterPreviewBounds(layout.preview)
        InventoryScreen.extractEntityInInventoryFollowsMouse(
            graphics,
            preview.x0,
            preview.y0,
            preview.x1,
            preview.y1,
            PREVIEW_ENTITY_SCALE,
            0.0625f,
            mouseX.toFloat(),
            mouseY.toFloat(),
            setup.player,
        )
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (!containsInteractiveArea(event.x(), event.y())) return true
        syncClientMenuState()
        slotAt(event.x(), event.y())?.let { selectedSlotIndex = menu.slots.indexOf(it) }
        return super.mouseClicked(event, doubleClick)
    }

    override fun slotClicked(slot: Slot, slotIndex: Int, mouseButton: Int, containerInput: ContainerInput) {
        val menuIndex = menu.slots.indexOf(slot).takeIf { it >= 0 } ?: slotIndex
        onMouseClickAction(slot, containerInput)
        val player = minecraft.player ?: return
        val inventoryMenu = player.inventoryMenu
        minecraft.gameMode?.handleContainerInput(
            inventoryMenu.containerId,
            menuIndex,
            mouseButton,
            containerInput,
            player,
        )
        menu.setCarried(inventoryMenu.getCarried())
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (!containsInteractiveArea(event.x(), event.y())) return true
        syncClientMenuState()
        if (!menu.getCarried().isEmpty() && slotAt(event.x(), event.y()) == null) return true
        return super.mouseReleased(event)
    }

    override fun hasClickedOutside(mouseX: Double, mouseY: Double, leftPos: Int, topPos: Int): Boolean =
        !containsInteractiveArea(mouseX, mouseY)

    override fun removed() {
        if (setup.player.containerMenu !== setup.player.inventoryMenu) setup.player.containerMenu = setup.player.inventoryMenu
        super.removed()
    }

    private fun drawActiveTab(graphics: GuiGraphicsExtractor) {
        val plate = if (layout.tiny) {
            HudRect(layout.rail.x + 4, layout.rail.y + 4, 100, 20)
        } else {
            HudRect(
                layout.rail.x + 8,
                layout.rail.y + layout.navTopPadding,
                layout.rail.width - 16,
                layout.navRowHeight,
            )
        }
        if (layout.tiny) {
            drawHorizontalSlice(graphics, plate, NAV_ROW_HOVER_TEXTURE, 6)
        } else {
            drawNineSlice(graphics, plate, NAV_ROW_HOVER_TEXTURE, 6)
        }
    }

    private fun drawRail(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val x = layout.rail.x + 18
        val y = layout.rail.y + if (layout.tiny) 8 else 10
        if (layout.tiny) {
            graphics.text(font, "インベントリ", x, y, 0xFFD0D1CC.toInt(), true)
            graphics.text(font, "キャラクター", x + 92, y, 0xFFB8BAB5.toInt(), false)
            return
        }
        val labels = listOf("インベントリ", "キャラクター", "クエスト", "パーティー", "コーデックス", "マップ", "設定")
        labels.forEachIndexed { index, label ->
            val row = HudRect(
                layout.rail.x + 8,
                layout.rail.y + layout.navTopPadding + index * (layout.navRowHeight + layout.navRowGap),
                layout.rail.width - 16,
                layout.navRowHeight,
            )
            if (index > 0) {
                val texture = if (row.contains(mouseX.toDouble(), mouseY.toDouble())) {
                    NAV_ROW_HOVER_TEXTURE
                } else if (index == labels.lastIndex) {
                    NAV_ROW_DISABLED_TEXTURE
                } else {
                    NAV_ROW_IDLE_TEXTURE
                }
                drawNineSlice(graphics, row, texture, 6)
            }
            graphics.text(
                font,
                label,
                row.x + 50,
                row.y + (row.height - font.lineHeight) / 2,
                if (index == 0) 0xFF4B4D49.toInt() else 0xFFD0D1CC.toInt(),
                index == 0,
            )
        }
    }

    private fun drawCharacter(graphics: GuiGraphicsExtractor) {
        val x = layout.character.x + 20
        val y = layout.character.y + 18
        graphics.text(font, "ProjectS", x, y, 0xFFF0F0EA.toInt(), true)
        val playerName = setup.player.name.string
        graphics.text(font, playerName, x, y + 16, 0xFFB8BAB5.toInt())
        val levelX = x + font.width(playerName) + 8
        drawPixelGlyph(graphics, levelX, y + 18, LEVEL_GLYPH, 0xFFB8BAB5.toInt())
        graphics.text(font, "Lv ${setup.player.experienceLevel}", levelX + 10, y + 16, 0xFFB8BAB5.toInt())
        graphics.text(font, "キャラクター", x, y + 32, 0xFFD0D1CC.toInt(), false)
        val statsY = layout.character.y + layout.character.height - 82
        drawHorizontalSlice(
            graphics,
            HudRect(x, statsY, (layout.character.width - 40).coerceAtLeast(80), 2),
            THIN_HIGHLIGHT_TEXTURE,
            2,
        )
        graphics.text(
            font,
            "ステータス",
            x,
            statsY + 12,
            0xFFF0F0EA.toInt(),
            true,
        )
        drawPixelGlyph(graphics, x, statsY + 29, HEALTH_GLYPH, 0xFFD0D1CC.toInt())
        graphics.text(
            font,
            "体力 ${setup.player.health.toInt()} / ${setup.player.maxHealth.toInt()}",
            x + 12,
            statsY + 28,
            0xFFD0D1CC.toInt(),
            false,
        )
        drawPixelGlyph(graphics, x, statsY + 41, XP_GLYPH, 0xFFB8BAB5.toInt())
        graphics.text(
            font,
            "経験値 ${(setup.player.experienceProgress * 100).toInt().coerceIn(0, 100)}%",
            x + 12,
            statsY + 40,
            0xFFB8BAB5.toInt(),
            false,
        )
    }

    private fun drawInventory(graphics: GuiGraphicsExtractor) {
        val x = layout.inventory.x + 18
        graphics.text(font, "インベントリ", x, layout.inventory.y + 18, 0xFFF0F0EA.toInt(), true)
        if (layout.tiny) {
            graphics.text(font, "アイテム一覧", x, layout.inventory.y + 34, 0xFFB8BAB5.toInt(), false)
            return
        }
        val chips = listOf("すべて" to 52, "武器" to 42, "防具" to 42, "消耗品" to 58)
        var chipX = x
        chips.forEachIndexed { index, (label, chipWidth) ->
            val chip = HudRect(chipX, layout.inventory.y + 46, chipWidth, 20)
            drawHorizontalSlice(
                graphics,
                chip,
                NAV_ROW_HOVER_TEXTURE,
                6,
            )
            graphics.text(
                font,
                label,
                chip.x + (chip.width - font.width(label)) / 2,
                chip.y + 6,
                if (index == 0) 0xFF4B4D49.toInt() else 0xFFD0D1CC.toInt(),
                index == 0,
            )
            chipX += chip.width + 6
        }
    }

    private fun drawDetail(graphics: GuiGraphicsExtractor) {
        val x = layout.detail.x + 18
        val y = layout.detail.y + 16
        graphics.text(font, "選択中のアイテム", x, y, 0xFFF0F0EA.toInt(), true)
        val stack = selectedSlot()?.getItem() ?: ItemStack.EMPTY
        if (stack.isEmpty) {
            graphics.text(font, "アイテムを選択してください", x, y + 28, 0xFFB8BAB5.toInt(), false)
            return
        }

        val presentation = stack.readEquipmentPresentation()
        val iconZone = HudRect(x, y + 30, 64, 64)
        drawPanel(graphics, iconZone, GLASS_SECONDARY_TEXTURE, 6)
        val frameX = iconZone.x + (iconZone.width - 24) / 2
        val frameY = iconZone.y + (iconZone.height - 24) / 2
        blitRegion(graphics, frameX, frameY, 24, 24, 0, 0, 24, 24, ITEM_SLOT_SELECTED_TEXTURE)
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate((iconZone.x + iconZone.width / 2).toFloat(), (iconZone.y + iconZone.height / 2).toFloat())
        pose.scale(2f)
        graphics.item(stack, -8, -8)
        graphics.itemDecorations(font, stack, -8, -8)
        pose.popMatrix()
        val textX = iconZone.x + 78
        graphics.text(font, stack.getHoverName(), textX, iconZone.y + 12, presentation?.let(::rarityColor) ?: 0xFFF0F0EA.toInt(), true)
        if (presentation == null) {
            graphics.text(font, "通常アイテム", textX, iconZone.y + 26, 0xFFB8BAB5.toInt(), false)
            drawHorizontalSlice(
                graphics,
                HudRect(x, y + 104, (layout.detail.width - 36).coerceAtLeast(60), 4),
                DIVIDER_TEXTURE,
                2,
            )
            return
        }
        graphics.text(
            font,
            "${rarityLabel(presentation.rarity)}  |  Lv ${presentation.itemLevel}  |  ${categoryLabel(presentation.category)}",
            textX,
            iconZone.y + 26,
            0xFFB8BAB5.toInt(),
            false,
        )
        drawHorizontalSlice(
            graphics,
            HudRect(x, y + 104, (layout.detail.width - 36).coerceAtLeast(60), 4),
            DIVIDER_TEXTURE,
            2,
        )
        var rowY = y + 120
        val maxRows = ((layout.detail.height - 126) / 12).coerceAtLeast(1)
        if (presentation.baseStats.isNotEmpty()) {
            graphics.text(font, "基本", x, rowY, 0xFFF0F0EA.toInt(), true)
            rowY += 12
        }
        presentation.baseStats.take((maxRows - 1).coerceAtLeast(0)).forEach { stat ->
            graphics.text(font, "${displayId(stat.statId)}  ${formatValue(stat.value)}", x, rowY, 0xFFD0D1CC.toInt(), false)
            rowY += 12
        }
        val remainingRows = (maxRows - 1 - presentation.baseStats.size).coerceAtLeast(0)
        if (presentation.installedMods.isNotEmpty() && remainingRows > 0) {
            graphics.text(font, "付与効果", x, rowY, 0xFFF0F0EA.toInt(), true)
            rowY += 12
            presentation.installedMods.take(remainingRows - 1).forEach { mod ->
                graphics.text(font, "${displayId(mod.modId)} +${formatValue(mod.rolledValue)}", x, rowY, 0xFFE5C878.toInt(), false)
                rowY += 12
            }
        }
    }

    private fun selectedSlot(): Slot? = selectedSlotIndex?.let { index -> menu.slots.getOrNull(index) }

    private fun syncClientMenuState() {
        menu.setCarried(setup.player.inventoryMenu.getCarried())
    }

    private fun containsInteractiveArea(mouseX: Double, mouseY: Double): Boolean =
        layout.panel.contains(mouseX, mouseY)

    private fun slotAt(mouseX: Double, mouseY: Double): Slot? = visibleSlotEntries()
        .firstOrNull { (_, slot) ->
            mouseX >= leftPos + slot.x && mouseX < leftPos + slot.x + SLOT_SIZE &&
                mouseY >= topPos + slot.y && mouseY < topPos + slot.y + SLOT_SIZE
        }?.second

    private fun visibleSlotEntries(): List<Pair<Int, Slot>> = menu.slots.mapIndexed { menuIndex, slot -> menuIndex to slot }
        .filter { (menuIndex, slot) -> menuIndex in visibleMenuIndices && slot.isActive() }

    private fun slotGroup(menuIndex: Int): InventoryCharacterSlotGroup? = inventoryCharacterVisibleSlotMapping()
        .firstOrNull { it.menuIndex == menuIndex }
        ?.group

    private fun drawSlotFrame(graphics: GuiGraphicsExtractor, slot: Slot, mouseX: Int, mouseY: Int) {
        val menuIndex = menu.slots.indexOf(slot)
        val group = slotGroup(menuIndex)
        val equipment = group == InventoryCharacterSlotGroup.ARMOR || group == InventoryCharacterSlotGroup.OFFHAND
        val selected = menuIndex == selectedSlotIndex
        val hovered = slotAt(mouseX.toDouble(), mouseY.toDouble()) === slot
        val texture = if (equipment) {
            if (selected) EQUIPMENT_SLOT_SELECTED_TEXTURE else EQUIPMENT_SLOT_IDLE_TEXTURE
        } else {
            when {
                selected -> ITEM_SLOT_SELECTED_TEXTURE
                hovered -> ITEM_SLOT_HOVER_TEXTURE
                else -> ITEM_SLOT_IDLE_TEXTURE
            }
        }
        blitRegion(graphics, slot.x - 1, slot.y - 1, SLOT_SIZE, SLOT_SIZE, 0, 0, texture.width, texture.height, texture)
    }

    private companion object {
        private val LEVEL_GLYPH = listOf(
            "00100",
            "01110",
            "11111",
            "00100",
            "00100",
            "01110",
            "00100",
        )
        private val HEALTH_GLYPH = listOf(
            "0110110",
            "1111111",
            "1111111",
            "0111110",
            "0011100",
            "0001000",
        )
        private val XP_GLYPH = listOf(
            "0010000",
            "0111000",
            "1111100",
            "0111000",
            "0010000",
        )

        private fun drawPixelGlyph(
            graphics: GuiGraphicsExtractor,
            x: Int,
            y: Int,
            pattern: List<String>,
            color: Int,
        ) {
            pattern.forEachIndexed { row, line ->
                line.forEachIndexed { column, pixel ->
                    if (pixel == '1') graphics.fill(x + column, y + row, x + column + 1, y + row + 1, color)
                }
            }
        }

        fun drawPanel(graphics: GuiGraphicsExtractor, rect: HudRect, texture: InventoryCharacterUiTexture, corner: Int) {
            drawNineSlice(graphics, rect, texture, corner)
        }

        fun drawNineSlice(
            graphics: GuiGraphicsExtractor,
            rect: HudRect,
            texture: InventoryCharacterUiTexture,
            corner: Int,
        ) {
            val centerWidth = (rect.width - corner * 2).coerceAtLeast(1)
            val centerHeight = (rect.height - corner * 2).coerceAtLeast(1)
            val sourceCenterWidth = texture.width - corner * 2
            val sourceCenterHeight = texture.height - corner * 2
            blitRegion(graphics, rect.x, rect.y, corner, corner, 0, 0, corner, corner, texture)
            blitRegion(graphics, rect.x + corner, rect.y, centerWidth, corner, corner, 0, sourceCenterWidth, corner, texture)
            blitRegion(
                graphics,
                rect.x + rect.width - corner,
                rect.y,
                corner,
                corner,
                texture.width - corner,
                0,
                corner,
                corner,
                texture,
            )
            blitRegion(graphics, rect.x, rect.y + corner, corner, centerHeight, 0, corner, corner, sourceCenterHeight, texture)
            blitRegion(
                graphics,
                rect.x + corner,
                rect.y + corner,
                centerWidth,
                centerHeight,
                corner,
                corner,
                sourceCenterWidth,
                sourceCenterHeight,
                texture,
            )
            blitRegion(
                graphics,
                rect.x + rect.width - corner,
                rect.y + corner,
                corner,
                centerHeight,
                texture.width - corner,
                corner,
                corner,
                sourceCenterHeight,
                texture,
            )
            blitRegion(
                graphics,
                rect.x,
                rect.y + rect.height - corner,
                corner,
                corner,
                0,
                texture.height - corner,
                corner,
                corner,
                texture,
            )
            blitRegion(
                graphics,
                rect.x + corner,
                rect.y + rect.height - corner,
                centerWidth,
                corner,
                corner,
                texture.height - corner,
                sourceCenterWidth,
                corner,
                texture,
            )
            blitRegion(
                graphics,
                rect.x + rect.width - corner,
                rect.y + rect.height - corner,
                corner,
                corner,
                texture.width - corner,
                texture.height - corner,
                corner,
                corner,
                texture,
            )
        }

        fun drawHorizontalSlice(
            graphics: GuiGraphicsExtractor,
            rect: HudRect,
            texture: InventoryCharacterUiTexture,
            corner: Int,
        ) {
            val centerWidth = (rect.width - corner * 2).coerceAtLeast(1)
            val sourceCenterWidth = texture.width - corner * 2
            blitRegion(graphics, rect.x, rect.y, corner, rect.height, 0, 0, corner, texture.height, texture)
            blitRegion(
                graphics,
                rect.x + corner,
                rect.y,
                centerWidth,
                rect.height,
                corner,
                0,
                sourceCenterWidth,
                texture.height,
                texture,
            )
            blitRegion(
                graphics,
                rect.x + rect.width - corner,
                rect.y,
                corner,
                rect.height,
                texture.width - corner,
                0,
                corner,
                texture.height,
                texture,
            )
        }

        fun blitRegion(
            graphics: GuiGraphicsExtractor,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            sourceX: Int,
            sourceY: Int,
            sourceWidth: Int,
            sourceHeight: Int,
            texture: InventoryCharacterUiTexture,
        ) {
            val region = inventoryCharacterTextureRegion(
                x,
                y,
                width,
                height,
                sourceX,
                sourceY,
                sourceWidth,
                sourceHeight,
            )
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture.identifier,
                region.x,
                region.y,
                region.sourceX.toFloat(),
                region.sourceY.toFloat(),
                region.width,
                region.height,
                region.sourceWidth,
                region.sourceHeight,
                texture.width,
                texture.height,
            )
        }

        fun ItemStack.readEquipmentPresentation(): EquipmentPresentationSnapshot? {
            val customData = get(DataComponents.CUSTOM_DATA) ?: return null
            val encoded = customData.copyTag().getString(PRESENTATION_TAG).orElse(null) ?: return null
            return runCatching { Base64.getDecoder().decode(encoded) }
                .getOrNull()
                ?.let(EquipmentPresentationCodec::decodeOrNull)
        }

        fun displayId(value: String): String {
            val short = value.substringAfterLast(':').substringAfterLast('/').replace('-', ' ')
            return when (short) {
                "physical attack" -> "物理攻撃"
                "attack speed" -> "攻撃速度"
                "maximum health" -> "最大体力"
                else -> short
            }
        }

        fun rarityLabel(value: String): String = when (value.lowercase(Locale.ROOT)) {
            "common" -> "コモン"
            "uncommon" -> "アンコモン"
            "rare" -> "レア"
            "epic" -> "エピック"
            else -> value
        }

        fun categoryLabel(value: String): String = when (value.lowercase(Locale.ROOT)) {
            "weapon" -> "武器"
            "armor" -> "防具"
            "accessory" -> "アクセサリ"
            else -> value
        }

        fun formatValue(value: Double): String = if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            "%.1f".format(Locale.ROOT, value)
        }

        fun rarityColor(snapshot: EquipmentPresentationSnapshot): Int = when (snapshot.rarity.lowercase(Locale.ROOT)) {
            "uncommon" -> 0xFF6EE7A4.toInt()
            "rare" -> 0xFF6CA8FF.toInt()
            "epic" -> 0xFFC486FF.toInt()
            else -> 0xFFF0F0EA.toInt()
        }
    }
}

private class InventoryCharacterScreenSetup(val player: Player) {
    val layout: InventoryCharacterLayout = inventoryCharacterLayout(
        Minecraft.getInstance().window.guiScaledWidth,
        Minecraft.getInstance().window.guiScaledHeight,
    )
    val menu = InventoryCharacterMenu(player, layout)
}
