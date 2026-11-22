package dev.projects.server.coreloop

import java.nio.file.Files
import java.util.UUID
import kotlin.test.*

/**
 * The ledger side of a dungeon run (docs/development/dungeon-test-coverage.md): what the boss pays and when, how the
 * clears (段階) are counted, and what is kept when the run ends by dying, falling, leaving or logging out — every one
 * of those ends in FinishRun (CoreLoopServer.returnToHarbor, or the login / disconnect recovery).
 */
class CoreDungeonLedgerTest {
    private class Fixture(initial: CoreAccount) {
        val player = initial.playerId
        val service: CoreAccountService
        init {
            val repository = CoreAccountRepository(Files.createTempDirectory("projects-dungeon"))
            assertEquals(CoreRepositorySave.Saved, repository.commit(0, initial))
            service = CoreAccountService(repository)
            service.open(player)
        }
        val account get() = service.snapshot(player)!!
        fun perform(action: CoreAction) = service.transact(player, CoreOperation(UUID.randomUUID(), account.revision, action))
        fun commit(action: CoreAction) = perform(action).also { assertEquals(CoreTransactionStatus.COMMITTED, it.status, it.message) }.account!!
        fun rejected(action: CoreAction, why: String) {
            val before = CoreAccountCodec.encode(account)
            assertEquals(CoreTransactionStatus.REJECTED, perform(action).status, why)
            assertEquals(before, CoreAccountCodec.encode(account), "$why: a rejected action changed the account")
        }
        fun enter(tier: Int = 1) = UUID.randomUUID().also { commit(CoreAction.StartDungeon(CoreDungeons.SAKURA, tier, it)) }
        fun reload() { service.forget(player); service.open(player) }
    }

    private fun account(clears: Int? = null, unlocked: Int = 1, xp: Long = 0) = CoreAccount(UUID.randomUUID(), revision = 1, craftingSeed = 42,
        unlockedMapTier = unlocked, journey = CoreJourney.fresh().copy(xp = xp),
        dungeonClears = clears?.let { mapOf(CoreDungeons.SAKURA to it) } ?: emptyMap())

    @Test fun `the boss pays once at the run's tier, adds 冒険 xp, and leaves maps, map tier and zone records alone`() {
        val f = Fixture(account(unlocked = 2, xp = CoreJourneyRules.threshold(CoreJourneyRules.floor(2))))
        val run = f.enter(2)
        val before = f.account
        assertEquals(2, before.activeRun!!.map.tier)
        f.commit(CoreAction.BossReward(run))
        val after = f.account
        assertEquals(2, after.amount(CoreResource.BOSS_SIGIL, 2))
        assertEquals(12, after.amount(CoreResource.COMBAT_TOKEN, 2))
        assertEquals(0, after.amount(CoreResource.BOSS_SIGIL, 1), "the sigils follow the run's tier, not T1")
        assertEquals(before.amount(CoreCraftingCurrency.EXALTED) + 1, after.amount(CoreCraftingCurrency.EXALTED))
        assertEquals(before.amount(CoreActivityKind.TRIAL) + 1, after.amount(CoreActivityKind.TRIAL))
        assertEquals(before.journey.gain(CoreJourneyRules.reward(2, true)).xp, after.journey.xp)
        assertEquals(before.maps, after.maps, "a dungeon gives no map")
        assertEquals(before.unlockedMapTier, after.unlockedMapTier)
        assertEquals(before.zoneRecords, after.zoneRecords)
        assertTrue(after.activeRun!!.bossDefeated)
        // Twice is refused, also after a reload (the boss reward retries every 5 s until it is saved).
        f.rejected(CoreAction.BossReward(run), "a second boss reward")
        f.reload()
        f.rejected(CoreAction.BossReward(run), "a boss reward after a reload")
    }

    @Test fun `a full map vault does not hold back the dungeon's boss reward`() {
        val f = Fixture(account())
        repeat(CoreLoopCatalog.MAX_MAPS) { f.commit(CoreAction.ClaimMap(1, it.toLong())) }
        val run = f.enter()
        f.commit(CoreAction.BossReward(run))
        assertEquals(CoreLoopCatalog.MAX_MAPS, f.account.maps.size)
        assertEquals(1, f.account.dungeonClears[CoreDungeons.SAKURA])
    }

    @Test fun `only a boss reward counts a clear - dying, falling, leaving or logging out do not`() {
        val f = Fixture(account())
        // Each of these ends the run with FinishRun and no boss reward.
        repeat(3) { f.commit(CoreAction.FinishRun(f.enter())) }
        val lost = f.enter(); f.reload(); f.commit(CoreAction.FinishRun(lost))   // the login recovery
        assertTrue(f.account.dungeonClears.isEmpty())
        assertEquals(1, CoreDungeons.stage(f.account.dungeonClears[CoreDungeons.SAKURA] ?: 0))
        for (clears in 1..5) {
            val run = f.enter()
            f.commit(CoreAction.BossReward(run))
            assertEquals(clears, f.account.dungeonClears[CoreDungeons.SAKURA], "the clear is counted when the reward is saved, before going home")
            f.commit(CoreAction.FinishRun(run))
            assertEquals(minOf(clears + 1, CoreDungeons.MAX_STAGE), CoreDungeons.stage(f.account.dungeonClears.getValue(CoreDungeons.SAKURA)))
        }
        f.reload()
        assertEquals(5, f.account.dungeonClears[CoreDungeons.SAKURA])
    }

    @Test fun `the clear count stops at its cap and the stage stays 5`() {
        val f = Fixture(account(clears = CoreZoneRecord.MAX_CLEARS))
        val run = f.enter()
        f.commit(CoreAction.BossReward(run))
        assertEquals(CoreZoneRecord.MAX_CLEARS, f.account.dungeonClears[CoreDungeons.SAKURA])
        assertEquals(CoreDungeons.MAX_STAGE, CoreDungeons.stage(CoreZoneRecord.MAX_CLEARS))
        f.commit(CoreAction.FinishRun(run))
        f.reload()
        assertEquals(CoreZoneRecord.MAX_CLEARS, f.account.dungeonClears[CoreDungeons.SAKURA])
    }

    @Test fun `loot picked up in the dungeon is kept when the run ends by death, and a started run can no longer be aborted`() {
        val f = Fixture(account())
        val run = f.enter()
        f.commit(CoreAction.AffixLoot(run, "room-1:soldier-0", CoreLootKind.NORMAL))
        val dust = f.account.amount(CoreResource.AFFIX_DUST)
        val tokens = f.account.amount(CoreResource.COMBAT_TOKEN)
        assertTrue(dust > 0)
        // The same enemy's loot is not paid twice (the loot queue drains again on the way home and on reconnect).
        f.rejected(CoreAction.AffixLoot(run, "room-1:soldier-0", CoreLootKind.NORMAL), "the same enemy's loot twice")
        // The boss's own loot waits for the boss reward.
        f.rejected(CoreAction.AffixLoot(run, "boss", CoreLootKind.BOSS), "boss loot before the boss reward")
        f.rejected(CoreAction.AbortRun(run), "aborting a dungeon that already paid loot")
        f.commit(CoreAction.FinishRun(run))      // 力尽きた… → home
        assertNull(f.account.activeRun)
        assertEquals(dust, f.account.amount(CoreResource.AFFIX_DUST))
        assertEquals(tokens, f.account.amount(CoreResource.COMBAT_TOKEN))
        assertTrue(f.account.dungeonClears.isEmpty())
        f.reload()
        assertEquals(dust, f.account.amount(CoreResource.AFFIX_DUST))
    }

    @Test fun `the boss's loot is paid once after the boss reward`() {
        val f = Fixture(account())
        val run = f.enter()
        f.commit(CoreAction.BossReward(run))
        f.commit(CoreAction.AffixLoot(run, "boss", CoreLootKind.BOSS))
        f.rejected(CoreAction.AffixLoot(run, "boss-again", CoreLootKind.BOSS), "a second boss loot under another source")
    }

    @Test fun `nothing is paid into a run that has ended or into another run`() {
        val f = Fixture(account())
        val run = f.enter()
        f.rejected(CoreAction.BossReward(UUID.randomUUID()), "a boss reward for another run")
        f.rejected(CoreAction.ActivityReward(run, "altar", CoreActivityKind.RITUAL), "an expedition event reward in a dungeon")
        f.rejected(CoreAction.StartDungeon(CoreDungeons.SAKURA, 1, UUID.randomUUID()), "entering again from inside")
        f.rejected(CoreAction.ClaimMap(1, 7), "a harbor action from inside")
        f.commit(CoreAction.FinishRun(run))
        f.rejected(CoreAction.FinishRun(run), "going home twice")
        f.rejected(CoreAction.BossReward(run), "a boss reward after going home")
        f.rejected(CoreAction.AffixLoot(run, "late", CoreLootKind.NORMAL), "loot after going home")
        f.rejected(CoreAction.StartDungeon(CoreDungeons.SAKURA, 1, run), "the same run id again")
        assertTrue(f.account.dungeonClears.isEmpty())
    }

    @Test fun `the dungeon's enemy level follows 冒険 Lv inside the tier's band`() {
        for ((xpLevel, expected) in listOf(1 to 1, 7 to 7, 25 to 10)) {
            val f = Fixture(account(xp = CoreJourneyRules.threshold(xpLevel)))
            f.enter(1)
            assertEquals(expected, f.account.activeRun!!.map.level, "Lv$xpLevel")
        }
    }
}
