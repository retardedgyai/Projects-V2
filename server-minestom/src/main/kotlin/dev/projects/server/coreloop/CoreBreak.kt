package dev.projects.server.coreloop

/**
 * How much a hit wears down a boss's break gauge (2026-10-19, Creator: the warrior is the "breaker", every class can
 * break a little, the warrior breaks the most; the feel is the weight of one blow). The gauge holds
 * [dev.projects.server.mob.QuestEncounterCombat.BREAK_MAX]; an empty gauge breaks the boss for a few seconds.
 * Bosses take break instead of crowd control; regular enemies still take the crowd control.
 */
internal object CoreBreak {
    const val NORMAL = 1.0
    const val NORMAL_WARRIOR = 2.0
    /** The warrior's heavy third normal hit. */
    const val NORMAL_WARRIOR_THIRD = 5.0
    /** Any other class's skill, per hit. */
    const val SKILL = 4.0
    private val WARRIOR = mapOf("dash" to 4.0, "slam" to 12.0, "whirl" to 4.0, "war_wound" to 6.0, "war_ult" to 15.0)
    /** 地割り right after the third normal hit. */
    const val FINISHER = 1.5
    /** 地割り within [RIPOSTE_TICKS] of 根張り catching a blow: the return strike. */
    const val RIPOSTE = 2.0
    const val RIPOSTE_TICKS = 60L

    fun skill(job: CoreClass, icon: String): Double =
        if (job == CoreClass.WARRIOR) WARRIOR[icon] ?: 0.0 else SKILL
    fun normal(job: CoreClass, heavy: Boolean): Double = when {
        job != CoreClass.WARRIOR -> NORMAL
        heavy -> NORMAL_WARRIOR_THIRD
        else -> NORMAL_WARRIOR
    }
}
