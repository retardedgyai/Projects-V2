package dev.projects.server.equipment

import java.util.Collections
import dev.projects.server.mod.ModSlotEntry

enum class EquipmentSlot(val id: String) {
    WEAPON("weapon"), HEAD("head"), CHEST("chest"), LEGS("legs"), BOOTS("boots"),
    NECKLACE("necklace"), RING_1("ring_1"), RING_2("ring_2")
}

enum class EquipmentCategory {
    WEAPON, ARMOR, ACCESSORY;

    fun accepts(slot: EquipmentSlot): Boolean = when (this) {
        WEAPON -> slot == EquipmentSlot.WEAPON
        ARMOR -> slot in setOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.BOOTS)
        ACCESSORY -> slot in setOf(EquipmentSlot.NECKLACE, EquipmentSlot.RING_1, EquipmentSlot.RING_2)
    }
}

enum class EquipmentTier(val minimumItemLevel: Int, val maximumItemLevel: Int) {
    T1(1, 15), T2(16, 30), T3(31, 45);

    fun contains(itemLevel: Int): Boolean = itemLevel in minimumItemLevel..maximumItemLevel
}

enum class EquipmentRarity(val modCapacity: Int) { COMMON(1), UNCOMMON(2), RARE(3), EPIC(4) }

enum class EquipmentQuality { UNSPECIFIED }

data class BaseStatRoll(val statId: String, val value: Double) {
    init {
        requireCanonicalId("statId", statId)
        require(value.isFinite()) { "value must be finite" }
    }
}

data class EquipmentModSlot(val index: Int, val entry: ModSlotEntry? = null) {
    init {
        require(index in 0..3) { "index must be 0..3" }
        require(entry == null || entry.slotIndex == index) { "entry slot index does not match container" }
    }

    companion object { fun empty(index: Int) = EquipmentModSlot(index) }
}

class EquipmentItem(
    val itemId: String,
    val category: EquipmentCategory,
    val slot: EquipmentSlot,
    val tier: EquipmentTier,
    val itemLevel: Int,
    val rarity: EquipmentRarity,
    baseStatRolls: List<BaseStatRoll>,
    modSlots: List<EquipmentModSlot>,
    val quality: EquipmentQuality = EquipmentQuality.UNSPECIFIED
) {
    val baseStatRolls: List<BaseStatRoll> = Collections.unmodifiableList(baseStatRolls.toList())
    val modSlots: List<EquipmentModSlot> = Collections.unmodifiableList(modSlots.toList())

    init {
        require(itemId.isNotEmpty() && itemId.length <= 128 && '\u0000' !in itemId) { "invalid itemId" }
        require(category.accepts(slot)) { "category does not accept slot" }
        require(tier.contains(itemLevel)) { "item level is outside tier" }
        require(modSlots.size == rarity.modCapacity) { "mod capacity mismatch" }
        require(modSlots.map { it.index }.toSet().size == modSlots.size) { "duplicate mod slot" }
        require(modSlots.all { it.index < rarity.modCapacity }) { "mod slot index out of range" }
        require(baseStatRolls.map { it.statId }.toSet().size == baseStatRolls.size) { "duplicate base stat" }
        require(quality == EquipmentQuality.UNSPECIFIED) { "unsupported quality" }
        require(modSlots.all { it.entry == null || it.entry.rank.tier == tier }) { "MOD rank does not match tier" }
    }

}

data class EquipmentValidation(val valid: Boolean, val issues: List<String>)

data class EquipmentStatContribution(val statId: String, val value: Double, val sourceId: String) {
    init {
        requireCanonicalId("statId", statId)
        requireCanonicalId("sourceId", sourceId)
        require(value.isFinite()) { "value must be finite" }
    }
}

typealias EquipmentItemV1 = EquipmentItem

internal fun requireCanonicalId(name: String, value: String) {
    require(Regex("[a-z0-9][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9-]{0,63}").matches(value)) {
        "$name must be a canonical namespaced ID"
    }
}
