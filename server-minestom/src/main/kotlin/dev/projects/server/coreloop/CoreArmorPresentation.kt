package dev.projects.server.coreloop

import net.minestom.server.component.DataComponents
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.item.ItemStack
import java.util.Locale

/** Native equipment layers for limbs; an actual head item model for the sculpted helmet. */
internal object CoreArmorPresentation {
    /**
     * 狂戦士の甲冑 (warrior ultimate): the worn armor shows as the berserker armor (scripts/build_berserker_armor.py)
     * until [show]; the real pieces are kept aside on the player and put back exactly. Transient, never saved.
     */
    private val berserkTag = net.minestom.server.tag.Tag.Transient<Map<EquipmentSlot, ItemStack>>("projects_berserk_armor")
    private val pieces = listOf(EquipmentSlot.HELMET, EquipmentSlot.CHESTPLATE, EquipmentSlot.LEGGINGS, EquipmentSlot.BOOTS)
    fun berserk(player: net.minestom.server.entity.Player) {
        if (player.hasTag(berserkTag)) return
        val kept = pieces.associateWith { player.getEquipment(it) }
        player.setTag(berserkTag, kept)
        for ((slot, item) in kept) {
            val equippable = item.get(DataComponents.EQUIPPABLE) ?: continue
            player.setEquipment(slot, if (slot == EquipmentSlot.HELMET) item.withItemModel("projects:armor/berserker_helmet")
                else item.with(DataComponents.EQUIPPABLE, equippable.withAssetId("projects:armor/berserker")))
        }
    }
    fun show(player: net.minestom.server.entity.Player) {
        val kept = player.getTag(berserkTag) ?: return
        player.removeTag(berserkTag)
        for ((slot, item) in kept) player.setEquipment(slot, item)
    }

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
