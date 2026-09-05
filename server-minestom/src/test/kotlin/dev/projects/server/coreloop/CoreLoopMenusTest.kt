package dev.projects.server.coreloop

import dev.projects.server.coreloop.ui.CoreForgeLayout
import dev.projects.server.questmap.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.Player
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.click.Click
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import kotlin.test.*

/** Exercises the real builders and click dispatch without a world, server socket or player save. */
class CoreLoopMenusTest {
    private data class Pending(val action: CoreAction, val revision: Long, val rejected: (() -> Unit)?, val after: () -> Unit)
    private class Host(var current: CoreAccount, var usePack: Boolean = true) : CoreMenuHost {
        val requests = mutableListOf<Pending>()
        val departures = mutableListOf<Pair<UUID, Long>>()
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

    private class Fixture(val player: Player, val host: Host, val menus: CoreLoopMenus) {
        fun click(slot: Int, right: Boolean = false) {
            val inventory = assertNotNull(player.openInventory)
            val event = InventoryPreClickEvent(inventory, player, if (right) Click.Right(slot) else Click.Left(slot))
            assertTrue(menus.click(event), "Click was not owned: $slot")
            assertTrue(event.isCancelled)
        }
        fun title(): String = decode((assertNotNull(player.openInventory) as Inventory).title)
    }

    @BeforeTest fun initializeMinestom() { MinecraftServer.init(Auth.Offline()) }

    private fun fixture(account: CoreAccount, packed: Boolean = true): Fixture {
        val connection = object : PlayerConnection() {
            override fun sendPacket(packet: SendablePacket) = Unit
            override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
        }
        val player = Player(connection, GameProfile(account.playerId, "MenuIntegration"))
        connection.player = player
        val host = Host(account, packed)
        return Fixture(player, host, CoreLoopMenus(host))
    }

    private fun account(tier: Int = 1, wealthy: Boolean = true, fullMods: Boolean = false, maximum: Boolean = false): CoreAccount {
        val globals = setOf(CoreResource.POTION, CoreResource.GATHERING_TABLET, CoreResource.WHETSTONE, CoreResource.AFFIX_DUST)
        val balances = if (wealthy) buildMap {
            CoreResource.entries.forEach { resource ->
                (if (resource in globals) listOf(1) else (1..4).toList()).forEach { t -> put(CoreMaterial(resource, t), 500_000L) }
            }
        } else emptyMap()
        val maps = List(9) { index -> CoreOwnedMap(UUID.randomUUID(), index.toLong(), tier, listOf(
            CoreMapModifier("woodcutting", "amount", 200), CoreMapModifier("mining", "dense_regions", 200),
            CoreMapModifier("herbalism", "quality", 200))) }
        var result = CoreAccount(UUID.randomUUID(), revision = 41, balances = balances, weaponTier = tier, armorTier = tier,
            unlockedMapTier = tier, maps = maps, currencies = if (wealthy) CoreCraftingCurrency.entries.associateWith { 500_000L } else emptyMap(),
            fragments = if (wealthy) CoreActivityKind.entries.associateWith { 500_000L } else emptyMap(),
            weaponEnhancement = CoreEnhancementState(if (maximum) 30 else 6), armorEnhancement = CoreEnhancementState(if (maximum) 30 else 6),
            smithingXp = if (maximum) 200 else 0,
            affixStones = if (wealthy) listOf(CoreAffixStone(UUID.randomUUID(), "projects:force", tier, CoreAffixCatalog.definitions.first().range(tier).first.toDouble())) else emptyList())
        if (fullMods) for (gear in CoreGearSlot.entries) {
            result = CoreCraftingCatalog.craft(result, gear, CoreCraftingCurrency.ALCHEMY, UUID.randomUUID())
            while (result.equippedAffixes.count { it.gear == gear } < 6) result = CoreCraftingCatalog.craft(result, gear, CoreCraftingCurrency.EXALTED, UUID.randomUUID())
        }
        return result
    }

    @Test fun `all real menu builders fit panels and hitboxes for empty wealthy and fully progressed accounts`() {
        val failures = mutableListOf<String>()
        for (tier in 1..4) for (variant in 0..3) for (packed in listOf(false, true)) {
            val f = fixture(account(tier, wealthy = variant != 0, fullMods = variant >= 2, maximum = variant == 3), packed)
            val prefix = "T$tier variant=$variant packed=$packed"
            fun check(name: String, render: () -> Unit) {
                try { render(); assertNotNull(f.player.openInventory) }
                catch (error: Exception) { failures += "$prefix / $name: ${error.message}" }
            }
            check("journal") { f.menus.journal(f.player) }
            for (page in 0..1) check("maps$page") { f.menus.expeditions(f.player, tier, page) }
            check("map detail three modifiers") { f.menus.mapDetail(f.player, f.host.current.maps.first().id) }
            for (page in 0..4) check("storage$page") { f.menus.storage(f.player, tier, page) }
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
            check("trials") { f.menus.trials(f.player, tier) }
            for (index in 0..2) check("trial confirmation$index") { f.menus.trials(f.player, tier); f.click(10 + index * 9) }
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
                f.click(CoreForgeLayout.RECIPES[recipe])
                // Equipment Tier recipes deliberately have no quantity control.
                if (tab == CoreForgeLayout.Tab.REFINE || recipe > 0) f.click(quantity)
                assertTrue(f.host.requests.isEmpty())
            }
        }
    }

    @Test fun `all three execute label slots dispatch the selected recipe with the displayed revision`() {
        for (offset in 0..2) {
            val f = fixture(account(tier = 2))
            f.menus.workshop(f.player, 2)
            f.click(CoreForgeLayout.Tab.REFINE.slot)
            f.click(CoreForgeLayout.RECIPES[1] + offset)
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
        assertTrue(f.title().contains("補給所"))
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

    @Test fun `theme refresh keeps storage tier page and selected detail`() {
        val f = fixture(account(tier = 3))
        f.menus.storage(f.player, 3, 1, 2)
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
