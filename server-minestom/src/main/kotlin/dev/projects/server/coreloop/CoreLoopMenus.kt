package dev.projects.server.coreloop

import dev.projects.server.coreloop.ui.*
import dev.projects.server.coreloop.ui.CoreMenuCanvas.Line
import dev.projects.server.coreloop.ui.CoreMenuCanvas.Tone
import dev.projects.server.questmap.*
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.Player
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Labels, previews and actions share vanilla slot bounds. The ledger owns every spend. */
internal class CoreLoopMenus(private val game: CoreMenuHost, private val inspect: ((CoreMenuCanvas) -> Unit)? = null) {
    private class View(val canvas: CoreMenuCanvas, val packed: Boolean) {
        val items = Array(54) { ItemStack.AIR }
        val actions = mutableMapOf<Int, () -> Unit>()
        val occupied = mutableSetOf<Int>()
        lateinit var screen: CoreMenuInventory.Screen
    }
    private val screens = CoreMenuInventory()
    private val selections = ConcurrentHashMap<UUID, CoreForgeLayout.Selection>()
    private val journeys = ConcurrentHashMap<UUID, CoreForgeJourney>()
    private val listSlots = listOf(9, 14, 18, 23, 27, 32, 36, 41)
    private val mapSlots = listOf(9, 12, 15, 27, 30, 33)
    private val stockSlots = listSlots
    internal companion object { val REFINE_SLOTS = listOf(9, 12, 15, 27, 30) }
    fun click(event: InventoryPreClickEvent): Boolean = screens.click(event)
    fun forget(playerId: UUID) { screens.forget(playerId); selections.remove(playerId); journeys.remove(playerId) }
    fun refreshTheme(player: Player) = screens.refresh(player)
    private fun journey(player: Player) = journeys.computeIfAbsent(player.uuid) { CoreForgeJourney() }

    private fun view(player: Player, title: String, redraw: () -> Unit, build: (View) -> Unit) {
        val v = View(CoreMenuCanvas(title), game.packed(player))
        build(v)
        inspect?.invoke(v.canvas)
        // Without the optional pack, no private glyph or invisible control is sent.
        // The always-visible panel's original text remains available in the central details book.
        if (!v.packed) v.items[8] = CoreLoopItems.icon(Material.BOOK, "画面の詳細 / ヘルプ", *v.canvas.fallbackLines().toTypedArray())
        v.items.indices.forEach { slot ->
            val item = v.items[slot]
            if (!item.isAir && item.get(DataComponents.TOOLTIP_STYLE) == null)
                v.items[slot] = CoreLoopItems.menuSkin(item, v.packed)
        }
        v.screen = CoreMenuInventory.Screen(if (v.packed) v.canvas.render() else CoreUiComponents.inventoryTitle(title, false), v.items, v.actions, redraw)
        screens.show(player, v.screen)
    }

    /** Every occupied slot activates the label, not just its small icon. */
    private fun tile(v: View, slot: Int, span: Int, label: String, item: ItemStack = CoreLoopItems.icon(Material.PAPER, label),
        tone: Tone = Tone.NEUTRAL, icon: Boolean = false, action: (() -> Unit)? = null) {
        require(slot in 0..53 && span in 1..9 && slot % 9 + span <= 9)
        val display = item.withAmount(1).withGlowing(false)
        for (at in slot until slot + span) {
            check(v.occupied.add(at)) { "Menu button overlaps slot $at: $label" }
            v.items[at] = if (v.packed && !(icon && at == slot)) CoreUiItemSkin.blank(display, true) else display
            if (action != null && tone != Tone.DISABLED) v.actions[at] = action
        }
        v.canvas.button(slot, span, label, tone, icon)
    }
    /** Art remains a server projection; every vanilla slot beneath a card shares one action. */
    private fun card(v: View, slot: Int, columns: Int, rows: Int, label: String, art: CoreMenuArt,
        item: ItemStack = CoreLoopItems.icon(Material.PAPER, label), tone: Tone = Tone.NEUTRAL, action: (() -> Unit)? = null) {
        require(slot in 0..53 && columns > 0 && rows > 0 && slot % 9 + columns <= 9 && slot / 9 + rows <= 6)
        val display = item.withAmount(1).withGlowing(false)
        repeat(rows) { row -> repeat(columns) { column ->
            val at = slot + row * 9 + column
            check(v.occupied.add(at)) { "Menu card overlaps slot $at: $label" }
            v.items[at] = if (v.packed) CoreUiItemSkin.blank(display, true) else display
            if (action != null && tone != Tone.DISABLED) v.actions[at] = action
        } }
        v.canvas.card(slot, columns, rows, label, art, tone)
    }
    private fun materialArt(resource: CoreResource): CoreMenuArt = when (resource) {
        CoreResource.WOOD -> CoreMenuArt.WOOD
        CoreResource.ORE -> CoreMenuArt.ORE
        CoreResource.STONE, CoreResource.WHETSTONE -> CoreMenuArt.STONE
        CoreResource.HIDE -> CoreMenuArt.HIDE
        CoreResource.FIBER -> CoreMenuArt.FIBER
        CoreResource.BOARD -> CoreMenuArt.PLANK
        CoreResource.INGOT -> CoreMenuArt.INGOT
        CoreResource.STONE_BLOCK -> CoreMenuArt.CUT_STONE
        CoreResource.LEATHER -> CoreMenuArt.LEATHER
        CoreResource.CLOTH -> CoreMenuArt.CLOTH
        CoreResource.POTION -> CoreMenuArt.POTION
        CoreResource.GATHERING_TABLET -> CoreMenuArt.TABLET
        CoreResource.BOSS_SIGIL -> CoreMenuArt.BOSS
        CoreResource.COMBAT_TOKEN -> CoreMenuArt.ORB
        CoreResource.AFFIX_DUST -> CoreMenuArt.SHARD
    }
    private fun gearArt(gear: CoreGearSlot) = if (gear == CoreGearSlot.WEAPON) CoreMenuArt.WEAPON else CoreMenuArt.ARMOR
    private fun gatheringResource(discipline: QuestGatheringDiscipline) = CoreResource.entries.first { it.raw && it.displayName == discipline.commonResourceName }
    private fun back(v: View, player: Player, label: String = "戻る", action: () -> Unit = { journal(player) }) =
        tile(v, 45, 2, label, CoreLoopItems.icon(Material.ARROW, label), action = action)
    private fun help(v: View, player: Player, returnTo: () -> Unit) =
        tile(v, 8, 1, "?", CoreLoopItems.icon(Material.BOOK, "画面の見方", "左右：選択内容と費用 / 中央：操作", "表示サイズ・操作の説明")) { displayHelp(player, returnTo) }
    private fun lines(vararg text: String): List<Line> = text.flatMap { CoreMenuCanvas.wrap(it).map { part -> Line(part) } }
    private fun paragraph(text: String, color: TextColor = CoreUiComponents.IVORY): List<Line> = CoreMenuCanvas.wrap(text).map { Line(it, color) }
    private fun pct(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.ROOT, "%.1f", value)
    private fun resourceName(resource: CoreResource) = when (resource) {
        CoreResource.INGOT -> "金属材"
        CoreResource.GATHERING_TABLET -> "採取石板"
        else -> resource.displayName
    }
    private fun orbName(currency: CoreCraftingCurrency) = currency.displayName.removeSuffix("のオーブ")
    private fun equipment(a: CoreAccount, gear: CoreGearSlot): List<Line> = buildList {
        add(Line("T${CoreAffixCatalog.gearTier(a, gear)} ${gear.displayName} +${CoreEnhancementCatalog.state(a, gear).level}", CoreUiComponents.GOLD))
        add(Line(CoreAffixCatalog.rarity(a, gear).displayName))
        if (gear == CoreGearSlot.WEAPON) {
            add(Line("攻撃力 ${CoreWeaponPresentation.damage(a)}")); add(Line("速度 ${CoreWeaponPresentation.attackSpeedLabel(a)}"))
        } else add(Line("最大HP ${CoreWeaponPresentation.health(a)}"))
        add(Line("MOD ${a.equippedAffixes.count { it.gear == gear }} / ${CoreAffixCatalog.capacity(a, gear)}"))
    }
    private fun tiers(v: View, selected: Int, action: (Int) -> Unit) {
        (1..4).forEach { tier -> tile(v, (tier - 1) * 2, 2, "T$tier", CoreLoopItems.icon(Material.PAPER, "素材・地図 Tier $tier"),
            if (tier == selected) Tone.SELECTED else Tone.NEUTRAL) { action(tier) } }
    }
    private fun pageButtons(v: View, page: Int, last: Int, action: (Int) -> Unit) {
        tile(v, 47, 1, "<", CoreLoopItems.icon(Material.ARROW, "前のページ"), if (page > 0) Tone.NEUTRAL else Tone.DISABLED) { action(page - 1) }
        tile(v, 48, 3, "${page + 1} / ${last + 1}", CoreLoopItems.icon(Material.PAPER, "${page + 1} / ${last + 1} ページ"), Tone.DISABLED)
        tile(v, 51, 1, ">", CoreLoopItems.icon(Material.ARROW, "次のページ"), if (page < last) Tone.NEUTRAL else Tone.DISABLED) { action(page + 1) }
    }
    private fun mutate(v: View, player: Player, action: CoreAction, revision: Long, after: () -> Unit) {
        game.mutate(player, action, revision, onRejected = {
            if (screens.isCurrent(player, v.screen)) v.screen.redraw()
        }) { if (screens.isCurrent(player, v.screen)) after() }
    }

    fun journal(player: Player) {
        val a = game.account(player) ?: return
        if (game.isDeparting(player)) { player.sendMessage(CoreLoopItems.text("遠征を準備しています。しばらくお待ちください。")); return }
        journey(player).clear()
        val run = a.activeRun
        view(player, if (run == null) "開拓港 / 手帳" else "遠征 / 手帳", { journal(player) }) { v ->
            help(v, player) { journal(player) }
            v.canvas.left("冒険者", equipment(a, CoreGearSlot.WEAPON) + lines("", "防具 T${a.armorTier} +${a.armorEnhancement.level}",
                "HP ${CoreWeaponPresentation.health(a)}"), hero = CoreMenuArt.WEAPON)
            if (run == null) {
                v.canvas.right("開拓の旅", lines("遠征 → 収集", "工房 → 装備更新", "", "解放 T1〜${a.unlockedMapTier}", "地図 ${a.maps.size}枚", "T1地図は無料", "素材は自動保存"), hero = CoreMenuArt.EXPEDITION)
                card(v, 9, 3, 3, "遠征", CoreMenuArt.EXPEDITION, CoreLoopItems.icon(Material.CARTOGRAPHY_TABLE, "地図台から遠征", "地図を選ぶ → 調整 → 出発"), Tone.PRIMARY) { expeditions(player) }
                card(v, 12, 3, 3, "工房", CoreMenuArt.FORGE, CoreLoopItems.icon(Material.ANVIL, "装備工房", "強化・精製・制作・MOD加工")) { workshop(player) }
                card(v, 15, 3, 3, "保管庫", CoreMenuArt.STORAGE, CoreLoopItems.icon(Material.BARREL, "素材倉庫", "持っている素材と正確な所持数")) { storage(player) }
                card(v, 36, 3, 1, "装備", CoreMenuArt.GEAR, CoreLoopItems.icon(Material.IRON_SWORD, "装備とMODを確認")) { gearMods(player, selections[player.uuid]?.gear ?: CoreGearSlot.WEAPON) }
                card(v, 39, 3, 1, "採取", CoreMenuArt.GATHER, CoreLoopItems.icon(Material.OAK_SAPLING, "採取の心得・道具")) { mastery(player) }
                card(v, 42, 3, 1, "試練", CoreMenuArt.TRIAL, CoreLoopItems.icon(Material.END_PORTAL_FRAME, "欠片で専用ボスに挑戦")) { trials(player) }
                card(v, 45, 3, 1, "補給", CoreMenuArt.STORAGE, CoreLoopItems.icon(Material.GOLD_NUGGET, "戦利品券を素材へ交換")) { supplies(player) }
                card(v, 48, 3, 1, "遊び方", CoreMenuArt.HELP, CoreLoopItems.icon(Material.BOOK, "最初の一周・戦闘・採取操作")) { guide(player) }
            } else {
                v.canvas.right("遠征の状況", lines(if (run.bossDefeated) "ボス討伐達成！" else "T${run.map.tier} を探索中", "寄り道は自由", "素材は保存済み", "", game.sessionSummary(player)), hero = CoreMenuArt.EXPEDITION)
                card(v, 9, 3, 3, "探索", CoreMenuArt.EXPEDITION, CoreLoopItems.icon(Material.MAP, "画面を閉じて探索を続ける"), Tone.PRIMARY) { player.closeInventory() }
                card(v, 12, 3, 3, "帰還", CoreMenuArt.RETURN, CoreLoopItems.icon(Material.COMPASS, "帰還の確認へ", "今のマップには戻れなくなります")) { confirmReturn(player) }
                card(v, 15, 3, 3, "獲得品", CoreMenuArt.STORAGE, CoreLoopItems.icon(Material.BARREL, "倉庫へ保存された戦利品")) { storage(player, run.map.tier) }
                card(v, 36, 3, 1, "装備", CoreMenuArt.GEAR, CoreLoopItems.icon(Material.IRON_SWORD, "武器・防具のMODを確認")) { gearMods(player, CoreGearSlot.WEAPON) }
                card(v, 39, 3, 1, "道具", CoreMenuArt.GATHER, CoreLoopItems.icon(Material.WOODEN_AXE, "採取道具を持つ")) { tools(player) }
                card(v, 42, 3, 1, "遊び方", CoreMenuArt.HELP, CoreLoopItems.icon(Material.BOOK, "操作ガイド")) { guide(player) }
            }
            tile(v, 51, 3, "閉じる", CoreLoopItems.icon(Material.BARRIER, "画面を閉じる")) { player.closeInventory() }
        }
    }

    fun expeditions(player: Player, tier: Int = 1, page: Int = 0) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        val maps = a.maps.filter { it.tier == tier }
        val last = (maps.size - 1).coerceAtLeast(0) / mapSlots.size
        val current = page.coerceIn(0, last)
        val unlocked = tier <= a.unlockedMapTier
        val affordable = tier == 1 || a.amount(CoreResource.COMBAT_TOKEN, tier - 1) > 0
        val room = a.maps.size < CoreLoopCatalog.MAX_MAPS
        view(player, "地図台 / T$tier", { expeditions(player, tier, current) }) { v ->
            tiers(v, tier) { expeditions(player, it) }; help(v, player) { expeditions(player, tier, current) }
            v.canvas.left("遠征先 T$tier", lines("所持 ${maps.size}枚", "解放 T1〜${a.unlockedMapTier}", "目安装備 T$tier", "武器 T${a.weaponTier}", "", "選ぶ → 調整", "選択では未消費"), hero = CoreMenuArt.EXPEDITION)
            v.canvas.right("地図の入手", lines(if (tier == 1) "T1は無料" else "T${tier - 1} 戦利品券1枚", if (tier == 1) "何度でも入手可能" else "所持 ${a.amount(CoreResource.COMBAT_TOKEN, tier - 1)}", "", "前Tierのボス討伐", "次のTierを解放", "", if (!unlocked) "このTierは未解放" else if (!room) "地図の保管上限" else if (!affordable) "戦利品券が不足" else "右下から受け取る"), hero = CoreMenuArt.TABLET)
            maps.drop(current * mapSlots.size).take(mapSlots.size).forEachIndexed { index, map ->
                card(v, mapSlots[index], 3, 2, "地図${current * mapSlots.size + index + 1}", CoreMenuArt.EXPEDITION, CoreLoopItems.map(map)) { mapDetail(player, map.id) }
            }
            if (maps.isEmpty()) v.canvas.text(8, 56, "地図なし → 右下で入手", CoreUiComponents.MUTED, 160)
            back(v, player, if (journey(player).isEmpty) "手帳" else "元へ") {
                journey(player).pop()?.let { forge(player, it) } ?: journal(player)
            }
            pageButtons(v, current, last) { expeditions(player, tier, it) }
            tile(v, 52, 2, "入手", CoreLoopItems.icon(Material.MAP, "T$tier 地図を受け取る", if (tier == 1) "無料" else "T${tier - 1} 戦利品券1枚"),
                if (unlocked && affordable && room) Tone.PRIMARY else Tone.DISABLED) {
                mutate(v, player, CoreAction.ClaimMap(tier, System.nanoTime()), a.revision) { expeditions(player, tier, current) }
            }
        }
    }

    fun mapDetail(player: Player, id: UUID) {
        val a = game.account(player) ?: return
        val map = a.maps.firstOrNull { it.id == id } ?: return expeditions(player)
        val ready = game.warmMap(player, map)
        view(player, "地図台 / 出発準備", { mapDetail(player, id) }) { v ->
            help(v, player) { mapDetail(player, id) }
            v.canvas.left("遠征の内容", lines("T${map.tier} のクエスト", "目安装備 T${map.tier}", "武器 T${a.weaponTier}", "地図1枚を消費", "道の先にボス", "寄り道は自由", if (ready) "地形の準備完了" else "地形を準備中", "出発後は返却なし"), hero = CoreMenuArt.EXPEDITION)
            v.canvas.right("採取MOD", listOf(Line("${map.modifiers.size} / 3 個")) +
                (if (map.modifiers.isEmpty()) lines("まだ付いていません") else map.modifiers.flatMap { paragraph(CoreLoopItems.modifierName(it)) }) +
                lines("", "石板 所持${a.amount(CoreResource.GATHERING_TABLET)}", "石板1枚で1個付与"))
            card(v, 9, 3, 3, "地図", CoreMenuArt.EXPEDITION, CoreLoopItems.map(map), Tone.SELECTED)
            card(v, 12, 3, 3, "石板付与", CoreMenuArt.TABLET, CoreLoopItems.icon(Material.AMETHYST_SHARD, "採取の石板を1枚使う", "採取量・品質・密集地域のMODを追加"),
                if (map.modifiers.size < 3 && a.amount(CoreResource.GATHERING_TABLET) > 0) Tone.NEUTRAL else Tone.DISABLED) {
                game.applyTablet(player, id, a.revision, onRejected = {
                    if (screens.isCurrent(player, v.screen)) mapDetail(player, id)
                }) { if (screens.isCurrent(player, v.screen)) mapDetail(player, id) }
            }
            card(v, 15, 3, 3, "手元へ", CoreMenuArt.GEAR, CoreLoopItems.icon(Material.FILLED_MAP, "地図をホットバー8へ用意", "インベントリで石板を重ねる操作も使えます")) {
                player.inventory.setItemStack(7, CoreLoopItems.map(map)); player.setHeldItemSlot(7); player.closeInventory()
            }
            back(v, player) { expeditions(player, map.tier) }
            card(v, 51, 3, 1, "出発", CoreMenuArt.EXPEDITION, CoreLoopItems.icon(Material.LIME_DYE, "地図1枚を使って出発"), Tone.PRIMARY) { game.depart(player, id, a.revision) }
        }
    }

    fun workshop(player: Player, tier: Int = game.account(player)?.weaponTier ?: 1) {
        forge(player, (selections[player.uuid] ?: CoreForgeLayout.Selection()).copy(tier = tier.coerceIn(1, 4)))
    }

    private fun forge(player: Player, requested: CoreForgeLayout.Selection) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        val s = requested.copy(tier = requested.tier.coerceIn(1, 4))
        selections[player.uuid] = s
        view(player, "開拓工房 / ${s.tab.label}", { forge(player, s) }) { v ->
            CoreForgeLayout.Tab.entries.forEach { tab ->
                tile(v, tab.slot, 2, if (tab == CoreForgeLayout.Tab.MODS) "刻印" else tab.label,
                    CoreLoopItems.icon(Material.ANVIL, tab.label, "選ぶだけでは素材を消費しません"), if (tab == s.tab) Tone.SELECTED else Tone.NEUTRAL) {
                    forge(player, s.copy(tab = tab, recipe = 0, currency = null, quantity = CoreForgeLayout.Quantity.ONE))
                }
            }
            help(v, player) { forge(player, s) }
            if (s.tab != CoreForgeLayout.Tab.REFINE) CoreGearSlot.entries.forEach { gear ->
                card(v, if (gear == CoreGearSlot.WEAPON) 9 else 12, 3, 1, gear.displayName, gearArt(gear),
                    CoreLoopItems.icon(if (gear == CoreGearSlot.WEAPON) Material.IRON_SWORD else Material.IRON_CHESTPLATE,
                        "対象を${gear.displayName}に変更", *equipment(a, gear).map { it.text }.toTypedArray()),
                    if (s.gear == gear) Tone.SELECTED else Tone.NEUTRAL) { forge(player, s.copy(gear = gear, recipe = 0, currency = null)) }
            }
            back(v, player, if (journey(player).isEmpty) "手帳" else "元へ") {
                journey(player).pop()?.let { forge(player, it) } ?: journal(player)
            }
            when (s.tab) {
                CoreForgeLayout.Tab.ENHANCE -> enhanceTab(v, player, a, s)
                CoreForgeLayout.Tab.REFINE, CoreForgeLayout.Tab.CRAFT -> recipeTab(v, player, a, s)
                CoreForgeLayout.Tab.MODS -> modTab(v, player, a, s)
            }
        }
    }

    private fun costPanel(v: View, a: CoreAccount, recipe: CoreRecipe) {
        val rows = CoreForgeSummary.materials(a, recipe)
        v.canvas.right("必要素材", listOf(Line("所持 / 必要", CoreUiComponents.MUTED)) + rows.flatMap { row ->
            listOf(Line("${if (row.satisfied) "" else "!"}T${row.material.tier} ${stockName(row.material.resource)}",
                    if (row.satisfied) CoreUiComponents.IVORY else CoreUiComponents.RED, materialArt(row.material.resource)),
                Line("${row.owned}/${row.required}",
                    if (row.satisfied) TextColor.color(0x95D7AE) else CoreUiComponents.RED))
        } + if (rows.isEmpty()) lines("消費なし") else emptyList())
    }

    private fun sourceButton(v: View, player: Player, s: CoreForgeLayout.Selection) =
        card(v, 42, 3, 1, "素材へ", CoreMenuArt.STORAGE, CoreLoopItems.icon(Material.BARREL, "必要素材を集める", "精製・補給へ寄り道し、元の操作へ戻れます")) { materials(player, s) }

    private fun execute(v: View, player: Player, label: String, blocked: String?, recipe: CoreRecipe? = null, action: () -> Unit) {
        val a = game.account(player) ?: return
        val lore = listOfNotNull(blocked) + recipe?.costs.orEmpty().map { "${it.key.displayName}: 所持 ${a.amount(it.key)} / 必要 ${it.value}" }
        val art = when (label) {
            "強化する" -> CoreMenuArt.ENHANCE
            "精製する" -> CoreMenuArt.REFINE
            "制作する" -> CoreMenuArt.CRAFT
            "加工する" -> CoreMenuArt.MOD
            else -> CoreMenuArt.STORAGE
        }
        val short = if (label == "加工する") "刻印" else label.removeSuffix("する")
        card(v, 51, 3, 1, if (blocked == null) short else "不可", art,
            CoreLoopItems.icon(if (blocked == null) Material.LIME_DYE else Material.BARRIER, label, *lore.toTypedArray()),
            if (blocked == null) Tone.PRIMARY else Tone.DISABLED) { if (game.requireHub(player)) action() }
    }

    private fun quantity(v: View, selected: CoreForgeLayout.Quantity, maximum: Int, action: (CoreForgeLayout.Quantity) -> Unit) {
        CoreForgeLayout.QUANTITIES.forEach { (slot, q) ->
            val label = when (q) { CoreForgeLayout.Quantity.ONE -> "1"; CoreForgeLayout.Quantity.FIVE -> "5"; CoreForgeLayout.Quantity.MAX -> "全" }
            tile(v, slot, 1, label, CoreLoopItems.icon(Material.PAPER, if (q == CoreForgeLayout.Quantity.MAX) "最大 $maximum 回" else "$label 回",
                "回数を選ぶだけでは消費しません", "完成品数は左に表示"), if (q == selected) Tone.SELECTED else Tone.NEUTRAL) { action(q) }
        }
    }

    private fun enhanceTab(v: View, player: Player, a: CoreAccount, s: CoreForgeLayout.Selection) {
        val summary = CoreForgeSummary.enhancement(a, s)
        val quote = summary.quote
        val maximum = quote.currentLevel == CoreEnhancementCatalog.MAX_LEVEL
        val mode = CoreForgeLayout.enhancementMode(a, s)
        val next = if (s.gear == CoreGearSlot.WEAPON) a.copy(weaponEnhancement = CoreEnhancementState(quote.targetLevel))
            else a.copy(armorEnhancement = CoreEnhancementState(quote.targetLevel))
        val changes = if (s.gear == CoreGearSlot.WEAPON) lines("攻撃 ${CoreWeaponPresentation.damage(a)} → ${CoreWeaponPresentation.damage(next)}",
            "AS補正 ${pct(CoreWeaponPresentation.attackSpeedPercent(a))}%", "→ ${pct(CoreWeaponPresentation.attackSpeedPercent(next))}%")
        else lines("HP ${CoreWeaponPresentation.health(a)} → ${CoreWeaponPresentation.health(next)}")
        v.canvas.left("T${CoreAffixCatalog.gearTier(a, s.gear)} ${s.gear.displayName}", lines(summary.levelLabel) + changes +
            lines(if (maximum) "最大まで強化済み" else "成功率 ${pct(quote.successChancePercent)}%",
                if (maximum || quote.guaranteed) "装備・MOD保護" else "失敗 ${quote.failures}/${quote.pityThreshold}",
                "熟練 ${CoreEnhancementCatalog.masteryRank(a.smithingXp)}/10") +
            if (!maximum) lines(if (quote.guaranteed) "成功確定" else "装備保護", "素材は毎回消費") else emptyList(), hero = gearArt(s.gear))
        costPanel(v, a, quote.recipe)
        tile(v, 15, 3, if (maximum) "最大強化" else "T${CoreEnhancementCatalog.materialTier(quote.targetLevel)}素材",
            CoreLoopItems.icon(Material.PAPER, "強化段階に応じた素材Tier", "装備Tierとは別の段階です"), Tone.DISABLED)
        card(v, 18, 3, 2, "通常", CoreMenuArt.ENHANCE, CoreLoopItems.icon(Material.IRON_INGOT, "通常強化", "追加の精錬触媒を消費しません"),
            if (mode == CoreEnhancementMode.STANDARD) Tone.SELECTED else Tone.NEUTRAL) { forge(player, s.copy(focused = false)) }
        val standard = CoreEnhancementCatalog.quote(a, s.gear)
        card(v, 21, 3, 2, "触媒あり", CoreMenuArt.SHARD, CoreLoopItems.icon(Material.GLOWSTONE_DUST, "精錬触媒を追加", "追加素材を消費し成功率+15ポイント", "費用・成功率を表示してから実行できます"),
            if (maximum || standard.guaranteed) Tone.DISABLED else if (mode == CoreEnhancementMode.FOCUSED) Tone.SELECTED else Tone.NEUTRAL) { forge(player, s.copy(focused = true)) }
        card(v, 24, 3, 2, "装備詳細", CoreMenuArt.GEAR, CoreLoopItems.gear(a, s.gear, v.packed)) { gearMods(player, s.gear) }
        if (quote.blockedReason != null && !maximum) v.canvas.text(8, 92, "素材不足", CoreUiComponents.RED, 106)
        sourceButton(v, player, s)
        execute(v, player, "強化する", quote.blockedReason, quote.recipe) {
            mutate(v, player, CoreAction.EnhanceEquipment(s.gear, mode), a.revision) { forge(player, s) }
        }
    }

    private data class ForgeRecipe(val label: String, val icon: Material, val unit: CoreRecipe, val batches: Boolean = true,
        val build: (Int) -> Pair<CoreRecipe, CoreAction>)
    private fun recipes(a: CoreAccount, s: CoreForgeLayout.Selection): List<ForgeRecipe> =
        if (s.tab == CoreForgeLayout.Tab.REFINE) CoreLoopCatalog.refined.map { (raw, refined) ->
            ForgeRecipe(resourceName(refined), CoreLoopItems.resourceMaterial(refined), CoreLoopCatalog.refine(raw, s.tier)) { count ->
                CoreLoopCatalog.refine(raw, s.tier, count) to CoreAction.Refine(raw, s.tier, count)
            }
        } else buildList {
            val tier = CoreAffixCatalog.gearTier(a, s.gear)
            if (tier < 4) {
                val recipe = if (s.gear == CoreGearSlot.WEAPON) CoreLoopCatalog.weaponUpgrade(tier) else CoreLoopCatalog.armorUpgrade(tier)
                add(ForgeRecipe("T${tier + 1}${s.gear.displayName}", if (s.gear == CoreGearSlot.WEAPON) Material.IRON_SWORD else Material.IRON_CHESTPLATE, recipe, false) {
                    recipe to if (s.gear == CoreGearSlot.WEAPON) CoreAction.UpgradeWeapon else CoreAction.UpgradeArmor
                })
            }
            listOf(CoreResource.POTION, CoreResource.GATHERING_TABLET, CoreResource.WHETSTONE).forEach { resource ->
                add(ForgeRecipe(resourceName(resource), CoreLoopItems.resourceMaterial(resource), CoreLoopCatalog.craft(resource, tier = s.tier)) { count ->
                    CoreLoopCatalog.craft(resource, count, s.tier) to CoreAction.Craft(resource, count, s.tier)
                })
            }
        }

    private fun recipeTab(v: View, player: Player, a: CoreAccount, s: CoreForgeLayout.Selection) {
        val entries = recipes(a, s)
        val selected = s.recipe.coerceIn(0, entries.lastIndex)
        val entry = entries[selected]
        val tierSlot = if (s.tab == CoreForgeLayout.Tab.REFINE) 33 else 15
        if (entry.batches) {
            tile(v, tierSlot, 1, "<", CoreLoopItems.icon(Material.ARROW, "下の素材Tier"), if (s.tier > 1) Tone.NEUTRAL else Tone.DISABLED) { forge(player, s.copy(tier = s.tier - 1)) }
            tile(v, tierSlot + 1, 1, "${s.tier}", CoreLoopItems.icon(Material.PAPER, "使用する素材：T${s.tier}"), Tone.SELECTED)
            tile(v, tierSlot + 2, 1, ">", CoreLoopItems.icon(Material.ARROW, "上の素材Tier"), if (s.tier < 4) Tone.NEUTRAL else Tone.DISABLED) { forge(player, s.copy(tier = s.tier + 1)) }
        } else tile(v, 15, 3, "T${CoreAffixCatalog.gearTier(a, s.gear)}素材", CoreLoopItems.icon(Material.PAPER, "現在の装備Tierの素材で制作"), Tone.DISABLED)
        entries.forEachIndexed { index, e ->
            val outputResource = e.unit.outputs.keys.firstOrNull()?.resource
            val art = outputResource?.let(::materialArt) ?: gearArt(s.gear)
            val label = outputResource?.let(::stockName) ?: "T${CoreAffixCatalog.gearTier(a, s.gear) + 1}"
            val slot = if (s.tab == CoreForgeLayout.Tab.REFINE) REFINE_SLOTS[index] else CoreForgeLayout.RECIPES[index]
            card(v, slot, 3, if (s.tab == CoreForgeLayout.Tab.REFINE) 2 else 1, label, art,
                CoreLoopItems.icon(e.icon, e.unit.displayName, "クリック：選択。まだ素材は使いません"), if (index == selected) Tone.SELECTED else Tone.NEUTRAL) { forge(player, s.copy(recipe = index)) }
        }
        val maximum = if (entry.batches) CoreForgeLayout.maxBatches(a, entry.unit) else if (entry.unit.canAfford(a)) 1 else 0
        val count = if (entry.batches) CoreForgeLayout.batches(s.quantity, maximum) else 1
        val (recipe, action) = entry.build(count)
        val summary = CoreForgeSummary.recipe(a, recipe)
        val output = if (entry.batches) recipe.outputs.flatMap { (key, amount) -> lines("${resourceName(key.resource)} ×$amount", "所持 ${a.amount(key)}") }
            else lines("T${CoreAffixCatalog.gearTier(a, s.gear)} → T${CoreAffixCatalog.gearTier(a, s.gear) + 1}", "強化値とMODを維持", "完成後に自動装備")
        v.canvas.left("完成するもの", output + lines("今回 $count 回", "制作可能 $maximum 回") +
            (summary.blockedReason?.let { paragraph(it, CoreUiComponents.RED) } ?: lines("制作できます")),
            hero = recipe.outputs.keys.firstOrNull()?.let { materialArt(it.resource) } ?: gearArt(s.gear))
        costPanel(v, a, recipe); sourceButton(v, player, s)
        if (entry.batches) quantity(v, s.quantity, maximum) { forge(player, s.copy(quantity = it)) }
        execute(v, player, if (s.tab == CoreForgeLayout.Tab.REFINE) "精製する" else "制作する", summary.blockedReason, recipe) {
            mutate(v, player, action, a.revision) {
                forge(player, if (entry.batches) s.copy(recipe = selected) else s.copy(tab = CoreForgeLayout.Tab.ENHANCE, recipe = 0))
            }
        }
    }

    private fun modTab(v: View, player: Player, a: CoreAccount, s: CoreForgeLayout.Selection) {
        tile(v, 15, 3, "全オーブ", CoreLoopItems.icon(Material.BOOK, "全オーブの効果・条件")) { forgeCurrencies(player, s) }
        val usable = CoreForgeLayout.usableCurrencies(a, s.copy(purpose = CoreForgeLayout.Purpose.ALL))
        check(usable.size <= CoreForgeLayout.RECIPES.size)
        usable.forEachIndexed { index, currency -> card(v, CoreForgeLayout.RECIPES[index], 3, 1, orbName(currency), CoreMenuArt.ORB,
            CoreLoopItems.icon(CoreLoopItems.currencyMaterial(currency), currency.displayName, "所持 ${a.amount(currency)} 個", "クリック：選ぶ / 効果は右に表示"),
            if (currency == s.currency) Tone.SELECTED else Tone.NEUTRAL) { forge(player, s.copy(currency = currency)) } }
        val currency = s.currency
        val reason = currency?.let { CoreCraftingCatalog.canUse(a, s.gear, it) }
        v.canvas.left("加工する装備", equipment(a, s.gear) + lines("", "選ぶだけは無料", "使用時に抽選"), hero = gearArt(s.gear))
        v.canvas.right("加工の内容", if (currency == null) lines("オーブを選ぶ", "右下で加工を実行", "", if (usable.isEmpty()) "使える所持品なし" else "左の装備が対象", "条件は全オーブへ")
            else paragraph(currency.displayName, CoreUiComponents.GOLD) + orbEffect(currency) +
                lines("", "所持 ${a.amount(currency)}", "必要 1個") + (reason?.let { paragraph(it, CoreUiComponents.RED) } ?: lines("取り消し不可")))
        if (usable.isEmpty()) v.canvas.text(8, 74, "使えるオーブがありません", CoreUiComponents.MUTED, 160)
        card(v, 42, 3, 1, "詳細", CoreMenuArt.GEAR, CoreLoopItems.gear(a, s.gear, v.packed)) { gearMods(player, s.gear) }
        if (a.affixStones.isNotEmpty()) tile(v, 47, 3, "旧石交換", CoreLoopItems.icon(Material.AMETHYST_SHARD, "旧刻印石をオーブへ", "所持 ${a.affixStones.size} 個")) { legacyStones(player) }
        execute(v, player, "加工する", if (currency == null) "オーブを選んでください" else reason) {
            if (currency != null) mutate(v, player, CoreAction.CraftEquipment(s.gear, currency), a.revision) { forge(player, s) }
        }
    }

    /** Short, semantically complete menu copy. Full item tooltips keep the catalogue description. */
    private fun orbEffect(currency: CoreCraftingCurrency): List<Line> = when (currency) {
        CoreCraftingCurrency.TRANSMUTATION -> lines("ノーマル専用", "マジックへ昇格", "1〜2個を抽選")
        CoreCraftingCurrency.AUGMENTATION -> lines("マジック専用", "空き枠へ1個追加")
        CoreCraftingCurrency.ALTERATION -> lines("マジック専用", "全MODを消去", "1〜2個を再抽選")
        CoreCraftingCurrency.ALCHEMY -> lines("ノーマル専用", "レアへ昇格", "4〜6個を抽選")
        CoreCraftingCurrency.CHAOS -> lines("レア専用", "全MODを消去", "4〜6個を再抽選")
        CoreCraftingCurrency.REGAL -> lines("マジック専用", "既存MODは保持", "レアへ昇格", "1個を追加抽選")
        CoreCraftingCurrency.EXALTED -> lines("レア専用", "空き枠へ1個追加")
        CoreCraftingCurrency.SCOURING -> lines("全MODを消去", "ノーマルに戻す", "取り消し不可")
        CoreCraftingCurrency.DIVINE -> lines("MOD・Tier保持", "数値を再抽選", "低下する場合も")
        CoreCraftingCurrency.RIFT -> lines("レアへ再構成", "全MODを消去", "4〜6個を再抽選", "元素MODを含む")
        CoreCraftingCurrency.RITUAL -> lines("MOD・Tier保持", "数値を各2回抽選", "高い方を採用", "低下する場合も")
        CoreCraftingCurrency.TRIAL -> lines("レア専用", "空き枠へ1個追加", "数値の上位25%")
    }

    private fun materials(player: Player, s: CoreForgeLayout.Selection) {
        val a = game.account(player) ?: return
        val recipe = if (s.tab == CoreForgeLayout.Tab.ENHANCE) CoreEnhancementCatalog.quote(a, s.gear, CoreForgeLayout.enhancementMode(a, s)).recipe
            else recipes(a, s).let { entries ->
                val e = entries[s.recipe.coerceIn(0, entries.lastIndex)]
                e.build(if (e.batches) CoreForgeLayout.batches(s.quantity, CoreForgeLayout.maxBatches(a, e.unit)) else 1).first
            }
        view(player, "工房 / 素材の調達", { materials(player, s) }) { v ->
            help(v, player) { materials(player, s) }
            v.canvas.left("元の操作", paragraph(recipe.displayName, CoreUiComponents.GOLD) + lines("", "素材を選んで調達", "元へ：操作を復元", "自動実行なし"), hero = CoreMenuArt.FORGE)
            costPanel(v, a, recipe)
            recipe.costs.entries.forEachIndexed { index, (material, required) ->
                val refine = CoreForgeLayout.refineSelection(material, s)
                card(v, mapSlots[index], 3, 2, stockName(material.resource), materialArt(material.resource),
                    CoreLoopItems.icon(CoreLoopItems.resourceMaterial(material.resource), material.displayName, "所持 ${a.amount(material)} / 必要 $required",
                        if (refine != null) "精製して作る" else if (material.resource.raw) "戦利品券と交換する" else "遠征で入手する")) {
                    when {
                        refine != null -> { journey(player).push(s); forge(player, refine) }
                        material.resource.raw -> { journey(player).push(s); supplies(player, material.tier, material.resource) }
                        else -> sourceGuide(player, material, s)
                    }
                }
            }
            back(v, player) { forge(player, s) }
        }
    }

    private fun sourceGuide(player: Player, material: CoreMaterial, s: CoreForgeLayout.Selection) {
        view(player, "工房 / 素材の入手先", { sourceGuide(player, material, s) }) { v ->
            v.canvas.left("探すもの", lines(material.displayName, "", if (material.resource == CoreResource.BOSS_SIGIL) "同Tierのボスを討伐" else "魔物を討伐して入手", "拾うと倉庫に保存"), hero = materialArt(material.resource))
            v.canvas.right("操作を保存", lines("戻ると工房を復元", "地図台へ進んでも", "制作は実行しません"))
            tile(v, 19, 7, "T${material.tier}の地図台へ", CoreLoopItems.icon(Material.MAP, "素材を集める遠征を選ぶ"), Tone.PRIMARY, true) { journey(player).push(s); expeditions(player, material.tier) }
            back(v, player) { materials(player, s) }
        }
    }

    private fun forgeCurrencies(player: Player, s: CoreForgeLayout.Selection, page: Int = 0, selected: CoreCraftingCurrency? = s.currency) {
        val a = game.account(player) ?: return
        val last = (CoreCraftingCurrency.entries.size - 1) / listSlots.size
        val current = page.coerceIn(0, last)
        view(player, "工房 / オーブ図鑑", { forgeCurrencies(player, s, current, selected) }) { v ->
            help(v, player) { forgeCurrencies(player, s, current, selected) }
            v.canvas.left("対象の装備", equipment(a, s.gear) + lines("", "図鑑は未所持も表示", "ここでは消費なし"), hero = gearArt(s.gear))
            v.canvas.right("効果と使用条件", selected?.let { c -> paragraph(c.displayName, CoreUiComponents.GOLD) + orbEffect(c) +
                lines("所持 ${a.amount(c)} 個") + paragraph(CoreCraftingCatalog.canUse(a, s.gear, c) ?: "この装備に使用可能") } ?: lines("オーブを選んで詳細", "使用条件を確認"))
            CoreCraftingCurrency.entries.drop(current * listSlots.size).take(listSlots.size).forEachIndexed { index, c ->
                card(v, listSlots[index], 4, 1, orbName(c), CoreMenuArt.ORB, CoreLoopItems.icon(CoreLoopItems.currencyMaterial(c), c.displayName,
                    "所持 ${a.amount(c)} 個", "クリック：効果と使用条件を見る"), if (c == selected) Tone.SELECTED else Tone.NEUTRAL) {
                    forgeCurrencies(player, s, current, c)
                }
            }
            back(v, player) { forge(player, s) }; pageButtons(v, current, last) { forgeCurrencies(player, s, it, selected) }
            tile(v, 52, 2, "選ぶ", CoreLoopItems.icon(Material.ENCHANTING_TABLE, "選んだオーブを工房へ", "まだオーブは消費しません"),
                if (selected != null && CoreCraftingCatalog.canUse(a, s.gear, selected) == null) Tone.PRIMARY else Tone.DISABLED) {
                forge(player, s.copy(tab = CoreForgeLayout.Tab.MODS, currency = selected))
            }
        }
    }

    private fun compactCount(count: Long): String = if (count < 10_000) count.toString() else "${count / 10_000}万${if (count % 10_000 == 0L) "" else "+"}"
    private fun stockName(resource: CoreResource) = when (resource) {
        CoreResource.STONE_BLOCK -> "切石"
        CoreResource.FIBER -> "繊維"
        CoreResource.LEATHER -> "革"
        CoreResource.GATHERING_TABLET -> "石板"
        CoreResource.COMBAT_TOKEN -> "戦利品"
        else -> resourceName(resource)
    }

    // Compact inventory labels are aliases, never item identities or accounting values.
    // The selected panel and original item tooltip keep the full name and exact count.
    private fun storageName(resource: CoreResource) = when (resource) {
        CoreResource.INGOT -> "金属"
        CoreResource.BOSS_SIGIL -> "討伐"
        CoreResource.COMBAT_TOKEN -> "券"
        CoreResource.POTION -> "薬"
        CoreResource.AFFIX_DUST -> "粉"
        else -> stockName(resource)
    }

    private fun storageCount(count: Long): String = when {
        count < 10_000 -> count.toString()
        count == 1_000_000L -> "百万"
        else -> "${count / 10_000}万"
    }

    fun storage(player: Player, tier: Int = 1, page: Int = 0, selected: Int = 0) {
        val a = game.account(player) ?: return
        val entries = CoreStorageView.entries(a, tier)
        val last = (entries.size - 1).coerceAtLeast(0) / stockSlots.size
        val current = page.coerceIn(0, last)
        val shown = entries.drop(current * stockSlots.size).take(stockSlots.size)
        val chosen = shown.getOrNull(selected.coerceIn(0, (shown.size - 1).coerceAtLeast(0)))
        view(player, "素材倉庫 / T$tier", { storage(player, tier, current, selected) }) { v ->
            tiers(v, tier) { storage(player, it) }; help(v, player) { storage(player, tier, current, selected) }
            v.canvas.left("個人倉庫", lines("所持 ${entries.size}種類", "持っている物だけ", "Tierごとに表示", "通貨・欠片は共通", "工房で直接使用", "万以上は概数", "正確な個数は右へ"), hero = CoreMenuArt.STORAGE)
            val detail = when (chosen) {
                is CoreStorageView.Entry.Material -> lines(chosen.material.displayName, "所持 ${chosen.count}", "", when {
                    chosen.material.resource.raw -> "精製に使う素材"
                    chosen.material.resource in CoreLoopCatalog.refined.values -> "制作・強化用の素材"
                    else -> "工房や冒険で使用"
                })
                is CoreStorageView.Entry.Currency -> paragraph(chosen.currency.displayName, CoreUiComponents.GOLD) + lines("所持 ${chosen.count}", "") + orbEffect(chosen.currency)
                is CoreStorageView.Entry.Fragment -> lines("${chosen.kind.displayName}の欠片", "所持 ${chosen.count}", "", "3個で専用ボスへ", if (a.activeRun == null) "港から挑戦できる" else "港へ帰還後に使用")
                null -> lines("このTierの保管品なし", "採取・討伐で入手")
            }
            val selectedArt = when (chosen) {
                is CoreStorageView.Entry.Material -> materialArt(chosen.material.resource)
                is CoreStorageView.Entry.Currency -> CoreMenuArt.ORB
                is CoreStorageView.Entry.Fragment -> CoreMenuArt.SHARD
                null -> CoreMenuArt.STORAGE
            }
            v.canvas.right("選択した所持品", detail, hero = selectedArt)
            shown.forEachIndexed { index, entry ->
                val name = when (entry) {
                    is CoreStorageView.Entry.Material -> storageName(entry.material.resource)
                    is CoreStorageView.Entry.Currency -> if (entry.currency == CoreCraftingCurrency.RIFT) "裂隙" else orbName(entry.currency)
                    is CoreStorageView.Entry.Fragment -> if (entry.kind == CoreActivityKind.RIFT) "裂隙" else entry.kind.displayName
                }
                val item = when (entry) {
                    is CoreStorageView.Entry.Material -> CoreLoopItems.resource(entry.material, entry.count)
                    is CoreStorageView.Entry.Currency -> CoreLoopItems.currency(entry.currency, entry.count, v.packed)
                    is CoreStorageView.Entry.Fragment -> CoreLoopItems.fragment(entry.kind, entry.count)
                }
                val count = storageCount(entry.count)
                val label = if (CoreMenuCanvas.width("$name $count") <= 52) "$name $count" else "$name$count"
                val art = when (entry) {
                    is CoreStorageView.Entry.Material -> materialArt(entry.material.resource)
                    is CoreStorageView.Entry.Currency -> CoreMenuArt.ORB
                    is CoreStorageView.Entry.Fragment -> CoreMenuArt.SHARD
                }
                card(v, stockSlots[index], 4, 1, label, art, item.withAmount(1), if (entry == chosen) Tone.SELECTED else Tone.NEUTRAL) { storage(player, tier, current, index) }
            }
            back(v, player); pageButtons(v, current, last) { storage(player, tier, it) }
            tile(v, 52, 2, if (chosen is CoreStorageView.Entry.Fragment) "試練" else "工房",
                CoreLoopItems.icon(Material.ANVIL, if (chosen is CoreStorageView.Entry.Fragment) "ボスへの挑戦を選ぶ" else "所持素材を工房で使う"),
                if (a.activeRun == null && chosen != null) Tone.PRIMARY else Tone.DISABLED) {
                when (chosen) {
                    is CoreStorageView.Entry.Currency -> confirmCraft(player, selections[player.uuid]?.gear ?: CoreGearSlot.WEAPON, chosen.currency)
                    is CoreStorageView.Entry.Fragment -> trials(player, tier)
                    is CoreStorageView.Entry.Material -> {
                        val rawIndex = CoreLoopCatalog.refined.keys.indexOf(chosen.material.resource)
                        if (rawIndex >= 0) forge(player, CoreForgeLayout.Selection(tab = CoreForgeLayout.Tab.REFINE, tier = tier, recipe = rawIndex))
                        else workshop(player, tier)
                    }
                    null -> Unit
                }
            }
        }
    }

    fun supplies(player: Player, tier: Int = game.account(player)?.weaponTier ?: 1,
        selected: CoreResource = CoreResource.WOOD, quantity: CoreForgeLayout.Quantity = CoreForgeLayout.Quantity.ONE) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        val unit = CoreLoopCatalog.exchange(selected, tier)
        val maximum = CoreForgeLayout.maxBatches(a, unit)
        val count = CoreForgeLayout.batches(quantity, maximum)
        val recipe = CoreLoopCatalog.exchange(selected, tier, count)
        val summary = CoreForgeSummary.recipe(a, recipe)
        view(player, "補給所 / 素材交換", { supplies(player, tier, selected, quantity) }) { v ->
            tiers(v, tier) { supplies(player, it, selected, quantity) }; help(v, player) { supplies(player, tier, selected, quantity) }
            v.canvas.left("交換で受け取る", lines("T$tier ${selected.displayName}", "完成 ${count * 4} 個", "所持 ${a.amount(selected, tier)}", "今回 $count 回", "交換可能 $maximum 回", "1枚から素材4個") +
                (summary.blockedReason?.let { paragraph(it, CoreUiComponents.RED) } ?: lines("交換できます")), hero = materialArt(selected))
            costPanel(v, a, recipe)
            CoreResource.entries.filter { it.raw }.forEachIndexed { index, raw ->
                card(v, mapSlots[index], 3, 2, stockName(raw), materialArt(raw), CoreLoopItems.icon(CoreLoopItems.resourceMaterial(raw), raw.displayName, "戦利品券1枚 → 素材4個"),
                    if (raw == selected) Tone.SELECTED else Tone.NEUTRAL) { supplies(player, tier, raw, quantity) }
            }
            back(v, player, if (journey(player).isEmpty) "手帳" else "元へ") {
                journey(player).pop()?.let { forge(player, it) } ?: journal(player)
            }
            quantity(v, quantity, maximum) { supplies(player, tier, selected, it) }
            execute(v, player, "交換する", summary.blockedReason, recipe) {
                mutate(v, player, CoreAction.Exchange(selected, tier, count), a.revision) { supplies(player, tier, selected, quantity) }
            }
        }
    }

    private fun tools(player: Player) {
        view(player, "道具箱 / 採取道具", { tools(player) }) { v ->
            help(v, player) { tools(player) }
            v.canvas.left("道具を持つ", lines("集めたい素材を選ぶ", "道具を無料で用意", "ホットバー7番へ", "", "右クリック長押し", "離すと採取を中断"), hero = CoreMenuArt.GATHER)
            v.canvas.right("採取の目印", lines("名前とTierが目印", "木・岩・草・死体", "それぞれ対応道具", "完了時に素材を保存"), hero = CoreMenuArt.WOOD)
            QuestGatheringDiscipline.entries.forEachIndexed { index, d ->
                card(v, mapSlots[index], 3, 2, stockName(gatheringResource(d)), materialArt(gatheringResource(d)), CoreLoopItems.icon(d.toolMaterial, d.toolName, "${d.commonResourceName}の採取用", "ホットバー7番に持ち、画面を閉じます")) {
                    player.inventory.setItemStack(6, d.toolItem()); player.setHeldItemSlot(6); player.closeInventory()
                }
            }
            back(v, player)
        }
    }

    fun mastery(player: Player, discipline: QuestGatheringDiscipline? = null) {
        val m = game.gatheringMastery(player)
        val selected = discipline ?: QuestGatheringDiscipline.entries.first()
        view(player, "採取 / 育成", { mastery(player, selected) }) { v ->
            help(v, player) { mastery(player, selected) }
            v.canvas.left("${selected.displayName}の成長", lines("Lv ${m.level(selected)}", "経験値 ${m.experience(selected)}", "未使用 ${m.availableTreePoints(selected)}pt", "", "Lv3ごとにポイント", "右の能力に使えます"), hero = materialArt(gatheringResource(selected)))
            val nodes = listOf(QuestGatheringMasteryNode.STEADY_HANDS, QuestGatheringMasteryNode.DEEP_YIELD, QuestGatheringMasteryNode.ABUNDANCE_KEYSTONE)
            v.canvas.right("能力の効果", lines("手つき：時間-4tick", "深掘り：素材+1", "豊穣：素材+2", "豊穣は時間+8tick", "", "左から順番に取得", "手つき → 深掘り", "→ 豊穣"))
            QuestGatheringDiscipline.entries.forEachIndexed { index, d ->
                card(v, CoreForgeLayout.RECIPES[index], 3, 1, if (d == QuestGatheringDiscipline.HERBALISM) "採草" else d.displayName, materialArt(gatheringResource(d)), CoreLoopItems.icon(d.toolMaterial, "${d.displayName} Lv${m.level(d)}"),
                    if (d == selected) Tone.SELECTED else Tone.NEUTRAL) { mastery(player, d) }
            }
            nodes.forEachIndexed { index, node ->
                val owned = node in m.unlockedNodes(selected)
                val prior = node.prerequisite == null || node.prerequisite in m.unlockedNodes(selected)
                val short = listOf("手つき", "深掘り", "豊穣")[index]
                tile(v, 36 + index * 3, 3, if (owned) "$short 済" else "$short${node.cost}", CoreLoopItems.icon(Material.OAK_SAPLING, node.displayName, node.description, "必要 ${node.cost} ポイント", if (prior) "前提を達成しています" else "先に${node.prerequisite.displayName}を取得してください"),
                    if (owned || !prior || m.availableTreePoints(selected) < node.cost) Tone.DISABLED else Tone.PRIMARY) {
                    game.unlockMastery(player, selected, node); mastery(player, selected)
                }
            }
            back(v, player)
            tile(v, 51, 3, "道具箱", CoreLoopItems.icon(Material.CHEST, "無料で採取道具を用意")) { tools(player) }
        }
    }

    private fun confirmReturn(player: Player) {
        view(player, "遠征 / 帰還の確認", { confirmReturn(player) }) { v ->
            v.canvas.left("港へ帰還", lines("今の遠征を終了", "このマップは閉鎖", "同じ地図へ戻れません"), hero = CoreMenuArt.RETURN)
            v.canvas.right("獲得物は保持", lines("採取素材は保存済み", "未回収の戦利品も", "まとめて保管します"), hero = CoreMenuArt.STORAGE)
            card(v, 12, 3, 3, "探索再開", CoreMenuArt.EXPEDITION, CoreLoopItems.icon(Material.MAP, "閉じて探索を続ける")) { player.closeInventory() }
            back(v, player)
            tile(v, 51, 3, "帰還する", CoreLoopItems.icon(Material.COMPASS, "遠征を終了し港へ帰還", "このマップへは戻れません"), Tone.PRIMARY) { game.returnToHarbor(player) }
        }
    }

    fun affixes(player: Player, page: Int = 0, selected: CoreGearSlot = selections[player.uuid]?.gear ?: CoreGearSlot.WEAPON) {
        val a = game.account(player) ?: return
        if (a.activeRun != null) return gearMods(player, selected)
        forge(player, (selections[player.uuid] ?: CoreForgeLayout.Selection()).copy(tab = CoreForgeLayout.Tab.MODS, gear = selected, currency = null))
    }

    fun gearMods(player: Player, gear: CoreGearSlot, selected: Int = 0) {
        val a = game.account(player) ?: return
        val mods = a.equippedAffixes.filter { it.gear == gear }.sortedBy { it.index }
        val chosen = mods.getOrNull(selected)
        view(player, "装備 / MOD詳細", { gearMods(player, gear, selected) }) { v ->
            CoreGearSlot.entries.forEachIndexed { index, g -> card(v, index * 3, 3, 1, g.displayName, gearArt(g),
                CoreLoopItems.icon(Material.IRON_SWORD, "${g.displayName}を確認"), if (g == gear) Tone.SELECTED else Tone.NEUTRAL) { gearMods(player, g) } }
            help(v, player) { gearMods(player, gear, selected) }
            v.canvas.left("現在の装備", equipment(a, gear) + lines("アイテムLv ${1 + (CoreAffixCatalog.gearTier(a, gear) - 1) * 15}", "内部Tier T${CoreAffixCatalog.gearTier(a, gear)}", "下の装備に全情報"), hero = gearArt(gear))
            v.canvas.right("選択したMOD", chosen?.let {
                val model = CoreLoopItems.affixModel(it.stone)
                paragraph(model.name, CoreUiComponents.GOLD) + paragraph(model.effect) + lines("", "ロール範囲", model.range, "品質 ${model.qualityPercent}%", "内部Tier T${it.stone.tier}")
            } ?: lines("MODがありません", "オーブで昇格・追加", "ノーマル 0枠", "マジック 2枠", "レア 6枠"))
            mods.forEachIndexed { index, installed ->
                val model = CoreLoopItems.affixModel(installed.stone)
                val short = CoreAffixCatalog.definition(installed.stone)?.displayName?.removeSuffix("の刻印石") ?: "MOD ${index + 1}"
                card(v, listSlots[index], 4, 1, short, CoreMenuArt.MOD, CoreLoopItems.icon(Material.ENCHANTED_BOOK, model.name, model.effect, "範囲 ${model.range}", "品質 ${model.qualityPercent}% / 内部Tier T${installed.stone.tier}"),
                    if (index == selected) Tone.SELECTED else Tone.NEUTRAL) { gearMods(player, gear, index) }
            }
            v.items[40] = CoreLoopItems.gear(a, gear, v.packed)
            back(v, player) { if (a.activeRun == null) forge(player, (selections[player.uuid] ?: CoreForgeLayout.Selection()).copy(gear = gear)) else journal(player) }
            tile(v, 51, 3, "加工へ", CoreLoopItems.icon(Material.ENCHANTING_TABLE, "この装備のMODを加工する", "港で使用できます"),
                if (a.activeRun == null) Tone.PRIMARY else Tone.DISABLED) { affixes(player, selected = gear) }
        }
    }

    fun confirmCraft(player: Player, gear: CoreGearSlot, currency: CoreCraftingCurrency) =
        forge(player, (selections[player.uuid] ?: CoreForgeLayout.Selection()).copy(tab = CoreForgeLayout.Tab.MODS, gear = gear, currency = currency))

    private fun legacyStones(player: Player, page: Int = 0) {
        val a = game.account(player) ?: return
        val last = (a.affixStones.size - 1).coerceAtLeast(0) / listSlots.size
        val current = page.coerceIn(0, last)
        view(player, "旧刻印石 / 交換", { legacyStones(player, current) }) { v ->
            v.canvas.left("旧形式の所持品", lines("所持 ${a.affixStones.size} 個", "選んで交換内容へ", "選ぶだけでは未消費"), hero = CoreMenuArt.SHARD)
            v.canvas.right("交換について", lines("旧石のMODは消え", "オーブに変わります", "改変は石のTier分", "錬金は1個", "交換は取り消し不可"))
            a.affixStones.drop(current * listSlots.size).take(listSlots.size).forEachIndexed { index, stone ->
                card(v, listSlots[index], 4, 1, "旧石${current * listSlots.size + index + 1}", CoreMenuArt.SHARD, CoreLoopItems.stone(stone, v.packed)) {
                    stoneDetail(player, stone.id, selections[player.uuid]?.gear)
                }
            }
            back(v, player) { affixes(player) }; pageButtons(v, current, last) { legacyStones(player, it) }
        }
    }

    fun stoneDetail(player: Player, id: UUID, preferred: CoreGearSlot? = null) {
        val a = game.account(player) ?: return
        val stone = a.affixStones.firstOrNull { it.id == id } ?: return affixes(player)
        view(player, "旧刻印石 / 交換確認", { stoneDetail(player, id, preferred) }) { v ->
            v.canvas.left("消費するもの", lines("T${stone.tier} 旧刻印石1個", "元のMODは失われる", "交換は取り消せない"), hero = CoreMenuArt.SHARD)
            v.canvas.right("受け取るもの", lines("改変のオーブ", "${stone.tier} 個", "錬金のオーブ", "1 個"), hero = CoreMenuArt.ORB)
            v.items[22] = CoreLoopItems.stone(stone, v.packed)
            back(v, player) { legacyStones(player) }
            execute(v, player, "交換する", null) { mutate(v, player, CoreAction.ConvertLegacyStone(id), a.revision) { affixes(player, selected = preferred ?: CoreGearSlot.WEAPON) } }
        }
    }

    private fun displayHelp(player: Player, returnTo: () -> Unit) {
        view(player, "画面の見方", { displayHelp(player, returnTo) }) { v ->
            v.canvas.left("操作", lines("枠全体をクリック", "光る枠は選択中", "緑は実行ボタン", "灰色は使用不可", "選択だけでは未消費", "右下で確定します", "ESCで閉じられます"), hero = CoreMenuArt.HELP)
            v.canvas.right("表示", lines("左：対象と変化", "右：費用や詳細", "所持 / 必要の順", "赤は不足している物", "", "装備の詳しい数値は", "装備詳細へ残しています"))
            v.canvas.text(8, 20, "左右が見切れるとき", CoreUiComponents.GOLD, 160)
            v.canvas.text(8, 38, "設定 → ビデオ設定", CoreUiComponents.IVORY, 160)
            v.canvas.text(8, 56, "GUI倍率を1段下げる", CoreUiComponents.IVORY, 160)
            v.canvas.text(8, 92, "クライアントMODは不要", CoreUiComponents.MUTED, 160)
            back(v, player, action = returnTo)
        }
    }

    fun guide(player: Player, section: Int = 0) {
        val current = section.coerceIn(0, 3)
        view(player, "冒険の手帳 / 遊び方", { guide(player, current) }) { v ->
            listOf("一周", "戦闘", "採取", "育成").forEachIndexed { index, name ->
                tile(v, index * 2, 2, name, CoreLoopItems.icon(Material.BOOK, name), if (index == current) Tone.SELECTED else Tone.NEUTRAL) { guide(player, index) }
            }
            help(v, player) { guide(player, current) }
            val content = when (current) {
                0 -> lines("地図台でT1を入手", "地図を選んで出発", "道の先のボスを討伐", "素材と地図を獲得", "港へ帰還して工房へ", "装備を更新して次へ") to
                    lines("採取と雑魚戦は自由", "寄り道で特別な報酬", "採取素材は自動保存", "死亡しても保持", "途中帰還もできます")
                1 -> lines("左クリックで通常攻撃", "3段階の大剣コンボ", "右クリックで踏み込み", "Fキーで回避", "2〜4番のスキルを", "右クリックで発動") to
                    lines("赤い予兆から離れる", "5番：回復薬", "砥石：3分間攻撃強化", "マナ・再使用はHUDへ", "敵を倒してオーブ入手", "近づくと戦利品回収")
                2 -> lines("道具箱で道具を選ぶ", "木・岩・草・死体へ", "右クリック長押し", "離すと採取中断", "対象の表示で進行確認", "完了すると素材を保存") to
                    lines("マップの石板MODで", "狙う素材を増やせます", "密集地域を探そう", "経験で採取育成を解放", "高Tierの素材は", "同Tierのマップから")
                else -> lines("採取素材を精製する", "精製素材で装備制作", "武器と防具はT1〜4", "+強化は0〜30", "オーブでランダムMOD", "強化値とMODは保持") to
                    lines("素材は倉庫から使用", "足りない素材は補給へ", "戦利品券と交換可能", "精製から元の操作へ", "戻って続けられます", "欠片3個で専用ボスへ")
            }
            v.canvas.left("基本の流れ", content.first); v.canvas.right("覚えておくこと", content.second)
            card(v, 19, 3, 3, "道具箱", CoreMenuArt.GATHER, CoreLoopItems.icon(Material.WOODEN_AXE, "採取道具を選ぶ")) { tools(player) }
            card(v, 23, 3, 3, "手帳へ", CoreMenuArt.HELP, CoreLoopItems.icon(Material.NETHER_STAR, "冒険の手帳へ戻る"), Tone.PRIMARY) { journal(player) }
            back(v, player)
        }
    }

    private fun bossName(kind: CoreActivityKind) = when (kind) {
        CoreActivityKind.RIFT -> "灰燼の王"
        CoreActivityKind.RITUAL -> "氷獄の巨兵"
        CoreActivityKind.TRIAL -> "嵐の司祭"
    }
    fun trials(player: Player, tier: Int = game.account(player)?.unlockedMapTier ?: 1) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        view(player, "境界の試練 / T$tier", { trials(player, tier) }) { v ->
            tiers(v, tier) { trials(player, it) }; help(v, player) { trials(player, tier) }
            v.canvas.left("ボスへの挑戦", lines("欠片3個で入場", "通常マップとは別", "解放 T1〜${a.unlockedMapTier}", "", "選ぶと確認画面へ", "まだ消費しません"), hero = CoreMenuArt.BOSS)
            v.canvas.right("欠片の入手先", lines("遠征中の寄り道で", "同じ欠片を3個収集", "報酬は専用オーブ", "高揚と討伐証も入手", "死亡・帰還で返却なし"), hero = CoreMenuArt.SHARD)
            CoreActivityKind.entries.forEachIndexed { index, kind ->
                card(v, 9 + index * 3, 3, 3, kind.displayName, CoreMenuArt.BOSS,
                    CoreLoopItems.icon(Material.ECHO_SHARD, bossName(kind), "${kind.displayName}の欠片 所持 ${a.amount(kind)} / 必要3個", "クリック：挑戦内容の確認")) { confirmTrial(player, kind, tier) }
                v.canvas.text(8 + index * 54, 92, "${compactCount(a.amount(kind))}/3", if (a.amount(kind) >= 3) CoreUiComponents.IVORY else CoreUiComponents.RED, 52)
            }
            back(v, player)
        }
    }
    private fun confirmTrial(player: Player, kind: CoreActivityKind, tier: Int) {
        val a = game.account(player) ?: return
        val available = a.amount(kind) >= 3 && tier <= a.unlockedMapTier
        view(player, "試練 / 出発確認", { confirmTrial(player, kind, tier) }) { v ->
            v.canvas.left("${bossName(kind)}", lines("T$tier の専用ボス", "${kind.displayName}の欠片", "所持 ${a.amount(kind)}", "必要3個", "一度きりの挑戦", "敗北・帰還で返却なし", "準備失敗時だけ返却"), hero = CoreMenuArt.BOSS)
            v.canvas.right("討伐の報酬", paragraph(kind.currency.displayName, CoreUiComponents.GOLD) + lines("2個", "高揚のオーブ 1個", "討伐証", "", if (available) "挑戦できます" else if (tier > a.unlockedMapTier) "Tierが未解放" else "欠片が不足しています"))
            v.items[22] = CoreLoopItems.icon(Material.END_PORTAL_FRAME, bossName(kind))
            back(v, player) { trials(player, tier) }
            card(v, 51, 3, 1, "挑戦", CoreMenuArt.TRIAL, CoreLoopItems.icon(Material.LIME_DYE, "欠片3個を使って挑戦する"), if (available) Tone.PRIMARY else Tone.DISABLED) { game.departTrial(player, kind, tier, a.revision) }
        }
    }
}
