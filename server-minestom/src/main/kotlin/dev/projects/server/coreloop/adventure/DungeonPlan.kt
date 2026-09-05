package dev.projects.server.coreloop.adventure

import dev.projects.server.coreloop.*
import net.minestom.server.coordinate.Pos
import kotlin.random.Random

enum class DungeonTheme(val displayName: String) { EMBER("灰燼の工廟"), TIDE("蒼潮の書庫"), ASTRAL("星蝕の王庭") }
enum class DungeonRoomKind(val displayName: String, val description: String) {
    BATTLE("守衛の間", "守衛を倒して加護を得る"), ELITE("精鋭の間", "強敵との戦闘・加護が強い"),
    TREASURE("宝庫", "三波を突破して混沌のオーブ"), SEALS("封印の間", "三つの封印を起動・近くで解読"),
    REST("憩いの間", "泉で回復して加護を選ぶ"), BOSS("守護者", "階層の主を攻略・確定オーブ報酬")
}
enum class DungeonLayout { OCTAGON, CLOISTER, VAULT, GALLERY, GARDEN, CRUCIBLE }
data class DungeonRoom(val id: Int, val stage: Int, val option: Int, val theme: DungeonTheme,
    val kind: DungeonRoomKind, val layout: DungeonLayout, val center: Pos, val encounterSeed: Long) {
    val spawn get() = center.add(0.0, 0.0, -13.0)
    val altar get() = center.add(0.0, 0.0, 15.0)
}

/** Acyclic layered graph. Every choice reaches every later floor boss; no key-drop softlocks. */
data class DungeonPlan(val seed: Long, val tier: Int, val ascension: Int, val roomsPerFloor: Int, val floors: Int,
    val rooms: List<DungeonRoom>, val edges: Map<Int, List<Int>>) {
    val stages get() = roomsPerFloor * floors
    fun choices(stage: Int) = rooms.filter { it.stage == stage }
    fun next(room: DungeonRoom) = edges[room.id].orEmpty().map { id -> rooms.single { it.id == id } }
    fun validate() {
        require(tier in 1..4 && ascension in 0..20 && floors in 1..3 && roomsPerFloor in 3..6)
        require(rooms.map { it.id }.distinct().size == rooms.size)
        require(rooms.map { it.center }.distinct().size == rooms.size && rooms.all { it.stage in 1..stages })
        require(edges.keys == rooms.map { it.id }.toSet())
        for (stage in 1..stages) {
            val at = choices(stage)
            require(at.size == if (stage % roomsPerFloor == 0) 1 else 2)
            require(at.all { (it.kind == DungeonRoomKind.BOSS) == (stage % roomsPerFloor == 0) })
            at.forEach { room -> require(next(room).map { it.id }.toSet() == choices(stage + 1).map { it.id }.toSet()) }
        }
    }
    companion object {
        fun generate(seed: Long, tier: Int, ascension: Int, balance: CoreMmoBalance = CoreMmoTuning.balance): DungeonPlan {
            val random = Random(seed)
            val kinds = listOf(DungeonRoomKind.ELITE, DungeonRoomKind.TREASURE, DungeonRoomKind.SEALS, DungeonRoomKind.REST)
            val rooms = buildList {
                var id = 0
                for (stage in 1..balance.dungeonStages) {
                    val boss = stage % balance.dungeonRoomsPerFloor == 0
                    val theme = DungeonTheme.entries[(stage - 1) / balance.dungeonRoomsPerFloor]
                    val alternate = if (stage == 1) DungeonRoomKind.SEALS else kinds[random.nextInt(kinds.size)]
                    repeat(if (boss) 1 else 2) { option ->
                        add(DungeonRoom(id++, stage, option, theme, if (boss) DungeonRoomKind.BOSS else if (option == 0) DungeonRoomKind.BATTLE else alternate,
                            DungeonLayout.entries[random.nextInt(DungeonLayout.entries.size)], Pos(if (boss) 0.5 else if (option == 0) -39.5 else 40.5, 40.0, stage * 64.0 + .5), random.nextLong()))
                    }
                }
            }
            return DungeonPlan(seed, tier, ascension, balance.dungeonRoomsPerFloor, balance.dungeonFloors, rooms,
                rooms.associate { r -> r.id to rooms.filter { it.stage == r.stage + 1 }.map { it.id } }).also { it.validate() }
        }
    }
}

enum class DungeonBoon(val displayName: String, val description: String) {
    FORCE("剛力", "攻撃力 +8%"), HASTE("疾風", "攻撃速度 +6%"), TECHNIQUE("戦技", "スキル威力 +12%"),
    VITALITY("生命", "最大HP +20"), FLOW("循環", "マナ回復 +18%・最大マナ +8"), FOCUS("集中", "クールダウン -4%"),
    GUARD("守護", "被ダメージ軽減 +3%"), STRIDE("軽歩", "移動速度 +4%"),
    FLAME("熾火", "火属性値 +4"), FROST("霜花", "氷属性値 +4"), STORM("雷光", "雷属性値 +4"),
    PRECISION("狩眼", "会心率増加 +40%・会心倍率 +10%");
    fun apply(s: CoreAffixStats, ranks: Int): CoreAffixStats = when (this) {
        FORCE -> s.copy(damagePercent = s.damagePercent + 8 * ranks)
        HASTE -> s.copy(attackSpeedPercent = s.attackSpeedPercent + 6 * ranks)
        TECHNIQUE -> s.copy(skillDamagePercent = s.skillDamagePercent + 12 * ranks)
        VITALITY -> s.copy(healthFlat = s.healthFlat + 20 * ranks)
        FLOW -> s.copy(manaRegenPercent = s.manaRegenPercent + 18 * ranks, maxManaFlat = s.maxManaFlat + 8 * ranks)
        FOCUS -> s.copy(cooldownReductionPercent = s.cooldownReductionPercent + 4 * ranks)
        GUARD -> s.copy(mitigationPercent = s.mitigationPercent + 3 * ranks)
        STRIDE -> s.copy(moveSpeedPercent = s.moveSpeedPercent + 4 * ranks)
        FLAME -> s.copy(fireFlat = s.fireFlat + 4 * ranks)
        FROST -> s.copy(iceFlat = s.iceFlat + 4 * ranks)
        STORM -> s.copy(lightningFlat = s.lightningFlat + 4 * ranks)
        PRECISION -> s.copy(critChanceIncreasedPercent = s.critChanceIncreasedPercent + 40 * ranks, critMultiplierBonusPercent = s.critMultiplierBonusPercent + 10 * ranks)
    }
}

/** Personal, deterministic offers. Reopening the UI never rerolls them, and no permanent item is touched. */
class DungeonBlessings(private val seed: Long) {
    private val selected = mutableMapOf<java.util.UUID, MutableMap<Int, Pair<DungeonBoon, Int>>>()
    fun offers(player: java.util.UUID, stage: Int) = DungeonBoon.entries.shuffled(Random(seed xor player.leastSignificantBits xor stage.toLong() * 7919)).take(3)
    fun hasChosen(player: java.util.UUID, stage: Int) = selected[player]?.containsKey(stage) == true
    fun choose(player: java.util.UUID, stage: Int, boon: DungeonBoon, empowered: Boolean): Boolean {
        if (stage !in 1..18 || hasChosen(player, stage) || boon !in offers(player, stage)) return false
        selected.getOrPut(player) { linkedMapOf() }[stage] = boon to if (empowered) 2 else 1
        return true
    }
    fun bonuses(player: java.util.UUID) = selected[player].orEmpty().values.groupBy { it.first }.mapValues { (_, values) -> values.sumOf { it.second } }
    fun stats(player: java.util.UUID, base: CoreAffixStats) = bonuses(player).entries.fold(base) { s, (boon, rank) -> boon.apply(s, rank) }
}
