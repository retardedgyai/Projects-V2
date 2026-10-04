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
    val iconItem: String? = null, val weaponArt: Boolean = false, val kindLabel: String = "",
)
data class ForgeV3Cost(val name: String, val tierLabel: String, val owned: Long, val required: Long, val icon: String)
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

/** The approved Forge3 artboard in Vanilla. Coordinates are the artboard's 1920x1080 CSS pixels,
 *  measured from assets/ui/forge-v3/measure.json; the static parts are the pre-rendered v3_chrome plate. */
class ForgeV3Scene(spriteJson: ByteArray) {
    private val sprites = JsonParser.parseString(spriteJson.toString(Charsets.UTF_8)).asJsonObject.entrySet().associate { (name, value) ->
        val v = value.asJsonObject
        name to UiSprite(v.get("char").asString, v.get("font").asString, v.get("width").asInt, v.get("height").asInt)
    }
    private val format = NumberFormat.getIntegerInstance(Locale.US)

    companion object {
        /** 1920x1080 fills the 800px canvas width; the 450px tall stage is centred in 480. */
        const val SCALE = 800.0 / 1920.0
        const val TOP = (480.0 - 1080.0 * SCALE) / 2
        /** Renderer zoom that shows the 450px stage at full client height (Polish05 used 0.8 for 480px). */
        const val ZOOM = 0.8 * 480.0 / (1080.0 * SCALE)
        const val HERO_X = 624.0
        const val HERO_Y = 480.0

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
        /** The artboard's glass panel (rgba(16,17,20,.86)) over its dark stone background. */
        const val PANEL = "#141518"
        fun blend(under: String, over: String, a: Double): String {
            fun ch(hex: String, i: Int) = hex.removePrefix("#").takeLast(6).substring(i * 2, i * 2 + 2).toInt(16)
            return "#" + (0..2).joinToString("") { i -> (ch(under, i) * (1 - a) + ch(over, i) * a).roundToInt().toString(16).padStart(2, '0') }
        }
    }

    /** Text width in artboard pixels, using the same glyph advances as the bitmap pages. */
    fun width(text: String, family: String, size: Double) = Polish05FontMetrics.advance(text, family) * size / Polish05FontMetrics.SIZE

    private inner class Draw {
        val list = mutableListOf<UiNode>()
        private fun box(x: Double, y: Double, w: Double, h: Double) = Box(x * SCALE, TOP + y * SCALE, w * SCALE, h * SCALE)

        fun node(id: String, x: Number, y: Number, w: Number, h: Number, style: Map<String, String> = emptyMap(), text: String = "",
                 action: String? = null, sprite: UiSprite? = null, item: String? = null, depth: Int = 2) {
            list += UiNode(id, box(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble()), text, style, action, item, true, depth, sprite)
        }
        fun rect(id: String, x: Number, y: Number, w: Number, h: Number, color: String, depth: Int = 1) {
            if (w.toDouble() <= 0 || h.toDouble() <= 0) return
            node(id, x, y, w, h, mapOf("background-color" to color), depth = depth)
        }
        fun sprite(id: String, name: String, x: Number, y: Number, w: Number, h: Number, color: String? = null,
                   opacity: Double = 1.0, depth: Int = 2) {
            val style = buildMap {
                if (color != null) put("sprite-color", color)
                if (opacity < 1.0) put("opacity", "%.3f".format(Locale.US, opacity))
            }
            node(id, x, y, w, h, style, sprite = sprites.getValue(name), depth = depth)
        }
        /** CSS border-radius fill: a cross of panels plus four tinted quarter discs. */
        fun round(id: String, x: Double, y: Double, w: Double, h: Double, r: Double, color: String, alpha: Double = 1.0, depth: Int = 1) {
            val rr = r.coerceAtMost(min(w, h) / 2)
            if (rr < 2) { rect(id, x, y, w, h, argb(color, alpha), depth); return }
            val c = argb(color, alpha)
            rect("$id-c", x + rr, y, w - 2 * rr, h, c, depth)
            rect("$id-l", x, y + rr, rr, h - 2 * rr, c, depth)
            rect("$id-r", x + w - rr, y + rr, rr, h - 2 * rr, c, depth)
            // Vanilla cannot fade text below ~10%; faint corners use the colour pre-blended over the panel.
            val (cornerColor, cornerAlpha) = if (alpha < 0.11) blend(PANEL, color, alpha) to 1.0 else color to alpha
            for ((corner, cx, cy) in listOf(Triple("tl", x, y), Triple("tr", x + w - rr, y), Triple("bl", x, y + h - rr), Triple("br", x + w - rr, y + h - rr)))
                sprite("$id-$corner", "v3_round_$corner", cx, cy, rr, rr, cornerColor, cornerAlpha, depth)
        }
        /** CSS 1px border (or inset box-shadow) on a rounded box. */
        fun border(id: String, x: Double, y: Double, w: Double, h: Double, r: Double, color: String, alpha: Double, depth: Int = 2, t: Double = 1.0) {
            val c = argb(color, alpha)
            val rr = r
            rect("$id-t", x + rr, y, w - 2 * rr, t, c, depth)
            rect("$id-b", x + rr, y + h - t, w - 2 * rr, t, c, depth)
            rect("$id-l", x, y + rr, t, h - 2 * rr, c, depth)
            rect("$id-r", x + w - t, y + rr, t, h - 2 * rr, c, depth)
            if (rr > 0) {
                val ring = if (rr >= 12) "v3_ring14" else "v3_ring10"
                val (cornerColor, cornerAlpha) = if (alpha < 0.11) blend(PANEL, color, alpha) to 1.0 else color to alpha
                for ((corner, cx, cy) in listOf(Triple("tl", x, y), Triple("tr", x + w - rr, y), Triple("bl", x, y + h - rr), Triple("br", x + w - rr, y + h - rr)))
                    sprite("$id-$corner", "${ring}_$corner", cx, cy, rr, rr, cornerColor, cornerAlpha, depth)
            }
        }
        fun text(id: String, x: Number, y: Number, w: Number, h: Number, text: String, family: String, size: Double, color: String,
                 align: String = "left", depth: Int = 3, action: String? = null) {
            if (text.isEmpty()) return
            // Each artboard size has its own glyph page; all pages share the same 32-unit metrics.
            val page = "$family${size.roundToInt()}".takeIf(Polish05FontMetrics::has) ?: family
            node(id, x, y, w, h, mapOf("color" to color.take(7), "font-size" to "${size * SCALE}px", "text-align" to align,
                "font-family" to "projects_ui_polish05:$page"), text, action, depth = depth)
        }
        /** Text whose right edge is at [right]. */
        fun right(id: String, right: Double, y: Number, h: Number, text: String, family: String, size: Double, color: String, depth: Int = 3) {
            val w = width(text, family, size)
            text(id, right - w, y, w + 1, h, text, family, size, color, "left", depth)
        }
        fun hit(id: String, x: Number, y: Number, w: Number, h: Number, action: String, hover: String) =
            node(id, x, y, w, h, mapOf("background-color" to "#00000000", "hover-background-color" to hover), action = action, depth = 1)
        fun slot(id: String, x: Double, y: Double, s: Double) {
            rect("$id-base", x, y, s, s, "#0D0E10", 2)
            rect("$id-dt", x, y, s, 3, "#050506", 2)
            rect("$id-dl", x, y + 3, 3, s - 3, "#050506", 2)
            rect("$id-lb", x + 3, y + s - 3, s - 3, 3, "#2C2E34", 2)
            rect("$id-lr", x + s - 3, y + 3, 3, s - 6, "#2C2E34", 2)
        }
    }

    fun build(state: ForgeV3State, nowMs: Long = System.currentTimeMillis(),
              motion: ForgeV3Motion? = null, banner: ForgeV3Banner? = null): UiScene {
        val d = Draw()
        val live = motion?.takeIf { nowMs - it.startMs in 0 until it.kind.durationMs }
        val actions = !state.modal && !state.busy
        d.rect("viewport-backdrop", -400, -200, 2720, 1480, "#0B0C0E", -1)
        sprites.keys.filter { it.startsWith("v3_chrome/") }.forEach { key ->
            val (x, y) = key.substringAfter('/').split('_').map(String::toDouble)
            val s = sprites.getValue(key)
            d.node("chrome-$key", x, y, s.width, s.height, sprite = s, depth = 0)
        }
        header(d, state)
        left(d, state, actions)
        hero(d, state, nowMs, live, banner)
        details(d, state)
        right(d, state, actions)
        footer(d, state)
        if (state.modal) modal(d, state)
        return UiScene(800.0, 480.0, d.list)
    }

    private fun header(d: Draw, state: ForgeV3State) {
        val rank = "Rank ${state.masteryRank}"
        d.text("rank-value", 1446, 50, 80, 19, rank, "v3num", 19.0, "#ECE6DC")
        d.text("rank-xp", 1446 + width(rank, "v3num", 19.0) + 13, 54, 40, 12, "${state.masteryProgress}/20", "v3num", 12.0, "#A29E97")
        d.round("rank-fill", 1338.0, 66.0, 96.0 * state.masteryProgress / 20, 4.0, 2.0, "#ECE6DC", 1.0, 2)
        d.text("dust-value", 1672, 50, 30, 19, format.format(state.dust), "v3num", 19.0, "#ECE6DC")
        d.text("wallet", 1790, 50, 70, 19, format.format(state.silver), "v3num", 19.0, "#ECE6DC")
    }

    private fun left(d: Draw, state: ForgeV3State, actions: Boolean) {
        state.gears.forEachIndexed { i, g ->
            val y = 170.0 + i * 89
            val selected = g.id == state.selected
            if (selected) {
                d.round("row-$i", 75.0, y, 362.0, 85.0, 10.0, "#EFE6D2", 0.08, 1)
                d.border("row-$i-line", 75.0, y, 362.0, 85.0, 10.0, "#EFE6D2", 0.45, 2)
            }
            if (actions) d.hit("row-hit-$i", 75, y, 362, 85, "select:${g.id}", if (selected) "#00000000" else "#0DEFE6D2")
            d.slot("row-slot-$i", 86.0, y + 14, 56.0)
            if (g.weaponArt) {
                val s = sprites.getValue("v3_sword_thumb")
                d.node("gear-icon-$i", 114.0 - s.width / 2.0, y + 42 - s.height / 2.0, s.width, s.height, sprite = s, depth = 4)
            } else if (g.iconItem != null) d.node("gear-icon-$i", 114.0 - 31, y + 42 - 31, 62, 62, item = g.iconItem, depth = 4)
            if (g.broken) {
                d.rect("row-broken-$i", 86, y + 14, 56, 56, "#47C2554A", 5)
                d.border("row-broken-$i-line", 86.0, y + 14, 56.0, 56.0, 0.0, "#C2554A", 1.0, 5, 2.0)
            }
            d.text("gear-name-$i", 156, y + 10, 232, 24, g.name, if (selected) "v3b" else "v3m", 16.0, rarityColor(g.rarity))
            d.text("gear-sub-$i", 156, y + 38, 232, 17, "${g.slotLabel} · T${g.tier} · ${g.statLabel} ${g.power}", "v3r", 12.0, "#A29E97")
            val (tag, tagColor) = when {
                g.broken -> "破損中 · 要修理" to "#E58B80"
                g.level >= 30 -> "最大強化" to "#6F6B65"
                g.pityThreshold > 0 && g.failures >= g.pityThreshold -> "次の強化は確定成功" to "#FFC48A"
                g.pityThreshold > 0 -> "天井 ${g.failures} / ${g.pityThreshold}" to "#6F6B65"
                else -> "+5までは必ず成功" to "#6F6B65"
            }
            d.text("gear-tag-$i", 156, y + 59, 232, 16, tag, "v3r", 11.0, tagColor)
            d.right("gear-level-$i", 426.0, y + 30, 24, "+${g.level}", "v3num", 24.0, heat(g.level))
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
            ForgeV3MotionKind.CHARGE -> { dx = if ((nowMs / 50) % 2 == 0L) 2.0 else -2.0; dy = if ((nowMs / 75) % 2 == 0L) 1.0 else -1.0 }
            ForgeV3MotionKind.HIT -> scale = 1.0 + 0.08 * (1 - p).pow(3)
            ForgeV3MotionKind.SHAKE, ForgeV3MotionKind.BREAK -> dx = 12.0 * (1 - p) * sin(p * PI * 6)
            null -> Unit
        }
        // Same placement as the artboard: the sword is 540px tall, the armour icon 320px.
        val baseW = if (g.weaponArt) 540.0 * 61 / 279 else 320.0
        val baseH = if (g.weaponArt) 540.0 else 320.0
        val cx = HERO_X + dx
        val cy = HERO_Y + dy
        val w = baseW * scale
        val h = baseH * scale
        val left = cx - w / 2
        val top = cy - h / 2

        // Heat aura (the artboard's drop-shadow), as a tinted silhouette glow
        val auraColor = if (kind == ForgeV3MotionKind.CHARGE) heat((shown + 1).coerceAtMost(30)) else heat(shown)
        val auraAlpha = when {
            kind == ForgeV3MotionKind.CHARGE -> 0.55 + 0.4 * p
            g.broken || shown <= 5 -> 0.0
            shown >= 21 -> 0.85
            shown >= 11 -> 0.7
            else -> 0.55
        }
        if (auraAlpha > 0) {
            if (g.weaponArt) {
                val k = h / 279.0
                d.sprite("aura-0", "v3_sword_glow_0", left - 24 * k, top - 24 * k, 109 * k, 256 * k, auraColor, auraAlpha, 3)
                d.sprite("aura-1", "v3_sword_glow_1", left - 24 * k, top + 232 * k, 109 * k, 71 * k, auraColor, auraAlpha, 3)
            } else d.sprite("aura", "v3_glow", cx - w * 0.75, cy - h * 0.75, w * 1.5, h * 1.5, auraColor, auraAlpha * 0.8, 3)
        }

        // Flash behind and over the item
        val flash = when (kind) {
            ForgeV3MotionKind.CHARGE -> heat((shown + 1).coerceAtMost(30)) to 0.5 * p
            ForgeV3MotionKind.HIT -> "#FFF1DE" to if (p < 0.35) 1 - p / 0.35 else 0.0
            ForgeV3MotionKind.BREAK -> "#C2554A" to if (p < 0.3) 1 - p / 0.3 else 0.0
            else -> null
        }
        if (flash != null && flash.second > 0.1) d.sprite("flash", "v3_glow", cx - 260, cy - 300, 520, 600, flash.first, flash.second, 4)

        if (g.weaponArt) {
            val k = h / 279.0
            d.sprite("hero-item-0", "v3_sword_hero_0", left, top, w, 256 * k, depth = 5)
            d.sprite("hero-item-1", "v3_sword_hero_1", left, top + 256 * k, w, 23 * k, depth = 5)
        } else if (g.iconItem != null) d.node("hero-item", cx - w * 0.625, cy - h * 0.625, w * 1.25, h * 1.25, item = g.iconItem, depth = 5)
        if (flash != null && flash.second > 0.1 && kind != ForgeV3MotionKind.CHARGE)
            d.sprite("flash-over", "v3_glow", cx - 120, cy - 160, 240, 320, flash.first, flash.second * 0.7, 6)

        if (g.broken) listOf(
            listOf(10, 2, 2, 4, 0), listOf(9, 6, 2, 4, 1), listOf(11, 10, 2, 3, 0), listOf(13, 12, 3, 1, 1),
            listOf(10, 13, 2, 5, 1), listOf(7, 17, 3, 1, 0), listOf(11, 18, 2, 5, 0), listOf(9, 23, 2, 4, 1), listOf(11, 27, 2, 3, 0),
        ).forEachIndexed { i, (x, y, cw, ch, dark) ->
            d.rect("crack-$i", 514 + x * 10, 290 + y * 10, cw * 10, ch * 10, if (dark == 1) "#E6C2554A" else "#E6E58B80", 7)
        }

        // Embers rise from hot items (the artboard's idle loop)
        val embers = when { g.broken -> 0; shown >= 21 -> 10; shown >= 16 -> 7; shown >= 11 -> 5; shown >= 6 -> 3; else -> 0 }
        for (i in 0 until embers) {
            val period = 2600 + (i * 7 % 5) * 350
            val phase = ((nowMs + i * 370L) % period).toDouble() / period
            val a = if (phase < 0.15) phase / 0.15 else 1 - phase
            val size = if (i % 3 == 0) 6.0 else 4.0
            val x = 484 + 280 * (0.5 + ((i * 37) % 60 - 30) / 100.0)
            d.rect("ember-$i", x, 800 - 90 - (i * 23) % 60 - phase * 300, size, size, argb(if (i % 4 == 0) "#FFF1DE" else heat(shown), a * 0.95), 6)
        }

        // Ring and sparks burst from the item
        if (motion != null && kind != ForgeV3MotionKind.CHARGE) {
            val color = when (kind) { ForgeV3MotionKind.HIT -> heat(shown); ForgeV3MotionKind.BREAK -> "#C2554A"; else -> "#6F6B65" }
            if (kind != ForgeV3MotionKind.SHAKE && p < 0.67) {
                val q = p / 0.67
                val r = 36 + 306 * (1 - (1 - q).pow(2))
                d.sprite("ring", "v3_ring", HERO_X - r, HERO_Y - 40 - r, r * 2, r * 2, color, 0.95 * (1 - q), 8)
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
                    val size = if (random.nextDouble() < 0.3) 10.0 else 6.0
                    val white = random.nextDouble() < 0.35 && kind != ForgeV3MotionKind.SHAKE
                    d.rect("spark-$i", HERO_X + cos(angle) * dist * ease - size / 2,
                        HERO_Y - 40 + (sin(angle) * dist * 0.9 - power * 0.35) * ease - size / 2, size, size,
                        argb(if (white) "#FFF1DE" else color, 1 - sp), 8)
                }
            }
        }

        // Result toast at the item's feet, as in the artboard
        if (banner != null && nowMs < banner.untilMs) {
            val bw = maxOf(width(banner.title, "v3num", 40.0), width(banner.sub, "v3r", 14.0)) + 48
            val bx = HERO_X - bw / 2
            d.round("toast", bx, 704.0, bw, 102.0, 12.0, "#0A0B0D", 0.88, 8)
            d.border("toast-line", bx, 704.0, bw, 102.0, 12.0, banner.color, 0.6, 9)
            d.text("toast-title", bx, 716, bw, 40, banner.title, "v3num", 40.0, banner.color, "center", 9)
            d.text("toast-sub", bx, 764, bw, 20, banner.sub, "v3r", 14.0, "#C9C3B8", "center", 9)
        }
    }

    private fun details(d: Draw, state: ForgeV3State) {
        val g = state.gear
        val maxed = g.level >= 30
        var tx = 792.0
        fun tag(id: String, text: String, color: String, bg: String, alpha: Double) {
            val tw = width(text, "v3b", 12.0) + 20
            d.round(id, tx, 244.0, tw, 25.0, 4.0, bg, alpha, 1)
            d.text("$id-text", tx + 10, 248, tw, 17, text, "v3b", 12.0, color)
            tx += tw + 8
        }
        tag("tag-rarity", g.rarity, rarityColor(g.rarity), when (g.rarity) { "レア" -> "#BE82E6"; "マジック" -> "#6F9BE0"; else -> "#FFFFFF" },
            if (g.rarity == "ノーマル") 0.07 else 0.16)
        tag("tag-tier", "T${g.tier} ${g.slotLabel}", "#C9C3B8", "#FFFFFF", 0.07)
        if (g.kindLabel.isNotEmpty()) tag("tag-kind", g.kindLabel, "#C9C3B8", "#FFFFFF", 0.07)
        if (g.broken) tag("tag-broken", "破損中 · 要修理", "#E58B80", "#C2554A", 0.2)
        d.text("hero-name", 792, 277, 564, 64, g.name, "v3b", 44.0, rarityColor(g.rarity))

        // A flex row as in the artboard: current column, 22px gap, 40px arrow, 22px gap, next column.
        val nowLabel = "現在 · ${heatName(g.level)}"
        d.text("level-now-label", 792, 364, 200, 17, nowLabel, "v3r", 12.0, "#A29E97")
        d.text("level-now", 792, 383, 160, 56, "+${g.level}", "v3num", 56.0, if (maxed) heat(g.level) else "#9A958D")
        if (!maxed) {
            val ax = 792 + maxOf(width(nowLabel, "v3r", 12.0), width("+${g.level}", "v3num", 56.0)) + 22
            d.rect("arrow-0", ax, 405, 30, 10, "#6F6B65", 2)
            d.rect("arrow-1", ax + 25, 400, 5, 20, "#6F6B65", 2)
            d.rect("arrow-2", ax + 30, 405, 5, 10, "#6F6B65", 2)
            val next = g.level + 1
            d.text("level-next-label", ax + 62, 350, 200, 17, "成功時 · ${heatName(next)}", "v3r", 12.0, heat(next))
            d.text("level-next", ax + 62, 369, 240, 84, "+$next", "v3num", 84.0, heat(next))
        }

        // Stats panel
        val rows = buildList {
            add(listOf(g.statLabel, "${g.power}", if (maxed) "" else "${g.nextPower}", if (maxed) "" else "+${g.nextPower - g.power}"))
            g.speedPercent?.let { s -> add(listOf("攻撃速度", "+${percent(s)}%", if (maxed) "" else "+${percent(s + 0.8)}%", if (maxed) "" else "+0.8")) }
        }
        val panelH = 12.0 + rows.size * 45 + 41
        d.round("stats", 792.0, 469.0, 564.0, panelH, 14.0, "#101114", 0.86, 1)
        d.border("stats-line", 792.0, 469.0, 564.0, panelH, 14.0, "#FFFFFF", 0.09, 2)
        val nextColor = if (maxed) "#9A958D" else heat(g.level + 1)
        rows.forEachIndexed { i, r ->
            val y = 475.0 + i * 45
            d.text("stat-label-$i", 813, y + 11, 300, 21, r[0], "v3r", 15.0, "#C9C3B8")
            d.right("stat-diff-$i", 1335.0, y + 16, 14, r[3], "v3num", 14.0, nextColor)
            val nextW = width(r[2], "v3num", 21.0)
            val nextX = 1279.0 - 18 - nextW
            d.text("stat-next-$i", nextX, y + 10, nextW + 1, 21, r[2], "v3num", 21.0, nextColor)
            d.right("stat-now-$i", nextX - 18, y + 10, 21, r[1], "v3num", 21.0, "#9A958D")
            d.rect("stat-rule-$i", 813, y + 44, 522, 1, "#12FFFFFF", 2)
        }
        val stateY = 475.0 + rows.size * 45
        d.text("state-label", 813, stateY + 10, 200, 20, "状態", "v3r", 14.0, "#A29E97")
        d.right("state-value", 1335.0, stateY + 9, 21, if (g.broken) "破損中（能力が無効）" else "正常", "v3r", 15.0,
            if (g.broken) "#E58B80" else "#ECE6DC")

        // MODs
        val modTop = 469.0 + panelH + 16
        d.text("mod-title", 792, modTop, 200, 19, "MOD ${g.mods.size} / ${g.modCapacity}", "v3r", 13.0, "#A29E97")
        d.right("mod-note", 1356.0, modTop + 1, 17, "強化してもMODは変わりません", "v3r", 12.0, "#6F6B65")
        if (g.mods.isEmpty()) d.text("mod-none", 792, modTop + 27, 564, 20, "ノーマル装備です。刻印工房のオーブでMODを付けられます", "v3r", 14.0, "#6F6B65")
        g.mods.take(6).forEachIndexed { i, m ->
            val y = modTop + 27 + i * 27
            d.text("mod-kind-$i", 792, y + 4, 40, 16, m.kind, "v3r", 11.0, "#6F6B65")
            d.text("mod-text-$i", 842, y, 480, 21, m.text, "v3r", 15.0, "#8FB4F0")
            if (m.source.isNotEmpty()) d.right("mod-src-$i", 1356.0, y + 3, 17, m.source, "v3r", 12.0, "#6F6B65")
        }

        // +0..+30 track
        val notes = listOf("必ず成功", "赤熱", "橙熱", "破損あり", "破損あり", "破損あり")
        for (b in 0 until 6) {
            val bx = 505.0 + b * 140.4
            for (k in 0 until 5) {
                val lv = b * 5 + k + 1
                val x = bx + k * 26.3
                val color = heat(lv)
                when {
                    lv <= g.level -> d.rect("cell-$lv", x, 951, 23, 12, color, 2)
                    lv == g.level + 1 -> {
                        d.rect("cell-$lv-t", x, 951, 23, 2, color, 2); d.rect("cell-$lv-b", x, 961, 23, 2, color, 2)
                        d.rect("cell-$lv-l", x, 953, 2, 8, color, 2); d.rect("cell-$lv-r", x + 21, 953, 2, 8, color, 2)
                    }
                    else -> {
                        d.rect("cell-$lv", x, 951, 23, 12, "#25272C", 2)
                        if (lv >= 16) d.rect("cell-$lv-risk", x, 960, 23, 3, "#8CC2554A", 3)
                    }
                }
            }
            val inBand = g.level + 1 in (b * 5 + 1)..(b * 5 + 5)
            d.text("band-range-$b", bx, 969, 60, 11, "+${b * 5 + 1}〜${b * 5 + 5}", "v3num", 11.0, if (inBand) heat(b * 5 + 1) else "#6F6B65")
            d.right("band-note-$b", bx + 128, 969, 16, notes[b], "v3r", 11.0, if (b >= 3) "#E58B80" else "#6F6B65")
        }
    }

    private fun right(d: Draw, state: ForgeV3State, actions: Boolean) {
        val g = state.gear
        val maxed = g.level >= 30
        val rate = if (maxed) 0.0 else state.chance
        val rateColor = when { rate >= 100 -> "#FFF1DE"; rate >= 60 -> "#FFC48A"; rate >= 25 -> "#F28A3C"; else -> "#D9563A" }
        val pct = width("%", "v3num", 26.0)
        d.text("chance-unit", 1841 - pct, 166, pct + 1, 26, if (maxed) "" else "%", "v3num", 26.0, rateColor)
        d.right("chance-value", 1841 - pct, 133, 64, if (maxed) "—" else percent(rate), "v3num", 64.0, rateColor)
        val lit = (rate / 5).roundToInt()
        for (i in 0 until 20) {
            val color = if (i >= lit) "#2A2C31" else when { i < 5 -> "#8E2B1C"; i < 10 -> "#C23A1E"; i < 15 -> "#F07A2A"; else -> "#FFC48A" }
            d.rect("seg-$i", 1407 + i * 21.85, 209, 19.0, 18, color, 2)
        }
        if (!maxed) {
            val lines = buildList {
                add(Triple("基礎（+${g.level + 1}への強化）", "${percent(state.baseChance)}%", "#C9C3B8"))
                add(Triple("鍛冶熟練 Rank ${state.masteryRank}", "+${state.masteryBonus.roundToInt()}", "#C9C3B8"))
                if (state.focused) add(Triple("集中鍛造", "+${state.catalystBonus.roundToInt()}", "#FFC48A"))
                if (state.pityGuaranteed && g.pityThreshold > 0) add(Triple("天井に到達", "確定", "#FFC48A"))
            }
            lines.forEachIndexed { i, (label, value, color) ->
                d.text("why-label-$i", 1407, 239 + i * 23, 300, 19, label, "v3r", 13.0, "#A29E97")
                d.right("why-value-$i", 1841.0, 239 + i * 23, 19, value, if (value == "確定") "v3r" else "v3num", 14.0, color)
            }
        }

        // Pity (box and title are in the plate)
        if (!maxed && g.pityThreshold > 0) {
            val pip = (188.0 - (g.pityThreshold - 1) * 4) / g.pityThreshold
            for (i in 0 until g.pityThreshold) d.rect("pity-pip-$i", 1419 + i * (pip + 4), 334, pip, 12,
                if (i < g.failures) "#F07A2A" else "#2A2C31", 2)
        }
        val pityText = when {
            maxed -> "最大強化に到達"
            g.pityThreshold == 0 -> "+5までは必ず成功"
            g.failures >= g.pityThreshold -> "天井到達 · 次は確定成功"
            else -> "失敗 ${g.failures} / ${g.pityThreshold} · あと${g.pityThreshold - g.failures}回で確定"
        }
        d.text("pity-text", 1419, if (g.pityThreshold > 0 && !maxed) 354 else 334, 188, 19, pityText, "v3r", 13.0,
            if (g.pityThreshold > 0 && g.failures >= g.pityThreshold) "#FFC48A" else "#ECE6DC")

        // Break risk
        val risky = !maxed && state.risky
        d.round("break", 1629.0, 297.0, 212.0, 115.0, 10.0, if (risky) "#C2554A" else "#FFFFFF", if (risky) 0.10 else 0.035, 1)
        if (risky) d.border("break-line", 1629.0, 297.0, 212.0, 115.0, 10.0, "#C2554A", 0.45, 2)
        d.text("break-title", 1641, 309, 188, 17, "失敗したときの破損", "v3r", 12.0, "#A29E97")
        d.text("break-value", 1641, 332, 188, 28, if (risky) "${percent(state.breakOnFailure)}%" else "なし", "v3num", 28.0,
            if (risky) "#E58B80" else "#ECE6DC")
        val breakSub = when {
            risky -> listOf("1回あたり ${percent((100 - state.chance) * state.breakOnFailure / 100)}%", "壊れても強化値とMODは残ります")
            state.breakOnFailure > 0 -> listOf("確定成功なので壊れません")
            else -> listOf("+15から失敗時に破損抽選があり", "ます")
        }
        breakSub.forEachIndexed { i, line -> d.text("break-sub-$i", 1641, 366 + i * 17, 188, 17, line, "v3r", 12.0, "#A29E97") }

        // Costs
        d.text("cost-title", 1407, 430, 330, 21, if (maxed) "必要な素材" else "必要な素材 · T${state.costTier}（+${g.level + 1} の段階で決まる）",
            "v3m", 15.0, "#C9C3B8")
        state.costs.take(5).forEachIndexed { i, c ->
            val y = 461.0 + i * 67
            val ok = c.owned >= c.required
            d.round("cost-$i", 1407.0, y, 434.0, 60.0, 10.0, if (ok) "#FFFFFF" else "#C2554A", if (ok) 0.035 else 0.08, 1)
            if (!ok) d.border("cost-$i-line", 1407.0, y, 434.0, 60.0, 10.0, "#C2554A", 0.5, 2)
            d.slot("cost-slot-$i", 1417.0, y + 8, 44.0)
            d.node("cost-icon-$i", 1421, y + 12, 36, 36, sprite = sprites.getValue(c.icon), depth = 4)
            d.text("cost-name-$i", 1473, y + 15, 220, 20, c.name, "v3m", 14.0, "#ECE6DC")
            d.text("cost-tier-$i", 1473 + width(c.name, "v3m", 14.0) + 6, y + 17, 40, 17, c.tierLabel, "v3m", 12.0, "#A29E97")
            d.round("cost-bar-$i", 1473.0, y + 40, 304.0, 4.0, 2.0, "#2A2C31", 1.0, 2)
            val fill = if (!ok) 1.0 else if (c.owned == 0L) 0.0 else c.required.toDouble() / c.owned
            d.round("cost-fill-$i", 1473.0, y + 40, 304.0 * fill.coerceIn(0.0, 1.0), 4.0, 2.0, if (ok) "#ECE6DC" else "#C2554A", 1.0, 3)
            val small = " / ${format.format(c.owned)}"
            val smallW = width(small, "v3num", 13.0)
            d.text("cost-own-$i", 1830 - smallW, y + 18, smallW + 1, 13, small, "v3num", 13.0, "#A29E97")
            d.right("cost-need-$i", 1830 - smallW, y + 11, 20, format.format(c.required), "v3num", 20.0, if (ok) "#ECE6DC" else "#E58B80")
            d.right("cost-after-$i", 1831.0, y + 33, 16, if (ok) "残り ${format.format(c.owned - c.required)}" else "あと ${format.format(c.required - c.owned)} 不足",
                "v3r", 11.0, if (ok) "#A29E97" else "#E58B80")
        }

        // Focused forging toggle, right after the cost rows
        val ty = 461.0 + state.costs.size.coerceAtMost(5) * 67 + 3
        val on = state.focused
        val enabled = state.focusAvailable || on
        val dim = if (enabled) 1.0 else 0.55
        d.round("catalyst", 1407.0, ty, 434.0, 67.0, 10.0, if (on) "#F07A2A" else "#FFFFFF", (if (on) 0.10 else 0.035) * dim, 1)
        d.border("catalyst-line", 1407.0, ty, 434.0, 67.0, 10.0, if (on) "#F07A2A" else "#FFFFFF", (if (on) 0.7 else 0.09) * dim, 2)
        if (actions && enabled) d.hit("catalyst-hit", 1407, ty, 434, 67, "catalyst", "#14F07A2A")
        d.text("catalyst-title", 1424, ty + 13, 340, 21, "集中鍛造（精錬触媒）", "v3m", 15.0, if (enabled) "#ECE6DC" else "#827E77")
        d.text("catalyst-sub", 1424, ty + 37, 340, 17, when {
            maxed -> "最大強化です"
            g.broken -> "修理してから使えます"
            !enabled -> "今回は確定成功なので不要です"
            else -> "成功率 +15 · 加工石材1・布1・刻印粉2 を追加で消費"
        }, "v3r", 12.0, if (enabled) "#FFC48A" else "#6F6B65")
        d.round("switch", 1778.0, ty + 20, 46.0, 26.0, 13.0, if (on) "#F07A2A" else "#3A3C42", dim, 2)
        d.round("knob", if (on) 1801.0 else 1781.0, ty + 23, 20.0, 20.0, 10.0, "#ECE6DC", dim, 3)

        // Enhance button
        val ready = !state.busy && state.blockedReason == null && !maxed
        val label = when {
            state.busy -> "鍛造中…"
            maxed -> "最大強化 +30"
            ready -> "強化する"
            else -> state.blockedReason ?: "強化できません"
        }
        if (ready) {
            d.round("enhance-lip", 1407.0, 876.0, 434.0, 72.0, 10.0, "#C9BDA3", 1.0, 1)
            d.round("enhance-face", 1407.0, 876.0, 434.0, 67.0, 10.0, "#EFE6D2", 1.0, 2)
            if (state.risky) d.border("enhance-risk", 1405.0, 874.0, 438.0, 76.0, 12.0, "#C2554A", 0.7, 2, 2.0)
            else d.border("enhance-line", 1406.0, 875.0, 436.0, 74.0, 11.0, "#FFFFFF", 0.15, 2)
            if (actions) d.hit("enhance", 1407, 876, 434, 72, "enhance", "#18FFFFFF")
        } else {
            d.round("enhance-face", 1407.0, 876.0, 434.0, 72.0, 10.0, "#24262B", 1.0, 1)
            d.border("enhance-line", 1407.0, 876.0, 434.0, 72.0, 10.0, "#303238", 1.0, 2)
        }
        val big = ready || state.busy || maxed
        d.text("enhance-label", 1407, if (big) 894 else 900, 434, if (big) 35 else 24, label, if (big) "v3b" else "v3m",
            if (big) 24.0 else 18.0, if (ready) "#17181B" else "#8A867F", "center", 4)
        val hint = when {
            state.busy -> "装備に熱を込めています"
            g.broken -> "装備庫で修理すると、強化値もMODも元どおり"
            maxed -> "これ以上は強化できません"
            state.blockedReason != null -> state.note
            state.risky -> "失敗すると ${percent(state.breakOnFailure)}% で破損します"
            else -> state.note
        }
        d.text("enhance-hint", 1407, 958, 434, 17, hint, "v3r", 12.0, if (state.risky && ready) "#E58B80" else "#A29E97", "center")
    }

    private fun footer(d: Draw, state: ForgeV3State) {
        d.text("footer-log", 130, 1026, 900, 19, state.history ?: "まだ記録はありません", "v3r", 13.0,
            if (state.history == null) "#6F6B65" else "#C9C3B8")
        var x = 1864.0
        for ((key, label) in listOf("Shift" to "閉じる", "左クリック" to "選択")) {
            val lw = width(label, "v3r", 13.0)
            x -= lw
            d.text("help-$key-label", x, 1026, lw + 1, 19, label, "v3r", 13.0, "#A29E97")
            val keyFamily = if (key == "Shift") "v3num" else "v3r"
            val kw = width(key, keyFamily, 13.0) + 16
            x -= 8 + kw
            d.round("help-$key", x, 1023.0, kw, 26.0, 5.0, "#3A3C42", 1.0, 1)
            d.round("help-$key-face", x + 1, 1024.0, kw - 2, 21.0, 4.0, "#1E2025", 1.0, 2)
            d.text("help-$key-text", x, 1028, kw, 13, key, keyFamily, 13.0, "#ECE6DC", "center")
            x -= 26
        }
    }

    private fun modal(d: Draw, state: ForgeV3State) {
        val g = state.gear
        d.rect("modal-mask", -400, -200, 2720, 1480, "#B80B0C0E", 20)
        d.round("modal", 660.0, 300.0, 600.0, 440.0, 14.0, "#101114", 0.97, 21)
        d.border("modal-line", 660.0, 300.0, 600.0, 440.0, 14.0, "#C2554A", 0.7, 22)
        d.text("modal-title", 700, 330, 520, 36, "破損の危険があります", "v3b", 26.0, "#E58B80", depth = 23)
        d.text("modal-gear", 700, 380, 520, 28, "${g.name}  +${g.level} → +${g.level + 1}", "v3m", 18.0, heat(g.level + 1), depth = 23)
        listOf(
            "成功率 ${percent(state.chance)}%",
            "失敗すると ${percent(state.breakOnFailure)}% の確率で破損します",
            "1回あたりの破損 ${percent((100 - state.chance) * state.breakOnFailure / 100)}%",
            "破損しても強化値とMODは残ります",
            "修理：同Tier・同部位の+0装備を1個",
        ).forEachIndexed { i, line ->
            d.text("modal-line-$i", 700, 428 + i * 30, 520, 21, line, "v3r", 15.0, if (i < 3) "#ECE6DC" else "#A29E97", depth = 23)
        }
        d.round("modal-cancel-face", 700.0, 652.0, 250.0, 60.0, 10.0, "#2A2D31", 1.0, 23)
        d.node("modal-cancel", 700, 652, 250, 60, mapOf("background-color" to "#00000000", "hover-background-color" to "#22FFFFFF"),
            action = "cancel", depth = 24)
        d.text("modal-cancel-label", 700, 667, 250, 30, "やめる", "v3b", 20.0, "#ECE6DC", "center", 25)
        d.round("modal-confirm-face", 970.0, 652.0, 250.0, 60.0, 10.0, "#EFE6D2", 1.0, 23)
        d.node("modal-confirm", 970, 652, 250, 60, mapOf("background-color" to "#00000000", "hover-background-color" to "#22FFFFFF"),
            action = "confirm", depth = 24)
        d.text("modal-confirm-label", 970, 667, 250, 30, "強化する", "v3b", 20.0, "#17181B", "center", 25)
    }
}
