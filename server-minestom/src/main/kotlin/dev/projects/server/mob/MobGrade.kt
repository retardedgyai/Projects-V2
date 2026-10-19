package dev.projects.server.mob

import kotlin.random.Random

/**
 * Monster grade (等級, Creator 2026-10-19: 一般・精鋭・希少・特異・厄災). Bosses stand outside the grades and use the
 * boss bar. A grade carries [mods] monster MODs, more health and damage, and from 精鋭 up a break gauge.
 */
enum class MobGrade(val label: String, val mods: Int, val health: Double, val damage: Double) {
    COMMON("一般", 0, 1.0, 1.0),
    ELITE("精鋭", 1, 1.5, 1.1),
    RARE("希少", 2, 2.1, 1.2),
    UNIQUE("特異", 3, 2.9, 1.3),
    CALAMITY("厄災", 4, 4.0, 1.45);

    val breaks: Boolean get() = this != COMMON
}

/** Monster MODs: each one changes how the mob fights (none is decorative). */
enum class MobMod(val label: String) {
    /** Moves 25% faster. */
    SWIFT("俊足"),
    /** Takes 20% less damage. */
    IRONHIDE("鉄皮"),
    /** Deals 20% more damage. */
    MIGHT("剛力"),
    /** Each hit that lands heals 4% of its maximum health. */
    LEECH("吸血"),
    /** At or below half health: 30% more damage and 15% faster. */
    FRENZY("狂化"),
    /** After 4 s without being hit, heals 2% of its maximum health per second. */
    REGEN("再生");

    companion object {
        const val SWIFT_SPEED = 1.25
        const val IRONHIDE_TAKEN = 0.8
        const val MIGHT_DAMAGE = 1.2
        const val LEECH_RATIO = 0.04
        const val FRENZY_HEALTH = 0.5
        const val FRENZY_DAMAGE = 1.3
        const val FRENZY_SPEED = 1.15
        const val REGEN_DELAY = 4000L
        const val REGEN_PER_SECOND = 0.02
    }
}

internal object MobGrades {
    /** Weights per grade for plain mobs and for elite archetypes (never below 精鋭). */
    private val NORMAL_WEIGHTS = intArrayOf(70, 18, 8, 3, 1)
    private val ELITE_WEIGHTS = intArrayOf(0, 60, 25, 11, 4)

    /** Deterministic per spawn: the same map seed and spawn slot always roll the same grade and MODs. */
    fun roll(seed: Long, archetype: QuestMobArchetype): Pair<MobGrade, List<MobMod>> {
        if (archetype.rarity == QuestMobRarity.BOSS) return MobGrade.COMMON to emptyList()
        val random = Random(seed)
        val weights = if (archetype.rarity == QuestMobRarity.ELITE) ELITE_WEIGHTS else NORMAL_WEIGHTS
        var pick = random.nextInt(weights.sum())
        val grade = MobGrade.entries.first { pick -= weights[it.ordinal]; pick < 0 }
        return grade to MobMod.entries.shuffled(random).take(grade.mods).sortedBy { it.ordinal }
    }
}

/** What a mob's HUD shows (overhead plate, or the boss bar for a boss). */
internal data class MobHud(
    val level: Int,
    val name: String,
    val grade: MobGrade,
    val mods: List<MobMod>,
    val health: Double,
    val maximumHealth: Double,
    /** Remaining break gauge 0..1; null = this mob has none. */
    val breakGauge: Double?,
    /** While broken: the share of the down time still left (1 → 0); null = not broken. */
    val broken: Double?,
    val effects: List<MobHudEffect>,
    /** The attack being prepared, or 帰還中; shown in place of the name. */
    val notice: String? = null,
)

internal enum class MobHudEffectKind { BUFF, DEBUFF, CC }

/** [icon] names a plate glyph (plate_icon_<icon>, boss_icon_<icon>). */
internal data class MobHudEffect(val kind: MobHudEffectKind, val icon: String)
