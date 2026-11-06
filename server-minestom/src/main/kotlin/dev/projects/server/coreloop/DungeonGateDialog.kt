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
 * ダンジョンの門 as a vanilla dialog, in Aion 2's dungeon-screen composition (藍 2026-11-06): the boss over the whole
 * page, the list down the left, the open dungeon's stars, name, difficulty and headed sections, 入る at the bottom right.
 * Everything that never changes is the baked page_dungeon (scripts/build_menu_dialog_assets.py); the tier, the reward
 * names on hover and 入る's hit area are placed here. One dungeon for now (散り桜の社).
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

    private fun dialog(account: CoreAccount, size: DialogCanvas.Size): Dialog {
        val meta = DialogMetadata(title(size), Component.text("ダンジョンの門"), true, false, DialogAfterAction.NONE,
            listOf(DialogBody.PlainMessage(body(account, size), size.canvasWidth + 9)), emptyList())
        val closeW = (size.layout["closeW"].asInt * size.unit).roundToInt()
        val close = DialogActionButton(DialogCanvas(size, metrics.space, 0.0, 1, 6, 1.0).apply { sprite("close", -closeW / 2.0, 0.0) }.buildLine(),
            null, closeW, DialogAction.Custom(Key.key(NS, "close"), null))
        return Dialog.Notice(meta, close)
    }

    private fun title(size: DialogCanvas.Size): Component =
        DialogCanvas(size, metrics.space, size.titleWidth.toDouble(), 1, size.titleTop, 1.0, metrics.forScale(4).styles).buildLine()

    private fun body(account: CoreAccount, size: DialogCanvas.Size): Component {
        val canvas = DialogCanvas(size, metrics.space, size.canvasWidth.toDouble(), size.lines)
        val layout = size.layout
        canvas.background("page_dungeon", 0.0, 0.0)
        val tier = layout.getAsJsonArray("dgTier")
        canvas.text("m6", tier[0].asDouble, tier[1].asDouble, "T${tier(account)}", TEXT)
        val names = layout.getAsJsonArray("dgRewardNames")
        layout.getAsJsonArray("dgRewards").forEachIndexed { i, spot ->
            val s = spot.asJsonArray
            canvas.sprite("hit_reward", s[0].asDouble, s[1].asDouble, hover = Component.text(names[i].asString, TEXT))
        }
        val enter = layout.getAsJsonArray("dgEnter")
        canvas.sprite("hit_enter", enter[0].asDouble, enter[1].asDouble, click("enter"), Component.text("散り桜の社へ入る", TEXT))
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
