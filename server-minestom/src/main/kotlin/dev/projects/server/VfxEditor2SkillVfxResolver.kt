package dev.projects.server

import dev.projects.server.particle.ParticleEffect
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec

/** Resolves one server-owned binding at cast time; it does not cache a composition snapshot. */
class VfxEditor2SkillVfxResolver(
    private val bindingStore: VfxEditor2BindingStore,
    private val compositionStore: VfxEditor2CompositionStore,
    private val warn: (String) -> Unit = { System.err.println(it) },
) {
    private val warnedDanglingBindings = mutableSetOf<Pair<String, String>>()

    fun resolve(targetId: String, origin: Point, direction: Vec): ParticleEffect? {
        if (!VfxEditor2TargetCatalog.contains(targetId)) {
            warn("VFX Editor 2 runtime rejected unknown target '$targetId'; using fallback")
            return null
        }
        val compositionName = bindingStore.bindingFor(targetId) ?: return null
        val composition = compositionStore.load(compositionName)
        if (composition == null) {
            if (warnedDanglingBindings.add(targetId to compositionName)) {
                warn(
                    "VFX Editor 2 dangling binding: target='$targetId' " +
                        "composition='$compositionName'; using fallback",
                )
            }
            return null
        }
        return try {
            VfxWorkbenchCompiler.compile(composition, origin, direction).also {
                warnedDanglingBindings.remove(targetId to compositionName)
            }
        } catch (error: RuntimeException) {
            warn(
                "VFX Editor 2 compile failed: target='$targetId' composition='$compositionName' " +
                    "exception=${error.javaClass.simpleName} reason=${error.message ?: "invalid composition"}; " +
                    "using fallback",
            )
            null
        }
    }
}
