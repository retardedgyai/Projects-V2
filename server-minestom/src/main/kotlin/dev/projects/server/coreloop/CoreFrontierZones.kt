package dev.projects.server.coreloop

import dev.projects.server.mob.QuestMobArchetype
import dev.projects.server.questmap.QuestGatheringDiscipline
import dev.projects.server.questmap.QuestTerrainStyle

/**
 * One zone of the 開拓大陸 painting (polish05/atlas-map.json holds only where it is drawn).
 * The zone decides the terrain, the tier, which gathering discipline dominates and which boss
 * waits at the end of the road; every departure still generates a new island from a fresh seed.
 */
internal data class CoreFrontierZone(
    val id: Int,
    val name: String,
    val tier: Int,
    val terrain: QuestTerrainStyle,
    /** Null only for the harbor, which is not an expedition destination. */
    val specialty: QuestGatheringDiscipline?,
    val boss: QuestMobArchetype?,
) {
    val hub: Boolean get() = specialty == null
    init {
        require(tier in 1..4)
        require((specialty == null) == (boss == null))
        require(boss == null || boss in CoreFrontierZones.BOSSES)
    }
}

internal object CoreFrontierZones {
    /** The road bosses that have a quest-map fight; the trial bosses keep their own arenas. */
    val BOSSES = listOf(QuestMobArchetype.EXECUTIONER, QuestMobArchetype.IRON_WARDEN, QuestMobArchetype.RIFT_ORACLE)
    /** Base gathering sites per map (QuestMapQualityGate). */
    const val GATHERING_SITES = 8

    private val V = QuestTerrainStyle.VERDANT
    private val H = QuestTerrainStyle.HIGHLANDS
    private val S = QuestTerrainStyle.SALTMARSH
    private val C = QuestTerrainStyle.CLIFFLANDS
    private val K = QuestTerrainStyle.SAKURA_GROVE
    private val I = QuestTerrainStyle.INFERNAL
    private val SKIN = QuestGatheringDiscipline.SKINNING
    private val WOOD = QuestGatheringDiscipline.WOODCUTTING
    private val QUARRY = QuestGatheringDiscipline.QUARRYING
    private val MINE = QuestGatheringDiscipline.MINING
    private val HERB = QuestGatheringDiscipline.HERBALISM
    private val EXE = QuestMobArchetype.EXECUTIONER
    private val WARDEN = QuestMobArchetype.IRON_WARDEN
    private val ORACLE = QuestMobArchetype.RIFT_ORACLE

    /** Ids match the painted markers. The five wilderness (PvP) markers have no zone until PvP exists. */
    val all: List<CoreFrontierZone> = listOf(
        CoreFrontierZone(33, "開拓港", 1, V, null, null),
        // T1
        CoreFrontierZone(26, "花筏の淵", 1, K, HERB, EXE),
        CoreFrontierZone(27, "泥炭の入江", 1, S, SKIN, WARDEN),
        CoreFrontierZone(32, "春雷の社", 1, K, WOOD, ORACLE),
        // T2
        CoreFrontierZone(12, "狼の背", 2, H, MINE, WARDEN),
        CoreFrontierZone(13, "鷹の巣岩", 2, C, QUARRY, EXE),
        CoreFrontierZone(18, "霧沼", 2, S, HERB, ORACLE),
        CoreFrontierZone(19, "塩の干潟", 2, S, SKIN, EXE),
        CoreFrontierZone(20, "氷柱の谷", 2, H, SKIN, WARDEN),
        CoreFrontierZone(21, "黒鉄の砦", 2, I, MINE, ORACLE),
        CoreFrontierZone(24, "風穴の崖", 2, C, QUARRY, WARDEN),
        CoreFrontierZone(25, "夜桜の参道", 2, K, HERB, ORACLE),
        CoreFrontierZone(30, "吹雪の関所", 2, H, QUARRY, EXE),
        CoreFrontierZone(31, "羊飼いの原", 2, V, SKIN, EXE),
        CoreFrontierZone(35, "緑風の谷", 2, V, WOOD, WARDEN),
        // T3
        CoreFrontierZone(6, "霧氷の城", 3, H, MINE, WARDEN),
        CoreFrontierZone(7, "凍て牙の峰", 3, H, SKIN, EXE),
        CoreFrontierZone(10, "白樫の森", 3, V, WOOD, ORACLE),
        CoreFrontierZone(11, "雪原の砦", 3, H, QUARRY, WARDEN),
        CoreFrontierZone(23, "蛙鳴の湿原", 3, S, HERB, EXE),
        CoreFrontierZone(29, "段々の白壁", 3, C, QUARRY, ORACLE),
        // T4
        CoreFrontierZone(0, "熾火の火口", 4, I, MINE, ORACLE),
        CoreFrontierZone(1, "白崖の見張り塔", 4, C, QUARRY, WARDEN),
        CoreFrontierZone(2, "花影の塔寺", 4, K, HERB, ORACLE),
        CoreFrontierZone(3, "石切りの段丘", 4, C, QUARRY, EXE),
        CoreFrontierZone(4, "風見の丘", 4, V, HERB, EXE),
        CoreFrontierZone(8, "沈み葦の集落", 4, S, SKIN, WARDEN),
        CoreFrontierZone(9, "麦穂の野", 4, V, WOOD, EXE),
        CoreFrontierZone(15, "桜吹雪の峠", 4, K, WOOD, ORACLE),
        CoreFrontierZone(22, "紅霞の庭", 4, K, HERB, WARDEN),
    )
    private val byId = all.associateBy { it.id }
    init { require(byId.size == all.size && all.count { it.hub } == 1) }

    fun zone(id: Int?): CoreFrontierZone? = id?.let(byId::get)
    /** A zone a map may be claimed for: known and not the harbor. */
    fun expedition(id: Int): CoreFrontierZone? = byId[id]?.takeUnless { it.hub }
    val expeditions: List<CoreFrontierZone> = all.filterNot { it.hub }

    /**
     * What departing to [zone] would mean for this account. Mirrors [CoreAction.ClaimMap]: the same level,
     * the same price, the same tier lock; the server still re-checks everything in the transaction.
     */
    fun departure(account: CoreAccount, zone: CoreFrontierZone): CoreFrontierDeparture {
        val level = account.journey.level.coerceIn(CoreJourneyRules.floor(zone.tier), CoreJourneyRules.ceiling(zone.tier))
        val cost = if (zone.tier == 1) null else CoreMaterial(CoreResource.COMBAT_TOKEN, zone.tier - 1)
        val blocked = when {
            zone.hub -> "港は遠征先ではありません"
            zone.tier > account.unlockedMapTier -> "T${zone.tier} は未解放"
            cost != null && account.amount(cost) < 1 -> "戦利品券が足りない"
            else -> null
        }
        return CoreFrontierDeparture(level, cost, blocked)
    }

    /** How the next tier opens today (BossReward at the tier's top level, or the gathering survey). */
    fun unlockHint(tier: Int): String {
        require(tier in 2..4)
        return "T${tier - 1} のボスを敵Lv ${CoreJourneyRules.ceiling(tier - 1)} で倒す"
    }

    /** Base gathering sites per discipline on a map of this specialty, specialty first. */
    fun gatheringSites(specialty: QuestGatheringDiscipline): Map<QuestGatheringDiscipline, Int> {
        val counts = (0 until GATHERING_SITES).map { QuestGatheringDiscipline.forGatheringOrdinal(it, specialty) }
            .groupingBy { it }.eachCount()
        return linkedMapOf(specialty to counts.getValue(specialty)) +
            QuestGatheringDiscipline.entries.filter { it != specialty }.associateWith { counts[it] ?: 0 }
    }
}

internal data class CoreFrontierDeparture(val level: Int, val cost: CoreMaterial?, val blocked: String?)
