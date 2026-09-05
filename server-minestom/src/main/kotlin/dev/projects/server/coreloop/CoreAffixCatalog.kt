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
    DAMAGE("攻撃力"), ATTACK_SPEED("攻撃速度"), SKILL_DAMAGE("スキルダメージ"),
    MAX_MANA("最大マナ", false), MANA_REGEN("マナ回復速度"), COOLDOWN_REDUCTION("クールダウン短縮"),
    HEALTH("最大HP", false), MITIGATION("被ダメージ軽減"), MOVE_SPEED("移動速度"),
    CRIT_CHANCE_INCREASED("クリティカル率増加"), CRIT_MULTIPLIER("クリティカル倍率"),
    NORMAL_DAMAGE("通常攻撃ダメージ"), CAST_REDUCTION("キャスト短縮"),
    FIRE("火属性値", false), ICE("氷属性値", false), LIGHTNING("雷属性値", false),
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
        CoreAffixStat.HEALTH, CoreAffixStat.FIRE, CoreAffixStat.ICE, CoreAffixStat.LIGHTNING -> CoreAffixGroup.PREFIX
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
) {
    val criticalChance: Double get() = (0.05 * (1.0 + critChanceIncreasedPercent / 100.0)).coerceAtMost(0.75)
    val criticalMultiplier: Double get() = (1.5 + critMultiplierBonusPercent / 100.0).coerceAtMost(4.0)
}

/** Provisional, data-driven sixteen-effect catalog; every listed stat has a core combat consumer. */
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
            if (definition != null && valid(installed.stone) && installed.gear in definition.allowedGear &&
                installed.index < capacity(account, installed.gear) && installed.stone.tier <= gearTier(account, installed.gear)) {
                totals.merge(definition.stat, installed.stone.value, Double::plus)
            }
        }
        fun stat(type: CoreAffixStat, limit: Double) = (totals[type] ?: 0.0).coerceIn(0.0, limit)
        return CoreAffixStats(stat(CoreAffixStat.DAMAGE, 100.0) - if (account.weaponCondition == 0) 25.0 else 0.0, stat(CoreAffixStat.ATTACK_SPEED, 60.0),
            stat(CoreAffixStat.SKILL_DAMAGE, 100.0), stat(CoreAffixStat.MAX_MANA, 100.0),
            stat(CoreAffixStat.MANA_REGEN, 100.0), stat(CoreAffixStat.COOLDOWN_REDUCTION, 45.0),
            stat(CoreAffixStat.HEALTH, 200.0) - if (account.armorCondition == 0) 20.0 else 0.0, stat(CoreAffixStat.MITIGATION, 45.0),
            stat(CoreAffixStat.MOVE_SPEED, 25.0), stat(CoreAffixStat.CRIT_CHANCE_INCREASED, 200.0),
            stat(CoreAffixStat.CRIT_MULTIPLIER, 100.0), stat(CoreAffixStat.NORMAL_DAMAGE, 100.0),
            stat(CoreAffixStat.CAST_REDUCTION, 40.0), stat(CoreAffixStat.FIRE, 100.0),
            stat(CoreAffixStat.ICE, 100.0), stat(CoreAffixStat.LIGHTNING, 100.0))
    }

    internal fun requireSource(sourceId: String) {
        require(sourceId.length in 1..128 && sourceId.all { it.isLetterOrDigit() || it in "_-.:/" }) { "報酬元が不正です" }
    }
    private fun derived(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray(UTF_8))
    private fun random(value: String): Random = derived(value).let { Random(it.mostSignificantBits xor it.leastSignificantBits) }
}
