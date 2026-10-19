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

    private var targetAt: Vec? = null

    /** An ability began; its strike lands [telegraphSeconds] later; [target] is where a target-anchored one lands. */
    fun start(abilityId: String, telegraphSeconds: Double, target: Point? = null) {
        ability = abilityId; abilityAt = time; hit = telegraphSeconds
        targetAt = target?.let { Vec(it.x(), it.y(), it.z()) }
        fired.removeAll { it.startsWith("a:") }
    }

    fun enrage() { enrageAt = time }

    private var burstAt = -1.0
    private var burstKind = ""

    private var sigilCrests: List<Vec> = emptyList()
    private var sigilUntil = -1.0

    /** 桜紋の陣: petals spiral up off every crest until it resolves. */
    fun sigil(crests: List<Pos>, seconds: Double) { sigilCrests = crests.map { Vec(it.x(), it.y(), it.z()) }; sigilUntil = time + seconds }

    /** It resolved: a burst of petals off each crest. */
    fun sigilResolve(crests: List<Pos>) {
        sigilUntil = time
        for (c in crests) repeat(count(60)) {
            val a = r(0.0, 2 * PI); val d = Vec(cos(a), 0.0, sin(a))
            add(Vec(c.x(), c.y() + .4, c.z()).add(d.mul(r(.2, 1.4))), d.mul(r(6.0, 12.0)).add(0.0, r(4.0, 10.0), 0.0), r(.9, 1.5), 1.8)
        }
    }

    private var finaleUntil = -1.0
    private var finaleFrom = 0.0

    /** 千本桜: petals stream in from the whole arena and spiral up round it for [seconds]. */
    fun finale(seconds: Double) { finaleFrom = time; finaleUntil = time + seconds }

    /** 千本桜 blows: everything bursts outward. */
    fun finaleBlow() { finaleUntil = time; burstAt = time; burstKind = "finale" }

    /** Countered: the strike's petals scatter off the shells as it is thrown back. */
    fun countered() { ability = null; burstAt = time; burstKind = "counter" }

    /** A part broke: the horns' blossoms (horns) or the tail tip's shells (tail) burst away. */
    fun partBurst(horns: Boolean) { burstAt = time; burstKind = if (horns) "horns" else "tail" }
    fun death() { deathAt = time; ability = null }

    /**
     * One server tick. [at] is the boss (its yaw is the facing), [point] maps (bone, Blockbench px) to the world.
     * The petals move every tick; their displays are re-sent every [SEND_EVERY] ticks with that much interpolation.
     */
    // --- the counter wreath ---------------------------------------------------------------------------------------
    private val wreath = mutableListOf<Petal>()
    private var wreathUntil = -1.0
    private var wreathFrom = 0.0

    /** The counter window opened: a wreath of petals whirls round its head for [seconds]. */
    fun wreath(seconds: Double) { wreathFrom = time; wreathUntil = time + seconds }

    /** The window closed (or the counter landed): the wreath bursts outward into ordinary petals. */
    fun burstWreath() { wreathUntil = time }

    private fun tickWreath(frame: Frame, point: (String, Vec) -> Point?) {
        if (wreath.isEmpty() && time >= wreathUntil) return
        if (time >= wreathUntil) {
            for (q in wreath) {
                val out = Vec(q.p.x() - wreathCentre.x(), 0.0, q.p.z() - wreathCentre.z()).let { if (it.length() < 1e-6) frame.side else it.normalize() }
                q.v = out.mul(r(10.0, 16.0)).add(0.0, r(1.0, 4.0), 0.0); q.age = 0.0
                if (petals.size < CAP) petals += Petal(q.p, q.v, r(.8, 1.3), 1.6, q.phase, q.flutter, q.spin).also { it.entity = q.entity } else q.entity?.remove()
            }
            wreath.clear()
            return
        }
        wreathCentre = point("skull", WREATH_CENTRE)?.let { Vec(it.x(), it.y(), it.z()) } ?: frame.px(0.0, 50.0, 36.0)
        while (wreath.size < WREATH_PETALS) wreath += Petal(wreathCentre, Vec.ZERO, 99.0, 0.0, r(0.0, 2 * PI), 0.0, r(4.0, 9.0))
        val spin = (time - wreathFrom) * WREATH_TURN
        val grow = minOf(1.0, (time - wreathFrom) / .15)
        for ((k, q) in wreath.withIndex()) {
            val a = spin + k * 2 * PI / WREATH_PETALS
            val radius = WREATH_RADIUS * grow
            val wobble = .25 * sin(a * 2 + spin)
            val pos = wreathCentre.add(cos(a) * radius, wobble, sin(a) * radius)
            q.v = Vec(-sin(a), 0.0, cos(a)).mul(radius * WREATH_TURN)
            q.p = pos
            q.age = .2
            send(q)
        }
    }
    private var wreathCentre: Vec = Vec.ZERO

    fun tick(at: Pos, enraged: Boolean, point: (String, Vec) -> Point?) {
        tick++
        val frame = Frame(at)
        emit(frame, enraged, point)
        tickWreath(frame, point)
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
        wreath.forEach { it.entity?.remove() }
        wreath.clear()
    }

    /** Where every live petal is (head, velocity), for tests and the preview dump. */
    internal fun snapshot(): List<Pair<Vec, Vec>> = petals.map { it.p to it.v }

    // --- emitters --------------------------------------------------------------------------------------------------
    private inner class Frame(val at: Pos) {
        private val yaw = Math.toRadians(at.yaw().toDouble())
        val forward = Vec(-sin(yaw), 0.0, cos(yaw))
        /** Blockbench +X: WSEE mirrors it, so in game it is the beast's right (BossModelView; SakuraFangBossTest checks). */
        val side = Vec(-cos(yaw), 0.0, -sin(yaw))
        val wind: Vec = side.mul(WIND_X).add(forward.mul(WIND_Z))
        /** Model px (Blockbench, +Z front) -> world, ignoring the animation. */
        fun px(x: Double, y: Double, z: Double): Vec = Vec(at.x(), at.y(), at.z()).add(side.mul(x / 16)).add(0.0, y / 16, 0.0).add(forward.mul(z / 16))
        fun dir(x: Double, y: Double, z: Double): Vec = side.mul(x).add(0.0, y, 0.0).add(forward.mul(z))
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
        if (time < finaleUntil) repeat(rate("finale", 70.0 + 50.0 * (time - finaleFrom))) {
            val a = r(0.0, 2 * PI); val rr = r(10.0, 20.0)
            val p = Vec(f.at.x() + cos(a) * rr, f.at.y() + r(.3, 4.0), f.at.z() + sin(a) * rr)
            val inward = Vec(-cos(a), 0.0, -sin(a))
            add(p, inward.mul(rr * .9).add(Vec(-sin(a), 0.0, cos(a)).mul(7.0)).add(0.0, r(1.0, 3.0), 0.0), r(1.0, 1.3), .2)
        }
        if (time < sigilUntil) for ((n, c) in sigilCrests.withIndex()) repeat(rate("sigil$n", 26.0)) {
            val a = r(0.0, 2 * PI); val d = Vec(cos(a), 0.0, sin(a))
            add(c.add(d.mul(1.8)).add(0.0, .2, 0.0), Vec(-d.z(), 0.0, d.x()).mul(4.0).add(0.0, r(2.0, 4.0), 0.0).sub(d.mul(1.0)), r(1.0, 1.6), .3)
        }
        if (burstAt >= 0 && once("burst@$burstAt", burstAt)) {
            val from: () -> Vec = when (burstKind) {
                "horns" -> { { bone("skull", Vec(r(-12.0, 12.0), 64.0, 10.0)) } }
                "tail" -> { { shells[5 + random.nextInt(3)].let { (b, px) -> bone(b, px) } } }
                else -> { { shell() } }
            }
            repeat(count(when (burstKind) { "finale" -> 360; "counter" -> 220; else -> 140 })) {
                add(from(), randomDir().mul(r(10.0, if (burstKind == "finale") 32.0 else 20.0)).add(0.0, 3.0, 0.0), r(1.0, 1.8), 1.6)
            }
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
            "sakura-claw", "sakura-claw2", "sakura-claw3" -> {
                val tip = if (id == "sakura-claw2") bone("hand_l", Vec(15.0, 1.0, 31.0)) else bone("hand_r", Vec(-15.0, 1.0, 31.0))
                val big = if (id == "sakura-claw3") 1.5 else if (id == "sakura-claw2") .6 else 1.0
                if (t in .25..(hit - .05)) repeat(rate("gather", 70.0)) {      // petals spiral into the raised claw
                    val off = randomDir().mul(r(1.75, 2.75))
                    val tangent = off.cross(Vec(0.0, 1.0, 0.0)).normalize()
                    add(tip.add(off), off.mul(-1.9).add(tangent.mul(5.6)), .5, 0.0)
                }
                if (once("a:burst", strike)) {                                  // the slam throws them up along the strip
                    repeat(count((110 * big).toInt())) { add(f.px(r(-34.0, 34.0), 2.0, r(16.0, 96.0)), f.dir(r(-3.8, 3.8), r(8.8, 18.8), r(-1.9, 5.6)), r(1.2, 2.0)) }
                    repeat(count(48)) { k ->
                        val a = k * 2 * PI / count(48)
                        add(Vec(tip.x(), f.at.y() + .2, tip.z()), Vec(cos(a), .15, sin(a)).mul(r(17.5, 21.0)), .9, 3.0)
                    }
                }
            }
            "sakura-lunge" -> {
                if (t in .2..(hit - .02)) repeat(rate("spring", 90.0)) {       // the spring leaves a streak of petals
                    add(shell(), f.dir(r(-1.5, 1.5), r(.3, 2.0), r(-4.0, -1.5)), r(.7, 1.1), 1.0)
                }
                if (once("a:rake", strike)) {                                   // and the rake flings a fan across its front
                    val paw = bone("hand_r", Vec(-15.0, 1.0, 31.0))
                    repeat(count(90)) {
                        val a = Math.toRadians(r(-30.0, 70.0))   // the paw rakes across to its right
                        add(paw.add(randomDir().mul(.3)), f.dir(sin(a), r(.2, .9), cos(a)).mul(r(12.0, 20.0)), r(.8, 1.3), 2.0)
                    }
                }
            }
            "sakura-barge" -> {
                if (t in .1..(hit - .05)) repeat(rate("load", 40.0)) { add(shell(), Vec(r(-1.0, 1.0), r(1.5, 3.5), r(-1.0, 1.0)), .8) }
                if (once("a:barge", strike)) {
                    // the petals burst off the flank it slams in with (whichever side leads), skimming low and ahead,
                    // not off the high back and tail (they sailed up over the target's head)
                    val flank = listOf(bone("chest", Vec(16.0, 34.0, 4.0)), bone("chest", Vec(-16.0, 34.0, 4.0)))
                        .maxBy { (it.x() - f.at.x()) * f.forward.x() + (it.z() - f.at.z()) * f.forward.z() }
                    repeat(count(90)) {
                        val a = Math.toRadians(r(-45.0, 45.0))
                        add(flank.add(randomDir().mul(.5)), f.dir(sin(a), r(-.1, .15), cos(a)).mul(r(12.0, 20.0)), r(.9, 1.3), 2.0)
                    }
                    repeat(count(50)) {                                         // and a spray along the ground where it hits
                        val a = Math.toRadians(r(-70.0, 70.0))
                        add(Vec(flank.x(), f.at.y() + .2, flank.z()), f.dir(sin(a), r(.1, .35), cos(a)).mul(r(8.0, 14.0)), r(.7, 1.1), 2.2)
                    }
                }
            }
            "sakura-hopswipe" -> {
                if (t in .2..(hit - .05)) repeat(rate("hop", 60.0)) { add(shell(), f.dir(r(-3.0, -1.0), r(.5, 2.0), r(-1.0, 1.0)), .8, 1.0) }
                if (t in (hit - .12)..(hit + .15)) repeat(rate("whip", 130.0)) { // the whipping tail throws an arc of petals
                    val (b, px) = shells[3 + random.nextInt(shells.size - 3)]
                    val p = bone(b, px)
                    val radial = Vec(p.x() - f.at.x(), 0.0, p.z() - f.at.z()).let { if (it.length() < 1e-6) f.side else it.normalize() }
                    add(p, Vec(0.0, 1.0, 0.0).cross(radial).mul(13.0).add(radial.mul(5.0)).add(0.0, r(.5, 2.5), 0.0), r(.8, 1.2), 1.4)
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
                    val tangent = Vec(0.0, 1.0, 0.0).cross(radial).let { if (it.length() < 1e-6) f.side else it.normalize() }
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
            "sakura-horn" -> if (once("a:horn", strike)) {                       // the horns rake petals up and ahead
                val head = bone("skull", Vec(0.0, 44.0, 40.0))
                repeat(count(70)) {
                    val a = Math.toRadians(r(-25.0, 25.0))
                    add(head.add(randomDir().mul(.3)), f.dir(sin(a), r(.6, 1.4), cos(a)).mul(r(10.0, 16.0)), r(.9, 1.5), 2.0)
                }
            }
            "sakura-spin" -> {
                if (t in (hit - .3)..(hit + .25)) repeat(rate("spin", 120.0)) {   // the spinning tail throws a ring of petals
                    val (b, px) = shells[3 + random.nextInt(shells.size - 3)]
                    val p = bone(b, px)
                    val radial = Vec(p.x() - f.at.x(), 0.0, p.z() - f.at.z()).let { if (it.length() < 1e-6) f.side else it.normalize() }
                    val tangent = Vec(0.0, 1.0, 0.0).cross(radial)
                    // the same way round as the tail (in game the model is mirrored, so not the preview's way)
                    add(p, tangent.mul(12.0).add(radial.mul(6.0)).add(0.0, r(.5, 2.5), 0.0), r(.8, 1.3), 1.4)
                }
            }
            "sakura-howl" -> {
                if (t in .45..(hit - .1)) repeat(rate("howl-shiver", 50.0)) { add(shell(), Vec(r(-1.5, 1.5), r(2.5, 5.0), r(-1.5, 1.5)), 1.0) }
                if (once("a:howl", strike)) {                                   // the howl blasts every petal outward
                    repeat(count(200)) {
                        val a = r(0.0, 2 * PI); val d = Vec(cos(a), 0.0, sin(a))
                        add(shell(), d.mul(r(20.0, 28.0)).add(0.0, r(2.0, 7.0), 0.0), r(.9, 1.5), 1.8)
                    }
                }
            }
            "sakura-pounce" -> {
                if (t in .6..(hit - .02)) repeat(rate("leap", 80.0)) {          // the leap trails a ribbon of petals
                    add(shell(), f.dir(r(-1.5, 1.5), r(-.5, 1.5), r(-3.0, -1.0)), r(.8, 1.2), 1.2)
                }
                if (once("a:land", strike)) repeat(count(160)) {                // and the landing throws them out in a ring
                    val a = r(0.0, 2 * PI); val d = Vec(cos(a), 0.0, sin(a))
                    add(Vec(f.at.x(), f.at.y() + .3, f.at.z()).add(d.mul(r(.5, 1.5))), d.mul(r(14.0, 22.0)).add(0.0, r(3.0, 9.0), 0.0), r(.9, 1.5), 2.0)
                }
            }
            "sakura-charge" -> {
                if (t in .55..1.4) repeat(rate("wake", 140.0)) {                // the run leaves a wake of petals
                    add(shell(), f.dir(r(-2.5, 2.5), r(.5, 3.0), r(-6.0, -2.0)), r(1.0, 1.6), 1.0)
                }
                if (once("a:skid", abilityAt + 1.4)) repeat(count(120)) {       // the skid sprays them ahead
                    val a = Math.toRadians(r(-50.0, 50.0))
                    add(f.px(r(-16.0, 16.0), 3.0, 30.0), f.dir(sin(a), r(.3, .9), cos(a)).mul(r(10.0, 18.0)), r(.9, 1.4), 2.0)
                }
            }
            "sakura-rain" -> {
                if (t in .5..(hit - .05)) repeat(rate("rise", 60.0)) {           // the howl sends petals up into the sky
                    add(shell(), Vec(r(-1.0, 1.0), r(9.0, 14.0), r(-1.0, 1.0)), 1.0, .3)
                }
                // they come down over the mark (the ability's origin is the target) and burst
                val mark = targetAt
                if (mark != null && t in (hit - .7)..(hit - .02)) repeat(rate("fall", 90.0)) {
                    val a = r(0.0, 2 * PI); val rr = r(0.0, 3.0)
                    add(mark.add(cos(a) * rr, r(6.0, 9.0), sin(a) * rr), Vec(r(-1.0, 1.0), -14.0, r(-1.0, 1.0)), .7, .2)
                }
                if (mark != null && once("a:rain", strike)) repeat(count(150)) {
                    val a = r(0.0, 2 * PI); val d = Vec(cos(a), 0.0, sin(a))
                    add(mark.add(0.0, .4, 0.0).add(d.mul(r(.3, 1.2))), d.mul(r(10.0, 17.0)).add(0.0, r(4.0, 10.0), 0.0), r(.9, 1.4), 2.0)
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
        const val SEND_EVERY = 3
        /** The counter wreath: petals whirling round the head (skull bone, Blockbench px) at this many radians a second. */
        val WREATH_CENTRE = Vec(0.0, 48.0, 32.0)
        const val WREATH_PETALS = 18
        const val WREATH_RADIUS = 2.2
        const val WREATH_TURN = 9.0
        const val CAP = 360
        /** As many petals as the preview (scripts/boss_preview/sakura_fx.py), the Creator's choice. */
        const val DENSITY = 1.0
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
