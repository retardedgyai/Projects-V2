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
                button(v, 22, CoreLoopItems.icon(Material.AMETHYST_SHARD, "刻印石と装備", "集めたMODと現在の装備を確認", "付与・再抽選は港の刻印工房で")) { affixes(player) }
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
            button(v, 22, CoreLoopItems.icon(Material.ENCHANTING_TABLE, "刻印工房 — 装備MOD", "武器・防具へ刻印石を付与", "付け替え・抽出・再抽選・分解", "所持 ${a.affixStones.size}個 / 装着 ${a.equippedAffixes.size}個")) { affixes(player) }
            button(v, 29, CoreLoopItems.icon(Material.GOLD_NUGGET, "補給所 — 素材交換", "戦利品券を欲しい素材へ", "採取せず戦闘中心でも装備を作れます")) { supplies(player) }
            button(v, 31, CoreLoopItems.icon(Material.OAK_SAPLING, "採取の心得", "マスタリーと採取道具")) { mastery(player) }
            button(v, 33, CoreLoopItems.icon(Material.BOOK, "操作ガイド", "最初の一周と戦闘・採取の操作")) { guide(player) }
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
            button(v, 31, CoreLoopItems.icon(Material.ENCHANTING_TABLE, "刻印工房へ", "拾った刻印石で装備の性能を変える")) { affixes(player) }
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

    fun storage(player: Player, tier: Int = 1) {
        val a = game.account(player) ?: return
        view(player, "素材倉庫 — T$tier") { v ->
            tiers(v, tier) { storage(player, it) }
            CoreResource.entries.forEachIndexed { index, resource ->
                val material = CoreMaterial(resource, if (resource in listOf(CoreResource.POTION, CoreResource.GATHERING_TABLET, CoreResource.WHETSTONE, CoreResource.AFFIX_DUST)) 1 else tier)
                v.inventory.setItemStack(contentSlots[index], CoreLoopItems.resource(material, a.amount(material)))
            }
            v.inventory.setItemStack(13, CoreLoopItems.icon(Material.BARREL, "あなたの素材倉庫", "取り出さず、そのまま工房で使えます", "満杯のインベントリでも報酬を失いません"))
            if (a.activeRun == null) button(v, 49, CoreLoopItems.icon(Material.ANVIL, "工房へ")) { workshop(player, tier) }
            button(v, 50, CoreLoopItems.icon(Material.AMETHYST_SHARD, "刻印石の保管庫", "所持 ${a.affixStones.size}個")) { affixes(player) }
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
                Triple(Material.ANVIL, "5. 装備を更新", listOf("工房で精製 → 武器と防具を制作", "刻印工房でドロップした石を装着", "余った石は分解・抽出・再抽選へ")),
            ).forEachIndexed { i, entry -> v.inventory.setItemStack(20 + i, CoreLoopItems.icon(entry.first, entry.second, *entry.third.toTypedArray())) }
            v.inventory.setItemStack(31, CoreLoopItems.icon(Material.AMETHYST_SHARD, "敵を倒す → 刻印石 → 装備MOD", "紫や金の戦利品へ近づいて回収", "精鋭は刻印石2個、ボスは3個確定", "隠し物資は近くの敵を倒して右クリック"))
            back(v, player)
        }
    }

    fun affixes(player: Player, page: Int = 0) {
        val a = game.account(player) ?: return
        val packed = game.packed(player)
        view(player, "刻印工房 — 装備と刻印石") { v ->
            button(v, 11, CoreLoopItems.gear(a, CoreGearSlot.WEAPON, packed)) { gearMods(player, CoreGearSlot.WEAPON) }
            button(v, 15, CoreLoopItems.gear(a, CoreGearSlot.ARMOR, packed)) { gearMods(player, CoreGearSlot.ARMOR) }
            v.inventory.setItemStack(13, CoreLoopItems.icon(Material.ENCHANTING_TABLE, "刻印石 ${a.affixStones.size} / 256",
                "武器・防具のTierごとにMOD枠が増加", "石と同Tier以上の装備へ刻印できます", "刻印粉 ${a.amount(CoreResource.AFFIX_DUST)}個", "石をクリック → 装着先・再抽選・分解"))
            val last = (a.affixStones.size - 1).coerceAtLeast(0) / contentSlots.size
            val current = page.coerceIn(0, last)
            a.affixStones.drop(current * contentSlots.size).take(contentSlots.size).forEachIndexed { i, stone ->
                button(v, contentSlots[i], CoreLoopItems.stone(stone, packed)) { stoneDetail(player, stone.id) }
            }
            if (a.affixStones.isEmpty()) v.inventory.setItemStack(31, CoreLoopItems.icon(Material.AMETHYST_SHARD, "刻印石はまだありません",
                "敵の戦利品・隠し物資から獲得", "精鋭2個 / ボス3個 確定", "装着済みの石は上の装備から確認"))
            if (current > 0) button(v, 48, CoreLoopItems.icon(Material.ARROW, "前のページ")) { affixes(player, current - 1) }
            if (current < last) button(v, 50, CoreLoopItems.icon(Material.ARROW, "次のページ")) { affixes(player, current + 1) }
            back(v, player)
        }
    }

    fun gearMods(player: Player, gear: CoreGearSlot) {
        val a = game.account(player) ?: return
        view(player, "刻印工房 — ${gear.displayName}のMOD") { v ->
            v.inventory.setItemStack(13, CoreLoopItems.gear(a, gear, game.packed(player)))
            (0..3).forEach { index ->
                val equipped = a.equippedAffixes.firstOrNull { it.gear == gear && it.index == index }
                val slot = 29 + index
                when {
                    index >= CoreAffixCatalog.capacity(a, gear) -> v.inventory.setItemStack(slot, CoreLoopItems.icon(Material.BARRIER, "MOD枠 ${index + 1} — 未解放", "工房で装備をT${index + 1}へ更新すると解放"))
                    equipped == null -> button(v, slot, CoreLoopItems.icon(Material.LIGHT_GRAY_DYE, "MOD枠 ${index + 1} — 空き", "保管庫から石を選んで装着")) { affixes(player) }
                    else -> button(v, slot, CoreLoopItems.stone(equipped.stone, game.packed(player))) {
                        confirmRecipe(player, CoreAffixCatalog.extractionRecipe(equipped.stone), CoreAction.ExtractAffix(gear, index, equipped.stone.id)) { gearMods(player, gear) }
                    }
                }
            }
            v.inventory.setItemStack(40, CoreLoopItems.icon(Material.BOOK, "装着済みの石をクリック：抽出", "刻印粉を消費し、石を保管庫に戻します", "上書き時、古い石は粉に変わります", "残したいMODは先に抽出してください"))
            back(v, player) { affixes(player) }
        }
    }

    fun stoneDetail(player: Player, id: UUID, preferred: CoreGearSlot? = null) {
        val a = game.account(player) ?: return
        val stone = a.affixStones.firstOrNull { it.id == id } ?: return affixes(player)
        val definition = CoreAffixCatalog.definition(stone)
        view(player, "刻印工房 — 装着先を選ぶ") { v ->
            v.inventory.setItemStack(13, CoreLoopItems.stone(stone, game.packed(player)))
            CoreGearSlot.entries.forEach { gear ->
                (0..3).forEach { index ->
                    val old = a.equippedAffixes.firstOrNull { it.gear == gear && it.index == index }?.stone
                    val allowed = definition != null && gear in definition.allowedGear && index < CoreAffixCatalog.capacity(a, gear) && stone.tier <= CoreAffixCatalog.gearTier(a, gear)
                    val slot = (if (gear == CoreGearSlot.WEAPON) 28 else 37) + index
                    button(v, slot, CoreLoopItems.icon(if (!allowed) Material.BARRIER else if (gear == CoreGearSlot.WEAPON) Material.IRON_SWORD else Material.IRON_CHESTPLATE,
                        "${gear.displayName} MOD枠 ${index + 1}", if (old == null) "空き枠に装着" else "現在：${CoreAffixCatalog.describe(old)}",
                        if (allowed) "クリック：付与内容を確認" else "対応装備・Tier・枠数の条件を満たしていません",
                        color = if (allowed) NamedTextColor.GREEN else NamedTextColor.GRAY).withGlowing(allowed && preferred == gear)) {
                        if (allowed) confirmAffix(player, stone, gear, index, old)
                    }
                }
            }
            button(v, 24, CoreLoopItems.icon(Material.GRINDSTONE, "数値を再抽選", "MODの種類とTierは変わりません", "数値は下がる場合もあります", "費用：刻印粉 ${stone.tier * 3} + T${stone.tier}石材 1")) {
                if (definition != null) confirmRecipe(player, CoreAffixCatalog.rerollRecipe(stone), CoreAction.RerollAffix(id)) { stoneDetail(player, id) }
            }
            button(v, 42, CoreLoopItems.icon(Material.GLOWSTONE_DUST, "この石を分解", "刻印石を消費 → 刻印粉 ${CoreAffixCatalog.salvageDust(stone)}個", "分解は元に戻せません")) {
                confirmRecipe(player, CoreRecipe("刻印石を分解（石は失われます）", emptyMap(), mapOf(CoreMaterial(CoreResource.AFFIX_DUST) to CoreAffixCatalog.salvageDust(stone))), CoreAction.SalvageAffix(id)) { affixes(player) }
            }
            back(v, player) { affixes(player) }
        }
    }

    private fun confirmAffix(player: Player, stone: CoreAffixStone, gear: CoreGearSlot, index: Int, old: CoreAffixStone?) {
        if (!game.requireHub(player)) return
        val a = game.account(player) ?: return
        view(player, "刻印工房 — MOD付与の確認") { v ->
            v.inventory.setItemStack(20, CoreLoopItems.gear(a, gear, game.packed(player)))
            v.inventory.setItemStack(24, CoreLoopItems.stone(stone, game.packed(player)))
            v.inventory.setItemStack(13, CoreLoopItems.icon(Material.ENCHANTING_TABLE, "${gear.displayName}のMOD枠 ${index + 1} へ付与",
                CoreAffixCatalog.describe(stone), if (old == null) "空き枠に装着します" else "上書き消失：${CoreAffixCatalog.describe(old)}",
                if (old == null) "刻印石は装備へ移動します" else "古い石 → 刻印粉 ${CoreAffixCatalog.salvageDust(old)}個（元に戻せません）",
                if (old == null) "費用なし" else "古い石を残すならキャンセルして先に抽出"))
            button(v, 40, CoreLoopItems.icon(Material.LIME_DYE, "付与を確定")) {
                if (game.requireHub(player)) game.mutate(player, CoreAction.ApplyAffix(gear, index, stone.id, old?.id), a.revision) { gearMods(player, gear) }
            }
            back(v, player) { stoneDetail(player, stone.id, gear) }
        }
    }
}
