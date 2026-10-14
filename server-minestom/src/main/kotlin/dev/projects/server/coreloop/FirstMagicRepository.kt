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
        require(Files.isRegularFile(path) && Files.size(path) <= 16384) { "Invalid first-magic save" }
        val lines = Files.readAllLines(path, UTF_8)
        val legacy = lines.firstOrNull() == "FIRST_MAGIC_V1"
        require(lines.size == if (legacy) 6 else 10)
        require(lines[0] == if (legacy) "FIRST_MAGIC_V1" else "FIRST_MAGIC_V2") { "Unknown first-magic save" }
        require(lines[1] == playerId.toString()) { "Wrong first-magic save owner" }
        val flags = lines[2].split(',')
        require(flags.size == 3 && flags.all { it == "0" || it == "1" })
        val counts = lines[3].split(',').map(String::toInt)
        val studied = lines[4].split(',')
        val jars = lines[5].split(',').map(String::toInt)
        require(counts.size == AnomalousMaterial.entries.size && counts.all { it in 0..FirstMagicRules.MATERIAL_CAPACITY })
        require(studied.size == AnomalousMaterial.entries.size && studied.all { it == "0" || it == "1" })
        require(jars.size == FirstAspect.entries.size && jars.all { it in 0..FirstMagicRules.JAR_CAPACITY })
        val studiedMaterials = AnomalousMaterial.entries.zip(studied).filter { it.second == "1" }.map { it.first }.toSet()
        val discovered = if (legacy) studiedMaterials.flatMap(AnomalousMaterial::researchAspects).toSet()
            else lines[6].split(',').filter(String::isNotEmpty).toSet()
        require(discovered.all(AspectCatalog.byId::containsKey))
        val ink = if (legacy) discovered.associateWith { 8 } else parseInk(lines[7])
        val placements = if (legacy) emptyMap() else parsePlacements(lines[8])
        val unlocked = if (legacy) emptySet() else lines[9].split(',').filter(String::isNotEmpty).toSet()
        require(unlocked.all(ResearchCatalog.byId::containsKey))
        require(ink.keys.all(discovered::contains))
        require(placements.keys.all(ResearchCatalog.byId::containsKey))
        require(placements.values.all { board -> board.keys.all(ResearchBoard.cells::containsKey) && board.values.all(discovered::contains) })
        require(unlocked.all { id -> ResearchBoard.complete(ResearchCatalog.byId.getValue(id), placements[id].orEmpty()) })
        val state = FirstMagicState(
            AnomalousMaterial.entries.zip(counts).filter { it.second > 0 }.toMap(),
            studiedMaterials,
            FirstAspect.entries.zip(jars).filter { it.second > 0 }.toMap(),
            flags[0] == "1", flags[1] == "1", flags[2] == "1",
            discovered, ink, placements, unlocked,
        )
        require(!state.deskRestored || state.reacted)
        require(!state.firstDistillation || state.deskRestored)
        require(state.studied.isEmpty() || state.deskRestored)
        return state
    }

    fun save(playerId: UUID, state: FirstMagicState) {
        Files.createDirectories(directory)
        val content = listOf(
            "FIRST_MAGIC_V2", playerId.toString(),
            listOf(state.reacted, state.deskRestored, state.firstDistillation).joinToString(",") { if (it) "1" else "0" },
            AnomalousMaterial.entries.joinToString(",") { state.count(it).toString() },
            AnomalousMaterial.entries.joinToString(",") { if (it in state.studied) "1" else "0" },
            FirstAspect.entries.joinToString(",") { state.jar(it).toString() },
            state.discoveredResearchAspects.sorted().joinToString(","),
            state.researchInk.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" },
            state.researchPlacements.entries.sortedBy { it.key }.joinToString(";") { (id, cells) ->
                "$id:${cells.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }}"
            },
            state.unlockedResearch.sorted().joinToString(","),
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

    private fun parseInk(line: String): Map<String, Int> = line.split(',').filter(String::isNotEmpty).associate { entry ->
        val pair = entry.split(':')
        require(pair.size == 2 && pair[0] in AspectCatalog.byId)
        val amount = pair[1].toInt()
        require(amount in 0..999)
        pair[0] to amount
    }

    private fun parsePlacements(line: String): Map<String, Map<Int, String>> = line.split(';').filter(String::isNotEmpty).associate { entry ->
        val cut = entry.indexOf(':')
        require(cut > 0)
        val id = entry.substring(0, cut)
        require(id in ResearchCatalog.byId)
        val cells = entry.substring(cut + 1).split(',').filter(String::isNotEmpty).associate { cell ->
            val pair = cell.split('=')
            require(pair.size == 2)
            val slot = pair[0].toInt()
            require(slot in ResearchBoard.cells && slot !in ResearchCatalog.byId.getValue(id).anchors && pair[1] in AspectCatalog.byId)
            slot to pair[1]
        }
        id to cells
    }
}
