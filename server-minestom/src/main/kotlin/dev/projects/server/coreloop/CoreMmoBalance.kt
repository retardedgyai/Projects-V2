package dev.projects.server.coreloop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** Concrete expansion tuning. Immutable per server start; malformed overrides fail before admission. */
data class CoreMmoBalance(
    val professionXpScale: Int = 12,
    val refineXp: Int = 3,
    val craftXp: Int = 30,
    val refineReturnMaxPercent: Int = 20,
    val craftQualityMax: Int = 20,
    val surveyTier2: Int = 40,
    val surveyTier3: Int = 240,
    val surveyTier4: Int = 900,
    val surveyMapCost: Int = 4,
    val dungeonRoomsPerFloor: Int = 4,
    val dungeonFloors: Int = 3,
    val dungeonMaxAscension: Int = 20,
    val dungeonHealthPerPlayer: Int = 65,
    val dungeonHealthPerAscension: Int = 8,
    val dungeonDamagePerAscension: Int = 4,
    val dungeonRoomTokens: Int = 3,
    val dungeonBossOrbs: Int = 2,
    val dungeonRevives: Int = 2,
    val bossMechanicSeconds: Int = 12,
    val journeyXpPercent: Int = 100,
    val equipmentRankPermille: Int = 45,
    val mobRankHealthPermille: Int = 70,
    val mobRankDamagePermille: Int = 35,
) {
    init {
        require(professionXpScale in 1..10_000 && refineXp in 1..1000 && craftXp in 1..10_000)
        require(refineReturnMaxPercent in 0..35 && craftQualityMax in 0..30)
        require(surveyTier2 in 1 until surveyTier3 && surveyTier3 < surveyTier4 && surveyTier4 <= 1_000_000)
        require(surveyMapCost in 1..64 && dungeonRoomsPerFloor in 3..6 && dungeonFloors in 1..3)
        require(dungeonMaxAscension in 1..20 && dungeonHealthPerPlayer in 0..200)
        require(dungeonHealthPerAscension in 0..30 && dungeonDamagePerAscension in 0..15)
        require(dungeonRoomTokens in 1..32 && dungeonBossOrbs in 1..16 && dungeonRevives in 0..5)
        require(bossMechanicSeconds in 6..30)
        require(journeyXpPercent in 10..1000 && equipmentRankPermille in 0..100)
        require(mobRankHealthPermille in 0..200 && mobRankDamagePermille in 0..100)
    }
    val dungeonStages get() = dungeonRoomsPerFloor * dungeonFloors

    companion object {
        fun load(path: Path): CoreMmoBalance {
            val properties = Properties()
            CoreMmoBalance::class.java.getResourceAsStream("/core-mmo-balance.properties")!!.use { properties.load(it) }
            if (Files.exists(path)) {
                require(Files.isRegularFile(path) && Files.size(path) <= 16_384) { "MMO設定ファイルが不正です" }
                Files.newBufferedReader(path).use { properties.load(it) }
            }
            val names = setOf("profession.xp-scale", "profession.refine-xp", "profession.craft-xp", "profession.refine-return-max-percent", "profession.craft-quality-max",
                "survey.tier2", "survey.tier3", "survey.tier4", "survey.map-cost", "dungeon.rooms-per-floor", "dungeon.floors", "dungeon.max-ascension",
                "dungeon.health-per-player", "dungeon.health-per-ascension", "dungeon.damage-per-ascension", "dungeon.room-tokens", "dungeon.boss-orbs", "dungeon.revives", "boss.mechanic-seconds",
                "journey.xp-percent", "journey.equipment-rank-permille", "journey.mob-health-rank-permille", "journey.mob-damage-rank-permille")
            require(properties.stringPropertyNames() == names) { "MMO設定に未知または不足した項目があります" }
            fun n(key: String) = properties.getProperty(key).trim().toInt()
            return CoreMmoBalance(n("profession.xp-scale"), n("profession.refine-xp"), n("profession.craft-xp"), n("profession.refine-return-max-percent"),
                n("profession.craft-quality-max"), n("survey.tier2"), n("survey.tier3"), n("survey.tier4"), n("survey.map-cost"), n("dungeon.rooms-per-floor"),
                n("dungeon.floors"), n("dungeon.max-ascension"), n("dungeon.health-per-player"), n("dungeon.health-per-ascension"), n("dungeon.damage-per-ascension"),
                n("dungeon.room-tokens"), n("dungeon.boss-orbs"), n("dungeon.revives"), n("boss.mechanic-seconds"),
                n("journey.xp-percent"), n("journey.equipment-rank-permille"), n("journey.mob-health-rank-permille"), n("journey.mob-damage-rank-permille"))
        }
    }
}

object CoreMmoTuning { @Volatile var balance = CoreMmoBalance(); internal set }
