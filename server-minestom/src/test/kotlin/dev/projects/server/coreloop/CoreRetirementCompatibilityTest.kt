package dev.projects.server.coreloop

import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.*

/** Genuine legacy rows exercise valueOf, version upgrades, durable cleanup and replay protection. */
class CoreRetirementCompatibilityTest {
    @Test fun `v7 v10 and v11 keep astral balances records checkpoints and original backups`() {
        for (version in listOf(7, 10, 11)) {
            val id = UUID.randomUUID(); val run = UUID.randomUUID()
            val original = CoreAccount(id, revision = 1, currencies = mapOf(CoreCraftingCurrency.ASTRAL to 123L),
                dungeonRecords = mapOf(1 to 2), activeRun = CoreActiveRun(run, CoreOwnedMap(UUID.randomUUID(), 91, 1),
                    dungeon = CoreDungeonEntry(2, 12, 4, 3)))
            val encoded = savedVersion(original, version)
            val dir = Files.createTempDirectory("core-retirement-v$version-")
            val file = dir.resolve("$id.account"); Files.writeString(file, encoded)
            val service = CoreAccountService(CoreAccountRepository(dir))
            val loaded = assertIs<CoreAccountLoadResult.Ready>(service.open(id)).account
            assertEquals(123, loaded.amount(CoreCraftingCurrency.ASTRAL))
            assertEquals(original.dungeonRecords, loaded.dungeonRecords)
            assertEquals(original.activeRun!!.dungeon, loaded.activeRun!!.dungeon)
            for (action in listOf(CoreAction.StartDungeon(UUID.randomUUID(), 1, 3, 9),
                CoreAction.CraftEquipment(CoreGearSlot.WEAPON, CoreCraftingCurrency.ASTRAL))) {
                assertEquals(CoreTransactionStatus.REJECTED,
                    service.transact(id, CoreOperation(UUID.randomUUID(), loaded.revision, action)).status)
                assertEquals(encoded, Files.readString(file))
            }
            val finish = CoreOperation(UUID.randomUUID(), loaded.revision, CoreAction.FinishRun(run))
            assertEquals(CoreTransactionStatus.COMMITTED, service.transact(id, finish).status)
            service.forget(id)
            val returned = assertIs<CoreAccountLoadResult.Ready>(service.open(id)).account
            assertNull(returned.activeRun); assertEquals(123, returned.amount(CoreCraftingCurrency.ASTRAL))
            assertEquals(original.dungeonRecords, returned.dungeonRecords)
            assertEquals(CoreTransactionStatus.REPLAYED, service.transact(id, finish).status)
            assertEquals(returned, service.snapshot(id))
            if (version < 11) assertEquals(encoded, Files.readString(dir.resolve("$id.account.v$version.bak")))
        }
    }

    @Test fun `legacy prepared dungeon abort keeps existing rewards and balance without refund or conversion`() {
        val id = UUID.randomUUID(); val run = UUID.randomUUID()
        val original = CoreAccount(id, revision = 1, currencies = mapOf(CoreCraftingCurrency.ASTRAL to 9L),
            balances = mapOf(CoreMaterial(CoreResource.COMBAT_TOKEN) to 8L), silver = 123L,
            activeRun = CoreActiveRun(run, CoreOwnedMap(UUID.randomUUID(), 19, 1), dungeon = CoreDungeonEntry(0, 12, 4)))
        val repo = CoreAccountRepository(Files.createTempDirectory("core-retirement-abort-"))
        assertEquals(CoreRepositorySave.Saved, repo.commit(0, original))
        val service = CoreAccountService(repo); service.open(id)
        val operation = CoreOperation(UUID.randomUUID(), 1, CoreAction.AbortRun(run))
        val result = service.transact(id, operation)
        assertEquals(CoreTransactionStatus.COMMITTED, result.status)
        val returned = assertNotNull(result.account)
        assertNull(returned.activeRun); assertEquals(original.currencies, returned.currencies)
        assertEquals(original.balances, returned.balances); assertEquals(original.silver, returned.silver)
        assertEquals(CoreTransactionStatus.REPLAYED, service.transact(id, operation).status)
    }

    @Test fun `old astral and start receipts replay without executing the retired operation again`() {
        for (action in listOf(CoreAction.CraftEquipment(CoreGearSlot.WEAPON, CoreCraftingCurrency.ASTRAL),
            CoreAction.StartDungeon(UUID.randomUUID(), 1, 0, 99))) {
            val id = UUID.randomUUID(); val request = UUID.randomUUID()
            val fingerprint = sha256("0|$action")
            val original = CoreAccount(id, revision = 1, currencies = mapOf(CoreCraftingCurrency.ASTRAL to 4L),
                receipts = mapOf(request to CoreReceipt(fingerprint, 1, "旧操作は保存済みです")))
            val repo = CoreAccountRepository(Files.createTempDirectory("core-retirement-replay-"))
            assertEquals(CoreRepositorySave.Saved, repo.commit(0, original))
            val service = CoreAccountService(repo); service.open(id)
            val before = CoreAccountCodec.encode(service.snapshot(id)!!)
            assertEquals(CoreTransactionStatus.REPLAYED, service.transact(id, CoreOperation(request, 0, action)).status)
            assertEquals(before, CoreAccountCodec.encode(service.snapshot(id)!!))
            assertEquals(CoreTransactionStatus.REJECTED,
                service.transact(id, CoreOperation(UUID.randomUUID(), 1, action)).status)
            assertEquals(before, CoreAccountCodec.encode(service.snapshot(id)!!))
        }
    }

    private fun savedVersion(account: CoreAccount, version: Int): String {
        if (version == 11) return CoreAccountCodec.encode(account)
        var body = armorV10Body(account)
        if (version == 7) body = body.lineSequence().filterNot {
            it.substringBefore('\t') in setOf("journey", "gear-base", "map-level", "class-build", "class-loadout")
        }.joinToString("\n").replaceFirst("\t10\t", "\t7\t")
        return body + "checksum\t${sha256(body)}\n"
    }

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
