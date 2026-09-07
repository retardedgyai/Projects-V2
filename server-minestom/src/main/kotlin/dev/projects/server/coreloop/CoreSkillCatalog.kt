package dev.projects.server.coreloop

/** This catalog is the authority for both casts and their Japanese tooltips. */
data class CoreSkillDefinition(
    val name: String, val icon: String, val description: String,
    val formula: CoreDamageFormula, val type: CoreDamageType,
    val mana: Int, val cooldown: Int, val startup: Int,
    val pulses: Int = 1, val projectile: Boolean = false, val area: Boolean = false,
) {
    val tags get() = buildSet {
        add(CoreAttackTag.SKILL)
        if (projectile) add(CoreAttackTag.PROJECTILE)
        else if (type == CoreDamageType.PHYSICAL) add(CoreAttackTag.MELEE)
        if (area) add(CoreAttackTag.AREA)
    }
    fun cooldownTicks(stats: CoreAffixStats) = CoreCombatMath.cooldownTicks(cooldown, stats.cooldownReductionPercent)
    fun startupTicks(sheet: CoreCombatSheet) = CoreCombatMath.castTicks(startup,
        if (type == CoreDamageType.MAGICAL) sheet.mods.castReductionPercent else (sheet.attackSpeed - 1) * 100)
    fun baseDamage(sheet: CoreCombatSheet) = formula.evaluate(sheet) +
        (sheet.mods.fireFlat + sheet.mods.iceFlat + sheet.mods.lightningFlat) * .65
    fun preview(sheet: CoreCombatSheet) = CoreCombatMath.outgoing(baseDamage(sheet), type, tags, sheet.mods)
    fun tooltip(sheet: CoreCombatSheet): List<String> = listOf(description, type.label,
        "${formula.label()}${if (pulses > 1) " × $pulses 回（1回ごと）" else ""}",
        "現在 ${CoreCombatMath.number(preview(sheet))}${if (pulses > 1) " / 全命中 ${CoreCombatMath.number(preview(sheet) * pulses)}" else ""}（属性加算・与ダメージMOD込み）",
        "敵防御・会心・弱点・蓄積・砥石は別計算",
        "マナ $mana / 再使用 ${CoreCombatMath.number(cooldownTicks(sheet.mods) / 20.0)}秒",
        "発生 ${CoreCombatMath.number(startupTicks(sheet) / 20.0)}秒 / ${if (type == CoreDamageType.MAGICAL) "詠唱速度" else "攻撃速度"}で短縮")
}

object CoreSkillCatalog {
    val unlockLevels = listOf(1, 4, 8)
    val basicFormula = CoreDamageFormula(ad = 1.0)
    fun basicType(job: CoreClass) = if (job.magic) CoreDamageType.MAGICAL else CoreDamageType.PHYSICAL
    fun basicTags(job: CoreClass) = setOf(CoreAttackTag.NORMAL,
        if (job == CoreClass.WARRIOR) CoreAttackTag.MELEE else CoreAttackTag.PROJECTILE)
    private fun physical(name: String, icon: String, description: String, base: Double, ad: Double, mana: Int, cd: Int,
        startup: Int, pulses: Int = 1, projectile: Boolean = false, area: Boolean = true) =
        CoreSkillDefinition(name, icon, description, CoreDamageFormula(base, ad = ad), CoreDamageType.PHYSICAL, mana, cd, startup, pulses, projectile, area)
    private fun magic(name: String, icon: String, description: String, base: Double, ap: Double, mana: Int, cd: Int,
        startup: Int, pulses: Int = 1, projectile: Boolean = false, area: Boolean = true) =
        CoreSkillDefinition(name, icon, description, CoreDamageFormula(base, ap = ap), CoreDamageType.MAGICAL, mana, cd, startup, pulses, projectile, area)
    private val byClass: Map<CoreClass, List<CoreSkillDefinition>> = CoreClass.entries.associateWith(::buildSkills)
    fun skills(job: CoreClass): List<CoreSkillDefinition> = byClass.getValue(job)
    private fun buildSkills(job: CoreClass): List<CoreSkillDefinition> = when (job) {
        CoreClass.WARRIOR -> listOf(
            physical("踏み込み斬り", "dash", "敵の手前まで踏み込み、前方を斬る", 6.0, 1.2, 15, 80, 5),
            physical("地砕き", "slam", "構えた地点から前方へ強い一撃", 12.0, 1.6, 25, 140, 12),
            physical("旋風斬り", "whirl", "自分の周囲へ三回の斬撃", 3.0, .9, 35, 220, 6, 3))
        CoreClass.RANGER -> listOf(
            physical("貫通射ち", "pierce", "狙った射線上の敵を最大三体貫通", 6.0, 1.5, 15, 80, 5, projectile = true),
            physical("霜矢の扇", "frost_fan", "前方の扇へ霜矢。命中した敵を減速", 6.0, 1.3, 25, 140, 12, projectile = true),
            physical("矢の嵐", "arrow_rain", "前方の地点へ三回の矢の雨", 3.0, 1.1, 35, 220, 6, 3, projectile = true))
        CoreClass.MAGE -> listOf(
            magic("火炎弾", "firebolt", "狙った敵へ遠距離の火炎弾", 8.0, 1.4, 15, 80, 5, projectile = true, area = false),
            magic("霜の波紋", "frost_nova", "周囲の敵を術式で攻撃し減速", 10.0, 1.0, 25, 140, 12),
            magic("流星雨", "meteor", "前方の地点へ三回の流星", 5.0, 1.1, 35, 220, 6, 3))
        CoreClass.STARWEAVER -> listOf(
            magic("星糸", "star_thread", "通常命中で星を編む。三蓄積なら三体貫通", 6.0, 1.4, 15, 80, 5, projectile = true, area = false),
            magic("星環", "star_ring", "三蓄積なら減速延長とマナ12回復", 8.0, 1.1, 25, 140, 12),
            magic("星降る夜", "starfall", "三蓄積なら四回に増加。蓄積は1個につき威力+15%", 4.0, 1.1, 35, 220, 6, 3))
    }
}
