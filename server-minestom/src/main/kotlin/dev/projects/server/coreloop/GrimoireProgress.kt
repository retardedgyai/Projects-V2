package dev.projects.server.coreloop

/**
 * One player's 魔術書 progress: finished entries, claimed rewards, the pin, counted goals and the wallet the
 * header shows. Ink is kept per aspect, as the research economy keeps it (FirstMagicState.researchInk).
 * Held in memory only; nothing here is saved yet.
 */
internal class GrimoireProgress(
    val done: MutableSet<String> = mutableSetOf(),
    val claimed: MutableSet<String> = mutableSetOf(),
    var pinned: String? = null,
    /** Counted goals by "entryId:goalIndex"; goals with a target of 1 are not counted. */
    val counts: MutableMap<String, Int> = mutableMapOf(),
    var rank: Int = 1,
    var rankXp: Int = 0,
    val ink: MutableMap<String, Int> = mutableMapOf(),
    var silver: Long = 0,
) {
    fun count(entry: String, goal: Int) = counts["$entry:$goal"] ?: 0
    val inkTotal get() = ink.values.sum()
}
