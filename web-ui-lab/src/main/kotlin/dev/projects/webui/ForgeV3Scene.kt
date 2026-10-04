package dev.projects.webui

import com.google.gson.JsonParser
import dev.projects.webui.polish05.Polish05ScreenSpace
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

data class ForgeV3Mod(val kind: String, val text: String)
data class ForgeV3Gear(
    val id: String, val name: String, val slotLabel: String, val tier: Int, val rarity: String,
    val level: Int, val failures: Int, val pityThreshold: Int, val broken: Boolean,
    val statLabel: String, val power: Int, val nextPower: Int, val speedPercent: Double? = null,
    val mods: List<ForgeV3Mod> = emptyList(), val modCapacity: Int = 0,
    val iconItem: String? = null, val heroSprite: String? = null, val thumbSprite: String? = null,
)
data class ForgeV3Cost(val name: String, val owned: Long, val required: Long, val icon: String)
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

/** The v3 forge: the item itself carries the enhancement heat. Stage is 1440x920 like Polish05. */
class ForgeV3Scene(spriteJson: ByteArray) {
    private val screen = Polish05ScreenSpace(800.0, 480.0)
    private val sprites = JsonParser.parseString(spriteJson.toString(Charsets.UTF_8)).asJsonObject.entrySet().associate { (name, value) ->
        val v = value.asJsonObject
        name to UiSprite(v.get("char").asString, v.get("font").asString, v.get("width").asInt, v.get("height").asInt)
    }
    private val format = NumberFormat.getIntegerInstance(Locale.US)

    companion object {
        // Vanilla glyphs are 8px. These stage sizes land on whole pixel multiples in the 800x480 canvas.
        const val S1 = 15.34
        const val S15 = 23.0
        const val S2 = 30.67
        const val S3 = 46.0
        const val S4 = 61.34
        const val HERO_X = 470.0
        const val HERO_Y = 380.0

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
        fun alpha(hex: String, a: Double): String {
            val v = (a.coerceIn(0.0, 1.0) * 255).roundToInt()
            return "#" + v.toString(16).padStart(2, '0') + hex.removePrefix("#").takeLast(6)
        }
        fun percent(v: Double) = "%.1f".format(Locale.US, v)
    }

    private class Nodes(private val screen: Polish05ScreenSpace) {
        val list = mutableListOf<UiNode>()
        fun box(x: Double, y: Double, w: Double, h: Double) = screen.forward(x, y).let { Box(it.x, it.y, w * screen.scale, h * screen.scale) }
        fun add(id: String, x: Number, y: Number, w: Number, h: Number, text: String = "", color: String = "#ECE6DC",
                size: Double = S1, bg: String? = null, hover: String? = null, action: String? = null,
                sprite: UiSprite? = null, item: String? = null, depth: Int = 2, align: String = "left") {
            val style = mutableMapOf("color" to color, "font-size" to "${size * screen.scale}px", "text-align" to align)
            if (bg != null) style["background-color"] = bg
            if (hover != null) style["hover-background-color"] = hover
            list += UiNode(id, box(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble()), text, style, action, item, true, depth, sprite)
        }
        fun rect(id: String, x: Number, y: Number, w: Number, h: Number, color: String, depth: Int = 1) =
            add(id, x, y, w, h, bg = color, depth = depth)
    }

    /** Approximate stage width of vanilla text, for chips and tags. */
    private fun textWidth(text: String, size: Double) = UiGeometry.textAdvance(text, false) * size / 8.0

    fun build(state: ForgeV3State, nowMs: Long = System.currentTimeMillis(),
              motion: ForgeV3Motion? = null, banner: ForgeV3Banner? = null): UiScene {
        val n = Nodes(screen)
        val live = motion?.takeIf { nowMs - it.startMs in 0 until it.kind.durationMs }
        val actions = !state.modal && !state.busy

        // Page and forge light
        n.rect("viewport-backdrop", -200, -150, 1840, 1220, "#121316", 0)
        n.rect("glow-0", 363, 120, 214, 560, "#0AF07A2A", 0)

        header(n, state)
        left(n, state, actions)
        center(n, state, nowMs, live, banner)
        right(n, state, actions)

        n.add("footer-log", 42, 872, 700, 18, state.history?.let { "鍛造記録  $it" } ?: "鍛造記録  まだありません",
            color = if (state.history == null) "#6F6B65" else "#C9C3B8")
        n.add("footer-help", 790, 872, 440, 18, "左クリック 選択  │  Shift 閉じる", color = "#A29E97")
        n.add("sound", 1240, 866, 72, 30, if (state.muted) "SE OFF" else "SE ON", color = "#C9C3B8", bg = "#1E2025",
            hover = "#2C2F35", action = if (actions) "sound" else null, align = "center", depth = 3)
        n.add("close", 1320, 866, 78, 30, "閉じる", color = "#ECE6DC", bg = "#1E2025",
            hover = "#2C2F35", action = if (!state.busy) "close" else null, align = "center", depth = 3)

        if (state.modal) modal(n, state)
        return UiScene(800.0, 480.0, n.list)
    }

    private fun header(n: Nodes, state: ForgeV3State) {
        n.rect("brand-slot", 42, 22, 44, 44, "#0D0E10", 2)
        n.add("brand-icon", 52, 29, 24, 30, sprite = sprites.getValue("hammer"), depth = 3)
        n.add("brand-title", 98, 22, 240, 26, "鍛冶師の仕事場", size = S15)
        n.add("brand-place", 98, 50, 240, 16, "帰還港 · 工房", color = "#A29E97")
        listOf("強化" to 560, "精錬" to 640, "製作" to 720, "修理" to 800, "装備庫" to 880).forEachIndexed { i, (label, x) ->
            n.add("tab-$i", x, 30, 80, 26, label, color = if (i == 0) "#ECE6DC" else "#6F6B65", size = S15)
        }
        n.rect("tab-underline", 560, 60, 46, 3, "#F07A2A", 2)

        n.rect("chip-rank", 1000, 22, 170, 44, "#DB101114", 1)
        n.add("rank-label", 1012, 26, 90, 16, "鍛冶熟練", color = "#A29E97")
        n.rect("rank-bar", 1012, 48, 80, 4, "#2A2C31", 2)
        n.rect("rank-fill", 1012, 48, 80.0 * state.masteryProgress / 20.0, 4, "#ECE6DC", 3)
        n.add("rank-value", 1100, 24, 66, 24, "Rank ${state.masteryRank}", size = S15)
        n.add("rank-xp", 1100, 48, 66, 16, "${state.masteryProgress}/20", color = "#A29E97")

        n.rect("chip-dust", 1178, 22, 108, 44, "#DB101114", 1)
        n.add("dust-icon", 1188, 32, 24, 24, sprite = sprites.getValue("forge_material_affix_dust"), depth = 3)
        n.add("dust-value", 1218, 32, 64, 24, format.format(state.dust), size = S15)

        n.rect("chip-silver", 1294, 22, 104, 44, "#DB101114", 1)
        n.add("silver-icon", 1302, 33, 20, 21, sprite = sprites.getValue("coin_ore"), depth = 3)
        n.add("wallet", 1326, 32, 70, 24, format.format(state.silver), size = S15)
    }

    private fun gearIcon(n: Nodes, id: String, g: ForgeV3Gear, x: Double, y: Double, size: Double, depth: Int) {
        val sprite = g.thumbSprite?.let(sprites::getValue)
        if (g.iconItem != null) n.add(id, x, y, size, size, item = g.iconItem, depth = depth)
        else if (sprite != null) {
            val h = size
            val w = h * sprite.width / sprite.height
            n.add(id, x + (size - w) / 2, y, w, h, sprite = sprite, depth = depth)
        }
    }

    private fun left(n: Nodes, state: ForgeV3State, actions: Boolean) {
        n.rect("left-panel", 42, 88, 300, 768, "#DB101114", 0)
        n.add("left-title", 60, 102, 120, 26, "装備中", size = S15)
        n.add("left-caption", 196, 108, 140, 16, "武器1 · 防具4部位", color = "#A29E97")
        state.gears.forEachIndexed { i, g ->
            val y = 140.0 + i * 76
            val selected = g.id == state.selected
            n.add("gear-row-$i", 54, y, 276, 70, bg = if (selected) "#1FEFE6D2" else "#06FFFFFF",
                hover = if (selected) "#26EFE6D2" else "#16EFE6D2", action = if (actions) "select:${g.id}" else null, depth = 1)
            if (selected) n.rect("gear-accent-$i", 54, y, 3, 70, "#EFE6D2", 2)
            n.rect("gear-slot-$i", 64, y + 11, 48, 48, "#0D0E10", 2)
            n.rect("gear-slot-lo-$i", 64, y + 56, 48, 3, "#2C2E34", 3)
            gearIcon(n, "gear-icon-$i", g, 66.0, y + 13, 44.0, 4)
            if (g.broken) n.rect("gear-broken-$i", 64, y + 11, 48, 48, "#55C2554A", 5)
            n.add("gear-name-$i", 122, y + 6, 140, 18, g.name, color = rarityColor(g.rarity), depth = 3)
            n.add("gear-sub-$i", 122, y + 27, 140, 16, "T${g.tier} · ${if (g.id == "weapon") "攻撃" else "HP"} ${g.power}", color = "#A29E97", depth = 3)
            val (tag, tagColor) = when {
                g.broken -> "破損中 · 要修理" to "#E58B80"
                g.level >= 30 -> "最大強化" to "#6F6B65"
                g.pityThreshold > 0 && g.failures >= g.pityThreshold -> "次は確定成功" to "#FFC48A"
                g.pityThreshold > 0 -> "天井 ${g.failures} / ${g.pityThreshold}" to "#6F6B65"
                else -> "+5までは必ず成功" to "#6F6B65"
            }
            n.add("gear-tag-$i", 122, y + 47, 140, 16, tag, color = tagColor, depth = 3)
            n.add("gear-level-$i", 270, y + 20, 54, 30, "+${g.level}", color = heat(g.level), size = S2, align = "right", depth = 3)
        }
        n.rect("left-rule", 60, 532, 264, 1, "#22FFFFFF", 1)
        n.add("note-title", 60, 546, 264, 16, "工房より", color = "#A29E97")
        state.note.split("。").filter(String::isNotBlank).flatMap { "$it。".chunked(16) }.take(3).forEachIndexed { i, line ->
            n.add("note-$i", 60, 568 + i * 22, 264, 16, line, color = "#C9C3B8")
        }
        n.add("tip-0", 60, 790, 264, 16, "素材は採取 → 精錬で用意できます", color = "#6F6B65")
        n.add("tip-1", 60, 812, 264, 16, "足りない素材は港の市場でも買えます", color = "#6F6B65")
    }

    private fun center(n: Nodes, state: ForgeV3State, nowMs: Long, motion: ForgeV3Motion?, banner: ForgeV3Banner?) {
        val g = state.gear
        val shown = motion?.level ?: g.level
        val maxed = g.level >= 30
        val p = motion?.let { (nowMs - it.startMs).toDouble() / it.kind.durationMs }?.coerceIn(0.0, 1.0) ?: 0.0
        val kind = motion?.kind

        // Hero placement and motion on the item itself
        val sprite = g.heroSprite?.let(sprites::getValue)
        var w = if (sprite != null) 400.0 * sprite.width / sprite.height else 230.0
        var h = if (sprite != null) 400.0 else 230.0
        var dx = 0.0
        var dy = 0.0
        when (kind) {
            ForgeV3MotionKind.CHARGE -> { dx = if ((nowMs / 50) % 2 == 0L) 2.0 else -2.0; dy = if ((nowMs / 75) % 2 == 0L) 1.0 else -1.0 }
            ForgeV3MotionKind.HIT -> { val s = 1.0 + 0.08 * (1 - p).pow(3); w *= s; h *= s }
            ForgeV3MotionKind.SHAKE, ForgeV3MotionKind.BREAK -> dx = 12.0 * (1 - p) * sin(p * PI * 6)
            null -> Unit
        }
        val hx = HERO_X - w / 2 + dx
        val hy = HERO_Y - h / 2 + dy

        // Aura: stepped translucent plates in the heat colour, stronger with the level
        val auraColor = if (kind == ForgeV3MotionKind.CHARGE) heat((shown + 1).coerceAtMost(30)) else heat(shown)
        val auraBase = when {
            kind == ForgeV3MotionKind.CHARGE -> 0.30
            g.broken || shown <= 5 -> 0.0
            shown >= 21 -> 0.26
            shown >= 11 -> 0.19
            else -> 0.13
        }
        if (auraBase > 0) listOf(14.0 to 1.0, 32.0 to 0.55, 56.0 to 0.28).forEachIndexed { i, (pad, a) ->
            n.rect("aura-$i", hx - pad, hy - pad, w + pad * 2, h + pad * 2, alpha(auraColor, auraBase * a), 3 - i.coerceAtMost(2))
        }
        n.rect("hero-plinth", HERO_X - 110, HERO_Y + 214, 220, 10, if (g.broken) "#66C2554A" else "#55F07A2A", 2)
        n.rect("hero-shadow", HERO_X - 70, HERO_Y + 224, 140, 8, "#88000000", 2)

        // Idle embers rise from hot items
        val embers = when { g.broken -> 0; shown >= 21 -> 10; shown >= 16 -> 7; shown >= 11 -> 5; shown >= 6 -> 3; else -> 0 }
        for (i in 0 until embers) {
            val period = 2600 + (i * 7 % 5) * 350
            val phase = ((nowMs + i * 370L) % period).toDouble() / period
            val a = if (phase < 0.15) phase / 0.15 else 1 - phase
            val size = if (i % 3 == 0) 6.0 else 4.0
            n.rect("ember-$i", HERO_X + (i * 37 % 120) - 60, HERO_Y + 180 - phase * 320, size, size,
                alpha(if (i % 4 == 0) "#FFF1DE" else heat(shown), a * 0.9), 6)
        }

        if (g.iconItem != null && g.heroSprite == null) n.add("hero-item", hx, hy, w, h, item = g.iconItem, depth = 5)
        else if (sprite != null) n.add("hero-item", hx, hy, w, h, sprite = sprite, depth = 5)
        if (g.broken) listOf(
            Triple(10, 2, 4), Triple(9, 6, 4), Triple(11, 10, 3), Triple(10, 13, 5), Triple(11, 18, 5), Triple(9, 23, 4), Triple(11, 27, 3),
        ).forEachIndexed { i, (cx, cy, len) ->
            n.rect("crack-$i", HERO_X - 110 + cx * 10, HERO_Y - 190 + cy * 11.5, 20, len * 11.5, if (i % 2 == 0) "#E58B80" else "#C2554A", 7)
        }

        // Flash over the item
        val flash = when (kind) {
            ForgeV3MotionKind.CHARGE -> heat((shown + 1).coerceAtMost(30)) to 0.35 * p
            ForgeV3MotionKind.HIT -> "#FFF1DE" to if (p < 0.35) 0.75 * (1 - p / 0.35) else 0.0
            ForgeV3MotionKind.BREAK -> "#C2554A" to if (p < 0.3) 0.6 * (1 - p / 0.3) else 0.0
            else -> null
        }
        if (flash != null && flash.second > 0.01) {
            n.rect("hero-flash", hx - 20, hy - 20, w + 40, h + 40, alpha(flash.first, flash.second), 4)
            n.rect("hero-flash-core", hx + w * 0.2, hy + h * 0.1, w * 0.6, h * 0.8, alpha("#FFF1DE", flash.second * 0.6), 4)
        }

        // Square ring and sparks burst from the item's centre
        if (motion != null && kind != ForgeV3MotionKind.CHARGE) {
            val color = when (kind) { ForgeV3MotionKind.HIT -> heat(shown); ForgeV3MotionKind.BREAK -> "#C2554A"; else -> "#8A8E96" }
            if (kind != ForgeV3MotionKind.SHAKE && p < 0.75) {
                val q = p / 0.75
                val r = 30 + 200 * (1 - (1 - q).pow(2))
                val c = alpha(color, 0.9 * (1 - q))
                n.rect("ring-t", HERO_X - r, HERO_Y - r, r * 2, 4, c, 8)
                n.rect("ring-b", HERO_X - r, HERO_Y + r - 4, r * 2, 4, c, 8)
                n.rect("ring-l", HERO_X - r, HERO_Y - r, 4, r * 2, c, 8)
                n.rect("ring-r", HERO_X + r - 4, HERO_Y - r, 4, r * 2, c, 8)
            }
            val count = when (kind) { ForgeV3MotionKind.HIT -> 10 + min(14, shown); ForgeV3MotionKind.BREAK -> 14; else -> 6 }
            val power = when (kind) { ForgeV3MotionKind.HIT -> 150.0 + shown * 6; ForgeV3MotionKind.BREAK -> 170.0; else -> 90.0 }
            val sparkP = (p * motion.kind.durationMs / 900.0).coerceIn(0.0, 1.0)
            if (sparkP < 1.0) {
                val random = java.util.Random(motion.seed)
                val ease = 1 - (1 - sparkP).pow(3)
                for (i in 0 until count) {
                    val angle = 2 * PI * i / count + random.nextDouble() * 0.5
                    val dist = power * (0.6 + random.nextDouble() * 0.6)
                    val size = if (random.nextDouble() < 0.3) 10.0 else 6.0
                    val white = random.nextDouble() < 0.35
                    n.rect("spark-$i", HERO_X + cos(angle) * dist * ease - size / 2,
                        HERO_Y + (sin(angle) * dist * 0.9 - power * 0.35) * ease - size / 2, size, size,
                        alpha(if (white && kind != ForgeV3MotionKind.SHAKE) "#FFF1DE" else color, 1 - sparkP), 8)
                }
            }
        }

        // Result banner at the item's feet
        if (banner != null && nowMs < banner.untilMs) {
            val bw = maxOf(textWidth(banner.title, S2), textWidth(banner.sub, S1)) + 48
            n.rect("banner-bg", HERO_X - bw / 2, HERO_Y + 244, bw, 70, "#E00A0B0D", 8)
            n.rect("banner-line", HERO_X - bw / 2, HERO_Y + 244, bw, 2, banner.color, 9)
            n.add("banner-title", HERO_X - bw / 2, HERO_Y + 252, bw, 32, banner.title, color = banner.color, size = S2, align = "center", depth = 9)
            n.add("banner-sub", HERO_X - bw / 2, HERO_Y + 288, bw, 18, banner.sub, color = "#C9C3B8", align = "center", depth = 9)
        }

        // Details beside the item
        var tx = 600.0
        fun tag(id: String, text: String, color: String, bg: String) {
            val tw = textWidth(text, S1) + 16
            n.add(id, tx, 108, tw, 24, text, color = color, bg = bg, align = "center", depth = 2)
            tx += tw + 8
        }
        tag("tag-rarity", g.rarity, rarityColor(g.rarity), when (g.rarity) { "レア" -> "#29BE82E6"; "マジック" -> "#296F9BE0"; else -> "#12FFFFFF" })
        tag("tag-tier", "T${g.tier} ${g.slotLabel}", "#C9C3B8", "#12FFFFFF")
        if (g.broken) tag("tag-broken", "破損中 · 要修理", "#E58B80", "#33C2554A")
        n.add("hero-name", 600, 140, 417, 34, g.name, color = rarityColor(g.rarity), size = S2)

        n.add("level-now-label", 600, 190, 150, 16, "現在 · ${heatName(shown)}", color = "#A29E97")
        n.add("level-now", 600, 210, 130, 48, "+$shown", color = if (maxed) heat(shown) else "#9A958D", size = S3)
        if (!maxed) {
            val next = shown + 1
            n.add("level-arrow", 724, 222, 30, 26, "→", color = "#6F6B65", size = S15)
            n.add("level-next-label", 766, 190, 200, 16, "成功時 · ${heatName(next)}", color = heat(next))
            n.add("level-next", 766, 202, 220, 64, "+$next", color = heat(next), size = S4)
        }

        val rows = buildList {
            add(listOf(g.statLabel, "${g.power}", if (maxed) "" else "${g.nextPower}", if (maxed) "" else "+${g.nextPower - g.power}"))
            g.speedPercent?.let { s ->
                add(listOf("攻撃速度", "+${percent(s)}%", if (maxed) "" else "+${percent(s + 0.8)}%", if (maxed) "" else "+0.8"))
            }
        }
        val statTop = 282.0
        val statH = rows.size * 34.0 + 34
        n.rect("stats-bg", 600, statTop, 417, statH, "#0CFFFFFF", 1)
        val nextColor = if (maxed) "#9A958D" else heat(shown + 1)
        rows.forEachIndexed { i, r ->
            val y = statTop + i * 34
            n.add("stat-label-$i", 614, y + 9, 170, 16, r[0], color = "#C9C3B8")
            n.add("stat-now-$i", 760, y + 6, 90, 22, r[1], color = "#9A958D", size = S15, align = "right")
            n.add("stat-next-$i", 860, y + 6, 90, 22, r[2], color = nextColor, size = S15, align = "right")
            n.add("stat-diff-$i", 956, y + 9, 50, 16, r[3], color = nextColor, align = "right")
            n.rect("stat-rule-$i", 614, y + 33, 389, 1, "#12FFFFFF", 2)
        }
        val stateY = statTop + rows.size * 34
        n.add("state-label", 614, stateY + 9, 100, 16, "状態", color = "#A29E97")
        n.add("state-value", 760, stateY + 9, 246, 16, if (g.broken) "破損中（能力が無効）" else "正常",
            color = if (g.broken) "#E58B80" else "#ECE6DC")

        val modTop = statTop + statH + 18
        n.add("mod-title", 600, modTop, 150, 16, "MOD ${g.mods.size} / ${g.modCapacity}", color = "#A29E97")
        n.add("mod-note", 760, modTop, 257, 16, "強化してもMODは変わりません", color = "#6F6B65")
        if (g.mods.isEmpty()) n.add("mod-none", 600, modTop + 26, 417, 16, "ノーマル装備です。刻印工房のオーブでMODを付けられます", color = "#6F6B65")
        g.mods.take(6).forEachIndexed { i, m ->
            val y = modTop + 26 + i * 22
            n.add("mod-kind-$i", 600, y, 44, 16, m.kind, color = "#6F6B65")
            n.add("mod-text-$i", 648, y, 369, 16, m.text, color = "#8FB4F0")
        }

        // +0..+30 track in six bands of five
        n.rect("track-bg", 363, 696, 654, 116, "#0CFFFFFF", 1)
        n.add("track-title", 379, 706, 280, 16, "強化段階 +0 〜 +30", color = "#A29E97")
        n.add("track-warn", 670, 706, 340, 16, "+15以降は失敗時に破損することがあります", color = "#E58B80")
        val bandW = (622.0 - 5 * 12) / 6
        val cellW = (bandW - 4 * 3) / 5
        val notes = listOf("必ず成功", "赤熱", "橙熱", "破損あり", "破損あり", "破損あり")
        for (b in 0 until 6) {
            val bx = 379 + b * (bandW + 12)
            for (k in 1..5) {
                val lv = b * 5 + k
                val color = heat(lv)
                val cx = bx + (k - 1) * (cellW + 3)
                when {
                    lv <= shown -> n.rect("cell-$lv", cx, 734, cellW, 12, color, 2)
                    lv == shown + 1 -> {
                        n.rect("cell-$lv", cx, 734, cellW, 12, "#25272C", 2)
                        n.rect("cell-$lv-t", cx, 734, cellW, 2, color, 3); n.rect("cell-$lv-b", cx, 744, cellW, 2, color, 3)
                        n.rect("cell-$lv-l", cx, 734, 2, 12, color, 3); n.rect("cell-$lv-r", cx + cellW - 2, 734, 2, 12, color, 3)
                    }
                    else -> {
                        n.rect("cell-$lv", cx, 734, cellW, 12, "#25272C", 2)
                        if (lv >= 16) n.rect("cell-$lv-risk", cx, 743, cellW, 3, "#8CC2554A", 3)
                    }
                }
            }
            val inBand = shown + 1 in (b * 5 + 1)..(b * 5 + 5)
            n.add("band-range-$b", bx, 756, bandW, 16, "+${b * 5 + 1}〜${b * 5 + 5}", color = if (inBand) heat(b * 5 + 1) else "#6F6B65")
            n.add("band-note-$b", bx, 778, bandW, 16, notes[b], color = if (b >= 3) "#B86A60" else "#6F6B65")
        }
    }

    private fun right(n: Nodes, state: ForgeV3State, actions: Boolean) {
        val g = state.gear
        val maxed = g.level >= 30
        n.rect("right-panel", 1038, 88, 360, 768, "#DB101114", 0)
        n.add("rate-label", 1056, 104, 120, 18, "成功率", color = "#C9C3B8")
        val rate = if (maxed) 0.0 else state.chance
        n.add("chance-value", 1150, 96, 230, 56, if (maxed) "—" else "${percent(rate)}%", size = S4, align = "right",
            color = when { rate >= 100 -> "#FFF1DE"; rate >= 60 -> "#FFC48A"; rate >= 25 -> "#F28A3C"; else -> "#D9563A" })
        val lit = (rate / 5).roundToInt()
        val segW = (324.0 - 19 * 3) / 20
        for (i in 0 until 20) {
            val color = if (i >= lit) "#2A2C31" else when { i < 5 -> "#8E2B1C"; i < 10 -> "#C23A1E"; i < 15 -> "#F07A2A"; else -> "#FFC48A" }
            n.rect("seg-$i", 1056 + i * (segW + 3), 160, segW, 14, color, 2)
        }
        if (!maxed) {
            val lines = buildList {
                add(Triple("基礎（+${g.level + 1}への強化）", "${percent(state.baseChance)}%", "#C9C3B8"))
                add(Triple("鍛冶熟練 Rank ${state.masteryRank}", "+${state.masteryBonus.roundToInt()}", "#C9C3B8"))
                if (state.focused) add(Triple("集中鍛造", "+${state.catalystBonus.roundToInt()}", "#FFC48A"))
                if (state.pityGuaranteed && g.pityThreshold > 0) add(Triple("天井に到達", "確定", "#FFC48A"))
            }
            lines.forEachIndexed { i, (label, value, color) ->
                n.add("why-label-$i", 1056, 184 + i * 19, 240, 16, label, color = "#A29E97")
                n.add("why-value-$i", 1290, 184 + i * 19, 90, 16, value, color = color, align = "right")
            }
        }

        // Pity and break boxes
        n.rect("pity-bg", 1056, 266, 158, 84, "#09FFFFFF", 1)
        n.add("pity-title", 1064, 274, 146, 16, "天井（連続失敗）", color = "#A29E97")
        if (!maxed && g.pityThreshold > 0) {
            val pipW = (142.0 - (g.pityThreshold - 1) * 4) / g.pityThreshold
            for (i in 0 until g.pityThreshold) n.rect("pity-pip-$i", 1064 + i * (pipW + 4), 298, pipW, 10,
                if (i < g.failures) "#F07A2A" else "#2A2C31", 2)
        }
        val pityText = when {
            maxed -> "最大強化"
            g.pityThreshold == 0 -> "必ず成功"
            g.failures >= g.pityThreshold -> "次は確定成功"
            else -> "あと${g.pityThreshold - g.failures}回で確定"
        }
        n.add("pity-text", 1064, 320, 146, 16, pityText, color = if (g.pityThreshold > 0 && g.failures >= g.pityThreshold) "#FFC48A" else "#ECE6DC")

        val risky = !maxed && state.risky
        n.rect("break-bg", 1222, 266, 158, 84, if (risky) "#1CC2554A" else "#09FFFFFF", 1)
        n.add("break-title", 1230, 274, 146, 16, "失敗時の破損", color = "#A29E97")
        n.add("break-value", 1230, 292, 146, 30, if (risky) "${percent(state.breakOnFailure)}%" else "なし",
            color = if (risky) "#E58B80" else "#ECE6DC", size = S2)
        n.add("break-sub", 1230, 326, 146, 16, when {
            risky -> "毎回 ${percent((100 - state.chance) * state.breakOnFailure / 100)}%"
            state.breakOnFailure > 0 -> "確定成功なので無事"
            else -> "+15から発生"
        }, color = "#A29E97")

        // Costs
        n.add("cost-title", 1056, 362, 220, 16, if (maxed) "必要な素材" else "必要な素材 · T${state.costTier}", color = "#C9C3B8")
        n.add("cost-silver", 1290, 362, 90, 16, "銀貨は不要", color = "#A29E97", align = "right")
        state.costs.take(5).forEachIndexed { i, c ->
            val y = 386.0 + i * 50
            val ok = c.owned >= c.required
            n.rect("cost-bg-$i", 1056, y, 324, 44, if (ok) "#09FFFFFF" else "#16C2554A", 1)
            n.rect("cost-slot-$i", 1062, y + 4, 36, 36, "#0D0E10", 2)
            n.add("cost-icon-$i", 1066, y + 8, 28, 28, sprite = sprites.getValue(c.icon), depth = 3)
            n.add("cost-name-$i", 1106, y + 6, 160, 16, c.name, color = "#ECE6DC", depth = 2)
            n.rect("cost-bar-$i", 1106, y + 30, 140, 4, "#2A2C31", 2)
            val fill = if (!ok) 1.0 else if (c.owned == 0L) 0.0 else c.required.toDouble() / c.owned
            n.rect("cost-fill-$i", 1106, y + 30, 140 * fill.coerceIn(0.0, 1.0), 4, if (ok) "#ECE6DC" else "#C2554A", 3)
            n.add("cost-amount-$i", 1256, y + 2, 118, 22, "${format.format(c.required)} / ${format.format(c.owned)}",
                color = if (ok) "#ECE6DC" else "#E58B80", size = S15, align = "right", depth = 2)
            n.add("cost-after-$i", 1256, y + 26, 118, 16, if (ok) "残り ${format.format(c.owned - c.required)}" else "${format.format(c.required - c.owned)}不足",
                color = if (ok) "#A29E97" else "#E58B80", align = "right", depth = 2)
        }

        // Focused forging (catalyst)
        val focusTop = 642.0
        val focusOn = state.focused
        n.add("catalyst", 1056, focusTop, 324, 52, bg = if (focusOn) "#1AF07A2A" else "#09FFFFFF",
            hover = if (state.focusAvailable) "#22F07A2A" else null,
            action = if (actions && (state.focusAvailable || focusOn)) "catalyst" else null, depth = 1)
        n.add("catalyst-title", 1068, focusTop + 7, 240, 16, "集中鍛造（精錬触媒）", color = if (state.focusAvailable || focusOn) "#ECE6DC" else "#6F6B65", depth = 2)
        n.add("catalyst-sub", 1068, focusTop + 29, 240, 16, when {
            maxed -> "最大強化です"
            g.broken -> "修理してから使えます"
            !state.focusAvailable && !focusOn -> "確定成功なので不要です"
            else -> "成功率+15 · 石材1 布1 刻印粉2"
        }, color = if (state.focusAvailable || focusOn) "#FFC48A" else "#6F6B65", depth = 2)
        n.rect("switch-track", 1324, focusTop + 15, 44, 22, if (focusOn) "#F07A2A" else "#3A3C42", 2)
        n.rect("switch-knob", if (focusOn) 1348.0 else 1328.0, focusTop + 18, 16, 16, "#ECE6DC", 3)

        if (g.broken) n.add("repair-note", 1056, 706, 324, 16, "装備庫で修理すると強化値もMODも元どおり", color = "#E58B80")

        val ready = !state.busy && state.blockedReason == null
        val label = when {
            state.busy -> "鍛造中…"
            ready -> "強化する"
            else -> state.blockedReason ?: "強化できません"
        }
        n.add("enhance", 1056, 742, 324, 60, label, color = if (ready) "#17181B" else "#8A867F",
            size = if (ready || state.busy) S15 else S1,
            bg = if (ready) "#EFE6D2" else "#24262B", hover = if (ready) "#FFF6E4" else null,
            action = if (ready && actions) "enhance" else null, align = "center", depth = 2)
        if (ready) n.rect("enhance-lip", 1056, 796, 324, 6, if (state.risky) "#C2554A" else "#C9BDA3", 3)
        val hint = when {
            state.busy -> "装備に熱を込めています"
            g.broken -> "破損中は強化できません"
            maxed -> "これ以上は強化できません"
            state.blockedReason != null -> "精錬か市場で素材を用意できます"
            state.risky -> "失敗すると ${percent(state.breakOnFailure)}% で破損します"
            else -> "失敗しても強化値は下がりません"
        }
        n.add("enhance-hint", 1056, 812, 324, 16, hint, color = if (state.risky && ready) "#E58B80" else "#A29E97", align = "center")
    }

    private fun modal(n: Nodes, state: ForgeV3State) {
        val g = state.gear
        n.rect("modal-mask", 0, 0, 1440, 920, "#BB0D1418", 20)
        n.rect("modal-border", 466, 226, 508, 388, "#C2554A", 21)
        n.rect("modal-panel", 470, 230, 500, 380, "#1C1F22", 22)
        n.add("modal-title", 500, 254, 440, 30, "破損の危険があります", color = "#E58B80", size = S2, depth = 23)
        n.add("modal-gear", 500, 300, 440, 22, "${g.name}  +${g.level} → +${g.level + 1}", color = heat(g.level + 1), size = S15, depth = 23)
        listOf(
            "成功率 ${percent(state.chance)}%",
            "失敗すると ${percent(state.breakOnFailure)}% の確率で破損",
            "1回あたりの破損 ${percent((100 - state.chance) * state.breakOnFailure / 100)}%",
            "破損しても強化値とMODは残ります",
            "修理：同Tier・同部位の+0装備を1個",
            "素材は成功・失敗にかかわらず消費します",
        ).forEachIndexed { i, line ->
            n.add("modal-line-$i", 500, 344 + i * 30, 440, 18, line, color = if (i < 3) "#ECE6DC" else "#A29E97", depth = 23)
        }
        n.add("modal-cancel", 500, 540, 210, 50, "やめる", color = "#ECE6DC", size = S15, bg = "#2A2D31", hover = "#363A3F",
            action = "cancel", align = "center", depth = 24)
        n.add("modal-confirm", 730, 540, 210, 50, "強化する", color = "#17181B", size = S15, bg = "#EFE6D2", hover = "#FFF6E4",
            action = "confirm", align = "center", depth = 24)
    }
}
