package dev.projects.server.questmap

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID
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
    COMMON("Common", 1, 3),
    BOUNTIFUL("Bountiful", 2, 5),
    RARE("Rare", 1, 8),
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

internal data class QuestGatheringMastery(
    private val experienceByDiscipline: Map<QuestGatheringDiscipline, Int> = emptyMap(),
) {
    fun experience(discipline: QuestGatheringDiscipline): Int = experienceByDiscipline[discipline] ?: 0

    fun level(discipline: QuestGatheringDiscipline): Int = sqrt(experience(discipline) / 6.0).toInt()

    fun harvestTicks(discipline: QuestGatheringDiscipline): Int =
        (BASE_HARVEST_TICKS - level(discipline) * 2).coerceAtLeast(MINIMUM_HARVEST_TICKS)

    fun yieldAmount(discipline: QuestGatheringDiscipline, quality: QuestGatheringQuality): Int =
        BASE_YIELD * quality.yieldMultiplier + level(discipline) / 5

    fun addExperience(discipline: QuestGatheringDiscipline, amount: Int): QuestGatheringMastery {
        require(amount >= 0)
        return copy(experienceByDiscipline = experienceByDiscipline + (discipline to experience(discipline) + amount))
    }

    fun asMap(): Map<QuestGatheringDiscipline, Int> = QuestGatheringDiscipline.entries.associateWith(::experience)

    companion object {
        const val BASE_HARVEST_TICKS = 50
        const val MINIMUM_HARVEST_TICKS = 24
        const val BASE_YIELD = 2

        fun fromMap(values: Map<QuestGatheringDiscipline, Int>): QuestGatheringMastery {
            require(values.values.all { it >= 0 })
            return QuestGatheringMastery(values.filterValues { it > 0 })
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
        val fields = ENVELOPE.matchEntire(raw)?.groupValues ?: error("Malformed gathering mastery envelope")
        require(fields[1].toInt() == SCHEMA_VERSION) { "Unsupported gathering mastery schema version: ${fields[1]}" }
        val values = QuestGatheringDiscipline.entries.mapIndexed { index, discipline ->
            discipline to fields[index + 2].toInt().also { require(it >= 0) { "Negative gathering mastery" } }
        }.toMap()
        return QuestGatheringMastery.fromMap(values)
    }

    private fun encode(mastery: QuestGatheringMastery): String = buildString {
        append("{\"schemaVersion\":").append(SCHEMA_VERSION)
        QuestGatheringDiscipline.entries.forEach { discipline ->
            append(",\"").append(discipline.id).append("\":").append(mastery.experience(discipline))
        }
        append('}')
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_FILE_BYTES = 4 * 1024L
        val ENVELOPE = Regex(
            """\A\{"schemaVersion":(-?\d+),"skinning":(-?\d+),"woodcutting":(-?\d+),"quarrying":(-?\d+),"mining":(-?\d+),"herbalism":(-?\d+)\}\z""",
        )
    }
}
