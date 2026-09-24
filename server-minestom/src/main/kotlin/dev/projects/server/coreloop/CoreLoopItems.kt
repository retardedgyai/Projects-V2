package dev.projects.server.coreloop

import dev.projects.server.questmap.*
import dev.projects.server.coreloop.ui.*
import net.minestom.server.component.DataComponents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import java.util.UUID
import kotlin.math.roundToInt

internal object CoreLoopItems {
    val actionTag: Tag<String> = Tag.String("projects_core_action")
    val ownedMapTag: Tag<String> = Tag.String("projects_core_owned_map")
    val gearTag: Tag<String> = Tag.String("projects_core_gear")
    val stoneTag: Tag<String> = Tag.String("projects_core_stone")
    val currencyTag: Tag<String> = Tag.String("projects_core_currency")
    val resourceTag: Tag<String> = Tag.String("projects_core_resource")
    val resourceQuantityTag: Tag<Long> = Tag.Long("projects_core_resource_quantity")
    val fragmentTag: Tag<String> = Tag.String("projects_core_fragment")
    val silverTag: Tag<Boolean> = Tag.Boolean("projects_core_silver")
    val colors = listOf(NamedTextColor.WHITE, NamedTextColor.GREEN, NamedTextColor.AQUA, NamedTextColor.LIGHT_PURPLE)

    fun text(value: String, color: NamedTextColor = NamedTextColor.GRAY): Component =
        Component.text(value, color).decoration(TextDecoration.ITALIC, false)

    fun icon(material: Material, name: String, vararg lore: String, color: NamedTextColor = NamedTextColor.GOLD): ItemStack =
        ItemStack.builder(material).customName(text(name, color).decoration(TextDecoration.BOLD, true)).lore(lore.map { text(it) }).build().withoutExtraTooltip()

    fun resourceMaterial(resource: CoreResource): Material = when (resource) {
        CoreResource.WOOD -> Material.OAK_LOG
        CoreResource.ORE -> Material.RAW_IRON
        CoreResource.STONE -> Material.COBBLESTONE
        CoreResource.HIDE -> Material.RABBIT_HIDE
        CoreResource.FIBER -> Material.WHEAT
        CoreResource.BOARD -> Material.OAK_PLANKS
        CoreResource.INGOT -> Material.IRON_INGOT
        CoreResource.STONE_BLOCK -> Material.STONE_BRICKS
        CoreResource.LEATHER -> Material.LEATHER
        CoreResource.CLOTH -> Material.WHITE_WOOL
        CoreResource.BOSS_SIGIL -> Material.ECHO_SHARD
        CoreResource.COMBAT_TOKEN -> Material.GOLD_NUGGET
        CoreResource.POTION -> Material.HONEY_BOTTLE
        CoreResource.GATHERING_TABLET -> Material.AMETHYST_SHARD
        CoreResource.WHETSTONE -> Material.FLINT
        CoreResource.AFFIX_DUST -> Material.GLOWSTONE_DUST
    }

    fun resource(material: CoreMaterial, count: Long): ItemStack = icon(resourceMaterial(material.resource), material.displayName,
        "所持：$count 個", if (material.resource.raw) "採取、または市場で購入して入手" else if (material.resource in CoreLoopCatalog.refined.values) "採取素材を精製、または市場で購入" else "遠征や工房で入手", color = colors[material.tier - 1])
        .withAmount(count.coerceIn(1, 64).toInt())

    fun carriedResource(material: CoreMaterial, count: Long): ItemStack = icon(resourceMaterial(material.resource), material.displayName,
        if (count <= 64) "インベントリ所持 $count 個" else "素材束：$count 個分",
        "探索・製造で得た実物の素材", color = colors[material.tier - 1])
        .withTag(resourceTag, "${material.resource.name}:${material.tier}")
        .withTag(resourceQuantityTag, count)
        .withAmount(count.coerceIn(1, 64).toInt())

    fun resourceId(item: ItemStack): CoreMaterial? = item.getTag(resourceTag)?.split(':')?.takeIf { it.size == 2 }?.let { parts ->
        val resource = CoreResource.entries.firstOrNull { it.name == parts[0] } ?: return@let null
        val tier = parts[1].toIntOrNull()?.takeIf { it in 1..4 } ?: return@let null
        CoreMaterial(resource, tier)
    }

    fun canCarry(player: Player, material: CoreMaterial): Boolean =
        (0 until 36).any { resourceId(player.inventory.getItemStack(it)) == material } ||
            (16..35).any { player.inventory.getItemStack(it).isAir }

    fun unrepresentedCount(player: Player, account: CoreAccount): Int {
        val stacks = (0 until 36).map(player.inventory::getItemStack)
        return account.balances.count { (material, count) -> count > 0 && stacks.none { resourceId(it) == material } } +
            account.currencies.count { (currency, count) -> count > 0 && stacks.none { currencyId(it) == currency } } +
            account.fragments.count { (kind, count) -> count > 0 && stacks.none { fragmentId(it) == kind } } +
            account.maps.count { map -> stacks.none { mapId(it) == map.id } } +
            account.affixStones.count { stone -> stacks.none { stoneId(it) == stone.id } } +
            (if (account.silver > 0 && stacks.none(::isSilver)) 1 else 0)
    }

    fun weapon(tier: Int): ItemStack = icon(listOf(Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD)[tier - 1],
        "T$tier 開拓者の大剣", "攻撃力 ${(12 * CoreLoopCatalog.weaponDamage(tier)).roundToInt()}",
        "左クリック：前方を斬る", "右クリック：踏み込み斬り", "F：移動方向へ回避", color = colors[tier - 1])
        .withTag(actionTag, "weapon").withTag(gearTag, CoreGearSlot.WEAPON.name)

    fun menuSkin(item: ItemStack, packed: Boolean): ItemStack = if (packed)
        item.with(DataComponents.TOOLTIP_STYLE, CoreUiRarity.COMMON.style).withoutExtraTooltip() else item.without(DataComponents.TOOLTIP_STYLE)

    fun gear(account: CoreAccount, slot: CoreGearSlot, packed: Boolean, material: Material? = null): ItemStack {
        val tier = CoreAffixCatalog.gearTier(account, slot)
        val stats = CoreAffixCatalog.stats(account)
        val sheet = CoreCombatSheet.from(account)
        val enhancement = CoreEnhancementCatalog.state(account, slot)
        val identity = CoreEconomy.identity(account, slot)
        val base = if (slot == CoreGearSlot.WEAPON) CoreWeaponPresentation.skin(weapon(tier), tier, packed) else icon(material ?: Material.IRON_CHESTPLATE, "開拓者の防具")
            .withTag(actionTag, "armor").withTag(gearTag, CoreGearSlot.ARMOR.name)
        val rows = if (slot == CoreGearSlot.WEAPON) buildList {
            add(CoreTooltipStat("物理攻撃 AD", CoreCombatMath.number(sheet.ad), CoreUiIcon.ATTACK))
            add(CoreTooltipStat("魔法攻撃 AP", CoreCombatMath.number(sheet.ap), CoreUiIcon.MAGIC))
            add(CoreTooltipStat("攻撃速度", CoreWeaponPresentation.attackSpeedLabel(account), CoreUiIcon.SPEED))
            add(CoreTooltipStat("会心率 / 倍率", "${(stats.criticalChance * 1000).roundToInt() / 10.0}% / ${(stats.criticalMultiplier * 100).toInt()}%", CoreUiIcon.CRITICAL))
            if (stats.fireFlat + stats.iceFlat + stats.lightningFlat > 0) add(CoreTooltipStat("炎 / 氷 / 雷", "${stats.fireFlat.toInt()} / ${stats.iceFlat.toInt()} / ${stats.lightningFlat.toInt()}", CoreUiIcon.MAGIC))
        } else listOf(CoreTooltipStat("最大HP", "${CoreWeaponPresentation.health(account)}", CoreUiIcon.HEALTH),
            CoreTooltipStat("物理防御 AR", CoreCombatMath.number(sheet.ar), CoreUiIcon.DEFENSE),
            CoreTooltipStat("魔法防御 MR", CoreCombatMath.number(sheet.mr), CoreUiIcon.DEFENSE),
            CoreTooltipStat("追加軽減", "${stats.mitigationPercent.toInt()}%", CoreUiIcon.DEFENSE),
            CoreTooltipStat("最大マナ", CoreCombatMath.number(sheet.mana), CoreUiIcon.MANA))
        val shown = if (slot == CoreGearSlot.WEAPON && packed) {
            base.withItemModel(CoreArmamentPresentation.model(identity.base, account.journey.job, tier))
        } else if (slot == CoreGearSlot.WEAPON && identity.base.family != "greatsword") {
            val model = when(identity.base) { CoreWeaponBase.LONGBOW -> "minecraft:bow"; CoreWeaponBase.DAGGERS -> "minecraft:iron_sword";
                CoreWeaponBase.MACE -> "minecraft:mace"; CoreWeaponBase.TOME -> "minecraft:enchanted_book"; else -> "minecraft:blaze_rod" }
            base.withItemModel(model)
        } else if (slot == CoreGearSlot.ARMOR) CoreArmorPresentation.skin(base, account.journey.job, tier, packed) else base
        return CoreUiTooltip.apply(shown, CoreTooltipModel("${if (CoreEconomy.broken(account, slot)) "【破損】" else ""}T$tier ${if (slot == CoreGearSlot.WEAPON) identity.base.displayName else "開拓者の防具"}${if (enhancement.level > 0) " +${enhancement.level}" else ""}",
            rarity = when (CoreAffixCatalog.rarity(account, slot)) {
                CoreGearRarity.NORMAL -> CoreUiRarity.COMMON
                CoreGearRarity.MAGIC -> CoreUiRarity.UNCOMMON
                CoreGearRarity.RARE -> CoreUiRarity.RARE
            }, tier = tier, itemLevel = CoreJourneyRules.itemLevel(identity, tier),
            rarityLabel = CoreAffixCatalog.rarity(account, slot).displayName,
            typeLabel = (if (slot == CoreGearSlot.WEAPON) identity.base.displayName else "防具セット") + " · 強化 +${enhancement.level}/30",
            stats = rows, affixes = account.equippedAffixes.filter { it.gear == slot }.sortedBy { it.index }.map { affixModel(it.stone) },
            modCapacity = CoreAffixCatalog.capacity(account, slot),
            footer = listOf(if (slot == CoreGearSlot.WEAPON) identity.base.detail else "装備Lv鍛錬で同Tier内の基礎性能が成長", "製造品質 +${CoreEconomy.identity(account, slot).quality}%（基礎性能）", if (CoreEconomy.broken(account, slot)) "破損中：この装備の性能・MODは無効" else "未破損 / 遠征・戦闘では壊れません",
                if (CoreEconomy.broken(account, slot)) "装備庫で修理：同Tier・同系統・+0・未破損を1個" else "+15以降の強化失敗で破損する場合があります",
                "修理対象のMOD・強化値・製作者は維持",
                "能力欄は装備Lv・強化・MODを反映", "マジック：接頭1＋接尾1 / レア：接頭3＋接尾3",
                if (slot == CoreGearSlot.WEAPON) "通常攻撃：${CoreSkillCatalog.basicFormula.label()} / ${CoreSkillCatalog.basicType(account.journey.job).label}" else "防御軽減：300 / (300 + 有効防御)",
                if (slot == CoreGearSlot.WEAPON) "左：通常 / 右：第1スキル / F：回避" else "防具MODはセット全体に1回適用")), packed)
    }

    fun affixModel(stone: CoreAffixStone): CoreTooltipAffix {
        val definition = CoreAffixCatalog.definition(stone)
        val range = definition?.range(stone.tier)
        val suffix = if (definition?.stat?.percent == true) "%" else ""
        return CoreTooltipAffix((definition?.group?.displayName?.let { "$it · " } ?: "") + (definition?.displayName?.removeSuffix("の刻印石") ?: "未対応MOD"), CoreAffixCatalog.describe(stone),
            if (range == null) "不明" else "${range.first}〜${range.last}$suffix", CoreAffixCatalog.qualityPercent(stone), "R${stone.tier}")
    }

    fun stone(stone: CoreAffixStone, packed: Boolean): ItemStack {
        val definition = CoreAffixCatalog.definition(stone)
        val material = when (definition?.stat) {
            CoreAffixStat.FIRE -> Material.FIRE_CHARGE; CoreAffixStat.ICE -> Material.PRISMARINE_CRYSTALS
            CoreAffixStat.LIGHTNING -> Material.ECHO_SHARD; else -> Material.AMETHYST_SHARD
        }
        return CoreUiTooltip.apply(ItemStack.of(material).withTag(stoneTag, stone.id.toString()).withTag(actionTag, "affix"),
            CoreTooltipModel("旧・${definition?.displayName ?: "刻印石"}", CoreUiRarity.entries[stone.tier - 1], stone.tier, 1 + (stone.tier - 1) * 15,
                "旧仕様の保管品・交換専用", affixes = listOf(affixModel(stone)), footer = listOf("所持品は消さずに保持しています",
                    "改変のオーブ ×${stone.tier} ＋ 錬金のオーブ ×1 と交換", "直接装着は終了 / 港の刻印工房から交換")), packed)
            .withGlowing(CoreAffixCatalog.qualityPercent(stone) >= 80)
    }

    fun stoneId(item: ItemStack): UUID? = item.getTag(stoneTag)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    fun gearSlot(item: ItemStack): CoreGearSlot? = item.getTag(gearTag)?.let { runCatching { CoreGearSlot.valueOf(it) }.getOrNull() }
    fun currencyId(item: ItemStack): CoreCraftingCurrency? = item.getTag(currencyTag)?.let { runCatching { CoreCraftingCurrency.valueOf(it) }.getOrNull() }

    fun currencyMaterial(currency: CoreCraftingCurrency): Material = when (currency) {
        CoreCraftingCurrency.TRANSMUTATION -> Material.PRISMARINE_CRYSTALS
        CoreCraftingCurrency.AUGMENTATION -> Material.GLOWSTONE_DUST
        CoreCraftingCurrency.ALTERATION -> Material.ENDER_PEARL
        CoreCraftingCurrency.ALCHEMY -> Material.GOLD_INGOT
        CoreCraftingCurrency.CHAOS -> Material.FIRE_CHARGE
        CoreCraftingCurrency.REGAL -> Material.DIAMOND
        CoreCraftingCurrency.EXALTED -> Material.NETHER_STAR
        CoreCraftingCurrency.SCOURING -> Material.QUARTZ
        CoreCraftingCurrency.DIVINE -> Material.HEART_OF_THE_SEA
        CoreCraftingCurrency.RIFT -> Material.ECHO_SHARD
        CoreCraftingCurrency.RITUAL -> Material.ENDER_EYE
        CoreCraftingCurrency.TRIAL -> Material.NETHERITE_SCRAP
        CoreCraftingCurrency.ASTRAL -> Material.NAUTILUS_SHELL
    }

    fun currency(currency: CoreCraftingCurrency, count: Long, packed: Boolean, quantityLabel: String = "所持"): ItemStack {
        val exclusive = CoreActivityKind.entries.firstOrNull { it.currency == currency }
        return CoreUiTooltip.apply(ItemStack.of(currencyMaterial(currency)).withTag(actionTag, "currency")
            .withTag(currencyTag, currency.name).withTag(resourceQuantityTag, count).withAmount(count.coerceIn(1, 64).toInt()),
            CoreTooltipModel(currency.displayName, if (exclusive == null) CoreUiRarity.RARE else CoreUiRarity.EPIC,
                tier = 1, itemLevel = 1, typeLabel = if (currency == CoreCraftingCurrency.ASTRAL) "星環の深殿・踏破専用報酬" else if (exclusive == null) "装備加工用の通貨" else "${exclusive.displayName}の専用報酬",
                stats = listOf(CoreTooltipStat(quantityLabel, "$count 個", CoreUiIcon.MOD)),
                footer = listOf(CoreCraftingCatalog.description(currency), "消費と加工は確認画面で確定", "港で装備に重ねる / 右クリックで刻印工房")), packed)
    }

    fun fragment(kind: CoreActivityKind, count: Long): ItemStack = icon(Material.ECHO_SHARD, "${kind.displayName}の欠片",
        "所持 $count 個 / 3個で専用ボスへ挑戦", when (kind) {
            CoreActivityKind.RIFT -> "入手：マップ内の裂け目を最後まで追う"
            CoreActivityKind.RITUAL -> "入手：儀式の波を倒し、報酬を確定する"
            CoreActivityKind.TRIAL -> "入手：通常マップのボスを討伐する"
        }, "手帳 → 境界の試練から出発", color = NamedTextColor.LIGHT_PURPLE)
        .withTag(fragmentTag, kind.name).withTag(resourceQuantityTag, count).withAmount(count.coerceIn(1, 64).toInt())

    fun fragmentId(item: ItemStack): CoreActivityKind? = item.getTag(fragmentTag)?.let { id ->
        CoreActivityKind.entries.firstOrNull { it.name == id }
    }

    fun silver(count: Long): ItemStack = icon(Material.IRON_NUGGET, "銀貨",
        if (count <= 64) "所持 $count 枚" else "銀貨束：$count 枚分", "港の市場で使用")
        .withTag(silverTag, true).withTag(resourceQuantityTag, count).withAmount(count.coerceIn(1, 64).toInt())

    fun isSilver(item: ItemStack): Boolean = item.getTag(silverTag) == true

    fun map(data: CoreOwnedMap): ItemStack = icon(Material.FILLED_MAP, "T${data.tier} Lv${data.level} 未踏の地図",
        *listOf("右クリック：この地図で出発", "石板をつかんで重ねるとMODを付与", "付与MOD ${data.modifiers.size}/3")
            .plus(data.modifiers.map { modifierName(it) }).toTypedArray(), color = colors[data.tier - 1])
        .withTag(ownedMapTag, data.id.toString())

    fun modifierName(mod: CoreMapModifier): String = QuestMapGatheringModifier(
        mod.discipline?.let { id -> QuestGatheringDiscipline.entries.first { it.id == id } },
        QuestMapGatheringStat.entries.first { it.id == mod.stat }, mod.percent).displayName()

    fun customization(data: CoreOwnedMap): QuestMapCustomization = QuestMapCustomization(data.modifiers.map { mod ->
        QuestMapGatheringModifier(mod.discipline?.let { id -> QuestGatheringDiscipline.entries.first { it.id == id } },
            QuestMapGatheringStat.entries.first { it.id == mod.stat }, mod.percent)
    })

    fun nextModifier(data: CoreOwnedMap, roll: Long): CoreMapModifier? {
        val updated = QuestMapItems.applyTablet(QuestMapItemData(data.seed, customization(data)), roll) ?: return null
        val mod = updated.customization.modifiers.last()
        return CoreMapModifier(mod.discipline?.id, mod.stat.id, mod.percent)
    }

    fun mapId(item: ItemStack): UUID? = item.getTag(ownedMapTag)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    fun refresh(player: Player, account: CoreAccount, initial: Boolean = false, packed: Boolean = false,
        combatSheet: CoreCombatSheet = CoreCombatSheet.from(account)) {
        val selectedMapId = mapId(player.inventory.getItemStack(7))
        if (initial) player.inventory.clear()
        // The account save persists owned stacks. Rebuild their quantities while retaining the
        // player's chosen backpack slots and clearing stale cursor/offhand copies after spending.
        fun ownedKey(item: ItemStack): String? = currencyId(item)?.let { "currency:$it" }
            ?: stoneId(item)?.let { "stone:$it" }
            ?: resourceId(item)?.let { "resource:${it.resource}:${it.tier}" }
            ?: fragmentId(item)?.let { "fragment:$it" }
            ?: mapId(item)?.let { "map:$it" }
            ?: if (isSilver(item)) "silver" else null
        val preferredSlots = (16..35).mapNotNull { slot ->
            ownedKey(player.inventory.getItemStack(slot))?.let { it to slot }
        }.toMap()
        fun projection(item: ItemStack) = ownedKey(item) != null
        for (slot in 0 until 36) if (projection(player.inventory.getItemStack(slot))) player.inventory.setItemStack(slot, ItemStack.AIR)
        if (projection(player.itemInOffHand)) player.setItemInOffHand(ItemStack.AIR)
        if (projection(player.inventory.cursorItem)) player.inventory.cursorItem = ItemStack.AIR
        fun placeOwned(key: String, item: ItemStack) {
            val preferred = preferredSlots[key]?.takeIf { player.inventory.getItemStack(it).isAir }
            val slot = preferred ?: (16..35).firstOrNull { player.inventory.getItemStack(it).isAir && it !in preferredSlots.values }
                ?: (16..35).firstOrNull { player.inventory.getItemStack(it).isAir } ?: return
            player.inventory.setItemStack(slot, item)
        }
        // A player may move gathered materials into a hotbar slot. Never overwrite those
        // real stacks while rebuilding the fixed combat controls.
        for (slot in (0..8) + listOf(14, 15)) {
            val held = player.inventory.getItemStack(slot)
            if (held.isAir || held.getTag(actionTag) != null || gearSlot(held) != null || mapId(held) != null ||
                held.getTag(QUEST_GATHERING_TOOL_TAG) != null) continue
            val free = (16..35).firstOrNull { player.inventory.getItemStack(it).isAir }
            if (free == null) {
                player.sendMessage(text("所持品がいっぱいです。アイテムを移動してから操作してください", NamedTextColor.YELLOW))
                return
            }
            player.inventory.setItemStack(free, held)
            player.inventory.setItemStack(slot, ItemStack.AIR)
        }
        player.inventory.setItemStack(0, gear(account, CoreGearSlot.WEAPON, packed))
        for (id in 0..4) {
            val available = CoreJourneyRules.skillUnlocked(account, id)
            val definition = CoreSkillCatalog.equipped(account.journey, combatSheet.mods)[id]
            val item = CoreSkillTooltip.item(definition, combatSheet, account.journey, packed, !available)
                .withTag(actionTag, "skill:$id")
            player.inventory.setItemStack(id + 1, item)
        }
        val potions = account.amount(CoreResource.POTION)
        player.inventory.setItemStack(6, if (potions == 0L) ItemStack.AIR else icon(Material.HONEY_BOTTLE, "回復薬",
            "所持 $potions 個", "右クリック：最大HPの45% + 回復力100%", "与回復・被回復MODが適用 / 再使用10秒").withTag(actionTag, "potion")
            .withTag(resourceTag, "POTION:1").withTag(resourceQuantityTag, potions).withAmount(potions.coerceAtMost(16).toInt()))
        player.inventory.setItemStack(8, icon(Material.NETHER_STAR, "ProjectS — 冒険の手帳", "右クリック：地図・刻印工房・所持品", "港の施設からも同じ操作ができます").withTag(actionTag, "journal"))
        if (initial) {
            QuestGatheringDiscipline.entries.forEachIndexed { i, discipline -> player.inventory.setItemStack(9 + i, discipline.toolItem()) }
        }
        val tablets = account.amount(CoreResource.GATHERING_TABLET)
        player.inventory.setItemStack(14, if (tablets == 0L) ItemStack.AIR else icon(Material.AMETHYST_SHARD, "採取の石板",
            "所持 $tablets 個", "つかんで地図に重ねるとMODを付与", "地図台でも同じ操作ができます", color = NamedTextColor.LIGHT_PURPLE)
            .withTag(actionTag, "tablet").withTag(resourceTag, "GATHERING_TABLET:1")
            .withTag(resourceQuantityTag, tablets).withAmount(tablets.coerceAtMost(64).toInt()))
        val whetstones = account.amount(CoreResource.WHETSTONE)
        player.inventory.setItemStack(15, if (whetstones == 0L) ItemStack.AIR else icon(Material.FLINT, "砥石",
            "所持 $whetstones 個", "右クリック：直接攻撃の与ダメージ+20% / 3分", "AD・AP自体は変化しません")
            .withTag(actionTag, "whetstone").withTag(resourceTag, "WHETSTONE:1")
            .withTag(resourceQuantityTag, whetstones).withAmount(whetstones.coerceAtMost(64).toInt()))
        // Keep free inventory slots available for collected world items and placeable blocks.
        val owned = CoreCraftingCurrency.entries.filter { account.amount(it) > 0 }
        owned.forEach { kind ->
            placeOwned("currency:$kind", currency(kind, account.amount(kind), packed))
        }
        account.balances.entries.filter { it.value > 0 && it.key.resource !in setOf(
            CoreResource.POTION, CoreResource.GATHERING_TABLET, CoreResource.WHETSTONE) }
            .sortedWith(compareBy({ it.key.tier }, { it.key.resource.ordinal })).forEach { (material, count) ->
                placeOwned("resource:${material.resource}:${material.tier}", carriedResource(material, count))
            }
        account.fragments.entries.filter { it.value > 0 }.forEach { (kind, count) ->
            placeOwned("fragment:$kind", fragment(kind, count))
        }
        if (account.silver > 0) placeOwned("silver", silver(account.silver))
        val selectedMap = account.maps.firstOrNull { it.id == selectedMapId } ?: account.maps.firstOrNull()
        player.inventory.setItemStack(7, selectedMap?.let(::map) ?: ItemStack.AIR)
        account.maps.filter { it.id != selectedMap?.id }.forEach { ownedMap ->
            placeOwned("map:${ownedMap.id}", map(ownedMap))
        }
        account.affixStones.forEach { ownedStone ->
            placeOwned("stone:${ownedStone.id}", stone(ownedStone, packed))
        }
        val tier = account.armorTier
        val armor = listOf(
            listOf(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS),
            listOf(Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS),
            listOf(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS),
            listOf(Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS),
        )[tier - 1]
        listOf(EquipmentSlot.HELMET, EquipmentSlot.CHESTPLATE, EquipmentSlot.LEGGINGS, EquipmentSlot.BOOTS).forEachIndexed { index, slot ->
            player.setEquipment(slot, gear(account, CoreGearSlot.ARMOR, packed, armor[index]))
        }
        for (slot in 1..15) {
            val item = player.inventory.getItemStack(slot)
            if (!item.isAir) player.inventory.setItemStack(slot, menuSkin(item, packed))
        }
    }
}
