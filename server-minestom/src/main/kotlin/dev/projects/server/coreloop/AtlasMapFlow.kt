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
    private var panX = (960.0 - home.cx).coerceIn(minX(), MARGIN)
    private var panY = (620.0 - home.cy).coerceIn(minY(), 60.0)
    private var vx = 0.0
    private var vy = 0.0
    private val openedAt = System.currentTimeMillis()

    private fun viewWidth() = if (selected != null) PANEL_X - 20.0 else 1920.0
    private fun minX() = viewWidth() - atlas.width - MARGIN
    private fun minY() = 1080.0 - atlas.height - MARGIN

    override fun pointer(x: Double, y: Double) {
        val dx = x / SCALE
        val dy = (y - TOP) / SCALE
        val right = viewWidth()
        vx = when { dx < EDGE -> SPEED; dx > right - EDGE && dx < right -> -SPEED; else -> 0.0 }
        vy = when { dy < EDGE -> SPEED; dy > 1080 - EDGE -> -SPEED; else -> 0.0 }
    }

    override fun tick(nowMs: Long): dev.projects.webui.ForgeUiReceipt? {
        panX = (panX + vx).coerceIn(minX(), MARGIN)
        panY = (panY + vy).coerceIn(minY(), 60.0)
        return null
    }

    override fun action(action: String): Boolean {
        when {
            action.startsWith("zone:") -> {
                val id = action.removePrefix("zone:").toIntOrNull() ?: return false
                if (selected == id) return false
                selected = id
                panX = panX.coerceIn(minX(), MARGIN)
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
        atlas.art["vignette"]?.let { sprite("vignette", it, 0.0, 0.0, 1920.0, 1080.0, 6) }
        // Ordinary zones are a small gold pin and a name; only bosses, wilds and the harbor get an icon badge.
        val live = bossZones.firstOrNull()
        for (z in atlas.zones) {
            val px = panX + z.cx; val py = panY + z.cy
            if (px < 30 || px > viewRight - 30 || py < 60 || py > 1050) continue
            val sel = z.id == selected
            val event = z.id in bossZones.take(2)
            val special = z.hub || z.pvp || z.id in bossZones
            val labelY: Double
            if (special) {
                val badge = atlas.art[when { z.hub -> "badge_hub"; z.pvp -> "badge_pvp"; else -> "badge_boss" }]
                val size = if (sel) 104.0 else 92.0
                val x = px - size / 2; val y = py - size * 116 / 120
                if (sel) atlas.art["badge_ring"]?.let { sprite("z${z.id}-ring", it, px - size * .62, y + size * 56 / 120 - size * .62, size * 1.24, size * 1.24, 7) }
                rect("z${z.id}-hit", x + size * .15, y + size * .05, size * .7, size * .8, "#00000000", 7, "zone:${z.id}", "#00000000")
                badge?.let { sprite("z${z.id}-b", it, x, y, size, size, 8) }
                labelY = py + 6
            } else {
                val pin = atlas.art["pin_${z.biome.lowercase()}"] ?: atlas.art["pin_verdant"]
                val size = if (sel) 72.0 else 60.0
                val x = px - size / 2; val y = py - size * 76 / 80
                if (sel) atlas.art["badge_ring"]?.let { sprite("z${z.id}-ring", it, px - size * .7, y + size * 34 / 80 - size * .7, size * 1.4, size * 1.4, 7) }
                rect("z${z.id}-hit", x + size * .15, y + size * .1, size * .7, size * .75, "#00000000", 7, "zone:${z.id}", "#00000000")
                pin?.let { sprite("z${z.id}-p", it, x, y, size, size, 8) }
                labelY = py + 4
            }
            if (event) {
                val isLive = z.id == live
                atlas.art[if (isLive) "timer_live" else "timer_soon"]?.let { sprite("z${z.id}-tm", it, px + 14, py - 34, 88.0, 32.0, 9) }
                text("z${z.id}-tmt", px + 40, py - 29, 58.0, if (isLive) "出現中" else "12分", 15.0, if (isLive) "#ffffff" else "#e8c878", 10, "left", "v3b")
            }
            val name = names.getValue(z.id)
            val nameSize = if (sel) 24.0 else 19.0
            val tagW = name.length * nameSize + 16
            val tagY = labelY
            rect("z${z.id}-tag", px - tagW / 2, tagY - 2, tagW, nameSize + 10, if (sel) "#f0201a0e" else "#d8101114", 8)
            rect("z${z.id}-tag-t", px - tagW / 2 + 6, tagY - 3, tagW - 12, 1.5, if (sel) "#ffe8c878" else "#90c8aa6e", 8)
            rect("z${z.id}-tag-b", px - tagW / 2 + 6, tagY + nameSize + 8, tagW - 12, 1.5, if (sel) "#ffe8c878" else "#90c8aa6e", 8)
            text("z${z.id}-n", px - tagW / 2, tagY + 2, tagW, name, nameSize,
                if (sel) "#f2d48a" else if (z.pvp) "#ffb0a0" else "#f4eedf", 9, "center")
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
        val x = PANEL_X; val y = 24.0; val w = 1920.0 - PANEL_X - 28; val h = 1032.0
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

        // Name.
        val portrait = when { z.hub -> "badge_hub"; z.pvp -> "badge_pvp"; z.id in bossZones -> "badge_boss"; else -> "pin_${z.biome.lowercase()}" }
        atlas.art[portrait]?.let { sprite("p-port-i", it, ix + 2, y + 84, 80.0, 80.0, 13) }
        text("p-name", ix + 102, y + 94, iw - 102, names.getValue(z.id), 26.0, "#f4eedf", 13, "left", "v3b")
        val key = if (z.pvp) "PVP" else z.biome
        text("p-sub", ix + 102, y + 134, iw - 102, "${BIOME[key]} · 特産 ${YIELD[key]}", 15.0, "#a29e97", 13, "left", "v3r")

        // Territory.
        text("p-land-l", ix, y + 196, iw, "領地", 14.0, "#8a867e", 13, "left", "v3m")
        val (guild, color) = GUILD.getValue(z.owner)
        atlas.banner?.let { sprite("p-land-flag", it, ix + 16, y + 238, 44.0, 48.0, 13, "#" + color.takeLast(6)) }
        text("p-land-o", ix + 74, y + 238, iw - 90, if (z.owner == "white") "持ち主なし" else "ギルド「$guild」", 20.0,
            if (z.owner == "white") "#a29e97" else "#" + color.takeLast(6), 13, "left", "v3b")
        text("p-land-w", ix + 74, y + 274, iw - 90, "次の領地戦　土 21:00", 14.0, "#e8c878", 13, "left", "v3r")

        // What is here.
        text("p-do-l", ix, y + 340, iw, "このゾーンでできること", 14.0, "#8a867e", 13, "left", "v3m")
        val rows = buildList {
            if (z.id in bossZones.take(2)) add(Triple(if (z.id == bossZones.first()) "ワールドボス" else "フィールドボス",
                "〜40人 · ダメージ順位で報酬", if (z.id == bossZones.first()) "出現中" else "12分"))
            if (!z.hub) add(Triple(if (z.pvp) "荒野" else "遠征口", if (z.pvp) "PvP あり · 準備中" else "入るたびに地形が変わる · 道の先にボス", ""))
            add(Triple(if (z.owner == "white") "砦（持ち主なし）" else "${guild}の砦",
                if (z.owner == "white") "領地戦でいちばん貢献したギルドのものに" else "ギルド員はここから出発できる", ""))
        }
        rows.forEachIndexed { i, (name, sub, whenText) ->
            val ry = y + 366 + i * 86
            atlas.art["card_0"]?.let { nodes += UiNode("p-r$i-c0", box(ix, ry, 256.0, 76.0), "", emptyMap(), null, null, true, 11, it) }
            atlas.art["card_1"]?.let { nodes += UiNode("p-r$i-c1", box(ix + 256, ry, 120.0, 76.0), "", emptyMap(), null, null, true, 11, it) }
            text("p-r$i-n", ix + 18, ry + 14, iw - 130, name, 18.0, "#f4eedf", 13, "left", "v3m")
            text("p-r$i-s", ix + 18, ry + 44, iw - 36, sub, 14.0, "#8a867e", 13, "left", "v3r")
            if (whenText.isNotEmpty()) text("p-r$i-w", ix + iw - 118, ry + 16, 100.0, whenText, 14.0, "#ff9a8a", 13, "right")
        }

        // Info card.
        val recommend = listOf("冒険Lv 1〜", "冒険Lv 11〜", "冒険Lv 21〜", "冒険Lv 31〜")[z.tier - 1] + " · 武器 T${z.tier}"
        val foes = FOES[if (z.pvp) "PVP" else z.biome] ?: "—"
        listOf("推奨" to recommend, "敵の傾向" to foes, "記録" to "まだ踏破していない").forEachIndexed { i, (k, v) ->
            val ly = y + 650 + i * 46
            text("p-i$i-k", ix + 18, ly, 100.0, k, 15.0, "#8a867e", 13, "left", "v3m")
            text("p-i$i-v", ix + 118, ly, iw - 136, v, 15.0, "#e6e2d8", 13, "left", "v3r")
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
        private const val EDGE = 70.0
        private const val SPEED = 22.0
        private const val PANEL_X = 1460.0
        private const val GOLD = "#ffc8aa6e"
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
