package dev.projects.server.coreloop

import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.*

internal fun coreExpansionRow(row: String) = row.substringBefore('\t') in setOf("survey", "profession", "buy-order", "dungeon-record", "gear-quality", "dungeon-run")

class CoreMmoExpansionTest {
    private class Fixture {
        val dir = Files.createTempDirectory("core-mmo-")
        val repo = CoreAccountRepository(dir)
        val service = CoreAccountService(repo)
        fun create(silver: Long = 1000, survey: Long = 0): UUID {
            val id = UUID.randomUUID()
            val balances = CoreLoopCatalog.refined.flatMap { (raw, refined) -> (1..4).flatMap { t -> listOf(CoreMaterial(raw, t) to 1000L, CoreMaterial(refined, t) to 1000L) } }.toMap()
            assertEquals(CoreRepositorySave.Saved, repo.commit(0, CoreAccount(id, revision = 1, silver = silver, balances = balances, surveyPoints = survey, unlockedMapTier = 4)))
            assertIs<CoreAccountLoadResult.Ready>(service.open(id)); return id
        }
        fun a(id: UUID) = service.snapshot(id)!!
        fun send(id: UUID, action: CoreAction) = service.transact(id, CoreOperation(UUID.randomUUID(), a(id).revision, action))
        fun commit(id: UUID, action: CoreAction) = send(id, action).also { assertEquals(CoreTransactionStatus.COMMITTED, it.status, it.message) }.account!!
    }

    @Test fun `buy order partial material fills cancel funded remainder and reject duplicate or self fill`() {
        val f = Fixture(); val buyer = f.create(); val seller = f.create(0)
        val order = f.commit(buyer, CoreAction.PlaceBuyOrder(CoreResource.ORE, null, 3, 20, 10)).buyOrders.single()
        assertEquals(800, f.a(buyer).silver)
        assertEquals(CoreTransactionStatus.REJECTED, f.send(buyer, CoreAction.FillBuyOrder(buyer, order.id, 10)).status)
        f.service.forget(buyer)
        val request = CoreOperation(UUID.randomUUID(), f.a(seller).revision, CoreAction.FillBuyOrder(buyer, order.id, 10, 7))
        assertTrue(f.service.transact(seller, request).successful)
        assertEquals(CoreTransactionStatus.REPLAYED, f.service.transact(seller, request).status)
        f.service.open(buyer)
        assertEquals(1007, f.a(buyer).amount(CoreResource.ORE, 3)); assertEquals(993, f.a(seller).amount(CoreResource.ORE, 3))
        assertEquals(66, f.a(seller).silver); assertEquals(13, f.a(buyer).buyOrders.single().remaining)
        f.commit(buyer, CoreAction.CancelBuyOrder(order.id)); assertEquals(930, f.a(buyer).silver)
        assertEquals(CoreTransactionStatus.REJECTED, f.send(seller, request.action).status)
    }

    @Test fun `gear order preserves exact quality identity and cannot consume equipped or bound gear`() {
        val f = Fixture(); val buyer = f.create(); val seller = f.create(0)
        val order = f.commit(buyer, CoreAction.PlaceBuyOrder(null, CoreGearSlot.WEAPON, 2, 1, 120)).buyOrders.single()
        val item = f.commit(seller, CoreAction.Manufacture(CoreGearSlot.WEAPON, 2)).storedGear.single()
        assertEquals(CoreTransactionStatus.REJECTED, f.send(seller, CoreAction.FillBuyOrder(buyer, order.id, 120, gearId = f.a(seller).weaponIdentity.id)).status)
        f.commit(seller, CoreAction.FillBuyOrder(buyer, order.id, 120, gearId = item.identity.id))
        assertEquals(item.identity, f.a(buyer).storedGear.single().identity)
        assertTrue(f.a(seller).storedGear.isEmpty()); assertTrue(f.a(buyer).buyOrders.isEmpty())
        assertEquals(114, f.a(seller).silver)
    }

    @Test fun `two suppliers race for last unit only one wins`() {
        val f = Fixture(); val buyer = f.create(); val sellers = listOf(f.create(0), f.create(0))
        val order = f.commit(buyer, CoreAction.PlaceBuyOrder(CoreResource.BOARD, null, 1, 1, 100)).buyOrders.single()
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        val results = try { executor.invokeAll(sellers.map { id -> java.util.concurrent.Callable { f.send(id, CoreAction.FillBuyOrder(buyer, order.id, 100)) } }).map { it.get() } } finally { executor.shutdownNow() }
        assertEquals(1, results.count { it.successful }); assertEquals(95, sellers.sumOf { f.a(it).silver })
        assertEquals(1001, f.a(buyer).amount(CoreResource.BOARD))
    }

    @Test fun `buy order write interruption recovers both escrows and supplier exactly once`() {
        val f = Fixture(); val buyer = f.create(); val seller = f.create(0)
        val o = f.commit(buyer, CoreAction.PlaceBuyOrder(CoreResource.HIDE, null, 2, 5, 10)).buyOrders.single()
        var writes = 0
        val failing = CoreAccountService(CoreAccountRepository(f.dir) { from, to ->
            if (to.toString().endsWith(".account") && ++writes == 2) error("power loss")
            Files.move(from, to, ATOMIC_MOVE, REPLACE_EXISTING); Unit
        })
        failing.open(seller)
        val op = CoreOperation(UUID.randomUUID(), f.a(seller).revision, CoreAction.FillBuyOrder(buyer, o.id, 10, 5))
        assertEquals(CoreTransactionStatus.UNAVAILABLE, failing.transact(seller, op).status)
        val recovered = CoreAccountService(CoreAccountRepository(f.dir)); recovered.open(seller); recovered.open(buyer)
        assertEquals(47, recovered.snapshot(seller)!!.silver); assertEquals(950, recovered.snapshot(buyer)!!.silver)
        assertTrue(recovered.snapshot(buyer)!!.buyOrders.isEmpty()); assertEquals(1005, recovered.snapshot(buyer)!!.amount(CoreResource.HIDE, 2))
        assertEquals(CoreTransactionStatus.REPLAYED, recovered.transact(seller, op).status)
    }

    @Test fun `batch production gains independent specialties quality survives equip and codec`() {
        val f = Fixture(); val id = f.create()
        val a = f.commit(id, CoreAction.Manufacture(CoreGearSlot.ARMOR, 4, 16))
        assertEquals(16, a.storedGear.size); assertEquals(936, a.amount(CoreResource.LEATHER, 4))
        assertEquals(1920, a.professions.getValue(CoreProfession.ARMORSMITH).xp)
        assertNull(a.professions[CoreProfession.WEAPONSMITH]); assertTrue(a.storedGear.all { it.identity.quality in 0..20 })
        val item = a.storedGear.first(); val equipped = f.commit(id, CoreAction.Equip(item.identity.id))
        assertEquals(item.identity, equipped.armorIdentity)
        assertEquals(CoreAccountCodec.encode(equipped), CoreAccountCodec.encode(CoreAccountCodec.decode(CoreAccountCodec.encode(equipped), id)))
        assertEquals(CoreTransactionStatus.REJECTED, f.send(id, CoreAction.Manufacture(CoreGearSlot.WEAPON, 1, 17)).status)
    }

    @Test fun `refining return fractions conserved across batch boundaries at capped mastery`() {
        val id = UUID.randomUUID()
        val a = CoreAccount(id, professions = mapOf(CoreProfession.SMELTING to CoreProfessionProgress(1_000_000)))
        val bulk = CoreProfessions.refineQuote(a, CoreResource.ORE, 1, 64)
        var progress = a; var returned = 0L
        repeat(64) {
            val (quote, next) = CoreProfessions.refineQuote(progress, CoreResource.ORE, 1, 1)
            returned += quote.outputs[CoreMaterial(CoreResource.ORE)] ?: 0
            progress = progress.copy(professions = mapOf(CoreProfession.SMELTING to next))
        }
        assertEquals(bulk.first.outputs[CoreMaterial(CoreResource.ORE)], returned)
        assertEquals(bulk.second, progress.professions[CoreProfession.SMELTING])
    }

    @Test fun `gathering unlocks upper map without boss or combat vouchers and old node cannot replay`() {
        val f = Fixture(); val id = f.create(survey = CoreMmoTuning.balance.surveyTier2 - 1L)
        assertEquals(CoreTransactionStatus.REJECTED, f.send(id, CoreAction.SurveyMap(2, CoreResource.ORE, 1)).status)
        val map = f.commit(id, CoreAction.SurveyMap(1, CoreResource.ORE, 2)).maps.single()
        val run = UUID.randomUUID(); f.commit(id, CoreAction.StartRun(map.id, run))
        val gather = CoreAction.Gather(run, "node", CoreResource.ORE, 2)
        f.commit(id, gather); assertEquals(CoreTransactionStatus.REJECTED, f.send(id, gather).status)
        f.commit(id, CoreAction.FinishRun(run))
        val unlocked = f.commit(id, CoreAction.SurveyMap(2, CoreResource.ORE, 1))
        assertEquals(2, unlocked.maps.single().tier); assertEquals(0, unlocked.amount(CoreResource.BOSS_SIGIL))
        assertEquals(CoreTransactionStatus.REJECTED, f.send(id, gather).status)
        assertTrue(unlocked.claimedSources.all { it.startsWith("run/") })
    }

    @Test fun `dungeon rewards follow checkpoints and clear unlocks next ascension without double ordinary boss payout`() {
        val f = Fixture(); val id = f.create(); val run = UUID.randomUUID()
        assertEquals(CoreTransactionStatus.REJECTED, f.send(id, CoreAction.StartDungeon(run, 1, 1, 9)).status)
        var a = f.commit(id, CoreAction.StartDungeon(run, 1, 0, 9))
        val d = a.activeRun!!.dungeon!!
        assertEquals(CoreTransactionStatus.REJECTED, f.send(id, CoreAction.DungeonReward(run, 2, false)).status)
        assertEquals(CoreTransactionStatus.REJECTED, f.send(id, CoreAction.BossReward(run)).status)
        for (stage in 1..d.stages) {
            val action = CoreAction.DungeonReward(run, stage, stage % d.roomsPerFloor == 0)
            a = f.commit(id, action); assertEquals(CoreTransactionStatus.REJECTED, f.send(id, action).status)
        }
        assertTrue(a.activeRun!!.bossDefeated); assertEquals(0, a.dungeonRecords[1]); assertTrue(a.amount(CoreCraftingCurrency.DIVINE) > 0)
        assertEquals(CoreTransactionStatus.REJECTED, f.send(id, CoreAction.AbortRun(run)).status)
        f.commit(id, CoreAction.FinishRun(run)); f.commit(id, CoreAction.StartDungeon(UUID.randomUUID(), 1, 1, 4))
    }

    @Test fun `v6 upgrade preserves exact backup equipment and enhancement`() {
        val f = Fixture(); val id = f.create()
        val original = f.a(id).copy(weaponEnhancement = CoreEnhancementState(25, 2), weaponBroken = true)
        val body = CoreAccountCodec.encode(original).substringBefore("checksum\t").lineSequence().filterNot(::coreExpansionRow).joinToString("\n").replaceFirst("\t7\t", "\t6\t")
        val old = body + "checksum\t" + MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString("") { "%02x".format(it) } + "\n"
        Files.writeString(f.dir.resolve("$id.account"), old); f.service.forget(id)
        val loaded = assertIs<CoreAccountLoadResult.Ready>(f.service.open(id)).account
        assertEquals(original.weaponEnhancement, loaded.weaponEnhancement); assertTrue(loaded.weaponBroken)
        f.commit(id, CoreAction.ClaimMap(1, 3))
        assertEquals(old, Files.readString(f.dir.resolve("$id.account.v6.bak")))
    }

    @Test fun `bounded receipts no longer halt long careers and retired request is stale`() {
        val f = Fixture(); val id = f.create()
        val requestId = UUID.randomUUID()
        val receipts = (1L..CoreLoopCatalog.MAX_RECEIPTS).associate { revision ->
            (if (revision == 1L) requestId else UUID.randomUUID()) to CoreReceipt("a".repeat(64), revision, "old")
        }
        val full = f.a(id).copy(revision = CoreLoopCatalog.MAX_RECEIPTS + 1L, receipts = receipts)
        Files.writeString(f.dir.resolve("$id.account"), CoreAccountCodec.encode(full)); f.service.forget(id); f.service.open(id)
        val next = f.commit(id, CoreAction.ClaimMap(1, 2)); assertEquals(4096, next.receipts.size)
        assertEquals(CoreTransactionStatus.STALE, f.service.transact(id, CoreOperation(requestId, 0, CoreAction.ClaimMap(1, 2))).status)
    }

    @Test fun `tuning loads defaults rejects typo and invalid scaling`() {
        val path = Files.createTempDirectory("mmo-tuning-").resolve("balance.properties")
        assertEquals(CoreMmoBalance(), CoreMmoBalance.load(path))
        Files.writeString(path, "profession.refine-return-max-percent=100\n")
        assertFailsWith<IllegalArgumentException> { CoreMmoBalance.load(path) }
        Files.writeString(path, "profesion.craft-xp=20\n")
        assertFailsWith<IllegalArgumentException> { CoreMmoBalance.load(path) }
    }
}
