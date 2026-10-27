package dev.projects.server.coreloop

import dev.projects.server.mob.PlayerPostEffects
import dev.projects.server.mob.SakuraLight
import net.kyori.adventure.key.Key
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player

/**
 * The light of the mage's spells (Creator 2026-10-28: "ドット絵と光"): coloured point lights that light the ground and
 * the enemies (mob/SakuraLight, the 桜牙獣 light), and a glow round the brightest pixels of the fire and the ice
 * (assets/projects/post_effect/mage_glow.json). Both are post effects, put on for the packed players near the mage
 * while a light stands or a glow was asked for in the last [GLOW_HOLD] ticks, through PlayerPostEffects so the boss
 * arena's own glow and light stay on.
 *
 * Two light slots are the mage's: [MOVING] follows the fire ball or a burning enemy, [BURST] is a hit or the frost nova.
 * A probe is re-sent only when its radius, strength or colour changes (a new item every tick makes the light blink), so
 * [show] is called with steady values and changes them in steps.
 */
internal class CoreMageLight(private val owner: Player) {
    private class Light(var light: SakuraLight?, var radius: Int, var strength: Double, var palette: Int, var at: Pos, var until: Long)

    private val lights = HashMap<Int, Light>()
    private var glowUntil = -1L
    private var tick = 0L
    private val on = HashSet<Player>()
    private val source = "mage@${owner.uuid}"

    /** Keep light [slot] at [at] for the next [ticks] ticks. */
    fun show(slot: Int, at: Pos, radius: Int, strength: Double, palette: Int, ticks: Int = 2) {
        val l = lights.getOrPut(slot) { Light(null, radius, strength, palette, at, 0) }
        if (l.light != null && (l.radius != radius || l.strength != strength || l.palette != palette)) l.light!!.set(slot, radius, strength, palette)
        l.radius = radius; l.strength = strength; l.palette = palette; l.until = tick + ticks
        if (l.at != at) { l.at = at; l.light?.moveTo(at) }
        glow(ticks)
    }

    /** Keep the glow on for at least [ticks] more ticks. */
    fun glow(ticks: Int = GLOW_HOLD) { glowUntil = maxOf(glowUntil, tick + ticks + GLOW_HOLD) }

    fun tick() {
        tick++
        val instance = owner.instance
        for ((slot, l) in lights.entries.toList()) {
            if (tick > l.until || instance == null) { l.light?.remove(); lights.remove(slot); continue }
            if (l.light == null) l.light = SakuraLight(instance, l.at, slot, l.radius, l.strength, l.palette) { it in on }
        }
        val wanted = if (instance == null || tick > glowUntil) emptySet() else instance.players.filter {
            CoreCombatPresentation.packed(it) && it.position.distanceSquared(owner.position) < VIEW * VIEW }.toSet()
        val effects = buildList { if (lights.isNotEmpty()) add(SakuraLight.POST); add(GLOW) }
        for (p in wanted) PlayerPostEffects.set(p, source, effects)
        for (p in on - wanted) PlayerPostEffects.clear(p, source)
        val changed = on != wanted
        on.clear(); on += wanted
        // only those with the post effect on may see a probe (without it, it shows as a line of coloured pixels)
        if (changed) lights.values.forEach { it.light?.entity?.updateViewableRule { p -> p in on } }
    }

    fun clear() {
        lights.values.forEach { it.light?.remove() }; lights.clear()
        on.forEach { PlayerPostEffects.clear(it, source) }; on.clear(); glowUntil = -1
    }

    companion object {
        val GLOW: Key = Key.key("projects", "mage_glow")
        const val MOVING = 6
        const val BURST = 7
        const val GLOW_HOLD = 20
        const val VIEW = 48.0
    }
}
