package dev.projects.server.coreloop

import com.google.gson.JsonParser
import dev.projects.webui.Box
import dev.projects.webui.ForgeLightPhase
import dev.projects.webui.ForgeUiFlow
import dev.projects.webui.Polish05FontMetrics
import dev.projects.webui.UiNode
import dev.projects.webui.UiScene
import dev.projects.webui.UiSprite

/**
 * 魔術書: the magic path's quest book, drawn like the 開拓大陸 map (camera display, mouse pointer).
 * The art is the approved v7 "magic window" (assets/ui/grimoire), authored for a 1280×720 frame and
 * placed at 1.5× so every sprite shares one texel size inside the 1920×1080 design space.
 *
 * Left: branch tabs. Middle: the trunk quest map on a night-sky nebula. Right: a pinned parchment page
 * for the selected entry, with reward claiming and pinning. Quest progress here is still sample data;
 * completion will come from the world (FTB-Quests style), so the page only offers a test shortcut.
 */
internal class GrimoireFlow(private val art: Art, private val leave: () -> Unit) : ForgeUiFlow {
    /** Parsed once at startup from polish05/grimoire.json. */
    class Art(json: ByteArray) {
        data class Part(val sprite: UiSprite, val x: Int, val y: Int)
        data class Layout(val width: Int, val height: Int, val parts: List<Part>)
        val sprites: Map<String, UiSprite>
        val layouts: Map<String, Layout>
        init {
            val root = JsonParser.parseString(json.toString(Charsets.UTF_8)).asJsonObject
            sprites = root.getAsJsonObject("sprites").entrySet().associate { (name, v) ->
                val o = v.asJsonObject
                name to UiSprite(o["char"].asString, o["font"].asString, o["width"].asInt, o["height"].asInt)
            }
            layouts = root.getAsJsonObject("layouts").entrySet().associate { (name, v) ->
                val o = v.asJsonObject
                name to Layout(o["width"].asInt, o["height"].asInt, o.getAsJsonArray("tiles").map { t ->
                    t.asJsonObject.let { Part(sprites.getValue(it["name"].asString), it["x"].asInt, it["y"].asInt) }
                })
            }
        }
    }

    enum class State { DONE, OPEN, SIDE, LOCKED, HIDDEN, GOAL, GOAL_OPEN }

    data class Entry(
        val id: String, val col: Int, val row: Int, val icon: String, val title: String, val chapter: String,
        val concept: String, val goals: List<String>, val unlocks: List<Pair<String, String>>, val reward: String,
        val parents: List<String>, val done: Boolean = false, val side: Boolean = false, val mystery: Boolean = false,
        val goal: Boolean = false, val hero: String? = null,
    )

    override val view = "grimoire"
    override val muted = false
    override val operationActive = false
    override val ownsEffects = true
    override val live = true

    private var tab = 0
    private var selected = "n5"
    private var pinned: String? = "n5"
    private val achieved = mutableSetOf<String>()
    private val claimed = mutableSetOf("n1", "n2", "n3")
    private var turnedAt = System.currentTimeMillis()
    private var fresh: String? = null
    private var freshAt = 0L

    private fun state(e: Entry): State {
        val done = e.done || e.id in achieved
        val ready = e.parents.all { p -> ENTRIES.first { it.id == p }.let { it.done || it.id in achieved } }
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
    private fun canClaim(e: Entry) = state(e) == State.DONE && e.reward.isNotEmpty() && e.id !in claimed

    override fun action(action: String): Boolean {
        when {
            action.startsWith("node:") -> {
                val id = action.removePrefix("node:")
                if (ENTRIES.none { it.id == id } || id == selected) return false
                selected = id; turnedAt = System.currentTimeMillis()
            }
            action.startsWith("tab:") -> {
                val t = action.removePrefix("tab:").toIntOrNull()?.takeIf { it in BRANCHES.indices } ?: return false
                if (t == tab) return false
                tab = t; turnedAt = System.currentTimeMillis()
            }
            action == "claim" -> {
                val e = ENTRIES.first { it.id == selected }
                if (!canClaim(e)) return false
                claimed += e.id
            }
            action == "pin" -> pinned = if (pinned == selected) null else selected
            action == "achieve" -> {
                val e = ENTRIES.first { it.id == selected }
                if (state(e) !in setOf(State.OPEN, State.SIDE, State.GOAL_OPEN)) return false
                achieved += e.id; fresh = e.id; freshAt = System.currentTimeMillis()
            }
            action == "close" -> leave()
            else -> return false
        }
        return true
    }

    // ------------------------------------------------------------------ drawing (1280 art units → design units)

    private val nodes = mutableListOf<UiNode>()
    private fun box(x: Double, y: Double, w: Double, h: Double) = Box(x * K * SCALE, TOP + y * K * SCALE, w * K * SCALE, h * K * SCALE)
    private fun sprite(id: String, name: String, x: Double, y: Double, w: Double? = null, h: Double? = null, depth: Int, tint: String? = null) {
        val s = art.sprites[name] ?: return
        nodes += UiNode(id, box(x, y, w ?: s.width.toDouble(), h ?: s.height.toDouble()), "",
            if (tint != null) mapOf("sprite-color" to tint) else emptyMap(), null, null, true, depth, s)
    }
    private fun layout(id: String, name: String, x: Double, y: Double, depth: Int) {
        val l = art.layouts[name] ?: return
        for (p in l.parts) nodes += UiNode("$id-${p.x}-${p.y}", box(x + p.x, y + p.y, p.sprite.width.toDouble(), p.sprite.height.toDouble()),
            "", emptyMap(), null, null, true, depth, p.sprite)
    }
    private fun rect(id: String, x: Double, y: Double, w: Double, h: Double, color: String, depth: Int, action: String? = null, hover: String? = null) {
        val style = buildMap { put("background-color", color); if (hover != null) put("hover-background-color", hover) }
        nodes += UiNode(id, box(x, y, w, h), "", style, action, null, true, depth)
    }
    /** [size] in art pixels (16 or 32). Text is DotGothic16, the font the v7 art was drawn against. */
    private fun text(id: String, x: Double, y: Double, w: Double, value: String, size: Int, color: String, depth: Int,
                     align: String = "left", shadow: Boolean = false) {
        val design = (size * K).toInt()
        val page = "v3num$design".takeIf(Polish05FontMetrics::has) ?: "v3num40".takeIf { size >= 32 && Polish05FontMetrics.has(it) } ?: "v3num"
        val px = if (page == "v3num") 32.0 else page.removePrefix("v3num").toDouble()
        nodes += UiNode(id, box(x, y, w, px / K * 1.25), value, buildMap {
            put("font-size", "${px * SCALE}px"); put("color", if (color.length == 9) "#" + color.takeLast(6) else color); put("text-align", align)
            put("font-family", "projects_ui_polish05:$page")
            if (shadow) put("text-shadow", "true")
        }, null, null, true, depth)
    }
    private fun width(value: String, size: Int): Double {
        val page = "v3num${(size * K).toInt()}".takeIf(Polish05FontMetrics::has) ?: "v3num"
        val px = if (page == "v3num") 32.0 else page.removePrefix("v3num").toDouble()
        return Polish05FontMetrics.advance(value, page) / Polish05FontMetrics.SIZE * px / K
    }

    override fun scene(light: ForgeLightPhase): UiScene {
        nodes.clear()
        val now = System.currentTimeMillis()
        rect("bg", 0.0, 0.0, 1280.0, 720.0, "#ff08080d", 0)
        tabs()
        layout("neb", NEBULA[tab], MAP_X, MAP_Y, 1)
        if (tab == 0) trunkMap(now) else sealedBranch()
        layout("frame", "frame", 0.0, 0.0, 10)
        text("plaque", 470.0, 72.0, 340.0, "魔術書", 16, "#fff0cc5a", 11, "center", shadow = true)
        layout("page", "page", PAGE_X, PAGE_Y, 12)
        if (tab == 0) page(ENTRIES.first { it.id == selected }) else branchPage()
        rect("close", 1196.0, 70.0, 40.0, 32.0, "#00000000", 13, "close", "#30ffffff")
        text("close-t", 1196.0, 74.0, 40.0, "×", 16, "#ffd4a838", 14, "center", shadow = true)
        return UiScene(800.0, 480.0, nodes.toList())
    }

    private fun tabs() {
        BRANCHES.forEachIndexed { i, (name, icon) ->
            val on = i == tab
            val x = if (on) 0.0 else 8.0; val y = 148.0 + i * 64
            sprite("tab$i", if (on) "tab_on" else "tab", x, y, depth = 9)
            sprite("tab$i-i", icon, x + 12, y + 10, depth = 9)
            rect("tab$i-hit", x, y, 48.0, 52.0, "#00000000", 9, "tab:$i", "#20ffffff")
        }
    }

    private fun trunkMap(now: Long) {
        val done = ENTRIES.count { state(it) == State.DONE }
        val claims = ENTRIES.count(::canClaim)
        text("h-trunk", MAP_X + 20, MAP_Y + 12, 40.0, "幹", 32, "#fff4d870", 5, shadow = true)
        text("h-sub", MAP_X + 60, MAP_Y + 22, 120.0, "基礎魔術", 16, "#fff0e0b0", 5, shadow = true)
        text("h-count", MAP_X + 150, MAP_Y + 22, 80.0, "$done / ${ENTRIES.size}", 16, "#ffa8c8e0", 5, shadow = true)
        if (claims > 0) text("h-claim", MAP_X + 230, MAP_Y + 22, 220.0, "! 受け取れる報酬 $claims", 16, "#fff08a6a", 5, shadow = true)
        for ((name, col) in CHAPTERS) {
            val cx = MAP_X + OX + col * SX + S / 2
            text("ch-$name", cx - 40, MAP_Y + 60, 80.0, name, 16, "#ff7a8ab0", 5, "center", shadow = true)
        }
        // Paths: dotted light between node centres, coloured by the state of the pair.
        for (e in ENTRIES) for (p in e.parents) {
            val a = ENTRIES.first { it.id == p }
            val sa = state(a); val sb = state(e)
            val color = when {
                sa != State.DONE -> "#ff2a3450"
                sb == State.DONE -> "#ffd4a838"
                sb == State.SIDE -> "#ffc8a8f0"
                sb == State.OPEN || sb == State.GOAL_OPEN -> "#ffa8ecf6"
                else -> "#ff3a4a6a"
            }
            val ax = MAP_X + OX + a.col * SX + S / 2; val ay = MAP_Y + OY + a.row * SY + S / 2
            val bx = MAP_X + OX + e.col * SX + S / 2; val by = MAP_Y + OY + e.row * SY + S / 2
            val len = kotlin.math.hypot(bx - ax, by - ay)
            var t = 30.0; var k = 0
            val anim = fresh == a.id
            while (t < len - 29) {
                val visible = !anim || now - freshAt > 300 + k * 45
                if (visible) rect("d-${a.id}-${e.id}-$k", ax + (bx - ax) * t / len - 2, ay + (by - ay) * t / len - 2, 4.0, 4.0, color, 2)
                t += 8; k++
            }
        }
        for (e in ENTRIES) {
            val st = state(e)
            val x = MAP_X + OX + e.col * SX; val y = MAP_Y + OY + e.row * SY
            val mount = when (st) {
                State.DONE -> "node_done"; State.OPEN, State.GOAL_OPEN -> "node_open"; State.SIDE -> "node_side"
                State.GOAL -> "node_goal"; State.HIDDEN -> "node_hidden"; State.LOCKED -> "node_locked"
            }
            // Ink spread: the freshly achieved node grows in over half a second.
            val grow = if (fresh == e.id) ((now - freshAt) / 500.0).coerceIn(0.2, 1.0) else 1.0
            val g = S * grow; val gx = x + (S - g) / 2; val gy = y + (S - g) / 2
            sprite("n-${e.id}", mount, gx, gy, g, g, 3)
            val pulse = (now / 450) % 2 == 0L
            if ((st == State.OPEN || st == State.GOAL_OPEN || st == State.SIDE) && pulse) sprite("n-${e.id}-glow", "node_glow", x, y, S, S, 4)
            if (st != State.HIDDEN) sprite("n-${e.id}-i", e.icon, x + 8 + 16 * (1 - grow), y + 8 + 16 * (1 - grow), 32.0 * grow, 32.0 * grow, 5,
                if (st == State.LOCKED || st == State.GOAL) "#8a8a96" else null)
            else text("n-${e.id}-q", x, y + 14, S, "?", 16, "#ff8a96c0", 5, "center")
            if (st != State.LOCKED && st != State.HIDDEN) {
                val label = e.title.removePrefix("寄り道：")
                text("n-${e.id}-l", x - 40, y + 52, S + 80, label, 16, if (st == State.DONE) "#ffd8c8a0" else "#ffc8f0f8", 5, "center", shadow = true)
            }
            if (canClaim(e)) {
                rect("n-${e.id}-c", x + 34, y - 6, 20.0, 20.0, "#ffb04a3a", 6)
                text("n-${e.id}-ct", x + 34, y - 4, 20.0, "!", 16, "#fffff4e0", 7, "center")
            }
            if (pinned == e.id && st != State.DONE) {
                rect("n-${e.id}-p", x, y - 12, 10.0, 8.0, "#ff49b4d9", 6); rect("n-${e.id}-pn", x + 4, y - 4, 2.0, 10.0, "#ffa0a0aa", 6)
            }
            if (selected == e.id) for ((k, cx, cy) in listOf(Triple("tl", x - 8, y - 8), Triple("tr", x + S - 2, y - 8),
                    Triple("bl", x - 8, y + S - 2), Triple("br", x + S - 2, y + S - 2))) {
                rect("sel-$k-h", if (k.endsWith("l")) cx else cx - 8, cy + if (k.startsWith("t")) 0.0 else 8.0, 10.0, 2.0, "#ffa8ecf6", 6)
                rect("sel-$k-v", cx + if (k.endsWith("l")) 0.0 else 8.0, if (k.startsWith("t")) cy else cy - 8, 2.0, 10.0, "#ffa8ecf6", 6)
            }
            rect("n-${e.id}-hit", x, y, S, S, "#00000000", 8, "node:${e.id}", "#18ffffff")
        }
        legend()
    }

    private fun legend() {
        var x = MAP_X + 20
        for ((label, color) in listOf("完了" to "#ffd4a838", "進められる" to "#ffa8ecf6", "寄り道" to "#ffb090e0", "未解放" to "#ff6a6a76")) {
            rect("lg-$label", x, MAP_Y + 542, 10.0, 10.0, color, 5)
            text("lg-$label-t", x + 16, MAP_Y + 536, 120.0, label, 16, "#ffc8d0e0", 5, shadow = true)
            x += 16 + width(label, 16) + 16
        }
    }

    private fun sealedBranch() {
        val (name, _) = BRANCHES[tab]
        sprite("seal", "seal", MAP_X + 306, MAP_Y + 200, 112.0, 112.0, 4)
        text("seal-n", MAP_X, MAP_Y + 330, MAP_W, name, 32, "#fff4d870", 5, "center", shadow = true)
        text("seal-s", MAP_X, MAP_Y + 378, MAP_W, "この枝は封じられている", 16, "#ffc8d8e8", 5, "center", shadow = true)
    }

    private fun page(e: Entry) {
        val st = state(e)
        val x = PAGE_X + 24; var y = PAGE_Y + 26; val w = 360.0
        val (label, ink, frame) = LABEL.getValue(st)
        text("pg-ch", x, y, 200.0, e.chapter, 16, "#ff7a5a3a", 13)
        val lw = width(label, 16) + 16
        rect("pg-st", x + w - lw, y - 2, lw, 24.0, frame, 13); rect("pg-st-in", x + w - lw + 2, y, lw - 4, 20.0, "#ffe6d6b0", 13)
        text("pg-st-t", x + w - lw, y, lw, label, 16, ink, 14, "center")
        y += 32
        // Illustration pasted on the page, then the title beside it.
        rect("pg-ill", x, y, 132.0, 120.0, "#ff7a5a3a", 13); rect("pg-ill-in", x + 2, y + 2, 128.0, 116.0, "#ffd2bb90", 13)
        if (st != State.HIDDEN) {
            val hero = e.hero?.let { art.sprites[it] }
            if (hero != null) {
                val k = minOf(120.0 / hero.width, 108.0 / hero.height)
                sprite("pg-hero", e.hero, x + 66 - hero.width * k / 2, y + 60 - hero.height * k / 2, hero.width * k, hero.height * k, 14)
            } else sprite("pg-hero", e.icon, x + 34, y + 28, 64.0, 64.0, 14)
        } else text("pg-hero-q", x, y + 40, 132.0, "?", 32, "#ff9c8460", 14, "center")
        text("pg-title", x + 146, y + 40, w - 146, if (st == State.HIDDEN) "？？？" else e.title, 32, "#ff2a1a10", 14)
        y += 132
        val concept = if (st == State.HIDDEN) "前の項目を終えると、ここに何があるか分かる。" else e.concept
        wrap(concept, w).forEachIndexed { i, line -> text("pg-c$i", x, y + i * 24, w, line, 16, "#ff4a3020", 14) }
        y += 24 * wrap(concept, w).size + 8
        if (st == State.HIDDEN) return
        fun divider(id: String) { layout(id, "div", x, y, 13); y += 14 }
        if (e.goals.isNotEmpty()) {
            divider("pg-d1")
            text("pg-g", x, y, w, "目標", 16, "#ff7a5a3a", 14); y += 24
            e.goals.forEachIndexed { i, g ->
                val ok = st == State.DONE
                sprite("pg-g$i-b", if (ok) "i16_check" else "i16_box", x, y + 4, depth = 14)
                text("pg-g$i", x + 24, y, w - 24, g, 16, if (ok) "#ff9c8460" else "#ff2a1a10", 14); y += 24
            }
        }
        if (e.unlocks.isNotEmpty()) {
            divider("pg-d2")
            text("pg-u", x, y, w, "できるようになること", 16, "#ff7a5a3a", 14); y += 24
            e.unlocks.forEachIndexed { i, (kind, what) ->
                KIND_ICON[kind]?.let { sprite("pg-u$i-i", it, x, y + 4, depth = 14) }
                text("pg-u$i-k", x + 24, y, 60.0, kind, 16, "#ff1f6fa8", 14)
                text("pg-u$i-t", x + 84, y, w - 84, what, 16, "#ff2a1a10", 14); y += 24
            }
        }
        if (e.reward.isNotEmpty()) {
            divider("pg-d3")
            text("pg-r", x, y + 4, 80.0, "報酬", 16, "#ff7a5a3a", 14)
            val rw = width(e.reward, 16)
            rect("pg-r-slot", x + w - rw - 38, y, 28.0, 28.0, "#ff9c8460", 13); rect("pg-r-slot-in", x + w - rw - 36, y + 2, 24.0, 24.0, "#ffcdb68c", 13)
            sprite("pg-r-i", if ("インク" in e.reward) "i16_ink" else "i16_coin", x + w - rw - 32, y + 6, depth = 14)
            text("pg-r-t", x + w - rw, y + 4, rw, e.reward, 16, "#ff2a1a10", 14, "right")
        }
        // Actions along the bottom of the page.
        val by = PAGE_Y + 568 - 26 - 44 - 30
        var bx = x
        if (canClaim(e)) {
            rect("pg-claim", bx, by, 150.0, 40.0, "#ff8a5a10", 14, "claim", "#ffb07a20")
            rect("pg-claim-in", bx + 2, by + 2, 146.0, 36.0, "#fff4d870", 14)
            text("pg-claim-t", bx, by + 10, 150.0, "報酬を受け取る", 16, "#ff2a1a10", 15, "center"); bx += 162
        } else if (st == State.DONE && e.reward.isNotEmpty()) {
            rect("pg-claimed", bx, by, 130.0, 40.0, "#ff8a5a10", 14); rect("pg-claimed-in", bx + 2, by + 2, 126.0, 36.0, "#ffe6d6b0", 14)
            text("pg-claimed-t", bx, by + 10, 130.0, "受け取り済み", 16, "#ff8a5a10", 15, "center"); bx += 142
        }
        if (st == State.OPEN || st == State.SIDE || st == State.GOAL_OPEN) {
            val on = pinned == e.id
            rect("pg-pin", bx, by, 130.0, 40.0, "#ff14506e", 14, "pin", "#ff1f6fa8")
            rect("pg-pin-in", bx + 2, by + 2, 126.0, 36.0, if (on) "#ff1f6fa8" else "#ffcfe4e0", 14)
            text("pg-pin-t", bx, by + 10, 130.0, if (on) "ピン留め中" else "ピン留めする", 16, if (on) "#ffe6f2ee" else "#ff14506e", 15, "center")
            rect("pg-test", x, by + 48, 220.0, 24.0, "#00000000", 14, "achieve", "#20000000")
            text("pg-test-t", x, by + 48, 220.0, "試し：ワールドで達成させる", 16, "#ff9c8460", 15)
        }
    }

    private fun branchPage() {
        val (name, _) = BRANCHES[tab]
        val x = PAGE_X + 24; var y = PAGE_Y + 26; val w = 360.0
        text("bp-k", x, y, w, "専門の枝", 16, "#ff7a5a3a", 13); y += 28
        text("bp-n", x, y, w, name, 32, "#ff2a1a10", 14); y += 52
        wrap(BRANCH_LINES[tab], w).forEachIndexed { i, l -> text("bp-l$i", x, y + i * 24, w, l, 16, "#ff4a3020", 14) }
        y += 24 * wrap(BRANCH_LINES[tab], w).size + 12
        layout("bp-d", "div", x, y, 13); y += 16
        wrap("幹の最後に「帰還の巻物」を作ると、封が割れてこの枝の地図が見えるようになる。", w)
            .forEachIndexed { i, l -> text("bp-s$i", x, y + i * 24, w, l, 16, "#ff7a5a3a", 14) }
    }

    /** Greedy line breaking by measured width (Japanese has no spaces). */
    private fun wrap(value: String, w: Double): List<String> {
        val lines = mutableListOf<String>(); var line = StringBuilder()
        for (c in value) {
            if (width(line.toString() + c, 16) > w && line.isNotEmpty()) { lines += line.toString(); line = StringBuilder() }
            line.append(c)
        }
        if (line.isNotEmpty()) lines += line.toString()
        return lines
    }

    companion object {
        private const val K = 1.5
        private const val SCALE = 800.0 / 1920.0
        private const val TOP = (480.0 - 1080.0 * SCALE) / 2
        private const val MAP_X = 76.0
        private const val MAP_Y = 116.0
        private const val MAP_W = 724.0
        private const val PAGE_X = 820.0
        private const val PAGE_Y = 116.0
        private const val OX = 36.0
        private const val OY = 4.0
        private const val SX = 86.0
        private const val SY = 118.0
        private const val S = 48.0
        private val NEBULA = listOf("neb_trunk", "neb_koku", "neb_chu", "neb_sho", "neb_ren", "neb_chi", "neb_mad")
        internal val BRANCHES = listOf("幹" to "ic_book", "刻印術" to "ic_scroll", "注入" to "ic_ignis", "召喚" to "ic_mortuus",
            "錬金" to "ic_victus", "地脈術" to "ic_terra", "魔導工学" to "ic_potentia")
        private val BRANCH_LINES = listOf("",
            "帰還・加速・転移などの巻物。使えば消える、魔術の効果そのもの。",
            "武器や防具に、魔術ならではの性質を与える・付け替える。",
            "ボスを呼ぶ封印の鍵を作る。戦闘職がボスに挑むための入口。",
            "薬や食べ物。体に入れる魔術。",
            "領地の結界、天候、転移の網。世界そのものに手を出す。",
            "仕分けや自動化の装置。工房を工場のように組む。")
        private val CHAPTERS = listOf("序章" to 1.0, "一章" to 3.5, "二章" to 5.0, "三章" to 6.0, "終章" to 7.0)
        private val KIND_ICON = mapOf("開く" to "i16_open", "見える" to "i16_see", "置ける" to "i16_place", "作れる" to "i16_craft", "もらえる" to "i16_get")
        private val LABEL = mapOf(
            State.DONE to Triple("完了", "#ff8a5a10", "#ffd4a838"),
            State.OPEN to Triple("進められる", "#ff14506e", "#ff49b4d9"),
            State.GOAL_OPEN to Triple("進められる", "#ff14506e", "#ff49b4d9"),
            State.SIDE to Triple("寄り道", "#ff5e4880", "#ff8a6ab0"),
            State.LOCKED to Triple("未解放", "#ff7a5a3a", "#ff9c8460"),
            State.HIDDEN to Triple("まだ見えない", "#ff9c8460", "#ffb8a078"),
            State.GOAL to Triple("幹のゴール", "#ffa03024", "#ffa03024"))
        internal val ENTRIES = listOf(
            Entry("n1", 0, 2, "ic_book", "古い手帳", "序章 観測", "光る草に挟まっていた手帳。最初のページには一文だけ。「すべての物は、目に見えない性質（相）でできている」",
                listOf("光る素材を拾う"), listOf("開く" to "魔術書"), "", emptyList(), done = true),
            Entry("n2", 1, 1, "ic_goggles", "観測の眼鏡", "序章 観測", "かぶっている間だけ、見ている物の相が見える。",
                listOf("眼鏡を作る（ガラス2・銅1）"), listOf("見える" to "照準の先の相"), "", listOf("n1"), done = true),
            Entry("n3", 1, 3, "ic_desk", "研究机", "序章 観測", "読めない相は、机の上で考えると分かる。最初の「自分の魔術の場所」。",
                listOf("研究机を置く"), listOf("置ける" to "研究机"), "", listOf("n1"), done = true, hero = "hero_desk"),
            Entry("n4", 2, 2, "ic_lux", "最初の研究「光」", "序章 観測", "相は2つ合わさって新しい相になる。松明の相は、Aer と Ignis が合わさった Lux だった。",
                listOf("研究盤で Lux を解く"), listOf("見える" to "珍しい物の光"), "銀貨 20", listOf("n2", "n3"), done = true),
            Entry("n5", 3, 1, "ic_distiller", "蒸留器", "1章 抽出", "相は取り出して液体（Essentia）にできる。素材を窯で溶かすと、中の相が分かれて出てくる。",
                listOf("蒸留器を作って工房に置く"), listOf("置ける" to "蒸留器"), "インク ×4", listOf("n4"), hero = "hero_distiller"),
            Entry("n6", 3, 3, "ic_jar", "瓶", "1章 抽出", "Essentia は瓶に溜まる。瓶ひとつにつき相はひとつ。",
                listOf("瓶を3つ置く"), listOf("置ける" to "瓶"), "", listOf("n4"), hero = "hero_jar"),
            Entry("n7", 4, 2, "ic_aqua", "相を溜める", "1章 抽出", "蒸留器と瓶がそろうと、相が溜まっていく。",
                listOf("ひとつの瓶に Essentia を16溜める"), listOf("作れる" to "瓶詰めの Essentia"), "銀貨 40", listOf("n5", "n6")),
            Entry("n8", 4, 4, "ic_vacuos", "寄り道：相の図鑑", "1章 抽出", "本筋には要らない。いろんな物を見て、相を集める。",
                listOf("相を12種類観察する 7/12"), listOf("もらえる" to "眼鏡の見た目"), "", listOf("n6"), side = true),
            Entry("n9", 5, 1, "ic_wand", "杖", "2章 流れ", "杖は工房をつなぐレンチ。顎で管をはさみ、弁の向きを決める。",
                listOf("杖を作る"), listOf("作れる" to "杖"), "", listOf("n7")),
            Entry("n10", 5, 3, "ic_motus", "管と弁", "2章 流れ", "相は、引っ張る力の強いほうへ流れる。",
                listOf("蒸留器から瓶へ管でつなぐ"), listOf("置ける" to "管、弁"), "", listOf("n7")),
            Entry("n11", 6, 2, "ic_vacuos", "地脈", "3章 地脈", "足もとを流れる、見えない川がある。",
                listOf("地脈の上に研究机を置く"), listOf("見える" to "地脈の流れ"), "", listOf("n9", "n10"), mystery = true),
            Entry("n12", 7, 2, "ic_scroll", "帰還の巻物", "終章 最初の儀式", "幹のゴール。遠征先から、魔術で帰ってくる。ここまで来ると専門の枝の封が解ける。",
                listOf("小さな祭壇で帰還の巻物を作る"), listOf("開く" to "専門の枝すべて"), "", listOf("n11"), goal = true),
        )
    }
}
