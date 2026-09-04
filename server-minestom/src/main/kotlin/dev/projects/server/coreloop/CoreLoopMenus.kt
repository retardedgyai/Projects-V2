package dev.projects.server.coreloop

import dev.projects.server.questmap.*
import dev.projects.server.coreloop.ui.*
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.inventory.click.Click
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.UUID
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** One owned inventory per viewer. Display items never become transferable rewards. */
internal class CoreLoopMenus(private val game: CoreLoopGame) {
    private data class View(val inventory: Inventory, val actions: MutableMap<Int, (Boolean) -> Unit>)
    private val views = ConcurrentHashMap<UUID, View>()
    private val forgeSelections = ConcurrentHashMap<UUID, CoreForgeLayout.Selection>()
    private val contentSlots = (19..43).filter { it % 9 in 1..7 }

    fun click(event: InventoryPreClickEvent): Boolean {
        val view = views[event.player.uuid] ?: return false
        if (event.player.openInventory !== view.inventory) return false
        // Cancel every click form, including bottom inventory, drag, shift and number keys.
        event.isCancelled = true
        if (event.inventory !== view.inventory || event.slot !in 0 until 54) return true
        if (event.click is Click.Left || event.click is Click.Right) {
            view.actions[event.slot]?.invoke(event.click is Click.Right)
        }
        return true
    }

    fun forget(playerId: UUID) { views.remove(playerId); forgeSelections.remove(playerId); forgeViewers.remove(playerId) }
    fun refreshTheme(player: Player) {
        val view = views[player.uuid] ?: return
        if (player.openInventory === view.inventory) {
            if (forgeSelections.containsKey(player.uuid) && forgeViewers.contains(player.uuid)) forge(player, forgeSelections.getValue(player.uuid))
            else journal(player)
        }
    }

    private val forgeViewers = ConcurrentHashMap.newKeySet<UUID>()
    private fun view(player: Player, title: String, forge: Boolean = false, emptyForge: Boolean = false, build: (View) -> Unit) {
        if (forge) forgeViewers.add(player.uuid) else forgeViewers.remove(player.uuid)
        val v = View(Inventory(InventoryType.CHEST_6_ROW, CoreUiComponents.inventoryTitle(title, game.packed(player), forge, emptyForge)), mutableMapOf())
        for (slot in 0 until 54) if (slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8) {
            v.inventory.setItemStack(slot, CoreUiItemSkin.blank(CoreLoopItems.icon(Material.GRAY_STAINED_GLASS_PANE, " "), game.packed(player)))
        }
        build(v)
        for (slot in 0 until 54) {
            val item = v.inventory.getItemStack(slot)
            if (!item.isAir && item.get(net.minestom.server.component.DataComponents.TOOLTIP_STYLE) == null)
                v.inventory.setItemStack(slot, CoreLoopItems.menuSkin(item, game.packed(player)))
        }
        views[player.uuid] = v
        player.openInventory(v.inventory)
    }

    private fun button(v: View, slot: Int, item: ItemStack, action: (Boolean) -> Unit) {
        v.inventory.setItemStack(slot, item)
        v.actions[slot] = action
    }

    private fun back(v: View, player: Player, action: () -> Unit = { journal(player) }) =
        button(v, 45, CoreLoopItems.icon(Material.ARROW, "戻る")) { action() }

    private fun tiers(v: View, selected: Int, action: (Int) -> Unit) {
        (1..4).forEach { tier -> button(v, tier + 1, CoreLoopItems.icon(Material.PAPER, "T$tier${if (tier == selected) "  選択中" else ""}",
            "このTierの素材・地図を表示", color = CoreLoopItems.colors[tier - 1]).withGlowing(tier == selected)) { action(tier) } }
    }

    fun journal(player: Player) {
        val a = game.account(player) ?: return
        if (game.isDeparting(player)) { player.sendMessage(CoreLoopItems.text("遠征を準備しています。しばらくお待ちください。")); return }
        if (a.activeRun != null) {
            view(player, "冒険の手帳 — 遠征中") { v ->
                val won = a.activeRun.bossDefeated
                v.inventory.setItemStack(13, CoreLoopItems.icon(if (won) Material.DRAGON_EGG else Material.IRON_SWORD,
                    if (won) "討伐達成！" else "T${a.activeRun.map.tier} 未踏の地を探索",
                    if (won) "討伐証と次の地図は倉庫に保存済み" else "道の先のボスを倒そう",
                    "雑魚戦・採取・寄り道は自由", "採取した素材はすべて自動保存", "${game.sessionSummary(player)}"))
                button(v, 30, CoreLoopItems.icon(Material.COMPASS, "港へ帰還",
                    "途中帰還でも獲得した素材は残ります", "現在のマップは閉じられます")) { confirmReturn(player) }
                button(v, 32, CoreLoopItems.icon(Material.CHEST, "獲得素材を見る", "採取物・戦利品は個人倉庫へ")) { storage(player, a.activeRun.map.tier) }
                button(v, 40, CoreLoopItems.icon(Material.WOODEN_AXE, "採取道具を選ぶ")) { tools(player) }
                button(v, 22, CoreLoopItems.icon(Material.AMETHYST_SHARD, "オーブと装備", "通貨と現在の装備を確認", "MODの抽選は港の刻印工房で")) { affixes(player) }
                button(v, 49, CoreLoopItems.icon(Material.BOOK, "操作ガイド")) { guide(player) }
            }
            return
        }
        view(player, "開拓港 — 冒険の手帳") { v ->
            v.inventory.setItemStack(4, CoreLoopItems.icon(Material.LANTERN, "おかえり、${player.username}",
                "武器 T${a.weaponTier} / 防具 T${a.armorTier}", "挑戦可能な地図：T1〜T${a.unlockedMapTier}"))
            button(v, 11, CoreLoopItems.icon(Material.CARTOGRAPHY_TABLE, "地図台 — 遠征へ", "地図を選ぶ → 石板で調整 → 出発", "T1地図は何度でも無料")) { expeditions(player) }
            button(v, 13, CoreLoopItems.icon(Material.ANVIL, "工房 — 装備を更新", "素材を精製して武器・防具を作る", "完成した装備は自動で装着")) { workshop(player) }
            button(v, 15, CoreLoopItems.icon(Material.BARREL, "素材倉庫", "採取・討伐の成果を確認", "素材はログアウトしても残ります")) { storage(player) }
            button(v, 22, CoreLoopItems.icon(Material.ENCHANTING_TABLE, "刻印工房 — ランダムMOD", "オーブで昇格・再抽選・MOD追加", "何が付くかは使うまで分かりません", "装着MOD ${a.equippedAffixes.size}個")) { affixes(player) }
            button(v, 29, CoreLoopItems.icon(Material.GOLD_NUGGET, "補給所 — 素材交換", "戦利品券を欲しい素材へ", "採取せず戦闘中心でも装備を作れます")) { supplies(player) }
            button(v, 31, CoreLoopItems.icon(Material.OAK_SAPLING, "採取の心得", "マスタリーと採取道具")) { mastery(player) }
            button(v, 33, CoreLoopItems.icon(Material.BOOK, "操作ガイド", "最初の一周と戦闘・採取の操作")) { guide(player) }
            button(v, 40, CoreLoopItems.icon(Material.END_PORTAL_FRAME, "境界の試練 — 専用ボス", "裂け目・儀式・討伐から欠片を集める", "欠片3個で専用フィールドへ挑戦", "専用オーブと高揚のオーブを狙う")) { trials(player) }
            v.inventory.setItemStack(49, CoreLoopItems.icon(Material.CAMPFIRE, "次にすること", *game.nextSteps(a).toTypedArray()))
        }
    }

    fun expeditions(player: Player, tier: Int = 1, page: Int = 0) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        view(player, "地図台 — T$tier の遠征") { v ->
            tiers(v, tier) { expeditions(player, it) }
            val available = tier <= a.unlockedMapTier
            button(v, 13, CoreLoopItems.icon(if (available) Material.MAP else Material.BARRIER,
                if (available) "T$tier 地図を受け取る" else "T$tier は未解放",
                if (tier == 1) "費用なし / いつでも再挑戦できます" else "費用：T${tier - 1} 戦利品券 1枚",
                "前Tierのボスを倒すと解放")) {
                if (available) game.mutate(player, CoreAction.ClaimMap(tier, System.nanoTime()), a.revision) { expeditions(player, tier, page) }
            }
            val maps = a.maps.filter { it.tier == tier }
            val lastPage = (maps.size - 1).coerceAtLeast(0) / contentSlots.size
            val actualPage = page.coerceIn(0, lastPage)
            maps.drop(actualPage * contentSlots.size).take(contentSlots.size).forEachIndexed { index, map ->
                button(v, contentSlots[index], CoreLoopItems.map(map)) { mapDetail(player, map.id) }
            }
            if (maps.isEmpty()) v.inventory.setItemStack(31, CoreLoopItems.icon(Material.PAPER, "地図がありません", "上の白紙の地図をクリックして受け取ろう"))
            if (actualPage > 0) button(v, 48, CoreLoopItems.icon(Material.ARROW, "前のページ")) { expeditions(player, tier, actualPage - 1) }
            if (actualPage < lastPage) button(v, 50, CoreLoopItems.icon(Material.ARROW, "次のページ")) { expeditions(player, tier, actualPage + 1) }
            back(v, player)
        }
    }

    fun mapDetail(player: Player, id: UUID) {
        val a = game.account(player) ?: return
        val map = a.maps.firstOrNull { it.id == id } ?: return expeditions(player)
        val ready = game.warmMap(player, map)
        view(player, "地図台 — T${map.tier} 遠征の準備") { v ->
            v.inventory.setItemStack(13, CoreLoopItems.map(map))
            button(v, 22, CoreLoopItems.icon(Material.LIME_DYE, "この地図で出発", "地図1枚を消費", "道を進めばボスに到達 / 寄り道は自由",
                "目安装備 T${map.tier} / 現在の武器 T${a.weaponTier}",
                if (ready) "地形準備済み — 出発できます" else "選択中の地図を裏で準備しています")) { game.depart(player, map.id, a.revision) }
            button(v, 30, CoreLoopItems.icon(Material.AMETHYST_SHARD, "採取MODを刻む", "採取の石板 1枚を消費 / 所持 ${a.amount(CoreResource.GATHERING_TABLET)}",
                "最大3個 / 生成量・品質・密集地域", "付いたMODは実際の生成に反映")) {
                game.applyTablet(player, map.id, a.revision) { mapDetail(player, id) }
            }
            button(v, 32, CoreLoopItems.icon(Material.FILLED_MAP, "地図を手元に用意", "ホットバー8番に持ち出す", "Eの中で石板を重ねても付与できます")) {
                player.inventory.setItemStack(7, CoreLoopItems.map(map)); player.setHeldItemSlot(7); player.closeInventory()
            }
            back(v, player) { expeditions(player, map.tier) }
        }
    }

    fun workshop(player: Player, tier: Int = game.account(player)?.weaponTier ?: 1) {
        val selection = forgeSelections[player.uuid] ?: CoreForgeLayout.Selection()
        forge(player, selection.copy(tier = tier.coerceIn(1, 4)))
    }

    /** One workbench, four tabs. Selection never consumes anything; only the gold execute button does. */
    private fun forge(player: Player, requested: CoreForgeLayout.Selection) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        val s = requested.copy(tier = requested.tier.coerceIn(1, 4))
        forgeSelections[player.uuid] = s
        val empty = s.tab == CoreForgeLayout.Tab.MODS && s.currency == null
        view(player, "開拓工房 — ${s.tab.label}", forge = true, emptyForge = empty) { v ->
            CoreForgeLayout.Tab.entries.forEach { tab ->
                val icon = when (tab) {
                    CoreForgeLayout.Tab.ENHANCE -> Material.ANVIL
                    CoreForgeLayout.Tab.REFINE -> Material.BLAST_FURNACE
                    CoreForgeLayout.Tab.CRAFT -> Material.CRAFTING_TABLE
                    CoreForgeLayout.Tab.MODS -> Material.ENCHANTING_TABLE
                }
                button(v, tab.slot, CoreLoopItems.icon(icon, "${tab.label}${if (s.tab == tab) " — 選択中" else ""}",
                    "対象の${s.gear.displayName}を保持して切り替え", color = if (s.tab == tab) NamedTextColor.GOLD else NamedTextColor.GRAY).withGlowing(s.tab == tab)) {
                    forge(player, s.copy(tab = tab, recipe = 0, quantity = CoreForgeLayout.Quantity.ONE, currency = null))
                }
            }
            CoreGearSlot.entries.forEach { gear ->
                button(v, if (gear == CoreGearSlot.WEAPON) CoreForgeLayout.WEAPON else CoreForgeLayout.ARMOR,
                    CoreLoopItems.gear(a, gear, game.packed(player)).withGlowing(s.gear == gear)) {
                    forge(player, s.copy(gear = gear, recipe = 0, currency = null))
                }
            }
            button(v, 4, CoreLoopItems.icon(Material.BOOK, "対象：${s.gear.displayName}", "左右の装備をクリックして変更", "クリック：現在のMODを詳しく見る")) { gearMods(player, s.gear) }
            val supplyTier = if (s.tab == CoreForgeLayout.Tab.ENHANCE)
                CoreEnhancementCatalog.quote(a, s.gear).recipe.costs.keys.firstOrNull()?.tier ?: s.tier else s.tier
            button(v, 36, CoreLoopItems.icon(Material.BARREL, "T$supplyTier 倉庫・補給へ", "所持品は倉庫から直接消費", "素材が不足したら戦利品券で交換")) { supplies(player, supplyTier) }
            back(v, player)
            v.inventory.setItemStack(7, CoreLoopItems.icon(Material.GOLD_NUGGET, "③ 必要素材", "緑：足りています / 赤：不足", "数字は今回の消費数 / 所持は各素材で確認"))
            when (s.tab) {
                CoreForgeLayout.Tab.ENHANCE -> enhanceTab(v, player, a, s)
                CoreForgeLayout.Tab.REFINE, CoreForgeLayout.Tab.CRAFT -> recipeTab(v, player, a, s)
                CoreForgeLayout.Tab.MODS -> modTab(v, player, a, s)
            }
        }
    }

    private fun forgeTier(v: View, player: Player, s: CoreForgeLayout.Selection) {
        v.inventory.setItemStack(1, CoreLoopItems.icon(Material.PAPER,
            if (s.tab == CoreForgeLayout.Tab.CRAFT) "① 制作のレシピ" else "① T${s.tier} レシピ",
            "消耗品・精製の使用素材：T${s.tier}", "装備制作は現在の装備Tierの素材を使用", "下のTボタンで素材Tierを変更"))
        if (s.tier > 1) button(v, 46, CoreLoopItems.icon(Material.ARROW, "T${s.tier - 1}の素材へ")) { forge(player, s.copy(tier = s.tier - 1)) }
        if (s.tier < 4) button(v, 50, CoreLoopItems.icon(Material.ARROW, "T${s.tier + 1}の素材へ")) { forge(player, s.copy(tier = s.tier + 1)) }
    }

    private fun costCards(v: View, player: Player, a: CoreAccount, recipe: CoreRecipe, s: CoreForgeLayout.Selection) {
        check(recipe.costs.size <= CoreForgeLayout.COSTS.size)
        recipe.costs.entries.forEachIndexed { index, (material, amount) ->
            val held = a.amount(material)
            val missing = (amount - held).coerceAtLeast(0)
            val refine = CoreForgeLayout.refineSelection(material, s)
            val shortcut = when {
                refine != null -> "クリック：T${material.tier}の精製へ"
                material.resource.raw -> "クリック：戦利品券で補給"
                material.resource == CoreResource.BOSS_SIGIL -> "クリック：討伐証を探しに遠征"
                material.resource == CoreResource.AFFIX_DUST -> "魔物を討伐して入手"
                else -> "倉庫の素材を直接使用します"
            }
            val item = CoreLoopItems.icon(CoreLoopItems.resourceMaterial(material.resource), material.displayName,
                "必要 $amount 個 / 所持 $held 個", if (missing == 0L) "足りています" else "あと $missing 個必要",
                shortcut, color = if (missing == 0L) NamedTextColor.GREEN else NamedTextColor.RED).withAmount(amount.coerceIn(1, 64).toInt())
            button(v, CoreForgeLayout.COSTS[index], item) {
                when {
                    refine != null -> forge(player, refine)
                    material.resource.raw -> supplies(player, material.tier, material.resource)
                    material.resource == CoreResource.BOSS_SIGIL -> expeditions(player, material.tier)
                }
            }
        }
    }

    private data class ForgeRecipe(val icon: Material, val unit: CoreRecipe, val batches: Boolean = true, val build: (Int) -> Pair<CoreRecipe, CoreAction>)

    private fun recipeTab(v: View, player: Player, a: CoreAccount, s: CoreForgeLayout.Selection) {
        forgeTier(v, player, s)
        val recipes = if (s.tab == CoreForgeLayout.Tab.REFINE) {
            CoreLoopCatalog.refined.map { (raw, refined) ->
                ForgeRecipe(CoreLoopItems.resourceMaterial(refined), CoreLoopCatalog.refine(raw, s.tier)) { count ->
                    CoreLoopCatalog.refine(raw, s.tier, count) to CoreAction.Refine(raw, s.tier, count)
                }
            }
        } else buildList {
            val tier = CoreAffixCatalog.gearTier(a, s.gear)
            if (tier < 4) {
                val recipe = if (s.gear == CoreGearSlot.WEAPON) CoreLoopCatalog.weaponUpgrade(tier) else CoreLoopCatalog.armorUpgrade(tier)
                add(ForgeRecipe(if (s.gear == CoreGearSlot.WEAPON) Material.IRON_SWORD else Material.IRON_CHESTPLATE, recipe, false) {
                    recipe to if (s.gear == CoreGearSlot.WEAPON) CoreAction.UpgradeWeapon else CoreAction.UpgradeArmor
                })
            }
            listOf(CoreResource.POTION, CoreResource.GATHERING_TABLET, CoreResource.WHETSTONE).forEach { resource ->
                add(ForgeRecipe(CoreLoopItems.resourceMaterial(resource), CoreLoopCatalog.craft(resource, tier = s.tier)) { count ->
                    CoreLoopCatalog.craft(resource, count, s.tier) to CoreAction.Craft(resource, count, s.tier)
                })
            }
        }
        val selected = s.recipe.coerceIn(0, recipes.lastIndex)
        recipes.forEachIndexed { index, entry ->
            button(v, CoreForgeLayout.RECIPES[index], CoreLoopItems.icon(entry.icon, entry.unit.displayName,
                if (selected == index) "選択中 — 中央の成果と右の費用を確認" else "クリック：レシピを選択",
                if (entry.unit.canAfford(a)) "1回分の素材あり" else "素材不足 — 選んで必要数を確認").withGlowing(selected == index)) {
                forge(player, s.copy(recipe = index, quantity = CoreForgeLayout.Quantity.ONE))
            }
        }
        val entry = recipes[selected]
        val maximum = if (entry.batches) CoreForgeLayout.maxBatches(a, entry.unit) else 1
        val count = if (entry.batches) CoreForgeLayout.batches(s.quantity, maximum) else 1
        val (recipe, action) = entry.build(count)
        costCards(v, player, a, recipe, s)
        v.inventory.setItemStack(13, CoreLoopItems.icon(Material.ITEM_FRAME, "② ${recipe.displayName}", "選ぶだけでは消費されません"))
        v.inventory.setItemStack(CoreForgeLayout.TARGET, if (entry.batches) CoreLoopItems.icon(entry.icon, recipe.displayName,
            "T${s.tier}素材を使用 / $count 回分", "倉庫の素材から直接制作") else CoreLoopItems.gear(a, s.gear, game.packed(player)))
        val result = if (entry.batches) recipe.outputs.map { "${it.key.displayName} ×${it.value}" } else listOf(
            "T${CoreAffixCatalog.gearTier(a, s.gear)} → T${CoreAffixCatalog.gearTier(a, s.gear) + 1} ${s.gear.displayName}", "装備Tierを上げ、次の遠征へ")
        v.inventory.setItemStack(CoreForgeLayout.RESULT, CoreLoopItems.icon(entry.icon, "完成品", *result.toTypedArray(), color = NamedTextColor.GREEN))
        val masteryGain = minOf(if (entry.batches) count.toLong() else 5L, CoreEnhancementCatalog.MAX_SMITHING_XP - a.smithingXp)
        v.inventory.setItemStack(CoreForgeLayout.DETAIL, CoreLoopItems.icon(Material.EXPERIENCE_BOTTLE,
            if (masteryGain == 0L) "鍛冶熟練は最大です" else "鍛冶熟練が成長", "今回の熟練XP +$masteryGain", "熟練が上がると強化成功率も上昇"))
        if (entry.batches) quantityButtons(v, s.quantity, maximum) { quantity -> forge(player, s.copy(quantity = quantity)) }
        val blocked = when {
            !recipe.canAfford(a) -> "素材不足 — 右側の赤い素材を確認"
            entry.batches && count > maximum -> "完成品の保管上限、または素材不足"
            else -> null
        }
        execute(v, player, if (entry.batches) "$count 回制作する" else "装備Tierを上げる", blocked, "素材を消費し、完成品を保存") {
            game.mutate(player, action, a.revision) {
                forge(player, if (entry.batches) s.copy(recipe = selected) else s.copy(tab = CoreForgeLayout.Tab.ENHANCE, recipe = 0))
            }
        }
    }

    private fun enhanceTab(v: View, player: Player, a: CoreAccount, s: CoreForgeLayout.Selection) {
        val standard = CoreEnhancementCatalog.quote(a, s.gear)
        val mode = CoreForgeLayout.enhancementMode(a, s)
        val useCatalyst = mode == CoreEnhancementMode.FOCUSED
        val quote = CoreEnhancementCatalog.quote(a, s.gear, mode)
        val rank = CoreEnhancementCatalog.masteryRank(a.smithingXp)
        val progress = CoreEnhancementCatalog.masteryProgress(a.smithingXp)
        v.inventory.setItemStack(1, CoreLoopItems.icon(Material.EXPERIENCE_BOTTLE, "鍛冶熟練 $rank / 10",
            if (a.smithingXp == CoreEnhancementCatalog.MAX_SMITHING_XP) "熟練は最大です" else "次の熟練：$progress / 20 XP",
            "成功率 +$rank ポイント", "精製・制作・強化で成長"))
        v.inventory.setItemStack(10, CoreLoopItems.icon(Material.ANVIL, "+${quote.currentLevel} → +${quote.targetLevel}", "素材を使って、装備を少しずつ強くする", "失敗しても装備・強化値・MODは保護").withGlowing(true))
        v.inventory.setItemStack(13, CoreLoopItems.icon(Material.ITEM_FRAME, "② T${CoreAffixCatalog.gearTier(a, s.gear)} ${s.gear.displayName}", "強化段階に応じた素材を使用", "Tier制作とは別の +0〜30 段階"))
        v.inventory.setItemStack(CoreForgeLayout.TARGET, CoreLoopItems.gear(a, s.gear, game.packed(player)))
        val delta = if (s.gear == CoreGearSlot.WEAPON) listOf(
            "威力の強化分：+${percent((CoreEnhancementCatalog.weaponDamageMultiplier(quote.currentLevel) - 1) * 100)}% → +${percent((CoreEnhancementCatalog.weaponDamageMultiplier(quote.targetLevel) - 1) * 100)}%",
            "攻撃速度：+${percent(CoreEnhancementCatalog.weaponAttackSpeedPercent(quote.currentLevel))}% → +${percent(CoreEnhancementCatalog.weaponAttackSpeedPercent(quote.targetLevel))}%")
        else listOf("最大HPの強化分：+${percent((CoreEnhancementCatalog.armorHealthMultiplier(quote.currentLevel) - 1) * 100)}% → +${percent((CoreEnhancementCatalog.armorHealthMultiplier(quote.targetLevel) - 1) * 100)}%")
        v.inventory.setItemStack(CoreForgeLayout.RESULT, CoreLoopItems.icon(if (s.gear == CoreGearSlot.WEAPON) Material.IRON_SWORD else Material.IRON_CHESTPLATE,
            "成功後：${s.gear.displayName} +${quote.targetLevel}", *delta.toTypedArray(), color = NamedTextColor.GREEN))
        costCards(v, player, a, quote.recipe, s)
        val pity = when {
            quote.currentLevel == CoreEnhancementCatalog.MAX_LEVEL -> "最大強化に到達しました"
            quote.guaranteed -> "今回は成功確定"
            else -> "連続失敗 ${quote.failures} / ${quote.pityThreshold} → 到達後の次回は確定"
        }
        v.inventory.setItemStack(CoreForgeLayout.DETAIL, CoreLoopItems.icon(Material.EXPERIENCE_BOTTLE,
            if (quote.currentLevel == CoreEnhancementCatalog.MAX_LEVEL) "最大強化 +${CoreEnhancementCatalog.MAX_LEVEL}" else "成功率 ${percent(quote.successChancePercent)}%",
            "基礎 ${percent(quote.baseChancePercent)} + 熟練 ${percent(quote.masteryBonusPercent)} + 触媒 ${percent(quote.catalystBonusPercent)}",
            pity, "失敗：強化値は据え置き / 素材は消費", color = if (quote.guaranteed) NamedTextColor.GREEN else NamedTextColor.GOLD))
        button(v, 47, CoreLoopItems.icon(Material.IRON_INGOT, "通常強化", "追加触媒なし / 成功率 ${percent(standard.successChancePercent)}%").withGlowing(!useCatalyst)) {
            forge(player, s.copy(focused = false))
        }
        if (!standard.guaranteed && standard.currentLevel < CoreEnhancementCatalog.MAX_LEVEL) {
            button(v, 49, CoreLoopItems.icon(Material.GLOWSTONE_DUST, "精錬触媒を使う", "成功率 +15 ポイント（最大100%）",
                "追加の加工石材・布・刻印粉を消費", "段階に応じた素材を右の費用へ合算").withGlowing(useCatalyst)) { forge(player, s.copy(focused = true)) }
        } else v.inventory.setItemStack(49, CoreLoopItems.icon(Material.LIGHT_GRAY_DYE, "追加触媒は不要", "成功確定、または最大強化です"))
        execute(v, player, "+${quote.targetLevel}へ強化する", quote.blockedReason,
            "成功率 ${percent(quote.successChancePercent)}% / 失敗時も素材を消費") {
            game.mutate(player, CoreAction.EnhanceEquipment(s.gear, mode), a.revision) { forge(player, s) }
        }
    }

    private fun percent(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.ROOT, "%.1f", value)

    private fun modTab(v: View, player: Player, a: CoreAccount, s: CoreForgeLayout.Selection) {
        button(v, 1, CoreLoopItems.icon(Material.COMPASS, "① 目的：${s.purpose.label}", "クリック：加工の目的を選ぶ", "今この装備に使える所持オーブだけ表示")) { forgePurpose(player, s) }
        button(v, 8, CoreLoopItems.icon(Material.BOOK, "加工一覧・使用条件", "使えないオーブの条件も確認", "ここではオーブを消費しません")) { forgeCurrencies(player, s) }
        val usable = CoreForgeLayout.usableCurrencies(a, s)
        check(usable.size <= CoreForgeLayout.RECIPES.size)
        usable.forEachIndexed { index, currency ->
            button(v, CoreForgeLayout.RECIPES[index], CoreLoopItems.currency(currency, a.amount(currency), game.packed(player)).withGlowing(currency == s.currency)) {
                forge(player, s.copy(currency = currency))
            }
        }
        if (usable.isEmpty()) v.inventory.setItemStack(10, CoreLoopItems.icon(Material.BOOK, "使えるオーブがありません", "別の目的・装備を選ぶか、オーブを集める", "右上の本：すべての使用条件"))
        if (a.affixStones.isNotEmpty()) button(v, 46, CoreLoopItems.icon(Material.AMETHYST_SHARD, "旧刻印石の交換", "所持 ${a.affixStones.size} 個 / 自動では消費しません")) { legacyStones(player) }
        val currency = s.currency
        if (currency == null) {
            v.inventory.setItemStack(CoreForgeLayout.RESULT, CoreLoopItems.icon(Material.BOOK, "左のオーブを選んでください", "中央：装備に何が起きるか", "右：消費するオーブ / 下：実行"))
            return
        }
        val reason = CoreCraftingCatalog.canUse(a, s.gear, currency)
        v.inventory.setItemStack(13, CoreLoopItems.icon(Material.ITEM_FRAME, "② ${s.gear.displayName}を加工", "選択したオーブ：${currency.displayName}"))
        v.inventory.setItemStack(CoreForgeLayout.TARGET, CoreLoopItems.gear(a, s.gear, game.packed(player)))
        v.inventory.setItemStack(CoreForgeLayout.COSTS.first(), CoreLoopItems.currency(currency, 1, game.packed(player), "今回の消費"))
        v.inventory.setItemStack(CoreForgeLayout.COSTS[1], CoreLoopItems.icon(Material.GOLD_NUGGET, "所持 ${a.amount(currency)} 個", if (reason == null) "この装備に使用できます" else reason,
            color = if (reason == null) NamedTextColor.GREEN else NamedTextColor.RED))
        v.inventory.setItemStack(CoreForgeLayout.RESULT, CoreLoopItems.icon(CoreLoopItems.currencyMaterial(currency), "加工後の変化",
            *wrapForgeText(CoreCraftingCatalog.description(currency)).toTypedArray(), color = NamedTextColor.GREEN))
        v.inventory.setItemStack(CoreForgeLayout.DETAIL, CoreLoopItems.icon(Material.BOOK, "実行前に確認",
            if (currency == CoreCraftingCurrency.SCOURING) "既存MODをすべて消去します" else "具体的な結果は実行時に決まります",
            "オーブの消費は取り消せません", "ロール範囲・品質・内部Tierは装備で確認"))
        execute(v, player, "オーブ1個で加工する", reason, "選択した${s.gear.displayName}に使用します") {
            game.mutate(player, CoreAction.CraftEquipment(s.gear, currency), a.revision) { forge(player, s) }
        }
    }

    private fun wrapForgeText(value: String): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        value.codePoints().forEach { code ->
            val character = String(Character.toChars(code))
            if (CoreUiComponents.width(line + character) > 180) { lines += line; line = "" }
            line += character
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    private fun forgePurpose(player: Player, s: CoreForgeLayout.Selection) {
        view(player, "刻印加工 — 何を変えますか") { v ->
            CoreForgeLayout.Purpose.entries.forEachIndexed { index, purpose ->
                button(v, 20 + index, CoreLoopItems.icon(Material.COMPASS, purpose.label, "使える所持オーブをこの目的で絞る").withGlowing(s.purpose == purpose)) {
                    forge(player, s.copy(purpose = purpose, currency = null))
                }
            }
            back(v, player) { forge(player, s) }
        }
    }

    private fun forgeCurrencies(player: Player, s: CoreForgeLayout.Selection) {
        val a = game.account(player) ?: return
        view(player, "刻印加工 — 全オーブの使用条件") { v ->
            CoreCraftingCurrency.entries.forEachIndexed { index, currency ->
                val reason = CoreCraftingCatalog.canUse(a, s.gear, currency)
                button(v, contentSlots[index], CoreLoopItems.icon(CoreLoopItems.currencyMaterial(currency), currency.displayName,
                    *wrapForgeText(CoreCraftingCatalog.description(currency)).plus("所持 ${a.amount(currency)} 個").plus(reason ?: "使用できます — クリックで選ぶ").toTypedArray(),
                    color = if (reason == null) NamedTextColor.GREEN else NamedTextColor.GRAY)) {
                    forge(player, s.copy(tab = CoreForgeLayout.Tab.MODS, currency = currency))
                }
            }
            back(v, player) { forge(player, s) }
        }
    }

    private fun quantityButtons(v: View, selected: CoreForgeLayout.Quantity, maximum: Int, action: (CoreForgeLayout.Quantity) -> Unit) {
        CoreForgeLayout.QUANTITIES.forEach { (slot, quantity) ->
            val count = CoreForgeLayout.batches(quantity, maximum)
            val label = if (quantity == CoreForgeLayout.Quantity.MAX) "最大 $maximum 回" else "$count 回"
            button(v, slot, CoreLoopItems.icon(if (maximum >= count) Material.PAPER else Material.GRAY_DYE, label,
                "クリック：今回の回数を選択", "最大64回 / 完成個数は中央を確認").withGlowing(quantity == selected)) { action(quantity) }
        }
    }

    private fun execute(v: View, player: Player, label: String, blocked: String?, detail: String, action: () -> Unit) {
        button(v, CoreForgeLayout.EXECUTE, CoreLoopItems.icon(if (blocked == null) Material.LIME_DYE else Material.BARRIER,
            if (blocked == null) "④ $label" else "④ 実行できません", blocked ?: detail,
            color = if (blocked == null) NamedTextColor.GREEN else NamedTextColor.RED)) {
            if (blocked == null && game.requireHub(player)) action()
        }
    }

    private fun recipeIcon(recipe: CoreRecipe, a: CoreAccount, material: Material): ItemStack = CoreLoopItems.icon(material, recipe.displayName,
        *recipe.costs.map { (key, count) -> "${key.displayName} $count 個 / 所持 ${a.amount(key)}" }
            .plus(recipe.outputs.map { (key, count) -> "完成：${key.displayName} ×$count" })
            .plus(if (recipe.canAfford(a)) "クリック：制作内容を確認" else "素材不足 — 補給所で戦利品券と交換も可能").toTypedArray(),
        color = if (recipe.canAfford(a)) NamedTextColor.GREEN else NamedTextColor.GRAY)

    fun storage(player: Player, tier: Int = 1, page: Int = 0) {
        val a = game.account(player) ?: return
        val entries = CoreStorageView.entries(a, tier)
        val last = (entries.size - 1).coerceAtLeast(0) / contentSlots.size
        val current = page.coerceIn(0, last)
        view(player, "素材倉庫 — T$tier") { v ->
            tiers(v, tier) { storage(player, it) }
            entries.drop(current * contentSlots.size).take(contentSlots.size).forEachIndexed { index, entry ->
                when (entry) {
                    is CoreStorageView.Entry.Material -> v.inventory.setItemStack(contentSlots[index], CoreLoopItems.resource(entry.material, entry.count))
                    is CoreStorageView.Entry.Currency -> button(v, contentSlots[index], CoreLoopItems.currency(entry.currency, entry.count, game.packed(player))) { affixes(player) }
                    is CoreStorageView.Entry.Fragment -> button(v, contentSlots[index], CoreLoopItems.fragment(entry.kind, entry.count)) { trials(player, tier) }
                }
            }
            v.inventory.setItemStack(13, CoreLoopItems.icon(Material.BARREL, "所持品 ${entries.size}種類 — ${current + 1}/${last + 1}", "所持数が1以上の物だけを表示", "通貨・欠片は全Tier共通", "取り出さず、そのまま工房で使えます"))
            if (entries.isEmpty()) v.inventory.setItemStack(31, CoreLoopItems.icon(Material.BOOK, "このTierの保管品はありません", "採取・討伐で獲得するとここに並びます"))
            if (a.activeRun == null) button(v, 49, CoreLoopItems.icon(Material.ANVIL, "工房へ")) { workshop(player, tier) }
            if (current > 0) button(v, 48, CoreLoopItems.icon(Material.ARROW, "前のページ")) { storage(player, tier, current - 1) }
            if (current < last) button(v, 50, CoreLoopItems.icon(Material.ARROW, "次のページ")) { storage(player, tier, current + 1) }
            back(v, player)
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
        view(player, "補給所 — 戦利品を素材へ") { v ->
            tiers(v, tier) { supplies(player, it, selected, quantity) }
            v.inventory.setItemStack(13, CoreLoopItems.icon(Material.GOLD_NUGGET, "T$tier 戦利品券：${a.amount(CoreResource.COMBAT_TOKEN, tier)}枚",
                "雑魚とボスの討伐で獲得", "1枚 → 同じTierの素材4個"))
            CoreResource.entries.filter { it.raw }.forEachIndexed { index, raw ->
                button(v, 20 + index, recipeIcon(CoreLoopCatalog.exchange(raw, tier), a, CoreLoopItems.resourceMaterial(raw)).withGlowing(raw == selected)) {
                    supplies(player, tier, raw, quantity)
                }
            }
            v.inventory.setItemStack(31, CoreLoopItems.icon(CoreLoopItems.resourceMaterial(selected), "交換内容を確認",
                "T$tier 戦利品券 $count 枚 → ${selected.displayName} ${count * 4} 個", "所持券 ${a.amount(CoreResource.COMBAT_TOKEN, tier)} 枚", "下で回数を選択 → 右下で確定"))
            button(v, 30, CoreLoopItems.icon(Material.WOODEN_AXE, "採取道具を選ぶ", "道具はなくしても無料で用意できます")) { tools(player) }
            button(v, 40, CoreLoopItems.icon(Material.ANVIL, "素材が揃ったら工房へ")) { workshop(player, tier) }
            quantityButtons(v, quantity, maximum) { supplies(player, tier, selected, it) }
            execute(v, player, "$count 回交換する", if (count <= maximum && recipe.canAfford(a)) null else "戦利品券不足、または素材の保管上限", "上記の券を消費して素材を受け取る") {
                game.mutate(player, CoreAction.Exchange(selected, tier, count), a.revision) { supplies(player, tier, selected, quantity) }
            }
            back(v, player)
        }
    }

    private fun tools(player: Player) {
        view(player, "道具箱 — 採取するものを選ぶ") { v ->
            QuestGatheringDiscipline.entries.forEachIndexed { i, discipline ->
                button(v, 20 + i, CoreLoopItems.icon(discipline.toolMaterial, discipline.toolName,
                    "${discipline.commonResourceName}に使う", "ホットバー7番に装備", "対象へ右クリック長押し / 離すと中断")) {
                    player.inventory.setItemStack(6, discipline.toolItem()); player.setHeldItemSlot(6); player.closeInventory()
                }
            }
            back(v, player)
        }
    }

    fun mastery(player: Player, discipline: QuestGatheringDiscipline? = null) {
        val mastery = game.gatheringMastery(player)
        view(player, "採取の心得") { v ->
            QuestGatheringDiscipline.entries.forEachIndexed { i, kind ->
                button(v, 19 + i, CoreLoopItems.icon(kind.toolMaterial, "${kind.displayName} Lv${mastery.level(kind)}",
                    "経験値 ${mastery.experience(kind)} / 未使用ポイント ${mastery.availableTreePoints(kind)}", "Lv3ごとにツリーポイントを獲得")) { mastery(player, kind) }
            }
            if (discipline != null) {
                listOf(QuestGatheringMasteryNode.STEADY_HANDS, QuestGatheringMasteryNode.DEEP_YIELD, QuestGatheringMasteryNode.ABUNDANCE_KEYSTONE).forEachIndexed { i, node ->
                    val unlocked = node in mastery.unlockedNodes(discipline)
                    button(v, 30 + i, CoreLoopItems.icon(if (unlocked) Material.LIME_DYE else Material.OAK_SAPLING,
                        node.displayName, node.description, "必要ポイント ${node.cost}", if (unlocked) "取得済み" else "クリックで取得")) {
                        game.unlockMastery(player, discipline, node); mastery(player, discipline)
                    }
                }
            }
            button(v, 40, CoreLoopItems.icon(Material.CHEST, "採取道具を用意")) { tools(player) }
            back(v, player)
        }
    }

    private fun confirmReturn(player: Player) {
        view(player, "遠征 — 港へ帰還しますか") { v ->
            v.inventory.setItemStack(13, CoreLoopItems.icon(Material.COMPASS, "遠征を終了", "獲得済み素材は倉庫に残ります", "未回収の戦利品もまとめて保管します", "このマップには戻れません"))
            button(v, 30, CoreLoopItems.icon(Material.LIME_DYE, "港へ帰還する")) { game.returnToHarbor(player) }
            button(v, 32, CoreLoopItems.icon(Material.RED_DYE, "探索を続ける")) { player.closeInventory() }
        }
    }

    fun guide(player: Player) {
        view(player, "冒険の手帳 — 最初の一周") { v ->
            listOf(
                Triple(Material.MAP, "1. 港の地図台", listOf("T1地図を受け取り、出発", "石板があれば採取MODも付与")),
                Triple(Material.IRON_SWORD, "2. 自由に探索", listOf("道の先にボス / 採取・雑魚は寄り道", "左クリック：通常攻撃 / F：回避", "スキルはホットバー2〜4を右クリック")),
                Triple(Material.WOODEN_AXE, "3. 採取する", listOf("道具箱で対応する道具を選ぶ", "木・岩・草・死体へ右クリック長押し", "進捗表示が完了すると素材を保存")),
                Triple(Material.ECHO_SHARD, "4. ボス討伐", listOf("予兆の範囲から移動して避ける", "討伐証・次の地図・石板を獲得", "羅針盤から港へ帰還")),
                Triple(Material.ANVIL, "5. 装備を更新", listOf("工房で精製 → 武器と防具を制作", "刻印工房でオーブを使ってMOD抽選", "マジック最大2 / レア最大6 MOD")),
            ).forEachIndexed { i, entry -> v.inventory.setItemStack(20 + i, CoreLoopItems.icon(entry.first, entry.second, *entry.third.toTypedArray())) }
            v.inventory.setItemStack(30, CoreLoopItems.icon(Material.AMETHYST_SHARD, "敵を倒す → オーブ → ランダムMOD", "紫や金の戦利品へ近づいて回収", "精鋭はオーブ2個、ボスは3個確定", "MODの種類・数値は加工時に決まる"))
            v.inventory.setItemStack(32, CoreLoopItems.icon(Material.END_PORTAL_FRAME, "寄り道 → 欠片 → 専用ボス", "裂け目：3か所を追って波を倒す", "儀式：続行か確定を選ぶ / 最大3波", "各欠片3個 → 手帳の「境界の試練」"))
            back(v, player)
        }
    }

    fun affixes(player: Player, page: Int = 0, selected: CoreGearSlot = forgeSelections[player.uuid]?.gear ?: CoreGearSlot.WEAPON) {
        val a = game.account(player) ?: return
        if (a.activeRun != null) return gearMods(player, selected)
        val s = forgeSelections[player.uuid] ?: CoreForgeLayout.Selection()
        forge(player, s.copy(tab = CoreForgeLayout.Tab.MODS, gear = selected, currency = null))
    }

    fun gearMods(player: Player, gear: CoreGearSlot) {
        val a = game.account(player) ?: return
        view(player, "刻印工房 — ${gear.displayName}のMOD") { v ->
            v.inventory.setItemStack(13, CoreLoopItems.gear(a, gear, game.packed(player)))
            (0..5).forEach { index ->
                val equipped = a.equippedAffixes.firstOrNull { it.gear == gear && it.index == index }
                val slot = 28 + index
                when {
                    index >= CoreAffixCatalog.capacity(a, gear) -> v.inventory.setItemStack(slot, CoreLoopItems.icon(Material.GRAY_DYE, "MOD枠 ${index + 1} — 昇格で解放", "Tierではなく装備のレアリティで枠が増加", "ノーマル0 / マジック2 / レア6"))
                    equipped == null -> v.inventory.setItemStack(slot, CoreLoopItems.icon(Material.LIGHT_GRAY_DYE, "MOD枠 ${index + 1} — 空き", "増強・高揚などのオーブでランダム追加"))
                    else -> {
                        val model = CoreLoopItems.affixModel(equipped.stone)
                        v.inventory.setItemStack(slot, CoreLoopItems.icon(Material.ENCHANTED_BOOK, model.name,
                            model.effect, "ロール範囲 ${model.range}", "品質 ${model.qualityPercent}% / 内部Tier ${equipped.stone.tier}", "接頭・接尾はそれぞれ最大3個 / 重複MODなし"))
                    }
                }
            }
            if (a.activeRun == null) {
                button(v, 40, CoreLoopItems.icon(Material.ENCHANTING_TABLE, "この装備を加工する", if (gear in a.legacyLayouts) "旧構成を保持中 / 改変・混沌で新構成へ" else "追加抽選・全抽選・数値抽選を使い分ける")) { affixes(player, selected = gear) }
                back(v, player) {
                    val previous = forgeSelections[player.uuid]
                    if (previous != null) forge(player, previous.copy(gear = gear)) else affixes(player, selected = gear)
                }
            } else {
                v.inventory.setItemStack(40, CoreLoopItems.icon(Material.BOOK, "遠征中は確認のみ", "港へ帰還すると装備を加工できます"))
                back(v, player)
            }
        }
    }

    fun stoneDetail(player: Player, id: UUID, preferred: CoreGearSlot? = null) {
        val a = game.account(player) ?: return
        val stone = a.affixStones.firstOrNull { it.id == id } ?: return affixes(player)
        view(player, "旧刻印石 — オーブへの交換") { v ->
            v.inventory.setItemStack(13, CoreLoopItems.stone(stone, game.packed(player)))
            v.inventory.setItemStack(29, CoreLoopItems.currency(CoreCraftingCurrency.ALTERATION, stone.tier.toLong(), game.packed(player), "交換で受け取る数"))
            v.inventory.setItemStack(33, CoreLoopItems.currency(CoreCraftingCurrency.ALCHEMY, 1, game.packed(player), "交換で受け取る数"))
            button(v, 40, CoreLoopItems.icon(Material.LIME_DYE, "この旧刻印石を消費して交換", "改変 ×${stone.tier} ＋ 錬金 ×1 を受け取る", "石に決まっていたMODは引き継ぎません", "交換は取り消せません")) {
                if (game.requireHub(player)) game.mutate(player, CoreAction.ConvertLegacyStone(id), a.revision) { affixes(player, selected = preferred ?: CoreGearSlot.WEAPON) }
            }
            back(v, player) { legacyStones(player) }
        }
    }

    fun confirmCraft(player: Player, gear: CoreGearSlot, currency: CoreCraftingCurrency) {
        val s = forgeSelections[player.uuid] ?: CoreForgeLayout.Selection()
        forge(player, s.copy(tab = CoreForgeLayout.Tab.MODS, gear = gear, currency = currency))
    }

    private fun legacyStones(player: Player, page: Int = 0) {
        val a = game.account(player) ?: return
        val last = (a.affixStones.size - 1).coerceAtLeast(0) / contentSlots.size
        val current = page.coerceIn(0, last)
        view(player, "旧刻印石 — 所持品のみ") { v ->
            a.affixStones.drop(current * contentSlots.size).take(contentSlots.size).forEachIndexed { i, stone ->
                button(v, contentSlots[i], CoreLoopItems.stone(stone, game.packed(player))) { stoneDetail(player, stone.id) }
            }
            if (current > 0) button(v, 48, CoreLoopItems.icon(Material.ARROW, "前のページ")) { legacyStones(player, current - 1) }
            if (current < last) button(v, 50, CoreLoopItems.icon(Material.ARROW, "次のページ")) { legacyStones(player, current + 1) }
            back(v, player) { affixes(player) }
        }
    }

    fun trials(player: Player, tier: Int = game.account(player)?.unlockedMapTier ?: 1) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        view(player, "境界の試練 — T$tier") { v ->
            tiers(v, tier) { trials(player, it) }
            v.inventory.setItemStack(13, CoreLoopItems.icon(Material.END_PORTAL_FRAME, "欠片3個で専用フィールドへ", "通常マップとは別のボス戦です", "死亡・帰還・切断で欠片は戻りません", "報酬：専用オーブ2個 ＋ 高揚1個 ＋ 討伐証"))
            CoreActivityKind.entries.forEachIndexed { i, kind ->
                val name = when (kind) { CoreActivityKind.RIFT -> "灰燼の王"; CoreActivityKind.RITUAL -> "氷獄の巨兵"; CoreActivityKind.TRIAL -> "嵐の司祭" }
                button(v, 29 + i * 2, CoreLoopItems.icon(Material.ECHO_SHARD, name,
                    "${kind.displayName}の欠片 ${a.amount(kind)} / 3個", "専用報酬：${kind.currency.displayName}", "クリック：挑戦内容の確認")) { confirmTrial(player, kind, tier) }
            }
            back(v, player)
        }
    }

    private fun confirmTrial(player: Player, kind: CoreActivityKind, tier: Int) {
        val a = game.account(player) ?: return
        val available = a.amount(kind) >= 3 && tier <= a.unlockedMapTier
        view(player, "境界の試練 — 出発確認") { v ->
            v.inventory.setItemStack(13, CoreLoopItems.icon(Material.END_PORTAL_FRAME, "T$tier ${kind.displayName}の試練",
                "消費：${kind.displayName}の欠片3個 / 所持 ${a.amount(kind)}", "1回限りの挑戦 / 敗北・帰還でも返却なし", "入場準備の失敗時だけ返却されます"))
            button(v, 30, CoreLoopItems.icon(if (available) Material.LIME_DYE else Material.BARRIER, if (available) "欠片を消費して挑戦" else "欠片不足、またはTier未解放")) {
                if (available) game.departTrial(player, kind, tier, a.revision)
            }
            back(v, player) { trials(player, tier) }
        }
    }
}
