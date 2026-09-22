package dev.projects.server.coreloop

import net.minestom.server.component.DataComponents
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.item.ItemStack
import java.util.Locale

/** Native equipment layers for limbs; an actual head item model for the sculpted helmet. */
internal object CoreArmorPresentation {
    fun skin(item: ItemStack, job: CoreClass, tier: Int, packed: Boolean): ItemStack {
        if (!packed) return item
        val equippable = item.get(DataComponents.EQUIPPABLE) ?: return item
        val part = when (equippable.slot()) {
            EquipmentSlot.HELMET -> "helmet"
            EquipmentSlot.CHESTPLATE -> "chestplate"
            EquipmentSlot.LEGGINGS -> "leggings"
            EquipmentSlot.BOOTS -> "boots"
            else -> return item
        }
        val key = "projects:armor/${job.name.lowercase(Locale.ROOT)}_t${tier.coerceIn(1, 4)}"
        // With assetId present Minecraft selects HumanoidArmorLayer and skips
        // CustomHeadLayer. Remove it only for the sculpted helmet, not the body.
        val asset = if (equippable.slot() == EquipmentSlot.HELMET) null else key
        return item.withItemModel("${key}_$part")
            .with(DataComponents.EQUIPPABLE, equippable.withAssetId(asset))
    }
}
