package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/**
 * Iron-mass warrior strikes (2026-10-17) on the approved native-plate pipeline (scripts/build_warrior_iron_vfx.py,
 * hand-drawn red-hot masters with translucent halos). 地割り: the overhead crescent (full only while it lands, then
 * its rim), fire bursting out of the split ground, slabs of ground thrust up, one molten fissure running forward
 * and thrown iron shards. 押し薙ぎ: a thick 190 degree crescent
 * whose white-hot rim leads and whose tail burns away, with shards flung outward.
 * 鉄槌 (2026-10-19): three falls that heat up, red, orange, then the full white-hot cleave, whose ground bursts
 * into a cluster of rock pillars under the target (the launch). The first two fall left and right of the wielder so
 * they read apart and are not hidden behind the body in third person.
 * Preparation and contact keep the existing blade choreography.
 */
internal object CoreWarriorIronChoreography {
    private const val PREFIX = "war_iron:"
    private const val TILT = -.42
    /**
     * Trial (2026-10-19, Creator: try the 黒翳竜 wing-arm's shader slash for cuts): when on, the hand-drawn crescent
     * plates (cleave, arc, ucleave) are replaced by one shader-coloured quad (sprite boss_vfx/war_slash, kinds 7..9 in
     * scripts/shaders/item.fsh) whose head runs with the progress the server sends every tick. Rocks, fire and
     * shards stay as they are. Off until the Creator picks it from the preview.
     */
    @Volatile var shaderCut = false
    /** Standing cut seen from behind: starts at the top and bulges to the wielder's left, like the drawn cleave. */
    private const val DOWNWARD = -PI / 2
    val sceneIds = setOf("slam", "whirl", "war_ult")
    fun owns(p: CoreCombatMeshPart) = p.shape.startsWith(PREFIX)
    // 鉄槌's windup is the greatsword itself rising over the shoulder (CoreArmamentPresentation); no ghost blade.
    fun handles(e: CoreSkillEffect) = e.job == CoreClass.WARRIOR && e.valid &&
        (e.phase == CoreSkillVisualPhase.PULSE || e.phase == CoreSkillVisualPhase.PREPARE && e.sceneId == "war_ult") &&
        e.sceneId in sceneIds && (e.sceneId != "whirl" || e.skill.motion == CoreSkillMotion.CONE)

    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart>? {
        if (!handles(e)) return null
        if (e.phase == CoreSkillVisualPhase.PREPARE) return emptyList()
        val yaw = atan2(e.direction.x(), e.direction.z())
        val reach = min(e.radius, CoreSkillScenes.get(e.sceneId).reach)
        fun local(x: Double, y: Double, z: Double) = Vec(cos(yaw) * x + sin(yaw) * z, y, -sin(yaw) * x + cos(yaw) * z)
        fun part(clip: String, at: Vec, scale: Vec, pitch: Double, life: Int, secondary: Boolean = false, ground: Boolean = false) =
            CoreCombatMeshPart("$PREFIX$clip", "steel", at, scale, yaw = yaw, pitch = pitch, durationTicks = life,
                startSize = 1.0, endSize = 1.0, secondary = secondary, ground = ground)
        // kind 7 = white-hot, 8 / 9 cooler. The drawn plate is used when the trial is off.
        fun cut(drawn: CoreCombatMeshPart, kind: Int, at: Vec, scale: Vec, pitch: Double, roll: Double, life: Int) =
            if (shaderCut) part("cut$kind", at, scale, pitch, life + 2).copy(roll = roll) else drawn
        return when (e.sceneId) {
            "slam" -> {
                // Everything stays inside the damage reach: the fissure ends where the hit ends.
                val impact = 2.0
                val rift = reach - impact
                listOf(
                    cut(part("cleave", local(0.0, 1.65, impact - .2), Vec(3.0, 1.0, 3.2), -PI / 2, 8), 7, local(0.0, 1.65, impact - .2), Vec(3.5, 1.0, 3.5), -PI / 2, DOWNWARD + .3, 8),
                    // Solid tilted rock chunks thrust out of the ground; model y=8 sits on the ground (16 units = 3.6 m).
                    part("heave", local(0.0, .12, impact + .2), Vec(3.4, 3.4, 3.4), 0.0, 12, ground = true),
                    part("burst", local(0.0, 1.47, impact + .2), Vec(2.9, 1.0, 2.9), -PI / 2, 9),
                    // A crossed copy keeps the fire's volume when seen from the side.
                    part("burstx", local(0.0, 1.47, impact + .2), Vec(2.4, 1.0, 2.9), -PI / 2, 9, secondary = true).let { it.copy(yaw = it.yaw + PI / 2) },
                    part("rift", local(0.0, .13, impact + rift * .5), Vec(rift * 1.2, 1.0, rift), 0.0, 16, ground = true),
                    part("rim", local(0.0, .12, impact + rift * .5), Vec(rift * 1.2, 3.0, rift), 0.0, 16, ground = true),
                    part("shards", local(0.0, 1.53, impact), Vec(3.0, 1.0, 3.0), -PI / 2, 12, secondary = true),
                )
            }
            "war_ult" -> {
                val impact = 2.4
                val rift = reach - impact
                when (e.pulse % 3) {
                    0 -> listOf(
                        cut(part("ucleave_r", local(-.9, 1.65, impact - .2), Vec(3.0, 1.0, 3.2), -PI / 2, 8).copy(roll = .45), 9, local(-.9, 1.65, impact - .2), Vec(3.5, 1.0, 3.5), -PI / 2, DOWNWARD + .45, 8),
                        part("rift", local(-.5, .13, impact + rift * .3), Vec(rift * .7, 1.0, rift * .6), 0.0, 14, ground = true),
                    )
                    1 -> listOf(
                        cut(part("ucleave_o", local(.9, 1.65, impact - .2), Vec(3.0, 1.0, 3.2), -PI / 2, 8).copy(roll = -.45), 8, local(.9, 1.65, impact - .2), Vec(3.5, 1.0, 3.5), -PI / 2, DOWNWARD - .45, 8),
                        part("uburst_o", local(0.0, 1.47, impact + .2), Vec(2.6, 1.0, 2.6), -PI / 2, 9),
                        part("shards", local(0.0, 1.53, impact), Vec(2.6, 1.0, 2.6), -PI / 2, 12, secondary = true),
                    )
                    else -> listOf(
                        cut(part("cleave", local(0.0, 1.9, impact - .2), Vec(3.6, 1.0, 3.9), -PI / 2, 8), 7, local(0.0, 1.9, impact - .2), Vec(4.2, 1.0, 4.2), -PI / 2, DOWNWARD + .2, 8),
                        part("pillar", local(0.0, .12, impact + .3), Vec(3.6, 3.6, 3.6), 0.0, 14, ground = true),
                        part("burst", local(0.0, 1.6, impact + .2), Vec(3.4, 1.0, 3.4), -PI / 2, 9),
                        part("burstx", local(0.0, 1.6, impact + .2), Vec(2.8, 1.0, 3.4), -PI / 2, 9, secondary = true).let { it.copy(yaw = it.yaw + PI / 2) },
                        part("rift", local(0.0, .13, impact + rift * .5), Vec(rift * 1.2, 1.0, rift), 0.0, 16, ground = true),
                        part("rim", local(0.0, .12, impact + rift * .5), Vec(rift * 1.2, 3.0, rift), 0.0, 16, ground = true),
                        part("shards", local(0.0, 1.7, impact), Vec(3.4, 1.0, 3.4), -PI / 2, 12, secondary = true),
                    )
                }
            }
            else -> {
                val span = reach * 2.0
                listOf(
                    // Tilted so the far side rises toward the wielder's eye: read as a crescent, not a line.
                    cut(part("arc", local(0.0, .95, 0.0), Vec(span, 1.0, span), TILT, 11), 7, local(0.0, .95, 0.0), Vec(span, 1.0, span), TILT, 0.0, 11),
                    part("wshards", local(0.0, .95, 0.0), Vec(span, 1.0, span), TILT, 12, secondary = true),
                )
            }
        }
    }

    fun pose(p: CoreCombatMeshPart, age: Double): CoreMeshPose? {
        if (!owns(p)) return null
        val frame = floor((age - p.delayTicks).coerceIn(0.0, (p.durationTicks - 1).toDouble())).toInt()
        val clip = p.shape.removePrefix(PREFIX)
        if (clip.startsWith("cut")) {
            // Red = progress, green = kind, blue = the shader marker 77 (scripts/shaders/item.fsh).
            val progress = (frame.toDouble() / (p.durationTicks - 1).coerceAtLeast(1) * 255).toInt()
            return CoreMeshPose(p.offset, p.scale, p.yaw, p.pitch, p.roll, "boss_vfx/war_slash",
                age >= p.delayTicks && age < p.delayTicks + p.durationTicks, tint = (progress shl 16) or (clip.drop(3).toInt() shl 8) or 77)
        }
        return CoreMeshPose(p.offset, p.scale, p.yaw, p.pitch, p.roll,
            "combat_vfx/warrior_iron/${p.shape.removePrefix(PREFIX)}_$frame",
            age >= p.delayTicks && age < p.delayTicks + p.durationTicks)
    }
}
