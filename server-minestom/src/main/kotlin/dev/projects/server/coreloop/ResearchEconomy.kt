package dev.projects.server.coreloop

import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

/** The anomalous sample is a market good of the same name in the core ledger. */
internal val AnomalousMaterial.resource: CoreResource get() = CoreResource.valueOf(name)

internal object ResearchGoods {
    fun sample(resource: CoreResource): AnomalousMaterial? = AnomalousMaterial.entries.firstOrNull { it.name == resource.name }
    /** Samples are always tier 1, so one market pool serves every gatherer and researcher. */
    fun material(sample: AnomalousMaterial) = CoreMaterial(sample.resource)
    fun counts(account: CoreAccount?): Map<AnomalousMaterial, Int> = if (account == null) emptyMap() else
        AnomalousMaterial.entries.associateWith { account.amount(it.resource).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }.filterValues { it > 0 }
    fun effect(product: CoreResource): String = when (product) {
        CoreResource.ARCANE_CATALYST -> "強化の「触媒」で使用：成功率+${CoreMmoTuning.balance.researchCatalystBonusPercent}（通常の触媒は+15）"
        CoreResource.MENDING_SEAL -> "破損装備をTier×${CoreMmoTuning.balance.researchSealsPerTier}個で修理（予備装備不要）"
        else -> "研究素材。分析・研究インク・蒸留に使う"
    }
}

/** One personal unlock per research. The unlock is not tradeable; what it lets you make is. */
internal data class ResearchRecipe(
    val researchId: String,
    val product: CoreResource,
    val essentia: Map<FirstAspect, Int>,
    val materials: Map<CoreMaterial, Long>,
    val output: Long = 1,
) {
    fun essentiaLabel() = essentia.entries.joinToString(" / ") { "${it.key.name} ${it.value}" }
    fun ledgerRecipe() = CoreRecipe("${product.displayName}を調合", materials, mapOf(CoreMaterial(product) to output))
}

internal object ResearchRecipes {
    private val ingot = CoreMaterial(CoreResource.INGOT, 1)
    private val cloth = CoreMaterial(CoreResource.CLOTH, 1)
    private fun catalyst(id: String, vararg essentia: Pair<FirstAspect, Int>) =
        ResearchRecipe(id, CoreResource.ARCANE_CATALYST, mapOf(*essentia), mapOf(ingot to 1L))
    private fun seal(id: String, vararg essentia: Pair<FirstAspect, Int>) =
        ResearchRecipe(id, CoreResource.MENDING_SEAL, mapOf(*essentia), mapOf(cloth to 1L))

    /** Which research you solve decides which Essentia (and so which samples) your products consume. */
    val all = listOf(
        catalyst("lamp", FirstAspect.EMBER to 2, FirstAspect.GALE to 2),
        seal("living_ink", FirstAspect.TIDE to 2, FirstAspect.STONE to 2),
        catalyst("storm_glass", FirstAspect.GALE to 3, FirstAspect.TIDE to 1),
        catalyst("ore_sight", FirstAspect.STONE to 3, FirstAspect.EMBER to 1),
        seal("frost_seal", FirstAspect.TIDE to 3, FirstAspect.GALE to 1),
        seal("void_lens", FirstAspect.GALE to 2, FirstAspect.TIDE to 2),
        catalyst("kinetic_rune", FirstAspect.GALE to 2, FirstAspect.STONE to 2),
        seal("venom_filter", FirstAspect.TIDE to 2, FirstAspect.EMBER to 2),
        catalyst("ember_engine", FirstAspect.EMBER to 3, FirstAspect.STONE to 1),
        seal("last_bloom", FirstAspect.STONE to 3, FirstAspect.TIDE to 1),
    )
    val byResearch = all.associateBy(ResearchRecipe::researchId)

    init {
        require(byResearch.keys == ResearchCatalog.byId.keys) { "Every research needs exactly one product recipe" }
        require(all.all { it.product.research && it.essentia.values.all { amount -> amount in 1..FirstMagicRules.JAR_CAPACITY } })
    }
}

internal data class ResearchStep(val change: FirstMagicChange, val ledger: CoreTransactionResult?) {
    val committed: Boolean get() = change.changed && (ledger == null || ledger.successful)
    val message: String get() = if (ledger != null && !ledger.successful) ledger.message else change.message
}

/**
 * Runs on the single ledger executor, so the sidecar and the ledger never interleave with other writes.
 * The ledger commits first (goods and silver leave), then the sidecar. A preview guarantees the sidecar
 * step is valid before anything is spent.
 */
internal class ResearchDesk(private val ledger: CoreAccountService, private val magic: FirstMagicService) {
    fun ledgerAction(action: FirstMagicAction): CoreAction? = when (action) {
        is FirstMagicAction.Distill -> CoreAction.ResearchDistill(action.material.resource)
        is FirstMagicAction.BrewInk -> CoreAction.ResearchInk(action.material.resource)
        is FirstMagicAction.Craft -> CoreAction.ResearchCraft(action.researchId)
        else -> null
    }

    fun perform(playerId: UUID, action: FirstMagicAction): ResearchStep {
        if (action == FirstMagicAction.PrepareResearchTest) return prepareResearchTest(playerId)
        val account = ledger.snapshot(playerId)
            ?: return ResearchStep(FirstMagicChange(FirstMagicState(), "データを読み込めません", false), null)
        val preview = magic.preview(playerId, action, ResearchGoods.counts(account))
        if (!preview.changed) return ResearchStep(preview, null)
        val spend = ledgerAction(action)
        val paid = spend?.let { ledger.transact(playerId, CoreOperation(UUID.randomUUID(), account.revision, it)) }
        if (paid != null && !paid.successful) return ResearchStep(preview, paid)
        magic.commit(playerId, preview.state)
        return ResearchStep(preview, paid)
    }

    /** Dev-only: tops samples up to four each and silver to 300, then refills ink. */
    private fun prepareResearchTest(playerId: UUID): ResearchStep {
        val account = ledger.snapshot(playerId)
            ?: return ResearchStep(FirstMagicChange(FirstMagicState(), "データを読み込めません", false), null)
        val missing = AnomalousMaterial.entries.associate { it.resource to (4L - account.amount(it.resource)) }.filterValues { it > 0 }
        val granted = ledger.transact(playerId, CoreOperation(UUID.randomUUID(), account.revision,
            CoreAction.GrantResearchMaterials(missing, silverFloor = 300)))
        if (!granted.successful) return ResearchStep(FirstMagicChange(FirstMagicState(), granted.message, false), granted)
        val preview = magic.preview(playerId, FirstMagicAction.PrepareResearchTest, ResearchGoods.counts(granted.account))
        if (preview.changed) magic.commit(playerId, preview.state)
        return ResearchStep(preview.copy(changed = true, message = "研究テスト用に素材4個ずつ・銀貨300枚・研究インクを補充した"), granted)
    }

    /**
     * Saves before this change kept samples in the sidecar. Move them into the ledger once: the fixed request id
     * leaves a receipt, so a crash between the two saves never grants twice.
     */
    fun migrateLegacyMaterials(playerId: UUID): CoreTransactionResult? {
        val legacy = magic.snapshot(playerId)?.materialCounts.orEmpty().filterValues { it > 0 }
        if (legacy.isEmpty()) return null
        val account = requireNotNull(ledger.snapshot(playerId)) { "Ledger is not open" }
        val requestId = migrationRequest(playerId)
        val result = if (requestId in account.receipts) null else ledger.transact(playerId, CoreOperation(requestId, account.revision,
            CoreAction.GrantResearchMaterials(legacy.entries.associate { it.key.resource to it.value.toLong() })))
        if (result != null && !result.successful) return result
        magic.clearLegacyMaterials(playerId)
        return result
    }

    companion object {
        fun migrationRequest(playerId: UUID): UUID = UUID.nameUUIDFromBytes("first-magic/material-migration/$playerId".toByteArray(UTF_8))
    }
}
