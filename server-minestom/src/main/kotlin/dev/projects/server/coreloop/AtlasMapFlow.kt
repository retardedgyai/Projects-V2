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
    class Atlas(json: ByteArray) {
        data class Tile(val sprite: UiSprite, val x: Int, val y: Int)
        data class Zone(val id: Int, val tier: Int, val biome: String, val pvp: Boolean, val hub: Boolean, val kind: String?,
                        val owner: String, val cx: Int, val cy: Int, val ox: Int, val oy: Int, val ax: Int, val ay: Int)
        val width: Int
        val height: Int
        val tiles: List<Tile>
        val icons: Map<String, UiSprite>
        val cloud: UiSprite?
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
    private fun rect(id: String, x: Double, y: Double, w: Double, h: Double, color: String, depth: Int, action: String? = null, hover: String? = null) {
        val style = buildMap { put("background-color", color); if (hover != null) put("hover-background-color", hover) }
        nodes += UiNode(id, box(x, y, w, h), "", style, action, null, true, depth)
    }
    private fun sprite(id: String, s: UiSprite, x: Double, y: Double, w: Double, h: Double, depth: Int) {
        nodes += UiNode(id, box(x, y, w, h), "", emptyMap(), null, null, true, depth, s)
    }
    private fun text(id: String, x: Double, y: Double, w: Double, value: String, size: Double, color: String, depth: Int, align: String = "left") {
        val page = "v3num${size.toInt()}".takeIf(Polish05FontMetrics::has) ?: "v3num"
        nodes += UiNode(id, box(x, y, w, size * 1.25), value,
            mapOf("font-size" to "${size * SCALE}px", "color" to color, "text-align" to align, "font-family" to "projects_ui_polish05:$page"),
            null, null, true, depth)
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
        // One marker per zone.
        val live = bossZones.firstOrNull()
        for (z in atlas.zones) {
            val px = panX + z.cx; val py = panY + z.cy
            if (px < 20 || px > viewRight - 20 || py < 40 || py > 1060) continue
            val sel = z.id == selected
            val event = z.id in bossZones.take(2)
            val size = if (sel || event || z.hub) 48.0 else 38.0
            val edge = when { sel -> GOLD; z.id == live -> "#ffe0503c"; z.pvp -> "#ffe0503c"; z.id in bossZones -> "#ffb8504a"; else -> "#ff5e5546" }
            val icon = when { z.hub -> "flag"; z.pvp -> "pvp"; z.id in bossZones -> "boss"; else -> z.biome.lowercase() }
            val x = px - size / 2; val y = py - size
            rect("z${z.id}-bg", x, y, size, size, "#ee121317", 7, "zone:${z.id}", "#ff2a2416")
            frame("z${z.id}-f", x, y, size, size, edge, 3.0, 8)
            atlas.icons[icon]?.let { sprite("z${z.id}-i", it, x + size * .2, y + size * .2, size * .6, size * .6, 8) }
            if (z.owner != "white") rect("z${z.id}-g", x + size - 4, y - 8, 8.0, 12.0, GUILD.getValue(z.owner).second, 9)
            if (event) {
                rect("z${z.id}-w", px - 34, py + 4, 68.0, 20.0, if (z.id == live) "#ffe0503c" else "#ee0a090c", 8)
                text("z${z.id}-wt", px - 34, py + 6, 68.0, if (z.id == live) "出現中" else "12分", 13.0, "#ffffff", 9, "center")
            }
            text("z${z.id}-n", px - 90, py + if (event) 28 else 6, 180.0, names.getValue(z.id), if (sel) 20.0 else 14.0,
                if (sel) "#f2d48a" else if (z.pvp) "#ffb0a0" else "#e6e2d8", 9, "center")
        }
        // Title and the one important notice.
        text("title", 34.0, 26.0, 300.0, "開拓大陸", 28.0, "#f4eedf", 22)
        live?.let { id ->
            rect("notice", 760.0, 22.0, 400.0, 40.0, "#e61e0c0c", 20)
            frame("notice-f", 760.0, 22.0, 400.0, 40.0, "#ffe0503c", 2.0, 21)
            text("notice-t", 760.0, 31.0, 400.0, "霧氷の騎士 出現中 · ${names.getValue(id)}", 19.0, "#ffd8d0", 22, "center")
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
        rect("p-bg", x, y, w, h, "#f8141519", 10)
        frame("p-line", x, y, w, h, "#4dc8aa6e", 1.0, 11)
        goldCorners("p-c", x, y, w, h, 12)
        val pad = 26.0; val ix = x + pad; val iw = w - pad * 2
        val type = if (z.hub) "拠点" else if (z.pvp) "荒野 PvP" else "安全"
        rect("p-type", ix, y + 26, 108.0, 30.0, if (z.pvp) "#ff3a1614" else "#ff14223a", 12)
        text("p-type-t", ix, y + 31, 108.0, type, 14.0, if (z.pvp) "#ffb0a0" else "#a9c8ff", 13, "center")
        rect("p-tier", ix + 116, y + 26, 48.0, 30.0, "#ffe8c878", 12)
        text("p-tier-t", ix + 116, y + 31, 48.0, "T${z.tier}", 14.0, "#2a1a08", 13, "center")
        val lv = listOf("1〜10", "11〜20", "21〜30", "31〜40")[z.tier - 1]
        text("p-lv", ix + 176, y + 31, 160.0, "敵Lv $lv", 14.0, "#f8a090", 13)
        rect("p-x", x + w - pad - 40, y + 22, 40.0, 40.0, "#ff121317", 12, "deselect", "#ff2a2416")
        frame("p-x-f", x + w - pad - 40, y + 22, 40.0, 40.0, "#ff2e3036", 2.0, 13)
        text("p-x-t", x + w - pad - 40, y + 30, 40.0, "×", 20.0, "#a29e97", 14, "center")

        val icon = when { z.hub -> "flag"; z.pvp -> "pvp"; z.id in bossZones -> "boss"; else -> z.biome.lowercase() }
        rect("p-port", ix, y + 80, 80.0, 80.0, "#ff1c1d22", 12)
        frame("p-port-f", ix, y + 80, 80.0, 80.0, GOLD, 2.0, 13)
        atlas.icons[icon]?.let { sprite("p-port-i", it, ix + 16, y + 96, 48.0, 48.0, 14) }
        text("p-name", ix + 96, y + 92, iw - 96, names.getValue(z.id), 28.0, "#f4eedf", 13)
        val key = if (z.pvp) "PVP" else z.biome
        text("p-sub", ix + 96, y + 134, iw - 96, "${BIOME[key]} · 特産 ${YIELD[key]}", 14.0, "#8a867e", 13)
        rect("p-div", ix, y + 180, iw, 2.0, "#ff3a3324", 12)
        rect("p-div-m", ix + iw / 2 - 16, y + 180, 32.0, 2.0, "#ffc9b27a", 13)

        text("p-land-l", ix, y + 200, iw, "領地", 14.0, "#8a867e", 13)
        rect("p-land", ix, y + 226, iw, 64.0, "#ff0e0f13", 12)
        frame("p-land-f", ix, y + 226, iw, 64.0, "#ff1e2026", 2.0, 13)
        val (guild, color) = GUILD.getValue(z.owner)
        rect("p-land-flag", ix + 16, y + 242, 24.0, 32.0, color, 13)
        text("p-land-o", ix + 54, y + 248, 240.0, if (z.owner == "white") "持ち主なし" else "ギルド「$guild」", 20.0,
            if (z.owner == "white") "#a29e97" else "#" + color.takeLast(6), 13)
        text("p-land-w", ix + iw - 130, y + 250, 116.0, "領地戦 土 21:00", 14.0, "#e8c878", 13, "right")

        text("p-do-l", ix, y + 314, iw, "このゾーンでできること", 14.0, "#8a867e", 13)
        val rows = buildList {
            if (z.id in bossZones.take(2)) add(Triple(if (z.id == bossZones.first()) "ワールドボス" else "フィールドボス",
                "〜40人 · ダメージ順位で報酬", if (z.id == bossZones.first()) "出現中" else "12分"))
            if (!z.hub) add(Triple(if (z.pvp) "荒野" else "遠征口", if (z.pvp) "PvP あり · 準備中" else "入るたびに地形が変わる · 道の先にボス", ""))
            add(Triple(if (z.owner == "white") "砦（持ち主なし）" else "${guild}の砦",
                if (z.owner == "white") "領地戦でいちばん貢献したギルドのものに" else "ギルド員はここから出発できる", ""))
        }
        rows.forEachIndexed { i, (name, sub, whenText) ->
            val ry = y + 340 + i * 68
            rect("p-r$i", ix, ry, iw, 60.0, "#ff0e0f13", 12)
            frame("p-r$i-f", ix, ry, iw, 60.0, "#ff1e2026", 2.0, 13)
            text("p-r$i-n", ix + 16, ry + 10, iw - 120, name, 19.0, "#f4eedf", 13)
            text("p-r$i-s", ix + 16, ry + 34, iw - 32, sub, 13.0, "#8a867e", 13)
            if (whenText.isNotEmpty()) text("p-r$i-w", ix + iw - 110, ry + 12, 96.0, whenText, 14.0, "#ff9a8a", 13, "right")
        }

        val by = y + h - pad - 72
        val goText = when { z.hub -> "港へ戻る"; z.pvp -> "準備中"; z.id == bossZones.firstOrNull() -> "参戦する"; else -> "遠征に出る" }
        val enabled = !z.pvp
        rect("p-go", ix, by, iw, 72.0, if (enabled) "#ffd8ae5c" else "#ff121317", 12, if (enabled) "go" else null, "#fff2cc80")
        frame("p-go-f", ix, by, iw, 72.0, if (enabled) "#fffff2cc" else "#ff24262c", 2.0, 13)
        text("p-go-t", ix, by + 22, iw, goText, 26.0, if (enabled) "#1a1208" else "#4a4740", 14, "center")
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
