package dev.projects.server.coreloop

import dev.projects.server.mob.CombatTarget
import dev.projects.server.mob.meleeHit
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import kotlin.math.*

/**
 * Core-loop melee combo: one accepted press, one impact; no client hit declarations. The attacks come from the
 * weapon ([CoreWeaponCombat], Better Combat manner); without one it is the original three greatsword swings.
 */
internal class GreatswordCombo {
    data class Swing(val step: Int, val impactTick: Int, val totalTicks: Int,
        val attack: CoreWeaponAttack = DEFAULT[(step - 1).coerceIn(0, 2)], val finisher: Boolean = step == 3) {
        val multiplier: Double get() = attack.multiplier
        /** 1, 2 alternate through the combo and 3 is the closing attack: what sounds and swing art key on. */
        val beat: Int get() = if (finisher) 3 else (step - 1) % 2 + 1
    }
    var swing: Swing? = null
        private set
    var elapsed = 0
        private set
    private var nextStep = 1
    private var idleTicks = RESET_TICKS
    private var buffered = false
    val isAttacking: Boolean get() = swing != null

    /**
     * [upswing]: Better Combat cadence (2026-10-25, Creator: the greatsword must feel like Better Combat). The whole
     * swing is the weapon's cooldown, shortened by attack speed, and the blade lands [upswing] of the way in.
     */
    fun press(speed: Double, quick: Boolean = false, immediate: Boolean = false, attacks: List<CoreWeaponAttack> = DEFAULT,
              upswing: Double? = null): Swing? {
        val current = swing
        if (current != null) {
            if (elapsed >= current.totalTicks - 6) buffered = true
            return null
        }
        if (idleTicks >= RESET_TICKS || nextStep > attacks.size) nextStep = 1
        val step = nextStep
        nextStep = step % attacks.size + 1
        val attack = attacks[step - 1]
        val haste = if (speed.isFinite()) speed.coerceIn(.75, 2.1) else 1.0
        val startup = round(attack.startup / (1 + .25 * (haste - 1))).toInt().coerceAtLeast(if(quick) 2 else 4)
        val recovery = round(attack.recovery / haste).toInt().coerceAtLeast(if(quick) 5 else 7)
        elapsed = 0
        idleTicks = 0
        if (upswing != null) {
            val total = round((attack.startup + 1 + attack.recovery) / haste).toInt().coerceAtLeast(8)
            return Swing(step, round(total * upswing).toInt().coerceAtLeast(2), total, attack, step == attacks.size).also { swing = it }
        }
        // Immediate AA spends its former startup AFTER the hit. Preserve cadence and
        // the one-slot late input buffer; repeated clicks cannot create extra strikes.
        return Swing(step, if (immediate) 0 else startup + 1, startup + 1 + recovery, attack, step == attacks.size).also { swing = it }
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
    /** Warrior follow-through cancel: preserve the combo step; caller preserves AA cadence. */
    fun endRecovery() {
        idleTicks = -(swing?.let { it.totalTicks - elapsed } ?: 0).coerceAtLeast(0)
        swing = null; buffered = false
    }
    fun reset() { swing = null; elapsed = 0; nextStep = 1; idleTicks = RESET_TICKS; buffered = false }
    fun holdSequence() { idleTicks = -20 }

    companion object {
        const val RESET_TICKS = 18
        /** The original three greatsword swings, for melee without a weapon table. */
        val DEFAULT = listOf(CoreWeaponAttack(CoreHitbox.HORIZONTAL, 1.0, 7, 12, angle = 132.0),
            CoreWeaponAttack(CoreHitbox.HORIZONTAL, 1.15, 7, 14, angle = 132.0), CoreWeaponAttack(CoreHitbox.HORIZONTAL, 1.85, 10, 19, angle = 132.0))
    }
}

/** Feet-relative vertical overlap is symmetric on slopes, unlike comparing feet to mob center. */
internal fun greatswordInRange(origin: Point, direction: Vec, target: CombatTarget, range: Double = 4.5, minDot: Double = .4): Boolean {
    if (target.parts.isNotEmpty()) return target.parts.meleeHit(origin, direction, range, minDot)
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
