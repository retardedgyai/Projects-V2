package dev.projects.server.coreloop

import java.util.UUID

/** Silver is removed at placement; remaining units therefore have a funded buyer, even offline. */
data class CoreBuyOrder(val id: UUID, val unitPrice: Long, val remaining: Int, val tier: Int,
    val resource: CoreResource? = null, val slot: CoreGearSlot? = null,
    val family: String? = if (slot == CoreGearSlot.WEAPON) "greatsword" else null) {
    init {
        require(unitPrice in 1..CoreEconomy.MAX_SILVER && remaining in 1..999 && tier in 1..4)
        require((resource == null) != (slot == null))
        require(resource == null || CoreEconomy.tradeable(resource))
        require(slot == null || remaining <= 16)
        require(escrow <= CoreEconomy.MAX_SILVER)
        require(if (slot == CoreGearSlot.WEAPON) family in setOf("greatsword", "bow", "staff") else family == null)
    }
    val escrow get() = Math.multiplyExact(unitPrice, remaining.toLong())
    val displayName get() = "T$tier ${resource?.displayName ?: when (family) { "greatsword" -> "大剣"; "bow" -> "長弓"; "staff" -> "杖"; else -> slot!!.displayName }}"
    fun accepts(item: CoreStoredGear) = item.slot == slot && item.tier == tier && !item.identity.bound && !item.broken && item.enhancement.level == 0 && (family == null || item.identity.base.family == family)
}
data class CoreBuyOrderEntry(val buyer: UUID, val order: CoreBuyOrder)
