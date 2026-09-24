package dev.projects.server.coreloop

import dev.projects.server.coreloop.ui.CoreMenuInventory
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
) {
    private val screens = CoreMenuInventory()
    private val brewing = ConcurrentHashMap<UUID, Pair<AnomalousMaterial, Long>>()
    private val materialSlots = listOf(10, 12, 14, 28, 30, 32)

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
    private fun frame(): Array<ItemStack> = Array(54) { index ->
        when {
            index < 9 || index >= 45 || index % 9 == 0 || index % 9 == 8 -> CoreLoopItems.icon(Material.BROWN_STAINED_GLASS_PANE, " ")
            else -> CoreLoopItems.icon(Material.GRAY_STAINED_GLASS_PANE, " ")
        }
    }
    private fun show(player: Player, title: String, items: Array<ItemStack>, actions: Map<Int, () -> Unit>, redraw: () -> Unit) {
        if (!inColony(player)) return
        items[53] = CoreLoopItems.icon(Material.COMPASS, "港へ帰還")
        screens.show(player, CoreMenuInventory.Screen(CoreLoopItems.text(title, NamedTextColor.GOLD), items,
            actions + (53 to { exit(player) }), redraw))
    }

    fun desk(player: Player) {
        val progress = state(player) ?: return
        val items = frame()
        val actions = mutableMapOf<Int, () -> Unit>()
        items[4] = CoreLoopItems.icon(Material.LECTERN, "観測記録", if (progress.deskRestored) "机は起動している" else "古い観測盤は眠っている")
        items[22] = CoreLoopItems.icon(if (progress.deskRestored) Material.AMETHYST_BLOCK else Material.COPPER_INGOT,
            if (progress.deskRestored) "観測盤 / 稼働中" else "研究机を修復", if (progress.reacted) "クリックして修復" else "異質素材を持ち帰ると反応する")
        if (!progress.deskRestored) actions[22] = { change(player, FirstMagicAction.RestoreDesk) { desk(player) } }
        AnomalousMaterial.entries.forEachIndexed { index, material ->
            val known = material in progress.studied
            val detail = if (known) material.aspects.entries.joinToString("  ") { "${it.key.name} ×${it.value}" } else "性質はまだ不明"
            items[materialSlots[index]] = CoreLoopItems.icon(materialIcon(material),
                "${material.label}  ×${progress.count(material)}", detail,
                if (progress.count(material) > 0 && !known) "クリックで分析 / 素材は残る" else if (known) "記録帳に保存済み" else "遠征で拾える")
            actions[materialSlots[index]] = { change(player, FirstMagicAction.Analyze(material)) { desk(player) } }
        }
        items[40] = CoreLoopItems.icon(Material.WRITABLE_BOOK, "魔導記録帳", "判明した性質：${progress.knownAspects.joinToString { it.name }.ifEmpty { "なし" }}", "クリックで記録を読む")
        actions[40] = { journal(player) }
        items[49] = CoreLoopItems.icon(Material.BREWING_STAND, "蒸留器へ", "分析済み素材からEssentiaを抽出")
        actions[49] = { distiller(player) }
        show(player, "古い観測工房 | 研究机", items, actions) { desk(player) }
    }

    fun distiller(player: Player) {
        val progress = state(player) ?: return
        val items = frame()
        val actions = mutableMapOf<Int, () -> Unit>()
        items[4] = CoreLoopItems.icon(Material.BREWING_STAND, "粗末な蒸留器", "研究済み素材を1個消費", "沸騰 → 冷却 → Jarへ保存")
        items[22] = CoreLoopItems.icon(Material.CAULDRON, brewing[player.uuid]?.let { "蒸留中：${it.first.label}" } ?: "蒸留槽", "1回につき約3秒")
        AnomalousMaterial.entries.forEachIndexed { index, material ->
            val known = material in progress.studied
            val detail = if (known) material.aspects.entries.joinToString("  ") { "${it.key.name} +${it.value}" } else "研究机で先に分析"
            items[materialSlots[index]] = CoreLoopItems.icon(materialIcon(material), "${material.label} ×${progress.count(material)}", detail, "クリックで蒸留開始")
            actions[materialSlots[index]] = { startDistillation(player, material) }
        }
        items[40] = CoreLoopItems.icon(Material.GLASS_BOTTLE, "Jar棚を見る", "保存されたEssentiaを確認")
        actions[40] = { jars(player) }
        items[49] = CoreLoopItems.icon(Material.LECTERN, "研究机へ")
        actions[49] = { desk(player) }
        show(player, "古い観測工房 | 蒸留器", items, actions) { distiller(player) }
    }

    private fun startDistillation(player: Player, material: AnomalousMaterial) {
        val progress = state(player) ?: return
        if (brewing.containsKey(player.uuid)) { player.sendMessage(CoreLoopItems.text("蒸留中です", NamedTextColor.YELLOW)); return }
        if (!progress.deskRestored || material !in progress.studied || progress.count(material) == 0 ||
            material.aspects.any { (aspect, amount) -> progress.jar(aspect) + amount > FirstMagicRules.JAR_CAPACITY }) {
            change(player, FirstMagicAction.Distill(material)) { distiller(player) }
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
        if (brewing.remove(player.uuid, brew)) change(player, FirstMagicAction.Distill(brew.first)) { distiller(player) }
    }

    fun jars(player: Player) {
        val progress = state(player) ?: return
        val items = frame()
        val actions = mutableMapOf<Int, () -> Unit>()
        items[4] = CoreLoopItems.icon(Material.GLASS_BOTTLE, "Essentia Jar棚", "1瓶につき1性質 / 容量${FirstMagicRules.JAR_CAPACITY}")
        FirstAspect.entries.forEachIndexed { index, aspect ->
            val amount = progress.jar(aspect)
            items[listOf(19, 21, 23, 25)[index]] = CoreLoopItems.icon(aspectIcon(aspect), aspect.label,
                "${"▰".repeat(amount / 2)}${"▱".repeat((FirstMagicRules.JAR_CAPACITY - amount) / 2)}",
                "${amount} / ${FirstMagicRules.JAR_CAPACITY} Essentia")
        }
        items[40] = CoreLoopItems.icon(Material.WRITABLE_BOOK, "魔導記録帳", "初回の観測結果を確認")
        actions[40] = { journal(player) }
        items[49] = CoreLoopItems.icon(Material.BREWING_STAND, "蒸留器へ")
        actions[49] = { distiller(player) }
        show(player, "古い観測工房 | Jar棚", items, actions) { jars(player) }
    }

    fun journal(player: Player) {
        val progress = state(player) ?: return
        val items = frame()
        val actions = mutableMapOf<Int, () -> Unit>()
        items[4] = CoreLoopItems.icon(Material.WRITABLE_BOOK, "魔導記録帳 第一頁", "発見した性質 ${progress.knownAspects.size} / 4")
        AnomalousMaterial.entries.forEachIndexed { index, material ->
            items[materialSlots[index]] = CoreLoopItems.icon(if (material in progress.studied) materialIcon(material) else Material.PAPER,
                if (material in progress.studied) material.label else "未分析の頁",
                if (material in progress.studied) material.aspects.entries.joinToString(" / ") { "${it.key.name} ×${it.value}" } else "未知を持ち帰り研究机に置く")
        }
        items[40] = CoreLoopItems.icon(Material.AMETHYST_SHARD, if (progress.firstDistillation) "最初の蒸留を記録" else "まだ白紙の余白",
            if (progress.firstDistillation) "封印区画にはさらに大きな装置が眠る" else "Jarが満たされる日を待つ")
        items[49] = CoreLoopItems.icon(Material.LECTERN, "研究机へ")
        actions[49] = { desk(player) }
        show(player, "魔導記録帳 | 第一頁", items, actions) { journal(player) }
    }
}
