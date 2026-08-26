package dev.projects.server

/** A resolved set of player-wide modifiers used by the current combat adapter. */
data class ProgressionEffects(
    val directDamageMultiplier: Double = 1.0,
    val normalAttackSpeedMultiplier: Double = 1.0,
    val cooldownRecoveryMultiplier: Double = 1.0,
    val maxHealthBonus: Int = 0,
    val incomingPveDamageMultiplier: Double = 1.0,
) {
    companion object {
        val DEFAULT = ProgressionEffects()
    }
}

data class PassiveNode(
    val id: String,
    val label: String,
    val description: String,
    val cost: Int = 1,
    val prerequisites: Set<String> = emptySet(),
    val directDamageBonus: Double = 0.0,
    val attackSpeedBonus: Double = 0.0,
    val cooldownRecoveryBonus: Double = 0.0,
    val maxHealthBonus: Int = 0,
    val incomingDamageReduction: Double = 0.0,
) {
    init {
        require(id.isNotBlank()) { "Passive node id must not be blank" }
        require(cost > 0) { "Passive node cost must be positive" }
        require(directDamageBonus >= 0.0 && directDamageBonus.isFinite()) {
            "Passive node direct damage bonus must be finite and non-negative"
        }
        require(attackSpeedBonus >= 0.0 && attackSpeedBonus.isFinite()) {
            "Passive node attack speed bonus must be finite and non-negative"
        }
        require(cooldownRecoveryBonus >= 0.0 && cooldownRecoveryBonus.isFinite()) {
            "Passive node cooldown bonus must be finite and non-negative"
        }
        require(maxHealthBonus >= 0) { "Passive node health bonus must be non-negative" }
        require(incomingDamageReduction in 0.0..1.0 && incomingDamageReduction.isFinite()) {
            "Passive node damage reduction must be between 0 and 1"
        }
    }
}

object GlobalPassiveTree {
    const val FORCE = "projects:passive/force"
    const val OVERPOWER = "projects:passive/overpower"
    const val TEMPO = "projects:passive/tempo"
    const val FLOW = "projects:passive/flow"
    const val VITALITY = "projects:passive/vitality"
    const val GUARD = "projects:passive/guard"

    val nodes: List<PassiveNode> = listOf(
        PassiveNode(
            id = FORCE,
            label = "Force",
            description = "通常攻撃と直接スキルのダメージ +15%",
            directDamageBonus = 0.15,
        ),
        PassiveNode(
            id = OVERPOWER,
            label = "Overpower",
            description = "通常攻撃と直接スキルのダメージをさらに +15%",
            prerequisites = setOf(FORCE),
            directDamageBonus = 0.15,
        ),
        PassiveNode(
            id = TEMPO,
            label = "Tempo",
            description = "通常攻撃速度 +15%",
            attackSpeedBonus = 0.15,
        ),
        PassiveNode(
            id = FLOW,
            label = "Flow",
            description = "S1 / S2 / S3のクールダウン回復 +20%",
            prerequisites = setOf(TEMPO),
            cooldownRecoveryBonus = 0.20,
        ),
        PassiveNode(
            id = VITALITY,
            label = "Vitality",
            description = "最大HP +4",
            maxHealthBonus = 4,
        ),
        PassiveNode(
            id = GUARD,
            label = "Guard",
            description = "PvE被ダメージ -10%",
            prerequisites = setOf(VITALITY),
            incomingDamageReduction = 0.10,
        ),
    )

    private val nodesById = nodes.associateBy(PassiveNode::id)

    fun node(id: String): PassiveNode? = nodesById[id]
}

enum class PassiveSpendResult {
    ACCEPTED,
    UNKNOWN_NODE,
    ALREADY_ACQUIRED,
    INSUFFICIENT_POINTS,
    MISSING_PREREQUISITE,
    STALE_REVISION,
}

data class ProgressionGain(
    val amount: Int,
    val levelUpCount: Int,
    val passivePointsGranted: Int,
    val resultingLevel: Int,
    val revision: Long,
)

data class ProgressionRecord(
    val level: Int,
    val experience: Int,
    val grantedPassivePoints: Int,
    val spentPassivePoints: Int,
    val allocatedPassiveNodeIds: List<String>,
    val revision: Long,
)

/** Server-owned global player progression. Experience is the remainder within the current level. */
class ProgressionState(
    level: Int = 1,
    experience: Int = 0,
    grantedPassivePoints: Int = 0,
    spentPassivePoints: Int = 0,
    allocatedPassiveNodeIds: Collection<String> = emptyList(),
    revision: Long = 0L,
) {
    var level: Int = level
        private set
    var experience: Int = experience
        private set
    var grantedPassivePoints: Int = grantedPassivePoints
        private set
    var spentPassivePoints: Int = spentPassivePoints
        private set
    var revision: Long = revision
        private set

    private val allocatedNodes = linkedSetOf<String>()

    init {
        require(level in 1..MAX_LEVEL) { "Progression level must be between 1 and $MAX_LEVEL" }
        require(experience >= 0) { "Progression experience must not be negative" }
        require(level == MAX_LEVEL || experience < xpRequired(level)) {
            "Progression experience must be below the next level threshold"
        }
        require(grantedPassivePoints >= 0) { "Granted passive points must not be negative" }
        require(spentPassivePoints >= 0) { "Spent passive points must not be negative" }
        require(spentPassivePoints <= grantedPassivePoints) {
            "Spent passive points must not exceed granted passive points"
        }
        require(revision >= 0L) { "Progression revision must not be negative" }
        allocatedPassiveNodeIds.forEach { nodeId ->
            require(allocatedNodes.add(nodeId)) { "Duplicate passive node: $nodeId" }
            require(GlobalPassiveTree.node(nodeId) != null) { "Unknown passive node: $nodeId" }
        }
        require(spentPassivePoints == allocatedNodes.sumOf { requireNotNull(GlobalPassiveTree.node(it)).cost }) {
            "Spent passive points must match allocated node costs"
        }
        if (level == MAX_LEVEL) this.experience = 0
    }

    val allocatedPassiveNodeIds: Set<String>
        get() = allocatedNodes.toSet()

    val availablePassivePoints: Int
        get() = grantedPassivePoints - spentPassivePoints

    val xpRequiredForNextLevel: Int
        get() = if (level == MAX_LEVEL) 0 else xpRequired(level)

    fun addXp(amount: Int): ProgressionGain {
        require(amount >= 0) { "XP must not be negative" }
        if (amount == 0) {
            return ProgressionGain(0, 0, 0, level, revision)
        }

        var remainingExperience = experience.toLong() + amount.toLong()
        var levelUps = 0
        while (level < MAX_LEVEL && remainingExperience >= xpRequired(level)) {
            remainingExperience -= xpRequired(level).toLong()
            level++
            grantedPassivePoints++
            levelUps++
        }
        experience = if (level == MAX_LEVEL) 0 else remainingExperience.toInt()
        revision++
        return ProgressionGain(amount, levelUps, levelUps, level, revision)
    }

    fun spend(nodeId: String, expectedRevision: Long? = null): PassiveSpendResult {
        if (expectedRevision != null && expectedRevision != revision) {
            return PassiveSpendResult.STALE_REVISION
        }
        val node = GlobalPassiveTree.node(nodeId) ?: return PassiveSpendResult.UNKNOWN_NODE
        if (nodeId in allocatedNodes) return PassiveSpendResult.ALREADY_ACQUIRED
        if (!node.prerequisites.all(allocatedNodes::contains)) {
            return PassiveSpendResult.MISSING_PREREQUISITE
        }
        if (availablePassivePoints < node.cost) return PassiveSpendResult.INSUFFICIENT_POINTS

        allocatedNodes += nodeId
        spentPassivePoints += node.cost
        revision++
        return PassiveSpendResult.ACCEPTED
    }

    fun has(nodeId: String): Boolean = nodeId in allocatedNodes

    fun effects(): ProgressionEffects {
        var directDamageBonus = 0.0
        var attackSpeedBonus = 0.0
        var cooldownRecoveryBonus = 0.0
        var maxHealthBonus = 0
        var incomingDamageReduction = 0.0
        allocatedNodes.forEach { nodeId ->
            val node = requireNotNull(GlobalPassiveTree.node(nodeId))
            directDamageBonus += node.directDamageBonus
            attackSpeedBonus += node.attackSpeedBonus
            cooldownRecoveryBonus += node.cooldownRecoveryBonus
            maxHealthBonus += node.maxHealthBonus
            incomingDamageReduction += node.incomingDamageReduction
        }
        return ProgressionEffects(
            directDamageMultiplier = 1.0 + directDamageBonus,
            normalAttackSpeedMultiplier = 1.0 + attackSpeedBonus,
            cooldownRecoveryMultiplier = 1.0 + cooldownRecoveryBonus,
            maxHealthBonus = maxHealthBonus,
            incomingPveDamageMultiplier = (1.0 - incomingDamageReduction).coerceAtLeast(0.0),
        )
    }

    fun record(): ProgressionRecord = ProgressionRecord(
        level = level,
        experience = experience,
        grantedPassivePoints = grantedPassivePoints,
        spentPassivePoints = spentPassivePoints,
        allocatedPassiveNodeIds = allocatedNodes.sorted(),
        revision = revision,
    )

    fun copyState(): ProgressionState = fromRecord(record())

    companion object {
        const val MAX_LEVEL = 45

        fun xpRequired(level: Int): Int {
            require(level in 1..MAX_LEVEL) { "Level must be between 1 and $MAX_LEVEL" }
            return 100 + (level - 1) * 50
        }

        fun fromRecord(record: ProgressionRecord): ProgressionState = ProgressionState(
            level = record.level,
            experience = record.experience,
            grantedPassivePoints = record.grantedPassivePoints,
            spentPassivePoints = record.spentPassivePoints,
            allocatedPassiveNodeIds = record.allocatedPassiveNodeIds,
            revision = record.revision,
        )
    }
}
