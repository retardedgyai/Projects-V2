package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Spatial stellar weaving; damage, resource consumption and accepted-hit bursts stay authoritative. */
internal object CoreStarweaverChoreography {
    val sceneIds=setOf("star_thread","star_needle","star_break","star_ring","star_step","star_shield","star_constellation")
    fun parts(e: CoreSkillEffect,raw: List<CoreCombatMeshPart>,life: Int): List<CoreCombatMeshPart> {
        val yaw=atan2(e.direction.x(),e.direction.z())
        fun local(x: Double,y: Double,z: Double)=Vec(cos(yaw)*x+sin(yaw)*z,y,-sin(yaw)*x+cos(yaw)*z)
        val prepare=e.phase==CoreSkillVisualPhase.PREPARE
        val ray=e.skill.motion==CoreSkillMotion.RAY
        if(prepare) return listOf(-1,1).map { side ->
            CoreCombatMeshPart("star_seed","astral",local(side*.55,1.1,.6),Vec(.4,.4,.4),
                yaw=yaw,pitch=-PI/2,travel=local(-side*.35,.15,.1),spin=side*.7,
                durationTicks=life,startSize=.4,endSize=1.0,motion=CoreMeshMotion.GATHER,followOwner=true)
        }
        if(ray) {
            // A clipped ray remains instantaneous. The woven wake dissolves in place;
            // neither a growing segment nor a moving head may extend through a wall.
            val width=when(e.sceneId) { "star_needle" -> .55; "star_break" -> 1.35; else -> .85 }
            val stream=raw.last().copy(shape="weave_ray",scale=Vec(width,width,raw.last().scale.z()),
                durationTicks=life,startSize=1.0,endSize=1.0,secondary=false,erode=false)
            return if(e.sceneId=="star_thread") listOf(stream) else listOf(stream,
                raw.first().copy(shape="weave_spindle",durationTicks=8,startSize=1.0,endSize=1.0,
                    erode=true,secondary=false))
        }
        if(e.sceneId=="star_ring") {
            // Six sweeping cloud crests: centre -> actual gameplay radius in five ticks,
            // followed by breakup. Not an opaque, expanding disc or a second damage wave.
            val r=e.radius
            return (0 until 6).map { i ->
                val a=yaw+i*PI/3
                CoreCombatMeshPart("star_crest",if(i%2==0) "astral" else "ice",
                    Vec(sin(a)*.35,.7,cos(a)*.35),Vec(r*.7,1.0,1.8),yaw=a,pitch=-.65,
                    travel=Vec(sin(a)*(r-1.25).coerceAtLeast(0.0),.0,cos(a)*(r-1.25).coerceAtLeast(0.0)),
                    startSize=.75,endSize=1.0,durationTicks=life,motion=CoreMeshMotion.THRUST,
                    atlas=CoreMeshAtlas.NEBULA_STREAM)
            }
        }
        if(e.sceneId=="star_step") {
            val depart=e.endpoint==CoreSkillEndpoint.DEPARTURE
            return (0 until 4).map { i ->
                val side=if(i%2==0) -1 else 1
                val delay=i%2*2
                CoreCombatMeshPart("star_fold",if(i<2) "astral" else "ice",
                    local(side*(if(depart) .7 else .12),.65+(i/2)*.65,.15),Vec(2.0,1.0,1.1),
                    yaw=yaw+side*.35,pitch=-PI/2,roll=side*.35,
                    travel=local(side*(if(depart) -.6 else .85),if(depart) -.25 else .35,if(depart) -.2 else .2),
                    rollTravel=side*(if(depart) -.9 else 1.1),
                    startSize=if(depart) 1.0 else .35,endSize=if(depart) .15 else 1.0,
                    durationTicks=life-delay,delayTicks=delay,motion=if(depart) CoreMeshMotion.GATHER else CoreMeshMotion.THRUST,
                    atlas=CoreMeshAtlas.NEBULA_STREAM)
            }
        }
        // Two open, articulated chains of stars drape around the shoulders. Each
        // edge includes both endpoint glints, so reduced detail retains the structure.
        val crown=e.sceneId=="star_constellation"
        val edges=listOf(-1,1).flatMap { side ->
            val count=if(crown) 3 else 2
            val nodes=(0..count).map { i ->
                val a=yaw+side*(.75+i*.62)
                val radius=if(crown) 1.25 else .95
                Vec(sin(a)*radius,if(crown) 1.45+(if(i%2==0) .8 else .3) else 1.65-i*.35,cos(a)*radius)
            }
            (0 until count).map { i ->
                CoreCombatMeshPart("constellation_edge","astral",nodes[i+1],Vec(1.0,1.0,1.0),
                    chainAnchor=nodes[i],spin=side*.14,durationTicks=life,
                    startSize=.35,endSize=1.0,followOwner=true,motion=CoreMeshMotion.EMERGE)
            }
        }
        val mantle=listOf(-1,1).flatMap { side -> (0 until if(crown) 2 else 1).map { i ->
            CoreCombatMeshPart("star_mantle_stream",if(i==0) "astral" else "ice",
                local(side*(if(crown) 1.15 else .9),1.15,if(i==0) .3 else -.7),Vec(2.4,1.0,1.35),
                yaw=yaw+side*.25,pitch=-PI/2,roll=side*PI/2,
                travel=local(side*.15,-.3,-.2),rollTravel=-side*.25,
                durationTicks=life,startSize=.55,endSize=1.0,motion=CoreMeshMotion.THRUST,
                atlas=CoreMeshAtlas.NEBULA_STREAM,followOwner=true,secondary=i>0)
        } }
        return edges+mantle
    }

    fun frame(p: CoreCombatMeshPart,age: Double): Int {
        val local=(age-p.delayTicks).coerceAtLeast(0.0)
        return if(local<4) local.toInt() else
            4+floor(((local-4)/(p.durationTicks-5).coerceAtLeast(1)).coerceIn(0.0,1.0)*11).toInt()
    }

    fun pose(p: CoreCombatMeshPart,age: Double): CoreMeshPose? {
        if(p.shape!="constellation_edge") return null
        val local=(age-p.delayTicks).coerceAtLeast(0.0)
        val t=(local/(p.durationTicks-1).coerceAtLeast(1)).coerceIn(0.0,1.0)
        val open=1-(1-(local/5).coerceIn(0.0,1.0)).pow(3)
        val grow=p.startSize+(p.endSize-p.startSize)*open
        val angle=p.spin*t
        fun at(v: Vec)=Vec((cos(angle)*v.x()+sin(angle)*v.z())*grow,v.y(),
            (-sin(angle)*v.x()+cos(angle)*v.z())*grow)
        val from=at(requireNotNull(p.chainAnchor));val to=at(p.offset)
        val delta=to.sub(from);val length=delta.length().coerceAtLeast(.001)
        val segments=ceil(length/.18).toInt().coerceIn(1,16)
        val fade=floor(((t-.6)/.4).coerceIn(0.0,1.0)*7).toInt()
        return CoreMeshPose(from.add(delta.mul(.5)),Vec(1.0,1.0,length),
            atan2(delta.x(),delta.z()),-atan2(delta.y(),hypot(delta.x(),delta.z())),0.0,
            "combat_vfx/weave/link_${segments}_$fade",age>=p.delayTicks && local<p.durationTicks)
    }
}
