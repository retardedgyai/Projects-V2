package dev.projects.server.coreloop

import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CoreRewardQueueTest {
    @Test fun `failed reward stays pending then commits once before departure barrier`() {
        val fail = AtomicBoolean(false)
        val repository = CoreAccountRepository(Files.createTempDirectory("projects-core-reward-queue")) { from, to ->
            if (fail.get()) error("simulated temporary disk outage")
            Files.move(from, to, ATOMIC_MOVE, REPLACE_EXISTING)
        }
        val ledger = CoreAccountService(repository)
        val player = UUID.randomUUID()
        val run = UUID.randomUUID()
        prepare(ledger, player, run)
        val executor = Executors.newSingleThreadScheduledExecutor()
        val failedOnce = CountDownLatch(1)
        val queue = CoreRewardQueue(ledger, executor, onRetry = { _, _ -> failedOnce.countDown() }, retryDelayMillis = 10)
        try {
            fail.set(true)
            val action = CoreAction.Gather(run, "ore-node", CoreResource.ORE, 4)
            val reward = queue.submit(player, action)
            assertSame(reward, queue.submit(player, action))
            val barrier = queue.drain(player)
            assertTrue(failedOnce.await(3, TimeUnit.SECONDS))
            assertTrue(queue.isPending(player))
            assertFalse(reward.isDone)
            assertFalse(barrier.isDone)
            assertEquals(0, ledger.snapshot(player)!!.amount(CoreResource.ORE))
            fail.set(false)
            queue.retryPending(player)
            assertTrue(reward.get(3, TimeUnit.SECONDS).successful)
            barrier.get(3, TimeUnit.SECONDS)
            assertFalse(queue.isPending(player))
            assertEquals(4, ledger.snapshot(player)!!.amount(CoreResource.ORE))
            assertEquals(CoreTransactionStatus.REPLAYED, queue.submit(player, action).get(3, TimeUnit.SECONDS).status)
            assertEquals(4, ledger.snapshot(player)!!.amount(CoreResource.ORE))
        } finally { executor.shutdownNow() }
    }

    @Test fun `stale delayed reward rebases safely and drain covers all queued rewards`() {
        val fail = AtomicBoolean(false)
        val ledger = CoreAccountService(CoreAccountRepository(Files.createTempDirectory("projects-core-reward-rebase")) { from, to ->
            if (fail.get()) error("first reward write fails")
            Files.move(from, to, ATOMIC_MOVE, REPLACE_EXISTING)
        })
        val player = UUID.randomUUID()
        val run = UUID.randomUUID()
        prepare(ledger, player, run)
        val executor = Executors.newSingleThreadScheduledExecutor()
        val failedOnce = CountDownLatch(1)
        val queue = CoreRewardQueue(ledger, executor, onRetry = { _, _ -> failedOnce.countDown() }, retryDelayMillis = 100)
        try {
            fail.set(true)
            val first = queue.submit(player, CoreAction.CombatReward(run, "mob-1", 2))
            assertTrue(failedOnce.await(3, TimeUnit.SECONDS))
            fail.set(false)
            // Same shared executor commits a different action before the first retry.
            executor.submit {
                val current = ledger.snapshot(player)!!
                assertTrue(ledger.transact(player, CoreOperation(UUID.randomUUID(), current.revision,
                    CoreAction.Gather(run, "wood", CoreResource.WOOD, 3))).successful)
            }.get(3, TimeUnit.SECONDS)
            val second = queue.submit(player, CoreAction.BossReward(run))
            queue.drain(player).get(5, TimeUnit.SECONDS)
            assertTrue(first.get().successful && second.get().successful)
            assertEquals(14, ledger.snapshot(player)!!.amount(CoreResource.COMBAT_TOKEN))
            assertEquals(2, ledger.snapshot(player)!!.amount(CoreResource.BOSS_SIGIL))
            assertEquals(3, ledger.snapshot(player)!!.amount(CoreResource.WOOD))
            assertTrue(ledger.snapshot(player)!!.activeRun!!.bossDefeated)
        } finally { executor.shutdownNow() }
    }

    private fun prepare(ledger: CoreAccountService, player: UUID, run: UUID) {
        ledger.open(player)
        val claim = ledger.transact(player, CoreOperation(UUID.randomUUID(), 0, CoreAction.ClaimMap(1, 42)))
        val account = assertNotNull(claim.account)
        assertTrue(ledger.transact(player, CoreOperation(UUID.randomUUID(), account.revision,
            CoreAction.StartRun(account.maps.single().id, run))).successful)
    }
}
