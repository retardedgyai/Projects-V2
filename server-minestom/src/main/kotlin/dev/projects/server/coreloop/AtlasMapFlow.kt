package dev.projects.server.coreloop

import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
 */
internal class AtlasMapFlow(
    private val atlas: Atlas,
    private val depart: (tier: Int, style: QuestTerrainStyle) -> Unit,
    private val leave: () -> Unit,
) : ForgeUiFlow {
    /** Parsed once at startup from polish05/atlas-map.json. */
    class Atlas(json: ByteArray, forgeFontMap: ByteArray) {
        data class Tile(val sprite: UiSprite, val x: Int, val y: Int)
        data class Zone(val id: Int, val tier: Int, val biome: String, val pvp: Boolean, val hub: Boolean, val kind: String?,
                        val owner: String, val cx: Int, val cy: Int, val ox: Int, val oy: Int, val ax: Int, val ay: Int)
        val width: Int
        val height: Int
        val tiles: List<Tile>
        val icons: Map<String, UiSprite>
        val cloud: UiSprite?
        val banner: UiSprite?
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
            icons = sprites.filterKeys { it.startsWith("icon_") }.mapKeys { it.key.removePrefix("icon_") }
            cloud = sprites["fx_cloud"]
            banner = sprites["fx_banner"]
            art = sprites
            round = JsonParser.parseString(forgeFontMap.toString(Charsets.UTF_8)).asJsonObject.entrySet()
                .filter { it.key.startsWith("v3_round_") }
                .associate { (k, v) -> v.asJsonObject.let { k.removePrefix("v3_round_") to UiSprite(it["char"].asString, it["font"].asString, it["width"].asInt, it["height"].asInt) } }
            zones = root.getAsJsonArray("zones").map { e ->
                val z: JsonObject = e.asJsonObject
                Zone(z["id"].asInt, z["tier"].asInt, z["biome"].asString, z["pvp"].asBoolean, z["hub"].asBoolean,
                    z["kind"]?.takeUnless { it.isJsonNull }?.asString, z["owner"].asString,
                    z["cx"].asInt, z["cy"].asInt, z["ox"].asInt, z["oy"].asInt, z["ax"].asInt, z["ay"].asInt)
            }
        }
    }

    override val view = "atlas"
    override val muted = false
    override val operationActive = false
    override val ownsEffects = true
    override val live = true

    private val names: Map<Int, String> = run {
        val used = mutableMapOf<String, Int>()
        atlas.zones.associate { z ->
            val key = if (z.pvp) "PVP" else z.biome
            val list = NAMES[key] ?: NAMES.getValue("VERDANT")
            z.id to if (z.hub) "開拓港" else list[(used.merge(key, 1, Int::plus)!! - 1) % list.size]
        }
    }
    private val bossZones = atlas.zones.filter { it.kind in setOf("castle", "fortress", "pagoda") }.map { it.id }
    private val home = atlas.zones.firstOrNull { it.hub } ?: atlas.zones.first()
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
                if (selected == id) return false
                if (selected == null) selectedAt = System.currentTimeMillis()
                selected = id
                val z = atlas.zones.first { it.id == id }
                glide = (720.0 - z.ax).coerceIn(minX(), maxPan()) to (600.0 - z.ay).coerceIn(minY(), maxPan())
            }
            action == "deselect" -> { if (selected == null) return false; selected = null }
            action == "go" -> {
                val z = atlas.zones.firstOrNull { it.id == selected } ?: return false
                when {
                    z.hub -> leave()
                    z.pvp -> return false
                    else -> depart(z.tier, QuestTerrainStyle.valueOf(z.biome))
                }
            }
            else -> return false
        }
        return true
    }

    // ------------------------------------------------------------------ drawing (design units)

    private val nodes = mutableListOf<UiNode>()
    private fun box(x: Double, y: Double, w: Double, h: Double) = Box(x * SCALE, TOP + y * SCALE, w * SCALE, h * SCALE)
    private fun item(id: String, key: String, x: Double, y: Double, size: Double, depth: Int) {
        nodes += UiNode(id, box(x, y, size, size), "", emptyMap(), null, key, true, depth)
    }
    private fun itemFor(z: Atlas.Zone) = when {
        z.hub -> "minecraft:compass"
        z.pvp -> "minecraft:iron_sword"
        z.id in bossZones -> "minecraft:wither_skeleton_skull"
        else -> TERRAIN_ITEM[z.biome] ?: "minecraft:grass_block"
    }
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
    private fun frame(id: String, x: Double, y: Double, w: Double, h: Double, color: String, t: Double, depth: Int) {
        rect("$id-t", x, y, w, t, color, depth); rect("$id-b", x, y + h - t, w, t, color, depth)
        rect("$id-l", x, y, t, h, color, depth); rect("$id-r", x + w - t, y, t, h, color, depth)
    }
    private fun goldCorners(id: String, x: Double, y: Double, w: Double, h: Double, depth: Int) {
        for ((k, cx, cy) in listOf(Triple("tl", x - 4, y - 4), Triple("tr", x + w - 16, y - 4), Triple("bl", x - 4, y + h - 16), Triple("br", x + w - 16, y + h - 16))) {
            val horizontalY = if (k.startsWith("t")) cy else cy + 16
            val verticalX = if (k.endsWith("l")) cx else cx + 16
            rect("$id-$k-h", cx, horizontalY, 20.0, 4.0, GOLD, depth)
            rect("$id-$k-v", verticalX, cy, 4.0, 20.0, GOLD, depth)
        }
    }

    override fun scene(light: ForgeLightPhase): UiScene {
        nodes.clear()
        val viewRight = viewWidth()
        rect("bg", 0.0, 0.0, 1920.0, 1080.0, "#ff0a0d1e", 0)
        for (t in atlas.tiles) {
            val x = panX + t.x; val y = panY + t.y
            if (x > viewRight || y > 1080 || x + t.sprite.width < 0 || y + t.sprite.height < 0) continue
            sprite("tile-${t.x}-${t.y}", t.sprite, x, y, t.sprite.width.toDouble(), t.sprite.height.toDouble(), 0)
        }
        // Markers follow the v8 mock: medallions for bosses and the harbor, a shield for the wilds,
        // a biome-rimmed plaque for everything else. Ordinary names appear only on hover or selection.
        val now = System.currentTimeMillis()
        val live = bossZones.firstOrNull()
        for (z in atlas.zones) {
            val sel = z.id == selected
            val hot = z.id == hovered && !sel
            val special = z.hub || z.pvp || z.id in bossZones
            val event = z.id in bossZones.take(2)
            val px = panX + z.ax
            val ground = panY + z.ay + 8
            if (px < 40 || px > viewRight - 40 || ground < 40 || ground > 1060) continue
            val grow = if (sel) 1.12 else 1.0
            val w = (if (special) 64.0 else 44.0) * grow
            val h = (if (special) 64.0 else 52.0) * grow
            val lift = when { sel -> 4 + 4 * kotlin.math.sin((now - selectedAt) / 280.0); hot -> 6.0; else -> 0.0 }
            val top = ground - h - lift
            if (sel || z.id == live) {
                val ring = atlas.art[if (sel) "wave_gold" else "wave_red"]
                for (k in 0 until 2) {
                    val phase = ((now + k * 900) % 1800) / 1800.0
                    val rw = 60.0 + 100 * phase
                    // Opaque only: a translucent glyph hides whatever is drawn behind it after it.
                    if (phase < 0.8) ring?.let { sprite("z${z.id}-wv$k", it, px - rw / 2, ground - rw * 20 / 96, rw, rw * 20 / 48, 1) }
                }
            }
            val art = atlas.art[when { z.hub -> "badge_hub"; z.pvp -> "badge_pvp"; special -> "badge_boss"; else -> "pin_${z.biome.lowercase()}" }]
            art?.let { sprite("z${z.id}-i", it, px - w / 2, top, w, h, 2) }
            rect("z${z.id}-hit", px - w / 2, top, w, h, "#00000000", 2, "zone:${z.id}", "#00000000")
            if (sel) {
                val body = if (special) h else h * 20 / 26
                val side = maxOf(w, body) + 32
                atlas.art["select"]?.let { sprite("z${z.id}-sel", it, px - side / 2, top + body / 2 - side / 2, side, side, 3) }
            }
            if (event) {
                val isLive = z.id == live
                atlas.art[if (isLive) "timer_live" else "timer_soon"]?.let { sprite("z${z.id}-tm", it, px + w / 2 - 12, top - 8, 80.0, 24.0, 6) }
                text("z${z.id}-tmt", px + w / 2 + 12, top - 6, 56.0, if (isLive) "出現中" else "12分", 15.0, if (isLive) "#ffffff" else "#e8c878", 7, "left", "v3b")
            }
            if (special || sel || hot) {
                val name = names.getValue(z.id)
                val size = if (sel) 21.0 else 17.0
                val tagW = name.length * size + 22
                val tagY = ground + 4
                rect("z${z.id}-tag", px - tagW / 2, tagY, tagW, size + 12, if (sel) "#f0141210" else "#c80c0d10", 9)
                rect("z${z.id}-tag-t", px - tagW / 2 + 4, tagY, tagW - 8, 1.5, if (sel) "#ffe8c878" else "#70c8aa6e", 9)
                rect("z${z.id}-tag-b", px - tagW / 2 + 4, tagY + size + 10.5, tagW - 8, 1.5, if (sel) "#ffe8c878" else "#70c8aa6e", 9)
                text("z${z.id}-n", px - tagW / 2, tagY + 4, tagW, name, size,
                    if (sel) "#f2d48a" else if (z.pvp) "#ffb0a0" else "#f4eedf", 5, "center", if (sel) "v3b" else "v3m")
            }
        }
        // Title plate and the one important notice, both drawn from the Chrome-rendered parts.
        atlas.art["title"]?.let { sprite("title-plate", it, 22.0, 18.0, 256.0, 64.0, 20) }
        text("title", 66.0, 34.0, 200.0, "開拓大陸", 24.0, "#f4eedf", 21, "left", "v3b")
        live?.let { id ->
            val message = "霧氷の騎士 出現中 · ${names.getValue(id)}"
            val middle = kotlin.math.ceil(message.length * 19.0 / 32.0).toInt() + 1
            val total = 56.0 + middle * 32 + 24
            val left = 960 - total / 2
            atlas.art["notice_l"]?.let { sprite("notice-l", it, left, 18.0, 56.0, 56.0, 20) }
            atlas.art["notice_m"]?.let { m -> for (i in 0 until middle) sprite("notice-m$i", m, left + 56 + i * 32, 18.0, 32.0, 56.0, 20) }
            atlas.art["notice_r"]?.let { sprite("notice-r", it, left + 56 + middle * 32, 18.0, 24.0, 56.0, 20) }
            text("notice-t", left + 60, 33.0, middle * 32.0, message, 19.0, "#ffd8d0", 21, "left", "v3m")
        }
        selected?.let { panel(atlas.zones.first { z -> z.id == it }) }
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

    private fun panel(z: Atlas.Zone) {
        // Slides in from the right the first time a zone is picked.
        val t = ((System.currentTimeMillis() - selectedAt) / 320.0).coerceIn(0.0, 1.0)
        val x = PANEL_X + 56 * (1 - t) * (1 - t) * (1 - t); val y = 24.0; val w = 1920.0 - PANEL_X - 28; val h = 1032.0
        for ((name, sprite) in atlas.art) {
            if (!name.startsWith("panel_")) continue
            val (tx, ty) = name.removePrefix("panel_").split('_').map { it.toDouble() }
            nodes += UiNode("p-$name", box(x + tx, y + ty, sprite.width.toDouble(), sprite.height.toDouble()), "", emptyMap(), null, null, true, 10, sprite)
        }
        val pad = 28.0; val ix = x + pad; val iw = w - pad * 2

        // Header: kind and tier pills, enemy level, close.
        val type = if (z.hub) "拠点" else if (z.pvp) "荒野 · PvP" else "安全"
        val typeColor = if (z.pvp) "#3a1a18" else if (z.hub) "#18301c" else "#172338"
        round("p-type", ix, y + 28, 120.0, 32.0, 14.0, typeColor, 1.0, 16)
        text("p-type-t", ix, y + 33, 120.0, type, 15.0, if (z.pvp) "#ffb0a0" else if (z.hub) "#9af0a0" else "#a9c8ff", 17, "center", "v3m")
        round("p-tier", ix + 128, y + 28, 56.0, 32.0, 14.0, "#e8c878", 1.0, 16)
        text("p-tier-t", ix + 128, y + 33, 56.0, "T${z.tier}", 14.0, "#2a1a08", 17, "center")
        val lv = listOf("1〜10", "11〜20", "21〜30", "31〜40")[z.tier - 1]
        text("p-lv", ix + 196, y + 33, 160.0, "敵Lv $lv", 14.0, "#f8a090", 13)
        round("p-x", x + w - pad - 40, y + 24, 40.0, 40.0, 10.0, "#202126", 1.0, 16, "deselect", "#ff2a2416")
        text("p-x-t", x + w - pad - 40, y + 31, 40.0, "×", 20.0, "#a29e97", 17, "center", "v3b")

        // Portrait (96 frame at 28,84 on the plate) and name.
        val special = z.hub || z.pvp || z.id in bossZones
        val portrait = when { z.hub -> "badge_hub"; z.pvp -> "badge_pvp"; special -> "badge_boss"; else -> "pin_${z.biome.lowercase()}" }
        val (pw, ph) = if (special) 64.0 to 64.0 else 55.0 to 65.0
        atlas.art[portrait]?.let { sprite("p-port-i", it, ix + 48 - pw / 2, y + 132 - ph / 2, pw, ph, 13) }
        text("p-name", ix + 116, y + 102, iw - 116, names.getValue(z.id), 26.0, "#f4eedf", 13, "left", "v3b")
        val key = if (z.pvp) "PVP" else z.biome
        text("p-sub", ix + 116, y + 142, iw - 116, "${BIOME[key]} · 特産 ${YIELD[key]}", 15.0, "#a29e97", 13, "left", "v3r")

        // Territory (card at 240..316).
        text("p-land-l", ix, y + 214, iw, "領地", 14.0, "#8a867e", 13, "left", "v3m")
        val (guild, color) = GUILD.getValue(z.owner)
        atlas.banner?.let { sprite("p-land-flag", it, ix + 16, y + 254, 44.0, 48.0, 13, "#" + color.takeLast(6)) }
        text("p-land-o", ix + 74, y + 252, iw - 90, if (z.owner == "white") "持ち主なし" else "ギルド「$guild」", 20.0,
            if (z.owner == "white") "#a29e97" else "#" + color.takeLast(6), 13, "left", "v3b")
        text("p-land-w", ix + 74, y + 286, iw - 90, "次の領地戦　土 21:00", 14.0, "#e8c878", 13, "left", "v3r")

        // What is here: one card per row, each with its icon.
        text("p-do-l", ix, y + 336, iw, "このゾーンでできること", 14.0, "#8a867e", 13, "left", "v3m")
        data class Row(val name: String, val sub: String, val whenText: String, val icon: String)
        val rows = buildList {
            if (z.id in bossZones.take(2)) add(Row(if (z.id == bossZones.first()) "ワールドボス" else "フィールドボス",
                "〜40人 · ダメージ順位で報酬", if (z.id == bossZones.first()) "出現中" else "12分", "badge_boss"))
            if (!z.hub) add(Row(if (z.pvp) "荒野" else "遠征口", if (z.pvp) "PvP あり · 準備中" else "入るたびに地形が変わる · 道の先にボス", "",
                if (z.pvp) "badge_pvp" else "pin_${z.biome.lowercase()}"))
            add(Row(if (z.owner == "white") "砦（持ち主なし）" else "${guild}の砦",
                if (z.owner == "white") "領地戦でいちばん貢献したギルドのものに" else "ギルド員はここから出発できる", "", "badge_hub"))
        }
        rows.forEachIndexed { i, r ->
            val ry = y + 362 + i * 82
            atlas.art["card_0"]?.let { nodes += UiNode("p-r$i-c0", box(ix, ry, 256.0, 72.0), "", emptyMap(), null, null, true, 11, it) }
            atlas.art["card_1"]?.let { nodes += UiNode("p-r$i-c1", box(ix + 256, ry, 120.0, 72.0), "", emptyMap(), null, null, true, 11, it) }
            val (iw2, ih2) = if (r.icon.startsWith("pin_")) 33.0 to 39.0 else 40.0 to 40.0
            atlas.art[r.icon]?.let { sprite("p-r$i-i", it, ix + 34 - iw2 / 2, ry + 36 - ih2 / 2, iw2, ih2, 13) }
            text("p-r$i-n", ix + 66, ry + 12, iw - 180, r.name, 18.0, "#f4eedf", 13, "left", "v3b")
            text("p-r$i-s", ix + 66, ry + 42, iw - 80, r.sub, 13.0, "#8a867e", 13, "left", "v3r")
            if (r.whenText.isNotEmpty()) text("p-r$i-w", ix + iw - 118, ry + 14, 100.0, r.whenText, 14.0, "#ff9a8a", 13, "right")
        }

        // Info card (620..728 on the plate).
        val recommend = listOf("冒険Lv 1〜", "冒険Lv 11〜", "冒険Lv 21〜", "冒険Lv 31〜")[z.tier - 1] + " · 武器 T${z.tier}"
        val foes = FOES[key] ?: "—"
        listOf("推奨" to recommend, "敵の傾向" to foes, "記録" to "まだ踏破していない").forEachIndexed { i, (k, v) ->
            val ly = y + 628 + i * 36
            text("p-i$i-k", ix + 18, ly, 100.0, k, 15.0, "#8a867e", 13, "left", "v3m")
            text("p-i$i-v", ix + 118, ly, iw - 136, v, 15.0, if (i == 2) "#a29e97" else "#e6e2d8", 13, "left", "v3r")
        }

        // Materials: Minecraft inventory slots.
        text("p-mat-l", ix, y + 750, 200.0, "主な素材", 14.0, "#8a867e", 13, "left", "v3m")
        text("p-mat-r", ix + iw - 200, y + 752, 200.0, "1回の遠征で", 13.0, "#6e6a64", 13, "right", "v3r")
        (MATS[key] ?: MATS.getValue("VERDANT")).forEachIndexed { i, m ->
            val sx = ix + i * 66; val sy = y + 776
            atlas.art["slot"]?.let { sprite("p-m$i-s", it, sx, sy, 60.0, 60.0, 12) }
            atlas.art["mat_$m"]?.let { sprite("p-m$i-i", it, sx + 9, sy + 9, 42.0, 42.0, 13) }
            nodes += UiNode("p-m$i-n", box(sx + 4, sy + 38, 52.0, 17.5), listOf("×24", "×12", "×6", "×2")[i],
                mapOf("font-size" to "${14 * SCALE}px", "color" to "#ffffff", "text-align" to "right", "text-shadow" to "true",
                    "font-family" to "projects_ui_polish05:" + ("v3num14".takeIf(Polish05FontMetrics::has) ?: "v3num")), null, null, true, 14)
        }

        // Depart.
        val by = y + h - pad - 72
        val goText = when { z.hub -> "港へ戻る"; z.pvp -> "準備中"; z.id == bossZones.firstOrNull() -> "参戦する"; else -> "遠征に出る" }
        val enabled = !z.pvp
        if (enabled) rect("p-go", ix, by, iw, 72.0, "#00000000", 12, "go", "#30ffffff")
        else rect("p-go-off", ix, by, iw, 72.0, "#e0141519", 16)
        text("p-go-t", ix, by + 22, iw, goText, 24.0, if (enabled) "#1a1208" else "#5e5a54", 17, "center", "v3b")
    }

    companion object {
        private const val SCALE = 800.0 / 1920.0
        private const val TOP = (480.0 - 1080.0 * SCALE) / 2
        private const val MARGIN = 40.0
        private const val EDGE = 56.0
        private const val SPEED = 28.0
        private const val PANEL_X = 1460.0
        private const val GOLD = "#ffc8aa6e"
        private const val SLACK = 140.0
        private val MATS = mapOf("VERDANT" to listOf("log", "herb", "hide", "shard"), "SAKURA_GROVE" to listOf("herb", "log", "shard"),
            "SALTMARSH" to listOf("hide", "herb", "log"), "CLIFFLANDS" to listOf("stone", "ore", "shard"),
            "HIGHLANDS" to listOf("stone", "ore", "hide", "shard"), "INFERNAL" to listOf("ore", "stone", "shard"), "PVP" to listOf("ore", "shard"))
        private val GUILD = mapOf("blue" to ("蒼月" to "#ff3a6fd8"), "green" to ("翠嵐" to "#ff3fa84a"), "gold" to ("金獅子" to "#ffe8b83a"),
            "purple" to ("紫電" to "#ff8a4ad8"), "red" to ("紅蓮" to "#ffc8382a"), "white" to ("空き" to "#ffe8e4da"))
        private val TERRAIN_ITEM = mapOf("VERDANT" to "minecraft:oak_sapling", "SAKURA_GROVE" to "minecraft:cherry_sapling",
            "SALTMARSH" to "minecraft:lily_pad", "CLIFFLANDS" to "minecraft:calcite", "HIGHLANDS" to "minecraft:spruce_sapling",
            "INFERNAL" to "minecraft:magma_block")
        private val FOES = mapOf("VERDANT" to "獣と盗賊 · 素早い", "SAKURA_GROVE" to "妖と影 · 状態異常", "SALTMARSH" to "沼の獣 · 毒",
            "CLIFFLANDS" to "岩の魔物 · 硬い", "HIGHLANDS" to "氷の騎士 · 凍結", "INFERNAL" to "炎の魔物 · 炎上", "PVP" to "プレイヤー · 季の印")
        private val BIOME = mapOf("VERDANT" to "緑野", "SAKURA_GROVE" to "桜の森", "SALTMARSH" to "塩沼", "CLIFFLANDS" to "断崖",
            "HIGHLANDS" to "高地", "INFERNAL" to "獄炎の地", "PVP" to "荒野")
        private val YIELD = mapOf("VERDANT" to "伐採・植物", "SAKURA_GROVE" to "植物・伐採", "SALTMARSH" to "皮剥ぎ・植物", "CLIFFLANDS" to "採石",
            "HIGHLANDS" to "採石・採鉱", "INFERNAL" to "採鉱", "PVP" to "希少鉱脈")
        private val NAMES = mapOf(
            "VERDANT" to listOf("風見の丘", "麦穂の野", "白樫の森", "羊飼いの原", "緑風の谷", "古道の野", "鈴蘭の里", "若葉の坂"),
            "SAKURA_GROVE" to listOf("花影の塔寺", "桜吹雪の峠", "紅霞の庭", "夜桜の参道", "花筏の淵", "春雷の社"),
            "SALTMARSH" to listOf("沈み葦の集落", "霧沼", "塩の干潟", "蛙鳴の湿原", "泥炭の入江", "水車の渡し"),
            "CLIFFLANDS" to listOf("白崖の見張り塔", "石切りの段丘", "鷹の巣岩", "風穴の崖", "段々の白壁", "岩笛の高台"),
            "HIGHLANDS" to listOf("霧氷の城", "凍て牙の峰", "雪原の砦", "狼の背", "氷柱の谷", "吹雪の関所"),
            "INFERNAL" to listOf("熾火の火口", "黒鉄の砦", "灰の平原", "溶岩の裂け目"),
            "PVP" to listOf("境界の荒野", "血砂の原", "折れ旗の丘", "無法の峡谷", "骸の野"),
        )
    }
}
