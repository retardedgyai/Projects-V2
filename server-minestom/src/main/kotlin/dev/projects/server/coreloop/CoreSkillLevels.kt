package dev.projects.server.coreloop

/**
 * Skill levels (2026-11-03 藍). Points come in like passive points (one per adventure level after the first, one per
 * boss beaten for the first time) but are a separate purse. A point raises one skill of the current class by a level,
 * up to [MAX_BASE]; only gear MODs (+1 to one skill, +1 to every skill) go past it. For now a level only raises damage;
 * what each skill gains per level is 藍's to decide later, so [multiplier] takes the skill and ignores it for now.
 * Levels are stored per class build ([CoreClassBuild.skillLevels], skill icon -> level, absent = 1).
 */
object CoreSkillLevels {
    const val MAX_BASE = 10
    /** Damage per level above 1 (Lv 10 = ×1.45, with +2 from gear ×1.55). */
    const val DAMAGE_PER_LEVEL = .05

    fun budget(account: CoreAccount) = CorePassiveTree.budget(account)
    fun spent(build: CoreClassBuild) = build.skillLevels.values.sumOf { it - 1 }
    fun available(account: CoreAccount) = budget(account) - spent(account.journey.build)

    /** The level bought with points. */
    fun base(build: CoreClassBuild, icon: String) = build.skillLevels[icon] ?: 1
    /** Levels from gear: the stones naming this skill plus the ones raising every skill. */
    fun gear(stats: CoreAffixStats, icon: String) = (stats.skillLevels[icon] ?: 0) + stats.bonus(CoreAffixStat.SKILL_LEVEL).toInt()
    fun level(build: CoreClassBuild, stats: CoreAffixStats, icon: String) = base(build, icon) + gear(stats, icon)

    @Suppress("UNUSED_PARAMETER")
    fun multiplier(skill: CoreSkillDefinition, level: Int) = 1 + DAMAGE_PER_LEVEL * (level - 1).coerceAtLeast(0)

    /** Icons a class can level: everything it can equip (retired skills are never offered). */
    fun levelable(job: CoreClass): Set<String> {
        val skills = CoreSkillCatalog.skills(job)
        val start = CoreSkillCatalog.ultimateStart(job)
        return (CoreSkillCatalog.normalChoices(job).map { skills[it].icon } +
            CoreSkillCatalog.ultimateChoices(job).map { skills[start + it].icon }).toSet()
    }

    fun valid(job: CoreClass, build: CoreClassBuild) = build.skillLevels.keys.all { it in levelable(job) }

    fun raise(account: CoreAccount, icon: String): CoreClassBuild {
        val build = account.journey.build
        require(icon in levelable(account.journey.job)) { "この技は上げられません" }
        require(base(build, icon) < MAX_BASE) { "この技はもう Lv $MAX_BASE です" }
        require(available(account) > 0) { "技ポイントが足りません" }
        return build.copy(skillLevels = build.skillLevels + (icon to base(build, icon) + 1))
    }
}
