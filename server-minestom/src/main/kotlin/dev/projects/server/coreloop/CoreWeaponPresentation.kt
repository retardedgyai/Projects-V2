package dev.projects.server.coreloop

import dev.projects.server.coreloop.ui.CoreUiItemSkin
import net.minestom.server.item.ItemStack
import kotlin.math.roundToInt

/** Equipment sheet numbers deliberately use the same clamps as authoritative combat. */
internal object CoreWeaponPresentation {
    fun damage(account: CoreAccount): Int {
        return CoreCombatSheet.from(account).ad.roundToInt()
    }

    fun attackSpeedPercent(account: CoreAccount): Double = if (account.weaponBroken) 0.0 else
        (account.weaponIdentity.base.speed * (1.0 + (CoreAffixCatalog.stats(account).attackSpeedPercent.coerceIn(0.0, 60.0) +
            CoreEnhancementCatalog.weaponAttackSpeedPercent(account.weaponEnhancement.level)) / 100.0) - 1.0) * 100.0

    fun attackSpeedLabel(account: CoreAccount): String =
        "${if (attackSpeedPercent(account) >= 0) "+" else ""}${(attackSpeedPercent(account) * 10).roundToInt() / 10.0}%"

    fun health(account: CoreAccount): Int = CoreCombatSheet.from(account).health.toInt()

    fun skin(item: ItemStack, tier: Int, packed: Boolean): ItemStack = if (packed)
        item.withItemModel("projects:weapons/greatsword_t${tier.coerceIn(1, 4)}")
    else CoreUiItemSkin.vanillaModel(item)
}
