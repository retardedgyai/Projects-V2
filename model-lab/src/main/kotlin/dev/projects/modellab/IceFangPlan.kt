package dev.projects.modellab

import kotlin.math.cos
import kotlin.math.sin

/** Pure server-side plan shared by the visual clock and training hit clock. */
object IceFangPlan {
    const val MODEL = "ice_fang.bbmodel"
    const val LIFETIME = 30
    const val COOLDOWN = 80
    const val COST = 20
    const val HIT_DELAY = 4
    const val DAMAGE = 88 // Training only: 40 + 80% AP, fixed AP 60. No production balance change.
    data class Point(val x: Double, val y: Double, val z: Double)
    data class Tooth(val point: Point, val start: Int, val size: Float)
    fun direction(yaw: Float): Point {
        val angle = Math.toRadians(yaw.toDouble())
        return Point(-sin(angle), 0.0, cos(angle))
    }
    /** Sample the full corridor, not only three endpoints: a thin wall cannot be skipped. */
    fun path(origin: Point, yaw: Float, clear: (Point) -> Boolean): List<Tooth> {
        val d = direction(yaw)
        val result = mutableListOf<Tooth>()
        for (step in 1..28) {
            val distance = step * .25
            val p = Point(origin.x + d.x*distance, origin.y, origin.z + d.z*distance)
            if (!clear(p)) break
            if (step in listOf(8, 18, 28)) result += Tooth(p, result.size*6, listOf(.8f, 1f, 1.2f)[result.size])
        }
        return result
    }
    fun hits(tooth: Tooth, target: Point): Boolean {
        val dx = target.x-tooth.point.x; val dz = target.z-tooth.point.z
        val radius = 1.15*tooth.size
        return dx*dx + dz*dz <= radius*radius && target.y >= tooth.point.y-1.8 && target.y <= tooth.point.y+2.7*tooth.size
    }
}
