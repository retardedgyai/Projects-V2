package dev.projects.server.coreloop

import dev.projects.server.mob.SakuraLight
import dev.projects.server.particle.dust
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.ParticlePacket
import java.util.UUID
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The frost nova's picture (Creator 2026-10-28, sample version 12, docs/development/mage-rework.md), from
 * scripts/build_mage_frost.py, 16 px to a block:
 *
 * - [nova]: a white pillar with a cyan rim where the staff comes down (3 ticks); a frost patch spreading over the ground
 *   (4 ticks, holds, shrinks back); a thin cyan ring racing out at knee height and breaking into dashes; three tilted
 *   cyan rings rising round the mage, thinning; and three rings of ice columns in clumps of three, outer rings taller,
 *   ring k starting at tick k and growing a piece a tick. They stand to tick 22 + k and are gone in two ticks in a
 *   burst of ice blocks and snowflakes.
 * - [tick] with the frozen: ice grows out of a frozen enemy's body (seven small columns), a piece a tick, and breaks
 *   into ice blocks when the freeze ends.
 * Players without the pack get vanilla snowflakes.
 */
internal class CoreMageFrost(private val owner: Player, private val light: CoreMageLight) {
    private class Piece(val entity: Entity, var at: Pos, var age: Int = 0, val life: Int, val step: (Piece) -> Boolean)
    private class Frozen(val pieces: List<Piece?>, var age: Int = 0, var seen: Long = 0, var at: Pos)

    private val live = mutableListOf<Piece>()
    private val frozen = HashMap<UUID, Frozen>()
    private val random = Random(0x66726f)
    private var tick = 0L
    private var chargePiece: Piece? = null
    private var burstAt: Pos? = null
    private var burstAge = 0

    /** The cast: a snowflake at [hand] for as long as this is called each tick. */
    fun charge(hand: Pos) {
        light.glow(2)
        val c = chargePiece?.takeIf { it in live }
        if (c == null) chargePiece = sprite("flake", 5 / 16.0, hand, life = 3) { true }
        else { c.age = 0; c.at = hand; c.entity.teleport(hand) }
    }

    /** The strike at [centre] (the mage's feet), the staff coming down ahead along [facing]. */
    fun nova(centre: Pos, facing: Vec) {
        chargePiece?.let(::drop); chargePiece = null
        burstAt = Pos(centre.x(), centre.y() + 1.0, centre.z()); burstAge = 0
        val f = Vec(facing.x(), 0.0, facing.z()).normalize(); val right = Vec(-f.z(), 0.0, f.x())
        val ground = Pos(centre.x(), centre.y(), centre.z())
        // the pillar, a little ahead and to the right where the staff hits
        val hit = ground.add(f.mul(1.6)).add(right.mul(.55))
        for ((model, w0) in listOf("pillar_core" to .5, "pillar_rim" to .8)) shaped(model, hit, life = 3) { p ->
            val w = w0 * doubleArrayOf(.7, .4, .15)[p.age.coerceAtMost(2)]; val h = 3.2 * doubleArrayOf(.45, .55, .6)[p.age.coerceAtMost(2)]
            set(p, scale = Vec(w, h, w), translation = Vec(0.0, h / 2, 0.0)); true }
        // the frost patch: four reach steps, holds, then the steps back
        shaped("patch_0", ground.add(0.0, .03, 0.0), life = 30) { p ->
            val stage = when { p.age < 4 -> p.age; p.age < 26 -> 3; else -> (29 - p.age).coerceAtLeast(0) }
            model(p, "patch_$stage"); set(p, scale = Vec(10.4, 1.0, 10.4)); true }
        // the dash ring at knee height
        shaped("dash_0", ground.add(0.0, .35, 0.0), life = 6) { p ->
            val a = p.age.coerceAtMost(5); model(p, "dash_$a"); val n = ringPx(DASH_R[a]) / 16.0
            p.entity.teleport(ground.add(0.0, .35 + a * .05, 0.0)); set(p, scale = Vec(n, 1.0, n)); true }
        // three tilted rings rising
        for (k in 0..2) shaped("halo_${k}_0", ground, life = 10, delay = 1) { p ->
            val l = (p.age).coerceAtMost(8); model(p, "halo_${k}_$l"); val n = ringPx(HALO_R(k, l)) / 16.0
            p.entity.teleport(ground.add(0.0, .35 + l * .2 + k * .45, 0.0))
            set(p, scale = Vec(n, 1.0, n), rotation = CoreCombatMeshArt.rotation(k * 1.1, atan(TILT[k]), 0.0)); true }
        // the columns
        for (k in 0..2) {
            val clumps = intArrayOf(5, 7, 9)[k]
            for (c in 0 until clumps) {
                val r0 = Random(30 + k * 40 + c)
                val an0 = (c + (k % 2) * .5) / clumps * 2 * PI + (r0.nextDouble() - .5) * .3
                val rr = RINGS[k] + (r0.nextDouble() - .5) * .4
                for (i in 0..2) {
                    val r = Random(300 + k * 90 + c * 7 + i)
                    val an = an0 + (r.nextDouble() - .5) * .25
                    val at = ground.add(cos(an) * rr + (r.nextDouble() - .5) * .3, 0.0, sin(an) * rr + (r.nextDouble() - .5) * .3)
                    val yaw = -an + PI / 2 + (i - 1) * .5
                    val tilt = if (i == 0) .1 else .3 + r.nextDouble() * .35
                    val rot = CoreCombatMeshArt.rotation(yaw, tilt, (r.nextDouble() - .5) * .2)
                    val shape = "$k$i"; val stages = COLUMN_PIECES.getValue(shape)
                    // it starts at tick k (its ring), stands STAND ticks, and is gone in two
                    shaped("col_${shape}_1", at, life = STAND + 1, delay = k) { p ->
                        val life = p.age
                        model(p, "col_${shape}_${(life + 1).coerceAtMost(stages)}")
                        val s = if (life >= STAND) (if (life == STAND) .6 else .25) else 1.0
                        set(p, scale = Vec(s, s, s), translation = pivot(rot, -1.5 * s), rotation = rot)
                        if (life == STAND) repeat(2) { bit(at.add(0.0, .4 + random.nextDouble() * 1.4, 0.0), an) }
                        true
                    }
                }
                if (k == 0 || c % 2 == 0) repeat(2) { flake(ground.add(cos(an0) * rr, 0.0, sin(an0) * rr), late = 22 + k) }
            }
        }
        repeat(14) { flake(ground.add(random.nextDouble(-2.5, 2.5), 0.0, random.nextDouble(-2.5, 2.5)), late = random.nextInt(3)) }
        fallback(ground.add(0.0, .5, 0.0), 40, 2.5)
    }

    /** Ice growing out of each frozen enemy ([frozenAt]: id to where it stands). */
    fun tick(frozenAt: Map<UUID, Pos>) {
        tick++
        // the nova's cold light, one steady strength while the ice stands
        burstAt?.let { at -> burstAge++
            if (burstAge > STAND + 4) burstAt = null
            else light.show(CoreMageLight.BURST, at, CoreMageLight.FROST_REACH, CoreMageLight.FROST, SakuraLight.FROST, ticks = 4) }
        if (frozenAt.isNotEmpty()) light.glow(2)
        for ((id, at) in frozenAt) {
            val fz = frozen.getOrPut(id) { Frozen(SPROUTS.map { (sx, y, sz, rx, rz, name) ->
                val rot = CoreCombatMeshArt.rotation(0.0, rx, rz)
                val stages = SPROUT_PIECES.getValue(name); val late = if (y > .5) 1 else 0
                shaped("sprout_${name}_1", at.add(sx, y, sz), life = Int.MAX_VALUE, delay = late) { p ->
                    model(p, "sprout_${name}_${(p.age + 1).coerceAtMost(stages)}")
                    set(p, translation = pivot(rot, -.5), rotation = rot); true }
            }, at = at) }
            // the ice is on the enemy: a chilled boss walks on, and its ice goes with it (left behind, it stood in the air)
            if (fz.at.distanceSquared(at) > 1e-4) {
                fz.pieces.forEachIndexed { i, piece -> piece?.let { val (sx, y, sz) = SPROUTS[i]; it.at = at.add(sx, y, sz); it.entity.teleport(it.at) } }
                fz.at = at
            }
            fz.seen = tick; fz.age++
            if (fz.age % 6 == 0) fallback(at.add(0.0, 1.0, 0.0), 2, .3)
        }
        frozen.entries.removeAll { (_, fz) ->
            (fz.seen != tick).also { gone -> if (gone) { fz.pieces.forEach { it?.let(::drop) }; repeat(10) { bit(fz.at.add(0.0, .3 + random.nextDouble() * 1.6, 0.0), random.nextDouble() * 2 * PI) } } }
        }
        live.removeAll { p ->
            p.age++
            if (p.age > p.life || p.entity.isRemoved) { p.entity.remove(); return@removeAll true }
            if (!p.step(p)) { p.entity.remove(); return@removeAll true }
            false
        }
    }

    fun clear() { live.forEach { it.entity.remove() }; live.clear(); frozen.clear(); chargePiece = null; burstAt = null }

    // ---- the small things

    /** A small ice block thrown out, tumbling, falling, gone in 12 ticks. */
    private fun bit(from: Pos, an0: Double) {
        val an = an0 + (random.nextDouble() - .5) * 1.4; val sp = .07 + random.nextDouble() * .06; val s0 = .14 + random.nextDouble() * .14
        shaped("bit", from, life = 12) { p ->
            val l = p.age.toDouble()
            p.entity.teleport(from.add(cos(an) * sp * l, l * .12 - l * l * .025, sin(an) * sp * l))
            val s = s0 * (if (l < 8) 1.0 else 1 - (l - 8) / 4)
            set(p, scale = Vec(s, s, s), translation = Vec(0.0, s / 2, 0.0), rotation = CoreCombatMeshArt.rotation(l * .4, l * .5, 0.0)); true }
    }

    /** A snowflake: a few on the strike, more when the ice breaks; they drift down, turning (no twinkle: a flake growing every
     *  fourth tick read as the ice blinking, the Creator 2026-10-31). */
    private fun flake(base: Pos, late: Int) {
        val life = if (late > 3) 24 + random.nextInt(16) else 16 + random.nextInt(8)
        val y0 = if (late > 3) .5 + random.nextDouble() * 3 else .6 + random.nextDouble() * 2
        val sway = random.nextDouble() * 6
        sprite("flake", 5 / 16.0, base.add(0.0, y0, 0.0), life = life, delay = late) { p ->
            val l = p.age.toDouble()
            val y = y0 + if (late > 3) -l * .035 else l * .05
            if (y < .05) return@sprite false
            p.entity.teleport(base.add(sin(l * .15 + sway) * .2, y, 0.0))
            true }
    }

    // ---- display entities

    private fun pivot(rot: FloatArray, baseY: Double): Vec {
        // where the model's base (0, baseY, 0) goes once turned: move it back to the entity, so it turns about its base
        val (x, y, z, w) = rot.map { it.toDouble() }
        val vx = 0.0; val vy = baseY; val vz = 0.0
        val tx = 2 * (y * vz - z * vy); val ty = 2 * (z * vx - x * vz); val tz = 2 * (x * vy - y * vx)
        val rx = vx + w * tx + (y * tz - z * ty); val ry = vy + w * ty + (z * tx - x * tz); val rz = vz + w * tz + (x * ty - y * tx)
        return Vec(-rx, -ry, -rz)
    }

    private fun set(p: Piece, scale: Vec? = null, translation: Vec? = null, rotation: FloatArray? = null) {
        p.entity.editEntityMeta(ItemDisplayMeta::class.java) { m ->
            m.setTransformationInterpolationStartDelta(0); m.setTransformationInterpolationDuration(1)
            scale?.let(m::setScale); translation?.let(m::setTranslation); rotation?.let(m::setLeftRotation)
        }
    }

    private fun model(p: Piece, name: String) = p.entity.editEntityMeta(ItemDisplayMeta::class.java) { it.setItemStack(stack(name)) }

    private fun sprite(model: String, scale: Double, at: Pos, life: Int, delay: Int = 0, step: (Piece) -> Boolean) =
        spawn(model, at, life, delay, billboard = true, scale = scale, step = step)

    private fun shaped(model: String, at: Pos, life: Int, delay: Int = 0, step: (Piece) -> Boolean) =
        spawn(model, at, life, delay, billboard = false, scale = 1.0, step = step)

    private fun viewers(instance: Instance, at: Point) =
        instance.players.filter { CoreCombatPresentation.packed(it) && it.position.distanceSquared(at) < VIEW * VIEW }

    private fun spawn(model: String, at: Pos, life: Int, delay: Int, billboard: Boolean, scale: Double, step: (Piece) -> Boolean): Piece? {
        val instance = owner.instance ?: return null
        if (live.size >= LIMIT || viewers(instance, at).isEmpty()) return null
        val entity = Entity(EntityType.ITEM_DISPLAY)
        entity.setHasPhysics(false); entity.setNoGravity(true); entity.setAutoViewable(false)
        entity.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
            meta.setItemStack(if (delay > 0) ItemStack.AIR else stack(model))
            meta.setDisplayContext(ItemDisplayMeta.DisplayContext.FIXED)
            if (billboard) {
                meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
                meta.setRightRotation(CoreCombatMeshArt.vanillaItemCorrection)
            }
            meta.setPosRotInterpolationDuration(1)
            meta.setBrightness(15, 15); meta.setViewRange(1.0f)
            meta.setScale(Vec(scale, scale, scale))
        }
        // a delayed piece waits empty, then runs its own clock from zero
        var waited = 0
        val piece = Piece(entity, at, life = life, step = { p ->
            if (waited < delay) { waited++; p.age = 0; if (waited == delay) model(p, model); true } else step(p) })
        piece.age = 0
        live += piece
        entity.setInstance(instance, at).whenComplete { _, failure ->
            if (failure != null || owner.instance !== instance || piece !in live) entity.remove()
            else viewers(instance, at).forEach(entity::addViewer)
        }
        // every piece also removes itself: if this owner stops ticking (the actor is replaced), nothing is left standing
        entity.scheduleRemove(java.time.Duration.ofMillis(50L * (if (life == Int.MAX_VALUE) SAFETY else life + delay + 4)))
        if (delay == 0) step(piece)
        return piece
    }

    private fun drop(p: Piece) { p.entity.remove(); live.remove(p) }

    private fun fallback(at: Pos, count: Int, spread: Double) {
        val instance = owner.instance ?: return
        val packet = ParticlePacket(dust(0xc8f2ff, 1.0f), at, Vec(spread, .4, spread), 0f, count)
        instance.players.filter { !CoreCombatPresentation.packed(it) && it.position.distanceSquared(at) < VIEW * VIEW }.forEach { it.sendPacket(packet) }
    }

    private fun stack(model: String) = ItemStack.of(Material.PAPER).withItemModel("projects:combat_vfx/mage_frost/$model")

    private data class Sprout(val x: Double, val y: Double, val z: Double, val rx: Double, val rz: Double, val name: String)

    companion object {
        val RINGS = doubleArrayOf(2.3, 3.4, 4.5)
        val TILT = doubleArrayOf(-.18, .22, -.08)
        val DASH_R = DoubleArray(6) { 1 + 4.6 * (1 - .55.pow(it + 1)) }
        fun HALO_R(k: Int, l: Int) = 1.5 + l * .5 + k * .6
        /** The ring textures' width in px, as scripts/build_mage_frost.py ring(). */
        fun ringPx(radius: Double) = kotlin.math.ceil(radius * 32).toInt() + 4
        /** How many pieces each column has (segments + a three-step point), as build_mage_frost.py COLUMNS. */
        val COLUMN_PIECES = (0..2).flatMap { k -> (0..2).map { i -> "$k$i" to intArrayOf(2, 3, 4)[k] + (if (i == 0) 1 else 0) + 3 } }.toMap()
        val SPROUT_PIECES = mapOf("a" to 6, "b" to 5, "c" to 6, "d" to 5)
        private val SPROUTS = listOf(Sprout(0.0, .05, 0.0, 0.0, 0.0, "a"), Sprout(.2, .1, .15, .5, -.6, "b"), Sprout(-.22, .1, -.1, -.5, .6, "b"),
            Sprout(.28, 1.35, 0.0, .2, -1.1, "c"), Sprout(-.28, 1.4, .05, -.3, 1.05, "c"), Sprout(0.0, 1.25, -.2, -.9, 0.0, "c"), Sprout(.1, .85, .25, .9, -.3, "d"))
        /** Ticks a column stands before it breaks. */
        const val STAND = 22
        const val LIMIT = 260
        /** Ticks a piece without its own end (the ice on a frozen enemy) may live at most: a little past the longest
         *  freeze (a boss's chill, CoreMageField.CHILL_TICKS). */
        const val SAFETY = 80
        const val VIEW = 48.0
    }
}
