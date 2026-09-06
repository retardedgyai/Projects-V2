package dev.projects.server.coreloop

import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.*

class CoreJourneyTest {
    private class Fixture(initial: CoreAccount? = null) {
        val dir = Files.createTempDirectory("journey-test-")
        val id = initial?.playerId ?: UUID.randomUUID()
        val repository = CoreAccountRepository(dir)
        val service = CoreAccountService(repository)
        init {
            if (initial != null) Files.writeString(dir.resolve("$id.account"), CoreAccountCodec.encode(initial))
            assertIs<CoreAccountLoadResult.Ready>(service.open(id))
        }
        val a get() = service.snapshot(id)!!
        fun send(action: CoreAction) = service.transact(id, CoreOperation(UUID.randomUUID(), a.revision, action))
        fun act(action: CoreAction) = send(action).also { assertEquals(CoreTransactionStatus.COMMITTED, it.status, it.message) }.account!!
        fun reload() { service.forget(id); assertIs<CoreAccountLoadResult.Ready>(service.open(id)) }
        fun start(map: CoreOwnedMap = a.maps.last()) = UUID.randomUUID().also { act(CoreAction.StartRun(map.id, it)) }
    }
    @Test fun `each Tier has ten distinct ranks with continuous monotonic levels`() {
        for (lv in 1..40) {
            assertEquals(lv, CoreJourneyRules.level(CoreJourneyRules.threshold(lv)))
            if (lv > 1) assertEquals(lv - 1, CoreJourneyRules.level(CoreJourneyRules.threshold(lv) - 1))
        }
        for (tier in 1..4) assertEquals(9, CoreJourneyRules.ceiling(tier) - CoreJourneyRules.floor(tier))
    }
    @Test fun `fresh choice is mandatory UI state and switching classes preserves equipment identities`() {
        val f = Fixture(); assertFalse(f.a.journey.chosen); assertFalse(f.a.journey.legacy)
        val original = f.a.weaponIdentity
        for (job in listOf(CoreClass.MAGE, CoreClass.RANGER, CoreClass.WARRIOR, CoreClass.MAGE, CoreClass.RANGER, CoreClass.WARRIOR)) {
            f.act(CoreAction.ChooseClass(job)); f.reload()
            assertTrue(f.a.weaponIdentity.base.usable(job))
            val ids = f.a.storedGear.map { it.identity.id } + f.a.weaponIdentity.id
            assertEquals(ids.size, ids.toSet().size)
            assertTrue(original.id in ids)
        }
        assertEquals(3, f.a.storedGear.size)
        assertTrue(f.a.storedGear.all { it.identity.bound })
    }
    @Test fun `Mage alone can ascend at level20 with a T2 boss proof without destroying it`() {
        val id = UUID.randomUUID()
        val f = Fixture(CoreAccount(id, journey = CoreJourney.fresh().copy(xp = CoreJourneyRules.threshold(20)),
            balances = mapOf(CoreMaterial(CoreResource.BOSS_SIGIL, 2) to 2L)))
        assertEquals(CoreTransactionStatus.REJECTED, f.send(CoreAction.ChooseClass(CoreClass.STARWEAVER)).status)
        f.act(CoreAction.ChooseClass(CoreClass.MAGE)); val weapon = f.a.weaponIdentity
        f.act(CoreAction.ChooseClass(CoreClass.STARWEAVER)); f.reload()
        assertEquals(weapon, f.a.weaponIdentity); assertEquals(2, f.a.amount(CoreResource.BOSS_SIGIL, 2))
        assertEquals(CoreClass.STARWEAVER, f.a.journey.job)
    }
    @Test fun `first boss keeps T1 and a ten-level capstone advances generation`() {
        val f = Fixture(); f.act(CoreAction.ChooseClass(CoreClass.MAGE)); f.act(CoreAction.ClaimMap(1, 17))
        repeat(6) { index ->
            val run = f.start(); val level = f.a.activeRun!!.map.level
            f.act(CoreAction.BossReward(run)); assertEquals(if (level == 10) 2 else 1, f.a.unlockedMapTier)
            f.act(CoreAction.FinishRun(run)); f.reload()
            assertEquals(listOf(3,5,7,9,10,11)[index], f.a.maps.last().level)
        }
    }
    @Test fun `T1 through T4 capstones and dungeon checkpoints are a connected durable campaign`() {
        val f = Fixture(); f.act(CoreAction.ChooseClass(CoreClass.WARRIOR)); f.act(CoreAction.ClaimMap(1, 43))
        repeat(24) {
            val run = f.start()
            f.act(CoreAction.LearnCombat(0)); f.act(CoreAction.LearnCombat(1))
            f.act(CoreAction.CombatReward(run, "enemy")); f.act(CoreAction.BossReward(run)); f.act(CoreAction.FinishRun(run))
            f.reload()
        }
        assertEquals(4, f.a.unlockedMapTier); assertEquals(40, f.a.maps.last().level)
        assertTrue(f.a.journey.level > 1); assertTrue((0..3).all(f.a.journey::knows))
        val run = UUID.randomUUID(); f.act(CoreAction.StartDungeon(run, 4, 0, 91))
        val dungeon = f.a.activeRun!!.dungeon!!
        for (stage in 1..dungeon.stages) f.act(CoreAction.DungeonReward(run, stage, stage % dungeon.roomsPerFloor == 0))
        assertEquals(0, f.a.dungeonRecords[4]); assertTrue(f.a.amount(CoreCraftingCurrency.DIVINE) > 0)
        f.act(CoreAction.FinishRun(run)); f.act(CoreAction.StartDungeon(UUID.randomUUID(), 4, 1, 92))
    }
    @Test fun `repeat loot cannot duplicate character XP even through legacy callback`() {
        val f = Fixture(); f.act(CoreAction.ClaimMap(1, 11)); val run = f.start()
        f.act(CoreAction.CombatReward(run, "same")); val xp = f.a.journey.xp
        f.act(CoreAction.AffixLoot(run, "same", CoreLootKind.NORMAL)); assertEquals(xp, f.a.journey.xp)
        assertEquals(CoreTransactionStatus.REJECTED, f.send(CoreAction.AffixLoot(run, "same", CoreLootKind.NORMAL)).status)
        assertEquals(xp, f.a.journey.xp)
    }
    @Test fun `all low generation materials retain concrete higher crafting demand`() {
        for (lower in 1..3) for (slot in CoreGearSlot.entries) {
            val material = CoreMaterial(if (slot == CoreGearSlot.WEAPON) CoreResource.INGOT else CoreResource.LEATHER, lower)
            assertTrue(CoreEconomy.manufacture(slot, 4).costs.getValue(material) > 0)
        }
        assertEquals(6, CoreWeaponBase.entries.size)
        assertEquals(4, CoreWeaponBase.entries.count { it.family == "greatsword" })
    }
    @Test fun `temper preserves mods quality enhancement crafter and ID and rejects past Tier cap`() {
        val id = UUID.randomUUID()
        val original = CoreAccount(id, balances = mapOf(CoreMaterial(CoreResource.INGOT) to 64L, CoreMaterial(CoreResource.STONE_BLOCK) to 64L),
            weaponIdentity = CoreGearIdentity(UUID.randomUUID(), id, quality = 17, base = CoreWeaponBase.CLEAVER), weaponEnhancement = CoreEnhancementState(12))
        val f = Fixture(original)
        repeat(9) { f.act(CoreAction.TemperEquipment(CoreGearSlot.WEAPON)) }
        assertEquals(original.weaponIdentity.copy(itemLevel = 10), f.a.weaponIdentity)
        assertEquals(original.weaponEnhancement, f.a.weaponEnhancement)
        assertEquals(CoreTransactionStatus.REJECTED, f.send(CoreAction.TemperEquipment(CoreGearSlot.WEAPON)).status)
        f.reload(); assertEquals(10, f.a.weaponIdentity.itemLevel)
    }
    @Test fun `v7 is read only until first commit and gets an exact backup with legacy access preserved`() {
        val id = UUID.randomUUID(); val f = Fixture(CoreAccount(id, unlockedMapTier = 4))
        val body = CoreAccountCodec.encode(f.a).substringBefore("checksum\t").lineSequence()
            .filterNot { it.substringBefore('\t') in setOf("journey", "gear-base", "map-level") }.joinToString("\n").replaceFirst("\t8\t", "\t7\t")
        val old = body + "checksum\t" + MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString("") { "%02x".format(it) } + "\n"
        Files.writeString(f.dir.resolve("$id.account"), old); f.reload()
        assertTrue(f.a.journey.legacy); assertEquals(31, f.a.journey.level)
        assertEquals(old, Files.readString(f.dir.resolve("$id.account")))
        f.act(CoreAction.ClaimMap(1, 1))
        assertEquals(old, Files.readString(f.dir.resolve("$id.account.v7.bak")))
    }
}
