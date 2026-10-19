package dev.projects.server.puppet

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A player motion played on the puppet (2026-10-19, docs/development/player-motion.md). The animation is the
 * Blockbench model's (assets/player-motions/<model>.bbmodel, copied to resources by the build); the motion JSON
 * names it and says which weapon the puppet holds. Mirrors scripts/player_rig.py: pure FK, Bedrock rotations
 * applied Z then Y then X with X and Y mirrored, 16 px = 1 block before the player model's 0.9375 scale.
 */
internal class PlayerMotion(
    val name: String,
    val length: Double,
    val weapon: Weapon,
    private val tracks: Map<String, BoneTrack>,
) {
    class Weapon(val model: String, val scale: Double, val grip: Double, val dual: Boolean)
    class Key(val time: Double, val value: DoubleArray, val interpolation: String)
    class BoneTrack(val rotation: List<Key>, val position: List<Key>)

    /** Bone frames at [time] seconds: 4x4 row-major, in blocks, puppet space (feet at 0, facing +z). */
    fun pose(time: Double): Map<String, M4> {
        val px = LinkedHashMap<String, M4>()
        for ((name, bone) in BONES) {
            val track = tracks[name]
            val rot = track?.rotation?.let { sample(it, time) } ?: DoubleArray(3)
            val pos = track?.position?.let { sample(it, time) } ?: DoubleArray(3)
            val parentPivot = bone.parent?.let { BONES.getValue(it).pivot } ?: doubleArrayOf(0.0, 0.0, 0.0)
            val local = M4.translation(bone.pivot[0] - parentPivot[0] + pos[0], bone.pivot[1] - parentPivot[1] + pos[1],
                bone.pivot[2] - parentPivot[2] + pos[2]).times(M4.rotation(bedrock(rot[0], rot[1], rot[2])))
            px[name] = bone.parent?.let { px.getValue(it).times(local) } ?: local
        }
        return px.mapValues { (_, m) -> m.scaledTranslation(K) }
    }

    class Bone(val parent: String?, val pivot: DoubleArray)

    companion object {
        /** One skin pixel in blocks: the vanilla player model's scale. */
        const val K = .9375 / 16
        val BONES: Map<String, Bone> = linkedMapOf(
            "root" to Bone(null, doubleArrayOf(0.0, 12.0, 0.0)),
            "torso" to Bone("root", doubleArrayOf(0.0, 12.0, 0.0)),
            "head" to Bone("torso", doubleArrayOf(0.0, 24.0, 0.0)),
            "arm_r_up" to Bone("torso", doubleArrayOf(-5.0, 22.0, 0.0)),
            "arm_r_low" to Bone("arm_r_up", doubleArrayOf(-6.0, 18.0, 0.0)),
            "weapon_r" to Bone("arm_r_low", doubleArrayOf(-6.0, 12.5, 0.0)),
            "arm_l_up" to Bone("torso", doubleArrayOf(5.0, 22.0, 0.0)),
            "arm_l_low" to Bone("arm_l_up", doubleArrayOf(6.0, 18.0, 0.0)),
            "weapon_l" to Bone("arm_l_low", doubleArrayOf(6.0, 12.5, 0.0)),
            "leg_r_up" to Bone("root", doubleArrayOf(-2.0, 12.0, 0.0)),
            "leg_r_low" to Bone("leg_r_up", doubleArrayOf(-2.0, 6.0, 0.0)),
            "leg_l_up" to Bone("root", doubleArrayOf(2.0, 12.0, 0.0)),
            "leg_l_low" to Bone("leg_l_up", doubleArrayOf(2.0, 6.0, 0.0)),
        )

        /** Bedrock bone rotation [x, y, z] degrees -> Rz(z) Ry(-y) Rx(-x). */
        fun bedrock(x: Double, y: Double, z: Double): DoubleArray {
            val a = -x * PI / 180; val b = -y * PI / 180; val c = z * PI / 180
            val rx = doubleArrayOf(1.0, 0.0, 0.0, 0.0, cos(a), -sin(a), 0.0, sin(a), cos(a))
            val ry = doubleArrayOf(cos(b), 0.0, sin(b), 0.0, 1.0, 0.0, -sin(b), 0.0, cos(b))
            val rz = doubleArrayOf(cos(c), -sin(c), 0.0, sin(c), cos(c), 0.0, 0.0, 0.0, 1.0)
            return M4.mul3(M4.mul3(rz, ry), rx)
        }

        internal fun sample(keys: List<Key>, t: Double): DoubleArray {
            if (keys.isEmpty()) return DoubleArray(3)
            if (t <= keys.first().time) return keys.first().value
            for (i in 0 until keys.size - 1) {
                val a = keys[i]; val b = keys[i + 1]
                if (t > b.time) continue
                val u = (t - a.time) / maxOf(b.time - a.time, 1e-9)
                if (a.interpolation == "step" || b.interpolation == "step") return a.value
                if (b.interpolation == "catmullrom") {
                    val p0 = keys[maxOf(i - 1, 0)].value; val p3 = keys[minOf(i + 2, keys.size - 1)].value
                    return DoubleArray(3) { k ->
                        val p1 = a.value[k]; val p2 = b.value[k]
                        .5 * (2 * p1 + (-p0[k] + p2) * u + (2 * p0[k] - 5 * p1 + 4 * p2 - p3[k]) * u * u +
                            (-p0[k] + 3 * p1 - 3 * p2 + p3[k]) * u * u * u)
                    }
                }
                return DoubleArray(3) { k -> a.value[k] + (b.value[k] - a.value[k]) * u }
            }
            return keys.last().value
        }

        private val cache = HashMap<String, PlayerMotion>()

        /** Loads player-motions/<name>.json and the Blockbench animation it names, or null if missing. */
        fun load(name: String): PlayerMotion? = synchronized(cache) {
            cache[name] ?: read(name)?.also { cache[name] = it }
        }

        private fun resource(path: String): String? =
            PlayerMotion::class.java.classLoader.getResourceAsStream(path)?.use { String(it.readAllBytes(), Charsets.UTF_8) }

        private fun read(name: String): PlayerMotion? {
            if (!Regex("[a-z0-9_]+").matches(name)) return null
            val meta = resource("player-motions/$name.json")?.let { JsonParser.parseString(it).asJsonObject } ?: return null
            val source = meta.get("blockbench")?.asString ?: return null
            val (modelName, animName) = source.split('#').takeIf { it.size == 2 } ?: return null
            val model = resource("player-motions/$modelName.bbmodel")?.let { JsonParser.parseString(it).asJsonObject } ?: return null
            val anim = model.getAsJsonArray("animations").map { it.asJsonObject }.firstOrNull { it.get("name").asString == animName } ?: return null
            val w = meta.getAsJsonObject("weapon")
            val weapon = Weapon(w.get("model").asString, w.get("scale").asDouble, w.get("grip").asDouble, w.get("hands").asString == "dual")
            return PlayerMotion(name, anim.get("length").asDouble, weapon, tracks(anim))
        }

        internal fun tracks(anim: JsonObject): Map<String, BoneTrack> {
            val out = HashMap<String, BoneTrack>()
            for ((_, value) in anim.getAsJsonObject("animators").entrySet()) {
                val animator = value.asJsonObject
                val keys = animator.getAsJsonArray("keyframes").map { it.asJsonObject }
                fun channel(name: String) = keys.filter { it.get("channel").asString == name }.map { k ->
                    val p = k.getAsJsonArray("data_points")[0].asJsonObject
                    Key(k.get("time").asDouble, doubleArrayOf(num(p.get("x")), num(p.get("y")), num(p.get("z"))),
                        k.get("interpolation")?.asString ?: "linear")
                }.sortedBy { it.time }
                out[animator.get("name").asString] = BoneTrack(channel("rotation"), channel("position"))
            }
            return out
        }

        private fun num(e: JsonElement): Double = if (e.asJsonPrimitive.isNumber) e.asDouble else e.asString.trim().toDoubleOrNull() ?: 0.0
    }
}

/** Minimal row-major 4x4 transform for the puppet. */
internal class M4(val m: DoubleArray) {
    fun times(o: M4) = M4(DoubleArray(16) { i -> (0 until 4).sumOf { k -> m[i / 4 * 4 + k] * o.m[k * 4 + i % 4] } })
    fun scaledTranslation(k: Double) = M4(m.copyOf().also { it[3] *= k; it[7] *= k; it[11] *= k })
    val origin get() = doubleArrayOf(m[3], m[7], m[11])
    val rotation get() = doubleArrayOf(m[0], m[1], m[2], m[4], m[5], m[6], m[8], m[9], m[10])
    fun apply(v: DoubleArray) = doubleArrayOf(m[0] * v[0] + m[1] * v[1] + m[2] * v[2] + m[3],
        m[4] * v[0] + m[5] * v[1] + m[6] * v[2] + m[7], m[8] * v[0] + m[9] * v[1] + m[10] * v[2] + m[11])

    companion object {
        fun translation(x: Double, y: Double, z: Double) = M4(doubleArrayOf(1.0, 0.0, 0.0, x, 0.0, 1.0, 0.0, y, 0.0, 0.0, 1.0, z, 0.0, 0.0, 0.0, 1.0))
        fun rotation(r: DoubleArray) = M4(doubleArrayOf(r[0], r[1], r[2], 0.0, r[3], r[4], r[5], 0.0, r[6], r[7], r[8], 0.0, 0.0, 0.0, 0.0, 1.0))
        fun mul3(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { i -> (0 until 3).sumOf { k -> a[i / 3 * 3 + k] * b[k * 3 + i % 3] } }
        fun apply3(r: DoubleArray, v: DoubleArray) = doubleArrayOf(r[0] * v[0] + r[1] * v[1] + r[2] * v[2],
            r[3] * v[0] + r[4] * v[1] + r[5] * v[2], r[6] * v[0] + r[7] * v[1] + r[8] * v[2])
        fun yaw(theta: Double) = doubleArrayOf(cos(theta), 0.0, sin(theta), 0.0, 1.0, 0.0, -sin(theta), 0.0, cos(theta))
        fun rotX(a: Double) = doubleArrayOf(1.0, 0.0, 0.0, 0.0, cos(a), -sin(a), 0.0, sin(a), cos(a))
        fun rotY(a: Double) = yaw(a)

        /** 3x3 rotation -> quaternion [x, y, z, w]. */
        fun quaternion(r: DoubleArray): FloatArray {
            val trace = r[0] + r[4] + r[8]
            val q = if (trace > 0) {
                val s = sqrt(trace + 1.0) * 2
                doubleArrayOf((r[7] - r[5]) / s, (r[2] - r[6]) / s, (r[3] - r[1]) / s, .25 * s)
            } else if (r[0] > r[4] && r[0] > r[8]) {
                val s = sqrt(1.0 + r[0] - r[4] - r[8]) * 2
                doubleArrayOf(.25 * s, (r[1] + r[3]) / s, (r[2] + r[6]) / s, (r[7] - r[5]) / s)
            } else if (r[4] > r[8]) {
                val s = sqrt(1.0 + r[4] - r[0] - r[8]) * 2
                doubleArrayOf((r[1] + r[3]) / s, .25 * s, (r[5] + r[7]) / s, (r[2] - r[6]) / s)
            } else {
                val s = sqrt(1.0 + r[8] - r[0] - r[4]) * 2
                doubleArrayOf((r[2] + r[6]) / s, (r[5] + r[7]) / s, .25 * s, (r[3] - r[1]) / s)
            }
            val n = sqrt(q.sumOf { it * it })
            return FloatArray(4) { (q[it] / n).toFloat() }
        }

    }
}
