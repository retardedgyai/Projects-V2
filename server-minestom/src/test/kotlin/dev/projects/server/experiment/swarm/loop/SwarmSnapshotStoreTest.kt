package dev.projects.server.experiment.swarm.loop

import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SwarmSnapshotStoreTest {
    @Test
    fun `bounded snapshot atomically round trips`() {
        val directory = Files.createTempDirectory("swarm-snapshot-roundtrip")
        val store = FileSwarmSnapshotStore(directory)
        val playerId = UUID.randomUUID()
        val snapshot = SwarmPlayerSnapshot.fresh().copy(
            revision = 1,
            selectedRoute = ProcurementRoute.HUNTER,
            questStage = QuestStage.ROUTE_SELECTED,
        )

        assertTrue(store.save(playerId, snapshot))
        assertEquals(snapshot, assertIs<SnapshotLoadResult.Loaded>(store.load(playerId)).snapshot)
        assertTrue(Files.list(directory).use { files -> files.noneMatch { it.fileName.toString().endsWith(".tmp") } })
    }

    @Test
    fun `unknown key and malformed snapshot are rejected without overwrite`() {
        val directory = Files.createTempDirectory("swarm-snapshot-invalid")
        val store = FileSwarmSnapshotStore(directory)
        val playerId = UUID.randomUUID()
        val file = directory.resolve("$playerId.json")
        val original = "{\"schemaVersion\":1,\"unknown\":0}"
        Files.writeString(file, original)

        assertIs<SnapshotLoadResult.Invalid>(store.load(playerId))
        assertTrue(store.isBlocked(playerId))
        assertFalse(store.save(playerId, SwarmPlayerSnapshot.fresh()))
        assertEquals(original, Files.readString(file))
    }

    @Test
    fun `oversized snapshot is rejected and preserved`() {
        val directory = Files.createTempDirectory("swarm-snapshot-oversized")
        val store = FileSwarmSnapshotStore(directory)
        val playerId = UUID.randomUUID()
        val file = directory.resolve("$playerId.json")
        val original = "x".repeat(FileSwarmSnapshotStore.MAX_FILE_BYTES.toInt() + 1)
        Files.writeString(file, original)

        assertIs<SnapshotLoadResult.Invalid>(store.load(playerId))
        assertFalse(store.save(playerId, SwarmPlayerSnapshot.fresh()))
        assertEquals(original, Files.readString(file))
    }

    @Test
    fun `complete hunter journey survives service restart`() {
        val directory = Files.createTempDirectory("swarm-snapshot-journey")
        val playerId = UUID.randomUUID()
        var service = SwarmLoopService(FileSwarmSnapshotStore(directory))
        service.loadPlayer(playerId)
        service.selectRoute(playerId, ProcurementRoute.HUNTER)
        repeat(4) { service.grantBrineclawCord(playerId) }
        service.sell(playerId, SwarmResource.CORD, 2)
        service.buy(playerId, SwarmResource.ORE, 2)
        service.craftAndInstallCoupler(playerId)

        service = SwarmLoopService(FileSwarmSnapshotStore(directory))
        assertEquals(QuestStage.COUPLER_INSTALLED, assertIs<PlayerLoadResult.Ready>(service.loadPlayer(playerId)).snapshot.questStage)
        service.claimBossVictory(playerId)
        service.installMod(playerId, TidehookModChoice.GUARD_RING)
        service.reportCompletion(playerId)

        val reloaded = SwarmLoopService(FileSwarmSnapshotStore(directory))
        val complete = assertIs<PlayerLoadResult.Ready>(reloaded.loadPlayer(playerId)).snapshot
        assertEquals(QuestStage.COMPLETE, complete.questStage)
        assertEquals(FittingState.MOD_GUARD, complete.fittingState)
        assertTrue(complete.firstClearClaimed)
        assertEquals(0, complete.scrip)
    }

    @Test
    fun `schema one profile migrates without granting unearned training`() {
        val directory = Files.createTempDirectory("swarm-snapshot-v1")
        val playerId = UUID.randomUUID()
        Files.writeString(
            directory.resolve("$playerId.json"),
            """{"schemaVersion":1,"revision":4,"initialGrantClaimed":true,"scrip":0,"ore":0,"cord":0,"selectedRoute":"GATHERER","hunterCordEarned":0,"gathererOreEarned":4,"supplierRecordMask":0,"supplierCommissionClaimed":false,"fittingState":"PREPARED","questStage":"COUPLER_INSTALLED","firstClearClaimed":false}""",
        )

        val service = SwarmLoopService(FileSwarmSnapshotStore(directory))
        val migrated = assertIs<PlayerLoadResult.Ready>(service.loadPlayer(playerId)).snapshot

        assertEquals(0, migrated.brineclawTrainingDefeats)
        assertFalse(migrated.preparedForEncounter)
        repeat(3) { service.grantBrineclawCord(playerId) }
        assertTrue(service.preparedForEncounter(playerId))
    }
}
