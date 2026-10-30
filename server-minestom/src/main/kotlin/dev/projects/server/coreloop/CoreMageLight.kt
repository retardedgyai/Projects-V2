package dev.projects.server.coreloop

import dev.projects.server.mob.PlayerPostEffects
import dev.projects.server.mob.SakuraLight
import net.kyori.adventure.key.Key
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player

/**
 * The light of the mage's spells (Creator 2026-10-28: "ドット絵と光"): coloured point lights that light the ground and
 * the enemies (mob/SakuraLight, the 桜牙獣 light), and a glow round the clearly coloured pixels of the fire and the ice
 * (assets/projects/post_effect/mage_glow.json). Both post effects stay on for the packed players near the mage for the
 * whole fight (through PlayerPostEffects, so the boss arena's own glow and light stay on too): turning one on for a
 * spell and off again made the screen flash (Creator, the same day: "like being flashed in Valorant"). Lights ease in
 * and out instead.
 *
 * Two light slots are the mage's: [MOVING] follows the fire ball or a burning enemy, [BURST] is a hit or the frost nova.
 */
internal class CoreMageLight(private val owner: Player) {
    /** A light eases toward [target] (a step every [STEP_TICKS] ticks, so the probe is not re-sent every tick) and,
     *  once nothing keeps it, fades down to nothing before it goes: no light jumps on or off. */
    private class Light(var light: SakuraLight?, var radius: Int, var target: Double, var shown: Double, var palette: Int, var at: Pos, var until: Long)

    private val lights = HashMap<Int, Light>()
    private var tick = 0L
    private val on = HashSet<Player>()
    private val source = "mage@${owner.uuid}"

    /** Keep light [slot] at [at], easing to [strength], for the next [ticks] ticks. */
    fun show(slot: Int, at: Pos, radius: Int, strength: Double, palette: Int, ticks: Int = 2) {
        val l = lights.getOrPut(slot) { Light(null, radius, strength, 0.0, palette, at, 0) }
        if (l.light != null && (l.radius != radius || l.palette != palette)) l.light!!.set(slot, radius, l.shown.coerceAtLeast(.1), palette)
        l.radius = radius; l.target = strength; l.palette = palette; l.until = tick + ticks
        if (l.at != at) { l.at = at; l.light?.moveTo(at) }
    }

    /** Kept for the callers: the glow is now on for the whole fight. */
    fun glow(ticks: Int = 0) = Unit

    fun tick() {
        tick++
        val instance = owner.instance
        for ((slot, l) in lights.entries.toList()) {
            if (instance == null) { l.light?.remove(); lights.remove(slot); continue }
            val goal = if (tick > l.until) 0.0 else l.target
            if (tick % STEP_TICKS == 0L && l.shown != goal) {
                l.shown = if (goal > l.shown) minOf(goal, l.shown + RISE) else maxOf(goal, l.shown - FALL)
                if (l.shown <= 0.0) { l.light?.remove(); lights.remove(slot); continue }
                l.light?.set(slot, l.radius, l.shown, l.palette)
            }
            if (l.light == null && l.shown > 0) l.light = SakuraLight(instance, l.at, slot, l.radius, l.shown, l.palette) { it in on }
        }
        // the light and the glow are on for the packed players near the mage for the whole fight: switching a post
        // effect on and off is itself a flash
        val wanted = if (instance == null) emptySet() else instance.players.filter {
            CoreCombatPresentation.packed(it) && it.position.distanceSquared(owner.position) < VIEW * VIEW }.toSet()
        for (p in wanted) PlayerPostEffects.set(p, source, listOf(SakuraLight.POST, GLOW))
        for (p in on - wanted) PlayerPostEffects.clear(p, source)
        val changed = on != wanted
        on.clear(); on += wanted
        // only those with the post effect on may see a probe (without it, it shows as a line of coloured pixels)
        if (changed) lights.values.forEach { it.light?.entity?.updateViewableRule { p -> p in on } }
    }

    fun clear() {
        lights.values.forEach { it.light?.remove() }; lights.clear()
        on.forEach { PlayerPostEffects.clear(it, source) }; on.clear()
    }

    companion object {
        val GLOW: Key = Key.key("projects", "mage_glow")
        const val MOVING = 6
        const val BURST = 7
        const val STEP_TICKS = 2
        /** Strength per step up and down: a hit's light of 2.4 rises in 3 steps and fades in about 8. */
        const val RISE = .8
        const val FALL = .3
        const val VIEW = 48.0
    }
}
