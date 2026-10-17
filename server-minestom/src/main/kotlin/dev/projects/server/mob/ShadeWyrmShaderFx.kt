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
 * (1 slash, 2 ring, 3 beam, 4 puff, 5 curtain), red = progress, rewritten every tick. Only players with the pack
 * see them; the others keep the particles of [ShadeWyrmVfx].
 */
internal class ShadeWyrmShaderFx(private val instance: Instance) {
    private class Piece(
        val sprite: String, val kind: Int, val at: Pos, val start: Int, val life: Int,
        val scale: Vec, val rotation: FloatArray, val translation: Vec = Vec.ZERO,
        val grow: Vec? = null, val slide: Vec? = null, val moveTicks: Int = 0, val billboard: Boolean = false,
    ) { var entity: Entity? = null }

    private val pieces = mutableListOf<Piece>()
    private var now = 0
    val liveCount: Int get() = pieces.count { it.entity != null }

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
                val landing = o.add(f.mul(2.0))
                add("ring", 2, landing.add(0.0, .15, 0.0), 0, 10, Vec(1.0, 1.0, 1.0), yaw, grow = Vec(6.5, 1.0, 6.5), moveTicks = 6)
                add("beam", 3, o.add(f.mul(2.0 + (shape.length - 2.0) / 2)).add(0.0, .12, 0.0), 1, 12,
                    Vec(shape.halfWidth * 2, 1.0, shape.length - 2.0), yaw)
                repeat(4) { n ->
                    val a = n * PI / 2 + .4
                    add("puff", 4, landing.add(cos(a) * 1.1, .8 + n % 2 * .5, sin(a) * 1.1), n % 2, 16, Vec(2.6, 2.6, 2.6), IDENTITY, billboard = true)
                }
            }
            "shade-dust" -> if (shape is MobAttackShape.Ring) {
                add("ring", 2, o.add(0.0, .15, 0.0), 0, 9, Vec(1.0, 1.0, 1.0), yaw, grow = Vec(shape.radius * 2.1, 1.0, shape.radius * 2.1), moveTicks = 5)
                repeat(7) { n ->
                    val a = n * 2 * PI / 7; val r = if (n == 0) 0.0 else 1.9
                    add("puff", 4, o.add(cos(a) * r, .7 + (n % 3) * .6, sin(a) * r), n % 3, 18, Vec(3.0 + n % 2 * .7, 3.0 + n % 2 * .7, 1.0), IDENTITY, billboard = true)
                }
            }
            "shade-pulse" -> if (shape is MobAttackShape.Ring) {
                val d = shape.radius * 2.0
                add("ring", 2, o.add(0.0, .3, 0.0), 0, 9, Vec(1.5, 1.0, 1.5), yaw, grow = Vec(d, 1.0, d), moveTicks = 6)
                add("ring", 2, o.add(0.0, 1.2, 0.0), 2, 9, Vec(1.5, 1.0, 1.5), yaw, grow = Vec(d, 1.0, d), moveTicks = 6)
                head(ShadeWyrmVfx.CROWN)?.let { add("puff", 4, Pos.fromPoint(it), 0, 8, Vec(2.4, 2.4, 1.0), IDENTITY, billboard = true) }
            }
            "shade-breath" -> if (shape is MobAttackShape.Slam) {
                add("beam", 3, o.add(f.mul(shape.length / 2)).add(0.0, .12, 0.0), 1, 16, Vec(shape.halfWidth * 2.2, 1.0, shape.length), yaw)
                val mouth = head(ShadeWyrmVfx.SNOUT) ?: o.add(f.mul(3.5)).add(0.0, 2.5, 0.0)
                val touch = o.add(f.mul(shape.length * .3)).add(0.0, .4, 0.0)
                val along = touch.sub(mouth)
                val length = along.distance(Vec.ZERO)
                val pitch = atan2(-along.y(), hypot(along.x(), along.z()))
                val mid = Pos(mouth.x() + along.x() / 2, mouth.y() + along.y() / 2, mouth.z() + along.z() / 2)
                for (roll in listOf(0.0, PI / 2)) {
                    add("beam_up", 3, mid, 0, 12, Vec(length, 1.6, 1.0), mul(mul(qy(atan2(-f.z(), f.x())), qz(-pitch)), qx(roll)))
                }
                repeat(6) { n ->
                    val at = o.add(f.mul(shape.length * (n + .7) / 6.5)).add(right.mul(((n % 3) - 1) * .5)).add(0.0, .9, 0.0)
                    add("puff", 4, at, 1 + n, 16, Vec(2.4, 2.4, 1.0), IDENTITY, billboard = true)
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
                meta.setItemStack(stack(piece, age.toDouble() / piece.life))
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
        View(p.sprite, p.kind, age.toDouble() / p.life, p.at, lerp(p.scale, p.grow), p.rotation, lerp(p.translation, p.slide), p.billboard)
    }

    fun dispose() {
        pieces.forEach { it.entity?.remove() }
        pieces.clear()
    }

    private fun add(sprite: String, kind: Int, at: Pos, delay: Int, life: Int, scale: Vec, rotation: FloatArray,
                    translation: Vec = Vec.ZERO, grow: Vec? = null, slide: Vec? = null, moveTicks: Int = 0, billboard: Boolean = false) {
        pieces += Piece(sprite, kind, at, now + 1 + delay, life, scale, rotation, translation, grow, slide, moveTicks, billboard)
    }

    private fun spawn(piece: Piece): Entity = Entity(EntityType.ITEM_DISPLAY).apply {
        setNoGravity(true)
        setHasPhysics(false)
        updateViewableRule { CoreCombatPresentation.packed(it) }
        editEntityMeta(ItemDisplayMeta::class.java) { meta ->
            meta.setDisplayContext(ItemDisplayMeta.DisplayContext.NONE)
            meta.setItemStack(stack(piece, 0.0))
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
