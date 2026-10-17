package dev.projects.server.coreloop

import com.google.gson.JsonParser
import dev.projects.webui.Box
import dev.projects.webui.ForgeLightPhase
import dev.projects.webui.ForgeUiFlow
import dev.projects.webui.Polish05FontMetrics
import dev.projects.webui.UiNode
import dev.projects.webui.UiScene
import dev.projects.webui.UiSprite
import java.util.Locale
import kotlin.math.roundToInt
import dev.projects.server.coreloop.GrimoireBook.BRANCHES
import dev.projects.server.coreloop.GrimoireBook.BRANCH_LINES
import dev.projects.server.coreloop.GrimoireBook.CHAPTERS
import dev.projects.server.coreloop.GrimoireBook.ENTRIES
import dev.projects.server.coreloop.GrimoireBook.Entry
import dev.projects.server.coreloop.GrimoireBook.Reward
import kotlin.math.roundToLong
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 魔術書: the magic path's quest book (FTB Quests / Thaumonomicon role), on the camera display like the
 * forge and the 開拓大陸 map. It shares their grammar — header with title, tabs and wallet pills,
 * rounded panels, M PLUS 2 text, key hints — and gets its magic from colour: a starry night sky,
 * glowing ring medallions for entries, gold for done and cyan for what can be worked on.
 * Pixel art is kept to item icons and illustrations, as in the forge.
 *
 * The book's content is [GrimoireBook]; the player's progress is [GrimoireProgress], which outlives the flow.
 * Completion will come from the world, so the page only offers a test shortcut for the unlock animation.
 */
internal class GrimoireFlow(
    private val art: Art, private val progress: GrimoireProgress,
    private val clock: () -> Long = System::currentTimeMillis, private val leave: () -> Unit,
) : ForgeUiFlow {
    /** Icons from polish05/grimoire.json plus the shared forge shapes from polish05/font-map.json. */
    class Art(json: ByteArray, forgeFontMap: ByteArray) {
        val sprites: Map<String, UiSprite>
        init {
            fun parse(bytes: ByteArray, section: String?) = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
                .let { if (section != null) it.getAsJsonObject(section) else it }.entrySet()
                .filter { it.value.isJsonObject && it.value.asJsonObject.has("char") }
                .associate { (name, v) -> v.asJsonObject.let { name to UiSprite(it["char"].asString, it["font"].asString, it["width"].asInt, it["height"].asInt) } }
            sprites = parse(json, "sprites") + parse(forgeFontMap, null).filterKeys { it.startsWith("v3_round_") || it.startsWith("v3_ring") }
        }
    }

    enum class State { DONE, OPEN, SIDE, LOCKED, HIDDEN, GOAL, GOAL_OPEN }

    override val view = "grimoire"
    override val muted = false
    override val operationActive = false
    override val ownsEffects = true
    override val live = true

    private var tab = 0
    private var selected = progress.pinned ?: "n5"
    private var hovered: String? = null
    private var fresh: String? = null
    private var freshAt = 0L
    private var selectedAt = clock()
    /** A claimed reward on its way to the wallet: the slot glows, the icon flies to its pill, the number counts up. */
    private class ClaimFx(val reward: Reward, val from: Long, val to: Long, val at: Long)
    private var claimFx: ClaimFx? = null
    private var rewardSlot = 0.0 to 0.0
    /** Map structure while the candidates are compared: A chapter bands, B chapter hairlines, C none. */
    internal var mapStyle = 'A'

    private fun state(e: Entry): State {
        val done = e.id in progress.done
        val ready = e.parents.all { it in progress.done }
        return when {
            done -> State.DONE
            ready && e.side -> State.SIDE
            ready && e.goal -> State.GOAL_OPEN
            ready -> State.OPEN
            e.mystery -> State.HIDDEN
            e.goal -> State.GOAL
            else -> State.LOCKED
        }
    }
    private fun canClaim(e: Entry) = state(e) == State.DONE && e.reward != null && e.id !in progress.claimed
    private fun workable(s: State) = s == State.OPEN || s == State.SIDE || s == State.GOAL_OPEN
    private fun wallet(kind: Reward.Kind) = when (kind) { Reward.Kind.SILVER -> progress.silver; Reward.Kind.INK -> progress.inkTotal.toLong() }
    /** The wallet as drawn: a claimed amount counts up once its icon has landed. */
    private fun shown(kind: Reward.Kind, now: Long): Long {
        val fx = claimFx?.takeIf { it.reward.kind == kind } ?: return wallet(kind)
        val t = ((now - fx.at - FLY_END) / COUNT_MS.toDouble()).coerceIn(0.0, 1.0)
        return fx.from + ((fx.to - fx.from) * t).roundToLong()
    }
    /** Why a locked entry is locked: the unfinished entries before it. */
    private fun lockReason(e: Entry): String = e.parents.filter { it !in progress.done }.map(GrimoireBook::entry)
        .joinToString("」と「", "「", "」を終えると開く") { if (state(it) == State.HIDDEN) "?" else it.title.removePrefix("寄り道：") }

    override fun hover(id: String?) {
        hovered = id?.takeIf { it.startsWith("n-") && it.endsWith("-hit") }?.removePrefix("n-")?.removeSuffix("-hit")
    }

    override fun action(action: String): Boolean {
        when {
            action.startsWith("node:") -> {
                val id = action.removePrefix("node:")
                if (ENTRIES.none { it.id == id } || id == selected) return false
                selected = id; selectedAt = clock()
            }
            action.startsWith("tab:") -> {
                val t = action.removePrefix("tab:").toIntOrNull()?.takeIf { it in BRANCHES.indices } ?: return false
                if (t == tab) return false
                tab = t; selectedAt = clock()
            }
            action == "claim" -> {
                val e = GrimoireBook.entry(selected)
                if (!canClaim(e)) return false
                val r = e.reward!!
                val from = wallet(r.kind)
                progress.claimed += e.id
                when (r.kind) {
                    Reward.Kind.SILVER -> progress.silver += r.amount
                    Reward.Kind.INK -> progress.ink.merge(requireNotNull(r.aspect), r.amount, Int::plus)
                }
                claimFx = ClaimFx(r, from, wallet(r.kind), clock())
            }
            action == "pin" -> {
                if (!workable(state(GrimoireBook.entry(selected)))) return false
                progress.pinned = if (progress.pinned == selected) null else selected
            }
            action == "achieve" -> {
                val e = GrimoireBook.entry(selected)
                if (!workable(state(e))) return false
                progress.done += e.id; fresh = e.id; freshAt = clock()
            }
            action == "close" -> leave()
            else -> return false
        }
        return true
    }

    // ------------------------------------------------------------------ drawing (1920×1080 design units)

    private val nodes = mutableListOf<UiNode>()
    private fun box(x: Double, y: Double, w: Double, h: Double) = Box(x * SCALE, TOP + y * SCALE, w * SCALE, h * SCALE)
    /** UiRenderer puts glyphs (sprites, text) 5 depth steps in front of fills; fills are lifted so one depth is one layer. */
    private fun rect(id: String, x: Double, y: Double, w: Double, h: Double, color: String, depth: Int, action: String? = null, hover: String? = null) {
        if (w <= 0 || h <= 0) return
        val style = buildMap { put("background-color", solid(color)); if (hover != null) put("hover-background-color", solid(hover)) }
        nodes += UiNode(id, box(x, y, w, h), "", style, action, null, true, depth + GLYPH_LIFT)
    }
    private fun sprite(id: String, name: String, x: Double, y: Double, w: Double, h: Double, depth: Int, tint: String? = null, opacity: Double = 1.0) {
        val s = art.sprites[name] ?: return
        val style = buildMap {
            if (tint != null) put("sprite-color", "#" + tint.takeLast(6))
            if (opacity < 1.0) put("opacity", String.format(Locale.US, "%.3f", opacity))
        }
        nodes += UiNode(id, box(x, y, w, h), "", style, null, null, true, depth, s)
    }
    /** The 魔術書's own crisp page for this face and size ("gmv3m15", built by build_grimoire_assets.py), else the forge's. */
    private fun page(family: String, size: Double) =
        listOf("gm$family${size.toInt()}", "$family${size.toInt()}").firstOrNull(Polish05FontMetrics::has) ?: family
    private fun text(id: String, x: Double, y: Double, w: Double, value: String, family: String, size: Double, color: String, depth: Int,
                     align: String = "left", shadow: Boolean = false) {
        if (value.isEmpty()) return
        nodes += UiNode(id, box(x, y, w, size * 1.25), value, buildMap {
            put("font-size", "${size * SCALE}px"); put("color", "#" + color.takeLast(6)); put("text-align", align)
            put("font-family", "projects_ui_polish05:${page(family, size)}")
            if (shadow) put("text-shadow", "true")
        }, null, null, true, depth)
    }
    private fun width(value: String, family: String, size: Double) =
        Polish05FontMetrics.advance(value, page(family, size)) / Polish05FontMetrics.SIZE * size
    private fun argb(hex: String, a: Double) = String.format("#%02x%s", (a * 255).toInt().coerceIn(0, 255), hex.takeLast(6))
    /**
     * The colour [ground] shows through a translucent fill, as an opaque fill. The client draws the display's fills in
     * entity order, not back to front, so a translucent fill can land before what is behind it and show the world's
     * sky instead (the map turned pale blue in the client). Fully clear fills stay clear: they only catch clicks.
     */
    private var ground = BACKDROP
    private fun solid(color: String): String {
        val hex = color.removePrefix("#")
        val a = if (hex.length == 8) hex.take(2).toInt(16) else 255
        if (a == 0 || a == 255) return color
        val over = hex.takeLast(6); val under = ground.takeLast(6)
        return "#ff" + (0..2).joinToString("") { i ->
            val o = over.substring(i * 2, i * 2 + 2).toInt(16); val u = under.substring(i * 2, i * 2 + 2).toInt(16)
            "%02x".format((u + (o - u) * a / 255.0).roundToInt())
        }
    }
    /** Forge-style rounded fill: a cross of fills plus four tinted quarter discs. */
    private fun round(id: String, x: Double, y: Double, w: Double, h: Double, r: Double, color: String, alpha: Double, depth: Int,
                      action: String? = null, hover: String? = null) {
        val rr = r.coerceAtMost(minOf(w, h) / 2)
        val c = argb(color, alpha)
        rect("$id-c", x + rr, y, w - 2 * rr, h, c, depth, action, hover)
        rect("$id-l", x, y + rr, rr, h - 2 * rr, c, depth)
        rect("$id-r", x + w - rr, y + rr, rr, h - 2 * rr, c, depth)
        for ((k, cx, cy) in listOf(Triple("tl", x, y), Triple("tr", x + w - rr, y), Triple("bl", x, y + h - rr), Triple("br", x + w - rr, y + h - rr)))
            sprite("$id-$k", "v3_round_$k", cx, cy, rr, rr, depth + GLYPH_LIFT, solid(c))
    }
    /** Forge-style 1px outline on a rounded box. */
    private fun border(id: String, x: Double, y: Double, w: Double, h: Double, r: Double, color: String, alpha: Double, depth: Int, t: Double = 1.0) {
        val c = argb(color, alpha)
        rect("$id-t", x + r, y, w - 2 * r, t, c, depth); rect("$id-b", x + r, y + h - t, w - 2 * r, t, c, depth)
        rect("$id-l", x, y + r, t, h - 2 * r, c, depth); rect("$id-r", x + w - t, y + r, t, h - 2 * r, c, depth)
        val ring = if (r >= 12) "v3_ring14" else "v3_ring10"
        for ((k, cx, cy) in listOf(Triple("tl", x, y), Triple("tr", x + w - r, y), Triple("bl", x, y + h - r), Triple("br", x + w - r, y + h - r)))
            sprite("$id-$k", "${ring}_$k", cx, cy, r, r, depth + GLYPH_LIFT, solid(c))
    }
    private fun panel(id: String, x: Double, y: Double, w: Double, h: Double, r: Double, depth: Int, fill: String = PANEL, alpha: Double = 0.9) {
        round(id, x, y, w, h, r, fill, alpha, depth)
        border("$id-line", x, y, w, h, r, "#ffffff", 0.09, depth + 1)
    }

    override fun scene(light: ForgeLightPhase): UiScene {
        nodes.clear()
        val now = clock()
        ground = BACKDROP
        rect("backdrop", -400.0, -200.0, 2720.0, 1480.0, "#ff" + BACKDROP.takeLast(6), -8)
        sky(now)
        header(now)
        panel("map", MAP_X, MAP_Y, MAP_W, MAP_H, 18.0, 1, "#0c1020", 0.55)
        ground = solid(argb("#0c1020", 0.55))
        if (tab == 0) trunkMap(now) else sealedBranch()
        ground = BACKDROP
        panel("side", SIDE_X, MAP_Y, SIDE_W, MAP_H, 18.0, 1)
        ground = solid(argb(PANEL, 0.9))
        if (tab == 0) entryPanel(GrimoireBook.entry(selected), now) else branchPanel()
        ground = BACKDROP
        footer()
        claimEffect(now)
        return UiScene(800.0, 480.0, nodes.toList())
    }

    /**
     * Night sky: a fixed scatter of stars. (Soft glow clouds behind the panels were dropped: in the client a
     * translucent sprite could be drawn before the backdrop and punch a pale hole of world sky through it.)
     */
    private fun sky(now: Long) {
        for ((i, s) in STARS.withIndex()) {
            val twinkle = ((now / 600 + i) % 7 == 0L)
            // Stars over the map sit on its (opaque) fill, so the map keeps its night sky.
            val onMap = s.first in MAP_X + 12..MAP_X + MAP_W - 16 && s.second in MAP_Y + 12..MAP_Y + MAP_H - 16
            val color = if (twinkle) "#ffeafcff" else if (i % 3 == 0) "#ff9fe7f4" else "#ff5e7aa8"
            rect("star$i", s.first, s.second, s.third, s.third, if (onMap && !twinkle) "#ff3a4a6e" else color, if (onMap) 2 else -7)
        }
    }

    private fun header(now: Long) {
        // Title block, as the forge's 鍛冶師の仕事場.
        round("h-slot", 56.0, 30.0, 52.0, 52.0, 6.0, "#0D0E10", 1.0, 2)
        sprite("h-slot-i", "ic_book", 66.0, 40.0, 32.0, 32.0, 8)
        text("h-title", 124.0, 30.0, 300.0, "魔術書", "v3b", 24.0, "#ECE6DC", 8)
        text("h-sub", 124.0, 64.0, 300.0, "帰還港 · 研究室", "v3r", 13.0, "#A29E97", 8)
        // Branch tabs in the middle, like 強化 / 精錬 / 製作, each with its branch icon; sealed branches are grey.
        var x = 960 - BRANCHES.sumOf { width(it.first, "v3b", 20.0) + 28 + 30 } / 2
        BRANCHES.forEachIndexed { i, (name, icon) ->
            val on = i == tab
            val w = 28 + width(name, "v3b", 20.0)
            sprite("tab$i-i", icon, x - 4, 44.0, 32.0, 32.0, 8, if (i == 0) null else "#ff8a8a96", if (on || i == 0) 1.0 else 0.6)
            text("tab$i", x + 28, 44.0, w - 28, name, if (on) "v3b" else "v3m", if (on) 20.0 else 18.0, if (on) "#ECE6DC" else if (i == 0) "#A29E97" else "#6F6B65", 8)
            if (on) rect("tab$i-u", x, 82.0, w, 3.0, CYAN, 8)
            rect("tab$i-hit", x - 10, 34.0, w + 20, 56.0, "#00000000", 9, "tab:$i", "#14ffffff")
            x += w + 30
        }
        // Wallet pills on the right.
        fun pill(id: String, x: Double, w: Double) { round(id, x, 34.0, w, 44.0, 22.0, "#101114", 0.86, 2); border("$id-l", x, 34.0, w, 44.0, 22.0, "#ffffff", 0.09, 3) }
        pill("p-ink", 1552.0, 140.0)
        sprite("p-ink-i", "i16_ink", 1568.0, 40.0, 32.0, 32.0, 8)
        text("p-ink-k", 1600.0, 47.0, 50.0, "インク", "v3r", 13.0, "#A29E97", 8)
        text("p-ink-v", 1654.0, 44.0, 40.0, "${shown(Reward.Kind.INK, now)}", "v3num", 19.0, "#ECE6DC", 8)
        pill("p-silver", 1704.0, 160.0)
        sprite("p-silver-i", "i16_coin", 1720.0, 40.0, 32.0, 32.0, 8)
        text("p-silver-k", 1752.0, 47.0, 40.0, "銀貨", "v3r", 13.0, "#A29E97", 8)
        text("p-silver-v", 1792.0, 44.0, 70.0, String.format(Locale.US, "%,d", shown(Reward.Kind.SILVER, now)), "v3num", 19.0, "#ECE6DC", 8)
    }

    private fun nodeX(e: Entry) = MAP_X + 96 + e.col * 166.0
    private fun nodeY(e: Entry) = MAP_Y + 40 + e.row * 170.0

    private fun trunkMap(now: Long) {
        // The count is the trunk only; side entries are optional.
        val trunk = ENTRIES.filter { !it.side }
        val done = trunk.count { state(it) == State.DONE }
        val claims = ENTRIES.count(::canClaim)
        text("m-title", MAP_X + 32, MAP_Y + 24, 200.0, "幹 · 基礎魔術", "v3b", 20.0, "#ECE6DC", 8)
        text("m-count", MAP_X + 32 + width("幹 · 基礎魔術", "v3b", 20.0) + 16, MAP_Y + 26, 120.0, "$done / ${trunk.size}", "v3num", 19.0, "#A29E97", 8)
        if (claims > 0) {
            val label = "受け取れる報酬 $claims"
            val w = width(label, "v3m", 14.0) + 36
            round("m-claim", MAP_X + MAP_W - 32 - w, MAP_Y + 22, w, 30.0, 15.0, "#C2554A", 0.22, 3)
            rect("m-claim-dot", MAP_X + MAP_W - 32 - w + 14, MAP_Y + 34, 6.0, 6.0, "#ffe58b80", 4)
            text("m-claim-t", MAP_X + MAP_W - 32 - w + 26, MAP_Y + 28, w, label, "v3m", 14.0, "#E58B80", 8)
        }
        for ((i, ch) in CHAPTERS.withIndex()) {
            val cx = MAP_X + 96 + ch.centre * 166.0
            val left = MAP_X + 96 + (ch.from - 0.5) * 166.0; val cw = (ch.to - ch.from + 1) * 166.0
            if (mapStyle == 'A' && i % 2 == 1) rect("ch-${ch.name}-band", left, MAP_Y + 56, cw, MAP_H - 112, "#07ffffff", 1)
            if (mapStyle == 'B' && i > 0) rect("ch-${ch.name}-line", left, MAP_Y + 56, 1.0, MAP_H - 112, "#12ffffff", 1)
            if (mapStyle != 'C') rect("ch-${ch.name}-u", cx - 20, MAP_Y + 88, 40.0, 2.0, "#ff2e3a5e", 2)
            text("ch-${ch.name}", cx - 60, MAP_Y + 62, 120.0, ch.name, "v3m", 14.0, "#6F7FA8", 8, "center")
        }
        // Paths: fine dotted light between medallion centres.
        for (e in ENTRIES) for (p in e.parents) {
            val a = GrimoireBook.entry(p)
            val sa = state(a); val sb = state(e)
            val color = when {
                sa != State.DONE -> "#ff26304a"
                sb == State.DONE -> "#ffb8995a"
                sb == State.SIDE -> "#ffa48ad6"
                workable(sb) -> "#ff7fe3f0"
                else -> "#ff3a4664"
            }
            val ax = nodeX(a); val ay = nodeY(a); val bx = nodeX(e); val by = nodeY(e)
            val len = kotlin.math.hypot(bx - ax, by - ay)
            var t = R + 10; var k = 0
            while (t < len - R - 8) {
                val visible = fresh != a.id || now - freshAt > 300 + k * 30
                if (visible) rect("d-${a.id}-${e.id}-$k", ax + (bx - ax) * t / len - 2, ay + (by - ay) * t / len - 2, 4.0, 4.0, color, 2)
                t += 11; k++
            }
        }
        for (e in ENTRIES) medallion(e, now)
        hovered?.takeIf { it != selected }?.let { tooltip(GrimoireBook.entry(it)) }
        legend()
    }

    private fun medallion(e: Entry, now: Long) {
        val st = state(e)
        val cx = nodeX(e); val cy = nodeY(e)
        val sel = e.id == selected
        val hot = e.id == hovered && !sel
        val ring = when (st) {
            State.DONE -> GOLD; State.OPEN, State.GOAL_OPEN -> CYAN; State.SIDE -> VIOLET
            State.GOAL -> "#ffa0503e"; State.LOCKED, State.HIDDEN -> "#ff3e4558"
        }
        val grow = if (fresh == e.id) ((now - freshAt) / 450.0).coerceIn(0.25, 1.0) else 1.0
        val r = R * grow * if (sel) 1.08 else 1.0
        round("n-${e.id}-disc", cx - r, cy - r, r * 2, r * 2, r, if (st == State.DONE) "#1a1608" else "#0c1222", 1.0, 4)
        sprite("n-${e.id}-ring", "v3_ring", cx - r, cy - r, r * 2, r * 2, 9, if (st == State.HIDDEN) "#ff2e3446" else ring)
        if (sel) sprite("n-${e.id}-ring2", "v3_ring", cx - r - 8, cy - r - 8, r * 2 + 16, r * 2 + 16, 9, "#ffeafcff", 0.7)
        if (hot) sprite("n-${e.id}-ring3", "v3_ring", cx - r - 5, cy - r - 5, r * 2 + 10, r * 2 + 10, 9, ring, 0.5)
        if (st != State.HIDDEN) {
            val s = 64 * grow
            sprite("n-${e.id}-i", e.icon, cx - s / 2, cy - s / 2, s, s, 10, if (st == State.LOCKED || st == State.GOAL) "#ff8a8a96" else null)
        } else text("n-${e.id}-q", cx - 30, cy - 15, 60.0, "?", "v3b", 24.0, "#5E6A8A", 10, "center")
        // Every visible entry is named, as in the panel; locked names are dim. Only `?` entries stay unnamed.
        if (st != State.HIDDEN) {
            val label = e.title.removePrefix("寄り道：")
            val color = when (st) { State.DONE -> "#C9C3B8"; State.LOCKED, State.GOAL -> "#8A867E"; else -> "#ECE6DC" }
            text("n-${e.id}-l", cx - 90, cy + R + 12, 180.0, label, if (sel) "v3b" else "v3m", if (sel) 16.0 else 15.0, color, 10, "center")
        }
        if (canClaim(e)) {
            round("n-${e.id}-c", cx + R - 18, cy - R - 4, 24.0, 24.0, 12.0, "#C2554A", 1.0, 6)
            text("n-${e.id}-ct", cx + R - 18, cy - R - 2, 24.0, "!", "v3b", 16.0, "#FFF1DE", 11, "center")
        }
        if (progress.pinned == e.id && st != State.DONE) {
            round("n-${e.id}-p", cx - R - 6, cy - R - 4, 22.0, 22.0, 11.0, "#14506e", 1.0, 6)
            round("n-${e.id}-pd", cx - R + 1, cy - R + 3, 8.0, 8.0, 4.0, "#7FE3F0", 1.0, 7)
        }
        rect("n-${e.id}-hit", cx - R - 6, cy - R - 6, R * 2 + 12, R * 2 + 12, "#00000000", 12, "node:${e.id}", "#00000000")
    }

    /** Name, state and what is missing for the medallion under the pointer. */
    private fun tooltip(e: Entry) {
        val st = state(e)
        val (label, color) = LABEL.getValue(st)
        val title = if (st == State.HIDDEN) "まだ見えない" else e.title
        val hint = when {
            st == State.HIDDEN -> "前の項目を終えると分かる"
            st == State.LOCKED || st == State.GOAL -> lockReason(e)
            canClaim(e) -> "報酬を受け取れる"
            workable(st) -> e.goals.firstOrNull()?.text
            else -> null
        }
        val w = maxOf(width(title, "v3b", 17.0), width(label, "v3m", 13.0), hint?.let { width(it, "v3r", 14.0) } ?: 0.0) + 32
        val h = if (hint != null) 92.0 else 66.0
        val cx = nodeX(e); val cy = nodeY(e)
        val x = if (cx + R + 16 + w > MAP_X + MAP_W - 12) cx - R - 16 - w else cx + R + 16
        val y = (cy - h / 2).coerceIn(MAP_Y + 12, MAP_Y + MAP_H - h - 12)
        round("tip", x, y, w, h, 10.0, "#0D0E10", 0.96, 20)
        border("tip-l", x, y, w, h, 10.0, "#" + color.takeLast(6), 0.5, 21)
        text("tip-t", x + 16, y + 12, w, title, "v3b", 17.0, "#ECE6DC", 26)
        text("tip-s", x + 16, y + 40, w, label, "v3m", 13.0, color, 26)
        if (hint != null) text("tip-h", x + 16, y + 62, w, hint, "v3r", 14.0, "#A29E97", 26)
    }

    private fun legend() {
        var x = MAP_X + 32
        val y = MAP_Y + MAP_H - 38
        fun label(id: String, value: String) { text("lg-$id-t", x + 20, y - 6, 120.0, value, "v3r", 14.0, "#A29E97", 8); x += 20 + width(value, "v3r", 14.0) + 26 }
        for ((name, color) in listOf("完了" to GOLD, "進められる" to CYAN, "寄り道" to VIOLET, "幹のゴール" to "#ffa0503e", "未解放" to "#ff4a5068")) {
            round("lg-$name", x, y, 12.0, 12.0, 6.0, color, 1.0, 3)
            label(name, name)
        }
        text("lg-q", x - 2, y - 7, 16.0, "?", "v3b", 15.0, "#5E6A8A", 8, "center"); label("q", "まだ見えない")
        round("lg-c", x - 2, y - 2, 16.0, 16.0, 8.0, "#C2554A", 1.0, 3)
        text("lg-c-x", x - 2, y - 2, 16.0, "!", "v3b", 11.0, "#FFF1DE", 8, "center"); label("c", "報酬あり")
        round("lg-p", x - 2, y - 2, 16.0, 16.0, 8.0, "#14506e", 1.0, 3)
        round("lg-p-d", x + 2, y + 2, 8.0, 8.0, 4.0, "#7FE3F0", 1.0, 4); label("p", "ピン留め")
    }

    private fun sealedBranch() {
        val (name, icon) = BRANCHES[tab]
        val cx = MAP_X + MAP_W / 2; val cy = MAP_Y + MAP_H / 2 - 40
        // A faint map that is not readable yet: `?` medallions on dim paths around the seal.
        val ghosts = listOf(-2 to -1, -1 to 1, 1 to -1, 2 to 1, -3 to 0, 3 to 0)
        for ((i, g) in ghosts.withIndex()) {
            val gx = cx + g.first * 190.0; val gy = cy + g.second * 150.0
            round("ghost$i-disc", gx - 36, gy - 36, 72.0, 72.0, 36.0, "#0c1222", 1.0, 4)
            sprite("ghost$i-ring", "v3_ring", gx - 36, gy - 36, 72.0, 72.0, 9, "#ff232a3c")
            text("ghost$i-q", gx - 30, gy - 14, 60.0, "?", "v3b", 22.0, "#2e3650", 10, "center")
            val (ax, ay) = if (abs(g.first) == 3) cx + (g.first / 3) * 100.0 to cy else gx to gy
            val (bx, by) = if (abs(g.first) == 3) gx to gy else cx + (g.first / abs(g.first)) * 64.0 to cy
            val len = hypot(bx - ax, by - ay); var t = 36.0 + 10; var k = 0
            while (t < len - 36 - 10) { rect("ghost$i-d$k", ax + (bx - ax) * t / len - 2, ay + (by - ay) * t / len - 2, 4.0, 4.0, "#ff1c2234", 2); t += 11; k++ }
        }
        round("seal-disc", cx - 64, cy - 64, 128.0, 128.0, 64.0, "#0c1222", 1.0, 4)
        sprite("seal-ring", "v3_ring", cx - 64, cy - 64, 128.0, 128.0, 9, "#ff6f6b65")
        sprite("seal-i", icon, cx - 32, cy - 32, 64.0, 64.0, 10, "#ff8a8a96")
        text("seal-n", MAP_X, cy + 92, MAP_W, name, "v3b", 26.0, "#ECE6DC", 10, "center")
        text("seal-s", MAP_X, cy + 136, MAP_W, "幹の最後に「帰還の巻物」を作ると開く", "v3r", 15.0, "#A29E97", 10, "center")
    }

    private fun entryPanel(e: Entry, now: Long) {
        val st = state(e)
        val slide = ((now - selectedAt) / 220.0).coerceIn(0.0, 1.0)
        val x = SIDE_X + 28 + 18 * (1 - slide) * (1 - slide); var y = MAP_Y + 28; val w = SIDE_W - 56
        val (label, color) = LABEL.getValue(st)
        val lw = width(label, "v3m", 14.0) + 28
        round("e-state", x, y, lw, 30.0, 15.0, color, 0.18, 3)
        text("e-state-t", x, y + 6, lw, label, "v3m", 14.0, color, 8, "center")
        text("e-ch", x + lw + 12, y + 7, 240.0, e.chapter, "v3r", 14.0, "#8A867E", 8)
        y += 48
        // Illustration card and title.
        round("e-card", x, y, w, 196.0, 12.0, "#0c1222", 1.0, 3)
        border("e-card-l", x, y, w, 196.0, 12.0, "#ffffff", 0.08, 4)
        if (st != State.HIDDEN) {
            val hero = e.hero?.let { art.sprites[it] }
            if (hero != null) {
                val k = minOf(240.0 / hero.width, 170.0 / hero.height).let { if (it >= 2) 2.0 else 1.0 }
                sprite("e-hero", e.hero, x + w / 2 - hero.width * k / 2, y + 98 - hero.height * k / 2, hero.width * k, hero.height * k, 6,
                    if (st == State.LOCKED || st == State.GOAL) "#ff8a8a96" else null)
            } else sprite("e-hero", e.icon, x + w / 2 - 48, y + 50, 96.0, 96.0, 6, if (st == State.LOCKED || st == State.GOAL) "#ff8a8a96" else null)
        } else text("e-hero-q", x, y + 70, w, "?", "v3b", 44.0, "#5E6A8A", 6, "center")
        y += 214
        text("e-title", x, y, w, if (st == State.HIDDEN) "まだ見えない" else e.title, "v3b", 26.0, "#ECE6DC", 8)
        y += 46
        val concept = if (st == State.HIDDEN) "前の項目を終えると、ここに何があるか分かる。" else e.concept
        val lines = wrap(concept, w, "v3r", 15.0)
        for ((i, line) in lines.withIndex()) text("e-c$i", x, y + i * 24, w, line, "v3r", 15.0, "#C9C3B8", 8)
        y += 24 * lines.size + 18
        if (st == State.HIDDEN) return
        fun section(id: String, title: String) {
            rect("$id-rule", x, y, w, 1.0, "#17ffffff", 3); y += 12
            text(id, x, y, w, title, "v3m", 14.0, "#8A867E", 8); y += 26
        }
        if (e.goals.isNotEmpty()) {
            section("e-g", "目標")
            // One check per goal; counted goals get their number on the right and a bar under the text.
            for ((i, g) in e.goals.withIndex()) {
                val n = if (st == State.DONE) g.target else progress.count(e.id, i).coerceAtMost(g.target)
                val ok = n >= g.target
                sprite("e-g$i-b", if (ok) "i16_check" else "i16_box", x - 4, y - 2, 32.0, 32.0, 8)
                text("e-g$i", x + 36, y, w - 36, g.text, "v3r", 15.0, if (ok) "#8A867E" else "#ECE6DC", 8)
                if (g.target > 1) {
                    text("e-g$i-n", x + 36, y + 1, w - 36, "$n / ${g.target}", "v3num", 15.0, if (ok) "#8A867E" else "#7FE3F0", 8, "right")
                    round("e-g$i-bar", x + 36, y + 26, w - 36, 4.0, 2.0, "#2A2C31", 1.0, 3)
                    round("e-g$i-fill", x + 36, y + 26, (w - 36) * n / g.target, 4.0, 2.0, if (ok) GOLD else CYAN, 1.0, 4)
                    y += 14
                }
                y += 28
            }
        }
        if (e.unlocks.isNotEmpty()) {
            section("e-u", "できるようになること")
            for ((i, u) in e.unlocks.withIndex()) {
                KIND_ICON[u.first]?.let { sprite("e-u$i-i", it, x - 4, y - 2, 32.0, 32.0, 8) }
                text("e-u$i-k", x + 36, y, 70.0, u.first, "v3m", 15.0, "#7FE3F0", 8)
                text("e-u$i-t", x + 106, y, w - 106, u.second, "v3r", 15.0, "#ECE6DC", 8); y += 28
            }
        }
        val next = GrimoireBook.children(e.id)
        if (next.isNotEmpty() && st != State.DONE) {
            section("e-n", "この先")
            var nx = x
            for ((i, c) in next.withIndex()) {
                val hidden = state(c) == State.HIDDEN
                val label = if (hidden) "?" else c.title.removePrefix("寄り道：")
                val cw = 40 + width(label, "v3r", 14.0) + 12
                round("e-n$i", nx, y - 4, cw, 36.0, 8.0, "#0c1222", 1.0, 3)
                border("e-n$i-l", nx, y - 4, cw, 36.0, 8.0, "#ffffff", 0.08, 4)
                if (hidden) text("e-n$i-q", nx + 4, y + 2, 32.0, "?", "v3b", 16.0, "#5E6A8A", 8, "center")
                else sprite("e-n$i-i", c.icon, nx + 4, y - 2, 32.0, 32.0, 8, "#ff8a8a96")
                text("e-n$i-t", nx + 40, y + 3, cw - 40, label, "v3r", 14.0, "#A29E97", 8)
                nx += cw + 8
            }
            y += 44
        }
        if (e.reward != null) {
            section("e-r", "報酬")
            rewardSlot = x to y
            round("e-r-slot", x, y, 48.0, 48.0, 6.0, "#0D0E10", 1.0, 3)
            border("e-r-slot-l", x, y, 48.0, 48.0, 6.0, "#ffffff", 0.08, 4)
            sprite("e-r-i", if (e.reward.kind == GrimoireBook.Reward.Kind.INK) "i16_ink" else "i16_coin", x + 8, y + 8, 32.0, 32.0, 8)
            text("e-r-t", x + 64, y + 12, w - 64, e.reward.label, "v3m", 18.0, "#ECE6DC", 8)
        }
        // Actions along the bottom of the panel, forge-style buttons.
        val by = MAP_Y + MAP_H - 28 - 64 - (if (workable(st)) 34 else 0)
        when {
            canClaim(e) -> {
                round("e-claim", x, by, w, 64.0, 10.0, "#E8C878", 1.0, 3, "claim", "#fff2d898")
                text("e-claim-t", x, by + 18, w, "報酬を受け取る", "v3b", 24.0, "#1A1208", 8, "center")
            }
            st == State.DONE && e.reward != null -> {
                round("e-claimed", x, by, w, 64.0, 10.0, "#1C1D22", 1.0, 3)
                text("e-claimed-t", x, by + 20, w, "受け取り済み", "v3m", 18.0, "#6F6B65", 8, "center")
            }
            workable(st) -> {
                val on = progress.pinned == e.id
                if (on) round("e-pin", x, by, w, 64.0, 10.0, "#1f6fa8", 0.35, 3, "pin", "#5531a0d0")
                else round("e-pin", x, by, w, 64.0, 10.0, "#101114", 0.9, 3, "pin", "#ff16202a")
                border("e-pin-l", x, by, w, 64.0, 10.0, "#7FE3F0", if (on) 0.9 else 0.55, 4)
                text("e-pin-t", x, by + 20, w, if (on) "画面にピン留め中" else "画面にピン留めする", "v3b", 20.0, "#7FE3F0", 8, "center")
                rect("e-test", x, by + 74, w, 26.0, "#00000000", 3, "achieve", "#10ffffff")
                text("e-test-t", x, by + 78, w, "試し：ワールドで達成させる", "v3r", 13.0, "#6F6B65", 8, "center")
            }
            st == State.LOCKED || st == State.GOAL -> {
                round("e-lock", x, by, w, 64.0, 10.0, "#101114", 0.6, 3)
                border("e-lock-l", x, by, w, 64.0, 10.0, "#ffffff", 0.06, 4)
                text("e-lock-t", x, by + 22, w, lockReason(e), "v3m", 16.0, "#8A867E", 8, "center")
            }
        }
    }

    /**
     * The claim: the slot is outlined in gold, five coins (or ink bottles) hop on staggered arcs to their
     * wallet pill, and the pill is outlined while its number counts up. Only opaque pieces: in the client a
     * soft glow sprite renders as a flat blob, which is what the first version looked like.
     */
    private fun claimEffect(now: Long) {
        val fx = claimFx ?: return
        val t = now - fx.at
        if (t > FLY_END + COUNT_MS + 600) { claimFx = null; return }
        val icon = if (fx.reward.kind == Reward.Kind.INK) "i16_ink" else "i16_coin"
        val (sx, sy) = rewardSlot
        val (pillX, pillW) = if (fx.reward.kind == Reward.Kind.INK) 1552.0 to 140.0 else 1704.0 to 160.0
        val px = pillX + 18; val py = 44.0
        fun arc(k: Double, lift: Double, spread: Double = 0.0): Pair<Double, Double> {
            val e = k * k * (3 - 2 * k)
            return (sx + 12 + (px - sx - 12) * e + spread * kotlin.math.sin(Math.PI * k)) to
                (sy + 12 + (py - sy - 12) * e - lift * kotlin.math.sin(Math.PI * k))
        }
        val landed = t >= FLY_END
        // The slot outline turns gold as the reward leaves; the pill outline as it arrives.
        if (tab == 0 && t < 450) border("fx-slot", sx - 3, sy - 3, 54.0, 54.0, 8.0, GOLD, 1.0, 30, 3.0)
        if (landed && t < FLY_END + COUNT_MS + 300) border("fx-pill", pillX, 34.0, pillW, 44.0, 22.0, GOLD, 1.0, 4, 2.0)
        if (landed) text("fx-plus", pillX, 84.0, pillW, "+${fx.to - fx.from}", "v3num", 19.0, "#E8C878", 30, "center")
        for (i in 0 until 5) {
            val k = (t - 150 - i * 70) / 650.0
            if (k !in 0.0..1.0) continue
            val (x, y) = arc(k, 150.0 + i * 12, (i - 2) * 26.0)
            val size = 28 - 8 * k
            sprite("fx-c$i", icon, x, y, size, size, 31)
        }
    }

    private fun branchPanel() {
        val (name, _) = BRANCHES[tab]
        val x = SIDE_X + 28; var y = MAP_Y + 28; val w = SIDE_W - 56
        round("b-state", x, y, 96.0, 30.0, 15.0, "#6f6b65", 0.22, 3)
        text("b-state-t", x, y + 6, 96.0, "封印中", "v3m", 14.0, "#A29E97", 8, "center")
        y += 52
        text("b-name", x, y, w, name, "v3b", 26.0, "#ECE6DC", 8); y += 50
        val lines = wrap(BRANCH_LINES[tab], w, "v3r", 15.0)
        for ((i, line) in lines.withIndex()) text("b-l$i", x, y + i * 24, w, line, "v3r", 15.0, "#C9C3B8", 8)
        y += 24 * lines.size + 18
        rect("b-rule", x, y, w, 1.0, "#17ffffff", 3); y += 14
        for ((i, line) in wrap("幹の最後に「帰還の巻物」を作ると、この枝の地図が開く。", w, "v3r", 15.0).withIndex())
            text("b-s$i", x, y + i * 24, w, line, "v3r", 15.0, "#8A867E", 8)
    }

    /** Key hints in the forge's bottom-right corner; the close hint also closes on click. */
    private fun footer() {
        var x = 1864.0
        for ((key, what) in listOf("Shift" to "閉じる", "左クリック" to "選択")) {
            val ww = width(what, "v3r", 13.0); val kw = width(key, "v3num", 13.0) + 16
            x -= ww; text("f-$key-t", x, 1030.0, ww + 2, what, "v3r", 13.0, "#A29E97", 8)
            x -= kw + 8
            round("f-$key", x, 1024.0, kw, 26.0, 5.0, "#1E2025", 1.0, 2)
            border("f-$key-l", x, 1024.0, kw, 26.0, 5.0, "#3A3C42", 1.0, 3)
            text("f-$key-k", x, 1029.0, kw, key, "v3num", 13.0, "#ECE6DC", 8, "center")
            x -= 28
        }
        rect("f-close", 1760.0, 1018.0, 104.0, 40.0, "#00000000", 3, "close", "#10ffffff")
    }

    /** Greedy line breaking by measured width (Japanese has no spaces). */
    private fun wrap(value: String, w: Double, family: String, size: Double): List<String> {
        val lines = mutableListOf<String>(); var line = StringBuilder()
        for (c in value) {
            // Closing punctuation never starts a line (kinsoku); it may hang past the edge instead.
            if (width(line.toString() + c, family, size) > w && line.isNotEmpty() && c !in "、。」）") { lines += line.toString(); line = StringBuilder() }
            line.append(c)
        }
        if (line.isNotEmpty()) lines += line.toString()
        return lines
    }

    companion object {
        private const val SCALE = 800.0 / 1920.0
        private const val TOP = (480.0 - 1080.0 * SCALE) / 2
        private const val GLYPH_LIFT = 5
        private const val MAP_X = 56.0
        private const val MAP_Y = 116.0
        private const val MAP_W = 1356.0
        private const val MAP_H = 880.0
        private const val SIDE_X = 1432.0
        private const val SIDE_W = 432.0
        private const val R = 44.0
        private const val FLY_END = 900L
        private const val COUNT_MS = 600L
        private const val PANEL = "#141518"
        private const val BACKDROP = "#090b14"
        private const val GOLD = "#ffe8c878"
        private const val CYAN = "#ff7fe3f0"
        private const val VIOLET = "#ffb49ae8"
        private val STARS: List<Triple<Double, Double, Double>> = run {
            val rnd = java.util.Random(7)
            List(110) { Triple(rnd.nextDouble() * 1920, 100 + rnd.nextDouble() * 940, if (rnd.nextInt(6) == 0) 4.0 else 2.0) }
        }
        private val KIND_ICON = mapOf("開く" to "i16_open", "見える" to "i16_see", "置ける" to "i16_place", "作れる" to "i16_craft", "もらえる" to "i16_get")
        private val LABEL = mapOf(
            State.DONE to ("完了" to "#E8C878"), State.OPEN to ("進められる" to "#7FE3F0"), State.GOAL_OPEN to ("進められる" to "#7FE3F0"),
            State.SIDE to ("寄り道" to "#B49AE8"), State.LOCKED to ("未解放" to "#8A867E"), State.HIDDEN to ("まだ見えない" to "#6F7FA8"),
            State.GOAL to ("幹のゴール" to "#E58B80"))
    }
}
