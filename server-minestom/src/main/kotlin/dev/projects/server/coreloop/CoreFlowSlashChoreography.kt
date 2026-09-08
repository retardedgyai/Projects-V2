package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Seven concrete slashes: persistent short surfaces, not swapped whole-stroke meshes.
 * The existing one-tick ItemDisplay interpolation connects their transforms on the client.
 */
internal object CoreFlowSlashChoreography {
    private data class Path(val angles: List<Double>, val radii: List<Double>, val center: Double,
        val ellipse: Double, val width: Double, val color: String)
    private val paths=mapOf(
        "dash" to Path(listOf(-1.24,-1.16,-.84,-.12,.65,1.13,1.30),listOf(5.2,5.3,5.65,6.2,6.35,6.2,6.1),6.2,.72,4.6,"steel"),
        "wound" to Path(listOf(-1.1,-1.05,-.87,-.3,.5,.91,1.03),listOf(4.8,4.9,5.0,5.3,5.5,5.3,5.1),6.5,.60,3.0,"steel"),
        "counter" to Path(listOf(1.35,1.30,1.12,.35,-.55,-1.08,-1.32),listOf(5.1,5.2,5.6,6.3,6.2,5.9,5.7),6.3,.72,3.7,"gold"),
        "fall" to Path(listOf(-1.45,-1.40,-1.25,-.48,.6,1.2,1.42),listOf(5.7,5.8,6.0,6.3,6.2,5.7,5.2),6.5,.65,4.5,"steel"),
        "orbit" to Path(listOf(0.0,.035,.12,1.9,3.8,5.55,2*PI),listOf(5.5,5.6,5.7,6.0,6.0,5.8,5.6),8.0,1.0,2.3,"steel"),
        "execute" to Path(listOf(-1.3,-1.26,-1.05,-.35,.55,1.03,1.2),listOf(4.8,4.9,5.0,5.6,5.5,5.2,4.9),6.3,.62,3.5,"shadow"),
        "execute_return" to Path(listOf(1.2,1.16,.96,.25,-.55,-1.08,-1.3),listOf(4.6,4.7,4.8,5.4,5.6,5.2,4.9),6.3,.62,2.7,"shadow"),
        "fan" to Path(listOf(0.0,.04,.13,2.05,3.7,5.5,2*PI),listOf(4.7,4.8,4.9,5.4,5.3,5.1,4.9),8.0,1.0,1.5,"shadow"),
        "fan_return" to Path(listOf(0.0,-.04,-.13,-2.05,-3.7,-5.5,-2*PI),listOf(4.9,5.0,5.1,5.5,5.4,5.2,5.0),8.0,1.0,1.5,"shadow"))

    fun parts(e: CoreSkillEffect, blades: List<CoreCombatMeshPart>): List<CoreCombatMeshPart> = blades.flatMap { blade ->
        val profile=if(e.sceneId=="dash") "dash" else blade.shape.split(':')[1]
        val prepare=e.phase==CoreSkillVisualPhase.PREPARE
        val count=if(e.sceneId in setOf("whirl","ass_fan")) 8 else 4
        val cut=(0 until count).map { i -> blade.copy(shape="flow:$profile:${if(prepare) "prepare" else "cut"}:$i:$count",
            durationTicks=if(prepare) e.prepareDuration else 8,secondary=false) }
        if(prepare) cut else cut+(0..3).map { i -> blade.copy(shape="flow:$profile:wake:$i:4",durationTicks=13,secondary=true) }
    }

    // Monotone cubic tangents remove speed corners without overshooting the authored arc.
    private fun sample(values: List<Double>, time: Double): Double {
        val t=time.coerceIn(0.0,6.0);val i=floor(t).toInt().coerceAtMost(5);val u=t-i
        fun tangent(k: Int): Double {
            if(k==0) return values[1]-values[0]
            if(k==6) return values[6]-values[5]
            val a=values[k]-values[k-1];val b=values[k+1]-values[k]
            return if(a*b<=0) 0.0 else 2*a*b/(a+b)
        }
        return (2*u*u*u-3*u*u+1)*values[i]+(u*u*u-2*u*u+u)*tangent(i)+
            (-2*u*u*u+3*u*u)*values[i+1]+(u*u*u-u*u)*tangent(i+1)
    }

    private fun rotate(v: Vec,p: CoreCombatMeshPart): Vec {
        val y=v.y()*cos(p.pitch)-v.z()*sin(p.pitch);val z=v.y()*sin(p.pitch)+v.z()*cos(p.pitch)
        val x=v.x()*cos(p.roll)-y*sin(p.roll)
        return Vec(x*cos(p.yaw)+z*sin(p.yaw),v.x()*sin(p.roll)+y*cos(p.roll),-x*sin(p.yaw)+z*cos(p.yaw))
    }
    private fun cross(a: Vec,b: Vec)=Vec(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x())
    private fun dot(a: Vec,b: Vec)=a.x()*b.x()+a.y()*b.y()+a.z()*b.z()

    fun pose(p: CoreCombatMeshPart,age: Double): CoreMeshPose? {
        if(!p.shape.startsWith("flow:")) return null
        val keys=p.shape.split(':');val path=paths.getValue(keys[1]);val layer=keys[2];val section=keys[3].toInt();val count=keys[4].toInt()
        val t=((age-p.delayTicks)/(p.durationTicks-1).coerceAtLeast(1)).coerceIn(0.0,1.0)
        val wake=layer=="wake"
        // PREPARE ends exactly where PULSE begins. No skipped 4 -> 6 -> 7 frames.
        val head=when(layer) { "prepare" -> 2*t; "cut" -> 2+7*t; else -> .7+9.3*t }
        val span=if(wake) .6 else 2.88/count
        val front=(head-section*span-(if(wake) .9 else 0.0)).coerceIn(0.0,6.0)
        val back=(head-(section+1)*span-(if(wake) .9 else 0.0)).coerceIn(0.0,6.0)
        fun point(birth: Double): Vec {
            val a=sample(path.angles,birth);val r=sample(path.radii,birth)
            val x=sin(a)*r;val z=path.center+cos(a)*r*path.ellipse-8
            // Continuous shallow curl: no re-quantized pixel rows between poses.
            val y=-.6*sin(z*.45)+(if(wake) t*.55 else 0.0)
            return rotate(Vec(x/16*p.scale.x(),y/16*p.scale.y(),z/16*p.scale.z()),p).add(p.offset)
        }
        val a=point(back);val b=point(front);val delta=b.sub(a);val length=delta.length()
        val tangent=if(length>1e-7) delta.div(length) else rotate(Vec(0.0,0.0,1.0),p)
        val plane=rotate(Vec(0.0,1.0,0.0),p)
        var normal=cross(plane,tangent).normalize()
        val middleAngle=sample(path.angles,(front+back)*.5)
        val outward=rotate(Vec(sin(middleAngle)*p.scale.x(),0.0,cos(middleAngle)*path.ellipse*p.scale.z()),p)
        if(dot(normal,outward)<0) normal=normal.mul(-1.0)
        val binormal=cross(tangent,normal).normalize()
        val mid=(front+back)*.5
        val body=sin(PI*mid/6).pow(2)
        val width=if(wake) p.scale.x()/16*.28*(1-t) else
            p.scale.x()/16*(.12+body*path.width*.70)
        val fade=if(layer=="prepare") 1.0 else (1-(t-.72).coerceAtLeast(0.0)/.28).coerceIn(0.0,1.0)
        // Move the center inside the cutting edge. Adjacent short planes overlap at
        // their joins; only their endpoints follow the blade, never a whole PNG.
        val center=a.add(b).mul(.5).sub(normal.mul(width*.40))
        val roll=asin(normal.y().coerceIn(-1.0,1.0))
        val yaw=atan2(-normal.z(),normal.x())
        val pitch=atan2(-tangent.y(),binormal.y())
        return CoreMeshPose(center,Vec(width*fade,1.0,if(length>1e-7) length*1.12 else 0.0),yaw,pitch,roll,
            "combat_vfx/flow/${path.color}_${when { wake -> "wake"; section==0 -> "tip"; section==count-1 -> "tail"; else -> "cut" }}",
            age>=p.delayTicks && age<p.delayTicks+p.durationTicks)
    }
}
