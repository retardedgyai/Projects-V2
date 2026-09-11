package dev.projects.server.coreloop

import dev.projects.server.particle.*
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.block.Block
import net.minestom.server.particle.Particle
import kotlin.math.*

/** Secondary motion around the approved models. No damage, terrain edits or extra hits. */
internal object CoreWarriorParticles {
    fun emit(e:CoreSkillEffect,tick:Int,sink:ParticleSink) {
        if(!e.valid || tick !in 0 until e.durationTicks) return
        val yaw=atan2(e.direction.x(),e.direction.z())
        val origin=Vec(e.origin.x(),e.origin.y(),e.origin.z())
        fun at(x:Double,y:Double,z:Double)=origin.add(cos(yaw)*x+sin(yaw)*z,y,-sin(yaw)*x+cos(yaw)*z)
        fun dust(x:Double,y:Double,z:Double,color:Int=0xdce8ef,size:Float=.85f,key:Boolean=false) {
            sink.spawn(ParticleSpawn(dustTransition(color,if(color==0xc74155)0x582736 else 0x627481,size),at(x,y,z),
                category=ParticleCategory.OWN_ACTIVE,importance=if(key)ParticleImportance.COMBAT_FEEDBACK else ParticleImportance.COSMETIC))
        }
        fun particle(p:Particle,x:Double,y:Double,z:Double) {
            sink.spawn(ParticleSpawn(p,at(x,y,z),category=ParticleCategory.OWN_ACTIVE))
        }
        val support=e.sceneId in CoreWarriorSupportChoreography.sceneIds
        if(e.phase==CoreSkillVisualPhase.CONTACT) {
            if(support && e.sceneId!="war_guard") return
            // Only emitted after a server-accepted hit / block; never on a miss.
            if(tick>5) return
            repeat(12) { i ->
                val a=i*PI/6+.2
                val r=.12+tick*.17
                dust(cos(a)*r,1+sin(a)*r,.07* sin(i*2.0),if(i%3==0)0xc74155 else 0xf4f8fa, .8f,tick<2)
                if(tick==0 && i%2==0) particle(Particle.CRIT,cos(a)*.15,1+sin(a)*.15,0.0)
            }
            return
        }
        if(e.phase==CoreSkillVisualPhase.PREPARE) {
            if(e.sceneId in CoreApprovedNormalV3.sceneIds) return
            if(tick%2!=0 || e.sceneId=="war_banner") return
            val t=tick.toDouble()/e.prepareDuration
            repeat(6) { i ->
                val a=i*PI/3
                val r=(1-t)*.8+.12
                dust(cos(a)*r,.6+t*.6,sin(a)*r+.4,0xbacbd6,.65f)
            }
            return
        }
        if(e.sceneId=="war_banner" || e.sceneId=="war_cry") {
            // A single grant at cast time, NOT an aura accepting later entrants.
            // The exact radius flashes, then the spent boundary disperses quickly.
            if(tick<=6 && tick%2==0) repeat(40) { i ->
                val a=i*PI/20
                dust(sin(a)*e.radius,.12,cos(a)*e.radius,if(i%5==0)0xc74155 else 0xe1edf2,
                    (1.1-tick*.09).toFloat(),tick==0)
            }
            if(tick in 1..11 && tick%2==1) repeat(12) { i ->
                val a=i*PI/6
                val r=e.radius*(.15+tick*.07).coerceAtMost(.95)
                dust(sin(a)*r,.18+sin(tick*PI/12)*.3,cos(a)*r,0x9aabae,.75f)
            }
            return
        }
        if(e.sceneId=="war_guard") {
            if(tick<8) repeat(8) { i -> dust(-.42+i*.12,.8+tick*.09,.65,0xdce8ef,.65f) }
            return
        }
        val ground=e.sceneId=="slam" || e.sceneId=="war_ult" && e.pulse%3==2
        if(ground) {
            val t=tick.toDouble()
            // Forward-moving fracture with a low dust skirt; delayed ballistic debris.
            if(tick<14 && tick%2==0) repeat(24) { i ->
                val angle=-.9+i*1.8/23
                val r=e.radius*(.15+tick*.065).coerceAtMost(.95)
                dust(sin(angle)*r,.14+tick*.012,cos(angle)*r,0xa89d8b,1.25f)
            }
            if(tick<18 && tick%2==0) repeat(12) { i ->
                val side=sin(i*2.4)
                val z=e.radius*(.35+i%4*.1)+t*.025
                val y=.2+(.13+i%3*.018)*t-.008*t*t
                if(y>.08) particle(Particle.BLOCK.withBlock(Block.STONE),side*(.2+t*.05),y,z)
            }
            if(tick<5) repeat(12) { i ->
                val a=i*2.4
                dust(cos(a)*(.1+tick*.22),.3+abs(sin(a))*(.2+tick*.17),e.radius*.55,
                    if(i%3==0)0xc74155 else 0xf0f4f7,1f,tick==0)
            }
        } else if(e.sceneId=="war_breach" || e.sceneId=="dash") {
            if(tick<14) repeat(24) { i ->
                val lane=i%4
                val progress=(tick*.1+i/4*.055).coerceAtMost(1.0)
                val spread=.12+progress*progress*.75
                dust((if(lane%2==0)-1 else 1)*spread,.75+lane/2*.4,
                    e.radius*progress,if(i%7==0)0xc74155 else 0xc9dbe5,.7f)
            }
            if(tick<8 && tick%2==0) repeat(8) { i -> particle(Particle.CLOUD,(i%2*2-1)*.35,.12,e.radius*i/10) }
        } else if(e.sceneId=="whirl") {
            if(tick<14) repeat(24) { i ->
                val a=i*PI/12+tick*.22+e.pulse*.5
                val r=e.radius*(.45+tick*.032).coerceAtMost(.92)
                dust(sin(a)*r,.15+sin(i*.8+tick*.25).let { abs(it) }*(.15+e.pulse*.1),cos(a)*r,
                    if(i%6==0)0xc74155 else 0xaaa394,.8f)
            }
            if(e.pulse%3==2 && tick in 2..10 && tick%2==0) repeat(12) { i ->
                val a=i*PI/6
                particle(Particle.BLOCK.withBlock(Block.STONE),sin(a)*e.radius*.8,.2+sin(tick*PI/12)*.7,cos(a)*e.radius*.8)
            }
        } else {
            // Cuts displace air outwards; the return stroke reverses that direction.
            if(tick<12) repeat(18) { i ->
                val u=i/17.0
                val a=-1.05+u*2.1
                val r=e.radius*(.45+tick*.032)
                val reverse=e.sceneId=="war_counter" || e.sceneId=="normal_reverse" || e.sceneId=="war_ult" && e.pulse%3==1
                dust(sin(a)*r*(if(reverse)-1 else 1),.65+u*.9+tick*.025,cos(a)*r,
                    if(i%6==0)0xc74155 else 0xc4d5df,.75f)
            }
        }
    }
}
