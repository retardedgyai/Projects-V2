package dev.projects.server.coreloop

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.*

class CoreAffixTest {
    private class Fixture(
        val initial: CoreAccount = CoreAccount(UUID.randomUUID(), revision = 1),
        val repository: CoreAccountRepository = CoreAccountRepository(Files.createTempDirectory("projects-affix")),
    ) {
        val player = initial.playerId
        val service = CoreAccountService(repository)
        init { assertEquals(CoreRepositorySave.Saved, repository.commit(0, initial)); assertIs<CoreAccountLoadResult.Ready>(service.open(player)) }
        val account get() = assertNotNull(service.snapshot(player))
        fun perform(action: CoreAction) = service.transact(player, CoreOperation(UUID.randomUUID(), account.revision, action))
        fun commit(action: CoreAction) = perform(action).also { assertEquals(CoreTransactionStatus.COMMITTED, it.status, it.message) }.account!!
        fun start(): UUID {
            val map = commit(CoreAction.ClaimMap(1, 12345)).maps.last()
            return UUID.randomUUID().also { commit(CoreAction.StartRun(map.id, it)) }
        }
    }
    private fun stone(id: String = "projects:force", tier: Int = 1, maximum: Boolean = false): CoreAffixStone {
        val range = CoreAffixCatalog.definitions.single { it.id == id }.range(tier)
        return CoreAffixStone(UUID.randomUUID(), id, tier, (if (maximum) range.last else range.first).toDouble())
    }

    @Test fun `catalog deterministic source rolls span all sixteen used stats and four ranks`() {
        assertEquals(16, CoreAffixCatalog.definitions.size)
        assertEquals(CoreAffixStat.entries.toSet(), CoreAffixCatalog.definitions.map { it.stat }.toSet())
        val seen = mutableSetOf<String>()
        for (tier in 1..4) {
            val run = CoreActiveRun(UUID(77, 88), CoreOwnedMap(UUID(99, 11), 1234567, tier))
            var normalDrops = 0
            repeat(200) { index ->
                val source = "enemy-$index"
                val first = CoreAffixCatalog.rollLoot(run, source, CoreLootKind.ELITE)
                assertEquals(first, CoreAffixCatalog.rollLoot(run, source, CoreLootKind.ELITE))
                assertEquals(2, first.size)
                assertEquals(2, first.map { it.id }.distinct().size)
                first.forEach { assertEquals(tier, it.tier); assertTrue(CoreAffixCatalog.valid(it)); seen += it.modId }
                normalDrops += CoreAffixCatalog.rollLoot(run, source, CoreLootKind.NORMAL).size
            }
            assertTrue(normalDrops in 30..110, "normal drops=$normalDrops")
            assertEquals(3, CoreAffixCatalog.rollLoot(run, "boss", CoreLootKind.BOSS).size)
            CoreAffixCatalog.definitions.forEach { definition ->
                assertEquals(0, CoreAffixCatalog.qualityPercent(stone(definition.id, tier)))
                assertEquals(100, CoreAffixCatalog.qualityPercent(stone(definition.id, tier, true)))
            }
        }
        assertEquals(CoreAffixCatalog.definitions.map { it.id }.toSet(), seen)
    }

    @Test fun `each known stat is emitted once in its own combat field and snapshots are immutable`() {
        CoreAffixCatalog.definitions.forEach { definition ->
            val value = stone(definition.id, 4, true)
            val gear = definition.allowedGear.first()
            val input = mutableListOf(CoreEquippedAffix(gear, 0, value))
            val account = CoreAccount(UUID.randomUUID(), weaponTier = 4, armorTier = 4, equippedAffixes = input)
            input.clear()
            val stats = CoreAffixCatalog.stats(account)
            val emitted = when (definition.stat) {
                CoreAffixStat.DAMAGE -> stats.damagePercent
                CoreAffixStat.ATTACK_SPEED -> stats.attackSpeedPercent
                CoreAffixStat.SKILL_DAMAGE -> stats.skillDamagePercent
                CoreAffixStat.MAX_MANA -> stats.maxManaFlat
                CoreAffixStat.MANA_REGEN -> stats.manaRegenPercent
                CoreAffixStat.COOLDOWN_REDUCTION -> stats.cooldownReductionPercent
                CoreAffixStat.HEALTH -> stats.healthFlat
                CoreAffixStat.MITIGATION -> stats.mitigationPercent
                CoreAffixStat.MOVE_SPEED -> stats.moveSpeedPercent
                CoreAffixStat.CRIT_CHANCE_INCREASED -> stats.critChanceIncreasedPercent
                CoreAffixStat.CRIT_MULTIPLIER -> stats.critMultiplierBonusPercent
                CoreAffixStat.NORMAL_DAMAGE -> stats.normalDamagePercent
                CoreAffixStat.CAST_REDUCTION -> stats.castReductionPercent
                CoreAffixStat.FIRE -> stats.fireFlat
                CoreAffixStat.ICE -> stats.iceFlat
                CoreAffixStat.LIGHTNING -> stats.lightningFlat
            }
            assertEquals(value.value, emitted, definition.id)
            assertEquals(1, account.equippedAffixes.size)
            assertFailsWith<UnsupportedOperationException> { (account.equippedAffixes as MutableList).clear() }
        }
        assertEquals(0.10, CoreAffixStats(critChanceIncreasedPercent = 100.0).criticalChance)
        assertEquals(1.8, CoreAffixStats(critMultiplierBonusPercent = 30.0).criticalMultiplier)
    }

    @Test fun `visible loot commits exact preview once and cannot also award old combat callback`() {
        val f = Fixture()
        val runId = f.start()
        val preview = CoreCraftingCatalog.rollLoot(f.account.activeRun!!, "elite-1", CoreLootKind.ELITE)
        val operation = CoreOperation(UUID.randomUUID(), f.account.revision, CoreAction.AffixLoot(runId, "elite-1", CoreLootKind.ELITE))
        assertTrue(f.service.transact(f.player, operation).successful)
        assertEquals(preview, f.account.currencies)
        assertEquals(3, f.account.amount(CoreResource.AFFIX_DUST))
        assertEquals(6, f.account.amount(CoreResource.COMBAT_TOKEN))
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.CombatReward(runId, "elite-1", 6)).status)
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.AffixLoot(runId, "elite-1", CoreLootKind.NORMAL)).status)
        f.service.forget(f.player); f.service.open(f.player)
        assertEquals(CoreTransactionStatus.REPLAYED, f.service.transact(f.player, operation).status)
        assertEquals(preview, f.account.currencies)
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.AbortRun(runId)).status)
        f.commit(CoreAction.CombatReward(runId, "legacy-enemy", 2))
        f.commit(CoreAction.AffixLoot(runId, "legacy-enemy", CoreLootKind.NORMAL))
        assertEquals(8, f.account.amount(CoreResource.COMBAT_TOKEN))
        assertEquals(4, f.account.amount(CoreResource.AFFIX_DUST))
    }

    @Test fun `boss bonus waits for progression and awards three currencies without duplicate boss tokens`() {
        val f = Fixture()
        val runId = f.start()
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.AffixLoot(runId, "boss", CoreLootKind.BOSS)).status)
        f.commit(CoreAction.BossReward(runId))
        f.commit(CoreAction.AffixLoot(runId, "boss", CoreLootKind.BOSS))
        assertEquals(3L, f.account.currencies.values.sum())
        assertEquals(12, f.account.amount(CoreResource.COMBAT_TOKEN))
        assertEquals(6, f.account.amount(CoreResource.AFFIX_DUST))
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.AffixLoot(runId, "other-boss-source", CoreLootKind.BOSS)).status)
    }

    @Test fun `full legacy bag does not affect currency rewards or delete existing stones`() {
        val original = List(CoreAffixCatalog.MAX_STONES) { stone() }
        val f = Fixture(CoreAccount(UUID.randomUUID(), revision = 1, affixStones = original))
        val run = f.start()
        f.commit(CoreAction.AffixLoot(run, "elite", CoreLootKind.ELITE))
        assertEquals(original, f.account.affixStones)
        assertEquals(3, f.account.amount(CoreResource.AFFIX_DUST))
        assertEquals(6, f.account.amount(CoreResource.COMBAT_TOKEN))
        f.commit(CoreAction.FinishRun(run))
        assertNull(f.account.activeRun)
    }

    @Test fun `full bag extraction and expedition crafting reject before spending`() {
        val original = List(CoreAffixCatalog.MAX_STONES) { stone() }
        val installed = stone("projects:haste")
        val f = Fixture(CoreAccount(UUID.randomUUID(), revision = 1,
            balances = mapOf(CoreMaterial(CoreResource.AFFIX_DUST) to 10), affixStones = original,
            equippedAffixes = listOf(CoreEquippedAffix(CoreGearSlot.WEAPON, 0, installed))))
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.ExtractAffix(CoreGearSlot.WEAPON, 0, installed.id)).status)
        assertEquals(10, f.account.amount(CoreResource.AFFIX_DUST))
        f.start()
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.SalvageAffix(original.first().id)).status)
        assertEquals(original, f.account.affixStones)
    }

    @Test fun `unknown definitions and future revisions round trip inert while malformed known rolls fail closed`() {
        val unknown = CoreAffixStone(UUID.randomUUID(), "future:effect", 1, 99.5, 2)
        val future = stone().copy(definitionRevision = 2)
        val account = CoreAccount(UUID.randomUUID(), revision = 1, affixStones = listOf(future),
            equippedAffixes = listOf(CoreEquippedAffix(CoreGearSlot.WEAPON, 0, unknown)))
        val encoded = CoreAccountCodec.encode(account)
        val decoded = CoreAccountCodec.decode(encoded, account.playerId)
        assertEquals(listOf(future), decoded.affixStones)
        assertEquals(unknown, decoded.equippedAffixes.single().stone)
        assertEquals(CoreAffixStats(), CoreAffixCatalog.stats(decoded))
        assertEquals(encoded, CoreAccountCodec.encode(decoded))
        assertFailsWith<IllegalArgumentException> { CoreAccount(UUID.randomUUID(), affixStones = listOf(stone().copy(value = 999.0))) }
        assertFailsWith<IllegalArgumentException> { stone().copy(value = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { CoreAccount(UUID.randomUUID(), affixStones = listOf(future, future)) }
    }

    @Test fun `v1 read does not write and first v3 mutation preserves all balances tiers maps receipts and exact backup`() {
        val directory = Files.createTempDirectory("projects-affix-v1-migration")
        val player = UUID.randomUUID()
        val receiptId = UUID.randomUUID()
        val original = CoreAccount(player, revision = 1, balances = mapOf(CoreMaterial(CoreResource.ORE, 4) to 77),
            weaponTier = 3, armorTier = 4, unlockedMapTier = 4, maps = listOf(CoreOwnedMap(UUID.randomUUID(), 111, 4)),
            receipts = mapOf(receiptId to CoreReceipt("a".repeat(64), 1, "旧記録")), claimedSources = setOf("combat/old-source"))
        val body = legacyBody(original, 1)
        val v1 = body + "checksum\t" + MessageDigest.getInstance("SHA-256").digest(body.toByteArray(UTF_8)).joinToString("") { "%02x".format(it) } + "\n"
        val path = directory.resolve("$player.account")
        val backup = directory.resolve("$player.account.v1.bak")
        Files.writeString(path, v1)
        val service = CoreAccountService(CoreAccountRepository(directory))
        val read = assertIs<CoreAccountLoadResult.Ready>(service.open(player)).account
        assertFalse(Files.exists(backup))
        assertEquals(v1, Files.readString(path))
        assertTrue(read.affixStones.isEmpty() && read.equippedAffixes.isEmpty())
        assertEquals(CoreTransactionStatus.COMMITTED, service.transact(player, CoreOperation(UUID.randomUUID(), 1, CoreAction.ClaimMap(1, 222))).status)
        assertEquals(v1, Files.readString(backup))
        assertTrue(Files.readString(path).startsWith("PROJECTS_CORE_LOOP\t6\t"))
        service.forget(player)
        val after = assertIs<CoreAccountLoadResult.Ready>(service.open(player)).account
        assertEquals(77, after.amount(CoreResource.ORE, 4))
        assertEquals(3, after.weaponTier); assertEquals(4, after.armorTier); assertEquals(4, after.unlockedMapTier)
        assertEquals(original.maps.single().id, after.maps.first().id)
        assertEquals(original.receipts[receiptId], after.receipts[receiptId])
        assertTrue("combat/old-source" in after.claimedSources)
    }

    @Test fun `failed v1 migration leaves original readable and exact backup survives later retry`() {
        val directory = Files.createTempDirectory("projects-affix-v1-failure")
        val player = UUID.randomUUID()
        val original = CoreAccount(player, revision = 1, weaponTier = 4, armorTier = 3,
            balances = mapOf(CoreMaterial(CoreResource.LEATHER, 3) to 12))
        val body = legacyBody(original, 1)
        val v1 = body + "checksum\t" + MessageDigest.getInstance("SHA-256").digest(body.toByteArray(UTF_8))
            .joinToString("") { "%02x".format(it) } + "\n"
        val path = directory.resolve("$player.account")
        Files.writeString(path, v1)
        var fail = true
        val service = CoreAccountService(CoreAccountRepository(directory) { from, to ->
            if (fail) error("simulated migration rename failure")
            Files.move(from, to, ATOMIC_MOVE, REPLACE_EXISTING)
        })
        service.open(player)
        val operation = CoreOperation(UUID.randomUUID(), 1, CoreAction.ClaimMap(1, 7))
        assertEquals(CoreTransactionStatus.SAVE_FAILED, service.transact(player, operation).status)
        assertEquals(v1, Files.readString(path))
        assertEquals(v1, Files.readString(directory.resolve("$player.account.v1.bak")))
        assertEquals(1, service.snapshot(player)!!.revision)
        fail = false
        assertEquals(CoreTransactionStatus.COMMITTED, service.transact(player, operation).status)
        assertEquals(12, service.snapshot(player)!!.amount(CoreResource.LEATHER, 3))
        assertEquals(4, service.snapshot(player)!!.weaponTier)
        assertEquals(v1, Files.readString(directory.resolve("$player.account.v1.bak")))
    }

    @Test fun `affix queue retries after disk recovery and deduplicates preview source before drain`() {
        val failing = AtomicBoolean(false)
        val repository = CoreAccountRepository(Files.createTempDirectory("projects-affix-queue")) { from, to ->
            if (failing.get()) error("disk outage")
            Files.move(from, to, ATOMIC_MOVE, REPLACE_EXISTING)
        }
        val f = Fixture(repository = repository)
        val run = f.start()
        val preview = CoreCraftingCatalog.rollLoot(f.account.activeRun!!, "elite-queue", CoreLootKind.ELITE)
        val executor = Executors.newSingleThreadScheduledExecutor()
        val attempted = CountDownLatch(1)
        val queue = CoreRewardQueue(f.service, executor, onRetry = { _, _ -> attempted.countDown() }, retryDelayMillis = 10)
        try {
            failing.set(true)
            val action = CoreAction.AffixLoot(run, "elite-queue", CoreLootKind.ELITE)
            val future = queue.submit(f.player, action)
            assertSame(future, queue.submit(f.player, action))
            val barrier = queue.drain(f.player)
            assertTrue(attempted.await(3, TimeUnit.SECONDS))
            assertFalse(barrier.isDone)
            assertTrue(f.account.affixStones.isEmpty())
            failing.set(false)
            queue.retryPending(f.player)
            assertTrue(future.get(3, TimeUnit.SECONDS).successful)
            barrier.get(3, TimeUnit.SECONDS)
            assertEquals(preview, f.account.currencies)
            assertEquals(CoreTransactionStatus.REPLAYED, queue.submit(f.player, action).get(3, TimeUnit.SECONDS).status)
            assertEquals(6, f.account.amount(CoreResource.COMBAT_TOKEN))
        } finally { executor.shutdownNow() }
    }

    @Test fun `old direct stone actions all reject and explicit exchange is atomic and once only`() {
        val original = stone(tier = 4)
        val equipped = stone("projects:haste")
        val f = Fixture(CoreAccount(UUID.randomUUID(), revision = 1, affixStones = listOf(original),
            equippedAffixes = listOf(CoreEquippedAffix(CoreGearSlot.WEAPON, 0, equipped))))
        listOf(CoreAction.ApplyAffix(CoreGearSlot.WEAPON, 0, original.id, equipped.id),
            CoreAction.ExtractAffix(CoreGearSlot.WEAPON, 0, equipped.id), CoreAction.RerollAffix(original.id), CoreAction.SalvageAffix(original.id))
            .forEach { assertEquals(CoreTransactionStatus.REJECTED, f.perform(it).status) }
        assertEquals(1, f.account.revision)
        assertEquals(listOf(original), f.account.affixStones)
        val operation = CoreOperation(UUID.randomUUID(), 1, CoreAction.ConvertLegacyStone(original.id))
        assertTrue(f.service.transact(f.player, operation).successful)
        assertTrue(f.account.affixStones.isEmpty())
        assertEquals(4, f.account.amount(CoreCraftingCurrency.ALTERATION))
        assertEquals(1, f.account.amount(CoreCraftingCurrency.ALCHEMY))
        assertEquals(equipped, f.account.equippedAffixes.single().stone)
        assertEquals(CoreTransactionStatus.REPLAYED, f.service.transact(f.player, operation).status)
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.ConvertLegacyStone(original.id)).status)
    }

    @Test fun `v2 migration preserves four prefix layout and exact backup even after failed replacement`() {
        val directory = Files.createTempDirectory("projects-affix-v2-migration")
        val player = UUID.randomUUID()
        val equipped = listOf("projects:force", "projects:technique", "projects:onslaught", "projects:flame")
            .mapIndexed { index, id -> CoreEquippedAffix(CoreGearSlot.WEAPON, index, stone(id, 4)) }
        val original = CoreAccount(player, revision = 1, weaponTier = 4, armorTier = 3, equippedAffixes = equipped,
            balances = mapOf(CoreMaterial(CoreResource.ORE, 4) to 77), affixStones = listOf(stone()))
        val body = legacyBody(original, 2)
        val old = body + "checksum\t" + MessageDigest.getInstance("SHA-256").digest(body.toByteArray(UTF_8)).joinToString("") { "%02x".format(it) } + "\n"
        val path = directory.resolve("$player.account")
        val backup = directory.resolve("$player.account.v2.bak")
        Files.writeString(path, old)
        var fail = true
        val service = CoreAccountService(CoreAccountRepository(directory) { from, to ->
            if (fail) error("migration rename outage")
            Files.move(from, to, ATOMIC_MOVE, REPLACE_EXISTING)
        })
        val loaded = assertIs<CoreAccountLoadResult.Ready>(service.open(player)).account
        assertFalse(Files.exists(backup))
        assertEquals(equipped, loaded.equippedAffixes)
        assertEquals(setOf(CoreGearSlot.WEAPON), loaded.legacyLayouts)
        assertEquals(CoreAffixCatalog.stats(original), CoreAffixCatalog.stats(loaded))
        val operation = CoreOperation(UUID.randomUUID(), 1, CoreAction.ConvertLegacyStone(original.affixStones.single().id))
        assertEquals(CoreTransactionStatus.SAVE_FAILED, service.transact(player, operation).status)
        assertEquals(old, Files.readString(path))
        assertEquals(old, Files.readString(backup))
        assertTrue(service.snapshot(player)!!.currencies.isEmpty())
        assertEquals(original.affixStones, service.snapshot(player)!!.affixStones)
        fail = false
        assertEquals(CoreTransactionStatus.COMMITTED, service.transact(player, operation).status)
        service.forget(player)
        val after = assertIs<CoreAccountLoadResult.Ready>(service.open(player)).account
        assertEquals(equipped, after.equippedAffixes)
        assertEquals(77, after.amount(CoreResource.ORE, 4))
        assertEquals(4, after.weaponTier); assertEquals(3, after.armorTier)
        assertEquals(old, Files.readString(backup))
        assertEquals(CoreTransactionStatus.REPLAYED, service.transact(player, operation).status)
    }

    private fun legacyBody(account: CoreAccount, version: Int): String =
        CoreAccountCodec.encode(account).substringBefore("checksum\t").lineSequence()
            .filterNot { it.startsWith("crafting\t") || it.startsWith("currency\t") || it.startsWith("fragment\t") || it.startsWith("legacy-layout\t") || it.startsWith("enhancement\t") || it.startsWith("economy\t") || it.startsWith("identity\t") }
            .joinToString("\n").replaceFirst("PROJECTS_CORE_LOOP\t6\t", "PROJECTS_CORE_LOOP\t$version\t")
}
