package dev.projects.client

import net.minecraft.world.entity.EquipmentSlot
import kotlin.math.min

data class InventoryCharacterLayout(
    val panel: HudRect,
    val rail: HudRect,
    val character: HudRect,
    val preview: HudRect,
    val inventory: HudRect,
    val inventoryGrid: HudRect,
    val detail: HudRect,
    val equipmentSlots: Map<EquipmentSlot, HudRect>,
    val offhandSlot: HudRect,
    val compact: Boolean,
    val tiny: Boolean,
)

fun inventoryCharacterLayout(screenWidth: Int, screenHeight: Int): InventoryCharacterLayout {
    require(screenWidth > 0 && screenHeight > 0) { "Inventory viewport must be positive" }

    val panelWidth = min(1120, (screenWidth - 16).coerceAtLeast(280))
    val panelHeight = min(560, (screenHeight - 16).coerceAtLeast(220))
    val panel = HudRect(
        (screenWidth - panelWidth) / 2,
        (screenHeight - panelHeight) / 2,
        panelWidth,
        panelHeight,
    )
    val tiny = panel.width < 520
    val compact = panel.width < 860
    val rail = if (tiny) {
        HudRect(panel.x + 8, panel.y + 8, panel.width - 16, 28)
    } else {
        val railWidth = if (compact) 84 else 104
        HudRect(panel.x + 8, panel.y + 44, railWidth, panel.height - 52)
    }

    val contentLeft = if (tiny) panel.x + 12 else rail.x + rail.width + 12
    val contentRight = panel.x + panel.width - 12
    val contentTop = if (tiny) rail.y + rail.height + 10 else panel.y + 52
    val detailHeight = when {
        tiny -> 58
        compact -> 88
        else -> panel.height - 64
    }.coerceAtMost(panel.height - 72).coerceAtLeast(52)
    val detail = if (tiny || compact) {
        HudRect(
            contentLeft,
            panel.y + panel.height - detailHeight - 12,
            (contentRight - contentLeft).coerceAtLeast(180),
            detailHeight,
        )
    } else {
        HudRect(
            contentRight - 250,
            contentTop,
            250,
            (panel.y + panel.height - contentTop - 12).coerceAtLeast(120),
        )
    }
    val columnsRight = if (tiny || compact) contentRight else detail.x - 12
    val columnsWidth = (columnsRight - contentLeft).coerceAtLeast(174)
    val characterWidth = when {
        tiny -> (columnsWidth - 174 - 12).coerceIn(0, 140)
        compact -> (columnsWidth - 174 - 12).coerceIn(136, 200)
        else -> min(230, columnsWidth - 300 - 12)
    }
    val inventoryWidth = (columnsWidth - characterWidth - 12).coerceAtLeast(162)
    val columnsBottom = if (tiny || compact) detail.y - 12 else panel.y + panel.height - 12
    val columnHeight = (columnsBottom - contentTop).coerceAtLeast(112)
    val character = HudRect(contentLeft, contentTop, characterWidth, columnHeight)
    val inventory = HudRect(contentLeft + characterWidth + 12, contentTop, inventoryWidth, columnHeight)
    val gridWidth = 162
    val gridHeight = 76
    val inventoryGrid = HudRect(
        inventory.x + ((inventory.width - gridWidth) / 2).coerceAtLeast(0),
        inventory.y + 24,
        gridWidth,
        gridHeight,
    )
    val equipmentTop = character.y + 42
    val equipmentX = character.x + 16
    val equipmentSlots = listOf(
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
    ).mapIndexed { index, slot ->
        slot to HudRect(equipmentX, equipmentTop + index * 22, 18, 18)
    }.toMap()
    val offhandSlot = HudRect(
        (character.x + character.width - 34).coerceAtLeast(character.x + 8),
        character.y + character.height - 36,
        18,
        18,
    )

    return InventoryCharacterLayout(
        panel = panel,
        rail = rail,
        character = character,
        preview = HudRect(character.x + 48, character.y + 34, (character.width - 56).coerceAtLeast(52), (character.height - 78).coerceAtLeast(72)),
        inventory = inventory,
        inventoryGrid = inventoryGrid,
        detail = detail,
        equipmentSlots = equipmentSlots,
        offhandSlot = offhandSlot,
        compact = compact,
        tiny = tiny,
    )
}
