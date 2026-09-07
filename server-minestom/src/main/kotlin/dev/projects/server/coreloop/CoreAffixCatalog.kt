package dev.projects.server.coreloop

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Collections
import java.util.Random
import java.util.UUID
import kotlin.math.roundToInt

enum class CoreGearSlot(val displayName: String) { WEAPON("武器"), ARMOR("防具") }
enum class CoreLootKind { NORMAL, ELITE, BOSS }
enum class CoreAffixGroup(val displayName: String) { PREFIX("接頭"), SUFFIX("接尾") }
enum class CoreAffixCategory(val displayName: String) { OFFENSE("攻撃"), RESOURCE("スキル・資源"), DEFENSE("防御"), UTILITY("機動") }
enum class CoreAffixStat(val displayName: String, val percent: Boolean = true) {
    DAMAGE("与ダメージ増加"), ATTACK_SPEED("攻撃速度"), SKILL_DAMAGE("スキルダメージ"),
    MAX_MANA("最大マナ", false), MANA_REGEN("マナ回復速度"), COOLDOWN_REDUCTION("クールダウン回復速度"),
    HEALTH("最大HP", false), MITIGATION("被ダメージ軽減"), MOVE_SPEED("移動速度"),
    CRIT_CHANCE_INCREASED("クリティカル率増加"), CRIT_MULTIPLIER("クリティカル倍率"),
    NORMAL_DAMAGE("通常攻撃ダメージ"), CAST_REDUCTION("詠唱速度"),
    FIRE("火属性値", false), ICE("氷属性値", false), LIGHTNING("雷属性値", false),
    AD_FLAT("物理攻撃力 AD", false), AD_PERCENT("物理攻撃力 AD"),
    AP_FLAT("魔法攻撃力 AP", false), AP_PERCENT("魔法攻撃力 AP"),
    AR_FLAT("物理防御 AR", false), AR_PERCENT("物理防御 AR"),
    MR_FLAT("魔法防御 MR", false), MR_PERCENT("魔法防御 MR"),
    PHYSICAL_DAMAGE("物理ダメージ"), MAGICAL_DAMAGE("魔法ダメージ"),
    MELEE_DAMAGE("近接ダメージ"), PROJECTILE_DAMAGE("投射物ダメージ"),
    PHYSICAL_PEN_FLAT("物理貫通", false), PHYSICAL_PEN_PERCENT("物理貫通"),
    MAGICAL_PEN_FLAT("魔法貫通", false), MAGICAL_PEN_PERCENT("魔法貫通"),
    HEALTH_PERCENT("最大HP"), MANA_PERCENT("最大マナ"), MANA_REGEN_FLAT("毎秒マナ回復", false),
    HEALING_FLAT("回復力", false), HEALING_PERCENT("回復力"),
    OUTGOING_HEALING("与回復量"), INCOMING_HEALING("被回復量"),
    LIFESTEAL("ライフスティール"),
    SHIELD_POWER("シールド量"),
    WAR_AFTERSHOCK("戦士・余波", false), MAGE_ECHO("メイジ・残響", false), HUNT_RETURN("狩人・追矢", false),
    ASS_VENOM("暗殺者・毒印", false), TEMP_GRAVITY("聖騎士・重力", false), HEAL_CONVERSION("治療師・予防", false), STAR_ORBIT("星織り・公転", false),
}

/** Identity and roll are server-owned. Unknown definitions remain serializable but inert. */
data class CoreAffixStone(
    val id: UUID, val modId: String, val tier: Int, val value: Double, val definitionRevision: Int = 1,
) {
    init {
        require(Regex("[a-z0-9][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9-]{0,63}").matches(modId))
        require(tier in 1..4 && value.isFinite() && value in 0.0..1000.0 && definitionRevision >= 1)
    }
}

data class CoreEquippedAffix(val gear: CoreGearSlot, val index: Int, val stone: CoreAffixStone) {
    init { require(index in 0..5) }
}

class CoreAffixDefinition(
    val id: String, val displayName: String, val stat: CoreAffixStat, val category: CoreAffixCategory,
    private val baseMinimum: Int, private val baseMaximum: Int, private val perTier: Int,
    allowedGear: Set<CoreGearSlot> = CoreGearSlot.entries.toSet(), val weight: Int = 10,
) {
    val allowedGear: Set<CoreGearSlot> = Collections.unmodifiableSet(allowedGear.toSet())
    val group: CoreAffixGroup = when (stat) {
        CoreAffixStat.DAMAGE, CoreAffixStat.SKILL_DAMAGE, CoreAffixStat.NORMAL_DAMAGE, CoreAffixStat.MAX_MANA,
        CoreAffixStat.HEALTH, CoreAffixStat.FIRE, CoreAffixStat.ICE, CoreAffixStat.LIGHTNING,
        CoreAffixStat.AD_FLAT, CoreAffixStat.AD_PERCENT, CoreAffixStat.AP_FLAT, CoreAffixStat.AP_PERCENT,
        CoreAffixStat.AR_FLAT, CoreAffixStat.MR_FLAT, CoreAffixStat.HEALTH_PERCENT,
        CoreAffixStat.MANA_PERCENT, CoreAffixStat.HEALING_FLAT -> CoreAffixGroup.PREFIX
        else -> CoreAffixGroup.SUFFIX
    }
    fun range(tier: Int): IntRange {
        require(tier in 1..4)
        return (baseMinimum + perTier * (tier - 1))..(baseMaximum + perTier * (tier - 1))
    }
}

/** Percent fields use percentage points (10 means +10%), never hidden multiplicative layers. */
data class CoreAffixStats(
    val damagePercent: Double = 0.0,
    val attackSpeedPercent: Double = 0.0,
    val skillDamagePercent: Double = 0.0,
    val maxManaFlat: Double = 0.0,
    val manaRegenPercent: Double = 0.0,
    val cooldownReductionPercent: Double = 0.0,
    val healthFlat: Double = 0.0,
    val mitigationPercent: Double = 0.0,
    val moveSpeedPercent: Double = 0.0,
    val critChanceIncreasedPercent: Double = 0.0,
    val critMultiplierBonusPercent: Double = 0.0,
    val normalDamagePercent: Double = 0.0,
    val castReductionPercent: Double = 0.0,
    val fireFlat: Double = 0.0,
    val iceFlat: Double = 0.0,
    val lightningFlat: Double = 0.0,
    val additional: Map<CoreAffixStat, Double> = emptyMap(),
) {
    fun bonus(stat: CoreAffixStat): Double = CoreCombatMath.safe(additional[stat] ?: 0.0)
    val criticalChance: Double get() = (0.05 * (1.0 + CoreCombatMath.safe(critChanceIncreasedPercent) / 100.0)).coerceAtMost(0.75)
    val criticalMultiplier: Double get() = (CoreCombatMath.BASE_CRITICAL_MULTIPLIER + CoreCombatMath.safe(critMultiplierBonusPercent) / 100.0).coerceAtMost(4.0)
}

/** Stable IDs survive old saves. Values are percentage points; consumers live in CoreCombatMath. */
object CoreAffixCatalog {
    const val MAX_STONES = 256
    val definitions: List<CoreAffixDefinition> = Collections.unmodifiableList(listOf(
        CoreAffixDefinition("projects:force", "剛力の刻印石", CoreAffixStat.DAMAGE, CoreAffixCategory.OFFENSE, 5, 10, 4),
        CoreAffixDefinition("projects:haste", "疾撃の刻印石", CoreAffixStat.ATTACK_SPEED, CoreAffixCategory.OFFENSE, 3, 6, 3),
        CoreAffixDefinition("projects:technique", "技力の刻印石", CoreAffixStat.SKILL_DAMAGE, CoreAffixCategory.OFFENSE, 8, 14, 5),
        CoreAffixDefinition("projects:reservoir", "魔力の刻印石", CoreAffixStat.MAX_MANA, CoreAffixCategory.RESOURCE, 8, 15, 5),
        CoreAffixDefinition("projects:renewal", "循環の刻印石", CoreAffixStat.MANA_REGEN, CoreAffixCategory.RESOURCE, 8, 15, 5),
        CoreAffixDefinition("projects:focus", "集中の刻印石", CoreAffixStat.COOLDOWN_REDUCTION, CoreAffixCategory.RESOURCE, 3, 5, 2),
        CoreAffixDefinition("projects:vitality", "生命の刻印石", CoreAffixStat.HEALTH, CoreAffixCategory.DEFENSE, 8, 15, 8),
        CoreAffixDefinition("projects:guard", "守護の刻印石", CoreAffixStat.MITIGATION, CoreAffixCategory.DEFENSE, 2, 3, 1,
            setOf(CoreGearSlot.ARMOR)),
        CoreAffixDefinition("projects:stride", "軽歩の刻印石", CoreAffixStat.MOVE_SPEED, CoreAffixCategory.UTILITY, 2, 3, 1,
            setOf(CoreGearSlot.ARMOR)),
        CoreAffixDefinition("projects:precision", "会心の刻印石", CoreAffixStat.CRIT_CHANCE_INCREASED, CoreAffixCategory.OFFENSE, 10, 20, 10),
        CoreAffixDefinition("projects:ferocity", "痛撃の刻印石", CoreAffixStat.CRIT_MULTIPLIER, CoreAffixCategory.OFFENSE, 8, 15, 5),
        CoreAffixDefinition("projects:onslaught", "連撃の刻印石", CoreAffixStat.NORMAL_DAMAGE, CoreAffixCategory.OFFENSE, 8, 14, 5),
        CoreAffixDefinition("projects:celerity", "速詠の刻印石", CoreAffixStat.CAST_REDUCTION, CoreAffixCategory.RESOURCE, 3, 5, 2),
        CoreAffixDefinition("projects:flame", "火炎の刻印石", CoreAffixStat.FIRE, CoreAffixCategory.OFFENSE, 2, 4, 2),
        CoreAffixDefinition("projects:frost", "氷結の刻印石", CoreAffixStat.ICE, CoreAffixCategory.OFFENSE, 2, 4, 2),
        CoreAffixDefinition("projects:storm", "雷鳴の刻印石", CoreAffixStat.LIGHTNING, CoreAffixCategory.OFFENSE, 2, 4, 2),
        CoreAffixDefinition("projects:edge", "刃の刻印石", CoreAffixStat.AD_FLAT, CoreAffixCategory.OFFENSE, 2, 4, 2),
        CoreAffixDefinition("projects:might", "武威の刻印石", CoreAffixStat.AD_PERCENT, CoreAffixCategory.OFFENSE, 8, 14, 5),
        CoreAffixDefinition("projects:insight", "叡智の刻印石", CoreAffixStat.AP_FLAT, CoreAffixCategory.OFFENSE, 3, 6, 3),
        CoreAffixDefinition("projects:sorcery", "魔術の刻印石", CoreAffixStat.AP_PERCENT, CoreAffixCategory.OFFENSE, 8, 14, 5),
        CoreAffixDefinition("projects:plate", "鋼壁の刻印石", CoreAffixStat.AR_FLAT, CoreAffixCategory.DEFENSE, 12, 24, 12),
        CoreAffixDefinition("projects:bulwark", "堅牢の刻印石", CoreAffixStat.AR_PERCENT, CoreAffixCategory.DEFENSE, 10, 20, 6),
        CoreAffixDefinition("projects:ward", "結界の刻印石", CoreAffixStat.MR_FLAT, CoreAffixCategory.DEFENSE, 12, 24, 12),
        CoreAffixDefinition("projects:aegis", "抗魔の刻印石", CoreAffixStat.MR_PERCENT, CoreAffixCategory.DEFENSE, 10, 20, 6),
        CoreAffixDefinition("projects:brutality", "武技の刻印石", CoreAffixStat.PHYSICAL_DAMAGE, CoreAffixCategory.OFFENSE, 8, 14, 5),
        CoreAffixDefinition("projects:arcane", "秘術の刻印石", CoreAffixStat.MAGICAL_DAMAGE, CoreAffixCategory.OFFENSE, 8, 14, 5),
        CoreAffixDefinition("projects:close-combat", "接戦の刻印石", CoreAffixStat.MELEE_DAMAGE, CoreAffixCategory.OFFENSE, 8, 14, 5),
        CoreAffixDefinition("projects:ballistics", "弾道の刻印石", CoreAffixStat.PROJECTILE_DAMAGE, CoreAffixCategory.OFFENSE, 8, 14, 5),
        CoreAffixDefinition("projects:puncture", "穿甲の刻印石", CoreAffixStat.PHYSICAL_PEN_FLAT, CoreAffixCategory.OFFENSE, 5, 10, 5),
        CoreAffixDefinition("projects:breach", "破甲の刻印石", CoreAffixStat.PHYSICAL_PEN_PERCENT, CoreAffixCategory.OFFENSE, 3, 6, 3),
        CoreAffixDefinition("projects:dispel", "破魔の刻印石", CoreAffixStat.MAGICAL_PEN_FLAT, CoreAffixCategory.OFFENSE, 5, 10, 5),
        CoreAffixDefinition("projects:unravel", "解呪の刻印石", CoreAffixStat.MAGICAL_PEN_PERCENT, CoreAffixCategory.OFFENSE, 3, 6, 3),
        CoreAffixDefinition("projects:vigor", "活力の刻印石", CoreAffixStat.HEALTH_PERCENT, CoreAffixCategory.DEFENSE, 3, 6, 2),
        CoreAffixDefinition("projects:deep-well", "深泉の刻印石", CoreAffixStat.MANA_PERCENT, CoreAffixCategory.RESOURCE, 4, 8, 3),
        CoreAffixDefinition("projects:spring", "湧泉の刻印石", CoreAffixStat.MANA_REGEN_FLAT, CoreAffixCategory.RESOURCE, 1, 2, 1),
        CoreAffixDefinition("projects:restoration", "癒力の刻印石", CoreAffixStat.HEALING_FLAT, CoreAffixCategory.RESOURCE, 3, 6, 3),
        CoreAffixDefinition("projects:grace", "慈愛の刻印石", CoreAffixStat.HEALING_PERCENT, CoreAffixCategory.RESOURCE, 8, 14, 5),
        CoreAffixDefinition("projects:benediction", "施療の刻印石", CoreAffixStat.OUTGOING_HEALING, CoreAffixCategory.RESOURCE, 5, 10, 3),
        CoreAffixDefinition("projects:receptivity", "受容の刻印石", CoreAffixStat.INCOMING_HEALING, CoreAffixCategory.DEFENSE, 5, 10, 3),
        CoreAffixDefinition("projects:siphon", "吸命の刻印石", CoreAffixStat.LIFESTEAL, CoreAffixCategory.OFFENSE, 1, 2, 1),
        CoreAffixDefinition("projects:sanctuary", "庇護の刻印石", CoreAffixStat.SHIELD_POWER, CoreAffixCategory.DEFENSE, 6, 12, 4),
        CoreAffixDefinition("projects:aftershock", "余波の刻印石", CoreAffixStat.WAR_AFTERSHOCK, CoreAffixCategory.OFFENSE, 1, 4, 1, weight=3),
        CoreAffixDefinition("projects:spell-echo", "残響の刻印石", CoreAffixStat.MAGE_ECHO, CoreAffixCategory.OFFENSE, 1, 4, 1, weight=3),
        CoreAffixDefinition("projects:returning-arrow", "追矢の刻印石", CoreAffixStat.HUNT_RETURN, CoreAffixCategory.OFFENSE, 1, 4, 1, weight=3),
        CoreAffixDefinition("projects:venom-mark", "毒印の刻印石", CoreAffixStat.ASS_VENOM, CoreAffixCategory.OFFENSE, 1, 4, 1, weight=3),
        CoreAffixDefinition("projects:gravity-well", "重力の刻印石", CoreAffixStat.TEMP_GRAVITY, CoreAffixCategory.UTILITY, 1, 4, 1, weight=3),
        CoreAffixDefinition("projects:preventive-prayer", "予防の刻印石", CoreAffixStat.HEAL_CONVERSION, CoreAffixCategory.DEFENSE, 1, 4, 1, weight=3),
        CoreAffixDefinition("projects:orbit", "公転の刻印石", CoreAffixStat.STAR_ORBIT, CoreAffixCategory.OFFENSE, 1, 4, 1, weight=3),
    ))
    private val byId = definitions.associateBy { it.id }

    fun definition(stone: CoreAffixStone): CoreAffixDefinition? = byId[stone.modId]?.takeIf { stone.definitionRevision == 1 }
    fun valid(stone: CoreAffixStone): Boolean = definition(stone)?.range(stone.tier)?.let { stone.value in it.first.toDouble()..it.last.toDouble() } == true
    fun gearTier(account: CoreAccount, gear: CoreGearSlot): Int = if (gear == CoreGearSlot.WEAPON) account.weaponTier else account.armorTier
    fun capacity(account: CoreAccount, gear: CoreGearSlot): Int = rarity(account, gear).capacity
    fun rarity(account: CoreAccount, gear: CoreGearSlot): CoreGearRarity = if (gear == CoreGearSlot.WEAPON) account.weaponRarity else account.armorRarity
    fun qualityPercent(stone: CoreAffixStone): Int {
        val range = definition(stone)?.range(stone.tier) ?: return 0
        return ((stone.value - range.first) / (range.last - range.first).coerceAtLeast(1) * 100).roundToInt().coerceIn(0, 100)
    }
    fun describe(stone: CoreAffixStone): String {
        val definition = definition(stone) ?: return "未対応のMOD（効果停止）"
        if (!valid(stone)) return "不正なMOD値（効果停止）"
        val v = stone.value.toInt()
        when (definition.stat) {
            CoreAffixStat.WAR_AFTERSHOCK -> return "戦士：範囲+${v * 8}% / 範囲技の威力85%"
            CoreAffixStat.MAGE_ECHO -> return "メイジ：起爆+1回 / 各${65 + v}%威力"
            CoreAffixStat.HUNT_RETURN -> return "狩人：スキル射撃+1回 / 各${60 + v}%威力"
            CoreAffixStat.ASS_VENOM -> return "暗殺者：印を付けた敵に${10 + v * 2}% ADの毒を3回"
            CoreAffixStat.TEMP_GRAVITY -> return "聖騎士：吸引+${v * 10}% / 威力80%"
            CoreAffixStat.HEAL_CONVERSION -> return "治療師：回復の${25 + v * 3}%を同量の障壁に変換"
            CoreAffixStat.STAR_ORBIT -> return "星織り：三蓄積の解放+1回 / 各${65 + v}%威力"
            else -> Unit
        }
        val value = if (stone.value % 1.0 == 0.0) stone.value.toInt().toString() else java.lang.String.format(java.util.Locale.ROOT, "%.1f", stone.value)
        return "${definition.stat.displayName} +$value${if (definition.stat.percent) "%" else ""}"
    }

    /** Same source and map always yield the same identities/rolls, across retries and restarts. */
    fun rollLoot(run: CoreActiveRun, sourceId: String, kind: CoreLootKind): List<CoreAffixStone> {
        requireSource(sourceId)
        val source = "affix-v1/${run.id}/$sourceId"
        val random = random("$source/${run.map.seed}")
        val count = when (kind) { CoreLootKind.NORMAL -> if (random.nextInt(100) < 35) 1 else 0; CoreLootKind.ELITE -> 2; CoreLootKind.BOSS -> 3 }
        return List(count) { index ->
            var choice = random.nextInt(definitions.sumOf { it.weight })
            val definition = definitions.first { choice -= it.weight; choice < 0 }
            val range = definition.range(run.map.tier)
            CoreAffixStone(derived("$source/$index"), definition.id, run.map.tier,
                (range.first + random.nextInt(range.last - range.first + 1)).toDouble())
        }
    }

    fun lootDust(kind: CoreLootKind): Long = when (kind) { CoreLootKind.NORMAL -> 1L; CoreLootKind.ELITE -> 3L; CoreLootKind.BOSS -> 6L }
    fun lootTokens(kind: CoreLootKind): Long = when (kind) { CoreLootKind.NORMAL -> 2L; CoreLootKind.ELITE -> 6L; CoreLootKind.BOSS -> 0L }
    fun salvageDust(stone: CoreAffixStone): Long = stone.tier * 2L
    fun extractionRecipe(stone: CoreAffixStone) = CoreRecipe("MODを石に抽出", mapOf(CoreMaterial(CoreResource.AFFIX_DUST) to stone.tier * 2L), emptyMap())
    fun rerollRecipe(stone: CoreAffixStone) = CoreRecipe("刻印石の数値を再抽選", mapOf(
        CoreMaterial(CoreResource.AFFIX_DUST) to stone.tier * 3L,
        CoreMaterial(CoreResource.STONE_BLOCK, stone.tier) to 1L), emptyMap())
    fun reroll(stone: CoreAffixStone, requestId: UUID): CoreAffixStone {
        val definition = requireNotNull(definition(stone)) { "未対応のMODは再抽選できません" }
        require(valid(stone)) { "不正なMOD値です" }
        val range = definition.range(stone.tier)
        return stone.copy(value = (range.first + random("reroll/$requestId/${stone.id}").nextInt(range.last - range.first + 1)).toDouble())
    }

    fun stats(account: CoreAccount): CoreAffixStats {
        val totals = mutableMapOf<CoreAffixStat, Double>()
        account.equippedAffixes.forEach { installed ->
            val definition = definition(installed.stone)
            if (!CoreEconomy.broken(account, installed.gear) && definition != null && valid(installed.stone) && installed.gear in definition.allowedGear &&
                installed.index < capacity(account, installed.gear) && installed.stone.tier <= gearTier(account, installed.gear)) {
                totals.merge(definition.stat, installed.stone.value, Double::plus)
            }
        }
        fun stat(type: CoreAffixStat, limit: Double) = (totals[type] ?: 0.0).coerceIn(0.0, limit)
        return CoreAffixStats(stat(CoreAffixStat.DAMAGE, 100.0), stat(CoreAffixStat.ATTACK_SPEED, 60.0),
            stat(CoreAffixStat.SKILL_DAMAGE, 100.0), stat(CoreAffixStat.MAX_MANA, 100.0),
            stat(CoreAffixStat.MANA_REGEN, 100.0), stat(CoreAffixStat.COOLDOWN_REDUCTION, 45.0),
            stat(CoreAffixStat.HEALTH, 200.0), stat(CoreAffixStat.MITIGATION, 45.0),
            stat(CoreAffixStat.MOVE_SPEED, 25.0), stat(CoreAffixStat.CRIT_CHANCE_INCREASED, 200.0),
            stat(CoreAffixStat.CRIT_MULTIPLIER, 100.0), stat(CoreAffixStat.NORMAL_DAMAGE, 100.0),
            stat(CoreAffixStat.CAST_REDUCTION, 40.0), stat(CoreAffixStat.FIRE, 100.0),
            stat(CoreAffixStat.ICE, 100.0), stat(CoreAffixStat.LIGHTNING, 100.0),
            Collections.unmodifiableMap(totals.toMap()))
    }

    internal fun requireSource(sourceId: String) {
        require(sourceId.length in 1..128 && sourceId.all { it.isLetterOrDigit() || it in "_-.:/" }) { "報酬元が不正です" }
    }
    private fun derived(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray(UTF_8))
    private fun random(value: String): Random = derived(value).let { Random(it.mostSignificantBits xor it.leastSignificantBits) }
}
