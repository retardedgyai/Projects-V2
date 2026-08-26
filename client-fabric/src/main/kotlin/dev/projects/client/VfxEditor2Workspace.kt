package dev.projects.client

import dev.projects.protocol.VfxEditor2Composition
import dev.projects.protocol.VfxEditor2LoadResponse

/** A single Editor 2 target's in-memory authoring state. It is intentionally session-only. */
internal data class VfxEditor2WorkspaceState(
    val composition: VfxEditor2Composition,
    val compositionNameInput: String,
    val savedSnapshot: VfxEditor2Composition?,
    val selectedEffectId: Long?,
    val nextEffectId: Long,
    val isPlaceholder: Boolean,
) {
    val isDirty: Boolean
        get() = !isPlaceholder && (
            savedSnapshot == null ||
                compositionNameInput != composition.name ||
                composition.withoutSolo() != savedSnapshot
            )
}

internal class VfxEditor2WorkspaceSession {
    private val workspaces = linkedMapOf<String, VfxEditor2WorkspaceState>()

    fun stateFor(targetId: String): VfxEditor2WorkspaceState? = workspaces[targetId]

    fun remember(targetId: String, state: VfxEditor2WorkspaceState) {
        workspaces[targetId] = state
    }
}

internal sealed interface VfxEditor2WorkspaceResolution {
    data class Restore(val state: VfxEditor2WorkspaceState) : VfxEditor2WorkspaceResolution
    data class LoadBound(val compositionName: String) : VfxEditor2WorkspaceResolution
    data object Empty : VfxEditor2WorkspaceResolution
}

internal fun resolveVfxEditor2Workspace(
    existing: VfxEditor2WorkspaceState?,
    bindingName: String?,
): VfxEditor2WorkspaceResolution {
    if (existing != null) {
        if (existing.isDirty) return VfxEditor2WorkspaceResolution.Restore(existing)
        if (bindingName == null || (!existing.isPlaceholder && existing.composition.name == bindingName)) {
            return VfxEditor2WorkspaceResolution.Restore(existing)
        }
    }
    return bindingName?.let(VfxEditor2WorkspaceResolution::LoadBound)
        ?: VfxEditor2WorkspaceResolution.Empty
}

internal data class VfxEditor2LoadContext(
    val targetId: String,
    val compositionName: String,
    val generation: Long,
)

/** Guards a response against a target switch or a newer request in this screen session. */
internal fun isCurrentVfxEditor2Load(
    request: VfxEditor2LoadContext?,
    response: VfxEditor2LoadResponse,
    currentTargetId: String?,
    currentGeneration: Long,
): Boolean = request != null &&
    request.targetId == currentTargetId &&
    request.generation == currentGeneration &&
    request.compositionName == response.name

internal fun emptyVfxEditor2Workspace(placeholder: Boolean = true): VfxEditor2WorkspaceState {
    val composition = VfxEditor2Composition(emptyList())
    return VfxEditor2WorkspaceState(
        composition = composition,
        compositionNameInput = composition.name,
        savedSnapshot = null,
        selectedEffectId = null,
        nextEffectId = 1L,
        isPlaceholder = placeholder,
    )
}
