package dev.projects.server.coreloop

import java.nio.file.Files
import java.util.UUID
import kotlin.test.*

/** gatherer → market → researcher → product → smith / fighter, with silver leaving the economy. */
class ResearchEconomyTest {
    private class Fixture {
        val dir = Files.createTempDirectory("research-economy-")
        val repo = CoreAccountRepository(dir.resolve("core"))
        val service = CoreAccountService(repo)
        val magicDir = dir.resolve("magic")
        val magic = FirstMagicService(magicDir)
        val desk = ResearchDesk(service, magic)
        fun create(silver: Long = 0, balances: Map<CoreMaterial, Long> = emptyMap(), magicState: FirstMagicState? = null,
                   edit: (CoreAccount) -> CoreAccount = { it }): UUID {
            val id = UUID.randomUUID()
            assertEquals(CoreRepositorySave.Saved, repo.commit(0, edit(CoreAccount(id, revision = 1, balances = balances, silver = silver))))
            assertIs<CoreAccountLoadResult.Ready>(service.open(id))
            magicState?.let { FirstMagicRepository(magicDir).save(id, it) }
            magic.load(id)
            return id
        }
        fun a(id: UUID) = requireNotNull(service.snapshot(id))
        fun send(id: UUID, action: CoreAction) = service.transact(id, CoreOperation(UUID.randomUUID(), a(id).revision, action))
        fun commit(id: UUID, action: CoreAction): CoreAccount = send(id, action).let {
            assertEquals(CoreTransactionStatus.COMMITTED, it.status, it.message); requireNotNull(it.account)
        }
        fun totalSilver(vararg ids: UUID) = ids.sumOf { a(it).silver }
    }

    private val moonbell = CoreMaterial(CoreResource.MOONBELL)
    private val catalyst = CoreMaterial(CoreResource.ARCANE_CATALYST)
    private val seal = CoreMaterial(CoreResource.MENDING_SEAL)

    /** Desk restored, MOONBELL/EMBER_MOSS analyzed, and "lamp" solved with its known two-step route. */
    private fun researcher(jars: Map<FirstAspect, Int> = mapOf(FirstAspect.EMBER to 4, FirstAspect.GALE to 4), unlocked: Boolean = true) = FirstMagicState(
        studied = setOf(AnomalousMaterial.MOONBELL, AnomalousMaterial.EMBER_MOSS),
        jars = jars, reacted = true, deskRestored = true, firstDistillation = true,
        discoveredResearchAspects = setOf("aer", "aqua", "ordo", "ignis", "terra", "lux", "tempestas"),
        researchInk = mapOf("aer" to 3, "aqua" to 3),
        researchPlacements = if (unlocked) mapOf("lamp" to mapOf(20 to "lux", 24 to "tempestas")) else emptyMap(),
        unlockedResearch = if (unlocked) setOf("lamp") else emptySet(),
    )

    @Test fun `gatherer picks a sample in the field and it is a tier-1 market good`() {
        val f = Fixture(); val gatherer = f.create()
        val map = f.commit(gatherer, CoreAction.ClaimMap(1, 7)).maps.single()
        val run = UUID.randomUUID()
        f.commit(gatherer, CoreAction.StartRun(map.id, run))
        f.commit(gatherer, CoreAction.Gather(run, "anomaly-moonbell", CoreResource.MOONBELL, 1))
        assertEquals(1, f.a(gatherer).amount(moonbell))
        assertEquals(CoreTransactionStatus.REJECTED, f.send(gatherer, CoreAction.Gather(run, "anomaly-moonbell", CoreResource.MOONBELL, 1)).status,
            "one find per sample per run")
        assertTrue(CoreEconomy.tradeable(CoreResource.MOONBELL))
        assertFailsWith<IllegalArgumentException> { CoreMarketOffer(UUID.randomUUID(), 10, CoreMaterial(CoreResource.MOONBELL, 2)) }
    }

    @Test fun `sample listing and buy order move goods between gatherer and researcher`() {
        val f = Fixture()
        val gatherer = f.create(balances = mapOf(moonbell to 5L))
        val researcher = f.create(silver = 500)
        val listing = f.commit(gatherer, CoreAction.ListMaterial(moonbell, 3, 60)).offers.single()
        assertTrue(f.service.marketSnapshot().any { it.offer.id == listing.id && it.offer.material == moonbell })
        assertTrue(f.service.transact(researcher, CoreOperation(UUID.randomUUID(), f.a(researcher).revision,
            CoreAction.BuyOffer(gatherer, listing.id, 60))).successful)
        assertEquals(3, f.a(researcher).amount(moonbell))
        assertEquals(60 - CoreEconomy.fee(60), f.a(gatherer).silver)
        // The reverse direction: a standing buy order, filled by the gatherer.
        f.commit(researcher, CoreAction.PlaceBuyOrder(CoreResource.MOONBELL, null, 1, 2, 25))
        val order = f.service.buyOrderSnapshot().single { it.buyer == researcher }
        assertTrue(f.service.transact(gatherer, CoreOperation(UUID.randomUUID(), f.a(gatherer).revision,
            CoreAction.FillBuyOrder(researcher, order.order.id, 25, 2))).successful)
        assertEquals(5, f.a(researcher).amount(moonbell))
        assertEquals(0, f.a(gatherer).amount(moonbell))
        assertFailsWith<IllegalArgumentException> { CoreBuyOrder(UUID.randomUUID(), 10, 1, 2, CoreResource.MOONBELL) }
    }

    @Test fun `distilling and ink spend the sample and destroy the silver fee`() {
        val f = Fixture()
        val b = CoreMmoTuning.balance
        val id = f.create(silver = 100, balances = mapOf(moonbell to 3L), magicState = researcher(jars = emptyMap(), unlocked = false))
        val distilled = f.desk.perform(id, FirstMagicAction.Distill(AnomalousMaterial.MOONBELL))
        assertTrue(distilled.committed, distilled.message)
        assertEquals(2, f.a(id).amount(moonbell))
        assertEquals(100L - b.researchDistillSilver, f.a(id).silver, "fee is burned, nobody receives it")
        assertEquals(2, f.magic.snapshot(id)!!.jar(FirstAspect.TIDE))
        val inked = f.desk.perform(id, FirstMagicAction.BrewInk(AnomalousMaterial.MOONBELL))
        assertTrue(inked.committed, inked.message)
        assertEquals(1, f.a(id).amount(moonbell))
        assertEquals(100L - b.researchDistillSilver - b.researchInkSilver, f.a(id).silver)
        assertEquals(3 + b.researchInkYield, f.magic.snapshot(id)!!.ink("aer"))
        // The sidecar never stores samples again; the ledger is the only owner.
        assertTrue(FirstMagicRepository(f.magicDir).load(id).materialCounts.isEmpty())
    }

    @Test fun `without silver nothing is spent on either side`() {
        val f = Fixture()
        val id = f.create(silver = 0, balances = mapOf(moonbell to 1L), magicState = researcher(jars = emptyMap(), unlocked = false))
        val before = f.magic.snapshot(id)
        val step = f.desk.perform(id, FirstMagicAction.Distill(AnomalousMaterial.MOONBELL))
        assertFalse(step.committed)
        assertEquals(1, f.a(id).amount(moonbell))
        assertEquals(before, f.magic.snapshot(id))
    }

    @Test fun `crafting is gated by the personal unlock and makes a tradeable product`() {
        val f = Fixture()
        val ingot = CoreMaterial(CoreResource.INGOT, 1)
        val locked = f.create(silver = 200, balances = mapOf(ingot to 2L), magicState = researcher(unlocked = false))
        val refused = f.desk.perform(locked, FirstMagicAction.Craft("lamp"))
        assertFalse(refused.committed)
        assertEquals(200, f.a(locked).silver); assertEquals(0, f.a(locked).amount(catalyst))

        val id = f.create(silver = 200, balances = mapOf(ingot to 2L), magicState = researcher())
        val crafted = f.desk.perform(id, FirstMagicAction.Craft("lamp"))
        assertTrue(crafted.committed, crafted.message)
        assertEquals(1, f.a(id).amount(catalyst))
        assertEquals(1, f.a(id).amount(ingot))
        assertEquals(200L - CoreMmoTuning.balance.researchCraftSilver, f.a(id).silver)
        assertEquals(2, f.magic.snapshot(id)!!.jar(FirstAspect.EMBER))
        assertEquals(2, f.magic.snapshot(id)!!.jar(FirstAspect.GALE))
        val notEnough = f.desk.perform(id, FirstMagicAction.Craft("lamp")).let { assertTrue(it.committed); f.desk.perform(id, FirstMagicAction.Craft("lamp")) }
        assertFalse(notEnough.committed, "jars are empty now")

        // The unlock is not a ledger good; the product is.
        val smith = f.create(silver = 300)
        val listing = f.commit(id, CoreAction.ListMaterial(catalyst, 1, 120)).offers.single()
        assertTrue(f.service.transact(smith, CoreOperation(UUID.randomUUID(), f.a(smith).revision,
            CoreAction.BuyOffer(id, listing.id, 120))).successful)
        assertEquals(1, f.a(smith).amount(catalyst))
        assertTrue(ResearchRecipes.all.all { CoreEconomy.tradeable(it.product) })
        assertEquals(ResearchCatalog.byId.keys, ResearchRecipes.byResearch.keys)
    }

    @Test fun `smith spends the catalyst for a higher focused success chance`() {
        val f = Fixture()
        val mats = (1..4).flatMap { t -> listOf(CoreResource.INGOT, CoreResource.BOARD, CoreResource.STONE_BLOCK, CoreResource.CLOTH).map { CoreMaterial(it, t) to 50L } }
            .toMap() + mapOf(CoreMaterial(CoreResource.AFFIX_DUST) to 10L)
        val id = f.create(balances = mats + mapOf(catalyst to 1L)) { it.copy(weaponEnhancement = CoreEnhancementState(15)) }
        val withCatalyst = CoreEnhancementCatalog.quote(f.a(id), CoreGearSlot.WEAPON, CoreEnhancementMode.FOCUSED)
        assertEquals(CoreMmoTuning.balance.researchCatalystBonusPercent.toDouble(), withCatalyst.catalystBonusPercent)
        assertEquals(1L, withCatalyst.recipe.costs[catalyst])
        assertNull(withCatalyst.recipe.costs[CoreMaterial(CoreResource.AFFIX_DUST)])
        f.commit(id, CoreAction.EnhanceEquipment(CoreGearSlot.WEAPON, CoreEnhancementMode.FOCUSED))
        assertEquals(0, f.a(id).amount(catalyst))
        val plain = CoreEnhancementCatalog.quote(f.a(id), CoreGearSlot.WEAPON, CoreEnhancementMode.FOCUSED)
        assertEquals(CoreEnhancementCatalog.CATALYST_BONUS_PERCENT, plain.catalystBonusPercent)
    }

    @Test fun `fighter repairs broken gear with seals instead of a spare`() {
        val f = Fixture()
        val id = f.create(balances = mapOf(seal to 3L)) { it.copy(weaponTier = 2, weaponBroken = true) }
        f.commit(id, CoreAction.SealRepair(CoreGearSlot.WEAPON))
        assertFalse(f.a(id).weaponBroken)
        assertEquals(3L - 2 * CoreMmoTuning.balance.researchSealsPerTier, f.a(id).amount(seal))
        assertEquals(CoreTransactionStatus.REJECTED, f.send(id, CoreAction.SealRepair(CoreGearSlot.WEAPON)).status)
    }

    @Test fun `old first-magic saves with samples migrate into the ledger exactly once`() {
        val f = Fixture()
        val id = UUID.randomUUID()
        assertEquals(CoreRepositorySave.Saved, f.repo.commit(0, CoreAccount(id, revision = 1)))
        // A pre-change V2 sidecar that still owns two samples.
        FirstMagicRepository(f.magicDir).save(id, FirstMagicState(
            materialCounts = mapOf(AnomalousMaterial.MOONBELL to 2, AnomalousMaterial.WARM_ORE to 1), reacted = true))
        assertIs<CoreAccountLoadResult.Ready>(f.service.open(id)); f.magic.load(id)
        assertTrue(f.desk.migrateLegacyMaterials(id)!!.successful)
        assertEquals(2, f.a(id).amount(moonbell)); assertEquals(1, f.a(id).amount(CoreResource.WARM_ORE))
        assertTrue(FirstMagicRepository(f.magicDir).load(id).materialCounts.isEmpty())
        assertNull(f.desk.migrateLegacyMaterials(id))

        // Crash between the two saves: the ledger has the receipt but the sidecar still lists samples.
        FirstMagicRepository(f.magicDir).save(id, FirstMagicState(materialCounts = mapOf(AnomalousMaterial.MOONBELL to 2), reacted = true))
        f.magic.load(id)
        assertNull(f.desk.migrateLegacyMaterials(id), "receipt present: clear the sidecar, grant nothing")
        assertEquals(2, f.a(id).amount(moonbell))
        assertTrue(f.magic.snapshot(id)!!.materialCounts.isEmpty())
        f.service.forget(id); f.service.open(id)
        assertEquals(2, f.a(id).amount(moonbell))
    }

    @Test fun `V1 sidecar and old balance overrides still load`() {
        val player = UUID.randomUUID()
        val directory = Files.createTempDirectory("research-v1")
        Files.writeString(directory.resolve("$player.magic"), listOf(
            "FIRST_MAGIC_V1", player.toString(), "1,1,0", "3,0,0,0,0,0", "1,0,0,0,0,0", "0,3,0,0",
        ).joinToString("\n", postfix = "\n"))
        assertEquals(3, FirstMagicRepository(directory).load(player).count(AnomalousMaterial.MOONBELL))
        val path = Files.createTempDirectory("research-balance").resolve("balance.properties")
        Files.writeString(path, "survey.map-cost=5\n")
        assertEquals(CoreMmoBalance(surveyMapCost = 5), CoreMmoBalance.load(path), "overrides written before research keys stay valid")
        Files.writeString(path, "research.craft-silver=80\n")
        assertEquals(80, CoreMmoBalance.load(path).researchCraftSilver)
    }

    @Test fun `research test top-up stays dev-only server data`() {
        val f = Fixture()
        val id = f.create(magicState = researcher(unlocked = false))
        val step = f.desk.perform(id, FirstMagicAction.PrepareResearchTest)
        assertTrue(step.committed, step.message)
        assertTrue(AnomalousMaterial.entries.all { f.a(id).amount(it.resource) == 4L })
        assertEquals(300, f.a(id).silver)
        assertTrue(FirstMagicRepository(f.magicDir).load(id).materialCounts.isEmpty())
    }
}
