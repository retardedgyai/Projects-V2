package dev.projects.server.mob

import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.item.Material
import java.util.UUID
import kotlin.math.pow

enum class QuestMobRarity { NORMAL, ELITE, BOSS }
enum class QuestMobDropKind { SOLDIER, GUARD, CASTER, ELITE, BOSS }

enum class QuestMobArchetype(val displayName: String, val rarity: QuestMobRarity, val weakness: String) {
    SOLDIER("追放兵", QuestMobRarity.NORMAL, "ice"),
    SHIELD_GUARD("盾持ちの追放兵", QuestMobRarity.NORMAL, "lightning"),
    RIFT_CASTER("裂け目の術師", QuestMobRarity.NORMAL, "fire"),
    ELITE_BRUTE("精鋭・裂け目の狂戦士", QuestMobRarity.ELITE, "ice"),
    EXECUTIONER("裂け目の執行官", QuestMobRarity.BOSS, "ice"),
    IRON_WARDEN("鉄壁の守護者", QuestMobRarity.BOSS, "lightning"),
    RIFT_ORACLE("裂け目の預言者", QuestMobRarity.BOSS, "fire"),
}

data class QuestMobInfo(
    val entityId: UUID,
    val archetype: QuestMobArchetype,
    val rarity: QuestMobRarity,
    val tier: Int,
    val position: Pos,
    val health: Double,
    val maximumHealth: Double,
)

/** Snapshot is assigned before the legacy defeat callback; sourceId is stable for reward deduplication. */
data class QuestMobDefeat(
    val sourceId: String,
    val entityId: UUID,
    val archetype: QuestMobArchetype,
    val rarity: QuestMobRarity,
    val dropKind: QuestMobDropKind,
    val tier: Int,
    val position: Pos,
    val killerId: UUID?,
)

internal data class QuestMobDefinition(
    val archetype: QuestMobArchetype,
    val entityType: EntityType,
    val maximumHealth: Double,
    val movementSpeed: Double,
    val activationRange: Double,
    val leashRange: Double,
    val preferredDistance: Double,
    val scale: Double,
    val weapon: Material,
    val offhand: Material = Material.AIR,
    val helmet: Material = Material.AIR,
    val chestplate: Material = Material.AIR,
    val frontalDamageMultiplier: Double = 1.0,
    val abilities: List<MobAbility>,
) {
    val name: String get() = archetype.displayName
    val boss: Boolean get() = archetype.rarity == QuestMobRarity.BOSS
    val soundFamily: String get() = when (archetype) {
        QuestMobArchetype.SHIELD_GUARD, QuestMobArchetype.IRON_WARDEN -> "husk"
        QuestMobArchetype.RIFT_CASTER, QuestMobArchetype.RIFT_ORACLE -> "evoker"
        QuestMobArchetype.ELITE_BRUTE -> "piglin_brute"
        else -> "vindicator"
    }
    val dropKind: QuestMobDropKind get() = when (archetype) {
        QuestMobArchetype.SOLDIER -> QuestMobDropKind.SOLDIER
        QuestMobArchetype.SHIELD_GUARD -> QuestMobDropKind.GUARD
        QuestMobArchetype.RIFT_CASTER -> QuestMobDropKind.CASTER
        QuestMobArchetype.ELITE_BRUTE -> QuestMobDropKind.ELITE
        else -> QuestMobDropKind.BOSS
    }
}

/** Concrete content recipes; every archetype combines actual attack and movement behavior. */
internal object QuestMobContent {
    fun boss(seed: Long): QuestMobArchetype = listOf(
        QuestMobArchetype.EXECUTIONER, QuestMobArchetype.IRON_WARDEN, QuestMobArchetype.RIFT_ORACLE,
    )[Math.floorMod(seed xor (seed ushr 32), 3).toInt()]

    fun composition(seed: Long, encounterIndex: Int, count: Int): List<QuestMobArchetype> {
        require(count in 1..4)
        val recipes = listOf(
            listOf(QuestMobArchetype.SOLDIER, QuestMobArchetype.SHIELD_GUARD, QuestMobArchetype.RIFT_CASTER, QuestMobArchetype.SOLDIER),
            listOf(QuestMobArchetype.SOLDIER, QuestMobArchetype.RIFT_CASTER, QuestMobArchetype.SOLDIER, QuestMobArchetype.SHIELD_GUARD),
            listOf(QuestMobArchetype.SHIELD_GUARD, QuestMobArchetype.RIFT_CASTER, QuestMobArchetype.ELITE_BRUTE, QuestMobArchetype.SOLDIER),
            listOf(QuestMobArchetype.SOLDIER, QuestMobArchetype.ELITE_BRUTE, QuestMobArchetype.SHIELD_GUARD, QuestMobArchetype.RIFT_CASTER),
        )
        return recipes[Math.floorMod((seed xor (seed ushr 32)) + encounterIndex, recipes.size.toLong()).toInt()].take(count)
    }

    fun definition(tier: Int, archetype: QuestMobArchetype): QuestMobDefinition {
        require(tier in 1..4)
        val factor = 1.65.pow(tier - 1)
        val boss = archetype.rarity == QuestMobRarity.BOSS
        fun attack(id: String, label: String, shape: MobAttackShape, range: Double, damage: Double,
            warning: Long = 1000, tracking: Long = 400, recovery: Long = 850, cooldown: Long = 1900,
            minimumRange: Double = 0.0, target: Boolean = false, health: Double = 1.0, weight: Int = 1) = MobAbility(
            id, label, shape, range, damage * factor, warning, tracking, recovery, cooldown, weight,
            minimumRange, if (target) MobAbilityAnchor.TARGET else MobAbilityAnchor.CASTER, health,
        )
        fun sweep(radius: Double = 3.4, damage: Double = 10.0) = attack(
            "sweep", "横薙ぎ", MobAttackShape.Sweep(radius), radius - 0.4, damage, weight = 3,
        )
        fun slam(length: Double = 5.8, width: Double = 1.25, damage: Double = 14.0) = attack(
            "slam", "前方叩きつけ", MobAttackShape.Slam(length, width), length - 0.4, damage,
            1450, 550, 1400, 3000, weight = 2,
        )
        fun definition(type: EntityType, hp: Double, speed: Double, distance: Double, scale: Double,
            weapon: Material, offhand: Material = Material.AIR, helmet: Material = Material.AIR,
            chest: Material = Material.AIR, guard: Double = 1.0, attacks: List<MobAbility>) = QuestMobDefinition(
            archetype, type, hp * factor, speed, if (boss) 28.0 else 20.0, if (boss) 38.0 else 28.0,
            distance, scale, weapon, offhand, helmet, chest, guard, attacks,
        )
        return when (archetype) {
            QuestMobArchetype.SOLDIER -> definition(EntityType.VINDICATOR, 44.0, 0.14, 2.0, 1.0,
                Material.IRON_AXE, attacks = listOf(sweep(), slam()))
            QuestMobArchetype.SHIELD_GUARD -> definition(EntityType.HUSK, 66.0, 0.105, 2.0, 1.0,
                Material.IRON_SWORD, Material.SHIELD, Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, 0.45,
                listOf(attack("shield-bash", "盾の強打", MobAttackShape.Slam(3.4, 1.25), 3.0, 12.0,
                    1150, 350, 1250, 2400), sweep(3.0, 9.0)))
            QuestMobArchetype.RIFT_CASTER -> definition(EntityType.EVOKER, 36.0, 0.13, 8.0, 1.0,
                Material.BLAZE_ROD, attacks = listOf(
                    attack("rift-bolt", "裂け目の槍", MobAttackShape.Slam(13.0, 0.75), 12.5, 12.0,
                        1250, 450, 1100, 2300, minimumRange = 3.0),
                    attack("rift-pulse", "拒絶の波", MobAttackShape.Ring(3.0), 3.5, 8.0,
                        1050, 0, 1400, 2800),
                ))
            QuestMobArchetype.ELITE_BRUTE -> definition(EntityType.PIGLIN_BRUTE, 105.0, 0.15, 2.0, 1.15,
                Material.NETHERITE_AXE, chest = Material.GOLDEN_CHESTPLATE, attacks = listOf(
                    sweep(4.0, 15.0), slam(6.0, 1.6, 20.0),
                    attack("rage-pulse", "憤怒の震撃", MobAttackShape.Ring(4.3), 4.0, 16.0,
                        1400, 0, 1500, 4500, health = 0.6),
                ))
            QuestMobArchetype.EXECUTIONER -> definition(EntityType.VINDICATOR, 300.0, 0.12, 2.0, 1.35,
                Material.NETHERITE_AXE, attacks = listOf(sweep(4.2, 16.0), slam(7.0, 1.6, 24.0),
                    attack("execution", "処刑の環", MobAttackShape.Ring(6.0, 2.0), 6.0, 28.0,
                        1700, 0, 1600, 5000, health = 0.5, weight = 3)))
            QuestMobArchetype.IRON_WARDEN -> definition(EntityType.HUSK, 380.0, 0.105, 2.5, 1.3,
                Material.NETHERITE_SWORD, Material.SHIELD, Material.IRON_HELMET, Material.IRON_CHESTPLATE, 0.5,
                listOf(slam(8.0, 1.7, 22.0),
                    attack("warden-ring", "城壁の震動", MobAttackShape.Ring(6.0, 2.3), 6.0, 26.0,
                        1700, 0, 1600, 4000),
                    attack("warden-crush", "鉄槌", MobAttackShape.Ring(3.4), 3.5, 20.0,
                        1300, 0, 1500, 3200)))
            QuestMobArchetype.RIFT_ORACLE -> definition(EntityType.EVOKER, 280.0, 0.125, 8.0, 1.25,
                Material.BLAZE_ROD, Material.ENCHANTED_BOOK, attacks = listOf(
                    attack("oracle-mark", "崩壊の刻印", MobAttackShape.Ring(2.8), 14.0, 22.0,
                        1500, 500, 1000, 2800, target = true, weight = 3),
                    attack("oracle-ray", "断絶の光", MobAttackShape.Slam(15.0, 1.0), 14.0, 24.0,
                        1400, 450, 1200, 3500, minimumRange = 3.0),
                    attack("oracle-pulse", "拒絶の波", MobAttackShape.Ring(3.2), 3.5, 14.0,
                        1100, 0, 1500, 3000),
                ))
        }
    }
}
