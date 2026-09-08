package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.min

/** AA uses the approved dash's actual contour and sample-age erosion, not another slash renderer. */
internal object CoreApprovedNormalV3 {
    val sceneIds = setOf("normal_sweep", "normal_reverse", "normal_finish")

    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart>? {
        if (e.job != CoreClass.WARRIOR || e.sceneId !in sceneIds || !e.valid) return null
        val reach = min(CoreSkillScenes.get(e.sceneId).reach, e.radius)
        val source = CoreSkillEffect(e.job, e.skill.copy(radius = reach), e.origin, e.direction,
            e.phase, sceneId = "dash", prepareTicks = e.prepareDuration)
        val reachRatio = reach / min(reach, CoreSkillScenes.get("dash").reach)
        return CoreApprovedDashV3.parts(source)!!.map { original ->
            if (e.phase == CoreSkillVisualPhase.CONTACT) original
            else {
                // Frozen dash clamps to its own 2.1m. Fit the unchanged contour to each AA's
                // existing visual envelope; never alter combat range or stretch it over time.
                val p = original.copy(scale = original.scale.mul(reachRatio),
                    offset = Vec(original.offset.x()*reachRatio, original.offset.y(), original.offset.z()*reachRatio))
                when (e.sceneId) {
                    // Reverse the contour itself, not the frame order or an orbiting finished image.
                    "normal_reverse" -> p.copy(roll = .28, spriteMirror = true)
                    "normal_finish" -> p.copy(offset = p.offset.add(0.0, .8, 0.0), roll = -.75)
                    else -> p
                }
            }
        }
    }

    fun pose(p: CoreCombatMeshPart, age: Double): CoreMeshPose? {
        if (!p.spriteMirror || !p.shape.startsWith("approved_dash_")) return null
        return CoreApprovedDashV3.pose(p, age)?.let {
            it.copy(model = it.model.replace("approved_dash_v3/", "approved_aa_reverse_v3/"))
        }
    }
}
