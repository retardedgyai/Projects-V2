package dev.projects.server.coreloop

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Collections
import java.util.Random
import java.util.UUID

enum class CoreGearRarity(val displayName: String, val capacity: Int, val groupCapacity: Int) {
    NORMAL("ノーマル", 0, 0), MAGIC("マジック", 2, 1), RARE("レア", 6, 3),
}

enum class CoreCraftingCurrency(val displayName: String) {
    TRANSMUTATION("変成のオーブ"), AUGMENTATION("増強のオーブ"), ALTERATION("改変のオーブ"),
    ALCHEMY("錬金のオーブ"), CHAOS("混沌のオーブ"), REGAL("富豪のオーブ"),
    EXALTED("高揚のオーブ"), SCOURING("洗浄のオーブ"), DIVINE("神聖のオーブ"),
    RIFT("裂け目のオーブ"), RITUAL("儀式のオーブ"), TRIAL("試練のオーブ"), ASTRAL("星環のオーブ"),
}

enum class CoreActivityKind(val displayName: String, val bossId: String, val currency: CoreCraftingCurrency) {
    RIFT("裂け目", "rift", CoreCraftingCurrency.RIFT),
    RITUAL("儀式", "ritual", CoreCraftingCurrency.RITUAL),
    TRIAL("試練", "trial", CoreCraftingCurrency.TRIAL),
}

/** Currency drops reveal no affix. Only the committed server operation rolls equipment. */
object CoreCraftingCatalog {
    const val TRIAL_ENTRY_FRAGMENTS = 3L
    private val elements = setOf(CoreAffixStat.FIRE, CoreAffixStat.ICE, CoreAffixStat.LIGHTNING)
    private val ordinaryWeights = linkedMapOf(
        CoreCraftingCurrency.TRANSMUTATION to 18, CoreCraftingCurrency.AUGMENTATION to 15,
        CoreCraftingCurrency.ALTERATION to 20, CoreCraftingCurrency.ALCHEMY to 12,
        CoreCraftingCurrency.CHAOS to 10, CoreCraftingCurrency.REGAL to 8,
        CoreCraftingCurrency.EXALTED to 4, CoreCraftingCurrency.SCOURING to 9,
        CoreCraftingCurrency.DIVINE to 4,
    )

    fun description(currency: CoreCraftingCurrency): String = when (currency) {
        CoreCraftingCurrency.TRANSMUTATION -> "ノーマルをマジックに変え、ランダムなMODを1〜2個付与"
        CoreCraftingCurrency.AUGMENTATION -> "マジックの空き枠へランダムなMODを1個追加"
        CoreCraftingCurrency.ALTERATION -> "マジックのMODをすべて消し、1〜2個を再抽選"
        CoreCraftingCurrency.ALCHEMY -> "ノーマルをレアに変え、ランダムなMODを4〜6個付与"
        CoreCraftingCurrency.CHAOS -> "レアのMODをすべて消し、4〜6個を再抽選"
        CoreCraftingCurrency.REGAL -> "マジックのMODを残してレアに変え、1個追加"
        CoreCraftingCurrency.EXALTED -> "レアの空き枠へランダムなMODを1個追加"
        CoreCraftingCurrency.SCOURING -> "MODをすべて消し、ノーマルに戻す（取り消し不可）"
        CoreCraftingCurrency.DIVINE -> "MODの種類とTierを保ち、数値だけ再抽選（低下する場合あり）"
        CoreCraftingCurrency.RIFT -> "全MODを再抽選し、元素MODを1個以上含むレア4〜6個へ変える"
        CoreCraftingCurrency.RITUAL -> "MODの数値をそれぞれ2回抽選し、高い方を採用（元の値を保証しない）"
        CoreCraftingCurrency.TRIAL -> "レアの空き枠へ、数値範囲の上位25％からMODを1個追加"
        CoreCraftingCurrency.ASTRAL -> "レア装備のMODをランダムに1個置換。他のMOD・強化・製造品質は保持（改善保証なし）"
    }

    /** Null is usable. This never rolls or reveals the next affix. */
    fun canUse(account: CoreAccount, gear: CoreGearSlot, currency: CoreCraftingCurrency): String? {
        if (account.activeRun != null) return "拠点で操作してください"
        if (CoreEconomy.broken(account, gear)) return "破損しています。先に装備庫で修理してください"
        if (account.amount(currency) < 1) return "${currency.displayName}が足りません"
        val rarity = CoreAffixCatalog.rarity(account, gear)
        val installed = account.equippedAffixes.filter { it.gear == gear }
        return when (currency) {
            CoreCraftingCurrency.TRANSMUTATION, CoreCraftingCurrency.ALCHEMY ->
                if (rarity != CoreGearRarity.NORMAL) "ノーマル装備に使用できます" else null
            CoreCraftingCurrency.ALTERATION -> if (rarity != CoreGearRarity.MAGIC) "マジック装備に使用できます" else null
            CoreCraftingCurrency.CHAOS -> if (rarity != CoreGearRarity.RARE) "レア装備に使用できます" else null
            CoreCraftingCurrency.AUGMENTATION -> addReason(account, gear, CoreGearRarity.MAGIC)
            CoreCraftingCurrency.EXALTED, CoreCraftingCurrency.TRIAL -> addReason(account, gear, CoreGearRarity.RARE)
            CoreCraftingCurrency.REGAL -> if (rarity != CoreGearRarity.MAGIC) "マジック装備に使用できます"
                else if (gear in account.legacyLayouts) "旧形式のMOD構成です。改変で新形式へ移してください" else null
            CoreCraftingCurrency.SCOURING -> if (rarity == CoreGearRarity.NORMAL) "すでにノーマルです" else null
            CoreCraftingCurrency.DIVINE, CoreCraftingCurrency.RITUAL -> if (installed.isEmpty()) "再抽選するMODがありません"
                else if (installed.any { !CoreAffixCatalog.valid(it.stone) }) "未対応のMODは数値を変更せず保管してください" else null
            CoreCraftingCurrency.ASTRAL -> if (rarity != CoreGearRarity.RARE || installed.isEmpty()) "MODのあるレア装備に使用できます"
                else if (gear in account.legacyLayouts) "旧形式のMOD構成です。先に混沌で移行してください" else null
            CoreCraftingCurrency.RIFT -> null
        }
    }

    private fun addReason(account: CoreAccount, gear: CoreGearSlot, required: CoreGearRarity): String? = when {
        CoreAffixCatalog.rarity(account, gear) != required -> "${required.displayName}装備に使用できます"
        gear in account.legacyLayouts -> "旧形式のMOD構成です。再鋳造で新形式へ移してください"
        account.equippedAffixes.count { it.gear == gear } >= required.capacity -> "MOD枠が満杯です"
        else -> null
    }

    internal fun craft(account: CoreAccount, gear: CoreGearSlot, currency: CoreCraftingCurrency, requestId: UUID): CoreAccount {
        require(canUse(account, gear, currency) == null) { canUse(account, gear, currency) ?: "使用できません" }
        val random = random("craft-v1/${account.craftingSeed}/${account.playerId}/${account.revision}/$requestId/$gear/$currency")
        val old = account.equippedAffixes.filter { it.gear == gear }.sortedBy { it.index }
        var rarity = CoreAffixCatalog.rarity(account, gear)
        var legacy = account.legacyLayouts
        val tier = CoreAffixCatalog.gearTier(account, gear)
        fun add(list: List<CoreEquippedAffix>, target: CoreGearRarity, elemental: Boolean = false, high: Boolean = false): List<CoreEquippedAffix> {
            val occupied = list.map { it.index }.toSet()
            val index = (0 until target.capacity).first { it !in occupied }
            val used = list.map { it.stone.modId }.toSet()
            val groups = list.groupingBy { CoreAffixCatalog.definition(it.stone)!!.group }.eachCount()
            val eligible = CoreAffixCatalog.definitions.filter {
                gear in it.allowedGear && it.id !in used && (groups[it.group] ?: 0) < target.groupCapacity && (!elemental || it.stat in elements)
            }
            require(eligible.isNotEmpty()) { "追加できるMODがありません" }
            var choice = random.nextInt(eligible.sumOf { it.weight })
            val definition = eligible.first { choice -= it.weight; choice < 0 }
            val range = definition.range(tier)
            val lower = if (high) range.first + kotlin.math.ceil((range.last - range.first) * 0.75).toInt() else range.first
            val value = lower + random.nextInt(range.last - lower + 1)
            val stone = CoreAffixStone(derived("craft-affix/$requestId/$gear/$index"), definition.id, tier, value.toDouble())
            return list + CoreEquippedAffix(gear, index, stone)
        }
        fun replace(target: CoreGearRarity, elemental: Boolean = false): List<CoreEquippedAffix> {
            rarity = target
            legacy = legacy - gear
            val count = if (target == CoreGearRarity.MAGIC) 1 + random.nextInt(2) else 4 + random.nextInt(3)
            var rolled = emptyList<CoreEquippedAffix>()
            repeat(count) { rolled = add(rolled, target, elemental && it == 0) }
            return rolled
        }
        val next = when (currency) {
            CoreCraftingCurrency.TRANSMUTATION, CoreCraftingCurrency.ALTERATION -> replace(CoreGearRarity.MAGIC)
            CoreCraftingCurrency.ALCHEMY, CoreCraftingCurrency.CHAOS -> replace(CoreGearRarity.RARE)
            CoreCraftingCurrency.RIFT -> replace(CoreGearRarity.RARE, true)
            CoreCraftingCurrency.REGAL -> { rarity = CoreGearRarity.RARE; add(old, rarity) }
            CoreCraftingCurrency.AUGMENTATION, CoreCraftingCurrency.EXALTED -> add(old, rarity)
            CoreCraftingCurrency.TRIAL -> add(old, rarity, high = true)
            CoreCraftingCurrency.ASTRAL -> add(old - old[random.nextInt(old.size)], rarity)
            CoreCraftingCurrency.SCOURING -> { rarity = CoreGearRarity.NORMAL; legacy = legacy - gear; emptyList() }
            CoreCraftingCurrency.DIVINE, CoreCraftingCurrency.RITUAL -> old.map { installed ->
                val range = CoreAffixCatalog.definition(installed.stone)!!.range(installed.stone.tier)
                fun roll() = range.first + random.nextInt(range.last - range.first + 1)
                val value = if (currency == CoreCraftingCurrency.RITUAL) maxOf(roll(), roll()) else roll()
                installed.copy(stone = installed.stone.copy(value = value.toDouble()))
            }
        }
        return account.copy(
            equippedAffixes = account.equippedAffixes.filterNot { it.gear == gear } + next,
            weaponRarity = if (gear == CoreGearSlot.WEAPON) rarity else account.weaponRarity,
            armorRarity = if (gear == CoreGearSlot.ARMOR) rarity else account.armorRarity,
            currencies = account.currencies + (currency to account.amount(currency) - 1), legacyLayouts = legacy,
        )
    }

    /** Deterministic preview contains only unrolled currency, never known-effect stones. */
    fun rollLoot(run: CoreActiveRun, sourceId: String, kind: CoreLootKind): Map<CoreCraftingCurrency, Long> {
        CoreAffixCatalog.requireSource(sourceId)
        val random = random("orb-loot-v1/${run.id}/${run.map.seed}/$sourceId/$kind")
        val count = when (kind) { CoreLootKind.NORMAL -> if (random.nextInt(100) < 35) 1 else 0; CoreLootKind.ELITE -> 2; CoreLootKind.BOSS -> 3 }
        val output = linkedMapOf<CoreCraftingCurrency, Long>()
        repeat(count) {
            var choice = random.nextInt(ordinaryWeights.values.sum())
            val currency = ordinaryWeights.entries.first { choice -= it.value; choice < 0 }.key
            output[currency] = (output[currency] ?: 0) + 1
        }
        return Collections.unmodifiableMap(output)
    }

    internal fun inferRarity(equipped: List<CoreEquippedAffix>, gear: CoreGearSlot): CoreGearRarity {
        val extent = equipped.filter { it.gear == gear }.maxOfOrNull { it.index + 1 } ?: 0
        return when { extent == 0 -> CoreGearRarity.NORMAL; extent <= 2 -> CoreGearRarity.MAGIC; else -> CoreGearRarity.RARE }
    }
    internal fun validLayout(equipped: List<CoreEquippedAffix>, rarity: CoreGearRarity): Boolean =
        equipped.size <= rarity.capacity && equipped.all { it.index < rarity.capacity && CoreAffixCatalog.definition(it.stone) != null } &&
            equipped.groupingBy { CoreAffixCatalog.definition(it.stone)?.group }.eachCount().values.all { it <= rarity.groupCapacity }

    internal fun legacyLayouts(equipped: List<CoreEquippedAffix>, weapon: CoreGearRarity, armor: CoreGearRarity): Set<CoreGearSlot> =
        CoreGearSlot.entries.filter { !validLayout(equipped.filter { installed -> installed.gear == it }, if (it == CoreGearSlot.WEAPON) weapon else armor) }.toSet()

    internal fun legacySeed(playerId: UUID): Long = derived("projects/legacy-craft-seed/$playerId").leastSignificantBits
    private fun derived(text: String): UUID = UUID.nameUUIDFromBytes(text.toByteArray(UTF_8))
    private fun random(text: String) = derived(text).let { Random(it.leastSignificantBits xor it.mostSignificantBits) }
}
