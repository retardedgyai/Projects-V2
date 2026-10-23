package dev.projects.server.coreloop

/** Concrete character progression; separate from tradable gear and temporary run boons. */
enum class CoreClass(val displayName: String, val description: String) {
    WARRIOR("戦士", "鉄塊の大剣で地を割り、敵を押し出す"),
    RANGER("レンジャー", "射線を通し、距離を保って狙い撃つ"),
    MAGE("メイジ", "炎と氷の術式。マナを使って集団を制する"),
    STARWEAVER("星織り師", "メイジ派生。星を編み、術式で解放する"),
    ASSASSIN("アサシン", "印を仕込み、接近と離脱を使い分ける"),
    TEMPLAR("テンプラー", "敵を集め、守った力を反撃と支援に回す"),
    HEALER("ヒーラー", "攻撃で信仰を得て、救護か裁きに使う");
    val magic get() = this == MAGE || this == STARWEAVER || this == HEALER
    val melee get() = this == WARRIOR || this == ASSASSIN || this == TEMPLAR
    val resourceName get() = when (this) { WARRIOR -> "獣"; RANGER -> "集中"; MAGE -> "術式"; STARWEAVER -> "星"; ASSASSIN -> "機会"; TEMPLAR -> "決意"; HEALER -> "信仰" }
    val healthFactor get() = when (this) { WARRIOR -> 1.0; RANGER -> .72; MAGE -> .8; STARWEAVER -> .8; ASSASSIN -> .75; TEMPLAR -> 1.4; HEALER -> .9 }
    /**
     * Class base AR / MR added before gear (2026-10-19, Creator: toughness comes from each class's default stats, not
     * a damage-reduction perk). The warrior is fairly tough, the templar the toughest, the assassin and ranger the softest.
     */
    val baseArmor get() = when (this) { WARRIOR -> 40.0; TEMPLAR -> 70.0; HEALER -> 20.0; ASSASSIN -> 10.0; RANGER -> 10.0; MAGE -> 5.0; STARWEAVER -> 10.0 }
    val baseResist get() = when (this) { WARRIOR -> 25.0; TEMPLAR -> 50.0; HEALER -> 40.0; ASSASSIN -> 10.0; RANGER -> 15.0; MAGE -> 35.0; STARWEAVER -> 25.0 }
    val passive get() = when(this) {
        WARRIOR -> "溜める技が当たると獣が1つ、通常攻撃でも少し溜まる（最大3）。地割りは獣を全部使い、1つごとに重くなる。使う技は振っている間怯まない。"
        RANGER -> "同じ獲物への通常攻撃で集中が育ち、威力が最大20%増す。獲物変更で集中半減。"
        MAGE -> "属性を変えて生成技を当てると術式を追加15獲得。集めた術式を大技に使う。"
        STARWEAVER -> "通常命中で星を編む。最大3。解放技は全て使い、星ごとに威力15%増加。"
        ASSASSIN -> "通常攻撃で機会を得る。印を付けた敵への消費技は最初の命中が35%強化。"
        TEMPLAR -> "攻撃と防御で決意を得る。命中した敵の狙いを2秒引きつけ、味方を守る。"
        HEALER -> "通常攻撃と光の技で信仰を得る。回復・障壁・裁きに同じ信仰を振り分ける。"
    }
    val icons get() = CoreSkillCatalog.skills(this).map { it.icon }
    val skills get() = CoreSkillCatalog.skills(this).map { it.name }
    val skillDescriptions get() = CoreSkillCatalog.skills(this).map { it.description }
}

enum class CoreWeaponBase(val displayName: String, val detail: String, val power: Double = 1.0, val speed: Double = 1.0) {
    STANDARD("開拓の大剣", "癖のない基準型。三連撃で戦う"),
    FLOW("連撃大剣", "回避後も連撃を保持。三段目でマナ回復", .88, 1.18),
    CLEAVER("重断大剣", "三段目は狭い縦斬り。締めの一撃が強い", 1.12, .88),
    CONDUIT("導脈大剣", "通常命中で固有資源を追加4獲得。資源消費技の威力+12%", .94),
    LONGBOW("潮風の長弓", "狙った射線へ矢を放つ", .95),
    STAFF("灯火の杖", "遠距離の魔弾と属性術式", .92),
    DAGGERS("影縫い短剣", "素早く印を仕込む。接近戦用の短い間合い", .78, 1.35),
    MACE("誓いの騎槍", "長い騎槍と守護の術。集団を制する", .95, .85),
    TOME("灯守の聖典", "裁きと救護。攻撃で得た信仰を振り分ける", .9);
    val family get() = when (this) { LONGBOW -> "bow"; STAFF -> "staff"; DAGGERS -> "dagger"; MACE -> "mace"; TOME -> "tome"; else -> "greatsword" }
    fun usable(job: CoreClass) = when (job) {
        CoreClass.WARRIOR -> family == "greatsword"
        CoreClass.ASSASSIN -> this == DAGGERS
        CoreClass.TEMPLAR -> this == MACE
        CoreClass.HEALER -> this == TOME
        CoreClass.RANGER -> this == LONGBOW
        else -> this == STAFF
    }
}

/** Defaults are a fresh, locked character; full unlock must be requested via [legacy]. */
data class CoreJourney(val job: CoreClass = CoreClass.WARRIOR, val chosen: Boolean = false,
    val xp: Long = 0, val lessons: Int = 0, val legacy: Boolean = false, val build: CoreClassBuild = CoreClassBuild(),
    val savedBuilds: Map<CoreClass, CoreClassBuild> = emptyMap()) {
    init {
        require(xp in 0..1_000_000_000L && lessons in 0..63)
        val budget = CoreClassTrees.budget(this)
        require(build.points <= budget && savedBuilds.values.all { it.points <= budget })
    }
    fun changeClass(next: CoreClass) = if(next == job) copy(chosen = true) else copy(job = next, chosen = true,
        build = savedBuilds[next] ?: CoreClassBuild(), savedBuilds = (savedBuilds + (job to build)) - next)
    val level get() = CoreJourneyRules.level(xp)
    fun knows(bit: Int) = lessons and (1 shl bit) != 0
    fun learn(bit: Int): CoreJourney { require(bit in 0..5); return copy(lessons = lessons or (1 shl bit)) }
    fun gain(amount: Long): CoreJourney {
        require(amount in 0..1_000_000_000L)
        return copy(xp = (xp + amount * CoreMmoTuning.balance.journeyXpPercent / 100).coerceAtMost(1_000_000_000))
    }
    companion object {
        fun fresh() = CoreJourney()
        /** Explicit full unlock: pre-journey saves (format < 8) and the skill-test lab only. */
        fun legacy(job: CoreClass = CoreClass.WARRIOR, xp: Long = 0, build: CoreClassBuild = CoreClassBuild()) =
            CoreJourney(job, chosen = true, xp = xp, lessons = 63, legacy = true, build = build)
    }
}

object CoreJourneyRules {
    const val MAX_LEVEL = 40
    fun floor(tier: Int): Int { require(tier in 1..4); return (tier - 1) * 10 + 1 }
    fun ceiling(tier: Int) = floor(tier) + 9
    fun threshold(level: Int): Long { require(level in 1..MAX_LEVEL); return (1 until level).sumOf { 80L + 20L * it + 3L * it * it } }
    fun level(xp: Long) = (1..MAX_LEVEL).last { xp >= threshold(it) }
    fun itemLevel(identity: CoreGearIdentity, tier: Int) = if (identity.itemLevel == 0) floor(tier) else identity.itemLevel
    fun rank(identity: CoreGearIdentity, tier: Int) = itemLevel(identity, tier) - floor(tier)
    fun power(identity: CoreGearIdentity, tier: Int) = 1.0 + rank(identity, tier) * CoreMmoTuning.balance.equipmentRankPermille / 1000.0
    fun base(a: CoreAccount) = a.weaponIdentity.base
    /** Single source of truth for the star-weaver change; null when eligible. */
    fun starweaverBlocker(a: CoreAccount): String? = when {
        a.journey.job != CoreClass.MAGE && a.journey.job != CoreClass.STARWEAVER -> "メイジから転職してください"
        a.journey.level < 20 || a.amount(CoreResource.BOSS_SIGIL, 2) < 2 -> "Lv20とT2討伐証2枚が必要です（証は消費しません）"
        else -> null
    }
    fun skillUnlocked(a: CoreAccount, index: Int) = a.journey.legacy || a.journey.level >= CoreSkillCatalog.unlockLevels[index]
    fun reward(tier: Int, boss: Boolean = false): Long = (if (boss) 300L else 35L) * tier
    fun next(a: CoreAccount): String = when {
        !a.journey.chosen -> "案内所で最初の職業を選ぼう"
        !a.journey.knows(0) -> "T1遠征で通常攻撃を敵に当てよう"
        !a.journey.knows(1) -> "ホットバー2番を選び、最初のスキルを使おう"
        !a.journey.knows(2) -> "敵の戦利品を回収しよう。近づくと保管される"
        !a.journey.knows(3) -> "手帳から港へ帰還し、戦利品を確認しよう"
        !a.journey.knows(4) -> "工房で制作するか、市場で装備を購入しよう"
        !a.journey.knows(5) -> "刻印工房でオーブを使い、MODを付けよう"
        a.journey.job == CoreClass.MAGE && a.journey.level >= 20 -> "職業の手帳で星織り師への転職条件を確認しよう"
        a.unlockedMapTier < 4 -> "ボスを討伐、または採取実績を積んで次のTierへ"
        else -> "T4遠征と工房で、装備のMOD・強化・製造品質を追求しよう"
    }
    fun temper(a: CoreAccount, slot: CoreGearSlot): CoreRecipe {
        val tier = CoreAffixCatalog.gearTier(a, slot)
        val identity = CoreEconomy.identity(a, slot)
        require(itemLevel(identity, tier) < ceiling(tier)) { "このTierの装備レベルは最大です" }
        val count = 1L + rank(identity, tier) / 3
        val primary = if (slot == CoreGearSlot.WEAPON) CoreResource.INGOT else CoreResource.LEATHER
        return CoreRecipe("装備レベルを1上げる", mapOf(CoreMaterial(primary, tier) to count,
            CoreMaterial(CoreResource.STONE_BLOCK, 1) to 1L), emptyMap())
    }
}
