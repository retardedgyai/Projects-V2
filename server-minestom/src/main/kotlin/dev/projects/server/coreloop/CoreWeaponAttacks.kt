package dev.projects.server.coreloop

import dev.projects.server.mob.CombatTarget
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Melee normal attacks in the Better Combat manner (2026-10-19, Creator: derive Project S's weapon combat from the
 * Better Combat lineage; only the model is followed, no data or code is copied). Every melee weapon family has a
 * reach and a combo; every attack in the combo has its own hitbox shape, angle, damage multiplier, windup and
 * recovery, and the hand it swings with. Attacking slows walking; the dodge during the windup is the feint.
 *
 * Hitboxes: HORIZONTAL is a flat sweep [angle] wide; VERTICAL is an upright slice [width] wide straight ahead;
 * FORWARD is a thrust box [width] wide. All reach [CoreWeaponCombat.range] and are server-judged.
 */
internal enum class CoreHitbox { HORIZONTAL, VERTICAL, FORWARD }
internal enum class CoreAttackHand { MAIN, OFF, BOTH }

internal data class CoreWeaponAttack(
    val hitbox: CoreHitbox, val multiplier: Double, val startup: Int, val recovery: Int,
    val angle: Double = 150.0, val width: Double = 1.0, val hand: CoreAttackHand = CoreAttackHand.MAIN,
) {
    /** Does this attack, swung from [origin] toward [direction] with [range], reach [target]? */
    fun reaches(origin: Point, direction: Vec, target: CombatTarget, range: Double): Boolean {
        if (hitbox == CoreHitbox.HORIZONTAL) return greatswordInRange(origin, direction, target, range, cos(angle / 2 * PI / 180))
        if (!greatswordInRange(origin, direction, target, range, -1.0)) return false
        val length = hypot(direction.x(), direction.z())
        if (length < 1e-9) return false
        val fx = direction.x() / length; val fz = direction.z() / length
        val dx = target.position.x() - origin.x(); val dz = target.position.z() - origin.z()
        val forward = dx * fx + dz * fz
        val lateral = abs(dx * fz - dz * fx)
        // The body counts, not only its centre: a big target is hit by a slice through its flank.
        return forward >= -.3 && lateral <= width / 2 + target.halfExtent.x().coerceIn(0.0, 3.0)
    }
}

internal data class CoreWeaponCombat(val range: Double, val attacks: List<CoreWeaponAttack>, val attackSlow: Double, val dual: Boolean = false) {
    companion object {
        /** Greatsword (claymore): right sweep, left sweep, then an overhead slice with a longer windup. */
        private val greatsword = listOf(
            CoreWeaponAttack(CoreHitbox.HORIZONTAL, 1.0, 7, 12, angle = 150.0),
            CoreWeaponAttack(CoreHitbox.HORIZONTAL, 1.15, 7, 14, angle = 150.0),
            CoreWeaponAttack(CoreHitbox.VERTICAL, 1.85, 10, 19, width = 1.4),
        )

        fun of(base: CoreWeaponBase): CoreWeaponCombat? = when (base.family) {
            "greatsword" -> CoreWeaponCombat(4.5, if (base == CoreWeaponBase.CLEAVER)
                // 重断大剣: the closing slice is narrow and far stronger.
                greatsword.dropLast(1) + CoreWeaponAttack(CoreHitbox.VERTICAL, 1.85 * 1.65, 10, 19, width = .7)
                // Walking slows only a little while swinging (Creator 2026-10-24: the old .45 made normal attacks
                // feel stuck in place); Better Combat's default halves the speed, which suits a mouse, not this.
                else greatsword, attackSlow = .2)
            // Dual daggers: the hands alternate (right thrust, left cut, right cut), then both cross.
            "dagger" -> CoreWeaponCombat(3.0, listOf(
                CoreWeaponAttack(CoreHitbox.FORWARD, .8, 3, 7, width = 1.2),
                CoreWeaponAttack(CoreHitbox.HORIZONTAL, 1.0, 3, 7, angle = 100.0, hand = CoreAttackHand.OFF),
                CoreWeaponAttack(CoreHitbox.HORIZONTAL, 1.0, 3, 7, angle = 100.0),
                CoreWeaponAttack(CoreHitbox.HORIZONTAL, 1.4, 4, 11, angle = 120.0, hand = CoreAttackHand.BOTH),
            ), attackSlow = .1, dual = true)
            // 騎槍 (lance): two long thrusts, then a sweep.
            "mace" -> CoreWeaponCombat(5.0, listOf(
                CoreWeaponAttack(CoreHitbox.FORWARD, 1.0, 7, 12, width = 1.0, hand = CoreAttackHand.BOTH),
                CoreWeaponAttack(CoreHitbox.FORWARD, 1.1, 7, 13, width = 1.0, hand = CoreAttackHand.BOTH),
                CoreWeaponAttack(CoreHitbox.HORIZONTAL, 1.5, 10, 18, angle = 120.0, hand = CoreAttackHand.BOTH),
            ), attackSlow = .2)
            else -> null
        }
    }
}
