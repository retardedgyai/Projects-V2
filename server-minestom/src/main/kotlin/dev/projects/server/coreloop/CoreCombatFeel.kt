package dev.projects.server.coreloop

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
    /** Warrior parry follow-ups; the parry/counter set itself is reworked with the warrior skills. */
    fun counterSkill(icon: String) = icon == "slam" || icon == "war_counter"
    fun recovery(job: CoreClass, s: CoreSkillDefinition) = when {
        s.icon == "dash" || s.icon == "war_wound" -> 3
        s.icon in setOf("whirl", "war_counter", "war_breach", "war_cry", "war_banner") -> 4
        s.motion == CoreSkillMotion.GUARD -> 0
        s.motion == CoreSkillMotion.EVADE -> 2
        else -> profile(job).recovery
    }
    fun lastImpact(s: CoreSkillDefinition, startup: Int) = startup + (s.pulses - 1) * 8
    fun finish(job: CoreClass, s: CoreSkillDefinition, startup: Int) = lastImpact(s, startup) + recovery(job, s)
    /** A guard may cut a skill after its last impact and any normal follow-through. */
    fun cancelsIntoGuard(s: CoreSkillDefinition) = s.motion == CoreSkillMotion.GUARD
    fun minDot(icon: String) = when (icon) {
        "whirl" -> 0.0 // A broad frontal sweep, not damage behind the wielder.
        "slam", "war_breach" -> .8 // Commit the heavy blade along the aimed lane.
        else -> .35
    }
}
