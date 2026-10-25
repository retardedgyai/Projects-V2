package dev.projects.server.coreloop

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 跳び叩き (Creator 2026-10-22): the warrior leaps toward the aimed point and slams down round where it lands.
 * Not a teleport and not a carried move: the server only gives the body its launch velocity, the client flies the
 * arc with vanilla physics, so the player keeps the usual (small) air control and can bend it a little.
 * The launch is solved against vanilla airborne player physics: each tick the body moves by its velocity, then
 * vertical speed loses gravity and 2% drag, horizontal speed keeps 91%.
 */
internal object CoreWarriorLeap {
    /** The retired 地走り's id is kept for this slot so its HUD art index stays; the icon is still to be drawn. */
    const val ICON = "war_wound"
    const val MIN_REACH = 2.0
    const val MAX_REACH = 8.0
    /** A landing is only taken after this many ticks in the air (the launch tick itself reads as grounded). */
    const val MIN_AIR_TICKS = 4
    /** The slam comes down where the body is if no landing was seen by then. */
    const val MAX_AIR_TICKS = 40
    const val LANDING_RADIUS = 3.5
    private const val GRAVITY = .08
    private const val VERTICAL_DRAG = .98
    private const val AIR_FRICTION = .91

    /** Arc height for a leap of [reach] blocks: about 2.7 blocks over 6. */
    fun apex(reach: Double) = 1.2 + .25 * reach

    /** Where the leap aims: the first solid block along the look within [reach], else the full reach on the same level. */
    fun aim(eye: Pos, look: Vec, solid: (Vec) -> Boolean, reach: Double = MAX_REACH): Vec {
        val flat = Vec(look.x(), 0.0, look.z()).let { if (it.lengthSquared() < 1e-6) Vec(0.0, 0.0, 1.0) else it.normalize() }
        var t = 0.0
        while (t <= reach + 2.0) {
            val p = Vec(eye.x() + look.x() * t, eye.y() + look.y() * t, eye.z() + look.z() * t)
            if (solid(p)) {
                val ground = Vec(p.x(), Math.floor(p.y()) + 1.0, p.z())
                return clampReach(eye, ground, flat, reach)
            }
            t += .25
        }
        return Vec(eye.x() + flat.x() * reach, eye.y() - 1.62, eye.z() + flat.z() * reach)
    }

    private fun clampReach(eye: Pos, target: Vec, flat: Vec, maximum: Double): Vec {
        val dx = target.x() - eye.x(); val dz = target.z() - eye.z()
        val d = sqrt(dx * dx + dz * dz)
        val reach = d.coerceIn(MIN_REACH, maximum)
        val dir = if (d < 1e-6) flat else Vec(dx / d, 0.0, dz / d)
        return Vec(eye.x() + dir.x() * reach, target.y(), eye.z() + dir.z() * reach)
    }

    /** Launch velocity in blocks per tick from [from] (feet) to land on [to] (feet). */
    fun launch(from: Pos, to: Vec, apexOverride: Double? = null): Vec {
        val dx = to.x() - from.x(); val dz = to.z() - from.z()
        val reach = sqrt(dx * dx + dz * dz)
        val rise = to.y() - from.y()
        val peak = max(apexOverride ?: apex(reach), rise + 1.0)
        var lo = 0.0; var hi = 2.0
        repeat(40) { val mid = (lo + hi) / 2; if (peakOf(mid) < peak) lo = mid else hi = mid }
        val vy = hi
        // Ticks until the body comes back down to the target's level, then the horizontal speed that covers the
        // distance in that time under air friction.
        var y = 0.0; var v = vy; var ticks = 0
        while (ticks < 200) { y += v; v = (v - GRAVITY) * VERTICAL_DRAG; ticks++; if (v < 0 && y <= rise) break }
        var sum = 0.0; var f = 1.0
        repeat(ticks) { sum += f; f *= AIR_FRICTION }
        val h = if (reach < 1e-6) 0.0 else reach / sum
        return Vec(dx / max(reach, 1e-6) * h, vy, dz / max(reach, 1e-6) * h)
    }

    private fun peakOf(vy: Double): Double {
        var y = 0.0; var v = vy; var top = 0.0
        while (v > 0) { y += v; top = max(top, y); v = (v - GRAVITY) * VERTICAL_DRAG }
        return top
    }

    /** The arc vanilla physics flies for [velocity] (blocks per tick) with no input; previews and tests replay it. */
    fun path(from: Pos, velocity: Vec, ground: Double = from.y()): List<Pos> {
        val out = mutableListOf<Pos>()
        var p = Vec(from.x(), from.y(), from.z()); var v = velocity
        for (tick in 0 until MAX_AIR_TICKS) {
            p = p.add(v)
            v = Vec(v.x() * AIR_FRICTION, (v.y() - GRAVITY) * VERTICAL_DRAG, v.z() * AIR_FRICTION)
            if (tick > 0 && p.y() <= ground) { out += Pos(p.x(), ground, p.z(), from.yaw(), from.pitch()); break }
            out += Pos(p.x(), p.y(), p.z(), from.yaw(), from.pitch())
        }
        return out
    }
}
