package dev.projects.server.mob

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.math.hypot

/**
 * What player attacks test against. [parts]: a body made of spheres that follow the model's bones (the 黒翳竜's
 * head, neck, tail, wings, fans); when present they replace the single box for rays and melee.
 */
data class CombatTarget(
    val id: UUID,
    val position: Point,
    val halfExtent: Vec = Vec.ZERO,
    val sphereRadius: Double = 0.0,
    val parts: List<HitSphere> = emptyList(),
)

data class HitSphere(val center: Point, val radius: Double)

/** How far along a ray from [origin] ([direction] unit) the first part within [tolerance] of it is, or null. */
internal fun List<HitSphere>.rayHit(origin: Point, direction: Vec, range: Double, tolerance: Double): Double? = mapNotNull { s ->
    val offset = s.center.sub(origin)
    val along = offset.x() * direction.x() + offset.y() * direction.y() + offset.z() * direction.z()
    if (along !in -s.radius..range + s.radius) return@mapNotNull null      // a shot from inside a sphere still hits it
    if (origin.add(direction.mul(along)).distance(s.center) > s.radius + tolerance) null else along.coerceAtLeast(0.0)
}.minOrNull()

/** A swing from [origin] facing [direction] reaches a part: within [range] of its surface, in front, about level. */
internal fun List<HitSphere>.meleeHit(origin: Point, direction: Vec, range: Double, minDot: Double): Boolean {
    val length = hypot(direction.x(), direction.z())
    val forward = if (length > 1e-9) Vec(direction.x() / length, 0.0, direction.z() / length) else Vec(0.0, 0.0, 1.0)
    return any { s ->
        val dx = s.center.x() - origin.x(); val dz = s.center.z() - origin.z()
        val distance = hypot(dx, dz)
        val dy = s.center.y() - origin.y()
        if (distance - s.radius > range || dy - s.radius > 3.0 || dy + s.radius < -1.0) false
        else distance <= s.radius || (dx * forward.x() + dz * forward.z()) / distance >= minDot
    }
}

/** Distance from [at] to the target's body: its nearest part's surface, or its centre when it has no parts. */
fun CombatTarget.distanceTo(at: Point): Double =
    if (parts.isEmpty()) position.distance(at) else parts.minOf { (it.center.distance(at) - it.radius).coerceAtLeast(0.0) }
