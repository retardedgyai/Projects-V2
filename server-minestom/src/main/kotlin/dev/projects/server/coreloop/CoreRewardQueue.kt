package dev.projects.server.coreloop

import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Pending rewards survive disconnect in memory until the same ledger executor has committed them. */
class CoreRewardQueue(
    private val ledger: CoreAccountService,
    private val executor: ScheduledExecutorService,
    private val onSnapshot: (UUID, CoreAccount) -> Unit = { _, _ -> },
    private val onRetry: (UUID, String) -> Unit = { _, _ -> },
    private val retryDelayMillis: Long = 250,
) {
    private data class Key(val playerId: UUID, val source: String)
    private class Job(val key: Key, val action: CoreAction) {
        val future = CompletableFuture<CoreTransactionResult>()
        var operation: CoreOperation? = null
        var attempts = 0
        var ticket = 0L
        var lastNotice = 0L
    }
    private val lock = Any()
    private val jobs = linkedMapOf<Key, Job>()
    private val waiters = mutableMapOf<UUID, MutableList<CompletableFuture<Void>>>()

    init { require(retryDelayMillis in 1..5_000) }

    fun submit(playerId: UUID, action: CoreAction): CompletableFuture<CoreTransactionResult> {
        val source = source(action) ?: return CompletableFuture.completedFuture(
            CoreTransactionResult(CoreTransactionStatus.REJECTED, null, "報酬以外の操作は保留できません"))
        synchronized(lock) {
            val key = Key(playerId, source)
            jobs[key]?.let { existing ->
                return if (existing.action == action) existing.future else CompletableFuture.completedFuture(
                    CoreTransactionResult(CoreTransactionStatus.CONFLICT, null, "同じ報酬元に異なる内容が送られました"))
            }
            val job = Job(key, action)
            jobs[key] = job
            schedule(job, 0)
            return job.future
        }
    }

    fun isPending(playerId: UUID): Boolean = synchronized(lock) { jobs.keys.any { it.playerId == playerId } }

    /** Stop new gameplay producers before calling. Never join this future on the supplied executor. */
    fun drain(playerId: UUID): CompletableFuture<Void> = synchronized(lock) {
        if (jobs.keys.none { it.playerId == playerId }) CompletableFuture.completedFuture(null)
        else CompletableFuture<Void>().also { waiters.getOrPut(playerId) { mutableListOf() } += it }
    }

    fun retryPending(playerId: UUID) = synchronized(lock) {
        jobs.values.filter { it.key.playerId == playerId }.forEach { schedule(it, 0) }
    }

    private fun schedule(job: Job, delay: Long) {
        val ticket = ++job.ticket
        try {
            executor.schedule({
                val current = synchronized(lock) { jobs[job.key] === job && job.ticket == ticket }
                if (current) attempt(job)
            }, delay, TimeUnit.MILLISECONDS)
        } catch (failure: RuntimeException) {
            // The reward stays pending. Shutdown must drain before stopping this shared executor.
            notice(job, "報酬の保存待ちです。保存処理が停止しています: ${failure.javaClass.simpleName}")
        }
    }

    private fun attempt(job: Job) {
        val account = ledger.snapshot(job.key.playerId) ?: when (val loaded = ledger.open(job.key.playerId)) {
            is CoreAccountLoadResult.Ready -> loaded.account
            is CoreAccountLoadResult.Invalid -> { retry(job, loaded.reason); return }
        }
        if (job.key.source in account.claimedSources) {
            complete(job, CoreTransactionResult(CoreTransactionStatus.REPLAYED, account, "この報酬は保存済みです"))
            return
        }
        val operation = job.operation ?: CoreOperation(UUID.randomUUID(), account.revision, job.action).also { job.operation = it }
        val result = try { ledger.transact(job.key.playerId, operation) }
        catch (failure: RuntimeException) { retry(job, failure.message ?: "報酬を保存できません"); return }
        if (result.successful) { complete(job, result); return }
        if (result.status == CoreTransactionStatus.STALE) {
            // Nothing committed for this source. A new revision requires a new operation identity.
            job.operation = null
        }
        retry(job, result.message)
    }

    private fun retry(job: Job, reason: String) = synchronized(lock) {
        if (jobs[job.key] !== job) return@synchronized
        job.attempts = (job.attempts + 1).coerceAtMost(6)
        notice(job, "報酬の保存を再試行しています: $reason")
        schedule(job, (retryDelayMillis * (1L shl (job.attempts - 1))).coerceAtMost(5_000))
    }

    private fun complete(job: Job, result: CoreTransactionResult) {
        val removed = synchronized(lock) { jobs.remove(job.key, job) }
        if (!removed) return
        result.account?.let { account -> runCatching { onSnapshot(job.key.playerId, account) } }
        job.future.complete(result)
        val finished = synchronized(lock) {
            if (jobs.keys.none { it.playerId == job.key.playerId }) waiters.remove(job.key.playerId).orEmpty() else emptyList()
        }
        finished.forEach { it.complete(null) }
    }

    private fun notice(job: Job, reason: String) {
        val now = System.nanoTime()
        if (job.lastNotice == 0L || now - job.lastNotice >= TimeUnit.SECONDS.toNanos(30)) {
            job.lastNotice = now
            runCatching { onRetry(job.key.playerId, reason.take(256)) }
        }
    }

    private fun source(action: CoreAction): String? = when (action) {
        is CoreAction.Gather -> "gather/${action.runId}/${action.nodeId}"
        is CoreAction.CombatReward -> "combat/${action.runId}/${action.encounterId}"
        is CoreAction.BossReward -> "boss/${action.runId}/defeat"
        is CoreAction.ActivityReward -> "activity/${action.runId}/${action.sourceId}"
        is CoreAction.AffixLoot -> if (action.kind == CoreLootKind.BOSS) "boss-affix/${action.runId}/defeat"
            else "affix/${action.runId}/${action.sourceId}"
        else -> null
    }
}
