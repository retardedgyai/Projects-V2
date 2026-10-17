package dev.projects.server.mob

/**
 * Crowd control a player skill can put on an enemy (2026-10-17, Creator: "LoL-like CC", bosses use a break gauge).
 * Slow and taunt keep their own APIs. Bosses never take any of these: each one wears [breakDamage] off the
 * boss break gauge instead, and an empty gauge knocks the boss down ([QuestEncounterCombat.BREAK_MILLIS]).
 */
enum class CrowdControl(val label: String, val hard: Boolean, val breakDamage: Double) {
    /** Cannot move or attack; a windup in progress is cancelled. */
    STUN("スタン", true, 20.0),
    /** Thrown up; nothing until it lands. */
    AIRBORNE("打ち上げ", true, 20.0),
    /** Shoved away along a clear path; nothing while sliding. */
    KNOCKBACK("ノックバック", true, 15.0),
    /** Walks toward the caster and does not attack. */
    CHARM("チャーム", true, 15.0),
    /** Runs away from the caster and does not attack. */
    FEAR("フィアー", true, 15.0),
    /** Only the windup in progress is cancelled. */
    FLINCH("怯み", false, 6.0),
    /** Cannot move but still attacks. */
    ROOT("拘束", false, 8.0),
    /** Attacks land well off the aimed spot. */
    BLIND("盲目", false, 8.0),
    /** Only the first (plain) ability can start. */
    SILENCE("沈黙", false, 8.0),
}
