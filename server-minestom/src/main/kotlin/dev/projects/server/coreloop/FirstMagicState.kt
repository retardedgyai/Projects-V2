package dev.projects.server.coreloop

internal enum class FirstAspect(val label: String) {
    EMBER("Ember / 熾火"), TIDE("Tide / 潮"), GALE("Gale / 風"), STONE("Stone / 石")
}

internal enum class AnomalousMaterial(
    val label: String,
    val aspects: Map<FirstAspect, Int>,
) {
    MOONBELL("月鈴草", mapOf(FirstAspect.TIDE to 2, FirstAspect.GALE to 1)),
    EMBER_MOSS("熾火苔", mapOf(FirstAspect.EMBER to 2, FirstAspect.STONE to 1)),
    HOLLOW_CRYSTAL("空洞水晶", mapOf(FirstAspect.GALE to 2, FirstAspect.STONE to 1)),
    WARM_ORE("微熱鉱片", mapOf(FirstAspect.EMBER to 2, FirstAspect.STONE to 1)),
    TIDEWING_FEATHER("潮羽", mapOf(FirstAspect.GALE to 2, FirstAspect.TIDE to 1)),
    WITHERED_CORE("枯れた核", mapOf(FirstAspect.STONE to 2, FirstAspect.EMBER to 1));
}

internal fun AnomalousMaterial.researchAspects(): List<String> = when (this) {
    AnomalousMaterial.MOONBELL -> listOf("aqua", "aer", "ordo")
    AnomalousMaterial.EMBER_MOSS -> listOf("ignis", "terra")
    AnomalousMaterial.HOLLOW_CRYSTAL -> listOf("aer", "perditio")
    AnomalousMaterial.WARM_ORE -> listOf("ignis", "terra", "ordo")
    AnomalousMaterial.TIDEWING_FEATHER -> listOf("aer", "aqua")
    AnomalousMaterial.WITHERED_CORE -> listOf("terra", "perditio")
}

internal data class FirstMagicState(
    val materialCounts: Map<AnomalousMaterial, Int> = emptyMap(),
    val studied: Set<AnomalousMaterial> = emptySet(),
    val jars: Map<FirstAspect, Int> = emptyMap(),
    val reacted: Boolean = false,
    val deskRestored: Boolean = false,
    val firstDistillation: Boolean = false,
    val discoveredResearchAspects: Set<String> = emptySet(),
    val researchInk: Map<String, Int> = emptyMap(),
    val researchPlacements: Map<String, Map<Int, String>> = emptyMap(),
    val unlockedResearch: Set<String> = emptySet(),
) {
    val hasMaterial: Boolean get() = materialCounts.values.any { it > 0 }
    val knownAspects: Set<FirstAspect> get() = studied.flatMap { it.aspects.keys }.toSet()
    fun count(material: AnomalousMaterial): Int = materialCounts[material] ?: 0
    fun jar(aspect: FirstAspect): Int = jars[aspect] ?: 0
    fun ink(aspect: String): Int = researchInk[aspect] ?: 0
}

internal sealed interface FirstMagicAction {
    data object PrepareResearchTest : FirstMagicAction
    data class Collect(val material: AnomalousMaterial) : FirstMagicAction
    data object React : FirstMagicAction
    data object RestoreDesk : FirstMagicAction
    data class Analyze(val material: AnomalousMaterial) : FirstMagicAction
    data class Distill(val material: AnomalousMaterial) : FirstMagicAction
    data class Combine(val first: String, val second: String) : FirstMagicAction
    data class Place(val researchId: String, val slot: Int, val aspectId: String) : FirstMagicAction
    data class Remove(val researchId: String, val slot: Int) : FirstMagicAction
}

internal data class FirstMagicChange(val state: FirstMagicState, val message: String, val changed: Boolean)

internal object FirstMagicRules {
    const val JAR_CAPACITY = 16
    const val MATERIAL_CAPACITY = 64

    fun apply(before: FirstMagicState, action: FirstMagicAction): FirstMagicChange = when (action) {
        FirstMagicAction.PrepareResearchTest -> {
            val next = before.copy(
                materialCounts = before.materialCounts + AnomalousMaterial.entries.associateWith { maxOf(before.count(it), 1) },
                reacted = true,
                deskRestored = true,
                researchInk = before.researchInk + before.discoveredResearchAspects.associateWith { maxOf(before.ink(it), 16) },
            )
            FirstMagicChange(next, "研究テスト用の素材と研究インクを補充した", next != before)
        }
        is FirstMagicAction.Collect -> {
            val count = before.count(action.material)
            if (count >= MATERIAL_CAPACITY) FirstMagicChange(before, "素材袋がいっぱいです", false)
            else FirstMagicChange(before.copy(materialCounts = before.materialCounts + (action.material to count + 1)),
                "${action.material.label}を拾った。コロニーの古い研究区画に持ち帰ろう", true)
        }
        FirstMagicAction.React -> when {
            before.reacted -> FirstMagicChange(before, "研究区画の紙が静かに光っている", false)
            !before.hasMaterial -> FirstMagicChange(before, "古い研究区画は眠ったまま。遠征で異質素材を探そう", false)
            else -> FirstMagicChange(before.copy(reacted = true), "持ち帰った素材に古い装置が反応した。研究机を修復できそうだ", true)
        }
        FirstMagicAction.RestoreDesk -> when {
            before.deskRestored -> FirstMagicChange(before, "研究机は起動している", false)
            !before.reacted -> FirstMagicChange(before, "この机を起こす手がかりがまだない", false)
            else -> FirstMagicChange(before.copy(deskRestored = true), "研究机が起動した。素材を観測盤に置こう", true)
        }
        is FirstMagicAction.Analyze -> when {
            !before.deskRestored -> FirstMagicChange(before, "先に研究机を修復しよう", false)
            before.count(action.material) == 0 -> FirstMagicChange(before, "${action.material.label}を持っていない", false)
            action.material in before.studied -> FirstMagicChange(before, "${action.material.label}は記録済み", false)
            else -> {
                val aspects = action.material.researchAspects()
                FirstMagicChange(before.copy(
                    studied = before.studied + action.material,
                    discoveredResearchAspects = before.discoveredResearchAspects + aspects,
                    researchInk = before.researchInk + aspects.associateWith { before.ink(it) + 8 },
                ),
                "${action.material.label}：${action.material.aspects.entries.joinToString(" / ") { "${it.key.name} ×${it.value}" }} を記録", true)
            }
        }
        is FirstMagicAction.Distill -> when {
            !before.deskRestored -> FirstMagicChange(before, "研究机を起動してから蒸留器を使おう", false)
            action.material !in before.studied -> FirstMagicChange(before, "先に${action.material.label}を分析しよう", false)
            before.count(action.material) == 0 -> FirstMagicChange(before, "${action.material.label}が足りない", false)
            action.material.aspects.any { (aspect, amount) -> before.jar(aspect) + amount > JAR_CAPACITY } ->
                FirstMagicChange(before, "対応するJarがいっぱい。蒸留を中止した", false)
            else -> FirstMagicChange(before.copy(
                materialCounts = if (before.count(action.material) == 1) before.materialCounts - action.material
                    else before.materialCounts + (action.material to before.count(action.material) - 1),
                jars = before.jars + action.material.aspects.mapValues { (aspect, amount) -> before.jar(aspect) + amount },
                firstDistillation = true,
            ), "${action.material.label}からEssentiaを抽出。Jarに保存した", true)
        }
        is FirstMagicAction.Combine -> {
            val result = AspectCatalog.combine(action.first, action.second)
            when {
                !before.deskRestored -> FirstMagicChange(before, "研究机を先に修復しよう", false)
                result == null -> FirstMagicChange(before, "この二つから生まれるAspectはまだ見つからない", false)
                action.first !in before.discoveredResearchAspects || action.second !in before.discoveredResearchAspects ->
                    FirstMagicChange(before, "両方のAspectを先に発見しよう", false)
                before.ink(action.first) < 1 || before.ink(action.second) < 1 ->
                    FirstMagicChange(before, "組み合わせる研究インクが足りない", false)
                else -> FirstMagicChange(before.copy(
                    discoveredResearchAspects = before.discoveredResearchAspects + result.id,
                    researchInk = before.researchInk + mapOf(
                        action.first to before.ink(action.first) - 1,
                        action.second to before.ink(action.second) - 1,
                        result.id to before.ink(result.id) + 4,
                    ),
                ), "${result.name} を発見。研究インクを4得た", true)
            }
        }
        is FirstMagicAction.Place -> {
            val research = ResearchCatalog.byId[action.researchId]
            val occupied = before.researchPlacements[action.researchId].orEmpty()
            when {
                !before.deskRestored -> FirstMagicChange(before, "研究机を先に修復しよう", false)
                research == null || action.slot !in ResearchBoard.cells || action.slot in research.anchors ||
                    action.aspectId !in AspectCatalog.byId -> FirstMagicChange(before, "置けない場所です", false)
                action.researchId in before.unlockedResearch -> FirstMagicChange(before, "研究は完了している", false)
                action.aspectId !in before.discoveredResearchAspects || before.ink(action.aspectId) < 1 ->
                    FirstMagicChange(before, "そのAspectの研究インクが足りない", false)
                action.slot in occupied -> FirstMagicChange(before, "先に置いたAspectを外そう", false)
                else -> {
                    val next = occupied + (action.slot to action.aspectId)
                    val completed = ResearchBoard.complete(research, next)
                    FirstMagicChange(before.copy(
                        researchInk = before.researchInk + (action.aspectId to before.ink(action.aspectId) - 1),
                        researchPlacements = before.researchPlacements + (research.id to next),
                        unlockedResearch = if (completed) before.unlockedResearch + research.id else before.unlockedResearch,
                    ), if (completed) "研究「${research.title}」を解明した" else "${AspectCatalog.byId.getValue(action.aspectId).name} を記した", true)
                }
            }
        }
        is FirstMagicAction.Remove -> {
            val research = ResearchCatalog.byId[action.researchId]
            val occupied = before.researchPlacements[action.researchId].orEmpty()
            val removed = occupied[action.slot]
            when {
                research == null || removed == null || action.researchId in before.unlockedResearch ->
                    FirstMagicChange(before, "外せるAspectがない", false)
                else -> FirstMagicChange(before.copy(
                    researchInk = before.researchInk + (removed to before.ink(removed) + 1),
                    researchPlacements = before.researchPlacements + (research.id to (occupied - action.slot)),
                ), "Aspectを外し、研究インクを戻した", true)
            }
        }
    }
}
