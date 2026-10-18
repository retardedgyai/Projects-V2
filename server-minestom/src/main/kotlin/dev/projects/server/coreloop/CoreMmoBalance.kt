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
    val journeyXpPercent: Int = 100,
    val equipmentRankPermille: Int = 45,
    val mobRankHealthPermille: Int = 70,
    val mobRankDamagePermille: Int = 35,
    // Magic path (docs/development/research-economy-v0.md). Silver fees are destroyed, not paid to anyone.
    val researchDistillSilver: Int = 5,
    val researchInkSilver: Int = 15,
    val researchInkYield: Int = 4,
    val researchCraftSilver: Int = 40,
    val researchCatalystBonusPercent: Int = 25,
    val researchSealsPerTier: Int = 1,
) {
    init {
        require(professionXpScale in 1..10_000 && refineXp in 1..1000 && craftXp in 1..10_000)
        require(refineReturnMaxPercent in 0..35 && craftQualityMax in 0..30)
        require(surveyTier2 in 1 until surveyTier3 && surveyTier3 < surveyTier4 && surveyTier4 <= 1_000_000)
        require(surveyMapCost in 1..64)
        require(journeyXpPercent in 10..1000 && equipmentRankPermille in 0..100)
        require(mobRankHealthPermille in 0..200 && mobRankDamagePermille in 0..100)
        require(researchDistillSilver in 0..10_000 && researchInkSilver in 0..10_000 && researchCraftSilver in 0..100_000)
        require(researchInkYield in 1..64 && researchCatalystBonusPercent in 0..100 && researchSealsPerTier in 1..16)
    }

    companion object {
        fun load(path: Path): CoreMmoBalance {
            val properties = Properties()
            CoreMmoBalance::class.java.getResourceAsStream("/core-mmo-balance.properties")!!.use { properties.load(it) }
            if (Files.exists(path)) {
                require(Files.isRegularFile(path) && Files.size(path) <= 16_384) { "MMO設定ファイルが不正です" }
                Files.newBufferedReader(path).use { properties.load(it) }
            }
            // Overrides written for the retired dungeon stay loadable; their values are ignored.
            properties.stringPropertyNames().filter { it.startsWith("dungeon.") || it == "boss.mechanic-seconds" }.forEach { properties.remove(it) }
            val names = setOf("profession.xp-scale", "profession.refine-xp", "profession.craft-xp", "profession.refine-return-max-percent", "profession.craft-quality-max",
                "survey.tier2", "survey.tier3", "survey.tier4", "survey.map-cost",
                "journey.xp-percent", "journey.equipment-rank-permille", "journey.mob-health-rank-permille", "journey.mob-damage-rank-permille",
                "research.distill-silver", "research.ink-silver", "research.ink-yield", "research.craft-silver",
                "research.catalyst-bonus-percent", "research.seals-per-tier")
            require(properties.stringPropertyNames() == names) { "MMO設定に未知または不足した項目があります" }
            fun n(key: String) = properties.getProperty(key).trim().toInt()
            return CoreMmoBalance(n("profession.xp-scale"), n("profession.refine-xp"), n("profession.craft-xp"), n("profession.refine-return-max-percent"),
                n("profession.craft-quality-max"), n("survey.tier2"), n("survey.tier3"), n("survey.tier4"), n("survey.map-cost"),
                n("journey.xp-percent"), n("journey.equipment-rank-permille"), n("journey.mob-health-rank-permille"), n("journey.mob-damage-rank-permille"),
                n("research.distill-silver"), n("research.ink-silver"), n("research.ink-yield"), n("research.craft-silver"),
                n("research.catalyst-bonus-percent"), n("research.seals-per-tier"))
        }
    }
}

object CoreMmoTuning { @Volatile var balance = CoreMmoBalance(); internal set }
