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
 * edge of the view and zooms with the hotbar scroll. Clicking a node selects it; clicking it again takes the
 * whole path to it. Every rule lives in [CorePassiveTree] and the account service; this only asks.
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

    init { centreOnStart() }

    private fun centreOnStart() {
        val start = account()?.journey?.job?.let(CorePassiveTree::start)
        val spot = start?.let { art.spots[it] } ?: Art.Spot(art.width / 2, art.height / 2)
        centreOn(spot.x, spot.y)
    }
    private fun centreOn(x: Double, y: Double) {
        panX = (VIEW_CX - x * zoom).coerceIn(minX(), MARGIN)
        panY = (VIEW_CY - y * zoom).coerceIn(minY(), MARGIN)
    }
    private fun minX() = minOf(0.0, 1920 - art.width * zoom) - MARGIN
    private fun minY() = minOf(0.0, 1080 - art.height * zoom) - MARGIN

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
        panX = (panX + vx).coerceIn(minX(), MARGIN)
        panY = (panY + vy).coerceIn(minY(), MARGIN)
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
        val job = a.journey.job
        val taken = a.journey.build.passives
        when (action) {
            "tree" -> {
                val id = nodeAt(pointerX, pointerY) ?: run { if (selected == null) return false; selected = null; return true }
                if (id == selected && id !in taken && CorePassiveTree.pathTo(job, taken, id) != null) return confirm(a)
                selected = id; failed = false
            }
            "confirm" -> return confirm(a)
            "choice:0", "choice:1", "choice:2" -> {
                val id = selected ?: return false
                val choice = action.substringAfter(':').toInt()
                if (busy || !masteryChoosable(a, id, choice)) return false
                send(a, CoreAction.TakeMastery(id, choice))
            }
            "reset" -> { if (taken.isEmpty() || busy) return false; send(a, CoreAction.ResetPassives) }
            "centre" -> centreOnStart()
            "zoom:in", "page:next" -> zoomBy(+1)
            "zoom:out", "page:prev" -> zoomBy(-1)
            "close" -> leave()
            else -> return false
        }
        return true
    }

    private fun confirm(a: CoreAccount): Boolean {
        val id = selected ?: return false
        if (busy) return false
        send(a, if (id in a.journey.build.passives) CoreAction.RefundPassive(id) else CoreAction.TakePassive(id))
        return true
    }

    /** Whether option [choice] of mastery [id] can be taken now (the service re-checks everything). */
    private fun masteryChoosable(a: CoreAccount, id: Int, choice: Int): Boolean {
        val b = a.journey.build
        return CorePassiveTree.isMastery(id) && a.activeRun == null && id !in b.passives && choice in CorePassiveTree.nodes[id].choices.indices &&
            CorePassiveTree.wheelOpen(b.passives, id) && !CorePassiveTree.choiceTaken(b.masteries, id, choice) &&
            b.passives.size < CorePassiveTree.budget(a)
    }

    private fun send(a: CoreAccount, operation: CoreAction) {
        busy = true; failed = false
        act(operation, a.revision) { ok -> busy = false; failed = !ok }
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
                     align: String = "left", family: String = "v3num") {
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
        val job = a?.journey?.job ?: CoreClass.WARRIOR
        val taken = a?.journey?.build?.passives.orEmpty()
        val start = CorePassiveTree.start(job)
        val lit = taken + start
        val budget = a?.let { CorePassiveTree.budget(it) } ?: 0
        val hovered = nodeAt(pointerX, pointerY)
        val path = (hovered ?: selected)?.let { CorePassiveTree.pathTo(job, taken, it) }.orEmpty().toSet()

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
        // The planned path: dashes on every link that the path would light.
        if (path.isNotEmpty()) for ((pair, parts) in art.paths) {
            val (p, q) = pair
            if (p in lit && q in lit) continue
            if ((p in path || p in lit) && (q in path || q in lit)) lines("d$p-$q", parts, 3)
        }
        fun mark(id: String, name: String, node: Int, depth: Int) {
            val s = art.sprites[name] ?: return
            val spot = art.spots[node]
            val x = panX + spot.x * zoom; val y = panY + spot.y * zoom
            if (x < -60 || y < -60 || x > 1980 || y > 1140) return
            sprite(id, s, x - s.width * zoom / 2, y - s.height * zoom / 2, s.width * zoom, s.height * zoom, depth)
        }
        for (i in lit) mark("f$i", "lit_${CorePassiveTree.nodes[i].kind.name}_${art.icons[i]}", i, 4)
        // State rings: selected, hovered, one step away, on the path, and masteries whose wheel is open.
        val reach = mutableSetOf<Int>()
        if (taken.size < budget) for (i in lit) for (m in CorePassiveTree.nodes[i].links) if (m !in lit && CorePassiveTree.nodes[m].ready) reach += m
        val open = if (taken.size < budget) CorePassiveTree.nodes.indices.filter { CorePassiveTree.isMastery(it) && it !in taken && CorePassiveTree.wheelOpen(taken, it) }
            else emptyList()
        for (i in (reach + path + open + listOfNotNull(hovered)).distinct()) {
            if (i == selected) continue
            val ring = when { i == hovered -> "hover"; i in reach -> "reach"; i in path -> "path"; else -> "reach" }
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

        header(a, taken, budget)
        totals(a, taken)
        panel(a, job, taken, budget, hovered ?: selected, path)
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
            val lw = width(art.labels[key]); val nw = measure(value, "v3num", 20.0)
            val w = 18 + lw + 10 + nw + 18
            val x = right - w
            framed(id, x, 39.0, w, 42.0, 21.0, PANEL, 12)
            label("$id-l", key, x + 18, 60 - height(art.labels[key]) / 2, 14)
            text("$id-n", x + 18 + lw + 10, 47.0, nw + 4, value, 20.0, color, 14)
            right = x - 10
        }
        chip("chip-silver", "chip_silver", "%,d".format(a?.silver ?: 0), "#ece6dc")
        chip("chip-pts", "chip_points", "${budget - taken.size} / $budget", "#e8c878")
    }

    /** The left panel: what the taken nodes add up to, and which keystones are held. */
    private fun totals(a: CoreAccount?, taken: Set<Int>) {
        framed("lp", LEFT_X, PANEL_TOP, LEFT_W, PANEL_BOTTOM - PANEL_TOP, 14.0, PANEL, 10)
        val ix = LEFT_X + 22; val iw = LEFT_W - 44
        label("lp-h", "totals", ix, 128.0, 12)
        text("lp-n", ix, 134.0, iw, "${taken.size} 点", 13.0, "#a29e97", 12, "right", "v3m")
        rect("lp-rule", ix, 168.0, iw, 1.5, "#ff" + LINE.removePrefix("#"), 12)
        val sum = linkedMapOf<String, Int>()
        val masteries = a?.journey?.build?.masteries.orEmpty()
        for (i in taken) {
            val text = when (CorePassiveTree.nodes[i].kind) {
                CorePassiveKind.MASTERY -> masteries[i]?.let { art.choiceTexts[i].getOrNull(it) } ?: ""
                CorePassiveKind.KEYSTONE, CorePassiveKind.START -> ""
                else -> art.texts[i]
            }
            for (part in text.split(" / ")) STAT.matchEntire(part)?.let { m -> sum.merge(m.groupValues[1], m.groupValues[2].toInt(), Int::plus) }
        }
        val keysTop = 860.0
        val rows = sum.entries.sortedByDescending { it.value }
        if (rows.isEmpty()) label("lp-empty", "totals_empty", ix, 180.0, 12)
        val fit = ((keysTop - 64 - 180) / ROW).toInt()
        rows.take(fit).forEachIndexed { k, (name, value) ->
            val y = 180 + k * ROW
            art.statLabels[name]?.let { label("lp-s$k", it, ix, y, 12) }
            text("lp-v$k", ix, y + 4, iw, "+$value%", 15.0, "#ece6dc", 12, "right")
        }
        rect("lp-rule2", ix, keysTop - 52, iw, 1.5, "#ff" + LINE.removePrefix("#"), 12)
        label("lp-k", "keystones", ix, keysTop - 42, 12)
        label("lp-ks", "keystones_sub", ix + iw - width(art.labels["keystones_sub"]), keysTop - 37, 12)
        CorePassiveTree.nodes.filter { it.kind == CorePassiveKind.KEYSTONE }.forEachIndexed { k, n ->
            art.sprites[if (n.id in taken) "key_on" else "key_off"]?.let {
                sprite("lp-key$k", it, ix + (k % 7) * 48, keysTop + (k / 7) * 48, 38.0, 38.0, 12)
            }
        }
    }

    private fun panel(a: CoreAccount?, job: CoreClass, taken: Set<Int>, budget: Int, shown: Int?, path: Set<Int>) {
        framed("rp", RIGHT_X, PANEL_TOP, RIGHT_W, PANEL_BOTTOM - PANEL_TOP, 14.0, PANEL, 10)
        val ix = RIGHT_X + 26; val iw = RIGHT_W - 52
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
                mastery && !CorePassiveTree.wheelOpen(taken, node.id) -> "locked"
                mastery && taken.size >= budget -> "nopoint"
                mastery -> "pick"
                !node.ready -> "soon"
                path.isEmpty() -> "far"
                taken.size + path.size > budget -> "short"
                else -> "need"
            }
            when (status) {
                null -> {}
                "need" -> {
                    label("st", "st_need_a", ix, sy, 12)
                    val nx = ix + width(art.labels["st_need_a"]) + 4; val n = "${path.size}"
                    text("st-n", nx, sy + 3, 60.0, n, 19.0, "#9af0a0", 12)
                    label("st-b", "st_need_b", nx + measure(n, "v3num", 19.0) + 6, sy, 12)
                }
                "short" -> {
                    val n = "${path.size}"
                    text("st-n", ix, sy + 3, 60.0, n, 19.0, "#e58b80", 12)
                    label("st", "st_short", ix + measure(n, "v3num", 19.0) + 6, sy, 12)
                }
                else -> label("st", "st_$status", ix, sy, 12)
            }
            if (mastery && a != null) masteryChoices(a, node, sy + 36)
        }
        // Confirm: take the path to the selected node, or refund it (with the silver it costs).
        val sel = selected?.let { CorePassiveTree.nodes[it] }
        val price = a?.journey?.let(CorePassiveTree::refundPrice) ?: 0L
        val owned = sel != null && sel.id in taken
        val refundCount = if (owned && a != null) taken.size - CorePassiveTree.refund(job, taken, sel!!.id).size else 0
        val cost = price * refundCount
        val toTake = if (sel != null && !owned) CorePassiveTree.pathTo(job, taken, sel.id) else null
        val ready = a != null && a.activeRun == null && !busy && sel != null && (
            if (owned) a.silver >= cost else toTake != null && taken.size + toTake.size <= budget)
        if (owned) {
            label("cost-l", "cost", ix, 830.0, 12)
            val unit = width(art.labels["cost_unit"])
            label("cost-u", "cost_unit", ix + iw - unit, 830.0, 12)
            text("cost", ix, 829.0, iw - unit - 6, "%,d".format(cost), 18.0, if (a != null && a.silver >= cost) "#ece6dc" else "#e58b80", 12, "right")
        }
        if (failed) label("failed", "st_failed", ix, if (owned) 796.0 else 826.0, 12)
        val by = 862.0
        val state = when { !ready -> "off"; owned -> "refund"; else -> "take" }
        for (k in 0 until 2) art.sprites["go_${state}_$k"]?.let { sprite("go-$k", it, ix + k * 256, by, it.width.toDouble(), 72.0, 12) }
        if (ready) rect("go", ix, by, iw, 72.0, "#00000000", 13, "confirm", "#30ffffff")
        val goText = when {
            owned -> if (refundCount > 1) "戻す（先の ${refundCount - 1} 個も）" else "戻す"
            toTake != null -> "取る（${toTake.size} 点）"
            else -> "取る"
        }
        text("go-t", ix, by + 21, iw, goText, 24.0, when (state) { "take" -> "#2a1406"; "refund" -> "#e58b80"; else -> "#5e5a54" }, 14, "center", "v3b")
        // Reset everything, back to the start, close.
        val resetOk = a != null && a.activeRun == null && !busy && taken.isNotEmpty() && a.silver >= price * taken.size
        val bw = (iw - 20) / 3
        listOf(Triple("reset", "全部戻す", resetOk), Triple("centre", "起点へ戻る", true), Triple("close", "閉じる", true)).forEachIndexed { k, (act, word, on) ->
            val x = ix + k * (bw + 10)
            framed("b-$act", x, 946.0, bw, 48.0, 10.0, "#1b1c20", 12)
            if (on) rect("b-$act-hit", x, 946.0, bw, 48.0, "#00000000", 14, act, "#30ffffff")
            text("b-$act-t", x, 960.0, bw, word, 16.0, if (on) "#ece6dc" else "#5e5a54", 15, "center", "v3m")
        }
    }

    /** The options of a mastery as buttons; the chosen one stays lit once taken. */
    private fun masteryChoices(a: CoreAccount, node: CorePassiveNode, top: Double) {
        val ix = RIGHT_X + 26; val w = RIGHT_W - 52
        val chosen = a.journey.build.masteries[node.id]
        var y = top
        for ((k, key) in art.choiceLabels[node.id].withIndex()) {
            val open = node.id == selected && !busy && masteryChoosable(a, node.id, k)
            val dup = chosen == null && CorePassiveTree.choiceTaken(a.journey.build.masteries, node.id, k)
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
        private const val MARGIN = 200.0
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
