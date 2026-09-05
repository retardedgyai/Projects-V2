package dev.projects.server.coreloop

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.*

class CoreEnhancementTest {
    private val weapon = CoreGearSlot.WEAPON
    private val armor = CoreGearSlot.ARMOR
    private fun rich(tier: Int = 1, weaponLevel: Int = 0, armorLevel: Int = 0, failures: Int = 0, xp: Long = 0) =
        CoreAccount(UUID.randomUUID(), revision = 1, weaponTier = tier, armorTier = tier,
            balances = CoreResource.entries.flatMap { resource -> (1..4).map { CoreMaterial(resource, it) to 50_000L } }.toMap(),
            weaponEnhancement = CoreEnhancementState(weaponLevel, failures), armorEnhancement = CoreEnhancementState(armorLevel), smithingXp = xp,
            craftingSeed = 9123456L, currencies = CoreCraftingCurrency.entries.associateWith { 10L })

    private class Fixture(initial: CoreAccount, repository: CoreAccountRepository = CoreAccountRepository(Files.createTempDirectory("projects-enhancement"))) {
        val player = initial.playerId
        val service = CoreAccountService(repository)
        init { assertEquals(CoreRepositorySave.Saved, repository.commit(0, initial)); assertIs<CoreAccountLoadResult.Ready>(service.open(player)) }
        val account get() = service.snapshot(player)!!
        fun perform(action: CoreAction) = service.transact(player, CoreOperation(UUID.randomUUID(), account.revision, action))
        fun commit(action: CoreAction) = perform(action).also { assertEquals(CoreTransactionStatus.COMMITTED, it.status, it.message) }.account!!
    }

    @Test fun `legacy success curve and +30 stat limits are explicit and monotonic`() {
        val expected = listOf(100.0, 100.0, 100.0, 100.0, 100.0, 95.0, 87.5, 80.0, 72.5, 65.0,
            55.0, 50.0, 45.0, 40.0, 35.0, 30.0, 26.0, 22.0, 18.0, 14.0, 10.0, 8.5, 7.0, 5.5, 4.0, 3.0, 2.5, 2.0, 1.5, 1.0)
        assertEquals(expected, (1..30).map(CoreEnhancementCatalog::baseChancePercent))
        assertEquals(2.2, CoreEnhancementCatalog.weaponDamageMultiplier(30))
        assertEquals(24.0, CoreEnhancementCatalog.weaponAttackSpeedPercent(30))
        assertEquals(1.6, CoreEnhancementCatalog.armorHealthMultiplier(30))
        for (level in 1..30) {
            assertTrue(CoreEnhancementCatalog.weaponDamageMultiplier(level) > CoreEnhancementCatalog.weaponDamageMultiplier(level - 1))
            assertTrue(CoreEnhancementCatalog.armorHealthMultiplier(level) > CoreEnhancementCatalog.armorHealthMultiplier(level - 1))
        }
        assertFailsWith<IllegalArgumentException> { CoreEnhancementState(31) }
        assertFailsWith<IllegalArgumentException> { CoreEnhancementState(30, 1) }
        assertFailsWith<IllegalArgumentException> { CoreEnhancementState(4, 1) }
        assertFailsWith<IllegalArgumentException> { CoreEnhancementState(5, 5) }
    }

    @Test fun `quotes charge stage materials regardless of starting equipment tier and catalyst combines correctly`() {
        for (equipmentTier in 1..4) for (target in 1..30) for (gear in CoreGearSlot.entries) {
            val account = rich(equipmentTier, target - 1, target - 1)
            val tier = (target + 7) / 8
            val normal = CoreEnhancementCatalog.quote(account, gear)
            val focused = CoreEnhancementCatalog.quote(account, gear, CoreEnhancementMode.FOCUSED)
            val unit = (target + 4L) / 5
            val primary = if (gear == weapon) CoreResource.INGOT else CoreResource.LEATHER
            val secondary = if (gear == weapon) CoreResource.BOARD else CoreResource.CLOTH
            assertEquals(mapOf(CoreMaterial(primary, tier) to unit, CoreMaterial(secondary, tier) to (unit + 1) / 2,
                CoreMaterial(CoreResource.STONE_BLOCK, tier) to unit), normal.recipe.costs)
            assertEquals(unit + 1, focused.recipe.costs[CoreMaterial(CoreResource.STONE_BLOCK, tier)])
            assertEquals((normal.recipe.costs[CoreMaterial(CoreResource.CLOTH, tier)] ?: 0) + 1,
                focused.recipe.costs[CoreMaterial(CoreResource.CLOTH, tier)])
            assertEquals(2, focused.recipe.costs[CoreMaterial(CoreResource.AFFIX_DUST)])
            assertTrue(focused.recipe.costs.keys.all { it.tier == tier || it == CoreMaterial(CoreResource.AFFIX_DUST) })
            assertNull(normal.blockedReason)
            assertFailsWith<UnsupportedOperationException> { (normal.recipe.costs as MutableMap).clear() }
        }
    }

    @Test fun `all attempts from +0 through +30 complete within pity cap without losing MOD or tier`() {
        for (gear in CoreGearSlot.entries) {
            val seed = CoreCraftingCatalog.craft(rich(4), gear, CoreCraftingCurrency.ALCHEMY, UUID.randomUUID())
            val originalMods = seed.equippedAffixes
            val f = Fixture(seed)
            var attempts = 0
            var repairs = 0
            while (CoreEnhancementCatalog.state(f.account, gear).level < 30) {
                if (CoreEconomy.broken(f.account, gear)) {
                    val donor = f.commit(CoreAction.Manufacture(gear, 4)).storedGear.last()
                    f.commit(CoreAction.Repair(gear, donor.identity.id))
                    repairs++
                }
                val before = CoreEnhancementCatalog.state(f.account, gear)
                val quote = CoreEnhancementCatalog.quote(f.account, gear)
                val balances = f.account.balances
                f.commit(CoreAction.EnhanceEquipment(gear))
                attempts++
                assertTrue(attempts <= 200, "pity should bound entire +0..30 path")
                val after = CoreEnhancementCatalog.state(f.account, gear)
                assertTrue(after.level == before.level || after.level == before.level + 1)
                if (before.level == after.level) assertEquals(before.failures + 1, after.failures) else assertEquals(0, after.failures)
                if (quote.guaranteed) assertEquals(before.level + 1, after.level)
                quote.recipe.costs.forEach { (material, amount) -> assertEquals(balances[material]!! - amount, f.account.amount(material)) }
                assertEquals(originalMods, f.account.equippedAffixes)
                assertEquals(4, f.account.weaponTier); assertEquals(4, f.account.armorTier)
            }
            assertEquals((attempts + repairs * 5).toLong().coerceAtMost(200), f.account.smithingXp)
            val snapshot = CoreAccountCodec.encode(f.account)
            assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.EnhanceEquipment(gear)).status)
            assertEquals(snapshot, CoreAccountCodec.encode(f.account))
        }
    }

    @Test fun `every pity boundary guarantees the next paid attempt and resets only successful equipment`() {
        for (level in 5..29) {
            val threshold = CoreEnhancementCatalog.pityThreshold(level + 1)
            val initial = rich(weaponLevel = level, armorLevel = 7, failures = threshold)
            val f = Fixture(initial)
            val quote = CoreEnhancementCatalog.quote(f.account, weapon)
            assertTrue(quote.guaranteed)
            assertEquals(100.0, quote.successChancePercent)
            f.commit(CoreAction.EnhanceEquipment(weapon))
            assertEquals(CoreEnhancementState(level + 1, 0), f.account.weaponEnhancement)
            assertEquals(initial.armorEnhancement, f.account.armorEnhancement)
        }
    }

    @Test fun `success failure pity and catalyst spending replay once after reconnect and deterministic retry`() {
        val f = Fixture(rich(weaponLevel = 29))
        val quote = CoreEnhancementCatalog.quote(f.account, weapon, CoreEnhancementMode.FOCUSED)
        val failedId = (0L..1000L).map { UUID(44, it) }.first {
            CoreEnhancementCatalog.resolve(f.account, weapon, quote, it, CoreEnhancementMode.FOCUSED).first.weaponEnhancement.level == 29
        }
        val operation = CoreOperation(failedId, f.account.revision, CoreAction.EnhanceEquipment(weapon, CoreEnhancementMode.FOCUSED))
        val before = f.account.balances
        assertEquals(CoreTransactionStatus.COMMITTED, f.service.transact(f.player, operation).status)
        assertEquals(CoreEnhancementState(29, 1), f.account.weaponEnhancement)
        assertEquals(1, f.account.smithingXp)
        quote.recipe.costs.forEach { (key, amount) -> assertEquals(before[key]!! - amount, f.account.amount(key)) }
        f.service.forget(f.player); f.service.open(f.player)
        assertEquals(CoreEnhancementState(29, 1), f.account.weaponEnhancement)
        val persisted = CoreAccountCodec.encode(f.account)
        assertEquals(CoreTransactionStatus.REPLAYED, f.service.transact(f.player, operation).status)
        assertEquals(persisted, CoreAccountCodec.encode(f.account))
        assertEquals(CoreTransactionStatus.CONFLICT, f.service.transact(f.player, operation.copy(action = CoreAction.EnhanceEquipment(armor))).status)
    }

    @Test fun `mastery comes from refine craft promotion and paid attempts but not exchange or rejected work`() {
        val f = Fixture(rich())
        f.commit(CoreAction.Refine(CoreResource.WOOD, 1, 12))
        assertEquals(12, f.account.smithingXp)
        f.commit(CoreAction.Craft(CoreResource.POTION, 7, 1))
        assertEquals(19, f.account.smithingXp)
        f.commit(CoreAction.EnhanceEquipment(weapon))
        assertEquals(20, f.account.smithingXp)
        assertEquals(1, CoreEnhancementCatalog.masteryRank(f.account.smithingXp))
        assertEquals(0, CoreEnhancementCatalog.masteryProgress(f.account.smithingXp))
        f.commit(CoreAction.UpgradeWeapon); f.commit(CoreAction.UpgradeArmor)
        assertEquals(30, f.account.smithingXp)
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.Exchange(CoreResource.ORE, 1, 5)).status)
        assertEquals(30, f.account.smithingXp)
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.Refine(CoreResource.INGOT, 1)).status)
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.Craft(CoreResource.POTION, 65, 1)).status)
        assertEquals(30, f.account.smithingXp)
        repeat(3) { f.commit(CoreAction.Refine(CoreResource.WOOD, 1, 64)) }
        assertEquals(200, f.account.smithingXp)
        assertEquals(10, CoreEnhancementCatalog.masteryRank(200))
        assertEquals(20, CoreEnhancementCatalog.masteryProgress(200))
    }

    @Test fun `mastery and catalyst percentage points are bounded and capped at one hundred`() {
        val high = rich(weaponLevel = 29, xp = 200)
        val base = CoreEnhancementCatalog.quote(high, weapon)
        val focused = CoreEnhancementCatalog.quote(high, weapon, CoreEnhancementMode.FOCUSED)
        assertEquals(1.0, base.baseChancePercent)
        assertEquals(10.0, base.masteryBonusPercent)
        assertEquals(11.0, base.successChancePercent)
        assertEquals(26.0, focused.successChancePercent)
        val early = CoreEnhancementCatalog.quote(rich(weaponLevel = 5, xp = 200), weapon, CoreEnhancementMode.FOCUSED)
        assertEquals(100.0, early.successChancePercent)
        assertTrue(early.guaranteed)
        assertFailsWith<IllegalArgumentException> { high.copy(smithingXp = 201) }
    }

    @Test fun `orb reroll scour and tier promotion preserve enhancement and pity independently`() {
        val f = Fixture(rich(weaponLevel = 20, armorLevel = 10, failures = 7))
        f.commit(CoreAction.CraftEquipment(weapon, CoreCraftingCurrency.ALCHEMY))
        f.commit(CoreAction.CraftEquipment(weapon, CoreCraftingCurrency.CHAOS))
        f.commit(CoreAction.CraftEquipment(weapon, CoreCraftingCurrency.SCOURING))
        f.commit(CoreAction.UpgradeWeapon)
        f.commit(CoreAction.UpgradeArmor)
        assertEquals(CoreEnhancementState(20, 7), f.account.weaponEnhancement)
        assertEquals(CoreEnhancementState(10), f.account.armorEnhancement)
        assertTrue(f.account.equippedAffixes.isEmpty())
        assertEquals(CoreGearRarity.NORMAL, f.account.weaponRarity)
        assertEquals(2, f.account.weaponTier)
    }

    @Test fun `failure to save returns exact old state including cost xp and pity`() {
        var failing = false
        val repository = CoreAccountRepository(Files.createTempDirectory("projects-enhancement-outage")) { from, to ->
            if (failing) error("simulated disk outage")
            Files.move(from, to, ATOMIC_MOVE, REPLACE_EXISTING)
        }
        val f = Fixture(rich(weaponLevel = 29, failures = 9, xp = 199), repository)
        val before = CoreAccountCodec.encode(f.account)
        val operation = CoreOperation(UUID.randomUUID(), 1, CoreAction.EnhanceEquipment(weapon, CoreEnhancementMode.FOCUSED))
        failing = true
        assertEquals(CoreTransactionStatus.SAVE_FAILED, f.service.transact(f.player, operation).status)
        assertEquals(before, CoreAccountCodec.encode(f.account))
        f.service.forget(f.player); f.service.open(f.player)
        assertEquals(before, CoreAccountCodec.encode(f.account))
        failing = false
        assertEquals(CoreTransactionStatus.COMMITTED, f.service.transact(f.player, operation).status)
        assertEquals(CoreEnhancementState(30), f.account.weaponEnhancement)
        assertEquals(200, f.account.smithingXp)
        assertEquals(CoreTransactionStatus.REPLAYED, f.service.transact(f.player, operation).status)
    }

    @Test fun `missing tier resources expedition max and stale view reject without costs or xp`() {
        val f = Fixture(rich(1, weaponLevel = 24).copy(balances = rich(1).balances.filterKeys { it.tier == 1 }))
        assertNotNull(CoreEnhancementCatalog.quote(f.account, weapon).blockedReason)
        val before = CoreAccountCodec.encode(f.account)
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.EnhanceEquipment(weapon)).status)
        assertEquals(before, CoreAccountCodec.encode(f.account))
        val active = rich().copy(activeRun = CoreActiveRun(UUID.randomUUID(), CoreOwnedMap(UUID.randomUUID(), 1, 1)))
        val away = Fixture(active)
        assertEquals(CoreTransactionStatus.REJECTED, away.perform(CoreAction.EnhanceEquipment(armor)).status)
        assertEquals(0, away.account.smithingXp)
        val max = Fixture(rich(weaponLevel = 30))
        assertEquals(CoreTransactionStatus.REJECTED, max.perform(CoreAction.EnhanceEquipment(weapon)).status)
        assertEquals(CoreTransactionStatus.STALE, max.service.transact(max.player,
            CoreOperation(UUID.randomUUID(), 0, CoreAction.EnhanceEquipment(armor))).status)
        assertEquals(0, max.account.smithingXp)
    }

    @Test fun `schema3 migration preserves all prior state and backs up exact bytes before first successful v4 write`() {
        val directory = Files.createTempDirectory("projects-enhancement-migration")
        val old = CoreCraftingCatalog.craft(rich(4).copy(
            activeRun = null, fragments = mapOf(CoreActivityKind.RIFT to 2L),
            maps = listOf(CoreOwnedMap(UUID.randomUUID(), 7654321, 4, listOf(CoreMapModifier("mining", "amount", 40)))),
            claimedSources = setOf("combat/legacy/source")), weapon, CoreCraftingCurrency.ALCHEMY, UUID.randomUUID())
        val encoded = asV3(old)
        val path = directory.resolve("${old.playerId}.account")
        val backup = directory.resolve("${old.playerId}.account.v3.bak")
        Files.writeString(path, encoded)
        var failure = true
        val service = CoreAccountService(CoreAccountRepository(directory) { from, to ->
            if (failure) error("migration save failure")
            Files.move(from, to, ATOMIC_MOVE, REPLACE_EXISTING)
        })
        val loaded = assertIs<CoreAccountLoadResult.Ready>(service.open(old.playerId)).account
        assertFalse(Files.exists(backup))
        assertEquals(CoreEnhancementState(), loaded.weaponEnhancement)
        assertEquals(CoreEnhancementState(), loaded.armorEnhancement)
        assertEquals(0, loaded.smithingXp)
        assertEquals(old.equippedAffixes, loaded.equippedAffixes)
        val operation = CoreOperation(UUID.randomUUID(), 1, CoreAction.EnhanceEquipment(weapon))
        assertEquals(CoreTransactionStatus.SAVE_FAILED, service.transact(old.playerId, operation).status)
        assertEquals(encoded, Files.readString(path)); assertEquals(encoded, Files.readString(backup))
        failure = false
        assertEquals(CoreTransactionStatus.COMMITTED, service.transact(old.playerId, operation).status)
        service.forget(old.playerId)
        val after = assertIs<CoreAccountLoadResult.Ready>(service.open(old.playerId)).account
        assertEquals(old.equippedAffixes, after.equippedAffixes)
        assertEquals(old.currencies, after.currencies); assertEquals(old.fragments, after.fragments)
        assertEquals(old.maps.map { it.toString() }, after.maps.map { it.toString() })
        assertEquals(old.craftingSeed, after.craftingSeed)
        assertEquals(old.weaponRarity, after.weaponRarity)
        assertEquals(old.weaponTier, after.weaponTier); assertEquals(old.armorTier, after.armorTier)
        assertTrue(after.claimedSources.containsAll(old.claimedSources))
        assertEquals(CoreEnhancementState(1), after.weaponEnhancement)
        assertEquals(1, after.smithingXp)
        assertEquals(encoded, Files.readString(backup))
        assertTrue(Files.readString(path).startsWith("PROJECTS_CORE_LOOP\t6\t"))
        assertEquals(CoreTransactionStatus.REPLAYED, service.transact(old.playerId, operation).status)
    }

    @Test fun `schema4 requires one valid enhancement row and rejects corruption without rewriting old data`() {
        val original = rich()
        val body = CoreAccountCodec.encode(original).substringBefore("checksum\t")
        val row = "enhancement\t0\t0\t0\t0\t0\n"
        for (bad in listOf(body.replace(row, ""), body.replace(row, row + row),
            body.replace(row, "enhancement\t31\t0\t0\t0\t0\n"), body.replace(row, "enhancement\t0\t1\t0\t0\t0\n"),
            body.replace(row, "enhancement\t0\t0\t0\t0\t201\n"))) {
            assertFailsWith<IllegalArgumentException> { CoreAccountCodec.decode(checksum(bad), original.playerId) }
        }
        val dir = Files.createTempDirectory("projects-enhancement-backup-conflict")
        val encoded = asV3(original)
        val path = dir.resolve("${original.playerId}.account")
        Files.writeString(path, encoded)
        Files.writeString(dir.resolve("${original.playerId}.account.v3.bak"), "unrelated backup")
        val service = CoreAccountService(CoreAccountRepository(dir))
        service.open(original.playerId)
        assertEquals(CoreTransactionStatus.SAVE_FAILED, service.transact(original.playerId,
            CoreOperation(UUID.randomUUID(), 1, CoreAction.EnhanceEquipment(weapon))).status)
        assertEquals(encoded, Files.readString(path))
        assertEquals(CoreEnhancementState(), service.snapshot(original.playerId)!!.weaponEnhancement)
    }

    private fun asV3(account: CoreAccount): String = checksum(CoreAccountCodec.encode(account).substringBefore("checksum\t")
        .lineSequence().filterNot { it.startsWith("enhancement\t") || it.startsWith("economy\t") || it.startsWith("identity\t") }.joinToString("\n")
        .replaceFirst("PROJECTS_CORE_LOOP\t6\t", "PROJECTS_CORE_LOOP\t3\t"))
    private fun checksum(body: String): String = body + "checksum\t" + MessageDigest.getInstance("SHA-256")
        .digest(body.toByteArray(UTF_8)).joinToString("") { "%02x".format(it) } + "\n"
}
