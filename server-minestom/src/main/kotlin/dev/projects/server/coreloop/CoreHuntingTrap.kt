package dev.projects.server.coreloop

import net.minestom.server.coordinate.Pos
import kotlin.math.abs

/** One planted trap. The provisional 120-tick wait comes from hunt_trap's existing catalog. */
internal class CoreHuntingTrap(val origin: Pos, val radius: Double, val plantedAt: Long, val waitTicks: Int) {
    var triggeredAt: Long? = null
        private set
    var pulses = 0
        private set
    private var nextPulseAt = Long.MIN_VALUE
    val finished get() = pulses >= 4
    fun contains(feet: Pos): Boolean = abs(feet.y() - origin.y()) <= 1.5 &&
        (feet.x() - origin.x()) * (feet.x() - origin.x()) +
        (feet.z() - origin.z()) * (feet.z() - origin.z()) <= radius * radius

    /** At most one release per tick; no catch-up burst, retrigger, or second set of four waves. */
    fun tick(now: Long, occupied: Boolean): Boolean {
        if (finished || expired(now)) return false
        if (triggeredAt == null && occupied) { triggeredAt = now; nextPulseAt = now }
        if (triggeredAt == null) return false
        if (now >= nextPulseAt && pulses < 4) { pulses++; nextPulseAt = now + 8; return true }
        return false
    }
    fun expired(now: Long) = triggeredAt == null && now >= plantedAt + waitTicks
}
