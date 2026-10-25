package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/**
 * The greatsword's normal-attack slashes, drawn along the swing itself (2026-10-25, Creator: the slash must match the
 * class's swing animation). scripts/build_normal_slash_v4.py builds them with the approved slash's recipe (sections
 * born where the blade's tip was, widest where it was fastest, a bright cutting rim, a wake that drifts and erodes),
 * following the tip of the first-person swing tick by tick after the hit, so the trail is always behind the blade.
 *
 * The models are in the player's own frame, a quarter scale, as the swing is drawn (the camera looking [DRAWN_PITCH]
 * down). The display stands at the feet, turned to the facing, and round the eye to the owner's look pitch, so the
 * trail stays on the blade wherever the owner looks (the first-person sword is drawn the same whatever the pitch).
 */
internal object CoreApprovedNormalV3 {
    val sceneIds = setOf("normal_sweep", "normal_reverse", "normal_finish")
    private const val FOLDER = "combat_vfx/normal_slash_v4"
    private const val SCALE = 4.0
    private const val EYE = 1.62
    private val DRAWN_PITCH = Math.toRadians(12.0)
    const val BLADE_TICKS = 7
    const val WAKE_TICKS = 9

    private fun beat(sceneId: String) = when (sceneId) { "normal_reverse" -> 2; "normal_finish" -> 3; else -> 1 }

    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart>? {
        if (e.job != CoreClass.WARRIOR || e.sceneId !in sceneIds || !e.valid) return null
        if (e.phase == CoreSkillVisualPhase.CONTACT) return CoreApprovedDashV3.parts(
            CoreSkillEffect(e.job, e.skill, e.origin, e.direction, e.phase, sceneId = "dash"))
        val yaw = atan2(e.direction.x(), e.direction.z())
        val tilt = (e.lookPitch - DRAWN_PITCH).coerceIn(-PI / 3, PI / 3)
        // Turned round the eye: the display turns about its own origin (the feet), so its origin moves the other way.
        val eye = Vec(sin(yaw) * EYE * sin(tilt), EYE * cos(tilt), cos(yaw) * EYE * sin(tilt))
        val offset = Vec(-eye.x(), EYE - eye.y(), -eye.z())
        val blade = CoreCombatMeshPart("normal_slash_blade_${beat(e.sceneId)}", "steel", offset, Vec(SCALE, SCALE, SCALE),
            yaw = yaw, pitch = tilt, durationTicks = BLADE_TICKS, startSize = 1.0, endSize = 1.0)
        return listOf(blade, blade.copy(shape = "normal_slash_wake_${beat(e.sceneId)}", durationTicks = WAKE_TICKS, secondary = true))
    }

    fun owns(p: CoreCombatMeshPart) = p.shape.startsWith("normal_slash_")

    fun pose(p: CoreCombatMeshPart, age: Double): CoreMeshPose? {
        if (!owns(p)) return null
        val layer = if (p.shape.startsWith("normal_slash_blade_")) "blade" else "wake"
        val beat = p.shape.substringAfterLast('_')
        val local = (age - p.delayTicks).coerceIn(0.0, (p.durationTicks - 1).toDouble())
        return CoreMeshPose(p.offset, p.scale, p.yaw, p.pitch, p.roll, "$FOLDER/b${beat}_${layer}_${floor(local).toInt()}",
            age >= p.delayTicks && age < p.delayTicks + p.durationTicks)
    }
}
