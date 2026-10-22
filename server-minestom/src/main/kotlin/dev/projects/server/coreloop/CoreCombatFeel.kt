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
        CoreClass.MAGE, CoreClass.HEALER, CoreClass.STARWEAVER -> Profile(6, 0, immediateNormal = false, recovery = 5, attackBreaksGuard = true)
    }
    fun recovery(job: CoreClass, s: CoreSkillDefinition) = when {
        s.icon == "dash" || s.icon == "war_wound" -> 3
        s.icon in setOf("war_breach", "war_cry", "war_banner") -> 4
        s.icon == CoreWarriorRules.FRENZY -> 6
        s.icon == CoreWarriorRules.SHATTER -> 7
        s.motion == CoreSkillMotion.GUARD -> 0
        s.motion == CoreSkillMotion.EVADE -> 2
        else -> profile(job).recovery
    }
    /** Ticks between a skill's pulses: 狂乱 swings faster than the usual 8. */
    fun pulseTicks(s: CoreSkillDefinition) = if (s.icon == CoreWarriorRules.FRENZY) 6 else 8
    fun lastImpact(s: CoreSkillDefinition, startup: Int) = startup + (s.pulses - 1) * pulseTicks(s)
    fun finish(job: CoreClass, s: CoreSkillDefinition, startup: Int) = lastImpact(s, startup) + recovery(job, s)
    /** A guard may cut a skill after its last impact and any normal follow-through. */
    fun cancelsIntoGuard(s: CoreSkillDefinition) = s.motion == CoreSkillMotion.GUARD
    fun minDot(icon: String) = when (icon) {
        "slam", "war_breach" -> .8 // Commit the heavy blade along the aimed lane.
        else -> .35
    }
}

/**
 * Warrior rules that are not timing: the beast (獣, 2026-10-22, Creator: Lost Ark Destroyer's cores in Guts's mood).
 * Up to three stacks are built by crowd control on regular enemies and by wearing down a boss's break gauge with
 * the skills that build; a spender (地割り) takes every stack and hits harder per stack. Strong crowd control sits
 * on the spenders, so stacking crowd-control skills never chains it (Creator). No rage, no rampage, no self-heal.
 */
internal object CoreWarriorRules {
    /** Skills that take every stack; everything else builds. */
    val spenders = setOf("slam", FRENZY, SKYFALL, SHATTER)
    const val FRENZY = "whirl"        // 狂乱
    const val SKYFALL = "war_guard"   // 天落とし
    const val SHATTER = "war_counter" // 大地崩し
    /** 天落とし: the rings the landing sends out, inside to outside. */
    val SKYFALL_RINGS = listOf(3.5, 6.0, 8.0)
    const val SKYFALL_RING_TICKS = 4
    const val BEAST_MAX = 3.0
    /** Each regular enemy put under crowd control by a building action. */
    const val BEAST_PER_CC = 1.0 / 3.0
    /** Break dealt to a boss by building actions that makes one stack. */
    const val BEAST_BREAK_PER_STACK = 12.0
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
     * 0 ≈4 m, regulars flinch / 1 ≈6 m, a low launch / 2 ≈8 m, launch then stun / 3 ≈8 m, a high launch and a longer
     * stun, and the crack splits a second time at its head (a second pulse).
     */
    fun slam(definition: CoreSkillDefinition, stacks: Int): CoreSkillDefinition = when (stacks.coerceIn(0, 3)) {
        0 -> definition.copy(radius = 4.0, stunAfterMillis = 0).copy(crowd = CrowdControl.FLINCH, crowdMillis = FLINCH_MILLIS)
        1 -> definition.copy(radius = 6.0, stunAfterMillis = 0).copy(crowd = CrowdControl.AIRBORNE, crowdMillis = 450)
        2 -> definition.copy(radius = 8.0, stunAfterMillis = 1500).copy(crowd = CrowdControl.AIRBORNE, crowdMillis = 700)
        else -> definition.copy(radius = 8.0, stunAfterMillis = 2000, pulses = 2).copy(crowd = CrowdControl.AIRBORNE, crowdMillis = 1000)
    }
    /** Every spender by the stacks it took: 地割り reaches further, 狂乱 swings more, 天落とし sends more rings, 大地崩し breaks wider. */
    fun shape(definition: CoreSkillDefinition, stacks: Int): CoreSkillDefinition {
        val n = stacks.coerceIn(0, 3)
        return when (definition.icon) {
            "slam" -> slam(definition, n)
            FRENZY -> definition.copy(pulses = 2 + n)
            SKYFALL -> definition.copy(pulses = maxOf(1, n))
            SHATTER -> definition.copy(radius = 4.0 + n, crowdMillis = 450L + 150L * n, stunAfterMillis = if (n == 3) 1600L else 0L)
            else -> definition
        }
    }
    /** 剣聖: each connected third normal hit takes this much off the spenders' remaining cooldown. */
    const val SAINT_REFUND_TICKS = 30L
}
