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
    private val sceneIds = setOf("slam")

    fun owns(p: CoreCombatMeshPart) = p.shape.startsWith(PREFIX)
    fun handles(e: CoreSkillEffect) = enabled && e.job == CoreClass.WARRIOR && e.valid &&
        e.phase == CoreSkillVisualPhase.PULSE && e.sceneId in sceneIds

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
        // 地割り in the Guts mood (Creator 2026-10-20): no light, the weight alone. The slab lands, the ground caves in a
        // crater, a dark crack runs on, clods are thrown up along it (the knock-up) and tumble back, dust rolls out,
        // and the swing leaves a dark band of wind.
        val impact = 1.9
        val length = 6.0
        out += part("wind", local(.3, 1.9, 2.0), 5.0, yaw + PI / 2 - 1.15, 8, ground = false)
        // The ground torn forward: one furrow, widest at the blow, running out ahead (texture row 0 = far end).
        val furrow = length + 2.0
        out += part("furrow", local(0.0, .12, impact - .6 + furrow / 2), 1.0, yaw, 44).copy(scale = Vec(furrow, 1.0, furrow))
        for (k in 0 until 16) {
            val along = impact + .4 + (k / 2) * (length + .6) / 8
            val side = if (k % 2 == 0) -1.0 else 1.0
            out += part("clod$k", local(side * (.3 + .5 * hash(k, 61)), .2, along), .45 + .35 * hash(k, 62), yaw + hash(k, 63) * 6.0,
                22, ground = false, delay = k / 2)
        }
        for (k in 0 until 7) {
            val a = yaw + k / 7.0 * 2 * PI
            out += part("dust$k", local(0.0, .7, impact).add(sin(a) * 1.3, 0.0, cos(a) * 1.3), 1.6 + .6 * hash(k, 64), 0.0, 18,
                ground = false, delay = 1 + k % 3, billboard = true, roll = hash(k, 65) * 6.0)
        }
        for (k in 0 until 5) out += part("dust${10 + k}", local((hash(k, 66) - .5) * 1.2, .6, impact + 1.6 + k * 1.2), 1.2, 0.0, 16,
            ground = false, delay = 2 + k, billboard = true, roll = hash(k, 67) * 6.0)
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
            name == "wind" -> CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0, "$DIR/wind_${tick.coerceAtMost(7)}", live)
            name == "furrow" -> CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0,
                "$DIR/furrow_${when { tick < 4 -> tick; tick < 36 -> 4; tick < 40 -> 5; else -> 6 }}", live)
            name == "crater" -> CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0,
                "$DIR/crater_${when { tick < 34 -> 0; tick < 39 -> 1; else -> 2 }}", live)
            name == "dcrack" -> CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0,
                "$DIR/dcrack_${when { tick < 32 -> 0; tick < 37 -> 1; else -> 2 }}", live)
            name.startsWith("clod") -> {
                val k = name.removePrefix("clod").toInt()
                val s = t.coerceAtLeast(0.0) / 20.0
                val y = (6.5 + 3.0 * hash(k, 71)) * s - 18.0 * s * s / 2
                val a = p.yaw
                CoreMeshPose(p.offset.add(sin(a) * .8 * s, max(y, -.1), cos(a) * .8 * s), p.scale, p.yaw + t * .3, t * .45, t * .25,
                    "$DIR/clod", live)
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
