package dev.projects.client

import dev.projects.protocol.VfxEditor2Composition
import dev.projects.protocol.VfxEditor2LoadResponse
import dev.projects.protocol.VFX_EDITOR_2_DEFAULT_TIMELINE_LENGTH_TICKS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VfxEditor2WorkspaceTest {
    @Test
    fun `unbound workspace starts empty and is not dirty`() {
        val workspace = emptyVfxEditor2Workspace()

        assertTrue(workspace.composition.effects.isEmpty())
        assertEquals("untitled", workspace.composition.name)
        assertEquals(VFX_EDITOR_2_DEFAULT_TIMELINE_LENGTH_TICKS, workspace.composition.timelineLengthTicks)
        assertEquals(null, workspace.selectedEffectId)
        assertFalse(workspace.isDirty)
    }

    @Test
    fun `session keeps separate workspaces for each target`() {
        val session = VfxEditor2WorkspaceSession()
        val roninQ = workspace("ronin-q-draft")
        val roninE = workspace("ronin-e-draft")

        session.remember("ronin-q", roninQ)
        session.remember("ronin-e", roninE)

        assertEquals(roninQ, session.stateFor("ronin-q"))
        assertEquals(roninE, session.stateFor("ronin-e"))
    }

    @Test
    fun `workspace resolution restores dirty state before binding`() {
        val dirty = workspace("ronin-q-draft")

        val resolution = resolveVfxEditor2Workspace(dirty, "saved-q")

        assertEquals(dirty, assertIs<VfxEditor2WorkspaceResolution.Restore>(resolution).state)
    }

    @Test
    fun `bound target loads binding when it has no workspace`() {
        val resolution = resolveVfxEditor2Workspace(null, "saved-q")

        assertEquals("saved-q", assertIs<VfxEditor2WorkspaceResolution.LoadBound>(resolution).compositionName)
    }

    @Test
    fun `new workspace is empty but intentionally dirty`() {
        val workspace = emptyVfxEditor2Workspace(placeholder = false)

        assertTrue(workspace.composition.effects.isEmpty())
        assertTrue(workspace.isDirty)
    }

    @Test
    fun `stale load response cannot apply to another target or generation`() {
        val response = VfxEditor2LoadResponse(
            name = "saved-q",
            composition = VfxEditor2Composition(emptyList(), "saved-q"),
            message = "Loaded",
        )
        val request = VfxEditor2LoadContext("ronin-q", "saved-q", generation = 4L)

        assertTrue(isCurrentVfxEditor2Load(request, response, "ronin-q", 4L))
        assertFalse(isCurrentVfxEditor2Load(request, response, "ronin-e", 4L))
        assertFalse(isCurrentVfxEditor2Load(request, response, "ronin-q", 3L))
        assertFalse(
            isCurrentVfxEditor2Load(
                request,
                response.copy(name = "other", composition = VfxEditor2Composition(emptyList(), "other")),
                "ronin-q",
                4L,
            ),
        )
    }

    private fun workspace(name: String): VfxEditor2WorkspaceState {
        val composition = VfxEditor2Composition(emptyList(), name)
        return VfxEditor2WorkspaceState(
            composition = composition,
            compositionNameInput = name,
            savedSnapshot = VfxEditor2Composition(emptyList(), "saved-$name"),
            selectedEffectId = null,
            nextEffectId = 1L,
            isPlaceholder = false,
        )
    }
}
