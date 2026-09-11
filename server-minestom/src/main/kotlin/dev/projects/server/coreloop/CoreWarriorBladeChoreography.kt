package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Native, individually eroding contours in the approved AA's drawing language.
 * The display never rotates a completed slash. Each skill has its own blade-tip trajectory.
 */
internal object CoreWarriorBladeChoreography {
    val sceneIds = setOf("normal_sweep", "normal_reverse", "normal_finish", "dash", "war_wound",
        "war_counter", "slam", "whirl", "war_breach", "war_ult")
    private const val PREFIX = "warrior_skill:"
    fun owns(p: CoreCombatMeshPart) = p.shape.startsWith(PREFIX)

    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart>? {
        if (e.job != CoreClass.WARRIOR || e.sceneId == "dash" || e.sceneId in CoreApprovedNormalV3.sceneIds ||
            e.sceneId !in sceneIds || !e.valid) return null
        val yaw = atan2(e.direction.x(), e.direction.z())
        if (e.phase == CoreSkillVisualPhase.CONTACT) return listOf(CoreCombatMeshPart(
            "${PREFIX}contact", "warred", Vec(0.0, 1.0, 0.0), Vec(1.65, 1.0, 1.65),
            yaw = yaw, pitch = -PI/2, durationTicks = 9))
        val clip = when (e.sceneId) {
            "war_wound" -> "wound"
            "war_counter" -> "counter"
            "slam" -> "cleave"
            "war_breach" -> "thrust"
            "whirl" -> listOf("spin_a", "spin_b", "spin_c")[e.pulse % 3]
            else -> listOf("rise", "return", "finish")[e.pulse % 3]
        }
        val vertical = clip in setOf("cleave", "rise", "finish")
        val spin = clip.startsWith("spin_")
        val reach = min(e.radius, CoreSkillScenes.get(e.sceneId).reach)
        val forward = when { spin -> 0.0; vertical -> reach*.53; clip=="thrust" -> reach*.55; else -> reach*.43 }
        val offset = Vec(sin(yaw)*forward, if(vertical) 1.65 else if(spin) 1.02+e.pulse%3*.12 else 1.15, cos(yaw)*forward)
        val scale = when {
            vertical -> Vec(reach*.95, reach*.75, 3.25)
            spin -> Vec(reach*1.85, 1.0, reach*1.85)
            clip == "thrust" -> Vec(.95, 1.0, reach*1.6)
            else -> Vec(reach*1.75, reach*1.35, reach*1.5)
        }
        val blade = CoreCombatMeshPart("$PREFIX$clip:blade", "warsteel", offset, scale, yaw = yaw,
            pitch = if(vertical) -PI/2 else if(spin) 0.0 else -.45,
            roll = when(clip) { "wound" -> -.38; "counter", "return" -> .26; else -> 0.0 },
            durationTicks = 8, startSize = 1.0, endSize = 1.0)
        if (e.phase == CoreSkillVisualPhase.PREPARE)
            return listOf(blade.copy(shape = "$PREFIX$clip:prepare", durationTicks = e.prepareDuration))
        return buildList {
            add(blade)
            add(blade.copy(shape = "$PREFIX$clip:wake", durationTicks = 16, secondary = true))
            if (clip != "wound") add(blade.copy(shape = "$PREFIX$clip:ember", palette = "warred", durationTicks = 16, secondary = true))
            if (clip in setOf("cleave", "finish")) add(CoreCombatMeshPart("${PREFIX}fracture", "warred",
                Vec(sin(yaw)*e.radius*.48, .12, cos(yaw)*e.radius*.48),
                Vec(e.radius*.85, 1.0, e.radius*.9), yaw = yaw, durationTicks = 14, ground = true))
        }
    }

    fun pose(p: CoreCombatMeshPart, age: Double): CoreMeshPose? {
        if (!owns(p)) return null
        val local = (age-p.delayTicks).coerceIn(0.0, (p.durationTicks-1).toDouble())
        val tokens = p.shape.removePrefix(PREFIX).split(':')
        val suffix = if(tokens.size == 1) "${tokens[0]}_${floor(local).toInt()}" else {
            val prepare = tokens[1] == "prepare"
            val frame = if(prepare) floor(local/(p.durationTicks-1).coerceAtLeast(1)*2).toInt() else floor(local).toInt()+3
            "${tokens[0]}_${if(prepare) "blade" else tokens[1]}_$frame"
        }
        return CoreMeshPose(p.offset, p.scale, p.yaw, p.pitch, p.roll,
            "combat_vfx/warrior_skills/$suffix", age >= p.delayTicks && age < p.delayTicks+p.durationTicks)
    }
}
