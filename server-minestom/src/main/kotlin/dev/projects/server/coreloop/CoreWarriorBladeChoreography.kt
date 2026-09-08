package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Warrior only: the cutting edge passes; its short wake stays where it was cut.
 * No whole-stroke rotation, model-frame swaps, or autonomous extra damage beats.
 */
internal object CoreWarriorBladeChoreography {
    val sceneIds=setOf("normal_sweep","normal_reverse","normal_finish","dash","war_wound",
        "war_counter","slam","whirl","war_breach","war_ult")
    private data class Stroke(val from: Double,val to: Double,val tilt: Double,val radius: Double,
        val height: Double,val width: Double,val ticks: Int,val gold: Boolean=false,val vertical: Boolean=false)

    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart>? {
        if(e.job!=CoreClass.WARRIOR || e.sceneId !in sceneIds || !e.valid) return null
        val yaw=atan2(e.direction.x(),e.direction.z())
        val heavy=e.sceneId in setOf("normal_finish","slam","war_ult")
        if(e.phase==CoreSkillVisualPhase.CONTACT) return listOf(CoreCombatMeshPart("greatsword_impact","gold",
            Vec(0.0,1.0,0.0),Vec(if(heavy) 1.9 else 1.1,1.0,if(heavy) 1.9 else 1.1),
            yaw=yaw,pitch=-PI/2,durationTicks=6))
        if(e.phase==CoreSkillVisualPhase.PREPARE) {
            // Anticipation belongs near the grip, not a second full slash before the hit.
            return listOf(CoreCombatMeshPart("warrior_charge",if(heavy) "gold" else "steel",
                Vec(sin(yaw)*.8,1.0,cos(yaw)*.8),Vec(.12,1.0,.55),yaw=yaw,
                pitch=-.6,roll=.35,durationTicks=e.prepareDuration))
        }
        val reach=min(e.radius,CoreSkillScenes.get(e.sceneId).reach)
        val r=reach.coerceAtMost(4.5)*.79
        val s=when(e.sceneId) {
            "normal_sweep" -> Stroke(-1.05,1.0,-.12,r*.84,1.05,.36,5)
            "normal_reverse" -> Stroke(1.0,-1.05,.22,r*.89,1.12,.40,5)
            "normal_finish" -> Stroke(-1.10,1.0,0.0,r*.86,1.3,.56,6,vertical=true)
            "war_wound" -> Stroke(-.72,.65,-.3,r*.72,1.0,.25,3)
            "war_counter" -> Stroke(1.15,-1.1,.28,r,1.12,.60,5,gold=true)
            "slam" -> Stroke(-1.15,1.03,0.0,r,1.4,.72,6,gold=true,vertical=true)
            "whirl" -> when(e.pulse%3) {
                0 -> Stroke(-PI,PI,0.0,r*.84,.9,.36,8)
                1 -> Stroke(-PI,PI,.12,r*.93,1.15,.45,8)
                else -> Stroke(-PI,PI,-.10,r,.82,.58,8,gold=true)
            }
            "war_ult" -> when(e.pulse%3) {
                0 -> Stroke(-1.0,.95,-.30,r*.80,1.15,.55,6)
                1 -> Stroke(1.08,-1.1,.30,r*.9,1.3,.65,6)
                else -> Stroke(-1.18,1.05,0.0,r,1.5,.85,7,gold=true,vertical=true)
            }
            "war_breach" -> Stroke(0.0,0.0,0.0,r,1.1,.30,5)
            else -> Stroke(-1.16,1.02,-.20,r,1.02,.48,5)
        }
        val count=8
        fun world(v: Vec)=Vec(cos(yaw)*v.x()+sin(yaw)*v.z(),v.y(),-sin(yaw)*v.x()+cos(yaw)*v.z())
        fun point(t: Double): Vec {
            if(e.sceneId=="war_breach") return world(Vec(0.0,s.height,.55+t*s.radius))
            val a=s.from+(s.to-s.from)*t
            if(s.vertical) return world(Vec(.45*(cos(a)-.65)*s.radius,
                (s.height-sin(a)*s.radius*.48).coerceAtLeast(.45),.30+.9*cos(a)*s.radius))
            return world(Vec(sin(a)*s.radius,s.height+sin(a)*s.tilt,cos(a)*s.radius))
        }
        return (0 until count).map { i ->
            val a=point(i.toDouble()/count);val b=point((i+1.0)/count)
            val delta=b.sub(a);val tangent=delta.normalize()
            val plane=when { s.vertical -> world(Vec(.9,0.0,-.45).normalize())
                e.sceneId=="war_breach" -> world(Vec(1.0,1.0,0.0).normalize())
                e.sceneId=="whirl" -> Vec(0.0,1.0,0.0)
                // A shallow curl across the wake exposes its surface from eye height.
                // Fixed cast-space orientation, never a camera-facing billboard.
                else -> world(Vec(0.0,1.0,-.65).normalize()) }
            fun cross(x: Vec,y: Vec)=Vec(x.y()*y.z()-x.z()*y.y(),x.z()*y.x()-x.x()*y.z(),x.x()*y.y()-x.y()*y.x())
            val normal=cross(plane,tangent).normalize();val binormal=cross(tangent,normal).normalize()
            CoreCombatMeshPart("warrior_trace:${if(i==0) "tail" else if(i==count-1) "tip" else "cut"}",
                if(s.gold) "gold" else "steel",a.add(b).mul(.5),
                Vec(s.width,1.0,delta.length()*1.13),yaw=atan2(-normal.z(),normal.x()),
                pitch=atan2(-tangent.y(),binormal.y()),roll=asin(normal.y().coerceIn(-1.0,1.0)),
                delayTicks=(i.toDouble()/count*s.ticks).toInt(),durationTicks=5)
        }
    }

    fun pose(p: CoreCombatMeshPart,age: Double): CoreMeshPose? {
        if(!p.shape.startsWith("warrior_trace:") && p.shape!="warrior_charge") return null
        val t=(age-p.delayTicks).coerceAtLeast(0.0)
        val charge=p.shape=="warrior_charge"
        // The position and direction never change: a wake cannot chase the blade.
        // Each short segment appears at its traversal time, then thins in place.
        val width=if(charge) .4+.6*(t/(p.durationTicks-1).coerceAtLeast(1)).coerceIn(0.0,1.0)
            else when { t<1 -> t; t<2 -> 1.0; else -> (1-(t-2)/2).coerceIn(0.0,1.0) }
        val layer=if(charge) "tip" else p.shape.substringAfter(':')
        return CoreMeshPose(p.offset,Vec(p.scale.x()*width,p.scale.y(),p.scale.z()),p.yaw,p.pitch,p.roll,
            "combat_vfx/flow/${p.palette}_$layer",age>=p.delayTicks && age<p.delayTicks+p.durationTicks)
    }
}
