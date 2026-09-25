package dev.projects.server.coreloop

import dev.projects.server.coreloop.ui.CoreMenuCanvas
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AspectResearchTest {
    @Test fun researchCanvasHasTheJapaneseGlyphsItNeeds() {
        val labels = "研究の星図六角盤空の六角クリックしてAspectを選ぶ研究インク手がかり接続中まだ起点とつながっていない" +
            "解明済み研究一覧へ前の頁次の頁未知の組み合わせ材料が不足Aspectの合成性質発見基礎複合構成" +
            "素材を分析し性質を発見二性質を合成して新発見六角盤の空欄に記すとなりの性質をつなぐ固定点が全てつながれば解明" +
            "所持した性質を一つ消費外せばインクは戻る合成で複合性質を4得る" +
            "研究の手順発見性質の合成発見済みの二性質を使う各インクを一つ消費新しい性質のインクを4得る" +
            "素材分析で基礎性質を補充構成の結び複合性質とその材料は研究盤で接続できる火風光" +
            ResearchCatalog.all.joinToString("") { it.title }
        assertTrue(CoreMenuCanvas.missingCharacters(labels).isEmpty(), CoreMenuCanvas.missingCharacters(labels).toString())
    }

    @Test fun everySubjectHasAValidTwoStepSolutionOnTheHexBoard() {
        assertEquals(6, AspectCatalog.primal.size)
        assertEquals(10, ResearchCatalog.all.size)
        assertEquals(19, ResearchBoard.cells.size)
        for (subject in ResearchCatalog.all) {
            val path = when (subject.anchors.keys) {
                setOf(18, 22, 26) -> listOf(18, 20, 22, 24, 26)
                setOf(2, 22, 42) -> listOf(2, 12, 22, 32, 42)
                setOf(6, 22, 38) -> listOf(6, 14, 22, 30, 38)
                else -> error("No route for ${subject.id}")
            }
            val first = AspectCatalog.combine(subject.anchors.getValue(path[0]), subject.anchors.getValue(path[2]))
            val second = AspectCatalog.combine(subject.anchors.getValue(path[2]), subject.anchors.getValue(path[4]))
            assertTrue(first != null && second != null, subject.id)
            assertTrue(ResearchBoard.complete(subject, mapOf(path[1] to first.id, path[3] to second.id)), subject.id)
            assertFalse(ResearchBoard.complete(subject, mapOf(path[1] to "terra", path[3] to second.id)), subject.id)
        }
        assertTrue(20 in ResearchBoard.neighbors(18))
        assertFalse(22 in ResearchBoard.neighbors(18))
        assertTrue(AspectCatalog.linked("ignis", "lux"))
        assertFalse(AspectCatalog.linked("ignis", "aer"))
    }

    @Test fun analysisCombinationPlacementAndUnlockSurviveReload() {
        val player = UUID.randomUUID()
        val repository = FirstMagicRepository(Files.createTempDirectory("aspect-research"))
        var state = FirstMagicState()
        fun act(action: FirstMagicAction) { state = FirstMagicRules.apply(state, action).state }
        act(FirstMagicAction.Collect(AnomalousMaterial.MOONBELL))
        act(FirstMagicAction.Collect(AnomalousMaterial.EMBER_MOSS))
        act(FirstMagicAction.React)
        act(FirstMagicAction.RestoreDesk)
        act(FirstMagicAction.Analyze(AnomalousMaterial.MOONBELL))
        act(FirstMagicAction.Analyze(AnomalousMaterial.EMBER_MOSS))
        assertTrue(setOf("aer", "aqua", "ignis", "terra", "ordo").all(state.discoveredResearchAspects::contains))
        act(FirstMagicAction.Combine("aer", "ignis"))
        act(FirstMagicAction.Combine("aer", "aqua"))
        assertTrue(setOf("lux", "tempestas").all(state.discoveredResearchAspects::contains))
        val beforeInk = state.ink("terra")
        act(FirstMagicAction.Place("lamp", 20, "terra"))
        assertFalse("lamp" in state.unlockedResearch)
        act(FirstMagicAction.Remove("lamp", 20))
        assertEquals(beforeInk, state.ink("terra"))
        act(FirstMagicAction.Place("lamp", 20, "lux"))
        assertFalse("lamp" in state.unlockedResearch)
        act(FirstMagicAction.Place("lamp", 24, "tempestas"))
        assertTrue("lamp" in state.unlockedResearch)
        assertFalse(FirstMagicRules.apply(state, FirstMagicAction.Remove("lamp", 20)).changed)
        repository.save(player, state)
        assertEquals(state, repository.load(player))
    }

    @Test fun oldFirstMagicSaveMigratesWithoutLosingEssentia() {
        val player = UUID.randomUUID()
        val directory = Files.createTempDirectory("aspect-research-v1")
        Files.writeString(directory.resolve("$player.magic"), listOf(
            "FIRST_MAGIC_V1", player.toString(), "1,1,0", "1,0,0,0,0,0",
            "1,0,0,0,0,0", "0,3,0,0",
        ).joinToString("\n", postfix = "\n"))
        val repository = FirstMagicRepository(directory)
        val state = repository.load(player)
        assertEquals(3, state.jar(FirstAspect.TIDE))
        assertTrue(setOf("aer", "aqua", "ordo").all(state.discoveredResearchAspects::contains))
        repository.save(player, state)
        assertEquals(state, repository.load(player))
    }
}
