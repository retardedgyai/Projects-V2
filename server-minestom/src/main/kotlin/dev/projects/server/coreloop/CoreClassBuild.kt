package dev.projects.server.coreloop

/** Stable catalog indices: four distinct skills, one ultimate, and the shared passive tree picks ([CorePassiveTree]). */
data class CoreClassBuild(val first: Int = 0, val second: Int = 1, val third: Int = 2, val fourth: Int = 3,
    val ultimate: Int = 0, val passives: Set<Int> = emptySet()) {
    val skills get() = listOf(first, second, third, fourth)
    init {
        require(skills.all { it in 0..7 } && skills.distinct().size == 4 && ultimate in 0..2)  // the warrior has three ultimates
    }
    fun equip(slot: Int, choice: Int): CoreClassBuild {
        require(slot in 0..4)
        if (slot == 4) return copy(ultimate = choice)
        require(choice in 0..7)
        val next = skills.toMutableList()
        val oldSlot = next.indexOf(choice)
        if (oldSlot >= 0) next[oldSlot] = next[slot]
        next[slot] = choice
        return copy(first = next[0], second = next[1], third = next[2], fourth = next[3])
    }
}
