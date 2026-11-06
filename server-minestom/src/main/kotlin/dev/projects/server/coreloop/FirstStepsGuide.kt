package dev.projects.server.coreloop

import net.minestom.server.entity.Player
import net.minestom.server.scoreboard.Sidebar
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import java.util.UUID

/**
 * The first steps after choosing a class (導入 0/6, CoreJourneyRules.next): the step on the right of the screen, as
 * RuneForge keeps its quest under the logo, until all six are known. Hidden while a full-screen view is open.
 */
internal class FirstStepsGuide {
    private class Shown(val sidebar: Sidebar, var step: String)
    private val shown = mutableMapOf<UUID, Shown>()

    /** Called with the HUD; [account] null or [visible] false hides it. */
    fun update(player: Player, account: CoreAccount?, visible: Boolean, packed: Boolean) {
        val done = account?.let { a -> (0 until STEPS).count(a.journey::knows) } ?: STEPS
        if (!visible || account == null || !account.journey.chosen || done >= STEPS) { hide(player); return }
        val step = CoreJourneyRules.next(account)
        val title = word("導入 $done/$STEPS", GOLD, "b8", packed)
        val current = shown[player.uuid]
        if (current == null) {
            val sidebar = Sidebar(title)
            sidebar.createLine(Sidebar.ScoreboardLine(LINE, word(step, INK, "m6", packed), 0, Sidebar.NumberFormat.blank()))
            sidebar.addViewer(player)
            shown[player.uuid] = Shown(sidebar, step)
        } else if (current.step != step) {
            current.sidebar.setTitle(title)
            current.sidebar.updateLineContent(LINE, word(step, INK, "m6", packed))
            current.step = step
        }
    }

    fun hide(player: Player) {
        shown.remove(player.uuid)?.sidebar?.removeViewer(player)
    }

    private companion object {
        const val STEPS = 6
        const val LINE = "step"
        val INK = TextColor.color(0xECE6DC)
        val GOLD = TextColor.color(0xF2C84B)

        fun word(text: String, color: TextColor, style: String, packed: Boolean): Component =
            if (packed) Component.text(text, color).font(Key.key("projects_ui_polish05", "menu4_${style}_0")) else Component.text(text, color)
    }
}
