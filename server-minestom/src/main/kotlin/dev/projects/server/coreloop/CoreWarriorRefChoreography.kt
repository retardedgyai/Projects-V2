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
        val impact = 3.6  // the blow lands ahead of where the lunge ends
        val centre = local(0.0, .12, impact)
        val chest = local(0.0, 1.1, impact)
        val out = mutableListOf<CoreCombatMeshPart>()
        // 地割り after Braum's Glacial Fissure: the ground splits along a line (no slash band, no hit flash).
        val start = 1.6
        val length = 7.0
        val steps = 8
        out += part("field", local(0.0, .12, start + length / 2), 1.0, yaw, 44, delay = 2).copy(scale = Vec(2.8, 1.0, length + .6))
        out += part("fissure", local(0.0, .12, start + length / 2), 1.0, yaw, 40).copy(scale = Vec(1.5, 1.0, length))
        // The burst at the blow: a tall fan of shards.
        for (k in 0 until 7) {
            val a = yaw + (k - 3) * .42 + PI
            val r = .5 + .3 * hash(k, 21)
            val at = Vec(local(0.0, 0.0, start).x() - sin(a) * r, .12, local(0.0, 0.0, start).z() - cos(a) * r)
            out += part("shard$k", at, 1.9 + .8 * hash(k, 22), a + PI, 34, pitch = -.35 - .2 * hash(k, 23))
        }
        // The wave: shard clusters in two rows running forward, one step a tick, light streaks at its front.
        for (i in 1..steps) {
            val along = start + i * length / steps
            for ((n, side) in listOf(-1.0, 1.0).withIndex()) {
                val k = 10 + i * 2 + n
                val at = local(side * (.55 + .25 * hash(k, 24)), .12, along + .2 * hash(k, 25))
                val lean = yaw + side * (PI / 2) + (hash(k, 26) - .5) * .6
                out += part("shard$k", at, (1.1 + .7 * hash(k, 27)) * (1 - i / (steps + 4.0)), lean, 34 - i, pitch = .25 + .2 * hash(k, 28), delay = i)
            }
            for (n in 0 until 2) {
                val k = 40 + i * 2 + n
                out += part("streak$k", local((hash(k, 29) - .5) * 1.4, .3, along), 1.0 + .5 * hash(k, 30), 0.0, 5, ground = false,
                    delay = i, roll = -.55 + (hash(k, 31) - .5) * .5, billboard = true)
            }
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
