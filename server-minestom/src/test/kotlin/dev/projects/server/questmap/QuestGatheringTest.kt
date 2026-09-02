package dev.projects.server.questmap

import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class QuestGatheringTest {
    @Test
    fun `eight generated gathering locations cover every discipline`() {
        val nodes = questGatheringNodes(VerdantRoadQuestPlanner.generate(91_337L))

        assertEquals(8, nodes.size)
        assertEquals(QuestGatheringDiscipline.entries.toSet(), nodes.map { it.discipline }.toSet())
        assertEquals(nodes.size, nodes.map { it.blockPosition }.distinct().size)
    }

    @Test
    fun `mastery improves gathering speed and eventually increases yield`() {
        val discipline = QuestGatheringDiscipline.MINING
        val novice = QuestGatheringMastery()
        val veteran = QuestGatheringMastery.fromMap(mapOf(discipline to 600))

        assertTrue(veteran.level(discipline) > novice.level(discipline))
        assertTrue(veteran.harvestTicks(discipline) < novice.harvestTicks(discipline))
        assertTrue(
            veteran.yieldAmount(discipline, QuestGatheringQuality.COMMON) >
                novice.yieldAmount(discipline, QuestGatheringQuality.COMMON),
        )
        assertTrue(
            novice.yieldAmount(discipline, QuestGatheringQuality.BOUNTIFUL) >
                novice.yieldAmount(discipline, QuestGatheringQuality.COMMON),
        )
    }

    @Test
    fun `gathering mastery persists independently for every discipline`() {
        val directory = Files.createTempDirectory("projects-gathering-mastery")
        val repository = QuestGatheringMasteryRepository(directory)
        val playerId = UUID.randomUUID()
        val mastery = QuestGatheringMastery.fromMap(
            QuestGatheringDiscipline.entries.associateWith { (it.ordinal + 1) * 17 },
        )

        assertIs<QuestGatheringMasteryLoadResult.Missing>(repository.load(playerId))
        assertTrue(repository.save(playerId, mastery))
        val loaded = assertIs<QuestGatheringMasteryLoadResult.Loaded>(repository.load(playerId)).mastery
        assertEquals(mastery.asMap(), loaded.asMap())
    }

    @Test
    fun `invalid mastery file is not overwritten`() {
        val directory = Files.createTempDirectory("projects-gathering-mastery-invalid")
        val repository = QuestGatheringMasteryRepository(directory)
        val playerId = UUID.randomUUID()
        val file = directory.resolve("$playerId.json")
        val invalid = "{\"schemaVersion\":99}"
        Files.writeString(file, invalid)

        assertIs<QuestGatheringMasteryLoadResult.Invalid>(repository.load(playerId))
        assertFalse(repository.save(playerId, QuestGatheringMastery()))
        assertEquals(invalid, Files.readString(file))
    }
}
