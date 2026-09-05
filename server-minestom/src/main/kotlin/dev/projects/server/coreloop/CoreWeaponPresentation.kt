package dev.projects.server.coreloop

import dev.projects.server.coreloop.ui.CoreUiItemSkin
import net.minestom.server.item.ItemStack
import kotlin.math.roundToInt

/** Equipment sheet numbers deliberately use the same clamps as authoritative combat. */
internal object CoreWeaponPresentation {
    fun damage(account: CoreAccount): Int {
        if (account.weaponBroken) return 0
        val stats = CoreAffixCatalog.stats(account)
        return (12 * CoreLoopCatalog.weaponDamage(account.weaponTier) *
            CoreEnhancementCatalog.weaponDamageMultiplier(account.weaponEnhancement.level) *
            (1 + stats.damagePercent / 100)).roundToInt()
    }

    fun attackSpeedPercent(account: CoreAccount): Double = if (account.weaponBroken) 0.0 else
        CoreAffixCatalog.stats(account).attackSpeedPercent.coerceIn(0.0, 60.0) +
            CoreEnhancementCatalog.weaponAttackSpeedPercent(account.weaponEnhancement.level)

    fun attackSpeedLabel(account: CoreAccount): String =
        "+${(attackSpeedPercent(account) * 10).roundToInt() / 10.0}%"

    fun health(account: CoreAccount): Int =
        (if (account.armorBroken) 100 else (CoreLoopCatalog.armorHealth(account.armorTier) *
            CoreEnhancementCatalog.armorHealthMultiplier(account.armorEnhancement.level)).toInt()) +
            CoreAffixCatalog.stats(account).healthFlat.toInt()

    fun skin(item: ItemStack, tier: Int, packed: Boolean): ItemStack = if (packed)
        item.withItemModel("projects:weapons/greatsword_t${tier.coerceIn(1, 4)}")
    else CoreUiItemSkin.vanillaModel(item)
}
