package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/**
 * Warrior strikes after the Outer Gods reference rules (2026-10-20, Creator: 「このリファレンスで見ながらウォーリアーの
 * スキル作ってみて」, filmed in game). Every piece is one textured model (scripts/build_warrior_ref_vfx.py) that the
 * server poses each tick: iron fangs burst out of the ground in a crown and overshoot, hold, then sink while losing
 * pixels; flame boards stand round the blow for eight ticks and cool in steps; dark plates are thrown and tumble;
 * the crack star is burnt into the ground and breaks up. 地割り first; the other skills follow once it is approved.
 */
internal object CoreWarriorRefChoreography {
    private const val PREFIX = "war_ref:"
    private const val DIR = "combat_vfx/warrior_ref"
    @Volatile var enabled = true
    private val sceneIds = setOf("slam", "war_wound")
    /** 狂乱 / 天落とし / 大地崩し: every phase is drawn here (their ids still carry older choreographies elsewhere). */
    private val ownedIds = setOf(CoreWarriorRules.FRENZY, CoreWarriorRules.SKYFALL, CoreWarriorRules.SHATTER)

    fun owns(p: CoreCombatMeshPart) = p.shape.startsWith(PREFIX)
    fun handles(e: CoreSkillEffect) = enabled && e.job == CoreClass.WARRIOR && e.valid &&
        (e.phase == CoreSkillVisualPhase.PULSE && e.sceneId in sceneIds || e.sceneId in ownedIds)

    private fun hash(vararg v: Int): Double {
        val x = sin(v.foldIndexed(0.0) { i, a, n -> a + n * doubleArrayOf(127.1, 311.7, 74.7, 19.3)[i % 4] }) * 43758.5453
        return x - floor(x)
    }

    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart>? {
        if (!handles(e)) return null
        val yaw = atan2(e.direction.x(), e.direction.z())
        fun local(x: Double, y: Double, z: Double) = Vec(cos(yaw) * x + sin(yaw) * z, y, -sin(yaw) * x + cos(yaw) * z)
        fun part(name: String, at: Vec, scale: Double, partYaw: Double, life: Int, pitch: Double = 0.0, delay: Int = 0,
                 ground: Boolean = true, roll: Double = 0.0, billboard: Boolean = false) =
            CoreCombatMeshPart("$PREFIX$name", "steel", at, Vec(scale, scale, scale), yaw = partYaw, pitch = pitch, roll = roll,
                durationTicks = life, startSize = 1.0, endSize = 1.0, delayTicks = delay, ground = ground, billboard = billboard)
        val out = mutableListOf<CoreCombatMeshPart>()
        if (e.sceneId == "war_wound") {
            // 跳び叩き landing (Creator 2026-10-22): the blade comes down in front as the body lands. Same family as
            // 地割り and no round crater: a short furrow torn forward, turf kicked up in a fan to the front (biggest
            // straight ahead), a few clods. No dust: the puffs read as stars at this size.
            out += part("furrow", local(0.0, .12, 2.3), 1.0, yaw, 34).copy(scale = Vec(3.8, 1.0, 3.8))
            for (k in 0 until 7) {
                val a = yaw + (k - 3) * .42 + (hash(k, 141) - .5) * .15     // a fan to the front, not a ring
                val d = 1.2 + .4 * hash(k, 142) + .25 * (3 - abs(k - 3))
                out += part("turf${70 + k}", Vec(sin(a) * d, .12, cos(a) * d), (.7 + .35 * hash(k, 143)) * (1.0 + .12 * (3 - abs(k - 3))), a + PI / 2, 30,
                    delay = abs(k - 3) / 2, roll = -(.75 + .35 * hash(k, 144)), pitch = (hash(k, 145) - .5) * .3)
            }
            for (k in 0 until 6) {
                val a = yaw + (k - 2.5) * .5
                out += part("clod${30 + k}", Vec(sin(a) * 1.0, .25, cos(a) * 1.0), .35 + .25 * hash(k, 146), a, 20,
                    ground = false, delay = k / 3).copy(startSize = .95)
            }
            return out
        }
        if (e.sceneId == CoreWarriorRules.SHATTER && e.phase == CoreSkillVisualPhase.PREPARE) {
            // The warning (Earth Eater): before the blow, thin cracks creep out under the feet and stones lift.
            val life = e.prepareDuration.coerceIn(2, 20)
            for (k in 0 until 3) {
                val a = yaw + .7 + k * 2.2 + (hash(k, 191) - .5) * .8
                val len = 1.4 + .9 * hash(k, 195)
                out += part("furrow", Vec(sin(a) * len / 2, .12, cos(a) * len / 2), 1.0, a, life).copy(scale = Vec(len, 1.0, len))
            }
            for (k in 0 until 6) {
                val a = hash(k, 192) * 2 * PI; val d = .9 + 1.6 * hash(k, 193)
                out += part("pebble$k", Vec(sin(a) * d, .1, cos(a) * d), .65 + .3 * hash(k, 194), a, life, ground = false, delay = k % 3)
            }
            return out
        }
        if (e.sceneId in ownedIds && e.phase == CoreSkillVisualPhase.PREPARE) {
            // The wind-up: the weight settles, a little dust off the feet.
            out += part("dust${20}", Vec(0.0, .3, 0.0), .8, 0.0, e.prepareDuration.coerceIn(1, 12), ground = false, billboard = true)
            return out
        }
        if (e.sceneId in ownedIds && e.phase != CoreSkillVisualPhase.PULSE) {
            // A hit landing on an enemy: a clod or two knocked off at its feet.
            for (k in 0 until 2) out += part("clod${90 + k}", Vec((k - .5) * .5, .2, 0.0), .3, yaw + k * 2.0, 14, ground = false).copy(startSize = .6)
            return out
        }
        if (e.sceneId == CoreWarriorRules.FRENZY) {
            // 狂乱: each swing leaves a dark band of wind, left and right in turn, and kicks earth off the tip; the
            // last swing is the overhead blow and tears a short furrow forward like a small 地割り.
            val last = e.pulse >= e.skill.pulses - 1
            if (!last) {
                // The cause first: the blade's dark trail, a different cut each swing (down-left, down-right, flat...).
                val rolls = doubleArrayOf(-.85, .85, 1.45, -.5, .5)
                val roll = rolls[e.pulse % rolls.size]
                out += part("wind", local(0.0, 1.3, 1.4), 4.6, yaw + PI / 2 - 1.15, 7, ground = false, roll = roll)
                // Where the edge comes down: one burst of dust and earth, nothing before it.
                val side = if (roll < 0) -1.0 else if (roll > 1.2) 0.0 else 1.0   // the side the trail comes down on
                val at = local(side * .9, .25, 2.3)
                for (k in 0 until 4) out += part("clod${40 + k}", at.add((hash(e.pulse, k, 150) - .5) * .5, 0.0, (hash(e.pulse, k, 149) - .5) * .5),
                    .3 + .15 * hash(e.pulse, k, 151), yaw + hash(e.pulse, k, 152) * 6.0, 16, ground = false, delay = 2 + k / 2).copy(startSize = .7)
                for (k in 0 until 2) out += part("dust${70 + k}", at.add(0.0, .2 + .3 * k, 0.0), 1.2 - .2 * k, 0.0, 10, ground = false,
                    delay = 2, billboard = true, roll = hash(e.pulse, k, 148) * 6.0)
                return out
            }
            out += part("wind", local(.3, 1.9, 2.0), 5.0, yaw + PI / 2 - 1.15, 8, ground = false)
            out += part("furrow", local(0.0, .12, 2.4), 1.0, yaw, 34).copy(scale = Vec(4.0, 1.0, 4.0))
            for (k in 0 until 6) {
                val side = if (k % 2 == 0) -1.0 else 1.0
                val tt = 1.0 - (k / 2) / 3.0
                out += part("turf${80 + k}", local(side * (.45 + .2 * hash(k, 153)), .12, 1.0 + (1 - tt) * 2.6), (.6 + .5 * tt) * (.8 + .4 * hash(k, 154)),
                    yaw + (hash(k, 155) - .5) * 1.0, 28, delay = ((1 - tt) * 3).toInt(), roll = -side * (.4 + .5 * hash(k, 156)))
            }
            return out
        }
        if (e.sceneId == CoreWarriorRules.SKYFALL) {
            // 天落とし: the landing caves the ground in and each ring heaves a circle of turf up, tipped outward, a
            // moment after the one inside it. Ring 0 also throws the biggest slabs right round the blow.
            val r = e.skill.radius
            // Each ring heaves up and drops back quickly, so the eye follows a wave going out, not a bullseye.
            if (e.pulse == 0) {
                // The cause: the blade brought straight down with the fall, then a column of dust off the impact point.
                out += part("wind", local(.2, 1.6, .9), 5.0, yaw + PI / 2 - 1.15, 6, ground = false)
                for (k in 0 until 3) out += part("dust${74 + k}", Vec(0.0, .4 + .8 * k, 0.0), 2.4 - .4 * k, 0.0, 12 + 2 * k, ground = false,
                    billboard = true, roll = hash(k, 159) * 6.0)
            }
            val count = (5 + 1.5 * r).roundToInt().coerceAtMost(14)
            for (k in 0 until count) {
                if (hash(e.pulse, k, 160) < .18) continue
                val a = yaw + k * 2 * PI / count + hash(e.pulse, k, 161) * .3
                val d = r - .5 - .4 * hash(e.pulse, k, 162)
                out += part("turf${90 + k}", Vec(sin(a) * d, .12, cos(a) * d), (.6 + .35 * hash(e.pulse, k, 163)) * (1.15 - .1 * e.pulse),
                    a + PI / 2, 14, delay = k % 2, roll = -(.9 + .4 * hash(e.pulse, k, 164)), pitch = (hash(e.pulse, k, 165) - .5) * .4)
            }
            for (k in 0 until 4) {
                val a = yaw + k * PI / 2 + hash(e.pulse, k, 169) * 1.2
                out += part("clod${70 + k}", Vec(sin(a) * (r - .4), .25, cos(a) * (r - .4)), .35 + .2 * hash(e.pulse, k, 170), a, 16,
                    ground = false, delay = k % 2).copy(startSize = .8)
            }
            if (e.pulse == 0) {
                // The ground caves in under the blow (the round crater fits a landing; 地割り runs forward instead).
                out += part("crater", Vec(0.0, .11, 0.0), 4.2, yaw + hash(e.pulse, 175) * 6.0, 44)
                // The blow itself: five uneven slabs thrown up round the blade (not a flower: angles and sizes vary).
                for (k in 0 until 5) {
                    val a = yaw + k * 2 * PI / 5 + (hash(k, 166) - .5) * .9
                    val d = .9 + .5 * hash(k, 171)
                    out += part("turf${130 + k}", Vec(sin(a) * d, .12, cos(a) * d), .9 + .6 * hash(k, 172), a + PI / 2 + (hash(k, 173) - .5) * .6, 32,
                        roll = -(.8 + .5 * hash(k, 167)), pitch = (hash(k, 174) - .5) * .5)
                }
                for (k in 0 until 6) {
                    val a = yaw + k * PI / 3
                    out += part("clod${50 + k}", Vec(sin(a) * .6, .3, cos(a) * .6), .4 + .3 * hash(k, 168), a, 22, ground = false,
                        delay = k / 3).copy(startSize = 1.2)
                }
            }
            return out
        }
        if (e.sceneId == CoreWarriorRules.SHATTER) {
            // 大地崩し: the blade is driven in and the ground round it breaks up everywhere at once: cracks run out from
            // the blade, the turf between them is flung up in slabs, the wave reaching the edge last.
            val r = e.skill.radius
            // The cause: the blade driven straight down, then a burst at the point it went in.
            out += part("wind", local(.1, 1.3, .8), 3.4, yaw + PI / 2 - 1.15, 6, ground = false)
            for (k in 0 until 2) out += part("dust${78 + k}", Vec(0.0, .4 + .6 * k, 0.0), 1.6 - .3 * k, 0.0, 12, ground = false,
                delay = 1, billboard = true, roll = hash(k, 189) * 6.0)
            // Five cracks, uneven in angle and length (not a star), each widest at the blade.
            val angles = (0 until 5).map { k -> yaw + k * 2 * PI / 5 + (hash(k, 171) - .5) * .9 }
            for ((k, a) in angles.withIndex()) {
                val len = r * (.6 + .4 * hash(k, 170))
                out += part("furrow", Vec(sin(a) * len / 2, .12, cos(a) * len / 2), 1.0, a, 40).copy(scale = Vec(len, 1.0, len))
            }
            // The ground between the cracks comes up in big slabs, tipped away from the blade, inner ones first.
            for (k in 0 until 5) for (j in 0 until 3) {
                val n = k * 3 + j
                if (hash(n, 181) < .25) continue
                val a = angles[k] + PI / 5 + (hash(n, 172) - .5) * .5
                val d = 1.2 + j * (r - 1.8) / 2.6 + .4 * hash(n, 173)
                out += part("turf${140 + n}", Vec(sin(a) * d, .12, cos(a) * d), (.95 + .45 * hash(n, 174)) * (1.0 - .12 * j), a + PI / 2, 30,
                    delay = j * 2, roll = -(.6 + .5 * hash(n, 176)), pitch = (hash(n, 177) - .5) * .4)
            }
            for (k in 0 until 10) {
                val a = hash(k, 178) * 2 * PI
                val d = 1.0 + (r - 1.5) * hash(k, 179)
                out += part("clod${60 + k}", Vec(sin(a) * d, .25, cos(a) * d), .35 + .3 * hash(k, 180), a, 22, ground = false,
                    delay = (d / r * 5).toInt()).copy(startSize = .8 + .1 * r / 2)
            }
            return out
        }
        // 地割り in the Guts mood (Creator 2026-10-20): no light, the weight alone. The slab lands, the ground is torn
        // forward in a furrow, turf is torn up along its lips, clods are thrown up along it (the knock-up) and tumble
        // back, dust rolls out, and the swing leaves a dark band of wind.
        // The beast it took sets the reach (CoreWarriorRules.slam puts it in the radius, Creator 2026-10-23): the furrow
        // ends just past it, and the more beast, the bigger the torn turf and the higher the clods.
        val impact = 1.9
        val reach = e.skill.radius.coerceIn(3.0, 9.0)
        val weight = ((reach - 4.0) / 4.0).coerceIn(0.0, 1.0)      // 0 at ≈4 m (no beast) .. 1 at ≈8 m
        val furrow = reach - .8
        val head = impact - .6 + furrow
        if (e.pulse >= 1) {
            // The second split at the head (three stacks): the ground bursts open where the furrow ended and forks.
            for ((n, turn) in listOf(-.55, .55).withIndex())
                out += part("furrow", local(0.0, .12, head - .3).add(sin(yaw + turn) * 1.6, 0.0, cos(yaw + turn) * 1.6), 1.0, yaw + turn, 36,
                    delay = n).copy(scale = Vec(3.2, 1.0, 3.2))
            // Not a ring (Creator: no round crater, no petals): the ground at the head heaves forward in a fan,
            // slabs tipped forward over the forks, the far ones biggest.
            for (k in 0 until 5) {
                val a = yaw + (k - 2) * .42 + (hash(k, 131) - .5) * .12
                val d = .9 + .5 * hash(k, 132) + .3 * (2 - abs(k - 2))
                out += part("turf${60 + k}", local(0.0, .12, head).add(sin(a) * d, 0.0, cos(a) * d),
                    1.1 + .4 * hash(k, 133), a, 34, delay = 1 + abs(k - 2) / 2, roll = (hash(k, 134) - .5) * .3, pitch = -(.9 + .3 * hash(k, 135)))
            }
            for (k in 0 until 6) {
                val a = yaw + (k - 2.5) * .3
                out += part("clod${20 + k}", local(0.0, .3, head).add(sin(a) * .8, 0.0, cos(a) * .8), .45 + .3 * hash(k, 136), a, 22,
                    ground = false, delay = k / 4).copy(startSize = 1.2)
            }
            return out
        }
        out += part("wind", local(.3, 1.9, 2.0), 5.0, yaw + PI / 2 - 1.15, 8, ground = false)
        // The ground torn forward: one furrow, widest at the blow, running out ahead (texture row 0 = far end).
        out += part("furrow", local(0.0, .12, impact - .6 + furrow / 2), 1.0, yaw, 44).copy(scale = Vec(furrow, 1.0, furrow))
        // The furrow's lips: turf torn up along both edges and tipped outward, biggest at the blow, coming up as the
        // furrow runs out (4 ticks for its whole length).
        // Irregular on purpose (Creator: not tiles): sizes, gaps, tilt and shape differ piece by piece, a few gaps left.
        val rows = (9 * furrow / 7.2).roundToInt().coerceIn(4, 9)
        val bulk = .75 + .2 * weight
        for (jj in 0 until rows) {
            for ((n, side) in listOf(-1.0, 1.0).withIndex()) {
                val k = jj * 2 + n
                if (hash(k, 94) < .2) continue                             // a gap here and there
                val tt = (1.0 - (jj + hash(k, 95) * .8) / rows).coerceIn(0.0, 1.0)   // 1 at the blow .. 0 far ahead
                val along = impact - .4 + (1 - tt) * (furrow - .6)
                val hw = (1.2 + 12.5 * tt.pow(1.4)) / 16.0 * furrow / 8.0
                val size = (.35 + .8 * tt) * (.6 + .8 * hash(k, 96)) * bulk
                out += part("turf$k", local(side * (hw + .15 + .2 * hash(k, 91)), .12, along), size,
                    yaw + (hash(k, 92) - .5) * 1.4, 34 - jj, delay = ((1 - tt) * 4).toInt(),
                    roll = -side * (.15 + .8 * hash(k, 93)) * (.7 + .5 * weight), pitch = (hash(k, 97) - .5) * .5)
            }
        }
        // Clods thrown up at the furrow's head as it runs (the knock-up), grass-topped turf among the earth;
        // more and higher with more beast.
        val clods = 4 + (4 * weight).roundToInt()
        for (k in 0 until clods) {
            val along = impact + (k / 2) * (furrow - 1.0) / ((clods + 1) / 2)
            val side = if (k % 2 == 0) -1.0 else 1.0
            out += part("clod$k", local(side * (.2 + .35 * hash(k, 61)), .2, along), (.3 + .3 * hash(k, 62)) * bulk, yaw + hash(k, 63) * 6.0,
                20, ground = false, delay = k / 2).copy(startSize = .7 + .6 * weight)
        }
        // A little dust, low and short: it must not hide the furrow.
        for (k in 0 until 4) {
            val a = yaw + PI / 2 + k * PI / 3
            out += part("dust$k", local(0.0, .4, impact).add(sin(a) * 1.0, 0.0, cos(a) * 1.0), 1.0 + .3 * hash(k, 64), 0.0, 12,
                ground = false, delay = 1 + k % 2, billboard = true, roll = hash(k, 65) * 6.0)
        }
        return out
    }

    private val rise = doubleArrayOf(0.0, .72, 1.12, 1.0)
    private val flameGrow = doubleArrayOf(.6, 1.0, 1.12, 1.1, 1.0, .9, .78, .6)

    fun pose(p: CoreCombatMeshPart, age: Double): CoreMeshPose? {
        if (!owns(p)) return null
        val t = age - p.delayTicks
        val live = t >= 0 && t < p.durationTicks
        val tick = t.toInt().coerceIn(0, p.durationTicks - 1)
        val name = p.shape.removePrefix(PREFIX)
        return when {
            name == "fissure" -> {
                val stage = when { tick < 3 -> 0; tick < 6 -> 1; tick < 9 -> 2; tick < 18 -> 3; tick < 30 -> 4; tick < 35 -> 5; else -> 6 }
                CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0, "$DIR/fissure_$stage", live)
            }
            name == "field" -> CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0, "$DIR/field", live && tick < p.durationTicks - 2)
            name == "wind" -> CoreMeshPose(p.offset, p.scale, p.yaw, p.pitch, p.roll, "$DIR/wind_${tick.coerceAtMost(7)}", live)
            name.startsWith("pebble") -> {
                // 大地崩し's warning: a stone lifts off the ground and trembles there until the blow.
                val lift = (t.coerceAtLeast(0.0) / 10.0).coerceAtMost(1.0) * .45
                CoreMeshPose(p.offset.add(0.0, lift + sin(t * 1.9) * .04, 0.0), p.scale, p.yaw + t * .05, .3, .2, CoreCombatMeshes.GROUND, live)
            }
            name == "sweep" -> CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, p.roll, "$DIR/sweep_${tick.coerceAtMost(7)}", live)
            name == "scrape" -> CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0,
                "$DIR/scrape_${when { tick < 4 -> tick; tick < 32 -> 4; tick < 36 -> 5; else -> 6 }}", live)
            name == "furrow" -> CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0,
                "$DIR/furrow_${when { tick < 4 -> tick; tick < 36 -> 4; tick < 40 -> 5; else -> 6 }}", live)
            name == "crater" -> CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0,
                "$DIR/crater_${when { tick < 34 -> 0; tick < 39 -> 1; else -> 2 }}", live)
            name == "dcrack" -> CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0,
                "$DIR/dcrack_${when { tick < 32 -> 0; tick < 37 -> 1; else -> 2 }}", live)
            name.startsWith("turf") -> {
                val up = when {
                    tick < 4 -> doubleArrayOf(.0, .7, 1.15, 1.05)[tick]
                    tick < p.durationTicks - 7 -> 1.0
                    else -> 1.0 - (tick - (p.durationTicks - 7)) / 6.0
                }.coerceIn(0.0, 1.2)
                val k = name.removePrefix("turf").toInt()
                // The ground block itself (a vanilla block item renders at half size in FIXED): a flat slab of it,
                // each one its own proportions, tipped about its centre.
                val sx = p.scale.x() * (1.4 + .5 * hash(k, 99)); val sy = p.scale.x() * (.45 + .3 * hash(k, 100))
                val sz = p.scale.x() * (1.1 + .5 * hash(k, 101))
                CoreMeshPose(p.offset.add(0.0, sy * .25 - p.scale.y() * .3 * (1 - up.coerceAtMost(1.0)), 0.0), Vec(sx, sy, sz),
                    p.yaw, p.pitch * up, p.roll * up, CoreCombatMeshes.GROUND, live)
            }
            name.startsWith("clod") -> {
                val k = name.removePrefix("clod").toInt()
                val s = t.coerceAtLeast(0.0) / 20.0
                val y = (6.5 + 3.0 * hash(k, 71)) * p.startSize * s - 18.0 * s * s / 2   // startSize carries the beast's lift
                val a = p.yaw
                CoreMeshPose(p.offset.add(sin(a) * .8 * s, max(y, -.1), cos(a) * .8 * s), p.scale.mul(1.6), p.yaw + t * .3, t * .45, t * .25,
                    CoreCombatMeshes.GROUND, live)
            }
            name.startsWith("dust") -> CoreMeshPose(p.offset.add(0.0, t.coerceAtLeast(0.0) * .02, 0.0), p.scale.mul(1.0 + tick * .05),
                0.0, 0.0, p.roll, "$DIR/dust_${(tick * 5 / p.durationTicks).coerceAtMost(4)}", live)
            name.startsWith("fing") -> {
                val idx = name.removePrefix("fing").toInt()
                val seg = idx % 3
                val lengths = doubleArrayOf(22.0, 18.0, 14.0).map { it / 16 * p.scale.y() }
                // Joint bends: spread a little backward while rising, then close (grip), then drag down.
                val grip = when {
                    tick < 12 -> 0.0
                    tick < 18 -> (tick - 12) / 6.0
                    else -> 1.0
                }
                val bends = doubleArrayOf(-.18 + .75 * grip, -.05 + .95 * grip, .85 * grip)
                val total = lengths.sum()
                val up = when {
                    tick < 4 -> doubleArrayOf(.0, .55, 1.08, 1.02)[tick]
                    tick < p.durationTicks - 9 -> 1.0
                    else -> 1.0 - (tick - (p.durationTicks - 9)) / 8.0
                }.coerceIn(0.0, 1.1)
                // Forward kinematics from the knuckle on the ground.
                var at = p.offset.add(0.0, -total * (1 - up.coerceAtMost(1.0)), 0.0)
                var pitch = 0.0
                for (s2 in 0 until seg) {
                    pitch += bends[s2]
                    at = at.add(sin(pitch) * sin(p.yaw) * lengths[s2], cos(pitch) * lengths[s2], sin(pitch) * cos(p.yaw) * lengths[s2])
                }
                pitch += bends[seg]
                CoreMeshPose(at, p.scale, p.yaw, pitch, 0.0, "$DIR/finger_$seg", live)
            }
            name == "gcrack" -> {
                val stage = when { tick < 6 -> 0; tick < 16 -> 1; tick < 28 -> 2; else -> 3 }
                CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0, "$DIR/gcrack_$stage", live)
            }
            name.startsWith("lump") -> {
                // Heaved up from below with an overshoot, settles, then sinks back at the end.
                val up = when {
                    tick < 4 -> doubleArrayOf(.0, .7, 1.15, 1.05)[tick]
                    tick < p.durationTicks - 7 -> 1.0
                    else -> 1.0 - (tick - (p.durationTicks - 7)) / 6.0
                }.coerceIn(0.0, 1.2)
                val k = name.removePrefix("lump").toInt()
                CoreMeshPose(p.offset.add(0.0, -p.scale.y() * .6 * (1 - up.coerceAtMost(1.0)), 0.0), p.scale, p.yaw,
                    p.pitch * up, p.roll * up, "$DIR/lump_${k % 3}", live)
            }
            name.startsWith("petal") -> {
                // Bursts up leaning inward (closed bud), opens outward past its rest with overshoot, holds, closes and sinks.
                val open = when {
                    tick < 2 -> -.5
                    tick < 5 -> doubleArrayOf(-.1, .75, .62)[tick - 2]
                    tick < p.durationTicks - 8 -> .62 + .04 * sin(tick * .7)
                    else -> .62 - (tick - (p.durationTicks - 8)) / 7.0 * 1.0
                }
                val rise = when { tick < 2 -> doubleArrayOf(.3, .8)[tick]; tick < p.durationTicks - 5 -> 1.0
                    else -> 1.0 - (tick - (p.durationTicks - 5)) / 4.0 }.coerceIn(0.0, 1.0)
                val k = name.removePrefix("petal").toInt()
                CoreMeshPose(p.offset.add(0.0, -p.scale.y() * 2.0 * (1 - rise), 0.0), p.scale, p.yaw, -open, 0.0, "$DIR/petal_${k % 3}", live)
            }
            name.startsWith("gslab") -> {
                val height = p.scale.y() * 12.0 / 16.0
                val up = when {
                    tick < 3 -> doubleArrayOf(.0, .8, 1.12)[tick]
                    tick < p.durationTicks - 8 -> 1.0
                    else -> 1.0 - (tick - (p.durationTicks - 8)) / 7.0
                }.coerceIn(0.0, 1.2)
                val k = name.removePrefix("gslab").toInt()
                CoreMeshPose(p.offset.add(0.0, -height * (1 - up), 0.0), p.scale, p.yaw, p.pitch * up.coerceAtMost(1.0), 0.0, "$DIR/gslab_${k % 3}", live)
            }
            name.startsWith("chunk") -> {
                val k = name.removePrefix("chunk").toInt()
                val s = t.coerceAtLeast(0.0) / 20.0
                val a = p.yaw + (hash(k, 33) - .5) * 2.4
                val y = (5.0 + 3.0 * hash(k, 34)) * s - 9.8 * s * s / 2
                CoreMeshPose(p.offset.add(sin(a) * 2.0 * s, max(y, -.3), cos(a) * 2.0 * s), p.scale, p.yaw + t * .4, t * .5, t * .3, "$DIR/chunk", live)
            }
            name.startsWith("shard") -> {
                val height = p.scale.y() * 18.0 / 16.0
                val up = when {
                    tick < 3 -> doubleArrayOf(.0, .8, 1.15)[tick]
                    tick < p.durationTicks - 8 -> 1.0
                    else -> 1.0 - (tick - (p.durationTicks - 8)) / 7.0
                }.coerceIn(0.0, 1.2)
                val model = when { tick < p.durationTicks - 9 -> 0; tick < p.durationTicks - 5 -> 1; else -> 2 }
                CoreMeshPose(p.offset.add(0.0, -height * (1 - up), 0.0), p.scale, p.yaw, p.pitch, 0.0, "$DIR/shard_$model", live)
            }
            name.startsWith("streak") -> CoreMeshPose(p.offset, p.scale.mul(1.0 + tick * .15), 0.0, 0.0, p.roll, "$DIR/streak", live)
            name.startsWith("slab") -> {
                // Thrown up out of the split (overshoot), held, then settling back down.
                val lift = when {
                    tick < 3 -> doubleArrayOf(.0, .75, 1.1)[tick]
                    tick < p.durationTicks - 6 -> 1.0
                    else -> 1.0 - (tick - (p.durationTicks - 6)) / 5.0
                }.coerceIn(0.0, 1.2)
                val heat = when { tick < 8 -> 0; tick < 16 -> 1; else -> 2 }
                CoreMeshPose(p.offset.add(0.0, -.35 + .35 * lift, 0.0), p.scale, p.yaw, 0.0, p.roll * lift, "$DIR/slab_$heat", live)
            }
            name == "crack" -> {
                val stage = when { tick < 3 -> 0; tick < 9 -> 1; tick < 22 -> 2; tick < 28 -> 3; else -> 4 }
                CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0, "$DIR/crack_$stage", live)
            }
            name.startsWith("fang") -> {
                val height = p.scale.y() * 17.0 / 16.0
                val up = when {
                    tick < rise.size -> rise[tick]
                    tick < p.durationTicks - 7 -> 1.0
                    else -> 1.0 - (tick - (p.durationTicks - 7)) / 6.0
                }.coerceIn(0.0, 1.2)
                val model = when { tick < p.durationTicks - 9 -> 0; tick < p.durationTicks - 5 -> 1; else -> 2 }
                // The fang stays the same size; it slides out of the ground and back.
                CoreMeshPose(p.offset.add(0.0, -height * (1 - up), 0.0), p.scale, p.yaw, p.pitch, 0.0, "$DIR/fang_$model", live)
            }
            name.startsWith("ray") -> {
                val stage = intArrayOf(0, 0, 1, 1, 2, 3)[tick.coerceAtMost(5)]
                val grow = doubleArrayOf(.55, 1.0, 1.08, 1.05, .95, .8)[tick.coerceAtMost(5)]
                CoreMeshPose(p.offset, p.scale.mul(grow), 0.0, 0.0, p.roll, "$DIR/ray_$stage", live)
            }
            name == "frame" -> CoreMeshPose(p.offset, p.scale, 0.0, 0.0, 0.0, "$DIR/frame", live)
            name == "ring" -> CoreMeshPose(p.offset, p.scale.mul(1.0 + tick * .06), 0.0, 0.0, 0.0, "$DIR/ring_${(tick / 2).coerceAtMost(3)}", live)
            name.startsWith("twinkle") -> CoreMeshPose(p.offset, p.scale.mul(if (tick % 2 == 0) 1.0 else .8), 0.0, 0.0, 0.0,
                "$DIR/twinkle_${(tick / 2).coerceAtMost(2)}", live)
            name.startsWith("bar") -> {
                val k = name.removePrefix("bar").toInt()
                val a = p.yaw - PI / 2
                val s = t.coerceAtLeast(0.0) / 20.0
                val speed = 9.0 + 4.0 * hash(k, 15)
                val y = (3.0 + 3.0 * hash(k, 16)) * s - 9.8 * s * s / 2
                val at = p.offset.add(sin(a) * speed * s, y, cos(a) * speed * s)
                CoreMeshPose(at, p.scale, p.yaw, t * .12 * (hash(k, 17) - .5), t * .3 * (hash(k, 18) - .5), "$DIR/bar", live)
            }
            else -> null
        }
    }
}
