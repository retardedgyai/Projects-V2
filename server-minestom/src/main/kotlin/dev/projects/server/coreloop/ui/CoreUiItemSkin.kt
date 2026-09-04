package dev.projects.server.coreloop.ui

import net.minestom.server.component.DataComponents
import net.minestom.server.item.ItemStack

object CoreUiItemSkin {
    /** Preserve menu click hitboxes while letting the authored background replace filler glass. */
    fun blank(item: ItemStack, packed: Boolean): ItemStack =
        if (packed) item.withItemModel("projects:core_ui/blank") else item

    /** Namespaced item models only, never replacement textures for every vanilla sword or axe. */
    fun apply(item: ItemStack, icon: CoreUiIcon, packed: Boolean): ItemStack =
        if (packed) item.withItemModel("projects:core_ui/${icon.name.lowercase()}")
        else item.without(DataComponents.ITEM_MODEL)
}
