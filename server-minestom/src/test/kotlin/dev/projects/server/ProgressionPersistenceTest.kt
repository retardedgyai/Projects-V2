package dev.projects.server

import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProgressionPersistenceTest {
    @Test
    fun `missing player starts clean and saved state round trips`() {
        val directory = Files.createTempDirectory("projects-progression-test")
        val repository = ProgressionRepository(directory)
        val playerId = UUID.randomUUID()
        val state = ProgressionState().also {
            it.addXp(100)
            assertEquals(PassiveSpendResult.ACCEPTED, it.spend(GlobalPassiveTree.FORCE))
        }

        assertIs<ProgressionLoadResult.Missing>(repository.load(playerId))
        assertTrue(repository.save(playerId, state))
        val loaded = assertIs<ProgressionLoadResult.Loaded>(repository.load(playerId)).state
        assertEquals(state.record(), loaded.record())
    }

    @Test
    fun `malformed schema is isolated and never silently overwritten`() {
        val directory = Files.createTempDirectory("projects-progression-invalid")
        val repository = ProgressionRepository(directory)
        val playerId = UUID.randomUUID()
        val file = directory.resolve("$playerId.json")
        val original = "{\"schemaVersion\":99,\"level\":1}"
        Files.createDirectories(directory)
        Files.writeString(file, original)

        assertIs<ProgressionLoadResult.Invalid>(repository.load(playerId))
        assertFalse(repository.save(playerId, ProgressionState()))
        assertEquals(original, Files.readString(file))
    }

    @Test
    fun `trailing garbage is rejected and original bytes remain untouched`() {
        val directory = Files.createTempDirectory("projects-progression-trailing")
        val repository = ProgressionRepository(directory)
        val playerId = UUID.randomUUID()
        val file = directory.resolve("$playerId.json")
        val original = "{\"schemaVersion\":1,\"level\":1,\"experience\":0,\"grantedPassivePoints\":0,\"spentPassivePoints\":0,\"allocatedPassiveNodeIds\":[],\"revision\":0} trailing"
        Files.writeString(file, original)

        assertIs<ProgressionLoadResult.Invalid>(repository.load(playerId))
        assertFalse(repository.save(playerId, ProgressionState()))
        assertEquals(original, Files.readString(file))
    }
}
