package dev.projects.server.coreloop

import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class CoreCraftingCurrencyTest {
    private val weapon = CoreGearSlot.WEAPON
    private val allCurrencies = CoreCraftingCurrency.entries.associateWith { 100L }
    private class Fixture(
        initial: CoreAccount,
        repository: CoreAccountRepository = CoreAccountRepository(Files.createTempDirectory("projects-orb")),
    ) {
        val player = initial.playerId
        val service = CoreAccountService(repository)
        init { assertEquals(CoreRepositorySave.Saved, repository.commit(0, initial)); service.open(player) }
        val account get() = service.snapshot(player)!!
        fun perform(action: CoreAction) = service.transact(player, CoreOperation(UUID.randomUUID(), account.revision, action))
        fun commit(action: CoreAction) = perform(action).also { assertEquals(CoreTransactionStatus.COMMITTED, it.status, it.message) }.account!!
        fun craft(currency: CoreCraftingCurrency, gear: CoreGearSlot = CoreGearSlot.WEAPON) = commit(CoreAction.CraftEquipment(gear, currency))
        fun start(): UUID {
            val map = commit(CoreAction.ClaimMap(1, 123)).maps.last()
            return UUID.randomUUID().also { commit(CoreAction.StartRun(map.id, it)) }
        }
    }
    private fun initial(tier: Int = 1) = CoreAccount(UUID.randomUUID(), revision = 1, weaponTier = tier, armorTier = tier,
        currencies = allCurrencies, craftingSeed = 123456789)
    private fun assertLayout(account: CoreAccount, gear: CoreGearSlot) {
        val mods = account.equippedAffixes.filter { it.gear == gear }
        val rarity = CoreAffixCatalog.rarity(account, gear)
        assertTrue(CoreCraftingCatalog.validLayout(mods, rarity))
        assertEquals(mods.size, mods.map { it.stone.modId }.distinct().size)
        mods.forEach { assertTrue(CoreAffixCatalog.valid(it.stone)); assertEquals(CoreAffixCatalog.gearTier(account, gear), it.stone.tier) }
        assertFalse(gear in account.legacyLayouts)
    }

    @Test fun `rarity and six affix slots are independent of gear tier and upgrade preserves all rolls`() {
        val f = Fixture(initial().copy(balances = CoreLoopCatalog.weaponUpgrade(1).costs))
        assertEquals(0, CoreAffixCatalog.capacity(f.account, weapon))
        f.craft(CoreCraftingCurrency.ALCHEMY)
        assertEquals(CoreGearRarity.RARE, f.account.weaponRarity)
        while (f.account.equippedAffixes.size < 6) f.craft(CoreCraftingCurrency.EXALTED)
        assertEquals(6, CoreAffixCatalog.capacity(f.account, weapon))
        assertEquals(1, f.account.weaponTier)
        assertLayout(f.account, weapon)
        val mods = f.account.equippedAffixes
        f.commit(CoreAction.UpgradeWeapon)
        assertEquals(2, f.account.weaponTier)
        assertEquals(mods, f.account.equippedAffixes)
        assertEquals(CoreGearRarity.RARE, f.account.weaponRarity)
        f.service.forget(f.player); f.service.open(f.player)
        assertEquals(mods, f.account.equippedAffixes)
        assertEquals(123456789L, f.account.craftingSeed)
    }

    @Test fun `astral currency replaces exactly one random mod while preserving quality enhancement and identity`() {
        repeat(120) { seed ->
            val f = Fixture(initial(1 + seed % 4).copy(craftingSeed = seed.toLong(),
                weaponIdentity = initial().weaponIdentity.copy(quality = 17),
                weaponEnhancement = CoreEnhancementState(12)))
            f.craft(CoreCraftingCurrency.ALCHEMY)
            val before = f.account
            val operation = CoreOperation(UUID.randomUUID(), before.revision, CoreAction.CraftEquipment(weapon, CoreCraftingCurrency.ASTRAL))
            assertTrue(f.service.transact(f.player, operation).successful)
            val after = f.account
            assertEquals(before.equippedAffixes.size, after.equippedAffixes.size)
            assertEquals(before.equippedAffixes.size - 1, after.equippedAffixes.count { it in before.equippedAffixes })
            assertEquals(before.weaponIdentity, after.weaponIdentity)
            assertEquals(before.weaponEnhancement, after.weaponEnhancement)
            assertEquals(before.weaponRarity, after.weaponRarity)
            assertEquals(99, after.amount(CoreCraftingCurrency.ASTRAL))
            assertLayout(after, weapon)
            assertEquals(CoreTransactionStatus.REPLAYED, f.service.transact(f.player, operation).status)
            assertEquals(after, f.account)
        }
    }

    @Test fun `all ordinary currency transitions consume one and preserve only their promised parts`() {
        val f = Fixture(initial())
        f.craft(CoreCraftingCurrency.TRANSMUTATION)
        assertEquals(CoreGearRarity.MAGIC, f.account.weaponRarity)
        assertTrue(f.account.equippedAffixes.size in 1..2)
        if (f.account.equippedAffixes.size == 1) f.craft(CoreCraftingCurrency.AUGMENTATION)
        assertLayout(f.account, weapon)
        assertEquals(2, f.account.equippedAffixes.size)
        val before = f.account.equippedAffixes
        f.craft(CoreCraftingCurrency.ALTERATION)
        assertTrue(f.account.equippedAffixes.none { next -> before.any { it.stone.id == next.stone.id } })
        val magic = f.account.equippedAffixes
        f.craft(CoreCraftingCurrency.REGAL)
        assertEquals(CoreGearRarity.RARE, f.account.weaponRarity)
        assertEquals(magic.size + 1, f.account.equippedAffixes.size)
        assertTrue(f.account.equippedAffixes.containsAll(magic))
        f.craft(CoreCraftingCurrency.CHAOS)
        assertTrue(f.account.equippedAffixes.size in 4..6)
        assertLayout(f.account, weapon)
        val rare = f.account.equippedAffixes
        f.craft(CoreCraftingCurrency.DIVINE)
        assertEquals(rare.map { it.stone.id to it.stone.modId }, f.account.equippedAffixes.map { it.stone.id to it.stone.modId })
        assertLayout(f.account, weapon)
        f.craft(CoreCraftingCurrency.SCOURING)
        assertEquals(CoreGearRarity.NORMAL, f.account.weaponRarity)
        assertTrue(f.account.equippedAffixes.isEmpty())
        f.craft(CoreCraftingCurrency.ALCHEMY, CoreGearSlot.ARMOR)
        assertEquals(CoreGearRarity.RARE, f.account.armorRarity)
        assertEquals(CoreGearRarity.NORMAL, f.account.weaponRarity)
        assertLayout(f.account, CoreGearSlot.ARMOR)
        assertEquals(99, f.account.amount(CoreCraftingCurrency.ALCHEMY))
    }

    @Test fun `special currency guarantees elements upper quarter and same mod lucky reroll`() {
        for (tier in 1..4) {
            val f = Fixture(initial(tier))
            repeat(24) {
                f.craft(CoreCraftingCurrency.RIFT)
                assertLayout(f.account, weapon)
                assertTrue(f.account.equippedAffixes.any { CoreAffixCatalog.definition(it.stone)!!.stat in setOf(CoreAffixStat.FIRE, CoreAffixStat.ICE, CoreAffixStat.LIGHTNING) })
                if (f.account.equippedAffixes.size < 6) {
                    val old = f.account.equippedAffixes
                    f.craft(CoreCraftingCurrency.TRIAL)
                    val added = (f.account.equippedAffixes - old.toSet()).single()
                    assertTrue(CoreAffixCatalog.qualityPercent(added.stone) >= 75)
                    assertTrue(f.account.equippedAffixes.containsAll(old))
                }
                val old = f.account.equippedAffixes
                f.craft(CoreCraftingCurrency.RITUAL)
                assertEquals(old.map { Triple(it.stone.id, it.stone.modId, it.stone.tier) },
                    f.account.equippedAffixes.map { Triple(it.stone.id, it.stone.modId, it.stone.tier) })
            }
        }
    }

    @Test fun `thousands of independent rolls respect type prefix suffix and no duplicate constraints`() {
        val seen = mutableSetOf<String>()
        for (tier in 1..4) for (gear in CoreGearSlot.entries) for (index in 0..149) {
            val original = initial(tier)
            val currency = if (index % 2 == 0) CoreCraftingCurrency.ALCHEMY else CoreCraftingCurrency.TRANSMUTATION
            val request = UUID(tier.toLong(), (gear.ordinal * 1000 + index).toLong())
            val result = CoreCraftingCatalog.craft(original, gear, currency, request)
            assertLayout(result, gear)
            result.equippedAffixes.forEach { assertTrue(gear in CoreAffixCatalog.definition(it.stone)!!.allowedGear); seen += it.stone.modId }
            val retry = CoreCraftingCatalog.craft(original, gear, currency, request)
            assertEquals(result.equippedAffixes, retry.equippedAffixes)
            assertEquals(result.currencies, retry.currencies)
        }
        assertEquals(CoreAffixCatalog.definitions.map { it.id }.toSet(), seen)
    }

    @Test fun `wrong rarity full slots missing balance active run and stale operations spend nothing`() {
        val f = Fixture(initial())
        listOf(CoreCraftingCurrency.AUGMENTATION, CoreCraftingCurrency.ALTERATION, CoreCraftingCurrency.CHAOS,
            CoreCraftingCurrency.REGAL, CoreCraftingCurrency.EXALTED, CoreCraftingCurrency.SCOURING,
            CoreCraftingCurrency.DIVINE, CoreCraftingCurrency.RITUAL, CoreCraftingCurrency.TRIAL, CoreCraftingCurrency.ASTRAL).forEach {
            assertNotNull(CoreCraftingCatalog.canUse(f.account, weapon, it))
            assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.CraftEquipment(weapon, it)).status)
        }
        assertEquals(allCurrencies, f.account.currencies)
        f.craft(CoreCraftingCurrency.ALCHEMY)
        while (f.account.equippedAffixes.size < 6) f.craft(CoreCraftingCurrency.EXALTED)
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.CraftEquipment(weapon, CoreCraftingCurrency.EXALTED)).status)
        val before = CoreAccountCodec.encode(f.account)
        assertEquals(CoreTransactionStatus.STALE, f.service.transact(f.player,
            CoreOperation(UUID.randomUUID(), 1, CoreAction.CraftEquipment(weapon, CoreCraftingCurrency.CHAOS))).status)
        assertEquals(before, CoreAccountCodec.encode(f.account))
        f.start()
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.CraftEquipment(weapon, CoreCraftingCurrency.CHAOS)).status)
        val poor = Fixture(CoreAccount(UUID.randomUUID(), revision = 1))
        assertEquals(CoreTransactionStatus.REJECTED, poor.perform(CoreAction.CraftEquipment(weapon, CoreCraftingCurrency.ALCHEMY)).status)
    }

    @Test fun `save failure leaves gear and currency unchanged and exact retry after reopen commits one result`() {
        var fail = false
        val repository = CoreAccountRepository(Files.createTempDirectory("projects-orb-failure")) { from, to ->
            if (fail) error("disk failure")
            Files.move(from, to, ATOMIC_MOVE, REPLACE_EXISTING)
        }
        val f = Fixture(initial(), repository)
        val old = CoreAccountCodec.encode(f.account)
        val operation = CoreOperation(UUID.randomUUID(), 1, CoreAction.CraftEquipment(weapon, CoreCraftingCurrency.ALCHEMY))
        val expected = CoreCraftingCatalog.craft(f.account, weapon, CoreCraftingCurrency.ALCHEMY, operation.requestId)
        fail = true
        assertEquals(CoreTransactionStatus.SAVE_FAILED, f.service.transact(f.player, operation).status)
        assertEquals(old, CoreAccountCodec.encode(f.account))
        f.service.forget(f.player); f.service.open(f.player)
        fail = false
        assertEquals(CoreTransactionStatus.COMMITTED, f.service.transact(f.player, operation).status)
        assertEquals(expected.equippedAffixes, f.account.equippedAffixes)
        assertEquals(99, f.account.amount(CoreCraftingCurrency.ALCHEMY))
        assertEquals(CoreTransactionStatus.REPLAYED, f.service.transact(f.player, operation).status)
        assertEquals(CoreTransactionStatus.CONFLICT, f.service.transact(f.player, operation.copy(action = CoreAction.CraftEquipment(weapon, CoreCraftingCurrency.CHAOS))).status)
    }

    @Test fun `loot draws only nine ordinary currencies and never exposes random affix content`() {
        val run = CoreActiveRun(UUID(1, 2), CoreOwnedMap(UUID(3, 4), 987654, 4))
        val seen = mutableSetOf<CoreCraftingCurrency>()
        var normal = 0L
        repeat(500) { index ->
            val elite = CoreCraftingCatalog.rollLoot(run, "enemy-$index", CoreLootKind.ELITE)
            assertEquals(elite, CoreCraftingCatalog.rollLoot(run, "enemy-$index", CoreLootKind.ELITE))
            assertEquals(2L, elite.values.sum())
            seen += elite.keys
            normal += CoreCraftingCatalog.rollLoot(run, "enemy-$index", CoreLootKind.NORMAL).values.sum()
        }
        assertTrue(normal in 120..230, "normal=$normal")
        assertEquals(CoreCraftingCurrency.entries.take(9).toSet(), seen)
        assertEquals(3L, CoreCraftingCatalog.rollLoot(run, "boss", CoreLootKind.BOSS).values.sum())
    }

    @Test fun `activity source is once regardless of kind and its reward prevents preparation refund`() {
        val f = Fixture(initial())
        val run = f.start()
        val currencyBefore = f.account.amount(CoreCraftingCurrency.RIFT)
        f.commit(CoreAction.ActivityReward(run, "rift-1", CoreActivityKind.RIFT))
        assertEquals(currencyBefore + 1, f.account.amount(CoreCraftingCurrency.RIFT))
        assertEquals(1, f.account.amount(CoreActivityKind.RIFT))
        assertEquals(3, f.account.amount(CoreResource.COMBAT_TOKEN))
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.ActivityReward(run, "rift-1", CoreActivityKind.RITUAL)).status)
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.AbortRun(run)).status)
        f.commit(CoreAction.FinishRun(run))
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.ActivityReward(run, "rift-2", CoreActivityKind.RIFT)).status)
    }

    @Test fun `three ordinary bosses guarantee entry to trial without circular dependency`() {
        val f = Fixture(initial())
        repeat(3) { val run = f.start(); f.commit(CoreAction.BossReward(run)); f.commit(CoreAction.FinishRun(run)) }
        assertEquals(3, f.account.amount(CoreActivityKind.TRIAL))
        val beforeMaps = f.account.maps.map { it.id }
        val trial = UUID.randomUUID()
        f.commit(CoreAction.StartTrial("trial", 1, trial))
        assertEquals(0, f.account.amount(CoreActivityKind.TRIAL))
        assertEquals("trial", f.account.activeRun!!.trialId)
        assertEquals(beforeMaps, f.account.maps.map { it.id })
        f.service.forget(f.player); f.service.open(f.player)
        assertEquals("trial", f.account.activeRun!!.trialId)
        f.commit(CoreAction.FinishRun(trial))
        assertEquals(0, f.account.amount(CoreActivityKind.TRIAL))
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.StartTrial("trial", 1, UUID.randomUUID())).status)
    }

    @Test fun `all dedicated bosses consume correct fragments refund only failed preparation and never unlock maps`() {
        for (kind in CoreActivityKind.entries) {
            val f = Fixture(initial().copy(fragments = mapOf(kind to 6L)))
            assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.StartTrial("unknown", 1, UUID.randomUUID())).status)
            assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.StartTrial(kind.bossId, 2, UUID.randomUUID())).status)
            val aborted = UUID.randomUUID()
            f.commit(CoreAction.StartTrial(kind.bossId, 1, aborted))
            assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.StartTrial(kind.bossId, 1, UUID.randomUUID())).status)
            f.commit(CoreAction.AbortRun(aborted))
            assertEquals(6, f.account.amount(kind))
            assertTrue(f.account.maps.isEmpty())
            assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.StartTrial(kind.bossId, 1, aborted)).status)
            val trial = UUID.randomUUID()
            f.commit(CoreAction.StartTrial(kind.bossId, 1, trial))
            assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.ActivityReward(trial, "no-event", kind)).status)
            f.commit(CoreAction.BossReward(trial))
            assertEquals(102, f.account.amount(kind.currency))
            assertEquals(101, f.account.amount(CoreCraftingCurrency.EXALTED))
            assertEquals(2, f.account.amount(CoreResource.BOSS_SIGIL))
            assertEquals(12, f.account.amount(CoreResource.COMBAT_TOKEN))
            assertEquals(1, f.account.unlockedMapTier)
            assertTrue(f.account.maps.isEmpty())
            assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.BossReward(trial)).status)
            assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.AbortRun(trial)).status)
            f.commit(CoreAction.FinishRun(trial))
            assertEquals(3, f.account.amount(kind))
        }
    }

    @Test fun `activity queue persists rewards and deduplicates before drain`() {
        val f = Fixture(initial())
        val run = f.start()
        val io = Executors.newSingleThreadScheduledExecutor()
        try {
            val queue = CoreRewardQueue(f.service, io)
            val action = CoreAction.ActivityReward(run, "altar", CoreActivityKind.RITUAL)
            val first = queue.submit(f.player, action)
            queue.drain(f.player).get(3, TimeUnit.SECONDS)
            assertTrue(first.get(3, TimeUnit.SECONDS).successful)
            assertEquals(CoreTransactionStatus.REPLAYED, queue.submit(f.player, action).get(3, TimeUnit.SECONDS).status)
            assertEquals(1, f.account.amount(CoreActivityKind.RITUAL))
            assertEquals(101, f.account.amount(CoreCraftingCurrency.RITUAL))
        } finally { io.shutdownNow() }
    }
}
