package dev.projects.server.coreloop

import net.kyori.adventure.key.Key
import net.kyori.adventure.nbt.BinaryTag
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.nbt.StringBinaryTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.`object`.ObjectContents
import net.kyori.adventure.text.`object`.PlayerHeadObjectContents
import net.minestom.server.dialog.Dialog
import net.minestom.server.dialog.DialogAction
import net.minestom.server.dialog.DialogActionButton
import net.minestom.server.dialog.DialogAfterAction
import net.minestom.server.dialog.DialogBody
import net.minestom.server.dialog.DialogInput
import net.minestom.server.dialog.DialogMetadata
import net.minestom.server.entity.Player
import java.util.UUID

/**
 * パーティ画面をマイクラ標準のダイアログで出す試作。編成（招待・参加・準備・はずす・抜ける）だけを扱い、
 * 一緒に出発する仕組みはまだ無い。ダイアログは開いている人にだけ、変化のたびに送り直す。
 */
internal class CorePartyDialogs(private val online: (UUID) -> Player?, private val byName: (String) -> Player?) {
    private data class Party(val leader: UUID, val members: List<UUID>, val ready: Set<UUID>)

    private val parties = mutableListOf<Party>()
    /** invitee -> leaders who invited them, newest last. */
    private val invites = mutableMapOf<UUID, MutableList<UUID>>()
    /** Players looking at the party dialog right now (ESC is off, so only our close button removes them). */
    private val open = mutableSetOf<UUID>()
    private val notices = mutableMapOf<UUID, Component>()
    /** Stand-in members for trying the screen alone: uuid -> name. */
    private val mates = mutableMapOf<UUID, String>()

    @Synchronized fun show(player: Player) {
        open += player.uuid
        player.showDialog(dialog(player))
    }

    @Synchronized fun click(player: Player, key: Key, payload: BinaryTag?): Boolean {
        if (key.namespace() != NS) return false
        val arg = (payload as? StringBinaryTag)?.value() ?: (payload as? CompoundBinaryTag)?.getString("arg") ?: ""
        val before = of(player.uuid)?.members.orEmpty()
        try {
            when (key.value()) {
                "close" -> { open -= player.uuid; notices -= player.uuid; player.closeDialog(); return true }
                "create" -> create(player.uuid)
                "invite" -> invite(player, ((payload as? CompoundBinaryTag)?.getString("name") ?: "").trim())
                "join" -> join(player.uuid, UUID.fromString(arg))
                "decline" -> invites.remove(player.uuid)
                "ready" -> toggleReady(player.uuid)
                "kick" -> kick(player.uuid, UUID.fromString(arg))
                "leave" -> leave(player.uuid)
                "mate" -> addMate(player.uuid)
                else -> return true
            }
        } catch (e: IllegalStateException) {
            notices[player.uuid] = Component.text(e.message ?: "できませんでした", NamedTextColor.RED)
        }
        refresh(player.uuid, before)
        return true
    }

    /** /party join <leader> from the chat invite. */
    @Synchronized fun joinByName(player: Player, leaderName: String) {
        val leader = byName(leaderName)?.uuid
        try {
            check(leader != null && invites[player.uuid]?.contains(leader) == true) { "その招待は見つかりません" }
            join(player.uuid, leader!!)
        } catch (e: IllegalStateException) { notices[player.uuid] = Component.text(e.message ?: "", NamedTextColor.RED) }
        show(player); refresh(player.uuid)
    }

    @Synchronized fun disconnect(player: UUID) {
        open -= player; notices -= player; invites -= player
        invites.values.forEach { it -= player }
        if (of(player) != null) runCatching { leave(player) }
    }

    // ------------------------------------------------------------------ party rules

    private fun of(id: UUID) = parties.firstOrNull { id in it.members }
    private fun replace(old: Party, new: Party?) { parties.remove(old); if (new != null) parties += new }

    private fun create(id: UUID) {
        check(of(id) == null) { "すでにパーティに入っています" }
        parties += Party(id, listOf(id), emptySet())
    }

    private fun invite(from: Player, name: String) {
        check(name.isNotEmpty()) { "招待する人の名前を入れてください" }
        if (of(from.uuid) == null) create(from.uuid)
        val p = of(from.uuid)!!
        check(p.leader == from.uuid) { "招待できるのはリーダーだけです" }
        check(p.members.size < MAX) { "パーティは${MAX}人までです" }
        val target = byName(name) ?: error("「$name」はオンラインにいません")
        check(target.uuid != from.uuid) { "自分は招待できません" }
        check(of(target.uuid) == null) { "${target.username} はほかのパーティにいます" }
        invites.getOrPut(target.uuid) { mutableListOf() }.apply { remove(from.uuid); add(from.uuid) }
        notices[from.uuid] = Component.text("${target.username} に招待を送りました", NamedTextColor.GREEN)
        target.sendMessage(Component.text()
            .append(Component.text("◆ ", GOLD)).append(Component.text(from.username, NamedTextColor.WHITE))
            .append(Component.text(" からパーティの招待  ", NamedTextColor.GRAY))
            .append(Component.text("[参加する]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/party join ${from.username}"))
                .hoverEvent(HoverEvent.showText(Component.text("${from.username} のパーティに入る"))))
            .append(Component.text("  "))
            .append(Component.text("[パーティ画面]", GOLD).clickEvent(ClickEvent.runCommand("/party")))
            .build())
        if (target.uuid in open) target.showDialog(dialog(target))
    }

    private fun join(id: UUID, leader: UUID) {
        check(of(id) == null) { "先に今のパーティを抜けてください" }
        val p = of(leader)?.takeIf { it.leader == leader } ?: error("その募集は終わりました")
        check(p.members.size < MAX) { "満員です" }
        invites[id]?.remove(leader)
        replace(p, p.copy(members = p.members + id, ready = emptySet()))
        p.members.forEach { notices[it] = Component.text("${name(id)} が参加しました", NamedTextColor.GREEN) }
    }

    private fun toggleReady(id: UUID) {
        val p = of(id) ?: error("パーティに入っていません")
        replace(p, p.copy(ready = if (id in p.ready) p.ready - id else p.ready + id))
    }

    private fun kick(leader: UUID, target: UUID) {
        val p = of(leader) ?: error("パーティに入っていません")
        check(p.leader == leader) { "はずせるのはリーダーだけです" }
        check(target in p.members && target != leader)
        replace(p, p.copy(members = p.members - target, ready = p.ready - target))
        mates -= target
        if (target in open) online(target)?.let { notices[target] = Component.text("パーティからはずされました", NamedTextColor.RED) }
        refresh(target)
    }

    private fun leave(id: UUID) {
        val p = of(id) ?: return
        val remaining = p.members - id
        val humans = remaining.filter { it !in mates }
        if (humans.isEmpty()) { mates.keys.removeAll(remaining.toSet()); replace(p, null); return }
        replace(p, p.copy(leader = if (p.leader == id) humans.first() else p.leader, members = remaining, ready = p.ready - id))
        humans.forEach { notices[it] = Component.text("${name(id)} が抜けました", NamedTextColor.YELLOW) }
    }

    private fun addMate(id: UUID) {
        if (of(id) == null) create(id)
        val p = of(id)!!
        check(p.members.size < MAX) { "パーティは${MAX}人までです" }
        val mate = UUID.randomUUID().also { mates[it] = MATE_NAMES[mates.size % MATE_NAMES.size] }
        replace(p, p.copy(members = p.members + mate, ready = p.ready + mate))
    }

    /** Re-send the dialog to everyone in the changed party who has it open. */
    private fun refresh(changed: UUID, before: List<UUID> = emptyList()) {
        val ids = before + (of(changed)?.members ?: emptyList()) + changed
        ids.distinct().filter { it in open }.forEach { id -> online(id)?.let { it.showDialog(dialog(it)) } }
    }

    private fun name(id: UUID) = mates[id] ?: online(id)?.username ?: "（不在）"

    // ------------------------------------------------------------------ dialog

    private fun dialog(player: Player): Dialog {
        val p = of(player.uuid)
        val body = mutableListOf<DialogBody>()
        fun line(text: Component) { body += DialogBody.PlainMessage(text, WIDTH) }
        notices.remove(player.uuid)?.let { line(it.font(FONT)) }
        val buttons = mutableListOf<DialogActionButton>()
        val inputs = mutableListOf<DialogInput>()
        if (p == null) {
            line(t("まだパーティに入っていません", TEXT, bold = true))
            line(t("作って仲間を招待するか、届いた招待に参加しましょう。", MUTED))
            val pending = invites[player.uuid].orEmpty().mapNotNull { leader -> of(leader)?.takeIf { it.leader == leader } }
            if (pending.isNotEmpty()) {
                line(DIVIDER)
                line(t("届いている招待", GOLD, bold = true))
                pending.forEach { party ->
                    line(Component.text().append(head(party.leader)).append(t("  ${name(party.leader)}", TEXT, bold = true))
                        .append(t("  のパーティ · ${party.members.size}/$MAX", MUTED)).build())
                    buttons += button("${name(party.leader)} に参加", "join", party.leader.toString(), primary = true)
                }
                buttons += button("招待をすべて断る", "decline")
            }
            buttons += button("パーティを作る", "create", primary = true)
        } else {
            val leader = p.leader == player.uuid
            line(Component.text().append(t("メンバー ", MUTED)).append(t("${p.members.size}/$MAX", TEXT, bold = true))
                .append(t("　　準備OK ", MUTED)).append(t("${p.ready.size}/${p.members.size}", if (p.ready.size == p.members.size) READY else TEXT, bold = true))
                .build())
            line(DIVIDER)
            p.members.forEach { id ->
                val row = Component.text().append(head(id)).append(Component.text("  "))
                if (id == p.leader) row.append(t("★ ", GOLD))
                row.append(t(name(id), if (id == player.uuid) GOLD_LIGHT else TEXT, bold = true))
                if (id in mates) row.append(t(" 試し", FAINT))
                row.append(t("　　"))
                row.append(if (id in p.ready) t("✔ 準備OK", READY, bold = true) else t("準備中…", MUTED))
                line(row.build())
            }
            repeat(MAX - p.members.size) { line(Component.text().append(Component.text("\uE001").font(ART)).append(t("  空き", FAINT)).build()) }
            line(DIVIDER)
            line(t("全員が準備OKになったら、リーダーが地図から出発します。", MUTED))
            line(t("（一緒に出発は次の段階で実装）", FAINT))
            if (leader && p.members.size < MAX) {
                inputs += DialogInput.Text("name", 200, t("招待するプレイヤー名", MUTED), true, "", 16, null)
                buttons += DialogActionButton(t("招待を送る", READY, bold = true), t("入力した名前の人に招待を送る"), 150,
                    DialogAction.DynamicCustom(Key.key(NS, "invite"), CompoundBinaryTag.empty()))
            }
            val ready = player.uuid in p.ready
            buttons += button(if (ready) "準備を取り消す" else "準備OK", "ready", primary = !ready)
            if (leader) p.members.filter { it != player.uuid }.forEach { buttons += button("${name(it)} をはずす", "kick", it.toString()) }
            if (leader && p.members.size < MAX) buttons += button("試しの仲間を追加", "mate")
            buttons += button("パーティを抜ける", "leave", danger = true)
        }
        val meta = DialogMetadata(t("パーティ", GOLD, bold = true), t("パーティ"), false, false,
            DialogAfterAction.WAIT_FOR_RESPONSE, body, inputs)
        val exit = DialogActionButton(t("閉じる", MUTED), null, 150, DialogAction.Custom(Key.key(NS, "close"), null))
        return Dialog.MultiAction(meta, buttons, exit, 2)
    }

    private fun t(text: String, color: TextColor = TEXT, bold: Boolean = false): Component =
        Component.text(text, color).font(if (bold) FONT_BOLD else FONT)

    private fun button(label: String, action: String, arg: String? = null, primary: Boolean = false, danger: Boolean = false) =
        DialogActionButton(t(label, when { primary -> GOLD_LIGHT; danger -> DANGER; else -> TEXT }, bold = primary), null, 150,
            DialogAction.Custom(Key.key(NS, action), arg?.let { StringBinaryTag.stringBinaryTag(it) }))

    /** The member's face drawn inline in the text line (vanilla player-head object); stand-ins get the default face. */
    private fun head(id: UUID): Component {
        val builder = ObjectContents.playerHead().name(name(id).take(16)).hat(true)
        online(id)?.let { player ->
            builder.id(player.uuid)
            player.skin?.let { builder.profileProperty(PlayerHeadObjectContents.property("textures", it.textures(), it.signature())) }
        }
        return Component.`object`(builder.build())
    }

    companion object {
        const val NS = "projects_party"
        private const val MAX = 4
        private const val WIDTH = 260
        private val FONT = Key.key("projects_ui_polish05", "dialog")
        private val FONT_BOLD = Key.key("projects_ui_polish05", "dialog_b")
        private val ART = Key.key("projects_ui_polish05", "dialog_art")
        private val DIVIDER: Component = Component.text("\uE000").font(ART)
        private val GOLD = TextColor.color(0xE8C878)
        private val GOLD_LIGHT = TextColor.color(0xF6DC94)
        private val TEXT = TextColor.color(0xECE6DC)
        private val MUTED = TextColor.color(0x9A968F)
        private val FAINT = TextColor.color(0x5E5A54)
        private val READY = TextColor.color(0x8EE09A)
        private val DANGER = TextColor.color(0xF2A090)
        private val MATE_NAMES = listOf("練習相手A", "練習相手B", "練習相手C")
    }
}
