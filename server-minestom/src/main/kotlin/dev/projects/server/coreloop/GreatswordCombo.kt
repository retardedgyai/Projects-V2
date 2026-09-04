package dev.projects.server.coreloop

import dev.projects.server.CombatTarget
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Core-loop weapon only: one accepted press, one impact; no client hit declarations. */
internal class GreatswordCombo {
    data class Swing(val step: Int, val impactTick: Int, val totalTicks: Int) {
        val multiplier: Double get() = doubleArrayOf(1.0, 1.15, 1.85)[step - 1]
    }
    var swing: Swing? = null
        private set
    var elapsed = 0
        private set
    private var nextStep = 1
    private var idleTicks = RESET_TICKS
    private var buffered = false
    val isAttacking: Boolean get() = swing != null

    fun press(speed: Double): Swing? {
        val current = swing
        if (current != null) {
            if (elapsed >= current.totalTicks - 6) buffered = true
            return null
        }
        if (idleTicks >= RESET_TICKS) nextStep = 1
        val step = nextStep
        nextStep = step % 3 + 1
        val haste = if (speed.isFinite()) speed.coerceIn(1.0, 1.84) else 1.0
        val startup = round((if (step == 3) 10.0 else 7.0) / (1 + .25 * (haste - 1))).toInt().coerceAtLeast(4)
        val recovery = round(intArrayOf(12, 14, 19)[step - 1] / haste).toInt().coerceAtLeast(7)
        elapsed = 0
        idleTicks = 0
        return Swing(step, startup + 1, startup + 1 + recovery).also { swing = it }
    }

    /** Returns the sole impact frame; recovery includes a two/three-tick contact hold. */
    fun tick(): Swing? {
        val current = swing ?: run { idleTicks = (idleTicks + 1).coerceAtMost(RESET_TICKS); return null }
        elapsed++
        if (elapsed >= current.totalTicks) { swing = null; idleTicks = 0 }
        return current.takeIf { elapsed == it.impactTick }
    }

    fun takeBuffered(): Boolean = buffered.also { buffered = false }
    fun clearBuffer() { buffered = false }
    fun reset() { swing = null; elapsed = 0; nextStep = 1; idleTicks = RESET_TICKS; buffered = false }

    companion object { const val RESET_TICKS = 18 }
}

/** Feet-relative vertical overlap is symmetric on slopes, unlike comparing feet to mob center. */
internal fun greatswordInRange(origin: Point, direction: Vec, target: CombatTarget, range: Double = 4.5, minDot: Double = .4): Boolean {
    if (!listOf(origin.x(), origin.y(), origin.z(), direction.x(), direction.y(), direction.z(),
            target.position.x(), target.position.y(), target.position.z(), target.halfExtent.y(), range, minDot).all { it.isFinite() }) return false
    val dx = target.position.x() - origin.x()
    val dz = target.position.z() - origin.z()
    val distance = hypot(dx, dz)
    // Players hit an open body, not a point that happens to be above/below its feet.
    val feetY = target.position.y() - target.halfExtent.y().coerceAtLeast(0.0)
    if (distance > range || abs(feetY - origin.y()) > 3.0) return false
    val length = hypot(direction.x(), direction.z())
    val forward = if (length > 1e-9) Vec(direction.x() / length, 0.0, direction.z() / length) else Vec(0.0, 0.0, 1.0)
    return distance < .1 || (dx * forward.x() + dz * forward.z()) / distance >= minDot
}
