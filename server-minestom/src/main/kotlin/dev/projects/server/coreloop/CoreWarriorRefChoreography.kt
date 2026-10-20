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
                 ground: Boolean = true, secondary: Boolean = false) =
            CoreCombatMeshPart("$PREFIX$name", "steel", at, Vec(scale, scale, scale), yaw = partYaw, pitch = pitch,
                durationTicks = life, startSize = 1.0, endSize = 1.0, delayTicks = delay, ground = ground, secondary = secondary)
        val impact = 3.6  // the blow lands ahead of where the lunge ends
        val centre = local(0.0, .12, impact)
        val out = mutableListOf<CoreCombatMeshPart>()
        out += part("crack", centre, 5.2, yaw, 34)
        // A crown of iron fangs round the blow, leaning outward; the far ones taller so they read behind the target.
        for (k in 0 until 7) {
            val a = yaw + k / 7.0 * 2 * PI + .2
            val r = 1.15 + .45 * hash(k, 1)
            val at = Vec(centre.x() + sin(a) * r, .12, centre.z() + cos(a) * r)
            out += part("fang$k", at, 1.5 + .8 * hash(k, 2), a, 28, pitch = .32 + .18 * hash(k, 3), delay = (k % 3))
        }
        // The flash: flame boards standing in a ring round the blow, facing out.
        for (k in 0 until 6) {
            val a = yaw + k / 6.0 * 2 * PI + .3
            val at = Vec(centre.x() + sin(a) * .8, .12, centre.z() + cos(a) * .8)
            out += part("flame$k", at, 1.7 + .5 * hash(k, 6), a, 8, pitch = .45)
        }
        for (k in 0 until 6) out += part("plate$k", local(0.0, .6, impact), .42 + .2 * hash(k, 5), yaw, 18, ground = false, secondary = true)
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
            name.startsWith("flame") -> {
                val stage = (tick / 2).coerceAtMost(3)
                val s = flameGrow[tick.coerceAtMost(flameGrow.size - 1)]
                CoreMeshPose(p.offset, p.scale.mul(s), p.yaw, p.pitch, 0.0, "$DIR/flame_$stage", live)
            }
            name.startsWith("plate") -> {
                val k = name.removePrefix("plate").toInt()
                val a = k / 6.0 * 2 * PI + hash(k, 7)
                val speed = 3.5 + 3.0 * hash(k, 8)
                val s = t.coerceAtLeast(0.0) / 20.0
                val y = (5.5 + 3.0 * hash(k, 9)) * s - 9.8 * s * s / 2
                val at = p.offset.add(sin(a) * speed * s, max(y, -.45), cos(a) * speed * s)
                CoreMeshPose(at, p.scale, p.yaw + t * .5, t * (.35 + .3 * hash(k, 4)), t * .25, "$DIR/plate", live)
            }
            else -> null
        }
    }
}
