package dev.projects.server.coreloop

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.projects.server.questmap.QuestGatheringDiscipline
import dev.projects.server.questmap.QuestTerrainStyle
import dev.projects.webui.Box
import dev.projects.webui.ForgeLightPhase
import dev.projects.webui.ForgeUiFlow
import dev.projects.webui.UiNode
import dev.projects.webui.UiScene
import dev.projects.webui.UiSprite
import dev.projects.webui.Polish05FontMetrics

/**
 * 開拓大陸: the continent map screen. Drawn in 1920×1080 design units like the forge.
 * The map is a grid of sprite tiles that pans when the pointer rests on a screen edge.
 * Clicking a zone marker opens the detail panel on the right; the panel's button departs.
 * Everything the panel says comes from [CoreFrontierZones] and the player's account.
 */
internal class AtlasMapFlow(
    private val atlas: Atlas,
    private val account: () -> CoreAccount?,
    private val depart: (zone: Int) -> Unit,
    private val leave: () -> Unit,
) : ForgeUiFlow {
    /** Parsed once at startup from polish05/atlas-map.json. */
    class Atlas(json: ByteArray, forgeFontMap: ByteArray) {
        data class Tile(val sprite: UiSprite, val x: Int, val y: Int)
        /** Where a painted marker sits; only markers with a [CoreFrontierZones] entry are drawn. */
        data class Zone(val id: Int, val tier: Int, val biome: String, val pvp: Boolean, val hub: Boolean, val kind: String?,
                        val cx: Int, val cy: Int, val ax: Int, val ay: Int)
        val width: Int
        val height: Int
        val tiles: List<Tile>
        val cloud: UiSprite?
        val art: Map<String, UiSprite>
        val round: Map<String, UiSprite>
        val zones: List<Zone>
        init {
            val root = JsonParser.parseString(json.toString(Charsets.UTF_8)).asJsonObject
            val sprites = root.getAsJsonObject("sprites").entrySet().associate { (name, v) ->
                val o = v.asJsonObject
                name to UiSprite(o["char"].asString, o["font"].asString, o["width"].asInt, o["height"].asInt)
            }
            width = root["width"].asInt; height = root["height"].asInt
            tiles = root.getAsJsonArray("tiles").map { t -> t.asJsonObject.let { Tile(sprites.getValue(it["name"].asString), it["x"].asInt, it["y"].asInt) } }
            cloud = sprites["fx_cloud"]
            art = sprites
            round = JsonParser.parseString(forgeFontMap.toString(Charsets.UTF_8)).asJsonObject.entrySet()
                .filter { it.key.startsWith("v3_round_") }
                .associate { (k, v) -> v.asJsonObject.let { k.removePrefix("v3_round_") to UiSprite(it["char"].asString, it["font"].asString, it["width"].asInt, it["height"].asInt) } }
            zones = root.getAsJsonArray("zones").map { e ->
                val z: JsonObject = e.asJsonObject
                Zone(z["id"].asInt, z["tier"].asInt, z["biome"].asString, z["pvp"].asBoolean, z["hub"].asBoolean,
                    z["kind"]?.takeUnless { it.isJsonNull }?.asString, z["cx"].asInt, z["cy"].asInt, z["ax"].asInt, z["ay"].asInt)
            }
        }
    }

    override val view = "atlas"
    override val muted = false
    override val operationActive = false
    override val ownsEffects = true
    override val live = true

    /** Painted markers that are real destinations (or the harbor). Wilderness/PvP markers stay hidden. */
    private val markers = atlas.zones.mapNotNull { z -> CoreFrontierZones.zone(z.id)?.let { z to it } }
    private val landmarks = markers.filter { (z, _) -> z.kind in setOf("castle", "fortress", "pagoda") }.map { it.first.id }.toSet()
    private val home = markers.firstOrNull { it.second.hub }?.first ?: atlas.zones.first()
    private var selected: Int? = null
    private var selectedAt = 0L
    private var hovered: Int? = null
    private var panX = (960.0 - home.cx).coerceIn(minX(), maxPan())
    private var panY = (620.0 - home.cy).coerceIn(minY(), maxPan())
    /** Where the camera glides after a click; cleared as soon as the player edge-scrolls. */
    private var glide: Pair<Double, Double>? = null
    private var targetVx = 0.0
    private var targetVy = 0.0
    private var vx = 0.0
    private var vy = 0.0
    private val openedAt = System.currentTimeMillis()

    private fun viewWidth() = if (selected != null) PANEL_X - 20.0 else 1920.0
    private fun minX() = minOf(0.0, viewWidth() - atlas.width) - SLACK
    private fun minY() = minOf(0.0, 1080.0 - atlas.height) - SLACK
    private fun maxPan() = SLACK

    override fun pointer(x: Double, y: Double) {
        val dx = x / SCALE
        val dy = (y - TOP) / SCALE
        val right = viewWidth()
        fun push(depth: Double) = SPEED * ((EDGE - depth) / EDGE).coerceIn(0.0, 1.0).let { it * it }
        targetVx = when { dx < EDGE -> push(dx); dx > right - EDGE && dx < right -> -push(right - dx); else -> 0.0 }
        targetVy = when { dy < EDGE -> push(dy); dy > 1080 - EDGE -> -push(1080 - dy); else -> 0.0 }
    }

    override fun tick(nowMs: Long): dev.projects.webui.ForgeUiReceipt? {
        vx += (targetVx - vx) * 0.25
        vy += (targetVy - vy) * 0.25
        if (targetVx != 0.0 || targetVy != 0.0) glide = null
        glide?.let { (gx, gy) ->
            panX += (gx - panX) * 0.16; panY += (gy - panY) * 0.16
            if (kotlin.math.abs(gx - panX) < 0.5 && kotlin.math.abs(gy - panY) < 0.5) glide = null
        }
        panX = (panX + vx).coerceIn(minX(), maxPan())
        panY = (panY + vy).coerceIn(minY(), maxPan())
        return null
    }

    override fun hover(id: String?) {
        hovered = id?.takeIf { it.startsWith("z") && it.endsWith("-hit") }?.removePrefix("z")?.removeSuffix("-hit")?.toIntOrNull()
    }

    override fun action(action: String): Boolean {
        when {
            action.startsWith("zone:") -> {
                val id = action.removePrefix("zone:").toIntOrNull() ?: return false
                val z = markers.firstOrNull { it.first.id == id }?.first ?: return false
                if (selected == id) return false
                if (selected == null) selectedAt = System.currentTimeMillis()
                selected = id
                glide = (720.0 - z.ax).coerceIn(minX(), maxPan()) to (600.0 - z.ay).coerceIn(minY(), maxPan())
            }
            action == "deselect" -> { if (selected == null) return false; selected = null }
            action == "go" -> {
                val zone = CoreFrontierZones.zone(selected) ?: return false
                if (zone.hub) leave()
                else {
                    val a = account() ?: return false
                    if (CoreFrontierZones.departure(a, zone).blocked != null) return false
                    depart(zone.id)
                }
            }
            else -> return false
        }
        return true
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
    /** Forge-style rounded fill: a cross of panels plus four tinted quarter discs. */
    private fun round(id: String, x: Double, y: Double, w: Double, h: Double, r: Double, color: String, alpha: Double, depth: Int,
                      action: String? = null, hover: String? = null) {
        val argb = String.format("#%02x%s", (alpha * 255).toInt().coerceIn(0, 255), color.removePrefix("#"))
        rect("$id-c", x + r, y, w - 2 * r, h, argb, depth, action, hover)
        rect("$id-l", x, y + r, r, h - 2 * r, argb, depth)
        rect("$id-r", x + w - r, y + r, r, h - 2 * r, argb, depth)
        for ((k, cx, cy) in listOf(Triple("tl", x, y), Triple("tr", x + w - r, y), Triple("bl", x, y + h - r), Triple("br", x + w - r, y + h - r))) {
            val sprite = atlas.round[k] ?: continue
            nodes += UiNode("$id-$k", box(cx, cy, r, r), "", buildMap {
                put("sprite-color", color)
                if (alpha < 1.0) put("opacity", String.format(java.util.Locale.US, "%.3f", alpha))
            }, null, null, true, depth, sprite)
        }
    }

    private fun markerArt(z: Atlas.Zone, f: CoreFrontierZone) = when {
        f.hub -> "badge_hub"
        z.id in landmarks -> "badge_boss"
        else -> "pin_${f.terrain.name.lowercase()}"
    }

    override fun scene(light: ForgeLightPhase): UiScene {
        nodes.clear()
        val a = account()
        val viewRight = viewWidth()
        rect("bg", 0.0, 0.0, 1920.0, 1080.0, "#ff0a0d1e", 0)
        for (t in atlas.tiles) {
            val x = panX + t.x; val y = panY + t.y
            if (x > viewRight || y > 1080 || x + t.sprite.width < 0 || y + t.sprite.height < 0) continue
            sprite("tile-${t.x}-${t.y}", t.sprite, x, y, t.sprite.width.toDouble(), t.sprite.height.toDouble(), 0)
        }
        // Markers follow the v8 mock: medallions for landmarks and the harbor, a biome-rimmed plaque for
        // everything else. Locked tiers are dimmed; a gold pip marks a zone whose boss this player has beaten.
        val now = System.currentTimeMillis()
        for ((z, f) in markers) {
            val sel = z.id == selected
            val hot = z.id == hovered && !sel
            val special = f.hub || z.id in landmarks
            val px = panX + z.ax
            val ground = panY + z.ay + 8
            if (px < 40 || px > viewRight - 40 || ground < 40 || ground > 1060) continue
            val locked = !f.hub && a != null && f.tier > a.unlockedMapTier
            val cleared = !f.hub && a?.zoneRecords?.containsKey(f.id) == true
            val grow = if (sel) 1.12 else 1.0
            val w = (if (special) 64.0 else 44.0) * grow
            val h = (if (special) 64.0 else 52.0) * grow
            val lift = when { sel -> 4 + 4 * kotlin.math.sin((now - selectedAt) / 280.0); hot -> 6.0; else -> 0.0 }
            val top = ground - h - lift
            if (sel) {
                val ring = atlas.art["wave_gold"]
                for (k in 0 until 2) {
                    val phase = ((now + k * 900) % 1800) / 1800.0
                    val rw = 60.0 + 100 * phase
                    // Opaque only: a translucent glyph hides whatever is drawn behind it after it.
                    if (phase < 0.8) ring?.let { sprite("z${z.id}-wv$k", it, px - rw / 2, ground - rw * 20 / 96, rw, rw * 20 / 48, 1) }
                }
            }
            atlas.art[markerArt(z, f)]?.let { sprite("z${z.id}-i", it, px - w / 2, top, w, h, 2, if (locked && !sel) LOCKED_TINT else null) }
            rect("z${z.id}-hit", px - w / 2, top, w, h, "#00000000", 2, "zone:${z.id}", "#00000000")
            if (cleared) {
                rect("z${z.id}-ok-o", px + w / 2 - 12, top - 2, 14.0, 14.0, "#ff1a1208", 4)
                rect("z${z.id}-ok", px + w / 2 - 10, top, 10.0, 10.0, "#ffe8c878", 5)
            }
            if (sel) {
                val body = if (special) h else h * 20 / 26
                val side = maxOf(w, body) + 32
                atlas.art["select"]?.let { sprite("z${z.id}-sel", it, px - side / 2, top + body / 2 - side / 2, side, side, 3) }
            }
            if (special || sel || hot) {
                val name = f.name
                val family = if (sel) "v3b" else "v3m"
                val size = if (sel) 20.0 else 18.0
                val tagW = measure(name, family, size) + 26
                val tagH = size * 1.25 + 10
                val tagY = ground + if (sel) 20 else 4
                rect("z${z.id}-tag", px - tagW / 2, tagY, tagW, tagH, if (sel) "#ff16130e" else "#ff0e0f13", 9)
                rect("z${z.id}-tag-t", px - tagW / 2, tagY, tagW, 2.0, if (sel) "#ffe8c878" else "#ff5a4c34", 9)
                rect("z${z.id}-tag-b", px - tagW / 2, tagY + tagH - 2, tagW, 2.0, if (sel) "#ffe8c878" else "#ff5a4c34", 9)
                text("z${z.id}-n", px - tagW / 2, tagY + 5, tagW, name, size,
                    if (sel) "#f2d48a" else if (locked) "#8a867e" else "#f4eedf", 5, "center", family)
            }
        }
        atlas.art["title"]?.let { sprite("title-plate", it, 22.0, 18.0, 256.0, 64.0, 20) }
        text("title", 90.0, 33.0, 180.0, "開拓大陸", 24.0, "#f4eedf", 21, "left", "v3b")
        CoreFrontierZones.zone(selected)?.let { panel(it, a) }
        clouds()
        return UiScene(800.0, 480.0, nodes.toList())
    }

    /** The map is revealed by a bank of clouds that parts outward when the screen opens. */
    private fun clouds() {
        val cloud = atlas.cloud ?: return
        val t = (System.currentTimeMillis() - openedAt) / 1100.0
        if (t >= 1.0) return
        val ease = 1 - (1 - t) * (1 - t) * (1 - t)
        rect("veil", 0.0, 0.0, 1920.0, 1080.0, String.format("#%02x0a0d1e", ((1 - ease) * 230).toInt().coerceIn(0, 255)), 30)
        for (i in 0 until 16) {
            val col = i % 4; val row = i / 4
            val sx = col * 520.0 - 160 + (row % 2) * 180; val sy = row * 280.0 - 80
            val dx = sx + 260 - 960; val dy = sy + 146 - 540
            val len = kotlin.math.max(1.0, kotlin.math.hypot(dx, dy))
            val push = ease * 1300
            val x = sx + dx / len * push; val y = sy + dy / len * push
            val grow = 1.0 + ease * 0.5
            nodes += UiNode("cloud$i", box(x, y, 640.0 * grow, 352.0 * grow), "", emptyMap(), null, null, true, 32 + (i % 3), cloud)
        }
    }

    private data class Row(val name: String, val sub: String, val icon: String)

    private fun panel(f: CoreFrontierZone, a: CoreAccount?) {
        // Slides in from the right the first time a zone is picked.
        val t = ((System.currentTimeMillis() - selectedAt) / 320.0).coerceIn(0.0, 1.0)
        val x = PANEL_X + 56 * (1 - t) * (1 - t) * (1 - t); val y = 24.0; val w = 1920.0 - PANEL_X - 28; val h = 1032.0
        for ((name, sprite) in atlas.art) {
            if (!name.startsWith("panel_")) continue
            val (tx, ty) = name.removePrefix("panel_").split('_').map { it.toDouble() }
            nodes += UiNode("p-$name", box(x + tx, y + ty, sprite.width.toDouble(), sprite.height.toDouble()), "", emptyMap(), null, null, true, 10, sprite)
        }
        val pad = 28.0; val ix = x + pad; val iw = w - pad * 2
        val departure = a?.let { CoreFrontierZones.departure(it, f) }
        val locked = !f.hub && a != null && f.tier > a.unlockedMapTier
        val record = a?.zoneRecords?.get(f.id)

        // Header: state and tier pills, the enemy level this player would meet, close.
        val (type, typeColor, typeText) = when {
            f.hub -> Triple("拠点", "#18301c", "#9af0a0")
            locked -> Triple("未解放", "#2a2224", "#a29e97")
            record != null -> Triple("踏破済み", "#302610", "#e8c878")
            else -> Triple("遠征地", "#172338", "#a9c8ff")
        }
        round("p-type", ix, y + 28, 120.0, 32.0, 14.0, typeColor, 1.0, 16)
        text("p-type-t", ix, y + 33, 120.0, type, 15.0, typeText, 17, "center", "v3m")
        round("p-tier", ix + 128, y + 28, 56.0, 32.0, 14.0, "#e8c878", 1.0, 16)
        text("p-tier-t", ix + 128, y + 33, 56.0, "T${f.tier}", 14.0, "#2a1a08", 17, "center")
        if (departure != null && !f.hub) text("p-lv", ix + 196, y + 33, 160.0, "敵Lv ${departure.level}", 14.0, "#f8a090", 13)
        round("p-x", x + w - pad - 40, y + 24, 40.0, 40.0, 10.0, "#202126", 1.0, 16, "deselect", "#ff2a2416")
        text("p-x-t", x + w - pad - 40, y + 31, 40.0, "×", 20.0, "#a29e97", 17, "center", "v3b")

        // Portrait (96 frame at 28,84 on the plate) and name.
        val z = markers.first { it.second.id == f.id }.first
        val portrait = markerArt(z, f)
        val (pw, ph) = if (portrait.startsWith("badge_")) 64.0 to 64.0 else 55.0 to 65.0
        atlas.art[portrait]?.let { sprite("p-port-i", it, ix + 48 - pw / 2, y + 132 - ph / 2, pw, ph, 13) }
        text("p-name", ix + 116, y + 102, iw - 116, f.name, 26.0, "#f4eedf", 13, "left", "v3b")
        val sub = f.specialty?.let { "${TERRAIN.getValue(f.terrain)} · 特産 ${it.displayName}" } ?: "遠征の出発点"
        text("p-sub", ix + 116, y + 142, iw - 116, sub, 15.0, "#a29e97", 13, "left", "v3r")

        // Card at 240..316: the boss at the end of the road (the harbor's card describes the harbor).
        val boss = f.boss
        text("p-card-l", ix, y + 214, iw, if (boss != null) "道の終わりのボス" else "港", 14.0, "#8a867e", 13, "left", "v3m")
        atlas.art[if (boss != null) "badge_boss" else "badge_hub"]?.let { sprite("p-card-i", it, ix + 14, y + 254, 48.0, 48.0, 13) }
        text("p-card-n", ix + 74, y + 252, iw - 90, boss?.displayName ?: "開拓港", 20.0, "#f4eedf", 13, "left", "v3b")
        text("p-card-s", ix + 74, y + 286, iw - 90,
            if (boss != null) "弱点 ${WEAKNESS[boss.weakness] ?: "—"} · 倒すと踏破" else "遠征から戻るのもここ", 14.0, "#e8c878", 13, "left", "v3r")

        // What is here: one card per row, each with its icon.
        text("p-do-l", ix, y + 336, iw, "このゾーンでできること", 14.0, "#8a867e", 13, "left", "v3m")
        val rows = f.specialty?.let { spec -> listOf(
            Row("討伐遠征", "毎回ちがう島 · 敵の群れ · 最後にボス", "pin_${f.terrain.name.lowercase()}"),
            Row("採取", "${spec.displayName}の採取点が半分を占める", MATERIAL.getValue(spec)),
            Row("寄り道", "隠し宝箱 · 裂け目 · 儀式", "mat_shard"),
        ) } ?: listOf(
            Row("港の施設", "鍛冶工房 · 交易市場 · 所持品目録", "badge_hub"),
            Row("遠征受付", "この地図で行き先を選んで出発", "pin_verdant"),
        )
        rows.forEachIndexed { i, r ->
            val ry = y + 362 + i * 82
            atlas.art["card_0"]?.let { nodes += UiNode("p-r$i-c0", box(ix, ry, 256.0, 72.0), "", emptyMap(), null, null, true, 11, it) }
            atlas.art["card_1"]?.let { nodes += UiNode("p-r$i-c1", box(ix + 256, ry, 120.0, 72.0), "", emptyMap(), null, null, true, 11, it) }
            val (iw2, ih2) = when { r.icon.startsWith("pin_") -> 33.0 to 39.0; r.icon.startsWith("mat_") -> 36.0 to 36.0; else -> 40.0 to 40.0 }
            atlas.art[r.icon]?.let { sprite("p-r$i-i", it, ix + 34 - iw2 / 2, ry + 36 - ih2 / 2, iw2, ih2, 13) }
            text("p-r$i-n", ix + 66, ry + 13, iw - 180, r.name, 16.0, "#f4eedf", 13, "left", "v3b")
            text("p-r$i-s", ix + 66, ry + 41, iw - 80, r.sub, 14.0, "#8a867e", 13, "left", "v3r")
        }

        // Info card (620..728 on the plate): three facts read from the account.
        val info: List<Triple<String, String, String>> = if (f.hub) {
            val cleared = a?.zoneRecords?.keys?.count { CoreFrontierZones.expedition(it) != null } ?: 0
            listOf(Triple("解放", "T${a?.unlockedMapTier ?: 1} まで", "#e6e2d8"),
                Triple("踏破", "$cleared / ${CoreFrontierZones.expeditions.size} 地域", "#e6e2d8"),
                Triple("記録", "ボス討伐 合計 ${a?.zoneRecords?.values?.sumOf { it.clears } ?: 0} 回", "#a29e97"))
        } else listOf(
            if (locked) Triple("解放", CoreFrontierZones.unlockHint(f.tier), "#ff9a8a")
            else Triple("費用", departure?.cost?.let { c -> "${c.displayName} ×1（所持 ${a?.amount(c) ?: 0}）" } ?: "無料",
                if (departure?.blocked != null) "#ff9a8a" else "#e6e2d8"),
            Triple("装備", a?.let { "武器 T${it.weaponTier} · 防具 T${it.armorTier}" } ?: "—",
                if (a != null && a.weaponTier < f.tier) "#ff9a8a" else "#e6e2d8"),
            Triple("記録", record?.let { "踏破 ${it.clears} 回 · 最高 敵Lv ${it.bestLevel}" } ?: "まだ踏破していない",
                if (record != null) "#e8c878" else "#a29e97"),
        )
        info.forEachIndexed { i, (k, v, color) ->
            val ly = y + 628 + i * 36
            text("p-i$i-k", ix + 18, ly, 100.0, k, 15.0, "#8a867e", 13, "left", "v3m")
            text("p-i$i-v", ix + 118, ly, iw - 136, v, 15.0, color, 13, "left", "v3r")
        }

        // Materials: one slot per gathering discipline, specialty first, with its base site count.
        f.specialty?.let { spec ->
            text("p-mat-l", ix, y + 750, 240.0, "採取できる素材 · T${f.tier}", 14.0, "#8a867e", 13, "left", "v3m")
            text("p-mat-r", ix + iw - 200, y + 752, 200.0, "採取点の数", 13.0, "#6e6a64", 13, "right", "v3r")
            CoreFrontierZones.gatheringSites(spec).entries.forEachIndexed { i, (discipline, count) ->
                val sx = ix + i * 66; val sy = y + 776
                atlas.art["slot"]?.let { sprite("p-m$i-s", it, sx, sy, 60.0, 60.0, 12) }
                atlas.art[MATERIAL.getValue(discipline)]?.let { sprite("p-m$i-i", it, sx + 9, sy + 9, 42.0, 42.0, 13) }
                nodes += UiNode("p-m$i-n", box(sx + 4, sy + 38, 52.0, 17.5), "×$count",
                    mapOf("font-size" to "${14 * SCALE}px", "color" to if (discipline == spec) "#f2d48a" else "#ffffff",
                        "text-align" to "right", "text-shadow" to "true",
                        "font-family" to "projects_ui_polish05:" + ("v3num14".takeIf(Polish05FontMetrics::has) ?: "v3num")), null, null, true, 14)
            }
        }

        // Depart.
        val by = y + h - pad - 72
        val blocked = if (f.hub) null else departure?.blocked ?: if (a == null) "読み込み中" else null
        val goText = when { f.hub -> "港へ戻る"; blocked != null -> blocked; else -> "遠征に出る" }
        if (blocked == null) rect("p-go", ix, by, iw, 72.0, "#00000000", 12, "go", "#30ffffff")
        else rect("p-go-off", ix + 2, by, iw - 4, 70.0, "#ff1c1d22", 16)
        text("p-go-t", ix, by + 22, iw, goText, 24.0, if (blocked == null) "#1a1208" else "#5e5a54", 17, "center", "v3b")
    }

    companion object {
        private const val SCALE = 800.0 / 1920.0
        private const val TOP = (480.0 - 1080.0 * SCALE) / 2
        private const val EDGE = 56.0
        private const val SPEED = 28.0
        private const val PANEL_X = 1460.0
        private const val SLACK = 140.0
        private const val LOCKED_TINT = "#ff5a5a5a"
        private val MATERIAL = mapOf(QuestGatheringDiscipline.SKINNING to "mat_hide", QuestGatheringDiscipline.WOODCUTTING to "mat_log",
            QuestGatheringDiscipline.QUARRYING to "mat_stone", QuestGatheringDiscipline.MINING to "mat_ore",
            QuestGatheringDiscipline.HERBALISM to "mat_herb")
        private val TERRAIN = mapOf(QuestTerrainStyle.VERDANT to "緑野", QuestTerrainStyle.SAKURA_GROVE to "桜の森",
            QuestTerrainStyle.SALTMARSH to "塩沼", QuestTerrainStyle.CLIFFLANDS to "断崖", QuestTerrainStyle.HIGHLANDS to "高地",
            QuestTerrainStyle.INFERNAL to "獄炎の地")
        private val WEAKNESS = mapOf("ice" to "氷", "lightning" to "雷", "fire" to "炎")
    }
}
