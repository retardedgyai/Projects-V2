package dev.projects.server.coreloop

import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class FirstMagicSliceTest {
    @Test fun firstMaterialToFirstJarSurvivesReload() {
        val player = UUID.randomUUID()
        val directory = Files.createTempDirectory("first-magic-test")
        val repository = FirstMagicRepository(directory)
        var state = FirstMagicState()
        state = FirstMagicRules.apply(state, FirstMagicAction.Collect(AnomalousMaterial.MOONBELL)).state
        assertFalse(FirstMagicRules.apply(state, FirstMagicAction.RestoreDesk).changed)
        state = FirstMagicRules.apply(state, FirstMagicAction.React).state
        state = FirstMagicRules.apply(state, FirstMagicAction.RestoreDesk).state
        state = FirstMagicRules.apply(state, FirstMagicAction.Analyze(AnomalousMaterial.MOONBELL)).state
        assertEquals(1, state.count(AnomalousMaterial.MOONBELL), "analysis must leave the sample for its first distillation")
        state = FirstMagicRules.apply(state, FirstMagicAction.Distill(AnomalousMaterial.MOONBELL)).state
        assertEquals(0, state.count(AnomalousMaterial.MOONBELL))
        assertEquals(2, state.jar(FirstAspect.TIDE))
        assertEquals(1, state.jar(FirstAspect.GALE))
        assertTrue(state.firstDistillation)
        repository.save(player, state)
        assertEquals(state, repository.load(player))
    }

    @Test fun fullJarRejectsDistillationWithoutConsumingSample() {
        val before = FirstMagicState(
            materialCounts = mapOf(AnomalousMaterial.MOONBELL to 1),
            studied = setOf(AnomalousMaterial.MOONBELL),
            jars = mapOf(FirstAspect.TIDE to 15),
            reacted = true, deskRestored = true,
        )
        val result = FirstMagicRules.apply(before, FirstMagicAction.Distill(AnomalousMaterial.MOONBELL))
        assertFalse(result.changed)
        assertEquals(before, result.state)
    }

    @Test fun invalidSaveIsNeverSilentlyReplaced() {
        val player = UUID.randomUUID()
        val directory = Files.createTempDirectory("first-magic-corrupt")
        val file = directory.resolve("$player.magic")
        Files.writeString(file, "unrecognized")
        val repository = FirstMagicRepository(directory)
        assertFailsWith<IllegalArgumentException> { repository.load(player) }
        assertEquals("unrecognized", Files.readString(file))
    }
}
