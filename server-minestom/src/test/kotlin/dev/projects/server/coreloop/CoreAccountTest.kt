package dev.projects.server.coreloop

import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreAccountTest {
    private class Fixture(val repository: CoreAccountRepository = CoreAccountRepository(Files.createTempDirectory("projects-core-test"))) {
        val player = UUID.randomUUID()
        val service = CoreAccountService(repository)
        init { assertIs<CoreAccountLoadResult.Ready>(service.open(player)) }
        val account: CoreAccount get() = assertNotNull(service.snapshot(player))
        fun perform(action: CoreAction): CoreTransactionResult = service.transact(player, CoreOperation(UUID.randomUUID(), account.revision, action))
        fun commit(action: CoreAction): CoreAccount = perform(action).let {
            assertEquals(CoreTransactionStatus.COMMITTED, it.status, it.message); assertNotNull(it.account)
        }
        fun start(tier: Int = 1): UUID {
            val owned = account.maps.firstOrNull { it.tier == tier } ?: commit(CoreAction.ClaimMap(tier, 77)).maps.last()
            val run = UUID.randomUUID()
            commit(CoreAction.StartRun(owned.id, run))
            return run
        }
    }

    @Test fun `combat only loop reaches T4 gear and maps without mandatory gathering`() {
        val f = Fixture()
        for (tier in 1..3) {
            val run = f.start(tier)
            val awarded = f.commit(CoreAction.BossReward(run))
            assertEquals(tier + 1, awarded.unlockedMapTier)
            assertTrue(awarded.maps.any { it.tier == tier + 1 })
            assertEquals(2, awarded.amount(CoreResource.BOSS_SIGIL, tier))
            f.commit(CoreAction.FinishRun(run))
            for ((raw, batches) in listOf(CoreResource.ORE to 2, CoreResource.WOOD to 1, CoreResource.HIDE to 2, CoreResource.FIBER to 1)) {
                f.commit(CoreAction.Exchange(raw, tier, batches))
                f.commit(CoreAction.Refine(raw, tier, batches * 2))
            }
            f.commit(CoreAction.UpgradeWeapon)
            val upgraded = f.commit(CoreAction.UpgradeArmor)
            assertEquals(tier + 1, upgraded.weaponTier)
            assertEquals(tier + 1, upgraded.armorTier)
            assertEquals(0, upgraded.amount(CoreResource.BOSS_SIGIL, tier))
            f.service.forget(f.player)
            assertIs<CoreAccountLoadResult.Ready>(f.service.open(f.player))
            assertEquals(tier + 1, f.account.weaponTier)
        }
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.UpgradeWeapon).status)
        assertTrue(CoreLoopCatalog.weaponDamage(4) > CoreLoopCatalog.weaponDamage(3))
        assertEquals(190.0, CoreLoopCatalog.armorHealth(4))
        val run = f.start(4)
        f.commit(CoreAction.Gather(run, "stone-node", CoreResource.STONE, 8))
        f.commit(CoreAction.Gather(run, "wood-node", CoreResource.WOOD, 4))
        f.commit(CoreAction.BossReward(run))
        f.commit(CoreAction.FinishRun(run))
        f.commit(CoreAction.Refine(CoreResource.STONE, 4, 4))
        f.commit(CoreAction.Refine(CoreResource.WOOD, 4, 2))
        f.commit(CoreAction.Craft(CoreResource.GATHERING_TABLET, tier = 4))
        f.commit(CoreAction.Craft(CoreResource.WHETSTONE, tier = 4))
        assertEquals(1, f.account.amount(CoreResource.WHETSTONE))
        assertTrue(f.account.maps.any { it.tier == 4 })
    }

    @Test fun `gathering stash recipes and map modifiers survive restart with exactly once rewards`() {
        val f = Fixture()
        val run = f.start()
        f.commit(CoreAction.Gather(run, "stone-1", CoreResource.STONE, 4))
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.Gather(run, "stone-1", CoreResource.STONE, 4)).status)
        f.commit(CoreAction.Gather(run, "wood-1", CoreResource.WOOD, 2))
        f.commit(CoreAction.FinishRun(run))
        f.commit(CoreAction.Refine(CoreResource.STONE, 1, 2))
        f.commit(CoreAction.Refine(CoreResource.WOOD, 1))
        f.commit(CoreAction.Craft(CoreResource.GATHERING_TABLET))
        val map = f.commit(CoreAction.ClaimMap(1, -999)).maps.single()
        val modifier = CoreMapModifier("woodcutting", "dense_regions", 50)
        f.commit(CoreAction.ApplyTablet(map.id, modifier))
        f.service.forget(f.player)
        val reloaded = assertIs<CoreAccountLoadResult.Ready>(f.service.open(f.player)).account
        assertEquals(listOf(modifier), reloaded.maps.single().modifiers)
        assertEquals(-999, reloaded.maps.single().seed)
        assertEquals(0, reloaded.amount(CoreResource.STONE))
        assertEquals(0, reloaded.amount(CoreResource.GATHERING_TABLET))
    }

    @Test fun `repeated request concurrent clicks and reconnect cannot multiply rewards`() {
        val f = Fixture()
        val run = f.start()
        val request = CoreOperation(UUID.randomUUID(), f.account.revision, CoreAction.BossReward(run))
        val pool = Executors.newFixedThreadPool(2)
        val results = try { pool.invokeAll(List(2) { java.util.concurrent.Callable { f.service.transact(f.player, request) } }).map { it.get() } }
        finally { pool.shutdown() }
        assertEquals(1, results.count { it.status == CoreTransactionStatus.COMMITTED })
        assertEquals(1, results.count { it.status == CoreTransactionStatus.REPLAYED })
        f.service.forget(f.player)
        f.service.open(f.player)
        assertEquals(CoreTransactionStatus.REPLAYED, f.service.transact(f.player, request).status)
        assertEquals(2, f.account.amount(CoreResource.BOSS_SIGIL))
        assertEquals(1, f.account.amount(CoreResource.GATHERING_TABLET))
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.BossReward(run)).status)
        val conflict = request.copy(action = CoreAction.CombatReward(run, "new"))
        assertEquals(CoreTransactionStatus.CONFLICT, f.service.transact(f.player, conflict).status)
    }

    @Test fun `map failure refund and withdrawal retain loot with free T1 escape hatch`() {
        val f = Fixture()
        val first = f.start()
        val original = f.account.activeRun!!.map.id
        f.commit(CoreAction.AbortRun(first))
        assertEquals(original, f.account.maps.single().id)
        assertNull(f.account.activeRun)
        val second = f.start()
        f.commit(CoreAction.Gather(second, "hide", CoreResource.HIDE, 3))
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.AbortRun(second)).status)
        f.commit(CoreAction.FinishRun(second))
        assertEquals(3, f.account.amount(CoreResource.HIDE))
        assertTrue(f.account.maps.isEmpty())
        f.commit(CoreAction.ClaimMap(1, 20))
        assertEquals(1, f.account.maps.size)
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.ClaimMap(2, 20)).status)
    }

    @Test fun `save failure consumes no resources and a retry commits once`() {
        var fail = false
        val directory = Files.createTempDirectory("projects-core-write-failure")
        val f = Fixture(CoreAccountRepository(directory) { source, target ->
            if (fail) error("injected disk failure")
            Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING)
        })
        val run = f.start()
        f.commit(CoreAction.Gather(run, "ore", CoreResource.ORE, 4))
        f.commit(CoreAction.FinishRun(run))
        val before = f.account.revision
        val request = CoreOperation(UUID.randomUUID(), before, CoreAction.Refine(CoreResource.ORE, 1, 2))
        fail = true
        assertEquals(CoreTransactionStatus.SAVE_FAILED, f.service.transact(f.player, request).status)
        assertEquals(4, f.account.amount(CoreResource.ORE))
        assertEquals(before, f.account.revision)
        fail = false
        assertEquals(CoreTransactionStatus.COMMITTED, f.service.transact(f.player, request).status)
        assertEquals(CoreTransactionStatus.REPLAYED, f.service.transact(f.player, request).status)
        assertEquals(0, f.account.amount(CoreResource.ORE))
        assertEquals(2, f.account.amount(CoreResource.INGOT))
        Files.list(directory).use { paths -> assertFalse(paths.anyMatch { it.fileName.toString().endsWith(".tmp") }) }
    }

    @Test fun `failure after successful atomic replace resolves durable receipt`() {
        val f = Fixture(CoreAccountRepository(Files.createTempDirectory("projects-core-after-rename")) { source, target ->
            Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING)
            error("injected failure after commit")
        })
        f.commit(CoreAction.ClaimMap(1, 42))
        assertEquals(1, f.account.maps.size)
    }

    @Test fun `corrupt unknown and non regular records fail closed without overwriting source`() {
        for (text in listOf("not an account", "PROJECTS_CORE_LOOP\t99\tunknown\n")) {
            val dir = Files.createTempDirectory("projects-core-invalid")
            val player = UUID.randomUUID()
            val path = dir.resolve("$player.account")
            Files.writeString(path, text)
            val repo = CoreAccountRepository(dir)
            assertIs<CoreRepositoryLoad.Invalid>(repo.load(player))
            assertIs<CoreRepositorySave.Failed>(repo.commit(0, CoreAccount(player, revision = 1)))
            assertEquals(text, Files.readString(path))
        }
        val dir = Files.createTempDirectory("projects-core-directory-file")
        val player = UUID.randomUUID()
        Files.createDirectory(dir.resolve("$player.account"))
        assertIs<CoreRepositoryLoad.Invalid>(CoreAccountRepository(dir).load(player))
    }

    @Test fun `stale account from another service cannot overwrite newer commit`() {
        val dir = Files.createTempDirectory("projects-core-revision")
        val player = UUID.randomUUID()
        val one = CoreAccountService(CoreAccountRepository(dir))
        val two = CoreAccountService(CoreAccountRepository(dir))
        one.open(player); two.open(player)
        assertEquals(CoreTransactionStatus.COMMITTED, one.transact(player, CoreOperation(UUID.randomUUID(), 0, CoreAction.ClaimMap(1, 1))).status)
        assertEquals(CoreTransactionStatus.CONFLICT, two.transact(player, CoreOperation(UUID.randomUUID(), 0, CoreAction.ClaimMap(1, 2))).status)
        assertNull(two.snapshot(player))
        assertEquals(1, assertIs<CoreAccountLoadResult.Ready>(two.open(player)).account.maps.single().seed)
    }

    @Test fun `rejected upgrade leaves all ingredients and identity unchanged`() {
        val f = Fixture()
        val before = f.account
        val rejected = f.perform(CoreAction.UpgradeWeapon)
        assertEquals(CoreTransactionStatus.REJECTED, rejected.status)
        assertEquals(before.revision, f.account.revision)
        assertEquals(before.balances, f.account.balances)
        assertEquals(1, f.account.weaponTier)
        val external = mutableMapOf(CoreMaterial(CoreResource.WOOD) to 4L)
        val snapshot = CoreAccount(f.player, balances = external)
        external.clear()
        assertEquals(4, snapshot.amount(CoreResource.WOOD))
    }
}
