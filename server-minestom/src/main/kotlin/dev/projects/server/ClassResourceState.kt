package dev.projects.server

import dev.projects.protocol.ClassResourceSnapshot

/** Server-owned class resource state. Skill3 currently has no mana cost. */
class ClassResourceState(
    val maxMana: Int = MAX_MANA,
) {
    init {
        require(maxMana > 0) { "Max Mana must be positive" }
    }

    private var manaValue = maxMana

    val mana: Int
        get() = manaValue

    fun reset() {
        manaValue = maxMana
    }

    fun canSpend(amount: Int): Boolean = amount >= 0 && manaValue >= amount

    fun trySpend(amount: Int): Boolean {
        if (!canSpend(amount)) return false
        manaValue -= amount
        return true
    }

    fun snapshot(skill3CooldownTicks: Int): ClassResourceSnapshot = ClassResourceSnapshot(
        mana = mana,
        maxMana = maxMana,
        skill3CooldownTicks = skill3CooldownTicks,
        skill3CooldownMaxTicks = Skill3State.COOLDOWN_TICKS,
    )

    companion object {
        const val MAX_MANA = 100
    }
}
