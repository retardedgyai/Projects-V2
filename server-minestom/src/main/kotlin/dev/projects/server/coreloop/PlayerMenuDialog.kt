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
 * The player menu (冒険の手帳) drawn inside a vanilla dialog, in the 7版 C look (港の窓): the 自分 page is three
 * windows, the name card, the four paths (戦闘・採取・鍛冶・魔術) as banners, and the chosen path's numbers; the 技
 * page picks and raises skills. The other tabs still open the existing screens. Picture: the 「プレイヤーメニュー
 * ダイアログ案」 page (7版 C); look and positions: scripts/build_menu_dialog_assets.py.
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

    private enum class Path(val id: String, val label: String, val target: Target, val button: String, val color: TextColor) {
        COMBAT("combat", "戦闘", Target.ATLAS, "地図台を開く", TextColor.color(0xFF8A3D)),
        GATHER("gather", "採取", Target.MASTERY, "採取の育成を開く", TextColor.color(0x79E0D2)),
        SMITH("smith", "鍛冶", Target.WORKSHOP, "工房を開く", TextColor.color(0xF2C84B)),
        MAGIC("magic", "魔術", Target.GRIMOIRE, "魔術書を開く", TextColor.color(0xB9A0FF)),
    }

    /** What one path shows: the line under its name, the value in its tooltip, the number on its banner, how far to the next step (0..1). */
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
        val meta = DialogMetadata(title(size), Component.text("冒険の手帳"), true, false, DialogAfterAction.NONE,
            // The width includes the text widget's 4 px padding on each side, so lines need canvas + 8 or they wrap; but a
            // body wider than the screen (474 at GUI scale 4) is pushed to its left edge instead of centred (2026-11-02, in game).
            listOf(DialogBody.PlainMessage(body(player, account, size), size.canvasWidth + 9)), emptyList())
        val close = DialogActionButton(Component.text("閉じる", MUTED).font(DIALOG_FONT), null, 80,
            DialogAction.Custom(Key.key(NS, "close"), null))
        return Dialog.Notice(meta, close)
    }

    /** The title is left empty (only its width, which keeps the vanilla warning mark at the right). */
    private fun title(size: DialogCanvas.Size): Component =
        DialogCanvas(size, metrics.space, size.titleWidth.toDouble(), 1, size.titleTop, 1.0, metrics.forScale(4).styles).buildLine()

    private fun body(player: Player, account: CoreAccount, size: DialogCanvas.Size): Component {
        if (pages[player.uuid] == Page.SKILLS) return skills(player, account, size)
        val canvas = DialogCanvas(size, metrics.space, size.canvasWidth.toDouble(), size.lines)
        val layout = size.layout
        canvas.background("page_self", 0.0, 0.0)
        tabs(canvas, layout, 0)
        val path = selected[player.uuid] ?: Path.COMBAT
        val progress = Path.entries.associateWith { progress(player, account, it) }
        portrait(canvas, layout, player, account)
        paths(canvas, layout, path, progress)
        panel(canvas, layout, player, account, path)
        return canvas.build()
    }

    /** The tab row is baked; each tab but the open one gets a hit area. */
    private fun tabs(canvas: DialogCanvas, layout: JsonObject, open: Int) {
        val tabs = layout.getAsJsonArray("tabs")
        for (i in 0 until tabs.size()) if (i != open) {
            val box = tabs[i].asJsonArray
            canvas.sprite("hit_tab$i", box[0].asDouble, box[1].asDouble, click("tab_$i"))
        }
    }

    /** Left window, the name card: the class in its slot, face and name, title, and the numbers beside the baked labels. */
    private fun portrait(canvas: DialogCanvas, layout: JsonObject, player: Player, account: CoreAccount) {
        val emblem = layout.getAsJsonArray("emblem24")
        canvas.sprite("emblem24_${account.journey.job.name.lowercase()}", emblem[0].asDouble, emblem[1].asDouble)
        // The face is the vanilla player-head object, 8 GUI px wide on its line at every scale.
        val name = layout.getAsJsonArray("name")
        val x = name[0].asDouble; val baseline = name[1].asDouble
        canvas.inline(x, baseline - 1, 8.0, head(player))
        canvas.text("dot16", x + canvas.design(8.0) + 4, baseline + 3, fit(canvas, "dot16", player.username, 90.0), TEXT)
        val kv = layout.getAsJsonArray("kv")
        val right = kv[0].asDouble + kv[2].asDouble
        val clears = account.zoneRecords.values.sumOf { it.clears }
        val passives = CorePassiveTree.budget(account) - account.journey.build.passives.size
        val skillPoints = CoreSkillLevels.available(account)
        listOf(account.journey.job.displayName to TEXT, "${account.journey.level}" to TEXT, "$clears" to TEXT,
            "$skillPoints" to if (skillPoints > 0) GOLD else TEXT, "$passives" to if (passives > 0) GOLD else TEXT,
            "%,d".format(account.silver) to TEXT)
            .forEachIndexed { i, (value, color) -> canvas.text("d7", right, kv[1].asDouble + i * kv[3].asDouble, value, color, Align.RIGHT) }
        val edit = layout.getAsJsonArray("edit")
        canvas.sprite("hit_edit", edit[0].asDouble, edit[1].asDouble, click("edit"))
    }

    /** Middle window: the four path banners (baked) with each one's step and number, the chosen one framed. */
    private fun paths(canvas: DialogCanvas, layout: JsonObject, chosen: Path, progress: Map<Path, Progress>) {
        val rows = layout.getAsJsonArray("rows")
        for (path in Path.entries) {
            val r = rows[path.ordinal].asJsonArray
            val x = r[0].asDouble; val top = r[1].asDouble
            val p = progress.getValue(path)
            if (path == chosen) canvas.sprite("row_on", x, top)
            canvas.text("m6", x + 6, top + 21, fit(canvas, "m6", p.sub, 100.0), SOFT)
            canvas.text("d9", x + 134, top + 16, p.short, if (path == Path.MAGIC) MUTED else TEXT, Align.RIGHT)
            canvas.sprite("hit_row", x, top, click("path_${path.ordinal}"), Component.text("${path.label}　${p.value}", TEXT))
        }
        val bar = layout.getAsJsonArray("pathBar")
        val p = progress.getValue(chosen)
        val bx = bar[0].asDouble; val by = bar[1].asDouble
        val w = canvas.text("b7", bx, by - 4, "いまの道 ・ ", TEXT)
        canvas.text("b7", bx + w, by - 4, chosen.label, chosen.color)
        canvas.text("d7", bx + bar[2].asDouble, by - 4, p.value, TEXT, Align.RIGHT)
        val width = bar[2].asInt
        repeat((p.next * width / 2).roundToInt().coerceIn(0, width / 2)) { canvas.sprite("bar_${chosen.id}", bx + it * 2, by) }
        val primary = layout.getAsJsonArray("primary")
        canvas.text("b7", primary[0].asDouble + 6, primary[1].asDouble + 10, chosen.button, DARK)
        canvas.sprite("hit_go", primary[0].asDouble, primary[1].asDouble, click("primary"))
    }

    /** Right window: the chosen path's own numbers, a label on the left and its value on the right. No sentences. */
    private fun panel(canvas: DialogCanvas, layout: JsonObject, player: Player, account: CoreAccount, path: Path) {
        val steps = layout.getAsJsonArray("steps")
        val x = steps[0].asDouble
        val step = steps[2].asDouble
        canvas.text("b8", x - 6, steps[1].asDouble - 14, path.label, path.color)
        numbers(player, account, path).take(steps[3].asInt).forEachIndexed { i, (label, value) ->
            val baseline = steps[1].asDouble + i * step
            canvas.text("m6", x, baseline, label, MUTED)
            canvas.text("d7", x + 136, baseline, value, TEXT, Align.RIGHT)
        }
        notices[player.uuid]?.let { canvas.text("m6", x, 182.0, it, HEAT) }
    }

    private fun numbers(player: Player, account: CoreAccount, path: Path): List<Pair<String, String>> = when (path) {
        Path.COMBAT -> {
            val level = account.journey.level
            buildList {
                add("冒険 Lv" to "$level / ${CoreJourneyRules.MAX_LEVEL}")
                if (level < CoreJourneyRules.MAX_LEVEL) add("次の Lv まで" to "%,d".format(CoreJourneyRules.threshold(level + 1) - account.journey.xp))
                add("開いた遠征" to "T${account.unlockedMapTier}")
                add("踏破" to "${account.zoneRecords.values.sumOf { it.clears }}")
            }
        }
        Path.GATHER -> {
            val mastery = host.gatheringMastery(player)
            QuestGatheringDiscipline.entries.map { it.displayName to "Lv ${mastery.level(it)}" }
        }
        Path.SMITH -> {
            val rank = CoreEnhancementCatalog.masteryRank(account.smithingXp)
            val max = (CoreEnhancementCatalog.MAX_SMITHING_XP / CoreEnhancementCatalog.XP_PER_RANK).toInt()
            buildList {
                add("鍛冶熟練" to "Rank $rank / $max")
                if (rank < max) add("次の Rank まで" to "${CoreEnhancementCatalog.XP_PER_RANK - CoreEnhancementCatalog.masteryProgress(account.smithingXp)}")
            }
        }
        Path.MAGIC -> emptyList()
    }

    private fun progress(player: Player, account: CoreAccount, path: Path): Progress = when (path) {
        Path.COMBAT -> {
            val level = account.journey.level
            val next = if (level >= CoreJourneyRules.MAX_LEVEL) 1.0 else {
                val from = CoreJourneyRules.threshold(level); val to = CoreJourneyRules.threshold(level + 1)
                (account.journey.xp - from).toDouble() / (to - from)
            }
            val sub = if (level >= CoreJourneyRules.MAX_LEVEL) "冒険 Lv は上限" else "次の Lv まで ${"%,d".format(CoreJourneyRules.threshold(level + 1) - account.journey.xp)}"
            Progress(sub, "Lv $level", "$level", next)
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
            val sub = if (rank >= (CoreEnhancementCatalog.MAX_SMITHING_XP / CoreEnhancementCatalog.XP_PER_RANK).toInt()) "Rank は上限"
                else "次の Rank まで ${CoreEnhancementCatalog.XP_PER_RANK - CoreEnhancementCatalog.masteryProgress(account.smithingXp)}"
            Progress(sub, "Rank $rank", "$rank",
                CoreEnhancementCatalog.masteryProgress(account.smithingXp).toDouble() / CoreEnhancementCatalog.XP_PER_RANK)
        }
        Path.MAGIC -> Progress("準備中", "—", "–", 0.0)
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
        tabs(canvas, layout, 1)
        val job = account.journey.job
        val build = account.journey.build
        val catalog = CoreSkillCatalog.skills(job)
        val stats = CoreAffixCatalog.stats(account)
        val equipped = equippedIndices(account)
        val pick = picks.getOrPut(player.uuid, ::SkillPick)
        if (!CoreJourneyRules.skillUnlocked(account, pick.slot)) { pick.slot = 0; pick.candidate = null }
        val shown = chosen(account, pick)
        fun lv(icon: String) = CoreSkillLevels.level(build, stats, icon)
        fun gearOn(icon: String) = CoreSkillLevels.gear(stats, icon) > 0
        val points = CoreSkillLevels.available(account)

        // Left: the class emblem with the Lv and points hexes, and the five keys.
        val emblem = layout.getAsJsonArray("emblem64")
        canvas.sprite("emblem64_${job.name.lowercase()}", emblem[0].asDouble, emblem[1].asDouble)
        val hexes = layout.getAsJsonArray("hex")
        for ((i, value) in listOf("${account.journey.level}" to TEXT, "$points" to if (points > 0) GOLD else MUTED).withIndex()) {
            val hex = hexes[i].asJsonArray
            canvas.text("d9", hex[0].asDouble + 13, hex[1].asDouble + 21, value.first, value.second, Align.CENTER)
        }
        val keys = layout.getAsJsonArray("keys")
        for (slot in 0..4) {
            val k = keys[slot].asJsonArray
            val x = k[0].asDouble; val top = k[1].asDouble
            if (!CoreJourneyRules.skillUnlocked(account, slot)) {
                canvas.sprite("slot_x", x, top, hover = Component.text("冒険 Lv ${CoreSkillCatalog.unlockLevels[slot]} で使えます", TEXT))
                continue
            }
            val skill = catalog[equipped[slot]]
            canvas.sprite("sk20_${skill.icon}", x + 3, top + 3)
            canvas.sprite("key_tri", x, top)
            canvas.text("d6", x + 1, top + 6, "${slot + 2}", DARK)
            canvas.text("d6", x + 25, top + 25, "${lv(skill.icon)}", if (gearOn(skill.icon)) HEAT else TEXT, Align.RIGHT)
            if (slot == pick.slot) canvas.sprite("slot_sel", x, top)
            canvas.sprite("hit_slot", x, top, click("key_$slot"), Component.text("キー${slot + 2}　${skill.name}", TEXT))
        }

        // Middle: every skill the open key can take.
        val start = CoreSkillCatalog.ultimateStart(job)
        val normals = CoreSkillCatalog.normalChoices(job)
        val ultimates = CoreSkillCatalog.ultimateChoices(job).map { start + it }
        fun cells(name: String, choices: List<Int>, active: Boolean) {
            val spots = layout.getAsJsonArray(name)
            for (n in 0 until spots.size()) {
                val s = spots[n].asJsonArray
                val x = s[0].asDouble; val top = s[1].asDouble
                val index = choices.getOrNull(n)
                if (index == null) { canvas.sprite("slot_x", x, top); continue }
                val skill = catalog[index]
                canvas.sprite("sk20_${skill.icon}", x + 3, top + 3)
                val key = equipped.indexOf(index)
                if (key >= 0) { canvas.sprite("key_tri", x, top); canvas.text("d6", x + 1, top + 6, "${key + 2}", DARK) }
                canvas.text("d6", x + 25, top + 25, "${lv(skill.icon)}", if (gearOn(skill.icon)) HEAT else TEXT, Align.RIGHT)
                if (!active || (key >= 0 && key != pick.slot)) canvas.overlay("slot_dim", x, top)
                if (skill.icon == shown.icon) canvas.sprite("slot_sel", x, top)
                canvas.sprite("hit_slot", x, top, if (active) click("cell_$index") else null, Component.text(skill.name, TEXT))
            }
        }
        cells("grid", normals, pick.slot != 4)
        cells("ults", ultimates, pick.slot == 4)

        // Right: the skill looked at.
        val rx = layout["rx"].asDouble
        val big = layout.getAsJsonArray("big")
        canvas.sprite("skb_${shown.icon}", big[0].asDouble, big[1].asDouble)
        val base = CoreSkillLevels.base(build, shown.icon)
        val gear = CoreSkillLevels.gear(stats, shown.icon)
        var x = rx
        x += canvas.text("d9", x, 40.0, "Lv $base", GOLD)
        if (gear > 0) x += canvas.text("d9", x + 3, 40.0, "+$gear", HEAT) + 3
        canvas.text("m6", x + 4, 40.0, "/ ${CoreSkillLevels.MAX_BASE}", MUTED)
        canvas.text("dot16", rx, 58.0, fit(canvas, "dot16", shown.name, 104.0), TEXT)
        role(shown)?.let { canvas.text("m6", rx, 69.0, it, SOFT) }
        wrap(canvas, description(shown), 104.0, "m6").take(2).forEachIndexed { i, line -> canvas.text("m6", rx, 79.0 + i * 9, line, MUTED) }
        val bars = layout.getAsJsonArray("bars")
        val barW = layout["barW"].asInt
        val all = (normals + ultimates).map { catalog[it] }
        fun fill(n: Int, fraction: Double) {
            val b = bars[n].asJsonArray
            repeat((fraction * barW / 2).roundToInt().coerceIn(0, barW / 2)) { canvas.sprite("bar_fill", b[0].asDouble + it * 2, b[1].asDouble) }
        }
        fun power(s: CoreSkillDefinition) = s.formula.base + s.formula.ad * 100 + s.formula.ap * 100
        fill(0, power(shown) / (all.maxOf(::power).takeIf { it > 0 } ?: 1.0))
        fill(1, 1 - shown.cooldown.toDouble() / (all.maxOf { it.cooldown }.takeIf { it > 0 } ?: 1))
        fill(2, shown.radius / all.maxOf { it.radius })
        canvas.text("m6", rx + 32, 122.0, "×%.2f　次の Lv で ×%.2f".format(CoreSkillLevels.multiplier(shown, base + gear),
            CoreSkillLevels.multiplier(shown, base + gear + 1)), MUTED)
        val dias = layout.getAsJsonArray("dias")
        for (i in 0 until minOf(10, base + gear)) {
            val d = dias[i].asJsonArray
            canvas.sprite(if (i < base) "dia_on" else "dia_gear", d[0].asDouble, d[1].asDouble)
        }
        val acts = layout.getAsJsonArray("acts")
        val raiseLabel = when {
            base >= CoreSkillLevels.MAX_BASE -> "Lv ${CoreSkillLevels.MAX_BASE}（上限）"
            points <= 0 -> "技ポイントがありません"
            else -> "Lv を上げる（1 ポイント）"
        }
        val raise = acts[0].asJsonArray
        canvas.text("b7", raise[0].asDouble + 6, raise[1].asDouble + 10, raiseLabel, if (raiseLabel.startsWith("Lv を")) DARK else DIM_DARK)
        canvas.sprite("hit_act", raise[0].asDouble, raise[1].asDouble, click("raise"))
        val equip = acts[1].asJsonArray
        val candidate = pick.candidate
        val equipLabel = if (candidate != null && candidate != equipped[pick.slot]) "キー${pick.slot + 2} に入れる" else "キー${pick.slot + 2} の技"
        canvas.text("b7", equip[0].asDouble + 6, equip[1].asDouble + 10, equipLabel, if (equipLabel.endsWith("入れる")) TEXT else MUTED)
        canvas.sprite("hit_act", equip[0].asDouble, equip[1].asDouble, click("equip"))
        val spent = CoreSkillLevels.spent(build)
        if (spent > 0) {
            val reset = layout.getAsJsonArray("reset")
            val price = CorePassiveTree.refundPrice(account.journey) * spent
            canvas.text("m6", reset[0].asDouble, reset[1].asDouble + 8, "技ポイントを全部戻す（銀貨 ${"%,d".format(price)}）", MUTED)
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
        private val HEAT = TextColor.color(0xFF9A4A)
        private val DARK = TextColor.color(0x2A1A06)
        private val DIM_DARK = TextColor.color(0x6B5320)
        private val GOLD = TextColor.color(0xF2C84B)

        /** Null when the menu art was never built (the old chest journal stays the only menu). */
        fun metrics(): DialogCanvas.Metrics? = PlayerMenuDialog::class.java.classLoader.getResourceAsStream("polish05/menu-metrics.json")
            ?.use { DialogCanvas.Metrics.parse(it.readBytes()) }
    }
}
