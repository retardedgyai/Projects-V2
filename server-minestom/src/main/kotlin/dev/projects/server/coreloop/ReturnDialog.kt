package dev.projects.server.coreloop

import net.kyori.adventure.key.Key
import net.kyori.adventure.nbt.BinaryTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.dialog.Dialog
import net.minestom.server.dialog.DialogAction
import net.minestom.server.dialog.DialogActionButton
import net.minestom.server.dialog.DialogAfterAction
import net.minestom.server.dialog.DialogBody
import net.minestom.server.dialog.DialogMetadata
import net.minestom.server.entity.Player

/**
 * The 手帳 out on a run or in a dungeon: the dialog book has no way home, and the way home is the one thing a player
 * needs there (after 桜牙獣, 「手帳から港へ帰還」). A small dialog in the party dialog's dress: where you are, its boss,
 * and 帰還 / 手帳 / やめる. Names and numbers only.
 */
internal class ReturnDialog(private val goHome: (Player) -> Unit, private val openBook: (Player) -> Unit) {
    /** What the dialog shows: the place, its boss (null when it has none) and whether it fell. */
    data class Place(val name: String, val boss: String?, val defeated: Boolean)

    fun show(player: Player, place: Place) {
        val body = buildList {
            add(line(t(place.name, TEXT, bold = true)))
            if (place.boss != null) add(line(Component.text().append(t("${place.boss}  ", MUTED),
                if (place.defeated) t("討伐済み", READY, bold = true) else t("未討伐", FAINT)).build()))
            add(line(t("このマップには戻れません", FAINT)))
        }
        val meta = DialogMetadata(t("港へ帰還", GOLD, bold = true), t("港へ帰還"), true, false, DialogAfterAction.WAIT_FOR_RESPONSE, body, emptyList())
        val buttons = listOf(button("帰還する", "home", GOLD_LIGHT, bold = true), button("手帳", "book", TEXT))
        player.showDialog(Dialog.MultiAction(meta, buttons, button("やめる", "close", MUTED), 2))
    }

    /** Clicks from [show]; false when the click is not this dialog's. */
    fun click(player: Player, key: Key, @Suppress("UNUSED_PARAMETER") payload: BinaryTag?): Boolean {
        if (key.namespace() != NS) return false
        player.closeDialog()
        when (key.value()) {
            "home" -> goHome(player)
            "book" -> openBook(player)
        }
        return true
    }

    private fun line(text: Component) = DialogBody.PlainMessage(text, WIDTH)
    private fun t(text: String, color: TextColor = TEXT, bold: Boolean = false): Component =
        Component.text(text, color).font(if (bold) FONT_BOLD else FONT)
    private fun button(label: String, action: String, color: TextColor, bold: Boolean = false) =
        DialogActionButton(t(label, color, bold), null, 150, DialogAction.Custom(Key.key(NS, action), null))

    private companion object {
        const val NS = "projects_return"
        const val WIDTH = 260
        val FONT = Key.key("projects_ui_polish05", "dialog")
        val FONT_BOLD = Key.key("projects_ui_polish05", "dialog_b")
        val GOLD = TextColor.color(0xE8C878)
        val GOLD_LIGHT = TextColor.color(0xF6DC94)
        val TEXT = TextColor.color(0xECE6DC)
        val MUTED = TextColor.color(0x9A968F)
        val FAINT = TextColor.color(0x5E5A54)
        val READY = TextColor.color(0x8EE09A)
    }
}
