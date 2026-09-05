package dev.projects.server.coreloop

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlin.test.*

class CoreBreakRepairTest {
    private val weapon = CoreGearSlot.WEAPON
    private fun rich(tier: Int = 3, level: Int = 20) = CoreAccount(UUID.randomUUID(), revision = 1,
        weaponTier = tier, armorTier = tier, craftingSeed = 23456,
        weaponEnhancement = CoreEnhancementState(level), armorEnhancement = CoreEnhancementState(level),
        balances = CoreLoopCatalog.refined.values.flatMap { r -> (1..4).map { CoreMaterial(r, it) to 999L } }.toMap(),
        currencies = CoreCraftingCurrency.entries.associateWith { 100L })

    private fun donor(slot: CoreGearSlot = weapon, tier: Int = 3, level: Int = 0, broken: Boolean = false,
        bound: Boolean = false, mods: Boolean = false): CoreStoredGear {
        val a = rich(tier, level)
        val rolled = if (mods) CoreCraftingCatalog.craft(a, slot, CoreCraftingCurrency.ALCHEMY, UUID.randomUUID()) else a
        return CoreStoredGear(CoreGearIdentity(UUID.randomUUID(), a.playerId, bound), slot, tier,
            CoreAffixCatalog.rarity(rolled, slot), CoreEnhancementCatalog.state(rolled, slot),
            rolled.equippedAffixes.filter { it.gear == slot }, broken = broken)
    }
    private class Fixture(initial: CoreAccount) {
        val dir = Files.createTempDirectory("break-repair-")
        var fail = false
        val repo = CoreAccountRepository(dir) { source, target ->
            if (fail) error("disk unavailable")
            Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING); Unit
        }
        val service = CoreAccountService(repo)
        val id = initial.playerId
        val a get() = service.snapshot(id)!!
        init {
            assertEquals(CoreRepositorySave.Saved, repo.commit(0, initial))
            assertIs<CoreAccountLoadResult.Ready>(service.open(id))
        }
        fun op(action: CoreAction) = CoreOperation(UUID.randomUUID(), a.revision, action)
        fun send(action: CoreAction) = service.transact(id, op(action))
    }

    @Test fun `legacy conditional break curve starts at current plus15 and total risk includes failure probability`() {
        for (level in 0..14) assertEquals(0.0, CoreEnhancementCatalog.breakOnFailurePercent(level))
        assertEquals(5.0, CoreEnhancementCatalog.breakOnFailurePercent(15))
        assertEquals(47.0, CoreEnhancementCatalog.breakOnFailurePercent(29))
        assertEquals(50.0, CoreEnhancementCatalog.breakOnFailurePercent(30))
        val quote = CoreEnhancementCatalog.quote(rich(level = 15), weapon)
        assertEquals(30.0, quote.successChancePercent)
        assertEquals(5.0, quote.breakOnFailurePercent)
        assertEquals(3.5, quote.breakPerAttemptPercent)
        val late = CoreEnhancementCatalog.quote(rich(level = 29), weapon)
        assertEquals(46.53, late.breakPerAttemptPercent, .000001)
        val focused = CoreEnhancementCatalog.quote(rich(level = 29), weapon, CoreEnhancementMode.FOCUSED)
        assertEquals(39.48, focused.breakPerAttemptPercent, .000001)
        for (level in 15..29) {
            val a = rich(level = level).copy(weaponEnhancement = CoreEnhancementState(level, CoreEnhancementCatalog.pityThreshold(level + 1)))
            val q = CoreEnhancementCatalog.quote(a, weapon)
            assertEquals(0.0, q.breakPerAttemptPercent)
            repeat(20) { assertFalse(CoreEnhancementCatalog.resolve(a, weapon, q, UUID(1, it.toLong()), CoreEnhancementMode.STANDARD).first.weaponBroken) }
        }
    }

    @Test fun `success never breaks and failures can be intact or broken without losing levels`() {
        for (slot in CoreGearSlot.entries) {
            val a = rich(level = 15)
            val q = CoreEnhancementCatalog.quote(a, slot)
            val outcomes = (0L..999).map { CoreEnhancementCatalog.resolve(a, slot, q, UUID(90, it), CoreEnhancementMode.STANDARD).first }
            assertTrue(outcomes.any { CoreEconomy.broken(it, slot) })
            assertTrue(outcomes.any { !CoreEconomy.broken(it, slot) && CoreEnhancementCatalog.state(it, slot).level == 15 })
            assertTrue(outcomes.any { CoreEnhancementCatalog.state(it, slot).level == 16 })
            outcomes.forEach { after ->
                if (CoreEnhancementCatalog.state(after, slot).level == 16) assertFalse(CoreEconomy.broken(after, slot))
                else assertEquals(CoreEnhancementState(15, 1), CoreEnhancementCatalog.state(after, slot))
                assertEquals(1, after.smithingXp)
            }
        }
        val safe = rich(level = 14)
        val q = CoreEnhancementCatalog.quote(safe, weapon)
        repeat(100) { assertFalse(CoreEnhancementCatalog.resolve(safe, weapon, q, UUID(9, it.toLong()), CoreEnhancementMode.STANDARD).first.weaponBroken) }
    }

    @Test fun `same tier family plus0 donor may have MODs and rarity and repair changes only target break flag`() {
        for (tier in 1..4) for (slot in CoreGearSlot.entries) {
            val input = donor(slot, tier, mods = true)
            val initial = CoreCraftingCatalog.craft(rich(tier), slot, CoreCraftingCurrency.ALCHEMY, UUID.randomUUID())
                .copy(weaponBroken = true, armorBroken = true, storedGear = listOf(input),
                    weaponEnhancement = CoreEnhancementState(20, 7), armorEnhancement = CoreEnhancementState(20, 8))
            assertTrue(input.affixes.isNotEmpty())
            assertTrue(CoreEconomy.repairInput(initial, slot, input))
            val f = Fixture(initial)
            val op = f.op(CoreAction.Repair(slot, input.identity.id))
            assertEquals(CoreTransactionStatus.COMMITTED, f.service.transact(f.id, op).status)
            val expected = initial.copy(storedGear = emptyList(), weaponBroken = slot != weapon, armorBroken = slot == weapon)
            assertEquals(CoreAccountCodec.encode(expected), CoreAccountCodec.encode(f.a.copy(revision = initial.revision, receipts = initial.receipts)))
            assertFalse(CoreEconomy.broken(f.a, slot))
            f.service.forget(f.id); f.service.open(f.id)
            assertEquals(CoreTransactionStatus.REPLAYED, f.service.transact(f.id, op).status)
            assertTrue(f.a.storedGear.isEmpty())
        }
    }

    @Test fun `wrong tier family enhanced broken bound and listed donors cannot be consumed`() {
        val candidates = listOf(donor(tier = 2), donor(CoreGearSlot.ARMOR), donor(level = 1),
            donor(broken = true), donor(bound = true), donor())
        candidates.forEachIndexed { index, item ->
            val initial = rich().copy(weaponBroken = true, storedGear = listOf(item),
                offers = if (index == 5) listOf(CoreMarketOffer(UUID.randomUUID(), 50, gearId = item.identity.id)) else emptyList())
            val f = Fixture(initial)
            assertFalse(CoreEconomy.repairInput(initial, weapon, item))
            assertEquals(CoreTransactionStatus.REJECTED, f.send(CoreAction.Repair(weapon, item.identity.id)).status)
            assertEquals(CoreAccountCodec.encode(initial), CoreAccountCodec.encode(f.a))
        }
        val input = donor()
        val healthy = Fixture(rich().copy(storedGear = listOf(input)))
        assertEquals(CoreTransactionStatus.REJECTED, healthy.send(CoreAction.Repair(weapon, input.identity.id)).status)
        assertEquals(1, healthy.a.storedGear.size)
    }

    @Test fun `repair and enhancement break commit atomically across outage reconnect and retry`() {
        val input = donor()
        val f = Fixture(rich(level = 29).copy(storedGear = listOf(input)))
        val q = CoreEnhancementCatalog.quote(f.a, weapon)
        val request = (0L..999).map { UUID(82, it) }.first {
            CoreEnhancementCatalog.resolve(f.a, weapon, q, it, CoreEnhancementMode.STANDARD).first.weaponBroken
        }
        val op = CoreOperation(request, f.a.revision, CoreAction.EnhanceEquipment(weapon))
        val before = CoreAccountCodec.encode(f.a)
        f.fail = true
        assertEquals(CoreTransactionStatus.SAVE_FAILED, f.service.transact(f.id, op).status)
        assertEquals(before, CoreAccountCodec.encode(f.a))
        f.fail = false
        assertEquals(CoreTransactionStatus.COMMITTED, f.service.transact(f.id, op).status)
        assertTrue(f.a.weaponBroken)
        assertEquals(CoreEnhancementState(29, 1), f.a.weaponEnhancement)
        assertEquals(CoreTransactionStatus.REPLAYED, f.service.transact(f.id, op).status)
        val repair = f.op(CoreAction.Repair(weapon, input.identity.id))
        val broken = CoreAccountCodec.encode(f.a)
        f.fail = true
        assertEquals(CoreTransactionStatus.SAVE_FAILED, f.service.transact(f.id, repair).status)
        assertEquals(broken, CoreAccountCodec.encode(f.a))
        f.service.forget(f.id); f.service.open(f.id)
        assertEquals(broken, CoreAccountCodec.encode(f.a))
        f.fail = false
        assertEquals(CoreTransactionStatus.COMMITTED, f.service.transact(f.id, repair).status)
        assertFalse(f.a.weaponBroken); assertTrue(f.a.storedGear.isEmpty())
        assertEquals(CoreEnhancementState(29, 1), f.a.weaponEnhancement)
        assertEquals(CoreTransactionStatus.REPLAYED, f.service.transact(f.id, repair).status)
    }

    @Test fun `broken gear cannot enhance reroll or promote but healthy other slot can`() {
        val f = Fixture(rich(tier = 2).copy(weaponBroken = true))
        val before = CoreAccountCodec.encode(f.a)
        for (action in listOf(CoreAction.EnhanceEquipment(weapon), CoreAction.UpgradeWeapon,
            CoreAction.CraftEquipment(weapon, CoreCraftingCurrency.ALCHEMY))) {
            assertEquals(CoreTransactionStatus.REJECTED, f.send(action).status)
            assertEquals(before, CoreAccountCodec.encode(f.a))
        }
        assertNull(CoreEnhancementCatalog.quote(f.a, CoreGearSlot.ARMOR).blockedReason)
        assertNull(CoreCraftingCatalog.canUse(f.a, CoreGearSlot.ARMOR, CoreCraftingCurrency.ALCHEMY))
    }

    @Test fun `ending an expedition does not create wear or break gear`() {
        val run = CoreActiveRun(UUID.randomUUID(), CoreOwnedMap(UUID.randomUUID(), 9, 3))
        val initial = rich().copy(activeRun = run)
        val f = Fixture(initial)
        assertEquals(CoreTransactionStatus.COMMITTED, f.send(CoreAction.FinishRun(run.id)).status)
        assertFalse(f.a.weaponBroken); assertFalse(f.a.armorBroken)
        assertEquals(CoreAccountCodec.encode(initial.copy(activeRun = null)),
            CoreAccountCodec.encode(f.a.copy(revision = initial.revision, receipts = initial.receipts)))
    }

    private fun checksum(body: String) = body + "checksum\t" + MessageDigest.getInstance("SHA-256")
        .digest(body.toByteArray(UTF_8)).joinToString("") { "%02x".format(it) } + "\n"
    private fun v5(a: CoreAccount, wear: String = "0"): String {
        val body = CoreAccountCodec.encode(a).substringBefore("checksum\t").lineSequence().filterNot(::coreExpansionRow).map { row ->
            val parts = row.split('\t').toMutableList()
            when (parts[0]) {
                "PROJECTS_CORE_LOOP" -> parts[1] = "5"
                "economy" -> { parts[4] = wear; parts[5] = wear }
                "stored-gear" -> parts[10] = wear
            }
            parts.joinToString("\t")
        }.joinToString("\n")
        return checksum(body)
    }

    @Test fun `v5 zero wear migrates healthy without changing items and exact old bytes are backed up`() {
        val input = donor(mods = true)
        val original = CoreCraftingCatalog.craft(rich(), weapon, CoreCraftingCurrency.ALCHEMY, UUID.randomUUID()).copy(storedGear = listOf(input))
        for (wear in listOf("0", "10", "100")) {
            val dir = Files.createTempDirectory("repair-v5-")
            val file = dir.resolve("${original.playerId}.account")
            val backup = dir.resolve("${original.playerId}.account.v5.bak")
            val old = v5(original, wear)
            Files.writeString(file, old)
            val service = CoreAccountService(CoreAccountRepository(dir))
            val loaded = assertIs<CoreAccountLoadResult.Ready>(service.open(original.playerId)).account
            assertEquals(CoreAccountCodec.encode(original), CoreAccountCodec.encode(loaded))
            assertFalse(Files.exists(backup)); assertEquals(old, Files.readString(file))
            assertTrue(service.transact(original.playerId, CoreOperation(UUID.randomUUID(), loaded.revision, CoreAction.ClaimMap(1, 8))).successful)
            assertEquals(old, Files.readString(backup))
            assertTrue(Files.readString(file).startsWith("PROJECTS_CORE_LOOP\t7\t"))
        }
        assertFailsWith<IllegalArgumentException> { CoreAccountCodec.decode(v5(original, "-1"), original.playerId) }
        assertFailsWith<IllegalArgumentException> { CoreAccountCodec.decode(v5(original, "101"), original.playerId) }
        val malformed = CoreAccountCodec.encode(original).substringBefore("checksum\t").replace("\tfalse\tfalse\n", "\t0\tfalse\n")
        assertFailsWith<IllegalArgumentException> { CoreAccountCodec.decode(checksum(malformed), original.playerId) }
    }

    @Test fun `a pending v5 market transfer recovers under v6 and preserves both legacy backups`() {
        val dir = Files.createTempDirectory("repair-v5-trade-")
        val one = rich().copy(silver = 100)
        val two = rich().copy(silver = 20)
        listOf(one, two).forEach { Files.writeString(dir.resolve("${it.playerId}.account"), v5(it)) }
        val targets = listOf(one.copy(revision = 2, silver = 60), two.copy(revision = 2, silver = 58))
        Files.writeString(dir.resolve("market.pending"), targets.joinToString("\n", postfix = "\n") {
            "${it.playerId}\t" + Base64.getEncoder().encodeToString(v5(it).toByteArray(UTF_8))
        })
        val repo = CoreAccountRepository(dir)
        targets.forEach { expected ->
            assertEquals(CoreAccountCodec.encode(expected), CoreAccountCodec.encode(assertIs<CoreRepositoryLoad.Loaded>(repo.load(expected.playerId)).account))
        }
        assertFalse(Files.exists(dir.resolve("market.pending")))
        listOf(one, two).forEach { assertEquals(v5(it), Files.readString(dir.resolve("${it.playerId}.account.v5.bak"))) }
    }
}
