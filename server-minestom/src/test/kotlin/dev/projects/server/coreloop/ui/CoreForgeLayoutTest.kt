package dev.projects.server.coreloop.ui

import dev.projects.server.coreloop.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import java.util.UUID
import kotlin.test.*

class CoreForgeLayoutTest {
    private fun account(vararg balances: Pair<CoreMaterial, Long>) = CoreAccount(UUID.randomUUID(), balances = mapOf(*balances))
    private fun plain(component: Component): String = (component as? TextComponent)?.content().orEmpty() + component.children().joinToString("") { plain(it) }

    @Test fun `forge regions never compete for an actionable six-row hitbox`() {
        val slots = CoreForgeLayout.Tab.entries.map { it.slot } + CoreForgeLayout.RECIPES + CoreForgeLayout.COSTS +
            listOf(CoreForgeLayout.WEAPON, CoreForgeLayout.ARMOR, CoreForgeLayout.TARGET, CoreForgeLayout.RESULT, CoreForgeLayout.DETAIL, CoreForgeLayout.EXECUTE) +
            CoreForgeLayout.QUANTITIES.keys + listOf(1, 4, 7, 8, 13, 36, 45, 46, 50)
        assertEquals(slots.size, slots.distinct().size)
        assertTrue(slots.all { it in 0 until 54 })
        assertTrue(CoreForgeLayout.Tab.entries.all { it.slot % 9 == 0 })
        assertTrue(CoreForgeLayout.RECIPES.all { it % 9 in 1..2 })
        assertTrue(CoreForgeLayout.COSTS.all { it % 9 in 7..8 })
        assertEquals(5, CoreForgeLayout.EXECUTE / 9)
    }

    @Test fun `maximum quantity is limited by every input and by the domain batch cap`() {
        val a = account(CoreMaterial(CoreResource.STONE_BLOCK, 2) to 31L, CoreMaterial(CoreResource.BOARD, 2) to 8L,
            CoreMaterial(CoreResource.LEATHER, 2) to 18L)
        assertEquals(8, CoreForgeLayout.maxBatches(a, CoreLoopCatalog.craft(CoreResource.GATHERING_TABLET, tier = 2)))
        assertEquals(64, CoreForgeLayout.maxBatches(account(CoreMaterial(CoreResource.WOOD) to 500L), CoreLoopCatalog.refine(CoreResource.WOOD, 1)))
        assertEquals(0, CoreForgeLayout.maxBatches(a, CoreLoopCatalog.refine(CoreResource.WOOD, 1)))
    }

    @Test fun `maximum respects output storage space and real potion yield`() {
        val a = account(CoreMaterial(CoreResource.CLOTH, 3) to 50L, CoreMaterial(CoreResource.POTION) to CoreLoopCatalog.MAX_BALANCE - 5)
        assertEquals(2, CoreForgeLayout.maxBatches(a, CoreLoopCatalog.craft(CoreResource.POTION, tier = 3)))
        val full = account(CoreMaterial(CoreResource.WOOD) to 500L, CoreMaterial(CoreResource.BOARD) to CoreLoopCatalog.MAX_BALANCE)
        assertEquals(0, CoreForgeLayout.maxBatches(full, CoreLoopCatalog.refine(CoreResource.WOOD, 1)))
    }

    @Test fun `quantity selection is explicit and never produces invalid zero batch recipes`() {
        assertEquals(1, CoreForgeLayout.batches(CoreForgeLayout.Quantity.ONE, 40))
        assertEquals(5, CoreForgeLayout.batches(CoreForgeLayout.Quantity.FIVE, 1)) // shortage remains visible, never silently makes fewer
        assertEquals(37, CoreForgeLayout.batches(CoreForgeLayout.Quantity.MAX, 37))
        assertEquals(1, CoreForgeLayout.batches(CoreForgeLayout.Quantity.MAX, 0)) // disabled by max=0 on the view
        assertEquals(64, CoreForgeLayout.batches(CoreForgeLayout.Quantity.MAX, 200))
    }

    @Test fun `all currencies have exactly one purpose besides all`() {
        CoreCraftingCurrency.entries.forEach { currency ->
            assertEquals(2, CoreForgeLayout.Purpose.entries.count { it.accepts(currency) })
        }
    }

    @Test fun `main MOD list omits zero owned and currently inapplicable currencies`() {
        val a = CoreAccount(UUID.randomUUID(), currencies = mapOf(
            CoreCraftingCurrency.TRANSMUTATION to 1L, CoreCraftingCurrency.ALCHEMY to 0L,
            CoreCraftingCurrency.EXALTED to 5L, CoreCraftingCurrency.CHAOS to 1L))
        val selection = CoreForgeLayout.Selection(tab = CoreForgeLayout.Tab.MODS)
        assertEquals(listOf(CoreCraftingCurrency.TRANSMUTATION), CoreForgeLayout.usableCurrencies(a, selection))
        assertTrue(CoreForgeLayout.usableCurrencies(a, selection.copy(purpose = CoreForgeLayout.Purpose.ADD)).isEmpty())
    }

    @Test fun `usable currency catalogue fits recipe sockets for every rarity and random legal layout`() {
        val currencies = CoreCraftingCurrency.entries.associateWith { 100L }
        for (tier in 1..4) for (gear in CoreGearSlot.entries) {
            var a = CoreAccount(UUID.randomUUID(), weaponTier = tier, armorTier = tier, currencies = currencies)
            repeat(24) { step ->
                val selection = CoreForgeLayout.Selection(tab = CoreForgeLayout.Tab.MODS, gear = gear)
                val usable = CoreForgeLayout.usableCurrencies(a, selection)
                assertTrue(usable.size <= CoreForgeLayout.RECIPES.size, "Too many usable currencies: $usable")
                if (usable.isNotEmpty()) a = CoreCraftingCatalog.craft(a, gear, usable[step % usable.size], UUID.randomUUID())
            }
        }
    }

    @Test fun `selected armor and recipe tier survive independent tab and quantity changes`() {
        val selection = CoreForgeLayout.Selection(gear = CoreGearSlot.ARMOR, tier = 3, focused = true)
        CoreForgeLayout.Tab.entries.forEach { tab ->
            val next = selection.copy(tab = tab, quantity = CoreForgeLayout.Quantity.MAX)
            assertEquals(CoreGearSlot.ARMOR, next.gear)
            assertEquals(3, next.tier)
        }
    }

    @Test fun `guaranteed normal enhancement never adds useless catalyst costs`() {
        val selected = CoreForgeLayout.Selection(focused = true)
        assertEquals(CoreEnhancementMode.STANDARD, CoreForgeLayout.enhancementMode(account(), selected))
        val uncertain = account().copy(weaponEnhancement = CoreEnhancementState(5))
        assertEquals(CoreEnhancementMode.FOCUSED, CoreForgeLayout.enhancementMode(uncertain, selected))
        val pity = uncertain.copy(weaponEnhancement = CoreEnhancementState(5, 4))
        assertEquals(CoreEnhancementMode.STANDARD, CoreForgeLayout.enhancementMode(pity, selected))
        val skilled = uncertain.copy(smithingXp = 100)
        assertEquals(CoreEnhancementMode.STANDARD, CoreForgeLayout.enhancementMode(skilled, selected))
        val maximum = uncertain.copy(weaponEnhancement = CoreEnhancementState(30))
        assertEquals(CoreEnhancementMode.STANDARD, CoreForgeLayout.enhancementMode(maximum, selected))
    }

    @Test fun `forge title uses dedicated private frame and preserves Japanese plain fallback`() {
        val title = "開拓工房 — 精製"
        assertTrue('\uE201' in plain(CoreUiComponents.inventoryTitle(title, true, forge = true)))
        assertTrue('\uE202' in plain(CoreUiComponents.inventoryTitle(title, true, forge = true, emptyForge = true)))
        val fallback = plain(CoreUiComponents.inventoryTitle(title, false, forge = true, emptyForge = true))
        assertEquals(title, fallback)
        assertFalse(fallback.any { it.code in 0xE000..0xF8FF })
    }

    @Test fun `cost shortcut follows quoted material tier while retaining armor target`() {
        val selected = CoreForgeLayout.Selection(gear = CoreGearSlot.ARMOR, tier = 1, quantity = CoreForgeLayout.Quantity.MAX)
        CoreLoopCatalog.refined.entries.forEachIndexed { index, (_, processed) ->
            val next = assertNotNull(CoreForgeLayout.refineSelection(CoreMaterial(processed, 4), selected))
            assertEquals(CoreForgeLayout.Tab.REFINE, next.tab)
            assertEquals(4, next.tier)
            assertEquals(index, next.recipe)
            assertEquals(CoreGearSlot.ARMOR, next.gear)
            assertEquals(CoreForgeLayout.Quantity.ONE, next.quantity)
        }
        assertNull(CoreForgeLayout.refineSelection(CoreMaterial(CoreResource.AFFIX_DUST), selected))
    }
}
