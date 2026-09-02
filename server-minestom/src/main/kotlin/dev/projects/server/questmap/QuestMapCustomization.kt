package dev.projects.server.questmap

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag

internal val QUEST_MAP_ITEM_TAG: Tag<String> = Tag.String("projects_quest_map")
internal val QUEST_GATHERING_TABLET_TAG: Tag<String> = Tag.String("projects_gathering_tablet")

internal enum class QuestMapGatheringStat(val id: String, val displayName: String) {
    AMOUNT("amount", "生成量"),
    QUALITY("quality", "品質"),
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

    fun displayName(): String =
        "${discipline?.commonResourceName ?: "全採取物"}の${stat.displayName} +$percent%"
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
    private const val SCHEMA = "v1"
    private const val TABLET_SCHEMA = "gathering_v1"

    fun questMap(data: QuestMapItemData): ItemStack {
        val modifierLore = if (data.customization.modifiers.isEmpty()) {
            listOf(Component.text("MODなし", NamedTextColor.DARK_GRAY))
        } else {
            data.customization.modifiers.map { modifier ->
                Component.text(modifier.displayName(), NamedTextColor.AQUA)
            }
        }
        return ItemStack.builder(Material.FILLED_MAP)
            .customName(Component.text("クエストマップ", NamedTextColor.GOLD))
            .lore(
                listOf(Component.text("seed: ${data.seed}", NamedTextColor.DARK_GRAY)) +
                    modifierLore +
                    listOf(
                        Component.empty(),
                        Component.text("右クリック: マップを開始", NamedTextColor.YELLOW),
                        Component.text("インベントリで石板を重ねるとMODを付与", NamedTextColor.GRAY),
                    ),
            )
            .build()
            .withTag(QUEST_MAP_ITEM_TAG, encode(data))
    }

    fun gatheringTablet(): ItemStack = ItemStack.builder(Material.AMETHYST_SHARD)
        .customName(Component.text("採取の石板", NamedTextColor.LIGHT_PURPLE))
        .lore(
            Component.text("クエストマップへランダムな採取MODを1つ付与する", NamedTextColor.GRAY),
            Component.text("インベントリでつかみ、クエストマップをクリック", NamedTextColor.YELLOW),
            Component.text("最大${QuestMapCustomization.MAX_MODIFIERS}MOD", NamedTextColor.DARK_GRAY),
        )
        .glowing(true)
        .build()
        .withTag(QUEST_GATHERING_TABLET_TAG, TABLET_SCHEMA)

    fun read(item: ItemStack): QuestMapItemData? = item.getTag(QUEST_MAP_ITEM_TAG)?.let(::decode)

    fun isGatheringTablet(item: ItemStack): Boolean = item.getTag(QUEST_GATHERING_TABLET_TAG) == TABLET_SCHEMA

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
            discipline == null && stat == QuestMapGatheringStat.QUALITY -> 8 + magnitudeRoll % 8
            stat == QuestMapGatheringStat.AMOUNT -> 25 + magnitudeRoll % 26
            else -> 20 + magnitudeRoll % 21
        }
        return data.copy(
            customization = QuestMapCustomization(
                data.customization.modifiers + QuestMapGatheringModifier(discipline, stat, percent),
            ),
        )
    }

    private fun encode(data: QuestMapItemData): String = buildString {
        append(SCHEMA).append(';').append(data.seed)
        data.customization.modifiers.forEach { modifier ->
            append(';')
                .append(modifier.discipline?.id ?: "all").append(',')
                .append(modifier.stat.id).append(',')
                .append(modifier.percent)
        }
    }

    private fun decode(raw: String): QuestMapItemData? = runCatching {
        val fields = raw.split(';')
        require(fields.size in 2..2 + QuestMapCustomization.MAX_MODIFIERS)
        require(fields[0] == SCHEMA)
        val modifiers = fields.drop(2).map { encoded ->
            val parts = encoded.split(',')
            require(parts.size == 3)
            val discipline = if (parts[0] == "all") null else {
                QuestGatheringDiscipline.entries.single { it.id == parts[0] }
            }
            val stat = QuestMapGatheringStat.entries.single { it.id == parts[1] }
            QuestMapGatheringModifier(discipline, stat, parts[2].toInt())
        }
        QuestMapItemData(fields[1].toLong(), QuestMapCustomization(modifiers))
    }.getOrNull()

    private fun mix64(value: Long): Long {
        var mixed = value
        mixed = (mixed xor (mixed ushr 30)) * -4_658_895_280_553_007_687L
        mixed = (mixed xor (mixed ushr 27)) * -7_723_592_293_110_705_685L
        return mixed xor (mixed ushr 31)
    }
}
