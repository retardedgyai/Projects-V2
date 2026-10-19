package dev.projects.server.coreloop

import dev.projects.server.mob.CrowdControl
import net.minestom.server.coordinate.Pos
import java.util.EnumMap
import kotlin.math.ceil

/**
 * Crowd control a player receives, from enemy attacks now and from other players in PvP fields (2026-10-19, Creator:
 * CC must work on players too, with PvP balance; PvP happens mainly in PvP-enabled fields).
 *
 * Three rules keep it fair:
 * 1. A player gets half the duration a monster would ([PVP_SHARE]) when the source is another player.
 * 2. Diminishing returns: every CC that locks movement shares one category. Within [DR_WINDOW_TICKS] of the last one,
 *    the next lasts 1, 1/2, 1/4 of its duration, then the player is immune until the window passes ([DR_STEPS]).
 *    Bosses hitting a player again and again are held to the same rule.
 * 3. Escape: the dodge (F) breaks every movement lock at once ([escape]); it still costs the dodge cooldown.
 * CC resistance (2026-10-19, Creator: one stat for every kind) shortens every CC by its percent, up to [MAX_RESIST].
 * It never makes a player immune: optional, and stacking it only makes fights easier.
 * Durations are server ticks. Flinch has no duration: the caller cancels the windup.
 */
internal class CorePlayerCrowdControl {
    private val until = EnumMap<CrowdControl, Long>(CrowdControl::class.java)
    private var drCount = 0
    private var drLast = Long.MIN_VALUE / 2
    /** Where charm pulls toward and fear runs from. */
    var source: Pos? = null
        private set

    /** Applies [kind] and returns the ticks it will actually last (0 when diminished to nothing or a flinch). */
    fun apply(kind: CrowdControl, millis: Long, tick: Long, pvp: Boolean, from: Pos?, resist: Double = 0.0): Int {
        if (kind == CrowdControl.FLINCH || millis <= 0) return 0
        var ticks = ceil(millis.coerceAtMost(MAX_MILLIS) / 50.0) * (if (pvp) PVP_SHARE else 1.0) *
            (1 - CoreCombatMath.safe(resist).coerceAtMost(MAX_RESIST) / 100)
        if (locksMovement(kind)) {
            if (tick - drLast > DR_WINDOW_TICKS) drCount = 0
            val share = DR_STEPS.getOrElse(drCount) { 0.0 }
            if (share <= 0.0) return 0
            ticks *= share
            drCount++
            drLast = tick
        }
        val applied = ceil(ticks).toInt().coerceAtLeast(1)
        until[kind] = maxOf(until[kind] ?: 0L, tick + applied)
        if (kind == CrowdControl.CHARM || kind == CrowdControl.FEAR) source = from
        return applied
    }

    fun active(kind: CrowdControl, tick: Long) = (until[kind] ?: 0L) > tick
    fun remaining(kind: CrowdControl, tick: Long) = ((until[kind] ?: 0L) - tick).coerceAtLeast(0)
    /** Stun, airborne, knockback and root hold the player in place; charm and fear move the player for them. */
    fun canMove(tick: Long) = HELD.none { active(it, tick) } && !forced(tick)
    fun forced(tick: Long) = active(CrowdControl.CHARM, tick) || active(CrowdControl.FEAR, tick)
    /** Normal attacks: anything but root, silence and blind stops them. */
    fun canAttack(tick: Long) = INCAPACITATING.none { active(it, tick) }
    fun canCast(tick: Long) = canAttack(tick) && !active(CrowdControl.SILENCE, tick)

    /** Rule 3: the dodge breaks every movement lock. Returns true when it freed the player from something. */
    fun escape(tick: Long): Boolean {
        val freed = until.keys.filter { locksMovement(it) && active(it, tick) }
        freed.forEach { until.remove(it) }
        return freed.isNotEmpty()
    }

    /** The strongest active CC for the HUD cue, e.g. "スタン 0.5秒". */
    fun cue(tick: Long): String? = CUE_ORDER.firstOrNull { active(it, tick) }?.let { kind ->
        "${kind.label} ${CoreCombatMath.number(remaining(kind, tick) / 20.0)}秒"
    }

    fun clear() { until.clear(); drCount = 0; drLast = Long.MIN_VALUE / 2; source = null }

    companion object {
        const val PVP_SHARE = .5
        const val DR_WINDOW_TICKS = 300L
        val DR_STEPS = listOf(1.0, .5, .25)
        /** No player is held longer than this by a single CC, whatever the source asks for. */
        const val MAX_MILLIS = 3000L
        /** CC resistance in percentage points stops here: a CC always lasts at least 40%. */
        const val MAX_RESIST = 60.0
        val HELD = setOf(CrowdControl.STUN, CrowdControl.AIRBORNE, CrowdControl.KNOCKBACK, CrowdControl.ROOT)
        val INCAPACITATING = setOf(CrowdControl.STUN, CrowdControl.AIRBORNE, CrowdControl.KNOCKBACK, CrowdControl.CHARM, CrowdControl.FEAR)
        private val CUE_ORDER = listOf(CrowdControl.STUN, CrowdControl.AIRBORNE, CrowdControl.KNOCKBACK, CrowdControl.CHARM,
            CrowdControl.FEAR, CrowdControl.ROOT, CrowdControl.SILENCE, CrowdControl.BLIND)
        fun locksMovement(kind: CrowdControl) = kind in HELD || kind == CrowdControl.CHARM || kind == CrowdControl.FEAR
    }
}
