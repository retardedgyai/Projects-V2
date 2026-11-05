package dev.projects.server.coreloop

import dev.projects.server.mob.CrowdControl

/**
 * Shared timing contract for every class: input buffer, normal->skill link, recovery, cancels and cone width.
 * Per-class values live in [profile].
 */
internal object CoreCombatFeel {
    const val NORMAL = -1
    data class Profile(
        /** Ticks a pressed input (normal or skill) is remembered while the previous action finishes. */
        val bufferTicks: Long,
        /** Melee: ticks after a normal swing's impact before a skill may cut its follow-through. Ranged: unused (the 4 tick draw). */
        val linkTicks: Int,
        /** The normal hit lands on the accepted click and its former windup is paid afterwards (greatsword only). */
        val immediateNormal: Boolean,
        /** Default ticks after a skill's last impact before the next action. */
        val recovery: Int,
        /** Attacking or casting an offensive skill drops a held guard. */
        val attackBreaksGuard: Boolean,
    )
    fun profile(job: CoreClass) = when (job) {
        CoreClass.WARRIOR -> Profile(6, 6, immediateNormal = true, recovery = 7, attackBreaksGuard = true)
        CoreClass.ASSASSIN -> Profile(6, 2, immediateNormal = false, recovery = 3, attackBreaksGuard = true)
        CoreClass.TEMPLAR -> Profile(6, 5, immediateNormal = false, recovery = 6, attackBreaksGuard = false)
        CoreClass.RANGER -> Profile(6, 0, immediateNormal = false, recovery = 4, attackBreaksGuard = true)
        CoreClass.MAGE, CoreClass.HEALER -> Profile(6, 0, immediateNormal = false, recovery = 5, attackBreaksGuard = true)
    }
    fun recovery(job: CoreClass, s: CoreSkillDefinition) = when {
        s.icon == "dash" || s.icon == "war_wound" || s.icon == "war_cry" -> 3
        s.icon in setOf("war_breach", "war_banner", "war_awaken") -> 4
        s.icon == CoreWarriorRules.FRENZY -> 6
        s.icon == CoreWarriorRules.SHATTER -> 7
        s.motion == CoreSkillMotion.GUARD -> 0
        s.motion == CoreSkillMotion.EVADE -> 2
        else -> profile(job).recovery
    }
    /** Ticks between a skill's pulses: 狂乱 swings faster than the usual 8. */
    fun pulseTicks(s: CoreSkillDefinition) = if (s.icon == CoreWarriorRules.FRENZY) 5 else 8
    fun lastImpact(s: CoreSkillDefinition, startup: Int) = startup + (s.pulses - 1) * pulseTicks(s)
    fun finish(job: CoreClass, s: CoreSkillDefinition, startup: Int) = lastImpact(s, startup) + recovery(job, s)
    /** A guard may cut a skill after its last impact and any normal follow-through. */
    fun cancelsIntoGuard(s: CoreSkillDefinition) = s.motion == CoreSkillMotion.GUARD
    fun minDot(icon: String) = when (icon) {
        "slam", "war_breach", "war_ult" -> .8 // Commit the heavy blade along the aimed lane.
        else -> .35
    }
}

/**
 * Warrior rules that are not timing: the beast (獣, 2026-10-22, Creator: Lost Ark Destroyer's cores in Guts's mood).
 * Up to three stacks are built like Lost Ark's cores (Creator 2026-10-23): a building skill that lands adds one,
 * whatever it hits, and normal hits add a little; a spender (地割り) takes every stack and hits harder per stack. Strong crowd control sits
 * on the spenders, so stacking crowd-control skills never chains it (Creator). No rage, no rampage, no self-heal.
 */
internal object CoreWarriorRules {
    /** Skills that take every stack; everything else builds. */
    val spenders = setOf("slam", FRENZY)
    const val FRENZY = "whirl"        // 狂乱
    const val IMPACT = "war_guard"    // 衝撃 (builds; takes the retired 天落とし's slot and id)
    const val SHATTER = "war_counter" // 大地崩し (retired 2026-11-04)
    const val LAUNCHER = "war_breach" // 斬り上げ (builds)
    const val BRACE = "war_cry"       // 受け止め (builds; takes the retired 引き摺り's slot and id)
    const val FULL_SWING = "war_ult"  // 全力の一振り: retired as an ultimate, now 地割り with three stacks (its look stays)
    const val ARMOR = "war_banner"    // 狂戦士の甲冑 (ultimate)
    const val AWAKEN = "war_awaken"   // 獣の目覚め (ultimate)
    const val RUSH = "dash"           // 轟進 (builds)

    // First person (Creator 2026-11-04): every skill happens in front of the eyes or moves the body, comes out on
    // the press, and pays the one who uses it well. Spending still pays a shield (Destroyer's cores).
    /** A spender pays out a shield for the stacks it took (Destroyer's cores): this share of max HP per stack. */
    const val BEAST_SHIELD_PERCENT_PER_STACK = 8.0
    const val BEAST_SHIELD_TICKS = 120
    /** While a spender is out, crowd control does not take hold and blows land at this share. */
    const val SWING_DAMAGE_TAKEN = .7
    /** 轟進: a run through what stands in front, on the press; each enemy passed is cut once; blows land at half meanwhile. */
    const val RUSH_TICKS = 10
    const val RUSH_SPEED = 9.0
    const val RUSH_REACH = 3.0
    const val RUSH_DAMAGE_TAKEN = .5
    /** 地割り with three stacks is 全力の一振り: the crack this long, each enemy in it hit this many times as hard. */
    const val FULL_SWING_RADIUS = 12.0
    const val FULL_SWING_MULTIPLIER = 2.0
    /**
     * 衝撃 (Creator 2026-11-04, Monster Hunter's impact burst): the blade driven into the ground once, then the next
     * [IMPACT_SWINGS] normal swings each bring a second blow down where they landed, [IMPACT_DELAY] ticks later, at
     * [IMPACT_SHARE] of the swing in [IMPACT_RADIUS]; the heavy third swing's in [IMPACT_HEAVY_RADIUS], staggering.
     * A swing that misses spends its count all the same. Each blow is a hit of its own (it builds the beast like a swing).
     */
    const val IMPACT_SWINGS = 8
    const val IMPACT_DELAY = 4L
    const val IMPACT_SHARE = .4
    const val IMPACT_RADIUS = 1.5
    const val IMPACT_HEAVY_RADIUS = 3.0
    const val IMPACT_HEAVY_STUN_MILLIS = 300L
    /**
     * 受け止め (Creator 2026-11-04, Overwatch's Zarya): a shield of this share of max HP for [BRACE_TICKS]. What it
     * stops heats the blade: stopping [HEAT_FULL_PERCENT] of max HP makes normal hits [HEAT_MAX_BONUS] harder, cooling
     * over [HEAT_COOL_TICKS]; every third of the shield stopped feeds one beast stack. It never heals.
     */
    const val BRACE_SHIELD_PERCENT = 20.0
    const val BRACE_TICKS = 60
    const val HEAT_FULL_PERCENT = 20.0
    const val HEAT_MAX_BONUS = .5
    const val HEAT_COOL_TICKS = 120
    const val BRACE_BEAST_PERCENT = BRACE_SHIELD_PERCENT / 3  // a broken-through shield is all three stacks
    /** 獣の目覚め (Destroyer's hypergravity): a shield, less taken, no crowd control; around, enemies slow and are drawn in; normal hits speed up. */
    const val AWAKEN_SHIELD_PERCENT = 20.0
    /**
     * 獣の目覚め runs on the beast (Creator 2026-11-05): three to go in, no cooldown, then it burns this much a second
     * and ends at none. Hits still feed it, so a warrior who builds faster than it burns stays awake for good; that
     * is an endgame build (獣の刻印石 stacked, 衝撃 kept landing), not the bare kit.
     */
    const val AWAKEN_BURN_PER_SECOND = .5
    /** 獣の刻印石: the beast builds this much faster at most (three high stones; the endgame road to staying awake). */
    const val BEAST_GAIN_BONUS_MAX = 60.0
    const val AWAKEN_DAMAGE_TAKEN = .85
    const val AWAKEN_RADIUS = 6.0
    const val AWAKEN_SLOW = .6
    const val AWAKEN_HASTE_PER_HIT = .1
    const val AWAKEN_HASTE_MAX = .5
    /** 狂戦士の甲冑: the beast builds this many times as fast. */
    const val ARMOR_BEAST_GAIN = 2.0
    /** 狂戦士の甲冑: faster moves and swings, and the health it burns each second (never below 1). */
    const val ARMOR_HASTE = 1.3
    const val ARMOR_BURN_PERCENT_PER_SECOND = 2.0
    /** 狂戦士の甲冑's look while it lasts: blood running off the body every few ticks, a spray on each heartbeat. */
    const val ARMOR_BLEED_PULSE = 100
    const val ARMOR_BEAT_PULSE = 300
    /** 獣の目覚め's look while it lasts: smoke clinging to the body, pulse numbers from here (one every 2 ticks). */
    const val AWAKEN_CLING_PULSE = 100
    /** 全力の一振り's blood burst runs out along the rift over [FULL_SWING_GEYSER_STEPS] ticks, two places a tick. */
    const val FULL_SWING_GEYSER_PULSE = 200
    const val FULL_SWING_GEYSER_STEPS = 8
    /** How long an enemy 斬り上げ threw up counts as in the air for 叩き落とし. */
    const val LAUNCHED_TICKS = 36L
    /** 叩き落とし: 地割り on an enemy 斬り上げ threw up. */
    const val SMASH_DOWN_PERCENT = 50
    const val SMASH_DOWN_STUN_MILLIS = 2000L
    const val BEAST_MAX = 3.0
    /** A building skill that lands, once per cast, whatever it hits (Lost Ark's cores, Creator 2026-10-23). */
    const val BEAST_PER_SKILL = 1.0
    /** A normal swing that connects; the heavy third swing builds twice as much. */
    const val BEAST_PER_NORMAL = .1
    /** One action (a swing, one skill hit or pulse) never builds more than this. */
    const val BEAST_PER_ACTION = 1.0
    const val BEAST_DAMAGE_PERCENT_PER_STACK = 25
    /** Extra break a spender deals per stack it took. */
    const val BEAST_BREAK_PER_STACK_SPENT = 4.0
    /** Extra damage per stack when the spender lands on a broken (downed) enemy. */
    const val BEAST_DOWN_PERCENT_PER_STACK = 15
    /** Flinch from the third normal hit. */
    const val FLINCH_MILLIS = 300L
    /** Ticks after a connected third normal hit in which the slam comes out fast and heavy. */
    const val FINISHER_TICKS = 12L
    const val FINISHER_STARTUP = 3
    const val FINISHER_BONUS_PERCENT = 30
    /**
     * 地割り by the stacks it took (Creator 2026-10-23): the more beast, the longer and deeper the crack.
     * 0 ≈4 m, regulars flinch / 1 ≈6 m, a low launch / 2 ≈8 m, launch then stun / 3 is 全力の一振り (Creator 2026-11-04):
     * the 12 m rift, a high launch and a longer stun, hit [FULL_SWING_MULTIPLIER] times as hard.
     */
    fun slam(definition: CoreSkillDefinition, stacks: Int): CoreSkillDefinition = when (stacks.coerceIn(0, 3)) {
        0 -> definition.copy(radius = 4.0, stunAfterMillis = 0).copy(crowd = CrowdControl.FLINCH, crowdMillis = FLINCH_MILLIS)
        1 -> definition.copy(radius = 6.0, stunAfterMillis = 0).copy(crowd = CrowdControl.AIRBORNE, crowdMillis = 450)
        2 -> definition.copy(radius = 8.0, stunAfterMillis = 1500).copy(crowd = CrowdControl.AIRBORNE, crowdMillis = 700)
        else -> definition.copy(radius = FULL_SWING_RADIUS, stunAfterMillis = 2000).copy(crowd = CrowdControl.AIRBORNE, crowdMillis = 1000)
    }
    /** Every spender by the stacks it took: 地割り reaches further, 狂乱 swings more. */
    fun shape(definition: CoreSkillDefinition, stacks: Int): CoreSkillDefinition {
        val n = stacks.coerceIn(0, 3)
        return when (definition.icon) {
            "slam" -> slam(definition, n)
            FRENZY -> definition.copy(pulses = 2 + n)
            else -> definition
        }
    }
}
