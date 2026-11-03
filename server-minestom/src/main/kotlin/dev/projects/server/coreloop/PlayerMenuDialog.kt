package dev.projects.server.coreloop

import com.google.gson.JsonObject
import dev.projects.server.coreloop.ui.DialogCanvas
import dev.projects.server.coreloop.ui.DialogCanvas.Align
import dev.projects.server.questmap.QuestGatheringDiscipline
import net.kyori.adventure.key.Key
import net.kyori.adventure.nbt.BinaryTag
import net.kyori.adventure.nbt.api.BinaryTagHolder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.`object`.ObjectContents
import net.kyori.adventure.text.`object`.PlayerHeadObjectContents
import net.minestom.server.dialog.Dialog
import net.minestom.server.dialog.DialogAction
import net.minestom.server.dialog.DialogActionButton
import net.minestom.server.dialog.DialogAfterAction
import net.minestom.server.dialog.DialogBody
import net.minestom.server.dialog.DialogMetadata
import net.minestom.server.entity.Player
import java.util.UUID
import kotlin.math.roundToInt

/**
 * The player menu (冒険の手帳) drawn inside a vanilla dialog: the 自分 page, a name card with the four paths
 * (戦い・採取・鍛冶・魔術). The other tabs still open the existing screens. Picture: design/log.md 2026-11-02 and the
 * 「プレイヤーメニュー ダイアログ案」 page; look and positions: scripts/build_menu_dialog_assets.py.
 */
internal class PlayerMenuDialog(
    private val metrics: DialogCanvas.Metrics,
    private val host: CoreMenuHost,
    private val open: (Player, Target) -> Unit,
    /** The GUI scale the player told us (PlayerDisplaySettings); null until they have. */
    private val guiScale: (Player) -> Int?,
    /** Asks for the GUI scale first; the settings dialog reopens the menu when it is saved. */
    private val askSettings: (Player) -> Unit,
) {
    enum class Target { SKILLS, PASSIVES, STORAGE, JOURNAL, ATLAS, MASTERY, WORKSHOP, GRIMOIRE }

    private enum class Path(val id: String, val label: String, val target: Target, val button: String) {
        COMBAT("combat", "戦い", Target.ATLAS, "地図台を開く"),
        GATHER("gather", "採取", Target.MASTERY, "採取の育成を開く"),
        SMITH("smith", "鍛冶", Target.WORKSHOP, "工房を開く"),
        MAGIC("magic", "魔術", Target.GRIMOIRE, "魔術書を開く"),
    }

    /** What one path shows: the short line beside its name, the number, how far to the next step (0..1). */
    private class Progress(val sub: String, val value: String, val short: String, val next: Double)

    private val selected = mutableMapOf<UUID, Path>()
    private val notices = mutableMapOf<UUID, String>()
    private enum class Page { SELF, SKILLS }
    private val pages = mutableMapOf<UUID, Page>()
    /** 技 page: the key being changed (0..3 normal skills, 4 the ultimate) and the candidate looked at, if any. */
    private class SkillPick(var slot: Int = 0, var candidate: Int? = null)
    private val picks = mutableMapOf<UUID, SkillPick>()

    @Synchronized fun show(player: Player) {
        val account = host.account(player) ?: return
        val scale = guiScale(player) ?: return askSettings(player)
        player.showDialog(dialog(player, account, metrics.forScale(scale)))
    }

    @Synchronized fun click(player: Player, key: Key, @Suppress("UNUSED_PARAMETER") payload: BinaryTag?): Boolean {
        if (key.namespace() != NS) return false
        val action = key.value()
        when {
            action == "close" -> { player.closeDialog(); notices -= player.uuid }
            action.startsWith("path_") -> { selected[player.uuid] = Path.entries[action.removePrefix("path_").toInt()]; show(player) }
            action == "edit" -> { notices[player.uuid] = "名刺を整える画面は準備中です"; show(player) }
            action == "primary" -> leave(player, (selected[player.uuid] ?: Path.COMBAT).target)
            action == "tab_0" -> { pages[player.uuid] = Page.SELF; show(player) }
            action == "tab_1" -> { pages[player.uuid] = Page.SKILLS; show(player) }
            action.startsWith("tab_") -> leave(player, TAB_TARGETS[action.removePrefix("tab_").toInt()])
            action.startsWith("key_") -> { picks.getOrPut(player.uuid, ::SkillPick).apply { slot = action.removePrefix("key_").toInt(); candidate = null }; show(player) }
            action.startsWith("cell_") -> { picks.getOrPut(player.uuid, ::SkillPick).candidate = action.removePrefix("cell_").toInt(); show(player) }
            action == "equip" -> skillAction(player) { account, pick ->
                val choice = pick.candidate ?: return@skillAction null
                CoreAction.SelectSkill(pick.slot, if (pick.slot == 4) choice - CoreSkillCatalog.ultimateStart(account.journey.job) else choice)
            }
            action == "raise" -> skillAction(player) { account, pick -> CoreAction.RaiseSkill(chosen(account, pick).icon) }
            action == "reset" -> skillAction(player) { _, _ -> CoreAction.ResetSkillLevels }
        }
        return true
    }

    /** Sends a 技 page change to the server (which decides), then redraws with the result. */
    private fun skillAction(player: Player, action: (CoreAccount, SkillPick) -> CoreAction?) {
        val account = host.account(player) ?: return
        val pick = picks.getOrPut(player.uuid, ::SkillPick)
        val request = action(account, pick) ?: return
        host.mutate(player, request, account.revision, { show(player) }) {
            if (request is CoreAction.SelectSkill) pick.candidate = null
            show(player)
        }
    }

    fun forget(player: UUID) { selected -= player; notices -= player; pages -= player; picks -= player }

    private fun leave(player: Player, target: Target) {
        player.closeDialog()
        open(player, target)
    }

    // ------------------------------------------------------------------ drawing (design px: the layout for GUI scale 4)

    private fun dialog(player: Player, account: CoreAccount, size: DialogCanvas.Size): Dialog {
        val meta = DialogMetadata(title(account, size), Component.text("冒険の手帳"), true, false, DialogAfterAction.NONE,
            // The width includes the text widget's 4 px padding on each side, so lines need canvas + 8 or they wrap; but a
            // body wider than the screen (474 at GUI scale 4) is pushed to its left edge instead of centred (2026-11-02, in game).
            listOf(DialogBody.PlainMessage(body(player, account, size), size.canvasWidth + 9)), emptyList())
        val close = DialogActionButton(Component.text("閉じる", MUTED).font(DIALOG_FONT), null, 80,
            DialogAction.Custom(Key.key(NS, "close"), null))
        return Dialog.Notice(meta, close)
    }

    /** The title band, in GUI px from the screen's top-left: the header is 33 GUI px at every scale. */
    private fun title(account: CoreAccount, size: DialogCanvas.Size): Component {
        val canvas = DialogCanvas(size, metrics.space, size.titleWidth.toDouble(), 1, size.titleTop, 1.0, metrics.forScale(4).styles)
        val x0 = size.titleX.toDouble()
        canvas.background("band", -x0, 0.0)
        val wallet = size.layout.getAsJsonArray("wallet")
        canvas.text("d9", wallet[0].asDouble - x0, wallet[1].asDouble, "%,d".format(account.silver), TEXT, Align.RIGHT)
        return canvas.buildLine()
    }

    private fun body(player: Player, account: CoreAccount, size: DialogCanvas.Size): Component {
        if (pages[player.uuid] == Page.SKILLS) return skills(player, account, size)
        val canvas = DialogCanvas(size, metrics.space, size.canvasWidth.toDouble(), size.lines)
        val layout = size.layout
        canvas.background("page_self", 0.0, 0.0)
        val tabs = layout.getAsJsonArray("tabs")
        for (i in 1 until tabs.size()) {
            val box = tabs[i].asJsonArray
            canvas.sprite("hit_tab$i", box[0].asDouble, box[1].asDouble, click("tab_$i"))
        }
        val path = selected[player.uuid] ?: Path.COMBAT
        val progress = Path.entries.associateWith { progress(player, account, it) }
        paths(canvas, layout, path, progress)
        card(canvas, layout, player, account, path, progress)
        panel(canvas, layout, player, account, path)
        return canvas.build()
    }

    private fun paths(canvas: DialogCanvas, layout: JsonObject, chosen: Path, progress: Map<Path, Progress>) {
        val left = layout["left"].asDouble
        val barDx = layout["barDx"].asDouble
        val barDy = layout["barDy"].asDouble
        val barW = layout["barW"].asInt
        for (path in Path.entries) {
            val top = layout["rowY"].asDouble + path.ordinal * layout["rowStep"].asDouble
            val p = progress.getValue(path)
            if (path == chosen) canvas.sprite("row_sel", left, top)
            val nameW = canvas.width("b7", path.label)
            canvas.text("m6", left + 28 + nameW + 5, top + 11, p.sub, MUTED)
            canvas.text("d7", left + 132, top + 11, p.value, if (path == chosen) IVORY else TEXT, Align.RIGHT)
            repeat((p.next * barW / 2).roundToInt().coerceIn(0, barW / 2)) { canvas.sprite("bar_${path.id}", left + barDx + it * 2, top + barDy) }
            canvas.sprite("hit_row", left, top, click("path_${path.ordinal}"))
        }
    }

    private fun card(canvas: DialogCanvas, layout: JsonObject, player: Player, account: CoreAccount, chosen: Path, progress: Map<Path, Progress>) {
        val box = layout.getAsJsonArray("card")
        val cx = box[0].asDouble + box[2].asDouble / 2
        val emblem = layout.getAsJsonArray("emblem")
        canvas.sprite("emblem_${account.journey.job.name.lowercase()}", emblem[0].asDouble, emblem[1].asDouble)
        // Face and name: the face is the vanilla player-head object, 8 GUI px wide on its line at every scale.
        val name = player.username
        val face = canvas.design(8.0)
        val nameW = canvas.width("b14", name)
        val start = cx - (face + 4 + nameW) / 2
        canvas.inline(start, 80.0, 8.0, head(player))
        canvas.text("b14", start + face + 4, 84.0, name, TEXT)
        canvas.text("m7", cx, 97.0, "称号はまだありません", FAINT, Align.CENTER)
        val job = account.journey.job.displayName + " "
        val lv = "Lv ${account.journey.level}"
        val jobW = canvas.width("b9", job) + canvas.width("d9", lv)
        canvas.text("b9", cx - jobW / 2, 111.0, job, TEXT)
        canvas.text("d9", cx - jobW / 2 + canvas.width("b9", job), 111.0, lv, TEXT)
        val ring = layout["ring"].asDouble
        val gap = layout["ringGap"].asDouble
        val ringY = layout["ringY"].asDouble
        val first = cx - (4 * ring + 3 * gap) / 2
        for (path in Path.entries) {
            val x = first + path.ordinal * (ring + gap)
            val p = progress.getValue(path)
            if (path == chosen) canvas.sprite("ring_sel", x - 2, ringY - 2)
            canvas.sprite("ring_${path.id}_${(p.next * 10).roundToInt().coerceIn(0, 10)}", x, ringY, click("path_${path.ordinal}"),
                Component.text("${path.label}　${p.value}", TEXT))
            canvas.text("d6", x + ring / 2, ringY + ring + 9, p.short, if (path == chosen) IVORY else SOFT, Align.CENTER)
        }
        val clears = account.zoneRecords.values.sumOf { it.clears }
        canvas.text("m6", cx, 160.0, "踏破 $clears　開放 T${account.unlockedMapTier}", MUTED, Align.CENTER)
        val edit = layout.getAsJsonArray("edit")
        canvas.sprite("hit_edit", edit[0].asDouble, edit[1].asDouble, click("edit"))
    }

    private fun panel(canvas: DialogCanvas, layout: JsonObject, player: Player, account: CoreAccount, path: Path) {
        val box = layout.getAsJsonArray("panel")
        val x = box[0].asDouble + 7
        val width = box[2].asDouble - 14
        canvas.text("m6", x, 32.0, "${path.label}の次の一歩", MUTED)
        var baseline = 46.0
        for ((text, color) in details(player, account, path)) {
            for (line in wrap(canvas, text, width)) {
                if (baseline > 150) break
                canvas.text("m7", x, baseline, line, color)
                baseline += 11
            }
        }
        notices[player.uuid]?.let { canvas.text("m6", x, 160.0, it, HEAT) }
        val primary = layout.getAsJsonArray("primary")
        canvas.text("b7", primary[0].asDouble + primary[2].asDouble / 2, primary[1].asDouble + 11, path.button, DARK, Align.CENTER)
        canvas.sprite("hit_primary", primary[0].asDouble, primary[1].asDouble, click("primary"))
    }

    private fun details(player: Player, account: CoreAccount, path: Path): List<Pair<String, TextColor>> = when (path) {
        Path.COMBAT -> {
            val level = account.journey.level
            buildList {
                add("冒険 Lv $level / ${CoreJourneyRules.MAX_LEVEL}" to TEXT)
                if (level < CoreJourneyRules.MAX_LEVEL) add("次の Lv まで ${"%,d".format(CoreJourneyRules.threshold(level + 1) - account.journey.xp)}" to MUTED)
                add(CoreJourneyRules.next(account) to TEXT)
            }
        }
        Path.GATHER -> {
            val mastery = host.gatheringMastery(player)
            QuestGatheringDiscipline.entries.map { "${it.displayName}　Lv ${mastery.level(it)}" to TEXT } + ("道具で採るたびに上がります" to MUTED)
        }
        Path.SMITH -> {
            val rank = CoreEnhancementCatalog.masteryRank(account.smithingXp)
            val max = (CoreEnhancementCatalog.MAX_SMITHING_XP / CoreEnhancementCatalog.XP_PER_RANK).toInt()
            buildList {
                add("鍛冶熟練 Rank $rank / $max" to TEXT)
                if (rank < max) add("次の Rank まで ${CoreEnhancementCatalog.XP_PER_RANK - CoreEnhancementCatalog.masteryProgress(account.smithingXp)}" to MUTED)
                add("強化するたびに上がります" to MUTED)
            }
        }
        Path.MAGIC -> listOf("魔術の進み方は準備中です" to MUTED)
    }

    private fun progress(player: Player, account: CoreAccount, path: Path): Progress = when (path) {
        Path.COMBAT -> {
            val level = account.journey.level
            val next = if (level >= CoreJourneyRules.MAX_LEVEL) 1.0 else {
                val from = CoreJourneyRules.threshold(level); val to = CoreJourneyRules.threshold(level + 1)
                (account.journey.xp - from).toDouble() / (to - from)
            }
            Progress("冒険 Lv", "Lv $level", "$level", next)
        }
        Path.GATHER -> {
            val mastery = host.gatheringMastery(player)
            val best = QuestGatheringDiscipline.entries.maxBy { mastery.experience(it) }
            val level = mastery.level(best)
            val xp = mastery.experience(best)
            val next = (xp - 10.0 * level * level) / (10.0 * ((level + 1) * (level + 1) - level * level))
            Progress(best.displayName, "Lv $level", "$level", next)
        }
        Path.SMITH -> {
            val rank = CoreEnhancementCatalog.masteryRank(account.smithingXp)
            Progress("熟練", "Rank $rank", "$rank",
                CoreEnhancementCatalog.masteryProgress(account.smithingXp).toDouble() / CoreEnhancementCatalog.XP_PER_RANK)
        }
        Path.MAGIC -> Progress("準備中", "—", "—", 0.0)
    }

    // ------------------------------------------------------------------ 技 page

    private fun equippedIndices(account: CoreAccount): List<Int> {
        val build = account.journey.build
        return build.skills + (CoreSkillCatalog.ultimateStart(account.journey.job) + build.ultimate)
    }

    private fun chosen(account: CoreAccount, pick: SkillPick): CoreSkillDefinition =
        CoreSkillCatalog.skills(account.journey.job)[pick.candidate ?: equippedIndices(account)[pick.slot]]

    private fun skills(player: Player, account: CoreAccount, size: DialogCanvas.Size): Component {
        val canvas = DialogCanvas(size, metrics.space, size.canvasWidth.toDouble(), size.lines)
        val layout = size.layout
        canvas.background("page_skills", 0.0, 0.0)
        val tabs = layout.getAsJsonArray("tabs")
        for (i in 0 until tabs.size()) if (i != 1) {
            val box = tabs[i].asJsonArray
            canvas.sprite("hit_tab$i", box[0].asDouble, box[1].asDouble, click("tab_$i"))
        }
        val job = account.journey.job
        val build = account.journey.build
        val catalog = CoreSkillCatalog.skills(job)
        val stats = CoreAffixCatalog.stats(account)
        val equipped = equippedIndices(account)
        val pick = picks.getOrPut(player.uuid, ::SkillPick)
        if (!CoreJourneyRules.skillUnlocked(account, pick.slot)) { pick.slot = 0; pick.candidate = null }
        val shown = chosen(account, pick)
        fun lv(icon: String) = CoreSkillLevels.level(build, stats, icon)

        // Left: the five keys.
        val left = layout["left"].asDouble
        for (slot in 0..4) {
            val top = layout["keyY"].asDouble + slot * layout["keyStep"].asDouble
            if (slot == pick.slot) canvas.sprite("row_sel", left, top)
            if (CoreJourneyRules.skillUnlocked(account, slot)) {
                val skill = catalog[equipped[slot]]
                canvas.sprite("sk_${skill.icon}", left + 20, top + 5)
                canvas.text("b7", left + 42, top + 11, fit(canvas, "b7", skill.name, 66.0), if (slot == pick.slot) IVORY else TEXT)
                canvas.text("d7", left + 136, top + 11, "Lv ${lv(skill.icon)}", if (slot == pick.slot) IVORY else SOFT, Align.RIGHT)
                role(skill)?.let { canvas.text("m6", left + 42, top + 21, fit(canvas, "m6", it, 92.0), MUTED) }
                canvas.sprite("hit_key", left, top, click("key_$slot"))
            } else {
                canvas.text("m6", left + 42, top + 15, "冒険 Lv ${CoreSkillCatalog.unlockLevels[slot]} で使えます", FAINT)
            }
        }

        // Middle: the skill looked at.
        val card = layout.getAsJsonArray("card")
        val cx = card[0].asDouble + card[2].asDouble / 2
        val hero = layout.getAsJsonArray("hero")
        canvas.sprite("skh_${shown.icon}", hero[0].asDouble, hero[1].asDouble)
        canvas.text("b14", cx, 84.0, shown.name, TEXT, Align.CENTER)
        role(shown)?.let { canvas.text("m6", cx, 96.0, it, HEAT, Align.CENTER) }
        val base = CoreSkillLevels.base(build, shown.icon)
        val gear = CoreSkillLevels.gear(stats, shown.icon)
        val parts = listOf(Triple("b9", "Lv $base", TEXT)) + (if (gear > 0) listOf(Triple("b9", " +$gear", HEAT)) else emptyList()) +
            Triple("m6", "  / ${CoreSkillLevels.MAX_BASE}", MUTED)
        var x = cx - parts.sumOf { (style, text, _) -> canvas.width(style, text) } / 2
        for ((style, text, color) in parts) x += canvas.text(style, x, 111.0, text, color)
        canvas.text("m7", cx, 124.0, "威力 ×%.2f".format(CoreSkillLevels.multiplier(shown, base + gear)), SOFT, Align.CENTER)
        wrap(canvas, description(shown), 156.0, "m6").take(2).forEachIndexed { i, line ->
            canvas.text("m6", card[0].asDouble + 8, 137.0 + i * 10, line, MUTED)
        }
        val raise = layout.getAsJsonArray("raise")
        val points = CoreSkillLevels.available(account)
        val raiseLabel = when {
            base >= CoreSkillLevels.MAX_BASE -> "Lv ${CoreSkillLevels.MAX_BASE}（上限）"
            points <= 0 -> "点数がありません"
            else -> "Lv を上げる"
        }
        canvas.text("b7", raise[0].asDouble + raise[2].asDouble / 2, raise[1].asDouble + 11, raiseLabel,
            if (raiseLabel == "Lv を上げる") TEXT else FAINT, Align.CENTER)
        canvas.sprite("hit_raise", raise[0].asDouble, raise[1].asDouble, click("raise"))
        val equip = layout.getAsJsonArray("equip")
        val candidate = pick.candidate
        val equipLabel = if (candidate != null && candidate != equipped[pick.slot]) "キー${pick.slot + 2} に入れる" else "キー${pick.slot + 2} の技"
        canvas.text("b7", equip[0].asDouble + equip[2].asDouble / 2, equip[1].asDouble + 11, equipLabel,
            if (equipLabel.endsWith("入れる")) DARK else FAINT, Align.CENTER)
        canvas.sprite("hit_equip", equip[0].asDouble, equip[1].asDouble, click("equip"))

        // Right: what this key can take.
        val panel = layout.getAsJsonArray("panel")
        canvas.text("m6", panel[0].asDouble + 7, 30.0, if (pick.slot == 4) "選べる奥義" else "選べる技", MUTED)
        canvas.text("d7", panel[0].asDouble + panel[2].asDouble - 7, 30.0, "点数 $points", if (points > 0) GOLD else MUTED, Align.RIGHT)
        val start = CoreSkillCatalog.ultimateStart(job)
        val choices = if (pick.slot == 4) CoreSkillCatalog.ultimateChoices(job).map { start + it } else CoreSkillCatalog.normalChoices(job)
        val cell = layout.getAsJsonArray("cell")
        for ((k, index) in choices.withIndex()) {
            val cellX = cell[0].asDouble + (k % 2) * cell[4].asDouble
            val cellTop = cell[1].asDouble + (k / 2) * cell[5].asDouble
            val skill = catalog[index]
            if (skill.icon == shown.icon) canvas.sprite("cell_sel", cellX, cellTop)
            canvas.sprite("sk_${skill.icon}", cellX + 2, cellTop + 3)
            canvas.text("m6", cellX + 21, cellTop + 9, fit(canvas, "m6", skill.name, 41.0), TEXT)
            canvas.text("m6", cellX + 21, cellTop + 18, "Lv ${lv(skill.icon)}", MUTED)
            if (index in equipped && index != equipped[pick.slot]) canvas.overlay("cell_dim", cellX, cellTop)
            canvas.sprite("hit_cell", cellX, cellTop, click("cell_$index"))
        }
        val spent = CoreSkillLevels.spent(build)
        if (spent > 0) {
            val reset = layout.getAsJsonArray("reset")
            val price = CorePassiveTree.refundPrice(account.journey) * spent
            canvas.text("m6", reset[0].asDouble + reset[2].asDouble / 2, reset[1].asDouble + 9, "点数を全部戻す（銀貨 ${"%,d".format(price)}）",
                MUTED, Align.CENTER)
            canvas.sprite("hit_reset", reset[0].asDouble, reset[1].asDouble, click("reset"))
        }
        return canvas.build()
    }

    private fun role(skill: CoreSkillDefinition) = CoreWarriorSkillPresentation.skills[skill.icon]?.role
    private fun description(skill: CoreSkillDefinition) = CoreWarriorSkillPresentation.skills[skill.icon]?.use ?: skill.description

    /** [text] cut with … to fit [width] design px. */
    private fun fit(canvas: DialogCanvas, style: String, text: String, width: Double): String {
        if (canvas.width(style, text) <= width) return text
        var cut = text
        while (cut.isNotEmpty() && canvas.width(style, "$cut…") > width) cut = cut.dropLast(1)
        return "$cut…"
    }

    private fun wrap(canvas: DialogCanvas, text: String, width: Double, style: String = "m7"): List<String> {
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (ch in text) {
            if (current.isNotEmpty() && canvas.width(style, current.toString() + ch) > width) { lines += current.toString(); current = StringBuilder() }
            current.append(ch)
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    private fun head(player: Player): Component {
        val builder = ObjectContents.playerHead().name(player.username.take(16)).hat(true).id(player.uuid)
        player.skin?.let { builder.profileProperty(PlayerHeadObjectContents.property("textures", it.textures(), it.signature())) }
        return Component.`object`(builder.build())
    }

    // Minestom cannot write a custom click without a payload, so every one carries an empty compound.
    private fun click(action: String) = ClickEvent.custom(Key.key(NS, action), BinaryTagHolder.binaryTagHolder("{}"))

    companion object {
        const val NS = "projects_menu"
        private val TAB_TARGETS = listOf(Target.JOURNAL, Target.SKILLS, Target.PASSIVES, Target.STORAGE)
        private val DIALOG_FONT = Key.key("projects_ui_polish05", "dialog")
        private val TEXT = TextColor.color(0xECE6DC)
        private val SOFT = TextColor.color(0xC9C3B8)
        private val MUTED = TextColor.color(0x8F8A82)
        private val FAINT = TextColor.color(0x6E6A64)
        private val IVORY = TextColor.color(0xEFE6D2)
        private val HEAT = TextColor.color(0xFF9A4A)
        private val DARK = TextColor.color(0x16181C)
        private val GOLD = TextColor.color(0xE8C878)

        /** Null when the menu art was never built (the old chest journal stays the only menu). */
        fun metrics(): DialogCanvas.Metrics? = PlayerMenuDialog::class.java.classLoader.getResourceAsStream("polish05/menu-metrics.json")
            ?.use { DialogCanvas.Metrics.parse(it.readBytes()) }
    }
}
