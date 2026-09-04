package dev.projects.server.coreloop.ui

/** A player's return path while obtaining ingredients. Contains selections, never executable actions. */
internal class CoreForgeJourney {
    private val goals = ArrayDeque<CoreForgeLayout.Selection>()

    val depth: Int get() = goals.size
    val isEmpty: Boolean get() = goals.isEmpty()

    fun push(selection: CoreForgeLayout.Selection) {
        if (goals.lastOrNull() == selection) return
        if (goals.size == MAX_DEPTH) goals.removeFirst()
        goals.addLast(selection)
    }

    fun peek(): CoreForgeLayout.Selection? = goals.lastOrNull()
    fun pop(): CoreForgeLayout.Selection? = goals.removeLastOrNull()
    fun clear() = goals.clear()

    companion object {
        const val MAX_DEPTH = 8
    }
}
