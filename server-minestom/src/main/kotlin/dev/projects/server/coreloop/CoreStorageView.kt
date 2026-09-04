package dev.projects.server.coreloop

/** An ownership filter, not a recipe catalogue. Empty balances never occupy storage slots. */
internal object CoreStorageView {
    sealed interface Entry {
        val count: Long
        data class Material(val material: CoreMaterial, override val count: Long) : Entry
        data class Currency(val currency: CoreCraftingCurrency, override val count: Long) : Entry
        data class Fragment(val kind: CoreActivityKind, override val count: Long) : Entry
    }

    fun entries(account: CoreAccount, tier: Int): List<Entry> {
        require(tier in 1..4)
        return buildList {
            CoreResource.entries.forEach { resource ->
                val key = CoreMaterial(resource, if (resource in globalResources) 1 else tier)
                account.amount(key).takeIf { it > 0 }?.let { add(Entry.Material(key, it)) }
            }
            CoreCraftingCurrency.entries.forEach { currency ->
                account.amount(currency).takeIf { it > 0 }?.let { add(Entry.Currency(currency, it)) }
            }
            CoreActivityKind.entries.forEach { kind ->
                account.amount(kind).takeIf { it > 0 }?.let { add(Entry.Fragment(kind, it)) }
            }
        }
    }

    private val globalResources = setOf(CoreResource.POTION, CoreResource.GATHERING_TABLET, CoreResource.WHETSTONE, CoreResource.AFFIX_DUST)
}
