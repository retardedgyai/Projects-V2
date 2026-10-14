package dev.projects.server.coreloop

import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.*

/** Genuine legacy rows exercise valueOf, version upgrades, durable cleanup and replay protection. */
class CoreRetirementCompatibilityTest {
    @Test fun `v7 v10 and v11 saves with retired dungeon rows load keep astral and drop dungeon rows on rewrite`() {
        for (version in listOf(7, 10, 11)) {
            val id = UUID.randomUUID(); val run = UUID.randomUUID()
            val original = CoreAccount(id, revision = 1, currencies = mapOf(CoreCraftingCurrency.ASTRAL to 123L),
                activeRun = CoreActiveRun(run, CoreOwnedMap(UUID.randomUUID(), 91, 1)))
            val encoded = savedVersion(original, version, listOf("dungeon-record\t1\t2", "dungeon-run\t2\t12\t4\t3"))
            val dir = Files.createTempDirectory("core-retirement-v$version-")
            val file = dir.resolve("$id.account"); Files.writeString(file, encoded)
            val service = CoreAccountService(CoreAccountRepository(dir))
            val loaded = assertIs<CoreAccountLoadResult.Ready>(service.open(id)).account
            assertEquals(123, loaded.amount(CoreCraftingCurrency.ASTRAL))
            assertEquals(run, loaded.activeRun?.id)
            val astral = CoreAction.CraftEquipment(CoreGearSlot.WEAPON, CoreCraftingCurrency.ASTRAL)
            assertEquals(CoreTransactionStatus.REJECTED, service.transact(id, CoreOperation(UUID.randomUUID(), loaded.revision, astral)).status)
            assertEquals(encoded, Files.readString(file))
            val finish = CoreOperation(UUID.randomUUID(), loaded.revision, CoreAction.FinishRun(run))
            assertEquals(CoreTransactionStatus.COMMITTED, service.transact(id, finish).status)
            assertFalse(Files.readString(file).lineSequence().any { it.startsWith("dungeon-") })
            service.forget(id)
            val returned = assertIs<CoreAccountLoadResult.Ready>(service.open(id)).account
            assertNull(returned.activeRun); assertEquals(123, returned.amount(CoreCraftingCurrency.ASTRAL))
            assertEquals(CoreTransactionStatus.REPLAYED, service.transact(id, finish).status)
            assertEquals(returned, service.snapshot(id))
            if (version < 11) assertEquals(encoded, Files.readString(dir.resolve("$id.account.v$version.bak")))
        }
    }

    @Test fun `old astral receipts replay without executing the retired operation again`() {
        val action = CoreAction.CraftEquipment(CoreGearSlot.WEAPON, CoreCraftingCurrency.ASTRAL)
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

    private fun savedVersion(account: CoreAccount, version: Int, extraRows: List<String>): String {
        var body = if (version == 11) CoreAccountCodec.encode(account).substringBefore("checksum\t") else armorV10Body(account) + "\n"
        if (version == 7) body = body.lineSequence().filterNot {
            it.substringBefore('\t') in setOf("journey", "gear-base", "map-level", "class-build", "class-loadout")
        }.joinToString("\n").replaceFirst("\t10\t", "\t7\t")
        val rows = body.trimEnd('\n').split('\n')
        body = (rows.take(2) + extraRows + rows.drop(2)).joinToString("\n", postfix = "\n")
        return body + "checksum\t${sha256(body)}\n"
    }

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
