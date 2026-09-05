package dev.projects.server.coreloop.ui

import dev.projects.server.coreloop.*
import java.util.Locale

/** Read-only facts for the persistent forge panels; ledger validation remains authoritative. */
internal object CoreForgeSummary {
    data class MaterialRow(val material: CoreMaterial, val required: Long, val owned: Long) {
        init { require(required > 0 && owned >= 0) }
        val missing: Long get() = (required - owned).coerceAtLeast(0)
        val satisfied: Boolean get() = owned >= required
    }

    data class OutputRow(val material: CoreMaterial, val produced: Long, val owned: Long) {
        init { require(produced > 0 && owned in 0..CoreLoopCatalog.MAX_BALANCE) }
        val remainingCapacity: Long get() = CoreLoopCatalog.MAX_BALANCE - owned
        val fits: Boolean get() = produced <= remainingCapacity
    }

    data class RecipeSummary(
        val materials: List<MaterialRow>,
        val outputs: List<OutputRow>,
        val blockedReason: String?,
    )

    data class EnhancementSummary(
        val quote: CoreEnhancementQuote,
        val materials: List<MaterialRow>,
        val levelLabel: String,
        val successLabel: String,
        val protectionLabel: String,
    )

    fun materials(account: CoreAccount, recipe: CoreRecipe): List<MaterialRow> =
        recipe.costs.map { (material, required) -> MaterialRow(material, required, account.amount(material)) }

    fun recipe(account: CoreAccount, recipe: CoreRecipe): RecipeSummary {
        val materials = materials(account, recipe)
        val outputs = recipe.outputs.map { (material, produced) -> OutputRow(material, produced, account.amount(material)) }
        val missing = materials.firstOrNull { !it.satisfied }
        val full = outputs.firstOrNull { !it.fits }
        val blocked = when {
            account.activeRun != null -> "拠点で操作してください"
            missing != null -> "${missing.material.displayName}が${missing.missing}個不足"
            full != null -> "${full.material.displayName}の保管上限を超えます"
            else -> null
        }
        return RecipeSummary(materials, outputs, blocked)
    }

    fun enhancement(account: CoreAccount, selection: CoreForgeLayout.Selection): EnhancementSummary {
        val quote = CoreEnhancementCatalog.quote(account, selection.gear, CoreForgeLayout.enhancementMode(account, selection))
        val maximum = quote.currentLevel == CoreEnhancementCatalog.MAX_LEVEL
        val level = if (maximum) "+${quote.currentLevel}（最大）" else "+${quote.currentLevel} → +${quote.targetLevel}"
        val success = when {
            maximum -> "最大まで強化済み"
            quote.guaranteed -> "成功率 100%・確定"
            else -> "成功率 ${decimal(quote.successChancePercent)}%"
        }
        val protection = when {
            CoreEconomy.broken(account, selection.gear) -> "破損中・装備庫で修理"
            maximum -> "最大強化・MODと強化値を維持"
            quote.guaranteed -> "成功確定・破損なし"
            quote.breakOnFailurePercent > 0 -> "失敗時の破損率 ${decimal(quote.breakOnFailurePercent)}%・修理可能"
            else -> "破損なし・成功保証 ${quote.failures}/${quote.pityThreshold}"
        }
        return EnhancementSummary(quote, materials(account, quote.recipe), level, success, protection)
    }

    /** Needed work, not an automatic execution quantity. Never overflows or hides a zero shortage. */
    fun requiredBatches(missing: Long, outputPerBatch: Long): Int {
        require(missing >= 0 && outputPerBatch > 0)
        val whole = missing / outputPerBatch
        if (whole >= 64) return 64
        return (whole + if (missing % outputPerBatch == 0L) 0 else 1).toInt()
    }

    private fun decimal(value: Double): String = String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")
}
