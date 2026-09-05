package dev.projects.server.coreloop.ui

import dev.projects.server.coreloop.*

/** Fixed Vanilla six-row hitboxes and presentation-only forge selection. No mutation authority. */
internal object CoreForgeLayout {
    enum class Tab(val label: String, val slot: Int) {
        ENHANCE("強化", 0), REFINE("精製", 2), CRAFT("制作", 4), MODS("MOD加工", 6)
    }
    enum class Purpose(val label: String) {
        ALL("すべて"), PROMOTE("レアリティ昇格"), ADD("MODを追加"), REROLL("MODを引き直す"), TUNE("数値・初期化");
        fun accepts(currency: CoreCraftingCurrency): Boolean = this == ALL || this == when (currency) {
            CoreCraftingCurrency.TRANSMUTATION, CoreCraftingCurrency.ALCHEMY, CoreCraftingCurrency.REGAL -> PROMOTE
            CoreCraftingCurrency.AUGMENTATION, CoreCraftingCurrency.EXALTED, CoreCraftingCurrency.TRIAL -> ADD
            CoreCraftingCurrency.ALTERATION, CoreCraftingCurrency.CHAOS, CoreCraftingCurrency.RIFT -> REROLL
            CoreCraftingCurrency.DIVINE, CoreCraftingCurrency.RITUAL, CoreCraftingCurrency.SCOURING -> TUNE
        }
    }
    enum class Quantity { ONE, FIVE, MAX }
    data class Selection(
        val tab: Tab = Tab.ENHANCE,
        val gear: CoreGearSlot = CoreGearSlot.WEAPON,
        val tier: Int = 1,
        val recipe: Int = 0,
        val quantity: Quantity = Quantity.ONE,
        val purpose: Purpose = Purpose.ALL,
        val currency: CoreCraftingCurrency? = null,
        val focused: Boolean = false,
    )
    // Each visual button owns every slot under its painted label, not just the icon.
    const val TAB_SPAN = 2
    const val GEAR_SPAN = 3
    const val RECIPE_SPAN = 3
    const val HELP = 8
    val RECIPES = listOf(18, 21, 24, 27, 30, 33, 36, 39)
    const val WEAPON = 9
    const val ARMOR = 12
    const val TIER_PREVIOUS = 15
    const val TIER = 16
    const val TIER_NEXT = 17
    const val MATERIALS = 42
    const val BACK = 45
    const val EXECUTE = 51
    val QUANTITIES = mapOf(47 to Quantity.ONE, 48 to Quantity.FIVE, 49 to Quantity.MAX)

    fun maxBatches(account: CoreAccount, unit: CoreRecipe): Int {
        val affordable = unit.costs.minOfOrNull { (material, cost) ->
            require(cost > 0)
            account.amount(material) / cost
        } ?: 64L
        val room = unit.outputs.minOfOrNull { (material, count) ->
            require(count > 0)
            (CoreLoopCatalog.MAX_BALANCE - account.amount(material)).coerceAtLeast(0) / count
        } ?: 64L
        return minOf(64L, affordable, room).coerceAtLeast(0).toInt()
    }
    fun batches(quantity: Quantity, maximum: Int): Int = when (quantity) {
        Quantity.ONE -> 1
        Quantity.FIVE -> 5
        Quantity.MAX -> maximum.coerceIn(1, 64)
    }
    fun usableCurrencies(account: CoreAccount, selection: Selection): List<CoreCraftingCurrency> =
        CoreCraftingCurrency.entries.filter { selection.purpose.accepts(it) && CoreCraftingCatalog.canUse(account, selection.gear, it) == null }

    fun enhancementMode(account: CoreAccount, selection: Selection): CoreEnhancementMode {
        val standard = CoreEnhancementCatalog.quote(account, selection.gear)
        return if (selection.focused && !standard.guaranteed && standard.currentLevel < CoreEnhancementCatalog.MAX_LEVEL)
            CoreEnhancementMode.FOCUSED else CoreEnhancementMode.STANDARD
    }

    fun refineSelection(material: CoreMaterial, selection: Selection): Selection? {
        val index = CoreLoopCatalog.refined.values.indexOf(material.resource)
        return if (index < 0) null else selection.copy(tab = Tab.REFINE, tier = material.tier, recipe = index, quantity = Quantity.ONE)
    }
}
