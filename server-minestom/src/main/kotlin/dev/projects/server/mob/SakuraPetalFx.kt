package dev.projects.server.mob

import dev.projects.server.coreloop.CoreCombatPresentation
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.CustomModelData
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 桜牙獣's 花吹雪 (look B "花の奔流"): every petal is an item display drawn by the pack's core/item shader as a white-hot
 * petal trailing a glowing ribbon (scripts/build_boss_vfx.py `sakura_streak`, scripts/shaders/item.fsh marker 78). The
 * server moves the petals; the emitters follow scripts/boss_preview/sakura_fx.py (model px there, blocks here).
 * Only players with the pack see them; everyone gets the vanilla petal particles of QuestEncounterCombat.sakuraBurst.
 */
internal class SakuraPetalFx(private val instance: Instance, seed: Int = 7) {
    private class Petal(var p: Vec, var v: Vec, val life: Double, val drag: Double, val phase: Double, val flutter: Double,
                        val spin: Double) {
        var age = 0.0
        var entity: Entity? = null
    }

    private val random = Random(seed)
    private val petals = mutableListOf<Petal>()
    private val accumulated = HashMap<String, Double>()
    private val fired = HashSet<String>()
    private var tick = 0
    private var ability: String? = null
    private var abilityAt = 0.0
    private var hit = 0.0
    private var enrageAt = -1.0
    private var deathAt = -1.0
    /** Body points (bone, Blockbench px) the petals shed from: along the back and the tail's petal row. */
    private val shells = listOf("body" to Vec(0.0, 52.0, 7.0), "body" to Vec(0.0, 50.0, -2.0), "body" to Vec(0.0, 45.0, -12.0),
        "tail_1" to Vec(0.0, 37.0, -36.0), "tail_2" to Vec(0.0, 45.0, -46.0), "tail_3" to Vec(0.0, 57.0, -52.0),
        "tail_4" to Vec(0.0, 70.0, -50.0), "tail_5" to Vec(0.0, 78.0, -43.0))

    val liveCount: Int get() = petals.size
    private val time: Double get() = tick * DT

    /** An ability began; its strike lands [telegraphSeconds] later. */
    fun start(abilityId: String, telegraphSeconds: Double) {
        ability = abilityId; abilityAt = time; hit = telegraphSeconds
        fired.removeAll { it.startsWith("a:") }
    }

    fun enrage() { enrageAt = time }
    fun death() { deathAt = time; ability = null }

    /**
     * One server tick. [at] is the boss (its yaw is the facing), [point] maps (bone, Blockbench px) to the world.
     * The petals move every tick; their displays are re-sent every [SEND_EVERY] ticks with that much interpolation.
     */
    fun tick(at: Pos, enraged: Boolean, point: (String, Vec) -> Point?) {
        tick++
        val frame = Frame(at)
        emit(frame, enraged, point)
        val iterator = petals.iterator()
        while (iterator.hasNext()) {
            val q = iterator.next()
            q.age += DT
            if (q.age > q.life) { q.entity?.remove(); iterator.remove(); continue }
            val flutter = Vec(sin(q.age * 7 + q.phase), 0.0, cos(q.age * 5 + q.phase)).mul(q.flutter)
            val accel = Vec(0.0, GRAVITY, 0.0).add(frame.wind.sub(q.v).mul(q.drag)).add(flutter)
            q.v = q.v.add(accel.mul(DT))
            q.p = q.p.add(q.v.mul(DT))
            if (q.p.y() < at.y() + .03) { q.p = Vec(q.p.x(), at.y() + .03, q.p.z()); q.v = Vec(q.v.x() * .3, 0.0, q.v.z() * .3) }
            if (!instance.isChunkLoaded(q.p)) { q.entity?.remove(); iterator.remove(); continue }   // blown out of the arena
            if (tick % SEND_EVERY == 0 || q.entity == null) send(q)
        }
    }

    fun dispose() {
        petals.forEach { it.entity?.remove() }
        petals.clear()
    }

    /** Where every live petal is (head, velocity), for tests and the preview dump. */
    internal fun snapshot(): List<Pair<Vec, Vec>> = petals.map { it.p to it.v }

    // --- emitters --------------------------------------------------------------------------------------------------
    private inner class Frame(val at: Pos) {
        private val yaw = Math.toRadians(at.yaw().toDouble())
        val forward = Vec(-sin(yaw), 0.0, cos(yaw))
        val left = Vec(cos(yaw), 0.0, sin(yaw))         // Blockbench +X: the beast's left
        val wind: Vec = left.mul(WIND_X).add(forward.mul(WIND_Z))
        /** Model px (Blockbench, +Z front) -> world, ignoring the animation. */
        fun px(x: Double, y: Double, z: Double): Vec = Vec(at.x(), at.y(), at.z()).add(left.mul(x / 16)).add(0.0, y / 16, 0.0).add(forward.mul(z / 16))
        fun dir(x: Double, y: Double, z: Double): Vec = left.mul(x).add(0.0, y, 0.0).add(forward.mul(z))
    }

    private fun rate(key: String, perSecond: Double): Int {
        val a = (accumulated[key] ?: 0.0) + perSecond * DT * DENSITY
        val n = a.toInt()
        accumulated[key] = a - n
        return n
    }

    private fun once(key: String, at: Double): Boolean = if (key in fired || time < at) false else { fired += key; true }
    private fun r(a: Double, b: Double) = a + (b - a) * random.nextDouble()
    private fun count(n: Int) = maxOf(1, (n * DENSITY).toInt())

    private fun randomDir(): Vec {
        val z = r(-1.0, 1.0); val a = r(0.0, 2 * PI); val s = sqrt(1 - z * z)
        return Vec(s * cos(a), z, s * sin(a))
    }

    private fun add(p: Vec, v: Vec, life: Double, drag: Double = DRAG) {
        if (petals.size >= CAP) return
        petals += Petal(p, v, life, drag, r(0.0, 2 * PI), r(1.2, 2.8), r(4.0, 11.0))
    }

    private fun emit(f: Frame, enraged: Boolean, point: (String, Vec) -> Point?) {
        fun bone(b: String, px: Vec): Vec = point(b, px)?.let { Vec(it.x(), it.y(), it.z()) } ?: f.px(px.x(), px.y(), px.z())
        fun shell(): Vec = shells[random.nextInt(shells.size)].let { (b, px) -> bone(b, px) }
        // always: petals shed from the shells and drift off in the wind (more once enraged)
        if (deathAt < 0) repeat(rate("shed", if (enraged) 16.0 else 6.0)) {
            add(shell(), f.dir(r(-1.0, 1.0), r(0.0, 1.6), r(-1.9, 0.0)), r(1.4, 2.2))
        }
        if (deathAt >= 0) {
            if (time - deathAt in .5..2.4) repeat(rate("fall", 40.0)) { add(shell(), f.dir(r(-1.2, 1.2), r(-.6, .6), r(-1.2, 1.2)), 1.8) }
            return
        }
        if (enrageAt >= 0) {
            val e = time - enrageAt
            if (e in .6..2.4) repeat(rate("shiver", 40.0)) { add(shell(), Vec(r(-1.9, 1.9), r(2.5, 5.6), r(-1.9, 1.9)), 1.2) }
            if (once("enrage-burst", enrageAt + 2.5)) repeat(count(260)) {
                add(shell(), randomDir().mul(r(14.0, 25.0)).add(0.0, 3.8, 0.0), r(1.2, 2.0), 1.4)
            }
            if (e in 2.5..5.0) repeat(rate("storm", 80.0)) {
                val a = r(0.0, 2 * PI); val rr = r(3.8, 10.6)
                val p = Vec(f.at.x() + cos(a) * rr, f.at.y() + r(.3, 5.6), f.at.z() + sin(a) * rr)
                add(p, Vec(-sin(a), 0.0, cos(a)).mul(10.6).add(0.0, r(-1.2, 1.9), 0.0), 1.4, .2)
            }
        }
        val id = ability ?: return
        val t = time - abilityAt
        val strike = abilityAt + hit
        when (id) {
            "sakura-claw" -> {
                val tip = bone("hand_r", Vec(-15.0, 1.0, 31.0))
                if (t in .25..(hit - .05)) repeat(rate("gather", 70.0)) {      // petals spiral into the raised claw
                    val off = randomDir().mul(r(1.75, 2.75))
                    val tangent = off.cross(Vec(0.0, 1.0, 0.0)).normalize()
                    add(tip.add(off), off.mul(-1.9).add(tangent.mul(5.6)), .5, 0.0)
                }
                if (once("a:burst", strike)) {                                  // the slam throws them up along the strip
                    repeat(count(110)) { add(f.px(r(-34.0, 34.0), 2.0, r(16.0, 96.0)), f.dir(r(-3.8, 3.8), r(8.8, 18.8), r(-1.9, 5.6)), r(1.2, 2.0)) }
                    repeat(count(48)) { k ->
                        val a = k * 2 * PI / count(48)
                        add(Vec(tip.x(), f.at.y() + .2, tip.z()), Vec(cos(a), .15, sin(a)).mul(r(17.5, 21.0)), .9, 3.0)
                    }
                }
            }
            "sakura-tail" -> {
                if (t in .3..(hit - .05)) repeat(rate("stream", 40.0)) {        // the coiling tail sheds a stream
                    val (b, px) = shells[3 + random.nextInt(shells.size - 3)]
                    add(bone(b, px), f.dir(r(-1.25, 1.25), r(-.6, 1.9), r(-2.5, .6)), 1.2)
                }
                for (d in 0 until 10) if (once("a:erupt$d", strike + d * .035)) repeat(count(14)) {   // erupting block by block
                    add(f.px(r(-26.0, 26.0), 2.0, 16.0 * d + r(-6.0, 6.0)), f.dir(r(-3.8, 3.8), r(12.5, 20.6), r(-1.25, 2.5)), r(1.0, 1.8))
                }
            }
            "sakura-scatter" -> {
                if (t in .2..(hit - .05)) repeat(rate("orbit", 60.0)) {         // petals peel off and orbit, widening
                    val p = shell()
                    val radial = Vec(p.x() - f.at.x(), 0.0, p.z() - f.at.z())
                    val tangent = Vec(0.0, 1.0, 0.0).cross(radial).let { if (it.length() < 1e-6) f.left else it.normalize() }
                    add(p, tangent.mul(9.4).add(radial.mul(.8)).add(0.0, 1.9, 0.0), 1.0, .4)
                }
                if (once("a:fan", strike)) {                                    // the lunge flings a fan of petals forward
                    val o = bone("chest", Vec(0.0, 42.0, 18.0))
                    repeat(count(170)) {
                        val a = Math.toRadians(r(-40.0, 40.0))
                        add(o.add(randomDir().mul(.4)), f.dir(sin(a), r(-.05, .12), cos(a)).mul(r(30.0, 40.0)), r(1.0, 1.6), 2.3)
                    }
                }
            }
            "sakura-bloom" -> {
                if (t <= hit - .1) {                                            // a vortex rises round the beast
                    val radius = (40 + 70 * (t / hit)) / 16
                    repeat(rate("vortex", 90.0)) {
                        val a = r(0.0, 2 * PI)
                        val p = Vec(f.at.x() + cos(a) * radius, f.at.y() + r(0.0, .6), f.at.z() + sin(a) * radius)
                        val v = Vec(-sin(a), 0.0, cos(a)).mul(11.9).add(0.0, r(3.8, 9.4), 0.0).sub(Vec(cos(a), 0.0, sin(a)).mul(radius * .5))
                        add(p, v, 1.1, .3)
                    }
                }
                if (once("a:ring", strike)) {                                   // the crash bursts it into a ring out to 8 blocks
                    repeat(count(240)) {
                        val a = r(0.0, 2 * PI); val d = Vec(cos(a), 0.0, sin(a))
                        add(Vec(f.at.x(), f.at.y() + .25, f.at.z()).add(d.mul(r(.6, 1.9))), d.mul(r(25.0, 32.5)).add(0.0, r(2.5, 8.1), 0.0), r(1.0, 1.6), 1.9)
                    }
                    repeat(count(60)) { add(Vec(f.at.x() + r(-.6, .6), f.at.y() + .6, f.at.z() + r(-.6, .6)), Vec(r(-2.5, 2.5), r(16.0, 22.5), r(-2.5, 2.5)), 1.6) }
                }
                if (t in (hit + .2)..(hit + 1.0)) repeat(rate("snow", 50.0)) {  // and it snows petals over the ring
                    val a = r(0.0, 2 * PI); val rr = 8.0 * sqrt(random.nextDouble())
                    add(Vec(f.at.x() + cos(a) * rr, f.at.y() + 6.2, f.at.z() + sin(a) * rr), Vec(0.0, -1.9, 0.0), 2.0)
                }
            }
        }
        if (t > hit + 1.5) ability = null
    }

    // --- display ---------------------------------------------------------------------------------------------------
    private fun send(q: Petal) {
        val speed = q.v.length()
        val dir = if (speed > 1e-4) q.v.div(speed) else Vec(0.0, -1.0, 0.0)
        val length = (speed * TRAIL_SECONDS).coerceIn(.3, 3.0)
        val rotation = ShadeWyrmShaderFx.mul(alignY(dir), ShadeWyrmShaderFx.qy(q.phase + q.age * q.spin))
        val fade = minOf(1.0, (q.life - q.age) / .35, q.age / .1 + .3)
        val heat = (1 - q.age / maxOf(.3, q.life * .6)).coerceIn(0.0, 1.0)
        val entity = q.entity
        if (entity == null) {
            q.entity = Entity(EntityType.ITEM_DISPLAY).apply {
                setNoGravity(true)
                setHasPhysics(false)
                updateViewableRule { CoreCombatPresentation.packed(it) }
                editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                    meta.setDisplayContext(ItemDisplayMeta.DisplayContext.NONE)
                    meta.setItemStack(stack(fade, heat))
                    meta.setScale(Vec(WIDTH, length, WIDTH))
                    meta.setLeftRotation(rotation)
                    meta.setTranslation(dir.mul(-length / 2))
                    meta.setBrightness(15, 15)
                    meta.setViewRange(2f)
                    meta.setShadowRadius(0f)
                    meta.setPosRotInterpolationDuration(SEND_EVERY)
                }
                setInstance(this@SakuraPetalFx.instance, Pos(q.p.x(), q.p.y(), q.p.z()))
            }
            return
        }
        entity.teleport(Pos(q.p.x(), q.p.y(), q.p.z()))
        entity.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
            meta.setTransformationInterpolationStartDelta(0)
            meta.setTransformationInterpolationDuration(SEND_EVERY)
            meta.setItemStack(stack(fade, heat))
            meta.setScale(Vec(WIDTH, length, WIDTH))
            meta.setLeftRotation(rotation)
            meta.setTranslation(dir.mul(-length / 2))
        }
    }

    private fun stack(fade: Double, heat: Double): ItemStack =
        ItemStack.of(Material.PAPER).withItemModel("projects:boss_vfx/sakura_streak")
            .with(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(emptyList(), emptyList(), emptyList(),
                listOf(TextColor.color((fade.coerceIn(0.0, 1.0) * 255).toInt(), 100 + (heat * 100).toInt(), MARKER))))

    companion object {
        const val MARKER = 78
        const val DT = .05
        const val SEND_EVERY = 2
        const val CAP = 180
        /** The in-game storm uses fewer, larger petals than the preview: each one carries a long ribbon. */
        const val DENSITY = .5
        const val GRAVITY = -3.75
        const val DRAG = 1.6
        const val WIND_X = .5
        const val WIND_Z = -.875
        const val WIDTH = .32
        /** The ribbon is as long as the distance the petal covers in this time. */
        const val TRAIL_SECONDS = .22

        /** Shortest rotation that turns local +Y onto [d] (x, y, z, w). */
        fun alignY(d: Vec): FloatArray {
            val c = d.y().coerceIn(-1.0, 1.0)
            if (c > .9999) return floatArrayOf(0f, 0f, 0f, 1f)
            if (c < -.9999) return floatArrayOf(1f, 0f, 0f, 0f)
            val axis = Vec(0.0, 1.0, 0.0).cross(d).normalize()
            val half = acos(c) / 2
            val s = sin(half)
            return floatArrayOf((axis.x() * s).toFloat(), (axis.y() * s).toFloat(), (axis.z() * s).toFloat(), cos(half).toFloat())
        }
    }
}
