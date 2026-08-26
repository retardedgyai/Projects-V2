package dev.projects.server

/** Deterministic in-memory progression for the v0 playable loop. */
class ProgressionState {
    var xp: Int = 0
        private set
    var level: Int = 1
        private set
    var unspentSkillPoints: Int = 0
        private set

    private val acquiredNodes = linkedSetOf<String>()

    fun addXp(amount: Int): Int {
        require(amount >= 0) { "XP must not be negative" }
        xp += amount
        var levels = 0
        while (xp >= xpForNextLevel(level)) {
            xp -= xpForNextLevel(level)
            level++
            unspentSkillPoints++
            levels++
        }
        return levels
    }

    fun canSpend(nodeId: String): SpendResult {
        val node = TwinBladesSkillTree.node(nodeId) ?: return SpendResult.UNKNOWN_NODE
        if (node.id in acquiredNodes) return SpendResult.ALREADY_ACQUIRED
        if (!node.prerequisites.all { it in acquiredNodes }) return SpendResult.MISSING_PREREQUISITE
        if (unspentSkillPoints < node.cost) return SpendResult.INSUFFICIENT_POINTS
        return SpendResult.ACCEPTED
    }

    fun spend(nodeId: String): SpendResult {
        val result = canSpend(nodeId)
        if (result == SpendResult.ACCEPTED) {
            val node = requireNotNull(TwinBladesSkillTree.node(nodeId))
            acquiredNodes += node.id
            unspentSkillPoints -= node.cost
        }
        return result
    }

    fun has(nodeId: String): Boolean = nodeId in acquiredNodes

    fun acquiredNodeIds(): Set<String> = acquiredNodes.toSet()

    companion object {
        fun xpForNextLevel(level: Int): Int {
            require(level >= 1) { "Level must be positive" }
            return 100 + (level - 1) * 50
        }
    }
}

enum class SpendResult {
    ACCEPTED,
    UNKNOWN_NODE,
    ALREADY_ACQUIRED,
    INSUFFICIENT_POINTS,
    MISSING_PREREQUISITE,
}

data class SkillNode(
    val id: String,
    val name: String,
    val description: String,
    val cost: Int = 1,
    val prerequisites: Set<String> = emptySet(),
)

object TwinBladesSkillTree {
    val nodes = listOf(
        SkillNode("keen-edge", "Keen Edge", "+10% normal attack damage"),
        SkillNode("swift-step", "Swift Step", "Skill 1 dash lasts one extra tick", prerequisites = setOf("keen-edge")),
        SkillNode("wide-cut", "Wide Cut", "Skill 1 hit radius +0.35", prerequisites = setOf("swift-step")),
        SkillNode("falling-star", "Falling Star", "Skill 2 pulse radius +0.5", prerequisites = setOf("keen-edge")),
        SkillNode("deep-cut", "Deep Cut", "Skill 2 landing damage +25%", prerequisites = setOf("falling-star")),
        SkillNode("air-dancer", "Air Dancer", "Skill 3 dash lasts one extra tick", prerequisites = setOf("swift-step")),
        SkillNode("execution-rhythm", "Execution Rhythm", "Skill 3 pulses arrive one tick faster", prerequisites = setOf("air-dancer")),
        SkillNode("final-edge", "Final Edge", "Skill 3 finisher damage +25%", prerequisites = setOf("execution-rhythm")),
    )

    fun node(id: String): SkillNode? = nodes.firstOrNull { it.id == id }
}

data class TwinBladesProgressionEffects(
    val normalDamageMultiplier: Double,
    val skill1DashTicks: Int,
    val skill1HitRadius: Double,
    val skill2PulseRadius: Double,
    val skill2LandingDamageMultiplier: Double,
    val skill3DashTicks: Int,
    val skill3PulseInterval: Int,
    val skill3FinisherDamageMultiplier: Double,
)

fun ProgressionState.twinBladesEffects(): TwinBladesProgressionEffects = TwinBladesProgressionEffects(
    normalDamageMultiplier = if (has("keen-edge")) 1.10 else 1.0,
    skill1DashTicks = if (has("swift-step")) Skill1State.DASH_TICKS + 1 else Skill1State.DASH_TICKS,
    skill1HitRadius = Skill1State.HIT_RADIUS + if (has("wide-cut")) 0.35 else 0.0,
    skill2PulseRadius = Skill2State.PULSE_RADIUS + if (has("falling-star")) 0.5 else 0.0,
    skill2LandingDamageMultiplier = if (has("deep-cut")) 1.25 else 1.0,
    skill3DashTicks = if (has("air-dancer")) Skill3State.DASH_TICKS + 1 else Skill3State.DASH_TICKS,
    skill3PulseInterval = if (has("execution-rhythm")) 1 else Skill3State.PULSE_INTERVAL_TICKS,
    skill3FinisherDamageMultiplier = if (has("final-edge")) 1.25 else 1.0,
)
