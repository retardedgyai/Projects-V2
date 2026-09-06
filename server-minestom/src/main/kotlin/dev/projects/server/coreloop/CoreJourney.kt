package dev.projects.server.coreloop

/** Concrete character progression; separate from tradable gear and temporary dungeon blessings. */
enum class CoreClass(val displayName: String, val description: String) {
    WARRIOR("戦士", "大剣の連撃と一撃。敵の隙に踏み込む"),
    RANGER("レンジャー", "射線を通し、距離を保って狙い撃つ"),
    MAGE("メイジ", "炎と氷の術式。マナを使って集団を制する"),
    STARWEAVER("星織り師", "メイジ上位職。星を編み、術式で解放する");
    val magic get() = this == MAGE || this == STARWEAVER
    val icons get() = when (this) {
        WARRIOR -> listOf("dash", "slam", "whirl")
        RANGER -> listOf("pierce", "frost_fan", "arrow_rain")
        MAGE -> listOf("firebolt", "frost_nova", "meteor")
        STARWEAVER -> listOf("star_thread", "star_ring", "starfall")
    }
    val skills get() = when (this) {
        WARRIOR -> listOf("踏み込み斬り", "地砕き", "旋風斬り")
        RANGER -> listOf("貫通射ち", "霜矢の扇", "矢の嵐")
        MAGE -> listOf("火炎弾", "霜の波紋", "流星雨")
        STARWEAVER -> listOf("星糸", "星環", "星降る夜")
    }
    val skillDescriptions get() = when (this) {
        WARRIOR -> listOf("敵の手前まで踏み込み、前方を斬る", "構えた地点から前方へ強い一撃", "自分の周囲へ三回の斬撃")
        RANGER -> listOf("狙った射線上の敵を最大三体貫通", "前方の扇へ霜矢。命中した敵を減速", "前方の地点へ三回の矢の雨")
        MAGE -> listOf("狙った敵へ遠距離の火炎弾", "周囲の敵を術式で攻撃し減速", "前方の地点へ三回の流星")
        STARWEAVER -> listOf("通常三命中で編む。三蓄積なら星糸が三体貫通", "三蓄積なら減速延長とマナ12回復", "三蓄積なら星降りが四回に増加")
    }
}

enum class CoreWeaponBase(val displayName: String, val detail: String, val power: Double = 1.0, val speed: Double = 1.0) {
    STANDARD("開拓の大剣", "癖のない基準型。三連撃で戦う"),
    FLOW("連撃大剣", "回避後も連撃を保持。三段目でマナ回復", .88, 1.18),
    CLEAVER("重断大剣", "三段目は狭い縦斬り。締めの一撃が強い", 1.12, .88),
    CONDUIT("導脈大剣", "通常攻撃で蓄積。次のスキルを強化", .94),
    LONGBOW("潮風の長弓", "狙った射線へ矢を放つ", .95),
    STAFF("灯火の杖", "遠距離の魔弾と属性術式", .92);
    val family get() = when (this) { LONGBOW -> "bow"; STAFF -> "staff"; else -> "greatsword" }
    fun usable(job: CoreClass) = when (job) {
        CoreClass.WARRIOR -> family == "greatsword"
        CoreClass.RANGER -> this == LONGBOW
        else -> this == STAFF
    }
}

data class CoreJourney(val job: CoreClass = CoreClass.WARRIOR, val chosen: Boolean = true,
    val xp: Long = 0, val lessons: Int = 63, val legacy: Boolean = true) {
    init { require(xp in 0..1_000_000_000L && lessons in 0..63) }
    val level get() = CoreJourneyRules.level(xp)
    fun knows(bit: Int) = lessons and (1 shl bit) != 0
    fun learn(bit: Int): CoreJourney { require(bit in 0..5); return copy(lessons = lessons or (1 shl bit)) }
    fun gain(amount: Long): CoreJourney {
        require(amount in 0..1_000_000_000L)
        return copy(xp = (xp + amount * CoreMmoTuning.balance.journeyXpPercent / 100).coerceAtMost(1_000_000_000))
    }
    companion object { fun fresh() = CoreJourney(chosen = false, lessons = 0, legacy = false) }
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
    fun skillUnlocked(a: CoreAccount, index: Int) = a.journey.legacy || a.journey.level >= listOf(1, 4, 8)[index]
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
        else -> "深殿の深度を進め、武器の型・MOD・製造品質を追求しよう"
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
