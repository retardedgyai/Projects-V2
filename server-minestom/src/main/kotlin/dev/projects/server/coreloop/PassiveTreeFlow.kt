package dev.projects.server.coreloop

import com.google.gson.JsonParser
import dev.projects.webui.Box
import dev.projects.webui.ForgeLightPhase
import dev.projects.webui.ForgeUiFlow
import dev.projects.webui.UiNode
import dev.projects.webui.UiScene
import dev.projects.webui.UiSprite

/**
 * パッシブツリー: the shared passive tree on the camera + display-entity screen, like the 開拓大陸 map.
 * Drawn in 1920×1080 design units. The tree itself is baked into 256 px tiles (scripts/build_passive_tree_assets.py);
 * gold edges, gold faces and rings for this player's allocation are drawn on top. The tree pans when the pointer
 * rests on a screen edge and zooms with the hotbar scroll. Clicking a node selects it; clicking it again takes the
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
        val edges: Map<Pair<Int, Int>, List<Placed>>
        val spots: List<Spot>
        val sprites: Map<String, UiSprite>
        val infos: Map<Int, List<Placed>>
        val labels: Map<String, List<Placed>>
        init {
            val root = JsonParser.parseString(json.toString(Charsets.UTF_8)).asJsonObject
            sprites = root.getAsJsonObject("sprites").entrySet().associate { (name, v) ->
                val o = v.asJsonObject
                name to UiSprite(o["char"].asString, o["font"].asString, o["width"].asInt, o["height"].asInt)
            }
            fun placed(o: com.google.gson.JsonObject, key: String) = Placed(sprites.getValue(o[key].asString), o["x"].asDouble, o["y"].asDouble)
            width = root["width"].asDouble; height = root["height"].asDouble
            tiles = root.getAsJsonArray("tiles").map { placed(it.asJsonObject, "name") }
            edges = root.getAsJsonArray("edges").associate { e ->
                val o = e.asJsonObject
                (o["a"].asInt to o["b"].asInt) to o.getAsJsonArray("parts").map { placed(it.asJsonObject, "sprite") }
            }
            spots = root.getAsJsonArray("nodes").map { n -> n.asJsonObject.let { Spot(it["x"].asDouble, it["y"].asDouble) } }
            require(spots.size == CorePassiveTree.nodes.size) { "passive-tree.json と passive-tree.tsv のノード数が違います" }
            fun blocks(o: com.google.gson.JsonObject) = o.entrySet().associate { (k, v) -> k to v.asJsonArray.map { placed(it.asJsonObject, "sprite") } }
            infos = blocks(root.getAsJsonObject("infos")).mapKeys { it.key.toInt() }
            labels = blocks(root.getAsJsonObject("labels"))
        }
    }

    override val view = "passive-tree"
    override val muted = false
    override val operationActive = false
    override val ownsEffects = true
    override val live = true

    private var zoomIndex = 1
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

    init {
        val start = account()?.journey?.job?.let(CorePassiveTree::start)
        val spot = start?.let { art.spots[it] } ?: Art.Spot(art.width / 2, art.height / 2)
        centreOn(spot.x, spot.y)
    }

    private fun centreOn(x: Double, y: Double) {
        panX = (VIEW_W / 2 - x * zoom).coerceIn(minX(), MARGIN)
        panY = (TOP_BAR + (1080 - TOP_BAR) / 2 - y * zoom).coerceIn(minY(), TOP_BAR + MARGIN)
    }
    private fun minX() = minOf(0.0, VIEW_W - art.width * zoom) - MARGIN
    private fun minY() = minOf(TOP_BAR, 1080 - art.height * zoom) - MARGIN

    override fun pointer(x: Double, y: Double) {
        val dx = x / SCALE
        val dy = (y - TOP) / SCALE
        pointerX = dx; pointerY = dy
        fun push(depth: Double) = SPEED * ((EDGE - depth) / EDGE).coerceIn(0.0, 1.0).let { it * it }
        targetVx = when { dx < EDGE -> push(dx); dx > VIEW_W - EDGE && dx < VIEW_W -> -push(VIEW_W - dx); else -> 0.0 }
        targetVy = when { dy < TOP_BAR + EDGE && dy > TOP_BAR -> push(dy - TOP_BAR); dy > 1080 - EDGE && dx < VIEW_W -> -push(1080 - dy); else -> 0.0 }
    }

    override fun tick(nowMs: Long): dev.projects.webui.ForgeUiReceipt? {
        vx += (targetVx - vx) * 0.25
        vy += (targetVy - vy) * 0.25
        panX = (panX + vx).coerceIn(minX(), MARGIN)
        panY = (panY + vy).coerceIn(minY(), TOP_BAR + MARGIN)
        return null
    }

    /** The node whose face lies under the pointer, if any. */
    private fun nodeAt(x: Double, y: Double): Int? {
        if (x >= VIEW_W || y < TOP_BAR) return null
        val tx = (x - panX) / zoom; val ty = (y - panY) / zoom
        return art.spots.indices.minByOrNull { (art.spots[it].x - tx).let { d -> d * d } + (art.spots[it].y - ty).let { d -> d * d } }
            ?.takeIf { i -> kotlin.math.hypot(art.spots[i].x - tx, art.spots[i].y - ty) * zoom <= HIT * maxOf(zoom, 0.7) }
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
            "reset" -> { if (taken.isEmpty() || busy) return false; send(a, CoreAction.ResetPassives) }
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

    private fun send(a: CoreAccount, operation: CoreAction) {
        busy = true; failed = false
        act(operation, a.revision) { ok -> busy = false; failed = !ok }
    }

    private fun zoomBy(step: Int) {
        val next = (zoomIndex + step).coerceIn(ZOOMS.indices)
        if (next == zoomIndex) return
        // Keep the tree point at the middle of the view where it is.
        val cx = (VIEW_W / 2 - panX) / zoom; val cy = (TOP_BAR + (1080 - TOP_BAR) / 2 - panY) / zoom
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
    private fun sprite(id: String, s: UiSprite, x: Double, y: Double, w: Double, h: Double, depth: Int) {
        nodes += UiNode(id, box(x, y, w, h), "", emptyMap(), null, null, true, depth, s)
    }
    private fun block(id: String, parts: List<Art.Placed>?, x: Double, y: Double, depth: Int, scale: Double = 1.0) =
        parts?.forEachIndexed { k, p -> sprite("$id-$k", p.sprite, x + p.x * scale, y + p.y * scale, p.sprite.width * scale, p.sprite.height * scale, depth) }
    private fun width(parts: List<Art.Placed>?) = parts?.sumOf { it.sprite.width.toDouble() } ?: 0.0
    private fun number(id: String, x: Double, y: Double, w: Double, value: String, size: Double, color: String, depth: Int, align: String = "left") {
        val page = "v3num${size.toInt()}".takeIf(dev.projects.webui.Polish05FontMetrics::has) ?: "v3num"
        nodes += UiNode(id, box(x, y, w, size * 1.25), value,
            mapOf("font-size" to "${size * SCALE}px", "color" to color, "text-align" to align, "font-family" to "projects_ui_polish05:$page"),
            null, null, true, depth)
    }

    override fun scene(light: ForgeLightPhase): UiScene {
        nodes.clear()
        val a = account()
        val job = a?.journey?.job ?: CoreClass.WARRIOR
        val taken = a?.journey?.build?.passives.orEmpty()
        val start = CorePassiveTree.start(job)
        val lit = taken + start
        val budget = a?.journey?.let(CorePassiveTree::budget) ?: 0
        val hovered = nodeAt(pointerX, pointerY)
        val path = (hovered ?: selected)?.let { CorePassiveTree.pathTo(job, taken, it) }.orEmpty().toSet()

        rect("bg", 0.0, 0.0, 1920.0, 1080.0, "#ff0e1018", 0)
        for (t in art.tiles) {
            val x = panX + t.x * zoom; val y = panY + t.y * zoom
            val w = t.sprite.width * zoom; val h = t.sprite.height * zoom
            if (x > VIEW_W || y > 1080 || x + w < 0 || y + h < TOP_BAR) continue
            sprite("t-${t.x.toInt()}-${t.y.toInt()}", t.sprite, x, y, w, h, 1)
        }
        fun visible(x: Double, y: Double) = x > -40 && x < VIEW_W + 40 && y > TOP_BAR - 40 && y < 1120
        for ((pair, parts) in art.edges) {
            val (p, q) = pair
            if (p !in lit || q !in lit) continue
            for ((k, part) in parts.withIndex()) {
                val x = panX + part.x * zoom; val y = panY + part.y * zoom
                if (!visible(x, y) && !visible(x + part.sprite.width * zoom, y + part.sprite.height * zoom)) continue
                sprite("e$p-$q-$k", part.sprite, x, y, part.sprite.width * zoom, part.sprite.height * zoom, 2)
            }
        }
        fun mark(id: String, name: String, node: Int, depth: Int) {
            val s = art.sprites[name] ?: return
            val spot = art.spots[node]
            val x = panX + spot.x * zoom; val y = panY + spot.y * zoom
            if (!visible(x, y)) return
            sprite(id, s, x - s.width * zoom / 2, y - s.height * zoom / 2, s.width * zoom, s.height * zoom, depth)
        }
        for (i in lit) mark("f$i", "face_${CorePassiveTree.nodes[i].kind}", i, 3)
        // Nodes one step away glow green; the path the pointer would take is outlined white.
        if (taken.size < budget) for (i in lit) for (m in CorePassiveTree.nodes[i].links)
            if (m !in lit && CorePassiveTree.nodes[m].ready) mark("r$m", "reach_${CorePassiveTree.nodes[m].kind}", m, 4)
        for (i in path) mark("p$i", "ring_${CorePassiveTree.nodes[i].kind}", i, 5)
        selected?.let { mark("sel", "ring_${CorePassiveTree.nodes[it].kind}", it, 6) }
        rect("tree", 0.0, TOP_BAR, VIEW_W, 1080 - TOP_BAR, "#00000000", 7, "tree", "#00000000")

        // Header: title, class, points, how to move.
        rect("top", 0.0, 0.0, 1920.0, TOP_BAR, "#ff0c0d12", 10)
        rect("top-l", 0.0, TOP_BAR - 2, 1920.0, 2.0, "#ff5a4c34", 11)
        block("title", art.labels["title"], 36.0, 16.0, 12)
        block("job", art.labels["job_${job.name}"], 330.0, 26.0, 12)
        block("pts-l", art.labels["points"], 330.0 + width(art.labels["job_${job.name}"]) + 24, 30.0, 12)
        number("pts", 330.0 + width(art.labels["job_${job.name}"]) + 140, 24.0, 160.0, "${budget - taken.size} / $budget", 26.0, "#f2d48a", 12)
        block("hint", art.labels["hint"], 1000.0, 32.0, 12)

        panel(a, job, taken, budget, hovered ?: selected, path)
        return UiScene(800.0, 480.0, nodes.toList())
    }

    private fun panel(a: CoreAccount?, job: CoreClass, taken: Set<Int>, budget: Int, shown: Int?, path: Set<Int>) {
        val x = VIEW_W; val ix = x + 28
        rect("pn", x, TOP_BAR, 1920 - x, 1080 - TOP_BAR, "#ff101116", 10)
        rect("pn-l", x, TOP_BAR, 2.0, 1080 - TOP_BAR, "#ff5a4c34", 11)
        rect("zoom-in", 1920.0 - 132, TOP_BAR + 18, 48.0, 48.0, "#ff202126", 12, "zoom:in", "#ff2a2416")
        number("zoom-in-t", 1920.0 - 132, TOP_BAR + 26, 48.0, "+", 28.0, "#e6e2d8", 13, "center")
        rect("zoom-out", 1920.0 - 76, TOP_BAR + 18, 48.0, 48.0, "#ff202126", 12, "zoom:out", "#ff2a2416")
        number("zoom-out-t", 1920.0 - 76, TOP_BAR + 26, 48.0, "-", 28.0, "#e6e2d8", 13, "center")
        val node = shown?.let { CorePassiveTree.nodes[it] }
        if (node != null) {
            block("info", art.infos[node.id], ix, TOP_BAR + 30, 12)
            val status = when {
                a == null -> null
                a.activeRun != null -> "hub"
                node.kind == CorePassiveKind.START -> null
                !node.ready -> "soon"
                node.id in taken -> "taken"
                path.isEmpty() -> "far"
                taken.size + path.size > budget -> "nopoint"
                else -> "need"
            }
            val sy = TOP_BAR + 330
            block("st", status?.let { art.labels[it] }, ix, sy, 12)
            if (status == "need" || status == "nopoint") {
                number("st-n", ix + 60, sy - 2, 80.0, "${path.size}", 26.0, "#9af0a0", 12)
                if (status == "need") block("st-p", art.labels["pt"], ix + 100, sy, 12)
            }
        }
        // Confirm: take the path to the selected node, or refund it (with the silver it costs).
        val sel = selected?.let { CorePassiveTree.nodes[it] }
        val price = a?.journey?.let(CorePassiveTree::refundPrice) ?: 0L
        val owned = sel != null && sel.id in taken
        val refundCount = if (owned && a != null) taken.size - CorePassiveTree.refund(job, taken, sel!!.id).size else 0
        val cost = price * refundCount
        val ready = a != null && a.activeRun == null && !busy && sel != null && (
            if (owned) a.silver >= cost
            else CorePassiveTree.pathTo(job, taken, sel.id)?.let { taken.size + it.size <= budget } == true)
        val by = 1080.0 - 220
        if (ready) rect("go", ix, by, 1920 - ix - 28, 72.0, "#ffe8b652", 12, "confirm", "#fff2cc78")
        else rect("go-off", ix, by, 1920 - ix - 28, 72.0, "#ff1c1d22", 12)
        val label = art.labels[if (owned) "refund" else "take"]
        block("go-t", label, ix + (1920 - ix - 28 - width(label)) / 2, by + 16, 13)
        if (owned) {
            block("cost-l", art.labels["cost"], ix, by - 34, 12)
            number("cost", ix + 160, by - 38, 200.0, "$cost", 22.0, if (a != null && a.silver >= cost) "#e6e2d8" else "#ff9a8a", 12)
        }
        block("silver-l", art.labels["silver"], ix, 1080.0 - 128, 12)
        number("silver", ix + 90, 1080.0 - 132, 200.0, "${a?.silver ?: 0}", 22.0, "#e6e2d8", 12)
        val resetOk = a != null && a.activeRun == null && !busy && taken.isNotEmpty() && a.silver >= price * taken.size
        rect("reset", ix, 1080.0 - 90, 200.0, 56.0, if (resetOk) "#ff2a1c1c" else "#ff1c1d22", 12, if (resetOk) "reset" else null, "#ff3a2424")
        block("reset-t", art.labels["reset"], ix + 100 - width(art.labels["reset"]) / 2, 1080.0 - 76, 13)
        rect("close", 1920.0 - 28 - 180, 1080.0 - 90, 180.0, 56.0, "#ff202126", 12, "close", "#ff2a2416")
        block("close-t", art.labels["close"], 1920.0 - 28 - 90 - width(art.labels["close"]) / 2, 1080.0 - 76, 13)
        if (failed) block("failed", art.labels["failed"], ix, by - 80, 12)
    }

    companion object {
        private const val SCALE = 800.0 / 1920.0
        private const val TOP = (480.0 - 1080.0 * SCALE) / 2
        private const val TOP_BAR = 84.0
        private const val VIEW_W = 1460.0
        private const val EDGE = 56.0
        private const val SPEED = 30.0
        private const val MARGIN = 200.0
        private const val HIT = 22.0
        private val ZOOMS = listOf(0.55, 1.0, 1.5)
    }
}
