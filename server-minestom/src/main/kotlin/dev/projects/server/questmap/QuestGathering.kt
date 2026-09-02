package dev.projects.server.questmap

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

internal val QUEST_GATHERING_TOOL_TAG: Tag<String> = Tag.String("projects_gathering_tool")

internal enum class QuestGatheringDiscipline(
    val id: String,
    val displayName: String,
    val toolName: String,
    val toolMaterial: Material,
    val nodeBlock: Block,
    val commonMaterial: Material,
    val commonResourceName: String,
    val rareMaterial: Material,
    val rareResourceName: String,
) {
    SKINNING(
        id = "skinning",
        displayName = "Skinning",
        toolName = "Skinning Knife",
        toolMaterial = Material.FLINT,
        nodeBlock = Block.BONE_BLOCK,
        commonMaterial = Material.LEATHER,
        commonResourceName = "Field Hide",
        rareMaterial = Material.RABBIT_FOOT,
        rareResourceName = "Pristine Hide",
    ),
    WOODCUTTING(
        id = "woodcutting",
        displayName = "Woodcutting",
        toolName = "Woodcutter's Axe",
        toolMaterial = Material.STONE_AXE,
        nodeBlock = Block.OAK_LOG,
        commonMaterial = Material.OAK_LOG,
        commonResourceName = "Heartwood",
        rareMaterial = Material.HONEYCOMB,
        rareResourceName = "Ancient Resin",
    ),
    QUARRYING(
        id = "quarrying",
        displayName = "Quarrying",
        toolName = "Stone Hammer",
        toolMaterial = Material.IRON_PICKAXE,
        nodeBlock = Block.ANDESITE,
        commonMaterial = Material.COBBLESTONE,
        commonResourceName = "Dense Stone",
        rareMaterial = Material.AMETHYST_SHARD,
        rareResourceName = "Resonant Crystal",
    ),
    MINING(
        id = "mining",
        displayName = "Mining",
        toolName = "Miner's Pickaxe",
        toolMaterial = Material.STONE_PICKAXE,
        nodeBlock = Block.COPPER_ORE,
        commonMaterial = Material.RAW_COPPER,
        commonResourceName = "Raw Ore",
        rareMaterial = Material.DIAMOND,
        rareResourceName = "Rare Gem",
    ),
    HERBALISM(
        id = "herbalism",
        displayName = "Fiber Gathering",
        toolName = "Gatherer's Sickle",
        toolMaterial = Material.IRON_HOE,
        nodeBlock = Block.MOSS_BLOCK,
        commonMaterial = Material.STRING,
        commonResourceName = "Plant Fiber",
        rareMaterial = Material.GLOW_BERRIES,
        rareResourceName = "Luminous Fiber",
    ),
    ;

    fun toolItem(): ItemStack = ItemStack.builder(toolMaterial)
        .customName(Component.text(toolName, NamedTextColor.GOLD))
        .build()
        .withTag(QUEST_GATHERING_TOOL_TAG, id)

    fun accepts(item: ItemStack): Boolean = item.getTag(QUEST_GATHERING_TOOL_TAG) == id

    companion object {
        fun forGatheringOrdinal(ordinal: Int): QuestGatheringDiscipline = entries[Math.floorMod(ordinal, entries.size)]
    }
}

internal enum class QuestGatheringQuality(
    val displayName: String,
    val yieldMultiplier: Int,
    val masteryExperience: Int,
) {
    COMMON("Common", 1, 1),
    BOUNTIFUL("Bountiful", 2, 2),
    RARE("Rare", 1, 3),
}

internal enum class QuestGatheringMasteryNode(
    val id: String,
    val displayName: String,
    val description: String,
    val cost: Int = 1,
    val prerequisite: QuestGatheringMasteryNode? = null,
    val keystone: Boolean = false,
) {
    STEADY_HANDS(
        id = "steady_hands",
        displayName = "Steady Hands",
        description = "Gather 4 ticks faster.",
    ),
    DEEP_YIELD(
        id = "deep_yield",
        displayName = "Deep Yield",
        description = "+1 common resource from every node.",
        prerequisite = STEADY_HANDS,
    ),
    KEEN_SENSES(
        id = "keen_senses",
        displayName = "Keen Senses",
        description = "+8% chance to discover the rare resource.",
    ),
    FORTUNE_SEEKER(
        id = "fortune_seeker",
        displayName = "Fortune Seeker",
        description = "+12% rare discovery chance.",
        prerequisite = KEEN_SENSES,
    ),
    ABUNDANCE_KEYSTONE(
        id = "abundance_keystone",
        displayName = "Abundance",
        description = "+2 common resources, but gathering takes 8 ticks longer.",
        cost = 2,
        prerequisite = DEEP_YIELD,
        keystone = true,
    ),
    DISCOVERY_KEYSTONE(
        id = "discovery_keystone",
        displayName = "Discovery",
        description = "+20% rare discovery chance, but -1 common resource.",
        cost = 2,
        prerequisite = FORTUNE_SEEKER,
        keystone = true,
    ),
    ;

    companion object {
        fun byId(id: String): QuestGatheringMasteryNode? = entries.firstOrNull { it.id == id }
    }
}

internal data class QuestGatheringNode(
    val id: Int,
    val contentPosition: QuestMapPoint,
    val blockPosition: BlockVec,
    val discipline: QuestGatheringDiscipline,
    val quality: QuestGatheringQuality,
)

internal fun questGatheringNodes(plan: QuestMapPlan): List<QuestGatheringNode> = plan.contents
    .filter { it.kind == QuestMapContentKind.GATHERING }
    .mapIndexed { ordinal, content ->
        val discipline = QuestGatheringDiscipline.forGatheringOrdinal(ordinal)
        val routeAnchor = plan.mainRoute[content.mainRouteIndex]
        val outwardX = (content.position.x - routeAnchor.x).coerceIn(-1, 1)
        val outwardZ = (content.position.z - routeAnchor.z).coerceIn(-1, 1)
        val resourcePoint = QuestMapPoint(
            (content.position.x + outwardX * 4).coerceIn(2, plan.size - 3),
            (content.position.z + outwardZ * 4).coerceIn(2, plan.size - 3),
        )
        val roll = Math.floorMod(plan.seed xor (ordinal * 2_654_435_761L), 100L).toInt()
        val quality = when {
            roll < 6 -> QuestGatheringQuality.RARE
            roll < 28 -> QuestGatheringQuality.BOUNTIFUL
            else -> QuestGatheringQuality.COMMON
        }
        QuestGatheringNode(
            id = ordinal,
            contentPosition = content.position,
            blockPosition = BlockVec(resourcePoint.x, plan.heightAt(resourcePoint) + 1, resourcePoint.z),
            discipline = discipline,
            quality = quality,
        )
    }

internal fun gatheringProgressDisplayPosition(
    playerPosition: Pos,
    playerEyeHeight: Double,
    blockPosition: BlockVec,
): Pos {
    val center = Vec(
        blockPosition.blockX() + 0.5,
        blockPosition.blockY() + 0.5,
        blockPosition.blockZ() + 0.5,
    )
    val eye = Vec(playerPosition.x(), playerPosition.y() + playerEyeHeight, playerPosition.z())
    val towardEye = eye.sub(center)
    val length = sqrt(
        towardEye.x() * towardEye.x() + towardEye.y() * towardEye.y() + towardEye.z() * towardEye.z(),
    )
    val direction = if (length < 0.0001) Vec(0.0, 0.0, 1.0) else towardEye.div(length)
    val dominantAxis = max(abs(direction.x()), max(abs(direction.y()), abs(direction.z()))).coerceAtLeast(0.0001)
    val distanceToFace = 0.52 / dominantAxis
    val anchor = center.add(direction.mul(distanceToFace + 0.10))
    return Pos(anchor.x(), anchor.y(), anchor.z())
}

internal sealed interface QuestGatheringMasteryUnlockResult {
    data class Unlocked(val mastery: QuestGatheringMastery) : QuestGatheringMasteryUnlockResult
    data object AlreadyUnlocked : QuestGatheringMasteryUnlockResult
    data object MissingPrerequisite : QuestGatheringMasteryUnlockResult
    data object KeystoneConflict : QuestGatheringMasteryUnlockResult
    data object NotEnoughPoints : QuestGatheringMasteryUnlockResult
}

internal data class QuestGatheringMastery(
    private val experienceByDiscipline: Map<QuestGatheringDiscipline, Int> = emptyMap(),
    private val unlockedByDiscipline: Map<QuestGatheringDiscipline, Set<QuestGatheringMasteryNode>> = emptyMap(),
) {
    fun experience(discipline: QuestGatheringDiscipline): Int = experienceByDiscipline[discipline] ?: 0

    fun level(discipline: QuestGatheringDiscipline): Int = sqrt(experience(discipline) / 10.0).toInt()

    fun unlockedNodes(discipline: QuestGatheringDiscipline): Set<QuestGatheringMasteryNode> =
        unlockedByDiscipline[discipline].orEmpty()

    fun earnedTreePoints(discipline: QuestGatheringDiscipline): Int = level(discipline) / 3

    fun spentTreePoints(discipline: QuestGatheringDiscipline): Int = unlockedNodes(discipline).sumOf { it.cost }

    fun availableTreePoints(discipline: QuestGatheringDiscipline): Int =
        earnedTreePoints(discipline) - spentTreePoints(discipline)

    fun harvestTicks(discipline: QuestGatheringDiscipline): Int {
        val unlocked = unlockedNodes(discipline)
        val steadyHandsReduction = if (QuestGatheringMasteryNode.STEADY_HANDS in unlocked) 4 else 0
        val abundancePenalty = if (QuestGatheringMasteryNode.ABUNDANCE_KEYSTONE in unlocked) 8 else 0
        return (BASE_HARVEST_TICKS - level(discipline) / 2 - steadyHandsReduction + abundancePenalty)
            .coerceAtLeast(MINIMUM_HARVEST_TICKS)
    }

    fun yieldAmount(discipline: QuestGatheringDiscipline, quality: QuestGatheringQuality): Int {
        val unlocked = unlockedNodes(discipline)
        val deepYieldBonus = if (QuestGatheringMasteryNode.DEEP_YIELD in unlocked) 1 else 0
        val abundanceBonus = if (QuestGatheringMasteryNode.ABUNDANCE_KEYSTONE in unlocked) 2 else 0
        val discoveryPenalty = if (QuestGatheringMasteryNode.DISCOVERY_KEYSTONE in unlocked) 1 else 0
        return (BASE_YIELD * quality.yieldMultiplier + level(discipline) / 10 + deepYieldBonus + abundanceBonus - discoveryPenalty)
            .coerceAtLeast(1)
    }

    fun rareDiscoveryChancePercent(discipline: QuestGatheringDiscipline): Int {
        val unlocked = unlockedNodes(discipline)
        return (if (QuestGatheringMasteryNode.KEEN_SENSES in unlocked) 8 else 0) +
            (if (QuestGatheringMasteryNode.FORTUNE_SEEKER in unlocked) 12 else 0) +
            (if (QuestGatheringMasteryNode.DISCOVERY_KEYSTONE in unlocked) 20 else 0)
    }

    fun unlock(
        discipline: QuestGatheringDiscipline,
        node: QuestGatheringMasteryNode,
    ): QuestGatheringMasteryUnlockResult {
        val unlocked = unlockedNodes(discipline)
        if (node in unlocked) return QuestGatheringMasteryUnlockResult.AlreadyUnlocked
        if (node.keystone && unlocked.any { it.keystone }) return QuestGatheringMasteryUnlockResult.KeystoneConflict
        if (node.prerequisite != null && node.prerequisite !in unlocked) {
            return QuestGatheringMasteryUnlockResult.MissingPrerequisite
        }
        if (availableTreePoints(discipline) < node.cost) return QuestGatheringMasteryUnlockResult.NotEnoughPoints
        return QuestGatheringMasteryUnlockResult.Unlocked(
            copy(unlockedByDiscipline = unlockedByDiscipline + (discipline to (unlocked + node))),
        )
    }

    fun addExperience(discipline: QuestGatheringDiscipline, amount: Int): QuestGatheringMastery {
        require(amount >= 0)
        return copy(experienceByDiscipline = experienceByDiscipline + (discipline to experience(discipline) + amount))
    }

    fun asMap(): Map<QuestGatheringDiscipline, Int> = QuestGatheringDiscipline.entries.associateWith(::experience)

    fun asTreeMap(): Map<QuestGatheringDiscipline, Set<QuestGatheringMasteryNode>> =
        QuestGatheringDiscipline.entries.associateWith(::unlockedNodes)

    companion object {
        const val BASE_HARVEST_TICKS = 50
        const val MINIMUM_HARVEST_TICKS = 24
        const val BASE_YIELD = 2

        fun fromMap(
            values: Map<QuestGatheringDiscipline, Int>,
            unlocked: Map<QuestGatheringDiscipline, Set<QuestGatheringMasteryNode>> = emptyMap(),
        ): QuestGatheringMastery {
            require(values.values.all { it >= 0 })
            val mastery = QuestGatheringMastery(
                values.filterValues { it > 0 },
                unlocked.mapValues { it.value.toSet() }.filterValues { it.isNotEmpty() },
            )
            QuestGatheringDiscipline.entries.forEach { discipline ->
                val nodes = mastery.unlockedNodes(discipline)
                require(nodes.count { it.keystone } <= 1) { "Conflicting gathering keystones" }
                require(nodes.all { it.prerequisite == null || it.prerequisite in nodes }) {
                    "Gathering mastery prerequisite is missing"
                }
                require(mastery.spentTreePoints(discipline) <= mastery.earnedTreePoints(discipline)) {
                    "Gathering mastery tree overspent"
                }
            }
            return mastery
        }
    }
}

internal sealed interface QuestGatheringMasteryLoadResult {
    data object Missing : QuestGatheringMasteryLoadResult
    data class Loaded(val mastery: QuestGatheringMastery) : QuestGatheringMasteryLoadResult
    data class Invalid(val reason: String) : QuestGatheringMasteryLoadResult
}

internal class QuestGatheringMasteryRepository(private val directory: Path) {
    private val blockedPlayers = mutableSetOf<UUID>()

    fun load(playerId: UUID): QuestGatheringMasteryLoadResult {
        val file = fileFor(playerId)
        if (!Files.isRegularFile(file)) return QuestGatheringMasteryLoadResult.Missing
        return runCatching {
            require(Files.size(file) <= MAX_FILE_BYTES) { "Gathering mastery file is too large" }
            parse(Files.readString(file))
        }.fold(
            onSuccess = { QuestGatheringMasteryLoadResult.Loaded(it) },
            onFailure = { error ->
                blockedPlayers += playerId
                QuestGatheringMasteryLoadResult.Invalid(error.message ?: "Malformed gathering mastery file")
            },
        )
    }

    fun save(playerId: UUID, mastery: QuestGatheringMastery): Boolean {
        if (playerId in blockedPlayers) return false
        return runCatching {
            Files.createDirectories(directory)
            val file = fileFor(playerId)
            val temporary = directory.resolve(".${file.fileName}.${UUID.randomUUID()}.tmp")
            try {
                Files.writeString(temporary, encode(mastery))
                try {
                    Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(temporary, file, REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
            true
        }.getOrDefault(false)
    }

    private fun fileFor(playerId: UUID): Path = directory.resolve("$playerId.json")

    private fun parse(raw: String): QuestGatheringMastery {
        LEGACY_ENVELOPE.matchEntire(raw)?.groupValues?.let { fields ->
            val values = QuestGatheringDiscipline.entries.mapIndexed { index, discipline ->
                discipline to fields[index + 1].toInt().also { require(it >= 0) { "Negative gathering mastery" } }
            }.toMap()
            return QuestGatheringMastery.fromMap(values)
        }
        val fields = ENVELOPE.matchEntire(raw)?.groupValues ?: error("Malformed gathering mastery envelope")
        val values = QuestGatheringDiscipline.entries.mapIndexed { index, discipline ->
            discipline to fields[index + 1].toInt().also { require(it >= 0) { "Negative gathering mastery" } }
        }.toMap()
        val treeOffset = 1 + QuestGatheringDiscipline.entries.size
        val unlocked = QuestGatheringDiscipline.entries.mapIndexed { index, discipline ->
            discipline to fields[treeOffset + index]
                .split('|')
                .filter { it.isNotEmpty() }
                .map { QuestGatheringMasteryNode.byId(it) ?: error("Unknown gathering mastery node: $it") }
                .toSet()
        }.toMap()
        return QuestGatheringMastery.fromMap(values, unlocked)
    }

    private fun encode(mastery: QuestGatheringMastery): String = buildString {
        append("{\"schemaVersion\":").append(SCHEMA_VERSION)
        QuestGatheringDiscipline.entries.forEach { discipline ->
            append(",\"").append(discipline.id).append("\":").append(mastery.experience(discipline))
        }
        QuestGatheringDiscipline.entries.forEach { discipline ->
            append(",\"").append(discipline.id).append("Tree\":\"")
            append(mastery.unlockedNodes(discipline).sortedBy { it.ordinal }.joinToString("|") { it.id })
            append('"')
        }
        append('}')
    }

    private companion object {
        const val SCHEMA_VERSION = 2
        const val MAX_FILE_BYTES = 8 * 1024L
        val LEGACY_ENVELOPE = Regex(
            """\A\{"schemaVersion":1,"skinning":(-?\d+),"woodcutting":(-?\d+),"quarrying":(-?\d+),"mining":(-?\d+),"herbalism":(-?\d+)\}\z""",
        )
        val ENVELOPE = Regex(
            """\A\{"schemaVersion":2,"skinning":(-?\d+),"woodcutting":(-?\d+),"quarrying":(-?\d+),"mining":(-?\d+),"herbalism":(-?\d+),"skinningTree":"([a-z_|]*)","woodcuttingTree":"([a-z_|]*)","quarryingTree":"([a-z_|]*)","miningTree":"([a-z_|]*)","herbalismTree":"([a-z_|]*)"\}\z""",
        )
    }
}
