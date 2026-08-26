package dev.projects.server

enum class PrimalEssence {
    EMBER,
    TIDE,
    GALE,
    STONE,
}

enum class DerivedEssence(val first: PrimalEssence, val second: PrimalEssence) {
    SPARK(PrimalEssence.EMBER, PrimalEssence.GALE),
    BLOOM(PrimalEssence.EMBER, PrimalEssence.TIDE),
    SURGE(PrimalEssence.TIDE, PrimalEssence.GALE),
    WARD(PrimalEssence.STONE, PrimalEssence.TIDE),
}

/** Small server-owned v0 inventory for the magic prototype. */
class MagicEssenceState(
    val capacity: Int = DEFAULT_CAPACITY,
) {
    init {
        require(capacity > 0) { "Essence capacity must be positive" }
    }

    private val primal = PrimalEssence.entries.associateWith { 0 }.toMutableMap()
    private val derived = DerivedEssence.entries.associateWith { 0 }.toMutableMap()

    fun amount(essence: PrimalEssence): Int = primal.getValue(essence)

    fun amount(essence: DerivedEssence): Int = derived.getValue(essence)

    fun grant(essence: PrimalEssence, amount: Int): Int {
        require(amount >= 0) { "Essence amount must not be negative" }
        val accepted = amount.coerceAtMost(capacity - primal.getValue(essence))
        primal[essence] = primal.getValue(essence) + accepted
        return accepted
    }

    fun canCombine(first: PrimalEssence, second: PrimalEssence): Boolean =
        first != second && amount(first) > 0 && amount(second) > 0 && recipe(first, second) != null

    fun tryCombine(first: PrimalEssence, second: PrimalEssence): DerivedEssence? {
        val result = recipe(first, second) ?: return null
        if (!canCombine(first, second)) return null
        primal[first] = amount(first) - 1
        primal[second] = amount(second) - 1
        derived[result] = amount(result) + 1
        return result
    }

    fun tryConsume(result: DerivedEssence): Boolean {
        if (amount(result) == 0) return false
        derived[result] = amount(result) - 1
        return true
    }

    fun reset() {
        PrimalEssence.entries.forEach { primal[it] = 0 }
        DerivedEssence.entries.forEach { derived[it] = 0 }
    }

    fun snapshot(): String = buildString {
        append("primal=")
        append(PrimalEssence.entries.joinToString(",") { "${it.name.lowercase()}:${amount(it)}" })
        append(" derived=")
        append(DerivedEssence.entries.joinToString(",") { "${it.name.lowercase()}:${amount(it)}" })
        append(" capacity=$capacity")
    }

    private fun recipe(first: PrimalEssence, second: PrimalEssence): DerivedEssence? =
        DerivedEssence.entries.firstOrNull {
            (it.first == first && it.second == second) || (it.first == second && it.second == first)
        }

    companion object {
        const val DEFAULT_CAPACITY = 10
    }
}
