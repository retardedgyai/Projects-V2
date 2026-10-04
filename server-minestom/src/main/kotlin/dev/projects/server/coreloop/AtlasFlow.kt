package dev.projects.server.coreloop

import dev.projects.server.coreloop.AtlasDiorama.Region
import dev.projects.server.questmap.QuestTerrainStyle
import dev.projects.webui.Box
import dev.projects.webui.ForgeLightPhase
import dev.projects.webui.ForgeUiFlow
import dev.projects.webui.UiNode
import dev.projects.webui.UiScene
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.BlockDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.instance.block.Block

/**
 * The map table: pick a region on the 開拓図 diorama, choose enemy level and tablets, depart.
 * The UI plane carries the panel and invisible hit areas over each islet; labels, fog and the
 * selection beam live in the world next to the diorama and are visible to this player only.
 */
internal class AtlasFlow(
    private val player: Player,
    private val account: () -> CoreAccount?,
    private val depart: (Region, Int, Int) -> Unit,
) : ForgeUiFlow {
    override val view = "atlas"
    override val muted = false
    override val operationActive = false

    private var selected: Region
    private var level: Int
    private var tablets = 0
    private var hovered: Region? = null
    private val labels = mutableMapOf<Region, Entity>()
    private val world = mutableListOf<Entity>()
    private var beam: Entity? = null

    init {
        val a = account()
        val tier = (a?.unlockedMapTier ?: 1).coerceIn(1, 4)
        selected = Region(tier, QuestTerrainStyle.VERDANT)
        level = defaultLevel(a, tier)
        spawnWorld(a)
    }

    private fun revealed(a: CoreAccount?, r: Region) = r.tier <= (a?.unlockedMapTier ?: 1)

    private fun levelRange(a: CoreAccount?, tier: Int): IntRange {
        val floor = CoreJourneyRules.floor(tier); val ceiling = CoreJourneyRules.ceiling(tier)
        val cap = if (a?.journey?.legacy == true) ceiling else maxOf(floor, (a?.journey?.level ?: 1) + 2).coerceAtMost(ceiling)
        return floor..cap
    }

    private fun defaultLevel(a: CoreAccount?, tier: Int) = (a?.journey?.level ?: 1).coerceIn(levelRange(a, tier))

    // ------------------------------------------------------------------ actions

    override fun action(action: String): Boolean {
        val a = account()
        when {
            action.startsWith("region:") -> {
                val r = parse(action.removePrefix("region:")) ?: return false
                if (r == selected) return false
                selected = r
                level = defaultLevel(a, r.tier)
                tablets = tablets.coerceAtMost(maxTablets(a))
                moveBeam()
            }
            action == "lv:-" -> { if (level <= levelRange(a, selected.tier).first) return false; level-- }
            action == "lv:+" -> { if (level >= levelRange(a, selected.tier).last) return false; level++ }
            action == "tab:-" -> { if (tablets <= 0) return false; tablets-- }
            action == "tab:+" -> { if (tablets >= maxTablets(a)) return false; tablets++ }
            action == "depart" -> {
                if (blocker(a) != null) return false
                depart(selected, level, tablets)
            }
            else -> return false
        }
        return true
    }

    private fun maxTablets(a: CoreAccount?) = minOf(3L, a?.amount(CoreResource.GATHERING_TABLET) ?: 0L).toInt()

    private fun parse(id: String): Region? {
        val (t, s) = id.removePrefix("T").split('-', limit = 2).takeIf { it.size == 2 } ?: return null
        val style = QuestTerrainStyle.entries.firstOrNull { it.name == s } ?: return null
        return Region(t.toIntOrNull()?.takeIf { it in 1..4 } ?: return null, style)
    }

    private fun blocker(a: CoreAccount?): String? = when {
        a == null -> "読込中"
        !revealed(a, selected) -> "まだ霧の中"
        selected.tier > 1 && a.amount(CoreResource.COMBAT_TOKEN, selected.tier - 1) < 1 -> "戦利品券が足りません"
        a.maps.size >= CoreLoopCatalog.MAX_MAPS -> "地図の保管庫が満杯です"
        else -> null
    }

    override fun hover(id: String?) {
        val next = id?.takeIf { it.startsWith("region:") }?.let { parse(it.removePrefix("region:")) }
        if (next == hovered) return
        hovered?.let { paintLabel(it, false) }
        hovered = next
        next?.let { paintLabel(it, true) }
    }

    // ------------------------------------------------------------------ world markers

    private fun display(type: EntityType, at: Vec, edit: (Entity) -> Unit): Entity = Entity(type).apply {
        setHasPhysics(false); setNoGravity(true); setAutoViewable(false)
        (entityMeta as AbstractDisplayMeta).setViewRange(6f)
        edit(this)
        setInstance(player.instance!!, at.asPos()).thenRun { if (!isRemoved) addViewer(player) }
        world += this
    }

    private fun spawnWorld(a: CoreAccount?) {
        if (player.instance == null) return
        for (islet in AtlasDiorama.islets) {
            val r = islet.region ?: continue
            if (revealed(a, r)) {
                labels[r] = display(EntityType.TEXT_DISPLAY, islet.anchor.add(0.0, 5.5 + r.tier * .5, 0.0)) { e ->
                    (e.entityMeta as TextDisplayMeta).apply {
                        setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
                        setScale(Vec(5.5, 5.5, 5.5)); setShadow(true); setLineWidth(400)
                        setUseDefaultBackground(false)
                    }
                }
                paintLabel(r, false)
            } else fog(islet)
        }
        moveBeam()
    }

    private fun paintLabel(r: Region, hot: Boolean) {
        val e = labels[r] ?: return
        val chosen = r == selected
        (e.entityMeta as TextDisplayMeta).apply {
            setText(Component.text("${STYLE_NAME[r.style]} T${r.tier}", TextColor.color(if (chosen) 0xFFD86A else if (hot) 0xFFFFFF else 0xD8E8E4)))
            setBackgroundColor(if (chosen) 0xD0302410.toInt() else if (hot) 0xC0204A54.toInt() else 0x90101A1E.toInt())
        }
    }

    private fun fog(islet: AtlasDiorama.Islet) {
        val seed = islet.region!!.id.hashCode()
        val puffs = 3 + islet.region.tier / 2
        for (i in 0 until puffs) {
            val a = Math.toRadians(i * 360.0 / puffs + seed % 60)
            val d = if (i == 0) 0.0 else islet.radius * .55
            val sx = islet.radius * (1.1 + (seed shr i and 3) * .12); val sz = sx * .9; val sy = 1.5 + (i % 3) * .6
            val cx = islet.x + Math.cos(a) * d; val cz = islet.z + Math.sin(a) * d
            val cy = islet.top + 3.0 + (i % 2) * 1.2
            display(EntityType.BLOCK_DISPLAY, Vec(cx, cy, cz)) { e ->
                (e.entityMeta as BlockDisplayMeta).apply {
                    setBlockState(Block.WHITE_CONCRETE_POWDER)
                    setScale(Vec(sx, sy, sz)); setTranslation(Vec(-sx / 2, 0.0, -sz / 2))
                    setBrightness(15, 15)
                }
            }
        }
    }

    private fun moveBeam() {
        beam?.let { it.remove(); world -= it }
        labels.keys.forEach { paintLabel(it, it == hovered) }
        val islet = AtlasDiorama.islet(selected)
        beam = display(EntityType.BLOCK_DISPLAY, islet.anchor.add(0.0, 1.0, 0.0)) { e ->
            (e.entityMeta as BlockDisplayMeta).apply {
                setBlockState(Block.YELLOW_STAINED_GLASS)
                setScale(Vec(.6, 14.0, .6)); setTranslation(Vec(-.3, 0.0, -.3))
                setBrightness(15, 15)
            }
        }
    }

    override fun dispose() {
        world.forEach(Entity::remove); world.clear(); labels.clear(); beam = null
    }

    // ------------------------------------------------------------------ UI plane

    private val nodes = mutableListOf<UiNode>()
    private fun rect(id: String, x: Double, y: Double, w: Double, h: Double, color: String, depth: Int, action: String? = null) {
        nodes += UiNode(id, Box(x, y, w, h), "", mapOf("background-color" to color), action, null, true, depth)
    }
    private fun text(id: String, x: Double, y: Double, w: Double, h: Double, value: String, size: Int, color: String,
                     depth: Int, align: String = "left", action: String? = null, enabled: Boolean = true) {
        nodes += UiNode(id, Box(x, y, w, h), value, mapOf("font-size" to "${size}px", "color" to color, "text-align" to align, "text-shadow" to "true"),
            action, null, enabled, depth)
    }
    /** Dungeons-style slab: dark rim, light border, fill; corners stepped by one 2px notch. */
    private fun slab(id: String, x: Double, y: Double, w: Double, h: Double, border: String, fill: String, depth: Int, action: String? = null) {
        rect("$id:o1", x + 2, y, w - 4, h, RIM, depth); rect("$id:o2", x, y + 2, w, h - 4, RIM, depth)
        rect("$id:b1", x + 3, y + 1, w - 6, h - 2, border, depth + 1); rect("$id:b2", x + 1, y + 3, w - 2, h - 6, border, depth + 1)
        nodes += UiNode("$id:f", Box(x + 3, y + 3, w - 6, h - 6), "",
            mapOf("background-color" to fill, "hover-background-color" to (if (action == "depart") "#fffcc060" else "#ff2e6270")), action, null, true, depth + 2)
    }

    override fun scene(light: ForgeLightPhase): UiScene {
        nodes.clear()
        val a = account()
        // Invisible hit areas over each islet.
        for (islet in AtlasDiorama.islets) {
            val r = islet.region ?: continue
            val (sx, sy) = AtlasDiorama.project(islet.anchor, ZOOM) ?: continue
            val pr = AtlasDiorama.projectedRadius(islet, ZOOM) * .85
            nodes += UiNode("region:${r.id}", Box(sx - pr, sy - pr * .75, pr * 2, pr * 1.5), "", emptyMap(), "region:${r.id}", null, true, 0)
        }
        // Title.
        text("title", 18.0, 14.0, 200.0, 24.0, "開拓図", 22, GOLD, 4)
        text("subtitle", 20.0, 42.0, 260.0, 12.0, "行き先の地域を選んで出発する", 9, SUB, 4)

        // Right panel.
        val px = 566.0; val py = 58.0; val pw = 218.0; val ph = 372.0
        slab("panel", px, py, pw, ph, BORDER, FILL, 2)
        rect("panel:head", px + 3, py + 3, pw - 6, 34.0, HEAD, 5)
        val open = revealed(a, selected)
        text("name", px + 12, py + 12, 150.0, 18.0, STYLE_NAME.getValue(selected.style), 16, TEXT, 6)
        slab("tier", px + pw - 52, py + 10, 40.0, 20.0, "#ff6e4a20", "#ffb0682a", 6)
        text("tier:t", px + pw - 52, py + 12, 40.0, 16.0, "T${selected.tier}", 12, "#fff1d6", 9, "center")
        text("flavor", px + 12, py + 46, pw - 24, 12.0, FLAVOR.getValue(selected.style), 9, SUB, 6)

        var y = py + 70
        fun row(key: String, label: String, value: String, valueColor: String = TEXT) {
            rect("row:$key", px + 8, y, pw - 16, 22.0, ROW, 5)
            text("row:$key:l", px + 14, y + 6, 60.0, 12.0, label, 10, SUB, 6)
            text("row:$key:v", px + 70, y + 6, pw - 84, 12.0, value, 10, valueColor, 6)
            y += 26
        }
        fun stepper(key: String, label: String, value: String, minus: String, plus: String, note: String) {
            rect("row:$key", px + 8, y, pw - 16, 26.0, ROW, 5)
            text("row:$key:l", px + 14, y + 8, 50.0, 12.0, label, 10, SUB, 6)
            slab("$key:-", px + 64, y + 3, 20.0, 20.0, BORDER, BUTTON, 6, minus)
            text("$key:-:t", px + 64, y + 7, 20.0, 12.0, "-", 12, TEXT, 9, "center")
            text("$key:v", px + 86, y + 7, 40.0, 14.0, value, 12, GOLD, 6, "center")
            slab("$key:+", px + 128, y + 3, 20.0, 20.0, BORDER, BUTTON, 6, plus)
            text("$key:+:t", px + 128, y + 7, 20.0, 12.0, "+", 12, TEXT, 9, "center")
            text("$key:n", px + 152, y + 9, 54.0, 10.0, note, 8, SUB, 6)
            y += 30
        }
        if (open && a != null) {
            row("yield", "特産", YIELD.getValue(selected.style))
            row("boss", "ボス", "道の先に待つ")
            val range = levelRange(a, selected.tier)
            stepper("lv", "敵Lv", "$level", "lv:-", "lv:+", "${range.first}〜${range.last}")
            stepper("tab", "石板", "$tablets / 3", "tab:-", "tab:+", "所持 ${a.amount(CoreResource.GATHERING_TABLET)}")
            val cost = if (selected.tier == 1) "無料" else "T${selected.tier - 1} 戦利品券 1（所持 ${a.amount(CoreResource.COMBAT_TOKEN, selected.tier - 1)}）"
            row("cost", "費用", cost)
            row("drop", "報酬", "討伐証・戦利品券・石板")
        } else {
            rect("fog", px + 8, y, pw - 16, 96.0, ROW, 5)
            text("fog:t", px + 8, y + 18, pw - 16, 16.0, "まだ霧の中", 14, "#c9d6d8", 6, "center")
            text("fog:d", px + 8, y + 46, pw - 16, 12.0, "T${selected.tier - 1} の上限Lvでボスを倒すと", 9, SUB, 6, "center")
            text("fog:d2", px + 8, y + 62, pw - 16, 12.0, "この輪の霧が晴れる", 9, SUB, 6, "center")
        }
        val reason = blocker(a)
        val by = py + ph - 52
        if (reason == null) {
            slab("go", px + 10, by, pw - 20, 40.0, "#fffff0b0", "#fff0a030", 6, "depart")
            text("go:t", px + 10, by + 12, pw - 20, 16.0, "出発する", 16, "#2a1606", 9, "center")
        } else {
            slab("go", px + 10, by, pw - 20, 40.0, "#ff3a4a50", "#ff222c30", 6)
            text("go:t", px + 10, by + 14, pw - 20, 12.0, reason, 11, "#7f8c90", 9, "center")
        }
        // Close button.
        slab("close", px + pw - 30, py - 26, 30.0, 22.0, BORDER, BUTTON, 6, "close")
        text("close:t", px + pw - 30, py - 22, 30.0, 12.0, "×", 12, TEXT, 9, "center")
        // Legend.
        text("hint", 20.0, 456.0, 400.0, 10.0, "島をクリックで選択    ×で閉じる", 8, SUB, 4)
        return UiScene(800.0, 480.0, nodes.toList())
    }

    companion object {
        const val ZOOM = 0.8
        private const val RIM = "#ff081418"
        private const val BORDER = "#ff3f7884"
        private const val FILL = "#ee15303a"
        private const val HEAD = "#ff24505c"
        private const val ROW = "#cc0e2228"
        private const val BUTTON = "#ff1e3e48"
        private const val TEXT = "#eef6f4"
        private const val SUB = "#9fbcc0"
        private const val GOLD = "#ffd257"
        val STYLE_NAME = mapOf(
            QuestTerrainStyle.VERDANT to "緑野", QuestTerrainStyle.HIGHLANDS to "高地", QuestTerrainStyle.SALTMARSH to "塩沼",
            QuestTerrainStyle.CLIFFLANDS to "断崖", QuestTerrainStyle.SAKURA_GROVE to "桜の森", QuestTerrainStyle.INFERNAL to "獄炎の地",
        )
        private val FLAVOR = mapOf(
            QuestTerrainStyle.VERDANT to "なだらかな草原と森が続く", QuestTerrainStyle.HIGHLANDS to "雪をいただく岩の高地",
            QuestTerrainStyle.SALTMARSH to "潮の満ちる湿地とマングローブ", QuestTerrainStyle.CLIFFLANDS to "白い岩壁が段をなす",
            QuestTerrainStyle.SAKURA_GROVE to "花びらの舞う丘", QuestTerrainStyle.INFERNAL to "溶岩の流れる黒い大地",
        )
        private val YIELD = mapOf(
            QuestTerrainStyle.VERDANT to "伐採・植物採取", QuestTerrainStyle.HIGHLANDS to "採石・採鉱", QuestTerrainStyle.SALTMARSH to "皮剥ぎ・植物採取",
            QuestTerrainStyle.CLIFFLANDS to "採石", QuestTerrainStyle.SAKURA_GROVE to "植物採取・伐採", QuestTerrainStyle.INFERNAL to "採鉱",
        )
    }
}
