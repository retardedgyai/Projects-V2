package dev.projects.server.mob

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.Instance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 桜牙獣's strike effects: the shader pieces of the shade wyrm (scripts/build_boss_vfx.py sprites, item.fsh) drawn in
 * the sakura palette (marker 79): shock rings, claw gouges, crescents, puffs and petal walls, so every blow has a
 * readable shape under the petal storm of [SakuraPetalFx]. Pack users only, like the wyrm's.
 */
internal class SakuraStrikeFx(instance: Instance) {
    private val fx = ShadeWyrmShaderFx(instance, MARKER)

    val liveCount: Int get() = fx.liveCount
    fun tick() = fx.tick()
    fun dispose() = fx.dispose()
    fun cancelWindup() = fx.cancelWindup()
    internal fun views() = fx.views()

    /** During the telegraph: what is gathering, so the long wind-ups read. */
    fun windup(abilityId: String, frame: MobAbilityFrame, point: (String, Vec) -> Point?) {
        val o = frame.origin
        val ticks = (frame.ability.telegraphMillis / 50).toInt()
        val shape = frame.ability.shape
        val appear: (Double) -> Double = { 1.0 - it * .85 }
        when (abilityId) {
            "sakura-claw", "sakura-claw3" -> {
                // Light gathers in the raised paw while the beast rears.
                val paw = point("hand_r", Vec(-15.0, 2.0, 28.0))?.let(Pos::fromPoint) ?: o.add(0.0, 3.5, 1.5)
                fx.add("puff", 4, paw, ticks / 3, ticks - ticks / 3, Vec(.5, .5, 1.0), IDENTITY, grow = Vec(1.6, 1.6, 1.0),
                    moveTicks = ticks - ticks / 3, billboard = true, progress = appear)
            }
            "sakura-bloom" -> if (shape is MobAttackShape.Ring) {
                // The circle it will burst lights up and a low wall of petals rises along its edge.
                fx.add("ring", 2, o.add(0.0, .08, 0.0), 0, ticks, Vec(shape.radius * 2.05, 1.0, shape.radius * 2.05), IDENTITY) { .1 + .3 * it * it }
                ring(o, shape.radius, 24) { dir, width ->
                    fx.add("curtain", 5, o, ticks / 3, ticks - ticks / 3, Vec(width, .6, 1.0), ShadeWyrmShaderFx.yawTo(dir),
                        translation = dir.mul(shape.radius).add(0.0, .3, 0.0), grow = Vec(width, 2.4, 1.0),
                        slide = dir.mul(shape.radius).add(0.0, 1.2, 0.0), moveTicks = ticks - ticks / 3) { .2 + .25 * it }
                }
            }
            "sakura-howl", "sakura-scatter" -> {
                val head = point("skull", Vec(0.0, 44.0, 40.0))?.let(Pos::fromPoint) ?: frame.casterOrigin.add(0.0, 3.0, 0.0)
                fx.add("puff", 4, head, ticks / 2, ticks - ticks / 2, Vec(.6, .6, 1.0), IDENTITY, grow = Vec(2.0, 2.0, 1.0),
                    moveTicks = ticks - ticks / 2, billboard = true, progress = appear)
            }
        }
    }

    fun strike(abilityId: String, frame: MobAbilityFrame, point: (String, Vec) -> Point?) {
        val f = horizontal(frame.facing)
        val right = Vec(-f.z(), 0.0, f.x())
        val o = frame.origin
        val yaw = ShadeWyrmShaderFx.yawTo(f)
        val shape = frame.ability.shape
        when (abilityId) {
            "sakura-claw", "sakura-claw2", "sakura-claw3" -> if (shape is MobAttackShape.Slam) {
                // The paw lands: a shock ring, three claw gouges torn down the strip and a puff of petals.
                val big = if (abilityId == "sakura-claw3") 1.5 else if (abilityId == "sakura-claw2") .8 else 1.0
                val impact = o.add(f.mul(shape.length * .35))
                fx.add("ring", 2, impact.add(0.0, .12, 0.0), 0, 12, Vec(2.0, 1.0, 2.0), yaw, grow = Vec(shape.halfWidth * 3.6 * big, 1.0, shape.halfWidth * 3.6 * big), moveTicks = 7)
                for (k in -1..1) {
                    val lane = o.add(right.mul(k * shape.halfWidth * .45)).add(f.mul(shape.length * .5))
                    fx.add("beam", 3, lane.add(0.0, .1, 0.0), k + 1, 14, Vec(.35, 1.0, 1.0), yaw,
                        grow = Vec(.5 * big, 1.0, shape.length), moveTicks = 4)
                }
                fx.add("puff", 4, impact.add(0.0, .8, 0.0), 0, 12, Vec(1.6 * big, 1.6 * big, 1.0), IDENTITY, grow = Vec(3.4 * big, 3.4 * big, 1.0),
                    slide = Vec(0.0, .9, 0.0), moveTicks = 11, billboard = true)
                if (abilityId == "sakura-claw3") fx.add("ring", 2, impact.add(0.0, .1, 0.0), 3, 12, Vec(1.0, 1.0, 1.0), yaw,
                    grow = Vec(shape.length * 2.0, 1.0, shape.length * 2.0), moveTicks = 9) { .3 + .6 * it }
            }
            "sakura-tail" -> if (shape is MobAttackShape.Slam) {
                // The somersault: an upright crescent traces the tail over the top, then the strip erupts block by block.
                val upright = ShadeWyrmShaderFx.mul(yaw, ShadeWyrmShaderFx.qz(PI / 2))
                fx.add("slash", 1, o.add(f.mul(1.5)).add(0.0, 2.6, 0.0), 0, 9, Vec(5.5, 1.0, 5.5), upright)
                var d = 1.0
                var n = 0
                while (d <= shape.length) {
                    val at = o.add(f.mul(d))
                    fx.add("puff", 4, at.add(0.0, .7, 0.0), n, 12, Vec(1.4, 1.4, 1.0), IDENTITY, grow = Vec(2.6, 2.6, 1.0),
                        slide = Vec(0.0, 1.0, 0.0), moveTicks = 11, billboard = true)
                    d += 1.6; n++
                }
                fx.add("beam", 3, o.add(f.mul(shape.length / 2)).add(0.0, .1, 0.0), 0, 14, Vec(.6, 1.0, 1.0), yaw,
                    grow = Vec(shape.halfWidth * 2.0, 1.0, shape.length), moveTicks = 5)
                fx.add("ring", 2, o.add(f.mul(shape.length * .5)).add(0.0, .12, 0.0), 1, 12, Vec(2.0, 1.0, 2.0), yaw,
                    grow = Vec(6.0, 1.0, 6.0), moveTicks = 7)
            }
            "sakura-scatter" -> if (shape is MobAttackShape.Sweep) {
                // The fling: crescents fanning out over the cone and streams of petals racing along it.
                val d = shape.radius * 2.1
                val narrow = shape.halfAngleDegrees / 90.0
                fx.add("slash", 1, o.add(0.0, 1.0, 0.0), 0, 9, Vec(d * narrow, 1.0, d), yaw)
                fx.add("slash", 1, o.add(0.0, 1.6, 0.0), 1, 9, Vec(d * .8 * narrow, 1.0, d * .8), ShadeWyrmShaderFx.mul(yaw, ShadeWyrmShaderFx.qx(-.12)))
                val half = Math.toRadians(shape.halfAngleDegrees)
                for (k in -2..2) {
                    val a = half * k / 2.0
                    val dir = f.mul(cos(a)).add(right.mul(sin(a)))
                    fx.add("beam", 3, o.add(dir.mul(shape.radius * .5)).add(0.0, .9 + .15 * k, 0.0), abs(k), 12, Vec(.4, 1.0, 1.0),
                        ShadeWyrmShaderFx.yawTo(dir), grow = Vec(.9, 1.0, shape.radius), moveTicks = 5)
                }
            }
            "sakura-bloom" -> if (shape is MobAttackShape.Ring) {
                // The crash: two rings race out to the edge, a wall of petals stands up along it, a column of light.
                val r2 = shape.radius * 2.05
                fx.add("ring", 2, o.add(0.0, .12, 0.0), 0, 12, Vec(2.0, 1.0, 2.0), IDENTITY, grow = Vec(r2, 1.0, r2), moveTicks = 7)
                fx.add("ring", 2, o.add(0.0, .1, 0.0), 3, 12, Vec(1.0, 1.0, 1.0), IDENTITY, grow = Vec(r2 * .8, 1.0, r2 * .8), moveTicks = 8)
                ring(o, shape.radius, 28) { dir, width ->
                    fx.add("curtain", 5, o, 3, 14, Vec(width, 1.0, 1.0), ShadeWyrmShaderFx.yawTo(dir),
                        translation = dir.mul(1.5).add(0.0, .5, 0.0), grow = Vec(width * shape.radius / 1.5, 3.2, 1.0),
                        slide = dir.mul(shape.radius).add(0.0, 1.6, 0.0), moveTicks = 8)
                }
                fx.add("puff", 4, o.add(0.0, 2.0, 0.0), 0, 14, Vec(2.5, 4.0, 1.0), IDENTITY, grow = Vec(4.0, 7.0, 1.0), moveTicks = 12, billboard = true)
            }
            "sakura-horn" -> if (shape is MobAttackShape.Slam) {
                // The horns rake upward: a standing crescent ahead and a small shock.
                val upright = ShadeWyrmShaderFx.mul(yaw, ShadeWyrmShaderFx.mul(ShadeWyrmShaderFx.qz(PI / 2), ShadeWyrmShaderFx.qy(PI)))
                fx.add("slash", 1, o.add(f.mul(shape.length * .5)).add(0.0, 1.6, 0.0), 0, 8, Vec(3.6, 1.0, 3.6), upright)
                fx.add("ring", 2, o.add(f.mul(shape.length * .6)).add(0.0, .12, 0.0), 1, 10, Vec(1.0, 1.0, 1.0), yaw, grow = Vec(4.0, 1.0, 4.0), moveTicks = 6)
            }
            "sakura-spin" -> if (shape is MobAttackShape.Ring) {
                // A full circle of crescents where the tail swept, and the ring it threw out.
                val d = shape.radius * 2.1
                fx.add("slash", 1, o.add(0.0, .9, 0.0), 0, 9, Vec(d, 1.0, d), yaw)
                fx.add("slash", 1, o.add(0.0, .9, 0.0), 1, 9, Vec(d, 1.0, d), ShadeWyrmShaderFx.mul(yaw, ShadeWyrmShaderFx.qy(PI)))
                fx.add("ring", 2, o.add(0.0, .12, 0.0), 0, 12, Vec(2.0, 1.0, 2.0), IDENTITY, grow = Vec(d, 1.0, d), moveTicks = 7)
            }
            "sakura-howl" -> if (shape is MobAttackShape.Ring) {
                // The howl: a burst at the head and two shock rings rolling out over the circle.
                val head = point("skull", Vec(0.0, 44.0, 40.0))?.let(Pos::fromPoint) ?: frame.casterOrigin.add(0.0, 3.0, 0.0)
                fx.add("puff", 4, head, 0, 12, Vec(2.0, 2.0, 1.0), IDENTITY, grow = Vec(5.0, 5.0, 1.0), moveTicks = 10, billboard = true)
                val r2 = shape.radius * 2.05
                fx.add("ring", 2, o.add(0.0, .12, 0.0), 0, 12, Vec(2.0, 1.0, 2.0), IDENTITY, grow = Vec(r2, 1.0, r2), moveTicks = 8)
                fx.add("ring", 2, o.add(0.0, .1, 0.0), 4, 12, Vec(1.0, 1.0, 1.0), IDENTITY, grow = Vec(r2, 1.0, r2), moveTicks = 9) { .3 + .6 * it }
            }
        }
    }

    /** The enrage howl's petals bursting open: a wave rolls out to the arena wall. */
    fun roar(at: Pos, radius: Double) {
        val d = radius * 2.05
        fx.add("ring", 2, at.add(0.0, .12, 0.0), 0, 24, Vec(2.0, 1.0, 2.0), IDENTITY, grow = Vec(d, 1.0, d), moveTicks = 22) { .25 + .5 * it }
        fx.add("ring", 2, at.add(0.0, .1, 0.0), 4, 22, Vec(1.0, 1.0, 1.0), IDENTITY, grow = Vec(d * .9, 1.0, d * .9), moveTicks = 20) { .5 + .4 * it }
        fx.add("puff", 4, at.add(0.0, 3.0, 0.0), 0, 12, Vec(3.0, 3.0, 1.0), IDENTITY, grow = Vec(7.0, 7.0, 1.0), moveTicks = 10, billboard = true)
    }

    private fun ring(o: Pos, radius: Double, count: Int, piece: (Vec, Double) -> Unit) {
        for (n in 0 until count) {
            val a = n * 2 * PI / count
            piece(Vec(cos(a), 0.0, sin(a)), 2 * PI * radius / count * 1.3)
        }
    }

    companion object {
        const val MARKER = 79
        private val IDENTITY = floatArrayOf(0f, 0f, 0f, 1f)
        private fun abs(k: Int) = if (k < 0) -k else k
        private fun horizontal(v: Vec): Vec {
            val l = hypot(v.x(), v.z())
            return if (l < 1e-6) Vec(0.0, 0.0, 1.0) else Vec(v.x() / l, 0.0, v.z() / l)
        }
    }
}
