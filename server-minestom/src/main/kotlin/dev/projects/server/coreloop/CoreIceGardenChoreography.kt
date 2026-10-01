package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Low pixel ice plates grow once, hold still, then chip away. No rotating blue dome. */
internal object CoreIceGardenChoreography {
    fun owns(p: CoreCombatMeshPart) = p.shape.startsWith("ice_garden:")
    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart> {
        if (e.phase == CoreSkillVisualPhase.CONTACT) return listOf(
            CoreCombatMeshPart("ice_garden:contact", "ice", Vec(0.0,.08,0.0), Vec(.8,.8,.8),
                durationTicks=10, ground=true, travel=Vec(0.0,.35,0.0)))
        // Reference/lab effects have no terrain sample. Live casts always provide the supported cells.
        val cellSize=CoreIceGarden.cellSize(e.skill.radius)
        val cells = e.gardenCells ?: CoreIceGarden.offsets.map { (x,z) ->
            net.minestom.server.coordinate.Pos(e.origin.x()+x*cellSize,e.origin.y(),e.origin.z()+z*cellSize)
        }
        val preparing = e.phase == CoreSkillVisualPhase.PREPARE
        val life = if (preparing) e.prepareDuration else e.skill.duration + 10
        return cells.map { cell ->
            val x=((cell.x()-e.origin.x())/cellSize).roundToInt()+2
            val z=((cell.z()-e.origin.z())/cellSize).roundToInt()+2
            CoreCombatMeshPart("ice_garden:${if(preparing) "prepare" else "tile"}:$x:$z", "ice",
                Vec(cell.x()-e.origin.x(),cell.y()-e.origin.y()+.035,cell.z()-e.origin.z()),
                Vec(cellSize,1.0,cellSize),
                durationTicks=life, ground=false, startSize=1.0,endSize=1.0)
        }
    }
    fun pose(p: CoreCombatMeshPart, age: Double): CoreMeshPose? {
        if (!owns(p)) return null
        val local = (age-p.delayTicks).coerceAtLeast(0.0)
        val prepare = p.shape.startsWith("ice_garden:prepare")
        val contact = p.shape == "ice_garden:contact"
        val live = p.durationTicks-10
        val stage = when {
            prepare -> 0
            contact -> 0
            local < 4 -> 1
            local < live-20 -> 2
            local < live -> 3
            else -> (4+floor((local-live)/2).toInt()).coerceAtMost(8)
        }
        val coords=p.shape.split(':').drop(2).joinToString("_")
        val model = if (prepare) "prepare" else if(contact) "contact" else "tile_${coords}_$stage"
        val grow = if(contact) 1+(local/9).coerceIn(0.0,1.0)*.35 else 1.0
        return CoreMeshPose(p.offset.add(if(contact) p.travel.mul(local/10) else Vec.ZERO),p.scale.mul(grow),
            p.yaw,p.pitch,p.roll,"combat_vfx/garden/$model",age>=p.delayTicks && local<p.durationTicks)
    }
}
