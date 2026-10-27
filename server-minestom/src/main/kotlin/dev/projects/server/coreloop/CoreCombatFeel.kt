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
        s.icon == "dash" || s.icon == "war_wound" -> 3
        s.icon in setOf("war_breach", "war_cry", "war_banner", "war_awaken") -> 4
        s.icon == CoreWarriorRules.FRENZY -> 6
        s.icon == CoreWarriorRules.SHATTER -> 7
        s.motion == CoreSkillMotion.GUARD -> 0
        s.motion == CoreSkillMotion.EVADE -> 2
        else -> profile(job).recovery
    }
    /** Ticks between a skill's pulses: 狂乱 swings faster than the usual 8. */
    fun pulseTicks(s: CoreSkillDefinition) = if (s.icon == CoreWarriorRules.FRENZY) 5 else 8
    /** 体当たり's impact comes at the end of its charge, at most [CoreWarriorRules.TACKLE_TICKS] after the startup. */
    fun lastImpact(s: CoreSkillDefinition, startup: Int) = startup + (s.pulses - 1) * pulseTicks(s) +
        if (s.icon == "dash") CoreWarriorRules.TACKLE_TICKS else 0
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
    val spenders = setOf("slam", FRENZY, SKYFALL, SHATTER)
    const val FRENZY = "whirl"        // 狂乱
    const val SKYFALL = "war_guard"   // 天落とし
    const val SHATTER = "war_counter" // 大地崩し
    const val LAUNCHER = "war_breach" // 斬り上げ (builds)
    const val DRAG = "war_cry"        // 引き摺り (builds)
    /** 全力の一振り: retired as an ultimate (Creator 2026-10-27); its id now names 地割り at a full beast (see [fullSwing]). */
    const val FULL_SWING = "war_ult"
    const val ARMOR = "war_banner"    // 狂戦士の甲冑 (ultimate)
    const val AWAKEN = "war_awaken"   // 獣の目覚め (ultimate)
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
    /**
     * 全力の一振り (Creator 2026-10-27: no longer an ultimate; 地割り with a full beast IS it): the sword is shouldered and
     * charged for [FULL_SWING_WINDUP] ticks (nothing stops it meanwhile), then one overhead blow cracks the ground far
     * ahead and blood bursts up along the rift. It hits like the old ultimate did (30 + AD 4.0 against 地割り's
     * 20 + AD 3.2, its break) and holds what it struck longer.
     */
    fun fullSwing(icon: String, stacks: Int) = icon == "slam" && stacks >= BEAST_MAX.toInt()
    const val FULL_SWING_WINDUP = 18
    const val FULL_SWING_RADIUS = 12.0
    const val FULL_SWING_BASE_SCALE = 1.5
    const val FULL_SWING_COEFFICIENT_SCALE = 1.25
    const val FULL_SWING_AIRBORNE_MILLIS = 1000L
    const val FULL_SWING_STUN_MILLIS = 2000L
    /** The ticks into the wind-up when the low rumble comes in (the heavy breath is at the cast). */
    const val FULL_SWING_RUMBLE_TICK = 8
    /** 地割り's hit-stop on each regular enemy struck (bosses never); 全力の一振り's is heavier. */
    const val SLAM_HITSTOP_TICKS = 3
    const val FULL_SWING_HITSTOP_TICKS = 7
    /** 引き摺り: how long the blade is dragged, how fast the warrior walks meanwhile, how far the hook reaches. */
    const val DRAG_TICKS = 40
    const val DRAG_SPEED = .8
    const val DRAG_HOOK = 1.5
    /**
     * Building skills by situation (Creator 2026-10-27: 「このスキルはこういうシチュエーションで…使い分け」): an ordinary
     * hit builds only [BUILDER_BASE]; used where it is meant to be, it builds much more (see [builderGain]).
     */
    const val BUILDER_BASE = .5
    /** 跳び叩き: a leap of at least [LEAP_FAR] blocks that lands on someone builds [LEAP_FAR_BEAST]. */
    const val LEAP_FAR = 6.0
    const val LEAP_FAR_BEAST = 1.5
    /**
     * 体当たり (it replaced 轟進, Creator 2026-10-27: 「一撃だけってのが肝」): a shoulder charge of up to [TACKLE_TICKS]
     * ticks, [TACKLE_STEP] blocks a tick, that stops on the first enemy in front. From the press to the impact the
     * first blow taken is caught whole (no damage, no stagger) and builds [TACKLE_CATCH_BEAST]; the impact after a
     * catch hits [TACKLE_CATCH_SCALE] times as hard and stuns. Only one blow is ever caught.
     */
    const val TACKLE_TICKS = 6
    const val TACKLE_STEP = .75
    const val TACKLE_CONTACT = 1.5
    const val TACKLE_RADIUS = 2.2
    const val TACKLE_CATCH_BEAST = 2.0
    const val TACKLE_CATCH_SCALE = 1.6
    const val TACKLE_CATCH_STUN_MILLIS = 800L
    /** The impact's hit-stop: each regular enemy hit is frozen this long (bosses never), longer after a catch. */
    const val TACKLE_HITSTOP_TICKS = 3
    const val TACKLE_CATCH_HITSTOP_TICKS = 5
    /** 跳び叩き's landing hit-stop: each regular enemy hit is frozen this long (bosses never), longer after a far leap. */
    const val LEAP_HITSTOP_TICKS = 3
    const val LEAP_FAR_HITSTOP_TICKS = 5
    fun leapHitStopTicks(leapReach: Double) = if (leapReach >= LEAP_FAR) LEAP_FAR_HITSTOP_TICKS else LEAP_HITSTOP_TICKS
    fun builderGain(icon: String, caught: Boolean, leapReach: Double) = when (icon) {
        "dash" -> if (caught) 0.0 else BUILDER_BASE                       // the catch already paid out
        CoreWarriorLeap.ICON -> if (leapReach >= LEAP_FAR) LEAP_FAR_BEAST else BUILDER_BASE
        else -> BEAST_PER_SKILL
    }
    /** The effect pulse that marks 引き摺り's final swing (the drag's own pulses count up from 1). */
    const val DRAG_FINISH_PULSE = 99
    /** How long an enemy 斬り上げ threw up counts as in the air for 叩き落とし. */
    const val LAUNCHED_TICKS = 36L
    /** 叩き落とし: 地割り on an enemy 斬り上げ threw up. */
    const val SMASH_DOWN_PERCENT = 50
    const val SMASH_DOWN_STUN_MILLIS = 2000L
    /** 天落とし: the rings the landing sends out, inside to outside. */
    val SKYFALL_RINGS = listOf(3.5, 6.0, 8.0)
    const val SKYFALL_RING_TICKS = 4
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
     * 0 ≈4 m, regulars flinch / 1 ≈6 m, a low launch / 2 ≈8 m, launch then stun / 3 is 全力の一振り ([fullSwing]):
     * a long wind-up, then ≈12 m, a high launch and a longer stun, the old ultimate's weight.
     */
    fun slam(definition: CoreSkillDefinition, stacks: Int): CoreSkillDefinition = when (stacks.coerceIn(0, 3)) {
        0 -> definition.copy(radius = 4.0, stunAfterMillis = 0).copy(crowd = CrowdControl.FLINCH, crowdMillis = FLINCH_MILLIS)
        1 -> definition.copy(radius = 6.0, stunAfterMillis = 0).copy(crowd = CrowdControl.AIRBORNE, crowdMillis = 450)
        2 -> definition.copy(radius = 8.0, stunAfterMillis = 1500).copy(crowd = CrowdControl.AIRBORNE, crowdMillis = 700)
        else -> definition.copy(radius = FULL_SWING_RADIUS, stunAfterMillis = FULL_SWING_STUN_MILLIS,
            formula = definition.formula.let { CoreDamageFormula(it.base * FULL_SWING_BASE_SCALE, it.ad * FULL_SWING_COEFFICIENT_SCALE,
                it.ap * FULL_SWING_COEFFICIENT_SCALE) })
            .copy(crowd = CrowdControl.AIRBORNE, crowdMillis = FULL_SWING_AIRBORNE_MILLIS)
    }
    /**
     * Each spender has its own job, and the beast changes how it behaves, not only how hard it hits (Creator 2026-10-26):
     * 地割り splits the far line, 狂乱 grinds down the one in front, 天落とし dives into a distant pack, 大地崩し breaks out
     * of a crowd. Few stacks come out fast, a full beast winds up long ([STARTUP_BY_STACKS]).
     */
    fun shape(definition: CoreSkillDefinition, stacks: Int): CoreSkillDefinition {
        val n = stacks.coerceIn(0, 3)
        val shaped = when (definition.icon) {
            "slam" -> slam(definition, n)
            FRENZY -> definition.copy(pulses = 2 + n)
            SKYFALL -> definition.copy(pulses = maxOf(1, n), range = SKYFALL_REACH[n])
            SHATTER -> definition.copy(radius = SHATTER_RADIUS[n], crowdMillis = 350L + 150L * n, stunAfterMillis = if (n == 3) 1600L else 0L)
            else -> return definition
        }
        if (fullSwing(definition.icon, n)) return shaped.copy(startup = FULL_SWING_WINDUP)
        return shaped.copy(startup = maxOf(1, Math.round(definition.startup * STARTUP_BY_STACKS[n]).toInt()))
    }
    /** A spender's wind-up by the stacks it took, against its listed startup: 0 stacks 60% … 3 stacks as listed. */
    val STARTUP_BY_STACKS = listOf(.6, .75, .9, 1.0)
    /** 狂乱 keeps to the one in front: a narrower fan than other swings, the last blow narrower still. */
    const val FRENZY_MIN_DOT = .6
    const val FRENZY_LAST_MIN_DOT = .75
    /** 狂乱 breaks a boss harder than its listed break on every swing; with a full beast the last blow breaks far more. */
    const val FRENZY_BREAK_SCALE = 1.25
    const val FRENZY_FULL_LAST_BREAK = 2.0
    /** 天落とし by the stacks: how far (blocks) and how high the jump goes. Few stacks hop low and land quickly. */
    val SKYFALL_REACH = listOf(5.0, 5.0, 6.5, 8.0)
    val SKYFALL_APEX = listOf(2.0, 2.0, 3.2, 5.0)
    /** 大地崩し by the stacks; with none it throws the crowd outward this far (regulars only). */
    val SHATTER_RADIUS = listOf(3.5, 5.0, 6.0, 7.0)
    const val SHATTER_SHOVE = 2.5
    /** 大地崩し: castable while staggered with at least one stack, untouchable for its first ticks. */
    const val SHATTER_EVADE_TICKS = 6L

    /**
     * A + B (Creator 2026-10-26): a spender within [COMBO_TICKS] of a building skill that landed turns into that
     * pair's variant (斬り上げ + 地割り's 叩き落とし goes by the launched enemies instead, see LAUNCHED_TICKS).
     */
    enum class Combo(val builder: String, val spender: String, val label: String) {
        HOIST(DRAG, SHATTER, "引き摺り＋大地崩し"),
        DOUBLE_DROP(CoreWarriorLeap.ICON, SKYFALL, "跳び叩き＋天落とし：二段落とし"),
        RUSH("dash", FRENZY, "体当たり＋狂乱"),
    }
    fun combo(builder: String?, spender: String): Combo? = Combo.entries.firstOrNull { it.builder == builder && it.spender == spender }
    const val COMBO_TICKS = 40L
    /** 引き摺り＋大地崩し: the gathered crowd goes up higher and comes down stunned, from a little further out. */
    const val HOIST_RADIUS_BONUS = 1.0
    const val HOIST_AIRBORNE_MILLIS = 1400L
    const val HOIST_STUN_MILLIS = 1500L
    /** 跳び叩き＋天落とし: the rings go out twice as fast. */
    const val DOUBLE_DROP_RING_TICKS = 2
    /** 轟進＋狂乱: the first step goes further and the first swing lands heavy (damage and break). */
    const val RUSH_FIRST_PUSH = 9.0
    const val RUSH_FIRST_SCALE = 1.8
    /** 剣聖: each connected third normal hit takes this much off the spenders' remaining cooldown. */
    const val SAINT_REFUND_TICKS = 30L
}
