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
 * Drawn in 1920×1080 design units after docs/proposals/skill-tree-2026-10/screen-prototype.template.html:
 * the tree fills the screen (baked into 256 px tiles by scripts/build_passive_tree_assets.py) and Forge-style
 * panels float over it — totals on the left, the chosen node on the right. Pixel art is only the things (node
 * badges, the emblem); lines, rings, words and panels are smooth. Gold lines, lit badges, the dashed planned path
 * and the state rings for this player are drawn on top of the tiles. The tree pans when the pointer rests on an
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
        val infos: Map<Int, List<Placed>>
        /** The right panel's head when nothing is chosen. */
        val noneInfo: List<Placed>
        val labels: Map<String, List<Placed>>
        /** Stat name (as written in node texts) → the label key of its baked name. */
        val statLabels: Map<String, String>
        /** Per node: the label keys of its mastery options (empty for other kinds). */
        val choiceLabels: List<List<String>>
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
            choiceLabels = nodes.map { n -> n.getAsJsonArray("choiceLabels")?.map { it.asString }.orEmpty() }
            require(spots.size == CorePassiveTree.nodes.size) { "passive-tree.json と passive-tree.tsv のノード数が違います" }
            fun blocks(o: com.google.gson.JsonObject) = o.entrySet().associate { (k, v) -> k to v.asJsonArray.map { placed(it.asJsonObject, "sprite") } }
            val heads = blocks(root.getAsJsonObject("infos"))
            infos = heads.filterKeys { it != "none" }.mapKeys { it.key.toInt() }
            noneInfo = heads.getValue("none")
            labels = blocks(root.getAsJsonObject("labels"))
            statLabels = root.getAsJsonObject("statLabels").entrySet().associate { (k, v) -> k to v.asString }
            require(CorePassiveTree.nodes.all { choiceLabels[it.id].size == it.choices.size }) { "passive-tree.json の極意の選択肢が passive-tree.tsv と違います" }
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
    private fun block(id: String, parts: List<Art.Placed>?, x: Double, y: Double, depth: Int, tint: String? = null) =
        parts?.forEachIndexed { k, p -> sprite("$id-$k", p.sprite, x + p.x, y + p.y, p.sprite.width.toDouble(), p.sprite.height.toDouble(), depth, tint) }
    private fun width(parts: List<Art.Placed>?) = parts?.sumOf { it.sprite.width.toDouble() } ?: 0.0
    private fun height(parts: List<Art.Placed>?) = parts?.maxOfOrNull { it.sprite.height.toDouble() } ?: 0.0
    private fun label(id: String, key: String, x: Double, y: Double, depth: Int, tint: String? = null) = block(id, art.labels[key], x, y, depth, tint)
    private fun text(id: String, x: Double, y: Double, w: Double, value: String, size: Double, color: String, depth: Int,
                     align: String = "left", family: String = "v3b") {
        val page = "$family${size.toInt()}".takeIf(Polish05FontMetrics::has) ?: family
        nodes += UiNode(id, box(x, y, w, size * 1.25), value,
            mapOf("font-size" to "${size * SCALE}px", "color" to color, "text-align" to align, "font-family" to "projects_ui_polish05:$page"),
            null, null, true, depth)
    }
    private fun measure(value: String, family: String, size: Double): Double {
        val page = "$family${size.toInt()}".takeIf(Polish05FontMetrics::has) ?: family
        return Polish05FontMetrics.advance(value, page) / Polish05FontMetrics.SIZE * size
    }
    /** Forge-style rounded fill: a cross of panels plus four tinted quarter discs (opaque colours only). */
    private fun round(id: String, x: Double, y: Double, w: Double, h: Double, r: Double, color: String, depth: Int) {
        val argb = "#ff" + color.removePrefix("#")
        rect("$id-c", x + r, y, w - 2 * r, h, argb, depth)
        rect("$id-l", x, y + r, r, h - 2 * r, argb, depth)
        rect("$id-r", x + w - r, y + r, r, h - 2 * r, argb, depth)
        for ((k, cx, cy) in listOf(Triple("tl", x, y), Triple("tr", x + w - r, y), Triple("bl", x, y + h - r), Triple("br", x + w - r, y + h - r)))
            art.sprites["round_$k"]?.let { sprite("$id-$k", it, cx, cy, r, r, depth, color) }
    }
    /** A rounded box with the Forge 1px light border. */
    private fun framed(id: String, x: Double, y: Double, w: Double, h: Double, r: Double, fill: String, depth: Int, border: String = LINE) {
        round("$id-o", x, y, w, h, r, border, depth)
        round("$id-i", x + 1.5, y + 1.5, w - 3, h - 3, r - 1.5, fill, depth + 1)
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
        label("legend", "legend", 470.0, 116.0, 9)

        header(a, held, budget)
        totals(held, choices)
        panel(a, job, taken, budget, hovered ?: selected, path, choices)
        art.sprites["zoom_in"]?.let { sprite("zoom-in", it, 460.0, 964.0, 44.0, 44.0, 10) }
        rect("zoom-in-hit", 460.0, 964.0, 44.0, 44.0, "#00000000", 11, "zoom:in", "#30ffffff")
        art.sprites["zoom_out"]?.let { sprite("zoom-out", it, 512.0, 964.0, 44.0, 44.0, 10) }
        rect("zoom-out-hit", 512.0, 964.0, 44.0, 44.0, "#00000000", 11, "zoom:out", "#30ffffff")
        label("footer", "footer", LEFT_X, 1030.0, 10)
        return UiScene(800.0, 480.0, nodes.toList())
    }

    private fun alpha(a: Double) = String.format("#%02x121316", (a * 255).toInt().coerceIn(0, 255))

    /** 星の盤: emblem and name, the growth tabs, and the points / silver chips. */
    private fun header(a: CoreAccount?, taken: Set<Int>, budget: Int) {
        art.sprites["brand_slot"]?.let { sprite("brand-slot", it, LEFT_X, 34.0, 52.0, 52.0, 12) }
        label("brand", "brand", LEFT_X + 66, 30.0, 12)
        label("brand-sub", "brand_sub", LEFT_X + 66, 64.0, 12)
        val tabs = listOf("tab_passive", "tab_skills", "tab_stats")
        var tx = 960 - (tabs.sumOf { width(art.labels[it]) } + 32 * (tabs.size - 1)) / 2
        for (key in tabs) {
            val w = width(art.labels[key])
            label(key, key, tx, 77 - height(art.labels[key]), 12)
            if (key == "tab_passive") rect("tab-line", tx + w / 2 - 13, 87.0, 26.0, 3.0, "#fff07a2a", 12)
            tx += w + 32
        }
        var right = 1920 - LEFT_X
        fun chip(id: String, key: String, value: String, color: String) {
            val lw = width(art.labels[key]); val nw = measure(value, "v3b", 20.0)
            val w = 18 + lw + 10 + nw + 18
            val x = right - w
            framed(id, x, 39.0, w, 42.0, 21.0, PANEL, 12)
            label("$id-l", key, x + 18, 60 - height(art.labels[key]) / 2, 14)
            text("$id-n", x + 18 + lw + 10, 43.5, nw + 4, value, 20.0, color, 14, family = "v3b")
            right = x - 10
        }
        chip("chip-silver", "chip_silver", "%,d".format(a?.silver ?: 0), "#ece6dc")
        chip("chip-pts", "chip_points", "${budget - taken.size} / $budget", "#e8c878")
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
        framed("lp", LEFT_X, PANEL_TOP, LEFT_W, PANEL_BOTTOM - PANEL_TOP, 14.0, PANEL, 10)
        val ix = LEFT_X + 22; val iw = LEFT_W - 44
        label("lp-h", "totals", ix, 128.0, 12)
        text("lp-n", ix, 131.5, iw, "${taken.size} 点", 16.0, "#a29e97", 12, "right", "v3b")
        rect("lp-rule", ix, 168.0, iw, 1.5, "#ff" + LINE.removePrefix("#"), 12)
        val sum = sums(taken, choices)
        val keysTop = 860.0
        val rows = sum.entries.sortedByDescending { it.value }
        if (rows.isEmpty()) label("lp-empty", "totals_empty", ix, 180.0, 12)
        val fit = ((keysTop - 64 - 180) / ROW).toInt()
        rows.take(fit).forEachIndexed { k, (name, value) ->
            val y = 180 + k * ROW
            art.statLabels[name]?.let { label("lp-s$k", it, ix, y, 12) }
            text("lp-v$k", ix, y + 2.5, iw, "+$value%", 16.0, "#ece6dc", 12, "right", "v3b")
        }
        rect("lp-rule2", ix, keysTop - 52, iw, 1.5, "#ff" + LINE.removePrefix("#"), 12)
        label("lp-k", "keystones", ix, keysTop - 42, 12)
        label("lp-ks", "keystones_sub", ix + iw - width(art.labels["keystones_sub"]), keysTop - 39, 12)
        CorePassiveTree.nodes.filter { it.kind == CorePassiveKind.KEYSTONE }.forEachIndexed { k, n ->
            art.sprites[if (n.id in taken) "key_on" else "key_off"]?.let {
                sprite("lp-key$k", it, ix + (k % 7) * 48, keysTop + (k / 7) * 48, 38.0, 38.0, 12)
            }
        }
    }

    private fun panel(a: CoreAccount?, job: CoreClass, taken: Set<Int>, budget: Int, shown: Int?, path: Set<Int>, choices: Map<Int, Int>) {
        framed("rp", RIGHT_X, PANEL_TOP, RIGHT_W, PANEL_BOTTOM - PANEL_TOP, 14.0, PANEL, 10)
        val ix = RIGHT_X + 26; val iw = RIGHT_W - 52
        val held = taken + planned
        val node = shown?.let { CorePassiveTree.nodes[it] }
        val head = node?.let { art.infos[it.id] } ?: art.noneInfo
        block("info", head, ix, PANEL_TOP + 24, 12)
        if (node != null) {
            val mastery = node.kind == CorePassiveKind.MASTERY
            val sy = PANEL_TOP + 24 + height(head) + 10
            val status = when {
                a == null -> null
                a.activeRun != null -> "hub"
                node.kind == CorePassiveKind.START -> if (node.start == job) "here" else "other"
                node.id in taken -> "taken"
                node.id in planned && !mastery -> "planned"
                mastery && !CorePassiveTree.wheelOpen(held, node.id) -> "locked"
                mastery && node.id !in planned && held.size >= budget -> "nopoint"
                mastery -> "pick"
                !node.ready -> "soon"
                path.isEmpty() -> "far"
                held.size + path.size > budget -> "short"
                else -> "need"
            }
            when (status) {
                null -> {}
                "need" -> {
                    label("st", "st_need_a", ix, sy, 12)
                    val nx = ix + width(art.labels["st_need_a"]) + 4; val n = "${path.size}"
                    text("st-n", nx, sy + 2.5, 60.0, n, 16.0, "#9af0a0", 12, family = "v3b")
                    label("st-b", "st_need_b", nx + measure(n, "v3b", 16.0) + 4, sy, 12)
                }
                "short" -> {
                    val n = "${path.size}"
                    text("st-n", ix, sy + 2.5, 60.0, n, 16.0, "#e58b80", 12, family = "v3b")
                    label("st", "st_short", ix + measure(n, "v3b", 16.0) + 4, sy, 12)
                }
                else -> label("st", "st_$status", ix, sy, 12)
            }
            if (mastery && a != null) masteryChoices(a, node, sy + 36)
            else if (path.isNotEmpty()) delta(held, path, choices, sy + 40)
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
            label("cost-l", "cost", ix, 830.0, 12)
            val unit = width(art.labels["cost_unit"])
            label("cost-u", "cost_unit", ix + iw - unit, 830.0, 12)
            text("cost", ix, 827.0, iw - unit - 6, "%,d".format(cost), 20.0, if (a != null && a.silver >= cost) "#ece6dc" else "#e58b80", 12, "right", "v3b")
        }
        if (failed) label("failed", "st_failed", ix, if (owned) 796.0 else 826.0, 12)
        val by = 862.0
        val state = when { !ready -> "off"; owned || inPlan -> "refund"; else -> "take" }
        for (k in 0 until 2) art.sprites["go_${state}_$k"]?.let { sprite("go-$k", it, ix + k * 256, by, it.width.toDouble(), 72.0, 12) }
        if (ready) rect("go", ix, by, iw, 72.0, "#00000000", 13, "confirm", "#30ffffff")
        val goText = when {
            owned && planned.isNotEmpty() -> "戻すのは確定のあと"
            owned -> if (refundCount > 1) "戻す（先の ${refundCount - 1} 個も）" else "戻す"
            inPlan -> if (refundCount > 1) "外す（先の ${refundCount - 1} 個も）" else "外す"
            toTake != null -> "仮に取る（${toTake.size} 点）"
            else -> "取る"
        }
        text("go-t", ix, by + 21, iw, goText, 24.0, when (state) { "take" -> "#2a1406"; "refund" -> "#e58b80"; else -> "#5e5a54" }, 14, "center", "v3b")
        // With a plan: confirm it (one request) or drop it. Without: reset everything, back to the start. And close.
        val bw = (iw - 20) / 3
        val row = if (planned.isNotEmpty()) {
            val commitOk = a != null && a.activeRun == null && !busy
            listOf(Triple("commit", "確定（${planned.size} 点）", commitOk), Triple("cancel", "取り消し", true), Triple("close", "閉じる", true))
        } else {
            val resetOk = a != null && a.activeRun == null && !busy && taken.isNotEmpty() && a.silver >= price * taken.size
            listOf(Triple("reset", "全部戻す", resetOk), Triple("centre", "起点へ戻る", true), Triple("close", "閉じる", true))
        }
        row.forEachIndexed { k, (act, word, on) ->
            val x = ix + k * (bw + 10)
            if (act == "commit" && on) framed("b-$act", x, 946.0, bw, 48.0, 10.0, "#2b1e17", 12, "#f07a2a")
            else framed("b-$act", x, 946.0, bw, 48.0, 10.0, "#1b1c20", 12)
            if (on) rect("b-$act-hit", x, 946.0, bw, 48.0, "#00000000", 14, act, "#30ffffff")
            text("b-$act-t", x, 960.0, bw, word, 16.0, when { !on -> "#5e5a54"; act == "commit" -> "#f5c08a"; else -> "#ece6dc" }, 15, "center", "v3b")
        }
    }

    /** What taking [path] would change: each stat it touches, from the current total to the new one. */
    private fun delta(held: Set<Int>, path: Set<Int>, choices: Map<Int, Int>, top: Double) {
        val before = sums(held, choices)
        val after = sums(held + path, choices)
        val rows = after.filter { (k, v) -> v != (before[k] ?: 0) }.keys.toList()
        if (rows.isEmpty()) return
        val ix = RIGHT_X + 26; val iw = RIGHT_W - 52
        label("dl-h", "delta", ix, top, 12)
        val arrow = width(art.labels["arrow"])
        rows.take(((780 - top - 34) / ROW).toInt()).forEachIndexed { k, name ->
            val y = top + 34 + k * ROW
            art.statLabels[name]?.let { label("dl-s$k", it, ix, y, 12) }
            val now = "+${after.getValue(name)}%"; val was = "+${before[name] ?: 0}%"
            val nw = measure(now, "v3b", 16.0)
            text("dl-n$k", ix, y + 2.5, iw, now, 16.0, "#9af0a0", 12, "right", "v3b")
            label("dl-a$k", "arrow", ix + iw - nw - 8 - arrow, y, 12)
            text("dl-w$k", ix, y + 2.5, iw - nw - 16 - arrow, was, 16.0, "#a29e97", 12, "right", "v3b")
        }
    }

    /** The options of a mastery as buttons; the chosen one stays lit once taken. */
    private fun masteryChoices(a: CoreAccount, node: CorePassiveNode, top: Double) {
        val ix = RIGHT_X + 26; val w = RIGHT_W - 52
        val chosen = a.journey.build.masteries[node.id] ?: plannedChoices[node.id]
        var y = top
        for ((k, key) in art.choiceLabels[node.id].withIndex()) {
            val open = node.id == selected && !busy && masteryChoosable(a, node.id, k)
            val dup = chosen == null && CorePassiveTree.choiceTaken(a.journey.build.masteries + plannedChoices, node.id, k)
            val h = if (dup) 62.0 else 46.0
            if (chosen == k) framed("ch$k", ix, y, w, h, 10.0, "#2b1e17", 12, "#f07a2a") else framed("ch$k", ix, y, w, h, 10.0, "#17181b", 12)
            if (open) rect("ch$k-hit", ix, y, w, h, "#00000000", 14, "choice:$k", "#30ffffff")
            label("ch$k-t", key, ix + 12, y + 9, 15, if (open || chosen == k) null else "#6b6b6b")
            if (dup) label("ch$k-d", "dup", ix + 12, y + 37, 15)
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
        private const val ROW = 27.0
        private const val EDGE = 56.0
        private const val SPEED = 30.0
        /** How far inside the open view the outermost node can be brought. */
        private const val PAD = 90.0
        private const val LINE = "#25262a"
        private const val PANEL = "#101114"
        private val STAT = Regex("^(.*)\\+(\\d+)%$")
        /** Half badge sizes in art pixels, for picking (the prototype's R). */
        private val PICK = mapOf(CorePassiveKind.SMALL to 6, CorePassiveKind.NOTABLE to 9, CorePassiveKind.KEYSTONE to 14,
            CorePassiveKind.START to 11, CorePassiveKind.MASTERY to 6)
        /** Zoom 1 is the bake: badges at exactly [DOT] design px per art pixel. */
        private val ZOOMS = listOf(0.5, 0.75, 1.0)
    }
}
