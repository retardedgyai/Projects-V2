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
import java.util.concurrent.ConcurrentHashMap

/** One owned inventory per viewer. Display items never become transferable rewards. */
internal class CoreLoopMenus(private val game: CoreLoopGame) {
    private data class View(val inventory: Inventory, val actions: MutableMap<Int, (Boolean) -> Unit>)
    private val views = ConcurrentHashMap<UUID, View>()
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

    fun forget(playerId: UUID) { views.remove(playerId) }
    fun refreshTheme(player: Player) {
        val view = views[player.uuid] ?: return
        if (player.openInventory === view.inventory) journal(player)
    }

    private fun view(player: Player, title: String, build: (View) -> Unit) {
        val v = View(Inventory(InventoryType.CHEST_6_ROW, CoreUiComponents.inventoryTitle(title, game.packed(player))), mutableMapOf())
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
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        view(player, "工房 — 精製と制作") { v ->
            tiers(v, tier) { workshop(player, it) }
            v.inventory.setItemStack(13, CoreLoopItems.icon(Material.CRAFTING_TABLE, "素材 → 加工品 → 装備", "制作は確認画面から確定", "左クリック：1回 / 右クリック：5回（精製・消耗品）"))
            CoreLoopCatalog.refined.keys.forEachIndexed { index, raw ->
                val recipe = CoreLoopCatalog.refine(raw, tier)
                button(v, 19 + index, recipeIcon(recipe, a, CoreLoopItems.resourceMaterial(CoreLoopCatalog.refined.getValue(raw)))) { many ->
                    val batches = if (many) 5 else 1
                    confirmRecipe(player, CoreLoopCatalog.refine(raw, tier, batches), CoreAction.Refine(raw, tier, batches)) { workshop(player, tier) }
                }
            }
            if (a.weaponTier < 4) button(v, 30, recipeIcon(CoreLoopCatalog.weaponUpgrade(a.weaponTier), a, Material.IRON_SWORD)) {
                confirmRecipe(player, CoreLoopCatalog.weaponUpgrade(a.weaponTier), CoreAction.UpgradeWeapon) { workshop(player, tier) }
            } else v.inventory.setItemStack(30, CoreLoopItems.icon(Material.NETHERITE_SWORD, "T4 武器完成", "最高Tierの武器を装備しています"))
            if (a.armorTier < 4) button(v, 32, recipeIcon(CoreLoopCatalog.armorUpgrade(a.armorTier), a, Material.IRON_CHESTPLATE)) {
                confirmRecipe(player, CoreLoopCatalog.armorUpgrade(a.armorTier), CoreAction.UpgradeArmor) { workshop(player, tier) }
            } else v.inventory.setItemStack(32, CoreLoopItems.icon(Material.DIAMOND_CHESTPLATE, "T4 防具完成"))
            listOf(CoreResource.POTION, CoreResource.GATHERING_TABLET, CoreResource.WHETSTONE).forEachIndexed { i, resource ->
                button(v, 38 + i, recipeIcon(CoreLoopCatalog.craft(resource, tier = tier), a, CoreLoopItems.resourceMaterial(resource))) { many ->
                    val batches = if (many) 5 else 1
                    confirmRecipe(player, CoreLoopCatalog.craft(resource, batches, tier), CoreAction.Craft(resource, batches, tier)) { workshop(player, tier) }
                }
            }
            button(v, 31, CoreLoopItems.icon(Material.ENCHANTING_TABLE, "刻印工房へ", "拾ったオーブで装備のMODを抽選する")) { affixes(player) }
            back(v, player)
        }
    }

    private fun recipeIcon(recipe: CoreRecipe, a: CoreAccount, material: Material): ItemStack = CoreLoopItems.icon(material, recipe.displayName,
        *recipe.costs.map { (key, count) -> "${key.displayName} $count 個 / 所持 ${a.amount(key)}" }
            .plus(recipe.outputs.map { (key, count) -> "完成：${key.displayName} ×$count" })
            .plus(if (recipe.canAfford(a)) "クリック：制作内容を確認" else "素材不足 — 補給所で戦利品券と交換も可能").toTypedArray(),
        color = if (recipe.canAfford(a)) NamedTextColor.GREEN else NamedTextColor.GRAY)

    private fun confirmRecipe(player: Player, recipe: CoreRecipe, action: CoreAction, backAction: () -> Unit) {
        if (!game.requireHub(player)) return
        val a = game.account(player) ?: return
        view(player, "工房 — 制作の確認") { v ->
            v.inventory.setItemStack(4, CoreLoopItems.icon(Material.CRAFTING_TABLE, recipe.displayName))
            recipe.costs.entries.forEachIndexed { index, (key, cost) ->
                v.inventory.setItemStack(19 + index, CoreLoopItems.icon(CoreLoopItems.resourceMaterial(key.resource), key.displayName,
                    "必要 $cost 個 / 所持 ${a.amount(key)}", color = if (a.amount(key) >= cost) NamedTextColor.GREEN else NamedTextColor.RED))
            }
            v.inventory.setItemStack(31, recipeIcon(recipe, a, Material.ANVIL))
            button(v, 40, CoreLoopItems.icon(if (recipe.canAfford(a)) Material.LIME_DYE else Material.BARRIER, "制作を確定", "素材を消費し、完成品を保存します")) {
                if (game.requireHub(player)) game.mutate(player, action, a.revision) { backAction() }
            }
            back(v, player, backAction)
        }
    }

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

    fun supplies(player: Player, tier: Int = game.account(player)?.weaponTier ?: 1) {
        val a = game.account(player) ?: return
        if (!game.requireHub(player)) return
        view(player, "補給所 — 戦利品を素材へ") { v ->
            tiers(v, tier) { supplies(player, it) }
            v.inventory.setItemStack(13, CoreLoopItems.icon(Material.GOLD_NUGGET, "T$tier 戦利品券：${a.amount(CoreResource.COMBAT_TOKEN, tier)}枚",
                "雑魚とボスの討伐で獲得", "1枚 → 同じTierの素材4個"))
            CoreResource.entries.filter { it.raw }.forEachIndexed { index, raw ->
                button(v, 20 + index, recipeIcon(CoreLoopCatalog.exchange(raw, tier), a, CoreLoopItems.resourceMaterial(raw))) { many ->
                    val batches = if (many) 5 else 1
                    game.mutate(player, CoreAction.Exchange(raw, tier, batches), a.revision) { supplies(player, tier) }
                }
            }
            button(v, 31, CoreLoopItems.icon(Material.WOODEN_AXE, "採取道具を選ぶ", "道具はなくしても無料で用意できます")) { tools(player) }
            button(v, 40, CoreLoopItems.icon(Material.ANVIL, "素材が揃ったら工房へ")) { workshop(player, tier) }
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

    fun affixes(player: Player, page: Int = 0, selected: CoreGearSlot = CoreGearSlot.WEAPON) {
        val a = game.account(player) ?: return
        val packed = game.packed(player)
        val owned = CoreCraftingCurrency.entries.filter { a.amount(it) > 0 }
        view(player, "刻印工房 — ${selected.displayName}を加工") { v ->
            CoreGearSlot.entries.forEach { gear ->
                button(v, if (gear == CoreGearSlot.WEAPON) 11 else 15, CoreLoopItems.gear(a, gear, packed).withGlowing(gear == selected)) {
                    affixes(player, selected = gear)
                }
            }
            button(v, 13, CoreLoopItems.icon(Material.ENCHANTING_TABLE, "加工対象：${selected.displayName}",
                "上の武器・防具をクリックで切り替え", "下の所持オーブから加工方法を選ぶ", "具体的なMODは確定ボタンを押してから抽選", "ここをクリック：現在のMOD一覧")) { gearMods(player, selected) }
            owned.forEachIndexed { i, currency ->
                button(v, contentSlots[i], CoreLoopItems.currency(currency, a.amount(currency), packed)) { confirmCraft(player, selected, currency) }
            }
            if (owned.isEmpty()) v.inventory.setItemStack(31, CoreLoopItems.icon(Material.BOOK, "オーブを集めよう",
                "敵の戦利品・隠し物資から獲得", "裂け目と儀式には専用のオーブもあります", "旧刻印石を持っている場合は下から交換"))
            if (a.affixStones.isNotEmpty()) button(v, 48, CoreLoopItems.icon(Material.AMETHYST_SHARD, "旧刻印石を交換（${a.affixStones.size}個）", "旧所持品は勝手に消費しません")) { legacyStones(player, page) }
            button(v, 50, CoreLoopItems.icon(Material.BOOK, "加工の進め方", "ノーマル → 変成 → マジック（最大2）", "改変で再抽選 / 増強で不足MOD追加", "富豪でレア化 / 高揚でMOD追加（最大6）", "錬金：ノーマル→レア / 混沌：レア再抽選", "洗浄：全消去 / 神聖・儀式：数値だけ再抽選")) { }
            back(v, player)
        }
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
            button(v, 40, CoreLoopItems.icon(Material.ENCHANTING_TABLE, "この装備を加工する", "抽出して固定MODを移す方式は終了", if (gear in a.legacyLayouts) "旧構成を保持中 / 改変・混沌で新構成へ" else "追加抽選・全抽選・数値抽選を使い分ける")) { affixes(player, selected = gear) }
            back(v, player) { affixes(player, selected = gear) }
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
        if (!game.requireHub(player)) return
        val a = game.account(player) ?: return
        val reason = CoreCraftingCatalog.canUse(a, gear, currency)
        view(player, "刻印工房 — ランダム加工の確認") { v ->
            v.inventory.setItemStack(20, CoreLoopItems.gear(a, gear, game.packed(player)))
            v.inventory.setItemStack(24, CoreLoopItems.currency(currency, a.amount(currency), game.packed(player)))
            v.inventory.setItemStack(13, CoreLoopItems.icon(Material.ENCHANTING_TABLE, "結果は使用後に決まります",
                CoreCraftingCatalog.description(currency), "消費：${currency.displayName} ×1", "抽選は取り消せません / 数値は下がる場合もあります",
                reason ?: "${gear.displayName}に使用できます"))
            button(v, 40, CoreLoopItems.icon(if (reason == null) Material.LIME_DYE else Material.BARRIER, if (reason == null) "1個使って加工を確定" else "使用できません", reason ?: "この時点でサーバーがMODを抽選します")) {
                if (reason == null && game.requireHub(player)) game.mutate(player, CoreAction.CraftEquipment(gear, currency), a.revision) { affixes(player, selected = gear) }
            }
            back(v, player) { affixes(player, selected = gear) }
        }
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
