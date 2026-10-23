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

    @Test fun `retired Starweaver saves load as Mage and drop the Starweaver loadout`() {
        val star = CoreClassBuild(first = 2, second = 3, third = 4, fourth = 5, nodes = 1)
        val mage = CoreClassBuild(first = 4, second = 5, third = 6, fourth = 7, ultimate = 1)
        val warrior = CoreClassBuild(first = 1, second = 2, third = 3, fourth = 4)
        // HEALER rows stand in for the removed enum value and are renamed to the retired id on disk.
        val cases = listOf(
            Triple(CoreJourney(CoreClass.HEALER, true, 500, build = star, savedBuilds = mapOf(CoreClass.WARRIOR to warrior, CoreClass.MAGE to mage)),
                "journey\tHEALER\t" to "journey\tSTARWEAVER\t", CoreJourney(CoreClass.MAGE, true, 500, build = mage, savedBuilds = mapOf(CoreClass.WARRIOR to warrior))),
            Triple(CoreJourney(CoreClass.HEALER, true, 500, build = star, savedBuilds = mapOf(CoreClass.WARRIOR to warrior)),
                "journey\tHEALER\t" to "journey\tSTARWEAVER\t", CoreJourney(CoreClass.MAGE, true, 500, savedBuilds = mapOf(CoreClass.WARRIOR to warrior))),
            Triple(CoreJourney(CoreClass.WARRIOR, true, 500, build = warrior, savedBuilds = mapOf(CoreClass.HEALER to star, CoreClass.MAGE to mage)),
                "class-loadout\tHEALER\t" to "class-loadout\tSTARWEAVER\t", CoreJourney(CoreClass.WARRIOR, true, 500, build = warrior, savedBuilds = mapOf(CoreClass.MAGE to mage))))
        for ((saved, rename, expected) in cases) {
            val id = UUID.randomUUID()
            val encoded = savedVersion(CoreAccount(id, revision = 1, journey = saved), 11, emptyList()) {
                require(rename.first in it); it.replace(rename.first, rename.second)
            }
            val dir = Files.createTempDirectory("core-retired-starweaver-")
            val file = dir.resolve("$id.account"); Files.writeString(file, encoded)
            val loaded = assertIs<CoreAccountLoadResult.Ready>(CoreAccountService(CoreAccountRepository(dir)).open(id)).account
            assertEquals(expected, loaded.journey)
            assertEquals(encoded, Files.readString(file), "Loading alone never rewrites the save")
            assertFalse("STARWEAVER" in CoreAccountCodec.encode(loaded))
        }
    }

    private fun savedVersion(account: CoreAccount, version: Int, extraRows: List<String>, transform: (String) -> String = { it }): String {
        var body = if (version == 11) CoreAccountCodec.encode(account).substringBefore("checksum\t") else armorV10Body(account) + "\n"
        if (version == 7) body = body.lineSequence().filterNot {
            it.substringBefore('\t') in setOf("journey", "gear-base", "map-level", "class-build", "class-loadout")
        }.joinToString("\n").replaceFirst("\t10\t", "\t7\t")
        val rows = body.trimEnd('\n').split('\n')
        body = transform((rows.take(2) + extraRows + rows.drop(2)).joinToString("\n", postfix = "\n"))
        return body + "checksum\t${sha256(body)}\n"
    }

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
