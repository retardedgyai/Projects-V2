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
 * computes (assets/bosses/shaders/item.fsh). Each display's tint is the marker: blue 77, green = kind
 * (1 slash, 2 ring, 3 beam, 4 puff, 5 curtain, 6 smoke), red = progress, rewritten every tick. Only players with the pack
 * see them; the others keep the particles of [ShadeWyrmVfx].
 */
internal class ShadeWyrmShaderFx(private val instance: Instance) {
    private class Piece(
        val sprite: String, val kind: Int, val at: Pos, val start: Int, val life: Int,
        val scale: Vec, val rotation: FloatArray, val translation: Vec = Vec.ZERO,
        val grow: Vec? = null, val slide: Vec? = null, val moveTicks: Int = 0, val billboard: Boolean = false,
        val progress: (Double) -> Double = { it }, val windup: Boolean = false,
    ) { var entity: Entity? = null }

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
                // Spores shaken off the wings drift out over the circle and hang there, glowing brighter.
                val d = shape.radius * 2.05
                add("ring", 2, o.add(0.0, .08, 0.0), 0, ticks, Vec(d, 1.0, d), IDENTITY) { .1 + .25 * it * it }
                val wings = frame.casterOrigin.add(0.0, 3.2, 0.0)
                for ((n, spot) in spores(shape.radius).withIndex()) {
                    val at = o.add(spot)
                    val delay = n % 8
                    val from = Vec(wings.x() - at.x() + (n % 3 - 1) * 1.5, wings.y() - at.y(), wings.z() - at.z())
                    add("puff", 4, at, delay, ticks - delay, Vec(.5, .5, 1.0), IDENTITY, translation = from,
                        grow = Vec(1.3, 1.3, 1.0), slide = Vec.ZERO, moveTicks = 14 + n % 6, billboard = true) { .55 - .4 * it }
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
            "shade-sweep" -> if (shape is MobAttackShape.Sweep) {
                val d = shape.radius * 2.1
                // Tilted like the wing-arm's swing: high where it starts (right), low across the front.
                add("slash", 1, o.add(0.0, 1.4, 0.0), 0, 9, Vec(d, 1.0, d), mul(yaw, mul(qz(.32), qx(-.12))))
                add("slash", 1, o.add(0.0, 1.9, 0.0), 1, 8, Vec(d * .8, 1.0, d * .8), mul(yaw, mul(qz(.5), qx(-.2))))
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
                for ((n, spot) in spores(r).withIndex()) {
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
                // A torrent of shadow dust, not a ray: billowing clouds rolling out and widening, the ground scorched.
                val mouth = head(ShadeWyrmVfx.SNOUT) ?: o.add(f.mul(3.5)).add(0.0, 2.5, 0.0)
                add("beam", 3, o.add(f.mul(shape.length / 2)).add(0.0, .1, 0.0), 1, 18, Vec(shape.halfWidth * 2.3, 1.0, shape.length), yaw)
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
                val edges = (shape.length / 1.6).toInt()
                for (n in 0 until edges) for (side in listOf(-1.0, 1.0)) {    // dust kicked up along both edges as it passes
                    val at = o.add(f.mul(1.5 + n * 1.6)).add(right.mul(side * shape.halfWidth))
                    add("curtain", 5, at.add(0.0, 1.1, 0.0), 1 + n / 2, 14, Vec(1.9, 2.2, 1.0), yawTo(right.mul(side)))
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
            if (age >= piece.life) { piece.entity?.remove(); iterator.remove(); continue }
            val entity = piece.entity ?: spawn(piece).also { piece.entity = it }
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

    private fun add(sprite: String, kind: Int, at: Pos, delay: Int, life: Int, scale: Vec, rotation: FloatArray,
                    translation: Vec = Vec.ZERO, grow: Vec? = null, slide: Vec? = null, moveTicks: Int = 0, billboard: Boolean = false,
                    progress: (Double) -> Double = { it }) {
        if (life <= 0) return
        pieces += Piece(sprite, kind, at, now + 1 + delay, life, scale, rotation, translation, grow, slide, moveTicks, billboard, progress, addingWindup)
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
        val red = (progress.coerceIn(0.0, 1.0) * 255).toInt()
        return ItemStack.of(Material.PAPER).withItemModel("projects:boss_vfx/${piece.sprite}")
            .with(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(emptyList(), emptyList(), emptyList(),
                listOf(TextColor.color(red, piece.kind, MARKER))))
    }

    /** Where the dust spores hang: a sunflower spread over the circle, low to chest height. */
    private fun spores(radius: Double): List<Vec> = (0 until 18).map { n ->
        val r = radius * .95 * kotlin.math.sqrt((n + .5) / 18)
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
