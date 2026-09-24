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

internal data class FirstMagicState(
    val materialCounts: Map<AnomalousMaterial, Int> = emptyMap(),
    val studied: Set<AnomalousMaterial> = emptySet(),
    val jars: Map<FirstAspect, Int> = emptyMap(),
    val reacted: Boolean = false,
    val deskRestored: Boolean = false,
    val firstDistillation: Boolean = false,
) {
    val hasMaterial: Boolean get() = materialCounts.values.any { it > 0 }
    val knownAspects: Set<FirstAspect> get() = studied.flatMap { it.aspects.keys }.toSet()
    fun count(material: AnomalousMaterial): Int = materialCounts[material] ?: 0
    fun jar(aspect: FirstAspect): Int = jars[aspect] ?: 0
}

internal sealed interface FirstMagicAction {
    data class Collect(val material: AnomalousMaterial) : FirstMagicAction
    data object React : FirstMagicAction
    data object RestoreDesk : FirstMagicAction
    data class Analyze(val material: AnomalousMaterial) : FirstMagicAction
    data class Distill(val material: AnomalousMaterial) : FirstMagicAction
}

internal data class FirstMagicChange(val state: FirstMagicState, val message: String, val changed: Boolean)

internal object FirstMagicRules {
    const val JAR_CAPACITY = 16
    const val MATERIAL_CAPACITY = 64

    fun apply(before: FirstMagicState, action: FirstMagicAction): FirstMagicChange = when (action) {
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
            else -> FirstMagicChange(before.copy(studied = before.studied + action.material),
                "${action.material.label}：${action.material.aspects.entries.joinToString(" / ") { "${it.key.name} ×${it.value}" }} を記録", true)
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
    }
}
