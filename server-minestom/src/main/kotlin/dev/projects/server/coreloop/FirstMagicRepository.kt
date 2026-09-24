package dev.projects.server.coreloop

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID

/** The slice owns a sidecar save; it never changes the shared core account format. */
internal class FirstMagicRepository(private val directory: Path) {
    fun load(playerId: UUID): FirstMagicState {
        val path = file(playerId)
        if (!Files.exists(path)) return FirstMagicState()
        require(Files.isRegularFile(path) && Files.size(path) <= 4096) { "Invalid first-magic save" }
        val lines = Files.readAllLines(path, UTF_8)
        require(lines.size == 6 && lines[0] == "FIRST_MAGIC_V1" && lines[1] == playerId.toString()) { "Unknown first-magic save" }
        val flags = lines[2].split(',')
        require(flags.size == 3 && flags.all { it == "0" || it == "1" })
        val counts = lines[3].split(',').map(String::toInt)
        val studied = lines[4].split(',')
        val jars = lines[5].split(',').map(String::toInt)
        require(counts.size == AnomalousMaterial.entries.size && counts.all { it in 0..FirstMagicRules.MATERIAL_CAPACITY })
        require(studied.size == AnomalousMaterial.entries.size && studied.all { it == "0" || it == "1" })
        require(jars.size == FirstAspect.entries.size && jars.all { it in 0..FirstMagicRules.JAR_CAPACITY })
        val state = FirstMagicState(
            AnomalousMaterial.entries.zip(counts).filter { it.second > 0 }.toMap(),
            AnomalousMaterial.entries.zip(studied).filter { it.second == "1" }.map { it.first }.toSet(),
            FirstAspect.entries.zip(jars).filter { it.second > 0 }.toMap(),
            flags[0] == "1", flags[1] == "1", flags[2] == "1",
        )
        require(!state.deskRestored || state.reacted)
        require(!state.firstDistillation || state.deskRestored)
        require(state.studied.isEmpty() || state.deskRestored)
        return state
    }

    fun save(playerId: UUID, state: FirstMagicState) {
        Files.createDirectories(directory)
        val content = listOf(
            "FIRST_MAGIC_V1", playerId.toString(),
            listOf(state.reacted, state.deskRestored, state.firstDistillation).joinToString(",") { if (it) "1" else "0" },
            AnomalousMaterial.entries.joinToString(",") { state.count(it).toString() },
            AnomalousMaterial.entries.joinToString(",") { if (it in state.studied) "1" else "0" },
            FirstAspect.entries.joinToString(",") { state.jar(it).toString() },
        ).joinToString("\n", postfix = "\n")
        val temporary = Files.createTempFile(directory, ".$playerId.", ".tmp")
        try {
            Files.writeString(temporary, content, UTF_8)
            Files.move(temporary, file(playerId), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun file(playerId: UUID) = directory.resolve("$playerId.magic")
}
