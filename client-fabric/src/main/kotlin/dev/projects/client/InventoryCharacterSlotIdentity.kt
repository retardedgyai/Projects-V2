package dev.projects.client

enum class InventoryCharacterSlotGroup {
    ARMOR,
    MAIN,
    HOTBAR,
    OFFHAND,
}

data class InventoryCharacterSlotIdentity(
    val menuIndex: Int,
    val containerIndex: Int,
    val group: InventoryCharacterSlotGroup,
)

internal fun inventoryCharacterVisibleSlotMapping(): List<InventoryCharacterSlotIdentity> = buildList {
    addAll((0..3).map { index ->
        InventoryCharacterSlotIdentity(5 + index, 39 - index, InventoryCharacterSlotGroup.ARMOR)
    })
    addAll((0..26).map { index ->
        InventoryCharacterSlotIdentity(9 + index, 9 + index, InventoryCharacterSlotGroup.MAIN)
    })
    addAll((0..8).map { index ->
        InventoryCharacterSlotIdentity(36 + index, index, InventoryCharacterSlotGroup.HOTBAR)
    })
    add(InventoryCharacterSlotIdentity(45, 40, InventoryCharacterSlotGroup.OFFHAND))
}
