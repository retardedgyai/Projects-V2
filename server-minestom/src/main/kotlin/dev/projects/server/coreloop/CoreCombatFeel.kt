package dev.projects.server.coreloop

/** Warrior-only timing contract. Other classes retain their existing action scheduler. */
internal object CoreWarriorCombatRules {
    const val BUFFER_TICKS = 6L
    const val NORMAL = -1
    const val AA_LINK_TICKS = 6
    fun counterSkill(icon: String) = icon == "slam" || icon == "war_counter"
    fun recovery(icon: String) = when (icon) {
        "war_guard" -> 0
        "dash", "war_wound" -> 3
        "whirl", "war_counter", "war_breach", "war_cry", "war_banner" -> 4
        else -> 7
    }
    fun lastImpact(s: CoreSkillDefinition, startup: Int) = startup + (s.pulses - 1) * 8
    fun finish(s: CoreSkillDefinition, startup: Int) = lastImpact(s, startup) + recovery(s.icon)
    fun minDot(icon: String) = when (icon) {
        "whirl" -> 0.0 // A broad frontal sweep, not damage behind the wielder.
        "slam", "war_breach" -> .8 // Commit the heavy blade along the aimed lane.
        else -> .35
    }
}
