package dev.projects.server.coreloop

import dev.projects.server.coreloop.ui.CoreMenuInventory
import dev.projects.server.coreloop.ui.CoreMenuCanvas
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

internal class FirstMagicService(directory: Path, private val io: Executor) {
    private val repository = FirstMagicRepository(directory)
    private val states = ConcurrentHashMap<UUID, FirstMagicState>()

    fun load(playerId: UUID) { states[playerId] = repository.load(playerId) }
    fun snapshot(playerId: UUID): FirstMagicState? = states[playerId]
    fun change(playerId: UUID, action: FirstMagicAction): CompletableFuture<FirstMagicChange> = CompletableFuture.supplyAsync({
        val before = requireNotNull(states[playerId]) { "First-magic state is not loaded" }
        val result = FirstMagicRules.apply(before, action)
        if (result.changed) {
            repository.save(playerId, result.state)
            states[playerId] = result.state
        }
        result
    }, io)
    fun forget(playerId: UUID) { io.execute { states.remove(playerId) } }
}

/** Clear native slots with a book-and-brass palette; usable with or without the optional resource pack. */
internal class FirstMagicWorkshop(
    private val state: (Player) -> FirstMagicState?,
    private val change: (Player, FirstMagicAction, () -> Unit) -> Unit,
    private val inColony: (Player) -> Boolean,
    private val exit: (Player) -> Unit,
    private val packed: (Player) -> Boolean,
    private val jarPlaced: (Player, FirstAspect) -> Boolean,
    private val jarOnShelf: (Player, FirstAspect) -> Boolean,
    private val takeShelfJar: (Player, FirstAspect) -> Boolean,
    private val stationPlaced: (Player, ColonyPlaceable) -> Boolean,
) {
    private val screens = CoreMenuInventory()
    private val brewing = ConcurrentHashMap<UUID, Pair<AnomalousMaterial, Long>>()
    private val materialSlots = listOf(10, 12, 14, 28, 30, 32)
    private val aspectSlots = listOf(37, 38, 42, 43)

    fun click(event: InventoryPreClickEvent) = screens.click(event)
    fun forget(playerId: UUID) { screens.forget(playerId); brewing.remove(playerId) }
    fun cancelBrew(playerId: UUID) { brewing.remove(playerId) }

    private fun materialIcon(material: AnomalousMaterial) = when (material) {
        AnomalousMaterial.MOONBELL -> Material.BLUE_ORCHID
        AnomalousMaterial.EMBER_MOSS -> Material.RED_MUSHROOM
        AnomalousMaterial.HOLLOW_CRYSTAL -> Material.AMETHYST_SHARD
        AnomalousMaterial.WARM_ORE -> Material.RAW_COPPER
        AnomalousMaterial.TIDEWING_FEATHER -> Material.FEATHER
        AnomalousMaterial.WITHERED_CORE -> Material.ECHO_SHARD
    }
    private fun aspectIcon(aspect: FirstAspect) = when (aspect) {
        FirstAspect.EMBER -> Material.BLAZE_POWDER
        FirstAspect.TIDE -> Material.PRISMARINE_CRYSTALS
        FirstAspect.GALE -> Material.FEATHER
        FirstAspect.STONE -> Material.AMETHYST_SHARD
    }
    private fun materialArt(material: AnomalousMaterial) = when (material) {
        AnomalousMaterial.MOONBELL -> "moonbell"
        AnomalousMaterial.EMBER_MOSS -> "ember_moss"
        AnomalousMaterial.HOLLOW_CRYSTAL -> "hollow_crystal"
        AnomalousMaterial.WARM_ORE -> "warm_ore"
        AnomalousMaterial.TIDEWING_FEATHER -> "tidewing_feather"
        AnomalousMaterial.WITHERED_CORE -> "withered_core"
    }
    private fun magicIcon(player: Player, art: String, fallback: Material, name: String, vararg lore: String): ItemStack {
        val item = CoreLoopItems.icon(fallback, name, *lore)
        return if (packed(player)) item.withItemModel("projects:first_magic/icon_$art") else item
    }
    private fun jarIcon(player: Player, aspect: FirstAspect, amount: Int): ItemStack {
        val item = CoreLoopItems.icon(Material.GLASS_BOTTLE, aspect.label,
            "${"▰".repeat(amount / 2)}${"▱".repeat((FirstMagicRules.JAR_CAPACITY - amount) / 2)}",
            "$amount / ${FirstMagicRules.JAR_CAPACITY} Essentia")
        val fill = if (amount == 0) "empty" else if (amount < 8) "low" else "high"
        return if (packed(player)) item.withItemModel("projects:first_magic/jar_${aspect.name.lowercase()}_$fill") else item
    }
    private fun frame(player: Player): Array<ItemStack> = if (packed(player)) Array(54) { ItemStack.AIR }
        else Array(54) { index ->
            CoreLoopItems.icon(if (index < 9 || index >= 45 || index % 9 == 0 || index % 9 == 8)
                Material.BROWN_STAINED_GLASS_PANE else Material.GRAY_STAINED_GLASS_PANE, " ")
        }
    private enum class Page { DESK, DISTILLER, JARS, JOURNAL, RESEARCH, COMBINE }
    private fun show(player: Player, page: Page, title: String, progress: FirstMagicState,
                     items: Array<ItemStack>, actions: Map<Int, () -> Unit>, redraw: () -> Unit) {
        if (!inColony(player)) return
        items[53] = magicIcon(player, "return", Material.COMPASS, "港へ帰還")
        val heading = if (packed(player)) {
            val canvas = CoreMenuCanvas(title, when (page) {
                Page.DESK -> CoreMenuCanvas.Background.FIRST_MAGIC_DESK
                Page.DISTILLER -> CoreMenuCanvas.Background.FIRST_MAGIC_DISTILLER
                Page.JARS -> CoreMenuCanvas.Background.FIRST_MAGIC_JARS
                Page.JOURNAL -> CoreMenuCanvas.Background.FIRST_MAGIC_JOURNAL
                Page.RESEARCH -> CoreMenuCanvas.Background.FIRST_MAGIC_JOURNAL
                Page.COMBINE -> CoreMenuCanvas.Background.FIRST_MAGIC_DESK
            })
            val known = progress.knownAspects.size
            val collected = AnomalousMaterial.entries.count { progress.count(it) > 0 }
            when (page) {
                Page.DESK -> {
                    canvas.left("観測手順", listOf(
                        CoreMenuCanvas.Line("01 素材を持ち帰る"),
                        CoreMenuCanvas.Line("02 観測盤を修復"),
                        CoreMenuCanvas.Line("03 素材を分析"),
                        CoreMenuCanvas.Line("04 記録帳へ残す"),
                        CoreMenuCanvas.Line(""),
                        CoreMenuCanvas.Line("素材 $collected / 6"),
                        CoreMenuCanvas.Line("性質 $known / 4"),
                    ))
                    canvas.right("古い観測工房", listOf(
                        CoreMenuCanvas.Line(if (progress.deskRestored) "観測盤は起動中" else "観測盤は休眠中"),
                        CoreMenuCanvas.Line(""),
                        CoreMenuCanvas.Line("紙上の小さな星図は"),
                        CoreMenuCanvas.Line("持ち帰った未知に応える"),
                        CoreMenuCanvas.Line(""),
                        CoreMenuCanvas.Line("素材を選ぶと分析"),
                        CoreMenuCanvas.Line("素材は消費しない"),
                    ))
                }
                Page.DISTILLER -> {
                    canvas.left("抽出工程", listOf(
                        CoreMenuCanvas.Line("研究済み素材を選ぶ"),
                        CoreMenuCanvas.Line("炉が約3秒で加熱"),
                        CoreMenuCanvas.Line("冷却管からJarへ"),
                        CoreMenuCanvas.Line(""),
                        CoreMenuCanvas.Line("投入素材は1個消費"),
                        CoreMenuCanvas.Line("対応するJarを先に置く"),
                        CoreMenuCanvas.Line("Jar上限 16 / 性質"),
                    ))
                    canvas.right("炉の状態", listOf(
                        CoreMenuCanvas.Line(brewing[player.uuid]?.let { "加熱中 ${it.first.label}" } ?: "待機中"),
                        CoreMenuCanvas.Line(""),
                        CoreMenuCanvas.Line("熾火は熱として"),
                        CoreMenuCanvas.Line("潮は循環として"),
                        CoreMenuCanvas.Line("風は流れとして"),
                        CoreMenuCanvas.Line("石は沈殿として残る"),
                    ))
                }
                Page.JARS -> {
                    canvas.left("四つの保存瓶", listOf(
                        CoreMenuCanvas.Line("瓶ごとに一性質"),
                        CoreMenuCanvas.Line("中身と残量を見る"),
                        CoreMenuCanvas.Line("Jarを持って棚を右クリック"),
                        CoreMenuCanvas.Line("棚の瓶をクリックで取り出す"),
                        CoreMenuCanvas.Line(""),
                        CoreMenuCanvas.Line("初回抽出 ${if (progress.firstDistillation) "済" else "未"}"),
                    ))
                    canvas.right("保管記録", FirstAspect.entries.map { aspect ->
                        CoreMenuCanvas.Line("${aspect.name}  ${progress.jar(aspect)} / 16")
                    } + listOf(CoreMenuCanvas.Line(""), CoreMenuCanvas.Line("瓶の色は性質に対応")))
                }
                Page.JOURNAL -> {
                    canvas.left("第一頁", listOf(
                        CoreMenuCanvas.Line("観測済み ${progress.studied.size} / 6"),
                        CoreMenuCanvas.Line("判明した性質 $known / 4"),
                        CoreMenuCanvas.Line(""),
                        CoreMenuCanvas.Line("未知から性質を知る"),
                        CoreMenuCanvas.Line("抽出して瓶へ残す"),
                    ))
                    canvas.right("その先の余白", listOf(
                        CoreMenuCanvas.Line("封印区画の円形台座"),
                        CoreMenuCanvas.Line("まだ共鳴が足りない"),
                        CoreMenuCanvas.Line(""),
                        CoreMenuCanvas.Line("四性質は入口にすぎない"),
                    ))
                }
                Page.RESEARCH -> {
                    canvas.left("研究の手順", listOf(
                        CoreMenuCanvas.Line("素材を分析し性質を発見"),
                        CoreMenuCanvas.Line("二性質を合成して新発見"),
                        CoreMenuCanvas.Line("六角盤の空欄に記す"),
                        CoreMenuCanvas.Line("となりの性質をつなぐ"),
                        CoreMenuCanvas.Line("固定点が全てつながれば解明"),
                        CoreMenuCanvas.Line(""),
                        CoreMenuCanvas.Line("解明 ${progress.unlockedResearch.size} / ${ResearchCatalog.all.size}"),
                    ))
                    canvas.right("研究インク", listOf(
                        CoreMenuCanvas.Line("所持した性質を一つ消費"),
                        CoreMenuCanvas.Line("外せばインクは戻る"),
                        CoreMenuCanvas.Line("合成で複合性質を4得る"),
                        CoreMenuCanvas.Line(""),
                        CoreMenuCanvas.Line("発見 ${progress.discoveredResearchAspects.size} / ${AspectCatalog.all.size}"),
                    ))
                }
                Page.COMBINE -> {
                    canvas.left("性質の合成", listOf(
                        CoreMenuCanvas.Line("発見済みの二性質を使う"),
                        CoreMenuCanvas.Line("各インクを一つ消費"),
                        CoreMenuCanvas.Line("新しい性質のインクを4得る"),
                        CoreMenuCanvas.Line(""),
                        CoreMenuCanvas.Line("素材分析で基礎性質を補充"),
                    ))
                    canvas.right("構成の結び", listOf(
                        CoreMenuCanvas.Line("複合性質とその材料は"),
                        CoreMenuCanvas.Line("研究盤で接続できる"),
                        CoreMenuCanvas.Line(""),
                        CoreMenuCanvas.Line("火 + 風 → 光"),
                    ))
                }
            }
            canvas.render()
        } else CoreLoopItems.text(title, NamedTextColor.GOLD)
        screens.show(player, CoreMenuInventory.Screen(heading, items,
            actions + (53 to { exit(player) }), redraw))
    }

    fun desk(player: Player) {
        if (!stationPlaced(player, ColonyPlaceable.DESK)) {
            player.sendMessage(CoreLoopItems.text("研究机を庭に配置してから調べよう", NamedTextColor.YELLOW))
            return
        }
        val progress = state(player) ?: return
        val items = frame(player)
        val actions = mutableMapOf<Int, () -> Unit>()
        items[4] = magicIcon(player, "journal", Material.LECTERN, "観測記録", if (progress.deskRestored) "机は起動している" else "古い観測盤は眠っている")
        items[22] = magicIcon(player, "desk", if (progress.deskRestored) Material.AMETHYST_BLOCK else Material.COPPER_INGOT,
            if (progress.deskRestored) "観測盤 / 稼働中" else "研究机を修復", if (progress.reacted) "クリックして修復" else "異質素材を持ち帰ると反応する")
        if (!progress.deskRestored) actions[22] = { change(player, FirstMagicAction.RestoreDesk) { desk(player) } }
        AnomalousMaterial.entries.forEachIndexed { index, material ->
            val known = material in progress.studied
            val detail = if (known) material.aspects.entries.joinToString("  ") { "${it.key.name} ×${it.value}" } else "性質はまだ不明"
            items[materialSlots[index]] = magicIcon(player, materialArt(material), materialIcon(material),
                "${material.label}  ×${progress.count(material)}", detail,
                if (progress.count(material) > 0 && !known) "クリックで分析 / 素材は残る" else if (known) "記録帳に保存済み" else "遠征で拾える")
            actions[materialSlots[index]] = { change(player, FirstMagicAction.Analyze(material)) { desk(player) } }
        }
        FirstAspect.entries.forEachIndexed { index, aspect ->
            val known = aspect in progress.knownAspects
            items[aspectSlots[index]] = magicIcon(player, if (known) aspect.name.lowercase() else "sealed",
                if (known) aspectIcon(aspect) else Material.GRAY_DYE,
                if (known) aspect.label else "未解明の性質",
                if (known) "分析により判明した" else "未知の素材に眠っている")
        }
        items[40] = magicIcon(player, "journal", Material.WRITABLE_BOOK, "魔導記録帳", "判明した性質：${progress.knownAspects.joinToString { it.name }.ifEmpty { "なし" }}", "クリックで記録を読む")
        actions[40] = { journal(player) }
        items[41] = magicIcon(player, "journal", Material.ENCHANTED_BOOK, "研究の星図", "六角盤で研究を解明する")
        actions[41] = { researchList(player) }
        items[49] = magicIcon(player, "distiller", Material.BREWING_STAND, "蒸留器へ", "分析済み素材からEssentiaを抽出")
        actions[49] = { distiller(player) }
        show(player, Page.DESK, "観測工房・研究机", progress, items, actions) { desk(player) }
    }

    fun distiller(player: Player) {
        if (!stationPlaced(player, ColonyPlaceable.DISTILLER)) {
            player.sendMessage(CoreLoopItems.text("蒸留器を庭に配置してから使おう", NamedTextColor.YELLOW))
            return
        }
        val progress = state(player) ?: return
        val items = frame(player)
        val actions = mutableMapOf<Int, () -> Unit>()
        items[4] = magicIcon(player, "distiller", Material.BREWING_STAND, "粗末な蒸留器", "研究済み素材を1個消費", "沸騰 → 冷却 → Jarへ保存")
        items[22] = magicIcon(player, "distiller", Material.CAULDRON, brewing[player.uuid]?.let { "蒸留中：${it.first.label}" } ?: "蒸留槽", "1回につき約3秒")
        AnomalousMaterial.entries.forEachIndexed { index, material ->
            val known = material in progress.studied
            val detail = if (known) material.aspects.entries.joinToString("  ") { "${it.key.name} +${it.value}" } else "研究机で先に分析"
            items[materialSlots[index]] = magicIcon(player, materialArt(material), materialIcon(material), "${material.label} ×${progress.count(material)}", detail, "クリックで蒸留開始")
            actions[materialSlots[index]] = { startDistillation(player, material) }
        }
        FirstAspect.entries.forEachIndexed { index, aspect ->
            items[aspectSlots[index]] = magicIcon(player, aspect.name.lowercase(), aspectIcon(aspect), aspect.label,
                if (jarPlaced(player, aspect)) "Jarに ${progress.jar(aspect)} / ${FirstMagicRules.JAR_CAPACITY}" else "Jarを庭に置くと受け取れる")
        }
        items[40] = magicIcon(player, "jar", Material.GLASS_BOTTLE, "Jar棚を見る", "保存されたEssentiaを確認")
        actions[40] = { jars(player) }
        items[49] = magicIcon(player, "desk", Material.LECTERN, "研究机へ")
        actions[49] = { desk(player) }
        show(player, Page.DISTILLER, "観測工房・蒸留器", progress, items, actions) { distiller(player) }
    }

    private fun startDistillation(player: Player, material: AnomalousMaterial) {
        val progress = state(player) ?: return
        if (brewing.containsKey(player.uuid)) { player.sendMessage(CoreLoopItems.text("蒸留中です", NamedTextColor.YELLOW)); return }
        if (!progress.deskRestored || material !in progress.studied || progress.count(material) == 0 ||
            material.aspects.any { (aspect, amount) -> progress.jar(aspect) + amount > FirstMagicRules.JAR_CAPACITY }) {
            change(player, FirstMagicAction.Distill(material)) { distiller(player) }
            return
        }
        if (material.aspects.keys.any { !jarPlaced(player, it) }) {
            player.sendMessage(CoreLoopItems.text("${material.label}に対応するJarを先に配置しよう", NamedTextColor.YELLOW))
            return
        }
        brewing[player.uuid] = material to (player.aliveTicks + 60)
        player.sendMessage(CoreLoopItems.text("${material.label}を加熱中…色のついた蒸気がガラス管を昇る", NamedTextColor.AQUA))
        distiller(player)
    }

    fun tick(player: Player) {
        val brew = brewing[player.uuid] ?: return
        if (!inColony(player)) { brewing.remove(player.uuid); return }
        val remaining = brew.second - player.aliveTicks
        if (remaining > 0) {
            if (remaining % 20L == 0L) player.sendActionBar(CoreLoopItems.text("蒸留中  ${remaining / 20 + 1}…", NamedTextColor.AQUA))
            return
        }
        if (brewing.remove(player.uuid, brew)) {
            if (brew.first.aspects.keys.any { !jarPlaced(player, it) }) {
                player.sendMessage(CoreLoopItems.text("Jarが外されたため蒸留を中止した。素材は残っている", NamedTextColor.YELLOW))
                distiller(player)
            } else change(player, FirstMagicAction.Distill(brew.first)) { distiller(player) }
        }
    }

    fun jars(player: Player) {
        val progress = state(player) ?: return
        val items = frame(player)
        val actions = mutableMapOf<Int, () -> Unit>()
        items[4] = magicIcon(player, "jar", Material.GLASS_BOTTLE, "Essentia Jar棚", "1瓶につき1性質 / 容量${FirstMagicRules.JAR_CAPACITY}")
        FirstAspect.entries.forEachIndexed { index, aspect ->
            val amount = progress.jar(aspect)
            val slot = listOf(19, 21, 23, 25)[index]
            items[slot] = jarIcon(player, aspect, amount)
            items[aspectSlots[index]] = magicIcon(player, aspect.name.lowercase(), aspectIcon(aspect), aspect.label,
                "${amount} / ${FirstMagicRules.JAR_CAPACITY} Essentia",
                if (jarOnShelf(player, aspect)) "棚に収納中 / 瓶をクリックして取り出す"
                else if (jarPlaced(player, aspect)) "庭に設置中" else "Jarを持って棚を右クリック")
            if (jarOnShelf(player, aspect)) actions[slot] = { if (takeShelfJar(player, aspect)) jars(player) }
        }
        items[40] = magicIcon(player, "journal", Material.WRITABLE_BOOK, "魔導記録帳", "初回の観測結果を確認")
        actions[40] = { journal(player) }
        items[49] = magicIcon(player, "distiller", Material.BREWING_STAND, "蒸留器へ")
        actions[49] = { distiller(player) }
        show(player, Page.JARS, "観測工房・Jar棚", progress, items, actions) { jars(player) }
    }

    fun journal(player: Player) {
        val progress = state(player) ?: return
        val items = frame(player)
        val actions = mutableMapOf<Int, () -> Unit>()
        items[4] = magicIcon(player, "journal", Material.WRITABLE_BOOK, "魔導記録帳 第一頁", "Essentia ${progress.knownAspects.size} / 4", "研究Aspect ${progress.discoveredResearchAspects.size} / ${AspectCatalog.all.size}")
        AnomalousMaterial.entries.forEachIndexed { index, material ->
            items[materialSlots[index]] = magicIcon(player, if (material in progress.studied) materialArt(material) else "journal", if (material in progress.studied) materialIcon(material) else Material.PAPER,
                if (material in progress.studied) material.label else "未分析の頁",
                if (material in progress.studied) material.aspects.entries.joinToString(" / ") { "${it.key.name} ×${it.value}" } else "未知を持ち帰り研究机に置く")
        }
        items[40] = magicIcon(player, "sealed", Material.AMETHYST_SHARD, if (progress.firstDistillation) "最初の蒸留を記録" else "まだ白紙の余白",
            if (progress.firstDistillation) "封印区画にはさらに大きな装置が眠る" else "Jarが満たされる日を待つ")
        items[41] = magicIcon(player, "journal", Material.ENCHANTED_BOOK, "研究の星図", "研究 ${progress.unlockedResearch.size} / ${ResearchCatalog.all.size}", "クリックで六角盤を開く")
        actions[41] = { researchList(player) }
        items[49] = magicIcon(player, "desk", Material.LECTERN, "研究机へ")
        actions[49] = { desk(player) }
        show(player, Page.JOURNAL, "魔導記録帳・第一頁", progress, items, actions) { journal(player) }
    }

    private fun researchModel(player: Player, item: ItemStack, aspectId: String? = null): ItemStack =
        if (packed(player)) item.withItemModel("projects:first_magic/research_${aspectId ?: "empty"}") else item

    private fun researchIcon(player: Player, aspectId: String, known: Boolean, ink: Int? = null): ItemStack {
        val aspect = AspectCatalog.byId.getValue(aspectId)
        val material = when (aspectId) {
            "aer" -> Material.FEATHER
            "aqua" -> Material.PRISMARINE_CRYSTALS
            "ignis" -> Material.BLAZE_POWDER
            "terra" -> Material.CLAY_BALL
            "ordo" -> Material.QUARTZ
            "perditio" -> Material.ECHO_SHARD
            else -> Material.AMETHYST_SHARD
        }
        return researchModel(player, CoreLoopItems.icon(if (known) material else Material.GRAY_DYE,
            if (known) aspect.name else "未発見のAspect",
            if (known) "構成: ${aspect.components.joinToString(" + ").ifEmpty { "基礎" }}" else "素材を分析して発見",
            if (ink != null) "研究インク $ink" else "固定された手がかり"), if (known) aspectId else null)
    }

    fun researchList(player: Player, page: Int = 0) {
        val progress = state(player) ?: return
        if (!progress.deskRestored) { desk(player); return }
        val items = frame(player)
        val actions = mutableMapOf<Int, () -> Unit>()
        items[4] = magicIcon(player, "journal", Material.ENCHANTED_BOOK, "研究の星図", "解明 ${progress.unlockedResearch.size} / ${ResearchCatalog.all.size}")
        val subjects = ResearchCatalog.all.drop(page * 5).take(5)
        val slots = listOf(10, 12, 14, 28, 30)
        subjects.forEachIndexed { index, research ->
            val unlocked = research.id in progress.unlockedResearch
            items[slots[index]] = CoreLoopItems.icon(if (unlocked) Material.ENCHANTED_BOOK else Material.BOOK,
                "${if (unlocked) "✦" else "◇"} ${research.title}", research.description,
                if (unlocked) "解明済み / クリックで盤面を見る" else "クリックで研究を開く")
            actions[slots[index]] = { researchBoard(player, research.id) }
        }
        items[36] = CoreLoopItems.icon(Material.ARROW, "前の頁")
        if (page > 0) actions[36] = { researchList(player, page - 1) }
        items[44] = CoreLoopItems.icon(Material.ARROW, "次の頁")
        if ((page + 1) * 5 < ResearchCatalog.all.size) actions[44] = { researchList(player, page + 1) }
        items[40] = magicIcon(player, "desk", Material.LECTERN, "Aspectを合成", "新しい性質を発見する")
        actions[40] = { combineList(player) }
        items[49] = magicIcon(player, "journal", Material.WRITABLE_BOOK, "記録帳へ")
        actions[49] = { journal(player) }
        show(player, Page.RESEARCH, "研究の星図 ${page + 1}/2", progress, items, actions) { researchList(player, page) }
    }

    private fun combineList(player: Player, page: Int = 0) {
        val progress = state(player) ?: return
        val items = frame(player)
        val actions = mutableMapOf<Int, () -> Unit>()
        items[4] = magicIcon(player, "desk", Material.LECTERN, "Aspectの合成", "基礎から複合性質を発見する")
        val slots = listOf(10, 12, 14, 16, 28, 30, 32, 34)
        AspectCatalog.compound.drop(page * 8).take(8).forEachIndexed { index, aspect ->
            val ready = aspect.components.all { it in progress.discoveredResearchAspects && progress.ink(it) > 0 }
            items[slots[index]] = researchModel(player, CoreLoopItems.icon(if (ready) Material.AMETHYST_SHARD else Material.GRAY_DYE,
                if (aspect.id in progress.discoveredResearchAspects) aspect.name else "未知の組み合わせ",
                aspect.components.joinToString(" + ") { AspectCatalog.byId.getValue(it).name },
                "インク ${progress.ink(aspect.id)} / ${if (ready) "クリックで合成" else "材料が不足"}"),
                aspect.id.takeIf { it in progress.discoveredResearchAspects })
            actions[slots[index]] = { change(player, FirstMagicAction.Combine(aspect.components[0], aspect.components[1])) { combineList(player, page) } }
        }
        items[36] = CoreLoopItems.icon(Material.ARROW, "前の頁")
        if (page > 0) actions[36] = { combineList(player, page - 1) }
        items[44] = CoreLoopItems.icon(Material.ARROW, "次の頁")
        if ((page + 1) * 8 < AspectCatalog.compound.size) actions[44] = { combineList(player, page + 1) }
        items[49] = magicIcon(player, "journal", Material.WRITABLE_BOOK, "研究一覧へ")
        actions[49] = { researchList(player) }
        show(player, Page.COMBINE, "Aspect合成 ${page + 1}/2", progress, items, actions) { combineList(player, page) }
    }

    private fun researchBoard(player: Player, researchId: String) {
        val progress = state(player) ?: return
        val research = ResearchCatalog.byId[researchId] ?: return
        val placed = progress.researchPlacements[researchId].orEmpty()
        val occupied = research.anchors + placed
        val connected = ResearchBoard.connectedAnchors(research, placed)
        val items = frame(player)
        val actions = mutableMapOf<Int, () -> Unit>()
        for (slot in ResearchBoard.cells.keys) {
            val anchor = research.anchors[slot]
            val aspect = anchor ?: placed[slot]
            items[slot] = if (aspect == null) researchModel(player, CoreLoopItems.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                "空の六角", "クリックしてAspectを選ぶ")) else {
                val links = ResearchBoard.neighbors(slot).count { next ->
                    occupied[next]?.let { AspectCatalog.linked(aspect, it) } == true
                }
                researchModel(player, CoreLoopItems.icon(Material.AMETHYST_SHARD, AspectCatalog.byId.getValue(aspect).name,
                    "有効な接続 $links", "クリックで外し、インクを戻す"), aspect)
            }
            if (anchor != null) {
                items[slot] = researchModel(player, CoreLoopItems.icon(if (slot in connected) Material.GLOWSTONE_DUST else Material.QUARTZ,
                    "◆ ${AspectCatalog.byId.getValue(anchor).name}", "固定された手がかり", if (slot in connected) "起点と接続中" else "まだ起点とつながっていない"), anchor)
                    .withGlowing(slot in connected)
            } else if (aspect == null && researchId !in progress.unlockedResearch) {
                actions[slot] = { chooseResearchInk(player, researchId, slot) }
            } else if (aspect != null && researchId !in progress.unlockedResearch) {
                actions[slot] = { change(player, FirstMagicAction.Remove(researchId, slot)) { researchBoard(player, researchId) } }
            }
        }
        items[45] = CoreLoopItems.icon(Material.ARROW, "研究一覧へ")
        actions[45] = { researchList(player) }
        items[49] = CoreLoopItems.icon(if (researchId in progress.unlockedResearch) Material.ENCHANTED_BOOK else Material.WRITABLE_BOOK,
            research.title, research.description,
            if (researchId in progress.unlockedResearch) "研究解明済み" else "手がかり ${connected.size} / ${research.anchors.size} 接続")
        items[51] = CoreLoopItems.icon(Material.AMETHYST_SHARD, "Aspect合成へ")
        actions[51] = { combineList(player) }
        show(player, Page.RESEARCH, "研究盤・${research.title}", progress, items, actions) { researchBoard(player, researchId) }
    }

    private fun chooseResearchInk(player: Player, researchId: String, slot: Int, page: Int = 0) {
        val progress = state(player) ?: return
        val items = frame(player)
        val actions = mutableMapOf<Int, () -> Unit>()
        items[4] = CoreLoopItems.icon(Material.WRITABLE_BOOK, "六角 $slot に記すAspect", "所持インクを一つ使う")
        val slots = listOf(10, 12, 14, 16, 28, 30, 32, 34)
        AspectCatalog.all.drop(page * 8).take(8).forEachIndexed { index, aspect ->
            val known = aspect.id in progress.discoveredResearchAspects
            items[slots[index]] = researchIcon(player, aspect.id, known, progress.ink(aspect.id))
                .withAmount(1)
            if (known && progress.ink(aspect.id) > 0) actions[slots[index]] = {
                change(player, FirstMagicAction.Place(researchId, slot, aspect.id)) { researchBoard(player, researchId) }
            }
        }
        items[36] = CoreLoopItems.icon(Material.ARROW, "前の頁")
        if (page > 0) actions[36] = { chooseResearchInk(player, researchId, slot, page - 1) }
        items[44] = CoreLoopItems.icon(Material.ARROW, "次の頁")
        if ((page + 1) * 8 < AspectCatalog.all.size) actions[44] = { chooseResearchInk(player, researchId, slot, page + 1) }
        items[49] = CoreLoopItems.icon(Material.BOOK, "盤面へ戻る")
        actions[49] = { researchBoard(player, researchId) }
        show(player, Page.RESEARCH, "研究インク ${page + 1}/2", progress, items, actions) { chooseResearchInk(player, researchId, slot, page) }
    }
}
