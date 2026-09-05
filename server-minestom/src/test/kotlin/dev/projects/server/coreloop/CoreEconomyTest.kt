package dev.projects.server.coreloop

import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID
import kotlin.test.*

class CoreEconomyTest {
    private class Fixture {
        val dir = Files.createTempDirectory("core-economy-")
        val repo = CoreAccountRepository(dir)
        var day = 20_000L
        val service = CoreAccountService(repo) { day }
        fun create(silver: Long = 0): UUID {
            val id = UUID.randomUUID()
            val balances = CoreResource.entries.flatMap { r -> (1..4).map { CoreMaterial(r, it) to 100L } }.toMap()
                .filterKeys { CoreEconomy.tradeable(it.resource) || it.resource == CoreResource.COMBAT_TOKEN }
            assertEquals(CoreRepositorySave.Saved, repo.commit(0, CoreAccount(id, revision = 1, balances = balances, silver = silver)))
            assertIs<CoreAccountLoadResult.Ready>(service.open(id))
            return id
        }
        fun a(id: UUID) = requireNotNull(service.snapshot(id))
        fun op(id: UUID, action: CoreAction) = CoreOperation(UUID.randomUUID(), a(id).revision, action)
        fun send(id: UUID, action: CoreAction) = service.transact(id, op(id, action))
        fun commit(id: UUID, action: CoreAction): CoreAccount = send(id, action).let {
            assertEquals(CoreTransactionStatus.COMMITTED, it.status, it.message); requireNotNull(it.account)
        }
        fun craft(id: UUID, slot: CoreGearSlot = CoreGearSlot.WEAPON, tier: Int = 1) = commit(id, CoreAction.Manufacture(slot, tier)).storedGear.last()
    }

    @Test fun `manufacture T1 through T4 creates distinct empty gear without replacing current equipment`() {
        val f = Fixture(); val id = f.create()
        val before = f.a(id)
        for (tier in 1..4) for (slot in CoreGearSlot.entries) {
            val item = f.craft(id, slot, tier)
            assertEquals(tier, item.tier); assertEquals(id, item.identity.crafter)
            assertFalse(item.identity.bound); assertTrue(item.affixes.isEmpty())
        }
        assertEquals(8, f.a(id).storedGear.map { it.identity.id }.distinct().size)
        assertEquals(before.weaponIdentity, f.a(id).weaponIdentity)
        assertEquals(1, f.a(id).weaponTier)
        assertEquals(96, f.a(id).amount(CoreResource.INGOT, 4))
        val item = f.a(id).storedGear.last()
        val equipped = f.commit(id, CoreAction.Equip(item.identity.id))
        assertEquals(item.identity, equipped.armorIdentity)
        assertTrue(equipped.storedGear.any { it.identity == before.armorIdentity })
        f.service.forget(id); f.service.open(id)
        assertEquals(CoreAccountCodec.encode(equipped), CoreAccountCodec.encode(f.a(id)))
    }

    @Test fun `material escrow cancellation and purchase conserve stock and charge only sale fee`() {
        val f = Fixture(); val seller = f.create(); val buyer = f.create(100)
        val material = CoreMaterial(CoreResource.ORE, 2)
        val listing = f.commit(seller, CoreAction.ListMaterial(material, 7, 60)).offers.single()
        assertEquals(93, f.a(seller).amount(material))
        val request = f.op(buyer, CoreAction.BuyOffer(seller, listing.id, 60))
        val result = f.service.transact(buyer, request)
        assertTrue(result.successful, result.message)
        assertEquals(107, f.a(buyer).amount(material)); assertEquals(40, f.a(buyer).silver)
        assertEquals(57, f.a(seller).silver); assertTrue(f.a(seller).offers.isEmpty())
        assertEquals(CoreTransactionStatus.REPLAYED, f.service.transact(buyer, request).status)
        assertEquals(CoreTransactionStatus.REJECTED, f.send(buyer, request.action).status)
        val second = f.commit(seller, CoreAction.ListMaterial(material, 4, 10)).offers.single()
        f.commit(seller, CoreAction.CancelOffer(second.id))
        assertEquals(93, f.a(seller).amount(material))
    }

    @Test fun `stored modified enhanced equipment sells with its identity and rolls intact`() {
        val f = Fixture(); val seller = f.create(); val buyer = f.create(1000)
        val base = f.craft(seller, tier = 4)
        f.commit(seller, CoreAction.Equip(base.identity.id))
        // Existing crafting/enhancement authority still mutates the equipped piece, not its identity.
        val a = f.a(seller)
        val rolled = CoreCraftingCatalog.craft(a.copy(currencies = mapOf(CoreCraftingCurrency.ALCHEMY to 1L)),
            CoreGearSlot.WEAPON, CoreCraftingCurrency.ALCHEMY, UUID.randomUUID())
        val modified = rolled.copy(revision = a.revision + 1, weaponEnhancement = CoreEnhancementState(12, 2), weaponBroken = true)
        assertEquals(CoreRepositorySave.Saved, f.repo.commit(a.revision, modified))
        f.service.forget(seller); f.service.open(seller)
        val starter = f.a(seller).storedGear.single()
        f.commit(seller, CoreAction.Equip(starter.identity.id))
        val offer = f.commit(seller, CoreAction.ListGear(base.identity.id, 400)).offers.single()
        assertEquals(CoreTransactionStatus.REJECTED, f.send(seller, CoreAction.Equip(base.identity.id)).status)
        f.service.forget(seller) // offline sellers are supported
        f.commit(buyer, CoreAction.BuyOffer(seller, offer.id, 400))
        val bought = f.a(buyer).storedGear.single()
        assertEquals(base.identity, bought.identity); assertEquals(CoreEnhancementState(12, 2), bought.enhancement)
        assertEquals(modified.equippedAffixes, bought.affixes)
        assertTrue(bought.broken)
        f.commit(buyer, CoreAction.Equip(bought.identity.id))
        assertEquals(4, f.a(buyer).weaponTier)
        assertTrue(f.a(buyer).weaponBroken)
        f.service.open(seller); assertEquals(380, f.a(seller).silver)
    }

    @Test fun `unaffordable stale price own listings and full stock do not partially spend`() {
        val f = Fixture(); val seller = f.create(); val buyer = f.create(9)
        val offer = f.commit(seller, CoreAction.ListMaterial(CoreMaterial(CoreResource.WOOD), 2, 10)).offers.single()
        val before = CoreAccountCodec.encode(f.a(buyer))
        assertEquals(CoreTransactionStatus.REJECTED, f.send(buyer, CoreAction.BuyOffer(seller, offer.id, 10)).status)
        assertEquals(CoreTransactionStatus.REJECTED, f.send(buyer, CoreAction.BuyOffer(seller, offer.id, 9)).status)
        assertEquals(CoreTransactionStatus.REJECTED, f.send(seller, CoreAction.BuyOffer(seller, offer.id, 10)).status)
        assertEquals(before, CoreAccountCodec.encode(f.a(buyer)))
        assertEquals(offer, f.a(seller).offers.single())
    }

    @Test fun `combat vouchers cannot become raw materials and finite delivery consumes only self crafted bases`() {
        val f = Fixture(); val id = f.create()
        assertEquals(CoreTransactionStatus.REJECTED, f.send(id, CoreAction.Exchange(CoreResource.ORE, 1)).status)
        f.commit(id, CoreAction.RedeemTokens(2, 2)); assertEquals(40, f.a(id).silver)
        repeat(3) { f.commit(id, CoreAction.Deliver(f.craft(id).identity.id)) }
        val fourth = f.craft(id)
        assertEquals(CoreTransactionStatus.REJECTED, f.send(id, CoreAction.Deliver(fourth.identity.id)).status)
        f.day++
        f.commit(id, CoreAction.Deliver(fourth.identity.id))
        assertEquals(360, f.a(id).silver); assertTrue(f.a(id).storedGear.isEmpty())
    }

    @Test fun `interrupted two party commit recovers both sides before reads and replays exactly once`() {
        val f = Fixture(); val seller = f.create(); val buyer = f.create(100)
        val offer = f.commit(seller, CoreAction.ListMaterial(CoreMaterial(CoreResource.ORE), 3, 40)).offers.single()
        var accountWrites = 0
        val failing = CoreAccountRepository(f.dir) { source, target ->
            if (target.toString().endsWith(".account") && ++accountWrites == 2) error("simulated interruption")
            Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING); Unit
        }
        val service = CoreAccountService(failing)
        service.open(buyer)
        val request = CoreOperation(UUID.randomUUID(), f.a(buyer).revision, CoreAction.BuyOffer(seller, offer.id, 40))
        assertEquals(CoreTransactionStatus.UNAVAILABLE, service.transact(buyer, request).status)
        assertTrue(Files.exists(f.dir.resolve("market.pending")))
        val recovered = CoreAccountService(CoreAccountRepository(f.dir))
        recovered.open(buyer); recovered.open(seller)
        assertEquals(60, recovered.snapshot(buyer)?.silver)
        assertEquals(38, recovered.snapshot(seller)?.silver)
        assertEquals(103, recovered.snapshot(buyer)?.amount(CoreResource.ORE))
        assertTrue(recovered.snapshot(seller)!!.offers.isEmpty())
        assertFalse(Files.exists(f.dir.resolve("market.pending")))
        assertEquals(CoreTransactionStatus.REPLAYED, recovered.transact(buyer, request).status)
    }

    @Test fun `v4 migration preserves exact equipment mods enhancement and backup on first write`() {
        val dir = Files.createTempDirectory("economy-v4-")
        val id = UUID.randomUUID()
        val a = CoreCraftingCatalog.craft(CoreAccount(id, revision = 7, weaponTier = 4,
            weaponEnhancement = CoreEnhancementState(23, 1), currencies = mapOf(CoreCraftingCurrency.ALCHEMY to 1L)),
            CoreGearSlot.WEAPON, CoreCraftingCurrency.ALCHEMY, UUID.randomUUID())
        val body = CoreAccountCodec.encode(a).substringBefore("checksum\t").lineSequence().filterNot(::coreExpansionRow)
            .filterNot { it.startsWith("identity\t") || it.startsWith("economy\t") }.joinToString("\n")
            .replaceFirst("PROJECTS_CORE_LOOP\t7\t", "PROJECTS_CORE_LOOP\t4\t")
        val checksum = java.security.MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString("") { "%02x".format(it) }
        val old = body + "checksum\t$checksum\n"
        val file = dir.resolve("$id.account")
        Files.writeString(file, old)
        val service = CoreAccountService(CoreAccountRepository(dir))
        val loaded = assertIs<CoreAccountLoadResult.Ready>(service.open(id)).account
        assertEquals(a.equippedAffixes, loaded.equippedAffixes)
        assertEquals(a.weaponEnhancement, loaded.weaponEnhancement)
        assertTrue(loaded.weaponIdentity.bound)
        assertEquals(old, Files.readString(file)) // read does not migrate the account
        assertTrue(service.transact(id, CoreOperation(UUID.randomUUID(), 7, CoreAction.ClaimMap(1, 9))).successful)
        assertEquals(old, Files.readString(dir.resolve("$id.account.v4.bak")))
        assertTrue(Files.readString(file).startsWith("PROJECTS_CORE_LOOP\t7\t"))
    }

    @Test fun `inventory capacity and failing ordinary save never spend inputs`() {
        val f = Fixture(); val id = f.create(100)
        val full = f.a(id).copy(revision = 2, storedGear = List(CoreEconomy.MAX_GEAR) {
            CoreStoredGear(CoreGearIdentity(UUID.randomUUID(), id), CoreGearSlot.WEAPON, 1, CoreGearRarity.NORMAL, CoreEnhancementState())
        })
        assertEquals(CoreRepositorySave.Saved, f.repo.commit(1, full))
        f.service.forget(id); f.service.open(id)
        assertEquals(CoreTransactionStatus.REJECTED, f.send(id, CoreAction.Manufacture(CoreGearSlot.WEAPON, 1)).status)
        val seller = f.create()
        val gear = f.craft(seller)
        val offer = f.commit(seller, CoreAction.ListGear(gear.identity.id, 30)).offers.single()
        assertEquals(CoreTransactionStatus.REJECTED, f.send(id, CoreAction.BuyOffer(seller, offer.id, 30)).status)
        assertEquals(100, f.a(id).silver)
        assertEquals(offer, f.a(seller).offers.single())
        val failing = CoreAccountService(CoreAccountRepository(f.dir) { _, _ -> error("disk failed") })
        failing.open(seller)
        val before = requireNotNull(failing.snapshot(seller))
        val result = failing.transact(seller, CoreOperation(UUID.randomUUID(), before.revision, CoreAction.Manufacture(CoreGearSlot.WEAPON, 1)))
        assertEquals(CoreTransactionStatus.SAVE_FAILED, result.status)
        assertEquals(CoreAccountCodec.encode(before), CoreAccountCodec.encode(requireNotNull(failing.snapshot(seller))))
    }

    @Test fun `two buyers competing for same offer only transfer it once`() {
        val f = Fixture(); val seller = f.create(); val one = f.create(100); val two = f.create(100)
        val offer = f.commit(seller, CoreAction.ListMaterial(CoreMaterial(CoreResource.ORE), 1, 60)).offers.single()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        val results = try { pool.invokeAll(listOf(one, two).map { id -> java.util.concurrent.Callable {
            f.send(id, CoreAction.BuyOffer(seller, offer.id, 60))
        } }).map { it.get() } } finally { pool.shutdownNow() }
        assertEquals(1, results.count { it.successful })
        assertEquals(140, f.a(one).silver + f.a(two).silver)
        assertEquals(201, f.a(one).amount(CoreResource.ORE) + f.a(two).amount(CoreResource.ORE))
        assertEquals(57, f.a(seller).silver)
    }

    @Test fun `expeditions preserve breakage and repair consumes donor without deleting target mods or enhancement`() {
        val f = Fixture(); val id = f.create()
        val made = f.craft(id)
        f.commit(id, CoreAction.Equip(made.identity.id))
        val a = f.a(id)
        val modified = CoreCraftingCatalog.craft(a.copy(currencies = mapOf(CoreCraftingCurrency.ALCHEMY to 1L)),
            CoreGearSlot.WEAPON, CoreCraftingCurrency.ALCHEMY, UUID.randomUUID())
            .copy(revision = a.revision + 1, weaponEnhancement = CoreEnhancementState(20), weaponBroken = true)
        assertEquals(CoreRepositorySave.Saved, f.repo.commit(a.revision, modified))
        f.service.forget(id); f.service.open(id)
        val map = f.commit(id, CoreAction.ClaimMap(1, 5)).maps.single()
        val run = UUID.randomUUID()
        f.commit(id, CoreAction.StartRun(map.id, run))
        f.commit(id, CoreAction.FinishRun(run))
        assertTrue(f.a(id).weaponBroken)
        assertEquals(CoreTransactionStatus.REJECTED, f.send(id, CoreAction.FinishRun(run)).status)
        val damageBefore = CoreWeaponPresentation.damage(f.a(id))
        val spare = f.craft(id)
        val repaired = f.commit(id, CoreAction.Repair(CoreGearSlot.WEAPON, spare.identity.id))
        assertFalse(repaired.weaponBroken)
        assertEquals(modified.weaponIdentity, repaired.weaponIdentity)
        assertEquals(modified.weaponEnhancement, repaired.weaponEnhancement)
        assertEquals(modified.equippedAffixes, repaired.equippedAffixes)
        assertTrue(CoreWeaponPresentation.damage(repaired) > damageBefore)
        assertFalse(repaired.storedGear.any { it.identity.id == spare.identity.id })
        assertEquals(CoreTransactionStatus.REJECTED, f.send(id, CoreAction.Repair(CoreGearSlot.WEAPON, spare.identity.id)).status)
    }
}
