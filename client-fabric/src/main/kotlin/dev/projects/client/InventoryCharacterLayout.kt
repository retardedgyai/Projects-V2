package dev.projects.client

import net.minecraft.world.entity.EquipmentSlot
import kotlin.math.min
import kotlin.math.roundToInt

internal const val INVENTORY_CHARACTER_SLOT_SIZE = 18
internal const val INVENTORY_CHARACTER_NAV_ROW_HEIGHT = 60
internal const val INVENTORY_CHARACTER_NAV_ROW_GAP = 12
internal const val INVENTORY_CHARACTER_NAV_TOP_PADDING = 18

private const val MOCK_WIDTH = 1672f
private const val MOCK_HEIGHT = 941f
private const val CANONICAL_MIN_SCALE = 0.43f
private const val CANONICAL_SLOT_STEP = 24
private const val LEGACY_SLOT_STEP = 18

data class InventoryCharacterLayout(
    val panel: HudRect,
    val rail: HudRect,
    val character: HudRect,
    val preview: HudRect,
    val inventory: HudRect,
    val inventoryGrid: HudRect,
    val detail: HudRect,
    val navRowHeight: Int,
    val navRowGap: Int,
    val navTopPadding: Int,
    val equipmentSlots: Map<EquipmentSlot, HudRect>,
    val offhandSlot: HudRect,
    val slotStep: Int,
    val compact: Boolean,
    val tiny: Boolean,
)

fun inventoryCharacterLayout(screenWidth: Int, screenHeight: Int): InventoryCharacterLayout {
    require(screenWidth > 0 && screenHeight > 0) { "Inventory viewport must be positive" }

    val transform = InventoryCharacterLayoutTransform(screenWidth, screenHeight)
    if (transform.scale < CANONICAL_MIN_SCALE) return legacyInventoryCharacterLayout(screenWidth, screenHeight)

    val panel = transform.rect(HudRect(116, 166, 1438, 588))
    val rail = transform.rect(HudRect(142, 186, 192, 532))
    val character = transform.rect(HudRect(346, 186, 356, 532))
    val inventory = transform.rect(HudRect(714, 186, 494, 532))
    val detail = transform.rect(HudRect(1220, 186, 310, 532))
    val gridWidth = CANONICAL_SLOT_STEP * 8 + INVENTORY_CHARACTER_SLOT_SIZE
    val gridHeight = CANONICAL_SLOT_STEP * 3 + INVENTORY_CHARACTER_SLOT_SIZE
    val gridX = (inventory.x + (inventory.width - gridWidth) / 2)
        .coerceIn(inventory.x, (inventory.x + inventory.width - gridWidth).coerceAtLeast(inventory.x))
    val inventoryGrid = HudRect(gridX, transform.y(320), gridWidth, gridHeight)
    val equipmentSlots = listOf(
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
    ).mapIndexed { index, slot ->
        val x = if (index < 2) 366 else 664
        val y = 294 + (index % 2) * 58
        slot to transform.slot(x, y)
    }.toMap()

    return InventoryCharacterLayout(
        panel = panel,
        rail = rail,
        character = character,
        preview = transform.rect(HudRect(388, 252, 272, 306)),
        inventory = inventory,
        inventoryGrid = inventoryGrid,
        detail = detail,
        navRowHeight = transform.size(INVENTORY_CHARACTER_NAV_ROW_HEIGHT),
        navRowGap = transform.size(INVENTORY_CHARACTER_NAV_ROW_GAP),
        navTopPadding = transform.size(INVENTORY_CHARACTER_NAV_TOP_PADDING),
        equipmentSlots = equipmentSlots,
        offhandSlot = transform.slot(664, 410),
        slotStep = CANONICAL_SLOT_STEP,
        compact = transform.scale < 0.75f,
        tiny = false,
    )
}

private fun legacyInventoryCharacterLayout(screenWidth: Int, screenHeight: Int): InventoryCharacterLayout {
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
    val gridWidth = LEGACY_SLOT_STEP * 8 + INVENTORY_CHARACTER_SLOT_SIZE
    val gridHeight = LEGACY_SLOT_STEP * 3 + INVENTORY_CHARACTER_SLOT_SIZE + 4
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
        navRowHeight = INVENTORY_CHARACTER_NAV_ROW_HEIGHT / 3,
        navRowGap = INVENTORY_CHARACTER_NAV_ROW_GAP / 2,
        navTopPadding = 10,
        equipmentSlots = equipmentSlots,
        offhandSlot = offhandSlot,
        slotStep = LEGACY_SLOT_STEP,
        compact = compact,
        tiny = tiny,
    )
}

private data class InventoryCharacterLayoutTransform(
    val screenWidth: Int,
    val screenHeight: Int,
) {
    val scale: Float = min(screenWidth / MOCK_WIDTH, screenHeight / MOCK_HEIGHT)
    private val offsetX: Float = (screenWidth - MOCK_WIDTH * scale) / 2f
    private val offsetY: Float = (screenHeight - MOCK_HEIGHT * scale) / 2f

    fun x(value: Int): Int = (offsetX + value * scale).roundToInt()

    fun y(value: Int): Int = (offsetY + value * scale).roundToInt()

    fun size(value: Int): Int = (value * scale).roundToInt().coerceAtLeast(1)

    fun rect(rect: HudRect): HudRect = HudRect(
        x(rect.x),
        y(rect.y),
        (rect.width * scale).roundToInt().coerceAtLeast(1),
        (rect.height * scale).roundToInt().coerceAtLeast(1),
    )

    fun slot(x: Int, y: Int): HudRect = HudRect(
        x(x),
        y(y),
        INVENTORY_CHARACTER_SLOT_SIZE,
        INVENTORY_CHARACTER_SLOT_SIZE,
    )
}
