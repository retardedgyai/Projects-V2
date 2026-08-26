package dev.projects.client

import dev.projects.protocol.EquipmentPresentationCodec
import dev.projects.protocol.EquipmentPresentationSnapshot
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
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
private const val SLOT_SIZE = 18
private const val UI_TEXTURE_SIZE = 64.0f
private const val GLASS_SOURCE_SIZE = 24
private const val GLASS_CORNER_SIZE = 7
private const val IVORY_SOURCE_X = 32
private const val IVORY_SOURCE_HEIGHT = 16
private val INVENTORY_CHARACTER_TEXTURE = Identifier.fromNamespaceAndPath(
    "projects",
    "textures/gui/inventory_character.png",
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
        addStandardInventorySlots(player.inventory, inventoryGridX, inventoryGridY)
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
        graphics.fill(0, 0, width, height, 0x520A0E12.toInt())
        drawPanel(graphics, layout.panel)
        drawPanel(graphics, layout.rail)
        drawPanel(graphics, layout.character)
        drawPanel(graphics, layout.inventory)
        drawPanel(graphics, layout.detail)
        drawActiveTab(graphics)
    }

    override fun init() {
        super.init()
        leftPos = layout.panel.x.coerceIn(0, (width - imageWidth).coerceAtLeast(0))
        topPos = layout.panel.y.coerceIn(0, (height - imageHeight).coerceAtLeast(0))
        if (setup.player.containerMenu !== menu) setup.player.containerMenu = menu
        if (selectedSlotIndex == null) {
            selectedSlotIndex = visibleSlotEntries().firstOrNull { (_, slot) -> slot.hasItem() }?.first
                ?: visibleMenuIndices.first()
        }
    }

    override fun extractLabels(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) = Unit

    override fun extractSlots(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        visibleSlotEntries().forEach { (_, slot) ->
            drawSlotFrame(graphics, slot)
            extractSlot(graphics, slot, mouseX, mouseY)
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        drawHeader(graphics)
        drawRail(graphics)
        drawCharacter(graphics, mouseX, mouseY)
        drawInventory(graphics)
        drawDetail(graphics)
        InventoryScreen.extractEntityInInventoryFollowsMouse(
            graphics,
            layout.preview.x,
            layout.preview.y,
            layout.preview.width,
            layout.preview.height,
            34,
            0.0625f,
            mouseX.toFloat(),
            mouseY.toFloat(),
            setup.player,
        )
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (!layout.panel.contains(event.x(), event.y())) return true
        slotAt(event.x(), event.y())?.let { selectedSlotIndex = menu.slots.indexOf(it) }
        return super.mouseClicked(event, doubleClick)
    }

    override fun slotClicked(slot: Slot, slotIndex: Int, mouseButton: Int, containerInput: ContainerInput) {
        val menuIndex = menu.slots.indexOf(slot).takeIf { it >= 0 } ?: slotIndex
        onMouseClickAction(slot, containerInput)
        minecraft.gameMode?.handleContainerInput(menu.containerId, menuIndex, mouseButton, containerInput, minecraft.player ?: return)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (!layout.panel.contains(event.x(), event.y())) return true
        if (!menu.getCarried().isEmpty() && slotAt(event.x(), event.y()) == null) return true
        return super.mouseReleased(event)
    }

    override fun hasClickedOutside(mouseX: Double, mouseY: Double, leftPos: Int, topPos: Int): Boolean =
        !layout.panel.contains(mouseX, mouseY)

    override fun removed() {
        if (setup.player.containerMenu === menu) setup.player.containerMenu = setup.player.inventoryMenu
        super.removed()
    }

    private fun drawActiveTab(graphics: GuiGraphicsExtractor) {
        val plate = if (layout.tiny) {
            HudRect(layout.rail.x + 4, layout.rail.y + 4, 100, 20)
        } else {
            HudRect(layout.rail.x + 4, layout.rail.y + 29, layout.rail.width - 8, 20)
        }
        drawNineSlice(graphics, plate, IVORY_SOURCE_X, 0, GLASS_SOURCE_SIZE, IVORY_SOURCE_HEIGHT, 5)
    }

    private fun drawHeader(graphics: GuiGraphicsExtractor) {
        val x = layout.panel.x + 18
        val y = layout.panel.y + 12
        graphics.text(font, "インベントリ / キャラクター", x, y, 0xFFE4F0EB.toInt(), true)
        graphics.text(font, "Eで閉じる - クリック / ドラッグで移動", x, y + 14, 0xFF7F9892.toInt(), false)
    }

    private fun drawRail(graphics: GuiGraphicsExtractor) {
        val x = layout.rail.x + 10
        val y = layout.rail.y + if (layout.tiny) 8 else 14
        if (layout.tiny) {
            graphics.text(font, "インベントリ", x, y, 0xFF394742.toInt(), true)
            graphics.text(font, "キャラクター", x + 92, y, 0xFF78928A.toInt(), false)
            return
        }
        graphics.text(font, "プロジェクトS", x, y, 0xFF7F9892.toInt(), true)
        graphics.text(font, "インベントリ", x, y + 34, 0xFF394742.toInt(), true)
        graphics.text(font, "キャラクター", x, y + 54, 0xFF9DB2AB.toInt(), false)
        graphics.text(font, "ステータス", x, y + 74, 0xFF6F8580.toInt(), false)
    }

    private fun drawCharacter(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val x = layout.character.x + 12
        val y = layout.character.y + 12
        graphics.text(font, "キャラクター", x, y, 0xFFE4F0EB.toInt(), true)
        graphics.text(font, setup.player.name, x, y + 14, 0xFF9DB2AB.toInt())
        graphics.text(
            font,
            "体力 ${setup.player.health.toInt()} / ${setup.player.maxHealth.toInt()}   防御 ${setup.player.getArmorValue()}",
            x,
            layout.character.y + layout.character.height - 16,
            0xFFB5C8C0.toInt(),
            false,
        )
        layout.equipmentSlots.forEach { (slot, position) ->
            val label = equipmentLabel(slot)
            graphics.text(font, label, position.x + 22, position.y + 5, 0xFF718982.toInt(), false)
        }
        graphics.text(font, "左手", layout.offhandSlot.x - 22, layout.offhandSlot.y + 5, 0xFF718982.toInt(), false)
        slotAt(mouseX.toDouble(), mouseY.toDouble())?.let { hovered ->
            if (menu.slots.indexOf(hovered) in visibleMenuIndices) {
                val position = slotPosition(hovered)
                graphics.outline(position.x - 1, position.y - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, 0xFFE5C878.toInt())
            }
        }
    }

    private fun drawInventory(graphics: GuiGraphicsExtractor) {
        val x = layout.inventory.x + 12
        graphics.text(font, "アイテム", x, layout.inventory.y + 12, 0xFFE4F0EB.toInt(), true)
        graphics.text(font, "アイテム一覧", x, layout.inventory.y + 26, 0xFF718982.toInt(), false)
        graphics.text(
            font,
            "ホットバー",
            layout.inventoryGrid.x,
            layout.inventoryGrid.y + SLOT_SIZE * 3 + 10,
            0xFF718982.toInt(),
            false,
        )
        selectedSlot()?.let { selected ->
            val position = slotPosition(selected)
            graphics.outline(position.x - 1, position.y - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, 0xFFE5C878.toInt())
        }
    }

    private fun drawDetail(graphics: GuiGraphicsExtractor) {
        val x = layout.detail.x + 14
        val y = layout.detail.y + 12
        graphics.text(font, "選択中のアイテム", x, y, 0xFFE4F0EB.toInt(), true)
        val stack = selectedSlot()?.getItem() ?: ItemStack.EMPTY
        if (stack.isEmpty) {
            graphics.text(font, "アイテムを選択してください", x, y + 28, 0xFF7F9892.toInt(), false)
            return
        }

        graphics.item(stack, x, y + 22)
        val presentation = stack.readEquipmentPresentation()
        val textX = x + 24
        graphics.text(font, stack.getHoverName(), textX, y + 22, presentation?.let(::rarityColor) ?: 0xFFE4F0EB.toInt(), true)
        if (presentation == null) {
            graphics.text(font, "通常アイテム", textX, y + 36, 0xFF7F9892.toInt(), false)
            return
        }
        graphics.text(
            font,
            "${rarityLabel(presentation.rarity)}  |  Lv ${presentation.itemLevel}  |  ${categoryLabel(presentation.category)}",
            textX,
            y + 36,
            0xFF9DB2AB.toInt(),
            false,
        )
        var rowY = y + 54
        val maxRows = ((layout.detail.height - 54) / 12).coerceAtLeast(1)
        presentation.baseStats.take(maxRows).forEach { stat ->
            graphics.text(font, "${displayId(stat.statId)}  ${formatValue(stat.value)}", x, rowY, 0xFFB5C8C0.toInt(), false)
            rowY += 12
        }
        presentation.installedMods.take((maxRows - presentation.baseStats.size).coerceAtLeast(0)).forEach { mod ->
            graphics.text(font, "付与 ${displayId(mod.modId)} +${formatValue(mod.rolledValue)}", x, rowY, 0xFFE5C878.toInt(), false)
            rowY += 12
        }
    }

    private fun selectedSlot(): Slot? = selectedSlotIndex?.let { index -> menu.slots.getOrNull(index) }

    private fun slotAt(mouseX: Double, mouseY: Double): Slot? = visibleSlotEntries()
        .firstOrNull { (_, slot) ->
            mouseX >= leftPos + slot.x && mouseX < leftPos + slot.x + SLOT_SIZE &&
                mouseY >= topPos + slot.y && mouseY < topPos + slot.y + SLOT_SIZE
        }?.second

    private fun slotPosition(slot: Slot): HudRect = HudRect(leftPos + slot.x, topPos + slot.y, SLOT_SIZE, SLOT_SIZE)

    private fun visibleSlotEntries(): List<Pair<Int, Slot>> = menu.slots.mapIndexed { menuIndex, slot -> menuIndex to slot }
        .filter { (menuIndex, slot) -> menuIndex in visibleMenuIndices && slot.isActive() }

    private companion object {
        fun drawPanel(graphics: GuiGraphicsExtractor, rect: HudRect) {
            drawNineSlice(graphics, rect, 0, 0, GLASS_SOURCE_SIZE, GLASS_SOURCE_SIZE, GLASS_CORNER_SIZE)
        }

        fun drawSlotFrame(graphics: GuiGraphicsExtractor, slot: Slot) {
            blitRegion(graphics, slot.x - 1, slot.y - 1, SLOT_SIZE, SLOT_SIZE, 0, 32, SLOT_SIZE, SLOT_SIZE)
        }

        fun drawNineSlice(
            graphics: GuiGraphicsExtractor,
            rect: HudRect,
            sourceX: Int,
            sourceY: Int,
            sourceWidth: Int,
            sourceHeight: Int,
            corner: Int,
        ) {
            val centerWidth = (rect.width - corner * 2).coerceAtLeast(1)
            val centerHeight = (rect.height - corner * 2).coerceAtLeast(1)
            val sourceCenterWidth = sourceWidth - corner * 2
            val sourceCenterHeight = sourceHeight - corner * 2
            blitRegion(graphics, rect.x, rect.y, corner, corner, sourceX, sourceY, corner, corner)
            blitRegion(graphics, rect.x + corner, rect.y, centerWidth, corner, sourceX + corner, sourceY, sourceCenterWidth, corner)
            blitRegion(graphics, rect.x + rect.width - corner, rect.y, corner, corner, sourceX + sourceWidth - corner, sourceY, corner, corner)
            blitRegion(graphics, rect.x, rect.y + corner, corner, centerHeight, sourceX, sourceY + corner, corner, sourceCenterHeight)
            blitRegion(
                graphics,
                rect.x + corner,
                rect.y + corner,
                centerWidth,
                centerHeight,
                sourceX + corner,
                sourceY + corner,
                sourceCenterWidth,
                sourceCenterHeight,
            )
            blitRegion(
                graphics,
                rect.x + rect.width - corner,
                rect.y + corner,
                corner,
                centerHeight,
                sourceX + sourceWidth - corner,
                sourceY + corner,
                corner,
                sourceCenterHeight,
            )
            blitRegion(graphics, rect.x, rect.y + rect.height - corner, corner, corner, sourceX, sourceY + sourceHeight - corner, corner, corner)
            blitRegion(
                graphics,
                rect.x + corner,
                rect.y + rect.height - corner,
                centerWidth,
                corner,
                sourceX + corner,
                sourceY + sourceHeight - corner,
                sourceCenterWidth,
                corner,
            )
            blitRegion(
                graphics,
                rect.x + rect.width - corner,
                rect.y + rect.height - corner,
                corner,
                corner,
                sourceX + sourceWidth - corner,
                sourceY + sourceHeight - corner,
                corner,
                corner,
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
        ) {
            graphics.blit(
                INVENTORY_CHARACTER_TEXTURE,
                x,
                y,
                width,
                height,
                sourceX / UI_TEXTURE_SIZE,
                sourceY / UI_TEXTURE_SIZE,
                sourceWidth / UI_TEXTURE_SIZE,
                sourceHeight / UI_TEXTURE_SIZE,
            )
        }

        fun ItemStack.readEquipmentPresentation(): EquipmentPresentationSnapshot? {
            val customData = get(DataComponents.CUSTOM_DATA) ?: return null
            val encoded = customData.copyTag().getString(PRESENTATION_TAG).orElse(null) ?: return null
            return runCatching { Base64.getDecoder().decode(encoded) }
                .getOrNull()
                ?.let(EquipmentPresentationCodec::decodeOrNull)
        }

        fun equipmentLabel(slot: EquipmentSlot): String = when (slot) {
            EquipmentSlot.HEAD -> "頭"
            EquipmentSlot.CHEST -> "胴"
            EquipmentSlot.LEGS -> "脚"
            EquipmentSlot.FEET -> "足"
            else -> slot.getName()
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
            else -> 0xFFE4F0EB.toInt()
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
