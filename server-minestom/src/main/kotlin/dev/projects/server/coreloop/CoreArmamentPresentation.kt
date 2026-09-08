package dev.projects.server.coreloop

import net.minestom.server.component.DataComponents
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.component.CustomModelData

/** Cosmetic model frames only. Never touches the account, damage, use state or skill runtime. */
internal object CoreArmamentPresentation {
    const val FRAMES = 12
    private val families = setOf("greatsword", "staff", "bow", "dagger", "mace", "tome", "astrolabe")
    private val models = families.flatMap { family -> (1..4).map { "projects:weapons/${family}_t$it" } }.toSet()

    fun model(base: CoreWeaponBase, job: CoreClass, tier: Int): String {
        val family = if (base == CoreWeaponBase.STAFF && job == CoreClass.STARWEAVER) "astrolabe" else base.family
        return "projects:weapons/${family}_t${tier.coerceIn(1, 4)}"
    }

    fun frame(item: ItemStack, frame: Int): ItemStack {
        if (item.get(DataComponents.ITEM_MODEL) !in models ||
            CoreLoopItems.gearSlot(item) != CoreGearSlot.WEAPON) return item
        val old = item.get(DataComponents.CUSTOM_MODEL_DATA) ?: CustomModelData(emptyList(), emptyList(), emptyList(), emptyList())
        val value = Math.floorMod(frame, FRAMES).toFloat()
        if (old.floats().firstOrNull() == value) return item
        return item.with(DataComponents.CUSTOM_MODEL_DATA,
            CustomModelData(listOf(value) + old.floats().drop(1), old.flags(), old.strings(), old.colors()))
    }

    fun tick(player: Player, packed: Boolean) {
        // Hand-held equipment only, 10 fps. No background timers or entities.
        // Do not churn container/cursor projections while a menu is open.
        if (!packed || player.instance == null || player.aliveTicks % 2L != 0L || player.openInventory != null) return
        val old = player.itemInMainHand
        val next = frame(old, ((player.aliveTicks / 2) % FRAMES).toInt())
        if (next !== old) player.setItemInMainHand(next)
    }
}
