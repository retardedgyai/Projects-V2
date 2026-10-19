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
    CINDER_REGENT("灰燼の王", QuestMobRarity.BOSS, "ice"),
    GLACIAL_COLOSSUS("氷獄の巨兵", QuestMobRarity.BOSS, "fire"),
    TEMPEST_HIEROPHANT("嵐の司祭", QuestMobRarity.BOSS, "lightning"),
    FORGE_SENTINEL("熔星の番人", QuestMobRarity.BOSS, "ice"),
    TIDE_ARCHIVIST("蒼潮の記録者", QuestMobRarity.BOSS, "fire"),
    ECLIPSE_REGENT("星蝕の王", QuestMobRarity.BOSS, "lightning"),
    SHADE_WYRM("黒翳竜", QuestMobRarity.BOSS, "fire"),
    SAKURA_FANG("桜牙獣", QuestMobRarity.BOSS, "fire"),
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
    /** Blockbench model id under assets/bosses/ (null = vanilla body or WardenModel). */
    val model: String? = null,
    /** At or below this ratio a model boss takes its second form once (a held roar, the glowing texture). 0 = never. */
    val awakenHealthRatio: Double = 0.0,
    val awakenSpeedMultiplier: Double = 1.0,
    /** At or below this health ratio the mob enrages once: a held roar, then [enragedSpeedMultiplier]. 0 = never. */
    val enrageHealthRatio: Double = 0.0,
    val enragedSpeedMultiplier: Double = 1.0,
    /** Model bones shown only after enraging (e.g. horns). */
    val enragedBones: Set<String> = emptySet(),
    /** Model bones (with the bones under them) hidden once enraged, e.g. folded wings replaced by opened ones. */
    val foldedBones: Set<String> = emptySet(),
) {
    val name: String get() = archetype.displayName
    val boss: Boolean get() = archetype.rarity == QuestMobRarity.BOSS
    val soundFamily: String get() = when (archetype) {
        QuestMobArchetype.SHIELD_GUARD, QuestMobArchetype.IRON_WARDEN, QuestMobArchetype.GLACIAL_COLOSSUS -> "husk"
        QuestMobArchetype.RIFT_CASTER, QuestMobArchetype.RIFT_ORACLE, QuestMobArchetype.TEMPEST_HIEROPHANT, QuestMobArchetype.TIDE_ARCHIVIST -> "evoker"
        QuestMobArchetype.ELITE_BRUTE, QuestMobArchetype.CINDER_REGENT, QuestMobArchetype.FORGE_SENTINEL -> "piglin_brute"
        QuestMobArchetype.ECLIPSE_REGENT -> "wither_skeleton"
        QuestMobArchetype.SHADE_WYRM, QuestMobArchetype.SAKURA_FANG -> "ravager"
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
    /** 黒翳竜's three phases (2026-10-19, 藍): the second form (glowing) at 70 %, the angry form (fans, horns) at 35 %. */
    const val AWAKEN = .7
    const val RAGE = .35

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
            minimumRange: Double = 0.0, target: Boolean = false, health: Double = 1.0, weight: Int = 1, scripted: Boolean = false,
            earlyHits: List<Long> = emptyList()) = MobAbility(
            id, label, shape, range, damage * factor, warning, tracking, recovery, cooldown, weight,
            minimumRange, if (target) MobAbilityAnchor.TARGET else MobAbilityAnchor.CASTER, health,
            if (id in setOf("rift-bolt", "rift-pulse", "oracle-mark", "oracle-ray", "oracle-pulse",
                    "forge-cross", "forge-mark", "tide-ray", "tide-ring", "tide-center", "eclipse-mark", "eclipse-cross",
                    "cinder-mark", "furnace-cross", "frost-outside", "frost-inside", "tempest-ray", "tempest-mark", "tempest-cross",
                    "shade-dust", "shade-pulse", "shade-breath", "shade-storm", "shade-orb", "shade-channel", "shade-shell",
                    "sakura-scatter", "sakura-bloom", "sakura-howl", "sakura-rain"))
                dev.projects.server.coreloop.CoreDamageType.MAGICAL
            else if (id in setOf("shade-channel", "shade-shell")) dev.projects.server.coreloop.CoreDamageType.TRUE   // a failed gimmick
            else dev.projects.server.coreloop.CoreDamageType.PHYSICAL, scripted,
            earlyHits,
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
            // Blind wyrm on wing-arms (docs/development/shade-wyrm-boss.md). Animation lengths in
            // assets/bosses/shade_wyrm.bbmodel equal telegraph+recovery of each ability below.
            // HP 2800 (2026-10-19, 藍「1人で約5分」): a Tier-1 player deals ~8-15 damage a second while dodging.
            QuestMobArchetype.SHADE_WYRM -> definition(EntityType.RAVAGER, 2800.0, .13, 3.0, 1.0,
                Material.AIR, attacks = listOf(
                    attack("shade-sweep", "翼脚薙ぎ", MobAttackShape.Sweep(7.0, 90.0), 4.8, 22.0,
                        1100, 850, 900, 2200, weight = 3),
                    attack("shade-pounce", "飛びかかり", MobAttackShape.Slam(5.5, 3.0), 9.5, 27.0,
                        1400, 1150, 1200, 3400, minimumRange = 3.0, weight = 2),
                    attack("shade-dust", "鱗粉炸裂", MobAttackShape.Ring(8.5, 2.5), 7.5, 23.0,
                        1700, 500, 900, 2800, weight = 2),
                    attack("shade-pulse", "触角の共鳴", MobAttackShape.Ring(4.6), 4.5, 20.0,
                        1200, 0, 1100, 3600, weight = 3),
                    attack("shade-breath", "翳の吐息", MobAttackShape.Slam(18.0, 2.4), 17.0, 30.0,
                        1600, 1300, 1000, 3600, health = RAGE, weight = 3),
                    attack("shade-storm", "鱗粉嵐", MobAttackShape.Ring(15.0, 4.0), 15.0, 32.0,
                        2200, 0, 1300, 6000, health = RAGE, weight = 2),
                    // 2026-10-19 (藍が選んだ行動): three bites (the first two are early hits), from phase one.
                    attack("shade-bite", "噛みつき", MobAttackShape.Slam(5.5, 1.6), 5.0, 14.0,
                        1600, 1450, 800, 4200, weight = 3, earlyHits = listOf(700L, 1150L)),
                    // Phase two (第2形態) on: the tail spin, the horn upthrust and the thrown dust clot.
                    attack("shade-tailspin", "しっぽ回転", MobAttackShape.Ring(6.0), 5.5, 24.0,
                        1250, 0, 1150, 5200, health = AWAKEN, weight = 2),
                    attack("shade-horn", "角の突き上げ", MobAttackShape.Slam(4.5, 1.8), 4.2, 26.0,
                        1000, 800, 1000, 4000, health = AWAKEN, weight = 2),
                    attack("shade-orb", "鱗粉弾", MobAttackShape.Ring(2.8), 18.0, 20.0,
                        1800, 1550, 400, 5000, minimumRange = 5.0, target = true, health = AWAKEN, weight = 2),
                    // 突進 (2026-10-19, A: attacks move the body): bounds ~14 blocks across the arena along its band.
                    attack("shade-charge", "突進", MobAttackShape.Slam(14.0, 1.8), 16.0, 26.0,
                        1400, 700, 1600, 7000, minimumRange = 6.0, weight = 2),
                    // MMO gimmicks (2026-10-19), started on a timer by QuestEncounterCombat: the cast to interrupt (second form
                    // on: damage it enough while it hangs in the air, or the whole arena blasts) and the shell to break
                    // (enraged: break it in time, or it bursts round the wyrm).
                    attack("shade-channel", "翳の大詠唱・殴って止めろ", MobAttackShape.Ring(22.0), 40.0, 85.0,
                        6000, 0, 1200, 40_000, health = AWAKEN, scripted = true),
                    attack("shade-shell", "鱗粉の殻・殻を割れ", MobAttackShape.Ring(22.0), 40.0, 80.0,
                        9000, 0, 1200, 45_000, health = RAGE, scripted = true),
                    // Someone behind it: the quick tail slap first, the turning sweep if they stay (QuestEncounterCombat).
                    attack("shade-tailslap", "尾打ち", MobAttackShape.Sweep(6.0, 70.0), 7.0, 18.0,
                        700, 500, 800, 4000, scripted = true),
                    // Only when the player got behind it during an attack and stays there (QuestEncounterCombat).
                    attack("shade-turnsweep", "振り向き薙ぎ", MobAttackShape.Sweep(6.5, 90.0), 7.0, 22.0,
                        900, 650, 900, 5000, scripted = true),
                    attack("shade-dive", "急降下", MobAttackShape.Ring(4.5), 30.0, 28.0,
                        2400, 2150, 1400, 9000, minimumRange = 12.0, target = true, weight = 2),
                )).copy(model = "shade_wyrm", awakenHealthRatio = AWAKEN, awakenSpeedMultiplier = 1.1, enrageHealthRatio = RAGE, enragedSpeedMultiplier = 1.2,
                    enragedBones = setOf("horn_l", "horn_r", "cloak_l", "cloak_r", "horn_l_tip", "horn_r_tip", "cloak_l_tatters", "cloak_r_tatters"), foldedBones = setOf("wing_l", "wing_r"))
            // Fanged beast with sakura petal shells (scripts/build_boss_sakura_fang.py). Animation lengths in
            // assets/bosses/sakura_fang.bbmodel equal telegraph+recovery of each ability below. Cooldowns are long on
            // purpose: between attack chains it rests and moves round its prey (QuestEncounterCombat.SAKURA_REST).
            QuestMobArchetype.SAKURA_FANG -> definition(EntityType.RAVAGER, 560.0, .14, 3.0, 1.0,
                Material.AIR, attacks = listOf(
                    attack("sakura-claw", "桜爪の叩きつけ・横へ", MobAttackShape.Slam(6.0, 2.4), 5.0, 22.0,
                        1200, 450, 1000, 6500, weight = 3),
                    attack("sakura-tail", "花尾の振り下ろし・横へ", MobAttackShape.Slam(9.0, 1.8), 8.5, 27.0,
                        1500, 500, 1100, 8000, minimumRange = 2.5, weight = 2),
                    attack("sakura-scatter", "散華・扇の外へ", MobAttackShape.Sweep(12.0, 40.0), 11.0, 20.0,
                        1700, 600, 900, 8000, weight = 2),
                    attack("sakura-bloom", "花嵐・獣から離れろ", MobAttackShape.Ring(8.0), 6.0, 30.0,
                        2000, 0, 1200, 10000, health = .5, weight = 2),
                    attack("sakura-horn", "枝角の突き上げ・横へ", MobAttackShape.Slam(4.8, 1.5), 4.2, 18.0,
                        800, 350, 700, 6000, weight = 3),
                    attack("sakura-spin", "花尾の旋回・離れろ", MobAttackShape.Ring(5.6), 4.5, 20.0,
                        1000, 0, 900, 7500, weight = 2),
                    attack("sakura-howl", "散り際の遠吠え・離れろ", MobAttackShape.Ring(6.5), 5.5, 26.0,
                        1600, 0, 1000, 14000, health = .5, weight = 1),
                    attack("sakura-pounce", "花冠の飛びかかり・着地点から離れろ", MobAttackShape.Ring(3.6), 14.0, 26.0,
                        1100, 550, 900, 9000, minimumRange = 5.0, target = true, weight = 2),
                    attack("sakura-rain", "花冠落とし・印から出ろ", MobAttackShape.Ring(3.2), 16.0, 22.0,
                        1800, 1300, 800, 11000, minimumRange = 4.0, target = true, weight = 2),
                    attack("sakura-charge", "花吹雪の突進・横へ", MobAttackShape.Slam(13.0, 2.0), 12.0, 28.0,
                        1000, 450, 1200, 10000, minimumRange = 3.0, health = .5, weight = 2),
                    // Small, quick blows thrown on the move (QuestEncounterCombat.SAKURA_LIGHT): the only attacks it uses while
                    // it rests between its big ones; the server carries the body through each.
                    attack("sakura-lunge", "踏み込み爪・横へ", MobAttackShape.Slam(6.0, 1.7), 5.0, 12.0,
                        550, 250, 550, 3000, weight = 2),
                    attack("sakura-barge", "花肩の体当たり・横へ", MobAttackShape.Slam(6.0, 2.2), 5.5, 14.0,
                        650, 300, 650, 3600, minimumRange = 1.5, weight = 2),
                    attack("sakura-hopswipe", "跳ね尾払い・離れろ", MobAttackShape.Sweep(5.5, 75.0), 4.5, 12.0,
                        600, 250, 600, 3200, weight = 2),
                    // 三連桜爪: the paw slam chains into a second blow, and once enraged into a third (QuestEncounterCombat).
                    attack("sakura-claw2", "桜爪・二の手", MobAttackShape.Slam(6.0, 2.4), 5.0, 20.0,
                        600, 250, 700, 0, scripted = true),
                    attack("sakura-claw3", "桜爪・止め", MobAttackShape.Slam(7.0, 3.0), 6.0, 28.0,
                        900, 300, 1300, 0, health = .5, scripted = true),
                )).copy(model = "sakura_fang", enrageHealthRatio = .5, enragedSpeedMultiplier = 1.2)
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
            QuestMobArchetype.FORGE_SENTINEL -> definition(EntityType.PIGLIN_BRUTE, 650.0, 0.115, 3.0, 1.6,
                Material.NETHERITE_AXE, chest = Material.NETHERITE_CHESTPLATE, attacks = listOf(
                    sweep(5.0, 22.0), slam(10.0, 1.8, 27.0),
                    attack("forge-cross", "熔断・対角線へ", MobAttackShape.Cross(17.0, 1.7), 20.0, 30.0, 1900, 300, 1600, 3200, health = .7, weight = 3),
                    attack("forge-mark", "火種・足元から離れろ", MobAttackShape.Ring(3.4), 20.0, 24.0, 1600, 400, 1000, 2700, target = true, health = .4)))
            QuestMobArchetype.TIDE_ARCHIVIST -> definition(EntityType.EVOKER, 600.0, .12, 7.0, 1.45,
                Material.TRIDENT, Material.ENCHANTED_BOOK, attacks = listOf(
                    attack("tide-ray", "潮流・横へ回避", MobAttackShape.Slam(20.0, 1.25), 20.0, 23.0, 1500, 350, 1200, 2500),
                    attack("tide-ring", "満潮・中心へ", MobAttackShape.Ring(17.0, 5.5), 20.0, 28.0, 2100, 0, 1500, 3300, health = .7),
                    attack("tide-center", "干潮・外へ", MobAttackShape.Ring(5.5), 20.0, 28.0, 1900, 0, 1500, 3100, health = .4)))
            QuestMobArchetype.ECLIPSE_REGENT -> definition(EntityType.WITHER_SKELETON, 800.0, .12, 3.0, 1.55,
                Material.NETHERITE_SWORD, helmet = Material.GOLDEN_HELMET, chest = Material.NETHERITE_CHESTPLATE, attacks = listOf(
                    sweep(5.0, 25.0), slam(12.0, 1.5, 30.0),
                    attack("eclipse-mark", "星落とし・足元から離れろ", MobAttackShape.Ring(3.3), 21.0, 26.0, 1750, 500, 1000, 2500, target = true),
                    attack("eclipse-cross", "星蝕十字・斜めへ", MobAttackShape.Cross(18.0, 1.6), 21.0, 34.0, 2000, 200, 1400, 3200, health = .4)))
            QuestMobArchetype.CINDER_REGENT -> definition(EntityType.PIGLIN_BRUTE, 460.0, 0.12, 3.0, 1.4,
                Material.NETHERITE_AXE, chest = Material.NETHERITE_CHESTPLATE, attacks = listOf(
                    attack("cinder-mark", "焼印・赤い床から離れろ", MobAttackShape.Ring(3.3), 18.0, 24.0,
                        1700, 450, 1000, 2700, target = true),
                    sweep(5.0, 22.0),
                    attack("furnace-cross", "熔炉十字・斜めへ回避", MobAttackShape.Cross(13.0, 1.6), 18.0, 32.0,
                        2100, 300, 1500, 3200, health = 0.5, weight = 4),
                ))
            QuestMobArchetype.GLACIAL_COLOSSUS -> definition(EntityType.HUSK, 510.0, 0.105, 4.0, 1.4,
                Material.DIAMOND_AXE, helmet = Material.DIAMOND_HELMET, chest = Material.DIAMOND_CHESTPLATE, attacks = listOf(
                    attack("frost-outside", "外周凍結・巨兵の近くへ", MobAttackShape.Ring(16.0, 5.0), 18.0, 26.0,
                        2200, 0, 1200, 1800),
                    attack("frost-inside", "中心砕氷・巨兵から離れろ", MobAttackShape.Ring(5.0), 18.0, 24.0,
                        1800, 0, 1200, 1800),
                ))
            QuestMobArchetype.TEMPEST_HIEROPHANT -> definition(EntityType.EVOKER, 420.0, 0.12, 7.0, 1.3,
                Material.LIGHTNING_ROD, Material.ENCHANTED_BOOK, attacks = listOf(
                    attack("tempest-ray", "天雷の道", MobAttackShape.Slam(20.0, 1.2), 18.0, 23.0,
                        1600, 400, 1200, 2600),
                    attack("tempest-mark", "落雷の刻印", MobAttackShape.Ring(3.0), 18.0, 23.0,
                        1500, 300, 1000, 2300, target = true),
                    attack("tempest-cross", "嵐の十字", MobAttackShape.Cross(11.0, 1.25), 18.0, 29.0,
                        2000, 200, 1600, 3600, health = 0.33, weight = 3),
                ))
        }
    }
}
