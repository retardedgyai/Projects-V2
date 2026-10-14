package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Five low native crowns form once; the supported footprint stays static between phase changes. */
internal object CoreIceGardenChoreography {
    private val crowns=setOf(0 to 2,-2 to 0,1 to -2,2 to 1,-1 to 1)
    internal fun crown(x: Int,z: Int) = (x to z) in crowns
    internal fun boundaryInk(age: Int,life: Int)=when { age>=life-10 -> 0x719da8;age>=life-20 -> 0x8bbbc4;else -> 0x9ee5ed }
    fun owns(p: CoreCombatMeshPart) = p.shape.startsWith("ice_garden:")
    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart> {
        if (e.phase == CoreSkillVisualPhase.CONTACT) return listOf(
            CoreCombatMeshPart("ice_garden:contact", "ice", Vec(0.0,.08,0.0), Vec(1.0,1.0,1.0),
                durationTicks=10, ground=true))
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
        val coords=p.shape.split(':').drop(2).mapNotNull(String::toIntOrNull)
        val crown=coords.size==2 && crown(coords[0]-2,coords[1]-2)
        val model=when {
            prepare -> "garden/prepare"
            contact -> "garden_bloom/contact_${when { local<3 -> 0;local<7 -> 1;else -> 2 }}"
            local>=live -> "garden_bloom/collapse_${if(!crown || local>=live+7) 2 else if(local<live+3) 0 else 1}"
            !crown -> "garden_bloom/tile_${coords.joinToString("_")}"
            else -> "garden_bloom/tile_${coords.joinToString("_")}_${when { local<4 -> 1;local<8 -> 2;local<live-20 -> 3;local<live-10 -> 4;else -> 5 }}"
        }
        return CoreMeshPose(p.offset,p.scale,p.yaw,p.pitch,p.roll,"combat_vfx/$model",age>=p.delayTicks && local<p.durationTicks)
    }
}
