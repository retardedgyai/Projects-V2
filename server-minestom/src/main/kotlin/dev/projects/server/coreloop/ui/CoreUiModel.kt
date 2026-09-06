package dev.projects.server.coreloop.ui

import net.kyori.adventure.text.format.TextColor

enum class CoreUiRarity(val japanese: String, val color: TextColor) {
    COMMON("コモン", TextColor.color(0xD2CEC4)),
    UNCOMMON("アンコモン", TextColor.color(0x8FBD99)),
    RARE("レア", TextColor.color(0x84B8D5)),
    EPIC("エピック", TextColor.color(0xC3A0DC));

    val style: String get() = "projects:item_${name.lowercase()}"
}

enum class CoreUiIcon(val glyph: Char, val fallback: String, val asset: String) {
    ATTACK('\uE001', "攻", "attack_power"), SPEED('\uE002', "速", "attack_speed"),
    CRITICAL('\uE003', "会", "magic_power"), DEFENSE('\uE004', "防", "defense"),
    HEALTH('\uE005', "HP", "health"), MAGIC('\uE006', "魔", "magic_power"),
    MANA('\uE007', "MP", "mana"), REWARD('\uE008', "報", "xp"),
    MOD('\uE009', "◆", "level"),
    DASH('\uE021', "踏込", "dash"), SLAM('\uE022', "地砕", "slam"), WHIRL('\uE023', "旋風", "whirl"),
}

data class CoreTooltipStat(val label: String, val value: String, val icon: CoreUiIcon = CoreUiIcon.MOD)
data class CoreTooltipAffix(
    val name: String,
    val effect: String,
    val range: String,
    val qualityPercent: Int,
    val rank: String = "I",
)
data class CoreTooltipModel(
    val name: String,
    val rarity: CoreUiRarity = CoreUiRarity.COMMON,
    val tier: Int = 1,
    val itemLevel: Int = 1,
    val typeLabel: String = "装備",
    val stats: List<CoreTooltipStat> = emptyList(),
    val affixes: List<CoreTooltipAffix> = emptyList(),
    val modCapacity: Int = 0,
    val footer: List<String> = emptyList(),
    val rarityLabel: String = rarity.name,
)

data class CoreHudSkill(
    val icon: CoreUiIcon,
    val key: String,
    val remainingSeconds: Double,
    val totalSeconds: Double,
    val manaCost: Int = 0,
    val artIndex: Int? = null,
    val unlocked: Boolean = true,
)
data class CoreHudState(
    val health: Double,
    val maxHealth: Double,
    val mana: Double,
    val maxMana: Double = 100.0,
    val skills: List<CoreHudSkill> = emptyList(),
    val hint: String = "",
)
