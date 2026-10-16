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

/**
 * 魔術書: the magic path's quest book (FTB Quests / Thaumonomicon role), on the camera display like the
 * forge and the 開拓大陸 map. It shares their grammar — header with title, tabs and wallet pills,
 * rounded panels, M PLUS 2 text, key hints — and gets its magic from colour: a night sky with soft
 * cyan clouds, glowing ring medallions for entries, gold for done and cyan for what can be worked on.
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
            sprites = parse(json, "sprites") + parse(forgeFontMap, null).filterKeys { it.startsWith("v3_round_") || it.startsWith("v3_ring") || it == "v3_glow" }
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
                progress.claimed += e.id
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
        val style = buildMap { put("background-color", color); if (hover != null) put("hover-background-color", hover) }
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
    private fun page(family: String, size: Double) = "$family${size.toInt()}".takeIf(Polish05FontMetrics::has) ?: family
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
    /** Forge-style rounded fill: a cross of fills plus four tinted quarter discs. */
    private fun round(id: String, x: Double, y: Double, w: Double, h: Double, r: Double, color: String, alpha: Double, depth: Int,
                      action: String? = null, hover: String? = null) {
        val rr = r.coerceAtMost(minOf(w, h) / 2)
        val c = argb(color, alpha)
        rect("$id-c", x + rr, y, w - 2 * rr, h, c, depth, action, hover)
        rect("$id-l", x, y + rr, rr, h - 2 * rr, c, depth)
        rect("$id-r", x + w - rr, y + rr, rr, h - 2 * rr, c, depth)
        for ((k, cx, cy) in listOf(Triple("tl", x, y), Triple("tr", x + w - rr, y), Triple("bl", x, y + h - rr), Triple("br", x + w - rr, y + h - rr)))
            sprite("$id-$k", "v3_round_$k", cx, cy, rr, rr, depth + GLYPH_LIFT, color, alpha)
    }
    /** Forge-style 1px outline on a rounded box. */
    private fun border(id: String, x: Double, y: Double, w: Double, h: Double, r: Double, color: String, alpha: Double, depth: Int, t: Double = 1.0) {
        val c = argb(color, alpha)
        rect("$id-t", x + r, y, w - 2 * r, t, c, depth); rect("$id-b", x + r, y + h - t, w - 2 * r, t, c, depth)
        rect("$id-l", x, y + r, t, h - 2 * r, c, depth); rect("$id-r", x + w - t, y + r, t, h - 2 * r, c, depth)
        val ring = if (r >= 12) "v3_ring14" else "v3_ring10"
        for ((k, cx, cy) in listOf(Triple("tl", x, y), Triple("tr", x + w - r, y), Triple("bl", x, y + h - r), Triple("br", x + w - r, y + h - r)))
            sprite("$id-$k", "${ring}_$k", cx, cy, r, r, depth + GLYPH_LIFT, color, alpha)
    }
    private fun panel(id: String, x: Double, y: Double, w: Double, h: Double, r: Double, depth: Int, fill: String = PANEL, alpha: Double = 0.9) {
        round(id, x, y, w, h, r, fill, alpha, depth)
        border("$id-line", x, y, w, h, r, "#ffffff", 0.09, depth + 1)
    }

    override fun scene(light: ForgeLightPhase): UiScene {
        nodes.clear()
        val now = clock()
        rect("backdrop", -400.0, -200.0, 2720.0, 1480.0, "#ff090b14", -8)
        sky(now)
        header()
        panel("map", MAP_X, MAP_Y, MAP_W, MAP_H, 18.0, 1, "#0c1020", 0.55)
        if (tab == 0) trunkMap(now) else sealedBranch()
        panel("side", SIDE_X, MAP_Y, SIDE_W, MAP_H, 18.0, 1)
        if (tab == 0) entryPanel(GrimoireBook.entry(selected), now) else branchPanel()
        footer()
        return UiScene(800.0, 480.0, nodes.toList())
    }

    /** Night sky: soft cyan and indigo clouds (the forge's glow sprite, tinted) and a fixed scatter of stars. */
    private fun sky(now: Long) {
        for ((i, c) in CLOUDS.withIndex()) sprite("cloud$i", "v3_glow", c.x - c.s / 2, c.y - c.s / 2, c.s, c.s, -7, c.color, c.a)
        for ((i, s) in STARS.withIndex()) {
            val twinkle = ((now / 600 + i) % 7 == 0L)
            rect("star$i", s.first, s.second, s.third, s.third, if (twinkle) "#ffeafcff" else if (i % 3 == 0) "#ff9fe7f4" else "#ff5e7aa8", -7)
        }
    }

    private fun header() {
        // Title block, as the forge's 鍛冶師の仕事場.
        round("h-slot", 56.0, 30.0, 52.0, 52.0, 6.0, "#0D0E10", 1.0, 2)
        sprite("h-slot-i", "ic_book", 66.0, 40.0, 32.0, 32.0, 8)
        text("h-title", 124.0, 30.0, 300.0, "魔術書", "v3b", 24.0, "#ECE6DC", 8)
        text("h-sub", 124.0, 64.0, 300.0, "帰還港 · 研究室", "v3r", 13.0, "#A29E97", 8)
        // Branch tabs in the middle, like 強化 / 精錬 / 製作.
        var x = 960 - BRANCHES.sumOf { width(it.first, "v3b", 20.0) + 36 } / 2
        BRANCHES.forEachIndexed { i, (name, _) ->
            val on = i == tab
            val w = width(name, "v3b", 20.0)
            text("tab$i", x, 44.0, w, name, if (on) "v3b" else "v3m", if (on) 20.0 else 18.0, if (on) "#ECE6DC" else if (i == 0) "#A29E97" else "#6F6B65", 8)
            if (on) rect("tab$i-u", x, 82.0, w, 3.0, CYAN, 8)
            rect("tab$i-hit", x - 10, 34.0, w + 20, 56.0, "#00000000", 9, "tab:$i", "#14ffffff")
            x += w + 36
        }
        // Wallet pills on the right.
        fun pill(id: String, x: Double, w: Double) { round(id, x, 34.0, w, 44.0, 22.0, "#101114", 0.86, 2); border("$id-l", x, 34.0, w, 44.0, 22.0, "#ffffff", 0.09, 3) }
        pill("p-rank", 1300.0, 240.0)
        text("p-rank-k", 1320.0, 42.0, 100.0, "魔術熟練", "v3r", 12.0, "#A29E97", 8)
        round("p-rank-bar", 1320.0, 62.0, 96.0, 4.0, 2.0, "#2A2C31", 1.0, 3)
        round("p-rank-fill", 1320.0, 62.0, (96.0 * progress.rankXp / GrimoireBook.RANK_XP).roundToInt().toDouble(), 4.0, 2.0, CYAN, 1.0, 4)
        text("p-rank-v", 1430.0, 44.0, 80.0, "Rank ${progress.rank}", "v3num", 19.0, "#ECE6DC", 8)
        text("p-rank-x", 1500.0, 49.0, 40.0, "${progress.rankXp}", "v3num", 12.0, "#A29E97", 8)
        pill("p-ink", 1552.0, 140.0)
        sprite("p-ink-i", "i16_ink", 1570.0, 44.0, 24.0, 24.0, 8)
        text("p-ink-k", 1600.0, 47.0, 50.0, "インク", "v3r", 13.0, "#A29E97", 8)
        text("p-ink-v", 1654.0, 44.0, 40.0, "${progress.inkTotal}", "v3num", 19.0, "#ECE6DC", 8)
        pill("p-silver", 1704.0, 160.0)
        sprite("p-silver-i", "i16_coin", 1722.0, 44.0, 24.0, 24.0, 8)
        text("p-silver-k", 1752.0, 47.0, 40.0, "銀貨", "v3r", 13.0, "#A29E97", 8)
        text("p-silver-v", 1792.0, 44.0, 70.0, String.format(Locale.US, "%,d", progress.silver), "v3num", 19.0, "#ECE6DC", 8)
    }

    private fun nodeX(e: Entry) = MAP_X + 96 + e.col * 166.0
    private fun nodeY(e: Entry) = MAP_Y + 96 + e.row * 176.0

    private fun trunkMap(now: Long) {
        val done = ENTRIES.count { state(it) == State.DONE }
        val claims = ENTRIES.count(::canClaim)
        text("m-title", MAP_X + 32, MAP_Y + 24, 200.0, "幹 · 基礎魔術", "v3b", 20.0, "#ECE6DC", 8)
        text("m-count", MAP_X + 32 + width("幹 · 基礎魔術", "v3b", 20.0) + 16, MAP_Y + 26, 120.0, "$done / ${ENTRIES.size}", "v3num", 19.0, "#A29E97", 8)
        if (claims > 0) {
            val label = "受け取れる報酬 $claims"
            val w = width(label, "v3m", 14.0) + 36
            round("m-claim", MAP_X + MAP_W - 32 - w, MAP_Y + 22, w, 30.0, 15.0, "#C2554A", 0.22, 3)
            rect("m-claim-dot", MAP_X + MAP_W - 32 - w + 14, MAP_Y + 34, 6.0, 6.0, "#ffe58b80", 4)
            text("m-claim-t", MAP_X + MAP_W - 32 - w + 26, MAP_Y + 28, w, label, "v3m", 14.0, "#E58B80", 8)
        }
        for ((name, col) in CHAPTERS) {
            val cx = MAP_X + 96 + col * 166.0
            text("ch-$name", cx - 60, MAP_Y + 66, 120.0, name, "v3m", 14.0, "#6F7FA8", 8, "center")
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
        // Soft halo: breathing for workable entries, steady under the selected one.
        if (workable(st) || sel) {
            val breath = 0.5 + 0.5 * kotlin.math.sin(now / 520.0 + e.col)
            val s = r * (if (sel) 3.6 else 3.0)
            sprite("n-${e.id}-halo", "v3_glow", cx - s / 2, cy - s / 2, s, s, 3, if (sel) "#ffffffff" else ring, if (sel) 0.35 else 0.22 + 0.25 * breath)
        }
        round("n-${e.id}-disc", cx - r, cy - r, r * 2, r * 2, r, if (st == State.DONE) "#1a1608" else "#0c1222", 1.0, 4)
        sprite("n-${e.id}-ring", "v3_ring", cx - r, cy - r, r * 2, r * 2, 9, if (st == State.HIDDEN) "#ff2e3446" else ring)
        if (sel) sprite("n-${e.id}-ring2", "v3_ring", cx - r - 8, cy - r - 8, r * 2 + 16, r * 2 + 16, 9, "#ffeafcff", 0.7)
        if (hot) sprite("n-${e.id}-ring3", "v3_ring", cx - r - 5, cy - r - 5, r * 2 + 10, r * 2 + 10, 9, ring, 0.5)
        if (st != State.HIDDEN) {
            val s = 48 * grow
            sprite("n-${e.id}-i", e.icon, cx - s / 2, cy - s / 2, s, s, 10, if (st == State.LOCKED || st == State.GOAL) "#ff8a8a96" else null)
        } else text("n-${e.id}-q", cx - 30, cy - 15, 60.0, "?", "v3b", 24.0, "#5E6A8A", 10, "center")
        if (st != State.LOCKED && st != State.HIDDEN) {
            val label = e.title.removePrefix("寄り道：")
            text("n-${e.id}-l", cx - 90, cy + R + 12, 180.0, label, if (sel) "v3b" else "v3m", if (sel) 16.0 else 15.0,
                if (st == State.DONE) "#C9C3B8" else "#ECE6DC", 10, "center", shadow = true)
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

    private fun legend() {
        var x = MAP_X + 32
        for ((label, color) in listOf("完了" to GOLD, "進められる" to CYAN, "寄り道" to VIOLET, "未解放" to "#ff4a5068")) {
            round("lg-$label", x, MAP_Y + MAP_H - 38, 12.0, 12.0, 6.0, color, 1.0, 3)
            text("lg-$label-t", x + 20, MAP_Y + MAP_H - 44, 120.0, label, "v3r", 14.0, "#A29E97", 8)
            x += 20 + width(label, "v3r", 14.0) + 26
        }
    }

    private fun sealedBranch() {
        val (name, icon) = BRANCHES[tab]
        val cx = MAP_X + MAP_W / 2; val cy = MAP_Y + MAP_H / 2 - 40
        sprite("seal-halo", "v3_glow", cx - 180, cy - 180, 360.0, 360.0, 3, "#ff6f5aa8", 0.35)
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
        sprite("e-card-halo", "v3_glow", x + w / 2 - 150, y + 98 - 150, 300.0, 300.0, 5, if (st == State.DONE) GOLD else CYAN, 0.18)
        if (st != State.HIDDEN) {
            val hero = e.hero?.let { art.sprites[it] }
            if (hero != null) {
                val k = minOf(240.0 / hero.width, 170.0 / hero.height)
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
            for ((i, g) in e.goals.withIndex()) {
                val ok = st == State.DONE
                val label = if (g.target > 1) "${g.text} ${progress.count(e.id, i)}/${g.target}" else g.text
                sprite("e-g$i-b", if (ok) "i16_check" else "i16_box", x, y + 2, 20.0, 20.0, 8)
                text("e-g$i", x + 30, y, w - 30, label, "v3r", 15.0, if (ok) "#8A867E" else "#ECE6DC", 8); y += 28
            }
        }
        if (e.unlocks.isNotEmpty()) {
            section("e-u", "できるようになること")
            for ((i, u) in e.unlocks.withIndex()) {
                KIND_ICON[u.first]?.let { sprite("e-u$i-i", it, x, y + 2, 20.0, 20.0, 8) }
                text("e-u$i-k", x + 30, y, 70.0, u.first, "v3m", 15.0, "#7FE3F0", 8)
                text("e-u$i-t", x + 100, y, w - 100, u.second, "v3r", 15.0, "#ECE6DC", 8); y += 28
            }
        }
        if (e.reward != null) {
            section("e-r", "報酬")
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
            if (width(line.toString() + c, family, size) > w && line.isNotEmpty()) { lines += line.toString(); line = StringBuilder() }
            line.append(c)
        }
        if (line.isNotEmpty()) lines += line.toString()
        return lines
    }

    private data class Cloud(val x: Double, val y: Double, val s: Double, val color: String, val a: Double)

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
        private const val R = 40.0
        private const val PANEL = "#141518"
        private const val GOLD = "#ffe8c878"
        private const val CYAN = "#ff7fe3f0"
        private const val VIOLET = "#ffb49ae8"
        private val CLOUDS = listOf(
            Cloud(520.0, 560.0, 900.0, "#ff23497a", 0.55), Cloud(980.0, 420.0, 760.0, "#ff2f7a9a", 0.32),
            Cloud(760.0, 760.0, 620.0, "#ff1b2e62", 0.6), Cloud(1260.0, 640.0, 700.0, "#ff2a2f6e", 0.45),
            Cloud(300.0, 300.0, 520.0, "#ff1a2a5a", 0.5), Cloud(1600.0, 300.0, 600.0, "#ff1b2e62", 0.35))
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
