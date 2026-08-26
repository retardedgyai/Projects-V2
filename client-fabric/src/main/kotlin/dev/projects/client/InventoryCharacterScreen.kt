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
private const val INVENTORY_SLOTS_START = 9

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
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack = player.inventoryMenu.quickMoveStack(player, index)

    override fun stillValid(player: Player): Boolean = true
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

    override fun isPauseScreen(): Boolean = false

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, 0xD90A0E12.toInt())
        drawPanel(graphics, layout.panel, 0xF0131A20.toInt(), 0xFF314047.toInt())
        drawPanel(graphics, layout.rail, 0xE8172228.toInt(), 0xFF26343A.toInt())
        drawPanel(graphics, layout.character, 0xCC172126.toInt(), 0xFF34484C.toInt())
        drawPanel(graphics, layout.inventory, 0xCC172126.toInt(), 0xFF34484C.toInt())
        drawPanel(graphics, layout.detail, 0xCC172126.toInt(), 0xFF34484C.toInt())
    }

    override fun init() {
        super.init()
        leftPos = layout.panel.x.coerceIn(0, (width - imageWidth).coerceAtLeast(0))
        topPos = layout.panel.y.coerceIn(0, (height - imageHeight).coerceAtLeast(0))
        if (setup.player.containerMenu !== menu) setup.player.containerMenu = menu
        if (selectedSlotIndex == null) {
            selectedSlotIndex = menu.slots.firstOrNull { it.index >= INVENTORY_SLOTS_START && it.hasItem() }?.index
                ?: InventoryMenu.ARMOR_SLOT_START
        }
    }

    override fun extractLabels(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) = Unit

    override fun extractSlots(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        menu.slots.asSequence()
            .filter { it.index >= InventoryMenu.ARMOR_SLOT_START && it.index <= InventoryMenu.SHIELD_SLOT }
            .filter { it.isActive() }
            .forEach { extractSlot(graphics, it, mouseX, mouseY) }
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
        slotAt(event.x(), event.y())?.let { selectedSlotIndex = it.index }
        return super.mouseClicked(event, doubleClick)
    }

    override fun hasClickedOutside(mouseX: Double, mouseY: Double, leftPos: Int, topPos: Int): Boolean =
        !layout.panel.contains(mouseX, mouseY)

    override fun removed() {
        if (setup.player.containerMenu === menu) setup.player.containerMenu = setup.player.inventoryMenu
        super.removed()
    }

    private fun drawHeader(graphics: GuiGraphicsExtractor) {
        val x = layout.panel.x + 18
        val y = layout.panel.y + 12
        graphics.text(font, "INVENTORY / CHARACTER", x, y, 0xFFE4F0EB.toInt(), true)
        graphics.text(font, "E to close - drag or click items to move", x, y + 14, 0xFF7F9892.toInt(), false)
    }

    private fun drawRail(graphics: GuiGraphicsExtractor) {
        val x = layout.rail.x + 10
        val y = layout.rail.y + if (layout.tiny) 8 else 14
        if (layout.tiny) {
            graphics.text(font, "INVENTORY", x, y, 0xFFE5C878.toInt(), true)
            graphics.text(font, "CHARACTER", x + 92, y, 0xFF78928A.toInt(), false)
            return
        }
        graphics.text(font, "PROJECTS", x, y, 0xFF7F9892.toInt(), true)
        graphics.text(font, "INVENTORY", x, y + 34, 0xFFE5C878.toInt(), true)
        graphics.text(font, "CHARACTER", x, y + 54, 0xFF9DB2AB.toInt(), false)
        graphics.text(font, "STATS", x, y + 74, 0xFF6F8580.toInt(), false)
        graphics.fill(x - 4, y + 28, x - 1, y + 44, 0xFFE5C878.toInt())
    }

    private fun drawCharacter(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val x = layout.character.x + 12
        val y = layout.character.y + 12
        graphics.text(font, "CHARACTER", x, y, 0xFFE4F0EB.toInt(), true)
        graphics.text(font, setup.player.name, x, y + 14, 0xFF9DB2AB.toInt())
        graphics.text(
            font,
            "HP ${setup.player.health.toInt()} / ${setup.player.maxHealth.toInt()}   ARMOR ${setup.player.getArmorValue()}",
            x,
            layout.character.y + layout.character.height - 16,
            0xFFB5C8C0.toInt(),
            false,
        )
        layout.equipmentSlots.forEach { (slot, position) ->
            val label = slot.getName().uppercase(Locale.ROOT)
            graphics.text(font, label, position.x + 22, position.y + 5, 0xFF718982.toInt(), false)
        }
        graphics.text(font, "OFF", layout.offhandSlot.x - 22, layout.offhandSlot.y + 5, 0xFF718982.toInt(), false)
        slotAt(mouseX.toDouble(), mouseY.toDouble())?.let { hovered ->
            if (hovered.index in InventoryMenu.ARMOR_SLOT_START..InventoryMenu.SHIELD_SLOT) {
                val position = slotPosition(hovered)
                graphics.outline(position.x - 1, position.y - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, 0xFFE5C878.toInt())
            }
        }
    }

    private fun drawInventory(graphics: GuiGraphicsExtractor) {
        val x = layout.inventory.x + 12
        graphics.text(font, "BACKPACK", x, layout.inventory.y + 12, 0xFFE4F0EB.toInt(), true)
        graphics.text(font, "SERVER-SYNCED ITEMS", x, layout.inventory.y + 26, 0xFF718982.toInt(), false)
        graphics.text(
            font,
            "HOTBAR",
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
        graphics.text(font, "SELECTED ITEM", x, y, 0xFFE4F0EB.toInt(), true)
        val stack = selectedSlot()?.getItem() ?: ItemStack.EMPTY
        if (stack.isEmpty) {
            graphics.text(font, "Select an item to inspect", x, y + 28, 0xFF7F9892.toInt(), false)
            return
        }

        graphics.item(stack, x, y + 22)
        val presentation = stack.readEquipmentPresentation()
        val textX = x + 24
        graphics.text(font, stack.getHoverName(), textX, y + 22, presentation?.let(::rarityColor) ?: 0xFFE4F0EB.toInt(), true)
        if (presentation == null) {
            graphics.text(font, "Vanilla item", textX, y + 36, 0xFF7F9892.toInt(), false)
            return
        }
        graphics.text(
            font,
            "${presentation.rarity.uppercase(Locale.ROOT)}  |  Lv ${presentation.itemLevel}  |  ${presentation.category}",
            textX,
            y + 36,
            0xFF9DB2AB.toInt(),
            false,
        )
        var rowY = y + 54
        val maxRows = ((layout.detail.height - 54) / 12).coerceAtLeast(1)
        presentation.baseStats.take(maxRows).forEach { stat ->
            graphics.text(font, "${shortId(stat.statId)}  ${formatValue(stat.value)}", x, rowY, 0xFFB5C8C0.toInt(), false)
            rowY += 12
        }
        presentation.installedMods.take((maxRows - presentation.baseStats.size).coerceAtLeast(0)).forEach { mod ->
            graphics.text(font, "MOD ${shortId(mod.modId)} +${formatValue(mod.rolledValue)}", x, rowY, 0xFFE5C878.toInt(), false)
            rowY += 12
        }
    }

    private fun selectedSlot(): Slot? = selectedSlotIndex?.let { index -> menu.slots.firstOrNull { it.index == index } }

    private fun slotAt(mouseX: Double, mouseY: Double): Slot? = menu.slots.asSequence()
        .filter { it.index >= InventoryMenu.ARMOR_SLOT_START && it.index <= InventoryMenu.SHIELD_SLOT }
        .filter { it.isActive() }
        .firstOrNull { slot ->
            mouseX >= leftPos + slot.x && mouseX < leftPos + slot.x + SLOT_SIZE &&
                mouseY >= topPos + slot.y && mouseY < topPos + slot.y + SLOT_SIZE
        }

    private fun slotPosition(slot: Slot): HudRect = HudRect(leftPos + slot.x, topPos + slot.y, SLOT_SIZE, SLOT_SIZE)

    private companion object {
        fun drawPanel(graphics: GuiGraphicsExtractor, rect: HudRect, fill: Int, border: Int) {
            graphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, fill)
            graphics.outline(rect.x, rect.y, rect.width, rect.height, border)
        }

        fun ItemStack.readEquipmentPresentation(): EquipmentPresentationSnapshot? {
            val customData = get(DataComponents.CUSTOM_DATA) ?: return null
            val encoded = customData.copyTag().getString(PRESENTATION_TAG).orElse(null) ?: return null
            return runCatching { Base64.getDecoder().decode(encoded) }
                .getOrNull()
                ?.let(EquipmentPresentationCodec::decodeOrNull)
        }

        fun shortId(value: String): String = value.substringAfterLast(':').substringAfterLast('/').replace('-', ' ')

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
