package dev.projects.server.coreloop

import dev.projects.server.particle.*
import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Packed scenes use small secondary motes, not the old competing giant particle silhouettes. */
internal object CoreSceneParticles {
    private val colors=mapOf("steel" to 0xdde7ed,"gold" to 0xffcd59,"astral" to 0xb677ef,"ice" to 0x75d8f5,
        "fire" to 0xff701d,"venom" to 0x88c92b,"life" to 0x95edc5,"shadow" to 0x8752b0,
        "hunter" to 0xc9db8b,"holy" to 0xffe5a0,"lightning" to 0x91bcff)
    fun emit(e: CoreSkillEffect,tick: Int,sink: ParticleSink) {
        val scene=CoreSkillScenes.get(e.sceneId)
        val color=colors.getValue(scene.palette)
        val t=tick.toDouble()/e.durationTicks
        val origin=Vec(e.origin.x(),e.origin.y(),e.origin.z())
        fun mote(at: Vec,size: Float=.65f,key: Boolean=false) {
            sink.spawn(ParticleSpawn(dustTransition(if(key) 0xfff4db else color,color,size),at,
                category=ParticleCategory.OWN_ACTIVE,importance=if(key) ParticleImportance.COMBAT_FEEDBACK else ParticleImportance.COSMETIC))
        }
        val parts=CoreCombatMeshArt.parts(e)
        for((index,part) in parts.take(5).withIndex()) {
            val center=origin.add(part.offset).add(part.travel.mul(t))
            repeat(if(e.phase==CoreSkillVisualPhase.CONTACT) 6 else 2) { i ->
                val a=i*2.4+index*1.7+tick*.5
                var at=center.add(cos(a)*.12,.08+sin(a)*.12,sin(a)*.12)
                if(e.clippedRay && e.direction.lengthSquared()>1e-8) {
                    val direction=e.direction.normalize()
                    val delta=at.sub(origin)
                    val along=delta.x()*direction.x()+delta.y()*direction.y()+delta.z()*direction.z()
                    at=at.add(direction.mul(along.coerceIn(0.0,e.length)-along))
                }
                mote(at,if(e.phase==CoreSkillVisualPhase.CONTACT) .9f else .6f,tick==0 && e.phase==CoreSkillVisualPhase.CONTACT)
            }
        }
        // Sparse ground reach marks are deliberately separate from the weapon's physical size.
        if(tick==0 && e.phase==CoreSkillVisualPhase.PULSE && e.skill.area && e.radius>1.5 &&
            scene.kind !in setOf(CoreSceneKind.TELEPORT,CoreSceneKind.GUARD,CoreSceneKind.AFTERIMAGE)) {
            val cone=e.skill.motion in setOf(CoreSkillMotion.CONE,CoreSkillMotion.LUNGE)
            val yaw=atan2(e.direction.x(),e.direction.z())
            repeat(14) { i ->
                val a=if(cone) yaw-acos(.35)+i*2*acos(.35)/13 else i*PI*2/14
                mote(origin.add(sin(a)*e.radius,.1,cos(a)*e.radius),.45f)
            }
        }
    }
}
