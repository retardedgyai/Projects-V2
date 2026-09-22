package dev.projects.server.coreloop

import java.util.UUID

/** Identity follows the equipment through equip, storage and sale; old equipment migrates bound. */
data class CoreGearIdentity(val id: UUID, val crafter: UUID, val bound: Boolean = false, val quality: Int = 0,
    val base: CoreWeaponBase = CoreWeaponBase.STANDARD, val itemLevel: Int = 0) {
    init { require(quality in 0..30 && itemLevel in 0..40) }
    companion object {
        fun legacy(player: UUID, slot: CoreGearSlot) = CoreGearIdentity(
            UUID.nameUUIDFromBytes("$player/legacy/$slot".toByteArray(Charsets.UTF_8)), player, true)
    }
}

class CoreStoredGear(
    val identity: CoreGearIdentity, val slot: CoreGearSlot, val tier: Int,
    val rarity: CoreGearRarity, val enhancement: CoreEnhancementState,
    affixes: List<CoreEquippedAffix> = emptyList(), val legacy: Boolean = false, val broken: Boolean = false,
) {
    val affixes = java.util.Collections.unmodifiableList(affixes.toList())
    init { require(tier in 1..4 && affixes.size <= 6 && affixes.all { it.gear == slot }) }
    fun project(a: CoreAccount): CoreAccount = a.copy(
        storedGear = a.storedGear.filterNot { it.identity.id == identity.id },
        offers = a.offers.filterNot { it.gearId == identity.id },
        weaponBroken = if (slot == CoreGearSlot.WEAPON) broken else a.weaponBroken,
        armorBroken = if (slot == CoreGearSlot.ARMOR) broken else a.armorBroken,
        weaponTier = if (slot == CoreGearSlot.WEAPON) tier else a.weaponTier,
        armorTier = if (slot == CoreGearSlot.ARMOR) tier else a.armorTier,
        weaponRarity = if (slot == CoreGearSlot.WEAPON) rarity else a.weaponRarity,
        armorRarity = if (slot == CoreGearSlot.ARMOR) rarity else a.armorRarity,
        weaponEnhancement = if (slot == CoreGearSlot.WEAPON) enhancement else a.weaponEnhancement,
        armorEnhancement = if (slot == CoreGearSlot.ARMOR) enhancement else a.armorEnhancement,
        equippedAffixes = a.equippedAffixes.filterNot { it.gear == slot } + affixes,
        legacyLayouts = (a.legacyLayouts - slot) + if (legacy) setOf(slot) else emptySet(),
        weaponIdentity = if (slot == CoreGearSlot.WEAPON) identity else a.weaponIdentity,
        armorIdentity = if (slot == CoreGearSlot.ARMOR) identity else a.armorIdentity,
    )
    val displayName get() = (if (broken) "【破損】" else "") + "T$tier Lv${CoreJourneyRules.itemLevel(identity, tier)} ${if (slot == CoreGearSlot.WEAPON) identity.base.displayName else slot.displayName} +${enhancement.level}"
}

/** A listing owns its material escrow, or locks exactly one stored equipment identity. */
data class CoreMarketOffer(val id: UUID, val price: Long, val material: CoreMaterial? = null,
    val quantity: Long = 1, val gearId: UUID? = null) {
    init {
        require(price in 1..CoreEconomy.MAX_SILVER && quantity in 1..999)
        require((material == null) != (gearId == null))
        require(gearId == null || quantity == 1L)
        require(material == null || CoreEconomy.tradeable(material.resource))
    }
}
data class CoreMarketEntry(val seller: UUID, val offer: CoreMarketOffer, val gear: CoreStoredGear?)

object CoreEconomy {
    const val MAX_SILVER = 1_000_000_000L
    const val MAX_GEAR = 108
    const val MAX_OFFERS = 24
    const val DAILY_DELIVERIES = 3
    fun tradeable(resource: CoreResource) = resource.raw || resource in CoreLoopCatalog.refined.values
    fun identity(a: CoreAccount, slot: CoreGearSlot) = if (slot == CoreGearSlot.WEAPON) a.weaponIdentity else a.armorIdentity
    fun broken(a: CoreAccount, slot: CoreGearSlot) = if (slot == CoreGearSlot.WEAPON) a.weaponBroken else a.armorBroken
    // Base variants share their family's repair supply, not equipment from unrelated classes.
    fun repairCompatible(a: CoreAccount, slot: CoreGearSlot, item: CoreStoredGear) = item.slot == slot &&
        item.tier == CoreAffixCatalog.gearTier(a, slot) && !item.identity.bound && item.enhancement.level == 0 &&
        !item.broken && (slot != CoreGearSlot.WEAPON || item.identity.base.family == a.weaponIdentity.base.family)
    fun repairInput(a: CoreAccount, slot: CoreGearSlot, item: CoreStoredGear) =
        repairCompatible(a, slot, item) && a.offers.none { it.gearId == item.identity.id }
    fun capture(a: CoreAccount, slot: CoreGearSlot) = CoreStoredGear(identity(a, slot), slot,
        CoreAffixCatalog.gearTier(a, slot), CoreAffixCatalog.rarity(a, slot), CoreEnhancementCatalog.state(a, slot),
        a.equippedAffixes.filter { it.gear == slot }, slot in a.legacyLayouts, broken(a, slot))
    fun manufacture(slot: CoreGearSlot, tier: Int, base: CoreWeaponBase = CoreWeaponBase.STANDARD): CoreRecipe {
        require(tier in 1..4)
        val main = if (slot == CoreGearSlot.WEAPON) CoreResource.INGOT else CoreResource.LEATHER
        val support = if (slot == CoreGearSlot.WEAPON) CoreResource.BOARD else CoreResource.CLOTH
        val costs = mutableMapOf(CoreMaterial(main, tier) to 4L, CoreMaterial(support, tier) to 2L, CoreMaterial(CoreResource.STONE_BLOCK, tier) to 1L)
        if (slot == CoreGearSlot.WEAPON && base != CoreWeaponBase.STANDARD) {
            val special = when (base) { CoreWeaponBase.FLOW, CoreWeaponBase.LONGBOW -> CoreResource.BOARD; CoreWeaponBase.CLEAVER -> CoreResource.INGOT; else -> CoreResource.CLOTH }
            costs.merge(CoreMaterial(special, tier), 2L, Long::plus)
        }
        // Lower-tier processing remains useful after entering a new generation, without consuming old gear.
        for (lower in 1 until tier) costs[CoreMaterial(main, lower)] = 1L
        return CoreRecipe("T$tier ${if (slot == CoreGearSlot.WEAPON) base.displayName else slot.displayName}を制作", costs, emptyMap())
    }
    fun deliveryPrice(tier: Int) = 80L * tier * tier
    fun fee(price: Long) = (price + 19) / 20 // 5%, rounded up; visible before purchase/listing.
}
