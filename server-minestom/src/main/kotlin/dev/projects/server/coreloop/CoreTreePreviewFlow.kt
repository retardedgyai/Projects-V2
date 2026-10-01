package dev.projects.server.coreloop

import com.google.gson.JsonParser
import dev.projects.webui.*
import dev.projects.webui.polish05.Polish05ScreenSpace
import java.util.Locale

/** Same native UiScene for the vanilla display and the local inspection surface. */
internal class CoreTreePreviewFlow(val model: CoreTreePreview, spriteJson: String) : ForgeUiFlow {
    override val muted = true
    override val view = "tree-preview"
    override val operationActive = false
    private var skillPage = 0
    private val space = Polish05ScreenSpace(800.0, 480.0)
    private val sprites = JsonParser.parseString(spriteJson).asJsonObject.entrySet().associate { (name, value) ->
        val v = value.asJsonObject
        name to UiSprite(v["char"].asString, v["font"].asString, v["width"].asInt, v["height"].asInt)
    }
    fun snapshot() = model.snapshot()

    override fun action(action: String): Boolean = when {
        action.startsWith("node:") -> model.select(action.removePrefix("node:")).also { if (it) skillPage = 0 }
        action == "trial:toggle" -> model.toggle().also { if (it) skillPage = 0 }
        action == "trial:reset" -> { model.reset(); skillPage = 0; true }
        action == "skill:next" -> { skillPage = (skillPage + 1).coerceAtMost((snapshot().skills.size - 1).coerceAtLeast(0)); true }
        action == "skill:prev" -> { skillPage = (skillPage - 1).coerceAtLeast(0); true }
        else -> false
    }

    override fun scene(light: ForgeLightPhase): UiScene {
        val s = snapshot()
        val out = mutableListOf<UiNode>()
        fun put(id: String, x: Double, y: Double, w: Double, h: Double, text: String = "",
                color: String = "#dbd8c6", size: Double = 17.0, bg: String? = null,
                action: String? = null, item: String? = null, enabled: Boolean = true,
                family: String = "sans", align: String = "left", depth: Int = 7, sprite: UiSprite? = null) {
            val p = space.forward(x, y)
            val style = mutableMapOf("color" to color, "font-size" to "${size * space.scale}px",
                "font-family" to "projects_ui_polish05:$family", "text-align" to if (action != null && text.isNotEmpty()) "center" else align)
            if (bg != null) style["background-color"] = bg
            if (action != null) style["hover-background-color"] = if (bg == "#d4b879") "#ecd39a" else "#3e3c2e"
            out += UiNode(id, Box(p.x, p.y, w * space.scale, h * space.scale), text, style, action, item, enabled, depth, sprite)
        }
        fun text(id: String, x: Double, y: Double, w: Double, value: String, size: Double = 17.0, color: String = "#dbd8c6", family: String = "sans", align: String = "left") =
            put(id, x, y, w, size + 8, value, color, size, family = family, align = align)
        fun line(id: String, x: Double, y: Double, w: Double, h: Double, color: String = "#514c37") =
            put(id, x, y, w.coerceAtLeast(2.0), h.coerceAtLeast(2.0), bg = color, depth = 4)
        fun edge(id: String, x1: Double, y1: Double, x2: Double, y2: Double, learned: Boolean) {
            val mid = (y1 + y2) / 2
            val color = if (learned) "#ac9054" else "#3b3f39"
            line("$id:a", x1 - 1, y1, 2.0, mid - y1, color)
            if (x1 != x2) line("$id:b", minOf(x1, x2), mid, kotlin.math.abs(x2 - x1), 2.0, color)
            line("$id:c", x2 - 1, mid, 2.0, y2 - mid, color)
        }
        fun wrap(value: String, limit: Int) = value.chunked(limit)
        fun number(n: Double) = String.format(Locale.ROOT, "%.2f", n).trimEnd('0').trimEnd('.')

        put("backdrop", 0.0, 0.0, 1440.0, 920.0, bg = "#101619", depth = 0)
        put("panel", 76.0, 148.0, 1288.0, 676.0, bg = "#141a1a", depth = 1)
        sprites.filterKeys { it.startsWith("window_chrome/") }.forEach { (key, sprite) ->
            val (x, y) = key.substringAfter('/').split('_').map(String::toDouble)
            put("plate-$key", 66 + x, 140 + y, sprite.width.toDouble(), sprite.height.toDouble(), depth = 2, sprite = sprite)
        }
        text("brand", 76.0, 25.0, 420.0, "ProjectS  /  育成", 20.0, "#cdbb8a", "serif")
        text("eyebrow", 480.0, 62.0, 480.0, "THE PATH OF GROWTH", 11.0, "#b4a77f", align = "center")
        text("heading", 360.0, 88.0, 720.0, "成長樹  /  ${s.job}", 32.0, "#e4d4a5", "serif", "center")
        text("tree-title", 96.0, 167.0, 470.0, "戦い方を選ぶ", 21.0, "#d6c38f", "serif")
        text("points", 650.0, 170.0, 275.0, "残り ${s.budget - s.spent} / ${s.budget} ポイント", 18.0, "#d8be7b", align = "right")
        text("level", 96.0, 197.0, 770.0, "Lv ${s.level}  ・  金: 習得済み  /  緑: 取得可  /  灰: 前提未達", 12.0, "#929c91")
        line("separator", 954.0, 222.0, 2.0, 506.0)
        put("root", 473.0, 232.0, 54.0, 54.0, item = "minecraft:book|projects:core_ui/${s.nodes.first { it.kind == "keystone" }.icon}")
        text("root-label", 535.0, 244.0, 250.0, "${s.job}の起点", 16.0, "#c7b57d")
        val byId = s.nodes.associateBy { it.id }
        s.nodes.forEach { node ->
            if (node.parents.isEmpty()) edge("edge-root-${node.id}", 500.0, 282.0, node.x, node.y - 22, node.learned)
            else node.parents.forEach { parentId ->
                val parent = byId.getValue(parentId)
                edge("edge-$parentId-${node.id}", parent.x, parent.y + 23, node.x, node.y - 23, parent.learned && node.learned)
            }
        }
        s.nodes.forEach { node ->
            val selected = node.id == s.selected
            val color = when { selected -> "#e1ca88"; node.learned -> "#aa8f53"; node.available -> "#6b7966"; else -> "#3a423f" }
            val size = if (node.kind == "keystone") 54.0 else 46.0
            put("frame-${node.id}", node.x - size / 2, node.y - size / 2, size, size, bg = color)
            put("node-${node.id}", node.x - size / 2 + 3, node.y - size / 2 + 3, size - 6, size - 6,
                bg = if (node.learned) "#393a2e" else "#1c2322", action = "node:${node.id}",
                item = "minecraft:book|projects:core_ui/${node.icon}")
            text("label-${node.id}", node.x - 80, node.y + size / 2 + 7, 160.0, node.name,
                15.0, if (node.learned || selected) "#d7c38d" else "#929d93", align = "center")
        }
        val chosen = byId.getValue(s.selected)
        text("detail-kind", 984.0, 233.0, 320.0, if (chosen.kind == "keystone") "大成技能 / 現行データ" else "選んだパッシブ", 13.0, "#a6a98f")
        text("detail-title", 984.0, 257.0, 342.0, chosen.name, 28.0, "#e3d19d", "serif")
        wrap(chosen.description, 20).forEachIndexed { i, value -> text("description-$i", 984.0, 300.0 + i * 24, 345.0, value, 16.0) }
        val prior = if (chosen.parents.isEmpty()) "なし" else chosen.parents.joinToString(" / ") { byId.getValue(it).name }
        text("prerequisite", 984.0, 365.0, 345.0, "前提: $prior", 13.0, "#a3aa9c")
        wrap(s.reason, 23).forEachIndexed { i, value -> text("reason-$i", 984.0, 391.0 + i * 20, 345.0, value, 14.0, "#bdb388") }
        line("detail-rule", 984.0, 440.0, 344.0, 2.0)
        text("comparison-title", 984.0, 453.0, 340.0, "現在 → 試し振り後", 19.0, "#d2c28f", "serif")
        val skill = s.skills.getOrNull(skillPage.coerceIn(0, (s.skills.size - 1).coerceAtLeast(0)))
        if (skill != null) {
            put("comparison-icon", 984.0, 488.0, 28.0, 28.0, item = "minecraft:book|projects:core_ui/${skill.icon}")
            text("skill-name", 1020.0, 490.0, 310.0, "${skill.name}${if (skill.equipped) " / 編成中" else " / 候補"}", 16.0)
            skill.metrics.take(5).forEachIndexed { i, m ->
                text("metric-label-$i", 984.0, 530.0 + i * 27, 134.0, m.label, 14.0, "#a2ab9e")
                text("metric-values-$i", 1120.0, 529.0 + i * 27, 208.0,
                    "${number(m.current)} → ${number(m.after)}${m.unit}", 17.0, "#d7bd7b", align = "right")
            }
            if (s.skills.size > 1) {
                put("skill-prev", 984.0, 674.0, 72.0, 29.0, "前へ", "#bcbca6", 13.0, "#252d29", "skill:prev")
                text("skill-page", 1062.0, 676.0, 170.0, "${skillPage.coerceAtMost(s.skills.lastIndex) + 1} / ${s.skills.size} 技能", 13.0, align = "center")
                put("skill-next", 1256.0, 674.0, 72.0, 29.0, "次へ", "#bcbca6", 13.0, "#252d29", "skill:next")
            }
        } else {
            val metric = if (s.health.current != s.health.after) s.health else s.damage
            if (metric.current != metric.after) {
                text("character-metric", 984.0, 503.0, 340.0, "${metric.label}  ${number(metric.current)} → ${number(metric.after)}${metric.unit}", 18.0, "#d7bd7b")
            } else {
                text("conditional", 984.0, 503.0, 340.0, "条件付きの効果は説明で確認", 16.0)
                text("conditional-help", 984.0, 535.0, 340.0, "この基準値は変わりません", 14.0, "#9ba597")
            }
        }
        text("limits", 984.0, 714.0, 346.0, "敵防御・会心・条件付き効果は別計算", 12.0, "#909b8d")
        put("trial-toggle", 984.0, 752.0, 344.0, 47.0, if (chosen.learned) "試し返還する" else "1ポイントを試し振り", "#2c281b", 19.0,
            "#d4b879", "trial:toggle", enabled = s.canToggle, family = "serif")
        put("reset-trial", 96.0, 755.0, 175.0, 42.0, "元の構成へ戻す", "#c0baa0", 15.0, "#252d29", "trial:reset")
        text("trial-state", 290.0, 766.0, 530.0, if (s.changed) "試し振り中  /  閉じると元の構成に戻ります" else "選択して効果を比較できます", 14.0, "#a7ae9d")
        put("close", 1320.0, 166.0, 32.0, 32.0, "×", "#c5bea5", 22.0, "#252d29", "close")
        text("footer", 76.0, 863.0, 1240.0, "育成プレビュー  /  現行の職別ツリーを比較  /  共通ツリーの完成版は制作中", 13.0, "#a1a68f")
        return UiScene(space.width, space.height, out)
    }
}
