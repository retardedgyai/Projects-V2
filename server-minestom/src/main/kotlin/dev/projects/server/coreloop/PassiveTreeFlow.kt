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
 * パッシブツリー（星の盤）: the shared passive tree on the camera + display-entity screen, like the 開拓大陸 map.
 * Drawn in 1920×1080 design units, all pixel art in the 冒険の手帳's look (scripts/build_passive_tree_assets.py):
 * the tree on one art grid of [DOT] px, baked into 256 px tiles, under the 手帳's iron windows — totals on the left,
 * the chosen node on the right. Words are the MaruMinya dot font ("ptdot"), so any string can be written. Gold
 * lines, lit badges, the dashed planned path and the state rings for this player are drawn on top of the tiles.
 * The tree pans when the pointer rests on an
 * edge of the view and zooms with the hotbar scroll. Clicking a node selects it; clicking it again plans the
 * whole path to it, free to undo until 確定 sends the plan in one go (PoE's pending allocation). Every rule
 * lives in [CorePassiveTree] and the account service; this only asks.
 */
internal class PassiveTreeFlow(
    private val art: Art,
    private val account: () -> CoreAccount?,
    private val act: (action: CoreAction, revision: Long, done: (Boolean) -> Unit) -> Unit,
    private val leave: () -> Unit,
) : ForgeUiFlow {
    /** Parsed once at startup from polish05/passive-tree.json. */
    class Art(json: ByteArray) {
        data class Placed(val sprite: UiSprite, val x: Double, val y: Double)
        data class Spot(val x: Double, val y: Double)
        val width: Double
        val height: Double
        val tiles: List<Placed>
        /** Gold lines per edge (both ends taken). */
        val edges: Map<Pair<Int, Int>, List<Placed>>
        /** Orange dashes per edge (the planned path). */
        val paths: Map<Pair<Int, Int>, List<Placed>>
        val spots: List<Spot>
        /** Per node: its badge icon (lit badges are shared per kind and icon). */
        val icons: List<String>
        val texts: List<String>
        val choiceTexts: List<List<String>>
        val sprites: Map<String, UiSprite>
        /** Fixed plates (panels, buttons, chips, the footer), each in pieces of at most 256 px. */
        val plates: Map<String, List<Placed>>
        init {
            val root = JsonParser.parseString(json.toString(Charsets.UTF_8)).asJsonObject
            sprites = root.getAsJsonObject("sprites").entrySet().associate { (name, v) ->
                val o = v.asJsonObject
                name to UiSprite(o["char"].asString, o["font"].asString, o["width"].asInt, o["height"].asInt)
            }
            fun placed(o: com.google.gson.JsonObject, key: String) = Placed(sprites.getValue(o[key].asString), o["x"].asDouble, o["y"].asDouble)
            width = root["width"].asDouble; height = root["height"].asDouble
            tiles = root.getAsJsonArray("tiles").map { placed(it.asJsonObject, "name") }
            fun lines(key: String) = root.getAsJsonArray(key).associate { e ->
                val o = e.asJsonObject
                (o["a"].asInt to o["b"].asInt) to o.getAsJsonArray("parts").map { placed(it.asJsonObject, "sprite") }
            }
            edges = lines("edges"); paths = lines("paths")
            val nodes = root.getAsJsonArray("nodes").map { it.asJsonObject }
            spots = nodes.map { Spot(it["x"].asDouble, it["y"].asDouble) }
            icons = nodes.map { it["icon"].asString }
            texts = nodes.map { it["text"].asString }
            choiceTexts = nodes.map { n -> n.getAsJsonArray("choiceTexts")?.map { it.asString }.orEmpty() }
            require(spots.size == CorePassiveTree.nodes.size) { "passive-tree.json と passive-tree.tsv のノード数が違います" }
            fun blocks(o: com.google.gson.JsonObject) = o.entrySet().associate { (k, v) -> k to v.asJsonArray.map { placed(it.asJsonObject, "sprite") } }
            plates = blocks(root.getAsJsonObject("plates"))
            require(CorePassiveTree.nodes.all { choiceTexts[it.id].size == it.choices.size }) { "passive-tree.json の極意の選択肢が passive-tree.tsv と違います" }
            require(CorePassiveTree.nodes.all { "lit_${it.kind.name}_${icons[it.id]}" in sprites }) { "passive-tree.json に光ったノードの絵がありません" }
        }
    }

    override val view = "passive-tree"
    override val muted = false
    override val operationActive = false
    override val ownsEffects = true
    override val live = true

    private var zoomIndex = ZOOMS.lastIndex
    private val zoom get() = ZOOMS[zoomIndex]
    private var panX = 0.0
    private var panY = 0.0
    private var targetVx = 0.0
    private var targetVy = 0.0
    private var vx = 0.0
    private var vy = 0.0
    private var pointerX = -1.0
    private var pointerY = -1.0
    private var selected: Int? = null
    private var busy = false
    private var failed = false
    /** Planned but not confirmed: path nodes and masteries, and the planned masteries' options. */
    private var planned = setOf<Int>()
    private var plannedChoices = mapOf<Int, Int>()

    // Pan far enough that every node, the outermost too, can sit in the open view between the panels.
    private val spotsX = art.spots.minOf { it.x }..art.spots.maxOf { it.x }
    private val spotsY = art.spots.minOf { it.y }..art.spots.maxOf { it.y }

    init { centreOnStart() }

    private fun centreOnStart() {
        val start = account()?.journey?.job?.let(CorePassiveTree::start)
        val spot = start?.let { art.spots[it] } ?: Art.Spot(art.width / 2, art.height / 2)
        centreOn(spot.x, spot.y)
    }
    private fun centreOn(x: Double, y: Double) {
        panX = clampX(VIEW_CX - x * zoom)
        panY = clampY(VIEW_CY - y * zoom)
    }
    private fun clamp(v: Double, lo: Double, hi: Double) = if (lo <= hi) v.coerceIn(lo, hi) else (lo + hi) / 2
    private fun clampX(v: Double) = clamp(v, VIEW_R - PAD - spotsX.endInclusive * zoom, VIEW_L + PAD - spotsX.start * zoom)
    private fun clampY(v: Double) = clamp(v, PANEL_BOTTOM - PAD - spotsY.endInclusive * zoom, PANEL_TOP + PAD - spotsY.start * zoom)

    /** Over a floating panel or the header: not the tree. */
    private fun overChrome(x: Double, y: Double) = y < HEADER ||
        (y in PANEL_TOP..PANEL_BOTTOM && (x in LEFT_X..LEFT_X + LEFT_W || x in RIGHT_X..RIGHT_X + RIGHT_W))

    override fun pointer(x: Double, y: Double) {
        val dx = x / SCALE
        val dy = (y - TOP) / SCALE
        pointerX = dx; pointerY = dy
        fun push(depth: Double) = SPEED * ((EDGE - depth) / EDGE).coerceIn(0.0, 1.0).let { it * it }
        // Rest the cursor on a screen edge or on an inner edge of the view between the panels.
        val band = dy in PANEL_TOP..PANEL_BOTTOM
        val left = when { dx < EDGE -> dx; band && dx >= VIEW_L && dx < VIEW_L + EDGE -> dx - VIEW_L; else -> null }
        val right = when { dx > 1920 - EDGE -> 1920 - dx; band && dx <= VIEW_R && dx > VIEW_R - EDGE -> VIEW_R - dx; else -> null }
        targetVx = left?.let(::push) ?: right?.let { -push(it) } ?: 0.0
        val columns = dx in VIEW_L..VIEW_R
        val top = when { dy < EDGE -> dy; columns && dy >= HEADER && dy < PANEL_TOP + EDGE -> dy - HEADER; else -> null }
        val bottom = if (dy > 1080 - EDGE) 1080 - dy else null
        targetVy = top?.let(::push) ?: bottom?.let { -push(it) } ?: 0.0
    }

    override fun tick(nowMs: Long): dev.projects.webui.ForgeUiReceipt? {
        vx += (targetVx - vx) * 0.25
        vy += (targetVy - vy) * 0.25
        panX = clampX(panX + vx)
        panY = clampY(panY + vy)
        return null
    }

    /** The node whose badge lies under the pointer, if any. */
    private fun nodeAt(x: Double, y: Double): Int? {
        if (x < 0 || y < 0 || overChrome(x, y)) return null
        val tx = (x - panX) / zoom; val ty = (y - panY) / zoom
        return art.spots.indices.minByOrNull { (art.spots[it].x - tx).let { d -> d * d } + (art.spots[it].y - ty).let { d -> d * d } }
            ?.takeIf { i ->
                val reach = maxOf(14.0, (PICK.getValue(CorePassiveTree.nodes[i].kind) * DOT + 6) * zoom)
                kotlin.math.hypot(art.spots[i].x - tx, art.spots[i].y - ty) * zoom <= reach
            }
    }

    override fun action(action: String): Boolean {
        val a = account() ?: return false
        prune(a)
        val taken = a.journey.build.passives
        when (action) {
            "tree" -> {
                val id = nodeAt(pointerX, pointerY) ?: run { if (selected == null) return false; selected = null; return true }
                if (id == selected && id !in taken) return confirm(a)
                selected = id; failed = false
            }
            "confirm" -> return confirm(a)
            "choice:0", "choice:1", "choice:2" -> {
                val id = selected ?: return false
                val choice = action.substringAfter(':').toInt()
                if (!masteryChoosable(a, id, choice)) return false
                planned = planned + id; plannedChoices = plannedChoices + (id to choice)
            }
            "commit" -> {
                if (planned.isEmpty() || busy || a.activeRun != null) return false
                send(a, CoreAction.TakePassives(planned, plannedChoices)) { planned = emptySet(); plannedChoices = emptyMap() }
            }
            "cancel" -> { if (planned.isEmpty()) return false; planned = emptySet(); plannedChoices = emptyMap() }
            "reset" -> {
                if (taken.isEmpty() || busy) return false
                planned = emptySet(); plannedChoices = emptyMap()
                send(a, CoreAction.ResetPassives)
            }
            "centre" -> centreOnStart()
            "zoom:in", "page:next" -> zoomBy(+1)
            "zoom:out", "page:prev" -> zoomBy(-1)
            "close" -> leave()
            else -> return false
        }
        return true
    }

    /**
     * The button under the panel, for the selected node: refund a taken node (silver, at once), drop a planned one
     * (free, with what only it reached), or plan the path to an open one.
     */
    private fun confirm(a: CoreAccount): Boolean {
        val id = selected ?: return false
        val job = a.journey.job
        val taken = a.journey.build.passives
        when {
            id in taken -> {
                if (busy || planned.isNotEmpty()) return false
                send(a, CoreAction.RefundPassive(id))
            }
            id in planned -> {
                planned = CorePassiveTree.refund(job, taken + planned, id) - taken
                plannedChoices = plannedChoices.filterKeys { it in planned }
            }
            else -> {
                if (a.activeRun != null) return false
                val path = CorePassiveTree.pathTo(job, taken + planned, id) ?: return false
                if (taken.size + planned.size + path.size > CorePassiveTree.budget(a)) return false
                planned = planned + path
            }
        }
        return true
    }

    /** Drops the plan once it no longer fits the account (a refund elsewhere, a commit seen twice, fewer points). */
    private fun prune(a: CoreAccount) {
        if (planned.isEmpty()) return
        val b = a.journey.build
        val all = b.passives + planned
        if (planned.any { it in b.passives } || all.size > CorePassiveTree.budget(a) ||
            !CorePassiveTree.valid(a.journey.job, all, b.masteries + plannedChoices)) { planned = emptySet(); plannedChoices = emptyMap() }
    }

    /** Whether option [choice] of mastery [id] can be planned now (or switched to, while planned). */
    private fun masteryChoosable(a: CoreAccount, id: Int, choice: Int): Boolean {
        val b = a.journey.build
        val all = b.passives + planned
        return CorePassiveTree.isMastery(id) && a.activeRun == null && id !in b.passives && choice in CorePassiveTree.nodes[id].choices.indices &&
            CorePassiveTree.wheelOpen(all, id) && !CorePassiveTree.choiceTaken(b.masteries + plannedChoices, id, choice) &&
            (id in planned || all.size < CorePassiveTree.budget(a))
    }

    private fun send(a: CoreAccount, operation: CoreAction, onOk: () -> Unit = {}) {
        busy = true; failed = false
        act(operation, a.revision) { ok -> busy = false; failed = !ok; if (ok) onOk() }
    }

    private fun zoomBy(step: Int) {
        val next = (zoomIndex + step).coerceIn(ZOOMS.indices)
        if (next == zoomIndex) return
        // Keep the tree point at the middle of the view where it is.
        val cx = (VIEW_CX - panX) / zoom; val cy = (VIEW_CY - panY) / zoom
        zoomIndex = next
        centreOn(cx, cy)
    }

    // ------------------------------------------------------------------ drawing (design units)

    private val nodes = mutableListOf<UiNode>()
    private fun box(x: Double, y: Double, w: Double, h: Double) = Box(x * SCALE, TOP + y * SCALE, w * SCALE, h * SCALE)
    private fun rect(id: String, x: Double, y: Double, w: Double, h: Double, color: String, depth: Int, action: String? = null, hover: String? = null) {
        val style = buildMap { put("background-color", color); if (hover != null) put("hover-background-color", hover) }
        nodes += UiNode(id, box(x, y, w, h), "", style, action, null, true, depth)
    }
    private fun sprite(id: String, s: UiSprite, x: Double, y: Double, w: Double, h: Double, depth: Int, tint: String? = null) {
        nodes += UiNode(id, box(x, y, w, h), "", if (tint != null) mapOf("sprite-color" to tint) else emptyMap(), null, null, true, depth, s)
    }
    private fun block(id: String, parts: List<Art.Placed>?, x: Double, y: Double, depth: Int) =
        parts?.forEachIndexed { k, p -> sprite("$id-$k", p.sprite, x + p.x, y + p.y, p.sprite.width.toDouble(), p.sprite.height.toDouble(), depth) }
    /** A baked plate (panel, button, chip) with its top-left at [x], [y]. */
    private fun plate(id: String, name: String, x: Double, y: Double, depth: Int) = block(id, art.plates[name], x, y, depth)

    /**
     * Dot words: [size] is the font's 12-dot em ([BODY] = 2 px a dot, [NAME] = 3), [y] the top of the letters' cell
     * (their baseline is [size] below it). [shadow] false for dark words on gold.
     */
    private fun text(id: String, x: Double, y: Double, value: String, color: String, depth: Int, size: Double = BODY,
                     align: String = "left", w: Double = measure(value, size), shadow: Boolean = true) {
        nodes += UiNode(id, box(x, y, w, size * 1.25), value,
            mapOf("font-size" to "${size * SCALE}px", "color" to color, "text-align" to align,
                "font-family" to "projects_ui_polish05:" + if (shadow) "ptdot" else "ptdotn"), null, null, true, depth)
    }
    private fun measure(value: String, size: Double = BODY) = Polish05FontMetrics.advance(value, "ptdot") / Polish05FontMetrics.SIZE * size
    /** [value] broken into lines no wider than [width] (Japanese breaks anywhere). */
    private fun wrap(value: String, width: Double, size: Double = BODY): List<String> {
        val lines = mutableListOf<String>(); var line = ""
        for (ch in value) {
            if (line.isNotEmpty() && measure(line + ch, size) > width) { lines += line; line = "" }
            line += ch
        }
        return if (line.isEmpty()) lines else lines + line
    }

    override fun scene(light: ForgeLightPhase): UiScene {
        nodes.clear()
        val a = account()
        a?.let(::prune)
        val job = a?.journey?.job ?: CoreClass.WARRIOR
        val taken = a?.journey?.build?.passives.orEmpty()
        // Everything below counts the plan as taken; only the gold lines and the refund button are for the confirmed.
        val held = taken + planned
        val choices = a?.journey?.build?.masteries.orEmpty() + plannedChoices
        val start = CorePassiveTree.start(job)
        val lit = taken + start
        val budget = a?.let { CorePassiveTree.budget(it) } ?: 0
        val hovered = nodeAt(pointerX, pointerY)
        val path = (hovered ?: selected)?.let { CorePassiveTree.pathTo(job, held, it) }.orEmpty().toSet()

        rect("bg", 0.0, 0.0, 1920.0, 1080.0, "#ff101116", 0)
        for (t in art.tiles) {
            val x = panX + t.x * zoom; val y = panY + t.y * zoom
            val w = t.sprite.width * zoom; val h = t.sprite.height * zoom
            if (x > 1920 || y > 1080 || x + w < 0 || y + h < 0) continue
            sprite("t-${t.x.toInt()}-${t.y.toInt()}", t.sprite, x, y, w, h, 1)
        }
        fun lines(prefix: String, parts: List<Art.Placed>, depth: Int) {
            for ((k, part) in parts.withIndex()) {
                val x = panX + part.x * zoom; val y = panY + part.y * zoom
                val w = part.sprite.width * zoom; val h = part.sprite.height * zoom
                if (x > 1920 || y > 1080 || x + w < 0 || y + h < 0) continue
                sprite("$prefix-$k", part.sprite, x, y, w, h, depth)
            }
        }
        for ((pair, parts) in art.edges) if (pair.first in lit && pair.second in lit) lines("e${pair.first}-${pair.second}", parts, 2)
        // The plan and the path under the pointer: dashes on every link they would light.
        val dashed = planned + path
        if (dashed.isNotEmpty()) for ((pair, parts) in art.paths) {
            val (p, q) = pair
            if (p in lit && q in lit) continue
            if ((p in dashed || p in lit) && (q in dashed || q in lit)) lines("d$p-$q", parts, 3)
        }
        fun mark(id: String, name: String, node: Int, depth: Int) {
            val s = art.sprites[name] ?: return
            val spot = art.spots[node]
            val x = panX + spot.x * zoom; val y = panY + spot.y * zoom
            if (x < -60 || y < -60 || x > 1980 || y > 1140) return
            sprite(id, s, x - s.width * zoom / 2, y - s.height * zoom / 2, s.width * zoom, s.height * zoom, depth)
        }
        for (i in lit + planned) mark("f$i", "lit_${CorePassiveTree.nodes[i].kind.name}_${art.icons[i]}", i, 4)
        // State rings: selected, hovered, planned or on the path, one step away, and masteries whose wheel is open.
        val reach = mutableSetOf<Int>()
        val shown = lit + planned
        if (held.size < budget) for (i in shown) for (m in CorePassiveTree.nodes[i].links) if (m !in shown && CorePassiveTree.nodes[m].ready) reach += m
        val open = if (held.size < budget) CorePassiveTree.nodes.indices.filter { CorePassiveTree.isMastery(it) && it !in held && CorePassiveTree.wheelOpen(held, it) }
            else emptyList()
        for (i in (planned + reach + path + open + listOfNotNull(hovered)).distinct()) {
            if (i == selected) continue
            val ring = when { i == hovered -> "hover"; i in planned || i in path -> "path"; else -> "reach" }
            mark("r$i", "ring_${ring}_${CorePassiveTree.nodes[i].kind.name}", i, 5)
        }
        selected?.let { mark("sel", "ring_sel_${CorePassiveTree.nodes[it].kind.name}", it, 6) }
        rect("tree", VIEW_L, HEADER, VIEW_R - VIEW_L, 1080 - HEADER, "#00000000", 7, "tree", "#00000000")

        // Fades under the header and the footer, like the prototype's gradients.
        rect("fade-t", 0.0, 0.0, 1920.0, 52.0, "#ff121316", 8)
        for (k in 0 until 6) rect("fade-t$k", 0.0, 52.0 + k * 16.4, 1920.0, 16.4, alpha(1 - (k + 0.5) / 6), 8)
        for (k in 0 until 3) rect("fade-b$k", 0.0, 990.0 + k * 9, 1920.0, 9.0, alpha((k + 0.5) / 3), 8)
        rect("fade-b", 0.0, 1017.0, 1920.0, 63.0, "#ff121316", 8)

        header(a, held, budget)
        totals(held, choices)
        panel(a, job, taken, budget, hovered ?: selected, path, choices)
        plate("zoom-in", "zoom_in", 460.0, 964.0, 10)
        rect("zoom-in-hit", 460.0, 964.0, 44.0, 44.0, "#00000000", 11, "zoom:in", "#30ffffff")
        plate("zoom-out", "zoom_out", 512.0, 964.0, 10)
        rect("zoom-out-hit", 512.0, 964.0, 44.0, 44.0, "#00000000", 11, "zoom:out", "#30ffffff")
        plate("footer", "footer", LEFT_X, 1031.0, 10)
        return UiScene(800.0, 480.0, nodes.toList())
    }

    private fun alpha(a: Double) = String.format("#%02x121316", (a * 255).toInt().coerceIn(0, 255))

    /** The emblem and the screen's name, and the points / silver chips. */
    private fun header(a: CoreAccount?, taken: Set<Int>, budget: Int) {
        plate("brand-slot", "brand_slot", LEFT_X, 34.0, 12)
        text("brand", LEFT_X + 68, 36.0, "パッシブ", INK, 12, NAME)
        fun chip(id: String, x: Double, label: String, value: String, color: String) {
            plate(id, "chip", x, 38.0, 12)
            text("$id-l", x + 14, 47.0, label, MUTE, 14)
            text("$id-n", x, 47.0, value, color, 14, align = "right", w = CHIP_W - 14)
        }
        chip("chip-silver", 1920 - LEFT_X - CHIP_W, "銀貨", "%,d".format(a?.silver ?: 0), INK)
        chip("chip-pts", 1920 - LEFT_X - 2 * CHIP_W - 10, "ポイント", "${budget - taken.size} / $budget", GOLD)
    }

    /** Stat name (as in the node texts) → summed percent, over [ids] with mastery options [choices]. */
    private fun sums(ids: Set<Int>, choices: Map<Int, Int>): Map<String, Int> {
        val sum = linkedMapOf<String, Int>()
        for (i in ids) {
            val text = when (CorePassiveTree.nodes[i].kind) {
                CorePassiveKind.MASTERY -> choices[i]?.let { art.choiceTexts[i].getOrNull(it) } ?: ""
                CorePassiveKind.KEYSTONE, CorePassiveKind.START -> ""
                else -> art.texts[i]
            }
            for (part in text.split(" / ")) STAT.matchEntire(part)?.let { m -> sum.merge(m.groupValues[1], m.groupValues[2].toInt(), Int::plus) }
        }
        return sum
    }

    /** The left panel: what the taken (and planned) nodes add up to, and which keystones are held. */
    private fun totals(taken: Set<Int>, choices: Map<Int, Int>) {
        plate("lp", "panel_left", LEFT_X, PANEL_TOP, 10)
        val ix = LEFT_X + 24; val iw = LEFT_W - 48
        text("lp-h", ix, PANEL_TOP + 22, "合計", INK, 12)
        text("lp-n", ix, PANEL_TOP + 22, "${taken.size} 点", MUTE, 12, align = "right", w = iw)
        rect("lp-rule", ix, PANEL_TOP + 58, iw, 2.0, RULE, 12)
        val keysTop = 860.0
        val rows = sums(taken, choices).entries.sortedByDescending { it.value }
        val fit = ((keysTop - 60 - (PANEL_TOP + 72)) / ROW).toInt()
        rows.take(fit).forEachIndexed { k, (name, value) ->
            val y = PANEL_TOP + 72 + k * ROW
            text("lp-s$k", ix, y, name, INK, 12, w = iw - 70)
            text("lp-v$k", ix, y, "+$value%", INK, 12, align = "right", w = iw)
        }
        rect("lp-rule2", ix, keysTop - 54, iw, 2.0, RULE, 12)
        text("lp-k", ix, keysTop - 44, "キーストーン", INK, 12)
        CorePassiveTree.nodes.filter { it.kind == CorePassiveKind.KEYSTONE }.forEachIndexed { k, n ->
            plate("lp-key$k", if (n.id in taken) "key_on" else "key_off", ix + (k % 7) * 48, keysTop + (k / 7) * 48, 12)
        }
    }

    private fun panel(a: CoreAccount?, job: CoreClass, taken: Set<Int>, budget: Int, shown: Int?, path: Set<Int>, choices: Map<Int, Int>) {
        plate("rp", "panel_right", RIGHT_X, PANEL_TOP, 10)
        val ix = RIGHT_X + 26; val iw = RIGHT_W - 52
        val held = taken + planned
        val node = shown?.let { CorePassiveTree.nodes[it] }
        if (node != null) {
            val mastery = node.kind == CorePassiveKind.MASTERY
            val kind = KIND_LABEL.getValue(node.kind).takeIf { it != node.name }
            kind?.let { text("kind", ix, PANEL_TOP + 22, it, MUTE, 12) }
            var y = PANEL_TOP + if (kind != null) 54 else 22
            wrap(node.name, iw, NAME).forEachIndexed { k, line -> text("name$k", ix, y, line, INK, 12, NAME); y += NAME + 8 }
            y += 8
            // Stat lines: the name, then its value in the heat colour; anything else (keystones) as plain lines.
            val body = when (node.kind) { CorePassiveKind.SMALL, CorePassiveKind.NOTABLE -> art.texts[node.id].split(" / ")
                CorePassiveKind.KEYSTONE -> listOf(art.texts[node.id]); else -> emptyList() }
            for ((p, part) in body.withIndex()) {
                val m = STAT.matchEntire(part)
                val lines = wrap(m?.groupValues?.get(1)?.trimEnd() ?: part, iw - if (m != null) 80 else 0)
                for ((k, line) in lines.withIndex()) {
                    text("b$p-$k", ix, y, line, if (m != null) INK else SOFT, 12)
                    if (m != null && k == lines.lastIndex) text("b$p-v", ix + measure(line) + 10, y, "+${m.groupValues[2]}%", HEAT, 12)
                    y += ROW
                }
            }
            val status = when {
                a == null -> null
                a.activeRun != null -> "港でのみ変更" to DANGER
                node.kind == CorePassiveKind.START -> (if (node.start == job) "自分の起点" else "ほかの職の起点") to MUTE
                node.id in taken -> "取得済み" to GOLD
                node.id in planned && !mastery -> "仮に取った" to OK
                mastery && !CorePassiveTree.wheelOpen(held, node.id) -> "主要ノードが先" to MUTE
                mastery && node.id !in planned && held.size >= budget -> "ポイント不足" to DANGER
                mastery -> "1つ選ぶ" to OK
                !node.ready -> "準備中" to MUTE
                path.isEmpty() -> "届かない" to DANGER
                held.size + path.size > budget -> "${held.size + path.size - budget} 点足りない" to DANGER
                else -> "あと ${path.size} 点" to OK
            }
            status?.let { (word, color) -> text("st", ix, y + 4, word, color, 12) }
            if (mastery && a != null) masteryChoices(a, node, y + 44)
            else if (path.isNotEmpty()) delta(held, path, choices, y + 44)
        }
        // The button: plan the path to the selected node, drop it from the plan, or refund it (with the silver it costs).
        val sel = selected?.let { CorePassiveTree.nodes[it] }
        val price = a?.journey?.let(CorePassiveTree::refundPrice) ?: 0L
        val owned = sel != null && sel.id in taken
        val inPlan = sel != null && sel.id in planned
        val refundCount = when {
            sel == null || a == null -> 0
            owned -> taken.size - CorePassiveTree.refund(job, taken, sel.id).size
            inPlan -> held.size - CorePassiveTree.refund(job, held, sel.id).size
            else -> 0
        }
        val cost = if (owned) price * refundCount else 0L
        val toTake = if (sel != null && !owned && !inPlan) CorePassiveTree.pathTo(job, held, sel.id) else null
        val ready = a != null && a.activeRun == null && sel != null && when {
            owned -> !busy && planned.isEmpty() && a.silver >= cost
            inPlan -> true
            else -> toTake != null && held.size + toTake.size <= budget
        }
        if (owned) {
            text("cost-l", ix, 822.0, "代金", MUTE, 12)
            text("cost", ix, 822.0, "%,d 銀貨".format(cost), if (a != null && a.silver >= cost) INK else DANGER, 12, align = "right", w = iw)
        }
        if (failed) text("failed", ix, if (owned) 790.0 else 822.0, "できなかった", DANGER, 12)
        val by = 862.0
        val state = when { !ready -> "off"; owned || inPlan -> "refund"; else -> "take" }
        plate("go", "go_$state", ix, by, 12)
        if (ready) rect("go-hit", ix, by, GO_W, GO_H, "#00000000", 13, "confirm", "#30ffffff")
        val goText = when {
            owned && planned.isNotEmpty() -> "確定のあとで"
            owned -> if (refundCount > 1) "戻す（${refundCount} 個）" else "戻す"
            inPlan -> if (refundCount > 1) "外す（${refundCount} 個）" else "外す"
            toTake != null -> "仮に取る（${toTake.size} 点）"
            else -> "取る"
        }
        text("go-t", ix, by + (GO_H - NAME * 1.25) / 2 + 2, goText, when (state) { "take" -> DARK; "refund" -> INK; else -> DIM }, 14, NAME,
            "center", GO_W, shadow = state != "take")
        // With a plan: confirm it (one request) or drop it. Without: reset everything, back to the start. And close.
        val row = if (planned.isNotEmpty()) {
            val commitOk = a != null && a.activeRun == null && !busy
            listOf(Triple("commit", "確定 (${planned.size})", commitOk), Triple("cancel", "取り消し", true), Triple("close", "閉じる", true))
        } else {
            val resetOk = a != null && a.activeRun == null && !busy && taken.isNotEmpty() && a.silver >= price * taken.size
            listOf(Triple("reset", "全部戻す", resetOk), Triple("centre", "起点へ", true), Triple("close", "閉じる", true))
        }
        row.forEachIndexed { k, (act, word, on) ->
            val x = ix + k * (BTN_W + 10)
            val gold = act == "commit" && on
            plate("b-$act", when { !on -> "btn_off"; gold -> "btn_gold"; else -> "btn" }, x, 946.0, 12)
            if (on) rect("b-$act-hit", x, 946.0, BTN_W, BTN_H, "#00000000", 14, act, "#30ffffff")
            text("b-$act-t", x, 946.0 + (BTN_H - BODY * 1.25) / 2 + 1, word, when { !on -> DIM; gold -> DARK; else -> INK }, 15,
                align = "center", w = BTN_W, shadow = !gold)
        }
    }

    /** What taking [path] would change: each stat it touches, from the current total to the new one. */
    private fun delta(held: Set<Int>, path: Set<Int>, choices: Map<Int, Int>, top: Double) {
        val before = sums(held, choices)
        val after = sums(held + path, choices)
        val rows = after.filter { (k, v) -> v != (before[k] ?: 0) }.keys.toList()
        if (rows.isEmpty()) return
        val ix = RIGHT_X + 26; val iw = RIGHT_W - 52
        rect("dl-rule", ix, top, iw, 2.0, RULE, 12)
        val arrow = measure("→")
        rows.take(((780 - top - 14) / ROW).toInt()).forEachIndexed { k, name ->
            val y = top + 14 + k * ROW
            val now = "+${after.getValue(name)}%"; val was = "+${before[name] ?: 0}%"
            val nw = measure(now)
            text("dl-s$k", ix, y, name, INK, 12)
            text("dl-n$k", ix, y, now, OK, 12, align = "right", w = iw)
            text("dl-a$k", ix + iw - nw - 8 - arrow, y, "→", MUTE, 12)
            text("dl-w$k", ix, y, was, MUTE, 12, align = "right", w = iw - nw - 16 - arrow)
        }
    }

    /** The options of a mastery as buttons; the chosen one stays lit once taken. */
    private fun masteryChoices(a: CoreAccount, node: CorePassiveNode, top: Double) {
        val ix = RIGHT_X + 26
        val chosen = a.journey.build.masteries[node.id] ?: plannedChoices[node.id]
        var y = top
        for ((k, option) in art.choiceTexts[node.id].withIndex()) {
            val open = node.id == selected && !busy && masteryChoosable(a, node.id, k)
            val dup = chosen == null && CorePassiveTree.choiceTaken(a.journey.build.masteries + plannedChoices, node.id, k)
            val lines = wrap(option, GO_W - 24).take(3)
            val h = choiceHeight(lines.size, dup)
            plate("ch$k", if (chosen == k) "choice_on_${h.toInt()}" else "choice_${h.toInt()}", ix, y, 12)
            if (open) rect("ch$k-hit", ix, y, GO_W, h, "#00000000", 14, "choice:$k", "#30ffffff")
            lines.forEachIndexed { l, line -> text("ch$k-t$l", ix + 12, y + 8 + l * CHOICE_LINE, line, if (open || chosen == k) INK else DIM, 15, w = GO_W - 24) }
            if (dup) text("ch$k-d", ix + 12, y + 8 + lines.size * CHOICE_LINE, "選び済み", DANGER, 15)
            y += h + 8
        }
    }

    companion object {
        private const val SCALE = 800.0 / 1920.0
        private const val TOP = (480.0 - 1080.0 * SCALE) / 2
        private const val DOT = 3.0
        private const val HEADER = 100.0
        private const val LEFT_X = 56.0
        private const val LEFT_W = 380.0
        private const val RIGHT_W = 460.0
        private const val RIGHT_X = 1920.0 - 56 - RIGHT_W
        private const val PANEL_TOP = 110.0
        private const val PANEL_BOTTOM = 1018.0
        /** The open view between the panels: edge scrolling and clicks on the tree. */
        private const val VIEW_L = LEFT_X + LEFT_W + 4
        private const val VIEW_R = RIGHT_X
        private const val VIEW_CX = (VIEW_L + VIEW_R) / 2
        private const val VIEW_CY = 580.0
        private const val ROW = 30.0
        private const val EDGE = 56.0
        private const val SPEED = 30.0
        /** How far inside the open view the outermost node can be brought. */
        private const val PAD = 90.0
        /** Dot words: the font's 12-dot em at 2 and 3 design px a dot. */
        private const val BODY = 24.0
        private const val NAME = 36.0
        private const val INK = "#ece6dc"
        private const val SOFT = "#c9c3b8"
        private const val MUTE = "#9a948c"
        private const val DIM = "#5e5a54"
        private const val GOLD = "#f2c84b"
        private const val HEAT = "#ff9a4a"
        private const val OK = "#9af0a0"
        private const val DANGER = "#e58b80"
        private const val DARK = "#2a1a06"
        private const val RULE = "#ff2a2a2e"
        private const val CHIP_W = 236.0
        private const val GO_W = 408.0
        private const val GO_H = 72.0
        private const val BTN_W = 129.0
        private const val BTN_H = 48.0
        /** A mastery option's plate: 1 to 3 lines, and one more for 選び済み (scripts/build_passive_tree_assets.py bakes each). */
        private const val CHOICE_LINE = 28.0
        private fun choiceHeight(lines: Int, dup: Boolean) = 18 + CHOICE_LINE * (lines + if (dup) 1 else 0)
        private val KIND_LABEL = mapOf(CorePassiveKind.SMALL to "ノード", CorePassiveKind.NOTABLE to "主要ノード",
            CorePassiveKind.KEYSTONE to "キーストーン", CorePassiveKind.START to "起点", CorePassiveKind.MASTERY to "極意")
        private val STAT = Regex("^(.*)\\+(\\d+)%$")
        /** Half badge sizes in art pixels, for picking (the prototype's R). */
        private val PICK = mapOf(CorePassiveKind.SMALL to 6, CorePassiveKind.NOTABLE to 9, CorePassiveKind.KEYSTONE to 14,
            CorePassiveKind.START to 11, CorePassiveKind.MASTERY to 6)
        /** Zoom 1 is the bake: badges at exactly [DOT] design px per art pixel. */
        private val ZOOMS = listOf(0.5, 0.75, 1.0)
    }
}
