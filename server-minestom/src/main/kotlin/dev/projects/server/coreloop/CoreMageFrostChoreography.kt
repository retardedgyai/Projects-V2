package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** R04: an outward crown rooted at the caster, not three translating gardens.
 * All timing here is visual. NOVA hit, radius, startup and slow remain untouched.
 */
internal object CoreMageFrostChoreography {
    fun owns(p:CoreCombatMeshPart)=p.shape.startsWith("mage_material:rime_")

    fun parts(e:CoreSkillEffect):List<CoreCombatMeshPart> {
        val r=min(e.radius,CoreSkillScenes.get(e.sceneId).reach)
        val yaw=atan2(e.direction.x(),e.direction.z())
        fun part(clip:String,height:Double,life:Int,delay:Int=0,turn:Double=0.0):CoreCombatMeshPart {
            // Models are rebased to each bundle's root (native units / 16).
            // The common ground resolver now samples under that root instead
            // of giving every distant bundle the caster's ground elevation.
            val distance=r*when { clip.startsWith("inner_")->3.4/16;clip.startsWith("outer_")->5.225/16;else->0.0 }
            return CoreCombatMeshPart("mage_material:rime_$clip",CoreSkillScenes.get(e.sceneId).palette,
                Vec(sin(yaw+turn)*distance,.12,cos(yaw+turn)*distance),Vec(r,height,r),yaw=yaw+turn,durationTicks=life,
                delayTicks=delay,startSize=1.0,endSize=1.0,ground=true)
        }
        if(e.phase==CoreSkillVisualPhase.PREPARE)
            return listOf(part("footing",.75,e.prepareDuration).copy(followOwner=true))
        // R04: the high tips end before the short roots. One instant impact,
        // not a repeating damage field; these lifetimes change visuals only.
        // Three interleaved inner/outer sectors
        // keep the whole circumference present without a rotating carousel.
        return listOf(part("footing",1.5,40))+listOf("a","b","c").flatMapIndexed { i,suffix ->
            listOf(part("inner_$suffix",2.3,34,i%2,turn=i*2*PI/3+PI/3),
                part("outer_$suffix",3.1,28,2+i%2,turn=i*2*PI/3))
        }
    }

    fun pose(p:CoreCombatMeshPart,age:Double):CoreMeshPose {
        val clip=p.shape.substringAfter(':')
        val local=(age-p.delayTicks).coerceAtLeast(0.0)
        fun ease(v:Double):Double { val u=v.coerceIn(0.0,1.0);return u*u*(3-2*u) }
        val footing=clip=="rime_footing"
        val grow=ease(local/if(footing)2.0 else 3.0)
        // Keep x/z roots fixed: height rises from the earth instead of the
        // whole finished crystal sliding horizontally away from the caster.
        val end=(p.durationTicks-1).coerceAtLeast(1).toDouble()
        val fade=if(footing)6.0 else 8.0
        val close=ease((local-(end-fade).coerceAtLeast(0.0))/fade)
        val scale=if(local>=p.durationTicks-1.0)Vec.ZERO else
            p.scale.mul(Vec(1.0,.018+.982*grow,1.0)).mul(Vec(1.0,1-close,1.0))
        return CoreMeshPose(p.offset.add(0.0,-.14,0.0),scale,p.yaw,p.pitch,p.roll,
            "combat_vfx/mage_material/${clip}_0",age>=p.delayTicks && age<p.delayTicks+p.durationTicks)
    }
}
