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
        else vanillaModel(item)

    /** Removing ITEM_MODEL also removes the material's built-in model in modern Vanilla. */
    fun vanillaModel(item: ItemStack): ItemStack =
        ItemStack.of(item.material()).get(DataComponents.ITEM_MODEL)?.let { item.with(DataComponents.ITEM_MODEL, it) }
            ?: item.without(DataComponents.ITEM_MODEL)
}
