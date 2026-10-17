package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/**
 * Iron-mass warrior strikes (2026-10-17) on the approved native-plate pipeline (scripts/build_warrior_iron_vfx.py,
 * hand-drawn red-hot masters with translucent halos). 地割り: the overhead crescent (full only while it lands, then
 * its rim), fire bursting out of the split ground, slabs of ground thrust up, one molten fissure running forward
 * and thrown iron shards. 押し薙ぎ: a thick 190 degree crescent
 * whose white-hot rim leads and whose tail burns away, with shards flung outward.
 * Preparation and contact keep the existing blade choreography.
 */
internal object CoreWarriorIronChoreography {
    private const val PREFIX = "war_iron:"
    private const val TILT = -.42
    val sceneIds = setOf("slam", "whirl")
    fun owns(p: CoreCombatMeshPart) = p.shape.startsWith(PREFIX)
    fun handles(e: CoreSkillEffect) = e.job == CoreClass.WARRIOR && e.valid && e.phase == CoreSkillVisualPhase.PULSE &&
        e.sceneId in sceneIds && (e.sceneId != "whirl" || e.skill.motion == CoreSkillMotion.CONE)

    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart>? {
        if (!handles(e)) return null
        val yaw = atan2(e.direction.x(), e.direction.z())
        val reach = min(e.radius, CoreSkillScenes.get(e.sceneId).reach)
        fun local(x: Double, y: Double, z: Double) = Vec(cos(yaw) * x + sin(yaw) * z, y, -sin(yaw) * x + cos(yaw) * z)
        fun part(clip: String, at: Vec, scale: Vec, pitch: Double, life: Int, secondary: Boolean = false, ground: Boolean = false) =
            CoreCombatMeshPart("$PREFIX$clip", "steel", at, scale, yaw = yaw, pitch = pitch, durationTicks = life,
                startSize = 1.0, endSize = 1.0, secondary = secondary, ground = ground)
        return when (e.sceneId) {
            "slam" -> {
                val impact = 2.4
                listOf(
                    part("cleave", local(0.0, 1.5, impact - .3), Vec(3.0, 1.0, 3.2), -PI / 2, 8),
                    // Solid tilted rock chunks thrust out of the ground; model y=8 sits on the ground (16 units = 3.6 m).
                    part("heave", local(0.0, .12, impact + .1), Vec(3.6, 3.6, 3.6), 0.0, 12, ground = true),
                    part("burst", local(0.0, 1.4, impact + .2), Vec(2.9, 1.0, 2.9), -PI / 2, 9),
                    // A crossed copy keeps the fire's volume when seen from the side.
                    part("burst", local(0.0, 1.4, impact + .2), Vec(2.4, 1.0, 2.9), -PI / 2, 9, secondary = true).let { it.copy(yaw = it.yaw + PI / 2) },
                    part("rift", local(0.0, .13, impact + reach * .5), Vec(reach * 1.1, 1.0, reach * 1.2), 0.0, 16, ground = true),
                    part("rim", local(0.0, .12, impact + reach * .5), Vec(reach * 1.1, 3.0, reach * 1.2), 0.0, 16, ground = true),
                    part("shards", local(0.0, 1.4, impact), Vec(3.0, 1.0, 3.0), -PI / 2, 12, secondary = true),
                )
            }
            else -> {
                val span = reach * 2.2
                listOf(
                    // Tilted so the far side rises toward the wielder's eye: read as a crescent, not a line.
                    part("arc", local(0.0, .95, .4), Vec(span, 1.0, span), TILT, 11),
                    part("wshards", local(0.0, .95, .4), Vec(span * 1.1, 1.0, span * 1.1), TILT, 12, secondary = true),
                )
            }
        }
    }

    fun pose(p: CoreCombatMeshPart, age: Double): CoreMeshPose? {
        if (!owns(p)) return null
        val frame = floor((age - p.delayTicks).coerceIn(0.0, (p.durationTicks - 1).toDouble())).toInt()
        return CoreMeshPose(p.offset, p.scale, p.yaw, p.pitch, p.roll,
            "combat_vfx/warrior_iron/${p.shape.removePrefix(PREFIX)}_$frame",
            age >= p.delayTicks && age < p.delayTicks + p.durationTicks)
    }
}
