package dev.projects.server

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID

sealed interface ProgressionLoadResult {
    data object Missing : ProgressionLoadResult
    data class Loaded(val state: ProgressionState) : ProgressionLoadResult
    data class Invalid(val reason: String) : ProgressionLoadResult
}

/** Small, progression-specific v1 repository. Invalid files are blocked from automatic overwrite. */
class ProgressionRepository(private val directory: Path) {
    private val blockedPlayers = mutableSetOf<UUID>()

    fun load(playerId: UUID): ProgressionLoadResult {
        val file = fileFor(playerId)
        if (!Files.isRegularFile(file)) return ProgressionLoadResult.Missing
        return runCatching {
            require(Files.size(file) <= MAX_FILE_BYTES) { "Progression file is too large" }
            parse(Files.readString(file))
        }.fold(
            onSuccess = { ProgressionLoadResult.Loaded(it) },
            onFailure = { error ->
                blockedPlayers += playerId
                ProgressionLoadResult.Invalid(error.message ?: "Malformed progression file")
            },
        )
    }

    fun save(playerId: UUID, state: ProgressionState): Boolean {
        if (playerId in blockedPlayers) return false
        return runCatching {
            Files.createDirectories(directory)
            val file = fileFor(playerId)
            val temporary = directory.resolve(".${file.fileName}.${UUID.randomUUID()}.tmp")
            try {
                Files.writeString(temporary, encode(state.record()))
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

    fun isBlocked(playerId: UUID): Boolean = playerId in blockedPlayers

    private fun fileFor(playerId: UUID): Path = directory.resolve("$playerId.json")

    private fun parse(raw: String): ProgressionState {
        val fields = ENVELOPE.find(raw)?.groupValues
            ?: error("Malformed progression envelope")
        val schemaVersion = fields[1].toInt()
        require(schemaVersion == SCHEMA_VERSION) { "Unsupported progression schema version: $schemaVersion" }
        val allocatedField = fields[6]
        val nodeIds = if (allocatedField.isBlank()) {
            emptyList()
        } else {
            val matches = NODE_ID.findAll(allocatedField).toList()
            require(matches.joinToString(",") { it.value } == allocatedField) {
                "Malformed allocated passive node list"
            }
            matches.map { it.groupValues[1] }
        }
        return ProgressionState(
            level = fields[2].toInt(),
            experience = fields[3].toInt(),
            grantedPassivePoints = fields[4].toInt(),
            spentPassivePoints = fields[5].toInt(),
            allocatedPassiveNodeIds = nodeIds,
            revision = fields[7].toLong(),
        )
    }

    private fun encode(record: ProgressionRecord): String = buildString {
        append("{\"schemaVersion\":").append(SCHEMA_VERSION)
            .append(",\"level\":").append(record.level)
            .append(",\"experience\":").append(record.experience)
            .append(",\"grantedPassivePoints\":").append(record.grantedPassivePoints)
            .append(",\"spentPassivePoints\":").append(record.spentPassivePoints)
            .append(",\"allocatedPassiveNodeIds\":[")
        record.allocatedPassiveNodeIds.forEachIndexed { index, nodeId ->
            if (index > 0) append(',')
            append('"').append(nodeId).append('"')
        }
        append("],\"revision\":").append(record.revision).append('}')
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_FILE_BYTES = 16 * 1024L
        val ENVELOPE = Regex(
            """\A\{"schemaVersion":(-?\d+),"level":(-?\d+),"experience":(-?\d+),"grantedPassivePoints":(-?\d+),"spentPassivePoints":(-?\d+),"allocatedPassiveNodeIds":\[(.*)],"revision":(-?\d+)\}\z""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        val NODE_ID = Regex("\"([a-z0-9][a-z0-9:_/-]*)\"")
    }
}
