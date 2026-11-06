package dev.projects.server.coreloop

import dev.projects.server.coreloop.ui.DialogCanvas
import net.kyori.adventure.key.Key
import net.kyori.adventure.nbt.BinaryTag
import net.kyori.adventure.nbt.api.BinaryTagHolder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.dialog.Dialog
import net.minestom.server.dialog.DialogAction
import net.minestom.server.dialog.DialogActionButton
import net.minestom.server.dialog.DialogAfterAction
import net.minestom.server.dialog.DialogBody
import net.minestom.server.dialog.DialogMetadata
import net.minestom.server.entity.Player
import kotlin.math.roundToInt

/**
 * ダンジョンの門 as a vanilla dialog over the whole screen, in Aion 2's dungeon-screen composition (藍 2026-11-06): the boss
 * fills the screen, the list down the left, the open dungeon's stars, name, difficulty and headed sections, 入る at the
 * bottom right. The client draws a dialog's title first and does not clip it, so the whole picture (scripts/
 * build_menu_dialog_assets.py, gate_screen) hangs from the title line, with the tier beside it; the body below only
 * holds the invisible hit areas (入る, and each reward's name on hover). Laid out for a 16:9 screen. One dungeon for now.
 */
internal class DungeonGateDialog(
    private val metrics: DialogCanvas.Metrics,
    private val host: CoreMenuHost,
    /** The GUI scale the player told us (PlayerDisplaySettings); null until they have. */
    private val guiScale: (Player) -> Int?,
    private val askSettings: (Player) -> Unit,
) {
    @Synchronized fun show(player: Player) {
        val account = host.account(player) ?: return
        if (!host.requireHub(player)) return
        val scale = guiScale(player) ?: return askSettings(player)
        player.showDialog(dialog(account, metrics.forScale(scale)))
    }

    @Synchronized fun click(player: Player, key: Key, @Suppress("UNUSED_PARAMETER") payload: BinaryTag?): Boolean {
        if (key.namespace() != NS) return false
        when (key.value()) {
            "close" -> player.closeDialog()
            "enter" -> {
                val account = host.account(player) ?: return true
                player.closeDialog()
                host.departDungeon(player, CoreDungeons.SAKURA, account.revision)
            }
        }
        return true
    }

    internal fun dialog(account: CoreAccount, size: DialogCanvas.Size): Dialog {
        val meta = DialogMetadata(title(account, size), Component.text("ダンジョンの門"), true, false, DialogAfterAction.NONE,
            listOf(DialogBody.PlainMessage(body(size), size.canvasWidth + 9)), emptyList())
        val closeW = (size.layout["closeW"].asInt * size.unit).roundToInt()
        val close = DialogActionButton(DialogCanvas(size, metrics.space, 0.0, 1, 6, 1.0).apply { sprite("close", -closeW / 2.0, 0.0) }.buildLine(),
            null, closeW, DialogAction.Custom(Key.key(NS, "close"), null))
        return Dialog.Notice(meta, close)
    }

    /** The screen and the tier, in GUI px from the screen's top left (the title starts titleX in). */
    private fun title(account: CoreAccount, size: DialogCanvas.Size): Component {
        val x0 = size.layout["titleX"].asDouble
        val tier = size.layout.getAsJsonArray("gateTier")
        return DialogCanvas(size, metrics.space, size.titleWidth.toDouble(), 1, size.titleTop, 1.0, metrics.forScale(4).styles).apply {
            background("gate_screen", -x0, 0.0)
            background("gate_tier${tier(account)}", tier[0].asDouble * size.unit - x0, tier[1].asDouble)
        }.buildLine()
    }

    /** Hit areas only, in screen design px less where the dialog puts the body (canvas x 8, gateTop down). */
    private fun body(size: DialogCanvas.Size): Component {
        val layout = size.layout
        val canvas = DialogCanvas(size, metrics.space, size.canvasWidth.toDouble(), layout["gateLines"].asInt)
        val left = (480 - size.canvasWidth / size.unit) / 2
        val top = layout["gateTop"].asDouble
        val names = layout.getAsJsonArray("gateRewardNames")
        layout.getAsJsonArray("gateRewards").forEachIndexed { i, spot ->
            val s = spot.asJsonArray
            canvas.sprite("hit_reward", s[0].asDouble - left, s[1].asDouble - top, hover = Component.text(names[i].asString, TEXT))
        }
        val enter = layout.getAsJsonArray("gateEnter")
        canvas.sprite("hit_enter", enter[0].asDouble - left, enter[1].asDouble - top, click("enter"), Component.text("散り桜の社へ入る", TEXT))
        return canvas.build()
    }

    /** The tier the dungeon is cut to: the player's level band, capped at the maps they have opened (as the gate did). */
    private fun tier(account: CoreAccount) = ((account.journey.level - 1) / 10 + 1).coerceIn(1, account.unlockedMapTier)

    // Minestom cannot write a custom click without a payload, so every one carries an empty compound.
    private fun click(action: String) = ClickEvent.custom(Key.key(NS, action), BinaryTagHolder.binaryTagHolder("{}"))

    companion object {
        const val NS = "projects_dungeon"
        private val TEXT = TextColor.color(0xECE6DC)
    }
}
