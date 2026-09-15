package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** R09: compact hot head, thin directed wake, separate confirmed-contact chips.
 * Cosmetic only: the existing clipped instantaneous ray remains authoritative.
 */
internal object CoreMageFireboltChoreography {
    fun owns(p:CoreCombatMeshPart)=p.shape.startsWith("mage_material:solar_bolt_")

    fun parts(e:CoreSkillEffect):List<CoreCombatMeshPart> {
        val direction=if(e.direction.lengthSquared()>1e-8)e.direction.normalize() else Vec(0.0,0.0,1.0)
        val yaw=atan2(direction.x(),direction.z())
        val pitch=-atan2(direction.y(),hypot(direction.x(),direction.z()))
        fun part(clip:String,offset:Vec,scale:Vec,life:Int,travel:Vec=Vec.ZERO,follow:Boolean=false)=
            CoreCombatMeshPart("mage_material:solar_bolt_$clip",CoreSkillScenes.get(e.sceneId).palette,
                offset,scale,yaw=yaw,pitch=pitch,durationTicks=life,startSize=1.0,endSize=1.0,
                travel=travel,followOwner=follow)
        if(e.phase==CoreSkillVisualPhase.PREPARE) return listOf(
            part("charge",Vec(cos(yaw)*.38,1.2,-sin(yaw)*.38).add(direction.mul(.55)),
                Vec(.5,.5,.6),e.prepareDuration,follow=true))
        if(e.phase==CoreSkillVisualPhase.CONTACT) {
            // These exist only at real contact events, not at every ray end.
            return listOf(Vec(-.6,.7,-.15),Vec(.55,.28,-.3),Vec(.10,-.25,-.2)).mapIndexed { i,v ->
                val travel=Vec(cos(yaw)*v.x()+sin(yaw)*v.z(),v.y(),-sin(yaw)*v.x()+cos(yaw)*v.z())
                part("chip",Vec(0.0,1.0,0.0),Vec(.22-i*.04,.22-i*.04,.32-i*.05),7+i,travel)
                    .copy(roll=(i-1)*.7)
            }
        }
        if(e.length<=.05) return emptyList()
        val length=min(e.length,.85)
        val head=direction.mul(e.length-length*.5)
        // Head is already at the resolved ray end on tick zero. A slow visual
        // projectile would contradict immediate damage and is not introduced.
        return listOf(
            part("core",head,Vec(1.15,1.15,length),6),
            part("shell",head,Vec(1.15,1.15,length),8),
            part("wake",direction.mul(e.length*.5),Vec(1.0,1.0,e.length),7),
            part("thread",direction.mul(e.length*.5),Vec(1.0,1.0,e.length),9))
    }

    fun pose(p:CoreCombatMeshPart,age:Double):CoreMeshPose {
        val clip=p.shape.substringAfter(':')
        val local=(age-p.delayTicks).coerceAtLeast(0.0)
        val end=(p.durationTicks-1).coerceAtLeast(1).toDouble()
        fun ease(v:Double):Double { val u=v.coerceIn(0.0,1.0);return u*u*(3-2*u) }
        val direction=Vec(sin(p.yaw)*cos(p.pitch),-sin(p.pitch),cos(p.yaw)*cos(p.pitch))
        val terminal=local>=p.durationTicks-1.0
        var offset=p.offset
        var scale=p.scale
        when(clip) {
            "solar_bolt_charge" -> {
                val grow=.25+.75*ease(local/3.0)
                scale=scale.mul(grow*(1-ease((local-(end-2).coerceAtLeast(0.0))/2.0)))
            }
            "solar_bolt_wake","solar_bolt_thread" -> {
                val cut=ease(local/end)
                // Rear catches up with the accepted endpoint: no wall crossing,
                // rotation loop or new whole-image playback per tick.
                offset=offset.add(direction.mul(p.scale.z()*.5*cut))
                scale=scale.mul(Vec(1-cut,1-cut,1-cut))
            }
            "solar_bolt_chip" -> {
                val u=ease(local/end)
                offset=offset.add(p.travel.mul(u)).add(0.0,-.10*u*u,0.0)
                scale=scale.mul(1-ease((local-2)/(end-2).coerceAtLeast(1.0)))
            }
            else -> {
                val fade=ease((local-1.0)/(end-1))
                // Core dies first; the darker rear shell lingers two ticks.
                // Keep the forward face fixed while the rear contracts.
                offset=offset.add(direction.mul(p.scale.z()*.5*fade))
                scale=scale.mul(1-fade)
            }
        }
        if(terminal)scale=Vec.ZERO
        return CoreMeshPose(offset,scale,p.yaw,p.pitch,p.roll,"combat_vfx/mage_material/${clip}_0",
            age>=p.delayTicks && age<p.delayTicks+p.durationTicks)
    }
}
