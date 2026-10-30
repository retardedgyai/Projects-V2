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
 * spell and off again made the screen flash (Creator, the same day: "like being flashed in Valorant").
 *
 * A light keeps one dim strength for its whole life and only moves: every change of a light re-sends its probe, and
 * stepping the strength up and down (10-30) blinked ("still blinking, it hurts my eyes"). So no short bright flashes:
 * [MOVING] follows the fire ball and then the enemy it set burning, [BURST] stands while the frost nova's ice stands.
 */
internal class CoreMageLight(private val owner: Player) {
    private class Light(val light: SakuraLight, var radius: Int, var strength: Double, var palette: Int, var at: Pos, var until: Long)

    private val lights = HashMap<Int, Light>()
    private var tick = 0L
    private val on = HashSet<Player>()
    private val source = "mage@${owner.uuid}"

    /** Keep light [slot] at [at] for the next [ticks] ticks. */
    fun show(slot: Int, at: Pos, radius: Int, strength: Double, palette: Int, ticks: Int = 2) {
        val instance = owner.instance ?: return
        val l = lights[slot]
        if (l == null) { lights[slot] = Light(SakuraLight(instance, at, slot, radius, strength, palette) { it in on }, radius, strength, palette, at, tick + ticks); return }
        if (l.radius != radius || l.strength != strength || l.palette != palette) {
            l.radius = radius; l.strength = strength; l.palette = palette; l.light.set(slot, radius, strength, palette)
        }
        l.until = tick + ticks
        if (l.at != at) { l.at = at; l.light.moveTo(at) }
    }

    /** Kept for the callers: the glow is now on for the whole fight. */
    fun glow(ticks: Int = 0) = Unit

    fun tick() {
        tick++
        val instance = owner.instance
        lights.entries.removeAll { (_, l) -> (instance == null || tick > l.until).also { if (it) l.light.remove() } }
        // the light and the glow are on for the packed players near the mage for the whole fight: switching a post
        // effect on and off is itself a flash
        val wanted = if (instance == null) emptySet() else instance.players.filter {
            CoreCombatPresentation.packed(it) && it.position.distanceSquared(owner.position) < VIEW * VIEW }.toSet()
        for (p in wanted) PlayerPostEffects.set(p, source, listOf(SakuraLight.POST, GLOW))
        for (p in on - wanted) PlayerPostEffects.clear(p, source)
        val changed = on != wanted
        on.clear(); on += wanted
        // only those with the post effect on may see a probe (without it, it shows as a line of coloured pixels)
        if (changed) lights.values.forEach { it.light.entity.updateViewableRule { p -> p in on } }
    }

    fun clear() {
        lights.values.forEach { it.light.remove() }; lights.clear()
        on.forEach { PlayerPostEffects.clear(it, source) }; on.clear()
    }

    companion object {
        val GLOW: Key = Key.key("projects", "mage_glow")
        const val MOVING = 6
        const val BURST = 7
        /** Each light's one strength (of 3) and reach. The light leaves its first 1.5 blocks alone and fades with the
         *  square of the distance, so .8 over 5 blocks showed nothing (2026-10-31, "the fire's light is gone"). */
        const val FIRE = 1.6
        const val FIRE_REACH = 7
        const val FROST = 1.4
        const val FROST_REACH = 9
        const val VIEW = 48.0
    }
}
