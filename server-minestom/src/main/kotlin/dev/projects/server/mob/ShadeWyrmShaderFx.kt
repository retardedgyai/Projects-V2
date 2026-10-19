package dev.projects.server.mob

import dev.projects.server.coreloop.CoreCombatPresentation
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.CustomModelData
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Shader experiment (2026-10-17): the 黒翳竜's strikes as item displays whose colour the pack's core/item shader
 * computes (scripts/shaders/item.fsh). Each display's tint is the marker: blue 77, green = kind
 * (1 slash, 2 ring, 3 beam, 4 puff, 5 curtain, 6 smoke), red = progress, rewritten every tick. Only players with the pack
 * see them; the others keep the particles of [ShadeWyrmVfx].
 */
internal class ShadeWyrmShaderFx(private val instance: Instance, private val marker: Int = MARKER) {
    private class Piece(
        val sprite: String, val kind: Int, val at: Pos, val start: Int, val life: Int,
        val scale: Vec, val rotation: FloatArray, val translation: Vec = Vec.ZERO,
        val grow: Vec? = null, val slide: Vec? = null, val moveTicks: Int = 0, val billboard: Boolean = false,
        val progress: (Double) -> Double = { it }, val windup: Boolean = false, val tag: String? = null,
    ) { var entity: Entity? = null; var life2 = life; var sent = -1 }

    private val pieces = mutableListOf<Piece>()
    private var now = 0
    private var addingWindup = false
    val liveCount: Int get() = pieces.count { it.entity != null }

    /**
     * During the telegraph: what the wyrm is gathering, so the long wind-ups say what is coming.
     * 鱗粉炸裂 = spores settling and glowing over the circle; 触角の共鳴 = sound drawn in toward the antennae;
     * 翳の吐息 = shadow gathering in the mouth.
     */
    fun windup(abilityId: String, frame: MobAbilityFrame, head: (Vec) -> Point?) {
        val f = horizontal(frame.facing)
        val o = frame.origin
        val ticks = (frame.ability.telegraphMillis / 50).toInt()
        val shape = frame.ability.shape
        val appear: (Double) -> Double = { 1.0 - it * .85 }          // a puff materialises instead of dissolving
        addingWindup = true
        try { when (abilityId) {
            "shade-dust" -> if (shape is MobAttackShape.Ring) {
                // For the whole telegraph the spores pour off the body and push outward: a low front of dust rolls from
                // the wyrm to the circle's edge while the cloud behind it thickens.
                val r = shape.radius
                add("ring", 2, o.add(0.0, .08, 0.0), 0, ticks, Vec(r * 2.05, 1.0, r * 2.05), IDENTITY) { .1 + .25 * it * it }
                val count = 32
                for (n in 0 until count) {
                    val a = n * 2 * PI / count
                    val dir = Vec(cos(a), 0.0, sin(a))
                    val width = 2 * PI * 1.5 / count * 1.5
                    add("curtain", 5, o, 0, ticks, Vec(width, 1.8, 1.0), yawTo(dir), translation = dir.mul(1.5).add(0.0, .9, 0.0),
                        grow = Vec(width * r / 1.5, 2.2, 1.0), slide = dir.mul(r).add(0.0, 1.1, 0.0), moveTicks = ticks - 2) { .15 + .3 * it }
                }
                val body = frame.casterOrigin.add(0.0, 2.0, 0.0)
                val all = spores(r, 64)
                for ((n, spot) in all.withIndex()) {
                    val at = o.add(spot)
                    val delay = n * (ticks - 8) / all.size
                    val from = Vec(body.x() - at.x(), body.y() - at.y(), body.z() - at.z())
                    add("puff", 4, at, delay, ticks - delay, Vec(.8, .8, 1.0), IDENTITY, translation = from,
                        grow = Vec(2.3, 2.3, 1.0), slide = Vec.ZERO, moveTicks = 12, billboard = true) { .45 - .35 * it }
                }
            }
            "shade-pulse" -> if (shape is MobAttackShape.Ring) {
                // The antennae start to hum; the spores hanging round the wyrm wake and quiver with them.
                val crown = head(ShadeWyrmVfx.CROWN)?.let(Pos::fromPoint) ?: o.add(f.mul(3.5)).add(0.0, 3.2, 0.0)
                add("puff", 4, crown, 0, ticks, Vec(.8, .8, 1.0), IDENTITY, grow = Vec(2.2, 2.2, 1.0), moveTicks = ticks,
                    billboard = true, progress = appear)
                for ((n, spot) in motes(shape.radius).withIndex()) {
                    add("puff", 4, o.add(spot), n % 6, ticks - n % 6, Vec(.7, .7, 1.0), IDENTITY, grow = Vec(1.1, 1.1, 1.0),
                        moveTicks = ticks - n % 6, billboard = true) { .6 - .45 * it }
                }
            }
            "shade-dive" -> if (shape is MobAttackShape.Ring) {
                // The takeoff blows dust off the ground; once it is up, its shadow gathers and darkens where it will land.
                val caster = frame.casterOrigin
                add("ring", 2, caster.add(0.0, .1, 0.0), 12, 10, Vec(2.0, 1.0, 2.0), IDENTITY, grow = Vec(9.0, 1.0, 9.0), moveTicks = 8)
                repeat(4) { n ->
                    val a = n * PI / 2 + .5
                    add("smoke", 6, caster.add(cos(a) * 2.0, .6, sin(a) * 2.0), 12 + n % 2, 16, Vec(2.6, 2.6, 1.0), IDENTITY,
                        slide = Vec(cos(a) * 2.5, .6, sin(a) * 2.5), moveTicks = 14, billboard = true)
                }
                val from = ticks * 2 / 5
                add("disc", 6, o.add(0.0, .06, 0.0), from, ticks - from, Vec(1.5, 1.0, 1.5), IDENTITY,
                    grow = Vec(shape.radius * 2.1, 1.0, shape.radius * 2.1), moveTicks = ticks - from) { .45 - .4 * it }
                add("ring", 2, o.add(0.0, .1, 0.0), from, ticks - from, Vec(shape.radius * 2.05, 1.0, shape.radius * 2.05), IDENTITY) { .3 - .2 * it }
            }
            "shade-breath" -> {
                val mouth = head(ShadeWyrmVfx.SNOUT)?.let(Pos::fromPoint) ?: o.add(f.mul(3.5)).add(0.0, 2.5, 0.0)
                add("puff", 4, mouth, ticks / 3, ticks - ticks / 3, Vec(.6, .6, 1.0), IDENTITY, grow = Vec(2.4, 2.4, 1.0),
                    moveTicks = ticks - ticks / 3, billboard = true, progress = appear)
            }
        } } finally { addingWindup = false }
    }

    fun strike(abilityId: String, frame: MobAbilityFrame, head: (Vec) -> Point?) {
        val f = horizontal(frame.facing)
        val right = Vec(-f.z(), 0.0, f.x())
        val o = frame.origin
        val yaw = yawTo(f)
        val shape = frame.ability.shape
        when (abilityId) {
            "shade-sweep", "shade-turnsweep" -> if (shape is MobAttackShape.Sweep) {
                // Three crescents fanning up from the swing, the wing-arm's wake left as burning dust and dark smoke
                // along the arc, and a shock on the ground under it. The slash sprite spans ±90° like the ability.
                val d = shape.radius * 2.1
                val wide = 1.0
                add("slash", 1, o.add(0.0, 1.2, 0.0), 0, 9, Vec(d * wide, 1.0, d), mul(yaw, mul(qz(.28), qx(-.1))))
                add("slash", 1, o.add(0.0, 1.8, 0.0), 1, 9, Vec(d * .82 * wide, 1.0, d * .82), mul(yaw, mul(qz(.45), qx(-.18))))
                add("slash", 1, o.add(0.0, .6, 0.0), 2, 8, Vec(d * 1.05 * wide, 1.0, d * 1.05), mul(yaw, qz(.12)))
                add("ring", 2, o.add(0.0, .12, 0.0), 1, 9, Vec(2.0, 1.0, 2.0), yaw, grow = Vec(d, 1.0, d), moveTicks = 5)
                val half = Math.toRadians(shape.halfAngleDegrees)
                for (n in 0..8) {
                    val a = half - 2 * half * n / 8.0
                    val dir = f.mul(cos(a)).add(right.mul(sin(a)))
                    val at = o.add(dir.mul(shape.radius * .8)).add(0.0, 1.3 - n * .06, 0.0)
                    add("puff", 4, at, n / 3, 12, Vec(2.4, 2.4, 1.0), IDENTITY, slide = dir.mul(1.2).add(0.0, .6, 0.0),
                        grow = Vec(3.2, 3.2, 1.0), moveTicks = 11, billboard = true)
                    if (n % 2 == 0) add("smoke", 6, at.add(0.0, .6, 0.0), 2 + n / 3, 18, Vec(3.4, 3.4, 1.0), IDENTITY,
                        slide = dir.mul(1.0).add(0.0, 1.2, 0.0), moveTicks = 16, billboard = true)
                }
            }
            "shade-bite" -> if (shape is MobAttackShape.Slam) {
                // A snap of the jaws at the end of the band: a small crescent and a burst of dust where the teeth meet.
                val jaws = o.add(f.mul(shape.length * .8)).add(0.0, 1.2, 0.0)
                add("slash", 1, jaws, 0, 6, Vec(2.6, 1.0, 2.6), mul(yaw, mul(qx(PI / 2), qz(.3))))
                add("puff", 4, jaws, 0, 9, Vec(1.8, 1.8, 1.0), IDENTITY, grow = Vec(2.8, 2.8, 1.0), moveTicks = 8, billboard = true)
            }
            "shade-tailspin" -> if (shape is MobAttackShape.Ring) {
                // The tail sweeps the whole circle: two flat crescents round it and a dust wake along the rim.
                val d = shape.radius * 2.1
                add("slash", 1, o.add(0.0, .7, 0.0), 0, 9, Vec(d, 1.0, d), yaw)
                add("slash", 1, o.add(0.0, .7, 0.0), 0, 9, Vec(d, 1.0, d), mul(yaw, qy(PI)))
                add("ring", 2, o.add(0.0, .12, 0.0), 0, 9, Vec(2.0, 1.0, 2.0), IDENTITY, grow = Vec(d, 1.0, d), moveTicks = 5)
                for (n in 0 until 12) {
                    val a = n * 2 * PI / 12
                    val dir = Vec(cos(a), 0.0, sin(a))
                    add("puff", 4, o.add(dir.mul(shape.radius * .85)).add(0.0, .6, 0.0), n / 4, 11, Vec(2.0, 2.0, 1.0), IDENTITY,
                        slide = dir.mul(1.0).add(0.0, .5, 0.0), moveTicks = 10, billboard = true)
                }
            }
            "shade-horn" -> if (shape is MobAttackShape.Slam) {
                // The head heaves up: a rising ring at the tip of the thrust and dust thrown upward.
                val tip = o.add(f.mul(shape.length * .75))
                add("ring_up", 2, tip.add(0.0, 1.6, 0.0), 0, 8, Vec(1.0, 1.0, 1.0), yaw, grow = Vec(4.0, 4.0, 1.0), moveTicks = 6)
                add("ring", 2, tip.add(0.0, .12, 0.0), 0, 9, Vec(1.5, 1.0, 1.5), yaw, grow = Vec(5.0, 1.0, 5.0), moveTicks = 5)
                repeat(4) { n ->
                    add("puff", 4, tip.add(right.mul((n - 1.5) * .6)).add(0.0, .8, 0.0), n / 2, 13, Vec(2.2, 2.2, 1.0), IDENTITY,
                        slide = Vec(0.0, 2.4, 0.0), moveTicks = 12, billboard = true)
                }
            }
            "shade-tailslap" -> if (shape is MobAttackShape.Sweep) {
                // The tail comes down behind it (facing points at the player behind): a vertical crescent and a shock.
                val down = o.add(f.mul(shape.radius * .7))
                add("slash", 1, down.add(0.0, 1.4, 0.0), 0, 7, Vec(3.6, 1.0, 3.6), mul(yaw, qz(PI / 2)))
                add("ring", 2, down.add(0.0, .12, 0.0), 0, 9, Vec(1.5, 1.0, 1.5), yaw, grow = Vec(5.5, 1.0, 5.5), moveTicks = 5)
                add("puff", 4, down.add(0.0, .6, 0.0), 0, 12, Vec(2.6, 2.6, 1.0), IDENTITY, grow = Vec(3.6, 3.6, 1.0), moveTicks = 10, billboard = true)
            }
            "shade-charge" -> if (shape is MobAttackShape.Slam) {
                // The charge tears the ground along its band: dust thrown up behind the running body, a shock at the end.
                for (n in 0 until 10) {
                    val at = o.add(f.mul(shape.length * (n + .5) / 10)).add(0.0, .5, 0.0)
                    add("puff", 4, at, n, 12, Vec(2.2, 2.2, 1.0), IDENTITY, slide = right.mul(if (n % 2 == 0) 1.4 else -1.4).add(0.0, .8, 0.0),
                        grow = Vec(3.2, 3.2, 1.0), moveTicks = 11, billboard = true)
                    if (n % 3 == 0) add("smoke", 6, at.add(0.0, .4, 0.0), n + 2, 16, Vec(2.6, 2.6, 1.0), IDENTITY, slide = Vec(0.0, 1.2, 0.0), moveTicks = 14, billboard = true)
                }
                val end = o.add(f.mul(shape.length))
                add("ring", 2, end.add(0.0, .12, 0.0), 10, 10, Vec(1.5, 1.0, 1.5), yaw, grow = Vec(6.0, 1.0, 6.0), moveTicks = 6)
            }
            "shade-channel" -> if (shape is MobAttackShape.Ring) {
                // The plunge lands: one shock wave rolls across the whole arena, dust erupting along it.
                val d = shape.radius * 2.05
                add("ring", 2, o.add(0.0, .12, 0.0), 0, 14, Vec(2.0, 1.0, 2.0), IDENTITY, grow = Vec(d, 1.0, d), moveTicks = 12) { .2 + .6 * it }
                add("ring", 2, o.add(0.0, .1, 0.0), 2, 14, Vec(1.0, 1.0, 1.0), IDENTITY, grow = Vec(d * .9, 1.0, d * .9), moveTicks = 12)
                add("puff", 4, o.add(0.0, 1.6, 0.0), 0, 14, Vec(5.0, 5.0, 1.0), IDENTITY, grow = Vec(10.0, 10.0, 1.0), moveTicks = 12, billboard = true)
                for (n in 0 until 24) {
                    val a = n * 2 * PI / 24
                    val dir = Vec(cos(a), 0.0, sin(a))
                    val r = 4.0 + (n % 4) * 4.5
                    add("puff", 4, o.add(dir.mul(r)).add(0.0, .8, 0.0), (r / 2).toInt(), 12, Vec(2.6, 2.6, 1.0), IDENTITY,
                        slide = Vec(0.0, 1.6, 0.0), moveTicks = 11, billboard = true)
                }
            }
            "shade-shell" -> if (shape is MobAttackShape.Ring) {
                // The shell bursts: shards of hardened dust flung out and a ring the size of the blast.
                val d = shape.radius * 2.05
                add("ring", 2, o.add(0.0, .12, 0.0), 0, 10, Vec(2.0, 1.0, 2.0), IDENTITY, grow = Vec(d, 1.0, d), moveTicks = 6)
                for (n in 0 until 16) {
                    val a = n * 2 * PI / 16
                    val dir = Vec(cos(a), 0.0, sin(a))
                    add("curtain", 5, o, 0, 10, Vec(1.4, 2.2, 1.0), yawTo(dir), translation = dir.mul(3.0).add(0.0, 1.2, 0.0),
                        slide = dir.mul(shape.radius).add(0.0, 1.0, 0.0), moveTicks = 8)
                    if (n % 2 == 0) add("puff", 4, o.add(dir.mul(4.0)).add(0.0, 1.2, 0.0), n % 3, 12, Vec(2.8, 2.8, 1.0), IDENTITY,
                        slide = dir.mul(4.0), moveTicks = 10, billboard = true)
                }
            }
            "shade-orb" -> if (shape is MobAttackShape.Ring) {
                // The clot lands and bursts: a dust burst, a ring the size of the blast, smoke rising (a pool stays).
                val r = shape.radius
                add("puff", 4, o.add(0.0, .8, 0.0), 0, 12, Vec(3.0, 3.0, 1.0), IDENTITY, grow = Vec(4.6, 4.6, 1.0), moveTicks = 10, billboard = true)
                add("ring", 2, o.add(0.0, .12, 0.0), 0, 9, Vec(1.0, 1.0, 1.0), IDENTITY, grow = Vec(r * 2.1, 1.0, r * 2.1), moveTicks = 5)
                add("smoke", 6, o.add(0.0, 1.4, 0.0), 2, 18, Vec(3.2, 3.2, 1.0), IDENTITY, slide = Vec(0.0, 1.4, 0.0), moveTicks = 16, billboard = true)
            }
            "shade-pounce" -> if (shape is MobAttackShape.Slam) {
                // Both wing-arms slam down side by side: a crater under each, the ground cracking out, one wide shock.
                val mid = o.add(f.mul(shape.length * .58))
                add("ring", 2, mid.add(0.0, .12, 0.0), 0, 12, Vec(2.0, 1.0, 2.0), yaw, grow = Vec(shape.halfWidth * 3.4, 1.0, shape.halfWidth * 3.4), moveTicks = 7)
                for (side in listOf(-1.0, 1.0)) {
                    val hand = mid.add(right.mul(side * shape.halfWidth * .55))
                    add("ring", 2, hand.add(0.0, .16, 0.0), 0, 10, Vec(1.0, 1.0, 1.0), yaw, grow = Vec(4.2, 1.0, 4.2), moveTicks = 5)
                    for (k in 0..4) {
                        val a = atan2(f.x(), f.z()) + side * (.35 + k * .55)
                        val dir = Vec(kotlin.math.sin(a), 0.0, kotlin.math.cos(a))
                        add("beam", 3, hand.add(dir.mul(1.9)).add(0.0, .1, 0.0), k % 2, 12, Vec(.9, 1.0, 3.6), yawTo(dir))
                    }
                    repeat(3) { n ->
                        add("puff", 4, hand.add(right.mul(side * (n - 1) * .7)).add(f.mul((n - 1) * .5)).add(0.0, .7 + n * .35, 0.0), n, 16,
                            Vec(3.6, 3.6, 1.0), IDENTITY, slide = Vec(side * right.x() * 1.2, 1.0, side * right.z() * 1.2), moveTicks = 14, billboard = true)
                    }
                }
            }
            "shade-dust" -> if (shape is MobAttackShape.Ring) {
                // The feet land and every hanging spore goes off at once.
                val r = shape.radius
                add("ring", 2, o.add(0.0, .15, 0.0), 0, 10, Vec(r, 1.0, r), IDENTITY, grow = Vec(r * 2.6, 1.0, r * 2.6), moveTicks = 5)
                for ((n, spot) in spores(r, 64).withIndex()) {
                    val at = o.add(spot)
                    val size = 2.2 + (n % 4) * .45
                    add("puff", 4, at, n % 2, 13, Vec(size, size, 1.0), IDENTITY, grow = Vec(size * 1.5, size * 1.5, 1.0), moveTicks = 10, billboard = true)
                    if (n % 2 == 0) add("smoke", 6, at.add(0.0, .4, 0.0), 2 + n % 3, 18, Vec(size * 1.2, size * 1.2, 1.0), IDENTITY,
                        slide = Vec(0.0, 1.2, 0.0), moveTicks = 16, billboard = true)
                    if (n % 3 == 0) add("ring", 2, at.withY(o.y() + .12), 0, 8, Vec(.8, 1.0, .8), IDENTITY, grow = Vec(2.6, 1.0, 2.6), moveTicks = 4)
                }
            }
            "shade-pulse" -> if (shape is MobAttackShape.Ring) {
                // The resonance peaks: the head flashes, a low shock runs out and the quivering spores burst outward.
                val d = shape.radius * 2.0
                val crown = head(ShadeWyrmVfx.CROWN)?.let(Pos::fromPoint) ?: o.add(f.mul(3.5)).add(0.0, 3.2, 0.0)
                add("puff", 4, crown, 0, 8, Vec(3.2, 3.2, 1.0), IDENTITY, billboard = true)
                add("ring_up", 2, crown, 0, 7, Vec(1.0, 1.0, 1.0), IDENTITY, grow = Vec(d * .8, d * .8, 1.0), moveTicks = 5, billboard = true)
                add("ring", 2, o.add(0.0, .15, 0.0), 0, 8, Vec(1.5, 1.0, 1.5), IDENTITY, grow = Vec(d, 1.0, d), moveTicks = 4)
                val count = 22
                for (n in 0 until count) {
                    val a = n * 2 * PI / count
                    val dir = Vec(cos(a), 0.0, sin(a))
                    val width = 2 * PI * 1.4 / count * 1.4
                    add("curtain", 5, o, 0, 8, Vec(width, 1.8, 1.0), yawTo(dir), translation = dir.mul(1.4).add(0.0, .9, 0.0),
                        grow = Vec(width * shape.radius / 1.4, 1.8, 1.0), slide = dir.mul(shape.radius).add(0.0, .9, 0.0), moveTicks = 4)
                }
                for ((n, spot) in motes(shape.radius).withIndex()) {
                    val out = Vec(spot.x(), 0.0, spot.z()).normalize()
                    add("puff", 4, o.add(spot), n % 2, 9, Vec(1.8, 1.8, 1.0), IDENTITY, slide = out.mul(1.8), grow = Vec(2.4, 2.4, 1.0),
                        moveTicks = 8, billboard = true)
                }
            }
            "shade-breath" -> if (shape is MobAttackShape.Slam) {
                // A torrent of shadow dust, not a ray: bright clouds rolling out and widening, dark billows behind them.
                val mouth = head(ShadeWyrmVfx.SNOUT) ?: o.add(f.mul(3.5)).add(0.0, 2.5, 0.0)
                val touch = o.add(f.mul(shape.length * .35)).add(0.0, .8, 0.0)
                val along = touch.sub(mouth)
                val pitch = atan2(-along.y(), hypot(along.x(), along.z()))
                val mid = Pos(mouth.x() + along.x() / 2, mouth.y() + along.y() / 2, mouth.z() + along.z() / 2)
                for (roll in listOf(0.0, PI / 2)) {
                    add("beam_up", 3, mid, 0, 14, Vec(along.distance(Vec.ZERO), shape.halfWidth * 1.4, 1.0),
                        mul(mul(qy(atan2(-f.z(), f.x())), qz(-pitch)), qx(roll)))
                }
                for (n in 0 until 20) {
                    val t = n / 19.0
                    val start = Pos(mouth.x(), mouth.y(), mouth.z()).add(f.mul(.5))
                    val at = o.add(f.mul(2.0 + t * (shape.length - 2.5))).add(right.mul(((n * 7) % 5 - 2) * .25 * shape.halfWidth))
                    val y = start.y() + (1.0 - start.y() + o.y()) * kotlin.math.min(1.0, t * 2.5) - o.y()
                    val size = 2.4 + t * shape.halfWidth * 2.0
                    add("puff", 4, at.withY(o.y() + y), n / 3, 15, Vec(size, size, 1.0), IDENTITY,
                        slide = f.mul(2.0).add(0.0, 1.0, 0.0), grow = Vec(size * 1.5, size * 1.5, 1.0), moveTicks = 13, billboard = true)
                }
                repeat(4) { n ->
                    add("puff", 4, o.add(f.mul(shape.length - 1.5)).add(right.mul((n - 1.5) * shape.halfWidth * .6)).add(0.0, 1.0 + n % 2, 0.0),
                        5 + n, 16, Vec(4.0, 4.0, 1.0), IDENTITY, billboard = true)
                }
                add("puff", 4, Pos.fromPoint(mouth), 0, 7, Vec(3.4, 3.4, 1.0), IDENTITY, billboard = true)
                for (n in 0 until 10) {                       // dark billows rolling behind and above the bright dust
                    val t = (n + .5) / 10.0
                    val size = 3.4 + t * shape.halfWidth * 2.2
                    add("smoke", 6, o.add(f.mul(2.5 + t * (shape.length - 3.0))).add(right.mul(((n * 3) % 5 - 2) * .3 * shape.halfWidth))
                        .add(0.0, 1.6 + t, 0.0), 1 + n / 2, 20, Vec(size, size, 1.0), IDENTITY,
                        slide = f.mul(1.5).add(0.0, 1.4, 0.0), grow = Vec(size * 1.3, size * 1.3, 1.0), moveTicks = 18, billboard = true)
                }
            }
            "shade-dive" -> if (shape is MobAttackShape.Ring) {
                // It hits the ground nose first: a flash, a shock running out with dust kicked up behind it, dark billows.
                val r = shape.radius
                add("puff", 4, o.add(0.0, 1.2, 0.0), 0, 8, Vec(4.5, 4.5, 1.0), IDENTITY, billboard = true)
                add("ring", 2, o.add(0.0, .12, 0.0), 0, 10, Vec(2.0, 1.0, 2.0), IDENTITY, grow = Vec(r * 2.8, 1.0, r * 2.8), moveTicks = 6)
                val count = 20
                for (n in 0 until count) {
                    val a = n * 2 * PI / count
                    val dir = Vec(cos(a), 0.0, sin(a))
                    val width = 2 * PI * 1.2 / count * 1.4
                    add("curtain", 5, o, 0, 10, Vec(width, 2.4, 1.0), yawTo(dir), translation = dir.mul(1.2).add(0.0, 1.2, 0.0),
                        grow = Vec(width * r / 1.2, 2.4, 1.0), slide = dir.mul(r).add(0.0, 1.2, 0.0), moveTicks = 5)
                }
                repeat(8) { n ->
                    val a = n * 2 * PI / 8 + .3; val rr = r * (if (n % 2 == 0) .55 else .85)
                    add("puff", 4, o.add(cos(a) * rr, .7, sin(a) * rr), n % 3, 14, Vec(2.8, 2.8, 1.0), IDENTITY,
                        slide = Vec(cos(a) * 1.4, .9, sin(a) * 1.4), moveTicks = 12, billboard = true)
                    if (n % 2 == 1) add("smoke", 6, o.add(cos(a) * rr, 1.2, sin(a) * rr), 2, 20, Vec(3.6, 3.6, 1.0), IDENTITY,
                        slide = Vec(cos(a) * 1.0, 1.6, sin(a) * 1.0), moveTicks = 18, billboard = true)
                }
            }
            "shade-storm" -> if (shape is MobAttackShape.Ring) {
                val count = 28
                for (n in 0 until count) {
                    val a = n * 2 * PI / count
                    val dir = Vec(cos(a), 0.0, sin(a))
                    val width = 2 * PI * shape.innerRadius / count * 1.35
                    add("curtain", 5, o, n % 2, 16, Vec(width, 4.2, 1.0), yawTo(dir), translation = dir.mul(shape.innerRadius).add(0.0, 2.1, 0.0),
                        grow = Vec(width * shape.radius / shape.innerRadius, 4.2, 1.0), slide = dir.mul(shape.radius).add(0.0, 2.1, 0.0), moveTicks = 8)
                }
                add("ring", 2, o.add(0.0, .15, 0.0), 0, 14, Vec(shape.innerRadius * 2, 1.0, shape.innerRadius * 2), yaw,
                    grow = Vec(shape.radius * 2.05, 1.0, shape.radius * 2.05), moveTicks = 8)
            }
        }
    }

    fun tick() {
        now++
        val iterator = pieces.iterator()
        while (iterator.hasNext()) {
            val piece = iterator.next()
            val age = now - piece.start
            if (age < 0) continue
            if (age >= piece.life2) { piece.entity?.remove(); iterator.remove(); continue }
            val entity = piece.entity ?: spawn(piece).also { piece.entity = it }
            val red = (piece.progress(age.toDouble() / piece.life).coerceIn(0.0, 1.0) * 255).toInt()
            if (red == piece.sent && !(age == 1 && piece.moveTicks > 0)) continue   // long-lived pieces: no resend
            piece.sent = red
            entity.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                meta.setItemStack(stack(piece, piece.progress(age.toDouble() / piece.life)))
                if (age == 1 && piece.moveTicks > 0) {
                    meta.setTransformationInterpolationStartDelta(0)
                    meta.setTransformationInterpolationDuration(piece.moveTicks)
                    piece.grow?.let { meta.setScale(it) }
                    piece.slide?.let { meta.setTranslation(it) }
                }
            }
        }
    }

    /** What a client shows now (transform interpolation included), for the CPU preview dump. */
    internal data class View(val sprite: String, val kind: Int, val progress: Double, val at: Pos, val scale: Vec,
                             val rotation: FloatArray, val translation: Vec, val billboard: Boolean)

    internal fun views(): List<View> = pieces.filter { it.entity != null }.map { p ->
        val age = now - p.start
        val k = if (p.moveTicks > 0) ((age - 1).toDouble() / p.moveTicks).coerceIn(0.0, 1.0) else 0.0
        fun lerp(a: Vec, b: Vec?) = if (b == null) a else a.add(b.sub(a).mul(k))
        View(p.sprite, p.kind, p.progress(age.toDouble() / p.life), p.at, lerp(p.scale, p.grow), p.rotation, lerp(p.translation, p.slide), p.billboard)
    }

    fun dispose() {
        pieces.forEach { it.entity?.remove() }
        pieces.clear()
    }

    /** One shader piece; [marker] picks the palette in item.fsh (77 the wyrm's violet, 79 桜牙獣's sakura, see SakuraStrikeFx). */
    internal fun add(sprite: String, kind: Int, at: Pos, delay: Int, life: Int, scale: Vec, rotation: FloatArray,
                    translation: Vec = Vec.ZERO, grow: Vec? = null, slide: Vec? = null, moveTicks: Int = 0, billboard: Boolean = false,
                    progress: (Double) -> Double = { it }) {
        if (life <= 0) return
        pieces += Piece(sprite, kind, at, now + 1 + delay, life, scale, rotation, translation, grow, slide, moveTicks, billboard, progress,
            addingWindup, addingTag)
    }
    private var addingTag: String? = null

    /** Ends every piece added under [tag] (a pool) on the next tick. */
    fun endTag(tag: String) {
        pieces.forEach { if (it.tag == tag) it.life2 = minOf(it.life2, now - it.start + 1) }
    }

    /** 鱗粉だまり: a dark, slowly breathing puddle with a faint violet rim and dust drifting up, for [ticks]. */
    fun pool(at: Pos, tag: String, ticks: Int) {
        addingTag = tag
        try {
            add("disc", 6, at.add(0.0, .05, 0.0), 0, ticks, Vec(1.0, 1.0, 1.0), IDENTITY, grow = Vec(3.8, 1.0, 3.8), moveTicks = 8) { .28 }
            add("ring", 2, at.add(0.0, .08, 0.0), 0, ticks, Vec(1.0, 1.0, 1.0), IDENTITY, grow = Vec(3.9, 1.0, 3.9), moveTicks = 8) { .55 }
            add("puff", 4, at.add(0.0, .7, 0.0), 4, ticks - 4, Vec(1.4, 1.4, 1.0), IDENTITY, billboard = true) { .45 }
        } finally { addingTag = null }
    }

    /** The pulse roared: the pool glows hot before it bursts. */
    fun poolWarning(at: Pos, tag: String) {
        addingTag = tag
        try { add("puff", 4, at.add(0.0, .9, 0.0), 0, 16, Vec(1.6, 1.6, 1.0), IDENTITY, grow = Vec(2.6, 2.6, 1.0), moveTicks = 15, billboard = true) { .15 } }
        finally { addingTag = null }
    }

    fun poolBurst(at: Pos, tag: String) {
        endTag(tag)
        add("puff", 4, at.add(0.0, 1.0, 0.0), 0, 12, Vec(3.5, 3.5, 1.0), IDENTITY, grow = Vec(5.0, 5.0, 1.0), moveTicks = 10, billboard = true)
        add("ring", 2, at.add(0.0, .1, 0.0), 0, 10, Vec(2.0, 1.0, 2.0), IDENTITY, grow = Vec(7.0, 1.0, 7.0), moveTicks = 6)
        add("smoke", 6, at.add(0.0, 1.8, 0.0), 2, 18, Vec(3.6, 3.6, 1.0), IDENTITY, slide = Vec(0.0, 1.4, 0.0), moveTicks = 16, billboard = true)
    }

    /** The enrage roar: a violet wave growing from the wyrm to [radius] over [ticks], a second one just behind it. */
    fun rageWave(at: Pos, radius: Double, ticks: Int) {
        val d = radius * 2.05
        add("ring", 2, at.add(0.0, .12, 0.0), 0, ticks + 2, Vec(2.0, 1.0, 2.0), IDENTITY, grow = Vec(d, 1.0, d), moveTicks = ticks) { .25 + .5 * it }
        add("ring", 2, at.add(0.0, .1, 0.0), 3, ticks, Vec(1.0, 1.0, 1.0), IDENTITY, grow = Vec(d * .9, 1.0, d * .9), moveTicks = ticks - 1) { .5 + .4 * it }
        add("puff", 4, at.add(0.0, 2.5, 0.0), 0, 10, Vec(3.0, 3.0, 1.0), IDENTITY, grow = Vec(7.0, 7.0, 1.0), moveTicks = 8, billboard = true)
    }

    /** A wind-up tell: a glow gathering on the part that will strike (the wing tip, the jaws, the crown, the tail tip). */
    fun cue(at: Pos, ticks: Int) {
        addingWindup = true
        try {
            add("puff", 4, at, 0, ticks, Vec(.5, .5, 1.0), IDENTITY, grow = Vec(1.9, 1.9, 1.0), moveTicks = ticks, billboard = true) { .25 + .6 * it }
        } finally { addingWindup = false }
    }

    /** 鱗粉弾 in flight: a glowing clot leaves the jaws after [delay] ticks and travels to where it lands in [ticks]. */
    fun orb(from: Pos, to: Pos, delay: Int, ticks: Int) {
        val path = Vec(to.x() - from.x(), to.y() + .6 - from.y(), to.z() - from.z())
        addingWindup = true                              // a cancelled throw takes its clot with it (cancelWindup)
        try {
        add("puff", 4, from, delay, ticks + 1, Vec(1.4, 1.4, 1.0), IDENTITY, slide = path, moveTicks = ticks, billboard = true) { .55 }
        add("smoke", 6, from, delay + 1, ticks + 2, Vec(1.2, 1.2, 1.0), IDENTITY, slide = path.mul(.92), moveTicks = ticks, billboard = true) { .3 }
        } finally { addingWindup = false }
    }

    /** 翳の大詠唱: dust drawn up off the whole floor toward the wyrm hanging above [at], for [ticks]. */
    fun channel(at: Pos, tag: String, ticks: Int) {
        addingTag = tag
        try {
            add("ring", 2, at.add(0.0, .1, 0.0), 0, ticks, Vec(43.0, 1.0, 43.0), IDENTITY) { .1 + .35 * it * it }
            for (n in 0 until 18) {
                val a = n * 2 * PI / 18
                val from = Vec(cos(a), 0.0, sin(a)).mul(6.0 + (n % 3) * 4.0)
                val start = (n * 13) % ticks.coerceAtLeast(1) / 3
                add("puff", 4, at.add(from).add(0.0, .6, 0.0), start, ticks - start, Vec(1.4, 1.4, 1.0), IDENTITY,
                    slide = from.mul(-1.0).add(0.0, 3.2, 0.0), moveTicks = ticks - start, billboard = true) { .35 + .4 * it }
            }
        } finally { addingTag = null }
    }

    /** 鱗粉の殻: a shell of hardened dust round the curled-up wyrm at [at] while it holds, for [ticks]. */
    fun shell(at: Pos, tag: String, ticks: Int) {
        addingTag = tag
        try {
            add("ring", 2, at.add(0.0, .1, 0.0), 0, ticks, Vec(2.0, 1.0, 2.0), IDENTITY, grow = Vec(8.0, 1.0, 8.0), moveTicks = 12) { .45 }
            for (n in 0 until 14) {
                val a = n * 2 * PI / 14
                val dir = Vec(cos(a), 0.0, sin(a))
                add("curtain", 5, at, 2 + n % 3, ticks - 4, Vec(1.0, .5, 1.0), yawTo(dir), translation = dir.mul(3.4).add(0.0, .2, 0.0),
                    grow = Vec(1.9, 4.2, 1.0), slide = dir.mul(3.4).add(0.0, 2.0, 0.0), moveTicks = 12) { .55 + .2 * it }
            }
            add("disc", 6, at.add(0.0, 4.0, 0.0), 6, ticks - 8, Vec(1.0, 1.0, 1.0), IDENTITY, grow = Vec(6.4, 1.0, 6.4), moveTicks = 10) { .4 }
        } finally { addingTag = null }
    }

    /** The shell shattered: shards flung out from [at]. */
    fun shellBreak(at: Pos, tag: String) {
        endTag(tag)
        for (n in 0 until 12) {
            val a = n * 2 * PI / 12
            val dir = Vec(cos(a), 0.0, sin(a))
            add("curtain", 5, at, 0, 9, Vec(1.6, 2.4, 1.0), yawTo(dir), translation = dir.mul(3.4).add(0.0, 1.4, 0.0),
                slide = dir.mul(6.0).add(0.0, -.6, 0.0), moveTicks = 8)
        }
        add("puff", 4, at.add(0.0, 2.0, 0.0), 0, 12, Vec(4.0, 4.0, 1.0), IDENTITY, grow = Vec(7.0, 7.0, 1.0), moveTicks = 10, billboard = true)
    }

    /** The player's erosion erupted: a violet burst around them. */
    fun erupt(at: Pos) {
        add("puff", 4, at.add(0.0, 1.0, 0.0), 0, 12, Vec(2.6, 2.6, 1.0), IDENTITY, grow = Vec(4.0, 4.0, 1.0), moveTicks = 10, billboard = true)
        add("ring", 2, at.add(0.0, .1, 0.0), 0, 10, Vec(1.0, 1.0, 1.0), IDENTITY, grow = Vec(4.5, 1.0, 4.5), moveTicks = 6)
    }

    /** A cancelled telegraph: its wind-up pieces go, strikes already flying stay. */
    fun cancelWindup() {
        pieces.removeAll { piece -> piece.windup.also { if (it) piece.entity?.remove() } }
    }

    private fun spawn(piece: Piece): Entity = Entity(EntityType.ITEM_DISPLAY).apply {
        setNoGravity(true)
        setHasPhysics(false)
        updateViewableRule { CoreCombatPresentation.packed(it) }
        editEntityMeta(ItemDisplayMeta::class.java) { meta ->
            meta.setDisplayContext(ItemDisplayMeta.DisplayContext.NONE)
            meta.setItemStack(stack(piece, piece.progress(0.0)))
            meta.setScale(piece.scale)
            meta.setLeftRotation(piece.rotation)
            meta.setTranslation(piece.translation)
            meta.setBrightness(15, 15)
            meta.setViewRange(3f)
            meta.setShadowRadius(0f)
            if (piece.billboard) meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
        }
        setInstance(this@ShadeWyrmShaderFx.instance, piece.at)
    }

    private fun stack(piece: Piece, progress: Double): ItemStack {
        val red = (progress.coerceIn(0.0, 1.0) * 255).toInt()  // the shader's progress
        return ItemStack.of(Material.PAPER).withItemModel("projects:boss_vfx/${piece.sprite}")
            .with(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(emptyList(), emptyList(), emptyList(),
                listOf(TextColor.color(red, piece.kind, marker))))
    }

    /** Where the dust spores hang: a sunflower spread over the circle, low to chest height. */
    private fun spores(radius: Double, count: Int = 26): List<Vec> = (0 until count).map { n ->
        val r = radius * .95 * kotlin.math.sqrt((n + .5) / count)
        val a = n * 2.39996
        Vec(cos(a) * r, .5 + (n % 4) * .35, sin(a) * r)
    }

    /** Spores floating round the wyrm for the pulse, in the ring it hurts. */
    private fun motes(radius: Double): List<Vec> = (0 until 16).map { n ->
        val r = 1.6 + (radius - 1.8) * ((n * 7) % 16) / 15.0
        val a = n * 2 * PI / 16 + (n % 2) * .2
        Vec(cos(a) * r, .6 + (n % 5) * .5, sin(a) * r)
    }

    companion object {
        const val MARKER = 77
        private val IDENTITY = floatArrayOf(0f, 0f, 0f, 1f)

        private fun horizontal(v: Vec): Vec {
            val l = hypot(v.x(), v.z())
            return if (l < 1e-6) Vec(0.0, 0.0, 1.0) else Vec(v.x() / l, 0.0, v.z() / l)
        }

        /** Rotation about Y that turns local +Z toward the horizontal direction d. */
        fun yawTo(d: Vec): FloatArray = qy(atan2(d.x(), d.z()))

        fun qy(a: Double) = floatArrayOf(0f, sin(a / 2).toFloat(), 0f, cos(a / 2).toFloat())
        fun qz(a: Double) = floatArrayOf(0f, 0f, sin(a / 2).toFloat(), cos(a / 2).toFloat())
        fun qx(a: Double) = floatArrayOf(sin(a / 2).toFloat(), 0f, 0f, cos(a / 2).toFloat())

        /** Hamilton product a*b (apply b, then a); quaternions as x, y, z, w. */
        fun mul(a: FloatArray, b: FloatArray) = floatArrayOf(
            a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
            a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
            a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
            a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2],
        )
    }
}
