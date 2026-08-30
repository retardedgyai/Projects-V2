package dev.projects.server.experiment.swarm.loop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface SnapshotLoadResult {
    data object Missing : SnapshotLoadResult
    data class Loaded(val snapshot: SwarmPlayerSnapshot) : SnapshotLoadResult
    data class Invalid(val reason: String) : SnapshotLoadResult
}

interface SwarmSnapshotStore {
    fun load(playerId: UUID): SnapshotLoadResult
    fun save(playerId: UUID, snapshot: SwarmPlayerSnapshot): Boolean
}

class FileSwarmSnapshotStore(
    private val directory: Path,
) : SwarmSnapshotStore {
    private val blockedPlayers = ConcurrentHashMap.newKeySet<UUID>()

    override fun load(playerId: UUID): SnapshotLoadResult {
        val file = fileFor(playerId)
        if (!Files.isRegularFile(file)) return SnapshotLoadResult.Missing
        return runCatching {
            require(Files.size(file) <= MAX_FILE_BYTES) { "Snapshot file is too large" }
            decode(Files.readString(file))
        }.fold(
            onSuccess = { SnapshotLoadResult.Loaded(it) },
            onFailure = { error ->
                blockedPlayers += playerId
                SnapshotLoadResult.Invalid(error.message ?: "Malformed Tidebreak snapshot")
            },
        )
    }

    override fun save(playerId: UUID, snapshot: SwarmPlayerSnapshot): Boolean {
        if (playerId in blockedPlayers) return false
        return runCatching {
            Files.createDirectories(directory)
            val file = fileFor(playerId)
            val temporary = directory.resolve(".${file.fileName}.${UUID.randomUUID()}.tmp")
            try {
                Files.writeString(temporary, encode(snapshot))
                Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(temporary)
            }
            true
        }.getOrDefault(false)
    }

    fun isBlocked(playerId: UUID): Boolean = playerId in blockedPlayers

    private fun fileFor(playerId: UUID): Path = directory.resolve("$playerId.json")

    private fun decode(raw: String): SwarmPlayerSnapshot {
        val matches = FIELD.findAll(raw).toList()
        require("{" + matches.joinToString(",") { it.value } + "}" == raw) { "Malformed snapshot envelope" }
        val fields = matches.associate { it.groupValues[1] to it.groupValues[2] }
        require(fields.size == matches.size) { "Duplicate snapshot key" }
        val schemaVersion = fields.getValue("schemaVersion").toInt()
        val expectedKeys = when (schemaVersion) {
            LEGACY_SCHEMA_VERSION -> LEGACY_KEYS
            SCHEMA_VERSION -> EXPECTED_KEYS
            else -> error("Unsupported snapshot schema")
        }
        require(fields.keys == expectedKeys) { "Unknown or missing snapshot key" }

        val fittingState = fields.enum<FittingState>("fittingState")
        val legacyTraining = if (
            fittingState == FittingState.CATALYST_READY ||
            fittingState == FittingState.MOD_BARBED ||
            fittingState == FittingState.MOD_GUARD
        ) {
            SwarmPlayerSnapshot.BRINECLAW_TRAINING_OBJECTIVE
        } else {
            0
        }
        val legacyGathererMask = if (schemaVersion == LEGACY_SCHEMA_VERSION) {
            (1 shl fields.int("gathererOreEarned")) - 1
        } else {
            fields.int("gathererOreNodeMask")
        }

        return SwarmPlayerSnapshot(
            revision = fields.long("revision"),
            initialGrantClaimed = fields.boolean("initialGrantClaimed"),
            scrip = fields.int("scrip"),
            ore = fields.int("ore"),
            cord = fields.int("cord"),
            selectedRoute = fields.enumOrNull<ProcurementRoute>("selectedRoute"),
            hunterCordEarned = fields.int("hunterCordEarned"),
            brineclawTrainingDefeats = if (schemaVersion == LEGACY_SCHEMA_VERSION) {
                legacyTraining
            } else {
                fields.int("brineclawTrainingDefeats")
            },
            gathererOreEarned = fields.int("gathererOreEarned"),
            gathererOreNodeMask = legacyGathererMask,
            supplierRecordMask = fields.int("supplierRecordMask"),
            supplierCommissionClaimed = fields.boolean("supplierCommissionClaimed"),
            fittingState = fittingState,
            questStage = fields.enumOrNull<QuestStage>("questStage"),
            firstClearClaimed = fields.boolean("firstClearClaimed"),
        )
    }

    private fun encode(snapshot: SwarmPlayerSnapshot): String = buildString {
        append('{')
        append("\"schemaVersion\":").append(SCHEMA_VERSION)
        append(",\"revision\":").append(snapshot.revision)
        append(",\"initialGrantClaimed\":").append(snapshot.initialGrantClaimed)
        append(",\"scrip\":").append(snapshot.scrip)
        append(",\"ore\":").append(snapshot.ore)
        append(",\"cord\":").append(snapshot.cord)
        append(",\"selectedRoute\":").append(snapshot.selectedRoute.json())
        append(",\"hunterCordEarned\":").append(snapshot.hunterCordEarned)
        append(",\"brineclawTrainingDefeats\":").append(snapshot.brineclawTrainingDefeats)
        append(",\"gathererOreEarned\":").append(snapshot.gathererOreEarned)
        append(",\"gathererOreNodeMask\":").append(snapshot.gathererOreNodeMask)
        append(",\"supplierRecordMask\":").append(snapshot.supplierRecordMask)
        append(",\"supplierCommissionClaimed\":").append(snapshot.supplierCommissionClaimed)
        append(",\"fittingState\":").append(snapshot.fittingState.json())
        append(",\"questStage\":").append(snapshot.questStage.json())
        append(",\"firstClearClaimed\":").append(snapshot.firstClearClaimed)
        append('}')
    }

    private fun String?.json(): String = this?.let { "\"$it\"" } ?: "null"
    private fun Enum<*>?.json(): String = this?.name.json()

    private fun Map<String, String>.int(key: String): Int = getValue(key).toInt()
    private fun Map<String, String>.long(key: String): Long = getValue(key).toLong()
    private fun Map<String, String>.boolean(key: String): Boolean = when (val value = getValue(key)) {
        "true" -> true
        "false" -> false
        else -> error("Invalid boolean for $key: $value")
    }

    private inline fun <reified T : Enum<T>> Map<String, String>.enum(key: String): T =
        enumValueOf(unquote(getValue(key)))

    private inline fun <reified T : Enum<T>> Map<String, String>.enumOrNull(key: String): T? =
        getValue(key).takeUnless { it == "null" }?.let { enumValueOf(unquote(it)) }

    private fun unquote(value: String): String {
        require(value.length >= 2 && value.first() == '"' && value.last() == '"') { "Invalid enum value" }
        return value.substring(1, value.length - 1)
    }

    companion object {
        const val LEGACY_SCHEMA_VERSION = 1
        const val SCHEMA_VERSION = 2
        const val MAX_FILE_BYTES = 4096L
        val EXPERIMENT_RELATIVE_PATH: Path = Path.of("experiments", "swarm-mmo-vslice-20260830", "players")

        private val EXPECTED_KEYS = linkedSetOf(
            "schemaVersion",
            "revision",
            "initialGrantClaimed",
            "scrip",
            "ore",
            "cord",
            "selectedRoute",
            "hunterCordEarned",
            "brineclawTrainingDefeats",
            "gathererOreEarned",
            "gathererOreNodeMask",
            "supplierRecordMask",
            "supplierCommissionClaimed",
            "fittingState",
            "questStage",
            "firstClearClaimed",
        )
        private val LEGACY_KEYS = EXPECTED_KEYS.filterTo(linkedSetOf()) {
            it != "brineclawTrainingDefeats" && it != "gathererOreNodeMask"
        }
        private val FIELD = Regex("\\\"([A-Za-z][A-Za-z0-9]*)\\\":(null|true|false|-?\\d+|\\\"[A-Z_]+\\\")")
    }
}
