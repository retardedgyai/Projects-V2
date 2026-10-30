package dev.projects.server.coreloop

import dev.projects.server.mob.SakuraLight
import dev.projects.server.particle.dust
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.ParticlePacket
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The fire bolt's picture (Creator 2026-10-28, sample version 21, docs/development/mage-rework.md): pixel-art sprites
 * from scripts/build_mage_fire.py, each shown at 16 px to a block and facing the camera.
 *
 * - [charge]: a small flame at the staff hand while the bolt is being cast.
 * - [launch]: the ball flies to where the server's ray ended, dropping small flames that burn out in 3 ticks; where it
 *   arrives, the small then the big burst drawing and a spray of embers. The hits wait for it ([arrived]).
 * - [burning]: an enemy that burns wears four standing flames for as long as it burns, flickering, sinking one by one
 *   in the last half second, with one-pixel embers popping out and a thin smoke.
 * Players without the pack get vanilla dust instead.
 */
internal class CoreMageFire(private val owner: Player, private val light: CoreMageLight) {
    /** A sprite: where it is and how it moves, which drawings it runs through, when it goes. */
    private class Sprite(val entity: Entity, var at: Pos, val life: Int, val frame: (Int) -> String, val move: (Sprite) -> Pos?, var age: Int = 0)
    private class Flight(val from: Pos, val to: Pos, val ticks: Int, var sprite: Sprite?, var age: Int = 0)
    private class Burn(val flames: MutableList<Sprite?>, var age: Int = 0, var seen: Long = 0)

    private val live = mutableListOf<Sprite>()
    private val flights = mutableListOf<Flight>()
    private val burns = HashMap<UUID, Burn>()
    private var chargeSprite: Sprite? = null
    private val random = Random(0x6d61)
    private var tick = 0L
    private val beat = CoreMageDisplay.Heartbeat()

    /** The cast: a flame at [hand] for as long as this is called each tick. */
    fun charge(hand: Pos) {
        val s = chargeSprite?.takeIf { it in live && !it.entity.isRemoved }
        // it lives three ticks unless the next tick's call renews it, so a cancelled cast leaves nothing behind
        if (s == null) chargeSprite = spawn("lick_0", LICK * CHARGE, hand, life = 3, renewed = true, frame = { "lick_${it % 3}" }) { it.at }
        else { s.age = 0; s.at = hand; s.entity.teleport(hand) }
    }

    fun endCharge() { chargeSprite?.let { it.entity.remove(); live.remove(it) }; chargeSprite = null }

    /** The ball leaves [from] and reaches [to] in [ticks]. */
    fun launch(from: Pos, to: Pos, ticks: Int) {
        endCharge()
        // the muzzle: one tick of the small burst just ahead of the hand
        spawn("burst_0", .7, from.add(to.sub(from).asVec().normalize().mul(.4)), life = 1, frame = { "burst_0" }) { it.at }
        val flight = Flight(from, to, ticks.coerceAtLeast(1), null)
        flight.sprite = spawn("ball_0", BALL, from, life = flight.ticks, frame = { "ball_${it % 3}" }) {
            from.add(to.sub(from).mul((flight.age.toDouble() / flight.ticks).coerceAtMost(1.0)))
        }
        flights += flight
        fallback(from, 4)
    }

    /** The flight has arrived: the burst and the embers. */
    private fun arrive(f: Flight) {
        val at = f.to
        spawn("burst_0", BURST_SMALL, at, life = 1, frame = { "burst_0" }) { it.at }
        spawn("burst_1", BURST_BIG, at, life = 2, delay = 1, frame = { "burst_1" }) { it.at }
        repeat(14) { embers(at, fast = true) }
        fallback(at, 16)
    }

    /** One tick of [burningAt] (each burning enemy and where it stands) and of every sprite. */
    fun tick(burningAt: Map<UUID, Pos>, remaining: (UUID) -> Long) {
        tick++; beat.touch()
        for (f in flights.toList()) {
            f.age++
            // a small flame dropped where the ball was, burning out in 3 ticks: a dotted trail, never a beam
            if (f.age in 2 until f.ticks) f.sprite?.at?.let { prev -> val k = f.age
                spawn("lick_0", LICK, prev.add(0.0, -.15, 0.0), life = 3, frame = { "lick_${(k + it) % 3}" }) { it.at } }
            f.sprite?.at?.let { light.show(CoreMageLight.MOVING, it, CoreMageLight.FIRE_REACH, CoreMageLight.FIRE, SakuraLight.EMBER, ticks = 4) }
            if (f.age >= f.ticks) { flights.remove(f); f.sprite?.let(::drop); arrive(f) }
        }
        for ((id, at) in burningAt) burnTick(id, at, remaining(id))
        // the burning enemy lights round itself (the moving slot, once no ball is flying)
        if (flights.isEmpty()) burningAt.entries.firstOrNull()?.let { (_, at) -> light.show(CoreMageLight.MOVING, at.add(0.0, 1.2, 0.0), CoreMageLight.FIRE_REACH, CoreMageLight.FIRE, SakuraLight.EMBER, ticks = 4) }
        burns.entries.removeAll { (_, b) -> (b.seen != tick).also { gone -> if (gone) b.flames.forEach { it?.let(::drop) } } }
        live.removeAll { s ->
            s.age++
            if (s.age > s.life || s.entity.isRemoved) { s.entity.remove(); return@removeAll true }
            s.move(s)?.let { p -> if (p != s.at) { s.at = p; s.entity.teleport(p) } } ?: run { s.entity.remove(); return@removeAll true }
            s.entity.editEntityMeta(ItemDisplayMeta::class.java) { it.setItemStack(stack(s.frame(s.age))) }
            false
        }
    }

    /** Four standing flames round [at], each its own size and drawing, re-sized every 3 ticks; they sink one by one in
     *  the last 10 ticks. Embers pop out of the body, a thin smoke rises off the head. */
    private fun burnTick(id: UUID, at: Pos, left: Long) {
        val b = burns.getOrPut(id) { Burn(MutableList(4) { null }) }
        b.seen = tick; b.age++
        for (i in 0 until 4) {
            val (an, r, sc) = SPOTS[i]
            val sinkAt = 8 + i * 3
            if (left < sinkAt - 2) { b.flames[i]?.let(::drop); b.flames[i] = null; continue }
            if (b.flames[i]?.let { it !in live || it.entity.isRemoved } == true) b.flames[i] = null
            if (b.flames[i] == null) b.flames[i] = spawn("tall_$i", TALL * sc, at, life = Int.MAX_VALUE, frame = { "tall_${(it + i) % 4}" }) { it.at }
            val f = b.flames[i] ?: continue
            val wobble = Random(id.hashCode() * 31 + i * 7 + (b.age / 3)).nextDouble(.92, 1.08)
            val sink = if (left < sinkAt) (sinkAt - left) * .55 * sc else 0.0
            val p = Pos(at.x() + cos(an) * r * 1.3, at.y() + TALL * sc / 2 - sink, at.z() + sin(an) * r * 1.04)
            f.at = p; f.entity.teleport(p)
            f.entity.editEntityMeta(ItemDisplayMeta::class.java) { it.setScale(Vec(TALL * sc * wobble)) }
        }
        if (left > 6) {
            repeat(if (random.nextDouble() < .18) 6 else 2) { embers(at.add(random.nextDouble(-.3, .3), random.nextDouble(.15, 1.9), random.nextDouble(-.3, .3)), fast = false) }
            if (b.age % 3 == 0) fallbackSmoke(at.add(0.0, 2.15, 0.0))
        }
        if (b.age % 4 == 0) fallback(at.add(0.0, 1.0, 0.0), 3)
    }

    /** One pixel flung out from [from]: its own speed, arc and life, cooling white, orange, red, dark red. */
    private fun embers(from: Pos, fast: Boolean) {
        val an = random.nextDouble(0.0, 2 * PI)
        val out = (if (fast) .2 else .04) + random.nextDouble() * (if (fast) .18 else .09)
        val up = (if (fast) .05 else .04) + random.nextDouble() * .12
        val g = .008 + random.nextDouble() * .006
        val life = 5 + random.nextInt(if (fast) 10 else 9)
        val drag = if (fast) .82 else 1.0
        spawn("ember_0", EMBER, from, life = life, frame = { a -> "ember_${(a * 5 / (life + 1)).coerceAtMost(4)}" }) { s ->
            val k = s.age.toDouble()
            val reach = if (drag < 1) out * (1 - Math.pow(drag, k)) / (1 - drag) else out * k
            val p = from.add(cos(an) * reach, up * k - g * k * k, sin(an) * reach)
            if (p.y() < from.y() - 2.2) null else p
        }
    }

    fun clear() { live.forEach { it.entity.remove() }; live.clear(); flights.clear(); burns.clear(); chargeSprite = null }

    private fun drop(s: Sprite) { s.entity.remove(); live.remove(s) }

    /** [renewed]: its age is put back each tick it is wanted (the charge flame), so it has no end of its own. */
    private fun spawn(model: String, scale: Double, at: Pos, life: Int, delay: Int = 0, renewed: Boolean = false, frame: (Int) -> String, move: (Sprite) -> Pos?): Sprite? {
        if (live.size >= LIMIT) return null
        var sprite: Sprite? = null
        val entity = CoreMageDisplay.spawn(owner, at, stack(model), scale, billboard = true,
            life = if (life == Int.MAX_VALUE || renewed) null else life + delay, alive = { beat.fresh() && sprite in live }, wanted = { sprite in live }) ?: return null
        return Sprite(entity, at, if (life == Int.MAX_VALUE) life else life + delay, { a -> if (a < delay) "none" else frame(a - delay) }, move, age = 0)
            .also { sprite = it; live += it }
    }

    private fun fallback(at: Pos, count: Int) =
        CoreMageDisplay.fallback(owner, at, ParticlePacket(dust(0xff7a2c, 1.1f), at, Vec(.3, .3, .3), 0f, count))

    /** The smoke stays vanilla dust for everyone: dark, small, rising. */
    private fun fallbackSmoke(at: Pos) {
        val instance = owner.instance ?: return
        val packet = ParticlePacket(dust(0x1e1616, .6f), at, Vec(.12, .05, .12), 0f, 1)
        instance.players.filter { it.position.distanceSquared(at) < CoreMageDisplay.VIEW * CoreMageDisplay.VIEW }.forEach { it.sendPacket(packet) }
    }

    private fun stack(model: String) =
        if (model == "none") ItemStack.AIR else ItemStack.of(Material.PAPER).withItemModel("projects:combat_vfx/mage_fire/$model")

    companion object {
        // 16 px to a block: a sprite's scale is its canvas size over 16
        const val BALL = 18 / 16.0
        /** The charge flame sits at the staff tip, close to the eye: drawn smaller there so it does not fill the view. */
        const val CHARGE = .3
        const val LICK = 14 / 16.0
        const val BURST_SMALL = 24 / 16.0
        const val BURST_BIG = 40 / 16.0
        const val TALL = 26 / 16.0
        const val EMBER = 1 / 16.0
        /** The flames round a burning enemy: angle, distance and size, as in the sample (×1.25 there). */
        val SPOTS = listOf(Triple(.3, .35, 1.25), Triple(2.4, .45, 1.06), Triple(4.3, .3, 1.38), Triple(1.4, .5, 1.0))
        /** Blocks a tick the ball flies; the sample's ball took 7 ticks for 5 blocks. */
        const val SPEED = 1.4
        const val LIMIT = 160
        fun flightTicks(distance: Double) = kotlin.math.ceil(distance / SPEED).toInt().coerceIn(2, 8)
    }
}
