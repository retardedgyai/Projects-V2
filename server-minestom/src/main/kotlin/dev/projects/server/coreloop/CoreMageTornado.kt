package dev.projects.server.coreloop

import net.kyori.adventure.text.format.TextColor
import net.minestom.server.component.DataComponents
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
import net.minestom.server.item.component.CustomModelData
import net.minestom.server.timer.TaskSchedule
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * メイジの竜巻 (version 7 of the preview, refs/mage-vfx/preview/src_tornado.js; 藍「いいね　マシになった求台点だね」
 * 2026-11-03), first brought into the game to see it there (藍「みてみようか」). The look only, played by /tornadotest:
 * it does not pull or hit anything yet.
 *
 * The funnel is one upright billboard (the item shader's marker 84, scripts/shaders/item.fsh mageFunnel): the shader
 * draws the 16 px funnel and turns it with GameTime; the server only tells it how much of the funnel stands (tint red).
 * Around it, as the preview: a pale ring of dust closing in and lines running inward on the ground (the mark), leaves
 * sucked in and up, grass and stone blocks carried round, dust at the foot, and at the end the funnel comes undone from
 * the top while its pixels fly off like wind (short streaks, white and pale green, flung on round the spin, not falling).
 * Timing in ticks as the preview: start-up 8, the funnel 60, coming undone 14.
 *
 * Version 2 (2026-11-03): 藍 saw version 1 in the game「全部違うなこれ」「そもそもが違うから直そう」. Its loose bits were
 * vanilla dust particles (soft, star-shaped, sizes all over the place: confetti, not pixels), and the funnel's pixels
 * were coarse. Now every loose bit is a small sprite whose texel is 1/16 block (px_* in build_boss_vfx.py), the mark on
 * the ground is a flat shader plate (marker 84 kind 2), and the funnel's coordinate map is four times finer.
 */
internal object CoreMageTornado {
    private const val CH = 8
    private const val END = 60
    private const val UNR = 14
    private const val TOP = 4.4
    private const val QUAD = 4.8                // the billboard: 4.8 x 4.8 blocks, its foot on the ground
    private const val MARKER_B = 84
    private val LEAF = intArrayOf(0x3c8c28, 0x50a834, 0x2e6e1e)

    private fun r(y: Double) = .28 + (max(0.0, y) / TOP).pow(1.35) * 1.55
    private fun wobX(y: Double, tt: Double) = (sin(y * 1.1 + tt * .22) * .3 + sin(y * 2.3 - tt * .13) * .1) * (y / TOP)
    private fun wobZ(y: Double, tt: Double) = sin(y * 1.7 + tt * .17) * .12 * (y / TOP)
    /** The preview's seeded random: the same picture every time it is played. */
    private fun rnd(seed: Int) = Random(seed)

    fun play(owner: Player, at: Pos) {
        val instance = owner.instance ?: return
        val foot = Pos(at.x(), at.y(), at.z())
        var funnel: Entity? = null
        var plate: Entity? = null
        val chunks = ArrayList<Entity>()
        val bits = HashMap<Int, Entity>()
        var t = 0
        lateinit var task: net.minestom.server.timer.Task
        task = owner.scheduler().buildTask {
            if (owner.instance !== instance || t > CH + END + UNR + 20) {
                funnel?.remove(); plate?.remove(); chunks.forEach(Entity::remove); bits.values.forEach(Entity::remove); bits.clear(); chunks.clear()
                task.cancel(); return@buildTask
            }
            tick(instance, foot, t, { funnel }, { funnel = it }, { plate }, { plate = it }, chunks, bits)
            t++
        }.repeat(TaskSchedule.tick(1)).schedule()
    }

    private fun tick(instance: Instance, g: Pos, t: Int, funnel: () -> Entity?, setFunnel: (Entity?) -> Unit,
                     ground: () -> Entity?, setGround: (Entity?) -> Unit, chunks: MutableList<Entity>, bits: MutableMap<Int, Entity>) {
        val a = t - CH
        val tt = t.toDouble()
        val want = ArrayList<Bit>()
        fun bit(id: Int, x: Double, y: Double, z: Double, sprite: String, color: Int = 0xffffff) { want += Bit(id, Pos(g.x() + x, g.y() + y, g.z() + z), sprite, color) }

        // the mark and the pull: a flat shader plate on the ground (a ring of pixels closing in, lines running inward)
        if (a < END + 2) { val p = ground() ?: spawnPlate(instance, g)?.also(setGround); p?.editEntityMeta(ItemDisplayMeta::class.java) { it.setItemStack(plateStack(t)) } }
        else { ground()?.remove(); setGround(null) }
        // the funnel: grows from the ground at the white tick, stands, comes undone from the top
        val stand = when {
            a < 0 -> 0.0
            a < END -> min(1.0, (.6 + a * 1.4) / TOP)
            else -> max(0.0, 1.0 - (a - END).toDouble() / UNR)
        }
        if (a >= 0 && stand > 0.0) {
            val f = funnel() ?: spawnFunnel(instance, g)?.also(setFunnel)
            f?.editEntityMeta(ItemDisplayMeta::class.java) { it.setItemStack(funnelStack(stand)) }
        } else if (a >= END + UNR) { funnel()?.remove(); setFunnel(null) }
        // the white tick: white pixels thrown up and out
        if (a in 0..1) for (j in 0 until 24) {
            val R = rnd(140 + j); val an = R.nextDouble() * 6.28; val d = .3 + a * .5 + R.nextDouble() * .4
            bit(1000 + j, cos(an) * d, .1 + R.nextDouble() * 1.4 + a * .6, sin(an) * d, "px_dot1")
        }
        // leaves and grass bits: torn up round the area, sucked in along a spiral, carried up round the funnel
        for (j in 0 until 60) {
            val R = rnd(400 + j); val born = (R.nextDouble() * (END - 6)).toInt(); val l = a - born
            if (l < 0 || l >= 26) continue
            val an = R.nextDouble() * 6.28 + l * .35; val r0 = 1.6 + R.nextDouble() * 2
            val lc = min(l, END - born); val u = min(1.0, lc / 9.0)
            var y = .05 + (if (lc < 9) lc * .03 else .27 + (lc - 9) * .22); var rr = r0 * (1 - u) + r(y) * 1.05 * u
            if (y > TOP) continue
            if (a >= END) { val f = a - END; if (f >= 12) continue; rr += f * .18; y -= f * f * .012; if (y < .02) continue }
            bit(2000 + j, wobX(y, tt) + cos(an) * rr, y, wobZ(y, tt) + sin(an) * rr, if (j % 5 == 0) "px_dot2" else "px_dot1", LEAF[j % 3])
        }
        // grass and stone blocks torn up and carried round and up; when it stops they drop, bounce once and sink
        while (chunks.size < 9 && a >= 0) chunks += spawnChunk(instance, g, chunks.size) ?: break
        chunks.forEachIndexed { i, m ->
            val R = rnd(500 + i); val st0 = 2 + i * 5; val l = a - st0
            if (l < 0) { m.teleport(g.add(0.0, -3.0, 0.0)); return@forEachIndexed }
            val an0 = R.nextDouble() * 6.28; val r0 = 1.4 + R.nextDouble() * 1.6; val hy = 1.2 + R.nextDouble() * 2.6
            val p: Vec? = if (a < END) {
                val u = min(1.0, l / 10.0); val y = min(hy, .1 + l * .12); val an = an0 + l * .3; val rr = r0 * (1 - u) + r(y) * 1.2 * u
                Vec(wobX(y, tt) + cos(an) * rr, y, wobZ(y, tt) + sin(an) * rr)
            } else {
                val f = a - END
                if (f >= 16) null else {
                    val y0 = min(hy, .1 + (END - st0) * .12); val an = an0 + (END - st0) * .3 + f * .08; val rr = r(y0) * 1.2 + f * .12
                    var y = y0 + .1 * f - .03 * f * f
                    if (y < .12) { val gg = f - (.1 + sqrt(.01 + .12 * y0)) / .06; y = if (gg < 4) max(.12, .12 + .06 * gg - .03 * gg * gg) else .12 - (gg - 4) * .07 }
                    if (y < -.2) null else Vec(cos(an) * rr, y, sin(an) * rr)
                }
            }
            if (p == null) m.teleport(g.add(0.0, -3.0, 0.0))
            else m.teleport(Pos(g.x() + p.x(), g.y() + p.y(), g.z() + p.z(), (a * 23f + i * 40) % 360f, (a * 17f) % 360f))
        }
        // blocky dust turning at the foot
        if (a >= 0 && a < END + 14) for (k in 0 until 6) {
            val an = k / 6.0 * 6.28 + a * .5; val e = max(0, a - END); val rr = .55 + (k % 2) * .35 + e * .06
            if (rnd(800 + k * 7 + a).nextDouble() > (if (e > 0) 1 - e / 14.0 else 1.0)) continue
            bit(3000 + k, cos(an) * rr, .18 + (k % 3) * .12 + e * .05, sin(an) * rr, "px_puff", if (k % 2 == 1) 0xd8d0c0 else 0xa8a094)
        }
        // the scatter: where the funnel has come undone its pixels fly off like wind, on round the spin and out, a little
        // up, never falling; each a streak of 2-4 pixels along its way, shortening and thinning out
        if (a >= END) for (j in 0 until 140) {
            val R = rnd(3000 + j); val yb = R.nextDouble() * TOP; val tb = END + (1 - yb / TOP) * UNR; val age = a - tb
            if (age < 0 || age >= 14) continue
            val r0 = r(yb); val an0 = R.nextDouble() * 6.28 + tb * .9; val sp = .8 + R.nextDouble() * .6; val r2 = R.nextDouble()
            val q = tb + CH
            val an = an0 + sp * .35 * (1 - exp(-age * .25)) / .25; val out = r0 + age * (.1 + r2 * .08) + age * age * .006
            if (age >= 8 && rnd(3500 + j + age.toInt()).nextDouble() < (age - 7) / 6) continue
            val n = max(2, round((4 - age * .22) * (.7 + R.nextDouble() * .5)).toInt()).coerceAtMost(4)
            val dark = j % 7 == 6
            bit(4000 + j, wobX(yb, q) + cos(an) * out, yb + age * (.025 + r2 * .03), wobZ(yb, q) + sin(an) * out,
                if (dark) (if (n >= 3) "px_streak3d" else "px_streak2d") else "px_streak$n")
        }
        // the loose pixels: one small sprite each, made, moved, or taken away to match this tick
        val seen = HashSet<Int>()
        for (b in want) {
            seen += b.id
            val e = bits[b.id] ?: spawnBit(instance, b)?.also { bits[b.id] = it } ?: continue
            e.teleport(b.at)
        }
        bits.keys.filter { it !in seen }.forEach { bits.remove(it)?.remove() }
    }

    private class Bit(val id: Int, val at: Pos, val sprite: String, val color: Int)

    private fun spriteStack(sprite: String, color: Int): ItemStack =
        ItemStack.of(Material.PAPER).withItemModel("projects:boss_vfx/$sprite")
            .with(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(emptyList(), emptyList(), emptyList(), listOf(TextColor.color(color))))

    private fun spawnBit(instance: Instance, b: Bit): Entity? {
        val viewers = instance.players.filter { CoreCombatPresentation.packed(it) && it.position.distanceSquared(b.at) < 48.0 * 48.0 }
        if (viewers.isEmpty()) return null
        val e = Entity(EntityType.ITEM_DISPLAY)
        e.setHasPhysics(false); e.setNoGravity(true); e.setAutoViewable(false)
        e.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
            meta.setItemStack(spriteStack(b.sprite, b.color))
            meta.setDisplayContext(ItemDisplayMeta.DisplayContext.FIXED)
            meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
            meta.setPosRotInterpolationDuration(1)
            meta.setBrightness(15, 15); meta.setViewRange(1.0f)
        }
        e.setInstance(instance, b.at).thenRun { viewers.forEach(e::addViewer) }
        return e
    }

    private fun plateStack(t: Int): ItemStack =
        ItemStack.of(Material.PAPER).withItemModel("projects:boss_vfx/mage_ground")
            .with(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(emptyList(), emptyList(), emptyList(),
                listOf(TextColor.color(t.coerceIn(0, 255), 2, MARKER_B))))

    private fun spawnPlate(instance: Instance, g: Pos): Entity? {
        val viewers = instance.players.filter { CoreCombatPresentation.packed(it) && it.position.distanceSquared(g) < 48.0 * 48.0 }
        if (viewers.isEmpty()) return null
        val e = Entity(EntityType.ITEM_DISPLAY)
        e.setHasPhysics(false); e.setNoGravity(true); e.setAutoViewable(false)
        e.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
            meta.setItemStack(plateStack(0))
            meta.setDisplayContext(ItemDisplayMeta.DisplayContext.FIXED)
            meta.setBrightness(15, 15); meta.setViewRange(1.0f)
            meta.setScale(Vec(8.0, 8.0, 8.0))
        }
        e.setInstance(instance, g.add(0.0, .03, 0.0)).thenRun { viewers.forEach(e::addViewer) }
        return e
    }

    private fun funnelStack(stand: Double): ItemStack =
        ItemStack.of(Material.PAPER).withItemModel("projects:boss_vfx/mage_funnel")
            .with(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(emptyList(), emptyList(), emptyList(),
                listOf(TextColor.color((stand.coerceIn(0.0, 1.0) * 255).toInt(), 1, MARKER_B))))

    private fun spawnFunnel(instance: Instance, g: Pos): Entity? {
        val viewers = instance.players.filter { CoreCombatPresentation.packed(it) && it.position.distanceSquared(g) < 48.0 * 48.0 }
        if (viewers.isEmpty()) return null
        val e = Entity(EntityType.ITEM_DISPLAY)
        e.setHasPhysics(false); e.setNoGravity(true); e.setAutoViewable(false)
        e.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
            meta.setItemStack(funnelStack(0.0))
            meta.setDisplayContext(ItemDisplayMeta.DisplayContext.FIXED)
            meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.VERTICAL)
            meta.setBrightness(15, 15); meta.setViewRange(1.0f)
            meta.setScale(Vec(QUAD, QUAD, QUAD))
        }
        e.setInstance(instance, g.add(0.0, QUAD / 2, 0.0)).thenRun { viewers.forEach(e::addViewer) }
        return e
    }

    private fun spawnChunk(instance: Instance, g: Pos, i: Int): Entity? {
        val e = Entity(EntityType.ITEM_DISPLAY)
        e.setHasPhysics(false); e.setNoGravity(true)
        e.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
            meta.setItemStack(ItemStack.of(if (i % 3 == 0) Material.STONE else Material.GRASS_BLOCK))
            meta.setDisplayContext(ItemDisplayMeta.DisplayContext.FIXED)
            meta.setPosRotInterpolationDuration(1)
            meta.setScale(Vec(.25, .25, .25))
        }
        e.setInstance(instance, g.add(0.0, -3.0, 0.0))
        return e
    }
}
