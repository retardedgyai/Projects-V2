package dev.projects.server.coreloop

import com.google.gson.Gson
import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/** Art review fixture, NOT a gameplay replay: fixed caster and no invented accepted hits.
 * Uses the base catalog and CorePlayerCombat's startup / eight-tick pulse schedule.
 * Gear-adjusted cast speed, movement, hit reactions and display budgets need separate tests.
 */
class CoreCompositeVfxReviewTest {
    @Test fun `export all seventy complete casts with preparations and every base damage pulse`() {
        fun xyz(v: Vec)=listOf(v.x(),v.y(),v.z())
        val scenes=CoreClass.entries.flatMap { job -> CoreSkillCatalog.skills(job).map { skill ->
            val events=mutableListOf<Triple<Int,CoreSkillEffect,List<CoreCombatMeshPart>>>()
            fun event(at: Int,phase: CoreSkillVisualPhase,pulse: Int,prepare: Int=4) {
                val ray=phase==CoreSkillVisualPhase.PULSE && CoreSkillScenes.get(skill.icon).kind==CoreSceneKind.RAY
                // Ground-targeted skills are aimed ahead, not forced under the
                // review camera. This is a chosen unobstructed target, not aim simulation.
                val target=if(skill.motion==CoreSkillMotion.FIELD) kotlin.math.abs(skill.range).coerceIn(1.0,6.0) else 0.0
                val effect=CoreSkillEffect(job,skill,Vec(0.0,if(ray) 1.1 else 0.0,target),Vec(0.0,0.0,1.0),
                    phase,pulse,prepareTicks=prepare,rayLength=if(ray) 6.0 else 0.0,clippedRay=ray)
                events+=Triple(at,effect,CoreSkillChoreography.parts(effect))
            }
            if(skill.startup>1) event(0,CoreSkillVisualPhase.PREPARE,0,skill.startup-1)
            repeat(skill.pulses) { pulse ->
                val at=skill.startup+pulse*8
                if(pulse>0 && skill.motion==CoreSkillMotion.FIELD) event(at-4,CoreSkillVisualPhase.PREPARE,pulse,4)
                event(at,CoreSkillVisualPhase.PULSE,pulse)
            }
            assertEquals(skill.pulses,events.count { it.second.phase==CoreSkillVisualPhase.PULSE })
            val end=events.maxOf { (at,_,parts) -> at+(parts.maxOfOrNull { it.delayTicks+it.durationTicks } ?: 0) }
            val frames=(0..end).map { tick -> events.flatMap { (at,effect,parts) ->
                if(tick<at) emptyList() else parts.mapNotNull { p ->
                    val pose=CoreSkillChoreography.pose(p,(tick-at).toDouble())
                    if(!pose.visible) null else mapOf("model" to pose.model,"offset" to xyz(pose.offset.add(effect.origin)),
                        "scale" to xyz(pose.scale),"yaw" to pose.yaw,"pitch" to pose.pitch,"roll" to pose.roll,
                        "primary" to !p.secondary,"phase" to effect.phase.name,"pulse" to effect.pulse)
                }
            } }
            mapOf("id" to skill.icon,"name" to skill.name,"job" to job.name,"frames" to frames,
                "events" to events.map { (at,e,_) -> mapOf("tick" to at,"phase" to e.phase.name,"pulse" to e.pulse) },
                "peakParts" to frames.maxOf { it.size })
        } }
        assertEquals(70,scenes.size)
        val path=Path.of("../.tools/composite-skill-frames.json")
        Files.createDirectories(path.parent);Files.writeString(path,Gson().toJson(scenes))
    }
}
