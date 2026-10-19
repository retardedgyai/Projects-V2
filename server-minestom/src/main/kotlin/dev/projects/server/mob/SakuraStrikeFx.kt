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
                fx.add("sakura_blossom", 4, paw, ticks / 3, ticks - ticks / 3, Vec(.5, .5, 1.0), IDENTITY, grow = Vec(1.6, 1.6, 1.0),
                    moveTicks = ticks - ticks / 3, billboard = true, progress = appear)
            }
            "sakura-bloom" -> if (shape is MobAttackShape.Ring) {
                // The circle it will burst lights up and a low wall of petals rises along its edge.
                fx.add("sakura_ring", 2, o.add(0.0, .08, 0.0), 0, ticks, Vec(shape.radius * 2.05, 1.0, shape.radius * 2.05), IDENTITY) { .1 + .3 * it * it }
                ring(o, shape.radius, 24) { dir, width ->
                    fx.add("sakura_curtain", 5, o, ticks / 3, ticks - ticks / 3, Vec(width, .6, 1.0), ShadeWyrmShaderFx.yawTo(dir),
                        translation = dir.mul(shape.radius).add(0.0, .3, 0.0), grow = Vec(width, 2.4, 1.0),
                        slide = dir.mul(shape.radius).add(0.0, 1.2, 0.0), moveTicks = ticks - ticks / 3) { .2 + .25 * it }
                }
            }
            "sakura-rain" -> if (shape is MobAttackShape.Ring) {
                // A crest of light opens under the mark and slowly brightens: that is where the blossoms will fall.
                fx.add("sakura_crest", 2, o.add(0.0, .14, 0.0), 0, ticks, Vec(shape.radius * 1.2, 1.0, shape.radius * 1.2), IDENTITY,
                    grow = Vec(shape.radius * 2.0, 1.0, shape.radius * 2.0), moveTicks = ticks / 2) { .05 + .35 * it * it }
                fx.add("sakura_ring", 2, o.add(0.0, .1, 0.0), 0, ticks, Vec(shape.radius * 2.05, 1.0, shape.radius * 2.05), IDENTITY) { .2 + .3 * it }
            }
            "sakura-pounce" -> if (shape is MobAttackShape.Ring) {
                fx.add("sakura_ring", 2, o.add(0.0, .1, 0.0), ticks / 3, ticks - ticks / 3, Vec(shape.radius * 2.05, 1.0, shape.radius * 2.05), IDENTITY) { .25 + .3 * it }
            }
            "sakura-howl", "sakura-scatter" -> {
                val head = point("skull", Vec(0.0, 44.0, 40.0))?.let(Pos::fromPoint) ?: frame.casterOrigin.add(0.0, 3.0, 0.0)
                fx.add("sakura_blossom", 4, head, ticks / 2, ticks - ticks / 2, Vec(.6, .6, 1.0), IDENTITY, grow = Vec(2.0, 2.0, 1.0),
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
            "sakura-lunge" -> if (shape is MobAttackShape.Slam) {
                // The paw rakes across where it lands: a tilted crescent, three short gouges and a blossom puff.
                val at = frame.casterOrigin.add(f.mul(3.6))
                fx.add("sakura_slash", 1, at.add(0.0, 1.1, 0.0), 0, 8, Vec(4.2, 1.0, 4.2), ShadeWyrmShaderFx.mul(yaw, ShadeWyrmShaderFx.qx(-.35)))
                for (k in -1..1) fx.add("sakura_trail", 3, at.add(right.mul(k * .5)).add(f.mul(.8)).add(0.0, .1, 0.0), k + 1, 10,
                    Vec(.3, 1.0, 1.0), yaw, grow = Vec(.45, 1.0, 2.6), moveTicks = 3)
                fx.add("sakura_blossom", 4, at.add(f.mul(1.0)).add(0.0, .9, 0.0), 0, 10, Vec(1.0, 1.0, 1.0), IDENTITY, grow = Vec(2.4, 2.4, 1.0),
                    slide = Vec(0.0, .6, 0.0), moveTicks = 9, billboard = true)
            }
            "sakura-barge" -> if (shape is MobAttackShape.Slam) {
                // The flank hits: a shock ring where the shoulder lands and a short wall of petals thrown ahead.
                val at = frame.casterOrigin.add(f.mul(4.2))
                fx.add("sakura_ring", 2, at.add(0.0, .12, 0.0), 0, 10, Vec(1.5, 1.0, 1.5), yaw, grow = Vec(5.0, 1.0, 5.0), moveTicks = 6)
                fx.add("sakura_curtain", 5, at, 0, 11, Vec(2.4, .8, 1.0), yaw, translation = Vec(0.0, .4, 0.0), grow = Vec(4.4, 2.6, 1.0),
                    slide = f.mul(1.6).add(0.0, .8, 0.0), moveTicks = 8)
                fx.add("sakura_blossom", 4, at.add(0.0, 1.6, 0.0), 0, 10, Vec(1.4, 1.4, 1.0), IDENTITY, grow = Vec(3.2, 3.2, 1.0), moveTicks = 9, billboard = true)
            }
            "sakura-hopswipe" -> if (shape is MobAttackShape.Sweep) {
                // The tail whips across its front as it lands: a wide low crescent and a second, fainter echo.
                val at = frame.casterOrigin.add(right.mul(2.5))
                val d = shape.radius * 2.0
                fx.add("sakura_slash", 1, at.add(0.0, .7, 0.0), 0, 8, Vec(d, 1.0, d), yaw)
                fx.add("sakura_slash", 1, at.add(0.0, 1.2, 0.0), 2, 8, Vec(d * .8, 1.0, d * .8), ShadeWyrmShaderFx.mul(yaw, ShadeWyrmShaderFx.qy(.35)))
                fx.add("sakura_ring", 2, at.add(0.0, .12, 0.0), 0, 10, Vec(1.5, 1.0, 1.5), IDENTITY, grow = Vec(4.0, 1.0, 4.0), moveTicks = 6)
            }
            "sakura-claw", "sakura-claw2", "sakura-claw3" -> if (shape is MobAttackShape.Slam) {
                // The paw lands: a shock ring, three claw gouges torn down the strip and a puff of petals.
                val big = if (abilityId == "sakura-claw3") 1.5 else if (abilityId == "sakura-claw2") .8 else 1.0
                val impact = o.add(f.mul(shape.length * .35))
                fx.add("sakura_ring", 2, impact.add(0.0, .12, 0.0), 0, 12, Vec(2.0, 1.0, 2.0), yaw, grow = Vec(shape.halfWidth * 3.6 * big, 1.0, shape.halfWidth * 3.6 * big), moveTicks = 7)
                for (k in -1..1) {
                    val lane = o.add(right.mul(k * shape.halfWidth * .45)).add(f.mul(shape.length * .5))
                    fx.add("sakura_trail", 3, lane.add(0.0, .1, 0.0), k + 1, 14, Vec(.35, 1.0, 1.0), yaw,
                        grow = Vec(.5 * big, 1.0, shape.length), moveTicks = 4)
                }
                fx.add("sakura_blossom", 4, impact.add(0.0, .8, 0.0), 0, 12, Vec(1.6 * big, 1.6 * big, 1.0), IDENTITY, grow = Vec(3.4 * big, 3.4 * big, 1.0),
                    slide = Vec(0.0, .9, 0.0), moveTicks = 11, billboard = true)
                crest(impact, shape.halfWidth * 2.6 * big, yaw)
                if (abilityId == "sakura-claw3") fx.add("sakura_ring", 2, impact.add(0.0, .1, 0.0), 3, 12, Vec(1.0, 1.0, 1.0), yaw,
                    grow = Vec(shape.length * 2.0, 1.0, shape.length * 2.0), moveTicks = 9) { .3 + .6 * it }
            }
            "sakura-tail" -> if (shape is MobAttackShape.Slam) {
                // The somersault: an upright crescent traces the tail over the top, then the strip erupts block by block.
                val upright = ShadeWyrmShaderFx.mul(yaw, ShadeWyrmShaderFx.qz(PI / 2))
                fx.add("sakura_slash", 1, o.add(f.mul(1.5)).add(0.0, 2.6, 0.0), 0, 9, Vec(5.5, 1.0, 5.5), upright)
                var d = 1.0
                var n = 0
                while (d <= shape.length) {
                    val at = o.add(f.mul(d))
                    fx.add("sakura_blossom", 4, at.add(0.0, .7, 0.0), n, 12, Vec(1.4, 1.4, 1.0), IDENTITY, grow = Vec(2.6, 2.6, 1.0),
                        slide = Vec(0.0, 1.0, 0.0), moveTicks = 11, billboard = true)
                    d += 1.6; n++
                }
                fx.add("sakura_trail", 3, o.add(f.mul(shape.length / 2)).add(0.0, .1, 0.0), 0, 14, Vec(.6, 1.0, 1.0), yaw,
                    grow = Vec(shape.halfWidth * 2.0, 1.0, shape.length), moveTicks = 5)
                fx.add("sakura_ring", 2, o.add(f.mul(shape.length * .5)).add(0.0, .12, 0.0), 1, 12, Vec(2.0, 1.0, 2.0), yaw,
                    grow = Vec(6.0, 1.0, 6.0), moveTicks = 7)
                crest(o.add(f.mul(shape.length * .55)), 4.5, yaw)
            }
            "sakura-scatter" -> if (shape is MobAttackShape.Sweep) {
                // The fling: crescents fanning out over the cone and streams of petals racing along it.
                val d = shape.radius * 2.1
                val narrow = shape.halfAngleDegrees / 90.0
                fx.add("sakura_slash", 1, o.add(0.0, 1.0, 0.0), 0, 9, Vec(d * narrow, 1.0, d), yaw)
                fx.add("sakura_slash", 1, o.add(0.0, 1.6, 0.0), 1, 9, Vec(d * .8 * narrow, 1.0, d * .8), ShadeWyrmShaderFx.mul(yaw, ShadeWyrmShaderFx.qx(-.12)))
                val half = Math.toRadians(shape.halfAngleDegrees)
                for (k in -2..2) {
                    val a = half * k / 2.0
                    val dir = f.mul(cos(a)).add(right.mul(sin(a)))
                    fx.add("sakura_trail", 3, o.add(dir.mul(shape.radius * .5)).add(0.0, .9 + .15 * k, 0.0), abs(k), 12, Vec(.4, 1.0, 1.0),
                        ShadeWyrmShaderFx.yawTo(dir), grow = Vec(.9, 1.0, shape.radius), moveTicks = 5)
                }
            }
            "sakura-bloom" -> if (shape is MobAttackShape.Ring) {
                // The crash: two rings race out to the edge, a wall of petals stands up along it, a column of light.
                val r2 = shape.radius * 2.05
                fx.add("sakura_ring", 2, o.add(0.0, .12, 0.0), 0, 12, Vec(2.0, 1.0, 2.0), IDENTITY, grow = Vec(r2, 1.0, r2), moveTicks = 7)
                fx.add("sakura_ring", 2, o.add(0.0, .1, 0.0), 3, 12, Vec(1.0, 1.0, 1.0), IDENTITY, grow = Vec(r2 * .8, 1.0, r2 * .8), moveTicks = 8)
                ring(o, shape.radius, 28) { dir, width ->
                    fx.add("sakura_curtain", 5, o, 3, 14, Vec(width, 1.0, 1.0), ShadeWyrmShaderFx.yawTo(dir),
                        translation = dir.mul(1.5).add(0.0, .5, 0.0), grow = Vec(width * shape.radius / 1.5, 3.2, 1.0),
                        slide = dir.mul(shape.radius).add(0.0, 1.6, 0.0), moveTicks = 8)
                }
                fx.add("sakura_blossom", 4, o.add(0.0, 2.6, 0.0), 0, 14, Vec(2.5, 2.5, 1.0), IDENTITY, grow = Vec(6.0, 6.0, 1.0), moveTicks = 12, billboard = true)
                crest(o, shape.radius * 1.9, ShadeWyrmShaderFx.qy(.3))
            }
            "sakura-pounce", "sakura-rain" -> if (shape is MobAttackShape.Ring) {
                // It lands (or the blossoms land) on the mark: a crest blooms, a ring of petals and a blossom burst.
                crest(o, shape.radius * 2.2, yaw)
                fx.add("sakura_ring", 2, o.add(0.0, .12, 0.0), 0, 12, Vec(2.0, 1.0, 2.0), IDENTITY, grow = Vec(shape.radius * 2.6, 1.0, shape.radius * 2.6), moveTicks = 7)
                fx.add("sakura_blossom", 4, o.add(0.0, 1.2, 0.0), 0, 12, Vec(1.8, 1.8, 1.0), IDENTITY, grow = Vec(4.0, 4.0, 1.0), moveTicks = 10, billboard = true)
            }
            "sakura-charge" -> if (shape is MobAttackShape.Slam) {
                // The run: two trails of petals down the strip, and the skid's ring at its end.
                for (k in listOf(-1, 1)) fx.add("sakura_trail", 3, o.add(right.mul(k * shape.halfWidth * .6)).add(f.mul(shape.length / 2)).add(0.0, .1, 0.0), 0, 18,
                    Vec(.6, 1.0, 1.0), yaw, grow = Vec(1.2, 1.0, shape.length), moveTicks = 8)
                fx.add("sakura_ring", 2, o.add(f.mul(shape.length * .8)).add(0.0, .12, 0.0), 6, 12, Vec(2.0, 1.0, 2.0), yaw, grow = Vec(6.0, 1.0, 6.0), moveTicks = 7)
                crest(o.add(f.mul(shape.length * .8)), 5.0, yaw)
            }
            "sakura-horn" -> if (shape is MobAttackShape.Slam) {
                // The horns rake upward: a standing crescent ahead and a small shock.
                // the crescent stands in the vertical plane through the facing, its arc ahead of the head
                val upright = ShadeWyrmShaderFx.mul(yaw, ShadeWyrmShaderFx.qz(PI / 2))
                fx.add("sakura_slash", 1, o.add(f.mul(shape.length * .3)).add(0.0, 2.2, 0.0), 0, 9, Vec(4.4, 1.0, 4.4), upright)
                fx.add("sakura_blossom", 4, o.add(f.mul(shape.length * .75)).add(0.0, 2.4, 0.0), 1, 10, Vec(1.0, 1.0, 1.0), IDENTITY,
                    grow = Vec(2.6, 2.6, 1.0), moveTicks = 8, billboard = true)
                fx.add("sakura_ring", 2, o.add(f.mul(shape.length * .6)).add(0.0, .12, 0.0), 1, 10, Vec(1.0, 1.0, 1.0), yaw, grow = Vec(4.0, 1.0, 4.0), moveTicks = 6)
            }
            "sakura-spin" -> if (shape is MobAttackShape.Ring) {
                // A full circle of crescents where the tail swept, and the ring it threw out.
                val d = shape.radius * 2.1
                fx.add("sakura_slash", 1, o.add(0.0, .9, 0.0), 0, 9, Vec(d, 1.0, d), yaw)
                fx.add("sakura_slash", 1, o.add(0.0, .9, 0.0), 1, 9, Vec(d, 1.0, d), ShadeWyrmShaderFx.mul(yaw, ShadeWyrmShaderFx.qy(PI)))
                fx.add("sakura_ring", 2, o.add(0.0, .12, 0.0), 0, 12, Vec(2.0, 1.0, 2.0), IDENTITY, grow = Vec(d, 1.0, d), moveTicks = 7)
            }
            "sakura-howl" -> if (shape is MobAttackShape.Ring) {
                // The howl: a burst at the head and two shock rings rolling out over the circle.
                val head = point("skull", Vec(0.0, 44.0, 40.0))?.let(Pos::fromPoint) ?: frame.casterOrigin.add(0.0, 3.0, 0.0)
                fx.add("sakura_blossom", 4, head, 0, 12, Vec(2.0, 2.0, 1.0), IDENTITY, grow = Vec(5.0, 5.0, 1.0), moveTicks = 10, billboard = true)
                val r2 = shape.radius * 2.05
                fx.add("sakura_ring", 2, o.add(0.0, .12, 0.0), 0, 12, Vec(2.0, 1.0, 2.0), IDENTITY, grow = Vec(r2, 1.0, r2), moveTicks = 8)
                fx.add("sakura_ring", 2, o.add(0.0, .1, 0.0), 4, 12, Vec(1.0, 1.0, 1.0), IDENTITY, grow = Vec(r2, 1.0, r2), moveTicks = 9) { .3 + .6 * it }
                crest(o, shape.radius * 1.4, yaw)
            }
        }
    }

    /** 千本桜: a crest of light spreads over the whole floor and brightens while it howls. */
    fun finale(at: Pos, ticks: Int) {
        fx.add("sakura_crest", 2, at.add(0.0, .15, 0.0), 0, ticks, Vec(4.0, 1.0, 4.0), IDENTITY, grow = Vec(44.0, 1.0, 44.0),
            moveTicks = ticks - 10) { .05 + .4 * it * it }
        fx.add("sakura_ring", 2, at.add(0.0, .1, 0.0), 0, ticks, Vec(44.0, 1.0, 44.0), IDENTITY, grow = Vec(6.0, 1.0, 6.0), moveTicks = ticks) { .2 + .3 * it }
        fx.add("sakura_blossom", 4, at.add(0.0, 5.0, 0.0), ticks / 2, ticks / 2, Vec(1.0, 1.0, 1.0), IDENTITY, grow = Vec(6.0, 6.0, 1.0),
            moveTicks = ticks / 2, billboard = true) { .05 }
    }

    /** 千本桜 blows: the floor's crest bursts, rings race to the wall, a wall of petals stands up round the arena. */
    fun finaleBlow(at: Pos, radius: Double) {
        crest(at, radius * 2.0, IDENTITY, life = 24)
        roar(at, radius)
        fx.add("sakura_blossom", 4, at.add(0.0, 4.0, 0.0), 0, 16, Vec(6.0, 6.0, 1.0), IDENTITY, grow = Vec(14.0, 14.0, 1.0), moveTicks = 14, billboard = true)
        ring(at, radius - 1.5, 32) { dir, width ->
            fx.add("sakura_curtain", 5, at, 2, 16, Vec(width, 1.0, 1.0), ShadeWyrmShaderFx.yawTo(dir),
                translation = dir.mul(3.0).add(0.0, .5, 0.0), grow = Vec(width * 2.0, 4.0, 1.0), slide = dir.mul(radius - 1.5).add(0.0, 2.0, 0.0), moveTicks = 10)
        }
    }

    /** 桜紋の陣: a crest of light on each spot, brightening as the howl runs out, with a ring of petals round its edge. */
    fun sigil(crests: List<Pos>, radius: Double, ticks: Int) {
        for (c in crests) {
            fx.add("sakura_crest", 2, c.add(0.0, .14, 0.0), 0, ticks, Vec(radius * .6, 1.0, radius * .6), IDENTITY,
                grow = Vec(radius * 2.2, 1.0, radius * 2.2), moveTicks = 10) { .05 + .45 * it * it }
            fx.add("sakura_ring", 2, c.add(0.0, .1, 0.0), 0, ticks, Vec(radius * 2.1, 1.0, radius * 2.1), IDENTITY) { .25 + .2 * it }
            fx.add("sakura_blossom", 4, c.add(0.0, 1.6, 0.0), 0, ticks, Vec(.6, .6, 1.0), IDENTITY, grow = Vec(1.4, 1.4, 1.0),
                moveTicks = ticks, billboard = true) { .05 }
        }
    }

    /** 桜紋の陣 resolved: held crests bloom and fade, empty ones burst. */
    fun sigilResolve(crests: List<Pos>, filled: List<Boolean>) {
        for ((c, held) in crests.zip(filled)) {
            if (held) {
                crest(c, 4.5, IDENTITY)
                fx.add("sakura_blossom", 4, c.add(0.0, 1.4, 0.0), 0, 12, Vec(1.4, 1.4, 1.0), IDENTITY, grow = Vec(3.2, 3.2, 1.0), moveTicks = 10, billboard = true)
            } else {
                fx.add("sakura_ring", 2, c.add(0.0, .12, 0.0), 0, 12, Vec(2.0, 1.0, 2.0), IDENTITY, grow = Vec(14.0, 1.0, 14.0), moveTicks = 8)
                fx.add("sakura_blossom", 4, c.add(0.0, 2.0, 0.0), 0, 14, Vec(2.5, 2.5, 1.0), IDENTITY, grow = Vec(6.0, 6.0, 1.0), moveTicks = 12, billboard = true)
                fx.add("sakura_curtain", 5, c, 0, 14, Vec(4.0, 1.0, 1.0), IDENTITY, translation = Vec(0.0, .5, 0.0), grow = Vec(6.0, 4.0, 1.0),
                    slide = Vec(0.0, 2.0, 0.0), moveTicks = 10)
            }
        }
    }

    /** The counter window: a blossom opens on each horn tip and stays lit while the window is open. */
    fun counterBloom(ticks: Int, point: (String, Vec) -> Point?) {
        for (x in listOf(11.4, -11.4)) {
            val at = point("skull", Vec(x, 40.5 + 27.0 * .94 + 18.0 * .34, 27.0 + 27.0 * .34 - 18.0 * .94))?.let(Pos::fromPoint) ?: continue
            fx.add("sakura_blossom", 4, at, 0, ticks, Vec(.4, .4, 1.0), IDENTITY, grow = Vec(1.6, 1.6, 1.0), moveTicks = 4, billboard = true) { .05 }
        }
    }

    /** The enrage howl's petals bursting open: a wave rolls out to the arena wall. */
    fun roar(at: Pos, radius: Double) {
        val d = radius * 2.05
        crest(at, 9.0, IDENTITY, life = 30)
        fx.add("sakura_ring", 2, at.add(0.0, .12, 0.0), 0, 24, Vec(2.0, 1.0, 2.0), IDENTITY, grow = Vec(d, 1.0, d), moveTicks = 22) { .25 + .5 * it }
        fx.add("sakura_ring", 2, at.add(0.0, .1, 0.0), 4, 22, Vec(1.0, 1.0, 1.0), IDENTITY, grow = Vec(d * .9, 1.0, d * .9), moveTicks = 20) { .5 + .4 * it }
        fx.add("sakura_blossom", 4, at.add(0.0, 3.0, 0.0), 0, 12, Vec(3.0, 3.0, 1.0), IDENTITY, grow = Vec(7.0, 7.0, 1.0), moveTicks = 10, billboard = true)
    }

    /** A sakura crest of light blooms open on the ground (grows fast, then fades and turns slowly). */
    private fun crest(at: Pos, size: Double, rotation: FloatArray, life: Int = 18) {
        fx.add("sakura_crest", 2, at.add(0.0, .14, 0.0), 0, life, Vec(size * .3, 1.0, size * .3), rotation,
            grow = Vec(size, 1.0, size), moveTicks = 6) { .15 + .85 * it }
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
