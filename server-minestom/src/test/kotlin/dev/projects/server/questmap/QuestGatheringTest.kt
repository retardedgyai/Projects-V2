package dev.projects.server.questmap

import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Pos
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
        val veteran = QuestGatheringMastery.fromMap(mapOf(discipline to 1_000))

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
    fun `mastery tree supports yield and discovery builds with exclusive keystones`() {
        val discipline = QuestGatheringDiscipline.MINING
        val base = QuestGatheringMastery.fromMap(mapOf(discipline to 3_600))
        val steady = (base.unlock(discipline, QuestGatheringMasteryNode.STEADY_HANDS) as QuestGatheringMasteryUnlockResult.Unlocked).mastery
        val yield = (steady.unlock(discipline, QuestGatheringMasteryNode.DEEP_YIELD) as QuestGatheringMasteryUnlockResult.Unlocked).mastery
        val abundance = (yield.unlock(discipline, QuestGatheringMasteryNode.ABUNDANCE_KEYSTONE) as QuestGatheringMasteryUnlockResult.Unlocked).mastery

        assertTrue(abundance.yieldAmount(discipline, QuestGatheringQuality.COMMON) > base.yieldAmount(discipline, QuestGatheringQuality.COMMON))
        assertTrue(abundance.harvestTicks(discipline) > yield.harvestTicks(discipline))
        assertIs<QuestGatheringMasteryUnlockResult.KeystoneConflict>(
            abundance.unlock(discipline, QuestGatheringMasteryNode.DISCOVERY_KEYSTONE),
        )

        val keen = (base.unlock(discipline, QuestGatheringMasteryNode.KEEN_SENSES) as QuestGatheringMasteryUnlockResult.Unlocked).mastery
        val fortune = (keen.unlock(discipline, QuestGatheringMasteryNode.FORTUNE_SEEKER) as QuestGatheringMasteryUnlockResult.Unlocked).mastery
        val discovery = (fortune.unlock(discipline, QuestGatheringMasteryNode.DISCOVERY_KEYSTONE) as QuestGatheringMasteryUnlockResult.Unlocked).mastery
        assertEquals(40, discovery.rareDiscoveryChancePercent(discipline))
    }

    @Test
    fun `progress display is placed on the clicked object face toward the player`() {
        val block = BlockVec(10, 64, 20)
        val fromSouth = gatheringProgressDisplayPosition(Pos(10.5, 64.0, 25.0), 1.62, block)
        val fromWest = gatheringProgressDisplayPosition(Pos(5.0, 64.0, 20.5), 1.62, block)

        assertEquals(10.5, fromSouth.x(), 0.0001)
        assertTrue(fromSouth.z() > 21.0)
        assertTrue(fromWest.x() < 10.0)
        assertEquals(20.5, fromWest.z(), 0.0001)
    }

    @Test
    fun `gathering mastery persists independently for every discipline`() {
        val directory = Files.createTempDirectory("projects-gathering-mastery")
        val repository = QuestGatheringMasteryRepository(directory)
        val playerId = UUID.randomUUID()
        val experience = QuestGatheringDiscipline.entries.associateWith { discipline ->
            if (discipline == QuestGatheringDiscipline.MINING) 1_000 else (discipline.ordinal + 1) * 17
        }
        val mastery = QuestGatheringMastery.fromMap(
            experience,
            mapOf(
                QuestGatheringDiscipline.MINING to setOf(
                    QuestGatheringMasteryNode.STEADY_HANDS,
                    QuestGatheringMasteryNode.DEEP_YIELD,
                ),
            ),
        )

        assertIs<QuestGatheringMasteryLoadResult.Missing>(repository.load(playerId))
        assertTrue(repository.save(playerId, mastery))
        val loaded = assertIs<QuestGatheringMasteryLoadResult.Loaded>(repository.load(playerId)).mastery
        assertEquals(mastery.asMap(), loaded.asMap())
        assertEquals(mastery.asTreeMap(), loaded.asTreeMap())
    }

    @Test
    fun `schema one mastery migrates with an empty tree`() {
        val directory = Files.createTempDirectory("projects-gathering-mastery-v1")
        val repository = QuestGatheringMasteryRepository(directory)
        val playerId = UUID.randomUUID()
        Files.writeString(
            directory.resolve("$playerId.json"),
            """{"schemaVersion":1,"skinning":11,"woodcutting":12,"quarrying":13,"mining":14,"herbalism":15}""",
        )

        val loaded = assertIs<QuestGatheringMasteryLoadResult.Loaded>(repository.load(playerId)).mastery
        assertEquals(14, loaded.experience(QuestGatheringDiscipline.MINING))
        assertTrue(loaded.asTreeMap().values.all { it.isEmpty() })
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
