package dev.projects.server.coreloop

import net.kyori.adventure.key.Key
import net.kyori.adventure.nbt.BinaryTag
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.dialog.Dialog
import net.minestom.server.dialog.DialogAction
import net.minestom.server.dialog.DialogActionButton
import net.minestom.server.dialog.DialogAfterAction
import net.minestom.server.dialog.DialogBody
import net.minestom.server.dialog.DialogInput
import net.minestom.server.dialog.DialogMetadata
import net.minestom.server.entity.Player
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * What the client never tells the server: the player's GUI scale (the dialog menus are drawn for it) and FOV (the
 * camera screens are framed for 70). Asked once on the first join and kept per player, one small file each.
 */
internal class PlayerDisplaySettings(private val dir: Path) {
    data class Values(val guiScale: Int, val fov: Int) {
        init { require(guiScale in GUI_SCALES && fov in FOV_RANGE) }
    }

    private val cache = ConcurrentHashMap<UUID, Values>()

    fun get(player: UUID): Values? = cache[player] ?: read(player)?.also { cache[player] = it }

    fun put(player: UUID, values: Values) {
        Files.createDirectories(dir)
        val file = dir.resolve("$player.properties")
        val temporary = dir.resolve(".$player.${UUID.randomUUID()}.tmp")
        Files.writeString(temporary, "gui=${values.guiScale}\nfov=${values.fov}\n")
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        cache[player] = values
    }

    private fun read(player: UUID): Values? {
        val file = dir.resolve("$player.properties")
        if (!Files.isRegularFile(file)) return null
        val fields = Files.readAllLines(file).mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
            .associate { (k, v) -> k.trim() to v.trim() }
        return runCatching { Values(fields.getValue("gui").toInt(), fields.getValue("fov").toInt()) }
            .onFailure { System.err.println("DISPLAY_SETTINGS_IGNORED: $file (${it.message})") }.getOrNull()
    }

    companion object {
        val GUI_SCALES = 1..6
        val FOV_RANGE = 30..110
        const val DEFAULT_FOV = 70
    }
}

/** The form for [PlayerDisplaySettings]: required on the first join, reopened with /settings. Vanilla font only. */
internal class DisplaySettingsDialog(private val settings: PlayerDisplaySettings, private val saved: (Player, Boolean) -> Unit) {
    /** Players who opened the menu before telling us; it opens once they have. */
    private val thenMenu = ConcurrentHashMap.newKeySet<UUID>()

    fun show(player: Player, required: Boolean, openMenuAfter: Boolean = false) {
        if (openMenuAfter) thenMenu += player.uuid
        val current = settings.get(player.uuid)
        val body = listOf(
            line("最初に、画面の設定を2つ教えてください。", TEXT),
            line("メニューとカメラの画面を、あなたの画面に合わせて描くために使います。", MUTED),
            line("どちらも「設定 → ビデオ設定」で見られます。", MUTED),
            line("GUIの大きさが「自動」なら、1920×1080 の画面では 4 です。", MUTED),
        )
        val gui = DialogInput.SingleOption("gui", 200, PlayerDisplaySettings.GUI_SCALES.map { scale ->
            DialogInput.SingleOption.Option("$scale", Component.text("$scale"), scale == (current?.guiScale ?: 4))
        }, Component.text("GUIの大きさ"), true)
        val fov = DialogInput.NumberRange("fov", 200, Component.text("視野角（FOV）"), "options.generic_value",
            PlayerDisplaySettings.FOV_RANGE.first.toFloat(), PlayerDisplaySettings.FOV_RANGE.last.toFloat(),
            (current?.fov ?: PlayerDisplaySettings.DEFAULT_FOV).toFloat(), 1f)
        val meta = DialogMetadata(Component.text("画面の設定"), Component.text("画面の設定"), !required, false,
            DialogAfterAction.NONE, body, listOf(gui, fov))
        val save = DialogActionButton(Component.text("決定"), null, 150,
            DialogAction.DynamicCustom(Key.key(NS, "save"), CompoundBinaryTag.empty()))
        player.showDialog(Dialog.Notice(meta, save))
    }

    fun click(player: Player, key: Key, payload: BinaryTag?): Boolean {
        if (key.namespace() != NS) return false
        val values = (payload as? CompoundBinaryTag)?.let { tag ->
            runCatching { PlayerDisplaySettings.Values(tag.getString("gui").toInt(), tag.getFloat("fov").roundToInt()) }.getOrNull()
        }
        if (values == null) {
            player.sendMessage(Component.text("設定を読み取れませんでした。もう一度選んでください。", NamedTextColor.RED))
            show(player, required = settings.get(player.uuid) == null)
            return true
        }
        settings.put(player.uuid, values)
        player.closeDialog()
        player.sendMessage(Component.text("画面の設定を保存しました（GUIの大きさ ${values.guiScale}・FOV ${values.fov}）。/settings で変えられます。", MUTED))
        saved(player, thenMenu.remove(player.uuid))
        return true
    }

    fun forget(player: UUID) { thenMenu -= player }

    private fun line(text: String, color: TextColor) = DialogBody.PlainMessage(Component.text(text, color), 300)

    companion object {
        const val NS = "projects_settings"
        private val TEXT = TextColor.color(0xECE6DC)
        private val MUTED = TextColor.color(0x9A968F)
    }
}
