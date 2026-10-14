package dev.projects.server.questmap

internal enum class QuestMapGatheringStat(val id: String, val displayName: String) {
    AMOUNT("amount", "生成量"),
    QUALITY("quality", "品質"),
    DENSE_REGIONS("dense_regions", "密集地域出現率"),
}

internal data class QuestMapGatheringModifier(
    val discipline: QuestGatheringDiscipline?,
    val stat: QuestMapGatheringStat,
    val percent: Int,
) {
    init {
        require(percent in 1..200)
    }

    val key: String = "${discipline?.id ?: "all"}:${stat.id}"

    fun displayName(): String {
        val target = when (stat) {
            QuestMapGatheringStat.DENSE_REGIONS -> discipline?.displayName ?: "全資源"
            QuestMapGatheringStat.AMOUNT, QuestMapGatheringStat.QUALITY ->
                discipline?.commonResourceName ?: "全採取物"
        }
        return "${target}の${stat.displayName} +$percent%"
    }
}

internal data class QuestMapCustomization(
    val modifiers: List<QuestMapGatheringModifier> = emptyList(),
) {
    init {
        require(modifiers.size <= MAX_MODIFIERS)
        require(modifiers.map { it.key }.distinct().size == modifiers.size)
    }

    fun amountBonusPercent(discipline: QuestGatheringDiscipline): Int = bonus(discipline, QuestMapGatheringStat.AMOUNT)

    fun qualityBonusPercent(discipline: QuestGatheringDiscipline): Int = bonus(discipline, QuestMapGatheringStat.QUALITY)

    fun denseRegionBonusPercent(discipline: QuestGatheringDiscipline): Int =
        bonus(discipline, QuestMapGatheringStat.DENSE_REGIONS)

    private fun bonus(discipline: QuestGatheringDiscipline, stat: QuestMapGatheringStat): Int = modifiers
        .filter { it.stat == stat && (it.discipline == null || it.discipline == discipline) }
        .sumOf { it.percent }

    companion object {
        const val MAX_MODIFIERS = 3
        val NONE = QuestMapCustomization()
    }
}

internal data class QuestMapItemData(
    val seed: Long,
    val customization: QuestMapCustomization = QuestMapCustomization.NONE,
)

internal object QuestMapItems {
    fun applyTablet(data: QuestMapItemData, roll: Long): QuestMapItemData? {
        if (data.customization.modifiers.size >= QuestMapCustomization.MAX_MODIFIERS) return null
        val existing = data.customization.modifiers.mapTo(hashSetOf()) { it.key }
        val candidates = buildList {
            QuestMapGatheringStat.entries.forEach { stat -> add(null to stat) }
            QuestGatheringDiscipline.entries.forEach { discipline ->
                QuestMapGatheringStat.entries.forEach { stat -> add(discipline to stat) }
            }
        }.filterNot { (discipline, stat) -> "${discipline?.id ?: "all"}:${stat.id}" in existing }
        if (candidates.isEmpty()) return null
        val mixed = mix64(roll xor data.seed xor (data.customization.modifiers.size * 0x9E3779B97F4A7C15UL.toLong()))
        val (discipline, stat) = candidates[Math.floorMod(mixed, candidates.size.toLong()).toInt()]
        val magnitudeRoll = Math.floorMod(mix64(mixed xor 0x517CC1B727220A95L), 10_000L).toInt()
        val percent = when {
            discipline == null && stat == QuestMapGatheringStat.AMOUNT -> 10 + magnitudeRoll % 11
            discipline == null -> 8 + magnitudeRoll % 8
            stat == QuestMapGatheringStat.AMOUNT -> 25 + magnitudeRoll % 26
            stat == QuestMapGatheringStat.DENSE_REGIONS -> 25 + magnitudeRoll % 26
            else -> 20 + magnitudeRoll % 21
        }
        return data.copy(
            customization = QuestMapCustomization(
                data.customization.modifiers + QuestMapGatheringModifier(discipline, stat, percent),
            ),
        )
    }

    private fun mix64(value: Long): Long {
        var mixed = value
        mixed = (mixed xor (mixed ushr 30)) * -4_658_895_280_553_007_687L
        mixed = (mixed xor (mixed ushr 27)) * -7_723_592_293_110_705_685L
        return mixed xor (mixed ushr 31)
    }
}
