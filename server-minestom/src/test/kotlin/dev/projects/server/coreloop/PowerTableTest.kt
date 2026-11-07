package dev.projects.server.coreloop

import dev.projects.server.coreloop.adventure.InstanceDungeonFactory
import dev.projects.server.coreloop.adventure.SpawnerRule
import dev.projects.server.mob.MobAbility
import dev.projects.server.mob.MobAbilityEvent
import dev.projects.server.mob.MobAbilityManager
import dev.projects.server.mob.MobGrade
import dev.projects.server.mob.MobGrades
import dev.projects.server.mob.QuestEncounterCombat
import dev.projects.server.mob.QuestMobArchetype
import dev.projects.server.mob.QuestMobContent
import dev.projects.server.mob.QuestMobDefinition
import dev.projects.server.mob.QuestMobRarity
import net.minestom.server.coordinate.Pos
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 強さの表 (docs/development/power-table.md). Measures, changes nothing: a solo player (warrior or mage) walks 散り桜の社
 * room by room, and each fight is played out tick by tick with the game's own numbers — the player's sheet
 * (CoreCombatSheet), the skills (CoreSkillCatalog.equipped), the greatsword cadence (GreatswordCombo), damage and defence
 * (CoreCombatMath, QuestEncounterCombat.enemyDefense), the enemies (QuestMobContent) and how they pick attacks
 * (MobAbilityManager). What the code leaves to a player's hands (where they stand, what they dodge) is fixed by the
 * assumptions written at the head of the table.
 *
 * Writes build/reports/power-table/power-table.md. Run: ./gradlew :server-minestom:test --tests '*PowerTableTest*'
 */
class PowerTableTest {
    internal data class Gear(val label: String, val tier: Int, val itemLevel: Int)

    /** A solo player with a default build: the first four catalog skills, unlocked by level, no passives, no MODs. */
    internal class Hero(val job: CoreClass, val level: Int, val gear: Gear) {
        val base = if (job == CoreClass.MAGE) CoreWeaponBase.STAFF else CoreWeaponBase.STANDARD
        val journey = CoreJourney(job, chosen = true, xp = CoreJourneyRules.threshold(level))
        val sheet: CoreCombatSheet = run {
            val weapon = CoreGearIdentity(UUID(0, 1), UUID(0, 9), base = base, itemLevel = gear.itemLevel)
            val armor = CoreGearIdentity(UUID(0, 2), UUID(0, 9), itemLevel = gear.itemLevel)
            CoreCombatSheet.from(CoreCombatGear(gear.tier, gear.tier, base,
                weaponLevelPower = CoreJourneyRules.power(weapon, gear.tier),
                armorLevelPower = CoreJourneyRules.power(armor, gear.tier)), CoreAffixStats()).specialize(journey)
        }
        val stats get() = sheet.mods
        /** Unlocked by level (CoreSkillCatalog.unlockLevels); the ultimate (Lv16) is a buff for both classes and is left out. */
        val skills: List<CoreSkillDefinition> = CoreSkillCatalog.equipped(journey).take(4)
            .filterIndexed { i, _ -> level >= CoreSkillCatalog.unlockLevels[i] }
        /** Base crit 5% x1.5 as an expected value. */
        val crit = 1 + stats.criticalChance * (stats.criticalMultiplier - 1)
    }

    /** The dungeon a player of [level] enters (CoreLoopServer.departDungeon / StartDungeon), assuming the tier is unlocked. */
    private fun dungeonTier(level: Int) = ((level - 1) / 10 + 1).coerceIn(1, 4)
    private fun dungeonLevel(level: Int) = dungeonTier(level).let { level.coerceIn(CoreJourneyRules.floor(it), CoreJourneyRules.ceiling(it)) }

    /** QuestEncounterCombat.spawn's scaling, written out (spawn is private): stage, grade, level rank. */
    private fun grade(a: QuestMobArchetype) = when (a.rarity) {
        QuestMobRarity.MINIBOSS -> MobGrades.MINIBOSS
        QuestMobRarity.ELITE -> MobGrade.ELITE    // the lowest an elite rolls (60%)
        else -> MobGrade.COMMON                   // normals roll 一般 70%; bosses never roll
    }
    private fun scaled(a: QuestMobArchetype, tier: Int, level: Int, stage: Int): QuestMobDefinition {
        val base = QuestMobContent.definition(tier, a)
        val rank = level - CoreJourneyRules.floor(tier)
        val b = CoreMmoTuning.balance
        val g = grade(a)
        return base.copy(maximumHealth = base.maximumHealth * CoreDungeons.health(stage) * g.health * (1 + rank * b.mobRankHealthPermille / 1000.0),
            abilities = base.abilities.map { it.copy(damage = it.damage * CoreDungeons.damage(stage) * g.damage * (1 + rank * b.mobRankDamagePermille / 1000.0)) })
    }

    private class Foe(val archetype: QuestMobArchetype, val def: QuestMobDefinition, seed: Long) {
        var hp = def.maximumHealth
        val abilities = MobAbilityManager(def.abilities, Random(seed))
        var combo: String? = null
        var restUntil = 0L
        var holdUntil = 0L          // not attacking (enrage roar, sigil, 千本桜, broken after them)
        var brokenUntil = 0L        // takes x1.3 (QuestEncounterCombat.BROKEN_DAMAGE)
        var enraged = false
        var finaleStarted = false
        var finaleEndsAt = 0L
        var sigilAt = Long.MAX_VALUE
        var attempt = 0
        val sakura get() = archetype == QuestMobArchetype.SAKURA_FANG
        val alive get() = hp > 1e-9
    }

    internal data class Result(val seconds: Double, val taken: Double, val hits: Int, val capped: Boolean,
        val finaleDamage: Double = 0.0, val sigilDamage: Double = 0.0, val sigils: Int = 0)

    /** Where the enemy may start an attack from: each try uses the next of these distances (the player moves about). */
    private val distances = listOf(3.0, 6.0, 10.0)
    private val origin = Pos(0.0, 64.0, 0.0)

    /**
     * One room, played out at 20 ticks a second. [waves] come one after another (all of a wave at once);
     * [spawner] = 穢れ場 (rise one every interval while fewer than the cap, until the target has fallen, then the last).
     */
    private fun fight(hero: Hero, level: Int, stage: Int, waves: List<List<QuestMobArchetype>>, spawner: SpawnerRule? = null,
        seed: Long = 7L, limitSeconds: Int = 900): Result {
        val tier = dungeonTier(level); val mobLevel = dungeonLevel(level)
        val defense = QuestEncounterCombat.enemyDefense(tier, mobLevel)
        val sheet = hero.sheet; val stats = hero.stats
        var foeSeed = seed
        fun make(a: QuestMobArchetype) = Foe(a, scaled(a, tier, mobLevel, stage), foeSeed++)
        val alive = mutableListOf<Foe>()
        var wave = 0
        var risen = 0; var fallen = 0; var lastOut = false; var nextRise = 0L
        val target = spawner?.let { it.target(1) + 3 * (stage - 1) } ?: 0
        val cap = spawner?.cap(1) ?: 0
        if (spawner == null) waves[0].forEach { alive += make(it) }

        // player state
        var mana = sheet.mana
        val readyAt = LongArray(hero.skills.size)
        var busyUntil = 0L
        val pendingHits = mutableListOf<Triple<Long, CoreSkillDefinition, Double>>()   // tick, skill, multiplier
        val combo = GreatswordCombo()
        val greatsword = CoreWeaponCombat.of(hero.base)?.attacks
        var nextShot = 0L; var shotAt = -1L
        var beast = 0.0; var impactSwings = 0
        var burn: Pair<Double, Int>? = null; var burnNext = 0L
        var taken = 0.0; var hits = 0; var finaleDamage = 0.0; var sigilDamage = 0.0; var sigils = 0

        fun weak(foe: Foe, element: Int) = when (foe.archetype.weakness) {
            "fire" -> element == 1; "ice" -> element == 2; "lightning" -> element == 3; else -> false
        }
        var t = 0L
        fun strike(foe: Foe, raw: Double, type: CoreDamageType) {
            val armored = foe.archetype in setOf(QuestMobArchetype.SHIELD_GUARD, QuestMobArchetype.IRON_WARDEN)
            val caster = foe.archetype in setOf(QuestMobArchetype.RIFT_CASTER, QuestMobArchetype.RIFT_ORACLE)
            var dealt = CoreCombatMath.mitigate(raw, type, defense * (if (armored) 1.5 else 1.0), defense * (if (caster) 1.5 else 1.0), stats)
            // 盾持ちの正面の守り (x0.45) is not applied: the hits are assumed to come from the side.
            dealt *= if (t * 50 < foe.brokenUntil) QuestEncounterCombat.BROKEN_DAMAGE else 1.0
            if (foe.sakura && !foe.finaleStarted)
                dealt = minOf(dealt, (foe.hp - foe.def.maximumHealth * QuestEncounterCombat.FINALE_RATIO).coerceAtLeast(0.0))
            foe.hp -= dealt
        }
        fun current(): Foe? = alive.firstOrNull { it.alive }

        val limit = limitSeconds * 20L
        while (t < limit) {
            val now = t * 50
            // --- room flow
            val died = alive.count { !it.alive }
            if (spawner != null && !lastOut) fallen += died
            alive.removeAll { !it.alive }
            if (spawner == null) {
                if (alive.isEmpty()) { if (++wave >= waves.size) break; waves[wave].forEach { alive += make(it) } }
            } else {
                if (lastOut && alive.isEmpty()) break
                if (!lastOut && fallen >= target && alive.isEmpty()) { lastOut = true; alive += make(spawner.last) }
                else if (!lastOut && risen < target && alive.size < cap && t >= nextRise) {
                    alive += make(spawner.mobs[risen % spawner.mobs.size]); risen++; nextRise = t + spawner.intervalTicks
                }
            }
            val foe = current()
            // --- the player
            if (foe != null) {
                if (hero.job == CoreClass.WARRIOR) {
                    combo.tick()?.let { swing ->
                        val raw = CoreCombatMath.outgoing(CoreSkillCatalog.basicFormula.evaluate(sheet) * swing.multiplier,
                            CoreDamageType.PHYSICAL, CoreSkillCatalog.basicTags(hero.job), stats) * hero.crit
                        strike(foe, raw, CoreDamageType.PHYSICAL)
                        beast = (beast + CoreWarriorRules.BEAST_PER_NORMAL * if (swing.finisher) 2 else 1).coerceAtMost(CoreWarriorRules.BEAST_MAX)
                        if (impactSwings > 0) { impactSwings--; strike(foe, raw * CoreWarriorRules.IMPACT_SHARE, CoreDamageType.PHYSICAL) }
                    }
                }
                if (hero.job == CoreClass.MAGE && shotAt == t) {
                    val raw = CoreCombatMath.outgoing(CoreSkillCatalog.basicFormula.evaluate(sheet), CoreDamageType.MAGICAL,
                        CoreSkillCatalog.basicTags(hero.job), stats) * hero.crit
                    strike(foe, raw, CoreDamageType.MAGICAL)
                }
                for (hit in pendingHits.filter { it.first == t }) {
                    val (_, s, multiplier) = hit
                    val target2 = current() ?: break
                    val raw = CoreCombatMath.outgoing(s.baseDamage(sheet) * multiplier, s.type, s.tags, stats) * hero.crit *
                        (if (weak(target2, s.element)) CorePlayerCombat.WEAK else 1.0)
                    strike(target2, raw, s.type)
                    if (hero.job == CoreClass.MAGE && s.element == 1) {
                        burn = CoreCombatMath.outgoing(3.0 + sheet.ap * .25, CoreDamageType.MAGICAL, setOf(CoreAttackTag.SKILL, CoreAttackTag.AREA), stats) to CoreMageField.BURN_TICKS / 20
                        burnNext = t + 20
                    }
                }
                pendingHits.removeAll { it.first <= t }
                burn?.let { (dmg, left) ->
                    if (t >= burnNext) { current()?.let { strike(it, dmg, CoreDamageType.MAGICAL) }
                        burn = if (left > 1) dmg to left - 1 else null; burnNext = t + 20 }
                }
                // next action
                val free = t >= busyUntil && !(hero.job == CoreClass.WARRIOR && combo.isAttacking) && shotAt < t
                if (free && current() != null) {
                    val i = hero.skills.indices.firstOrNull { t >= readyAt[it] && mana >= hero.skills[it].mana }
                    if (i != null) {
                        var s = hero.skills[i]
                        var boost = 1.0
                        if (hero.job == CoreClass.WARRIOR && s.icon in CoreWarriorRules.spenders) {
                            val stacks = beast.toInt(); beast -= stacks
                            boost *= 1 + stacks * CoreWarriorRules.BEAST_DAMAGE_PERCENT_PER_STACK / 100.0
                            s = CoreWarriorRules.shape(s, stacks)
                            if (s.icon == "slam" && stacks >= CoreWarriorRules.BEAST_MAX) boost *= CoreWarriorRules.FULL_SWING_MULTIPLIER
                        }
                        mana -= s.mana
                        readyAt[i] = t + s.cooldownTicks(stats)
                        val startup = s.startupTicks(sheet)
                        val pulse = CoreCombatFeel.pulseTicks(s)
                        for (p in 0 until s.pulses) {
                            val last = hero.job == CoreClass.WARRIOR && s.icon == CoreWarriorRules.FRENZY && p == s.pulses - 1
                            pendingHits += Triple(t + startup + p * pulse, s, boost * if (last) 2.0 else 1.0)
                        }
                        busyUntil = t + CoreCombatFeel.finish(hero.job, s, startup) + 1
                        if (hero.job == CoreClass.WARRIOR && s.icon !in CoreWarriorRules.spenders)
                            beast = (beast + CoreWarriorRules.BEAST_PER_SKILL).coerceAtMost(CoreWarriorRules.BEAST_MAX)
                        if (hero.job == CoreClass.WARRIOR && s.icon == CoreWarriorRules.IMPACT) impactSwings = CoreWarriorRules.IMPACT_SWINGS
                    } else if (hero.job == CoreClass.WARRIOR) {
                        combo.press(sheet.attackSpeed, attacks = greatsword!!, upswing = CorePlayerCombat.GREATSWORD_UPSWING)
                    } else if (t >= nextShot) {
                        shotAt = t + 4
                        nextShot = t + ceil(17.0 / sheet.attackSpeed).toLong().coerceAtLeast(8)
                        busyUntil = shotAt
                    }
                }
            }
            mana = (mana + CoreCombatMath.manaRegeneration(stats, false) / 20.0).coerceAtMost(sheet.mana)

            // --- the enemies
            for (f in alive.filter { it.alive }) {
                val ratio = f.hp / f.def.maximumHealth
                if (f.sakura) {
                    if (!f.enraged && ratio <= f.def.enrageHealthRatio) {
                        f.enraged = true; f.abilities.cancel(); f.combo = null
                        f.holdUntil = now + ENRAGE_HOLD_MILLIS; f.sigilAt = f.holdUntil + QuestEncounterCombat.SIGIL_FIRST_MILLIS
                    }
                    if (f.enraged && now >= f.sigilAt && !f.finaleStarted && now >= f.holdUntil) {
                        // 桜紋の陣: solo, one crest; standing on it (assumed) breaks the boss
                        sigils++
                        sigilDamage += CoreCombatMath.mitigate(QuestEncounterCombat.SIGIL_DAMAGE * Math.pow(1.65, tier - 1.0) *
                            CoreDungeons.damage(stage) * 2, CoreDamageType.MAGICAL, sheet.ar, sheet.mr)
                        f.abilities.cancel(); f.combo = null
                        f.holdUntil = now + QuestEncounterCombat.SIGIL_MILLIS + QuestEncounterCombat.SIGIL_BREAK_MILLIS
                        f.brokenUntil = f.holdUntil; f.sigilAt = now + QuestEncounterCombat.SIGIL_EVERY_MILLIS
                    }
                    if (!f.finaleStarted && ratio <= QuestEncounterCombat.FINALE_RATIO + 1e-9 && now >= f.holdUntil) {
                        f.finaleStarted = true; f.abilities.cancel(); f.combo = null
                        f.finaleEndsAt = now + QuestEncounterCombat.FINALE_MILLIS
                        f.holdUntil = f.finaleEndsAt + QuestEncounterCombat.FINALE_SPENT_MILLIS
                        f.brokenUntil = f.holdUntil
                        finaleDamage += CoreCombatMath.mitigate(QuestEncounterCombat.FINALE_DAMAGE * Math.pow(1.65, tier - 1.0) *
                            CoreDungeons.damage(stage), CoreDamageType.MAGICAL, sheet.ar, sheet.mr)
                    }
                }
                if (now < f.holdUntil) continue
                val d = distances[f.attempt % distances.size]
                val at = origin.add(d, 0.0, 0.0)
                for (event in f.abilities.tick(now, at)) {
                    if (event is MobAbilityEvent.Hit) {
                        taken += CoreCombatMath.mitigate(event.frame.ability.damage, event.frame.ability.damageType, sheet.ar, sheet.mr,
                            damageReduction = stats.mitigationPercent)
                        hits++
                    }
                    if (event is MobAbilityEvent.Finished && f.sakura) {
                        f.combo = when (event.abilityId) {
                            "sakura-claw" -> "sakura-claw2"
                            "sakura-claw2" -> if (f.enraged) "sakura-claw3" else null
                            "sakura-tail" -> if (f.enraged) "sakura-spin" else null
                            "sakura-horn" -> if (f.enraged) "sakura-claw" else null
                            "sakura-pounce" -> if (f.enraged) "sakura-horn" else null
                            else -> null
                        }
                        if (f.combo == null && event.abilityId !in QuestEncounterCombat.SAKURA_LIGHT)
                            f.restUntil = now + (if (f.enraged) QuestEncounterCombat.SAKURA_REST_ENRAGED else QuestEncounterCombat.SAKURA_REST).let { (it.first + it.last) / 2 }
                    }
                }
                if (f.abilities.current == null) {
                    f.attempt++
                    val c = f.combo
                    if (c != null) { f.abilities.tryStart(now, origin, at, ratio, only = c); f.combo = null }
                    else f.abilities.tryStart(now, origin, at, ratio,
                        allowed = if (f.sakura && now < f.restUntil) QuestEncounterCombat.SAKURA_LIGHT else null)
                }
            }
            t++
        }
        return Result(t / 20.0, taken, hits, t >= limit, finaleDamage, sigilDamage, sigils)
    }

    /** The enemies pick their attacks at random (MobAbilityManager); each cell is the mean of [SEEDS] runs. */
    private fun mean(hero: Hero, level: Int, stage: Int, room: Room): Result {
        val runs = SEEDS.map { fight(hero, level, stage, room.waves, room.spawner, seed = it) }
        return Result(runs.map { it.seconds }.average(), runs.map { it.taken }.average(), runs.map { it.hits }.sum() / runs.size,
            runs.any { it.capped }, runs.map { it.finaleDamage }.average(), runs.map { it.sigilDamage }.average(), runs.map { it.sigils }.sum() / runs.size)
    }

    // --- 散り桜の社 (InstanceDungeonFactory, rooms.json): solo, 段階 stage
    private val S = QuestMobArchetype.SOLDIER; private val G = QuestMobArchetype.SHIELD_GUARD
    private val C = QuestMobArchetype.RIFT_CASTER; private val E = QuestMobArchetype.ELITE_BRUTE
    internal data class Room(val name: String, val waves: List<List<QuestMobArchetype>>, val spawner: SpawnerRule? = null)
    private fun rooms(stage: Int): List<Room> {
        fun more(w: List<QuestMobArchetype>) = if (stage >= 2 && w.size < 4) w + G else w           // DungeonRun.waveAt
        fun escorts(a: QuestMobArchetype) = listOf(listOf(a) + List((stage - 1).coerceAtMost(2)) { C })
        val spawner = SpawnerRule(InstanceDungeonFactory.perPlayers("8 + 4 * 人数"), InstanceDungeonFactory.perPlayers("3 + 人数"),
            (3.5 * 20).toInt(), listOf(S, C, S, G), E)
        return listOf(
            Room("入口の野の群れ", listOf((0 until 4).map { if (it % 3 == 2) C else S })),
            Room("花守の回廊", listOf(listOf(S, S, C), listOf(G, S, C)).map(::more)),
            Room("ミニボス1 白蛇", escorts(QuestMobArchetype.SAKURA_MINIBOSS_1)),
            Room("散華の間", listOf(listOf(G, C, S, S), listOf(E, C, G)).map(::more)),
            Room("穢れ場（1部屋）", emptyList(), spawner),
            Room("ミニボス2 灯籠", escorts(QuestMobArchetype.SAKURA_MINIBOSS_2)),
            Room("桜牙獣", listOf(listOf(QuestMobArchetype.SAKURA_FANG))),
        )
    }

    private val gears = listOf(Gear("T1 iLv1", 1, 1), Gear("T1 iLv5", 1, 5), Gear("T1 iLv10", 1, 10), Gear("T2 iLv11", 2, 11))
    private val levels = listOf(1, 2, 4, 6, 8, 10, 11)

    private fun n(v: Double) = String.format(Locale.ROOT, "%.0f", v)
    private fun time(r: Result) = if (r.capped) "15分超" else if (r.seconds >= 60) String.format(Locale.ROOT, "%d分%02d秒", (r.seconds / 60).toInt(), (r.seconds % 60).toInt()) else "${n(r.seconds)}秒"

    @Test fun `power table`() {
        val out = StringBuilder()
        val flags = mutableListOf<String>()
        // Reference: the enemies themselves at T1 Lv1 段階1 and at T1 Lv10.
        out.appendLine("## 敵の数字（本物のコードから）\n")
        out.appendLine("| 敵 | 等級（表の前提） | 弱点 | HP Lv1 | HP Lv10 | AR/MR Lv1→Lv10 | 技（Lv1 のダメージ・種別・予兆→硬直 ms・再使用 ms） |")
        out.appendLine("|---|---|---|---|---|---|---|")
        for (a in listOf(S, G, C, E, QuestMobArchetype.SAKURA_MINIBOSS_1, QuestMobArchetype.SAKURA_MINIBOSS_2, QuestMobArchetype.SAKURA_FANG)) {
            val d1 = scaled(a, 1, 1, 1); val d10 = scaled(a, 1, 10, 1)
            val armored = a == G; val caster = a == C
            val ar1 = QuestEncounterCombat.enemyDefense(1, 1); val ar10 = QuestEncounterCombat.enemyDefense(1, 10)
            fun def(v: Double) = "${n(v * if (armored) 1.5 else 1.0)}/${n(v * if (caster) 1.5 else 1.0)}"
            val moves = d1.abilities.joinToString("<br>") { m: MobAbility ->
                "${m.displayName.substringBefore('・')} ${n(m.damage)} ${if (m.damageType == CoreDamageType.MAGICAL) "魔" else "物"} ${m.telegraphMillis}→${m.recoveryMillis} /${m.cooldownMillis}" +
                    (if (m.maximumHealthRatio < 1) "（HP${n(m.maximumHealthRatio * 100)}%以下）" else "") + (if (m.scripted) "（連携・条件）" else "")
            }
            out.appendLine("| ${a.displayName} | ${grade(a).label}（HP×${grade(a).health}・攻×${grade(a).damage}） | ${a.weakness} | ${n(d1.maximumHealth)} | ${n(d10.maximumHealth)} | ${def(ar1)} → ${def(ar10)} | $moves |")
        }
        out.appendLine()

        // Reference: the player.
        out.appendLine("## プレイヤーの数字（本物のコードから）\n")
        out.appendLine("| 職 | 装備 | 最大HP | AD | AP | AR | MR | 攻撃速度 | 通常攻撃の1周 |")
        out.appendLine("|---|---|---|---|---|---|---|---|---|")
        for (job in listOf(CoreClass.WARRIOR, CoreClass.MAGE)) for (g in gears) {
            val h = Hero(job, 10, g)
            val cycle = if (job == CoreClass.WARRIOR) {
                val c = GreatswordCombo(); val atk = CoreWeaponCombat.of(h.base)!!.attacks
                val ticks = (0 until 3).sumOf { c.press(h.sheet.attackSpeed, attacks = atk, upswing = CorePlayerCombat.GREATSWORD_UPSWING)!!.totalTicks.also { repeat(it) { c.tick() } } }
                "3振り ${atk.joinToString("+") { CoreCombatMath.number(it.multiplier) }}×AD / 約${CoreCombatMath.number(ticks / 20.0)}秒"
            } else "1発 1×AD / ${CoreCombatMath.number(ceil(17.0 / h.sheet.attackSpeed).coerceAtLeast(8.0) / 20)}秒"
            out.appendLine("| ${job.displayName} | ${g.label} | ${n(h.sheet.health)} | ${n(h.sheet.ad)} | ${n(h.sheet.ap)} | ${n(h.sheet.ar)} | ${n(h.sheet.mr)} | ${CoreCombatMath.number(h.sheet.attackSpeed)} | $cycle |")
        }
        out.appendLine()
        out.appendLine("スキル（既定の4枠、解放 Lv ${CoreSkillCatalog.unlockLevels.take(4).joinToString("/")}）:\n")
        for (job in listOf(CoreClass.WARRIOR, CoreClass.MAGE)) {
            val h = Hero(job, 16, gears[0])
            out.appendLine("- ${job.displayName}: " + h.skills.joinToString("、") { s ->
                "${s.name}（${s.formula.label()}${if (s.pulses > 1) "×${s.pulses}" else ""}・マナ${s.mana}・再使用${CoreCombatMath.number(s.cooldownTicks(h.stats) / 20.0)}秒・発生${CoreCombatMath.number(s.startupTicks(h.sheet) / 20.0)}秒）"
            })
        }
        out.appendLine()

        // The main tables.
        val room = rooms(1)
        val bossName = "桜牙獣"
        for (job in listOf(CoreClass.WARRIOR, CoreClass.MAGE)) {
            out.appendLine("## ${job.displayName}（段階1・ひとり）\n")
            out.appendLine("各マス：**倒すまでの時間** / 受けるダメージ（最大HPの何%）。受けるダメージは「全部当たる」の値。半分避けるなら半分。\n")
            out.appendLine("| Lv | 装備 | 最大HP | " + room.joinToString(" | ") { it.name } + " | 合計（穢れ場は3部屋） |")
            out.appendLine("|---|---|---|" + room.joinToString("") { "---|" } + "---|")
            for (level in levels) for (g in gears) {
                val h = Hero(job, level, g)
                val results = room.map { mean(h, level, 1, it) }
                val totalTime = results.withIndex().sumOf { (i, r) -> r.seconds * if (room[i].spawner != null) 3 else 1 }
                val totalTaken = results.withIndex().sumOf { (i, r) -> r.taken * if (room[i].spawner != null) 3 else 1 }
                val cells = results.map { r -> "**${time(r)}** / ${n(r.taken)}（${n(r.taken / h.sheet.health * 100)}%）" }
                out.appendLine("| $level | ${g.label} | ${n(h.sheet.health)} | " + cells.joinToString(" | ") +
                    " | **${time(Result(totalTime, 0.0, 0, false))}** / ${n(totalTaken)}（${n(totalTaken / h.sheet.health * 100)}%） |")
                results.forEachIndexed { i, r ->
                    if (r.seconds > 60) flags += "${job.displayName} Lv$level ${g.label} ${room[i].name}: 倒すまで ${time(r)}"
                    if (r.taken / 2 > h.sheet.health) flags += "${job.displayName} Lv$level ${g.label} ${room[i].name}: 半分避けても ${n(r.taken / 2)}（最大HP ${n(h.sheet.health)} の ${n(r.taken / 2 / h.sheet.health * 100)}%）"
                }
                assertTrue(results.all { it.seconds > 0 && it.taken.isFinite() })
            }
            out.appendLine()
            val h = Hero(job, 10, gears[2]); val r = mean(h, 10, 1, room.last())
            val sigilFail = CoreCombatMath.mitigate(QuestEncounterCombat.SIGIL_DAMAGE * CoreDungeons.damage(1) * 2, CoreDamageType.MAGICAL, h.sheet.ar, h.sheet.mr)
            out.appendLine("${bossName}のギミック（T1・段階1・装備 T1 iLv10、上の表には入れていない）：千本桜を隠れずに受けると ${n(r.finaleDamage)}（最大HPの${n(r.finaleDamage / h.sheet.health * 100)}%）。" +
                "桜紋の陣に立たないと1回 ${n(sigilFail)}（${n(sigilFail / h.sheet.health * 100)}%）。陣は怒りの15秒後に初めて来るので、この表の速さ（Lv10 で平均${time(r)}）では${if (r.sigils == 0) "一度も来ない" else "平均${r.sigils}回来る"}。" +
                "どちらも Lv（rank）では大きくならない（Tier と段階だけ）。\n")
        }

        // 段階 (stage) at Lv10, T1 iLv10.
        out.appendLine("## 段階ごと（Lv10・T1 iLv10・ひとり）\n")
        out.appendLine("段階2から：波の部屋は盾持ちが1体増える（4体まで）、ミニボスに術師が付く（段階2で1体、3以上で2体）、穢れ場は3体ずつ増える。敵のHP ×(1+0.3×(段階-1))、攻撃 ×(1+0.2×(段階-1))。\n")
        for (job in listOf(CoreClass.WARRIOR, CoreClass.MAGE)) {
            out.appendLine("### ${job.displayName}\n")
            out.appendLine("| 段階 | " + room.joinToString(" | ") { it.name } + " |")
            out.appendLine("|---|" + room.joinToString("") { "---|" })
            for (stage in 1..CoreDungeons.MAX_STAGE) {
                val h = Hero(job, 10, gears[2])
                val cells = rooms(stage).map { mean(h, 10, stage, it) }.map { r -> "**${time(r)}** / ${n(r.taken / h.sheet.health * 100)}%" }
                out.appendLine("| $stage | " + cells.joinToString(" | ") + " |")
            }
            out.appendLine()
        }
        out.appendLine("## 自動で拾った所（段階1）：倒すのに1分を超える／半分避けても最大HPを超える\n")
        if (flags.isEmpty()) out.appendLine("- なし")
        flags.forEach { out.appendLine("- $it") }

        val path = Path.of("build/reports/power-table/power-table.md")
        Files.createDirectories(path.parent)
        Files.writeString(path, out.toString())
        println("power table: ${path.toAbsolutePath()}")
    }

    companion object {
        /** QuestEncounterCombat's ENRAGE_HOLD_MILLIS (file-private there): the enrage roar, 5 s. */
        const val ENRAGE_HOLD_MILLIS = 5000L
        val SEEDS = listOf(11L, 23L, 37L, 41L, 53L)
    }
}
