package dev.projects.server.coreloop

import dev.projects.server.particle.*
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Authored class VFX consumer. Uses the existing scheduler, distance falloff and shared scene budget. */
internal class CoreClassEffect(private val job: CoreClass, private val motion: CoreSkillMotion,
    origin: Point, direction: Vec, radius: Double, private val ultimate: Boolean,
    private val reverse: Boolean = false, element: Int = 0) : ParticleEffect {
    private val transform = ParticleTransform.fromDirection(origin, direction)
    private val radius = radius.coerceIn(.3, 10.5)
    private val color = when(job) {
        CoreClass.WARRIOR -> 0xffc585
        CoreClass.RANGER -> 0x9ee6b3
        CoreClass.ASSASSIN -> 0xec9cbf
        CoreClass.TEMPLAR -> 0xffe4a0
        CoreClass.HEALER -> 0xa6f1d3
        CoreClass.STARWEAVER -> 0xbfb0ff
        CoreClass.MAGE -> when(element) { 1 -> 0xffa76e; 2 -> 0xa4e7ff; else -> 0xc3abff }
    }
    override val durationTicks = if(ultimate) 12 else 8
    override fun emit(tick: Int, sink: ParticleSink) {
        if(tick !in 0 until durationTicks) return
        val t = tick.toDouble() / durationTicks
        fun p(x: Double, y: Double, z: Double, bright: Boolean = false, scale: Float = 1f) {
            sink.spawn(ParticleSpawn(dustTransition(if(bright) 0xfff7e3 else color, color, scale),
                transform.localPoint(Vec(x,y,z)), category = ParticleCategory.OWN_ACTIVE,
                importance = ParticleImportance.COMBAT_FEEDBACK))
        }
        when(motion) {
            CoreSkillMotion.CONE, CoreSkillMotion.LUNGE, CoreSkillMotion.SPIN -> {
                // Two crossing close blades for daggers, a weighted falling mace for the tank.
                val progress = min(1.0, t * 2)
                val extent = if(motion == CoreSkillMotion.SPIN) PI else acos(.35)
                for(i in 0..24) {
                    val u = i / 24.0
                    val a = -extent + 2 * extent * u
                    val r = radius * (.82 + .12 * progress)
                    val x = sin(a) * r * if(reverse) -1 else 1
                    if(job == CoreClass.TEMPLAR) {
                        p(sin(a)*radius, .12, cos(a)*radius, scale=.7f)
                        if(i < 13) p(.05, 3.5-i*.24, 1+progress*radius*.55, true, 1.65f)
                    } else {
                        p(x, .55+u*1.5, cos(a)*r, u <= progress, if(ultimate) 1.55f else 1.1f)
                        if(job == CoreClass.ASSASSIN) p(-x, 2-u*1.5, cos(a)*r*.85, u<=progress, .8f)
                    }
                }
            }
            CoreSkillMotion.PULL -> {
                // Inward spirals communicate compression, not an outward explosion.
                for(arm in 0..3) for(i in 0..9) {
                    val u = i/9.0
                    val a = arm*PI/2 + u*1.5 + t*1.8
                    val r = radius * (1-t*.8) * u
                    p(cos(a)*r,.2+(1-u)*.9,sin(a)*r,i<3,if(ultimate) 1.5f else 1f)
                }
            }
            CoreSkillMotion.GUARD, CoreSkillMotion.SHIELD -> {
                for(i in 0..23) {
                    val a = i*PI*2/24
                    p(cos(a)*min(radius,1.3),.25+t*1.8,sin(a)*min(radius,1.3),i%6==0,.8f)
                }
                if(radius>2 && tick%2==0) for(i in 0..23) {
                    val a=i*PI*2/24;p(cos(a)*radius,.12,sin(a)*radius,false,.65f)
                }
            }
            CoreSkillMotion.HEAL -> {
                for(arm in 0..3) for(i in 0..7) {
                    val a=arm*PI/2+t*1.5;val r=radius*i/7
                    p(cos(a)*r,.15+t*1.4,sin(a)*r,i%3==0,.85f)
                }
            }
            CoreSkillMotion.FIELD -> {
                for(i in 0..23) {
                    val a=i*PI*2/24
                    p(cos(a)*radius,.12,sin(a)*radius,false,.7f)
                    if(i%4==0) for(h in 0..2) {
                        val y = 4.5*(1-t)+h*.28
                        p(cos(a)*radius*.65,y,sin(a)*radius*.65,h==0,1.1f)
                    }
                }
            }
            else -> {
                val r=radius*min(1.0,t*2+.2)
                for(i in 0..35) { val a=i*PI*2/36;p(cos(a)*r,.18+sin(t*PI)*.25,sin(a)*r,i%6==0,.95f) }
            }
        }
    }
}
