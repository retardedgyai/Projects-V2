package dev.projects.server.coreloop

import java.util.UUID

/** Identity follows the equipment through equip, storage and sale; old equipment migrates bound. */
data class CoreGearIdentity(val id: UUID, val crafter: UUID, val bound: Boolean = false) {
    companion object {
        fun legacy(player: UUID, slot: CoreGearSlot) = CoreGearIdentity(
            UUID.nameUUIDFromBytes("$player/legacy/$slot".toByteArray(Charsets.UTF_8)), player, true)
    }
}

class CoreStoredGear(
    val identity: CoreGearIdentity, val slot: CoreGearSlot, val tier: Int,
    val rarity: CoreGearRarity, val enhancement: CoreEnhancementState,
    affixes: List<CoreEquippedAffix> = emptyList(), val legacy: Boolean = false, val condition: Int = 100,
) {
    val affixes = java.util.Collections.unmodifiableList(affixes.toList())
    init { require(tier in 1..4 && condition in 0..100 && affixes.size <= 6 && affixes.all { it.gear == slot }) }
    fun project(a: CoreAccount): CoreAccount = a.copy(
        storedGear = a.storedGear.filterNot { it.identity.id == identity.id },
        offers = a.offers.filterNot { it.gearId == identity.id },
        weaponCondition = if (slot == CoreGearSlot.WEAPON) condition else a.weaponCondition,
        armorCondition = if (slot == CoreGearSlot.ARMOR) condition else a.armorCondition,
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
    val displayName get() = "T$tier ${slot.displayName} +${enhancement.level}"
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
    fun condition(a: CoreAccount, slot: CoreGearSlot) = if (slot == CoreGearSlot.WEAPON) a.weaponCondition else a.armorCondition
    fun repairInput(a: CoreAccount, slot: CoreGearSlot, item: CoreStoredGear) = item.slot == slot &&
        item.tier == CoreAffixCatalog.gearTier(a, slot) && !item.identity.bound && item.enhancement.level == 0 &&
        item.affixes.isEmpty() && item.rarity == CoreGearRarity.NORMAL && item.condition == 100 && a.offers.none { it.gearId == item.identity.id }
    fun capture(a: CoreAccount, slot: CoreGearSlot) = CoreStoredGear(identity(a, slot), slot,
        CoreAffixCatalog.gearTier(a, slot), CoreAffixCatalog.rarity(a, slot), CoreEnhancementCatalog.state(a, slot),
        a.equippedAffixes.filter { it.gear == slot }, slot in a.legacyLayouts, condition(a, slot))
    fun manufacture(slot: CoreGearSlot, tier: Int): CoreRecipe {
        require(tier in 1..4)
        val main = if (slot == CoreGearSlot.WEAPON) CoreResource.INGOT else CoreResource.LEATHER
        val support = if (slot == CoreGearSlot.WEAPON) CoreResource.BOARD else CoreResource.CLOTH
        return CoreRecipe("T$tier ${slot.displayName}を制作", mapOf(CoreMaterial(main, tier) to 4L,
            CoreMaterial(support, tier) to 2L, CoreMaterial(CoreResource.STONE_BLOCK, tier) to 1L), emptyMap())
    }
    fun deliveryPrice(tier: Int) = 80L * tier * tier
    fun fee(price: Long) = (price + 19) / 20 // 5%, rounded up; visible before purchase/listing.
}
