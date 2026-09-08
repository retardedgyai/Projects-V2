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
        val height: Double,val width: Double,val ticks: Int,val accent: Boolean=false,val vertical: Boolean=false)

    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart>? {
        if(e.job!=CoreClass.WARRIOR || e.sceneId=="dash" || e.sceneId !in sceneIds || !e.valid) return null
        val yaw=atan2(e.direction.x(),e.direction.z())
        val heavy=e.sceneId in setOf("normal_finish","slam","war_ult")
        if(e.phase==CoreSkillVisualPhase.CONTACT) return listOf(CoreCombatMeshPart("warrior_impact","warred",
            Vec(0.0,1.0,0.0),Vec(if(heavy) 1.9 else 1.1,1.0,if(heavy) 1.9 else 1.1),
            yaw=yaw,pitch=-PI/2,durationTicks=6))
        if(e.phase==CoreSkillVisualPhase.PREPARE) {
            // Anticipation belongs near the grip, not a second full slash before the hit.
            return listOf(CoreCombatMeshPart("warrior_charge",if(heavy) "warred" else "warsteel",
                Vec(sin(yaw)*.8,1.0,cos(yaw)*.8),Vec(.12,1.0,.55),yaw=yaw,
                pitch=-.6,roll=.35,durationTicks=e.prepareDuration))
        }
        val reach=min(e.radius,CoreSkillScenes.get(e.sceneId).reach)
        val r=reach.coerceAtMost(4.5)*.79
        val s=when(e.sceneId) {
            "normal_sweep" -> Stroke(-1.05,1.0,-.12,r*.84,1.12,1.05,3)
            "normal_reverse" -> Stroke(1.0,-1.05,.22,r*.89,1.18,1.18,3)
            "normal_finish" -> Stroke(-1.10,1.0,0.0,r*.86,1.4,1.42,4,accent=true,vertical=true)
            "war_wound" -> Stroke(-.72,.65,-.3,r*.72,1.15,.86,3)
            "war_counter" -> Stroke(1.15,-1.1,.28,r,1.25,1.45,4,accent=true)
            "slam" -> Stroke(-1.15,1.03,0.0,r,1.5,1.65,4,accent=true,vertical=true)
            "whirl" -> when(e.pulse%3) {
                0 -> Stroke(-PI,PI,0.0,r*.84,1.0,1.05,6)
                1 -> Stroke(-PI,PI,.12,r*.93,1.2,1.20,6)
                else -> Stroke(-PI,PI,-.10,r,1.05,1.42,6,accent=true)
            }
            "war_ult" -> when(e.pulse%3) {
                0 -> Stroke(-1.0,.95,-.30,r*.80,1.3,1.35,4)
                1 -> Stroke(1.08,-1.1,.30,r*.9,1.35,1.50,4)
                else -> Stroke(-1.18,1.05,0.0,r,1.6,1.85,4,accent=true,vertical=true)
            }
            "war_breach" -> Stroke(0.0,0.0,0.0,r,1.2,.82,3)
            else -> Stroke(-1.16,1.02,-.20,r,1.18,1.40,4)
        }
        val count=8
        fun world(v: Vec)=Vec(cos(yaw)*v.x()+sin(yaw)*v.z(),v.y(),-sin(yaw)*v.x()+cos(yaw)*v.z())
        fun point(t: Double): Vec {
            if(e.sceneId=="war_breach") return world(Vec(0.0,s.height,.55+t*s.radius))
            val a=s.from+(s.to-s.from)*t
            if(s.vertical) return world(Vec(.45*(cos(a)-.65)*s.radius,
                (s.height-sin(a)*s.radius*.48).coerceAtLeast(.75),.10+.75*cos(a)*s.radius))
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
            var normal=cross(plane,tangent).normalize()
            val mid=a.add(b).mul(.5)
            // The white cutting edge is the OUTSIDE of the stroke in both directions.
            // Previously reverse traversal inverted this basis and exposed the dark rim.
            if(!s.vertical && e.sceneId!="war_breach" && normal.x()*mid.x()+normal.z()*mid.z()<0)
                normal=normal.mul(-1.0)
            val binormal=cross(tangent,normal).normalize()
            var center=mid.sub(normal.mul(s.width*.28))
            var width=s.width
            var length=delta.length()*1.22
            // Bound the entire widened surface, not just its centreline. This is
            // display sizing only: the authoritative hit/reach values stay untouched.
            val edgeRadius=listOf(-.5,.5).flatMap { x -> listOf(-.5,.5).map { z ->
                val corner=center.add(normal.mul(width*x)).add(tangent.mul(length*z))
                hypot(corner.x(),corner.z())
            } }.max()
            val fit=min(1.0,(reach-.02)/edgeRadius.coerceAtLeast(.01))
            center=Vec(center.x()*fit,center.y(),center.z()*fit)
            width*=fit;length*=fit
            val floorClearance=.08+abs(normal.y())*width*.5+abs(tangent.y())*length*.5
            center=Vec(center.x(),max(center.y(),floorClearance),center.z())
            CoreCombatMeshPart("warrior_trace:${if(i==0) "tail" else if(i==count-1) "tip" else "cut"}",
                if(s.accent) "warred" else "warsteel",center,
                Vec(width,1.0,length),yaw=atan2(-normal.z(),normal.x()),
                pitch=atan2(-tangent.y(),binormal.y()),roll=asin(normal.y().coerceIn(-1.0,1.0)),
                delayTicks=(i.toDouble()/count*s.ticks).toInt(),durationTicks=8)
        }
    }

    fun pose(p: CoreCombatMeshPart,age: Double): CoreMeshPose? {
        if(p.shape=="warrior_impact") {
            val pose=CoreGreatswordSweepChoreography.pose(p.copy(shape="greatsword_impact"),age)!!
            return pose.copy(model=pose.model.replace("combat_vfx/greatsword/", "combat_vfx/warrior_blade/"))
        }
        if(!p.shape.startsWith("warrior_trace:") && p.shape!="warrior_charge") return null
        val t=(age-p.delayTicks).coerceAtLeast(0.0)
        val charge=p.shape=="warrior_charge"
        // The position and direction never change: a wake cannot chase the blade.
        // Each short segment appears at its traversal time, then thins in place.
        val width=if(charge) .4+.6*(t/(p.durationTicks-1).coerceAtLeast(1)).coerceIn(0.0,1.0)
            // Adjacent sections coexist through the apex. A 1-tick peak made the
            // in-game picture a lone matchstick instead of the complete cut surface.
            else when { t<1 -> t; t<4 -> 1.0; else -> (1-(t-4)/3).coerceIn(0.0,1.0) }
        val layer=if(charge) "tip" else p.shape.substringAfter(':')
        return CoreMeshPose(p.offset,Vec(p.scale.x()*width,p.scale.y(),p.scale.z()),p.yaw,p.pitch,p.roll,
            "combat_vfx/warrior_blade/${p.palette}_$layer",age>=p.delayTicks && age<p.delayTicks+p.durationTicks)
    }
}
