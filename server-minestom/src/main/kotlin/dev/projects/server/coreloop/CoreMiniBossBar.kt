package dev.projects.server.coreloop

import dev.projects.server.coreloop.ui.CoreBossBarHud
import dev.projects.server.coreloop.ui.CoreHudTrail
import dev.projects.server.mob.MobHud
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import kotlin.math.roundToInt

/**
 * One player's boss bar for a mini-boss (QuestMobRarity.MINIBOSS): up while a mini-boss of the fight stands, down
 * otherwise. With the pack its name draws the pixel boss bar with the ミニボス tag (on the pink bar the pack blanks);
 * without it, the red vanilla bar and a plain name. Instance dungeon runs and the boss lab (/minibosstest) use it.
 */
internal class CoreMiniBossBar(private val player: Player) {
    private val bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS)
    private val trail = CoreHudTrail()
    private var shown = false

    /** [hud]: QuestEncounterCombat.miniBossHud() (null = no mini-boss standing). */
    fun update(hud: MobHud?, packed: Boolean, now: Long) {
        if (hud == null) { hide(); return }
        bar.progress((hud.health / hud.maximumHealth).toFloat().takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f)
        if (packed) {
            bar.color(BossBar.Color.PINK)
            bar.name(CoreBossBarHud.render(hud, trail.update(hud.health, hud.maximumHealth, now)))
        } else {
            bar.color(BossBar.Color.RED)
            bar.name(CoreLoopItems.text("${CoreBossBarHud.MINI_TAG}  ${hud.name}  ${hud.health.roundToInt()} / ${hud.maximumHealth.roundToInt()}",
                NamedTextColor.LIGHT_PURPLE))
        }
        if (!shown) { player.showBossBar(bar); shown = true }
    }

    fun hide() {
        if (shown) { player.hideBossBar(bar); shown = false }
    }
}
