package dev.projects.server.coreloop

import dev.projects.server.coreloop.ui.*
import dev.projects.server.coreloop.adventure.*
import dev.projects.server.coreloop.ui.CoreMenuCanvas.Line
import dev.projects.server.coreloop.ui.CoreMenuCanvas.TextStyle
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
    private val stockSlots = (9..44).toList()
    internal companion object {
        val REFINE_SLOTS = listOf(9, 12, 15, 27, 30)
        val ENHANCE_FOCUS_SLOTS = listOf(18, 19, 20, 21, 22, 23, 27, 28, 29, 30, 31, 32, 36, 37, 38, 39, 40, 41)
        const val ENHANCE_STANDARD = 24
        const val ENHANCE_CATALYST = 33
        const val ENHANCE_DETAIL = 15
    }
    fun click(event: InventoryPreClickEvent): Boolean = screens.click(event)
    fun forget(playerId: UUID) { screens.forget(playerId); selections.remove(playerId); journeys.remove(playerId) }
    fun refreshTheme(player: Player) = screens.refresh(player)
    private fun journey(player: Player) = journeys.computeIfAbsent(player.uuid) { CoreForgeJourney() }

    private fun view(player: Player, title: String, redraw: () -> Unit, nativeChest: Boolean = false, build: (View) -> Unit) {
        val v = View(CoreMenuCanvas(title), game.packed(player))
        build(v)
        if (nativeChest) (0..8).plus(45..53).forEach { slot ->
            if (v.items[slot].isAir) v.items[slot] = CoreLoopItems.icon(Material.BROWN_STAINED_GLASS_PANE, " ")
        }
        inspect?.invoke(v.canvas)
        // Without the optional pack, no private glyph or invisible control is sent.
        // The always-visible panel's original text remains available in the central details book.
        if (!v.packed && !nativeChest) v.items[8] = CoreLoopItems.icon(Material.BOOK, "画面の詳細 / ヘルプ", *v.canvas.fallbackLines().toTypedArray())
        v.items.indices.forEach { slot ->
            val item = v.items[slot]
            if (!item.isAir && item.get(DataComponents.TOOLTIP_STYLE) == null)
                v.items[slot] = CoreLoopItems.menuSkin(item, v.packed)
        }
        v.screen = CoreMenuInventory.Screen(if (v.packed && !nativeChest) v.canvas.render() else CoreUiComponents.inventoryTitle(title, false), v.items, v.actions, redraw)
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
    /** A single piece on the anvil, not another rectangular menu card. */
    private fun enhancementFocus(v: View, player: Player, a: CoreAccount, gear: CoreGearSlot, caption: String) {
        val display = CoreLoopItems.gear(a, gear, v.packed).withAmount(1).withGlowing(false)
        ENHANCE_FOCUS_SLOTS.forEach { slot ->
            check(v.occupied.add(slot)) { "Enhancement subject overlaps slot $slot" }
            v.items[slot] = if (v.packed) CoreUiItemSkin.blank(display, true) else display
            v.actions[slot] = { gearMods(player, gear) }
        }
        v.canvas.focus(gearArt(gear), caption)
    }
    private fun gatheringResource(discipline: QuestGatheringDiscipline) = CoreResource.entries.first { it.raw && it.displayName == discipline.commonResourceName }
    private fun back(v: View, player: Player, label: String = "戻る", compact: Boolean = false, action: () -> Unit = { journal(player) }) =
        tile(v, 45, if (compact) 1 else 2, if (compact) "←" else label, CoreLoopItems.icon(Material.ARROW, label), action = action)
    private fun help(v: View, player: Player, returnTo: () -> Unit) =
        tile(v, 8, 1, "?", CoreLoopItems.icon(Material.BOOK, "画面の見方", "左右：選択内容と費用 / 中央：操作", "表示サイズ・操作の説明")) { displayHelp(player, returnTo) }
    private fun lines(vararg text: String): List<Line> = text.flatMap { CoreMenuCanvas.wrap(it).map { part -> Line(part) } }
    private fun emphasis(text: String, color: TextColor = CoreUiComponents.GOLD) = Line(text, color, style = TextStyle.EMPHASIS)
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
        if (!a.journey.chosen) { career(player); return }
        if (game.isDeparting(player)) { player.sendMessage(CoreLoopItems.text("遠征を準備しています。しばらくお待ちください。")); return }
        journey(player).clear()
        val run = a.activeRun
        if (run?.dungeon != null && game.dungeonView(player) != null) { dungeonRun(player); return }
        view(player, if (run == null) "開拓港 / 手帳" else "遠征 / 手帳", { journal(player) }) { v ->
            help(v, player) { journal(player) }
            tile(v, 0, 8, "${a.journey.job.displayName} Lv${a.journey.level} / 成長と職業", CoreLoopItems.icon(Material.EXPERIENCE_BOTTLE, CoreJourneyRules.next(a))) { career(player) }
            v.canvas.left("旅の装備", equipment(a, CoreGearSlot.WEAPON) + lines("", "防具 T${a.armorTier} +${a.armorEnhancement.level}",
                "HP ${CoreWeaponPresentation.health(a)}"), hero = CoreMenuArt.WEAPON)
            if (run == null) {
                v.canvas.right("次の遠征", listOf(emphasis("T1〜${a.unlockedMapTier}")) + lines("挑戦できる地域", "", "地図 ${a.maps.size}枚", "道の先に待つボス", "寄り道で見つかる素材"), hero = CoreMenuArt.EXPEDITION)
                card(v, 9, 3, 3, "遠征", CoreMenuArt.EXPEDITION, CoreLoopItems.icon(Material.CARTOGRAPHY_TABLE, "地図台から遠征", "地図を選ぶ → 調整 → 出発", "T1の地図は無料で何度でも入手できます"), Tone.PRIMARY) { expeditions(player) }
                card(v, 12, 3, 3, "工房", CoreMenuArt.FORGE, CoreLoopItems.icon(Material.ANVIL, "装備工房", "強化・精製・制作・MOD加工")) { workshop(player) }
                card(v, 15, 3, 3, "保管庫", CoreMenuArt.STORAGE, CoreLoopItems.icon(Material.BARREL, "素材倉庫", "持っている素材と正確な所持数")) { storage(player) }
                card(v, 36, 3, 1, "装備庫", CoreMenuArt.GEAR, CoreLoopItems.icon(Material.IRON_SWORD, "作った装備を使う・出品する・納品する")) { equipmentStock(player) }
                card(v, 39, 3, 1, "採取", CoreMenuArt.GATHER, CoreLoopItems.icon(Material.OAK_SAPLING, "採取の心得・道具")) { professions(player) }
                card(v, 42, 3, 1, "深殿", CoreMenuArt.TRIAL, CoreLoopItems.icon(Material.END_PORTAL_FRAME, "自動生成ダンジョン・専用ボス・仲間と挑戦")) { dungeons(player) }
                card(v, 45, 3, 1, "市場", CoreMenuArt.STORAGE, CoreLoopItems.icon(Material.GOLD_NUGGET, "素材・装備をプレイヤーと売買", "銀貨 ${a.silver}枚")) { supplies(player) }
                card(v, 48, 3, 1, "目標", CoreMenuArt.HELP, CoreLoopItems.icon(Material.BOOK, CoreJourneyRules.next(a))) { career(player) }
            } else {
                v.canvas.right("遠征の記録", listOf(emphasis(if (run.bossDefeated) "ボス討伐！" else "T${run.map.tier} を探索中")) + lines("寄り道は自由", "", game.sessionSummary(player)), hero = CoreMenuArt.EXPEDITION)
                card(v, 9, 3, 3, "探索", CoreMenuArt.EXPEDITION, CoreLoopItems.icon(Material.MAP, "画面を閉じて探索を続ける"), Tone.PRIMARY) { player.closeInventory() }
                card(v, 12, 3, 3, "帰還", CoreMenuArt.RETURN, CoreLoopItems.icon(Material.COMPASS, "帰還の確認へ", "今のマップには戻れなくなります")) { confirmReturn(player) }
                card(v, 15, 3, 3, "獲得品", CoreMenuArt.STORAGE, CoreLoopItems.icon(Material.BARREL, "倉庫へ保存された戦利品", "採取素材は自動保存され、死亡・帰還しても保持されます")) { storage(player, run.map.tier) }
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
            tiers(v, tier) { expeditions(player, it) }
            tile(v, 8, 1, "採", CoreLoopItems.icon(Material.OAK_SAPLING, "討伐不要の採取地図")) { surveyMaps(player, tier) }
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
            v.canvas.left("遠征の内容", lines("T${map.tier} / 敵Lv${map.level}", "冒険Lv${a.journey.level}", "武器 T${a.weaponTier}", "地図1枚を消費", "道の先にボス", "寄り道は自由", if (ready) "地形の準備完了" else "地形を準備中", "出発後は返却なし"), hero = CoreMenuArt.EXPEDITION)
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

    fun career(player: Player) {
        val a = game.account(player) ?: return
        view(player, "旅の始まり / 成長と職業", { career(player) }) { v ->
            v.canvas.left("${a.journey.job.displayName} Lv${a.journey.level}",
                lines("経験 ${a.journey.xp}", if (a.journey.level < 40) "次まで ${CoreJourneyRules.threshold(a.journey.level + 1) - a.journey.xp}" else "冒険Lv最大", "T1 Lv1〜10", "T2 Lv11〜20", "T3 Lv21〜30", "T4 Lv31〜40", "技能解放 Lv1/4/8"), hero = CoreMenuArt.GEAR)
            v.canvas.right("次の一歩", paragraph(CoreJourneyRules.next(a)) + lines("", "港で職業を変更可能", "装備と経験は保持", "採取でも経験を獲得"), hero = CoreMenuArt.EXPEDITION)
            CoreClass.entries.forEachIndexed { i, job ->
                val slot = listOf(9, 12, 15, 27)[i]
                val eligible = a.activeRun == null && (job != CoreClass.STARWEAVER || ((a.journey.job == CoreClass.MAGE || a.journey.job == CoreClass.STARWEAVER) && a.journey.level >= 20 && a.amount(CoreResource.BOSS_SIGIL, 2) >= 2))
                card(v, slot, 3, if (i == 3) 1 else 2, if (job == CoreClass.STARWEAVER) "星織り" else job.displayName, if (job.magic) CoreMenuArt.ORB else CoreMenuArt.WEAPON,
                    CoreLoopItems.icon(if (job.magic) Material.AMETHYST_SHARD else Material.IRON_SWORD, job.displayName, job.description,
                        if (job == CoreClass.STARWEAVER) "メイジLv20 + T2討伐証2枚（非消費）" else "港でいつでも選び直せます"),
                    if (!eligible) Tone.DISABLED else if (a.journey.chosen && job == a.journey.job) Tone.SELECTED else Tone.PRIMARY) {
                    mutate(v, player, CoreAction.ChooseClass(job), a.revision) { career(player) }
                }
            }
            card(v, 30, 3, 1, "武器型", CoreMenuArt.FORGE) { weaponBases(player, CoreForgeLayout.Selection(tab = CoreForgeLayout.Tab.CRAFT, tier = a.weaponTier)) }
            card(v, 33, 3, 1, "鍛錬", CoreMenuArt.WEAPON) { temper(player) }
            a.journey.job.skills.forEachIndexed { i, name ->
                tile(v, 36 + i * 3, 3, name.replace("踏み込み斬り", "踏込斬り"), CoreLoopItems.icon(Material.PAPER, name, "ホットバー${i + 2}番 / Lv${listOf(1, 4, 8)[i]}で解放"),
                    if (CoreJourneyRules.skillUnlocked(a, i)) Tone.SELECTED else Tone.DISABLED)
            }
            back(v, player, "手帳")
            tile(v, 48, 3, "操作ガイド", CoreLoopItems.icon(Material.BOOK, "左クリック：通常攻撃 / 右：最初のスキル / F：回避")) { guide(player) }
            tile(v, 51, 3, if (a.activeRun == null) "遠征へ" else "探索へ", tone = if (a.journey.chosen) Tone.PRIMARY else Tone.DISABLED) {
                if (a.activeRun != null) player.closeInventory() else expeditions(player)
            }
        }
    }

    private fun weaponBases(player: Player, s: CoreForgeLayout.Selection) {
        val a = game.account(player) ?: return
        view(player, "工房 / 武器の型", { weaponBases(player, s) }) { v ->
            v.canvas.left("同じTierの選択", lines("T1から全型を制作", "型の特徴は固定", "MODは後から抽選", "強化と品質は保持", "職業で使える系統が変化"), hero = CoreMenuArt.WEAPON)
            v.canvas.right("制作と購入", lines("採取素材から制作", "上位製造に低Tier材", "良い型を選んで厳選", "今の職業", a.journey.job.displayName), hero = CoreMenuArt.FORGE)
            CoreWeaponBase.entries.forEachIndexed { i, base ->
                card(v, mapSlots[i], 3, 2, base.displayName, CoreMenuArt.WEAPON,
                    CoreLoopItems.icon(Material.IRON_SWORD, base.displayName, base.detail, "使用可：${CoreClass.entries.filter(base::usable).joinToString { it.displayName }}"),
                    if (s.base == base) Tone.SELECTED else Tone.NEUTRAL) { forge(player, s.copy(tab = CoreForgeLayout.Tab.CRAFT, gear = CoreGearSlot.WEAPON, base = base, recipe = 0)) }
            }
            back(v, player) { career(player) }
        }
    }

    private fun temper(player: Player, slot: CoreGearSlot = CoreGearSlot.WEAPON) {
        val a = game.account(player) ?: return
        val quote = runCatching { CoreJourneyRules.temper(a, slot) }
        view(player, "工房 / 装備レベル鍛錬", { temper(player, slot) }) { v ->
            val level = CoreJourneyRules.itemLevel(CoreEconomy.identity(a, slot), CoreAffixCatalog.gearTier(a, slot))
            v.canvas.left("${slot.displayName} Lv$level", lines("同Tier内で最大+9段階", "MODと強化はそのまま", "基礎性能を少しずつ向上", "冒険Lv+2まで鍛錬可能"), hero = gearArt(slot))
            quote.getOrNull()?.let { costPanel(v, a, it) } ?: v.canvas.right("このTierは完成", lines("次のTierでも好きな型を", "制作・購入できます"))
            card(v, 9, 3, 2, "武器", CoreMenuArt.WEAPON) { temper(player, CoreGearSlot.WEAPON) }
            card(v, 12, 3, 2, "防具", CoreMenuArt.ARMOR) { temper(player, CoreGearSlot.ARMOR) }
            val ready = a.activeRun == null && !CoreEconomy.broken(a, slot) && quote.getOrNull()?.canAfford(a) == true && (a.journey.legacy || level + 1 <= a.journey.level + 2)
            card(v, 15, 3, 2, if (ready) "Lvを上げる" else "鍛錬不可", CoreMenuArt.FORGE, tone = if (ready) Tone.PRIMARY else Tone.DISABLED) {
                mutate(v, player, CoreAction.TemperEquipment(slot), a.revision) { temper(player, slot) }
            }
            back(v, player) { career(player) }
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
                    if (s.gear == gear) Tone.SELECTED else Tone.NEUTRAL) {
                    if (gear == CoreGearSlot.WEAPON && s.gear == gear && s.tab == CoreForgeLayout.Tab.CRAFT) weaponBases(player, s)
                    else forge(player, s.copy(gear = gear, recipe = 0, currency = null))
                }
            }
            back(v, player, if (journey(player).isEmpty) "手帳" else "元へ", compact = s.tab == CoreForgeLayout.Tab.ENHANCE) {
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
        v.canvas.right("必要素材", rows.flatMap { row ->
            listOf(Line("T${row.material.tier} ${stockName(row.material.resource)} ×${row.required}",
                    if (row.satisfied) CoreUiComponents.IVORY else CoreUiComponents.RED, materialArt(row.material.resource))) +
                paragraph(if (row.satisfied) "足りる 所持${row.owned}" else "あと${row.missing}個 所持${row.owned}",
                    if (row.satisfied) TextColor.color(0xB6CA91) else CoreUiComponents.RED)
        } + if (rows.isEmpty()) lines("消費なし") else emptyList())
    }

    private fun sourceButton(v: View, player: Player, s: CoreForgeLayout.Selection) =
        card(v, 42, 3, 1, "入手先", CoreMenuArt.STORAGE, CoreLoopItems.icon(Material.BARREL, "必要素材の入手先", "精製・補給へ寄り道し、元の操作へ戻れます")) { materials(player, s) }

    private fun execute(v: View, player: Player, label: String, blocked: String?, recipe: CoreRecipe? = null, action: () -> Unit) {
        val a = game.account(player) ?: return
        val lore = listOfNotNull(blocked) + recipe?.costs.orEmpty().map { "${it.key.displayName}: 所持 ${a.amount(it.key)} / 必要 ${it.value}" } +
            if (label == "強化する") listOf("+15からは失敗時に破損する可能性があります", "強化値・MODは維持 / 破損時は装備1個で修理", "素材は成功・失敗にかかわらず毎回消費します") else emptyList()
        val short = if (label == "加工する") "刻印する" else label
        if (blocked == null) tile(v, 51, 3, short,
            CoreLoopItems.icon(Material.LIME_DYE, label, *lore.toTypedArray()), Tone.PRIMARY) { if (game.requireHub(player)) action() }
        else {
            val reason = when {
                "破損" in blocked -> "要修理"
                "最大強化" in blocked -> "最大強化"
                "保管上限" in blocked -> "保管上限"
                "不足" in blocked || "足りません" in blocked -> "素材不足"
                "選んで" in blocked -> "選択待ち"
                "拠点" in blocked -> "港で使用"
                else -> "条件未達"
            }
            val maximum = "最大強化" in blocked
            tile(v, 51, 3, if ("破損" in blocked) "要修理" else if (label == "強化する" && !maximum) "強化不可" else reason,
                CoreLoopItems.icon(Material.BARRIER, "$label：$reason", *lore.toTypedArray()),
                if (maximum) Tone.DISABLED else Tone.DANGER)
        }
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
        val changes = if (s.gear == CoreGearSlot.WEAPON) listOf(
            Line("攻撃力", CoreUiComponents.MUTED),
            emphasis(if (maximum) "${CoreWeaponPresentation.damage(a)}" else "${CoreWeaponPresentation.damage(a)} → ${CoreWeaponPresentation.damage(next)}"),
            Line("攻撃速度の補正", CoreUiComponents.MUTED),
            emphasis(if (maximum) "${pct(CoreWeaponPresentation.attackSpeedPercent(a))}%" else
                "${pct(CoreWeaponPresentation.attackSpeedPercent(a))}% → ${pct(CoreWeaponPresentation.attackSpeedPercent(next))}%"))
        else listOf(Line("最大HP", CoreUiComponents.MUTED),
            emphasis(if (maximum) "${CoreWeaponPresentation.health(a)}" else "${CoreWeaponPresentation.health(a)} → ${CoreWeaponPresentation.health(next)}"))
        v.canvas.left(if (maximum) "鍛え抜いた装備" else "強化後の性能",
            lines("T${CoreAffixCatalog.gearTier(a, s.gear)} ${s.gear.displayName}") + changes + lines("") +
                listOf(emphasis(if (maximum) "最大強化 +30" else "成功率 ${pct(quote.successChancePercent)}%")) +
                if (maximum) lines("制作後も強化を維持", "鍛冶熟練 ${CoreEnhancementCatalog.masteryRank(a.smithingXp)}/10")
                else lines(if (quote.guaranteed) "次の強化は成功確定" else "成功保証 ${quote.failures}/${quote.pityThreshold}",
                    "鍛冶熟練 ${CoreEnhancementCatalog.masteryRank(a.smithingXp)}/10", "") +
                    listOf(Line(if (CoreEconomy.broken(a, s.gear)) "破損中・修理が必要" else if (quote.breakPerAttemptPercent > 0)
                        "失敗時破損 ${pct(quote.breakOnFailurePercent)}%" else "今回の破損なし",
                        if (CoreEconomy.broken(a, s.gear) || quote.breakPerAttemptPercent > 0) CoreUiComponents.RED else CoreUiComponents.IVORY)) +
                    lines("強化値・MODは維持", "素材は毎回消費"))
        costPanel(v, a, quote.recipe)
        enhancementFocus(v, player, a, s.gear, if (maximum) "+30" else summary.levelLabel)
        card(v, ENHANCE_DETAIL, 3, 1, "詳細", CoreMenuArt.GEAR, CoreLoopItems.gear(a, s.gear, v.packed)) { gearMods(player, s.gear) }
        card(v, ENHANCE_STANDARD, 3, 1, "通常", CoreMenuArt.ENHANCE, CoreLoopItems.icon(Material.IRON_INGOT, "通常強化", "追加の精錬触媒を消費しません", "素材Tierは装備Tierではなく強化段階によって決まります"),
            if (maximum) Tone.DISABLED else if (mode == CoreEnhancementMode.STANDARD) Tone.SELECTED else Tone.NEUTRAL) { forge(player, s.copy(focused = false)) }
        val standard = CoreEnhancementCatalog.quote(a, s.gear)
        card(v, ENHANCE_CATALYST, 3, 1, "触媒", CoreMenuArt.SHARD, CoreLoopItems.icon(Material.GLOWSTONE_DUST, "精錬触媒を追加", "追加素材を消費し成功率+15ポイント", "費用・成功率を表示してから実行できます"),
            if (maximum || standard.guaranteed) Tone.DISABLED else if (mode == CoreEnhancementMode.FOCUSED) Tone.SELECTED else Tone.NEUTRAL) { forge(player, s.copy(focused = true)) }
        if (CoreEconomy.broken(a, s.gear))
            card(v, 42, 3, 1, "修理へ", CoreMenuArt.ENHANCE, CoreLoopItems.icon(Material.ANVIL, "破損した装備を修理する")) { repairMenu(player, s.gear) }
        else sourceButton(v, player, s)
        execute(v, player, "強化する", quote.blockedReason, quote.recipe) {
            if (quote.breakPerAttemptPercent > 0) enhancementConfirm(player, s)
            else mutate(v, player, CoreAction.EnhanceEquipment(s.gear, mode), a.revision) { forge(player, s) }
        }
    }

    /** Risk confirmation spends nothing until the exact displayed account revision commits. */
    private fun enhancementConfirm(player: Player, s: CoreForgeLayout.Selection) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        val mode = CoreForgeLayout.enhancementMode(a, s)
        val quote = CoreEnhancementCatalog.quote(a, s.gear, mode)
        view(player, "強化の確認 / 破損リスクあり", { forge(player, s) }, nativeChest = true) { v ->
            v.items[13] = CoreLoopItems.gear(a, s.gear, v.packed)
            v.items[4] = CoreLoopItems.icon(Material.BOOK, "+${quote.currentLevel} → +${quote.targetLevel}",
                "成功率 ${pct(quote.successChancePercent)}%",
                "失敗した場合の破損率 ${pct(quote.breakOnFailurePercent)}%",
                "今回1回あたりの破損確率 ${pct(quote.breakPerAttemptPercent)}%",
                "破損しても対象は消失せず、強化値・MOD・成功保証を維持",
                "修理には同Tier・同系統・+0・未破損の装備1個を消費")
            val costs = quote.recipe.costs.map { "${it.key.displayName}: 所持 ${a.amount(it.key)} / 必要 ${it.value}" }
            v.items[22] = CoreLoopItems.icon(if (quote.blockedReason == null) Material.ORANGE_DYE else Material.BARRIER,
                if (quote.blockedReason == null) "リスクを確認して強化する" else "強化できません",
                *(listOfNotNull(quote.blockedReason) + costs + "素材は成功・失敗どちらでも消費 / 取り消せません").toTypedArray())
            if (quote.blockedReason == null) v.actions[22] = {
                mutate(v, player, CoreAction.EnhanceEquipment(s.gear, mode), a.revision) {
                    if (game.account(player)?.let { CoreEconomy.broken(it, s.gear) } == true) repairMenu(player, s.gear)
                    else forge(player, s)
                }
            }
            v.items[45] = CoreLoopItems.icon(Material.ARROW, "やめる / 強化へ戻る")
            v.actions[45] = { forge(player, s) }
        }
    }

    private data class ForgeRecipe(val label: String, val icon: Material, val unit: CoreRecipe, val batches: Boolean = true,
        val build: (Int) -> Pair<CoreRecipe, CoreAction>)
    private fun recipes(a: CoreAccount, s: CoreForgeLayout.Selection): List<ForgeRecipe> =
        if (s.tab == CoreForgeLayout.Tab.REFINE) CoreLoopCatalog.refined.map { (raw, refined) ->
            ForgeRecipe(resourceName(refined), CoreLoopItems.resourceMaterial(refined), CoreLoopCatalog.refine(raw, s.tier)) { count ->
                CoreProfessions.refineQuote(a, raw, s.tier, count).first to CoreAction.Refine(raw, s.tier, count)
            }
        } else buildList {
            val base = if (s.gear == CoreGearSlot.WEAPON) s.base else CoreWeaponBase.STANDARD
            val recipe = CoreEconomy.manufacture(s.gear, s.tier, base)
            add(ForgeRecipe("T${s.tier}${s.gear.displayName}", if (s.gear == CoreGearSlot.WEAPON) Material.IRON_SWORD else Material.IRON_CHESTPLATE, recipe) { count ->
                CoreProfessions.manufacture(s.gear, s.tier, count, base) to CoreAction.Manufacture(s.gear, s.tier, count, base)
            })
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
        if (entry.batches || s.tab == CoreForgeLayout.Tab.CRAFT) {
            tile(v, tierSlot, 1, "<", CoreLoopItems.icon(Material.ARROW, "下の素材Tier"), if (s.tier > 1) Tone.NEUTRAL else Tone.DISABLED) { forge(player, s.copy(tier = s.tier - 1)) }
            tile(v, tierSlot + 1, 1, "${s.tier}", CoreLoopItems.icon(Material.PAPER, "使用する素材：T${s.tier}"), Tone.SELECTED)
            tile(v, tierSlot + 2, 1, ">", CoreLoopItems.icon(Material.ARROW, "上の素材Tier"), if (s.tier < 4) Tone.NEUTRAL else Tone.DISABLED) { forge(player, s.copy(tier = s.tier + 1)) }
        } else tile(v, 15, 3, "T${CoreAffixCatalog.gearTier(a, s.gear)}素材", CoreLoopItems.icon(Material.PAPER, "現在の装備Tierの素材で制作"), Tone.DISABLED)
        entries.forEachIndexed { index, e ->
            val outputResource = e.unit.outputs.keys.firstOrNull()?.resource
            val art = outputResource?.let(::materialArt) ?: gearArt(s.gear)
            val label = outputResource?.let(::stockName) ?: "T${s.tier}"
            val slot = if (s.tab == CoreForgeLayout.Tab.REFINE) REFINE_SLOTS[index] else CoreForgeLayout.RECIPES[index]
            card(v, slot, 3, if (s.tab == CoreForgeLayout.Tab.REFINE) 2 else 1, label, art,
                CoreLoopItems.icon(e.icon, e.unit.displayName, "クリック：選択。まだ素材は使いません"), if (index == selected) Tone.SELECTED else Tone.NEUTRAL) { forge(player, s.copy(recipe = index)) }
        }
        val isEquipment = s.tab == CoreForgeLayout.Tab.CRAFT && selected == 0
        val maximum = if (isEquipment) minOf(16, CoreEconomy.MAX_GEAR - a.storedGear.size, CoreForgeLayout.maxBatches(a, entry.unit))
            else if (entry.batches) CoreForgeLayout.maxBatches(a, entry.unit) else if (entry.unit.canAfford(a)) 1 else 0
        val count = if (entry.batches) CoreForgeLayout.batches(s.quantity, maximum) else 1
        val (recipe, action) = entry.build(count)
        val summary = CoreForgeSummary.recipe(a, recipe)
        val output = if (!isEquipment && entry.batches) recipe.outputs.flatMap { (key, amount) -> lines("${resourceName(key.resource)} ×$amount", "所持 ${a.amount(key)}") }
            else lines("T${s.tier} ${s.gear.displayName} ×$count", "新品を装備庫へ保管", "製造品質を個別抽選", "今の装備は変更なし")
        v.canvas.left("完成するもの", output + lines("今回 $count 回", "制作可能 $maximum 回") +
            (summary.blockedReason?.let { paragraph(it, CoreUiComponents.RED) } ?: lines("制作できます")),
            hero = recipe.outputs.keys.firstOrNull()?.let { materialArt(it.resource) } ?: gearArt(s.gear))
        costPanel(v, a, recipe); sourceButton(v, player, s)
        if (entry.batches) quantity(v, s.quantity, maximum) { forge(player, s.copy(quantity = it)) }
        execute(v, player, if (s.tab == CoreForgeLayout.Tab.REFINE) "精製する" else "制作する",
            if (isEquipment && a.storedGear.size + count > CoreEconomy.MAX_GEAR) "装備の保管上限です" else summary.blockedReason, recipe) {
            mutate(v, player, action, a.revision) {
                if (!isEquipment) forge(player, s.copy(recipe = selected)) else equipmentStock(player)
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
        CoreCraftingCurrency.ASTRAL -> lines("レアのMOD1個を", "ランダムに置換", "他のMODは保持", "深殿踏破の専用報酬")
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
                        if (refine != null) "精製して作る" else if (material.resource.raw) "採取または市場で入手" else "遠征で入手する")) {
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
            v.canvas.left("探すもの", lines(material.displayName, "", if (material.resource == CoreResource.BOSS_SIGIL) "同Tierのボスを討伐" else if (material.resource.raw) "同Tierの地域で採取" else "魔物を討伐して入手", "獲得品は倉庫へ保存"), hero = materialArt(material.resource))
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

    /** Vanilla chest grid; these are read-only ledger stacks, never physical withdrawable items. */
    fun storage(player: Player, tier: Int = 1, page: Int = 0) {
        val a = game.account(player) ?: return
        val selectedTier = tier.coerceIn(1, 4)
        val entries = CoreStorageView.entries(a, selectedTier)
        val last = (entries.size - 1).coerceAtLeast(0) / stockSlots.size
        val current = page.coerceIn(0, last)
        val shown = entries.drop(current * stockSlots.size).take(stockSlots.size)
        view(player, "素材倉庫 / T$selectedTier", { storage(player, selectedTier, current) }, nativeChest = true) { v ->
            // Restrained brass/brown trim only on navigation rows; stock cells remain empty.
            (0..8).plus(45..53).forEach { slot ->
                v.items[slot] = CoreLoopItems.icon(Material.BROWN_STAINED_GLASS_PANE, " ")
            }
            (1..4).forEach { t ->
                val slot = (t - 1) * 2
                v.items[slot] = CoreLoopItems.icon(
                    if (t == selectedTier) Material.GOLD_INGOT else Material.PAPER,
                    if (t == selectedTier) "T$t の素材（表示中）" else "T$t の素材",
                    "クリック：このTierの素材を表示", "オーブ・欠片などの共通品も表示します")
                v.actions[slot] = { storage(player, t) }
            }
            v.items[8] = CoreLoopItems.icon(Material.BOOK, "素材倉庫の使い方",
                "1枠に1種類。カーソルを合わせると名前・正確な個数を表示",
                "右下の数字は最大64。合計数は説明欄で確認できます",
                "素材は自動保管され、工房で直接消費します",
                "持ち出し・預け入れは不要です",
                "クリック：その素材を使う画面へ（ここでは消費しません）")
            shown.forEachIndexed { index, entry ->
                val slot = stockSlots[index]
                val destination = when (entry) {
                    is CoreStorageView.Entry.Currency -> "刻印画面へ"
                    is CoreStorageView.Entry.Fragment -> "ボスの選択へ"
                    is CoreStorageView.Entry.Material -> if (entry.material.resource.raw) "精製画面へ" else "工房へ"
                }
                val original = when (entry) {
                    is CoreStorageView.Entry.Material -> CoreLoopItems.resource(entry.material, entry.count)
                    is CoreStorageView.Entry.Currency -> CoreLoopItems.currency(entry.currency, entry.count, v.packed)
                    is CoreStorageView.Entry.Fragment -> CoreLoopItems.fragment(entry.kind, entry.count)
                }
                val hint = if (a.activeRun == null) "クリック：$destination" else "港へ帰還すると使用できます"
                v.items[slot] = original.with(DataComponents.LORE,
                    listOf(CoreUiComponents.text("所持 ${entry.count} 個", CoreUiComponents.GOLD)) +
                        original.get(DataComponents.LORE).orEmpty() +
                        listOf(CoreUiComponents.text(hint, CoreUiComponents.GOLD),
                            CoreUiComponents.text("自動保管 / この画面では消費しません", CoreUiComponents.MUTED)))
                if (a.activeRun == null) v.actions[slot] = {
                    when (entry) {
                        is CoreStorageView.Entry.Currency -> confirmCraft(player, selections[player.uuid]?.gear ?: CoreGearSlot.WEAPON, entry.currency)
                        is CoreStorageView.Entry.Fragment -> trials(player, selectedTier)
                        is CoreStorageView.Entry.Material -> {
                            val rawIndex = CoreLoopCatalog.refined.keys.indexOf(entry.material.resource)
                            if (rawIndex >= 0) forge(player, CoreForgeLayout.Selection(tab = CoreForgeLayout.Tab.REFINE, tier = entry.material.tier, recipe = rawIndex))
                            else workshop(player, selectedTier)
                        }
                    }
                }
            }
            if (shown.isEmpty()) v.items[22] = CoreLoopItems.icon(Material.CHEST, "このTierの素材はまだありません",
                "採取・討伐すると、ここに自動で保管されます", "上段から他のTierも確認できます")
            v.items[45] = CoreLoopItems.icon(Material.ARROW, "冒険の手帳へ戻る")
            v.actions[45] = { journal(player) }
            if (a.activeRun == null) {
                v.items[46] = CoreLoopItems.icon(Material.GOLD_NUGGET, "市場へ / 素材を売買する")
                v.actions[46] = { supplies(player, selectedTier) }
            }
            if (current > 0) {
                v.items[47] = CoreLoopItems.icon(Material.ARROW, "前のページ")
                v.actions[47] = { storage(player, selectedTier, current - 1) }
            }
            v.items[49] = CoreLoopItems.icon(Material.CHEST, "${entries.size} 種類 / ${current + 1} / ${last + 1} ページ",
                "所持している素材・オーブ・欠片だけを表示しています")
            if (current < last) {
                v.items[51] = CoreLoopItems.icon(Material.ARROW, "次のページ")
                v.actions[51] = { storage(player, selectedTier, current + 1) }
            }
            v.items[53] = CoreLoopItems.icon(if (a.activeRun == null) Material.ANVIL else Material.BARRIER,
                if (a.activeRun == null) "工房を開く" else "工房は港で利用できます")
            if (a.activeRun == null) v.actions[53] = { workshop(player, selectedTier) }
        }
    }

    /** Stock and market use ordinary chest slots. Clicking stock opens a quote, never spends immediately. */
    fun equipmentStock(player: Player, page: Int = 0) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        val last = (a.storedGear.size - 1).coerceAtLeast(0) / 36
        val current = page.coerceIn(0, last)
        view(player, "装備庫 / 銀貨${a.silver}", { equipmentStock(player, current) }, nativeChest = true) { v ->
            CoreGearSlot.entries.forEachIndexed { index, slot ->
                v.items[index] = CoreLoopItems.gear(a, slot, v.packed)
                v.actions[index] = { gearMods(player, slot) }
                val broken = CoreEconomy.broken(a, slot)
                v.items[3 + index * 2] = CoreLoopItems.icon(if (broken) Material.ANVIL else Material.IRON_INGOT,
                    "${slot.displayName}：${if (broken) "破損中 / 修理が必要" else "未破損"}", "遠征・戦闘では壊れません / +15以降の強化失敗で破損",
                    "同Tier・同系統・+0・未破損を1個消費して修理", "MOD・強化値は失いません / クリックで修理へ")
                v.actions[3 + index * 2] = { repairMenu(player, slot) }
            }
            v.items[8] = CoreLoopItems.icon(Material.ANVIL, "新しく制作する", "採取 → 精製 → 武器・防具を制作", "完成品は自動装備せず、この装備庫へ保管")
            v.actions[8] = { forge(player, CoreForgeLayout.Selection(tab = CoreForgeLayout.Tab.CRAFT)) }
            a.storedGear.drop(current * 36).take(36).forEachIndexed { index, item ->
                v.items[9 + index] = gearStockIcon(a, item, v.packed)
                v.actions[9 + index] = { equipmentDetail(player, item.identity.id) }
            }
            if (a.storedGear.isEmpty()) v.items[22] = CoreLoopItems.icon(Material.CHEST, "保管している装備はありません", "右上の金床から制作できます", "今の装備は左上から確認")
            v.items[45] = CoreLoopItems.icon(Material.ARROW, "手帳へ"); v.actions[45] = { journal(player) }
            v.items[49] = CoreLoopItems.icon(Material.PAPER, "${current + 1}/${last + 1}ページ", "装備 ${a.storedGear.size}/${CoreEconomy.MAX_GEAR}", "港の納品：自作・未加工の装備を1日3個まで", "更新は日本時間9時。市場への出品は別枠です")
            if (current > 0) { v.items[47] = CoreLoopItems.icon(Material.ARROW, "前へ"); v.actions[47] = { equipmentStock(player, current - 1) } }
            if (current < last) { v.items[51] = CoreLoopItems.icon(Material.ARROW, "次へ"); v.actions[51] = { equipmentStock(player, current + 1) } }
            v.items[53] = CoreLoopItems.icon(Material.GOLD_NUGGET, "市場へ"); v.actions[53] = { supplies(player) }
        }
    }

    private fun gearStockIcon(a: CoreAccount, item: CoreStoredGear, packed: Boolean): ItemStack {
        val base = CoreLoopItems.gear(item.project(a), item.slot, packed)
        return base.with(DataComponents.LORE, base.get(DataComponents.LORE).orEmpty() +
            listOf(CoreLoopItems.text(if (item.identity.bound) "初期・引継ぎ装備 / 売却不可" else "製作者 ${item.identity.crafter.toString().take(8)}"),
                CoreLoopItems.text(if (a.offers.any { it.gearId == item.identity.id }) "出品中 / 取り下げてから装備可能" else "クリック：装備・出品・納品")))
    }

    private fun equipmentDetail(player: Player, id: UUID) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        val item = a.storedGear.singleOrNull { it.identity.id == id } ?: return equipmentStock(player)
        val offer = a.offers.singleOrNull { it.gearId == id }
        view(player, "装備庫 / ${item.displayName}", { equipmentDetail(player, id) }, nativeChest = true) { v ->
            v.items[13] = gearStockIcon(a, item, v.packed)
            v.items[45] = CoreLoopItems.icon(Material.ARROW, "装備庫へ"); v.actions[45] = { equipmentStock(player) }
            if (offer != null) {
                v.items[22] = CoreLoopItems.icon(Material.BARRIER, "出品を取り下げる", "価格 銀貨${offer.price}枚")
                v.actions[22] = { mutate(v, player, CoreAction.CancelOffer(offer.id), a.revision) { equipmentDetail(player, id) } }
            } else {
                v.items[20] = CoreLoopItems.icon(Material.LIME_DYE, "この装備に変更する", "今の装備は装備庫へ戻します", "MOD・強化値も装備ごとに保持します")
                v.actions[20] = { mutate(v, player, CoreAction.Equip(id), a.revision) { equipmentStock(player) } }
                v.items[22] = CoreLoopItems.icon(if (item.identity.bound) Material.BARRIER else Material.GOLD_NUGGET,
                    if (item.identity.bound) "売却できない装備" else "価格を決めて出品する", "出品画面ではまだ装備を消費しません")
                if (!item.identity.bound) v.actions[22] = { listingQuote(player, gearId = id, price = CoreEconomy.deliveryPrice(item.tier)) }
                val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay()
                val used = if (today == a.deliveryDay) a.deliveries else 0
                val eligible = !item.identity.bound && item.identity.crafter == a.playerId && item.enhancement.level == 0 &&
                    item.affixes.isEmpty() && item.rarity == CoreGearRarity.NORMAL && !item.broken && used < CoreEconomy.DAILY_DELIVERIES
                v.items[24] = CoreLoopItems.icon(if (eligible) Material.EMERALD else Material.BARRIER,
                    if (eligible) "港の依頼へ納品 / 確認へ" else "この装備は納品できません",
                    "自作・未強化・MODなし・未破損の装備のみ", "本日 ${used}/3個 / 完成品は消費されます",
                    "報酬 銀貨${CoreEconomy.deliveryPrice(item.tier)}枚")
                if (eligible) v.actions[24] = { deliveryConfirm(player, id) }
            }
        }
    }

    private fun repairMenu(player: Player, slot: CoreGearSlot, page: Int = 0, selected: UUID? = null) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        val inputs = a.storedGear.filter { CoreEconomy.repairInput(a, slot, it) }
        val last = (inputs.size - 1).coerceAtLeast(0) / 36
        val current = page.coerceIn(0, last)
        val input = inputs.singleOrNull { it.identity.id == selected }
        val broken = CoreEconomy.broken(a, slot)
        view(player, "${slot.displayName}の修理 / ${if (!broken) "未破損" else if (selected != null) "材料を確認" else "材料を選択"}", { repairMenu(player, slot, current) }, nativeChest = true) { v ->
            v.items[4] = CoreLoopItems.gear(a, slot, v.packed)
            v.items[8] = CoreLoopItems.icon(Material.BOOK, "修理材料の装備1個を消費", "同Tier・同系統・+0・未破損 / 初期装備・出品中は不可", "対象の装備は消えません / MOD・強化値・成功保証を維持",
                "修理材料のレアリティ・MODは問いません", "材料に付いているMODも消失します / 選択後に確認")
            when {
                !broken -> v.items[22] = CoreLoopItems.icon(Material.IRON_INGOT, "破損していません", "素材は消費しません")
                input != null -> {
                    v.items[13] = gearStockIcon(a, input, v.packed)
                    v.items[22] = CoreLoopItems.icon(Material.LIME_DYE, "この材料装備を消費して修理する", "消費するのは上の材料装備1個です", "材料装備のMODも消失 / 取り消せません",
                        "修理対象の強化値・MOD・成功保証はそのまま")
                    v.actions[22] = { mutate(v, player, CoreAction.Repair(slot, input.identity.id), a.revision) { equipmentStock(player) } }
                }
                inputs.isEmpty() -> {
                    v.items[20] = CoreLoopItems.icon(Material.ANVIL, "予備装備が不足 / 制作へ", "T${CoreAffixCatalog.gearTier(a, slot)} ${slot.displayName}の+0・未破損が1個必要")
                    v.actions[20] = { forge(player, CoreForgeLayout.Selection(tab = CoreForgeLayout.Tab.CRAFT, gear = slot, tier = CoreAffixCatalog.gearTier(a, slot))) }
                    v.items[24] = CoreLoopItems.icon(Material.GOLD_NUGGET, "市場で予備装備を探す")
                    v.actions[24] = { supplies(player, CoreAffixCatalog.gearTier(a, slot), repairFor = slot) }
                }
                else -> inputs.drop(current * 36).take(36).forEachIndexed { index, item ->
                    v.items[9 + index] = gearStockIcon(a, item, v.packed)
                    v.actions[9 + index] = { repairMenu(player, slot, current, item.identity.id) }
                }
            }
            if (selected == null) {
                if (current > 0) { v.items[47] = CoreLoopItems.icon(Material.ARROW, "前へ"); v.actions[47] = { repairMenu(player, slot, current - 1) } }
                if (current < last) { v.items[51] = CoreLoopItems.icon(Material.ARROW, "次へ"); v.actions[51] = { repairMenu(player, slot, current + 1) } }
            }
            v.items[45] = CoreLoopItems.icon(Material.ARROW, "戻る")
            v.actions[45] = { if (selected == null) equipmentStock(player) else repairMenu(player, slot, current) }
        }
    }

    private fun deliveryConfirm(player: Player, id: UUID) {
        val a = game.account(player) ?: return
        val item = a.storedGear.singleOrNull { it.identity.id == id } ?: return equipmentStock(player)
        view(player, "納品 / 装備を消費します", { deliveryConfirm(player, id) }, nativeChest = true) { v ->
            v.items[13] = gearStockIcon(a, item, v.packed)
            v.items[22] = CoreLoopItems.icon(Material.LIME_DYE, "この装備を納品する", "取り消せません / 完成品1個を消費", "銀貨${CoreEconomy.deliveryPrice(item.tier)}枚を受け取ります")
            v.actions[22] = { mutate(v, player, CoreAction.Deliver(id), a.revision) { equipmentStock(player) } }
            v.items[45] = CoreLoopItems.icon(Material.ARROW, "やめる"); v.actions[45] = { equipmentDetail(player, id) }
        }
    }

    fun supplies(player: Player, tier: Int = 1, selected: CoreResource? = null,
        quantity: CoreForgeLayout.Quantity = CoreForgeLayout.Quantity.ONE, page: Int = 0, repairFor: CoreGearSlot? = null) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        val entries = game.market().filter { entry ->
            (entry.gear?.tier ?: entry.offer.material?.tier) == tier &&
                (selected == null || entry.offer.material?.resource == selected) &&
                (repairFor == null || (entry.seller != a.playerId && entry.gear?.let { CoreEconomy.repairCompatible(a, repairFor, it) } == true))
        }
        val last = (entries.size - 1).coerceAtLeast(0) / 36
        val current = page.coerceIn(0, last)
        view(player, "${if (repairFor != null) "修理材料の市場" else "市場"} / T$tier / 銀貨${a.silver}", { supplies(player, tier, selected, quantity, current, repairFor) }, nativeChest = true) { v ->
            (1..4).forEach { t ->
                v.items[(t - 1) * 2] = CoreLoopItems.icon(if (tier == t) Material.GOLD_INGOT else if (repairFor != null) Material.BARRIER else Material.PAPER,
                    if (repairFor != null && tier != t) "修理材料はT${tier}のみ" else "T$t の出品を見る")
                if (repairFor == null) v.actions[(t - 1) * 2] = { supplies(player, t, selected) }
            }
            v.items[8] = CoreLoopItems.icon(Material.BOOK, "採取者・職人・戦闘者の市場", "素材は採取者が集め、職人が装備を制作", "戦利品券 → 銀貨 → 素材や装備を購入", "成約手数料5% / 出品時の消費なし",
                if (repairFor != null) "同Tier・同系統・+0・未破損のみ表示" else "クリックで素材フィルターを解除", "クリックで市場の全商品へ")
            v.actions[8] = { supplies(player, tier) }
            entries.drop(current * 36).take(36).forEachIndexed { index, entry ->
                v.items[9 + index] = marketIcon(a, entry, v.packed)
                v.actions[9 + index] = { marketDetail(player, entry, repairFor) }
            }
            if (entries.isEmpty()) {
                v.items[22] = CoreLoopItems.icon(Material.CHEST, "この条件の出品はありません", "NPCから採取素材は購入できません", "採取して出品する / 装備を制作して出品する", "クリック：同Tierの採取遠征へ")
                v.actions[22] = { expeditions(player, tier) }
            }
            v.items[45] = CoreLoopItems.icon(Material.ARROW, if (repairFor != null) "修理へ" else if (journey(player).isEmpty) "手帳へ" else "元の工房へ")
            v.actions[45] = { if (repairFor != null) repairMenu(player, repairFor) else journey(player).pop()?.let { forge(player, it) } ?: journal(player) }
            v.items[46] = CoreLoopItems.icon(Material.CHEST, "素材を出品する"); v.actions[46] = { saleMaterials(player, tier) }
            v.items[50] = CoreLoopItems.icon(Material.WRITABLE_BOOK, "購入注文 / 買い手を探す", "注文は銀貨を預託済み。持ち物を納品して即時売却"); v.actions[50] = { orders(player, tier) }
            v.items[48] = CoreLoopItems.icon(Material.IRON_SWORD, "装備庫 / 装備を出品"); v.actions[48] = { equipmentStock(player) }
            v.items[49] = CoreLoopItems.icon(Material.PAPER, "${current + 1}/${last + 1}ページ / クリックで更新"); v.actions[49] = { supplies(player, tier, selected, quantity, current, repairFor) }
            if (current > 0) { v.items[47] = CoreLoopItems.icon(Material.ARROW, "前へ"); v.actions[47] = { supplies(player, tier, selected, quantity, current - 1, repairFor) } }
            if (current < last) { v.items[51] = CoreLoopItems.icon(Material.ARROW, "次へ"); v.actions[51] = { supplies(player, tier, selected, quantity, current + 1, repairFor) } }
            val tokens = a.amount(CoreResource.COMBAT_TOKEN, tier)
            v.items[53] = CoreLoopItems.icon(if (tokens > 0) Material.GOLD_NUGGET else Material.BARRIER,
                if (tokens > 0) "T$tier 戦利品券を1枚換金" else "T$tier 戦利品券がありません",
                "所持 ${tokens}枚 / 1枚 → 銀貨${10 * tier}枚", "高Tierの地図入手にも使います")
            if (tokens > 0) v.actions[53] = { mutate(v, player, CoreAction.RedeemTokens(tier, 1), a.revision) { supplies(player, tier, selected, quantity, current, repairFor) } }
        }
    }

    private fun marketIcon(a: CoreAccount, entry: CoreMarketEntry, packed: Boolean): ItemStack {
        val offer = entry.offer
        val base = entry.gear?.let { CoreLoopItems.gear(it.project(a), it.slot, packed) }
            ?: CoreLoopItems.icon(CoreLoopItems.resourceMaterial(requireNotNull(offer.material).resource),
                "${offer.material.displayName} ×${offer.quantity}").withAmount(offer.quantity.toInt().coerceAtMost(64))
        return base.with(DataComponents.LORE, base.get(DataComponents.LORE).orEmpty() + listOf(
            CoreLoopItems.text("合計 銀貨${offer.price}枚 / 所持 ${a.silver}枚"),
            CoreLoopItems.text(if (entry.seller == a.playerId) "自分の出品 / クリックで取り下げ確認" else "クリック：商品と価格を確認")))
    }

    private fun marketDetail(player: Player, entry: CoreMarketEntry, repairFor: CoreGearSlot? = null) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        val own = entry.seller == a.playerId
        val offer = entry.offer
        view(player, "市場 / ${if (own) "出品の管理" else "購入の確認"}", { supplies(player, entry.gear?.tier ?: offer.material?.tier ?: 1, repairFor = repairFor) }, nativeChest = true) { v ->
            v.items[13] = marketIcon(a, entry, v.packed)
            val available = own || a.silver >= offer.price
            v.items[22] = CoreLoopItems.icon(if (available) Material.LIME_DYE else Material.BARRIER,
                if (own) "出品を取り下げる" else if (available) "銀貨${offer.price}枚で購入する" else "銀貨が${offer.price - a.silver}枚不足",
                if (own) "商品は自分の倉庫へ戻ります" else "購入品を倉庫に保管 / 装備は自動装備しません",
                if (entry.gear?.broken == true) "注意：破損品です。別の装備1個で修理するまで性能は無効" else "未破損の商品です")
            if (available) v.actions[22] = { mutate(v, player,
                if (own) CoreAction.CancelOffer(offer.id) else CoreAction.BuyOffer(entry.seller, offer.id, offer.price), a.revision) {
                    if (repairFor != null) repairMenu(player, repairFor)
                    else if (entry.gear != null) equipmentStock(player) else storage(player, offer.material?.tier ?: 1)
                } }
            v.items[45] = CoreLoopItems.icon(Material.ARROW, "市場へ"); v.actions[45] = { supplies(player, entry.gear?.tier ?: offer.material?.tier ?: 1, repairFor = repairFor) }
        }
    }

    private fun saleMaterials(player: Player, tier: Int) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        view(player, "出品する素材 / T$tier", { saleMaterials(player, tier) }, nativeChest = true) { v ->
            a.balances.filter { (m, n) -> m.tier == tier && n > 0 && CoreEconomy.tradeable(m.resource) }.entries.forEachIndexed { index, (m, n) ->
                v.items[9 + index] = CoreLoopItems.icon(CoreLoopItems.resourceMaterial(m.resource), m.displayName, "所持 ${n}個", "クリック：数量と価格を決める").withAmount(n.toInt().coerceAtMost(64))
                v.actions[9 + index] = { listingQuote(player, material = m) }
            }
            v.items[45] = CoreLoopItems.icon(Material.ARROW, "市場へ"); v.actions[45] = { supplies(player, tier) }
        }
    }

    private fun listingQuote(player: Player, material: CoreMaterial? = null, gearId: UUID? = null, quantity: Long = 1, price: Long = 10) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        val item = gearId?.let { id -> a.storedGear.singleOrNull { it.identity.id == id } ?: return equipmentStock(player) }
        val maximum = material?.let { a.amount(it).coerceAtMost(999) } ?: 1L
        val count = quantity.coerceIn(1, maximum.coerceAtLeast(1))
        val total = price.coerceIn(1, CoreEconomy.MAX_SILVER)
        val ready = maximum > 0 && a.offers.size < CoreEconomy.MAX_OFFERS && (item == null || !item.identity.bound)
        view(player, "出品 / 数量と合計価格", { listingQuote(player, material, gearId, count, total) }, nativeChest = true) { v ->
            v.items[4] = item?.let { gearStockIcon(a, it, v.packed) } ?: CoreLoopItems.icon(CoreLoopItems.resourceMaterial(requireNotNull(material).resource), material.displayName)
            listOf(-1000L, -100L, -1L, 1L, 100L, 1000L).forEachIndexed { index, delta ->
                val slot = listOf(10, 11, 12, 14, 15, 16)[index]
                v.items[slot] = CoreLoopItems.icon(Material.GOLD_NUGGET, "合計価格 ${if (delta > 0) "+" else ""}$delta")
                v.actions[slot] = { listingQuote(player, material, gearId, count, (total + delta).coerceIn(1, CoreEconomy.MAX_SILVER)) }
            }
            v.items[13] = CoreLoopItems.icon(Material.GOLD_INGOT, "合計 銀貨${total}枚", "商品 ${count}個分の合計価格です", "成約手数料 ${CoreEconomy.fee(total)}枚", "売上受取 ${total - CoreEconomy.fee(total)}枚", "売れなければ手数料なし / 取り下げ可能")
            if (material != null) {
                listOf(-10L, -1L, 1L, 10L).forEachIndexed { index, delta ->
                    val slot = listOf(28, 29, 33, 34)[index]
                    v.items[slot] = CoreLoopItems.icon(Material.PAPER, "数量 ${if (delta > 0) "+" else ""}$delta")
                    v.actions[slot] = { listingQuote(player, material, null, (count + delta).coerceIn(1, maximum.coerceAtLeast(1)), total) }
                }
                v.items[31] = CoreLoopItems.icon(Material.CHEST, "出品 ${count}個 / 所持 ${a.amount(material)}個", "数量を変えても合計価格は変わりません")
            }
            v.items[40] = CoreLoopItems.icon(if (ready) Material.LIME_DYE else Material.BARRIER,
                if (ready) "この数量・価格で出品する" else "出品できません", "最大24出品 / 素材は一時的に倉庫から預かります")
            if (ready) v.actions[40] = { mutate(v, player, if (item != null) CoreAction.ListGear(item.identity.id, total)
                else CoreAction.ListMaterial(requireNotNull(material), count, total), a.revision) { supplies(player, item?.tier ?: material?.tier ?: 1) } }
            v.items[45] = CoreLoopItems.icon(Material.ARROW, "やめる")
            v.actions[45] = { if (item != null) equipmentDetail(player, item.identity.id) else saleMaterials(player, material?.tier ?: 1) }
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

    fun professions(player: Player) {
        val a = game.account(player) ?: return
        view(player, "職業 / 好きな仕事を育てる", { professions(player) }, nativeChest = true) { v ->
            v.items[10] = CoreLoopItems.icon(Material.OAK_SAPLING, "採取技能", "採取時間と収量を育てる。現在の技能を保持しています")
            v.actions[10] = { mastery(player) }
            v.items[12] = CoreLoopItems.icon(Material.MAP, "採取実績 ${a.surveyPoints}", "討伐不要の地図：T1〜${CoreProfessions.surveyTier(a.surveyPoints)}", "採取対象1個につき、そのTier分の実績")
            v.actions[12] = { surveyMaps(player) }
            v.items[14] = CoreLoopItems.icon(Material.WRITABLE_BOOK, "注文へ納品する", "買い手が求める素材・装備を確認してから作れる")
            v.actions[14] = { orders(player) }
            CoreProfession.entries.forEachIndexed { i, p ->
                val progress = CoreProfessions.progress(a, p)
                val refine = p.ordinal < 5
                val level = progress.level()
                v.items[27 + i] = CoreLoopItems.icon(if (refine) Material.FURNACE else Material.ANVIL, "${p.displayName} Lv$level / 100",
                    "経験値 ${progress.xp}", if (refine) "原料還元 ${level * CoreMmoTuning.balance.refineReturnMaxPercent / 100}%（端数は保持）" else "Lvが上がると製造品質の下限・抽選回数が向上",
                    "購入した素材でも経験値を獲得できます", "クリック：工房へ")
                v.actions[27 + i] = { forge(player, CoreForgeLayout.Selection(tab = if (refine) CoreForgeLayout.Tab.REFINE else CoreForgeLayout.Tab.CRAFT,
                    recipe = if (refine) i else 0, gear = if (p == CoreProfession.ARMORSMITH) CoreGearSlot.ARMOR else CoreGearSlot.WEAPON)) }
            }
            v.items[45] = CoreLoopItems.icon(Material.ARROW, "手帳へ"); v.actions[45] = { journal(player) }
        }
    }

    private fun surveyMaps(player: Player, tier: Int = 1) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        val b = CoreMmoTuning.balance
        view(player, "採取地図 / T$tier", { surveyMaps(player, tier) }, nativeChest = true) { v ->
            (1..4).forEach { t -> v.items[(t - 1) * 2] = CoreLoopItems.icon(Material.MAP, "T$t 採取地図"); v.actions[(t - 1) * 2] = { surveyMaps(player, t) } }
            v.items[8] = CoreLoopItems.icon(Material.BOOK, "採取実績 ${a.surveyPoints}", "T2 ${b.surveyTier2} / T3 ${b.surveyTier3} / T4 ${b.surveyTier4}", "現在 T${CoreProfessions.surveyTier(a.surveyPoints)}まで", "地図の内容は通常遠征と同じ。討伐せず帰っても素材は保持")
            CoreLoopCatalog.refined.keys.forEachIndexed { i, raw ->
                val quote = CoreProfessions.surveyMap(tier, raw)
                val unlocked = tier <= CoreProfessions.surveyTier(a.surveyPoints)
                val cost = quote.costs.entries.firstOrNull()
                val allowed = unlocked && quote.canAfford(a) && a.maps.size < CoreLoopCatalog.MAX_MAPS
                v.items[20 + i] = CoreLoopItems.icon(if (allowed) CoreLoopItems.resourceMaterial(raw) else Material.BARRIER, "${raw.displayName}で地図を用意",
                    cost?.let { "${it.key.displayName} 必要${it.value} / 所持${a.amount(it.key)}" } ?: "T1は無料",
                    if (!unlocked) "採取実績が足りません" else if (!quote.canAfford(a)) "素材不足" else if (!allowed) "地図の保管上限" else "クリック：地図を1枚受け取る")
                if (allowed) v.actions[20 + i] = { mutate(v, player, CoreAction.SurveyMap(tier, raw, System.nanoTime()), a.revision) { expeditions(player, tier) } }
            }
            v.items[45] = CoreLoopItems.icon(Material.ARROW, "地図台へ"); v.actions[45] = { expeditions(player, tier) }
        }
    }

    private fun orders(player: Player, tier: Int = 1, page: Int = 0) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        val entries = game.buyOrders().filter { it.order.tier == tier }
        val last = ((entries.size - 1).coerceAtLeast(0) / 36)
        val p = page.coerceIn(0, last)
        view(player, "購入注文 / T$tier / 銀貨${a.silver}", { orders(player, tier, p) }, nativeChest = true) { v ->
            (1..4).forEach { t -> v.items[(t - 1) * 2] = CoreLoopItems.icon(Material.PAPER, "T$t 注文"); v.actions[(t - 1) * 2] = { orders(player, t) } }
            entries.drop(p * 36).take(36).forEachIndexed { i, e ->
                val o = e.order
                v.items[9 + i] = CoreLoopItems.icon(o.resource?.let(CoreLoopItems::resourceMaterial) ?: if (o.slot == CoreGearSlot.WEAPON) Material.IRON_SWORD else Material.IRON_CHESTPLATE,
                    o.displayName, "残り${o.remaining}個 / 1個 銀貨${o.unitPrice}枚", "代金は預託済み / 納品手数料5%",
                    if (o.slot == null) "持っている素材を納品できます" else "同Tier・同系統の+0・未破損装備（MOD・品質不問）",
                    if (e.buyer == a.playerId) "自分の注文：クリックで取り下げ確認" else "クリック：納品の確認へ")
                v.actions[9 + i] = { orderDetail(player, e) }
            }
            if (entries.isEmpty()) v.items[22] = CoreLoopItems.icon(Material.BOOK, "このTierには注文がありません", "自分で銀貨を預けて購入注文を出せます")
            v.items[45] = CoreLoopItems.icon(Material.ARROW, "市場へ"); v.actions[45] = { supplies(player, tier) }
            v.items[49] = CoreLoopItems.icon(Material.PAPER, "${p + 1}/${last + 1} 更新"); v.actions[49] = { orders(player, tier, p) }
            v.items[53] = CoreLoopItems.icon(Material.WRITABLE_BOOK, "新しい購入注文"); v.actions[53] = { orderTargets(player, tier) }
            if (p > 0) { v.items[47] = CoreLoopItems.icon(Material.ARROW, "前へ"); v.actions[47] = { orders(player, tier, p - 1) } }
            if (p < last) { v.items[51] = CoreLoopItems.icon(Material.ARROW, "次へ"); v.actions[51] = { orders(player, tier, p + 1) } }
        }
    }

    private fun orderTargets(player: Player, tier: Int) {
        view(player, "購入する品物を選ぶ / T$tier", { orderTargets(player, tier) }, nativeChest = true) { v ->
            (CoreLoopCatalog.refined.keys + CoreLoopCatalog.refined.values).forEachIndexed { i, r ->
                v.items[10 + i] = CoreLoopItems.icon(CoreLoopItems.resourceMaterial(r), r.displayName)
                v.actions[10 + i] = { orderQuote(player, tier, r) }
            }
            CoreGearSlot.entries.forEachIndexed { i, s -> v.items[30 + i] = CoreLoopItems.icon(if (s == CoreGearSlot.WEAPON) Material.IRON_SWORD else Material.IRON_CHESTPLATE, "T$tier ${s.displayName} +0")
                v.actions[30 + i] = { orderQuote(player, tier, slot = s) } }
            v.items[45] = CoreLoopItems.icon(Material.ARROW, "注文一覧へ"); v.actions[45] = { orders(player, tier) }
        }
    }

    private fun orderQuote(player: Player, tier: Int, resource: CoreResource? = null, slot: CoreGearSlot? = null, quantity: Int = 1, price: Long = 10) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        val order = CoreBuyOrder(UUID(0, 0), price, quantity, tier, resource, slot, if (slot == CoreGearSlot.WEAPON) a.weaponIdentity.base.family else null)
        view(player, "購入注文 / 代金を預ける", { orderQuote(player, tier, resource, slot, quantity, price) }, nativeChest = true) { v ->
            fun redraw(q: Int = quantity, p: Long = price) = orderQuote(player, tier, resource, slot, q.coerceIn(1, if (slot == null) 999 else 16), p.coerceIn(1, CoreEconomy.MAX_SILVER / 999))
            v.items[13] = CoreLoopItems.icon(Material.BOOK, order.displayName, "数量 $quantity / 単価 $price", "預託合計 ${order.escrow} / 所持${a.silver}", "注文の取消で未成立分を返却", "購入後は素材倉庫・装備庫へ自動保管")
            listOf(-10, -1, 1, 10).forEachIndexed { i, n ->
                v.items[19 + i] = CoreLoopItems.icon(Material.PAPER, "数量 ${if (n > 0) "+" else ""}$n"); v.actions[19 + i] = { redraw(q = quantity + n) }
                v.items[28 + i] = CoreLoopItems.icon(Material.GOLD_NUGGET, "単価 ${if (n > 0) "+" else ""}$n"); v.actions[28 + i] = { redraw(p = price + n) }
            }
            val allowed = a.silver >= order.escrow && a.buyOrders.size < CoreEconomy.MAX_OFFERS
            v.items[40] = CoreLoopItems.icon(if (allowed) Material.LIME_DYE else Material.BARRIER, if (allowed) "銀貨${order.escrow}枚を預けて注文する" else "銀貨不足、または注文上限")
            if (allowed) v.actions[40] = { mutate(v, player, CoreAction.PlaceBuyOrder(resource, slot, tier, quantity, price), a.revision) { orders(player, tier) } }
            v.items[45] = CoreLoopItems.icon(Material.ARROW, "戻る"); v.actions[45] = { orderTargets(player, tier) }
        }
    }

    private fun orderDetail(player: Player, entry: CoreBuyOrderEntry, page: Int = 0) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        val current = game.buyOrders().firstOrNull { it.buyer == entry.buyer && it.order.id == entry.order.id } ?: return orders(player, entry.order.tier)
        val o = current.order
        view(player, "注文への納品 / ${o.displayName}", { orderDetail(player, current, page) }, nativeChest = true) { v ->
            if (current.buyer == player.uuid) {
                v.items[22] = CoreLoopItems.icon(Material.BARRIER, "注文を取り下げる", "未成立の${o.escrow}枚を返却（確定済み取引は戻りません）")
                v.actions[22] = { mutate(v, player, CoreAction.CancelBuyOrder(o.id), a.revision) { orders(player, o.tier) } }
            } else if (o.resource != null) {
                val held = a.amount(o.resource, o.tier)
                listOf(1, 10, 64, minOf(held, o.remaining.toLong()).toInt()).distinct().filter { it in 1..o.remaining }.forEachIndexed { i, count ->
                    val price = o.unitPrice * count
                    val allowed = held >= count
                    v.items[20 + i] = CoreLoopItems.icon(if (allowed) CoreLoopItems.resourceMaterial(o.resource) else Material.BARRIER, "$count 個を納品する",
                        "所持 $held / 手取り${price - CoreEconomy.fee(price)}枚", "クリックで素材を消費し納品します")
                    if (allowed) v.actions[20 + i] = { mutate(v, player, CoreAction.FillBuyOrder(current.buyer, o.id, o.unitPrice, count), a.revision) { orders(player, o.tier) } }
                }
            } else {
                val gear = a.storedGear.filter { o.accepts(it) && a.offers.none { offer -> offer.gearId == it.identity.id } }
                gear.drop(page * 27).take(27).forEachIndexed { i, item ->
                    v.items[9 + i] = CoreLoopItems.gear(item.project(a), item.slot, v.packed)
                    v.actions[9 + i] = { confirmOrderGear(player, current, item.identity.id) }
                }
                if (gear.isEmpty()) v.items[22] = CoreLoopItems.icon(Material.BARRIER, "納品できる装備がありません", "同Tier・同系統・+0・未破損 / 初期・出品中の装備は不可")
                if (page > 0) { v.items[47] = CoreLoopItems.icon(Material.ARROW, "前へ"); v.actions[47] = { orderDetail(player, current, page - 1) } }
                if ((page + 1) * 27 < gear.size) { v.items[51] = CoreLoopItems.icon(Material.ARROW, "次へ"); v.actions[51] = { orderDetail(player, current, page + 1) } }
            }
            v.items[8] = CoreLoopItems.icon(Material.BOOK, "単価${o.unitPrice}枚 / 残り${o.remaining}個", "装備のMOD・品質もそのまま相手へ渡ります")
            v.items[45] = CoreLoopItems.icon(Material.ARROW, "注文一覧へ"); v.actions[45] = { orders(player, o.tier) }
        }
    }

    private fun confirmOrderGear(player: Player, e: CoreBuyOrderEntry, id: UUID) {
        val a = game.account(player) ?: return
        val item = a.storedGear.firstOrNull { it.identity.id == id } ?: return orders(player, e.order.tier)
        view(player, "この装備を納品しますか", { confirmOrderGear(player, e, id) }, nativeChest = true) { v ->
            v.items[22] = CoreLoopItems.gear(item.project(a), item.slot, v.packed)
            v.items[31] = CoreLoopItems.icon(Material.BOOK, "MOD・製造品質も相手へ移ります", "手取り${e.order.unitPrice - CoreEconomy.fee(e.order.unitPrice)}枚", "個体 ${item.identity.id.toString().take(8)}")
            v.items[40] = CoreLoopItems.icon(Material.LIME_DYE, "この装備を渡して銀貨を受け取る")
            v.actions[40] = { mutate(v, player, CoreAction.FillBuyOrder(e.buyer, e.order.id, e.order.unitPrice, gearId = id), a.revision) { orders(player, e.order.tier) } }
            v.items[45] = CoreLoopItems.icon(Material.ARROW, "戻る"); v.actions[45] = { orderDetail(player, e) }
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
            back(v, player) { professions(player) }
            tile(v, 51, 3, "道具箱", CoreLoopItems.icon(Material.CHEST, "無料で採取道具を用意")) { tools(player) }
        }
    }

    private fun confirmReturn(player: Player) {
        view(player, "遠征 / 帰還の確認", { confirmReturn(player) }) { v ->
            v.canvas.left("港へ帰還", if (game.dungeonView(player) != null) lines("自分だけ探索を終了", "仲間の探索は継続", "この深殿へ戻れません") else lines("今の遠征を終了", "このマップは閉鎖", "同じ地図へ戻れません"), hero = CoreMenuArt.RETURN)
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
            v.canvas.left("操作", lines("絵や名前をクリック", "明るい縁は選択中", "金色は実行ボタン", "赤・暗色は使用不可", "選択だけでは未消費", "右下で確定します", "ESCで閉じられます"), hero = CoreMenuArt.HELP)
            v.canvas.right("保管と表示", lines("倉庫は1枠1種類", "重ね数は最大64", "正確な数は説明欄", "素材は自動で保管", "工房で直接使えます", "持ち出しは不要", "", "工房の × は必要数", "足りる：素材は充足", "あと何個：不足数", "入手先から調達", "装備の全情報は詳細"))
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
                    lines("素材は倉庫から使用", "採取 → 精製 → 制作", "素材・装備は市場へ", "戦利品券は銀貨へ", "装備庫から装備変更", "欠片3個で専用ボスへ")
            }
            v.canvas.left("基本の流れ", content.first); v.canvas.right("覚えておくこと", content.second)
            card(v, 19, 3, 3, "道具箱", CoreMenuArt.GATHER, CoreLoopItems.icon(Material.WOODEN_AXE, "採取道具を選ぶ")) { tools(player) }
            card(v, 23, 3, 3, "手帳へ", CoreMenuArt.HELP, CoreLoopItems.icon(Material.NETHER_STAR, "冒険の手帳へ戻る"), Tone.PRIMARY) { journal(player) }
            back(v, player)
        }
    }

    fun dungeons(player: Player, tier: Int = 1, ascension: Int = 0) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        val own = game.dungeonParties().firstOrNull { player.uuid in it.members }
        if (own != null) { dungeonParty(player, own); return }
        val maximum = minOf(CoreMmoTuning.balance.dungeonMaxAscension, (a.dungeonRecords[tier] ?: -1) + 1)
        val depth = ascension.coerceIn(0, maximum)
        val allowed = tier <= a.unlockedMapTier && !a.weaponBroken
        view(player, "星環の深殿 / T$tier 深度$depth", { dungeons(player, tier, depth) }) { v ->
            tiers(v, tier) { dungeons(player, it) }; help(v, player) { dungeons(player, tier, depth) }
            v.canvas.left("分岐する迷宮", lines("1〜4人で攻略", "${CoreMmoTuning.balance.dungeonFloors}層・${CoreMmoTuning.balance.dungeonStages}部屋を選ぶ", "入場料なし", "部屋ごとに報酬確定", "加護は周回限定", "", "一つ前を踏破すると", "次の深度を解放"), hero = CoreMenuArt.EXPEDITION)
            v.canvas.right("深度 $depth", lines(if (tier in a.dungeonRecords) "最高踏破 ${a.dungeonRecords[tier]}" else "踏破記録なし", "4〜 精鋭の増援", "8〜 星落とし拡大", "12〜 複合予兆", "", "ボスは4形態", "報酬：オーブと券", "採取原料は出ません", if (allowed) "挑戦できます" else "未解放・武器破損"), hero = CoreMenuArt.BOSS)
            card(v, 9, 3, 3, "一人で", CoreMenuArt.WEAPON, CoreLoopItems.icon(Material.IRON_SWORD, "一人で出発", "クリックで生成・転送を開始"), if (allowed) Tone.PRIMARY else Tone.DISABLED) {
                game.dungeonLobby(player, DungeonLobbyAction.Solo(tier, depth)); if (!game.isDeparting(player)) dungeons(player, tier, depth)
            }
            card(v, 12, 3, 3, "募集する", CoreMenuArt.GEAR, CoreLoopItems.icon(Material.CAMPFIRE, "仲間を募集", "港の掲示から参加できます"), if (allowed) Tone.NEUTRAL else Tone.DISABLED) {
                game.dungeonLobby(player, DungeonLobbyAction.Create(tier, depth)); dungeons(player, tier, depth)
            }
            card(v, 15, 3, 3, "参加する", CoreMenuArt.EXPEDITION, CoreLoopItems.icon(Material.PLAYER_HEAD, "募集中のパーティへ")) { dungeonParties(player) }
            tile(v, 36, 3, "浅く", CoreLoopItems.icon(Material.ARROW, "深度を下げる"), if (depth > 0) Tone.NEUTRAL else Tone.DISABLED) { dungeons(player, tier, depth - 1) }
            tile(v, 39, 3, "深度$depth", CoreLoopItems.icon(Material.BOOK, "今の難度"), Tone.SELECTED)
            tile(v, 42, 3, "深く", CoreLoopItems.icon(Material.ARROW, "深度を上げる"), if (depth < maximum) Tone.NEUTRAL else Tone.DISABLED) { dungeons(player, tier, depth + 1) }
            back(v, player)
            card(v, 51, 3, 1, "試練", CoreMenuArt.TRIAL, CoreLoopItems.icon(Material.ECHO_SHARD, "既存の欠片で挑む専用ボスへ")) { trials(player, tier) }
        }
    }

    private fun dungeonParties(player: Player, page: Int = 0) {
        if (!game.requireHub(player)) return
        val parties = game.dungeonParties().filter { !it.starting }
        val last = (parties.size - 1).coerceAtLeast(0) / 36; val p = page.coerceIn(0, last)
        view(player, "深殿 / 仲間の募集", { dungeonParties(player, p) }, nativeChest = true) { v ->
            parties.drop(p * 36).take(36).forEachIndexed { i, party ->
                v.items[9 + i] = CoreLoopItems.icon(Material.CAMPFIRE, "${game.playerName(party.leader)}のパーティ", "T${party.tier} / 深度${party.ascension}", "${party.members.size}/4人 / クリックで参加")
                v.actions[9 + i] = { game.dungeonLobby(player, DungeonLobbyAction.Join(party.id)); dungeons(player) }
            }
            if (parties.isEmpty()) v.items[22] = CoreLoopItems.icon(Material.BOOK, "募集中の仲間はいません", "自分で募集するか、一人でも挑戦できます")
            v.items[45] = CoreLoopItems.icon(Material.ARROW, "深殿へ"); v.actions[45] = { dungeons(player) }
            v.items[49] = CoreLoopItems.icon(Material.PAPER, "${p + 1}/${last + 1} 更新"); v.actions[49] = { dungeonParties(player, p) }
            if (p > 0) { v.items[47] = CoreLoopItems.icon(Material.ARROW, "前へ"); v.actions[47] = { dungeonParties(player, p - 1) } }
            if (p < last) { v.items[51] = CoreLoopItems.icon(Material.ARROW, "次へ"); v.actions[51] = { dungeonParties(player, p + 1) } }
        }
    }

    private fun dungeonParty(player: Player, snapshot: DungeonParty) {
        val p = game.dungeonParties().firstOrNull { it.id == snapshot.id } ?: return dungeons(player)
        view(player, "深殿 / T${p.tier} 深度${p.ascension} / 出発準備", { dungeonParty(player, p) }, nativeChest = true) { v ->
            p.members.forEachIndexed { i, id -> v.items[20 + i] = CoreLoopItems.icon(if (id in p.ready) Material.LIME_DYE else Material.PLAYER_HEAD,
                game.playerName(id) + if (id == p.leader) " [隊長]" else "", if (id in p.ready) "準備完了" else "準備中") }
            v.items[31] = CoreLoopItems.icon(Material.BOOK, "全員の準備完了後、隊長が出発", "装備・回復薬を準備してから完了にしてください", "途中参加なし / 各自で帰還可能", "クリックで状態を更新"); v.actions[31] = { dungeonParty(player, p) }
            v.items[39] = CoreLoopItems.icon(if (player.uuid in p.ready) Material.YELLOW_DYE else Material.LIME_DYE, if (player.uuid in p.ready) "準備完了を取り消す" else "準備完了にする")
            v.actions[39] = { game.dungeonLobby(player, DungeonLobbyAction.Ready); dungeons(player) }
            val allowed = player.uuid == p.leader && p.ready.size == p.members.size
            v.items[41] = CoreLoopItems.icon(if (allowed) Material.ENDER_PEARL else Material.BARRIER, "全員で出発", if (player.uuid != p.leader) "隊長が出発を決めます" else "準備完了 ${p.ready.size}/${p.members.size}")
            if (allowed) v.actions[41] = { game.dungeonLobby(player, DungeonLobbyAction.Start); if (!game.isDeparting(player)) dungeons(player) }
            v.items[45] = CoreLoopItems.icon(Material.ARROW, "手帳へ（パーティを維持）"); v.actions[45] = { journal(player) }
            v.items[53] = CoreLoopItems.icon(Material.BARRIER, "パーティを抜ける"); v.actions[53] = { game.dungeonLobby(player, DungeonLobbyAction.Leave); dungeons(player) }
        }
    }

    fun dungeonRun(player: Player) {
        val state = game.dungeonView(player) ?: return
        val ready = state.phase == DungeonRunPhase.CHOOSING
        view(player, "深殿 / ${state.room.stage}の間 / 深度${state.ascension}", { dungeonRun(player) }) { v ->
            if (ready && !state.chosen) v.canvas.text(8, 20, "加護は1つ選択・周回限定", CoreUiComponents.GOLD, 160)
            v.canvas.left("探索の記録", lines("${state.room.stage} / ${state.stages} 部屋", state.room.theme.displayName, "復活 残り${state.revives}", "", "隊長 ${game.playerName(state.leader)}".take(18),
                if (ready) "加護は自分で選ぶ" else if (state.phase == DungeonRunPhase.COMPLETE) "踏破報酬を保存済み" else "獲得済み報酬は保持", if (ready) "未選択 ${state.waitingFor}人" else "途中帰還できます"), hero = CoreMenuArt.EXPEDITION)
            v.canvas.right("今回の加護", (if (state.blessings.isEmpty()) lines("まだありません") else state.blessings.entries.take(9).map { Line("${it.key.displayName} ×${it.value}") }) + lines("", "港へ戻ると消えます"), hero = CoreMenuArt.ORB)
            if (ready && !state.chosen) state.boonOffers.forEachIndexed { i, boon ->
                card(v, 9 + i * 3, 3, 3, boon.displayName, when (boon) { DungeonBoon.VITALITY, DungeonBoon.GUARD -> CoreMenuArt.ARMOR; DungeonBoon.FLAME, DungeonBoon.FROST, DungeonBoon.STORM -> CoreMenuArt.ORB; else -> CoreMenuArt.WEAPON },
                    CoreLoopItems.icon(Material.AMETHYST_SHARD, boon.displayName, boon.description, if (state.room.kind in setOf(DungeonRoomKind.BOSS, DungeonRoomKind.ELITE)) "強い加護：2段階分" else "1段階分", "この周回だけ有効 / クリックで確定"), Tone.PRIMARY) { game.dungeonBoon(player, boon) }
            } else card(v, 12, 3, 3, if (ready) "選択済み" else if (state.phase == DungeonRunPhase.COMPLETE) "踏破" else "探索再開", CoreMenuArt.EXPEDITION,
                CoreLoopItems.icon(Material.BOOK, state.objective, "画面を閉じて続ける")) { player.closeInventory() }
            if (ready) state.choices.forEachIndexed { i, room -> card(v, 36 + i * 3, 3, 1, room.kind.displayName.removeSuffix("の間"),
                if (room.kind == DungeonRoomKind.BOSS) CoreMenuArt.BOSS else CoreMenuArt.EXPEDITION,
                CoreLoopItems.icon(Material.ENDER_PEARL, "次：${room.kind.displayName}", room.kind.description, "全員の加護選択後、隊長が進路を決定"),
                if (state.waitingFor == 0 && state.leader == player.uuid) Tone.PRIMARY else Tone.DISABLED) { game.dungeonRoute(player, room.id) } }
            tile(v, 45, 3, "港へ帰還", CoreLoopItems.icon(Material.COMPASS, "途中帰還の確認", "この深殿には戻れません")) { confirmReturn(player) }
            tile(v, 48, 3, "更新", CoreLoopItems.icon(Material.PAPER, "仲間の選択状況を更新")) { dungeonRun(player) }
            tile(v, 51, 3, "閉じる", CoreLoopItems.icon(Material.BARRIER, "画面を閉じる")) { player.closeInventory() }
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
