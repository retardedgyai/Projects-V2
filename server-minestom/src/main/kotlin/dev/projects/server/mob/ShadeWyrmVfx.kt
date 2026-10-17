package dev.projects.server.mob

import dev.projects.server.particle.ParticleCategory
import dev.projects.server.particle.ParticleEffect
import dev.projects.server.particle.ParticleImportance
import dev.projects.server.particle.ParticleSink
import dev.projects.server.particle.ParticleSpawn
import dev.projects.server.particle.dust
import dev.projects.server.particle.dustTransition
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.block.Block
import net.minestom.server.particle.Particle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The 黒翳竜's particles, timed with its model clips (docs/development/shade-wyrm-boss.md): a cue during the
 * wind-up and the strike itself where the hit lands, so what hurts is what is seen. The shapes follow the
 * abilities' MobAttackShape; the ground telegraph stays the exact edge. The model's bones place the mouth and
 * head (null before the model exists: the effect then starts from the carrier).
 */
internal object ShadeWyrmVfx {
    private const val DUST_DARK = 0x231430
    private const val DUST_MID = 0x6a3f96
    private const val DUST_LIGHT = 0xc7a2ee
    private const val GLOW = 0xff6fc0
    private val DEBRIS = Particle.BLOCK.withBlock(Block.POLISHED_BLACKSTONE)

    /** Blockbench px on the skull bone: the snout tip and the antennae roots. */
    val SNOUT = Vec(0.0, 42.0, 66.0)
    val CROWN = Vec(0.0, 50.0, 64.0)

    fun windup(abilityId: String, frame: MobAbilityFrame, head: (Vec) -> Point?): ParticleEffect? {
        val ticks = (frame.ability.telegraphMillis / 50).toInt().coerceAtLeast(1)
        val caster = frame.casterOrigin
        return when (abilityId) {
            "shade-dust" -> Timed(ticks) { tick, sink ->         // spores shaken off the lifted wings
                val k = tick.toDouble() / ticks
                repeat(if (k > .5) 4 else 2) { sink.cosmetic(dust(if (it % 2 == 0) DUST_LIGHT else DUST_MID, 1.1f),
                    caster.add(rand(-2.6, 2.6), rand(2.4, 4.4), rand(-2.6, 1.0)), Vec(.1, .05, .1), 1) }
            }
            "shade-pulse" -> Timed(ticks) { tick, sink ->        // the antennae glow brighter until the pulse
                val at = head(CROWN) ?: caster.add(0.0, 4.0, 0.0)
                val k = tick.toDouble() / ticks
                sink.cosmetic(dust(GLOW, (.6 + k).toFloat()), at, Vec(.25, .15, .25), if (k > .6) 3 else 1)
            }
            "shade-breath" -> Timed(ticks) { tick, sink ->       // the dust drawn into the mouth
                val mouth = head(SNOUT) ?: caster.add(frame.facing.normalize().mul(3.5)).add(0.0, 2.5, 0.0)
                val k = tick.toDouble() / ticks
                if (k < .3) return@Timed
                repeat(3) {
                    val a = rand(0.0, 2 * PI); val r = 2.2 * (1 - k) + .6
                    sink.cosmetic(dustTransition(DUST_LIGHT, DUST_DARK, .9f),
                        mouth.add(cos(a) * r, rand(-1.0, 1.0) * r * .6, sin(a) * r), Vec.ZERO, 1)
                }
                if (k > .7) sink.cosmetic(dust(GLOW, 1.4f), mouth, Vec(.1, .1, .1), 2)
            }
            "shade-storm" -> Timed(ticks) { tick, sink ->        // dust streaming off the raised fans
                val k = tick.toDouble() / ticks
                repeat(if (k > .4) 6 else 3) { sink.cosmetic(dust(if (it % 3 == 0) GLOW else DUST_LIGHT, 1.2f),
                    caster.add(rand(-5.0, 5.0), rand(3.0, 7.5), rand(-3.0, 2.0)), Vec(.15, .1, .15), 1) }
            }
            else -> null
        }
    }

    fun hit(abilityId: String, frame: MobAbilityFrame, head: (Vec) -> Point?): ParticleEffect? {
        val forward = horizontal(frame.facing)
        val right = Vec(-forward.z(), 0.0, forward.x())
        val origin = frame.origin
        val caster = frame.casterOrigin
        return when (val shape = frame.ability.shape) {
            is MobAttackShape.Sweep -> Timed(7) { tick, sink ->  // the wing-arm's wake crossing the fan, right to left
                val half = Math.toRadians(shape.halfAngleDegrees)
                val head0 = (tick.coerceAtMost(3) / 3.0)
                for (n in 0..20) {
                    val t = n / 20.0
                    if (tick < 3 && t > head0 + .08) continue
                    val a = half - 2 * half * t
                    val dir = forward.mul(cos(a)).add(right.mul(sin(a)))
                    for (layer in 0..2) {
                        val r = shape.radius * (.55 + .2 * layer)
                        val fresh = tick < 4 && t > head0 - .3
                        sink.combat(dustTransition(if (fresh) DUST_LIGHT else DUST_MID, DUST_DARK, (if (fresh) 1.8f else 1.0f) - .3f * layer),
                            origin.add(dir.mul(r)).add(0.0, .7 + .25 * layer, 0.0))
                    }
                    if (tick == 2 && n % 5 == 0) sink.combat(DEBRIS, origin.add(dir.mul(shape.radius)).add(0.0, .2, 0.0), Vec(.3, .1, .3), 6)
                }
                if (tick == 1) sink.combat(Particle.SWEEP_ATTACK, origin.add(forward.mul(2.6)).add(0.0, 1.0, 0.0), Vec(1.2, .2, 1.2), 3)
            }
            is MobAttackShape.Slam -> if (abilityId == "shade-breath") {
                val mouth = head(SNOUT) ?: caster.add(forward.mul(3.5)).add(0.0, 2.5, 0.0)
                Timed(12) { tick, sink ->                         // the beam to the ground, then the band erupting
                    val reach = (tick + 1) / 3.0
                    for (n in 0..36) {
                        val t = n / 36.0
                        if (t > reach) break
                        val ground = origin.add(forward.mul(shape.length * t)).add(0.0, if (tick < 5) .6 else .3, 0.0)
                        val touch = origin.add(forward.mul(shape.length * .25)).add(0.0, .6, 0.0)
                        val p = if (tick < 5 && t < .25) lerp(mouth, touch, t / .25) else ground
                        sink.combat(dustTransition(if (tick < 5) GLOW else DUST_LIGHT, DUST_DARK, if (tick < 5) 2.2f else 1.2f), p,
                            Vec(shape.halfWidth * .3, .1, shape.halfWidth * .3), 2)
                    }
                    if (tick in 3..9 && tick % 2 == 1) for (n in 0..5) {
                        val at = origin.add(forward.mul(shape.length * (n + .5) / 6)).add(right.mul(rand(-1.0, 1.0) * shape.halfWidth))
                        sink.combat(Particle.DRAGON_BREATH, at.add(0.0, .4, 0.0), Vec(.4, .3, .4), 6)
                        sink.combat(dust(DUST_MID, 1.6f), at.add(0.0, 1.0, 0.0), Vec(.5, .6, .5), 4)
                    }
                }
            } else {
                val landing = origin.add(forward.mul(2.0))      // the forepaws land ~2 blocks ahead, then the shock runs on
                Timed(12) { tick, sink ->
                    if (tick == 0) {
                        sink.combat(Particle.EXPLOSION, landing.add(0.0, .3, 0.0), Vec.ZERO, 1)
                        sink.combat(DEBRIS, landing.add(0.0, .3, 0.0), Vec(1.0, .2, 1.0), 30)
                    }
                    if (tick < 5) for (n in 0..17) {
                        val a = n * 2 * PI / 18; val r = .6 + tick * .5
                        sink.combat(Particle.POOF, landing.add(cos(a) * r, .15, sin(a) * r), Vec.ZERO, 1)
                    }
                    val front = 2.0 + (shape.length - 2.0) * (tick / 8.0).coerceAtMost(1.0)
                    if (tick <= 8) for (side in -1..1) {
                        val at = origin.add(forward.mul(front)).add(right.mul(side * shape.halfWidth * .7))
                        sink.combat(DEBRIS, at.add(0.0, .2, 0.0), Vec(.25, .1, .25), 5)
                        sink.combat(dust(DUST_MID, 1.4f), at.add(0.0, .4, 0.0), Vec(.2, .2, .2), 2)
                    }
                }
            }
            is MobAttackShape.Ring -> when {
                shape.innerRadius > 0.0 -> Timed(14) { tick, sink -> // 鱗粉嵐: a wall of dust rising outward over the band
                    val front = shape.innerRadius + (shape.radius - shape.innerRadius) * (tick / 6.0).coerceAtMost(1.0)
                    val count = (front * 4).toInt()
                    for (n in 0 until count) {
                        val a = n * 2 * PI / count + tick * .08
                        val r = if (tick <= 6) front else rand(shape.innerRadius, shape.radius)
                        val at = origin.add(cos(a) * r, 0.0, sin(a) * r)
                        if (tick <= 6) {
                            sink.combat(dustTransition(if (n % 4 == 0) GLOW else DUST_LIGHT, DUST_DARK, 1.6f), at.add(0.0, .3 + rand(0.0, 2.2), 0.0))
                            if (n % 3 == 0) sink.combat(DEBRIS, at.add(0.0, .2, 0.0), Vec(.2, .1, .2), 3)
                        } else if (n % 3 == 0) sink.cosmetic(dust(DUST_MID, 1.3f), at.add(0.0, rand(.5, 3.0), 0.0), Vec(.3, .3, .3), 1)
                    }
                    if (tick == 0) sink.combat(DEBRIS, caster.add(forward.mul(2.5)).add(0.0, .3, 0.0), Vec(1.6, .2, 1.6), 40)
                }
                abilityId == "shade-pulse" -> {
                    val at = head(CROWN)
                    Timed(8) { tick, sink ->                      // two rings of sound running out from the wyrm
                        val r = shape.radius * ((tick + 1) / 6.0).coerceAtMost(1.0)
                        val count = (r * 7).toInt().coerceAtLeast(8)
                        if (tick < 6) for (n in 0 until count) {
                            val a = n * 2 * PI / count
                            for (h in doubleArrayOf(.3, 1.3)) sink.combat(dustTransition(GLOW, DUST_MID, 1.3f), origin.add(cos(a) * r, h, sin(a) * r))
                        }
                        if (tick == 0 && at != null) sink.combat(Particle.SONIC_BOOM, at, Vec.ZERO, 1)
                    }
                }
                else -> Timed(14) { tick, sink ->                  // 鱗粉炸裂: a dome of spores bursting, then a haze
                    if (tick < 5) {
                        val r = shape.radius * (tick + 1) / 5.0
                        for (n in 0 until 28) {
                            val a = n * 2 * PI / 28; val e = (n % 4) * .35
                            sink.combat(dustTransition(DUST_LIGHT, DUST_DARK, 1.7f),
                                origin.add(cos(a) * r * cos(e), .3 + r * sin(e) * .8, sin(a) * r * cos(e)))
                        }
                        if (tick == 0) sink.combat(Particle.REVERSE_PORTAL, origin.add(0.0, 1.0, 0.0), Vec(1.0, .6, 1.0), 40)
                    } else repeat(4) {
                        val a = rand(0.0, 2 * PI); val r = rand(0.0, shape.radius)
                        sink.cosmetic(dust(DUST_MID, 1.2f), origin.add(cos(a) * r, rand(.3, 2.0), sin(a) * r), Vec(.2, .2, .2), 1)
                    }
                }
            }
            else -> null
        }
    }

    private class Timed(override val durationTicks: Int, private val body: (Int, ParticleSink) -> Unit) : ParticleEffect {
        override fun emit(tick: Int, sink: ParticleSink) { if (tick in 0 until durationTicks) body(tick, sink) }
    }

    private fun ParticleSink.combat(particle: Particle, at: Point, spread: Vec = Vec.ZERO, count: Int = 1) =
        spawn(ParticleSpawn(particle, at, count, spread, 0f, ParticleCategory.BOSS, importance = ParticleImportance.COMBAT_FEEDBACK))

    private fun ParticleSink.cosmetic(particle: Particle, at: Point, spread: Vec, count: Int) =
        spawn(ParticleSpawn(particle, at, count, spread, 0f, ParticleCategory.BOSS, importance = ParticleImportance.COSMETIC))

    private fun horizontal(v: Vec): Vec {
        val l = hypot(v.x(), v.z())
        return if (l < 1e-6) Vec(0.0, 0.0, 1.0) else Vec(v.x() / l, 0.0, v.z() / l)
    }

    private fun lerp(a: Point, b: Point, t: Double): Point = Pos(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t)

    private fun rand(lo: Double, hi: Double) = lo + (hi - lo) * java.util.concurrent.ThreadLocalRandom.current().nextDouble()
}
