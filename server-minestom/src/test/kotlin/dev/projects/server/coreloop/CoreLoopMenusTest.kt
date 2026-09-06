package dev.projects.server.coreloop

import com.google.gson.GsonBuilder
import dev.projects.server.coreloop.ui.CoreForgeLayout
import dev.projects.server.coreloop.ui.CoreMenuCanvas
import dev.projects.server.coreloop.ui.CoreMenuCanvas.TextStyle
import dev.projects.server.questmap.*
import dev.projects.server.coreloop.adventure.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.Player
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.click.Click
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*

/** Exercises the real builders and click dispatch without a world, server socket or player save. */
class CoreLoopMenusTest {
    private data class Pending(val action: CoreAction, val revision: Long, val rejected: (() -> Unit)?, val after: () -> Unit)
    private class Host(var current: CoreAccount, var usePack: Boolean = true) : CoreMenuHost {
        var marketEntries = emptyList<CoreMarketEntry>()
        override fun market() = marketEntries
        val requests = mutableListOf<Pending>()
        val departures = mutableListOf<Pair<UUID, Long>>()
        var partySnapshots = emptyList<DungeonParty>()
        var dungeonState: DungeonRunView? = null
        val lobbyActions = mutableListOf<DungeonLobbyAction>()
        val boons = mutableListOf<DungeonBoon>()
        val routes = mutableListOf<Int>()
        override fun dungeonParties() = partySnapshots
        override fun dungeonView(player: Player) = dungeonState
        override fun dungeonLobby(player: Player, action: DungeonLobbyAction) { lobbyActions += action }
        override fun dungeonBoon(player: Player, boon: DungeonBoon) { boons += boon }
        override fun dungeonRoute(player: Player, roomId: Int) { routes += roomId }
        var trialDepartures = 0
        var returns = 0
        var masteryUnlocks = 0
        override fun account(player: Player) = current
        override fun packed(player: Player) = usePack
        override fun isDeparting(player: Player) = false
        override fun requireHub(player: Player) = current.activeRun == null
        override fun sessionSummary(player: Player) = "採取 123 / 発見 45 / 討伐 999"
        override fun warmMap(player: Player, map: CoreOwnedMap) = true
        override fun mutate(player: Player, action: CoreAction, revision: Long, onRejected: (() -> Unit)?, after: () -> Unit) {
            requests += Pending(action, revision, onRejected, after)
        }
        override fun applyTablet(player: Player, mapId: UUID, revision: Long, onRejected: (() -> Unit)?, after: () -> Unit) {
            mutate(player, CoreAction.ApplyTablet(mapId, CoreMapModifier(null, "amount", 10)), revision, onRejected, after)
        }
        override fun depart(player: Player, mapId: UUID, revision: Long) { departures += mapId to revision }
        override fun returnToHarbor(player: Player) { returns++ }
        override fun gatheringMastery(player: Player) = QuestGatheringMastery()
        override fun unlockMastery(player: Player, discipline: QuestGatheringDiscipline, node: QuestGatheringMasteryNode) { masteryUnlocks++ }
        override fun departTrial(player: Player, kind: CoreActivityKind, tier: Int, revision: Long) { trialDepartures++ }
    }

    private class Fixture(val player: Player, val host: Host, val menus: CoreLoopMenus,
        val snapshots: MutableList<CoreMenuCanvas.Snapshot>) {
        fun click(slot: Int, right: Boolean = false) {
            val inventory = assertNotNull(player.openInventory)
            val event = InventoryPreClickEvent(inventory, player, if (right) Click.Right(slot) else Click.Left(slot))
            assertTrue(menus.click(event), "Click was not owned: $slot")
            assertTrue(event.isCancelled)
        }
        fun title(): String = decode((assertNotNull(player.openInventory) as Inventory).title)
        fun snapshot(): CoreMenuCanvas.Snapshot = snapshots.last()
    }

    @BeforeTest fun initializeMinestom() { MinecraftServer.init(Auth.Offline()) }

    @Test fun `dungeon menus gate departure routes and return uses party-safe text in both pack modes`() {
        for (packed in listOf(false, true)) {
            val f = fixture(account(), packed)
            f.menus.dungeons(f.player); assertTrue(auditSnapshot(f.snapshot()).isEmpty(), auditSnapshot(f.snapshot()).toString())
            f.click(9); assertEquals(DungeonLobbyAction.Solo(1, 0), f.host.lobbyActions.single())
            f.host.lobbyActions.clear()
            f.host.current = f.host.current.copy(weaponBroken = true)
            f.menus.dungeons(f.player); f.click(9); f.click(12); assertTrue(f.host.lobbyActions.isEmpty())
            f.host.current = f.host.current.copy(weaponBroken = false)
            val party = DungeonParty(UUID.randomUUID(), f.player.uuid, 1, 0, listOf(f.player.uuid, UUID.randomUUID()))
            f.host.partySnapshots = listOf(party)
            f.menus.dungeons(f.player); f.click(41); assertTrue(f.host.lobbyActions.isEmpty())
            f.click(39); assertEquals(DungeonLobbyAction.Ready, f.host.lobbyActions.single())
            f.host.partySnapshots = listOf(party.copy(ready = party.members.toSet()))
            f.menus.dungeons(f.player); f.click(41); assertEquals(DungeonLobbyAction.Start, f.host.lobbyActions.last())
            val plan = DungeonPlan.generate(1, 1, 0)
            f.host.dungeonState = DungeonRunView(UUID.randomUUID(), f.player.uuid, plan.choices(1).first(),
                plan.stages, 0, DungeonRunPhase.CHOOSING, "加護を選ぶ", 2, plan.choices(2),
                listOf(DungeonBoon.FORCE, DungeonBoon.GUARD, DungeonBoon.FLAME), false, emptyMap(), 2)
            f.menus.dungeonRun(f.player); assertTrue(auditSnapshot(f.snapshot()).isEmpty(), auditSnapshot(f.snapshot()).toString())
            f.click(36); assertTrue(f.host.routes.isEmpty())
            f.click(9); assertEquals(listOf(DungeonBoon.FORCE), f.host.boons)
            f.host.dungeonState = f.host.dungeonState!!.copy(chosen = true, waitingFor = 0)
            f.menus.dungeonRun(f.player); f.click(36); assertEquals(listOf(plan.choices(2).first().id), f.host.routes)
            f.click(45)
            assertTrue(f.snapshot().leftPanel!!.lines.any { it.text == "仲間の探索は継続" })
            assertEquals(0, f.host.returns)
            f.click(51); assertEquals(1, f.host.returns)
        }
    }

    @Test fun `risky enhancement quotes then confirms while broken equipment links directly to repair`() {
        for (packed in listOf(false, true)) {
            val f = fixture(account(tier = 3).copy(weaponEnhancement = CoreEnhancementState(15)), packed)
            f.menus.workshop(f.player, 3)
            assertTrue(f.snapshot().leftPanel!!.lines.any { it.text == "失敗時破損 5%" })
            f.click(CoreForgeLayout.EXECUTE)
            assertTrue(f.host.requests.isEmpty())
            assertTrue(f.title().contains("破損リスク"))
            val lore = f.player.openInventory!!.getItemStack(4).get(DataComponents.LORE)!!.joinToString("\n") { decode(it) }
            assertTrue("今回1回あたりの破損確率 3.5%" in lore)
            f.click(45)
            assertTrue(f.host.requests.isEmpty())
            f.click(CoreForgeLayout.EXECUTE)
            f.click(22)
            assertEquals(CoreAction.EnhanceEquipment(CoreGearSlot.WEAPON), f.host.requests.single().action)
            f.host.current = f.host.current.copy(weaponBroken = true)
            f.host.requests.single().after()
            assertTrue(f.title().contains("修理"))
            f.host.requests.clear()
            f.menus.workshop(f.player, 3)
            f.click(CoreForgeLayout.EXECUTE)
            assertTrue(f.host.requests.isEmpty())
            f.click(42)
            assertTrue(f.title().contains("修理"))
            f.click(20)
            f.click(CoreForgeLayout.EXECUTE)
            assertEquals(CoreAction.Manufacture(CoreGearSlot.WEAPON, 3), f.host.requests.single().action)
        }
    }

    @Test fun `repair market filters incompatible equipment and returns a purchased donor to repair selection`() {
        for (packed in listOf(false, true)) {
            val f = fixture(account(tier = 3).copy(weaponBroken = true, silver = 1000), packed)
            fun entry(slot: CoreGearSlot = CoreGearSlot.WEAPON, tier: Int = 3, level: Int = 0, broken: Boolean = false): CoreMarketEntry {
                val seller = UUID.randomUUID()
                val item = CoreStoredGear(CoreGearIdentity(UUID.randomUUID(), seller), slot, tier,
                    CoreGearRarity.NORMAL, CoreEnhancementState(level), broken = broken)
                return CoreMarketEntry(seller, CoreMarketOffer(UUID.randomUUID(), 100, gearId = item.identity.id), item)
            }
            val good = entry()
            f.host.marketEntries = listOf(entry(CoreGearSlot.ARMOR), entry(tier = 2), entry(level = 1), entry(broken = true), good)
            f.menus.equipmentStock(f.player)
            f.click(3)
            f.click(24)
            assertTrue(f.title().contains("修理材料の市場"))
            assertTrue(f.player.openInventory!!.getItemStack(10).isAir)
            f.click(9)
            f.click(22)
            assertEquals(CoreAction.BuyOffer(good.seller, good.offer.id, 100), f.host.requests.single().action)
            f.host.current = f.host.current.copy(storedGear = listOf(good.gear!!))
            f.host.requests.single().after()
            assertTrue(f.title().contains("修理"))
            f.host.requests.clear()
            f.click(9)
            f.click(22)
            assertEquals(CoreAction.Repair(CoreGearSlot.WEAPON, good.gear.identity.id), f.host.requests.single().action)
        }
    }

    @Test fun `repair selection consumes nothing before explicit confirmation of a modified donor`() {
        for (packed in listOf(false, true)) {
            val f = fixture(account(tier = 3), packed)
            val donorAccount = CoreCraftingCatalog.craft(CoreAccount(UUID.randomUUID(), weaponTier = 3,
                currencies = mapOf(CoreCraftingCurrency.ALCHEMY to 1L)), CoreGearSlot.WEAPON, CoreCraftingCurrency.ALCHEMY, UUID.randomUUID())
            val donor = CoreStoredGear(CoreGearIdentity(UUID.randomUUID(), donorAccount.playerId), CoreGearSlot.WEAPON, 3,
                donorAccount.weaponRarity, CoreEnhancementState(), donorAccount.equippedAffixes)
            f.host.current = f.host.current.copy(weaponBroken = true, storedGear = listOf(donor))
            f.menus.equipmentStock(f.player)
            f.click(3)
            f.click(9)
            assertTrue(f.host.requests.isEmpty())
            val lore = f.player.openInventory!!.getItemStack(22).get(DataComponents.LORE)!!.joinToString("\n") { decode(it) }
            assertTrue("材料装備のMODも消失" in lore)
            f.click(22)
            assertEquals(CoreAction.Repair(CoreGearSlot.WEAPON, donor.identity.id), f.host.requests.single().action)
        }
    }

    @Test fun `manufacturing quote routes to owned equipment and material listing needs explicit confirmation`() {
        for (packed in listOf(false, true)) {
            val f = fixture(account(tier = 4), packed)
            f.menus.workshop(f.player, 4)
            f.click(CoreForgeLayout.Tab.CRAFT.slot)
            assertTrue(f.host.requests.isEmpty())
            f.click(CoreForgeLayout.EXECUTE)
            assertEquals(CoreAction.Manufacture(CoreGearSlot.WEAPON, 4), f.host.requests.single().action)
            val item = CoreStoredGear(CoreGearIdentity(UUID.randomUUID(), f.host.current.playerId), CoreGearSlot.WEAPON, 4, CoreGearRarity.NORMAL, CoreEnhancementState())
            f.host.current = f.host.current.copy(storedGear = listOf(item))
            f.host.requests.single().after()
            assertTrue(f.title().contains("装備庫"))
            f.click(9)
            assertTrue(f.title().contains(item.displayName))
            assertEquals(1, f.host.requests.size)
            f.click(20)
            assertEquals(CoreAction.Equip(item.identity.id), f.host.requests.last().action)

            f.host.requests.clear()
            f.menus.supplies(f.player)
            f.click(46); f.click(9) // WOOD T1 -> quote
            f.click(34); f.click(14) // 11 items, total 11 silver
            assertTrue(f.host.requests.isEmpty())
            f.click(40)
            assertEquals(CoreAction.ListMaterial(CoreMaterial(CoreResource.WOOD), 11, 11), f.host.requests.single().action)
        }
    }

    @Test fun `market insufficient silver is visibly disabled and purchase is separate from selection`() {
        for (silver in listOf(0L, 100L)) {
            val f = fixture(account().copy(silver = silver))
            val offer = CoreMarketOffer(UUID.randomUUID(), 30, CoreMaterial(CoreResource.ORE), 4)
            val seller = UUID.randomUUID()
            f.host.marketEntries = listOf(CoreMarketEntry(seller, offer, null))
            f.menus.supplies(f.player)
            f.click(9)
            assertTrue(f.host.requests.isEmpty())
            f.click(22)
            if (silver == 0L) assertTrue(f.host.requests.isEmpty())
            else assertEquals(CoreAction.BuyOffer(seller, offer.id, 30), f.host.requests.single().action)
        }
    }

    private fun fixture(account: CoreAccount, packed: Boolean = true): Fixture {
        val connection = object : PlayerConnection() {
            override fun sendPacket(packet: SendablePacket) = Unit
            override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
        }
        val player = Player(connection, GameProfile(account.playerId, "MenuIntegration"))
        connection.player = player
        val host = Host(account, packed)
        val snapshots = mutableListOf<CoreMenuCanvas.Snapshot>()
        return Fixture(player, host, CoreLoopMenus(host) { snapshots += it.snapshot() }, snapshots)
    }

    private fun account(tier: Int = 1, wealthy: Boolean = true, fullMods: Boolean = false, maximum: Boolean = false): CoreAccount {
        val globals = setOf(CoreResource.POTION, CoreResource.GATHERING_TABLET, CoreResource.WHETSTONE, CoreResource.AFFIX_DUST)
        val balances = if (wealthy) buildMap {
            CoreResource.entries.forEach { resource ->
                (if (resource in globals) listOf(1) else (1..4).toList()).forEach { t -> put(CoreMaterial(resource, t), if (maximum) CoreLoopCatalog.MAX_BALANCE else 500_000L) }
            }
        } else emptyMap()
        fun id(value: String): UUID = UUID.nameUUIDFromBytes("menu-test/$tier/$wealthy/$fullMods/$maximum/$value".toByteArray())
        val maps = List(9) { index -> CoreOwnedMap(id("map/$index"), index.toLong(), tier, listOf(
            CoreMapModifier("woodcutting", "amount", 200), CoreMapModifier("mining", "dense_regions", 200),
            CoreMapModifier("herbalism", "quality", 200))) }
        var result = CoreAccount(id("player"), revision = 41, balances = balances, weaponTier = tier, armorTier = tier,
            unlockedMapTier = tier, maps = maps, currencies = if (wealthy) CoreCraftingCurrency.entries.associateWith { 500_000L } else emptyMap(),
            fragments = if (wealthy) CoreActivityKind.entries.associateWith { 500_000L } else emptyMap(),
            weaponEnhancement = CoreEnhancementState(if (maximum) 30 else 6), armorEnhancement = CoreEnhancementState(if (maximum) 30 else 6),
            smithingXp = if (maximum) 200 else 0, craftingSeed = 0xC0DEL,
            affixStones = if (wealthy) listOf(CoreAffixStone(id("legacy"), "projects:force", tier, CoreAffixCatalog.definitions.first().range(tier).first.toDouble())) else emptyList())
        if (fullMods) for (gear in CoreGearSlot.entries) {
            result = CoreCraftingCatalog.craft(result, gear, CoreCraftingCurrency.ALCHEMY, id("$gear/alchemy"))
            while (result.equippedAffixes.count { it.gear == gear } < 6)
                result = CoreCraftingCatalog.craft(result, gear, CoreCraftingCurrency.EXALTED, id("$gear/exalted/${result.equippedAffixes.size}"))
        }
        return result
    }

    @Test fun `all real menu builders fit panels and hitboxes for empty wealthy and fully progressed accounts`() {
        val failures = mutableListOf<String>()
        for (tier in 1..4) for (variant in 0..3) for (packed in listOf(false, true)) {
            val f = fixture(account(tier, wealthy = variant != 0, fullMods = variant >= 2, maximum = variant == 3), packed)
            val prefix = "T$tier variant=$variant packed=$packed"
            fun check(name: String, render: () -> Unit) {
                try {
                    render()
                    val inventory = requireNotNull(f.player.openInventory)
                    require(name.startsWith("storage") || (0 until 54).all { inventory.getItemStack(it).let { item -> item.isAir || item.amount() == 1 } }) {
                        "A projected menu item would paint its stack count over the persistent label"
                    }
                    auditSnapshot(f.snapshot()).forEach { failures += "$prefix / $name: $it" }
                    if (!packed) {
                        val components = (0 until 54).flatMap { slot ->
                            val item = inventory.getItemStack(slot)
                            listOfNotNull(item.get(DataComponents.CUSTOM_NAME)) + item.get(DataComponents.LORE).orEmpty()
                        }
                        require(components.none { component -> plain(component).any { it.code in 0xE000..0xF8FF } }) { "Private glyph sent without the pack" }
                        val fallback = inventory.getItemStack(8).get(DataComponents.LORE).orEmpty().joinToString("\n", transform = ::plain)
                        for (panel in listOfNotNull(f.snapshot().leftPanel, f.snapshot().rightPanel)) {
                            require(panel.lines.all { fallback.contains(it.text) }) { "Fallback book lost visible panel facts" }
                        }
                        f.snapshot().focus?.let { require(fallback.contains(it.caption)) { "Fallback book lost the enhancement target" } }
                    }
                }
                catch (error: Exception) { failures += "$prefix / $name: ${error.message}" }
            }
            check("journal") { f.menus.journal(f.player) }
            check("career") { f.menus.career(f.player) }
            check("weapon bases") { f.menus.career(f.player); f.click(30) }
            check("temper weapon") { f.menus.career(f.player); f.click(33) }
            check("temper armor") { f.menus.career(f.player); f.click(33); f.click(12) }
            for (page in 0..1) check("maps$page") { f.menus.expeditions(f.player, tier, page) }
            check("map detail three modifiers") { f.menus.mapDetail(f.player, f.host.current.maps.first().id) }
            for (page in 0..8) check("storage$page") { f.menus.storage(f.player, tier, page) }
            check("workshop") { f.menus.workshop(f.player, tier) }
            for (tab in CoreForgeLayout.Tab.entries) check("forge ${tab.name}") { f.menus.workshop(f.player, tier); f.click(tab.slot) }
            for (gear in CoreGearSlot.entries) {
                check("gear ${gear.name}") { f.menus.gearMods(f.player, gear) }
                for (currency in CoreCraftingCurrency.entries) check("orb ${gear.name}/${currency.name}") { f.menus.confirmCraft(f.player, gear, currency) }
            }
            for (raw in CoreResource.entries.filter { it.raw }) for (q in CoreForgeLayout.Quantity.entries)
                check("exchange $raw/$q") { f.menus.supplies(f.player, tier, raw, q) }
            for (discipline in QuestGatheringDiscipline.entries) check("mastery $discipline") { f.menus.mastery(f.player, discipline) }
            for (section in 0..3) check("guide$section") { f.menus.guide(f.player, section) }
            check("help") { f.menus.journal(f.player); f.click(8) }
            check("dungeon") { f.menus.dungeons(f.player, tier) }
            check("trials") { f.menus.trials(f.player, tier) }
            for (index in 0..2) check("trial confirmation$index") { f.menus.trials(f.player, tier); f.click(9 + index * 3) }
            f.host.current.affixStones.firstOrNull()?.let { stone -> check("legacy stone") { f.menus.stoneDetail(f.player, stone.id) } }
            assertTrue(f.host.requests.isEmpty(), "Selections consumed resources: $prefix")
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test fun `every forge recipe and quantity selector changes selection without consuming anything`() {
        val f = fixture(account(tier = 3))
        for (tab in listOf(CoreForgeLayout.Tab.REFINE, CoreForgeLayout.Tab.CRAFT)) {
            val count = if (tab == CoreForgeLayout.Tab.REFINE) 5 else 4
            for (recipe in 0 until count) for (quantity in CoreForgeLayout.QUANTITIES.keys) {
                f.menus.workshop(f.player, 3)
                f.click(tab.slot)
                f.click(if (tab == CoreForgeLayout.Tab.REFINE) CoreLoopMenus.REFINE_SLOTS[recipe] else CoreForgeLayout.RECIPES[recipe])
                // Equipment Tier recipes deliberately have no quantity control.
                if (tab == CoreForgeLayout.Tab.REFINE || recipe > 0) f.click(quantity)
                assertTrue(f.host.requests.isEmpty())
            }
        }
    }

    @Test fun `every slot of the three journal illustrations opens the same intended destination`() {
        for ((start, destination) in listOf(9 to "地図台", 12 to "開拓工房", 15 to "素材倉庫")) {
            for (row in 0..2) for (column in 0..2) {
                val f = fixture(account())
                f.menus.journal(f.player)
                val original = f.snapshot()
                assertEquals(listOf("遠征", "工房", "保管庫"), original.cards.filter { it.rows == 3 }.map { it.label })
                assertNotNull(original.leftPanel?.hero)
                f.click(start + row * 9 + column, right = column == 1)
                assertTrue(f.snapshot().title.contains(destination), "Card at $start did not own row=$row column=$column")
                assertTrue(f.host.requests.isEmpty())
            }
        }
    }

    @Test fun `all six cells of each illustrated refine recipe select the same real output`() {
        CoreLoopCatalog.refined.keys.forEachIndexed { index, raw ->
            for (row in 0..1) for (column in 0..2) {
                val f = fixture(account(tier = 3))
                f.menus.workshop(f.player, 3)
                f.click(CoreForgeLayout.Tab.REFINE.slot)
                f.click(CoreLoopMenus.REFINE_SLOTS[index] + row * 9 + column)
                assertTrue(f.host.requests.isEmpty())
                f.click(CoreForgeLayout.EXECUTE)
                assertEquals(CoreAction.Refine(raw, 3, 1), f.host.requests.single().action)
                assertEquals(41L, f.host.requests.single().revision)
            }
        }
    }

    @Test fun `material illustrations never replace required and owned figures with decorative progress`() {
        val f = fixture(account(tier = 3))
        f.menus.workshop(f.player)
        val costs = assertNotNull(f.snapshot().rightPanel)
        assertEquals("必要素材", costs.title)
        assertEquals(3, costs.lines.count { it.art != null })
        assertEquals(listOf("T1 金属材 ×2", "T1 板材 ×1", "T1 切石 ×2"), costs.lines.filter { it.art != null }.map { it.text })
        assertEquals(List(3) { "足りる 所持500000" }, costs.lines.filter { it.art == null }.map { it.text })
        assertTrue(costs.lines.filter { it.art != null }.all { it.maxWidth == 70 })
        assertTrue(costs.lines.filter { it.text.startsWith("所持 ") }.all { it.maxWidth == 88 })
    }

    @Test fun `storage uses visible vanilla stacks full names and exact counts without side panels`() {
        for (packed in listOf(false, true)) {
            val a = CoreAccount(UUID.randomUUID(), balances = mapOf(CoreMaterial(CoreResource.WOOD, 2) to 1_000_000L,
                CoreMaterial(CoreResource.ORE, 2) to 9L, CoreMaterial(CoreResource.STONE, 2) to 1L))
            val f = fixture(a, packed)
            f.menus.storage(f.player, 2)
            val inventory = assertNotNull(f.player.openInventory)
            assertEquals("素材倉庫 / T2", f.title())
            assertFalse(plain((inventory as Inventory).title).any { it.code in 0xE000..0xF8FF })
            assertNull(f.snapshot().leftPanel)
            assertNull(f.snapshot().rightPanel)
            assertTrue(f.snapshot().cards.isEmpty())
            assertEquals(listOf(Material.OAK_LOG, Material.RAW_IRON, Material.COBBLESTONE),
                (9..11).map { inventory.getItemStack(it).material() })
            assertEquals(listOf(64, 9, 1), (9..11).map { inventory.getItemStack(it).amount() })
            assertTrue((12..44).all { inventory.getItemStack(it).isAir })
            assertTrue(inventory.getItemStack(9).get(DataComponents.LORE).orEmpty().any { plain(it) == "所持 1000000 個" })
            val before = f.host.current
            f.click(10)
            assertTrue(f.title().contains("精製"))
            assertTrue(f.host.requests.isEmpty())
            assertEquals(before, f.host.current)
        }
    }

    @Test fun `storage shows up to thirty six owned kinds and keeps empty cells empty`() {
        val a = account().let { it.copy(balances = it.balances + (CoreMaterial(CoreResource.WOOD) to 999_999L)) }
        val f = fixture(a)
        f.menus.storage(f.player)
        val inventory = assertNotNull(f.player.openInventory)
        val entries = CoreStorageView.entries(a, 1)
        assertEquals(entries.size.coerceAtMost(36), (9..44).count { !inventory.getItemStack(it).isAir })
        assertTrue(inventory.getItemStack(9).get(DataComponents.LORE).orEmpty().any { plain(it) == "所持 999999 個" })
        assertTrue(inventory.getItemStack(8).get(DataComponents.LORE).orEmpty().any { plain(it).contains("最大64") })
        f.click(6)
        assertEquals("素材倉庫 / T4", f.title())
        assertTrue(f.host.requests.isEmpty())
        val empty = fixture(account(wealthy = false))
        empty.menus.storage(empty.player)
        val emptyInventory = assertNotNull(empty.player.openInventory)
        assertEquals(listOf(22), (9..44).filter { !emptyInventory.getItemStack(it).isAir })
        empty.click(22)
        assertTrue(empty.host.requests.isEmpty())
    }

    @Test fun `storage cancels transfers and field shortcuts never spend or navigate to the forge`() {
        val a = account()
        val run = CoreActiveRun(UUID.randomUUID(), a.maps.first())
        val f = fixture(a.copy(activeRun = run, maps = a.maps.drop(1)))
        f.menus.storage(f.player)
        val inventory = assertNotNull(f.player.openInventory)
        val before = inventory.getItemStack(9)
        val shift = InventoryPreClickEvent(inventory, f.player, Click.LeftShift(9))
        assertTrue(f.menus.click(shift))
        assertTrue(shift.isCancelled)
        f.click(9)
        f.click(53)
        assertEquals("素材倉庫 / T1", f.title())
        assertEquals(before, inventory.getItemStack(9))
        assertTrue(f.host.requests.isEmpty())
    }

    @Test fun `mixed cost availability shows exact shortfall and reaching it enables the full execution verb`() {
        val a = account(wealthy = false).copy(balances = mapOf(
            CoreMaterial(CoreResource.INGOT) to 1L, CoreMaterial(CoreResource.BOARD) to 1L,
            CoreMaterial(CoreResource.STONE_BLOCK) to 2L))
        val f = fixture(a)
        f.menus.workshop(f.player)
        val panel = assertNotNull(f.snapshot().rightPanel)
        assertEquals(2, panel.lines.count { it.text.startsWith("足りる ") })
        assertEquals(1, panel.lines.count { it.text == "あと1個 所持1" })
        assertEquals("強化不可", f.snapshot().buttons.single { it.firstSlot == 51 }.label)
        f.click(51)
        assertTrue(f.host.requests.isEmpty())
        f.host.current = a.copy(balances = a.balances + (CoreMaterial(CoreResource.INGOT) to 2L))
        f.menus.refreshTheme(f.player)
        assertEquals("強化する", f.snapshot().buttons.single { it.firstSlot == 51 }.label)
        assertEquals("PRIMARY", f.snapshot().buttons.single { it.firstSlot == 51 }.tone)
        assertEquals(3, assertNotNull(f.snapshot().rightPanel).lines.count { it.text.startsWith("足りる ") })
    }

    @Test fun `the complete anvil subject opens equipment detail without spending or showing repeated item models`() {
        for (packed in listOf(false, true)) for (gear in CoreGearSlot.entries) for (slot in CoreLoopMenus.ENHANCE_FOCUS_SLOTS) {
            val f = fixture(account(tier = 3, fullMods = true), packed)
            f.menus.workshop(f.player, 3)
            if (gear == CoreGearSlot.ARMOR) f.click(CoreForgeLayout.ARMOR)
            val snapshot = f.snapshot()
            val focus = assertNotNull(snapshot.focus)
            assertEquals(CoreLoopMenus.ENHANCE_FOCUS_SLOTS, focus.reservedSlots)
            assertEquals(if (gear == CoreGearSlot.WEAPON) "WEAPON" else "ARMOR", focus.artPlacement.art)
            assertEquals(48, focus.artPlacement.size)
            assertEquals("+6 → +7", focus.caption)
            assertNull(snapshot.leftPanel?.hero)
            val back = snapshot.buttons.single { it.firstSlot == CoreForgeLayout.BACK }
            assertEquals(1, back.span)
            assertEquals("←", back.label)
            assertTrue(snapshot.cards.none { card -> card.occupiedSlots.any { it in CoreLoopMenus.ENHANCE_FOCUS_SLOTS } })
            val expected = CoreLoopItems.gear(f.host.current, gear, packed)
            val projected = assertNotNull(f.player.openInventory).getItemStack(slot)
            assertEquals(expected.get(DataComponents.LORE), projected.get(DataComponents.LORE))
            assertEquals(expected.get(DataComponents.CUSTOM_NAME), projected.get(DataComponents.CUSTOM_NAME))
            if (packed) assertEquals("projects:core_ui/blank", projected.get(DataComponents.ITEM_MODEL))
            f.click(slot, right = slot % 2 == 0)
            assertEquals("装備 / MOD詳細", f.snapshot().title)
            assertTrue(f.host.requests.isEmpty())
        }
    }

    @Test fun `every catalyst and standard control keeps preview and dispatched enhancement mode in sync`() {
        for (gear in CoreGearSlot.entries) for (offset in 0..2) for (focused in listOf(false, true)) {
            val f = fixture(account(tier = 3))
            f.menus.workshop(f.player, 3)
            if (gear == CoreGearSlot.ARMOR) f.click(CoreForgeLayout.ARMOR)
            f.click(CoreLoopMenus.ENHANCE_CATALYST + offset)
            if (!focused) f.click(CoreLoopMenus.ENHANCE_STANDARD + offset)
            val mode = if (focused) CoreEnhancementMode.FOCUSED else CoreEnhancementMode.STANDARD
            val quote = CoreEnhancementCatalog.quote(f.host.current, gear, mode)
            val panel = assertNotNull(f.snapshot().leftPanel)
            val chance = quote.successChancePercent.toString().removeSuffix(".0")
            assertTrue(panel.lines.any { it.text == "成功率 $chance%" && it.style == "EMPHASIS" })
            val upgraded = if (gear == CoreGearSlot.WEAPON) f.host.current.copy(weaponEnhancement = CoreEnhancementState(7))
                else f.host.current.copy(armorEnhancement = CoreEnhancementState(7))
            val stat = if (gear == CoreGearSlot.WEAPON) "${CoreWeaponPresentation.damage(f.host.current)} → ${CoreWeaponPresentation.damage(upgraded)}"
                else "${CoreWeaponPresentation.health(f.host.current)} → ${CoreWeaponPresentation.health(upgraded)}"
            assertTrue(panel.lines.any { it.text == stat && it.style == "EMPHASIS" })
            assertTrue(panel.lines.any { it.text == "素材は毎回消費" })
            assertTrue(panel.lines.any { it.text == "今回の破損なし" })
            assertTrue(panel.lines.any { it.text == "強化値・MODは維持" })
            val costs = assertNotNull(f.snapshot().rightPanel)
            assertEquals(quote.recipe.costs.size, costs.lines.count { it.art != null })
            assertEquals(quote.recipe.costs.values.map { "×$it" }, costs.lines.filter { it.art != null }.map { it.text.substringAfterLast(' ') })
            assertTrue(f.host.requests.isEmpty())
            f.click(CoreForgeLayout.EXECUTE + offset)
            assertEquals(CoreAction.EnhanceEquipment(gear, mode), f.host.requests.single().action)
            assertEquals(41L, f.host.requests.single().revision)
        }
    }

    @Test fun `shortage and maximum state explain why enhancement cannot execute and never spend`() {
        for (maximum in listOf(false, true)) {
            val f = fixture(account(wealthy = false, maximum = maximum))
            f.menus.workshop(f.player)
            val snapshot = f.snapshot()
            assertEquals(if (maximum) "最大強化" else "強化不可", snapshot.buttons.single { it.firstSlot == 51 }.label)
            assertEquals(if (maximum) "DISABLED" else "DANGER", snapshot.buttons.single { it.firstSlot == 51 }.tone)
            if (maximum) {
                assertEquals("+30", snapshot.focus?.caption)
                assertTrue(assertNotNull(snapshot.leftPanel).lines.any { it.text == "最大強化 +30" })
                assertTrue(assertNotNull(snapshot.rightPanel).lines.any { it.text == "消費なし" })
            } else {
                assertEquals(listOf("あと2個 所持0", "あと1個 所持0", "あと2個 所持0"), assertNotNull(snapshot.rightPanel).lines.filter { it.art == null }.map { it.text })
            }
            for (slot in 51..53) f.click(slot)
            assertTrue(f.host.requests.isEmpty())
        }
    }

    @Test fun `failure progress remains visible and pity guarantee disables unnecessary catalyst spending`() {
        for (failures in listOf(2, 4)) {
            val f = fixture(account(tier = 3).copy(weaponEnhancement = CoreEnhancementState(6, failures)))
            f.menus.workshop(f.player)
            val snapshot = f.snapshot()
            val panel = assertNotNull(snapshot.leftPanel)
            if (failures == 2) assertTrue(panel.lines.any { it.text == "成功保証 2/4" })
            else {
                assertTrue(panel.lines.any { it.text == "成功率 100%" && it.style == "EMPHASIS" })
                assertTrue(panel.lines.any { it.text == "次の強化は成功確定" })
                assertEquals("DISABLED", snapshot.cards.single { it.firstSlot == CoreLoopMenus.ENHANCE_CATALYST }.tone)
                for (slot in CoreLoopMenus.ENHANCE_CATALYST..CoreLoopMenus.ENHANCE_CATALYST + 2) f.click(slot)
                assertEquals(3, assertNotNull(f.snapshot().rightPanel).lines.count { it.art != null })
            }
            assertTrue(f.host.requests.isEmpty())
            f.click(CoreForgeLayout.EXECUTE)
            assertEquals(CoreAction.EnhanceEquipment(CoreGearSlot.WEAPON), f.host.requests.single().action)
        }
    }

    @Test fun `all three execute label slots dispatch the selected recipe with the displayed revision`() {
        for (offset in 0..2) {
            val f = fixture(account(tier = 2))
            f.menus.workshop(f.player, 2)
            f.click(CoreForgeLayout.Tab.REFINE.slot)
            f.click(CoreLoopMenus.REFINE_SLOTS[1] + offset)
            f.click(CoreForgeLayout.QUANTITIES.entries.first { it.value == CoreForgeLayout.Quantity.FIVE }.key)
            f.host.current = f.host.current.copy(revision = 99)
            f.click(CoreForgeLayout.EXECUTE + offset, right = offset == 1)
            val sent = f.host.requests.single()
            assertEquals(41L, sent.revision)
            assertEquals(CoreAction.Refine(CoreResource.ORE, 2, 5), sent.action)
        }
    }

    @Test fun `nested material detours restore selected armor enhancement without implicit crafting`() {
        val f = fixture(account(tier = 3))
        f.menus.workshop(f.player, 3)
        f.click(CoreForgeLayout.ARMOR + 2)
        val original = f.title()
        f.click(CoreForgeLayout.MATERIALS + 2)
        f.click(9) // Leather cost -> hide refining; enhancement is saved as the goal.
        assertTrue(f.title().contains("精製"))
        f.click(CoreForgeLayout.MATERIALS)
        f.click(9) // Raw hide -> token supplies; the refining selection is a nested goal.
        assertTrue(f.title().contains("市場"))
        f.click(CoreForgeLayout.BACK)
        assertTrue(f.title().contains("精製"))
        f.click(CoreForgeLayout.BACK + 1)
        assertEquals(original, f.title())
        assertTrue(f.host.requests.isEmpty())
        f.click(CoreForgeLayout.EXECUTE)
        assertEquals(CoreAction.EnhanceEquipment(CoreGearSlot.ARMOR), f.host.requests.single().action)
    }

    @Test fun `completion and rejection cannot reopen closed menus or replace a newer screen`() {
        for (rejected in listOf(false, true)) for (closed in listOf(false, true)) {
            val f = fixture(account())
            f.menus.workshop(f.player)
            f.click(CoreForgeLayout.EXECUTE)
            val pending = f.host.requests.single()
            if (closed) f.player.closeInventory() else f.menus.storage(f.player, 1)
            val currentInventory = f.player.openInventory as? Inventory
            val currentTitle = currentInventory?.title
            if (rejected) pending.rejected?.invoke() else pending.after()
            assertSame(currentInventory, f.player.openInventory)
            assertEquals(currentTitle, (f.player.openInventory as? Inventory)?.title)
        }
    }

    @Test fun `theme refresh keeps native storage tier and page`() {
        val f = fixture(account(tier = 3))
        f.menus.storage(f.player, 3, 1)
        val title = f.title()
        f.host.usePack = false
        f.menus.refreshTheme(f.player)
        assertEquals("素材倉庫 / T3", f.title())
        f.host.usePack = true
        f.menus.refreshTheme(f.player)
        assertEquals(title, f.title())
    }

    @Test fun `field gear inspection allows armor selection but disables crafting`() {
        val initial = account(tier = 2, fullMods = true)
        val run = CoreActiveRun(UUID.randomUUID(), CoreOwnedMap(UUID.randomUUID(), 123, 2))
        val f = fixture(initial.copy(activeRun = run))
        f.menus.journal(f.player)
        f.menus.gearMods(f.player, CoreGearSlot.WEAPON)
        f.click(3)
        assertTrue(f.title().contains("防具"))
        f.click(CoreForgeLayout.EXECUTE)
        assertTrue(f.host.requests.isEmpty())
    }

    @Test fun `actual menu snapshots keep full labels and essential figures readable and export visual fixtures`() {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
        val output = Files.createDirectories(root.resolve("build/readable-ui-preview"))
        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        val f = fixture(account(tier = 3, fullMods = true))
        val failures = mutableListOf<String>()
        fun capture(name: String, render: () -> Unit) {
            render()
            val snapshot = f.snapshot()
            Files.writeString(output.resolve("$name.json"), gson.toJson(snapshot))
            auditSnapshot(snapshot).forEach { failures += "$name: $it" }
        }
        capture("dungeon-entry") { f.menus.dungeons(f.player, 3) }
        val dungeonPlan = DungeonPlan.generate(32, 3, 0)
        f.host.dungeonState = DungeonRunView(UUID.randomUUID(), f.player.uuid, dungeonPlan.choices(4).first(),
            dungeonPlan.stages, 0, DungeonRunPhase.CHOOSING, "次の道を選ぶ", 2, dungeonPlan.choices(5),
            listOf(DungeonBoon.FORCE, DungeonBoon.GUARD, DungeonBoon.FLAME), false, mapOf(DungeonBoon.VITALITY to 2, DungeonBoon.HASTE to 1), 2)
        capture("dungeon-boons") { f.menus.dungeonRun(f.player) }
        f.host.dungeonState = null
        capture("journal") { f.menus.journal(f.player) }
        capture("career") { f.menus.career(f.player) }
        capture("weapon-bases") { f.menus.career(f.player); f.click(30) }
        capture("temper") { f.menus.career(f.player); f.click(33) }
        capture("forge-enhance") { f.menus.workshop(f.player, 3); f.click(CoreForgeLayout.ARMOR); f.click(CoreLoopMenus.ENHANCE_CATALYST) }
        capture("forge-weapon") { f.click(CoreForgeLayout.WEAPON); f.click(CoreLoopMenus.ENHANCE_STANDARD) }
        capture("forge-catalyst-weapon") { f.click(CoreLoopMenus.ENHANCE_CATALYST) }
        capture("forge-refine") {
            f.click(CoreForgeLayout.Tab.REFINE.slot); f.click(CoreLoopMenus.REFINE_SLOTS[3]); f.click(48)
        }
        capture("mod") { f.menus.confirmCraft(f.player, CoreGearSlot.ARMOR, CoreCraftingCurrency.DIVINE) }
        capture("craft") { f.click(CoreForgeLayout.Tab.CRAFT.slot); f.click(CoreForgeLayout.RECIPES[2]); f.click(48) }
        val empty = fixture(account(wealthy = false))
        val mixed = fixture(account(wealthy = false).copy(balances = mapOf(
            CoreMaterial(CoreResource.INGOT) to 1L, CoreMaterial(CoreResource.BOARD) to 1L,
            CoreMaterial(CoreResource.STONE_BLOCK) to 2L)))
        val maximum = fixture(account(tier = 4, maximum = true))
        val failed = fixture(account(tier = 3).copy(weaponEnhancement = CoreEnhancementState(6, 2)))
        val guaranteed = fixture(account(tier = 3).copy(weaponEnhancement = CoreEnhancementState(6, 4)))
        val nearMaximum = fixture(account(tier = 4).copy(weaponEnhancement = CoreEnhancementState(29, 6)))
        for ((name, subject, render) in listOf(
            Triple("forge-empty", empty) { empty.menus.workshop(empty.player, 1) },
            Triple("forge-mixed-costs", mixed) { mixed.menus.workshop(mixed.player, 1) },
            Triple("forge-max", maximum) { maximum.menus.workshop(maximum.player, 4) },
            Triple("forge-after-failure", failed) { failed.menus.workshop(failed.player, 3) },
            Triple("forge-guaranteed", guaranteed) { guaranteed.menus.workshop(guaranteed.player, 3) },
            Triple("forge-near-max", nearMaximum) { nearMaximum.menus.workshop(nearMaximum.player, 4) },
        )) {
            render()
            Files.writeString(output.resolve("$name.json"), gson.toJson(subject.snapshot()))
            auditSnapshot(subject.snapshot()).forEach { failures += "$name: $it" }
            assertTrue(subject.host.requests.isEmpty())
        }
        assertTrue(f.host.requests.isEmpty())
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun auditSnapshot(snapshot: CoreMenuCanvas.Snapshot): List<String> = buildList {
        fun check(label: String, value: String, width: Int, style: TextStyle = TextStyle.BODY) {
            if (CoreMenuCanvas.width(value, style) > width) add("$label exceeds $width px: '$value' (${CoreMenuCanvas.width(value, style)})")
            val missing = CoreMenuCanvas.missingCharacters(value)
            if (missing.isNotEmpty()) add("$label has missing glyphs: ${missing.map { "U+${it.toString(16)}" }} '$value'")
        }
        check("title", snapshot.title, 160, TextStyle.EMPHASIS)
        listOf("left" to snapshot.leftPanel, "right" to snapshot.rightPanel).forEach { (side, panel) ->
            if (panel != null) {
                check("$side title", panel.title, CoreMenuCanvas.PANEL_WIDTH, TextStyle.EMPHASIS)
                panel.lines.forEachIndexed { index, line -> check("$side line $index", line.text, line.maxWidth, TextStyle.valueOf(line.style)) }
            }
        }
        snapshot.buttons.forEach { button -> check("button ${button.firstSlot}", button.label, button.span * 18 - 2 - if (button.icon) 18 else 0, TextStyle.EMPHASIS) }
        val occupied = mutableSetOf<Int>()
        snapshot.buttons.forEach { button ->
            (button.firstSlot until button.firstSlot + button.span).forEach { if (!occupied.add(it)) add("Overlapping button $it") }
        }
        snapshot.cards.forEach { card ->
            check("card ${card.firstSlot}", card.label, card.labelMaxWidth, TextStyle.EMPHASIS)
            card.occupiedSlots.forEach { slot ->
                if (slot !in 0..53) add("Card escaped top inventory: $slot")
                if (!occupied.add(slot)) add("Overlapping card slot $slot")
            }
            snapshot.texts.forEach { text ->
                val width = minOf(CoreMenuCanvas.width(text.value, TextStyle.valueOf(text.style)), text.maxWidth)
                if (text.x < card.x + card.width && text.x + width > card.x &&
                    text.y < card.y + card.height && text.y + CoreMenuCanvas.LINE_HEIGHT > card.y)
                    add("Text '${text.value}' overlaps illustrated card ${card.firstSlot}")
            }
        }
        snapshot.focus?.let { focus ->
            check("focus caption", focus.caption, focus.captionMaxWidth, TextStyle.valueOf(focus.style))
            focus.reservedSlots.forEach { slot -> if (!occupied.add(slot)) add("Focus overlaps control $slot") }
            val captionWidth = CoreMenuCanvas.width(focus.caption, TextStyle.valueOf(focus.style))
            snapshot.buttons.forEach { button ->
                val x = 8 + button.firstSlot % 9 * 18
                val y = 18 + button.firstSlot / 9 * 18
                if (focus.captionX < x + button.span * 18 - 2 && focus.captionX + captionWidth > x &&
                    focus.captionY < y + 16 && focus.captionY + CoreMenuCanvas.LINE_HEIGHT > y)
                    add("Enhancement level caption overlaps button ${button.firstSlot}")
            }
        }
        snapshot.texts.forEach { text -> check("text ${text.x},${text.y}", text.value, text.maxWidth, TextStyle.valueOf(text.style)) }
    }

    companion object {
        private val glyphs: Map<Int, String> by lazy {
            requireNotNull(CoreLoopMenusTest::class.java.classLoader.getResourceAsStream("core-ui-pack/assets/projects/menu/glyphs.tsv"))
                .bufferedReader().useLines { rows -> rows.filter { it.isNotBlank() && !it.startsWith('#') }.associate { row ->
                    val parts = row.split('\t')
                    parts[1].toInt(16) to String(Character.toChars(parts[0].toInt(16)))
                } }
        }
        private fun plain(component: Component): String = (component as? TextComponent)?.content().orEmpty() + component.children().joinToString("") { plain(it) }
        private fun decode(component: Component): String = buildString { plain(component).codePoints().forEach { append(glyphs[it] ?: String(Character.toChars(it))) } }
    }
}
