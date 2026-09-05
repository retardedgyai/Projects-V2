package dev.projects.server.coreloop

import java.util.UUID

/** Silver is removed at placement; remaining units therefore have a funded buyer, even offline. */
data class CoreBuyOrder(val id: UUID, val unitPrice: Long, val remaining: Int, val tier: Int,
    val resource: CoreResource? = null, val slot: CoreGearSlot? = null) {
    init {
        require(unitPrice in 1..CoreEconomy.MAX_SILVER && remaining in 1..999 && tier in 1..4)
        require((resource == null) != (slot == null))
        require(resource == null || CoreEconomy.tradeable(resource))
        require(slot == null || remaining <= 16)
        require(escrow <= CoreEconomy.MAX_SILVER)
    }
    val escrow get() = Math.multiplyExact(unitPrice, remaining.toLong())
    val displayName get() = "T$tier ${resource?.displayName ?: slot!!.displayName}"
    fun accepts(item: CoreStoredGear) = item.slot == slot && item.tier == tier && !item.identity.bound && !item.broken && item.enhancement.level == 0
}
data class CoreBuyOrderEntry(val buyer: UUID, val order: CoreBuyOrder)
