package dev.projects.server.coreloop

import java.util.UUID
import kotlin.test.*

class CoreStorageViewTest {
    @Test fun `empty account shows no recipe placeholders`() {
        val account = CoreAccount(UUID.randomUUID(), balances = emptyMap())
        for (tier in 1..4) assertTrue(CoreStorageView.entries(account, tier).isEmpty())
    }

    @Test fun `only positive owned balances and chosen material tier are visible`() {
        val account = CoreAccount(UUID.randomUUID(), balances = mapOf(
            CoreMaterial(CoreResource.WOOD, 1) to 9L,
            CoreMaterial(CoreResource.WOOD, 2) to 4L,
            CoreMaterial(CoreResource.ORE, 2) to 0L,
            CoreMaterial(CoreResource.POTION, 1) to 2L),
            currencies = mapOf(CoreCraftingCurrency.CHAOS to 3L, CoreCraftingCurrency.DIVINE to 0L),
            fragments = mapOf(CoreActivityKind.RIFT to 1L, CoreActivityKind.RITUAL to 0L))
        val result = CoreStorageView.entries(account, 2)
        assertEquals(listOf(
            CoreStorageView.Entry.Material(CoreMaterial(CoreResource.WOOD, 2), 4),
            CoreStorageView.Entry.Material(CoreMaterial(CoreResource.POTION), 2),
            CoreStorageView.Entry.Currency(CoreCraftingCurrency.CHAOS, 3),
            CoreStorageView.Entry.Fragment(CoreActivityKind.RIFT, 1)), result)
        assertTrue(result.all { it.count > 0 })
    }

    @Test fun `all owned categories survive page splitting without duplication`() {
        val account = CoreAccount(UUID.randomUUID(), balances = CoreResource.entries.associate { CoreMaterial(it) to 1L },
            currencies = CoreCraftingCurrency.entries.associateWith { 2L }, fragments = CoreActivityKind.entries.associateWith { 3L })
        val entries = CoreStorageView.entries(account, 1)
        val pages = entries.chunked(21)
        assertEquals(31, entries.size)
        assertEquals(listOf(21, 10), pages.map { it.size })
        assertEquals(entries, pages.flatten())
        assertEquals(entries.size, entries.distinct().size)
        assertFailsWith<IllegalArgumentException> { CoreStorageView.entries(account, 0) }
    }
}
