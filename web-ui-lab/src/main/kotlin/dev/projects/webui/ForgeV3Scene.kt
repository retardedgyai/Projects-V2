package dev.projects.webui

import com.google.gson.JsonParser
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

data class ForgeV3Mod(val kind: String, val text: String, val source: String = "")
data class ForgeV3Gear(
    val id: String, val name: String, val slotLabel: String, val tier: Int, val rarity: String,
    val level: Int, val failures: Int, val pityThreshold: Int, val broken: Boolean,
    val statLabel: String, val power: Int, val nextPower: Int, val speedPercent: Double? = null,
    val mods: List<ForgeV3Mod> = emptyList(), val modCapacity: Int = 0,
    val iconItem: String? = null, val kindLabel: String = "",
)
/** [item] is the material's item id, drawn as the game's own item. */
data class ForgeV3Cost(val name: String, val tierLabel: String, val owned: Long, val required: Long, val item: String)
data class ForgeV3State(
    val gears: List<ForgeV3Gear>, val selected: String, val silver: Long, val dust: Long,
    val masteryRank: Int, val masteryProgress: Int,
    val baseChance: Double, val masteryBonus: Double, val catalystBonus: Double, val chance: Double,
    val pityGuaranteed: Boolean, val breakOnFailure: Double, val costTier: Int,
    val costs: List<ForgeV3Cost>, val focused: Boolean, val focusAvailable: Boolean,
    val blockedReason: String?, val history: String?, val note: String,
    val modal: Boolean, val muted: Boolean, val busy: Boolean,
) {
    val gear get() = gears.single { it.id == selected }
    val risky get() = breakOnFailure > 0 && chance < 100.0
}

enum class ForgeV3MotionKind(val durationMs: Long) { CHARGE(800), HIT(1200), SHAKE(450), BREAK(900) }
/** One short animation on the item itself. [level] is the level the item shows during it. */
data class ForgeV3Motion(val kind: ForgeV3MotionKind, val startMs: Long, val level: Int, val seed: Long = startMs)
data class ForgeV3Banner(val title: String, val sub: String, val color: String, val untilMs: Long)

/**
 * The harbor forge in the 冒険の手帳's pixel look: iron windows and plates baked by scripts/build_forge_dot_assets.py
 * (polish05/forge.json), words in the passive tree's MaruMinya dot font, items as the game's own item models.
 * Coordinates are 1920x1080 design px. Names and numbers only, no prose (藍 2026-11-04).
 */
class ForgeV3Scene(forgeJson: ByteArray) {
    private class Placed(val sprite: UiSprite, val x: Int, val y: Int)
    private val plates: Map<String, List<Placed>>
    private val sprites: Map<String, UiSprite>
    init {
        val root = JsonParser.parseString(forgeJson.toString(Charsets.UTF_8)).asJsonObject
        sprites = root.getAsJsonObject("sprites").entrySet().associate { (name, value) ->
            val v = value.asJsonObject
            name to UiSprite(v.get("char").asString, v.get("font").asString, v.get("width").asInt, v.get("height").asInt)
        }
        plates = root.getAsJsonObject("plates").entrySet().associate { (name, value) ->
            name to value.asJsonArray.map { p -> p.asJsonObject.let { Placed(sprites.getValue(it.get("sprite").asString), it.get("x").asInt, it.get("y").asInt) } }
        }
    }
    private val format = NumberFormat.getIntegerInstance(Locale.US)

    companion object {
        /** 1920x1080 fills the 800px canvas width; the 450px tall stage is centred in 480. */
        const val SCALE = 800.0 / 1920.0
        const val TOP = (480.0 - 1080.0 * SCALE) / 2
        /** Renderer zoom that shows the 450px stage at full client height (Polish05 used 0.8 for 480px).
         *  A 1080p screenshot measured 0.9967 screen px per design px; nearest-sampled glyphs then lose
         *  whole columns. The 1.004 margin keeps the stage at or just above one pixel per pixel. */
        const val ZOOM = 0.8 * 480.0 / (1080.0 * SCALE) * 1.004 / 0.9967
        const val HERO_X = 640.0
        const val HERO_Y = 500.0
        private const val HERO = 300.0
        private const val LX = 56.0
        private const val LW = 380.0
        private const val RW = 460.0
        private const val RX = 1920.0 - 56 - RW
        private const val RIX = RX + 24
        private const val RIW = 412.0
        private const val DX = 836.0
        private const val STATS_W = 504.0
        private const val TRACK_X = 476.0
        private const val TRACK_Y = 922.0
        private const val GO_Y = 930.0
        /** The dot font's em: 12 dots, so 24 is 2 px a dot, 36 is 3, 48 is 4, 72 is 6, 96 is 8. */
        private const val BODY = 24.0
        private const val NAME = 36.0
        private const val BIG = 48.0
        private const val HUGE = 72.0
        private const val GIANT = 96.0
        private const val ROW = 30.0
        private const val INK = "#ece6dc"
        private const val SOFT = "#c9c3b8"
        private const val MUTE = "#9a948c"
        private const val DIM = "#5e5a54"
        private const val GOLD = "#f2c84b"
        private const val DANGER = "#e58b80"
        private const val DARK = "#2a1a06"
        private const val RULE = "#ff2a2a2e"
        private const val MOD = "#8fb4f0"

        fun heat(level: Int) = when {
            level <= 5 -> "#8A8E96"
            level <= 10 -> "#D9563A"
            level <= 15 -> "#F28A3C"
            level <= 20 -> "#FFC48A"
            else -> "#FFF1DE"
        }
        fun heatName(level: Int) = when {
            level <= 5 -> "鉄"
            level <= 10 -> "赤熱"
            level <= 15 -> "橙熱"
            level <= 20 -> "黄熱"
            else -> "白熱"
        }
        fun rarityColor(rarity: String) = when (rarity) {
            "レア" -> "#D9B3F5"
            "マジック" -> "#8FB4F0"
            else -> "#ECE6DC"
        }
        fun argb(hex: String, a: Double): String {
            val v = (a.coerceIn(0.0, 1.0) * 255).roundToInt()
            return "#" + v.toString(16).padStart(2, '0') + hex.removePrefix("#").takeLast(6)
        }
        fun percent(v: Double) = "%.1f".format(Locale.US, v)
    }

    /** Text width in design px, using the dot font's glyph advances. */
    fun width(text: String, size: Double = BODY) = Polish05FontMetrics.advance(text, "ptdot") * size / Polish05FontMetrics.SIZE

    private inner class Draw {
        val list = mutableListOf<UiNode>()
        private fun box(x: Double, y: Double, w: Double, h: Double) = Box(x * SCALE, TOP + y * SCALE, w * SCALE, h * SCALE)

        fun node(id: String, x: Number, y: Number, w: Number, h: Number, style: Map<String, String> = emptyMap(), text: String = "",
                 action: String? = null, sprite: UiSprite? = null, item: String? = null, depth: Int = 4) {
            list += UiNode(id, box(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble()), text, style, action, item, true, depth, sprite)
        }
        fun rect(id: String, x: Number, y: Number, w: Number, h: Number, color: String, depth: Int = 8) {
            if (w.toDouble() <= 0 || h.toDouble() <= 0) return
            node(id, x, y, w, h, mapOf("background-color" to color), depth = depth)
        }
        /** A white sprite, tinted and faded. */
        fun sprite(id: String, name: String, x: Number, y: Number, w: Number, h: Number, color: String? = null, opacity: Double = 1.0, depth: Int = 2) {
            val style = buildMap {
                if (color != null) put("sprite-color", color.take(7))
                if (opacity < 1.0) put("opacity", "%.3f".format(Locale.US, opacity))
            }
            node(id, x, y, w, h, style, sprite = plates.getValue(name).single().sprite, depth = depth)
        }
        /** A baked plate (window, button, slot) with its top-left at [x], [y]. */
        fun plate(id: String, name: String, x: Double, y: Double, depth: Int = 2) =
            plates.getValue(name).forEachIndexed { k, p -> node("$id-$k", x + p.x, y + p.y, p.sprite.width, p.sprite.height, sprite = p.sprite, depth = depth) }
        /** Dot words; [y] is the top of the letters' cell. [shadow] false for dark words on gold. */
        fun text(id: String, x: Double, y: Double, value: String, color: String, size: Double = BODY, align: String = "left",
                 w: Double = width(value, size), depth: Int = 4, shadow: Boolean = true) {
            if (value.isEmpty()) return
            node(id, x, y, w, size * 1.25, mapOf("font-size" to "${size * SCALE}px", "color" to color.take(7), "text-align" to align,
                "font-family" to "projects_ui_polish05:" + if (shadow) "ptdot" else "ptdotn"), value, depth = depth)
        }
        /** Words whose right edge is at [right]. */
        fun right(id: String, right: Double, y: Double, value: String, color: String, size: Double = BODY, depth: Int = 4) =
            text(id, right - width(value, size), y, value, color, size, depth = depth)
        fun hit(id: String, x: Number, y: Number, w: Number, h: Number, action: String) =
            node(id, x, y, w, h, mapOf("background-color" to "#00000000", "hover-background-color" to "#30ffffff"), action = action, depth = 10)
    }

    fun build(state: ForgeV3State, nowMs: Long = System.currentTimeMillis(),
              motion: ForgeV3Motion? = null, banner: ForgeV3Banner? = null): UiScene {
        val d = Draw()
        val live = motion?.takeIf { nowMs - it.startMs in 0 until it.kind.durationMs }
        val actions = !state.modal && !state.busy
        d.rect("viewport-backdrop", -400, -200, 2720, 1480, "#ff121316", -8)
        header(d, state)
        left(d, state, actions)
        hero(d, state, nowMs, live, banner)
        details(d, state)
        track(d, state)
        right(d, state, actions)
        footer(d, state)
        if (state.modal) modal(d, state)
        return UiScene(800.0, 480.0, d.list)
    }

    private fun header(d: Draw, state: ForgeV3State) {
        d.plate("brand", "brand", LX, 34.0)
        d.text("brand-name", LX + 68, 36.0, "鍛冶", INK, NAME)
        fun chip(id: String, x: Double, label: String, value: String) {
            d.plate(id, "chip", x, 38.0)
            d.text("$id-l", x + 14, 45.0, label, MUTE)
            d.right(id.removePrefix("chip-"), x + 220 - 14, 45.0, value, INK)
        }
        chip("chip-wallet", 1920 - LX - 220, "銀貨", format.format(state.silver))
        chip("chip-dust-value", 1920 - LX - 450, "刻印粉", format.format(state.dust))
        chip("chip-rank-value", 1920 - LX - 680, "熟練", "Rank ${state.masteryRank}")
    }

    private fun left(d: Draw, state: ForgeV3State, actions: Boolean) {
        d.plate("lp", "panel_left", LX, 110.0)
        d.text("lp-h", LX + 24, 130.0, "装備", INK)
        d.rect("lp-rule", LX + 24, 166.0, 332.0, 2.0, RULE)
        state.gears.forEachIndexed { i, g ->
            val y = 182.0 + i * 92
            val selected = g.id == state.selected
            d.plate("row-$i", if (selected) "row_on" else "row", LX + 24, y)
            if (actions) d.hit("row-hit-$i", LX + 24, y, 332, 84, "select:${g.id}")
            d.plate("row-slot-$i", "slot60", LX + 36, y + 12)
            if (g.iconItem != null) d.node("gear-icon-$i", LX + 38, y + 14, 56, 56, item = g.iconItem, depth = 4)
            if (g.broken) d.rect("row-broken-$i", LX + 40, y + 16, 52, 52, "#66c2554a", 15)
            d.text("gear-name-$i", LX + 110, y + 12, g.name, rarityColor(g.rarity))
            d.right("gear-level-$i", LX + 344, y + 6, "+${g.level}", heat(g.level), NAME)
            if (g.broken) d.text("gear-sub-$i", LX + 110, y + 46, "破損", DANGER)
            else d.text("gear-sub-$i", LX + 110, y + 46, "T${g.tier}  ${g.statLabel} ${g.power}", MUTE)
        }
    }

    private fun hero(d: Draw, state: ForgeV3State, nowMs: Long, motion: ForgeV3Motion?, banner: ForgeV3Banner?) {
        val g = state.gear
        val shown = motion?.level ?: g.level
        val p = motion?.let { (nowMs - it.startMs).toDouble() / it.kind.durationMs }?.coerceIn(0.0, 1.0) ?: 0.0
        val kind = motion?.kind
        var scale = 1.0
        var dx = 0.0
        var dy = 0.0
        when (kind) {
            ForgeV3MotionKind.CHARGE -> { dx = if ((nowMs / 50) % 2 == 0L) 3.0 else -3.0; dy = if ((nowMs / 75) % 2 == 0L) 3.0 else 0.0 }
            ForgeV3MotionKind.HIT -> scale = 1.0 + 0.08 * (1 - p).pow(3)
            ForgeV3MotionKind.SHAKE, ForgeV3MotionKind.BREAK -> dx = (12.0 * (1 - p) * sin(p * PI * 6) / 3).roundToInt() * 3.0
            null -> Unit
        }
        val cx = HERO_X + dx
        val cy = HERO_Y + dy
        val size = HERO * scale

        // Heat: a dithered disc in the level's colour behind the item.
        val auraColor = if (kind == ForgeV3MotionKind.CHARGE) heat((shown + 1).coerceAtMost(30)) else heat(shown)
        val aura = when {
            kind == ForgeV3MotionKind.CHARGE -> 0.6 + 0.4 * p
            g.broken || shown <= 5 -> 0.0
            shown >= 21 -> 1.0
            shown >= 11 -> 0.85
            else -> 0.6
        }
        if (aura > 0) d.sprite("aura", "glow", cx - 240, cy - 240, 480, 480, auraColor, aura, 1)
        val flash = when (kind) {
            ForgeV3MotionKind.HIT -> "#FFF1DE" to if (p < 0.35) 1 - p / 0.35 else 0.0
            ForgeV3MotionKind.BREAK -> "#C2554A" to if (p < 0.3) 1 - p / 0.3 else 0.0
            else -> null
        }
        if (flash != null && flash.second > 0.1) d.sprite("flash", "glow", cx - 192, cy - 192, 384, 384, flash.first, flash.second, 3)
        if (g.iconItem != null) d.node("hero-item", cx - size / 2, cy - size / 2, size, size, item = g.iconItem, depth = 4)
        if (g.broken) d.rect("hero-broken", cx - 6, cy - 120, 12, 240, "#99c2554a", 15)

        // Embers rise from hot items, square dots on a 3 px grid.
        val embers = when { g.broken -> 0; shown >= 21 -> 10; shown >= 16 -> 7; shown >= 11 -> 5; shown >= 6 -> 3; else -> 0 }
        for (i in 0 until embers) {
            val period = 2600 + (i * 7 % 5) * 350
            val phase = ((nowMs + i * 370L) % period).toDouble() / period
            val a = if (phase < 0.15) phase / 0.15 else 1 - phase
            val s = if (i % 3 == 0) 6.0 else 3.0
            val x = HERO_X + 240 * (((i * 37) % 60 - 30) / 60.0)
            val y = HERO_Y + 150 - (i * 23) % 60 - phase * 300
            d.rect("ember-$i", (x / 3).roundToInt() * 3, (y / 3).roundToInt() * 3, s, s, argb(if (i % 4 == 0) "#FFF1DE" else heat(shown), a), 15)
        }

        // A ring and sparks burst from the item.
        if (motion != null && kind != ForgeV3MotionKind.CHARGE) {
            val color = when (kind) { ForgeV3MotionKind.HIT -> heat(shown); ForgeV3MotionKind.BREAK -> "#C2554A"; else -> "#6F6B65" }
            if (kind != ForgeV3MotionKind.SHAKE && p < 0.67) {
                val q = p / 0.67
                val r = 40 + 300 * (1 - (1 - q).pow(2))
                d.sprite("ring", "ring", HERO_X - r, HERO_Y - r, r * 2, r * 2, color, 1 - q, 12)
            }
            val count = when (kind) { ForgeV3MotionKind.HIT -> 10 + min(14, shown); ForgeV3MotionKind.BREAK -> 14; else -> 6 }
            val power = when (kind) { ForgeV3MotionKind.HIT -> 150.0 + shown * 6; ForgeV3MotionKind.BREAK -> 170.0; else -> 90.0 }
            val sp = (p * motion.kind.durationMs / 900.0).coerceIn(0.0, 1.0)
            if (sp < 1.0) {
                val random = java.util.Random(motion.seed)
                val ease = 1 - (1 - sp).pow(3)
                for (i in 0 until count) {
                    val angle = 2 * PI * i / count + random.nextDouble() * 0.5
                    val dist = power * (0.6 + random.nextDouble() * 0.6)
                    val s = if (random.nextDouble() < 0.3) 9.0 else 6.0
                    val white = random.nextDouble() < 0.35 && kind != ForgeV3MotionKind.SHAKE
                    val x = HERO_X + cos(angle) * dist * ease - s / 2
                    val y = HERO_Y + (sin(angle) * dist * 0.9 - power * 0.35) * ease - s / 2
                    d.rect("spark-$i", (x / 3).roundToInt() * 3, (y / 3).roundToInt() * 3, s, s, argb(if (white) "#FFF1DE" else color, 1 - sp), 15)
                }
            }
        }

        // The result, at the item's feet.
        if (banner != null && nowMs < banner.untilMs) {
            d.plate("toast", "toast", HERO_X - 180, 752.0, 16)
            d.text("toast-title", HERO_X - 180, 764.0, banner.title, banner.color, BIG, "center", 360.0, 17)
        }
    }

    private fun details(d: Draw, state: ForgeV3State) {
        val g = state.gear
        val maxed = g.level >= 30
        d.text("tag-rarity", DX, 196.0, g.rarity, rarityColor(g.rarity))
        d.text("tag-kind", DX + width(g.rarity) + 16, 196.0, "T${g.tier}  ${g.kindLabel.ifEmpty { g.slotLabel }}", MUTE)
        d.text("hero-name", DX, 230.0, g.name, rarityColor(g.rarity), BIG)

        // Now → next, the next one large in its heat colour.
        d.text("level-now", DX, 326.0, "+${g.level}", if (maxed) heat(g.level) else MUTE, HUGE)
        if (!maxed) {
            val ax = DX + width("+${g.level}", HUGE) + 24
            for ((k, r) in listOf(listOf(0, 6, 30, 6), listOf(24, 0, 6, 18), listOf(30, 3, 6, 12)).withIndex())
                d.rect("arrow-$k", ax + r[0], 358.0 + r[1], r[2].toDouble(), r[3].toDouble(), "#6f6b65")
            val next = g.level + 1
            val nx = ax + 60
            d.text("level-next", nx, 302.0, "+$next", heat(next), GIANT)
            d.text("level-heat", nx + width("+$next", GIANT) + 14, 380.0, heatName(next), heat(next))
        }

        // The stat rows: name, then now → next.
        val rows = buildList {
            add(Triple(g.statLabel, "${g.power}", "${g.nextPower}"))
            g.speedPercent?.let { s -> add(Triple("攻撃速度", "+${percent(s)}%", "+${percent(s + 0.8)}%")) }
        }
        val top = 452.0
        d.plate("stats", if (rows.size > 1) "stats" else "stats1", DX, top)
        val nextColor = if (maxed) MUTE else heat(g.level + 1)
        val edge = DX + STATS_W - 18
        rows.forEachIndexed { i, (label, now, next) ->
            val y = top + 10 + i * 40 + 2
            d.text("stat-label-$i", DX + 18, y, label, SOFT)
            if (maxed) { d.right("stat-now-$i", edge, y, now, INK); return@forEachIndexed }
            d.right("stat-next-$i", edge, y, next, nextColor)
            val arrowX = edge - width(next) - 10 - width("→")
            d.text("stat-arrow-$i", arrowX, y, "→", DIM)
            d.right("stat-now-$i", arrowX - 10, y, now, MUTE)
        }
        var y = top + rows.size * 40 + 20 + 16
        if (g.broken) { d.text("state-value", DX, y, "破損", DANGER); y += ROW + 8 }

        // MODs
        d.text("mod-title", DX, y, "MOD ${g.mods.size} / ${g.modCapacity}", MUTE)
        g.mods.take(6).forEachIndexed { i, m ->
            val my = y + 36 + i * ROW
            d.text("mod-kind-$i", DX, my, m.kind, DIM)
            d.text("mod-text-$i", DX + 96, my, m.text, MOD)
        }
    }

    /** +1..+30 as five-cell bands, lit up to the current level; cells that can break carry a red foot. */
    private fun track(d: Draw, state: ForgeV3State) {
        val g = state.gear
        d.plate("track", "track", TRACK_X, TRACK_Y)
        for (b in 0 until 6) {
            val bx = TRACK_X + 16 + b * 144
            for (k in 0 until 5) {
                val lv = b * 5 + k + 1
                val x = bx + k * 28
                val y = TRACK_Y + 22
                val color = heat(lv)
                when {
                    lv <= g.level -> d.rect("cell-$lv", x, y, 24, 14, color)
                    lv == g.level + 1 -> {
                        d.rect("cell-$lv-t", x, y, 24, 2, color); d.rect("cell-$lv-b", x, y + 12, 24, 2, color)
                        d.rect("cell-$lv-l", x, y + 2, 2, 10, color); d.rect("cell-$lv-r", x + 22, y + 2, 2, 10, color)
                    }
                    else -> {
                        d.rect("cell-$lv", x, y, 24, 14, "#ff2a2c31")
                        if (lv >= 16) d.rect("cell-$lv-risk", x, y + 10, 24, 4, "#ffa8483e")
                    }
                }
            }
            val inBand = g.level + 1 in (b * 5 + 1)..(b * 5 + 5)
            d.text("band-range-$b", bx, TRACK_Y + 46, "+${b * 5 + 1}〜${b * 5 + 5}", if (inBand) heat(b * 5 + 1) else DIM)
        }
    }

    private fun right(d: Draw, state: ForgeV3State, actions: Boolean) {
        val g = state.gear
        val maxed = g.level >= 30
        d.plate("rp", "panel_right", RX, 110.0)
        val edge = RIX + RIW
        val rate = if (maxed) 0.0 else state.chance
        val rateColor = when { rate >= 100 -> "#FFF1DE"; rate >= 60 -> "#FFC48A"; rate >= 25 -> "#F28A3C"; else -> "#D9563A" }
        d.text("chance-label", RIX, 130.0, "成功率", MUTE)
        d.right("chance-value", edge, 124.0, if (maxed) "—" else percent(rate) + "%", rateColor, HUGE)
        val lit = (rate / 5).roundToInt()
        for (i in 0 until 20) {
            val color = if (i >= lit) "#ff2a2c31" else when { i < 5 -> "#ff8e2b1c"; i < 10 -> "#ffc23a1e"; i < 15 -> "#fff07a2a"; else -> "#ffffc48a" }
            d.rect("seg-$i", RIX + 2 + i * 20.6, 224, 18, 16, color)
        }
        var y = 254.0
        if (!maxed) {
            val lines = buildList {
                add(Triple("基礎", "${percent(state.baseChance)}%", SOFT))
                add(Triple("熟練", "+${state.masteryBonus.roundToInt()}", SOFT))
                if (state.focused) add(Triple("集中鍛造", "+${state.catalystBonus.roundToInt()}", GOLD))
                if (state.pityGuaranteed && g.pityThreshold > 0) add(Triple("天井", "確定", GOLD))
            }
            lines.forEachIndexed { i, (label, value, color) ->
                d.text("why-label-$i", RIX, y + i * ROW, label, MUTE)
                d.right("why-value-$i", edge, y + i * ROW, value, color)
            }
        }

        // Pity and break, side by side.
        val by = 384.0
        d.plate("pity", "box", RIX, by)
        d.text("pity-title", RIX + 14, by + 10, "天井", MUTE)
        if (!maxed && g.pityThreshold > 0) {
            val pip = (174.0 - (g.pityThreshold - 1) * 4) / g.pityThreshold
            for (i in 0 until g.pityThreshold)
                d.rect("pity-pip-$i", RIX + 14 + i * (pip + 4), by + 46, pip, 10, if (i < g.failures) "#fff07a2a" else "#ff2a2c31")
            d.text("pity-text", RIX + 14, by + 64, "${g.failures} / ${g.pityThreshold}", if (g.failures >= g.pityThreshold) GOLD else INK)
        } else d.text("pity-text", RIX + 14, by + 50, "—", DIM, NAME)
        val risky = !maxed && state.risky
        d.plate("break", if (risky) "box_risk" else "box", RIX + 210, by)
        d.text("break-title", RIX + 224, by + 10, "破損", MUTE)
        d.text("break-value", RIX + 224, by + 40, if (risky) "${percent(state.breakOnFailure)}%" else "なし",
            if (risky) DANGER else DIM, if (risky) BIG else NAME)

        // Materials
        y = 504.0
        d.text("cost-title", RIX, y, if (maxed) "素材" else "素材  T${state.costTier}", SOFT)
        state.costs.take(4).forEachIndexed { i, c ->
            val cy = 540.0 + i * 80
            val ok = c.owned >= c.required
            d.plate("cost-$i", "cost", RIX, cy)
            d.plate("cost-slot-$i", "slot44", RIX + 12, cy + 14)
            d.node("cost-icon-$i", RIX + 14, cy + 16, 40, 40, item = c.item, depth = 4)
            d.text("cost-name-$i", RIX + 70, cy + 8, c.name, INK)
            d.text("cost-tier-$i", RIX + 70 + width(c.name) + 10, cy + 8, c.tierLabel, MUTE)
            d.text("cost-own-$i", RIX + 70, cy + 38, "所持 ${format.format(c.owned)}", if (ok) MUTE else DANGER)
            d.right("cost-need-$i", edge - 16, cy + 14, "×${format.format(c.required)}", if (ok) INK else DANGER, NAME)
        }

        // Focused forging, right after the materials.
        val ty = 540.0 + state.costs.size.coerceAtMost(4) * 80
        val on = state.focused
        val enabled = state.focusAvailable || on
        d.plate("catalyst", if (on) "toggle_on" else "toggle", RIX, ty)
        if (actions && enabled) d.hit("catalyst-hit", RIX, ty, RIW, 64, "catalyst")
        d.text("catalyst-title", RIX + 16, ty + 17, "集中鍛造", if (enabled) INK else DIM)
        if (enabled) d.text("catalyst-sub", RIX + 16 + width("集中鍛造") + 14, ty + 17, "+${state.catalystBonus.roundToInt().takeIf { it > 0 } ?: 15}%", GOLD)
        d.plate("switch", if (on) "switch_on" else "switch_off", edge - 60, ty + 20)

        // The button
        val ready = !state.busy && state.blockedReason == null && !maxed
        val label = when {
            state.busy -> "鍛造中"
            maxed -> "最大 +30"
            ready -> "強化する"
            else -> state.blockedReason ?: "強化できない"
        }
        val look = when { !ready -> "go_off"; state.risky -> "go_risk"; else -> "go_take" }
        d.plate("enhance-face", look, RIX, GO_Y)
        if (ready && actions) d.hit("enhance", RIX, GO_Y, RIW, 72, "enhance")
        val size = if (ready || state.busy || maxed) NAME else BODY
        d.text("enhance-label", RIX, GO_Y + (72 - size * 1.25) / 2 + 2, label,
            when (look) { "go_take" -> DARK; "go_risk" -> INK; else -> DIM }, size, "center", RIW, shadow = look != "go_take")
    }

    private fun footer(d: Draw, state: ForgeV3State) {
        d.plate("footer", "footer", LX, 1032.0)
        state.history?.let { d.right("footer-log", 1920 - LX, 1036.0, it, SOFT) }
    }

    private fun modal(d: Draw, state: ForgeV3State) {
        val g = state.gear
        val x = 660.0; val y = 340.0
        d.rect("modal-mask", -400, -200, 2720, 1480, "#b80b0c0e", 30)
        d.plate("modal", "modal", x, y, 31)
        d.text("modal-title", x + 40, y + 34, "破損の危険", DANGER, NAME, depth = 32)
        d.text("modal-gear", x + 40, y + 88, "${g.name}  +${g.level} → +${g.level + 1}", heat(g.level + 1), depth = 32)
        d.rect("modal-rule", x + 40, y + 130, 520.0, 2.0, RULE, 32)
        listOf(
            "成功率" to "${percent(state.chance)}%",
            "失敗時の破損" to "${percent(state.breakOnFailure)}%",
            "1回の破損" to "${percent((100 - state.chance) * state.breakOnFailure / 100)}%",
        ).forEachIndexed { i, (label, value) ->
            d.text("modal-label-$i", x + 40, y + 148 + i * 40, label, MUTE, depth = 32)
            d.right("modal-value-$i", x + 560, y + 148 + i * 40, value, if (i == 0) INK else DANGER, depth = 32)
        }
        d.plate("modal-cancel-face", "modal_cancel", x + 40, y + 300, 31)
        d.node("modal-cancel", x + 40, y + 300, 250, 60, mapOf("background-color" to "#00000000", "hover-background-color" to "#30ffffff"),
            action = "cancel", depth = 38)
        d.text("modal-cancel-label", x + 40, y + 300 + (60 - NAME * 1.25) / 2 + 2, "やめる", INK, NAME, "center", 250.0, 32)
        d.plate("modal-confirm-face", "modal_confirm", x + 310, y + 300, 31)
        d.node("modal-confirm", x + 310, y + 300, 250, 60, mapOf("background-color" to "#00000000", "hover-background-color" to "#30ffffff"),
            action = "confirm", depth = 38)
        d.text("modal-confirm-label", x + 310, y + 300 + (60 - NAME * 1.25) / 2 + 2, "強化する", INK, NAME, "center", 250.0, 32)
    }
}
